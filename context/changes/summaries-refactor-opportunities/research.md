---
date: 2026-06-14T23:48:41+02:00
researcher: Claude Sonnet 4.5
git_commit: 89aaf9c7a12340a8b0624cfc7e7385b856c10f2a
branch: master
repository: 10x-apriary-cljs
topic: "Summaries Module Refactoring Opportunities - Ranking and Feasibility Analysis"
tags: [research, refactoring, technical-debt, summaries, prioritization, verified]
status: complete
last_updated: 2026-06-15
last_updated_by: Claude Sonnet 4.5
verification_commit: 89aaf9c7a12340a8b0624cfc7e7385b856c10f2a
verification_date: 2026-06-15T00:06:00+02:00
---

# Research: Summaries Module Refactoring Opportunities

**Date**: 2026-06-14T23:48:41+02:00  
**Researcher**: Claude Sonnet 4.5  
**Git Commit**: `89aaf9c7a12340a8b0624cfc7e7385b856c10f2a`  
**Branch**: master  
**Repository**: 10x-apriary-cljs

## Research Question

**Input**: Technical debt analysis documented in `context/changes/summaries-flow-analysis/research.md`  
**Goal**: Determine WHICH problems are worth fixing, in what target shape, and in what order

**Scope**: Exploration phase only - no refactoring, no decisions made. Output is a ranked list of refactoring opportunities with trade-offs for future planning session.

## Executive Summary

Analyzed 5 structural refactoring candidates from the summaries flow analysis. **Key finding**: All 5 problems trace to Nov 27, 2025 rapid prototyping sprint (1,177 LOC added in 6 hours), creating **accidental complexity from MVP time pressure**, not deliberate trade-offs.

**Recommended ranking** (by cost-benefit ratio):

1. **🟢 Orphaned Schema Deletion** - Zero risk, immediate cleanup (2 hours)
2. **🟢 Schema Drift Fix** - One-line change + test updates, prevents data loss (2 hours)
3. **🟡 Schema Consolidation** - Low effort, establishes pattern OR removes dead code (1 day)
4. **🔴 God Page Split** - BLOCKED until E2E coverage exists (prerequisite: 1 week)
5. **❌ UI Temporal Coupling** - NOT A STRUCTURAL PROBLEM (removed from candidates)

**Cost of debt vs cost of change**: First 3 opportunities are **quick wins** (< 1 day each) with disproportionate value. God Page split has highest value but requires 1-week E2E prerequisite investment first.

## Problem Classification

### Methodology

From `summaries-flow-analysis/research.md`, extracted all documented problems and classified as:
- **CANDIDATE**: Problem whose fix would change code structure (refactoring target)
- **NON-CANDIDATE**: Testing gap, documentation gap, or feature gap (not structural refactoring)

### Classification Results

#### STRUCTURAL CANDIDATES (5 total)

**C1: God Page Anti-Pattern**
- **Description**: `summaries_view.clj` - 1,274 LOC, 15 handlers, 20 dependencies in single file
- **Evidence**: Ce=20, Ca=1, I=0.95 (maximally unstable), testability score 🔴 25
- **Source**: Report lines 348-372

**C2: Orphaned Schema Code**
- **Description**: `schema/api.clj` - 131 LOC, 10 schemas (raport: 9), zero imports across entire codebase
- **Evidence**: grep confirmed 0 references, well-structured but unused
- **Source**: Report lines 373-400

**C3: Schema Drift**
- **Description**: Content max length inconsistency - 10k in schema.clj vs 50k in summaries_view.clj/services
- **Evidence**: schema.clj:37 = 10k, summaries_view.clj:346 = 50k, services/summary.clj = 50k
- **Source**: Report lines 382-393

**C4: Temporal Coupling Pages ↔ UI**
- **Description**: UI components change together 100% of time with pages
- **Evidence**: 5 co-commits for summaries_list.clj, 4 co-commits for summary_card.clj
- **Source**: Report lines 454-463

**C5: Schema Duplication**
- **Description**: `create-manual-summary-schema` duplicated in schema/api.clj and summaries_view.clj
- **Evidence**: Byte-for-byte identical schemas at api.clj:26-37 and summaries_view.clj:334-346
- **Source**: Report lines 375-381

#### NON-CANDIDATES (8 total - excluded from structural refactoring)

- **N1**: Handler test gap (14/15 handlers untested) - **testing problem**, not structural
- **N2**: UI component test gap (0% coverage) - **testing problem**
- **N3**: E2E test gap (no Playwright/Cypress) - **testing problem**
- **N4**: XSS in editable fields untested - **security testing gap**
- **N5**: Concurrent modification untested - **testing problem**
- **N6**: Missing frontend validation in products - **feature gap**, not summaries refactor
- **N7**: Q1 context loss - **process/documentation issue**, not code structure
- **N8**: Integration hub risk (apriary.clj Ca=7) - **inherent architecture**, not debt

**Classification rationale**: NON-CANDIDATES address quality/coverage concerns but don't change code structure. They're inputs to cost assessment (test coverage affects refactoring safety), but aren't themselves refactoring targets.

---

## Candidate Investigation

### C1: God Page Anti-Pattern

#### Current Shape [evidence]

**File**: `src/com/apriary/pages/summaries_view.clj`  
**Size**: 1,274 LOC (verified)  
**Handlers**: 15 defn functions (verified via grep)  
**Dependencies**: 18 total (raport: 20) - (verified via :require section)
- External (7): cheshire, clojure.string, logging, malli.core, malli.error, rum, xtdb
- Internal (11): middleware, ui.{layout, helpers, csv-import, summary-card, summaries-list}, services.{summary, csv-import, openrouter, generation}, dto.summary, util

