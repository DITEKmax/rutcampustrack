import { describe, expect, it } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import HomeworkScreen from './HomeworkScreen.vue'
import type { StudentHomework, StudentHomeworkItem } from '../../api/types'
import { createStudentNavigationItems } from '../../shared/mobile-navigation-items'
import { canRestoreHomeworkFocus, type HomeworkFocusRequest } from './homework-focus'

const body = {} as Element
const completion = {} as Element
const otherControl = {} as Element

const request: HomeworkFocusRequest = {
  id: 'math',
  expectedCompleted: true,
  origin: completion,
  feedIdentity: 'semester:from:to',
  ownerKey: 'student-a:group-a:0',
}

function screenFeed(): StudentHomework {
  const base: StudentHomeworkItem = {
    id: 'early',
    title: 'Задание',
    description: '',
    link: null,
    lessonDate: '2026-09-07',
    lessonNumber: 2,
    bindingMode: 'LESSON',
    archived: false,
    subject: { id: 'math', name: 'Раньше' },
    completed: false,
    completedAt: null,
  }
  return {
    semester: { id: 'semester', name: 'Осень', dateFrom: '2026-09-03', dateTo: '2026-12-31' },
    from: '2026-09-01',
    to: '2026-09-30',
    serverNow: '2026-09-07T08:00:00Z',
    items: [
      {
        ...base,
        id: 'done',
        lessonNumber: 1,
        subject: { id: 'done', name: 'Готово' },
        completed: true,
        completedAt: '2026-09-07T07:00:00Z',
      },
      base,
      {
        ...base,
        id: 'outside',
        lessonDate: '2026-08-25',
        subject: { id: 'outside', name: 'Вне периода' },
        completed: true,
        completedAt: '2026-09-07T07:00:00Z',
      },
    ],
  }
}

