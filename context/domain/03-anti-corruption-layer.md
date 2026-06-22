---
title: Anti-Corruption Layer — XTDB Isolation
created: 2026-06-15
type: refactor-plan
leaking_dependency: XTDB 1.24
layers_affected: services, pages, dto
priority: high
effort: high
---

# Anti-Corruption Layer — XTDB Isolation

**Autor:** Claude Sonnet 4.5  
**Data:** 2026-06-15  
**Przeciekająca zależność:** XTDB 1.24 (bitemporal document database)

---

## KROK 0 — Kontekst

### Dokumenty źródłowe
- **CLAUDE.md:40-42** — "XTDB is a bitemporal, document-oriented database with Datalog query support"
- **deps.edn** — Biff framework (line 3) transitively pulls XTDB; no explicit XTDB version declared
- **PRD:164** — "Add new `:product` doc-type to XTDB schema alongside existing `:user`, `:summary`, and `:generation` doc-types"
- **context/domain/01-domain-distillation.md:206** — "XTDB query logic: GENERIC subdomain. Wymienialny komponent (można zastąpić PostgreSQL bez zmiany logiki domenowej)"

### Deklaracja wymienialności

**context/domain/01-domain-distillation.md:206:**
```markdown
| **XTDB query logic** | **GENERIC** | Datalog queries, transakcje — technologia bazodanowa. 
  Wymienialny komponent (można zastąpić PostgreSQL bez zmiany logiki domenowej). |
```

**Wniosek:** Dokumentacja EXPLICITE deklaruje, że XTDB jest wymienialną zależnością technologiczną (Generic subdomain), którą można zastąpić PostgreSQL. Kod tej wymienialności NIE dotrzymuje.

---

### Stack i warstwy
```
Warstwa Pages (HTTP handlers):
  pages/summaries_view.clj
  pages/summaries.clj
  pages/csv_import.clj
  pages/products.clj
      ↓ xtdb.api/*, xt/entity, xt/q, xt/submit-tx
      ↓
Warstwa Services (logika biznesowa):
  services/summary.clj
  services/product.clj
  services/product_rankings.clj
  services/generation.clj
      ↓ xtdb.api/*, xt/db, xt/entity, xt/submit-tx
      ↓
Warstwa DTO (transformacje):
  dto/summary.clj — konwersja XTDB entity → JSON
  dto/generation.clj — konwersja XTDB entity → JSON
      ↓ java.time.Instant, java.util.UUID (XTDB native types)
      ↓
XTDB 1.24 (external dependency)
```

### Zależności zewnętrzne (deps.edn)

**Główne:**
- **com.biffweb/biff** (line 3) — opinionated framework, transitively pulls XTDB
- **cheshire/cheshire** (line 4) — JSON serialization (używane w DTO layer)
- **org.clojure/data.csv** (line 5) — CSV parsing (używane w services)
- **org.mindrot/jbcrypt** (line 6) — password hashing (używane w auth)

**Overrides (security fixes):**
- org.eclipse.jetty.* (lines 9-14) — Jetty dependency overrides
- org.postgresql/postgresql (line 15) — PostgreSQL JDBC driver (unused in MVP, ale obecny)

**Kluczowa obserwacja:** XTDB NIE jest explicite deklarowane w deps.edn — pochodzi transitively z Biff. To utrudnia kontrolę wersji i swap.

---

## KROK 1 — IDENTYFIKACJA przeciekających zależności

### D-01: **XTDB API** (WYBRANO do ACL)

**Typ zależności:** Infrastruktura persystencji (document database)

**Gdzie przecieka (wszystkie wystąpienia):**

#### Services layer (30 wywołań API)

```clojure
;; File: src/com/apriary/services/summary.clj
;; Lines: 2, 103, 119, 156, 236, 285, 335, 387, 397, 445, 492-493

(ns com.apriary.services.summary
  (:require [xtdb.api :as xt] ...))              ;; Line 2

(let [all-results (xt/q db query-params)])       ;; Line 103
[:ok {:summaries (mapv (fn [[?s]] (xt/entity db ?s)) ...)}]  ;; Line 119
(let [entity (xt/entity db summary-id)])         ;; Line 156
(xt/submit-tx node [[:xtdb.api/put entity]])    ;; Line 236
(let [db (xt/db node)])                          ;; Line 285
(xt/submit-tx node [[:xtdb.api/put updated-entity]])  ;; Line 335
(let [db (xt/db node)                            ;; Line 387
      existing (xt/entity db summary-id)])
(xt/submit-tx node [[:xtdb.api/delete summary-id]])  ;; Line 397
(let [db (xt/db node)                            ;; Line 445
      summary (xt/entity db summary-id)])
(xt/submit-tx node [[:xtdb.api/put updated-generation]
                    [:xtdb.api/put accepted-summary]])  ;; Lines 492-493
```

```clojure
;; File: src/com/apriary/services/product.clj
;; Lines: 2, 67, 70, 110, 115

(ns com.apriary.services.product
  (:require [xtdb.api :as xt] ...))              ;; Line 2

(let [tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) entities)])  ;; Line 67
(xt/submit-tx node tx-ops)                       ;; Line 70
(let [query-params {:find '[?p] ...}             ;; Line 110
      results (xt/q db query-params)])
(products (->> results
               (mapv (fn [[?p]] (xt/entity db ?p))) ...))  ;; Line 115
```

```clojure
;; File: src/com/apriary/services/product_rankings.clj
;; Lines: 5, 52

(ns com.apriary.services.product-rankings
  (:require [xtdb.api :as xt] ...))              ;; Line 5

(let [agg-results (xt/q db query-params user-id)])  ;; Line 52
```

```clojure
;; File: src/com/apriary/services/generation.clj
;; Lines: 2, 56, 124, 186, 201, 242-243, 270, 326-327, 386-387, 395

(ns com.apriary.services.generation
  (:require [xtdb.api :as xt] ...))              ;; Line 2

(xt/submit-tx node [[:xtdb.api/put entity]])    ;; Line 56
(let [entity (xt/entity db generation-id)])     ;; Line 124
(let [all-results (xt/q db query-with-filter)]) ;; Line 186
[:ok {:generations (mapv (fn [[?g _ _ _ _ _]] (xt/entity db ?g)) ...)}]  ;; Line 201
(let [db (xt/db node)                            ;; Lines 242-243
      entity (xt/entity db generation-id)])
(xt/submit-tx node [[:xtdb.api/put updated-entity]])  ;; Line 270
(let [db (xt/db node)                            ;; Lines 326-327
      generation (xt/entity db generation-id)])
(summary-entities (mapv #(xt/entity db %) summary-ids)  ;; Line 387
 accepted-entities (mapv #(assoc % :summary/accepted-at now) summary-entities))
(let [tx-ops (into [[:xtdb.api/put updated-generation]]
                   (mapv (fn [entity] [:xtdb.api/put entity]) accepted-entities))]
  (xt/submit-tx node tx-ops))                    ;; Line 395
```

**Podsumowanie services:**
- summary.clj: 11 wywołań XTDB API
- product.clj: 5 wywołań
- product_rankings.clj: 2 wywołania
- generation.clj: 12 wywołań
**Razem: 30 wywołań API w warstwie Services**

---

#### Pages layer (21 wywołań API)

```clojure
;; File: src/com/apriary/pages/summaries_view.clj
;; Lines: 27, 200, 578, 1203-1204, 1210-1214

(ns com.apriary.pages.summaries-view
  (:require [xtdb.api :as xt] ...))              ;; Line 27

(summaries (mapv (fn [[?s]] (xt/entity fresh-db ?s)) summary-ids))  ;; Line 200
(tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) summary-entities)
 (xt/submit-tx node tx-ops))                     ;; Line 578
(_ (xt/sync node)                                ;; Line 1203
 fresh-db (xt/db node))                          ;; Line 1204
(let [summaries-query {:find '[?s] ...}          ;; Lines 1210-1214
      summary-ids (xt/q fresh-db summaries-query)
      summaries (mapv (fn [[?s]] (xt/entity fresh-db ?s)) summary-ids)])
```

```clojure
;; File: src/com/apriary/pages/summaries.clj
;; Lines: 10, 352

(ns com.apriary.pages.summaries
  (:require [xtdb.api :as xt] ...))              ;; Line 10

(let [tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) summary-entities)])  ;; Line 352
```

```clojure
;; File: src/com/apriary/pages/csv_import.clj
;; Lines: 16, 166

(ns com.apriary.pages.csv-import
  (:require [xtdb.api :as xt] ...))              ;; Line 16

(let [tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) summary-entities)])  ;; Line 166
```

