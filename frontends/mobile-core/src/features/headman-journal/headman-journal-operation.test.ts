import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, markRaw, reactive, nextTick, type App, type Component } from 'vue'
import HeadmanJournalScreen from './HeadmanJournalScreen.vue'
import HeadmanHomeScreen from '../headman-home/HeadmanHomeScreen.vue'
import type {
  HeadmanJournalApi,
  HeadmanJournalLesson,
  HeadmanJournalReport,
  HeadmanJournalRosterEntry,
} from './headman-journal-client'
import { journalScopeCurrent, type JournalMutationScope } from './headman-journal-operation'

describe('journal destructive confirmation and late ACK ownership', () => {
  const scope: JournalMutationScope = { api: {}, groupId: 4, lessonId: 30, generation: 1 }
  it('allows the unchanged captured account/group/lesson generation', () => {
    expect(journalScopeCurrent(scope, { ...scope })).toBe(true)
  })
  it.each([
    ['account', { ...scope, api: {} }],
    ['group', { ...scope, groupId: 5 }],
    ['lesson', { ...scope, lessonId: 31 }],
    ['rights or offline cycle', { ...scope, generation: 2 }],
    ['disconnected owner', null],
  ])('rejects an old confirmation and ACK after changing %s', (_label, next) => {
    expect(journalScopeCurrent(scope, next)).toBe(false)
  })
  it('does not publish a late destructive ACK into the newly selected lesson', async () => {
    let current: JournalMutationScope | null = { ...scope }
    let finish!: () => void
    const ack = new Promise<void>((resolve) => {
      finish = resolve
    })
    let visibleMark = 'EXCUSED'
    const run = async () => {
      await ack
      if (journalScopeCurrent(scope, current)) visibleMark = 'EMPTY'
    }
    const pending = run()
    current = { ...scope, lessonId: 31, generation: 2 }
    finish()
    await pending
    expect(visibleMark).toBe('EXCUSED')
  })
})

