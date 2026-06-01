# Product Rankings — Plan Brief

> Full plan: `context/changes/product-rankings/plan.md`
> PRD: `context/foundation/prd.md`

## What & Why

Add a Rankings view showing top 5 and bottom 5 hives per product type, ranked by all-time cumulative quantity. Beekeepers use these rankings to identify best and worst performing hives for breeding decisions and hive management planning.

Currently, users can import and view production data but cannot easily identify top/bottom performers—they must manually analyze the products table.

## Starting Point

Product tracking is fully implemented (`product-input-view` change, commits 7b55ae2, e17f678, d9305f9):
- `:product` schema in XTDB with `user-id`, `hive-number`, `date`, `product`, `quantity`, `metric`
- CSV import and validation
- Products table at `/products`
- RLS enforcement via `product/user-id` filtering

## Desired End State

Users navigate to `/rankings` via header link and see:
- One section per product type (Honey, Pollen, Venom, etc.)
- Each section shows "Top N" and "Bottom N" hives (N = min(5, actual count))
- Rankings display hive number and total quantity (e.g., "A-01 • 15 kg")
- Only user's own rankings visible (RLS enforced)
- Empty state message if no products exist

## Key Decisions Made

| Decision                       | Choice                              | Why (1 sentence)                                                                                  | Source |
| ------------------------------ | ----------------------------------- | ------------------------------------------------------------------------------------------------- | ------ |
| Layout                         | Separate sections per product       | Easy to scan, matches PRD "per product type" language, works naturally with varying product types | Plan   |
| < 5 hives handling             | Show actual count (Top N, Bottom N) | Honest representation, still provides value with partial data                                     | Plan   |
| Navigation                     | Separate "Rankings" nav link        | Matches established pattern (Summaries/Products are separate), gives rankings equal prominence    | Plan   |
| Ranking entry details          | Hive number + total quantity only   | Focused on ranking decision, matches PRD spec (sum per hive per product)                          | Plan   |
| Aggregation approach           | XTDB Datalog query                  | Leverage database optimization, consistent with existing query patterns                           | Plan   |
| All-time vs date-filtered      | All-time cumulative only            | PRD explicitly defers time-series to v2                                                           | PRD    |

## Scope

**In scope:**
- Rankings calculation service (aggregation query)
- Rankings page with top/bottom tables
- Navigation link in header
- RLS enforcement
- Empty state handling

**Out of scope:**
- Date range filtering
- Time-series analysis or trends
- Queen tracking or breeding recommendations
- Export/print functionality
- Edit/delete from rankings page

## Architecture / Approach

**Data flow:**
1. User navigates to `/rankings` → handler calls `product-rankings/calculate-rankings`
2. Service runs XTDB Datalog query: group by (hive-number, product-type), sum quantity
3. Service splits results by product type, sorts (descending for top, ascending for bottom), takes N
4. Service returns: `{product-type {:top [...] :bottom [...]}}`
5. Handler renders page with `layout/app-page`
6. UI renders one section per product type, each section has two tables

**Components:**
- `product-rankings.clj` - service with aggregation logic
- `rankings.clj` - UI components (product-section, ranking-table, page-content)
- `rankings.clj` (pages) - handler + route
- `header.clj` - navigation link

## Phases at a Glance

| Phase                         | What it delivers                                                                    | Key risk                                                                           |
| ----------------------------- | ----------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| 1. Rankings Service & Page    | Service, handler, UI components, navigation link—complete vertical slice           | Aggregation query complexity: grouping by two dimensions requires careful testing  |

**Prerequisites:** Product tracking implementation complete (schema exists, data present)  
**Estimated effort:** ~1 session (single phase, no schema changes, pure read operations)

## Open Risks & Assumptions

- **Assumption:** All-time cumulative totals provide sufficient value without date filtering for MVP validation
- **Assumption:** Small apiary scale (5-50 hives) means aggregation performance is acceptable without caching
- **Risk:** XTDB Datalog aggregation syntax may require iteration if grouping by two dimensions (hive + product type) needs different approach than single-dimension grouping

## Success Criteria (Summary)

- User navigates to `/rankings` and sees sections for each product type with correct top/bottom hives
- Rankings match manual calculation from products table (aggregation correct)
- Product types with < 5 hives show "Top N" / "Bottom N" labels correctly
- Different users see different rankings (RLS verified)
