---
name: swing-trade-systematic-debugging
description: Systematic debugging workflow for the swing-trade system with persistent state tracking across context resets. Use this skill when encountering any bug, test failure, or unexpected behavior in the trading system. This skill is essential for efficient problem resolution and should be triggered proactively whenever system errors occur, signals behave unexpectedly, or data pipeline failures happen.
---

# SwingTrade Systematic Debugging Skill

## Overview

This skill provides a structured, scientific approach to debugging the swing-trade system. It maintains persistent state across context resets, ensuring debugging sessions can continue seamlessly and systematically.

## When to Use This Skill

Trigger this skill when:
- Any system error or exception occurs
- Test failures appear
- Signals behave unexpectedly
- Data pipeline failures happen
- Position calculations seem incorrect
- API responses are malformed
- Database queries return unexpected results

## Debugging Workflow

### Phase 1: Problem Definition

```
1. Document the symptom
   - What is happening?
   - What is expected?
   - When does it occur?

2. Gather initial evidence
   - Error messages
   - Logs
   - Reproduction steps

3. Define scope
   - Which components affected?
   - Is it intermittent or consistent?
   - Recent changes that might be related?
```

### Phase 2: Hypothesis Generation

```
Based on symptoms, generate hypotheses:

Hypothesis 1: [Most likely cause]
- Evidence for: [supporting facts]
- Evidence against: [contradicting facts]
- Test: [how to verify]

Hypothesis 2: [Alternative cause]
- Evidence for: [supporting facts]
- Evidence against: [contradicting facts]
- Test: [how to verify]

Hypothesis 3: [Less likely cause]
- Evidence for: [supporting facts]
- Evidence against: [contradicting facts]
- Test: [how to verify]
```

### Phase 3: Systematic Testing

```
For each hypothesis:

1. Design test
   - What will prove/disprove this?
   - How to isolate the variable?

2. Execute test
   - Run with controlled inputs
   - Capture outputs

3. Analyze results
   - Did it confirm or reject hypothesis?
   - What new information was revealed?

4. Update hypothesis list
   - Remove rejected hypotheses
   - Refine remaining hypotheses
   - Generate new hypotheses if needed
```

### Phase 4: Solution Implementation

```
1. Identify root cause
2. Design fix
3. Implement fix
4. Test fix
5. Verify no regressions
6. Document solution
```

## Debug State Management

### Debug Session File Structure

```
.debug/
├── session-[timestamp].json  # Current session state
├── evidence/                 # Collected evidence
│   ├── logs.txt
│   ├── error_messages.txt
│   └── stack_traces.txt
├── hypotheses.md             # Generated hypotheses
├── tests.md                  # Tests executed
└── resolution.md             # Final resolution
```

### Session State Schema

```json
{
  "session_id": "debug-2024-01-15-001",
  "start_time": "2024-01-15T10:30:00Z",
  "symptom": "Signal generation count dropped by 80%",
  "expected": "Normal signal count of 15-20 per day",
  "components_affected": ["SignalEngine", "SentimentAnalysisService"],
  "hypotheses": [
    {
      "id": "H1",
      "description": "Sentiment API timeout causing signal suppression",
      "status": "testing",
      "tests": []
    }
  ],
  "tests_executed": [],
  "resolution": null,
  "last_updated": "2024-01-15T11:00:00Z"
}
```

## Common SwingTrade Debugging Patterns

### Pattern 1: Signal Generation Failure

```
Symptom: No signals generated today

Debug Steps:
1. Check data ingestion logs
   → Verify candles exist for all symbols
2. Check signal engine logs
   → Verify signal generation started
3. Check sentiment API status
   → Verify vLLM/Ollama is responding
4. Check cache status
   → Verify no stale cache blocking regeneration

Evidence Collection:
- SELECT COUNT(*) FROM ohlcv_candles WHERE date = TODAY;
- Check SignalEngine logs for errors
- Test vLLM endpoint directly
- Clear sentiment cache and retry
```

### Pattern 2: Incorrect Position P&L

```
Symptom: Position P&L calculation seems wrong

Debug Steps:
1. Verify current price data
   → Check latest candle for symbol
2. Verify position entry price
   → Check position record
3. Verify quantity and direction
   → Check position fields
4. Recalculate P&L manually
   → Compare with system calculation

Evidence Collection:
- SELECT * FROM positions WHERE position_id = X;
- SELECT * FROM ohlcv_candles WHERE symbol = X ORDER BY date DESC LIMIT 1;
- Manual calculation: (current - entry) * quantity * direction
```

### Pattern 3: Data Ingestion Failure

```
Symptom: Some stocks missing data

Debug Steps:
1. Check Upstox API status
   → Verify API is accessible
2. Check API rate limits
   → Verify not rate limited
3. Check specific stock data
   → Verify stock exists in exchange
4. Check database constraints
   → Verify no duplicate rejections

Evidence Collection:
- curl to Upstox API endpoint
- Check API response codes
- SELECT COUNT(*) FROM ohlcv_candles WHERE symbol = 'XYZ' AND date = TODAY;
- Check ingestion logs for error messages
```

## Debug Report Format

```
# Debug Report - [Session ID]

## Problem Statement
[Symptom description]

## Expected Behavior
[What should happen]

## Evidence Collected
### Logs
[Relevant log excerpts]

### Error Messages
[Error messages with context]

### Stack Traces
[Full stack traces]

## Hypotheses Tested
| Hypothesis | Status | Evidence |
|------------|--------|----------|
| H1: ... | REJECTED | Test showed ... |
| H2: ... | CONFIRMED | Test showed ... |

## Root Cause
[Detailed explanation of root cause]

## Solution Implemented
[What was done to fix]

## Verification
[How the fix was verified]

## Prevention
[Steps to prevent recurrence]

## Lessons Learned
[Key takeaways]
```

## Example Usage

```bash
# Start new debug session
/skill: swing-trade-systematic-debugging --symptom "no signals generated"

# Add evidence to current session
/skill: swing-trade-systematic-debugging --add-evidence logs.txt

# Generate hypothesis
/skill: swing-trade-systematic-debugging --hypothesize

# Test hypothesis
/skill: swing-trade-systematic-debugging --test H1

# Document resolution
/skill: swing-trade-systematic-debugging --resolve "fixed sentiment API timeout"

# Generate full report
/skill: swing-trade-systematic-debugging --report
```

## Integration with Other Systems

- **GSD debugging**: Can create debug phase in GSD workflow
- **Memory persistence**: Saves debug state to episodic memory
- **Notification alerts**: Can alert on critical debugging findings
- **Documentation**: Auto-generates debug documentation for knowledge base

## Dependencies

- Database access (for evidence collection)
- Log file access
- System monitoring tools
- Git (for tracking changes)

## Performance Considerations

- Debug sessions should maintain state across context resets
- Evidence collection should be non-intrusive
- Tests should be isolated to avoid side effects
- Resolution documentation should be concise and actionable
