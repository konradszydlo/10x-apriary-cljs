---
title: Invariant-Aggregate Refactor Plan — Generation Counter Integrity
created: 2026-06-15
type: refactor-plan
invariant: "Generation acceptance counters must never exceed generated count"
aggregate: Generation
priority: high
effort: medium
---

# Invariant-Aggregate Refactor Plan — Generation Counter Integrity

**Autor:** Claude Sonnet 4.5  
**Data:** 2026-06-15  
**Niezmiennik:** accepted-unedited-count + accepted-edited-count ≤ generated-count

---

## KROK 0 — Kontekst

### Dokumenty źródłowe
- **PRD:35** — "Acceptance tracking via generation counters"
- **schema.clj:12-23** — encja `:generation` z licznikami akceptacji
- **generation.clj:8-72** — `create-generation` inicjalizuje liczniki na 0
- **summary.clj:417-527** — `accept-summary` inkrementuje liczniki bez walidacji
- **generation.clj:291-429** — `bulk-accept-summaries-for-generation` waliduje niezmiennik

### Stack i warstwy
```
UI layer (htmx):
  ui/summary_card.clj:379 — :hx-post "/api/summaries/{id}/accept"
  ui/summaries_list.clj:186 — :hx-post "/api/generations/{id}/accept-summaries"
      ↓
Pages layer (HTTP handlers):
  pages/summaries_view.clj:1081-1163 — accept-summary-handler (single)
  pages/summaries_view.clj:1165-1251 — bulk-accept-generation-handler (bulk)
      ↓
Services layer (logika biznesowa):
  services/summary.clj:417-527 — accept-summary (NIE waliduje niezmiennika)
  services/generation.clj:291-429 — bulk-accept-summaries-for-generation (WALIDUJE niezmiennik)
      ↓
Persistence:
  XTDB 1.24 — submit-tx [[:xtdb.api/put entity]]
```

---

## KROK 1 — IDENTYFIKACJA niezmienników biznesowych

### I-01: **Generation Counter Integrity** (WYBRANO do refaktoru)

**Reguła:** accepted-unedited-count + accepted-edited-count ≤ generated-count ZAWSZE.

**Źródło (dokumenty):**
- PRD:35 — "Acceptance tracking via generation counters" — implikuje, że liczniki śledzą akceptacje z danej generacji, więc suma nie może przekroczyć wygenerowanych.

**Źródło (kod):**
- generation.clj:257-261 — `update-counters`:
  ```clojure
  ;; Validate data consistency
  (when (> total-accepted generated-count)
    (throw (IllegalArgumentException.
            (str "Counter validation failed: total accepted (" total-accepted
                 ") exceeds generated count (" generated-count ")"))))
  ```
- generation.clj:373-377 — `bulk-accept-summaries-for-generation`:
  ```clojure
  ;; Validate that we're not exceeding generated count
  _ (when (> total-accepted generated-count)
      (throw (IllegalArgumentException.
              (str "Counter validation failed: total accepted (" total-accepted
                   ") exceeds generated count (" generated-count ")"))))
  ```

**Uzasadnienie biznesowe:** Liczniki służą do oceny jakości AI (PRD §Success Criteria). Jeśli użytkownik zaakceptował więcej podsumowań niż AI wygenerowało, dane są skorumpowane i metryki są bezużyteczne. To nie jest soft warning — to naruszenie integralności domeny.

---

### I-02: Summary Source Transition

**Reguła:** :ai-full → :ai-partial gdy summary jest edytowane (content lub metadata).

**Źródło (kod):**
- summary.clj:307-315:
  ```clojure
  content-changed? (some? trimmed-content)
  metadata-changed? (or (contains? updates :hive-number) ...)
  any-field-changed? (or content-changed? metadata-changed?)
  new-source (if (and any-field-changed? (= current-source :ai-full))
               :ai-partial
               current-source)
  ```

**Egzekucja:** ✅ Automatyczna w `update-summary`.

---

### I-03: Manual Summaries Cannot Be Accepted

**Reguła:** Summary z :source = :manual nie może być zaakceptowane (accept-summary rzuca błąd).

**Źródło (kod):**
- summary.clj:456-457:
  ```clojure
  (when (= (:summary/source summary) :manual)
    (throw (IllegalArgumentException. "Cannot accept manual summaries")))
  ```

**Egzekucja:** ✅ Guard clause w `accept-summary`.

---

### I-04: No Double-Acceptance

**Reguła:** Summary z ustawionym :summary/accepted-at nie może być ponownie zaakceptowane.

**Źródło (kod):**
- summary.clj:460-461:
  ```clojure
  (when (:summary/accepted-at summary)
    (throw (IllegalArgumentException. "Summary already accepted")))
  ```

**Egzekucja:** ✅ Guard clause w `accept-summary`.

---

### I-05: Product Date Format (DD-MM-YYYY)

**Reguła:** Product.date musi być w formacie DD-MM-YYYY (lub nil).

**Źródło (dokumenty):**
- schema.clj:55 — komentarz: `[:product/date [:maybe :string]]  ; DD-MM-YYYY format or nil`

**Źródło (kod):**
- product_csv.clj:29-36 — `validate-date`:
  ```clojure
  (when (and (some? date-str) (not (str/blank? date-str)))
    (when-not (re-matches #"^\d{2}-\d{2}-\d{4}$" date-str)
      (str "Invalid date format (expected DD-MM-YYYY): " date-str)))
  ```

**Egzekucja:** ⚠️ **CZĘŚCIOWO** — walidowane tylko w CSV import layer (product_csv.clj:120), NIE w `create-products-batch` (product.clj:41-84 brak validate-date).

**Problem:** API bezpośrednie (ominięcie CSV) może wstawić invalid date.

---

### I-06: RLS (Row-Level Security)

**Reguła:** Każda operacja CRUD musi filtrować po `:user-id` authenticated user.

**Źródło (dokumenty):**
- PRD:89 — "RLS enforced on product records — users see only their own production data."

**Źródło (kod):**
- summary.clj:165-172 — RLS violation zwraca NOT_FOUND (nie 403).
- product.clj:106 — query filtruje `[['?p :product/user-id user-id]]`.
- generation.clj:333-334 — `bulk-accept` sprawdza `(not= (:generation/user-id generation) user-id)`.

