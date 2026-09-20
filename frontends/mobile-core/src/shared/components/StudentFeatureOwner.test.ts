import { createRenderer, h, nextTick, ref, type App, type PropType, type SetupContext } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { StudentApi } from '../../api/student-client'
import { CheckinCommandRecovery } from '../../domain/checkin'
import type { StudentCheckinAck, StudentCheckinCommand, StudentRequestOptions, StudentRequestPage, StudentToday, StudentHomework } from '../../api/types'
import StudentFeatureOwner from './StudentFeatureOwner.vue'
import { studentFeatureScopeIdentity, studentOfflineScopeKey, type StudentFeatureScope } from '../session-owner'

vi.mock('../../features/today/TodayScreen.vue', () => ({
  default: {
    name: 'OwnerTestTodayScreen',
    emits: ['navigate'],
    setup(_props: unknown, { emit }: SetupContext) {
      return () => h('button', {
        class: 'test-enter-more',
        type: 'button',
        onClick: () => emit('navigate', 'more'),
      }, 'Ещё')
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
    emits: ['newRequest'],
    setup(_props: unknown, { emit }: SetupContext) {
      return () => h('button', {
        class: 'requests-primary-action',
        type: 'button',
        onClick: () => emit('newRequest'),
      }, 'Подать')
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

vi.mock('../../features/today/use-today', () => ({
  useToday: () => ({
    query: { data: ref(null), error: ref(null), isPending: ref(false) },
    mutation: { isPending: ref(false), variables: ref(undefined), mutateAsync: vi.fn() },
  }),
}))

vi.mock('../../features/homework/use-homework', () => ({
  useHomework: () => ({
    query: { data: ref(null), error: ref(null), isPending: ref(false), refetch: vi.fn() },
    submitCompletion: vi.fn(),
    retryCompletion: vi.fn(() => null),
    isHistorical: ref(false),
    canLoadPrevious: ref(false),
    isPending: vi.fn(() => false),
    itemError: vi.fn(() => null),
    returnToToday: vi.fn(),
  }),
}))

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

async function settleOwnerTestRender(): Promise<void> {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

let mountedOwnerTestApps: App[] = []

afterEach(() => {
  for (const app of mountedOwnerTestApps) app.unmount()
  mountedOwnerTestApps = []
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

function mountOwnerTest(api: StudentApi): OwnerTestHostNode {
  const root = ownerTestElement('root')
  const app = ownerTestRenderer.createApp(StudentFeatureOwner, {
    api,
    scope,
    offline: false,
    readOnly: false,
    acquireCheckinCommand: async (): Promise<StudentCheckinCommand> => ({
      geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 },
    }),
    openMaterial: () => undefined,
  })
  app.mount(root)
  mountedOwnerTestApps.push(app)
  return root
}

describe('StudentFeatureOwner requests route', () => {
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
})
