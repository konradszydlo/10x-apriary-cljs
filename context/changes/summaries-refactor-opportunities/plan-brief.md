# Summaries Module Quick Wins — Plan Brief

> Full plan: `context/changes/summaries-refactor-opportunities/plan.md`
> Research: `context/changes/summaries-refactor-opportunities/research.md`

## What & Why

Clean up three quick-win technical debt items in the summaries module: orphaned schema file (131 LOC dead code), schema drift (content length mismatch creating data loss risk), and schema duplication. All problems trace to Nov 27, 2025 rapid prototyping sprint where 1,177 LOC were added in 6 hours — accidental complexity from MVP time pressure, not deliberate trade-offs. Total effort: 4-6 hours across 3 phases.

## Starting Point

- **Orphaned file**: `schema/api.clj` exists with 10 well-structured schemas but zero imports (created Nov 2025 as planned validation layer, bypassed 6 hours later during UI sprint, survived 6-month dormancy)
- **Schema drift**: Content max length = 10k in schema.clj vs 50k in summaries_view.clj/services — frontend accepts 50k, database schema says 10k (potential data loss if XTDB enforces strictly)
- **Duplication**: `create-manual-summary-schema` duplicated byte-for-byte in schema/api.clj (unused) and summaries_view.clj (actively used)

**Existing safeguards**: grep verification for C2 (zero imports), explicit test coverage for C3 (`csv_import_test.clj` lines 99-124, 385-420), CI pipeline (clj-kondo + unit/integration tests).

## Desired End State

After completion:
- `schema/api.clj` **deleted** (131 LOC removed, duplication eliminated as side effect)
- Content max length = **50k everywhere** (aligned to current service-layer behavior)
- Single canonical `create-manual-summary-schema` in summaries_view.clj (docstring updated to clarify it's canonical)
- All tests GREEN, CI passes, zero user-facing changes

## Key Decisions Made

| Decision                       | Choice                                     | Why (1 sentence)                                                                                                     | Source   |
| ------------------------------ | ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- | -------- |
| **Scope**                      | All quick wins (C2, C3, C5)                | Maximum cleanup value (4-6 hours) with minimal effort — all zero-risk with test coverage.                           | Plan     |
| **Schema consolidation**       | Option A — Delete schema/api.clj (YAGNI)   | Only 1 of 8 pages has validation; Q2 products didn't add any — simpler to consolidate later when 2+ pages need it.  | Plan     |
| **E2E investment**             | Defer to separate change-id                | Keeps this focused on quick wins (4-6 hours) vs 1.5-2 week investment for E2E + God Page split.                     | Plan     |
| **Testing strategy**           | Rely on existing test suite + CI           | Quick wins have test coverage (C3) or zero blast radius (C2) — no new test infrastructure needed.                   | Plan     |
| **Sequencing**                 | C2 → C3 → C5 (research ranking)            | Start with zero-risk (C2), then data-loss fix (C3 with tests), then duplication (C5 covered by C2 deletion).        | Research |
| **Schema drift direction**     | Align to 50k (not PRD's 10k)               | Matches production service-layer behavior since Nov 2025; changing to 10k would break existing data expectations.   | Plan     |

## Scope

**In scope:**
- Remove orphaned schema/api.clj (C2 — 131 LOC deletion)
- Fix schema drift by aligning content max length to 50k (C3 — 1-line change + test updates)
- Verify duplication eliminated (C5 — covered by C2 deletion, verification only)

**Out of scope:**
- E2E test foundation (Playwright/Cypress setup) — deferred to future change-id
- God Page split (summaries_view.clj 1,274 LOC → 4 namespaces) — BLOCKED until E2E exists, est. 1.5-2 weeks
- Handler-level unit tests (105-150 mock setups for 15 handlers) — not justified for quick wins
- Adopting schema/api.clj as centralized validation layer (Option B) — YAGNI, only 1 consumer

## Architecture / Approach

**Sequential execution** following research cost-benefit ranking:
1. **Phase 1 (C2)**: Delete orphaned file — zero blast radius, completely safe
2. **Phase 2 (C3)**: Fix schema drift — test coverage enforces correctness
3. **Phase 3 (C5)**: Verification only — duplication removed by Phase 1 deletion

**No new abstractions**: All changes are cleanup/alignment, no new patterns introduced. Testing relies on existing suite (no handler tests added). CI catches regressions (clj-kondo + unit/integration tests).

## Phases at a Glance

| Phase     | What it delivers                                                  | Key risk                                                          |
| --------- | ----------------------------------------------------------------- | ----------------------------------------------------------------- |
| 1. Remove Orphaned Schema (C2) | Delete schema/api.clj (131 LOC), update docstring, verify zero imports | grep verification might miss dynamic requires (LOW — none expected) |
| 2. Fix Schema Drift (C3) | Align content max to 50k (schema.clj + 4 test updates) | Test updates could miss edge cases (LOW — explicit test coverage) |
| 3. Verify Duplication Eliminated (C5) | Confirm single canonical schema remains | No risk — verification only, covered by Phase 1 |

**Prerequisites:** None — can start immediately. All changes are internal refactoring with existing test coverage.

**Estimated effort:** 4-6 hours total (2 hours per phase, Phase 3 is verification-only)

## Open Risks & Assumptions

**Assumptions:**
- XTDB does not currently enforce the 10k schema limit strictly (or existing 50k content would have failed) — aligning to 50k prevents future enforcement from breaking existing data
- No dynamic `requiring-resolve` of `schema.api` exists (grep confirmed, but dynamic loading could bypass static analysis) — CI lint (clj-kondo) will catch if missed
- Service layer has validated at 50k since Nov 2025 — no production data exceeds 50k (changing schema to 50k won't break existing content)

**Risks:**
- **LOW**: Schema/api.clj deletion might break if someone added import since research (2026-06-14) — mitigated by grep verification in Phase 1 Step 1
- **LOW**: Test updates in Phase 2 could introduce typos (10001 vs 50001) — mitigated by test execution (will fail if wrong)
- **NONE**: Phase 3 is verification-only — if Phase 1 succeeded, duplication is gone

**Mitigation**: Each phase has automated verification (grep, tests, CI) before proceeding. Sequential execution means each phase builds confidence for the next.

## Success Criteria (Summary)

From user perspective, this plan succeeded when:
- Codebase is cleaner: 131 LOC dead code removed, no orphaned files, single canonical schema
- Schema consistency established: 50k limit everywhere (database schema matches frontend/service layer)
- Potential data loss prevented: XTDB schema aligned to production behavior, no mismatch risk
- All automated checks GREEN: grep verification passes, CI pipeline completes, tests pass
- Zero user-facing changes: UI behavior unchanged, service layer behavior unchanged (we aligned database schema to match)
