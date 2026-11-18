# 智慧辦公室管理系統 --- 專案工程規格書

> 文件版本：1.6（2026-11-18 修訂，見文末「修訂紀錄」）\
> 文件狀態：MVP 開發基準（部分決策標示為待確認）\
> 目標讀者：Claude Code、專案開發者\
> 專案脈絡：索馬利蘭外交替代役團部內部使用\
> 主要使用規模：約 15 人以內

------------------------------------------------------------------------

## 0. Claude Code 執行指示（優先閱讀）

你是本專案的資深全端工程師。請以本文件作為第一版 MVP
的主要需求依據，建立一套可實際執行、可測試、前後端真正串接的應用程式，而不是只有靜態畫面或假資料
Demo。

### 0.1 開發原則

1.  **先檢查工作目錄**：確認是否已有專案、Git
    狀態、現有程式碼及執行環境。不得未經檢查就覆寫或刪除既有內容。
2.  **先建立可執行骨架，再逐階段實作**：遵守第 14 節 Implementation
    Plan。每個階段完成後執行 build、測試並回報。
3.  **規格優先**：若程式碼、註解或臨時假設與本文件衝突，以本文件為準；若本文件標示「待確認」，不得自行把假設寫成已確認需求。
4.  **避免過度工程化**：本系統約 15 人使用，採 Modular
    Monolith。不要引入 Kubernetes、Microservices、Redis、訊息佇列、獨立
    AI Server 或 GPU，除非有具體必要且先提出理由。
5.  **不做假功能**：不得以硬編碼假資料、Mock API
    或固定回覆冒充已完成的正式功能。外部服務尚未設定時，應提供明確設定錯誤及開發替代方式。
6.  **保持前後端契約一致**：API
    路徑、DTO、驗證規則、錯誤格式及資料欄位應有文件，Android 與 Backend
    不得各自臆測。
7.  **安全但不過度複雜**：遵守 HTTPS、Server-side
    secret、密碼雜湊、輸入驗證及參數化資料庫查詢；不要自行擴張成企業級
    IAM 或繁複帳號防護系統。
8.  **所有重要設定使用環境變數**：不得把團部通行碼、JWT
    secret、Groq API Key、資料庫密碼或正式儲存憑證寫入程式碼、Git
    或 Android APK。
9.  **完成後更新文件**：至少維護 README、API
    文件、資料庫遷移、環境變數範例及測試說明。
10. **不要自行增加需求**：例如 Email 登入、Google
    OAuth、管理者審批註冊、複雜權限矩陣、即時推播等均不在第一版範圍。

### 0.2 實作前的工作

開始寫程式前，先輸出： - 目前工作目錄及既有檔案摘要。 - 建議的 monorepo
目錄結構。 - 預計採用的主要套件與版本（選擇穩定且彼此相容的版本）。 -
本文件中會影響實作的待確認事項。 - Phase 1 的具體實作清單。

若沒有阻斷開發的問題，請直接開始，不要只停留在提問或規劃。

------------------------------------------------------------------------

## 1. 專案概述

### 1.1 目標

建立一套供團部內部人員使用的 Android
智慧辦公室管理系統，主要管理辦公室資產清冊，提供：

-   使用者註冊與登入。
-   資產新增、查詢、修改、刪除（CRUD）。
-   一般條件搜尋。
-   AI 自然語言資產搜尋。
-   資產數量統計及重複品項分析。
-   資產照片管理。
-   使用說明頁面。
-   預留智慧導航系統入口，但第一版不實作導航功能。

### 1.2 使用規模與設計取向

-   預期使用者：15 人以內。
-   使用情境：團部內部、低併發、資料量小至中等。
-   優先目標：快速完成可用 MVP、易於維護、部署簡單、成本低。
-   不以高流量、大規模多租戶或企業級微服務為目標。

### 1.3 第一版範圍

**In Scope** - Android 原生 App。 - Spring Boot Backend。 -
PostgreSQL。 - 認證及基本使用者資料。 - 資產
CRUD、條件查詢、數量統計。 - AI 自然語言查詢。 -
單筆資產照片上傳／檢視／刪除。 - 使用說明。 -
本機開發及本機架設（Self-hosted）所需設定。

**Out of Scope（第一版不做）** - 智慧導航演算法及導航資料。 - Excel
初始資料範本的自動產生。 - Excel 初始資料由系統自動建立：初始 Excel
由專案負責人自行整理；系統可先不實作匯入功能。 -
Email、Google、Apple、OAuth 等第三方登入。 - 多租戶。 -
即時聊天、推播通知。 - 複雜簽核流程。 - 任意 SQL 對話執行。 -
Kubernetes、微服務拆分及 GPU 部署。 - 雲端主機部署（第一版以零成本的本機架設為準，見第 2.6 節）。

------------------------------------------------------------------------

## 2. 系統架構與技術選型

### 2.1 整體架構

採用 **Modular Monolith + Android Client + REST API + PostgreSQL**。

``` text
┌───────────────────────────────────────────┐
│            Android Application            │
│ Kotlin / Jetpack Compose / MVVM           │
│ Retrofit + OkHttp                         │
└─────────────────────┬─────────────────────┘
                      │ HTTPS / JSON REST API
                      ▼
┌───────────────────────────────────────────┐
│             Spring Boot Backend           │
│                                           │
│  Auth Module                              │
│  Asset Module                             │
│  Search Module                            │
│  AI Agent Module                          │
│  Image Storage Module                     │
│  User / Location Module                   │
└───────────────┬─────────────────┬─────────┘
                │                 │
                ▼                 ▼
       ┌────────────────┐  ┌────────────────┐
       │  PostgreSQL    │  │   Groq API     │
       │  Persistent DB │  │ LLM inference  │
       └────────────────┘  └────────────────┘
                │
                ▼
       ┌────────────────┐
       │ Local disk     │
       │ Asset images   │
       └────────────────┘
```

Server、PostgreSQL 與照片皆位於同一台團部 Windows 電腦；手機經 Tailscale 私有網路以 HTTPS 連線（見第 2.6 節）。只有 AI 查詢會呼叫外部的 Groq API。

### 2.2 Android Client

-   Language：Kotlin。
-   UI：Jetpack Compose。
-   Architecture：MVVM。
-   Networking：Retrofit + OkHttp。
-   Async：Kotlin Coroutines / Flow。
-   Navigation：Navigation Compose。
-   Local preferences：DataStore（記住使用者名稱及必要的非敏感偏好）。
-   Token：使用適當的安全儲存方式；不得把 PIN 明文保存。
-   UI 狀態：明確處理 Loading、Success、Empty、Error。

