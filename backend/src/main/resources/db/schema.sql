-- ============================================================
-- 智能导购 Agent 电商平台 · 通用建表脚本
-- 兼容 MySQL 8.0 与 H2 2.x (MODE=MySQL)
-- JSON 字段统一以 TEXT 存储，由应用层序列化，便于跨库迁移
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id          VARCHAR(32)  PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50),
    email       VARCHAR(100),
    phone       VARCHAR(20),
    avatar      VARCHAR(200),
    role        VARCHAR(20)  DEFAULT 'USER',
    status      TINYINT      DEFAULT 1,
    created_at  DATETIME,
    updated_at  DATETIME
);

CREATE TABLE IF NOT EXISTS user_address (
    id          VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    receiver    VARCHAR(50) NOT NULL,
    phone       VARCHAR(20) NOT NULL,
    province    VARCHAR(50),
    city        VARCHAR(50),
    district    VARCHAR(50),
    detail      VARCHAR(200),
    is_default  TINYINT DEFAULT 0,
    created_at  DATETIME
);

CREATE TABLE IF NOT EXISTS user_preference (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          VARCHAR(32) NOT NULL,
    preference_key   VARCHAR(50) NOT NULL,
    preference_value VARCHAR(200),
    weight           DECIMAL(3,2) DEFAULT 1.00,
    updated_at       DATETIME
);

CREATE TABLE IF NOT EXISTS browse_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    product_id  VARCHAR(32) NOT NULL,
    created_at  DATETIME
);

CREATE TABLE IF NOT EXISTS user_favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    product_id  VARCHAR(32) NOT NULL,
    created_at  DATETIME,
    UNIQUE (user_id, product_id)
);

CREATE TABLE IF NOT EXISTS category (
    id                VARCHAR(32) PRIMARY KEY,
    parent_id         VARCHAR(32),
    name              VARCHAR(50) NOT NULL,
    icon              VARCHAR(20),
    description       VARCHAR(200),
    attribute_schema  TEXT,
    sort_order        INT DEFAULT 0,
    status            TINYINT DEFAULT 1
);

-- 品牌档案：首页品牌条点击后展示品牌介绍与该品牌全部商品
CREATE TABLE IF NOT EXISTS brand (
    id            VARCHAR(32) PRIMARY KEY,
    name          VARCHAR(50) NOT NULL,
    slogan        VARCHAR(100),
    origin        VARCHAR(50),
    founded       VARCHAR(20),
    positioning   VARCHAR(100),
    intro         TEXT,
    story         TEXT,
    highlight     VARCHAR(200),
    logo_text     VARCHAR(10),
    accent        VARCHAR(20),
    sort_order    INT DEFAULT 0,
    status        TINYINT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS product (
    id              VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    subtitle        VARCHAR(200),
    category_id     VARCHAR(32),
    brand           VARCHAR(50),
    price           DECIMAL(10,2) NOT NULL,
    original_price  DECIMAL(10,2),
    stock           INT DEFAULT 0,
    sales           INT DEFAULT 0,
    rating          DECIMAL(2,1) DEFAULT 5.0,
    review_count    INT DEFAULT 0,
    description     TEXT,
    attributes      TEXT,
    tags            VARCHAR(200),
    image           VARCHAR(200),
    image_fallback VARCHAR(255),
    seller_id     VARCHAR(32),
    status          TINYINT DEFAULT 1,
    created_at      DATETIME,
    updated_at      DATETIME
);

CREATE TABLE IF NOT EXISTS product_image (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  VARCHAR(32) NOT NULL,
    url         VARCHAR(200),
    sort_order  INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS product_review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  VARCHAR(32) NOT NULL,
    user_id     VARCHAR(32),
    username    VARCHAR(50),
    rating      INT DEFAULT 5,
    content     VARCHAR(500),
    status      TINYINT DEFAULT 1,
    created_at  DATETIME
);

CREATE TABLE IF NOT EXISTS cart_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    product_id  VARCHAR(32) NOT NULL,
    quantity    INT DEFAULT 1,
    selected    TINYINT DEFAULT 1,
    created_at  DATETIME,
    updated_at  DATETIME,
    UNIQUE (user_id, product_id)
);

