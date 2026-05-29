---
project: Apriary Production Tracking
context_type: brownfield
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  frs_drafted: 10
  quality_check_status: accepted
created: 2026-05-29
updated: 2026-05-29
---

## Current System

**What exists:**
Apriary Summary MVP — a web application for automating apiary work summaries. Users can register, log in, import observations via CSV (currently as textarea paste), receive AI-generated summaries (currently mocked), and perform CRUD operations on summaries. The system tracks acceptance events via counters in generation records.

**Tech stack:**
- Clojure 1.12
- XTDB 1.24
- Biff framework
- Tailwind 4
- htmx for interactivity

**Current users:**
Small apiary owners who need to document hive inspection history.

**Pain / gap:**
The system tracks **what happened** (observations, summaries of hive work) but doesn't track **production metrics** — how much honey, pollen, or other bee products each hive yields. Without this data, beekeepers cannot identify top-performing hives or queen bees, making breeding decisions and hive management planning harder. The value of production tracking became clear after planning how the app would be used in practice.

**Must preserve:**
- Existing summary functionality (observation imports, AI generation when implemented, CRUD operations)
- All existing summary data and generation records
- Authentication system (registration, login, password recovery)
- Row-Level Security — users see only their own data
- Existing API contracts for summary endpoints
- Database schema for users, summaries, and generations

## Vision & Problem Statement

**Change category:** Significant feature — extends existing summaries

**What's changing:**
Add production tracking capability alongside the existing observation summary system. Users will be able to record harvest events (hive number, date, product type, quantity) and view ranked lists of top 5 and bottom 5 hives/queens based on production metrics. This enables beekeepers to make data-driven breeding and hive management decisions.

**Why now:**
The existing summary system captures qualitative observations but doesn't quantify hive performance. Production data is the missing piece that turns historical notes into actionable insights for hive selection and queen breeding planning.

## User & Persona

**Primary persona:**
Small apiary owners (same as existing users) — individuals managing 5-50 hives who need both qualitative summaries of hive inspections AND quantitative production metrics to improve their apiary over time.

**Role:**
Hobbyist or small-scale commercial beekeeper who performs their own inspections, harvests, and breeding decisions.

**Access model (current):**
- Email + password authentication
- Session-based login
- Row-Level Security ensures each user sees only their own summaries and production records
- No role separation (flat user model)

**Change to access model:**
No changes planned — production records will follow the same RLS rules as summaries.

## Access Control

**Current model:**
- Email + password authentication (session-based via Biff)
- Flat user model — no roles, no admin distinction
- Row-Level Security at middleware/query level ensures users see only their own data
- CSRF protection enabled

**Changes planned:**
No changes — current model preserved.

Production records will be scoped to the authenticated user via the same RLS pattern applied to summaries. Each production record will include a `user-id` field, and queries will filter by `(= user-id (:uid ctx))` per existing middleware convention.

## Success Criteria

### Primary

User can input production data via CSV-like textarea and see ranked lists of hives by product type.

**End-to-end flow:**
1. User logs in to existing Apriary app
2. User navigates to new "Products" section (separate nav link from "Summaries")
3. User sees textarea with placeholder example and existing products table below
4. User pastes CSV text:
   ```
   hive_number;date;product;quantity;metric
   A-01;23-11-2025;Pollen;1;kg
   A-02;23-11-2025;Honey;2;kg
   A-03;24-11-2025;Venom;2;ml
   ```
5. User submits form
6. System validates each row (format, required fields) and stores valid records
7. Products table updates to show new records (columns: hive_number, date, product, quantity, metric; sorted by date descending)
8. User navigates to "Rankings" view (separate section)
9. User sees top 5 and bottom 5 hives per product type (e.g., "Top 5 Honey Producers", "Bottom 5 Pollen Producers")
10. Rankings calculated as total quantity per hive per product type

### Secondary

Edit/delete individual product records — allows users to fix mistakes in imported data without re-importing entire batches.

### Guardrails

