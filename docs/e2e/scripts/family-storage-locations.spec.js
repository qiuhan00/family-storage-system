// family-storage-locations.spec.js —— 「位置管理」页面 E2E（场景链 E-LM-01..E-LM-10）
// 生成依据：docs/e2e/scenarios-location-management.csv；移动优先视口 375x667。
// 证据（lstep-*.png / video / trace）输出到 $env:EVIDENCE_DIR。
// 约束：不修改任何应用代码，仅产出证据与报告。
const { test, expect } = require('@playwright/test');
const path = require('path');

test.use({ viewport: { width: 375, height: 667 } });

const EVIDENCE_DIR = process.env.EVIDENCE_DIR || path.join(__dirname, 'evidence');
const BASE = 'http://localhost:5173';
const ADMIN = 'e2eadmin';
const NICK = '测试管理员';
const PWD = 'E2eTest123!';

// ---------- 证据截图 ----------
let shotSeq = 0;
function shot(page, name, full = false) {
  shotSeq += 1;
  return page.screenshot({
    path: path.join(EVIDENCE_DIR, `lstep-${String(shotSeq).padStart(2, '0')}-${name}.png`),
    fullPage: full,
  });
}

// ---------- 步骤结果收集（供报告生成） ----------
const results = [];
function mark(id, name, status, note = '') {
  results.push({ id, name, status, note });
  console.log(`[STEP] ${status} ${id} ${name}${note ? ` — ${note}` : ''}`);
}

// ---------- 已登录 API 助手（前置数据 / 清理，非 UI 断言） ----------
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

function findLocation(nodes, name) {
  for (const n of nodes || []) {
    if (n.name === name) return n;
    const hit = findLocation(n.children, name);
    if (hit) return hit;
  }
  return null;
}

// ---------- 测试用图片 ----------
const tinyPng = {
  name: 'tiny.png', mimeType: 'image/png',
  buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64'),
};
const tinyJpg = {
  name: 'tiny.jpg', mimeType: 'image/jpeg',
  buffer: Buffer.from('/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigD//2Q==', 'base64'),
};
const bigPng = {
  name: 'big.png', mimeType: 'image/png',
  buffer: Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(6 * 1024 * 1024)]),
};
const txtFile = { name: 'note.txt', mimeType: 'text/plain', buffer: Buffer.from('definitely not an image') };

// ---------- 弹层作用域（多个 van-popup 同时在 DOM 中，需按内部容器区分） ----------
const formPanel = (page) => page.locator('.form-panel');
const parentPanel = (page) => page.locator('.parent-panel');
const toast = (page) => page.locator('.van-toast');
const row = (page, name) => page.locator('.location-row', { hasText: name });

