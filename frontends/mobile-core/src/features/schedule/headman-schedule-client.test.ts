import { describe, expect, it } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  acceptedRecurringTransfers,
  hideDeniedHeadmanSchedule,
  persistRecurringIntent,
  readRecurringIntent,
  recurringItemMatches,
  recurringReadback,
  recurringUpdate,
  retainRecurringBack,
  continueRecurringDeletion,
  type RecurringIntent,
} from './headman-recurring-intent'
import { createMobileNavigationStack, nestedRoute, rootRoute } from '../../shared/navigation'
import {
  HeadmanScheduleApi,
  HeadmanScheduleApiError,
  canCorrectRejectedOneOff,
  isOneOffDateWithinSemester,
  createGenerationBoundHeadmanScheduleApi,
  persistOneOffIntent,
  readOneOffIntent,
  canDeleteUnusedSeries,
  scheduleProblemCode,
  type HeadmanScheduleCreateInput,
  type HeadmanOneOffCreateInput,
} from './headman-schedule-client'

const input: HeadmanOneOffCreateInput = {
  groupId: 7,
  subjectId: 9,
  assignmentId: 501,
  date: '2026-10-12',
  lessonNumber: 3,
  startTime: '12:00:00',
  endTime: '13:30:00',
  classroom: 'А-101',
}
const key = '11111111-2222-4333-8444-555555555555'
const canonical = {
  id: 31,
  physicalLessonId: 42,
  groupId: 7,
  subjectId: 9,
  semesterId: 10,
  date: input.date,
  lessonNumber: 3,
}
const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const deleteIntent = (): RecurringIntent => ({
  owner: { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 },
  target: { id: 31, groupId: 7, semesterId: 10, subjectId: 9 },
  action: 'DELETE', input: null, key, rejected: false, transferIds: [],
  preview: { revision: 'a'.repeat(64), updatedCount: 0, removedCount: 4, restoredCount: 0, createdCount: 0, operationKind: 'DELETE_UNUSED_SERIES', deletable: true, protectedCount: 0, removableCount: 4, protectedReasons: [] },
})

describe('complete unused constructor series deletion', () => {
  it('fails closed for an old preview and exposes real protection without confusing automatic absence with manual history', async () => {
    const preview = deleteIntent().preview
    const old = { ...preview, operationKind: undefined }
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => json(old) })
    await expect(api.previewScheduleItem(31, null)).rejects.toThrow('полное удаление')
    const protectedPreview = { ...preview, deletable: false, protectedCount: 1, protectedReasons: [{ code: 'MANUAL_ATTENDANCE', count: 1 }] }
    const protectedApi = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => json(protectedPreview) })
    const result = await protectedApi.previewScheduleItem(31, null)
    expect(canDeleteUnusedSeries(result)).toBe(false)
    expect(result.protectedReasons).toEqual([{ code: 'MANUAL_ATTENDANCE', count: 1 }])
    expect(() => readRecurringIntent({ getItem: () => JSON.stringify({ ...deleteIntent(), preview: old }) }, deleteIntent().owner)).toThrow()
  })

  it('recovers a lost 202 with the identical key, persists the separate operation ID and completes without reading a deleted item', async () => {
    const durable = new Map<string, string>()
    const storage = { getItem: (scope: string) => durable.get(scope) ?? null, setItem: (scope: string, value: string) => { durable.set(scope, value) } }
    const intent = deleteIntent()
    persistRecurringIntent(storage, intent)
    const requests: { path: string; method: string; key: string | null; revision: string | null }[] = []
    const operationId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async (path, init) => {
      const headers = new Headers(init?.headers)
      requests.push({ path: String(path), method: init?.method ?? 'GET', key: headers.get('Idempotency-Key'), revision: headers.get('If-Match') })
      if (requests.length === 1) throw new TypeError('accepted response lost')
      const receipt = { operationId, state: requests.length === 2 ? 'COMMITTING' : 'COMPLETED', deletedCount: requests.length === 2 ? 0 : 4, protectedCount: 0, errorCode: null }
      return requests.length === 2 ? new Response(JSON.stringify(receipt), { status: 202 }) : json(receipt)
    } })
    const persist = (next: RecurringIntent) => persistRecurringIntent(storage, next)
    await expect(continueRecurringDeletion(api, intent, () => true, persist)).rejects.toThrow('lost')
    const restored = readRecurringIntent(storage, intent.owner)!
    const pending = await continueRecurringDeletion(api, restored, () => true, persist)
    expect(pending?.operation?.state).toBe('COMMITTING')
    expect(readRecurringIntent(storage, intent.owner)?.operationId).toBe(operationId)
    expect(readRecurringIntent(storage, { ...intent.owner, userId: '6' })).toBeNull()
    const complete = await continueRecurringDeletion(api, readRecurringIntent(storage, intent.owner)!, () => true, persist)
    expect(complete?.operation?.state).toBe('COMPLETED')
    expect(requests[1]).toEqual(requests[0])
    expect(requests[2]?.path).toBe(`/api/schedule/items/operations/${operationId}`)
    expect(requests.map((request) => request.method)).toEqual(['DELETE', 'DELETE', 'GET'])
  })

  it('does not accept an unknown operation, a generic 200 deletion ACK or a stale owner as completed deletion', async () => {
    const intent = deleteIntent()
    const unknown = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => new Response('{}', { status: 404 }) })
    await expect(unknown.getScheduleItemOperation(key)).rejects.toBeInstanceOf(HeadmanScheduleApiError)
    const malformed = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => json({}) })
    await expect(malformed.deleteScheduleItem(31, intent.preview.revision, key)).rejects.toThrow('состояние')
    let persisted = false
    const complete = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => new Response(null, { status: 204 }) })
    expect(await continueRecurringDeletion(complete, intent, () => false, () => { persisted = true })).toBeNull()
    expect(persisted).toBe(false)
  })

  it('requests legacy inactive templates only when explicitly selected', async () => {
    const paths: string[] = []
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async (path) => {
      paths.push(String(path))
      return json({ _embedded: { scheduleItemResponseList: [] }, page: { totalPages: 1 } })
    } })
    await api.listScheduleItems(7, 10)
    await api.listScheduleItems(7, 10, true)
    expect(paths[0]).not.toContain('includeInactive')
    expect(paths[1]).toContain('includeInactive=true')
  })
})

