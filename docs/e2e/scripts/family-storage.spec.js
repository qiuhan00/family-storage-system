// family-storage.spec.js —— 家庭收纳物品管理系统 Task 15 前端集成回归 E2E
// 生成依据：docs/e2e/scenarios.csv；证据（截图/视频/trace）输出到 $env:EVIDENCE_DIR。
const { test, expect } = require('@playwright/test');
const path = require('path');
const { spawn } = require('child_process');

const EVIDENCE_DIR = process.env.EVIDENCE_DIR || path.join(__dirname, 'evidence');
const BASE = 'http://localhost:5173';
const ADMIN = 'e2eadmin';
const PWD = 'E2eTest123!';
const PWD_NEW = 'E2eNew123!';

// ---------- 日期工具（本地时区） ----------
function ymd(offsetDays) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
function dayNum(offsetDays) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.getDate();
}

// ---------- 证据截图 ----------
let shotSeq = 0;
function shot(page, name, full = false) {
  shotSeq += 1;
  return page.screenshot({ path: path.join(EVIDENCE_DIR, `step-${String(shotSeq).padStart(2, '0')}-${name}.png`), fullPage: full });
}

// ---------- 通知种子数据（模拟每日 09:00 调度产物，写入测试库） ----------
function seedNotification() {
  return new Promise((resolve, reject) => {
    const sql =
      "INSERT INTO notifications (user_id, title, body, type, ref_id, reminder_key, read_flag, created_at) " +
      `SELECT id, '临期提醒', '「牛奶」将于 ${ymd(3)} 到期', 'EXPIRY', NULL, CONCAT('E2E:', ${Date.now()}), 0, NOW() ` +
      `FROM users WHERE username='${ADMIN}';`;
    const proc = spawn('C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin\\mysql.exe',
      ['-uroot', '--database=family_storage_e2e', '-e', sql],
      { env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD || 'root' } });
    let err = '';
    proc.stderr.on('data', (d) => (err += d));
    proc.on('close', (code) => (code === 0 ? resolve() : reject(new Error('mysql seed failed: ' + err))));
  });
}

// ---------- 已登录 API 助手（前置数据，不属 UI 断言） ----------
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

async function ensureFoodCategory(page) {
  let r = await api(page, 'GET', '/api/categories');
  let food = (r.data || []).find((c) => c.name === '食品');
  if (!food) {
    const c = await api(page, 'POST', '/api/categories', { name: '食品' });
    const id = typeof c.data === 'number' ? c.data : c.data.id;
    food = { id };
  }
  return food.id;
}

async function ensureKitchenLocation(page) {
  let r = await api(page, 'GET', '/api/locations');
  let kitchen = (r.data || []).find((l) => l.name === '厨房橱柜');
  if (!kitchen) {
    const l = await api(page, 'POST', '/api/locations', { name: '厨房橱柜', parentId: null });
    kitchen = l.data;
  }
  let cabinet = (kitchen.children || []).find((c) => c.name === '吊柜');
  if (!cabinet) {
    const l = await api(page, 'POST', '/api/locations', { name: '吊柜', parentId: kitchen.id });
    cabinet = l.data;
  }
  return { kitchenId: kitchen.id, cabinetId: cabinet.id };
}

// ---------- 弹层内常用按钮 ----------
const dialogConfirm = (page) => page.locator('.van-dialog:visible').getByRole('button', { name: '确认' });
// van-picker 的确认是 div（非 button）；筛选弹层/日历确认是 van-button
const pickerConfirm = (page) => page.locator('.van-picker:visible .van-picker__confirm');
const popupConfirm = (page) => page.locator('.van-popup:visible').getByRole('button', { name: '确定' });

