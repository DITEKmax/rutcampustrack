/// <reference types="vite/client" />
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compile, createRenderer, h, nextTick, shallowReactive, ssrContextKey, type App } from 'vue'
import { compileScript, parse } from '@vue/compiler-sfc'
import HeadmanScheduleScreen from '../schedule/HeadmanScheduleScreen.vue'
import scheduleSource from '../schedule/HeadmanScheduleScreen.vue?raw'
import HeadmanHomeworkScreen from './HeadmanHomeworkScreen.vue'
import type { HeadmanSubjectsApi } from '../headman-subjects/headman-subjects-client'
import homeworkSource from './HeadmanHomeworkScreen.vue?raw'
import MobileShell from '../../shared/components/MobileShell.vue'
import shellSource from '../../shared/components/MobileShell.vue?raw'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import warningSource from '../../shared/components/StudentWarningBlock.vue?raw'
import MobileBottomNav from '../../shared/components/MobileBottomNav.vue'
import navSource from '../../shared/components/MobileBottomNav.vue?raw'
import HeadmanMoreScreen from '../headman-home/HeadmanMoreScreen.vue'
import moreSource from '../headman-home/HeadmanMoreScreen.vue?raw'
import HeadmanHomeScreen from '../headman-home/HeadmanHomeScreen.vue'
import type { HeadmanJournalApi } from '../headman-journal/headman-journal-client'
import type { MobileHostAdapter } from '../../shared/host'
import {
  HeadmanHomeworkApiError,
  type HeadmanHomeworkApi,
  type HeadmanHomeworkCreateInput,
  type HeadmanHomeworkCreateResult,
  type HeadmanHomeworkPublicationReceipt,
  type HeadmanHomeworkUpdateInput,
  type HeadmanManagedHomework,
} from './headman-homework-client'

// Use the existing custom renderer approach with the real parent, shell and
// form. Node imports SSR setup, so compile unchanged templates for interaction.
for (const [component, source] of [
  [HeadmanScheduleScreen, scheduleSource],
  [HeadmanHomeworkScreen, homeworkSource],
  [MobileShell, shellSource],
  [MobileBottomNav, navSource],
  [HeadmanMoreScreen, moreSource],
  [StudentWarningBlock, warningSource],
] as const) {
  const descriptor = parse(source).descriptor
  component.render = compile(descriptor.template!.content, {
    bindingMetadata: compileScript(descriptor, { id: 'homework-back-test' }).bindings!,
    prefixIdentifiers: true,
  })
}
// The initial Today content is outside this regression; its real dock remains mounted.
HeadmanHomeScreen.render = () => h('section')

interface Node {
  tag: string
  tagName: string
  text: string
  value: unknown
  selected: boolean
  props: Record<string, unknown>
  children: Node[]
  parent: Node | null
  options: Node[]
  addEventListener: () => void
  getRootNode: () => { activeElement: null }
}
function node(tag = ''): Node {
  return {
    tag,
    tagName: tag.toUpperCase(),
    text: '',
    value: '',
    selected: false,
    props: {},
    children: [],
    parent: null,
    get options() {
      return this.children.filter((child) => child.tag === 'option')
    },
    addEventListener: () => undefined,
    getRootNode: () => ({ activeElement: null }),
  }
}
function remove(node: Node): void {
  if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node)
  node.parent = null
}
const renderer = createRenderer<Node, Node>({
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor) => {
    remove(node)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove,
  createElement: node,
  createText: (text) => ({ ...node('#text'), text }),
  createComment: (text) => ({ ...node('#comment'), text }),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
})
const nodes = (node: Node): Node[] => [node, ...node.children.flatMap(nodes)]
const text = (node: Node): string =>
  node.tag === '#comment' ? '' : node.text + node.children.map(text).join('')
async function settle(): Promise<void> {
  for (let index = 0; index < 32; index += 1) await Promise.resolve()
  await nextTick()
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (cause: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
})

