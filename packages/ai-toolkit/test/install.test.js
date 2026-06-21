#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Simple test fixture for install.js
 * Creates a temporary consumer project, runs installer, verifies installation
 */

function assert(condition, message) {
  if (!condition) {
    throw new Error(`Assertion failed: ${message}`);
  }
}

function runTest() {
  console.log('Starting installer test...');

  // 1. Create temp directory
  const tmpDir = fs.mkdtempSync(path.join(require('os').tmpdir(), 'ai-toolkit-test-'));
  console.log(`✓ Created temp directory: ${tmpDir}`);

  try {
    // 2. Create mock consumer project structure
    const consumerRoot = path.join(tmpDir, 'consumer');
    fs.mkdirSync(consumerRoot, { recursive: true });

    // Create a mock node_modules structure
    const nodeModulesDir = path.join(consumerRoot, 'node_modules', '@konradszydlo', 'ai-toolkit');
    fs.mkdirSync(nodeModulesDir, { recursive: true });

    // Copy package contents to mock node_modules
    const packageRoot = path.resolve(__dirname, '..');
    const filesToCopy = ['install.js', 'package.json', 'skills', 'rules'];

    for (const file of filesToCopy) {
      const src = path.join(packageRoot, file);
      const dest = path.join(nodeModulesDir, file);

      if (fs.statSync(src).isDirectory()) {
        fs.cpSync(src, dest, { recursive: true });
      } else {
        fs.copyFileSync(src, dest);
      }
    }

    console.log('✓ Created mock consumer project structure');

    // 3. Run installer
    const installScript = path.join(nodeModulesDir, 'install.js');
    process.env.PROJECT_ROOT = consumerRoot;

    try {
      execSync(`node "${installScript}"`, {
        cwd: consumerRoot,
        stdio: 'pipe'
      });
    } catch (err) {
      // Installer might warn but should exit 0
      if (err.status !== 0) {
        throw err;
      }
    }

    console.log('✓ Ran installer');

    // 4. Assertions
    // Check .claude/skills/ directories exist
    const claudeSkillsDir = path.join(consumerRoot, '.claude', 'skills');
    assert(fs.existsSync(claudeSkillsDir), '.claude/skills/ directory should exist');

    // Since Phase 1 has placeholder .gitkeep files, check those exist
    // (In Phase 2, we'll check for actual SKILL.md files)
    const codeReviewDir = path.join(claudeSkillsDir, 'code-review');
    const biffPatternsDir = path.join(claudeSkillsDir, 'biff-patterns');
    const clojureStyleDir = path.join(claudeSkillsDir, 'clojure-style');

    // These directories should exist (installer copies all files including .gitkeep)
    assert(fs.existsSync(codeReviewDir), 'code-review skill directory should exist');
    assert(fs.existsSync(biffPatternsDir), 'biff-patterns skill directory should exist');
    assert(fs.existsSync(clojureStyleDir), 'clojure-style skill directory should exist');

    console.log('✓ Skills directories created');

    // Check CLAUDE.md contains sentinel block
    const claudeMdPath = path.join(consumerRoot, 'CLAUDE.md');
    assert(fs.existsSync(claudeMdPath), 'CLAUDE.md should exist');

    const claudeContent = fs.readFileSync(claudeMdPath, 'utf8');
    assert(
      claudeContent.includes('<!-- BEGIN @konradszydlo/ai-toolkit -->'),
      'CLAUDE.md should contain BEGIN sentinel'
    );
    assert(
      claudeContent.includes('<!-- END @konradszydlo/ai-toolkit -->'),
      'CLAUDE.md should contain END sentinel'
    );

    console.log('✓ CLAUDE.md sentinel markers present');

    // Check manifest exists and has expected fields
    const manifestPath = path.join(consumerRoot, '.claude', '.ai-toolkit-manifest.json');
    assert(fs.existsSync(manifestPath), 'Manifest should exist');

    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    assert(manifest.package === '@konradszydlo/ai-toolkit', 'Manifest should have correct package name');
    assert(manifest.version === '0.1.0', 'Manifest should have correct version');
    assert(Array.isArray(manifest.files), 'Manifest should have files array');
    assert(manifest.installedAt, 'Manifest should have installedAt timestamp');

    console.log('✓ Manifest created with expected fields');
    console.log(`  Package: ${manifest.package}`);
    console.log(`  Version: ${manifest.version}`);
    console.log(`  Files tracked: ${manifest.files.length}`);

    // 5. Test idempotency - run installer again
    try {
      execSync(`node "${installScript}"`, {
        cwd: consumerRoot,
        stdio: 'pipe'
      });
    } catch (err) {
      if (err.status !== 0) {
        throw err;
      }
    }

    // Verify still works after second install
    const claudeContent2 = fs.readFileSync(claudeMdPath, 'utf8');
    const beginCount = (claudeContent2.match(/<!-- BEGIN @konradszydlo\/ai-toolkit -->/g) || []).length;
    assert(beginCount === 1, 'CLAUDE.md should have exactly one BEGIN sentinel after reinstall');

    console.log('✓ Idempotency verified (reinstall works)');

    console.log('\n✅ All tests passed!');

  } finally {
    // Cleanup
    fs.rmSync(tmpDir, { recursive: true, force: true });
    console.log(`✓ Cleaned up temp directory`);
  }
}

// Run test
try {
  runTest();
  process.exit(0);
} catch (error) {
  console.error('\n❌ Test failed:');
  console.error(error.message);
  console.error(error.stack);
  process.exit(1);
}
