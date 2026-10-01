import { createRenderer, h, nextTick, reactive, ref, type App, type PropType, type Ref, type SetupContext } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { StudentApi } from '../../api/student-client'
import { CheckinCommandRecovery } from '../../domain/checkin'
import type { StudentCheckinAck, StudentCheckinCommand, StudentRequestDetail, StudentRequestOptions, StudentRequestPage, StudentRequestSummary, StudentToday, StudentHomework } from '../../api/types'
import type { RequestFileRef } from '../../features/requests/types'
import StudentFeatureOwner from './StudentFeatureOwner.vue'
import { studentFeatureScopeIdentity, studentOfflineScopeKey, type StudentFeatureScope } from '../session-owner'

vi.mock('../../features/today/TodayScreen.vue', () => ({
  default: {
    name: 'OwnerTestTodayScreen',
    props: ['today', 'offline', 'readOnly'],
    emits: ['navigate'],
    setup(props: { today: StudentToday | null; offline: boolean; readOnly: boolean }, { emit }: SetupContext) {
      return () => h('section', [
        h('output', { class: 'test-today-data' }, JSON.stringify(props.today ?? null)),
        h('output', { class: 'test-today-readonly' }, String(props.readOnly)),
        h('button', {
          class: 'test-enter-more',
          type: 'button',
          onClick: () => emit('navigate', 'more'),
        }, 'Ещё'),
        h('button', { class: 'test-enter-homework', onClick: () => emit('navigate', 'homework') }, 'ДЗ'),
      ])
    },
  },
}))

vi.mock('../../features/homework/HomeworkScreen.vue', () => ({
  default: {
    props: ['homework', 'offline', 'readOnly'],
    emits: ['navigate'],
    setup(props: { homework: StudentHomework | null; offline: boolean; readOnly: boolean }, { emit }: SetupContext) {
      return () => h('section', [
        h('output', { class: 'test-homework-data' }, JSON.stringify(props.homework ?? null)),
        h('output', { class: 'test-homework-offline' }, String(props.offline)),
        h('button', { class: 'test-return-today', onClick: () => emit('navigate', 'today') }, 'Сегодня'),
      ])
    },
  },
}))

vi.mock('../../features/profile/MoreScreen.vue', () => ({
  default: {
    name: 'OwnerTestMoreScreen',
    props: {
      onNavigate: {
        type: Function as PropType<(route: 'requests') => void>,
        required: false,
        default: undefined,
      },
    },
    setup(props: { onNavigate?: (route: 'requests') => void }) {
      return () => h('button', {
        class: 'test-enter-requests',
        type: 'button',
        onClick: () => props.onNavigate?.('requests'),
      }, 'Заявки')
    },
  },
}))

vi.mock('../../features/requests/RequestsScreen.vue', () => ({
  default: {
    name: 'OwnerTestRequestsScreen',
    emits: ['newRequest', 'cancel'],
    setup(_props: unknown, { emit }: SetupContext) {
      return () => h('div', [
        h('button', {
          class: 'requests-primary-action',
          type: 'button',
          onClick: () => emit('newRequest'),
        }, 'Подать'),
        h('button', {
          class: 'test-cancel-request',
          type: 'button',
          onClick: () => emit('cancel', 'request-1'),
        }, 'Отменить'),
      ])
    },
  },
}))

vi.mock('../../features/requests/RequestTypeScreen.vue', () => ({
  default: {
    name: 'OwnerTestRequestTypeScreen',
    emits: ['back', 'choose'],
    setup(_props: unknown, { emit }: SetupContext) {
      return () => h('button', {
        class: 'test-choose-excuse',
        type: 'button',
        onClick: () => emit('choose', 'EXCUSE'),
      }, 'Уважительная причина')
    },
  },
}))

