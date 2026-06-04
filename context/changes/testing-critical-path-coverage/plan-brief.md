# Critical-path Coverage for CSV Import and Rankings — Plan Brief

> Full plan: `context/changes/testing-critical-path-coverage/plan.md`
> Research: `context/changes/testing-critical-path-coverage/research.md`

## What & Why

Add test coverage for the critical path: CSV import → XTDB persistence → rankings calculation. This is rollout Phase 1 from `context/foundation/test-plan.md`, protecting against the highest-risk failure scenarios: silent CSV import failure (Risk #1), frontend-backend contract drift (Risk #2), incorrect ranking calculations (Risk #5), and invalid CSV data acceptance (Risk #6). Research confirmed all four risks exist with grounded failure paths identified.

## Starting Point

Test infrastructure is mature (`cognitect.test-runner`, in-memory XTDB via `test-xtdb-node`, established RLS patterns). Existing coverage:
- ✅ Service-level tests for `create-products-batch`, `list-products`, RLS
- ✅ CSV validation tests for field-level validation, batch processing
- ❌ Handler-level tests for `import-products-handler` (gap)
- ❌ Ranking service tests for `calculate-rankings` (gap)
- ❌ Schema drift tests (CSV validator vs Malli schema) (gap)

Research identified critical bugs: `product.clj:70` ignores `xt/submit-tx` return value (silent failure), no Malli validation at handler boundary, ranking edge cases untested (tie-breaking undefined, zero-quantity included).

## Desired End State

All four risks protected by automated tests:
- Handler integration tests verify CSV import round-trip (XTDB persistence), 100% rejection handling, rejected rows rendering, and RLS
- Ranking service tests cover edge cases (<5 hives, zero-quantity, ties) via integration tests with XTDB
- Schema validation tests prevent CSV validator / Malli schema drift
- Test-plan cookbook (`context/foundation/test-plan.md §6`) documented for future contributors

Success: `clj -M:test` passes with coverage across handler, service, and schema layers.

## Key Decisions Made

| Decision                       | Choice                     | Why (1 sentence)                                                                                                      | Source    |
| ------------------------------ | -------------------------- | --------------------------------------------------------------------------------------------------------------------- | --------- |
| Risk #1 test approach          | Round-trip only            | CSV → XTDB query proves persistence without mocking transaction rejection (simpler, follows existing patterns)        | Plan      |
| Risk #2 contract drift         | Both layers                | Unit test prevents schema drift (validator vs Malli), handler test proves integration (research identified dual sources) | Plan      |
| Risk #5 edge cases             | Core edge cases            | Test <5 hives, zero-quantity, ties (most likely failures); skip same-hive-twice (intentional design)                 | Plan      |
| Risk #5 tie-breaking           | Document undefined         | Test documents that tie-breaking relies on XTDB result order (research open question #3: acceptable undefined behavior) | Plan      |
| Ranking test layer             | Integration tests          | Tests XTDB aggregation query + Clojure logic; follows existing service test patterns                                 | Plan      |
| Risk #6 handler tests          | Add edge cases             | Test 100% rejection, rejected rows rendering (research identified gaps in existing CSV validator tests)              | Plan      |
| RLS coverage                   | Yes, with `every?` pattern | Verify every record's user-id via established pattern (not just counts); prevents cross-user leakage                 | Plan      |
| Schema test scope              | Positive + negative        | Valid data passes AND invalid data fails (both paths prove Malli catches drift)                                      | Plan      |
| File organization              | New handler test file      | Create `pages/products_test.clj` for handler tests (matches convention: pages/* handlers tested in pages/*)         | Plan      |
| Cookbook scope (Phase 4)       | §6.1, §6.2, §6.3, §6.5     | Add unit, integration, RLS test patterns; Phase 1 establishes RLS pattern so fill §6.3 now (not deferred to Phase 3) | Plan      |

## Scope

**In scope:**
- Handler integration tests (`test/com/apriary/pages/products_test.clj`) with RLS `every?` pattern
- Ranking service tests (`test/com/apriary/services/product_rankings_test.clj`) including tie-breaking documentation
- Schema validation tests (extend `test/com/apriary/services/product_csv_test.clj`) with positive + negative paths
- Test-plan cookbook update (`context/foundation/test-plan.md §6.1, §6.2, §6.3, §6.5`)

**Out of scope:**
- Simulating XTDB transaction failures (round-trip test is sufficient)
- Testing same-hive-twice behavior (intentional design, documented)
- Large CSV files, XSS testing (Risk #7, Phase 3: Security hardening)
- Implementation fixes (e.g., checking `xt/submit-tx` return value — separate change)

## Architecture / Approach

Follow existing test patterns:
- **Handler tests** mirror `generations_test.clj` (use `make-ctx`, test unauthorized/valid/invalid flows, verify response shape)
- **Service tests** mirror `product_test.clj` (use `with-open [node ...]`, verify XTDB state via queries, test RLS)
- **Schema tests** are new: construct entity from CSV validator output, verify `(m/explain schema entity)` returns `nil`

Tests use in-memory XTDB (`test-xtdb-node`) with auto-cleanup, RLS multi-user fixtures, and `xt/sync` before querying. All tests are integration or unit tests — no e2e or frontend tests (per test-plan guidance).

## Phases at a Glance

| Phase     | What it delivers                                  | Key risk                               |
| --------- | ------------------------------------------------- | -------------------------------------- |
| 1. Handler integration tests | CSV import round-trip, RLS with `every?` pattern, invalid input edge cases | Missing edge cases (100% rejection, rejected rows rendering) |
| 2. Ranking service tests | Edge case coverage (<5 hives, zero-quantity, ties), documents undefined tie-breaking | Tie-breaking is undefined (XTDB result order) — test documents (not enforces) current behavior |
| 3. Schema validation tests | CSV validator output passes Malli (positive) AND invalid data fails (negative) | Brittle if Malli schema or CSV validator changes independently |
| 4. Update test-plan cookbook | Document patterns for future contributors (§6.1, §6.2, §6.3, §6.5) | Clarity — entries must be actionable without reading full plan |

**Prerequisites:** Existing test infrastructure (`cognitect.test-runner`, `test-xtdb-node` from Biff, Malli schemas in `schema.clj`)

**Estimated effort:** ~1 session across 4 phases (Phases 1-3 are test implementation, Phase 4 is documentation)

## Open Risks & Assumptions

- **Tie-breaking is undefined** (Risk #5): Current ranking logic relies on XTDB result set order for ties. Test will pin current behavior but won't make it deterministic. Research question #3 flagged this; user accepted undefined behavior as acceptable for MVP.
- **100% rejection edge case** (Risk #6): Research question #4 identified this gap (success toast with "0 products" + rejected rows alert may confuse users). Test will verify current behavior; UX improvement is out of scope.
- **Schema drift detection is reactive**: Schema validation test catches drift after it happens (test fails), not proactively during development. Acceptable for this phase; CI enforcement (Phase 4: Quality-gates wiring) will surface failures early.

## Success Criteria (Summary)

- All handler tests pass, including round-trip persistence (queries XTDB directly), RLS with `every?` assertion, and 100% rejection edge case
- All ranking tests pass, covering <5 hives, zero-quantity, and tie scenarios (documenting undefined tie-breaking behavior)
- Schema validation test proves both paths: valid CSV data passes Malli AND invalid data fails Malli
- Test-plan cookbook §6.1, §6.2, §6.3, §6.5 filled in with actionable examples referencing actual test files from this phase
