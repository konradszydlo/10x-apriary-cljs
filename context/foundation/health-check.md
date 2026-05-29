---
project: Apriary
assessed_at: 2026-05-29T17:35:00Z
health_status: healthy
context_type: brownfield
test_runner: cognitect.test-runner
ci_provider: GitHub Actions
audit_findings:
  critical: 0
  high: 0
  moderate: 0
  low: 0
category_a_fixes: 0
category_b_items: 2
---

## Executive Summary

Your Apriary project is in **healthy operational condition** and ready for agent-assisted development. All security vulnerabilities have been resolved, the test suite runs successfully (9 test files), and CI/CD is configured with comprehensive coverage.

**Key strengths:**

- ✅ **Zero security vulnerabilities** (recent fixes upgraded all vulnerable dependencies)
- ✅ Working test runner (cognitect.test-runner) with 9 test files
- ✅ Comprehensive GitHub Actions CI pipeline (lint, unit tests, E2E tests, coverage)
- ✅ clj-kondo linter configured and enforced in CI
- ✅ Coverage reporting configured (cloverage alias)
- ✅ Project structure follows Biff conventions
- ✅ CLAUDE.md instruction file exists with all recommended compensation strategies
- ✅ Essential configuration files present (.editorconfig, .env.example, .gitignore)

**No Category A fixes required.** The project is ready for agent work.

**Category B items (upcoming lessons):**

1. Dependency lockfile — Clojure CLI ecosystem limitation (addressed in infrastructure lesson)
2. Security scanning in CI/CD — configured locally, will be added to CI later per user decision

## Pre-Check: Dependencies & Security

### Lockfile Status

⚠️ **No lockfile detected**

Clojure CLI (`deps.edn`) does not generate a lockfile by default. Dependencies are resolved from Maven Central and git repositories on every build.

**Why it matters for agent workflows:**
The agent cannot reason about exact dependency versions when they float. A dependency update between builds can introduce breaking changes that invalidate the agent's understanding of the codebase.

**Current mitigation:**
- Biff pinned to exact git SHA: `fef3b44b` (tag v1.9.1)
- All :mvn/version dependencies use exact versions (no ranges)
- Security overrides explicitly pin patched versions

**Future enhancement (Category B):**
Clojure CLI added experimental lockfile support in recent versions. This will be explored in the infrastructure lesson.

**Effort:** Moderate (15-30 min to implement tools.deps lock file or alternative strategy)

**Priority:** Low for local development, addressed in upcoming infrastructure lesson

### Security Audit

**Tool:** clj-watson v6.1.0 with NVD API integration

**Findings:** ✅ **0 vulnerabilities**

Dependencies scanned: 123

**Recent security fixes (applied 2026-05-29):**

All vulnerabilities identified in the previous health check have been resolved via dependency overrides in `deps.edn`:

1. **CVE-2026-2332** (CRITICAL, CVSS 9.1) — org.eclipse.jetty.websocket → ✅ Fixed (upgraded to 12.1.8)
2. **CVE-2026-1605** (HIGH, CVSS 7.5) — org.eclipse.jetty.websocket → ✅ Fixed (upgraded to 12.1.8)
3. **CVE-2026-5795** (HIGH, CVSS 7.4) — org.eclipse.jetty.websocket → ✅ Fixed (upgraded to 12.1.8)
4. **CVE-2026-42198** (HIGH, CVSS 7.5) — org.postgresql/postgresql → ✅ Fixed (upgraded to 42.7.11)
5. **CVE-2025-67721** (HIGH, CVSS 7.5) — io.airlift/aircompressor → ✅ Fixed (upgraded to 2.0.3)
6. **CVE-2025-11143** (MEDIUM, CVSS 6.5) — org.eclipse.jetty components → ✅ Fixed (upgraded to 12.1.8)
7. **CVE-2024-36124** (MEDIUM, CVSS 5.3) — org.iq80.snappy/snappy → ✅ Fixed (upgraded to 0.5)

**Security overrides in deps.edn:**
```clojure
;; Security vulnerability fixes (dependency overrides)
org.eclipse.jetty.websocket/jetty-websocket-core-common {:mvn/version "12.1.8"}
org.eclipse.jetty.websocket/jetty-websocket-core-server {:mvn/version "12.1.8"}
org.eclipse.jetty.ee9.websocket/jetty-ee9-websocket-jetty-server {:mvn/version "12.1.8"}
org.eclipse.jetty/jetty-xml            {:mvn/version "12.1.8"}
org.eclipse.jetty/jetty-unixdomain-server {:mvn/version "12.1.8"}
org.eclipse.jetty/jetty-session        {:mvn/version "12.1.8"}
org.postgresql/postgresql  {:mvn/version "42.7.11"}
io.airlift/aircompressor   {:mvn/version "2.0.3"}
org.iq80.snappy/snappy     {:mvn/version "0.5"}
```

**Next action:** Continue running `clojure -M:clj-watson` locally before significant dependency updates. Security scanning in CI will be added later per user decision (currently runs locally only).

### Outdated Dependencies

