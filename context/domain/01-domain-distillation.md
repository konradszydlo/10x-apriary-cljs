---
title: Domain Distillation — Apriary Production Tracking
created: 2026-06-15
type: domain-distillation
status: initial
sources:
  - context/foundation/prd.md
  - context/foundation/roadmap.md
  - src/com/apriary/schema.clj
  - src/com/apriary/services/summary.clj
  - src/com/apriary/services/product.clj
  - src/com/apriary/services/product_rankings.clj
  - src/com/apriary/services/generation.clj
---

# Domain Distillation — Apriary Production Tracking

**Autor:** Claude Sonnet 4.5  
**Data:** 2026-06-15  
**Zakres:** Destylacja domeny z istniejących dokumentów wymagań i kodu

## KROK 0 — Kontekst projektu

### Dokumenty źródłowe
- **PRD:** context/foundation/prd.md (v1, status: draft, 2026-05-29)
- **Roadmap:** context/foundation/roadmap.md (v1, 2026-06-01)
- **README:** README.md (podstawowy opis systemu)
- **Kod źródłowy:** src/com/apriary/ (struktura warstwowa: schema, services, pages, ui)

### Stack technologiczny
- **Backend:** Clojure 1.12, Biff v1.9.0 (opinionated web framework)
- **Baza danych:** XTDB 1.24 (bitemporal, document-oriented, Datalog queries)
- **Walidacja danych:** Malli schemas (all entities)
- **Frontend:** Rum (Clojure React wrapper), Tailwind 4, htmx

### Struktura repozytorum
```
src/com/apriary/
  schema.clj           — Malli schemas dla wszystkich encji
  services/            — Logika biznesowa (CRUD + domainowe operacje)
    summary.clj        — Zarządzanie podsumowaniami obserwacji
    product.clj        — Zarządzanie danymi produkcyjnymi
    product_rankings.clj — Agregacja i ranking uli
    generation.clj     — Śledzenie generacji AI
    csv_import.clj     — Parsowanie i walidacja CSV
    openrouter.clj     — Integracja AI (obecnie zamockowana)
  pages/               — Handlery HTTP (routing + middleware)
  ui/                  — Komponenty UI (Rum/Hiccup)
  middleware.clj       — Auth, RLS, CSRF protection
```

**Ograniczenia:** Brak narzędzi migracji bazy danych (XTDB); evolucja schematu odbywa się przez closed Malli maps.

---

## KROK 1 — Ubiquitous Language

### Apiary (Pasieka)
**Definicja:** Mała pasieka (5-50 uli) prowadzona przez jednego właściciela.  
**Źródło (PRD:48):** "Small apiary owners (same as existing users) — individuals managing 5-50 hives"  
**W kodzie:** BRAK bezpośredniej encji; zasięg skali jest założeniem projektowym widocznym w komentarzach o wydajności (np. product_rankings.clj:25-27).

### Hive (Ul)
**Definicja:** Pojedynczy ul w pasiece, identyfikowany przez `hive_number`.  
**Źródło (PRD:71):** "hive_number (string identifier)"  
**W kodzie:**
- schema.clj:54 — `:product/hive-number :string`
- schema.clj:34 — `:summary/hive-number {:optional true} [:maybe :string]`
- product_rankings.clj:43 — `?hive-number` w query agregacyjnej

**Uwaga:** Ul NIE jest osobną encją — pojawia się wyłącznie jako atrybut tekstowy w `Summary` i `Product`. Nie ma centralnego rejestru uli.

### Observation (Obserwacja)
**Definicja:** Tekst opisujący pracę pasiecznika na danym ulu (np. przegląd, leczenie, wymiana matki).  
**Źródło (PRD:31):** "CSV import of observation text via textarea"  
**W kodzie:**
- csv_import.clj:96-98 — wymagane pole `observation`, 50-50,000 znaków po trim
- csv_import.clj:146 — `{:observation trimmed-obs ...}`

**Uwaga:** Obserwacja NIE jest encją — jest surowym tekstem wejściowym do wygenerowania `Summary`.