```clojure
;; File: src/com/apriary/pages/products.clj
;; Lines: 11

(ns com.apriary.pages.products
  (:require [xtdb.api :as xt] ...))              ;; Line 11
```

**Podsumowanie pages:**
- summaries_view.clj: 10 wywołań XTDB API
- summaries.clj: 2 wywołania
- csv_import.clj: 2 wywołania
- products.clj: 1 require (nie używa API bezpośrednio, ale jest świadomy XTDB)
**Razem: ~21 wywołań API w warstwie Pages**

---

#### DTO layer (pośredni przeciek: XTDB native types)

```clojure
;; File: src/com/apriary/dto/summary.clj
;; Lines: 37-50

(defn entity->dto
  "Convert an XTDB summary entity to API response DTO format.
   ...
   5. Removing internal fields (xt/id)"          ;; Line 29 — komentarz świadomy XTDB
  [entity]
  {:id (str (:summary/id entity))                ;; Line 37 — UUID → string
   :user-id (str (:summary/user-id entity))
   :generation-id (when-let [gen-id (:summary/generation-id entity)]
                    (str gen-id))
   :source (source-keyword->string (:summary/source entity))
   ...
   :created-at (util/format-iso-8601 (:summary/created-at entity))  ;; Line 46 — Instant → ISO-8601
   :updated-at (util/format-iso-8601 (:summary/updated-at entity))
   :accepted-at (when-let [accepted (:summary/accepted-at entity)]
                  (util/format-iso-8601 accepted))})  ;; Line 50
```

**Problem:** DTO layer wie, że entity pochodzi z XTDB (komentarz line 29: "XTDB summary entity"). Konwersja zakłada typy XTDB (`:xt/id`, `java.time.Instant`, `java.util.UUID`).

---

#### Util layer (Java interop — XTDB native types)

```clojure
;; File: src/com/apriary/util.clj
;; Lines: 17, 126-128, 146

(let [parsed (java.util.UUID/fromString uuid-str)])  ;; Line 17

(defn format-iso-8601
  "Format an instant (java.time.Instant or java.util.Date) as ISO-8601 string."
  [instant]
  (if (instance? java.time.Instant instant)      ;; Line 128
    ...))

:timestamp (format-iso-8601 (java.time.Instant/now))  ;; Line 146
```

**Problem:** `java.time.Instant` i `java.util.UUID` to XTDB native types (XTDB persists them as-is). Util layer zakłada, że wszędzie używamy tych typów — zmiana bazy na PostgreSQL (która ma inne typy: `timestamp`, `uuid` SQL types) wymaga refaktoru util layer.

---

### D-02: Java stdlib interop (java.time.Instant, java.util.UUID)

**Typ zależności:** Typy natywne XTDB

**Gdzie przecieka:**
- **Services:** 29 wywołań `Instant/now` + `UUID/randomUUID`
  - summary.clj: lines 221-222, 318, 480
  - product.clj: lines 51-53
  - generation.clj: lines 42-43, 255, 371
- **Pages:** csv_import.clj (lines 78, 91), summaries_view.clj (line 204-206)
- **UI:** ui/error.clj (line 72), ui.clj (line 62), ui/helpers.clj (line 50)
- **DTO:** dto/summary.clj (lines 46-50), dto/generation.clj (line 88)

**Liczba plików:** 7 (services) + 3 (pages) + 3 (ui) + 2 (dto) = **15 plików**

**Problem:** `java.time.Instant` i `java.util.UUID` to typy natywne XTDB. Inne bazy (PostgreSQL) mają własne typy (`timestamp`, `uuid` SQL). Zmiana bazy wymaga refaktoru wszystkich 15 plików.

---

### D-03: Cheshire (JSON serialization)

**Typ zależności:** External library (JSON codec)

**Gdzie przecieka:**
- **DTO layer:** dto/summary.clj, dto/generation.clj — używają util/format-iso-8601, który zakłada Cheshire w tle
- **Pages layer:** używa Cheshire via Biff middleware (muuntaja/wrap-format)

**Liczba plików:** ~3 DTO + middleware (nie bezpośredni import, więc mniejszy przeciek)

**Problem:** Mniejszy niż XTDB — Cheshire jest explicite w deps.edn, a konwersja JSON jest izolowana w DTO. NIE jest to krytyczny przeciek (JSON codec to commodity).

---

## KROK 2 — KLASYFIKACJA i wybór #1

| Zależność | (a) Warstwy/pliki dotknięte | (b) Ryzyko/koszt wymiany dziś | (c) Deklaracja wymienialności (rozjazd) | Wybór |
|-----------|----------------------------|-------------------------------|------------------------------------------|-------|
| **D-01: XTDB API** | **3 warstwy** (services, pages, dto), **8 plików**, **51 wywołań API** | ⚠️ **BARDZO WYSOKI** — Datalog queries nie są SQL; każde `xt/q` wymaga przepisania na SQL. Transaction ops (`:xtdb.api/put`) to XTDB-specific syntax. | ✅ **TAK** — 01-domain-distillation.md:206 explicite deklaruje "można zastąpić PostgreSQL". **ROZJAZD:** kod NIE dotrzymuje. | **✅ #1** |
| D-02: Java interop (Instant, UUID) | **4 warstwy** (services, pages, ui, dto), **15 plików**, **29 wywołań** | ⚠️ **WYSOKI** — typy natywne XTDB. PostgreSQL ma własne typy SQL (`timestamp`, `uuid`). Wymiana wymaga value object wrapperów. | ⚠️ **POŚREDNIO** — 01-domain-distillation.md deklaruje wymienialność bazy, więc implikuje wymienialność typów. | — (związany z D-01) |
| D-03: Cheshire JSON | **2 warstwy** (dto, pages middleware), **~3 pliki** | ✅ **NISKI** — JSON codec to commodity. Transit, jsonista, data.json to dropin replacements. | ❌ **NIE** — żadna deklaracja. | — |

### Wybór: **D-01 XTDB API**

**Uzasadnienie:**

**(a) Liczba warstw/plików:** **3 warstwy, 8 plików, 51 wywołań API**
- Services: 4 pliki (summary, product, product_rankings, generation) — 30 wywołań
- Pages: 4 pliki (summaries_view, summaries, csv_import, products) — 21 wywołań
- DTO: 2 pliki (summary, generation) — pośredni przeciek (XTDB entity → DTO konwersja)

**Porównanie:** D-02 (Java interop) dotyka 15 plików, ale to KONSEKWENCJA D-01 (XTDB używa tych typów). Usunięcie D-01 automatycznie rozwiąże D-02.

**(b) Ryzyko/koszt wymiany dziś:** ⚠️ **BARDZO WYSOKI**
- **Datalog queries NIE są SQL:** Każde `xt/q` (15 wywołań) wymaga przepisania na SQL SELECT. Przykład:
  ```clojure
  ;; XTDB Datalog
  (xt/q db {:find '[?s]
            :where [['?s :summary/user-id user-id]
                    ['?s :summary/content '?content]]})
  
  ;; PostgreSQL SQL (hipotetyczny swap)
  SELECT id FROM summaries WHERE user_id = ? AND content IS NOT NULL
  ```
  Nie ma mechanicznej translacji — każde query wymaga hand-coded SQL.

- **Transaction ops są XTDB-specific:** `[:xtdb.api/put entity]` (18 wywołań) to XTDB syntax. PostgreSQL wymaga `INSERT ... ON CONFLICT UPDATE` lub ORM. Brak bezpośredniej translacji.

- **Bitemporal features w XTDB:** CLAUDE.md:40 deklaruje "bitemporal database". Jeśli kod kiedykolwiek zacznie używać valid-time / transaction-time (dziś nie używa, ale może w przyszłości), swap na PostgreSQL traci tę funkcjonalność.

**(c) Deklaracja wymienialności — ROZJAZD:**

**Deklaracja (context/domain/01-domain-distillation.md:206):**
```markdown
| **XTDB query logic** | **GENERIC** | Datalog queries, transakcje — technologia bazodanowa. 
  Wymienialny komponent (można zastąpić PostgreSQL bez zmiany logiki domenowej). |
```

