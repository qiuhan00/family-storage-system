# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: f2-expiry-threshold.spec.js >> F2 临期口径统一（expiry.remind.days）移动端验收
- Location: f2-expiry-threshold.spec.js:90:1

# Error details

```
Error: 非预期 console error：Failed to load resource: the server responded with a status of 404 (Not Found)

expect(received).toEqual(expected) // deep equality

- Expected  - 1
+ Received  + 3

- Array []
+ Array [
+   "Failed to load resource: the server responded with a status of 404 (Not Found)",
+ ]
```

# Page snapshot

```yaml
- generic [active] [ref=f3e1]:
  - generic [ref=f3e4]:
    - generic [ref=f3e7]:
      - generic [ref=f3e8] [cursor=pointer]
      - generic [ref=f3e9]: 物品
      - generic [ref=f3e10] [cursor=pointer]: 
    - main [ref=f3e13]:
      - generic [ref=f3e14]:
        - generic [ref=f3e15]:
          - generic [ref=f3e18] [cursor=pointer]:
            - generic [ref=f3e19]: 
            - searchbox "搜索物品名称" [ref=f3e23]
          - generic [ref=f3e24]:
            - button " 筛选" [ref=f3e26] [cursor=pointer]:
              - generic [ref=f3e27]:
                - generic [ref=f3e28]: 
                - generic [ref=f3e29]: 筛选
            - button " 新增" [ref=f3e30] [cursor=pointer]:
              - generic [ref=f3e31]:
                - generic [ref=f3e32]: 
                - generic [ref=f3e33]: 新增
        - feed [ref=f3e37]:
          - generic [ref=f3e38]:
            - generic [ref=f3e39]:
              - generic [ref=f3e40]:
                - generic [ref=f3e41]: F2E临期3天
                - generic [ref=f3e42]: 食品 · 客厅 / 冰箱
                - generic [ref=f3e43]: F2 E2E 种子数据（跑完自动清理）
              - generic [ref=f3e44]:
                - generic [ref=f3e45]: 2个
                - generic [ref=f3e46]: 临期 2026-10-02
            - generic [ref=f3e47]:
              - generic [ref=f3e48]:
                - generic [ref=f3e49]: F2E远期15天
                - generic [ref=f3e50]: 食品 · 客厅 / 冰箱
                - generic [ref=f3e51]: F2 E2E 种子数据（跑完自动清理）
              - generic [ref=f3e52]:
                - generic [ref=f3e53]: 2个
                - generic [ref=f3e54]: 到期 2026-10-14
            - generic [ref=f3e55]:
              - generic [ref=f3e56]:
                - generic [ref=f3e57]: F2E无到期
                - generic [ref=f3e58]: 食品 · 客厅 / 冰箱
                - generic [ref=f3e59]: F2 E2E 种子数据（跑完自动清理）
              - generic [ref=f3e60]: 2个
            - generic [ref=f3e62]:
              - generic [ref=f3e63]:
                - generic [ref=f3e64]: 价格边界0元
                - generic [ref=f3e65]: 食品 · 客厅 / 冰箱
              - generic [ref=f3e66]: 1个
            - generic [ref=f3e68]:
              - generic [ref=f3e69]:
                - generic [ref=f3e70]: 乐观锁压力物品
                - generic [ref=f3e71]: 食品 · 客厅 / 冰箱
              - generic [ref=f3e72]: 117个
            - generic [ref=f3e74]:
              - generic [ref=f3e75]:
                - generic [ref=f3e76]: 拉面
                - generic [ref=f3e77]: 食品 · 客厅
              - generic [ref=f3e78]: 4袋
            - generic [ref=f3e80]:
              - generic [ref=f3e81]:
                - generic [ref=f3e82]: 牛奶
                - generic [ref=f3e83]: 食品 · 客厅 / 冰箱
                - generic [ref=f3e84]: update test
              - generic [ref=f3e85]:
                - generic [ref=f3e86]: 4盒
                - generic [ref=f3e87]: 临期 2026-09-28
          - generic [ref=f3e88]: 没有更多了
        - text:       
    - tablist [ref=f3e90]:
      - tab " 首页" [ref=f3e91] [cursor=pointer]:
        - generic [ref=f3e92]: 
        - generic [ref=f3e94]: 首页
      - tab " 物品" [selected] [ref=f3e95] [cursor=pointer]:
        - generic [ref=f3e96]: 
        - generic [ref=f3e98]: 物品
      - tab " 购物" [ref=f3e99] [cursor=pointer]:
        - generic [ref=f3e100]: 
        - generic [ref=f3e102]: 购物
      - tab " 我的" [ref=f3e103] [cursor=pointer]:
        - generic [ref=f3e104]: 
        - generic [ref=f3e106]: 我的
  - dialog [ref=f3e107]:
    - generic [ref=f3e108]: 已保存
```