async function mount(hostBack = false, editing = false) {
  vi.stubGlobal('Document', class {})
  vi.stubGlobal('ShadowRoot', class {})
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
  const existing: HeadmanManagedHomework = {
    id: 91,
    bindingId: 501,
    requestKey: null,
    title: 'Исходное ДЗ',
    description: '',
    link: null,
    subjectId: 88,
    groupId: 7,
    semesterId: 12,
    publishedBy: 41,
    lessonDate: today,
    lessonNumber: 1,
    lessonType: 'LECTURE',
    bindingMode: 'LESSON',
    revision: 4,
    archived: false,
  }
  const records = new Map<string, HeadmanManagedHomework>()
  const creates: {
    input: HeadmanHomeworkCreateInput
    receipt: HeadmanHomeworkPublicationReceipt | null
  }[] = []
  const edits: HeadmanHomeworkUpdateInput[] = []
  let createResponse = deferred<HeadmanHomeworkCreateResult>()
  let editResponse = deferred<HeadmanManagedHomework>()
  const api = {
    activeSemester: async () => ({
      id: 12,
      name: 'Семестр',
      dateFrom: null,
      dateTo: null,
      active: true,
    }),
    listHomeworks: async () => (editing ? [existing] : [...records.values()]),
    listSubjects: async () => [{ id: 88, name: 'Предмет' }],
    nextHomeworkLesson: async () => ({
      id: 300,
      occurrenceId: '700',
      occurrenceRevision: '1',
      groupId: 7,
      subjectId: 88,
      date: today,
      status: 'PLANNED',
      lessonNumber: 1,
      startTime: '23:00',
      endTime: '23:59',
      room: null,
      lessonType: 'LECTURE',
      current: true,
      transferOperationId: null,
      transferState: null,
    }),
    getHomework: async () => ({ ...existing, revision: 5 }),
    createHomework: (
      input: HeadmanHomeworkCreateInput,
      receipt: HeadmanHomeworkPublicationReceipt | null,
    ) => {
      creates.push({ input, receipt })
      // A second UUID creates another server record, even with identical text.
      if (!records.has(input.requestKey))
        records.set(input.requestKey, { ...existing, ...input, id: 91 + records.size })
      return createResponse.promise
    },
    updateHomework: (_id: number, input: HeadmanHomeworkUpdateInput) => {
      edits.push(input)
      return editResponse.promise
    },
  } as unknown as HeadmanHomeworkApi
  const journalApi = {
    listLessons: async (_group: number, from: string, to: string) =>
      today >= from && today <= to
        ? [
            {
              id: 300,
              groupId: 7,
              subjectId: 88,
              date: today,
              status: 'PLANNED',
              lessonNumber: 1,
              startTime: '23:00',
              endTime: '23:59',
              room: null,
              lessonType: 'LECTURE',
              occurrenceId: '700',
              occurrenceRevision: '1',
              current: true,
              transferOperationId: null,
              transferState: null,
            },
          ]
        : [],
  } as unknown as HeadmanJournalApi
  let hostListener: (() => void) | null = null
  const host: MobileHostAdapter = {
    backOwner: hostBack ? 'host' : 'product',
    subscribeBack: (listener) => {
      hostListener = listener
      return () => {
        hostListener = null
      }
    },
  }
  const subjectsApi = {
    listSubjects: async () => [
      {
        id: 88,
        name: 'Предмет',
        groupId: 7,
        type: 'LECTURE',
        lessonTypes: ['LECTURE'],
        teacherIds: [41],
        assignments: [
          {
            id: 1,
            teacherId: 41,
            semesterId: 12,
            lessonType: 'LECTURE',
            validFrom: '2026-01-01',
            validUntilExclusive: null,
          },
        ],
      },
    ],
    listSemesters: async () => [
      { id: 12, name: 'Семестр', dateFrom: '2026-01-01', dateTo: '2027-12-31', active: true },
    ],
  } as unknown as HeadmanSubjectsApi
  const props = shallowReactive({
    subjectsApi,
    api: null,
    journalApi,
    homeworkApi: api,
    homeworkActorUserId: 41,
    groupId: 7,
    profile: null,
    host,
    readOnly: false,
    offline: false,
  })
  const root = node('root')
  const app = renderer.createApp({ render: () => h(HeadmanScheduleScreen, props) })
  app.provide(ssrContextKey, { modules: new Set() })
  apps.push(app)
  app.mount(root)
  const button = (label: string) => {
    const result = nodes(root).find(
      (node) =>
        node.tag === 'button' &&
        (node.props['aria-label'] === label || text(node).trim() === label),
    )
    if (!result) throw new Error(`Button ${label} missing: ${text(root)}`)
    return result
  }
  const click = async (label: string) => {
    const target = button(label)
    expect(target.props.disabled, `${label}: ${text(root)}`).not.toBe(true)
    ;(target.props.onClick as () => void)()
    await settle()
  }
  const back = async () => {
    if (hostBack) hostListener!()
    else {
      const backButton = nodes(root).find(
        (node) => node.tag === 'button' && node.props['aria-label'] === 'Назад',
      )
      if (backButton) (backButton.props.onClick as () => void)()
      else await click('Ещё')
    }
    await settle()
  }
  const submit = async () => {
    const form = nodes(root).find((node) => node.tag === 'form')!
    ;(form.props.onSubmit as (event: { preventDefault: () => void }) => void)({
      preventDefault: () => undefined,
    })
    await settle()
  }
  const titleField = () =>
    nodes(root).find((node) => node.tag === 'input' && node.props.maxlength === '255')!
  const open = async () => {
    await click('Домашнее задание')
    if (editing) {
      const item = nodes(root).find(
        (node) =>
          node.tag === 'button' && String(node.props.class).includes('headman-homework__item'),
      )!
      ;(item.props.onClick as () => void)()
      await settle()
      await click('Изменить задание')
    } else {
      await click('Выдать ДЗ')
      await click('Предмет')
    }
    await click('Продолжить')
    const type = nodes(root).find(
      (node) => node.tag === 'button' && text(node).trim().startsWith('Лекция'),
    )!
    ;(type.props.onClick as () => void)()
    await settle()
    await click('Продолжить')
    await click('Продолжить')
    ;(titleField().props['onUpdate:modelValue'] as (value: string) => void)('Точный вариант')
    await settle()
  }
  const exitDraft = async () => {
    for (
      let i = 0;
      i < 5 &&
      nodes(root).some((node) => node.props['aria-labelledby'] === 'headman-homework-title');
      i += 1
    )
      await back()
  }
  await settle()
  await click('Ещё')
  await open()
  return {
    root,
    props,
    click,
    back,
    exitDraft,
    submit,
    open,
    titleField,
    records,
    creates,
    edits,
    chooseCurrentDate: async () => {
      const month = nodes(root).find((item) => item.props.class === 'headman-homework__month')!
      const target = month.children.find(
        (item) => item.tag === 'button' && item.props.disabled === false,
      )!
      ;(target.props.onClick as () => void)()
      await settle()
    },
    createResponse: () => createResponse,
    nextCreate: () => {
      createResponse = deferred<HeadmanHomeworkCreateResult>()
      return createResponse
    },
    editResponse: () => editResponse,
    nextEdit: () => {
      editResponse = deferred<HeadmanManagedHomework>()
      return editResponse
    },
    existing,
  }
}

