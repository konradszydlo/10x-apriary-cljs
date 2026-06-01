# Product Input and View Implementation Plan

## Overview

Add production tracking capability: users can paste CSV data (hive_number;date;product;quantity;metric) into a textarea, system validates each row, stores valid records in XTDB, and displays all products in an HTML table sorted by date (newest first). This is S-01 from the roadmap — the first half of the north star validation that proves production tracking delivers value.

## Current State Analysis

**Existing patterns to follow:**
- **CSV import**: Summaries feature has `csv_import.clj` with semicolon-delimited parsing, row-by-row validation, batch XTDB transactions
- **XTDB schema**: Closed Malli maps in `schema.clj` with `:xt/id`, `:<entity>/id`, `:<entity>/user-id`, timestamps
- **RLS enforcement**: `(:uid session)` extracted in handlers, passed to service layer, filtered in XTDB queries via `:where` clause
- **UI pattern**: Server-side Rum/Hiccup rendering, htmx for form submissions, Tailwind styling
- **Error handling**: Summaries shows success toast + rejected-rows component via htmx OOB swaps

**What's missing:**
- `:product` entity schema in `schema.clj`
- Product-specific CSV validation (date DD-MM-YYYY, integer quantity, enum metric)
- Product service layer (create, list with RLS)
- `/products` page with CSV form and HTML table
- Product routes in module definition

## Desired End State

After implementation:
1. User visits `/products` page (new nav link)
2. User pastes CSV with format: `hive_number;date;product;quantity;metric`
3. System validates: date is DD-MM-YYYY or empty, quantity is positive integer, metric is kg|ml|g
4. Valid rows stored in XTDB with `:product/user-id` for RLS
5. Table displays all user's products sorted by date descending (columns: hive_number, date, product, quantity, metric)
6. Rejected rows shown in separate component below table
7. Success toast confirms import count

**Verification:**
- Paste valid CSV → see records in table immediately
- Paste mixed valid/invalid CSV → valid rows appear, rejected rows listed with reasons
- Reload page → products persist, still sorted by date
- Different user → sees only their own products (RLS enforced)

### Key Discoveries:

- **CSV parsing reuse**: `csv_import.clj:19-66` provides `parse-csv-string` — returns `[:ok {:headers [...] :rows [...]}]` or `[:error {...}]`
- **Validation pattern**: Summaries has `validate-csv-row` returning `[:valid {...}]` or `[:invalid "reason"]` — we'll create `validate-product-row` following same structure
- **Batch transaction**: Summaries uses `(mapv (fn [entity] [:xtdb.api/put entity]) entities)` then `(xt/submit-tx node tx-ops)`
- **Table rendering**: No existing table pattern — Summaries uses cards (`summaries-list.clj`), we'll create new HTML `<table>` component
- **RLS pattern from summary_test.clj**: Query pattern is `{:find '[?p] :where [['?p :product/user-id user-id] ...]}`, post-fetch verification is `(not= (:product/user-id entity) user-id)` returns NOT_FOUND

## What We're NOT Doing

- Edit/delete product records (S-03, deferred)
- Rankings view (S-02, depends on this slice)
- Date filtering or time-series analysis (parked per roadmap)
- Pagination (PRD: small apiaries fit in one scrollable table)
- Decimal quantity support (decision: integers only)
- Free-form metric field (decision: enum kg|ml|g)
- Semantic date validation (decision: regex only, like Summaries)

## Implementation Approach

**Phased vertical slices:**
1. Schema + service (data layer complete, testable)
2. CSV import handler (integration layer, toast + rejected-rows response)
3. Products page + table UI (presentation layer, manual testing)
4. Tests (validation rules, RLS enforcement)

**Key constraints:**
- Reuse `csv_import.clj` base parsing to avoid duplication
- Product validation is separate function (`validate-product-row`) — domain-specific rules without coupling to Summaries
- Metric enum: `kg`, `ml`, `g` (hardcoded, no config needed)
- Table uses traditional HTML `<table>` (new pattern, not card-based)

---

## Phase 1: Schema & Service Layer

### Overview

