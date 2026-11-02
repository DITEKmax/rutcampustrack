import { readFileSync, realpathSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, searchForWorkspaceRoot } from 'vite'
import { browserDependencyOptimization } from '../dev-server'

type Review = { role: string; label: string; entry: string; initial: string; fixture?: string; registry?: string; examples?: readonly string[] }
const reviews: readonly Review[] = [
  { role: 'Студент', label: 'Сегодня', entry: 'today', initial: 'default', examples: ['default'], },
  { role: 'Студент', label: 'Посещаемость', entry: 'attendance', initial: 'attendance-days', examples: ['attendance-days', 'attendance-empty-day', 'attendance-error', 'attendance-read-only'] },
  { role: 'Студент', label: 'Статистика', entry: 'statistics', initial: 'statistics-overview', examples: ['statistics-overview', 'subject-all', 'subject-one-type'] },
  { role: 'Студент', label: 'Домашние задания', entry: 'homework', initial: 'homework-feed', examples: ['homework-feed', 'homework-root20'] },
  { role: 'Студент', label: 'Заявки', entry: 'requests', initial: 'open', fixture: 'requests-fixtures', registry: 'requestsReviewScenarios' },
  { role: 'Староста', label: 'Сегодня и журнал', entry: 'headman', initial: 'today-current', fixture: 'headman-fixtures', registry: 'headmanReviewStates' },
  { role: 'Староста', label: 'Статистика', entry: 'headman-stats', initial: 'overview-autumn', fixture: 'headman-stats-fixtures', registry: 'HEADMAN_STATS_REVIEW_STATES' },
  { role: 'Староста', label: 'Домашние задания', entry: 'headman-homework', initial: 'calendar', fixture: 'headman-homework-fixtures', registry: 'headmanHomeworkStates' },
  { role: 'Староста', label: 'Группа и предметы', entry: 'headman-group-subjects', initial: 'group-list', fixture: 'headman-group-subjects-fixtures', registry: 'headmanGroupSubjectsStates' },
  { role: 'Староста', label: 'Заявки', entry: 'headman-requests', initial: 'queue', fixture: 'headman-requests-review-fixtures', registry: 'requestReviewStates' },
  { role: 'Староста', label: 'Конструктор и управление парами', entry: 'headman-schedule-management', initial: 'constructor-filled', fixture: 'headman-schedule-management-fixtures', registry: 'scheduleManagementStates' },
  { role: 'Преподаватель', label: 'Главная, журнал, одна пара и статистика', entry: 'teacher', initial: 'home', fixture: 'teacher-review-fixtures', registry: 'teacherReviewStates' },
  { role: 'Администратор', label: 'Главная, профиль и навигация', entry: 'admin-shell', initial: 'overview-today', fixture: 'admin-shell-fixtures', registry: 'adminShellStates' },
  { role: 'Администратор', label: 'Пользователи', entry: 'admin-users', initial: 'list', fixture: 'admin-users-fixtures', registry: 'adminUsersReviewStates' },
  { role: 'Администратор', label: 'Группы', entry: 'admin-groups', initial: 'active', fixture: 'admin-groups-fixtures', registry: 'adminGroupsStates' },
  { role: 'Администратор', label: 'Семестры', entry: 'admin-semester', initial: 'list', fixture: 'admin-semester-fixtures', registry: 'adminSemesterStates' },
  { role: 'Администратор', label: 'Схемы кампуса', entry: 'admin-map', initial: 'full', fixture: 'admin-map-fixtures', registry: 'adminMapStates' },
  { role: 'Общие экраны', label: 'Профиль, роли и карта', entry: 'profile-map', initial: 'profile', fixture: 'profile-map-fixtures', registry: 'profileMapScenarios' },
  { role: 'Общие экраны', label: 'Уведомления четырёх ролей', entry: 'notifications', initial: '', examples: [] },
]
const escape = (value: string) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;')
const href = (review: Review, state = review.initial) => `/?${new URLSearchParams({ screen: review.entry, ...(state ? { state } : {}) })}`
const directHref = (review: Review, state = review.initial, theme = 'dark') => `/${review.entry}-review.html?${new URLSearchParams({ shell: 'pwa', capture: '1', catalog: '0', theme, ...(state ? { state } : {}) })}`
function states(review: Review): readonly string[] {
  if (!review.fixture || !review.registry) return review.examples ?? []
  // Read only the existing flat state registry, including tuple registries.
  // Do not evaluate fixture factories or initialise their browser-side state here.
  const source = readFileSync(new URL(`../mobile-core/src/dev/${review.fixture}.ts`, import.meta.url), 'utf8')
  const array = source.match(new RegExp(`export const ${review.registry} = \\[([\\s\\S]*?)\\n\\]`))?.[1]
  if (!array) throw new Error(`Missing review state registry: ${review.registry}`)
  const result = [...array.matchAll(/^\s*(?:\[\s*)?'([^']+)'/gm)].map(match => match[1]!)
  if (!result.includes(review.initial)) throw new Error(`Unknown initial review state: ${review.initial}`)
  return result
}

const isolation = `
if (location.hostname !== '127.0.0.1' || location.port !== '14221') throw new Error('PWA review requires 127.0.0.1:14221');
const requestedTheme = new URLSearchParams(location.search).get('theme') === 'light' ? 'light' : 'dark';
document.documentElement.setAttribute('data-theme', requestedTheme);
document.documentElement.style.colorScheme = requestedTheme;
const localAssetFetch = window.fetch.bind(window);
window.fetch = async (input, init) => {
  const url = new URL(input instanceof Request ? input.url : String(input), location.origin);
  if (url.origin !== location.origin || url.pathname === '/api' || url.pathname.startsWith('/api/'))
    return Response.json({title:'PWA visual review: real network API is disabled'}, {status:501});
  return localAssetFetch(input, init);
};
if ('serviceWorker' in navigator) void navigator.serviceWorker.getRegistrations().then(registrations => {
  for (const registration of registrations) if (new URL(registration.scope).origin === location.origin) void registration.unregister();
});
`

/** Serve existing synthetic review entries only. Never imported by the release config. */
export default defineConfig(({ command }) => {
  if (command !== 'serve') throw new Error('PWA visual review is dev-only; use the normal config to build.')
  const root = fileURLToPath(new URL('.', import.meta.url))
  const inventory = reviews.map(review => ({ ...review, url: href(review), directUrl: directHref(review), states: states(review) }))
  const allowed = new Set(reviews.map(review => `/${review.entry}-review.html`))
  const catalog = (query: URLSearchParams) => {
    const selected = inventory.find(review => review.entry === query.get('screen')) ?? inventory[0]!
    const state = query.get('state') ?? selected.initial
    const theme = query.get('theme') === 'light' ? 'light' : 'dark'
    const direct = directHref(selected, state, theme) + (selected.entry === 'notifications' && ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'].includes(query.get('role') ?? '') ? `&role=${query.get('role')}` : '')
    return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><link rel="stylesheet" href="/visual-review.pcss?direct"><title>PWA · визуальный стенд</title></head><body><main><h1>PWA · визуальный стенд</h1><p>Существующие синтетические сценарии четырёх ролей. Реальный API и service worker отключены.</p><p><a href="/visual-review-inventory.json">JSON: экраны и состояния</a></p><div class="pwa-review-layout"><nav aria-label="Каталог экранов">${[...new Set(reviews.map(review => review.role))].map(role => `<section><h2>${escape(role)}</h2><ul>${inventory.filter(review => review.role === role).map(review => `<li><a href="${escape(review.url)}">${escape(review.label)}</a>${review.states.length ? `<details><summary>Состояния (${review.states.length})</summary><ul>${review.states.map(state => `<li><a href="${escape(href(review, state))}">${escape(state)}</a></li>`).join('')}</ul></details>` : `<p>${['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'].map(role => `<a href="${escape(`${review.url}&role=${role}`)}">${role}</a>`).join(' · ')}</p>`}</li>`).join('')}</ul></section>`).join('')}</nav><section class="pwa-review-viewport" aria-label="Мобильный просмотр"><h2>${escape(selected.role)} · ${escape(selected.label)}</h2><p>Viewport 390 × 844 · ${escape(state || 'default')} · <a href="${escape(direct)}" target="_blank" rel="noopener">Открыть напрямую</a></p><p><a href="${escape(href(selected, state) + '&theme=dark')}">Тёмная</a> · <a href="${escape(href(selected, state) + '&theme=light')}">Светлая</a></p><iframe class="pwa-review-frame" title="${escape(selected.role + ': ' + selected.label)}" src="${escape(direct)}" width="390" height="844"></iframe></section></div></main></body></html>`
  }
  return {
    root,
    cacheDir: '../../.agent/evidence/pwa-visual-1102/vite-cache',
    plugins: [vue(), {
      name: 'rct-isolated-pwa-visual-review',
      transformIndexHtml: { order: 'pre', handler: html => ({ html, tags: [{ tag: 'script', children: isolation, injectTo: 'head-prepend' }] }) },
      configureServer(server) {
        server.middlewares.use(async (request, response, next) => {
          const path = (request.url ?? '/').split('?')[0] ?? '/'
          if (path === '/api' || path.startsWith('/api/')) {
            response.writeHead(501, { 'Content-Type': 'application/json' })
            response.end(JSON.stringify({ title: 'PWA visual review: real API is disabled' }))
            return
          }
          if (['/sw.js', '/sw-assets.js', '/version.json', '/src/main.ts'].includes(path)) {
            response.writeHead(410); response.end('Production bootstrap and service worker are disabled in visual review'); return
          }
          if (path === '/visual-review-inventory.json') {
            response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' })
            response.end(JSON.stringify(inventory)); return
          }
          if (['/', '/index.html', '/visual-review.html'].includes(path)) {
            try {
              const html = await server.transformIndexHtml('/visual-review.html', catalog(new URL(request.url ?? '/', 'http://127.0.0.1:14221').searchParams))
              response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); response.end(html)
            } catch (error) { next(error) }
            return
          }
          if ((path.endsWith('.html') || request.headers.accept?.includes('text/html')) && !allowed.has(path)) {
            response.writeHead(404); response.end('Use the PWA visual review catalog at /'); return
          }
          next()
        })
      },
    }],
    optimizeDeps: browserDependencyOptimization,
    css: { postcss: '../postcss.config.mjs' },
    resolve: { alias: { '@rct/mobile-core': fileURLToPath(new URL('../mobile-core/src/index.ts', import.meta.url)) } },
    define: { __RCT_SERVICE_WORKER_ENABLED__: false, __RCT_APP_VERSION__: JSON.stringify('visual-review'), __RCT_MIN_SUPPORTED_VERSION__: JSON.stringify('visual-review'), __RCT_FORCE_UPDATE__: false },
    server: {
      host: '127.0.0.1', port: 14221, strictPort: true,
      headers: {
        'Cache-Control': 'no-store',
        'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' ws://127.0.0.1:14221; worker-src 'none'; object-src 'none'; frame-src 'self' blob:; form-action 'none'",
        'Permissions-Policy': 'geolocation=(), camera=(), microphone=()',
      },
      fs: { allow: [searchForWorkspaceRoot(root), realpathSync(new URL('../node_modules', import.meta.url))] },
    },
  }
})
