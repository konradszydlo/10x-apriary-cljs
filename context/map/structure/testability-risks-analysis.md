# Analiza Ryzyk Testowalności — Najaktywniejsze Obszary

**Data:** 2026-06-09  
**Źródło:** clj-kondo analysis + artifact-1-territory.md  
**Focus:** Hot zones z wysokim coupling do platform dependencies

---

## Podsumowanie

### Kluczowe Metryki Testowalności

| Namespace | Testability Score | Dependencies | Risk Level | Primary Issue |
|-----------|------------------|--------------|------------|---------------|
| **summaries-view** | 🔴 **25** (highest) | 19 total (7 external) | CRITICAL | XTDB + Rum + HTTP + Malli + 4 services |
| **pages.products** | 🟡 **18** | 9 total (3 external) | HIGH | XTDB + Rum + 3 services |
| **services.generation** | 🟡 **16** | 3 total (3 external) | HIGH | Pure XTDB + HTTP (all external) |
| **middleware** | 🟢 **7** | 4 total (4 external) | MEDIUM | Ring/HTTP only (no DB/UI) |
| **util** | 🟢 **4** | 1 total (1 external) | LOW | Only clojure.string |

**Scoring:** External deps + XTDB×10 + Rum×5 + HTTP×3

### Główne Wnioski

1. **summaries-view to testing nightmare** — 19 dependencies (XTDB, Rum, 4 services, 6 UI components, Malli) = wymaga masywnego mockowania LUB full integration test

2. **Services są testowalne** — generation/summary/csv-import mają tylko XTDB dep = łatwo mockować DB, reszta pure logic

3. **UI components są OK** — zero platform deps (tylko clojure.string) = pure functions, easy to test

4. **Middleware testuje się integration-only** — Ring/HTTP deps = trudno unit test, ale smoke tests wystarczą

5. **90% testów to integration, nie unit** — większość hot zones ma XTDB/HTTP/Rum = unit testing wymaga complex mocks, lepiej integration/e2e

---

## Lista Ryzyk Testowych

### Ryzyko #1: summaries-view — "God Page Testing Hell"

**Moduł:** `com.apriary.pages.summaries-view`  
**Testability Score:** 🔴 **25** (highest risk)  
**Aktywność z territory.md:** 🔥 Absolutny hotspot (9 zmian w 12 miesięcy)

#### Zależności (19 total):

**Platform dependencies (7):**
- `xtdb.api` — database queries & transactions
- `rum.core` — UI rendering (server-side)
- `malli.core` + `malli.error` — validation
- `cheshire.core` — JSON encoding
- `clojure.string`, `clojure.tools.logging`

**Internal dependencies (12):**
- **Services (4):** summary, csv-import, openrouter, generation
- **UI components (6):** layout, helpers, csv-import, summary-card, summaries-list, util
- **DTOs (1):** dto.summary
- **Infrastructure (1):** middleware

#### Ryzyko Testowalności:

```clojure
;; Typowy handler w summaries-view:
(defn summaries-list-page [{:keys [session biff/db params] :as ctx}]
  (let [user-id (:uid session)                    ; SESSION state
        [status result] (summary-service/list-summaries db user-id ...)  ; DB query via service
        [gen-status gen-result] (gen-service/list-user-generations db user-id ...)]  ; Another DB query
    (layout/app-page ctx {:page-title "Summaries"} ...)))  ; Rum rendering
```

**Co trzeba mockować dla unit test:**
1. ✗ **Session** (Biff context map)
2. ✗ **XTDB database** (`biff/db`)
3. ✗ **4 services** (summary, csv-import, openrouter, generation)
4. ✗ **Rum rendering** (server-side HTML generation)
5. ✗ **Malli validation** (schema validation errors)

**Total mocks needed:** 🔴 **7-10 complex mocks**

#### Dlaczego to problem:

- **16 handlers** w jednym pliku (1274 lines) — każdy ma podobne deps
- **Każdy handler = 7-10 mocks** → 16 × 7 = **112+ mock setups** dla pełnego coverage
- **Integration hell:** services wywołują XTDB, który wywołuje query, który zwraca entities, które są transformowane przez DTOs, które są renderowane przez Rum
- **Mock cascade:** Jeśli mockujesz service → musisz wiedzieć jaki exact output service zwraca → musisz znać DTOs → musisz znać XTDB schema

#### Rekomendacja:

| Test Type | Viability | Reason |
|-----------|-----------|--------|
| **Unit test** | ❌ **NIE** | Zbyt wiele mocks (7-10 per handler), fragile tests |
| **Integration test** | ✅ **TAK** | Test z real XTDB (in-memory) + real services, mock tylko HTTP (openrouter) |
| **E2E test** | ✅ **TAK (priority)** | Summaries flow to core user journey — Playwright test całego flow (import CSV → view → edit → accept) |

**Territory.md context:**
> "Summaries-view hotspot (9 zmian)" / "32% top-3 zmian w jednym pliku"

Każda zmiana w summaries-view ryzykuje breaking something → **E2E regression suite jest must-have**.

---

### Ryzyko #2: pages.products — "Active Development Without Guards"

**Moduł:** `com.apriary.pages.products`  
**Testability Score:** 🟡 **18**  
**Aktywność z territory.md:** 🟡 Q2 2026 hot zone (4 zmiany) — frontier domain

#### Zależności (9 total):

**Platform dependencies (3):**
- `xtdb.api` — database
- `rum.core` — UI rendering
- `clojure.tools.logging`

**Internal dependencies (6):**
- **Services (3):** csv-import, product-csv, product
- **UI components (2):** layout, products
- **Infrastructure (1):** middleware

#### Ryzyko Testowalności:

```clojure
;; Products page handler pattern:
(defn products-page [{:keys [session biff/db] :as ctx}]
  (let [user-id (:uid session)
        products (product-service/list-products db user-id)]  ; DB query
    (layout/app-page ctx ... (ui.products/render products))))
```

**Mocks needed:** 5-7 (session, db, 3 services, rum)

**Mniej niż summaries-view ALE:**
- Products to **Q2 2026 frontier** (4 zmiany vs summaries 9) = **active development**
- Territory.md: "Products pivot — 0 w Q4 → 4 w Q2" = **new code, może mieć bugs**
- **Zero frontend validation** (layer-boundaries-analysis.md) = więcej ryzyka w handlers

#### Dlaczego to problem:

- **Active development** (4 zmiany) = kod wciąż ewoluuje → testy muszą być maintainable
- **Brak frontend validation** = backend validation to jedyny guard → testy muszą coverage validation logic
- **Services isolation** (Ca=0 Ce=0) = services łatwo testować **unit**, ale pages integration-only

#### Rekomendacja:

| Test Type | Viability | Reason |
|-----------|-----------|--------|
| **Unit test** | ⚠️ **Częściowo** | Services (product, product-csv) = unit testowalne (tylko XTDB mock). Pages = NIE (zbyt wiele deps). |
| **Integration test** | ✅ **TAK (priority)** | Products handlers + real XTDB + real services = coverage całego flow |
| **E2E test** | ✅ **TAK** | Products CSV import flow podobny do summaries → reuse E2E patterns |

**Territory.md context:**
> "Products Q2 hot zone (4 zmiany)" / "Active development, testy alongside code"

Q2 był **test-first** (1.4:1 ratio) — products **powinien** mieć testy, ale prawdopodobnie **backend unit** nie **frontend integration**.

---

### Ryzyko #3: services.generation — "Pure DB Service (Testable!)"

**Moduł:** `com.apriary.services.generation`  
**Testability Score:** 🟡 **16**  
**Aktywność z territory.md:** 🟡 Warm (3 zmiany) — supporting service

#### Zależności (3 total — ALL external):

**Platform dependencies (3):**
- `xtdb.api` — database
- `clojure.string`
- `clojure.tools.logging`

**Internal dependencies:** **ZERO** (Ca=0 Ce=0 — perfect isolation!)

#### Ryzyko Testowalności:

```clojure
;; Typical service function:
(defn create-generation [node user-id model generated-count duration-ms]
  (try
    ;; Guard clauses (pure validation)
    (when (nil? user-id) (throw ...))
    
    ;; Entity creation (pure data)
    (let [entity {:xt/id (UUID/randomUUID) ...}]
      
      ;; DB write (only external dep)
      (xt/submit-tx node [[:xtdb.api/put entity]])
      
      [:ok entity])
    (catch Exception e
      [:error {:code "INTERNAL_ERROR" ...}])))
```

**Mocks needed:** **1** (tylko XTDB node/db)

#### Dlaczego to NIE jest problem:

- ✅ **Zero internal deps** — service nie zależy od innych services/pages/UI
- ✅ **Single external dep** — tylko XTDB, łatwo mockować (in-memory XTDB lub mock submit-tx)
- ✅ **Pure functions** — guard clauses, entity creation, error handling = testowalne bez mocks
- ✅ **Clear contract** — [:ok entity] lub [:error {:code ...}] = easy assertions

#### Rekomendacja:

| Test Type | Viability | Reason |
|-----------|-----------|--------|
| **Unit test** | ✅ **TAK (best fit)** | Mock XTDB node/db, test business logic, guard clauses, error handling |
| **Integration test** | ✅ **TAK** | Real XTDB (in-memory) = coverage XTDB query correctness |
| **E2E test** | ❌ **NIE** | Service nie jest user-facing — integration wystarczy |

**Territory.md context:**
> "Services layer = safest to modify" / "Best test discipline (63% w-commit)"

Services są **najbardziej testowalne** w projekcie — generation follow good pattern.

---

### Ryzyko #4: middleware — "Integration-Only (By Design)"

**Moduł:** `com.apriary.middleware`  
**Testability Score:** 🟢 **7**  
**Aktywność z territory.md:** ❄️ Cold (niska aktywność) ALE Ca=7 (all pages depend)

#### Zależności (4 total — ALL external):

**Platform dependencies (4):**
- `com.biffweb` — framework
- `ring.middleware.anti-forgery` — CSRF
- `ring.middleware.defaults` — Ring defaults
- `muuntaja.middleware` — content negotiation

**Internal dependencies:** **ZERO**

#### Ryzyko Testowalności:

```clojure
;; Middleware pattern:
(defn wrap-site-defaults [handler]
  (-> handler
      (ring.defaults/wrap-defaults site-defaults-config)
      (anti-forgery/wrap-anti-forgery)
      ...))
```

**Mocks needed:** Complex — Ring request/response cycle

#### Dlaczego to problem (ale akceptowalny):

- **Ring middleware = integration by nature** — trudno unit test bo wymaga full request/response cycle
- **Ca=7 (all pages depend)** → bug w middleware = all pages broken → critical testing needed
- **ALE:** Middleware to **thin layer** (4 deps, wszystkie external framework) = logic jest minimal

#### Rekomendacja:

| Test Type | Viability | Reason |
|-----------|-----------|--------|
| **Unit test** | ❌ **NIE** | Ring middleware nie ma sensu unit testować (zbyt wiele framework magic) |
| **Integration test** | ⚠️ **Częściowo** | Można test middleware stack z mock handler, ale fragile |
| **Smoke test** | ✅ **TAK (best fit)** | Simple request → middleware → response = check że nie crashuje |
| **E2E test** | ✅ **TAK (implicit)** | Każdy E2E test pages implicitly testuje middleware (bo pages depend on it) |

**Territory.md context:**
> "Middleware stable (Ca=7, I=0.00)" / "Cold zone (niska aktywność)"

Middleware jest **stable** (low churn) → **smoke tests + implicit E2E coverage wystarczą**, nie potrzeba complex integration tests.

---

### Ryzyko #5: util — "Pure Functions (Easy!)"

**Moduł:** `com.apriary.util`  
**Testability Score:** 🟢 **4** (lowest risk)  
**Aktywność z territory.md:** ❄️ Cold (3 zmiany) ALE Ca=5 (cross-domain usage)

#### Zależności (1 total):

