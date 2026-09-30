// playwright.f2.config.js —— F2 临期口径 E2E 专用配置（移动视口 390×844）
// 证据目录由 EVIDENCE_DIR 环境变量指定；须放在含 @playwright/test 的目录（e2e-pw）下运行。
const path = require('path');

const EVIDENCE_DIR = process.env.EVIDENCE_DIR || path.join(__dirname, 'f2-evidence');

module.exports = {
  testDir: __dirname,
  timeout: 300000,
  expect: { timeout: 12000 },
  workers: 1,
  retries: 0,
  use: {
    baseURL: 'http://localhost:5173/',
    browserName: 'chromium',
    channel: 'chrome',
    headless: true,
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    video: 'on',
    screenshot: 'on',
    trace: 'on',
    actionTimeout: 15000,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: path.join(EVIDENCE_DIR, 'html-report') }],
  ],
  outputDir: path.join(EVIDENCE_DIR, 'test-results'),
};
