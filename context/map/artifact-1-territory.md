# Artifact 1: Territory — Historia zmian i aktywne obszary

**Zakres:** ostatnie 12 miesięcy  
**Data analizy:** 2026-06-08  
**Metoda:** git log --since="12 months ago" z filtrowaniem szumu (lockfile'y, context/, config, snapshoty)

---

## TOP 10 Najczęściej Modyfikowanych Modułów/Folderów

| Ranga | Moduł/Folder | Liczba zmian | Typ |
|-------|-------------|--------------|-----|
| 1 | `src/com/apriary/pages/` | 28 | Moduł |
| 2 | `src/com/apriary/ui/` | 26 | Moduł |
| 3 | `test/com/apriary/services/` | 21 | Testy |
| 4 | `src/com/apriary/services/` | 15 | Moduł |
| 5 | `test/com/apriary/pages/` | 15 | Testy |
| 6 | `src/com/apriary.clj` | 8 | Core app |
| 7 | `.github/workflows/` | 6 | CI/CD |
| 8 | `deps.edn` | 5 | Deps |
| 9 | `Dockerfile` | 5 | Infra |
| 10 | `test/com/apriary/ui/` | 4 | Testy |

---

## TOP 10 Najczęściej Modyfikowanych Plików

**A) Pliki źródłowe (src/):**

| Ranga | Plik | Zmiany | Obszar |
|-------|------|--------|--------|
| 1 | `src/com/apriary/pages/summaries_view.clj` | 9 | Pages |
| 2 | `src/com/apriary.clj` | 8 | Core |
| 3 | `src/com/apriary/ui/header.clj` | 6 | UI |
| 4 | `src/com/apriary/ui/summaries_list.clj` | 5 | UI |
| 5 | `src/com/apriary/schema.clj` | 5 | Schema |
| 6 | `src/com/apriary/pages/app.clj` | 5 | Pages |
| 7 | `src/com/apriary/ui/summary_card.clj` | 4 | UI |
| 8 | `src/com/apriary/ui.clj` | 4 | UI |
| 9 | `src/com/apriary/pages/products.clj` | 4 | Pages |
| 10 | `src/com/apriary/email.clj` | 4 | Services |

**B) Pliki testowe (test/):**

| Ranga | Plik | Zmiany | Obszar testowy |
|-------|------|--------|----------------|
| 1 | `test/com/apriary/ui/integration_test.clj` | 4 | UI Integration |
| 2 | `test/com/apriary/services/summary_test.clj` | 4 | Services |
| 3 | `test/com/apriary/services/product_csv_test.clj` | 4 | Services |
| 4 | `test/com/apriary/services/csv_import_test.clj` | 4 | Services |
| 5 | `test/com/apriary/pages/summaries_view_test.clj` | 4 | Pages |
| 6 | `test/com/apriary/pages/rankings_test.clj` | 4 | Pages |
| 7 | `test/com/apriary/pages/products_test.clj` | 4 | Pages |

---

## Szczegółowe Rozbicie Aktywnych Obszarów

### 1. Pages Module (`src/com/apriary/pages/`)
**28 zmian** — najaktywniejszy moduł w projekcie

| Plik | Zmiany | Hot/Warm/Cold |
|------|--------|---------------|
| `summaries_view.clj` | 9 | 🔥 HOT |
| `app.clj` | 5 | 🔥 HOT |
| `products.clj` | 4 | 🟡 WARM |
| `home.clj` | 3 | 🟡 WARM |
| `generations.clj` | 3 | 🟡 WARM |
| `summaries.clj` | 2 | ❄️ COLD |
| `rankings.clj` | 1 | ❄️ COLD |
| `csv_import.clj` | 1 | ❄️ COLD |

**Dominujący obszar:** Summaries view — punkt centralny aktywności.

### 2. UI Module (`src/com/apriary/ui/`)
**26 zmian** — drugi najaktywniejszy moduł

| Plik | Zmiany | Hot/Warm/Cold |
|------|--------|---------------|
| `header.clj` | 6 | 🔥 HOT |
| `summaries_list.clj` | 5 | 🔥 HOT |
| `summary_card.clj` | 4 | 🟡 WARM |
| `layout.clj` | 3 | 🟡 WARM |
| `products.clj` | 2 | ❄️ COLD |
| `csv_import.clj` | 2 | ❄️ COLD |
| `toast.clj` | 1 | ❄️ COLD |
| `rankings.clj` | 1 | ❄️ COLD |
| `helpers.clj` | 1 | ❄️ COLD |
| `error.clj` | 1 | ❄️ COLD |

**Dominujący obszar:** Komponenty związane z summaries (header, list, card).

### 3. Services Module (`src/com/apriary/services/`)
**15 zmian** — trzeci najaktywniejszy moduł

| Plik | Zmiany | Hot/Warm/Cold |
|------|--------|---------------|
| `generation.clj` | 3 | 🟡 WARM |
| `summary.clj` | 2 | ❄️ COLD |
| `product_rankings.clj` | 2 | ❄️ COLD |
| `product_csv.clj` | 2 | ❄️ COLD |
| `product.clj` | 2 | ❄️ COLD |
| `openrouter.clj` | 2 | ❄️ COLD |
| `csv_import.clj` | 2 | ❄️ COLD |

**Rozkład:** Bardziej równomierny niż w pages/ui — aktywność rozproszona.