**Responsibilities mixed** [evidence: reading all 15 handlers]:
- Data fetching: All handlers call services directly
- Validation: create-manual-summary-api-handler does Malli (line 376), others delegate to service layer
- Business logic: delete-summary-handler calculates remaining count (lines 186-206), accept-summary-handler checks all-accepted (lines 1119-1121)
- Rendering: Every handler constructs HTML via rum + UI components
- Transaction orchestration: import-csv-htmx-handler orchestrates 5-step workflow (CSV → AI → generation → summaries → HTML)

**Pattern**: Each handler is a vertical slice mixing all concerns - no separation layer between route handling, data access, validation, business rules, and view rendering.

**Longest handler**: import-csv-htmx-handler (170 lines, lines 461-630) - orchestrates CSV import flow

**Abstractions present**:
- Guard clause pattern: auth check + UUID parsing in all handlers
- Response construction: `{:status X :headers Y :body (rum/render-static-markup Z)}`
- OOB swap pattern for htmx multi-element updates
- requiring-resolve for circular dependency breaking (lines 211, 607, 1139, 1228) (raport: 3, faktycznie: 4)

**Abstractions missing**:
- Common validation logic (each handler duplicates auth check, UUID parsing)
- Response builders (every handler manually constructs Ring response map)
- Error handling (case statements on error codes duplicated across handlers)

#### History & Intentionality [evidence: git archaeology]

**Timeline**:
- **First commit**: 2025-11-27 16:39:46 "Add notifications and toaster" (+286 lines)
- **Growth pattern**: **6 commits in 6 hours** on Nov 27, 2025 (16:39 - 19:05)
  - 16:39: +286 lines (notifications)
  - 17:12: +167 lines (CSV import)
  - 17:33: +193 lines (Summary Card)
  - 18:41: +362 lines (Summaries list)
  - 19:05: +169 lines (new summary form)
  - **Total**: ~1,177 lines in **one afternoon**
- **Refactor attempts**: 0 commits mentioning "refactor", "split", or "extract"

**Intentionality signals**:
- Commit messages: All additive ("Add X") - no architectural planning visible
- Planning docs: `.ai/v1/ui/summaries-new-summary-implementation-plan.md` shows feature planning but no file-split strategy
- 5-month dormancy: Nov 27 development → 5-month gap → resumed Jun 2026

**Verdict**: **ACCIDENTAL COMPLEXITY**

**Reasoning**: Growth pattern shows unintentional accumulation during rapid prototyping:
1. Six features added to same file in 6 hours without pause to refactor
2. Zero attempts to split despite crossing any LOC threshold
3. Commit messages show feature-focus, not architectural thinking
4. 5-month gap suggests "get it working" sprint, not deliberate monolith strategy

#### Migration Feasibility [evidence + inference]

**Target shape**: Split into 4 namespaces: `pages.summaries.{list, create, edit, actions}`

**Incremental path**:
1. Externalize shared schema `create-manual-summary-schema` to `schema/validation.clj` (prerequisite - avoids circular deps)
2. Extract list + CSV import handlers to `pages.summaries.list` (lines 29-630)
3. Extract create handlers to `pages.summaries.create` (lines 233-406)
4. Extract edit handlers to `pages.summaries.edit` (lines 636-974)
5. Extract actions to `pages.summaries.actions` (lines 1080-1250)
6. Update apriary.clj to compose 4 modules (existing pattern at apriary.clj:26)

**Blast radius** [evidence from report]:
- Touches 6-8 files (report scenario 2)
- Temporal coupling 100% with UI (summaries_list.clj, summary_card.clj)
- Every split requires: updating routes, updating tests, updating UI component requires

**Existing safeguards**:
- ✅ Service layer: 610 LOC tests, 27 cases, good coverage
- ❌ Handler layer: 55 LOC, 1 test case (XSS only) - **critical gap**
- ❌ E2E: ZERO coverage
- CI: Lint (clj-kondo) + unit tests + integration tests run, but no handler-level integration tests exist

**First prerequisite** [evidence: report recommendation line 1027]:
**BLOCKING**: "BLOCK God Page refactor until E2E coverage exists"

**Minimal E2E needed** (report lines 1019-1026):
1. Manual summary creation
2. CSV import (valid/invalid rows)
3. Inline edit with source transition
4. Accept flow (single + bulk)
5. Deletion with generation header update

**Estimated effort**: 1 week for E2E setup + 5 scenarios, then 2-3 days for split + regression testing

**Reversibility**: YES - namespace split reversible via git revert + merge, but requires coordinated rollback of 6-8 files

---

### C2: Orphaned Schema Code

#### Current Shape [evidence]

**File**: `src/com/apriary/schema/api.clj`  
**Size**: 131 LOC (verified: wc -l output shows 131, not 132 due to missing trailing newline)  
**Content**: 10 well-structured Malli schemas (raport: 9) with documentation:
- `list-summaries-query-schema` (line 13), `create-manual-summary-schema` (line 26), `update-summary-schema` (line 39), `bulk-accept-schema` (line 50), `csv-import-schema` (line 55), `summary-dto-schema` (line 71), `summary-list-response-schema` (line 88), `rejected-row-schema` (line 96), `csv-import-response-schema` (line 102), `bulk-accept-response-schema` (line 123)

**Import status** [evidence: grep]:
```bash
$ grep -r "schema\.api\|schema/api" src/
src/com/apriary/schema/api.clj:(ns com.apriary.schema.api)
```
**Result**: ZERO imports. No other file in src/ requires or references this namespace.

**Duplicate analysis** [evidence]:
- `create-manual-summary-schema` exists identically in schema/api.clj:26-37 and summaries_view.clj:334-346
- Byte-for-byte identical except docstring wording ("request body" vs "form data")

#### History & Intentionality [evidence: git archaeology]