# Test source

```ts
  282 |   await expect(page.getByRole('heading', { name: '临期提醒（7 天内）' })).toHaveCount(0);
  283 |   expect(
  284 |     expiringListReqs.some((u) => /[?&]days=30(&|$)/.test(u)),
  285 |     `临期列表请求应为 days=30，实际：${JSON.stringify(expiringListReqs)}`,
  286 |   ).toBe(true);
  287 |   const h30 = await homeExpiring(page);
  288 |   expect(h30.statCount, `统计卡值=${h30.statText}`).toBe(h30.rowCount);
  289 |   expect(h30.rowCount, '阈值 30 应包含 +3 与 +15 天两条').toBeGreaterThanOrEqual(2);
  290 |   expect(h30.names.join('|')).toContain(NEAR);
  291 |   expect(h30.names.join('|'), '8~30 天内到期物品应进入临期列表').toContain(FAR);
  292 |   await shot(page, 'b4-home-title30-counts', { full: true });
  293 | 
  294 |   // B6 物品页筛选文案 + 角标切换
  295 |   await page.locator('.van-tabbar').getByText('物品', { exact: true }).click();
  296 |   await expect(page.locator('.item-card').first()).toBeVisible();
  297 |   await page.getByRole('button', { name: '筛选' }).click();
  298 |   await expect(panel.getByText('仅看 30 天内临期')).toBeVisible();
  299 |   await expect(panel.getByText('仅看 7 天内临期')).toHaveCount(0);
  300 |   await shot(page, 'b5-filter-text-30');
  301 |   await panel.getByRole('button', { name: '确定' }).click();
  302 |   await expect(panel).toBeHidden();
  303 | 
  304 |   await farCard.scrollIntoViewIfNeeded();
  305 |   const farBadge30 = farCard.locator('.item-card__expiry');
  306 |   await expect(farBadge30, '阈值 30 后 +15 天物品应变红「临期」').toHaveText(/^\s*临期 \d{4}-\d{2}-\d{2}\s*$/);
  307 |   await expect(farBadge30).toHaveClass(/item-card__expiry--red/);
  308 |   expect(await colorOf(farBadge30)).toBe(RED);
  309 |   await shotEl(farCard, 'b6-badge-far-red-30');
  310 |   await expect(nearCard.locator('.item-card__expiry')).toHaveClass(/item-card__expiry--red/);
  311 |   await shot(page, 'b7-items-list-30', { full: true });
  312 | 
  313 |   // B7 expiring=1 窗口随阈值变 30
  314 |   await page.getByRole('button', { name: '筛选' }).click();
  315 |   await page.locator('.filter-panel .van-switch').click();
  316 |   await panel.getByRole('button', { name: '确定' }).click();
  317 |   await expect(page.locator('.item-card').filter({ hasText: FAR })).toHaveCount(1);
  318 |   await expect(page.locator('.item-card').filter({ hasText: NEAR })).toHaveCount(1);
  319 |   const uiCount = await page.locator('.item-card').count();
  320 |   const apiExp = await api(page, 'GET', '/api/items?expiring=1&page=0&size=50&status=ACTIVE');
  321 |   expect(apiExp.status).toBe(200);
  322 |   expect(apiExp.data.totalElements, 'UI 结果条数应与 GET /api/items?expiring=1 一致').toBe(uiCount);
  323 |   expect(uiCount, '阈值 30 下 expiring=1 至少命中 2 条').toBeGreaterThanOrEqual(2);
  324 |   expect(
  325 |     expiringSearchReqs.some((u) => u.includes('expiring=1')),
  326 |     '应发起 GET /api/items?expiring=1',
  327 |   ).toBe(true);
  328 |   await shot(page, 'b8-expiring-search-30');
  329 | 
  330 |   // ================= 场景 C：恢复阈值 7 =================
  331 |   await page.goto(`${BASE}/settings`);
  332 |   await expect(page.getByRole('button', { name: '保存设置' })).toBeVisible();
  333 |   await expect(stepper).toHaveValue('30');
  334 |   await setStepper(page, 7);
  335 |   await expect(page.locator('.settings-hint')).toContainText('7 天');
  336 |   await page.getByRole('button', { name: '保存设置' }).click();
  337 |   await expect(page.locator('.van-toast')).toContainText('已保存');
  338 |   const s7 = await api(page, 'GET', '/api/settings');
  339 |   expect(s7.data.expiry_remind_days, '阈值必须恢复为 7').toBe(7);
  340 |   await shot(page, 'c1-settings-restored-7');
  341 | 
  342 |   await page.locator('.van-tabbar').getByText('首页', { exact: true }).click();
  343 |   await expect(page.getByRole('heading', { name: '临期提醒（7 天内）', exact: true })).toBeVisible();
  344 |   await expect(page.getByRole('heading', { name: '临期提醒（30 天内）' })).toHaveCount(0);
  345 |   expect(
  346 |     expiringListReqs.filter((u) => /[?&]days=7(&|$)/.test(u)).length,
  347 |     '恢复后应再次请求 days=7',
  348 |   ).toBeGreaterThanOrEqual(2);
  349 |   const h7b = await homeExpiring(page);
  350 |   expect(h7b.statCount).toBe(h7b.rowCount);
  351 |   await shot(page, 'c2-home-title7-restored', { full: true });
  352 | 
  353 |   await page.locator('.van-tabbar').getByText('物品', { exact: true }).click();
  354 |   await expect(page.locator('.item-card').first()).toBeVisible();
  355 |   await page.getByRole('button', { name: '筛选' }).click();
  356 |   await expect(panel.getByText('仅看 7 天内临期')).toBeVisible();
  357 |   await expect(panel.getByText('仅看 30 天内临期')).toHaveCount(0);
  358 |   await shot(page, 'c3-filter-text-7-restored');
  359 |   await panel.getByRole('button', { name: '确定' }).click();
  360 | 
  361 |   // ================= 场景 D：质量 —— 控制台 / 网络 =================
  362 |   const logLines = [
  363 |     '=== console errors ===',
  364 |     ...consoleErrors.map((t) => `- ${t}`),
  365 |     '=== page errors (uncaught) ===',
  366 |     ...pageErrors.map((t) => `- ${t}`),
  367 |     '=== http >= 400 ===',
  368 |     ...badResponses.map((t) => `- ${t}`),
  369 |     '=== failed requests ===',
  370 |     ...failedRequests.map((t) => `- ${t}`),
  371 |     '=== /api/items/expiring requests ===',
  372 |     ...expiringListReqs.map((t) => `- ${t}`),
  373 |     '=== /api/items?expiring=1 requests ===',
  374 |     ...expiringSearchReqs.map((t) => `- ${t}`),
  375 |     '=== /api/settings requests ===',
  376 |     ...settingsReqs.map((t) => `- ${t}`),
  377 |   ];
  378 |   fs.writeFileSync(path.join(EVIDENCE_DIR, 'console-network-log.txt'), logLines.join('\n'), 'utf8');
  379 | 
  380 |   expect(pageErrors, `未捕获 JS 异常：${pageErrors.join(' | ')}`).toEqual([]);
  381 |   const unexpected = consoleErrors.filter((t) => !/401/.test(t));
> 382 |   expect(unexpected, `非预期 console error：${unexpected.join(' | ')}`).toEqual([]);
      |                                                                     ^ Error: 非预期 console error：Failed to load resource: the server responded with a status of 404 (Not Found)
  383 |   expect(failedRequests, `请求失败：${failedRequests.join(' | ')}`).toEqual([]);
  384 |   // 非 401 的 HTTP 错误（401 = 登录失败分支 + 未登录探测，预期内）
  385 |   const unexpectedHttp = badResponses.filter((t) => !/^401 /.test(t));
  386 |   expect(unexpectedHttp, `非预期 HTTP 错误：${unexpectedHttp.join(' | ')}`).toEqual([]);
  387 |   await shot(page, 'd1-final-console-clean', { full: true });
  388 | 
  389 |   // 终态：阈值必须是 7
  390 |   const sFinal = await api(page, 'GET', '/api/settings');
  391 |   expect(sFinal.data.expiry_remind_days, '结束时阈值必须恢复为 7').toBe(7);
  392 | });
  393 | 
```