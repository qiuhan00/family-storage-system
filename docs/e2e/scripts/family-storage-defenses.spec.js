// family-storage-defenses.spec.js —— 终轮回归：code-review 防御分支（HEAD=11c7bc9）
// 依赖：先跑 family-storage.spec.js（全量回归）后的同一隔离库 family_storage_e2e：
//   e2eadmin 已存在且密码已被改为 E2eNew123!，member1 已启用，牛奶/洗发水已入库，
//   settings=30、洗洁精已购、e2eadmin 有一条未读通知种子。
// 覆盖：成员停用→401 登出跳 /login；ADMIN 自停用 LAST_ADMIN 400；登录连错 5 次→429；
//       真实 JPEG 带魔数上传回显 + 假魔数被后端拒绝；Tabbar/统计/角标/购物/设置 抽查。
// 不改动任何应用代码，仅产出证据与报告。
const { test, expect } = require('@playwright/test');
const path = require('path');

const EVIDENCE_DIR = process.env.EVIDENCE_DIR || path.join(__dirname, 'evidence');
const BASE = 'http://localhost:5173';
const ADMIN = 'e2eadmin';
const PWD = 'E2eNew123!'; // 回归已将 e2eadmin 密码从 E2eTest123! 改为 E2eNew123!
const MEMBER = 'member_live';
const MEMBER_PWD = 'MemberLive123!';
// 429 探针每次运行用唯一用户名，避免上一轮遗留的限流锁（lock 窗口 5 分钟）导致误判
const LOCK = `lockprobe${Date.now().toString().slice(-6)}`;

function ymd(offsetDays) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

let shotSeq = 0;
function shot(page, name, full = false) {
  shotSeq += 1;
  return page.screenshot({ path: path.join(EVIDENCE_DIR, `dstep-${String(shotSeq).padStart(2, '0')}-${name}.png`), fullPage: full });
}

async function api(page, method, pathname, body) {
  const token = await page.evaluate(() => localStorage.getItem('fs_token'));
  const res = await page.request.fetch(`${BASE}${pathname}`, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    data: body,
  });
  const text = await res.text();
  try { return { status: res.status(), data: text ? JSON.parse(text) : null }; }
  catch { return { status: res.status(), data: text }; }
}

const pickerConfirm = (page) => page.locator('.van-picker:visible .van-picker__confirm');

