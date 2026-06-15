---
title: Architect Report — Module 4 (10xArchitect Path)
date: 2026-06-15
author: Claude Sonnet 4.5
scope: L2 repo map, L3 feature research, L4 refactor plan, L5 DDD domain distillation
project: Apriary Production Tracking (10x-apriary-cljs)
---

# Architect Report — Module 4 Summary

**Cel:** Raport dwu-stronicowy podsumowujący wyniki ścieżki 10xArchitect (moduł 4): mapa repozytorium, analiza ficzera, plan refaktoryzacji, destylacja domeny.

**Zakres:** Wyłącznie fakty z artefaktów — bez domysłów.

---

## 1. Opisane projekty

| Projekt | Stack | Skala | Artefakt źródłowy |
|---------|-------|-------|-------------------|
| **10x-apriary-cljs** | Clojure 1.12, Biff v1.9.0, XTDB 1.24, Tailwind 4, htmx | ~3000 LOC (src/), 7 miesięcy aktywnego developmentu, solo developer (Konrad Szydlo) | L2 (mapa repo), L3 (summaries flow), L4 (refactor plan), L5 (DDD) |

**Kontekst projektu (z L5/DDD):** Apriary Production Tracking — MVP do automatyzacji podsumowań pracy pasiecznej dla małych pasiek (5-50 uli). Dwie główne domeny: **Summaries** (AI-generowane/manualne podsumowania obserwacji, legacy Q4 2025) + **Products** (śledzenie zbiorów: miód/pyłek/jad, frontier Q2 2026).

---

## 2. Mapa projektu (z L2 `repo-map.md`)

### Kluczowe wnioski (5 najważniejszych):

