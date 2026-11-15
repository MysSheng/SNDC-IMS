package icdf.sndc.ims.asset;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryCalculatorTest {

    @Test
    void addsWholeYears() {
        assertThat(ExpiryCalculator.expectedExpiryDate(LocalDate.of(2024, 3, 1), new BigDecimal("5")))
                .isEqualTo(LocalDate.of(2029, 3, 1));
    }

    @Test
    void supportsHalfYears() {
        assertThat(ExpiryCalculator.expectedExpiryDate(LocalDate.of(2024, 1, 15), new BigDecimal("2.5")))
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void returnsNullWhenDataMissing() {
        assertThat(ExpiryCalculator.expectedExpiryDate(null, new BigDecimal("3"))).isNull();
        assertThat(ExpiryCalculator.expectedExpiryDate(LocalDate.of(2024, 1, 1), null)).isNull();
    }
}
