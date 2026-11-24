// 출근길 API 부하 테스트. perf/run.sh로 실행한다.
//  - browse : 홈·노선·스몰토크·보관함·기록 조회
//  - commute: 노선 생성 → 담기 → 탑승 → 완독 → 도착. 노선은 하루 1번이라 회원마다 한 번만 돈다
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// 운영 서버에 부하를 주지 않도록 로컬과 perf 컨테이너만 허용한다
if (!/^http:\/\/(localhost|127\.0\.0\.1|app)(:\d+)?$/.test(BASE_URL)) {
  throw new Error(`로컬이 아닌 주소에는 실행하지 않습니다: ${BASE_URL}`);
}

const PROFILE = __ENV.PROFILE || 'load';
const USERS = Number(__ENV.USERS || 5000);
const FIRST_USER_ID = Number(__ENV.FIRST_USER_ID || 1);
// 예열에서 쓴 회원을 건너뛴다
const COMMUTE_OFFSET = Number(__ENV.COMMUTE_OFFSET || 0);
const P95_MS = Number(__ENV.P95_MS || 500);
const RATE = Number(__ENV.RATE || 0);

// browse는 초당 반복 수(반복마다 API 7번), commute는 초당 시작하는 회원 수.
// load는 회원 5,000명 중 40%가 2시간 동안 타고(초당 0.28명) 몰릴 때 3배가 된다고 본 값이다. 운영 실측이 아니라 가정이다.
const PROFILES = {
  smoke: { browse: [{ target: 1, duration: '15s' }], commute: [{ target: 1, duration: '15s' }], vus: 20 },
  warmup: { browse: [{ target: 2, duration: '40s' }], commute: [{ target: 1, duration: '40s' }], vus: 50 },
  load: {
    browse: [{ target: RATE || 2, duration: '30s' }, { target: RATE || 2, duration: '3m' }],
    commute: [{ target: 1, duration: '30s' }, { target: 1, duration: '3m' }],
    vus: 200,
  },
  // 어디서 무너지는지 찾는다. 끝에서는 기준을 넘는 것이 정상이다
  stress: {
    browse: [2, 4, 6, 8, 10].flatMap((r) => [{ target: r, duration: '10s' }, { target: r, duration: '50s' }]),
    commute: [{ target: 1, duration: '10s' }, { target: 1, duration: '4m50s' }],
    vus: 400,
  },
};
const profile = PROFILES[PROFILE];
if (!profile) throw new Error(`PROFILE은 smoke, load, stress 중 하나입니다: ${PROFILE}`);

const scenario = (stages, fn) => ({
  executor: 'ramping-arrival-rate', startRate: 0, timeUnit: '1s', exec: fn, stages,
  preAllocatedVUs: Math.ceil(profile.vus / 4), maxVUs: profile.vus, gracefulStop: '15s',
});

const NAMES = ['home', 'popular', 'route_today', 'smalltalks', 'scraps', 'stat_summary', 'stat_calendar',
  'route_create', 'cart_add', 'boarding', 'departure', 'stop', 'read', 'arrival'];
const thresholds = {
  http_req_duration: [`p(95)<${P95_MS}`, `p(99)<${P95_MS * 2}`],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  dropped_iterations: ['count<1'],
};
// 요약에 API별 p95를 남기기 위해 모든 API에 기준을 둔다
NAMES.forEach((name) => { thresholds[`http_req_duration{name:${name}}`] = [`p(95)<${P95_MS}`]; });

