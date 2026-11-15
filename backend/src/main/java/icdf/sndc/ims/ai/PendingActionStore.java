package icdf.sndc.ims.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暫存「等待使用者確認」的修改／刪除動作。只存在記憶體中，10 分鐘後失效，後端重啟後清空。
 * 確認時執行的是當初列給使用者看的那幾筆資產（以 ID 固定），不會重新查詢而多刪到其他資產。
 */
@Component
public class PendingActionStore {

    public static final Duration TTL = Duration.ofMinutes(10);

    public enum Type { DELETE, UPDATE }

    /** 已解析的修改內容：欄位為 null 代表不修改。locationId／managerId 已確認存在。 */
    public record ResolvedChanges(
            String assetCode, String itemName, String techMission, String color, String description,
            UUID locationId, UUID managerId, java.time.LocalDate purchaseDate,
            java.math.BigDecimal usefulLifeYears, Integer quantity) {}

    public record PendingAction(UUID id, UUID ownerId, Type type, List<UUID> targetIds,
                                ResolvedChanges changes, Instant expiresAt) {}

    private final Map<UUID, PendingAction> actions = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingActionStore(Clock clock) {
        this.clock = clock;
    }

    public PendingAction create(UUID ownerId, Type type, List<UUID> targetIds, ResolvedChanges changes) {
        purgeExpired();
        PendingAction a = new PendingAction(UUID.randomUUID(), ownerId, type, List.copyOf(targetIds), changes,
                clock.instant().plus(TTL));
        actions.put(a.id(), a);
        return a;
    }

    /** 只有建立者本人、且尚未過期時才取得。 */
    public Optional<PendingAction> find(UUID id, UUID ownerId) {
        PendingAction a = actions.get(id);
        if (a == null || !a.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(a.expiresAt())) {
            actions.remove(id);
            return Optional.empty();
        }
        return Optional.of(a);
    }

    public void remove(UUID id) {
        actions.remove(id);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        actions.values().removeIf(a -> !now.isBefore(a.expiresAt()));
    }
}
