package icdf.sndc.ims.common;

import java.time.Instant;
import java.util.List;

/** 統一錯誤回應格式（規格書第 11.2 節）。 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldDetail> details
) {
    public record FieldDetail(String field, String message) {}

    public static ApiError of(int status, String code, String message, List<FieldDetail> details) {
        return new ApiError(Instant.now(), status, code, message, details == null ? List.of() : details);
    }
}
