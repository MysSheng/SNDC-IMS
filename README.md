# SNDC-IMS 智慧辦公室管理系統

索馬利蘭外交替代役團部內部使用的資產管理系統（約 15 人）。
完整需求見 [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md)（v1.5）。

**部署方式：本機架設（零成本）。** Server、PostgreSQL 與照片都在團部的一台 Windows 電腦上，以 Windows 服務在背景執行。手機透過 Tailscale 私有網路以 HTTPS 連線，在團部內外都能使用。Server 程式結束或電腦重開機，資料都不會遺失（資料存在 PostgreSQL 的磁碟檔案中）。

## 目錄結構

```
SNDC_IMS/
├── app/                 Android App（Kotlin + Jetpack Compose + MVVM）
├── backend/             Spring Boot 後端（獨立 Gradle 專案）
│   ├── src/main/java/icdf/sndc/ims/
│   │   ├── asset/       資產 CRUD、查詢、到期日計算
│   │   ├── location/    存放位置
│   │   ├── manager/     資產保管人
│   │   ├── ai/          AI 助理（Groq）：自然語言 → 驗證 → 查詢／待確認動作
│   │   ├── auth/        註冊、登入、登入失敗次數限制
│   │   ├── security/    Spring Security、JWT
│   │   ├── user/        App 登入帳號
│   │   ├── image/       資產照片中繼資料（Phase 3 實作 API）
│   │   ├── health/      健康檢查
│   │   ├── config/      設定（時區、分頁）
│   │   └── common/      統一錯誤格式、文字正規化、分頁
│   ├── src/main/resources/db/migration/   Flyway migration
│   ├── .env.example     環境變數範本
│   └── docker-compose.yml  本機 PostgreSQL（可選）
└── docs/
    ├── PROJECT_SPEC.md  規格書
    └── API.md           REST API 文件
```

Android 專案維持在根目錄，避免影響 Android Studio 既有設定；`backend/` 是獨立的 Gradle build，需另外開啟或在終端機執行。

## 技術版本

| 項目 | 版本 |
|---|---|
| Java | 17 以上（建議使用 Android Studio 內建的 JBR 21） |
| Spring Boot | 4.1.2（OSS 支援至 2027-07-31） |
| Flyway | 由 Spring Boot 管理（`spring-boot-starter-flyway`） |
| PostgreSQL | 18（官方支援至 2030-11-14） |
| springdoc-openapi | 3.2.0 |
| Gradle Wrapper | 8.14.3（第一次執行會自動下載） |
| 測試資料庫 | H2（PostgreSQL 相容模式，只用於自動化測試） |

## 本機啟動後端（Windows PowerShell）

### 1. 指定 JDK

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 2. 準備 PostgreSQL

任選一種方式：

- **直接安裝 PostgreSQL 18**（Windows 建議使用 postgresql.org 下載頁的 EDB 安裝程式），安裝後用 pgAdmin 或 psql 建立資料庫：
  ```sql
  CREATE USER sndc WITH PASSWORD 'change-me';
  CREATE DATABASE sndc_ims OWNER sndc;
  ```
- **Docker**（需另外安裝 Docker Desktop）：`cd backend; docker compose up -d`

### 3. 設定環境變數

```powershell
cd backend
Copy-Item .env.example .env   # 再編輯 .env 填入密碼
```

**必填**：`JWT_SECRET`（至少 32 個字元的隨機字串，產生方式寫在 `.env.example` 裡）和 `DEPARTMENT_REGISTRATION_CODE`（團部通行碼）。缺少任何一項，後端都會拒絕啟動並顯示原因。

`.env` 會由 Spring Boot 自動讀取（`spring.config.import`），且已被 `.gitignore` 排除。系統環境變數的優先權高於 `.env`。

### 4. 執行

```powershell
.\gradlew.bat bootRun
```

啟動時 Flyway 會自動執行 migration 建立資料表。

後端預設只監聽 `127.0.0.1`，同一個區網的其他裝置無法直接連入。開發時的連線方式：

- 本機瀏覽器：`http://localhost:8080`
- Android 模擬器：`http://10.0.2.2:8080`（模擬器用這個位址連到電腦本機）
- 實體手機：透過 Tailscale（見下方「本機架設」）

- 健康檢查：<http://localhost:8080/api/v1/health>
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>

## 執行 Android App（開發）

1. 先在電腦上用 `bootRun` 啟動後端。
2. 用 Android Studio 開啟專案根目錄，等待 Gradle Sync 完成。
3. 選擇 Android 模擬器並按 Run。debug 版預設連到 `http://10.0.2.2:8080/api/v1/`，也就是模擬器所在電腦的後端。
4. 若要改連其他位址（例如之後透過 Tailscale 連線），在 `local.properties` 加入：
   ```properties
   sndc.debugApiBaseUrl=http://10.0.2.2:8080/api/v1/
   sndc.releaseApiBaseUrl=https://<電腦名稱>.<tailnet>.ts.net/api/v1/
   ```