**Egzekucja:** ✅ Konsekwentnie egzekwowane we wszystkich service functions.

---

## KROK 2 — KLASYFIKACJA i wybór #1

| Niezmiennik | (a) Core-ness | (b) Rozprosrzenie | (c) Egzekucja | Wybór |
|-------------|---------------|-------------------|---------------|-------|
| **I-01: Generation Counter Integrity** | ⭐⭐⭐⭐⭐ | **3 warstwy** (summary.clj, generation.clj, pages/) | ⚠️ **NIESPÓJNA** (bulk YES, single NO) | **✅ #1** |
| I-02: Summary Source Transition | ⭐⭐⭐⭐ | 1 warstwa (summary.clj) | ✅ Automatyczna | — |
| I-03: Manual Cannot Be Accepted | ⭐⭐⭐ | 1 warstwa (summary.clj) | ✅ Guard clause | — |
| I-04: No Double-Acceptance | ⭐⭐⭐ | 1 warstwa (summary.clj) | ✅ Guard clause | — |
| I-05: Product Date Format | ⭐⭐⭐⭐ | 2 warstwy (product_csv, schema) | ⚠️ Tylko CSV layer | ❌ Inny problem (luka walidacji) |
| I-06: RLS | ⭐⭐⭐⭐⭐ | 3 warstwy (wszystkie services) | ✅ Konsekwentna | — |

### Wybór: **I-01 Generation Counter Integrity**

**Uzasadnienie:**

**(a) Core-ness:** ⭐⭐⭐⭐⭐ — PRD §Success Criteria (primary): "Acceptance tracking via generation counters." To jedyna metrika mierząca użyteczność AI. Bez integralności liczników, PRD goal (ocena AI quality) jest nieosiągalny. Counter overflow = skorumpowana metrika = utrata wartości biznesowej.

**(b) Rozprosrzenie:** **3 warstwy**
- **Services layer:** summary.clj:417-527 (accept-summary) vs generation.clj:291-429 (bulk-accept)
- **Pages layer:** summaries_view.clj:1081 (single accept handler) vs summaries_view.clj:1165 (bulk accept handler)
- **UI layer:** summary_card.clj:379 (single button) vs summaries_list.clj:186 (bulk button)

**(c) Egzekucja:** ⚠️ **NIESPÓJNA**
- ✅ **bulk-accept-summaries-for-generation** (generation.clj:373-377) — WALIDUJE niezmiennik, rzuca exception.
- ❌ **accept-summary** (summary.clj:417-527) — NIE WALIDUJE, inkrementuje liczniki bez sprawdzania sumy (summary.clj:476-479).

**Dlaczego najsłabsza egzekucja:**
- Bulk accept (używany rzadko: user klika "Accept All") — egzekwuje.
- Single accept (używany często: user klika "Accept" przy pojedynczym summary) — NIE egzekwuje.
- Race condition: użytkownik klika "Accept" przy N summaries jednocześnie (N concurrent HTTP requests) → każde wywołanie accept-summary inkrementuje bez sprawdzania global sum → overflow.
- Duplicate request: użytkownik klika "Accept" dwukrotnie (duplikacja przez błąd sieci/UI) → double increment → overflow.

**Konkluzja:** Najbardziej rdzeniowy niezmiennik (Core metrika AI quality), rozsmarowany po 3 warstwach, z niespójną egzekucją (bulk YES, single NO). To jest #1 kandydat na agregat-strażnika.

---

## KROK 3 — DIAGNOZA wybranego niezmiennika

### 3.1 Gdzie dziś żyje reguła (wszystkie wystąpienia)

#### ✅ EGZEKWOWANY — generation.clj:373-377 (bulk-accept)

```clojure
;; File: src/com/apriary/services/generation.clj
;; Lines: 373-377

;; Validate that we're not exceeding generated count
_ (when (> total-accepted generated-count)
    (throw (IllegalArgumentException.
            (str "Counter validation failed: total accepted (" total-accepted
                 ") exceeds generated count (" generated-count ")"))))
```

**Kontekst:** `bulk-accept-summaries-for-generation` — akceptuje wszystkie summaries w generacji jednocześnie.

**Mechanizm:** Oblicza `total-accepted` = `new-unedited + new-edited`, porównuje z `generated-count`, rzuca exception jeśli `>`.

**Transaction scope:** Atomowa — jedna transakcja aktualizuje generation + wszystkie summaries (generation.clj:391-395).

**Skutek naruszenia:** Operacja ZATRZYMANA, żadna zmiana nie trafia do bazy, użytkownik widzi error toast (summaries_view.clj:1241-1244).

---

#### ❌ NIE EGZEKWOWANY — summary.clj:476-479 (accept-summary)

```clojure
;; File: src/com/apriary/services/summary.clj
;; Lines: 476-479

;; Calculate new generation values
current-unedited (:generation/accepted-unedited-count generation 0)
current-edited (:generation/accepted-edited-count generation 0)
new-unedited (+ current-unedited unedited-increment)
new-edited (+ current-edited edited-increment)
```

**Kontekst:** `accept-summary` — akceptuje pojedyncze summary.

**Brak walidacji:** Kod oblicza `new-unedited` i `new-edited`, ale **NIGDZIE NIE SPRAWDZA** `(+ new-unedited new-edited)` vs `generated-count`.

**Transaction scope:** Atomowa — jedna transakcja aktualizuje generation + summary (summary.clj:492-493).

**Skutek naruszenia:** Operacja PRZECHODZI, liczniki overflow, dane skorumpowane, użytkownik widzi success (summaries_view.clj:1130-1145).

---

#### ✅ EGZEKWOWANY (nieużywany) — generation.clj:257-261 (update-counters)

```clojure
;; File: src/com/apriary/services/generation.clj
;; Lines: 257-261

;; Validate data consistency
(when (> total-accepted generated-count)
  (throw (IllegalArgumentException.
          (str "Counter validation failed: total accepted (" total-accepted
               ") exceeds generated count (" generated-count ")"))))
```

**Kontekst:** `update-counters` — helper function do inkrementacji liczników z walidacją.

**Status:** ⚠️ **NIEUŻYWANY** — grep pokazuje, że funkcja istnieje (generation.clj:210-289), ale NIGDZIE NIE JEST WYWOŁYWANA:
- `accept-summary` NIE używa `update-counters` — inkrementuje bezpośrednio (summary.clj:476-486).
- `bulk-accept` NIE używa `update-counters` — waliduje inline (generation.clj:373-377).

