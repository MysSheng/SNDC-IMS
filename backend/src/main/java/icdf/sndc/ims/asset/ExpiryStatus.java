package icdf.sndc.ims.asset;

import java.time.LocalDate;

/** 使用年限狀態；以 Server 業務時區（APP_TIMEZONE）的今天判斷。 */
public enum ExpiryStatus {
    /** 預計到期日已到或已過（到期日 ≤ 今天）。 */
    EXPIRED,
    /** 尚未到期，但在「即將到期」天數內（今天 < 到期日 ≤ 今天 + N 天）。 */
    EXPIRING_SOON,
    /** 尚未到期且不在即將到期範圍內。 */
    VALID;

    public static ExpiryStatus of(LocalDate expiry, LocalDate today, int soonDays) {
        if (expiry == null) {
            return null;
        }
        if (!expiry.isAfter(today)) {
            return EXPIRED;
        }
        return expiry.isAfter(today.plusDays(soonDays)) ? VALID : EXPIRING_SOON;
    }
}