Define `:product` Malli schema, create product service with CSV validation (date regex, integer quantity, enum metric), XTDB storage with RLS enforcement.

### Changes Required:

#### 1. XTDB Schema for Product

**File**: `src/com/apriary/schema.clj`

**Intent**: Add `:product` entity schema following existing closed-map pattern. Product records store production events with user ownership, matching the CSV format (hive_number, date, product type, quantity, metric).

**Contract**: New schema definitions after existing entities (around line 50):

```clojure
:product/id :uuid
:product [:map {:closed true}
          [:xt/id                 :uuid]
          [:product/id            :uuid]
          [:product/user-id       :uuid]
          [:product/hive-number   :string]
          [:product/date          [:maybe :string]]  ; DD-MM-YYYY format or nil
          [:product/product       :string]           ; e.g., "Honey", "Pollen"
          [:product/quantity      [:int {:min 1}]]   ; Positive integer
          [:product/metric        [:enum "kg" "ml" "g"]]
          [:product/created-at    inst?]
          [:product/updated-at    inst?]]
```

#### 2. Product CSV Validation

**File**: `src/com/apriary/services/product_csv.clj` (new)

**Intent**: Validate product CSV rows with domain-specific rules: date DD-MM-YYYY regex (optional), quantity positive integer, metric enum kg|ml|g. Returns `[:valid {...}]` or `[:invalid "reason"]` following Summaries' validation pattern.

**Contract**: Public function `validate-product-row`:

```clojure
(ns com.apriary.services.product-csv
  (:require [clojure.string :as str]))

(defn validate-product-row
  "Validate a single product CSV row.
   Returns [:valid {:hive-number ... :date ... :product ... :quantity ... :metric ...}]
   or [:invalid \"reason\"]."
  [row headers]
  ...)
```

Validation rules:
- `hive_number`: required, non-empty after trim
- `date`: optional (nil if blank), must match `^\d{2}-\d{2}-\d{4}$` if provided
- `product`: required, non-empty after trim
- `quantity`: required, must parse as integer > 0
- `metric`: required, must be exactly "kg", "ml", or "g" (case-sensitive)

#### 3. Product Service

**File**: `src/com/apriary/services/product.clj` (new)

**Intent**: Create product service following existing service pattern (`summary.clj`, `generation.clj`). Provides `create-products-batch` (bulk insert with RLS) and `list-products` (query with RLS filtering, sorted by date descending).

**Contract**:

```clojure
(ns com.apriary.services.product
  (:require [xtdb.api :as xt]))

(defn create-products-batch
  "Create multiple product records in a single transaction.
   Returns [:ok {:count N}] or [:error {...}]."
  [node user-id products]
  ...)

(defn list-products
  "List all products for a user, sorted by date descending.
   Returns [:ok {:products [...]}] or [:error {...}]."
  [db user-id]
  ...)
```

RLS enforcement:
- `create-products-batch`: Set `:product/user-id` to authenticated `user-id` for all entities
- `list-products`: Query with `:where [['?p :product/user-id user-id] ...]`

### Success Criteria:

#### Automated Verification:

- Schema validation passes: `clj -M:dev test :only com.apriary.schema-test` (after adding schema test case)
- Product CSV validation unit tests pass: `clj -M:dev test :only com.apriary.services.product-csv-test`
- Product service unit tests pass: `clj -M:dev test :only com.apriary.services.product-test`
- Linting passes: `clj -M:dev lint`

#### Manual Verification:

- REPL test: Create product entities via `create-products-batch`, verify `:product/user-id` is set
- REPL test: Query products via `list-products`, verify different `user-id` returns empty list (RLS)
- REPL test: Validate product row with invalid date → returns `[:invalid "..."]`
- REPL test: Validate product row with metric "liter" → returns `[:invalid "..."]` (not in enum)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual REPL confirmation that RLS and validation work correctly before proceeding to the next phase.

---

## Phase 2: CSV Import Handler

### Overview

Create `/api/products-import` endpoint that accepts CSV text, reuses base CSV parsing from `csv_import.clj`, validates rows with `validate-product-row`, stores via `create-products-batch`, and returns htmx response with toast + rejected-rows component.