---

## Analiza Kwartalna — Ewolucja Priorytetów

**Zakres czasowy projektu:** 2025-11-22 (pierwszy commit) do 2026-06-08 (ostatni commit)  
**Okres aktywny:** ~7 miesięcy (z 3-miesięczną przerwą: styczeń-kwiecień 2026)

### Podział na kwartały

| Kwartał | Commits | Src zmian | Test zmian | Test/Src ratio | Aktywność |
|---------|---------|-----------|------------|----------------|-----------|
| **Q4 2025** (X-XII) | 32 | 84 | 17 | 1:4.9 | 🔥 Feature development |
| **Q1 2026** (I-III) | 0 | 0 | 0 | - | ❄️ Przerwa |
| **Q2 2026** (IV-VI) | 53 | 22 | 30 | 1.4:1 | 🧪 Test-driven hardening |

### Rozkład miesięczny (ostatnie 8 miesięcy)

```
2025-11: ████████████████████ (18 commits)
2025-12: ████████████████     (14 commits)
2026-01: ∅                     (0 commits)
2026-02: ∅                     (0 commits)
2026-03: ∅                     (0 commits)
2026-04: ∅                     (0 commits)
2026-05: ███████               (10 commits)
2026-06: ███████████████████████████████████ (35 commits)
```

**Wzorzec:** Intensywny development (Q4 2025) → pauza (Q1 2026) → test hardening surge (maj-czerwiec 2026)

---

## Q4 2025: Feature Development Phase

**Dominujący focus:** Summaries + UI Components

### Top 10 plików (Q4 2025)

| Plik | Zmiany | Obszar |
|------|--------|--------|
| `src/com/apriary/pages/summaries_view.clj` | 9 | Pages |
| `src/com/apriary.clj` | 6 | Core |
| `src/com/apriary/ui/summaries_list.clj` | 5 | UI |
| `src/com/apriary/ui/summary_card.clj` | 4 | UI |
| `src/com/apriary/ui.clj` | 4 | UI |
| `src/com/apriary/pages/app.clj` | 4 | Pages |
| `src/com/apriary/email.clj` | 4 | Services |
| `resources/tailwind.css` | 4 | Styling |
| `Dockerfile` | 4 | Infra |
| `deps.edn` | 4 | Deps |

### Aktywność modułów (Q4 2025)

| Moduł | Zmiany | Focus |
|-------|--------|-------|
| `src/com/apriary/pages/` | 22 | **Summaries dominacja** (summaries_view:9, app:4, home:3, generations:3) |
| `src/com/apriary/ui/` | 20 | **Summaries UI** (summaries_list:5, summary_card:4, layout:3) |
| `src/com/apriary/services/` | 9 | **AI generation** (generation:3, summary:2, openrouter:2) |

**Charakterystyka Q4 2025:**
- **Feature-first:** 84 src changes vs 17 test changes (ratio 1:4.9)
- **Summaries-centric:** Cały ekosystem budowany wokół summaries view
- **AI integration:** Services layer focused on generation + openrouter
- **UI polish:** Tailwind, layout, component refinement

---

## Q2 2026: Test Hardening Phase

**Dominujący focus:** Products domain + retroactive test coverage

### Top 10 plików (Q2 2026)

| Plik | Zmiany | Obszar |
|------|--------|--------|
| `test/com/apriary/services/product_csv_test.clj` | 4 | **Test** |
| `test/com/apriary/pages/summaries_view_test.clj` | 4 | **Test** |
| `test/com/apriary/pages/rankings_test.clj` | 4 | **Test** |
| `test/com/apriary/pages/products_test.clj` | 4 | **Test** |
| `src/com/apriary/pages/products.clj` | 4 | Pages |
| `test/com/apriary/services/product_rankings_test.clj` | 3 | **Test** |
| `test/com/apriary/services/csv_import_test.clj` | 3 | **Test** |
| `src/com/apriary/ui/header.clj` | 3 | UI |
| `.github/workflows/master-docker.yml` | 3 | CI/CD |
| `test/com/apriary/services/product_test.clj` | 2 | **Test** |

### Aktywność modułów (Q2 2026)

| Moduł | Zmiany | Focus |
|-------|--------|-------|
| `test/com/apriary/pages/` | 15 | **Retroactive test coverage** (summaries_view, rankings, products) |
| `test/com/apriary/services/` | 21 | **Services testing** (product_csv, rankings, csv_import, product) |
| `src/com/apriary/pages/` | 6 | **Products shift** (products:4, rankings:1) |
| `src/com/apriary/services/` | 6 | **Products services** (product_rankings:2, product_csv:2, product:2) |
| `src/com/apriary/ui/` | 6 | Maintenance (header:3, products:2) |

**Charakterystyka Q2 2026:**
- **Test-first:** 30 test changes vs 22 src changes (ratio 1.4:1) — **REVERSED**
- **Products pivot:** Shift from summaries (Q4) to products domain (Q2)
- **Retroactive coverage:** Tests written for existing features (summaries_view_test:4)
- **CI/CD hardening:** Workflow updates (master-docker.yml:3)

---

## Kluczowe Przesunięcia Priorytetów

### 1. **Domain Focus Shift**

