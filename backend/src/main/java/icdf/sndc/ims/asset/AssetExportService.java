package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetDtos.ExportRequest;
import icdf.sndc.ims.asset.AssetDtos.WriteOffExportRequest;
import icdf.sndc.ims.asset.AssetDtos.WriteOffItem;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.period.PeriodService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 勾選匯出：產生與主管「財產管理個人清單」相同版面的 .xlsx（規格書第 4.6 節）。
 * 工作表名稱為盤點期別（例如 2026Q3）；每位保管人一個區塊（標題、保管人、表頭、資料、小計），列印時各自分頁。
 */
@Service
public class AssetExportService {

    static final String DEFAULT_MISSION = "駐索馬利蘭技術團";
    private static final String[] HEADER_TOP = {"財產編號", "財產名稱", "規格及摘要", "購置", null, "購置類型\n經費來源",
            "放置地點", "單位", "數量", "價值計算", null, "備註"};
    private static final String[] HEADER_SUB = {null, null, null, "年度", "月份", null, null, null, null, "台幣", "美金", null};
    private static final int[] WIDTHS = {18, 14, 36, 7, 6, 11, 14, 6, 6, 12, 12, 30};

    public record ExportFile(String fileName, byte[] content) {}

    private final AssetRepository assets;
    private final Clock clock;

