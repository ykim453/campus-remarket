import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Imports the legacy campus application's PUBLIC schema into a new, offline MySQL database. */
class H2ToMysql {
    // Foreign-key parents appear first. Any unknown source table blocks the migration.
    private static final List<String> TABLES = List.of(
        "catalog_meta", "school", "board_categories", "business_categories", "member",
        "email_verification", "student", "shops", "sales", "board_posts", "board_comments",
        "trade_orders", "manner_ratings", "shop_change_logs", "admin_action_logs",
        "seller_notifications", "notification_deliveries", "notification_settings", "trade_reviews",
        "content_reactions", "reservation_requests", "student_campus_events");
    private static final List<String> HISTORICAL_TABLES = List.of(
        "escrow_simulations", "payment_receipts", "payment_events", "toss_customer_keys",
        "toss_escrow_orders", "toss_test_seller_links", "toss_test_payout_links");

    private static String stage = "설정 확인";

    public static void main(String[] args) {
        try {
            migrate();
        } catch (Exception e) {
            // JDBC errors may include passwords, column contents or entire rows.
            String reason = e instanceof MigrationError ? e.getMessage() : e.getClass().getSimpleName();
            if (e instanceof SQLException sql) reason += " SQLState=" + sql.getSQLState() + " code=" + sql.getErrorCode();
            System.err.println("이관 중단 (" + stage + "): " + reason);
            System.exit(1);
        }
    }

    private static String required(String name) throws MigrationError {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new MigrationError(name + " 환경변수가 필요합니다.");
        return value;
    }

