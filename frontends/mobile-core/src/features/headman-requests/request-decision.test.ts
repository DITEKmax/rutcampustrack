import { describe, expect, it } from 'vitest'
import { HeadmanRequestsApi, createGenerationBoundHeadmanRequestsApi } from './headman-requests-client'
import { formatRequestLessonTime } from './request-lesson-time'
import { freezeRequestDecision, sendRequestDecision } from './request-decision'
const summary = { id: 'whole-ticket', kind: 'EXCUSE', status: 'PENDING', studentId: 7, studentName: 'Мария', reason: 'Болезнь', comment: null, coverageStart: '2026-09-02', coverageEnd: '2026-09-04', lessonCount: 3, alreadyMarkedCount: 1, hasAttachments: false, createdAt: null, updatedAt: null, decisionBy: null, decisionAt: null, decisionComment: null }
const lesson = (id: number, date: string) => ({ lessonId: id, groupId: 4, subjectId: 3, subjectName: 'Математика', subjectType: 'LECTURE', semesterId: 1, lessonNumber: 1, date, startsAt: null, endsAt: null, requestLessonStatus: 'ABSENT', attendanceStatus: 'PRESENT', attendanceSource: 'QR' })
const original = [lesson(1, '2026-09-02')], affected = [lesson(21, '2026-09-10'), lesson(22, '2026-09-11'), lesson(23, '2026-09-12')]
const value = { summary, lessons: original, currentAffectedLessons: affected, attachments: [] }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
describe('headman whole-ticket decisions and authoritative targets', () => {
  it('keeps original evidence while exposing the complete current physical projection', async () => {
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) })
    const detail = await api.get('whole-ticket')
    expect(detail.lessons.map((item) => item.lessonId)).toEqual([1])
    expect(detail.currentAffectedLessons?.map((item) => item.lessonId)).toEqual([21, 22, 23])
    expect(detail.summary.lessonCount).toBe(3)
  })
  it.each([null, [affected[0], {}], [affected[0], affected[0]]])('fails closed on malformed/partial pending current targets %#', async (currentAffectedLessons) => {
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json({ ...value, currentAffectedLessons }) })
    await expect(api.get('whole-ticket')).rejects.toThrow()
  })
  it('supports terminal historical reads without inventing current targets; detects a legacy binary', async () => {
    const terminal = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json({ ...value, summary: { ...summary, status: 'APPROVED' }, currentAffectedLessons: null }) })
    expect((await terminal.get('whole-ticket')).currentAffectedLessons).toBeNull()
    const legacy = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json({ summary, lessons: original, attachments: [] }) })
    expect((await legacy.get('whole-ticket')).currentAffectedLessons).toBeUndefined()
  })
  it('sends a whole-ticket decision with no filtered/subset lesson IDs and drops approval draft reasons', async () => {
    let body: unknown, path = ''
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async (input, init) => { path = String(input); body = JSON.parse(String(init?.body)); return json({ ...value, summary: { ...summary, status: 'APPROVED' } }) } })
    const detail = await new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }).get('whole-ticket')
    const outcome = await sendRequestDecision(freezeRequestDecision(api, detail, 'APPROVED', 'unused'), false, () => true)
    expect(outcome.status).toBe('done'); expect(body).toEqual({ decision: 'APPROVED' }); expect(path).toBe('/api/attendance/requests/whole-ticket/decision')
  })
  it('reconciles before exact replay after lost response; never changes frozen rejection reason', async () => {
    const events: string[] = [], bodies: unknown[] = []; let writes = 0
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async (_input, init) => {
      events.push(init?.method ?? 'GET')
      if (init?.method === 'POST') { bodies.push(JSON.parse(String(init.body))); if (++writes === 1) throw new TypeError('response lost'); return json({ ...value, summary: { ...summary, status: 'REJECTED', decisionComment: 'Нет справки' } }) }
      return json(value)
    } })
    const detail = await api.get('whole-ticket'), frozen = freezeRequestDecision(api, detail, 'REJECTED', '  Нет справки  ')
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown')
    expect((await sendRequestDecision(frozen, true, () => true)).status).toBe('done')
    expect(events).toEqual(['GET', 'POST', 'GET', 'POST']); expect(bodies).toEqual([{ decision: 'REJECTED', reason: 'Нет справки' }, { decision: 'REJECTED', reason: 'Нет справки' }]); expect(Object.isFrozen(frozen)).toBe(true)
  })
  it('does not replay when GET observes terminal opposite action after an uncertain write', async () => {
    let posts = 0
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async (_input, init) => { if (init?.method === 'POST') { posts++; throw new TypeError('unknown') } return json({ ...value, summary: { ...summary, status: 'REJECTED', decisionComment: 'Решение другого оператора' } }) } })
    const detail = await new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }).get('whole-ticket'), frozen = freezeRequestDecision(api, detail, 'APPROVED', '')
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown'); expect((await sendRequestDecision(frozen, true, () => true)).status).toBe('conflict'); expect(posts).toBe(1)
  })
  it('retains uncertainty and does not POST if reconciliation fails or detail belongs to another ticket', async () => {
    let posts = 0
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async (_input, init) => { if (init?.method === 'POST') posts++; return json({ ...value, summary: { ...summary, id: 'other-ticket' } }) } })
    const detail = await new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }).get('whole-ticket')
    expect((await sendRequestDecision(freezeRequestDecision(api, detail, 'APPROVED', ''), true, () => true)).status).toBe('unknown'); expect(posts).toBe(0)
  })
  it('requires meaningful rejection reason within UI300 and refuses already terminal decisions', async () => {
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }), detail = await api.get('whole-ticket')
    expect(() => freezeRequestDecision(api, detail, 'REJECTED', ' ')).toThrow(); expect(() => freezeRequestDecision(api, detail, 'REJECTED', 'x'.repeat(301))).toThrow()
    expect(() => freezeRequestDecision(api, { ...detail, summary: { ...detail.summary, status: 'APPROVED' } }, 'REJECTED', 'Причина')).toThrow()
  })
  it('generation-bound old actor cannot publish or replay a delayed result after session switch', async () => {
    let generation = 1, resolve!: (response: Response) => void, calls = 0
    const api = createGenerationBoundHeadmanRequestsApi({ currentGeneration: () => generation, accessTokenFor: () => 'synthetic', refreshFor: async () => {} }, async () => { calls++; return await new Promise<Response>((done) => { resolve = done }) })
    const read = api.get('whole-ticket'); generation = 2; resolve(json(value))
    await expect(read).rejects.toThrow('no longer current')
    await expect(api.decide('whole-ticket', 'APPROVED')).rejects.toThrow('no longer current'); expect(calls).toBe(1)
  })
  it.each(['readOnly', 'offline', 'permission', 'unmount', 'revision', 'actor'] as const)('blocks replay POST after delayed GET when %s changes and retains exact intent', async (change) => {
    let release!: (response: Response) => void, started!: () => void, posts = 0
    const readStarted = new Promise<void>((resolve) => { started = resolve })
    const access = { readOnly: false, offline: false, allowed: true, disposed: false, revision: 1, actor: 1 }
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async (_input, init) => {
      if (init?.method === 'POST') { posts++; return json(value) }
      started(); return await new Promise<Response>((resolve) => { release = resolve })
    } })
    const detail = await new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }).get('whole-ticket')
    const frozen = freezeRequestDecision(api, detail, 'REJECTED', 'Не хватает документа')
    const writeCurrent = () => !access.readOnly && !access.offline && access.allowed && !access.disposed && access.revision === 1 && access.actor === 1
    const retry = sendRequestDecision(frozen, true, writeCurrent)
    await readStarted
    if (change === 'readOnly') access.readOnly = true
    if (change === 'offline') access.offline = true
    if (change === 'permission') access.allowed = false
    if (change === 'unmount') access.disposed = true
    if (change === 'revision') access.revision = 2
    if (change === 'actor') access.actor = 2
    release(json(value))
    expect((await retry).status).toBe('blocked'); expect(posts).toBe(0)
    expect(frozen).toMatchObject({ id: 'whole-ticket', decision: 'REJECTED', reason: 'Не хватает документа', actor: api })
    expect(Object.isFrozen(frozen)).toBe(true)
  })
  it('checks access before the first send and never issues a write for a blocked frozen intent', async () => {
    let calls = 0
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => { calls++; return json(value) } })
    const detail = await new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }).get('whole-ticket')
    const frozen = freezeRequestDecision(api, detail, 'APPROVED', '')
    expect((await sendRequestDecision(frozen, false, () => false)).status).toBe('blocked')
    expect(calls).toBe(0); expect(frozen.decision).toBe('APPROVED')
  })
  it('reconciles identical terminal status/comment by another actor as neutral and never own-write success', async () => {
    let posts = 0
    const api = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async (_input, init) => {
      if (init?.method === 'POST') { posts++; throw new TypeError('lost result') }
      return json({ ...value, summary: { ...summary, status: 'REJECTED', decisionComment: 'Не хватает документа', decisionBy: 99 } })
    } })
    const detail = await new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) }).get('whole-ticket')
    const frozen = freezeRequestDecision(api, detail, 'REJECTED', 'Не хватает документа')
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown')
    const outcome = await sendRequestDecision(frozen, true, () => true)
    expect(outcome.status).toBe('conflict'); expect(posts).toBe(1)
    if (outcome.status === 'conflict') expect(outcome.detail.summary.decisionBy).toBe(99)
  })
  it.each([
    ['18:00', '19:30', '18:00–19:30'],
    ['08:05:00', '09:35:00', '08:05–09:35'],
    ['08:05:12.123456', '09:35:59', '08:05–09:35'],
  ])('renders Schedule LocalTime %s without slicing a fictitious date or converting timezone', (startsAt, endsAt, expected) => {
    expect(formatRequestLessonTime({ startsAt: startsAt!, endsAt: endsAt!, lessonNumber: 5 })).toBe(expected)
  })
})
