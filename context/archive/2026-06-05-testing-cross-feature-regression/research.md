---
date: 2026-06-05T11:23:00+02:00
researcher: Claude Sonnet 4.5
git_commit: 087d94e04b93d7f102eed07bbcd8763c13948841
branch: master
repository: 10x-apriary-cljs
topic: "CSV parsing shared logic between summaries and products features"
tags: [research, codebase, csv-parsing, cross-feature-regression, risk-3]
status: complete
last_updated: 2026-06-05
last_updated_by: Claude Sonnet 4.5
---

# Research: CSV Parsing Shared Logic & Cross-Feature Regression Risk

**Date**: 2026-06-05T11:23:00+02:00  
**Researcher**: Claude Sonnet 4.5  
**Git Commit**: 087d94e04b93d7f102eed07bbcd8763c13948841  
**Branch**: master  
**Repository**: 10x-apriary-cljs

## Research Question

**From test-plan.md Risk #3:**
"CSV parsing regression breaks existing summaries feature — shared parsing logic change silently breaks summaries import"

**Must Challenge (from Risk Response Guidance):**
"Shared parsing logic is obvious" — verify both use same parser or prove isolation

**Context to Ground:**
- Where CSV parsing lives
- How summaries import works
- How products import works
- Fixtures for both features

## Executive Summary

### Finding: CONFIRMED SHARED PARSING LAYER WITH DOMAIN-SPECIFIC VALIDATION

The risk is **REAL** but **NUANCED**. The codebase uses a two-layer architecture:

1. **Layer 1 (SHARED):** Base CSV parsing in `csv_import.clj` — both features call `parse-csv-string`
2. **Layer 2 (ISOLATED):** Domain-specific validation — separate functions per feature

**Key Risk Points:**
- **Shared delimiter change** would break both features simultaneously
- **Shared header parsing logic** change could affect case-sensitivity, trimming, or validation
- **Domain validation is isolated** — a change to products validation won't break summaries

**Protection Strategy:**
The cheapest test is an **integration test** that imports summaries CSV after products changes land, verifying:
1. Summaries CSV still parses correctly (shared layer intact)
2. Summaries validation still works (domain layer untouched)
3. XTDB persistence succeeds (end-to-end proof)

**Surprising Discovery:**
Products feature duplicates two utility functions (`find-column-index`, `validate-date`) instead of importing from the shared module. This creates **isolated but divergent implementations** — a third risk vector not covered by the test-plan assumption.

## Detailed Findings

### 1. Shared CSV Parsing Infrastructure

**File:** `src/com/apriary/services/csv_import.clj`

This module contains the shared CSV parsing foundation used by **both** summaries and products.

#### 1.1 Base Parser: `parse-csv-string`

**Location:** `csv_import.clj:19-66`

**Function Signature:**
```clojure
(defn parse-csv-string [csv-string]
  ;; Returns [:ok {:headers [...] :rows [...]}]
  ;;     or [:error {:code "..." :message "..."}]
  )
```

**What It Does:**
- Parses semicolon-delimited CSV using `clojure.data.csv/read-csv`
- **Critical Line 41:** `(csv/read-csv reader :separator \;)` — hardcoded semicolon delimiter
- Lowercases headers for case-insensitive matching (line 57)
- Validates: non-empty CSV, headers present, at least one data row

**Used By:**
- Products handler: `pages/products.clj:68`
- Summaries handler: `pages/summaries_view.clj:506` (via `process-csv-import`)

**Risk Vector:**
Any change to this function (delimiter, header processing, row parsing) affects BOTH features.

#### 1.2 Summaries-Specific Pipeline: `process-csv-import`

**Location:** `csv_import.clj:156-227`

**What It Does:**
1. Calls `parse-csv-string` (line 177)
2. Validates `observation` column exists (lines 180-186)
3. Validates each row with `validate-csv-row` (line 198)
4. Separates valid/rejected rows (lines 204-216)

**Used By:**
- Summaries handler only (NOT used by products)

**Isolation:**
This is summaries-specific domain logic. Products has its own equivalent: `product_csv/process-product-csv`.

### 2. Products CSV Flow (Separate but Coupled at Layer 1)

**Handler:** `src/com/apriary/pages/products.clj:42-145`

**Flow:**
1. **Line 68:** Calls shared `csv-import/parse-csv-string` (SHARED)
2. **Line 81:** Calls `product-csv/process-product-csv` (ISOLATED)
3. **Line 97:** Calls `product-service/create-products-batch` (ISOLATED)