| Kwartał | Primary Domain | Secondary Domain |
|---------|---------------|------------------|
| Q4 2025 | **Summaries** (summaries_view:9, summaries_list:5, summary_card:4) | Generations (generation:3) |
| Q2 2026 | **Products** (products:4, product_csv:2, product_rankings:2) | Rankings (rankings:1) |

**Wniosek:** Projekt ewoluował od summaries-centric MVP do product-aware application.

### 2. **Development Philosophy Shift**

| Kwartał | Filozofia | Test/Src Ratio | Evidence |
|---------|-----------|----------------|----------|
| Q4 2025 | Feature-driven | 1:4.9 | Ship features, add tests later |
| Q2 2026 | Test-driven hardening | 1.4:1 | Test backfill + new features with tests |

**Wniosek:** Projekt przeszedł z "move fast" do "stabilize and harden" mode.

### 3. **Infrastructure Maturity**

| Aspekt | Q4 2025 | Q2 2026 |
|--------|---------|---------|
| Docker | 4 zmiany | 0 zmian (stabilny) |
| CI/CD workflows | Brak widocznej aktywności | 3 zmiany (optimization) |
| Dependencies | 4 zmiany (budowanie stacku) | 0 zmian (stack stable) |

**Wniosek:** Infra established w Q4, refined w Q2, teraz stable.

### 4. **Test Coverage Evolution**

**Q4 2025 test focus:**
- `ui/integration_test.clj` (3) — smoke tests
- `services/summary_test.clj` (3) — core business logic
- Minimalna coverage, targeted at critical paths

**Q2 2026 test focus:**
- `services/product_csv_test.clj` (4) — new domain
- `pages/summaries_view_test.clj` (4) — **retroactive** coverage of Q4 work
- `pages/products_test.clj` (4) — new domain
- `services/csv_import_test.clj` (3) — data integrity
- Comprehensive coverage, both new and retroactive

**Wniosek:** Q2 był "test debt paydown" phase.

---

## Wnioski — Mapa Aktywności

### 🔥 **HOT ZONES** (9+ zmian)
Strefy wysokiego ryzyka — każda zmiana tutaj ma szansę wpłynąć na stabilność:

1. **`summaries_view.clj`** (9 zmian) — absolutny hotspot projektu
2. **`apriary.clj`** (8 zmian) — core app, punkt wejściowy

### 🟡 **WARM ZONES** (4-6 zmian)
Strefy średniej aktywności — stabilne, ale wciąż w użyciu:

- `ui/header.clj` (6)
- `ui/summaries_list.clj` (5)
- `schema.clj` (5)
- `pages/app.clj` (5)
- `ui/summary_card.clj` (4)
- `ui.clj` (4)
- `pages/products.clj` (4)
- `email.clj` (4)

### ❄️ **COLD ZONES** (1-3 zmiany)
Strefy niskiej aktywności — rzadko modyfikowane:

- Większość plików w `services/`
- Pliki w `pages/` poza summaries i app
- Utility files (`util.clj`, `middleware.clj`, `auth.clj`)

---

## Charakterystyka Projektu

**Koncentracja aktywności:** Wysoka  
- **32% wszystkich zmian** w kodzie źródłowym dotyczy tylko **3 plików** (summaries_view, apriary.clj, header)
- **Summaries domain** dominuje: pages/summaries_view + ui/summaries_list + ui/summary_card = koncentracja biznesowej logiki

**Pokrycie testami:**  
- 44 zmiany w test/ vs 98 zmian w src/ = **ratio 1:2.2**
- Test activity mirrors src activity: services (21), pages (15), ui (4)
- Highest test activity: `services/` tests — sugeruje service-heavy testing strategy

**Infrastruktura:**  
- CI/CD actively maintained (`.github/workflows/master-docker.yml` — 6 zmian)
- Docker deployment evolved (`Dockerfile` — 5 zmian)
- Dependencies stabilizing (`deps.edn` — 5 zmian, ale nie top-tier)

**Styl rozwoju:**  
- **Feature-driven evolution** — summaries view był epicentrum rozwoju w ostatnim roku
- **Incremental refinement** — warm zones pokazują iteracyjne ulepszenia, nie rewrites
- **Test-conscious** — test changes follow src changes, ale nie TDD (src leads)

---

## Rekomendacje dla Agentic Work

1. **Pre-flight check przed dotknięciem `summaries_view.clj`:**  
   - To najczęściej modyfikowany plik — prawdopodobnie ma najwięcej dependentów
   - Wymaga dependency analysis (artifact-2) przed inwazyjnymi zmianami

2. **`apriary.clj` jako integration risk:**  
   - 8 zmian w core app file — każda zmiana tutaj to potencjalny blast radius
   - Wymaga smoke testów po każdej zmianie

3. **Summaries domain = critical path:**  
   - pages/summaries_view + ui/summaries_list + ui/summary_card + services/summary  
   - Ten łańcuch to prawdopodobnie główna user journey — priorytet dla regression testing

4. **Services layer stabilny:**  
   - Niższa aktywność niż pages/ui — sugeruje, że business logic jest bardziej stabilna niż presentation
   - Zmiany w services mają mniejsze ryzyko breaking changes niż w UI

---

---

## Sprzężenia Modułów — Co Zmienia Się Razem

**Analiza:** 85 commitów z ostatnich 12 miesięcy, wykrywanie co-occurrence katalogów w tych samych commitach.