describe('constructor generation receipts', () => {
  const input: HeadmanScheduleCreateInput = { assignmentId: 501, groupId: 7, subjectId: 9, semesterId: 10, dayOfWeek: 1, lessonNumber: 3, startTime: '12:00:00', endTime: '13:30:00', weekType: 'ALL' }
  it.each([
    { generatedCount: 0, generatedFrom: null, generatedUntil: null },
    { generatedCount: 22, generatedFrom: '2026-09-01', generatedUntil: '2027-01-26' },
  ])('keeps the confirmed generation extent %j', async (receipt) => {
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => json({ id: 31, ...input, ...receipt }) })
    expect(await api.createScheduleItem(input, key)).toMatchObject(receipt)
  })
  it.each([
    {},
    { generatedCount: null, generatedFrom: null, generatedUntil: null },
    { generatedCount: 0 },
    { generatedCount: -1, generatedFrom: null, generatedUntil: null },
    { generatedCount: 1, generatedFrom: '2026-09-31', generatedUntil: '2026-10-01' },
    { generatedCount: 1, generatedFrom: '2026-10-01', generatedUntil: '2026-09-01' },
  ])('refuses missing or malformed generation metadata %j', async (receipt) => {
    const api = new HeadmanScheduleApi({ accessToken: () => null, fetcher: async () => json({ id: 31, ...input, ...receipt }) })
    await expect(api.createScheduleItem(input, key)).rejects.toThrow('число и период')
  })
  it('recognizes the deleted-series tombstone in the existing problem extras', () => {
    const error = new HeadmanScheduleApiError(new Response('{}', { status: 410 }), { extras: { code: 'CONSTRUCTOR_SERIES_DELETED' } })
    expect(scheduleProblemCode(error)).toBe('CONSTRUCTOR_SERIES_DELETED')
  })
})

