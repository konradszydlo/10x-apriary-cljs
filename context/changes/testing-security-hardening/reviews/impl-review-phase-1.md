<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Security Hardening Tests

- **Plan**: context/changes/testing-security-hardening/plan.md
- **Scope**: Phase 1 of 4
- **Date**: 2026-06-05
- **Verdict**: APPROVED (after fixes)
- **Findings**: 1 critical, 1 warning (all fixed)

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Missing database-level RLS verification

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: test/com/apriary/pages/rankings_test.clj:51-64
- **Detail**: Test verifies RLS by checking hive numbers in rendered HTML but does NOT verify RLS at the database query level. Reference tests (products_test.clj:70, generations_test.clj:104) use `every?` to verify ALL database records have correct user-id. Current test could pass even if database query returns wrong data but rendering filters it out.
- **Fix**: Add database-level RLS verification after handler calls (query XTDB directly and verify all records have correct user-id using `every?` pattern from products_test.clj:70).
  - Strength: Catches RLS bugs at the query layer where they matter most. Matches the §6.3 pattern cited in manual verification. Reference tests prove this pattern works.
  - Tradeoff: Adds ~10 lines to test. Need to query XTDB directly after calling handler.
  - Confidence: HIGH — identical pattern used in products_test.clj:70, 144 and generations_test.clj:104-105.
  - Blind spot: None significant — pattern is well-established.
- **Decision**: FIXED — Added database-level `every?` assertions verifying all products belong to correct user

### F2 — RLS assertion pattern diverges from plan

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: test/com/apriary/pages/rankings_test.clj:51-64
- **Detail**: Plan's manual verification says "Review test output to confirm RLS assertion logic matches §6.3 pattern (using `every?` to verify all records have correct user-id)" but implementation uses HTML string matching (`str/includes?`) instead of structural `every?` checks.
- **Fix**: Document the HTML-assertion pattern in Phase 4 cookbook update as an alternative for page handlers returning HTML. Add note that handler-level tests can verify via HTML while service-level tests should use `every?` on data structures.
- **Decision**: FIXED — Added note to Phase 4 plan documenting dual assertion strategy (HTML + database-level)
