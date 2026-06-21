# AI Toolkit Package — Plan Brief

> Full plan: `context/changes/ai-toolkit/plan.md`
> Research: `context/changes/ai-toolkit/research.md`

## What & Why

Build `@konradszydlo/ai-toolkit` as a self-installing npm package published via GitHub Packages. The package bundles 3 Clojure/Biff-specific skills (code-review, biff-patterns, clojure-style) and team conventions, automatically installing into consumer projects via postinstall hooks. This enables consistent AI-assisted development across team repositories without manual skill/rule distribution.

## Starting Point

Repository (konradszydlo/10x-apriary-cljs) is a Clojure/Biff monolithic project with existing GitHub Actions publishing Docker images to `ghcr.io`. CLAUDE.md exists as a symlink to `.github/copilot-instructions.md` containing Biff/Clojure conventions. No `/packages/` directory or npm package infrastructure exists yet. Research has already mapped architecture, templates exist in `.claude/config-templates/`, and 13 Clojure conventions + 6 Biff patterns have been extracted from the codebase.

## Desired End State

Published npm package (`@konradszydlo/ai-toolkit@0.1.0`) on GitHub Packages registry. Running `npm install @konradszydlo/ai-toolkit` in any consumer project automatically:
1. Creates `.claude/skills/` directory if missing
2. Copies 3 skills (code-review, biff-patterns, clojure-style) to `.claude/skills/`
3. Injects team conventions into `CLAUDE.md` between sentinel markers (`<!-- BEGIN @konradszydlo/ai-toolkit -->`)
4. Writes `.claude/.ai-toolkit-manifest.json` tracking installed files for clean uninstalls

GitHub Actions workflow validates package structure and publishes on master push using `GITHUB_TOKEN` authentication (no manual secrets).

## Key Decisions Made

| Decision                       | Choice                                    | Why (1 sentence)                                                                                      | Source           |
| ------------------------------ | ----------------------------------------- | ----------------------------------------------------------------------------------------------------- | ---------------- |
| Package scope                  | `@konradszydlo/ai-toolkit`                | Matches repo owner for automatic GitHub Packages permissions without manual configuration            | Plan             |
| Skills in v0.1.0               | code-review, biff-patterns, clojure-style | Covers most valuable patterns (conventions, framework, linting) adapted to Clojure/Biff stack        | Plan             |
| Auto-create structure          | Yes                                       | Zero-friction onboarding — installer creates `.claude/` if missing so `npm install` just works       | Plan             |
| Testing strategy               | Temporary test fixtures                   | Isolated, repeatable tests without polluting dev environment; matches Node.js best practices         | Plan             |
| Phasing                        | 3 phases (pack → skills → CI/CD)         | Clear verification points at each step; installer testable locally before publish infrastructure     | Plan             |
| Rules injection                | Sentinel markers in CLAUDE.md             | Idempotent updates preserve user edits outside markers; matches researched pattern                   | Research / Plan  |
| Skill conventions              | Clojure/Biff-specific (not TypeScript)    | Research extracted conventions from actual codebase (error tuples, RLS, Malli, Biff module pattern)  | Research         |

## Scope

**In scope:**
- Package structure (`package.json`, `README.md`, `install.js`, `uninstall.js`)
- 3 skills: code-review (9 Clojure categories), biff-patterns (6 documented patterns), clojure-style (clj-kondo integration)
- Team rules document (`rules/CLAUDE.md`) synthesizing research findings
- Installer auto-creating `.claude/` structure, copying skills, injecting rules via sentinels, writing manifest
- Uninstaller cleaning up tracked files and removing sentinel blocks
- GitHub Actions workflow (validate + publish jobs) replicating `master-docker.yml` pattern
- Temporary test fixtures for installer testing
- Installation guide in README (authentication, manual commands, troubleshooting)

**Out of scope:**
- AWS infrastructure (CodeArtifact, IAM, Terraform) — this is Model 1: GitHub Packages only
- Multi-tool support (Cursor, Copilot) — Claude Code only; deferred to v0.2.0
- Monorepo migration — package lives in `/packages/` but main project stays monolithic
- Consumer `.npmrc` auto-modification — documented in README, not automated
- Independent skill versioning — skills coupled to package version; deferred
- TypeScript conventions — replaced with Clojure/Biff-specific rules

