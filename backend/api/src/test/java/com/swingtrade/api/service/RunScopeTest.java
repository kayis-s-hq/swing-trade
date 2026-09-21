package com.swingtrade.api.service;

import com.swingtrade.domain.JobRunStage.StageName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RunScope validation")
class RunScopeTest {

    private static final List<String> WATCHLIST = List.of("TCS", "INFY", "RELIANCE");
    private static final Set<String> VARIANTS = Set.of("breakout-v1", "pullback-v1");

    @Test
    void emptyRequestIsTheFullRun() {
        RunScope scope = RunScope.resolve(RunRequest.NONE, WATCHLIST, VARIANTS);

        assertThat(scope.symbols()).isNull();
        assertThat(scope.includesVariant("anything")).isTrue();
        assertThat(scope.dryRun()).isFalse();
        assertThat(scope.requestJson()).isNull();
        for (StageName stage : StageName.values()) assertThat(scope.skipReason(stage)).isNull();
    }

    @Test
    void narrowsSymbolsAndVariantsCaseInsensitively() {
        var request = new RunRequest(List.of("tcs", "INFY"), List.of("pullback-v1"), null, null, null);

        RunScope scope = RunScope.resolve(request, WATCHLIST, VARIANTS);

        assertThat(scope.symbols()).containsExactly("TCS", "INFY");
        assertThat(scope.includesVariant("pullback-v1")).isTrue();
        assertThat(scope.includesVariant("breakout-v1")).isFalse();
        assertThat(scope.requestJson()).contains("\"variantIds\":[\"pullback-v1\"]");
    }

    @Test
    void unknownIdsAreRejectedWithAllOffenders() {
        var request = new RunRequest(List.of("NOPE"), List.of("ghost-v1"), List.of("BOGUS"), null, null);

        assertThatThrownBy(() -> RunScope.resolve(request, WATCHLIST, VARIANTS))
            .isInstanceOf(InvalidRunRequestException.class)
            .hasMessageContaining("NOPE").hasMessageContaining("ghost-v1").hasMessageContaining("BOGUS");
    }

    @Test
    void skipLlmSkipsTheTwoLlmStagesOnly() {
        RunScope scope = RunScope.resolve(new RunRequest(null, null, null, true, null), WATCHLIST, VARIANTS);

        assertThat(scope.skipReason(StageName.SENTIMENT)).isNotNull();
        assertThat(scope.skipReason(StageName.LLM_ANALYSIS)).isNotNull();
        assertThat(scope.skipReason(StageName.SIGNAL)).isNull();
        assertThat(scope.skipReason(StageName.PAPER_TRADE)).isNull();
    }

    @Test
    void stagesListSkipsEverythingElse() {
        RunScope scope = RunScope.resolve(new RunRequest(null, null, List.of("data_fetch", "SIGNAL"), null, null),
            WATCHLIST, VARIANTS);

        assertThat(scope.skipReason(StageName.SIGNAL)).isNull();
        assertThat(scope.skipReason(StageName.DATA_FETCH)).isNull();
        assertThat(scope.skipReason(StageName.BACKTEST)).isEqualTo("not requested");
    }

    @Test
    void dryRunSkipsPersistingStages() {
        RunScope scope = RunScope.resolve(new RunRequest(null, null, null, null, true), WATCHLIST, VARIANTS);

        assertThat(scope.dryRun()).isTrue();
        assertThat(scope.skipReason(StageName.PAPER_TRADE)).isEqualTo("dry run");
        assertThat(scope.skipReason(StageName.NEWS)).isEqualTo("dry run");
        assertThat(scope.skipReason(StageName.SIGNAL)).isNull();
    }
}
