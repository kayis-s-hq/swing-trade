Analyse the following for a swing trade entry decision on {symbol}.

Recent news headlines (last 7 days):
{newsContent}

Latest earnings summary:
{earningsContent}

Task: Determine if news sentiment supports a 1-4 week swing trade entry.

Respond in this exact JSON format only, no other text:
{
  "score": "POSITIVE|NEUTRAL|NEGATIVE",
  "confidence": 0.0-1.0,
  "summary": "2 sentence max reasoning",
  "red_flags": ["list any specific risks"],
  "catalysts": ["list any upcoming catalysts"]
}

/no_think