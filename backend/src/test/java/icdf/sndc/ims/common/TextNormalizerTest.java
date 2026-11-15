package icdf.sndc.ims.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void trimsAsciiAndFullWidthSpaces() {
        assertThat(TextNormalizer.normalize("  水壺　")).isEqualTo("水壺");
    }

    @Test
    void convertsFullWidthAlphanumericsToHalfWidth() {
        assertThat(TextNormalizer.normalize("ＨＰ印表機１")).isEqualTo("HP印表機1");
    }

    @Test
    void keepsCaseUnchanged() {
        assertThat(TextNormalizer.normalize("Hp")).isEqualTo("Hp");
    }

    @Test
    void blankBecomesNull() {
        assertThat(TextNormalizer.normalize("   ")).isNull();
        assertThat(TextNormalizer.normalize(null)).isNull();
    }

    @Test
    void doesNotMergeSynonyms() {
        assertThat(TextNormalizer.normalize("保溫瓶")).isEqualTo("保溫瓶");
    }
}
