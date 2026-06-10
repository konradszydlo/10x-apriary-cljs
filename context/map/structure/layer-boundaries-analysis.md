# Analiza Granic Warstw — Frontend vs Schema API

**Data:** 2026-06-09  
**Kontekst:** Czy frontend respektuje `schema.api` jako fundament walidacji?  
**Focus:** Aktywne obszary z territory.md — `summaries-view`, `rankings`, `products`

---

## 🚨 Najważniejsze Obserwacje

### 1. **schema.api jest MARTWY KOD — zero importów w całej aplikacji**

`com.apriary.schema.api` to 132 linie Malli schemas dla API request/response validation, ale **żaden plik w projekcie go nie importuje**.

**Implikacja:** Schema.api był zaprojektowany jako fundament walidacji API, ale nigdy nie został zintegrowany. Frontend definiuje własne schemas inline zamiast używać centralne.

---

### 2. **summaries-view łamie DRY — duplikuje schema.api**

**Dowód:**
- `schema.api` definiuje `create-manual-summary-schema` (linie 26-37)
- `pages/summaries_view.clj` definiuje **identyczny** `create-manual-summary-schema` (linia 334)

Oba są **byte-for-byte identyczne** — ta sama walidacja (content 50-50k chars, date regex `DD-MM-YYYY`, etc.)

**Implikacja:** Zmiana validation rules wymaga update'u w **dwóch miejscach**. Duplication = ryzyko desynchronizacji.

---

### 3. **rankings i products NIE używają żadnej walidacji Malli**

**Dowód:**
- `pages/rankings.clj` — zero importów Malli, zero schema definitions
- `pages/products.clj` — zero importów Malli, zero schema definitions

**Implikacja:** Products domain (Q2 2026 frontier) i rankings nie mają **żadnej** walidacji po stronie frontendu. Wszystko polega na services layer.

---

### 4. **Tylko summaries-view używa Malli — ale inline, nie przez schema.api**

**Dowód:**
```clojure
;; summaries_view.clj linia 24-25
[malli.core :as m]
[malli.error :as me]

;; summaries_view.clj linia 334-347
(def create-manual-summary-schema ...)  ; Duplicate of schema.api
```

**Implikacja:** Summaries domain (Q4 2025 legacy, 9 zmian) to **jedyny obszar** z frontend validation. Rest polega na backend.

---

### 5. **schema.api i schema.clj żyją w równoległych uniwersach**

**Dowód:**
- `schema.clj` — XTDB entity schemas (`:user`, `:summary`, `:product`), zaimportowany przez `apriary.clj` (core)
- `schema.api` — API request/response schemas, **zero importów**

`schema.clj` jest używany (malli registry w core app), `schema.api` jest **orphan** (0 dependents).

---

## Szczegółowa Analiza Według Territory Map