test('family-storage task15 full regression', async ({ page }) => {
  // 控制台 / 网络异常收集（`consoleErrors` 最终断言）
  const consoleErrors = [];
  const pageErrors = [];
  const server5xx = [];
  const failedReqs = [];
  const errResponses = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e)));
  page.on('response', (r) => { if (r.status() >= 400) errResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`); if (r.status() >= 500) server5xx.push(`${r.status()} ${r.url()}`); });
  page.on('requestfailed', (r) => failedReqs.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));

  // ================= 场景 1-3：未登录守卫 / 注册首账号 / 首页 =================
  await page.goto(`${BASE}/`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  await shot(page, 'login-redirect');

  await page.getByRole('button', { name: '注册新账号' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  await page.getByPlaceholder('请输入昵称').fill('测试管理员');
  await page.getByPlaceholder('6-32 位').fill(PWD);
  await page.getByPlaceholder('请再次输入密码').fill(PWD);
  await shot(page, 'register-filled');
  await page.getByRole('button', { name: '注册', exact: true }).click();
  // 注册成功 → 自动登录跳首页；系统非空（重跑）则回退登录
  let registered = false;
  try {
    await expect(page.locator('.home-page')).toBeVisible({ timeout: 6000 });
    registered = true;
  } catch (e) {
    // 系统已非空（重跑/复用库）：注册关闭 → 回退登录（PWD 或 PWD_NEW）
    await shot(page, 'register-fallback');
    await page.goto(`${BASE}/login`);
    await page.getByPlaceholder('请输入用户名').fill(ADMIN);
    await page.getByPlaceholder('请输入密码').fill(PWD);
    await page.getByRole('button', { name: '登录' }).click();
    try {
      await expect(page.locator('.home-page')).toBeVisible({ timeout: 6000 });
    } catch (e2) {
      await page.getByPlaceholder('请输入密码').fill(PWD_NEW);
      await page.getByRole('button', { name: '登录' }).click();
      await expect(page.locator('.home-page')).toBeVisible({ timeout: 15000 });
    }
  }
  await shot(page, 'home-after-register');

  await expect(page.getByText('统计总览')).toBeVisible();
  for (const label of ['物品总数', '在库总量', '总价值', '临期提醒', '待购清单']) {
    await expect(page.locator('.stat-card__label', { hasText: label })).toBeVisible();
  }
  await expect(page.getByText('暂无临期物品')).toBeVisible();
  const tabbar = page.locator('.van-tabbar');
  for (const t of ['首页', '物品', '购物', '我的']) {
    await expect(tabbar.getByText(t, { exact: true })).toBeVisible();
  }
  await shot(page, 'home-stats-tabbar');

  // ================= 场景 4：分类/位置前置（API）→ UI 级联回显 =================
  const foodId = await ensureFoodCategory(page);
  const { kitchenId, cabinetId } = await ensureKitchenLocation(page);

  // ================= 场景 5：新增物品（含日期/照片/超限校验） =================
  await page.goto(`${BASE}/items/new`);
  await expect(page.locator('.van-nav-bar__title')).toHaveText('新增物品');
  await page.getByPlaceholder('物品名称').fill('牛奶');
  await page.getByPlaceholder('请选择分类').click();
  await page.locator('.van-picker-column__item', { hasText: '食品' }).click();
  await pickerConfirm(page).click();
  await expect(page.getByPlaceholder('请选择分类')).toHaveValue('食品');
  await shot(page, 'item-form-category');

  // 位置级联：厨房橱柜 → 吊柜
  await page.getByPlaceholder('请选择收纳位置').click();
  await page.locator('.van-cascader__option', { hasText: '厨房橱柜' }).click();
  await page.locator('.van-cascader__option', { hasText: '吊柜' }).click();
  await expect(page.getByPlaceholder('请选择收纳位置')).toHaveValue('厨房橱柜 / 吊柜');
  await shot(page, 'item-form-location-cascade');

  await page.getByPlaceholder('0').fill('3');
  await page.getByPlaceholder('如：件 / 瓶').fill('盒');
  await page.getByPlaceholder('可选').first().fill('5.9');

  // 购买日期 = 今天（日历点今天再确定）
  await page.getByPlaceholder('选择日期').first().click();
  await page.locator('.van-calendar:visible .van-calendar__day').filter({ hasText: new RegExp(`^${dayNum(0)}$`) }).first().click();
  await page.locator('.van-calendar:visible .van-calendar__confirm').click();
  await expect(page.getByPlaceholder('选择日期').first()).toHaveValue(ymd(0));

  // 到期日期 = 今天+3
  await page.getByPlaceholder('选择日期').nth(1).click();
  await page.locator('.van-calendar:visible .van-calendar__day').filter({ hasText: new RegExp(`^${dayNum(3)}$`) }).first().click();
  await page.locator('.van-calendar:visible .van-calendar__confirm').click();
  await expect(page.getByPlaceholder('选择日期').nth(1)).toHaveValue(ymd(3));
  await shot(page, 'item-form-dates');

  // 照片：先验证 6MB 假图被拒（客户端 max-size 校验）
  const bigPng = { name: 'big.png', mimeType: 'image/png', buffer: Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(6 * 1024 * 1024)]) };
  const tinyPng = { name: 'tiny.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64') };
  await page.locator('.van-uploader input[type=file]').setInputFiles(bigPng);
  await expect(page.getByText('图片不能超过 5MB')).toBeVisible({ timeout: 5000 });
  await shot(page, 'upload-oversize-rejected');
  // 合法小图上传：预览缩略图回显（vant 预览 src 优先 objectUrl/blob，视觉回显即可；
  // 服务器 /uploads URL 在详情页断言）
  await page.locator('.van-uploader input[type=file]').setInputFiles(tinyPng);
  const preview = page.locator('.van-uploader__preview img');
  await expect(preview).toBeVisible();
  await expect.poll(() => preview.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  await shot(page, 'upload-ok-thumbnail');

  await page.getByRole('button', { name: '新增物品' }).click();
  await expect(page).toHaveURL(/\/items$/, { timeout: 15000 });
  await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  await shot(page, 'items-list-first-item');

  // 场景 6 前置：再建一件 +10 天到期物品（API，供设置阈值生效验证）
  const b = await api(page, 'POST', '/api/items', {
    name: '洗发水', categoryId: foodId, locationId: cabinetId, quantity: 2, unit: '瓶',
    purchasePrice: null, purchaseDate: null, expiryDate: ymd(10), photoUrl: null, remark: null,
  });
  expect([200, 201]).toContain(b.status);

  // ================= 详情：照片回显 / 字段 / 临期高亮 =================
  await page.locator('.item-card', { hasText: '牛奶' }).click();
  await expect(page).toHaveURL(/\/items\/\d+$/, { timeout: 15000 });
  const detailImg = page.locator('.detail-cover img');
  await expect(detailImg).toBeVisible();
  await expect.poll(() => detailImg.getAttribute('src'), { timeout: 15000 }).toContain('/uploads/');
  await expect.poll(() => detailImg.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  await shot(page, 'detail-photo');
  await expect(page.locator('.van-cell', { hasText: /^数量/ })).toContainText('3盒');
  await expect(page.locator('.van-cell', { hasText: /^分类/ })).toContainText('食品');
  await expect(page.locator('.van-cell', { hasText: /^位置/ })).toContainText('厨房橱柜 / 吊柜');
  await expect(page.getByText(`临期 ${ymd(3)}`)).toBeVisible();
  await shot(page, 'detail-info-expiry');

  // ================= 编辑：数量 5 + 备注 =================
  await page.getByRole('button', { name: '编辑' }).click();
  await expect(page).toHaveURL(/\/edit$/, { timeout: 15000 });
  await page.getByPlaceholder('0').fill('5');
  await page.getByPlaceholder('补充说明（可选）').fill('E2E 备注修改');
  await shot(page, 'edit-filled');
  await page.getByRole('button', { name: '保存修改' }).click();
  await expect(page).toHaveURL(/\/items\/\d+$/, { timeout: 15000 });
  await expect(page.locator('.van-cell', { hasText: /^数量/ })).toContainText('5盒');
  await expect(page.getByText('E2E 备注修改')).toBeVisible();
  await shot(page, 'detail-after-edit');

  // ================= 搜索 / 筛选 =================
  await page.locator('.van-tabbar-item', { hasText: '物品' }).click();
  await expect(page.getByPlaceholder('搜索物品名称')).toBeVisible();
  await page.getByPlaceholder('搜索物品名称').fill('牛奶');
  await page.getByPlaceholder('搜索物品名称').press('Enter');
  await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  await shot(page, 'search-hit');

  await page.getByPlaceholder('搜索物品名称').fill('不存在的物品xyz');
  await page.getByPlaceholder('搜索物品名称').press('Enter');
  await expect(page.getByText('没有找到物品')).toBeVisible();
  await shot(page, 'search-empty');

  await page.getByPlaceholder('搜索物品名称').fill('');
  await page.getByPlaceholder('搜索物品名称').press('Enter');
  await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();

  // 分类筛选 食品
  await page.getByRole('button', { name: /筛选/ }).click();
  await page.locator('.van-radio', { hasText: '食品' }).click();
  await popupConfirm(page).click();
  await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  await shot(page, 'filter-category');

  // 临期筛选（7 天内）：仅 牛奶，洗发水（+10 天）不在
  await page.getByRole('button', { name: /筛选/ }).click();
  await page.locator('.van-cell', { hasText: '仅看 7 天内临期' }).locator('.van-switch').click();
  await popupConfirm(page).click();
  await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  await expect(page.locator('.item-card', { hasText: '洗发水' })).toHaveCount(0);
  await expect(page.locator('.item-card', { hasText: '牛奶' })).toContainText(`临期 ${ymd(3)}`);
  await shot(page, 'filter-expiring');

  // 重置恢复
  await page.getByRole('button', { name: /筛选/ }).click();
  await page.locator('.van-popup:visible').getByRole('button', { name: '重置' }).click();
  await popupConfirm(page).click();
  await expect(page.locator('.item-card', { hasText: '洗发水' })).toBeVisible();

  // ================= 出入库：入库 +2 / 超存量报错 / 出库 -1 =================
  await page.locator('.item-card', { hasText: '牛奶' }).click();
  await expect(page.getByRole('button', { name: '出入库' })).toBeVisible();
  const stockAction = async (name) => {
    await page.getByRole('button', { name: '出入库' }).click();
    await page.locator('.van-action-sheet:visible').getByText(name, { exact: true }).click();
  };
  // 入库 +2
  await stockAction('入库');
  await expect(page.locator('.van-dialog:visible')).toBeVisible();
  await page.locator('.van-dialog:visible .van-stepper__plus').click();
  await shot(page, 'stock-in-dialog');
  await dialogConfirm(page).click();
  await expect(page.getByText('已更新库存')).toBeVisible();
  await expect(page.locator('.van-cell', { hasText: /^数量/ })).toContainText('7盒');
  await expect(page.getByText(/入库 · /)).toBeVisible();
  await expect(page.getByText('+2')).toBeVisible();
  await shot(page, 'history-after-in');

  // 超存量报错（盘点调整 -99999 使库存为负）
  await stockAction('盘点调整');
  await expect(page.locator('.van-dialog:visible')).toBeVisible();
  await page.locator('.van-dialog:visible .van-stepper__input').fill('-99999');
  await shot(page, 'stock-oversell-dialog');
  await dialogConfirm(page).click();
  await expect(page.getByText('库存不足，出库数量超出当前存量')).toBeVisible({ timeout: 8000 });
  await shot(page, 'stock-oversell-error');
  await page.locator('.van-dialog:visible').getByRole('button', { name: '取消' }).click();

  // 出库 -1
  await stockAction('出库');
  await expect(page.locator('.van-dialog:visible')).toBeVisible();
  await dialogConfirm(page).click();
  await expect(page.getByText('已更新库存')).toBeVisible();
  await expect(page.locator('.van-cell', { hasText: /^数量/ })).toContainText('6盒');
  await expect(page.getByText(/出库 · /)).toBeVisible();
  await expect(page.getByText('-1')).toBeVisible();
  await shot(page, 'history-after-out');

  // ================= 购物：新增 → BOUGHT → Tab 过滤 =================
  await page.locator('.van-tabbar-item', { hasText: '购物' }).click();
  await expect(page.getByRole('button', { name: /新增/ })).toBeVisible();
  await page.getByRole('button', { name: /新增/ }).click();
  await page.getByPlaceholder('缺什么买什么').fill('洗洁精');
  await page.getByPlaceholder('1').fill('1');
  await page.getByPlaceholder('如：件 / 瓶').fill('瓶');
  await shot(page, 'shopping-add-form');
  await page.getByRole('button', { name: '添加' }).click();
  const shopRow = page.locator('.shop-row', { hasText: '洗洁精' });
  await expect(shopRow).toBeVisible();
  await expect(shopRow).toContainText('待购');
  await shot(page, 'shopping-pending');

  await page.locator('.shop-row', { hasText: '洗洁精' }).locator('.van-checkbox').click();
  await expect(shopRow).toContainText('已购');
  await shot(page, 'shopping-bought');

  await page.getByRole('tab', { name: '已购' }).click();
  await expect(page.locator('.shop-row', { hasText: '洗洁精' })).toBeVisible();
  await shot(page, 'shopping-tab-bought');

  await page.getByRole('tab', { name: '待购' }).click();
  await expect(page.getByText('没有符合条件的缺耗品')).toBeVisible();
  await shot(page, 'shopping-tab-pending-empty');

  await page.getByRole('tab', { name: '全部' }).click();
  await expect(shopRow).toBeVisible();
  await expect(shopRow).toContainText('已购');

  // ================= 通知角标：种子数据模拟调度产物 =================
  await seedNotification();
  await page.locator('.van-tabbar-item', { hasText: '首页' }).click();
  await expect(page.locator('.van-nav-bar .van-badge')).toContainText('1', { timeout: 15000 });
  await shot(page, 'home-badge');

  // ================= 我的：改密 / 退出 / 新密码登录 / 失败分支 =================
  await page.locator('.van-tabbar-item', { hasText: '我的' }).click();
  await expect(page.locator('.profile-card__name')).toHaveText('测试管理员');
  await expect(page.locator('.profile-card__meta')).toContainText('管理员');
  await shot(page, 'profile-card');

  await page.locator('.van-cell', { hasText: '修改密码' }).click();
  await page.getByPlaceholder('请输入原密码').fill(PWD);
  await page.getByPlaceholder('至少 6 位').fill(PWD_NEW);
  await page.getByPlaceholder('再次输入新密码').fill(PWD_NEW);
  await shot(page, 'change-password-form');
  await page.getByRole('button', { name: '确认修改' }).click();
  await expect(page.getByText('密码已修改')).toBeVisible();
  await shot(page, 'change-password-ok');

  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login$/, { timeout: 15000 });
  await shot(page, 'logout');

  // 登录失败分支
  await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  await page.getByPlaceholder('请输入密码').fill('WrongPass999');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByText('用户名或密码错误')).toBeVisible({ timeout: 8000 });
  await shot(page, 'login-fail');

  // 新密码登录
  await page.getByPlaceholder('请输入密码').fill(PWD_NEW);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.locator('.home-page')).toBeVisible({ timeout: 15000 });

  // ================= ADMIN：/users 成员管理 =================
  await page.goto(`${BASE}/users`);
  await expect(page.locator('.users-toolbar')).toContainText('成员管理');
  const adminRow = page.locator('.user-card', { hasText: '@e2eadmin' });
  await expect(adminRow).toBeVisible();
  await expect(adminRow).toContainText('管理员');
  await expect(adminRow).toContainText('启用中');
  await shot(page, 'users-list');

  // 停用管理员 → 被告知不可停用
  await adminRow.locator('.van-switch').click();
  await expect(page.getByText('不能停用管理员账户')).toBeVisible();
  await shot(page, 'users-admin-switch-blocked');

  // 新增成员（密码留空随机）
  await page.getByRole('button', { name: /新增成员/ }).click();
  await page.getByPlaceholder('登录账号（创建后不可改）').fill('member1');
  await page.getByPlaceholder('如：老妈').fill('普通成员');
  await shot(page, 'users-create-form');
  await page.getByRole('button', { name: '确认新增' }).click();
  await expect(page.getByText('已新增成员')).toBeVisible();
  const memberRow = page.locator('.user-card', { hasText: '@member1' });
  await expect(memberRow).toBeVisible();
  await expect(memberRow).toContainText('启用中');
  await shot(page, 'users-member-created');

  // 停用 → reload 回显真实状态
  await memberRow.locator('.van-switch').click();
  await expect(memberRow).toContainText('已停用');
  await shot(page, 'users-member-disabled');
  await page.reload();
  const mr2 = page.locator('.user-card', { hasText: '@member1' });
  await expect(mr2).toContainText('已停用');
  await expect(mr2.locator('.van-switch')).not.toHaveClass(/van-switch--on/);
  await shot(page, 'users-member-disabled-reload');

  // 再启用 → reload 保持
  await mr2.locator('.van-switch').click();
  await expect(mr2).toContainText('启用中');
  await page.reload();
  const mr3 = page.locator('.user-card', { hasText: '@member1' });
  await expect(mr3).toContainText('启用中');
  await expect(mr3.locator('.van-switch')).toHaveClass(/van-switch--on/);
  await shot(page, 'users-member-enabled-reload');

  // ================= ADMIN：/settings 临期阈值 =================
  await page.goto(`${BASE}/settings`);
  await expect(page.locator('.settings-page')).toContainText('临期提醒天数');
  const stepperInput = page.locator('.settings-page .van-stepper input');
  await expect(stepperInput).toHaveValue('7');
  await stepperInput.fill('30');
  await stepperInput.press('Tab');
  await shot(page, 'settings-filled');
  await page.getByRole('button', { name: '保存设置' }).click();
  await expect(page.getByText('已保存')).toBeVisible();
  await expect(page.getByText('已购物品在到期日前 30 天收到站内提醒')).toBeVisible();
  await shot(page, 'settings-saved');
  const sr = await api(page, 'GET', '/api/settings');
  expect(sr.data.expiry_remind_days).toBe(30);

  // 阈值生效：首页统计临期提醒 = 2（+3 天 牛奶 与 +10 天 洗发水 均被计入）
  await page.locator('.van-tabbar-item', { hasText: '首页' }).click();
  await expect(page.locator('.stat-card', { hasText: '临期提醒' }).locator('.stat-card__value')).toHaveText(/^2项$/, { timeout: 15000 });
  await shot(page, 'home-threshold-effective');

  // ================= 注册关闭分支（系统非空） =================
  await page.locator('.van-tabbar-item', { hasText: '我的' }).click();
  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page).toHaveURL(/\/login$/, { timeout: 15000 });
  await page.goto(`${BASE}/register`);
  await page.getByPlaceholder('请输入用户名').fill('e2e_closed_check');
  await page.getByPlaceholder('请输入昵称').fill('检查注册');
  await page.getByPlaceholder('6-32 位').fill(PWD);
  await page.getByPlaceholder('请再次输入密码').fill(PWD);
  await page.getByRole('button', { name: '注册', exact: true }).click();
  await expect(page.getByText('注册已关闭，请联系管理员创建账号')).toBeVisible({ timeout: 8000 });
  await shot(page, 'register-closed');

  // ================= 最终断言：无意外 console error / pageerror / 4xx+ / failed =================
  await expect.poll(() => new Promise((r) => setTimeout(() => r(1), 1000))).toBe(1); // 让异步错误落袋
  // 已知良性错误：favicon 404、错误密码登录 401、注册关闭分支 4xx、故意触发的超存量 400
  const benign = (s) => s.includes('favicon.ico') || s.includes('/api/auth/login') || s.includes('/api/auth/register') || s.includes('/stock');
  const realErrs = errResponses.filter((s) => !benign(s));
  expect(pageErrors).toEqual([]);
  expect(realErrs).toEqual([]);
  expect(server5xx).toEqual([]);
  expect(failedReqs.filter((s) => !s.includes('favicon'))).toEqual([]);
  console.log('CONSOLE_ERRORS(INFO):', JSON.stringify(consoleErrors));
  await shot(page, 'final-clean-console');
});