**Platform dependencies (1):**
- `clojure.string`

**Internal dependencies:** **ZERO**

#### Ryzyko Testowalności:

```clojure
;; Util pattern (hypothesis — plik nie był read, ale pattern jest standard):
(defn parse-date [date-str]
  ;; Pure function — string → date lub nil
  (when date-str
    (try
      (java.time.LocalDate/parse date-str DateTimeFormatter/ISO_LOCAL_DATE)
      (catch Exception _ nil))))
```

**Mocks needed:** **ZERO** (pure functions!)

#### Dlaczego to NIE jest problem:

- ✅ **Pure functions** — input → output, no side effects
- ✅ **Zero deps** — tylko clojure.string (standard lib)
- ✅ **Ca=5** — używany przez wiele modułów → **must be well-tested**

#### Rekomendacja:

| Test Type | Viability | Reason |
|-----------|-----------|--------|
| **Unit test** | ✅ **TAK (perfect fit)** | Pure functions = zero mocks, fast tests, high coverage |
| **Integration test** | ❌ **NIE** | Brak potrzeby — unit tests wystarczą |
| **E2E test** | ❌ **NIE** | Util nie jest user-facing |

**Territory.md context:**
> "Util Ca=5 (cross-domain)" / "Secondary hub (3 zmiany)"

Util jest **shared helpers** → **must have 100% unit test coverage** (Ca=5 = wiele modułów zależy).

---

## Najbardziej Podejrzane Moduły

### Ranking Według Testability Risk

| Rank | Module | Score | Issue | Test Strategy |
|------|--------|-------|-------|---------------|
| 🥇 **#1** | **summaries-view** | 🔴 25 | 19 deps (XTDB+Rum+4 services+6 UI+Malli) | **E2E priority** (Playwright full flow) + Integration (real XTDB) |
| 🥈 **#2** | **pages.products** | 🟡 18 | 9 deps (XTDB+Rum+3 services) | **Integration** (real XTDB + services) + E2E (CSV import flow) |
| 🥉 **#3** | **services.generation** | 🟡 16 | 3 deps (all external: XTDB+string+logging) | **Unit** (mock XTDB) + Integration (in-memory XTDB) |
| **#4** | **middleware** | 🟢 7 | 4 deps (all Ring/HTTP) | **Smoke tests** + implicit E2E coverage |
| **#5** | **util** | 🟢 4 | 1 dep (clojure.string) | **Unit** (100% coverage required, Ca=5) |

---

### Szczegółowa Analiza: Gdzie Mockować vs Integracja vs E2E

#### Mockowanie Praktycznie Niemożliwe:

**summaries-view handlers:**
- 16 handlers × 7-10 mocks each = **112+ mock setups**
- Mock cascade: service → DTO → XTDB schema → Rum rendering
- **Fragile:** Każda zmiana w service contract = update all mocks

**Verdict:** ❌ Unit testing z mocks = **bad ROI** (effort vs value)

---

#### Mockowanie Możliwe (Ale Nie Warte Wysiłku):

**pages.products:**
- Mniej handlers niż summaries-view (8 vs 16)
- Mniej deps (9 vs 19)
- **ALE:** Wciąż wymaga 5-7 mocks per handler

**Verdict:** ⚠️ Unit testing możliwe ALE **integration lepszy** (real XTDB + services)

---

#### Mockowanie Sensowne:

**services.generation (i inne services):**
- **1 mock** (XTDB node/db)
- Pure logic (guard clauses, entity creation, error handling)
- Clear contracts ([:ok ...] vs [:error ...])

**Verdict:** ✅ Unit testing z XTDB mock = **good ROI**

**Util:**
- **0 mocks** (pure functions)
- Fast tests (ms per test)
- High coverage easy (Ca=5 = must test)

**Verdict:** ✅ Unit testing = **perfect fit**

---

#### Integration Tests — Where Best Fit:

**summaries-view + products pages:**
- Real XTDB (in-memory dla testów)
- Real services (generation, summary, product)
- Mock tylko HTTP externes (openrouter API)
- **Coverage:** Całej flow pages → services → XTDB