不得讓 Android App 直接連線 PostgreSQL，也不得把資料庫憑證或 Groq
API Key 放入 APK。

### 2.3 Backend

-   Language：Java。
-   Framework：Spring Boot 4.1.x（須使用官方仍支援的版本；4.1 OSS 支援至 2027-07-31，屆時升級至當時的支援版本）。
-   API：REST + JSON。
-   Persistence：Spring Data JPA / Hibernate。
-   Database：PostgreSQL 18（官方支援至 2030-11-14）。
-   Security：Spring Security + JWT。
-   Validation：Jakarta Bean Validation。
-   Migration：Flyway（或同等資料庫版本遷移工具；優先採 Flyway）。
-   Excel 匯入目前非第一版必要功能，不需為此先加入 Apache POI。

建議採清楚的分層結構： - Controller：HTTP/API 層。 -
Service：業務邏輯。 - Repository：資料存取。 - Entity：資料庫映射。 -
DTO：API 請求與回應。 - Mapper：Entity / DTO 轉換。 -
Config：安全、CORS、外部服務設定。 - Exception：統一例外處理。

### 2.4 Database

PostgreSQL 為正式資料唯一來源（Single Source of Truth）。Excel
僅由專案負責人自行整理初始資料，不作為正式線上資料庫。

### 2.5 AI

-   使用 Groq API（OpenAI 相容的 Chat Completions，Structured Outputs strict JSON Schema）；模型以 `GROQ_MODEL` 設定，預設 `openai/gpt-oss-120b`。（v1.5 由 OpenRouter 改為 Groq）
-   LLM 只負責理解自然語言、分類任務及產生受限制的結構化查詢條件。
-   Backend 負責驗證、授權、執行查詢及計算統計。
-   不允許 LLM 直接連線資料庫或執行任意 SQL。

### 2.6 Deployment（已定案：本機架設）

為控制成本，第一版**不部署到雲端主機**，改在團部一台 Windows 電腦上架設：

``` text
手機 App ──HTTPS──▶ Tailscale（私有 VPN） ──▶ tailscale serve（TLS 終止）
                                                   │
                                                   ▼ http://127.0.0.1:8080
                                        Spring Boot（Windows 服務，背景執行）
                                                   │
                                     ┌─────────────┴─────────────┐
                                     ▼                           ▼
                          PostgreSQL 18（Windows 服務）     照片資料夾（本機磁碟）
```

-   **持久性**：所有資料寫入 PostgreSQL（磁碟檔案）及照片資料夾。Spring Boot 程式結束、當機或電腦重開機都不會遺失已寫入的資料。
-   **背景執行**：PostgreSQL 與 Spring Boot 皆以 Windows 服務執行，開機自動啟動、當機自動重啟，不需開著視窗。
-   **對外連線**：使用 Tailscale。Spring Boot 只監聽 `127.0.0.1`，不直接暴露於區網或網際網路；由 `tailscale serve --bg localhost:8080` 提供 HTTPS（自動取得 `*.ts.net` 憑證），僅 Tailscale 網路內的裝置可連線。**不得**在路由器開放對外連接埠，也不使用 Tailscale Funnel（公開網際網路）。
-   **使用者人數**：Tailscale 免費 Personal 方案可容納 6 位使用者；超過時以「Device sharing」把 Server 這台電腦分享給其他成員（分享不增加使用者數，對方需以自己的免費 Tailscale 帳號登入手機）。此方式需於 Phase 6 實測。
-   **前提**：Server 電腦需保持開機且不進入睡眠；建議使用固定放在團部的電腦。
-   **備份**：每日以 Windows 工作排程執行 `pg_dump`，並連同照片資料夾複製到另一個儲存裝置（外接硬碟或另一台電腦），保留至少 14 天。
-   **登入保護仍為必要**：本機架設不取代第 3 節的註冊、登入與 JWT。
-   核心程式碼不得寫死 Tailscale 或特定主機設定；網路位址、監聽介面、路徑皆以環境變數設定，日後如改為雲端部署仍可沿用。

------------------------------------------------------------------------

## 3. 使用者註冊與登入規格

### 3.1 註冊流程

註冊畫面包含：

1.  使用者名稱。
2.  自行設定的四位數 PIN 碼。
3.  再次輸入 PIN 碼確認。
4.  團部通行碼。

只有團部通行碼正確，才允許建立帳號。團部通行碼由 Server
端環境變數預先設定，不得放在 Android APK 或公開 API 回應中。

註冊成功後，使用者名稱與 PIN 碼綁定同一帳號。

### 3.2 登入流程

-   登入使用者名稱 + 四位數 PIN 碼。
-   Client 記住使用者名稱，方便下次登入。
-   若已記住使用者名稱，登入畫面可預先填入。
-   不得明文保存 PIN。
-   不需要 Email、第三方 OAuth 或其他登入方式。

### 3.3 PIN 規則

-   PIN 固定四位數，允許前導零，例如 `0037`。
-   PIN 必須以字串處理，不可轉成整數導致前導零遺失。
-   不要求不同使用者的 PIN 唯一。
-   使用者名稱 + PIN 才是登入憑證。
-   Backend 儲存 PIN 的安全雜湊值，不儲存明文。
-   團部通行碼使用 Server-side environment variable / secret。

### 3.4 JWT

-   登入成功後由 Backend 簽發 JWT。
-   Android 透過 Authorization: Bearer `<token>`{=html} 呼叫需要登入的
    API。
-   JWT secret 由環境變數提供。
-   Token 有效期限採合理且可設定的預設值，並集中管理。
-   第一版不要求 Refresh Token 系統；若採用 Refresh
    Token，需保持流程簡單並在文件說明。

### 3.5 權限模型（已定案）

**所有成功註冊並成功登入的使用者，均可使用完整資產 CRUD 功能。**

- 不區分 `ADMIN` 與 `USER`。
- 不設置管理員專屬操作。
- 所有已登入使用者均可新增、查詢、修改及刪除資產。
- 所有資產 CRUD API 僅需確認使用者已登入並持有有效 JWT，不需再檢查角色。
- 註冊仍須輸入正確的團部通行碼；團部通行碼只用於註冊資格驗證，不代表管理員身分。
- 不需要初始 Admin、角色指派或管理員設定流程。