**Domain Validation:** `src/com/apriary/services/product_csv.clj`

**Location:** `product_csv.clj:74-149` (`validate-product-row`)

**Required Columns:**
- `hive_number`, `date`, `product`, `quantity`, `metric`

**Validation Rules:**
- `quantity` must be integer > 0 (lines 38-56)
- `metric` must be exactly "kg", "ml", or "g" (case-sensitive, lines 58-68)
- `date` optional but must match DD-MM-YYYY if provided (lines 29-36)

### 3. Summaries CSV Flow (Uses Both Shared and Dedicated Logic)

**Handler:** `src/com/apriary/pages/summaries_view.clj:461-630`

**Flow:**
1. **Line 506:** Calls `csv-service/process-csv-import` (wraps shared parser)
2. **Line 531:** Generates AI summaries from valid rows
3. **Line 545:** Creates generation record
4. **Lines 562-575:** Builds summary entities
5. **Line 577:** Persists to XTDB

**Domain Validation:** `src/com/apriary/services/csv_import.clj:92-154`

**Required Columns:**
- `observation` only

**Validation Rules:**
- `observation`: 50-10,000 characters after trim (lines 130-138)
- `observation_date`, `hive_number`, `special_feature` all optional

### 4. CSV Format Comparison

| Aspect | Summaries | Products |
|--------|-----------|----------|
| **Delimiter** | Semicolon (`;`) — shared | Semicolon (`;`) — shared |
| **Parser** | `csv-import/parse-csv-string` | `csv-import/parse-csv-string` |
| **Domain Validator** | `csv-import/validate-csv-row` | `product-csv/validate-product-row` |
| **Required Fields** | `observation` only | `hive_number`, `product`, `quantity`, `metric` |
| **Optional Fields** | `hive_number`, `observation_date`, `special_feature` | `date` only |
| **Date Format** | DD-MM-YYYY (regex: `^\d{2}-\d{2}-\d{4}$`) | DD-MM-YYYY (same regex) |
| **Content Constraints** | 50-10,000 chars | Quantity > 0, metric enum |

### 5. Duplicated Utility Functions (Hidden Coupling)

**Discovery:** Products feature duplicates two functions instead of importing from shared module.

#### 5.1 `find-column-index` — Duplicated

**Shared Version:** `csv_import.clj:72-81`
**Products Version:** `product_csv.clj:18-27`

**Comparison:**
- **Same implementation** (line-by-line identical)
- Both are private (`defn-`)
- Products version is NOT imported — it's a copy

**Risk:**
If the shared version gets a bug fix (e.g., better whitespace handling, Unicode normalization), products won't inherit it unless manually synced.

#### 5.2 `validate-date` / `validate-observation-date` — Similar but Separate

**Shared Version:** `csv_import.clj:83-90` (`validate-observation-date`)
**Products Version:** `product_csv.clj:29-36` (`validate-date`)

**Comparison:**
- Same regex: `^\d{2}-\d{2}-\d{4}$`
- Same error handling
- Different function names
- Both private

**Risk:**
If date format changes (e.g., support YYYY-MM-DD), both must be updated independently.

### 6. Test Coverage Analysis

#### Products Tests

**File:** `test/com/apriary/services/product_csv_test.clj`

**Coverage:**
- Line 132-251: **Schema drift prevention test** — verifies CSV validator output matches Malli `:product` schema
  - Tests all three metric values ("kg", "ml", "g")
  - Verifies quantity constraint (> 0) matches schema `[:int {:min 1}]`
- Lines 7-85: Field-level validation (quantity, metric, date format)
- Lines 87-130: Missing columns, mixed valid/invalid rows

**File:** `test/com/apriary/pages/products_test.clj`