**Verdict:** ✅ Integration = **best balance** (effort vs coverage)

**Example pattern:**
```clojure
(deftest summaries-list-integration-test
  (with-xtdb-node [node (xt/start-node {})]  ; In-memory XTDB
    (let [db (xt/db node)
          ctx {:session {:uid test-user-id}
               :biff/db db
               :biff.xtdb/node node}]
      
      ;; Setup test data
      (create-test-summaries! node test-user-id)
      
      ;; Test handler
      (let [response (summaries-view/summaries-list-page ctx)]
        (is (= 200 (:status response)))
        (is (str/includes? (:body response) "Summaries"))))))
```

**Benefits:**
- ✅ Real XTDB = tests actual queries, not mocked behavior
- ✅ Real services = tests service contracts
- ✅ Mock tylko external APIs (openrouter) = controlled test environment
- ✅ Fast (in-memory XTDB) = seconds per test suite

---

#### E2E Tests — Where Critical:

**Summaries flow (core user journey):**
1. CSV import → generation → summaries list
2. Edit summary → save → verify update
3. Accept/reject AI suggestions
4. Delete summary

**Products flow:**
1. CSV import → products list
2. View rankings

**Coverage z territory.md:**
> "Summaries domain = critical path" (9 zmian, hotspot)  
> "Products domain = active development" (4 zmiany Q2)

**Verdict:** ✅ E2E Playwright tests dla **happy paths + critical errors**

**Example:**
```typescript
// tests/e2e/summaries-flow.spec.ts
test('CSV import → view → edit → accept flow', async ({ page }) => {
  await page.goto('/summaries');
  
  // Upload CSV
  await page.setInputFiles('input[type="file"]', 'test-data.csv');
  await page.click('button:has-text("Import")');
  
  // Wait for generation
  await page.waitForSelector('.summary-card');
  
  // Edit summary
  await page.click('.summary-card:first-child .edit-button');
  await page.fill('textarea[name="content"]', 'Updated content');
  await page.click('button:has-text("Save")');
  
  // Verify update
  await expect(page.locator('.summary-card:first-child')).toContainText('Updated content');
});
```

---

## Co Sprawdzić Dalej

### Immediate Actions (P0)

#### 1. **Inventory Istniejących Testów**

**Pytanie:** Które z hot zones mają już testy?

**Check:**
```bash
find test/com/apriary -name "*test.clj" -exec grep -l "summaries-view\|products\|generation\|middleware\|util" {} \;
```

**Expected z territory.md:**
- Services: 63% w-commit coverage → prawdopodobnie **mają** unit tests
- Pages: 16% w-commit coverage → prawdopodobnie **brak** integration tests
- UI: 17% w-commit coverage → prawdopodobnie **brak** tests

**Action:** 
1. Read existing test files
2. Map coverage: które funkcje/handlers mają testy, które nie
3. Identify gaps: summaries-view (16 handlers) — ile ma testy?

---

#### 2. **XTDB Mock Strategy Audit**

**Pytanie:** Jak obecne testy mockują XTDB?

**Patterns to check:**
```clojure
;; Pattern 1: Mock XTDB with with-redefs (BAD — fragile)
(with-redefs [xt/q (fn [_ _] [[:mock-id]])]
  (test-function))

;; Pattern 2: In-memory XTDB (GOOD — real behavior)
(with-open [node (xt/start-node {})]
  (let [db (xt/db node)]
    (test-function db)))

;; Pattern 3: Fixtures (BEST — reusable)
(use-fixtures :each
  (fn [f]
    (with-open [node (xt/start-node {})]
      (binding [*test-node* node]
        (f)))))
```

**Action:**
1. Grep test files for `xt/` usage
2. Check czy używają in-memory XTDB czy mocks
3. If mocks → **refactor to in-memory** (better coverage, less fragile)

---

#### 3. **E2E Test Gap Analysis**

**Pytanie:** Czy istnieją E2E tests dla summaries/products flows?

