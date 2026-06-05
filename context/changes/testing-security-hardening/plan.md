# Security Hardening Tests Implementation Plan

## Overview

Add integration tests to prove RLS isolation for product rankings (Risk #4) and XSS prevention for CSV-imported user input (Risk #7). These tests close coverage gaps identified in test-plan.md Phase 3 by verifying that malicious input cannot execute in the UI and that users cannot access other users' ranking data.

## Current State Analysis

**Existing RLS Coverage:**
- Products service has multi-user RLS test at service layer (`test/com/apriary/services/product_test.clj:90`)
- Rankings service has multi-user RLS test at service layer (`test/com/apriary/services/product_rankings_test.clj:158`) but no handler-level integration test
- **Gap**: No handler-level test proving user A cannot see user B's rankings through the `/api/rankings` endpoint

**Existing XSS Protection:**
- Rum/Hiccup rendering auto-escapes HTML by default
- Malli schemas validate input format (length, regex, enums)
- CSV parsing trims whitespace and validates field structure
- **Gap**: No tests explicitly proving script tags in CSV input render safely in HTML

**Key Discoveries:**
- All RLS queries filter at database level with `:where` clauses: `src/com/apriary/services/product_rankings.clj:42-49`
- Product rendering uses Hiccup with auto-escape: `src/com/apriary/ui/products.clj:83-87`
- Summary content rendering uses Hiccup: `src/com/apriary/ui/summary_card.clj:289`
- Test-plan.md §6.2 documents integration test pattern with `test-xtdb-node`
- Test-plan.md §6.3 documents multi-user RLS test pattern with `every?` assertion

## Desired End State

Integration tests verify:
1. User A sees rankings only for their own products when user B also has products in the system
2. Script tags in product CSV fields (hive-number, product name) are escaped in HTML response
3. Script tags in summary CSV content are escaped in HTML response

## What We're NOT Doing

- Unit-level XSS tests (we test the full CSV → XTDB → render flow)
- Event handler injection payloads (`<img onerror=...>`) — script tags prove escaping works
- Re-testing existing RLS patterns for products/summaries CRUD (already covered)
- Testing all product fields — date/quantity/metric are validated as enums/integers and cannot contain script tags

## Implementation Approach

Follow existing integration test patterns:
- Use `with-open [node (test-xtdb-node [])]` for XTDB cleanup
- Use `make-ctx` helper to create handler contexts with different user IDs
- Call handlers directly (not service layer) to verify full middleware stack
- Assert on both response structure and HTML content
- Use existing fixtures from products/summaries tests as templates

## Phase 1: RLS Rankings Isolation Test

### Overview

Verify that rankings endpoint enforces RLS — user A cannot see user B's product rankings through the handler layer.

### Changes Required:

#### 1. Rankings RLS Integration Test

**File**: `test/com/apriary/pages/rankings_test.clj`

**Intent**: Prove rankings handler enforces multi-user isolation by creating products for two users and verifying each user sees only their own rankings.

**Contract**: Test file follows existing integration test pattern from `products_test.clj` and `generations_test.clj`. Test uses `test-xtdb-node`, creates products for two users via service layer, then calls `rankings-page-handler` for each user and asserts:
- User A's response contains only user A's hive numbers
- User B's response contains only user B's hive numbers
- No cross-contamination of ranking data

### Success Criteria:

#### Automated Verification:

- Test passes: `clj -M:test -n com.apriary.pages.rankings-test`
- Linting passes: `clj -M:clj-kondo --lint test/com/apriary/pages/rankings_test.clj`
- Full test suite still passes: `clj -M:test`

#### Manual Verification:

- Review test output to confirm RLS assertion logic matches §6.3 pattern (using `every?` to verify all records have correct user-id)

---

## Phase 2: XSS Prevention Tests - Products

### Overview

Prove that script tags in product CSV fields (hive-number and product name) are HTML-escaped during the CSV import → XTDB → rendering flow.

### Changes Required:

#### 1. Product XSS Integration Tests

**File**: `test/com/apriary/pages/products_test.clj`

**Intent**: Add two tests proving malicious CSV input renders safely:
1. Script tag in hive-number field is escaped to `&lt;script&gt;`
2. Script tag in product name field is escaped to `&lt;script&gt;`

**Contract**: Each test creates a CSV payload with `<script>alert('XSS')</script>` in the target field, submits via `import-products-handler`, then asserts the HTML response does not contain the raw `<script` tag (primary check) and optionally verifies the escaped form is present using flexible regex (e.g., `#"&lt;\s*script\s*&gt;"` to tolerate whitespace variations). Pattern follows existing `import-products-rls-test` structure.

### Success Criteria:

#### Automated Verification:

- Tests pass: `clj -M:test -n com.apriary.pages.products-test`
- Linting passes: `clj -M:clj-kondo --lint test/com/apriary/pages/products_test.clj`
- Full test suite still passes: `clj -M:test`

#### Manual Verification:

- Review test assertions to confirm they check for escaped HTML entities, not just absence of raw script tags

---

## Phase 3: XSS Prevention Tests - Summaries

### Overview

Prove that script tags in summary content field are HTML-escaped during CSV import → rendering flow.

### Changes Required:

#### 1. Summary XSS Integration Test

**File**: `test/com/apriary/pages/summaries_view_test.clj`

**Intent**: Prove malicious summary content renders safely by submitting CSV with `<script>alert('XSS')</script>` in the observation field, then verifying the HTML response escapes it.

**Contract**: Test follows existing summary import pattern. Creates CSV with script tag in observation field (padded to meet 50-char minimum), calls the summaries import handler, then retrieves the summary list and asserts the response does not contain raw `<script` tag (primary check) and optionally verifies escaped form is present using flexible regex.

### Success Criteria:

#### Automated Verification:

- Test passes: `clj -M:test -n com.apriary.pages.summaries-view-test`
- Linting passes: `clj -M:clj-kondo --lint test/com/apriary/pages/summaries_view_test.clj`
- Full test suite still passes: `clj -M:test`

#### Manual Verification:

- Review test to confirm it covers the largest user-controlled text field (summary content)
- Verify test uses integration-level CSV import flow, not just unit-level rendering

---

## Phase 4: Cookbook Update

### Overview

Document the new test patterns in test-plan.md §6 so future contributors know how to write RLS and XSS tests.

### Changes Required:

#### 1. Update Multi-User RLS Pattern for Rankings

**File**: `context/foundation/test-plan.md`

**Intent**: Update existing §6.3 to add a subsection documenting the pattern for testing RLS on aggregation/ranking endpoints (as opposed to CRUD operations that §6.3 currently covers).

**Contract**: New subsection within §6.3 explains:
- When to test rankings-style RLS (derived data, not single-record CRUD)
- Code example from Phase 1 test showing multi-user fixture + assertion on ranking contents
- Key assertion: verify ALL returned ranking records belong to the expected user, not just the count
- Differentiates from existing §6.3 CRUD-RLS pattern (which uses `every?` on entity collections)

#### 2. Add XSS Prevention Pattern

**File**: `context/foundation/test-plan.md`

**Intent**: Add §6.6 subsection documenting how to test XSS prevention through CSV import flows.

**Contract**: Section explains:
- When to add XSS tests (user-controlled string fields that render in HTML)
- Code example showing CSV payload with `<script>` tag + assertion for `&lt;script&gt;` in response
- Why integration tests are preferred (proves full CSV → XTDB → render pipeline is safe)
- Note that Rum's auto-escaping handles this framework-wide, so exhaustive field testing isn't needed

### Success Criteria:

#### Automated Verification:

- Markdown linting passes: `npx markdownlint context/foundation/test-plan.md` (if configured)
- File still parses as valid markdown

#### Manual Verification:

- Review §6.3 addition to confirm it clearly differentiates rankings-RLS from CRUD-RLS patterns
- Review §6.6 addition to confirm it explains both WHAT to test and WHY (Rum escaping is universal but needs proof)
- Verify examples include file:line references to the new tests

---

## Testing Strategy

### Integration Tests:

All tests in this plan are integration tests using in-memory XTDB + full handler stack. This is the cheapest layer that gives real signal for these risks:
- RLS Risk #4: Must verify query filtering at database level, not just middleware
- XSS Risk #7: Must verify end-to-end flow from CSV paste to HTML rendering

### Coverage:

- Phase 1: 1 test (rankings RLS)
- Phase 2: 2 tests (product hive-number XSS, product name XSS)
- Phase 3: 1 test (summary content XSS)
- Total: 4 new integration tests

### Manual Testing Steps:

After Phase 2 and 3:
1. Start dev server: `clj -M:dev dev`
2. Navigate to Products page
3. Paste CSV with script tag in hive-number field:
   ```
   hive_number;date;product;quantity;metric
   <script>alert('XSS')</script>;23-11-2025;Honey;5;kg
   ```
4. Submit and verify rendered table shows `&lt;script&gt;alert('XSS')&lt;/script&gt;` (not executable script)
5. Check browser console — no JavaScript errors, no alert dialog
6. Repeat for summary import with malicious observation content

## Performance Considerations

No performance impact — tests are local-only integration tests using in-memory XTDB. They add ~200-300ms to the test suite runtime.

## References

- Test-plan Risk #4: `context/foundation/test-plan.md:30` (RLS bypass)
- Test-plan Risk #7: `context/foundation/test-plan.md:33` (XSS via CSV)
- Test-plan §6.2: Integration test pattern
- Test-plan §6.3: RLS test pattern with `every?` assertion
- Existing RLS tests: `test/com/apriary/services/product_test.clj:90`, `test/com/apriary/pages/generations_test.clj:89`
- Rankings service: `src/com/apriary/services/product_rankings.clj`
- Product rendering: `src/com/apriary/ui/products.clj:83-87`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: RLS Rankings Isolation Test

#### Automated

- [x] 1.1 Test passes: `clj -M:test -n com.apriary.pages.rankings-test`
- [x] 1.2 Linting passes: `clj -M:clj-kondo --lint test/com/apriary/pages/rankings_test.clj`
- [x] 1.3 Full test suite still passes: `clj -M:test`

#### Manual

- [x] 1.4 Review test output to confirm RLS assertion logic matches §6.3 pattern

### Phase 2: XSS Prevention Tests - Products

#### Automated

- [ ] 2.1 Tests pass: `clj -M:test -n com.apriary.pages.products-test`
- [ ] 2.2 Linting passes: `clj -M:clj-kondo --lint test/com/apriary/pages/products_test.clj`
- [ ] 2.3 Full test suite still passes: `clj -M:test`

#### Manual

- [ ] 2.4 Review test assertions to confirm they check for escaped HTML entities

### Phase 3: XSS Prevention Tests - Summaries

#### Automated

- [ ] 3.1 Test passes: `clj -M:test -n com.apriary.pages.summaries-view-test`
- [ ] 3.2 Linting passes: `clj -M:clj-kondo --lint test/com/apriary/pages/summaries_view_test.clj`
- [ ] 3.3 Full test suite still passes: `clj -M:test`

#### Manual

- [ ] 3.4 Review test to confirm it covers summary content field via CSV import

### Phase 4: Cookbook Update

#### Automated

- [ ] 4.1 File still parses as valid markdown

#### Manual

- [ ] 4.2 Review §6.3 addition confirms it differentiates rankings-RLS from CRUD-RLS
- [ ] 4.3 Review §6.6 addition explains WHAT to test and WHY
- [ ] 4.4 Verify examples include file:line references to new tests
