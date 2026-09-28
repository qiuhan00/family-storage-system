# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: family-storage-locations.spec.js >> family-storage location management E-LM
- Location: family-storage-locations.spec.js:76:1

# Error details

```
Error: expect(locator).toHaveText(expected) failed

Locator:  locator('.van-cell').filter({ hasText: '位置管理' }).locator('.van-cell__title')
Expected: "位置管理"
Received: "位置管理新增位置 / 填写说明与照片"
Timeout:  12000ms

Call log:
  - Expect "toHaveText" locator('.van-cell').filter({ hasText: '位置管理' }).locator('.van-cell__title') with timeout 12000ms
  - waiting for locator('.van-cell').filter({ hasText: '位置管理' }).locator('.van-cell__title')
    27 × locator resolved to <div class="van-cell__title">…</div>
       - unexpected value "位置管理新增位置 / 填写说明与照片"

```

```yaml
- text: 位置管理 新增位置 / 填写说明与照片
```

# Test source

```ts
  26  | // ---------- 步骤结果收集（供报告生成） ----------
  27  | const results = [];
  28  | function mark(id, name, status, note = '') {
  29  |   results.push({ id, name, status, note });
  30  |   console.log(`[STEP] ${status} ${id} ${name}${note ? ` — ${note}` : ''}`);
  31  | }
  32  | 
  33  | // ---------- 已登录 API 助手（前置数据 / 清理，非 UI 断言） ----------
  34  | async function api(page, method, pathname, body) {
  35  |   const token = await page.evaluate(() => localStorage.getItem('fs_token'));
  36  |   const res = await page.request.fetch(`${BASE}${pathname}`, {
  37  |     method,
  38  |     headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  39  |     data: body,
  40  |   });
  41  |   const text = await res.text();
  42  |   try { return { status: res.status(), data: text ? JSON.parse(text) : null }; }
  43  |   catch { return { status: res.status(), data: text }; }
  44  | }
  45  | 
  46  | function findLocation(nodes, name) {
  47  |   for (const n of nodes || []) {
  48  |     if (n.name === name) return n;
  49  |     const hit = findLocation(n.children, name);
  50  |     if (hit) return hit;
  51  |   }
  52  |   return null;
  53  | }
  54  | 
  55  | // ---------- 测试用图片 ----------
  56  | const tinyPng = {
  57  |   name: 'tiny.png', mimeType: 'image/png',
  58  |   buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64'),
  59  | };
  60  | const tinyJpg = {
  61  |   name: 'tiny.jpg', mimeType: 'image/jpeg',
  62  |   buffer: Buffer.from('/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigD//2Q==', 'base64'),
  63  | };
  64  | const bigPng = {
  65  |   name: 'big.png', mimeType: 'image/png',
  66  |   buffer: Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(6 * 1024 * 1024)]),
  67  | };
  68  | const txtFile = { name: 'note.txt', mimeType: 'text/plain', buffer: Buffer.from('definitely not an image') };
  69  | 
  70  | // ---------- 弹层作用域（多个 van-popup 同时在 DOM 中，需按内部容器区分） ----------
  71  | const formPanel = (page) => page.locator('.form-panel');
  72  | const parentPanel = (page) => page.locator('.parent-panel');
  73  | const toast = (page) => page.locator('.van-toast');
  74  | const row = (page, name) => page.locator('.location-row', { hasText: name });
  75  | 
  76  | test('family-storage location management E-LM', async ({ page }) => {
  77  |   test.setTimeout(300000);
  78  | 
  79  |   // ---------- 控制台 / 网络异常收集 ----------
  80  |   const consoleErrors = [];
  81  |   const pageErrors = [];
  82  |   const server5xx = [];
  83  |   const failedReqs = [];
  84  |   const errResponses = [];
  85  |   const locationWrites = [];
  86  |   const fileUploads = [];
  87  |   page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  88  |   page.on('pageerror', (e) => pageErrors.push(String(e)));
  89  |   page.on('response', (r) => {
  90  |     if (r.status() >= 400) errResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`);
  91  |     if (r.status() >= 500) server5xx.push(`${r.status()} ${r.url()}`);
  92  |   });
  93  |   page.on('request', (r) => {
  94  |     const u = r.url();
  95  |     if (u.includes('/api/locations') && ['POST', 'PUT', 'DELETE'].includes(r.method())) {
  96  |       locationWrites.push(`${r.method()} ${u}`);
  97  |     }
  98  |     if (u.includes('/api/files') && r.method() === 'POST') fileUploads.push(`POST ${u}`);
  99  |   });
  100 |   page.on('requestfailed', (r) => failedReqs.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));
  101 | 
  102 |   // ================= E-LM-01：空库注册首个账号（自动成为 ADMIN） =================
  103 |   await page.goto(`${BASE}/`);
  104 |   await expect(page).toHaveURL(/\/login$/);
  105 |   await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  106 |   await page.getByRole('button', { name: '注册新账号' }).click();
  107 |   await expect(page).toHaveURL(/\/register$/);
  108 |   await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  109 |   await page.getByPlaceholder('请输入昵称').fill(NICK);
  110 |   await page.getByPlaceholder('6-32 位').fill(PWD);
  111 |   await page.getByPlaceholder('请再次输入密码').fill(PWD);
  112 |   await shot(page, 'lm01-register-filled');
  113 |   await page.getByRole('button', { name: '注册', exact: true }).click();
  114 |   await expect(page.locator('.home-page')).toBeVisible({ timeout: 20000 });
  115 |   await expect(page.getByText('统计总览')).toBeVisible();
  116 |   await shot(page, 'lm01-home-after-register');
  117 |   const me = await api(page, 'GET', '/api/auth/me');
  118 |   expect(me.data.role).toBe('ADMIN');
  119 |   mark('E-LM-01', '空库注册首账号并自动登录（ADMIN）', 'PASS', `role=${me.data.role}`);
  120 | 
  121 |   // ================= E-LM-02：我的页入口 → /locations → 返回 =================
  122 |   await page.locator('.van-tabbar-item', { hasText: '我的' }).click();
  123 |   await expect(page).toHaveURL(/\/profile$/);
  124 |   const entry = page.locator('.van-cell', { hasText: '位置管理' });
  125 |   await expect(entry).toBeVisible();
