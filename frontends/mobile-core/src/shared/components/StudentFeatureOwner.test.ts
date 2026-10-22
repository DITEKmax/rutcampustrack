import {
  createRenderer,
  h,
  nextTick,
  reactive,
  ref,
  type App,
  type PropType,
  type Ref,
  type SetupContext,
} from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { MobileBottomNavItems, MobileNavigationStack } from '../navigation'
import type { StudentApi } from '../../api/student-client'
import type { NotificationTargetIntent } from '../../features/notifications/notifications-client'
import { CheckinCommandRecovery } from '../../domain/checkin'
import type {
  StudentCheckinAck,
  StudentCheckinCommand,
  StudentRequestDetail,
  StudentRequestOptions,
  StudentRequestPage,
  StudentRequestSummary,
  StudentToday,
  StudentHomework,
} from '../../api/types'
import type { RequestFileRef, RequestLessonOption } from '../../features/requests/types'
import StudentFeatureOwner from './StudentFeatureOwner.vue'
import {
  studentFeatureScopeIdentity,
  studentOfflineScopeKey,
  type StudentFeatureScope,
} from '../session-owner'

vi.mock('../../features/today/TodayScreen.vue', () => ({
  default: {
    name: 'OwnerTestTodayScreen',
    props: ['today', 'offline', 'readOnly', 'acquiringLessonId', 'checkinError'],
    emits: ['navigate', 'checkin', 'toggleActions', 'openRequest', 'retry'],
    setup(
      props: {
        today: StudentToday | null
        offline: boolean
        readOnly: boolean
        acquiringLessonId: string | null
        checkinError: string | null
      },
      { emit }: SetupContext,
    ) {
      return () =>
        h('section', [
          h(
            'button',
            { class: 'test-retry-connection', onClick: () => emit('retry') },
            'Перезагрузить',
          ),
          h('button', { class: 'test-enter-profile', onClick: () => emit('navigate', 'profile') }, 'Профиль'),
          h('output', { class: 'test-today-data' }, JSON.stringify(props.today ?? null)),
          h('output', { class: 'test-today-readonly' }, String(props.readOnly)),
          h('output', { class: 'test-acquiring' }, props.acquiringLessonId ?? ''),
          h('output', { class: 'test-checkin-error' }, props.checkinError ?? ''),
          h(
            'button',
            {
              class: 'test-toggle-today-actions',
              onClick: () => emit('toggleActions', props.today?.lessons[0]),
            },
            'Действия',
          ),
          h(
            'button',
            {
              class: 'test-open-today-excuse',
              onClick: () => emit('openRequest', props.today?.lessons[0], 'EXCUSE'),
            },
            'Уважительная причина',
          ),
          h(
            'button',
            {
              class: 'test-open-today-late',
              onClick: () => emit('openRequest', props.today?.lessons[0], 'LATE_CHECKIN'),
            },
            'Забыл отметиться',
          ),
          h(
            'button',
            { class: 'test-checkin', onClick: () => emit('checkin', props.today?.lessons[0]) },
            'Отметиться',
          ),
          h(
            'button',
            {
              class: 'test-enter-more',
              type: 'button',
              onClick: () => emit('navigate', 'more'),
            },
            'Ещё',
          ),
          h(
            'button',
            { class: 'test-enter-homework', onClick: () => emit('navigate', 'homework') },
            'ДЗ',
          ),
        ])
    },
  },
}))

vi.mock('../../features/homework/HomeworkScreen.vue', () => ({
  default: {
    props: ['homework', 'offline', 'readOnly', 'periodMode', 'selectedDate'],
    emits: ['navigate', 'selectPeriod', 'selectDate', 'stepPeriod', 'returnToday', 'complete'],
    setup(
      props: {
        homework: StudentHomework | null
        offline: boolean
        readOnly: boolean
        periodMode: string
        selectedDate: string | null
      },
      { emit }: SetupContext,
    ) {
      return () =>
        h('section', [
          h('output', { class: 'test-homework-data' }, JSON.stringify(props.homework ?? null)),
          h('output', { class: 'test-homework-offline' }, String(props.offline)),
          h(
            'button',
            {
              class: 'test-homework-complete',
              onClick: () => emit('complete', props.homework?.items[0], true),
            },
            'Выполнить',
          ),
          h(
            'output',
            { class: 'test-homework-period' },
            `${props.periodMode}:${props.selectedDate ?? ''}`,
          ),
          h(
            'button',
            { class: 'test-homework-month', onClick: () => emit('selectPeriod', 'month') },
            'Месяц',
          ),
          h(
            'button',
            { class: 'test-homework-next', onClick: () => emit('stepPeriod', 1) },
            'Следующий период',
          ),
          h(
            'button',
            { class: 'test-homework-day', onClick: () => emit('selectPeriod', 'day') },
            'День',
          ),
          h(
            'button',
            { class: 'test-homework-select-day', onClick: () => emit('selectDate', '2026-09-20') },
            '20 сентября',
          ),
          h(
            'button',
            { class: 'test-homework-current', onClick: () => emit('returnToday') },
            'К текущим заданиям',
          ),
          h(
            'button',
            { class: 'test-return-today', onClick: () => emit('navigate', 'today') },
            'Сегодня',
          ),
        ])
    },
  },
}))

vi.mock('../../features/profile/MoreScreen.vue', () => ({
  default: {
    name: 'OwnerTestMoreScreen',
    props: {
      onNavigate: {
        type: Function as PropType<(route: 'requests' | 'statistics') => void>,
        required: false,
        default: undefined,
      },
    },
    setup(props: { onNavigate?: (route: 'requests' | 'statistics') => void }) {
      return () =>
        h('section', [
          h(
            'button',
            {
              class: 'test-enter-requests',
              type: 'button',
              onClick: () => props.onNavigate?.('requests'),
            },
            'Заявки',
          ),
          h(
            'button',
            { class: 'test-enter-statistics', onClick: () => props.onNavigate?.('statistics') },
            'Статистика',
          ),
        ])
    },
  },
}))

vi.mock('../../features/statistics/StatisticsScreen.vue', () => ({
  default: {
    props: ['selectedSubjectId'],
    emits: ['open-subject', 'back'],
    setup(props: { selectedSubjectId: string | null }, { emit }: SetupContext) {
      return () =>
        h('section', [
          h('output', { class: 'test-statistics-subject' }, props.selectedSubjectId ?? 'overview'),
          h(
            'button',
            {
              class: 'test-open-statistics-subject',
              onClick: () => emit('open-subject', 'subject-1'),
            },
            'Предмет',
          ),
          h('button', { class: 'test-back-statistics', onClick: () => emit('back') }, 'Назад'),
        ])
    },
  },
}))

