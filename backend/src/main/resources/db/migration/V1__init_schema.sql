-- V1: 核心資料表（Users / Managers / Locations / Assets / AssetImages）
-- UUID 由應用程式產生；時間欄位以 UTC 儲存。
-- 此 SQL 同時相容 PostgreSQL 18 與 H2（PostgreSQL mode，僅供測試）。

CREATE TABLE users (
    id          UUID PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    pin_hash    VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE managers (
    id            UUID PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL,
    user_id       UUID NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_managers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE locations (
    id           UUID PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500) NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_locations_name UNIQUE (name)
);

CREATE TABLE assets (
    id                  UUID PRIMARY KEY,
    asset_code          VARCHAR(64)  NOT NULL,
    item_name           VARCHAR(200) NOT NULL,
    category            VARCHAR(100) NULL,
    manager_id          UUID NULL,
    service_start_date  DATE NULL,
    useful_life_years   NUMERIC(4, 1) NULL,
    location_id         UUID NULL,
    color               VARCHAR(50)  NULL,
    brand               VARCHAR(100) NULL,
    model               VARCHAR(100) NULL,
    specification       VARCHAR(2000) NULL,
    quantity            INTEGER NOT NULL DEFAULT 1,
    unit                VARCHAR(20)  NULL,
    description         VARCHAR(2000) NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at          TIMESTAMP WITH TIME ZONE NULL,
    -- asset_code 在所有紀錄（含 Soft Deleted）中唯一，避免日後匯入比對混淆
    CONSTRAINT uk_assets_asset_code UNIQUE (asset_code),
    CONSTRAINT ck_assets_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_assets_useful_life_positive CHECK (useful_life_years IS NULL OR useful_life_years > 0),
    CONSTRAINT fk_assets_manager  FOREIGN KEY (manager_id)  REFERENCES managers (id),
    CONSTRAINT fk_assets_location FOREIGN KEY (location_id) REFERENCES locations (id)
);

CREATE INDEX idx_assets_item_name   ON assets (item_name);
CREATE INDEX idx_assets_manager_id  ON assets (manager_id);
CREATE INDEX idx_assets_location_id ON assets (location_id);
CREATE INDEX idx_assets_deleted_at  ON assets (deleted_at);

CREATE TABLE asset_images (
    id                 UUID PRIMARY KEY,
    asset_id           UUID NOT NULL,
    object_key         VARCHAR(500) NOT NULL,
    original_filename  VARCHAR(255) NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_images_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT uk_asset_images_object_key UNIQUE (object_key)
);

CREATE INDEX idx_asset_images_asset_id ON asset_images (asset_id);
