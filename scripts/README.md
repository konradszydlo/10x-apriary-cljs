# Dependency Analysis Tool

Narzędzie do analizy zależności między namespace'ami w projekcie Clojure, zbudowane na bazie `clj-kondo`.

## Wymagania

- **clj-kondo** - już zainstalowany ✓
- **babashka** (bb) - już zainstalowany ✓
- **GraphViz** (opcjonalnie) - do wizualizacji grafów w formacie SVG/PNG

## Instalacja GraphViz (opcjonalnie)

Aby renderować grafy dependency w formacie graficznym:

```bash
# Ubuntu/Debian
sudo apt-get install graphviz

# macOS
brew install graphviz

# Arch Linux
sudo pacman -S graphviz
```

## Użycie

### 1. Metryki stabilności namespace'ów

Pokazuje metryki stabilności dla każdego namespace:

```bash
bb scripts/analyze-deps.clj metrics
```

**Wyjście:**
```
com.apriary.util                   | Ca= 5 | Ce= 0 | I=0.00 | STABLE
com.apriary.middleware             | Ca= 7 | Ce= 0 | I=0.00 | STABLE
com.apriary.pages.summaries-view   | Ca= 1 | Ce=12 | I=0.92 | unstable
com.apriary                        | Ca= 0 | Ce=10 | I=1.00 | unstable
```

**Metryki:**
- **Ca (afferent coupling)** - ile namespace'ów zależy od tego
- **Ce (efferent coupling)** - od ilu namespace'ów ten zależy
- **I (instability)** = Ce / (Ca + Ce)
  - `I < 0.3` - **STABLE** (trudne do zmiany, wiele zależności)
  - `0.3 ≤ I < 0.7` - **medium** (średnia stabilność)
  - `I ≥ 0.7` - **unstable** (łatwe do zmiany, mało zależności)

### 2. Format tekstowy (lista zależności)

```bash
bb scripts/analyze-deps.clj text
```

Pokazuje każdy namespace z listą jego zależności i metrykami.

### 3. Format JSON

```bash
bb scripts/analyze-deps.clj json
```

Eksportuje dane w formacie JSON do dalszej obróbki:

```json
{
  "namespaces": ["com.apriary", "com.apriary.util", ...],
  "dependencies": {
    "com.apriary": ["com.apriary.auth", "com.apriary.middleware", ...],
    ...
  },
  "metrics": {
    "com.apriary.util": {"ca": 5, "ce": 0, "instability": 0.0},
    ...
  }
}
```

### 4. Graf GraphViz (DOT)

Generuje wizualny graf zależności z kolorowaniem według stabilności:

```bash
bb scripts/analyze-deps.clj dot > deps.dot
```

**Z GraphViz (renderowanie do SVG):**

```bash
# SVG (rekomendowane dla przeglądarek)
bb scripts/analyze-deps.clj dot | dot -Tsvg > deps.svg

# PNG
bb scripts/analyze-deps.clj dot | dot -Tpng > deps.png

# PDF
bb scripts/analyze-deps.clj dot | dot -Tpdf > deps.pdf
```

**Legendy kolorów w grafie:**
- 🟢 **Zielony** (lightgreen) - Stable (I < 0.3)
- 🟡 **Żółty** (lightyellow) - Medium (0.3 ≤ I < 0.7)  
- 🔴 **Czerwony** (lightcoral) - Unstable (I ≥ 0.7)

### 5. Alias w deps.edn

```bash
clojure -X:deps/graph
```

Pokazuje help z dostępnymi formatami.

## Interpretacja wyników

### Stable namespaces (I < 0.3)

**Przykład:** `com.apriary.util`, `com.apriary.middleware`

- Wiele innych namespace'ów od nich zależy (wysokie Ca)
- Mało zależności wychodzących (niskie Ce)
- **Implikacje:** Zmiany w tych namespace'ach wpływają na wiele modułów
- **Strategia:** Minimalizuj zmiany, dokładnie testuj przed zmianami

### Unstable namespaces (I ≥ 0.7)

