package icdf.sndc.ims.auth;

import icdf.sndc.ims.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 規格書第 13.1 節。 */
class AuthApiIntegrationTest extends ApiTestSupport {

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void registerWithCorrectCodeSucceeds() throws Exception {
        register("小明", "1234", "1234", REGISTRATION_CODE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("小明"));
    }

    @Test
    void registerWithWrongCodeFails() throws Exception {
        register("小明", "1234", "1234", "WRONG")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_CODE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void registerWithMismatchedPinFails() throws Exception {
        register("小明", "1234", "1235", REGISTRATION_CODE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("pinConfirm"));
    }

    @Test
    void pinMustBeFourDigits() throws Exception {
        register("小明", "123", "123", REGISTRATION_CODE).andExpect(status().isBadRequest());
        register("小明", "12a4", "12a4", REGISTRATION_CODE).andExpect(status().isBadRequest());
    }

    @Test
    void duplicateUsernameRejected() throws Exception {
        register("小明", "1234", "1234", REGISTRATION_CODE).andExpect(status().isCreated());
        register(" 小明 ", "5678", "5678", REGISTRATION_CODE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void leadingZeroPinIsPreservedAndPinIsHashed() throws Exception {
        register("阿華", "0037", "0037", REGISTRATION_CODE).andExpect(status().isCreated());
        login("阿華", "0037").andExpect(status().isOk());
        login("阿華", "37").andExpect(status().isBadRequest());

        String hash = jdbc.queryForObject("SELECT pin_hash FROM users WHERE username = '阿華'", String.class);
        assertThat(hash).isNotEqualTo("0037").startsWith("$2");
    }

    @Test
    void wrongPinFails() throws Exception {
        register("阿華", "0037", "0037", REGISTRATION_CODE);
        login("阿華", "0038")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("不存在的人", "0037")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void locksAfterRepeatedFailures() throws Exception {
        register("鎖定測試", "1111", "1111", REGISTRATION_CODE);
        for (int i = 0; i < 5; i++) {
            login("鎖定測試", "2222").andExpect(status().isUnauthorized());
        }
        // 已鎖定：即使 PIN 正確也暫時拒絕
        login("鎖定測試", "1111")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void protectedApiRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(bearer(get("/api/v1/assets"), "not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenGrantsAccessAndMeHidesSecrets() throws Exception {
        String token = registerAndGetToken("小美");
        mvc.perform(bearer(get("/api/v1/assets"), token)).andExpect(status().isOk());

        String body = mvc.perform(bearer(get("/api/v1/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("小美"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("pin").doesNotContain("Hash");
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
}
