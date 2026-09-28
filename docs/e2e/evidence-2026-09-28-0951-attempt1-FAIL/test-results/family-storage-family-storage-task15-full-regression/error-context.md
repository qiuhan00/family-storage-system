# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: family-storage.spec.js >> family-storage task15 full regression
- Location: family-storage.spec.js:94:1

# Error details

```
Error: expect(locator).toHaveValue(expected) failed

Locator:  getByPlaceholder('选择日期').nth(1)
Expected: "2026-10-01"
Received: "2026-09-28"
Timeout:  12000ms

Call log:
  - Expect "toHaveValue" getByPlaceholder('选择日期').nth(1) with timeout 12000ms
  - waiting for getByPlaceholder('选择日期').nth(1)
    28 × locator resolved to <input readonly type="text" name="expiryDate" placeholder="选择日期" id="van-field-8-input" class="van-field__control" data-allow-mismatch="attribute" aria-labelledby="van-field-8-label"/>
       - unexpected value "2026-09-28"

```

```yaml
- textbox "到期日期":
  - /placeholder: 选择日期
  - text: 2026-09-28
```

# Test source

```ts
  88  | // ---------- 弹层内常用按钮 ----------
  89  | const dialogConfirm = (page) => page.locator('.van-dialog:visible').getByRole('button', { name: '确认' });
  90  | // van-picker 的确认是 div（非 button）；筛选弹层/日历确认是 van-button
  91  | const pickerConfirm = (page) => page.locator('.van-picker:visible .van-picker__confirm');
  92  | const popupConfirm = (page) => page.locator('.van-popup:visible').getByRole('button', { name: '确定' });
  93  | 
  94  | test('family-storage task15 full regression', async ({ page }) => {
  95  |   // 控制台 / 网络异常收集（`consoleErrors` 最终断言）
  96  |   const consoleErrors = [];
  97  |   const pageErrors = [];
  98  |   const server5xx = [];
  99  |   const failedReqs = [];
  100 |   const errResponses = [];
  101 |   page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  102 |   page.on('pageerror', (e) => pageErrors.push(String(e)));
  103 |   page.on('response', (r) => { if (r.status() >= 400) errResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`); if (r.status() >= 500) server5xx.push(`${r.status()} ${r.url()}`); });
  104 |   page.on('requestfailed', (r) => failedReqs.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));
  105 | 
  106 |   // ================= 场景 1-3：未登录守卫 / 注册首账号 / 首页 =================
  107 |   await page.goto(`${BASE}/`);
  108 |   await expect(page).toHaveURL(/\/login$/);
  109 |   await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  110 |   await shot(page, 'login-redirect');
  111 | 
  112 |   await page.getByRole('button', { name: '注册新账号' }).click();
  113 |   await expect(page).toHaveURL(/\/register$/);
  114 |   await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  115 |   await page.getByPlaceholder('请输入昵称').fill('测试管理员');
  116 |   await page.getByPlaceholder('6-32 位').fill(PWD);
  117 |   await page.getByPlaceholder('请再次输入密码').fill(PWD);
  118 |   await shot(page, 'register-filled');
  119 |   await page.getByRole('button', { name: '注册', exact: true }).click();
  120 |   // 注册成功 → 自动登录跳首页；系统非空（重跑）则回退登录
  121 |   let registered = false;
  122 |   try {
  123 |     await expect(page.locator('.home-page')).toBeVisible({ timeout: 6000 });
  124 |     registered = true;
  125 |   } catch (e) {
  126 |     // 系统已非空（重跑/复用库）：注册关闭 → 回退登录（PWD 或 PWD_NEW）
  127 |     await shot(page, 'register-fallback');
  128 |     await page.goto(`${BASE}/login`);
  129 |     await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  130 |     await page.getByPlaceholder('请输入密码').fill(PWD);
  131 |     await page.getByRole('button', { name: '登录' }).click();
  132 |     try {
  133 |       await expect(page.locator('.home-page')).toBeVisible({ timeout: 6000 });
  134 |     } catch (e2) {
  135 |       await page.getByPlaceholder('请输入密码').fill(PWD_NEW);
  136 |       await page.getByRole('button', { name: '登录' }).click();
  137 |       await expect(page.locator('.home-page')).toBeVisible({ timeout: 15000 });
  138 |     }
  139 |   }
  140 |   await shot(page, 'home-after-register');
  141 | 
  142 |   await expect(page.getByText('统计总览')).toBeVisible();
  143 |   for (const label of ['物品总数', '在库总量', '总价值', '临期提醒', '待购清单']) {
  144 |     await expect(page.locator('.stat-card__label', { hasText: label })).toBeVisible();
  145 |   }
  146 |   await expect(page.getByText('暂无临期物品')).toBeVisible();
  147 |   const tabbar = page.locator('.van-tabbar');
  148 |   for (const t of ['首页', '物品', '购物', '我的']) {
  149 |     await expect(tabbar.getByText(t, { exact: true })).toBeVisible();
  150 |   }
  151 |   await shot(page, 'home-stats-tabbar');
  152 | 
  153 |   // ================= 场景 4：分类/位置前置（API）→ UI 级联回显 =================
  154 |   const foodId = await ensureFoodCategory(page);
  155 |   const { kitchenId, cabinetId } = await ensureKitchenLocation(page);
  156 | 
  157 |   // ================= 场景 5：新增物品（含日期/照片/超限校验） =================
  158 |   await page.goto(`${BASE}/items/new`);
  159 |   await expect(page.locator('.van-nav-bar__title')).toHaveText('新增物品');
  160 |   await page.getByPlaceholder('物品名称').fill('牛奶');
  161 |   await page.getByPlaceholder('请选择分类').click();
  162 |   await page.locator('.van-picker-column__item', { hasText: '食品' }).click();
  163 |   await pickerConfirm(page).click();
  164 |   await expect(page.getByPlaceholder('请选择分类')).toHaveValue('食品');
  165 |   await shot(page, 'item-form-category');
  166 | 
  167 |   // 位置级联：厨房橱柜 → 吊柜
  168 |   await page.getByPlaceholder('请选择收纳位置').click();
  169 |   await page.locator('.van-cascader__option', { hasText: '厨房橱柜' }).click();
  170 |   await page.locator('.van-cascader__option', { hasText: '吊柜' }).click();
  171 |   await expect(page.getByPlaceholder('请选择收纳位置')).toHaveValue('厨房橱柜 / 吊柜');
  172 |   await shot(page, 'item-form-location-cascade');
  173 | 
  174 |   await page.getByPlaceholder('0').fill('3');
  175 |   await page.getByPlaceholder('如：件 / 瓶').fill('盒');
  176 |   await page.getByPlaceholder('可选').first().fill('5.9');
  177 | 
  178 |   // 购买日期 = 今天（日历点今天再确定）
  179 |   await page.getByPlaceholder('选择日期').first().click();
  180 |   await page.locator('.van-calendar:visible .van-calendar__day').filter({ hasText: new RegExp(`^${dayNum(0)}$`) }).first().click();
  181 |   await page.locator('.van-calendar:visible .van-calendar__confirm').click();
  182 |   await expect(page.getByPlaceholder('选择日期').first()).toHaveValue(ymd(0));
  183 | 
  184 |   // 到期日期 = 今天+3
  185 |   await page.getByPlaceholder('选择日期').nth(1).click();
  186 |   await page.locator('.van-calendar:visible .van-calendar__day').filter({ hasText: new RegExp(`^${dayNum(3)}$`) }).first().click();
  187 |   await page.locator('.van-calendar:visible .van-calendar__confirm').click();
