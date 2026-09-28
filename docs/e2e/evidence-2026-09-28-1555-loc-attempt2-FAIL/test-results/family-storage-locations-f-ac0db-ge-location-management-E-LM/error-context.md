# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: family-storage-locations.spec.js >> family-storage location management E-LM
- Location: family-storage-locations.spec.js:76:1

# Error details

```
Error: expect(locator).toHaveAttribute(expected) failed

Locator:  locator('.form-panel').locator('textarea')
Expected: "500"
Received: ""
Timeout:  12000ms

Call log:
  - Expect "toHaveAttribute" locator('.form-panel').locator('textarea') with timeout 12000ms
  - waiting for locator('.form-panel').locator('textarea')
    28 × locator resolved to <textarea rows="3" id="van-field-9-input" placeholder="具体摆放说明，如：吊柜顶层放干货" data-allow-mismatch="attribute" aria-labelledby="van-field-9-label" class="van-field__control van-field__control--min-height"></textarea>
       - unexpected value "null"

```

```yaml
- textbox "说明":
  - /placeholder: 具体摆放说明，如：吊柜顶层放干货
```

# Test source

```ts
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
  114 |   let e01note = '空库注册首账号成功（本轮首跑）';
  115 |   try {
  116 |     await expect(page.locator('.home-page')).toBeVisible({ timeout: 12000 });
  117 |   } catch (e) {
  118 |     // 复跑场景：首账号已存在 → 注册关闭，回退登录（首账号注册已由本轮首跑证据覆盖）
  119 |     await shot(page, 'lm01-register-closed-fallback');
  120 |     e01note = '复跑回退登录（首账号已在本轮首跑注册成功，注册已关闭）';
  121 |     await page.goto(`${BASE}/login`);
  122 |     await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  123 |     await page.getByPlaceholder('请输入密码').fill(PWD);
  124 |     await page.getByRole('button', { name: '登录' }).click();
  125 |     await expect(page.locator('.home-page')).toBeVisible({ timeout: 20000 });
  126 |   }
  127 |   await expect(page.getByText('统计总览')).toBeVisible();
  128 |   await shot(page, 'lm01-home-after-register');
  129 |   const me = await api(page, 'GET', '/api/auth/me');
  130 |   expect(me.data.role).toBe('ADMIN');
  131 |   mark('E-LM-01', '空库注册首账号并自动登录（ADMIN）', 'PASS', `${e01note}; role=${me.data.role}`);
  132 | 
  133 |   // ================= E-LM-02：我的页入口 → /locations → 返回 =================
  134 |   await page.locator('.van-tabbar-item', { hasText: '我的' }).click();
  135 |   await expect(page).toHaveURL(/\/profile$/);
  136 |   const entry = page.locator('.van-cell', { hasText: '位置管理' });
  137 |   await expect(entry).toBeVisible();
  138 |   await expect(entry.locator('.van-cell__title > span')).toHaveText('位置管理');
  139 |   await expect(entry.locator('.van-cell__label')).toContainText('新增位置 / 填写说明与照片');
  140 |   await shot(page, 'lm02-profile-entry');
  141 |   await entry.click();
  142 |   await expect(page).toHaveURL(/\/locations$/);
  143 |   await expect(page.locator('.van-nav-bar__title')).toHaveText('位置管理');
  144 |   const backBtn = page.getByRole('button', { name: '返回' });
  145 |   await expect(backBtn).toBeVisible();
  146 |   await shot(page, 'lm02-locations-page');
  147 |   await backBtn.click();
  148 |   await expect(page).toHaveURL(/\/profile$/);
  149 |   await shot(page, 'lm02-back-to-profile');
  150 |   mark('E-LM-02', '我的页入口 / 顶部标题 / 返回键回我的页', 'PASS');
  151 | 
  152 |   // ================= E-LM-03：空态 + 新增弹层字段 =================
  153 |   await page.locator('.van-cell', { hasText: '位置管理' }).click();
  154 |   await expect(page).toHaveURL(/\/locations$/);
  155 |   await expect(page.locator('.locations-toolbar__title')).toHaveText('共 0 个位置');
  156 |   await expect(page.locator('.van-empty__description')).toHaveText('暂无位置，点击右上角新增');
  157 |   await shot(page, 'lm03-empty-state');
  158 | 
  159 |   await page.getByRole('button', { name: '新增位置' }).click();
  160 |   const fp = formPanel(page);
  161 |   await expect(fp).toBeVisible();
  162 |   await expect(fp.locator('.form-panel__title')).toHaveText('新增位置');
  163 |   await expect(fp.getByPlaceholder('如：厨房吊柜（必填）')).toBeVisible();
  164 |   await expect(fp.locator('.van-field', { hasText: '父级位置' }).locator('input')).toHaveValue('根节点');
  165 |   const descBox = fp.locator('textarea');
> 166 |   await expect(descBox).toHaveAttribute('maxlength', '500');
      |                         ^ Error: expect(locator).toHaveAttribute(expected) failed
  167 |   await expect(fp.locator('.van-field__word-limit')).toHaveText('0/500');
  168 |   await expect(fp.locator('.uploader-block__label')).toHaveText('位置照片');
  169 |   await shot(page, 'lm03-create-form');
  170 |   mark('E-LM-03', '空态文案 + 新增弹层字段（必填名/根节点/500 字数统计/照片）', 'PASS');
  171 | 
  172 |   // ================= E-LM-04：不填名称直接保存 → 提示 + 不发请求 =================
  173 |   const postBefore = locationWrites.filter((s) => s.startsWith('POST')).length;
  174 |   await fp.getByRole('button', { name: '保存' }).click();
  175 |   await expect(page.getByText('请输入位置名称').first()).toBeVisible();
  176 |   const toastHit = await page.locator('.van-toast', { hasText: '请输入位置名称' }).count();
  177 |   const inlineHit = await fp.locator('.van-field__error-message', { hasText: '请输入位置名称' }).count();
  178 |   await shot(page, 'lm04-validate-empty-name');
  179 |   const postAfter = locationWrites.filter((s) => s.startsWith('POST')).length;
  180 |   expect(postAfter).toBe(postBefore);
  181 |   await expect(fp).toBeVisible(); // 弹层不关闭
  182 |   mark('E-LM-04', '名称必填校验：提示「请输入位置名称」且不发请求', 'PASS',
  183 |     `展示方式=${toastHit > 0 ? 'toast' : ''}${inlineHit > 0 ? 'inline-field-error' : ''}(toast=${toastHit}, inline=${inlineHit})`);
  184 | 
  185 |   // ================= E-LM-05：新增根位置「厨房」 =================
  186 |   await fp.getByPlaceholder('如：厨房吊柜（必填）').fill('厨房');
  187 |   await fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('吊柜顶层放干货');
  188 |   await shot(page, 'lm05-kitchen-form');
  189 |   await fp.getByRole('button', { name: '保存' }).click();
  190 |   await expect(toast(page)).toContainText('已保存');
  191 |   await expect(fp).toBeHidden();
  192 |   const kRow = row(page, '厨房');
  193 |   await expect(kRow).toBeVisible();
  194 |   await expect(kRow.locator('.location-row__name')).toHaveText('厨房');
  195 |   await expect(kRow.locator('.location-row__desc')).toHaveText('吊柜顶层放干货');
  196 |   await expect(page.locator('.locations-toolbar__title')).toHaveText('共 1 个位置');
  197 |   await shot(page, 'lm05-kitchen-created');
  198 |   mark('E-LM-05', '新增「厨房」+ 说明摘要回显', 'PASS');
  199 | 
  200 |   // ================= E-LM-06：新增子位置「吊柜」（父级改为厨房） =================
  201 |   await page.getByRole('button', { name: '新增位置' }).click();
  202 |   await expect(fp.locator('.form-panel__title')).toHaveText('新增位置');
  203 |   await fp.getByPlaceholder('如：厨房吊柜（必填）').fill('吊柜');
  204 |   await fp.locator('.van-field', { hasText: '父级位置' }).click();
  205 |   const pp = parentPanel(page);
  206 |   await expect(pp).toBeVisible();
  207 |   await expect(pp.locator('.parent-panel__title')).toHaveText('选择父级位置');
  208 |   await shot(page, 'lm06-parent-picker');
  209 |   await pp.locator('.van-cell', { hasText: '厨房' }).click();
  210 |   await expect(pp).toBeHidden();
  211 |   await expect(fp.locator('.van-field', { hasText: '父级位置' }).locator('input')).toHaveValue('厨房');
  212 |   await fp.getByRole('button', { name: '保存' }).click();
  213 |   await expect(toast(page)).toContainText('已保存');
  214 |   await expect(page.locator('.locations-toolbar__title')).toHaveText('共 2 个位置');
  215 |   const allRows = page.locator('.location-row');
  216 |   await expect(allRows).toHaveCount(2);
  217 |   await expect(allRows.nth(0).locator('.location-row__name')).toHaveText('厨房');
  218 |   await expect(allRows.nth(1).locator('.location-row__name')).toHaveText('吊柜');
  219 |   const kitchenPad = await allRows.nth(0).evaluate((el) => el.style.paddingLeft);
  220 |   const cabinetPad = await allRows.nth(1).evaluate((el) => el.style.paddingLeft);
  221 |   expect(kitchenPad).toBe('12px');
  222 |   expect(cabinetPad).toBe('28px');
  223 |   await shot(page, 'lm06-child-created-indented');
  224 |   mark('E-LM-06', '父级改为厨房创建「吊柜」+ depth 缩进', 'PASS', `padding ${kitchenPad} / ${cabinetPad}`);
  225 | 
  226 |   // ================= E-LM-07：编辑「厨房」说明 =================
  227 |   await row(page, '厨房').getByRole('button', { name: '编辑' }).click();
  228 |   await expect(fp.locator('.form-panel__title')).toHaveText('编辑位置');
  229 |   await expect(fp.getByPlaceholder('如：厨房吊柜（必填）')).toHaveValue('厨房');
  230 |   await expect(fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货')).toHaveValue('吊柜顶层放干货');
  231 |   await shot(page, 'lm07-edit-prefill');
  232 |   await fp.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('一层台面放锅具');
  233 |   await fp.getByRole('button', { name: '保存' }).click();
  234 |   await expect(toast(page)).toContainText('已保存');
  235 |   await expect(row(page, '厨房').locator('.location-row__desc')).toHaveText('一层台面放锅具');
  236 |   await shot(page, 'lm07-desc-updated');
  237 |   mark('E-LM-07', '编辑说明保存后列表摘要更新', 'PASS');
  238 | 
  239 |   // ================= E-LM-08：照片上传（oversize / 非图片 / png / jpg） =================
  240 |   await row(page, '厨房').getByRole('button', { name: '编辑' }).click();
  241 |   const upInput = fp.locator('.van-uploader input[type=file]');
  242 |   const uploadsBefore = fileUploads.length;
  243 | 
  244 |   // 8a 超 5MB
  245 |   await upInput.setInputFiles(bigPng);
  246 |   await expect(page.getByText('图片不能超过 5MB')).toBeVisible({ timeout: 8000 });
  247 |   await shot(page, 'lm08a-upload-oversize');
  248 |   expect(fileUploads.length).toBe(uploadsBefore);
  249 |   mark('E-LM-08a', '超 5MB oversize toast 且不发请求', 'PASS');
  250 | 
  251 |   // 8b 非图片类型
  252 |   await upInput.setInputFiles(txtFile);
  253 |   await expect(page.getByText('仅支持 jpg/jpeg/png/webp 图片')).toBeVisible({ timeout: 8000 });
  254 |   await shot(page, 'lm08b-upload-non-image');
  255 |   expect(fileUploads.length).toBe(uploadsBefore);
  256 |   mark('E-LM-08b', '非图片类型 beforeRead 拒绝 toast 且不发请求', 'PASS');
  257 | 
  258 |   // 8c 合法 png 上传（vant 预览 src 优先 objectUrl/blob，服务器 URL 在列表行与编辑回显断言）
  259 |   await upInput.setInputFiles(tinyPng);
  260 |   const preview = fp.locator('.van-uploader__preview img');
  261 |   await expect(preview).toBeVisible({ timeout: 20000 });
  262 |   await expect.poll(() => preview.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 20000 }).toBe(true);
  263 |   const pngPreviewSrc = await preview.getAttribute('src');
  264 |   await shot(page, 'lm08c-upload-png-ok');
  265 |   expect(fileUploads.length).toBe(uploadsBefore + 1);
  266 |   await fp.getByRole('button', { name: '保存' }).click();
```