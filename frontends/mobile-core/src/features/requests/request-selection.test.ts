import { createRenderer, createSSRApp, h, nextTick, ref } from 'vue'
import { renderToString } from '@vue/server-renderer'
import RequestLessonSelectionScreen from './RequestLessonSelectionScreen.vue'
import RequestLessonSelector from './RequestLessonSelector.vue'
import RequestDateCalendar from './RequestDateCalendar.vue'
import ExcuseRequestScreen from './ExcuseRequestScreen.vue'
import HeadmanRequestsScreen from '../headman-requests/HeadmanRequestsScreen.vue'
import { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getOrCreateRequestsDraft,
  purgeRequestsDrafts,
  readRequestsDraft,
  requestSelectionEligible,
  updateRequestsDraft,
  removeSelectedLessonId,
} from './state'
import type { RequestLessonOption } from './types'
// This flow exercises source selection/draft navigation; DOM-only measurement and
// outside-pointer behavior of the unchanged reason/text controls are independent.
vi.mock('./AutoGrowTextarea.vue', () => ({
  default: {
    props: ['modelValue'],
    setup: (props: { modelValue: string }) => () => h('textarea', { value: props.modelValue }),
  },
}))
vi.mock('./ReasonSelect.vue', () => ({
  default: {
    props: ['modelValue'],
    setup: (props: { modelValue: string | null }) => () => h('span', props.modelValue ?? ''),
  },
}))
const options: RequestLessonOption[] = [
  { lesson: { id: 'one' }, excuseEligible: true, lateCheckinEligible: true, pendingRequests: [] },
  {
    lesson: { id: 'two' },
    excuseEligible: true,
    lateCheckinEligible: true,
    pendingRequests: [{ id: 'other', kind: 'LATE_CHECKIN' }],
  },
  { lesson: { id: 'blocked' }, excuseEligible: false, lateCheckinEligible: false },
  {
    lesson: { id: 'pending' },
    excuseEligible: true,
    lateCheckinEligible: true,
    pendingRequests: [{ id: 'own', kind: 'EXCUSE' }],
  },
]
afterEach(() => purgeRequestsDrafts())
describe('separate lesson selection and retained drafts', () => {
  it('allows eligible multi excuse and exactly one late, honoring per-kind pending requests', () => {
    expect(requestSelectionEligible(options, ['one', 'two'], 'EXCUSE')).toBe(true)
    expect(requestSelectionEligible(options, ['two'], 'LATE_CHECKIN')).toBe(false)
    expect(requestSelectionEligible(options, ['pending'], 'EXCUSE')).toBe(false)
    expect(requestSelectionEligible(options, ['pending'], 'LATE_CHECKIN')).toBe(true)
    expect(requestSelectionEligible(options, ['one', 'pending'], 'LATE_CHECKIN')).toBe(false)
    expect(requestSelectionEligible(options, [], 'EXCUSE')).toBe(false)
    expect(requestSelectionEligible(options, ['one', 'one'], 'EXCUSE')).toBe(false)
  })
  it('retains missing/ineligible selections until explicit removal, then permits continuation', () => {
    updateRequestsDraft('student', 'session', {
      view: 'select-excuse',
      excuseLessonIds: ['one', 'missing', 'blocked'],
    })
    const retained = getOrCreateRequestsDraft('student', 'session')
    expect(requestSelectionEligible(options, retained.excuseLessonIds, 'EXCUSE')).toBe(false)
    expect(retained.excuseLessonIds).toEqual(['one', 'missing', 'blocked'])
    const fixed = removeSelectedLessonId(
      removeSelectedLessonId(retained.excuseLessonIds, 'missing'),
      'blocked',
    )
    expect(requestSelectionEligible(options, fixed, 'EXCUSE')).toBe(true)
  })
  it('preserves reason, comment and files through selection edit/back without leaking owner or session', () => {
    updateRequestsDraft('student', 'session', {
      view: 'excuse',
      excuseLessonIds: ['one'],
      excuseReason: 'OTHER',
      excuseComment: 'Причина',
      excuseFiles: [{ id: 'proof', name: 'справка.pdf', size: 1, type: 'application/pdf' }],
    })
    updateRequestsDraft('student', 'session', { view: 'select-excuse' })
    updateRequestsDraft('student', 'session', { view: 'type' })
    updateRequestsDraft('student', 'session', {
      view: 'select-excuse',
      excuseLessonIds: ['one', 'two'],
    })
    const restored = updateRequestsDraft('student', 'session', { view: 'excuse' })
    expect(restored.excuseReason).toBe('OTHER')
    expect(restored.excuseComment).toBe('Причина')
    expect(restored.excuseFiles[0]?.id).toBe('proof')
    expect(readRequestsDraft('other', 'session')).toBeNull()
    expect(readRequestsDraft('student', 'new-session')).toBeNull()
  })
})