| Sprawdzana granica | Wynik | Dowód z dependency analysis | Dlaczego to ważne przy zmianie | Związek z artifact-1-territory.md | Co sprawdzić dalej |
|-------------------|-------|----------------------------|-------------------------------|----------------------------------|-------------------|
| **schema.api → summaries-view** | ❌ **ZŁAMANA** — summaries-view NIE importuje schema.api, definiuje własny duplicate schema | `pages/summaries_view.clj:334` definiuje `create-manual-summary-schema` identyczny do `schema.api:26`.<br><br>Zero importów `schema.api` w całym projekcie (grep result: "No schema.api imports"). | **Duplication risk:** Jeśli zmieniasz validation rules (np. content length 50-50k → 50-100k), musisz update'ować **2 miejsca**. Łatwo przegapić jedno → desynchronizacja.<br><br>**Legacy debt:** schema.api powstał jako planned foundation ale nigdy nie został adopted — summaries-view był rozwijany w Q4 2025 bez integracji z schema.api. | "Summaries-view hotspot (9 zmian)" / "Q4 2025 = feature-first (1:4.9 test ratio)" / "Ship features, add validation later"<br><br>Q4 był "move fast" mode — prawdopodobnie schema.api był created jako TODO dla przyszłej integracji, ale nigdy nie zrealizowany. | 1. **Czy schema.api jest aktualny?** (może jest outdated vs summaries_view)<br>2. **Dlaczego nie został zintegrowany?** (tech debt? time pressure?)<br>3. **Plan konsolidacji:** summaries_view powinien importować schema.api zamiast duplicate |
| **schema.api → rankings** | ❌ **NIE ISTNIEJE** — rankings nie ma żadnej frontend validation | `pages/rankings.clj` zero importów Malli.<br><br>Zero schema definitions w rankings.<br><br>Dependency analysis pokazuje: rankings → middleware, services.product-rankings, ui.layout, ui.rankings (zero validation layers). | **No frontend guard:** Rankings polega wyłącznie na backend validation. Jeśli backend service ma bug w validation → frontend przepuści invalid data.<br><br>**User experience:** Brak client-side validation = poor UX (errors tylko po submit, nie real-time feedback). | "Rankings: Q2 2026 products pivot" / "Rankings 1 zmiana (cold zone)"<br><br>Rankings to **nowy feature** (Q2 2026) ale ma bardzo niską aktywność (1 commit). Prawdopodobnie szybki MVP bez frontend validation. | 1. **Czy rankings potrzebuje frontend validation?** (może jest read-only view?)<br>2. **Check rankings.clj handlers** — czy są POST/PUT endpoints?<br>3. **User flow audit:** czy rankings ma forms/inputs? |
| **schema.api → products** | ❌ **NIE ISTNIEJE** — products nie ma żadnej frontend validation | `pages/products.clj` zero importów Malli.<br><br>Zero schema definitions w products.<br><br>Dependency analysis: products → middleware, ui.layout, ui.products, 3x services (zero validation). | **Q2 2026 frontier risk:** Products to **active development domain** (4 zmiany w Q2) ale **zero frontend validation**. Nowy kod bez validation guards = higher bug risk.<br><br>**Contrast z summaries:** Summaries (legacy Q4) ma Malli validation, products (frontier Q2) nie ma — regression w quality discipline? | "Products Q2 2026 hot zone (4 zmiany)" / "Products pivot (0 w Q4 → 4 w Q2)" / "Active development, testy alongside code"<br><br>Products to **frontier domain** — jeśli nie ma frontend validation, może to być signal że quality shortcuts były wzięte dla speed. | 1. **Products handlers inventory** — ile POST/PUT endpoints?<br>2. **Czy products ma forms?** (CSV import? manual input?)<br>3. **Priority dla validation:** products jest active (4 zmiany) = high priority dla dodania validation |
| **schema.clj (XTDB) → schema.api (API)** | ❌ **DISCONNECT** — schema.clj (entity) i schema.api (request/response) żyją w równoległych uniwersach, zero integracji | `schema.clj` — zdefiniowany `:summary`, `:product`, `:user` XTDB entities, importowany przez `apriary.clj:12`.<br><br>`schema.api` — zdefiniowany API request/response schemas, **zero importów** w całym projekcie.<br><br>Brak shared types między nimi (np. `summary-dto-schema` w schema.api vs `:summary` w schema.clj). | **Desynchronization risk:** Entity schema (db) i API schema (validation) mogą drift apart. Przykład: jeśli db `summary/content` zmienia się z 50-10k na 50-100k (schema.clj:37), ale API schema nie jest updated → API będzie reject valid data.<br><br>**No single source of truth:** Każda zmiana w data model wymaga manual sync między schema.clj i schema.api (jeśli ktoś by w ogóle używał schema.api). | "Schema.clj 5 zmian (warm zone)" / "Schema changes są isolated"<br><br>Schema.clj ma **średnią aktywność** (5 zmian), ale brak connection do schema.api oznacza że API validation nie ewoluuje razem z db schema. | 1. **Audit schema drift:** porównaj `:summary` (schema.clj) vs `summary-dto-schema` (schema.api)<br>2. **Are they in sync?** (content length, field names, optional fields)<br>3. **Integration strategy:** czy schema.api powinien derive from schema.clj? |
| **DTOs (dto.summary) → schema.api** | ❌ **NIE ISTNIEJE** — DTOs nie używają schema.api dla validation | `dto/summary.clj` dependency analysis: używa tylko `util` (Ca=2, Ce=1).<br><br>Zero importów Malli w dto.summary.<br><br>summaries-view używa `dto.summary` (line 19) ale NIE schema.api. | **DTO layer brak validation:** DTOs transformują dane między layers (services → pages) ale nie validują. Jeśli service zwraca malformed data → DTO przepuści to bez error.<br><br>**Missed opportunity:** schema.api definiuje `summary-dto-schema` (linie 71-86) ale dto.summary go nie używa — zaprojektowany fundament nie jest used. | "dto.summary używany przez 2 namespace (pages.summaries-view, pages.summaries)" / "DTOs medium instability (I=0.33-0.50)"<br><br>DTOs są **transformation layer** między services i pages, ale bez validation = "hope and pray" approach. | 1. **DTO code review:** co robi dto.summary? (pure transform or has logic?)<br>2. **Czy DTO potrzebuje validation?** (defensive programming vs trust services)<br>3. **Integration cost:** ile pracy to dodać schema.api validation do DTOs? |
| **UI Components (ui.\*) → Malli validation** | ✅ **BRAK (by design)** — UI components są pure presentation, nie mają validation logic | UI layer dependency analysis: `ui.summary-card` → tylko clojure.string.<br><br>`ui.summaries-list` → tylko ui.summary-card.<br><br>**Zero** Malli imports w całej warstwie `ui/*`. | **Separation of concerns:** UI components są **dumb** — rendering only, zero business logic, zero validation.<br><br>**Correct layering:** Validation belongs w pages (handlers) nie w UI components. To jest **good architecture**. | "UI layer 100% coupling z pages" / "UI = page-specific components, nie reusable library"<br><br>UI components są tightly coupled do pages, ale to coupling jest **presentational** nie **logical** — UI renders, pages validate. | ✅ **No action needed** — UI layer correctly separated from validation concerns |
| **Services → schema.api** | ❌ **NIE ISTNIEJE** — services nie używają schema.api (tylko XTDB schema) | Services dependency analysis: wszystkie services mają `Ce=0` (zero internal deps).<br><br>Services używają XTDB dla persistence ale nie importują ani `schema.clj` ani `schema.api`.<br><br>Services validate via service-internal logic, nie przez central schemas. | **Service-level duplication risk:** Każdy service może mieć własną validation logic zamiast shared schemas.<br><br>**API contract drift:** Jeśli schema.api reprezentuje API contract, ale services go nie używają → services mogą return data które nie pasuje do API schema. | "Services layer perfect isolation (Ca=0 Ce=0)" / "Services = safest to modify" / "Best test discipline (63% w-commit)"<br><br>Services są **izolowane** co jest good dla testowania, ALE brak shared validation może być bad dla consistency. | 1. **Services validation audit:** jak services validują input? (inline? malli? custom?)<br>2. **Czy services używają schema.clj?** (XTDB entity schemas)<br>3. **Integration path:** czy services powinny validate against schema.api before return? |