請勿自行加入角色權限限制、管理員審批或其他 CRUD 權限分級。未來若需求改變，再另行擴充。

------------------------------------------------------------------------

## 4. 資產管理資料規格

### 4.1 核心概念

一筆 Asset 記錄代表一組具有相同管理屬性的物品。`quantity`
表示該筆紀錄代表的數量。

若同一品項在保管人、存放位置、購置日期或其他管理屬性上不同，應建立不同
Asset
記錄。若原本同一筆紀錄中的部分物品移至其他位置或更換保管人，應允許拆分成另一筆紀錄。

這是「批次數量 + 必要時拆分紀錄」的混合模式，不要求所有物品都逐件建檔。

### 4.2 資產欄位（v1.6 對照主管 Excel 修訂）

| 欄位 | 介面名稱 | 主管 Excel 欄位 | 型別 | 必填 | 說明 |
|---|---|---|---|---|---|
| id | — | — | UUID | 是 | 系統主鍵 |
| asset_code | 編號 | 財產編號 | VARCHAR(64) | **是** | 唯一；匯入時以此比對（去除前後空白） |
| item_name | 名稱 | 財產名稱 | VARCHAR(200) | **是** | 例如：冷氣機。重複品項依此欄判斷（第 5 節） |
| specification | 規格及摘要 | 規格及摘要 | VARCHAR(2000) | 否 | v1.6 加回；關鍵字搜尋也會比對 |
| tech_mission | 所屬技術團 | 標題列「駐…技術團」 | VARCHAR(100) | **是** | App 新增時預先填入「駐索馬利蘭技術團」 |
| purchase_date | 購置日期 | 購置 年度＋月份 | DATE | **是** | Excel 只有年月，匯入時存為該月 1 日；匯出時再拆回年度、月份 |
| acquisition_type | 購置類型 | 購置類型／經費來源 | VARCHAR(100) | 否 | 例如：當地採購 |
| location_id | 存放位置 | 放置地點 | UUID → Locations | **是** | 匯入時以名稱對應，沒有就自動建立 |
| manager_id | 保管人 | 表頭「財產保管人：」 | UUID → Managers | **是** | 匯入時以名稱對應，沒有就自動建立（名稱唯一） |
| unit | 單位 | 單位 | VARCHAR(20) | 否 | v1.6 加回，例如：臺、具、套 |
| quantity | 數量 | 數量 | INTEGER | 是 | 預設 1，必須大於 0 |
| value_twd | 價值（台幣） | 價值計算 台幣 | NUMERIC(14,2) | 否 | |
| value_usd | 價值（美金） | 價值計算 美金 | NUMERIC(14,2) | 否 | |
| description | 備註 | 備註 | VARCHAR(2000) | 否 | |
| useful_life_years | 使用年限 | （無） | NUMERIC(4,1) | 否 | **v1.6 改為選填**；未填則不計算到期日 |
| expected_expiry_date | 預計到期日 | （無） | DATE | 自動 | 購置日期 + 使用年限；只由 Service 寫入 |
| color | 顏色 | （無） | VARCHAR(50) | 否 | |
| created_at／updated_at | — | — | TIMESTAMPTZ | 是 | 建立／更新時間 |
| deleted_at | — | — | TIMESTAMPTZ | 否 | Soft Delete 時間 |

另以 `asset_periods` 記錄資產屬於哪些盤點期別（第 4.6 節）。仍未使用的欄位：品項分類（category）、品牌（brand）、型號（model）。

注意： - `asset_code` 對每筆 Asset 記錄唯一，不代表每個實體物品都有獨立序號。 - `quantity` 是該筆紀錄的數量。 - 同一名稱可有多筆 Asset 記錄。 - 不得把名稱與編號混為一談。 - 預計到期日 = 購置日期 + 使用年限，由 Service 層計算。v1.4 起另存於 `expected_expiry_date` 欄位以支援「已過使用年限」查詢，該欄位只由 Service 寫入。
- 使用年限狀態（以 `APP_TIMEZONE` 的今天判斷）：已過使用年限（到期日 ≤ 今天）、90 天內到期、使用年限內；沒有使用年限的資產不屬於任何一種。 - 必填欄位由 API 驗證強制。
- V4 migration 會清空資產、照片、保管人、存放位置（皆為測試資料），保留已註冊的帳號。

### 4.3 保管人與位置（v1.3 起「管理人」改稱「保管人」，資料表仍為 Managers）

建議使用獨立資料表：

**Users**（App 登入帳號）- id - username（唯一） - pin_hash - created_at - updated_at

> 第一版不設角色，Users 表**不建立 `role` 欄位**（見第 3.5 節）。

**Managers**（資產保管人）- id - display_name（v1.6 起唯一） - user_id（UUID，optional，關聯 Users） - created_at - updated_at

**Locations** - id - name - description（optional） - created_at -
updated_at

Asset.manager_id 關聯 **Managers**；Asset.location_id 關聯 Locations。

**保管人設計（Phase 1 採用的暫定設計）：** 資產保管人不一定是 App 使用者，因此另建 `Managers`
表。`user_id` 為選填：若保管人同時是 App 使用者，可連結其帳號；若不是，只填 `display_name`
即可。此設計同時涵蓋兩種情況，日後無論團部確認哪一種，都不需要更動 Asset 的外鍵。
是否需要「保管人必須是 App 使用者」的限制仍列於第 15 節待確認，但不阻擋 Phase 1。

### 4.4 AssetImages

照片與資產資料分離，圖片 Binary 不存 PostgreSQL。

建議欄位： - id UUID - asset_id UUID - object_key VARCHAR -
original_filename VARCHAR - content_type VARCHAR - file_size BIGINT -
created_at TIMESTAMPTZ

儲存實際圖片的 Storage Provider
透過介面抽象化。第一版開發與正式環境皆使用本機磁碟（`IMAGE_STORAGE_PATH`），照片資料夾須納入每日備份。介面保留日後改用 Object Storage 的彈性，但第一版不需實作雲端 Provider。

照片上傳需限制允許的 MIME type 與合理檔案大小。API 回傳圖片存取 URL
或受控下載路徑，不回傳 Base64 大型圖片內容。

**資料敏感性提醒：**
資產資料與照片存放在團部電腦，不上傳第三方雲端儲存。但 AI 自然語言查詢會將查詢文字送至 Groq（第三方服務）；保管人與位置的名單不送給模型，由 Backend 比對，使用前仍需確認團部是否允許；不得在 AI 查詢中輸入機敏資訊。不得未經核准拍攝敏感設施、管制區域或含機敏資訊的照片。

