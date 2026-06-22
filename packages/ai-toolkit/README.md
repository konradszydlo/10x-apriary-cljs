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

## CI/CD & Publishing

### Automated Workflow

This package is automatically published to GitHub Packages via GitHub Actions:

- **Validation**: Runs on all pushes and pull requests
  - Verifies skill files exist
  - Validates YAML frontmatter in all skills
  - Runs `npm pack --dry-run` to check package structure

- **Publishing**: Only runs on pushes to `master` branch
  - Depends on validation passing
  - Publishes to `https://github.com/konradszydlo/10x-apriary-cljs/pkgs/npm/ai-toolkit`
  - Uses `GITHUB_TOKEN` for authentication (no setup needed)

### First Publish

When merged to master, the workflow will:
1. Run validation checks
2. Publish `@konradszydlo/ai-toolkit@0.1.0` to GitHub Packages
3. Package will be available at: `https://github.com/konradszydlo/10x-apriary-cljs/pkgs/npm/ai-toolkit`

### Local Testing

You can test the package locally before pushing:

```bash
# Validate package structure
cd packages/ai-toolkit
npm pack --dry-run

# Test frontmatter validation (from repo root)
node -e "
const fs = require('fs');
const skills = ['code-review', 'biff-patterns', 'clojure-style'];
skills.forEach(skill => {
  const content = fs.readFileSync(\`packages/ai-toolkit/skills/\${skill}/SKILL.md\`, 'utf8');
  const match = content.match(/^---[\r\n]+([\s\S]+?)[\r\n]+---/);
  if (!match || !match[1].includes('name:') || !match[1].includes('description:')) {
    throw new Error(\`\${skill}: Invalid frontmatter\`);
  }
  console.log(\`✓ \${skill}: Valid frontmatter\`);
});
"
```

**Note**: Full publish testing requires pushing to master. The workflow uses ephemeral `GITHUB_TOKEN` which is only available in GitHub Actions.

### Version Bumping

For future releases:

1. Update version in `packages/ai-toolkit/package.json`
2. Commit and push to master
3. Workflow will publish the new version automatically

### Workflow Troubleshooting

**Validation job fails**:
- Check that all skill files exist with valid frontmatter
- Ensure `npm pack --dry-run` succeeds locally

**Publish job fails**:
- Verify `packages: write` permission is set in workflow
- Check that `NODE_AUTH_TOKEN` is properly passed
- Ensure package name matches repository scope (`@konradszydlo`)

**Package not appearing**:
- Check GitHub Actions logs for error messages
- Verify you're on `master` branch (publish only runs there)
- Check repository Settings → Packages for published packages

For more information, see [GitHub Packages documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-npm-registry).

## License

UNLICENSED - Internal team use only.
