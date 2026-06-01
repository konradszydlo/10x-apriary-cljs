# Product Rankings Implementation Plan

## Overview

Add a Rankings view that displays top 5 and bottom 5 hives per product type, ranked by all-time cumulative quantity. Users navigate via a new "Rankings" link in the header. Rankings are calculated from existing `:product` entities via aggregation queries (group by hive + product type, sum quantities).

## Current State Analysis

**What exists:**
- Full product tracking implementation from `product-input-view` change (commits 7b55ae2, e17f678, d9305f9)
- `:product` schema in XTDB with fields: `user-id`, `hive-number`, `date`, `product`, `quantity`, `metric`
- Products page at `/products` with CSV import and table display
- Product service with `create-products-batch` and `list-products` functions
- RLS enforcement via `product/user-id` filtering in queries
- Navigation pattern: header with "Summaries" and "Products" links

**What's missing:**
- Aggregation query to calculate total quantity per hive per product type
- Rankings service function to return top/bottom N hives
- Rankings page handler and route
- Rankings UI components (product section headers, ranking tables)
- "Rankings" navigation link in header

**Key constraints:**
- Must filter by `product/user-id` to enforce RLS (same pattern as `list-products`)
- All-time cumulative totals only (no date filtering per PRD decision)
- Show actual count if < 5 hives per product type (don't pad or hide)

### Key Discoveries:

- **Aggregation pattern from XTDB docs**: Use Datalog `:find` with aggregate functions like `(sum ?quantity)` grouped by `?hive-number` and `?product-type`
- **Existing RLS pattern** (from `src/com/apriary/services/product.clj:100-102`): `:where [['?p :product/user-id user-id]]` filters at query level
- **Service error handling pattern** (from `product.clj:8-11`): All service functions return `[:ok result]` or `[:error {:code ... :message ...}]`
- **Page handler pattern** (from `src/com/apriary/pages/products.clj:17-38`): Use `layout/app-page` with `{:page-title "..."}` options map
- **Navigation link pattern** (from `src/com/apriary/ui/header.clj:59-65`): Links use `:hx-boost "true"` for smooth navigation

## Desired End State

After implementation:
1. User clicks "Rankings" in header navigation
2. Rankings page loads showing sections for each product type (e.g., "Honey", "Pollen", "Venom")
3. Each section displays two tables:
   - "Top 5 Honey Producers" (or "Top N" if < 5 hives)
   - "Bottom 5 Honey Producers" (or "Bottom N" if < 5 hives)
4. Each table row shows: hive number and total quantity with metric (e.g., "A-01 • 15 kg")
5. Rankings are sorted by quantity (descending for top, ascending for bottom)
6. Only product types with at least 1 hive appear (no sections for unused products)
7. User sees only their own rankings (RLS enforced)

**Verification:**
- Navigate to `/rankings` → see sections for each product type user has tracked
- Products with < 5 hives show "Top N" / "Bottom N" where N = actual hive count
- Different user sees different rankings (RLS verified)
- Rankings match manual calculation from products table (aggregation correct)

## What We're NOT Doing

- Date range filtering (all-time totals only, per PRD decision)
- Time-series analysis or trend graphs (deferred to v2)
- Queen tracking or breeding recommendations (out of scope)
- Pagination (small apiary scale = small dataset)
- Export to CSV or printable view
- Edit/delete from rankings page (must go to Products page)

## Implementation Approach

**Single-phase vertical slice:** rankings service + rankings page + navigation link. No schema changes needed—pure read operations on existing `:product` entities.

**Data flow:**
1. User navigates to `/rankings`
2. Handler calls `product-rankings/calculate-rankings` service
3. Service queries XTDB with aggregation: group by (hive-number, product type), sum quantity
4. Service separates results by product type, sorts each, takes top/bottom N
5. Handler renders page with one section per product type
6. Each section renders two tables (top N, bottom N)

**Key decision:** Aggregation happens in XTDB Datalog query (not in-memory Clojure) to leverage database optimization and maintain consistency with existing query patterns.

## Phase 1: Rankings Service & Page

### Overview

Create rankings calculation service with aggregation query, rankings page handler with route, and UI components for displaying ranked tables. Add navigation link to header.

### Changes Required:

#### 1. Rankings Service

**File**: `src/com/apriary/services/product_rankings.clj` (new)

**Intent**: Calculate top N and bottom N hives per product type via XTDB aggregation query. Group products by (hive-number, product type), sum quantities, then split by product type and sort each. Return structure suitable for rendering sections in UI.

**Contract**:

Public function signature:
```clojure
(defn calculate-rankings
  "Calculate top/bottom N hive rankings per product type.
  
   Returns map of product types to ranking data:
   {:product-type {:top [...] :bottom [...]}}
   
   Each ranking entry: {:hive-number str :total-quantity int :metric str :count int}
   
   Args:
     db - XTDB database instance
     user-id - UUID of authenticated user
     n - Number of top/bottom hives to return (default 5)
   
   Returns:
     [:ok {:rankings {\"Honey\" {:top [...] :bottom [...]} ...}}]
     [:error {:code ... :message ...}]"
  [db user-id & {:keys [n] :or {n 5}}])
```

XTDB aggregation query structure (not full implementation):
- `:find` aggregates: `?hive-number`, `?product-type`, `?metric`, `(sum ?quantity)`, `(count ?product-id)`
- `:where` clause filters by `user-id` for RLS
- Group by `?hive-number`, `?product-type`, AND `?metric` implicitly via `:find` tuple
- **Critical:** Grouping by metric prevents mixing units (e.g., summing kg + g). Each hive+product+metric combination is aggregated separately.

The service post-processes query results:
1. Group by product type
2. For each product type, sort by total quantity (descending for top, ascending for bottom)
3. Take first N for top, first N for bottom
4. Return map: `{product-type {:top [...] :bottom [...]}}`
5. Note: Same hive may appear multiple times if it has same product in different metrics (e.g., "A-01 • 15 kg" and "A-01 • 2000 g" as separate entries)

#### 2. Rankings UI Components

**File**: `src/com/apriary/ui/rankings.clj` (new)

**Intent**: Render rankings page layout with one section per product type. Each section contains two tables (top N, bottom N) showing hive number and total quantity.

**Contract**:

Component signatures:
```clojure
(defn product-section
  "Render one product type's rankings section with top/bottom tables.
   
   Args:
     product-type - String (e.g., \"Honey\")
     rankings - {:top [...] :bottom [...]} from service
   
   Returns: Hiccup div with heading and two tables"
  [product-type rankings])

(defn ranking-table
  "Render single ranking table (top or bottom).
   
   Args:
     title - String (e.g., \"Top 5 Honey Producers\")
     entries - Vector of {:hive-number :total-quantity :metric}
   
   Returns: Hiccup table with columns: Rank, Hive, Total"
  [title entries])

(defn rankings-page-content
  "Render full rankings page content with all product sections.
   
   Args:
     rankings-map - Map from service: {product-type {:top :bottom}}
   
   Returns: Hiccup div with page heading and product sections"
  [rankings-map])
```

Table structure: 3 columns (Rank, Hive Number, Total Quantity). Rank is ordinal (1-5), Hive Number is string, Total Quantity is formatted as `{quantity} {metric}`.

#### 3. Rankings Page Handler

**File**: `src/com/apriary/pages/rankings.clj` (new)

**Intent**: Handle GET /rankings request. Fetch rankings via service, render page using app-page layout. Return Ring response with HTML body.

**Contract**:

Handler signature:
```clojure
(defn rankings-page-handler
  "Render rankings page with top/bottom hives per product type.
   
   GET /rankings
   
   Returns: Ring response with HTML body"
  [{:keys [session biff/db] :as ctx}])
```

Error handling: if service returns `[:error ...]`, render error state in page (don't return 500). Show message: "Unable to load rankings. Please try again."

Module definition with routes:
```clojure
(def module
  {:routes [["/rankings" {:middleware [mid/wrap-signed-in]}
             ["" {:get rankings-page-handler}]]]})
```

#### 4. Module Registration

**File**: `src/com/apriary.clj`

**Intent**: Register rankings module in main app so routes are available.

**Contract**: Add `[com.apriary.pages.rankings :as rankings]` to `:require`, add `rankings/module` to `modules` vector (after `products/module`).

#### 5. Navigation Link

**File**: `src/com/apriary/ui/header.clj`

**Intent**: Add "Rankings" link to navigation alongside "Summaries" and "Products".

**Contract**: Insert after Products link (around line 65):

```clojure
[:a.text-gray-700.hover:text-gray-900.px-3.py-2.rounded.hover:bg-gray-100
 {:href "/rankings"
  :hx-boost "true"}
 "Rankings"]
```

### Success Criteria:

#### Automated Verification:

- Server starts without errors: `clj -M:dev dev`
- Rankings route registered: `curl -I http://localhost:8080/rankings` (after auth) returns 200
- Service tests pass: `clj -M:test -n com.apriary.services.product-rankings-test`
- No linting errors: Check compilation via `clj -M -e "(require 'com.apriary.pages.rankings)"`

#### Manual Verification:

- Navigate to /rankings → see one section per product type
- Each section shows "Top N" and "Bottom N" where N ≤ 5
- Hive numbers and quantities match manual sum from products table
- Rankings sorted correctly (top = highest first, bottom = lowest first)
- Product types with < 5 hives show "Top N" / "Bottom N" labels correctly
- Sign in as different user → see different rankings (RLS verified)
- Click Rankings nav link → smooth htmx navigation
- Empty state: user with no products sees message "No products yet. Import data to see rankings."

**Implementation Note**: 

**CRITICAL: Verify XTDB aggregation syntax first.** The codebase has no existing examples of XTDB Datalog aggregation functions (`sum`, `count`). Before implementing the full service, test the aggregation query in REPL to confirm XTDB 1.24 syntax. If aggregation syntax doesn't work as expected, fall back to Clojure-side aggregation (proven pattern in `generation.clj:344-350`).

After completing this phase and all automated verification passes, pause here for manual confirmation that rankings display correctly and RLS is enforced before marking complete.

---

## Testing Strategy

### Unit Tests:

**Rankings Service** (`product_rankings_test.clj`):
- `calculate-rankings-basic-test` - Multiple hives and products, verify aggregation sums
- `calculate-rankings-rls-test` - Verify user A sees only their rankings, not user B's
- `calculate-rankings-less-than-n-test` - Product type with 3 hives returns top 3, bottom 3
- `calculate-rankings-empty-test` - User with no products returns empty rankings map
- `calculate-rankings-single-product-type-test` - Only one product type appears in results
- `calculate-rankings-sorting-test` - Top sorted descending, bottom sorted ascending

### Manual Testing Steps:

1. **Basic Rankings Display:**
   - Import products for multiple hives and product types
   - Navigate to /rankings
   - Verify sections appear for each product type
   - Verify top/bottom tables show correct hive numbers and totals

2. **Edge Cases:**
   - Product type with exactly 5 hives → shows "Top 5" / "Bottom 5"
   - Product type with < 5 hives (e.g., 3) → shows "Top 3" / "Bottom 3"
   - User with no products → sees empty state message

3. **RLS Verification:**
   - Sign in as user A, import products, view rankings
   - Sign out, sign in as user B
   - Verify user B sees different rankings (or empty if no products)

4. **Regression Check:**
   - Products CSV import still works
   - Products table still displays correctly
   - Summaries feature still works (no breakage)

## Performance Considerations

**Aggregation query performance:** XTDB Datalog query with `sum` and `count` aggregates runs in-database. For small apiary scale (5-50 hives, 100-500 product records), aggregation completes in < 100ms. No caching needed for MVP.

**Page load time:** Rankings page makes one service call (single XTDB query). Expected load time < 200ms for typical dataset. Acceptable without optimization.

## Migration Notes

No schema changes or data migrations required. Rankings feature is pure read operations on existing `:product` entities.

## References

- PRD: `context/foundation/prd.md` (US-02, FR-004, FR-005)
- Product schema: `src/com/apriary/schema.clj:49-60`
- Product service pattern: `src/com/apriary/services/product.clj`
- Existing products implementation: `context/changes/product-input-view/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Rankings Service & Page

#### Automated

- [x] 1.1 Server starts without errors — cb09f78
- [x] 1.2 Rankings route registered (returns 200 after auth) — cb09f78
- [x] 1.3 Service tests pass — cb09f78
- [x] 1.4 No linting errors — cb09f78

#### Manual

- [x] 1.5 Navigate to /rankings → see one section per product type — cb09f78
- [x] 1.6 Each section shows correct top N and bottom N tables — cb09f78
- [x] 1.7 Rankings match manual calculation from products table — cb09f78
- [x] 1.8 Product types with < 5 hives show correct "Top N" / "Bottom N" labels — cb09f78
- [x] 1.9 Sign in as different user → see different rankings (RLS verified) — cb09f78
- [x] 1.10 Click Rankings nav link → smooth htmx navigation — cb09f78
- [x] 1.11 Empty state: user with no products sees appropriate message — cb09f78
