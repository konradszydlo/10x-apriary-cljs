# Analiza Zależności w Aktywnych Obszarach

**Data:** 2026-06-09  
**Kontekst:** Analiza zależności między namespace'ami z perspektywy mapy terytorium (artifact-1-territory.md)  
**Narzędzie:** clj-kondo analysis API

---

## Kluczowe Obserwacje

### 1. ✅ Brak Cykli Zależności (Excellent Architecture)

**Wniosek:** Projekt ma czystą, acykliczną architekturę. Żaden namespace nie tworzy circular dependency.

**Implikacja:** To jest **bardzo dobra wiadomość** dla legacy codebase. Brak cykli oznacza:
- Łatwiejsze reasoning o kodzie (dependencies flow w jednym kierunku)
- Możliwość izolowanego testowania modułów
- Mniejsze ryzyko cascade failures przy zmianach

### 2. 🔥 Summaries-View: Największy Fan-Out (12 zależności)

**Dowód:** `com.apriary.pages.summaries-view` zależy od **12 innych namespace'ów** — największy fan-out w projekcie.

**Związek z territory.md:** To jest **absolutny hotspot** projektu (9 zmian w ostatnich 12 miesiącach, najwięcej ze wszystkich plików).

**Implikacja:** Każda zmiana w którymkolwiek z 12 zależnych namespace'ów może potencjalnie wpłynąć na summaries-view. Wysoki blast radius.

### 3. 🎯 Middleware: Najwyższe Afferent Coupling (7 dependentów)

**Dowód:** `com.apriary.middleware` jest używany przez **7 innych namespace'ów** (wszystkie pages/*.clj).

**Implikacja:** Zmiana w middleware propaguje się na całą warstwę prezentacji. To jest **shared foundation** dla wszystkich pages.

### 4. 📦 Services Layer: Izolowany i Stabilny

**Dowód:** Wszystkie `services/*` namespace'y mają **zero internal dependencies** (Ca=0, Ce=0 dla internal deps).

**Związek z territory.md:** Services miały niższą aktywność (15 zmian) niż pages (28) i ui (26), ale najlepszą test discipline (63% coverage w commitach).

**Implikacja:** Services są **najbezpieczniejsze do modyfikacji** — brak internal coupling oznacza że changes są izolowane.

### 5. 🔄 UI-Pages Coupling: 100% Overlap

**Dowód:** Każdy UI component jest używany **wyłącznie** przez pages. Zero standalone components.

**Związek z territory.md:** 100% UI changes dotyka pages (12/12 commitów).

**Implikacja:** UI components są **page-specific**, nie reusable library. Duplikacja jest prawdopodobna między pages jeśli potrzebują podobnych UI.

---

## Analiza Według Obszarów Aktywności

| Obszar | Zależności (Ce) | Dependenci (Ca) | Status Aktywności | Ryzyko Zmiany |
|--------|-----------------|-----------------|-------------------|---------------|
| **🔥 HOT: summaries-view.clj** | 12 | 0 | Absolutny hotspot (9 zmian) | **HIGH** — fan-out 12 deps |
| **🔥 HOT: apriary.clj** | 10 | 0 | Core routing (8 zmian) | **HIGH** — routing hub |
| **🟡 WARM: ui/header.clj** | 1 (biffweb) | 4 | UI component (6 zmian) | LOW — prosty component |
| **🟡 WARM: ui/summaries-list.clj** | 1 (summary-card) | 1 | UI list (5 zmian) | LOW — single dep |
| **🟡 WARM: schema.clj** | 0 | 1 | Data definitions (5 zmian) | LOW — izolowany |
| **🟡 WARM: pages/app.clj** | 1 (middleware) | 1 | Entry page (5 zmian) | LOW — prosty routing |
| **🟡 WARM: ui/summary-card.clj** | 1 (string utils) | 2 | Card component (4 zmian) | LOW — single dep |
| **🟡 WARM: ui.clj** | 6 | 1 | UI helpers (4 zmian) | MEDIUM — cross-cutting |
| **🟡 WARM: pages/products.clj** | 8 | 0 | Products page (4 zmian) | MEDIUM — fan-out 8 |
| **🟡 WARM: email.clj** | 2 (logging, biffweb) | 1 | Email service (4 zmian) | LOW — izolowany |
| **SHARED: middleware** | 4 (external) | **7** | Foundation | **CRITICAL** — Ca=7 |
| **SHARED: util.clj** | 1 (string) | **5** | Helpers | **HIGH** — Ca=5 |

