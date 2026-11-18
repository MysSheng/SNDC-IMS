package icdf.sndc.ims.asset;

import icdf.sndc.ims.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 規格書第 13.2、13.3 節（重複統計 API 另行補上）。 */
class AssetApiIntegrationTest extends ApiTestSupport {

    String token;
    String office1;
    String office2;
    String custodian;

    @BeforeEach
    void seed() throws Exception {
        cleanDatabase();
        token = registerAndGetToken("測試員");
        office1 = read(postJson("/api/v1/locations", Map.of("name", "第一辦公室")).andExpect(status().isCreated()), "$.id");
        office2 = read(postJson("/api/v1/locations", Map.of("name", "第二辦公室")).andExpect(status().isCreated()), "$.id");
        custodian = read(postJson("/api/v1/managers", Map.of("displayName", "李大豪")).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void createReadUpdateSoftDelete() throws Exception {
        Map<String, Object> body = asset("A001", "  水壺 ", 3, "藍色", office1);
        body.put("usefulLifeYears", 2.5);

        String id = read(postJson("/api/v1/assets", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemName").value("水壺"))
                .andExpect(jsonPath("$.techMission").value("駐索馬利蘭技術團"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.manager.name").value("李大豪"))
                .andExpect(jsonPath("$.location.name").value("第一辦公室"))
                .andExpect(jsonPath("$.purchaseDate").value("2024-01-15"))
                .andExpect(jsonPath("$.expectedExpiryDate").value("2026-07-15"))
                .andExpect(jsonPath("$.expiryStatus").exists()), "$.id");

        perform(get("/api/v1/assets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetCode").value("A001"));

        body.put("quantity", 4);
        body.put("color", "綠色");
        perform(put("/api/v1/assets/" + id).contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.color").value("綠色"));

        perform(delete("/api/v1/assets/" + id)).andExpect(status().isNoContent());

        perform(get("/api/v1/assets/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        perform(get("/api/v1/assets"))
                .andExpect(jsonPath("$.totalItems").value(0));

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assets WHERE asset_code = 'A001' AND deleted_at IS NOT NULL", Integer.class);
        assertThat(remaining).isEqualTo(1);
    }

    @Test
    void optionalFieldsMayBeOmittedAndQuantityDefaultsToOne() throws Exception {
        Map<String, Object> body = asset("A010", "印表機", null, null, office1);
        body.remove("quantity");
        body.remove("color");
        postJson("/api/v1/assets", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.color").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void requiredFieldsAreEnforced() throws Exception {
        for (String field : new String[]{"assetCode", "itemName", "techMission", "purchaseDate", "locationId", "managerId"}) {
            Map<String, Object> body = asset("A020", "印表機", 1, null, office1);
            body.remove(field);
            postJson("/api/v1/assets", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value(field));
        }
    }

    @Test
    void purchaseYearMustStartWithTwo() throws Exception {
        Map<String, Object> body = asset("A021", "印表機", 1, null, office1);
        body.put("purchaseDate", "1999-12-31");
        postJson("/api/v1/assets", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("purchaseDate"));
    }

    @Test
    void removedFieldsAreNotReturned() throws Exception {
        Map<String, Object> body = asset("A022", "印表機", 1, null, office1);
        String json = postJson("/api/v1/assets", body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("brand").doesNotContain("category")
                .doesNotContain("\"model\"").doesNotContain("deletedAt");
    }

    @Test
    void usefulLifeIsOptionalAndThenThereIsNoExpiry() throws Exception {
        Map<String, Object> body = asset("A023", "冷氣機", 1, null, office1);
        body.remove("usefulLifeYears");
        postJson("/api/v1/assets", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usefulLifeYears").doesNotExist())
                .andExpect(jsonPath("$.expectedExpiryDate").doesNotExist())
                .andExpect(jsonPath("$.expiryStatus").doesNotExist());
        perform(get("/api/v1/assets").param("expiryStatus", "EXPIRED")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void excelFieldsAreStoredAndSearchable() throws Exception {
        Map<String, Object> body = asset("301200304-0001", "冷氣機", 1, null, office1);
        body.put("specification", "SAMSUNG AR18KCF 1.5噸分離式冷氣機");
        body.put("acquisitionType", "當地採購");
        body.put("unit", "套");
        body.put("valueUsd", 650);
        body.put("valueTwd", 19500);
        postJson("/api/v1/assets", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.specification").value("SAMSUNG AR18KCF 1.5噸分離式冷氣機"))
                .andExpect(jsonPath("$.acquisitionType").value("當地採購"))
                .andExpect(jsonPath("$.unit").value("套"))
                .andExpect(jsonPath("$.valueUsd").value(650))
                .andExpect(jsonPath("$.periods.length()").value(0));
        // 關鍵字也會比對規格
        perform(get("/api/v1/assets").param("keyword", "SAMSUNG")).andExpect(jsonPath("$.totalItems").value(1));
        body.put("assetCode", "A024");
        body.put("valueUsd", -1);
        postJson("/api/v1/assets", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("valueUsd"));
    }

    @Test
    void periodsFilterTheListByYearThenQuarter() throws Exception {
        Map<String, Object> q3 = asset("P001", "冷氣機", 1, null, office1);
        q3.put("periodYear", 2026);
        q3.put("periodQuarter", 3);
        String id = read(postJson("/api/v1/assets", q3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periods[0].year").value(2026))
                .andExpect(jsonPath("$.periods[0].quarter").value(3)), "$.id");
        Map<String, Object> q1 = asset("P002", "印表機", 1, null, office1);
        q1.put("periodYear", 2025);
        q1.put("periodQuarter", 1);
        postJson("/api/v1/assets", q1).andExpect(status().isCreated());
        postJson("/api/v1/assets", asset("P003", "水壺", 1, null, office1)).andExpect(status().isCreated());

        // 同一筆加入第二個期別（PUT 帶期別只會新增，不會移除）
        q3.put("periodYear", 2026);
        q3.put("periodQuarter", 4);
        perform(put("/api/v1/assets/" + id).contentType(MediaType.APPLICATION_JSON).content(toJson(q3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periods.length()").value(2));

        perform(get("/api/v1/assets")).andExpect(jsonPath("$.totalItems").value(3));
        // 2026 年有 Q3、Q4 兩季，但同一筆資產只列一次
        perform(get("/api/v1/assets").param("year", "2026")).andExpect(jsonPath("$.totalItems").value(1));
        perform(get("/api/v1/assets").param("year", "2026").param("quarter", "3"))
                .andExpect(jsonPath("$.items[0].assetCode").value("P001"));
        perform(get("/api/v1/assets").param("year", "2026").param("quarter", "1"))
                .andExpect(jsonPath("$.totalItems").value(0));
        perform(get("/api/v1/assets").param("year", "2025")).andExpect(jsonPath("$.totalItems").value(1));
        perform(get("/api/v1/assets").param("quarter", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("year"));

        perform(get("/api/v1/periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].label").value("2026Q4"))
                .andExpect(jsonPath("$[0].assetCount").value(1))
                .andExpect(jsonPath("$[2].label").value("2025Q1"));

        Map<String, Object> half = asset("P004", "水壺", 1, null, office1);
        half.put("periodYear", 2026);
        postJson("/api/v1/assets", half).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateCustodianName() throws Exception {
        postJson("/api/v1/managers", Map.of("displayName", "李大豪"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANAGER_NAME_DUPLICATE"));
    }

    @Test
    void rejectsDuplicateAssetCode() throws Exception {
        postJson("/api/v1/assets", asset("A001", "水壺", 1, null, office1)).andExpect(status().isCreated());
        postJson("/api/v1/assets", asset("A001", "印表機", 1, null, office1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_CODE_DUPLICATE"));
    }

    @Test
    void rejectsZeroOrNegativeQuantity() throws Exception {
        postJson("/api/v1/assets", asset("A002", "水壺", 0, null, office1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));
        postJson("/api/v1/assets", asset("A003", "水壺", -2, null, office1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownLocation() throws Exception {
        postJson("/api/v1/assets", asset("A004", "水壺", 1, null, "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("locationId"));
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        Map<String, Object> body = asset("A005", "水壺", 1, null, office1);
        body.put("purchaseDate", "2024-13-40");
        postJson("/api/v1/assets", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void searchFiltersAndPaging() throws Exception {
        // 規格書第 13.3 節測試資料
        postJson("/api/v1/assets", asset("A001", "水壺", 3, "藍色", office1)).andExpect(status().isCreated());
        postJson("/api/v1/assets", asset("A002", "水壺", 2, "紅色", office2)).andExpect(status().isCreated());
        postJson("/api/v1/assets", asset("A003", "印表機", 1, "黑色", office1)).andExpect(status().isCreated());

        perform(get("/api/v1/assets").param("itemName", "水壺").param("color", "藍色"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].assetCode").value("A001"));

        perform(get("/api/v1/assets").param("keyword", "A00"))
                .andExpect(jsonPath("$.totalItems").value(3));
        perform(get("/api/v1/assets").param("keyword", "印表"))
                .andExpect(jsonPath("$.totalItems").value(1));

        perform(get("/api/v1/assets").param("locationId", office1))
                .andExpect(jsonPath("$.totalItems").value(2));

        perform(get("/api/v1/assets").param("size", "2").param("page", "1").param("sort", "assetCode,asc"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].assetCode").value("A003"));

        perform(get("/api/v1/assets").param("size", "500"))
                .andExpect(status().isBadRequest());
        perform(get("/api/v1/assets").param("sort", "pinHash"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankTechMissionIsRejected() throws Exception {
        Map<String, Object> body = asset("A023", "印表機", 1, null, office1);
        body.put("techMission", "   ");
        postJson("/api/v1/assets", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("techMission"));
    }

    @Test
    void filtersByCustodianAndExpiryStatus() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);   // 測試設定 APP_TIMEZONE=UTC
        String other = read(postJson("/api/v1/managers", Map.of("displayName", "王小明")).andExpect(status().isCreated()), "$.id");

        Map<String, Object> expired = asset("E001", "舊電腦", 1, null, office1);
        expired.put("purchaseDate", today.minusYears(6).toString());          // 5 年 → 一年前已到期
        Map<String, Object> soon = asset("E002", "投影機", 1, null, office1);
        soon.put("purchaseDate", today.minusYears(5).plusDays(30).toString()); // 30 天後到期
        Map<String, Object> valid = asset("E003", "新電腦", 1, null, office2);
        valid.put("purchaseDate", today.minusYears(1).toString());
        valid.put("managerId", other);
        postJson("/api/v1/assets", expired).andExpect(status().isCreated()).andExpect(jsonPath("$.expiryStatus").value("EXPIRED"));
        postJson("/api/v1/assets", soon).andExpect(status().isCreated()).andExpect(jsonPath("$.expiryStatus").value("EXPIRING_SOON"));
        postJson("/api/v1/assets", valid).andExpect(status().isCreated()).andExpect(jsonPath("$.expiryStatus").value("VALID"));

        perform(get("/api/v1/assets").param("expiryStatus", "EXPIRED"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].assetCode").value("E001"));
        perform(get("/api/v1/assets").param("expiryStatus", "EXPIRING_SOON"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].assetCode").value("E002"));
        perform(get("/api/v1/assets").param("expiryStatus", "VALID"))
                .andExpect(jsonPath("$.totalItems").value(1));

        perform(get("/api/v1/assets").param("managerId", other))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].assetCode").value("E003"));
        perform(get("/api/v1/assets").param("locationId", office1).param("managerId", custodian))
                .andExpect(jsonPath("$.totalItems").value(2));

        perform(get("/api/v1/assets").param("purchasedFrom", today.minusYears(2).toString()))
                .andExpect(jsonPath("$.totalItems").value(1));
        perform(get("/api/v1/assets").param("expiryStatus", "BROKEN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingDatesRecomputesExpiry() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<String, Object> body = asset("E010", "冰箱", 1, null, office1);
        body.put("purchaseDate", today.minusYears(1).toString());
        String id = read(postJson("/api/v1/assets", body).andExpect(status().isCreated()), "$.id");
        perform(get("/api/v1/assets").param("expiryStatus", "EXPIRED")).andExpect(jsonPath("$.totalItems").value(0));

        body.put("purchaseDate", today.minusYears(10).toString());
        perform(put("/api/v1/assets/" + id).contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk());
        perform(get("/api/v1/assets").param("expiryStatus", "EXPIRED")).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void rejectsDuplicateLocationName() throws Exception {
        postJson("/api/v1/locations", Map.of("name", "第一辦公室"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_NAME_DUPLICATE"));
    }

    // ------------------------------------------------------------------

    /** 含所有必填欄位的資產；所屬技術團、購置日期 2024-01-15、使用年限 5 年、保管人李大豪。 */
    private Map<String, Object> asset(String code, String name, Integer qty, String color, String locationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetCode", code);
        m.put("itemName", name);
        m.put("techMission", "駐索馬利蘭技術團");
        m.put("purchaseDate", "2024-01-15");
        m.put("usefulLifeYears", 5);
        m.put("managerId", custodian);
        m.put("locationId", locationId);
        m.put("quantity", qty);
        m.put("color", color);
        return m;
    }

    private ResultActions perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(bearer(req, token));
    }

    private ResultActions postJson(String url, Map<?, ?> body) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }
}
