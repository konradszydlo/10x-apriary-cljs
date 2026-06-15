# Summaries Module Quick Wins - Technical Debt Cleanup Implementation Plan

## Overview

Execute three quick-win refactorings (4-6 hours total) to clean up summaries module technical debt identified in `context/changes/summaries-refactor-opportunities/research.md`. All changes are low-risk with existing test coverage or zero blast radius. Focus on immediate value: remove 131 LOC dead code, prevent potential data loss from schema drift, eliminate schema duplication.

**Deferred to future work**: E2E test foundation and God Page split (C1) — requires 1.5-2 week investment, treated as separate change-id per research roadmap.

## Current State Analysis

Based on research findings from `context/changes/summaries-refactor-opportunities/research.md`:

### Key Discoveries:

- **Orphaned Schema (C2)**: `src/com/apriary/schema/api.clj` exists with 131 LOC, 10 well-structured schemas, but **zero imports** across entire codebase (verified via grep). Created Nov 27, 2025 as planned validation layer, then bypassed 6 hours later during rapid UI sprint. File survived 6-month dormancy unchanged.

- **Schema Drift (C3)**: Content max length inconsistent across layers:
  - `schema.clj:37` = **10,000 chars** (database schema)
  - `summaries_view.clj:346` = **50,000 chars** (frontend validation)
  - `services/summary.clj:37` = **50,000 chars** (service layer)
  - Mismatch creates **data loss risk** if XTDB enforces 10k limit strictly while frontend accepts 50k.
  - PRD specified 10k, implementation used 50k; partial fix attempt in Jun 2026 (commit 63ee231) only updated schema.clj.

- **Schema Duplication (C5)**: `create-manual-summary-schema` duplicated byte-for-byte in:
  - `schema/api.clj:26-37` (unused, part of orphaned file)
  - `summaries_view.clj:334-346` (actively used for form validation)
  - Only difference: docstring wording ("request body" vs "form data")
  - Root cause: Time-pressure copy during Nov 27 sprint instead of importing from api.clj

### Root Cause

All three issues trace to **Nov 27, 2025 rapid prototyping sprint** where 1,177 LOC were added in 6 hours across 6 commits. Accidental complexity from MVP time pressure, not deliberate trade-offs:
- Centralized schema layer planned (`schema/api.clj` created with proper structure)
- Under deadline, schemas duplicated inline for speed
- 5-month dormancy (Jan-Apr 2026) frosilized the rushed state
- Zero refactoring attempts visible in git history

### Existing Safeguards

- **C2 (orphan deletion)**: grep verification confirms zero imports
- **C3 (schema drift)**: Explicit test coverage in `test/com/apriary/services/csv_import_test.clj` (lines 99-108, 118-124, 385-420) — tests WILL BREAK if schema changes without test updates
- **C5 (duplication)**: Covered by C2 — deleting schema/api.clj removes one copy atomically
- **CI pipeline**: `.github/workflows/pull-request.yml` runs clj-kondo lint + unit tests + integration tests

## Desired End State

After this plan completes:

1. **File state**:
   - `src/com/apriary/schema/api.clj` — **DELETED** (131 LOC removed)
   - `src/com/apriary/schema.clj:37` — content max length = **50,000** (aligned with service layer)
   - `src/com/apriary/pages/summaries_view.clj:334-346` — single canonical `create-manual-summary-schema` (duplicate removed by C2)

2. **Test state**:
   - `test/com/apriary/services/csv_import_test.clj` — all hardcoded 10k limits updated to 50k
   - All tests GREEN in CI

3. **No user-facing changes**: All refactorings are internal; UI behavior unchanged

4. **Verification**:
   - `grep -r "schema\.api" src/ test/` returns zero results (excluding deleted file)
   - `grep ":max 10000" src/` returns zero results (schema drift eliminated)
   - CI passes (clj-kondo + unit/integration tests)

**Success from user perspective**: Codebase cleaner (131 LOC dead code removed), schema consistency established (50k limit everywhere), potential data loss prevented (XTDB schema matches frontend validation).

## What We're NOT Doing

**Explicitly out of scope** (per user decisions + research recommendations):

