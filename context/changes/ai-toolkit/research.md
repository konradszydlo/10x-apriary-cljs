---
date: 2026-06-21T22:45:00+01:00
researcher: Claude Sonnet 4.5
git_commit: e0f4b627f54e490a0d49b11677e8e467c5f1fc21
branch: master
repository: konradszydlo/10x-apriary-cljs
topic: "Building minimal team AI toolkit package using GitHub Packages"
tags: [research, codebase, github-packages, ai-toolkit, npm-package, m5l4]
status: complete
last_updated: 2026-06-21
last_updated_by: Claude Sonnet 4.5
---

# Research: Building Minimal Team AI Toolkit Package Using GitHub Packages

**Date**: 2026-06-21T22:45:00+01:00
**Researcher**: Claude Sonnet 4.5
**Git Commit**: e0f4b627f54e490a0d49b11677e8e467c5f1fc21
**Branch**: master
**Repository**: konradszydlo/10x-apriary-cljs

## Research Question

How to build a minimal team AI toolkit package using GitHub Packages (Model 1 from m5l4 lesson) that bundles skills and rules for distribution to team repositories?

## Summary

The research identified a complete architecture for creating a self-installing npm package that distributes AI artifacts (skills, rules, prompts) via GitHub Packages. The Model 1 approach uses:

- **Package structure**: `@twoj-zespol/ai-toolkit` published to `https://npm.pkg.github.com`
- **Installer pattern**: Postinstall hook (`install.js`) that copies skills to `.claude/skills/`, injects rules into `CLAUDE.md` using sentinel markers, and tracks installed files in a manifest
- **Authentication**: Ephemeral `GITHUB_TOKEN` for publishing, `GH_PKG_TOKEN` for consumer CI
- **CI/CD**: GitHub Actions workflow with validation and publish jobs
- **Zero AWS dependencies**: No CodeArtifact, IAM roles, or Terraform required

Key architectural decisions include sentinel-based idempotent updates, manifest tracking for clean uninstalls, and soft error handling to prevent npm install failures.

## Detailed Findings

### 1. Package Architecture & Structure

**File structure** (`.claude/prompts/m5l4-github-packages-spec-pack.md:19-33`):

```
ai-toolkit/
├── package.json          # Package metadata and publishing config
├── README.md             # Installation and usage guide
├── install.js            # Postinstall hook for automatic installation
├── uninstall.js          # Cleanup script
├── skills/               # AI skills bundled with the toolkit
│   └── code-review/
│       └── SKILL.md      # Code review skill definition
└── rules/                # Team rules and conventions
    └── CLAUDE.md         # Rules to inject into consumer projects
```

**Package metadata requirements**:
- Name: `@twoj-zespol/ai-toolkit` (scoped to team namespace)
- Registry: `https://npm.pkg.github.com` (GitHub Packages)
- Node version: `>=20`
- Type: `commonjs` (avoids ESM complications in installers)

**Published files whitelist** (`package.json` `files` array):
```json
["skills/", "rules/", "install.js", "uninstall.js", "README.md"]
```

This excludes `node_modules`, tests, and development files from the published package.

### 2. Installer Pattern (install.js)

**Core algorithm** (`.claude/config-templates/m5l4-github-packages-install.js.template:12-91`):

1. **Project root detection**:
   - Check `PROJECT_ROOT` environment variable
   - Walk up from `node_modules` to find package consumer
   - Fallback to `process.cwd()`

2. **Skills installation**:
   - Copy each directory from `skills/` to `.claude/skills/<skill-name>/`
   - Delete old versions first for clean updates
   - Track copied files in `installedFiles` array

3. **Rules injection with sentinel markers**:
   ```markdown
   <!-- BEGIN @twoj-zespol/ai-toolkit -->
   [team rules content]
   <!-- END @twoj-zespol/ai-toolkit -->
   ```
   - Replace existing block (idempotent updates)
   - Append if not present (initial install)
   - Preserves user-edited content outside markers

4. **Manifest creation** (`.claude/.ai-toolkit-manifest.json`):
   ```json
   {
     "package": "@twoj-zespol/ai-toolkit",
     "version": "0.1.0",
     "installedAt": "2026-06-21T22:45:00Z",
     "files": ["skills/code-review/SKILL.md", "..."]
   }
   ```
   - Enables clean uninstall without path guessing
   - Tracks package version for upgrade detection

