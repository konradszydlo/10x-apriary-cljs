<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Security Hardening Tests Implementation Plan

- **Plan**: context/changes/testing-security-hardening/plan.md
- **Mode**: Deep
- **Date**: 2026-06-05
- **Verdict**: SOUND (after fixes)
- **Findings**: 1 critical, 2 warnings, 1 observation (all fixed)

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | PASS |
| Plan Completeness | PASS (after fixes) |

## Grounding
3/5 paths ✓ (2 new test files expected), 4/4 symbols ✓, brief↔plan ✓

## Findings

### F1 — §6.3 already exists, cannot add

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Plan Completeness
- **Location**: Phase 4, line 154-159
- **Detail**: Plan says "Add §6.3 subsection documenting the pattern for testing RLS on aggregation/ranking endpoints" but test-plan.md §6.3 already exists at lines 171-213, titled "Adding a multi-user RLS test". The existing section documents RLS for CRUD operations (list-products) using the `every?` assertion pattern. Phase 4 would conflict with existing content.
- **Fix A ⭐ Recommended**: Update §6.3 to add rankings-RLS subsection
  - Strength: Preserves existing CRUD-RLS documentation while adding the new rankings pattern; §6.3 becomes comprehensive RLS guide. Matches how §6.2 has multiple subsections.
  - Tradeoff: §6.3 grows longer; implementer reads both CRUD and rankings patterns (but that's the point).
  - Confidence: HIGH — test-plan.md:171-213 shows §6.3 exists; adding a subsection is backward-compatible.
  - Blind spot: Need to verify §6.3's current structure supports adding a subsection vs a sibling heading.
- **Fix B**: Renumber and add §6.7 for rankings-RLS
  - Strength: Clean separation — CRUD-RLS stays in §6.3, rankings-RLS gets its own top-level section.
  - Tradeoff: Fragments related RLS patterns across two sections; §6.6 (XSS) sits between two RLS sections creating odd flow.
  - Confidence: MEDIUM — works structurally but creates conceptual split.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A

### F2 — Outdated characterization of rankings test

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Current State Analysis, line 11
- **Detail**: Plan says "Rankings service has single-user unit test" but test/com/apriary/services/product_rankings_test.clj:158-188 is actually a multi-user RLS test creating user-a and user-b and verifying isolation. This understates existing coverage.
- **Fix**: Change line 11 to "Rankings service has multi-user RLS test at service layer but no handler-level integration test."
- **Decision**: FIXED

### F3 — Phase 4 scope unclear on update vs add

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Completeness
- **Location**: Phase 4, lines 154-176
- **Detail**: Phase 4 says "Add §6.3 subsection" (line 158) and "Add §6.6 subsection" (line 169), but only §6.6 is truly missing. The "Intent" fields don't clarify whether §6.3 means append-to-existing or create-new, leaving implementer guessing.
- **Fix A ⭐ Recommended**: Split into two changes with clear verbs
  - Strength: Unambiguous — "Update §6.3 to add rankings subsection" vs "Add new §6.6 for XSS patterns". Implementer knows exactly which sections to modify vs create.
  - Tradeoff: Slightly more verbose.
  - Confidence: HIGH — existing plan structure supports this edit.
  - Blind spot: None significant.
- **Fix B**: Keep as-is, add clarifying note
  - Strength: Minimal edit — just append "(update existing)" and "(new section)" annotations.
  - Tradeoff: Weaker signal than fixing the verb; still requires reading Intent carefully.
  - Confidence: HIGH — works but less clear than Fix A.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A (addressed in F1 fix)

### F4 — XSS assertion brittleness

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 & 3 Contract descriptions
- **Detail**: Phase 2 line 99 and Phase 3 line 129 assert the HTML response contains escaped form `&lt;script&gt;`. This string-matching approach is brittle to whitespace or formatting changes in Rum's HTML output (e.g., if Rum starts outputting `&lt; script &gt;`).
- **Fix**: Use regex `#"&lt;\s*script\s*&gt;"` instead of exact string match to tolerate whitespace variations, or assert absence of raw `<script` as primary check and escaped form as secondary.
- **Decision**: FIXED (primary check changed to absence of raw tag, with optional regex-based escaped form verification)
