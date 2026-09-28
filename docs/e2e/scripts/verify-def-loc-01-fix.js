// verify-def-loc-01-fix.js —— DEF-LOC-01 修复复测：保存成功 toast 在弹层关闭动画结束后弹出，不再与标题重叠
// 运行（需 5173/8080 在跑）：node verify-def-loc-01-fix.js [输出文件]
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const lines = [];
function log(...a) {
  const s = a.join(' ');
  console.log(s);
  lines.push(s);
}

(async () => {
  const out = process.argv[2] || path.join(__dirname, 'verify-def-loc-01-fix.out.txt');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 375, height: 667 } });
  await page.goto('http://localhost:5173/login');
  await page.getByPlaceholder('请输入用户名').fill('e2eadmin');
  await page.getByPlaceholder('请输入密码').fill('E2eNew123!');
  await page.getByRole('button', { name: '登录' }).click();
  await page.waitForSelector('.home-page', { timeout: 15000 });
  await page.goto('http://localhost:5173/locations');
  await page.waitForSelector('.locations-toolbar__title');

  async function sample(pathName) {
    const out = [];
    for (let i = 0; i <= 22; i++) {
      const st = await page.evaluate(() => {
        const fp = document.querySelector('.form-panel');
        const pop = fp && fp.closest('.van-popup');
        const toast = document.querySelector('.van-toast');
        const title = fp && fp.querySelector('.form-panel__title');
        const r = pop ? pop.getBoundingClientRect() : null;
        const tr = title ? title.getBoundingClientRect() : null;
        const tR = toast ? toast.getBoundingClientRect() : null;
        const overlap = r && tr && tR && !(tR.bottom < tr.top || tR.top > tr.bottom || tR.right < tr.left || tR.left > tr.right);
        return { display: pop ? getComputedStyle(pop).display : 'none', toast: !!toast, overlap: !!overlap };
      });
      out.push({ path: pathName, ms: i * 100, ...st });
      await page.waitForTimeout(100);
    }
    return out;
  }
  function report(name, tl) {
    const ov = tl.filter((x) => x.overlap);
    const ts = tl.filter((x) => x.toast);
    log(name, 'samples=', tl.length, 'toast=', ts.length, 'OVERLAP_TRUE=', ov.length,
      'toast_ms=', ts.length ? ts.map((x) => x.ms).join(',') : 'none',
      'first_overlap_ms=', ov.length ? ov[0].ms : 'none');
  }

  // create 路径
  await page.getByRole('button', { name: '新增位置' }).click();
  await page.waitForSelector('.form-panel');
  await page.getByPlaceholder('如：厨房吊柜（必填）').fill('终验路径');
  await page.getByRole('button', { name: '保存' }).click();
  const tlCreate = await sample('create');
  report('CREATE:', tlCreate);
  await page.waitForTimeout(1200);

  // edit 路径
  const row = page.locator('.location-row', { hasText: '终验路径' });
  await row.getByRole('button', { name: '编辑' }).click();
  await page.waitForSelector('.form-panel');
  await page.getByPlaceholder('具体摆放说明，如：吊柜顶层放干货').fill('终验说明');
  await page.getByRole('button', { name: '保存' }).click();
  const tlEdit = await sample('edit');
  report('EDIT:  ', tlEdit);
  await page.waitForTimeout(1200);
  log('ROW_AFTER=', await page.locator('.location-row', { hasText: '终验路径' }).textContent());

  // 清理
  await page.locator('.location-row', { hasText: '终验路径' }).getByRole('button', { name: '删除' }).click();
  await page.locator('.van-dialog:visible').getByRole('button', { name: '确认' }).click();
  await page.waitForTimeout(800);
  log('CLEANUP_LEFT=', await page.locator('.location-row', { hasText: '终验路径' }).count());

  const total = tlCreate.length + tlEdit.length;
  const ovTotal = [...tlCreate, ...tlEdit].filter((x) => x.overlap).length;
  log('SUMMARY total_samples=', total, 'OVERLAP_TRUE=', ovTotal, ovTotal === 0 ? 'PASS' : 'FAIL');

  await browser.close();
  fs.writeFileSync(out, lines.join('\r\n') + '\r\n', 'utf8');
})().catch((e) => {
  const s = 'ERROR: ' + (e && e.stack ? e.stack : String(e));
  console.log(s);
  lines.push(s);
  fs.writeFileSync(process.argv[2] || path.join(__dirname, 'verify-def-loc-01-fix.out.txt'), lines.join('\r\n') + '\r\n', 'utf8');
  process.exit(1);
});