describe('accepted homework period rendering', () => {
  it('shows the material hostname and escaped safe inline links without interpreting markup', async () => {
    const feed = screenFeed()
    const item = {
      ...feed.items[1]!,
      link: 'https://materials.example.test/course?topic=one',
      description:
        '<script>alert(1)</script> https://notes.example.test/read?a=1&b=2 javascript:alert(2) https://user:secret@private.example.test/read',
    }
    const html = await renderToString(
      createSSRApp(HomeworkScreen, {
        homework: { ...feed, items: [item] },
        loading: false,
        error: null,
        navItems: createStudentNavigationItems({ homeworkEnabled: true }),
        focusItemId: item.id,
        focusRequestId: 1,
      }),
    )
    expect(html).toContain('class="homework-material__hostname">materials.example.test</span>')
    expect(html).toContain('aria-label="Материалы к заданию: Раньше"')
    expect(html).toMatch(
      /<a href="https:\/\/notes.example.test\/read\?a=1&amp;b=2" target="_blank" rel="noopener noreferrer"/,
    )
    expect(html).toContain(
      'aria-label="Материалы к заданию: Раньше (https://notes.example.test/read?a=1&amp;b=2)"',
    )
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).toContain('javascript:alert(2)')
    expect(html).toContain('https://user:secret@private.example.test/read')
    expect(html).not.toContain('href="javascript:')
    expect(html).not.toContain('href="https://user:')
    expect(html).not.toContain('<script>')
  })

  it('keeps unsupported primary material inert and does not invent a hostname', async () => {
    const feed = screenFeed()
    const html = await renderToString(
      createSSRApp(HomeworkScreen, {
        homework: { ...feed, items: [{ ...feed.items[1]!, link: 'javascript:alert(1)' }] },
        loading: false,
        error: null,
        navItems: createStudentNavigationItems({ homeworkEnabled: true }),
      }),
    )
    expect(html).toContain('Материалы недоступны: ссылка не поддерживается.')
    expect(html).not.toContain('homework-material__hostname')
    expect(html).not.toContain('href="javascript:')
  })

  it.each(['loading', 'error', 'unknown', 'partial'] as const)(
    'does not announce unknown month assignments as empty: %s',
    async (state) => {
      const partial = { ...screenFeed(), from: '2026-09-07', to: '2026-09-07' }
      const html = await renderToString(
        createSSRApp(HomeworkScreen, {
          homework: state === 'partial' ? partial : null,
          loading: state === 'loading',
          error: state === 'error' ? 'Синтетическая ошибка' : null,
          navItems: createStudentNavigationItems({ homeworkEnabled: true }),
          selectedDate: '2026-09-07',
          todayDate: '2026-09-07',
          periodMode: 'month',
          periodRange: { from: '2026-09-03', to: '2026-09-30' },
          semesterRange: { from: '2026-09-03', to: '2026-12-31' },
        }),
      )
      expect(html).toContain('СЕНТЯБРЬ 2026')
      expect(html).not.toContain('Нет заданий')
      expect(html).not.toContain('Заданий:')
      expect(html).not.toContain('Заданий пока нет')
      expect(html).toMatch(/data-homework-date="2026-09-07"[^>]*disabled/)
      if (state === 'loading') expect(html).toContain('aria-busy="true"')
      if (state === 'error') expect(html).toContain('Не удалось загрузить календарь.')
    },
  )

  it('announces an actually observed empty month only after a successful complete range', async () => {
    const html = await renderToString(
      createSSRApp(HomeworkScreen, {
        homework: { ...screenFeed(), items: [] },
        loading: false,
        error: null,
        navItems: createStudentNavigationItems({ homeworkEnabled: true }),
        selectedDate: '2026-09-07',
        periodMode: 'month',
        periodRange: { from: '2026-09-03', to: '2026-09-30' },
        semesterRange: { from: '2026-09-03', to: '2026-12-31' },
      }),
    )
    expect(html).toContain('Нет заданий')
    expect(html).toContain('На этот день заданий нет.')
    expect(html).toMatch(/data-homework-date="2026-09-07"(?![^>]*disabled)[^>]*>/)
  })
  it('renders selected-day unfinished assignments before completed while excluding the server completion-date union', async () => {
    const html = await renderToString(
      createSSRApp(HomeworkScreen, {
        homework: screenFeed(),
        loading: false,
        error: null,
        navItems: createStudentNavigationItems({ homeworkEnabled: true }),
        selectedDate: '2026-09-07',
        periodMode: 'day',
        periodRange: { from: '2026-09-07', to: '2026-09-07' },
      }),
    )
    expect(html).toContain('Раньше')
    expect(html).toContain('Готово')
    expect(html.indexOf('Раньше'), 'selected-day unfinished before completed').toBeLessThan(
      html.indexOf('Готово'),
    )
    expect(html).not.toContain('Вне периода')
  })

  it('renders exclusive period tabs and a bounded month calendar with actual subject labels and selected-day panel', async () => {
    const html = await renderToString(
      createSSRApp(HomeworkScreen, {
        homework: screenFeed(),
        loading: false,
        error: null,
        navItems: createStudentNavigationItems({ homeworkEnabled: true }),
        selectedDate: '2026-09-07',
        todayDate: '2026-09-07',
        periodMode: 'month',
        periodRange: { from: '2026-09-03', to: '2026-09-30' },
        semesterRange: { from: '2026-09-03', to: '2026-12-31' },
      }),
    )
    expect(html.match(/role="tab"/g)).toHaveLength(3)
    expect(html.match(/aria-selected="true"/g)).toHaveLength(1)
    expect(html).toContain('aria-current="date"')
    expect(html).toMatch(/data-homework-date="2026-09-01"[^>]*disabled/)
    expect(html).toMatch(/data-homework-date="2026-09-07"[^>]*aria-pressed="true"/)
    expect(html).toContain('Заданий: 2. Готово, Раньше')
    expect(html).not.toContain('Вне периода')
  })
})

describe('homework completion focus ownership', () => {
  it('restores the same assignment after a confirmed ACK reorder', () => {
    expect(
      canRestoreHomeworkFocus({
        request,
        item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
        pending: false,
        activeElement: body,
        documentBody: body,
        feedIdentity: request.feedIdentity,
        ownerKey: request.ownerKey,
      }),
    ).toBe(true)
  })

  it('does not steal focus after the user moves elsewhere while ACK is delayed', () => {
    expect(
      canRestoreHomeworkFocus({
        request,
        item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
        pending: false,
        activeElement: otherControl,
        documentBody: body,
        feedIdentity: request.feedIdentity,
        ownerKey: request.ownerKey,
      }),
    ).toBe(false)
  })

  it('rejects pending, malformed, and stale-owner responses', () => {
    const base = {
      request,
      pending: true,
      activeElement: body,
      documentBody: body,
      feedIdentity: request.feedIdentity,
      ownerKey: request.ownerKey,
    }
    expect(
      canRestoreHomeworkFocus({
        ...base,
        item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
      }),
    ).toBe(false)
    expect(
      canRestoreHomeworkFocus({
        ...base,
        pending: false,
        item: { completed: true, completedAt: null },
      }),
    ).toBe(false)
    expect(
      canRestoreHomeworkFocus({
        ...base,
        pending: false,
        item: { completed: true, completedAt: '2026-09-07T09:30:00Z' },
        ownerKey: 'student-b:group-a:0',
      }),
    ).toBe(false)
  })
})
