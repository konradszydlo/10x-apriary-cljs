# Manual Testing Checklist — Product Input & View

## Prerequisites
- [x] Server running: `clj -M:dev dev`
- [x] Signed in as test user

## CSV Import
- [x] Navigate to /products via nav link
- [x] Paste valid CSV (3 rows) → submit → see 3 products in table
- [x] Paste CSV with 1 invalid row (bad date) → see valid rows in table, rejected row listed with error
- [x] Paste CSV with invalid metric "liter" → see rejection error
- [x] Paste CSV with quantity = 0 → see rejection error

## Table Display
- [x] Products sorted by date descending (newest at top)
- [x] Empty date field shows "-" in table
- [x] Quantity right-aligned in table
- [x] Table columns: hive_number, date, product, quantity, metric

## RLS Enforcement
- [x] Sign in as user A → import products → see products
- [x] Sign out, sign in as user B → visit /products → do NOT see user A's products
- [x] Sign in as user B → import different products → see only user B's products

## Regression Check (Summaries)
- [x] Import observation CSV via Summaries → still works (no breakage from shared CSV parsing)