---

## Szczegółowa Analiza Problematycznych Wzorców

### Wzorzec 1: Summaries-View — God Page

**Namespace:** `com.apriary.pages.summaries-view`

**Zależności (12):**
1. `com.apriary.middleware` (routing foundation)
2. `com.apriary.ui.layout` (page layout)
3. `com.apriary.ui.helpers` (UI utilities)
4. `com.apriary.ui.csv-import` (CSV UI component)
5. `com.apriary.ui.summary-card` (card component)
6. `com.apriary.ui.summaries-list` (list component)
7. `com.apriary.services.summary` (business logic)
8. `com.apriary.services.csv-import` (CSV service)
9. `com.apriary.services.openrouter` (AI service)
10. `com.apriary.services.generation` (generation service)
11. `com.apriary.dto.summary` (data transfer object)
12. `com.apriary.util` (shared utilities)

**+ 7 external dependencies** (cheshire, clojure.string, malli, rum, xtdb, etc.)

**Dlaczego to ważne przy zmianie:**

- **Fan-out of 12** oznacza że summaries-view **integruje** wiele różnych concern'ów (UI, services, DTOs, utils)
- Każda zmiana w którymkolwiek z tych 12 namespace'ów może wymagać update'u summaries-view
- Jest to **integration point** całego summaries domain — jeśli coś się psuje, często objawia się tutaj
- **Legacy status** (9 zmian w Q4 2025, 0 w Q2 2026) oznacza że kod jest stabilny ALE może mieć technical debt z okresu "ship features fast"

**Związek z artifact-1-territory.md:**

> "summaries_view.clj (9 zmian) — absolutny hotspot projektu"  
> "32% wszystkich zmian w kodzie źródłowym dotyczy tylko 3 plików (summaries_view, apriary.clj, header)"  
> "Summaries domain dominuje: pages/summaries_view + ui/summaries_list + ui/summary_card = koncentracja biznesowej logiki"

**Co sprawdzić dalej:**

1. **Czy summaries-view ma integration tests?** (artifact-1 sugeruje że tests były dodane post-facto w Q2)
2. **Czy można rozdzielić concerns?** (np. CSV import logic do osobnego page handler?)
3. **Czy wszystkie 12 deps są naprawdę potrzebne?** (może część można wydzielić do sub-components?)

---

### Wzorzec 2: Middleware — Shared Foundation (Ca=7)

**Namespace:** `com.apriary.middleware`

**Używany przez (7):**
1. `com.apriary.pages.summaries-view`
2. `com.apriary.pages.rankings`
3. `com.apriary.pages.app`
4. `com.apriary.pages.products`
5. `com.apriary.pages.home`
6. `com.apriary.pages.csv-import`
7. `com.apriary` (core routing)

**Zależności (4 external):**
- `com.biffweb` (framework)
- `muuntaja.middleware` (content negotiation)
- `ring.middleware.anti-forgery` (CSRF)
- `ring.middleware.defaults` (ring defaults)

**Dlaczego to ważne przy zmianie:**

- **Ca=7** oznacza że **każdy page** zależy od middleware
- Zmiana w middleware (np. dodanie nowego middleware, zmiana kolejności, zmiana konfiguracji) wpływa na **całą warstwę prezentacji**
- To jest **single point of failure** — bug w middleware może zepsuć wszystkie pages naraz
- **Zero internal dependencies** (tylko external) = dobra separacja, ALE wysoki blast radius przy zmianie

**Związek z artifact-1-territory.md:**

> "middleware.clj — Ca=7 | Ce=0 | I=0.00 | STABLE"  
> "Zmiany w services mają mniejsze ryzyko breaking changes niż w UI"  
> "Services layer stabilny — Niższa aktywność niż pages/ui"

Middleware jest w **cold zone** (niska aktywność) ale **high coupling** (Ca=7) — paradoks:
- Rzadko modyfikowany (stabilny)
- Ale jeśli się modyfikuje → szeroki wpływ

**Co sprawdzić dalej:**

1. **Czy middleware ma integration tests?** (zmiana w middleware powinna być testowana na poziomie całej request pipeline)
2. **Czy można wydzielić page-specific middleware?** (zamiast shared foundation, może niektóre pages potrzebują custom middleware?)
3. **Jaka jest pokrywanie testami middleware?** (Ca=7 sugeruje że testy są krytyczne)

---

