// probe-popup-timeline.js —— 保存后弹层关闭时序 / toast 与标题重叠探针（16:30 补充验证，仅测试工具，不改应用代码）
// 运行：node probe-popup-timeline.js（需 5173/8080 在跑；结束后自删临时位置）
// probe-popup.js —— 时序探针：保存后弹层关闭时机 / toast 与标题重叠（只读验证，结束即清理临时数据）
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 375, height: 667 } });
  await page.goto('http://localhost:5173/login');
  await page.getByPlaceholder('请输入用户名').fill('e2eadmin');
  await page.getByPlaceholder('请输入密码').fill('E2eNew123!');
  await page.getByRole('button', { name: '登录' }).click();
  await page.locator('.home-page').waitFor({ timeout: 15000 });
  await page.goto('http://localhost:5173/locations');
  await page.locator('.locations-toolbar__title').waitFor();

  const NAME = '探针位置时序';
  const timeline = [];
  async function sample(label) {
    const t0 = Date.now();
    for (let i = 0; i <= 20; i++) {
      const st = await page.evaluate(() => {
        const fp = document.querySelector('.form-panel');
        const pop = fp && fp.closest('.van-popup');
        const toast = document.querySelector('.van-toast');
        const r = pop ? pop.getBoundingClientRect() : null;
        const title = fp && fp.querySelector('.form-panel__title');
        const tr = title ? title.getBoundingClientRect() : null;
        const toastR = toast ? toast.getBoundingClientRect() : null;
        let overlap = false;
        if (r && toastR && tr) {
          overlap = !(toastR.bottom < tr.top || toastR.top > tr.bottom || toastR.right < tr.left || toastR.left > tr.right);
        }
        return {
          open: pop ? getComputedStyle(pop).display !== 'none' : false,
          top: r ? Math.round(r.top) : null,
          titleTop: tr ? Math.round(tr.top) : null,
          toast: !!toast,
          toastText: toast ? toast.textContent : null,
          overlap,
        };
      });
      timeline.push({ label, ms: i * 100, ...st });
      if (i < 20) await page.waitForTimeout(100 - (Date.now() - t0 - i * 100 > 0 ? 0 : 0));
      await page.waitForTimeout(0);
    }
  }

  // ---- 新增路径 ----
  await page.getByRole('button', { name: '新增位置' }).click();
  await page.locator('.form-panel').waitFor();
  await page.getByPlaceholder('如：厨房吊柜（必填）').fill(NAME);
  const t0 = Date.now();
  await page.getByRole('button', { name: '保存' }).click();
  for (let i = 0; i <= 15; i++) {
    const st = await page.evaluate(() => {
      const fp = document.querySelector('.form-panel');
      const pop = fp && fp.closest('.van-popup');
      const toast = document.querySelector('.van-toast');
      const title = fp && fp.querySelector('.form-panel__title');
      const r = pop ? pop.getBoundingClientRect() : null;
      const tr = title ? title.getBoundingClientRect() : null;
      const tR = toast ? toast.getBoundingClientRect() : null;
      const overlap = r && tr && tR && !(tR.bottom < tr.top || tR.top > tr.bottom || tR.right < tr.left || tR.left > tr.right);
      return { display: pop ? getComputedStyle(pop).display : 'none', top: r ? Math.round(r.top) : null, titleTop: tr ? Math.round(tr.top) : null, toast: !!toast, toastText: toast ? toast.textContent : null, overlap: !!overlap };
    });
    timeline.push({ path: 'create', ms: Date.now() - t0, ...st });
    await page.waitForTimeout(100);
  }
  await page.waitForTimeout(1200);

  // ---- 编辑路径（对刚创建的临时位置） ----
  const row = page.locator('.location-row', { hasText: NAME });
  await row.getByRole('button', { name: '编辑' }).click();
  await page.locator('.form-panel').waitFor();
  await page.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('时序探针说明');
  const t1 = Date.now();
  await page.getByRole('button', { name: '保存' }).click();
  for (let i = 0; i <= 15; i++) {
    const st = await page.evaluate(() => {
      const fp = document.querySelector('.form-panel');
      const pop = fp && fp.closest('.van-popup');
      const toast = document.querySelector('.van-toast');
      const title = fp && fp.querySelector('.form-panel__title');
      const r = pop ? pop.getBoundingClientRect() : null;
      const tr = title ? title.getBoundingClientRect() : null;
      const tR = toast ? toast.getBoundingClientRect() : null;
      const overlap = r && tr && tR && !(tR.bottom < tr.top || tR.top > tr.bottom || tR.right < tr.left || tR.left > tr.right);
      return { display: pop ? getComputedStyle(pop).display : 'none', top: r ? Math.round(r.top) : null, titleTop: tr ? Math.round(tr.top) : null, toast: !!toast, toastText: toast ? toast.textContent : null, overlap: !!overlap };
    });
    timeline.push({ path: 'edit', ms: Date.now() - t1, ...st });
    await page.waitForTimeout(100);
  }
  await page.screenshot({ path: 'probe-after-edit-save.png' });
  await page.waitForTimeout(1200);

  console.log(JSON.stringify(timeline, null, 1));

  // ---- 清理临时位置 ----
  await page.reload();
  const r2 = page.locator('.location-row', { hasText: NAME });
  if (await r2.count()) {
    await r2.getByRole('button', { name: '删除' }).click();
    await page.locator('.van-dialog:visible').getByRole('button', { name: '确认' }).click();
    await page.waitForTimeout(800);
  }
  const left = await page.locator('.location-row', { hasText: NAME }).count();
  console.log('CLEANUP_LEFT=', left);
  await browser.close();
})();


