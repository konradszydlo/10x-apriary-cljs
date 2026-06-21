# AI Toolkit Package Implementation Plan

## Overview

Build and publish `@konradszydlo/ai-toolkit` as a self-installing npm package distributed via GitHub Packages. The package bundles 3 Clojure/Biff-specific skills (code-review, biff-patterns, clojure-style) and team conventions, automatically installing into consumer projects via postinstall hooks.

## Current State Analysis

**Existing infrastructure:**
- Repository: konradszydlo/10x-apriary-cljs (Clojure/Biff monolithic project, NOT a monorepo)
- GitHub Actions: `.github/workflows/master-docker.yml` already uses `ghcr.io` with `packages:write` permission
- CLAUDE.md: Symlink to `.github/copilot-instructions.md` containing Biff/Clojure conventions
- Linting: `.clj-kondo/config.edn` with configured rules
- No existing `/packages/` directory structure

**Research findings:**
- Comprehensive GitHub Packages architecture researched (`context/changes/ai-toolkit/research.md`)
- 13 Clojure-specific coding conventions extracted from codebase
- 6 Biff framework patterns documented (module pattern, RLS, transactions, middleware, Hiccup)
- Template files available in `.claude/config-templates/` for m5l4 patterns
- Existing `ghcr.io` workflow provides pattern for GitHub Packages authentication

## Desired End State

A published npm package (`@konradszydlo/ai-toolkit@0.1.0`) available on GitHub Packages registry that:

1. **Automatic installation**: `npm install @konradszydlo/ai-toolkit` runs postinstall hook
2. **Skills deployed**: 3 skills copied to consumer's `.claude/skills/` directory
3. **Rules injected**: Team conventions inserted into `CLAUDE.md` via sentinel markers
4. **Manifest tracked**: `.claude/.ai-toolkit-manifest.json` enables clean uninstalls
5. **CI/CD operational**: GitHub Actions validates and publishes on master push
6. **Idempotent updates**: Re-installing updates skills/rules without duplication

**Verification**: `npm pack --dry-run` succeeds, validation workflow passes, manual `npm install` test in separate repo works.

### Key Discoveries:

- **CLAUDE.md symlink** (`CLAUDE.md` → `.github/copilot-instructions.md:1-105`): Installer must follow symlink or inject to target file
- **Error tuple pattern** (`src/com/apriary/util.clj:7-52`): `[:ok value]` / `[:error {:code :message}]` convention used throughout codebase — critical for code-review skill
- **RLS enforcement** (`src/com/apriary/services/generation.clj:155-171`): All XTDB queries filter by `:user-id` — key security pattern to document
- **Existing packages permission** (`.github/workflows/master-docker.yml:39-42`): `packages: write` already granted, can reuse pattern
- **Biff module pattern** (`src/com/apriary/pages/app.clj:8-10`): Maps with `:routes` and optional `:api-routes` — most referenced pattern

## What We're NOT Doing

- **No AWS infrastructure**: No CodeArtifact, IAM roles, or Terraform (this is Model 1: GitHub Packages only)
- **No TypeScript conventions**: Research templates reference TypeScript; we're replacing with Clojure/Biff-specific rules
- **No monorepo migration**: Package lives in `/packages/ai-toolkit/` but main project stays monolithic
- **No multi-tool support yet**: Claude Code only; Cursor/Copilot expansion deferred to v0.2.0
- **No consumer .npmrc modification**: Installer doesn't auto-add registry mapping (documented in README)
- **No skill versioning**: Skills coupled to package version (v0.1.0); independent versioning deferred

## Implementation Approach

**3-phase incremental approach** with clear verification points:

1. **Phase 1**: Package structure + installer logic (test locally with temp fixture)
2. **Phase 2**: Skills content (Clojure/Biff-specific, verified via frontmatter validation)
3. **Phase 3**: CI/CD workflow (replicate master-docker.yml pattern for npm publish)

Each phase has both automated (commands/scripts) and manual (UI/behavior) verification criteria. Pause for manual confirmation between phases to catch issues early.