1. **Existing summaries functionality intact** — users can still import observations via CSV, view summaries, edit/accept summaries, and delete summaries. No regression in the observation → AI summary → acceptance workflow.

2. **RLS enforced on product records** — users see only their own production data. Product queries filter by `user-id` via existing middleware pattern. No cross-user data leakage.

3. **Shared components don't conflict** — CSV parsing logic, UI layout patterns, and middleware changes for Products do not break Summaries. Medium blast radius acknowledged: both features use textarea-based CSV input and similar validation patterns.

## Timeline & Constraints

**Delivery estimate:** 3 weeks of after-hours work

**Blast radius:** Medium risk
- New database schema for products (`:product` doc-type in XTDB)
- New routes: `/products`, `/products/rankings`
- New UI section with shared CSV parsing logic
- Potential conflicts: CSV validation, htmx patterns, Tailwind layout
- Isolated failure mode: if Products breaks, Summaries remain functional

**Must preserve:**
- Existing summary workflow and data
- Authentication and RLS
- API contracts for summary endpoints

## Functional Requirements

### Product Input

- FR-001: User can input production data via CSV format (hive_number;date;product;quantity;metric) in a textarea. Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "CSV textarea is the proven pattern from Summaries; reusing it is the right call." Resolution: kept; no counter-argument raised.

### Product Viewing

- FR-002: User can view a list of all their production records in a table showing hive_number, date, product, quantity, metric. Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "List view isn't needed if Rankings exist — users care about rankings, not raw record lists." Resolution: kept; users need to verify CSV import worked correctly, even if rankings are the primary value. Table provides import confirmation.

- FR-006: User can edit individual product records. Priority: nice-to-have. Change: new
  > Socrates: Counter-argument considered: "No counter-argument; CRUD is necessary even if not in first release." Resolution: kept as nice-to-have; users will make input mistakes.

- FR-007: User can delete individual product records. Priority: nice-to-have. Change: new
  > Socrates: Counter-argument considered: "No counter-argument; CRUD is necessary even if not in first release." Resolution: kept as nice-to-have.

### Rankings

- FR-003: User can navigate to a Rankings view. Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "Rankings are the core value proposition." Resolution: kept.

- FR-004: User can see top 5 hives per product type ranked by highest total quantity (all-time cumulative). Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "Rankings need date filters to be useful — all-time totals are misleading." Resolution: kept for MVP without date filters; all-time cumulative totals are sufficient to validate the workflow. Date range filtering deferred to v2.

- FR-005: User can see bottom 5 hives per product type ranked by lowest total quantity (all-time cumulative). Priority: must-have. Change: new
  > Socrates: Counter-argument considered: same as FR-004 (date filters). Resolution: kept for MVP; all-time is acceptable for first version.

### Preserved - Summaries

- FR-008: User can import observation summaries via CSV textarea. Priority: must-have. Change: preserved
- FR-009: User can view, edit, accept, and delete summaries (full CRUD). Priority: must-have. Change: preserved

### Preserved - Security

- FR-010: User sees only their own products and summaries (Row-Level Security enforced). Priority: must-have. Change: preserved

## User Stories

### US-01: Input and View Production Data

**Given** a logged-in beekeeper with existing hives  
**When** they navigate to the Products section and paste CSV text into the textarea:
```
hive_number;date;product;quantity;metric
A-01;23-11-2025;Pollen;1;kg
A-02;23-11-2025;Honey;2;kg
A-03;24-11-2025;Venom;2;ml
```
and submit the form  
**Then** the system validates each row, stores valid records, and displays them in a table sorted by date (newest first) with columns: hive_number, date, product, quantity, metric

### US-02: View Hive Rankings by Product

**Given** a user with production records across multiple hives and product types  
**When** they navigate to the Rankings view  
**Then** they see separate ranked lists for each product type (e.g., "Top 5 Honey Producers", "Bottom 5 Pollen Producers") showing hive numbers and total quantities, calculated as sum of quantity per hive per product type

## Business Logic

The system aggregates production records by hive and product type, then ranks hives in descending order of total quantity to surface the highest and lowest performers.