vi.mock('../../features/requests/RequestsScreen.vue', () => ({
  default: {
    name: 'OwnerTestRequestsScreen',
    props: ['requests'],
    emits: ['newRequest', 'cancel'],
    setup(props: { requests: readonly { summary: { id: string } }[] }, { emit }: SetupContext) {
      return () =>
        h('div', [
          h(
            'output',
            { class: 'test-request-ids' },
            props.requests.map((request) => request.summary.id).join(','),
          ),
          h(
            'button',
            {
              class: 'requests-primary-action',
              type: 'button',
              onClick: () => emit('newRequest'),
            },
            'Подать',
          ),
          h(
            'button',
            {
              class: 'test-cancel-request',
              type: 'button',
              onClick: () => emit('cancel', 'request-1'),
            },
            'Отменить',
          ),
        ])
    },
  },
}))

vi.mock('../../features/requests/RequestTypeScreen.vue', () => ({
  default: {
    name: 'OwnerTestRequestTypeScreen',
    emits: ['back', 'choose'],
    setup(_props: unknown, { emit }: SetupContext) {
      return () =>
        h(
          'button',
          {
            class: 'test-choose-excuse',
            type: 'button',
            onClick: () => emit('choose', 'EXCUSE'),
          },
          'Уважительная причина',
        )
    },
  },
}))

vi.mock('../../features/requests/RequestLessonSelectionScreen.vue', () => ({
  default: {
    props: ['lessonIds', 'lessons'],
    emits: ['back', 'continue', 'update:lessonIds'],
    setup(
      props: { lessonIds: string[]; lessons: readonly RequestLessonOption[] },
      { emit }: SetupContext,
    ) {
      return () =>
        h('section', [
          h('output', { class: 'test-selection-lessons' }, props.lessonIds.join(',')),
          h(
            'output',
            { class: 'test-selection-options' },
            props.lessons.map((option) => option.lesson?.id).join(','),
          ),
          h(
            'button',
            {
              class: 'test-select-request-lessons',
              onClick: () => emit('update:lessonIds', ['lesson-1', 'lesson-2']),
            },
            'Выбрать пары',
          ),
          h(
            'button',
            {
              class: 'test-invalid-request-lessons',
              onClick: () => emit('update:lessonIds', ['unknown']),
            },
            'Недоступная пара',
          ),
          h(
            'button',
            { class: 'test-continue-selection', onClick: () => emit('continue') },
            'Продолжить',
          ),
          h(
            'button',
            { class: 'test-back-request-selection', onClick: () => emit('back') },
            'Назад',
          ),
        ])
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
      submitError: { type: String as PropType<string | null>, default: null },
      ambiguous: { type: Boolean, default: false },
    },
    emits: [
      'back',
      'update:files',
      'update:lesson-ids',
      'update:reason',
      'update:comment',
      'submit',
      'abandon',
    ],
    setup(
      props: {
        files: RequestFileRef[]
        lessonIds: string[]
        reason: string | null
        submitError: string | null
        ambiguous: boolean
      },
      { emit }: SetupContext,
    ) {
      function pickFile(): void {
        const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' })
        emit('update:files', [
          { id: 'proof-file', name: file.name, size: file.size, type: file.type, file },
        ])
      }

      return () =>
        h('section', [
          h(
            'output',
            { class: 'test-request-files' },
            props.files.map((file) => file.name).join(','),
          ),
          h('output', { class: 'test-request-lessons' }, props.lessonIds.join(',')),
          h('output', { class: 'test-request-reason' }, props.reason ?? ''),
          h('output', { class: 'test-request-submit-error' }, props.submitError ?? ''),
          h(
            'button',
            { class: 'test-clear-request-reason', onClick: () => emit('update:reason', null) },
            'Убрать причину',
          ),
          h(
            'button',
            {
              class: 'test-edit-request-comment',
              onClick: () => emit('update:comment', 'Изменённый комментарий'),
            },
            'Изменить комментарий',
          ),
          h('output', { class: 'test-request-ambiguous' }, String(props.ambiguous)),
          h(
            'button',
            {
              class: 'test-submit-request',
              disabled: props.ambiguous,
              onClick: () =>
                emit('submit', {
                  lessonIds: props.lessonIds,
                  reason: props.reason,
                  comment: 'Есть пояснение',
                  files: props.files,
                }),
            },
            'Отправить заявку',
          ),
          props.ambiguous
            ? h(
                'button',
                { class: 'test-abandon-request', onClick: () => emit('abandon') },
                'Отказаться от неподтверждённой отправки',
              )
            : null,
          h(
            'button',
            { class: 'test-pick-request-file', type: 'button', onClick: pickFile },
            'Добавить файл',
          ),
          h(
            'button',
            {
              class: 'test-remove-request-files',
              type: 'button',
              onClick: () => emit('update:files', []),
            },
            'Удалить файлы',
          ),
          h(
            'button',
            {
              class: 'test-select-request-lessons',
              type: 'button',
              onClick: () => emit('update:lesson-ids', ['lesson-1', 'lesson-2']),
            },
            'Выбрать пары',
          ),
          h(
            'button',
            {
              class: 'test-select-request-reason',
              type: 'button',
              onClick: () => emit('update:reason', 'OTHER'),
            },
            'Выбрать причину',
          ),
          h(
            'button',
            { class: 'test-back-request-form', type: 'button', onClick: () => emit('back') },
            'Назад',
          ),
        ])
    },
  },
}))

vi.mock('../../features/requests/LateCheckinRequestScreen.vue', () => ({
  default: {
    props: ['lessonId'],
    setup(props: { lessonId: string | null }) {
      return () => h('output', { class: 'test-late-request-lesson' }, props.lessonId ?? '')
    },
  },
}))

vi.mock('./MobileShell.vue', async () => {
  const actual = await vi.importActual<typeof import('./MobileShell.vue')>('./MobileShell.vue')
  return {
    default: {
      name: 'OwnerTestMobileShell',
      setup(_props: unknown, { attrs, slots }: SetupContext) {
        return () =>
          ownerQueryData.realShell
            ? h(
                actual.default,
                {
                  ...attrs,
                  navItems: (attrs['nav-items'] ?? attrs.navItems) as MobileBottomNavItems,
                },
                slots,
              )
            : h('div', [
                h(
                  'button',
                  {
                    class: 'test-owner-today',
                    onClick: () => (attrs.navigation as MobileNavigationStack).goRoot('today'),
                  },
                  'Сегодня',
                ),
                h('button', { class: 'test-owner-profile', onClick: () => (attrs.navigation as MobileNavigationStack).goRoot('profile') }, 'Профиль'),
                slots.default?.(),
              ])
      },
    },
  }
})

const refreshToday = vi.hoisted(() => vi.fn())
const checkinSubmit = vi.hoisted(() => vi.fn())
const ownerQueryData = vi.hoisted(() => ({
  today: null as Ref<StudentToday | null> | null,
  homework: null as Ref<StudentHomework | null> | null,
  todayRefetch: vi.fn(() => Promise.resolve({ isSuccess: true })),
  homeworkRefetch: vi.fn(() => Promise.resolve({ isSuccess: true })),
  realReads: false,
  realShell: false,
}))