**Key architectural decisions:**
- **Scope**: `@konradszydlo/ai-toolkit` (matches repo owner for automatic GitHub Packages permissions)
- **Auto-create structure**: Installer creates `.claude/skills/` and `CLAUDE.md` if missing (zero-friction onboarding)
- **Sentinel injection**: `<!-- BEGIN @konradszydlo/ai-toolkit -->` markers preserve user edits
- **Test strategy**: Temporary test fixtures in temp directories (no dev environment pollution)

## Phase 1: Package Structure & Installer

### Overview

Create the npm package skeleton with install/uninstall scripts that handle `.claude/` directory creation, skill deployment, rules injection via sentinels, and manifest tracking.

### Changes Required:

#### 1. Package Directory Structure

**File**: `packages/ai-toolkit/` (new directory)

**Intent**: Scaffold the package directory structure separate from main Clojure project. This makes the package extractable and publishable independently.

**Contract**: Directory structure matching research spec:
```
packages/ai-toolkit/
├── package.json
├── README.md
├── install.js
├── uninstall.js
├── skills/
│   ├── code-review/
│   ├── biff-patterns/
│   └── clojure-style/
└── rules/
    └── CLAUDE.md
```

#### 2. Package Metadata

**File**: `packages/ai-toolkit/package.json`

**Intent**: Define package metadata, GitHub Packages registry configuration, published files whitelist, and postinstall hook trigger.

**Contract**: JSON with required fields:
- `name`: `@konradszydlo/ai-toolkit`
- `version`: `0.1.0`
- `publishConfig.registry`: `https://npm.pkg.github.com`
- `files`: `["skills/", "rules/", "install.js", "uninstall.js", "README.md"]`
- `scripts.postinstall`: `node install.js`
- `bin`: `{"ai-toolkit": "./install.js"}` (enables `npx @konradszydlo/ai-toolkit install`)
- `type`: `commonjs`
- `engines.node`: `>=20`

#### 3. Installer Script

**File**: `packages/ai-toolkit/install.js`

**Intent**: Postinstall hook that auto-creates `.claude/` structure, copies skills, injects rules between sentinel markers, and writes manifest. Must be idempotent (safe to run multiple times) and fail gracefully in CI environments.

**Contract**: Node.js script with:
1. **Project root detection**: Check `PROJECT_ROOT` env var → walk up from `node_modules` → fallback to `process.cwd()`
2. **Directory creation**: `fs.mkdirSync('.claude/skills/', {recursive: true})` if missing
3. **Skills copy**: Delete old `skill-name/` first, then copy from `skills/*` to `.claude/skills/*`
4. **CLAUDE.md injection**:
   - Follow symlink if present (`fs.realpathSync()`)
   - Search for `<!-- BEGIN @konradszydlo/ai-toolkit -->` sentinel
   - Replace block if found, append if not
   - Preserve all content outside sentinels
5. **Manifest write**: `JSON.stringify({package, version, installedAt, files})` to `.claude/.ai-toolkit-manifest.json`
6. **Error handling**: `try/catch` wrapper, `console.warn()` on failure, `process.exit(0)` to not break `npm install`

#### 4. Uninstaller Script

**File**: `packages/ai-toolkit/uninstall.js`

**Intent**: Cleanup script that reads manifest, deletes tracked files, removes rules block via sentinels, and cleans up manifest. Can be invoked manually or via package removal.

**Contract**: Node.js script with:
1. **Manifest read**: `JSON.parse(fs.readFileSync('.claude/.ai-toolkit-manifest.json'))`
2. **File deletion**: Iterate `manifest.files`, `fs.rmSync(file, {force: true})`
3. **Rules removal**: Replace sentinel block with empty string, clean extra newlines (`/\n{3,}/g` → `\n\n`)
4. **Manifest cleanup**: `fs.unlinkSync('.claude/.ai-toolkit-manifest.json')`
5. **Safety**: Skip if manifest not found (nothing to uninstall)