**Inputs (user-facing):**
- Production records containing: hive number (string identifier), date (DD-MM-YYYY format), product type (e.g., Honey, Pollen, Venom), quantity (numeric value), metric (unit like kg, ml)

**Output:**
- Top 5 hives per product type — the five hive identifiers with the highest cumulative quantity for that product
- Bottom 5 hives per product type — the five hive identifiers with the lowest cumulative quantity for that product

**How the user encounters it:**
After importing production data via CSV, the user navigates to the Rankings view and sees pre-calculated lists organized by product type. The ranking is purely prioritization — no recommendation or classification logic. The user uses these ranked lists to manually decide which hives/queens to prioritize for breeding or management decisions outside the application.

## Constraints & Preserved Behavior

**Existing integrations:**
- CSV parsing logic should be shared between Summaries and Products to avoid duplication. Both features use semicolon-delimited text input with header rows.

**Data migrations:**
- No migration required. Add new `:product` doc-type to XTDB schema alongside existing `:user`, `:summary`, and `:generation` doc-types.
- Existing user, summary, and generation data remains untouched.

**Backward compatibility:**
- Existing API routes for summaries (`/api/summaries/*`, `/api/generations/*`) must not change.
- Database schema for `:summary` and `:generation` doc-types cannot be modified.
- Products adds new routes (`/api/products`, `/api/products/rankings`) without affecting existing endpoints.

**Existing system behaviors that must not regress:**
- Summary CSV import workflow (textarea paste, validation, batch processing)
- Summary CRUD operations (create, read, update, delete)
- Acceptance tracking via generation counters
- Authentication and session management
- Row-Level Security enforcement on summaries

## Non-Functional Requirements

- **Correctness**: Ranking aggregation math must accurately sum quantities per hive per product type. No data loss, no rounding errors, no double-counting of records.

- **Browser compatibility**: Products feature works in modern browsers (same support matrix as existing Summaries feature — Chrome, Firefox, Safari, Edge).

- **Responsive UI**: Products table and Rankings view render correctly on desktop and tablet screen sizes (mobile optimization not required for MVP).

- **Data isolation (RLS)**: Users see only their own production records. Product queries filter by `user-id` via existing middleware pattern, preventing cross-user data leakage.

## Product Framing

**Product type:**
No change — existing web application (Biff + htmx + Tailwind).

**User base:**
No change — small apiary owners (5-50 hives). Products feature serves the same existing user segment; no expansion to commercial beekeepers or new personas.

**Timeline:**
- Delivery estimate: 3 weeks of after-hours work
- Hard deadline: None
- Work mode: After-hours only (evenings/weekends)

## Non-Goals

**Avoid: breeding recommendations or queen tracking**
The system ranks hives by production but does not track which queen is in which hive, does not recommend which queens to breed from, and does not classify hives into performance tiers. Users manually interpret rankings to make breeding decisions outside the application.

**Rationale:** Queen tracking would require a separate data model (queen registry, hive-queen assignments, queen lineage), plus UI for managing queen records. This is out of scope for MVP; the ranking data alone provides value without the queen dimension.

**Avoid: date filtering or time-series analysis**
MVP shows all-time cumulative totals only. No seasonal comparisons, no date range pickers (e.g., "show rankings for last 30 days"), no trend graphs or year-over-year analysis.

**Rationale:** Date filtering requires a more complex ranking calculation (aggregating within a time window) and UI controls (date pickers, preset ranges). All-time totals are simpler to implement and still provide actionable data for users starting to track production. Time-series features deferred to v2 after validating the core workflow.

**Avoid: pagination or advanced table features**
Products table displays all records without pagination, sorting controls, or search functionality. Users see all their production data in a single scrollable table sorted by date (newest first).

**Rationale:** For small apiaries (5-50 hives), even daily production records across a full season (~100-500 records) fit in a single table. Pagination and search add complexity without clear MVP value. If users report performance issues with large datasets, pagination can be added in v2.

