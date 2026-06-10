# Artifact 2: Structure — Architektura, Zależności i Testowalność

**Zakres:** Analiza struktury projektu, granic warstw, ryzyk testowalności  
**Data analizy:** 2026-06-09  
**Metoda:** clj-kondo analysis + cross-reference z artifact-1-territory.md  
**Narzędzia:** dependency-cruiser alternatywa (clj-kondo), custom analysis scripts

---

## Executive Summary

**Status projektu:** ✅ **Czysta architektura** (brak cykli) ALE ❌ **Orphan foundations** (schema.api nieużywany) + ⚠️ **Fragmentary validation** (tylko summaries)

### Kluczowe Liczby

| Metryka | Wartość | Ocena | Znaczenie |
|---------|---------|-------|-----------|
| **Cykle zależności** | 0 | ✅ Excellent | Acykliczna architektura = łatwe reasoning |
| **God Page dependencies** | 12 (summaries-view) | 🔴 Critical | Największy fan-out w projekcie |
| **Orphan code** | 132 linie (schema.api) | ❌ Bad | Planned foundation nigdy nie adopted |
| **Frontend validation** | 12.5% (1/8 pages) | ❌ Bad | Tylko summaries ma Malli guards |
| **Testability Score (max)** | 25 (summaries-view) | 🔴 Critical | 19 deps = testing nightmare |
| **Services isolation** | Ca=0 Ce=0 | ✅ Excellent | Perfect isolation, testowalne |

---

## Struktura w Liczbach

### Dependency Metrics (Hot Zones)

| Module | Total Deps | External | Internal | Ca (afferent) | Ce (efferent) | Instability |
|--------|-----------|----------|----------|---------------|---------------|-------------|
| **summaries-view** | 19 | 7 | 12 | 0 | 12 | 1.00 (unstable) |
| **middleware** | 4 | 4 | 0 | 7 | 0 | 0.00 (stable) |
| **util** | 1 | 1 | 0 | 5 | 0 | 0.00 (stable) |
| **products** | 9 | 3 | 6 | 0 | 8 | 1.00 (unstable) |
| **services.generation** | 3 | 3 | 0 | 4 | 0 | 0.00 (stable) |

**Interpretacja:**
- **Pages (summaries, products):** High instability (I=1.00) = końcówki aplikacji, łatwo zmieniać
- **Middleware, util:** Perfect stability (I=0.00) = foundation, zmiana wpływa na wiele modułów
- **Services:** Stable (Ca>0, Ce=0 internal) = business logic dobrze izolowana

---

## Trzy Kluczowe Odkrycia

### 1. ✅ Brak Cykli = Excellent Foundation

**Wniosek:** Żaden namespace nie tworzy circular dependency.

**Znaczenie:**
- ✅ Dependencies flow w jednym kierunku → łatwiejsze reasoning
- ✅ Możliwość izolowanego testowania modułów
- ✅ Mniejsze ryzyko cascade failures
- ✅ Łatwiejszy refactoring (można zmieniać "liście" bez touching "root")

**Contrast z typowym legacy:**
- ❌ Legacy często ma cycles między UI ↔ Services ↔ Data
- ❌ Cycles = trudno mockować dependencies w testach
- ❌ Cycles = reasoning nightmare

**Związek z territory.md:**
> Projekt ewoluował przez 7 miesięcy active development (Q4 2025 + Q2 2026) bez wprowadzenia cykli — świadczy o **discipline w architekturze**.

---

### 2. ❌ schema.api to "Ghost Architecture"

**Wniosek:** 132 linie Malli schemas zaprojektowane jako fundament walidacji API, ale **zero importów** w całym projekcie.

**Co się stało:**

**Q4 2025 (feature sprint):**
1. Schema.api **created** jako planned foundation
2. Summaries-view **developed równolegle** z inline schemas (szybciej)
3. Nigdy nie było refactor time ("use schema.api instead of inline")

**Evidence:**
- `schema.api` definiuje `create-manual-summary-schema` (linie 26-37)
- `summaries_view.clj` definiuje **identyczny** schema (linia 334)
- **Byte-for-byte duplicate** = schema.api powstał jako template, summaries skopiował

**Impact:**
```
Planned architecture ≠ Actual architecture
```