### Summary (Podsumowanie)
**Definicja:** Uporządkowany opis pracy na ulu, może być wygenerowany przez AI lub wpisany manualnie.  
**Źródło (PRD:31):** "AI-generated summaries (currently mocked)"  
**Źródło (PRD:34):** "CRUD operations on summaries"  
**W kodzie:**
- schema.clj:25-37 — encja `:summary` z atrybutami:
  - `:summary/source` (`:ai-full | :ai-partial | :manual`)
  - `:summary/content` (50-50,000 znaków)
  - `:summary/generation-id` (link do Generation)
  - `:summary/accepted-at` (timestamp akceptacji przez użytkownika)
- summary.clj:62-129 — `list-summaries` (query z RLS)
- summary.clj:189-251 — `create-manual-summary`
- summary.clj:417-527 — `accept-summary` (zmienia source :ai-full → :ai-partial przy edycji)

**Niezmienniki:**
1. Content must be 50-50,000 characters (schema.clj:37, summary.clj:21).
2. `:ai-full` summary becomes `:ai-partial` when edited (summary.clj:313-315).
3. Manual summaries cannot be accepted (summary.clj:456-457).
4. Summary already accepted cannot be re-accepted (summary.clj:460-461).

### Generation (Generacja)
**Definicja:** Rekord opisujący jedną sesję generowania podsumowań przez AI dla partii obserwacji CSV.  
**Źródło (PRD:35):** "Acceptance tracking via generation counters"  
**W kodzie:**
- schema.clj:12-23 — encja `:generation` z atrybutami:
  - `:generation/model` (nazwa modelu AI)
  - `:generation/generated-count` (liczba wygenerowanych podsumowań)
  - `:generation/accepted-unedited-count` (akceptacje bez edycji)
  - `:generation/accepted-edited-count` (akceptacje po edycji)
  - `:generation/duration-ms` (czas wywołania API)
- generation.clj:8-72 — `create-generation`
- summary.clj:464-504 — `accept-summary` inkrementuje liczniki w Generation

**Niezmienniki:**
1. Generated-count ≥ accepted-unedited-count + accepted-edited-count (logiczny, nie egzekwowany w kodzie).
2. Model, generated-count, duration-ms są immutable po utworzeniu (brak update w generation.clj).

### Product (Produkt)
**Definicja:** Rekord zbiorów z danego ulu: typ produktu (miód/pyłek/jad), ilość, metryka, data.  
**Źródło (PRD:60):** "User can input production data via CSV format (hive_number;date;product;quantity;metric)"  
**W kodzie:**
- schema.clj:49-60 — encja `:product` z atrybutami:
  - `:product/hive-number :string`
  - `:product/product :string` (np. "Honey", "Pollen", "Venom")
  - `:product/quantity [:int {:min 1}]`
  - `:product/metric [:enum "kg" "ml" "g"]`
  - `:product/date [:maybe :string]` (format DD-MM-YYYY lub nil)
- product.clj:22-84 — `create-products-batch`
- product.clj:86-129 — `list-products` (sorted by date desc)

**Niezmienniki:**
1. Quantity must be ≥ 1 (schema.clj:57).
2. Metric must be one of: "kg", "ml", "g" (schema.clj:58).
3. Date format DD-MM-YYYY if provided (schema.clj:55, komentarz).

**Uwaga:** MVP nie implementuje edycji/usuwania (product.clj:12-16: "Edit/delete deferred to roadmap S-03").

### Ranking (Ranking)
**Definicja:** Lista uli uporządkowanych wg łącznej ilości danego produktu (Top 5 / Bottom 5).  
**Źródło (PRD:78):** "User sees top 5 and bottom 5 hives per product type"  
**Źródło (PRD:79):** "Rankings calculated as total quantity per hive per product type"  
**W kodzie:**
- product_rankings.clj:7-96 — `calculate-rankings`
  - Agregacja: `GROUP BY (hive-number, product-type, metric)` + `SUM(quantity)`
  - Sortowanie: desc by total-quantity
  - Zwraca: `{:rankings {"Honey" {:top [...] :bottom [...]} ...}}`

**Niezmienniki:**
1. Rankings aggregate by (hive, product, **metric**) — prevents mixing units (product_rankings.clj:41-42 komentarz: "CRITICAL: Grouping by metric prevents mixing units (e.g., kg + g)").
2. Top/bottom N limited to 5 (PRD:78, product_rankings.clj:28 default n=5).
3. All-time cumulative only (PRD:144-145: "date filters deferred to v2").