test('family-storage defenses final regression', async ({ browser, page }) => {
  const consoleErrors = [];
  const pageErrors = [];
  const server5xx = [];
  const failedReqs = [];
  const errResponses = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e)));
  page.on('response', (r) => { if (r.status() >= 400) errResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`); if (r.status() >= 500) server5xx.push(`${r.status()} ${r.url()}`); });
  page.on('requestfailed', (r) => failedReqs.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));

  // ================= A. ADMIN 登录 + 预置成员（live / lockprobe） =================
  await page.goto(`${BASE}/login`);
  await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  await page.getByPlaceholder('请输入密码').fill(PWD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.locator('.home-page')).toBeVisible({ timeout: 15000 });

  // 创建两个带已知密码的 MEMBER（live 用于停用 401 分支；lockprobe 用于 429 分支）
  // 幂等：重跑时成员已存在则确保 live 为启用态、密码保持一致
  let liveId = null;
  let liveCreated = await api(page, 'GET', '/api/users');
  let liveRow0 = (liveCreated.data?.content || []).find((u) => u.username === MEMBER);
  if (!liveRow0) {
    const c = await api(page, 'POST', '/api/users', { username: MEMBER, displayName: '实时成员', role: 'MEMBER', password: MEMBER_PWD });
    expect([200, 201]).toContain(c.status);
    liveId = c.data.id;
  } else {
    liveId = liveRow0.id;
    if (!liveRow0.enabled) {
      const c = await api(page, 'PATCH', `/api/users/${liveId}/status`, { enabled: true });
      expect(c.status).toBe(200);
    }
  }
  const lockCreated = await api(page, 'GET', '/api/users');
  if (!(lockCreated.data?.content || []).some((u) => u.username === LOCK)) {
    const c = await api(page, 'POST', '/api/users', { username: LOCK, displayName: '限流探针', role: 'MEMBER', password: 'LockProbe123!' });
    expect([200, 201]).toContain(c.status);
  }
  await shot(page, 'admin-login-ready');

  // ================= B. 成员停用 → 401 → 登出跳 /login（http 拦截器行为） =================
  // 主页面切换为 member_live 会话；另开 admin 上下文执行停用
  await page.locator('.van-tabbar-item', { hasText: '我的' }).click();
  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login$/, { timeout: 15000 });
  await page.getByPlaceholder('请输入用户名').fill(MEMBER);
  await page.getByPlaceholder('请输入密码').fill(MEMBER_PWD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.locator('.home-page')).toBeVisible({ timeout: 15000 });
  await shot(page, 'member-live-logged-in');

  const memberPage = page; // member 会话（主页面）
  const adminCtx = await browser.newContext({ viewport: { width: 1280, height: 720 } });
  const adminPage = await adminCtx.newPage();
  await adminPage.goto(`${BASE}/login`);
  await adminPage.getByPlaceholder('请输入用户名').fill(ADMIN);
  await adminPage.getByPlaceholder('请输入密码').fill(PWD);
  await adminPage.getByRole('button', { name: '登录' }).click();
  await expect(adminPage.locator('.home-page')).toBeVisible({ timeout: 15000 });
  await adminPage.goto(`${BASE}/users`);
  const liveRow = adminPage.locator('.user-card', { hasText: `@${MEMBER}` });
  await expect(liveRow).toBeVisible();
  await liveRow.locator('.van-switch').click(); // 停用 member_live
  await expect(liveRow).toContainText('已停用');
  await shot(adminPage, 'admin-disabled-member');

  // member 页面触发受保护操作：物品 Tab → GET /api/items → 401 USER_DISABLED → 拦截器清 token 跳 /login
  await memberPage.locator('.van-tabbar-item', { hasText: '物品' }).click();
  await expect(memberPage).toHaveURL(/\/login$/, { timeout: 15000 });
  const memberToken = await memberPage.evaluate(() => localStorage.getItem('fs_token'));
  expect(memberToken).toBeNull();
  await expect(memberPage.getByPlaceholder('请输入用户名')).toBeVisible();
  await shot(memberPage, 'member-401-logout-redirect');
  await adminCtx.close();

  // ================= C. ADMIN 自停用 → LAST_ADMIN 400，前端不崩溃 =================
  // 主页面切回 admin 会话
  await memberPage.getByPlaceholder('请输入用户名').fill(ADMIN);
  await memberPage.getByPlaceholder('请输入密码').fill(PWD);
  await memberPage.getByRole('button', { name: '登录' }).click();
  await expect(memberPage.locator('.home-page')).toBeVisible({ timeout: 15000 });

  // UI：点自己 Switch → 客户端提示（不允许停用管理员），不发请求
  await memberPage.goto(`${BASE}/users`);
  const adminRow = memberPage.locator('.user-card', { hasText: `@${ADMIN}` });
  await expect(adminRow).toBeVisible();
  await adminRow.locator('.van-switch').click();
  await expect(memberPage.getByText('不能停用管理员账户')).toBeVisible();
  await shot(memberPage, 'admin-self-switch-blocked');

  // 后端兜底：以 admin token 直连 PATCH 自身 enabled=false → 400 LAST_ADMIN
  const me = await api(memberPage, 'GET', '/api/auth/me');
  const selfId = me.data.id;
  const patch = await api(memberPage, 'PATCH', `/api/users/${selfId}/status`, { enabled: false });
  expect(patch.status).toBe(400);
  expect(patch.data.code).toBe('LAST_ADMIN');
  expect(patch.data.message).toContain('保留至少一名启用状态的管理员');

  // 前端未崩溃：仍可导航 /settings 且页面错误为空
  await memberPage.goto(`${BASE}/settings`);
  await expect(memberPage.locator('.settings-page')).toContainText('临期提醒天数');
  await shot(memberPage, 'frontend-alive-after-last-admin');

  // ================= D. 登录连错 5 次 → 6 次起 429（专用用户，避免影响 admin） =================
  // 先登出（路由器守卫生效：已登录访问 /login 会被弹回 /）
  await memberPage.goto(`${BASE}/profile`);
  await memberPage.getByRole('button', { name: '退出登录' }).click();
  await expect(memberPage).toHaveURL(/\/login$/, { timeout: 15000 });
  for (let i = 1; i <= 5; i++) {
    await memberPage.getByPlaceholder('请输入用户名').fill(LOCK);
    await memberPage.getByPlaceholder('请输入密码').fill('WrongPass99');
    await memberPage.getByRole('button', { name: '登录' }).click();
    await expect(memberPage.getByText('用户名或密码错误')).toBeVisible({ timeout: 8000 });
  }
  await shot(memberPage, 'login-5-fails');
  // 第 6 次：限流已锁定 → 429 TOO_MANY_ATTEMPTS toast
  await memberPage.getByPlaceholder('请输入密码').fill('WrongPass99');
  await memberPage.getByRole('button', { name: '登录' }).click();
  await expect(memberPage.getByText('尝试次数过多，请 5 分钟后再试')).toBeVisible({ timeout: 8000 });
  // 不卡死：仍停留登录页，登录按钮不在 loading
  await expect(memberPage).toHaveURL(/\/login$/);
  await expect(memberPage.getByRole('button', { name: '登录' })).not.toHaveClass(/van-button--loading/);
  await shot(memberPage, 'login-429-toast');
  // 其他用户不受影响：admin 仍可登录
  await memberPage.getByPlaceholder('请输入用户名').fill(ADMIN);
  await memberPage.getByPlaceholder('请输入密码').fill(PWD);
  await memberPage.getByRole('button', { name: '登录' }).click();
  await expect(memberPage.locator('.home-page')).toBeVisible({ timeout: 15000 });

  // ================= E. 真实 JPEG（带魔数）上传回显 + 假魔数被拒 =================
  await memberPage.goto(`${BASE}/items/new`);
  // 假魔数：扩展名/类型为 png，内容为 HTML → 后端魔数校验失败 → toast
  const evilPng = { name: 'evil.png', mimeType: 'image/png', buffer: Buffer.from('<html><script>alert(1)</script></html>') };
  await memberPage.locator('.van-uploader input[type=file]').setInputFiles(evilPng);
  await expect(memberPage.getByText('文件内容与声明图片类型不符（魔数校验失败）')).toBeVisible({ timeout: 8000 });
  await shot(memberPage, 'fake-magic-rejected');
  await memberPage.reload(); // 清空表单/失败项，重新开始合法上传流程

  // 合法 JPEG：1x1 白图（FF D8 FF），填表 + 回显 + 详情 /uploads 可加载
  const jpegB64 = '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q==';
  const realJpeg = { name: 'sample.jpg', mimeType: 'image/jpeg', buffer: Buffer.from(jpegB64, 'base64') };
  await memberPage.getByPlaceholder('物品名称').fill('JPEG测试');
  await memberPage.getByPlaceholder('请选择分类').click();
  await memberPage.locator('.van-picker-column__item', { hasText: '食品' }).click();
  await pickerConfirm(memberPage).click();
  await memberPage.getByPlaceholder('请选择收纳位置').click();
  await memberPage.locator('.van-cascader__option', { hasText: '厨房橱柜' }).click();
  await memberPage.locator('.van-cascader__option', { hasText: '吊柜' }).click();
  await memberPage.getByPlaceholder('0').fill('2');
  await memberPage.getByPlaceholder('如：件 / 瓶').fill('张');
  await memberPage.getByPlaceholder('可选').first().fill('1');
  await memberPage.locator('.van-uploader input[type=file]').setInputFiles(realJpeg);
  const preview = memberPage.locator('.van-uploader__preview img');
  await expect(preview).toBeVisible();
  await expect.poll(() => preview.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  await shot(memberPage, 'jpeg-upload-thumbnail');

  await memberPage.getByRole('button', { name: '新增物品' }).click();
  await expect(memberPage).toHaveURL(/\/items$/, { timeout: 15000 });
  await expect(memberPage.locator('.item-card', { hasText: 'JPEG测试' }).first()).toBeVisible();
  await memberPage.locator('.item-card', { hasText: 'JPEG测试' }).first().click();
  await expect(memberPage).toHaveURL(/\/items\/\d+$/, { timeout: 15000 });
  const detailImg = memberPage.locator('.detail-cover img');
  await expect(detailImg).toBeVisible();
  await expect.poll(() => detailImg.getAttribute('src'), { timeout: 15000 }).toContain('/uploads/');
  await expect.poll(() => detailImg.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  await shot(memberPage, 'jpeg-detail-echo');

  // ================= F. Tabbar / 统计 / 角标 / 购物 / 设置 抽查照旧绿 =================
  await memberPage.locator('.van-tabbar-item', { hasText: '首页' }).click();
  await expect(memberPage.getByText('统计总览')).toBeVisible();
  for (const label of ['物品总数', '在库总量', '总价值', '临期提醒', '待购清单']) {
    await expect(memberPage.locator('.stat-card__label', { hasText: label })).toBeVisible();
  }
  await expect(memberPage.locator('.stat-card', { hasText: '临期提醒' }).locator('.stat-card__value')).toHaveText(/^2项$/, { timeout: 15000 });
  await expect(memberPage.locator('.van-nav-bar .van-badge')).toContainText('1', { timeout: 15000 });
  const tabbar = memberPage.locator('.van-tabbar');
  for (const t of ['首页', '物品', '购物', '我的']) {
    await expect(tabbar.getByText(t, { exact: true })).toBeVisible();
  }
  await shot(memberPage, 'home-green-recheck');

  await memberPage.locator('.van-tabbar-item', { hasText: '购物' }).click();
  await expect(memberPage.locator('.shop-row', { hasText: '洗洁精' })).toContainText('已购');
  await memberPage.goto(`${BASE}/settings`);
  const sr = await api(memberPage, 'GET', '/api/settings');
  expect(sr.data.expiry_remind_days).toBe(30);
  await shot(memberPage, 'shopping-settings-green');

  // ================= G. 最终清扫：无 pageerror / 无 5xx / 仅预期 4xx =================
  await expect.poll(() => new Promise((r) => setTimeout(() => r(1), 1000))).toBe(1);
  const benign = (s) =>
    s.includes('favicon') ||
    s.includes('/api/auth/login') || // 429/401 预期分支（lockprobe 5+1 次 + 错误密码测试）
    s.includes('/api/items') || // member 停用后 /api/items 401（预期 USER_DISABLED）
    s.includes('/api/locations') || // member 停用后导航触发的 401（预期 USER_DISABLED）
    s.includes('/api/categories') || // member 停用后导航触发的 401（预期 USER_DISABLED）
    s.includes('/api/notifications/unread-count') || // member 停用后导航触发的 401（预期 USER_DISABLED）
    s.includes('/api/statistics') || // member 停用后导航触发的 401（预期 USER_DISABLED）
    s.includes('/api/users') || // 自停用 PATCH 400 LAST_ADMIN（预期防御）
    s.includes('/api/files'); // 假魔数上传 400 INVALID_FILE_TYPE（预期防御）
  const realErrs = errResponses.filter((s) => !benign(s));
  expect(pageErrors).toEqual([]);
  expect(realErrs).toEqual([]);
  expect(server5xx).toEqual([]);
  // /login ERR_ABORTED 是 401 拦截器 window.location.href='/login' 强制跳转时中断导航的预期产物
  expect(failedReqs.filter((s) => !s.includes('favicon') && !s.includes('/login'))).toEqual([]);
  console.log('DEFENSE_CONSOLE_ERRORS(INFO):', JSON.stringify(consoleErrors));
  await shot(memberPage, 'final-clean-console');
});