vi.mock('../../features/requests/ExcuseRequestScreen.vue', () => ({
  default: {
    name: 'OwnerTestExcuseRequestScreen',
    props: {
      files: { type: Array as PropType<RequestFileRef[]>, default: () => [] },
      lessonIds: { type: Array as PropType<string[]>, default: () => [] },
      reason: { type: String as PropType<string | null>, default: null },
    },
    emits: ['back', 'update:files', 'update:lesson-ids', 'update:reason'],
    setup(props: { files: RequestFileRef[]; lessonIds: string[]; reason: string | null }, { emit }: SetupContext) {
      function pickFile(): void {
        const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' })
        emit('update:files', [{ id: 'proof-file', name: file.name, size: file.size, type: file.type, file }])
      }

      return () => h('section', [
        h('output', { class: 'test-request-files' }, props.files.map((file) => file.name).join(',')),
        h('output', { class: 'test-request-lessons' }, props.lessonIds.join(',')),
        h('output', { class: 'test-request-reason' }, props.reason ?? ''),
        h('button', { class: 'test-pick-request-file', type: 'button', onClick: pickFile }, 'Добавить файл'),
        h('button', { class: 'test-remove-request-files', type: 'button', onClick: () => emit('update:files', []) }, 'Удалить файлы'),
        h('button', { class: 'test-select-request-lessons', type: 'button', onClick: () => emit('update:lesson-ids', ['lesson-1', 'lesson-2']) }, 'Выбрать пары'),
        h('button', { class: 'test-select-request-reason', type: 'button', onClick: () => emit('update:reason', 'OTHER') }, 'Выбрать причину'),
        h('button', { class: 'test-back-request-form', type: 'button', onClick: () => emit('back') }, 'Назад'),
      ])
    },
  },
}))

vi.mock('./MobileShell.vue', () => ({
  default: {
    name: 'OwnerTestMobileShell',
    setup(_props: unknown, { slots }: SetupContext) {
      return () => h('div', slots.default?.())
    },
  },
}))

const refreshToday = vi.hoisted(() => vi.fn())
const ownerQueryData = vi.hoisted(() => ({
  today: null as Ref<StudentToday | null> | null,
  homework: null as Ref<StudentHomework | null> | null,
  todayRefetch: vi.fn(() => Promise.resolve({ isSuccess: true })),
  homeworkRefetch: vi.fn(() => Promise.resolve({ isSuccess: true })),
  realReads: false,
}))

vi.mock('../../features/today/use-today', async () => {
  const actual = await vi.importActual<typeof import('../../features/today/use-today')>('../../features/today/use-today')
  return { ...actual, useToday: (...args: Parameters<typeof actual.useToday>) => ownerQueryData.realReads ? actual.useToday(...args) : ({
    query: { data: ownerQueryData.today = ref<StudentToday | null>(null), error: ref(null), isPending: ref(false), refetch: ownerQueryData.todayRefetch },
    mutation: { isPending: ref(false), variables: ref(undefined), mutateAsync: vi.fn() },
    refresh: refreshToday,
  }),
  }
})

vi.mock('../../features/homework/use-homework', async () => {
  const actual = await vi.importActual<typeof import('../../features/homework/use-homework')>('../../features/homework/use-homework')
  return { ...actual, useHomework: (...args: Parameters<typeof actual.useHomework>) => ownerQueryData.realReads ? actual.useHomework(...args) : ({
    query: { data: ownerQueryData.homework = ref<StudentHomework | null>(null), error: ref(null), isPending: ref(false), refetch: ownerQueryData.homeworkRefetch },
    range: ref(null),
    submitCompletion: vi.fn(),
    retryCompletion: vi.fn(() => null),
    isHistorical: ref(false),
    canLoadPrevious: ref(false),
    isPending: vi.fn(() => false),
    itemError: vi.fn(() => null),
    returnToToday: vi.fn(),
  }),
  }
})

type OwnerTestHostNode = {
  kind: 'element' | 'text' | 'comment'
  tag?: string
  text?: string
  props: Record<string, unknown>
  children: OwnerTestHostNode[]
  parent: OwnerTestHostNode | null
}

function ownerTestElement(tag: string): OwnerTestHostNode {
  return { kind: 'element', tag, props: {}, children: [], parent: null }
}

function insertOwnerTestNode(node: OwnerTestHostNode, parent: OwnerTestHostNode, anchor: OwnerTestHostNode | null): void {
  if (node.parent) {
    const previousIndex = node.parent.children.indexOf(node)
    if (previousIndex >= 0) node.parent.children.splice(previousIndex, 1)
  }
  node.parent = parent
  const index = anchor ? parent.children.indexOf(anchor) : -1
  if (index >= 0) parent.children.splice(index, 0, node)
  else parent.children.push(node)
}

