package icdf.sndc.ims.ui.common

import icdf.sndc.ims.data.remote.AppError

/** 送出前的表單檢查，規則與後端一致。回傳 null 代表通過。 */
object AuthValidation {
    private val PIN_REGEX = Regex("^[0-9]{4}$")

    fun username(value: String): String? = when {
        value.isBlank() -> tr("請輸入使用者名稱", "Enter a username")
        value.trim().length > 30 -> tr("使用者名稱最多 30 個字", "Username: at most 30 characters")
        else -> null
    }

    fun pin(value: String): String? = if (PIN_REGEX.matches(value)) null else tr("PIN 碼必須是 4 位數字", "PIN must be 4 digits")

    fun pinConfirm(pin: String, confirm: String): String? = if (pin == confirm) null else tr("兩次輸入的 PIN 碼不一致", "The two PINs do not match")

    fun registrationCode(value: String): String? = if (value.isBlank()) tr("請輸入團部通行碼", "Enter the department access code") else null

    /** PIN 輸入框只接受數字，最多 4 碼。 */
    fun sanitizePin(input: String): String = input.filter { it.isDigit() }.take(4)
}

/** 把 API 錯誤轉成一般使用者看得懂的訊息（不顯示技術細節）。 */
fun AppError.toUserMessage(): String = when (this) {
    AppError.Network -> tr("無法連線到伺服器，請確認網路連線，以及伺服器是否已開啟。", "Cannot reach the server. Check your connection and that the server is running.")
    is AppError.Server -> tr("伺服器發生錯誤，請稍後再試。", "Server error. Please try again later.")
    is AppError.Unavailable -> when (code) {
        "AI_NOT_CONFIGURED" -> tr("AI 助理尚未設定：伺服器沒有讀到 GROQ_API_KEY。請確認 backend/.env 後重新啟動後端。", "The AI assistant is not configured: the server has no GROQ_API_KEY. Check backend/.env and restart the backend.")
        "AI_ACCESS_DENIED" -> tr("Groq 拒絕了伺服器的連線，常見原因是伺服器電腦開著 VPN 或所在網路受限。請關閉 VPN 後再試。", "Groq refused the server's connection, usually because the server computer is on a VPN or a restricted network. Turn off the VPN and try again.")
        "AI_KEY_REJECTED" -> tr("Groq 不接受目前的金鑰（可能無效、複製不完整或已撤銷）。請重新建立金鑰後更新 backend/.env 並重新啟動後端。", "Groq rejected the API key (invalid, incomplete or revoked). Create a new key, update backend/.env and restart the backend.")
        "AI_BUSY" -> tr("AI 助理目前使用量已達上限，請稍後再試。", "The AI assistant has reached its usage limit. Please try again later.")
        else -> tr("AI 助理暫時無法使用，請稍後再試。", "The AI assistant is unavailable. Please try again later.")
    }
    is AppError.Auth -> when (code) {
        "INVALID_CREDENTIALS" -> tr("使用者名稱或 PIN 碼錯誤。", "Wrong username or PIN.")
        "INVALID_REGISTRATION_CODE" -> tr("團部通行碼錯誤。", "Wrong department access code.")
        "TOO_MANY_ATTEMPTS" -> tr("錯誤次數過多，請 15 分鐘後再試。", "Too many attempts. Try again in 15 minutes.")
        else -> tr("登入已失效，請重新登入。", "Your session has expired. Please log in again.")
    }
    is AppError.Validation -> when (code) {
        "USERNAME_TAKEN" -> tr("此使用者名稱已被使用。", "This username is already taken.")
        "ACTION_NOT_FOUND" -> tr("這個動作已失效（超過 10 分鐘或已執行過），請重新提出需求。", "This action has expired (over 10 minutes old or already done). Please ask again.")
        "NOT_FOUND" -> tr("找不到這筆資料，可能已被刪除。", "Not found. It may have been deleted.")
        else -> tr("輸入資料有誤，請檢查後再試。", "Some input is invalid. Please check and try again.")
    }
}
