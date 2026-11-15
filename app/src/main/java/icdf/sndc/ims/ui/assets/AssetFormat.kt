package icdf.sndc.ims.ui.assets

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

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
        if (text.isBlank()) return "請選擇購置日期"
        val date = parseDisplayDate(text) ?: return "格式須為 2xxx/xx/xx，例如 2024/01/15"
        return if (date.year in 2000..2999) null else "年份須為 2000 至 2999"
    }

    fun toIso(text: String): String = parseDisplayDate(text)!!.format(ISO)

    fun toDisplay(date: LocalDate): String = date.format(DISPLAY)

    /** 日曆元件使用的 UTC 毫秒值 ↔ LocalDate。 */
    fun toEpochMillis(date: LocalDate): Long = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromEpochMillis(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()

    fun usefulLifeError(text: String): String? {
        if (text.isBlank()) return "請輸入使用年限"
        val v = text.trim().toBigDecimalOrNull() ?: return "請輸入數字，例如 5 或 2.5"
        return when {
            v <= BigDecimal.ZERO -> "使用年限必須大於 0"
            v > BigDecimal("999.9") -> "使用年限過大"
            v.stripTrailingZeros().scale() > 1 -> "最多一位小數"
            else -> null
        }
    }

    fun quantityError(text: String): String? {
        val v = text.trim().toIntOrNull() ?: return "請輸入整數"
        return if (v > 0) null else "數量必須大於 0"
    }

    /** 5.0 顯示為 5，2.5 顯示為 2.5。 */
    fun years(value: Double?): String =
        value?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: ""
}