vi.mock('../../features/today/use-today', async () => {
  const actual = await vi.importActual<typeof import('../../features/today/use-today')>(
    '../../features/today/use-today',
  )
  return {
    ...actual,
    useToday: (...args: Parameters<typeof actual.useToday>) =>
      ownerQueryData.realReads
        ? actual.useToday(...args)
        : {
            query: {
              data: (ownerQueryData.today = ref<StudentToday | null>(null)),
              error: ref(null),
              isPending: ref(false),
              refetch: ownerQueryData.todayRefetch,
            },
            mutation: {
              isPending: ref(false),
              variables: ref(undefined),
              mutateAsync: checkinSubmit,
            },
            refresh: refreshToday,
          },
  }
})

vi.mock('../../features/homework/use-homework', async () => {
  const actual = await vi.importActual<typeof import('../../features/homework/use-homework')>(
    '../../features/homework/use-homework',
  )
  return {
    ...actual,
    useHomework: (...args: Parameters<typeof actual.useHomework>) =>
      ownerQueryData.realReads
        ? actual.useHomework(...args)
        : {
            query: {
              data: (ownerQueryData.homework = ref<StudentHomework | null>(null)),
              error: ref(null),
              isPending: ref(false),
              refetch: ownerQueryData.homeworkRefetch,
            },
            range: ref(null),
            periodMode: ref('day'),
            selectedDate: ref(null),
            periodRange: ref(null),
            periodSemester: ref(null),
            todayDate: ref(null),
            selectPeriod: vi.fn(),
            selectDate: vi.fn(),
            stepPeriod: vi.fn(),
            submitCompletion: vi.fn(),
            retryCompletion: vi.fn(() => null),
            isHistorical: ref(false),
            canLoadPrevious: ref(false),
            isPending: vi.fn(() => false),
            itemError: vi.fn(() => null),
            presentHomework: (feed: StudentHomework | null) => feed,
            returnToToday: vi.fn(),
          },
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

function insertOwnerTestNode(
  node: OwnerTestHostNode,
  parent: OwnerTestHostNode,
  anchor: OwnerTestHostNode | null,
): void {
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
    return index >= 0 ? (node.parent.children[index + 1] ?? null) : null
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
    const node: OwnerTestHostNode = {
      kind: 'text',
      text: content,
      props: {},
      children: [],
      parent: null,
    }
    insertOwnerTestNode(node, parent, anchor)
    return [node, node]
  },
})

function findOwnerTestNode(
  root: OwnerTestHostNode,
  predicate: (node: OwnerTestHostNode) => boolean,
): OwnerTestHostNode | undefined {
  if (predicate(root)) return root
  for (const child of root.children) {
    const match = findOwnerTestNode(child, predicate)
    if (match) return match
  }
  return undefined
}

function clickOwnerTestButton(root: OwnerTestHostNode, className: string): void {
  const node = findOwnerTestNode(
    root,
    (candidate) => candidate.kind === 'element' && candidate.props.class === className,
  )
  if (!node) throw new Error(`Test button ${className} was not rendered`)
  const handler = node.props.onClick
  if (typeof handler !== 'function')
    throw new Error(`Test button ${className} has no click handler`)
  handler()
}

function ownerTestOutput(root: OwnerTestHostNode, className: string): string | undefined {
  return findOwnerTestNode(
    root,
    (candidate) => candidate.kind === 'element' && candidate.props.class === className,
  )?.text
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
  checkinSubmit.mockReset()
  ownerQueryData.todayRefetch.mockReset().mockResolvedValue({ isSuccess: true })
  ownerQueryData.homeworkRefetch.mockReset().mockResolvedValue({ isSuccess: true })
  ownerQueryData.realReads = false
  ownerQueryData.realShell = false
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
    files: {
      maxBytesPerFile: 10_000,
      maxBytesTotal: 10_000,
      maxFiles: 2,
      contentTypes: ['application/pdf'],
      extensions: ['.pdf'],
    },
    lessons: [
      {
        lesson: { id: 'lesson-1', lessonNumber: 1, status: 'OPEN', blocked: false },
        excuseEligible: true,
        lateCheckinEligible: true,
        pendingRequests: [],
      },
    ],
    reasons: [{ code: 'OTHER', label: 'Другое', commentRequired: false }],
  }
}

