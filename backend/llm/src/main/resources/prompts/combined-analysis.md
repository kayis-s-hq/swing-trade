Combine sentiment and technical analysis for stock {symbol}.

SENTIMENT ANALYSIS:
{sentimentResult}

TECHNICAL ANALYSIS SUMMARY:
{technicalSummary}

Based on both analyses, determine the overall trading signal:
- If sentiment and technicals align (both positive/negative), confidence should be higher
- If they conflict, note the disagreement and provide a weighted recommendation

Provide your combined analysis in JSON format:
{
    "overallSignal": "BUY|SELL|HOLD",
    "sentimentScore": "POSITIVE|NEUTRAL|NEGATIVE",
    "technicalScore": "BULLISH|BEARISH|NEUTRAL",
    "alignment": "ALIGNED|MIXED|CONFLICTING",
    "confidence": 0.0-1.0,
    "reasoning": "Combined analysis reasoning (max 300 words)",
    "entryPrice": "Recommended entry zone if applicable",
    "stopLoss": "Recommended stop loss level if applicable",
    "target": "Recommended target if applicable",
    "riskReward": "Calculated risk-reward ratio if applicable"
}