package com.campus.remarket.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Upgrade older one-notification-per-order databases without replacing notifications. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationRecipientMigration implements ApplicationRunner {
    private final JdbcTemplate db;

    public NotificationRecipientMigration(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    public void migrate() {
        boolean mysql =
                Boolean.TRUE.equals(
                        db.execute(
                                (ConnectionCallback<Boolean>)
                                        connection ->
                                                "MySQL"
                                                        .equals(
                                                                connection
                                                                        .getMetaData()
                                                                        .getDatabaseProductName())));
        String schema =
                db.queryForObject(
                        mysql ? "SELECT DATABASE()" : "SELECT CURRENT_SCHEMA", String.class);
        String uniqueColumns =
                """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage k
                  ON k.constraint_schema = tc.constraint_schema AND k.table_name = tc.table_name
                  AND k.constraint_name = tc.constraint_name
                WHERE tc.table_schema = ? AND LOWER(tc.table_name) = 'seller_notifications'
                  AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.constraint_name
                """;
        List<String> old =
                db.queryForList(
                        uniqueColumns
                                + " HAVING COUNT(*) = 1 AND MAX(LOWER(k.column_name)) = 'order_id'",
                        String.class,
                        schema);
        if (old.isEmpty()) return;

        List<String> recipientKeys =
                db.queryForList(
                        uniqueColumns
                                + """
                                HAVING COUNT(*) = 2 AND SUM(CASE WHEN LOWER(k.column_name)
                                    IN ('order_id','seller_member_id') THEN 1 ELSE 0 END) = 2
                                """,
                        String.class,
                        schema);
        if (recipientKeys.isEmpty()) {
            db.execute(
                    "ALTER TABLE seller_notifications ADD CONSTRAINT"
                        + " uq_notification_order_recipient UNIQUE (order_id, seller_member_id)");
        }
        for (String name : old) {
            if (!name.matches("[A-Za-z0-9_]+"))
                throw new IllegalStateException("Unexpected notification constraint name");
            db.execute(
                    mysql
                            ? "ALTER TABLE seller_notifications DROP INDEX `" + name + "`"
                            : "ALTER TABLE seller_notifications DROP CONSTRAINT \"" + name + "\"");
        }
    }
}
