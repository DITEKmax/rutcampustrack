import { describe, expect, it } from 'vitest'
import { HeadmanSubjectsApi } from './headman-subjects-client'
import { HeadmanGroupApi } from '../headman-group/headman-group-client'
const key = '11111111-1111-4111-8111-111111111111'
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status })
describe('mutation transport preserves receipts and authority', () => {
  it('reads server semester progress without interpreting the paged lessons as totals', async () => {
    let progress: unknown = {
      semesterId: 2,
      generatedAt: '2026-10-15T10:00:00Z',
      items: [{ subjectId: 3, lessonType: 'LECTURE', done: 2, total: 7 }],
    }
    const paths: string[] = []
    const api = new HeadmanSubjectsApi({
      accessToken: () => 'synthetic',
      fetcher: async (input) => {
        paths.push(String(input))
        return json({
          _embedded: { lessonResponseList: [] },
          page: { totalElements: 0 },
          semesterProgress: progress,
        })
      },
    })
    const semester = {
      id: 2,
      name: 'Семестр',
      dateFrom: '2026-09-01',
      dateTo: '2027-01-01',
      active: false,
    }
    expect((await api.semesterProgress(1, semester))?.items).toEqual([
      { subjectId: 3, lessonType: 'LECTURE', done: 2, total: 7 },
    ])
    expect(paths[0]).toContain('/api/schedule/groups/1/lessons?semesterId=2&')
    expect(paths[0]).toContain('size=1')
    progress = null
    expect(await api.semesterProgress(1, semester)).toBeNull()
    progress = { semesterId: 9, generatedAt: '2026-10-15T10:00:00Z', items: [] }
    await expect(api.semesterProgress(1, semester)).rejects.toThrow('чужой прогресс')
    progress = {
      semesterId: 2,
      generatedAt: '2026-10-15T10:00:00Z',
      items: [{ subjectId: 3, lessonType: 'LECTURE', done: 3, total: 2 }],
    }
    await expect(api.semesterProgress(1, semester)).rejects.toThrow('прогресс предмета')
  })
  it('retries401 DELETE with unchanged frozen identity across the Moscow midnight and only accepts204', async () => {
    let token = 'old',
      reads = 0
    const calls: { path: string; key: string | null; authorization: string | null }[] = []
    const api = new HeadmanSubjectsApi({
      accessToken: () => token,
      onUnauthorized: async () => {
        token = 'new'
      },
      fetcher: async (input, init) => {
        calls.push({
          path: String(input),
          key: new Headers(init?.headers).get('Idempotency-Key'),
          authorization: new Headers(init?.headers).get('Authorization'),
        })
        return ++reads === 1 ? json({}, 401) : new Response(null, { status: 204 })
      },
    })
    await api.removeTeacher(1, 10, 101, '2026-10-06', key)
    expect(calls).toEqual([
      {
        path: '/api/academic/subjects/1/teachers/10?assignmentId=101&validUntilExclusive=2026-10-06',
        key,
        authorization: 'Bearer old',
      },
      {
        path: '/api/academic/subjects/1/teachers/10?assignmentId=101&validUntilExclusive=2026-10-06',
        key,
        authorization: 'Bearer new',
      },
    ])
    const unconfirmed = new HeadmanSubjectsApi({
      accessToken: () => null,
      fetcher: async () => json({}, 200),
    })
    await expect(unconfirmed.removeTeacher(1, 10, 101, '2026-10-06', key)).rejects.toThrow(
      'не подтверждает',
    )
  })
  it('does not accept202 resume or an old session response as completion', async () => {
    const api = new HeadmanSubjectsApi({
      accessToken: () => null,
      fetcher: async () => json({}, 202),
    })
    await expect(api.resumeAdoption(101)).rejects.toThrow('не подтверждает')
    let active = true
    const old = new HeadmanSubjectsApi({
      accessToken: () => null,
      assertCurrent: () => {
        if (!active) throw new Error('old owner')
      },
      fetcher: async () => {
        active = false
        return new Response(null, { status: 204 })
      },
    })
    await expect(old.removeTeacher(1, 10, 101, '2026-10-06', key)).rejects.toThrow('old owner')
  })
  it('retains the legacy replacement requestKey body and future lifecycle state for durable recovery', async () => {
    let payload: unknown
    const api = new HeadmanSubjectsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        payload = JSON.parse(String(init?.body))
        return json({
          operationId: key,
          sourceAssignmentId: 101,
          targetAssignmentId: 102,
          sourceTeacherId: 10,
          targetTeacherId: 11,
          subjectId: 1,
          groupId: 4,
          semesterId: 1,
          lessonType: 'LECTURE',
          effectiveFrom: '2026-10-07',
          sourceValidUntilExclusive: '2026-10-07',
          targetValidUntilExclusive: null,
          state: 'FUTURE_PENDING_STATE',
          scheduleReceiptState: null,
          movedCount: 0,
          skippedCount: 0,
        })
      },
    })
    const result = await api.replaceAssignment(101, {
      replacementTeacherId: '11',
      effectiveFrom: '2026-10-07',
      requestKey: key,
    })
    expect(payload).toEqual({
      replacementTeacherId: '11',
      effectiveFrom: '2026-10-07',
      requestKey: key,
    })
    expect(result.state).toBe('FUTURE_PENDING_STATE')
  })
  it('preserves durable member roles including inactive/wrong-group grants without adopting legacy headmanbool', async () => {
    const roles = [
      { role: 'HEADMAN', status: 'REVOKED', groupId: 4 },
      { role: 'HEADMAN', status: 'ACTIVE', groupId: 8 },
    ]
    const api = new HeadmanGroupApi({
      accessToken: () => null,
      fetcher: async () =>
        json({
          _embedded: {
            userResponseList: [
              {
                id: 1,
                fullName: 'Участник',
                login: 'member',
                status: 'ACTIVE',
                groupId: 4,
                roles,
                headman: true,
              },
            ],
          },
        }),
    })
    const member = (await api.listMembers(4))[0]!
    expect(member.roles).toEqual(roles)
    expect(
      member.roles?.some((r) => r.role === 'HEADMAN' && r.status === 'ACTIVE' && r.groupId === 4),
    ).toBe(false)
  })
})
