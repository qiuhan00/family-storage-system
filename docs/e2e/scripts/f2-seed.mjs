// f2-seed.mjs —— F2 E2E 前置种子数据（幂等：先清同名 F2E 数据再创建）
// 创建 3 条物品用于验证临期口径：+3 天（阈值内）/ +15 天（7 外 30 内）/ 无到期日。
// 用法：node f2-seed.mjs   （需后端 8080 已就绪；只写测试数据，不改任何应用代码）
const BASE = 'http://localhost:8080';

const PREFIX = 'F2E';
const SEEDS = [
  { name: 'F2E临期3天', offset: 3 },
  { name: 'F2E远期15天', offset: 15 },
  { name: 'F2E无到期', offset: null },
];

function ymd(offset) {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

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

const settings = await req('GET', '/api/settings', token);
const threshold = settings.data?.expiry_remind_days;
if (threshold !== 7) {
  console.error(`PRECONDITION FAILED: expiry_remind_days=${threshold} (期望 7)，请先恢复阈值再跑 F2 E2E`);
  process.exit(2);
}

// 幂等清理旧 F2E 数据
let guard = 0;
while (guard < 50) {
  guard += 1;
  const list = await req('GET', '/api/items?page=0&size=100', token);
  const olds = (list.data?.content ?? []).filter((i) => String(i.name).startsWith(PREFIX));
  if (olds.length === 0) break;
  for (const it of olds) {
    const del = await req('DELETE', `/api/items/${it.id}`, token);
    console.log(`deleted old ${it.id} ${it.name} -> ${del.status}`);
  }
}

const created = [];
for (const s of SEEDS) {
  const body = {
    name: s.name,
    categoryId: 1,
    locationId: 2,
    quantity: 2,
    unit: '个',
    remark: 'F2 E2E 种子数据（跑完自动清理）',
    expiryDate: s.offset == null ? null : ymd(s.offset),
  };
  const r = await req('POST', '/api/items', token, body);
  if (r.status !== 201 && r.status !== 200) {
    console.error('SEED FAILED', s.name, r.status, JSON.stringify(r.data));
    process.exit(3);
  }
  created.push({ id: r.data.id, name: r.data.name, expiryDate: r.data.expiryDate });
}

console.log(JSON.stringify({ threshold, created }, null, 2));
console.log('SEED OK');