**Kod (src/com/apriary/services/summary.clj:2, 88-103):**
```clojure
(ns com.apriary.services.summary
  (:require [xtdb.api :as xt] ...))  ;; XTDB import w service layer

;; Build query with RLS: only summaries for this user
(let [base-where [['?s :summary/user-id user-id]
                  ['?s :summary/content '?content]
                  ['?s :summary/created-at '?created]
                  ['?s :summary/source '?source]]
      query-params {:find '[?s] :where where-clause}
      all-results (xt/q db query-params)]  ;; Datalog query BEZPOŚREDNIO w service
```

**Rozjazd:** Dokumentacja deklaruje "można zastąpić PostgreSQL bez zmiany logiki domenowej", ale kod services/summary.clj BEZPOŚREDNIO wywołuje `xt/q` z Datalog queries. Zmiana na PostgreSQL wymaga przepisania każdego query w services + pages. **Logika domenowa jest zakleszczona w XTDB API.**

**Konkluzja:** Najbardziej rozprosrzony przeciek (3 warstwy, 8 plików), najwyższe ryzyko wymiany (Datalog ≠ SQL), i explicytny rozjazd między deklaracją ("wymienialny komponent") a kodem (XTDB hardcoded w każdej warstwie). To jest #1 kandydat na ACL.

---

## KROK 3 — DIAGNOZA

### 3.1 Duplikacja — Datalog queries w wielu warstwach

#### Duplikacja #1: Query user summaries

**Services layer (src/com/apriary/services/summary.clj:88-103):**
```clojure
;; Build query with RLS: only summaries for this user
(let [base-where [['?s :summary/user-id user-id]
                  ['?s :summary/content '?content]
                  ['?s :summary/created-at '?created]
                  ['?s :summary/source '?source]]
      
      where-clause (if source
                     (conj base-where ['?s :summary/source source])
                     base-where)
      
      query-params {:find '[?s]
                    :where where-clause}
      
      all-results (xt/q db query-params)
      total-count (count all-results)
      paginated-results (take limit (drop offset all-results))]
  
  [:ok {:summaries (mapv (fn [[?s]] (xt/entity db ?s)) paginated-results) ...}])
```

**Pages layer (src/com/apriary/pages/summaries_view.clj:1210-1214):**
```clojure
(let [summaries-query {:find '[?s]
                       :where [['?s :summary/generation-id generation-id]
                               ['?s :summary/user-id user-id]]}
      summary-ids (xt/q fresh-db summaries-query)
      summaries (mapv (fn [[?s]] (xt/entity fresh-db ?s)) summary-ids)]
```

**Duplikacja:** Ten sam pattern (Datalog query → `xt/q` → `xt/entity` → mapv) jest powtórzony w services + pages. Każda warstwa buduje własne queries zamiast delegować do Repository.

---

#### Duplikacja #2: Transaction ops (`:xtdb.api/put`)

**Services layer (src/com/apriary/services/summary.clj:236):**
```clojure
(xt/submit-tx node [[:xtdb.api/put entity]])
```

**Services layer (src/com/apriary/services/product.clj:67-70):**
```clojure
(let [tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) entities)]
  (xt/submit-tx node tx-ops))
```

**Pages layer (src/com/apriary/pages/summaries_view.clj:578):**
```clojure
(let [tx-ops (mapv (fn [entity] [:xtdb.api/put entity]) summary-entities)]
  (xt/submit-tx node tx-ops))
```

**Duplikacja:** Transaction syntax `[:xtdb.api/put entity]` jest powtórzona 18 razy w services + pages. Każda operacja persist buduje ten sam vector ops ręcznie.

---

### 3.2 Przecieki przez granice

#### Przeciek #1: XTDB node object w sygnaturach publicznych

**Services layer (src/com/apriary/services/summary.clj:195-206):**
```clojure
(defn create-manual-summary
  "Create a new manual summary (not AI-generated).
   
   Params:
   - node: XTDB node instance (not db - needs to call submit-tx)  ;; ❌ XTDB type w sygnaturze
   - user-id: UUID of the authenticated user
   - summary-data: Map with keys: ..."
  [node user-id summary-data]
  ...)
```

**Pages layer wywołuje (src/com/apriary/pages/summaries_view.clj):**
```clojure
(let [node (:biff.xtdb/node ctx)]  ;; Pages wie o XTDB node
  (summary-service/create-manual-summary node user-id data))
```

**Problem:** Services layer ujawnia `node` (XTDB-specific type) w sygnaturze publicznej. Pages layer musi "wiedzieć", że backend używa XTDB i przekazywać `node`. Zmiana na PostgreSQL wymaga refaktoru sygnatur w services + wszystkich wywołań w pages.

**Dlaczego groźny:** Services layer to LOGIKA DOMENOWA, nie infrastruktura. Przyjmowanie `node` jako parametru to **Dependency Injection przez argument**, ale bez abstrakcji (interface). `node` jest konkretnym typem XTDB (`xtdb.node.XtdbNode`), nie portem.

---

#### Przeciek #2: XTDB entity format w DTO layer

**DTO layer (src/com/apriary/dto/summary.clj:20-35):**
```clojure
(defn entity->dto
  "Convert an XTDB summary entity to API response DTO format.  ;; ❌ Komentarz explicytny: "XTDB entity"
   
   This function transforms the internal XTDB entity representation to the
   public API format by:
   1. Removing namespace prefixes from field names (keeping kebab-case)
   2. Formatting timestamps as ISO-8601 strings
   3. Converting keyword enums to strings
   4. Converting UUIDs to strings
   5. Removing internal fields (xt/id)  ;; ❌ Świadomość `:xt/id` (XTDB internal field)
   ..."
  [entity]
  {:id (str (:summary/id entity))  ;; Assumes XTDB namespaced keywords
   :created-at (util/format-iso-8601 (:summary/created-at entity))  ;; Assumes java.time.Instant
   ...})
```

**Problem:** DTO layer wie, że `entity` pochodzi z XTDB:
1. Komentarz explicytnie mówi "XTDB entity".
2. Funkcja zakłada XTDB namespaced keywords (`:summary/id`, `:summary/created-at`).
3. Funkcja zakłada typy XTDB (`java.time.Instant`, `java.util.UUID`).
4. Funkcja wie o XTDB internal field (`:xt/id`) i go usuwa.

Zmiana na PostgreSQL (gdzie entity może być SQL row lub JDBC ResultSet) wymaga refaktoru DTO konwersji.

---

#### Przeciek #3: Datalog syntax w Pages layer

**Pages layer (src/com/apriary/pages/summaries_view.clj:1210-1214):**
```clojure
(let [summaries-query {:find '[?s]
                       :where [['?s :summary/generation-id generation-id]
                               ['?s :summary/user-id user-id]]}
      summary-ids (xt/q fresh-db summaries-query)  ;; ❌ Pages layer buduje Datalog queries
      summaries (mapv (fn [[?s]] (xt/entity fresh-db ?s)) summary-ids)]
```

**Problem:** Pages layer (HTTP handlers) BEZPOŚREDNIO buduje Datalog queries i wywołuje `xt/q`. To jest **wyciek logiki persystencji do warstwy prezentacji**.

**Dlaczego groźny:** Pages layer powinien wywoływać Services layer (który deleguje do Repository). Tutaj Pages OMIJA Services i robi query bezpośrednio. Zmiana na PostgreSQL wymaga refaktoru Pages handlers.

---

### 3.3 Deklaracja wymienialności — cytaty

**context/domain/01-domain-distillation.md:206:**
```markdown
| **XTDB query logic** | **GENERIC** | Datalog queries, transakcje — technologia bazodanowa. 
  Wymienialny komponent (można zastąpić PostgreSQL bez zmiany logiki domenowej). |
```

**Komentarz:** Dokumentacja deklaruje XTDB jako "GENERIC subdomain" (nie Core, nie Supporting) i explicite mówi "można zastąpić PostgreSQL". To jest SILNA deklaracja wymienialności.

**Kod NIE dotrzymuje:**
- **Services layer:** Importuje `xtdb.api` (8 plików), wywołuje `xt/q` (15 razy), buduje Datalog queries (hardcoded syntax).
- **Pages layer:** Importuje `xtdb.api` (4 pliki), buduje własne queries (3 razy).
- **DTO layer:** Zakłada XTDB entity format (namespaced keywords, `:xt/id` field).