export const options = {
  scenarios: { browse: scenario(profile.browse, 'browse'), commute: scenario(profile.commute, 'commute') },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

// stress는 단계마다 p95를 따로 남긴다(초당 조회 반복 수)
const STEPS = [2, 4, 6, 8, 10];
if (PROFILE === 'stress') {
  STEPS.forEach((r) => {
    thresholds[`http_req_duration{step:${r}}`] = [`p(95)<${P95_MS}`];
    thresholds[`http_req_failed{step:${r}}`] = ['rate<0.01'];
  });
}
const step = () => (PROFILE === 'stress'
  ? String(STEPS[Math.min(STEPS.length - 1, Math.floor((Date.now() - exec.scenario.startTime) / 60000))]) : '0');

const b64 = (value) => encoding.b64encode(JSON.stringify(value), 'rawurl');
function params(userId, name) {
  const now = Math.floor(Date.now() / 1000);
  const body = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: String(userId), iat: now, exp: now + 3600 })}`;
  const token = `${body}.${crypto.hmac('sha256', __ENV.JWT_SECRET, body, 'base64rawurl')}`;
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { name, step: step() } };
}

function call(method, path, userId, name, body) {
  const res = http.request(method, `${BASE_URL}/api/v1${path}`, body ? JSON.stringify(body) : null, params(userId, name));
  check(res, { 'status 200': (r) => r.status === 200 });
  return res.status === 200 ? res.json('data') : null;
}

export function browse() {
  const userId = FIRST_USER_ID + Math.floor(Math.random() * USERS);
  call('GET', '/home', userId, 'home');
  call('GET', '/home/popular', userId, 'popular');
  call('GET', '/routes/today', userId, 'route_today');
  call('GET', '/smalltalks', userId, 'smalltalks');
  call('GET', '/scraps', userId, 'scraps');
  call('GET', '/stats/summary?period=MONTH', userId, 'stat_summary');
  call('GET', '/stats/calendar', userId, 'stat_calendar');
}

export function commute() {
  const index = COMMUTE_OFFSET + exec.scenario.iterationInTest;
  // 뒤쪽 40%는 오늘 이미 완주한 회원이라 쓰지 않는다(seed.sql)
  if (index >= USERS * 0.6) exec.test.abort('회원이 부족합니다. USERS를 늘리세요.');
  const userId = FIRST_USER_ID + index;
  const conditions = ['SUNNY', 'NORMAL', 'CLOUDY', 'RAINY'];
  const created = call('POST', '/routes', userId, 'route_create', { condition: conditions[index % 4] });
  if (!check(created, { '노선 생성됨': (d) => d !== null && d.route !== null })) return;

  let route = created.route;
  const id = route.routeId;
  while (!route.cart.startable) {
    const card = route.cards.find((c) => c.addable && !c.carted);
    if (!check(card, { '담을 카드가 있음': (c) => c !== undefined })) return;
    route = call('PUT', `/routes/${id}/cart/${card.articleId}`, userId, 'cart_add');
    if (!route) return;
  }
  if (!call('POST', `/routes/${id}/boarding`, userId, 'boarding')) return;
  const ride = call('POST', `/routes/${id}/departure`, userId, 'departure');
  if (!ride) return;
  for (let stop = 1; stop <= ride.progress.totalStops; stop += 1) {
    call('GET', `/routes/${id}/stops/${stop}`, userId, 'stop');
    call('POST', `/routes/${id}/stops/${stop}/read`, userId, 'read');
  }
  call('POST', `/routes/${id}/arrival`, userId, 'arrival');
}

export function handleSummary(data) {
  const ms = (v) => (v === undefined ? '-' : v.toFixed(1));
  const rows = NAMES.map((name) => {
    const v = (data.metrics[`http_req_duration{name:${name}}`] || { values: {} }).values;
    return `| ${name} | ${ms(v.med)} | ${ms(v['p(95)'])} | ${ms(v['p(99)'])} | ${ms(v.max)} |`;
  });
  const all = data.metrics.http_req_duration.values;
  const failed = Object.entries(data.metrics).flatMap(([name, m]) =>
    Object.entries(m.thresholds || {}).filter(([, t]) => !t.ok).map(([rule]) => `${name}: ${rule}`));
  const steps = PROFILE !== 'stress' ? [] : ['| 초당 조회 반복 | p95 ms | 실패 |', '|---|---|---|', ...STEPS.map((r) => {
    const d = data.metrics[`http_req_duration{step:${r}}`];
    const f = data.metrics[`http_req_failed{step:${r}}`];
    return `| ${r} | ${d ? ms(d.values['p(95)']) : '-'} | ${f ? (f.values.rate * 100).toFixed(2) : '-'}% |`;
  }), ''];
  const text = [
    `# ${PROFILE} (${new Date().toISOString()})`, '',
    `- 요청 ${data.metrics.http_reqs.values.count}건, 초당 ${data.metrics.http_reqs.values.rate.toFixed(1)}건, `
      + `실패 ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%, `
      + `못 보낸 반복 ${data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0}회`,
    `- 기준 p95 ${P95_MS}ms: ${failed.length === 0 ? '통과' : `실패 (${failed.join(', ')})`}`, '', ...steps,
    '| API | p50 ms | p95 ms | p99 ms | 최대 ms |', '|---|---|---|---|---|',
    `| 전체 | ${ms(all.med)} | ${ms(all['p(95)'])} | ${ms(all['p(99)'])} | ${ms(all.max)} |`, ...rows, '',
  ].join('\n');
  return { stdout: text, [`/results/${__ENV.RESULT || PROFILE}.md`]: text };
}