describe('lesson selection continuation boundary', () => {
  it('renders an enabled continuation only for the eligible selection and blocks stale/read-only/offline drafts', async () => {
    const render = (ids: string[], extra: { offline?: boolean; readOnly?: boolean } = {}) =>
      renderToString(
        createSSRApp(RequestLessonSelectionScreen, {
          kind: 'EXCUSE',
          lessons: options,
          lessonIds: ids,
          access: 'allowed',
          optionsLoaded: true,
          ...extra,
        }),
      )
    expect(await render(['one', 'two'])).not.toMatch(/class="requests-submit-action"[^>]*disabled/)
    expect(await render([])).not.toContain('class="requests-submit-action"')
    for (const ids of [['one', 'missing'], ['blocked'], ['pending']])
      expect(await render(ids)).toMatch(/class="requests-submit-action"[^>]*disabled/)
    expect(await render(['one'], { offline: true })).toMatch(
      /class="requests-submit-action"[^>]*disabled/,
    )
    expect(await render(['one'], { readOnly: true })).toMatch(
      /class="requests-submit-action"[^>]*disabled/,
    )
  })
})

describe('lesson options failure and recovery', () => {
  it('asserts an empty lesson list only after successful resolution and retains cached lessons while loading or failed', async () => {
    const cached: RequestLessonOption[] = [
      { lesson: { id: 'cached', subjectName: 'Сохранённая известная пара' }, excuseEligible: true },
    ]
    const render = (
      lessons: RequestLessonOption[],
      extra: {
        error?: string
        loading?: boolean
        optionsLoaded?: boolean
        offline?: boolean
        access?: 'forbidden' | 'no-active-semester'
      } = {},
    ) =>
      renderToString(
        createSSRApp(RequestLessonSelectionScreen, {
          kind: 'EXCUSE',
          lessons,
          lessonIds: ['cached'],
          access: 'allowed',
          optionsLoaded: true,
          ...extra,
        }),
      )
    expect(await render([])).toContain('В выбранные даты пар нет.')
    for (const state of [
      { error: 'Не удалось загрузить варианты' },
      { loading: true },
      { optionsLoaded: false },
      { offline: true },
      { access: 'forbidden' as const },
      { access: 'no-active-semester' as const },
    ]) {
      const unknown = await render([], state)
      expect(unknown).not.toContain('В выбранные даты пар нет.')
      expect(unknown).not.toContain('эти пары больше не пришли с сервера')
      expect(unknown).toContain('Твой выбор сохранён.')
      expect(unknown).toContain('Убрать из заявки')
      if (!('offline' in state || 'access' in state))
        expect(unknown).not.toMatch(/class="request-lesson-recovery-action"[^>]*disabled/)
      const retained = await render(cached, state)
      expect(retained).toContain('Сохранённая известная пара')
      expect(retained).toMatch(/type="checkbox"[^>]*checked[^>]*disabled/)
      expect(retained).toMatch(/class="requests-submit-action"[^>]*disabled/)
    }
  })
})

// Reuse the repository's Node/Vue custom-renderer pattern for actual input events.
interface SelectionNode {
  tag: string
  text: string
  props: Record<string, unknown>
  children: SelectionNode[]
  parent: SelectionNode | null
  value: string
  selected?: boolean
  readonly options: SelectionNode[]
  addEventListener: (name: string, listener: (event: Event) => void) => void
}
function selectionNode(tag: string, text = ''): SelectionNode {
  return {
    tag,
    text,
    props: {},
    children: [],
    parent: null,
    value: '',
    get options() {
      return this.children.filter((child) => child.tag === 'option')
    },
    addEventListener(name, listener) {
      this.props[`native:${name}`] = listener
    },
  }
}
const selectionRenderer = createRenderer<SelectionNode, SelectionNode>({
  createElement: selectionNode,
  createText: (text) => selectionNode('#text', text),
  createComment: (text) => selectionNode('#comment', text),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  patchProp: (node, key, _before, value) => {
    node.props[key] = value
    if (key === 'value' && typeof value === 'string') node.value = value
  },
  insert(node, parent, anchor) {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) parent.children.push(node)
    else parent.children.splice(index, 0, node)
  },
  remove(node) {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
})
function selectionNodes(node: SelectionNode): SelectionNode[] {
  return [node, ...node.children.flatMap(selectionNodes)]
}