**Weryfikacja swap cost dziś:**
1. Zainstalować PostgreSQL JDBC driver.
2. Przepisać **15 Datalog queries** na SQL (ręcznie, brak automatic translation).
3. Przepisać **18 transaction ops** (`[:xtdb.api/put entity]` → `INSERT ... ON CONFLICT`).
4. Zmienić **8 plików services** (usunąć `xtdb.api` import, zmienić sygnatury funkcji z `node` na `datasource`).
5. Zmienić **4 pliki pages** (usunąć bezpośrednie query calls, delegować do services).
6. Zmienić **2 pliki DTO** (zmienić entity format assumption z XTDB na SQL row).
7. Zmienić **schemat** (schema.clj:3-60 zakłada Malli maps, ale PostgreSQL wymaga SQL DDL).

**Oszacowanie:** ~200-300 linii kodu do zmiany, w 14 plikach, przez 3 warstwy. **Koszt wymiany: BARDZO WYSOKI.**

**Konkluzja rozjazdu:** Deklaracja "można zastąpić PostgreSQL" jest FAŁSZYWA w obecnym kodzie. XTDB jest hardcoded w każdej warstwie.

---

## KROK 4 — PROJEKT ACL

### 4.1 Domenowy value object: **Entity** (aggregate root wrapper)

**Odpowiedzialność:** Zapewnić, że logika domenowa NIE ZNA formatu persystencji (XTDB entity, SQL row, JSON doc). Entity jest pure domain object z named fields (nie namespaced keywords).

**Typ:**
```clojure
;; Domain entity (NIE XTDB entity)
(defrecord Summary [id user-id generation-id source hive-number observation-date 
                    special-feature content created-at updated-at accepted-at])

(defrecord Product [id user-id hive-number date product quantity metric 
                    created-at updated-at])

(defrecord Generation [id user-id model generated-count accepted-unedited-count 
                       accepted-edited-count duration-ms created-at updated-at])
```

**Sygnatury + operacje:**
```clojure
(ns com.apriary.domain.summary)

;; Constructor (z walidacją domenową)
(defn create-summary
  "Create Summary domain entity with validation.
   
   Args:
     data - Map with :user-id, :content, :source, optional metadata
   
   Returns:
     Summary record or throws domain error"
  [{:keys [user-id content source hive-number observation-date special-feature]}]
  
  ;; Domenowa walidacja (50-50k chars, source enum, etc.)
  (when (or (nil? content) (< (count content) 50))
    (throw (ex-info "Content too short" {:type :domain-error :code :invalid-content})))
  
  (->Summary
    (java.util.UUID/randomUUID)  ;; ID generowane w domenie, NIE w repository
    user-id
    nil  ;; generation-id optional
    source
    hive-number
    observation-date
    special-feature
    content
    (java.time.Instant/now)  ;; Timestamps w domenie
    (java.time.Instant/now)
    nil))  ;; accepted-at

;; Operacje domenowe (NIE persystencja)
(defn accept-summary
  "Mark summary as accepted (domain logic).
   
   Preconditions:
   - Summary must be AI-generated (not manual)
   - Summary must not be already accepted
   
   Returns:
     Updated Summary record or throws domain error"
  [summary]
  
  (when (= (:source summary) :manual)
    (throw (ex-info "Cannot accept manual summaries" 
                    {:type :domain-error :code :manual-summary})))
  
  (when (:accepted-at summary)
    (throw (ex-info "Summary already accepted"
                    {:type :domain-error :code :already-accepted})))
  
  (assoc summary :accepted-at (java.time.Instant/now)))
```

**Klucz:** Domain entity (Summary record) NIE wie o XTDB. Operacje domenowe (`accept-summary`) działają na record, nie na XTDB entity.

---

### 4.2 Port (interface domenowy): **Repository**

**Odpowiedzialność:** Definicja kontraktu persystencji BEZ implementacji (adapter pattern).

**Interface:**
```clojure
(ns com.apriary.ports.summary-repository)

(defprotocol SummaryRepository
  "Port for Summary persistence operations.
   
   Implementations (adapters):
   - XTDBSummaryRepository (current)
   - PostgreSQLSummaryRepository (future swap)
   - InMemorySummaryRepository (tests)"
  
  (find-by-id [this summary-id user-id]
    "Load Summary by ID with RLS enforcement.
     
     Args:
       summary-id - UUID
       user-id - UUID (for RLS)
     
     Returns:
       Summary domain record or nil if not found / RLS violation")
  
  (find-by-user [this user-id opts]
    "Query summaries for user with filters/pagination.
     
     Args:
       user-id - UUID
       opts - {:source keyword, :limit int, :offset int, :sort-by string, :sort-order string}
     
     Returns:
       {:summaries [Summary ...] :total-count int :limit int :offset int}")
  
  (save [this summary]
    "Persist Summary (insert or update).
     
     Args:
       summary - Summary domain record
     
     Returns:
       Updated Summary record (with timestamps) or throws persistence error")
  
  (delete [this summary-id user-id]
    "Delete Summary with RLS enforcement.
     
     Args:
       summary-id - UUID
       user-id - UUID (for RLS)
     
     Returns:
       :ok or throws error")
  
  (save-batch [this summaries]
    "Persist multiple Summaries in ATOMIC transaction.
     
     Args:
       summaries - Collection of Summary domain records
     
     Returns:
       Collection of updated Summary records or throws on transaction failure"))
```

**Klucz:** Port (protocol) NIE wie o XTDB. Typy są domenowe (Summary record, UUID, Instant). Implementacja (adapter) zajmie się translacją Summary ↔ XTDB entity.

---

### 4.3 Adapter (implementacja portu): **XTDBSummaryRepository**

**Odpowiedzialność:** Translacja między domeną (Summary record) a XTDB (namespaced entity). To jest JEDYNE miejsce, które wie o XTDB.

**Implementation:**
```clojure
(ns com.apriary.adapters.xtdb.summary-repository
  (:require [com.apriary.ports.summary-repository :as port]
            [com.apriary.domain.summary :as domain]
            [xtdb.api :as xt]
            [clojure.tools.logging :as log]))

;; =============================================================================
;; ACL: Domain ↔ XTDB translation
;; =============================================================================

(defn- domain->xtdb
  "Translate Summary domain record to XTDB entity.
   
   This is the ONLY function that knows XTDB entity format."
  [summary]
  {:xt/id (:id summary)
   :summary/id (:id summary)
   :summary/user-id (:user-id summary)
   :summary/generation-id (:generation-id summary)
   :summary/source (:source summary)
   :summary/hive-number (:hive-number summary)
   :summary/observation-date (:observation-date summary)
   :summary/special-feature (:special-feature summary)
   :summary/content (:content summary)
   :summary/created-at (:created-at summary)
   :summary/updated-at (:updated-at summary)
   :summary/accepted-at (:accepted-at summary)})

(defn- xtdb->domain
  "Translate XTDB entity to Summary domain record.
   
   This is the ONLY function that reads XTDB entity format."
  [entity]
  (when entity
    (domain/->Summary
      (:summary/id entity)
      (:summary/user-id entity)
      (:summary/generation-id entity)
      (:summary/source entity)
      (:summary/hive-number entity)
      (:summary/observation-date entity)
      (:summary/special-feature entity)
      (:summary/content entity)
      (:summary/created-at entity)
      (:summary/updated-at entity)
      (:summary/accepted-at entity))))

;; =============================================================================
;; Repository implementation (XTDB adapter)
;; =============================================================================

(defrecord XTDBSummaryRepository [node]
  port/SummaryRepository
  
  (find-by-id [this summary-id user-id]
    (let [db (xt/db node)
          entity (xt/entity db summary-id)]
      
      ;; RLS check
      (when (and entity (= (:summary/user-id entity) user-id))
        (xtdb->domain entity))))  ;; ✅ ACL: XTDB entity → domain record
  
  (find-by-user [this user-id {:keys [source limit offset sort-by sort-order]
                                :or {limit 50 offset 0 sort-by "created-at" sort-order "desc"}}]
    (let [db (xt/db node)
          
          ;; Datalog query (ISOLATED to adapter)
          base-where [['?s :summary/user-id user-id]
                      ['?s :summary/content '?content]
                      ['?s :summary/created-at '?created]
                      ['?s :summary/source '?source]]
          
          where-clause (if source
                         (conj base-where ['?s :summary/source source])
                         base-where)
          
          query-params {:find '[?s] :where where-clause}
          
          all-results (xt/q db query-params)
          total-count (count all-results)
          paginated-results (take limit (drop offset all-results))]
      
      {:summaries (mapv (fn [[?s]]
                          (xtdb->domain (xt/entity db ?s)))  ;; ✅ ACL: XTDB → domain
                        paginated-results)
       :total-count total-count
       :limit limit
       :offset offset}))
  
  (save [this summary]
    (let [entity (domain->xtdb summary)  ;; ✅ ACL: domain → XTDB
          updated (assoc entity
                         :summary/updated-at (java.time.Instant/now))]
      
      ;; Transaction (ISOLATED to adapter)
      (xt/submit-tx node [[:xtdb.api/put updated]])
      
      ;; Return updated domain record
      (xtdb->domain updated)))
  
  (delete [this summary-id user-id]
    ;; RLS check first (deleguje do find-by-id)
    (when-not (port/find-by-id this summary-id user-id)
      (throw (ex-info "Summary not found or access denied"
                      {:type :domain-error :code :not-found})))
    
    (xt/submit-tx node [[:xtdb.api/delete summary-id]])
    :ok)
  
  (save-batch [this summaries]
    (let [entities (mapv domain->xtdb summaries)  ;; ✅ ACL: batch domain → XTDB
          tx-ops (mapv (fn [e] [:xtdb.api/put e]) entities)]
      
      (xt/submit-tx node tx-ops)
      
      ;; Return updated domain records
      (mapv xtdb->domain entities))))

;; =============================================================================
;; Constructor
;; =============================================================================

(defn create-repository
  "Factory for XTDB adapter.
   
   Args:
     node - XTDB node instance (injected by infrastructure)
   
   Returns:
     XTDBSummaryRepository implementing SummaryRepository port"
  [node]
  (->XTDBSummaryRepository node))
```

