export const canvas = { width: 1200, height: 1660, labelGutter: 139 }
const height = 52
const spine = 490

function node(name: string, x: number, y: number, width: number, tone?: 'accent' | 'data', hint?: string) {
  return { name, x, y, width, height, tone, hint }
}

export const nodes = {
  browser: node('Браузер / WebView', 392, 34, 196),
  nginx: node('Nginx', 392, 180, 196, 'accent'),
  web: node('Web', 636, 322, 126, undefined, '/'),
  pwa: node('PWA', 776, 322, 126, undefined, '/app/'),
  mini: node('Mini App', 916, 322, 126, undefined, '/mini-app/'),
  landing: node('Landing', 1056, 322, 126, undefined, '/presentation/'),
  gateway: node('API Gateway', 392, 482, 196, 'accent'),
  auth: node('Auth', 94, 482, 176),
  bff: node('Mobile BFF', 392, 634, 196, 'accent'),
  bot: node('Notification Bot', 836, 634, 188),
  academic: node('Academic', 251, 798, 182),
  schedule: node('Schedule', 481, 798, 182),
  attendance: node('Attendance', 712, 798, 182, 'accent'),
  notification: node('Notification', 942, 798, 182),
  pgAcademic: node('PostgreSQL Academic', 24, 1005, 136, 'data'),
  pgSchedule: node('PostgreSQL Schedule', 176, 1005, 146, 'data'),
  redis: node('Redis', 339, 1005, 137, 'data'),
  redisCache: node('Redis Cache', 493, 1005, 139, 'data'),
  mongo: node('MongoDB Attendance', 649, 1005, 155, 'data'),
  rabbit: node('RabbitMQ', 821, 1005, 148, 'data'),
  renderer: node('Document Renderer', 986, 1005, 183),
  grafana: node('Grafana', 479, 1130, 176, 'data'),
  prometheus: node('Prometheus', 243, 1282, 172, 'data'),
  tempo: node('Tempo', 578, 1282, 172, 'data'),
  loki: node('Loki', 800, 1282, 170, 'data'),
  alloy: node('Alloy', 1014, 1282, 165, 'data'),
  dockerLogs: node('Docker stdout / stderr', 1014, 1425, 165, 'data'),
}
export type NodeId = keyof typeof nodes
export type LevelKind = 'entry' | 'clients' | 'routing' | 'domains' | 'data' | 'telemetry'

export const levels: { kind: LevelKind; title: string; y: number; rows: NodeId[][] }[] = [
  { kind: 'entry', title: 'Публичный вход', y: 26, rows: [['browser'], ['nginx']] },
  { kind: 'clients', title: 'Веб-клиенты', y: 311, rows: [['web', 'pwa'], ['mini', 'landing']] },
  { kind: 'routing', title: 'Маршрутизация и допуск', y: 471, rows: [['gateway', 'auth'], ['bff', 'bot']] },
  { kind: 'domains', title: 'Доменные сервисы', y: 787, rows: [['academic', 'schedule'], ['attendance', 'notification']] },
  { kind: 'data', title: 'Данные и инфраструктура', y: 994, rows: [['pgAcademic', 'pgSchedule'], ['redis', 'redisCache'], ['mongo', 'rabbit'], ['renderer']] },
  { kind: 'telemetry', title: 'Наблюдаемость', y: 1120, rows: [['grafana'], ['prometheus', 'tempo'], ['loki', 'alloy'], ['dockerLogs']] },
]

type Protocol = 'HTTPS' | 'HTTP' | 'REST' | 'gRPC' | 'gRPC/TLS' | 'PostgreSQL' | 'MongoDB' | 'RESP' | 'AMQP' | 'OTLP/HTTP' | 'OTLP/gRPC' | 'Docker API'
export type Point = readonly [number, number]
export interface TopologyEdge {
  from: NodeId
  to: NodeId
  protocol: Protocol
  port: number | null
  detail: string
  points: Point[] | undefined
  pulse: boolean
  telemetry: boolean
}

