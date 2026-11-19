package icdf.sndc.ims.ai;

import icdf.sndc.ims.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 規格書第 13.4 節：以假的 LLM 回應測試 Backend 的驗證、查詢與確認流程（不呼叫 Groq）。 */
@Import(AgentApiIntegrationTest.ScriptedLlmConfig.class)
class AgentApiIntegrationTest extends ApiTestSupport {

    /** 每個測試設定下一次「模型輸出」。 */
    static volatile String nextOutput;
    /** 最近一次送給模型的系統提示（檢查資產名稱清單）。 */
    static volatile String lastSystem;

    @TestConfiguration
    static class ScriptedLlmConfig {
        @Bean
        @Primary
        LlmClient scriptedLlm() {
            return (system, turns) -> {
                lastSystem = system;
                return nextOutput;
            };
        }
    }

    String token;
    String office1;
    String liu;
    String lee;

    @BeforeEach
    void seed() throws Exception {
        cleanDatabase();
        token = registerAndGetToken("助理測試");
        office1 = read(postAuthed("/api/v1/locations", Map.of("name", "第一辦公室")), "$.id");
        liu = read(postAuthed("/api/v1/managers", Map.of("displayName", "劉正正")), "$.id");
        lee = read(postAuthed("/api/v1/managers", Map.of("displayName", "李大維")), "$.id");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        createAsset("60109-0001", "電腦", liu, today.minusYears(6));            // 劉正正，已過期
        createAsset("60109-0002", "螢幕", liu, today.minusYears(7));            // 劉正正，已過期
        createAsset("60109-0003", "水管", liu, today.minusYears(1));            // 劉正正，未過期
        createAsset("60109-0004", "冷氣", lee, today.minusYears(5).plusDays(20)); // 李大維，快到期
        createAsset("60109-0005", "冷氣", lee, today.minusYears(1));            // 李大維，未過期
    }

