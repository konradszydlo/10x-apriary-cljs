<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Product Input and View

- **Plan**: context/changes/product-input-view/plan.md
- **Scope**: All Phases (1-4)
- **Date**: 2026-06-01
- **Verdict**: APPROVED (after fixes)
- **Findings**: 1 critical, 4 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS (after fix) |
| Architecture | PASS |
| Pattern Consistency | PASS (after fixes) |
| Success Criteria | PASS (after fix) |

## Findings

### F1 — Missing sync error handling in import handler

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/com/apriary/pages/products.clj:112-114
- **Detail**: Import handler called `(xt/sync node)` then immediately `(xt/db node)` without checking sync success. If sync fails, db reflects stale state and response shows outdated products to user, creating false feedback ("imported" but not visible).
- **Fix**: Wrap sync/db sequence in error handling
  - Strength: Prevents false-positive feedback. Matches defensive pattern in other critical paths. User gets honest error instead of misleading "success" toast with stale table.
  - Tradeoff: Adds 3-5 lines of try-catch. Minimal.
  - Confidence: HIGH — sync failure is rare but catastrophic for UX.
  - Blind spot: None significant.
- **Decision**: FIXED — Added try-catch around sync/db sequence with 500 error response on failure

### F2 — Manual test checklist file missing

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/product-input-view/ (missing file)
- **Detail**: Plan Phase 4 specified creating manual-test-checklist.md documenting test steps. File doesn't exist. Progress shows manual tests passed (all checkboxes ticked), so testing was performed but not documented.
- **Fix**: Create the checklist file as planned, or acknowledge it was completed verbally
  - Strength: Future developers have repeatable test procedure. Matches plan contract.
  - Tradeoff: Post-hoc documentation less valuable than pre-testing checklist.
  - Confidence: HIGH — straightforward to write from plan + progress notes.
  - Blind spot: None.
- **Decision**: FIXED — Created context/changes/product-input-view/manual-test-checklist.md with all test cases from plan

### F3 — Sorting logic in UI layer instead of service

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/com/apriary/ui/products.clj:80
- **Detail**: Products table sorts via `(sort-by :product/date #(compare %2 %1) products)` inline in UI component. Baseline pattern (summaries) applies sorting in service layer (list-summaries returns pre-sorted data), keeping business logic out of UI.
- **Fix**: Move sorting to product.clj list-products function
  - Strength: Matches summary pattern. Business logic in service layer.
  - Tradeoff: One-line move. No functional impact.
  - Confidence: HIGH — summaries/product symmetry is explicit design goal per plan.
  - Blind spot: None.
- **Decision**: FIXED — Moved sorting to src/com/apriary/services/product.clj list-products function, removed from UI component

### F4 — Incomplete CRUD symmetry with summaries

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: src/com/apriary/services/product.clj
- **Detail**: Summary service provides full CRUD (create, list, get-by-id, update, delete). Product service only has create-batch and list. Breaks pattern symmetry.
- **Fix A ⭐ Recommended**: Document intentional scope limit in service docstring
  - Strength: If edit/delete deferred to S-03 per roadmap, explicit note prevents future confusion. Low effort.
  - Tradeoff: Doesn't add operations, just clarifies intent.
  - Confidence: HIGH — plan's "What We're NOT Doing" lists edit/delete as S-03.
  - Blind spot: None.
- **Fix B**: Implement get-by-id, update, delete stubs
  - Strength: Full CRUD symmetry with summaries.
  - Tradeoff: Scope creep — not in this change's plan. Better as separate change.
  - Confidence: MEDIUM — may conflict with S-03 approach.
  - Blind spot: S-03 design not yet planned.
- **Decision**: FIXED via Fix A — Added docstring clarifying intentional scope limit (edit/delete deferred to S-03)

### F5 — Unused bindings (linting warnings)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: Multiple files
- **Detail**: clj-kondo reports 4 unused bindings: products.clj:26 `status`, products.clj:52 `ctx`, products.clj:114 `list-status`, product_csv_test.clj:6 `headers`
- **Fix**: Replace unused bindings with `_`
  - Strength: Silences warnings. Explicit intent (underscore = intentionally ignored).
  - Tradeoff: Cosmetic — doesn't affect runtime.
  - Confidence: HIGH — standard Clojure idiom for unused destructured values.
  - Blind spot: None.
- **Decision**: FIXED — Replaced all 4 unused bindings with `_`

### F6 — Inline row-number calculation in hot loop

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/com/apriary/services/product_csv.clj:218-221
- **Detail**: `(+ idx 2)` computed twice per row in map-indexed (once for :row-number, once for error message construction). Minor DRY violation.
- **Fix**: Compute once: `(let [row-num (+ idx 2)] {:row-number row-num ...})`
- **Decision**: FIXED — Refactored to compute row-num once per iteration