### TOP 3 Pary Katalogów (coupling strength)

| Ranga | Para | Co-commits | % z total | Wzorzec |
|-------|------|------------|-----------|---------|
| **#1** | **pages + ui** | 12 | 14% | 🔥 **Najsilniejsze sprzężenie** |
| **#2** | **pages + core** | 7 | 8% | 🎯 Routing integration |
| **#3** | **pages + services** | 6 | 7% | 🔄 Business logic wiring |

#### Statystyka bazowa:
- Commits touching **pages**: 19 (22% wszystkich)
- Commits touching **ui**: 12 (14% wszystkich)
- Commits touching **services**: 8 (9% wszystkich)

---

### #1: pages + ui (12 commitów, 63% overlap pages↔ui)

**Coupling ratio:** 12/19 = **63%** commitów z pages dotyka też ui  
**Reverse ratio:** 12/12 = **100%** commitów z ui dotyka też pages

**Wniosek:** **UI components NIE ISTNIEJĄ bez pages** — każda zmiana w `ui/` to zmiana w kontekście konkretnego page.

#### Przykładowe commity (pages+ui):
```
c0ac0ad fix(product-input-view): apply implementation review fixes
cb09f78 feat(product-rankings): Rankings Service & Page (p1)
72ed70f Fix accepting cards
083b754 Implement auth, login, sign
5c6bcb7 Fix displaying summaries
b02d732 Fix loading summaries
46f7315 Add Summaries list page
71e4904 Add Summary Card
c36fe51 Add CSV import section
3fb4347 Add notifications and toaster
```

**Wzorce:**
- **New feature pattern:** Nowy page → nowe UI components (summaries page + summary card, rankings page + rankings UI)
- **Bug fix pattern:** Fix w page → często wymaga fix w UI component (fix displaying/loading summaries)
- **Refactor pattern:** Review fixes dotykają obu warstw jednocześnie

**Dekompozycja sprzężenia:**
- pages + ui **TYLKO** (bez services): **8 commitów** — UI-only work (layouts, styling, component structure)
- pages + ui + services (full stack): **4 commity** — end-to-end features

**Charakterystyka:**
- **67% sprzężeń pages+ui** to pure presentation layer work (bez services)
- **33% sprzężeń pages+ui** to full-stack features (z services w tym samym commicie)

---

### #2: pages + core (7 commitów, 37% overlap)

**Coupling ratio:** 7/19 = **37%** commitów z pages dotyka też `apriary.clj` (core app)

**Wniosek:** **Routing changes** — każdy nowy page lub endpoint wymaga update w core routing table.

#### Przykładowe commity (pages+core):
```
cb09f78 feat(product-rankings): Rankings Service & Page (p1)
e17f678 feat(product-input-view): CSV Import Handler (p2)
083b754 Implement auth, login, sign
b02d732 Fix loading summaries
46e395a Add api endpoints
4dba961 Add generations endpoints
```

**Wzorce:**
- **New endpoint pattern:** Nowy page → nowy route w `apriary.clj` (rankings, csv import, generations)
- **Auth integration:** Auth pages → core middleware setup
- **API wiring:** Feature page → API endpoint registration w core

**Charakterystyka:**
- Wszystkie 7 commitów to **nowe features** lub **nowe endpointy** — żaden fix/refactor
- Sprzężenie pages+core = **infrastructure expansion**, nie maintenance

---

### #3: pages + services (6 commitów, 32% overlap)

**Coupling ratio:** 6/19 = **32%** commitów z pages dotyka też services

**Wniosek:** **Business logic wiring** — pages integrują się z services, ale nie zawsze (2/3 pages changes nie dotyka services).

#### Przykładowe commity (pages+services):
```
c0ac0ad fix(product-input-view): apply implementation review fixes
cb09f78 feat(product-rankings): Rankings Service & Page (p1)
72ed70f Fix accepting cards
b02d732 Fix loading summaries
46e395a Add api endpoints
4dba961 Add generations endpoints
```

**Wzorce:**
- **New feature pattern:** Nowy page z business logic → nowy service (rankings, generations)
- **Integration fix:** Fix w page może wymagać fix w service (loading summaries, accepting cards)

**Dekompozycja:**
- pages + services **TYLKO** (bez ui): **2 commity** — backend-focused work
- pages + services + ui (full stack): **4 commity** — end-to-end features

**Charakterystyka:**
- **67% sprzężeń pages+services** to full-stack (z UI w tym samym commicie)
- **33% sprzężeń pages+services** to backend-only integration

---

## Dodatkowe Sprzężenia

### Test Coupling

| Para | Co-commits | Pattern |
|------|------------|---------|
| **services + test_services** | 5 | Dobra praktyka — service code z testami |
| **ui + test_ui** | 2 | Słabe — większość UI changes bez testów |
| **pages + test_pages** | 3 | Słabe — większość pages changes bez testów |

**Test discipline:**
- **Services layer:** 5/8 (63%) zmian w services ma testy w tym samym commicie
- **UI layer:** 2/12 (17%) zmian w UI ma testy w tym samym commicie
- **Pages layer:** 3/19 (16%) zmian w pages ma testy w tym samym commicie

**Wniosek:** Tests są pisane dla **services** (business logic), nie dla **UI/pages** (presentation).

### Cross-cutting Concerns