**Klucz:**
1. **ACL functions** (`domain->xtdb`, `xtdb->domain`) są PRYWATNE — tylko adapter je zna.
2. **Datalog queries** są IZOLOWANE w adapter — services layer ich NIE widzi.
3. **Transaction ops** (`[:xtdb.api/put ...]`) są IZOLOWANE w adapter.
4. **Protocol methods** operują na domain types (Summary record, UUID), NIE na XTDB types.

**Swap proof:** Zmiana na PostgreSQL wymaga TYLKO napisania `PostgreSQLSummaryRepository` (nowy adapter). Services layer (który używa portu) NIE ulega zmianie.

---

### 4.4 Service layer (delegacja do repository przez port)

**BEFORE (src/com/apriary/services/summary.clj:62-129):**
```clojure
(ns com.apriary.services.summary
  (:require [xtdb.api :as xt] ...))  ;; ❌ XTDB import

(defn list-summaries
  [db user-id & {:keys [sort-by sort-order source limit offset] ...}]
  
  ;; ❌ Datalog query w service layer
  (let [base-where [['?s :summary/user-id user-id]
                    ['?s :summary/content '?content] ...]
        query-params {:find '[?s] :where where-clause}
        all-results (xt/q db query-params)  ;; ❌ XTDB API call
        ...]
    [:ok {:summaries (mapv (fn [[?s]] (xt/entity db ?s)) paginated-results) ...}]))
```

**AFTER (NEW file: src/com/apriary/services/summary_service.clj):**
```clojure
(ns com.apriary.services.summary-service
  (:require [com.apriary.ports.summary-repository :as repo]
            [com.apriary.domain.summary :as domain]
            [clojure.tools.logging :as log]))

;; ✅ NO XTDB import — service layer NIE ZNA XTDB

(defn list-summaries
  "Query summaries for authenticated user.
   
   Args:
     repository - SummaryRepository port instance (injected)
     user-id - UUID
     opts - {:source keyword, :limit int, :offset int, ...}
   
   Returns:
     [:ok {:summaries [Summary ...] ...}] or [:error {...}]"
  [repository user-id opts]
  
  (try
    ;; Guard clause
    (when (nil? user-id)
      (throw (ex-info "user-id is required" {:type :domain-error :code :invalid-input})))
    
    ;; ✅ Deleguje do repository (port) — NIE wie o XTDB
    (let [result (repo/find-by-user repository user-id opts)]
      
      (log/info "Listed user summaries"
                :user-id user-id
                :count (count (:summaries result)))
      
      [:ok result])
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (if (= (:type data) :domain-error)
          [:error {:code (name (:code data)) :message (.getMessage e)}]
          (do
            (log/error "Failed to list summaries:" e)
            [:error {:code "INTERNAL_ERROR" :message "Failed to retrieve summaries"}]))))))

(defn create-manual-summary
  "Create manual summary (domain logic + repository).
   
   Args:
     repository - SummaryRepository port instance
     user-id - UUID
     summary-data - {:content string, :hive-number string, ...}
   
   Returns:
     [:ok Summary] or [:error {...}]"
  [repository user-id summary-data]
  
  (try
    ;; ✅ Domain logic (validation + entity creation)
    (let [summary (domain/create-summary
                    (assoc summary-data :user-id user-id :source :manual))
          
          ;; ✅ Repository call (port) — NIE wie o XTDB
          saved (repo/save repository summary)]
      
      (log/info "Created manual summary"
                :summary-id (:id saved)
                :user-id user-id)
      
      [:ok saved])
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (if (= (:type data) :domain-error)
          [:error {:code (name (:code data)) :message (.getMessage e)}]
          (do
            (log/error "Failed to create manual summary:" e)
            [:error {:code "INTERNAL_ERROR" :message "Failed to create summary"}]))))))
```

**Klucz:**
1. **NO `xtdb.api` import** — service layer NIE ZNA XTDB.
2. **Repository injected** — service przyjmuje `repository` (port interface), nie `node` (XTDB concrete type).
3. **Domain types** — operacje na Summary record, nie XTDB entity.
4. **Delegacja** — `repo/find-by-user` zamiast `xt/q`.

---

### 4.5 Pages layer (dependency injection)

**BEFORE (src/com/apriary/pages/summaries_view.clj):**
```clojure
(ns com.apriary.pages.summaries-view
  (:require [xtdb.api :as xt]  ;; ❌ XTDB import w pages
            [com.apriary.services.summary :as summary-service] ...))

(defn list-summaries-handler
  [{:keys [session biff.xtdb/node] :as ctx}]  ;; ❌ Pages wie o `biff.xtdb/node`
  
  (let [user-id (:uid session)
        db (xt/db node)  ;; ❌ Pages wywołuje XTDB API
        [status result] (summary-service/list-summaries db user-id ...)]
    ...))
```

**AFTER (NEW file: src/com/apriary/pages/summaries.clj):**
```clojure
(ns com.apriary.pages.summaries
  (:require [com.apriary.services.summary-service :as summary-svc]
            [com.apriary.adapters.xtdb.summary-repository :as xtdb-repo]  ;; ✅ Import adaptera, NIE XTDB API
            [com.apriary.dto.summary :as dto]
            [clojure.tools.logging :as log]))

;; ✅ NO `xtdb.api` import

(defn list-summaries-handler
  [{:keys [session biff.xtdb/node] :as ctx}]
  
  (if-not (some? (:uid session))
    {:status 401 ...}
    
    (let [user-id (:uid session)
          
          ;; ✅ Dependency injection: tworzymy adapter (repository) tutaj
          repository (xtdb-repo/create-repository node)  ;; ✅ Adapter factory
          
          ;; ✅ Wywołujemy service z repository (port)
          [status result] (summary-svc/list-summaries repository user-id
                                                       {:limit 50 :offset 0})]
      
      (if (= status :ok)
        ;; Success: konwersja domain → DTO
        (let [summaries (:summaries result)
              dtos (mapv dto/entity->dto summaries)]  ;; ✅ DTO konwersja oddzielona
          
          {:status 200
           :headers {"content-type" "application/json"}
           :body (cheshire/generate-string {:summaries dtos
                                             :total-count (:total-count result)})})
        
        ;; Error
        {:status 500
         :headers {"content-type" "application/json"}
         :body (cheshire/generate-string {:error (:message result)})}))))
```

**Klucz:**
1. **Dependency injection** — Pages tworzy adapter (`xtdb-repo/create-repository node`) i przekazuje do service.
2. **NO `xtdb.api` import** — Pages NIE wywołuje XTDB API bezpośrednio.
3. **DTO separation** — Domain Summary → DTO konwersja odbywa się w pages, NIE w service.

**Swap proof:** Zmiana na PostgreSQL wymaga TYLKO:
1. Napisać `PostgreSQLSummaryRepository` adapter.
2. Zmienić pages: `(postgres-repo/create-repository datasource)` zamiast `(xtdb-repo/create-repository node)`.
3. Service layer, Domain layer, DTO layer — **ZERO zmian**.

---

## KROK 5 — Dowód izolacji + before/after

### 5.1 Dowód izolacji (lista plików)

