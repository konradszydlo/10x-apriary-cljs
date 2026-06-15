<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Summaries Module Quick Wins

- **Plan**: `context/changes/summaries-refactor-opportunities/plan.md`
- **Mode**: Deep
- **Date**: 2026-06-15
- **Verdict**: SOUND (after fixes)
- **Findings**: 2 critical, 2 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | WARNING → PASS (after F3) |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | FAIL → PASS (after F1, F2) |
| Plan Completeness | WARNING → PASS (after F4) |

## Grounding

Grounding: 5/5 paths ✓, 5/5 symbols ✓, brief↔plan ✓

All key file paths verified:
- `src/com/apriary/schema/api.clj` — exists (131 LOC orphaned schemas)
- `src/com/apriary/schema.clj:37` — confirmed `:max 10000` limit
- `src/com/apriary/pages/summaries_view.clj:346` — confirmed `:max 50000` limit
- `src/com/apriary/services/csv_import.clj:135` — confirmed hardcoded `10000` check (plan missed this)
- `test/com/apriary/services/csv_import_test.clj` — exists with test coverage

## Findings

### F1 — CSV import validator drift missed (second drift point)

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Blind Spots
- **Location**: Phase 2 — Fix Schema Drift (C3)
- **Detail**: Plan changes schema.clj to 50k but misses the independent CSV validator in csv_import.clj:135-138 that hardcodes 10,000-char limit with error message "Maximum: 10,000 characters". After Phase 2, CSV import would still reject 10,001-char observations while manual entry accepts 50k — creates inconsistent UX and contradicts the "schema consistency established" success criterion. Evidence: csv_import.clj:95 comment "50-10,000 characters after trim", line 135 `(> (count trimmed-obs) 10000)`, line 138 error msg. Also schema/api.clj:63 doc comment "Each observation: 50-10,000 characters".
- **Fix A ⭐ Recommended**: Add Phase 2 step to update CSV validator + docs
  - Strength: Achieves true consistency (all paths accept 50k). Matches plan's "50k limit everywhere" end state. CSV tests already exist (csv_import_test.clj:385-404) so changes are verified.
  - Tradeoff: Adds 2 more file edits (csv_import.clj + schema/api.clj doc).
  - Confidence: HIGH — sub-agent verified exact line numbers; test coverage exists (csv_import_test.clj will fail if mismatch remains).
  - Blind spot: None significant. Test coverage enforces correctness.
- **Fix B**: Document CSV path as intentional exception (keep 10k for CSV)
  - Strength: Zero code change; avoids touching CSV import logic.
  - Tradeoff: Contradicts end-state claim "50k everywhere"; leaves inconsistent validation (manual=50k, CSV=10k); users confused why CSV rejects what UI accepts.
  - Confidence: LOW — inconsistency is confusing; no user benefit.
  - Blind spot: Why would CSV deserve a stricter limit? Plan offers no rationale.
- **Decision**: FIXED (via Fix A)
  - Applied Phase 2 Steps 4-5: Update CSV validator (csv_import.clj lines 95, 135, 138) and schema doc (schema/api.clj line 63)
  - Updated success criteria: Added CSV-specific grep commands (2.3-2.4)
  - Updated manual verification: 9 changes (was 5)

### F2 — Test will break silently (csv_import_test.clj drift check)

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 2 — Fix Schema Drift (C3), Step 6
- **Detail**: Plan says "Scan lines 385-420 for 10000 → 50000" but Phase 2 Step 6 description is vague on exactly WHICH test this is. Sub-agent found the test: "Observation length constraint matches CSV validator - maximum (10,000 chars)" at lines 385-404. If we fix F1 (CSV validator to 50k), this test's title becomes wrong ("maximum (10,000 chars)" vs actual 50k). Misleading test names hide drift later. Evidence: csv_import_test.clj:385 test title still says "10,000 chars" limit. If CSV validator changes to 50k, the test title is stale.
- **Fix**: In Phase 2 Step 6, explicitly list test title update
  - Strength: Prevents test title from lying about what it checks (10k vs 50k). Keeps test suite self-documenting.
  - Tradeoff: One more line edit (test title string).
  - Confidence: HIGH — trivial change, clear benefit (accuracy).
  - Blind spot: None significant.