describe('late-checkin source candidates and retained draft recovery', () => {
  const candidate = (
    id: string,
    eligible: boolean,
    pendingRequests: RequestLessonOption['pendingRequests'] = [],
  ): RequestLessonOption => ({
    lesson: { id, subjectName: id, date: '2026-09-02', status: eligible ? 'CLOSED' : 'PLANNED' },
    lateCheckinEligible: eligible,
    excuseEligible: true,
    pendingRequests,
  })
  it('lists only source-eligible candidates and blocks a refreshed stale selection until explicit recovery', async () => {
    const source = ref<RequestLessonOption[]>([
      candidate('absent', true),
      candidate('present', false),
      candidate('excused', false),
      candidate('future', false),
      candidate('same-kind-pending', true, [{ id: 'p', kind: 'LATE_CHECKIN' }]),
      candidate('other-kind-allowed', true, [{ id: 'e', kind: 'EXCUSE' }]),
    ])
    const selected = ref<string[]>([])
    const readOnly = ref(false)
    let continued = 0
    const root = selectionNode('root')
    const app = selectionRenderer.createApp({
      render: () =>
        h(RequestLessonSelectionScreen, {
          kind: 'LATE_CHECKIN',
          lessons: source.value,
          lessonIds: selected.value,
          access: 'allowed',
          optionsLoaded: true,
          readOnly: readOnly.value,
          'onUpdate:lessonIds': (ids: string[]) => {
            selected.value = ids
          },
          onContinue: () => {
            continued++
          },
        }),
    })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const choices = () => nodes().filter((node) => node.props.type === 'checkbox')
    const continueButton = () =>
      nodes().find((node) => node.props.class === 'requests-submit-action')!
    const remove = () =>
      nodes().find((node) => node.props.class === 'request-lesson-recovery-action')!
    const choose = async (id: string) => {
      const input = choices().find((node) => node.props.value === id)!
      ;(input.props.onChange as (event: unknown) => void)({ target: { checked: true } })
      await nextTick()
    }
    try {
      expect(choices().map((node) => node.props.value)).toEqual(['absent', 'other-kind-allowed'])
      await choose('absent')
      expect(continueButton().props.disabled).toBe(false)
      source.value = source.value.map((option) =>
        option.lesson?.id === 'absent'
          ? {
              ...option,
              lateCheckinEligible: false,
              unavailableReason: 'Отметка уже подтверждена.',
            }
          : option,
      )
      await nextTick()
      expect(selected.value).toEqual(['absent'])
      expect(choices().map((node) => node.props.value)).toEqual(['other-kind-allowed'])
      expect(nodes().some((node) => node.text.includes('Отметка уже подтверждена.'))).toBe(true)
      expect(remove()).toBeDefined()
      expect(continueButton().props.disabled).toBe(true)
      ;(continueButton().props.onClick as () => void)()
      expect(continued).toBe(0)
      readOnly.value = true
      await nextTick()
      expect(remove().props.disabled).toBe(true)
      expect(continueButton().props.disabled).toBe(true)
      readOnly.value = false
      await nextTick()
      ;(remove().props.onClick as () => void)()
      await nextTick()
      expect(selected.value).toEqual([])
      await choose('other-kind-allowed')
      expect(continueButton().props.disabled).toBe(false)
      ;(continueButton().props.onClick as () => void)()
      expect(continued).toBe(1)
      source.value = source.value.map((option) => ({ ...option, lateCheckinEligible: false }))
      await nextTick()
      expect(choices()).toHaveLength(0)
      expect(nodes().some((node) => node.text.includes('Нет доступных пропущенных пар.'))).toBe(
        true,
      )
      expect(selected.value).toEqual(['other-kind-allowed'])
      expect(continueButton().props.disabled).toBe(true)
      ;(remove().props.onClick as () => void)()
      await nextTick()
      expect(selected.value).toEqual([])
      expect(continueButton().props.disabled).toBe(true)
    } finally {
      app.unmount()
    }
  })
  it('also filters the alternate select while preserving missing/ineligible recovery, without changing EXCUSE options', async () => {
    const options = [candidate('eligible', true), candidate('unavailable', false)]
    const render = (mode: 'late' | 'excuse', ids: string | string[]) =>
      renderToString(
        createSSRApp(RequestLessonSelector, {
          mode,
          options,
          modelValue: ids,
        }),
      )
    const late = await render('late', 'unavailable')
    expect(late).toContain('<option value="eligible">')
    expect(late).not.toContain('<option value="unavailable"')
    expect(late).toContain('Убрать из заявки')
    const missing = await render('late', 'missing')
    expect(missing).toContain('Сохранённая пара больше недоступна')
    expect(missing).toContain('Убрать из заявки')
    const excuse = await render('excuse', [])
    expect(excuse).toContain('value="unavailable"')
    expect(excuse).toContain('value="eligible"')
  })
})

