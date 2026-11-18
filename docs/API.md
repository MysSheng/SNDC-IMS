# REST API 文件（Spring Boot 4.1，規格書 v1.6）

- Base path：`/api/v1`
- 正式環境 Base URL：`https://<電腦名稱>.<tailnet>.ts.net/api/v1`（經 Tailscale；後端本身只監聽 127.0.0.1:8080）
- 格式：JSON（UTF-8），欄位命名採 camelCase
- 時間：`Instant` 以 ISO-8601 UTC 表示，例如 `2026-11-11T08:00:00Z`；日期使用 `YYYY-MM-DD`
- 認證：除 `POST /auth/register`、`POST /auth/login`、`GET /health`、API 文件外，所有 API 都需要 `Authorization: Bearer <token>`。不分角色（規格書第 3.5 節）。
- Swagger UI 右上角的「Authorize」可以貼上登入取得的 `accessToken`，用來測試受保護的 API。
- 互動式文件：後端啟動後開啟 `/swagger-ui.html`

## 統一錯誤格式

```json
{
  "timestamp": "2026-11-11T10:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [{ "field": "quantity", "message": "Quantity must be greater than zero" }]
}
```

| HTTP | code | 情況 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 沒有 Token、Token 無效或已過期 |
| 401 | `INVALID_CREDENTIALS` | 使用者名稱或 PIN 錯誤（不區分是哪一個錯） |
| 403 | `INVALID_REGISTRATION_CODE` | 註冊時團部通行碼錯誤 |
| 409 | `USERNAME_TAKEN` | 使用者名稱已被使用 |
| 429 | `TOO_MANY_ATTEMPTS` | 同一使用者名稱連續登入失敗 5 次，暫停 15 分鐘 |
| 400 | `VALIDATION_ERROR` | 欄位驗證失敗、參照的管理人／位置不存在、分頁或排序參數不合法 |
| 400 | `MALFORMED_REQUEST` | JSON 格式錯誤、日期或 UUID 格式不合法 |
| 404 | `NOT_FOUND` | 資源不存在，或資產已被 Soft Delete |
| 405 | `METHOD_NOT_ALLOWED` | HTTP 方法錯誤 |
| 409 | `ASSET_CODE_DUPLICATE` | 資產編號已存在（含已刪除的資產） |
| 409 | `LOCATION_NAME_DUPLICATE` | 位置名稱已存在 |
| 409 | `DATA_CONFLICT` | 其他資料庫唯一性衝突 |
| 404 | `ACTION_NOT_FOUND` | AI 待確認動作不存在、已過期（10 分鐘）、已執行，或屬於其他使用者 |
| 503 | `AI_NOT_CONFIGURED` | 伺服器沒有讀到 `GROQ_API_KEY` |
| 503 | `AI_KEY_REJECTED` | 有讀到金鑰，但 Groq 回傳 401（金鑰無效或已撤銷） |
| 503 | `AI_ACCESS_DENIED` | Groq 回傳 403，常見於伺服器電腦開著 VPN 或網路受限 |
| 503 | `AI_BUSY` | Groq 使用量達上限（HTTP 429） |
| 503 | `AI_UNAVAILABLE` | Groq 逾時或發生錯誤 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type 不是 JSON |
| 500 | `INTERNAL_ERROR` | 未預期的錯誤（不回傳 stack trace） |

第一版沒有角色，因此不會因為權限不足而回傳 403；403 只用於團部通行碼錯誤。

## Auth

### `POST /auth/register` → `201` AuthResponse
```json
{ "username": "小明", "pin": "0037", "pinConfirm": "0037", "registrationCode": "團部通行碼" }
```
- `username`：最多 30 個字，經 NFKC 正規化並去除前後空白，區分大小寫，不可重複
- `pin`：剛好 4 位數字，**字串**格式（保留前導零）；後端以 BCrypt 雜湊儲存
- 檢查順序：欄位格式 → 團部通行碼 → PIN 確認 → 使用者名稱是否重複
- 註冊成功即登入，直接回傳 Token

### `POST /auth/login` → `200` AuthResponse
```json
{ "username": "小明", "pin": "0037" }
```

### AuthResponse
```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-12T04:00:00Z",
  "user": { "id": "uuid", "username": "小明", "createdAt": "2026-11-11T16:00:00Z" }
}
```
Token 為 HS256 JWT，有效期限由 `JWT_EXPIRATION` 設定（預設 12 小時），沒有 Refresh Token；過期後需重新登入。

### `GET /auth/me` → `200` `{ "id", "username", "createdAt" }`

## Health

### `GET /health`
`200 {"status":"UP","database":"UP"}`；資料庫無法連線時回傳 `503`，`status` 為 `DOWN`。

## Assets

### AssetRequest（新增與修改共用）

