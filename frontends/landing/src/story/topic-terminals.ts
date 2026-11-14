export interface TerminalExample {
  title: string
  command: string
  output: readonly { channel: string; detail: string }[]
}

const requestId = '42424242-abcd-4e51-803e-123456789abc'
const traceId = 'faceacc242c94f328a633a7db953b8ec'

export const topicTerminals: Readonly<Partial<Record<string, TerminalExample>>> = {
  observability: {
    title: 'Терминал наблюдаемости',
    command: "curl -i https://example.test/api/v1/student/today -H 'Authorization: Bearer <access-token>'",
    output: [
      { channel: 'Nginx', detail: 'HTTPS /api/ → Gateway' },
      { channel: 'HTTP', detail: '200 OK · X-Request-Id: ' + requestId },
      { channel: 'Tempo', detail: 'OTLP → tempo:4318/v1/traces · traceId=' + traceId },
      { channel: 'Loki', detail: 'Docker stdout/stderr → Alloy → Loki · requestId=' + requestId },
    ],
  },
  'ci-cd-frontend': {
    title: 'Сборка Vue в CI',
    command: 'GitHub Actions · ci.yml / mobile-vue-build-test',
    output: [
      { channel: 'Install', detail: 'npm ci · зависимости из lockfile' },
      { channel: 'Contracts', detail: 'npm run generate:types:check · снимки типов совпадают' },
      { channel: 'Check', detail: 'npm run typecheck · npm run test:web · node --test pwa-vue/tests/*.test.mjs' },
      { channel: 'Build', detail: 'npm run build · Web, PWA, Mini App' },
    ],
  },
  'cache-group': {
    title: 'Холодное и повторное чтение',
    command: 'GetGroup(groupId: 411)',
    output: [
      { channel: 'Redis', detail: 'MISS · groups::411' },
      { channel: 'PostgreSQL', detail: 'GroupRepository → группа 411' },
      { channel: 'Redis', detail: 'SET groups::411 · TTL 5 минут' },
      { channel: 'Redis', detail: 'Повтор GetGroup(411) → HIT' },
    ],
  },
  'report-export-status': {
    title: 'От очереди до PDF',
    command: 'POST /api/auth/export-jobs · недельный журнал, PDF',
    output: [
      { channel: 'HTTP', detail: '202 · queued' },
      { channel: 'Academic', detail: 'Worker берёт задачу · preparing' },
      { channel: 'Renderer', detail: 'Document Renderer возвращает байты PDF' },
      { channel: 'HTTP', detail: 'GET /api/auth/export-jobs/{jobId} → 200 · ready' },
      { channel: 'PWA', detail: 'POST /api/auth/export-jobs/{jobId}/ticket → скачивание PDF по билету' },
    ],
  },
}