## Architecture / Approach

**Package structure:**
```
packages/ai-toolkit/
├── package.json (GitHub Packages config, postinstall hook)
├── install.js (auto-create structure, copy skills, inject rules, write manifest)
├── uninstall.js (read manifest, delete files, remove sentinel blocks)
├── README.md (installation guide, authentication, troubleshooting)
├── skills/
│   ├── code-review/ (9 Clojure/Biff categories)
│   ├── biff-patterns/ (6 framework patterns)
│   └── clojure-style/ (clj-kondo integration)
└── rules/
    └── CLAUDE.md (team conventions for injection)
```

**Installer flow:**
1. Postinstall hook triggers `install.js`
2. Detect project root (env var → walk up `node_modules` → fallback `cwd`)
3. Create `.claude/skills/` if missing
4. Copy `skills/*` → `.claude/skills/*` (delete old versions first)
5. Follow CLAUDE.md symlink, inject rules between sentinels
6. Write `.claude/.ai-toolkit-manifest.json` tracking installed files

**CI/CD workflow:**
- **Validate job** (push + PR): Check package.json, verify SKILL.md frontmatter, run `npm pack --dry-run`
- **Publish job** (push to master only): Publish with `NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}`
- Replicates `master-docker.yml` pattern (permissions, authentication, conditional publish)

## Phases at a Glance

| Phase     | What it delivers                                          | Key risk                                                      |
| --------- | --------------------------------------------------------- | ------------------------------------------------------------- |
| 1. Package Structure & Installer | Package skeleton, install/uninstall scripts, test fixture | Installer edge cases (symlink following, missing `.claude/`) might not be caught by test fixture |
| 2. Skills Content | 3 SKILL.md files, rules/CLAUDE.md, frontmatter validation | Clojure conventions might not map cleanly to review categories; skill content quality depends on research accuracy |
| 3. CI/CD & Publishing | GitHub Actions workflow, published v0.1.0 package | GitHub Packages permissions could fail if scope doesn't match org; authentication issues in CI |

**Prerequisites:** GitHub repository with `packages:write` permission (already granted per `master-docker.yml`), Node.js >=20 locally, `gh` CLI for workflow testing (optional).

**Estimated effort:** ~2-3 sessions across 3 phases (Phase 1: 1 session, Phase 2: 1 session, Phase 3: 0.5-1 session including publish verification).

## Open Risks & Assumptions

- **CLAUDE.md symlink handling**: Installer must follow symlink (`.github/copilot-instructions.md`) correctly; untested until Phase 1 manual verification
- **GitHub Packages permissions**: Assumes `@konradszydlo` scope auto-inherits permissions from repo owner; might require org-level configuration if scope differs
- **Skill content quality**: 3 skills derived from research extraction — accuracy depends on research completeness (research already validated patterns with file:line references)
- **Consumer CI authentication**: Assumes `GH_PKG_TOKEN` secret setup documented in README is sufficient; third-party CI platforms might have additional requirements
- **Sentinel marker conflicts**: If consumer already uses `<!-- BEGIN @konradszydlo/ai-toolkit -->` markers, installer will overwrite — low probability but documented in README troubleshooting
- **Test fixture coverage**: Temporary test fixture covers happy path; edge cases (permissions, disk space, concurrent installs) tested manually only

## Success Criteria (Summary)

1. **Package published**: `@konradszydlo/ai-toolkit@0.1.0` appears on GitHub Packages at `https://github.com/konradszydlo/10x-apriary-cljs/pkgs/npm/ai-toolkit`
2. **Automatic installation works**: `npm install @konradszydlo/ai-toolkit` in test consumer project creates skills, injects rules, writes manifest without errors
3. **Skills functional**: `/code-review`, `/biff-patterns`, `/clojure-style` commands work in consumer project using installed skills
4. **Idempotent updates**: Re-running `npm install` updates skills/rules without duplication, preserves user edits outside sentinels
5. **Clean uninstall**: Running uninstaller removes all tracked files, sentinel blocks, and manifest; user-edited CLAUDE.md content preserved
6. **CI/CD operational**: GitHub Actions validate job passes on PRs, publish job succeeds on master merge
