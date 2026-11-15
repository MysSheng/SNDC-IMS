package icdf.sndc.ims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 認證設定（application.yml 的 app.auth.*）。
 * JWT secret 與團部通行碼為必填；未設定時程式啟動即失敗並顯示明確訊息，不以預設值替代。
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        Duration jwtExpiration,
        String registrationCode,
        Integer maxFailedLogins,
        Duration lockoutDuration
) {
    public static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 未設定或長度不足：請在 backend/.env 設定至少 32 個字元的隨機字串");
        }
        if (registrationCode == null || registrationCode.isBlank()) {
            throw new IllegalStateException(
                    "DEPARTMENT_REGISTRATION_CODE 未設定：請在 backend/.env 設定團部通行碼");
        }
        if (jwtExpiration == null || jwtExpiration.isZero() || jwtExpiration.isNegative()) {
            jwtExpiration = Duration.ofHours(12);
        }
        if (maxFailedLogins == null || maxFailedLogins < 1) {
            maxFailedLogins = 5;
        }
        if (lockoutDuration == null || lockoutDuration.isNegative() || lockoutDuration.isZero()) {
            lockoutDuration = Duration.ofMinutes(15);
        }
    }

    public byte[] jwtSecretBytes() {
        return jwtSecret.getBytes(StandardCharsets.UTF_8);
    }
}