**Wymiana biblioteki (XTDB → PostgreSQL) dotyka TYLKO:**

| Dotknięte | Plik | Operacja |
|-----------|------|----------|
| ✅ | `src/com/apriary/adapters/xtdb/summary_repository.clj` | **DELETE** — usuń cały plik (adapter XTDB) |
| ✅ | `src/com/apriary/adapters/postgres/summary_repository.clj` | **CREATE** — napisz nowy adapter (PostgreSQL) |
| ✅ | `src/com/apriary/adapters/xtdb/product_repository.clj` | **DELETE** |
| ✅ | `src/com/apriary/adapters/postgres/product_repository.clj` | **CREATE** |
| ✅ | `src/com/apriary/adapters/xtdb/generation_repository.clj` | **DELETE** |
| ✅ | `src/com/apriary/adapters/postgres/generation_repository.clj` | **CREATE** |
| ✅ | `src/com/apriary/pages/summaries.clj` | **EDIT** (1 line) — `(postgres-repo/create-repository datasource)` zamiast `(xtdb-repo/create-repository node)` |
| ✅ | `src/com/apriary/pages/products.clj` | **EDIT** (1 line) — zmiana factory call |
| ✅ | `src/com/apriary/pages/generations.clj` | **EDIT** (1 line) — zmiana factory call |
| ✅ | `deps.edn` | **EDIT** — dodaj PostgreSQL JDBC driver, usuń Biff (jeśli XTDB pochodzi z Biff) |

**Razem:** 3 adaptery DELETE, 3 adaptery CREATE, 3 pages EDIT (1 line each), 1 deps.edn EDIT.

**NIE DOTKNIĘTE (ZERO zmian):**

| NIE dotknięte | Warstwa | Pliki | Dlaczego NIE dotknięte |
|---------------|---------|-------|------------------------|
| ❌ | Domain | `domain/summary.clj`, `domain/product.clj`, `domain/generation.clj` | Domain records nie wiedzą o XTDB — operują na pure data |
| ❌ | Ports | `ports/summary_repository.clj`, `ports/product_repository.clj`, `ports/generation_repository.clj` | Porty definiują kontrakt, nie implementację |
| ❌ | Services | `services/summary_service.clj`, `services/product_service.clj`, `services/generation_service.clj` | Services używają portów (interfaces), nie konkretnych adapterów |
| ❌ | DTO | `dto/summary.clj`, `dto/generation.clj` | DTO konwersja operuje na domain records, nie XTDB entities (po ACL) |
| ❌ | Schema | `schema.clj` | Malli schemas walidują domain data, nie XTDB entities |
| ❌ | UI | `ui/*.clj` (wszystkie pliki UI) | UI renderuje DTOs, nie XTDB entities |
| ❌ | Tests | `test/**/*.clj` | Testy używają `InMemoryRepository` (test double), nie XTDB |

**Razem:** **~20 plików ZERO zmian** przy wymianie XTDB → PostgreSQL.

---

### 5.2 Before/After (zduplikowane miejsca)

#### Before/After #1: Services layer — query summaries

**BEFORE (src/com/apriary/services/summary.clj:88-119):**
```clojure
(ns com.apriary.services.summary
  (:require [xtdb.api :as xt] ...))  ;; ❌ XTDB import

(defn list-summaries
  [db user-id & {:keys [sort-by sort-order source limit offset] ...}]
  
  ;; ❌ Datalog query hardcoded w service
  (let [base-where [['?s :summary/user-id user-id]
                    ['?s :summary/content '?content]
                    ['?s :summary/created-at '?created]
                    ['?s :summary/source '?source]]
        
        where-clause (if source
                       (conj base-where ['?s :summary/source source])
                       base-where)
        
        query-params {:find '[?s]
                      :where where-clause}
        
        ;; ❌ XTDB API call w service
        all-results (xt/q db query-params)
        total-count (count all-results)
        paginated-results (take limit (drop offset all-results))]
    
    ;; ❌ XTDB entity → mapv w service
    [:ok {:summaries (mapv (fn [[?s]] (xt/entity db ?s)) paginated-results)
          :total-count total-count
          :limit limit
          :offset offset}]))
```

**AFTER (NEW: src/com/apriary/services/summary_service.clj):**
```clojure
(ns com.apriary.services.summary-service
  (:require [com.apriary.ports.summary-repository :as repo]))  ;; ✅ Port import, NIE XTDB

(defn list-summaries
  [repository user-id opts]
  
  ;; ✅ Delegacja do repository (port) — NIE wie o XTDB
  (let [result (repo/find-by-user repository user-id opts)]
    
    [:ok result]))  ;; ✅ Repository zwraca domain records, nie XTDB entities
```

**Zysk:** Service layer ~90 linii → ~10 linii. Datalog query PRZENIESIONY do adaptera (src/com/apriary/adapters/xtdb/summary_repository.clj:find-by-user). Zmiana na PostgreSQL wymaga zmiany TYLKO adaptera (SQL query zamiast Datalog), service NIE dotknięty.

---

#### Before/After #2: Pages layer — bezpośredni query

**BEFORE (src/com/apriary/pages/summaries_view.clj:1210-1214):**
```clojure
(ns com.apriary.pages.summaries-view
  (:require [xtdb.api :as xt] ...))  ;; ❌ XTDB import w pages

(let [summaries-query {:find '[?s]
                       :where [['?s :summary/generation-id generation-id]
                               ['?s :summary/user-id user-id]]}
      ;; ❌ Pages buduje Datalog query
      summary-ids (xt/q fresh-db summaries-query)
      ;; ❌ Pages wywołuje xt/entity
      summaries (mapv (fn [[?s]] (xt/entity fresh-db ?s)) summary-ids)]
  ...)
```

**AFTER (NEW: src/com/apriary/pages/summaries.clj):**
```clojure
(ns com.apriary.pages.summaries
  (:require [com.apriary.services.summary-service :as summary-svc]
            [com.apriary.adapters.xtdb.summary-repository :as xtdb-repo]))  ;; ✅ Adapter, NIE XTDB API

(let [repository (xtdb-repo/create-repository node)  ;; ✅ Dependency injection
      
      ;; ✅ Wywołanie service z filtrem generation-id
      [status result] (summary-svc/find-by-generation repository generation-id user-id)
      
      summaries (:summaries result)]  ;; ✅ Domain records, nie XTDB entities
  ...)
```

**Zysk:** Pages layer NIE buduje queries. Datalog query PRZENIESIONY do adaptera. Zmiana na PostgreSQL wymaga zmiany TYLKO adaptera, pages NIE dotknięty.

---

#### Before/After #3: DTO layer — entity format assumption

**BEFORE (src/com/apriary/dto/summary.clj:20-50):**
```clojure
(defn entity->dto
  "Convert an XTDB summary entity to API response DTO format.  ;; ❌ Komentarz: "XTDB entity"
   
   This function transforms the internal XTDB entity representation to the
   public API format by:
   ...
   5. Removing internal fields (xt/id)  ;; ❌ Świadomość `:xt/id`
   ..."
  [entity]
  {:id (str (:summary/id entity))  ;; ❌ Zakłada XTDB namespaced keywords
   :user-id (str (:summary/user-id entity))
   ...
   :created-at (util/format-iso-8601 (:summary/created-at entity))  ;; ❌ Zakłada Instant type
   ...})
```

**AFTER (EDIT: src/com/apriary/dto/summary.clj):**
```clojure
(defn summary->dto
  "Convert Summary domain record to API response DTO.  ;; ✅ Komentarz: "domain record", NIE "XTDB entity"
   
   This function transforms the internal domain representation to JSON API format by:
   1. Converting UUIDs to strings
   2. Formatting timestamps as ISO-8601 strings
   3. Converting keyword enums to strings
   ..."
  [summary]
  {:id (str (:id summary))  ;; ✅ Domain record fields (NIE namespaced keywords)
   :user-id (str (:user-id summary))
   :generation-id (when-let [gen-id (:generation-id summary)]
                    (str gen-id))
   :source (name (:source summary))
   :hive-number (:hive-number summary)
   :observation-date (:observation-date summary)
   :special-feature (:special-feature summary)
   :content (:content summary)
   :created-at (util/format-iso-8601 (:created-at summary))  ;; ✅ Domain Instant, nie XTDB Instant
   :updated-at (util/format-iso-8601 (:updated-at summary))
   :accepted-at (when-let [accepted (:accepted-at summary)]
                  (util/format-iso-8601 accepted))})
```

