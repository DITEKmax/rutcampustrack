import { describe, expect, it } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  HeadmanScheduleApi,
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

describe('ONE_OFF durable creation', () => {
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