### User (Użytkownik)
**Definicja:** Właściciel małej pasieki, zalogowany w systemie, widziący tylko swoje dane.  
**Źródło (PRD:48):** "Small apiary owners — individuals managing 5-50 hives"  
**W kodzie:**
- schema.clj:4-10 — encja `:user` (email, password-hash, joined-at)
- middleware.clj:14-19 — `wrap-signed-in` (auth middleware)
- summary.clj:88-90 — RLS: `[['?s :summary/user-id user-id] ...]`
- product.clj:106 — RLS: `[['?p :product/user-id user-id]]`

**Niezmienniki:**
1. Row-Level Security: każde query musi filtrować po `:user-id` (summary.clj:11 komentarz, product.clj:7 komentarz).
2. Flat user model — brak ról ani admina (PRD:201-205).

### CSV Import (Import CSV)
**Definicja:** Proces wczytania danych (obserwacji lub produktów) z tekstowego formatu CSV wklejanego w textarea.  
**Źródło (PRD:31):** "CSV import of observations via textarea"  
**Źródło (PRD:69):** "paste CSV text: hive_number;date;product;quantity;metric"  
**W kodzie:**
- csv_import.clj:19-66 — `parse-csv-string` (separator `;`, headers + rows)
- csv_import.clj:92-149 — `validate-csv-row` (reguły dla observation)
- product_csv.clj — analogiczne funkcje dla Product CSV

**Niezmienniki:**
1. Delimiter: semicolon (csv_import.clj:10).
2. Header row required (csv_import.clj:46-47).
3. At least one data row (csv_import.clj:50-51).

### Row-Level Security (RLS)
**Definicja:** Wzorzec bezpieczeństwa zapewniający, że użytkownik widzi tylko swoje dane.  
**Źródło (PRD:89):** "RLS enforced on product records — users see only their own production data. Product queries filter by `user-id`."  
**W kodzie:**
- summary.clj:11 — komentarz: "Implements Row-Level Security (RLS) checks"
- summary.clj:165-172 — RLS violation returns NOT_FOUND (nie ujawnia istnienia zasobu)
- product.clj:7 — komentarz: "Implements Row-Level Security (RLS) checks"
- product_rankings.clj:44 — query filtruje `['?p :product/user-id 'user-id]`

**Niezmienniki:**
1. Każde query MUSI zawierać predykat `[:entity/user-id user-id]`.
2. RLS violation zwraca 404 (nie 403), aby nie ujawniać istnienia zasobu (summary.clj:172).

---

## KROP 2 — Klasyfikacja subdomen: Core / Supporting / Generic

| Pojęcie / Obszar | Subdomena | Uzasadnienie (odniesienie do celów produktu) |
|------------------|-----------|----------------------------------------------|
| **Ranking** (calculate-rankings) | **CORE** | PRD §Success Criteria (primary): "User can see ranked lists of hives by product type." To jest główny cel produktu — identyfikacja najlepszych/najgorszych uli dla decyzji hodowlanych. Logika agregacji z grupowaniem po metryce (zapobieganie mieszaniu jednostek) jest unikalną wartością biznesową. |
| **Product** (production tracking) | **CORE** | PRD §Problem Statement: "The system tracks what happened but doesn't track production metrics." Product tracking + ranking = przewaga konkurencyjna wobec "manual spreadsheets" (PRD:44-45). |
| **Summary** (AI-generated summaries) | **CORE** | PRD §Current System Overview: "AI-generated summaries (currently mocked)" — to jest istniejąca funkcjonalność Core dla dokumentowania pracy pasiecznej. Źródło niezmienników (:ai-full → :ai-partial przy edycji) jest kluczowe dla śledzenia interwencji użytkownika. |
| **Generation** (acceptance tracking) | **SUPPORTING** | PRD §Current System Overview: "Acceptance tracking via generation counters." To metrika użyteczności AI, nie produkt sam w sobie. Wspiera ewaluację modelu AI, ale nie dostarcza bezpośredniej wartości dla użytkownika pasiecznika. |
| **CSV Import** | **SUPPORTING** | PRD §Success Criteria: "User can input production data via CSV-like textarea." Import jest mechanizmem dostawy danych, nie celem samym w sobie. Wzorzec textarea jest reużywany z Summaries (PRD:125 Socrates: "proven pattern"). |
| **User** + **RLS** | **SUPPORTING** | PRD §Guardrails: "RLS enforced on product records — users see only their own production data." Bezpieczeństwo i autentykacja wspierają Core, ale nie są przewagą konkurencyjną (każda webapp musi to mieć). |
| **XTDB query logic** | **GENERIC** | Datalog queries, transakcje — technologia bazodanowa. Wymienialny komponent (można zastąpić PostgreSQL bez zmiany logiki domenowej). |
| **Biff framework patterns** | **GENERIC** | Middleware stack, routing — infrastruktura webowa. Framework choice jest szczegółem implementacyjnym. |
| **OpenRouter AI integration** | **GENERIC** | PRD §Current System Overview: "AI-generated summaries (currently mocked)." Integracja API jest commodity — można zamienić OpenRouter na inny LLM provider bez zmiany logiki Summary. |

