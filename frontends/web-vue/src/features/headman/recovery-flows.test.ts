import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, nextTick, type Component } from 'vue'
import { HeadmanRequestsApi } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client'
import {
  HeadmanSubjectsApi,
  type HeadmanSubject,
} from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client'
import { subjectEditStorageKey } from '../../../../mobile-core/src/features/headman-subjects/subject-edit-intent'
import SchedulePage from './SchedulePage.vue'
import RequestsPage from './RequestsPage.vue'
import SubjectsPage from './SubjectsPage.vue'
const box = vi.hoisted(() => ({ feature: {} as Record<string, unknown>, writable: true }))
vi.mock('../../auth/session', () => ({
  webSession: {
    snapshot: { value: { userId: '3', sessionId: 'own-session' } },
    identity: { value: 'own-authority' },
  },
  message: (cause: unknown) => (cause instanceof Error ? cause.message : String(cause)),
}))
vi.mock('../../app/live', () => ({ useWebLiveRefresh: () => undefined }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('./HeadmanPage.vue', () => ({ default: { render: () => null } }))
vi.mock('./context', () => ({
  useHeadmanFeature: () => box.feature,
  today: () => '2026-10-24',
  lessonTypes: { LECTURE: 'Лекция' },
  markLabels: {},
  lessonTypeCode: (s: string) => s?.toUpperCase(),
  reasonLabel: (s: string) => s,
  saveBlob: () => undefined,
  dateLabel: (s: string) => s,
  addDays: (s: string, n: number) =>
    new Date(Date.parse(s + 'T12:00:00Z') + n * 86400000).toISOString().slice(0, 10),
}))
interface Host {
  parent: Host | null
}
const node = (): Host => ({ parent: null })
const renderer = createRenderer<Host, Host>({
  createElement: node,
  createText: node,
  createComment: node,
  insert: (child, parent) => {
    child.parent = parent
  },
  remove: () => undefined,
  setText: () => undefined,
  setElementText: () => undefined,
  patchProp: () => undefined,
  parentNode: (child) => child.parent,
  nextSibling: () => null,
})
let dispose: (() => void) | undefined
function mount<T>(component: Component): T {
  const app = renderer.createApp(component)
  app.config.warnHandler = () => undefined
  const vm = app.mount(node())
  dispose = () => app.unmount()
  return (vm.$ as unknown as { setupState: T }).setupState
}
class MemoryStorage implements Storage {
  values = new Map<string, string>()
  get length() {
    return this.values.size
  }
  clear() {
    this.values.clear()
  }
  getItem(k: string) {
    return this.values.get(k) ?? null
  }
  key(i: number) {
    return [...this.values.keys()][i] ?? null
  }
  removeItem(k: string) {
    this.values.delete(k)
  }
  setItem(k: string, v: string) {
    this.values.set(k, v)
  }
}
let storage: MemoryStorage
async function settle() {
  for (let i = 0; i < 15; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: Error) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const feature = (extra: Record<string, unknown>) => ({
  initialize: async () => undefined,
  assertCurrent: () => undefined,
  groupId: 1,
  isHeadman: true,
  allowed: () => box.writable,
  writable: () => box.writable,
  ...extra,
})
const semester = {
  id: 2,
  name: 'Текущий',
  active: true,
  dateFrom: '2026-10-23',
  dateTo: '2026-12-23',
}
beforeEach(() => {
  storage = new MemoryStorage()
  box.writable = true
  vi.stubGlobal('sessionStorage', storage)
  vi.stubGlobal('window', { confirm: () => true })
})
afterEach(() => {
  dispose?.()
  dispose = undefined
  vi.unstubAllGlobals()
})
interface ScheduleState {
  semesterId: number
  rows: { id: number; semesterId: number }[]
  assignments: { id: number; semesterId: number }[]
  loading: boolean
  error: string | null
  writable: boolean
  load(): Promise<void>
  read(): Promise<void>
}
describe('actual schedule semester reads', () => {
  it('discards a slow archive response after the active response', async () => {
    const oldRows = deferred<{ id: number; semesterId: number }[]>(),
      oldTeachers = deferred<{ id: number; semesterId: number }[]>()
    box.feature = feature({
      schedule: {
        listSemesters: async () => [{ ...semester, id: 1, active: false }, semester],
        lessonSlots: async () => ({ slots: [] }),
        academicWeekPolicy: async () => ({}),
        listScheduleItems: async (_g: number, id: number) =>
          id === 1 ? oldRows.promise : [{ id: 22, semesterId: 2 }],
        listAssignments: async (_g: number, id: number) =>
          id === 1 ? oldTeachers.promise : [{ id: 2, semesterId: 2 }],
      },
    })
    const state = mount<ScheduleState>(SchedulePage)
    await settle()
    state.semesterId = 1
    const slow = state.load()
    await settle()
    state.semesterId = 2
    await state.load()
    oldRows.resolve([{ id: 11, semesterId: 1 }])
    oldTeachers.resolve([{ id: 1, semesterId: 1 }])
    await slow
    expect(state.rows).toEqual([{ id: 22, semesterId: 2 }])
    expect(state.assignments).toEqual([{ id: 2, semesterId: 2 }])
    expect(state.loading).toBe(false)
    expect(state.writable).toBe(true)
  })
  it('also owns helper reads and ignores an obsolete error', async () => {
    const old = deferred<{ id: number; semesterId: number }[]>()
    const schedule = {
      listSemesters: async () => [semester],
      lessonSlots: async () => ({ slots: [] }),
      academicWeekPolicy: async () => ({}),
      listScheduleItems: async (_g: number, id: number) =>
        id === 1 ? old.promise : [{ id: 22, semesterId: 2 }],
      listAssignments: async () => [],
    }
    box.feature = feature({ schedule })
    const state = mount<ScheduleState>(SchedulePage)
    await settle()
    state.semesterId = 1
    const read = state.read()
    state.semesterId = 2
    await state.load()
    old.resolve([{ id: 11, semesterId: 1 }])
    await read
    expect(state.rows[0]?.semesterId).toBe(2)
    expect(state.loading).toBe(false)
    const failed = deferred<{ id: number; semesterId: number }[]>()
    schedule.listScheduleItems = async (_g, id) =>
      id === 1 ? failed.promise : [{ id: 22, semesterId: 2 }]
    state.semesterId = 1
    const slow = state.load()
    await settle()
    state.semesterId = 2
    await state.load()
    failed.reject(new Error('obsolete failure'))
    await slow
    expect(state.error).toBeNull()
    expect(state.loading).toBe(false)
    expect(state.rows[0]?.semesterId).toBe(2)
  })
})
const summary = {
  id: 'ticket-14',
  kind: 'EXCUSE',
  status: 'PENDING',
  studentId: 14,
  lessonCount: 1,
  alreadyMarkedCount: 0,
}
const occurrence = { lessonId: 63, occurrenceId: '44', occurrenceRevision: '1' }
interface RequestState {
  proposed: 'REJECTED'
  reason: string
  pending: unknown
  replayReady: unknown
  error: string | null
  open(id: string): Promise<void>
  decide(): Promise<void>
  reconcile(): Promise<void>
  sendPending(replay: boolean): Promise<void>
}
function requests(kind: 'EXCUSE' | 'LATE_CHECKIN' = 'EXCUSE', loseAcknowledgement = false) {
  const bodies: Record<string, unknown>[] = []
  let admitted = false,
    foreign = false
  const fetcher: typeof fetch = async (path, init) => {
    if (String(path).includes('?'))
      return Response.json({
        content: [{ ...summary, kind }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      })
    if (init?.method === 'POST') {
      bodies.push(JSON.parse(String(init.body)) as Record<string, unknown>)
      if (bodies.length === 1) throw new TypeError('not admitted')
      admitted = true
      if (loseAcknowledgement) throw new TypeError('committed response lost')
    }
    const body = bodies[0]
    return Response.json({
      summary: {
        ...summary,
        kind,
        decisionBy: foreign ? 99 : admitted ? 3 : null,
        status: admitted || foreign ? 'REJECTED' : 'PENDING',
        decisionComment: admitted || foreign ? 'Причина' : null,
      },
      lessons: [],
      attachments: [],
      currentAffectedLessons: [occurrence],
      decisionRevision: 'a'.repeat(64),
      decisionSelection:
        kind === 'EXCUSE' && (admitted || foreign)
          ? {
              requestKey: foreign ? 'other-command-key' : body?.requestKey,
              expectedRevision: body?.expectedRevision,
              includedLessons: [occurrence],
              excludedLessons: [],
            }
          : null,
    })
  }
  return {
    bodies,
    api: () => new HeadmanRequestsApi({ accessToken: () => null, fetcher }),
    foreign: () => {
      foreign = true
    },
  }
}
describe('actual persisted decision completion', () => {
  it('reads PENDING after reload, then explicitly sends the identical unadmitted command', async () => {
    const server = requests()
    box.feature = feature({ requests: server.api() })
    let state = mount<RequestState>(RequestsPage)
    await settle()
    await state.open('ticket-14')
    state.proposed = 'REJECTED'
    state.reason = 'Причина'
    await state.decide()
    expect(storage.length).toBe(1)
    dispose?.()
    box.feature = feature({ requests: server.api() })
    state = mount<RequestState>(RequestsPage)
    await settle()
    await state.reconcile()
    expect(state.replayReady).toBe(state.pending)
    await state.sendPending(true)
    expect(server.bodies).toHaveLength(2)
    expect(server.bodies[1]).toEqual(server.bodies[0])
    expect(state.pending).toBeNull()
    expect(storage.length).toBe(0)
  })
  it('continues a restored unadmitted late decision and reads its lost acknowledgement by actor', async () => {
    const server = requests('LATE_CHECKIN', true)
    box.feature = feature({ requests: server.api() })
    let state = mount<RequestState>(RequestsPage)
    await settle()
    await state.open('ticket-14')
    state.proposed = 'REJECTED'
    state.reason = 'Причина'
    await state.decide()
    expect(server.bodies).toHaveLength(1)
    dispose?.()
    box.feature = feature({ requests: server.api() })
    state = mount<RequestState>(RequestsPage)
    await settle()
    await state.reconcile()
    expect(state.replayReady).toBe(state.pending)
    await state.sendPending(true)
    expect(server.bodies).toHaveLength(2)
    expect(server.bodies[1]).toEqual(server.bodies[0])
    expect(server.bodies[1]).toEqual({ decision: 'REJECTED', reason: 'Причина' })
    expect(state.pending).not.toBeNull()
    dispose?.()
    box.feature = feature({ requests: server.api() })
    state = mount<RequestState>(RequestsPage)
    await settle()
    await state.reconcile()
    expect(state.pending).toBeNull()
    expect(storage.length).toBe(0)
    expect(server.bodies).toHaveLength(2)
    expect(state.error).toBeNull()
  })
  it('keeps another actor late acknowledgement distinct even with identical decision and reason', async () => {
    const server = requests('LATE_CHECKIN')
    box.feature = feature({ requests: server.api() })
    const state = mount<RequestState>(RequestsPage)
    await settle()
    await state.open('ticket-14')
    state.proposed = 'REJECTED'
    state.reason = 'Причина'
    await state.decide()
    await state.reconcile()
    expect(state.replayReady).toBe(state.pending)
    server.foreign()
    await state.sendPending(true)
    expect(server.bodies).toHaveLength(1)
    expect(state.pending).toBeNull()
    expect(state.error).toContain('другим решением')
  })
  it('checks current write authority and another receipt before replaying', async () => {
    const server = requests()
    box.feature = feature({ requests: server.api() })
    const state = mount<RequestState>(RequestsPage)
    await settle()
    await state.open('ticket-14')
    state.proposed = 'REJECTED'
    state.reason = 'Причина'
    await state.decide()
    await state.reconcile()
    box.writable = false
    await state.sendPending(true)
    expect(server.bodies).toHaveLength(1)
    expect(state.pending).not.toBeNull()
    box.writable = true
    await state.reconcile()
    server.foreign()
    await state.sendPending(true)
    expect(server.bodies).toHaveLength(1)
    expect(state.pending).toBeNull()
    expect(state.error).toContain('другим решением')
  })
})
const subject: HeadmanSubject = {
  id: 4,
  groupId: 1,
  name: 'Предмет',
  type: 'LECTURE',
  lessonTypes: ['LECTURE'],
  teacherIds: [2],
  assignments: [
    {
      id: 10,
      teacherId: 2,
      semesterId: 2,
      lessonType: 'LECTURE',
      validFrom: '2026-10-23',
      validUntilExclusive: null,
    },
  ],
}
interface SubjectState {
  selected: HeadmanSubject
  draft: { from: string; replacingId: number; teacherId: number }
  replacement: unknown
  locked: boolean
  error: string | null
  replace(recover?: boolean): Promise<void>
}
function subjectApi() {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
    Response.json({
      operationId: 'c3960e90-a0b6-4e8e-aed9-cb08c1155886',
      sourceAssignmentId: 10,
      targetAssignmentId: 11,
      sourceTeacherId: 2,
      targetTeacherId: 7,
      subjectId: 4,
      groupId: 1,
      semesterId: 2,
      lessonType: 'LECTURE',
      effectiveFrom: '2026-10-25',
      sourceValidUntilExclusive: '2026-10-25',
      targetValidUntilExclusive: null,
      state: 'COMMITTED',
      scheduleReceiptState: 'COMMITTED',
      movedCount: 0,
      skippedCount: 0,
    }),
  )
  const api = new HeadmanSubjectsApi({ accessToken: () => null, fetcher })
  vi.spyOn(api, 'listSemesters').mockResolvedValue([semester])
  vi.spyOn(api, 'listSubjects').mockResolvedValue([subject])
  vi.spyOn(api, 'resolveTeachers').mockResolvedValue([
    { id: 2, fullName: 'Первый', employeeNumber: null },
    { id: 7, fullName: 'Другой', employeeNumber: null },
  ])
  vi.spyOn(api, 'semesterProgress').mockResolvedValue({ semesterId: 2, generatedAt: '', items: [] })
  return { api, fetcher }
}
describe('actual replacement preflight', () => {
  it('keeps an invalid date editable without fetch and sends only after correction', async () => {
    const { api, fetcher } = subjectApi()
    box.feature = feature({ subjects: api })
    const state = mount<SubjectState>(SubjectsPage)
    await settle()
    state.selected = subject
    state.draft.replacingId = 10
    state.draft.teacherId = 7
    state.draft.from = ''
    await state.replace()
    expect(fetcher).not.toHaveBeenCalled()
    expect(storage.length).toBe(0)
    expect(state.locked).toBe(false)
    expect(state.replacement).toBeNull()
    state.draft.from = '2026-10-25'
    await state.replace()
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(state.replacement).toBeNull()
    expect(state.locked).toBe(false)
  })
  it('releases an old saved payload that typed preflight could never transmit', async () => {
    const { api, fetcher } = subjectApi()
    storage.setItem(
      subjectEditStorageKey('3', 1) + ':web-replacement',
      JSON.stringify({
        subjectId: 4,
        assignmentId: 10,
        input: {
          replacementTeacherId: '7',
          effectiveFrom: '',
          requestKey: 'c3960e90-a0b6-4e8e-aed9-cb08c1155886',
        },
        receipt: null,
      }),
    )
    box.feature = feature({ subjects: api })
    const state = mount<SubjectState>(SubjectsPage)
    await settle()
    expect(fetcher).not.toHaveBeenCalled()
    expect(state.replacement).toBeNull()
    expect(storage.length).toBe(0)
    expect(state.locked).toBe(false)
  })
  it('retains a syntactically valid unknown operation without giving it a new key', async () => {
    const { api, fetcher } = subjectApi()
    const key = subjectEditStorageKey('3', 1) + ':web-replacement'
    const original = JSON.stringify({
      subjectId: 4,
      assignmentId: 10,
      input: {
        replacementTeacherId: '7',
        effectiveFrom: '2026-10-25',
        requestKey: 'c3960e90-a0b6-4e8e-aed9-cb08c1155886',
      },
      receipt: null,
    })
    storage.setItem(key, original)
    box.feature = feature({ subjects: api })
    const state = mount<SubjectState>(SubjectsPage)
    await settle()
    expect(fetcher).not.toHaveBeenCalled()
    expect(state.replacement).not.toBeNull()
    expect(state.locked).toBe(true)
    expect(storage.getItem(key)).toBe(original)
  })
})
