package icdf.sndc.ims.asset;

import icdf.sndc.ims.common.TextNormalizer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 讀取主管的財產清單（.xlsx／.xls）或 CSV，轉成尚未驗證的資料列（規格書第 4.6 節）。
 *
 * <p>Excel 格式的特徵：標題列（駐…技術團 YYYY 年財產管理個人清單）、「財產保管人：姓名」列、
 * 兩層表頭（購置 → 年度／月份；價值計算 → 台幣／美金）、最後的小計列。欄位依表頭文字對應，不依固定欄位順序，
 * 同一工作表中保管人可以換人（每遇到一次「財產保管人：」就切換）。
 *
 * <p>CSV 為單層表頭，保管人放在「保管人」欄；編碼可為 UTF-8（含 BOM）或 Excel 預設的 Big5（MS950）。
 */
final class AssetSheetReader {

    static final int MAX_DATA_ROWS = 5000;

    enum Column { CODE, NAME, SPEC, YEAR, MONTH, DATE, ACQUISITION, LOCATION, CUSTODIAN, UNIT, QTY, TWD, USD, REMARKS }

    /** 一列原始資料：文字皆已正規化（NFKC、去頭尾空白、空字串為 null），尚未驗證。 */
    record RawRow(String sheet, int rowNumber, Map<Column, String> values, String custodianFromHeader) {
        String get(Column c) {
            return values.get(c);
        }
    }

    record Result(List<RawRow> rows, String title, List<String> fileErrors) {}

    private static final Pattern CUSTODIAN = Pattern.compile("財產保管人\\s*[:：]\\s*(.+)");
    private static final Pattern MISSION = Pattern.compile("(駐.{1,30}?技術團)");

    private AssetSheetReader() {}

    static Result read(byte[] bytes) {
        if (isSpreadsheet(bytes)) {
            return readWorkbook(bytes);
        }
        return readCsv(bytes);
    }

    /** 從標題列推斷所屬技術團，例如「駐索馬利蘭技術團2026年財產管理個人清單」→「駐索馬利蘭技術團」。 */
    static String missionFromTitle(String title) {
        if (title == null) {
            return null;
        }
        Matcher m = MISSION.matcher(title);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------
    // Excel
    // ------------------------------------------------------------------

    private static boolean isSpreadsheet(byte[] b) {
        boolean zip = b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
        boolean ole2 = b.length >= 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF
                && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0;
        return zip || ole2;
    }

    private static Result readWorkbook(byte[] bytes) {
        List<String> errors = new ArrayList<>();
        List<RawRow> rows = new ArrayList<>();
        String title = null;
        boolean anyHeader = false;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            for (Sheet sheet : wb) {
                List<List<String>> table = new ArrayList<>();
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    table.add(cells(sheet.getRow(r)));
                }
                TableResult t = parseTable(sheet.getSheetName(), table, rows);
                if (t.headerFound()) {
                    anyHeader = true;
                    if (title == null) {
                        title = t.title();
                    }
                    errors.addAll(t.errors());
                }
                if (rows.size() > MAX_DATA_ROWS) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            // POI 對損毀或加密的檔案會丟出各種 RuntimeException；不回傳內部訊息
            return new Result(List.of(), null, List.of("無法讀取 Excel 檔案，請確認檔案未損毀且沒有設定密碼。"));
        }
        if (!anyHeader) {
            errors.add("找不到表頭：第一欄需為「財產編號」。");
        }
        return finish(rows, title, errors);
    }

    private static List<String> cells(Row row) {
        List<String> out = new ArrayList<>();
        if (row == null || row.getLastCellNum() < 0) {
            return out;
        }
        for (int c = 0; c < row.getLastCellNum(); c++) {
            out.add(TextNormalizer.normalize(text(row.getCell(c))));
        }
        return out;
    }

    /** 公式儲存格使用快取的計算結果，不在伺服器上重新計算。 */
    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    private static Result readCsv(byte[] bytes) {
        String text = decode(bytes);
        List<List<String>> table = new ArrayList<>();
        for (List<String> r : parseCsv(text)) {
            table.add(r.stream().map(TextNormalizer::normalize).toList());
        }
        List<RawRow> rows = new ArrayList<>();
        TableResult t = parseTable("CSV", table, rows);
        List<String> errors = new ArrayList<>(t.errors());
        if (!t.headerFound()) {
            errors.add("找不到表頭：需要「財產編號」欄位。若是 Excel 檔，請直接上傳 .xlsx。");
        }
        return finish(rows, t.title(), errors);
    }