#### 5. Installation Guide

**File**: `packages/ai-toolkit/README.md`

**Intent**: Document installation, authentication setup, and manual invocation for consumers.

**Contract**: Markdown file with sections:
- **Installation**: `npm install @konradszydlo/ai-toolkit`
- **Authentication**: `.npmrc` configuration (`@konradszydlo:registry=https://npm.pkg.github.com`), local `npm login`, CI `GH_PKG_TOKEN` setup
- **Manual commands**: `npx @konradszydlo/ai-toolkit install` / `uninstall` (requires `bin` entry in package.json)
- **What gets installed**: List of skills and rules injected
- **Troubleshooting**: Common issues (permissions, missing `.claude/`, conflicting skills)

#### 6. Installer Test Fixture

**File**: `packages/ai-toolkit/test/install.test.js` (new file)

**Intent**: Automated test that creates temporary consumer project structure, runs installer, and verifies skills/rules/manifest are created correctly. Prevents regressions and enables local verification without polluting dev environment.

**Contract**: Node.js test (using built-in `node:test` or simple script) that:
1. **Setup**: `fs.mkdtempSync()` creates temp directory
2. **Mock structure**: Create fake `node_modules/@konradszydlo/ai-toolkit/` with test package
3. **Run installer**: Execute `install.js` in temp context
4. **Assertions**:
   - `.claude/skills/code-review/SKILL.md` exists
   - `CLAUDE.md` contains sentinel block
   - `.claude/.ai-toolkit-manifest.json` exists with expected fields
5. **Teardown**: `fs.rmSync(tmpDir, {recursive: true, force: true})`

### Success Criteria:

#### Automated Verification:

- Package structure validated: `test -d packages/ai-toolkit && test -f packages/ai-toolkit/package.json`
- Package.json parses: `node -e "JSON.parse(require('fs').readFileSync('packages/ai-toolkit/package.json'))"`
- Install script has no syntax errors: `node --check packages/ai-toolkit/install.js`
- Uninstall script has no syntax errors: `node --check packages/ai-toolkit/uninstall.js`
- Test fixture passes: `node packages/ai-toolkit/test/install.test.js`
- npm pack succeeds: `cd packages/ai-toolkit && npm pack --dry-run`

#### Manual Verification:

- Create a test consumer project in `/tmp/test-consumer`
- Add `.npmrc` with registry mapping
- Run `npm install packages/ai-toolkit/` (local file path)
- Verify `.claude/skills/` directory created (even if empty at this phase)
- Verify `CLAUDE.md` has sentinel markers with placeholder content
- Verify `.claude/.ai-toolkit-manifest.json` exists
- Run uninstaller: `node packages/ai-toolkit/uninstall.js`
- Verify sentinel block removed, skills deleted, manifest gone

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Skills Content

### Overview

Create the 3 Clojure/Biff-specific skills (code-review, biff-patterns, clojure-style) and team conventions document. Each skill gets YAML frontmatter with proper metadata and content derived from research findings.

### Changes Required:

#### 1. Code Review Skill

**File**: `packages/ai-toolkit/skills/code-review/SKILL.md`

**Intent**: Automated code review skill enforcing Clojure/Biff team conventions. Categories mapped to research findings: Naming, Error Handling, Guards & Flow, Validation, RLS/Security, Biff Patterns, XTDB Queries, Testing, Linting.

**Contract**: YAML frontmatter + skill body:

```yaml
---
name: code-review
description: Review Clojure/Biff code changes against team engineering conventions, testing standards, and security expectations. Use when asked to review code, check a PR, or audit code quality.
allowed-tools:
  - Read
  - Bash
  - Glob
  - Grep
---
```