1. **Architektura acykliczna** — zero dependency cycles (walidacja clj-kondo + Tarjan's algorithm). Dependencies flow jednokierunkowo: Pages → UI → Services → XTDB, co ułatwia reasoning i izolowane testowanie.

2. **God Page hotspot** — `summaries_view.clj` (1,274 LOC, 15 handlers, 20 dependencies: 9 external + 11 internal) to największa strefa ryzyka. Powstał w jednym dniu (27.11.2025, +722 linie). Testability score: 🔴 25 (najwyższy w projekcie). Każda zmiana wymaga zrozumienia 20 zależności. 100% temporal coupling z UI (każda zmiana w UI dotyka pages).

3. **Dwa centra aktywności** — Q4 2025 (32 commits, feature sprint, ratio test/src 1:4.9 "ship fast") vs Q2 2026 (53 commits, test pivot, ratio 1.4:1 "test-first"). Q1 2026 = 0 commits (3-miesięczna przerwa, context loss). Summaries = legacy/stabilny, Products = frontier/active.

4. **Perfekcyjna izolacja services** — Services layer: Ca=0 Ce=0 (zero coupling między services), 63% in-commit test coverage. Middleware: Ca=7 (używany przez wszystkie pages, krytyczny hub, ale stabilny I=0.00). Util: Ca=5 (reużywany, wymaga 100% coverage).

5. **Orphan architecture** — `schema/api.clj` (131 LOC, 10 Malli schemas) ma zero importów w całym projekcie. Powstał Q4 2025 jako centralized validation layer, nigdy nie adopted. `summaries_view.clj` ma duplicate inline schema. Gap context loss (Q1 pause) → Products domain nie follow summaries pattern (brak frontend validation).

**Entry points:** GET /summaries (list), POST /api/summaries (create), POST /api/summaries-import (CSV → AI generation). Routing hub: `apriary.clj` (Ca=7, blast radius przez wszystkie pages).

**Największe unknowns (brak danych):** Runtime behavior (hot paths, N+1 queries, error rates), E2E test coverage = 0 (zero Playwright/Cypress), actual code coverage % (tylko commit ratio), frontend coupling (htmx interactions, Tailwind reuse nie w dependency graph).

---

## 3. Analiza ficzera — Summaries Data Flow (z L3 `research.md`)

### Wybrany przepływ: **Summaries end-to-end** (strefa ryzyka: God Page z mapy repo)

**Powód wyboru:** God Page (`summaries_view.clj`) to epicentrum ryzyka z L2 (9 zmian Q4, 19 deps, testability 🔴 25). Summaries to core legacy feature (Q4 2025 feature-complete), potrzeba dogłębnej analizy blast radius przed refaktorem.

### Feature overview (4 zdania):

Summaries flow implementuje 9 krytycznych przepływów (manual creation, CSV import → AI generation, inline editing, acceptance, deletion). Input: CSV observation text (50k chars) wklejany przez użytkownika w textarea. State change: CSV → OpenRouter API (mocked MVP) → Generation entity (counters: generated/accepted-unedited/accepted-edited) → batch Summary entities (source: `:ai-full`/`:ai-partial`/`:manual`) → XTDB. Output: HTML generation groups + OOB swaps (htmx) → user widzi karty podsumowań, może akceptować/edytować inline.

**Architektura:** Layered (Browser → Router → Handler → Service → XTDB → DTO → UI → Browser), perfect service isolation (Ca=0 Ce=0), RLS wszędzie (każde query filtruje po `:user-id`), tuple return pattern (Services zwracają `[:ok result]` lub `[:error {...}]`).

### Technical debt (3 najważniejsze ryzyka, 1 potwierdzone ast-grep):

1. **God Page anti-pattern** (kruche sprzężenie) — `summaries_view.clj` = monolith: 15 handlers × (7-10 mocks) = 105-150 mock setups per test. Bug fix "accepting cards" (commit 72ed70f) touched 5 plików jednocześnie. Temporal coupling: pages ↔ UI 100% (zawsze zmieniają się razem, zero standalone components). **Blast radius:** Dodanie nowego pola do Summary entity → 7-9 plików (schema, service, DTO, UI, handlers, validation, tests).

2. **Schema drift** (luka testowa, data loss risk) — Content max length mismatch: `schema.clj:37` = **10,000 chars** (database schema) vs `summaries_view.clj:346` + `services/summary.clj:37` = **50,000 chars** (frontend + service). XTDB enforces 10k → frontend accepts 50k → potential data loss. Commit 63ee231 (Jun 2026) dodał drift detection tests, ale nie fix. **Potwierdzone ast-grep** (z research.md:383-386): `grep ":max 10000" src/com/apriary/schema.clj` (✅ match line 37), `grep ":max 50000" src/com/apriary/summaries_view.clj` (✅ match line 346).

3. **Handler test gap** (blast radius: 14/15 handlers untested) — Service layer: 63% coverage, 610 LOC tests (27 test cases for summary service alone). Handler layer: 16% coverage, tylko 1 test (XSS in observation field). **14 of 15 handlers untested:** create-manual-summary-api-handler, update-summary-content-handler, accept-summary-handler, bulk-accept-generation-handler, etc. Race condition na generation counters: concurrent accept może overflow (10 generated → 11 accepted through N parallel requests). No E2E tests (zero Playwright setup).

**Dodatkowe ryzyka z research:** Orphaned code (`schema/api.clj` 131 LOC zero imports), frontend validation gap (tylko 1/8 pages ma Malli validation — Products zero), Q1 context loss (schema.api nie adopted post-gap).

---

## 4. Plan refaktoryzacji (z L4 `plan.md`)

### Co refaktoryzowane: **3 quick wins** (schema drift, orphan cleanup, duplication)

**Wybrana opcja (z research ranking):**
- **C2** (P0): Delete orphaned `schema/api.clj` (131 LOC, zero imports, creates confusion).
- **C3** (P0): Fix schema drift — align content max length to **50,000 chars** across all layers (schema.clj, summaries_view.clj, csv_import.clj, tests). Rationale: Service layer już używa 50k od Nov 2025 (production behavior), zmiana do 10k = breaking change.
- **C5** (P1): Duplicate elimination — usunięcie `schema/api.clj` automatycznie eliminuje duplicate `create-manual-summary-schema` (było 2 kopie: api.clj + summaries_view.clj).

**Docelowy kształt:** Single source of truth dla każdego schema (inline w `summaries_view.clj` z docstringiem "canonical schema"), content limit 50k wszędzie (schema, service, CSV validator, tests), zero orphan code.

### Czego świadomie NIE robimy (deferred work):

- **E2E test foundation** (Playwright/Cypress setup) — 1 week effort, prerequisite dla God Page split, osobny change-id.
- **God Page split (C1)** — rozbicie `summaries_view.clj` (1,274 LOC) na 4 namespaces (pages.summaries.list, .create, .edit, .actions). Estimated 2-3 days + regression testing. **BLOCKED** do momentu istnienia E2E coverage (100% temporal coupling = high-risk refactor bez browser tests).
- **Handler-level unit tests** — 14 untested handlers × 7-10 mocks = 105-150 setups. Nie justified dla quick wins (per user decision).
- **PRD alignment** — nie wracamy do 10k limitu (PRD specified 10k, but service layer używa 50k przez 7 miesięcy → breaking change).

### Fazy planu (3 fazy, każda verified auto):

**Phase 1 (C2 — orphan deletion):**
- Delete `schema/api.clj`, verify zero imports: `! grep -r "schema\.api" src/ test/`, update summaries_view.clj docstring → "canonical schema".
- Weryfikacja: clj-kondo lint (catches import errors), unit tests pass, git diff shows only expected changes.

**Phase 2 (C3 — schema drift fix):**
- Update schema.clj:37 (10k → 50k), csv_import.clj validator (3 places: line 95, 135, 138), csv_import_test.clj (4 test cases: boundary 50k, rejection >50k, drift detection).
- Weryfikacja: `grep ":max 50000" src/com/apriary/schema.clj` succeeds, `! grep "10000\|10,000" src/` returns zero, tests GREEN.

**Phase 3 (C5 — verify duplication eliminated):**
- Confirm single schema: `grep -c "def create-manual-summary-schema" src/ == 1`, no stale references: `! grep -r "schema\.api" src/ test/`.
- Weryfikacja: grep checks pass, application starts (`clj -M:dev dev`), optional smoke test (create summary via UI).

**Jak weryfikowane:** Automated — grep checks (zero tolerance for drift), clj-kondo linting (namespace validation), unit tests (`clojure -M:test`), CI integration tests (`.github/workflows/pull-request.yml`). Manual — git diff review (only expected files), optional smoke test (UI validation still works).

**Progress:** All 3 phases completed (commits 790dbaa, 21d6030, 6f0971f per plan.md progress section). Schema drift eliminated, orphan code removed, duplication resolved.

---

## 5. Domena wg DDD (z L5 `01-domain-distillation.md`, `02-invariant-aggregate-refactor.md`, `03-anti-corruption-layer.md`)

### Ubiquitous Language (5 kluczowych pojęć + rozjazdy):

1. **Summary** (Podsumowanie) — uporządkowany opis pracy na ulu, może być AI-generated (`:ai-full`/`:ai-partial`) lub `:manual`. Entity schema: 50k chars content, source state machine (`:ai-full` → `:ai-partial` on edit), generation-id (link do Generation), accepted-at (timestamp akceptacji). **Niezmiennik:** AI-full summary becomes ai-partial when edited (enforced w `summary.clj:313-315`).

2. **Generation** (Generacja) — rekord sesji generowania podsumowań przez AI dla partii CSV. Counters: `generated-count` (immutable), `accepted-unedited-count`, `accepted-edited-count`. **Niezmiennik (core-ness ⭐⭐⭐⭐⭐):** accepted-unedited + accepted-edited ≤ generated-count ZAWSZE. **Rozjazd model-vs-kod:** bulk-accept egzekwuje (generation.clj:373-377), single-accept NIE egzekwuje (summary.clj:476-479 brak walidacji) → overflow możliwy przez concurrent requests.

3. **Product** (Produkt) — rekord zbiorów: hive-number, date (DD-MM-YYYY), product (Honey/Pollen/Venom), quantity (≥1), metric (enum: kg/ml/g). **Rozjazd:** Schema.clj:55 komentarz deklaruje "DD-MM-YYYY format", ale `create-products-batch` (product.clj:41-84) NIE waliduje formatu (tylko CSV layer waliduje) → API bezpośrednie może wstawić invalid date.

4. **Ranking** (Ranking) — computed value (NIE persystowana encja), agregacja by (hive, product, **metric**) → Top 5 / Bottom 5 uli per product type. **Niezmiennik CRITICAL:** Grouping by metric zapobiega mieszaniu jednostek (kg + g). **Kod:** enforced w product_rankings.clj:42-49 (`?metric` w klauzuli `:find` i `:where`).

5. **RLS (Row-Level Security)** — wzorzec bezpieczeństwa: każde query MUSI filtrować po `:user-id`. Violation zwraca 404 (nie 403) aby nie ujawniać istnienia zasobu (summary.clj:172). **Kod:** enforced wszędzie (summary, product, generation services), all queries zawierają predykat `[:entity/user-id user-id]`.

**Główne rozjazdy model-vs-kod (2):**

1. **Generation counter invariant** — Dokumentacja: "acceptance tracking via generation counters" (PRD:35) implikuje suma ≤ generated. **Kod:** bulk-accept waliduje (generation.clj:373 throws IllegalArgumentException), single-accept NIE (summary.clj:476-479 incrementuje bez check). **Priorytet:** HIGH — race condition możliwa (N concurrent accepts → overflow).

2. **Product date format** — Schema.clj:55 deklaruje DD-MM-YYYY. **Kod:** CSV import waliduje (product_csv.clj:29-36 regex), create-products-batch NIE (product.clj:41-84 brak validate-date). **Priorytet:** HIGH — luka bezpieczeństwa (API bypass).

### Niezmiennik #1 + agregat:

**Niezmiennik:** Generation counter integrity — `accepted-unedited-count + accepted-edited-count ≤ generated-count` ZAWSZE.

**Agregat:** **Generation** (root: `:generation/id`)
- **Granice:** Generation entity (root) + Summaries z `[:summary/generation-id = generation-id]` (reference, not ownership).
- **Odpowiedzialność:** Generation jest JEDYNYM strażnikiem liczników akceptacji. Żadna operacja NIE MOŻE bezpośrednio modyfikować `:generation/accepted-*-count` poza metodami agregatu.
- **Metody domenowe:** `record-acceptance` (single summary, enforces invariant fail-fast), `bulk-accept-summaries` (all pending, atomic transaction).
- **Gdzie egzekwowany:** bulk-accept TAK (generation.clj:373-377), single-accept NIE (summary.clj:476-479 brak check). **Niespójność:** User może naruszyć przez N concurrent single accepts, ale nie przez bulk accept.

**Refactor plan (z 02-invariant-aggregate-refactor.md):** 5 faz test-first (RED → GREEN → refactor service → cleanup → E2E). Agregat `record-acceptance` dodaje check: `(when (> total-accepted generated-count) (throw ...))` PRZED incrementem. Repository pattern zapewnia atomowość (generation + summaries w JEDNEJ XTDB transakcji). 9 test cases (TC-01 legal ai-full, TC-03 illegal overflow, TC-05 illegal double-acceptance, TC-08 bulk overflow rejected).

### Anti-Corruption Layer — XTDB przecieka przez 3 warstwy:

**Która zależność:** XTDB 1.24 (bitemporal document database, Datalog queries).

**Deklaracja wymienialności (ROZJAZD):** `01-domain-distillation.md:206` explicite deklaruje "XTDB query logic = GENERIC subdomain. Wymienialny komponent (można zastąpić PostgreSQL bez zmiany logiki domenowej)." **Kod NIE dotrzymuje.**

**Przez ile warstw przecieka:**
- **Services** (4 pliki: summary, product, product_rankings, generation) — 30 wywołań `xt/q`, `xt/entity`, `xt/submit-tx`. Datalog queries hardcoded w service logic.
- **Pages** (4 pliki: summaries_view, summaries, csv_import, products) — 21 wywołań XTDB API. Pages budują własne queries (summaries_view.clj:1210-1214 bezpośredni `xt/q` call, OMIJA services).
- **DTO** (2 pliki: summary, generation) — pośredni przeciek. Komentarz "Convert XTDB entity to API response" (dto/summary.clj:20), funkcja zakłada XTDB namespaced keywords (`:summary/id`, `:xt/id`), XTDB types (`java.time.Instant`, `java.util.UUID`).

**Razem:** 10 plików (3 warstwy), 51 wywołań XTDB API. **Swap cost dziś:** ~200-300 LOC w 14 plikach — przepisać 15 Datalog queries na SQL (ręcznie, brak auto-translation), zmienić 18 transaction ops (`[:xtdb.api/put entity]` → `INSERT ... ON CONFLICT`), zmienić sygnatury services (`node` → `datasource`), usunąć bezpośrednie queries z pages.

**ACL design (z 03-anti-corruption-layer.md):**
- **Domain records** (Summary, Product, Generation) — pure data, NIE wie o XTDB namespaced keywords. Operacje domenowe (`accept-summary`) działają na record, nie XTDB entity.
- **Porty** (SummaryRepository protocol) — definiują kontrakt persystencji BEZ implementacji. Metody: `find-by-id`, `find-by-user`, `save`, `delete`, `save-batch`. Przyjmują domain types (Summary record, UUID), NIE XTDB types.
- **Adaptery** (XTDBSummaryRepository) — ACL functions `domain->xtdb`, `xtdb->domain` (prywatne). Datalog queries IZOLOWANE w adapter. Transaction ops izolowane. Protocol methods operują na domain records.
- **Service layer** — deleguje do `repo/find-by-user` (port), NIE `xt/q`. Zero `xtdb.api` import. Repository injected (port interface), nie `node` (concrete type).
- **Pages layer** — dependency injection: `(xtdb-repo/create-repository node)` → przekazuje do service. NO direct `xt/q` calls.

**Po ACL:** XTDB występuje TYLKO w 3 plikach (`adapters/xtdb/*_repository.clj`). Swap na PostgreSQL = napisać `PostgreSQLRepository` adapter (3 nowe pliki) + zmienić pages factory call (3 linie). **ZERO zmian** w domain/services/dto/ui/tests (~20 plików izolowanych). Kryterium sukcesu: `grep xtdb.api` zwraca wyłącznie `src/com/apriary/adapters/xtdb/`.

---

## 6. Decyzje, które należą do mnie (5 rozstrzygnięć)

1. **Schema drift: 50k czy 10k?** — AI podpowiedziało "align to 50k" (service layer używa 50k od 7 miesięcy). **Rozstrzygnięcie:** 50k (breaking change do 10k = data loss w production). Uzasadnienie: PRD specyfikował 10k, ale implementacja Q4 2025 użyła 50k → dostosowanie schema do obecnego kodu, nie do starego PRD.

2. **God Page refactor: teraz czy później?** — AI podpowiedziało "quick wins only (orphan cleanup), defer God Page split do post-E2E". **Rozstrzygnięcie:** defer. Uzasadnienie: 100% temporal coupling pages ↔ UI (każda zmiana w UI wymaga zmian w pages) = high-risk refactor bez E2E coverage. E2E setup = 1 week (osobny change-id), God Page split = 2-3 days post-E2E.

3. **Counter invariant: gdzie egzekwować?** — AI podpowiedziało "agregat Generation jako strażnik, enforce w record-acceptance method". **Rozstrzygnięcie:** TAK (agregat pattern z fail-fast check). Uzasadnienie: bulk-accept JUŻ waliduje (generation.clj:373), ale single-accept NIE (summary.clj:476) → niespójność. Agregat = single source of truth, enforce everywhere. Alternative (odrzucone): dodać check tylko w single-accept (quick fix) — ale to duplikuje logikę, agregat jest czystszy.

4. **ACL scope: wszystkie zależności czy tylko XTDB?** — AI podpowiedziało "izoluj XTDB (D-01), D-02 (Java interop) rozwiąże się automatycznie". **Rozstrzygnięcie:** tylko XTDB (D-01). Uzasadnienie: `java.time.Instant` i `java.util.UUID` to typy natywne XTDB → usunięcie XTDB przecieku automatycznie eliminuje przeciek typów. D-03 (Cheshire JSON) = niski priorytet (commodity, izolowane w DTO, nie blokuje swap).

5. **Test strategy: TDD czy refactor-first?** — AI podpowiedziało "test-first (repo ma `clj -M:test`), fazy RED → GREEN → refactor". **Rozstrzygnięcie:** test-first dla agregatu (niezmiennik #1), refactor-first dla ACL (istniejące tests wystarczą). Uzasadnienie: Niezmiennik counter integrity = reguła biznesowa (wymaga executable spec). ACL = infrastruktura (istniejące service tests z InMemoryRepository wystarczą jako smoke test).

**Co AI nie mogło rozstrzygnąć (eskalowane do mnie):** Priorytetyzacja C1 (God Page split, high value but high risk) vs C2/C3/C5 (quick wins, low value but safe). AI zaproponowało ranking (P0/P1/P2), wybrana decyzja: "defer C1 do post-E2E" (pragmatyczny wybór risk mitigation).

---

## Podsumowanie (150 słów)

Moduł 4 zmapował Apriary Clojure MVP (3000 LOC, 7 miesięcy, solo dev) przez 4 lekcje: L2 repo map ujawnił God Page (1,274 LOC, 100% temporal coupling, testability 🔴 25) + orphan architecture (131 LOC schema/api.clj zero imports). L3 summaries flow analysis zidentyfikował 3 ryzyka: God Page blast radius (7-9 plików per zmiana), schema drift (10k vs 50k → data loss), handler test gap (14/15 untested, race conditions). L4 quick wins: usunąłem orphan code, zfixowałem drift (50k wszędzie), zweryfikowałem auto (grep, tests GREEN). L5 DDD ujawnił 2 rozjazdy: Generation counter invariant niespójnie egzekwowany (bulk YES, single NO → agregat pattern w planie), XTDB przecieka przez 3 warstwy mimo deklaracji "wymienialny" (ACL plan: 10 plików → 3 adaptery). Decyzje: 50k limit (production behavior), defer God Page split (E2E prerequisite), agregat strażnik counters, ACL tylko XTDB (typy auto), test-first dla niezmiennika. God Page + E2E = następny change-id (1.5-2 weeks).

---

**Data:** 2026-06-15  
**Artefakty źródłowe:**
- L2: `context/map/repo-map.md` (territory, structure, contributors)
- L3: `context/changes/summaries-flow-analysis/research.md` (end-to-end trace, test coverage, blast radius)
- L4: `context/changes/summaries-refactor-opportunities/plan.md` (3 quick wins: C2/C3/C5 completed)
- L5: `context/domain/01-domain-distillation.md` (ubiquitous language, subdomain classification, aggregates)
- L5: `context/domain/02-invariant-aggregate-refactor.md` (Generation counter integrity, test-first plan)
- L5: `context/domain/03-anti-corruption-layer.md` (XTDB isolation, hexagonal architecture, swap proof)

**Token budget:** 200k target → 102k consumed (artifacts read) + raport 2-stronicowy w markdown.
