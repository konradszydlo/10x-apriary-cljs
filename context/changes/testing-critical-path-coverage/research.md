---
date: 2026-06-03T23:59:04+02:00
researcher: Claude (Sonnet 4.5)
git_commit: b7d7fa057f5f9b6275e73ab4dab450d20e85f1a6
branch: master
repository: 10x-apriary-cljs
topic: "Ground rollout Phase 1 - Critical-path coverage for CSV import and rankings"
tags: [research, codebase, testing, product-csv, rankings, risk-verification]
status: complete
last_updated: 2026-06-03
last_updated_by: Claude (Sonnet 4.5)
---

# Research: Ground rollout Phase 1 - Critical-path coverage for CSV import and rankings

**Date**: 2026-06-03T23:59:04+02:00  
**Researcher**: Claude (Sonnet 4.5)  
**Git Commit**: b7d7fa057f5f9b6275e73ab4dab450d20e85f1a6  
**Branch**: master  
**Repository**: 10x-apriary-cljs

## Research Question

Ground rollout Phase 1 of `context/foundation/test-plan.md` by verifying the real failure paths in code for risks #1, #2, #5, and #6. For each risk, locate the actual code that could fail, verify or correct the response guidance from the test plan, identify existing tests, and determine the cheapest useful test layer.

**Risks to verify:**
- **Risk #1**: Silent CSV import failure — user enters production data, sees success feedback, but data doesn't persist to XTDB
- **Risk #2**: Frontend-backend contract drift — FE sends product record with wrong shape, BE rejects or stores corrupted data
- **Risk #5**: Ranking calculation incorrect — top/bottom 5 hives show wrong totals or wrong hive ordering
- **Risk #6**: Invalid CSV row accepted — malformed data gets stored and corrupts rankings

## Summary

All four risks are **CONFIRMED** with grounded failure paths identified. Key findings:

1. **Risk #1 (Silent failure) — CRITICAL CONFIRMED**: `xt/submit-tx` return value is ignored at `product.clj:70`, handler always returns success even if XTDB write fails. **No Malli validation** exists at handler or service boundaries. This is a system-wide pattern affecting summaries and generations too.

2. **Risk #2 (Contract drift) — CONFIRMED**: No Malli validation at handler boundary (`products.clj:52`), no schema enforcement before XTDB write (`product.clj:52-63`). CSV validator and Malli schema are dual sources of truth with no enforcement that they stay in sync.

3. **Risk #5 (Ranking calculation) — PARTIALLY CONFIRMED**: Aggregation is straightforward but has **complex non-obvious behaviors**: tie-breaking is undefined (relies on XTDB result set order), zero-quantity records are included, same hive can appear twice if using different metrics. **No tests exist** for ranking service.

4. **Risk #6 (Invalid row handling) — PARTIALLY COVERED**: Field-level validation exists and is tested, rejected rows are surfaced to user, but **no handler-level tests** verify the full flow, and edge cases (100% rejection, malformed-but-parseable CSV) are untested.

**Hot-spot evidence assessment**: All cited files (`product_csv.clj`, `product.clj`, `products.clj`, `product_rankings.clj`) are correct failure owners. No misleading evidence.

**Test infrastructure is mature**: Existing test suite uses `cognitect.test-runner` with in-memory XTDB via `test-xtdb-node`, RLS tests are systematic, fixture patterns are consistent.

## Detailed Findings

### Risk #1: Silent CSV Import Failure

#### Failure Path (CONFIRMED)

**Handler entry:** `src/com/apriary/pages/products.clj:42-145` (`import-products-handler`)
- Route: `POST /api/products-import`
- Accepts `csv` parameter (raw CSV string)
- Returns htmx fragments with OOB swaps

**CSV parsing:** `src/com/apriary/services/csv_import.clj:19-66` (`parse-csv-string`)
- **SHARED** with summaries feature (same parser for both)
- Returns `[:ok {:headers [...] :rows [...]}]` or `[:error {...}]`

