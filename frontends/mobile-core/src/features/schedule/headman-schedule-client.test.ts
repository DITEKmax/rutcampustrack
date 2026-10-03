import { describe, expect, it } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { persistRecurringIntent, readRecurringIntent, recurringUpdate } from './headman-recurring-intent'
import {
  HeadmanScheduleApi,
  HeadmanScheduleApiError,
  canCorrectRejectedOneOff,
  isOneOffDateWithinSemester,
  createGenerationBoundHeadmanScheduleApi,
  persistOneOffIntent,
  readOneOffIntent,
  type HeadmanOneOffCreateInput,
} from './headman-schedule-client'

const input: HeadmanOneOffCreateInput = {
  groupId: 7, subjectId: 9, assignmentId: 501, date: '2026-10-12',
  lessonNumber: 3, startTime: '12:00:00', endTime: '13:30:00', classroom: 'А-101',
}
const key = '11111111-2222-4333-8444-555555555555'
const canonical = { id: 31, physicalLessonId: 42, groupId: 7, subjectId: 9, semesterId: 10, date: input.date, lessonNumber: 3 }
const json = (body: unknown): Response => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

describe('recurring lifecycle durable ownership', () => {
  it('replays exact target/payload/revision/key after loss and reload, isolates owners, and fences a late empty DELETE response', async () => {
    const owner = { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 }
    const target = { id: 31, assignmentId: 501, groupId: 7, semesterId: 10, subjectId: 9,
      dayOfWeek: 1, lessonNumber: 3, startTime: '12:00:00', endTime: '13:30:00', weekType: 'ALL' as const, active: true }
    const revision = 'a'.repeat(64)
    const preview = { revision, updatedCount: 2, removedCount: 1, restoredCount: 0, createdCount: 0 }
    const update = { ...recurringUpdate(target, 'ODD', 'А-102'), expectedRevision: revision }
    const values = new Map<string, string>()
    const storage = { getItem: (scope: string) => values.get(scope) ?? null, setItem: (scope: string, value: string) => { values.set(scope, value) } }
    persistRecurringIntent(storage, { owner, target, action: 'UPDATE', input: update, preview, key, rejected: false, transferIds: [] })
    const requests: Array<{ path: string; body: unknown; key: string | null; revision: string | null }> = []
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async (path, init) => {
      const headers = new Headers(init?.headers)
      requests.push({ path: String(path), body: init?.body, key: headers.get('Idempotency-Key'), revision: headers.get('If-Match') })
      if (requests.length === 1) throw new TypeError('accepted response lost')
      return init?.method === 'DELETE' ? new Response(null, { status: 204 }) : json({ ...target, ...update })
    } })
    await expect(api.updateScheduleItem(target.id, update, key)).rejects.toThrow('response lost')
    const recovered = readRecurringIntent(storage, owner)!
    expect(Object.isFrozen(recovered.input)).toBe(true)
    expect(readRecurringIntent(storage, { ...owner, userId: '6' })).toBeNull()
    expect(readRecurringIntent(storage, { ...owner, semesterId: 11 })).toBeNull()
    await api.updateScheduleItem(recovered.target.id!, recovered.input!, recovered.key)
    expect(requests[0]).toEqual(requests[1])
    expect(JSON.parse(String(requests[1]?.body))).toEqual(update)
    // A DELETE has no JSON body, but retains the same revision/key during retries.
    const deletionKey = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    persistRecurringIntent(storage, { ...recovered, action: 'DELETE', input: null, key: deletionKey })
    const deletion = readRecurringIntent(storage, owner)!
    await expect(api.deleteScheduleItem(deletion.target.id!, deletion.preview.revision, deletion.key)).resolves.toBeUndefined()
    expect(requests[2]?.revision).toBe(revision)
    expect(requests[2]?.key).toBe(deletionKey)
    let generation = 1
    let respond!: (response: Response) => void
    const bound = createGenerationBoundHeadmanScheduleApi({ currentGeneration: () => generation,
      accessTokenFor: () => null, refreshFor: async () => undefined }, async () => new Promise<Response>((resolve) => { respond = resolve }))
    const pending = bound.deleteScheduleItem(target.id, revision, key)
    generation = 2
    respond(new Response(null, { status: 204 }))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(() => readRecurringIntent({ getItem: () => JSON.stringify({ ...recovered, input: { ...update, subjectId: 99 } }) }, owner)).toThrow('не совпадает')
  })
})

