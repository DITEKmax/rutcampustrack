export const pwaDemoChannel = 'rct:landing:pwa-demo'

export interface PwaExchange {
  id: number
  method: string
  path: string
  request: string | null
  cached?: boolean
  response?: { status: number | null; body: string | null }
}

export type PwaTraceEvent = { actionId: number } & (
  | { type: 'action'; label: string }
  | { type: 'local-result'; result: string }
  | { type: 'request'; id: number; method: string; path: string; body: string | null; replaces?: number | undefined; cached?: boolean }
  | { type: 'request-body'; id: number; body: string }
  | { type: 'response'; id: number; status: number | null; body: string | null }
)

export interface PwaTraceState {
  actionId: number
  exchanges: PwaExchange[]
  local?: { label: string; result?: string }
}

export function applyPwaTraceEvent(state: PwaTraceState, event: PwaTraceEvent): PwaTraceState {
  if (event.type === 'action')
    return event.actionId > state.actionId
      ? { actionId: event.actionId, exchanges: [], local: { label: event.label } }
      : state
  if (event.actionId !== state.actionId) return state
  if (event.type === 'local-result')
    return state.local && state.exchanges.length === 0
      ? { ...state, local: { ...state.local, result: event.result } }
      : state
  if (event.type === 'request') {
    const { id, method, path, body, replaces } = event
    const exchange = { id, method, path, request: body, cached: event.cached ?? false }
    const exchanges = replaces === undefined ? [...state.exchanges, exchange]
      : state.exchanges.map(previous => previous.id === replaces ? exchange : previous)
    return { ...state, exchanges }
  }
  return {
    ...state,
    exchanges: state.exchanges.map(exchange => exchange.id !== event.id ? exchange
      : event.type === 'request-body' ? { ...exchange, request: event.body }
      : { ...exchange, response: { status: event.status, body: event.body } }),
  }
}

export function jsonTokens(line: string) {
  const tokens = /"(?:[^"\\]|\\.)*"(\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g
  const parts: { text: string; style?: string }[] = []
  let offset = 0
  for (const match of line.matchAll(tokens)) {
    if (match.index > offset) parts.push({ text: line.slice(offset, match.index) })
    const text = match[0]
    const style = match[1] ? 'type' : text.startsWith('"') ? 'string' : /^-?\d/.test(text) ? 'number' : 'keyword'
    parts.push({ text, style })
    offset = match.index + text.length
  }
  if (offset < line.length) parts.push({ text: line.slice(offset) })
  return parts
}

// Automatic session maintenance, badge polling and check-in diagnostics.
const systemPaths = [
  '/api/auth/refresh',
  '/api/auth/session',
  '/api/auth/ws-ticket',
  '/api/notifications/inbox/unread-count',
  '/api/v1/student/checkin/acquisition-reports',
  '/api/v1/student/session',
]
export const studentSectionPath = {
  today: '/api/v1/student/today',
  homework: '/api/v1/student/homework',
  attendance: '/api/v1/student/attendance',
  statistics: '/api/v1/student/statistics',
  schedule: '/api/v1/student/schedule',
  requests: '/api/v1/student/requests',
  map: '/api/v1/map/manifest',
  notifications: '/api/notifications/inbox',
  notificationSettings: '/api/notifications/preferences/v2',
  profile: '/api/academic/users/me',
} as const

export type StudentTraceSection = keyof typeof studentSectionPath

const notificationCatalogPath = '/api/notifications/catalog'

const preloadedPaths = new Set<string>([
  studentSectionPath.today, studentSectionPath.homework, studentSectionPath.attendance,
  studentSectionPath.statistics, studentSectionPath.schedule, '/api/auth/device-preferences',
  '/api/academic/assistants/me/permissions', '/api/v1/student/requests/options',
])

