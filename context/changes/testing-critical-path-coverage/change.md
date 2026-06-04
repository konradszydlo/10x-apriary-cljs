---
change_id: testing-critical-path-coverage
title: Critical-path coverage for CSV import and rankings
status: impl_reviewed
created: 2026-06-03
updated: 2026-06-05
archived_at: null
---

## Notes

Open a change folder for rollout Phase 1 of context/foundation/test-plan.md: "Critical-path coverage".
Risks covered: #1 (Silent CSV import failure), #2 (Frontend-backend contract drift), #5 (Ranking calculation incorrect), #6 (Invalid CSV row accepted). Test types planned: Integration (in-memory XTDB + handlers), unit (parsing, ranking).
Risk response intent:
- Risk #1: prove CSV import round-trip (paste valid CSV → submit → query XTDB directly → verify records exist with correct user-id); challenge "200 response means data was stored"; avoid happy-path-only tests and mocking XTDB write without proving persistence.
- Risk #2: prove FE request payload matches BE Malli schema AND BE response matches FE rendering; challenge "schema validation passes means contract is correct"; avoid copying FE request from BE code.
- Risk #5: prove rankings show correct top/bottom 5 for known dataset; challenge "aggregation is obvious" (verify tie-breaking, zero-quantity, <5 hives, multi-product); avoid asserting current output.
- Risk #6: prove invalid CSV rows rejected with clear errors, valid rows still process; challenge "Malli catches everything" (verify field-level validation, partial-batch handling); avoid checking schema without parse edge cases.
