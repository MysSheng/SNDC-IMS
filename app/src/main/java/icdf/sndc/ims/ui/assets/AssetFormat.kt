package icdf.sndc.ims.ui.assets

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import icdf.sndc.ims.ui.common.tr

/** 日期與數字的顯示／輸入轉換。畫面上的日期格式為 YYYY/MM/DD，API 為 YYYY-MM-DD。 */
object AssetFormat {
    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT)
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun isoToDisplay(iso: String?): String =
        iso?.let { runCatching { LocalDate.parse(it, ISO).format(DISPLAY) }.getOrNull() } ?: ""

    /** 解析 YYYY/MM/DD；不合法回傳 null。 */
    fun parseDisplayDate(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim(), DISPLAY)
        } catch (e: DateTimeParseException) {
            null
        }

    fun purchaseDateError(text: String): String? {
        if (text.isBlank()) return tr("請選擇購置日期", "Select a purchase date")
        val date = parseDisplayDate(text) ?: return tr("格式須為 2xxx/xx/xx，例如 2024/01/15", "Use the format 2xxx/xx/xx, e.g. 2024/01/15")
        return if (date.year in 2000..2999) null else tr("年份須為 2000 至 2999", "Year must be between 2000 and 2999")
    }

    fun toIso(text: String): String = parseDisplayDate(text)!!.format(ISO)

    fun toDisplay(date: LocalDate): String = date.format(DISPLAY)

    /** 日曆元件使用的 UTC 毫秒值 ↔ LocalDate。 */
    fun toEpochMillis(date: LocalDate): Long = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromEpochMillis(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()

    /** 使用年限為選填：空白代表不計算到期日。 */
    fun usefulLifeError(text: String): String? {
        if (text.isBlank()) return null
        val v = text.trim().toBigDecimalOrNull() ?: return tr("請輸入數字，例如 5 或 2.5", "Enter a number, e.g. 5 or 2.5")
        return when {
            v <= BigDecimal.ZERO -> tr("使用年限必須大於 0", "Useful life must be greater than 0")
            v > BigDecimal("999.9") -> tr("使用年限過大", "Useful life is too large")
            v.stripTrailingZeros().scale() > 1 -> tr("最多一位小數", "At most one decimal place")
            else -> null
        }
    }

    /** 金額為選填：0 以上、最多兩位小數，可含千分位逗號。 */
    fun moneyError(text: String): String? {
        if (text.isBlank()) return null
        val v = text.replace(",", "").trim().toBigDecimalOrNull() ?: return tr("請輸入數字，例如 650 或 1,990.00", "Enter a number, e.g. 650 or 1,990.00")
        return when {
            v < BigDecimal.ZERO -> tr("金額不可為負數", "Value cannot be negative")
            v.stripTrailingZeros().scale() > 2 -> tr("最多兩位小數", "At most two decimal places")
            v.precision() - v.scale() > 12 -> tr("金額過大", "Value is too large")
            else -> null
        }
    }

    fun parseMoney(text: String): Double? = text.replace(",", "").trim().toBigDecimalOrNull()?.toDouble()

    /** 650.0 顯示為 650，1990.5 顯示為 1990.5。 */
    fun money(value: Double?): String =
        value?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: ""

    fun quantityError(text: String): String? {
        val v = text.trim().toIntOrNull() ?: return tr("請輸入整數", "Enter a whole number")
        return if (v > 0) null else tr("數量必須大於 0", "Quantity must be greater than 0")
    }

    /** 5.0 顯示為 5，2.5 顯示為 2.5。 */
    fun years(value: Double?): String =
        value?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: ""
}
