# Cross-Feature Regression Test Implementation Plan

## Overview

Add integration tests to protect summaries CSV import against breakage from products-focused changes to the shared `parse-csv-string` layer (Risk #3 from test-plan.md). Research confirmed both features share CSV parsing infrastructure at Layer 1 while maintaining isolated domain validation at Layer 2.

## Current State Analysis

**From research.md:**

The codebase uses a two-layer CSV processing architecture:
- **Layer 1 (SHARED):** `csv_import/parse-csv-string` (csv_import.clj:19-66) — both features call this
- **Layer 2 (ISOLATED):** Domain-specific validators in separate modules

**Coupling Points (where changes break both):**
- `csv_import.clj:41` — semicolon delimiter
- `csv_import.clj:57` — header lowercasing (case-insensitive matching)
- `csv_import.clj:36-51` — empty CSV guards

**Isolation Points (where changes affect only one):**
- `csv_import.clj:92-154` — `validate-csv-row` (summaries only)
- `product_csv.clj:74-149` — `validate-product-row` (products only)

**Test Coverage Gaps Identified:**
1. No summaries CSV service tests exist (products has `product_csv_test.clj`)
2. No schema-drift test for summaries (products has this at product_csv_test.clj:132-251)
3. No cross-feature test verifying both imports work sequentially

**Existing Test Patterns:**
- Products tests: `test/com/apriary/pages/products_test.clj` and `test/com/apriary/services/product_csv_test.clj`
- Follow Biff `test-xtdb-node` pattern for integration tests
- Use `[status result]` destructuring for service returns
- RLS verification: `(is (every? #(= (:*/user-id %) expected-user) records))`

## Desired End State

Three new test namespaces protecting summaries CSV import:

1. **`test/com/apriary/services/csv_import_test.clj`** — Unit tests for `process-csv-import` (parse + validate)
2. **Schema drift test** added to above file — prevents CSV validator / Malli schema divergence  
3. **Cross-feature test** in existing `products_test.clj` or new `csv_cross_feature_test.clj` — sequential import (products → summaries)

**Verification:**
- `clj -M:test -n com.apriary.services.csv-import-test` passes
- `clj -M:test -n com.apriary.pages.products-test` passes (if cross-feature added there)
- test-plan.md §6.4 "Adding a test for shared CSV parsing" filled in with cookbook entry

## What We're NOT Doing

- Full handler integration tests for summaries (would require mocking AI generation)
- Refactoring duplicated utility functions (`find-column-index`, `validate-date` in `product_csv.clj`)
- Adding summaries handler tests (out of scope — focus is cross-feature protection)
- Testing UI/htmx rendering (test-plan.md §7 explicitly excludes FE tests in CI)

## Implementation Approach

**Strategy:** Follow the existing products test pattern exactly.

Products has two test files:
- Service tests: `product_csv_test.clj` (validator logic)
- Handler tests: `products_test.clj` (integration with XTDB)

We'll mirror this for summaries, then add a cross-feature test.

**Key Decision:** User chose **"Parse + validate only"** (not full handler flow with AI). This means:
- Test `csv-import/process-csv-import` directly (service-level)
- Skip the summaries handler (which calls AI generation)
- Simpler, faster test with no AI mocking required

**Fixtures (user selected ALL options):**
- Valid observation (50+ chars)
- Optional fields populated
- Case-insensitive headers (e.g., "Observation", "HIVE_NUMBER")
- Empty optional field (tests nil handling)

## Phase 1: Summaries CSV Service Tests

### Overview

Create `test/com/apriary/services/csv_import_test.clj` to test `process-csv-import` function (parse + validate pipeline for summaries).

### Changes Required:

#### 1. New Test Namespace

**File**: `test/com/apriary/services/csv_import_test.clj`

**Intent**: Test summaries CSV processing service-level logic (parsing, validation, rejection handling) without invoking the handler or AI generation. Mirrors the structure of `product_csv_test.clj`.

**Contract**: New namespace with `deftest` functions. Requires:
- `com.apriary.services.csv-import :as sut`
- `clojure.test :refer [deftest is testing]`

Tests to include:
1. **Valid CSV with all fields** — observation (50+ chars), hive_number, observation_date, special_feature
2. **Valid CSV with minimal fields** — observation only, optional fields empty
3. **Case-insensitive headers** — "Observation", "HIVE_NUMBER", "observation_DATE"
4. **Missing required column** — no observation column present
5. **Observation too short** — < 50 characters
6. **Observation too long** — > 10,000 characters
7. **Invalid date format** — date present but not DD-MM-YYYY
8. **Mixed valid/invalid rows** — verify separation into valid-rows and rejected-rows
9. **Empty optional fields** — observation valid, hive_number/date/special_feature blank

**Verification approach (user selected):**
- Valid rows returned with correct field values
- Rejected rows empty for valid CSV
- Field values (observation, hive-number, date) match input

### Success Criteria:

#### Automated Verification:

- Tests pass: `clj -M:test -n com.apriary.services.csv-import-test`
- Coverage includes all edge cases: short/long observation, invalid date, missing columns
- Follows existing test pattern from `product_csv_test.clj`

#### Manual Verification:

- Review test output confirms clear failure messages
- Each test has descriptive docstring referencing behavior tested

## Phase 2: Summaries Schema Drift Test

### Overview

Add schema-drift prevention test to `csv_import_test.clj` — verifies CSV validator output matches `:summary` Malli schema. Closes the gap research identified (products has this, summaries doesn't).

### Changes Required:

#### 1. Schema Drift Test

**File**: `test/com/apriary/services/csv_import_test.clj` (add to Phase 1 file)

**Intent**: Prevent CSV validator and Malli schema from diverging over time. Verify that CSV validator output can be transformed into a valid `:summary` entity.

**Contract**: Test function `csv-validator-matches-schema-test` following the pattern from `product_csv_test.clj:132-251`. Requires importing:
- `malli.core :as m`
- `com.apriary.schema :as schema`

**Critical transformation** (CSV validator → schema fields):
- `:observation` → `:summary/content`
- `:hive-number` → `:summary/hive-number`
- `:observation-date` → `:summary/observation-date`
- `:special-feature` → `:summary/special-feature`

Test structure:
- Construct CSV row output: `{:observation "..." :hive-number "A-01" :observation-date "23-11-2025" :special-feature "Queen active"}`
- Transform to entity (as handler does): `{:summary/content (:observation csv-row) :summary/hive-number (:hive-number csv-row) ...}`
- Positive path: entity passes Malli validation `(is (nil? (m/explain summary-schema entity)))`
- Negative path: invalid field → Malli validation fails (explain returns non-nil)
- Edge case: minimum observation length (50 chars) passes, 49 chars fails
- Edge case: maximum observation length (10,000 chars) passes, 10,001 fails

### Success Criteria:

#### Automated Verification:

- Schema drift test passes: verify `(is (nil? (m/explain summary-schema entity)))`
- Negative tests fail correctly: verify `(is (some? (m/explain summary-schema entity)))`
- Coverage includes: min/max observation length, optional field nil handling

#### Manual Verification:

- Review test structure matches products schema test pattern
- Confirm Malli error paths reference correct schema fields (`:summary/content`, etc.)

## Phase 3: Cross-Feature Integration Test

### Overview

Add integration test that imports products CSV, then summaries CSV, and verifies both persist correctly. Proves the shared `parse-csv-string` layer works for both features after changes.

### Changes Required:

#### 1. Cross-Feature Test Function

**File**: `test/com/apriary/pages/products_test.clj` (add to existing file)

**Intent**: Test that products CSV import followed by summaries CSV import both succeed, proving the shared parsing layer is intact and domain-specific validation remains isolated.

**Contract**: Integration test function `import-products-then-summaries-test`. Uses:
- `test-xtdb-node` for in-memory XTDB
- Products handler: `products/import-products-handler`
- CSV service directly: `csv-service/process-csv-import` (not summaries handler)

Test flow:
1. Create two user-ids (or use one — both patterns are valid)
2. Import products CSV: call `products/import-products-handler` with valid product CSV
3. Verify products persisted: query XTDB for `:product` entities
4. Import summaries CSV: call `csv-service/process-csv-import` with valid summaries CSV
5. Verify summaries parsing succeeded: check `[:ok {:valid-rows [...]}]` return
6. Verify both datasets isolated: products in XTDB, summaries validated correctly

**CSV Fixtures:**
- Products: `hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg`
- Summaries: `observation;hive_number;observation_date;special_feature\nThis is a detailed hive inspection observation with sufficient length for validation;A-01;23-11-2025;Queen active`

**Note on summaries verification:** Since we're testing `process-csv-import` (not the handler), we verify the *parsing* succeeded and returned valid rows. We do NOT persist summaries to XTDB in this test — that would require calling the handler with AI mocking.

### Success Criteria:

#### Automated Verification:

- Products import succeeds: status 200, XTDB contains product records
- Summaries parsing succeeds: `[:ok {:valid-rows [...] :rejected-rows []}]`
- Products XTDB query returns expected records
- Summaries valid-rows contain expected observation data
- Test passes: `clj -M:test -n com.apriary.pages.products-test` (if added there)

#### Manual Verification:

- Review test proves both features work sequentially (not just one)
- Confirm test would catch shared-layer breakage (e.g., delimiter change)
- Verify test does NOT depend on AI generation (uses service directly)

## Phase 4: Update Test-Plan Cookbook

### Overview

Fill in test-plan.md §6.4 "Adding a test for shared CSV parsing" with the pattern shipped in Phases 1-3.

### Changes Required:

#### 1. Cookbook Entry

**File**: `context/foundation/test-plan.md`

**Intent**: Document the cross-feature testing pattern so future contributors know how to verify shared CSV parsing logic changes don't break existing features.

**Contract**: Replace "TBD — see §3 Phase 2" with:

```markdown
### 6.4 Adding a test for shared CSV parsing

When changing `csv_import.clj` (shared CSV parsing layer), verify both products and summaries still work.

**Pattern:**
```clojure
(deftest import-products-then-summaries-test
  "Verify shared parse-csv-string layer works for both features"
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          
          ;; Step 1: Import products
          product-csv "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg"
          products-ctx (make-ctx node user-id :params {:csv product-csv})
          products-response (products/import-products-handler products-ctx)]
      
      ;; Verify products persisted
      (is (= (:status products-response) 200))
      (xt/sync node)
      (let [db (xt/db node)
            products (xt/q db '{:find [(pull ?p [*])] :in [user-id] 
                                :where [[?p :product/user-id user-id]]} user-id)]
        (is (= (count products) 1)))
      
      ;; Step 2: Import summaries (service-level, not handler)
      (let [summary-csv "observation;hive_number;observation_date\nThis is a detailed hive inspection observation with sufficient length for validation;A-01;23-11-2025"
            [status result] (csv-service/process-csv-import summary-csv)]
        
        ;; Verify summaries parsing succeeded
        (is (= :ok status))
        (is (= 1 (:rows-valid result)))
        (is (= 0 (:rows-rejected result)))
        (is (= "This is a detailed hive inspection observation with sufficient length for validation" 
               (:observation (first (:valid-rows result)))))))))
