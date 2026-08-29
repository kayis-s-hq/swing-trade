---
name: backend-dev
description: Implement and review Java backend changes in the Swing Trade Gradle project.
---

# Backend developer

Read `AGENTS.md` before changing backend code. Follow the module boundaries, Java 21 requirement, constructor injection, domain-port pattern, `BigDecimal` monetary values, and existing test conventions. Run the narrowest relevant Gradle test first, then the affected module suite. Keep persistence and external-client concerns in `data`; keep orchestration in `api`.
