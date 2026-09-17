package com.swingtrade.llm.service;

import com.swingtrade.domain.store.CandleStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SynthesisEvaluationJobTest {

    @Test
    void enabledJob_recordsSuccessfulRunAndProcessedCount() {
        SynthesisEvaluationService evaluationService = mock(SynthesisEvaluationService.class);
        CandleStore candleStore = mock(CandleStore.class);
        when(evaluationService.evaluatePending(candleStore, LocalDate.now(), 5)).thenReturn(3);
        SynthesisEvaluationJob job = new SynthesisEvaluationJob(evaluationService, candleStore, 5, true);

        job.evaluatePendingSynthesis();

        assertThat(job.getLastRun()).isNotNull();
        assertThat(job.getLastStatus()).isEqualTo("OK");
        assertThat(job.getLastCount()).isEqualTo(3);
        verify(evaluationService).evaluatePending(candleStore, LocalDate.now(), 5);
    }

    @Test
    void disabledJob_doesNotInvokeEvaluationOrUpdateRunState() {
        SynthesisEvaluationService evaluationService = mock(SynthesisEvaluationService.class);
        CandleStore candleStore = mock(CandleStore.class);
        SynthesisEvaluationJob job = new SynthesisEvaluationJob(evaluationService, candleStore, 7, false);

        job.evaluatePendingSynthesis();

        assertThat(job.getLastRun()).isNull();
        assertThat(job.getLastStatus()).isNull();
        assertThat(job.getLastCount()).isNull();
        verifyNoInteractions(evaluationService, candleStore);
    }

    @Test
    void failedJob_recordsFailureAndResetsCount() {
        SynthesisEvaluationService evaluationService = mock(SynthesisEvaluationService.class);
        CandleStore candleStore = mock(CandleStore.class);
        when(evaluationService.evaluatePending(candleStore, LocalDate.now(), 2))
                .thenThrow(new IllegalStateException("database unavailable"));
        SynthesisEvaluationJob job = new SynthesisEvaluationJob(evaluationService, candleStore, 2, true);

        job.evaluatePendingSynthesis();

        assertThat(job.getLastRun()).isNotNull();
        assertThat(job.getLastStatus()).isEqualTo("FAILED: database unavailable");
        assertThat(job.getLastCount()).isZero();
    }
}