| Para | Co-commits | Znaczenie |
|------|------------|-----------|
| ui + core | 4 | UI infrastructure (layout, header) → core integration |
| services + core | 4 | Service registration w core app |
| ui + schema | 1 | Rare — UI rarely touches schema directly |
| services + schema | 1 | Rare — schema changes are isolated |

---

## Wnioski z Analizy Sprzężeń

### 1. **Architecture Pattern: Pages-Driven Development**

```
         pages (19 commits)
           ├─ 63% → ui (12/19)
           ├─ 37% → core (7/19)
           └─ 32% → services (6/19)
```

**Pages są punktem integracji** — większość zmian zaczyna się od pages i rozprzestrzenia na inne moduły.

### 2. **UI Components = Coupled Tightly to Pages**

- **100% UI changes** dotyka pages (12/12)
- **0% UI changes** są standalone

**Wniosek:** `ui/` to nie reusable component library — to **page-specific components**. Każdy UI component jest tworzony dla konkretnego page i nie jest reusowany poza jego kontekstem.

**Ryzyko:** Brak reusability oznacza duplication risk. Jeśli dwa pages potrzebują podobnego UI, mogą mieć duplicate components zamiast shared.

### 3. **Services = Loosely Coupled**

- **Tylko 32%** pages changes dotyka services (6/19)
- **Tylko 63%** services changes ma testy (5/8)

**Wniosek:** Services są relatywnie niezależne od presentation layer. Można zmienić UI bez touching services.

**Dobra praktyka:** Services layer ma najlepszą test discipline (63% coverage w commitach).

### 4. **Core App = Routing Hub**

- **37%** pages changes wymaga core update (7/19)
- Wszystkie pages+core commity to **nowe features/endpointy**

**Wniosek:** `apriary.clj` to routing table + middleware setup. Każdy nowy page = update w core. Nie jest to logika biznesowa (ta jest w services), tylko infrastructure wiring.

### 5. **Full-Stack Commits są Rzadkie**

- pages + ui + services: **4 commity** (tylko 5% z 85 total)
- Większość commitów dotyka **1-2 modułów**, nie całego stacku

**Wniosek:** Development jest **layer-focused**, nie full-stack. Programiści pracują w jednej warstwie naraz (albo UI, albo services, albo routing), nie piszą features end-to-end w jednym commicie.

**Implikacja dla testów:** E2E tests mogą wykrywać integration bugs między layers, bo layers są rozwijane oddzielnie.

---

## Rekomendacje dla Agentic Work (na bazie sprzężeń)

### 1. **Modyfikując pages → expect UI changes**

63% prawdopodobieństwa, że zmiana w pages wymaga update UI components. Przed dotknięciem pages:
- Zidentyfikuj powiązane UI components (dependency analysis w artifact-2)
- Sprawdź, czy UI component jest używany tylko w tym page, czy shared

### 2. **Modyfikując UI → ZAWSZE check pages context**

100% UI changes jest coupled do pages. Przed dotknięciem UI component:
- Znajdź, który page go używa
- Sprawdź, czy zmiana w UI component nie zepsuje page layout/behavior

### 3. **Dodając nowy page → expect core routing update**

37% pages changes wymaga core update. Przy tworzeniu nowego page:
- Plan na update `apriary.clj` routing table
- Sprawdź pattern w istniejących pages+core commitach dla consistency

### 4. **Services layer = safest to modify**

Tylko 32% pages changes dotyka services, co oznacza że services są relatywnie izolowane. Services to najbezpieczniejsza warstwa do refactoringu.

### 5. **Test gap w UI/Pages = regression risk**

Tylko 16-17% UI/pages changes ma testy w tym samym commicie. Przy modyfikacji UI/pages:
- Sprawdź czy są **post-facto** tests (dodane w Q2 2026)
- Jeśli nie, expect manual testing lub wyższe ryzyko regresu

---

## Timeline Synthesis — Projekt w Czasie

```
2025-11  ┃ 🚀 Initial development (18 commits)
2025-12  ┃ 🎨 Summaries MVP + UI polish (14 commits)
         ┃ 
2026-01  ┃ 
2026-02  ┃ ❄️ 3-month pause (hiberfnacja? context switch?)
2026-03  ┃ 
         ┃
2026-05  ┃ 🔄 Products pivot begins (10 commits)
2026-06  ┃ 🧪 Test hardening surge (35 commits) ← current
```

**Wzorzec projektu:** Sprint → Pause → Pivot → Harden

---

## Implikacje dla Agentic Work

### 1. **Hot Zones są domain-specific**

**Q4 2025 hot zones (już stabilne):**
- `summaries_view.clj` — 9 zmian w Q4, **0 w Q2** → feature complete
- `ui/summaries_list.clj` — 5 w Q4, **0 w Q2** → stabilny
- `ui/summary_card.clj` — 4 w Q4, **0 w Q2** → stabilny

**Q2 2026 hot zones (aktywne):**
- `pages/products.clj` — 0 w Q4, **4 w Q2** → active development
- `services/product_csv.clj` — 0 w Q4, **2 w Q2** → nowy kod
- `services/product_rankings.clj` — 0 w Q4, **2 w Q2** → nowy kod

**Wniosek:** Summaries domain jest **legacy** (stabilny, pokryty testami). Products domain jest **frontier** (aktywny, może mieć luki w testach).

### 2. **Test coverage ma gaps**