**Duplication risk:**
- Zmiana validation rules (np. content 50-50k → 50-100k) = update w **2 miejscach**
- Schema.api może drift from reality (bo nikt go nie używa)
- Brak single source of truth

**Związek z territory.md:**
> "Q4 2025 feature-first (test/src 1:4.9)" / "Ship features fast"

Q4 był **move fast** mode → schema.api planned ALE nie integrated (time pressure).

---

### 3. 🔴 summaries-view to "God Page"

**Wniosek:** Summaries-view ma **największy fan-out** (12 internal deps) + **highest testability risk** (score 25).

**Zależności (12 internal):**
1. **Services (4):** summary, csv-import, openrouter, generation
2. **UI components (6):** layout, helpers, csv-import, summary-card, summaries-list, util
3. **DTOs (1):** dto.summary
4. **Infrastructure (1):** middleware

**+ 7 external:** xtdb, rum, malli (2), cheshire, clojure.string, logging

**Visual analysis:** Zobacz `structure/god-page-dependencies.svg` i `structure/god-page-visualization.md` dla graficznej reprezentacji tego problemu.

**Dlaczego to problem:**

**Testing nightmare:**
- Unit test = **7-10 mocks per handler** × 16 handlers = 112+ mock setups
- Mock cascade: service → DTO → XTDB → Rum rendering
- Fragile tests: każda zmiana w service contract = update all mocks

**Integration hell:**
- Każda zmiana w którymkolwiek z 12 deps może wymagać update summaries-view
- Trudno isolate changes (touching service → must check summaries-view impact)
- Bug w jednym dep → objawia się w summaries-view

**Związek z territory.md:**
> "Summaries-view absolutny hotspot (9 zmian)" / "32% top-3 zmian w jednym pliku"

God Page + highest churn = **maximum risk zone** w projekcie.

---

## Wzorce Architektoniczne

### Pattern 1: Pages-Driven Development

```
         pages (22% wszystkich commits)
           ├─ 63% → ui (page-specific components)
           ├─ 37% → core (routing integration)
           └─ 32% → services (business logic wiring)
```

**Pages są punktem integracji** całego stacku.

**Consequence:**
- ✅ Clear entry points (każdy feature zaczyna się od page)
- ❌ God Pages (summaries-view ma 12 deps)
- ⚠️ Testing challenge (pages trudno unit testować, lepiej integration)

---

### Pattern 2: UI Components = Tightly Coupled

**Wniosek:** 100% UI changes dotyka pages (12/12 commitów z territory.md).

**Evidence:**
- `ui.layout` → używany przez 4 pages
- `ui.summary-card` → używany przez summaries-view, summaries-list
- `ui.summaries-list` → używany tylko przez summaries-view
- **Zero standalone** UI components

**Implication:**
- ❌ Brak reusability → duplication risk (jeśli products i summaries potrzebują podobnego card)
- ✅ UI pure presentation → zero validation logic (correct separation)
- ⚠️ 100% coupling = nie możesz zmienić UI bez checking pages context

**Związek z territory.md:**
> "UI components NIE ISTNIEJĄ bez pages — każda zmiana w ui/ to zmiana w kontekście konkretnego page"

---

### Pattern 3: Services Perfect Isolation

**Wniosek:** Wszystkie services mają **Ca=0 Ce=0** (internal deps).

**Evidence:**
- `services.generation` → tylko XTDB, clojure.string, logging (zero internal)
- `services.summary` → tylko XTDB, clojure.string, logging
- **Żaden service nie używa innego service**

