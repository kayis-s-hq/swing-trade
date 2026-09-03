# LLM module

The LLM module provides OpenAI-compatible/vLLM client integration, news ingestion, sentiment analysis, and prompt resources. It is used by API orchestration and does not own trading decisions or persistence.

Run from `backend/`:

```bash
./gradlew :llm:test
```

Runtime configuration is supplied through `infra/env/.env`; never place credentials in this README or prompt files.