const ownerTestRenderer = createRenderer<OwnerTestHostNode, OwnerTestHostNode>({
  patchProp(element, key, _previous, next) {
    if (next === null || next === undefined) delete element.props[key]
    else element.props[key] = next
  },
  insert(node, parent, anchor) {
    insertOwnerTestNode(node, parent, anchor ?? null)
  },
  remove(node) {
    if (!node.parent) return
    const index = node.parent.children.indexOf(node)
    if (index >= 0) node.parent.children.splice(index, 1)
    node.parent = null
  },
  createElement: ownerTestElement,
  createText(text) {
    return { kind: 'text', text, props: {}, children: [], parent: null }
  },
  createComment(text) {
    return { kind: 'comment', text, props: {}, children: [], parent: null }
  },
  setText(node, text) {
    node.text = text
  },
  setElementText(node, text) {
    for (const child of node.children) child.parent = null
    node.children = []
    node.text = text
  },
  parentNode(node) {
    return node.parent
  },
  nextSibling(node) {
    if (!node.parent) return null
    const index = node.parent.children.indexOf(node)
    return index >= 0 ? node.parent.children[index + 1] ?? null : null
  },
  querySelector() {
    return null
  },
  setScopeId() {
    // No host scope attributes are needed for this behavioral renderer.
  },
  cloneNode(node) {
    return node
  },
  insertStaticContent(content, parent, anchor) {
    const node: OwnerTestHostNode = { kind: 'text', text: content, props: {}, children: [], parent: null }
    insertOwnerTestNode(node, parent, anchor)
    return [node, node]
  },
})

function findOwnerTestNode(root: OwnerTestHostNode, predicate: (node: OwnerTestHostNode) => boolean): OwnerTestHostNode | undefined {
  if (predicate(root)) return root
  for (const child of root.children) {
    const match = findOwnerTestNode(child, predicate)
    if (match) return match
  }
  return undefined
}

function clickOwnerTestButton(root: OwnerTestHostNode, className: string): void {
  const node = findOwnerTestNode(root, (candidate) => candidate.kind === 'element' && candidate.props.class === className)
  if (!node) throw new Error(`Test button ${className} was not rendered`)
  const handler = node.props.onClick
  if (typeof handler !== 'function') throw new Error(`Test button ${className} has no click handler`)
  handler()
}

function ownerTestOutput(root: OwnerTestHostNode, className: string): string | undefined {
  return findOwnerTestNode(root, (candidate) => candidate.kind === 'element' && candidate.props.class === className)?.text
}

async function settleOwnerTestRender(): Promise<void> {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

let mountedOwnerTestApps: App[] = []
let ownerTestQueryClients: QueryClient[] = []

afterEach(() => {
  for (const app of mountedOwnerTestApps) app.unmount()
  mountedOwnerTestApps = []
  for (const client of ownerTestQueryClients) client.clear()
  ownerTestQueryClients = []
  refreshToday.mockReset()
  ownerQueryData.todayRefetch.mockReset().mockResolvedValue({ isSuccess: true })
  ownerQueryData.homeworkRefetch.mockReset().mockResolvedValue({ isSuccess: true })
  ownerQueryData.realReads = false
})

const command: StudentCheckinCommand = {
  geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 },
}
const ack: StudentCheckinAck = {
  outcome: 'PRESENT',
  lessonId: 'lesson-1',
  attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: '2026-09-08T08:30:00Z' },
  request: null,
  retryAt: null,
  serverNow: '2026-09-08T08:30:00Z',
  _links: {},
}
const scope: StudentFeatureScope = {
  userId: 'student-1',
  activeRole: 'STUDENT',
  groupId: 'group-1',
  semesterId: 'semester-1',
  sessionId: 'session-1',
  sessionVersion: '1',
  rolesVersion: '1',
  readOnly: false,
  resetGeneration: 4,
}