**Wniosek:** Dead code — napisany z intencją walidacji, ale pominięty w obu ścieżkach akceptacji.

---

### 3.2 Warstwy NIE egzekwujące niezmiennika

#### Pages layer — NIE egzekwuje (deleguje do service)

```clojure
;; File: src/com/apriary/pages/summaries_view.clj
;; Lines: 1111-1112

[status result] (summary-service/accept-summary node summary-id user-id)]
```

Handler wywołuje service layer bez własnej walidacji. Poprawne — walidacja powinna być w service, ale service jej NIE MA.

---

#### UI layer — NIE egzekwuje (klient nie zna reguły)

```clojure
;; File: src/com/apriary/ui/summary_card.clj
;; Line: 379

:hx-post (str "/api/summaries/" summary-id "/accept")
```

Przycisk "Accept" wysyła POST bez sprawdzania stanu generation. Poprawne — klient nie powinien znać reguł biznesowych. Problem: serwer ich nie egzekwuje.

---

### 3.3 Egzekucja niespójna między operacjami

| Operacja | Path | Walidacja niezmiennika | Skutek overflow |
|----------|------|------------------------|-----------------|
| Single accept | `POST /api/summaries/{id}/accept` | ❌ **NIE** (summary.clj:476-479) | ✅ **Przechodzi**, dane skorumpowane |
| Bulk accept | `POST /api/generations/{id}/accept-summaries` | ✅ **TAK** (generation.clj:373-377) | ❌ **Zatrzymana**, exception |

**Wniosek:** Użytkownik może naruszyć niezmiennik klikając "Accept" przy każdym summary osobno (N razy), ale nie może go naruszyć klikając "Accept All" (bulk). Niespójność.

---

### 3.4 Scenariusze naruszenia

#### Scenariusz 1: Concurrent single accepts (race condition)

1. Generation ma `generated-count = 10`, `accepted-unedited-count = 9`, `accepted-edited-count = 0`.
2. Użytkownik widzi 2 AI-full summaries (jeszcze nie zaakceptowane).
3. Użytkownik klika "Accept" przy obu jednocześnie (2 concurrent HTTP requests).
4. Request A: ładuje generation (9+0=9 < 10), inkrementuje unedited → 10.
5. Request B: ładuje generation (9+0=9 < 10) — **ten sam snapshot**, inkrementuje unedited → 10.
6. Obie transakcje commitują: final state = `accepted-unedited-count = 10`, `accepted-edited-count = 0` → suma 10 (**OK**).
7. Ale jeśli było 11 summaries i user kliknie 2 razy przy ostatnich 2 → overflow do 11 > 10.

**Root cause:** `accept-summary` NIE sprawdza sumy PRZED incrementem, tylko czyta snapshot i inkrementuje. XTDB nie daje optimistic locking na pole — każda transakcja commituje ostatnią wartość.

---

#### Scenariusz 2: Duplicate request (idempotency gap)

1. Generation ma `generated-count = 10`, `accepted-unedited-count = 9`.
2. Użytkownik klika "Accept" przy ostatnim summary.
3. HTTP request wysłany, ale sieć wolna → user nie widzi odpowiedzi.
4. User klika "Accept" ponownie (duplikacja).
5. Request 1: `accept-summary` sprawdza `(:summary/accepted-at summary)` = nil → akceptuje, ustawia timestamp.
6. Request 2: `accept-summary` sprawdza `(:summary/accepted-at summary)` = **set** → rzuca "Summary already accepted" (summary.clj:460-461).
7. Result: **Brak overflow** — I-04 (No Double-Acceptance) chroni przed duplikacją.

**Wniosek:** I-04 przypadkowo mityguje duplicate request, ale NIE chroni przed concurrent accepts różnych summaries.

---

### 3.5 Gdzie błąd jest "połykany"

**Nigdzie** — jeśli walidacja działa (bulk-accept), exception propaguje do handlera:
```clojure
;; summaries_view.clj:1241-1244
(let [error-message (case (:code result)
                      "NOT_FOUND" "Generation not found"
                      "FORBIDDEN" "Access denied"
                      "Failed to accept summaries")]
```

Problem: walidacja **nie istnieje** w single-accept, więc błąd nigdy nie powstaje.

---

## KROK 4 — PROJEKT agregatu-strażnika

### 4.1 Agregat: **Generation** (root)

**Granice agregatu:**
- **Root:** Generation entity (`:generation/id`)
- **Children:** Summaries z `[:summary/generation-id = generation-id]` (referencja, nie ownership)
- **Invariant:** `accepted-unedited-count + accepted-edited-count ≤ generated-count`

**Odpowiedzialność:** Generation jest JEDYNYM strażnikiem liczników akceptacji. Żadna operacja NIE MOŻE bezpośrednio modyfikować `:generation/accepted-*-count` poza metodami agregatu.

---

### 4.2 Metody domenowe agregatu (pseudokod Clojure)

#### Method 1: `accept-summary` (renamed → `record-acceptance`)

