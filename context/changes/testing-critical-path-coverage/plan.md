# Critical-path Coverage for CSV Import and Rankings Implementation Plan

## Overview

Add test coverage for the critical path: CSV import → XTDB persistence → rankings calculation. This phase protects against risks #1 (silent failure), #2 (contract drift), #5 (ranking bugs), and #6 (invalid input) identified in `context/foundation/test-plan.md §3 Phase 1`.

Tests use existing infrastructure (`cognitect.test-runner`, in-memory XTDB via `test-xtdb-node`) and follow established patterns from `product_test.clj`, `generations_test.clj`.

## Current State Analysis

### Key Discoveries:

- **Risk #1 (silent failure) is CRITICAL**: `product.clj:70` ignores `xt/submit-tx` return value; handler always returns success even if XTDB write fails
- **Risk #2 (contract drift) is CONFIRMED**: No Malli validation at handler boundary (`products.clj:52`); CSV validator and Malli schema are dual sources of truth with no sync enforcement
- **Risk #5 (ranking bugs) is PARTIALLY CONFIRMED**: No tests exist for `product_rankings.clj`; complex edge cases (tie-breaking undefined, zero-quantity included, same hive appears twice with different metrics)
- **Risk #6 (invalid input) is PARTIALLY COVERED**: Field validation exists (`product_csv_test.clj`), but no handler-level tests for edge cases (100% rejection, rejected rows rendering)

### Existing Test Infrastructure:

**Test runner:** `cognitect.test-runner` v0.5.1, invoked via `clj -M:test`

**In-memory XTDB pattern:**
```clojure
(with-open [node (test-xtdb-node [])]
  ;; Write: pass node to service
  ;; Read: (xt/sync node) then (xt/db node)
  )
```

**Handler context builder:** `make-ctx` helper from `generations_test.clj:8-16` creates test context with session, node, params

**RLS test pattern:** Multi-user fixtures verify user A cannot see user B's data (`product_test.clj:90-112`, `generations_test.clj:89-105`)

### Existing Coverage:

- ✅ **Service-level tests**: `product_test.clj` covers `create-products-batch`, `list-products`, RLS
- ✅ **CSV validation tests**: `product_csv_test.clj` covers field-level validation, batch processing
- ❌ **Handler-level tests**: None exist for `import-products-handler`
- ❌ **Ranking tests**: No `product_rankings_test.clj` file
- ❌ **Schema drift tests**: No verification that CSV validator output matches Malli schema

## Desired End State

### Key Test Coverage Added:

1. **Handler integration tests** (`test/com/apriary/pages/products_test.clj`):
   - CSV import round-trip proves XTDB persistence (Risk #1)
   - Handler contract verified (request validation, response structure) (Risk #2)
   - Invalid input edge cases: 100% rejection, rejected rows rendered (Risk #6)
   - RLS: user A cannot import/see user B's products

2. **Ranking service tests** (`test/com/apriary/services/product_rankings_test.clj`):
   - Edge cases: <5 hives, zero-quantity in bottom rankings, tie scenarios (Risk #5)
   - Integration tests with XTDB aggregation query verification

3. **Schema validation tests** (extend `test/com/apriary/services/product_csv_test.clj`):
   - CSV validator output passes Malli `:product` schema (Risk #2 drift prevention)

4. **Test-plan cookbook updated** (`context/foundation/test-plan.md §6`):
   - §6.1 Adding a unit test
   - §6.2 Adding an integration test
   - §6.5 Per-rollout-phase notes

### How to Verify:

All tests pass: `clj -M:test`
Coverage includes handler, service, and schema layers for all four risks

## What We're NOT Doing

- **Not simulating XTDB transaction failures**: Round-trip persistence test is sufficient; simulating `xt/submit-tx` rejection would require mocking and adds minimal signal (user decision: round-trip only)
- **Not testing same-hive-twice behavior** (Risk #5): Intentional design per `product_rankings.clj:41` comment; grouping by metric prevents unit mixing
- **Not testing large CSV files or XSS**: These are Risk #7 (Phase 3: Security hardening), not this phase
- **Not refactoring service code**: This phase adds tests only; implementation fixes (e.g., checking `xt/submit-tx` return value) are out of scope

## Implementation Approach

Follow existing test patterns:
1. **Handler tests** mirror `generations_test.clj` structure (use `make-ctx`, test unauthorized/invalid/valid flows, verify response shape)
2. **Service tests** mirror `product_test.clj` patterns (use `with-open [node ...]`, verify XTDB state via queries, test RLS)
3. **Schema tests** are new but simple: construct entity from CSV validator output, verify `(nil? (m/explain schema entity))`

Phases are ordered to build confidence incrementally:
- Phase 1: Handler tests (highest user-facing signal)
- Phase 2: Ranking tests (complex logic, no existing coverage)
- Phase 3: Schema tests (simple, drift prevention)
- Phase 4: Document patterns for future contributors

## Critical Implementation Details

### RLS Assertion Pattern

RLS tests in this codebase verify **every record's user-id**, not just query counts. The established pattern from `product_test.clj:90-112` and `summary_test.clj:135-152`:

```clojure
(is (every? #(= (:product/user-id %) expected-user) (:products result)))
```

This is non-obvious and easy to miss. A test that only checks `(= (count products) N)` passes even if products from other users leak into the result set. Phase 1 handler tests must use this pattern for RLS verification.

## Phase 1: Handler Integration Tests

### Overview

Create `test/com/apriary/pages/products_test.clj` to test `import-products-handler` end-to-end. Covers risks #1 (silent failure), #2 (contract), #6 (invalid input), and RLS.

### Changes Required:

#### 1. Create Handler Test File

**File**: `test/com/apriary/pages/products_test.clj`

**Intent**: Test the full CSV import flow from handler entry to XTDB persistence, verifying request handling, error cases, and RLS.

**Contract**: New namespace `com.apriary.pages.products-test` with handler integration tests following the pattern from `generations_test.clj`:
- `make-ctx` helper to build test context
- Tests for: unauthorized, valid import, 100% rejection, rejected rows rendering, RLS

Tests to implement:
1. **`import-products-unauthorized-test`** — 401 when no session
2. **`import-products-valid-round-trip-test`** (Risk #1) — Valid CSV → verify XTDB persistence via `list-products` query AND every record has correct `:product/user-id` (RLS assertion pattern from `product_test.clj:90-112`)
3. **`import-products-empty-csv-test`** — 400 when CSV param empty
4. **`import-products-all-invalid-rows-test`** (Risk #6) — 100% rejection → success response, rejected rows rendered, zero products in XTDB
5. **`import-products-rejected-rows-rendering-test`** (Risk #6) — Mixed valid/invalid → verify rejected rows in response body
6. **`import-products-rls-test`** — User A imports, user B cannot see those products; verify via `(every? #(= (:product/user-id %) user-a) ...)` pattern

#### 2. Helper Function

**File**: `test/com/apriary/pages/products_test.clj`

**Intent**: Create a test context builder helper following the pattern from `generations_test.clj:8-16`.

**Contract**: Function `make-ctx` accepts `node`, `user-id`, and optional `:params` keyword arg; returns map with `:session`, `:biff.xtdb/node`, `:biff/db`, and `:params` keys. The implementer will adapt the existing pattern to this test file.

### Success Criteria:

#### Automated Verification:

- Handler test file exists and all tests pass: `clj -M:test -n com.apriary.pages.products-test`
- Round-trip test (Risk #1) queries XTDB directly (not via `list-products` service) and verifies records exist
- 100% rejection test (Risk #6) verifies zero products in XTDB: `(= 0 (count products))`
- RLS test verifies every product record has correct `:product/user-id` via `(every? #(= (:product/user-id %) expected-user) ...)`

#### Manual Verification:

- Review test output for clear failure messages
- Verify test comments reference risk numbers from test-plan (e.g., `; Risk #1`)

---

## Phase 2: Ranking Service Tests

### Overview

Create `test/com/apriary/services/product_rankings_test.clj` to test `calculate-rankings` with edge cases. Covers Risk #5.

### Changes Required:

#### 1. Create Ranking Test File

**File**: `test/com/apriary/services/product_rankings_test.clj`

**Intent**: Test ranking calculation edge cases using integration tests with in-memory XTDB to verify both aggregation query and Clojure sorting logic.

**Contract**: New namespace `com.apriary.services.product-rankings-test` with integration tests following the pattern from `product_test.clj`:
- Use `(with-open [node (test-xtdb-node [])] ...)`
- Insert products via `create-products-batch`
- Call `calculate-rankings`, verify top/bottom results

Tests to implement:
1. **`calculate-rankings-basic-test`** — Happy path: 10 hives, verify top 5 and bottom 5 are correct
2. **`calculate-rankings-fewer-than-five-hives-test`** (Risk #5 edge case) — 3 hives total → verify top/bottom both return 3 entries, not 5
3. **`calculate-rankings-zero-quantity-test`** (Risk #5 edge case) — Hive with 0 total quantity → verify it appears in bottom rankings
4. **`calculate-rankings-tie-scenario-test`** (Risk #5 edge case) — Two hives with identical totals → verify both appear in results (order may vary but both present)
5. **`calculate-rankings-multi-product-test`** — Multiple product types → verify each has independent top/bottom rankings

#### 2. Test Fixture Helper

**File**: `test/com/apriary/services/product_rankings_test.clj`

**Intent**: Helper to create product records for ranking tests.

**Contract**:
```clojure
(defn create-test-products
  [node user-id products]
  (product-service/create-products-batch node user-id products)
  (xt/sync node))
```

### Success Criteria:

#### Automated Verification:

- All ranking tests pass: `clj -M:test -v com.apriary.services.product-rankings-test`
- Edge case tests verify: <5 hives, zero-quantity, tie handling
- Multi-product test verifies independent rankings per product type

#### Manual Verification:

- Review tie scenario test — verify it documents that tie-breaking is undefined (XTDB result order), not enforcing determinism (per research.md open question #3: "acceptable undefined behavior")
- Verify edge case coverage matches research findings from `research.md` (fewer than 5 hives, zero-quantity, ties)

---

## Phase 3: Schema Validation Tests

### Overview

Extend `test/com/apriary/services/product_csv_test.clj` to verify CSV validator output matches Malli `:product` schema. Prevents drift (Risk #2).

### Changes Required:

#### 1. Add Schema Drift Test

**File**: `test/com/apriary/services/product_csv_test.clj`

**Intent**: Prevent drift between CSV validator constraints and Malli schema by verifying validated CSV row output passes Malli validation.

**Contract**: New test `csv-validator-matches-schema-test` that constructs a product entity from CSV validator output and verifies `(m/explain product-schema entity)` returns `nil`.

Requires:
- Import `malli.core` as `m`
- Import `com.apriary.schema` for `:product` schema
- Construct entity with all required fields (`:xt/id`, `:product/id`, `:product/user-id`, `:product/created-at`, `:product/updated-at`, plus CSV fields)
- Test multiple metric values (`"kg"`, `"ml"`, `"g"`) to verify enum sync

### Success Criteria:

#### Automated Verification:

- Schema validation test passes: `clj -M:test -n com.apriary.services.product-csv-test`
- Test verifies all three metric enum values (`"kg"`, `"ml"`, `"g"`) pass Malli validation
- Test verifies quantity constraint (`:int {:min 1}`) matches CSV validator rule (`> 0`)
- Negative test: entity with invalid metric (e.g., `"liters"`) fails Malli validation (proves Malli would catch drift)

#### Manual Verification:

- Review test to ensure both positive (valid data passes) and negative (invalid data fails) paths are covered

---

## Phase 4: Update Test-Plan Cookbook

### Overview

Document test patterns added in this rollout phase in `context/foundation/test-plan.md §6` so future contributors know how to add tests.

### Changes Required:

#### 1. Fill in §6.1 Adding a Unit Test

**File**: `context/foundation/test-plan.md`

**Intent**: Document unit test pattern using the schema validation test from Phase 3 as the example.

**Contract**: Replace `TBD — see §3 Phase 1` with a section titled "6.1 Adding a unit test" that includes:
- Example use case: CSV parsing validation, schema drift prevention
- Pattern showing `[status result]` destructuring for service function returns
- Key conventions: test both valid/invalid paths, include edge cases
- Reference to `test/com/apriary/services/product_csv_test.clj` for concrete examples

#### 2. Fill in §6.2 Adding an Integration Test

**File**: `context/foundation/test-plan.md`

**Intent**: Document integration test pattern using handler and ranking tests from Phases 1-2 as examples.

**Contract**: Replace `TBD — see §3 Phase 1` with a section titled "6.2 Adding an integration test" that includes:
- Example use cases: handler end-to-end, service with XTDB
- Pattern showing `(with-open [node (test-xtdb-node [])] ...)` for auto-cleanup
- Key conventions: call `(xt/sync node)` before reading XTDB state, verify both response structure AND XTDB persistence, use `make-ctx` helper for handler tests
- References to `test/com/apriary/pages/products_test.clj` and `test/com/apriary/services/product_rankings_test.clj` for concrete examples

#### 3. Fill in §6.3 Adding a Multi-User RLS Test

**File**: `context/foundation/test-plan.md`

**Intent**: Document RLS test pattern using the handler RLS test from Phase 1 as the example.

**Contract**: Replace `TBD — see §3 Phase 3` with a section titled "6.3 Adding a multi-user RLS test" that includes:
- Example use case: verify user A cannot access user B's product records
- Pattern showing multi-user fixture creation and cross-user access verification
- Key RLS assertion: `(every? #(= (:product/user-id %) expected-user) ...)` — verify ALL records, not just count
- Reference to `test/com/apriary/pages/products_test.clj` and `test/com/apriary/services/product_test.clj:90-112` for concrete examples

#### 4. Add §6.5 Per-Rollout-Phase Notes

**File**: `context/foundation/test-plan.md`

**Intent**: Capture lessons learned from Phase 1 rollout.

**Contract**: Append to §6.5 a subsection titled "Phase 1 (Critical-path coverage):" with 2-3 bullet points covering:
- Round-trip persistence test approach (CSV → XTDB query catches silent failure without simulating transaction rejection)
- Schema drift test purpose (prevents CSV validator and Malli schema from diverging)
- Ranking edge cases worth explicit coverage (<5 hives, zero-quantity, ties are non-obvious)

### Success Criteria:

#### Automated Verification:

- `context/foundation/test-plan.md` passes markdown lint (if configured)
- File diff shows §6.1, §6.2, §6.3, §6.5 filled in

#### Manual Verification:

- Review cookbook entries for clarity — could a new contributor add a test following these patterns?
- Verify examples reference actual test files from this phase (products_test.clj, product_rankings_test.clj, product_csv_test.clj)

---

## Testing Strategy

### Unit Tests:

- **CSV validation**: Field-level validation, batch processing (`product_csv_test.clj`)
- **Schema drift**: CSV validator output passes Malli validation (Phase 3)

### Integration Tests:

- **Handler**: End-to-end CSV import → XTDB persistence (`products_test.clj`, Phase 1)
- **Ranking**: XTDB aggregation query + edge cases (`product_rankings_test.clj`, Phase 2)
- **RLS**: Multi-user isolation at handler and service levels (Phase 1)

### Manual Testing Steps:

Not applicable — all risks are testable via automated tests. Manual verification limited to reviewing test output clarity.

## References

- Test plan: `context/foundation/test-plan.md`
- Research: `context/changes/testing-critical-path-coverage/research.md`
- Existing test patterns:
  - Handler: `test/com/apriary/pages/generations_test.clj:8-16` (make-ctx), `test/com/apriary/pages/generations_test.clj:22-28` (unauthorized test)
  - Service: `test/com/apriary/services/product_test.clj:15-39` (XTDB round-trip), `test/com/apriary/services/product_test.clj:90-112` (RLS)
  - CSV validation: `test/com/apriary/services/product_csv_test.clj:5-83` (field-level), `test/com/apriary/services/product_csv_test.clj:85-128` (batch)
- XTDB test utilities: `com.biffweb/test-xtdb-node` (in-memory XTDB with auto-cleanup)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Handler Integration Tests

#### Automated

- [x] 1.1 Handler test file exists and all tests pass: `clj -M:test -n com.apriary.pages.products-test` — a351834
- [x] 1.2 Round-trip test (Risk #1) queries XTDB directly and verifies records exist — a351834
- [x] 1.3 100% rejection test (Risk #6) verifies zero products in XTDB — a351834
- [x] 1.4 RLS test verifies every product record has correct `:product/user-id` — a351834

#### Manual

- [x] 1.5 Review test output for clear failure messages and risk number comments — a351834

### Phase 2: Ranking Service Tests

#### Automated

- [x] 2.1 All ranking tests pass: `clj -M:test -n com.apriary.services.product-rankings-test` — 5c3eef5
- [x] 2.2 Edge case tests verify: <5 hives, zero-quantity, tie handling — 5c3eef5
- [x] 2.3 Multi-product test verifies independent rankings per product type — 5c3eef5

#### Manual

- [x] 2.4 Review tie scenario test — verify it documents undefined behavior, not enforcing determinism — 5c3eef5

### Phase 3: Schema Validation Tests

#### Automated

- [x] 3.1 Schema validation test passes: `clj -M:test -n com.apriary.services.product-csv-test` — 9b586ac
- [x] 3.2 Test verifies all three metric enum values pass Malli validation — 9b586ac
- [x] 3.3 Test verifies quantity constraint matches CSV validator — 9b586ac
- [x] 3.4 Negative test: invalid metric fails Malli validation — 9b586ac

#### Manual

- [x] 3.5 Review test covers both positive and negative paths — 9b586ac

### Phase 4: Update Test-Plan Cookbook

#### Automated

- [x] 4.1 File diff shows §6.1, §6.2, §6.3, §6.5 filled in

#### Manual

- [ ] 4.2 Review cookbook entries for clarity and verify file references