**Body sections**:
1. **Role statement**: Review code changes against Clojure/Biff conventions
2. **When to use**: "review code", "check this PR", "review my changes", "code review"
3. **Review categories** (9 dimensions from research):
   - **Naming**: Kebab-case, predicates with `?`, private with `-`
   - **Error Handling**: Two patterns - `[:ok val]` / `[:error map]` tuples (services layer: `util.clj`, `generation.clj`, `summary.clj`) AND exception-throwing (`product_rankings.clj:32`, `csv_import.clj:37`, `product.clj:45`). Both are legitimate.
   - **Guards & Flow**: Early returns, happy path last, avoid `else`
   - **Validation**: Malli closed maps, type predicates
   - **RLS/Security**: All queries filter by `:user-id`, jBCrypt passwords, SecureRandom tokens
   - **Biff Patterns**: Module `:routes`, context destructuring, 303 redirects
   - **XTDB Queries**: `:xt/id`, `:db/doc-type`, `:db/op`
   - **Testing**: `[status result]` destructuring, `clojure.test` macros
   - **Linting**: clj-kondo errors (redefined, invalid arity)
4. **Output format**: Findings sorted by severity (Critical → Warning → Observation), `file:line` references
5. **Final recommendation**: `APPROVE` / `REQUEST CHANGES` / `NEEDS DISCUSSION`

#### 2. Biff Patterns Skill

**File**: `packages/ai-toolkit/skills/biff-patterns/SKILL.md`

**Intent**: Document the 6 key Biff framework patterns used in this project. Reference skill when implementing new features or debugging Biff-specific issues.

**Contract**: YAML frontmatter + skill body:

```yaml
---
name: biff-patterns
description: Document Biff framework patterns used in this project. Use when implementing new Biff modules, debugging middleware, or understanding XTDB queries.
allowed-tools:
  - Read
  - Grep
  - Bash
---
```

**Body sections**:
1. **Module pattern** (`src/com/apriary/pages/app.clj:8-10`): Map with `:routes`, `:api-routes`, middleware application
   - **Module variations**: Three edge cases - `{:schema schema}` for schema modules (`schema.clj:62`), empty `{}` when routes delegated to views (`summaries.clj:387`), api-only modules with `:api-routes` but no `:routes` (`generations.clj:119`)
2. **RLS query pattern** (`src/com/apriary/services/summary.clj:88-117`, `155-171`): Filter by `:user-id`, ownership checks after `xt/entity`
3. **Transaction pattern** (`src/com/apriary/auth.clj:82-92`): `biff/submit-tx` in handlers vs `xt/submit-tx` in services
4. **Middleware stacks** (`src/com/apriary/middleware.clj:34-64`): Site/API/Base defaults composition
5. **Malli schemas** (`src/com/apriary/schema.clj:5-37`): Closed maps with `:db/doc-type`
6. **Hiccup pages** (`src/com/apriary/pages/home.clj:9-41`): `biff/form`, Tailwind classes, fragments

Each pattern includes file:line references and 1-2 code examples.

#### 3. Clojure Style Skill

**File**: `packages/ai-toolkit/skills/clojure-style/SKILL.md`

**Intent**: Enforce Clojure-specific style rules beyond code-review: linting integration, formatting conventions, namespace organization.

**Contract**: YAML frontmatter + skill body:

```yaml
---
name: clojure-style
description: Enforce Clojure formatting and linting rules. Use when checking style compliance or integrating clj-kondo.
allowed-tools:
  - Bash
  - Read
---
```

**Body sections**:
1. **Naming conventions**: Kebab-case files, namespaces mirror directory structure, `_test` suffix for tests
2. **clj-kondo integration**: Run `clj-kondo --lint src/`, interpret warnings/errors
3. **Linting rules** (`.clj-kondo/config.edn:1-18`): Warn on unused bindings, error on redefined vars/invalid arity
4. **Documentation**: Docstrings with params/returns, two-tuple returns documented
5. **Formatting**: 2-space indentation, threading macros for >2 nested calls

#### 4. Team Rules Document

**File**: `packages/ai-toolkit/rules/CLAUDE.md`

**Intent**: Extract team conventions from research into reusable rules document. This gets injected into consumer `CLAUDE.md` between sentinels.

**Contract**: Markdown content synthesizing research findings into concise rules:

Sections:
1. **Naming**: Kebab-case conventions, predicate/private naming
2. **Error Handling**: `[:ok val]` / `[:error {:code :message}]` pattern
3. **Guard Clauses**: Early returns, happy path last
4. **Malli Validation**: Closed maps, type enforcement
5. **Row-Level Security**: All queries filter by `:user-id`
6. **Biff Framework**: Module pattern, middleware, transactions
7. **XTDB**: Entity structure, Datalog queries
8. **Testing**: Test namespaces, result tuple destructuring
9. **Security**: jBCrypt passwords, SecureRandom tokens, prevent enumeration

Length: ~50-80 lines (concise reference, not full documentation).

### Success Criteria:

#### Automated Verification:

- All SKILL.md files exist: `test -f packages/ai-toolkit/skills/code-review/SKILL.md && test -f packages/ai-toolkit/skills/biff-patterns/SKILL.md && test -f packages/ai-toolkit/skills/clojure-style/SKILL.md`
- Frontmatter validation script passes:
  ```bash
  node -e "
    const fs = require('fs');
    const yaml = require('yaml'); // or simple regex if no yaml lib
    const files = ['code-review', 'biff-patterns', 'clojure-style'];
    files.forEach(skill => {
      const content = fs.readFileSync(\`packages/ai-toolkit/skills/\${skill}/SKILL.md\`, 'utf8');
      const match = content.match(/^---\\n([\\s\\S]+?)\\n---/);
      if (!match) throw new Error(\`\${skill}: No frontmatter\`);
      const fm = match[1];
      if (!fm.includes('name:')) throw new Error(\`\${skill}: Missing name\`);
      if (!fm.includes('description:')) throw new Error(\`\${skill}: Missing description\`);
    });
  "
  ```
- Rules document exists: `test -f packages/ai-toolkit/rules/CLAUDE.md`
- npm pack still succeeds: `cd packages/ai-toolkit && npm pack --dry-run`

#### Manual Verification:

- Read `skills/code-review/SKILL.md`: verify 9 categories match Clojure/Biff conventions, not TypeScript
- Read `skills/biff-patterns/SKILL.md`: verify 6 patterns documented with file:line references
- Read `skills/clojure-style/SKILL.md`: verify clj-kondo integration instructions
- Read `rules/CLAUDE.md`: verify concise, covers all research categories
- Re-run installer test: `npm install` in temp consumer project, verify skills copied and rules injected
- Check sentinel block in test consumer's `CLAUDE.md`: contains expected rules content

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: CI/CD & Publishing

### Overview

Create GitHub Actions workflow that validates package structure, verifies skill frontmatter, and publishes to GitHub Packages on master push. Replicates authentication pattern from `master-docker.yml`.

### Changes Required:

#### 1. GitHub Actions Workflow

**File**: `.github/workflows/publish-ai-toolkit.yml`

**Intent**: Two-job pipeline (validate → publish) that checks package integrity before publishing to GitHub Packages. Only publish job runs on master push; validation runs on all PRs/pushes.

**Contract**: YAML workflow with jobs:

**Permissions** (at workflow level, not nested under jobs - note that master-docker.yml has them under `jobs.build-and-push` but we need them at top level):
```yaml
permissions:
  contents: read
  packages: write
```

**Validate job** (runs on push + PR):
- Checkout: `actions/checkout@v6`
- Setup Node: `actions/setup-node@v4` with `node-version: 20`, `registry-url: "https://npm.pkg.github.com"`, `scope: "@konradszydlo"`
- Verify skills exist: `test -f packages/ai-toolkit/skills/code-review/SKILL.md`
- Validate frontmatter: Run script from Phase 2 automated verification
- Dry-run pack: `cd packages/ai-toolkit && npm pack --dry-run`

**Publish job** (runs only on push to master):
- Depends on: `validate` job passing
- Checkout + Setup Node (same as validate)
- Publish: `cd packages/ai-toolkit && npm publish` with `env: NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}`

