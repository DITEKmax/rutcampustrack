import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import RequestLessonSelectionScreen from './RequestLessonSelectionScreen.vue'
import { afterEach, describe, expect, it } from 'vitest'
import { getOrCreateRequestsDraft, purgeRequestsDrafts, readRequestsDraft, requestSelectionEligible, updateRequestsDraft, removeSelectedLessonId } from './state'
import type { RequestLessonOption } from './types'
const options: RequestLessonOption[] = [
  { lesson: { id: 'one' }, excuseEligible: true, lateCheckinEligible: true, pendingRequests: [] },
  { lesson: { id: 'two' }, excuseEligible: true, lateCheckinEligible: true, pendingRequests: [{ id: 'other', kind: 'LATE_CHECKIN' }] },
  { lesson: { id: 'blocked' }, excuseEligible: false, lateCheckinEligible: false },
  { lesson: { id: 'pending' }, excuseEligible: true, lateCheckinEligible: true, pendingRequests: [{ id: 'own', kind: 'EXCUSE' }] },
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
    updateRequestsDraft('student', 'session', { view: 'select-excuse', excuseLessonIds: ['one', 'missing', 'blocked'] })
    const retained = getOrCreateRequestsDraft('student', 'session')
    expect(requestSelectionEligible(options, retained.excuseLessonIds, 'EXCUSE')).toBe(false)
    expect(retained.excuseLessonIds).toEqual(['one', 'missing', 'blocked'])
    const fixed = removeSelectedLessonId(removeSelectedLessonId(retained.excuseLessonIds, 'missing'), 'blocked')
    expect(requestSelectionEligible(options, fixed, 'EXCUSE')).toBe(true)
  })
  it('preserves reason, comment and files through selection edit/back without leaking owner or session', () => {
    updateRequestsDraft('student', 'session', { view: 'excuse', excuseLessonIds: ['one'], excuseReason: 'OTHER', excuseComment: 'Причина', excuseFiles: [{ id: 'proof', name: 'справка.pdf', size: 1, type: 'application/pdf' }] })
    updateRequestsDraft('student', 'session', { view: 'select-excuse' })
    updateRequestsDraft('student', 'session', { view: 'type' })
    updateRequestsDraft('student', 'session', { view: 'select-excuse', excuseLessonIds: ['one', 'two'] })
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
    const render = (ids: string[], extra: { offline?: boolean; readOnly?: boolean } = {}) => renderToString(createSSRApp(RequestLessonSelectionScreen, { kind: 'EXCUSE', lessons: options, lessonIds: ids, access: 'allowed', optionsLoaded: true, ...extra }))
    expect(await render(['one', 'two'])).not.toMatch(/class="requests-submit-action"[^>]*disabled/)
    for (const ids of [[], ['one', 'missing'], ['blocked'], ['pending']]) expect(await render(ids)).toMatch(/class="requests-submit-action"[^>]*disabled/)
    expect(await render(['one'], { offline: true })).toMatch(/class="requests-submit-action"[^>]*disabled/)
    expect(await render(['one'], { readOnly: true })).toMatch(/class="requests-submit-action"[^>]*disabled/)
  })
})

describe('lesson options failure and recovery', () => {
  it('asserts an empty lesson list only after successful resolution and retains cached lessons while loading or failed', async () => {
    const cached: RequestLessonOption[] = [{ lesson: { id: 'cached', subjectName: 'Сохранённая известная пара' }, excuseEligible: true }]
    const render = (lessons: RequestLessonOption[], extra: { error?: string; loading?: boolean; optionsLoaded?: boolean; offline?: boolean; access?: 'forbidden' | 'no-active-semester' } = {}) => renderToString(createSSRApp(RequestLessonSelectionScreen, { kind: 'EXCUSE', lessons, lessonIds: ['cached'], access: 'allowed', optionsLoaded: true, ...extra }))
    expect(await render([])).toContain('В выбранные даты пар нет.')
    for (const state of [{ error: 'Не удалось загрузить варианты' }, { loading: true }, { optionsLoaded: false }, { offline: true }, { access: 'forbidden' as const }, { access: 'no-active-semester' as const }]) {
      const unknown = await render([], state)
      expect(unknown).not.toContain('В выбранные даты пар нет.')
      expect(unknown).not.toContain('эти пары больше не пришли с сервера')
      expect(unknown).toContain('Твой выбор сохранён.')
      expect(unknown).toContain('Убрать из заявки')
      if (!('offline' in state || 'access' in state)) expect(unknown).not.toMatch(/class="request-lesson-recovery-action"[^>]*disabled/)
      const retained = await render(cached, state)
      expect(retained).toContain('Сохранённая известная пара')
      expect(retained).toMatch(/type="checkbox"[^>]*checked[^>]*disabled/)
      expect(retained).toMatch(/class="requests-submit-action"[^>]*disabled/)
    }
  })
})
