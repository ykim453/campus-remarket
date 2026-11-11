-- MySQL 8.4. The application validates the same tables that these statements create.
CREATE TABLE IF NOT EXISTS school (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    region VARCHAR(50) NOT NULL,
    email_domain VARCHAR(100) NOT NULL,
    address VARCHAR(200),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    photo VARCHAR(100),
    map_image VARCHAR(100),
    campus_code VARCHAR(20) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(30) NOT NULL,
    account_key VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    name VARCHAR(20) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    role ENUM('ADMIN','MANAGER','OWNER','STUDENT') NOT NULL,
    student_status ENUM('ENROLLED','GRADUATED'),
    school_id BIGINT,
    business_no VARCHAR(12) UNIQUE,
    owner_id BIGINT,
    shop_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_member_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE IF NOT EXISTS email_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    verified BOOLEAN NOT NULL,
    attempts INT NOT NULL
);

-- 로그인/회원가입 공통 규격: 학번 문자열, 365 = 36.5도
CREATE TABLE IF NOT EXISTS student (
    unisno VARCHAR(50) PRIMARY KEY,
    member_id BIGINT UNIQUE, -- 실제 회원 ID와 연결; 다른 학교의 동일 학번을 자동 합치지 않음
    temp INT NOT NULL DEFAULT 365,
    CONSTRAINT ck_student_temp CHECK(temp BETWEEN 0 AND 990)
);

CREATE TABLE IF NOT EXISTS shops (
    school_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    shopId INT AUTO_INCREMENT PRIMARY KEY,
    owner_member_id BIGINT UNIQUE,
    shopname VARCHAR(50) NOT NULL,
    shopphone VARCHAR(50),
    address VARCHAR(200),
    photo VARCHAR(255),
    latitude DECIMAL(10,7), longitude DECIMAL(10,7),
    busino VARCHAR(50),
    ceoname VARCHAR(50),
    busi_kind VARCHAR(50),
    bmenu VARCHAR(400),
    reservtime VARCHAR(100),
    busitime VARCHAR(100),
    offday VARCHAR(20),
    specialevent VARCHAR(50),
    sizeandtype VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT '영업종료',
    availablespace VARCHAR(50),
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_shop_coordinates CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)),
    CONSTRAINT ck_shop_status CHECK (status IN ('영업중','혼잡','영업종료','휴무'))
);

CREATE TABLE IF NOT EXISTS sales (
    school_id BIGINT,
    selno BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller VARCHAR(50) NOT NULL,
    seller_member_id BIGINT,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    oprice INT NOT NULL, -- 표시가 / 할인 전 가격
    buyprice INT, -- 판매자가 구입했던 가격 (선택)
    wishprice INT, -- 판매자의 희망 매가 (선택, 할인 계산 기준과 별도)
    detail VARCHAR(1500),
    discount INT NOT NULL DEFAULT 0, -- 할인율 %, 0이면 할인 없음
    starttime DATETIME(6),
    endtime DATETIME(6),
    regtime TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status VARCHAR(20) NOT NULL DEFAULT '판매중',
    photo1 VARCHAR(500), photo2 VARCHAR(500), photo3 VARCHAR(500),
    location VARCHAR(80) NOT NULL DEFAULT '인하대학교',
    icon VARCHAR(20) NOT NULL DEFAULT 'book',
    seller_temperature DECIMAL(5,2) NOT NULL DEFAULT 36.50,
    bumped_at TIMESTAMP(6),
    CONSTRAINT fk_sale_seller_member FOREIGN KEY(seller_member_id) REFERENCES student(member_id),
    CONSTRAINT ck_sale_price CHECK (oprice >= 0 AND (buyprice IS NULL OR buyprice >= 0) AND (wishprice IS NULL OR wishprice >= 0)),
    CONSTRAINT ck_sale_discount CHECK (discount BETWEEN 0 AND 99),
    CONSTRAINT ck_sale_status CHECK (status IN ('판매중','예약중','거래완료')),
    CONSTRAINT ck_sale_discount_time CHECK (
        (discount = 0 AND starttime IS NULL AND endtime IS NULL)
        OR (discount BETWEEN 1 AND 99 AND starttime IS NOT NULL AND endtime IS NOT NULL
            AND endtime = TIMESTAMPADD(HOUR, 24, starttime))
    ),
    CONSTRAINT ck_sale_discount_amount CHECK (discount = 0 OR FLOOR(CAST(oprice AS DECIMAL(18,2)) * (100 - discount) / 100) >= 1),
    INDEX ix_sales_status_regtime (status, regtime),
    INDEX ix_sales_status_bumped (status, bumped_at),
    INDEX ix_sales_seller (seller),
    INDEX ix_sales_seller_member (seller_member_id)
);

