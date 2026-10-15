import { describe, expect, it } from 'vitest'
import {
  HeadmanRequestsApi,
  createGenerationBoundHeadmanRequestsApi,
} from './headman-requests-client'
import { formatRequestLessonTime } from './request-lesson-time'
import {
  freezeRequestDecision,
  sendRequestDecision,
  updateRequestExclusions,
} from './request-decision'
const summary = {
  id: 'whole-ticket',
  kind: 'EXCUSE',
  status: 'PENDING',
  studentId: 7,
  studentName: 'Мария',
  reason: 'Болезнь',
  comment: null,
  coverageStart: '2026-09-02',
  coverageEnd: '2026-09-04',
  lessonCount: 3,
  alreadyMarkedCount: 1,
  hasAttachments: false,
  createdAt: null,
  updatedAt: null,
  decisionBy: null,
  decisionAt: null,
  decisionComment: null,
}
const lesson = (id: number, date: string) => ({
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
  startsAt: null,
  endsAt: null,
  requestLessonStatus: 'ABSENT',
  attendanceStatus: 'PRESENT',
  attendanceSource: 'QR',
})
const original = [lesson(1, '2026-09-02')],
  affected = [lesson(21, '2026-09-10'), lesson(22, '2026-09-11'), lesson(23, '2026-09-12')]
const value = {
  decisionRevision: 'a'.repeat(64),
  summary,
  lessons: original,
  currentAffectedLessons: affected,
  attachments: [],
}
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
function receipt(body: {
  requestKey?: string
  expectedRevision?: string
  excludedOccurrenceIds?: readonly string[]
}) {
  return {
    requestKey: body.requestKey,
    expectedRevision: body.expectedRevision,
    includedLessons: affected.filter(
      (item) => !body.excludedOccurrenceIds?.includes(item.occurrenceId),
    ),
    excludedLessons: affected.filter((item) =>
      body.excludedOccurrenceIds?.includes(item.occurrenceId),
    ),
  }
}

