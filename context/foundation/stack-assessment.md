---
project: Apriary
assessed_at: 2026-05-29T11:09:09Z
agent_readiness: ready-with-compensation
context_type: brownfield
stack_components:
  language: Clojure 1.12.0
  framework: Biff v1.9.0
  build_tool: Clojure CLI (deps.edn)
  test_runner: cognitect.test-runner
  package_manager: Clojure CLI (Maven coordinates)
  database: XTDB 1.24
  ci_provider: GitHub Actions
  deployment_target: Docker
  linter: clj-kondo
gates_passed: 2
gates_failed: 2
---

## Stack Components

**Language:** Clojure 1.12.0 — a dynamic, functional Lisp dialect on the JVM with strong immutability defaults and a focus on data-oriented programming.

**Framework:** Biff v1.9.0 — an opinionated Clojure web framework that bundles XTDB, htmx, Tailwind, and authentication scaffolding. Designed for solo developers and small teams building full-stack applications.

**Build tool:** Clojure CLI with deps.edn — the canonical Clojure build toolchain. Dependencies are resolved from Maven Central and git repositories.

**Test runner:** cognitect.test-runner — the community-standard test runner for Clojure CLI projects, integrated as an alias in deps.edn.

**Database:** XTDB 1.24 — a bitemporal, document-oriented database with Datalog query support. XTDB schemas are defined using Malli (found in `src/com/apriary/schema.clj`).

**CI/CD:** GitHub Actions — two workflows detected (`master-docker.yml`, `pull-request.yml`).

**Deployment:** Docker — `Dockerfile` and `docker-compose.yml` configurations present.

**Linter:** clj-kondo — the standard Clojure linter with configuration in `.clj-kondo/`.

**Instruction files:** `CLAUDE.md` and `.github/copilot-instructions.md` provide project-specific guidance.

## Quality Gate Assessment

| Component   | Typed | Convention | Training Data | Documented | Verdict         |
|-------------|-------|------------|---------------|------------|-----------------|
| Language    | ~     | —          | ✓             | —          | partial (typed) |
| Framework   | —     | ✓          | ✗             | ~          | partial overall |
| Build tool  | —     | ✓          | ✓             | ✓          | pass            |
| Test runner | —     | —          | ✓             | ✓          | pass            |
| Database    | —     | ✓          | ~             | ✓          | partial (training) |

**Legend:** ✓ = pass, ✗ = fail, ~ = partial, — = not applicable

### Gate Details

#### Gate 1: Typed

**Language (Clojure): ~ Partial**

Clojure is dynamically typed by default, BUT this project uses **Malli schemas** for data validation at API and database boundaries (evidenced in `src/com/apriary/schema.clj`). Malli provides runtime schema validation and is the community-standard approach for typed contracts in Clojure.

**Evidence:**
- `deps.edn` does not declare explicit static type-checking tools (no `clojure.spec.alpha` or Typed Clojure).
- `src/com/apriary/schema.clj` defines closed Malli maps for `:user`, `:generation`, `:summary`, and `:password-reset-token` entities with explicit field types (`:uuid`, `:string`, `inst?`, `:int`, `:enum`).
- Malli provides **runtime** type safety, not **static** type checking. Agents can reason about data shapes from schema definitions, but cannot infer types from arbitrary function signatures.

**Verdict:** Partial pass. The project has typed contracts at data boundaries (Malli schemas), which is the Clojure community's recommended practice, but lacks full static typing across the codebase.

#### Gate 2: Convention-based

**Framework (Biff): ✓ Pass**

Biff is an **opinionated** framework with strong conventions:
- File-based routing via Biff pages (`src/com/apriary/pages/`)
- Middleware stack in `src/com/apriary/middleware.clj`
- Schema definitions in `src/com/apriary/schema.clj`
- Task runner convention (`dev` alias → `com.biffweb.task-runner`)
- Authentication scaffolding built-in

