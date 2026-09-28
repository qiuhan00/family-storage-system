// verify-y1-file-lifecycle.js —— code-review Y1 验证：照片文件的物理删除延迟到弹层关闭
// 场景：建位带图→换图后取消（只删会话新图、原图保留）→换图后保存（删原图、保留新图）
// 运行（需 5173/8080 在跑）：node verify-y1-file-lifecycle.js [输出文件]
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const lines = [];
function log(...a) {
  const s = a.join(' ');
  console.log(s);
  lines.push(s);
}

const PNG_1x1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64'
);

(async () => {
  const out = process.argv[2] || path.join(__dirname, 'verify-y1-file-lifecycle.out.txt');
  const tmp = path.join(__dirname, '_y1-tmp');
  fs.mkdirSync(tmp, { recursive: true });
  const f1 = path.join(tmp, 'a.png');
  const f2 = path.join(tmp, 'b.png');
  const f3 = path.join(tmp, 'c.png');
  fs.writeFileSync(f1, PNG_1x1);
  fs.writeFileSync(f2, PNG_1x1);
  fs.writeFileSync(f3, PNG_1x1);

  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport: { width: 375, height: 667 } });
  const fileDeletes = [];
  page.on('request', (r) => {
    if (r.method() === 'DELETE' && r.url().includes('/api/files/')) {
      fileDeletes.push(decodeURIComponent(r.url().split('/api/files/')[1]));
    }
  });
  await page.goto('http://localhost:5173/login');
  await page.getByPlaceholder('请输入用户名').fill('e2eadmin');
  await page.getByPlaceholder('请输入密码').fill('E2eNew123!');
  await page.getByRole('button', { name: '登录' }).click();
  await page.waitForSelector('.home-page', { timeout: 15000 });
  await page.goto('http://localhost:5173/locations');
  await page.waitForSelector('.locations-toolbar__title');

  async function uploadTo(inputLocator, file) {
    const respP = page.waitForResponse(
      (r) => r.url().includes('/api/files') && r.request().method() === 'POST',
      { timeout: 15000 }
    );
    await inputLocator.setInputFiles(file);
    const resp = await respP;
    if (!resp.ok()) throw new Error('upload failed: ' + resp.status());
    await page.waitForSelector('.form-panel .van-uploader__preview img', { timeout: 5000 });
    return (await resp.json()).url;
  }
  async function httpStatus(url) {
    return (await page.request.get('http://localhost:8080' + url)).status();
  }
  const short = (u) => (u ? u.split('/').pop() : 'none');

  const NAME = 'Y1验证位';

  // 1. 新增位置并上传 photo1
  await page.getByRole('button', { name: '新增位置' }).click();
  await page.waitForSelector('.form-panel');
  await page.getByPlaceholder('如：厨房吊柜（必填）').fill(NAME);
  const url1 = await uploadTo(page.locator('.form-panel input[type=file]'), f1);
  await page.getByRole('button', { name: '保存' }).click();
  await page.waitForTimeout(1500);
  const row1 = page.locator('.location-row', { hasText: NAME });
  await row1.waitFor();
  log('STEP1 create photo1=', short(url1), 'fileDeletes=', JSON.stringify(fileDeletes));

  // 2. 编辑：删缩略图→传 photo2→取消 → 预期：仅删 photo2（会话新图），photo1 保留
  await row1.getByRole('button', { name: '编辑' }).click();
  await page.waitForSelector('.form-panel');
  await page.locator('.form-panel .van-uploader__preview-delete').click();
  const url2 = await uploadTo(page.locator('.form-panel input[type=file]'), f2);
  await page.locator('.form-panel').getByRole('button', { name: '取消' }).click();
  await page.waitForTimeout(1200);
  const st1 = await httpStatus(url1);
  const st2 = await httpStatus(url2);
  log('STEP2 cancel fileDeletes=', JSON.stringify(fileDeletes),
    'expect=[', short(url2), '] photo1_GET=', st1, '(want 200) photo2_GET=', st2, '(want !=200)');

  // 3. 编辑：删缩略图→传 photo3→保存 → 预期：删 photo1（被替换的原图），photo3 保留
  await row1.getByRole('button', { name: '编辑' }).click();
  await page.waitForSelector('.form-panel');
  await page.locator('.form-panel .van-uploader__preview-delete').click();
  const url3 = await uploadTo(page.locator('.form-panel input[type=file]'), f3);
  await page.getByRole('button', { name: '保存' }).click();
  await page.waitForTimeout(1800);
  const st1b = await httpStatus(url1);
  const st3 = await httpStatus(url3);
  const thumb = await page.locator('.location-row', { hasText: NAME }).locator('img').getAttribute('src');
  log('STEP3 save fileDeletes=', JSON.stringify(fileDeletes),
    'photo1_GET=', st1b, '(want !=200) photo3_GET=', st3, '(want 200) rowThumb=', short(thumb));

  // 已删文件的 GET：静态资源处理器返回 500（既有行为，非 404），只要非 200 即视为文件已不在
  const gone = (s) => s !== 200;
  const pass =
    fileDeletes.filter((x) => x === url2.replace('/uploads/', '')).length === 1 &&
    fileDeletes.includes(url1.replace('/uploads/', '')) &&
    !fileDeletes.includes(url3.replace('/uploads/', '')) &&
    st1 === 200 && gone(st2) && gone(st1b) && st3 === 200;
  log('SUMMARY', pass ? 'PASS' : 'FAIL');

  // 清理：删位置（按契约不级联删图）→ 手动删残留 photo3 文件 → 校验残留
  await page.locator('.location-row', { hasText: NAME }).getByRole('button', { name: '删除' }).click();
  await page.locator('.van-dialog:visible').getByRole('button', { name: '确认' }).click();
  await page.waitForTimeout(800);
  const name3 = url3.replace('/uploads/', '');
  const diskPath = path.join(__dirname, '..', '..', 'backend', 'uploads', name3);
  if (fs.existsSync(diskPath)) fs.unlinkSync(diskPath);
  log('CLEANUP locationLeft=', await page.locator('.location-row', { hasText: NAME }).count(),
    'photo3_file_removed=', !fs.existsSync(diskPath));

  await browser.close();
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.writeFileSync(out, lines.join('\r\n') + '\r\n', 'utf8');
  process.exit(pass ? 0 : 1);
})().catch((e) => {
  const s = 'ERROR: ' + (e && e.stack ? e.stack : String(e));
  console.log(s);
  lines.push(s);
  fs.writeFileSync(process.argv[2] || path.join(__dirname, 'verify-y1-file-lifecycle.out.txt'), lines.join('\r\n') + '\r\n', 'utf8');
  process.exit(1);
});
