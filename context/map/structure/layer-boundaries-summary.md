# Analiza Granic Warstw — Quick Summary

## 🚨 Top 5 Obserwacji

1. **schema.api jest MARTWY KOD** — 132 linie Malli schemas, zero importów w całym projekcie
2. **summaries-view duplikuje schema.api** — identyczny `create-manual-summary-schema` w dwóch miejscach
3. **products/rankings mają ZERO frontend validation** — Q2 2026 frontier bez Malli guards
4. **schema.clj i schema.api żyją osobno** — entity schemas (db) vs API schemas (validation) nie są zsynchronizowane
5. **Tylko summaries używa Malli** — reszta aplikacji polega wyłącznie na backend validation

## Szczegółowa Tabela

| Sprawdzana granica | Wynik | Dowód z dependency analysis | Dlaczego to ważne przy zmianie | Związek z artifact-1-territory.md | Co sprawdzić dalej |
|-------------------|-------|----------------------------|-------------------------------|----------------------------------|-------------------|
| **schema.api → summaries-view** | ❌ **ZŁAMANA** — duplicate schema zamiast import | `pages/summaries_view.clj:334` definiuje `create-manual-summary-schema` **identyczny** do `schema.api:26`<br><br>`grep -r "schema.api"` → zero importów w całym projekcie | **Duplication = drift risk:** Zmiana validation rules (np. content 50-50k → 50-100k) wymaga update w **2 miejscach**. Łatwo przegapić jeden → desynchronizacja frontend/API contract.<br><br>**Single point of failure:** Jeśli schema.api się outdatuje (bo nikt go nie używa) → może nie odzwierciedlać reality. | "Summaries hotspot (9 zmian)" / "Q4 2025 feature-first (1:4.9)" / "Ship fast, validate later"<br><br>Q4 był **move fast mode** — schema.api prawdopodobnie created jako TODO "use later" ale nigdy zintegrowany. Summaries rozwijany równolegle z inline schemas. | 1. Porównaj schema.api vs summaries_view schema byte-by-byte<br>2. Sprawdź czy są in sync (prawdopodobnie tak bo duplicate)<br>3. **P0 action:** Refactor summaries_view → import schema.api |
| **schema.api → rankings** | ❌ **NIE ISTNIEJE** — zero frontend validation | `pages/rankings.clj` → zero Malli imports<br><br>Dependency analysis: rankings → middleware, services, ui (brak validation layer)<br><br>Tylko 1 commit w całej historii (cold zone) | **Poor UX risk:** Brak client-side validation = errors tylko post-submit, nie real-time feedback.<br><br>**Backend-only guard:** Jeśli backend service ma validation bug → frontend przepuści invalid data bez warning.<br><br>**MVP shortcuts:** Rankings to nowy feature (Q2) ale shipped bez quality guards. | "Rankings Q2 2026 (1 zmiana)" / "Cold zone — minimal activity"<br><br>Rankings to **quick MVP** — prawdopodobnie rushed feature bez time dla frontend validation. Low priority bo low activity. | 1. **Check rankings handlers:** czy ma POST/PUT endpoints? (może read-only?)<br>2. User flow audit — czy rankings ma forms/inputs?<br>3. **P4 priority** (low activity = low urgency) |
| **schema.api → products** | ❌ **NIE ISTNIEJE** — zero frontend validation w active domain | `pages/products.clj` → zero Malli imports<br><br>Products ma 8 dependencies (middleware, 3x services, ui) ale **zero validation**<br><br>4 commits w Q2 2026 = active development | **Q2 frontier risk:** Products to **najaktywniejszy obszar** w Q2 (4 zmiany) ALE zero frontend guards. More features = more forms = more validation needed.<br><br>**Quality regression:** Summaries (Q4 legacy) ma validation, products (Q2 frontier) nie ma — backwards step w discipline.<br><br>**User pain:** Products forms (CSV import? manual input?) mają poor UX bez real-time validation. | "Products Q2 hot zone (4 zmiany)" / "Products pivot (0 w Q4 → 4 w Q2)" / "Active development"<br><br>Q2 był **test-first pivot** (1.4:1) ALE focus był na **backend tests** (services 63% coverage), nie frontend validation. Products shipped jako MVP. | 1. **Products handlers inventory:** ile POST/PUT endpoints?<br>2. Czy products ma forms? (likely — CSV import mentioned)<br>3. **P1 priority** — active + user-facing = urgent validation need |
| **schema.clj ↔ schema.api** | ❌ **DISCONNECT** — parallel universes, zero sync | `schema.clj` — XTDB entity schemas (`:summary`, `:product`), imported by `apriary.clj:12`<br><br>`schema.api` — API request/response schemas, **zero imports anywhere**<br><br>No shared types between them | **Desync disaster:** Entity schema (db) może drift from API schema (validation). Example: db `summary/content` changes 50-10k → 50-100k (schema.clj) but API schema not updated → API rejects valid data.<br><br>**Double maintenance:** Every data model change = update **2 schemas** manually. No single source of truth. | "schema.clj 5 zmian (warm zone)" / "Schema changes isolated"<br><br>Schema.clj **ewoluuje** (5 zmian) ale schema.api **nie** (orphan). Drift is inevitable unless manual sync discipline (risky). | 1. **Drift audit:** Compare `:summary` (schema.clj:26-37) vs `summary-dto-schema` (schema.api:71-86)<br>2. Are content lengths in sync? (db: 50-10k, API: 50-50k?)<br>3. **P2:** Design sync strategy (schema.api derive from schema.clj?) |
| **DTOs → schema.api** | ❌ **MISSING** — DTOs nie validują | `dto/summary.clj` dependency: tylko `util` (Ca=2, Ce=1)<br><br>Zero Malli imports w dto.summary<br><br>Summaries-view używa dto.summary **bez validation checkpoint** | **Service bugs propagate:** Jeśli service zwraca malformed data (bug, db corruption) → DTO passes through bez validate → może crash rendering.<br><br>**Missed opportunity:** schema.api defines `summary-dto-schema` (lines 71-86) ALE dto.summary go nie używa. Planned defensive layer nigdy nie implemented. | "dto.summary używany przez 2 pages" / "Medium instability (I=0.33)"<br><br>DTOs są **transform layer** (services → pages) ale bez validation = "hope services are correct" approach. | 1. **DTO code review:** co robi dto.summary? (pure transform or has logic?)<br>2. Czy DTO potrzebuje defensive validation?<br>3. **P3:** Add schema.api validation checkpoint w DTOs |
| **UI components → Malli** | ✅ **BRAK (correct)** — UI pure presentation | UI layer analysis: `ui.summary-card` → tylko clojure.string<br><br>`ui.summaries-list` → tylko ui.summary-card<br><br>**Zero** Malli imports w całej warstwie `ui/*` | ✅ **Correct separation:** UI components są dumb rendering layer. Validation belongs w pages (handlers) nie UI.<br><br>**Good architecture:** Pages validate user input → pass clean data → UI renders. Clean boundary. | "UI 100% coupling z pages" / "UI = page-specific, nie reusable"<br><br>UI coupling jest **presentational** nie **logical** — UI renders what pages give it, zero business logic. This is **good design**. | ✅ **No action** — UI correctly separated from validation |
| **Services → schema.api** | ❌ **MISSING** — services nie używają central schemas | Services dependency: wszystkie `Ca=0 Ce=0` (perfect isolation)<br><br>Services używają XTDB (db) ale nie importują `schema.clj` ani `schema.api`<br><br>Validation is service-internal, not shared | **Duplication risk:** Każdy service może mieć własną validation logic (inline) zamiast shared schemas → consistency risk.<br><br>**API contract drift:** Services mogą return data które nie match schema.api contracts (bo services don't validate against it). | "Services perfect isolation" / "Safest to modify" / "Best test discipline (63%)"<br><br>Services **izolacja** jest good dla testing ALE **brak shared validation** może be bad dla API contract consistency. | 1. **Services validation audit:** jak validują? (inline? malli? custom?)<br>2. Czy services używają schema.clj (XTDB)?<br>3. **P3:** Services validate output against schema.api DTOs? |

## Wzorce Łamania Granic

### Pattern 1: Orphan Foundation (schema.api)

**Symptom:** 132 linie dobrze zaprojektowanych schemas, zero usage

**Root cause (z territory.md):**
- Q4 2025 = "ship fast" mode (1:4.9 test ratio)
- Schema.api created jako **planned foundation** ("we'll need this")
- Summaries-view developed **równolegle** z inline schemas (szybciej)
- Nigdy nie było refactor time ("use schema.api instead of inline")

**Impact:**
```
Planned architecture ≠ Actual architecture
```

---

### Pattern 2: Frontend Validation Regression

**Q4 2025 (summaries):** ✅ Malli validation (inline, ale istnieje)  
**Q2 2026 (products/rankings):** ❌ Zero frontend validation

**Root cause (z territory.md):**
- Q2 = test-first pivot (1.4:1 ratio) ALE focus na **backend tests**
- Frontend validation **not prioritized** w Q2
- Products/rankings shipped jako MVPs bez frontend guards

**Impact:** Quality regression — legacy ma validation, frontier nie ma

---

### Pattern 3: DTO Layer Brak Defensive Programming

**Obecny flow:**
```
services → dto.summary → pages
         (no validation checkpoint)
```

**Gdzie validation happens:**
- ✅ Pages: validate user input (forms)
- ❌ DTOs: **nie** validate service output
- ❓ Services: validate internally (nie przez shared schemas)

**Impact:** Service bugs propagate to UI bez early catch

---

## Priority Actions

| Priorytet | Action | Impact | Effort | Reason |
|-----------|--------|--------|--------|--------|
| **P0** | Consolidate summaries_view → schema.api | HIGH | MEDIUM | Hotspot (9 zmian) + eliminate duplication |
| **P1** | Add frontend validation dla products | HIGH | MEDIUM | Active domain (4 zmiany) + UX improvement |
| **P2** | Sync schema.clj ↔ schema.api | MEDIUM | LOW | Prevent entity/API drift |
| **P3** | DTO defensive validation | MEDIUM | LOW | Catch service bugs early |
| **P4** | Add validation dla rankings | LOW | LOW | Cold zone (1 zmiana) = low urgency |

## Metrics

- **schema.api adoption:** 0% (0/8 pages)
- **Frontend validation coverage:** 12.5% (1/8 pages — tylko summaries-view)
- **Known duplications:** 1 (create-manual-summary-schema)
- **Orphan code:** 132 lines (całość schema.api)

## Wniosek

**schema.api to "ghost architecture"** — zaprojektowany jako fundament ale nigdy nie adopted.

**Frontend validation jest fragmentary** (tylko summaries) i **duplicated** (inline zamiast central).

**Recommendation:**
1. **P0:** Consolidate summaries → import schema.api (eliminate dup w hotspot)
2. **P1:** Extend validation do products (active domain needs guards)
3. **P2:** Sync schema.clj ↔ schema.api (prevent drift)
4. **P3:** DTOs validate service output (defensive programming)

---

**Pełna analiza:** `context/map/layer-boundaries-analysis.md`  
**Status:** ✅ Zakończona  
**Date:** 2026-06-09
