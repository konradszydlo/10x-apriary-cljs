#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const PACKAGE_NAME = '@konradszydlo/ai-toolkit';
const SENTINEL_BEGIN = `<!-- BEGIN ${PACKAGE_NAME} -->`;
const SENTINEL_END = `<!-- END ${PACKAGE_NAME} -->`;

/**
 * Detect the consumer project root directory.
 * Priority: PROJECT_ROOT env var → walk up from node_modules → fallback to cwd
 */
function findProjectRoot() {
  // 1. Check PROJECT_ROOT env var
  if (process.env.PROJECT_ROOT) {
    return process.env.PROJECT_ROOT;
  }

  // 2. Walk up from node_modules
  let currentDir = __dirname;
  while (currentDir !== path.parse(currentDir).root) {
    if (currentDir.includes('node_modules')) {
      // Found node_modules, go up to find the project root
      const parts = currentDir.split(path.sep);
      const nmIndex = parts.lastIndexOf('node_modules');
      if (nmIndex > 0) {
        return parts.slice(0, nmIndex).join(path.sep);
      }
    }
    currentDir = path.dirname(currentDir);
  }

  // 3. Fallback to cwd
  return process.cwd();
}

/**
 * Copy skills from package to consumer's .claude/skills/ directory
 */
function installSkills(projectRoot) {
  const sourceDir = path.join(__dirname, 'skills');
  const targetDir = path.join(projectRoot, '.claude', 'skills');

  // Create .claude/skills directory if missing
  fs.mkdirSync(targetDir, { recursive: true });

  const installedFiles = [];

  // Read all skill directories
  if (!fs.existsSync(sourceDir)) {
    console.warn(`Warning: skills directory not found at ${sourceDir}`);
    return installedFiles;
  }

  const skillDirs = fs.readdirSync(sourceDir, { withFileTypes: true })
    .filter(dirent => dirent.isDirectory())
    .map(dirent => dirent.name);

  // Copy each skill
  for (const skillName of skillDirs) {
    const sourceSkillDir = path.join(sourceDir, skillName);
    const targetSkillDir = path.join(targetDir, skillName);

    // Delete old version first (if exists)
    if (fs.existsSync(targetSkillDir)) {
      fs.rmSync(targetSkillDir, { recursive: true, force: true });
    }

    // Create target skill directory
    fs.mkdirSync(targetSkillDir, { recursive: true });

    // Copy all files from source skill directory
    function copyRecursive(src, dest) {
      const entries = fs.readdirSync(src, { withFileTypes: true });
      for (const entry of entries) {
        const srcPath = path.join(src, entry.name);
        const destPath = path.join(dest, entry.name);

        if (entry.isDirectory()) {
          fs.mkdirSync(destPath, { recursive: true });
          copyRecursive(srcPath, destPath);
        } else {
          fs.copyFileSync(srcPath, destPath);
          // Track relative path from project root
          const relPath = path.relative(projectRoot, destPath);
          installedFiles.push(relPath);
        }
      }
    }

    copyRecursive(sourceSkillDir, targetSkillDir);
  }

  return installedFiles;
}

/**
 * Inject rules into CLAUDE.md between sentinel markers
 */
function injectRules(projectRoot) {
  const rulesSource = path.join(__dirname, 'rules', 'CLAUDE.md');
  let claudePath = path.join(projectRoot, 'CLAUDE.md');

  // Follow symlink if present
  try {
    const realPath = fs.realpathSync(claudePath);
    if (realPath !== claudePath) {
      claudePath = realPath;
    }
  } catch (err) {
    // CLAUDE.md doesn't exist yet, will be created
  }

  // Read rules content
  let rulesContent = '';
  if (fs.existsSync(rulesSource)) {
    rulesContent = fs.readFileSync(rulesSource, 'utf8');
  }

  const injectionBlock = `${SENTINEL_BEGIN}\n${rulesContent}\n${SENTINEL_END}`;

  // Read or create CLAUDE.md
  let claudeContent = '';
  if (fs.existsSync(claudePath)) {
    claudeContent = fs.readFileSync(claudePath, 'utf8');
  }

  // Check if sentinels already exist
  const beginIndex = claudeContent.indexOf(SENTINEL_BEGIN);
  const endIndex = claudeContent.indexOf(SENTINEL_END);

  if (beginIndex !== -1 && endIndex !== -1) {
    // Replace existing block (idempotent update)
    const before = claudeContent.substring(0, beginIndex);
    const after = claudeContent.substring(endIndex + SENTINEL_END.length);
    claudeContent = before + injectionBlock + after;
  } else {
    // Append to end (initial install)
    if (claudeContent && !claudeContent.endsWith('\n')) {
      claudeContent += '\n';
    }
    if (claudeContent) {
      claudeContent += '\n';
    }
    claudeContent += injectionBlock + '\n';
  }

  // Write back
  fs.writeFileSync(claudePath, claudeContent, 'utf8');
}

/**
 * Write manifest tracking installed files
 */
function writeManifest(projectRoot, installedFiles) {
  const manifestPath = path.join(projectRoot, '.claude', '.ai-toolkit-manifest.json');
  const packageJson = require('./package.json');

  const manifest = {
    package: packageJson.name,
    version: packageJson.version,
    installedAt: new Date().toISOString(),
    files: installedFiles
  };

  fs.mkdirSync(path.dirname(manifestPath), { recursive: true });
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');
}

/**
 * Main installer entry point
 */
function install() {
  try {
    const projectRoot = findProjectRoot();
    console.log(`Installing ${PACKAGE_NAME} to ${projectRoot}`);

    // Install skills and track files
    const installedFiles = installSkills(projectRoot);

    // Inject rules into CLAUDE.md
    injectRules(projectRoot);

    // Write manifest
    writeManifest(projectRoot, installedFiles);

    console.log(`✓ ${PACKAGE_NAME} installed successfully`);
    console.log(`  Skills: ${installedFiles.length} files installed`);
    console.log(`  Rules: injected into CLAUDE.md`);
    console.log(`  Manifest: .claude/.ai-toolkit-manifest.json`);

    // Exit with success (don't break npm install on warnings)
    process.exit(0);
  } catch (error) {
    console.warn(`Warning: ${PACKAGE_NAME} installation encountered an error:`);
    console.warn(error.message);
    console.warn('Installation will continue, but toolkit may not be fully functional.');

    // Exit with success to not break npm install
    process.exit(0);
  }
}

// Run installer
install();
