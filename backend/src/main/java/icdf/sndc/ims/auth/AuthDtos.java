package icdf.sndc.ims.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    private static final String PIN_PATTERN = "^[0-9]{4}$";
    private static final String PIN_MESSAGE = "PIN must be exactly 4 digits";

    /** PIN 一律以字串處理，保留前導零（規格書第 3.3 節）。 */
    public record RegisterRequest(
            @NotBlank(message = "Username is required")
            @Size(max = 30, message = "Username must be at most 30 characters")
            String username,

            @NotNull(message = "PIN is required")
            @Pattern(regexp = PIN_PATTERN, message = PIN_MESSAGE)
            String pin,

            @NotNull(message = "PIN confirmation is required")
            String pinConfirm,

            @NotBlank(message = "Registration code is required")
            String registrationCode
    ) {
        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + "]"; // 不記錄 PIN 與通行碼
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required")
            String username,

            @NotNull(message = "PIN is required")
            @Pattern(regexp = PIN_PATTERN, message = PIN_MESSAGE)
            String pin
    ) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]";
        }
    }

    public record UserResponse(UUID id, String username, Instant createdAt) {}

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {}
}
