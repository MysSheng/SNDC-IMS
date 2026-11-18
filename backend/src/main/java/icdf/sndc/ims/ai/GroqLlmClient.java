package icdf.sndc.ims.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import icdf.sndc.ims.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq Chat Completions（OpenAI 相容 API），使用 Structured Outputs（strict JSON Schema）
 * 讓模型只能輸出符合 AgentPrompt.SCHEMA 的 JSON。API Key 只存在 Server 端環境變數。
 */
@Component
public class GroqLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GroqLlmClient(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(props.timeout()).build();
        // 啟動時顯示設定狀態，方便排查（不顯示金鑰內容）
        if (props.configured()) {
            log.info("AI 助理：已讀取 GROQ_API_KEY（{} 個字元），模型 {}", props.groqApiKey().strip().length(), props.groqModel());
        } else {
            log.warn("AI 助理：未讀取到 GROQ_API_KEY，AI 查詢將回傳 AI_NOT_CONFIGURED。請確認 backend/.env 並重新啟動後端。");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletion(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}

    @Override
    public String plan(String systemPrompt, List<Turn> turns) {
        if (!props.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "AI service is not configured: set GROQ_API_KEY in backend/.env");
        }
        String body = mapper.writeValueAsString(requestBody(systemPrompt, turns));
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.groqBaseUrl() + "/chat/completions"))
                .timeout(props.timeout())
                .header("Authorization", "Bearer " + props.groqApiKey().strip())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw unavailable("AI service timed out");
        } catch (IOException e) {
            log.warn("Groq request failed: {}", e.getMessage());
            throw unavailable("AI service is unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("AI request was interrupted");
        }
        int status = response.statusCode();
        if (status == 401) {
            // 有讀到金鑰，但 Groq 不接受（無效、複製錯誤或已撤銷）——與「沒有設定」分開回報
            log.warn("Groq rejected the API key (HTTP 401)");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_KEY_REJECTED", "GROQ_API_KEY was rejected by Groq");
        }
        if (status == 403) {
            // 金鑰以外的拒絕：常見於使用 VPN、所在地區或網路受限制
            log.warn("Groq denied access (HTTP 403): {}", truncate(response.body()));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ACCESS_DENIED", "Groq denied access from this network");
        }
        if (status == 429) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_BUSY", "AI service rate limit reached");
        }
        if (status >= 400) {
            // 不把回應內容回傳給 Client，只記錄於 Server log
            log.warn("Groq returned HTTP {}: {}", status, truncate(response.body()));
            throw unavailable("AI service returned an error");
        }
        ChatCompletion completion = mapper.readValue(response.body(), ChatCompletion.class);
        if (completion.choices() == null || completion.choices().isEmpty()
                || completion.choices().get(0).message() == null) {
            throw unavailable("AI service returned an empty response");
        }
        return completion.choices().get(0).message().content();
    }

    private Map<String, Object> requestBody(String systemPrompt, List<Turn> turns) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Turn t : turns) {
            messages.add(Map.of("role", t.role(), "content", t.content()));
        }
        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "asset_agent_plan");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", mapper.readTree(AgentPrompt.SCHEMA));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.groqModel());
        body.put("messages", messages);
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_schema", "json_schema", jsonSchema));
        if (props.reasoningEffort() != null && !props.reasoningEffort().isBlank()) {
            body.put("reasoning_effort", props.reasoningEffort());
        }
        return body;
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) + "…" : s);
    }
}