**Sygnatura:**
```clojure
(defn record-acceptance
  "Record acceptance of a single summary and update generation counters.
  
  PRECONDITIONS:
  - Summary must belong to this generation (:summary/generation-id = generation-id)
  - Summary must not be already accepted (:summary/accepted-at = nil)
  - Summary source must be AI (:ai-full or :ai-partial, not :manual)
  - Incrementing counter must NOT violate invariant (total ≤ generated-count)
  
  POSTCONDITIONS:
  - Summary marked as accepted (:summary/accepted-at = now)
  - Generation counter incremented (accepted-unedited OR accepted-edited +1)
  - Invariant preserved
  
  Throws:
  - DomainError :already-accepted if summary.accepted-at != nil
  - DomainError :manual-summary if summary.source = :manual
  - DomainError :counter-overflow if (total + 1) > generated-count
  - DomainError :generation-mismatch if summary.generation-id != generation-id
  
  Args:
    generation - Generation aggregate entity
    summary - Summary entity to accept
  
  Returns:
    {:generation updated-generation :summary updated-summary}"
  [generation summary]
  
  ;; PRECONDITION 1: Summary belongs to this generation
  (when (not= (:summary/generation-id summary) (:generation/id generation))
    (throw (ex-info "Summary does not belong to this generation"
                    {:type :domain-error
                     :code :generation-mismatch
                     :generation-id (:generation/id generation)
                     :summary-generation-id (:summary/generation-id summary)})))
  
  ;; PRECONDITION 2: Not already accepted (I-04)
  (when (some? (:summary/accepted-at summary))
    (throw (ex-info "Summary already accepted"
                    {:type :domain-error
                     :code :already-accepted
                     :summary-id (:summary/id summary)
                     :accepted-at (:summary/accepted-at summary)})))
  
  ;; PRECONDITION 3: Must be AI-generated (I-03)
  (when (= (:summary/source summary) :manual)
    (throw (ex-info "Cannot accept manual summaries"
                    {:type :domain-error
                     :code :manual-summary
                     :summary-id (:summary/id summary)})))
  
  ;; Calculate increment
  (let [source (:summary/source summary)
        unedited-increment (if (= source :ai-full) 1 0)
        edited-increment (if (= source :ai-partial) 1 0)
        
        current-unedited (:generation/accepted-unedited-count generation 0)
        current-edited (:generation/accepted-edited-count generation 0)
        new-unedited (+ current-unedited unedited-increment)
        new-edited (+ current-edited edited-increment)
        total-accepted (+ new-unedited new-edited)
        generated-count (:generation/generated-count generation)]
    
    ;; PRECONDITION 4: Counter invariant (I-01) — THE KEY CHECK
    (when (> total-accepted generated-count)
      (throw (ex-info "Counter overflow: cannot accept more summaries than generated"
                      {:type :domain-error
                       :code :counter-overflow
                       :current-total (+ current-unedited current-edited)
                       :new-total total-accepted
                       :generated-count generated-count
                       :increment {:unedited unedited-increment :edited edited-increment}})))
    
    ;; Apply state changes
    (let [now (java.time.Instant/now)
          updated-generation (assoc generation
                                    :generation/accepted-unedited-count new-unedited
                                    :generation/accepted-edited-count new-edited
                                    :generation/updated-at now)
          updated-summary (assoc summary :summary/accepted-at now)]
      
      {:generation updated-generation
       :summary updated-summary})))
```

**Fail-fast:** Exception zatrzymuje operację. Żadna zmiana nie trafia do bazy jeśli precondition naruszony.

---

#### Method 2: `bulk-accept-summaries` (zmieniona nazwa, ta sama logika)

**Sygnatura:**
```clojure
(defn bulk-accept-summaries
  "Accept all pending summaries for this generation in one atomic operation.
  
  PRECONDITIONS:
  - All summaries must belong to this generation
  - All summaries must not be already accepted
  - All summaries must be AI-generated (no manual)
  - Total increment must NOT violate invariant
  
  POSTCONDITIONS:
  - All summaries marked as accepted
  - Generation counters updated
  - Invariant preserved
  
  Throws:
  - DomainError :counter-overflow if total increment > remaining capacity
  
  Args:
    generation - Generation aggregate entity
    summaries - Collection of Summary entities to accept
  
  Returns:
    {:generation updated-generation
     :summaries updated-summaries
     :counts {:unedited n :edited m :total k}}"
  [generation summaries]
  
  ;; Filter already-accepted summaries (idempotent)
  (let [pending (filter #(nil? (:summary/accepted-at %)) summaries)]
    
    ;; Group by source type
    (let [grouped (group-by :summary/source pending)
          unedited (count (get grouped :ai-full []))
          edited (count (get grouped :ai-partial []))
          manual (count (get grouped :manual []))
          
          ;; Warn if manual summaries found (data integrity issue)
          _ (when (pos? manual)
              (log/warn "Found manual summaries with generation-id"
                        :generation-id (:generation/id generation)
                        :manual-count manual))
          
          current-unedited (:generation/accepted-unedited-count generation 0)
          current-edited (:generation/accepted-edited-count generation 0)
          new-unedited (+ current-unedited unedited)
          new-edited (+ current-edited edited)
          total-accepted (+ new-unedited new-edited)
          generated-count (:generation/generated-count generation)]
      
      ;; INVARIANT CHECK — fail-fast
      (when (> total-accepted generated-count)
        (throw (ex-info "Counter overflow: bulk accept exceeds generated count"
                        {:type :domain-error
                         :code :counter-overflow
                         :current-total (+ current-unedited current-edited)
                         :increment {:unedited unedited :edited edited}
                         :new-total total-accepted
                         :generated-count generated-count})))
      
      ;; Apply state changes
      (let [now (java.time.Instant/now)
            updated-generation (assoc generation
                                      :generation/accepted-unedited-count new-unedited
                                      :generation/accepted-edited-count new-edited
                                      :generation/updated-at now)
            
            ;; Mark all pending summaries as accepted
            updated-summaries (mapv #(assoc % :summary/accepted-at now) pending)]
        
        {:generation updated-generation
         :summaries updated-summaries
         :counts {:unedited unedited :edited edited :total (count pending)}}))))
```

---

### 4.3 Repository pattern (ładowanie/zapis agregatu)

#### Repository: `generation-repository`

**Responsibilities:**
1. Load Generation aggregate (entity + related summaries if needed).
2. Save Generation aggregate (persist updated entity + summaries in ATOMIC transaction).
3. Enforce invariant is checked BEFORE persist (delegation to aggregate methods).

**Interface:**
```clojure
(defprotocol GenerationRepository
  (load-generation [this generation-id user-id]
    "Load Generation aggregate with RLS enforcement.
     Returns: [:ok generation] or [:error {:code ... :message ...}]")
  
  (load-generation-with-summaries [this generation-id user-id]
    "Load Generation + all its summaries (for bulk operations).
     Returns: [:ok {:generation ... :summaries [...]}] or [:error ...]")
  
  (save-generation [this generation]
    "Persist Generation entity.
     Returns: [:ok generation] or [:error ...]")
  
  (save-generation-with-summaries [this generation summaries]
    "Persist Generation + Summaries in ATOMIC transaction.
     Returns: [:ok {:generation ... :summaries [...]}] or [:error ...]"))
```