---

## Wzorce Łamania Granic

### Pattern 1: **Orphan Foundation (schema.api)**

**Co się stało:**
1. Ktoś created `schema.api` jako planned foundation (132 linie dobrze zaprojektowanych Malli schemas)
2. Schema.api definiuje clean API contracts (request schemas, response DTOs, query params)
3. **ALE:** Żaden kod go nie używa — zero importów w całym projekcie

**Dlaczego to się stało (hypothesis z territory.md):**

Q4 2025 był **"feature-first" mode** (test/src ratio 1:4.9, "ship features fast"). Schema.api prawdopodobnie był:
- Created jako **planning artifact** ("we'll need this later")
- Ale nigdy zintegrowany bo **time pressure** (Q4 = 32 commits w 2 miesiące)
- Summaries-view był rozwijany **równolegle** z schema.api → powstały duplicates

**Evidence z git (hypothesis):**
- Schema.api: created early (foundation planning)
- Summaries-view: active development (9 zmian) → własne schemas inline dla szybkości
- Nigdy nie było czasu na refactor "use schema.api instead of inline schemas"

**Implikacja dla legacy:**
```
Planned architecture ≠ Actual architecture
```
Schema.api reprezentuje **intent** (jak powinno być), summaries_view reprezentuje **reality** (jak jest).

---

### Pattern 2: **Frontend Validation Regression (summaries → products)**

**Q4 2025 (summaries domain):**
- ✅ Summaries-view ma Malli validation (inline, nie schema.api, ale **istnieje**)
- ✅ `create-manual-summary-schema` validates content length, date format, etc.
- Frontend guards przed invalid input

