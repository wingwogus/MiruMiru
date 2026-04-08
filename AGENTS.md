# MiruMiru Agent Guide

This file defines repo-local working rules for future agent threads in this workspace.

## Git And Branching
- Default branch flow is `main -> dev -> feature`.
- New feature work should branch from `dev` whenever possible.
- Branches meant to be pushed or reviewed should use descriptive prefixes such as `feat/<scope>`, `fix/<scope>`, `chore/<scope>`, or `docs/<scope>`.
- Do not use `codex/` for shared PR branches unless the user explicitly asks for it; reserve `codex/` names for temporary agent-local work only.
- If a feature branch was created from `main` before `dev` existed, it may still be merged into `dev` if the history is clean.
- Do not rewrite shared history unless the user explicitly asks for it.

## Commit And PR Conventions
- Use conventional commit prefixes for commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`.
- PR titles should use capitalized conventional style such as `Feat: add writable timetable flows`.
- Prefer one focused commit per coherent change group, but do not rewrite user history unless asked.
- Before opening a PR, summarize the change clearly and list validation steps that were actually run.
- If known issues remain, call them out explicitly in the PR body under a follow-up or known issues section.

## Review Expectations
- When asked for a review, prioritize findings first: bugs, regressions, missing tests, and operational risks.
- Keep summaries brief after findings.
- If no findings are present, say so explicitly and mention residual risks or unverified areas.

## Backend Conventions
- Preserve the current layered structure: `domain -> application -> api`.
- Prefer read/write service separation when the query logic and mutation rules are meaningfully different.
- Keep controller layers thin and move business rules into application services.
- Use `ApiResponse` for API responses unless an existing endpoint already follows a different established pattern.
- Reuse existing authentication patterns with `@AuthenticationPrincipal userId: String`.
- Prefer simple JPA repository methods or fetch strategies before introducing heavier query tooling.

## Seed Data Conventions
- Local-only bootstrap data belongs in `LocalTestDataInitializer`.
- Seed data should support local manual testing as well as automated integration tests.
- Timetable-related local seeds should keep weekday coverage rich enough for UI and conflict testing.
- The current expectation is that local timetable seeds provide at least two lectures per weekday.

## Validation Expectations
- For backend changes, run `./gradlew test` from `backend` before closing the task whenever feasible.
- If iOS code changes, run the relevant XCTest or `xcodebuild` checks when the environment supports it.
- If a check could not be run, say so clearly in the final handoff.

## Thread Continuity
- Do not assume a new thread knows prior conversational agreements unless they are encoded in the repo.
- When important workflow rules change, update this file so future agents can follow the same process.
