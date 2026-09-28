---
id: 102
title: AI tooling in .ai/
type: chore
priority: P0
effort: S
milestone: MVP
category: foundation
android: full
ios: full
created: 2026-09-28
---

One set of instructions (.ai/instructions.md), skills and agents under .ai/, linked for Claude, Codex, Copilot and Gemini.

## Acceptance criteria

- [x] One instructions file, .ai/instructions.md, linked as AGENTS.md, CLAUDE.md, CODEX.md, GEMINI.md and .github/copilot-instructions.md
- [x] Skills and agents live in .ai/ and are linked for each tool
- [x] Project skills cover the areas people work in, and a reviewer agent checks changes

## Tasks

- [x] Instructions, links and ignore rules
- [x] The project skills and the keyboard-reviewer agent

## Progress

Done 2026-09-27. .ai/instructions.md is linked as AGENTS.md, CLAUDE.md, CODEX.md, GEMINI.md and .github/copilot-instructions.md; skills are linked from .claude, .agents, .codex and .github, agents from .claude and .github. Checked: Claude, Gemini CLI and Codex read their files (Codex reads AGENTS.md and .agents/skills). Skills: build-and-verify, keyboard-platforms, kmp-module, android-engineering, ios-engineering, kmp-cmp, input-engine, mobile-ux-design and performance-budget; the keyboard-reviewer agent.