describe('EXCUSE date range input', () => {
  it('uses EXCUSE eligibility for planned/active/closed source candidates and preserves refreshed stale selections for removal', async () => {
    const source = ref<RequestLessonOption[]>([
      { lesson: { id: 'planned', date: '2026-09-02', status: 'PLANNED' }, excuseEligible: true },
      { lesson: { id: 'active', date: '2026-09-02', status: 'ACTIVE' }, excuseEligible: true },
      { lesson: { id: 'closed', date: '2026-09-02', status: 'CLOSED' }, excuseEligible: true },
      { lesson: { id: 'denied', date: '2026-09-02' }, excuseEligible: false },
      {
        lesson: { id: 'pending', date: '2026-09-02' },
        excuseEligible: true,
        pendingRequests: [{ id: 'p', kind: 'EXCUSE' }],
      },
      {
        lesson: { id: 'other-kind', date: '2026-09-02' },
        excuseEligible: true,
        pendingRequests: [{ id: 'l', kind: 'LATE_CHECKIN' }],
      },
    ])
    const selected = ref(['active'])
    let continued = 0
    const root = selectionNode('root')
    const app = selectionRenderer.createApp({
      render: () =>
        h(RequestLessonSelectionScreen, {
          kind: 'EXCUSE',
          lessons: source.value,
          lessonIds: selected.value,
          access: 'allowed',
          optionsLoaded: true,
          'onUpdate:lessonIds': (ids: string[]) => {
            selected.value = ids
          },
          onContinue: () => {
            continued++
          },
        }),
    })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const choices = () =>
      nodes()
        .filter((node) => node.props.type === 'checkbox')
        .map((node) => node.props.value)
    const continueButton = () =>
      nodes().find((node) => node.props.class === 'requests-submit-action')!
    try {
      expect(choices()).toEqual(['planned', 'active', 'closed', 'other-kind'])
      expect(continueButton().props.disabled).toBe(false)
      source.value = source.value.map((option) =>
        option.lesson?.id === 'active'
          ? {
              ...option,
              pendingRequests: [{ id: 'new-pending', kind: 'EXCUSE' }],
              unavailableReason: 'Заявка уже на рассмотрении.',
            }
          : option,
      )
      await nextTick()
      expect(choices()).toEqual(['planned', 'closed', 'other-kind'])
      expect(selected.value).toEqual(['active'])
      expect(nodes().some((node) => node.text.includes('Заявка уже на рассмотрении.'))).toBe(true)
      expect(continueButton().props.disabled).toBe(true)
      ;(continueButton().props.onClick as () => void)()
      expect(continued).toBe(0)
      const remove = nodes().find((node) => node.props.class === 'request-lesson-recovery-action')!
      ;(remove.props.onClick as () => void)()
      await nextTick()
      expect(selected.value).toEqual([])
      const checkbox = nodes().find((node) => node.props.value === 'planned')!
      ;(checkbox.props.onChange as (event: unknown) => void)({ target: { checked: true } })
      await nextTick()
      expect(continueButton().props.disabled).toBe(false)
      ;(continueButton().props.onClick as () => void)()
      expect(continued).toBe(1)
      source.value = source.value.map((option) => ({ ...option, excuseEligible: false }))
      await nextTick()
      expect(choices()).toEqual([])
      expect(selected.value).toEqual(['planned'])
      expect(continueButton().props.disabled).toBe(true)
      expect(nodes().some((node) => node.text.includes('Доступных пар нет.'))).toBe(true)
    } finally {
      app.unmount()
    }
  })

  it('filters inclusive local dates to eligible candidates and retains selected outside dates until explicit removal', async () => {
    const dated: RequestLessonOption[] = [
      { lesson: { id: 'before', date: '2026-09-01' }, excuseEligible: true },
      { lesson: { id: 'start', date: '2026-09-02' }, excuseEligible: true },
      { lesson: { id: 'end', date: '2026-09-03' }, excuseEligible: true },
      { lesson: { id: 'after', date: '2026-09-04' }, excuseEligible: true },
      {
        lesson: { id: 'blocked', date: '2026-09-02' },
        excuseEligible: false,
        unavailableReason: 'Пара отменена',
      },
    ]
    const selected = ref(['before', 'start', 'end'])
    let continued = 0
    const root = selectionNode('root')
    const app = selectionRenderer.createApp({
      render: () =>
        h(RequestLessonSelectionScreen, {
          kind: 'EXCUSE',
          lessons: dated,
          lessonIds: selected.value,
          access: 'allowed',
          optionsLoaded: true,
          'onUpdate:lessonIds': (ids: string[]) => {
            selected.value = ids
          },
          onContinue: () => {
            continued++
          },
        }),
    })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const field = (id: string) => nodes().find((node) => node.props.id === id)!
    const button = () => nodes().find((node) => node.props.class === 'requests-submit-action')!
    const checkboxIds = () =>
      nodes()
        .filter((node) => node.props.type === 'checkbox')
        .map((node) => node.props.value)
    const input = async (id: string, value: string) => {
      ;(field(id).props.onClick as () => void)()
      await nextTick()
      const day = nodes().find((node) => node.props['data-date'] === value)!
      expect(day.props.disabled).toBe(false)
      ;(day.props.onClick as () => void)()
      await nextTick()
    }
    const show = async () => {
      const action = nodes().find((node) => node.text.trim() === 'Показать пары')!
      expect(action.props.disabled).toBe(false)
      ;(action.props.onClick as () => void)()
      await nextTick()
    }
    try {
      await input('request-date-from', '2026-09-02')
      await input('request-date-to', '2026-09-03')
      expect(checkboxIds()).toEqual([])
      expect(selected.value).toEqual(['before', 'start', 'end'])
      await show()
      expect(checkboxIds()).toEqual(['before', 'start', 'end'])
      expect(nodes().some((node) => node.props.value === 'blocked')).toBe(false)
      expect(nodes().some((node) => node.text.includes('Пара отменена'))).toBe(false)
      expect(selected.value).toEqual(['before', 'start', 'end'])
      expect(
        nodes()
          .map((node) => node.text)
          .join(''),
      ).toContain('Выбрано вне диапазона: 1')
      expect(button().props.disabled).toBe(true)
      ;(button().props.onClick as () => void)()
      expect(continued).toBe(0)
      const before = nodes().find((node) => node.props.value === 'before')!
      ;(before.props.onChange as (event: Event) => void)({
        target: { checked: false },
      } as unknown as Event)
      await nextTick()
      expect(selected.value).toEqual(['start', 'end'])
      expect(checkboxIds()).toEqual(['start', 'end'])
      expect(button().props.disabled).toBe(false)
      await input('request-date-from', '2026-09-04')
      expect(nodes().find((node) => node.props.id === 'request-date-error')?.text).toContain(
        'Дата окончания',
      )
      expect(field('request-date-from').props['aria-invalid']).toBe(true)
      expect(button()).toBeUndefined()
      expect(selected.value).toEqual(['start', 'end'])
      await input('request-date-from', '2026-09-01')
      await input('request-date-to', '2026-09-04')
      await show()
      expect(checkboxIds()).toEqual(['before', 'start', 'end', 'after'])
      expect(button().props.disabled).toBe(false)
      ;(button().props.onClick as () => void)()
      expect(continued).toBe(1)
    } finally {
      app.unmount()
    }
  })
  it('applies the range before new selection and preserves it with the reason draft through next-step/edit/back', async () => {
    const dated: RequestLessonOption[] = [
      { lesson: { id: 'first', date: '2026-09-01' }, excuseEligible: true },
      { lesson: { id: 'chosen', date: '2026-09-02' }, excuseEligible: true },
      { lesson: { id: 'last', date: '2026-09-03' }, excuseEligible: true },
    ]
    const draft = ref(
      updateRequestsDraft('student', 'session', {
        view: 'select-excuse',
        excuseReason: 'OTHER',
        excuseComment: 'Черновик причины',
        excuseFiles: [{ id: 'proof', name: 'справка.pdf', size: 1, type: 'application/pdf' }],
      }),
    )
    const range = ref<{ from: string; to: string } | null>(null)
    const update = (patch: Parameters<typeof updateRequestsDraft>[2]) => {
      draft.value = updateRequestsDraft('student', 'session', patch)
    }
    const root = selectionNode('root')
    const app = selectionRenderer.createApp({
      render: () =>
        draft.value.view === 'select-excuse'
          ? h(RequestLessonSelectionScreen, {
              kind: 'EXCUSE',
              lessons: dated,
              lessonIds: draft.value.excuseLessonIds,
              access: 'allowed',
              optionsLoaded: true,
              dateRange: range.value,
              'onUpdate:dateRange': (value: { from: string; to: string }) => {
                range.value = value
              },
              'onUpdate:lessonIds': (ids: string[]) => update({ excuseLessonIds: ids }),
              onContinue: () => update({ view: 'excuse' }),
            })
          : h(ExcuseRequestScreen, {
              access: 'allowed',
              lessons: dated,
              lessonIds: draft.value.excuseLessonIds,
              reasons: [{ code: 'OTHER', label: 'Прочее', commentRequired: true }],
              reason: draft.value.excuseReason,
              comment: draft.value.excuseComment,
              files: draft.value.excuseFiles,
              fileLimits: null,
              selectionComplete: true,
              onEditSelection: () => update({ view: 'select-excuse' }),
              onBack: () => update({ view: 'select-excuse' }),
            }),
    })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const click = async (predicate: (node: SelectionNode) => boolean) => {
      const node = nodes().find(predicate)!
      expect(node).toBeDefined()
      expect(node.props.disabled).not.toBe(true)
      ;(node.props.onClick as () => void)()
      await nextTick()
    }
    try {
      expect(nodes().some((node) => node.props.type === 'checkbox')).toBe(false)
      expect(nodes().some((node) => node.props.class === 'requests-submit-action')).toBe(false)
      for (const id of ['request-date-from', 'request-date-to']) {
        await click((node) => node.props.id === id)
        await click((node) => node.props['data-date'] === '2026-09-02')
      }
      await click((node) => node.text.trim() === 'Показать пары')
      const checkbox = nodes().find((node) => node.props.type === 'checkbox')!
      expect(checkbox.props.value).toBe('chosen')
      ;(checkbox.props.onChange as (event: unknown) => void)({ target: { checked: true } })
      await nextTick()
      expect(draft.value.excuseLessonIds).toEqual(['chosen'])
      for (const edit of [true, false]) {
        await click((node) => node.props.class === 'requests-submit-action')
        expect(draft.value.view).toBe('excuse')
        if (edit)
          await click((node) => node.tag === 'button' && node.text.includes('Изменить пары'))
        else await click((node) => node.props.class === 'requests-back-button')
        expect(draft.value.view).toBe('select-excuse')
        expect(nodes().find((node) => node.props.value === 'chosen')?.props.checked).toBe(true)
        expect(range.value).toEqual({ from: '2026-09-02', to: '2026-09-02' })
        expect(draft.value.excuseReason).toBe('OTHER')
        expect(draft.value.excuseComment).toBe('Черновик причины')
        expect(draft.value.excuseFiles[0]?.id).toBe('proof')
      }
    } finally {
      app.unmount()
    }
  })
  it('clamps calendar keyboard navigation and keeps close separate from route back', async () => {
    const selected = ref('2026-09-30')
    let closed = 0
    const root = selectionNode('root')
    const app = selectionRenderer.createApp({
      render: () =>
        h(RequestDateCalendar, {
          modelValue: selected.value,
          min: '2026-09-02',
          max: '2026-10-03',
          label: 'С даты',
          'onUpdate:modelValue': (date: string) => {
            selected.value = date
          },
          onClose: () => {
            closed++
          },
        }),
    })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const key = async (date: string, value: string) => {
      const day = nodes().find((node) => node.props['data-date'] === date)!
      await (day.props.onKeydown as (event: unknown) => Promise<void>)({
        key: value,
        preventDefault() {},
      })
      await nextTick()
    }
    try {
      expect(nodes().find((node) => node.props['data-date'] === '2026-09-01')?.props.disabled).toBe(
        true,
      )
      await key('2026-09-30', 'PageDown')
      expect(nodes().find((node) => node.props['data-date'] === '2026-10-03')?.props.tabindex).toBe(
        0,
      )
      expect(
        nodes().find((node) => node.props['aria-label'] === 'Следующий месяц')?.props.disabled,
      ).toBe(true)
      expect(nodes().find((node) => node.props['data-date'] === '2026-10-04')?.props.disabled).toBe(
        true,
      )
      await key('2026-10-03', 'ArrowRight')
      expect(nodes().find((node) => node.props['data-date'] === '2026-10-03')?.props.tabindex).toBe(
        0,
      )
      await key('2026-10-03', 'PageUp')
      expect(nodes().find((node) => node.props['data-date'] === '2026-09-03')?.props.tabindex).toBe(
        0,
      )
      await key('2026-09-03', 'Home')
      expect(nodes().find((node) => node.props['data-date'] === '2026-09-02')?.props.tabindex).toBe(
        0,
      )
      const first = nodes().find((node) => node.props['data-date'] === '2026-09-02')!
      ;(first.props.onClick as () => void)()
      await nextTick()
      expect(selected.value).toBe('2026-09-02')
      const calendar = nodes().find((node) => node.props.class === 'request-date-calendar')!
      ;(calendar.props.onKeydown as (event: unknown) => void)({
        key: 'Escape',
        preventDefault() {},
        stopPropagation() {},
      })
      expect(closed).toBe(1)
    } finally {
      app.unmount()
    }
  })
  it('leaves the single-lesson LATE selection without date controls', async () => {
    const markup = await renderToString(
      createSSRApp(RequestLessonSelectionScreen, {
        kind: 'LATE_CHECKIN',
        lessons: options,
        lessonIds: ['one'],
        access: 'allowed',
        optionsLoaded: true,
      }),
    )
    expect(markup).not.toContain('type="date"')
    expect(markup).not.toMatch(/class="requests-submit-action"[^>]*disabled/)
  })
})