function multiRequestOptions(): StudentRequestOptions {
  const options = requestOptions()
  const first = options.lessons[0]!
  return {
    ...options,
    lessons: [first, { ...first, lesson: { ...first.lesson!, id: 'lesson-2' } }],
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

function mountOwnerTest(
  api: StudentApi,
  overrides: Record<string, unknown> = {},
): OwnerTestHostNode {
  const root = ownerTestElement('root')
  const app = ownerTestRenderer.createApp({
    render: () =>
      h(StudentFeatureOwner, {
        api,
        scope,
        offline: false,
        readOnly: false,
        acquireCheckinCommand: async (): Promise<StudentCheckinCommand> => ({
          geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 },
        }),
        openMaterial: () => undefined,
        ...overrides,
      }),
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  app.use(VueQueryPlugin, { queryClient })
  ownerTestQueryClients.push(queryClient)
  app.mount(root)
  mountedOwnerTestApps.push(app)
  return root
}

describe('StudentFeatureOwner offline read models', () => {
  it.each([false, true])(
    'presents pending completion and rollback in the real owner view (notification=%s)',
    async (notification) => {
      ownerQueryData.realReads = true
      const item = {
        id: 'notice',
        title: 'Задание',
        description: '',
        link: null,
        lessonDate: '2026-09-07',
        lessonNumber: 1,
        bindingMode: 'LESSON' as const,
        archived: false,
        subject: { id: 'subject', name: 'Предмет' },
        completed: false,
        completedAt: null,
      }
      const feed = (from = '2026-09-07', to = '2026-12-31'): StudentHomework => ({
        semester: { id: 'semester-1', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
        from,
        to,
        serverNow: '2026-09-07T08:00:00Z',
        items: [item],
      })
      let reject!: (error: Error) => void
      const response = new Promise((_, no) => {
        reject = no
      })
      const onOwnerError = vi.fn()
      const props = reactive<{
        ownerKey: string
        onOwnerError: typeof onOwnerError
        notificationTargetIntent: NotificationTargetIntent | null
      }>({ ownerKey: 'owner-A', onOwnerError, notificationTargetIntent: null })
      const root = mountOwnerTest(
        {
          getToday: async () => ({ lessons: [] }),
          getHomework: async (from?: string, to?: string) => feed(from, to),
          getRequestOptions: async () => requestOptions(),
          setHomeworkCompletion: () => response,
        } as unknown as StudentApi,
        props,
      )
      clickOwnerTestButton(root, 'test-enter-homework')
      await vi.waitFor(() =>
        expect(
          JSON.parse(ownerTestOutput(root, 'test-homework-data') ?? 'null')?.items[0]?.completed,
        ).toBe(false),
      )
      if (notification) {
        props.notificationTargetIntent = {
          requestId: 7,
          generation: scope.resetGeneration,
          ownerKey: 'owner-A',
          target: { kind: 'homework', homeworkId: item.id, lessonDate: item.lessonDate },
        }
        await vi.waitFor(() => {
          const data = JSON.parse(ownerTestOutput(root, 'test-homework-data') ?? 'null')
          expect(data?.to).toBe(item.lessonDate)
          expect(data?.items[0]?.completed).toBe(false)
        })
      }
      clickOwnerTestButton(root, 'test-homework-complete')
      await vi.waitFor(() =>
        expect(
          JSON.parse(ownerTestOutput(root, 'test-homework-data') ?? 'null')?.items[0],
        ).toMatchObject({
          completed: true,
          completedAt: null,
        }),
      )
      expect(item.completed).toBe(false)
      const cache = ownerTestQueryClients
        .at(-1)!
        .getQueriesData<StudentHomework>({ queryKey: ['student', 'homework'] })
      expect(
        cache.every(([, data]) => !data || data.items.every((entry) => !entry.completed)),
      ).toBe(true)
      reject(new Error('503'))
      await vi.waitFor(() =>
        expect(
          JSON.parse(ownerTestOutput(root, 'test-homework-data') ?? 'null')?.items[0]?.completed,
        ).toBe(false),
      )
      expect(onOwnerError).toHaveBeenCalledOnce()
    },
  )

  it.each([
    { pendingTarget: false, step: false },
    { pendingTarget: true, step: false },
    { pendingTarget: false, step: true },
  ])(
    'leaves homework notification ownership before period navigation: $pendingTarget/$step',
    async ({ pendingTarget, step }) => {
      ownerQueryData.realReads = true
      const notice = {
        id: 'notice',
        title: 'Уведомление',
        description: '',
        link: null,
        lessonDate: '2026-09-20',
        lessonNumber: 1,
        bindingMode: 'LESSON' as const,
        archived: false,
        subject: { id: 'subject', name: 'Предмет' },
        completed: false,
        completedAt: null,
      }
      const other = { ...notice, id: 'other', lessonDate: '2026-09-21' }
      const feed = (from = '2026-09-07', to = '2026-12-31'): StudentHomework => ({
        semester: { id: 'semester-1', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
        from,
        to,
        serverNow: '2026-09-07T08:00:00Z',
        items: [notice, other].filter((item) => item.lessonDate >= from && item.lessonDate <= to),
      })
      const pending: ((value: StudentHomework) => void)[] = []
      const getHomework = vi.fn((from?: string, to?: string) =>
        pendingTarget && from === notice.lessonDate && to === from
          ? new Promise<StudentHomework>((resolve) => {
              pending.push(resolve)
            })
          : Promise.resolve(feed(from, to)),
      )
      const cleared = vi.fn()
      const props = reactive<{
        ownerKey: string
        notificationTargetIntent: NotificationTargetIntent | null
        onClearNotificationTarget: (id: number) => void
      }>({
        ownerKey: 'owner-A',
        notificationTargetIntent: null,
        onClearNotificationTarget: cleared,
      })
      const root = mountOwnerTest(
        {
          getToday: async () => ({ lessons: [] }),
          getHomework,
          getRequestOptions: async () => requestOptions(),
        } as unknown as StudentApi,
        props,
      )
      await vi.waitFor(() => expect(getHomework).toHaveBeenCalled())
      clickOwnerTestButton(root, 'test-enter-homework')
      await vi.waitFor(() =>
        expect(ownerTestOutput(root, 'test-homework-period')).toBe('day:2026-09-07'),
      )
      props.notificationTargetIntent = {
        requestId: 7,
        generation: scope.resetGeneration,
        ownerKey: 'owner-A',
        target: { kind: 'homework', homeworkId: notice.id, lessonDate: notice.lessonDate },
      }
      await vi.waitFor(() =>
        expect(getHomework).toHaveBeenCalledWith(notice.lessonDate, notice.lessonDate),
      )
      if (!pendingTarget)
        await vi.waitFor(() => {
          expect(ownerTestOutput(root, 'test-homework-data')).toContain('notice')
          expect(ownerTestOutput(root, 'test-homework-data')).not.toContain('other')
        })
      clickOwnerTestButton(root, step ? 'test-homework-next' : 'test-homework-month')
      await vi.waitFor(() => expect(ownerTestOutput(root, 'test-homework-data')).toContain('other'))
      expect(cleared).toHaveBeenCalledExactlyOnceWith(7)
      expect(ownerTestOutput(root, 'test-homework-period')).toBe(
        step ? 'day:2026-09-21' : 'month:2026-09-07',
      )
      for (const resolve of pending) resolve(feed(notice.lessonDate, notice.lessonDate))
      await settleOwnerTestRender()
      expect(ownerTestOutput(root, 'test-homework-data')).toContain('other')
    },
  )
  it('connects real homework period selection and one-action Today return to the same scoped query owner', async () => {
    ownerQueryData.realReads = true
    const getHomework = vi.fn(async (from?: string, to?: string) => ({
      semester: { id: 'semester-1', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
      from: from ?? '2026-09-07',
      to: to ?? '2026-12-31',
      serverNow: '2026-09-06T21:30:00Z',
      items: [],
    }))
    const root = mountOwnerTest({
      getToday: async () => ({ lessons: [], serverNow: '2026-09-06T21:30:00Z' }),
      getHomework,
      getRequestOptions: async () => requestOptions(),
    } as unknown as StudentApi)
    await vi.waitFor(() => expect(getHomework).toHaveBeenCalledOnce())
    clickOwnerTestButton(root, 'test-enter-homework')
    await vi.waitFor(() =>
      expect(ownerTestOutput(root, 'test-homework-period')).toBe('day:2026-09-07'),
    )
    clickOwnerTestButton(root, 'test-homework-month')
    await vi.waitFor(() => expect(getHomework).toHaveBeenLastCalledWith('2026-09-01', '2026-09-30'))
    await vi.waitFor(() =>
      expect(ownerTestOutput(root, 'test-homework-period')).toBe('month:2026-09-07'),
    )
    const reads = getHomework.mock.calls.length
    clickOwnerTestButton(root, 'test-homework-select-day')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-period')).toBe('month:2026-09-20')
    expect(getHomework).toHaveBeenCalledTimes(reads)
    clickOwnerTestButton(root, 'test-homework-current')
    await vi.waitFor(() =>
      expect(ownerTestOutput(root, 'test-homework-period')).toBe('day:2026-09-07'),
    )
  })
  it.each([
    { oldPageLoading: false, auto: true, offlineReentry: false },
    { oldPageLoading: true, auto: true, offlineReentry: false },
    { oldPageLoading: false, auto: false, offlineReentry: false },
    { oldPageLoading: false, auto: true, offlineReentry: true },
  ])(
    'refreshes OPEN only after AUTO: old loading=$oldPageLoading, auto=$auto, offline=$offlineReentry',
    async ({ oldPageLoading, auto, offlineReentry }) => {
      const pending = requestSummary('PENDING')
      let created = false
      let finishOldPage: ((page: StudentRequestPage) => void) | undefined
      const listRequests = vi.fn(() =>
        created
          ? Promise.resolve(requestPage([pending]))
          : oldPageLoading
            ? new Promise<StudentRequestPage>((resolve) => {
                finishOldPage = resolve
              })
            : Promise.resolve(emptyRequestPage()),
      )
      const props = reactive({ offline: false })
      const root = mountOwnerTest(
        {
          listRequests,
          getRequest: async () => requestDetail(pending),
          getRequestOptions: async () => requestOptions(),
        } as unknown as StudentApi,
        props,
      )
      ownerQueryData.today!.value = {
        date: '2026-10-04',
        timeZone: 'Europe/Moscow',
        serverNow: '2026-10-04T08:00:00Z',
        _links: {},
        lessons: [
          {
            schedule: {
              id: 'lesson-1',
              date: '2026-10-04',
              lessonNumber: 1,
              startsAt: '10:40:00',
              endsAt: '12:10:00',
              status: 'ACTIVE',
              subject: { id: 'subject-1', name: 'Программирование', type: 'LECTURE' },
              room: { current: 'А-401', previous: null, changeState: 'UNCHANGED' },
            },
            attendance: null,
            request: null,
            checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
          },
        ],
      }
      checkinSubmit.mockImplementation(async () => {
        created = auto
        if (!auto) return ack
        return {
          ...ack,
          outcome: 'PENDING_CONFIRMATION',
          attendance: null,
          request: {
            id: pending.id,
            origin: 'AUTO_GEO_FAILURE',
            status: 'PENDING',
            resolutionReason: null,
          },
        } satisfies StudentCheckinAck
      })
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-enter-more')
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-enter-requests')
      await settleOwnerTestRender()
      expect(listRequests).toHaveBeenCalledExactlyOnceWith('OPEN', 0)
      expect(ownerTestOutput(root, 'test-request-ids')).toBe('')
      clickOwnerTestButton(root, 'test-owner-today')
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-checkin')
      await vi.waitFor(() => expect(checkinSubmit).toHaveBeenCalledOnce())
      await settleOwnerTestRender()
      finishOldPage?.(emptyRequestPage())
      await settleOwnerTestRender()
      expect(listRequests).toHaveBeenCalledOnce()
      props.offline = offlineReentry
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-enter-more')
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-enter-requests')
      if (offlineReentry) {
        await settleOwnerTestRender()
        expect(listRequests).toHaveBeenCalledOnce()
        props.offline = false
      }
      await vi.waitFor(() =>
        expect(ownerTestOutput(root, 'test-request-ids')).toBe(auto ? pending.id : ''),
      )
      expect(listRequests).toHaveBeenCalledTimes(auto ? 2 : 1)
      expect(listRequests).toHaveBeenLastCalledWith('OPEN', 0)
    },
  )

  it('blocks another click during location acquisition and keeps its failure local', async () => {
    let rejectLocation!: (error: Error) => void
    const acquire = vi.fn(
      () =>
        new Promise<StudentCheckinCommand>((_resolve, reject) => {
          rejectLocation = reject
        }),
    )
    const ownerError = vi.fn()
    const root = mountOwnerTest(
      { getRequestOptions: async () => requestOptions() } as unknown as StudentApi,
      {
        acquireCheckinCommand: acquire,
        onOwnerError: ownerError,
      },
    )
    ownerQueryData.today!.value = {
      date: '2026-10-04',
      timeZone: 'Europe/Moscow',
      serverNow: '2026-10-04T08:00:00Z',
      _links: {},
      lessons: [
        {
          schedule: {
            id: 'lesson-1',
            date: '2026-10-04',
            lessonNumber: 1,
            startsAt: '10:40:00',
            endsAt: '12:10:00',
            status: 'ACTIVE',
            subject: { id: 'subject-1', name: 'Основы программирования', type: 'LECTURE' },
            room: { current: 'А-401', previous: null, changeState: 'UNCHANGED' },
          },
          attendance: null,
          request: null,
          checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
        },
      ],
    }
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-checkin')
    clickOwnerTestButton(root, 'test-checkin')
    await settleOwnerTestRender()
    expect(acquire).toHaveBeenCalledTimes(1)
    expect(ownerTestOutput(root, 'test-acquiring')).toBe('lesson-1')
    rejectLocation(new Error('Геолокация временно недоступна'))
    await settleOwnerTestRender()
    await vi.waitFor(() =>
      expect(ownerTestOutput(root, 'test-checkin-error')).toBe('Геолокация временно недоступна'),
    )
    expect(ownerTestOutput(root, 'test-acquiring')).toBe('')
    expect(checkinSubmit).not.toHaveBeenCalled()
    expect(ownerError).not.toHaveBeenCalled()
  })

  it('waits for real warm Vue Query GETs, keeps one read per feed, and accepts a later successful retry', async () => {
    ownerQueryData.realReads = true
    const warmToday = { serverNow: 'warm-today', lessons: [] } as unknown as StudentToday
    const warmHomework = {
      serverNow: 'warm-homework',
      from: '2026-09-08',
      to: '2026-09-09',
      semester: { dateFrom: '2026-09-01', dateTo: '2026-12-31' },
      items: [],
    } as unknown as StudentHomework
    const getToday = vi.fn(() => Promise.resolve(warmToday))
    const getHomework = vi.fn(() => Promise.resolve(warmHomework))
    const props = reactive({
      offline: false,
      readOnly: false,
      todayFallback: warmToday,
      homeworkFallback: warmHomework,
    })
    const root = mountOwnerTest({ getToday, getHomework } as unknown as StudentApi, props)
    await vi.waitFor(() => expect(ownerTestOutput(root, 'test-today-data')).toContain('warm-today'))
    props.offline = true
    props.readOnly = true
    await settleOwnerTestRender()
    let resolveToday!: (today: StudentToday) => void
    let rejectHomework!: (error: Error) => void
    getToday.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveToday = resolve
        }),
    )
    getHomework.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectHomework = reject
        }),
    )
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
    await vi.waitFor(
      () =>
        expect(
          queryClient.getQueryCache().find({ queryKey: ['student', 'homework'], exact: false })
            ?.state.status,
        ).toBe('error'),
      { timeout: 2500 },
    )
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')
    getHomework.mockResolvedValue({ ...warmHomework, serverNow: 'fresh-homework' })
    await queryClient.refetchQueries({ queryKey: ['student', 'homework'] })
    await vi.waitFor(() =>
      expect(ownerTestOutput(root, 'test-homework-data')).toContain('fresh-homework'),
    )
    clickOwnerTestButton(root, 'test-return-today')
    await vi.waitFor(() =>
      expect(ownerTestOutput(root, 'test-today-data')).toContain('fresh-today'),
    )
  })

  it('does not present warm Today/HW as fresh while the same owner reconnects', async () => {
    let finishToday!: (result: { isSuccess: boolean }) => void
    let finishHomework!: (result: { isSuccess: boolean }) => void
    ownerQueryData.todayRefetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          finishToday = resolve
        }),
    )
    ownerQueryData.homeworkRefetch.mockImplementation(
      () =>
        new Promise((resolve) => {
          finishHomework = resolve
        }),
    )
    const props = reactive({ offline: false, readOnly: false })
    const root = mountOwnerTest({} as StudentApi, props)
    ownerQueryData.today!.value = {
      serverNow: 'warm-today',
      lessons: [],
    } as unknown as StudentToday
    ownerQueryData.homework!.value = {
      serverNow: 'warm-homework',
      items: [],
    } as unknown as StudentHomework
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

    ownerQueryData.today!.value = {
      serverNow: 'fresh-today',
      lessons: [],
    } as unknown as StudentToday
    ownerQueryData.homework!.value = {
      serverNow: 'fresh-homework',
      items: [],
    } as unknown as StudentHomework
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
    const props = reactive({
      offline: true,
      readOnly: true,
      todayFallback: savedToday,
      homeworkFallback: savedHomework,
    })
    const root = mountOwnerTest({} as StudentApi, props)
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toContain('saved-today')
    expect(ownerTestOutput(root, 'test-today-readonly')).toBe('true')

    props.offline = false
    props.readOnly = false
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toBe('null')
    ownerQueryData.today!.value = {
      serverNow: 'fresh-today',
      lessons: [],
    } as unknown as StudentToday
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-today-data')).toContain('fresh-today')

    clickOwnerTestButton(root, 'test-enter-homework')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-homework-data')).toBe('null')
    ownerQueryData.homework!.value = {
      serverNow: 'fresh-homework',
      items: [],
    } as unknown as StudentHomework
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
  it.each([
    ['EXCUSE', 'EXCUSE', 'MANUAL', false],
    ['EXCUSE', 'LATE_CHECKIN', 'MANUAL', true],
    ['EXCUSE', 'LATE_CHECKIN', 'AUTO_GEO_FAILURE', true],
    ['LATE_CHECKIN', 'LATE_CHECKIN', 'MANUAL', false],
    ['LATE_CHECKIN', 'LATE_CHECKIN', 'AUTO_GEO_FAILURE', false],
    ['LATE_CHECKIN', 'EXCUSE', 'MANUAL', true],
    ['EXCUSE', null, 'MANUAL', false],
    ['LATE_CHECKIN', undefined, 'MANUAL', false],
    ['EXCUSE', 'UNKNOWN', 'MANUAL', false],
    ['LATE_CHECKIN', 'UNKNOWN', 'MANUAL', false],
  ] as const)(
    'fences actual Today %s entry with pending %s (%s)',
    async (kind, pendingKind, origin, allowed) => {
      const value = requestOptions()
      value.lessons[0]!.pendingRequests = [{ id: 'pending-1', kind: pendingKind as never, origin }]
      const getRequestOptions = vi.fn(() => Promise.resolve(value))
      const root = mountOwnerTest({ getRequestOptions } as unknown as StudentApi)
      ownerQueryData.today!.value = {
        date: '2026-10-04',
        timeZone: 'Europe/Moscow',
        serverNow: '2026-10-04T10:00:00Z',
        _links: {},
        lessons: [
          {
            schedule: {
              id: 'lesson-1',
              date: '2026-10-04',
              lessonNumber: 1,
              startsAt: '10:40:00',
              endsAt: '12:10:00',
              status: 'CLOSED',
              subject: { id: 'subject-1', name: 'Основы программирования', type: 'LECTURE' },
              room: { current: 'А-401', previous: null, changeState: 'UNCHANGED' },
            },
            attendance: { status: 'ABSENT', source: 'HEADMAN', markedAt: '2026-10-04T10:00:00Z' },
            request: null,
            checkinEligibility: { allowed: false, reason: 'WINDOW_CLOSED', retryAt: null },
          },
        ],
      }
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-toggle-today-actions')
      await Promise.all(getRequestOptions.mock.results.map((call) => call.value))
      await settleOwnerTestRender()
      clickOwnerTestButton(
        root,
        kind === 'EXCUSE' ? 'test-open-today-excuse' : 'test-open-today-late',
      )
      await settleOwnerTestRender()
      if (allowed)
        expect(
          ownerTestOutput(
            root,
            kind === 'EXCUSE' ? 'test-request-lessons' : 'test-late-request-lesson',
          ),
        ).toBe('lesson-1')
      else expect(ownerTestOutput(root, 'test-today-data')).toContain('lesson-1')
    },
  )

  it('refreshes Today after a successful local request cancellation', async () => {
    let cancelled = false
    const pending = requestSummary('PENDING')
    const cancelledDetail = requestDetail(requestSummary('CANCELLED'))
    const api = {
      getToday: vi.fn(() => Promise.resolve({} as StudentToday)),
      getHomework: vi.fn(() => Promise.resolve({} as StudentHomework)),
      listRequests: vi.fn((bucket: 'OPEN' | 'ARCHIVE') =>
        Promise.resolve(
          bucket === 'OPEN' && !cancelled
            ? requestPage([pending])
            : bucket === 'ARCHIVE' && cancelled
              ? requestPage([cancelledDetail.summary])
              : emptyRequestPage(),
        ),
      ),
      getRequest: vi.fn(() =>
        Promise.resolve(cancelled ? cancelledDetail : requestDetail(pending)),
      ),
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

  it('localizes a request port network failure and clears only its error on explicit abandon', async () => {
    ownerQueryData.realShell = true
    const submitExcuse = vi.fn(() => Promise.reject(new TypeError('Failed to fetch synthetic-private')))
    const api = {
      getRequestOptions: async () => multiRequestOptions(),
      listRequests: async () => emptyRequestPage(),
      submitExcuse,
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
    clickOwnerTestButton(root, 'test-select-request-lessons')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-select-request-reason')
    clickOwnerTestButton(root, 'test-pick-request-file')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-submit-request')
    await vi.waitFor(() => expect(submitExcuse).toHaveBeenCalledOnce())
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-submit-error')).toMatch(/\S/)
    expect(ownerTestOutput(root, 'test-request-submit-error')).not.toContain('synthetic-private')
    expect(ownerTestOutput(root, 'test-request-ambiguous')).toBe('true')
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    clickOwnerTestButton(root, 'test-clear-request-reason')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-submit-error')).toMatch(/\S/)
    expect(ownerTestOutput(root, 'test-request-ambiguous')).toBe('true')
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    clickOwnerTestButton(root, 'test-edit-request-comment')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-submit-error')).toMatch(/\S/)
    expect(ownerTestOutput(root, 'test-request-ambiguous')).toBe('true')
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    clickOwnerTestButton(root, 'test-abandon-request')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-submit-error')).toBe('')
    expect(ownerTestOutput(root, 'test-request-ambiguous')).toBe('false')
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    expect(submitExcuse).toHaveBeenCalledOnce()
  })

  it('loads request options once when the real owner opens a request form', async () => {
    let resolveOptions!: (value: StudentRequestOptions) => void
    const pendingOptions = new Promise<StudentRequestOptions>((resolve) => {
      resolveOptions = resolve
    })
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

  it('requires current eligible nonempty selection before continuing, preserves draft on host Back, and performs no create', async () => {
    ownerQueryData.realShell = true
    let hostBack: (() => void) | undefined
    const submitExcuse = vi.fn()
    const api = {
      getRequestOptions: async () => multiRequestOptions(),
      listRequests: async () => emptyRequestPage(),
      submitExcuse,
    } as unknown as StudentApi
    const options = reactive({
      offline: false,
      host: {
        backOwner: 'host',
        primaryActionOwner: 'product',
        subscribeBack(callback: () => void) {
          hostBack = callback
          return () => {
            hostBack = undefined
          }
        },
        setBackVisible: vi.fn(),
      },
    })
    const root = mountOwnerTest(api, options)
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-more')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-requests')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'requests-primary-action')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-choose-excuse')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-selection-lessons')).toBe('')
    clickOwnerTestButton(root, 'test-invalid-request-lessons')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-selection-lessons')).toBe('unknown')
    clickOwnerTestButton(root, 'test-select-request-lessons')
    await settleOwnerTestRender()
    options.offline = true
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-selection-lessons')).toBe('lesson-1,lesson-2')
    options.offline = false
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    hostBack?.()
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-selection-lessons')).toBe('lesson-1,lesson-2')
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    expect(submitExcuse).not.toHaveBeenCalled()
  })

  it('drops a late options response and draft selection when account authority changes', async () => {
    let resolveOptions!: (value: StudentRequestOptions) => void
    const pending = new Promise<StudentRequestOptions>((resolve) => {
      resolveOptions = resolve
    })
    const api = {
      getRequestOptions: () => pending,
      listRequests: async () => emptyRequestPage(),
    } as unknown as StudentApi
    const options = reactive({ scope: { ...scope } })
    const root = mountOwnerTest(api, options)
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-more')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-enter-requests')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'requests-primary-action')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-choose-excuse')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-select-request-lessons')
    await settleOwnerTestRender()
    options.scope = { ...scope, userId: 'another-student', sessionId: 'another-session' }
    await settleOwnerTestRender()
    resolveOptions(multiRequestOptions())
    await pending
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-selection-lessons')).toBe('')
    expect(ownerTestOutput(root, 'test-selection-options')).toBe('')
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-lessons')).toBeUndefined()
    expect(ownerTestOutput(root, 'test-selection-lessons')).toBe('')
  })

  it('publishes request draft edits and preserves the latest file, pairs, and reason across form navigation', async () => {
    const api = {
      getToday: vi.fn(() => Promise.resolve({} as StudentToday)),
      getHomework: vi.fn(() => Promise.resolve({} as StudentHomework)),
      listRequests: vi.fn(() => Promise.resolve(emptyRequestPage())),
      getRequest: vi.fn(),
      getRequestOptions: vi.fn(() => Promise.resolve(multiRequestOptions())),
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
    clickOwnerTestButton(root, 'test-select-request-lessons')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
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
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    expect(ownerTestOutput(root, 'test-request-reason')).toBe('OTHER')

    clickOwnerTestButton(root, 'test-remove-request-files')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('')
    clickOwnerTestButton(root, 'test-back-request-form')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('')

    clickOwnerTestButton(root, 'test-pick-request-file')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-back-request-form')
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-continue-selection')
    await settleOwnerTestRender()
    expect(ownerTestOutput(root, 'test-request-files')).toBe('proof.pdf')
    expect(ownerTestOutput(root, 'test-request-lessons')).toBe('lesson-1,lesson-2')
    expect(ownerTestOutput(root, 'test-request-reason')).toBe('OTHER')
  })
})