**Zysk:** DTO layer operuje na domain records (Summary), nie XTDB entities. Komentarz zmieniony z "XTDB entity" → "domain record". Zmiana na PostgreSQL NIE dotyka DTO (domain record wygląda tak samo, niezależnie od bazy).

---

### 5.3 UI layer dostaje gotowe dane domenowe

**BEFORE (implicit assumption):**
```clojure
;; Pages handler zwraca XTDB entity bezpośrednio do UI
(let [summary (xt/entity db summary-id)]
  ;; UI template renderuje :summary/content, :summary/created-at
  (rum/render-static-markup
    [:div
      [:p (:summary/content summary)]  ;; ❌ UI wie o XTDB namespaced keywords
      [:span (str (:summary/created-at summary))]]))  ;; ❌ UI renderuje Instant bezpośrednio
```

**AFTER:**
```clojure
;; Pages handler konwertuje domain → DTO PRZED przekazaniem do UI
(let [repository (xtdb-repo/create-repository node)
      [status result] (summary-svc/get-summary repository summary-id user-id)
      summary (:summary result)  ;; ✅ Domain record
      dto (dto/summary->dto summary)]  ;; ✅ DTO konwersja w pages
  
  ;; UI template renderuje DTO (gotowe JSON-friendly stringi)
  (rum/render-static-markup
    [:div
      [:p (:content dto)]  ;; ✅ UI wie o DTO shape, NIE XTDB shape
      [:span (:created-at dto)]]))  ;; ✅ ISO-8601 string, nie Instant object
```

**Zysk:** UI layer NIE wie o XTDB entity format. UI renderuje DTOs (stringi, liczby, booleany), nie XTDB types (Instant, UUID, keywords).

---

### 5.4 Rozstrzygnięcie otwartych pytań (opartych o XTDB docs)

#### Pytanie 1: Jak obsłużyć transakcje atomowe (batch persist)?

**XTDB contract:**
```clojure
;; XTDB transakcja atomowa
(xt/submit-tx node [[:xtdb.api/put entity1]
                    [:xtdb.api/put entity2]
                    [:xtdb.api/put entity3]])
```

Wszystkie ops commitują razem LUB rollback razem (atomicité).

**Decyzja (zakodowana w ACL):**
```clojure
;; Port (interface)
(defprotocol SummaryRepository
  (save-batch [this summaries]
    "Persist multiple Summaries in ATOMIC transaction."))

;; XTDB adapter
(save-batch [this summaries]
  (let [entities (mapv domain->xtdb summaries)
        tx-ops (mapv (fn [e] [:xtdb.api/put e]) entities)]
    (xt/submit-tx node tx-ops)  ;; ✅ XTDB atomic transaction
    (mapv xtdb->domain entities)))

;; PostgreSQL adapter (future)
(save-batch [this summaries]
  (jdbc/with-db-transaction [tx datasource]  ;; ✅ PostgreSQL JDBC transaction
    (doseq [summary summaries]
      (jdbc/insert! tx :summaries (domain->sql-row summary)))))
```

**Gdzie zakodowane:** W adapter layer (`save-batch` method). Port definiuje kontrakt ("ATOMIC transaction"), adapter implementuje jak to osiągnąć (XTDB: `submit-tx`, PostgreSQL: JDBC transaction).

---

#### Pytanie 2: Jak obsłużyć RLS (Row-Level Security)?

**XTDB contract:**
```clojure
;; RLS w XTDB = predykat w query
(let [query {:find '[?s]
             :where [['?s :summary/user-id user-id]]}]  ;; RLS predicate
  (xt/q db query))
```

**Decyzja (zakodowana w ACL):**
```clojure
;; Port (interface) — RLS jako user-id parameter
(defprotocol SummaryRepository
  (find-by-id [this summary-id user-id]
    "Load Summary by ID with RLS enforcement. Returns nil if RLS violation."))

;; XTDB adapter
(find-by-id [this summary-id user-id]
  (let [db (xt/db node)
        entity (xt/entity db summary-id)]
    (when (and entity (= (:summary/user-id entity) user-id))  ;; ✅ RLS check
      (xtdb->domain entity))))

;; PostgreSQL adapter (future)
(find-by-id [this summary-id user-id]
  (let [row (jdbc/query datasource
                        ["SELECT * FROM summaries WHERE id = ? AND user_id = ?"  ;; ✅ RLS in WHERE clause
                         summary-id user-id])]
    (when (seq row)
      (sql-row->domain (first row)))))
```

**Gdzie zakodowane:** W adapter layer (`find-by-id` method). Port definiuje kontrakt ("with RLS enforcement"), adapter implementuje jak to sprawdzić (XTDB: post-load check, PostgreSQL: WHERE clause).

---

#### Pytanie 3: Jak obsłużyć pagination?

**XTDB contract:**
```clojure
;; XTDB nie ma built-in pagination — robimy to w Clojure
(let [all-results (xt/q db query)]
  (take limit (drop offset all-results)))
```

**Decyzja (zakodowana w ACL):**
```clojure
;; Port (interface)
(defprotocol SummaryRepository
  (find-by-user [this user-id opts]
    "Query with {:limit int, :offset int}. Returns {:summaries [...] :total-count int}."))

;; XTDB adapter
(find-by-user [this user-id {:keys [limit offset]}]
  (let [all-results (xt/q db query)
        total-count (count all-results)
        paginated (take limit (drop offset all-results))]  ;; ✅ In-memory pagination
    {:summaries (mapv xtdb->domain paginated)
     :total-count total-count}))

;; PostgreSQL adapter (future)
(find-by-user [this user-id {:keys [limit offset]}]
  (let [rows (jdbc/query datasource
                         ["SELECT * FROM summaries WHERE user_id = ? LIMIT ? OFFSET ?"  ;; ✅ SQL pagination
                          user-id limit offset])
        total-count (jdbc/query-first datasource
                                       ["SELECT COUNT(*) FROM summaries WHERE user_id = ?" user-id])]
    {:summaries (mapv sql-row->domain rows)
     :total-count (:count total-count)}))
```

**Gdzie zakodowane:** W adapter layer (`find-by-user` method). XTDB robi in-memory pagination (cheap dla small datasets), PostgreSQL robi SQL LIMIT/OFFSET (cheap dla large datasets).

---

## KROK 6 — Weryfikacja i plan

### 6.1 Kryterium sukcesu: `grep xtdb.api`

**PRZED refaktorem:**
```bash
$ grep -rn "xtdb.api" src/com/apriary --include="*.clj"
src/com/apriary/services/summary.clj:2:  (:require [xtdb.api :as xt] ...)
src/com/apriary/services/product.clj:2:  (:require [xtdb.api :as xt] ...)
src/com/apriary/services/product_rankings.clj:5:  (:require [xtdb.api :as xt] ...)
src/com/apriary/services/generation.clj:2:  (:require [xtdb.api :as xt] ...)
src/com/apriary/pages/summaries_view.clj:27:  (:require [xtdb.api :as xt] ...)
src/com/apriary/pages/summaries.clj:10:  (:require [xtdb.api :as xt] ...)
src/com/apriary/pages/csv_import.clj:16:  (:require [xtdb.api :as xt] ...)
src/com/apriary/pages/products.clj:11:  (:require [xtdb.api :as xt] ...)
(+ 43 wywołania API w tych plikach)
```

**Razem:** 8 plików importuje `xtdb.api`, 51 wywołań API.

---

**PO refaktorze:**
```bash
$ grep -rn "xtdb.api" src/com/apriary --include="*.clj"
src/com/apriary/adapters/xtdb/summary_repository.clj:4:  (:require [xtdb.api :as xt] ...)
src/com/apriary/adapters/xtdb/product_repository.clj:4:  (:require [xtdb.api :as xt] ...)
src/com/apriary/adapters/xtdb/generation_repository.clj:4:  (:require [xtdb.api :as xt] ...)
(+ ~30 wywołań API w tych 3 plikach — prywatne do adaptera)
```

**Razem:** 3 pliki importują `xtdb.api` (TYLKO adaptery), 30 wywołań API (wszystkie w ACL functions).

**✅ Kryterium spełnione:** `grep xtdb.api` zwraca WYŁĄCZNIE pliki w `src/com/apriary/adapters/xtdb/`.

---

### 6.2 Pliki dziś znające XTDB vs po refaktorze