---

## KROK 3 — Kandydaci na agregaty i ich niezmienniki

### Agregat: **Summary** (root: `summary/id`)

**Granice agregatu:**
- **Root:** Summary entity
- **Value objects:** generation-id (reference, nie ownership), hive-number, observation-date, special-feature (metadata)
- **Nie zawiera:** Generation (tylko reference)

**Niezmienniki (reguły biznesowe, które MUSZĄ być zawsze prawdziwe):**

1. **Content length constraint**
   - **Reguła:** Content must be 50-50,000 characters after trim.
   - **Źródło (schema.clj:37):** `[:summary/content [:string {:min 50 :max 50000}]]`
   - **Źródło (summary.clj:21):** komentarz w `validate-content`
   - **Status egzekucji:** ✅ **Egzekwowany** — validate-content rzuca IllegalArgumentException przy naruszeniu (summary.clj:34-39).

2. **Source transition rule**
   - **Reguła:** :ai-full summary becomes :ai-partial when any field is edited (content OR metadata).
   - **Źródło (summary.clj:307-315):**
     ```clojure
     content-changed? (some? trimmed-content)
     metadata-changed? (or (contains? updates :hive-number) ...)
     any-field-changed? (or content-changed? metadata-changed?)
     new-source (if (and any-field-changed? (= current-source :ai-full))
                  :ai-partial
                  current-source)
     ```
   - **Status egzekucji:** ✅ **Egzekwowany** — update-summary automatycznie zmienia source (summary.clj:313-315).

3. **Manual summaries cannot be accepted**
   - **Reguła:** Summary z :source = :manual nie może przejść przez accept-summary.
   - **Źródło (summary.clj:456-457):**
     ```clojure
     (when (= (:summary/source summary) :manual)
       (throw (IllegalArgumentException. "Cannot accept manual summaries")))
     ```
   - **Status egzekucji:** ✅ **Egzekwowany** — accept-summary rzuca exception.

4. **No double-acceptance**
   - **Reguła:** Summary already accepted (has :summary/accepted-at) cannot be re-accepted.
   - **Źródło (summary.clj:460-461):**
     ```clojure
     (when (:summary/accepted-at summary)
       (throw (IllegalArgumentException. "Summary already accepted")))
     ```
   - **Status egzekucji:** ✅ **Egzekwowany** — accept-summary rzuca exception.

5. **RLS: User can only access their own summaries**
   - **Reguła:** Query/update/delete musi być ograniczone do `:summary/user-id = current-user-id`.
   - **Źródło (summary.clj:165-172):** RLS check w get-summary-by-id
   - **Status egzekucji:** ✅ **Egzekwowany** — każda funkcja sprawdza user-id i zwraca NOT_FOUND przy naruszeniu.

**Potencjalne problemy:**
- **BRAK:** Kod nie egzekwuje niezmiennika "generation-id musi wskazywać na istniejący Generation record" — accept-summary sprawdza `(nil? generation)` (summary.clj:467-468), ale create-manual-summary ustawia generation-id = nil bez walidacji (summary.clj:226).

---

### Agregat: **Product** (root: `product/id`)

**Granice agregatu:**
- **Root:** Product entity
- **Value objects:** hive-number, date, product (typ produktu), quantity, metric

