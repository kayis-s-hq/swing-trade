---
name: ask-user-question-always
description: User wants all questions presented via AskUserQuestion tool, never as free text
metadata:
  type: feedback
---

**ALWAYS use AskUserQuestion tool for questions.** Never ask questions as free text in the response. Use it for any decision that needs user input — file locations, cleanup choices, design decisions, implementation details.

**Why:** User prefers structured Q&A via the AskUserQuestion UI component for all decisions.

**How to apply:** Whenever you would normally ask a question or present options, use AskUserQuestion instead. Group related questions together. Keep options concise.