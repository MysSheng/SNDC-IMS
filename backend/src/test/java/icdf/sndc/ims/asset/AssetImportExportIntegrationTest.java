package icdf.sndc.ims.asset;

import icdf.sndc.ims.support.ApiTestSupport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 規格書第 4.6 節：匯入主管的財產清單（預覽 → 確認）、盤點期別篩選、勾選匯出。
 * 測試資料在程式中產生（版面與主管的 Excel 相同），不使用真實清單。
 */
class AssetImportExportIntegrationTest extends ApiTestSupport {

    String token;

    @BeforeEach
    void seed() throws Exception {
        cleanDatabase();
        token = registerAndGetToken("測試員");
    }

    @Test
    void previewThenConfirmImportsSupervisorWorkbook() throws Exception {
        byte[] xlsx = supervisorWorkbook("王小明", List.of(
                row("301200304-0001  ", "冷氣機", "SAMSUNG AR18KCF 1.5噸分離式冷氣機", "2021", 5, "ICT中心", "套", "650.00", "財國合發字第1號"),
                row("301200304-0002", "冷氣機", "SAMSUNG AR18KCF 1.5噸分離式冷氣機", "2021", 5, "ICT中心", "套", "650.00", null),
                row("60107-0035", "書櫃", "木製側櫃", "2026", 1, "團部", "個", "20.00", null)));

        ResultActions preview = upload("ICT計畫_2026Q3.xlsx", xlsx, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026Q3"))
                .andExpect(jsonPath("$.techMission").value("駐索馬利蘭技術團"))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.createCount").value(3))
                .andExpect(jsonPath("$.updateCount").value(0))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.newLocations.length()").value(2))
                .andExpect(jsonPath("$.newCustodians[0]").value("王小明"))
                .andExpect(jsonPath("$.token").exists());
        // 「新增」工作表（增置單）不可被當成資產匯入
        String previewToken = read(preview, "$.token");
        assertThat(count("SELECT COUNT(*) FROM assets")).isZero();

        perform(post("/api/v1/assets/import/" + previewToken + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.period").value("2026Q3"));
        // 同一份預覽不能確認第二次
        perform(post("/api/v1/assets/import/" + previewToken + "/confirm"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));

        perform(get("/api/v1/assets").param("keyword", "301200304-0001"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].assetCode").value("301200304-0001"))
                .andExpect(jsonPath("$.items[0].purchaseDate").value("2021-05-01"))
                .andExpect(jsonPath("$.items[0].manager.name").value("王小明"))
                .andExpect(jsonPath("$.items[0].location.name").value("ICT中心"))
                .andExpect(jsonPath("$.items[0].unit").value("套"))
                .andExpect(jsonPath("$.items[0].valueUsd").value(650.0))
                .andExpect(jsonPath("$.items[0].acquisitionType").value("當地採購"))
                .andExpect(jsonPath("$.items[0].description").value("財國合發字第1號"))
                .andExpect(jsonPath("$.items[0].expectedExpiryDate").doesNotExist())
                .andExpect(jsonPath("$.items[0].periods[0].quarter").value(3));
        perform(get("/api/v1/assets").param("year", "2026").param("quarter", "3"))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void reimportNextQuarterUpdatesAndKeepsBothPeriods() throws Exception {
        importAndConfirm("清單_2026Q3.xlsx", supervisorWorkbook("王小明", List.of(
                row("60107-0035", "書櫃", "木製側櫃", "2026", 1, "團部", "個", "20.00", null),
                row("60107-0036", "書櫃", "木製立式置物櫃", "2026", 1, "團部", "個", "260.00", null))));
        String id = read(perform(get("/api/v1/assets").param("keyword", "60107-0035")), "$.items[0].id");
        // 手動補上使用年限與顏色：重新匯入時不應被清掉
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("assetCode", "60107-0035");
        edit.put("itemName", "書櫃");
        edit.put("techMission", "駐索馬利蘭技術團");
        edit.put("managerId", read(perform(get("/api/v1/assets/" + id)), "$.manager.id"));
        edit.put("purchaseDate", "2026-01-01");
        edit.put("usefulLifeYears", 8);
        edit.put("locationId", read(perform(get("/api/v1/assets/" + id)), "$.location.id"));
        edit.put("color", "咖啡色");
        edit.put("specification", "木製側櫃");
        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/assets/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(edit))).andExpect(status().isOk());

        // Q4：0035 移到 ICT中心，0036 不在清單上，新增 0037
        String t = read(upload("清單_2026Q4.xlsx", supervisorWorkbook("王小明", List.of(
                row("60107-0035", "書櫃", null, "2026", 1, "ICT中心", "個", "20.00", null),
                row("60107-0037", "白板", "白板 2m*1m", "2026", 9, "ICT中心", "具", "35.00", null))), null, null)
                .andExpect(jsonPath("$.createCount").value(1))
                .andExpect(jsonPath("$.updateCount").value(1)), "$.token");
        perform(post("/api/v1/assets/import/" + t + "/confirm")).andExpect(status().isOk());

        perform(get("/api/v1/assets/" + id))
                .andExpect(jsonPath("$.location.name").value("ICT中心"))
                .andExpect(jsonPath("$.usefulLifeYears").value(8))
                .andExpect(jsonPath("$.color").value("咖啡色"))
                .andExpect(jsonPath("$.specification").value("木製側櫃"))
                .andExpect(jsonPath("$.expectedExpiryDate").value("2034-01-01"))
                .andExpect(jsonPath("$.periods.length()").value(2));
        perform(get("/api/v1/assets").param("year", "2026").param("quarter", "4")).andExpect(jsonPath("$.totalItems").value(2));
        perform(get("/api/v1/assets").param("year", "2026").param("quarter", "3")).andExpect(jsonPath("$.totalItems").value(2));
        perform(get("/api/v1/assets").param("year", "2026")).andExpect(jsonPath("$.totalItems").value(3));
        assertThat(count("SELECT COUNT(*) FROM locations WHERE name = 'ICT中心'")).isEqualTo(1);
    }

    @Test
    void invalidRowsBlockTheWholeImport() throws Exception {
        upload("清單_2026Q3.xlsx", supervisorWorkbook("王小明", List.of(
                row("A-1", "冷氣機", null, "2021", 5, "ICT中心", "套", "650.00", null),
                row("A-1", "冷氣機", null, "2021", 5, "ICT中心", "套", "650.00", null),
                row("A-2", "冷氣機", null, "1999", 5, "ICT中心", "套", "650.00", null),
                row("A-3", "冷氣機", null, "2021", 13, "ICT中心", "套", "abc", null),
                row("A-4", null, null, "2021", 5, null, "套", "1", null))), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.errorCount").value(6))
                .andExpect(jsonPath("$.errors[0].row").value(7))
                .andExpect(jsonPath("$.errors[0].assetCode").value("A-1"));
        assertThat(count("SELECT COUNT(*) FROM assets")).isZero();
    }

    @Test
    void periodIsRequiredWhenFileNameHasNone() throws Exception {
        byte[] xlsx = supervisorWorkbook("王小明", List.of(row("A-1", "冷氣機", null, "2021", 5, "ICT中心", "套", "1", null)));
        upload("清單.xlsx", xlsx, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("year"));
        upload("清單.xlsx", xlsx, 2026, 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("quarter"));
        upload("清單.xlsx", xlsx, 2025, 4).andExpect(jsonPath("$.period").value("2025Q4"));
    }

    @Test
    void csvInUtf8AndBig5IsAccepted() throws Exception {
        String csv = "財產編號,財產名稱,規格及摘要,購置日期,購置類型,放置地點,保管人,單位,數量,美金,備註\r\n"
                + "C-001,碎紙機,\"碎紙機, 大型\",2020/11/03,當地採購,ICT中心,陳大文,具,1,\"1,145.00\",\r\n";
        byte[] utf8 = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        String t = read(upload("assets.csv", utf8, 2026, 3, "駐索馬利蘭技術團")
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.newCustodians[0]").value("陳大文")), "$.token");
        perform(post("/api/v1/assets/import/" + t + "/confirm")).andExpect(status().isOk());
        perform(get("/api/v1/assets").param("keyword", "C-001"))
                .andExpect(jsonPath("$.items[0].specification").value("碎紙機, 大型"))
                .andExpect(jsonPath("$.items[0].purchaseDate").value("2020-11-03"))
                .andExpect(jsonPath("$.items[0].valueUsd").value(1145.0));

        byte[] big5 = csv.replace("C-001", "C-002").getBytes(Charset.forName("MS950"));
        upload("assets.csv", big5, 2026, 3, "駐索馬利蘭技術團")
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.createCount").value(1));
    }

    @Test
    void anotherUserCannotConfirmMyPreview() throws Exception {
        String t = read(upload("清單_2026Q3.xlsx", supervisorWorkbook("王小明",
                List.of(row("A-1", "冷氣機", null, "2021", 5, "ICT中心", "套", "1", null))), null, null), "$.token");
        String other = registerAndGetToken("另一位");
        mvc.perform(bearer(post("/api/v1/assets/import/" + t + "/confirm"), other)).andExpect(status().isNotFound());
        perform(delete("/api/v1/assets/import/" + t)).andExpect(status().isNoContent());
        perform(post("/api/v1/assets/import/" + t + "/confirm")).andExpect(status().isNotFound());
    }

    @Test
    void exportSelectedAssetsAsSupervisorLayout() throws Exception {
        importAndConfirm("清單_2026Q3.xlsx", supervisorWorkbook("王小明", List.of(
                row("60107-0035", "書櫃", "木製側櫃", "2026", 1, "團部", "個", "20.00", null),
                row("60107-0036", "書櫃", "=HYPERLINK(\"x\")", "2026", 1, "團部", "個", "260.00", null),
                row("60107-0037", "白板", null, "2026", 9, "ICT中心", "具", "35.00", null))));
        String a = read(perform(get("/api/v1/assets").param("keyword", "60107-0035")), "$.items[0].id");
        String b = read(perform(get("/api/v1/assets").param("keyword", "60107-0036")), "$.items[0].id");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(a, b));
        body.put("year", 2026);
        body.put("quarter", 3);
        byte[] file = perform(post("/api/v1/assets/export").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("2026Q3")))
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            Sheet s = wb.getSheetAt(0);
            // 工作表名稱為盤點期別
            assertThat(s.getSheetName()).isEqualTo("2026Q3");
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("駐索馬利蘭技術團2026年財產管理個人清單");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("財產保管人：王小明");
            assertThat(s.getRow(5).getCell(0).getStringCellValue()).isEqualTo("60107-0035");
            assertThat(s.getRow(6).getCell(2).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(s.getRow(7).getCell(7).getStringCellValue()).isEqualTo("小計:");
            assertThat(s.getRow(7).getCell(10).getNumericCellValue()).isEqualTo(280.0);
        }
        // 匯出的檔案可以再匯入
        upload("匯出_2026Q4.xlsx", file, null, null)
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.updateCount").value(2));

        body.put("ids", List.of());
        perform(post("/api/v1/assets/export").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void writeOffExportFollowsTemplateAndCanSaveRemarks() throws Exception {
        importAndConfirm("清單_2026Q1.xlsx", supervisorWorkbook("王小明", List.of(
                row("60102-0005", "印表機", "EPSON L382噴墨印表機", "2018", 3, "團部", "臺", "150.00", "財國合發字第1號"),
                row("60114-0150", "電腦椅", null, "2017", 6, "ICT中心", "張", "40.00", null))));
        String a = read(perform(get("/api/v1/assets").param("keyword", "60102-0005")), "$.items[0].id");
        String b = read(perform(get("/api/v1/assets").param("keyword", "60114-0150")), "$.items[0].id");
        String reason = "報廢原因:超過使用年限且損壞。";

        // 不儲存備註：只影響匯出內容
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of());
        body.put("title", "測試計畫");
        body.put("year", 2026);
        body.put("quarter", 1);
        body.put("saveRemarks", false);
        String json = toJson(body).replace("\"items\":[]",
                "\"items\":[{\"id\":\"" + b + "\",\"remarks\":\"" + reason + "\"},{\"id\":\"" + a + "\",\"remarks\":null}]");
        byte[] file = perform(post("/api/v1/assets/export/write-off").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("%E5%A0%B1%E9%8A%B7%E6%A0%B8%E7%AE%97.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("測試計畫 2026年Q1");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("報廢物品: ");
            assertThat(s.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Asset ID");
            // 依送出的順序；備註 null 時沿用資產目前的備註
            assertThat(s.getRow(3).getCell(0).getNumericCellValue()).isEqualTo(1.0);
            assertThat(s.getRow(3).getCell(1).getStringCellValue()).isEqualTo("60114-0150");
            assertThat(s.getRow(3).getCell(5).getStringCellValue()).isEqualTo(reason);
            assertThat(s.getRow(4).getCell(2).getStringCellValue()).isEqualTo("印表機");
            assertThat(s.getRow(4).getCell(3).getStringCellValue()).isEqualTo("EPSON L382噴墨印表機");
            assertThat(s.getRow(4).getCell(4).getStringCellValue()).isEqualTo("團部");
            assertThat(s.getRow(4).getCell(5).getStringCellValue()).isEqualTo("財國合發字第1號");
        }
        perform(get("/api/v1/assets/" + b)).andExpect(jsonPath("$.description").doesNotExist());

        // 沒送 saveRemarks（舊版 App 在 false 時會省略此欄位）也要能匯出，且不寫回
        perform(post("/api/v1/assets/export/write-off").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace(",\"saveRemarks\":false", "")))
                .andExpect(status().isOk());
        perform(get("/api/v1/assets/" + b)).andExpect(jsonPath("$.description").doesNotExist());

        // 儲存備註
        perform(post("/api/v1/assets/export/write-off").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"saveRemarks\":false", "\"saveRemarks\":true")))
                .andExpect(status().isOk());
        perform(get("/api/v1/assets/" + b)).andExpect(jsonPath("$.description").value(reason));
        perform(get("/api/v1/assets/" + a)).andExpect(jsonPath("$.description").value("財國合發字第1號"));

        // 原本有備註：新備註在前，原本的移到括號內；重複匯出不會再加一次
        String withA = json.replace("\"remarks\":null", "\"remarks\":\"" + reason + "\"")
                .replace("\"saveRemarks\":false", "\"saveRemarks\":true");
        byte[] second = perform(post("/api/v1/assets/export/write-off").contentType(MediaType.APPLICATION_JSON).content(withA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        perform(get("/api/v1/assets/" + a)).andExpect(jsonPath("$.description").value(reason + "(財國合發字第1號)"));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(second))) {
            // 報銷核算檔案中只放這次填的備註
            assertThat(wb.getSheetAt(0).getRow(4).getCell(5).getStringCellValue()).isEqualTo(reason);
        }
        perform(post("/api/v1/assets/export/write-off").contentType(MediaType.APPLICATION_JSON).content(withA))
                .andExpect(status().isOk());
        perform(get("/api/v1/assets/" + a)).andExpect(jsonPath("$.description").value(reason + "(財國合發字第1號)"));

        body.put("quarter", 5);
        perform(post("/api/v1/assets/export/write-off").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"quarter\":1", "\"quarter\":5")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPutsEachCustodianInABlockOnOnePeriodSheet() throws Exception {
        importAndConfirm("清單A_2026Q3.xlsx", supervisorWorkbook("王小明",
                List.of(row("A-1", "書櫃", null, "2026", 1, "團部", "個", "20.00", null))));
        importAndConfirm("清單B_2026Q3.xlsx", supervisorWorkbook("陳大文",
                List.of(row("B-1", "白板", null, "2026", 9, "ICT中心", "具", "35.00", null))));
        String a = read(perform(get("/api/v1/assets").param("keyword", "A-1")), "$.items[0].id");
        String b = read(perform(get("/api/v1/assets").param("keyword", "B-1")), "$.items[0].id");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(a, b));
        body.put("year", 2026);
        byte[] file = perform(post("/api/v1/assets/export").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getSheetName()).isEqualTo("2026");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("財產保管人：王小明");
            // 第一個區塊：0–6 列，空兩列後第二個區塊從第 8 列開始
            assertThat(s.getRow(8 + 2).getCell(0).getStringCellValue()).isEqualTo("財產保管人：陳大文");
            assertThat(s.getRow(8 + 5).getCell(0).getStringCellValue()).isEqualTo("B-1");
        }
        // 多位保管人的檔案也能再匯入
        upload("匯出_2026Q4.xlsx", file, null, null)
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.updateCount").value(2));
    }

    // ------------------------------------------------------------------

    private record SheetRow(String code, String name, String spec, String year, int month, String location,
                            String unit, String usd, String remarks) {}

    private static SheetRow row(String code, String name, String spec, String year, int month, String location,
                                String unit, String usd, String remarks) {
        return new SheetRow(code, name, spec, year, month, location, unit, usd, remarks);
    }

    /** 與主管清單相同的版面：標題、製表日期、保管人、兩層表頭、資料、小計，另附一個不可匯入的「新增」工作表。 */
    private static byte[] supervisorWorkbook(String custodian, List<SheetRow> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("ICT計畫");
            s.createRow(0).createCell(0).setCellValue("駐索馬利蘭技術團2026年財產管理個人清單");
            s.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("製表日期：2026年10月08日");
            r1.createCell(10).setCellValue("頁次:2/9");
            s.createRow(2).createCell(0).setCellValue("財產保管人：" + custodian);
            String[] top = {"財產編號", "財產名稱", "規格及摘要", "購置", null, "購置類型\n經費來源", "放置地點", "單位", "數量", "價值計算", null, "備註"};
            String[] sub = {null, null, null, "年度", "月份", null, null, null, null, "台幣", "美金", null};
            Row h1 = s.createRow(3), h2 = s.createRow(4);
            for (int c = 0; c < 12; c++) {
                if (top[c] != null) h1.createCell(c).setCellValue(top[c]);
                if (sub[c] != null) h2.createCell(c).setCellValue(sub[c]);
            }
            int r = 5;
            for (SheetRow x : rows) {
                Row row = s.createRow(r++);
                List<Object> v = new ArrayList<>(List.of());
                v.add(x.code()); v.add(x.name()); v.add(x.spec()); v.add(x.year()); v.add(x.month());
                v.add("當地採購"); v.add(x.location()); v.add(x.unit()); v.add("1"); v.add(null); v.add(x.usd()); v.add(x.remarks());
                for (int c = 0; c < v.size(); c++) {
                    Object o = v.get(c);
                    if (o instanceof String str) row.createCell(c).setCellValue(str);
                    else if (o instanceof Integer i) row.createCell(c).setCellValue(i);
                }
            }
            Row total = s.createRow(r);
            total.createCell(7).setCellValue("小計:");
            total.createCell(8).setCellValue(rows.size());

            Sheet add = wb.createSheet("新增");
            Row ah = add.createRow(2);
            String[] addHeader = {"財產號碼", "財產名稱", "計畫別", "單價", "匯率", "保管人", "入帳日期", "備註說明", "規格說明", "購入日期", "放置地點"};
            for (int c = 0; c < addHeader.length; c++) ah.createCell(c).setCellValue(addHeader[c]);
            Row ar = add.createRow(3);
            ar.createCell(0).setCellValue(60114);
            ar.createCell(1).setCellValue("辦公桌");
            ar.createCell(10).setCellValue("團部");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ResultActions upload(String name, byte[] bytes, Integer year, Integer quarter) throws Exception {
        return upload(name, bytes, year, quarter, null);
    }

    private ResultActions upload(String name, byte[] bytes, Integer year, Integer quarter, String mission) throws Exception {
        var req = multipart("/api/v1/assets/import/preview")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes));
        if (year != null) req.param("year", year.toString());
        if (quarter != null) req.param("quarter", quarter.toString());
        if (mission != null) req.param("techMission", mission);
        // Spring 7 起 multipart 的 builder 不再繼承 MockHttpServletRequestBuilder，直接加上 Token
        req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private void importAndConfirm(String name, byte[] bytes) throws Exception {
        String t = read(upload(name, bytes, null, null).andExpect(jsonPath("$.errorCount").value(0)), "$.token");
        perform(post("/api/v1/assets/import/" + t + "/confirm")).andExpect(status().isOk());
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private ResultActions perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(bearer(req, token));
    }
}