function sectionSnapshotKey(url: URL): string | undefined {
  const path = url.pathname
  if (path === studentSectionPath.homework && (url.searchParams.has('from') || url.searchParams.has('to')))
    return undefined
  if (path === studentSectionPath.requests) {
    const bucket = url.searchParams.get('bucket')
    const page = url.searchParams.get('page')
    return (bucket === null || bucket === 'OPEN') && (page === null || page === '0') ? path : undefined
  }
  if (path === studentSectionPath.notifications)
    return !url.searchParams.has('cursor') && url.searchParams.get('unreadOnly') !== 'true' ? path : undefined
  return path === notificationCatalogPath || Object.values(studentSectionPath).some(value => value === path)
    ? path : undefined
}

const sensitiveField = /password|token$|ticket|authorization|cookie|bearer|downloadPath|^otp$/i

function redact(_key: string, value: unknown): unknown {
  if (sensitiveField.test(_key))
    return '[скрыто]'
  return value
}

function jsonText(value: unknown, privateAuth = false): string {
  return JSON.stringify(value, (key, item) =>
    privateAuth && /^(code|login|email)$/i.test(key) ? '[скрыто]' : redact(key, item), 2)
}

async function formText(body: FormData): Promise<string> {
  const fields: Record<string, unknown> = Object.create(null)
  await Promise.all([...new Set(body.keys())].map(async key => {
    const values = await Promise.all(body.getAll(key).map(async value => {
      if (typeof value === 'string') return value
      if (value.type === 'application/json') {
        try { return JSON.parse(await value.text()) as unknown } catch { /* Report the metadata below. */ }
      }
      return { name: value.name, type: value.type, bytes: value.size }
    }))
    fields[key] = values.length === 1 ? values[0] : values
  }))
  return jsonText(fields)
}

function bodyText(text: string, privateAuth = false): string {
  try { return jsonText(JSON.parse(text), privateAuth) } catch { return privateAuth ? '[скрыто]' : text }
}

function readablePath(url: URL): string {
  if (url.pathname.startsWith('/api/report-download/')) return '/api/report-download/[скрыто]'
  for (const key of new Set(url.searchParams.keys())) {
    if (sensitiveField.test(key)) url.searchParams.set(key, '[скрыто]')
  }
  return url.pathname + url.search
}

function requestBody(input: Request | null, init: RequestInit | undefined, privateAuth: boolean): string | Promise<string> {
  const body = init?.body
  const formatText = (text: string) => bodyText(text, privateAuth)
  if (typeof body === 'string') return formatText(body)
  if (body instanceof FormData) return privateAuth ? '[скрыто]' : formText(body)
  if (body instanceof URLSearchParams) return jsonText(Object.fromEntries(body), privateAuth)
  if (body instanceof Blob && body.type.includes('json')) return body.text().then(formatText)
  if (body instanceof Blob) return `// ${body.type || 'Файл'} · ${body.size} Б`
  if (body != null) return '// Двоичные данные'
  if (!input?.body) return ''
  const copy = input.clone()
  const type = copy.headers.get('content-type') ?? ''
  return /^(multipart\/form-data|application\/x-www-form-urlencoded)/.test(type)
    ? privateAuth ? '[скрыто]' : copy.formData().then(formText)
    : type.includes('json') || type.startsWith('text/') ? copy.text().then(formatText)
    : '// Двоичные данные'
}

async function responseBody(copy: Response): Promise<string> {
  const type = copy.headers.get('content-type') ?? ''
  return copy.body === null ? ''
    : type.includes('json') ? bodyText(await copy.text())
    : type.startsWith('text/') ? await copy.text()
    : `// ${type || 'Файл'} · ${(await copy.arrayBuffer()).byteLength} Б`
}

