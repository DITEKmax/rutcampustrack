/**
 * M08 Группа 7 (P2-8/7, NEW-163) — k6 load test для geolocation checkin.
 *
 * Сценарий: 50 студентов одновременно нажимают «отметиться» в начале
 * пары — типичный пиковый нагрузочный паттерн для CheckinService.
 *
 * Каждый студент делает POST /api/attendance/checkin с случайным
 * смещением lat/lng в радиусе ~50m от кампуса — реалистичная variance
 * GPS-сенсоров. Ожидается: majority PRESENT (если в зоне), rate-limited
 * rejections на повторных кликах (CHKN-07).
 *
 * Threshold: p95 < 1s (более толстая граница чем bulk-mark из-за
 * M05 gRPC fan-out + Redis dedup), error rate < 5% (+/- rate limits).
 *
 * Запуск:
 *   k6 run tests/load/geolocation-flood.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const STUDENT_LOGIN_PREFIX = __ENV.STUDENT_LOGIN_PREFIX || 'student';
const STUDENT_PASSWORD = __ENV.STUDENT_PASSWORD || 'student_test_pass';

// Координаты кампуса РУТ МИИТ (Москва, примерно) — для реалистичной
// variance при тесте. Реальные координаты — в academic.campus_settings.
const CAMPUS_LAT = 55.8085;
const CAMPUS_LON = 37.6115;

export const options = {
  vus: 50,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
  tags: {
    test: 'geolocation-flood',
    scope: 'hot-path',
  },
};

export function setup() {
  // В реальном seed'е студенты — student, student00001..student00020.
  // Для setup логинимся одним test-user'ом (рейт-лимит на checkin
  // индивидуальный, 3/min per userId, так что 50 VU'шек "видят"
  // 3 checkin'а каждая, потом 429).
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ login: STUDENT_LOGIN_PREFIX, password: STUDENT_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, { 'login 200': (r) => r.status === 200 });
  const accessToken = loginRes.json('access_token');
  if (!accessToken) {
    throw new Error(`login failed: ${loginRes.status} ${loginRes.body}`);
  }
  return { accessToken };
}

export default function (data) {
  // Случайный jitter ~50m (0.0005 deg lat ≈ 55m).
  const latJitter = (Math.random() - 0.5) * 0.001;
  const lonJitter = (Math.random() - 0.5) * 0.001;

  const res = http.post(
    `${BASE_URL}/api/attendance/checkin`,
    JSON.stringify({
      lat: CAMPUS_LAT + latJitter,
      lng: CAMPUS_LON + lonJitter,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.accessToken}`,
      },
      tags: { endpoint: 'checkin' },
    }
  );

  // Ожидаемые коды: 200 (PRESENT), 404 (нет активной пары), 409 (уже
  // отмечен), 429 (rate limit), 422 (вне зоны).
  // НЕ ожидаемые: 500 (наш bug), timeout.
  check(res, {
    'checkin no 500': (r) => r.status !== 500,
    'checkin not timeout': (r) => r.status !== 0,
  });

  sleep(0.2);  // небольшой jitter между checkin-попытками
}