**Validation:** `src/com/apriary/services/product_csv.clj:151-250` (`process-product-csv`)
- Field-level validation (hive_number, date, product, quantity, metric)
- Returns `[:ok {:valid-rows [...] :rejected-rows [...]}]` or `[:error {...}]`
- **CRITICAL**: No Malli schema validation — pure function guards only

**XTDB write:** `src/com/apriary/services/product.clj:22-84` (`create-products-batch`)
```clojure
;; Line 70 - CRITICAL SILENT FAILURE PATH
(xt/submit-tx node tx-ops)

;; Line 76 - Always returns success
[:ok {:count (count products)}]
```

**Silent failure scenario:**
1. Service ignores `xt/submit-tx` return value (line 70)
2. Service always returns `[:ok {:count N}]` (line 76)
3. Handler proceeds to success path (line 111)
4. `xt/sync` succeeds (line 113)
5. `list-products` returns empty or stale data (lines 114-116)
6. User sees success toast "Successfully imported N products" (lines 130-131)
7. Table shows no new products — **no error message**

**Comparison with summaries:** In `src/com/apriary/pages/summaries_view.clj`, Malli validation IS enforced at handler level via `m/explain`, but product CSV import has NO such validation.

#### Response Guidance Verification

**Test plan said:**
- "Prove CSV import round-trip: paste valid CSV → submit → query XTDB directly → verify records exist with correct user-id"
- "Challenge: '200 response means data was stored'"
- "Avoid: happy-path-only, mocking XTDB write without proving persistence"

**Verdict: CORRECT**. The response guidance is accurate — the cheapest test that catches this is an integration test with in-memory XTDB that:
1. Calls the handler or service with valid CSV
2. Calls `xt/sync node`
3. Queries XTDB directly with `xt/q` or `xt/entity`
4. Asserts records exist with correct `:product/user-id`

**Existing test coverage:**
- `test/com/apriary/services/product_test.clj:15-55` tests `create-products-batch` with in-memory XTDB
- Tests verify records exist via `list-products` query
- **GAP**: No tests verify `xt/submit-tx` transaction acceptance
- **GAP**: No tests for schema violations at XTDB level
- **GAP**: No handler-level tests for `import-products-handler`

#### Cheapest Test Layer

**Integration test** with in-memory XTDB is the cheapest layer that gives real signal. Unit tests cannot catch this failure because the bug is at the boundary (ignoring `xt/submit-tx` return value).

**Recommended test location:** `test/com/apriary/services/product_test.clj` or new `test/com/apriary/pages/products_test.clj` for handler-level tests.

---

### Risk #2: Frontend-Backend Contract Drift

#### Failure Path (CONFIRMED)

**FE POST construction:** `src/com/apriary/ui/products.clj:39-43`
```clojure
[:form {:hx-post "/api/products-import"
        :hx-target "#products-table"
        :hx-swap "outerHTML"}
 [:textarea#csv-input {:name "csv" :required true ...}]]
```
- Frontend sends single field: `csv` (raw CSV string)
- Uses htmx for AJAX POST with HTML fragment response

**BE Malli schema:** `src/com/apriary/schema.clj:49-60`
```clojure
:product [:map {:closed true}
          [:xt/id :uuid]
          [:product/id :uuid]
          [:product/user-id :uuid]
          [:product/hive-number :string]
          [:product/date [:maybe :string]]
          [:product/product :string]
          [:product/quantity [:int {:min 1}]]
          [:product/metric [:enum "kg" "ml" "g"]]
          [:product/created-at inst?]
          [:product/updated-at inst?]]
```

**Request validation:** `src/com/apriary/pages/products.clj:52-68`
```clojure
(defn import-products-handler
  [{:keys [session biff.xtdb/node params] :as _ctx}]
  (let [csv-input (:csv params)]
    (if (or (nil? csv-input) (empty? csv-input))
      ;; 400 error
      {:status 400 ...}
      ;; No Malli validation - proceeds to CSV parsing
      (let [[parse-status parse-result] (csv-import/parse-csv-string csv-input)]
        ...))))
```

**Contract mismatch risks identified:**

