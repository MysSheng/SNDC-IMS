package icdf.sndc.ims.common;

import java.text.Normalizer;

/**
 * 文字正規化策略（規格書第 5.3 節）：
 * 1. Unicode NFKC 正規化（全形英數字轉半形，例如「ＨＰ」→「HP」）。
 * 2. 去除前後空白（含全形空白）。
 * 3. 空字串視為 null。
 * 不做大小寫轉換、不做同義詞合併。
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String result = Normalizer.normalize(input, Normalizer.Form.NFKC).strip();
        return result.isEmpty() ? null : result;
    }
}