**Implication:**
- ✅ Perfect testability (tylko XTDB mock needed)
- ✅ Safe to modify (brak internal coupling)
- ⚠️ Może być logic duplication (services don't share code)

**Związek z territory.md:**
> "Services layer = safest to modify" / "Best test discipline (63% w-commit coverage)"

---

### Pattern 4: Middleware = Single Point of Failure

**Wniosek:** Middleware ma **Ca=7** (wszystkie pages zależą) ALE **Ce=0** (zero internal deps).

**Evidence:**
- 7 pages importuje middleware
- Middleware importuje tylko external (ring, biffweb, muuntaja)
- **Zero business logic** w middleware (tylko framework wiring)

**Implication:**
- ✅ Clean separation (middleware to thin layer)
- ❌ Blast radius (bug w middleware → all pages broken)
- ⚠️ Cold zone (low churn) ALE critical (Ca=7)

**Związek z territory.md:**
> "Middleware stable (I=0.00)" / "Cold zone (niska aktywność) ALE Ca=7"

---

## Łamanie Granic Warstw

### Granica #1: Frontend → schema.api (BROKEN)

**Expected:** Pages używają schema.api dla request/response validation

**Reality:** 
- ❌ Schema.api ma **zero importów**
- ❌ Summaries-view **duplicates** schema.api (create-manual-summary-schema)
- ❌ Products/rankings **zero** frontend validation

**Impact:**
```
| Validation Coverage | Status |
|---------------------|--------|
| summaries-view      | ✅ Inline Malli (duplicate of schema.api) |
| products            | ❌ Backend-only |
| rankings            | ❌ Backend-only |
| other pages         | ❌ Backend-only |
```

**Overall:** 12.5% pages (1/8) ma frontend validation

**Dlaczego to problem:**
1. **Duplication:** summaries schema w 2 miejscach → drift risk
2. **Poor UX:** products/rankings brak client-side validation → errors post-submit only
3. **Quality regression:** Q4 summaries ma validation, Q2 products nie ma

**Związek z territory.md:**
> "Q4 feature-first (1:4.9)" / "Q2 test-first (1.4:1) ALE focus na backend tests"

Q2 test pivot **nie** obejmował frontend validation → gap.

---

### Granica #2: schema.clj ↔ schema.api (DISCONNECT)

**Expected:** Entity schemas (XTDB) i API schemas (validation) są synchronized

**Reality:**
- `schema.clj` — używany przez core app (malli registry)
- `schema.api` — **orphan** (zero usage)
- **No shared types** między nimi

**Example drift risk:**
```clojure
;; schema.clj (XTDB entity)
:summary/content [:string {:min 50 :max 10000}]

;; schema.api (API validation)
:content [:string {:min 50 :max 50000}]
```

**Different max lengths!** (10k vs 50k) — który jest correct?

**Impact:**
- Entity schema może drift from API schema
- Zmiana data model = update **2 schemas** manually
- No single source of truth

---

### Granica #3: DTOs → Validation (MISSING)

**Expected:** DTOs validate service output przed passing do pages

**Reality:**
- ❌ DTOs **nie** importują schema.api
- ❌ DTOs **nie** importują Malli
- DTOs są **pure transformation** (no validation checkpoint)

**Flow:**
```
services → dto.summary → pages.summaries-view
         (no validation checkpoint)
```

**Risk:**
- Service bug (malformed data) → DTO passes through → może crash rendering
- Schema.api definiuje `summary-dto-schema` ALE dto.summary go nie używa

---

## Ryzyka Testowalności

### Testability Score Ranking

| Rank | Module | Score | Issue | Test Strategy |
|------|--------|-------|-------|---------------|
| 🥇 #1 | **summaries-view** | 🔴 25 | 19 deps (XTDB+Rum+4 services+6 UI+Malli) | **E2E priority** + Integration |
| 🥈 #2 | **pages.products** | 🟡 18 | 9 deps (XTDB+Rum+3 services) | **Integration** + E2E |
| 🥉 #3 | **services.generation** | 🟡 16 | 3 deps (all external: XTDB only) | **Unit** + Integration |
| #4 | **middleware** | 🟢 7 | 4 deps (Ring/HTTP only) | **Smoke** + implicit E2E |
| #5 | **util** | 🟢 4 | 1 dep (clojure.string) | **Unit** (100% required) |

**Scoring:** External deps + XTDB×10 + Rum×5 + HTTP×3

---

### Gdzie Mockować vs Integration vs E2E

#### ❌ Unit Testing Praktycznie Niemożliwe:

**summaries-view:**
- 16 handlers × 7-10 mocks = **112+ mock setups**
- Mock cascade: service → DTO → XTDB schema → Rum rendering
- **Fragile:** każda service contract change = update all mocks

**Verdict:** Unit testing z mocks = bad ROI

---

#### ✅ Unit Testing Sensowne:

**Services (generation, summary, csv-import):**
- **1 mock** (XTDB node/db)
- Pure logic (guard clauses, entity creation)
- Clear contracts ([:ok ...] vs [:error ...])

**Util:**
- **0 mocks** (pure functions)
- Fast tests (ms per test)
- Ca=5 = must test

**Verdict:** Unit testing = perfect fit

---

#### ✅ Integration Tests — Best Balance:

**Pages (summaries-view, products):**
- Real XTDB (in-memory)
- Real services
- Mock tylko HTTP externes (openrouter)
- **Coverage:** całej flow pages → services → XTDB

**Example pattern:**
```clojure
(deftest summaries-integration
  (with-xtdb-node [node (xt/start-node {})]
    (let [ctx {:biff/db (xt/db node) ...}]
      (testing-handler ctx))))
```

**Benefits:**
- Real XTDB = tests actual queries
- Real services = tests contracts
- Fast (in-memory) = seconds per suite

---

#### ✅ E2E Tests — Critical Flows:

**Summaries flow:**
1. CSV import → generation → list
2. Edit → save → verify
3. Accept/reject AI suggestions

**Products flow:**
1. CSV import → products list
2. View rankings

**Verdict:** Playwright tests dla happy paths + critical errors

---

## Recommended Test Strategy

### Test Pyramid dla Apriary

```
           /\
          /E2E\          ← Summaries + Products flows (2-3 journeys)
         /------\
        /  INT  \        ← Pages + Services + XTDB (10-20 tests)
       /----------\
      /    UNIT    \     ← Services + Util (50-100 tests)
     /--------------\
```

**Distribution:**
- 70% Integration (pages + services + real XTDB)
- 20% Unit (services + util)
- 10% E2E (critical flows)

**Rationale:**
- Integration > Unit bo większość kodu ma XTDB/Rum deps
- E2E dla core journeys (summaries = 9 zmian hotspot)
- Unit dla pure logic (services, util)

---

## Cross-Cutting Concerns

### Concern #1: XTDB Usage (Wszędzie)

**Używany przez:**
- All pages (summaries-view, products, rankings)
- All services (generation, summary, product, csv-import)
- **16/36 namespaces** ma XTDB dependency

**Impact na testowanie:**
- ❌ Unit testing wymaga XTDB mock (complex)
- ✅ Integration testing z in-memory XTDB (real behavior, fast)
- Best practice: **Reusable test fixtures** (with-xtdb-node)

---

### Concern #2: Rum Rendering (Pages Only)

**Używany przez:**
- summaries-view, products (page rendering)
- **2/8 pages** ma Rum dependency

**Impact na testowanie:**
- ❌ Unit testing Rum rendering = trudne (server-side HTML)
- ✅ Integration testing = render actual HTML, assert strings
- ⚠️ E2E testing = Playwright, full browser rendering

---

### Concern #3: Malli Validation (Fragmentary)

**Używany przez:**
- summaries-view (inline schema)
- **1/8 pages** ma Malli dependency

**Gap:**
- Products, rankings, other pages = **zero** frontend validation
- Schema.api orphan = **zero** usage

**Impact:**
- Backend-only validation = poor UX
- Duplication (summaries inline vs schema.api)

---

## Priorytetowe Akcje

### P0 (Critical — Do Immediately)

#### 1. **Inventory Existing Tests**

**Action:**
```bash
find test/com/apriary -name "*test.clj" | xargs grep -l "summaries-view\|products\|generation"
```

**Expected z territory.md:**
- Services: 63% w-commit → prawdopodobnie mają unit tests
- Pages: 16% w-commit → prawdopodobnie **brak** integration
- UI: 17% w-commit → prawdopodobnie **brak** tests

**Gap analysis:**
- Które hot zones (summaries 9 zmian, products 4) mają testy?
- Coverage: ile handlers/functions ma tests?

---

#### 2. **Consolidate summaries-view → schema.api**

**Problem:** Duplicate `create-manual-summary-schema` w 2 miejscach

**Action:**
```clojure
;; summaries_view.clj - BEFORE
(def create-manual-summary-schema [...])

;; summaries_view.clj - AFTER
(ns com.apriary.pages.summaries-view
  (:require [com.apriary.schema.api :as api-schema]))

;; Use api-schema/create-manual-summary-schema
```

**Benefit:**
- Eliminate duplication
- Single source of truth
- Schema.api becomes **used** (not orphan)

---

#### 3. **Setup E2E Framework (Playwright)**

**Target:** Summaries flow (core journey, 9 zmian hotspot)

**Action:**
1. Install Playwright
2. Write **1 happy path** test (CSV import → view → edit → accept)
3. Run w CI

**ROI:** High — summaries hotspot = regression risk, E2E catch integration bugs

---

### P1 (High Priority — This Sprint)

#### 4. **Integration Tests dla Pages**

**Target:** summaries-view (16 handlers), products (8 handlers)

**Pattern:**
```clojure
(use-fixtures :each with-test-system)  ; XTDB + test user

(deftest summaries-list-integration
  (create-test-data!)
  (let [response (summaries-view/summaries-list-page test-ctx)]
    (is (= 200 (:status response)))))
```

**Coverage:** Happy path + error cases (RLS, validation, not found)

---

#### 5. **Unit Tests dla Services**

**Target:** generation, summary, product-csv, csv-import

**Pattern:**
```clojure
(deftest create-generation-unit
  (with-redefs [xt/submit-tx (fn [_ _] :mocked)]
    (let [[status result] (generation/create-generation ...)]
      (is (= :ok status)))))
```

**Coverage:** Guard clauses, entity creation, error handling, RLS

**Target:** 90%+ coverage (services są foundation)

---

#### 6. **Add Frontend Validation dla Products**

**Problem:** Products zero Malli validation (poor UX)

**Action:**
1. Define products schemas w schema.api (nie inline!)
2. Import schema.api w pages.products
3. Validate forms (similar to summaries pattern)

**Benefit:**
- Better UX (real-time validation)
- Consistency (schema.api usage)
- Q2 frontier (4 zmiany) → prevent bugs early

---

### P2 (Medium — Next Sprint)

#### 7. **Util 100% Coverage**

**Target:** com.apriary.util (Ca=5 = critical shared code)

**Action:**
1. Inventory wszystkich funkcji
2. Unit test każdej (pure functions, zero mocks)
3. Edge cases: nil, invalid input, boundaries

**Target:** 100% coverage (Ca=5 = must be bulletproof)

---

#### 8. **Sync schema.clj ↔ schema.api**

**Problem:** Entity schemas (db) vs API schemas (validation) disconnect

**Action:**
1. Audit drift: porównaj `:summary` vs `summary-dto-schema`
2. Check content lengths (db: 10k, API: 50k — które correct?)
3. Design sync strategy (API derive from entity?)

---

#### 9. **Refactor summaries-view — Reduce Coupling**

**Problem:** 19 deps = testing nightmare

**Ideas:**
1. Extract CSV import logic → osobny handler
2. Extract generation logic → reduce service calls w summaries-view
3. Composable handlers — małe functions zamiast monolithic

**Before:** 1 handler = 12 deps  
**After:** fetch-data (testowalne unit) + render (testowalne mock) + thin handler (integration only)

---

## Metryki Sukcesu

### Przed Refaktorem (Obecny Stan)

| Metryka | Wartość | Status |
|---------|---------|--------|
| Test coverage (overall) | ~30%? | ⚠️ Unknown |
| Pages integration tests | 0? | ❌ Gap |
| E2E tests | 0? | ❌ Gap |
| Services unit tests | Niektóre (63% w-commit) | ⚠️ Partial |
| schema.api usage | 0% (0/8 pages) | ❌ Orphan |
| Frontend validation | 12.5% (1/8 pages) | ❌ Low |
| God Pages (12+ deps) | 1 (summaries-view) | ⚠️ Risk |

---

### Po Refaktorze (Target)

| Metryka | Target | Status |
|---------|--------|--------|
| Test coverage (overall) | 80%+ | 🎯 Goal |
| Pages integration tests | 10-20 tests | 🎯 Goal |
| E2E tests | 2-3 critical flows | 🎯 Goal |
| Services unit tests | 90%+ coverage | 🎯 Goal |
| schema.api usage | 100% (8/8 pages) | 🎯 Goal |
| Frontend validation | 75%+ (6/8 pages) | 🎯 Goal |
| God Pages (12+ deps) | 0 (refactor to <8 deps) | 🎯 Goal |

---

## Związki z Territory Map (artifact-1)

### Q4 2025: Feature Sprint (Consequences)

**Charakterystyka z territory.md:**
- 32 commits, test/src **1:4.9** (ship fast)
- Summaries hotspot (9 zmian)

**Structural impact:**
- ❌ Schema.api created ALE not integrated (time pressure)
- ❌ Summaries-view God Page (12 deps, fast iteration)
- ✅ Services clean (isolation maintained despite rush)

**Lesson:** Fast shipping → tech debt w pages (God Page), nie w services (isolation preserved)

---

### Q2 2026: Test Pivot (Gaps)

**Charakterystyka z territory.md:**
- 53 commits, test/src **1.4:1** (test-first)
- Products pivot (0 → 4 zmiany)

**Structural impact:**
- ✅ Services 63% w-commit coverage (backend tests!)
- ❌ Products zero frontend validation (backend-only focus)
- ❌ Pages 16% w-commit (integration tests missing)

**Lesson:** Test pivot **partial** — backend improved, frontend validation regressed

---

### 3-Month Gap (Q1 2026): Context Loss?

**Charakterystyka z territory.md:**
- Zero commits styczeń-kwiecień

**Hypothesis:**
- Team composition change?
- Schema.api plan lost during gap?
- Q2 team nie wiedział o schema.api intent?

**Evidence:**
- Q2 products nie używa schema.api (nie był aware?)
- Schema.api orphan (nigdy nie picked up after gap)

---

## Wnioski — Mapa Struktury Projektu

### Strengths (Co Działa Dobrze)

1. ✅ **Brak cykli** — czysta, acykliczna architektura
2. ✅ **Services isolation** — Ca=0 Ce=0, perfect testability
3. ✅ **UI separation** — zero validation logic, pure presentation
4. ✅ **Clear layering** — pages → ui → services → db
5. ✅ **Middleware thin** — zero business logic, tylko framework wiring

---

### Weaknesses (Tech Debt)

1. ❌ **Schema.api orphan** — 132 linie unused code, duplication w summaries
2. ❌ **God Page** — summaries-view 12 deps, testing nightmare
3. ❌ **Fragmentary validation** — tylko 1/8 pages ma frontend guards
4. ❌ **Schema drift risk** — schema.clj vs schema.api disconnect
5. ❌ **DTO no validation** — brak defensive checkpoint

---

### Opportunities (Quick Wins)

1. 🎯 **Consolidate summaries → schema.api** — eliminate dup w hotspot
2. 🎯 **E2E dla summaries flow** — core journey, high regression risk
3. 🎯 **Unit tests dla util** — Ca=5, pure functions, zero mocks, fast
4. 🎯 **Services unit coverage** — 1 XTDB mock, clear contracts, foundation
5. 🎯 **Products frontend validation** — Q2 frontier, prevent UX bugs

---

### Threats (Risks)

1. ⚠️ **Summaries-view churn** — 9 zmian + 12 deps = maximum risk zone
2. ⚠️ **Products active dev** — 4 zmiany Q2 + zero validation = bug risk
3. ⚠️ **Test gap** — 16% pages w-commit = integration tests missing
4. ⚠️ **Middleware blast radius** — Ca=7, bug → all pages broken
5. ⚠️ **Q2 regression** — test pivot nie obejmował frontend quality

---

## Następne Kroki

### Immediate (This Week)

1. ✅ **Read this artifact** — understand structural risks
2. 🔍 **Inventory tests** — `find test/ -name "*test.clj"`
3. 🔍 **Check E2E setup** — `ls tests/` for Playwright/Cypress
4. 📝 **Document gaps** — które hot zones nie mają testów?

---

### Short-Term (This Sprint)

1. ⚙️ **Setup Playwright** — E2E framework dla summaries flow
2. 🧪 **Write util tests** — 100% coverage (Ca=5, pure functions)
3. 🧪 **Services unit tests** — generation, summary (mock XTDB)
4. 📋 **Plan refactor** — summaries-view God Page → composable handlers

---

### Medium-Term (Next Sprint)

1. 🔄 **Consolidate schema.api** — summaries import, not duplicate
2. 🧪 **Pages integration** — summaries + products handlers (real XTDB)
3. 🛡️ **Products validation** — frontend guards (schema.api usage)
4. 🔍 **Schema audit** — sync schema.clj ↔ schema.api

---

### Long-Term (Next Quarter)

1. 🏗️ **Refactor summaries-view** — reduce 12 deps → <8
2. 🧪 **E2E suite** — summaries + products critical flows
3. 🛡️ **DTO validation** — defensive checkpoint (schema.api DTOs)
4. 📊 **Mutation testing** — services quality verification

---

## Pliki Wygenerowane w Tej Sesji

```
context/map/
├── artifact-2-structure.md              ← Ten plik (executive summary)
└── structure/                            ← Supporting analysis files
    ├── dependency-summary.md             ← Cykle + coupling analysis
    ├── dependency-analysis-active-zones.md ← Szczegółowa analiza hot zones
    ├── layer-boundaries-summary.md       ← Schema.api orphan analysis
    ├── layer-boundaries-analysis.md      ← Pełna analiza granic warstw
    ├── testability-risks-analysis.md     ← Ryzyka testowalności
    ├── dependencies.json                 ← Surowe dane dependency analysis
    ├── stability-metrics.txt             ← Metryki stabilności namespace'ów
    ├── namespace-dependencies.dot        ← Full namespace graph (DOT)
    ├── god-page-dependencies.dot         ← Graf DOT: summaries-view dependencies
    ├── god-page-dependencies.svg         ← Rendered SVG visualization (16KB)
    └── god-page-visualization.md         ← God Page analysis + viewing guide
```

---

## Narzędzia Użyte

### Custom Scripts (bb scripts/)

1. **analyze-deps.clj** — dependency analysis z clj-kondo
2. **detect-cycles.clj** — Tarjan's algorithm dla circular deps
3. **analyze-testability.clj** — testability risk scoring

### Komendy Diagnostyczne

```bash
# Dependency analysis
clj-kondo --lint src/com/apriary --config '{:output {:analysis {...}}}'

# Schema.api usage check
grep -r "schema.api\|schema/api" src/

# Malli usage check
grep -r "malli" src/com/apriary/pages src/com/apriary/ui

# Test inventory
find test/ -name "*test.clj" | xargs grep -l "summaries\|products"

# Generate dependency graph visualization
dot -Tsvg context/map/structure/god-page-dependencies.dot -o context/map/structure/god-page-dependencies.svg
```

---

## Podsumowanie dla Agentic Work

### Przed Dotknięciem Hot Zones:

**summaries-view (9 zmian):**
1. ✓ Read structure/dependency-analysis-active-zones.md (12 deps = integration hell)
2. ✓ Check existing tests (prawdopodobnie brak integration)
3. ✓ Write integration test BEFORE change (regression guard)
4. ⚠️ Każda zmiana = risk breaking 12 dependencies

**products (4 zmiany Q2):**
1. ✓ Read structure/layer-boundaries-analysis.md (zero frontend validation)
2. ✓ Add frontend validation BEFORE new features
3. ✓ Integration tests dla handlers (real XTDB + services)
4. ⚠️ Active dev = bugs likely, tests critical

**middleware (Ca=7):**
1. ✓ Read structure/testability-risks-analysis.md (smoke tests sufficient)
2. ⚠️ Zmiana w middleware = all 7 pages must be smoke tested
3. ✓ E2E tests implicitly cover middleware (każdy page test)
4. ❌ Don't change unless absolutely necessary (stable, low churn)

---

## Status Artefaktu

**Kompletność:** ✅ Pełna analiza struktury (dependencies, layers, testability)  
**Weryfikacja:** ✅ Cross-referenced z artifact-1-territory.md  
**Następny krok:** Implement P0 actions (inventory tests, consolidate schema.api, setup E2E)

---

**Ostatnia aktualizacja:** 2026-06-09  
**Maintainer:** Update po każdym refactorze struktury (God Page split, schema.api integration)  
**Related artifacts:**
- `artifact-1-territory.md` — git history, active zones, churn analysis
- `structure/dependency-summary.md` — quick reference dla dependency metrics
- `structure/testability-risks-analysis.md` — testing strategy per module
- `structure/god-page-visualization.md` — visual analysis of God Page problem