- **E2E test foundation**: Playwright/Cypress setup for browser-level testing — deferred to separate change-id (prerequisite for God Page split)
- **God Page split (C1)**: Splitting `summaries_view.clj` (1,274 LOC, 15 handlers) into 4 namespaces — **BLOCKED** until E2E coverage exists, estimated 1.5-2 week investment
- **Handler-level unit tests**: Writing tests for `create-manual-summary-api-handler` or other handlers — research notes this requires 105-150 mock setups, not justified for quick wins
- **Schema consolidation Option B**: Adopting `schema/api.clj` as centralized validation layer — chose YAGNI (only 1 of 8 pages has validation, Q2 products didn't add any)
- **Other 9 unused schemas**: If deleting `schema/api.clj`, no need to decide fate of `list-summaries-query-schema`, `bulk-accept-schema`, etc.
- **PRD alignment**: Not changing 50k limit back to PRD's 10k — would break existing data and service layer expectations

## Implementation Approach

**Strategy**: Sequential execution following research ranking (C2 → C3 → C5). Each phase is independently valuable and low-risk:

1. **Phase 1 (C2)**: Remove orphaned schema — zero blast radius, completely safe
2. **Phase 2 (C3)**: Fix schema drift — test coverage enforces correctness, prevents data loss
3. **Phase 3 (C5)**: Covered by Phase 1 — deleting `schema/api.clj` removes duplicate automatically; update summaries_view.clj to note it's now the canonical schema

**Rationale**:
- Start with zero-risk change (C2) to validate process
- Address highest-impact bug (C3 data loss) while we have confidence from Phase 1
- C5 requires no separate action (duplicate lives in deleted file)

**Testing strategy**: Rely on existing test suite + CI (no new tests needed per user decision):
- C2: grep verification (no imports exist)
- C3: Existing tests in `csv_import_test.clj` will fail if schema/test mismatch
- C5: No handler-level tests exist; CI catches import errors if missed

## Phase 1: Remove Orphaned Schema (C2)

### Overview

Delete `src/com/apriary/schema/api.clj` (131 LOC, 10 schemas, zero imports). This file was created Nov 27, 2025 as a planned centralized validation layer but was never imported — schemas were duplicated inline during the 6-hour UI sprint. Removing it eliminates confusion ("Should I use this?"), prevents future drift, and cleans up dead code.

### Changes Required:

#### 1. Verify Zero Imports

**File**: Entire `src/` and `test/` directories

**Intent**: Re-verify that no code references `schema.api` namespace before deletion. Research confirmed zero imports via grep (2026-06-14), but paranoia check ensures nothing changed since.

**Contract**: `grep -r "schema\.api\|schema/api" src/ test/` must return 0 results (excluding the file itself and documentation files).

```bash
# Verification command
grep -r "schema\.api\|schema/api" src/ test/ | grep -v "schema/api.clj"
# Expected output: empty (zero results)
```

#### 2. Delete Orphaned Schema File

**File**: `src/com/apriary/schema/api.clj`

**Intent**: Remove 131 LOC of unused schema definitions. This eliminates dead code and resolves schema duplication (C5) as a side effect — `create-manual-summary-schema` duplicate is deleted along with the file.

**Contract**: File must not exist after this change. Git will track the deletion; CI (clj-kondo) will catch any missed imports during lint phase.

```bash
rm src/com/apriary/schema/api.clj
```

#### 3. Update summaries_view.clj Comment

**File**: `src/com/apriary/pages/summaries_view.clj`

**Intent**: Clarify that the inline `create-manual-summary-schema` (lines 334-346) is now the canonical schema, not a temporary duplicate.

**Contract**: Update docstring at line 335 to reflect new status.

Current (line 335):
```clojure
"Schema for validating manual summary creation form data..."
```

Change to:
```clojure
"Canonical schema for validating manual summary creation form data.
Inline schema preferred over centralized validation layer (YAGNI: only 1 of 8 pages has validation as of Q2 2026)."
```

### Success Criteria:

#### Automated Verification:

- Schema file deletion verified: `test ! -f src/com/apriary/schema/api.clj` (exit 0)
- Zero import references: `! grep -r "schema\.api" src/ test/` (exit 0)
- Linting passes: `clojure -M:dev lint` or equivalent clj-kondo invocation
- Unit tests pass: `clojure -M:test`
- Integration tests pass: CI `.github/workflows/pull-request.yml` completes

#### Manual Verification:

- Git diff confirms only expected changes: deletion of `schema/api.clj` + docstring update in `summaries_view.clj`
- No unexpected files modified (check `git status`)

**Implementation Note**: After completing this phase and all automated verification passes, this is a safe change requiring no manual UI testing (zero user-facing impact). Proceed directly to Phase 2.

---

## Phase 2: Fix Schema Drift (C3)

### Overview

Align content max length to **50,000 characters** across all layers. Currently schema.clj specifies 10k while frontend and service layer accept 50k — this mismatch creates data loss risk if XTDB enforces the schema strictly. Change aligns to current service-layer behavior (50k has been in production since Nov 2025) rather than PRD's 10k (which would break existing data).

### Changes Required:

#### 1. Update Database Schema

**File**: `src/com/apriary/schema.clj`

**Intent**: Change content max length from 10,000 to 50,000 to match frontend validation and service layer checks. This prevents potential data loss from schema enforcement mismatch.

**Contract**: Line 37 `:summary/content` schema constraint.

Change line 37 from:
```clojure
[:summary/content [:string {:min 50 :max 10000}]]
```

To:
```clojure
[:summary/content [:string {:min 50 :max 50000}]]
```

#### 2. Update CSV Import Test - Long Observation Rejection

**File**: `test/com/apriary/services/csv_import_test.clj`

**Intent**: Update test expectations to reflect new 50k limit. Test verifies that observations exceeding the limit are rejected with appropriate error message.

**Contract**: Lines 99-108 test case "Observation too long (> 10,000 chars)" must use 50,001 char input and expect "50,000" in error message.

Change line 100 from:
```clojure
(repeat 10001 "x")
```

To:
```clojure
(repeat 50001 "x")
```

Change line 108 from:
```clojure
:message "Observation field is too long (max 10,000 characters)"}]
```

To:
```clojure
:message "Observation field is too long (max 50,000 characters)"}]
```

#### 3. Update CSV Import Test - Boundary Test

**File**: `test/com/apriary/services/csv_import_test.clj`

**Intent**: Update boundary test to verify that exactly 50,000 chars is accepted (not rejected).

**Contract**: Lines 118-124 test case must use 50,000 char input and expect SUCCESS.

Change line 119 from:
```clojure
(repeat 10000 "x")
```

To:
```clojure
(repeat 50000 "x")
```

Change line 124 comment (if exists) referencing 10,000 to reference 50,000.

#### 4. Update CSV Import Validator

**File**: `src/com/apriary/services/csv_import.clj`

**Intent**: Align CSV import hardcoded validation to 50,000-char limit to match manual entry path. Currently csv_import.clj:135-138 hardcodes 10,000 limit independently of schema layer — this creates inconsistent UX where manual summaries accept 50k but CSV rejects 10,001+.

**Contract**: Lines 95, 135, 138 — update hardcoded 10,000 checks and error messages.

Change line 95 comment from:
```clojure
;; observation field: required, 50-10,000 characters after trim
```

To:
```clojure
;; observation field: required, 50-50,000 characters after trim
```

Change line 135 from:
```clojure
(> (count trimmed-obs) 10000)
```

To:
```clojure
(> (count trimmed-obs) 50000)
```

Change line 138 from:
```clojure
{:error "Observation field is too long" :detail "Maximum: 10,000 characters"}
```

To:
```clojure
{:error "Observation field is too long" :detail "Maximum: 50,000 characters"}
```

#### 5. Update CSV Schema Documentation

**File**: `src/com/apriary/schema/api.clj`

**Intent**: Update documentation comment to reflect 50k limit for CSV observations.

**Contract**: Line 63 doc comment.

Change line 63 from:
```clojure
;; Each observation: 50-10,000 characters after trim
```

To:
```clojure
;; Each observation: 50-50,000 characters after trim
```

#### 6. Update Schema Drift Tests

**File**: `test/com/apriary/services/csv_import_test.clj`

**Intent**: Update schema drift detection tests (lines 385-404) that explicitly verify the 10k limit in Malli validation. These tests were added in Jun 2026 commit 63ee231 to detect drift — now we're intentionally aligning, so tests must reflect new limit.

**Contract**: Test "Observation length constraint matches CSV validator - maximum (10,000 chars)" at lines 385-404. Update hardcoded values and test title.

Changes:
- Test title (line ~385): "maximum (10,000 chars)" → "maximum (50,000 chars)"
- `10000` → `50000`
- `10001` → `50001`  
- `"10,000"` → `"50,000"`
- Comments referencing 10k limit → update to 50k

### Success Criteria:

#### Automated Verification:

- Schema file updated: `grep ":max 50000" src/com/apriary/schema.clj` (exit 0)
- No remaining 10k limits in schema: `! grep ":max 10000" src/com/apriary/schema.clj` (exit 0)
- CSV validator updated: `grep -E "(> \(count .* 50000)" src/com/apriary/services/csv_import.clj` (exit 0)
- No hardcoded 10k limits in CSV: `! grep "10000\|10,000" src/com/apriary/services/csv_import.clj` (exit 0)
- Unit tests pass: `clojure -M:test` (all updated tests GREEN)
- Linting passes: `clojure -M:dev lint`
- Integration tests pass: CI pipeline completes

#### Manual Verification:

- Git diff confirms exactly 9 changes: schema.clj (1) + csv_import.clj (3) + schema/api.clj doc (1) + csv_import_test.clj (4)
- No unexpected test failures (all csv_import_test.clj cases pass)
- Test failure messages now reference "50,000 characters" not "10,000"
- CSV import and manual entry both accept same max length (consistency achieved)

**Implementation Note**: After completing this phase and all automated verification passes, this change is safe (tests enforce correctness). No manual UI testing needed — service layer already validates at 50k, we're just aligning database schema. Proceed to Phase 3.

---

## Phase 3: Verify Schema Duplication Eliminated (C5)

### Overview

Confirm that Phase 1 deletion of `schema/api.clj` successfully eliminated schema duplication. The duplicate `create-manual-summary-schema` lived in the deleted file, so no additional code changes needed — this phase is pure verification that duplication is gone and inline schema is now canonical.

### Changes Required:

No code changes — verification only.

#### 1. Confirm Single Schema Remains

**File**: `src/com/apriary/pages/summaries_view.clj`

**Intent**: Verify that only one copy of `create-manual-summary-schema` exists in the codebase (the inline version at lines 334-346, updated with canonical docstring in Phase 1).

**Contract**: Grep for schema definition across codebase must return exactly 1 result.

```bash
# Verification command
grep -r "create-manual-summary-schema" src/ | grep "def "
# Expected: single result from summaries_view.clj:334
```

#### 2. Verify No Stale References

**File**: Entire codebase

**Intent**: Ensure no code attempts to reference the deleted `schema.api/create-manual-summary-schema` (would cause import error).

**Contract**: No require statements or namespace-qualified references to deleted schema.

```bash
# Verification commands
grep -r "schema\.api" src/ test/
grep -r "api-schema/create-manual-summary-schema" src/ test/
# Expected: both return zero results
```

### Success Criteria:

#### Automated Verification:

- Single schema definition: `grep -c "def create-manual-summary-schema" src/ == 1` (exit 0)
- No orphaned imports: `! grep -r "schema\.api" src/ test/` (exit 0)
- Linting passes: `clojure -M:dev lint` (CI would catch import errors)
- All tests pass: `clojure -M:test` (regression check)

#### Manual Verification:

- Visual inspection of `summaries_view.clj:334-346` confirms schema structure unchanged (only docstring updated)
- No complaints from IDE/REPL about missing namespace imports
- Application starts successfully: `clj -M:dev dev` runs without errors

**Implementation Note**: This is a verification-only phase. If all checks pass, duplication is eliminated with zero additional work (covered by Phase 1 deletion). If any check fails, investigate whether Phase 1 deletion missed a reference or if new code was added between phases.

---

## Testing Strategy

### Automated Testing

**Per user decision**: Rely on existing test suite + CI, no new tests written.

**Coverage by phase**:
- **Phase 1 (C2)**: grep verification (zero imports) + CI lint (clj-kondo catches import errors)
- **Phase 2 (C3)**: Explicit test coverage in `csv_import_test.clj` (tests WILL FAIL if schema/test mismatch) + CI integration tests
- **Phase 3 (C5)**: Covered by Phase 1 grep checks + CI lint

**CI pipeline**: `.github/workflows/pull-request.yml` runs:
- clj-kondo linting (namespace validation)
- Unit tests (`clojure -M:test`)
- Integration tests

**Test updates required**: Phase 2 only — 4-5 hardcoded values in `csv_import_test.clj`

### Manual Testing

**Minimal manual verification needed** (all changes are internal):
- Phase 1: git diff check (only expected files modified)
- Phase 2: git diff check (schema + test updates only)
- Phase 3: grep verification commands

**No UI testing required**: All refactorings are internal code cleanup with zero user-facing changes. Service layer behavior unchanged (already validates at 50k). Frontend validation unchanged (already at 50k). Only database schema aligned to match.

**Smoke test** (optional, after all phases): Start application (`clj -M:dev dev`), verify no startup errors, manually create a summary via UI to confirm validation still works.

## Performance Considerations

No performance impact expected:
- **Phase 1**: Deleting file — no runtime change
- **Phase 2**: Schema validation limit change (10k→50k) — relaxes constraint, doesn't add overhead
- **Phase 3**: Verification only — no code changes

All changes are compile-time or validation-time; no new runtime computations added.

## Migration Notes

**No data migration needed**: 
- Schema drift fix aligns database schema to existing service-layer behavior (50k limit already enforced in `services/summary.clj` since Nov 2025)
- Existing summaries in XTDB remain valid (any existing content was already validated at 50k by service layer)
- No breaking changes to API contracts or database structure

**Rollback strategy**: Each phase is independently revertable via git:
- Phase 1: `git restore src/com/apriary/schema/api.clj` + revert docstring change
- Phase 2: `git revert <commit-sha>` (reverts schema.clj + all test changes atomically)
- Phase 3: No code changes, nothing to revert

**Deployment**: Standard deployment process — no special steps, restarts, or configuration changes required.

## References

- Research foundation: `context/changes/summaries-refactor-opportunities/research.md` (comprehensive analysis of all 5 candidates, ranking methodology, cost-benefit assessment)
- Original technical debt analysis: `context/changes/summaries-flow-analysis/research.md` (identified the 5 structural problems)
- Related files:
  - Schema definitions: `src/com/apriary/schema.clj` (database), `src/com/apriary/schema/api.clj` (orphaned)
  - Validation usage: `src/com/apriary/pages/summaries_view.clj:334-346` (inline schema)
  - Service layer: `src/com/apriary/services/summary.clj:37-39` (50k limit enforcement)
  - Test coverage: `test/com/apriary/services/csv_import_test.clj` (lines 99-108, 118-124, 385-420)
- Git history:
  - Nov 27, 2025 sprint: commits creating orphaned schema + duplication
  - Jun 5, 2026 partial drift fix: commit 63ee231 "feat(testing-cross-feature-regression)"
- CI configuration: `.github/workflows/pull-request.yml`

**Deferred work** (future change-ids per research roadmap):
- E2E test foundation: 1 week (Playwright/Cypress + 5 critical scenarios)
- God Page split (C1): 2-3 days (after E2E exists) — split summaries_view.clj into 4 namespaces
- Handler-level unit tests: Est. 1-2 weeks (105-150 mock setups for 15 handlers)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Remove Orphaned Schema (C2)

#### Automated

- [x] 1.1 Verify zero imports: `grep -r "schema\.api" src/ test/` returns 0 results — 790dbaa
- [x] 1.2 Delete schema file: `test ! -f src/com/apriary/schema/api.clj` — 790dbaa
- [x] 1.3 Linting passes: `clojure -M:dev lint` (clj-kondo) — 790dbaa
- [x] 1.4 Unit tests pass: `clojure -M:test` — 790dbaa
- [x] 1.5 Integration tests pass (CI) — 790dbaa

#### Manual

- [x] 1.6 Git diff shows only expected changes (schema/api.clj deletion + summaries_view.clj docstring) — 790dbaa

### Phase 2: Fix Schema Drift (C3)

#### Automated

- [x] 2.1 Schema updated: `grep ":max 50000" src/com/apriary/schema.clj` succeeds — 21d6030
- [x] 2.2 No schema 10k limits: `! grep ":max 10000" src/com/apriary/schema.clj` — 21d6030
- [x] 2.3 CSV validator updated: `grep -E "(> \(count .* 50000)" src/com/apriary/services/csv_import.clj` succeeds — 21d6030
- [x] 2.4 No CSV 10k limits: `! grep "10000\|10,000" src/com/apriary/services/csv_import.clj` — 21d6030
- [x] 2.5 Unit tests pass: `clojure -M:test` (csv_import_test.clj GREEN) — 21d6030
- [x] 2.6 Linting passes: `clojure -M:dev lint` — 21d6030
- [x] 2.7 Integration tests pass (CI) — 21d6030

#### Manual

- [x] 2.8 Git diff confirms expected changes (schema.clj + csv_import.clj + tests) — 21d6030
- [x] 2.9 CSV import and manual entry both accept same max length (consistency verified) — 21d6030

### Phase 3: Verify Schema Duplication Eliminated (C5)

#### Automated

- [x] 3.1 Single schema remains: `grep -c "def create-manual-summary-schema" src/ == 1`
- [x] 3.2 No schema.api references: `! grep -r "schema\.api" src/ test/`
- [x] 3.3 Linting passes: `clojure -M:dev lint`
- [x] 3.4 All tests pass: `clojure -M:test` (regression check)

#### Manual

- [ ] 3.5 Application starts: `clj -M:dev dev` runs without errors
- [ ] 3.6 Optional smoke test: Create summary via UI, verify validation works