### 4.5 AssetHistory / AuditLogs

建議保留基本變更紀錄： -
AssetHistory：資產保管人、位置、數量等重要欄位變更。 -
AuditLogs：操作者、操作類型、目標資產、時間。

第一版可採簡化設計，不必建立複雜事件溯源系統。至少應保留建立、更新、刪除的基本資訊。若實作成本過高，先完成
`created_at`、`updated_at`、`deleted_at`，並在文件標示完整歷史紀錄為後續項目。

### 4.6 盤點期別與 Excel 匯入匯出（v1.6 新增，已定案）

**盤點期別**：主管每季提供一份「財產管理個人清單」。`inventory_periods`（年度＋季度，唯一）與 `asset_periods`（多對多）記錄每筆資產出現在哪幾季的清單中；同一筆資產可以同時屬於 2026Q3 與 2026Q4。

- App 清單上方有兩個下拉選單：先選年度、再選季度，預設「全部」。只選年度時列出該年度任一季的資產（同一筆只列一次）；季度必須搭配年度。
- 手動新增資產時可選擇要加入的期別（預設帶入清單目前選的期別）；修改時可再加入一個期別，不會移除既有期別。

**匯入**（`.xlsx`／`.xls`／`.csv`，上限 10 MB、5000 筆）分兩步：

1. **檢查**：伺服器讀檔、逐列驗證、以財產編號比對既有資料，回傳新增／更新／恢復筆數、將新增的存放位置與保管人、錯誤清單（工作表＋列號）。不寫入資料庫。
2. **確認**：寫入檢查過的資料，在同一個交易中完成；任一筆失敗則整批不寫入。檢查結果在伺服器記憶體保留 10 分鐘，只有本人能確認，且只能確認一次。

規則：
- Excel 依表頭文字對應欄位（不依欄位順序），支援兩層表頭、「財產保管人：」列（同一工作表中可換人）、小計列；只讀表頭為「財產編號」的工作表（「新增」增置單等其他工作表會略過）。
- CSV 為單層表頭，保管人放在「保管人」欄；購置日期可用「購置日期」一欄或「購置年度」＋「購置月份」；編碼可為 UTF-8 或 Big5。
- 期別由使用者選擇；檔名含「2026Q3」時 App 會自動帶入。所屬技術團預設讀取 Excel 標題。
- 有任何一列錯誤（缺必填、財產編號重複、年月不正確、金額不是數字等）就不能確認。
- 已存在的財產編號：更新 Excel 有的欄位；Excel 空白的選填欄位保留原值；Excel 沒有的欄位（使用年限、顏色）不改動。已刪除的同編號資產會被恢復。
- 匯入不會刪除資產：上一季有、這一季沒有的資產仍保留，只是不屬於新的期別。

**匯出**：清單每張資產卡片左上角有勾選方框，可跨頁、跨條件勾選後按「匯出」。伺服器產生與主管清單相同版面的 `.xlsx`（每位保管人一個工作表，含小計），App 讓使用者選擇存檔位置。匯出的檔案可以再匯入。

**個資**：財產清單含保管人姓名，`.gitignore` 排除 `*.xlsx`、`*.xls`、`*.csv`，不可提交到公開 GitHub。

------------------------------------------------------------------------

## 5. 品項名稱、數量及重複分析規則

### 5.1 重複品項定義（已定案）

**只要 `item_name` 品項名稱相同，就視為相同品項。**

不考慮： - 顏色。 - 保管人。 - 存放位置。 - 所屬技術團。 - 購置日期。

例如： - A 紀錄：水壺，quantity = 3，藍色，第一辦公室。 - B
紀錄：水壺，quantity = 2，紅色，第二辦公室。

統計結果為「水壺，共 5 個，來自 2 筆紀錄」。

### 5.2 重複品項統計

-   依 `item_name` 分組。
-   合計 `SUM(quantity)`。
-   計算紀錄數 `COUNT(*)`。
-   合計數量大於 1 才列入「有重複的品項」清單。
-   排除 Soft Deleted 紀錄。
-   不得由 LLM 自行推測統計結果，必須由 Backend / PostgreSQL 計算。

SQL 參考：

``` sql
SELECT item_name,
       SUM(quantity) AS total_quantity,
       COUNT(*) AS record_count
FROM assets
WHERE deleted_at IS NULL
GROUP BY item_name
HAVING SUM(quantity) > 1
ORDER BY total_quantity DESC;
```

### 5.3 名稱比對

第一版以 `item_name`
完全相同為判斷依據。可以在寫入時去除名稱前後空白，但不得未經確認就： -
把「水壺」與「保溫瓶」視為同義詞。 - 自動進行語意合併。 -
把不同名稱改寫成同一品項。 - 依顏色、品牌或規格拆成不同品項群組。

大小寫、全形半形及 Unicode
正規化策略需保持一致並寫入測試；中文名稱通常不涉及大小寫。不要自行加入同義詞字典。

------------------------------------------------------------------------

## 6. 資產 CRUD 與一般搜尋

### 6.1 CRUD 操作

管理介面提供一個 CRUD 按鈕，點擊後顯示四個操作：

-   Create：新增資產。
-   Read：查詢／檢視資產。
-   Update：修改資產。
-   Delete：刪除資產。

Update / Delete
必須先搜尋或選擇目標資產，再操作，避免使用者不清楚正在修改哪筆資料。

### 6.2 Delete

第一版採 Soft Delete： - 設定 `deleted_at`。 -
一般查詢預設排除已刪除資料。 - 重複品項分析排除已刪除資料。 -
不實作一般使用者可直接永久刪除 DB 紀錄的功能。

### 6.3 一般條件搜尋

至少支援（可同時套用多個條件）： - 名稱或編號關鍵字。 - 所屬技術團。 - 保管人（從既有清單選擇）。 - 存放位置（從既有清單選擇）。 - 顏色。 - 數量下限。 - 購置日期範圍。 - 使用年限狀態：已過使用年限／90 天內到期／使用年限內。

查詢結果應顯示： - 名稱。 - 編號。 - 數量。 - 保管人。 - 位置。 - 顏色。 - 預計到期日。 - 照片縮圖（若有）。

支援分頁，避免一次回傳全部資料。

------------------------------------------------------------------------

## 7. AI Agent 自然語言查詢規格