**Q2 2026 (products domain):**
- ❌ Products page zero frontend validation
- ❌ Rankings zero frontend validation
- Backend-only validation

**Dlaczego regression:**

Territory.md pokazuje że Q2 był **"test-first pivot"** (test/src 1.4:1 vs Q4 1:4.9) — więcej testów, ale **backend tests**, nie frontend validation.

**Hypothesis:**
- Q2 focus był na **service layer tests** (63% w-commit coverage dla services)
- Frontend validation (Malli w pages) nie była priorytetem
- Products/rankings shipped bez frontend guards bo "backend validates anyway"

**Risk:**
- Poor UX (errors tylko post-submit)
- Frontend może przepuścić edge cases które backend nie obsługuje

---

### Pattern 3: **DTO Layer Brak Defensive Programming**

**Obecny flow:**
```
services → dto.summary → pages.summaries-view
         (no validation)  (Malli validation)
```

**Gdzie validation happens:**
- ✅ Pages: validate **user input** (forms)
- ❌ DTOs: **nie** validate service output
- ❓ Services: validate internally (nie przez shared schemas)

**Problem:**

Jeśli service zwraca malformed data (bug, db corruption, migration error), DTO przepuści to bez validate. Dopiero gdy data dotrze do pages → może crash rendering lub pass invalid data do UI.

**Gdzie powinno być:**
```
services → [VALIDATE] → dto.summary → pages
           ^schema.api
```

DTO powinien validate że data z services pasuje do `summary-dto-schema` (schema.api) **before** passing do pages.

**Why not now:**

Schema.api orphan = DTOs nie mają central schema do validate against. Każdy DTO musiałby define własny schema (duplication) albo trust services (current approach).

---

## Territory.md Context — Dlaczego Granice Się Rozmyły

### Q4 2025: Feature Sprint (summaries domain)

**Charakterystyka:**
- 32 commits, summaries-view hotspot (9 zmian)
- Test/src ratio **1:4.9** (ship features first, test later)
- Summaries domain built **fast** → inline schemas dla speed

**Validation approach:**
- Schema.api **planned** jako foundation
- Summaries-view **implemented** inline schemas (duplicate)
- Nigdy nie zintegrowane (time pressure? context loss?)

---

### Q1 2026: Hibernacja

**3-month gap** (zero commits styczeń-kwiecień) może oznaczać:
- Team composition change
- Context loss — nowa osoba w Q2 nie wiedziała o schema.api?
- Requirement changes — schema.api przestał być relevant?

---

### Q2 2026: Products Pivot + Test Hardening

**Charakterystyka:**
- 53 commits, products focus (4 zmiany), test/src **1.4:1** (reversed)
- **Test discipline improved** (services 63% w-commit coverage)
- **ALE:** frontend validation **regressed** (products/rankings zero Malli)

**Validation approach:**
- Focus na **backend tests** (services layer)
- Frontend validation **pominięta** (products/rankings ship bez guards)
- Schema.api wciąż orphan (zero integracji w Q2)

**Hypothesis:**

Q2 team prioritized **service-layer quality** (testy, coverage) ale **frontend quality** (validation, UX) była secondary. Products/rankings shipped jako MVPs bez frontend guards.

---

## Co To Oznacza dla Zmian

### Scenario 1: Zmiana Validation Rules dla Summaries

**Obecny stan:**
```
1. Update schema.api (orphan — nikt nie używa)
2. Update summaries_view inline schema (actual validation)
3. Update schema.clj (db entity, jeśli db constraints się zmieniają)
```

**Risk:**
- Łatwo przegapić jeden z kroków → desynchronizacja
- Schema.api może być **outdated** (nikt go nie maintenance)
- Brak single source of truth

**Recommendation:**
1. **Consolidate:** summaries_view powinien importować schema.api zamiast duplicate
2. **Test integration:** przed konsolidacją, sprawdź czy schema.api jest aktualny
3. **Future proof:** nowe features powinny używać schema.api (nie inline schemas)

---

### Scenario 2: Dodanie Frontend Validation dla Products

**Obecny stan:**
```
products page → zero frontend validation → backend service validates
```

**Risk:**
- Poor UX (errors post-submit, nie real-time)
- Products jest **active development** (4 zmiany Q2) → więcej features = więcej forms = więcej potrzeby validation