describe('StudentFeatureOwner lifecycle contract', () => {
  it('keeps the same command and idempotency key after a lost ACK and reconnect', async () => {
    const recovery = new CheckinCommandRecovery(() => 'same-key')
    const sent: Array<{ command: StudentCheckinCommand; key: string }> = []
    let lost = true

    const submit = async (attempt: { command: StudentCheckinCommand; key: string }): Promise<StudentCheckinAck> => {
      sent.push({ command: attempt.command, key: attempt.key })
      if (lost) throw new TypeError('response lost after commit')
      return ack
    }

    await expect(recovery.execute('lesson-1', async () => command, submit)).rejects.toThrow('response lost after commit')
    // A same-owner reconnect leaves the mounted owner/recovery instance alive.
    lost = false
    await expect(recovery.execute('lesson-1', async () => command, submit)).resolves.toEqual(ack)

    expect(sent).toEqual([
      { command, key: 'same-key' },
      { command, key: 'same-key' },
    ])
  })

  it('includes the authenticated generation in the owner identity', () => {
    const sameScope = { ...scope }
    const newOwner = { ...scope, resetGeneration: scope.resetGeneration + 1 }
    expect(studentFeatureScopeIdentity(sameScope)).toBe(studentFeatureScopeIdentity(scope))
    expect(studentFeatureScopeIdentity(newOwner)).not.toBe(studentFeatureScopeIdentity(scope))
  })

  it('includes server authority tuple changes while keeping the offline partition stable', () => {
    const renewed = { ...scope, sessionVersion: '5', rolesVersion: '2', readOnly: true }
    expect(studentFeatureScopeIdentity(renewed)).not.toBe(studentFeatureScopeIdentity(scope))
    expect(studentOfflineScopeKey(renewed)).toBe(studentOfflineScopeKey(scope))
  })
})

function requestOptions(): StudentRequestOptions {
  return {
    budget: { limit: 2, used: 0, remaining: 2, semesterId: 'semester-1' },
    files: { maxBytesPerFile: 10_000, maxBytesTotal: 10_000, maxFiles: 2, contentTypes: ['application/pdf'], extensions: ['.pdf'] },
    lessons: [{
      lesson: { id: 'lesson-1', lessonNumber: 1, status: 'OPEN', blocked: false },
      excuseEligible: true,
      lateCheckinEligible: true,
      pendingRequests: [],
    }],
    reasons: [{ code: 'OTHER', label: 'Другое', commentRequired: false }],
  }
}

function emptyRequestPage(): StudentRequestPage {
  return { content: [], page: 0, size: 10, totalElements: 0, totalPages: 1 }
}

function requestSummary(status: StudentRequestSummary['status']): StudentRequestSummary {
  return {
    id: 'request-1',
    kind: 'LATE_CHECKIN',
    origin: 'AUTO_GEO_FAILURE',
    status,
    lessons: [{ id: 'lesson-1', lessonNumber: 1, status: 'OPEN', blocked: false }],
    createdAt: '2026-09-30T08:00:00Z',
    updatedAt: '2026-09-30T08:00:00Z',
  }
}

function requestDetail(summary: StudentRequestSummary): StudentRequestDetail {
  return { summary, attachments: [], comment: null, decision: null, reason: null }
}

function requestPage(content: StudentRequestSummary[]): StudentRequestPage {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function mountOwnerTest(api: StudentApi, overrides: Record<string, unknown> = {}): OwnerTestHostNode {
  const root = ownerTestElement('root')
  const app = ownerTestRenderer.createApp({ render: () => h(StudentFeatureOwner, {
    api,
    scope,
    offline: false,
    readOnly: false,
    acquireCheckinCommand: async (): Promise<StudentCheckinCommand> => ({
      geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 },
    }),
    openMaterial: () => undefined,
    ...overrides,
  }) })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  app.use(VueQueryPlugin, { queryClient })
  ownerTestQueryClients.push(queryClient)
  app.mount(root)
  mountedOwnerTestApps.push(app)
  return root
}

