---
project: Apriary Production Tracking
version: 1
status: draft
created: 2026-06-01
updated: 2026-06-01
prd_version: 1
main_goal: speed
top_blocker: time
---

# Roadmap: Apriary Production Tracking

> Derived from `context/foundation/prd.md` (v1) + auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Slices below are listed in dependency order. The "At a glance" table is the index.

## Vision recap

The system tracks observation summaries but doesn't track production metrics — how much honey, pollen, or other bee products each hive yields. Without this data, beekeepers cannot identify top-performing hives or queen bees for breeding decisions. This change adds production tracking alongside the existing summary system: users import harvest data via CSV, view production records in a table, and see ranked lists of top 5 / bottom 5 hives per product type. The validation is that production tracking surfaces actionable insight for hive selection and breeding planning.

## North star

**S-01 + S-02: input products + view rankings** — the smallest end-to-end flow that proves production tracking delivers value. The north star is the first slice (or combination of slices) whose successful delivery would prove the core product hypothesis — placed as early as Prerequisites allow because everything else only matters if this works. US-01 (input) alone doesn't prove value; US-02 (rankings) alone has no data to rank. The validation is the complete capability: user pastes CSV → sees products table → navigates to rankings → sees top 5 / bottom 5 per product type.

## At a glance

| ID   | Change ID             | Outcome (user can …)                                                                  | Prerequisites | PRD refs                            | Status   |
| ---- | --------------------- | ------------------------------------------------------------------------------------- | ------------- | ----------------------------------- | -------- |
| S-01 | product-input-view    | input production data via CSV textarea and view all records in a table                | —             | FR-001, FR-002, US-01               | ready    |
| S-02 | product-rankings      | see top 5 / bottom 5 hives per product type in a Rankings view                        | S-01          | FR-003, FR-004, FR-005, US-02       | proposed |
| S-03 | product-edit-delete   | edit or delete individual product records                                             | S-01          | FR-006, FR-007                      | proposed |

## Baseline

What's already in place in the codebase as of 2026-06-01 (auto-researched + user-confirmed).
Slices below assume these are present and do NOT re-scaffold them.

- **Frontend:** present — Rum (Clojure React wrapper) + Tailwind 4 + htmx + Reitit routing → /src/com/apriary/ui.clj:9, /resources/tailwind.config.js, /src/com/apriary.clj:27-30
- **Backend / API:** present — Biff framework + Jetty + Reitit routes + comprehensive middleware pipeline → /src/com/apriary/pages/generations.clj:7, /src/com/apriary/middleware.clj:34-64
- **Data:** present — XTDB 1.24 + Malli schemas + fixtures (no migrations tooling) → /src/com/apriary/schema.clj:3-50, /resources/fixtures.edn
- **Auth:** present — Session-based auth (no OAuth), password reset tokens, route middleware → /src/com/apriary/auth.clj:93,119, /src/com/apriary/middleware.clj:14-19
- **Deploy / infra:** partial — Dockerfile + docker-compose + GitHub Actions CI/CD; no IaC → Dockerfile:1-5, .github/workflows/pull-request.yml, .github/workflows/master-docker.yml
- **Observability:** partial — clojure.tools.logging + SLF4J; no error tracking or metrics → deps.edn:20-23, resources/config.edn:29

## Foundations

(No foundations required. All layers needed for MVP are present or can be introduced inside vertical slices. The `main_goal: speed` + baseline completeness → no cross-cutting enablers.)

## Slices

### S-01: Input and View Production Data

- **Outcome:** User can input production data via CSV textarea (hive_number;date;product;quantity;metric) and view all their records in a table sorted by date (newest first).
- **Change ID:** product-input-view
- **PRD refs:** FR-001 (CSV input, must-have), FR-002 (view products table, must-have), US-01
- **Prerequisites:** —
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** First slice; introduces new `:product` doc-type to XTDB schema and shared CSV parsing logic. Medium blast radius — CSV parsing reused from Summaries pattern but new validation rules. If parsing breaks, both Products and Summaries could regress. Mitigation: test both workflows after schema + parsing changes land.
- **Status:** ready

### S-02: View Hive Rankings by Product

- **Outcome:** User can navigate to Rankings view and see top 5 / bottom 5 hives per product type ranked by all-time cumulative quantity.
- **Change ID:** product-rankings
- **PRD refs:** FR-003 (navigate to Rankings, must-have), FR-004 (top 5, must-have), FR-005 (bottom 5, must-have), US-02
- **Prerequisites:** S-01 (needs product records to rank)
- **Parallel with:** S-03
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Completes north star validation. Aggregation query (GROUP BY hive + product type, ORDER BY sum(quantity)) is standard but untested at scale. For MVP (5-50 hives, ~100-500 records), performance is not a concern. Sequenced after S-01 because rankings without data prove nothing.
- **Status:** proposed

### S-03: Edit and Delete Product Records

- **Outcome:** User can edit or delete individual product records to fix import mistakes.
- **Change ID:** product-edit-delete
- **PRD refs:** FR-006 (edit, nice-to-have), FR-007 (delete, nice-to-have)
- **Prerequisites:** S-01 (can't edit/delete what doesn't exist)
- **Parallel with:** S-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Nice-to-have CRUD completion. Follows existing Summary edit/delete patterns. Low risk. Sequenced after north star (S-01 + S-02) because `main_goal: speed` prioritizes must-haves; edit/delete can be deferred if time runs short.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID           | Suggested issue title                     | Ready for `/10x-plan` | Notes                                 |
| ---------- | ------------------- | ----------------------------------------- | --------------------- | ------------------------------------- |
| S-01       | product-input-view  | Add product input and view table          | yes                   | Run `/10x-plan product-input-view`    |
| S-02       | product-rankings    | Add rankings view for top/bottom hives    | no                    | Blocked by S-01                       |
| S-03       | product-edit-delete | Add edit/delete for product records       | no                    | Blocked by S-01; nice-to-have         |

## Open Roadmap Questions

(No open questions. PRD §Open Questions is empty. No new cross-cutting questions surfaced during roadmap decomposition.)

## Parked

- **Breeding recommendations or queen tracking** — Why parked: PRD §Non-Goals; queen tracking requires separate data model (queen registry, hive-queen assignments, lineage) out of scope for MVP. The ranking data alone provides value without the queen dimension.
- **Date filtering or time-series analysis** — Why parked: PRD §Non-Goals; all-time cumulative totals are sufficient to validate workflow. Date range pickers, seasonal comparisons, and trend graphs deferred to v2 after validating core workflow.
- **Pagination or advanced table features** — Why parked: PRD §Non-Goals; small apiaries (5-50 hives, ~100-500 records) fit in a single scrollable table. Pagination and search deferred until users report performance issues with large datasets.

## Done

(Empty on first generation. `/10x-archive` appends an entry here — and flips that item's `Status` to `done` — when a change whose `Change ID` matches the item is archived.)
