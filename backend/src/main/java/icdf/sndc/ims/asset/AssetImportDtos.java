package icdf.sndc.ims.asset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssetImportDtos {

    private AssetImportDtos() {}

    /** 一個錯誤：row 為 Excel／CSV 中的列號（從 1 開始）；檔案層級的錯誤 row 為 null。 */
    public record ImportError(String sheet, Integer row, String assetCode, String message) {}

    /**
     * 匯入預覽：尚未寫入資料庫。errors 為空時才會有 token，App 以 token 呼叫 confirm 才真正寫入。
     * errors 最多回傳 200 筆，errorCount 為實際總數。
     */
    public record ImportPreview(
            UUID token,
            String fileName,
            int year,
            int quarter,
            String period,
            String techMission,
            int totalRows,
            int createCount,
            int updateCount,
            int restoreCount,
            List<String> newLocations,
            List<String> newCustodians,
            int errorCount,
            List<ImportError> errors,
            Instant expiresAt) {}

    public record ImportResult(String period, int created, int updated, int restored) {}
}
