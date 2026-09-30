// f2-expiry-threshold.spec.js —— F2 缺陷修复验收：临期口径统一为 app_settings 的 expiry.remind.days
// 验证点（对应 F2 修复说明 1~4）：
//   1) 首页标题「临期提醒（{N} 天内）」动态化，且临期列表请求 GET /api/items/expiring?days={N} 同一 N；
//   2) 物品页筛选项「仅看 {N} 天内临期」动态化；
//   3) 角标：到期 ≤ 阈值 N（含已过期）→ 红色「临期 {date}」；超出阈值 → 普通「到期 {date}」（橙色档已删除）；
//   4) GET /api/items?expiring=1 的搜索窗口后端按同一 N 生效。
// 视口 390×844。证据：截图（f2-step-NN-*.png）+ 视频 + trace，输出到 $env:EVIDENCE_DIR。
// 前置数据：scripts/f2-seed.mjs 创建 F2E 前缀 3 条物品（+3 天 / +15 天 / 无到期），跑完 f2-cleanup.mjs 清理。
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const EVIDENCE_DIR = process.env.EVIDENCE_DIR || path.join(__dirname, 'f2-evidence');
const BASE = 'http://localhost:5173';
const ADMIN = 'admin';
const PWD = process.env.E2E_ADMIN_PWD;
if (!PWD) throw new Error('缺少环境变量 E2E_ADMIN_PWD（凭据不落盘，见 AGENTS.md 敏感信息规则）');

const NEAR = 'F2E临期3天'; // 到期 today+3（阈值 7/30 内）
const FAR = 'F2E远期15天'; // 到期 today+15（7 外、30 内 —— F2 修复点）
const NOEXP = 'F2E无到期'; // 无到期日
const EXPIRED = '牛奶'; // 已过期（2026-09-28）

const RED = 'rgb(220, 38, 38)'; // --fs-danger
const ORANGE = 'rgb(217, 119, 6)'; // --fs-warning（旧版橙色档色值）

let seq = 0;
function shot(page, name, opts = {}) {
  seq += 1;
  const file = path.join(EVIDENCE_DIR, `f2-step-${String(seq).padStart(2, '0')}-${name}.png`);
  return page.screenshot({ path: file, fullPage: Boolean(opts.full) });
}
function shotEl(locator, name) {
  seq += 1;
  return locator.screenshot({ path: path.join(EVIDENCE_DIR, `f2-step-${String(seq).padStart(2, '0')}-${name}.png`) });
}

async function api(page, method, pathname, body) {
  const token = await page.evaluate(() => localStorage.getItem('fs_token'));
  const res = await page.request.fetch(`${BASE}${pathname}`, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    data: body,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: res.status(), data };
}

function digits(text) {
  const m = String(text).replace(/[^\d]/g, '');
  return m === '' ? NaN : Number(m);
}

async function colorOf(locator) {
  return locator.evaluate((el) => getComputedStyle(el).color);
}

/** 首页「临期提醒」统计卡计数 + 临期列表条数/名称（同一口径核对）。 */
async function homeExpiring(page) {
  const stat = page.locator('.stat-card').filter({ hasText: '临期提醒' });
  await expect(stat).toBeVisible();
  const statText = (await stat.locator('.stat-card__value').innerText()).trim();
  const section = page.locator('.home-section').filter({
    has: page.locator('h2.section-title', { hasText: '临期提醒' }),
  });
  const names = await section.locator('.van-cell__title').allInnerTexts();
  return { statCount: digits(statText), statText, rowCount: names.length, names, section };
}

/** 设置页 stepper 设为目标值（fill + Tab；不同步则用 +/- 兜底）。 */
async function setStepper(page, target) {
  const input = page.locator('.van-stepper__input');
  await input.fill(String(target));
  await input.press('Tab');
  await page.waitForTimeout(300);
  if ((await input.inputValue()) !== String(target)) {
    const plus = page.locator('.van-stepper__plus');
    const minus = page.locator('.van-stepper__minus');
    for (let i = 0; i < 120; i += 1) {
      const v = Number(await input.inputValue());
      if (v === target) break;
      if (Number.isNaN(v)) break;
      await (v < target ? plus : minus).click();
    }
  }
  await expect(input).toHaveValue(String(target));
}

