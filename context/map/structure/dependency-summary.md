# Analiza Cykli Zależności — Podsumowanie

## Najważniejsze Obserwacje

1. **✅ Brak cykli zależności** — projekt ma czystą, acykliczną architekturę (rzadkość w legacy!)
2. **🔥 Summaries-View to God Page** — 12 zależności, absolutny hotspot (9 zmian), największy fan-out
3. **🎯 Middleware = Single Point of Failure** — używany przez wszystkie 7 pages, zero internal deps ale Ca=7
4. **📦 Services idealnie izolowane** — Ca=0 Ce=0 internal, najbezpieczniejsze do modyfikacji
5. **🔄 UI-Pages 100% coupling** — zero standalone UI components, duplikacja prawdopodobna

## Szczegółowa Analiza (według Territory Map)

| Obszar | Co znalazłeś | Dowód z dependency analysis | Dlaczego to ważne przy zmianie | Związek z artifact-1-territory.md | Co sprawdzić dalej |
|--------|--------------|----------------------------|-------------------------------|----------------------------------|-------------------|
| **🔥 summaries-view.clj** | God Page z fan-out 12 dependencies (middleware, 6x ui components, 4x services, dto, util) | `Ce=12` (największy w projekcie), `Ca=0` | Każda zmiana w którymkolwiek z 12 deps może wymagać update'u tego page. Integration hell — trudno testować 12 dependencies naraz. Jeśli bug w jednym z deps → objawia się tutaj. | "Absolutny hotspot projektu (9 zmian)" / "32% top-3 zmian w jednym pliku" / "Summaries domain = koncentracja biznesowej logiki" | 1. Czy ma integration tests? (12 deps = must have)<br>2. Czy wszystkie deps naprawdę potrzebne?<br>3. Czy można wydzielić CSV import logic? |
| **🔥 middleware** | Single Point of Failure — wszystkie 7 pages zależą od tego | `Ca=7` (highest afferent coupling), `Ce=4` (tylko external: biffweb, ring, muuntaja) | Zmiana w middleware propaguje się na WSZYSTKIE pages naraz. Bug tutaj → całość aplikacji może nie działać. Ale: zero internal deps = dobra separacja, tylko external framework deps. | "Cold zone (niska aktywność) ALE Ca=7" / "Middleware stable (I=0.00)" / "Services mają mniejsze ryzyko niż UI" | 1. Czy ma smoke tests dla wszystkich pages?<br>2. Czy można wydzielić page-specific middleware?<br>3. Coverage dla middleware pipeline? |
| **🟡 util.clj** | Cross-cutting helper używany przez 5 namespace'ów w różnych domenach | `Ca=5` (multi-domain: dto.generation, dto.summary, pages.summaries-view, pages.generations, pages.summaries) | Shared helpers = wygodne ALE ryzykowne. Zmiana behavior jednej funkcji w util → ripple effect przez 5 różnych obszarów (DTOs + pages). Różne domeny używają util → trudno sprawdzić wszystkie use cases. | "Secondary hub (3 zmiany)" / "Util łączy obszary przez shared helpers" / "Cold zone activity (stabilny)" | 1. Czy ma unit tests dla KAŻDEJ funkcji?<br>2. Jakie konkretnie funkcje są w util?<br>3. Czy można split na domain-specific utils? |
| **🟡 UI layer (all ui/\*)** | 100% coupling z pages — zero standalone components | Każdy UI component używany WYŁĄCZNIE przez pages. `ui.layout` → 4 pages, `ui.summary-card` → 2 usages, `ui.summaries-list` → 1 usage | Zero reusability → duplication risk. Jeśli products i summaries potrzebują podobnego card → prawdopodobnie duplicate code zamiast shared component. 100% overlap = nie możesz zmienić UI bez checking pages context. | "100% UI changes dotyka pages (12/12 commitów)" / "UI = page-specific components, nie reusable library" / "Tylko 17% UI changes ma testy" | 1. Audit for duplicate UI patterns (card, list, form)<br>2. Czy można wydzielić truly reusable components?<br>3. UI tests status (17% w-commit = słabe)? |
| **✅ Services layer** | Perfect isolation — żaden service nie zależy od innego service | Wszystkie services: `Ca=0 Ce=0` (internal deps). Używane TYLKO przez pages, nie przez inne services. | Safest area to modify — brak internal coupling = changes są izolowane. Łatwo testować (tylko external deps: xtdb, logging). ALE: może być duplikacja logiki między services (bo nie komunikują się). | "Services = najbezpieczniejsze do modyfikacji" / "32% pages changes dotyka services (luźno sprzężone)" / "Najlepsza test discipline (63% w-commit)" | 1. Czy jest duplikacja AI logic między summary i generation?<br>2. Czy isolation jest zamierzona czy symptom?<br>3. Runtime coverage testów services? |
| **🔥 apriary.clj (core)** | Routing hub z fan-out 10 (wszystkie pages + middleware) | `Ce=10` (drugi największy fan-out), `Ca=0` | Integration hub — każda full-stack feature przechodzi przez ten plik. 37% pages changes wymaga core update (nowy page = nowy route). Bug w routing → całość może nie działać. | "Core routing (8 zmian)" / "Routing hub — 6 multi-area commits" / "Każdy nowy page/endpoint touches apriary.clj" | 1. Smoke tests dla routing?<br>2. Czy routing logic jest testowalna?<br>3. Pattern consistency dla nowych routes? |
| **🟡 pages/products.clj** | Products domain page z fan-out 8 (medium coupling) | `Ce=8` (middleware, ui.layout, ui.products, 3x services) | Medium coupling — nie tak złe jak summaries-view (12) ale wciąż spore. Products to **frontier domain** (Q2 2026 active development) vs summaries = legacy (Q4 2025). Może mieć luki w testach bo nowy. | "Q2 2026 hot zone (4 zmiany)" / "Products pivot (0 w Q4 → 4 w Q2)" / "Active development, testy alongside code" | 1. Czy products ma testy (Q2 = test-first)?<br>2. Czy products ma podobne patterns jak summaries?<br>3. Coverage dla products domain? |
| **❄️ schema.clj** | Izolowany — używany tylko przez apriary.clj | `Ca=1` (tylko apriary), `Ce=0` (no deps) | Low risk — schema changes są izolowane. Ale: jeśli schema się zmienia → może wymagać update'u w wielu places (data validation). Low coupling = dobra separacja danych. | "Warm zone (5 zmian)" / "Schema changes są isolated" / "Low coupling w schema files" | 1. Czy schema ma Malli validation tests?<br>2. Czy schema changes propagują się na services?<br>3. Migration strategy dla schema? |