describe('StudentFeatureOwner offline read models', () => {
  it('waits for real warm Vue Query GETs, keeps one read per feed, and accepts a later successful retry', async () => {
    ownerQueryData.realReads = true
    const warmToday = { serverNow: 'warm-today', lessons: [] } as unknown as StudentToday
    const warmHomework = { serverNow: 'warm-homework', from: '2026-09-08', to: '2026-09-09', semester: { dateFrom: '2026-09-01', dateTo: '2026-12-31' }, items: [] } as unknown as StudentHomework
    const getToday = vi.fn(() => Promise.resolve(warmToday))
    const getHomework = vi.fn(() => Promise.resolve(warmHomework))
    const props = reactive({ offline: false, readOnly: false, todayFallback: warmToday, homeworkFallback: warmHomework })
    const root = mountOwnerTest({ getToday, getHomework } as unknown as StudentApi, props)
    await vi.waitFor(() => expect(ownerTestOutput(root, 'test-today-data')).toContain('warm-today'))
    props.offline = true
    props.readOnly = true
    await settleOwnerTestRender()
    let resolveToday!: (today: StudentToday) => void
    let rejectHomework!: (error: Error) => void
    getToday.mockImplementation(() => new Promise((resolve) => { resolveToday = resolve }))
    getHomework.mockImplementation(() => new Promise((_resolve, reject) => { rejectHomework = reject }))
    props.offline = false
    props.readOnly = false
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toBe('null')
    clickOwnerTestButton(root, 'test-enter-homework')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')

    expect(getToday).toHaveBeenCalledTimes(2)
    expect(getHomework).toHaveBeenCalledTimes(2)
    resolveToday({ ...warmToday, serverNow: 'fresh-today' })
    // useHomework has one query retry; both failures must leave warm data hidden.
    getHomework.mockRejectedValue(new Error('offline again'))
    rejectHomework(new Error('offline again'))
    const queryClient = ownerTestQueryClients.at(-1)!
    await vi.waitFor(() => expect(queryClient.getQueryCache().find({ queryKey: ['student', 'homework'], exact: false })?.state.status).toBe('error'), { timeout: 2500 })
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')
    getHomework.mockResolvedValue({ ...warmHomework, serverNow: 'fresh-homework' })
    await queryClient.refetchQueries({ queryKey: ['student', 'homework'] })
    await vi.waitFor(() => expect(ownerTestOutput(root, 'test-homework-data')).toContain('fresh-homework'))
    clickOwnerTestButton(root, 'test-return-today')
    await vi.waitFor(() => expect(ownerTestOutput(root, 'test-today-data')).toContain('fresh-today'))
  })

  it('does not present warm Today/HW as fresh while the same owner reconnects', async () => {
    let finishToday!: (result: { isSuccess: boolean }) => void
    let finishHomework!: (result: { isSuccess: boolean }) => void
    ownerQueryData.todayRefetch.mockImplementation(() => new Promise((resolve) => { finishToday = resolve }))
    ownerQueryData.homeworkRefetch.mockImplementation(() => new Promise((resolve) => { finishHomework = resolve }))
    const props = reactive({ offline: false, readOnly: false })
    const root = mountOwnerTest({} as StudentApi, props)
    ownerQueryData.today!.value = { serverNow: 'warm-today', lessons: [] } as unknown as StudentToday
    ownerQueryData.homework!.value = { serverNow: 'warm-homework', items: [] } as unknown as StudentHomework
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toContain('warm-today')
    props.offline = true
    props.readOnly = true
    await settleOwnerTestRender()
    props.offline = false
    props.readOnly = false
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toBe('null')
    clickOwnerTestButton(root, 'test-enter-homework')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')

    const previousToday = finishToday
    const previousHomework = finishHomework
    props.offline = true
    await settleOwnerTestRender()
    props.offline = false
    await settleOwnerTestRender()
    previousToday({ isSuccess: true })
    previousHomework({ isSuccess: true })
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')

    ownerQueryData.today!.value = { serverNow: 'fresh-today', lessons: [] } as unknown as StudentToday
    ownerQueryData.homework!.value = { serverNow: 'fresh-homework', items: [] } as unknown as StudentHomework
    finishToday({ isSuccess: true })
    finishHomework({ isSuccess: true })
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toContain('fresh-homework')
    // Both queries were revalidated without replacing the owner or its state.
    expect(ownerQueryData.todayRefetch).toHaveBeenCalledTimes(2)
    expect(ownerQueryData.homeworkRefetch).toHaveBeenCalledTimes(2)
  })

  it('keeps saved Today/HW read-only offline and waits for fresh feeds after reconnect', async () => {
    const savedToday = { serverNow: 'saved-today', lessons: [] } as unknown as StudentToday
    const savedHomework = { serverNow: 'saved-homework', items: [] } as unknown as StudentHomework
    const props = reactive({ offline: true, readOnly: true, todayFallback: savedToday, homeworkFallback: savedHomework })
    const root = mountOwnerTest({} as StudentApi, props)
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toContain('saved-today')
    expect(ownerTestOutput(root, 'test-today-readonly')).toBe('true')

    props.offline = false
    props.readOnly = false
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toBe('null')
    ownerQueryData.today!.value = { serverNow: 'fresh-today', lessons: [] } as unknown as StudentToday
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toContain('fresh-today')

    clickOwnerTestButton(root, 'test-enter-homework')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')
    ownerQueryData.homework!.value = { serverNow: 'fresh-homework', items: [] } as unknown as StudentHomework
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toContain('fresh-homework')
    props.offline = true
    props.readOnly = true
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toContain('saved-homework')
    expect(ownerTestOutput(root, 'test-homework-offline')).toBe('true')
  })
})

