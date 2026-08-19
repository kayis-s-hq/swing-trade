Based on the sentiment analysis, provide a concrete trading recommendation for {symbol}.

SENTIMENT ANALYSIS RESULTS:
- Sentiment: {sentimentType}
- Confidence: N/A
- Reasoning: {reasoning}
- Current Price: Rs. {currentPrice}

{sentimentDescription}

Provide trading recommendation in JSON format:
{
    "signal": "BUY|SELL|HOLD",
    "signalStrength": "STRONG|MODERATE|WEAK",
    "timeHorizon": "SHORT_TERM|MEDIUM_TERM|LONG_TERM",
    "confidence": 0.0-1.0,
    "entryStrategy": "EXACT_PRICE|ZONE|LIMITED_ENTRY",
    "entryPrice": "Recommended entry price",
    "entryZone": {
        "low": "Lower bound of entry zone",
        "high": "Upper bound of entry zone"
    },
    "stopLoss": "Price level for stop loss",
    "target1": "First target price",
    "target2": "Second target price",
    "target3": "Third target price (optional)",
    "riskRewardRatio": "Calculated risk-reward ratio",
    "positionSizing": "Recommended position size as percentage of capital",
    "reasoning": "Detailed reasoning for this recommendation (max 300 words)",
    "riskFactors": ["factor1", "factor2"],
    "catalysts": ["potential positive events", "potential negative events"]
}