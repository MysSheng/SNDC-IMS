package icdf.sndc.ims.asset;

import icdf.sndc.ims.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetSortTest {

    @Test
    void defaultSortsByItemName() {
        assertThat(AssetService.parseSort(null).getOrderFor("itemName")).isNotNull();
    }

    @Test
    void acceptsWhitelistedFieldAndDirection() {
        Sort.Order order = AssetService.parseSort("quantity,desc").getOrderFor("quantity");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsUnknownField() {
        assertThatThrownBy(() -> AssetService.parseSort("pinHash,asc")).isInstanceOf(ApiException.class);
    }
}
