/**
 * M08 Группа 7 (P2-8/7, NEW-163) — k6 load test для bulk-mark endpoint.
 *
 * Сценарий: 10 старост одновременно отмечают 30 студентов одной группы
 * через POST /api/attendance/marks/batch (M05 G4 — batch endpoint).
 *
 * Цель — baseline p95 latency для release-process check:
 * threshold p95 < 500ms, error rate < 1%.
 *
 * Запуск:
 *   k6 run tests/load/bulk-mark.js
 *
 * С overrides:
 *   k6 run -e BASE_URL=https://ruttrack.site \
 *          -e HEADMAN_LOGIN=headman \
 *          -e HEADMAN_PASSWORD=headman_test_pass \
 *          tests/load/bulk-mark.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADMAN_LOGIN = __ENV.HEADMAN_LOGIN || 'headman';
const HEADMAN_PASSWORD = __ENV.HEADMAN_PASSWORD || 'headman_test_pass';

export const options = {
  vus: 10,
  duration: '2m',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
  // Тэги для выборки в Grafana при nightly runs (v0.1).
  tags: {
    test: 'bulk-mark',
    scope: 'hot-path',
  },
};

// Setup — один раз перед прогоном: логин, получение JWT + group + lesson id.
export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ login: HEADMAN_LOGIN, password: HEADMAN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, { 'login 200': (r) => r.status === 200 });
  const accessToken = loginRes.json('access_token');
  if (!accessToken) {
    throw new Error(`login failed: ${loginRes.status} ${loginRes.body}`);
  }

  // Получаем текущее активное lesson для группы старосты
  const today = new Date().toISOString().split('T')[0];
  const scheduleRes = http.get(`${BASE_URL}/api/schedule/lessons?date=${today}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  check(scheduleRes, { 'schedule 200': (r) => r.status === 200 });
  const lessons = scheduleRes.json() || [];
  const activeLesson = lessons.find((l) => l.status === 'active' || l.status === 'closed');

  if (!activeLesson) {
    console.warn('No active lesson for today — bulk-mark test will skip mark step');
  }

  return {
    accessToken,
    lessonId: activeLesson?.id || null,
    groupId: activeLesson?.groupId || null,
  };
}

export default function (data) {
  if (!data.lessonId) {
    sleep(1);
    return;
  }

  // Генерируем 30 mock'овых marks (userId 100..129 — достаточно для
  // perf seed'а из M05). На реальном прогоне — backend вернёт partial
  // success если какие-то userId не из группы.
  const marks = [];
  for (let userId = 100; userId < 130; userId++) {
    marks.push({
      userId,
      status: 'present',
      lessonId: data.lessonId,
    });
  }

  const res = http.post(
    `${BASE_URL}/api/attendance/marks/batch`,
    JSON.stringify({ marks, lessonId: data.lessonId }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.accessToken}`,
      },
      tags: { endpoint: 'marks/batch' },
    }
  );

  check(res, {
    'batch 200/207': (r) => r.status === 200 || r.status === 207,
    'response has marks array': (r) => {
      try {
        return Array.isArray(r.json('marks'));
      } catch {
        return false;
      }
    },
  });

  sleep(1);  // "думает" между batch'ами (реалистичный паттерн старосты)
}