### 7.1 使用目標

使用者可用自然語言查詢資產，例如：

-   「我要找李大豪的東西」
-   「我要找藍色的水壺」
-   「我要找使用年限到今年為止的品項」
-   「找第一辦公室的印表機」
-   「找數量超過五個的物品」
-   「我們有多少個水壺？」
-   「幫我找有哪些物品有重複的」
-   「找快要不能用的東西」

### 7.2 任務類型

至少支援以下任務分類：

-   `ASSET_SEARCH`：一般資產搜尋。
-   `COUNT_QUERY`：品項數量統計。
-   `DUPLICATE_ANALYSIS`：重複品項分析。
-   `NEEDS_CLARIFICATION`：資訊不足或語意不明，需要詢問使用者。

v1.5 起實作的 intent（後端名稱）：`SEARCH`、`COUNT`、`DUPLICATES`、`DELETE`、`UPDATE`、`CLARIFY`、`UNSUPPORTED`。

### 7.2.1 AI 修改與刪除（v1.5 新增，已定案）

-   AI 助理可依自然語言修改（含批次）與刪除（含批次）資產，例如「我們今天汰換掉由劉正正管理的過期資產了」「編號 60109-0003 的那個資產是水龍頭請幫我修正」。
-   **新增資產不開放給 AI**，一律手動建立（intent = `UNSUPPORTED`）。
-   查詢、統計直接執行；**修改與刪除一律先產生「待確認動作」**，App 跳出確認視窗列出所有受影響資產（與修改內容），使用者按「確認」後才執行。
-   確認時執行的是當初列出的那幾筆（以 ID 固定），不重新查詢；動作 10 分鐘後失效，只有建立者本人可確認。
-   沒有任何條件的修改／刪除（例如「全部刪掉」）一律拒絕並要求說明範圍；單次上限：刪除 100 筆、修改 50 筆；修改編號一次只能一筆。
-   修改後的資料須符合手動編輯的所有規則（必填、格式、年份），否則整批不執行。
-   保管人／位置名稱由 Backend 比對（完全相同 → 唯一部分符合 → 否則列出候選請使用者確認），人名稱謂（技師、主任等）由模型去除。
-   回覆文字由 Backend 依資料庫實際結果組成，不由模型撰寫。

### 7.3 處理流程

``` text
User natural-language query
          |
          v
Backend receives query
          |
          v
LLM parses intent and filters
          |
          v
Backend validates structured result
          |
          +---- invalid / ambiguous ---> ask clarification
          |
          v
Backend executes safe JPA / parameterized query
          |
          v
Database returns actual records / aggregates
          |
          v
Backend builds grounded result
          |
          v
LLM optionally explains result
          |
          v
Android displays result
```

### 7.4 LLM 輸出

要求 LLM 輸出符合固定 JSON Schema 的結構化資料。概念示例：

``` json
{
  "intent": "ASSET_SEARCH",
  "filters": [
    {
      "field": "item_name",
      "operator": "CONTAINS",
      "value": "水壺"
    },
    {
      "field": "color",
      "operator": "EQUALS",
      "value": "藍色"
    }
  ],
  "sort": {
    "field": "item_name",
    "direction": "ASC"
  },
  "clarification_question": null
}
```

這只是格式示意，實際 DTO 與 JSON Schema 需在 `AI_AGENT_SPEC.md`
或程式碼中正式定義。

### 7.5 安全查詢規則

-   Backend 僅允許白名單欄位。
-   Backend 僅允許白名單運算子。
-   不接受 LLM 產生任意 SQL。
-   不允許 LLM 指定任意資料表、JOIN 或資料庫函式。
-   所有查詢使用 JPA Criteria / Specification 或參數化 SQL。
-   限制最大 page size 及查詢結果筆數。
-   任何查詢都必須套用 `deleted_at IS NULL`。
-   AI 回覆的數量、紀錄及統計必須來自資料庫實際結果。
-   LLM 不得編造不存在的資產。

### 7.6 模糊查詢

例如「快要不能用的東西」沒有明確資料欄位定義，不可自行推斷為即將到期或故障。應回問使用者，例如：
「你是指預計使用年限即將到期，還是目前已經故障／損壞的物品？」

第一版若沒有維修狀態欄位，不得假裝可以查詢故障狀態。

### 7.7 日期語意

「使用年限到今年為止」等相對日期查詢，需先由 Backend 取得 Server
當年度及日期，再把明確日期範圍提供給查詢邏輯。不要讓 LLM
自行猜測時區或日期。Server 時區採可設定值，資料庫時間使用 UTC
儲存；日期型欄位使用 DATE。

------------------------------------------------------------------------

## 8. Android UI / UX 規格

### 8.1 畫面清單

1.  Login Screen。
2.  Register Screen。
3.  Home Screen。
4.  Smart Office Dashboard。
5.  Asset List / Search Result Screen。
6.  Asset Detail Screen。
7.  Create / Edit Asset Screen。
8.  Delete Confirmation Dialog。
9.  AI 助理對話視窗：在智慧辦公室管理系統畫面右下角以原創卡通頭像「小管家」按鈕開啟，開場白「我可以怎麼協助你？」。
10. Help / User Guide Screen。
11. Image Viewer / Image Picker。
12. 智慧導航入口頁（Placeholder）。
13. 資產詳情頁的「導引」按鈕：預留未來室內導引（從目前所在房間導引到資產存放位置），目前顯示「功能尚未開放」。

### 8.2 Home Screen

登入後顯示兩個入口： - 智慧辦公室管理系統：進入本次 MVP。 -
智慧導航系統：保留入口與模組架構，顯示「功能尚未開放」或簡潔
Placeholder，不實作導航功能。

### 8.3 Smart Office Dashboard

至少包含： - AI 自然語言搜尋輸入區。 - 一般條件搜尋入口。 - CRUD
按鈕。 - 問號／使用說明按鈕。 - 資產清單或最近查詢結果區域。

### 8.4 CRUD 互動

點擊 CRUD 按鈕後，顯示 Create、Read、Update、Delete 四個選項。Update /
Delete 需先選定目標資產。Delete 顯示確認對話框。

### 8.5 UI 原則

-   介面清楚、簡潔，適合內部人員快速操作。
-   表單需有必填提示與欄位驗證。
-   API 請求期間顯示 Loading。
-   空結果顯示清楚訊息，不顯示空白畫面。
-   錯誤訊息使用一般使用者可理解的文字。
-   長清單使用 LazyColumn / 分頁。
-   不得在 UI 假造成功訊息；以 API 真實回應決定狀態。

