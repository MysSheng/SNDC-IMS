package icdf.sndc.ims.common;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 業務例外；由 GlobalExceptionHandler 轉為統一錯誤格式。 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiError.FieldDetail> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<ApiError.FieldDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException conflict(String code, String message, String field) {
        return new ApiException(HttpStatus.CONFLICT, code, message,
                List.of(new ApiError.FieldDetail(field, message)));
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                List.of(new ApiError.FieldDetail(field, message)));
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<ApiError.FieldDetail> getDetails() { return details; }
}