// Same browser-free Vue host seam as the existing headman screen tests; the SFCs are real.
class JournalNode {
  children: JournalNode[] = []
  parent: JournalNode | null = null
  props: Record<string, unknown> = {}
  text = ''
  value: unknown = ''
  selected = false
  checked = false
  constructor(readonly tag: string) {
    markRaw(this)
  }
  focus(): void {
    focusJournalNode(this)
  }
  close(): void {
    this.props.open = false
  }
  showModal(): void {
    this.props.open = true
  }
  addEventListener(): void {}
  getRootNode() {
    return { activeElement: null }
  }
  get tagName() {
    return this.tag.toUpperCase()
  }
  get options() {
    return this.children.filter((node) => node.tag === 'option')
  }
}
let journalFocusedNode: JournalNode | null = null
function focusJournalNode(node: JournalNode): void {
  if (journalFocusedNode) journalFocusedNode.props.focused = false
  journalFocusedNode = node
  node.props.focused = true
}
const journalRenderer = createRenderer<JournalNode, JournalNode>({
  createElement: (tag) => new JournalNode(tag),
  createText: (text) => Object.assign(new JournalNode('#text'), { text }),
  createComment: () => new JournalNode('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _before, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
function journalNodes(root: JournalNode): JournalNode[] {
  return [root, ...root.children.flatMap(journalNodes)]
}
function journalButton(root: JournalNode, label: string): JournalNode | undefined {
  return journalNodes(root).find(
    (node) => node.tag === 'button' && node.props['aria-label'] === label,
  )
}
function journalClick(node: JournalNode | undefined): void {
  expect(node).toBeDefined()
  ;(node!.props.onClick as () => void)()
}
const journalApps: App[] = []
afterEach(() => {
  journalApps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})
function mountJournalScreen(component: Component, props: Record<string, unknown>): JournalNode {
  vi.stubGlobal('HTMLElement', class {})
  vi.stubGlobal('HTMLButtonElement', class {})
  vi.stubGlobal('document', {
    activeElement: null,
    addEventListener() {},
    removeEventListener() {},
  })
  const root = new JournalNode('root')
  const app = journalRenderer.createApp({ render: () => h(component, props) })
  app.mount(root)
  journalApps.push(app)
  return root
}
function journalFixture(
  future = false,
  initialStatus: 'PRESENT' | 'ABSENT' | 'EXCUSED' = 'PRESENT',
) {
  const date = future ? '2099-09-08' : '2026-09-07'
  const lesson: HeadmanJournalLesson = {
    id: 30,
    groupId: 4,
    subjectId: 5,
    date,
    status: future ? 'PLANNED' : 'ACTIVE',
    lessonNumber: 1,
    startTime: '08:30',
    endTime: '09:50',
    room: null,
    lessonType: 'LECTURE',
    blockedByHeadman: false,
    occurrenceRevision: '1',
    current: true,
    transferOperationId: null,
    transferState: null,
  }
  let entry: HeadmanJournalRosterEntry = {
    userId: 7,
    displayName: 'Синтетический студент',
    status: future ? 'EMPTY' : initialStatus,
    symbol: future ? null : '+',
    markRevision: future ? null : 'revision-a',
    approvedTicket: null,
    source: future ? null : 'headman',
    excuseReason: null,
    excuseType: null,
    comment: null,
    attachmentId: null,
    attachmentName: null,
    attachmentContentType: null,
    attachmentSize: null,
    editable: !future,
  }
  const report = (): HeadmanJournalReport => ({
    lessonId: 30,
    groupId: 4,
    subjectId: 5,
    lessonDate: date,
    semesterId: 2,
    lessonStatus: lesson.status,
    editable: !future,
    entries: [{ ...entry }],
  })
  const clear = vi.fn(async () => {
    entry = { ...entry, status: 'EMPTY', symbol: null, markRevision: null, source: null }
  })
  const api = {
    writable: true,
    listLessons: vi.fn(async () => [lesson]),
    getLessonAttendance: vi.fn(async () => report()),
    clear,
    mark: vi.fn(),
    setHeadmanBlock: vi.fn(async (_id: number, blocked: boolean) => ({
      ...lesson,
      blockedByHeadman: blocked,
    })),
  } as unknown as HeadmanJournalApi
  return { api, lesson, clear, report }
}
describe('accepted journal production component controls', () => {
  it('opens the filled chooser without clearing, then clears only the selected choice', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(
        journalButton(root, 'Был: Синтетический студент. Открыть выбор отметки'),
      ).toBeDefined(),
    )
    const edit = journalButton(root, 'Изменить отметку: Синтетический студент')
    expect(edit, 'filled mark exposes the accepted edit action').toBeDefined()
    journalClick(edit)
    await vi.waitFor(() =>
      expect(journalButton(root, 'Не был: Синтетический студент')).toBeDefined(),
    )
    expect(journalButton(root, 'Был: Синтетический студент')?.props.focused).toBe(true)
    expect(fixture.clear).not.toHaveBeenCalled()
    expect(fixture.report().entries[0]?.status).toBe('PRESENT')
    journalClick(journalButton(root, 'Был: Синтетический студент'))
    await vi.waitFor(() => expect(fixture.report().entries[0]?.status).toBe('EMPTY'))
    expect(fixture.clear).toHaveBeenCalledTimes(1)
    expect(fixture.clear).toHaveBeenCalledWith(30, 7, {
      expectedStatus: 'PRESENT',
      expectedRevision: 'revision-a',
    })
    await vi.waitFor(() => expect(journalButton(root, 'Был: Синтетический студент')).toBeDefined())
  })
  it('clears the repeated EXCUSED choice immediately without opening an attachment dialog', async () => {
    const fixture = journalFixture(false, 'EXCUSED')
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(journalButton(root, 'Изменить отметку: Синтетический студент')).toBeDefined(),
    )
    journalClick(journalButton(root, 'Изменить отметку: Синтетический студент'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Уважительная причина: Синтетический студент')).toBeDefined(),
    )
    expect(fixture.clear).not.toHaveBeenCalled()
    journalClick(journalButton(root, 'Уважительная причина: Синтетический студент'))
    await vi.waitFor(() => expect(fixture.report().entries[0]?.status).toBe('EMPTY'))
    expect(fixture.clear).toHaveBeenCalledTimes(1)
    expect(fixture.clear).toHaveBeenCalledWith(30, 7, {
      expectedStatus: 'EXCUSED',
      expectedRevision: 'revision-a',
    })
    expect(journalNodes(root).some((node) => node.tag === 'dialog' && node.props.open)).toBe(false)
  })
  it('retains the MANAGE_EXCUSES admission before an EXCUSED choice can clear', async () => {
    const fixture = journalFixture(false, 'EXCUSED')
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
      assistantPermissions: ['MARK_ATTENDANCE'],
    })
    await vi.waitFor(() =>
      expect(journalButton(root, 'Изменить отметку: Синтетический студент')).toBeDefined(),
    )
    const edit = journalButton(root, 'Изменить отметку: Синтетический студент')
    expect(edit?.props.disabled).toBe(true)
    journalClick(edit)
    await nextTick()
    expect(journalButton(root, 'Уважительная причина: Синтетический студент') === undefined).toBe(
      true,
    )
    expect(fixture.clear).not.toHaveBeenCalled()
    expect(fixture.report().entries[0]?.status).toBe('EXCUSED')
  })
  it('does not render mark choices before the lesson starts', async () => {
    const fixture = journalFixture(true)
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(journalNodes(root).some((node) => node.props['data-user-id'] === 7)).toBe(true),
    )
    expect(
      journalButton(root, 'Был: Синтетический студент') === undefined,
      'future mark choices are absent, not disabled',
    ).toBe(true)
    expect(journalNodes(root).some((node) => node.text.includes('Пара ещё не началась'))).toBe(true)
    expect(fixture.clear).not.toHaveBeenCalled()
  })
  it('offers geo blocking on the loaded active main lesson', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanHomeScreen, {
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
    })
    await vi.waitFor(() => expect(journalButton(root, 'Открыть учёт выбранной пары')).toBeDefined())
    expect(
      journalButton(root, 'Заблокировать геоотметки'),
      'active main lesson exposes its block action',
    ).toBeDefined()
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Подтвердить блокировку геоотметок')).toBeDefined(),
    )
    expect(fixture.api.setHeadmanBlock).not.toHaveBeenCalled()
    const cancel = journalNodes(root).find(
      (node) => node.tag === 'button' && node.text.trim() === 'Отмена',
    )
    expect(cancel?.props.focused).toBe(true)
    const group = journalNodes(root).find(
      (node) =>
        node.props.role === 'group' &&
        node.props['aria-label'] === 'Подтвердить блокировку геоотметок',
    )
    expect(group).toBeDefined()
    const preventDefault = vi.fn()
    const stopPropagation = vi.fn()
    ;(group!.props.onKeydown as (event: unknown) => void)({
      key: 'Escape',
      preventDefault,
      stopPropagation,
    })
    await nextTick()
    expect(preventDefault).toHaveBeenCalledOnce()
    expect(stopPropagation).toHaveBeenCalledOnce()
    expect(journalButton(root, 'Подтвердить блокировку геоотметок') === undefined).toBe(true)
    expect(journalButton(root, 'Заблокировать геоотметки')?.props.focused).toBe(true)
    expect(fixture.api.setHeadmanBlock).not.toHaveBeenCalled()
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(
      journalNodes(root).find((node) => node.tag === 'button' && node.text.trim() === 'Отмена'),
    )
    await nextTick()
    expect(journalButton(root, 'Заблокировать геоотметки')?.props.focused).toBe(true)
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(journalButton(root, 'Подтвердить блокировку геоотметок'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Разблокировать геоотметки')?.props.focused).toBe(true),
    )
    expect(fixture.api.setHeadmanBlock).toHaveBeenCalledWith(30, true)
    journalClick(journalButton(root, 'Разблокировать геоотметки'))
    await vi.waitFor(() => expect(journalButton(root, 'Заблокировать геоотметки')).toBeDefined())
    expect(fixture.api.setHeadmanBlock).toHaveBeenLastCalledWith(30, false)
  })
  it('keeps a writable transport read-only in an archived owner', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanHomeScreen, {
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
      readOnly: true,
    })
    await vi.waitFor(() => expect(journalButton(root, 'Открыть учёт выбранной пары')).toBeDefined())
    expect(journalButton(root, 'Заблокировать геоотметки') === undefined).toBe(true)
    expect(fixture.api.setHeadmanBlock).not.toHaveBeenCalled()
  })
  it('drops the old block confirmation and late ACK when the owner goes offline', async () => {
    const fixture = journalFixture()
    let finish!: (lesson: HeadmanJournalLesson) => void
    const pending = new Promise<HeadmanJournalLesson>((resolve) => {
      finish = resolve
    })
    vi.mocked(fixture.api.setHeadmanBlock).mockReturnValue(pending)
    const props = reactive({
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
      offline: false,
    })
    const root = mountJournalScreen(HeadmanHomeScreen, props)
    await vi.waitFor(() => expect(journalButton(root, 'Заблокировать геоотметки')).toBeDefined())
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(journalButton(root, 'Подтвердить блокировку геоотметок'))
    props.offline = true
    await nextTick()
    finish({ ...fixture.lesson, blockedByHeadman: true })
    await nextTick()
    await nextTick()
    expect(journalButton(root, 'Разблокировать геоотметки') === undefined).toBe(true)
    expect(journalButton(root, 'Подтвердить блокировку геоотметок') === undefined).toBe(true)
    expect(fixture.api.setHeadmanBlock).toHaveBeenCalledTimes(1)
  })
})