describe('ONE_OFF durable creation', () => {
  it('corrects only a proven first refusal; unknown/recovered/replay/stale errors retain the original intent', () => {
    const problem = { status: 409, type: 'https://api.rutcampustrack.ru/problems/one-off-create-rejected',
      instance: '/schedule/one-off-lessons', detail: 'Серверное описание может меняться' }
    const rejected = new HeadmanScheduleApiError(new Response('{}', { status: 409 }), problem)
    expect(canCorrectRejectedOneOff(rejected, true)).toBe(true)
    expect(canCorrectRejectedOneOff(rejected, false)).toBe(false)
    for (const type of ['conflict', 'lifecycle-not-ready', 'recurring-protocol-conflict']) {
      expect(canCorrectRejectedOneOff(new HeadmanScheduleApiError(new Response('{}', { status: 409 }),
        { ...problem, type: `https://api.rutcampustrack.ru/problems/${type}` }), true)).toBe(false)
    }
    expect(canCorrectRejectedOneOff(new TypeError('response lost'), true)).toBe(false)
    expect(canCorrectRejectedOneOff(new StaleSessionGenerationError(), true)).toBe(false)
    const values = new Map<string, string>()
    const storage = { getItem: (scope: string) => values.get(scope) ?? null, setItem: (scope: string, value: string) => { values.set(scope, value) } }
    persistOneOffIntent(storage, 'scope', { key, input })
    // Refusal after refresh cannot prove the earlier request was not accepted.
    if (canCorrectRejectedOneOff(rejected, false)) values.delete('scope')
    expect(readOneOffIntent(storage, 'scope')).toEqual({ key, input })
    // Fresh refusal unlocks correction, then a new immutable intent gets a new key.
    if (canCorrectRejectedOneOff(rejected, true)) values.delete('scope')
    const correctedKey = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    const correctedInput = { ...input, date: '2026-10-13' }
    persistOneOffIntent(storage, 'scope', { key: correctedKey, input: correctedInput })
    expect(readOneOffIntent(storage, 'scope')).toEqual({ key: correctedKey, input: correctedInput })
  })

  it('permits the inclusive final semester day but rejects the following day and dates before today', () => {
    expect(isOneOffDateWithinSemester('2027-01-31', '2026-10-01', '2026-09-01', '2027-01-31')).toBe(true)
    expect(isOneOffDateWithinSemester('2027-02-01', '2026-10-01', '2026-09-01', '2027-01-31')).toBe(false)
    expect(isOneOffDateWithinSemester('2026-09-30', '2026-10-01', '2026-09-01', '2027-01-31')).toBe(false)
  })

  it('recovers the exact request/key after an unknown outcome and refresh, then reads canonical physical identity', async () => {
    const values = new Map<string, string>()
    const storage = { getItem: (scope: string) => values.get(scope) ?? null, setItem: (scope: string, value: string) => { values.set(scope, value) } }
    const scope = 'user5:session1:HEADMAN:group7'
    persistOneOffIntent(storage, scope, { key, input })
    const requests: Array<{ path: string; key: string | null; body: string | null }> = []
    const api = new HeadmanScheduleApi({ accessToken: () => 'owned-token', fetcher: async (path, init) => {
      requests.push({ path: String(path), key: new Headers(init?.headers).get('Idempotency-Key'), body: typeof init?.body === 'string' ? init.body : null })
      if (requests.length === 1) throw new TypeError('response lost after acceptance')
      return requests.length === 2 ? json(canonical) : json({ _embedded: { oneOffLessonResponseList: [canonical] } })
    } })
    await expect(api.createOneOffLesson(input, key)).rejects.toThrow('response lost')
    const recovered = readOneOffIntent(storage, scope)!
    expect(Object.isFrozen(recovered.input)).toBe(true)
    expect(readOneOffIntent(storage, 'user6:session2:HEADMAN:group7')).toBeNull()
    await expect(api.createOneOffLesson(recovered.input, recovered.key)).resolves.toEqual(canonical)
    expect(requests[0]).toEqual(requests[1])
    expect(JSON.parse(requests[1]!.body!)).toEqual(input)
    await expect(api.listOneOffLessons(7, '2026-09-01', '2027-01-01')).resolves.toEqual([canonical])
    expect(requests[2]!.path).toBe('/api/schedule/one-off-lessons?groupId=7&dateFrom=2026-09-01&dateTo=2027-01-01')
    expect(values.get(scope)).not.toContain('owned-token')
  })

  it('rejects a response from the old session and never retries its 401 with the new account', async () => {
    let generation = 1
    let respond!: (response: Response) => void
    let refreshes = 0
    let calls = 0
    const api = createGenerationBoundHeadmanScheduleApi({
      currentGeneration: () => generation, accessTokenFor: () => 'old-token',
      refreshFor: async () => { refreshes += 1 },
    }, async () => { calls += 1; return new Promise<Response>((resolve) => { respond = resolve }) })
    const pending = api.createOneOffLesson(input, key)
    generation = 2
    respond(new Response('{}', { status: 401 }))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(refreshes).toBe(0)
    expect(calls).toBe(1)
    await expect(api.createOneOffLesson(input, key)).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(calls).toBe(1)
  })

  it('fails closed for unavailable durable storage or a server response without a physical lesson', async () => {
    expect(() => persistOneOffIntent({ setItem: () => { throw new Error('storage denied') } }, 'scope', { key, input })).toThrow('storage denied')
    expect(() => readOneOffIntent({ getItem: () => '{broken' }, 'scope')).toThrow()
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => json({ ...canonical, physicalLessonId: undefined }) })
    await expect(api.createOneOffLesson(input, key)).rejects.toThrow('physicalLessonId')
  })
})