```

**Key principles:**
- Test both features sequentially (not just one)
- Use service-level call for summaries (avoids AI mocking)
- Verify XTDB persistence for products, parsing success for summaries
- Catches shared-layer breakage (delimiter, header processing, guards)

See `test/com/apriary/pages/products_test.clj` for full example.
```

### Success Criteria:

#### Automated Verification:

- Cookbook entry renders correctly in markdown
- Code example is syntactically valid Clojure
- Pattern references match actual test file paths

#### Manual Verification:

- Review cookbook entry is clear and actionable
- Verify it explains WHEN to use this pattern (changing shared CSV layer)
- Confirm example code matches what Phase 3 implemented

---

## Testing Strategy

### Unit Tests:

**File:** `test/com/apriary/services/csv_import_test.clj`

- `process-csv-import` with various fixtures:
  - Valid all fields
  - Valid minimal (observation only)
  - Case-insensitive headers
  - Invalid: missing observation column
  - Invalid: observation too short/long
  - Invalid: bad date format
  - Mixed valid/invalid rows

**Schema Drift:**
- CSV row → summary entity → Malli validation
- Positive: valid fields pass
- Negative: invalid fields fail
- Edge cases: min/max observation length

### Integration Tests:

**File:** `test/com/apriary/pages/products_test.clj` (or new cross-feature test file)

