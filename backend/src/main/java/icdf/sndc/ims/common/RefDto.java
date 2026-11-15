package icdf.sndc.ims.common;

import java.util.UUID;

/** 關聯物件的精簡表示（例如資產上的管理人、位置）。 */
public record RefDto(UUID id, String name) {}