App 的行為：
- 會記住上次登入的使用者名稱，但**不保存 PIN，也不保存 Token**。App 關閉後要重新輸入 PIN。
- debug 版只允許對 `10.0.2.2` 和 `localhost` 使用 HTTP；正式版只允許 HTTPS。

## 本機架設（Phase 6，規劃）

```
手機 App ──HTTPS──▶ Tailscale ──▶ tailscale serve ──▶ http://127.0.0.1:8080 Spring Boot（Windows 服務）
                                                         ├─▶ PostgreSQL 18（Windows 服務）
                                                         └─▶ 照片資料夾
```

1. PostgreSQL 18 的安裝程式會自動建立開機啟動的 Windows 服務。
2. Backend 會用 WinSW 包成 Windows 服務，開機自動啟動，當掉時自動重啟。
3. 安裝 Tailscale 並啟用 HTTPS，然後執行：`tailscale serve --bg localhost:8080`。之後手機只要連到 `https://<電腦名稱>.<tailnet>.ts.net` 即可。這個設定在重開機後仍然有效。
4. 每天由 Windows 工作排程執行 `pg_dump`，並把照片資料夾一起複製到另一個儲存裝置。
5. 這台電腦需關閉睡眠。不要在路由器開放對外連接埠，也不要使用 Tailscale Funnel。

Tailscale 免費方案可加入 6 位使用者。超過時，用「Device sharing」把 Server 電腦分享給其他成員，對方用自己的免費帳號登入即可，不佔使用者名額。

## 測試

```powershell
cd backend
.\gradlew.bat test
```

- 測試使用 H2 記憶體資料庫，**不需要** PostgreSQL。
- 測試範圍：註冊／登入（通行碼、PIN 確認、前導零、PIN 雜湊、錯誤 PIN、登入失敗鎖定、未登入回傳 401）、資產 CRUD、Soft Delete、`asset_code` 重複、`quantity` 驗證、日期格式、分頁與排序白名單、位置／保管人建立、必填欄位、購置年份、到期日計算、文字正規化、健康檢查。
- 報告：`backend/build/reports/tests/test/index.html`
- Android 單元測試：在專案根目錄執行 `.\gradlew.bat :app:testDebugUnitTest`

## 開發進度

| Phase | 狀態 |
|---|---|
| 0 Repository 檢查與規劃 | ✅ 完成 |
| 1 Backend 骨架與資料庫 | ✅ 完成（本機啟動與 PostgreSQL 連線已確認） |
| 2 認證與 Android 基礎 | ✅ 完成（本機與 App 測試通過） |
| 3 資產管理 | 🔧 App 手動 CRUD 與多條件查詢完成；重複統計、照片尚未開始 |
| 4 AI Agent（Groq） | 🔧 程式碼完成，待本機測試 |
| 5 Polish & Testing | ⏳ 未開始 |
| 6 Deployment（本機架設） | ⏳ 未開始 |

### Phase 1 已完成

- Flyway `V1__init_schema.sql`：`users`、`managers`、`locations`、`assets`、`asset_images`。
- 資產 CRUD API（含 Soft Delete、`asset_code` 唯一、`quantity > 0`、預計到期日計算）。
- 基本條件查詢：資產編號、品項名稱（部分符合）、分類、顏色、管理人、位置，以及分頁和排序白名單。
- 位置、管理人的清單與新增 API。
- 統一錯誤格式、輸入驗證、健康檢查。

### Phase 2 已完成（程式碼）

- 後端：`/auth/register`、`/auth/login`、`/auth/me`，使用 Spring Security 和 JWT（HS256）。PIN 以 BCrypt 雜湊儲存，團部通行碼以固定時間比對，並有登入失敗次數限制。除註冊、登入、健康檢查和 API 文件外，其他 API 都需要登入。
- Android：從 Views 範本改為 Jetpack Compose，採用 MVVM 架構，使用 Retrofit 和 kotlinx.serialization。畫面有登入、註冊（四個欄位）和首頁（兩個入口）。智慧辦公室與智慧導航目前都是說明頁，前者會在 Phase 3 完成。App 會記住使用者名稱，並分別處理 Loading 與錯誤狀態。

### Phase 3（進行中）已完成

- 資產欄位依 v1.3 調整（Flyway `V2__asset_fields_revision.sql`）：新增「所屬技術團」，「服役起始日」改為「購置日期」，刪除分類、品牌、型號、規格、單位；編號、名稱、購置日期、存放位置、使用年限、保管人為必填。
- App 的「智慧辦公室管理系統」：
  - 資產清單：顯示總筆數，可搜尋名稱或編號，可載入更多。
  - 資產詳情：顯示預計到期日。
  - 新增／修改表單：存放位置與保管人可直接在選單中新增。
  - 刪除前會先確認。
  - CRUD 按鈕提供新增、查詢、修改、刪除四個選項；選「修改」或「刪除」後，再點選清單中的資產即可操作。