describe('StudentFeatureOwner requests route', () => {
  it('refreshes Today after a successful local request cancellation', async () => {
    let cancelled = false
    const pending = requestSummary('PENDING')
    const cancelledDetail = requestDetail(requestSummary('CANCELLED'))
    const api = {
      getToday: vi.fn(() => Promise.resolve({} as StudentToday)),
      getHomework: vi.fn(() => Promise.resolve({} as StudentHomework)),
      listRequests: vi.fn((bucket: 'OPEN' | 'ARCHIVE') => Promise.resolve(bucket === 'OPEN' && !cancelled
        ? requestPage([pending])
        : bucket === 'ARCHIVE' && cancelled
          ? requestPage([cancelledDetail.summary])
          : emptyRequestPage())),
      getRequest: vi.fn(() => Promise.resolve(cancelled ? cancelledDetail : requestDetail(pending))),
      cancelRequest: vi.fn(async () => {
        cancelled = true
        return cancelledDetail
      }),
      getRequestOptions: vi.fn(() => Promise.resolve(requestOptions())),
      submitExcuse: vi.fn(),
      submitLateCheckin: vi.fn(),
      downloadRequestAttachment: vi.fn(),
    } as unknown as StudentApi
    const root = mountOwnerTest(api)

    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-more')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-requests')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-cancel-request')
    await vi.waitFor(() => expect(refreshToday).toHaveBeenCalledTimes(1))

    expect(api.cancelRequest).toHaveBeenCalledWith('request-1')
    expect(refreshToday).toHaveBeenCalledWith(scope)
  })

  it('loads request options once when the real owner opens a request form', async () => {
    let resolveOptions!: (value: StudentRequestOptions) => void
    const pendingOptions = new Promise<StudentRequestOptions>((resolve) => { resolveOptions = resolve })
    const getRequestOptions = vi.fn(() => pendingOptions)
    const api = {
      getToday: vi.fn(() => Promise.resolve({} as StudentToday)),
      getHomework: vi.fn(() => Promise.resolve({} as StudentHomework)),
      listRequests: vi.fn(() => Promise.resolve(emptyRequestPage())),
      getRequest: vi.fn(),
      getRequestOptions,
      submitExcuse: vi.fn(),
      submitLateCheckin: vi.fn(),
      cancelRequest: vi.fn(),
      downloadRequestAttachment: vi.fn(),
    } as unknown as StudentApi
    const root = mountOwnerTest(api)

    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-more')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-requests')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'requests-primary-action')
    await settleOwnerTestRender()

    expect(getRequestOptions).toHaveBeenCalledOnce()

    resolveOptions(requestOptions())
    await pendingOptions
    await settleOwnerTestRender()
    expect(getRequestOptions).toHaveBeenCalledOnce()
  })

  it('publishes request draft edits and preserves the latest file, pairs, and reason across form navigation', async () => {
    const api = {
      getToday: vi.fn(() => Promise.resolve({} as StudentToday)),
      getHomework: vi.fn(() => Promise.resolve({} as StudentHomework)),
      listRequests: vi.fn(() => Promise.resolve(emptyRequestPage())),
      getRequest: vi.fn(),
      getRequestOptions: vi.fn(() => Promise.resolve(requestOptions())),
      submitExcuse: vi.fn(),
      submitLateCheckin: vi.fn(),
      cancelRequest: vi.fn(),
      downloadRequestAttachment: vi.fn(),
    } as unknown as StudentApi
    const root = mountOwnerTest(api)

    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-more')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-requests')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'requests-primary-action')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-choose-excuse')
    await settleOwnerTestRender()

    clickOwnerTestButton(root, 'test-pick-request-file')
    clickOwnerTestButton(root, 'test-select-request-lessons')
    clickOwnerTestButton(root, 'test-select-request-reason')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    expect(ownerTestOutput(root, 'test-request-reason')).toBe('OTHER')

    clickOwnerTestButton(root, 'test-back-request-form')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-choose-excuse')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    expect(ownerTestOutput(root, 'test-request-reason')).toBe('OTHER')

    clickOwnerTestButton(root, 'test-remove-request-files')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('')
    clickOwnerTestButton(root, 'test-back-request-form')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-choose-excuse')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('')

    clickOwnerTestButton(root, 'test-pick-request-file')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-back-request-form')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-choose-excuse')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    expect(ownerTestOutput(root, 'test-request-reason')).toBe('OTHER')
  })
})