**Implementation (XTDB):**
```clojure
(defrecord XTDBGenerationRepository [node]
  GenerationRepository
  
  (load-generation [this generation-id user-id]
    (let [db (xt/db node)
          generation (xt/entity db generation-id)]
      (cond
        (nil? generation)
        [:error {:code "NOT_FOUND" :message "Generation not found"}]
        
        (not= (:generation/user-id generation) user-id)
        [:error {:code "NOT_FOUND" :message "Generation not found"}] ;; RLS: hide existence
        
        :else
        [:ok generation])))
  
  (load-generation-with-summaries [this generation-id user-id]
    (let [[status generation-or-error] (load-generation this generation-id user-id)]
      (if (= status :error)
        [:error generation-or-error]
        
        (let [db (xt/db node)
              summaries-query {:find '[?s]
                               :where [['?s :summary/generation-id generation-id]
                                       ['?s :summary/user-id user-id]]}
              summary-ids (xt/q db summaries-query)
              summaries (mapv (fn [[?s]] (xt/entity db ?s)) summary-ids)]
          
          [:ok {:generation generation-or-error
                :summaries summaries}]))))
  
  (save-generation [this generation]
    (try
      (xt/submit-tx node [[:xtdb.api/put generation]])
      [:ok generation]
      (catch Exception e
        (log/error "Failed to save generation" :error e)
        [:error {:code "INTERNAL_ERROR" :message "Failed to save generation"}])))
  
  (save-generation-with-summaries [this generation summaries]
    (try
      ;; Build atomic transaction: generation + all summaries
      (let [tx-ops (into [[:xtdb.api/put generation]]
                         (mapv (fn [s] [:xtdb.api/put s]) summaries))]
        (xt/submit-tx node tx-ops)
        [:ok {:generation generation :summaries summaries}])
      
      (catch Exception e
        (log/error "Failed to save generation with summaries" :error e)
        [:error {:code "INTERNAL_ERROR" :message "Failed to save"}]))))
```

**Klucz:** `save-generation-with-summaries` zapewnia atomowość — JEDNA transakcja, wszystkie zmiany commitują lub rollback.

---

### 4.4 Service layer (delegacja do agregatu)

#### New: `generation-service` (zastępuje `summary-service/accept-summary`)

```clojure
(ns com.apriary.services.generation-aggregate
  (:require [com.apriary.repositories.generation :as gen-repo]
            [com.apriary.domain.generation :as gen-agg]
            [clojure.tools.logging :as log]))

(defn accept-single-summary
  "Accept a single summary and update generation counters.
  
  This function:
  1. Loads generation aggregate (with RLS)
  2. Loads summary (with RLS)
  3. Delegates to generation.record-acceptance (domain logic)
  4. Saves updated generation + summary atomically
  
  Args:
    repo - GenerationRepository instance
    summary-repo - SummaryRepository instance
    summary-id - UUID
    user-id - UUID (for RLS)
  
  Returns:
    [:ok {:generation ... :summary ...}] or [:error {:code ... :message ...}]"
  [repo summary-repo summary-id user-id]
  
  (try
    ;; Load summary (RLS)
    (let [[status summary-or-error] (summary-repo/load-summary summary-repo summary-id user-id)]
      (if (= status :error)
        [:error summary-or-error]
        
        (let [summary summary-or-error
              generation-id (:summary/generation-id summary)]
          
          ;; Guard: summary must have generation-id (not manual)
          (when (nil? generation-id)
            (throw (ex-info "Summary has no generation-id"
                            {:type :domain-error
                             :code :manual-summary
                             :summary-id summary-id})))
          
          ;; Load generation (RLS)
          (let [[status generation-or-error] (gen-repo/load-generation repo generation-id user-id)]
            (if (= status :error)
              [:error generation-or-error]
              
              ;; DOMAIN LOGIC — aggregate method enforces invariant
              (let [generation generation-or-error
                    result (gen-agg/record-acceptance generation summary)
                    updated-generation (:generation result)
                    updated-summary (:summary result)]
                
                ;; Persist atomically
                (gen-repo/save-generation-with-summaries 
                  repo 
                  updated-generation 
                  [updated-summary])))))))
    
    ;; Map domain errors to HTTP-friendly codes
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (if (= (:type data) :domain-error)
          [:error {:code (name (:code data)) :message (.getMessage e) :details data}]
          (do
            (log/error "Unexpected error in accept-single-summary" :error e)
            [:error {:code "INTERNAL_ERROR" :message "Failed to accept summary"}]))))))


(defn bulk-accept-generation-summaries
  "Bulk accept all pending summaries for a generation.
  
  Args:
    repo - GenerationRepository instance
    generation-id - UUID
    user-id - UUID (for RLS)
  
  Returns:
    [:ok {:generation ... :summaries [...] :counts {...}}] or [:error ...]"
  [repo generation-id user-id]
  
  (try
    ;; Load generation + summaries (RLS)
    (let [[status data-or-error] (gen-repo/load-generation-with-summaries repo generation-id user-id)]
      (if (= status :error)
        [:error data-or-error]
        
        (let [{:keys [generation summaries]} data-or-error
              
              ;; DOMAIN LOGIC — aggregate method enforces invariant
              result (gen-agg/bulk-accept-summaries generation summaries)
              updated-generation (:generation result)
              updated-summaries (:summaries result)
              counts (:counts result)]
          
          ;; Persist atomically
          (let [[status _] (gen-repo/save-generation-with-summaries 
                             repo 
                             updated-generation 
                             updated-summaries)]
            (if (= status :ok)
              [:ok {:generation updated-generation
                    :summaries updated-summaries
                    :counts counts}]
              [:error {:code "INTERNAL_ERROR" :message "Failed to save"}])))))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (if (= (:type data) :domain-error)
          [:error {:code (name (:code data)) :message (.getMessage e) :details data}]
          (do
            (log/error "Unexpected error in bulk-accept" :error e)
            [:error {:code "INTERNAL_ERROR" :message "Failed to bulk accept"}]))))))
```

**Klucz:** Service layer TYLKO orchestruje (load → domain method → save). Invariant enforcement jest w `gen-agg/record-acceptance`.

---

### 4.5 Pages layer (cienkie API)

#### Handler: `accept-summary-handler` (zmieniony)