**Recommendation:**
1. **Import schema.api:** jeśli schema.api ma products schemas, użyj ich
2. **Jeśli brak:** define w schema.api (nie inline w products page)
3. **Follow summaries pattern:** summaries_view pokazuje jak integrate Malli (nawet jeśli duplicate, pattern jest OK)

---

### Scenario 3: Refactor Schema.api Integration

**Cel:** Schema.api jako **single source of truth** dla API validation

**Steps:**
1. **Audit schema.api vs reality:**
   - Porównaj `create-manual-summary-schema` (schema.api) vs summaries_view
   - Check czy są in sync (prawdopodobnie tak, bo duplicate)

2. **Integrate summaries-view:**
   - Replace inline schema z import z schema.api
   - Test że validation wciąż działa

3. **Extend do products/rankings:**
   - Define products/rankings schemas w schema.api
   - Integrate do pages

4. **DTO layer validation:**
   - DTOs powinny validate service output against schema.api DTOs
   - Defensive programming — catch service bugs before rendering

**Risk:**
- Large refactor (touches hot zones: summaries-view 9 zmian)
- Testing burden (każda zmiana w schema.api = re-test wszystkich pages)
- Może break existing behavior (jeśli schema.api drift from inline schemas)

---

## Executive Summary

### ❌ Łamanie Granic

| Granica | Status | Impact |
|---------|--------|--------|
| **schema.api → frontend** | ❌ BROKEN | Schema.api orphan (zero użycia), frontend definiuje własne schemas |
| **summaries-view → schema.api** | ❌ DUPLICATE | Summaries-view duplicates schema.api zamiast import |
| **products/rankings → validation** | ❌ MISSING | Zero frontend validation, backend-only |
| **schema.clj ↔ schema.api** | ❌ DISCONNECT | Entity schemas (db) i API schemas (validation) nie są synchronized |
| **DTOs → validation** | ❌ MISSING | DTOs nie validują service output |

### ✅ Poprawne Granice

| Granica | Status | Why Good |
|---------|--------|----------|
| **UI components → validation** | ✅ SEPARATED | UI są pure presentation, zero validation logic |
| **Services → isolation** | ✅ CLEAN | Services Ca=0 Ce=0 internal, nie coupled do validation layers |

### 🎯 Priorities dla Fix

| Priorytet | Action | Reason |
|-----------|--------|--------|
| **P0** | Consolidate summaries_view schema → import schema.api | Eliminate duplication w hotspot (9 zmian) |
| **P1** | Add frontend validation dla products | Active development (4 zmiany Q2), UX improvement |
| **P2** | Sync schema.clj ↔ schema.api | Prevent entity/API schema drift |
| **P3** | DTO layer defensive validation | Catch service bugs early |
| **P4** | Add frontend validation dla rankings | Low priority (1 zmiana, cold zone) |

### 📊 Metrics

- **Schema.api coverage:** 0% (0/8 pages używa)
- **Frontend validation coverage:** 12.5% (1/8 pages ma Malli — tylko summaries-view)
- **Duplication count:** 1 known (create-manual-summary-schema)
- **Orphan schemas:** 132 lines (całość schema.api)

---

## Next Steps

1. **Schema.api audit:**
   - Read schema.api line-by-line
   - Compare vs summaries_view inline schemas
   - Check for drift

2. **Products/rankings validation gap:**
   - Inventory wszystkich forms/endpoints w products/rankings
   - Design validation schemas (w schema.api)
   - Integrate Malli validation

3. **DTO defensive programming:**
   - Add validation layer w dto.summary
   - Use schema.api DTOs dla validate service output
   - Catch malformed data early

4. **Integration test:**
   - Przed każdym refactor: sprawdź czy existing validation działa
   - Po integracji schema.api: regression test summaries-view
   - Coverage dla nowej validation (products/rankings)

---

**Status:** ✅ Analiza zakończona  
**Wniosek:** Schema.api to **ghost architecture** — zaprojektowany jako fundament ale nigdy nie adopted. Frontend validation jest **fragmentary** (tylko summaries) i **duplicated** (inline schemas zamiast central).  
**Recommendation:** Consolidate summaries → schema.api (P0), extend do products/rankings (P1), sync schema.clj ↔ schema.api (P2).