> 188 |   await expect(page.getByPlaceholder('选择日期').nth(1)).toHaveValue(ymd(3));
      |                                                      ^ Error: expect(locator).toHaveValue(expected) failed
  189 |   await shot(page, 'item-form-dates');
  190 | 
  191 |   // 照片：先验证 6MB 假图被拒（客户端 max-size 校验）
  192 |   const bigPng = { name: 'big.png', mimeType: 'image/png', buffer: Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(6 * 1024 * 1024)]) };
  193 |   const tinyPng = { name: 'tiny.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64') };
  194 |   await page.locator('.van-uploader input[type=file]').setInputFiles(bigPng);
  195 |   await expect(page.getByText('图片不能超过 5MB')).toBeVisible({ timeout: 5000 });
  196 |   await shot(page, 'upload-oversize-rejected');
  197 |   // 合法小图上传：预览缩略图回显（vant 预览 src 优先 objectUrl/blob，视觉回显即可；
  198 |   // 服务器 /uploads URL 在详情页断言）
  199 |   await page.locator('.van-uploader input[type=file]').setInputFiles(tinyPng);
  200 |   const preview = page.locator('.van-uploader__preview img');
  201 |   await expect(preview).toBeVisible();
  202 |   await expect.poll(() => preview.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  203 |   await shot(page, 'upload-ok-thumbnail');
  204 | 
  205 |   await page.getByRole('button', { name: '新增物品' }).click();
  206 |   await expect(page).toHaveURL(/\/items$/, { timeout: 15000 });
  207 |   await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  208 |   await shot(page, 'items-list-first-item');
  209 | 
  210 |   // 场景 6 前置：再建一件 +10 天到期物品（API，供设置阈值生效验证）
  211 |   const b = await api(page, 'POST', '/api/items', {
  212 |     name: '洗发水', categoryId: foodId, locationId: cabinetId, quantity: 2, unit: '瓶',
  213 |     purchasePrice: null, purchaseDate: null, expiryDate: ymd(10), photoUrl: null, remark: null,
  214 |   });
  215 |   expect([200, 201]).toContain(b.status);
  216 | 
  217 |   // ================= 详情：照片回显 / 字段 / 临期高亮 =================
  218 |   await page.locator('.item-card', { hasText: '牛奶' }).click();
  219 |   await expect(page).toHaveURL(/\/items\/\d+$/, { timeout: 15000 });
  220 |   const detailImg = page.locator('.detail-cover img');
  221 |   await expect(detailImg).toBeVisible();
  222 |   await expect.poll(() => detailImg.getAttribute('src'), { timeout: 15000 }).toContain('/uploads/');
  223 |   await expect.poll(() => detailImg.evaluate((el) => el.complete && el.naturalWidth > 0), { timeout: 15000 }).toBe(true);
  224 |   await shot(page, 'detail-photo');
  225 |   await expect(page.locator('.van-cell', { hasText: /^数量/ })).toContainText('3盒');
  226 |   await expect(page.locator('.van-cell', { hasText: /^分类/ })).toContainText('食品');
  227 |   await expect(page.locator('.van-cell', { hasText: /^位置/ })).toContainText('厨房橱柜 / 吊柜');
  228 |   await expect(page.getByText(`临期 ${ymd(3)}`)).toBeVisible();
  229 |   await shot(page, 'detail-info-expiry');
  230 | 
  231 |   // ================= 编辑：数量 5 + 备注 =================
  232 |   await page.getByRole('button', { name: '编辑' }).click();
  233 |   await expect(page).toHaveURL(/\/edit$/, { timeout: 15000 });
  234 |   await page.getByPlaceholder('0').fill('5');
  235 |   await page.getByPlaceholder('补充说明（可选）').fill('E2E 备注修改');
  236 |   await shot(page, 'edit-filled');
  237 |   await page.getByRole('button', { name: '保存修改' }).click();
  238 |   await expect(page).toHaveURL(/\/items\/\d+$/, { timeout: 15000 });
  239 |   await expect(page.locator('.van-cell', { hasText: /^数量/ })).toContainText('5盒');
  240 |   await expect(page.getByText('E2E 备注修改')).toBeVisible();
  241 |   await shot(page, 'detail-after-edit');
  242 | 
  243 |   // ================= 搜索 / 筛选 =================
  244 |   await page.locator('.van-tabbar-item', { hasText: '物品' }).click();
  245 |   await expect(page.getByPlaceholder('搜索物品名称')).toBeVisible();
  246 |   await page.getByPlaceholder('搜索物品名称').fill('牛奶');
  247 |   await page.getByPlaceholder('搜索物品名称').press('Enter');
  248 |   await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  249 |   await shot(page, 'search-hit');
  250 | 
  251 |   await page.getByPlaceholder('搜索物品名称').fill('不存在的物品xyz');
  252 |   await page.getByPlaceholder('搜索物品名称').press('Enter');
  253 |   await expect(page.getByText('没有找到物品')).toBeVisible();
  254 |   await shot(page, 'search-empty');
  255 | 
  256 |   await page.getByPlaceholder('搜索物品名称').fill('');
  257 |   await page.getByPlaceholder('搜索物品名称').press('Enter');
  258 |   await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  259 | 
  260 |   // 分类筛选 食品
  261 |   await page.getByRole('button', { name: /筛选/ }).click();
  262 |   await page.locator('.van-radio', { hasText: '食品' }).click();
  263 |   await popupConfirm(page).click();
  264 |   await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  265 |   await shot(page, 'filter-category');
  266 | 
  267 |   // 临期筛选（7 天内）：仅 牛奶，洗发水（+10 天）不在
  268 |   await page.getByRole('button', { name: /筛选/ }).click();
  269 |   await page.locator('.van-cell', { hasText: '仅看 7 天内临期' }).locator('.van-switch').click();
  270 |   await popupConfirm(page).click();
  271 |   await expect(page.locator('.item-card', { hasText: '牛奶' })).toBeVisible();
  272 |   await expect(page.locator('.item-card', { hasText: '洗发水' })).toHaveCount(0);
  273 |   await expect(page.locator('.item-card', { hasText: '牛奶' })).toContainText(`临期 ${ymd(3)}`);
  274 |   await shot(page, 'filter-expiring');
  275 | 
  276 |   // 重置恢复
  277 |   await page.getByRole('button', { name: /筛选/ }).click();
  278 |   await page.locator('.van-popup:visible').getByRole('button', { name: '重置' }).click();
  279 |   await popupConfirm(page).click();
  280 |   await expect(page.locator('.item-card', { hasText: '洗发水' })).toBeVisible();
  281 | 
  282 |   // ================= 出入库：入库 +2 / 超存量报错 / 出库 -1 =================
  283 |   await page.locator('.item-card', { hasText: '牛奶' }).click();
  284 |   await expect(page.getByRole('button', { name: '出入库' })).toBeVisible();
  285 |   const stockAction = async (name) => {
  286 |     await page.getByRole('button', { name: '出入库' }).click();
  287 |     await page.locator('.van-action-sheet:visible').getByText(name, { exact: true }).click();
  288 |   };
```