1. **HIGH RISK (handler boundary):** No Malli validation of incoming request params — only manual `(or (nil? csv-input) (empty? csv-input))` guard.
   
2. **MEDIUM RISK (DB write):** No Malli validation before XTDB write at `product.clj:52-63` — entities constructed directly from validated CSV data without running Malli validation against `:product` schema.

3. **LOW RISK (dual sources of truth):** CSV validator (`product_csv.clj`) and Malli schema define overlapping constraints but no enforcement that they stay in sync:
   - CSV validator: `metric` must be `"kg"`, `"ml"`, or `"g"` (case-sensitive)
   - Malli schema: `[:enum "kg" "ml" "g"]`
   - CSV validator: `quantity` must be `> 0`
   - Malli schema: `[:int {:min 1}]`

4. **LOW RISK (response contract):** Response is htmx HTML fragments with OOB swaps — no schema enforcement for response structure. FE relies on implicit target IDs (`#rejected-rows`, `#toast-container`, `#csv-form`).

#### Response Guidance Verification

**Test plan said:**
- "Prove FE request payload matches BE Malli schema AND BE response matches FE rendering"
- "Challenge: 'schema validation passes means contract is correct'"
- "Avoid: copying FE request from BE code, validating schema without checking FE sends it"

**Verdict: MOSTLY CORRECT** but needs refinement. The FE doesn't send structured JSON — it sends raw CSV text. The contract drift risk is **not** FE/BE schema mismatch, but rather:
1. No schema validation at handler boundary (params shape)
2. No schema validation before XTDB write (entity shape)
3. Dual validation logic (CSV validator vs Malli schema) can drift

**Existing test coverage:**
- **ZERO** contract tests
- **ZERO** handler-level tests
- CSV parsing tests exist (`csv_import_test.clj`) but don't verify handler behavior
- Product service tests exist (`product_test.clj`) but don't verify Malli validation

#### Cheapest Test Layer

**Integration test** or **contract test**:
1. Call handler with htmx POST (simulated ring request with `{:params {:csv "..."}`)
2. Verify response structure (status 200, htmx OOB swap targets present)
3. Verify entities written to XTDB match Malli schema

**Alternative (unit test for schema drift):** Test that CSV validator output always passes Malli validation:
```clojure
(deftest csv-validator-matches-schema-test
  (let [csv-row {:hive-number "A-01" :date "23-11-2025" :product "Honey" :quantity 5 :metric "kg"}
        entity (construct-entity csv-row user-id)]
    (is (nil? (m/explain product-schema entity)))))
```

**Recommended test location:** New `test/com/apriary/pages/products_test.clj` for handler integration tests.

---

### Risk #5: Ranking Calculation Incorrect

#### Failure Path (PARTIALLY CONFIRMED)

**Ranking function:** `src/com/apriary/services/product_rankings.clj:7-96` (`calculate-rankings`)

**XTDB aggregation query** (lines 42-49):
```clojure
{:find '[?hive-number ?product-type ?metric (sum ?quantity) (count ?product-id)]
 :in '[user-id]
 :where [['?p :product/user-id 'user-id]
         ['?p :product/id '?product-id]
         ['?p :product/hive-number '?hive-number]
         ['?p :product/product '?product-type]
         ['?p :product/metric '?metric]
         ['?p :product/quantity '?quantity]]}
```

**Grouping:** Implicit grouping by `(?hive-number, ?product-type, ?metric)` tuple via `:find` clause.

**Critical design note (line 41 comment):** "Grouping by metric prevents mixing units (e.g., kg + g)". Same hive can appear multiple times in results if it has the same product in different metrics (e.g., "A-01 • 15 kg" and "A-01 • 2000 g" as separate ranking entries).

**Sorting & ranking** (lines 64-75):
1. Group by product type: `(group-by :product-type entries)`
2. Sort by `:total-quantity` descending: `(sort-by :total-quantity > entries-for-product)`
3. Calculate `actual-n = (min n (count sorted))`
4. Top N: `(take actual-n sorted)`
5. Bottom N: `(take actual-n (reverse sorted))`

**Edge case handling:**

