package com.swingtrade.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValueRecordsTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    void simpleAnalysisRecordsExposeTheirValues() {
        BacktestResult backtest = new BacktestResult(7L, "TCS", DATE, 10, 6, 4,
            .6, 4.2, -1.8, 3.0, 1.4, 18.0, 2.1, 2.3, true);
        FundamentalData fundamentals = new FundamentalData("TCS", 1_000L, BigDecimal.TEN);
        LlmAnalysisResult llm = new LlmAnalysisResult(2L, "run-1", "TCS", DATE, "BUY",
            .85, "Strong trend", List.of("trend"), List.of("growth"), List.of(), 80, "BUY", true, false, null);
        PersistedNewsArticle persisted = new PersistedNewsArticle(9L,
            NewsArticle.builder().symbol("TCS").title("Results").build(),
            OffsetDateTime.parse("2026-01-15T10:00:00Z"));

        assertThat(backtest.totalTrades()).isEqualTo(10);
        assertThat(backtest.hasEnoughData()).isTrue();
        assertThat(fundamentals).extracting(FundamentalData::symbol, FundamentalData::marketCap,
            FundamentalData::peRatio).containsExactly("TCS", 1_000L, BigDecimal.TEN);
        assertThat(llm.recommendation()).isEqualTo("BUY");
        assertThat(llm.keyDrivers()).containsExactly("trend");
        assertThat(persisted.id()).isEqualTo(9L);
        assertThat(persisted.article().title()).isEqualTo("Results");
    }

    @Test
    void benchmarkSeriesCopiesCandleListAndValidatesWindow() {
        OhlcvCandle candle = OhlcvCandle.of("NIFTY", DATE, BigDecimal.TEN, BigDecimal.TEN,
            BigDecimal.TEN, BigDecimal.TEN, 100L);
        List<OhlcvCandle> candles = new java.util.ArrayList<>(List.of(candle));
        BenchmarkCandleSeries series = new BenchmarkCandleSeries("NIFTY50", DATE, DATE, candles);
        candles.clear();

        assertThat(series.candles()).containsExactly(candle);
        assertThat(new BenchmarkCandleSeries("NIFTY50", DATE, DATE, null).candles()).isEmpty();
        assertThatThrownBy(() -> new BenchmarkCandleSeries(" ", DATE, DATE, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkCandleSeries("NIFTY50", DATE.plusDays(1), DATE, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositeAnalysisAndNestedRecordsPreserveAnalysisDetails() {
        CompositeAnalysis.SourceScore source = new CompositeAnalysis.SourceScore("RSI", 8, .5, "momentum");
        CompositeAnalysis.NewsScore news = new CompositeAnalysis.NewsScore(7, "positive",
            List.of("earnings"), List.of("none"), 3);
        CompositeAnalysis.TechnicalScore technical = new CompositeAnalysis.TechnicalScore(8, "BUY", .9,
            List.of("RSI"));
        CompositeAnalysis analysis = new CompositeAnalysis("TCS", DATE, 8, "BUY", BigDecimal.valueOf(.9),
            List.of(source), news, technical, new CompositeAnalysis.FundamentalScore(6, List.of("PE")),
            new CompositeAnalysis.BacktestScore(20, .7, 2.0, 4.0, 12.0, 1.5, true),
            "Aligned signals", new SynthesisResult("Looks good", "BUY", .9, List.of("trend"), List.of(), List.of(), true));

        assertThat(analysis.symbol()).isEqualTo("TCS");
        assertThat(analysis.sources()).containsExactly(source);
        assertThat(analysis.news().articleCount()).isEqualTo(3);
        assertThat(analysis.technical().indicators()).containsExactly("RSI");
        assertThat(analysis.backtest().profitFactor()).isEqualTo(2.0);
        assertThat(analysis.synthesis().success()).isTrue();
    }

    @Test
    void eventAndExposureRecordsApplyTheirInvariants() {
        EligibilityInputs.EventWindow found = EligibilityInputs.EventWindow.found(3);
        EligibilityInputs.EventWindow absent = EligibilityInputs.EventWindow.noneFound();
        EligibilityInputs inputs = new EligibilityInputs("TCS", BigDecimal.valueOf(50), 20,
            false, false, false, true, found, absent);
        EligibilityAssessment assessment = new EligibilityAssessment(true,
            List.of(EligibilityAssessment.RejectionReason.RESULTS_TOO_CLOSE));
        PortfolioExposureContext.Holding holding = new PortfolioExposureContext.Holding("INFY", "IT", BigDecimal.valueOf(100));
        PortfolioExposureContext context = new PortfolioExposureContext("TCS", "IT", BigDecimal.valueOf(200), BigDecimal.valueOf(10_000),
            List.of(holding), Map.of());

        assertThat(found).extracting(EligibilityInputs.EventWindow::known, EligibilityInputs.EventWindow::tradingDaysUntil)
            .containsExactly(true, 3);
        assertThat(absent.tradingDaysUntil()).isEqualTo(-1);
        assertThat(inputs.priceBandEligible()).isTrue();
        assertThat(assessment.rejectionReasons()).containsExactly(EligibilityAssessment.RejectionReason.RESULTS_TOO_CLOSE);
        assertThat(context.openHoldings()).containsExactly(holding);
        assertThatThrownBy(() -> new EligibilityInputs.EventWindow(false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorrelationExposureLimit(1.1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectorExposureLimit(1, -0.1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycleRecordsAndConvenienceFactoriesExposeDefaults() {
        UUID runId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 1, 15, 9, 0);
        JobRun run = new JobRun(runId, JobRun.TriggerType.MANUAL, JobRun.Status.RUNNING, now, null, 2, 1, 0, null);
        JobRunStage stage = new JobRunStage(runId, "TCS", JobRunStage.StageName.SIGNAL,
            JobRunStage.Status.COMPLETED, now, now.plusSeconds(1), 100L, null, "log", "BUY");
        PositionEntry entry = PositionEntry.of("TCS", BigDecimal.TEN, DATE, 2, now, "breakout", "p1", "b1", null, null, null);
        PositionRisk risk = new PositionRisk(BigDecimal.ONE, BigDecimal.TWO, null);
        PositionValuation valuation = new PositionValuation(BigDecimal.TEN, null, null);
        RelativeStrengthAssessment unavailable = RelativeStrengthAssessment.unavailable("No index data");

        assertThat(run.runId()).isEqualTo(runId);
        assertThat(stage.stageName()).isEqualTo(JobRunStage.StageName.SIGNAL);
        assertThat(entry.exchange()).isEqualTo(Exchange.NSE);
        assertThat(entry.direction()).isEqualTo(TradeDirection.LONG);
        assertThat(entry.averagePrice()).isEqualTo(BigDecimal.TEN);
        assertThat(risk.marginUtilized()).isEqualTo(BigDecimal.ZERO);
        assertThat(valuation.unrealizedPnL()).isEqualTo(BigDecimal.ZERO);
        assertThat(unavailable).extracting(RelativeStrengthAssessment::eligible, RelativeStrengthAssessment::reason)
            .containsExactly(false, "No index data");
        assertThat(MarketRegimeAssessment.unavailable("No regime data").regime()).isEqualTo(MarketRegime.UNKNOWN);
    }

    @Test
    void decisionsMetadataAndNewsBuilderExposeImportantValues() {
        PortfolioExposureDecision accepted = PortfolioExposureDecision.accept();
        PortfolioExposureDecision rejected = PortfolioExposureDecision.reject("CORRELATION_LIMIT");
        SignalStrategyMetadata metadata = new SignalStrategyMetadata(" ", 0);
        ZonedDateTime published = ZonedDateTime.of(DATE.atStartOfDay(), ZoneOffset.UTC);
        NewsArticle article = NewsArticle.builder().symbol("TCS").title("Title").link("https://example.test")
            .description("Description").publishedDate(published).source("RSS").rawContent("raw").build();
        SynthesisResult legacy = new SynthesisResult("narrative", "HOLD", .4, List.of(), List.of(), List.of());

        assertThat(accepted).extracting(PortfolioExposureDecision::accepted, PortfolioExposureDecision::reason)
            .containsExactly(true, "");
        assertThat(rejected.reason()).isEqualTo("CORRELATION_LIMIT");
        assertThat(metadata).extracting(SignalStrategyMetadata::strategy, SignalStrategyMetadata::version)
            .containsExactly("DEFAULT", 1);
        assertThat(article).extracting(NewsArticle::symbol, NewsArticle::title, NewsArticle::link,
            NewsArticle::description, NewsArticle::publishedDate, NewsArticle::source, NewsArticle::rawContent)
            .containsExactly("TCS", "Title", "https://example.test", "Description", published, "RSS", "raw");
        assertThat(legacy.success()).isFalse();
    }

    @Test
    void sharedConstantsAndPositionEventsExposeStableContracts() {
        PositionClosedEvent event = new PositionClosedEvent("TCS", DATE, "TARGET_HIT",
            BigDecimal.valueOf(4.25));

        assertThat(event.getSymbol()).isEqualTo("TCS");
        assertThat(event.getSignalDate()).isEqualTo(DATE);
        assertThat(event.getOutcome()).isEqualTo("TARGET_HIT");
        assertThat(event.getPnlPct()).isEqualByComparingTo("4.25");
        assertThat(StrategyParams.EMA_FAST).isEqualTo(20);
        assertThat(StrategyParams.RSI_LOWER).isEqualByComparingTo("50");
        assertThat(StrategyParams.RSI_UPPER).isEqualByComparingTo("65");
        assertThat(StrategyParams.MIN_CANDLES).isEqualTo(50);
    }
}