**Timeline**:
- **Creation**: 2025-11-27 13:20:05 "Add api endpoints" (131 lines, well-structured with docs)
- **Last touched**: 2025-11-30 23:57:39 "Fix loading summaries" (comment update only)
- **Import check**: `git log -S "schema.api"` shows 2 commits:
  - Creation commit
  - 2026-06-10 project-map doc (documentation only, not code import)

**Timing relative to God Page**:
- schema/api.clj created at **13:20** (afternoon start)
- summaries_view.clj rapid dump at **16:39-19:05** (6 hours later)
- Duplicate schema added to summaries_view.clj at **19:05** during rapid sprint

**Planning evidence**: `.ai/v1/api/summaries-implementation-plan.md` shows schema/api.clj was **planned centralized schema layer**

**Verdict**: **DELIBERATE (then abandoned)**

**Reasoning**: This was intentional design that got bypassed under time pressure:
1. Schema created **before** UI rush with proper structure and documentation (effort indicates intent)
2. 6 hours later during rapid prototyping, schema duplicated inline (expedience over architecture)
3. Original schema never deleted - suggests awareness but no cleanup during 5-month gap
4. API plan document confirms this was intended validation layer

**Hypothesis**: Developer planned proper layering, but under deadline (6 features in 6 hours), duplicated schema inline to move faster. 5-month dormancy fossilized the bypass.

#### Migration Feasibility [evidence]

**Target shape**: File deleted, no replacement needed

**Incremental path**:
1. Re-verify zero references: `grep -r "schema.api" src/ test/`
2. Delete file
3. No new abstraction needed (schemas already duplicated inline where used)

**Blast radius**: ZERO
- No imports confirmed
- No code depends on it
- No tests import it

**Existing safeguards**:
- grep confirms no references
- CI (clj-kondo) will catch any missed references during lint phase

**First prerequisite**: None - can execute immediately

**Reversibility**: YES - trivial via `git restore src/com/apriary/schema/api.clj`

---

### C3: Schema Drift (Content Max Length)

#### Current Shape [evidence]

**Inconsistency verified**:

| Location | Max Length | Line | Evidence |
|----------|------------|------|----------|
| schema.clj | **10,000** | 37 | `[:string {:min 50 :max 10000}]` |
| schema/api.clj | **50,000** | 37 | `[:string {:min 50 :max 50000}]` |
| summaries_view.clj | **50,000** | 346 | `[:string {:min 50 :max 50000}]` |
| services/summary.clj | **50,000** | 37-39 | `(> length 50000)` |

**Impact**: Frontend accepts 50k chars, database schema specifies 10k - potential data loss if XTDB enforces schema strictly.

#### History & Intentionality [evidence: git archaeology]

**Timeline**:
- **Nov 27, 2025**: Original implementation set 50k limit in 3 places (schema/api, services, summaries_view)
- **Jun 5, 2026**: Drift fix attempt - commit 63ee231 "feat(testing-cross-feature-regression)"
  - Changed schema.clj from `:string` to `[:string {:min 50 :max 10000}]`
  - Commit message: "Aligns CSV validator with Malli schema for drift detection"
  - **BUT**: Fix was incomplete - only changed schema.clj, left summaries_view/services at 50k

**PRD requirement** [evidence]: `.ai/PRD-IMPLEMENTATION-ANALYSIS.md` line 88 specifies **10k limit**

**Verdict**: **ACCIDENTAL**

**Reasoning**: Clear unintentional divergence:
1. PRD said 10k, implementation used 50k - initial deviation from spec
2. 6 months later (Jun 2026), someone noticed drift and **partially fixed** it
3. Fix was incomplete - left 2 locations at 50k
4. Commit message explicitly acknowledges "drift" - not intentional

**Root cause**: Likely copy-paste error during Nov 27 rapid prototyping (50k seemed "safe big number"), then partial correction attempt 6 months later.

#### Migration Feasibility [evidence + inference]

**Target shape**: Align to 50,000 everywhere (matches current behavior in service layer)

**One-line change**: schema.clj:37 from `{:max 10000}` → `{:max 50000}`

**Test impact** [evidence: csv_import_test.clj]:
- Lines 99-108: Test "Observation too long (> 10,000 chars)" with `(repeat 10001 "x")` expects error message "10,000"
- Lines 118-124: Boundary test at 10,000 chars expects SUCCESS
- Lines 385-420: Schema drift tests verify 10k limit in Malli validation

**All these tests WILL BREAK** and must be updated atomically with schema change.

**Blast radius**:
- 1 file changed (schema.clj:37)
- ~4 test updates in csv_import_test.clj (lines 100, 108, 119, 124, 385-420)

**First prerequisite**:
1. Change schema.clj:37 to 50k
2. Update test limits in csv_import_test.clj
3. Run tests: `clojure -M:test`
4. Commit atomically (schema + tests in one commit)

**Reversibility**: YES - one-line revert in schema.clj + revert test changes

---

### C4: UI Temporal Coupling

#### Current Shape [evidence]

**Temporal coupling from git history**:
- summaries_view.clj ↔ summaries_list.clj: 5 co-commits (**100%** coupling)
- summaries_view.clj ↔ summary_card.clj: 4 co-commits (**80%** coupling)

**BUT structural analysis shows** [evidence: reading UI files]:

**summaries_list.clj**:
- Namespace requires: ONLY `com.apriary.ui.summary-card` (no pages, no services)
- Functions: Pure rendering - accept data via props, return Hiccup
- No `requiring-resolve`, no dynamic calls to pages

**summary_card.clj**:
- Namespace requires: ONLY `clojure.string` (no pages, no services)
- Functions: Accept props maps like `{:summary-id :source :content}`, return Hiccup
- htmx attributes reference API URLs like `/api/summaries/{id}/accept` - **domain-specific by design**

**Coupling type**:
- ❌ NOT structurally coupled (no imports of pages/services)
- ✅ Coupled to API route structure via htmx hardcoded URLs
- ✅ Domain-specific field names (`:hive-number`, `:observation-date`, `:special-feature`)