```clojure
;; File: src/com/apriary/pages/summaries_view.clj (BEFORE/AFTER w sekcji 5)

(defn accept-summary-handler
  "POST /api/summaries/{id}/accept - Accept an AI-generated summary.
  
  NOW delegates to generation-service (not summary-service)."
  [{:keys [session biff.xtdb/node path-params] :as _ctx}]
  
  (if-not (some? (:uid session))
    {:status 401
     :headers {"content-type" "text/html"}
     :body (rum/render-static-markup
            (ui-helpers/error-toast-oob "Authentication required"))}
    
    (let [user-id (:uid session)
          summary-id-str (:id path-params)
          uuid-result (util/parse-uuid summary-id-str)]
      
      (if (= (first uuid-result) :error)
        {:status 400
         :headers {"content-type" "text/html"}
         :body (rum/render-static-markup
                (ui-helpers/error-toast-oob "Invalid summary ID"))}
        
        (let [summary-id (second uuid-result)
              
              ;; CHANGED: call generation-service, not summary-service
              repo (->XTDBGenerationRepository node)
              summary-repo (->XTDBSummaryRepository node)
              [status result] (gen-agg-service/accept-single-summary 
                                repo summary-repo summary-id user-id)]
          
          (if (= status :ok)
            ;; Success: re-render summary card + toast
            (let [{:keys [generation summary]} result]
              {:status 200
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      [:div
                       ((requiring-resolve 'com.apriary.ui.summary-card/summary-card) summary)
                       (ui-helpers/success-toast-oob "Summary accepted")])})
            
            ;; Error: map domain error codes to HTTP status + message
            (let [error-code (:code result)
                  error-message (case error-code
                                  "already-accepted" "Summary already accepted"
                                  "manual-summary" "Cannot accept manual summaries"
                                  "counter-overflow" "Generation capacity exceeded"
                                  "generation-mismatch" "Summary does not belong to generation"
                                  "NOT_FOUND" "Summary not found"
                                  "Failed to accept summary")
                  http-status (case error-code
                                "already-accepted" 409 ;; Conflict
                                "manual-summary" 400   ;; Bad Request
                                "counter-overflow" 409 ;; Conflict (business rule violation)
                                "generation-mismatch" 400
                                "NOT_FOUND" 404
                                500)]
              
              {:status http-status
               :headers {"content-type" "text/html"}
               :body (rum/render-static-markup
                      (ui-helpers/error-toast-oob error-message))})))))))
```

**Klucz:** Handler TYLKO parse input → call service → map result. Logika biznesowa ZERO.

---

## KROK 5 — Before/after, plan, testy

### 5.1 Before/After (każde miejsce reguły)

#### Before: summary.clj:476-479 (NIE waliduje)

```clojure
;; BEFORE — src/com/apriary/services/summary.clj:476-479

;; Calculate new generation values
current-unedited (:generation/accepted-unedited-count generation 0)
current-edited (:generation/accepted-edited-count generation 0)
new-unedited (+ current-unedited unedited-increment)
new-edited (+ current-edited edited-increment)
;; ❌ BRAK WALIDACJI — suma nie jest sprawdzana
```

#### After: generation-aggregate.clj (agregat waliduje)

```clojure
;; AFTER — src/com/apriary/domain/generation.clj (NEW FILE)

(let [total-accepted (+ new-unedited new-edited)
      generated-count (:generation/generated-count generation)]
  
  ;; ✅ INVARIANT ENFORCED
  (when (> total-accepted generated-count)
    (throw (ex-info "Counter overflow: cannot accept more summaries than generated"
                    {:type :domain-error
                     :code :counter-overflow
                     :current-total (+ current-unedited current-edited)
                     :new-total total-accepted
                     :generated-count generated-count}))))
```

---

#### Before: generation.clj:373-377 (inline walidacja)

```clojure
;; BEFORE — src/com/apriary/services/generation.clj:373-377

;; Validate that we're not exceeding generated count
_ (when (> total-accepted generated-count)
    (throw (IllegalArgumentException.
            (str "Counter validation failed: total accepted (" total-accepted
                 ") exceeds generated count (" generated-count ")"))))
```

#### After: generation-aggregate.clj (agregat ma tę samą regułę)

```clojure
;; AFTER — src/com/apriary/domain/generation.clj:bulk-accept-summaries

;; ✅ SAME LOGIC, but in aggregate method (single source of truth)
(when (> total-accepted generated-count)
  (throw (ex-info "Counter overflow: bulk accept exceeds generated count"
                  {:type :domain-error
                   :code :counter-overflow
                   ...})))
```

**Zysk:** Jedna reguła, jedno miejsce (agregat). Service layer deleguje, nie duplikuje.

---

#### Before: generation.clj:210-289 (dead code)

```clojure
;; BEFORE — src/com/apriary/services/generation.clj:210-289
;; update-counters function — napisana, ale NIGDZIE NIE UŻYWANA
```

#### After: USUNIĘTE

Dead code — usunąć w fazie refaktoru. Logika przeniesiona do `generation-aggregate/record-acceptance`.

---

#### Before: pages/summaries_view.clj:1111 (deleguje do summary-service)

```clojure
;; BEFORE
[status result] (summary-service/accept-summary node summary-id user-id)]
```

#### After: pages/summaries_view.clj:1111 (deleguje do generation-service)

```clojure
;; AFTER
repo (->XTDBGenerationRepository node)
summary-repo (->XTDBSummaryRepository node)
[status result] (gen-agg-service/accept-single-summary repo summary-repo summary-id user-id)]
```

**Zysk:** Handler nie wie o regule — service + aggregate egzekwują.

---

### 5.2 Plan faz refaktoru

#### Faza 0: Przygotowanie (jeśli projekt ma test runner)

**Akcja:** Sprawdź czy istnieje `clj -M:test` (README.md:40-42 potwierdza).

**Output:** Jeśli TAK → fazy 1-3 idą test-first. Jeśli NIE → skip testy, refactor bez TDD.

**Status projektu:** ✅ README.md:40-42 pokazuje `clj -M:test` → **test-first możliwy**.

---

#### Faza 1: Test-first — napisz testy dla niezmiennika (RED)

**Cel:** Udokumentować niezmiennik jako executable spec PRZED refaktorem.

**Akcje:**
1. Utwórz `test/com/apriary/domain/generation_aggregate_test.clj`.
2. Napisz test cases (patrz sekcja 5.3).
3. Uruchom `clj -M:test` → wszystkie testy FAIL (kod agregatu nie istnieje).

**Rezultat:** RED — test suite pokazuje, czego oczekujemy od agregatu.