- 所有資料都經後端 API 寫入 PostgreSQL，App 本身不保存資產資料。
- v1.4：
  - 所屬技術團改為必填。
  - 必填欄位未填時，App 會跳出視窗列出缺少的欄位。
  - 購置日期改用日曆選擇。
  - 清單新增「篩選條件」：存放位置、保管人、使用年限狀態（已過使用年限、90 天內到期、使用年限內），可以與關鍵字同時使用。
  - 清單與詳情會標示已過使用年限或 90 天內到期的資產。

### Phase 4（AI 助理）已完成（程式碼）

- 在智慧辦公室管理系統畫面點右下角的「小管家」頭像，即可用自然語言查詢、統計、找重複品項，以及修改或刪除資產（可批次）。
- 修改與刪除會先跳出確認視窗，列出所有受影響的資產，按「確認」後才執行。新增資產仍須手動。
- 查詢結果可按「在清單中顯示」，把同樣的條件套用到主清單。
- 資產詳情頁新增「導引」按鈕，是室內導引的預留入口，目前顯示功能尚未開放。
- 設定方式：在 `backend/.env` 填入 `GROQ_API_KEY`（到 <https://console.groq.com/keys> 建立）。沒有設定也不影響其他功能。

### 尚未完成（依規格排在後續 Phase）

- App 的重複品項畫面（目前可透過小管家查詢）。App 尚未提供購置日期範圍和數量下限的篩選畫面（後端已支援）。
- 照片上傳 API 與 Storage Provider（Phase 3）。
- AssetHistory／AuditLogs：第一版目前只有 `created_at`、`updated_at`、`deleted_at`，完整歷史紀錄列為後續項目。

## Assumptions / Pending Decisions

以下是為了推進開發而採用的**暫定值**，不代表使用者已確認：

| 項目 | 暫定做法 | 修改位置 |
|---|---|---|
| 保管人是否必須是 App 使用者 | 不必須；`managers.user_id` 選填 | `manager/`、V1 migration |
| `asset_code`（編號）編碼規則 | 不限格式，最長 64 字元，正規化後全域唯一（含已刪除紀錄） | `AssetDtos.AssetRequest` |
| 文字正規化 | Unicode NFKC（全形英數轉半形）＋去除前後空白；不轉換大小寫，不使用同義詞 | `common/TextNormalizer` |
| 使用年限 | 最多一位小數（例如 2.5 年），以四捨五入後的月數計算到期日 | `asset/ExpiryCalculator` |
| 位置名稱 | 正規化後唯一 | V1 migration |
| 保管人名稱 | 不要求唯一（可能有同名的人） | — |
| Server 時區 | `APP_TIMEZONE`，預設 UTC；資料庫時間一律使用 UTC | `config/TimeConfig` |
| `specification`／`description` | 使用 `VARCHAR(2000)`，不用 `TEXT`，以便與 H2 測試相容 | V1 migration |
| JSON 欄位命名 | camelCase | — |
| 所屬技術團 | 必填、自由輸入文字；App 新增時預先填入「駐索馬利蘭技術團」 | `AssetFormUiState` |
| AI 模型 | `openai/gpt-oss-120b`（Groq，可用 `GROQ_MODEL` 更換） | `.env` |
| AI 批次上限 | 刪除 100 筆、修改 50 筆；待確認動作 10 分鐘失效 | `AgentService`、`PendingActionStore` |
| 「即將到期」範圍 | 90 天 | `AssetService.EXPIRING_SOON_DAYS` |
| 已過使用年限的判斷 | 預計到期日 ≤ 今天（伺服器時區） | `ExpiryStatus` |
| 必填欄位的強制方式 | 由 API 驗證；資料庫欄位暫不加 NOT NULL，避免 V2 migration 因既有測試資料失敗 | V2 migration |
| 使用者名稱 | 最多 30 字、NFKC 正規化、區分大小寫 | `auth/AuthService` |
| 登入失敗限制 | 同一使用者名稱連續失敗 5 次暫停 15 分鐘（記憶體保存，重啟清空）。理由：4 位數 PIN 只有一萬種組合 | `app.auth.*` |
| Token 保存 | App 只把 Token 存在記憶體，不寫入磁碟；沒有 Refresh Token | `SessionManager` |
| Token 有效期限 | 12 小時 | `JWT_EXPIRATION` |
| 位置清單由誰維護 | API 開放所有已登入使用者；實際管理流程待團部決定 | — |
| 部署 | 本機架設 + Tailscale（已定案）；由哪台電腦擔任 Server、Tailscale 帳號擁有者、備份位置尚待決定 | Phase 6 |
