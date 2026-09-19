package com.swingtrade.data.utilities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FyersOrderUtilityTest {
    @Test
    void mapsSupportedOrderTypesAndSidesToSdkCodes() {
        assertThat(FyersOrderType.LIMIT.getCode()).isEqualTo(1);
        assertThat(FyersOrderType.MARKET.getCode()).isEqualTo(2);
        assertThat(FyersOrderType.SL_MARKET.getCode()).isEqualTo(3);
        assertThat(FyersOrderType.SL_LIMIT.getCode()).isEqualTo(4);
        assertThat(FyersOrderType.fromCode(1)).isEqualTo(FyersOrderType.LIMIT);
        assertThat(FyersOrderType.fromCode(4)).isEqualTo(FyersOrderType.SL_LIMIT);
        assertThat(FyersOrderType.fromCode(99)).isNull();
        assertThat(FyersSide.BUY.getCode()).isEqualTo(1);
        assertThat(FyersSide.SELL.getCode()).isEqualTo(-1);
        assertThat(FyersSide.fromCode(1)).isEqualTo(FyersSide.BUY);
        assertThat(FyersSide.fromCode(-1)).isEqualTo(FyersSide.SELL);
        assertThat(FyersSide.fromCode(0)).isNull();
    }
}
