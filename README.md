<a id="chinese"></a>

# SNDC-IMS 智慧辦公室管理系統

**中文** | [English](#english)

索馬利蘭外交替代役團部內部使用的資產管理系統（約 15 人）。
完整需求見 [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md)（v1.6）。App 介面可切換中文／英文（登入頁、首頁右上角的「中文 | EN」）。

**部署方式：本機架設（零成本）。** Server、PostgreSQL 與照片都在團部的一台 Windows 電腦上，以 Windows 服務在背景執行。手機透過 Tailscale 私有網路以 HTTPS 連線，在團部內外都能使用。Server 程式結束或電腦重開機，資料都不會遺失（資料存在 PostgreSQL 的磁碟檔案中）。

## 目錄結構

```
SNDC_IMS/
├── app/                 Android App（Kotlin + Jetpack Compose + MVVM）
├── backend/             Spring Boot 後端（獨立 Gradle 專案）
│   ├── src/main/java/icdf/sndc/ims/
│   │   ├── asset/       資產 CRUD、查詢、到期日計算、Excel／CSV 匯入與匯出
│   │   ├── period/      盤點期別（年度＋季度）
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
| Apache POI | 5.5.1（讀寫 Excel） |
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
- 介面語言（中文／EN）存在手機上，下次開啟沿用。介面文字寫在使用處，例如 `tr("登入", "Log in")`（`ui/common/I18n.kt`）；新增畫面時請一併提供英文。後端回傳的文字（AI 助理回覆、匯入錯誤說明）目前仍為中文。
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
- 測試範圍：註冊／登入（通行碼、PIN 確認、前導零、PIN 雜湊、錯誤 PIN、登入失敗鎖定、未登入回傳 401）、資產 CRUD、Soft Delete、`asset_code` 重複、`quantity` 驗證、日期格式、分頁與排序白名單、位置／保管人建立、必填欄位、購置年份、到期日計算、文字正規化、盤點期別篩選、Excel／CSV 匯入（預覽、確認、錯誤、Big5）、勾選匯出、AI 助理、健康檢查。
- 匯入測試的 Excel 由程式產生，不使用真實清單。
- 報告：`backend/build/reports/tests/test/index.html`
- Android 單元測試：在專案根目錄執行 `.\gradlew.bat :app:testDebugUnitTest`

## 開發進度

| Phase | 狀態 |
|---|---|
| 0 Repository 檢查與規劃 | ✅ 完成 |
| 1 Backend 骨架與資料庫 | ✅ 完成（本機啟動與 PostgreSQL 連線已確認） |
| 2 認證與 Android 基礎 | ✅ 完成（本機與 App 測試通過） |
| 3 資產管理 | 🔧 CRUD、多條件查詢、盤點期別、Excel 匯入匯出完成；照片尚未開始 |
| 4 AI Agent（Groq） | ✅ 完成（本機測試通過） |
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

### v1.6 已完成（程式碼）：主管 Excel 匯入匯出、盤點期別、中英切換

- 資產欄位對照主管的「財產管理個人清單」（Flyway `V4__excel_import_and_periods.sql`）：加回規格及摘要、單位，新增購置類型、價值（台幣／美金）；使用年限改為選填。**V4 會清空資產、照片、保管人、存放位置（測試資料），保留帳號。**
- 清單上方的「年度 → 季度」下拉選單，預設全部。
- CRUD →「匯入 Excel／CSV」：直接上傳主管的 `.xlsx`，先檢查再確認，有任何錯誤整批都不寫入。
- 資產卡片左上角的勾選方框 →「匯出」：產生主管清單格式的 `.xlsx`。
- 介面中文／EN 切換。
- 財產清單含保管人姓名：`.gitignore` 排除 `*.xlsx`、`*.xls`、`*.csv`，**不要提交到 GitHub**。

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
| 使用年限 | 選填（主管 Excel 沒有此欄）；最多一位小數，以四捨五入後的月數計算到期日 | `asset/ExpiryCalculator` |
| Excel 只有購置年月 | 存為該月 1 日；匯出時拆回年度、月份 | `AssetImportService` |
| 位置名稱 | 正規化後唯一 | V1 migration |
| 保管人名稱 | v1.6 起唯一（匯入時以姓名對應保管人） | V4 migration |
| Server 時區 | `APP_TIMEZONE`，預設 UTC；資料庫時間一律使用 UTC | `config/TimeConfig` |
| `specification`／`description` | 使用 `VARCHAR(2000)`，不用 `TEXT`，以便與 H2 測試相容 | V1 migration |
| JSON 欄位命名 | camelCase | — |
| 所屬技術團 | 必填、自由輸入文字；App 新增時預先填入「駐索馬利蘭技術團」 | `AssetFormUiState` |
| AI 模型 | `openai/gpt-oss-120b`（Groq，可用 `GROQ_MODEL` 更換） | `.env` |
| AI 批次上限 | 刪除 100 筆、修改 50 筆；待確認動作 10 分鐘失效 | `AgentService`、`PendingActionStore` |
| 「即將到期」範圍 | 90 天 | `AssetService.EXPIRING_SOON_DAYS` |
| 已過使用年限的判斷 | 預計到期日 ≤ 今天（伺服器時區） | `ExpiryStatus` |
| 必填欄位的強制方式 | 由 API 驗證；資料庫欄位暫不加 NOT NULL | V2 migration |
| 匯入預覽 | 存在伺服器記憶體 10 分鐘，只有本人能確認一次；檔案上限 10 MB、5000 筆 | `ImportSessionStore` |
| 使用者名稱 | 最多 30 字、NFKC 正規化、區分大小寫 | `auth/AuthService` |
| 登入失敗限制 | 同一使用者名稱連續失敗 5 次暫停 15 分鐘（記憶體保存，重啟清空）。理由：4 位數 PIN 只有一萬種組合 | `app.auth.*` |
| Token 保存 | App 只把 Token 存在記憶體，不寫入磁碟；沒有 Refresh Token | `SessionManager` |
| Token 有效期限 | 12 小時 | `JWT_EXPIRATION` |
| 位置清單由誰維護 | API 開放所有已登入使用者；實際管理流程待團部決定 | — |
| 部署 | 本機架設 + Tailscale（已定案）；由哪台電腦擔任 Server、Tailscale 帳號擁有者、備份位置尚待決定 | Phase 6 |

---

<a id="english"></a>

# SNDC-IMS Smart Office Management System (English)

[中文](#chinese) | **English**

An asset management system used internally by the Taiwan Diplomatic Alternative Service headquarters (團部) in Somaliland, for about 15 users.
Full requirements are in [`docs/PROJECT_SPEC.md`](docs/PROJECT_SPEC.md) (v1.6, written in Chinese); the REST API is in [`docs/API.md`](docs/API.md). The app UI can be switched between Chinese and English (the "中文 | EN" toggle at the top right of the login and home screens).

**Deployment: self-hosted at zero cost.** The server, PostgreSQL and photos all live on one Windows computer in the department and run as Windows services. Phones connect over HTTPS through a private Tailscale network, both inside and outside the office. Data survives a server crash or a reboot because it is stored in PostgreSQL's files on disk.

## Repository layout

```
SNDC_IMS/
├── app/                 Android app (Kotlin + Jetpack Compose + MVVM)
├── backend/             Spring Boot backend (separate Gradle project)
│   ├── src/main/java/icdf/sndc/ims/
│   │   ├── asset/       asset CRUD, search, expiry dates, Excel/CSV import and export
│   │   ├── period/      inventory periods (year + quarter)
│   │   ├── location/    storage locations
│   │   ├── manager/     asset custodians
│   │   ├── ai/          AI assistant (Groq): natural language → validation → query / pending action
│   │   ├── auth/        registration, login, failed-login limit
│   │   ├── security/    Spring Security, JWT
│   │   ├── user/        app login accounts
│   │   ├── image/       asset photo metadata (API not built yet)
│   │   ├── health/      health check
│   │   ├── config/      settings (time zone, paging)
│   │   └── common/      unified error format, text normalization, paging
│   ├── src/main/resources/db/migration/   Flyway migrations
│   ├── .env.example     environment variable template
│   └── docker-compose.yml  local PostgreSQL (optional)
└── docs/
    ├── PROJECT_SPEC.md  specification (Chinese)
    └── API.md           REST API reference (Chinese)
```

The Android project stays at the repository root so Android Studio's existing setup keeps working. `backend/` is a separate Gradle build; open it separately or run it from a terminal.

## Versions

| Item | Version |
|---|---|
| Java | 17 or later (Android Studio's bundled JBR 21 is recommended) |
| Spring Boot | 4.1.2 (OSS support until 2027-07-31) |
| Flyway | managed by Spring Boot (`spring-boot-starter-flyway`) |
| PostgreSQL | 18 (supported until 2030-11-14) |
| springdoc-openapi | 3.2.0 |
| Apache POI | 5.5.1 (Excel read/write) |
| Gradle Wrapper | 8.14.3 (downloaded automatically on first run) |
| Test database | H2 in PostgreSQL mode (automated tests only) |

## Running the backend locally (Windows PowerShell)

### 1. Point to a JDK

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 2. Prepare PostgreSQL

Pick one:

- **Install PostgreSQL 18** (on Windows, use the EDB installer from the postgresql.org download page), then create the database with pgAdmin or psql:
  ```sql
  CREATE USER sndc WITH PASSWORD 'change-me';
  CREATE DATABASE sndc_ims OWNER sndc;
  ```
- **Docker** (requires Docker Desktop): `cd backend; docker compose up -d`

### 3. Environment variables

```powershell
cd backend
Copy-Item .env.example .env   # then edit .env and fill in the passwords
```

**Required:** `JWT_SECRET` (a random string of at least 32 characters; `.env.example` shows how to generate one) and `DEPARTMENT_REGISTRATION_CODE` (the department access code). If either is missing, the backend refuses to start and says why.

**Optional:** `GROQ_API_KEY` for the AI assistant (create one at <https://console.groq.com/keys>). Everything else works without it.

Spring Boot reads `.env` automatically (`spring.config.import`), and `.gitignore` excludes it. System environment variables take precedence over `.env`. **Never commit `.env` or share its contents.**

### 4. Run

```powershell
.\gradlew.bat bootRun
```

Flyway runs the migrations on startup and creates the tables.

By default the backend only listens on `127.0.0.1`, so other devices on the LAN cannot reach it directly. During development:

- Browser on the same computer: `http://localhost:8080`
- Android emulator: `http://10.0.2.2:8080` (the emulator's address for the host computer)
- Physical phone: through Tailscale (see "Self-hosting" below)

- Health check: <http://localhost:8080/api/v1/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## Running the Android app (development)

1. Start the backend on your computer with `bootRun`.
2. Open the repository root in Android Studio and wait for Gradle Sync to finish.
3. Pick an Android emulator and press Run. The debug build connects to `http://10.0.2.2:8080/api/v1/`, which is the backend on the emulator's host computer.
4. To use another address (for example through Tailscale later), add to `local.properties`:
   ```properties
   sndc.debugApiBaseUrl=http://10.0.2.2:8080/api/v1/
   sndc.releaseApiBaseUrl=https://<computer-name>.<tailnet>.ts.net/api/v1/
   ```

App behavior:
- It remembers the last username but **does not store the PIN or the token**. After the app is closed, the PIN must be entered again.
- The UI language (中文 / EN) is saved on the phone. UI text is written where it is used, e.g. `tr("登入", "Log in")` (see `ui/common/I18n.kt`). When you add a screen, please provide both languages. Text returned by the backend (AI assistant replies, import error details) is still in Chinese.
- The debug build allows plain HTTP only to `10.0.2.2` and `localhost`; the release build allows HTTPS only.

## Self-hosting (Phase 6, planned)

```
Phone app ──HTTPS──▶ Tailscale ──▶ tailscale serve ──▶ http://127.0.0.1:8080 Spring Boot (Windows service)
                                                          ├─▶ PostgreSQL 18 (Windows service)
                                                          └─▶ photo folder
```

1. The PostgreSQL 18 installer creates a Windows service that starts at boot.
2. The backend will be wrapped as a Windows service with WinSW, starting at boot and restarting after a crash.
3. Install Tailscale, enable HTTPS, and run `tailscale serve --bg localhost:8080`. Phones then connect to `https://<computer-name>.<tailnet>.ts.net`. This setting survives reboots.
4. A Windows scheduled task runs `pg_dump` every day and copies the photo folder to another storage device.
5. Sleep must be disabled on this computer. Do not open router ports and do not use Tailscale Funnel.

Tailscale's free plan allows 6 users. Beyond that, share the server machine with other members through "Device sharing"; they sign in with their own free accounts and do not use up seats.

## Tests

```powershell
cd backend
.\gradlew.bat test
```

- Tests use an in-memory H2 database; PostgreSQL is **not** needed.
- Coverage: registration and login (access code, PIN confirmation, leading zeros, PIN hashing, wrong PIN, lockout after failed logins, 401 when not logged in), asset CRUD, soft delete, duplicate `asset_code`, `quantity` validation, date format, paging and sort whitelist, creating locations and custodians, required fields, purchase year, expiry calculation, text normalization, inventory period filters, Excel/CSV import (preview, confirm, errors, Big5), export of selected assets, the AI assistant, and the health check.
- Import tests generate their own Excel files; no real asset list is used.
- Report: `backend/build/reports/tests/test/index.html`
- Android unit tests: run `.\gradlew.bat :app:testDebugUnitTest` from the repository root.

## Progress

| Phase | Status |
|---|---|
| 0 Repository review and planning | ✅ Done |
| 1 Backend skeleton and database | ✅ Done (local startup and PostgreSQL connection verified) |
| 2 Authentication and Android foundation | ✅ Done (tested locally and in the app) |
| 3 Asset management | 🔧 CRUD, multi-filter search, inventory periods, Excel import/export done; photos not started |
| 4 AI agent (Groq) | ✅ Done (tested locally) |
| 5 Polish and testing | ⏳ Not started |
| 6 Deployment (self-hosted) | ⏳ Not started |

### Features

- **Accounts**: registration with the department access code, 4-digit PIN login, JWT (HS256), BCrypt-hashed PINs, constant-time code comparison, and a failed-login limit. Every API except registration, login, health check and API docs requires login.
- **Smart Office (asset register)**:
  - Asset list with total count, search by name, code or specification, "load more", and filters for location, custodian and useful-life status (past useful life, expires within 90 days, within useful life).
  - **Inventory period dropdowns** above the list: pick a year, then a quarter; the default shows everything.
  - Asset details, create/edit form (locations and custodians can be added from the picker), delete with confirmation.
  - **Import** (CRUD → "Import Excel/CSV"): upload the supervisor's `.xlsx` as is. The server checks every row first, and nothing is written if any row has an error. Existing asset codes are updated; values the Excel file does not have (useful life, color) are kept.
  - **Export**: tick the box at the top left of asset cards, then tap "Export" to get an `.xlsx` in the supervisor's layout (one sheet per custodian, with a subtotal row).
  - All data is written to PostgreSQL through the backend API; the app stores no asset data.
- **AI assistant**: tap the mascot at the bottom right to search, count, find duplicates, or edit and delete assets (in bulk) in natural language. Edits and deletions open a confirmation dialog listing every affected asset; nothing changes until you confirm. New assets must still be created manually.
- **Indoor guidance**: the "Guide me" button on the asset details screen is a placeholder for the indoor navigation module.

Asset fields follow the supervisor's asset list (Flyway `V4__excel_import_and_periods.sql`). **V4 clears assets, photos, custodians and locations (test data) and keeps user accounts.**

### Not done yet

- Photo upload API and storage provider.
- A duplicate-items screen in the app (available through the AI assistant), and app filters for purchase date range and minimum quantity (the backend supports them).
- AssetHistory / AuditLogs: only `created_at`, `updated_at` and `deleted_at` for now.
- English text from the backend (AI replies, import error details).

## Privacy

- The supervisor's asset lists contain custodians' names. `.gitignore` excludes `*.xlsx`, `*.xls` and `*.csv`; **do not commit them to GitHub**.
- Floor plans, marker positions, photos and maps stay on the department server only.
- AI queries are sent to Groq (a third-party service). Do not type sensitive information into the assistant.

## Assumptions / pending decisions

These are **provisional defaults** chosen to keep development moving. They have not been confirmed.

| Item | Current choice | Where to change |
|---|---|---|
| Must a custodian be an app user? | No; `managers.user_id` is optional | `manager/`, V1 migration |
| Asset code format | Free format, up to 64 characters, unique after normalization (including deleted records) | `AssetDtos.AssetRequest` |
| Text normalization | Unicode NFKC (full-width to half-width) and trimming; no case folding or synonyms | `common/TextNormalizer` |
| Useful life | Optional (the supervisor's Excel has no such column); at most one decimal; expiry uses the rounded number of months | `asset/ExpiryCalculator` |
| Excel has purchase year and month only | Stored as the 1st of that month; split back into year and month on export | `AssetImportService` |
| Location names | Unique after normalization | V1 migration |
| Custodian names | Unique since v1.6 (import matches custodians by name) | V4 migration |
| Server time zone | `APP_TIMEZONE`, default UTC; database times are always UTC | `config/TimeConfig` |
| JSON field naming | camelCase | — |
| Technical mission | Required free text; the app pre-fills "駐索馬利蘭技術團" | `AssetFormUiState` |
| AI model | `openai/gpt-oss-120b` on Groq (change with `GROQ_MODEL`) | `.env` |
| AI bulk limits | Delete up to 100, edit up to 50; pending actions expire after 10 minutes | `AgentService`, `PendingActionStore` |
| "Expiring soon" window | 90 days | `AssetService.EXPIRING_SOON_DAYS` |
| Import preview | Kept in server memory for 10 minutes, can be confirmed once by its owner; files up to 10 MB and 5,000 rows | `ImportSessionStore` |
| Username | Up to 30 characters, NFKC-normalized, case-sensitive | `auth/AuthService` |
| Failed-login limit | 5 failures for the same username lock it for 15 minutes (kept in memory, cleared on restart), because a 4-digit PIN has only 10,000 combinations | `app.auth.*` |
| Token storage | The app keeps the token in memory only; no refresh token | `SessionManager` |
| Token lifetime | 12 hours | `JWT_EXPIRATION` |
| Deployment | Self-hosted + Tailscale (decided); which computer is the server, who owns the Tailscale account and where backups go are still open | Phase 6 |