export function createPwaTraceObserver(publish: (event: PwaTraceEvent) => void) {
  let activeAction = 0
  let sequence = 0
  let openPaths: string[] = []
  const replayedIds = new Map<string, number>()
  const snapshots = new Map<string, PwaExchange>()

  function beginAction(label: string, section?: StudentTraceSection): number {
    openPaths = section === undefined ? [] : [studentSectionPath[section]]
    if (section === 'notificationSettings') openPaths.push(notificationCatalogPath)
    replayedIds.clear()
    const actionId = ++activeAction
    publish({ type: 'action', actionId, label })
    for (const key of openPaths) {
      // A stale catalog on its own is not the settings currently rendered by PWA.
      if (key === notificationCatalogPath && !snapshots.has(studentSectionPath.notificationSettings)) continue
      const snapshot = snapshots.get(key)
      if (!snapshot) continue
      const { id, method, path, request, response } = snapshot
      replayedIds.set(key, id)
      publish({ type: 'request', actionId, id, method, path, body: request, cached: true })
      if (response) publish({ type: 'response', actionId, id, ...response })
    }
    return actionId
  }

  function localResult(actionId: number, result: string): void {
    if (actionId === activeAction) publish({ type: 'local-result', actionId, result })
  }

  function wrap(transport: typeof fetch): typeof fetch {
    return async (input, init) => {
      const source = input instanceof Request ? input : null
      const url = new URL(source?.url ?? String(input), location.origin)
      if (url.origin !== location.origin || !url.pathname.startsWith('/api/') || systemPaths.includes(url.pathname))
        return transport(input, init)

      // Keep the generation captured at dispatch even when either body resolves later.
      const actionId = activeAction
      const method = (init?.method ?? source?.method ?? 'GET').toUpperCase()
      const isRead = method === 'GET'
      if (!isRead) {
        if (url.pathname === '/api/auth/session/active-role' || url.pathname === '/api/auth/logout') snapshots.clear()
        if (url.pathname.startsWith(studentSectionPath.notifications)) snapshots.delete(studentSectionPath.notifications)
        if (url.pathname === studentSectionPath.notificationSettings) snapshots.delete(studentSectionPath.notificationSettings)
      }
      const visible = actionId === 0
        ? isRead && url.pathname === studentSectionPath.today && !url.search
        : openPaths.length === 0 || !isRead || !preloadedPaths.has(url.pathname) || openPaths.includes(url.pathname)
      const snapshotKey = isRead ? sectionSnapshotKey(url) : undefined
      if (!visible && !snapshotKey) return transport(input, init)

      const id = ++sequence
      const path = readablePath(url)
      let body: string | Promise<string>
      const privateAuth = /^\/api\/auth\/(login|otp\/|password-reset\/)/.test(url.pathname)
      try { body = requestBody(source, init, privateAuth) } catch { body = '// Не удалось прочитать тело запроса' }
      const request = typeof body === 'string' ? body : null
      const snapshot: PwaExchange | undefined = snapshotKey ? { id, method, path, request } : undefined
      if (snapshotKey && snapshot) snapshots.set(snapshotKey, snapshot)

      if (visible) {
        const replaces = snapshotKey ? replayedIds.get(snapshotKey) : undefined
        if (replaces !== undefined && snapshotKey) replayedIds.delete(snapshotKey)
        publish({ type: 'request', actionId, id, method, path, body: request, replaces })
      }

      function publishUpdate(update: { type: 'request-body'; body: string } |
        { type: 'response'; status: number | null; body: string | null }) {
        if (snapshot) {
          if (update.type === 'request-body') snapshot.request = update.body
          else snapshot.response = { status: update.status, body: update.body }
        }
        if (visible) publish({ ...update, actionId, id })
        // An in-flight preload can already be serving the screen the user just opened.
        // Forward its updates only while that exact pair is still in view.
        if (snapshotKey && replayedIds.get(snapshotKey) === id && activeAction !== actionId)
          publish({ ...update, actionId: activeAction, id })
      }

      if (typeof body !== 'string') {
        void body.catch(() => '// Не удалось прочитать тело запроса')
          .then(text => publishUpdate({ type: 'request-body', body: text }))
      }

      let response: Response
      try {
        response = await transport(input, init)
      } catch (error) {
        publishUpdate({ type: 'response', status: null, body: '// Ответ не получен' })
        throw error
      }
      let text: Promise<string>
      try { text = responseBody(response.clone()) } catch { text = Promise.resolve('// Не удалось прочитать тело ответа') }
      // A status is already available, even if the copy of the body is still being read.
      publishUpdate({ type: 'response', status: response.status, body: null })
      void text.catch(() => '// Не удалось прочитать тело ответа')
        .then(body => publishUpdate({ type: 'response', status: response.status, body }))
      return response
    }
  }

  return { beginAction, localResult, wrap }
}