### Changes Required:

#### 1. Products Import Handler

**File**: `src/com/apriary/pages/products.clj` (new)

**Intent**: Handle CSV import POST request. Extract CSV from form params, parse with `csv_import/parse-csv-string`, validate each row with `product-csv/validate-product-row`, separate valid/rejected rows, store valid rows via `product/create-products-batch`, return htmx response with success toast and rejected-rows component.

**Contract**: Handler function following Summaries pattern (`summaries_view.clj:461-630`):

```clojure
(ns com.apriary.pages.products
  (:require [com.apriary.services.csv-import :as csv-import]
            [com.apriary.services.product-csv :as product-csv]
            [com.apriary.services.product :as product-service]
            [com.apriary.ui.layout :as layout]
            [com.apriary.middleware :as mid]
            [ring.middleware.anti-forgery :as csrf]))

(defn import-products-handler
  "Handle CSV import of products. Validates, stores, returns htmx response."
  [{:keys [session biff/node params] :as ctx}]
  ...)
```

Response structure (htmx):
- Main content: refreshed products table
- OOB swap for success toast: `[:div {:hx-swap-oob "afterbegin:#toast-container"} ...]`
- OOB swap for rejected rows (if any): `[:div {:hx-swap-oob "innerHTML:#rejected-rows"} ...]`
- OOB swap to clear form: `[:div {:hx-swap-oob "innerHTML:#csv-form"} ...]`

#### 2. Route Definition

**File**: `src/com/apriary/pages/products.clj`

**Intent**: Define module with routes for products page and import API endpoint, protected by `wrap-signed-in` middleware.

**Contract**:

```clojure
(def module
  {:routes [["/products" {:middleware [mid/wrap-signed-in]}
             ["" {:get products-page-handler}]]]
   :api-routes [["/api/products-import" {:middleware [mid/wrap-signed-in]
                                          :post import-products-handler}]]})
```

#### 3. Rejected Rows Component

**File**: `src/com/apriary/ui/products.clj` (new)

**Intent**: Render rejected rows from CSV import as a dismissible alert component, following Summaries rejected-rows pattern. Shows row number and validation error message for each rejected row.

**Contract**:

```clojure
(defn rejected-rows-component
  "Render rejected CSV rows with validation errors."
  [rejected-rows]
  [:div#rejected-rows.mt-4
   (when (seq rejected-rows)
     [:div.bg-red-50.border.border-red-200.rounded.p-4
      [:h3.text-red-800.font-semibold "Some rows were rejected:"]
      [:ul.mt-2.space-y-1
       (for [{:keys [row-number reason]} rejected-rows]
         [:li.text-sm.text-red-700
          (str "Row " row-number ": " reason)])]])])
```

### Success Criteria:

#### Automated Verification:

- Routes register correctly: server starts without errors
- Integration test: POST valid CSV to `/api/products-import` → 200 response
- Integration test: POST invalid CSV → 200 with rejected-rows in response
- Linting passes: `clj -M:dev lint`

#### Manual Verification:

- Submit valid CSV via form → success toast appears, table refreshes with new records
- Submit CSV with mixed valid/invalid rows → valid rows appear in table, rejected rows listed below
- Check XTDB directly → products have correct `:product/user-id`
- Sign in as different user → cannot see other user's products (RLS)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation that CSV import + validation + RLS work end-to-end before proceeding to the UI phase.

---

## Phase 3: Products Page & Table UI

### Overview

Create `/products` page with navigation link, CSV import form (htmx submission), and HTML table displaying products sorted by date descending (newest first).

### Changes Required:

#### 1. Products Page Handler

**File**: `src/com/apriary/pages/products.clj`

**Intent**: Fetch user's products via `product/list-products`, render page with CSV form and products table using Rum layout. Returns Ring response with `app-page` layout.

**Contract**:

```clojure
(defn products-page-handler
  "Render products page with CSV form and table."
  [{:keys [session biff/db] :as ctx}]
  (let [user-id (:uid session)
        [status result] (product-service/list-products db user-id)
        products (:products result [])]
    {:status 200
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (layout/app-page
             ctx
             [:div.max-w-6xl.mx-auto.p-6
              [:h1.text-2xl.font-bold.mb-6 "Production Tracking"]
              (products-ui/csv-form)
              (products-ui/rejected-rows-component [])
              (products-ui/products-table products)]))}))
```