**Triggers**:
```yaml
on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
```

#### 2. Package Version Bump (if needed)

**File**: `packages/ai-toolkit/package.json`

**Intent**: Ensure version is `0.1.0` for first publish. Future changes increment version before merge.

**Contract**: JSON field `version: "0.1.0"`.

#### 3. Workflow Testing Documentation

**File**: `packages/ai-toolkit/README.md` (append section)

**Intent**: Document how to test the workflow locally and what happens on first publish.

**Contract**: Add section:
- **First Publish**: Merge to master triggers workflow, package appears at `https://github.com/konradszydlo/10x-apriary-cljs/pkgs/npm/ai-toolkit`
- **Local Testing**: Can't fully test publish without push, but `npm pack --dry-run` validates structure
- **Troubleshooting**: Link to GitHub Packages docs, common permission errors

### Success Criteria:

#### Automated Verification:

- Workflow file exists: `test -f .github/workflows/publish-ai-toolkit.yml`
- Workflow syntax valid: `gh workflow view publish-ai-toolkit.yml` (requires `gh` CLI)
- Permissions include packages:write: `grep -q "packages: write" .github/workflows/publish-ai-toolkit.yml`
- NODE_AUTH_TOKEN used: `grep -q "NODE_AUTH_TOKEN" .github/workflows/publish-ai-toolkit.yml`
- Validate job exists: `grep -q "jobs:" .github/workflows/publish-ai-toolkit.yml && grep -q "validate:" .github/workflows/publish-ai-toolkit.yml`
- Publish job conditional: `grep -q "if: github.event_name == 'push'" .github/workflows/publish-ai-toolkit.yml`

#### Manual Verification:

- Create PR with workflow file to trigger validation job (don't merge yet)
- Check GitHub Actions tab: validation job should run and pass
- Review job logs: verify all steps executed (checkout, setup-node, npm ci, test -f, frontmatter check, npm pack)
- Merge PR to master: publish job should trigger
- Check GitHub Packages tab: `@konradszydlo/ai-toolkit@0.1.0` should appear
- Test installation from GitHub Packages:
  1. Create new test repo (or use existing consumer project)
  2. Add `.npmrc`: `@konradszydlo:registry=https://npm.pkg.github.com`
  3. Authenticate: `npm login --registry=https://npm.pkg.github.com` (or set `GH_PKG_TOKEN` in CI)
  4. Install: `npm install @konradszydlo/ai-toolkit`
  5. Verify: Skills in `.claude/skills/`, rules in `CLAUDE.md`, manifest exists

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

**Installer/Uninstaller:**
- Project root detection logic (env var → walk up → fallback)
- Sentinel block replacement (found vs not found)
- Manifest read/write/delete
- CLAUDE.md symlink following
- Error handling (missing permissions, invalid paths)

**Validation:**
- Frontmatter parsing (valid vs invalid YAML)
- Required fields check (`name`, `description`)
- Directory name match (skill dir vs frontmatter name)

### Integration Tests:

**End-to-end install flow:**
1. Create temp consumer project structure
2. Run `npm install` with local package path
3. Verify skills copied, rules injected, manifest created
4. Modify consumer's `CLAUDE.md` manually (add user content)
5. Re-run `npm install` (simulating update)
6. Verify sentinel block updated, user content preserved
7. Run uninstaller
8. Verify complete cleanup

**GitHub Actions workflow:**
- PR triggers validation job (test in real PR)
- Push to master triggers publish job
- Published package installable from GitHub Packages

### Manual Testing Steps:

1. **Installer in clean environment**:
   - Test consumer with no `.claude/` directory
   - Verify auto-creation of structure
   - Check permissions (installer should not fail)

2. **Installer in existing environment**:
   - Test consumer with existing `.claude/skills/`
   - Verify old skills replaced, new ones added
   - Check existing `CLAUDE.md` preserved outside sentinels

3. **Uninstaller**:
   - Install toolkit, manually edit `CLAUDE.md` outside sentinels
   - Run uninstaller
   - Verify toolkit content removed, user edits preserved

4. **GitHub Packages authentication**:
   - Test `npm login` flow for local development
   - Test `GH_PKG_TOKEN` env var for CI
   - Verify permissions error if not authenticated

5. **Skills functionality**:
   - Install toolkit in real project
   - Invoke `/code-review` on Clojure file
   - Verify skill categories match Clojure conventions (not TypeScript)

6. **Workflow validation**:
   - Create PR changing package.json version
   - Verify validation job catches version issues
   - Check frontmatter validation fails with broken SKILL.md

## Performance Considerations

- **Installer speed**: Copying skills/rules should be near-instant (<100ms for 3 small files)
- **npm pack size**: Target <50KB for package (skills + installer scripts)
- **Workflow runtime**: Validate job should complete in <2 min, publish in <3 min

## Migration Notes

**First installation in consumer projects:**
1. Add `.npmrc` with registry mapping (manual step, documented in README)
2. Authenticate via `npm login` or set `GH_PKG_TOKEN`
3. Run `npm install @konradszydlo/ai-toolkit`
4. Verify skills available via `/code-review`, `/biff-patterns`, `/clojure-style`

**Upgrading from v0.1.0 to future versions:**
- Installer is idempotent: `npm install @konradszydlo/ai-toolkit@latest` updates skills/rules
- Sentinel block replaced, manifest version updated
- No manual cleanup needed

**Uninstalling:**
- Manual: `node node_modules/@konradszydlo/ai-toolkit/uninstall.js`
- Or remove from package.json + `npm uninstall @konradszydlo/ai-toolkit`

## References

- **Research**: `context/changes/ai-toolkit/research.md`
- **GitHub Packages spec**: `.claude/prompts/m5l4-github-packages-spec-pack.md`
- **CI/CD spec**: `.claude/prompts/m5l4-github-packages-spec-cicd.md`
- **Skill spec**: `.claude/prompts/m5l4-shared-spec-skill.md`
- **Conventions**: `.claude/prompts/m5l4-shared-conventions.md`
- **Existing workflow pattern**: `.github/workflows/master-docker.yml:39-104`
- **Clojure conventions**: `.github/copilot-instructions.md:1-105`
- **Biff patterns**: `src/com/apriary/pages/app.clj:8-10`, `src/com/apriary/middleware.clj:34-64`, `src/com/apriary/services/summary.clj:88-117`
- **clj-kondo config**: `.clj-kondo/config.edn:1-18`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Package Structure & Installer

#### Automated

- [x] 1.1 Package structure validated — 23b0683
- [x] 1.2 Package.json parses — 23b0683
- [x] 1.3 Install script has no syntax errors — 23b0683
- [x] 1.4 Uninstall script has no syntax errors — 23b0683
- [x] 1.5 Test fixture passes — 23b0683
- [x] 1.6 npm pack succeeds — 23b0683

#### Manual

- [x] 1.7 Manual installer test in /tmp/test-consumer — 23b0683

### Phase 2: Skills Content

#### Automated

- [ ] 2.1 All SKILL.md files exist
- [ ] 2.2 Frontmatter validation script passes
- [ ] 2.3 Rules document exists
- [ ] 2.4 npm pack still succeeds

#### Manual

- [ ] 2.5 Skills content reviewed (Clojure-specific, not TypeScript)
- [ ] 2.6 Installer test with real skills

### Phase 3: CI/CD & Publishing

#### Automated

- [ ] 3.1 Workflow file exists
- [ ] 3.2 Workflow syntax valid
- [ ] 3.3 Permissions include packages:write
- [ ] 3.4 NODE_AUTH_TOKEN used
- [ ] 3.5 Validate job exists
- [ ] 3.6 Publish job conditional

#### Manual

- [ ] 3.7 PR validation job runs and passes
- [ ] 3.8 Publish job triggers on master merge
- [ ] 3.9 Package appears on GitHub Packages
- [ ] 3.10 Installation from GitHub Packages works