**Evidence:**
- `CLAUDE.md` lines 17-24 document the expected directory structure: `./src/com/apriary/pages`, `./src/com/apriary/middleware.clj`, `./src/com/apriary/schema.clj`.
- Biff's convention-over-configuration approach is evident in the `deps.edn` aliases (`:dev`, `:test`, `:prod`) and JVM opts.

**Build tool (Clojure CLI): ✓ Pass**

Clojure CLI with deps.edn follows strong community conventions:
- Dependencies in `:deps` map with Maven coordinates or git coordinates
- Aliases for development (`:dev`), testing (`:test`), and production (`:prod`)
- Standard project layout (`src/`, `resources/`, `test/`)

**Database (XTDB): ✓ Pass**

XTDB with Malli schemas follows a convention-based pattern:
- `:db/doc-type` attribute to distinguish entity types
- `:xt/id` as the primary identifier
- Malli closed maps for schema validation

**Verdict:** Pass. The framework, build tool, and database all follow strong conventions documented in the project.

#### Gate 3: Popular in training data

**Language (Clojure): ✓ Pass**

Clojure is a well-established language (launched 2007) with significant representation in training data. Major Clojure libraries and idioms (Ring, Compojure, Re-frame, core.async) are well-documented in open-source codebases.

**Evidence:**
- Clojure's functional programming patterns, immutable data structures, and Lisp syntax are distinctive and well-represented in training corpora.
- The language is popular within the functional programming community, though smaller than mainstream ecosystems (JS, Python, Java).

**Per-language-family assessment:** Within the JVM functional programming family, Clojure is the dominant choice. Comparing Clojure's absolute volume to JavaScript or Python is unfair — within its niche (functional, hosted on JVM), it's the leader.

**Framework (Biff): ✗ Fail**

Biff is a **niche** framework within the Clojure ecosystem. While it bundles best-practice defaults (XTDB, htmx, Tailwind), it has limited training data representation compared to more established Clojure web frameworks.

**Evidence:**
- Biff's GitHub repository (jacobobryant/biff) is less popular than Clojure's Ring (web server abstraction) or Luminus (application template).
- Biff is designed for solo developers and small teams, not large-scale production deployments, limiting its visibility in open-source codebases.
- Few Stack Overflow questions or blog posts compared to Ring/Compojure.

**Per-language-family assessment:** Within Clojure web frameworks, Ring + Compojure are more popular in training data. Biff is a newer, opinionated layer on top of Ring but lacks the same training corpus.

**Build tool (Clojure CLI): ✓ Pass**

Clojure CLI (deps.edn) is the **community-standard** build tool for Clojure projects. It has largely replaced Leiningen (project.clj) as the recommended approach for new projects.

**Database (XTDB): ~ Partial**

XTDB (formerly Crux) is a **specialized** database within the Clojure ecosystem. It has good documentation and a dedicated community, but less training data representation than mainstream databases (PostgreSQL, MySQL, MongoDB).

**Evidence:**
- XTDB's bitemporal and Datalog features are well-documented in its official docs.
- Limited Stack Overflow presence compared to relational databases.
- XTDB is popular within Clojure projects but niche globally.

**Per-language-family assessment:** Within Clojure databases, XTDB is a strong choice alongside Datomic. It's not niche within Clojure, but it's not as ubiquitous as PostgreSQL in training data.

**Test runner (cognitect.test-runner): ✓ Pass**

Community-standard test runner for Clojure CLI projects. Well-represented in Clojure project templates and training data.

**Verdict:** Mixed. Language, build tool, and test runner pass. Framework (Biff) fails. Database (XTDB) is partial.

#### Gate 4: Well-documented

**Framework (Biff): ~ Partial**

Biff has official documentation at https://biffweb.com/, including a tutorial, reference guide, and example projects. However, the docs are **not versioned** — there's a single set of docs for the current version, making it harder to match docs to older versions.

**Evidence:**
- Biff docs exist and are current for v1.9.0.
- No versioned docs archive (e.g., no "v1.8 docs" vs "v1.9 docs" distinction).
- Limited third-party tutorials or Stack Overflow answers compared to larger frameworks.

**Verdict:** Partial pass. Docs exist and are current, but lack version pinning.