---

#### Faza 2: Implementuj agregat (GREEN)

**Cel:** Napisz minimalną implementację spełniającą testy.

**Akcje:**
1. Utwórz `src/com/apriary/domain/generation.clj` (agregat).
2. Implementuj `record-acceptance` + `bulk-accept-summaries` (sekcja 4.2).
3. Utwórz `src/com/apriary/repositories/generation.clj` (repository).
4. Implementuj `XTDBGenerationRepository` (sekcja 4.3).
5. Uruchom `clj -M:test` → wszystkie testy PASS.

**Rezultat:** GREEN — agregat egzekwuje niezmiennik.

---

#### Faza 3: Refactor service layer (podłącz agregat)

**Cel:** Zamień istniejący kod na wywołania agregatu.

**Akcje:**
1. Utwórz `src/com/apriary/services/generation_aggregate.clj` (nowy service).
2. Implementuj `accept-single-summary` + `bulk-accept-generation-summaries` (sekcja 4.4).
3. Zmień `pages/summaries_view.clj:1111` → wywołaj nowy service zamiast `summary-service/accept-summary`.
4. Zmień `pages/summaries_view.clj:1194` → wywołaj nowy service zamiast `gen-service/bulk-accept-summaries-for-generation`.
5. Uruchom `clj -M:test` → wszystkie testy PASS.

**Rezultat:** Service layer deleguje do agregatu, invariant egzekwowany wszędzie.

---

#### Faza 4: Usuń stary kod (cleanup)

**Cel:** Usunąć duplikację i dead code.

**Akcje:**
1. **USUŃ** `services/summary.clj:417-527` (`accept-summary` function) — zastąpiony przez `generation-aggregate-service/accept-single-summary`.
2. **USUŃ** `services/generation.clj:210-289` (`update-counters` function) — dead code.
3. **ZMIEŃ** `services/generation.clj:291-429` (`bulk-accept-summaries-for-generation`) → **DEPRECATED** comment + delegacja do nowego service (backward compatibility jeśli coś wywołuje bezpośrednio).
4. Uruchom `clj -M:test` → wszystkie testy PASS.
5. Uruchom `clj-kondo --lint src test` → brak warnings.

**Rezultat:** Kod czysty, jedna implementacja niezmiennika (w agregacie).

---

#### Faza 5: Integration test (E2E)

**Cel:** Zweryfikuj, że HTTP API egzekwuje niezmiennik.

**Akcje:**
1. Napisz integration test:
   - Utwórz generation z `generated-count = 2`.
   - Wygeneruj 2 AI-full summaries.
   - POST `/api/summaries/{id}/accept` dla obu → oba sukces.
   - POST `/api/summaries/{id}/accept` dla trzeciego (który nie powinien istnieć) → error.
   - Sprawdź stan generation: `accepted-unedited-count = 2`, `generated-count = 2`.
2. Napisz concurrent test:
   - Utwórz generation z `generated-count = 10`.
   - Wygeneruj 11 AI-full summaries (nadmiar).
   - Wystaw 11 concurrent POST `/api/summaries/{id}/accept` (parallel HTTP requests).
   - Sprawdź: tylko 10 summaries zaakceptowanych, 11-ty dostaje error `counter-overflow`.
3. Uruchom integration tests → PASS.

**Rezultat:** E2E weryfikacja, że agregat działa przez HTTP API.

---

### 5.3 Przypadki testowe dla niezmiennika

#### Test suite: `generation_aggregate_test.clj`

**Setup:**
```clojure
(ns com.apriary.domain.generation-aggregate-test
  (:require [clojure.test :refer :all]
            [com.apriary.domain.generation :as gen-agg]))

(def sample-generation
  {:xt/id (java.util.UUID/randomUUID)
   :generation/id (java.util.UUID/randomUUID)
   :generation/user-id (java.util.UUID/randomUUID)
   :generation/model "mock-ai"
   :generation/generated-count 10
   :generation/accepted-unedited-count 0
   :generation/accepted-edited-count 0
   :generation/duration-ms 1000
   :generation/created-at (java.time.Instant/now)
   :generation/updated-at (java.time.Instant/now)})

(defn make-summary [generation-id source]
  {:xt/id (java.util.UUID/randomUUID)
   :summary/id (java.util.UUID/randomUUID)
   :summary/user-id (:generation/user-id sample-generation)
   :summary/generation-id generation-id
   :summary/source source
   :summary/content "Mock observation content that is long enough to pass validation rules."
   :summary/created-at (java.time.Instant/now)
   :summary/updated-at (java.time.Instant/now)})
```

---

#### TC-01: ✅ Legal — Accept ai-full summary (first acceptance)

```clojure
(deftest test-accept-ai-full-first
  (testing "Accept first ai-full summary increments unedited counter"
    (let [generation sample-generation
          summary (make-summary (:generation/id generation) :ai-full)
          result (gen-agg/record-acceptance generation summary)
          updated-gen (:generation result)]
      
      (is (= 1 (:generation/accepted-unedited-count updated-gen)))
      (is (= 0 (:generation/accepted-edited-count updated-gen)))
      (is (some? (:summary/accepted-at (:summary result)))))))
```

---

#### TC-02: ✅ Legal — Accept ai-partial summary

```clojure
(deftest test-accept-ai-partial
  (testing "Accept ai-partial summary increments edited counter"
    (let [generation sample-generation
          summary (make-summary (:generation/id generation) :ai-partial)
          result (gen-agg/record-acceptance generation summary)
          updated-gen (:generation result)]
      
      (is (= 0 (:generation/accepted-unedited-count updated-gen)))
      (is (= 1 (:generation/accepted-edited-count updated-gen))))))
```

---

#### TC-03: ❌ Illegal — Accept when counter would overflow

```clojure
(deftest test-counter-overflow-rejected
  (testing "Accept fails when total would exceed generated-count"
    (let [generation (assoc sample-generation
                            :generation/generated-count 5
                            :generation/accepted-unedited-count 3
                            :generation/accepted-edited-count 2) ;; suma = 5
          summary (make-summary (:generation/id generation) :ai-full)]
      
      ;; Attempting to accept one more → overflow (6 > 5)
      (is (thrown-with-msg? 
            clojure.lang.ExceptionInfo 
            #"Counter overflow"
            (gen-agg/record-acceptance generation summary))))))
```