describe('StudentFeatureOwner statistics navigation', () => {
  it.each(['product', 'host'] as const)(
    'opens a subject without dock and clears it on %s Back',
    async (backOwner) => {
      ownerQueryData.realShell = true
      let hostBack: (() => void) | undefined
      const setBackVisible = vi.fn()
      const metrics = {
        present: { count: 1, percent: 100 },
        presentOrExcused: { count: 1, percent: 100 },
        excused: { count: 0, percent: 0 },
        absent: { count: 0, percent: 0 },
        held: 1,
        planned: 1,
      }
      const api = {
        getStatistics: async () => ({
          metrics,
          ownRank: { available: true, position: 1, participantCount: 1 },
          semesterSeries: [],
          subjects: [],
        }),
        getStatisticsSubject: async () => ({
          subjectId: 'subject-1',
          name: 'Предмет',
          availableTypes: ['LECTURE'],
          selectedTypes: ['LECTURE'],
          selectedAggregate: metrics,
          series: [],
          typeCards: [],
        }),
      } as unknown as StudentApi
      const root = mountOwnerTest(api, {
        host: {
          backOwner,
          primaryActionOwner: 'product',
          subscribeBack: (callback: () => void) => {
            hostBack = callback
            return () => {
              hostBack = undefined
            }
          },
          setBackVisible,
        },
      })
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-enter-more')
      await settleOwnerTestRender()
      clickOwnerTestButton(root, 'test-enter-statistics')
      await settleOwnerTestRender()
      expect(ownerTestOutput(root, 'test-statistics-subject')).toBe('overview')
      expect(
        findOwnerTestNode(root, (node) => node.props['data-dock-visible'] === true),
      ).toBeDefined()
      clickOwnerTestButton(root, 'test-open-statistics-subject')
      await settleOwnerTestRender()
      expect(ownerTestOutput(root, 'test-statistics-subject')).toBe('subject-1')
      expect(
        findOwnerTestNode(root, (node) => node.props.class === 'mobile-shell__back'),
      ).toBeUndefined()
      expect(
        findOwnerTestNode(
          root,
          (node) =>
            node.props['data-surface'] === 'detail' && node.props['data-dock-visible'] === false,
        ),
      ).toBeDefined()
      if (backOwner === 'host') {
        expect(setBackVisible).toHaveBeenLastCalledWith(true)
        hostBack?.()
      } else clickOwnerTestButton(root, 'test-back-statistics')
      await settleOwnerTestRender()
      expect(ownerTestOutput(root, 'test-statistics-subject')).toBe('overview')
      expect(
        findOwnerTestNode(root, (node) => node.props['data-dock-visible'] === true),
      ).toBeDefined()
      clickOwnerTestButton(root, 'test-back-statistics')
      await settleOwnerTestRender()
      expect(
        findOwnerTestNode(root, (node) => node.props.class === 'test-enter-statistics'),
      ).toBeDefined()
      clickOwnerTestButton(root, 'test-enter-statistics')
      await settleOwnerTestRender()
      expect(ownerTestOutput(root, 'test-statistics-subject')).toBe('overview')
    },
  )
})

