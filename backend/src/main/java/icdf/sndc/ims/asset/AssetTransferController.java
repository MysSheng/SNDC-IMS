package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetDtos.ExportRequest;
import icdf.sndc.ims.asset.AssetDtos.WriteOffExportRequest;
import icdf.sndc.ims.asset.AssetImportDtos.ImportPreview;
import icdf.sndc.ims.asset.AssetImportDtos.ImportResult;
import icdf.sndc.ims.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Excel／CSV 匯入與勾選匯出（規格書第 4.6 節）。 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetTransferController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AssetImportService importService;
    private final AssetExportService exportService;

    public AssetTransferController(AssetImportService importService, AssetExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    /** 上傳 .xlsx／.xls／.csv 並檢查；不寫入資料庫。year／quarter 未提供時會嘗試從檔名判斷。 */
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(@AuthenticationPrincipal Jwt jwt,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) Integer quarter,
                                 @RequestParam(required = false) String techMission) throws IOException {
        if (file.getSize() > AssetImportService.MAX_FILE_BYTES) {
            throw ApiException.invalidField("file", "File must be at most 10 MB");
        }
        return importService.preview(userId(jwt), file.getOriginalFilename(), file.getBytes(), year, quarter, techMission);
    }

    /** 寫入預覽過的資料；任一筆失敗則整批不寫入。 */
    @PostMapping("/import/{token}/confirm")
    public ImportResult confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID token) {
        return importService.confirm(userId(jwt), token);
    }

    @DeleteMapping("/import/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID token) {
        importService.discard(userId(jwt), token);
    }

    /** 匯出勾選的資產為主管清單格式的 .xlsx。 */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        AssetExportService.ExportFile f = exportService.export(request);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    /** 匯出報銷核算 .xlsx（檔名固定為「報銷核算.xlsx」）；saveRemarks 時同時更新資產備註。 */
    @PostMapping("/export/write-off")
    public ResponseEntity<byte[]> exportWriteOff(@Valid @RequestBody WriteOffExportRequest request) {
        return file(exportService.exportWriteOff(request));
    }

    private static ResponseEntity<byte[]> file(AssetExportService.ExportFile f) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