CREATE TABLE IF NOT EXISTS board_categories (
    category_id INT PRIMARY KEY,
    category_name VARCHAR(30) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS board_posts (
    school_id BIGINT,
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL,
    writer VARCHAR(50) NOT NULL,
    author_member_id BIGINT,
    author_name VARCHAR(20),
    school_name VARCHAR(100),
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    regtime TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_post_category FOREIGN KEY(category_id) REFERENCES board_categories(category_id),
    INDEX ix_posts_category_time (category_id, regtime)
);

CREATE TABLE IF NOT EXISTS board_comments (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    author_name VARCHAR(20) NOT NULL,
    school_name VARCHAR(100),
    content VARCHAR(1000) NOT NULL,
    regtime TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_comment_post FOREIGN KEY(post_id) REFERENCES board_posts(post_id) ON DELETE CASCADE,
    INDEX ix_comments_post_time (post_id, comment_id)
);

-- 기존 화면의 약속 확정 가격을 판매글 가격과 분리해 보존할 테이블입니다.
CREATE TABLE IF NOT EXISTS trade_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    selno BIGINT NOT NULL,
    buyer VARCHAR(50) NOT NULL,
    agreed_price INT NOT NULL CHECK(agreed_price >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    meeting_place VARCHAR(150),
    meeting_time TIMESTAMP(6),
    latitude DECIMAL(10,7), longitude DECIMAL(10,7),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_order_sale FOREIGN KEY(selno) REFERENCES sales(selno),
    CONSTRAINT ck_order_status CHECK(status IN ('RESERVED','PAYMENT_READY','WAITING_FOR_DEPOSIT','PAID','COMPLETED','거래완료','CANCELED')),
    CONSTRAINT ck_order_coordinates CHECK ((latitude IS NULL AND longitude IS NULL) OR
        (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)),
    INDEX ix_orders_sale (selno)
);

-- 거래당 각 당사자가 상대방을 한 번 평가합니다.
CREATE TABLE IF NOT EXISTS manner_ratings (
    rating_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    reviewer_unisno VARCHAR(50) NOT NULL,
    recipient_unisno VARCHAR(50) NOT NULL,
    feedback VARCHAR(10) NOT NULL,
    delta INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rating_order FOREIGN KEY(order_id) REFERENCES trade_orders(order_id),
    CONSTRAINT fk_rating_reviewer FOREIGN KEY(reviewer_unisno) REFERENCES student(unisno),
    CONSTRAINT fk_rating_recipient FOREIGN KEY(recipient_unisno) REFERENCES student(unisno),
    CONSTRAINT uq_rating_order_reviewer UNIQUE(order_id,reviewer_unisno),
    CONSTRAINT ck_rating_feedback CHECK(feedback IN ('GOOD','BAD')),
    CONSTRAINT ck_rating_self CHECK(reviewer_unisno <> recipient_unisno)
);

-- 실시간 할인 가격을 계산합니다. 만료 후 저장 값을 갱신하지 않아도 조회 가격은 복구됩니다.
CREATE OR REPLACE VIEW sale_listing AS
SELECT s.school_id,s.selno,s.seller,s.seller_member_id,s.category,s.name,s.oprice,s.buyprice,s.wishprice,s.detail,s.discount,s.starttime,s.endtime,s.regtime,s.status,s.photo1,s.photo2,s.photo3,s.location,s.icon,s.bumped_at,
    COALESCE(CAST(st.temp AS DECIMAL(5,1))/10,s.seller_temperature) AS seller_temperature,
    CAST(CASE WHEN status = '판매중' AND discount > 0 AND starttime <= CURRENT_TIMESTAMP AND endtime > CURRENT_TIMESTAMP
        THEN FLOOR(CAST(oprice AS DECIMAL(18,2)) * (100 - discount) / 100) ELSE oprice END AS DECIMAL(12,0)) AS current_price,
    CASE WHEN status = '거래완료' THEN 4 WHEN status = '예약중' THEN 3
         WHEN regtime > TIMESTAMPADD(HOUR, -24, CURRENT_TIMESTAMP) THEN 0
         WHEN bumped_at IS NOT NULL AND bumped_at > TIMESTAMPADD(HOUR, -24, CURRENT_TIMESTAMP) THEN 1
         ELSE 2 END AS exposure_priority
FROM sales s LEFT JOIN student st ON st.unisno=s.seller AND (s.seller_member_id IS NULL OR st.member_id=s.seller_member_id);

-- 기존 Product.java / ProductMapper.java의 조회 컬럼을 유지하는 호환 VIEW입니다.
CREATE OR REPLACE VIEW products AS
SELECT selno AS id, name AS title, current_price AS price, category, location, icon FROM sale_listing;


-- Record only successful owner/manager edits; keep actor snapshots after manager deletion.
CREATE TABLE IF NOT EXISTS shop_change_logs (
    change_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    actor_member_id BIGINT NOT NULL,
    actor_name VARCHAR(20) NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    field_key VARCHAR(40) NOT NULL,
    field_label VARCHAR(50) NOT NULL,
    before_value VARCHAR(500) NOT NULL,
    after_value VARCHAR(500) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_shop_change_shop FOREIGN KEY(shop_id) REFERENCES shops(shopId),
    INDEX ix_shop_change_time (shop_id,changed_at,change_id)
);

CREATE TABLE IF NOT EXISTS admin_action_logs (
    action_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    shop_name VARCHAR(50) NOT NULL,
    actor_member_id BIGINT NOT NULL,
    actor_name VARCHAR(20) NOT NULL,
    action VARCHAR(20) NOT NULL,
    before_value VARCHAR(100) NOT NULL,
    after_value VARCHAR(100) NOT NULL,
    note VARCHAR(300) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_admin_action_shop FOREIGN KEY(shop_id) REFERENCES shops(shopId),
    INDEX ix_admin_action_time (created_at,action_id)
);

-- v14: reservation notification outbox (all delivery is disabled by default).
CREATE TABLE IF NOT EXISTS seller_notifications (
 notification_id VARCHAR(36) PRIMARY KEY,
 order_id VARCHAR(64) NOT NULL,
 seller_member_id BIGINT NOT NULL,
 sale_id BIGINT NOT NULL,
 title VARCHAR(150) NOT NULL,
 message VARCHAR(1500) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 read_at TIMESTAMP(6),
 CONSTRAINT fk_notification_order FOREIGN KEY(order_id) REFERENCES trade_orders(order_id),
 CONSTRAINT uq_notification_order_recipient UNIQUE(order_id,seller_member_id),
    INDEX ix_notification_seller (seller_member_id,created_at)
);
CREATE TABLE IF NOT EXISTS notification_deliveries (
 delivery_id VARCHAR(36) PRIMARY KEY,
 notification_id VARCHAR(36) NOT NULL,
 channel VARCHAR(10) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 attempted_at TIMESTAMP(6),
 last_error VARCHAR(300),
 CONSTRAINT uq_notification_channel UNIQUE(notification_id,channel),
 CONSTRAINT ck_notification_channel CHECK(channel IN ('SMS','EMAIL','KAKAO')),
 CONSTRAINT fk_delivery_notification FOREIGN KEY(notification_id) REFERENCES seller_notifications(notification_id),
    INDEX ix_notification_pending (status)
);
CREATE TABLE IF NOT EXISTS notification_settings (
 member_id BIGINT PRIMARY KEY,
 email VARCHAR(100),phone VARCHAR(20),
 sms_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 kakao_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- Teammate five-score manner review table. Existing student/member tables stay unchanged.
CREATE TABLE IF NOT EXISTS trade_reviews (
                                             review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             order_id VARCHAR(50) NOT NULL,
    reviewer_id VARCHAR(50) NOT NULL,
    target_id VARCHAR(50) NOT NULL,
    score INT NOT NULL,
    attitude VARCHAR(50),
    content VARCHAR(500),
    temperature_delta INT NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_trade_review UNIQUE (order_id, reviewer_id)
    );

-- Likes and private bookmarks
-- Additive migration for existing H2/MySQL databases. Keep existing content intact.
CREATE TABLE IF NOT EXISTS content_reactions (
    member_id BIGINT NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(member_id,target_type,target_id,kind),
    CONSTRAINT ck_reaction_type CHECK(target_type IN ('SALE','POST','SHOP','COMMENT')),
    CONSTRAINT ck_reaction_kind CHECK(kind IN ('LIKE','BOOKMARK')),
    INDEX ix_reaction_target (target_type,target_id,kind)
);
CREATE OR REPLACE VIEW engagement_targets AS
 SELECT 'SALE' AS target_type,selno AS target_id,name AS title,location AS subtitle,current_price AS price,photo1 AS photo,selno AS parent_id,school_id FROM sale_listing
 UNION ALL SELECT 'POST',post_id,title,SUBSTRING(content,1,160),NULL,NULL,post_id,school_id FROM board_posts
 UNION ALL SELECT 'SHOP',shopId,shopname,address,NULL,photo,shopId,school_id FROM shops WHERE checked=TRUE
 UNION ALL SELECT 'COMMENT',comment_id,SUBSTRING(c.content,1,100),p.title,NULL,NULL,c.post_id,p.school_id FROM board_comments c JOIN board_posts p ON p.post_id=c.post_id;

CREATE TABLE IF NOT EXISTS catalog_meta(name VARCHAR(60) PRIMARY KEY);
CREATE TABLE IF NOT EXISTS reservation_requests (
    buyer_member_id BIGINT NOT NULL,
    request_key VARCHAR(80) NOT NULL,
    request_payload TEXT NOT NULL,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    response_json TEXT NOT NULL,
    PRIMARY KEY (buyer_member_id, request_key)
);
CREATE TABLE IF NOT EXISTS business_categories(category_id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(50) NOT NULL UNIQUE,version BIGINT NOT NULL DEFAULT 0);