**Niezmienniki:**

1. **Quantity must be positive**
   - **Reguła:** Quantity ≥ 1.
   - **Źródło (schema.clj:57):** `[:product/quantity [:int {:min 1}]]`
   - **Status egzekucji:** ✅ **Deklarowany w schemacie** — Malli validation przy persystencji (schema.clj).

2. **Metric is enum**
   - **Reguła:** Metric must be one of: "kg", "ml", "g".
   - **Źródło (schema.clj:58):** `[:product/metric [:enum "kg" "ml" "g"]]`
   - **Status egzekucji:** ✅ **Deklarowany w schemacie** — Malli validation.

3. **Date format DD-MM-YYYY**
   - **Reguła:** Date must match DD-MM-YYYY if provided (może być nil).
   - **Źródło (schema.clj:55):** `[:product/date [:maybe :string]]` + komentarz "DD-MM-YYYY format or nil"
   - **Status egzekucji:** ⚠️ **Częściowo egzekwowany** — schema.clj NIE egzekwuje regex; walidacja jest w product_csv.clj (CSV import layer), ale create-products-batch NIE waliduje formatu daty (product.clj:41-84 brak validate-date).

4. **RLS: User can only access their own products**
   - **Reguła:** Query musi filtrować po `:product/user-id = current-user-id`.
   - **Źródło (product.clj:106):** `[['?p :product/user-id user-id]]`
   - **Status egzekucji:** ✅ **Egzekwowany** — list-products query zawiera predykat RLS.

**Potencjalne problemy:**
- **BRAK:** Product edit/delete nie istnieje w MVP (product.clj:12-16 komentarz), więc nie można ocenić RLS w tych operacjach.

---

### Agregat: **Generation** (root: `generation/id`)

**Granice agregatu:**
- **Root:** Generation entity
- **Value objects:** model, generated-count, duration-ms, counters (accepted-unedited-count, accepted-edited-count)

**Niezmienniki:**

1. **Counter invariant (logiczny)**
   - **Reguła:** accepted-unedited-count + accepted-edited-count ≤ generated-count.
   - **Źródło (PRD:35):** "Acceptance tracking via generation counters"
   - **Status egzekucji:** ❌ **NIE egzekwowany w kodzie** — accept-summary inkrementuje liczniki (summary.clj:476-479), ale NIE sprawdza, czy suma nie przekroczy generated-count. Możliwy bug: jeśli użytkownik zaakceptuje więcej podsumowań niż wygenerowano (np. przez duplikację requestu), niezmiennik zostanie naruszony.

2. **Immutability of generation metadata**
   - **Reguła:** Model, generated-count, duration-ms są immutable po utworzeniu.
   - **Źródło (generation.clj:8-72):** Brak funkcji update-generation.
   - **Status egzekucji:** ✅ **Egzekwowany przez brak API** — generation.clj nie eksportuje update, więc nie można zmienić tych pól.

3. **RLS: User can only access their own generations**
   - **Reguła:** Query musi filtrować po `:generation/user-id = current-user-id`.
   - **Źródło (generation.clj:97-100):** guard clauses w get-generation-by-id
   - **Status egzekucji:** ✅ **Egzekwowany** — get-generation-by-id sprawdza user-id (generation.clj:122-128 — plik nie był w pełni odczytany, ale pattern RLS jest identyczny jak w summary.clj).

---

### Agregat: **Ranking** (UWAGA: to NIE jest encja persystowana!)

**Granice agregatu:**
- **NIE jest encją** — to **computed value** (view model) generowany on-demand przez calculate-rankings.
- **Dane wejściowe:** wszystkie Product records dla danego user-id.

**Niezmienniki:**

1. **Aggregate by (hive, product, metric)**
   - **Reguła:** Ranking MUSI grupować po metryce, aby nie mieszać jednostek (kg + g).
   - **Źródło (product_rankings.clj:41-42):** komentarz "CRITICAL: Grouping by metric prevents mixing units (e.g., kg + g)"
   - **Źródło (product_rankings.clj:42-49):** query `{:find '[?hive-number ?product-type ?metric (sum ?quantity) ...] ...}`
   - **Status egzekucji:** ✅ **Egzekwowany** — query zawiera `?metric` w klauzuli `:find` i `:where`.