| 欄位 | 介面名稱 | 型別 | 必填 | 規則 |
|---|---|---|---|---|
| assetCode | 編號 | string | ✔ | ≤ 64，正規化後全域唯一 |
| itemName | 名稱 | string | ✔ | ≤ 200 |
| techMission | 所屬技術團 | string | ✔ | ≤ 100 |
| purchaseDate | 購置日期 | date | ✔ | `YYYY-MM-DD`，年份 2000–2999（App 顯示為 `YYYY/MM/DD`） |
| locationId | 存放位置 | UUID | ✔ | 必須存在 |
| managerId | 保管人 | UUID | ✔ | 必須存在 |
| usefulLifeYears | 使用年限 | number | | > 0，最多一位小數，≤ 999.9；未提供則不計算到期日 |
| specification | 規格及摘要 | string | | ≤ 2000 |
| acquisitionType | 購置類型 | string | | ≤ 100 |
| unit | 單位 | string | | ≤ 20 |
| quantity | 數量 | integer | | > 0，未提供時為 1 |
| valueTwd, valueUsd | 價值（台幣／美金） | number | | ≥ 0，最多兩位小數 |
| color | 顏色 | string | | ≤ 50 |
| description | 備註 | string | | ≤ 2000 |
| periodYear, periodQuarter | 盤點期別 | integer | | 兩者需同時提供；把資產加入該期別（不會移除既有期別） |

所有字串都會經過正規化：Unicode NFKC、去除前後空白，空字串視為 null。

### AssetResponse

```json
{
  "id": "uuid",
  "assetCode": "A001",
  "itemName": "印表機",
  "techMission": "駐索馬利蘭技術團",
  "manager": { "id": "uuid", "name": "李大豪" },
  "purchaseDate": "2024-01-15",
  "usefulLifeYears": 5,
  "expectedExpiryDate": "2029-01-15",
  "expiryStatus": "VALID",
  "location": { "id": "uuid", "name": "第一辦公室" },
  "specification": "HP LaserJet Pro M404dw",
  "acquisitionType": "當地採購",
  "unit": "臺",
  "quantity": 1,
  "valueTwd": null,
  "valueUsd": 322.00,
  "color": "黑色",
  "description": null,
  "periods": [ { "year": 2026, "quarter": 3 } ],
  "createdAt": "2026-11-15T08:00:00Z",
  "updatedAt": "2026-11-15T08:00:00Z"
}
```

`expectedExpiryDate` 的計算方式是購置日期加上使用年限（換算成月數）。

`expiryStatus` 以伺服器時區（`APP_TIMEZONE`）的今天判斷：

| 值 | 意思 |
|---|---|
| `EXPIRED` | 已過使用年限（到期日 ≤ 今天） |
| `EXPIRING_SOON` | 90 天內到期 |
| `VALID` | 使用年限內 |
| `null` | 沒有使用年限（不計算到期日） |

### `POST /assets` → `201` AssetResponse

### `GET /assets` → `200` 分頁結果

| Query | 說明 |
|---|---|
| keyword | 名稱、編號或規格部分符合（App 的搜尋框使用此參數） |
| year | 盤點期別年度：該年度任一季清單中的資產（同一筆只列一次） |
| quarter | 盤點期別季度 1–4；必須搭配 `year`，否則 `400` |
| assetCode | 完全符合 |
| itemName | 部分符合 |
| techMission, color | 完全符合 |
| expiryStatus | `EXPIRED`／`EXPIRING_SOON`／`VALID` |
| expiresFrom, expiresTo | 預計到期日範圍（含頭尾），`YYYY-MM-DD` |
| purchasedFrom, purchasedTo | 購置日期範圍（含頭尾），`YYYY-MM-DD` |
| minQuantity | 數量 ≥ 此值 |
| managerId, locationId | UUID |
| page | 從 0 開始，預設 0 |
| size | 1–100，預設 20 |
| sort | `欄位,asc\|desc`；可用欄位：`itemName`、`assetCode`、`quantity`、`purchaseDate`、`expectedExpiryDate`、`createdAt`、`updatedAt`。預設 `itemName,asc` |

```json
{ "items": [ AssetResponse ], "page": 0, "size": 20, "totalItems": 3, "totalPages": 1 }
```

結果一律排除 Soft Deleted 的資料。

### `GET /assets/{id}` → `200` AssetResponse；不存在或已刪除時回傳 `404`

### `PUT /assets/{id}` → `200` AssetResponse
整筆取代：未提供的選填欄位會被清為 `null`，`quantity` 未提供時為 1。

### `DELETE /assets/{id}` → `204`
Soft Delete：設定 `deleted_at`，資料仍保留在資料庫中。

## 盤點期別

### `GET /periods` → `200`
```json
[ { "year": 2026, "quarter": 4, "label": "2026Q4", "assetCount": 120 } ]
```
新到舊排序；`assetCount` 為該期別中未刪除的資產筆數。App 以此建立「年度 → 季度」下拉選單。

## 匯入與匯出（規格書第 4.6 節）