**Przykład:** `com.apriary.pages.*`, główny `com.apriary`

- Niewiele innych namespace'ów od nich zależy (niskie Ca)
- Wiele zależności wychodzących (wysokie Ce)
- **Implikacje:** Łatwo zmieniać bez wpływu na resztę systemu
- **Strategia:** To są "końcówki" aplikacji - strony, handlery

### Medium namespaces (0.3 ≤ I < 0.7)

**Przykład:** `com.apriary.ui.layout`, `com.apriary.dto.*`

- Zbalansowane zależności
- **Strategia:** Standardowa ostrożność przy zmianach

## Przykładowe workflow

### 1. Quick check stabilności projektu

```bash
bb scripts/analyze-deps.clj metrics | head -20
```

### 2. Znalezienie najbardziej stabilnych modułów

```bash
bb scripts/analyze-deps.clj metrics | grep STABLE | head -10
```

### 3. Wygenerowanie wizualnego grafu

```bash
bb scripts/analyze-deps.clj dot | dot -Tsvg > context/map/namespace-dependencies.svg
```

Otworzyć w przeglądarce: `context/map/namespace-dependencies.svg`

### 4. Eksport do JSON dla CI/CD

```bash
bb scripts/analyze-deps.clj json > deps-analysis.json
```

## Porównanie z dependency-cruiser

| Feature | dependency-cruiser | analyze-deps.clj |
|---------|-------------------|------------------|
| Języki | JS/TS | **Clojure** |
| Instalacja | npm install | ✓ Already works (bb + clj-kondo) |
| Metryki stabilności | ✓ | ✓ |
| Graf wizualny | ✓ GraphViz | ✓ GraphViz |
| Filtrowanie | ✓ Advanced | Basic (przez CLI) |
| Reguły walidacji | ✓ | - (używaj clj-kondo imports) |
| Szybkość | Fast | **Very fast** (native) |

## Rozszerzenia

### Dodanie filtrowania namespace'ów

Edytuj `scripts/analyze-deps.clj` i dodaj opcję `--filter`:

```clojure
;; W extract-namespace-graph dodaj filtr:
(defn extract-namespace-graph [analysis filter-pattern]
  (let [ns-defs (-> analysis :analysis :namespace-definitions)
        all-ns (set (filter #(re-matches filter-pattern %) 
                            (map :name ns-defs)))
        ...]))
```

### Dodanie wykrywania cykli

```clojure
(defn detect-cycles [graph]
  ;; Implement Tarjan's algorithm or DFS cycle detection
  ...)
```

### Integracja z pre-commit hook

Dodaj do `lefthook.yml`:

```yaml
pre-commit:
  commands:
    deps-check:
      run: bb scripts/analyze-deps.clj metrics | grep -q "I=1.00" && echo "Warning: Some namespaces have max instability"
```

## Troubleshooting

### "clj-kondo: command not found"

Zainstaluj clj-kondo:

```bash
# Linux/macOS (via homebrew)
brew install borkdude/brew/clj-kondo

# Linux (manual)
curl -sLO https://raw.githubusercontent.com/clj-kondo/clj-kondo/master/script/install-clj-kondo
chmod +x install-clj-kondo
./install-clj-kondo
```

### "bb: command not found"

Zainstaluj babashka:

```bash
# Linux/macOS (via homebrew)
brew install borkdude/brew/babashka

# Linux (manual)
curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
chmod +x install
./install
```

### Graf nie renderuje się

1. Sprawdź czy GraphViz jest zainstalowany: `dot -V`
2. Sprawdź czy plik .dot jest poprawny: `bb scripts/analyze-deps.clj dot | head`
3. Jeśli brakuje GraphViz, zainstaluj (patrz sekcja "Instalacja GraphViz")

## Źródła

- [clj-kondo analysis documentation](https://github.com/clj-kondo/clj-kondo/blob/master/analysis/README.md)
- [GraphViz documentation](https://graphviz.org/documentation/)
- [Robert C. Martin - Stability Metrics](https://en.wikipedia.org/wiki/Software_package_metrics)