**Coverage:**
- Lines 42-73: Round-trip test (CSV → XTDB query)
- Lines 75-92: All invalid rows (zero products stored)
- Lines 121-146: RLS test (user A cannot see user B's products)

#### Summaries Tests

**Gap Found:** No equivalent schema-drift test for summaries

**Existing Coverage:**
- No dedicated `csv_import_test.clj` file found
- Handler tests exist but no CSV parsing unit tests visible in research

### 7. Coupling Points (Where Changes Would Break Both)

| Location | What It Does | Blast Radius |
|----------|--------------|--------------|
| `csv_import.clj:41` | Semicolon delimiter | Both features break if changed |
| `csv_import.clj:57` | Header lowercasing | Both features break if removed |
| `csv_import.clj:36-51` | Empty CSV guards | Both features lose validation if removed |
| `clojure.data.csv` import | CSV library dependency | Both features break if library changes |

### 8. Isolation Points (Where Changes Affect Only One Feature)

| Location | What It Does | Scope |
|----------|--------------|-------|
| `csv_import.clj:92-154` | `validate-csv-row` | Summaries only |
| `product_csv.clj:74-149` | `validate-product-row` | Products only |
| `csv_import.clj:156-227` | `process-csv-import` | Summaries only |
| `product_csv.clj:151-250` | `process-product-csv` | Products only |

## Architecture Insights

### Two-Layer CSV Processing Pattern

The codebase implements a **composable validation pipeline**:

```
Layer 1: Generic CSV Parsing (SHARED)
    ↓
    csv-import/parse-csv-string
    - Semicolon delimiter
    - Header extraction
    - Row splitting
    ↓
Layer 2: Domain Validation (ISOLATED)
    ↓
    ┌─────────────────────┬─────────────────────┐
    ↓                     ↓                     ↓
Summaries:          Products:           Future Feature:
validate-csv-row    validate-product-row    ???
```

**Benefits:**
- Reusable CSV parsing
- Domain-specific validation rules
- Easy to add new CSV-based features

**Risks:**
- Shared layer changes affect all features
- No compile-time guarantee that domain validators use compatible schemas
- Duplicated utility functions can drift

### RLS (Row-Level Security) Pattern

Both features implement identical RLS:
- Every entity includes `:*/user-id` field
- Queries filter by `(= user-id (:uid session))`
- XTDB queries pass `user-id` as `:in` parameter

**Summaries:** `src/com/apriary/services/summary.clj:165-172`
**Products:** `src/com/apriary/services/product.clj:106-107`

## Code References

### Shared CSV Parsing
- `src/com/apriary/services/csv_import.clj:19-66` — `parse-csv-string` (base parser)
- `src/com/apriary/services/csv_import.clj:41` — Semicolon delimiter literal
- `src/com/apriary/services/csv_import.clj:72-81` — `find-column-index` (case-insensitive)

### Summaries CSV Flow
- `src/com/apriary/pages/summaries_view.clj:461-630` — HTTP handler
- `src/com/apriary/pages/summaries_view.clj:506` — Calls `process-csv-import`
- `src/com/apriary/services/csv_import.clj:156-227` — `process-csv-import` (full pipeline)
- `src/com/apriary/services/csv_import.clj:92-154` — `validate-csv-row` (domain rules)
- `src/com/apriary/ui/csv_import.clj:187-205` — UI form with htmx

### Products CSV Flow
- `src/com/apriary/pages/products.clj:42-145` — HTTP handler
- `src/com/apriary/pages/products.clj:68` — Calls shared `parse-csv-string`
- `src/com/apriary/pages/products.clj:81` — Calls `process-product-csv`
- `src/com/apriary/services/product_csv.clj:151-250` — `process-product-csv`
- `src/com/apriary/services/product_csv.clj:74-149` — `validate-product-row`
- `src/com/apriary/services/product.clj:22-84` — `create-products-batch` (XTDB write)

### Duplicated Functions
- `src/com/apriary/services/product_csv.clj:18-27` — `find-column-index` (duplicate)
- `src/com/apriary/services/product_csv.clj:29-36` — `validate-date` (similar to shared version)

### Test Coverage
- `test/com/apriary/services/product_csv_test.clj:132-251` — Schema drift prevention
- `test/com/apriary/pages/products_test.clj:42-73` — Round-trip persistence test
- `test/com/apriary/pages/products_test.clj:121-146` — RLS test

### Schemas
- `src/com/apriary/schema.clj:25-37` — `:summary` Malli schema
- `src/com/apriary/schema.clj:49-60` — `:product` Malli schema

## Historical Context

**From test-plan.md:**
- Risk #3 was raised during user interview Q3 + PRD §Guardrails line 88
- Hot-spot evidence: `src/com/apriary/services/product_csv.clj` (2 commits/30d)
- Impact: High (breaks existing feature silently)
- Likelihood: Medium-High (shared code + active development)

**PRD Context (prd.md lines 160-162):**
"**Existing integrations:** CSV parsing logic should be shared between Summaries and Products to avoid duplication. Both features use semicolon-delimited text input with header rows."

This confirms the **intentional sharing** of CSV parsing logic — the architecture is by design, not accidental coupling.

## Test Plan Corrections

### Risk Response Guidance — Verified & Refined

**Original Guidance (test-plan.md:41):**
> "Shared parsing logic is obvious" — verify both use same parser or prove isolation

**Research Verdict:**
✅ **VERIFIED** — Both features DO share `parse-csv-string` at Layer 1  
⚠️ **REFINED** — Domain validation is isolated; risk is **partial coupling**, not total

**Updated Context:**
- Shared layer: `csv_import.clj:19-66` (parse-csv-string)
- Isolated layer: Domain-specific validators in separate modules
- **Hidden coupling:** Duplicated utility functions in `product_csv.clj`

**Required Test Fixtures:**
- Summaries CSV: Must include `observation` field (50+ chars)
- Products CSV: Must include all five required columns
- Both: Use semicolon delimiter, DD-MM-YYYY date format

**Cheapest Test Layer:**
Integration test (in-memory XTDB + both handlers)

**Anti-Pattern to Avoid:**
Testing products parsing only — must verify summaries still works **after products changes**

### New Risk Discovered: Duplicated Utility Drift

**Risk:** `product_csv.clj` duplicates `find-column-index` and `validate-date` instead of importing from shared module.

**Impact:** Medium (silent divergence if shared version gets bug fixes)  
**Likelihood:** Medium (functions are private, no compile-time coupling)

**Mitigation Options:**
1. **Refactor:** Extract duplicated functions to shared utility module
2. **Accept:** Document the duplication and ensure test coverage catches drift
3. **Test:** Add cross-feature validation that both parse the same CSV identically

## Open Questions

### 1. Why are utility functions duplicated instead of shared?

**Hypothesis:** `find-column-index` and `validate-date` are private (`defn-`) in `csv_import.clj`, so `product_csv.clj` couldn't import them. Developer chose to duplicate rather than make them public.

**Next Step:** Check git history to see if duplication was intentional or accidental.

### 2. Should summaries have a schema-drift test?

**Gap:** Products has `csv-validator-matches-schema-test` (product_csv_test.clj:132-251), but summaries has no equivalent.

**Risk:** CSV validator could diverge from `:summary` Malli schema over time.

**Recommendation:** Add schema-drift test for summaries in Phase 2 or Phase 1 follow-up.

### 3. Could delimiter or date format change in the future?

**Current State:** Both features hardcode:
- Semicolon delimiter (`\;`)
- DD-MM-YYYY date format

**PRD Check:** No mention of internationalization or alternate formats.

**Verdict:** Acceptable for MVP. If date format needs to change, it's a breaking change requiring migration.

## Related Research

No prior research artifacts found in `context/changes/` or `context/archive/` related to CSV parsing, shared utilities, or cross-feature regression risks.

## Recommendations for Test Plan

### Phase 2: Cross-Feature Regression Guard

**Test Strategy:**
1. **Integration test** that imports summaries CSV after products CSV processing
2. Verify **both** features work end-to-end (not just one)
3. Use **real XTDB** (in-memory node) to catch schema drift
4. Include **edge cases** that exercise shared layer:
   - Case-insensitive headers
   - Empty optional fields
   - Mixed valid/invalid rows

**Fixture Requirements:**
- Summaries CSV with valid `observation` (50+ chars)
- Products CSV with valid `hive_number`, `product`, `quantity`, `metric`
- Both using semicolon delimiter

**Success Criteria:**
- Summaries import succeeds (proves shared parser intact)
- Products import succeeds (proves isolation works)
- XTDB queries return correct records for both

**Anti-Pattern to Avoid:**
- Mocking the shared parser (wouldn't catch delimiter changes)
- Testing only products (wouldn't catch summaries breakage)
- Asserting CSV parses without XTDB round-trip (wouldn't catch schema drift)

### Optional: Refactor Duplicated Utilities

**If time permits:**
1. Extract `find-column-index` and `validate-date` to shared module (e.g., `csv_utils.clj`)
2. Make them public
3. Update both `csv_import.clj` and `product_csv.clj` to import from shared module
4. Add unit tests for shared utilities

**Benefit:** Eliminates third risk vector (drift between duplicated functions)

**Cost:** Medium (refactor + update tests)

## Conclusion

**Risk #3 is CONFIRMED but NUANCED:**
- Shared parsing layer exists (`parse-csv-string`)
- Domain validation is isolated
- Test must verify **both** features work after changes

**Surprise Finding:**
Duplicated utility functions create a **third coupling point** not anticipated by the test plan.

**Next Step:**
Proceed to `/10x-plan` with this grounded understanding of where the code lives and what needs protection.