### `POST /assets/import/preview`（`multipart/form-data`）→ `200` ImportPreview
| Part | 說明 |
|---|---|
| file | `.xlsx`／`.xls`／`.csv`，上限 10 MB（超過回 `413 FILE_TOO_LARGE`） |
| year, quarter | 盤點期別；未提供時從檔名判斷（例如 `清單_2026Q3.xlsx`），都沒有則 `400` |
| techMission | 選填；未提供時從 Excel 標題判斷 |

只檢查，不寫入。
```json
{
  "token": "uuid 或 null（有錯誤時）",
  "fileName": "ICT計畫_2026Q3.xlsx",
  "year": 2026, "quarter": 3, "period": "2026Q3",
  "techMission": "駐索馬利蘭技術團",
  "totalRows": 120, "createCount": 115, "updateCount": 5, "restoreCount": 0,
  "newLocations": ["第二辦公室"], "newCustodians": ["王小明"],
  "errorCount": 0,
  "errors": [ { "sheet": "ICT計畫", "row": 12, "assetCode": "A-1", "message": "缺少放置地點" } ],
  "expiresAt": "2026-11-18T10:10:00Z"
}
```
`errors` 最多列出 200 筆，`errorCount` 為總數。

### `POST /assets/import/{token}/confirm` → `200`
`{ "period": "2026Q3", "created": 115, "updated": 5, "restored": 0 }`。整批在同一個交易中寫入。token 不存在、已過期（10 分鐘）、已使用過或屬於其他使用者時回 `404 IMPORT_NOT_FOUND`。

### `DELETE /assets/import/{token}` → `204`
放棄尚未確認的匯入。

### `POST /assets/export` → `200`（`.xlsx`）
```json
{ "ids": ["uuid", "uuid"], "year": 2026, "quarter": 3 }
```
`ids` 1–1000 筆；`year`／`quarter` 選填，只用於標題列與檔名。回傳主管清單格式的 `.xlsx`（每位保管人一個工作表，含小計），檔名在 `Content-Disposition`（`filename*=UTF-8''…`）。已刪除或不存在的 ID 會略過；全部都找不到時回 `404`。

## Locations

### `GET /locations` → `200` `[ { "id", "name", "description", "createdAt" } ]`（依名稱排序）

### `POST /locations` → `201`
```json
{ "name": "第一辦公室", "description": "選填，≤ 500" }
```

## Managers

### `GET /managers`（保管人清單） → `200` `[ { "id", "displayName", "userId", "createdAt" } ]`（依名稱排序）

### `POST /managers` → `201`
```json
{ "displayName": "李大豪", "userId": "選填，對應 App 使用者" }
```

### `GET /assets/duplicates` → `200`
名稱相同即視為同一品項，合計數量大於 1 才列入（規格書第 5 節）：
```json
[ { "itemName": "冷氣", "totalQuantity": 5, "recordCount": 2 } ]
```

## AI 助理（Groq）

模型只把自然語言轉成結構化條件；查詢、驗證、執行都在後端進行。修改與刪除一律需要使用者確認。

### `POST /ai/asset-query` → `200` AgentResponse
```json
{ "message": "我想找由李大維技師管理且快要過期的資產",
  "history": [ { "role": "user", "content": "…" }, { "role": "assistant", "content": "…" } ] }
```
- `message` ≤ 500 字；`history` 最多 6 則（App 送回先前回應的 `historyText`），用來理解「把它們刪掉」這類說法。

AgentResponse：

| 欄位 | 說明 |
|---|---|
| intent | `SEARCH`／`COUNT`／`DUPLICATES`／`DELETE`／`UPDATE`／`CLARIFY`／`UNSUPPORTED` |
| reply | 給使用者看的回覆（由後端依資料庫結果組成） |
| historyText | 下一輪送回 `history` 的內容（含條件摘要） |
| filters | 已解析的條件：`keyword`、`assetCode`、`itemName`、`techMission`、`color`、`location {id,name}`、`custodian {id,name}`、`expiryStatus`、`purchasedFrom/To`、`expiresFrom/To`、`minQuantity` |
| results, totalResults | SEARCH 的前 20 筆與總筆數；DELETE／UPDATE 時為受影響的資產 |
| summary | COUNT：`{ recordCount, totalQuantity }` |
| duplicates | DUPLICATES：同 `GET /assets/duplicates` |
| pendingAction | DELETE／UPDATE：`{ id, type, description, targets[], changes{欄位中文名稱: 新值}, expiresAt }`，**尚未執行** |

### `POST /ai/actions/{id}/confirm` → `200` AgentResponse（`intent = DONE`）
執行使用者已確認的動作。只會處理 `pendingAction.targets` 列出的資產；確認前已被刪除的會略過。修改後的資料若不符合規則，整批不執行並回傳 `CLARIFY`。

### `DELETE /ai/actions/{id}` → `204`
取消待確認動作。

## 規劃中（尚未實作）

| Phase | Endpoint |
|---|---|
| 3 | `/assets/{id}/images`（照片） |