function edge(
  from: NodeId, to: NodeId, protocol: Protocol, port: TopologyEdge['port'], detail: string,
  options: { points?: Point[]; pulse?: boolean; telemetry?: boolean } = {},
): TopologyEdge {
  return {
    from, to, protocol, port, detail,
    points: options.points,
    pulse: options.pulse ?? false,
    telemetry: options.telemetry ?? false,
  }
}

export function roundedPath(points: Point[]) {
  return points.reduce((path, point, index) => {
    const previous = points[index - 1]
    if (!previous) return 'M' + point.join(' ')
    const next = points[index + 1]
    if (!next) return path + 'L' + point.join(' ')
    return path + 'L' + towards(point, previous).join(' ') + 'Q' + point.join(' ') + ' ' + towards(point, next).join(' ')
  }, '')
}

function towards([x, y]: Point, [nextX, nextY]: Point): Point {
  const dx = nextX - x, dy = nextY - y
  const length = Math.hypot(dx, dy)
  const ratio = length ? Math.min(8, length / 2) / length : 0
  return [x + dx * ratio, y + dy * ratio]
}

function vertical(from: NodeId, to: NodeId, x: number): Point[] {
  return [[x, nodes[from].y + height], [x, nodes[to].y - 2]]
}

function down(from: NodeId, to: NodeId, y: number, startX: number, endX: number): Point[] {
  return [[startX, nodes[from].y + height], [startX, y], [endX, y], [endX, nodes[to].y - 2]]
}

const frontends = ['web', 'pwa', 'mini', 'landing'] as const
const domains = ['academic', 'schedule', 'attendance', 'notification'] as const
const grpcDomains = ['auth', 'academic', 'schedule', 'attendance'] as const
const javaTargets = [
  ['auth', 9090], ['academic', 9091], ['schedule', 9092], ['attendance', 9093],
  ['notification', 9094], ['renderer', 9095], ['bff', 9080], ['gateway', 8080],
] as const