describe('headman request coverage and predecision summary', () => {
  it('shows both coverage dates and independent server counts through detail and fresh confirmation', async () => {
    const id = 'aaaaaaaaaaaaaaaaaaaaaaaa'
    const summary = {
      id,
      kind: 'EXCUSE',
      status: 'PENDING',
      studentId: 1,
      studentName: 'Тестовый студент',
      reason: 'OTHER',
      comment: null,
      coverageStart: '2026-09-02',
      coverageEnd: '2026-09-07',
      lessonCount: 5,
      alreadyMarkedCount: 2,
      hasAttachments: false,
    }
    const lessons = ['2026-09-02', '2026-09-07'].map((date, index) => ({
      lessonId: index + 1,
      occurrenceId: String(index + 100),
      occurrenceRevision: '1',
      date,
      attendanceStatus: 'ABSENT',
    }))
    const detail = {
      summary,
      lessons,
      currentAffectedLessons: lessons,
      decisionRevision: '1'.repeat(64),
      attachments: [],
    }
    let markedCount = 2
    const api = new HeadmanRequestsApi({
      accessToken: () => 'synthetic-local-test',
      fetcher: async (url) =>
        new Response(
          JSON.stringify(
            String(url).includes('?')
              ? { content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 }
              : { ...detail, summary: { ...summary, alreadyMarkedCount: markedCount } },
          ),
          { status: 200 },
        ),
    })
    const root = selectionNode('root')
    const app = selectionRenderer.createApp(HeadmanRequestsScreen, { api })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const text = (node: SelectionNode) =>
      selectionNodes(node)
        .map((item) => item.text)
        .join(' ')
    const settle = async () => {
      for (let i = 0; i < 20; i++) {
        await Promise.resolve()
        await nextTick()
      }
    }
    try {
      await settle()
      const ticket = nodes().find((node) => node.props['data-request-id'] === id)!
      expect(text(ticket)).toContain('2 сентября')
      expect(text(ticket)).toContain('— 7 сентября')
      expect(text(ticket)).toContain('Другая причина')
      expect(text(ticket)).not.toContain('Пар: 5')
      await (ticket.props.onClick as () => Promise<void>)()
      await settle()
      expect(text(root)).toContain('2 из 5 пар уже отмечены')
      markedCount = 3
      const approve = nodes().find(
        (node) => node.tag === 'button' && node.text.trim() === 'Одобрить',
      )!
      await (approve.props.onClick as () => Promise<void>)()
      await settle()
      expect(text(root)).toContain('3 из 5 пар уже отмечены')
      expect(text(root)).toContain('Подтверждённое присутствие сохранится')
    } finally {
      app.unmount()
    }
  })
})