- Sequential import: products → summaries
- Verify products in XTDB
- Verify summaries parsing succeeded (service-level)

### Manual Testing Steps:

1. Run full test suite: `clj -M:test`
2. Verify all new tests pass
3. Check test output for clear failure messages
4. Review cookbook entry makes sense to a future contributor

## Performance Considerations

- In-memory XTDB nodes are fast (~ms setup)
- Service-level tests (no XTDB) are faster than integration tests
- No AI calls in tests (parsing only) — fast execution

## Migration Notes

Not applicable — adding new tests, no data migration.

## References

- Research: `context/changes/testing-cross-feature-regression/research.md`
- Test-plan risk: `context/foundation/test-plan.md` §2 Risk #3
- Existing products tests: `test/com/apriary/services/product_csv_test.clj`, `test/com/apriary/pages/products_test.clj`
- Shared CSV parser: `src/com/apriary/services/csv_import.clj:19-66`
- Summaries validator: `src/com/apriary/services/csv_import.clj:92-154`
- Summary schema: `src/com/apriary/schema.clj:25-37`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Summaries CSV Service Tests

#### Automated

- [x] 1.1 Tests pass: `clj -M:test -n com.apriary.services.csv-import-test` — a2b278d
- [x] 1.2 Coverage includes all edge cases: short/long observation, invalid date, missing columns — a2b278d
- [x] 1.3 Follows existing test pattern from `product_csv_test.clj` — a2b278d