### 8.6 使用說明

問號按鈕打開靜態使用說明頁面，第一版不需要 AI。內容至少說明： -
如何新增資產。 - 如何搜尋資產。 - 如何修改／刪除資產。 -
數量欄位的意義。 - AI 搜尋可使用的自然語言範例。 - 重複品項的計算規則。

------------------------------------------------------------------------

## 9. REST API 初步規格

請依此規劃 API，並在實作時產生完整 API 文件（可使用 OpenAPI /
Swagger）。

建議 API 前綴：`/api/v1`

### 9.1 Authentication

  Method   Endpoint           說明
  -------- ------------------ --------------------------
  POST     `/auth/register`   使用團部通行碼註冊
  POST     `/auth/login`      使用 username + PIN 登入
  GET      `/auth/me`         取得目前登入使用者

### 9.2 Assets

  Method   Endpoint               說明
  -------- ---------------------- --------------------------------
  POST     `/assets`              新增資產
  GET      `/assets`              分頁與條件查詢
  GET      `/assets/{id}`         取得單筆資產
  PUT      `/assets/{id}`         修改資產
  DELETE   `/assets/{id}`         Soft Delete
  GET      `/assets/duplicates`   重複品項統計
  GET      `/assets/count`        數量統計（依需求定義查詢參數）

### 9.3 AI

  Method   Endpoint            說明
  -------- ------------------- ------------------
  POST     `/ai/asset-query`   自然語言資產查詢；修改／刪除回傳待確認動作
  POST     `/ai/actions/{id}/confirm`   執行使用者已確認的動作
  DELETE   `/ai/actions/{id}`   取消待確認動作

### 9.4 Images

  Method   Endpoint                          說明
  -------- --------------------------------- --------------
  POST     `/assets/{id}/images`             上傳資產照片
  GET      `/assets/{id}/images`             取得照片清單
  DELETE   `/assets/{id}/images/{imageId}`   刪除照片

### 9.5 Locations / Managers

  Method   Endpoint       說明
  -------- -------------- ------------------------------
  GET      `/locations`   取得位置清單
  POST     `/locations`   新增位置（任何已登入使用者）
  GET      `/managers`    取得可選保管人清單
  POST     `/managers`    新增保管人（任何已登入使用者）

依第 3.5 節，上述 API 與資產 CRUD 相同，只需有效 JWT，不檢查角色。

API 必須： - 使用 DTO，不直接暴露 JPA Entity。 -
使用統一錯誤回應格式。 - 驗證輸入欄位。 - 對清單 API 提供分頁。 -
對需要登入的 API 驗證 JWT。 - 不回傳 PIN hash、團部通行碼或任何 secret。

------------------------------------------------------------------------

## 10. Excel 初始資料策略

v1.6 起以主管的「財產管理個人清單」為資料來源，透過 App 的「匯入 Excel／CSV」建立與更新資產（第 4.6 節）。手動 CRUD 仍可使用。

------------------------------------------------------------------------

## 11. 錯誤處理與資料驗證

### 11.1 後端

-   必填欄位不得為空。
-   quantity 必須大於 0。
-   useful_life_years 若提供，必須大於 0。
-   asset_code 必須唯一。
-   manager_id / location_id 若提供，必須存在。
-   日期格式必須合法。
-   不存在的資產回傳 404。
-   第一版沒有角色權限，因此不會因角色不足回傳 403；403 保留給未來擴充。
-   未登入或 Token 無效回傳 401。
-   輸入驗證失敗回傳 400。
-   Server 未設定 GROQ_API_KEY 時，AI API
    回傳明確的服務未設定錯誤，不可假裝成功。

### 11.2 統一錯誤格式

建議格式：

``` json
{
  "timestamp": "2026-11-11T10:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    {
      "field": "quantity",
      "message": "Quantity must be greater than zero"
    }
  ]
}
```

### 11.3 Android

-   區分網路錯誤、驗證錯誤、認證錯誤、Server 錯誤。
-   不直接把 Stack Trace 顯示給使用者。
-   使用者輸入錯誤時，在欄位附近顯示提示。
-   網路失敗不得清空使用者已輸入的表單資料。

------------------------------------------------------------------------

## 12. 環境設定與 Secrets

建立 `.env.example`
或等效設定範本，只放變數名稱與示例佔位值，不放真實密鑰。

至少預留：

``` env
SPRING_PROFILES_ACTIVE=dev
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=

JWT_SECRET=
JWT_EXPIRATION=

DEPARTMENT_REGISTRATION_CODE=

GROQ_API_KEY=
GROQ_MODEL=openai/gpt-oss-120b
GROQ_REASONING_EFFORT=low

APP_BASE_URL=
APP_TIMEZONE=
SERVER_ADDRESS=127.0.0.1
PORT=8080

IMAGE_STORAGE_PROVIDER=local
IMAGE_STORAGE_PATH=
```

要求： - `.env` 加入 `.gitignore`。 - 團部通行碼、JWT secret、Groq
API Key 不得提交 Git。 - Android APK 不得包含 Server secrets。 -
正式環境的 `.env` 只放在 Server 電腦的服務目錄，並限制檔案存取權限（僅系統管理員與服務帳號可讀）。 -
不可把正式資料庫密碼寫在 README。

------------------------------------------------------------------------

## 13. 測試與驗收標準

### 13.1 Authentication

-   正確團部通行碼可註冊。
-   錯誤團部通行碼不可註冊。
-   PIN 確認不一致時不可註冊。
-   PIN 可保留前導零。
-   正確 username + PIN 可登入。
-   錯誤 PIN 不可登入。
-   登入後可呼叫受保護 API。
-   Android 可記住 username，但不得明文保存 PIN。

### 13.2 Asset CRUD

-   可建立資產。
-   可查詢單筆及清單。
-   可修改資產。
-   刪除後一般查詢不再顯示。
-   Soft Deleted 資料仍保留於 DB。
-   asset_code 重複時拒絕新增。
-   quantity 不可為 0 或負數。

### 13.3 Quantity / Duplicate

測試資料：

  asset_code   item_name     quantity color   location
  ------------ ----------- ---------- ------- ------------
  A001         水壺                 3 藍色    第一辦公室
  A002         水壺                 2 紅色    第二辦公室
  A003         印表機               1 黑色    第一辦公室

