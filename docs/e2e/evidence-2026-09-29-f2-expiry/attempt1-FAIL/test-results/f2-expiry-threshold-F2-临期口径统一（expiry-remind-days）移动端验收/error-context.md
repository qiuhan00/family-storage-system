# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: f2-expiry-threshold.spec.js >> F2 临期口径统一（expiry.remind.days）移动端验收
- Location: f2-expiry-threshold.spec.js:90:1

# Error details

```
Error: expect(locator).toHaveText(expected) failed

Locator: locator('.item-card').filter({ hasText: 'F2E临期3天' }).locator('.item-card__expiry')
Expected pattern: /^临期 \d{4}-\d{2}-\d{2}$/
Received string:  " 临期 2026-10-02"
Timeout: 12000ms

Call log:
  - Expect "toHaveText" locator('.item-card').filter({ hasText: 'F2E临期3天' }).locator('.item-card__expiry') with timeout 12000ms
  - waiting for locator('.item-card').filter({ hasText: 'F2E临期3天' }).locator('.item-card__expiry')
    28 × locator resolved to <div data-v-af0c4724="" class="item-card__expiry item-card__expiry--red"> 临期 2026-10-02</div>
       - unexpected value " 临期 2026-10-02"

```

```yaml
- text: 临期 2026-10-02
```

# Test source

```ts
  81  |       const v = Number(await input.inputValue());
  82  |       if (v === target) break;
  83  |       if (Number.isNaN(v)) break;
  84  |       await (v < target ? plus : minus).click();
  85  |     }
  86  |   }
  87  |   await expect(input).toHaveValue(String(target));
  88  | }
  89  | 
  90  | test('F2 临期口径统一（expiry.remind.days）移动端验收', async ({ page }) => {
  91  |   const consoleErrors = [];
  92  |   const pageErrors = [];
  93  |   const badResponses = [];
  94  |   const failedRequests = [];
  95  |   const expiringListReqs = [];
  96  |   const expiringSearchReqs = [];
  97  |   const settingsReqs = [];
  98  | 
  99  |   page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  100 |   page.on('pageerror', (e) => pageErrors.push(String(e)));
  101 |   page.on('response', (r) => { if (r.status() >= 400) badResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`); });
  102 |   page.on('requestfailed', (r) => failedRequests.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));
  103 |   page.on('request', (r) => {
  104 |     const u = r.url();
  105 |     if (u.includes('/api/items/expiring')) expiringListReqs.push(u);
  106 |     if (u.includes('/api/items?') && u.includes('expiring=1')) expiringSearchReqs.push(u);
  107 |     if (u.includes('/api/settings')) settingsReqs.push(u);
  108 |   });
  109 | 
  110 |   // ================= 前置：未登录 → /login（含错误分支） =================
  111 |   await page.goto(`${BASE}/`);
  112 |   await expect(page).toHaveURL(/\/login$/);
  113 |   await expect(page.getByPlaceholder('请输入用户名')).toBeVisible();
  114 |   await shot(page, 'a0-login-page');
  115 | 
  116 |   // --- 错误分支：错误密码 ---
  117 |   await page.getByPlaceholder('请输入用户名').fill(ADMIN);
  118 |   await page.getByPlaceholder('请输入密码').fill('wrong-password-xyz');
  119 |   await page.getByRole('button', { name: '登录', exact: true }).click();
  120 |   const errToast = page.locator('.van-toast');
  121 |   await expect(errToast).toBeVisible();
  122 |   await expect(errToast).toContainText(/密码|失败|错误/);
  123 |   await expect(page).toHaveURL(/\/login$/);
  124 |   expect(await page.evaluate(() => localStorage.getItem('fs_token'))).toBeNull();
  125 |   await shot(page, 'a1-login-error-branch');
  126 |   await expect(errToast).toBeHidden({ timeout: 10000 });
  127 | 
  128 |   // --- 正常登录 ---
  129 |   await page.getByPlaceholder('请输入密码').fill(PWD);
  130 |   await page.getByRole('button', { name: '登录', exact: true }).click();
  131 |   await expect(page.locator('.home-page')).toBeVisible({ timeout: 20000 });
  132 |   await shot(page, 'a2-home-after-login');
  133 | 
  134 |   // ================= 场景 A：阈值 = 7 初始态 =================
  135 |   // 前置条件：阈值必须是 7
  136 |   const s0 = await api(page, 'GET', '/api/settings');
  137 |   expect(s0.status, 'GET /api/settings 应为 200').toBe(200);
  138 |   expect(s0.data.expiry_remind_days, '前置条件：expiry_remind_days 应为 7').toBe(7);
  139 | 
  140 |   // A1-a 首页标题动态化（7 天内），并由 /api/settings 驱动
  141 |   const title7 = page.getByRole('heading', { name: '临期提醒（7 天内）' });
  142 |   await expect(title7).toBeVisible();
  143 |   await expect(page.getByRole('heading', { name: '临期提醒（7 天内）', exact: true })).toHaveCount(1);
  144 |   expect(settingsReqs.length, '首页应请求 /api/settings 取阈值').toBeGreaterThan(0);
  145 |   expect(
  146 |     expiringListReqs.some((u) => /[?&]days=7(&|$)/.test(u)),
  147 |     `临期列表请求应为 days=7，实际：${JSON.stringify(expiringListReqs)}`,
  148 |   ).toBe(true);
  149 | 
  150 |   // A1-b 统计卡「临期提醒」计数 = 临期列表条数
  151 |   const h7 = await homeExpiring(page);
  152 |   expect(h7.statCount, `统计卡值=${h7.statText}`).toBe(h7.rowCount);
  153 |   expect(h7.rowCount, '阈值 7 下应至少有 1 条临期（F2E临期3天）').toBeGreaterThanOrEqual(1);
  154 |   expect(h7.names.join('|')).toContain(NEAR);
  155 |   expect(h7.names.join('|'), '阈值 7 不应包含 +15 天物品').not.toContain(FAR);
  156 |   await shot(page, 'a3-home-title7-counts', { full: true });
  157 | 
  158 |   // ================= A2 物品页筛选文案 =================
  159 |   await page.locator('.van-tabbar').getByText('物品', { exact: true }).click();
  160 |   await expect(page.locator('.item-card').first()).toBeVisible();
  161 |   await page.getByRole('button', { name: '筛选' }).click();
  162 |   const panel = page.locator('.filter-panel');
  163 |   await expect(panel).toBeVisible();
  164 |   await expect(panel.getByText('仅看 7 天内临期')).toBeVisible();
  165 |   await shot(page, 'a4-filter-text-7');
  166 |   await panel.getByRole('button', { name: '确定' }).click();
  167 |   await expect(panel).toBeHidden();
  168 | 
  169 |   // ================= A3 列表角标（阈值 7） =================
  170 |   const nearCard = page.locator('.item-card').filter({ hasText: NEAR });
  171 |   const farCard = page.locator('.item-card').filter({ hasText: FAR });
  172 |   const expiredCard = page.locator('.item-card').filter({ hasText: EXPIRED });
  173 |   const noCard = page.locator('.item-card').filter({ hasText: NOEXP });
  174 |   await expect(nearCard).toHaveCount(1);
  175 |   await expect(farCard).toHaveCount(1);
  176 |   await expect(noCard).toHaveCount(1);
  177 | 
  178 |   // A3-a ≤7 天 → 红色「临期」
  179 |   await nearCard.scrollIntoViewIfNeeded();
  180 |   const nearBadge = nearCard.locator('.item-card__expiry');
