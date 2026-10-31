import { createSSRApp, type Component, type Ref, type VNodeChild } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MapPage from './MapPage.vue'
import type {
  AdminMapBuildingResponse,
  AdminMapPlan,
  AdminMapPlanDeleteRequest,
  AdminMapPlanDeletionPreview,
  MapFormat,
} from '../../../../mobile-core/src/api/types'

const owner = vi.hoisted(() => ({
  retired: false,
  permissions: ['DATA_DELETE', 'CAMPUS_MAP_MANAGE'],
  fetcher: null as typeof fetch | null,
}))
vi.mock('../../app/live', () => ({ useWebLiveRefresh: () => {} }))
vi.mock('../../auth/session', () => ({ webSession: { generation: { value: 1 } } }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: () => {} }))
vi.mock('./use-admin', async () => {
  const { ref } = await import('vue')
  return {
    useAdmin: () => {
      const error = ref(''),
        busy = ref(false)
      const assertCurrent = () => {
        if (owner.retired) throw new Error('retired owner')
      }
      return {
        options: { accessToken: () => null, fetcher: owner.fetcher! },
        signal: new AbortController().signal,
        assertCurrent,
        loading: ref(false),
        busy,
        error,
        notice: ref(''),
        can: (permission: string) => owner.permissions.includes(permission),
        guardUnfinished: () => {},
        init: async () => {},
        refresh: async () => {},
        run: async (task: () => Promise<void>, permission?: string) => {
          if (busy.value) return
          busy.value = true
          try {
            assertCurrent()
            if (permission && !owner.permissions.includes(permission)) throw new Error('denied')
            await task()
          } catch (cause) {
            error.value = String(cause)
          } finally {
            busy.value = false
          }
        },
      }
    },
  }
})
type Reference = Omit<AdminMapPlanDeleteRequest, 'password'>
type State = {
  password: Ref<string>
  deleteFormat: Ref<MapFormat | ''>
  deletionPreview: Ref<AdminMapPlanDeletionPreview | null>
  permanentDeletionAvailable: Ref<boolean>
  unknownDeletion: Ref<Reference | null>
  inventory: Ref<readonly AdminMapBuildingResponse[]>
  open(row: {
    building: AdminMapBuildingResponse
    floor: AdminMapBuildingResponse['floors'][number]
  }): void
  previewDeletion(): Promise<void>
  removePlan(): Promise<void>
  inspectDeletion(): Promise<void>
}
type TestPage = { setup(props: object, context: { expose(): void }): State; render(): VNodeChild }
// Exercise the compiled private setup and rendered controls, as in review-sequences.test.ts.
async function runPage(run: (state: State) => Promise<void>) {
  const page = MapPage as Component as unknown as TestPage
  let state!: State
  const html = await renderToString(
    createSSRApp({
      async setup() {
        state = page.setup({}, { expose() {} })
        await run(state)
        return state
      },
      render: page.render,
    }),
  )
  return { state, html }
}
function building(plan: AdminMapPlan | null): AdminMapBuildingResponse {
  return {
    id: '10',
    code: '1',
    label: 'Корпус 1',
    floors: [
      {
        id: '20',
        buildingId: '10',
        code: '2',
        label: 'Этаж 2',
        currentPlan: plan,
        openCount: 0,
        openCountPeriod: 'all_time',
      },
    ],
  }
}
const replacement: AdminMapPlan = {
  buildingId: '10',
  floorId: '20',
  version: '4',
  catalogRevision: '12',
  label: 'Новая схема',
  png: {
    format: 'png',
    state: 'ready',
    contentType: 'image/png',
    id: 'new-png',
    bytes: 20,
    sha256: 'b'.repeat(64),
    width: 10,
    height: 10,
    viewBox: null,
  },
  svg: {
    format: 'svg',
    state: 'absent',
    contentType: 'image/svg+xml',
    id: null,
    bytes: 0,
    sha256: null,
    width: null,
    height: null,
    viewBox: null,
  },
}
function server(
  scope: AdminMapPlanDeletionPreview['deletionScope'],
  format: MapFormat | null = null,
) {
  const preview: AdminMapPlanDeletionPreview = {
    buildingCode: '1',
    floorCode: '2',
    format,
    label: 'Схема этажа',
    files: 6,
    bytes: 120,
    previewDigest: 'a'.repeat(64),
    ...(scope === undefined ? {} : { deletionScope: scope }),
  }
  const model = {
    plan: null as AdminMapPlan | null,
    posts: [] as AdminMapPlanDeleteRequest[],
    receipts: new Map<string, Reference>(),
    retireOnCompletion: false,
    outcome: 'complete' as 'complete' | 'lost' | 'network' | 'stale' | 'denied' | 'wrong-receipt',
  }
  owner.fetcher = async (path, init) => {
    if (String(path).endsWith('/buildings')) return Response.json([building(model.plan)])
    if (String(path).includes('/deletion-preview?')) return Response.json(preview)
    if (String(path).endsWith('/deletion') && init?.method === 'POST') {
      const command = JSON.parse(String(init.body)) as AdminMapPlanDeleteRequest
      model.posts.push(command)
      if (model.outcome === 'network') throw new TypeError('lost connection')
      if (model.outcome === 'stale') return Response.json({}, { status: 409 })
      if (model.outcome === 'denied') return Response.json({}, { status: 403 })
      const { password, ...reference } = command
      if (!password) return Response.json({}, { status: 403 })
      const receipt = model.receipts.get(command.operationId)
      if (receipt) expect(reference).toEqual(receipt)
      else {
        model.receipts.set(command.operationId, reference)
        model.plan = null
      }
      if (model.outcome === 'lost') throw new TypeError('receipt response lost')
      if (model.retireOnCompletion) owner.retired = true
      return Response.json({
        ...reference,
        format: command.format ?? null,
        status: 'COMPLETED',
        ...(model.outcome === 'wrong-receipt' ? { operationId: 'another-operation' } : {}),
      })
    }
    throw new Error(`Unexpected map read/write: ${String(path)}`)
  }
  return model
}
function select(state: State) {
  const value = building(null)
  state.open({ building: value, floor: value.floors[0]! })
}
beforeEach(() => {
  owner.retired = false
  owner.permissions = ['DATA_DELETE', 'CAMPUS_MAP_MANAGE']
})
describe('ADMIN map deletion intent and receipt', () => {
  it.each([undefined, null])(
    'keeps old-server preview readable but cannot delete with scope %s',
    async (scope) => {
      const model = server(scope)
      await runPage(async (state) => {
        select(state)
        await state.previewDeletion()
        expect(state.deletionPreview.value?.files).toBe(6)
        expect(state.permanentDeletionAvailable.value).toBe(false)
        state.password.value = 'synthetic-current-password'
        await state.removePlan()
        expect(state.unknownDeletion.value).toBeNull()
        expect(state.password.value).toBe('')
      })
      expect(model.posts).toEqual([])
    },
  )
  it('allows all-history preview and deletion for an orphan floor without a current plan', async () => {
    const model = server('ALL_VERSIONS')
    const { html } = await runPage(async (state) => {
      select(state)
      await state.previewDeletion()
      expect(state.permanentDeletionAvailable.value).toBe(true)
      state.password.value = 'synthetic-current-password'
    })
    expect(html).not.toMatch(/disabled[^>]*>\s*Показать последствия удаления/)
    await runPage(async (state) => {
      select(state)
      await state.previewDeletion()
      state.password.value = 'synthetic-current-password'
      await state.removePlan()
      expect(state.unknownDeletion.value).toBeNull()
      expect(state.password.value).toBe('')
    })
    expect(model.receipts.size).toBe(1)
    expect(model.posts[0]?.format).toBeUndefined()
  })
  it('preserves the explicit current-format variant and invalidates a preview when its selector changes', async () => {
    const model = server('CURRENT_FORMAT', 'png')
    await runPage(async (state) => {
      select(state)
      state.deleteFormat.value = 'png'
      await state.previewDeletion()
      expect(state.permanentDeletionAvailable.value).toBe(true)
      state.deleteFormat.value = 'svg'
      expect(state.permanentDeletionAvailable.value).toBe(false)
      state.deleteFormat.value = 'png'
      state.password.value = 'synthetic-current-password'
      await state.removePlan()
    })
    expect(model.posts[0]?.format).toBe('png')
  })
  it('requires explicit password reconciliation, reuses the original key and accepts a receipt while a newer version exists', async () => {
    const model = server('ALL_VERSIONS')
    model.outcome = 'lost'
    await runPage(async (state) => {
      select(state)
      await state.previewDeletion()
      state.password.value = 'synthetic-first-password'
      await state.removePlan()
      const original = structuredClone(state.unknownDeletion.value)
      expect(original).not.toBeNull()
      expect(state.password.value).toBe('')
      expect(model.receipts.size).toBe(1)
      expect(model.posts).toHaveLength(1)
      // Neither absent currentPlan nor a new preview/action resolves the lost receipt.
      await state.previewDeletion()
      await state.removePlan()
      await state.inspectDeletion()
      expect(state.unknownDeletion.value).toEqual(original)
      expect(model.posts).toHaveLength(1)
      model.plan = replacement
      model.outcome = 'complete'
      // Even changed local selectors cannot retarget the original operation.
      state.deleteFormat.value = 'svg'
      state.password.value = 'synthetic-reentered-password'
      await state.inspectDeletion()
      expect(state.unknownDeletion.value).toBeNull()
      expect(state.password.value).toBe('')
      expect(model.plan).toEqual(replacement)
      expect(state.inventory.value[0]?.floors[0]?.currentPlan).toEqual(replacement)
      const { password, ...reconciled } = model.posts[1]!
      expect(reconciled).toEqual(original)
      expect(password).toBe('synthetic-reentered-password')
    })
  })
  it.each(['network', 'stale', 'denied', 'wrong-receipt'] as const)(
    'keeps the same unknown reference after failed %s reconciliation',
    async (outcome) => {
      const model = server('ALL_VERSIONS')
      model.outcome = 'lost'
      await runPage(async (state) => {
        select(state)
        await state.previewDeletion()
        state.password.value = 'synthetic-current-password'
        await state.removePlan()
        const original = structuredClone(state.unknownDeletion.value)
        model.outcome = outcome
        state.password.value = 'synthetic-reentered-password'
        await state.inspectDeletion()
        expect(state.unknownDeletion.value).toEqual(original)
        expect(state.password.value).toBe('')
        expect(model.receipts.size).toBe(1)
      })
    },
  )
  it('rejects a completed response after the captured owner retires', async () => {
    const model = server('ALL_VERSIONS')
    model.outcome = 'lost'
    await runPage(async (state) => {
      select(state)
      await state.previewDeletion()
      state.password.value = 'synthetic-current-password'
      await state.removePlan()
      const original = structuredClone(state.unknownDeletion.value)
      model.outcome = 'complete'
      model.retireOnCompletion = true
      state.password.value = 'synthetic-reentered-password'
      await state.inspectDeletion()
      expect(state.unknownDeletion.value).toEqual(original)
      expect(state.password.value).toBe('')
      owner.retired = false
    })
  })
  it.each(['DATA_DELETE', 'CAMPUS_MAP_MANAGE'])(
    'requires the live %s grant during reconciliation',
    async (permission) => {
      const model = server('ALL_VERSIONS')
      model.outcome = 'lost'
      await runPage(async (state) => {
        select(state)
        await state.previewDeletion()
        state.password.value = 'synthetic-current-password'
        await state.removePlan()
        const original = structuredClone(state.unknownDeletion.value)
        owner.permissions = owner.permissions.filter((value) => value !== permission)
        state.password.value = 'synthetic-reentered-password'
        await state.inspectDeletion()
        expect(state.unknownDeletion.value).toEqual(original)
        expect(state.password.value).toBe('')
        expect(model.posts).toHaveLength(1)
      })
    },
  )
})
