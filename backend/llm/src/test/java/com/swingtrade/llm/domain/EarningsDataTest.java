package com.swingtrade.llm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EarningsDataTest {
    @Test
    void parsesNumericAndTextualFields() {
        EarningsData data = EarningsData.fromJson("""
            {"symbol":"TCS","quarter":"Q1","revenue":100.5,"netProfit":"20.25",
             "eps":4,"ebitda":30,"guidance":"positive"}
            """);

        assertThat(data).isNotNull();
        assertThat(data.symbol()).isEqualTo("TCS");
        assertThat(data.revenue()).isEqualByComparingTo("100.5");
        assertThat(data.netProfit()).isEqualByComparingTo("20.25");
        assertThat(data.eps()).isEqualByComparingTo("4");
        assertThat(data.ebitda()).isEqualByComparingTo("30");
        assertThat(data.guidance()).isEqualTo("positive");
        assertThat(data.extractionDate()).isNotNull();
    }

    @Test
    void handlesMissingAndMalformedValuesWithoutThrowing() {
        EarningsData missing = EarningsData.fromJson("{\"symbol\":\"TCS\"}");
        assertThat(missing).isNotNull();
        assertThat(missing.revenue()).isNull();
        assertThat(EarningsData.fromJson("not-json")).isNull();
    }
}
