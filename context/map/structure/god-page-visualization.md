# God Page Dependencies — Visual Analysis

**Plik:** `god-page-dependencies.svg`  
**Pytanie:** Dlaczego summaries-view ma testability score 25 (🔴 Critical)?  
**Źródło:** artifact-2-structure.md §3 "summaries-view to God Page"

---

## Co Pokazuje Ten Graf

Graf wizualizuje **największe ryzyko testowalności** w projekcie Apriary:

- **Centralny węzeł (czerwony):** `summaries-view` — God Page z 12 internal deps + 7 external
- **Zielone bloki:** Services layer (4 serwisy) — Ca=0 Ce=0, perfect isolation
- **Niebieskie bloki:** UI components (6 komponentów) — pure presentation
- **Żółty:** dto.summary — data transformation layer
- **Pomarańczowy:** middleware — Ca=7 (wszystkie pages zależą)
- **Szary 3D:** External dependencies (XTDB, Rum, Malli, etc.)

---

## Testability Score Breakdown

```
summaries-view = 25 points (🔴 Critical)
├─ XTDB dependency:        10 points (database mock needed)
├─ Rum dependency:          5 points (rendering complexity)
├─ 4 Services:              4 points (business logic mocking)
├─ 6 UI components:         3 points (presentation layer)
├─ 1 DTO:                   1 point  (transformation)
├─ 1 Middleware:            1 point  (HTTP handling)
└─ 7 External libs:         1 point  (framework dependencies)
```

**Scoring formula:** External deps + XTDB×10 + Rum×5 + HTTP×3

---

## Dlaczego To Problem

### Testing Nightmare

**Unit testing praktycznie niemożliwe:**
- 16 handlers × 7-10 mocks per handler = **112+ mock setups**
- Mock cascade: service → DTO → XTDB schema → Rum rendering
- **Fragile tests:** każda service contract change = update all mocks

**Example mock hell:**
```clojure
(deftest summaries-list-handler-test
  (with-redefs [xt/db               (constantly mock-db)
                xt/q                mock-q-fn
                summary/list-all    mock-list
                dto/transform       mock-dto
                rum/render-html     mock-render
                layout/page         mock-layout
                summary-card/view   mock-card
                summaries-list/view mock-list-view]
    ;; Test logic here... 8 mocks to maintain!
    ))
```

### Integration Hell

- Każda zmiana w którymkolwiek z 12 deps może wymagać update summaries-view
- Trudno isolate changes (touching service → must check summaries-view impact)
- Bug w jednym dep → objawia się w summaries-view

### Active Development Risk

Z artifact-1-territory.md:
- **9 zmian** w summaries-view (absolutny hotspot)
- **32%** top-3 zmian w jednym pliku
- Q4 2025 feature sprint → God Page powstał pod time pressure

**God Page + highest churn = maximum risk zone**

---

## Contrast: Services Perfect Isolation

**Dlaczego services (zielone) mają testability score tylko 16?**

```
services.generation = 16 points
└─ XTDB dependency: 10 points
└─ 3 external libs:  6 points
└─ Ca=0 Ce=0 (zero internal coupling)
```

**Unit testing trivial:**
```clojure
(deftest create-generation-test
  (with-redefs [xt/submit-tx (fn [_ _] :mocked)]
    (let [[status result] (generation/create-generation ...)]
      (is (= :ok status)))))
```

**Tylko 1 mock** (XTDB) vs summaries-view 7-10 mocks.

---

## Recommended Test Strategy

### ❌ Nie Unit-testuj summaries-view

**Powód:** 112+ mock setups = bad ROI, fragile tests

### ✅ Integration Tests (Real XTDB + Real Services)

```clojure
(use-fixtures :each with-test-system)  ; Real XTDB in-memory

(deftest summaries-integration
  (with-xtdb-node [node (xt/start-node {})]
    (let [ctx {:biff/db (xt/db node) ...}]
      ;; Test całej flow: pages → services → XTDB
      (testing "summaries list loads"
        (let [response (summaries-view/summaries-list-page ctx)]
          (is (= 200 (:status response))))))))
```

**Coverage:**
- Real XTDB (in-memory) = tests actual queries
- Real services = tests contracts
- Mock tylko HTTP externes (openrouter)
- Fast (seconds per suite)

### ✅ E2E Tests dla Critical Flows

**Summaries flow (Playwright):**
1. CSV import → generation → list
2. Edit → save → verify
3. Accept/reject AI suggestions

**Target:** Core journey, high regression risk (9 zmian hotspot)

---

## Refactoring Path (P2 Priority)

### Cel: Reduce 12 deps → <8

**Idea 1: Extract CSV Import Logic**
```
BEFORE: summaries-view → csv-import service + csv-import UI
AFTER:  csv-import-handler (standalone) → summaries-view tylko view logic
```

**Idea 2: Composable Handlers**
```
BEFORE: 1 monolithic handler = 12 deps
AFTER:  fetch-data (unit testable)
      + render (mock testable)
      + thin handler (integration only)
```

**Benefit:** Każdy composable component ma <5 deps → testability score <10

---

## Related Artifacts

- **../artifact-2-structure.md** — pełna analiza struktury (§3 God Page)
- **testability-risks-analysis.md** — ranking testability scores
- **dependency-analysis-active-zones.md** — summaries-view dependency breakdown
- **../artifact-1-territory.md** — git history, 9 zmian w summaries-view

---

## Viewing the Graph

**Open:**
```bash
# Browser
firefox context/map/structure/god-page-dependencies.svg

# VS Code
code context/map/structure/god-page-dependencies.svg
```

**Or regenerate:**
```bash
dot -Tsvg context/map/structure/god-page-dependencies.dot -o context/map/structure/god-page-dependencies.svg
```

---

## Key Takeaway

**summaries-view** jest **testing nightmare** PRZEZ coupling (12 deps), NIE complexity:

- ❌ Unit testing = bad ROI (112+ mocks)
- ✅ Integration testing = sweet spot (real XTDB + services)
- ✅ E2E testing = critical flows only
- 🎯 Refactor target = reduce coupling (P2 priority)

**Contrast z services:** Perfect isolation (Ca=0 Ce=0) = trivial unit testing (1 XTDB mock).

---

**Graf wygenerowany:** 2026-06-10  
**Next step:** Implement test strategy (context/foundation/test-plan.md)
