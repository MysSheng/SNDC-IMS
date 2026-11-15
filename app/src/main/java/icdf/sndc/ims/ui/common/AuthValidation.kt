package icdf.sndc.ims.ui.common

import icdf.sndc.ims.data.remote.AppError

/** 送出前的表單檢查，規則與後端一致。回傳 null 代表通過。 */
object AuthValidation {
    private val PIN_REGEX = Regex("^[0-9]{4}$")

    fun username(value: String): String? = when {
        value.isBlank() -> "請輸入使用者名稱"
        value.trim().length > 30 -> "使用者名稱最多 30 個字"
        else -> null
    }

    fun pin(value: String): String? = if (PIN_REGEX.matches(value)) null else "PIN 碼必須是 4 位數字"

    fun pinConfirm(pin: String, confirm: String): String? = if (pin == confirm) null else "兩次輸入的 PIN 碼不一致"

    fun registrationCode(value: String): String? = if (value.isBlank()) "請輸入團部通行碼" else null

    /** PIN 輸入框只接受數字，最多 4 碼。 */
    fun sanitizePin(input: String): String = input.filter { it.isDigit() }.take(4)
}

/** 把 API 錯誤轉成一般使用者看得懂的訊息（不顯示技術細節）。 */
fun AppError.toUserMessage(): String = when (this) {
    AppError.Network -> "無法連線到伺服器，請確認網路連線，以及伺服器是否已開啟。"
    is AppError.Server -> "伺服器發生錯誤，請稍後再試。"
    is AppError.Auth -> when (code) {
        "INVALID_CREDENTIALS" -> "使用者名稱或 PIN 碼錯誤。"
        "INVALID_REGISTRATION_CODE" -> "團部通行碼錯誤。"
        "TOO_MANY_ATTEMPTS" -> "錯誤次數過多，請 15 分鐘後再試。"
        else -> "登入已失效，請重新登入。"
    }
    is AppError.Validation -> when (code) {
        "USERNAME_TAKEN" -> "此使用者名稱已被使用。"
        else -> "輸入資料有誤，請檢查後再試。"
    }
}