## Brak Cykli = Excellent Architecture

**Wniosek:** Żaden namespace nie tworzy circular dependency. To jest **bardzo dobra wiadomość** dla legacy codebase.

**Dlaczego to ważne:**
- ✅ Dependencies flow w jednym kierunku → łatwiejsze reasoning
- ✅ Możliwość izolowanego testowania modułów → każdy może być unit tested
- ✅ Mniejsze ryzyko cascade failures → bug nie propaguje się w kółko
- ✅ Łatwiejszy refactoring → możesz zmieniać "liście" drzewa bez touching "root"

**Contrast z typowym legacy:**
- ❌ Legacy często ma cycles między UI ↔ Services ↔ Data
- ❌ Cycles = trudno mockować dependencies w testach
- ❌ Cycles = reasoning nightmare (gdzie zaczyna się flow?)

## Priorytety dla Zmian

| Priorytet | Obszar | Dlaczego | Action |
|-----------|--------|----------|--------|
| 🔥 **P0** | `summaries-view` | Fan-out 12 + hotspot (9 zmian) | Integration tests + dependency audit |
| 🔥 **P0** | `middleware` | Ca=7 (all pages depend) | Smoke tests all pages + regression suite |
| 🟡 **P1** | `util` | Ca=5 (multi-domain ripple) | Unit tests every function + consider split |
| 🟡 **P1** | UI layer | 100% coupling + 17% test coverage | Audit duplicates + retroactive tests |
| ✅ **P2** | Services | Already safe (isolated + 63% tests) | Maintain discipline + check duplication |

## Next Steps

1. **Run coverage report:** `clojure -M:coverage` → reality check dla hot zones
2. **Integration test audit:** Sprawdź czy summaries-view + middleware mają integration tests
3. **UI duplication scan:** Compare ui.summary-card vs ui.products patterns
4. **Util function inventory:** List wszystkich funkcji w util + usage audit
5. **Services logic comparison:** Check czy summary/generation mają duplicate AI logic