**Check:**
```bash
find tests -name "*.spec.ts" -o -name "*e2e*" -o -name "*integration*"
ls -la tests/  # Check for Playwright/Cypress config
```

**Expected:** Territory.md nie wspomina E2E tests → prawdopodobnie **brak**.

**Action:**
1. If brak E2E → **P0: Setup Playwright** (summaries flow = core journey)
2. If istnieją → audit coverage (które flows, które edge cases)
3. Design E2E test plan (happy paths + critical errors)

---

### Medium-Term Actions (P1)

#### 4. **Services Unit Test Coverage**

**Target:** Services generation, summary, product-csv, csv-import

**Why:** Ca=0 Ce=0 (perfect isolation) + only XTDB dep = **easiest to test**

**Action:**
1. Write unit tests z XTDB mock dla każdego service function
2. Coverage: guard clauses, entity creation, error handling, RLS
3. Target: **90%+ coverage** (services są foundation)

---

#### 5. **Integration Test Harness dla Pages**

**Target:** summaries-view, products pages

**Pattern:**
```clojure
;; test/com/apriary/test_helpers.clj
(defn with-test-system [f]
  (with-open [node (xt/start-node {})]
    (let [ctx {:biff.xtdb/node node
               :biff/db (xt/db node)
               :session {:uid (UUID/randomUUID)}}]
      (f ctx))))

;; test/com/apriary/pages/summaries_view_test.clj
(use-fixtures :each with-test-system)

(deftest summaries-list-page-test
  (fn [ctx]
    (create-test-data! (:biff.xtdb/node ctx))
    (let [response (summaries-view/summaries-list-page ctx)]
      (is (= 200 (:status response))))))
```

**Action:**
1. Create reusable test fixtures (XTDB node, test user, test data)
2. Write integration tests dla każdego page handler
3. Coverage: happy path + error cases (RLS, validation, not found)

---

#### 6. **Util 100% Coverage**

**Target:** com.apriary.util (Ca=5 = critical shared code)

**Why:** Pure functions + zero deps = **easiest high-value tests**

**Action:**
1. Inventory wszystkich funkcji w util
2. Write unit tests dla każdej (input → output assertions)
3. Edge cases: nil handling, invalid input, boundary conditions
4. Target: **100% coverage** (Ca=5 = must be bulletproof)

---

### Long-Term Actions (P2)

#### 7. **Refactor summaries-view — Reduce Coupling**

**Problem:** 19 deps = testing nightmare

**Refactor ideas:**
1. **Extract CSV import logic** → osobny page/handler (reduce deps w summaries-view)
2. **Extract generation logic** → osobny handler (summaries-view nie powinien wywołać 4 services)
3. **Composable handlers** — małe, focused functions zamiast monolithic handlers

**Before:**
```clojure
(defn summaries-list-page [ctx]
  (let [summaries (summary-service/list-summaries ...)
        generations (gen-service/list-user-generations ...)
        ...16 lines of logic...]
    (layout/app-page ...)))
```

**After:**
```clojure
(defn fetch-summaries-data [db user-id]
  ;; Pure function — testowalne unit
  {:summaries (summary-service/list-summaries db user-id)
   :generations (gen-service/list-user-generations db user-id)})

(defn render-summaries-page [ctx data]
  ;; Render logic — testowalne z mock data
  (layout/app-page ctx ...))

(defn summaries-list-page [ctx]
  ;; Thin handler — integration test only
  (let [data (fetch-summaries-data (:biff/db ctx) (:uid (:session ctx)))]
    (render-summaries-page ctx data)))
```

**Benefits:**
- `fetch-summaries-data` = unit testowalne (mock services)
- `render-summaries-page` = unit testowalne (mock data, no services)
- `summaries-list-page` = thin integration (only composition)

---

#### 8. **Mutation Testing dla Services**

