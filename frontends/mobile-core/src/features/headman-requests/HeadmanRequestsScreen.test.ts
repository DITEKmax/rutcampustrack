import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import HeadmanRequestsScreen from './HeadmanRequestsScreen.vue'
import {
  HeadmanRequestsApi,
  type HeadmanRequestDetail,
  type HeadmanRequestLesson,
  type HeadmanRequestSummary,
} from './headman-requests-client'

class Node {
  children: Node[] = []
  parent: Node | null = null
  attrs: Record<string, unknown> = {}
  constructor(
    readonly tag: string,
    public text = '',
  ) {
    markRaw(this)
  }
  focus() {}
}
const renderer = createRenderer<Node, Node>({
  createElement: (tag) => new Node(tag),
  createText: (text) => new Node('#text', text),
  createComment: () => new Node('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.attrs[key] = value
  },
  remove: (node) => {
    if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node)
    node.parent = null
  },
  insert: (node, parent, anchor) => {
    if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
})
const apps: App[] = []
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

function find(root: Node, predicate: (node: Node) => boolean): Node[] {
  return [
    ...(predicate(root) ? [root] : []),
    ...root.children.flatMap((child) => find(child, predicate)),
  ]
}
function byAttr(root: Node, attr: string, value: unknown): Node[] {
  return find(root, (node) => node.attrs[attr] === value)
}
function text(node: Node | undefined): string {
  return node ? node.text + node.children.map(text).join('') : ''
}
function button(root: Node, copy: string): Node {
  const match = find(root, (node) => node.tag === 'button' && text(node).trim().startsWith(copy))[0]
  if (!match) throw new Error(`No button: ${copy}`)
  return match
}
async function press(node: Node | undefined): Promise<void> {
  if (!node || node.attrs.disabled === true || typeof node.attrs.onClick !== 'function')
    throw new Error('Button is unavailable')
  node.attrs.onClick()
  await settle()
}
async function settle(): Promise<void> {
  for (let i = 0; i < 24; i++) await nextTick()
}

function summary(
  id: string,
  name = 'Андреев Пётр',
  studentId: number | null = 7,
  kind: HeadmanRequestSummary['kind'] = 'EXCUSE',
  status: HeadmanRequestSummary['status'] = 'PENDING',
): HeadmanRequestSummary {
  return {
    id,
    kind,
    status,
    studentId,
    studentName: name,
    reason: kind === 'EXCUSE' ? 'ILLNESS' : 'LATE_CHECKIN',
    comment: null,
    coverageStart: '2026-09-02',
    coverageEnd: '2026-09-04',
    lessonCount: kind === 'EXCUSE' ? 3 : 1,
    alreadyMarkedCount: 1,
    hasAttachments: false,
    createdAt: null,
    updatedAt: null,
    decisionBy: null,
    decisionAt: null,
    decisionComment: null,
  }
}
function lesson(id: number, date: string, attendanceStatus: string | null): HeadmanRequestLesson {
  return {
    occurrenceId: String(id + 100),
    occurrenceRevision: '1',
    lessonId: id,
    groupId: 4,
    subjectId: 3,
    subjectName: 'Математика',
    subjectType: 'LECTURE',
    semesterId: 1,
    lessonNumber: 1,
    date,
    startsAt: '08:00:00',
    endsAt: '09:30:00',
    requestLessonStatus: 'closed',
    attendanceStatus,
    attendanceSource: null,
  }
}
const json = (value: unknown) => new Response(JSON.stringify(value))
function mount(fetcher: typeof fetch): Node {
  const root = new Node('root')
  const api = markRaw(new HeadmanRequestsApi({ accessToken: () => null, fetcher }))
  const app = renderer.createApp({
    render: () => h(HeadmanRequestsScreen, { api, showBack: false }),
  })
  app.mount(root)
  apps.push(app)
  return root
}

