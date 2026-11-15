package icdf.sndc.ims.ai;

import icdf.sndc.ims.common.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqLlmClientTest {

    @Test
    void missingApiKeyGivesClearConfigurationError() {
        AiProperties props = new AiProperties("", null, null, null, null);
        GroqLlmClient client = new GroqLlmClient(props, JsonMapper.builder().build());
        assertThatThrownBy(() -> client.plan("sys", List.of(new LlmClient.Turn("user", "hi"))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("AI_NOT_CONFIGURED"));
    }

    @Test
    void schemaIsValidJson() {
        assertThat(JsonMapper.builder().build().readTree(AgentPrompt.SCHEMA).get("required").size()).isEqualTo(4);
    }
}