#### Manual

- [ ] 1.4 Review test output confirms clear failure messages
- [ ] 1.5 Each test has descriptive docstring referencing behavior tested

### Phase 2: Summaries Schema Drift Test

#### Automated

- [x] 2.1 Schema drift test passes: verify `(is (nil? (m/explain summary-schema entity)))` — 5609526
- [x] 2.2 Negative tests fail correctly: verify `(is (some? (m/explain summary-schema entity)))` — 5609526
- [x] 2.3 Coverage includes: min/max observation length, optional field nil handling — 5609526

#### Manual

- [ ] 2.4 Review test structure matches products schema test pattern
- [ ] 2.5 Confirm Malli error paths reference correct schema fields (`:summary/content`, etc.)

### Phase 3: Cross-Feature Integration Test

#### Automated

- [x] 3.1 Products import succeeds: status 200, XTDB contains product records — 63ee231
- [x] 3.2 Summaries parsing succeeds: `[:ok {:valid-rows [...] :rejected-rows []}]` — 63ee231
- [x] 3.3 Products XTDB query returns expected records — 63ee231
- [x] 3.4 Summaries valid-rows contain expected observation data — 63ee231
- [x] 3.5 Test passes: `clj -M:test -n com.apriary.pages.products-test` (if added there) — 63ee231

#### Manual

- [ ] 3.6 Review test proves both features work sequentially (not just one)
- [ ] 3.7 Confirm test would catch shared-layer breakage (e.g., delimiter change)
- [ ] 3.8 Verify test does NOT depend on AI generation (uses service directly)

### Phase 4: Update Test-Plan Cookbook

#### Automated

- [x] 4.1 Cookbook entry renders correctly in markdown
- [x] 4.2 Code example is syntactically valid Clojure
- [x] 4.3 Pattern references match actual test file paths

#### Manual

- [ ] 4.4 Review cookbook entry is clear and actionable
- [ ] 4.5 Verify it explains WHEN to use this pattern (changing shared CSV layer)
- [ ] 4.6 Confirm example code matches what Phase 3 implemented
