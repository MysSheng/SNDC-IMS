-- V2: 依專案負責人 2026-11-15 的需求調整資產欄位（規格書 v1.3 第 4.2 節）
--   新增：tech_mission（所屬技術團）
--   更名：service_start_date → purchase_date（購置日期）
--   刪除：category、brand、model、specification、unit
-- 必填欄位（購置日期、存放位置、使用年限、保管人）由 API 驗證強制；
-- 資料庫暫不加 NOT NULL，避免既有測試資料使 migration 失敗。

ALTER TABLE assets ADD COLUMN tech_mission VARCHAR(100) NULL;

ALTER TABLE assets RENAME COLUMN service_start_date TO purchase_date;

ALTER TABLE assets DROP COLUMN category;
ALTER TABLE assets DROP COLUMN brand;
ALTER TABLE assets DROP COLUMN model;
ALTER TABLE assets DROP COLUMN specification;
ALTER TABLE assets DROP COLUMN unit;

CREATE INDEX idx_assets_tech_mission ON assets (tech_mission);