    public AssetExportService(AssetRepository assets, Clock clock) {
        this.assets = assets;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExportFile export(ExportRequest req) {
        PeriodService.validate(req.year(), req.quarter());
        List<Asset> found = assets.findByIdInAndDeletedAtIsNull(new HashSet<>(req.ids()));
        if (found.isEmpty()) {
            throw ApiException.notFound("Asset");
        }
        LocalDate today = LocalDate.now(clock);
        int titleYear = req.year() != null ? req.year() : today.getYear();
        String mission = mostCommon(found.stream().map(Asset::getTechMission).filter(Objects::nonNull).toList());
        if (mission == null) {
            mission = DEFAULT_MISSION;
        }

        // 依保管人分工作表（名稱排序；未指定保管人的放最後）
        Comparator<String> byName = Comparator.nullsLast(Comparator.<String>naturalOrder());
        Map<String, List<Asset>> byCustodian = new TreeMap<String, List<Asset>>(byName);
        for (Asset a : found) {
            String name = a.getManager() == null ? null : a.getManager().getDisplayName();
            byCustodian.computeIfAbsent(name, k -> new ArrayList<>()).add(a);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            // 工作表名稱為盤點期別（例如 2026Q3）；未指定期別時用今天所在的季度
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(sheetName(req.year(), req.quarter(), today)));
            int page = 1, start = 0;
            for (Map.Entry<String, List<Asset>> e : byCustodian.entrySet()) {
                String custodian = e.getKey() == null ? "未指定" : e.getKey();
                List<Asset> list = e.getValue().stream().sorted(Comparator.comparing(Asset::getAssetCode)).toList();
                if (start > 0) {
                    sheet.setRowBreak(start - 1);   // 列印時每位保管人從新的一頁開始
                }
                // 每位保管人一個區塊（與主管清單的分頁相同）：標題、製表日期、保管人、表頭、資料、小計
                start = writeBlock(sheet, st, start, mission + titleYear + "年財產管理個人清單", today, page++,
                        byCustodian.size(), custodian, list) + 2;
            }
            for (int c = 0; c < WIDTHS.length; c++) {
                sheet.setColumnWidth(c, WIDTHS[c] * 256);
            }
            if (byCustodian.size() == 1) {
                sheet.createFreezePane(0, 5);
            }
            wb.write(out);
            String period = req.year() == null ? "" : "_" + req.year() + (req.quarter() == null ? "" : "Q" + req.quarter());
            String fileName = "財產清單" + period + "_" + today.toString().replace("-", "") + ".xlsx";
            return new ExportFile(fileName, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 報銷核算的檔名固定為中文（不論 App 介面語言）。 */
    static final String WRITE_OFF_FILE_NAME = "報銷核算.xlsx";
    private static final String[] WRITE_OFF_HEADER = {"No.", "Asset ID", "Asset Type", "Specification", "Location", "Remarks"};
    private static final double[] WRITE_OFF_WIDTHS = {14.75, 15.75, 18.25, 29.875, 17.5, 43.875};

    /**
     * 報銷核算：依主管提供的格式（標題列「計畫名稱 YYYY年Qn」、「報廢物品:」、No.／Asset ID／Asset Type／
     * Specification／Location／Remarks）。saveRemarks 時把編輯過的備註寫回資產。
     */
    @Transactional
    public ExportFile exportWriteOff(WriteOffExportRequest req) {
        PeriodService.validate(req.year(), req.quarter());
        if (req.quarter() != null && req.year() == null) {
            throw ApiException.invalidField("year", "Select a year before selecting a quarter");
        }
        Map<java.util.UUID, Asset> byId = assets.findByIdInAndDeletedAtIsNull(
                        req.items().stream().map(WriteOffItem::id).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getId, Function.identity()));
        if (byId.isEmpty()) {
            throw ApiException.notFound("Asset");
        }

        String title = TextNormalizer.normalize(req.title());
        if (title == null) {
            title = "報銷核算";
        }
        if (req.year() != null) {
            title += " " + req.year() + "年" + (req.quarter() == null ? "" : "Q" + req.quarter());
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font kai = wb.createFont();
            kai.setFontName("標楷體");
            kai.setFontHeightInPoints((short) 12);
            Font arial = wb.createFont();
            arial.setFontName("Arial");
            arial.setFontHeightInPoints((short) 12);

            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(kai);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle plain = wb.createCellStyle();
            plain.setFont(kai);
            CellStyle header = Styles.bordered(wb);
            header.setFont(arial);
            CellStyle cell = Styles.bordered(wb);
            cell.setFont(kai);
            cell.setVerticalAlignment(VerticalAlignment.CENTER);
            cell.setWrapText(true);

            Sheet sheet = wb.createSheet("工作表1");
            set(sheet.createRow(0), 0, title, titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            set(sheet.createRow(1), 0, "報廢物品: ", plain);
            Row h = sheet.createRow(2);
            for (int c = 0; c < WRITE_OFF_HEADER.length; c++) {
                set(h, c, WRITE_OFF_HEADER[c], header);
            }

            int rowIndex = 3, no = 1;
            Set<java.util.UUID> done = new HashSet<>();
            for (WriteOffItem item : req.items()) {
                Asset a = byId.get(item.id());
                if (a == null || !done.add(a.getId())) {
                    continue;   // 已刪除、不存在或重複的 ID 略過
                }
                String remarks = TextNormalizer.normalize(item.remarks());
                String original = a.getDescription();
                if (req.shouldSaveRemarks() && remarks != null) {
                    a.setDescription(mergeRemarks(remarks, original));
                }
                Row r = sheet.createRow(rowIndex++);
                num(r, 0, no++, cell);
                set(r, 1, a.getAssetCode(), cell);
                set(r, 2, a.getItemName(), cell);
                set(r, 3, a.getSpecification(), cell);
                set(r, 4, a.getLocation() == null ? null : a.getLocation().getName(), cell);
                // 報銷核算檔案中只放這次的備註（例如報廢原因）；沒有填時沿用資產原本的備註
                set(r, 5, remarks != null ? remarks : original, cell);
            }
            for (int c = 0; c < WRITE_OFF_WIDTHS.length; c++) {
                sheet.setColumnWidth(c, (int) Math.round(WRITE_OFF_WIDTHS[c] * 256));
            }
            if (req.shouldSaveRemarks()) {
                assets.flush();
            }
            wb.write(out);
            return new ExportFile(WRITE_OFF_FILE_NAME, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 存回資產時保留原本的備註：新備註在前，原本的備註移到後面的括號內，
     * 例如「報廢原因:超過使用年限且損壞。(財國合發字第1號)」。
     * 原本就以新備註開頭（例如重複匯出）或新備註已包含原本的內容時不再重複加上。
     */
    static String mergeRemarks(String remarks, String original) {
        if (original == null || original.isBlank() || remarks.contains(original) || original.startsWith(remarks)) {
            return original != null && original.startsWith(remarks) ? original : remarks;
        }
        String merged = remarks + "(" + original + ")";
        return merged.length() <= 2000 ? merged : merged.substring(0, 2000);
    }

    static String sheetName(Integer year, Integer quarter, LocalDate today) {
        if (year == null) {
            return today.getYear() + "Q" + ((today.getMonthValue() - 1) / 3 + 1);
        }
        return quarter == null ? String.valueOf(year) : year + "Q" + quarter;
    }

    /** 從 start 列開始寫一位保管人的區塊，回傳小計列的列號。 */
    private static int writeBlock(Sheet sheet, Styles st, int start, String title, LocalDate today, int page, int pages,
                                  String custodian, List<Asset> list) {
        Row r0 = sheet.createRow(start);
        r0.setHeightInPoints(24);
        set(r0, 0, title, st.title);
        sheet.addMergedRegion(new CellRangeAddress(start, start, 0, 11));

        Row r1 = sheet.createRow(start + 1);
        set(r1, 0, "製表日期：" + today.getYear() + "年" + two(today.getMonthValue()) + "月" + two(today.getDayOfMonth()) + "日", null);
        sheet.addMergedRegion(new CellRangeAddress(start + 1, start + 1, 0, 1));
        set(r1, 10, "頁次:" + page + "/" + pages, null);
        sheet.addMergedRegion(new CellRangeAddress(start + 1, start + 1, 10, 11));

        set(sheet.createRow(start + 2), 0, "財產保管人：" + custodian, null);

        int h = start + 3;
        Row h1 = sheet.createRow(h);
        Row h2 = sheet.createRow(h + 1);
        h1.setHeightInPoints(30);
        for (int c = 0; c < 12; c++) {
            set(h1, c, HEADER_TOP[c], st.header);
            set(h2, c, HEADER_SUB[c], st.header);
        }
        for (int c : new int[]{0, 1, 2, 5, 6, 7, 8, 11}) {
            sheet.addMergedRegion(new CellRangeAddress(h, h + 1, c, c));
        }
        sheet.addMergedRegion(new CellRangeAddress(h, h, 3, 4));
        sheet.addMergedRegion(new CellRangeAddress(h, h, 9, 10));

        int rowIndex = h + 2;
        long qty = 0;
        BigDecimal twd = null, usd = null;
        for (Asset a : list) {
            Row r = sheet.createRow(rowIndex++);
            set(r, 0, a.getAssetCode(), st.text);
            set(r, 1, a.getItemName(), st.text);
            set(r, 2, a.getSpecification(), st.text);
            if (a.getPurchaseDate() != null) {
                num(r, 3, a.getPurchaseDate().getYear(), st.integer);
                num(r, 4, a.getPurchaseDate().getMonthValue(), st.integer);
            } else {
                set(r, 3, null, st.text);
                set(r, 4, null, st.text);
            }
            set(r, 5, a.getAcquisitionType(), st.text);
            set(r, 6, a.getLocation() == null ? null : a.getLocation().getName(), st.text);
            set(r, 7, a.getUnit(), st.text);
            num(r, 8, a.getQuantity(), st.integer);
            money(r, 9, a.getValueTwd(), st.twd);
            money(r, 10, a.getValueUsd(), st.usd);
            set(r, 11, a.getDescription(), st.text);
            qty += a.getQuantity();
            twd = add(twd, a.getValueTwd());
            usd = add(usd, a.getValueUsd());
        }
        Row total = sheet.createRow(rowIndex);
        set(total, 7, "小計:", st.text);
        num(total, 8, qty, st.integer);
        money(total, 9, twd, st.twd);
        money(total, 10, usd, st.usd);
        return rowIndex;
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static BigDecimal add(BigDecimal sum, BigDecimal v) {
        if (v == null) return sum;
        return sum == null ? v : sum.add(v);
    }

    private static void set(Row r, int c, String v, CellStyle style) {
        Cell cell = r.createCell(c);
        if (v != null) {
            cell.setCellValue(v);   // 一律寫成文字儲存格，內容以「=」開頭也不會被當成公式
        }
        if (style != null) cell.setCellStyle(style);
    }

    private static void num(Row r, int c, long v, CellStyle style) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(style);
    }

    private static void money(Row r, int c, BigDecimal v, CellStyle style) {
        Cell cell = r.createCell(c);
        if (v != null) cell.setCellValue(v.doubleValue());
        cell.setCellStyle(style);
    }

    private static String mostCommon(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static final class Styles {
        final CellStyle title, header, text, integer, twd, usd;

        Styles(XSSFWorkbook wb) {
            DataFormat fmt = wb.createDataFormat();
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFont(big);
            title.setAlignment(HorizontalAlignment.CENTER);

            header = bordered(wb);
            header.setFont(bold);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);

            text = bordered(wb);
            text.setVerticalAlignment(VerticalAlignment.CENTER);
            text.setWrapText(true);

            integer = bordered(wb);
            integer.setAlignment(HorizontalAlignment.CENTER);
            integer.setDataFormat(fmt.getFormat("0"));

            twd = bordered(wb);
            twd.setDataFormat(fmt.getFormat("#,##0"));

            usd = bordered(wb);
            usd.setDataFormat(fmt.getFormat("#,##0.00"));
        }

        static CellStyle bordered(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }
}