export const edges: TopologyEdge[] = [
  edge('browser', 'nginx', 'HTTPS', 443, 'Публичный вход. HTTP :80 перенаправляет на HTTPS, кроме ACME.', { points: vertical('browser', 'nginx', spine), pulse: true }),
  ...frontends.map((to, index) => {
    const y = 224 - index * 10
    const x = nodes[to].x + nodes[to].width / 2
    return edge('nginx', to, 'HTTP', 80, 'Внутренний контейнер статики · ' + nodes[to].hint, {
      points: [[588, y], [x, y], [x, nodes[to].y - 2]], pulse: index === 1,
    })
  }),
  edge('nginx', 'gateway', 'HTTP', 8080, 'Проксирование /api/ и WebSocket на /api/ws/.', { points: vertical('nginx', 'gateway', spine), pulse: true }),
  edge('gateway', 'auth', 'HTTP', 9090, 'На применимых защищённых маршрутах Gateway вызывает POST /internal/auth/admit, проверяет ответ Auth и сам пересылает запрос целевому сервису. Маршруты API Auth также идут на этот порт.', { points: [[392, 508], [272, 508]], pulse: true }),
  edge('gateway', 'bff', 'REST', 9080, 'Gateway сам передаёт запросы, адресованные Mobile BFF, после необходимых проверок. Здесь проходит /api/v1/student/** и геоотметка.', { points: vertical('gateway', 'bff', spine), pulse: true }),
  edge('bot', 'bff', 'gRPC/TLS', 19098, 'Контур Bot Read.'),
  ...domains.map((to, index) => edge('gateway', to, 'REST', 9091 + index, 'Прямой маршрут доменного API.')),
  ...grpcDomains.map((to, index) => edge('bff', to, 'gRPC', 19090 + index, to === 'attendance' ? 'Геоотметку обрабатывает Attendance.' : 'Клиентские проекции и вызовы Bot Read.',
    to === 'attendance' ? { points: down('bff', to, 746, 490, 783), pulse: true } : {})),
  edge('attendance', 'schedule', 'gRPC', 19092, 'Данные занятия и геозоны.'),
  edge('attendance', 'academic', 'gRPC', 19091, 'Данные группы и учащегося.'),
  edge('academic', 'renderer', 'gRPC', 19095, 'Конвертация сформированных документов.'),
  edge('attendance', 'renderer', 'gRPC', 19095, 'Конвертация сформированных документов.'),
  edge('auth', 'pgAcademic', 'PostgreSQL', 5432, 'Данные Auth в общем экземпляре PostgreSQL.'),
  edge('academic', 'pgAcademic', 'PostgreSQL', 5432, 'Данные Academic.'),
  edge('schedule', 'pgSchedule', 'PostgreSQL', 5432, 'Отдельный экземпляр PostgreSQL для расписания.'),
  edge('attendance', 'mongo', 'MongoDB', 27017, 'Посещение, ответ на команду, аудит и outbox.', { points: vertical('attendance', 'mongo', 754), pulse: true }),
  edge('gateway', 'redis', 'RESP', 6379, 'Хранилище состояния и лимитов Gateway.'),
  edge('auth', 'redis', 'RESP', 6379, 'Хранилище состояния Auth.'),
  edge('notification', 'redis', 'RESP', 6379, 'Хранилище состояния Notification.'),
  edge('academic', 'redisCache', 'RESP', 6379, 'Отдельный экземпляр Redis для кэша Academic.'),
  edge('attendance', 'rabbit', 'AMQP', 5672, 'Публикация событий Attendance.'),
  edge('academic', 'rabbit', 'AMQP', 5672, 'Публикация событий Academic.'),
  edge('schedule', 'rabbit', 'AMQP', 5672, 'Публикация событий Schedule.'),
  edge('rabbit', 'notification', 'AMQP', 5672, 'Доставка событий в очередь Notification. Указан порт RabbitMQ.'),
  edge('rabbit', 'bot', 'AMQP', 5672, 'Доставка событий в очередь Python-бота. Указан порт RabbitMQ.'),
  ...javaTargets.map(([id, port]) => edge('prometheus', id, 'HTTP', port, 'Prometheus опрашивает /actuator/prometheus.', {
    telemetry: true,
    pulse: id === 'academic',
  })),
  edge('prometheus', 'bot', 'HTTP', 8081, 'Prometheus опрашивает /metrics в приватной сети.', { telemetry: true }),
  ...javaTargets.map(([id]) => edge(id, 'tempo', 'OTLP/HTTP', 4318, 'Сервис отправляет трассировки в Tempo, /v1/traces.', {
    telemetry: true,
    pulse: id === 'attendance',
  })),
  edge('bot', 'tempo', 'OTLP/gRPC', 4317, 'Python-бот отправляет трассировки в Tempo.', { telemetry: true }),
  edge('grafana', 'prometheus', 'HTTP', 9090, 'Grafana читает метрики из Prometheus.', { points: down('grafana', 'prometheus', 1220, 522, 362), telemetry: true }),
  edge('grafana', 'tempo', 'HTTP', 3200, 'Grafana читает трассировки из Tempo.', { points: down('grafana', 'tempo', 1237, 562, 648), telemetry: true }),
  edge('grafana', 'loki', 'HTTP', 3100, 'Grafana читает логи из Loki.', { points: down('grafana', 'loki', 1226, 610, 871), telemetry: true }),
  edge('alloy', 'loki', 'HTTP', 3100, 'Alloy отправляет собранные логи на /loki/api/v1/push.', { points: [[1014, 1308], [972, 1308]], telemetry: true, pulse: true }),
  edge('alloy', 'dockerLogs', 'Docker API', null, 'Alloy читает stdout/stderr контейнеров своего Compose-проекта через /var/run/docker.sock.', { points: vertical('alloy', 'dockerLogs', 1096), telemetry: true }),
]

export const listening: Partial<Record<NodeId, string[]>> = {
  nginx: ['HTTPS :443', 'HTTP :80'],
  grafana: ['HTTP :3000'],
  alloy: ['HTTP :12345'],
}

export function edgeKey(edge: TopologyEdge) {
  return edge.from + '-' + edge.to + '-' + (edge.port ?? 'socket')
}