**Database (XTDB): ✓ Pass**

XTDB has comprehensive, versioned official documentation at https://docs.xtdb.com/, with separate docs for v1.x and v2.x. API reference, tutorials, and guides are all current.

**Build tool (Clojure CLI): ✓ Pass**

Official Clojure CLI documentation at https://clojure.org/guides/deps_and_cli is comprehensive and versioned.

**Test runner (cognitect.test-runner): ✓ Pass**

Documentation in the GitHub repository is clear and current.

**Verdict:** Database, build tool, and test runner pass. Framework (Biff) is partial.

## Gaps & Compensation

### Gap 1: Dynamic typing (Language — Gate 1 Partial)

**What failed:** Clojure is dynamically typed. While this project uses Malli schemas at data boundaries, there's no static type checking across the codebase.

**Why it matters for agent workflows:** Agents reason more effectively when they can infer types from function signatures and intermediate values. Without static types, the agent must rely on naming conventions, comments, and context to understand data shapes.

**Compensation strategy:**

1. **Expand Malli schema coverage** — Add schemas for all API request/response shapes, not just database entities.
2. **Document type conventions** — Add a "Type discipline" section to CLAUDE.md naming the convention: "All public function inputs and outputs are validated against Malli schemas at the function boundary."
3. **Schema co-location** — Keep Malli schemas close to the functions they validate (e.g., in the same namespace), so agents can easily locate them.

### Gap 2: Niche framework (Framework — Gate 3 Fail)

**What failed:** Biff is a niche framework within the Clojure ecosystem. It has limited representation in training data compared to Ring + Compojure.

**Why it matters for agent workflows:** Agents are less fluent with Biff-specific idioms and conventions. They may generate code that matches Ring patterns but misses Biff's opinions (e.g., task runner, authentication scaffolding).

**Compensation strategy:**