describe('headman selected atomic decisions and authoritative targets', () => {
  it('keeps original evidence while exposing the complete current physical projection', async () => {
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    })
    const detail = await api.get('whole-ticket')
    expect(detail.lessons.map((item) => item.lessonId)).toEqual([1])
    expect(detail.currentAffectedLessons?.map((item) => item.lessonId)).toEqual([21, 22, 23])
    expect(detail.summary.lessonCount).toBe(3)
  })
  it.each([
    { alreadyMarkedCount: undefined },
    { alreadyMarkedCount: -1 },
    { alreadyMarkedCount: 0.5 },
    { alreadyMarkedCount: 4 },
    { lessonCount: undefined },
    { lessonCount: 0 },
    { lessonCount: Number.MAX_SAFE_INTEGER + 1 },
  ])('rejects an unconfirmed EXCUSE count in both queue and detail %#', async (counts) => {
    const malformed = { ...summary, ...counts }
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (url) =>
        json(
          String(url).includes('?')
            ? { content: [malformed], totalElements: 1 }
            : { ...value, summary: malformed },
        ),
    })
    await expect(api.list()).rejects.toThrow('Сервер не подтвердил количество пар заявки')
    await expect(api.get('whole-ticket')).rejects.toThrow(
      'Сервер не подтвердил количество пар заявки',
    )
  })
  it('preserves genuine zero and server totals independently of detail rows, including legacy LATE without N', async () => {
    const zero = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () =>
        json({ ...value, summary: { ...summary, lessonCount: 5, alreadyMarkedCount: 0 } }),
    })
    const detail = await zero.get('whole-ticket')
    expect(detail.summary).toMatchObject({ lessonCount: 5, alreadyMarkedCount: 0 })
    expect(detail.currentAffectedLessons).toHaveLength(3)
    const legacyLate = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () =>
        json({
          ...value,
          summary: {
            ...summary,
            kind: 'LATE_CHECKIN',
            lessonCount: 1,
            alreadyMarkedCount: undefined,
          },
        }),
    })
    expect((await legacyLate.get('whole-ticket')).summary).toMatchObject({
      kind: 'LATE_CHECKIN',
      lessonCount: 1,
      alreadyMarkedCount: 0,
    })
  })
  it.each([null, [affected[0], {}], [affected[0], affected[0]]])(
    'fails closed on malformed/partial pending current targets %#',
    async (currentAffectedLessons) => {
      const api = new HeadmanRequestsApi({
        accessToken: () => null,
        fetcher: async () => json({ ...value, currentAffectedLessons }),
      })
      await expect(api.get('whole-ticket')).rejects.toThrow()
    },
  )
  it('supports terminal historical reads without inventing current targets; detects a legacy binary', async () => {
    const terminal = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () =>
        json({
          ...value,
          summary: { ...summary, status: 'APPROVED' },
          currentAffectedLessons: null,
        }),
    })
    expect((await terminal.get('whole-ticket')).currentAffectedLessons).toBeNull()
    const legacy = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json({ summary, lessons: original, attachments: [] }),
    })
    expect((await legacy.get('whole-ticket')).currentAffectedLessons).toBeUndefined()
  })
  it('sends whole approval with revision/key and an empty exclusion set', async () => {
    let body: unknown,
      path = ''
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (input, init) => {
        path = String(input)
        body = JSON.parse(String(init?.body))
        return json({
          ...value,
          decisionSelection: receipt(body as Parameters<typeof receipt>[0]),
          summary: { ...summary, status: 'APPROVED' },
        })
      },
    })
    const detail = await new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    }).get('whole-ticket')
    const outcome = await sendRequestDecision(
      freezeRequestDecision(api, detail, 'APPROVED', 'unused'),
      false,
      () => true,
    )
    expect(outcome.status).toBe('done')
    expect(body).toEqual({
      decision: 'APPROVED',
      expectedRevision: value.decisionRevision,
      excludedOccurrenceIds: [],
      requestKey: expect.any(String),
    })
    expect(path).toBe('/api/attendance/requests/whole-ticket/decision')
  })
  it('reconciles before exact replay after lost response; never changes frozen rejection reason', async () => {
    const events: string[] = [],
      bodies: unknown[] = []
    let writes = 0
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        events.push(init?.method ?? 'GET')
        if (init?.method === 'POST') {
          bodies.push(JSON.parse(String(init.body)))
          if (++writes === 1) throw new TypeError('response lost')
          return json({
            ...value,
            decisionSelection: receipt(
              JSON.parse(String(init.body)) as Parameters<typeof receipt>[0],
            ),
            summary: { ...summary, status: 'REJECTED', decisionComment: 'Нет справки' },
          })
        }
        return json(value)
      },
    })
    const detail = await api.get('whole-ticket'),
      frozen = freezeRequestDecision(api, detail, 'REJECTED', '  Нет справки  ')
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown')
    expect((await sendRequestDecision(frozen, true, () => true)).status).toBe('done')
    expect(events).toEqual(['GET', 'POST', 'GET', 'POST'])
    expect(bodies).toHaveLength(2)
    expect(bodies[0]).toEqual(bodies[1])
    expect(bodies[0]).toMatchObject({
      decision: 'REJECTED',
      reason: 'Нет справки',
      expectedRevision: value.decisionRevision,
      excludedOccurrenceIds: [],
    })
    expect(Object.isFrozen(frozen)).toBe(true)
  })
  it('does not replay when GET observes terminal opposite action after an uncertain write', async () => {
    let posts = 0
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        if (init?.method === 'POST') {
          posts++
          throw new TypeError('unknown')
        }
        return json({
          ...value,
          summary: { ...summary, status: 'REJECTED', decisionComment: 'Решение другого оператора' },
        })
      },
    })
    const detail = await new HeadmanRequestsApi({
        accessToken: () => null,
        fetcher: async () => json(value),
      }).get('whole-ticket'),
      frozen = freezeRequestDecision(api, detail, 'APPROVED', '')
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown')
    expect((await sendRequestDecision(frozen, true, () => true)).status).toBe('conflict')
    expect(posts).toBe(1)
  })
  it('retains uncertainty and does not POST if reconciliation fails or detail belongs to another ticket', async () => {
    let posts = 0
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        if (init?.method === 'POST') posts++
        return json({ ...value, summary: { ...summary, id: 'other-ticket' } })
      },
    })
    const detail = await new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    }).get('whole-ticket')
    expect(
      (
        await sendRequestDecision(
          freezeRequestDecision(api, detail, 'APPROVED', ''),
          true,
          () => true,
        )
      ).status,
    ).toBe('unknown')
    expect(posts).toBe(0)
  })
  it('requires meaningful rejection reason within UI300 and refuses already terminal decisions', async () => {
    const api = new HeadmanRequestsApi({
        accessToken: () => null,
        fetcher: async () => json(value),
      }),
      detail = await api.get('whole-ticket')
    expect(() => freezeRequestDecision(api, detail, 'REJECTED', ' ')).toThrow()
    expect(() => freezeRequestDecision(api, detail, 'REJECTED', 'x'.repeat(301))).toThrow()
    expect(() =>
      freezeRequestDecision(
        api,
        { ...detail, summary: { ...detail.summary, status: 'APPROVED' } },
        'REJECTED',
        'Причина',
      ),
    ).toThrow()
  })
  it('generation-bound old actor cannot publish or replay a delayed result after session switch', async () => {
    let generation = 1,
      resolve!: (response: Response) => void,
      calls = 0
    const api = createGenerationBoundHeadmanRequestsApi(
      {
        currentGeneration: () => generation,
        accessTokenFor: () => 'synthetic',
        refreshFor: async () => {},
      },
      async () => {
        calls++
        return await new Promise<Response>((done) => {
          resolve = done
        })
      },
    )
    const read = api.get('whole-ticket')
    generation = 2
    resolve(json(value))
    await expect(read).rejects.toThrow('no longer current')
    await expect(api.decide('whole-ticket', 'APPROVED')).rejects.toThrow('no longer current')
    expect(calls).toBe(1)
  })
  it.each(['readOnly', 'offline', 'permission', 'unmount', 'revision', 'actor'] as const)(
    'blocks replay POST after delayed GET when %s changes and retains exact intent',
    async (change) => {
      let release!: (response: Response) => void,
        started!: () => void,
        posts = 0
      const readStarted = new Promise<void>((resolve) => {
        started = resolve
      })
      const access = {
        readOnly: false,
        offline: false,
        allowed: true,
        disposed: false,
        revision: 1,
        actor: 1,
      }
      const api = new HeadmanRequestsApi({
        accessToken: () => null,
        fetcher: async (_input, init) => {
          if (init?.method === 'POST') {
            posts++
            return json(value)
          }
          started()
          return await new Promise<Response>((resolve) => {
            release = resolve
          })
        },
      })
      const detail = await new HeadmanRequestsApi({
        accessToken: () => null,
        fetcher: async () => json(value),
      }).get('whole-ticket')
      const frozen = freezeRequestDecision(api, detail, 'REJECTED', 'Не хватает документа')
      const writeCurrent = () =>
        !access.readOnly &&
        !access.offline &&
        access.allowed &&
        !access.disposed &&
        access.revision === 1 &&
        access.actor === 1
      const retry = sendRequestDecision(frozen, true, writeCurrent)
      await readStarted
      if (change === 'readOnly') access.readOnly = true
      if (change === 'offline') access.offline = true
      if (change === 'permission') access.allowed = false
      if (change === 'unmount') access.disposed = true
      if (change === 'revision') access.revision = 2
      if (change === 'actor') access.actor = 2
      release(json(value))
      expect((await retry).status).toBe('blocked')
      expect(posts).toBe(0)
      expect(frozen).toMatchObject({
        id: 'whole-ticket',
        decision: 'REJECTED',
        reason: 'Не хватает документа',
        actor: api,
      })
      expect(Object.isFrozen(frozen)).toBe(true)
    },
  )
  it('checks access before the first send and never issues a write for a blocked frozen intent', async () => {
    let calls = 0
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => {
        calls++
        return json(value)
      },
    })
    const detail = await new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    }).get('whole-ticket')
    const frozen = freezeRequestDecision(api, detail, 'APPROVED', '')
    expect((await sendRequestDecision(frozen, false, () => false)).status).toBe('blocked')
    expect(calls).toBe(0)
    expect(frozen.decision).toBe('APPROVED')
  })
  it('reconciles identical terminal status/comment by another actor as neutral and never own-write success', async () => {
    let posts = 0
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        if (init?.method === 'POST') {
          posts++
          throw new TypeError('lost result')
        }
        return json({
          ...value,
          summary: {
            ...summary,
            status: 'REJECTED',
            decisionComment: 'Не хватает документа',
            decisionBy: 99,
          },
        })
      },
    })
    const detail = await new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    }).get('whole-ticket')
    const frozen = freezeRequestDecision(api, detail, 'REJECTED', 'Не хватает документа')
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown')
    const outcome = await sendRequestDecision(frozen, true, () => true)
    expect(outcome.status).toBe('conflict')
    expect(posts).toBe(1)
    if (outcome.status === 'conflict') expect(outcome.detail.summary.decisionBy).toBe(99)
  })
  it('freezes canonical occurrence exclusions and reconciles the exact immutable receipt after lost ACK', async () => {
    let saved: unknown,
      writes = 0
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        if (init?.method === 'POST') {
          writes++
          const body = JSON.parse(String(init.body)) as Parameters<typeof receipt>[0]
          saved = {
            ...value,
            decisionRevision: null,
            decisionSelection: receipt(body),
            summary: { ...summary, status: 'APPROVED' },
          }
          throw new TypeError('ACK lost after commit')
        }
        return json(saved ?? value)
      },
    })
    const detail = await api.get('whole-ticket'),
      draft = ['122']
    const frozen = freezeRequestDecision(api, detail, 'APPROVED', '', draft)
    draft.push('123')
    expect(frozen.selection?.excludedOccurrenceIds).toEqual(['122'])
    expect((await sendRequestDecision(frozen, false, () => true)).status).toBe('unknown')
    expect((await sendRequestDecision(frozen, true, () => true)).status).toBe('done')
    expect(writes).toBe(1)
  })
  it('refuses empty, duplicate, foreign, unavailable and late-only selections', async () => {
    const api = new HeadmanRequestsApi({
        accessToken: () => null,
        fetcher: async () => json(value),
      }),
      detail = await api.get('whole-ticket')
    for (const ids of [['121', '122', '123'], ['121', '121'], ['999']])
      expect(() => freezeRequestDecision(api, detail, 'APPROVED', '', ids)).toThrow()
    expect(() =>
      freezeRequestDecision(api, { ...detail, decisionRevision: null }, 'APPROVED', ''),
    ).toThrow()
    expect(() => freezeRequestDecision(api, detail, 'REJECTED', 'Причина', ['121'])).toThrow()
    const late = {
      ...detail,
      summary: { ...detail.summary, kind: 'LATE_CHECKIN' as const },
      decisionRevision: null,
    }
    expect(() => freezeRequestDecision(api, late, 'APPROVED', '', ['121'])).toThrow()
    expect(freezeRequestDecision(api, late, 'APPROVED', '').selection).toBeUndefined()
  })
  it('does not treat a mismatched subset receipt as successful approval', async () => {
    const api = new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        const body = JSON.parse(String(init?.body)) as Parameters<typeof receipt>[0]
        return json({
          ...value,
          decisionSelection: receipt({ ...body, excludedOccurrenceIds: [] }),
          summary: { ...summary, status: 'APPROVED' },
        })
      },
    })
    const detail = await new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    }).get('whole-ticket')
    expect(
      (
        await sendRequestDecision(
          freezeRequestDecision(api, detail, 'APPROVED', '', ['122']),
          false,
          () => true,
        )
      ).status,
    ).toBe('unknown')
  })
  it.each([
    ['18:00', '19:30', '18:00–19:30'],
    ['08:05:00', '09:35:00', '08:05–09:35'],
    ['08:05:12.123456', '09:35:59', '08:05–09:35'],
  ])(
    'renders Schedule LocalTime %s without slicing a fictitious date or converting timezone',
    (startsAt, endsAt, expected) => {
      expect(
        formatRequestLessonTime({ startsAt: startsAt!, endsAt: endsAt!, lessonNumber: 5 }),
      ).toBe(expected)
    },
  )
})

