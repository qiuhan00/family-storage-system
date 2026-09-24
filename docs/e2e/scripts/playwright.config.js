// playwright.config.js —— 供 ui-tester E2E 使用；证据目录由 EVIDENCE_DIR 指定
const path = require('path');
const EVIDENCE_DIR = process.env.EVIDENCE_DIR || path.join(__dirname, 'evidence');

module.exports = {
  testDir: __dirname,
  timeout: 180000,
  expect: { timeout: 12000 },
  workers: 1,
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173/',
    browserName: 'chromium',
    channel: 'chrome',
    headless: true,
    viewport: { width: 1280, height: 720 },
    video: 'on',
    screenshot: 'on',
    trace: 'on',
    actionTimeout: 15000,
  },
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: path.join(EVIDENCE_DIR, 'html-report') }],
  ],
  outputDir: path.join(EVIDENCE_DIR, 'test-results'),
};