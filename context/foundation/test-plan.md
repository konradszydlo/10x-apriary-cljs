# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan --refresh` when stale (see §8).
>
> Last updated: 2026-06-03

## 1. Strategy

Tests follow three non-negotiable principles for this project:

1. **Cost × signal.** The cheapest test that gives a real signal for the risk wins. Do not promote to e2e because e2e "feels safer." Do not put a vision model on top of a deterministic visual diff that already catches the regression.
2. **User concerns are first-class evidence.** Risks anchored in "the team is worried about X, and the failure would surface somewhere in \<area\>" carry the same weight as PRD lines or hot-spot data.
3. **Risks are scenarios, not code locations.** This plan documents *what could fail* and *why we believe it's likely* — drawn from documents, interview, and codebase *signal* (churn, structure, test base). It does NOT claim to know which line owns the failure. That knowledge is produced by `/10x-research` during each rollout phase. If the plan and research disagree about where the failure lives, research is the ground truth.

Hot-spot scope used for likelihood weighting: `src/` (20 commits/30d, churn concentrated in `src/com/apriary` product-related modules).

## 2. Risk Map

The top failure scenarios this project must protect against, ordered by risk = impact × likelihood. Risks are failure scenarios in user / business terms, not test names. The Source column cites the *evidence that surfaced this risk* — never a specific file as "where the failure lives" (that is research's job, see §1 principle #3).

| # | Risk (failure scenario) | Impact | Likelihood | Source (evidence — not anchor) |
|---|------------------------|--------|------------|--------------------------------|
| 1 | Silent CSV import failure — user enters production data, sees success feedback, but data doesn't persist to XTDB | High | Medium | User interview Q1 + PRD §Guardrails lines 86-92 |
| 2 | Frontend-backend contract drift — FE sends product record with wrong shape, BE rejects or stores corrupted data | High | Medium | User interview Q2 + hot-spot `src/com/apriary/pages/` (3 commits/30d), `src/com/apriary/ui/products.clj` (2 commits/30d) |
| 3 | CSV parsing regression breaks existing summaries feature — shared parsing logic change silently breaks summaries import | High | Medium-High | User interview Q3 + PRD §Guardrails line 88 + hot-spot `src/com/apriary/services/product_csv.clj` (2 commits/30d) |
| 4 | RLS bypass on product records — user queries return another user's production data | High | Medium | PRD §Guardrails line 87 + PRD §Access Control Changes lines 197-200 + user emphasis on authorization |
| 5 | Ranking calculation incorrect — top/bottom 5 hives show wrong totals or wrong hive ordering | Medium-High | Medium | PRD §Business Logic Changes lines 178-186 + PRD US-02 + Roadmap S-02 |
| 6 | Invalid CSV row accepted — malformed data (missing fields, wrong date format, negative quantity) gets stored and corrupts rankings | Medium | Medium | PRD US-01 step 6 + User interview Q3 |
| 7 | XSS or injection via CSV input — malicious hive_number/product names escape validation and execute in UI or corrupt queries | High | Low-Medium | Abuse scenario (untrusted input) + PRD §Tech Stack (htmx renders user data) + CLAUDE.md Malli validation |

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research` must ground | Likely cheapest layer | Anti-pattern to avoid |
|------|-----------------------------|----------------|--------------------------------------|-----------------------|-----------------------|
| #1 | CSV import round-trip: paste valid CSV → submit → query XTDB directly → verify records exist with correct user-id | "200 response means data was stored" — verify actual XTDB write | POST handler, XTDB transaction shape, Malli validation, error surfacing | Integration test (in-memory XTDB + handler) | Happy-path-only; mocking XTDB write without proving persistence |
| #2 | FE request payload matches BE Malli schema AND BE response matches FE rendering | "Schema validation passes means contract is correct" — verify FE sends schema-valid shape in real usage | API route, request/response Malli schemas, FE POST body construction, error translation | Contract test OR integration test | Copying FE request from BE code; validating schema without checking FE sends it |
| #3 | Summaries CSV import still works after products CSV changes | "Shared parsing logic is obvious" — verify both use same parser or prove isolation | Where CSV parsing lives, how summaries import works, fixtures | Integration test (import summaries after products changes) | Testing products parsing only; assuming isolation when code shares logic |
| #4 | User A cannot query user B's product records | "Middleware enforces RLS automatically" — verify queries explicitly filter by user-id | XTDB query shape, session ctx, existing RLS pattern | Integration test (multi-user fixture) | Mocking user-id; checking one user without attempting cross-user access |
| #5 | Rankings show correct top/bottom 5 for known dataset | "Aggregation is obvious" — verify tie-breaking, zero-quantity, <5 hives, multi-product | Aggregation query, tie-breaking, null handling | Unit test (pure ranking logic) OR integration test | Asserting current output; checking only happy path |
| #6 | Invalid CSV rows rejected with clear errors, valid rows still process | "Malli catches everything" — verify field-level validation, partial-batch handling | Validation rules, Malli schemas, batch error surfacing | Unit test (CSV parse + validation) | Checking schema without parse edge cases; not verifying partial-batch |
| #7 | Malicious input sanitized/escaped before UI rendering and XTDB queries | "Malli prevents injection" — verify Malli validates types but may not sanitize; check htmx rendering | Field rendering (htmx/Rum), Malli constraints, XTDB query construction | Unit test (sanitization) + integration test (rendering) | Checking Malli passes without verifying rendering; assuming htmx auto-escapes |

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder via `/10x-new`. Status moves left-to-right through the values below; the orchestrator updates Status as artifacts appear on disk.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|-----------|-----------------|---------------|-----------|--------|---------------|
| 1 | Critical-path coverage | Defend core CSV import → rankings flow at the cheapest layer | #1, #2, #5, #6 | Integration (in-memory XTDB + handlers), unit (parsing, ranking) | change opened | context/changes/testing-critical-path-coverage |
| 2 | Cross-feature regression guard | Prove summaries CSV import still works after products changes | #3 | Integration (summaries import) | not started | — |
| 3 | Security hardening | Verify RLS isolation and input sanitization | #4, #7 | Integration (multi-user RLS, XSS rendering) | not started | — |
| 4 | Quality-gates wiring | Lock the floor: coverage gate + CI (BE tests only) | — | CI config (GitHub Actions + coverage) | not started | — |

## 4. Stack

The classic test base for this project. Recommendations in this section are grounded in local manifests/configs. No docs or search MCPs (Context7, Exa.ai) are available in the current session; stack guidance relies on CLAUDE.md + deps.edn.

| Layer | Tool | Version | Notes |
|-------|------|---------|-------|
| unit + integration | cognitect.test-runner | v0.5.1 | Configured in deps.edn `:test` alias; meaningful test suite (10+ files) |
| Coverage | cloverage | 1.2.4 | Configured in deps.edn `:coverage` alias with codecov output |
| Security scanning | clj-watson | v6.1.0 | Configured in `:clj-watson` alias |
| e2e | none yet | — | Not needed for MVP — Phase 1 covers critical flows via integration tests; reassess if complex UI flows land |

**Stack grounding tools (current session):**
- Docs: none — no Context7 or framework docs MCP available; Biff v1.9.0 referenced via CLAUDE.md; checked: 2026-06-03
- Search: none — no Exa.ai or web search MCP available; checked: 2026-06-03
- Runtime/browser: none — no Playwright MCP or browser tool available; not needed (user excluded "FE testing in CI" per interview Q5); checked: 2026-06-03
- Provider/platform: none — no GitHub/Cloudflare/Supabase MCP available; checked: 2026-06-03

## 5. Quality Gates

The full set of gates that must pass before a change reaches production. "Required for §3 Phase N" means the gate is enforced once that rollout phase lands; before that, the gate is `planned`.

| Gate | Where | Required? | Catches |
|------|-------|-----------|---------|
| lint (clj-kondo) | local + CI | required | syntactic drift, unused vars, arity mismatches |
| unit + integration | local + CI | required after §3 Phase 1 | logic regressions, contract drift, RLS bypass |
| coverage gate | CI on PR | required after §3 Phase 4 | floor regression (target TBD by Phase 1 baseline) |
| security scan (clj-watson) | CI on PR | required | vulnerable dependencies |
| FE tests | local only | recommended (not in CI per user interview Q5) | UI contract drift, rendering regressions |

## 6. Cookbook Patterns

How to add new tests in this project. Each sub-section is filled in once the relevant rollout phase ships; before that, the sub-section reads "TBD — see §3 Phase N."

### 6.1 Adding a unit test

TBD — see §3 Phase 1 (CSV parsing validation, ranking calculation).

### 6.2 Adding an integration test

TBD — see §3 Phase 1 (CSV import round-trip with in-memory XTDB, contract tests).

### 6.3 Adding a multi-user RLS test

TBD — see §3 Phase 3 (verify user A cannot access user B's product records).

### 6.4 Adding a test for shared CSV parsing

TBD — see §3 Phase 2 (verify summaries import still works after products parsing changes).

### 6.5 Per-rollout-phase notes

(Optional. After each phase lands, /10x-implement appends a 2-3 line note here capturing anything surprising the rollout phase taught.)

## 7. What We Deliberately Don't Test

Exclusions agreed during the rollout (Phase 2 interview, Q5). Future contributors should respect these unless the underlying assumption changes.

- **Frontend tests in CI** — local/manual FE testing is acceptable for this project's scale and timeline. Re-evaluate if the team grows beyond solo development or if contract-drift incidents spike. (Source: Phase 2 interview Q5.)

## 8. Freshness Ledger

- Strategy (§1–§5) last reviewed: 2026-06-03
- Stack versions last verified: 2026-06-03
- AI-native tool references last verified: N/A (no AI-native layer in this rollout)

Refresh (`/10x-test-plan --refresh`) when:

- a new top-3 risk surfaces from the roadmap or archive,
- a recommended tool's `checked:` date is older than three months,
- the project's tech stack changes (new framework, new test runner),
- §7 negative-space no longer matches what the team believes.