1. **Document Biff conventions explicitly** — Expand CLAUDE.md to include Biff-specific patterns:
   - Middleware registration order
   - Page handler signature (what Biff expects from a page function)
   - Task runner usage (`clj -M:dev dev` pattern)
   - Authentication integration (how Biff's built-in auth works)
2. **Link to Biff docs** — Add a "Framework reference" section to CLAUDE.md with direct links to https://biffweb.com/docs/.
3. **Include inline examples** — Add code snippets showing common Biff patterns (e.g., a typical page handler, a typical middleware function).

### Gap 3: Framework documentation not versioned (Framework — Gate 4 Partial)

**What failed:** Biff docs exist but are not versioned. If you're on v1.8 and the docs show v1.9 examples, there's no easy way to see v1.8-specific docs.

**Why it matters for agent workflows:** Agents may generate code that matches current Biff examples but is incompatible with the version pinned in deps.edn.

**Compensation strategy:**

1. **Pin Biff version in CLAUDE.md** — Explicitly state: "This project uses Biff v1.9.0 (git/sha 12d4ac6)."
2. **Note version-specific quirks** — If you encounter version-specific behavior, document it in CLAUDE.md (e.g., "In Biff v1.9, X changed from Y to Z").

### Gap 4: Specialized database (Database — Gate 3 Partial)

**What failed:** XTDB is well-documented but has less training data representation than mainstream databases like PostgreSQL.

**Why it matters for agent workflows:** Agents are less fluent with XTDB's Datalog query syntax and bitemporal semantics. They may generate queries that work in SQL but are incorrect for XTDB.

**Compensation strategy:**

1. **Document XTDB query patterns** — Add a "Database queries" section to CLAUDE.md with examples of common XTDB operations:
   - Entity lookup by ID
   - Query with `xt/q` and Datalog syntax
   - Temporal queries (as-of, history)
2. **Schema reference** — Link to `src/com/apriary/schema.clj` in CLAUDE.md so agents know where to find entity definitions.
3. **Prefer Malli over raw XTDB** — Emphasize that all database operations should validate against Malli schemas before writing to XTDB.

## Recommended Instruction File Additions

Add the following sections to `CLAUDE.md` to compensate for identified gaps:

### Type discipline (compensates Gap 1)

```markdown
## Type Discipline

This project uses **Malli schemas** for data validation at API and database boundaries.

- All database entities are defined in `src/com/apriary/schema.clj` with closed Malli maps.
- All API request/response shapes should be validated against Malli schemas at the handler boundary.
- When adding new entities, define the Malli schema first, then implement CRUD operations.

**Convention:** Public functions that accept or return complex data structures should validate inputs/outputs against a Malli schema. Co-locate schemas with the functions that use them.
```

### Biff framework patterns (compensates Gap 2)

```markdown
## Biff Framework Patterns

This project uses **Biff v1.9.0** (git/sha 12d4ac6), an opinionated Clojure web framework.

**Key conventions:**

- **Pages:** Biff pages live in `src/com/apriary/pages/`. Each page is a function that returns Hiccup (Clojure HTML).
- **Middleware:** Middleware is registered in `src/com/apriary/middleware.clj`. Middleware runs in the order it's listed in the middleware stack.
- **Task runner:** Start the dev server with `clj -M:dev dev`. This runs the Biff task runner, which watches for file changes and reloads the app.
- **Authentication:** Biff includes built-in authentication scaffolding. Users are stored in XTDB with `:user/email` and `:user/password-hash`. Password hashing uses jBCrypt.

**Reference:** https://biffweb.com/docs/
```

### XTDB query patterns (compensates Gap 4)

```markdown
## XTDB Query Patterns

This project uses **XTDB 1.24** as its database. XTDB is a bitemporal, document-oriented database with Datalog query support.

**Entity schemas:** All entity schemas are defined in `src/com/apriary/schema.clj` using Malli.

**Common query patterns:**

1. **Lookup by ID:**
   ```clojure
   (xt/entity db [:xt/id user-id])
   ```

2. **Query with Datalog:**
   ```clojure
   (xt/q db
         '{:find [user-id email]
           :where [[user-id :user/email email]]})
   ```

3. **Row-Level Security (RLS):**
   All queries for user-owned data must filter by `:user-id`:
   ```clojure
   (xt/q db
         '{:find [summary-id content]
           :in [user-id]
           :where [[summary-id :summary/user-id user-id]
                   [summary-id :summary/content content]]}
         current-user-id)
   ```

**Convention:** Always validate data against Malli schemas before writing to XTDB. Use `:db/op` (`:delete`, `:update`) for database operations.
```

### Version pinning (compensates Gap 3)

```markdown
## Stack Versions

- **Clojure:** 1.12.0
- **Biff:** v1.9.0 (git/sha 12d4ac6, git/tag v1.9.0)
- **XTDB:** 1.24
- **Tailwind:** 4
- **htmx:** (version managed by Biff)

When referencing Biff documentation, ensure examples match v1.9.0 behavior. If upgrading Biff, review release notes for breaking changes.
```

## Summary

**Overall verdict:** Ready with compensation

Your stack meets 2 out of 4 quality gates cleanly (convention-based, well-documented for most components) and partially passes 2 gates (typed via Malli, training data for database). The main gaps are:

1. **Dynamic typing** — Mitigated by Malli schemas at boundaries, but lacks full static type checking.
2. **Niche framework** — Biff has limited training data representation. Compensation via explicit documentation of Biff conventions.

**Key strengths:**

- **Convention-based:** Biff, Clojure CLI, and XTDB all follow strong conventions. Folder structure is predictable.
- **Type safety at boundaries:** Malli schemas provide runtime validation for all database entities and (when added) API shapes.
- **Existing instruction files:** CLAUDE.md and .github/copilot-instructions.md already provide project context.
- **Linting:** clj-kondo ensures code quality.

**Key gaps:**

- **Training data:** Biff is niche within Clojure; XTDB is specialized. Agents will need more steering than with mainstream frameworks.
- **Versioned docs:** Biff docs are not versioned, requiring explicit version pinning in instruction files.

**Recommended next step:** Run `/10x-health-check` to audit dependency health, test suite coverage, and CI/CD robustness.