#### 2. CSV Form Component

**File**: `src/com/apriary/ui/products.clj`

**Intent**: Render CSV import form with textarea, placeholder example, and htmx POST to `/api/products-import`. Follows Summaries CSV form pattern (`csv-import.clj:34-64`).

**Contract**:

```clojure
(defn csv-form
  "Render CSV import form with htmx submission."
  []
  [:div#csv-form.mb-6
   [:form {:hx-post "/api/products-import"
           :hx-target "#products-table"
           :hx-swap "outerHTML"
           :hx-indicator "#csv-loading"}
    [:label.block.text-sm.font-medium.mb-2 {:for "csv-input"}
     "Paste CSV data (hive_number;date;product;quantity;metric)"]
    [:textarea#csv-input.w-full.font-mono.text-sm.border.rounded.p-3.resize-y
     {:name "csv"
      :rows 8
      :required true
      :placeholder "hive_number;date;product;quantity;metric\nA-01;23-11-2025;Honey;5;kg\nA-02;24-11-2025;Pollen;2;kg"}]
    [:button.mt-3.bg-blue-600.text-white.px-4.py-2.rounded.hover:bg-blue-700
     {:type "submit"}
     "Import Products"]
    [:div#csv-loading.htmx-indicator.ml-3.inline-block
     "Importing..."]]])
```

#### 3. Products Table Component

**File**: `src/com/apriary/ui/products.clj`

**Intent**: Render HTML table with products sorted by date descending. Columns: hive_number, date, product, quantity, metric. New pattern (not card-based) — uses semantic HTML `<table>` with Tailwind styling.

**Contract**:

```clojure
(defn products-table
  "Render products as HTML table, sorted by date descending (newest first)."
  [products]
  [:div#products-table.mt-6
   (if (seq products)
     [:table.min-w-full.border.border-gray-300
      [:thead.bg-gray-100
       [:tr
        [:th.border.px-4.py-2.text-left "Hive Number"]
        [:th.border.px-4.py-2.text-left "Date"]
        [:th.border.px-4.py-2.text-left "Product"]
        [:th.border.px-4.py-2.text-right "Quantity"]
        [:th.border.px-4.py-2.text-left "Metric"]]]
      [:tbody
       (for [product (sort-by :product/date #(compare %2 %1) products)]
         [:tr.hover:bg-gray-50
          [:td.border.px-4.py-2 (:product/hive-number product)]
          [:td.border.px-4.py-2 (or (:product/date product) "-")]
          [:td.border.px-4.py-2 (:product/product product)]
          [:td.border.px-4.py-2.text-right (:product/quantity product)]
          [:td.border.px-4.py-2 (:product/metric product)]])]]
     [:p.text-gray-500.italic "No products yet. Import CSV data above to get started."])])
```

Sorting: `(sort-by :product/date #(compare %2 %1) products)` — reversed comparator for descending order, newest first.

#### 4. Navigation Link

**File**: `src/com/apriary/ui/header.clj`

**Intent**: Add "Products" link to navigation header alongside "Summaries". Uses htmx-boost for smooth navigation.

**Contract**: Insert after Summaries link (around line 55):

```clojure
[:a.text-white.hover:underline.px-3.py-2
 {:href "/products"
  :hx-boost "true"}
 "Products"]
```

### Success Criteria:

#### Automated Verification:

- Server starts without errors: `clj -M:dev dev`
- Navigation link renders: inspect page source for `/products` href
- Linting passes: `clj -M:dev lint`

#### Manual Verification:

- Visit `/products` page → see CSV form and empty state message
- Paste valid CSV → submit → table appears with records sorted by date (newest first)
- Reload page → products persist, still sorted correctly
- Check responsive layout on tablet screen size → table scrolls horizontally if needed
- Click "Products" nav link → smooth navigation (htmx boost)
- Verify table columns match CSV: hive_number, date, product, quantity, metric

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation that the full UI flow works (nav → form → import → table display) before proceeding to tests.

