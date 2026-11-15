package icdf.sndc.ims.auth;

import icdf.sndc.ims.config.AuthProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 最簡單的登入失敗次數限制：四位數 PIN 只有一萬種組合，沒有限制可被快速猜中。
 * 同一使用者名稱連續失敗 N 次後暫停登入一段時間；成功登入即重置。狀態只存在記憶體中，重啟後清空。
 */
@Component
public class LoginAttemptLimiter {

    private record State(int failures, Instant lockedUntil) {}

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final AuthProperties props;
    private final Clock clock;

    public LoginAttemptLimiter(AuthProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        State s = states.get(username);
        return s != null && s.lockedUntil() != null && clock.instant().isBefore(s.lockedUntil());
    }

    public void recordFailure(String username) {
        states.compute(username, (k, s) -> {
            int failures = (s == null || (s.lockedUntil() != null && !clock.instant().isBefore(s.lockedUntil())))
                    ? 1 : s.failures() + 1;
            Instant lockedUntil = failures >= props.maxFailedLogins()
                    ? clock.instant().plus(props.lockoutDuration()) : null;
            return new State(failures, lockedUntil);
        });
    }

    public void recordSuccess(String username) {
        states.remove(username);
    }
}
