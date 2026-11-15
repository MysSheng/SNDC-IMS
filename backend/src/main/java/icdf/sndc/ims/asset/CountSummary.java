package icdf.sndc.ims.asset;

/** 數量統計：符合條件的紀錄筆數與 quantity 合計，由資料庫計算。 */
public record CountSummary(long recordCount, long totalQuantity) {}
