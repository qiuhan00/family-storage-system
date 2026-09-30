// f2-cleanup.mjs —— F2 E2E 现场恢复：删除 F2E 测试种子物品 + 确保阈值恢复为 7
// 用法：node f2-cleanup.mjs
const BASE = 'http://localhost:8080';
const PREFIX = 'F2E';

async function req(method, pathname, token, body) {
  const res = await fetch(`${BASE}${pathname}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: res.status, data };
}

const ADMIN_PWD = process.env.E2E_ADMIN_PWD;
if (!ADMIN_PWD) {
  console.error('缺少环境变量 E2E_ADMIN_PWD（凭据不落盘，见 AGENTS.md 敏感信息规则）');
  process.exit(1);
}
const login = await req('POST', '/api/auth/login', null, { username: 'admin', password: ADMIN_PWD });
if (login.status !== 200 || !login.data?.token) {
  console.error('LOGIN FAILED', login.status, JSON.stringify(login.data));
  process.exit(1);
}
const token = login.data.token;

// 1) 删除 F2E 种子物品
const deleted = [];
let guard = 0;
while (guard < 50) {
  guard += 1;
  const list = await req('GET', '/api/items?page=0&size=100', token);
  const olds = (list.data?.content ?? []).filter((i) => String(i.name).startsWith(PREFIX));
  if (olds.length === 0) break;
  for (const it of olds) {
    const r = await req('DELETE', `/api/items/${it.id}`, token);
    deleted.push({ id: it.id, name: it.name, status: r.status });
  }
}

// 2) 阈值恢复 7（安全兜底；UI 侧场景 C 已验证）
const before = await req('GET', '/api/settings', token);
let threshold = before.data?.expiry_remind_days;
if (threshold !== 7) {
  const put = await req('PUT', '/api/settings', token, { key: 'expiry.remind.days', value: '7' });
  threshold = put.data?.expiry_remind_days;
  console.log(`threshold restored via API: ${before.data?.expiry_remind_days} -> ${threshold}`);
}

const after = await req('GET', '/api/items?page=0&size=100', token);
console.log(JSON.stringify({
  deleted,
  threshold,
  thresholdOk: threshold === 7,
  leftoverF2E: (after.data?.content ?? []).filter((i) => String(i.name).startsWith(PREFIX)).map((i) => i.name),
  totalItems: after.data?.totalElements,
}, null, 2));
console.log(threshold === 7 ? 'CLEANUP OK（阈值已恢复 7）' : 'CLEANUP WARN（阈值未恢复 7）');