describe('homework mutation across real parent navigation and form', () => {
  it.each([
    ['uncertain', 400],
    ['uncertain', 422],
    ['pending', 400],
    ['pending', 422],
  ] as const)(
    'retains %s CREATE identity/receipt after a later %s rejection',
    async (history, status) => {
      const screen = await mount()
      await screen.submit()
      const original = screen.creates[0]!.input
      const receipt = { homeworkId: '91', bindingId: '501', requestKey: original.requestKey }
      if (history === 'pending') screen.createResponse().resolve({ state: 'PENDING', receipt })
      else screen.createResponse().reject(new TypeError('Ответ потерян'))
      await settle()
      screen.nextCreate()
      await screen.submit()
      screen
        .createResponse()
        .reject(new HeadmanHomeworkApiError(new Response(null, { status }), null))
      await settle()
      await screen.back()
      expect(screen.titleField().value).toBe('Точный вариант')
      expect(screen.titleField().props.disabled).toBe(true)
      screen.nextCreate()
      await screen.submit()
      expect(screen.creates.at(-1)!.input).toBe(original)
      if (history === 'pending') expect(screen.creates.at(-1)!.receipt).toEqual(receipt)
      expect(screen.records.size).toBe(1)
    },
  )

  it('retains an uncertain publication across same-owner offline/online and retries its original identity', async () => {
    const screen = await mount()
    await screen.submit()
    screen.createResponse().reject(new TypeError('Ответ потерян'))
    await settle()
    const original = screen.creates[0]!.input
    screen.props.offline = true
    await settle()
    expect(text(screen.root)).toContain('Ты офлайн')
    expect(nodes(screen.root).some((item) => item.tag === 'form')).toBe(false)
    await screen.back()
    screen.props.offline = false
    await settle()
    expect(screen.titleField().value).toBe('Точный вариант')
    screen.nextCreate()
    await screen.submit()
    expect(screen.creates.at(-1)!.input).toBe(original)
  })

  it.each([false, true])(
    'only a fresh typed rejection permits explicitly confirmed reselection (ever uncertain=%s)',
    async (uncertain) => {
      const screen = await mount()
      await screen.submit()
      if (uncertain) {
        screen.createResponse().reject(new TypeError('Ответ потерян'))
        await settle()
        screen.nextCreate()
        await screen.submit()
      }
      screen.createResponse().reject(
        new HeadmanHomeworkApiError(new Response(null, { status: 409 }), {
          extras: {
            code: 'HOMEWORK_SELECTION_STALE',
            admission: 'CURRENT_ATTEMPT_REJECTED_BEFORE_ADMISSION',
          },
        }),
      )
      await settle()
      const original = screen.creates[0]!.input
      await screen.click(uncertain ? 'Проверить исходную публикацию' : 'Выбрать актуальную пару')
      if (!uncertain) {
        expect(text(screen.root)).toContain('Шаг 3 из 4')
        await screen.chooseCurrentDate()
        await screen.click('Продолжить')
      }
      expect(screen.titleField().value).toBe('Точный вариант')
      screen.nextCreate()
      await screen.submit()
      const retried = screen.creates.at(-1)!.input
      if (uncertain) expect(retried).toBe(original)
      else {
        expect(retried.requestKey).not.toBe(original.requestKey)
        expect(retried).toMatchObject({
          selectedOccurrenceId: '700',
          selectedOccurrenceRevision: '1',
          title: 'Точный вариант',
        })
      }
    },
  )

  it.each([false, true])(
    'retains delayed/lost CREATE and PENDING receipt until same-key completion (host Back=%s)',
    async (hostBack) => {
      const screen = await mount(hostBack)
      await screen.submit()
      await screen.back()
      expect(screen.titleField().value).toBe('Точный вариант')
      expect(text(screen.root)).toContain('Дождись ответа сервера')
      expect(screen.titleField().props.disabled).toBe(true)
      screen.createResponse().reject(new TypeError('Ответ потерян после принятия сервером'))
      await settle()
      await screen.back()
      expect(screen.titleField().value).toBe('Точный вариант')
      expect(text(screen.root)).toContain('Повторить публикацию')
      screen.nextCreate()
      await screen.submit()
      const receipt = {
        homeworkId: '91',
        bindingId: '501',
        requestKey: screen.creates[0]!.input.requestKey,
      }
      screen.createResponse().resolve({ state: 'PENDING', receipt })
      await settle()
      expect(text(screen.root)).toContain('Публикация выполняется')
      await screen.back()
      expect(screen.titleField().props.disabled).toBe(true)
      screen.nextCreate()
      await screen.submit()
      expect(screen.creates[1]!.input).toBe(screen.creates[0]!.input)
      expect(screen.creates[2]!.input).toBe(screen.creates[0]!.input)
      expect(screen.creates[2]!.receipt).toEqual(receipt)
      expect(screen.records.size).toBe(1)
      screen
        .createResponse()
        .resolve({ state: 'ACTIVE', homework: [...screen.records.values()][0]! })
      await settle()
      await screen.back()
      expect(text(screen.root)).toContain('Конструктор расписания')
      expect(nodes(screen.root).some((node) => node.tag === 'form')).toBe(false)
      await screen.click('Домашнее задание')
      await screen.back()
      expect(text(screen.root)).toContain('Конструктор расписания')
    },
  )

  it.each(['lost', '503'])(
    'retains EDIT key/body/revision after %s even when the later retry returns generic409',
    async (history) => {
      const screen = await mount(true, true)
      await screen.submit()
      await screen.back()
      screen
        .editResponse()
        .reject(
          history === 'lost'
            ? new TypeError('Ответ редактирования потерян')
            : new HeadmanHomeworkApiError(new Response(null, { status: 503 }), null),
        )
      await settle()
      await screen.back()
      expect(screen.titleField().value).toBe('Точный вариант')
      screen.nextEdit()
      await screen.submit()
      expect(screen.edits[1]).toBe(screen.edits[0])
      expect(screen.edits[1]).toMatchObject({ title: 'Точный вариант', expectedRevision: 4 })
      screen
        .editResponse()
        .reject(new HeadmanHomeworkApiError(new Response(null, { status: 409 }), null))
      await settle()
      await screen.click('Проверить исходное изменение')
      await screen.back()
      expect(screen.titleField().props.disabled).toBe(true)
      screen.nextEdit()
      await screen.submit()
      expect(screen.edits.at(-1)).toBe(screen.edits[0])
      expect(screen.edits.at(-1)).toMatchObject({ title: 'Точный вариант', expectedRevision: 4 })
      screen.editResponse().resolve(screen.existing)
      await settle()
      await screen.back()
      expect(text(screen.root)).toContain('Конструктор расписания')
    },
  )

  it('allows explicit current-version recovery after a first definitive EDIT revision409', async () => {
    const screen = await mount(true, true)
    await screen.submit()
    screen
      .editResponse()
      .reject(new HeadmanHomeworkApiError(new Response(null, { status: 409 }), null))
    await settle()
    await screen.click('Использовать текущую версию и проверить мой вариант')
    expect(text(screen.root)).toContain('Твой текст сохранён')
    await screen.chooseCurrentDate()
    await screen.click('Продолжить')
    screen.nextEdit()
    await screen.submit()
    expect(screen.edits.at(-1)!.requestKey).not.toBe(screen.edits[0]!.requestKey)
    expect(screen.edits.at(-1)).toMatchObject({ title: 'Точный вариант', expectedRevision: 5 })
    screen.editResponse().resolve(screen.existing)
    await settle()
    await screen.back()
    expect(text(screen.root)).toContain('Конструктор расписания')
  })

  it('allows Back for an unsent draft or definitive validation rejection', async () => {
    const screen = await mount()
    await screen.back()
    expect(text(screen.root)).toContain('Шаг 3 из 4')
    await screen.exitDraft()
    expect(text(screen.root)).toContain('Конструктор расписания')
    await screen.open()
    await screen.submit()
    screen
      .createResponse()
      .reject(new HeadmanHomeworkApiError(new Response(null, { status: 422 }), null))
    await settle()
    await screen.exitDraft()
    expect(text(screen.root)).toContain('Конструктор расписания')
  })

  it('clears protected data and releases Back on a denied mutation without waiting for the parent refresh', async () => {
    const screen = await mount()
    await screen.submit()
    screen
      .createResponse()
      .reject(new HeadmanHomeworkApiError(new Response(null, { status: 403 }), null))
    await settle()
    expect(text(screen.root)).toContain('Раздел недоступен')
    expect(text(screen.root)).not.toContain('Точный вариант')
    expect(nodes(screen.root).some((node) => node.tag === 'form')).toBe(false)
    await screen.back()
    expect(text(screen.root)).toContain('Конструктор расписания')
  })

  it.each(['readOnly', 'actor', 'api'] as const)(
    'clears protected intent on %s revocation and ignores its late answer without leaking a guard',
    async (change) => {
      const screen = await mount()
      await screen.submit()
      const originalApi = screen.props.homeworkApi
      if (change === 'readOnly') screen.props.readOnly = true
      else if (change === 'actor') screen.props.homeworkActorUserId = 42
      else screen.props.homeworkApi = null as unknown as HeadmanHomeworkApi
      await settle()
      expect(nodes(screen.root).some((node) => node.tag === 'form')).toBe(false)
      screen.createResponse().resolve({
        state: 'PENDING',
        receipt: {
          homeworkId: '91',
          bindingId: '501',
          requestKey: screen.creates[0]!.input.requestKey,
        },
      })
      await settle()
      expect(text(screen.root)).not.toContain('Сервер принял публикацию')
      await screen.back()
      expect(text(screen.root)).toContain('Конструктор расписания')
      screen.props.readOnly = false
      screen.props.homeworkApi = originalApi
      await settle()
      if (
        !nodes(screen.root).some(
          (node) => node.tag === 'button' && text(node).trim() === 'Домашнее задание',
        )
      )
        await screen.click('Ещё')
      await screen.click('Домашнее задание')
      await screen.back()
      expect(text(screen.root)).toContain('Конструктор расписания')
    },
  )
})
