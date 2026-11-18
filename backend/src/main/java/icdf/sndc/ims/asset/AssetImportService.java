package icdf.sndc.ims.asset;

import icdf.sndc.ims.asset.AssetImportDtos.ImportError;
import icdf.sndc.ims.asset.AssetImportDtos.ImportPreview;
import icdf.sndc.ims.asset.AssetImportDtos.ImportResult;
import icdf.sndc.ims.asset.AssetSheetReader.Column;
import icdf.sndc.ims.asset.AssetSheetReader.RawRow;
import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.location.Location;
import icdf.sndc.ims.location.LocationRepository;
import icdf.sndc.ims.manager.Manager;
import icdf.sndc.ims.manager.ManagerRepository;
import icdf.sndc.ims.period.InventoryPeriod;
import icdf.sndc.ims.period.PeriodService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 匯入主管的財產清單（規格書第 4.6 節）。兩個步驟：
 * <ol>
 *   <li>preview：讀檔、逐列驗證、比對既有資料，回傳新增／更新筆數與錯誤；不寫入資料庫。</li>
 *   <li>confirm：在同一個交易中寫入預覽過的資料列；任一筆失敗則整批不寫入。</li>
 * </ol>
 * 以財產編號比對：已存在則更新 Excel 有的欄位（Excel 空白的選填欄位保留原值），已刪除的會恢復；
 * 使用年限、顏色等 Excel 沒有的欄位不會被改動。每筆資產都會加入指定的盤點期別。
 */
@Service
public class AssetImportService {

    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_REPORTED_ERRORS = 200;
    private static final Pattern PERIOD_IN_NAME = Pattern.compile("(20\\d{2})\\s*[Qq]\\s*([1-4])");
    private static final Pattern DATE = Pattern.compile("^(\\d{2,4})[/.\\-年](\\d{1,2})(?:[/.\\-月](\\d{1,2})日?)?(?:T.*)?$");

    /** 已驗證的一列；文字皆已正規化。 */
    record ImportRow(String sheet, int rowNumber, String assetCode, String itemName, String specification,
                     LocalDate purchaseDate, String acquisitionType, String location, String custodian,
                     String unit, int quantity, BigDecimal valueTwd, BigDecimal valueUsd, String remarks) {}

    private final AssetRepository assets;
    private final LocationRepository locations;
    private final ManagerRepository managers;
    private final PeriodService periods;
    private final ImportSessionStore sessions;

    public AssetImportService(AssetRepository assets, LocationRepository locations, ManagerRepository managers,
                              PeriodService periods, ImportSessionStore sessions) {
        this.assets = assets;
        this.locations = locations;
        this.managers = managers;
        this.periods = periods;
        this.sessions = sessions;
    }

    // ==================================================================
    // 預覽
    // ==================================================================