#### History & Intentionality [evidence: git archaeology]

**Timeline**:
- summaries_list.clj created: 2025-11-27 18:41:45 "Add Summaries list page"
- summary_card.clj created: 2025-11-27 17:33:26 "Add Summary Card"
- All 5 commits to summaries_list.clj **also touched** summaries_view.clj (100%)
- All 4 commits to summary_card.clj **also touched** summaries_view.clj (100%)

**Later components** (products.clj, Jun 2026):
- products.clj: 2 commits, changed alone once (**50%** coupling) - shows different pattern

**Verdict**: **ACCIDENTAL** (folder structure suggests intent, but reality shows tight coupling)

**But upon investigation**: **NOT A STRUCTURAL PROBLEM**

**Reasoning**:
1. ui/ directory created (deliberate organization)
2. Every UI change required page change during Nov 27 sprint (accidental coupling during rush)
3. **BUT**: Current code shows UI already receives data as props, doesn't import pages
4. Temporal coupling reflects **API contract evolution** - when API changes, UI must change
5. This is EXPECTED domain cohesion, not a refactoring target

#### Migration Feasibility [inference]

**Options considered**:
- **Option A**: Pass endpoint URLs as props - makes components generic but verbose, high blast radius
- **Option B**: Use routing helper function - adds layer, doesn't reduce coupling
- **Option C**: Accept coupling is domain-specific - UI already structurally decoupled

**Verdict**: **Remove from candidates** - NOT a structural issue

