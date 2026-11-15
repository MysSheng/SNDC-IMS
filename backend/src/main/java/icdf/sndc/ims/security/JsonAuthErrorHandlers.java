package icdf.sndc.ims.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 讓 401／403 也回傳統一錯誤格式（規格書第 11.2 節）。手寫 JSON 以避免依賴特定 Jackson 版本。 */
final class JsonAuthErrorHandlers {

    private JsonAuthErrorHandlers() {}

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required or token is invalid");
    }

    static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":[]}");
    }
}
