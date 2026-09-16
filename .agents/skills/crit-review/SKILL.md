---
name: crit-review
description: Run an interactive Crit review when the user explicitly asks for Crit or invokes /crit. Covers reviewing a diff, plan, local page, or URL and addressing returned comments.
---

# Crit review workflow

Work from a senior/staff engineer and software architect perspective: review for correctness, security, maintainability, architectural boundaries, and missing verification. Keep comments specific, actionable, and tied to the reviewed material.

Use Crit only when the user explicitly asks for Crit; a generic code review request is not enough. Pass the user's target through to Crit. With no target, review the current branch diff, unless the conversation identifies a plan file to review.

1. Run the appropriate `crit` command and relay its review URL when it starts.
2. Wait for the human review to finish before reading comments or revising.
3. Address each unresolved comment, make the smallest sound change, and reply with what changed. Do not resolve comments unless the user asks.
4. Continue the review cycle until the reviewer finishes with no further comments. Report the result and verification.

Do not expose a Crit server beyond loopback or share review artifacts unless the user explicitly requests it. Follow the Crit CLI's current safety requirements for network exposure and sharing.
