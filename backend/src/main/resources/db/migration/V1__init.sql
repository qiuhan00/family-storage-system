-- ============================================================================
-- V1__init.sql — 家庭收纳物品管理系统初始 schema（MySQL 5.7 兼容）
-- 数据模型见 spec §4；utf8mb4 下严格遵守索引列长度限制（<=100 字符，
-- 超长字段使用前缀索引，如 items.name 的 name(64)），规避 767 字节限制。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. users 成员
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',  -- ADMIN / MEMBER
    email         VARCHAR(100) NULL,
    phone         VARCHAR(20)  NULL,
    enabled       BIT          NOT NULL DEFAULT b'1',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 2. categories 物品分类
-- ----------------------------------------------------------------------------
CREATE TABLE categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 3. locations 收纳位置（自引用层级）
-- ----------------------------------------------------------------------------
CREATE TABLE locations (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    parent_id  BIGINT      NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_locations_parent_id (parent_id),
    CONSTRAINT fk_locations_parent FOREIGN KEY (parent_id) REFERENCES locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 4. items 物品
-- ----------------------------------------------------------------------------
CREATE TABLE items (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100)  NOT NULL,
    category_id    BIGINT        NOT NULL,
    location_id    BIGINT        NOT NULL,
    quantity       INT           NOT NULL DEFAULT 0,
    unit           VARCHAR(20)   NULL,
    purchase_price DECIMAL(10,2) NULL,
    purchase_date  DATE          NULL,
    expiry_date    DATE          NULL,
    photo_url      VARCHAR(255)  NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / ARCHIVED
    remark         VARCHAR(500)  NULL,
    created_by     BIGINT        NOT NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_items_name (name(64)),
    KEY idx_items_expiry (expiry_date),
    KEY idx_items_category_location (category_id, location_id),
    KEY idx_items_created_by (created_by),
    CONSTRAINT fk_items_category  FOREIGN KEY (category_id)  REFERENCES categories (id),
    CONSTRAINT fk_items_location  FOREIGN KEY (location_id)  REFERENCES locations (id),
    CONSTRAINT fk_items_created_by FOREIGN KEY (created_by)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 5. stock_changes 出入库记录（审计）
-- ----------------------------------------------------------------------------
CREATE TABLE stock_changes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    item_id    BIGINT       NOT NULL,
    change_qty INT          NOT NULL,          -- 正入负出
    type       VARCHAR(20)  NOT NULL,          -- INITIAL / IN / OUT / ADJUST
    note       VARCHAR(200) NULL,
    created_by BIGINT       NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_changes_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 6. shopping_items 缺耗品 / 购物清单
-- ----------------------------------------------------------------------------
CREATE TABLE shopping_items (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    quantity     INT          NOT NULL DEFAULT 1,
    unit         VARCHAR(20)  NULL,
    category_id  BIGINT       NULL,
    remark       VARCHAR(200) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / BOUGHT / CANCELLED
    created_by   BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shopping_items_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 7. notifications 站内通知
-- ----------------------------------------------------------------------------
CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(100) NOT NULL,
    body         VARCHAR(500) NOT NULL,
    type         VARCHAR(20)  NOT NULL,          -- EXPIRY / OTHER
    ref_id       BIGINT       NULL,
    reminder_key VARCHAR(100) NULL,
    read_flag    BIT          NOT NULL DEFAULT b'0',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_user_reminder (user_id, reminder_key),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 8. app_settings 应用设置
-- ----------------------------------------------------------------------------
CREATE TABLE app_settings (
    `key`   VARCHAR(50)  NOT NULL,
    `value` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 种子：临期提醒阈值（天）
INSERT INTO app_settings (`key`, `value`) VALUES ('expiry.remind.days', '7');