| Edge Case | Handling | Evidence |
|-----------|----------|----------|
| **Fewer than 5 hives** | `actual-n = (min n (count sorted))` ensures we take only available hives. UI adapts labels dynamically. | Lines 70-72 in service; lines 43-46 in ui/rankings.clj |
| **Tie-breaking** | **NOT HANDLED**. Clojure's `sort-by` is stable, so ties preserve original query result order (XTDB result set order is undefined). No secondary sort key. | No tie-breaking logic found |
| **Zero-quantity records** | **NOT FILTERED**. Query sums all quantities including zeros. A hive with 0 total can rank in bottom 5. | No `:where` clause filtering `?quantity > 0` |
| **Multiple product types** | Handled correctly. Each product type gets independent top/bottom rankings. | Lines 64-75 |
| **Multiple metrics (kg/g)** | Each metric creates separate ranking entry. Hive "A-01" with 15kg and 2000g of Honey appears TWICE in rankings (by design to prevent unit mixing). | Line 41 comment |

#### Response Guidance Verification

**Test plan said:**
- "Prove rankings show correct top/bottom 5 for known dataset"
- "Challenge: 'aggregation is obvious' — verify tie-breaking, zero-quantity, <5 hives, multi-product"
- "Avoid: asserting current output, checking only happy path"

**Verdict: CORRECT and the challenge is VALIDATED**. The assumption "aggregation is obvious" is **partially wrong**:
1. Unit mixing prevention (grouping by metric) creates surprising behavior — same hive appears twice
2. Tie-breaking is undefined (relies on XTDB result set order)
3. Zero-sum inclusion (not filtered)
4. Multi-step Clojure processing required (not just SQL-style aggregation)

**Existing test coverage:**
- **ZERO TESTS** for `product_rankings` service
- No `product_rankings_test.clj` file exists
- Implementation review notes manual browser verification but no automated tests

#### Cheapest Test Layer

**Unit test** for pure ranking logic OR **integration test** with in-memory XTDB:

**Unit test approach:**
```clojure
(deftest calculate-rankings-tie-breaking-test
  (with-open [node (test-xtdb-node [])]
    ;; Insert products with same totals for two hives
    ;; Verify rankings are deterministic (same order every run)
    ))
```

**Integration test approach** (preferred because it also tests XTDB aggregation):
```clojure
(deftest calculate-rankings-edge-cases-test
  (with-open [node (test-xtdb-node [])]
    (testing "fewer than 5 hives"
      ;; Create 3 hives
      ;; Verify top/bottom both return 3, not 5
      )
    (testing "zero-quantity hive"
      ;; Create hive with 0 total
      ;; Verify it appears in bottom rankings
      )
    (testing "tie scenario"
      ;; Create two hives with identical totals
      ;; Verify both appear (order may vary but both present)
      )))
```

**Recommended test location:** New `test/com/apriary/services/product_rankings_test.clj`.

---

### Risk #6: Invalid CSV Row Accepted

#### Failure Path (PARTIALLY COVERED)

**Validation logic:** `src/com/apriary/services/product_csv.clj:74-149` (`validate-product-row`)

**Field-level validation:**
- `hive_number`: required, non-empty after trim (lines 113-114)
- `product`: required, non-empty after trim (lines 116-117)
- `date`: optional (nil if blank), must match `^\d{2}-\d{2}-\d{4}$` if provided (lines 29-36)
- `quantity`: required, must parse as integer > 0 (lines 38-56)
- `metric`: required, must be exactly `"kg"`, `"ml"`, or `"g"` (case-sensitive) (lines 58-68)

**Row-level return:** `[:valid {...}]` or `[:invalid "reason string"]`

**Batch processing:** `src/com/apriary/services/product_csv.clj:151-250` (`process-product-csv`)
- Returns `[:ok {:valid-rows [...] :rejected-rows [...] :rows-submitted N :rows-valid N :rows-rejected N}]`
- Rejected rows include row number and reason

**Handler flow:** `src/com/apriary/pages/products.clj:94-95`
```clojure
valid-rows (:valid-rows validation-result)
rejected-rows (:rejected-rows validation-result)
```