    /** UTF-8（可含 BOM）優先；不是合法 UTF-8 時視為 Excel 在繁體中文 Windows 預設的 MS950（Big5）。 */
    static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            Charset big5 = Charset.isSupported("MS950") ? Charset.forName("MS950") : Charset.forName("Big5");
            return new String(bytes, big5);
        }
    }

    /** RFC 4180：逗號分隔、雙引號包住含逗號／換行的欄位、"" 代表一個雙引號。 */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 共用：把表格（每列為文字清單）轉成資料列
    // ------------------------------------------------------------------

    private record TableResult(boolean headerFound, String title, List<String> errors) {}

    private static TableResult parseTable(String sheet, List<List<String>> table, List<RawRow> out) {
        String title = null;
        String custodian = null;
        Map<Column, Integer> cols = null;
        List<String> errors = new ArrayList<>();
        boolean headerFound = false;

        for (int r = 0; r < table.size(); r++) {
            List<String> row = table.get(r);
            String joined = String.join("", row.stream().filter(v -> v != null).toList());
            if (joined.isEmpty()) {
                continue;
            }
            if (title == null && cols == null && joined.contains("清單")) {
                title = joined;
            }
            Matcher m = CUSTODIAN.matcher(joined);
            if (m.find()) {
                custodian = TextNormalizer.normalize(m.group(1));
                continue;
            }
            if (isHeader(row)) {
                List<String> next = r + 1 < table.size() ? table.get(r + 1) : List.of();
                boolean hasSubHeader = isSubHeader(next);
                Map<Column, Integer> mapped = mapColumns(row, hasSubHeader ? next : List.of());
                List<String> missing = missingColumns(mapped);
                if (!missing.isEmpty()) {
                    errors.add("工作表「" + sheet + "」缺少欄位：" + String.join("、", missing));
                    return new TableResult(true, title, errors);
                }
                cols = mapped;
                headerFound = true;
                if (hasSubHeader) {
                    r++;
                }
                continue;
            }
            if (cols == null) {
                continue;
            }
            String code = at(row, cols.get(Column.CODE));
            if (code == null || code.contains("小計") || code.contains("合計")) {
                continue;
            }
            if (out.size() >= MAX_DATA_ROWS + 1) {
                break;
            }
            Map<Column, String> values = new EnumMap<>(Column.class);
            for (Map.Entry<Column, Integer> e : cols.entrySet()) {
                String v = at(row, e.getValue());
                if (v != null) {
                    values.put(e.getKey(), v);
                }
            }
            out.add(new RawRow(sheet, r + 1, values, custodian));
        }
        return new TableResult(headerFound, title, errors);
    }

    private static String at(List<String> row, Integer index) {
        return index == null || index >= row.size() ? null : row.get(index);
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private static boolean isHeader(List<String> row) {
        return row.stream().anyMatch(v -> {
            String c = compact(v);
            // 只認「財產編號」：同一活頁簿的「新增」（增置單）工作表用的是「財產號碼」且內容為分類代碼，不可匯入
            return c.equals("財產編號");
        });
    }

    private static boolean isSubHeader(List<String> row) {
        return row.stream().anyMatch(v -> {
            String c = compact(v);
            return c.equals("年度") || c.equals("月份") || c.equals("台幣") || c.equals("美金");
        });
    }

    private static Map<Column, Integer> mapColumns(List<String> header, List<String> sub) {
        Map<Column, Integer> cols = new EnumMap<>(Column.class);
        int width = Math.max(header.size(), sub.size());
        for (int c = 0; c < width; c++) {
            String s = compact(at(sub, c));
            String h = compact(at(header, c));
            Column col = s.isEmpty() ? byHeader(h) : bySubHeader(s);
            if (col == null && !s.isEmpty()) {
                col = byHeader(h);
            }
            if (col != null) {
                cols.putIfAbsent(col, c);
            }
        }
        return cols;
    }

    private static Column bySubHeader(String s) {
        if (s.contains("年度")) return Column.YEAR;
        if (s.contains("月份")) return Column.MONTH;
        if (s.contains("台幣") || s.contains("臺幣")) return Column.TWD;
        if (s.contains("美金")) return Column.USD;
        return null;
    }

    private static Column byHeader(String h) {
        if (h.isEmpty()) return null;
        if (h.equals("財產編號")) return Column.CODE;
        if (h.equals("財產名稱") || h.equals("名稱")) return Column.NAME;
        if (h.startsWith("規格")) return Column.SPEC;
        if (h.contains("購置類型") || h.contains("經費來源")) return Column.ACQUISITION;
        if (h.contains("購置日期") || h.contains("購入日期")) return Column.DATE;
        if (h.contains("年度")) return Column.YEAR;
        if (h.contains("月份")) return Column.MONTH;
        if (h.contains("放置地點") || h.contains("存放位置")) return Column.LOCATION;
        if (h.contains("保管人")) return Column.CUSTODIAN;
        if (h.equals("單位")) return Column.UNIT;
        if (h.equals("數量")) return Column.QTY;
        if (h.contains("台幣") || h.contains("臺幣")) return Column.TWD;
        if (h.contains("美金")) return Column.USD;
        if (h.startsWith("備註")) return Column.REMARKS;
        return null;
    }

    private static List<String> missingColumns(Map<Column, Integer> cols) {
        List<String> missing = new ArrayList<>();
        if (!cols.containsKey(Column.CODE)) missing.add("財產編號");
        if (!cols.containsKey(Column.NAME)) missing.add("財產名稱");
        if (!cols.containsKey(Column.LOCATION)) missing.add("放置地點");
        if (!cols.containsKey(Column.DATE) && !(cols.containsKey(Column.YEAR) && cols.containsKey(Column.MONTH))) {
            missing.add("購置年度／月份");
        }
        return missing;
    }

    private static Result finish(List<RawRow> rows, String title, List<String> errors) {
        if (rows.size() > MAX_DATA_ROWS) {
            errors.add("資料超過 " + MAX_DATA_ROWS + " 筆，請分批匯入。");
            return new Result(List.of(), title, errors);
        }
        return new Result(rows, title, errors);
    }
}
