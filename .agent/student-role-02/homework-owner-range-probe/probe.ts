import { createRenderer, ref, nextTick } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useHomework } from './src/features/homework/use-homework'
const renderer = createRenderer({
  patchProp() {}, insert() {}, remove() {}, createElement: () => ({}),
  createText: () => ({}), createComment: () => ({}), setText() {}, setElementText() {},
  parentNode: () => null, nextSibling: () => null, querySelector: () => null,
  setScopeId() {}, cloneNode: (node) => node, insertStaticContent: () => [{}, {}],
})
const scope = ref({ userId: 'A', activeRole: 'STUDENT', groupId: 'g', semesterId: 's', resetGeneration: 0 })
const oldRange = { from: '2026-09-01', to: '2026-09-07' }
const calls: { owner: string; from: string | null; to: string | null }[] = []
const api = {
  getHomework: async (from?: string, to?: string) => {
    calls.push({ owner: scope.value.userId, from: from ?? null, to: to ?? null })
    return { semester: { id: 's', name: 'Test', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
      from: from ?? '2026-09-14', to: to ?? '2026-09-20', serverNow: '2026-09-15T09:00:00Z', items: [] }
  },
}
let state: ReturnType<typeof useHomework>
const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
const app = renderer.createApp({ setup() { state = useHomework(api as never, scope, { initialRange: oldRange, retry: false }); return () => null } })
app.use(VueQueryPlugin, { queryClient: client })
app.mount({})
async function settle() { await nextTick(); await new Promise((done) => setTimeout(done, 0)); await nextTick() }
async function main() {
  try {
    await settle()
    if (calls.length !== 1 || calls[0]?.owner !== 'A' || calls[0]?.from !== oldRange.from) throw new Error('Historical owner A control failed')
    scope.value = { ...scope.value, userId: 'B', resetGeneration: 1 }
    await settle()
    const staleRangeForB = calls.some((call) => call.owner === 'B' && call.from === oldRange.from)
    console.log(JSON.stringify({ calls, finalRange: state.range.value, staleRangeForB, cache: client.getQueryCache().getAll().map((query) => ({ key: query.queryKey, dataRange: query.state.data ? { from: (query.state.data as any).from, to: (query.state.data as any).to } : null })) }))
    if (staleRangeForB) throw new Error('New owner must not request the previous owner historical range')
  } finally { app.unmount(); client.clear() }
}
main().catch((error) => { console.error(error.message); process.exitCode = 1 })
