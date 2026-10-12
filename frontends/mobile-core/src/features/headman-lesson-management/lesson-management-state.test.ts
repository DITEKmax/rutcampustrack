import { describe, expect, it, vi } from 'vitest'
import {
  LessonManagementState,
  lessonCanRoom,
  lessonCanTransfer,
  transferEffectiveRoom,
  readManagementIntent,
  type ManagementJournal,
  type ManagementSchedule,
} from './lesson-management-state'
import {
  HeadmanJournalApi,
  HeadmanJournalApiError,
} from '../headman-journal/headman-journal-client'
import type {
  HeadmanJournalLesson,
  HeadmanLessonRoomReceipt,
} from '../headman-journal/headman-journal-client'
const owner = { userId: '1', sessionId: 'a', groupId: 4 },
  key = '11111111-1111-4111-8111-111111111111'
const lesson: HeadmanJournalLesson = {
  id: 41,
  groupId: 4,
  subjectId: 1,
  date: '2026-10-12',
  lessonNumber: 3,
  startTime: '11:20:00',
  endTime: '12:50:00',
  room: 'А-401',
  lessonType: 'LECTURE',
  status: 'PLANNED',
  current: true,
  occurrenceId: '141',
  occurrenceRevision: '9007199254740993',
  transferOperationId: null,
  transferState: null,
}
function fixture() {
  const rows = new Map<string, string>(),
    storage = {
      getItem: (k: string) => rows.get(k) ?? null,
      setItem: (k: string, v: string) => {
        rows.set(k, v)
      },
      removeItem: (k: string) => {
        rows.delete(k)
      },
    }
  const receipt: HeadmanLessonRoomReceipt = {
    operationId: key,
    state: 'COMPLETED',
    lessonId: '41',
    occurrenceId: '141',
    revision: '9007199254740994',
    room: 'Б-204',
  }
  const journal: ManagementJournal = {
    listLessons: vi.fn(async () => [lesson]),
    cancelLesson: vi.fn(async () => ({
      ...lesson,
      status: 'CANCELLED' as const,
      occurrenceRevision: '9007199254740994',
    })),
    restoreLesson: vi.fn(async () => lesson),
    transferLesson: vi.fn(async () => {
      throw new TypeError('not used')
    }),
    getLessonTransfer: vi.fn(async () => {
      throw new TypeError('not used')
    }),
    changeLessonRoom: vi.fn(async () => receipt),
  }
  const schedule: ManagementSchedule = {
    listSemesters: vi.fn(async () => []),
    listAssignments: vi.fn(async () => []),
    academicWeekPolicy: vi.fn(async () => {
      throw new Error('not used')
    }),
    lessonSlots: vi.fn(async () => {
      throw new Error('not used')
    }),
    createOneOffLesson: vi.fn(async () => {
      throw new Error('not used')
    }),
  }
  const create = () =>
    new LessonManagementState(
      journal,
      schedule,
      owner,
      storage,
      () => undefined,
      () => undefined,
    )
  return { rows, storage, journal, schedule, create }
}
describe('management durable physical actions', () => {
  it('blocks any POST when storage refuses', async () => {
    const f = fixture()
    const state = new LessonManagementState(
      f.journal,
      f.schedule,
      owner,
      {
        ...f.storage,
        setItem: () => {
          throw new Error('refused')
        },
      },
      () => undefined,
      () => undefined,
    )
    await state.submit({
      kind: 'ROOM',
      target: lesson,
      key,
      input: { room: 'Б-204', expectedRevision: lesson.occurrenceRevision! },
    })
    expect(f.journal.changeLessonRoom).not.toHaveBeenCalled()
    expect(state.locked).toBe(true)
  })
  it('reload replays exactly one keyed room command after read, with decimal revision preserved', async () => {
    const f = fixture()
    vi.mocked(f.journal.changeLessonRoom).mockRejectedValueOnce(new TypeError('reply lost'))
    const state = f.create()
    await state.submit({
      kind: 'ROOM',
      target: lesson,
      key,
      input: { room: 'Б-204', expectedRevision: lesson.occurrenceRevision! },
    })
    expect(state.locked).toBe(true)
    state.dispose()
    const restored = f.create()
    expect(restored.intent?.command.kind).toBe('ROOM')
    await restored.recover()
    expect(f.journal.changeLessonRoom).toHaveBeenLastCalledWith(
      41,
      { room: 'Б-204', expectedRevision: '9007199254740993' },
      key,
    )
    expect(restored.intent).toBeNull()
    expect(readManagementIntent(f.storage, { ...owner, sessionId: 'other' })).toBeNull()
  })
  it('lost unkeyed cancellation reconciles GET and never repeats mutation', async () => {
    const f = fixture()
    vi.mocked(f.journal.cancelLesson).mockRejectedValueOnce(new TypeError('lost'))
    const state = f.create()
    await state.submit({ kind: 'CANCEL', target: lesson, reason: 'Преподаватель отсутствует' })
    await state.recover()
    expect(state.locked).toBe(true)
    expect(f.journal.cancelLesson).toHaveBeenCalledTimes(1)
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([
      { ...lesson, status: 'CANCELLED', occurrenceRevision: '9007199254740994' },
    ])
    await state.recover()
    expect(state.intent).toBeNull()
    expect(f.journal.cancelLesson).toHaveBeenCalledTimes(1)
  })
  it('rejects a receipt for another occurrence without unlocking new actions', async () => {
    const f = fixture()
    vi.mocked(f.journal.changeLessonRoom).mockResolvedValue({
      ...(await f.journal.changeLessonRoom(41, { room: 'Б-204', expectedRevision: '1' }, key)),
      occurrenceId: '142',
    })
    const state = f.create()
    await state.submit({
      kind: 'ROOM',
      target: lesson,
      key,
      input: { room: 'Б-204', expectedRevision: lesson.occurrenceRevision! },
    })
    expect(state.intent).not.toBeNull()
    expect(state.notice).toBeNull()
  })
  it('owner replacement suppresses delayed publication and retains old owner intent', async () => {
    const f = fixture()
    let resolve: (value: HeadmanLessonRoomReceipt) => void = () => undefined
    vi.mocked(f.journal.changeLessonRoom).mockImplementation(
      () =>
        new Promise((r) => {
          resolve = r
        }),
    )
    const state = f.create(),
      pending = state.submit({
        kind: 'ROOM',
        target: lesson,
        key,
        input: { room: 'Б-204', expectedRevision: lesson.occurrenceRevision! },
      })
    state.dispose()
    resolve({
      operationId: key,
      state: 'COMPLETED',
      lessonId: '41',
      occurrenceId: '141',
      revision: '2',
      room: 'Б-204',
    })
    await pending
    expect(readManagementIntent(f.storage, owner)).not.toBeNull()
    expect(state.notice).toBeNull()
  })
  it('rechecks access after recovery GET before exact replay and retains the frozen owner intent', async () => {
    const f = fixture()
    vi.mocked(f.journal.changeLessonRoom).mockRejectedValueOnce(new TypeError('lost'))
    let allowed = true
    const state = new LessonManagementState(
      f.journal,
      f.schedule,
      owner,
      f.storage,
      () => undefined,
      () => undefined,
      () => allowed,
    )
    await state.submit({
      kind: 'ROOM',
      target: lesson,
      key,
      input: { room: 'Б-204', expectedRevision: lesson.occurrenceRevision! },
    })
    vi.mocked(f.journal.listLessons).mockImplementationOnce(async () => {
      allowed = false
      return [lesson]
    })
    await state.recover()
    expect(f.journal.changeLessonRoom).toHaveBeenCalledTimes(1)
    expect(state.intent).not.toBeNull()
  })
  it('accepts restored current generation after assignment replacement', async () => {
    const f = fixture(),
      target = { ...lesson, status: 'CANCELLED' as const },
      restored = { ...lesson, id: 42, occurrenceRevision: '9007199254740994' }
    vi.mocked(f.journal.restoreLesson).mockResolvedValueOnce({
      ...restored,
      occurrenceRevision: null,
    })
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([restored])
    const state = f.create()
    await state.submit({ kind: 'RESTORE', target })
    expect(state.intent).toBeNull()
    expect(state.notice).toBe('Пара восстановлена.')
    expect(f.journal.restoreLesson).toHaveBeenCalledOnce()
  })
  it.each([null, undefined])(
    'cancel wire ACK with occurrenceRevision=%s automatically confirms authoritative GET',
    async (ackRevision) => {
      const f = fixture(),
        wireApi = new HeadmanJournalApi({
          accessToken: () => null,
          fetcher: async () =>
            new Response(
              JSON.stringify({ ...lesson, status: 'CANCELLED', occurrenceRevision: ackRevision }),
              { headers: { 'Content-Type': 'application/json' } },
            ),
        })
      vi.mocked(f.journal.cancelLesson).mockImplementationOnce((id, reason) =>
        wireApi.cancelLesson(id, reason),
      )
      vi.mocked(f.journal.listLessons).mockResolvedValueOnce([
        { ...lesson, status: 'CANCELLED', occurrenceRevision: '9007199254740994' },
      ])
      const state = f.create()
      await state.submit({ kind: 'CANCEL', target: lesson, reason: 'Нет преподавателя' })
      expect(state.intent).toBeNull()
      expect(state.notice).toBe('Пара отменена.')
      expect(f.journal.listLessons).toHaveBeenCalledWith(4, lesson.date, lesson.date)
      expect(f.journal.cancelLesson).toHaveBeenCalledTimes(1)
    },
  )
  it.each(['CANCEL', 'RESTORE'] as const)(
    'successful %s ACK and failed GET retain the sent intent on reload',
    async (kind) => {
      const f = fixture(),
        target = kind === 'RESTORE' ? { ...lesson, status: 'CANCELLED' as const } : lesson
      vi.mocked(
        kind === 'CANCEL' ? f.journal.cancelLesson : f.journal.restoreLesson,
      ).mockResolvedValueOnce({
        ...lesson,
        status: kind === 'CANCEL' ? 'CANCELLED' : 'PLANNED',
        occurrenceRevision: null,
      })
      vi.mocked(f.journal.listLessons).mockRejectedValueOnce(new TypeError('GET unavailable'))
      const state = f.create()
      await state.submit(
        kind === 'CANCEL' ? { kind, target, reason: 'Нет преподавателя' } : { kind, target },
      )
      expect(state.intent?.sent).toBe(true)
      state.dispose()
      const reloaded = f.create()
      vi.mocked(f.journal.listLessons).mockResolvedValueOnce([
        {
          ...lesson,
          status: kind === 'CANCEL' ? 'CANCELLED' : 'PLANNED',
          id: kind === 'RESTORE' ? 42 : 41,
          occurrenceRevision: '9007199254740994',
        },
      ])
      await reloaded.recover()
      expect(reloaded.intent).toBeNull()
      expect(
        kind === 'CANCEL' ? f.journal.cancelLesson : f.journal.restoreLesson,
      ).toHaveBeenCalledTimes(1)
    },
  )
  it('a definitive GET refusal after successful mutation ACK never discards the sent command', async () => {
    const f = fixture()
    vi.mocked(f.journal.cancelLesson).mockResolvedValueOnce({
      ...lesson,
      status: 'CANCELLED',
      occurrenceRevision: null,
    })
    vi.mocked(f.journal.listLessons).mockRejectedValueOnce(
      new HeadmanJournalApiError(new Response(null, { status: 404 }), { detail: 'gone' }),
    )
    const state = f.create()
    await state.submit({ kind: 'CANCEL', target: lesson, reason: 'Нет преподавателя' })
    expect(state.intent?.sent).toBe(true)
    expect(f.journal.cancelLesson).toHaveBeenCalledTimes(1)
  })
  it('lost restore reply reload reconciles new current generation without a second PATCH', async () => {
    const f = fixture()
    vi.mocked(f.journal.restoreLesson).mockRejectedValueOnce(new TypeError('lost'))
    const state = f.create()
    await state.submit({ kind: 'RESTORE', target: { ...lesson, status: 'CANCELLED' } })
    state.dispose()
    const reloaded = f.create()
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([
      { ...lesson, id: 42, occurrenceRevision: '9007199254740994' },
    ])
    await reloaded.recover()
    expect(reloaded.intent).toBeNull()
    expect(f.journal.restoreLesson).toHaveBeenCalledTimes(1)
  })
  it.each([
    { occurrenceId: '142' },
    { lessonNumber: 4 },
    { date: '2026-10-13' },
    { subjectId: 2 },
    { groupId: 5 },
    { occurrenceRevision: lesson.occurrenceRevision },
    { status: 'TRANSFERRED' as const },
    { current: false },
  ])('foreign or unconfirmed restore ACK and GET retain lock: %j', async (delta) => {
    const f = fixture(),
      other = { ...lesson, id: 42, occurrenceRevision: '9007199254740994', ...delta }
    vi.mocked(f.journal.restoreLesson).mockResolvedValueOnce({ ...other, occurrenceRevision: null })
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([other])
    const state = f.create()
    await state.submit({ kind: 'RESTORE', target: { ...lesson, status: 'CANCELLED' } })
    expect(state.intent).not.toBeNull()
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([other])
    await state.recover()
    expect(state.intent).not.toBeNull()
    expect(f.journal.restoreLesson).toHaveBeenCalledTimes(1)
  })
  it('cancellation keeps the same physical ID guard in ACK and reconciliation', async () => {
    const f = fixture(),
      other = {
        ...lesson,
        id: 42,
        status: 'CANCELLED' as const,
        occurrenceRevision: '9007199254740994',
      }
    vi.mocked(f.journal.cancelLesson).mockResolvedValueOnce({ ...other, occurrenceRevision: null })
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([other])
    const state = f.create()
    await state.submit({ kind: 'CANCEL', target: lesson, reason: 'Нет преподавателя' })
    vi.mocked(f.journal.listLessons).mockResolvedValueOnce([other])
    await state.recover()
    expect(state.intent).not.toBeNull()
    expect(f.journal.cancelLesson).toHaveBeenCalledTimes(1)
  })
  it('transfer preview preserves source room on empty input and uses explicit trimmed room', () => {
    expect(transferEffectiveRoom('', lesson.room)).toBe('А-401')
    expect(transferEffectiveRoom('  ', lesson.room)).toBe('А-401')
    expect(transferEffectiveRoom(' Б-204 ', lesson.room)).toBe('Б-204')
    expect(transferEffectiveRoom('', null)).toBeNull()
  })
  it('distinguishes today-before-start room edits from future-date-only transfer', () => {
    const now = Date.parse('2026-10-12T10:00:00+03:00')
    expect(lessonCanRoom(lesson, now)).toBe(true)
    expect(lessonCanTransfer(lesson, '2026-10-12')).toBe(false)
    expect(lessonCanRoom(lesson, Date.parse('2026-10-12T11:20:00+03:00'))).toBe(false)
    expect(lessonCanTransfer({ ...lesson, current: false }, '2026-10-11')).toBe(false)
  })
})