5. **Error handling**:
   - Wraps entire flow in try/catch
   - Logs warnings but doesn't fail `npm install` (critical for CI)

**Idempotency guarantees**:
- Skills: Delete + recopy (always fresh)
- Rules: Replace between sentinels (no duplicates)
- Manifest: Complete overwrite

### 3. Uninstaller Pattern (uninstall.js)

**Reverse operations** (`.claude/config-templates/m5l4-github-packages-uninstall.js.template:18-41`):

1. Read manifest from `.claude/.ai-toolkit-manifest.json`
2. Delete each tracked file
3. Remove rules block using sentinels (preserves user content)
4. Clean extra newlines: `replace(/\n{3,}/g, "\n\n")`
5. Delete manifest itself

**Safety**: Skips `CLAUDE.md` file deletion to avoid removing user-edited sections.

### 4. Consumer Authentication

**Registry mapping** (`.npmrc` in consumer repos):
```
@twoj-zespol:registry=https://npm.pkg.github.com
```

**Local development**:
- Developers use `npm login --registry=https://npm.pkg.github.com`
- Or user-level `.npmrc` with personal access token

**CI/CD authentication** (`.claude/prompts/m5l4-github-packages-spec-pack.md:89-96`):
```bash
# Preinstall helper added to consumer package.json
[ -n "$GH_PKG_TOKEN" ] && echo '//npm.pkg.github.com/:_authToken=${GH_PKG_TOKEN}' >> .npmrc || true
```

- Uses separate `GH_PKG_TOKEN` secret for third-party CI
- Never commit `_authToken` to repository

### 5. GitHub Actions Publishing Workflow

**File**: `.github/workflows/publish-ai-toolkit.yml`

**Two-job pipeline**:

| Job | Trigger | Purpose |
|-----|---------|---------|
| `validate` | Push + PR | Check package integrity, verify SKILL.md frontmatter |
| `publish` | Push only | Publish to GitHub Packages |

**Validation steps** (`.claude/config-templates/m5l4-github-packages-publish-ai-toolkit.yml.template:14-27`):
```yaml
- uses: actions/checkout@v4
- uses: actions/setup-node@v4
  with:
    node-version: 20
    registry-url: "https://npm.pkg.github.com"
    scope: "@twoj-zespol"
- run: npm ci
- run: test -f skills/code-review/SKILL.md
- run: npm pack --dry-run
```

**Publish job** (lines 29-46):
```yaml
- run: npm publish
  env:
    NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

**Permissions** (`.claude/prompts/m5l4-github-packages-spec-cicd.md:23-26`):
```yaml
permissions:
  contents: read
  packages: write
