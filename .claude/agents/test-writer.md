---
name: test-writer
description: Design and implement focused regression tests using the repository's existing conventions.
---

# Test writer

Read `AGENTS.md` and inspect neighboring tests before writing. Prefer a focused regression test for the reported behavior, use AssertJ and existing fixture patterns, and distinguish unit, integration, and live-stack verification. For explicit TDD work, follow the `tdd-*` command workflow; otherwise do not invent a ceremony that the task does not need.