**Tool:** [test.check](https://github.com/clojure/test.check) lub custom mutation

**Target:** Services (generation, summary) — już mają testy, ale czy testy są **good**?

**Mutations:**
- Change `>` → `>=` (boundary conditions)
- Remove guard clauses (nil checks)
- Swap [:ok ...] → [:error ...] (contract violations)

**Question:** Czy testy **catch mutations**?

**Action:**
1. Run mutation testing na services
2. Identify weak tests (mutations survive)
3. Add test cases dla uncaught mutations

---

## Opcjonalny Kolejny Krok: Graf

### Dependency Graph for Testability Visualization

**Jeśli chcesz wizualizacji:**

```bash
# Generate dependency graph z testability scores
bb scripts/analyze-deps.clj dot | \
  # Color nodes by testability score (red = high, green = low)
  sed 's/summaries-view/summaries-view [color=red, penwidth=3]/' | \
  sed 's/pages.products/pages.products [color=orange, penwidth=2]/' | \
  sed 's/services.generation/services.generation [color=yellow]/' | \
  sed 's/util/util [color=green]/' | \
  dot -Tsvg > context/map/testability-graph.svg
```

**Graf pokaże:**
- **Red nodes:** High testability risk (summaries-view) — dużo deps, trudno testować
- **Green nodes:** Low risk (util, services) — mało deps, łatwo testować
- **Edges:** Dependencies — ile mocks potrzeba dla unit test

**Użyteczność:**
- Visual identification hot zones dla refactoringu
- Dependency flow — które moduły ciągną za sobą cascade deps
- Testability clusters — które moduły można testować razem (integration)

---

## Podsumowanie Strategii Testowej

### Test Pyramid dla Apriary

```
           /\
          /E2E\          ← Summaries flow, Products flow (Playwright)
         /------\           2-3 critical user journeys
        /  INT  \        ← Pages + Services + XTDB (in-memory)
       /----------\         10-20 integration tests
      /    UNIT    \     ← Services (mock XTDB) + Util (pure)
     /--------------\      50-100 unit tests
```

**Distribution (recommended):**
- **70% Integration** — pages + services + real XTDB (in-memory)
- **20% Unit** — services (mock XTDB) + util (pure functions)
- **10% E2E** — critical flows (Playwright)

**Rationale:**
- **Integration > Unit** bo większość kodu ma XTDB/Rum deps → mockowanie fragile/expensive
- **E2E for critical paths** bo summaries/products to core journeys → regression protection
- **Unit dla pure logic** (services business logic, util helpers) bo easy + fast + high ROI

---

### Prioritized Test Writing Order

| Priority | Target | Type | Effort | Value | Reason |
|----------|--------|------|--------|-------|--------|
| **P0** | Util | Unit | LOW | HIGH | Ca=5, pure functions, zero mocks, fast |
| **P0** | Services (generation, summary) | Unit | MEDIUM | HIGH | Ca=0 Ce=0, only XTDB mock, foundation |
| **P1** | Summaries E2E flow | E2E | HIGH | CRITICAL | Core journey (9 zmian), regression risk |
| **P1** | Pages integration | Integration | MEDIUM | HIGH | Real XTDB + services, coverage handlers |
| **P2** | Products E2E flow | E2E | MEDIUM | MEDIUM | Q2 frontier (4 zmiany), active dev |
| **P3** | Middleware smoke | Smoke | LOW | MEDIUM | Ca=7 ALE stable (low churn) |

---

### Territory.md Context — Why This Matters

**Q4 2025 (feature-first):**
> Test/src ratio 1:4.9 — ship features fast, add tests later

**Result:** Summaries (hotspot 9 zmian) prawdopodobnie **brak comprehensive tests** → tech debt.

**Q2 2026 (test-first pivot):**
> Test/src ratio 1.4:1 — more tests than code

**Result:** Services mają 63% w-commit coverage → prawdopodobnie **dobry unit test coverage**.

**Gap:** Pages/UI **17% w-commit** → **brak integration/E2E tests**.

**Action:** Focus na **pages integration** + **E2E dla core flows** = biggest gap, highest value.

---

**Status:** ✅ Analiza zakończona  
**Next:** Inventory existing tests → prioritize gaps → implement P0/P1 tests  
**Blocking question:** Czy istnieją już E2E tests? (check `tests/` directory)
