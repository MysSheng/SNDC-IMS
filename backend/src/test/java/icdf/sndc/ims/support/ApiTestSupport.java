package icdf.sndc.ims.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 整合測試共用：H2 資料庫、MockMvc、登入取得 JWT、極簡 JSON 序列化。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    public static final String REGISTRATION_CODE = "TEST-CODE-2026";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    protected void cleanDatabase() {
        jdbc.update("DELETE FROM asset_images");
        jdbc.update("DELETE FROM asset_periods");
        jdbc.update("DELETE FROM inventory_periods");
        jdbc.update("DELETE FROM assets");
        jdbc.update("DELETE FROM managers");
        jdbc.update("DELETE FROM locations");
        jdbc.update("DELETE FROM users");
    }

    protected ResultActions register(String username, String pin, String pinConfirm, String code) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("pin", pin);
        body.put("pinConfirm", pinConfirm);
        body.put("registrationCode", code);
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions login(String username, String pin) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("pin", pin);
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    /** 註冊新帳號並回傳 access token。 */
    protected String registerAndGetToken(String username) throws Exception {
        String json = register(username, "0037", "0037", REGISTRATION_CODE)
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    protected static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    protected static String read(ResultActions result, String path) throws Exception {
        Object v = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
        return v == null ? null : v.toString();
    }

    /** 測試用的極簡 JSON 序列化（只支援字串、數字、布林、null 與清單），避免測試綁定特定 Jackson 版本。 */
    protected static String toJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(e.getKey()).append("\":");
            appendValue(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    private static void appendValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof java.util.Collection<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                appendValue(sb, item);
                first = false;
            }
            sb.append(']');
        } else {
            sb.append('"').append(v.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
    }
}