### Wzorzec 3: Util.clj — Cross-Cutting Helpers (Ca=5)

**Namespace:** `com.apriary.util`

**Używany przez (5):**
1. `com.apriary.dto.generation`
2. `com.apriary.dto.summary`
3. `com.apriary.pages.summaries-view`
4. `com.apriary.pages.generations`
5. `com.apriary.pages.summaries`

**Zależności (1):**
- `clojure.string`

**Dlaczego to ważne przy zmianie:**

- **Ca=5** oznacza że util jest używany przez **multiple domains** (DTOs, pages)
- Zmiana w util może wpłynąć na różne obszary projektu jednocześnie
- **Shared helpers** to dobra praktyka ALE ryzyko: jeśli helper zmienia behavior, ripple effect przez 5 namespace'ów
- **Cold zone activity** (3 zmiany w 12 miesięcy) sugeruje że util jest **stabilny** — rzadko potrzebuje zmian

**Związek z artifact-1-territory.md:**

> "util.clj — 3 zmiany (shared helpers)"  
> "util zmienia się gdy potrzebne są shared helpers"  
> "util.clj łączy obszary przez shared helpers (3 commits)"

Util jest **secondary hub** — nie tak krytyczny jak middleware (Ca=7) ale wciąż cross-cutting.

**Co sprawdzić dalej:**

1. **Czy util ma unit tests dla każdej helper function?** (Ca=5 + cross-domain usage = krytyczne testowanie)
2. **Czy można podzielić util na domain-specific helpers?** (np. `util.generation`, `util.summary` zamiast jednego `util`)
3. **Jakie konkretnie funkcje są w util?** (formatowanie, parsowanie, walidacja? Może część można przenieść do domain-specific namespaces?)

---

### Wzorzec 4: UI Layer — 100% Coupling z Pages

**Namespace'y UI:**
- `ui.layout` → używany przez: `summaries-view`, `rankings`, `products`, `csv-import`
- `ui.summary-card` → używany przez: `summaries-view`, `summaries-list`
- `ui.summaries-list` → używany przez: `summaries-view`
- `ui.header` → używany przez: `layout`, `ui` (base)
- `ui.helpers` → używany przez: `summaries-view`, `csv-import`

**Dowód:**
```
100% UI changes dotyka pages (12/12 commitów z territory.md)
63% pages changes dotyka UI (12/19)
```

**Dlaczego to ważne przy zmianie:**

- **Zero reusability** — każdy UI component jest tworzony dla konkretnego page
- **Duplication risk** — jeśli dwa pages potrzebują podobnego UI, mogą mieć duplicate components zamiast shared
- **Tight coupling** — zmiana w UI component **zawsze** wymaga sprawdzenia pages context (100% overlap)
- **Testing gap** — tylko 17% UI changes ma testy w tym samym commicie (territory.md)

**Związek z artifact-1-territory.md:**

> "UI Components = Coupled Tightly to Pages"  
> "100% UI changes dotyka pages (12/12)"  
> "0% UI changes są standalone"  
> "ui/ to nie reusable component library — to page-specific components"

**Co sprawdzić dalej:**

1. **Czy są duplicate UI patterns między pages?** (np. czy products i summaries mają podobne list/card components?)
2. **Czy można wydzielić shared UI components?** (np. generic card, generic list, generic form?)
3. **Jaka jest pokrycie testami UI components?** (17% w-commit coverage sugeruje luki)

---

### Wzorzec 5: Services Layer — Izolacja (Ca=0, Ce=0 internal)

**Wszystkie services namespace'y:**
- `services.product` (0 internal deps)
- `services.product-csv` (0 internal deps)
- `services.product-rankings` (0 internal deps)
- `services.openrouter` (0 internal deps)
- `services.generation` (0 internal deps)
- `services.summary` (0 internal deps)
- `services.csv-import` (0 internal deps)

**Dowód:**
```
Żaden service nie zależy od innego service (Ce=0)
Żaden service nie jest używany przez inny service (Ca=0)
Services są używane TYLKO przez pages
```

**Dlaczego to ważne przy zmianie:**

- **Perfect isolation** — każdy service jest niezależny
- **Łatwo testować** — brak internal dependencies = łatwo mockować external deps (xtdb, logging)
- **Bezpieczny refactoring** — zmiana w jednym service nie wpływa na inne services
- **Service-to-service brak komunikacji** — może oznaczać że business logic jest **duplikowana** między services zamiast shared