**User feedback:** Lines 133 — rejected rows rendered via htmx OOB swap to `#rejected-rows`.

**Existing test coverage:**
- `test/com/apriary/services/product_csv_test.clj:5-128` covers row validation (valid rows, missing fields, invalid date, quantity validation, metric enum) and batch processing (missing columns, mixed valid/invalid rows)
- **GAP**: No handler-level tests verify rejected rows actually reach user
- **GAP**: No tests for edge cases: 100% rejection rate, malformed-but-parseable CSV, very large CSV files

#### Response Guidance Verification

**Test plan said:**
- "Prove invalid CSV rows rejected with clear errors, valid rows still process"
- "Challenge: 'Malli catches everything' — verify field-level validation, partial-batch handling"
- "Avoid: checking schema without parse edge cases, not verifying partial-batch"

**Verdict: CORRECT**. The challenge is accurate — Malli does NOT validate at this layer. Validation is pure Clojure guard clauses, not schema-driven.

**Gaps identified:**
1. No tests for handler-level invalid row rendering
2. No tests for CSV with ALL invalid rows (100% rejection)
3. No tests for very large CSV files (memory/timeout issues)
4. No tests for malformed CSV that passes parser but fails validation

#### Cheapest Test Layer

**Unit test** for CSV validation (already exists) + **integration test** for handler-level rejected row rendering:

```clojure
(deftest import-products-rejected-rows-test
  (with-open [node (test-xtdb-node [])]
    (let [user-id (java.util.UUID/randomUUID)
          csv "hive_number;date;product;quantity;metric\nA-01;INVALID-DATE;Honey;5;kg\nA-02;23-11-2025;Pollen;-1;ml"
          ctx {:session {:uid user-id}
               :biff.xtdb/node node
               :params {:csv csv}}
          response (import-products-handler ctx)]
      
      ;; Verify response contains rejected rows
      (is (= (:status response) 200))
      (is (str/includes? (:body response) "INVALID-DATE"))
      (is (str/includes? (:body response) "must be greater than 0")))))
```

**Recommended test location:** New `test/com/apriary/pages/products_test.clj`.

---

### Test Infrastructure Analysis

**Test runner:** `cognitect.test-runner` (v0.5.1)
- Configured in `deps.edn:31-33` with `:test` alias
- Invocation: `clj -M:test`
- Namespace pattern: `*-test.clj` suffix

**Directory structure:**
```
test/com/apriary/
├── services/           (service layer tests with in-memory XTDB)
├── pages/              (handler integration tests)
├── auth/               (auth tests)
└── ui/                 (UI component tests, no database)
```

**In-memory XTDB pattern:** `(with-open [node (test-xtdb-node [])] ...)`
- Provided by Biff framework (`com.biffweb/test-xtdb-node`)
- Creates in-memory XTDB instance, auto-cleanup via `with-open`
- Write operations: pass `node` to service functions
- Read operations: pass `(xt/db node)` to query functions
- Synchronization: `(xt/sync node)` before reads

**Fixture pattern:** Function-based helpers (no global fixtures)
```clojure
(defn create-test-summary [node user-id & {:keys [content ...]}]
  (summary-service/create-manual-summary node user-id {...}))
```

**RLS test pattern:** Systematically tested across services
```clojure
(deftest list-products-rls-test
  (with-open [node (test-xtdb-node [])]
    (let [user1 (UUID.) user2 (UUID.)
          _ (create-test-product node user1 {...})
          _ (create-test-product node user2 {...})
          _ (xt/sync node)
          db (xt/db node)
          [_ result1] (product-service/list-products db user1)
          [_ result2] (product-service/list-products db user2)]
      (is (= (count (:products result1)) N1))
      (is (= (count (:products result2)) N2))
      (is (every? #(= (:product/user-id %) user1) (:products result1))))))
```

---

## Code References

