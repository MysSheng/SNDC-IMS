-- V4：配合主管 Excel（財產管理個人清單）重新設計資產資料（規格書 v1.6 第 4.2、4.6 節）
--   1. 清空資產、照片、保管人、存放位置（皆為測試資料）；保留 users（已註冊帳號）。
--   2. 加回 Excel 有的欄位：規格及摘要、購置類型／經費來源、單位、台幣價值、美金價值。
--   3. 新增盤點期別（年度＋季度）；同一筆資產可屬於多個期別（例如 2026Q3 與 2026Q4 的清單都有它）。
--   4. 保管人名稱改為唯一，匯入時才能以名稱對應。
-- 使用年限改為選填由 API 驗證處理（資料庫本來就允許 NULL）。

DELETE FROM asset_images;
DELETE FROM assets;
DELETE FROM managers;
DELETE FROM locations;

ALTER TABLE assets ADD COLUMN specification    VARCHAR(2000) NULL;
ALTER TABLE assets ADD COLUMN acquisition_type VARCHAR(100)  NULL;
ALTER TABLE assets ADD COLUMN unit             VARCHAR(20)   NULL;
ALTER TABLE assets ADD COLUMN value_twd        NUMERIC(14, 2) NULL;
ALTER TABLE assets ADD COLUMN value_usd        NUMERIC(14, 2) NULL;

ALTER TABLE managers ADD CONSTRAINT uk_managers_display_name UNIQUE (display_name);

CREATE TABLE inventory_periods (
    id              UUID PRIMARY KEY,
    period_year     INTEGER NOT NULL,
    period_quarter  INTEGER NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_inventory_periods_year_quarter UNIQUE (period_year, period_quarter),
    CONSTRAINT ck_inventory_periods_year CHECK (period_year BETWEEN 2000 AND 2999),
    CONSTRAINT ck_inventory_periods_quarter CHECK (period_quarter BETWEEN 1 AND 4)
);

CREATE TABLE asset_periods (
    asset_id   UUID NOT NULL,
    period_id  UUID NOT NULL,
    CONSTRAINT pk_asset_periods PRIMARY KEY (asset_id, period_id),
    CONSTRAINT fk_asset_periods_asset  FOREIGN KEY (asset_id)  REFERENCES assets (id),
    CONSTRAINT fk_asset_periods_period FOREIGN KEY (period_id) REFERENCES inventory_periods (id)
);

CREATE INDEX idx_asset_periods_period_id ON asset_periods (period_id);