**Związek z artifact-1-territory.md:**

> "Services layer = safest to modify"  
> "Tylko 32% pages changes dotyka services — relatywnie izolowane"  
> "Services layer ma najlepszą test discipline (63% coverage w commitach)"

**Co sprawdzić dalej:**

1. **Czy jest duplikacja logiki między services?** (np. czy `summary` i `generation` mają podobną AI logic?)
2. **Czy brak service-to-service deps jest zamierzony?** (czy to dobra izolacja czy symptom że services są zbyt małe/zbyt duże?)
3. **Jaka jest rzeczywista coverage testów services?** (63% w-commit to dobry znak, ale jaka jest runtime coverage?)

---

## Wzorce Zależności — Podsumowanie

| Wzorzec | Namespace | Typ Problemu | Severity | Związek z Territory |
|---------|-----------|--------------|----------|---------------------|
| **God Page** | `summaries-view` | Fan-out 12 deps | 🔥 CRITICAL | Absolutny hotspot (9 zmian) |
| **Shared Foundation** | `middleware` | Ca=7 (all pages depend) | 🔥 CRITICAL | Stable but high blast radius |
| **Cross-Cutting Helpers** | `util` | Ca=5 (multi-domain) | 🟡 MEDIUM | Secondary hub (3 zmian) |
| **UI-Pages Tight Coupling** | All `ui/*` | 100% overlap pages | 🟡 MEDIUM | 63% pages→ui coupling |
| **Services Isolation** | All `services/*` | Ca=0 Ce=0 internal | ✅ GOOD | Best test discipline |

---

## Dlaczego To Ważne przy Zmianie Legacy Code?

### 1. God Page (summaries-view) = Integration Hell

**Prosty język:**

Wyobraź sobie że chcesz zmienić jak działa CSV import. `summaries-view` używa:
- `services.csv-import` (business logic)
- `ui.csv-import` (UI component)
- `services.generation` (AI generation po imporcie)
- `services.openrouter` (AI provider)
- `dto.summary` (data format)

Zmiana w **którymkolwiek** z tych może wymagać update'u summaries-view. A summaries-view jest **najczęściej modyfikowany plik** w projekcie — każda zmiana tutaj to ryzyko regresu.

**W legacy:**
- Trudno zmienić jeden concern bez touching others
- Testing jest trudny bo trzeba mockować 12 dependencies
- Bug w jednym z 12 deps może się objawić w summaries-view

**Recommendation:**
- Przed touching summaries-view: check które z 12 deps są rzeczywiście potrzebne
- Rozważ wydzielenie CSV import logic do osobnego page/handler
- Dodaj integration tests jeśli ich nie ma

---

### 2. Middleware (Ca=7) = Blast Radius

**Prosty język:**

Każdy page w projekcie zależy od middleware. Jeśli zmienisz middleware (np. dodasz nowy CSRF check, zmienisz content negotiation), **wszystkie pages** mogą być dotknięte.

**W legacy:**
- Zmiana w middleware może zepsuć wszystkie pages naraz
- Trudno przetestować wpływ na każdy page (7 różnych pages)
- Jeśli middleware ma bug → całość aplikacji może nie działać

**Recommendation:**
- Middleware changes wymagają smoke tests na WSZYSTKICH pages
- Rozważ page-specific middleware jeśli różne pages potrzebują różnych middleware stacks
- Priorytet dla integration tests middleware → pages flow

---

### 3. Util (Ca=5) = Ripple Effect

**Prosty język:**

Util jest używany przez 5 różnych namespace'ów w różnych domenach (DTOs, pages). Jeśli zmienisz behavior helper function w util, może to wpłynąć na summaries, generations, i DTOs jednocześnie.

**W legacy:**
- Shared helpers są wygodne ALE ryzykowne — jedna zmiana → wiele side effects
- Jeśli util nie ma testów → nie wiesz co się zepsuje po zmianie
- Different domains używają util → trudno sprawdzić wszystkie use cases

**Recommendation:**
- Util MUSI mieć unit tests dla każdej funkcji (Ca=5 = critical)
- Rozważ namespace splitting: `util.generation`, `util.summary` zamiast jednego `util`
- Before changing util function: grep all usages i sprawdź context

---

### 4. UI-Pages 100% Coupling = No Reusability

**Prosty język:**

Każdy UI component jest tworzony dla konkretnego page. Jeśli products page i summaries page potrzebują podobnej "card" UI, prawdopodobnie mają **duplicate code** zamiast shared component.