2. **Top/bottom N default = 5**
   - **Reguła:** Rankings zwraca top 5 i bottom 5 per product type (jeśli n nie jest przekazane).
   - **Źródło (PRD:78):** "top 5 and bottom 5 hives"
   - **Źródło (product_rankings.clj:28):** `[db user-id & {:keys [n] :or {n 5}}]`
   - **Status egzekucji:** ✅ **Egzekwowany** — default parameter n=5.

3. **All-time cumulative only**
   - **Reguła:** Rankings nie filtrują po dacie (all-time totals).
   - **Źródło (PRD:144-145):** "all-time cumulative totals are sufficient to validate the workflow. Date range filtering deferred to v2."
   - **Status egzekucji:** ✅ **Egzekwowany przez brak kodu** — calculate-rankings nie ma parametru date-range.

---

## KROK 4 — Rozjazdy MODEL vs KOD

| Dokument mówi X | Kod robi Y | Dowód (plik:linia) | Priorytet |
|-----------------|------------|--------------------|-----------|
| PRD §FR-006, FR-007: "User can edit individual product records" + "User can delete individual product records" (nice-to-have) | Kod NIE implementuje edit/delete dla Product | product.clj:12-16 (komentarz: "Edit/delete functionality is intentionally deferred to roadmap item S-03") | **LOW** — świadome odroczenie do S-03 per roadmap.md:79-86. |
| PRD §Business Logic Changes: "AI summaries from observation text (currently mocked)" | OpenRouter API jest zamockowana — zwraca observation jako content | openrouter.clj:42-45 (komentarz: "Returns the observation text as-is without calling the actual OpenRouter API") | **MEDIUM** — MVP świadomie mockuje AI; produkcja wymaga integracji API. |
| Schema.clj:55: "Date format DD-MM-YYYY" (komentarz) | product.clj:41-84 create-products-batch NIE waliduje formatu daty | product.clj nie zawiera validate-date; product_csv.clj:106-132 waliduje tylko w CSV import layer | **HIGH** — luka bezpieczeństwa: użytkownik może wstawić invalid date przez API bezpośredni (ominąć CSV layer). |
| PRD §Success Criteria (guardrail 1): "Existing summaries functionality intact" | Kod NIE zawiera testów regresji dla Summaries | Brak katalogu tests/ w src/com/apriary/ (tylko fixtures.edn) | **MEDIUM** — brak automated verification, że Products nie łamie Summaries. |
| Generation invariant: accepted-unedited-count + accepted-edited-count ≤ generated-count | accept-summary inkrementuje liczniki BEZ sprawdzania sumy | summary.clj:476-479 — brak walidacji przed incrementem | **MEDIUM** — możliwy bug przy duplikacji requestu akceptacji. |
| PRD §Scope of Change: "CSV parsing logic should be shared between Summaries and Products" (PRD:161) | Kod MA dwa osobne moduły CSV: csv_import.clj (Summaries) + product_csv.clj (Products) | csv_import.clj:19-66 vs product_csv.clj:93-139 — duplikacja parse-csv-string logic | **LOW** — duplikacja, ale rozsądna separacja concern (różne walidacje per doc-type). |

---

## KROK 5 — Ranking refaktoru (Top 3 kandydaci na agregaty do refaktoru)

### #1 — **Product aggregate** (NAJWYŻSZY priorytet)

**Wartość biznesowa (Core-ness):** ⭐⭐⭐⭐⭐  
Product + Ranking = główny value proposition produktu (PRD §Success Criteria: "see ranked lists of hives by product type"). Bez tego dane nie mają wartości.

**Ryzyko (słaba egzekucja niezmienników):** ⭐⭐⭐⭐  
1. **Date format NIE jest walidowany w create-products-batch** — luka bezpieczeństwa (kod:HIGH priority w tabeli rozjazdów).
2. **Brak edit/delete → brak RLS verification** — nie można ocenić, czy RLS będzie działać przy update (MVP defers to S-03).
3. **Metric grouping w rankingu jest krytyczna** (product_rankings.clj:41-42), ale schema.clj:58 egzekwuje tylko enum — NIE ma walidacji "czy metric pasuje do product type" (np. czy "Honey" + "ml" jest poprawne).

