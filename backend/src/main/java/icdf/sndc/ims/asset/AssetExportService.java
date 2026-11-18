package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetDtos.ExportRequest;
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
 * 每位保管人一個工作表（與主管的清單一樣以保管人為單位），最後一列為小計。
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
            Set<String> usedNames = new HashSet<>();
            int page = 1;
            for (Map.Entry<String, List<Asset>> e : byCustodian.entrySet()) {
                String custodian = e.getKey() == null ? "未指定" : e.getKey();
                Sheet sheet = wb.createSheet(uniqueSheetName(custodian, usedNames));
                List<Asset> list = e.getValue().stream().sorted(Comparator.comparing(Asset::getAssetCode)).toList();
                writeSheet(sheet, st, mission + titleYear + "年財產管理個人清單", today, page++, byCustodian.size(),
                        custodian, list);
            }
            wb.write(out);
            String period = req.year() == null ? "" : "_" + req.year() + (req.quarter() == null ? "" : "Q" + req.quarter());
            String fileName = "財產清單" + period + "_" + today.toString().replace("-", "") + ".xlsx";
            return new ExportFile(fileName, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeSheet(Sheet sheet, Styles st, String title, LocalDate today, int page, int pages,
                                   String custodian, List<Asset> list) {
        Row r0 = sheet.createRow(0);
        r0.setHeightInPoints(24);
        set(r0, 0, title, st.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

        Row r1 = sheet.createRow(1);
        set(r1, 0, "製表日期：" + today.getYear() + "年" + two(today.getMonthValue()) + "月" + two(today.getDayOfMonth()) + "日", null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));
        set(r1, 10, "頁次:" + page + "/" + pages, null);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 10, 11));

        set(sheet.createRow(2), 0, "財產保管人：" + custodian, null);

        Row h1 = sheet.createRow(3);
        Row h2 = sheet.createRow(4);
        h1.setHeightInPoints(30);
        for (int c = 0; c < 12; c++) {
            set(h1, c, HEADER_TOP[c], st.header);
            set(h2, c, HEADER_SUB[c], st.header);
        }
        for (int c : new int[]{0, 1, 2, 5, 6, 7, 8, 11}) {
            sheet.addMergedRegion(new CellRangeAddress(3, 4, c, c));
        }
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 3, 4));
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 9, 10));

        int rowIndex = 5;
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

        for (int c = 0; c < WIDTHS.length; c++) {
            sheet.setColumnWidth(c, WIDTHS[c] * 256);
        }
        sheet.createFreezePane(0, 5);
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

    /** Excel 工作表名稱最多 31 字、不可含 \ / ? * [ ] :，且不可重複。 */
    private static String uniqueSheetName(String name, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(name);
        String candidate = base;
        for (int i = 2; !used.add(candidate.toLowerCase()); i++) {
            String suffix = "(" + i + ")";
            candidate = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
        }
        return candidate;
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

        private static CellStyle bordered(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }
}