describe('recurring lifecycle durable ownership', () => {
  it('confirms the exact accepted move before showing a later active placement for the same series', async () => {
    const owner = { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 }
    const target = {
      id: 31, groupId: 7, semesterId: 10, subjectId: 9, assignmentId: 501,
      dayOfWeek: 1, lessonNumber: 1, startTime: '08:30:00', endTime: '09:50:00',
      weekType: 'ALL' as const, active: true,
    }
    const preview = { revision: 'a'.repeat(64), updatedCount: 1, removedCount: 0, restoredCount: 0, createdCount: 0 }
    const input = { ...recurringUpdate(target, 'ODD', 'А-102', {
      dayOfWeek: 5, lessonNumber: 2, startTime: '10:05:00', endTime: '11:25:00',
    }), expectedRevision: preview.revision }
    const operationId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    const intent: RecurringIntent = { owner, target, input, key, action: 'UPDATE', preview, rejected: false, transferIds: [] }
    const accepted = { ...target, ...input, transfers: [{ operationId, state: 'PENDING' as const }] }
    const newer = { ...accepted, dayOfWeek: 4, weekType: 'EVEN' as const }
    const api = new HeadmanScheduleApi({
      accessToken: () => null,
      fetcher: async (_path, init) => {
        expect(init?.cache).toBe('no-store')
        return json(newer)
      },
    })
    expect(acceptedRecurringTransfers(intent, accepted)).toEqual([operationId])
    expect(() => acceptedRecurringTransfers(intent, newer)).toThrow('не подтвердил')
    expect(() => acceptedRecurringTransfers(intent, { ...accepted, transfers: [] })).toThrow('не подтвердил')
    expect(recurringReadback(intent, await api.getScheduleItem(target.id, true)))
      .toEqual({ dayOfWeek: 4, weekType: 'EVEN', superseded: true })
    expect(() => recurringReadback(intent, { ...newer, assignmentId: 502 })).toThrow('не подтвердил')
  })

  it('hides denied preview/apply/readback data, releases Back and retains the exact uncertain command only for its owner', async () => {
    const owner = { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 }
    const target = {
      id: 31,
      groupId: 7,
      semesterId: 10,
      subjectId: 9,
      dayOfWeek: 1,
      lessonNumber: 3,
      startTime: '12:00:00',
      endTime: '13:30:00',
      weekType: 'ALL' as const,
      active: true,
    }
    const preview = {
      revision: 'a'.repeat(64),
      updatedCount: 2,
      removedCount: 0,
      restoredCount: 0,
      createdCount: 0,
    }
    const input = { ...recurringUpdate(target, 'ODD', 'А-102'), expectedRevision: preview.revision }
    const intent = {
      owner,
      target,
      input,
      action: 'UPDATE' as const,
      preview,
      key,
      rejected: false,
      transferIds: [],
    }
    for (const phase of ['preview', 'apply', 'readback'] as const) {
      const values = new Map<string, string>()
      const storage = {
        getItem: (scope: string) => values.get(scope) ?? null,
        setItem: (scope: string, value: string) => {
          values.set(scope, value)
        },
      }
      if (phase !== 'preview') persistRecurringIntent(storage, intent)
      const pendingIntent = { value: phase === 'preview' ? null : intent }
      const view = {
        denied: { value: false },
        busy: { value: true },
        rows: [{ value: [target] }],
        editor: [pendingIntent, { value: target }],
      }
      const navigation = createMobileNavigationStack(rootRoute('headman-more'))
      navigation.push(nestedRoute('headman-more', 'headman-more/schedule/form', 'editor'))
      navigation.beforeBack(
        () => !retainRecurringBack(view.denied.value, view.busy.value, pendingIntent.value),
      )
      expect(navigation.back()?.id).toBe('headman-more/schedule/form')
      const api = new HeadmanScheduleApi({
        accessToken: () => null,
        fetcher: async (path, init) => {
          if (
            (phase === 'preview' && String(path).includes('lifecycle-preview')) ||
            (phase === 'apply' && init?.method === 'PUT') ||
            (phase === 'readback' && !init?.method)
          )
            return new Response('{}', { status: 403 })
          return json({ ...target, ...input, transfers: [] })
        },
      })
      try {
        if (phase === 'preview')
          await api.previewScheduleItem(target.id, recurringUpdate(target, 'ODD', 'А-102'))
        else {
          await api.updateScheduleItem(target.id, input, key)
          await api.getScheduleItem(target.id)
        }
        throw new Error('expected authorization refusal')
      } catch (cause) {
        expect(hideDeniedHeadmanSchedule(cause, view)).toBe(true)
      }
      expect(view.denied.value).toBe(true)
      expect(view.rows[0]?.value).toEqual([])
      expect(view.editor.every((field) => field.value === null)).toBe(true)
      expect(view.busy.value).toBe(false)
      expect(navigation.back()?.id).toBe('headman-more')
      expect(readRecurringIntent(storage, owner)).toEqual(phase === 'preview' ? null : intent)
      expect(readRecurringIntent(storage, { ...owner, userId: '6' })).toBeNull()
    }
    const unchanged = {
      denied: { value: false },
      busy: { value: true },
      rows: [{ value: [target] }],
      editor: [{ value: intent }],
    }
    expect(hideDeniedHeadmanSchedule(new TypeError('response lost'), unchanged)).toBe(false)
    expect(
      hideDeniedHeadmanSchedule(
        new HeadmanScheduleApiError(new Response('{}', { status: 401 }), null),
        unchanged,
      ),
    ).toBe(false)
    expect(unchanged.editor[0]?.value).toEqual(intent)
    expect(retainRecurringBack(false, true, intent)).toBe(true)
  })
  it('replays exact target/payload/revision/key after loss and reload, isolates owners, and fences a late empty DELETE response', async () => {
    const owner = { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 }
    const target = {
      id: 31,
      assignmentId: 501,
      groupId: 7,
      semesterId: 10,
      subjectId: 9,
      dayOfWeek: 1,
      lessonNumber: 3,
      startTime: '12:00:00',
      endTime: '13:30:00',
      weekType: 'ALL' as const,
      active: true,
    }
    const revision = 'a'.repeat(64)
    const preview = {
      revision,
      updatedCount: 2,
      removedCount: 1,
      restoredCount: 0,
      createdCount: 0,
    }
    const update = {
      ...recurringUpdate(target, 'ODD', 'А-102', {
        dayOfWeek: 4,
        lessonNumber: 2,
        startTime: '10:05:00',
        endTime: '11:25:00',
      }),
      expectedRevision: revision,
    }
    const values = new Map<string, string>()
    const storage = {
      getItem: (scope: string) => values.get(scope) ?? null,
      setItem: (scope: string, value: string) => {
        values.set(scope, value)
      },
    }
    persistRecurringIntent(storage, {
      owner,
      target,
      action: 'UPDATE',
      input: update,
      preview,
      key,
      rejected: false,
      transferIds: [],
    })
    const requests: Array<{
      path: string
      body: unknown
      key: string | null
      revision: string | null
    }> = []
    const api = new HeadmanScheduleApi({
      accessToken: () => null,
      fetcher: async (path, init) => {
        const headers = new Headers(init?.headers)
        requests.push({
          path: String(path),
          body: init?.body,
          key: headers.get('Idempotency-Key'),
          revision: headers.get('If-Match'),
        })
        if (requests.length === 1) throw new TypeError('accepted response lost')
        return init?.method === 'DELETE'
          ? new Response(null, { status: 204 })
          : json({ ...target, ...update, transfers: [] })
      },
    })
    await expect(api.updateScheduleItem(target.id, update, key)).rejects.toThrow('response lost')
    const recovered = readRecurringIntent(storage, owner)!
    expect(Object.isFrozen(recovered.input)).toBe(true)
    expect(readRecurringIntent(storage, { ...owner, userId: '6' })).toBeNull()
    expect(readRecurringIntent(storage, { ...owner, semesterId: 11 })).toBeNull()
    await api.updateScheduleItem(recovered.target.id!, recovered.input!, recovered.key)
    expect(requests[0]).toEqual(requests[1])
    expect(JSON.parse(String(requests[1]?.body))).toEqual(update)
    expect(recurringItemMatches({ ...target, ...update, startTime: '10:05', endTime: '11:25' }, update)).toBe(true)
    expect(recurringItemMatches({ ...target, ...update, dayOfWeek: target.dayOfWeek }, update)).toBe(false)
    expect(recurringItemMatches({ ...target, ...update, lessonNumber: target.lessonNumber }, update)).toBe(false)
    expect(recurringItemMatches({ ...target, ...update, startTime: '10:05:30' }, update)).toBe(false)
    // A DELETE has no JSON body, but retains the same revision/key during retries.
    const deletionKey = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    persistRecurringIntent(storage, {
      ...recovered,
      action: 'DELETE',
      input: null,
      key: deletionKey,
      preview: { ...recovered.preview, operationKind: 'DELETE_UNUSED_SERIES', deletable: true, protectedCount: 0, removableCount: 1, protectedReasons: [] },
    })
    const deletion = readRecurringIntent(storage, owner)!
    await expect(
      api.deleteScheduleItem(deletion.target.id!, deletion.preview.revision, deletion.key),
    ).resolves.toBeUndefined()
    expect(requests[2]?.revision).toBe(revision)
    expect(requests[2]?.key).toBe(deletionKey)
    let generation = 1
    let respond!: (response: Response) => void
    const bound = createGenerationBoundHeadmanScheduleApi(
      {
        currentGeneration: () => generation,
        accessTokenFor: () => null,
        refreshFor: async () => undefined,
      },
      async () =>
        new Promise<Response>((resolve) => {
          respond = resolve
        }),
    )
    const pending = bound.deleteScheduleItem(target.id, revision, key)
    generation = 2
    respond(new Response(null, { status: 204 }))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(() =>
      readRecurringIntent(
        { getItem: () => JSON.stringify({ ...recovered, input: { ...update, subjectId: 99 } }) },
        owner,
      ),
    ).toThrow('не совпадает')
  })

  it.each([
    ['omitted list', undefined],
    ['null list', null],
    ['nonarray list', {}],
    ['null transfer', [null]],
    ['missing UUID', [{ state: 'COMPLETED' }]],
    ['invalid UUID', [{ operationId: 'not-a-uuid', state: 'COMPLETED' }]],
    ['missing state', [{ operationId: key }]],
    ['invalid state', [{ operationId: key, state: 'ACCEPTED' }]],
  ])(
    'retains the exact durable command after a malformed PUT ACK (%s), then safely replays it',
    async (_label, transfers) => {
      const owner = { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 }
      const target = {
        id: 31,
        groupId: 7,
        semesterId: 10,
        subjectId: 9,
        dayOfWeek: 1,
        lessonNumber: 3,
        startTime: '12:00:00',
        endTime: '13:30:00',
        weekType: 'ALL' as const,
        active: true,
      }
      const preview = {
        revision: 'a'.repeat(64),
        updatedCount: 1,
        removedCount: 0,
        restoredCount: 0,
        createdCount: 0,
      }
      const update = {
        ...recurringUpdate(target, 'ODD', 'А-102'),
        expectedRevision: preview.revision,
      }
      const intent = {
        owner,
        target,
        input: update,
        action: 'UPDATE' as const,
        preview,
        key,
        rejected: false,
        transferIds: [],
      }
      const values = new Map<string, string>()
      const storage = {
        getItem: (scope: string) => values.get(scope) ?? null,
        setItem: (scope: string, value: string) => {
          values.set(scope, value)
        },
      }
      persistRecurringIntent(storage, intent)
      const durableBefore = [...values.entries()]
      const operationId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
      const accepted = { ...target, ...update, transfers: [{ operationId, state: 'PENDING' }] }
      const requests: Array<{ path: string; body: unknown; key: string | null }> = []
      const api = new HeadmanScheduleApi({
        accessToken: () => null,
        fetcher: async (path, init) => {
          requests.push({
            path: String(path),
            body: init?.body,
            key: new Headers(init?.headers).get('Idempotency-Key'),
          })
          return json(requests.length === 1 ? { ...target, ...update, transfers } : accepted)
        },
      })
      await expect(api.updateScheduleItem(target.id, update, key)).rejects.toThrow()
      expect([...values.entries()]).toEqual(durableBefore)
      const recovered = readRecurringIntent(storage, owner)!
      expect(recovered).toEqual(intent)
      expect(Object.isFrozen(recovered.input)).toBe(true)
      expect(recovered.transferIds).toEqual([])
      await expect(
        api.updateScheduleItem(recovered.target.id!, recovered.input!, recovered.key),
      ).resolves.toEqual(accepted)
      expect(requests[1]).toEqual(requests[0])
      expect(JSON.parse(String(requests[1]?.body))).toEqual(update)
    },
  )

  it('keeps accepted transfer IDs and the original intent when current-item or transfer readback is malformed', async () => {
    const owner = { userId: '5', sessionId: 'session1', groupId: 7, semesterId: 10 }
    const target = {
      id: 31,
      groupId: 7,
      semesterId: 10,
      subjectId: 9,
      dayOfWeek: 1,
      lessonNumber: 3,
      startTime: '12:00:00',
      endTime: '13:30:00',
      weekType: 'ALL' as const,
      active: true,
    }
    const preview = {
      revision: 'a'.repeat(64),
      updatedCount: 1,
      removedCount: 0,
      restoredCount: 0,
      createdCount: 0,
    }
    const update = {
      ...recurringUpdate(target, 'ODD', 'А-102'),
      expectedRevision: preview.revision,
    }
    const operationId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
    const intent = {
      owner,
      target,
      input: update,
      action: 'UPDATE' as const,
      preview,
      key,
      rejected: false,
      transferIds: [operationId],
    }
    const values = new Map<string, string>()
    const storage = {
      getItem: (scope: string) => values.get(scope) ?? null,
      setItem: (scope: string, value: string) => {
        values.set(scope, value)
      },
    }
    persistRecurringIntent(storage, intent)
    const durableBefore = [...values.entries()]
    let response: unknown = { ...target, ...update, transfers: [{ state: 'COMPLETED' }] }
    const api = new HeadmanScheduleApi({
      accessToken: () => null,
      fetcher: async () => json(response),
    })
    await expect(api.getScheduleItem(target.id)).rejects.toThrow()
    for (const malformed of [
      null,
      { operationId },
      { operationId, state: 'ACCEPTED' },
      { operationId: key, state: 'COMPLETED' },
    ]) {
      response = malformed
      await expect(api.getRecurringTransfer(operationId)).rejects.toThrow()
    }
    expect([...values.entries()]).toEqual(durableBefore)
    expect(readRecurringIntent(storage, owner)).toEqual(intent)
    response = { operationId, state: 'COMPLETED' }
    await expect(api.getRecurringTransfer(operationId)).resolves.toEqual(response)
  })
})

