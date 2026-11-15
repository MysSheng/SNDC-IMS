package icdf.sndc.ims.ai;

import java.util.List;

/** 大型語言模型介面：只負責把自然語言轉成結構化 JSON（不接觸資料庫）。測試時以假實作取代。 */
public interface LlmClient {

    record Turn(String role, String content) {}

    /**
     * @param systemPrompt 系統提示
     * @param turns        對話（user／assistant），最後一則為本次使用者訊息
     * @return 模型輸出的 JSON 字串（符合 AgentPrompt.SCHEMA）
     */
    String plan(String systemPrompt, List<Turn> turns);
}