describe('StudentFeatureOwner connection retry', () => {
  it('routes the existing Today retry to shell readmission only while offline', async () => {
    const reconnect = vi.fn()
    const props = reactive({ offline: true, onRetryConnection: reconnect })
    const root = mountOwnerTest({} as StudentApi, props)
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-retry-connection')
    expect(reconnect).toHaveBeenCalledTimes(1)
    props.offline = false
    await settleOwnerTestRender()
    clickOwnerTestButton(root, 'test-retry-connection')
    expect(reconnect).toHaveBeenCalledTimes(1)
  })
})

it('retains the mounted owner uncertain check-in through explicit connection retry', async () => {
  const today: StudentToday = {
    date: '2026-10-04',
    timeZone: 'Europe/Moscow',
    serverNow: '2026-10-04T08:00:00Z',
    _links: {},
    lessons: [
      {
        schedule: {
          id: 'lesson-1',
          date: '2026-10-04',
          lessonNumber: 1,
          startsAt: '10:40:00',
          endsAt: '12:10:00',
          status: 'ACTIVE',
          subject: { id: 'subject-1', name: 'Предмет', type: 'LECTURE' },
          room: { current: 'А-401', previous: null, changeState: 'UNCHANGED' },
        },
        attendance: null,
        request: null,
        checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
      },
    ],
  }
  const acquire = vi.fn(async () => command)
  const props = reactive({
    offline: false,
    readOnly: false,
    todayFallback: today,
    acquireCheckinCommand: acquire,
    onRetryConnection: async () => {
      props.offline = false
      props.readOnly = false
    },
  })
  checkinSubmit
    .mockRejectedValueOnce(new TypeError('Synthetic lost acknowledgement'))
    .mockResolvedValueOnce(ack)
  const root = mountOwnerTest(
    { getRequestOptions: async () => requestOptions() } as unknown as StudentApi,
    props,
  )
  ownerQueryData.today!.value = today
  await settleOwnerTestRender()
  clickOwnerTestButton(root, 'test-checkin')
  await vi.waitFor(() => expect(checkinSubmit).toHaveBeenCalledTimes(1))
  await settleOwnerTestRender()
  const first = checkinSubmit.mock.calls[0]![0]
  expect(first.key).toEqual(expect.any(String))
  props.offline = true
  props.readOnly = true
  await settleOwnerTestRender()
  clickOwnerTestButton(root, 'test-checkin')
  expect(checkinSubmit).toHaveBeenCalledTimes(1)
  clickOwnerTestButton(root, 'test-retry-connection')
  await settleOwnerTestRender()
  // Readmission itself never replays the uncertain mutation.
  expect(checkinSubmit).toHaveBeenCalledTimes(1)
  clickOwnerTestButton(root, 'test-checkin')
  await vi.waitFor(() => expect(checkinSubmit).toHaveBeenCalledTimes(2))
  expect(checkinSubmit.mock.calls[1]![0]).toEqual(first)
  expect(acquire).toHaveBeenCalledTimes(1)
})

