package icdf.sndc.ims.period;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class PeriodDtos {

    private PeriodDtos() {}

    /** App 以此建立「年度 → 季度」兩層下拉選單。 */
    public record PeriodResponse(int year, int quarter, String label, long assetCount) {}

    /** 資產上的期別（不含筆數）。 */
    public record PeriodRef(
            @NotNull(message = "Year is required")
            @Min(value = 2000, message = "Year must be between 2000 and 2999")
            @Max(value = 2999, message = "Year must be between 2000 and 2999")
            Integer year,
            @NotNull(message = "Quarter is required")
            @Min(value = 1, message = "Quarter must be between 1 and 4")
            @Max(value = 4, message = "Quarter must be between 1 and 4")
            Integer quarter) {

        public static PeriodRef of(InventoryPeriod p) {
            return new PeriodRef(p.getYear(), p.getQuarter());
        }
    }
}
