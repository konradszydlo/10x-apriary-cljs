# AI Toolkit

Team AI artifacts (skills, rules, prompts) distributed through GitHub Packages.

## Installation

### Prerequisites

1. **Registry Configuration**: Add the following to your project's `.npmrc`:

```
@konradszydlo:registry=https://npm.pkg.github.com
```

2. **Authentication**:

   - **Local development**: Run `npm login --registry=https://npm.pkg.github.com`
   - **CI/CD**: Set `GH_PKG_TOKEN` environment variable with a GitHub Personal Access Token (PAT) that has `read:packages` permission

### Install

```bash
npm install @konradszydlo/ai-toolkit
```

The package automatically installs via `postinstall` hook:
- Skills are copied to `.claude/skills/`
- Team rules are injected into `CLAUDE.md` between sentinel markers
- A manifest (`.claude/.ai-toolkit-manifest.json`) tracks installed files

## What Gets Installed

### Skills

Three Clojure/Biff-specific skills:

- **`code-review`**: Automated code review enforcing Clojure/Biff team conventions (naming, error handling, RLS, Malli schemas, etc.)
- **`biff-patterns`**: Documentation of 6 key Biff framework patterns (modules, RLS queries, transactions, middleware, schemas, Hiccup)
- **`clojure-style`**: Style rules and clj-kondo integration

Invoke with `/code-review`, `/biff-patterns`, or `/clojure-style` in Claude Code.

### Rules

Team conventions injected into `CLAUDE.md`:
- Naming conventions (kebab-case, predicates, private functions)
- Error handling patterns (`[:ok val]` / `[:error map]` tuples)
- Guard clauses and flow control
- Malli validation with closed maps
- Row-level security (`:user-id` filtering)
- Biff framework patterns
- XTDB entity structure
- Testing conventions
- Security practices (jBCrypt, SecureRandom, prevent enumeration)

## Manual Commands

If you need to manually install or uninstall:

```bash
# Manual install
npx @konradszydlo/ai-toolkit install

# Manual uninstall
npx @konradszydlo/ai-toolkit uninstall
```

Or if the package is already in `node_modules`:

```bash
node node_modules/@konradszydlo/ai-toolkit/uninstall.js
```

## Updating

Simply reinstall the package:

```bash
npm install @konradszydlo/ai-toolkit@latest
```

The installer is idempotent:
- Skills are updated (old versions deleted first)
- Rules in `CLAUDE.md` are replaced between sentinels
- User edits outside sentinel markers are preserved

## Uninstalling

Remove from `package.json` and run:

```bash
npm uninstall @konradszydlo/ai-toolkit
```

Or use the manual uninstall command above.

The uninstaller:
- Deletes all tracked skill files
- Removes the sentinel block from `CLAUDE.md` (preserves user edits)
- Cleans up the manifest

## Troubleshooting

### `npm install` fails with 404

**Cause**: Not authenticated to GitHub Packages.

**Fix**:
- Local: Run `npm login --registry=https://npm.pkg.github.com`
- CI: Ensure `GH_PKG_TOKEN` is set with a valid PAT

### Sentinel markers conflict

**Cause**: Consumer project already has `<!-- BEGIN @konradszydlo/ai-toolkit -->` markers.

**Fix**: The installer will update the existing block. If you see unexpected content, check for duplicate markers.

### Missing `.claude/` directory

**Cause**: Consumer project doesn't have a `.claude/` directory yet.

**Fix**: The installer auto-creates it. No action needed.

### Skills not appearing in Claude Code

**Cause**: Claude Code may not have reloaded skills.

**Fix**: Restart Claude Code or run `/help` to refresh skills list.

## Version

Current version: `0.1.0`

## License

UNLICENSED - Internal team use only.
