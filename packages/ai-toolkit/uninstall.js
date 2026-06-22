#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const PACKAGE_NAME = '@konradszydlo/ai-toolkit';
const SENTINEL_BEGIN = `<!-- BEGIN ${PACKAGE_NAME} -->`;
const SENTINEL_END = `<!-- END ${PACKAGE_NAME} -->`;

/**
 * Find project root (same logic as install.js)
 */
function findProjectRoot() {
  if (process.env.PROJECT_ROOT) {
    return process.env.PROJECT_ROOT;
  }

  let currentDir = __dirname;
  while (currentDir !== path.parse(currentDir).root) {
    if (currentDir.includes('node_modules')) {
      const parts = currentDir.split(path.sep);
      const nmIndex = parts.lastIndexOf('node_modules');
      if (nmIndex > 0) {
        return parts.slice(0, nmIndex).join(path.sep);
      }
    }
    currentDir = path.dirname(currentDir);
  }

  return process.cwd();
}

/**
 * Remove sentinel block from CLAUDE.md
 */
function removeSentinelBlock(projectRoot) {
  let claudePath = path.join(projectRoot, 'CLAUDE.md');

  // Follow symlink if present
  try {
    const realPath = fs.realpathSync(claudePath);
    if (realPath !== claudePath) {
      claudePath = realPath;
    }
  } catch (err) {
    // CLAUDE.md doesn't exist, nothing to clean
    return;
  }

  if (!fs.existsSync(claudePath)) {
    return;
  }

  let content = fs.readFileSync(claudePath, 'utf8');

  const beginIndex = content.indexOf(SENTINEL_BEGIN);
  const endIndex = content.indexOf(SENTINEL_END);

  if (beginIndex !== -1 && endIndex !== -1) {
    // Remove the block including sentinels
    const before = content.substring(0, beginIndex);
    const after = content.substring(endIndex + SENTINEL_END.length);
    content = before + after;

    // Clean up extra newlines (max 2 consecutive)
    content = content.replace(/\n{3,}/g, '\n\n');

    fs.writeFileSync(claudePath, content, 'utf8');
    console.log('✓ Removed rules from CLAUDE.md');
  }
}

/**
 * Delete tracked files from manifest
 */
function deleteTrackedFiles(projectRoot, manifest) {
  let deletedCount = 0;

  for (const file of manifest.files) {
    const filePath = path.join(projectRoot, file);
    try {
      if (fs.existsSync(filePath)) {
        fs.rmSync(filePath, { force: true });
        deletedCount++;
      }
    } catch (err) {
      console.warn(`Warning: Could not delete ${file}: ${err.message}`);
    }
  }

  // Try to remove empty parent directories (skills directories)
  const skillDirs = new Set();
  for (const file of manifest.files) {
    const dir = path.dirname(file);
    if (dir.includes('.claude/skills/')) {
      skillDirs.add(path.join(projectRoot, dir));
    }
  }

  for (const dir of skillDirs) {
    try {
      if (fs.existsSync(dir) && fs.readdirSync(dir).length === 0) {
        fs.rmdirSync(dir);
      }
    } catch (err) {
      // Ignore errors removing directories
    }
  }

  console.log(`✓ Deleted ${deletedCount} skill files`);
}

/**
 * Main uninstaller entry point
 */
function uninstall() {
  try {
    const projectRoot = findProjectRoot();
    const manifestPath = path.join(projectRoot, '.claude', '.ai-toolkit-manifest.json');

    // Check if manifest exists
    if (!fs.existsSync(manifestPath)) {
      console.log(`${PACKAGE_NAME} is not installed (no manifest found)`);
      process.exit(0);
    }

    console.log(`Uninstalling ${PACKAGE_NAME} from ${projectRoot}`);

    // Read manifest
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

    // Delete tracked files
    deleteTrackedFiles(projectRoot, manifest);

    // Remove sentinel block from CLAUDE.md
    removeSentinelBlock(projectRoot);

    // Delete manifest itself
    fs.unlinkSync(manifestPath);
    console.log('✓ Removed manifest');

    console.log(`✓ ${PACKAGE_NAME} uninstalled successfully`);
    process.exit(0);
  } catch (error) {
    console.error(`Error during uninstallation: ${error.message}`);
    process.exit(1);
  }
}

// Run uninstaller
uninstall();