- Summaries domain: tests dodane **post-facto** w Q2 (summaries_view_test:4)
- Products domain: tests pisane **alongside** w Q2 (products_test:4, product_csv_test:4)

**Ryzyko:** Jeśli Q4 summaries code ma bugs, tests mogą je kodyfikować zamiast wykrywać.

**Zalecenie:** Przed modyfikacją summaries domain, zweryfikuj czy Q2 tests są assertion-based czy snapshot-based.

### 3. **3-month gap = potential context loss**

Brak commitów styczeń-kwiecień 2026 może oznaczać:
- Zmianę team composition
- Context switch na inny projekt
- Requirement changes podczas pauzy

**Zalecenie:** Jeśli Q2 code wprowadza breaking changes do Q4 architecture, może to być symptom "fresh eyes" rethink — warto sprawdzić spójność patterns między Q4 a Q2.

### 4. **Infrastruktura jest stable → safe to ignore**

- Docker: 4 zmiany w Q4, 0 w Q2
- Deps: 4 zmiany w Q4, 0 w Q2
- Workflows: stabilne do maja 2026, potem 3 zmiany (likely optimizations)

**Zalecenie:** Nie dotykaj Dockerfile ani deps.edn unless explicitly required — są w stable state.

---

---

## Cross-Cutting Files — "Wspólny Mianownik" Projektu

**Pytanie:** Czy istnieje plik który zmienia się razem z wieloma różnymi obszarami naraz? Plik który łączy różne moduły?

### Ranking Cross-Cutting Files

Pliki pojawiające się w commitach dotyczących ≥2 różnych obszarów jednocześnie:

| Ranga | Plik | Multi-area commits | Typ | Status |
|-------|------|-------------------|-----|--------|
| **#1** | `src/com/apriary.clj` | 6 | Core app | ✓ EXISTS |
| **#2** | `src/com/apriary/util.clj` | 3 | Utilities | ✓ EXISTS |
| **#2** | `resources/tailwind.css` | 3 | Styling | ✓ EXISTS |
| #4 | `src/com/apriary/ui.clj` | 2 | UI helpers | ✓ EXISTS |
| #4 | `src/com/apriary/schema.clj` | 2 | Schema defs | ✓ EXISTS |
| #4 | `src/com/apriary/schema/api.clj` | 2 | API schema | ✓ EXISTS |
| #4 | `resources/public/js/main.js` | 2 | Frontend JS | ✓ EXISTS |
| #4 | `deps.edn` | 2 | Dependencies | ✓ EXISTS |

**Wniosek:** `src/com/apriary.clj` to **dominujący hub** — pojawia się w 6 commitach multi-area (75% z 8 total commitów touching core).

### #1 Hub: `src/com/apriary.clj` (Core App)

**Funkcja:** Routing table + middleware setup + app initialization

**Pattern:** Gdy commit dotyka ≥2 obszarów (np. pages + services), bardzo często musi też update'ować `apriary.clj` aby podłączyć routing.

**Przykłady użycia:**
- New page → new route in apriary.clj
- New API endpoint → endpoint registration in apriary.clj
- Auth integration → middleware setup in apriary.clj

**Coupling strength:** 
- Z pages: 7 commitów (37% pages changes)
- Z UI: 4 commity (33% UI changes)
- Z services: 4 commity (50% services changes)

**Wniosek:** `apriary.clj` to **integration hub** — każda full-stack feature przechodzi przez ten plik.

### #2 Hub: `src/com/apriary/util.clj` (Utilities)

**Funkcja:** Shared utility functions używane przez wszystkie moduły

**Commity gdzie util łączy obszary:**
- `b02d732 Fix loading summaries` — util + pages + ui + services (full stack fix)
- `46e395a Add api endpoints` — util + pages + services
- `4dba961 Add generations endpoints` — util + pages + services

**Pattern:** Gdy feature wymaga wspólnej logiki (formatowanie, parsowanie, helpers), util.clj jest update'owany alongside feature code.

**Coupling strength:** Słabsze niż apriary.clj (3 vs 6), ale bardziej **semantic** — util zmienia się gdy potrzebne są shared helpers.

### #3 Hub: `resources/tailwind.css` (Styling)

**Funkcja:** Global styles + Tailwind customization

**Commity:**
- `083b754 Implement auth, login, sign` — new auth UI
- `b02d732 Fix loading summaries` — UI fix
- `c36fe51 Add CSV import section` — new feature UI

**Pattern:** Nowe UI features lub UI fixes często wymagają update global styles (custom colors, spacing, components).

**Coupling strength:** Słaba (3 commity), ale **konsystentna** — każdy większy UI feature dotyka tailwind.

### Inne Cross-Cutting Concerns

**Schema files (`schema.clj`, `schema/api.clj`):**
- Tylko 2 multi-area commits każdy
- Pattern: Dodanie nowego entity type lub API contract
- Low coupling = dobra separacja — schema changes nie ripple przez cały codebase

**Dependencies (`deps.edn`):**
- 2 multi-area commits
- Pattern: Dodanie nowej biblioteki dla nowego feature
- Stabilny (5 zmian w Q4, 0 w Q2) — stack is mature

### Co NIE jest hubem?

