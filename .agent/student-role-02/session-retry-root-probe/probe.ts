import { StudentApi } from './src/student-client'
import { usePwaAuth } from './src/pwa-auth'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

async function main() {
  const firstResponse = deferred<Response>()
  let owner: 'A' | 'B' = 'A'
  const calls: { owner: string; method: string; body: string }[] = []
  let refreshCalls = 0
  const api = new StudentApi({
    accessToken: () => `synthetic-${owner}`,
    onUnauthorized: async () => { refreshCalls++ },
    fetcher: async (_path, init) => {
      const usedOwner = new Headers(init?.headers).get('Authorization') === 'Bearer synthetic-A' ? 'A' : 'B'
      calls.push({ owner: usedOwner, method: init?.method ?? 'GET', body: String(init?.body) })
      return calls.length === 1 ? firstResponse.promise : Response.json({ id: 'shared-id', completed: true, completedAt: '2026-09-07T09:00:00Z' })
    },
  })
  const oldCommand = api.setHomeworkCompletion('shared-id', { completed: true })
  owner = 'B'
  firstResponse.resolve(Response.json({ title: 'Expired' }, { status: 401 }))
  await oldCommand
  const priorCommandSentForNewOwner = calls.some((call) => call.owner === 'B')
  console.log(JSON.stringify({ case: 'old-command-401-after-owner-change', calls, refreshCalls, priorCommandSentForNewOwner }))

  const originalFetch = globalThis.fetch
  const refreshResponse = deferred<Response>()
  let snapshotsCleared = 0
  try {
    globalThis.fetch = async (path) => {
      if (path === '/api/auth/refresh') return refreshResponse.promise
      if (path === '/api/auth/logout') return new Response(null, { status: 204 })
      throw new Error('Unexpected network request')
    }
    const auth = usePwaAuth()
    const pendingRefresh = auth.refresh()
    await auth.logout(async () => { snapshotsCleared++ })
    const clearedAfterLogout = auth.accessToken.value === null
    refreshResponse.resolve(Response.json({ accessToken: 'synthetic-old-session' }))
    await pendingRefresh
    const tokenRestoredAfterLogout = auth.accessToken.value !== null
    console.log(JSON.stringify({ case: 'pending-refresh-resolves-after-logout', clearedAfterLogout, snapshotsCleared, tokenRestoredAfterLogout }))
    if (!clearedAfterLogout || snapshotsCleared !== 1) throw new Error('Logout control failed')
    if (priorCommandSentForNewOwner || tokenRestoredAfterLogout) throw new Error('Session generation guards missing in the two reproduced boundaries')
  } finally { globalThis.fetch = originalFetch }
}
main().catch((error) => { console.error(error.message); process.exitCode = 1 })