測試前需先建立 Locations 種子資料（第一辦公室、第二辦公室），表中 location 欄位代表對應的 `location_id`，而非直接存文字。

預期： - 水壺總數 = 5。 - 水壺紀錄數 = 2。 - 水壺列入重複品項。 -
印表機不列入重複品項。 - 顏色及位置不同不影響重複判斷。

### 13.4 AI Query

至少測試： - 「找藍色的水壺」能轉成品項及顏色條件。 -
「找第一辦公室的印表機」能轉成位置及品項條件。 -
「我們有多少個水壺？」由 DB 計算總數。 -
「有哪些物品有重複？」呼叫重複分析邏輯。 -
「找快要不能用的東西」若沒有足夠欄位定義，必須詢問澄清。 - LLM
回傳不合法欄位或運算子時，Backend 拒絕，不得執行任意查詢。

### 13.5 Android Integration

-   App 可登入並取得 JWT。
-   App 可取得資產清單。
-   App 可新增、修改、刪除資產。
-   App 可執行一般搜尋及 AI 搜尋。
-   App 可顯示 Loading、Empty、Error 狀態。
-   智慧導航入口可正常顯示 Placeholder。

------------------------------------------------------------------------

## 14. Implementation Plan（Claude Code 執行順序）

### Phase 0 --- Repository Inspection & Plan

1.  檢查目錄、Git 狀態及工具鏈。
2.  建立 monorepo 或清楚的前後端目錄。
3.  建立本文件及 README。
4.  確認 JDK、Gradle、Android SDK、PostgreSQL 等開發需求。
5.  提出不超過必要範圍的待確認事項。

**驗收：** 專案目錄、技術版本及啟動方式清楚。

### Phase 1 --- Backend Foundation & Database

1.  建立 Spring Boot 專案。
2.  設定 PostgreSQL。
3.  設定 Flyway migration。
4.  建立 User、Manager、Location、Asset、AssetImage 等 Entity。
5.  建立 Repository、Service、DTO、Controller。
6.  加入基本驗證及統一錯誤處理。
7.  提供健康檢查 endpoint。

**驗收：** Backend 可啟動、DB migration 成功、基本 API 可測試。

### Phase 2 --- Authentication & Android Foundation

1.  實作註冊、登入、JWT。
2.  建立 Android 專案與 Compose Navigation。
3.  建立登入、註冊、首頁。
4.  接上真實 Auth API。
5.  記住 username。
6.  顯示智慧辦公室及智慧導航兩個入口。

**驗收：** Android 可註冊、登入並進入首頁。

### Phase 3 --- Asset Management

1.  實作資產 CRUD API。
2.  實作一般條件搜尋與分頁。
3.  實作資產列表、詳情、新增、編輯、刪除 UI。
4.  實作 quantity。
5.  實作重複品項統計。
6.  實作基本照片 Storage abstraction 及 API。

**驗收：** Android 可透過 Backend 真正操作 PostgreSQL 中的資產資料。

### Phase 4 --- AI Agent

1.  建立 Groq Client。
2.  定義任務分類與結構化輸出 DTO。
3.  定義欄位及運算子白名單。
4.  實作 Backend 查詢轉換及驗證。
5.  實作
    ASSET_SEARCH、COUNT_QUERY、DUPLICATE_ANALYSIS、NEEDS_CLARIFICATION。
6.  串接 Android AI 搜尋 UI。
7.  補上 AI 查詢測試。

**驗收：** 自然語言查詢結果來自真實 DB，不使用假資料，不允許任意 SQL。

### Phase 5 --- Polish & Testing

1.  完善 Loading、Empty、Error、表單驗證。
2.  完成使用說明。
3.  執行 Backend 單元及整合測試。
4.  執行 Android Build。
5.  執行端到端操作測試。
6.  修正文件與實際 API 不一致處。

**驗收：** MVP 核心流程可從 Android 端完整操作。

### Phase 6 --- Deployment

1.  在 Server 電腦安裝 PostgreSQL 18（Windows 服務、開機自動啟動）並建立資料庫與專用帳號。
2.  建置 Backend jar，以 WinSW（或同等工具）註冊為 Windows 服務：開機自動啟動、失敗自動重啟、日誌輪替。
3.  設定 Windows 電源選項：不睡眠。
4.  安裝 Tailscale，啟用 HTTPS 憑證，執行 `tailscale serve --bg localhost:8080`；設定成員邀請或 Device sharing。
5.  建立每日備份排程（`pg_dump` + 照片資料夾），並實際演練一次還原。
6.  Android 正式 API Base URL 設為 `https://<電腦名稱>.<tailnet>.ts.net/api/v1/`（以 BuildConfig 設定，不寫死在程式碼）。
7.  Smoke test：手機分別在團部 Wi-Fi 與行動網路下登入、新增、修改、刪除資產；重新啟動 Backend 服務與整台電腦後確認資料仍在。

**驗收：** 手機在團部內外皆可透過 HTTPS 使用；Backend 不在 127.0.0.1 以外的介面監聽；服務與電腦重啟後資料與照片仍在；備份可還原；Secrets 不在 APK 或 Git 中。

------------------------------------------------------------------------

## 15. 待確認事項（不得阻止可先行的開發）

以下項目需要在相應功能實作前或部署前確認；其他工作不應因此停擺。

  項目                          目前狀態                     建議處理時機
  ----------------------------- ---------------------------- -----------------------
  保管人是否必須是 App 使用者   暫定：不必須（見 4.3）       Phase 3 前
  asset_code 實際編碼規則       待確認                       Phase 1 migration 前
  位置清單由誰維護（流程面）    API 已開放所有登入者         部署前
  Server 電腦由哪一台擔任       待確認（需可長時間開機）     Phase 6 前
  Tailscale 帳號由誰擁有        待確認（建議團部公務帳號）   Phase 6 前
  備份存放位置                  待確認（外接硬碟／另一台）   Phase 6 前
  AI 查詢是否允許送至 Groq     待確認                      Phase 4 前
  Server 時區                   預設可設定；日期語意需一致   Phase 4 前
  AI 使用模型                   透過環境變數設定             Phase 4 前

### 預設值使用原則

若 Claude 必須使用暫定值才能推進： 1. 選擇最簡單、最容易更改的設計。 2.
將設定集中於 Config，不要散落在程式碼。 3. 在 README 的「Assumptions /
Pending Decisions」列出。 4. 不得把暫定值描述成使用者已確認的需求。 5.
不得因待確認項目而停止其他不受影響的工作。

