# AI Rules for 

Apriary Application

Apriary Summary is an MVP web application designed to automate the process of creating summaries of apiary work. The application is aimed at owners of small apiaries who need a quick and efficient way to document the history of work performed on hives.

## Stack Versions

- **Clojure:** 1.12.0
- **Biff:** v1.9.0 (git/sha 12d4ac6, git/tag v1.9.0)
- **XTDB:** 1.24
- **Tailwind:** 4
- **htmx:** (version managed by Biff)

When referencing Biff documentation, ensure examples match v1.9.0 behavior. If upgrading Biff, review release notes for breaking changes.

## Type Discipline

This project uses **Malli schemas** for data validation at API and database boundaries.

- All database entities are defined in `src/com/apriary/schema.clj` with closed Malli maps.
- All API request/response shapes should be validated against Malli schemas at the handler boundary.
- When adding new entities, define the Malli schema first, then implement CRUD operations.

**Convention:** Public functions that accept or return complex data structures should validate inputs/outputs against a Malli schema. Co-locate schemas with the functions that use them.

## Biff Framework Patterns

This project uses **Biff v1.9.0** (git/sha 12d4ac6), an opinionated Clojure web framework.

**Key conventions:**

- **Pages:** Biff pages live in `src/com/apriary/pages/`. Each page is a function that returns Hiccup (Clojure HTML).
- **Middleware:** Middleware is registered in `src/com/apriary/middleware.clj`. Middleware runs in the order it's listed in the middleware stack.
- **Task runner:** Start the dev server with `clj -M:dev dev`. This runs the Biff task runner, which watches for file changes and reloads the app.
- **Authentication:** Biff includes built-in authentication scaffolding. Users are stored in XTDB with `:user/email` and `:user/password-hash`. Password hashing uses jBCrypt.

**Reference:** https://biffweb.com/docs/

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

## Project Structure

When introducing changes to the project, always follow the directory structure below:

- `./src` - source code
- `./src/com/apriary/pages` - Biff pages
- `./src/com/apriary/middleware.clj` - Biff middleware
- `./src/com//apriary/schema.clj` - XTDB schema definitions
- `./resources/public` - public assets

When modifying the directory structure, always update this section.

## Coding practices

### Guidelines for clean code

- Use feedback from clj-kondo linter to improve the code when making changes.
- Prioritize error handling and edge cases.
- Handle errors and edge cases at the beginning of functions.
- Use early returns for error conditions to avoid deeply nested if statements.
- Place the happy path last in the function for improved readability.
- Avoid unnecessary else statements; use when pattern instead.
- Use guard clauses to handle preconditions and invalid states early.
- Implement proper error logging and user-friendly error messages.
- Consider using custom error types for consistent error handling.

## Frontend

### General Guidelines

Use htmx for interactivity and dynamic content loading.

### Guidelines for Styling

#### Tailwind

- Use the @layer directive to organize styles into components, utilities, and base layers
- Use arbitrary values with square brackets (e.g., w-[123px]) for precise one-off designs
- Implement the Tailwind configuration file for customizing theme, plugins, and variants
- Leverage the theme() function in CSS for accessing Tailwind theme values
- Implement dark mode with the dark: variant
- Use responsive variants (sm:, md:, lg:, etc.) for adaptive designs
- Leverage state variants (hover:, focus-visible:, active:, etc.) for interactive elements

### Guidelines for Accessibility

#### ARIA Best Practices

- Use ARIA landmarks to identify regions of the page (main, navigation, search, etc.)
- Apply appropriate ARIA roles to custom interface elements that lack semantic HTML equivalents
- Set aria-expanded and aria-controls for expandable content like accordions and dropdowns
- Use aria-live regions with appropriate politeness settings for dynamic content updates
- Implement aria-hidden to hide decorative or duplicative content from screen readers
- Apply aria-label or aria-labelledby for elements without visible text labels
- Use aria-describedby to associate descriptive text with form inputs or complex elements
- Implement aria-current for indicating the current item in a set, navigation, or process
- Avoid redundant ARIA that duplicates the semantics of native HTML elements

### Backend and Database

- Use XTDB for database service
- Use Malli schemas to validate data exchanged with the backend.
- use `parse-uuid` to parse UUID strings into UUID objects.
- Use `:db/doc-type` attribute in XTDB schema to define document types
- Use `:db/op` like `:delete`, `:update` for database operations

<!-- BEGIN @przeprogramowani/10x-cli -->

## 10xDevs AI Toolkit - Module 2, Lesson 1

Move from sprint-zero setup to project orchestration with the **roadmap chain**:

```
(Module 1 foundation docs) -> /10x-roadmap -> backlog-ready roadmap items
```

`/10x-roadmap` is the lesson focus. `/10x-new` is intentionally introduced in Module 2, Lesson 2, when a selected roadmap item becomes an implementation change folder.

### Task Router - Where to start

| Skill | Use it when |
| --- | --- |
| **Roadmap (lesson focus)** | |
| `/10x-roadmap` | You have `context/foundation/prd.md` and a scaffolded project baseline, and you need a vertical-first MVP roadmap. The skill reads the PRD, inspects the code baseline, uses available foundation docs such as `tech-stack.md`, `infrastructure.md`, and `deploy-plan.md`, then writes `context/foundation/roadmap.md`. Use it BEFORE creating per-change folders or implementation plans. |
| **Re-run upstream if needed** | |
| `/10x-shape` / `/10x-prd` / `/10x-tech-stack-selector` / `/10x-bootstrapper` / `/10x-agents-md` / `/10x-infra-research` | Bundled from Module 1 so foundation contracts can be fixed before roadmap sequencing. If roadmap generation exposes a PRD gap, repair the PRD before pretending the backlog is ready. |

### How the chain hands off

- `/10x-roadmap` bridges product and implementation. It does not choose frameworks, design schemas, or write a per-change implementation plan.
- The output is `context/foundation/roadmap.md`: ordered milestones, vertical slices, bounded foundations, dependencies, unknowns, risk, and backlog handoff fields.
- Roadmap items should receive stable human-readable identifiers in backlog tools. The actual `context/changes/<change-id>/` folder is created in Lesson 2 with `/10x-new`.

### Roadmap boundaries

- Default to vertical slices: user-visible outcomes that cross UI, data, business logic, and integrations.
- Horizontal work is allowed only as a bounded enabler that names the downstream vertical milestone it unlocks.
- Avoid orphan horizontal work such as "build the whole database", "build all API endpoints", or "design the whole UI" before the first user-visible flow.
- Roadmap is not a calendar estimate. Do not invent dates, story points, or sprint velocity unless the user explicitly asks for a separate planning artifact.

### Foundation paths used by this lesson

- `context/foundation/prd.md` - input
- `context/foundation/tech-stack.md` - optional input
- `context/foundation/infrastructure.md` - optional input
- `context/deployment/deploy-plan.md` - optional input
- `context/foundation/roadmap.md` - output
- `context/foundation/lessons.md` - recurring rules and pitfalls
- `docs/reference/contract-surfaces.md` - load-bearing names registry

Skills must not write to `context/archive/`. Archived changes are immutable; if a resolved target path starts with `context/archive/`, abort with: "This change is archived. Open a new change with `/10x-new` instead."

<!-- END @przeprogramowani/10x-cli -->
