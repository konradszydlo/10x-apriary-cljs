<!-- PLAN-REVIEW-REPORT -->
# Plan Review: AI Toolkit Package Implementation Plan

- **Plan**: `context/changes/ai-toolkit/plan.md`
- **Mode**: Deep
- **Date**: 2026-06-21
- **Verdict**: SOUND (after fixes)
- **Findings**: [1 critical] [3 warnings] [1 observation] - all fixed during triage

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS ✅ |
| Lean Execution | PASS ✅ |
| Architectural Fitness | PASS ✅ (after F3 fix) |
| Blind Spots | PASS ✅ (after F1, F2 fixes) |
| Plan Completeness | PASS ✅ (after F4, F5 fixes) |

## Grounding
4/5 paths ✓ (`packages/ai-toolkit/` doesn't exist yet - expected), 2/3 symbols ✓ (`packages:write` is in wrong location in workflow), brief↔plan ✓

## Findings

### F1 — Error tuple pattern not universal

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Phase 2 — Code Review Skill
- **Detail**: Plan states error tuple pattern `[:ok val]` / `[:error map]` is "used throughout codebase" (line 39) but verification shows 3 services use exception-throwing (`product_rankings.clj:32`, `csv_import.clj:37`, `product.clj:45`). Code-review skill will flag legitimate patterns as violations.
- **Fix A ⭐ Recommended**: Document both patterns in code-review skill
  - Strength: Reflects actual codebase — services layer uses tuples, some utilities use exceptions; skill becomes accurate.
  - Tradeoff: Slightly more complex skill (two error patterns to check).
  - Confidence: HIGH — verification identified exact files using each pattern.
  - Blind spot: None significant.
- **Fix B**: Mark exception-throwing files for refactoring
  - Strength: Enforces single pattern; eventually unifies codebase.
  - Tradeoff: Requires refactoring 3 services before v0.1.0 ships or skill will incorrectly flag them; delays package delivery.
  - Confidence: MEDIUM — refactoring scope not estimated.
  - Blind spot: UI layer also uses unwrapped error maps — full unification is larger than 3 files.
- **Decision**: FIXED via Fix A — Updated Error Handling category to document both patterns (tuples in services, exceptions in utilities)

### F2 — Biff module pattern incomplete

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 — Biff Patterns Skill
- **Detail**: Plan documents module pattern as `{:routes [...]}` with optional `:api-routes` (line 263) but codebase has 3 edge cases: `{:schema schema}` (`schema.clj:62`), empty `{}` (`summaries.clj:387`), and api-only modules with no `:routes` (`generations.clj:119`). Skill documentation is incomplete.
- **Fix**: Add "Module Variations" subsection to biff-patterns skill documenting the 3 edge cases with file:line references. Explain when each is used (`:schema` for schema modules, `{}` when routes delegated to views, api-only for pure API modules).
  - Strength: Complete documentation; implementers won't be surprised.
  - Tradeoff: 2-3 extra paragraphs in skill.
  - Confidence: HIGH — variations already identified with file references.
  - Blind spot: None significant.
- **Decision**: FIXED — Added Module Variations subsection to biff-patterns skill spec

### F3 — Workflow permissions location mismatch

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Phase 3 — GitHub Actions Workflow
- **Detail**: Plan says permissions are at `.github/workflows/master-docker.yml:39-42` (line 41, 371) but actual permissions are nested under `jobs.build-and-push` (lines 39-42 within that job), not at workflow level. The npm publish workflow needs workflow-level permissions for both validate and publish jobs.
- **Fix**: Use workflow-level permissions (outside jobs block) so both validate and publish jobs inherit them. Pattern: lines 1-10 of new workflow, not nested under each job.
- **Decision**: FIXED — Clarified that permissions go at workflow level, not job level (with note about master-docker.yml difference)

### F4 — No npm ci dependencies in Phase 1

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — Package Metadata
- **Detail**: package.json (line 100-111) specifies no dependencies, but Phase 3 workflow runs `npm ci` (line 381, 389). With zero dependencies, `npm ci` will succeed trivially but adds no value. Either add test dependencies (e.g., node:test needs no package) or remove `npm ci` from workflow.
- **Fix**: Remove `npm ci` from Phase 3 workflow steps since package has no dependencies. Validate/publish jobs can skip it — installers are dependency-free Node scripts.
- **Decision**: FIXED — Removed `npm ci` from workflow steps, added `cd packages/ai-toolkit &&` prefix to npm commands

### F5 — README bin entry not in package.json

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1 — Installation Guide
- **Detail**: README documents manual commands `npx @konradszydlo/ai-toolkit install` (line 153) but package.json contract (lines 104-111) doesn't include `bin` entry. Manual invocation will fail.
- **Fix**: Add `"bin": {"ai-toolkit": "./install.js"}` to package.json or remove manual command documentation from README if not needed for v0.1.0.
- **Decision**: FIXED — Added `bin` entry to package.json contract