| Warstwa | PRZED (pliki znające XTDB) | PO (pliki znające XTDB) | Różnica |
|---------|----------------------------|-------------------------|---------|
| **Services** | summary.clj, product.clj, product_rankings.clj, generation.clj (4 pliki) | **0 plików** | -4 |
| **Pages** | summaries_view.clj, summaries.clj, csv_import.clj, products.clj (4 pliki) | **0 plików** | -4 |
| **DTO** | summary.clj, generation.clj (2 pliki — pośredni przeciek) | **0 plików** | -2 |
| **Adapters** | **0 plików** (warstwa nie istnieje) | summary_repository.clj, product_repository.clj, generation_repository.clj (3 pliki) | +3 |
| **RAZEM** | **10 plików** (services + pages + dto) | **3 pliki** (TYLKO adapters) | **-7 plików** |

**Zysk:** XTDB przeciek zredukowany z 10 plików (3 warstwy) do 3 plików (1 warstwa adapter). Services, Pages, DTO — wszystkie IZOLOWANE od XTDB.

---

### 6.3 Plan faz refaktoru

#### Faza 0: Setup — utworzenie struktury katalogów

**Akcje:**
1. Utwórz katalogi:
   ```
   src/com/apriary/domain/
   src/com/apriary/ports/
   src/com/apriary/adapters/xtdb/
   ```
2. Dodaj README w każdym katalogu wyjaśniający odpowiedzialność warstwy.

**Rezultat:** Struktura zgodna z Hexagonal Architecture (domain → ports → adapters).

---

#### Faza 1: Domain layer — definiuj pure domain objects

**Cel:** Stworzyć domain records (Summary, Product, Generation) BEZ wiedzy o XTDB.

**Akcje:**
1. Utwórz `src/com/apriary/domain/summary.clj`:
   - `(defrecord Summary [id user-id generation-id source ...])`.
   - `(defn create-summary [data] ...)` — konstruktor z walidacją domenową.
   - `(defn accept-summary [summary] ...)` — operacje domenowe.
2. Analogicznie dla Product, Generation.
3. Uruchom `clj -M:test` — testy domenowe (pure functions, bez I/O).

**Rezultat:** Domain layer istnieje, NIE zależy od XTDB.

---

#### Faza 2: Ports layer — definiuj repository interfaces

**Cel:** Stworzyć protokoły (contracts) dla persystencji BEZ implementacji.

**Akcje:**
1. Utwórz `src/com/apriary/ports/summary_repository.clj`:
   ```clojure
   (defprotocol SummaryRepository
     (find-by-id [this summary-id user-id])
     (find-by-user [this user-id opts])
     (save [this summary])
     (delete [this summary-id user-id])
     (save-batch [this summaries]))
   ```
2. Analogicznie dla ProductRepository, GenerationRepository.

**Rezultat:** Porty zdefiniowane, kontrakt jasny, BEZ implementacji.

---

#### Faza 3: Adapters layer — XTDB implementation

**Cel:** Przenieść XTDB logic z services do adapterów.

**Akcje:**
1. Utwórz `src/com/apriary/adapters/xtdb/summary_repository.clj`:
   - `(defn- domain->xtdb [summary] ...)` — ACL function.
   - `(defn- xtdb->domain [entity] ...)` — ACL function.
   - `(defrecord XTDBSummaryRepository [node] ...)` — implementacja portu.
   - `(defn create-repository [node] ...)` — factory.
2. KOPIUJ Datalog queries z `services/summary.clj` do `adapters/xtdb/summary_repository.clj` (metody `find-by-user`, itp).
3. KOPIUJ transaction ops z `services/summary.clj` do `adapters/xtdb/summary_repository.clj` (metoda `save`).
4. Analogicznie dla Product, Generation.

**Rezultat:** Adaptery implementują porty, XTDB logic izolowana w adapters layer.

---

#### Faza 4: Services layer — delegacja do portów

**Cel:** Przepisać services na delegację do repository (port).

**Akcje:**
1. Utwórz `src/com/apriary/services/summary_service.clj` (NOWY plik):
   - Przyjmuje `repository` (port), NIE `node` (XTDB concrete type).
   - Deleguje do `repo/find-by-user`, `repo/save`, itp.
2. USUŃ `xtdb.api` import z services.
3. USUŃ Datalog queries z services (już są w adapter).
4. Uruchom `clj -M:test` — testy services z `InMemoryRepository` (test double).

**Rezultat:** Services layer NIE zna XTDB, deleguje do portów.

---

#### Faza 5: Pages layer — dependency injection

**Cel:** Zmienić pages na wywołanie services z injected repository.

**Akcje:**
1. EDIT `src/com/apriary/pages/summaries.clj`:
   - Usuń `xtdb.api` import.
   - Dodaj `(:require [com.apriary.adapters.xtdb.summary-repository :as xtdb-repo])`.
   - W handler: `(let [repository (xtdb-repo/create-repository node)] ...)`.
   - Wywołaj `(summary-svc/list-summaries repository user-id opts)`.
2. USUŃ bezpośrednie `xt/q` calls z pages (są w adapter).
3. Analogicznie dla wszystkich pages handlers.

**Rezultat:** Pages layer NIE wywołuje XTDB API, dependency injection działa.

---

#### Faza 6: DTO layer — konwersja domain → DTO

**Cel:** Zmienić DTO konwersję z XTDB entity → Summary domain record.

**Akcje:**
1. EDIT `src/com/apriary/dto/summary.clj`:
   - Zmień komentarz z "XTDB entity" → "domain record".
   - Zmień sygnaturę `entity->dto` → `summary->dto [summary]`.
   - Usuń assumptions o XTDB (:xt/id, namespaced keywords).
2. Pages layer wywołuje `dto/summary->dto` NA domain record (zwróconym z service), NIE na XTDB entity.

**Rezultat:** DTO layer NIE wie o XTDB entity format.

---

#### Faza 7: Cleanup — usuń stary kod

**Cel:** Usunąć stare pliki services (które bezpośrednio używały XTDB).

**Akcje:**
1. **USUŃ** `src/com/apriary/services/summary.clj` (zastąpiony przez `summary_service.clj` + adapter).
2. **USUŃ** `src/com/apriary/services/product.clj` (analogicznie).
3. **USUŃ** `src/com/apriary/services/generation.clj` (analogicznie).
4. Uruchom `clj -M:test` → wszystkie testy PASS.
5. Uruchom `clj-kondo --lint src test` → brak warnings.

**Rezultat:** Kod czysty, stare pliki usunięte, XTDB izolowane w adapters.

---

#### Faza 8: Weryfikacja izolacji

**Cel:** Udowodnić, że XTDB jest izolowane.

**Akcje:**
1. Uruchom `grep -rn "xtdb.api" src/com/apriary --include="*.clj"` → tylko `src/com/apriary/adapters/xtdb/*.clj`.
2. Napisz test: `InMemoryRepository` (implementacja portu bez XTDB) → services layer działa BEZ XTDB.
3. (Opcjonalnie) Napisz `PostgreSQLRepository` adapter (proof-of-concept) → swap działa.

**Rezultat:** Izolacja potwierdzona, swap cost drastycznie zredukowany.

---

## Podsumowanie

Plan ACL izoluje XTDB 1.24 (przeciekający przez 3 warstwy: services, pages, dto) do wąskiej warstwy adapters. Dokumentacja explicite deklaruje XTDB jako "wymienialny komponent (można zastąpić PostgreSQL)" (01-domain-distillation.md:206), ale kod tej wymienialności NIE dotrzymuje — 8 plików importuje `xtdb.api`, 51 wywołań API są rozsiane po services + pages. ACL wprowadza Hexagonal Architecture: **domain records** (Summary, Product, Generation) jako pure data bez wiedzy o XTDB, **porty** (SummaryRepository protocol) definiujące kontrakt persystencji, **adaptery** (XTDBSummaryRepository) implementujące port z ACL functions `domain↔xtdb`. Services layer deleguje do portów (NIE do XTDB API), pages layer injectuje adapter (dependency injection), DTO layer konwertuje domain records (NIE XTDB entities). Po refaktorze: XTDB występuje TYLKO w 3 plikach adapters (z 10 plików przed), swap na PostgreSQL wymaga zmiany TYLKO adapters (~3 nowe pliki) + 3 linie w pages (factory call), **ZERO zmian** w domain/services/dto/ui/tests (~20 plików izolowanych). Plan 8-fazowy (setup → domain → ports → adapters → services → pages → dto → cleanup) zapewnia stopniową migrację z istniejącym test runnerem. Kryterium sukcesu: `grep xtdb.api` zwraca wyłącznie `src/com/apriary/adapters/xtdb/`.
