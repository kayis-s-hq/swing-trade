Analyze the overall sentiment by considering the following news articles about stock {symbol}:

{articles}

Please provide a comprehensive sentiment analysis in JSON format:
{
    "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
    "confidence": 0.0-1.0,
    "reasoning": "Analysis considering all articles (max 250 words)",
    "keyFactors": ["factor1", "factor2"],
    "articleCount": {articleCount},
    "positiveArticles": {positiveCount},
    "negativeArticles": {negativeCount},
    "tradingImplication": "How this combined sentiment affects trading decisions"
}