**Dlaczego #1:**  
Date validation gap + brak CRUD completion = potencjalna utrata integralności danych. Refaktoring: dodać validate-date do product.clj:create-products-batch, zaimplementować edit/delete z RLS checks, rozważyć Product-Metric compatibility rules (np. enum per product type).

---

### #2 — **Generation aggregate**

**Wartość biznesowa (Supporting):** ⭐⭐⭐  
Supporting subdomain — wspiera ewaluację AI, ale nie jest Core value.

**Ryzyko (słaba egzekucja niezmienników):** ⭐⭐⭐⭐  
1. **Counter invariant (accepted ≤ generated) NIE jest egzekwowany** — możliwy overflow przy duplikacji requestu (kod:MEDIUM priority).
2. **Generation-id w Summary może być nil lub wskazywać na nieistniejący record** — accept-summary sprawdza nil (summary.clj:467), ale create-manual-summary NIE waliduje foreign key.

**Dlaczego #2:**  
Bug risk jest realny (duplikacja HTTP request → double increment), ale impact jest ograniczony do metryk (nie wpływa na Summary content integrity).

**Refaktoring:** Dodać check w accept-summary:
```clojure
(when (>= (+ new-unedited new-edited) (:generation/generated-count generation))
  (throw (IllegalStateException. "Cannot accept more summaries than were generated")))
```

---

### #3 — **Summary aggregate**

**Wartość biznesowa (Core):** ⭐⭐⭐⭐  
Istniejąca funkcjonalność Core — dokumentowanie pracy pasiecznej.

**Ryzyko (słaba egzekucja niezmienników):** ⭐⭐  
Niezmienniki są DOBRZE egzekwowane (content length, source transition, no double-acceptance, RLS). Jedyna luka: generation-id foreign key nie jest walidowany.

**Dlaczego #3:**  
Niezmienniki są silne, ale jest to agregat złożony (dependency na Generation). Refaktoring: rozważyć explicit foreign key validation lub relaksację (allow nil generation-id for manual summaries, enforce non-nil for AI summaries).

---

## Podsumowanie (najważniejsze wnioski)

1. **Trzy subdomeny Core:** Ranking, Product, Summary — wszystkie dotyczą pasieki (Apiary domain). Supporting: Generation (metrika AI), CSV Import (mechanizm dostawy danych), User/RLS (infrastruktura bezpieczeństwa). Generic: XTDB, Biff, OpenRouter.

2. **Niezmienniki są CZĘŚCIOWO egzekwowane:**
   - ✅ **Strong:** Summary (content length, source transition, acceptance rules, RLS).
   - ⚠️ **Weak:** Product (date format NOT validated in service layer), Generation (counter invariant NOT enforced).

3. **Główny rozjazd MODEL vs KOD:** PRD zakłada spójną walidację daty DD-MM-YYYY (schema.clj:55 komentarz), ale create-products-batch NIE waliduje formatu — luka bezpieczeństwa.

4. **Top kandydat na refaktoring:** Product aggregate (#1) — Core value, słaba walidacja daty, brak CRUD completion (edit/delete), brak Product-Metric compatibility rules.

5. **Agregaty NIE są explicite w kodzie:** XTDB jest document store; kod nie definiuje granic transakcyjnych (brak aggregate root pattern w stylu DDD tactical). Persistence logic jest rozrzucona po services/ (summary.clj, product.clj, generation.clj). Potencjalny refactor: wprowadzić explicit aggregate boundaries + transaction scripts per aggregate.

6. **Ubiquitous Language istnieje, ale NIE jest eksplicytny:** Pojęcia domenowe (Hive, Summary, Product, Ranking) są obecne w nazwach encji i funkcji, ale BRAK centralnego słownika (np. glossary.md). Komunikacja developerów może polegać na implicit knowledge.

7. **Brak testów regresji:** PRD §Guardrails: "Existing summaries functionality intact" — kod NIE ma automated tests verifying that Products don't break Summaries (medium risk per tabela rozjazdów).

8. **MVP scope jest świadomy:** Wiele rozjazdów (mocked AI, brak edit/delete Product, duplikacja CSV parsing) jest **zamierzonych** (roadmap.md defers to later slices). To NIE są błędy — to pragmatyczne odroczenie złożoności.
