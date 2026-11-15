package icdf.sndc.ims.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 助理設定（application.yml 的 app.ai.*）。GROQ_API_KEY 為選填：
 * 未設定時其他功能照常運作，只有 AI 查詢會回傳「AI 服務未設定」錯誤（規格書第 11.1 節）。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String groqApiKey,
        String groqModel,
        String groqBaseUrl,
        String reasoningEffort,
        Duration timeout
) {
    public AiProperties {
        if (groqModel == null || groqModel.isBlank()) {
            groqModel = "openai/gpt-oss-120b";
        }
        if (groqBaseUrl == null || groqBaseUrl.isBlank()) {
            groqBaseUrl = "https://api.groq.com/openai/v1";
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(30);
        }
    }

    public boolean configured() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }
}
