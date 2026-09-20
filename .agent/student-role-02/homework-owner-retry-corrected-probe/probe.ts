import { createRenderer, ref, nextTick } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useHomework } from './src/features/homework/use-homework'

const renderer = createRenderer({
  patchProp() {}, insert() {}, remove() {}, createElement: () => ({}),
  createText: () => ({}), createComment: () => ({}), setText() {}, setElementText() {},
  parentNode: () => null, nextSibling: () => null, querySelector: () => null,
  setScopeId() {}, cloneNode: (node) => node, insertStaticContent: () => [{}, {}],
})
const a = { userId: 'student-a', activeRole: 'STUDENT', groupId: 'g', semesterId: 's', resetGeneration: 0 }
const scope = ref(a)
const calls: { userId: string; id: string; completed: boolean }[] = []
const feed = { semester: { id: 's', name: 'Test', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
  from: '2026-09-01', to: '2026-12-31', serverNow: '2026-09-07T09:00:00Z', items: [] }
const api = {
  getHomework: async () => feed,
  setHomeworkCompletion: async (id, value) => {
    calls.push({ userId: scope.value.userId, id, completed: value.completed })
    if (calls.length === 1) throw new Error('synthetic save failure')
    return { id, completed: value.completed, completedAt: '2026-09-07T09:00:00Z' }
  },
}
let state: ReturnType<typeof useHomework>
const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
const app = renderer.createApp({ setup() { state = useHomework(api as never, scope); return () => null } })
app.use(VueQueryPlugin, { queryClient: client })
app.mount({})
async function main() {
  try {
    try { await state.submitCompletion('shared-homework-id', true) } catch (error) {
      if (!(error instanceof Error) || error.message !== 'synthetic save failure') throw error
    }
    if (calls.length !== 1) throw new Error('Initial failure control failed')
    scope.value = { ...a, userId: 'student-b', resetGeneration: 1 }
    // This is intentionally the same tick, before the composable watcher flush.
    const retry = state.retryCompletion('shared-homework-id')
    let retryResult = retry === null ? 'null' : 'promise'
    if (retry) { try { await retry } catch { retryResult = 'rejected' } }
    await nextTick()
    console.log(JSON.stringify({ retryResult, calls }))
    if (calls.length !== 1) throw new Error('Prior owner retry must not issue a command for the new owner')
  } finally { app.unmount(); client.clear() }
}
main().catch((error) => { console.error(error.message); process.exitCode = 1 })
