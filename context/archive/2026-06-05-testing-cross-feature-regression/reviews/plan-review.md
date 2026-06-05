<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Cross-Feature Regression Test

- **Plan**: context/changes/testing-cross-feature-regression/plan.md
- **Mode**: Deep
- **Date**: 2026-06-05
- **Verdict**: SOUND (after fixes)
- **Findings**: 1 critical, 2 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING (1 finding) |
| Plan Completeness | WARNING (2 findings) |

## Grounding
5/5 paths ✓, 3/3 symbols ✓, brief↔plan ✓

## Findings

### F1 — Schema drift test missing field transformation

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 — Schema Drift Test
- **Detail**: Plan said "verify that a CSV row processed by `validate-csv-row` produces data compatible with the `:summary` schema" but there's a field name transformation required: CSV validator returns `:observation` (un-namespaced) while schema expects `:summary/content` (namespaced, different name). The test must construct the entity WITH the transformation (like products does at product_csv_test.clj:145-154) — not test direct compatibility.
- **Fix A ⭐ Recommended**: Update Phase 2 description + add transformation to test contract
  - Strength: Matches products test pattern exactly; makes the transformation explicit so implementer knows to include it.
  - Tradeoff: Slightly more verbose test description.
  - Confidence: HIGH — products test shows this exact pattern working.
  - Blind spot: None significant.
- **Fix B**: Leave as-is, trust implementer to figure it out
  - Strength: Shorter plan; implementer can reference products test.
  - Tradeoff: Critical detail left implicit; easy to miss the :observation → :summary/content rename.
  - Confidence: LOW — the rename is non-obvious without reading handler code.
  - Blind spot: If implementer misses this, test will be wrong.
- **Decision**: FIXED (via Fix A)

### F2 — Missing test file location decision

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 — Cross-Feature Integration Test
- **Detail**: Phase 3 line 170 said "add to existing file OR new csv_cross_feature_test.clj" but didn't guide the implementer on which to choose. Phase 4 cookbook references `test/com/apriary/pages/products_test.clj` suggesting that's the expected location, but Phase 3 left it ambiguous.
- **Fix**: Recommend adding to `products_test.clj` and remove the OR clause. The cross-feature test logically belongs with products tests since it imports products first.
- **Decision**: FIXED

### F3 — Cookbook example truncates observation fixture

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4 — Cookbook Entry
- **Detail**: Line 251 showed `summary-csv "observation;hive_number;observation_date\nDetailed inspection notes...;A-01;23-11-2025"` where "Detailed inspection notes..." is only ~30 chars but the plan's own Phase 1 (line 99) says observation must be ≥50 chars to pass validation. The cookbook example would fail if copied verbatim.
- **Fix**: Expand the observation text to 50+ chars in the cookbook example.
- **Decision**: FIXED