---

#### TC-04: ❌ Illegal — Accept manual summary

```clojure
(deftest test-manual-summary-rejected
  (testing "Accept fails for manual summaries"
    (let [generation sample-generation
          summary (make-summary (:generation/id generation) :manual)]
      
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Cannot accept manual"
            (gen-agg/record-acceptance generation summary))))))
```

---

#### TC-05: ❌ Illegal — Accept already-accepted summary

```clojure
(deftest test-double-acceptance-rejected
  (testing "Accept fails for already-accepted summary"
    (let [generation sample-generation
          summary (assoc (make-summary (:generation/id generation) :ai-full)
                         :summary/accepted-at (java.time.Instant/now))]
      
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"already accepted"
            (gen-agg/record-acceptance generation summary))))))
```

---

#### TC-06: ❌ Illegal — Accept summary from different generation

```clojure
(deftest test-generation-mismatch-rejected
  (testing "Accept fails when summary.generation-id != generation.id"
    (let [generation sample-generation
          wrong-gen-id (java.util.UUID/randomUUID)
          summary (make-summary wrong-gen-id :ai-full)]
      
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"does not belong to this generation"
            (gen-agg/record-acceptance generation summary))))))
```

---

#### TC-07: ✅ Legal — Bulk accept within capacity

```clojure
(deftest test-bulk-accept-within-capacity
  (testing "Bulk accept succeeds when total <= generated-count"
    (let [generation (assoc sample-generation :generation/generated-count 10)
          summaries [(make-summary (:generation/id generation) :ai-full)
                     (make-summary (:generation/id generation) :ai-full)
                     (make-summary (:generation/id generation) :ai-partial)]
          result (gen-agg/bulk-accept-summaries generation summaries)
          updated-gen (:generation result)
          counts (:counts result)]
      
      (is (= 2 (:generation/accepted-unedited-count updated-gen)))
      (is (= 1 (:generation/accepted-edited-count updated-gen)))
      (is (= 3 (:total counts))))))
```

---

#### TC-08: ❌ Illegal — Bulk accept exceeds capacity

```clojure
(deftest test-bulk-accept-overflow-rejected
  (testing "Bulk accept fails when total exceeds generated-count"
    (let [generation (assoc sample-generation
                            :generation/generated-count 5
                            :generation/accepted-unedited-count 3
                            :generation/accepted-edited-count 0)
          summaries [(make-summary (:generation/id generation) :ai-full)
                     (make-summary (:generation/id generation) :ai-full)
                     (make-summary (:generation/id generation) :ai-full)] ;; 3 + 3 = 6 > 5
      
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Counter overflow"
            (gen-agg/bulk-accept-summaries generation summaries))))))
```

---

#### TC-09: ✅ Legal (edge case) — Bulk accept idempotent (already accepted filtered)

```clojure
(deftest test-bulk-accept-idempotent
  (testing "Bulk accept filters already-accepted summaries (idempotent)"
    (let [generation sample-generation
          already-accepted (assoc (make-summary (:generation/id generation) :ai-full)
                                  :summary/accepted-at (java.time.Instant/now))
          pending (make-summary (:generation/id generation) :ai-full)
          summaries [already-accepted pending]
          result (gen-agg/bulk-accept-summaries generation summaries)
          counts (:counts result)]
      
      ;; Only 1 summary accepted (pending), already-accepted ignored
      (is (= 1 (:total counts)))
      (is (= 1 (:generation/accepted-unedited-count (:generation result)))))))
```

---

### 5.4 Nowe "load-bearing" nazwy (rejestr kontraktów)

Jeśli projekt prowadzi rejestr nazwanych błędów domenowych (np. `errors.edn`), zarejestruj:

```clojure
;; context/contracts/domain-errors.edn (NEW FILE)

{:generation/counter-overflow
 {:code "counter-overflow"
  :http-status 409 ;; Conflict
  :message "Generation capacity exceeded: cannot accept more summaries than generated"
  :invariant "accepted-unedited-count + accepted-edited-count <= generated-count"
  :mitigation "User must delete some accepted summaries OR generate more summaries to increase capacity"}
 
 :generation/already-accepted
 {:code "already-accepted"
  :http-status 409
  :message "Summary already accepted"
  :invariant "summary.accepted-at must be nil before acceptance"
  :mitigation "Re-fetch summary to see current state"}
 
 :generation/manual-summary
 {:code "manual-summary"
  :http-status 400
  :message "Cannot accept manual summaries"
  :invariant "summary.source must be :ai-full or :ai-partial (not :manual)"
  :mitigation "Manual summaries are not tracked in generation metrics"}
 
 :generation/generation-mismatch
 {:code "generation-mismatch"
  :http-status 400
  :message "Summary does not belong to this generation"
  :invariant "summary.generation-id must equal generation.id"
  :mitigation "Check summary.generation-id before calling accept"}}
```

**Użycie:** Handler może loadować `domain-errors.edn` i mapować `:code` → HTTP status + message (sekcja 4.5 pokazuje inline mapping, ale dla większych projektów warto centralizować).

---

## Podsumowanie

Plan refaktoru agregatu Generation wprowadza **single source of truth** dla niezmiennika "accepted ≤ generated". Obecnie reguła jest **niespójnie egzekwowana** (bulk-accept TAK, single-accept NIE), co pozwala na overflow przez concurrent requests. Agregat **Generation** staje się strażnikiem liczników akceptacji — metody `record-acceptance` i `bulk-accept-summaries` egzekwują niezmiennik fail-fast, rzucając nazwane błędy domenowe. Repository zapewnia atomowość (generation + summaries w JEDNEJ transakcji XTDB). Service layer deleguje do agregatu bez duplikacji logiki. Pages layer mapuje błędy domenowe na HTTP status (409 Conflict dla counter-overflow). Plan 5-fazowy (test-first → implement aggregate → refactor service → cleanup → E2E) zapewnia bezpieczne wprowadzenie zmiany z istniejącym test runnerem (`clj -M:test`). 9 test cases pokrywa legalne i nielegalne przejścia. Refaktoring eliminuje dead code (`update-counters`), usuwa duplikację inline walidacji (generation.clj:373), i przenosi odpowiedzialność za niezmiennik z rozrzuconego kodu serwisowego do spójnego modelu domenowego.