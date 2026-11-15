package icdf.sndc.ims.asset;

/** 重複品項統計（規格書第 5.2 節）：依名稱分組，由資料庫計算。 */
public record DuplicateItem(String itemName, Long totalQuantity, Long recordCount) {}