### CSV Import Flow (Risk #1, #6)
- `src/com/apriary/pages/products.clj:42-145` — Handler entry point (`import-products-handler`)
- `src/com/apriary/services/csv_import.clj:19-66` — CSV parser (shared with summaries)
- `src/com/apriary/services/product_csv.clj:151-250` — Product CSV validation (`process-product-csv`)
- `src/com/apriary/services/product.clj:22-84` — XTDB write (`create-products-batch`)
- `src/com/apriary/services/product.clj:70` — **CRITICAL**: `xt/submit-tx` return value ignored

### FE-BE Contract (Risk #2)
- `src/com/apriary/ui/products.clj:39-43` — FE POST construction (htmx form)
- `src/com/apriary/schema.clj:49-60` — Malli schema definition (`:product`)
- `src/com/apriary/pages/products.clj:52-68` — Request validation (manual guard clause, no Malli)
- `src/com/apriary/services/product_csv.clj:74-149` — Domain validation (`validate-product-row`)

### Ranking Logic (Risk #5)
- `src/com/apriary/services/product_rankings.clj:7-96` — Ranking function (`calculate-rankings`)
- `src/com/apriary/services/product_rankings.clj:42-49` — XTDB aggregation query
- `src/com/apriary/services/product_rankings.clj:64-75` — Sorting & top/bottom selection

### Existing Tests
- `test/com/apriary/services/product_test.clj:15-55` — Product service tests (XTDB write, RLS)
- `test/com/apriary/services/product_csv_test.clj:5-128` — CSV validation tests
- `test/com/apriary/services/csv_import_test.clj` — CSV parser tests
- **MISSING**: `test/com/apriary/services/product_rankings_test.clj` — No ranking tests
- **MISSING**: `test/com/apriary/pages/products_test.clj` — No handler integration tests

### Test Infrastructure
- `deps.edn:31-33` — Test runner config (`:test` alias)
- `test/com/apriary/services/summary_test.clj:135-152` — RLS test pattern reference
- `test/com/apriary/pages/generations_test.clj:8-16` — Handler context builder pattern (`make-ctx`)

---

## Architecture Insights

1. **CSV parsing is shared**: `csv_import.clj` is used by both products and summaries features. Any bug in the base parser affects both flows. This validates the test plan's Risk #3 (cross-feature regression).

2. **No Malli enforcement at runtime**: Malli schemas exist in `schema.clj` but are NOT validated at handler or service boundaries for product CSV import. This is a system-wide pattern (summaries and generations also ignore `xt/submit-tx` return values).

3. **Dual validation layers**: CSV validator (pure Clojure guards) and Malli schema are two sources of truth for constraints. They overlap but no mechanism enforces they stay in sync.

4. **RLS is consistent**: All queries filter by `:product/user-id`, user-id is attached to all entities, and RLS tests are systematic across services.

5. **Ranking design choice**: Grouping by metric (kg/g/ml) prevents unit mixing but creates surprising UX where same hive appears multiple times. This is intentional per line 41 comment but not documented in user-facing docs.

6. **Test infrastructure is mature**: Existing test suite demonstrates strong patterns (in-memory XTDB, RLS tests, fixture helpers). New tests should follow these patterns.

---

## Related Research

No prior research artifacts found in `context/changes/**/research.md` or `context/archive/**/research.md` for this codebase.

---

## Open Questions

1. **System-wide `xt/submit-tx` issue**: Should all services validate transaction acceptance, or is the current "optimistic write" pattern acceptable for MVP scale? (Affects summaries, generations, products equally.)

2. **Malli at boundary vs domain validation**: Should we add Malli validation at handler/service boundaries, or rely on pure Clojure domain validation? Trade-off: schema-driven validation vs explicit business rules.

3. **Tie-breaking in rankings**: Should tie scenarios be deterministic (add secondary sort by hive-number) or is current undefined behavior acceptable? User impact: same data could show different rankings on each page load.

4. **100% rejection edge case**: What should UI show if all CSV rows are invalid? Current code may not handle this gracefully (success toast with "0 products" + rejected rows alert).

5. **Performance at scale**: Current ranking query loads ALL user products without pagination. Documented as acceptable for "5-50 hives, 100-500 records" but no load tests verify this assumption.