describe('заявки старосты', () => {
  it('объединяет страницы по ID заявки и группирует по ID студента; типы работают внутри архива', async () => {
    const multi = summary('multi')
    const late = summary('late', 'Андреев Пётр', 7, 'LATE_CHECKIN')
    const unknown = [
      summary('unknown-1', 'Яковлев Иван', null),
      summary('unknown-2', 'Яковлев Иван', null),
    ]
    const queue = [
      [summary('other', 'Борисов Олег', 11), multi, unknown[0]],
      [multi, late, unknown[1]],
    ]
    const archive = [
      summary('approved', 'Андреев Пётр', 7, 'EXCUSE', 'APPROVED'),
      summary('rejected', 'Андреев Пётр', 7, 'LATE_CHECKIN', 'REJECTED'),
    ]
    const urls: URL[] = []
    const root = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture.local')
      urls.push(url)
      const { searchParams } = url
      const type = searchParams.get('type')
      const page = Number(searchParams.get('page'))
      const archived = searchParams.get('bucket') === 'ARCHIVE'
      const content = (archived ? archive : (queue[page] ?? [])).filter(
        (item) => item && (!type || item.kind === type),
      )
      return json({
        content,
        page,
        totalPages: archived ? 1 : 2,
        totalElements: archived ? content.length : 5,
      })
    })
    await settle()
    await press(button(root, 'Показать ещё'))
    expect(find(root, (node) => node.tag === 'h2').map(text)).toEqual([
      'Андреев Пётр',
      'Борисов Олег',
      'Яковлев Иван',
      'Яковлев Иван',
    ])
    const andreev = byAttr(root, 'aria-labelledby', 'headman-request-group-student:7')[0]!
    expect(
      find(andreev, (node) => node.tag === 'button').map((node) => node.attrs['data-request-id']),
    ).toEqual(['multi', 'late'])
    const excuseCard = byAttr(andreev, 'data-request-id', 'multi')[0]!
    const lateCard = byAttr(andreev, 'data-request-id', 'late')[0]!
    const indicators = [
      byAttr(excuseCard, 'data-kind', 'EXCUSE')[0],
      byAttr(lateCard, 'data-kind', 'LATE_CHECKIN')[0],
    ]
    expect(indicators.map((node) => node?.attrs['aria-hidden'])).toEqual(['true', 'true'])
    expect(indicators.map(text)).toEqual(['', ''])
    expect(text(excuseCard)).toContain('Уважительная причина')
    expect(text(lateCard)).toContain('Забыл отметиться')
    expect(urls.every((url) => url.pathname === '/api/attendance/requests')).toBe(true)

    await press(button(root, 'Архив'))
    expect(byAttr(root, 'data-request-view', 'archive')).toHaveLength(1)
    await press(button(root, 'уУважительные причины'))
    expect(byAttr(root, 'data-request-id', 'approved').map(text).join()).toContain('Одобрена')
    expect(byAttr(root, 'data-request-id', 'rejected')).toHaveLength(0)
    expect(urls.at(-1)?.searchParams.get('bucket')).toBe('ARCHIVE')
    await press(button(root, 'нЗабыл отметиться'))
    expect(byAttr(root, 'data-request-id', 'rejected').map(text).join()).toContain('Отклонена')
    expect(urls.at(-1)?.searchParams.get('bucket')).toBe('ARCHIVE')
    await press(byAttr(root, 'aria-label', 'Назад')[0])
    expect(byAttr(root, 'data-request-view', 'queue')).toHaveLength(1)
  })

  it.each([
    { scenario: 'без квитанции', hasReceipt: false },
    { scenario: 'с квитанцией без снимка «До»', hasReceipt: true },
  ])('показывает только имеющиеся архивные отметки $scenario', async ({ hasReceipt }) => {
    const archived = summary('legacy', 'Андреев Пётр', 7, 'EXCUSE', 'APPROVED')
    const result = lesson(2, '2026-09-03', 'EXCUSED')
    const detail: HeadmanRequestDetail = {
      summary: archived,
      lessons: [{ ...lesson(1, '2026-09-02', 'PRESENT'), subjectName: 'Исходный предмет' }],
      currentAffectedLessons: [{ ...result, attendanceStatus: 'ABSENT' }],
      attachments: [],
      decisionSelection: hasReceipt
        ? {
            requestKey: 'legacy-key',
            expectedRevision: 'a'.repeat(64),
            includedLessons: [result],
            excludedLessons: [],
          }
        : null,
    }
    const root = mount(async (input) => {
      const url = new URL(String(input), 'http://fixture.local')
      if (url.pathname.endsWith('/legacy')) return json(detail)
      const content = url.searchParams.get('bucket') === 'ARCHIVE' ? [archived] : []
      return json({ content, page: 0, totalPages: 1, totalElements: content.length })
    })
    await settle()
    await press(button(root, 'Архив'))
    await press(byAttr(root, 'data-request-id', 'legacy')[0])

    expect(byAttr(root, 'data-comparison', 'before')).toHaveLength(0)
    const after = byAttr(root, 'data-comparison', 'after')
    expect(after).toHaveLength(hasReceipt ? 1 : 0)
    expect(text(root)).not.toContain('не сохранены')
    expect(text(root)).not.toContain('Исходные пары заявки')
    expect(text(root)).not.toContain('Исходный предмет')
    if (hasReceipt) {
      expect(byAttr(after[0]!, 'data-status', 'EXCUSED')).toHaveLength(1)
      expect(text(root)).not.toContain('Текущие отметки в журнале')
    } else {
      const journal = find(
        root,
        (node) => node.tag === 'h2' && text(node).trim() === 'Текущие отметки в журнале',
      )[0]!.parent!
      expect(byAttr(journal, 'data-status', 'ABSENT')).toHaveLength(1)
    }
  })

  it('использует текущие цели для прогноза и точную квитанцию при повторном открытии', async () => {
    const current = [
      lesson(21, '2026-09-11', 'ABSENT'),
      lesson(22, '2026-09-11', 'PRESENT'),
      lesson(23, '2026-09-12', null),
    ]
    const saved = [
      lesson(1, '2026-09-02', 'PRESENT'),
      lesson(2, '2026-09-03', 'PRESENT'),
      lesson(3, '2026-09-04', 'PRESENT'),
    ]
    let detail: HeadmanRequestDetail = {
      summary: summary('ticket'),
      currentAffectedLessons: current,
      decisionRevision: 'a'.repeat(64),
      lessons: saved,
      attachments: [],
    }
    const commands: unknown[] = []
    const root = mount(async (input, init) => {
      const url = new URL(String(input), 'http://fixture.local')
      if (url.pathname.endsWith('/decision')) {
        const body = JSON.parse(String(init?.body))
        commands.push(body)
        detail = {
          ...detail,
          summary: { ...detail.summary, status: 'APPROVED' },
          currentAffectedLessons: null,
          decisionSelection: {
            requestKey: body.requestKey,
            expectedRevision: body.expectedRevision,
            beforeLessons: [current[2]!, current[0]!, current[1]!],
            includedLessons: [{ ...current[0]!, attendanceStatus: 'EXCUSED' }],
            excludedLessons: [current[1]!, current[2]!],
          },
        }
        return json(detail)
      }
      if (url.pathname.endsWith('/ticket')) return json(detail)
      const archived = url.searchParams.get('bucket') === 'ARCHIVE'
      const content = archived === (detail.summary.status === 'APPROVED') ? [detail.summary] : []
      return json({ content, page: 0, totalPages: 1, totalElements: content.length })
    })
    await settle()
    await press(byAttr(root, 'data-request-id', 'ticket')[0])
    const exclusionButtons = () =>
      find(
        root,
        (node) =>
          typeof node.attrs['aria-label'] === 'string' &&
          /^(Исключить|Вернуть) пару/.test(node.attrs['aria-label']),
      )
    expect(exclusionButtons().map((node) => node.attrs['aria-label'])).toEqual([
      'Исключить пару: Математика, 11 сентября, 08:00–09:30',
      'Исключить пару: Математика, 11 сентября, 08:00–09:30',
      'Исключить пару: Математика, 12 сентября, 08:00–09:30',
    ])
    await press(exclusionButtons()[1])
    await press(exclusionButtons()[2])
    expect(exclusionButtons()[0]?.attrs.disabled).toBe(true)
    await press(button(root, 'Одобрить'))
    const before = () => byAttr(root, 'data-comparison', 'before')[0]!
    const after = () => byAttr(root, 'data-comparison', 'after')[0]!
    expect(text(before())).toContain('Текущие отметки')
    expect(text(after())).toContain('Прогноз')
    expect(byAttr(after(), 'data-status', 'PRESENT')).toHaveLength(1)
    expect(byAttr(after(), 'data-status', 'EXCUSED')).toHaveLength(1)

    await press(button(root, 'Одобрить 1 пару'))
    expect(commands).toEqual([
      {
        decision: 'APPROVED',
        expectedRevision: 'a'.repeat(64),
        requestKey: expect.any(String),
        excludedOccurrenceIds: ['122', '123'],
      },
    ])
    const dismiss = byAttr(root, 'aria-label', 'Закрыть уведомление')[0]
    expect(dismiss?.parent?.parent?.attrs['data-tone']).toBe('success')
    await press(dismiss)
    expect(byAttr(root, 'aria-label', 'Закрыть уведомление')).toHaveLength(0)
    const beforeAfter = [text(before()), text(after())]
    expect(beforeAfter[0]).toContain('Отметки перед решением')
    expect(beforeAfter[1]?.match(/Исключена · без изменений/g)).toHaveLength(2)
    expect(byAttr(before(), 'data-status', 'ABSENT')).toHaveLength(1)
    expect(byAttr(after(), 'data-status', 'EXCUSED')).toHaveLength(1)
    expect(text(root)).toContain('Исходный период: 2 сентября — 4 сентября')
    expect(text(root)).toContain('Исходные пары заявки')
    await press(byAttr(root, 'aria-label', 'Назад')[0])
    await press(button(root, 'Архив'))
    await press(byAttr(root, 'data-request-id', 'ticket')[0])
    expect([text(before()), text(after())]).toEqual(beforeAfter)
    expect(commands).toHaveLength(1)
  })
})
