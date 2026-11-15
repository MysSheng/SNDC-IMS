package icdf.sndc.ims.auth;

import icdf.sndc.ims.auth.AuthDtos.AuthResponse;
import icdf.sndc.ims.auth.AuthDtos.LoginRequest;
import icdf.sndc.ims.auth.AuthDtos.RegisterRequest;
import icdf.sndc.ims.auth.AuthDtos.UserResponse;
import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.config.AuthProperties;
import icdf.sndc.ims.security.JwtService;
import icdf.sndc.ims.user.User;
import icdf.sndc.ims.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class AuthService {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter limiter;
    private final AuthProperties props;
    /** 用於「使用者不存在」時仍執行一次 BCrypt 比對，讓回應時間一致。 */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptLimiter limiter, AuthProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limiter = limiter;
        this.props = props;
        this.dummyHash = passwordEncoder.encode("dummy-pin-for-timing");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (!constantTimeEquals(req.registrationCode(), props.registrationCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_REGISTRATION_CODE",
                    "Registration code is incorrect");
        }
        if (!req.pin().equals(req.pinConfirm())) {
            throw ApiException.invalidField("pinConfirm", "PIN confirmation does not match");
        }
        String username = normalizeUsername(req.username());
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken", "username");
        }
        User user = userRepository.save(new User(username, passwordEncoder.encode(req.pin())));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String username = normalizeUsername(req.username());
        if (limiter.isLocked(username)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Please try again later");
        }
        User user = userRepository.findByUsername(username).orElse(null);
        boolean ok = passwordEncoder.matches(req.pin(), user == null ? dummyHash : user.getPinHash());
        if (user == null || !ok) {
            limiter.recordFailure(username);
            // 不區分「帳號不存在」與「PIN 錯誤」，避免洩漏帳號是否存在
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Username or PIN is incorrect");
        }
        limiter.recordSuccess(username);
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication required or token is invalid"));
        return toUserResponse(user);
    }

    // ------------------------------------------------------------------

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), toUserResponse(user));
    }

    private static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getCreatedAt());
    }

    /** 使用者名稱：NFKC 正規化並去除前後空白；區分大小寫。 */
    static String normalizeUsername(String raw) {
        String v = TextNormalizer.normalize(raw);
        if (v == null) {
            throw ApiException.invalidField("username", "Username is required");
        }
        if (v.length() > 30) {
            throw ApiException.invalidField("username", "Username must be at most 30 characters");
        }
        if (v.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalidField("username", "Username contains invalid characters");
        }
        return v;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
