# 01-TechnicalIndicators-Implementation

**wave:** 1
**depends_on:** []
**files_modified:** [
  "strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java",
  "strategy/src/test/java/com/swingtrade/strategy/TechnicalIndicatorsTest.java"
]
**autonomous:** true

---

## Objective

Implement and test TechnicalIndicators service with TA4J library integration for EMA, SMA, RSI, MACD, ATR, and VolumeMA calculations.

---

## Tasks

<task>
<id>1</id>
<title>Review existing TechnicalIndicators implementation</title>
<description>
- Read current TechnicalIndicators.java implementation
- Verify TA4J integration patterns
- Identify any gaps or improvements needed
</description>
</task>

<task>
<id>2</id>
<title>Implement EMA calculation with BigDecimal and double inputs</title>
<description>
- Create calculateEMA(List<BigDecimal>, int period) method
- Create calculateEMA(List<Double>, int period) method
- Use TA4J EMAIndicator internally
- Return null for insufficient data
- Add JavaDoc comments
</description>
</task>

<task>
<id>3</id>
<title>Implement SMA calculation with BigDecimal and double inputs</title>
<description>
- Create calculateSMA(List<BigDecimal>, int period) method
- Create calculateSMA(List<Double>, int period) method
- Use TA4J SMAIndicator internally
- Return null for insufficient data
- Add JavaDoc comments
</description>
</task>

<task>
<id>4</id>
<title>Implement RSI calculation with BigDecimal and double inputs</title>
<description>
- Create calculateRSI(List<BigDecimal>, int period) method
- Create calculateRSI(List<Double>, int period) method
- Use TA4J RSIIndicator internally
- Return null for insufficient data
- Add JavaDoc comments
</description>
</task>

<task>
<id>5</id>
<title>Implement MACD calculation with multiple periods</title>
<description>
- Create calculateMACD(List<BigDecimal>, fastPeriod, slowPeriod, signalPeriod) method
- Use TA4J MACDIndicator internally
- Return null for insufficient data
- Add JavaDoc comments
</description>
</task>

<task>
<id>6</id>
<title>Implement ATR calculation with OHLCV candles</title>
<description>
- Create helper class CandleWithPrices for OHLCV data
- Create calculateATR(List<CandleWithPrices>, int period) method
- Use TA4J ATRIndicator internally
- Return null for insufficient data
- Add JavaDoc comments
</description>
</task>

<task>
<id>7</id>
<title>Implement VolumeMA calculation</title>
<description>
- Create calculateVolumeMA(List<BigDecimal>, int period) method
- Use TA4J VolumeIndicator with SMA internally
- Return null for insufficient data
- Add JavaDoc comments
</description>
</task>

<task>
<id>8</id>
<title>Create unit tests for TechnicalIndicators</title>
<description>
- Create TechnicalIndicatorsTest.java
- Test each indicator with known input/output (TA4J examples)
- Test edge cases: insufficient data, null inputs, NaN behavior
- Use synthetic data for strategy scenarios (uptrend, downtrend, sideways)
- Target 100% coverage for TechnicalIndicators class
</description>
</task>

</task>

## Verification

<verification>
<criteria>
- All 6 indicators implemented (EMA, SMA, RSI, MACD, ATR, VolumeMA)
- Both BigDecimal and double input methods available
- Unit tests created with known input/output verification
- Edge cases explicitly tested (insufficient data, null, NaN)
- JaCoCo coverage >= 100% for TechnicalIndicators class
</criteria>
</verification>

## must_haves

- [ ] TechnicalIndicators class implements all 6 indicators
- [ ] Both BigDecimal and double input methods available
- [ ] Unit tests pass for all indicators
- [ ] Edge cases handled correctly (null returns for insufficient data)