---

## Phase 4: Testing

### Overview

Write unit tests for validation rules and product service RLS enforcement. Manual testing checklist for full UI flow.

### Changes Required:

#### 1. Product CSV Validation Tests

**File**: `test/com/apriary/services/product_csv_test.clj` (new)

**Intent**: Test `validate-product-row` function with valid and invalid inputs across all validation rules. Follows pattern from `csv_import_test.clj`.

**Contract**: Test cases covering:
- Valid row: all fields present and correct
- Missing hive_number → `[:invalid "..."]`
- Invalid date format (DD/MM/YYYY with slashes) → `[:invalid "..."]`
- Empty date → `[:valid {...}]` with `:date nil`
- Quantity = 0 → `[:invalid "..."]`
- Quantity = -5 → `[:invalid "..."]`
- Quantity = "abc" → `[:invalid "..."]`
- Metric = "liter" → `[:invalid "..."]` (not in enum)
- Metric = "kg" → `[:valid {...}]`

#### 2. Product Service Tests

**File**: `test/com/apriary/services/product_test.clj` (new)

**Intent**: Test `create-products-batch` and `list-products` with focus on RLS enforcement. Follows pattern from `summary_test.clj`.

**Contract**: Test cases covering:
- `create-products-batch`: products created with correct `:product/user-id`
- `list-products`: user A sees only their products, not user B's
- `list-products`: returns products sorted by date descending
- `list-products`: handles empty result (new user, no products)

#### 3. Manual Testing Checklist

**File**: `context/changes/product-input-view/manual-test-checklist.md` (new)

**Intent**: Document manual test steps to verify UI and end-to-end flow before marking phase complete.

**Contract**:

```markdown
# Manual Testing Checklist — Product Input & View

## Prerequisites
- [ ] Server running: `clj -M:dev dev`
- [ ] Signed in as test user

## CSV Import
- [ ] Navigate to /products via nav link
- [ ] Paste valid CSV (3 rows) → submit → see 3 products in table
- [ ] Paste CSV with 1 invalid row (bad date) → see valid rows in table, rejected row listed with error
- [ ] Paste CSV with invalid metric "liter" → see rejection error
- [ ] Paste CSV with quantity = 0 → see rejection error

## Table Display
- [ ] Products sorted by date descending (newest at top)
- [ ] Empty date field shows "-" in table
- [ ] Quantity right-aligned in table
- [ ] Table columns: hive_number, date, product, quantity, metric

## RLS Enforcement
- [ ] Sign in as user A → import products → see products
- [ ] Sign out, sign in as user B → visit /products → do NOT see user A's products
- [ ] Sign in as user B → import different products → see only user B's products

## Regression Check (Summaries)
- [ ] Import observation CSV via Summaries → still works (no breakage from shared CSV parsing)
```

### Success Criteria:

#### Automated Verification:

- All product CSV validation tests pass: `clj -M:dev test :only com.apriary.services.product-csv-test`
- All product service tests pass: `clj -M:dev test :only com.apriary.services.product-test`
- Linting passes: `clj -M:dev lint`

#### Manual Verification:

- Complete manual testing checklist (all checkboxes ticked)
- Summaries CSV import still works (regression check)
- RLS verified: different users see only their own products

**Implementation Note**: After completing this phase and all tests pass + manual checklist is complete, the feature is ready for review. Update `context/changes/product-input-view/change.md` status to `implemented`.

---

## Testing Strategy

### Unit Tests:

**Product CSV Validation** (`product_csv_test.clj`):
- Valid row with all fields
- Missing required fields (hive_number, product, quantity, metric)
- Invalid date format (regex mismatch)
- Empty date (should pass as nil)
- Quantity edge cases: 0, negative, non-numeric
- Metric enum validation: valid (kg, ml, g) vs invalid (liter, oz)