    @Test
    void categoryWordsMapToActualItemNames() throws Exception {
        // 模型拿到資料庫中的實際名稱清單，把「computer／電腦類」對應到多個名稱
        nextOutput = plan("COUNT", filters().replace("\"itemNames\":null", "\"itemNames\":[\"電腦\",\"螢幕\",\"不存在的名稱\"]"), null);
        ask("How many computers do we have?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("COUNT"))
                .andExpect(jsonPath("$.summary.recordCount").value(2))
                .andExpect(jsonPath("$.filters.itemNames.length()").value(3));
        // 只檢查「資產名稱」這一段：系統提示的範例句本身就有人名（例如劉正正），不能用整份提示判斷
        String start = "## 目前資料庫中的資產名稱";
        String names = lastSystem.substring(lastSystem.indexOf(start) + start.length());
        names = names.substring(0, names.indexOf("##"));
        assertThat(names).contains("電腦").contains("冷氣").contains("水管").contains("螢幕")
                .doesNotContain("劉正正").doesNotContain("李大維").doesNotContain("第一辦公室");

        // 「在清單中顯示」使用同樣的條件
        mvc.perform(bearer(get("/api/v1/assets").param("itemNames", "電腦", "冷氣"), token))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void searchResolvesNamesWithTitlesAndExpiry() throws Exception {
        nextOutput = plan("SEARCH", filters("custodianName", "李大維技師", "expiryStatus", "EXPIRING_SOON"), null);
        ask("我想找由李大維技師管理且快要過期的資產")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("SEARCH"))
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.results[0].assetCode").value("60109-0004"))
                .andExpect(jsonPath("$.filters.custodian.id").value(lee))
                .andExpect(jsonPath("$.filters.expiryStatus").value("EXPIRING_SOON"))
                .andExpect(jsonPath("$.pendingAction").doesNotExist());
    }

    @Test
    void bulkDeleteRequiresConfirmationAndDeletesOnlyListedTargets() throws Exception {
        nextOutput = plan("DELETE", filters("custodianName", "劉正正", "expiryStatus", "EXPIRED"), null);
        ResultActions r = ask("我們今天汰換掉由劉正正管理的過期資產了")
                .andExpect(jsonPath("$.intent").value("DELETE"))
                .andExpect(jsonPath("$.pendingAction.type").value("DELETE"))
                .andExpect(jsonPath("$.pendingAction.targets.length()").value(2));
        String actionId = read(r, "$.pendingAction.id");

        // 確認前不會刪除任何資料
        assertThat(activeCount()).isEqualTo(5);

        mvc.perform(bearer(post("/api/v1/ai/actions/" + actionId + "/confirm"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("已刪除 2 筆資產。"));
        assertThat(activeCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE asset_code = '60109-0003' AND deleted_at IS NULL",
                Integer.class)).isEqualTo(1);

        // 同一個動作不能執行第二次
        mvc.perform(bearer(post("/api/v1/ai/actions/" + actionId + "/confirm"), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotConfirmMyAction() throws Exception {
        nextOutput = plan("DELETE", filters("custodianName", "劉正正"), null);
        String actionId = read(ask("刪掉劉正正的資產"), "$.pendingAction.id");
        String otherToken = registerAndGetToken("其他人");
        mvc.perform(bearer(post("/api/v1/ai/actions/" + actionId + "/confirm"), otherToken))
                .andExpect(status().isNotFound());
        assertThat(activeCount()).isEqualTo(5);
    }

    @Test
    void cancelDiscardsAction() throws Exception {
        nextOutput = plan("DELETE", filters("custodianName", "劉正正"), null);
        String actionId = read(ask("刪掉劉正正的資產"), "$.pendingAction.id");
        mvc.perform(bearer(delete("/api/v1/ai/actions/" + actionId), token)).andExpect(status().isNoContent());
        mvc.perform(bearer(post("/api/v1/ai/actions/" + actionId + "/confirm"), token)).andExpect(status().isNotFound());
        assertThat(activeCount()).isEqualTo(5);
    }

    @Test
    void updateByAssetCodeRequiresConfirmation() throws Exception {
        nextOutput = plan("UPDATE", filters("assetCode", "60109-0003"), changes("itemName", "水龍頭"));
        ResultActions r = ask("編號60109-0003的那個資產是水龍頭請幫我修正")
                .andExpect(jsonPath("$.pendingAction.type").value("UPDATE"))
                .andExpect(jsonPath("$.pendingAction.changes['名稱']").value("水龍頭"))
                .andExpect(jsonPath("$.pendingAction.targets.length()").value(1));
        assertThat(itemName("60109-0003")).isEqualTo("水管");

        mvc.perform(bearer(post("/api/v1/ai/actions/" + read(r, "$.pendingAction.id") + "/confirm"), token))
                .andExpect(status().isOk());
        assertThat(itemName("60109-0003")).isEqualTo("水龍頭");
    }

    @Test
    void deleteWithoutConditionsIsRefused() throws Exception {
        nextOutput = plan("DELETE", filters(), null);
        ask("全部刪掉")
                .andExpect(jsonPath("$.intent").value("CLARIFY"))
                .andExpect(jsonPath("$.pendingAction").doesNotExist());
        assertThat(activeCount()).isEqualTo(5);
    }

    @Test
    void unknownCustodianAsksForClarificationWithCandidates() throws Exception {
        nextOutput = plan("SEARCH", filters("custodianName", "陳小華"), null);
        String reply = read(ask("找陳小華的東西").andExpect(jsonPath("$.intent").value("CLARIFY")), "$.reply");
        assertThat(reply).contains("陳小華").contains("劉正正").contains("李大維");
    }

    @Test
    void invalidModelOutputNeverExecutes() throws Exception {
        nextOutput = "not json at all";
        ask("隨便").andExpect(status().isOk()).andExpect(jsonPath("$.intent").value("CLARIFY"));

        nextOutput = plan("SEARCH", filters("expiryStatus", "DROP TABLE assets"), null);
        ask("奇怪的狀態").andExpect(jsonPath("$.intent").value("CLARIFY"));

        nextOutput = plan("EXECUTE_SQL", filters(), null);
        ask("執行 SQL").andExpect(jsonPath("$.intent").value("UNSUPPORTED"));
        assertThat(activeCount()).isEqualTo(5);
    }

    @Test
    void countAndDuplicatesComeFromDatabase() throws Exception {
        nextOutput = plan("COUNT", filters("itemName", "冷氣"), null);
        ask("我們有多少台冷氣？")
                .andExpect(jsonPath("$.summary.totalQuantity").value(2))
                .andExpect(jsonPath("$.summary.recordCount").value(2));

        nextOutput = plan("DUPLICATES", filters(), null);
        ask("有哪些物品有重複？")
                .andExpect(jsonPath("$.duplicates.length()").value(1))
                .andExpect(jsonPath("$.duplicates[0].itemName").value("冷氣"));

        mvc.perform(bearer(get("/api/v1/assets/duplicates"), token))
                .andExpect(jsonPath("$[0].itemName").value("冷氣"))
                .andExpect(jsonPath("$[0].totalQuantity").value(2));
    }

    @Test
    void createRequestsAreUnsupported() throws Exception {
        nextOutput = plan("UNSUPPORTED", filters(), null);
        String reply = read(ask("幫我新增一台印表機").andExpect(jsonPath("$.intent").value("UNSUPPORTED")), "$.reply");
        assertThat(reply).contains("手動");
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(post("/api/v1/ai/asset-query").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------

    private ResultActions ask(String message) throws Exception {
        return mvc.perform(bearer(post("/api/v1/ai/asset-query"), token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("message", message))));
    }

    private ResultActions postAuthed(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(bearer(post(url), token).contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated());
    }

    private void createAsset(String code, String name, String custodian, LocalDate purchase) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetCode", code);
        m.put("itemName", name);
        m.put("techMission", "駐索馬利蘭技術團");
        m.put("purchaseDate", purchase.toString());
        m.put("usefulLifeYears", 5);
        m.put("managerId", custodian);
        m.put("locationId", office1);
        m.put("quantity", 1);
        postAuthed("/api/v1/assets", m);
    }

    private int activeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE deleted_at IS NULL", Integer.class);
    }

    private String itemName(String code) {
        return jdbc.queryForObject("SELECT item_name FROM assets WHERE asset_code = ?", String.class, code);
    }

    private static final List<String> FILTER_KEYS = List.of("keyword", "assetCode", "itemName", "techMission", "color",
            "locationName", "custodianName", "expiryStatus", "purchasedFrom", "purchasedTo", "expiresFrom", "expiresTo",
            "minQuantity", "year", "quarter", "itemNames");
    private static final List<String> CHANGE_KEYS = List.of("assetCode", "itemName", "techMission", "color",
            "description", "locationName", "custodianName", "purchaseDate", "usefulLifeYears", "quantity",
            "specification", "unit");

    private static String filters(String... kv) {
        return obj(FILTER_KEYS, kv);
    }

    private static String changes(String... kv) {
        return obj(CHANGE_KEYS, kv);
    }

    /** 產生與 strict schema 相同形狀的物件：未指定的欄位為 null。 */
    private static String obj(List<String> keys, String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        keys.forEach(k -> m.put(k, null));
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return toJson(m);
    }

    private static String plan(String intent, String filters, String changes) {
        return "{\"intent\":\"" + intent + "\",\"filters\":" + filters + ",\"changes\":"
                + (changes == null ? obj(CHANGE_KEYS) : changes) + ",\"clarificationQuestion\":null}";
    }
}
