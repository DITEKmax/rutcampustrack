import { effectScope, shallowReactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { HeadmanStatsApi } from './headman-stats-client'
import { HeadmanJournalApi } from '../headman-journal/headman-journal-client'
import { useHeadmanStatsHistory, type HeadmanStatsHistoryScope } from './use-headman-stats-history'
import { headmanStatsFixtureLessons } from '../../dev/headman-stats-fixtures'
import type { HeadmanStatsLesson } from './headman-stats-history-client'

function harness(status: 'PRESENT' | 'EXCUSED' | 'ABSENT' | null = 'EXCUSED') {
  let lesson: HeadmanStatsLesson = { ...headmanStatsFixtureLessons('history')[1]!, status, ...(status === null ? { source: null, markRevision: null } : {}) }
  let writes = 0; let conflict = false; let releaseWrite: (() => void) | null = null
  let hold = false
  let denyRead = false; let denyWrite = false
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input), 'http://fixture.test')
    if (url.pathname.endsWith('/lessons')) return new Response(JSON.stringify(denyRead ? { detail: 'Просмотр истории запрещён' } : { page: 0, size: 50, totalElements: 1, totalPages: 1, hasPrevious: false, hasNext: false, items: [lesson] }), { status: denyRead ? 403 : 200, headers: { 'Content-Type': 'application/json' } })
    if (init?.method === 'PUT') {
      writes += 1
      if (denyWrite) return new Response(JSON.stringify({ detail: 'Изменение отметки запрещено' }), { status: 403, headers: { 'Content-Type': 'application/json' } })
      const body = JSON.parse(String(init.body))
      if (conflict) { lesson = { ...lesson, status: 'EXCUSED', markRevision: 'remote-excuse-revision' }; return new Response(null, { status: 409 }) }
      if (body.expectedStatus !== (lesson.status ?? 'EMPTY') || body.expectedRevision !== (lesson.markRevision ?? 'EMPTY')) return new Response(null, { status: 409 })
      lesson = { ...lesson, status: body.status, source: 'HEADMAN_MANUAL', markRevision: 'new-mark-revision' }
      return new Response(JSON.stringify({ lessonId: lesson.lessonId, userId: 11, status: lesson.status, timestamp: '2026-10-06T08:35:00Z' }), { headers: { 'Content-Type': 'application/json' } })
    }
    if (init?.method === 'DELETE') {
      writes += 1
      if (denyWrite) return new Response(JSON.stringify({ detail: 'Изменение отметки запрещено' }), { status: 403, headers: { 'Content-Type': 'application/json' } })
      if (hold) await new Promise<void>((resolve) => { releaseWrite = resolve })
      if (conflict) { lesson = { ...lesson, markRevision: 'changed-reason-revision' }; return new Response(JSON.stringify({ detail: 'Отметка изменилась' }), { status: 409, headers: { 'Content-Type': 'application/json' } }) }
      if (url.searchParams.get('expectedStatus') !== lesson.status || url.searchParams.get('expectedRevision') !== lesson.markRevision) return new Response(null, { status: 409 })
      lesson = { ...lesson, status: null, source: null, markRevision: null }
      return new Response(null, { status: 204 })
    }
    return new Response(null, { status: 404 })
  }
  const api = new HeadmanStatsApi({ accessToken: () => 'synthetic-token', fetcher })
  const journalApi = new HeadmanJournalApi({ accessToken: () => 'synthetic-token', fetcher })
  const owner = shallowReactive<HeadmanStatsHistoryScope>({ api, journalApi, groupId: 2, studentId: 11, semesterId: 7, offline: false, allowed: true, markAllowed: true, excuseAllowed: true })
  const scope = effectScope(); const refreshed = vi.fn(); const controller = scope.run(() => useHeadmanStatsHistory(() => owner, refreshed))!
  return { controller, owner, refreshed, stop: () => scope.stop(), writes: () => writes, setConflict: () => { conflict = true }, denyRead: (value: boolean) => { denyRead = value }, denyWrite: () => { denyWrite = true }, hold: () => { hold = true }, release: () => { releaseWrite?.() } }
}
describe('headman history scoped clear', () => {
  it('read403 removes restricted rows and intents, preserves error and gates writes until an authorized snapshot', async () => {
    const h = harness(); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    const staleLesson = h.controller.data.value!.items[0]!
    h.controller.choose(staleLesson, 'EXCUSED'); h.denyRead(true); await h.controller.load()
    expect(h.controller.denied.value).toBe(true); expect(h.controller.data.value).toBeNull()
    expect(h.controller.confirmation.value).toBeNull(); expect(h.controller.excuse.value).toBeNull()
    expect(h.controller.error.value).toContain('Просмотр истории запрещён'); expect(h.controller.writable.value).toBe(false)
    h.controller.choose(staleLesson, 'EXCUSED'); h.controller.confirm(); expect(h.writes()).toBe(0)
    await h.controller.load(); expect(h.controller.denied.value).toBe(true)
    h.denyRead(false); await h.controller.load(); expect(h.controller.denied.value).toBe(false)
    expect(h.controller.writable.value).toBe(true); expect(h.controller.data.value).not.toBeNull(); h.stop()
  })
  it.each(['PRESENT', null] as const)('write403 clears a denied %s scope and cannot reuse its old row', async (status) => {
    const h = harness(status); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull()); h.denyWrite()
    const staleLesson = h.controller.data.value!.items[0]!
    h.controller.choose(staleLesson, 'PRESENT')
    await vi.waitFor(() => expect(h.controller.denied.value).toBe(true))
    expect(h.controller.data.value).toBeNull(); expect(h.controller.feedback.value).toBeNull()
    expect(h.controller.error.value).toContain('Изменение отметки запрещено'); expect(h.controller.writable.value).toBe(false)
    h.controller.choose(staleLesson, 'PRESENT'); expect(h.writes()).toBe(1); expect(h.refreshed).not.toHaveBeenCalled()
    h.owner.studentId = 12; await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    expect(h.controller.denied.value).toBe(false); expect(h.controller.error.value).toBeNull(); h.stop()
  })
  it('creates an unmarked status only with the EMPTY pair, then publishes the fresh snapshot', async () => {
    const h = harness(null); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    h.controller.choose(h.controller.data.value!.items[0]!, 'PRESENT')
    await vi.waitFor(() => expect(h.controller.data.value?.items[0]?.status).toBe('PRESENT'))
    expect(h.controller.data.value!.items[0]!.markRevision).toBe('new-mark-revision')
    expect(h.refreshed).toHaveBeenCalledOnce(); h.stop()
  })
  it('a concurrent EXCUSED mark prevents creating a stale EMPTY status', async () => {
    const h = harness(null); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull()); h.setConflict()
    h.controller.choose(h.controller.data.value!.items[0]!, 'ABSENT')
    await vi.waitFor(() => expect(h.controller.error.value).toContain('повтори действие'))
    expect(h.controller.data.value!.items[0]!.status).toBe('EXCUSED')
    expect(h.controller.data.value!.items[0]!.markRevision).toBe('remote-excuse-revision')
    expect(h.controller.feedback.value).toBeNull(); expect(h.refreshed).not.toHaveBeenCalled(); h.stop()
  })
  it('cancel preserves the mark and independent approved ticket; confirm clears only its revision', async () => {
    const h = harness(); await vi.waitFor(() => expect(h.controller.data.value?.items.length).toBe(1))
    h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED')
    expect(h.controller.confirmation.value).not.toBeNull()
    h.controller.cancel()
    expect(h.writes()).toBe(0)
    expect(h.controller.data.value!.items[0]!.status).toBe('EXCUSED')
    h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED'); h.controller.confirm()
    await vi.waitFor(() => expect(h.controller.data.value?.items[0]?.status).toBeNull())
    expect(h.controller.data.value!.items[0]!.approvedTicket?.status).toBe('APPROVED')
    expect(h.controller.feedback.value).toContain('Статус снят')
    expect(h.refreshed).toHaveBeenCalledOnce(); h.stop()
  })
  it.each(['PRESENT', 'ABSENT'] as const)('clears %s directly and restores an unmarked row', async (status) => {
    const h = harness(status); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    h.controller.choose(h.controller.data.value!.items[0]!, status)
    await vi.waitFor(() => expect(h.controller.data.value?.items[0]?.status).toBeNull())
    expect(h.controller.confirmation.value).toBeNull(); h.stop()
  })
  it('a revised EXCUSED mark survives conflict and requires a new confirmation', async () => {
    const h = harness(); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull()); h.setConflict()
    h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED'); h.controller.confirm()
    await vi.waitFor(() => expect(h.controller.error.value).toContain('подтверди снятие заново'))
    expect(h.controller.data.value!.items[0]!.status).toBe('EXCUSED')
    expect(h.controller.data.value!.items[0]!.markRevision).toBe('changed-reason-revision')
    expect(h.controller.confirmation.value).toBeNull(); expect(h.refreshed).not.toHaveBeenCalled(); h.stop()
  })
  it('owner replacement invalidates a pending confirmation and stale write feedback', async () => {
    const h = harness(); await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED'); h.owner.studentId = 12; h.controller.confirm()
    expect(h.writes()).toBe(0)
    await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    h.hold(); h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED'); h.controller.confirm()
    await vi.waitFor(() => expect(h.writes()).toBe(1)); h.owner.offline = true; h.release()
    await new Promise((resolve) => setTimeout(resolve, 10))
    expect(h.controller.feedback.value).toBeNull(); expect(h.refreshed).not.toHaveBeenCalled(); expect(h.controller.data.value).toBeNull(); h.stop()
  })
  it('VIEW_STATS alone cannot write and a revoked excuse permission invalidates confirmation', async () => {
    const h = harness(); h.owner.markAllowed = false; h.owner.excuseAllowed = false
    await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED'); h.controller.confirm()
    expect(h.writes()).toBe(0)
    h.owner.excuseAllowed = true; await vi.waitFor(() => expect(h.controller.data.value).not.toBeNull())
    h.controller.choose(h.controller.data.value!.items[0]!, 'EXCUSED'); expect(h.controller.confirmation.value).not.toBeNull()
    h.owner.excuseAllowed = false; h.controller.confirm(); expect(h.writes()).toBe(0); h.stop()
  })
})
