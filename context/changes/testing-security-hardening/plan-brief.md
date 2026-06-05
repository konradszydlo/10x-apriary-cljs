# Security Hardening Tests — Plan Brief

> Full plan: `context/changes/testing-security-hardening/plan.md`

## What & Why

Add integration tests proving RLS isolation for product rankings and XSS prevention across CSV import flows. Closes two coverage gaps identified in test-plan.md Phase 3: (1) no handler-level test proving users cannot access other users' rankings, and (2) no explicit proof that malicious CSV input renders safely in HTML despite Rum's auto-escaping.

## Starting Point

RLS is thoroughly tested at service layer for CRUD operations (products, summaries, generations), but rankings endpoint lacks multi-user integration test. Input validation is comprehensive (Malli schemas, CSV parsing, field trimming), and Rum/Hiccup auto-escapes HTML, but no tests explicitly prove script tags in CSV cannot execute in the UI.

## Desired End State

Integration tests verify that:
- User A sees only their own product rankings when user B also has products (handler-level RLS proof)
- Script tags in product fields (hive-number, product name) are escaped to `&lt;script&gt;` in HTML
- Script tags in summary content are escaped to `&lt;script&gt;` in HTML

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|----------|--------|------------------|--------|
| RLS coverage gap to close | Product rankings isolation | Rankings service has unit test but no multi-user handler test, while other features have both. | Plan |
| XSS attack vectors to test | Script tags only (`<script>alert()</script>`) | Most common payload; proves Rum escaping works without exhaustive event-handler variants. | Plan |
| XSS verification method | Check for escaped HTML (`&lt;script&gt;` present in response) | Proves escaping happened rather than just asserting script didn't execute. | Plan |
| Test layer | Integration (CSV import → XTDB → render) | Matches how users encounter attack; proves full pipeline is safe. | Plan |
| Product fields to test | hive-number and product name | Only XSS-vulnerable string fields (date/quantity/metric are enum/integer validated). | Plan |
| Summary fields to test | Summary content field | Largest user-controlled text field; summaries are separate feature with different rendering. | Plan |

## Scope

**In scope:**
- RLS test for rankings handler with multi-user fixture
- XSS tests for product CSV fields (hive-number, product name)
- XSS test for summary CSV field (content)
- Cookbook update documenting both patterns in test-plan.md §6

**Out of scope:**
- Unit-level XSS tests (testing full CSV → XTDB → render flow instead)
- Event handler injection payloads (`<img onerror=...>`)
- Re-testing existing RLS for products/summaries CRUD
- Testing date/quantity/metric fields (validated as enums/integers, cannot contain scripts)

## Architecture / Approach

Follow existing integration test patterns from `products_test.clj` and `generations_test.clj`:
- Use `test-xtdb-node` for in-memory XTDB with auto-cleanup
- Use `make-ctx` helper to create handler contexts with different user IDs
- Call handlers directly (not service layer) to verify full middleware stack
- Assert on HTML response content using regex/string matching
- Use existing `every?` pattern from §6.3 for RLS assertions

## Phases at a Glance

| Phase | What it delivers | Key risk |
|-------|------------------|----------|
| 1. RLS Rankings Isolation Test | Proves user A cannot see user B's rankings via handler | Regression in RLS query filtering for derived/aggregated data |
| 2. XSS Prevention Tests - Products | Proves script tags in hive-number and product name are escaped | XSS vulnerability in product table rendering |
| 3. XSS Prevention Tests - Summaries | Proves script tags in summary content are escaped | XSS vulnerability in summary card rendering |
| 4. Cookbook Update | Documents RLS and XSS test patterns in test-plan.md §6 | Future contributors don't know how to write these tests |

**Prerequisites:** Existing integration test patterns in place; test-plan.md §6 has established cookbook structure
**Estimated effort:** ~1-2 sessions across 4 phases (4 new tests + documentation)

## Open Risks & Assumptions

- Assumes Rum/Hiccup auto-escaping is consistent across all rendering paths (likely true, but first explicit verification)
- Assumes script tags are sufficient proof of escaping without testing event handlers (reasonable given Rum escapes attributes too)

## Success Criteria (Summary)

- Multi-user rankings test passes and asserts all records belong to correct user
- XSS tests for products pass and verify escaped HTML entities in response
- XSS test for summaries passes and verifies escaped HTML in summary content
- test-plan.md §6.3 and §6.6 document the new patterns with code examples and file:line references