------------------------------------------------------------------------

## 16. Definition of Done

第一版 MVP 只有在以下條件均達成後，才可標示為完成：

-   [ ] Android 專案可成功 Build。
-   [ ] Spring Boot 可成功啟動。
-   [ ] PostgreSQL migration 可成功執行。
-   [ ] Android 可註冊及登入。
-   [ ] 登入後可進入兩個系統入口。
-   [ ] 智慧辦公室管理頁面可正常使用。
-   [ ] 資產 CRUD 透過正式 REST API 完成。
-   [ ] 一般條件搜尋可用。
-   [ ] quantity 正確保存及統計。
-   [ ] 重複品項依 `item_name` 分組並由 DB 計算。
-   [ ] 照片上傳流程可用，照片存於本機資料夾並納入備份。
-   [ ] AI 自然語言查詢可用真實 Groq API 與 DB。
-   [ ] AI 不會執行任意 SQL，也不會捏造查詢結果。
-   [ ] 使用說明頁面可用。
-   [ ] 智慧導航入口顯示 Placeholder。
-   [ ] 測試已執行並記錄結果。
-   [ ] README 包含本機啟動、環境變數、DB 初始化及測試說明。
-   [ ] 未設定的外部服務與待確認決策均有清楚標示。

------------------------------------------------------------------------

## 17. 最後給 Claude Code 的執行命令

請先完整閱讀本文件，再檢查目前 Repository。接著依照第 14 節逐階段實作。

**現在先執行 Phase 0 與 Phase 1，不要一次生成所有功能。** Phase 1
完成後： 1. 執行 Backend build。 2. 執行資料庫 migration。 3.
執行相關測試。 4. 檢查 API 與資料表是否符合本規格。 5. 更新 README
與實作進度。 6.
回報已完成內容、測試結果、尚未完成事項及需要使用者決策的項目。

不得只產生規劃文件而不建立程式碼；若環境缺少必要工具，請明確指出阻礙及可執行的替代步驟。

------------------------------------------------------------------------

## 修訂紀錄

### v1.6（2026-11-18）

-   **資產欄位對照主管 Excel**（第 4.2 節，Flyway V4）：加回規格及摘要、單位；新增購置類型、價值（台幣／美金）；使用年限改為選填。V4 清空測試資料（資產、照片、保管人、存放位置），保留帳號；保管人名稱改為唯一。
-   **盤點期別**（第 4.6 節）：新增 `inventory_periods`、`asset_periods`；清單以「年度 → 季度」兩層下拉選單篩選；`GET /periods`。
-   **匯入**：`.xlsx`／`.xls`／`.csv`，先檢查再確認，整批成功或整批不寫入（`POST /assets/import/preview`、`/import/{token}/confirm`）。
-   **勾選匯出**：資產卡片左上角勾選方框，匯出主管清單格式的 `.xlsx`（`POST /assets/export`）。
-   AI 助理可依盤點期別查詢，並可修改規格及摘要、單位。
-   `.gitignore` 排除試算表檔案（含保管人姓名）。
-   App 介面可切換中文／EN（登入頁、註冊頁、首頁、智慧辦公室畫面右上角），選擇存在手機上；後端回傳的文字仍為中文。README 新增英文版。

### v1.5（2026-11-15）

-   AI 供應商由 OpenRouter 改為 Groq（`GROQ_API_KEY`、`GROQ_MODEL`）。
-   AI 助理可修改、刪除（含批次），執行前須使用者確認；新增仍須手動（第 7.2.1 節）。
-   App 新增「小管家」頭像按鈕與對話視窗；資產詳情新增「導引」按鈕（室內導引預留入口）。
-   新增 `GET /assets/duplicates`，以及依預計到期日範圍查詢（`expiresFrom`／`expiresTo`）。

### v1.4（2026-11-15）

-   所屬技術團改為必填（App 預先填入「駐索馬利蘭技術團」）。
-   App 新增／修改資產時，必填欄位未填寫會先跳出提醒視窗並擋下，不送出到伺服器。
-   購置日期改以日曆選擇。
-   多條件查詢：存放位置、保管人、使用年限狀態、購置日期範圍、數量下限（第 6.3 節）。
-   新增 `expected_expiry_date` 欄位（Flyway V3，含既有資料回填）。

### v1.3（2026-11-15）

-   **資產欄位修訂**（第 4.2 節，Flyway V2）：新增「所屬技術團」；「品項名稱」改稱「名稱」；「服役起始日」改為「購置日期」（格式 2xxx/xx/xx）；編號、名稱、購置日期、存放位置、使用年限、保管人改為必填；顏色、備註為選填；刪除品項分類、品牌、型號、規格、單位。
-   「管理人」改稱「保管人」。
-   一般搜尋條件同步調整（第 6.3 節）；新增 `keyword` 參數可同時搜尋名稱與編號。

### v1.2（2026-11-11）

-   **部署改為本機架設**（第 2.6 節、Phase 6）：Server、PostgreSQL、照片皆在團部 Windows 電腦，以 Windows 服務背景執行；手機經 Tailscale 以 HTTPS 連線，團部內外皆可使用。移除雲端平台比較與 Object Storage 需求。
-   **版本**：PostgreSQL 16 → 18；Spring Boot 定為 4.1.x（原本採用的 3.3 已於 2025-06-30 停止支援）。
-   **環境變數**：新增 `SERVER_ADDRESS`、`PORT`；移除 `OBJECT_STORAGE_*`。
-   **待確認事項**：移除「圖片供應商」「正式雲端平台」；新增 Server 電腦、Tailscale 帳號、備份位置、AI 資料外送四項。

### v1.1（2026-11-11）

-   **權限一致化**：依第 3.5 節已定案的「所有登入者皆可完整 CRUD」，移除 Users 表的 `role` 欄位、移除第 15 節「CRUD 權限」及「初始 Admin」兩個待確認項目、`POST /locations` 改為任何已登入使用者可用，並調整第 11.1 節 403 的描述。
-   **PIM 更正為 PIN**：全文統一，資料欄位改為 `pin_hash`。
-   **管理人模型**：新增 `Managers` 表（`user_id` 選填），Asset.manager_id 改關聯 Managers；API 由 `GET /users` 改為 `GET/POST /managers`；Phase 1 Entity 清單加入 Manager。
-   **測試資料**：第 13.3 節註明需先建立 Locations 種子資料。
