# Cross-Feature Regression Test — Plan Brief

> Full plan: `context/changes/testing-cross-feature-regression/plan.md`
> Research: `context/changes/testing-cross-feature-regression/research.md`

## What & Why

**From test-plan.md Risk #3:**
"CSV parsing regression breaks existing summaries feature — shared parsing logic change silently breaks summaries import"

Research confirmed both features share `csv_import/parse-csv-string` at Layer 1 (semicolon delimiter, header processing) while keeping domain validation isolated at Layer 2. The risk is real: a products-focused change to the shared parser could silently break summaries import.

## Starting Point

**Test coverage gaps (from research):**
- No summaries CSV service tests exist (products has `product_csv_test.clj`)
- No schema-drift test for summaries (products has this)
- No cross-feature test verifying both imports work sequentially

**Existing patterns to follow:**
- Products tests: `test/com/apriary/pages/products_test.clj` and `test/com/apriary/services/product_csv_test.clj`
- Biff `test-xtdb-node` for integration tests
- `[status result]` destructuring for service returns

## Desired End State

Three new test capabilities protecting summaries CSV import:

1. **Summaries CSV service tests** — `test/com/apriary/services/csv_import_test.clj` (unit-level validation)
2. **Schema drift test** — prevents CSV validator / Malli schema divergence
3. **Cross-feature integration test** — sequential products → summaries import

When complete: `clj -M:test` passes, test-plan.md §6.4 filled in with cookbook pattern.

## Key Decisions Made

| Decision                       | Choice            | Why (1 sentence)  | Source           |
| ------------------------------ | ----------------- | ----------------- | ---------------- |
| Test scope | Sequential import (products → summaries) | Directly tests the cross-feature regression scenario — products changes don't break summaries | Plan |
| Flow depth | Parse + validate only (no handler/AI) | Simpler, faster test with no AI mocking; matches service-level isolation | Plan (user choice) |
| Schema test | Add schema-drift test for summaries | Closes gap research identified — products has this, summaries doesn't | Research / Plan |
| Fixtures | All options (valid, optional fields, case-insensitive, empty) | Exercise shared parser thoroughly: case matching, nil handling, validation | Plan (user choice) |
| Verification | Valid rows returned, field values correct, rejected rows empty | Proves parsing and validation work without full XTDB round-trip | Plan (user choice) |

## Scope

**In scope:**
- Unit tests for `csv-import/process-csv-import` (summaries service)
- Schema drift test (CSV validator → Malli schema compatibility)
- Cross-feature integration test (products → summaries sequential import)
- test-plan.md §6.4 cookbook update

**Out of scope:**
- Full summaries handler tests (would require AI mocking)
- Refactoring duplicated utilities (`find-column-index`, `validate-date`)
- UI/htmx rendering tests (test-plan §7 excludes FE in CI)
- Summaries XTDB persistence in cross-feature test (service-level only)

## Architecture / Approach

**Two-layer testing strategy:**

1. **Service-level (fast, isolated):**
   - Test `process-csv-import` directly
   - No XTDB, no handler, no AI
   - Fixtures: valid/invalid observation lengths, case-insensitive headers, empty optionals

2. **Integration-level (proves coupling):**
   - Import products via handler (→ XTDB)
   - Import summaries via service (→ parse result)
   - Verify both succeed sequentially

**Key insight:** Testing summaries at service-level (not handler) avoids AI mocking while still proving the shared parser works. The cross-feature test catches shared-layer breakage without full end-to-end complexity.

## Phases at a Glance

| Phase     | What it delivers       | Key risk                  |
| --------- | ---------------------- | ------------------------- |
| 1. Summaries CSV Service Tests | Unit tests for `process-csv-import` (parse + validate) | Must cover all edge cases: short/long observation, invalid date, missing columns |
| 2. Schema Drift Test | Verify CSV validator → Malli schema compatibility | Must match products test pattern exactly (positive + negative + edge cases) |
| 3. Cross-Feature Integration | Sequential products → summaries import test | Must prove shared-layer intact without AI mocking dependency |
| 4. Update Test-Plan Cookbook | Fill in §6.4 with shipped pattern | Cookbook entry must be clear and actionable for future contributors |

**Prerequisites:** None — all context from research  
**Estimated effort:** ~1 session across 4 phases (service tests are bulk of work; schema + cross-feature follow existing patterns)

## Open Risks & Assumptions

- **Assumption:** Summaries CSV import currently works (no existing tests to verify baseline)
- **Risk:** If summaries handler has bugs, service-level test won't catch them (mitigated: scope is cross-feature protection, not full summaries coverage)
- **Assumption:** Mocked AI service pattern exists in codebase (not used in this plan, but relevant for future full handler tests)

## Success Criteria (Summary)

- All new tests pass: `clj -M:test`
- test-plan.md §6.4 cookbook filled in
- Future products changes to shared parser will fail these tests if they break summaries
