package icdf.sndc.ims.asset;

import icdf.sndc.ims.common.RefDto;
import icdf.sndc.ims.period.PeriodDtos.PeriodRef;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() {}

    /**
     * 新增與修改共用（PUT 為整筆取代）。
     * 必填：編號、名稱、所屬技術團、保管人、購置日期、存放位置。
     * 選填：使用年限（未填則不計算到期日）、規格及摘要、購置類型、單位、數量（預設 1）、台幣、美金、顏色、備註。
     * periodYear／periodQuarter：選填，兩者需同時提供；會把資產加入該盤點期別（不會移除既有期別）。
     */
    public record AssetRequest(
            @NotBlank(message = "Asset code is required")
            @Size(max = 64, message = "Asset code must be at most 64 characters")
            String assetCode,

            @NotBlank(message = "Name is required")
            @Size(max = 200, message = "Name must be at most 200 characters")
            String itemName,

            @NotBlank(message = "Technical mission is required")
            @Size(max = 100, message = "Technical mission must be at most 100 characters")
            String techMission,

            @NotNull(message = "Custodian is required")
            UUID managerId,

            /** API 使用 ISO 格式 YYYY-MM-DD；App 顯示為 YYYY/MM/DD。年份需為 2000–2999。 */
            @NotNull(message = "Purchase date is required")
            LocalDate purchaseDate,

            @DecimalMin(value = "0", inclusive = false, message = "Useful life years must be greater than zero")
            @DecimalMax(value = "999.9", message = "Useful life years is too large")
            @Digits(integer = 3, fraction = 1, message = "Useful life years allows at most one decimal place")
            BigDecimal usefulLifeYears,

            @NotNull(message = "Location is required")
            UUID locationId,

            @Size(max = 2000, message = "Specification must be at most 2000 characters")
            String specification,

            @Size(max = 100, message = "Acquisition type must be at most 100 characters")
            String acquisitionType,

            @Size(max = 20, message = "Unit must be at most 20 characters")
            String unit,

            /** 未提供時預設為 1。 */
            @Positive(message = "Quantity must be greater than zero")
            Integer quantity,

            @DecimalMin(value = "0", message = "Value must not be negative")
            @Digits(integer = 12, fraction = 2, message = "Value allows at most 12 digits and 2 decimal places")
            BigDecimal valueTwd,

            @DecimalMin(value = "0", message = "Value must not be negative")
            @Digits(integer = 12, fraction = 2, message = "Value allows at most 12 digits and 2 decimal places")
            BigDecimal valueUsd,

            @Size(max = 50, message = "Color must be at most 50 characters")
            String color,

            @Size(max = 2000, message = "Description must be at most 2000 characters")
            String description,

            Integer periodYear,

            Integer periodQuarter
    ) {}

    public record AssetResponse(
            UUID id,
            String assetCode,
            String itemName,
            String techMission,
            RefDto manager,
            LocalDate purchaseDate,
            BigDecimal usefulLifeYears,
            LocalDate expectedExpiryDate,
            ExpiryStatus expiryStatus,
            RefDto location,
            String specification,
            String acquisitionType,
            String unit,
            int quantity,
            BigDecimal valueTwd,
            BigDecimal valueUsd,
            String color,
            String description,
            List<PeriodRef> periods,
            Instant createdAt,
            Instant updatedAt
    ) {
        static AssetResponse from(Asset a, LocalDate today, int soonDays) {
            LocalDate expiry = ExpiryCalculator.expectedExpiryDate(a.getPurchaseDate(), a.getUsefulLifeYears());
            return new AssetResponse(
                    a.getId(),
                    a.getAssetCode(),
                    a.getItemName(),
                    a.getTechMission(),
                    a.getManager() == null ? null : new RefDto(a.getManager().getId(), a.getManager().getDisplayName()),
                    a.getPurchaseDate(),
                    a.getUsefulLifeYears(),
                    expiry,
                    ExpiryStatus.of(expiry, today, soonDays),
                    a.getLocation() == null ? null : new RefDto(a.getLocation().getId(), a.getLocation().getName()),
                    a.getSpecification(),
                    a.getAcquisitionType(),
                    a.getUnit(),
                    a.getQuantity(),
                    a.getValueTwd(),
                    a.getValueUsd(),
                    a.getColor(),
                    a.getDescription(),
                    a.getPeriodsSorted().stream().map(PeriodRef::of).toList(),
                    a.getCreatedAt(),
                    a.getUpdatedAt());
        }
    }

    /**
     * GET /assets 的查詢條件，全部選填且以 AND 組合。keyword 會同時比對名稱、編號與規格（部分符合）。
     * year／quarter：盤點期別；只給 year 時為該年度任一季的清單，quarter 需搭配 year。
     */
    public record AssetQuery(
            String keyword,
            String assetCode,
            String itemName,
            String techMission,
            String color,
            UUID managerId,
            UUID locationId,
            ExpiryStatus expiryStatus,
            LocalDate purchasedFrom,
            LocalDate purchasedTo,
            LocalDate expiresFrom,
            LocalDate expiresTo,
            Integer minQuantity,
            Integer year,
            Integer quarter,
            /** 名稱完全符合其中任一個（AI 助理把「椅子／chair」對應到實際名稱，例如辦公椅、電腦椅）。 */
            java.util.List<String> itemNames
    ) {
        /** 是否至少有一個條件（批次修改／刪除時必須有條件，避免誤刪全部）。 */
        public boolean hasAnyCondition() {
            return keyword != null || assetCode != null || itemName != null || techMission != null || color != null
                    || managerId != null || locationId != null || expiryStatus != null || purchasedFrom != null
                    || purchasedTo != null || expiresFrom != null || expiresTo != null || minQuantity != null
                    || year != null || (itemNames != null && !itemNames.isEmpty());
        }
    }

    /** 勾選匯出：最多 1000 筆。year／quarter 選填，只用於標題列。 */
    public record ExportRequest(
            @jakarta.validation.constraints.NotEmpty(message = "Select at least one asset")
            @Size(max = 1000, message = "At most 1000 assets per export")
            List<@NotNull UUID> ids,
            Integer year,
            Integer quarter) {}

    /**
     * 報銷核算匯出（規格書第 4.6 節）。items 依畫面順序；remarks 為匯出用的備註，null 代表沿用資產目前的備註。
     * saveRemarks = true 時，同時把非空白的 remarks 寫回資產的備註。title 為計畫名稱（標題列）。
     */
    public record WriteOffExportRequest(
            @jakarta.validation.constraints.NotEmpty(message = "Select at least one asset")
            @Size(max = 1000, message = "At most 1000 assets per export")
            List<@NotNull @jakarta.validation.Valid WriteOffItem> items,
            @Size(max = 200, message = "Title must be at most 200 characters")
            String title,
            Integer year,
            Integer quarter,
            /** 用 Boolean：欄位未送出時視為 false（Jackson 3 不允許基本型別 boolean 缺值）。 */
            Boolean saveRemarks) {

        public boolean shouldSaveRemarks() {
            return Boolean.TRUE.equals(saveRemarks);
        }
    }

    public record WriteOffItem(
            @NotNull(message = "Asset id is required")
            UUID id,
            @Size(max = 2000, message = "Remarks must be at most 2000 characters")
            String remarks) {}
}