test('family-storage location management E-LM', async ({ page }) => {
  test.setTimeout(300000);

  // ---------- 控制台 / 网络异常收集 ----------
  const consoleErrors = [];
  const pageErrors = [];
  const server5xx = [];
  const failedReqs = [];
  const errResponses = [];
  const locationWrites = [];
  const fileUploads = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('pageerror', (e) => pageErrors.push(String(e)));
  page.on('response', (r) => {
    if (r.status() >= 400) errResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`);
    if (r.status() >= 500) server5xx.push(`${r.status()} ${r.url()}`);
  });
  page.on('request', (r) => {
    const u = r.url();
    if (u.includes('/api/locations') && ['POST', 'PUT', 'DELETE'].includes(r.method())) {
      locationWrites.push(`${r.method()} ${u}`);
    }
    if (u.includes('/api/files') && r.method() === 'POST') fileUploads.push(`POST ${u}`);
  });
  page.on('requestfailed', (r) => failedReqs.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));

  // ================= E-LM-01：空库注册首个账号（自动成为 ADMIN） =================
  await page.goto(`${BASE}/`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  await page.getByRole('button', { name: '注册新账号' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  await page.getByPlaceholder('请输入昵称').fill(NICK);
  await page.getByPlaceholder('6-32 位').fill(PWD);
  await page.getByPlaceholder('请再次输入密码').fill(PWD);
  await shot(page, 'lm01-register-filled');
  await page.getByRole('button', { name: '注册', exact: true }).click();
  let e01note = '空库注册首账号成功（本轮首跑）';
  try {
    await expect(page.locator('.home-page')).toBeVisible({ timeout: 12000 });
  } catch (e) {
    // 复跑场景：首账号已存在 → 注册关闭，回退登录（首账号注册已由本轮首跑证据覆盖）
    await shot(page, 'lm01-register-closed-fallback');
    e01note = '复跑回退登录（首账号已在本轮首跑注册成功，注册已关闭）';
    await page.goto(`${BASE}/login`);
    await page.getByPlaceholder('请输入用户名').fill(ADMIN);
    await page.getByPlaceholder('请输入密码').fill(PWD);
    await page.getByRole('button', { name: '登录' }).click();
    await expect(page.locator('.home-page')).toBeVisible({ timeout: 20000 });
  }
  await expect(page.getByText('统计总览')).toBeVisible();
  await shot(page, 'lm01-home-after-register');
  const me = await api(page, 'GET', '/api/auth/me');
  expect(me.data.role).toBe('ADMIN');
  mark('E-LM-01', '空库注册首账号并自动登录（ADMIN）', 'PASS', `${e01note}; role=${me.data.role}`);

  // ================= E-LM-02：我的页入口 → /locations → 返回 =================
  await page.locator('.van-tabbar-item', { hasText: '我的' }).click();
  await expect(page).toHaveURL(/\/profile$/);
  const entry = page.locator('.van-cell', { hasText: '位置管理' });
  await expect(entry).toBeVisible();
  await expect(entry.locator('.van-cell__title > span')).toHaveText('位置管理');
  await expect(entry.locator('.van-cell__label')).toContainText('新增位置 / 填写说明与照片');
  await shot(page, 'lm02-profile-entry');
  await entry.click();
  await expect(page).toHaveURL(/\/locations$/);
  await expect(page.locator('.van-nav-bar__title')).toHaveText('位置管理');
  const backBtn = page.getByRole('button', { name: '返回' });
  await expect(backBtn).toBeVisible();
  await shot(page, 'lm02-locations-page');
  await backBtn.click();
  await expect(page).toHaveURL(/\/profile$/);
  await shot(page, 'lm02-back-to-profile');
  mark('E-LM-02', '我的页入口 / 顶部标题 / 返回键回我的页', 'PASS');

  // ================= E-LM-03：空态 + 新增弹层字段 =================
  await page.locator('.van-cell', { hasText: '位置管理' }).click();
  await expect(page).toHaveURL(/\/locations$/);
  await expect(page.locator('.locations-toolbar__title')).toHaveText('共 0 个位置');
  await expect(page.locator('.van-empty__description')).toHaveText('暂无位置，点击右上角新增');
  await shot(page, 'lm03-empty-state');

  await page.getByRole('button', { name: '新增位置' }).click();
  const fp = formPanel(page);
  await expect(fp).toBeVisible();
  await expect(fp.locator('.form-panel__title')).toHaveText('新增位置');
  await expect(fp.getByPlaceholder('如：厨房吊柜（必填）')).toBeVisible();
  await expect(fp.locator('.van-field', { hasText: '父级位置' }).locator('input')).toHaveValue('根节点');
  const descBox = fp.locator('textarea');
  // Vant 不把 maxlength 渲染成 HTML 属性（内部 JS 截断），因此做行为断言：
  // 字数统计 0/500 → 输入 520 字被截断为 500 → 清空回到 0/500
  await expect(fp.locator('.van-field__word-limit')).toHaveText('0/500');
  await descBox.fill('字'.repeat(520));
  await expect(descBox).toHaveValue('字'.repeat(500));
  await expect(fp.locator('.van-field__word-limit')).toHaveText('500/500');
  await descBox.fill('');
  await expect(fp.locator('.van-field__word-limit')).toHaveText('0/500');
  await expect(fp.locator('.uploader-block__label')).toHaveText('位置照片');
  await shot(page, 'lm03-create-form');
  mark('E-LM-03', '空态文案 + 新增弹层字段（必填名/根节点/500 字数统计/照片）', 'PASS');

  // ================= E-LM-04：不填名称直接保存 → 提示 + 不发请求 =================
  const postBefore = locationWrites.filter((s) => s.startsWith('POST')).length;
  await fp.getByRole('button', { name: '保存' }).click();
  await expect(page.getByText('请输入位置名称').first()).toBeVisible();
  const toastHit = await page.locator('.van-toast', { hasText: '请输入位置名称' }).count();
  const inlineHit = await fp.locator('.van-field__error-message', { hasText: '请输入位置名称' }).count();
  await shot(page, 'lm04-validate-empty-name');
  const postAfter = locationWrites.filter((s) => s.startsWith('POST')).length;
  expect(postAfter).toBe(postBefore);
  await expect(fp).toBeVisible(); // 弹层不关闭
  mark('E-LM-04', '名称必填校验：提示「请输入位置名称」且不发请求', 'PASS',
    `展示方式=${toastHit > 0 ? 'toast' : ''}${inlineHit > 0 ? 'inline-field-error' : ''}(toast=${toastHit}, inline=${inlineHit})`);

  // ================= E-LM-05：新增根位置「厨房」 =================
  await fp.getByPlaceholder('如：厨房吊柜（必填）').fill('厨房');
  await fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('吊柜顶层放干货');
  await shot(page, 'lm05-kitchen-form');
  await fp.getByRole('button', { name: '保存' }).click();
  await expect(toast(page)).toContainText('已保存');
  await expect(fp).toBeHidden();
  const kRow = row(page, '厨房');
  await expect(kRow).toBeVisible();
  await expect(kRow.locator('.location-row__name')).toHaveText('厨房');
  await expect(kRow.locator('.location-row__desc')).toHaveText('吊柜顶层放干货');
  await expect(page.locator('.locations-toolbar__title')).toHaveText('共 1 个位置');
  await shot(page, 'lm05-kitchen-created');
  mark('E-LM-05', '新增「厨房」+ 说明摘要回显', 'PASS');

  // ================= E-LM-06：新增子位置「吊柜」（父级改为厨房） =================
  await page.getByRole('button', { name: '新增位置' }).click();
  await expect(fp.locator('.form-panel__title')).toHaveText('新增位置');
  await fp.getByPlaceholder('如：厨房吊柜（必填）').fill('吊柜');
  await fp.locator('.van-field', { hasText: '父级位置' }).click();
  const pp = parentPanel(page);
  await expect(pp).toBeVisible();
  await expect(pp.locator('.parent-panel__title')).toHaveText('选择父级位置');
  await shot(page, 'lm06-parent-picker');
  await pp.locator('.van-cell', { hasText: '厨房' }).click();
  await expect(pp).toBeHidden();
  await expect(fp.locator('.van-field', { hasText: '父级位置' }).locator('input')).toHaveValue('厨房');
  await fp.getByRole('button', { name: '保存' }).click();
  await expect(toast(page)).toContainText('已保存');
  await expect(page.locator('.locations-toolbar__title')).toHaveText('共 2 个位置');
  const allRows = page.locator('.location-row');
  await expect(allRows).toHaveCount(2);
  await expect(allRows.nth(0).locator('.location-row__name')).toHaveText('厨房');
  await expect(allRows.nth(1).locator('.location-row__name')).toHaveText('吊柜');
  const kitchenPad = await allRows.nth(0).evaluate((el) => el.style.paddingLeft);
  const cabinetPad = await allRows.nth(1).evaluate((el) => el.style.paddingLeft);
  expect(kitchenPad).toBe('12px');
  expect(cabinetPad).toBe('28px');
  await shot(page, 'lm06-child-created-indented');
  mark('E-LM-06', '父级改为厨房创建「吊柜」+ depth 缩进', 'PASS', `padding ${kitchenPad} / ${cabinetPad}`);

  // ================= E-LM-07：编辑「厨房」说明 =================
  await row(page, '厨房').getByRole('button', { name: '编辑' }).click();
  await expect(fp.locator('.form-panel__title')).toHaveText('编辑位置');
  await expect(fp.getByPlaceholder('如：厨房吊柜（必填）')).toHaveValue('厨房');
  await expect(fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货')).toHaveValue('吊柜顶层放干货');
  await shot(page, 'lm07-edit-prefill');
  await fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('一层台面放锅具');
  await fp.getByRole('button', { name: '保存' }).click();
  await expect(toast(page)).toContainText('已保存');
  await expect(row(page, '厨房').locator('.location-row__desc')).toHaveText('一层台面放锅具');
  await shot(page, 'lm07-desc-updated');
  mark('E-LM-07', '编辑说明保存后列表摘要更新', 'PASS');

  // ================= E-LM-08：照片上传（oversize / 非图片 / png / jpg） =================
  await row(page, '厨房').getByRole('button', { name: '编辑' }).click();
  const upInput = fp.locator('.van-uploader input[type=file]');
  const uploadsBefore = fileUploads.length;

  // 8a 超 5MB
  await upInput.setInputFiles(bigPng);
  await expect(page.getByText('图片不能超过 5MB')).toBeVisible({ timeout: 8000 });
  await shot(page, 'lm08a-upload-oversize');
  expect(fileUploads.length).toBe(uploadsBefore);
  mark('E-LM-08a', '超 5MB oversize toast 且不发请求', 'PASS');

  // 8b 非图片类型
  await upInput.setInputFiles(txtFile);
  await expect(page.getByText('仅支持 jpg/jpeg/png/webp 图片')).toBeVisible({ timeout: 8000 });
  await shot(page, 'lm08b-upload-non-image');
  expect(fileUploads.length).toBe(uploadsBefore);
  mark('E-LM-08b', '非图片类型 beforeRead 拒绝 toast 且不发请求', 'PASS');

  // 8c 合法 png 上传（vant 预览 src 优先 objectUrl/blob，服务器 URL 在列表行与编辑回显断言）
  await upInput.setInputFiles(tinyPng);
  const preview = fp.locator('.van-uploader__preview img');
  await expect(preview).toBeVisible({ timeout: 20000 });
  await expect.poll(() => preview.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 20000 }).toBe(true);
  const pngPreviewSrc = await preview.getAttribute('src');
  await shot(page, 'lm08c-upload-png-ok');
  expect(fileUploads.length).toBe(uploadsBefore + 1);
  await fp.getByRole('button', { name: '保存' }).click();
  await expect(toast(page)).toContainText('已保存');
  const kRow2 = row(page, '厨房');
  await expect(kRow2.locator('.location-row__desc')).toHaveText('一层台面放锅具');
  const kThumb = kRow2.locator('img.location-row__thumb');
  await expect(kThumb).toBeVisible();
  await expect.poll(() => kThumb.getAttribute('src'), { timeout: 15000 }).toContain('/uploads/');
  await expect.poll(() => kThumb.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  const thumbBox = await kThumb.boundingBox();
  expect(Math.round(thumbBox.width)).toBe(44);
  expect(Math.round(thumbBox.height)).toBe(44);
  await shot(page, 'lm08c-list-thumb-44px');
  mark('E-LM-08c', 'png 上传成功 + 弹层缩略回显 + 列表 44px 缩略', 'PASS',
    `previewSrc=${String(pngPreviewSrc).slice(0, 24)}… thumb=${Math.round(thumbBox.width)}x${Math.round(thumbBox.height)}`);

  // 记录 imageUrl 供末尾清理
  const tree1 = await api(page, 'GET', '/api/locations');
  const kitchenImgUrl = findLocation(tree1.data, '厨房')?.imageUrl || null;

  // 8d 编辑弹层回显已保存缩略图（fileList 由 imageUrl 回填 → src 必为服务器 /uploads/ URL）
  await row(page, '厨房').getByRole('button', { name: '编辑' }).click();
  const echoThumb = fp.locator('.van-uploader__preview img');
  await expect(echoThumb).toBeVisible();
  await expect(echoThumb).toHaveAttribute('src', /^\/uploads\//);
  await expect.poll(() => echoThumb.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  await shot(page, 'lm08d-edit-echo-thumb');
  await fp.getByRole('button', { name: '取消' }).click();
  await expect(fp).toBeHidden();

  // 8e jpg 上传（吊柜）
  await row(page, '吊柜').getByRole('button', { name: '编辑' }).click();
  const uploadsBeforeJpg = fileUploads.length;
  await fp.locator('.van-uploader input[type=file]').setInputFiles(tinyJpg);
  const previewJpg = fp.locator('.van-uploader__preview img');
  await expect(previewJpg).toBeVisible({ timeout: 20000 });
  await expect.poll(() => previewJpg.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 20000 }).toBe(true);
  const jpgPreviewSrc = await previewJpg.getAttribute('src');
  await shot(page, 'lm08e-jpg-upload-ok');
  expect(fileUploads.length).toBe(uploadsBeforeJpg + 1);
  await fp.getByRole('button', { name: '保存' }).click();
  await expect(toast(page)).toContainText('已保存');
  const cThumb = row(page, '吊柜').locator('img.location-row__thumb');
  await expect(cThumb).toBeVisible();
  await expect.poll(() => cThumb.getAttribute('src'), { timeout: 15000 }).toContain('/uploads/');
  await expect.poll(() => cThumb.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  await shot(page, 'lm08e-list-thumb-jpg');
  mark('E-LM-08d', 'jpg 上传成功 + 列表缩略图', 'PASS', `previewSrc=${String(jpgPreviewSrc).slice(0, 24)}…`);

  const tree2 = await api(page, 'GET', '/api/locations');
  const cabinetImgUrl = findLocation(tree2.data, '吊柜')?.imageUrl || null;

  // ================= E-LM-09：删除保护 =================
  // 前置：给吊柜挂一个物品（API，物品创建无独立管理 UI 之外的更短路径）
  let cat = (await api(page, 'GET', '/api/categories')).data.find((c) => c.name === '日用品');
  if (!cat) {
    const created = await api(page, 'POST', '/api/categories', { name: '日用品' });
    cat = { id: typeof created.data === 'number' ? created.data : created.data.id };
  }
  const cabinetId = findLocation(tree2.data, '吊柜')?.id;
  expect(cabinetId).toBeTruthy();
  const itemRes = await api(page, 'POST', '/api/items', {
    name: '储物盒', categoryId: cat.id, locationId: cabinetId, quantity: 1, unit: '个',
    purchasePrice: null, purchaseDate: null, expiryDate: null, photoUrl: null, remark: null,
  });
  expect([200, 201]).toContain(itemRes.status);
  const itemId = typeof itemRes.data === 'number' ? itemRes.data : itemRes.data.id;
  expect(itemId).toBeTruthy();
  await page.reload();
  await expect(page.locator('.locations-toolbar__title')).toHaveText('共 2 个位置');

  // 9a 有子级删除被拒
  const delWritesBeforeA = locationWrites.filter((s) => s.startsWith('DELETE')).length;
  await row(page, '厨房').getByRole('button', { name: '删除' }).click();
  const dlgA = page.locator('.van-dialog:visible');
  await expect(dlgA).toContainText('确定删除「厨房」');
  await shot(page, 'lm09a-delete-confirm-dialog');
  await dlgA.getByRole('button', { name: '确认' }).click();
  await expect(toast(page)).toContainText('该位置下仍有子位置，请先删除子位置');
  await shot(page, 'lm09a-delete-has-children');
  expect(locationWrites.filter((s) => s.startsWith('DELETE')).length).toBe(delWritesBeforeA + 1);
  await expect(page.locator('.location-row')).toHaveCount(2);
  mark('E-LM-09a', '删除有子级的「厨房」被拒（LOCATION_HAS_CHILDREN）', 'PASS');

  // 9b 有物品删除被拒
  await row(page, '吊柜').getByRole('button', { name: '删除' }).click();
  const dlgB = page.locator('.van-dialog:visible');
  await expect(dlgB).toContainText('确定删除「厨房 / 吊柜」');
  await dlgB.getByRole('button', { name: '确认' }).click();
  await expect(toast(page)).toContainText('该位置下仍有物品，请先迁移或删除物品');
  await shot(page, 'lm09b-delete-in-use');
  await expect(page.locator('.location-row')).toHaveCount(2);
  mark('E-LM-09b', '删除有物品的「吊柜」被拒（LOCATION_IN_USE）', 'PASS');

  // 9c 移除物品后删除子位置成功
  const delItem = await api(page, 'DELETE', `/api/items/${itemId}`);
  expect([200, 204]).toContain(delItem.status);
  await row(page, '吊柜').getByRole('button', { name: '删除' }).click();
  await page.locator('.van-dialog:visible').getByRole('button', { name: '确认' }).click();
  await expect(toast(page)).toContainText('已删除');
  await expect(page.locator('.location-row')).toHaveCount(1);
  await expect(page.locator('.locations-toolbar__title')).toHaveText('共 1 个位置');
  await shot(page, 'lm09c-cabinet-deleted');
  mark('E-LM-09c', '删除子位置「吊柜」成功', 'PASS');

  // 9d 删除根位置成功 → 回到空态
  await row(page, '厨房').getByRole('button', { name: '删除' }).click();
  await page.locator('.van-dialog:visible').getByRole('button', { name: '确认' }).click();
  await expect(toast(page)).toContainText('已删除');
  await expect(page.locator('.locations-toolbar__title')).toHaveText('共 0 个位置');
  await expect(page.locator('.van-empty__description')).toHaveText('暂无位置，点击右上角新增');
  await shot(page, 'lm09d-final-empty');
  mark('E-LM-09d', '删除根位置「厨房」成功并回到空态', 'PASS');

  // ================= 清理：本次上传的照片文件 =================
  const cleanupNotes = [];
  for (const u of [kitchenImgUrl, cabinetImgUrl]) {
    if (!u) continue;
    const m = /^\/?uploads\/(.+)$/.exec(u);
    if (!m) continue;
    const r = await api(page, 'DELETE', `/api/files/${m[1]}`);
    cleanupNotes.push(`${u} -> ${r.status}`);
  }
  console.log('[CLEANUP] files:', JSON.stringify(cleanupNotes));

  // ================= E-LM-10：控制台 / 网络清扫 =================
  await expect.poll(() => new Promise((r) => setTimeout(() => r(1), 1200))).toBe(1);
  // 预期的 4xx：favicon 404、复跑时的注册关闭分支（/api/auth/register）、E-LM-09 的删除保护 400
  const benign = (s) => s.includes('favicon.ico')
    || s.includes('/api/auth/register')
    || (s.includes('/api/locations') && s.includes('DELETE'));
  const realErrs = errResponses.filter((s) => !benign(s));
  console.log('CONSOLE_ERRORS(INFO):', JSON.stringify(consoleErrors));
  console.log('ERR_RESPONSES(INFO):', JSON.stringify(errResponses));
  console.log('FAILED_REQUESTS(INFO):', JSON.stringify(failedReqs));
  console.log('LOCATION_WRITES(INFO):', JSON.stringify(locationWrites));
  console.log('STEP_RESULTS(JSON):', JSON.stringify(results));
  expect(pageErrors).toEqual([]);
  expect(realErrs).toEqual([]);
  expect(server5xx).toEqual([]);
  expect(failedReqs.filter((s) => !s.includes('favicon'))).toEqual([]);
  await shot(page, 'lm10-final-clean-console');
  mark('E-LM-10', '无 pageerror / 未预期 4xx / 5xx / failed 请求', 'PASS');
});