    private static void migrate() throws Exception {
        Path file = Path.of(required("CAMPUS_H2_FILE")).toRealPath();
        String filename = file.toString();
        if (!Files.isRegularFile(file) || !filename.endsWith(".mv.db") || filename.contains(";"))
            throw new MigrationError("기존 H2 .mv.db 파일을 지정하세요.");
        String h2Url = "jdbc:h2:file:" + filename.substring(0, filename.length() - 6)
                + ";MODE=MySQL;IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
        String mysqlUrl = required("CAMPUS_DB_URL");
        if (!mysqlUrl.startsWith("jdbc:mysql://")) throw new MigrationError("MySQL JDBC 주소를 지정하세요.");
        try (Connection src = DriverManager.getConnection(h2Url, required("CAMPUS_H2_USER"),
                        System.getenv().getOrDefault("CAMPUS_H2_PASSWORD", ""));
             Connection dst = DriverManager.getConnection(mysqlUrl, required("CAMPUS_DB_USER"), required("CAMPUS_DB_PASSWORD"))) {
            if (!dst.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL"))
                throw new MigrationError("대상 서버가 MySQL이어야 합니다.");
            stage = "원본 스키마 확인";
            Map<String,String> actualNames = new HashMap<>();
            List<String> copyOrder = new ArrayList<>(TABLES);
            copyOrder.addAll(HISTORICAL_TABLES);
            try (var statement=src.createStatement(); var rs=statement.executeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_TYPE='BASE TABLE'")) {
                while (rs.next()) {
                    String name=rs.getString(1), lower=name.toLowerCase(Locale.ROOT);
                    if (!copyOrder.contains(lower) || actualNames.put(lower, name)!=null)
                        throw new MigrationError("지원하지 않는 원본 테이블이 있습니다. 원본을 확인하세요.");
                }
            }
            if (!actualNames.keySet().containsAll(List.of("school","member","student","sales","trade_orders")))
                throw new MigrationError("캠퍼스 리마켓의 필수 테이블을 찾지 못했습니다.");
            stage = "대상 DB 확인";
            String catalog=dst.getCatalog();
            if (catalog==null || List.of("mysql","sys","information_schema","performance_schema").contains(catalog.toLowerCase(Locale.ROOT)))
                throw new MigrationError("새 프로젝트 전용 DB를 지정하세요.");
            if (scalar(dst,"SELECT GET_LOCK(SHA2(CONCAT('campus-h2-',DATABASE()),256),0)")!=1)
                throw new MigrationError("이 DB에서 다른 이관 작업이 진행 중입니다.");
            if (scalar(dst,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")!=0)
                throw new MigrationError("대상 DB에 테이블 또는 뷰가 있습니다. 비어 있는 새 DB를 지정하세요.");
            stage="빈 대상 DB에 테이블 생성";
            try (Statement s=dst.createStatement()) { s.execute("SET SESSION default_storage_engine=InnoDB"); }
            Properties config = new Properties();
            Path resources = Path.of("src","main","resources");
            try (var reader=Files.newBufferedReader(resources.resolve("application.properties"))) { config.load(reader); }
            for (String location : config.getProperty("spring.sql.init.schema-locations").split(",")) {
                if (!location.startsWith("classpath:")) throw new MigrationError("스키마 파일 경로를 확인하세요.");
                Path sqlFile=resources.resolve(location.substring("classpath:".length()));
                ScriptUtils.executeSqlScript(dst,new EncodedResource(new FileSystemResource(sqlFile),"UTF-8"));
            }
            // Keep old receipts and order keys when importing an earlier version's database.
            if (!Collections.disjoint(actualNames.keySet(), HISTORICAL_TABLES)) {
                Path history = Path.of("tools", "legacy-financial-history.sql");
                ScriptUtils.executeSqlScript(dst, new EncodedResource(new FileSystemResource(history), "UTF-8"));
            }
            try (var columns = src.getMetaData().getColumns(null, "PUBLIC", actualNames.get("trade_orders"), null)) {
                while (columns.next()) {
                    if ("payment_key".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        try (Statement statement = dst.createStatement()) {
                            statement.execute("ALTER TABLE trade_orders ADD COLUMN payment_key VARCHAR(300)");
                        }
                        break;
                    }
                }
            }
            // MySQL DDL commits by itself. Copy and verify all data under one transaction.
            dst.setAutoCommit(false);
            long total=0;
            try {
                for (String table : copyOrder) {
                    if (!actualNames.containsKey(table)) continue;
                    stage = table;
                    total += copyTable(src,dst,actualNames.get(table),table);
                }
                dst.commit();
            } catch (Exception e) {
                dst.rollback();
                throw e;
            }
            System.out.println("완료: " + actualNames.size() + "개 테이블에서 " + total + "개 행의 키와 값을 확인했습니다.");
        }
    }

    private static long scalar(Connection conn, String sql) throws SQLException {
        try (Statement s=conn.createStatement(); ResultSet rs=s.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }

    private static long copyTable(Connection src, Connection dst, String sourceName, String table) throws Exception {
        List<String> keys=new ArrayList<>();
        try (var ps=dst.prepareStatement("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION")) {
            ps.setString(1,table);
            try(var rs=ps.executeQuery()){while(rs.next()) keys.add(rs.getString(1));}
        }
        if (keys.isEmpty()) throw new MigrationError("기본 키를 찾지 못했습니다.");
        // Source table names must match our known table names; quote each driver explicitly.
        try (Statement s=src.createStatement(); ResultSet rows=s.executeQuery("SELECT * FROM \"" + sourceName + "\"")) {
            ResultSetMetaData meta=rows.getMetaData();
            List<String> names=new ArrayList<>(); List<Integer> keyIndexes=new ArrayList<>();
            for (int i=1; i<=meta.getColumnCount(); i++) {
                String name=meta.getColumnName(i);
                if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) throw new MigrationError("지원하지 않는 열 이름입니다.");
                names.add("`"+name+"`");
            }
            for (String key:keys) keyIndexes.add(rows.findColumn(key));
            String columns=String.join(",",names);
            String values=String.join(",",Collections.nCopies(names.size(),"?"));
            String predicate=keys.stream().map(k -> "`"+k+"`=?").collect(Collectors.joining(" AND "));
            try (var insert=dst.prepareStatement("INSERT INTO `"+table+"` ("+columns+") VALUES ("+values+")");
                 var lookup=dst.prepareStatement("SELECT "+columns+" FROM `"+table+"` WHERE "+predicate)) {
                long copied=0;
                while(rows.next()) {
                    for (int i=1; i<=names.size(); i++) insert.setObject(i,value(rows,i,meta.getColumnType(i)));
                    insert.executeUpdate();
                    for (int j=0; j<keys.size(); j++) {
                        int i=keyIndexes.get(j);
                        lookup.setObject(j+1,value(rows,i,meta.getColumnType(i)));
                    }
                    try(var saved=lookup.executeQuery()){
                        if (!saved.next()) throw new MigrationError("원본 키로 이관한 행을 찾을 수 없습니다.");
                        for(int i=1; i<=names.size(); i++){
                            if (!Objects.equals(value(rows,i,meta.getColumnType(i)),value(saved,i,meta.getColumnType(i))))
                                throw new MigrationError(names.get(i)+" 열 값이 원본과 일치하지 않습니다.");
                        }
                    }
                    copied++;
                }
                if (scalar(dst,"SELECT COUNT(*) FROM `"+table+"`")!=copied) throw new MigrationError("행 개수가 다릅니다.");
                return copied;
            }
        }
    }

    private static Object value(ResultSet row, int i, int type) throws SQLException {
        return switch(type) {
            case Types.BOOLEAN, Types.BIT -> { boolean value=row.getBoolean(i); yield row.wasNull()?null:value; }
            case Types.TIMESTAMP -> row.getObject(i,LocalDateTime.class);
            case Types.DATE -> row.getObject(i,LocalDate.class);
            case Types.INTEGER,Types.BIGINT,Types.SMALLINT,Types.TINYINT,Types.NUMERIC,Types.DECIMAL -> {
                BigDecimal number=row.getBigDecimal(i); yield number==null?null:number.stripTrailingZeros();
            }
            default -> row.getString(i);
        };
    }

    private static class MigrationError extends Exception {
        MigrationError(String message) { super(message); }
    }
}
