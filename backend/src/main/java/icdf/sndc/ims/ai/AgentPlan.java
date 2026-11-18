package icdf.sndc.ims.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** 模型輸出（尚未驗證）。所有內容都必須經 AgentService 驗證後才會使用。 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AgentPlan(String intent, Filters filters, Changes changes, String clarificationQuestion) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Filters(
            String keyword, String assetCode, String itemName, String techMission, String color,
            String locationName, String custodianName, String expiryStatus,
            String purchasedFrom, String purchasedTo, String expiresFrom, String expiresTo,
            Integer minQuantity, Integer year, Integer quarter) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Changes(
            String assetCode, String itemName, String techMission, String color, String description,
            String locationName, String custodianName, String purchaseDate,
            BigDecimal usefulLifeYears, Integer quantity, String specification, String unit) {

        boolean isEmpty() {
            return assetCode == null && itemName == null && techMission == null && color == null
                    && description == null && locationName == null && custodianName == null
                    && purchaseDate == null && usefulLifeYears == null && quantity == null
                    && specification == null && unit == null;
        }
    }
}