it('keeps loaded profile history offline and only paginates after an explicit online action', async () => {
  const { DEFAULT_PASSWORD_POLICY } = await import('../../features/profile/profile-types')
  const record = { id: 'history-a', type: 'PASSWORD_CHANGED', occurredAt: '2026-09-08T08:00:00Z', metadata: {} }
  let resolvePage!: (page: { items: typeof record[]; nextCursor: null }) => void
  const listHistory = vi.fn().mockResolvedValueOnce({ items: [record], nextCursor: 'cursor-a' })
    .mockImplementationOnce(() => new Promise(resolve => { resolvePage = resolve }))
  const options = reactive({ offline: false })
  const profilePort = {
    getSnapshot: async () => ({ ...scope, displayName: 'Synthetic profile owner', groupLabel: 'Group',
      roles: [{ grantId: 'g', role: 'STUDENT', status: 'ACTIVE', groupId: scope.groupId,
        contextLabel: 'Group', selectable: true, readOnly: false }], passwordPolicy: DEFAULT_PASSWORD_POLICY }),
    listHistory, listSessions: async () => ({ items: [], nextCursor: null }), isOnline: () => !options.offline,
  }
  const root = mountOwnerTest({} as StudentApi, {
    get offline() { return options.offline }, profilePort,
  })
  clickOwnerTestButton(root, 'test-enter-profile')
  await settleOwnerTestRender()
  const contents = (node: OwnerTestHostNode): string => (node.text ?? '') + node.children.map(contents).join(' ')
  const history = findOwnerTestNode(root, node => node.tag === 'button' && contents(node).includes('История аккаунта'))!
  expect(history).toBeDefined()
  ;(history.props.onClick as () => void)()
  await settleOwnerTestRender()
  await vi.waitFor(() => expect(listHistory).toHaveBeenCalledTimes(1))
  options.offline = true
  await settleOwnerTestRender()
  const more = findOwnerTestNode(root, node => node.tag === 'button' && contents(node).includes('Показать ещё'))!
  expect(more).toBeDefined()
  ;(more.props.onClick as () => void)()
  await settleOwnerTestRender()
  expect(listHistory).toHaveBeenCalledTimes(1)
  expect(findOwnerTestNode(root, node => node.tag === 'ol' && node.props['aria-label'] === 'История аккаунта')?.children.filter(node => node.tag === 'li')).toHaveLength(1)
  options.offline = false
  await settleOwnerTestRender()
  expect(listHistory).toHaveBeenCalledTimes(1)
  ;(more.props.onClick as () => void)()
  await settleOwnerTestRender()
  expect(listHistory).toHaveBeenCalledTimes(2)
  expect(listHistory).toHaveBeenLastCalledWith({ cursor: 'cursor-a' })
  resolvePage({ items: [{ ...record, id: 'history-b' }], nextCursor: null })
  await settleOwnerTestRender()
  expect(findOwnerTestNode(root, node => node.tag === 'button' && contents(node).includes('Показать ещё'))).toBeUndefined()
})
