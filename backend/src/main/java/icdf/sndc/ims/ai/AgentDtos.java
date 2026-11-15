package icdf.sndc.ims.ai;

import icdf.sndc.ims.asset.AssetDtos.AssetResponse;
import icdf.sndc.ims.asset.CountSummary;
import icdf.sndc.ims.asset.DuplicateItem;
import icdf.sndc.ims.asset.ExpiryStatus;
import icdf.sndc.ims.common.RefDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentDtos {

    private AgentDtos() {}

    public record ChatTurnDto(
            @NotNull @Pattern(regexp = "user|assistant", message = "role must be user or assistant") String role,
            @NotNull @Size(max = 1000, message = "History message is too long") String content) {}

    public record AgentRequest(
            @NotBlank(message = "Message is required")
            @Size(max = 500, message = "Message must be at most 500 characters")
            String message,
            /** 先前的對話（最多 6 則），讓「把它們刪掉」這類指稱可以理解。 */
            @Size(max = 6, message = "At most 6 history messages") List<@Valid ChatTurnDto> history) {}

    /** 已驗證並解析為實際 ID 的查詢條件；App 可直接套用到清單。 */
    public record AgentFilters(
            String keyword, String assetCode, String itemName, String techMission, String color,
            RefDto location, RefDto custodian, ExpiryStatus expiryStatus,
            LocalDate purchasedFrom, LocalDate purchasedTo, LocalDate expiresFrom, LocalDate expiresTo,
            Integer minQuantity) {}

    public record AssetBrief(UUID id, String assetCode, String itemName, int quantity,
                             String location, String custodian, LocalDate expectedExpiryDate) {
        static AssetBrief from(AssetResponse a) {
            return new AssetBrief(a.id(), a.assetCode(), a.itemName(), a.quantity(),
                    a.location() == null ? null : a.location().name(),
                    a.manager() == null ? null : a.manager().name(),
                    a.expectedExpiryDate());
        }
    }

    /** 需要使用者確認後才會執行的動作（修改／刪除）。 */
    public record PendingActionDto(
            UUID id,
            String type,
            String description,
            List<AssetBrief> targets,
            /** 欄位中文名稱 → 新值（僅 UPDATE）。 */
            Map<String, String> changes,
            Instant expiresAt) {}

    public record AgentResponse(
            String intent,
            String reply,
            /** 給 App 放入對話紀錄的內容：回覆 + 條件摘要，下一輪送回 history。 */
            String historyText,
            AgentFilters filters,
            List<AssetResponse> results,
            Long totalResults,
            CountSummary summary,
            List<DuplicateItem> duplicates,
            PendingActionDto pendingAction) {

        static AgentResponse text(String intent, String reply) {
            return new AgentResponse(intent, reply, reply, null, null, null, null, null, null);
        }
    }
}