- **Decision**: FIXED
  - Updated Step 6 to explicitly require test title update: "maximum (10,000 chars)" → "maximum (50,000 chars)"
  - Added to Changes list in Step 6

### F3 — Success criteria gap on CSV path consistency

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: End-State Alignment
- **Location**: Desired End State, Section 4 (Verification)
- **Detail**: Plan's success criteria say "schema drift eliminated" and verify with `grep ":max 10000" src/` returns zero, but this only catches Malli schemas. The CSV validator uses hardcoded integer comparison `(> count 10000)`, not `:max 10000`, so the grep misses it. Success criteria would pass even with F1 unfixed.
- **Fix**: Add CSV-specific grep to success criteria
  - Strength: Catches both Malli drift and hardcoded drift. Phase 2's verification (Step 2.2) becomes accurate.
  - Tradeoff: One more grep command in verification section.
  - Confidence: HIGH — sub-agent confirmed csv_import.clj uses `10000` not `:max 10000`, so existing grep won't catch it.
  - Blind spot: Other hardcoded limits might exist (didn't exhaustively search all integers), but csv_import.clj is the only CSV validator so likely complete.
- **Decision**: FIXED
  - Added Phase 2 success criteria steps 2.3-2.4: CSV validator grep checks
  - Updated manual verification to expect 9 changes (was 5)

### F4 — Vague "lines 385-420" in Phase 2 Step 6

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 2, Step 6 — Update Schema Drift Tests
- **Detail**: Step 6 says "scan lines 385-420 for 10000 → 50000" but doesn't name the test or explain what it's checking. Implementer has to read 35 lines to figure out which assertions to update. Sub-agent found it's one specific test ("Observation length constraint matches CSV validator") but the plan doesn't say so.
- **Fix**: Specify test name and exact changes in Step 6 description
  - Strength: Implementer knows exactly what to update without reading 35 lines of test code. Faster execution, less confusion.
  - Tradeoff: Slightly longer plan text (adds test name + 2-line summary).
  - Confidence: HIGH — sub-agent verified the exact test at lines 385-404.
  - Blind spot: None significant. This is just clarity.
- **Decision**: FIXED
  - Updated Step 6 **Contract** to name the specific test: "Observation length constraint matches CSV validator - maximum (10,000 chars)" at lines 385-404
  - Added explicit Changes list with test title update requirement

## Triage Summary

**All findings addressed in plan edits:**

- **F1 (CRITICAL)**: Applied Fix A — Added Phase 2 Steps 4-5 for CSV validator and doc updates
- **F2 (CRITICAL)**: Fixed — Step 6 now explicitly requires test title update
- **F3 (WARNING)**: Fixed — Added CSV-specific grep commands to success criteria
- **F4 (WARNING)**: Fixed — Step 6 now names the test and lists exact changes

**Changes made to plan:**

1. **Phase 2 Step 4** (NEW): Update CSV Import Validator (csv_import.clj lines 95, 135, 138)
2. **Phase 2 Step 5** (NEW): Update CSV Schema Documentation (schema/api.clj line 63)
3. **Phase 2 Step 6** (ENHANCED): Explicit test name, title update requirement, exact line ranges
4. **Phase 2 Success Criteria**: Added steps 2.3-2.4 for CSV-specific grep verification
5. **Phase 2 Manual Verification**: Updated to expect 9 changes (was 5), added consistency check
6. **Progress Section**: Updated Phase 2 with steps 2.3-2.4 (CSV checks), 2.8-2.9 (manual verification)

**Final verdict**: SOUND — Plan is now ready for implementation. All validation paths (schema, service, frontend, CSV) will consistently accept 50k. Hidden drift point identified and fixed before any code is written.