    @Transactional(readOnly = true)
    public ImportPreview preview(UUID userId, String fileName, byte[] bytes, Integer year, Integer quarter,
                                 String techMission) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.invalidField("file", "File is empty");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw ApiException.invalidField("file", "File must be at most 10 MB");
        }
        int[] period = resolvePeriod(fileName, year, quarter);
        int y = period[0], q = period[1];

        AssetSheetReader.Result read = AssetSheetReader.read(bytes);
        List<ImportError> errors = new ArrayList<>();
        read.fileErrors().forEach(m -> errors.add(new ImportError(null, null, null, m)));

        String mission = TextNormalizer.normalize(techMission);
        if (mission == null) {
            mission = AssetSheetReader.missionFromTitle(read.title());
        }
        if (mission == null) {
            errors.add(new ImportError(null, null, null, "無法從標題判斷所屬技術團，請在匯入畫面填寫。"));
        } else if (mission.length() > 100) {
            errors.add(new ImportError(null, null, null, "所屬技術團最多 100 個字。"));
        }

        List<ImportRow> rows = new ArrayList<>();
        Map<String, RawRow> seen = new HashMap<>();
        for (RawRow raw : read.rows()) {
            List<String> problems = new ArrayList<>();
            ImportRow row = validate(raw, problems);
            String code = raw.get(Column.CODE);
            if (code != null && seen.containsKey(code)) {
                RawRow first = seen.get(code);
                problems.add("財產編號重複（與「" + first.sheet() + "」第 " + first.rowNumber() + " 列相同）");
            } else if (code != null) {
                seen.put(code, raw);
            }
            if (problems.isEmpty()) {
                rows.add(row);
            } else {
                problems.forEach(p -> errors.add(new ImportError(raw.sheet(), raw.rowNumber(), code, p)));
            }
        }
        if (read.rows().isEmpty() && errors.isEmpty()) {
            errors.add(new ImportError(null, null, null, "檔案中沒有任何資產資料列。"));
        }

        // 與資料庫比對（含已刪除的資產）
        Map<String, Asset> existing = assets.findByAssetCodeIn(rows.stream().map(ImportRow::assetCode).toList())
                .stream().collect(Collectors.toMap(Asset::getAssetCode, Function.identity()));
        int create = 0, update = 0, restore = 0;
        for (ImportRow r : rows) {
            Asset a = existing.get(r.assetCode());
            if (a == null) create++;
            else if (a.isDeleted()) restore++;
            else update++;
        }
        List<String> newLocations = distinct(rows, ImportRow::location).stream()
                .filter(n -> locations.findByName(n).isEmpty()).toList();
        List<String> newCustodians = distinct(rows, ImportRow::custodian).stream()
                .filter(n -> managers.findByDisplayName(n).isEmpty()).toList();

        UUID token = null;
        java.time.Instant expiresAt = null;
        if (errors.isEmpty()) {
            ImportSessionStore.Session s = sessions.create(userId, y, q, mission, rows);
            token = s.token();
            expiresAt = s.expiresAt();
        }
        return new ImportPreview(token, fileName, y, q, y + "Q" + q, mission,
                read.rows().size(), create, update, restore, newLocations, newCustodians,
                errors.size(), errors.stream().limit(MAX_REPORTED_ERRORS).toList(), expiresAt);
    }

    /** year／quarter 未提供時，嘗試從檔名判斷，例如「ICT計畫_2026Q3.xlsx」。 */
    static int[] resolvePeriod(String fileName, Integer year, Integer quarter) {
        if ((year == null || quarter == null) && fileName != null) {
            Matcher m = PERIOD_IN_NAME.matcher(fileName);
            if (m.find()) {
                if (year == null) year = Integer.parseInt(m.group(1));
                if (quarter == null) quarter = Integer.parseInt(m.group(2));
            }
        }
        if (year == null) {
            throw ApiException.invalidField("year", "Year is required");
        }
        if (quarter == null) {
            throw ApiException.invalidField("quarter", "Quarter is required");
        }
        PeriodService.validate(year, quarter);
        return new int[]{year, quarter};
    }

    private static ImportRow validate(RawRow raw, List<String> problems) {
        String code = text(raw, Column.CODE, "財產編號", 64, true, problems);
        String name = text(raw, Column.NAME, "財產名稱", 200, true, problems);
        String spec = text(raw, Column.SPEC, "規格及摘要", 2000, false, problems);
        String acquisition = text(raw, Column.ACQUISITION, "購置類型", 100, false, problems);
        String location = text(raw, Column.LOCATION, "放置地點", 100, true, problems);
        String unit = text(raw, Column.UNIT, "單位", 20, false, problems);
        String remarks = text(raw, Column.REMARKS, "備註", 2000, false, problems);

        String custodian = raw.get(Column.CUSTODIAN) != null ? raw.get(Column.CUSTODIAN) : raw.custodianFromHeader();
        if (custodian == null) {
            problems.add("缺少保管人（表頭「財產保管人：」或「保管人」欄）");
        } else if (custodian.length() > 100) {
            problems.add("保管人最多 100 個字");
        }

        LocalDate purchase = purchaseDate(raw, problems);

        int quantity = 1;
        String qty = raw.get(Column.QTY);
        if (qty != null) {
            try {
                quantity = new BigDecimal(qty.replace(",", "")).intValueExact();
                if (quantity < 1) {
                    problems.add("數量需為 1 以上的整數");
                }
            } catch (NumberFormatException | ArithmeticException e) {
                problems.add("數量「" + qty + "」不是整數");
            }
        }
        BigDecimal twd = money(raw.get(Column.TWD), "台幣", problems);
        BigDecimal usd = money(raw.get(Column.USD), "美金", problems);
        return new ImportRow(raw.sheet(), raw.rowNumber(), code, name, spec, purchase, acquisition, location,
                custodian, unit, quantity, twd, usd, remarks);
    }

    private static String text(RawRow raw, Column c, String label, int max, boolean required, List<String> problems) {
        String v = raw.get(c);
        if (v == null) {
            if (required) problems.add("缺少" + label);
            return null;
        }
        if (v.length() > max) {
            problems.add(label + "最多 " + max + " 個字");
        }
        return v;
    }

    /** 優先使用「購置日期」欄；否則以「年度＋月份」組成該月 1 日（Excel 只有年月）。民國年（3 位數以下）會轉為西元。 */
    private static LocalDate purchaseDate(RawRow raw, List<String> problems) {
        String date = raw.get(Column.DATE);
        Integer y = null, m = null, d = 1;
        if (date != null) {
            Matcher mt = DATE.matcher(date);
            if (!mt.matches()) {
                problems.add("購置日期「" + date + "」格式不正確，請用 2026/01/25");
                return null;
            }
            y = Integer.parseInt(mt.group(1));
            m = Integer.parseInt(mt.group(2));
            d = mt.group(3) == null ? 1 : Integer.parseInt(mt.group(3));
        } else {
            y = integer(raw.get(Column.YEAR));
            m = integer(raw.get(Column.MONTH));
            if (y == null || m == null) {
                problems.add("缺少購置年度或月份");
                return null;
            }
        }
        if (y < 1000) {
            y += 1911;
        }
        if (y < 2000 || y > 2999) {
            problems.add("購置年度需在 2000–2999 之間");
            return null;
        }
        try {
            return LocalDate.of(y, m, d);
        } catch (DateTimeException e) {
            problems.add("購置年月「" + y + "/" + m + "」不正確");
            return null;
        }
    }

    private static Integer integer(String s) {
        if (s == null) return null;
        try {
            return new BigDecimal(s).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static BigDecimal money(String s, String label, List<String> problems) {
        if (s == null) return null;
        String cleaned = s.replace(",", "").replace("NT$", "").replace("US$", "").replace("$", "").strip();
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try {
            BigDecimal v = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
            if (v.signum() < 0) {
                problems.add(label + "不可為負數");
            } else if (v.precision() - v.scale() > 12) {
                problems.add(label + "金額過大");
            }
            return v;
        } catch (NumberFormatException e) {
            problems.add(label + "「" + s + "」不是數字");
            return null;
        }
    }

    private static List<String> distinct(List<ImportRow> rows, Function<ImportRow, String> f) {
        Set<String> out = new LinkedHashSet<>();
        rows.forEach(r -> out.add(f.apply(r)));
        return List.copyOf(out);
    }

    // ==================================================================
    // 確認寫入
    // ==================================================================

    @Transactional
    public ImportResult confirm(UUID userId, UUID token) {
        ImportSessionStore.Session s = sessions.take(token, userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND",
                "The import preview does not exist, has expired, or belongs to another user"));
        InventoryPeriod period = periods.getOrCreate(s.year(), s.quarter());

        Map<String, Asset> existing = new HashMap<>();
        assets.findByAssetCodeIn(s.rows().stream().map(ImportRow::assetCode).toList())
                .forEach(a -> existing.put(a.getAssetCode(), a));
        Map<String, Location> locationCache = new HashMap<>();
        Map<String, Manager> managerCache = new HashMap<>();
        Set<String> touched = new HashSet<>();

        int created = 0, updated = 0, restored = 0;
        List<Asset> toSave = new ArrayList<>();
        for (ImportRow r : s.rows()) {
            if (!touched.add(r.assetCode())) {
                continue;   // 預覽時已排除重複，這裡只是保險
            }
            Asset a = existing.get(r.assetCode());
            boolean isNew = a == null;
            if (isNew) {
                a = new Asset(r.assetCode(), r.itemName(), r.quantity());
                created++;
            } else if (a.isDeleted()) {
                a.restore();
                restored++;
            } else {
                updated++;
            }
            a.setItemName(r.itemName());
            a.setTechMission(s.techMission());
            a.setPurchaseDate(r.purchaseDate());
            a.setExpectedExpiryDate(ExpiryCalculator.expectedExpiryDate(r.purchaseDate(), a.getUsefulLifeYears()));
            a.setLocation(locationCache.computeIfAbsent(r.location(),
                    n -> locations.findByName(n).orElseGet(() -> locations.save(new Location(n, null)))));
            a.setManager(managerCache.computeIfAbsent(r.custodian(),
                    n -> managers.findByDisplayName(n).orElseGet(() -> managers.save(new Manager(n, null)))));
            a.setQuantity(r.quantity());
            // Excel 空白的選填欄位：新資產就是空白；既有資產保留原值，避免覆蓋手動補上的資料
            if (isNew || r.specification() != null) a.setSpecification(r.specification());
            if (isNew || r.acquisitionType() != null) a.setAcquisitionType(r.acquisitionType());
            if (isNew || r.unit() != null) a.setUnit(r.unit());
            if (isNew || r.valueTwd() != null) a.setValueTwd(r.valueTwd());
            if (isNew || r.valueUsd() != null) a.setValueUsd(r.valueUsd());
            if (isNew || r.remarks() != null) a.setDescription(r.remarks());
            a.addPeriod(period);
            toSave.add(a);
        }
        assets.saveAll(toSave);
        assets.flush();
        return new ImportResult(period.label(), created, updated, restored);
    }

    public void discard(UUID userId, UUID token) {
        sessions.discard(token, userId);
    }
}