describe('ONE_OFF durable creation', () => {
  it('corrects only a proven first refusal; unknown/recovered/replay/stale errors retain the original intent', () => {
    const problem = {
      status: 409,
      type: 'https://api.rutcampustrack.ru/problems/one-off-create-rejected',
      instance: '/schedule/one-off-lessons',
      detail: 'Серверное описание может меняться',
    }
    const rejected = new HeadmanScheduleApiError(new Response('{}', { status: 409 }), problem)
    expect(canCorrectRejectedOneOff(rejected, true)).toBe(true)
    expect(canCorrectRejectedOneOff(rejected, false)).toBe(false)
    for (const type of ['conflict', 'lifecycle-not-ready', 'recurring-protocol-conflict']) {
      expect(
        canCorrectRejectedOneOff(
          new HeadmanScheduleApiError(new Response('{}', { status: 409 }), {
            ...problem,
            type: `https://api.rutcampustrack.ru/problems/${type}`,
          }),
          true,
        ),
      ).toBe(false)
    }
    expect(canCorrectRejectedOneOff(new TypeError('response lost'), true)).toBe(false)
    expect(canCorrectRejectedOneOff(new StaleSessionGenerationError(), true)).toBe(false)
    const values = new Map<string, string>()
    const storage = {
      getItem: (scope: string) => values.get(scope) ?? null,
      setItem: (scope: string, value: string) => {
        values.set(scope, value)
      },
    }
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
    expect(isOneOffDateWithinSemester('2027-01-31', '2026-10-01', '2026-09-01', '2027-01-31')).toBe(
      true,
    )
    expect(isOneOffDateWithinSemester('2027-02-01', '2026-10-01', '2026-09-01', '2027-01-31')).toBe(
      false,
    )
    expect(isOneOffDateWithinSemester('2026-09-30', '2026-10-01', '2026-09-01', '2027-01-31')).toBe(
      false,
    )
  })

  it('replays the original request and reads the same logical lesson at its new location', async () => {
    const values = new Map<string, string>()
    const storage = {
      getItem: (scope: string) => values.get(scope) ?? null,
      setItem: (scope: string, value: string) => {
        values.set(scope, value)
      },
    }
    const scope = 'user5:session1:HEADMAN:group7'
    persistOneOffIntent(storage, scope, { key, input })
    const requests: Array<{ path: string; key: string | null; body: string | null }> = []
    const moved = {
      ...canonical,
      physicalLessonId: 43,
      date: '2026-10-16',
      lessonNumber: 5,
      classroom: 'Б-204',
    }
    const api = new HeadmanScheduleApi({
      accessToken: () => 'owned-token',
      fetcher: async (path, init) => {
        requests.push({
          path: String(path),
          key: new Headers(init?.headers).get('Idempotency-Key'),
          body: typeof init?.body === 'string' ? init.body : null,
        })
        if (requests.length === 1) throw new TypeError('response lost after acceptance')
        return requests.length === 2
          ? json(moved, 201)
          : json({ _embedded: { oneOffLessonResponseList: [moved] } })
      },
    })
    await expect(api.createOneOffLesson(input, key)).rejects.toThrow('response lost')
    const recovered = readOneOffIntent(storage, scope)!
    expect(Object.isFrozen(recovered.input)).toBe(true)
    expect(readOneOffIntent(storage, 'user6:session2:HEADMAN:group7')).toBeNull()
    await expect(api.createOneOffLesson(recovered.input, recovered.key)).resolves.toEqual(moved)
    expect(requests[0]).toEqual(requests[1])
    expect(JSON.parse(requests[1]!.body!)).toEqual(input)
    await expect(api.listOneOffLessons(7, '2026-09-01', '2027-01-01')).resolves.toEqual([moved])
    expect(requests[2]!.path).toBe(
      '/api/schedule/one-off-lessons?groupId=7&dateFrom=2026-09-01&dateTo=2027-01-01',
    )
    expect(values.get(scope)).not.toContain('owned-token')
  })

  it.each([200, 202])('rejects a complete creation body without HTTP 201 (%s)', async (status) => {
    const api = new HeadmanScheduleApi({
      accessToken: () => null,
      fetcher: async () => json(canonical, status),
    })
    await expect(api.createOneOffLesson(input, key)).rejects.toThrow('Исходный запрос сохранён')
  })

  it.each([
    [{ groupId: 8 }, 'другого предмета или группы'],
    [{ subjectId: 10 }, 'другого предмета или группы'],
    [{ id: 0 }, 'id must be a positive integer'],
    [{ date: '2026-02-30' }, 'некорректную дату'],
    [{ lessonNumber: 9 }, 'некорректный номер'],
    [{ classroom: { name: 'Б-204' } }, 'некорректную аудиторию'],
  ])('rejects HTTP 201 with invalid receipt fields %j', async (fields, message) => {
    const api = new HeadmanScheduleApi({
      accessToken: () => null,
      fetcher: async () => json({ ...canonical, ...fields }, 201),
    })
    await expect(api.createOneOffLesson(input, key)).rejects.toThrow(message)
  })

  it('rejects a response from the old session and never retries its 401 with the new account', async () => {
    let generation = 1
    let respond!: (response: Response) => void
    let refreshes = 0
    let calls = 0
    const api = createGenerationBoundHeadmanScheduleApi(
      {
        currentGeneration: () => generation,
        accessTokenFor: () => 'old-token',
        refreshFor: async () => {
          refreshes += 1
        },
      },
      async () => {
        calls += 1
        return new Promise<Response>((resolve) => {
          respond = resolve
        })
      },
    )
    const pending = api.createOneOffLesson(input, key)
    generation = 2
    respond(new Response('{}', { status: 401 }))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(refreshes).toBe(0)
    expect(calls).toBe(1)
    await expect(api.createOneOffLesson(input, key)).rejects.toBeInstanceOf(
      StaleSessionGenerationError,
    )
    expect(calls).toBe(1)
  })

  it('fails closed for unavailable durable storage or a server response without a physical lesson', async () => {
    expect(() =>
      persistOneOffIntent(
        {
          setItem: () => {
            throw new Error('storage denied')
          },
        },
        'scope',
        { key, input },
      ),
    ).toThrow('storage denied')
    expect(() => readOneOffIntent({ getItem: () => '{broken' }, 'scope')).toThrow()
    const api = new HeadmanScheduleApi({
      accessToken: () => null,
      fetcher: async () => json({ ...canonical, physicalLessonId: undefined }, 201),
    })
    await expect(api.createOneOffLesson(input, key)).rejects.toThrow('physicalLessonId')
  })
})
