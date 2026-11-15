package icdf.sndc.ims.asset;

import icdf.sndc.ims.common.RefDto;
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
import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() {}

    /**
     * 新增與修改共用（PUT 為整筆取代）。
     * 必填：編號、名稱、所屬技術團、購置日期、存放位置、使用年限、保管人。選填：顏色、備註；數量預設 1。
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

            @NotNull(message = "Useful life years is required")
            @DecimalMin(value = "0", inclusive = false, message = "Useful life years must be greater than zero")
            @DecimalMax(value = "999.9", message = "Useful life years is too large")
            @Digits(integer = 3, fraction = 1, message = "Useful life years allows at most one decimal place")
            BigDecimal usefulLifeYears,

            @NotNull(message = "Location is required")
            UUID locationId,

            @Size(max = 50, message = "Color must be at most 50 characters")
            String color,

            /** 未提供時預設為 1。 */
            @Positive(message = "Quantity must be greater than zero")
            Integer quantity,

            @Size(max = 2000, message = "Description must be at most 2000 characters")
            String description
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
            String color,
            int quantity,
            String description,
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
                    a.getColor(),
                    a.getQuantity(),
                    a.getDescription(),
                    a.getCreatedAt(),
                    a.getUpdatedAt());
        }
    }

    /** GET /assets 的查詢條件，全部選填且以 AND 組合。keyword 會同時比對名稱與編號（部分符合）。 */
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
            Integer minQuantity
    ) {}
}