**Product Service** (`product_test.clj`):
- `create-products-batch`: verify batch insert, `:product/user-id` set correctly
- `list-products`: verify RLS (user A cannot see user B's products)
- `list-products`: verify sort order (date descending)
- `list-products`: handle empty result (new user)

### Manual Testing Steps:

1. **CSV Import Flow**:
   - Navigate to /products
   - Paste valid CSV → verify products appear in table immediately
   - Paste mixed valid/invalid → verify partial import + rejected-rows component
   - Verify toast confirmation appears

2. **Table Display**:
   - Check sort order (newest date at top)
   - Check columns match CSV structure
   - Check empty date displays as "-"
   - Check responsive layout (table scrolls on narrow screens)

3. **RLS Verification**:
   - Sign in as user A → import products → sign out
   - Sign in as user B → visit /products → verify user A's products NOT visible
   - Import products as user B → verify only user B's products appear

4. **Regression Check**:
   - Import observation CSV via Summaries → verify still works (shared CSV parsing didn't break)

## Performance Considerations

**Query performance**: `list-products` fetches all user's products without pagination. For MVP (5-50 hives, ~100-500 records per user), this is acceptable. If users report slow load times with >1000 records, add pagination in v2.

**CSV import performance**: Batch transaction via `xt/submit-tx` handles 100+ rows efficiently. Summaries already validates this pattern — no special optimization needed for MVP.

**Table rendering**: Server-side Rum rendering generates HTML table. For <500 rows, no client-side virtualization needed. If users report slow page loads, add pagination in v2.

## Migration Notes

No data migration required. Adds new `:product` doc-type alongside existing `:user`, `:summary`, `:generation` doc-types. Existing data untouched.

## References

- Roadmap: `context/foundation/roadmap.md` (S-01)
- PRD: `context/foundation/prd.md` (FR-001, FR-002, US-01)
- CSV parsing pattern: `src/com/apriary/services/csv_import.clj:19-66`
- Validation pattern: `src/com/apriary/services/csv_import.clj:92-154`
- Service pattern: `src/com/apriary/services/summary.clj`
- RLS pattern: `src/com/apriary/services/summary.clj:131-186`
- Summaries tests: `test/com/apriary/services/csv_import_test.clj`, `test/com/apriary/services/summary_test.clj`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Schema & Service Layer

#### Automated

- [x] 1.1 Schema validation passes
- [x] 1.2 Product CSV validation unit tests pass
- [x] 1.3 Product service unit tests pass
- [x] 1.4 Linting passes

#### Manual

- [x] 1.5 REPL test: create products via service, verify user-id set
- [x] 1.6 REPL test: list products with different user-id returns empty (RLS)
- [x] 1.7 REPL test: validate invalid date returns error
- [x] 1.8 REPL test: validate invalid metric returns error

### Phase 2: CSV Import Handler

#### Automated

- [ ] 2.1 Routes register correctly (server starts)
- [ ] 2.2 Integration test: POST valid CSV returns 200
- [ ] 2.3 Integration test: POST invalid CSV returns rejected-rows
- [ ] 2.4 Linting passes

#### Manual

- [ ] 2.5 Submit valid CSV → success toast + table refresh
- [ ] 2.6 Submit mixed CSV → valid rows in table, rejected rows listed
- [ ] 2.7 Check XTDB → products have correct user-id
- [ ] 2.8 Sign in as different user → cannot see other user's products

### Phase 3: Products Page & Table UI

#### Automated

- [ ] 3.1 Server starts without errors
- [ ] 3.2 Navigation link renders in page source
- [ ] 3.3 Linting passes

#### Manual

- [ ] 3.4 Visit /products → see CSV form and empty state
- [ ] 3.5 Paste CSV → submit → table appears sorted by date descending
- [ ] 3.6 Reload page → products persist, still sorted correctly
- [ ] 3.7 Check responsive layout on tablet
- [ ] 3.8 Click Products nav link → smooth htmx navigation
- [ ] 3.9 Verify table columns match CSV structure

### Phase 4: Testing

#### Automated

- [ ] 4.1 Product CSV validation tests pass
- [ ] 4.2 Product service tests pass
- [ ] 4.3 Linting passes

#### Manual

- [ ] 4.4 Complete manual testing checklist
- [ ] 4.5 Summaries CSV import still works (regression check)
- [ ] 4.6 RLS verified across different users
