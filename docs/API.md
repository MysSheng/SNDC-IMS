# REST API 文件（Spring Boot 4.1，資產欄位 v1.3）

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
| usefulLifeYears | 使用年限 | number | ✔ | > 0，最多一位小數，≤ 999.9 |
| managerId | 保管人 | UUID | ✔ | 必須存在 |
| quantity | 數量 | integer | | > 0，未提供時為 1 |
| color | 顏色 | string | | ≤ 50 |
| description | 備註 | string | | ≤ 2000 |

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
  "color": "黑色",
  "quantity": 1,
  "description": null,
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
| `null` | 缺少購置日期或使用年限（只會出現在 V2 之前的舊資料） |

### `POST /assets` → `201` AssetResponse

### `GET /assets` → `200` 分頁結果

| Query | 說明 |
|---|---|
| keyword | 名稱**或**編號部分符合（App 的搜尋框使用此參數） |
| assetCode | 完全符合 |
| itemName | 部分符合 |
| techMission, color | 完全符合 |
| expiryStatus | `EXPIRED`／`EXPIRING_SOON`／`VALID` |
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

## 規劃中（尚未實作）

| Phase | Endpoint |
|---|---|
| 3 | `GET /assets/duplicates`、`GET /assets/count`、`/assets/{id}/images` |
| 4 | `POST /ai/asset-query` |