**Status:** Not checked (no built-in staleness tool for Clojure CLI projects)

**Current approach:** Manual review using clj-watson and dependency documentation.

**Recommendation:** Periodically review dependencies:
- Check Biff releases: https://github.com/jacobobryant/biff/releases
- Check Clojure releases: https://clojure.org/releases/downloads
- Monitor security advisories via clj-watson

**Optional tooling (informational only):**
- [antq](https://github.com/liquidz/antq) — checks for outdated dependencies
- [depot](https://github.com/Olical/depot) — alternative outdated-dependency checker

## In-Check: Test Infrastructure & CI/CD

### Test Runner

**Detected:** ✅ cognitect.test-runner

**Status:** Working

**Test files found:** 9 test namespaces in `test/` directory

**Test execution:** Tests run via `clojure -M:test` alias. The runner auto-discovers namespaces matching `*-test$` pattern.

**Test breakdown:**
- Unit tests (excludes integration tests): `--namespace-regex "^com\.apriary\.(?!ui\.integration).*-test$"`
- E2E tests: `--namespace com.apriary.ui.integration-test`

**Coverage:** Test coverage reporting is configured via `:coverage` alias (cloverage 1.2.4), generating reports to `target/coverage/`.

**Verdict:** ✅ Healthy. The agent can verify its own changes by running the test suite.

### CI/CD Configuration

**Provider:** ✅ GitHub Actions

**Workflows detected:**
- `.github/workflows/pull-request.yml` — PR validation
- `.github/workflows/master-docker.yml` — Docker build and deployment

**Pull Request CI stages:**

| Stage | Status | Details |
|-------|--------|---------|
| **Lint** | ✅ | clj-kondo on `src` and `test` |
| **Unit Tests** | ✅ | Runs unit tests (excludes `com.apriary.ui.integration`) |
| **E2E Tests** | ✅ | Runs integration tests (`com.apriary.ui.integration-test`) |
| **Coverage** | ✅ | Generates coverage report (uploaded as artifact) |
| **Build** | ➖ | Not applicable (Clojure compiles on-demand; Docker build in master workflow) |
| **Type Check** | ➖ | Not applicable (Clojure is dynamically typed; Malli validates at runtime) |
| **Security Scan** | ⚠️ | **Not in CI** (clj-watson runs locally only, per user preference) |

**Caching:** ✅ Clojure dependencies cached via `~/.m2/repository`, `~/.gitlibs`, `~/.clojure` (cache key based on `deps.edn` hash).

**Job orchestration:** ✅ Sequential execution — lint runs first, unit tests and E2E tests run in parallel after lint passes.

**Verdict:** ✅ Strong CI coverage for code quality and testing. Security scanning is intentionally local-only at this stage (Category B — will be added to CI later per user decision).

### Configuration Files

**Essential configuration:**

| File | Status | Purpose |
|------|--------|---------|
| `.gitignore` | ✅ Present | Excludes build artifacts, secrets, IDE files |
| `.editorconfig` | ✅ Present | Consistent formatting (2-space indent for Clojure) |
| `.env.example` | ✅ Present | Documents environment variables |
| `CLAUDE.md` | ✅ Present | Project-specific AI instructions |

**Clojure-specific configuration:**

| File | Status | Purpose |
|------|--------|---------|
| `.clj-kondo/` | ✅ Present | Linter configuration |
| `clj-watson.properties` | ✅ Present | Security scanner configuration (gitignored) |

**Verdict:** ✅ All essential configuration files are present.

## Cross-Reference with Stack Assessment

Stack assessment (`context/foundation/stack-assessment.md`) identified:

- **Agent readiness:** ready-with-compensation
- **Gates passed:** 2/4 (convention-based ✓, well-documented for most components ✓)
- **Gates with gaps:** typed ~ (Malli compensates), training data mixed (Biff is niche)

**Recommended CLAUDE.md compensation strategies from stack assessment:**

1. ✅ **Type discipline section** — Present in CLAUDE.md (lines 11-24)
2. ✅ **Biff framework patterns** — Present in CLAUDE.md (lines 26-37)
3. ✅ **XTDB query patterns** — Present in CLAUDE.md (lines 41-77)
4. ✅ **Stack versions** — Present in CLAUDE.md (lines 3-9)

**Verdict:** All recommended compensation strategies from stack assessment are implemented in `CLAUDE.md`.

### Gap 1: Dynamic typing (compensated)

**Stack-assessment finding:** Clojure is dynamically typed; Malli schemas provide runtime validation at boundaries.

**Health-check verification:**
- ✅ Malli schemas present in `src/com/apriary/schema.clj`
- ✅ CLAUDE.md documents type discipline convention
- ✅ Tests validate schema enforcement

**Status:** Compensated via CLAUDE.md guidance and existing Malli infrastructure.

### Gap 2: Niche framework (compensated)

**Stack-assessment finding:** Biff has limited training data representation.

**Health-check verification:**
- ✅ CLAUDE.md contains Biff framework patterns section
- ✅ Working test suite provides code examples
- ✅ CLAUDE.md links to Biff documentation

**Status:** Compensated via CLAUDE.md documentation and test examples.

### Gap 3: No dependency lockfile (ecosystem limitation)

**Stack-assessment finding:** Not flagged by stack-assessment (infrastructure concern, not stack choice).

**Health-check finding:** Clojure CLI lacks standard lockfile mechanism.

**Current mitigation:**
- Exact git SHAs for git dependencies (Biff: `fef3b44b`)
- Exact :mvn/version specifications (no ranges)
- Security overrides explicitly pin patched versions

**Status:** Category B — acknowledged ecosystem limitation, addressed in upcoming infrastructure lesson.

### Gap 4: Security scanning in CI (user decision)

**Health-check finding:** clj-watson configured and working locally. Not in CI per user preference.

**Status:** Category B — user will add to CI later when ready.

## Overall Health Verdict: Healthy

**Status:** ✅ **HEALTHY**

**Rationale:**
- ✅ Zero security vulnerabilities (all CRITICAL and HIGH findings resolved)
- ✅ Test runner detected and working (9 test files)
- ✅ CI/CD configured with comprehensive coverage (lint, unit tests, E2E tests, coverage)
- ✅ All essential configuration files present
- ✅ All stack-assessment compensation strategies implemented in CLAUDE.md

**No Category A fixes required.** The project is ready for agent-assisted development.

## Category A — Fix Before Agent Work

**No findings.** All critical operational gaps have been resolved.

The security vulnerabilities identified in the previous health check (5 vulnerabilities: 1 CRITICAL, 2 HIGH, 1 MEDIUM in Jetty, PostgreSQL, aircompressor, and snappy) have been fixed via dependency overrides in `deps.edn`.

## Category B — Addressed in Upcoming Lessons

These items are expected gaps at this stage of the brownfield workflow and will be addressed in upcoming lessons:

### 1. Dependency lockfile

**Status:** No standard lockfile mechanism in Clojure CLI ecosystem.

**Impact:** Builds are not fully reproducible across environments. Different team members or CI runs may resolve slightly different transitive dependency versions.

**Mitigation:** Current dependencies use exact versions (Biff pinned to git SHA, all Maven coords use exact versions, security overrides explicitly pin versions).

**When:** Infrastructure lesson explores tools.deps experimental lock file feature or alternative strategies.

**Forward reference:** [Sprint Zero z Agentem: infrastruktura, walking skeleton i pierwszy deploy (M1L5)](https://platforma.przeprogramowani.pl/external/10xdevs-3/m1-l5)

### 2. Security scanning in CI/CD

**Status:** clj-watson configured locally with NVD API key, runs successfully. Intentionally not in CI per user decision.

**Current workflow:** Run `clojure -M:clj-watson` locally before significant dependency updates.

**When:** User decision — can be added to CI anytime by adding a security-scan job to `.github/workflows/pull-request.yml` with `NVD_API_KEY` secret configured in GitHub repository settings.

**Action when ready:**
```yaml
security-scan:
  name: Security Scan
  runs-on: ubuntu-latest
  needs: lint
  steps:
    - uses: actions/checkout@v6
    - uses: DeLaGuardo/setup-clojure@13.4
      with:
        cli: latest
    - name: Cache dependencies
      uses: actions/cache@v4
      with:
        path: |
          ~/.m2/repository
          ~/.gitlibs
          ~/.clojure
        key: clojure-deps-${{ hashFiles('deps.edn') }}
    - name: Create clj-watson properties
      run: echo "nvd.api.key=${{ secrets.NVD_API_KEY }}" > clj-watson.properties
    - run: clojure -M:clj-watson
```

## Summary

**Project:** Apriary

**Health:** ✅ Healthy

**Audit findings:** 0 CRITICAL, 0 HIGH (all vulnerabilities fixed via dependency overrides)

**Test runner:** ✅ cognitect.test-runner (9 test files, working)

**CI/CD:** ✅ GitHub Actions (lint ✓, unit tests ✓, E2E tests ✓, coverage ✓)

**Category A fixes:** 0 (ready for agent work)

**Category B items:** 2 (addressed in upcoming lessons)

**Agent readiness:** The project is ready for agent-assisted development. All critical operational gaps have been resolved, and agent instruction files (CLAUDE.md) include comprehensive compensation strategies for stack-specific gaps identified in the stack assessment.

**Next step:** Agent onboarding — both greenfield and brownfield paths converge with equivalent context artifacts.

═══════════════════════════════════════════════════════════
  HEALTH CHECK COMPLETE
═══════════════════════════════════════════════════════════

  Project:        Apriary
  Health:         healthy
  Audit findings: 0 CRITICAL, 0 HIGH
  Test runner:    cognitect.test-runner (9 test files)
  CI/CD:          GitHub Actions (lint, unit tests, E2E, coverage)
  Fixes:          0 recommended

  ► Report:       context/foundation/health-check.md
  ► Next:         Agent onboarding — both greenfield and brownfield
                  paths converge with equivalent context artifacts.
═══════════════════════════════════════════════════════════
