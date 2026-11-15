package icdf.sndc.ims.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** 分頁回應；page 從 0 開始。 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static <E, R> PageResponse<R> from(Page<E> page, Function<E, R> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