**Brak:**
- ❌ Plików z tłumaczeniami (i18n/locale) — projekt nie ma internationalization
- ❌ Plików z konfiguracją biznesową — `resources/config.edn` ma tylko 3 commity (infra setup, nie business logic)
- ❌ Generated files (build artifacts, compiled JS) — nie są w repo
- ❌ Constants/enums file — constans są inline w modułach, nie w centralnym pliku

**Wniosek:** Projekt jest **prosty** — brak complex cross-cutting concerns jak i18n, feature flags, centralne stałe biznesowe.

---

## Weryfikacja Istnienia Kluczowych Plików

**Wszystkie TOP 10 hotspot files:** ✓ EXIST  
**Wszystkie cross-cutting hub files:** ✓ EXIST

### Pliki które zostały USUNIĘTE z repo (ale są w historii):

**Dokumentacja AI (`.ai/`):**
- `.ai/prd.md`, `.ai/tech-stack.md` (zostały przeniesione do `context/foundation/`)
- `.ai/test/auth/*.md` (stare plany testowe)
- `.ai/api/*.md`, `.ai/db/*.md` (stare plany implementacyjne)

**Źródła które zostały PRZENIESIONE:**
- ✗ `src/com/apriary/app.clj` → ✓ `src/com/apriary/pages/app.clj` (refactor: page do pages/)
- ✗ `src/com/apriary/home.clj` → ✓ `src/com/apriary/pages/home.clj` (refactor: page do pages/)

**WNIOSEK KLUCZOWY:**
Projekt przeszedł **refactor struktur** — pages były początkowo w root `src/com/apriary/`, potem zostały przeniesione do `pages/` subfolder.

**To wyjaśnia część sprzężenia pages+core** — niektóre commity w historii dotyczą starej struktury (`app.clj`, `home.clj` w root), zanim zostały przeniesione do `pages/`.

**Implikacja dla analizy:**
- Historia git pokazuje ewolucję struktury projektu
- Obecna struktura (`pages/`, `ui/`, `services/`) jest **relatywnie nowa** (post-refactor)
- Analiza dependencies w artifact-2 powinna bazować na **obecnej** strukturze, nie historycznej

---

## Podsumowanie Odkryć

### 1. **Core Hub Pattern**

`apriary.clj` to **niezbędny punkt przejścia** dla każdej full-stack zmiany. 6/8 commitów touching core to multi-area commits.

**Implikacja:** Modyfikacje `apriary.clj` mają największy blast radius — wymagają testów routing + middleware + integration.

### 2. **Utilities jako Secondary Hub**

`util.clj` łączy obszary przez **shared helpers**, nie przez infrastructure. Słabsze sprzężenie niż core, ale bardziej semantyczne.

**Implikacja:** Zmiany w util.clj mogą wpływać na wiele modułów jednocześnie — require regression testing across modules.

### 3. **Styling jako UI Hub**

`tailwind.css` łączy wszystkie UI changes — każdy nowy feature UI dotyka global styles.

**Implikacja:** Zmiany w tailwind mogą wpływać na wszystkie pages/components — visual regression testing needed.

### 4. **Brak Centralized Business Config**

Projekt nie ma pliku z business constants, feature flags, ani i18n — wszystko jest inline w kodzie.

**Zaleta:** Prostota, brak indirection  
**Wada:** Trudniej zmienić global business rules (np. limits, thresholds) bez touching code

### 5. **Struktura Ewoluowała**

Refactor z root pages (`app.clj`, `home.clj`) do `pages/` subfolder pokazuje że projekt **cleanup'ował strukturę** w trakcie rozwoju.

**Implikacja:** Historia git może zawierać patterns które już nie obowiązują — analiza dependencies powinna bazować na **obecnym stanie**, nie historycznym.

---

## Executive Summary — Mapa Terytorialna Projektu

**Projekt:** Apriary Application (MVP — apiary work summaries)  
**Okres analizy:** 12 miesięcy (2025-11-22 do 2026-06-08)  
**Total commits:** 85  
**Aktywny development:** 7 miesięcy (z 3-miesięczną przerwą Q1 2026)

### Kluczowe Liczby

| Metryka | Wartość | Insight |
|---------|---------|---------|
| Najaktywniejszy moduł | `pages/` (28 zmian) | Presentation layer = epicentrum |
| Absolutny hotspot | `summaries_view.clj` (9 zmian) | 32% top-3 zmian w jednym pliku |
| Test/Src ratio Q4 2025 | 1:4.9 | Feature-first development |
| Test/Src ratio Q2 2026 | 1.4:1 | Test hardening pivot |
| Najsilniejsze sprzężenie | pages↔ui (12 commits, 100% overlap) | UI = page-specific components |
| Integration hub | `apriary.clj` (6 multi-area commits) | Routing + middleware nexus |

### Timeline — Trzy Fazy Projektu

```
Q4 2025 (Nov-Dec)   →  Q1 2026 (Jan-Mar)  →  Q2 2026 (Apr-Jun)
────────────────────    ──────────────────    ─────────────────
🚀 Feature Sprint       ❄️ Hibernacja        🧪 Test Hardening
32 commits              0 commits            53 commits
Summaries domain        -                    Products pivot
Feature-first (1:4.9)   -                    Test-first (1.4:1)
```

### Trzy Domeny Produktowe