```

Uses ephemeral `GITHUB_TOKEN` automatically provided by GitHub Actions—no AWS, IAM roles, or CodeArtifact needed.

### 6. Code Review Skill Structure

**Skill anatomy** (`.claude/prompts/m5l4-shared-spec-skill.md`):

**YAML frontmatter** (required):
```yaml
---
name: code-review
description: Review code changes against team engineering conventions, testing standards and security expectations.
---
```

**Review categories** (derived from `.claude/prompts/m5l4-shared-conventions.md:1-45`):

1. **Naming** (lines 7-12):
   - camelCase for vars/functions
   - `is/has/should/can` for booleans
   - Verb-first functions
   - UPPER_SNAKE_CASE for constants

2. **Error handling** (lines 14-19):
   - try/catch on async operations
   - Error messages include context
   - No empty catch blocks
   - Cleanup in `finally` blocks

3. **TypeScript** (lines 21-26):
   - Zero `any` without justification
   - `interface` over `type`
   - `unknown` for external data with narrowing
   - Discriminated unions for states

4. **Function design** (lines 28-32):
   - Single responsibility
   - Max 3 parameters (use options object)
   - Early returns over nested conditionals
   - Pure query functions

5. **Security** (lines 34-38):
   - No hardcoded secrets (env vars only)
   - Validate at boundaries
   - Parameterized SQL
   - No stack traces in API responses

6. **Testing** (lines 40-44):
   - Behavior-focused test names
   - Isolated setup/teardown
   - Specific assertions
   - Cover edge cases and errors

**Output format**:
- Findings organized by severity: Critical → Warning → Suggestion
- Each finding includes `file:line` reference
- Final recommendation: `APPROVE`, `REQUEST CHANGES`, or `NEEDS DISCUSSION`

### 7. Project Context & Integration

**Repository details**:
- Owner: konradszydlo
- Repository: 10x-apriary-cljs
- Current branch: master
- Structure: Clojure monolithic project (NOT a monorepo)

**Existing GitHub Actions patterns** (`.github/workflows/master-docker.yml:39-46`):
- Already uses `ghcr.io` registry
- Has `packages:write` permission
- Builds Docker images on master push

**Recommended package location**:
- Create `/packages/ai-toolkit/` directory in repository root
- Not nested in `/src` since it's an extractable package
- Track progress in `context/changes/ai-toolkit/`

## Code References

- `.claude/prompts/m5l4-github-packages-spec-pack.md:1-97` - Package structure requirements
- `.claude/prompts/m5l4-github-packages-spec-cicd.md:1-97` - CI/CD workflow specification
- `.claude/prompts/m5l4-shared-spec-skill.md:1-41` - Code review skill requirements
- `.claude/prompts/m5l4-shared-conventions.md:1-45` - Team engineering conventions
- `.claude/config-templates/m5l4-github-packages-package.json.template:1-26` - Package.json template
- `.claude/config-templates/m5l4-github-packages-install.js.template:12-108` - Installer implementation
- `.claude/config-templates/m5l4-github-packages-uninstall.js.template:18-41` - Uninstaller implementation
- `.claude/config-templates/m5l4-github-packages-publish-ai-toolkit.yml.template:1-46` - GitHub Actions workflow
- `.claude/config-templates/m5l4-github-packages-consumer.npmrc.template:1-1` - Consumer .npmrc config
- `.claude/skills/10x-impl-review/SKILL.md:113-260` - Review skill output format patterns
- `.github/workflows/master-docker.yml:39-46` - Existing GitHub Packages integration

## Architecture Insights

**Key design decisions**:

1. **Sentinel-based rules injection**: Preserves user-edited content while enabling idempotent updates
2. **Manifest tracking**: Enables clean uninstall without path guessing; survives monorepo layouts
3. **Postinstall hook**: Automatic installation for regular `npm install` consumers
4. **Bin entry point**: Supports `npx @twoj-zespol/ai-toolkit install` for standalone execution
5. **Soft error handling**: Prevents npm install failure; logs warnings for debugging
6. **CommonJS type**: Avoids ESM compatibility issues in CI/installer context
7. **Registry in package.json**: Centralizes config; consumers inherit via `.npmrc` scope mapping

**Publishing flow**:
```
Developer commits → GitHub Actions → Validation job → Publish job → GitHub Packages
                                    ↓
                    Consumer npm install → Postinstall hook → Skills + Rules installed
```

**Authentication flow**:
```
Publisher: GITHUB_TOKEN (ephemeral, auto-provided by GitHub Actions)
Consumer (CI): GH_PKG_TOKEN (repository secret)
Consumer (local): npm login or user-level .npmrc
```

## Historical Context (from prior changes)

No directly related historical changes found in `context/changes/` or `context/archive/`. This is a new initiative to create team AI toolkit distribution infrastructure.

## Related Research

No prior research artifacts found for AI toolkit packaging. This research establishes the baseline for Model 1 (GitHub Packages) implementation.

## Open Questions

1. **Package scope naming**: Should we use `@konradszydlo/ai-toolkit` (matching repo owner) or `@10x-apriary-cljs/ai-toolkit` (matching repo name)?
   
2. **Skill versioning**: How to handle breaking changes in skill definitions across toolkit versions? Should we version skills independently or keep them coupled to the package version?

3. **Multi-tool support**: Should the toolkit support multiple AI tools (Claude Code, Cursor, Copilot) from day one, or start with Claude Code only and expand later?

4. **Installer robustness**: How to handle edge cases like:
   - Consumer project has no `.claude/` directory yet
   - Multiple toolkit packages installed (different teams/scopes)
   - Conflicting skill names from different sources

5. **CLAUDE.md vs separate rules file**: Should rules be injected into existing `CLAUDE.md` or should we create a separate `.claude/rules/<source>.md` file that CLAUDE.md imports?

6. **Testing strategy**: How to test the installer/uninstaller in isolation without polluting the development environment? Should we create a test harness that mocks the consumer project structure?