test('F2 临期口径统一（expiry.remind.days）移动端验收', async ({ page }) => {
  const consoleErrors = [];
  const pageErrors = [];
  const badResponses = [];
  const failedRequests = [];
  const expiringListReqs = [];
  const expiringSearchReqs = [];
  const settingsReqs = [];

  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push({ text: m.text(), url: (m.location() || {}).url || '' });
  });
  page.on('pageerror', (e) => pageErrors.push(String(e)));
  page.on('response', (r) => { if (r.status() >= 400) badResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`); });
  page.on('requestfailed', (r) => failedRequests.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));
  page.on('request', (r) => {
    const u = r.url();
    if (u.includes('/api/items/expiring')) expiringListReqs.push(u);
    if (u.includes('/api/items?') && u.includes('expiring=1')) expiringSearchReqs.push(u);
    if (u.includes('/api/settings')) settingsReqs.push(u);
  });

  // ================= 前置：未登录 → /login（含错误分支） =================
  await page.goto(`${BASE}/`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  await shot(page, 'a0-login-page');

  // --- 错误分支：错误密码 ---
  await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  await page.getByPlaceholder('请输入密码').fill('wrong-password-xyz');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  const errToast = page.locator('.van-toast');
  await expect(errToast).toBeVisible();
  await expect(errToast).toContainText(/密码|失败|错误/);
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => localStorage.getItem('fs_token'))).toBeNull();
  await shot(page, 'a1-login-error-branch');
  await expect(errToast).toBeHidden({ timeout: 10000 });

  // --- 正常登录 ---
  await page.getByPlaceholder('请输入密码').fill(PWD);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.home-page')).toBeVisible({ timeout: 20000 });
  await shot(page, 'a2-home-after-login');

  // ================= 场景 A：阈值 = 7 初始态 =================
  // 前置条件：阈值必须是 7
  const s0 = await api(page, 'GET', '/api/settings');
  expect(s0.status, 'GET /api/settings 应为 200').toBe(200);
  expect(s0.data.expiry_remind_days, '前置条件：expiry_remind_days 应为 7').toBe(7);

  // A1-a 首页标题动态化（7 天内），并由 /api/settings 驱动
  const title7 = page.getByRole('heading', { name: '临期提醒（7 天内）' });
  await expect(title7).toBeVisible();
  await expect(page.getByRole('heading', { name: '临期提醒（7 天内）', exact: true })).toHaveCount(1);
  expect(settingsReqs.length, '首页应请求 /api/settings 取阈值').toBeGreaterThan(0);
  expect(
    expiringListReqs.some((u) => /[?&]days=7(&|$)/.test(u)),
    `临期列表请求应为 days=7，实际：${JSON.stringify(expiringListReqs)}`,
  ).toBe(true);

  // A1-b 统计卡「临期提醒」计数 = 临期列表条数
  const h7 = await homeExpiring(page);
  expect(h7.statCount, `统计卡值=${h7.statText}`).toBe(h7.rowCount);
  expect(h7.rowCount, '阈值 7 下应至少有 1 条临期（F2E临期3天）').toBeGreaterThanOrEqual(1);
  expect(h7.names.join('|')).toContain(NEAR);
  expect(h7.names.join('|'), '阈值 7 不应包含 +15 天物品').not.toContain(FAR);
  await shot(page, 'a3-home-title7-counts', { full: true });

  // ================= A2 物品页筛选文案 =================
  await page.locator('.van-tabbar').getByText('物品', { exact: true }).click();
  await expect(page.locator('.item-card').first()).toBeVisible();
  await page.getByRole('button', { name: '筛选' }).click();
  const panel = page.locator('.filter-panel');
  await expect(panel).toBeVisible();
  const filterRow7 = panel.getByText('仅看 7 天内临期', { exact: true });
  await filterRow7.scrollIntoViewIfNeeded();
  await expect(filterRow7).toBeInViewport();
  await expect(filterRow7).toHaveCount(1);
  await shot(page, 'a4-filter-text-7');
  await panel.getByRole('button', { name: '确定' }).click();
  await expect(panel).toBeHidden();

  // ================= A3 列表角标（阈值 7） =================
  const nearCard = page.locator('.item-card').filter({ hasText: NEAR });
  const farCard = page.locator('.item-card').filter({ hasText: FAR });
  const expiredCard = page.locator('.item-card').filter({ hasText: EXPIRED });
  const noCard = page.locator('.item-card').filter({ hasText: NOEXP });
  await expect(nearCard).toHaveCount(1);
  await expect(farCard).toHaveCount(1);
  await expect(noCard).toHaveCount(1);

  // A3-a ≤7 天 → 红色「临期」
  await nearCard.scrollIntoViewIfNeeded();
  const nearBadge = nearCard.locator('.item-card__expiry');
  await expect(nearBadge).toHaveText(/^\s*临期 \d{4}-\d{2}-\d{2}\s*$/);
  await expect(nearBadge).toHaveClass(/item-card__expiry--red/);
  expect(await colorOf(nearBadge)).toBe(RED);
  await shotEl(nearCard, 'a5-badge-near-red');

  // A3-b >7 且 ≤30 天 → 普通「到期」（旧版此处为橙色「临期」——修复点）
  await farCard.scrollIntoViewIfNeeded();
  const farBadge = farCard.locator('.item-card__expiry');
  await expect(farBadge).toBeVisible();
  const farText = (await farBadge.innerText()).trim();
  expect(farText, `期望「到期 …」，实际「${farText}」`).toMatch(/^到期 \d{4}-\d{2}-\d{2}$/);
  expect(farText, '超出阈值不得显示「临期」').not.toContain('临期');
  const farClass = await farBadge.getAttribute('class');
  expect(farClass, `角标 class=${farClass}`).not.toMatch(/--red|orange|warn/);
  const farColor = await colorOf(farBadge);
  expect(farColor, `实际色值 ${farColor} 不应为旧版橙色 ${ORANGE}`).not.toBe(ORANGE);
  expect(farColor, `实际色值 ${farColor} 不应为临期红 ${RED}`).not.toBe(RED);
  expect(
    await page.locator('.item-card__expiry--orange, .item-card__expiry--warn').count(),
    '页面不得残留橙色临期档',
  ).toBe(0);
  await shotEl(farCard, 'a6-badge-far-normal');

  // A3-c 已过期 → 红色「临期」（含已过期）
  await expiredCard.scrollIntoViewIfNeeded();
  const expiredBadge = expiredCard.locator('.item-card__expiry');
  await expect(expiredBadge).toHaveText(/^\s*临期 \d{4}-\d{2}-\d{2}\s*$/);
  await expect(expiredBadge).toHaveClass(/item-card__expiry--red/);
  expect(await colorOf(expiredBadge)).toBe(RED);
  await shotEl(expiredCard, 'a7-badge-expired-red');

  // A3-d 无到期日 → 无角标
  await noCard.scrollIntoViewIfNeeded();
  expect(await noCard.locator('.item-card__expiry').count(), '无到期日不应有角标').toBe(0);
  await shotEl(noCard, 'a8-badge-none');
  await shot(page, 'a9-items-list-7', { full: true });

  // ================= A4 详情页角标 =================
  await nearCard.click();
  await expect(page).toHaveURL(/\/items\/\d+/);
  const detailRed = page.locator('.detail-head__expiry--red');
  await expect(detailRed).toBeVisible();
  await expect(detailRed).toHaveText(/^\s*临期 \d{4}-\d{2}-\d{2}\s*$/);
  expect(await colorOf(detailRed)).toBe(RED);
  await shot(page, 'a10-detail-near-red');
  await page.locator('[aria-label="返回"]').click();
  await expect(page.locator('.item-card').first()).toBeVisible();

  await farCard.click();
  await expect(page).toHaveURL(/\/items\/\d+/);
  const detailPlain = page.locator('.detail-head__expiry');
  await expect(detailPlain).toBeVisible();
  const detailText = (await detailPlain.innerText()).trim();
  expect(detailText, `详情期望「到期 …」，实际「${detailText}」`).toMatch(/^到期 \d{4}-\d{2}-\d{2}$/);
  expect(await page.locator('.detail-head__expiry--red, .detail-head__expiry--orange').count(), '详情不得有红/橙临期档').toBe(0);
  expect(await colorOf(detailPlain)).not.toBe(ORANGE);
  await shot(page, 'a11-detail-far-normal');
  await page.locator('[aria-label="返回"]').click();
  await expect(page.locator('.item-card').first()).toBeVisible();

  // ================= A5 expiring=1 搜索窗口 = 阈值 7 =================
  await page.getByRole('button', { name: '筛选' }).click();
  await page.locator('.filter-panel .van-switch').click();
  await panel.getByRole('button', { name: '确定' }).click();
  await expect(page.locator('.item-card')).toHaveCount(1);
  await expect(page.locator('.item-card').filter({ hasText: NEAR })).toHaveCount(1);
  await expect(page.locator('.item-card').filter({ hasText: FAR })).toHaveCount(0);
  expect(
    expiringSearchReqs.some((u) => u.includes('expiring=1')),
    '应发起 GET /api/items?expiring=1',
  ).toBe(true);
  await shot(page, 'a12-expiring-search-7');

  // 关闭临期筛选，恢复全量列表
  await page.getByRole('button', { name: '筛选' }).click();
  await page.locator('.filter-panel .van-switch').click();
  await panel.getByRole('button', { name: '确定' }).click();
  await expect(page.locator('.item-card').filter({ hasText: FAR })).toHaveCount(1);

  // ================= 场景 B：阈值改为 30 =================
  await page.goto(`${BASE}/settings`);
  await expect(page.getByRole('button', { name: '保存设置' })).toBeVisible();
  const stepper = page.locator('.van-stepper__input');
  await expect(stepper).toHaveValue('7');
  await shot(page, 'b1-settings-7');

  await setStepper(page, 30);
  await expect(page.locator('.settings-hint')).toContainText('30 天');
  await shot(page, 'b2-settings-30-filled');

  await page.getByRole('button', { name: '保存设置' }).click();
  const savedToast = page.locator('.van-toast');
  await expect(savedToast).toBeVisible();
  await expect(savedToast).toContainText('已保存');
  const s30 = await api(page, 'GET', '/api/settings');
  expect(s30.data.expiry_remind_days, '保存后阈值应为 30').toBe(30);
  await shot(page, 'b3-settings-saved-30');

  // B5 回首页：标题 30 天内 + 计数一致 + 列表含 8~30 天物品 + days=30 请求
  await page.locator('.van-tabbar').getByText('首页', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '临期提醒（30 天内）', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '临期提醒（7 天内）' })).toHaveCount(0);
  expect(
    expiringListReqs.some((u) => /[?&]days=30(&|$)/.test(u)),
    `临期列表请求应为 days=30，实际：${JSON.stringify(expiringListReqs)}`,
  ).toBe(true);
  const h30 = await homeExpiring(page);
  expect(h30.statCount, `统计卡值=${h30.statText}`).toBe(h30.rowCount);
  expect(h30.rowCount, '阈值 30 应包含 +3 与 +15 天两条').toBeGreaterThanOrEqual(2);
  expect(h30.names.join('|')).toContain(NEAR);
  expect(h30.names.join('|'), '8~30 天内到期物品应进入临期列表').toContain(FAR);
  await shot(page, 'b4-home-title30-counts', { full: true });

  // B6 物品页筛选文案 + 角标切换
  await page.locator('.van-tabbar').getByText('物品', { exact: true }).click();
  await expect(page.locator('.item-card').first()).toBeVisible();
  await page.getByRole('button', { name: '筛选' }).click();
  const filterRow30 = panel.getByText('仅看 30 天内临期', { exact: true });
  await filterRow30.scrollIntoViewIfNeeded();
  await expect(filterRow30).toBeInViewport();
  await expect(panel.getByText('仅看 7 天内临期')).toHaveCount(0);
  await shot(page, 'b5-filter-text-30');
  await panel.getByRole('button', { name: '确定' }).click();
  await expect(panel).toBeHidden();

  await farCard.scrollIntoViewIfNeeded();
  const farBadge30 = farCard.locator('.item-card__expiry');
  await expect(farBadge30, '阈值 30 后 +15 天物品应变红「临期」').toHaveText(/^\s*临期 \d{4}-\d{2}-\d{2}\s*$/);
  await expect(farBadge30).toHaveClass(/item-card__expiry--red/);
  expect(await colorOf(farBadge30)).toBe(RED);
  await shotEl(farCard, 'b6-badge-far-red-30');
  await expect(nearCard.locator('.item-card__expiry')).toHaveClass(/item-card__expiry--red/);
  await shot(page, 'b7-items-list-30', { full: true });

  // B7 expiring=1 窗口随阈值变 30
  await page.getByRole('button', { name: '筛选' }).click();
  await page.locator('.filter-panel .van-switch').click();
  await panel.getByRole('button', { name: '确定' }).click();
  await expect(page.locator('.item-card').filter({ hasText: FAR })).toHaveCount(1);
  await expect(page.locator('.item-card').filter({ hasText: NEAR })).toHaveCount(1);
  const uiCount = await page.locator('.item-card').count();
  const apiExp = await api(page, 'GET', '/api/items?expiring=1&page=0&size=50&status=ACTIVE');
  expect(apiExp.status).toBe(200);
  expect(apiExp.data.totalElements, 'UI 结果条数应与 GET /api/items?expiring=1 一致').toBe(uiCount);
  expect(uiCount, '阈值 30 下 expiring=1 至少命中 2 条').toBeGreaterThanOrEqual(2);
  expect(
    expiringSearchReqs.some((u) => u.includes('expiring=1')),
    '应发起 GET /api/items?expiring=1',
  ).toBe(true);
  await shot(page, 'b8-expiring-search-30');

  // ================= 场景 C：恢复阈值 7 =================
  await page.goto(`${BASE}/settings`);
  await expect(page.getByRole('button', { name: '保存设置' })).toBeVisible();
  await expect(stepper).toHaveValue('30');
  await setStepper(page, 7);
  await expect(page.locator('.settings-hint')).toContainText('7 天');
  await page.getByRole('button', { name: '保存设置' }).click();
  await expect(page.locator('.van-toast')).toContainText('已保存');
  const s7 = await api(page, 'GET', '/api/settings');
  expect(s7.data.expiry_remind_days, '阈值必须恢复为 7').toBe(7);
  await shot(page, 'c1-settings-restored-7');

  await page.locator('.van-tabbar').getByText('首页', { exact: true }).click();
  await expect(page.getByRole('heading', { name: '临期提醒（7 天内）', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '临期提醒（30 天内）' })).toHaveCount(0);
  expect(
    expiringListReqs.filter((u) => /[?&]days=7(&|$)/.test(u)).length,
    '恢复后应再次请求 days=7',
  ).toBeGreaterThanOrEqual(2);
  const h7b = await homeExpiring(page);
  expect(h7b.statCount).toBe(h7b.rowCount);
  await shot(page, 'c2-home-title7-restored', { full: true });

  await page.locator('.van-tabbar').getByText('物品', { exact: true }).click();
  await expect(page.locator('.item-card').first()).toBeVisible();
  await page.getByRole('button', { name: '筛选' }).click();
  const filterRow7b = panel.getByText('仅看 7 天内临期', { exact: true });
  await filterRow7b.scrollIntoViewIfNeeded();
  await expect(filterRow7b).toBeInViewport();
  await expect(panel.getByText('仅看 30 天内临期')).toHaveCount(0);
  await shot(page, 'c3-filter-text-7-restored');
  await panel.getByRole('button', { name: '确定' }).click();

  // ================= 场景 D：质量 —— 控制台 / 网络 =================
  const logLines = [
    '=== console errors ===',
    ...consoleErrors.map((e) => `- ${e.text} @ ${e.url}`),
    '=== page errors (uncaught) ===',
    ...pageErrors.map((t) => `- ${t}`),
    '=== http >= 400 ===',
    ...badResponses.map((t) => `- ${t}`),
    '=== failed requests ===',
    ...failedRequests.map((t) => `- ${t}`),
    '=== /api/items/expiring requests ===',
    ...expiringListReqs.map((t) => `- ${t}`),
    '=== /api/items?expiring=1 requests ===',
    ...expiringSearchReqs.map((t) => `- ${t}`),
    '=== /api/settings requests ===',
    ...settingsReqs.map((t) => `- ${t}`),
  ];
  fs.writeFileSync(path.join(EVIDENCE_DIR, 'console-network-log.txt'), logLines.join('\n'), 'utf8');

  expect(pageErrors, `未捕获 JS 异常：${pageErrors.join(' | ')}`).toEqual([]);
  // console error 白名单（均非 JS 异常）：401 = 错误密码登录分支的预期响应；
  // favicon.ico 404 = 站点未声明 favicon（已作为 OBS-F2-01 记录，见报告）
  const isAllowed = (e) => /401/.test(e.text) || /favicon\.ico/.test(e.url);
  const allowedErrors = consoleErrors.filter(isAllowed);
  const unexpected = consoleErrors.filter((e) => !isAllowed(e));
  fs.appendFileSync(
    path.join(EVIDENCE_DIR, 'console-network-log.txt'),
    `\n=== allowed console errors (${allowedErrors.length}) ===\n${allowedErrors.map((e) => `- ${e.text} @ ${e.url}`).join('\n')}\n=== unexpected console errors (${unexpected.length}) ===\n${unexpected.map((e) => `- ${e.text} @ ${e.url}`).join('\n')}`,
    'utf8',
  );
  expect(unexpected, `非预期 console error：${unexpected.map((e) => `${e.text} @ ${e.url}`).join(' | ')}`).toEqual([]);
  expect(failedRequests, `请求失败：${failedRequests.join(' | ')}`).toEqual([]);
  // 非 401 的 HTTP 错误（401 = 登录失败分支 + 未登录探测，预期内）
  const unexpectedHttp = badResponses.filter((t) => !/^401 /.test(t));
  expect(unexpectedHttp, `非预期 HTTP 错误：${unexpectedHttp.join(' | ')}`).toEqual([]);
  await shot(page, 'd1-final-console-clean', { full: true });

  // 终态：阈值必须是 7
  const sFinal = await api(page, 'GET', '/api/settings');
  expect(sFinal.data.expiry_remind_days, '结束时阈值必须恢复为 7').toBe(7);
});
