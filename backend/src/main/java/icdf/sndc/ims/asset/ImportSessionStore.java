package icdf.sndc.ims.asset;

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
 * 暫存「已預覽、等待確認」的匯入內容。只存在記憶體，10 分鐘後失效；每位使用者同時只保留最新一份。
 * 確認時寫入的就是預覽時檢查過的那些資料列，不需要重新上傳檔案。
 */
@Component
class ImportSessionStore {

    static final Duration TTL = Duration.ofMinutes(10);

    record Session(UUID token, UUID ownerId, int year, int quarter, String techMission,
                   List<AssetImportService.ImportRow> rows, Instant expiresAt) {}

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    ImportSessionStore(Clock clock) {
        this.clock = clock;
    }

    Session create(UUID ownerId, int year, int quarter, String techMission, List<AssetImportService.ImportRow> rows) {
        Instant now = clock.instant();
        sessions.values().removeIf(s -> !now.isBefore(s.expiresAt()) || s.ownerId().equals(ownerId));
        Session s = new Session(UUID.randomUUID(), ownerId, year, quarter, techMission, List.copyOf(rows), now.plus(TTL));
        sessions.put(s.token(), s);
        return s;
    }

    /** 只有建立者本人、且尚未過期時才取得；取得後即移除（同一份預覽只能確認一次）。 */
    Optional<Session> take(UUID token, UUID ownerId) {
        Session s = sessions.get(token);
        if (s == null || !s.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        sessions.remove(token);
        return clock.instant().isBefore(s.expiresAt()) ? Optional.of(s) : Optional.empty();
    }

    void discard(UUID token, UUID ownerId) {
        Session s = sessions.get(token);
        if (s != null && s.ownerId().equals(ownerId)) {
            sessions.remove(token);
        }
    }
}
