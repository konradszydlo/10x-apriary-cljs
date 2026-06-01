<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Product Rankings Implementation Plan

- **Plan**: context/changes/product-rankings/plan.md
- **Mode**: Deep
- **Date**: 2026-06-01
- **Verdict**: SOUND (after fixes)
- **Findings**: 1 critical, 1 warning

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS (after fix) |
| Plan Completeness | PASS (after fix) |

## Grounding
3/3 paths ✓, 1/1 symbol ✓, brief↔plan ✓

## Findings

### F1 — Mixed metrics summed incorrectly

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Phase 1 — Rankings Service, lines 116-125
- **Detail**: Plan groups by (hive-number, product-type) but schema allows different metrics for same product. Example: hive A-01 has "5 kg Honey" + "2000 g Honey" → query sums to 2005, mixing units. Schema (schema.clj:58) allows `:metric [:enum "kg" "ml" "g"]` for any product with no constraint preventing same product in multiple metrics from same hive.
- **Fix A ⭐ Recommended**: Group by (hive, product, metric)
  - Strength: Mathematically correct — only sums same units. Handles edge case if users record same product in different metrics.
  - Tradeoff: Hive A-01 could appear twice for "Honey" if it has both kg and g entries. Rankings show "A-01 • 15 kg" and "A-01 • 2000 g" separately.
  - Confidence: HIGH — this is how aggregation works everywhere (sum by dimension including unit).
  - Blind spot: UI mockup doesn't show how to label split entries.
- **Fix B**: Add schema constraint: one metric per product-type
  - Strength: Simplifies aggregation. "Honey is always kg" encoded in data model.
  - Tradeoff: Requires schema migration. Breaks if users legitimately use multiple metrics.
  - Confidence: MEDIUM — unclear if this pattern exists in schema.
  - Blind spot: PRD doesn't specify whether metric is tied to product-type.
- **Decision**: FIXED via Fix A — Updated lines 116-125 to group by (hive, product, metric) and added note about same hive appearing multiple times with different metrics.

### F2 — XTDB aggregation syntax unverified

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — Rankings Service, lines 116-119
- **Detail**: Plan assumes XTDB Datalog aggregation syntax: `(sum ?quantity)` grouped by `:find` tuple. But codebase has ZERO examples of XTDB aggregation — all existing queries use simple `:find '[?p]` pattern. Existing pattern (product.clj:101-105) fetches entity IDs, then pulls full entities. One case (generation.clj:344-350) does grouping/counting in Clojure post-query, not in XTDB Datalog. Project uses Biff v1.9.1 which bundles XTDB 1.24, but no aggregation examples found in codebase.
- **Fix**: Add XTDB aggregation syntax verification step to Phase 1
  - Strength: Explicit verification before full implementation. If syntax differs, catch it at start vs mid-phase. Can fall back to Clojure-side aggregation (proven pattern in codebase).
  - Tradeoff: Adds a research/spike task before main work. Minimal time cost (~15 min to test query in REPL).
  - Confidence: HIGH — XTDB 1.24 docs are authoritative; verification is straightforward.
  - Blind spot: None significant — REPL test will confirm or deny.
- **Decision**: FIXED — Added critical implementation note at line 242 to verify XTDB aggregation syntax in REPL before full implementation, with fallback to Clojure-side aggregation if needed.