describe('headman archive type filter', () => {
  it('applies an archive type with the existing name and period, shows filtered empty and resets', async () => {
    const summary = {
      id: 'bbbbbbbbbbbbbbbbbbbbbbbb',
      kind: 'EXCUSE',
      status: 'APPROVED',
      studentId: 1,
      studentName: 'Тестовый студент',
      reason: 'ILLNESS',
      comment: null,
      coverageStart: '2026-09-02',
      coverageEnd: '2026-09-07',
      lessonCount: 2,
      alreadyMarkedCount: 0,
      hasAttachments: false,
    }
    const queries: URLSearchParams[] = []
    const api = new HeadmanRequestsApi({
      accessToken: () => 'synthetic-local-test',
      fetcher: async (input) => {
        const url = new URL(String(input), 'https://synthetic.invalid')
        if (!url.search) {
          return new Response(JSON.stringify({ summary, lessons: [], attachments: [] }), {
            status: 200,
          })
        }
        queries.push(url.searchParams)
        const content =
          url.searchParams.get('bucket') === 'ARCHIVE' &&
          url.searchParams.get('type') !== 'LATE_CHECKIN'
            ? [summary]
            : []
        return new Response(
          JSON.stringify({
            content,
            page: 0,
            size: 20,
            totalElements: content.length,
            totalPages: 1,
          }),
          { status: 200 },
        )
      },
    })
    const root = selectionNode('root')
    const app = selectionRenderer.createApp(HeadmanRequestsScreen, { api })
    app.mount(root)
    const nodes = () => selectionNodes(root)
    const text = (node: SelectionNode) =>
      selectionNodes(node)
        .map((item) => item.text)
        .join(' ')
    const settle = async () => {
      for (let i = 0; i < 30; i++) {
        await Promise.resolve()
        await nextTick()
      }
    }
    const openFilters = async () => {
      const button = nodes().find((node) => node.props['aria-label'] === 'Поиск и период')!
      ;(button.props.onClick as () => void)()
      await nextTick()
    }
    const typeTab = (label: string) => {
      const filters = nodes().find(
        (node) => node.props.role === 'group' && node.props['aria-label'] === 'Фильтры заявок',
      )!
      expect(filters).toBeDefined()
      const tab = selectionNodes(filters).find(
        (node) => node.tag === 'button' && text(node).includes(label),
      )!
      expect(tab).toBeDefined()
      return tab
    }
    const selectType = async (label: string) => {
      const tab = typeTab(label)
      ;(tab.props.onClick as () => void)()
      await settle()
      expect(tab.props['aria-pressed']).toBe(true)
    }
    const fill = (label: string, value: string) => {
      const input = nodes().find(
        (node) => node.tag === 'input' && node.parent && text(node.parent).trim() === label,
      )!
      input.value = value
      ;(input.props['native:input'] as (event: unknown) => void)({ target: input })
    }
    const apply = async () => {
      const form = nodes().find((node) => node.tag === 'form')!
      ;(form.props.onSubmit as (event: unknown) => void)({ preventDefault() {} })
      await settle()
    }
    const lastArchive = () => queries.filter((query) => query.get('bucket') === 'ARCHIVE').at(-1)!
    try {
      await settle()
      const archive = typeTab('Архив')
      ;(archive.props.onClick as () => void)()
      await settle()
      expect(archive.props['aria-pressed']).toBe(true)
      expect(typeTab('Все').props['aria-pressed']).toBe(true)
      await selectType('Уважительные причины')
      expect(lastArchive().get('type')).toBe('EXCUSE')
      await openFilters()
      const beforeDraft = queries.length
      fill('ФИО', 'Тестовый студент')
      fill('Период от', '2026-09-02')
      fill('Период до', '2026-09-07')
      expect(queries).toHaveLength(beforeDraft)
      await apply()
      expect(Object.fromEntries(lastArchive())).toEqual({
        bucket: 'ARCHIVE',
        page: '0',
        size: '20',
        type: 'EXCUSE',
        studentName: 'Тестовый студент',
        coverageDateFrom: '2026-09-02',
        coverageDateTo: '2026-09-07',
      })
      expect(nodes().filter((node) => node.props['data-request-id'] === summary.id)).toHaveLength(1)
      await selectType('Забыл отметиться')
      expect(lastArchive().get('type')).toBe('LATE_CHECKIN')
      expect(lastArchive().get('studentName')).toBe('Тестовый студент')
      expect(lastArchive().get('coverageDateFrom')).toBe('2026-09-02')
      expect(lastArchive().get('coverageDateTo')).toBe('2026-09-07')
      expect(nodes().some((node) => node.props['data-request-id'] === summary.id)).toBe(false)
      expect(text(root)).toContain('В архиве нет заявок по заданным условиям.')
      await selectType('Все')
      expect(lastArchive().get('type')).toBeNull()
      expect(lastArchive().get('studentName')).toBe('Тестовый студент')
      await openFilters()
      fill('ФИО', '')
      fill('Период от', '')
      fill('Период до', '')
      await apply()
      expect(Object.fromEntries(lastArchive())).toEqual({
        bucket: 'ARCHIVE',
        page: '0',
        size: '20',
      })
      expect(nodes().filter((node) => node.props['data-request-id'] === summary.id)).toHaveLength(1)
    } finally {
      app.unmount()
    }
  })
})
