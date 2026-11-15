package icdf.sndc.ims.asset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 預計到期日 = 購置日期 + 使用年限（規格書第 4.2 節）。
 * 年限可含一位小數，換算為月數（四捨五入）後相加。任一欄位缺少時回傳 null，不猜測。
 */
public final class ExpiryCalculator {

    private ExpiryCalculator() {}

    public static LocalDate expectedExpiryDate(LocalDate purchaseDate, BigDecimal usefulLifeYears) {
        if (purchaseDate == null || usefulLifeYears == null || usefulLifeYears.signum() <= 0) {
            return null;
        }
        long months = usefulLifeYears.multiply(BigDecimal.valueOf(12))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return purchaseDate.plusMonths(months);
    }
}