> 126 |   await expect(entry.locator('.van-cell__title')).toHaveText('位置管理');
      |                                                   ^ Error: expect(locator).toHaveText(expected) failed
  127 |   await expect(entry.locator('.van-cell__label')).toContainText('新增位置 / 填写说明与照片');
  128 |   await shot(page, 'lm02-profile-entry');
  129 |   await entry.click();
  130 |   await expect(page).toHaveURL(/\/locations$/);
  131 |   await expect(page.locator('.van-nav-bar__title')).toHaveText('位置管理');
  132 |   const backBtn = page.getByRole('button', { name: '返回' });
  133 |   await expect(backBtn).toBeVisible();
  134 |   await shot(page, 'lm02-locations-page');
  135 |   await backBtn.click();
  136 |   await expect(page).toHaveURL(/\/profile$/);
  137 |   await shot(page, 'lm02-back-to-profile');
  138 |   mark('E-LM-02', '我的页入口 / 顶部标题 / 返回键回我的页', 'PASS');
  139 | 
  140 |   // ================= E-LM-03：空态 + 新增弹层字段 =================
  141 |   await page.locator('.van-cell', { hasText: '位置管理' }).click();
  142 |   await expect(page).toHaveURL(/\/locations$/);
  143 |   await expect(page.locator('.locations-toolbar__title')).toHaveText('共 0 个位置');
  144 |   await expect(page.locator('.van-empty__description')).toHaveText('暂无位置，点击右上角新增');
  145 |   await shot(page, 'lm03-empty-state');
  146 | 
  147 |   await page.getByRole('button', { name: '新增位置' }).click();
  148 |   const fp = formPanel(page);
  149 |   await expect(fp).toBeVisible();
  150 |   await expect(fp.locator('.form-panel__title')).toHaveText('新增位置');
  151 |   await expect(fp.getByPlaceholder('如：厨房吊柜（必填）')).toBeVisible();
  152 |   await expect(fp.locator('.van-field', { hasText: '父级位置' }).locator('input')).toHaveValue('根节点');
  153 |   const descBox = fp.locator('textarea');
  154 |   await expect(descBox).toHaveAttribute('maxlength', '500');
  155 |   await expect(fp.locator('.van-field__word-limit')).toHaveText('0/500');
  156 |   await expect(fp.locator('.uploader-block__label')).toHaveText('位置照片');
  157 |   await shot(page, 'lm03-create-form');
  158 |   mark('E-LM-03', '空态文案 + 新增弹层字段（必填名/根节点/500 字数统计/照片）', 'PASS');
  159 | 
  160 |   // ================= E-LM-04：不填名称直接保存 → 提示 + 不发请求 =================
  161 |   const postBefore = locationWrites.filter((s) => s.startsWith('POST')).length;
  162 |   await fp.getByRole('button', { name: '保存' }).click();
  163 |   await expect(page.getByText('请输入位置名称')).toBeVisible();
  164 |   const toastHit = await page.locator('.van-toast', { hasText: '请输入位置名称' }).count();
  165 |   const inlineHit = await fp.locator('.van-field__error-message', { hasText: '请输入位置名称' }).count();
  166 |   await shot(page, 'lm04-validate-empty-name');
  167 |   const postAfter = locationWrites.filter((s) => s.startsWith('POST')).length;
  168 |   expect(postAfter).toBe(postBefore);
  169 |   await expect(fp).toBeVisible(); // 弹层不关闭
  170 |   mark('E-LM-04', '名称必填校验：提示「请输入位置名称」且不发请求', 'PASS',
  171 |     `展示方式=${toastHit > 0 ? 'toast' : ''}${inlineHit > 0 ? 'inline-field-error' : ''}(toast=${toastHit}, inline=${inlineHit})`);
  172 | 
  173 |   // ================= E-LM-05：新增根位置「厨房」 =================
  174 |   await fp.getByPlaceholder('如：厨房吊柜（必填）').fill('厨房');
  175 |   await fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('吊柜顶层放干货');
  176 |   await shot(page, 'lm05-kitchen-form');
  177 |   await fp.getByRole('button', { name: '保存' }).click();
  178 |   await expect(toast(page)).toContainText('已保存');
  179 |   await expect(fp).toBeHidden();
  180 |   const kRow = row(page, '厨房');
  181 |   await expect(kRow).toBeVisible();
  182 |   await expect(kRow.locator('.location-row__name')).toHaveText('厨房');
  183 |   await expect(kRow.locator('.location-row__desc')).toHaveText('吊柜顶层放干货');
  184 |   await expect(page.locator('.locations-toolbar__title')).toHaveText('共 1 个位置');
  185 |   await shot(page, 'lm05-kitchen-created');
  186 |   mark('E-LM-05', '新增「厨房」+ 说明摘要回显', 'PASS');
  187 | 
  188 |   // ================= E-LM-06：新增子位置「吊柜」（父级改为厨房） =================
  189 |   await page.getByRole('button', { name: '新增位置' }).click();
  190 |   await expect(fp.locator('.form-panel__title')).toHaveText('新增位置');
  191 |   await fp.getByPlaceholder('如：厨房吊柜（必填）').fill('吊柜');
  192 |   await fp.locator('.van-field', { hasText: '父级位置' }).click();
  193 |   const pp = parentPanel(page);
  194 |   await expect(pp).toBeVisible();
  195 |   await expect(pp.locator('.parent-panel__title')).toHaveText('选择父级位置');
  196 |   await shot(page, 'lm06-parent-picker');
  197 |   await pp.locator('.van-cell', { hasText: '厨房' }).click();
  198 |   await expect(pp).toBeHidden();
  199 |   await expect(fp.locator('.van-field', { hasText: '父级位置' }).locator('input')).toHaveValue('厨房');
  200 |   await fp.getByRole('button', { name: '保存' }).click();
  201 |   await expect(toast(page)).toContainText('已保存');
  202 |   await expect(page.locator('.locations-toolbar__title')).toHaveText('共 2 个位置');
  203 |   const allRows = page.locator('.location-row');
  204 |   await expect(allRows).toHaveCount(2);
  205 |   await expect(allRows.nth(0).locator('.location-row__name')).toHaveText('厨房');
  206 |   await expect(allRows.nth(1).locator('.location-row__name')).toHaveText('吊柜');
  207 |   const kitchenPad = await allRows.nth(0).evaluate((el) => el.style.paddingLeft);
  208 |   const cabinetPad = await allRows.nth(1).evaluate((el) => el.style.paddingLeft);
  209 |   expect(kitchenPad).toBe('12px');
  210 |   expect(cabinetPad).toBe('28px');
  211 |   await shot(page, 'lm06-child-created-indented');
  212 |   mark('E-LM-06', '父级改为厨房创建「吊柜」+ depth 缩进', 'PASS', `padding ${kitchenPad} / ${cabinetPad}`);
  213 | 
  214 |   // ================= E-LM-07：编辑「厨房」说明 =================
  215 |   await row(page, '厨房').getByRole('button', { name: '编辑' }).click();
  216 |   await expect(fp.locator('.form-panel__title')).toHaveText('编辑位置');
  217 |   await expect(fp.getByPlaceholder('如：厨房吊柜（必填）')).toHaveValue('厨房');
  218 |   await expect(fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货')).toHaveValue('吊柜顶层放干货');
  219 |   await shot(page, 'lm07-edit-prefill');
  220 |   await fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('一层台面放锅具');
  221 |   await fp.getByRole('button', { name: '保存' }).click();
  222 |   await expect(toast(page)).toContainText('已保存');
  223 |   await expect(row(page, '厨房').locator('.location-row__desc')).toHaveText('一层台面放锅具');
  224 |   await shot(page, 'lm07-desc-updated');
  225 |   mark('E-LM-07', '编辑说明保存后列表摘要更新', 'PASS');
  226 | 
```