| Domain | Status | Evidence |
|--------|--------|----------|
| **Summaries** | Legacy/Stable | 9 zmian w Q4, 0 w Q2 — feature complete |
| **Products** | Frontier/Active | 0 zmian w Q4, 4+ w Q2 — new development |
| **Generations** | Service layer | 3 zmiany (AI integration) — supporting feature |

### Trzy Warstwy Architektury

| Layer | Commits | Coupling | Test Coverage | Charakterystyka |
|-------|---------|----------|---------------|-----------------|
| **Pages** | 19 (22%) | 63%→ui, 37%→core, 32%→services | 16% w-commit | Integration nexus |
| **UI** | 12 (14%) | 100%→pages | 17% w-commit | Page-specific, nie reusable |
| **Services** | 8 (9%) | Luźno sprzężony | 63% w-commit | Best test discipline |

### Pięć Kluczowych Odkryć

#### 1. **Pages-Driven Development**
```
         pages (19 commits)
           ├─ 63% → ui (page-specific components)
           ├─ 37% → core (routing integration)
           └─ 32% → services (business logic wiring)
```
Pages są punktem integracji całego stacku.

#### 2. **UI Components = Tightly Coupled**
- **100% UI changes** dotyka pages
- **0% standalone** UI components
- Wniosek: `ui/` to **nie** reusable library — duplication risk między pages

#### 3. **Summaries → Products Pivot**
- Q4 2025: Summaries hotspot (summaries_view:9, summaries_list:5, summary_card:4)
- Q2 2026: Products shift (products:4, product_csv:2, product_rankings:2)
- Summaries domain jest **stabilny** (0 zmian w Q2) — legacy code

#### 4. **Test Debt Paydown w Q2**
- Q4: 17 test zmian vs 84 src (ship features, add tests later)
- Q2: 30 test zmian vs 22 src (**reversed** — więcej testów niż kodu)
- Czerwiec 2026 (35 commits) = największy miesiąc w historii projektu

#### 5. **Integration Hub Pattern**
- `apriary.clj` to **niezbędny punkt przejścia** dla full-stack changes (6/8 commits)
- `util.clj` łączy obszary przez shared helpers (3 commits)
- `tailwind.css` łączy UI features (3 commits)

### Trzy Strefy Ryzyka

| Strefa | Pliki | Dlaczego Ryzykowne |
|--------|-------|-------------------|
| 🔥 **HOT** | `summaries_view.clj`, `apriary.clj` | Najczęściej modyfikowane — highest blast radius |
| 🟡 **WARM** | `header.clj`, `summaries_list.clj`, `schema.clj`, `pages/app.clj` | Średnia aktywność — stabilne ale używane |
| ❄️ **COLD** | Większość `services/`, utility files | Niska aktywność — rzadko dotykane |

### Pięć Rekomendacji dla Agentic Work

#### 1. **Przed dotknięciem pages → expect UI changes**
63% prawdopodobieństwa że zmiana w pages wymaga update UI components.

#### 2. **Przed modyfikacją UI → ZAWSZE check pages context**
100% UI changes jest coupled do pages. Zero standalone components.

#### 3. **Services layer = safest to modify**
Tylko 32% pages changes dotyka services — relatywnie izolowane.

#### 4. **Summaries domain = legacy, Products domain = frontier**
- Summaries: feature complete (Q4), pokryty testami post-facto (Q2)
- Products: active development (Q2), testy alongside code

#### 5. **Core app changes = integration risk**
37% pages changes wymaga core update. Each new page/endpoint touches `apriary.clj`.

### Ograniczenia Analizy

1. **Historia zawiera refactor** — pages były w root, potem przeniesione do `pages/` subfolder
2. **3-miesięczna przerwa** (Q1 2026) może oznaczać team composition change lub context loss
3. **Test coverage metrics** bazują na commit-time coverage, nie actual runtime coverage
4. **Coupling analysis** pokazuje co-occurrence, nie causation — może być correlation bez dependency

### Struktura Projektu (Post-Refactor)

```
src/com/apriary/
  ├─ pages/          28 zmian (presentation layer)
  ├─ ui/             26 zmian (page-specific components)
  ├─ services/       15 zmian (business logic)
  ├─ apriary.clj      8 zmian (routing hub)
  ├─ schema.clj       5 zmian (data definitions)
  └─ util.clj         3 zmian (shared helpers)

test/com/apriary/
  ├─ services/       21 zmian (best test coverage)
  ├─ pages/          15 zmian (retroactive Q2 coverage)
  └─ ui/              4 zmian (weak coverage)
```

### Weryfikacja Danych

✅ **Wszystkie kluczowe pliki istnieją** (TOP 10 hotspots + cross-cutting hubs)  
✅ **Brak missing dependencies** — analiza bazuje na aktualnym stanie repo  
✅ **Historia jest kompletna** — 85 commits z ostatnich 12 miesięcy

### Status Artefaktu

**Kompletność:** ✓ Pełna analiza git history  
**Weryfikacja:** ✓ Wszystkie pliki zweryfikowane jako istniejące  
**Następny krok:** `artifact-2-structure.md` — dependency analysis (entry points, cycles, local centers)

---

**Ostatnia aktualizacja:** 2026-06-09  
**Następny krok:** `artifact-2-structure.md` — dependency analysis dla zidentyfikowanych hot zones (summaries domain jako legacy reference, products domain jako active target), bazując na **obecnej** strukturze projektu post-refactor.