describe('headman local decision draft before atomic confirmation', () => {
  async function detail() {
    return new HeadmanRequestsApi({
      accessToken: () => null,
      fetcher: async () => json(value),
    }).get('whole-ticket')
  }
  it('excludes and restores in one local step without changing original attendance evidence', async () => {
    const current = await detail(),
      before = JSON.stringify(current),
      draft: readonly string[] = []
    const excluded = updateRequestExclusions(current, draft, '121', true)
    expect(excluded).toEqual(['121'])
    expect(Object.isFrozen(excluded)).toBe(true)
    expect(draft).toEqual([])
    expect(updateRequestExclusions(current, excluded, '121', false)).toEqual([])
    expect(JSON.stringify(current)).toBe(before)
    expect(current.currentAffectedLessons?.[0]?.attendanceStatus).toBe('PRESENT')
  })
  it('keeps one included pair, handles repeated clicks, and restores without losing other exclusions', async () => {
    const current = await detail()
    const one = updateRequestExclusions(current, [], '121', true)
    expect(updateRequestExclusions(current, one, '121', true)).toEqual(one)
    const two = updateRequestExclusions(current, one, '122', true)
    expect(() => updateRequestExclusions(current, two, '123', true)).toThrow(
      'Оставь хотя бы одну пару',
    )
    expect(updateRequestExclusions(current, two, '121', false)).toEqual(['122'])
  })
  it('refuses stale foreign/duplicate selections and nonselectable requests', async () => {
    const current = await detail()
    expect(() => updateRequestExclusions(current, [], '999', true)).toThrow('Состав пар изменился')
    expect(() => updateRequestExclusions(current, ['121', '121'], '122', true)).toThrow(
      'Состав пар изменился',
    )
    expect(() => updateRequestExclusions(current, ['999'], '121', false)).toThrow(
      'Состав пар изменился',
    )
    expect(() =>
      updateRequestExclusions({ ...current, decisionRevision: null }, [], '121', true),
    ).toThrow('Обнови заявку')
    expect(() =>
      updateRequestExclusions(
        { ...current, summary: { ...current.summary, kind: 'LATE_CHECKIN' } },
        [],
        '121',
        true,
      ),
    ).toThrow('Обнови заявку')
    expect(() =>
      updateRequestExclusions(
        { ...current, summary: { ...current.summary, status: 'APPROVED' } },
        [],
        '121',
        true,
      ),
    ).toThrow('Обнови заявку')
  })
  it('freezes only the final subset and preserves that intent even if a separate local draft changes', async () => {
    const current = await detail(),
      actor = new HeadmanRequestsApi({ accessToken: () => null, fetcher: async () => json(value) })
    const draft = updateRequestExclusions(current, [], '122', true)
    const frozen = freezeRequestDecision(actor, current, 'APPROVED', '', draft)
    const payload = JSON.stringify(frozen.selection),
      key = frozen.selection?.requestKey
    expect(frozen.includedOccurrenceIds).toEqual(['121', '123'])
    expect(updateRequestExclusions(current, draft, '122', false)).toEqual([])
    expect(JSON.stringify(frozen.selection)).toBe(payload)
    expect(frozen.selection?.requestKey).toBe(key)
    expect(frozen.selection?.excludedOccurrenceIds).toEqual(['122'])
  })
})