> 181 |   await expect(nearBadge).toHaveText(/^临期 \d{4}-\d{2}-\d{2}$/);
      |                           ^ Error: expect(locator).toHaveText(expected) failed
  182 |   await expect(nearBadge).toHaveClass(/item-card__expiry--red/);
  183 |   expect(await colorOf(nearBadge)).toBe(RED);
  184 |   await shotEl(nearCard, 'a5-badge-near-red');
  185 | 
  186 |   // A3-b >7 且 ≤30 天 → 普通「到期」（旧版此处为橙色「临期」——修复点）
  187 |   await farCard.scrollIntoViewIfNeeded();
  188 |   const farBadge = farCard.locator('.item-card__expiry');
  189 |   await expect(farBadge).toBeVisible();
  190 |   const farText = (await farBadge.innerText()).trim();
  191 |   expect(farText, `期望「到期 …」，实际「${farText}」`).toMatch(/^到期 \d{4}-\d{2}-\d{2}$/);
  192 |   expect(farText, '超出阈值不得显示「临期」').not.toMatch(/^临期/);
  193 |   const farClass = await farBadge.getAttribute('class');
  194 |   expect(farClass, `角标 class=${farClass}`).not.toMatch(/--red|orange|warn/);
  195 |   const farColor = await colorOf(farBadge);
  196 |   expect(farColor, `实际色值 ${farColor} 不应为旧版橙色 ${ORANGE}`).not.toBe(ORANGE);
  197 |   expect(farColor, `实际色值 ${farColor} 不应为临期红 ${RED}`).not.toBe(RED);
  198 |   expect(
  199 |     await page.locator('.item-card__expiry--orange, .item-card__expiry--warn').count(),
  200 |     '页面不得残留橙色临期档',
  201 |   ).toBe(0);
  202 |   await shotEl(farCard, 'a6-badge-far-normal');
  203 | 
  204 |   // A3-c 已过期 → 红色「临期」（含已过期）
  205 |   await expiredCard.scrollIntoViewIfNeeded();
  206 |   const expiredBadge = expiredCard.locator('.item-card__expiry');
  207 |   await expect(expiredBadge).toHaveText(/^临期 \d{4}-\d{2}-\d{2}$/);
  208 |   await expect(expiredBadge).toHaveClass(/item-card__expiry--red/);
  209 |   expect(await colorOf(expiredBadge)).toBe(RED);
  210 |   await shotEl(expiredCard, 'a7-badge-expired-red');
  211 | 
  212 |   // A3-d 无到期日 → 无角标
  213 |   await noCard.scrollIntoViewIfNeeded();
  214 |   expect(await noCard.locator('.item-card__expiry').count(), '无到期日不应有角标').toBe(0);
  215 |   await shotEl(noCard, 'a8-badge-none');
  216 |   await shot(page, 'a9-items-list-7', { full: true });
  217 | 
  218 |   // ================= A4 详情页角标 =================
  219 |   await nearCard.click();
  220 |   await expect(page).toHaveURL(/\/items\/\d+/);
  221 |   const detailRed = page.locator('.detail-head__expiry--red');
  222 |   await expect(detailRed).toBeVisible();
  223 |   await expect(detailRed).toHaveText(/^临期 \d{4}-\d{2}-\d{2}$/);
  224 |   expect(await colorOf(detailRed)).toBe(RED);
  225 |   await shot(page, 'a10-detail-near-red');
  226 |   await page.locator('[aria-label="返回"]').click();
  227 |   await expect(page.locator('.item-card').first()).toBeVisible();
  228 | 
  229 |   await farCard.click();
  230 |   await expect(page).toHaveURL(/\/items\/\d+/);
  231 |   const detailPlain = page.locator('.detail-head__expiry');
  232 |   await expect(detailPlain).toBeVisible();
  233 |   const detailText = (await detailPlain.innerText()).trim();
  234 |   expect(detailText, `详情期望「到期 …」，实际「${detailText}」`).toMatch(/^到期 \d{4}-\d{2}-\d{2}$/);
  235 |   expect(await page.locator('.detail-head__expiry--red, .detail-head__expiry--orange').count(), '详情不得有红/橙临期档').toBe(0);
  236 |   expect(await colorOf(detailPlain)).not.toBe(ORANGE);
  237 |   await shot(page, 'a11-detail-far-normal');
  238 |   await page.locator('[aria-label="返回"]').click();
  239 |   await expect(page.locator('.item-card').first()).toBeVisible();
  240 | 
  241 |   // ================= A5 expiring=1 搜索窗口 = 阈值 7 =================
  242 |   await page.getByRole('button', { name: '筛选' }).click();
  243 |   await page.locator('.filter-panel .van-switch').click();
  244 |   await panel.getByRole('button', { name: '确定' }).click();
  245 |   await expect(page.locator('.item-card')).toHaveCount(1);
  246 |   await expect(page.locator('.item-card').filter({ hasText: NEAR })).toHaveCount(1);
  247 |   await expect(page.locator('.item-card').filter({ hasText: FAR })).toHaveCount(0);
  248 |   expect(
  249 |     expiringSearchReqs.some((u) => u.includes('expiring=1')),
  250 |     '应发起 GET /api/items?expiring=1',
  251 |   ).toBe(true);
  252 |   await shot(page, 'a12-expiring-search-7');
  253 | 
  254 |   // 关闭临期筛选，恢复全量列表
  255 |   await page.getByRole('button', { name: '筛选' }).click();
  256 |   await page.locator('.filter-panel .van-switch').click();
  257 |   await panel.getByRole('button', { name: '确定' }).click();
  258 |   await expect(page.locator('.item-card').filter({ hasText: FAR })).toHaveCount(1);
  259 | 
  260 |   // ================= 场景 B：阈值改为 30 =================
  261 |   await page.goto(`${BASE}/settings`);
  262 |   await expect(page.getByRole('button', { name: '保存设置' })).toBeVisible();
  263 |   const stepper = page.locator('.van-stepper__input');
  264 |   await expect(stepper).toHaveValue('7');
  265 |   await shot(page, 'b1-settings-7');
  266 | 
  267 |   await setStepper(page, 30);
  268 |   await expect(page.locator('.settings-hint')).toContainText('30 天');
  269 |   await shot(page, 'b2-settings-30-filled');
  270 | 
  271 |   await page.getByRole('button', { name: '保存设置' }).click();
  272 |   const savedToast = page.locator('.van-toast');
  273 |   await expect(savedToast).toBeVisible();
  274 |   await expect(savedToast).toContainText('已保存');
  275 |   const s30 = await api(page, 'GET', '/api/settings');
  276 |   expect(s30.data.expiry_remind_days, '保存后阈值应为 30').toBe(30);
  277 |   await shot(page, 'b3-settings-saved-30');
  278 | 
  279 |   // B5 回首页：标题 30 天内 + 计数一致 + 列表含 8~30 天物品 + days=30 请求
  280 |   await page.locator('.van-tabbar').getByText('首页', { exact: true }).click();
  281 |   await expect(page.getByRole('heading', { name: '临期提醒（30 天内）', exact: true })).toBeVisible();
```