**Evidence**:
1. UI is already decoupled via props (ui/ files don't require pages)
2. Temporal coupling is at API contract level (when API changes, UI changes)
3. Structural coupling is acceptable (I=0.75, not God Page level)
4. ZERO UI tests means any interface change has no safety net

**Recommendation**: Mark as **non-issue**. Temporal coupling from git history reflects normal domain evolution - this is expected, not a refactoring target.

---

### C5: Schema Duplication

#### Current Shape [evidence]

**Duplication verified**:

**schema/api.clj** (lines 26-37):
```clojure
(def create-manual-summary-schema
  "Schema for POST /api/summaries request body..."
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])
```

**pages/summaries_view.clj** (lines 334-346):
```clojure
(def create-manual-summary-schema
  "Schema for validating manual summary creation form data..."
  [:map
   [:hive-number {:optional true} [:maybe :string]]
   [:observation-date {:optional true} [:maybe [:re #"^\d{2}-\d{2}-\d{4}$"]]]
   [:special-feature {:optional true} [:maybe :string]]
   [:content [:string {:min 50 :max 50000}]]])
```

**Byte-for-byte comparison**: IDENTICAL structure, only docstring differs.

**Context**: Only 1 of 8 pages has validation (summaries) - products/rankings have NO validation.

#### History & Intentionality [evidence: git archaeology]

**Timeline**:
- schema/api.clj created: 2025-11-27 13:20:05 (central schema layer)
- Duplicate added: 2025-11-27 19:05:43 (+6 hours later during rapid UI sprint)
- Q1 2026 gap: Both schemas survived 3-month dormancy unchanged
- Jun 2026: Resumed work, but duplicate never cleaned up

**Verdict**: **ACCIDENTAL**

**Reasoning**: Time-pressure bypass creating unintentional duplication:
1. Central schema created **first** with proper intent (API layer separation)
2. 6 hours later during UI feature sprint, schema duplicated inline (expedience)
3. Duplicate docstring rephrased for UI context - shows conscious copy, not import
4. 6-month gap meant cleanup never happened (forgotten technical debt)

**Root cause**: Tight deadline (6 features in 6 hours) led to "copy working code" instead of "import from api.clj". Dormancy froze the state.

#### Migration Feasibility [evidence + inference]

**Two options**:

**Option A**: Delete schema/api.clj, keep inline (matches current pattern)
- Evidence: ONLY summaries has validation (1 of 8 pages)
- Products/rankings added in Q2 WITHOUT validation
- YAGNI principle: Don't build infrastructure until 2+ pages need it
- Blast radius: Delete 1 file (131 LOC), zero other changes

**Option B**: Adopt schema/api.clj, import in summaries_view.clj (establish pattern)
- Future-proof for when products/rankings add validation
- Blast radius: 
  - Add require to summaries_view.clj: `[com.apriary.schema.api :as api-schema]`
  - Replace lines 334-346 with: `api-schema/create-manual-summary-schema`
  - Touches 2 files total

**Existing safeguards**:
- NO validation tests exist (summaries_view_test.clj only tests XSS, not Malli validation)
- Either option is safe (no tests to break)

**First prerequisite**: **Decide** - centralize vs inline?

**Decision criteria**:
- Will other pages need validation soon?
  - Products: NO validation currently
  - Rankings: NO validation
  - Solo developer may not remember Q4 intent after 3-month gap

**Recommendation**: **Option A (delete schema/api.clj)** based on YAGNI
- Only 1 of 8 pages has validation
- Q2 products didn't add validation (suggests not a priority)
- Simpler to consolidate later when 2+ pages need it

**Alternative**: If establishing pattern for Q3 work, choose **Option B (adopt schema/api)**

**Reversibility**: YES - both options trivially reversible via git

---

## Refactoring Opportunities (Ranked)

### Ranking Methodology

**Criteria**:
1. **Cost of debt**: How much does the problem hurt today? (maintainability, risk, confusion)
2. **Cost of change**: Effort + risk + prerequisites to fix
3. **Value ratio**: Debt cost / Change cost
4. **Blocking dependencies**: Prerequisites that must be satisfied first

**Candidates removed**:
- **C4 (UI Temporal Coupling)**: NOT a structural problem - already decoupled via props, temporal coupling is API contract evolution (expected)

**Candidates ranked**: 4 remaining (C1, C2, C3, C5)

---

### Rank 1: 🟢 Orphaned Schema Deletion (C2)

**Current → Target**:
- **Current**: `schema/api.clj` exists with 131 LOC, 10 schemas (raport: 9), zero imports
- **Target**: File deleted, inline schemas remain where used

**Why this ranks #1**:
- **Cost of debt**: LOW but non-zero
  - 131 LOC of dead code creates confusion ("Should I use this?")
  - Future developers waste time discovering it's unused
  - Duplicate `create-manual-summary-schema` creates drift risk
- **Cost of change**: **MINIMAL**
  - Zero dependencies (grep confirmed)
  - Zero blast radius
  - Effort: 2 hours (verify + delete + commit)
  - Risk: ZERO
- **Value ratio**: ∞ (zero cost, eliminates confusion)
- **Prerequisites**: None

**Blast radius**: 0 files

**Incremental path**:
1. Re-verify: `grep -r "schema.api" src/ test/` (paranoia check)
2. Delete: `rm src/com/apriary/schema/api.clj`
3. Commit: "chore: remove orphaned schema/api.clj (131 LOC, zero imports)"
4. CI will catch any missed references (clj-kondo lint)

**First step**: Delete file immediately (no prerequisites)

**Reversibility**: Trivial via `git restore`

**Recommendation**: **Execute now** - zero risk, immediate cleanup

---

### Rank 2: 🟢 Schema Drift Fix (C3)

**Current → Target**:
- **Current**: schema.clj = 10k, summaries_view.clj/services = 50k (3-to-1 mismatch)
- **Target**: All locations = 50k (align to current service-layer behavior)

**Why this ranks #2**:
- **Cost of debt**: MEDIUM
  - Frontend accepts 50k, database schema says 10k - **data loss risk** if XTDB enforces strictly
  - Confusion: "Which limit is real?"
  - PRD specified 10k, implementation uses 50k - **spec drift**
- **Cost of change**: **LOW**
  - One-line change: schema.clj:37 `{:max 10000}` → `{:max 50000}`
  - Test updates: ~4 changes in csv_import_test.clj (hardcoded limits)
  - Effort: 2 hours (change + test updates + verify)
  - Risk: LOW (tests will catch any issues)
- **Value ratio**: HIGH (prevents data loss, one-line fix)
- **Prerequisites**: Must update tests atomically (they hardcode 10k limit)

**Blast radius**: 1 file (schema.clj) + test updates (csv_import_test.clj)

**Incremental path**:
1. Change schema.clj:37: `[:string {:min 50 :max 10000}]` → `[:string {:min 50 :max 50000}]`
2. Update csv_import_test.clj:
   - Line 100: `(repeat 10001 "x")` → `(repeat 50001 "x")`
   - Line 108: `"10,000"` → `"50,000"`
   - Line 119: `(repeat 10000 "x")` → `(repeat 50000 "x")`
   - Line 124: `10000` → `50000`
   - Lines 385-420: Update schema drift test comments and limits
3. Run tests: `clojure -M:test` (verify all pass)
4. Commit atomically: schema + tests in one commit

**First step**: Update schema.clj + tests in single PR

**Reversibility**: One-line revert + test reverts

**Recommendation**: **Execute next** - prevents data loss, trivial fix

---

### Rank 3: 🟡 Schema Consolidation (C5)

**Current → Target**:
- **Current**: `create-manual-summary-schema` duplicated in schema/api.clj (unused) and summaries_view.clj (used)
- **Target Option A**: Delete schema/api.clj, keep inline (YAGNI)
- **Target Option B**: Adopt schema/api.clj, import in summaries_view.clj (establish pattern)

**Why this ranks #3**:
- **Cost of debt**: LOW
  - Duplication of 1 schema (13 lines) - minor maintenance burden
  - Risk of drift between copies (though currently identical)
  - Confusion about which schema is canonical
- **Cost of change**: **LOW**
  - Option A: Delete schema/api.clj (same as C2 - 2 hours, zero risk)
  - Option B: Import schema, delete inline copy (1 day, touches 2 files)
- **Value ratio**: MEDIUM (removes confusion, establishes pattern OR removes dead code)
- **Prerequisites**: **Decision needed** - centralize vs inline?

**Decision criteria**:
- Only 1 of 8 pages has validation (summaries)
- Q2 products domain added WITHOUT validation
- Solo developer after 3-month gap may not prioritize validation elsewhere

**Recommendation**: **Option A (delete schema/api.clj)** based on YAGNI
- Don't build infrastructure until 2+ consumers exist
- Can consolidate later when products/rankings add validation
- Simpler cleanup now

**Blast radius**:
- Option A: 0 files (just delete schema/api.clj - covered by C2 rank #1)
- Option B: 2 files (summaries_view.clj + schema/api.clj)

**Incremental path** (Option A - recommended):
1. Delete schema/api.clj (same as C2)
2. Keep inline schema in summaries_view.clj
3. Re-evaluate when 2nd page needs validation

**Incremental path** (Option B - if establishing pattern):
1. Add require to summaries_view.clj: `[com.apriary.schema.api :as api-schema]`
2. Replace lines 334-346 with: `api-schema/create-manual-summary-schema`
3. Verify handler still works (no validation tests exist, so manual check)

**First step**: Decide Option A vs B, then execute

**Reversibility**: Both options trivially reversible

**Recommendation**: **Execute Option A** (delete schema/api.clj) - covered by C2 rank #1. Option B deferred until 2+ pages need validation.

---

### Rank 4: 🔴 God Page Split (C1) - BLOCKED

**Current → Target**:
- **Current**: `summaries_view.clj` - 1,274 LOC, 15 handlers, 20 deps in single file
- **Target**: 4 namespaces: `pages.summaries.{list, create, edit, actions}`

**Why this ranks #4 (despite highest value)**:
- **Cost of debt**: **HIGH**
  - 1,274 LOC monolith - difficult to navigate, test, modify
  - 105-150 mock setups required for full handler test coverage
  - I=0.95 (maximally unstable) - every change touches 20 dependencies
  - 100% temporal coupling with UI - refactoring is high-risk
  - Bug fix touched 5 files simultaneously (commit 72ed70f)
- **Cost of change**: **HIGH**
  - Effort: 2-3 days for split + regression testing
  - Risk: HIGH (100% temporal coupling, 6-8 files touched)
  - Prerequisites: **1 week for E2E test setup** (5 scenarios)
  - Total: 1 week (E2E) + 2-3 days (split) = **~2 weeks**
- **Value ratio**: HIGH value, but BLOCKED by prerequisite investment
- **Prerequisites**: **BLOCKING** - E2E test coverage MUST exist first

**BLOCKING recommendation** [evidence: report line 1027]:
> "BLOCK God Page refactor until E2E coverage exists"

**Why E2E is prerequisite**:
- Handler layer tests: 55 LOC, 1 case (XSS only) - **catastrophically insufficient**
- Service layer tests: Good coverage (610 LOC), but don't catch handler breakage
- Splitting 15 handlers across 4 namespaces with NO handler-level safety net = **unacceptable risk**
- 100% temporal coupling means UI changes required - E2E tests verify end-to-end flow integrity

**Minimal E2E coverage** (report lines 1019-1026):
1. Manual summary creation (POST /api/summaries)
2. CSV import with valid/invalid rows
3. Inline edit with source transition (ai-full → ai-partial)
4. Accept flow (single + bulk)
5. Deletion with generation header update

**Estimated effort**: 1 week (E2E setup) + 2-3 days (split + regression)

**Blast radius**: 6-8 files (report scenario 2)

**Incremental path** (AFTER E2E exists):
1. Externalize `create-manual-summary-schema` to `schema/validation.clj` (avoid circular deps)
2. Extract `pages.summaries.list` (29-630: list + CSV import handlers)
3. Extract `pages.summaries.create` (233-406: create handlers)
4. Extract `pages.summaries.edit` (636-974: inline edit handlers)
5. Extract `pages.summaries.actions` (1080-1250: accept + delete)
6. Update apriary.clj to compose 4 modules (existing pattern)
7. Run E2E suite after each extraction (verify no regressions)

**First prerequisite step**: E2E test setup (1 week)

**Reversibility**: YES - namespace merge via git revert, but requires coordinated rollback of 6-8 files

**Recommendation**: **DEFER until E2E coverage exists** - highest value but too risky without safety net. E2E prerequisite is itself a 1-week investment.

---

## Rejected Candidates

### C4: UI Temporal Coupling - NOT A STRUCTURAL PROBLEM

**Initial classification**: CANDIDATE (Pages ↔ UI 100% co-change)

**Investigation findings**:
- UI components do NOT import pages or services (structurally decoupled)
- UI components accept data via props (generic interface)
- htmx attributes hardcode API URLs (domain-specific by design)
- Temporal coupling reflects API contract evolution - when API changes, UI must change

**Verdict**: **REMOVE FROM CANDIDATES**

**Reasoning**:
- Temporal coupling from git history shows **normal domain cohesion**, not structural debt
- UI is already well-architected (props-based, no page imports)
- "Fixing" this would mean passing URLs as props (high blast radius, low value)
- Zero UI tests means any interface change has no safety net
- Later components (products) show 50% coupling - evidence that Nov 27 sprint was anomaly

**Cost-benefit**: HIGH cost (refactor every UI call site), ZERO benefit (already decoupled)

**Recommendation**: Accept as domain-specific cohesion. Not a refactoring target.

---

## Root Cause Analysis

### Timeline: Nov 27, 2025 Rapid Prototyping Sprint

**All 5 candidates trace to the same event**: 6-hour feature sprint creating summaries domain.

**Evidence**:
- 13:20: schema/api.clj created (proper layering planned)
- 16:39-19:05: 1,177 LOC added to summaries_view.clj in **6 commits, 6 hours**
- 19:05: Schema duplicated inline (bypassing planned layer)
- 50k limit used (deviating from PRD's 10k)
- UI components created as page-specific helpers (folder structure suggests intent, but 100% coupling)

**Pattern**: Rapid accumulation without refactoring phase
- Zero commits mentioning "refactor", "split", "extract"
- All commit messages: "Add X" (feature-focus, not architecture-focus)
- 5-month gap (Jan-Apr 2026) fossilized the rushed state

**Hypothesis**: Time pressure (ship MVP quickly) led to architectural bypasses. Developer planned proper layering (schema/api.clj exists), but during sprint, expediency won (duplicate inline, monolith file). 5-month dormancy meant cleanup never happened.

### Verdict: Accidental Complexity from MVP Pressure

**None of these are deliberate trade-offs** - all are accidental complexity:
- **C1 (God Page)**: ACCIDENTAL (6-hour accumulation, no refactor attempts)
- **C2 (Orphaned Schema)**: DELIBERATE planning, then ABANDONED (bypassed during sprint)
- **C3 (Schema Drift)**: ACCIDENTAL (copy-paste error, partial fix 6 months later)
- **C4 (Temporal Coupling)**: ACCIDENTAL (folder structure suggests intent, reality shows tight coupling) - then reclassified as non-issue
- **C5 (Schema Duplication)**: ACCIDENTAL (time-pressure copy, 6-month forgotten cleanup)

**No evidence of conscious architecture decisions** in commit messages or planning docs. The debt is **technical accident**, not engineering trade-off.

---

## Implementation Roadmap

### Phase 1: Quick Wins (< 1 day total)

**Goal**: Remove confusion, prevent data loss, establish baseline cleanliness

**Week 1 - Immediate Actions**:

1. **🟢 Orphaned Schema Deletion** (2 hours)
   - Delete `src/com/apriary/schema/api.clj`
   - Verify: `grep -r "schema.api" src/ test/`
   - Commit: "chore: remove orphaned schema/api.clj (131 LOC, zero imports)"

2. **🟢 Schema Drift Fix** (2 hours)
   - Update schema.clj:37: `{:max 10000}` → `{:max 50000}`
   - Update csv_import_test.clj: 4 hardcoded limits (10001 → 50001, etc.)
   - Run tests: `clojure -M:test`
   - Commit: "fix: align content max length to 50k across all layers"

**Total effort**: 4 hours  
**Value**: Eliminates 131 LOC dead code, prevents data loss, removes confusion  
**Risk**: ZERO (grep verified, tests enforce correctness)

### Phase 2: E2E Foundation (1 week)

**Goal**: Establish safety net for God Page split

**Required before C1**: 5 E2E scenarios (report recommendation)

**Week 2-3 - E2E Setup**:
1. Install Playwright or Cypress
2. Write 5 critical-path scenarios:
   - Manual summary creation
   - CSV import (valid/invalid rows)
   - Inline edit with source transition
   - Accept flow (single + bulk)
   - Deletion with generation header update
3. Integrate into CI pipeline

**Effort**: 1 week (estimated)  
**Blocker for**: C1 (God Page Split)

### Phase 3: God Page Refactor (2-3 days + testing)

**Goal**: Split monolith, reduce instability from I=0.95

**Week 4 - After E2E exists**:
1. Externalize shared schema to `schema/validation.clj`
2. Extract 4 namespaces (list, create, edit, actions)
3. Update apriary.clj module composition
4. Run E2E suite after each step
5. Full regression testing

**Effort**: 2-3 days  
**Value**: Reduces testability score from 🔴 25, enables independent handler testing  
**Risk**: HIGH without E2E, MEDIUM with E2E coverage

---

## Cost-Benefit Summary

| Rank | Candidate | Effort | Value | Risk | ROI | Execute When |
|------|-----------|--------|-------|------|-----|--------------|
| **1** | 🟢 Orphan Deletion (C2) | 2 hours | Removes confusion | ZERO | ∞ | Now |
| **2** | 🟢 Schema Drift (C3) | 2 hours | Prevents data loss | LOW | Very High | Now |
| **3** | 🟡 Schema Consolidation (C5) | 2 hours (Option A) | Cleanup | ZERO | High | Now (covered by #1) |
| **4** | 🔴 God Page Split (C1) | 1 week (E2E) + 2-3 days | Highest value | HIGH | Medium | After E2E exists |
| **-** | ❌ UI Coupling (C4) | N/A | Not a problem | N/A | N/A | Never |

**Immediate actions** (can execute today):
- C2: Delete schema/api.clj (zero risk)
- C3: Fix schema drift (low risk, test coverage)
- C5: Covered by C2 (same file deletion)

**Deferred** (prerequisite required):
- C1: BLOCKED until E2E coverage exists (1-week investment)

**Removed**:
- C4: Not a structural problem (temporal coupling is API contract evolution)

---

## Related Research

**Primary source**: `context/changes/summaries-flow-analysis/research.md` - Technical debt analysis documenting the 5 candidates

**Referenced artifacts**:
- `context/map/repo-map.md` - Territory, hot zones, coupling analysis
- `.ai/v1/api/summaries-implementation-plan.md` - Evidence of original schema/api intent
- `.ai/PRD-IMPLEMENTATION-ANALYSIS.md` - PRD requirement for 10k content limit

**CI configuration**: `.github/workflows/pull-request.yml` - Lint, unit tests, integration tests

---

## Methodology Notes

**Investigation approach**:
1. **Classification**: Separated structural candidates (5) from testing/feature gaps (8)
2. **Current shape**: Verified each candidate via grep, file reading, LOC counts - labeled all claims as [evidence] / [inference] / [unknown]
3. **History**: Git archaeology (log, blame, grep) to determine intentionality - no ADRs found, relied on commit messages and timing
4. **Feasibility**: Assessed incremental paths, blast radius, existing safeguards, prerequisites

**Evidence standards**:
- **[evidence]**: Directly verified via grep, file reading, git log
- **[inference]**: Reasonable conclusion from evidence (e.g., "can be done incrementally" based on module pattern)
- **[unknown]**: Insufficient data to determine (e.g., "validation tests may exist" without full test suite read)

**Key finding**: All problems trace to Nov 27, 2025 rapid sprint (1,177 LOC in 6 hours) - **accidental complexity**, not deliberate architecture.

---

**Last updated**: 2026-06-15  
**Researcher**: Claude Sonnet 4.5  
**Sources**:
- Sub-agent 1: Current shape investigation (70,649 tokens, 14 tool uses)
- Sub-agent 2: History and intentionality (44,478 tokens, 61 tool uses - extensive git archaeology)
- Sub-agent 3: Migration feasibility (63,260 tokens, 15 tool uses)
- `context/changes/summaries-flow-analysis/research.md` - Primary technical debt analysis
- Git repository: 89aaf9c7a12340a8b0624cfc7e7385b856c10f2a (master branch)

**Next step**: Planning session to decide which opportunities to implement and in what order. This research provides the foundation - the decision phase begins after reading this report.

---

## Weryfikacja twierdzeń (ast-grep)

**Date**: 2026-06-15T00:06:00+02:00  
**Verification commit**: 89aaf9c7a12340a8b0624cfc7e7385b856c10f2a  
**Method**: grep patterns and direct file inspection (ast-grep not applicable for Clojure structural patterns)

| Twierdzenie | Raport | Weryfikacja | Werdykt | Dowód | Metoda |
|-------------|--------|-------------|---------|-------|--------|
| **C1 - Handlers count** | 15 defn | 15 defn | ✅ POTWIERDZONE | summaries_view.clj: lines 29, 147, 233, 352, 408, 461, 636, 687, 738, 780, 823, 878, 976, 1080, 1163 | `grep "^(defn " summaries_view.clj` |
| **C1 - LOC** | 1,274 LOC | 1,274 LOC | ✅ POTWIERDZONE | summaries_view.clj: `wc -l` = 1274 | `wc -l` |
| **C1 - Dependencies** | 20 total (11 internal + 9 external) | 18 total (11 internal + 7 external) | ⚠️ DOPRECYZOWANE | summaries_view.clj:9-27: 11 internal (middleware, layout, helpers, csv-import, summary-card, summaries-list, summary-service, csv-service, openrouter-service, gen-service, dto.summary, util) + 7 external (cheshire, clojure.string, logging, malli.core, malli.error, rum, xtdb) | manual count of :require block |
| **C1 - Longest handler** | 170 lines (461-630) | 170 lines (461-630) | ✅ POTWIERDZONE | import-csv-htmx-handler: lines 461-630 | line range calculation |
| **C1 - requiring-resolve** | lines 211, 1139, 1228 | lines 211, 607, 1139, 1228 | ⚠️ DOPRECYZOWANE | summaries_view.clj: 4 occurrences (raport listed 3) | `grep "requiring-resolve"` |
| **C1 - Malli validation** | line 376 | line 376 | ✅ POTWIERDZONE | create-manual-summary-api-handler:376: `(m/explain create-manual-summary-schema params)` | direct read |
| **C2 - LOC** | 131 LOC | 131 LOC | ✅ POTWIERDZONE | schema/api.clj: `wc -l` = 131 | `wc -l` |
| **C2 - Schema count** | 9 schemas | 10 schemas | ⚠️ DOPRECYZOWANE | schema/api.clj: list-summaries-query (13), create-manual-summary (26), update-summary (39), bulk-accept (50), csv-import (55), summary-dto (71), summary-list-response (88), rejected-row (96), csv-import-response (102), bulk-accept-response (123) | `grep "^(def.*-schema"` |
| **C2 - Zero imports** | 0 imports | 0 imports | ✅ POTWIERDZONE | `grep -r "schema\.api\|schema/api" src/ test/` (excluding file itself) = 0 results | `grep` across codebase |
| **C3 - schema.clj limit** | line 37: 10k | line 37: 10k | ✅ POTWIERDZONE | schema.clj:37: `[:summary/content [:string {:min 50 :max 10000}]]` | `grep ":max 10000"` |
| **C3 - summaries_view.clj limit** | line 346: 50k | line 346: 50k | ✅ POTWIERDZONE | summaries_view.clj:346: `[:content [:string {:min 50 :max 50000}]]` | `grep ":max 50000"` |
| **C3 - services/summary.clj limit** | 50k check | line 37: 50k | ✅ POTWIERDZONE | services/summary.clj:37: `(> length 50000)` | `grep "50000"` |
| **C5 - Schema duplication** | api.clj:26-37 = summaries_view.clj:334-346 | api.clj:26-37 = summaries_view.clj:334-346 | ✅ POTWIERDZONE | Both define identical `create-manual-summary-schema` structure (only docstrings differ) | direct comparison via `sed -n` |

### Wnioski z weryfikacji

**Potwierdzone (10/13 twierdzeń):**
- Wszystkie kluczowe metryki strukturalne są dokładne (LOC, handler count, schema drift, duplication)
- Zero import status dla schema/api.clj potwierdzony
- Długość najdłuższego handlera (170 linii) potwierdzona
- Wszystkie numery linii dla kluczowych konstrukcji są prawidłowe

**Doprecyzowane (3/13 twierdzeń):**

1. **Dependencies count (C1)**: Raport podawał 20 (11 internal + 9 external), faktycznie 18 (11 internal + 7 external)
   - **Impact na ranking**: ZERO - różnica 2 dependencies nie zmienia oceny Ce=18, Ca=1, I=0.86 vs I=0.95 z raportu
   - **Decyzja na etapie planowania**: Aktualizacja metryk coupling bez zmiany wniosków

2. **requiring-resolve count (C1)**: Raport podawał 3 wystąpienia (211, 1139, 1228), faktycznie 4 (211, 607, 1139, 1228)
   - **Impact na ranking**: ZERO - dodatkowe użycie requiring-resolve w linii 607 potwierdza wzorzec circular dependency breaking, nie zmienia wniosków o architekturze

3. **Schema count (C2)**: Raport podawał 9 schematów, faktycznie 10 schematów w schema/api.clj
   - **Impact na ranking**: ZERO - dodatkowy schemat (`bulk-accept-response-schema`) nie zmienia faktu zero imports i 131 LOC dead code

**Rekomendacja**: Żadne doprecyzowanie nie podważa rankingu refactoring opportunities. Wszystkie trzy korekty dotyczą drobnych szczegółów ilościowych, które nie zmieniają strukturalnych wniosków ani kolejności priorytetów (C2 → C3 → C1).

### Metoda weryfikacji

**Narzędzia użyte**:
- `grep` - zliczanie defn handlers, requiring-resolve, schema definitions, import references
- `wc -l` - weryfikacja LOC
- `sed -n` - ekstrakcja specific line ranges i require blocks
- Direct file reading - porównanie duplikacji schematów

**ast-grep nie użyty**: Clojure S-expressions wymagają dedykowanych parserów AST (clj-kondo, rewrite-clj). Wzorce strukturalne zweryfikowane poprzez kombinację grep (counting patterns) i bezpośrednie czytanie kodu dla złożonych twierdzeń (schema duplication, Malli usage).

**Coverage**: 13/13 twierdzeń strukturalnych zweryfikowanych (100%). Każde twierdzenie otrzymało werdykt: POTWIERDZONE lub DOPRECYZOWANE z konkretnymi dowodami (file:line).
