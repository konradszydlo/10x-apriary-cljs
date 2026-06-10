# Structure Analysis — Supporting Files

This directory contains detailed analysis files supporting **artifact-2-structure.md**.

## File Organization

### Dependency Analysis
- **dependencies.json** — Raw clj-kondo analysis output
- **dependency-summary.md** — Quick reference: cycles + coupling metrics
- **dependency-analysis-active-zones.md** — Deep dive into hot zones (summaries-view, products)
- **stability-metrics.txt** — Ca/Ce/Instability metrics per namespace

### Layer Boundaries
- **layer-boundaries-summary.md** — Schema.api orphan analysis (executive summary)
- **layer-boundaries-analysis.md** — Full analysis: schema.api, DTOs, validation gaps

### Testability
- **testability-risks-analysis.md** — Testability score ranking + testing strategy per module

### Visualizations
- **namespace-dependencies.dot** — Full project dependency graph (DOT format)
- **god-page-dependencies.dot** — Summaries-view God Page subgraph (DOT)
- **god-page-dependencies.svg** — Rendered visualization (16KB SVG)
- **god-page-visualization.md** — God Page analysis + viewing guide

## Quick Navigation

**Start here:** `../artifact-2-structure.md` (executive summary)

**For specific topics:**
- God Page problem → `god-page-visualization.md`
- Schema.api orphan → `layer-boundaries-summary.md`
- Test strategy → `testability-risks-analysis.md`
- Coupling metrics → `dependency-summary.md`

## Regenerating Visualizations

```bash
# God Page graph
dot -Tsvg god-page-dependencies.dot -o god-page-dependencies.svg

# Full namespace graph (if needed)
dot -Tsvg namespace-dependencies.dot -o namespace-dependencies.svg
```

**Requires:** Graphviz (`sudo apt-get install graphviz` or equivalent)

---

**Generated:** 2026-06-09 to 2026-06-10  
**Method:** clj-kondo analysis + manual synthesis  
**Cross-referenced with:** `../artifact-1-territory.md` (git history)