**W legacy:**
- Duplikacja UI code = trudniej utrzymać consistency
- Zmiana w jednym UI component nie propaguje się do innych (bo są separate)
- 100% coupling oznacza że nie możesz zmienić UI bez checking pages context

**Recommendation:**
- Audit for duplicate UI patterns (card, list, form components)
- Rozważ wydzielenie truly reusable components (generic card, generic list)
- UI tests są słabe (17% w-commit) — priorytet dla retroactive testing

---

### 5. Services Isolation = Safe Zone

**Prosty język:**

Services nie zależą od siebie nawzajem. Możesz zmienić `summary service` bez wpływu na `generation service`. To jest **dobra separacja**.

**W legacy:**
- Safest area to modify — brak internal coupling
- Łatwo testować — tylko external deps (db, logging)
- ALE: sprawdź czy nie ma duplikacji logiki między services

**Recommendation:**
- Services to najlepszy punkt startowy dla zmian w legacy
- Check for logic duplication between services
- Maintain high test coverage (63% w-commit to dobry baseline)

---

## Co Sprawdzić Dalej?

### 1. Integration Tests dla Hot Zones

| Obszar | Priorytet | Dlaczego |
|--------|-----------|----------|
| `summaries-view` | 🔥 CRITICAL | Fan-out 12, absolutny hotspot, integration point |
| `middleware` | 🔥 CRITICAL | Ca=7, single point of failure, all pages depend |
| `util` | 🟡 MEDIUM | Ca=5, cross-domain usage, ripple effect risk |

**Pytanie:** Czy te 3 namespace'y mają integration/smoke tests?

---

### 2. Duplikacja UI Patterns

**Pytanie:** Czy `summaries-view` i `products page` mają duplicate UI code?

**Gdzie szukać:**
- Card components (`ui.summary-card` vs `ui.products`)
- List components (`ui.summaries-list` vs ?)
- Form components (CSV import form vs products form)

---

### 3. Service-to-Service Logic Sharing

**Pytanie:** Czy `services.summary` i `services.generation` mają shared AI logic?

**Gdzie szukać:**
- Czy oba wywołują `openrouter`?
- Czy mają duplicate prompt formatting?
- Czy mają duplicate error handling?

---

### 4. Test Coverage Reality Check

**Territory.md sugeruje:**
- Services: 63% w-commit coverage (dobry)
- UI: 17% w-commit coverage (słaby)
- Pages: 16% w-commit coverage (słaby)

**Pytanie:** Jaka jest **runtime coverage** (nie tylko w-commit)?

**Gdzie szukać:**
- `clojure -M:coverage` output
- Coverage report dla hot zones (summaries-view, middleware, util)

---

## Executive Summary

### ✅ Dobre Wiadomości

1. **Brak cykli zależności** — czysta, acykliczna architektura
2. **Services layer dobrze izolowany** — Ca=0 Ce=0 internal, łatwo modyfikować
3. **Middleware zero internal deps** — tylko external (framework), dobra separacja
4. **Projekt ma clear layering** — pages → ui → services → db

### 🟡 Obszary Ryzyka

1. **Summaries-view God Page** — fan-out 12 deps, integration hell
2. **Middleware blast radius** — Ca=7, single point of failure
3. **UI-Pages tight coupling** — 100% overlap, zero reusability
4. **Util cross-domain usage** — Ca=5, ripple effect risk

### 🔴 Krytyczne Pytania

1. Czy summaries-view ma integration tests? (12 deps = testing nightmare bez integration tests)
2. Czy middleware ma smoke tests dla wszystkich pages? (Ca=7 = must test all dependents)
3. Czy util ma unit tests? (Ca=5 = ripple effect jeśli brak testów)
4. Czy są duplicate UI patterns? (100% coupling sugeruje duplication risk)

### 🎯 Rekomendacje dla Next Steps

1. **Uruchom coverage report** (`clojure -M:coverage`) dla reality check
2. **Audit summaries-view dependencies** — czy wszystkie 12 są naprawdę potrzebne?
3. **Check for UI duplication** — compare `ui.summary-card` vs `ui.products` patterns
4. **Priority testing:** middleware (Ca=7) > summaries-view (fan-out 12) > util (Ca=5)

---

**Status:** ✅ Analiza zakończona  
**Next artifact:** Test coverage report + UI duplication audit  
**Blocking questions:** Integration test status dla hot zones