CREATE TABLE IF NOT EXISTS orders (
    id            VARCHAR(32) PRIMARY KEY,
    order_no      VARCHAR(40) NOT NULL UNIQUE,
    user_id       VARCHAR(32) NOT NULL,
    total_amount  DECIMAL(10,2) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    address_id    VARCHAR(32),
    receiver      VARCHAR(50),
    phone         VARCHAR(20),
    address_text  VARCHAR(300),
    remark        VARCHAR(200),
    carrier       VARCHAR(50),
    tracking_no   VARCHAR(64),
    payment_no     VARCHAR(40),
    payment_channel VARCHAR(20),
    delivery_method VARCHAR(20),
    delivery_fee  DECIMAL(10,2) DEFAULT 0,
    created_at    DATETIME,
    paid_at       DATETIME,
    shipped_at    DATETIME,
    finished_at   DATETIME,
    expired_at    DATETIME
);

CREATE TABLE IF NOT EXISTS order_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        VARCHAR(32) NOT NULL,
    product_id      VARCHAR(32) NOT NULL,
    product_name    VARCHAR(200),
    product_image   VARCHAR(200),
    product_price   DECIMAL(10,2),
    quantity        INT,
    -- 发货归属：下单时按商品的 seller_id 固化，支持多卖家订单各自发货
    seller_id       VARCHAR(32),
    ship_status     VARCHAR(32) DEFAULT 'PENDING',
    carrier         VARCHAR(64),
    tracking_no     VARCHAR(64),
    shipped_at      DATETIME,
    refund_status   VARCHAR(32) DEFAULT 'NONE'
);

CREATE TABLE IF NOT EXISTS conversation (
    id          VARCHAR(32) PRIMARY KEY,
    user_id     VARCHAR(32),
    title       VARCHAR(200),
    slots       TEXT,
    status      TINYINT DEFAULT 1,
    created_at  DATETIME,
    updated_at  DATETIME
);

CREATE TABLE IF NOT EXISTS dialogue_turn (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id  VARCHAR(32) NOT NULL,
    role             VARCHAR(20) NOT NULL,
    content          TEXT,
    intent           VARCHAR(50),
    slots            TEXT,
    products         TEXT,
    task_id          VARCHAR(32),
    created_at       DATETIME
);

CREATE TABLE IF NOT EXISTS agent_execution_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id          VARCHAR(32) NOT NULL,
    conversation_id  VARCHAR(32),
    agent_id         VARCHAR(50),
    step             VARCHAR(80),
    step_name        VARCHAR(100),
    input            TEXT,
    output           TEXT,
    status           VARCHAR(20),
    duration_ms      INT,
    created_at       DATETIME
);

CREATE TABLE IF NOT EXISTS compare_record (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      VARCHAR(32),
    product_ids  TEXT,
    result       TEXT,
    created_at   DATETIME
);

CREATE TABLE IF NOT EXISTS after_sale (
    id           VARCHAR(32) PRIMARY KEY,
    order_id     VARCHAR(32) NOT NULL,
    user_id      VARCHAR(32) NOT NULL,
    type         VARCHAR(20) NOT NULL,
    reason       VARCHAR(500),
    status       VARCHAR(20) NOT NULL,
    admin_note   VARCHAR(300),
    created_at   DATETIME,
    handled_at   DATETIME
);

CREATE TABLE IF NOT EXISTS agent_prompt (
    prompt_key   VARCHAR(50) PRIMARY KEY,
    name         VARCHAR(100),
    description  VARCHAR(300),
    content      TEXT,
    updated_at   DATETIME
);

CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(32) NOT NULL,
    type        VARCHAR(30),
    title       VARCHAR(100),
    content     VARCHAR(500),
    link        VARCHAR(200),
    read_status TINYINT DEFAULT 0,
    created_at  DATETIME
);

CREATE TABLE IF NOT EXISTS payment_record (
    id          VARCHAR(32) PRIMARY KEY,
    payment_no  VARCHAR(40) NOT NULL UNIQUE,
    order_id    VARCHAR(32) NOT NULL,
    user_id     VARCHAR(32) NOT NULL,
    amount      DECIMAL(10,2),
    channel     VARCHAR(20),
    status      VARCHAR(20),
    created_at  DATETIME,
    paid_at     DATETIME
);
