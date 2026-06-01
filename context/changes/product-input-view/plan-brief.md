# Product Input and View — Plan Brief

> Full plan: `context/changes/product-input-view/plan.md`

## What & Why

Add production tracking to the apiary app: users can paste CSV data (hive_number;date;product;quantity;metric) into a textarea, system validates and stores records, then displays them in an HTML table sorted by date. This is S-01 from the roadmap — the first half of the north star validation. Without this, beekeepers cannot identify top-performing hives or queens for breeding decisions; production data is the missing piece that turns observation notes into actionable insights.

## Starting Point

The app already has CSV import for observation summaries (`csv_import.clj` with semicolon parsing + row validation), XTDB with Malli schemas, RLS via `user-id` filtering, and server-side Rum/htmx UI patterns. Missing: `:product` schema, product-specific validation (date/quantity/metric rules), and a table display pattern (existing UI uses cards, not tables).

## Desired End State

User visits new `/products` page, pastes CSV, sees valid rows immediately in a sorted table (newest first, columns: hive_number, date, product, quantity, metric). Invalid rows listed separately with error reasons. Different users see only their own products (RLS enforced). Summaries CSV import still works (no regression from shared parsing).

## Key Decisions Made

| Decision                       | Choice                                | Why (1 sentence)                                                                  | Source |
| ------------------------------ | ------------------------------------- | --------------------------------------------------------------------------------- | ------ |
| Date validation                | Strict regex DD-MM-YYYY, allow empty  | Reuses Summaries pattern (no semantic check), consistent UX                       | Plan   |
| Quantity validation            | Positive integers only                | Simplest for MVP, avoids decimal precision issues in aggregation                  | Plan   |
| Metric validation              | Enum: kg, ml, g                       | Enforces consistency, prevents typos that would mislead rankings                  | Plan   |
| Table vs cards                 | Traditional HTML `<table>`            | Fits tabular data semantics, PRD says "table", easier to scan numeric data        | Plan   |
| Error handling                 | Toast + rejected-rows component       | Reuses Summaries pattern (OOB swaps), users see successes and failures            | Plan   |
| CSV parsing sharing            | Shared base parse, separate validation| DRY for parsing, flexibility for domain rules (quantity vs observation length)    | Plan   |
| Testing                        | Unit tests + manual UI checklist      | Covers critical logic (validation, RLS) with fast tests, UI tested manually       | Plan   |
| Navigation                     | Separate /products page               | PRD explicit: "separate nav link from Summaries"                                  | PRD    |

## Scope

**In scope:**
- `:product` Malli schema (hive_number, date, product, quantity, metric, user-id, timestamps)
- Product CSV validation (date DD-MM-YYYY regex, integer quantity > 0, metric enum kg|ml|g)
- Product service (create batch, list with RLS, sort by date descending)
- `/api/products-import` endpoint (parse, validate, store, htmx response with toast + rejected-rows)
- `/products` page with CSV form and HTML table
- "Products" nav link
- Unit tests for validation + service RLS
- Manual test checklist

**Out of scope:**
- Edit/delete product records (S-03, deferred)
- Rankings view (S-02, depends on this slice)
- Date filtering, time-series analysis (parked per roadmap)
- Pagination (PRD: small apiaries fit in one table)
- Decimal quantity support (decision: integers only)
- Semantic date validation (regex only, like Summaries)

## Architecture / Approach

**Data flow:**
1. User pastes CSV → POST `/api/products-import`
2. Handler: parse with `csv_import/parse-csv-string` (shared), validate rows with `product-csv/validate-product-row` (new)
3. Separate valid/rejected rows
4. Store valid rows: `product/create-products-batch` → XTDB batch transaction with `:product/user-id`
5. Return htmx response: main = refreshed table, OOB = toast + rejected-rows + clear form
6. Page load: `product/list-products` → query XTDB with RLS filter, sort by date desc → render HTML table

**Key components:**
- `schema.clj`: `:product` closed map with enum metric, int quantity constraints
- `product_csv.clj`: validation (date regex, quantity parse, metric enum)
- `product.clj`: service layer (create, list with RLS)
- `pages/products.clj`: handlers (page, import API)
- `ui/products.clj`: Rum components (CSV form, table, rejected-rows)

## Phases at a Glance

| Phase     | What it delivers                              | Key risk                                                      |
| --------- | --------------------------------------------- | ------------------------------------------------------------- |
| 1. Schema & Service | `:product` schema, CSV validation, service with RLS | Validation rules mismatch PRD (date/quantity/metric)          |
| 2. CSV Import Handler | `/api/products-import` endpoint, toast + rejected-rows | Shared CSV parsing breaks Summaries (regression)              |
| 3. Products Page & Table | `/products` page, nav link, HTML table UI | Table rendering pattern new (no existing reference)           |
| 4. Testing | Unit tests for validation + RLS, manual checklist | Insufficient RLS coverage → cross-user data leakage           |

**Prerequisites:** None — all layers present (XTDB, Biff, Rum, auth middleware)
**Estimated effort:** ~4-6 sessions across 4 phases (data layer testable first, UI last, manual verification each phase)

## Open Risks & Assumptions

- **Shared CSV parsing**: `csv_import.clj` changes could affect Summaries. Mitigation: Phase 4 regression check (Summaries import still works).
- **No semantic date validation**: Regex allows "31-02-2025" (invalid date). Acceptable for MVP per decision; v2 can add parsing validation if users report issues.
- **Integer-only quantity**: Users may harvest fractional amounts (1.5 kg). Acceptable per decision; v2 can relax if users request decimals.
- **Table rendering performance**: No pagination for MVP. Acceptable for 100-500 records per PRD; if users report slow loads with >1000 records, add pagination in v2.

## Success Criteria (Summary)

- User pastes valid CSV → sees products in table immediately, sorted by date (newest first)
- User pastes mixed valid/invalid CSV → valid rows appear, rejected rows listed with error reasons
- Different users see only their own products (RLS enforced)
- Summaries CSV import still works (no regression)
