import pendingAck from '../../fixtures/checkin-pending-ack.json'
import presentAck from '../../fixtures/checkin-present-ack.json'
import schedule from '../../fixtures/semester-schedule.json'
import session from '../../fixtures/session.json'
import pendingToday from '../../fixtures/today-pending.json'
import presentToday from '../../fixtures/today-present.json'
import type { StudentCheckinAck, StudentCheckinCommand, StudentSemesterSchedule, StudentSession, StudentToday, TodayLesson } from '../api/types'

const fixtureSession = session as StudentSession
const fixturePendingToday = pendingToday as StudentToday
const fixturePresentToday = presentToday as StudentToday
const fixturePendingAck = pendingAck as StudentCheckinAck
const fixturePresentAck = presentAck as StudentCheckinAck
const fixtureSchedule = fixtureScheduleProjection()

type FixtureTodayState = 'default' | 'pending' | 'confirmed'
type FixtureApiState = 'normal' | 'network' | 'unauthorized' | 'forbidden'

/**
 * Test-only adapter. Production shells never select it without
 * VITE_MOBILE_FIXTURE_MODE=true. Use `?fixtureToday=default|pending|confirmed`
 * to capture the three approved Today screenshots without editing canonical
 * contract fixtures. `fixtureApi=network|unauthorized|forbidden` exercises the PWA's
 * normal cached-read recovery after a previous normal load. A check-in POST also
 * moves this adapter instance to its ACK state.
 */
export function createFixtureTransport(): typeof fetch {
  let todayState = selectedTodayState()
  return async (input, init) => {
    const url = typeof input === 'string' ? input : input.toString()
    const method = init?.method ?? 'GET'
    const apiState = selectedApiState()
    if (typeof navigator !== 'undefined' && navigator.onLine === false) throw new TypeError('Fixture network unavailable')
    if (apiState === 'network') throw new TypeError('Fixture network unavailable')
    if (apiState === 'unauthorized') return new Response(null, { status: 401 })
    if (apiState === 'forbidden') return new Response(null, { status: 403 })
    if (url.includes('/session')) return json(fixtureSession)
    if (url.includes('/schedule')) return json(fixtureSchedule, { ETag: '"fixture-semester-v1"' })
    if (url.includes('/today')) return json(todayProjection(todayState))
    if (url.includes('/checkin') && method === 'POST') {
      const command = JSON.parse(String(init?.body ?? '{}')) as StudentCheckinCommand
      todayState = command.geo.kind === 'UNAVAILABLE' ? 'pending' : 'confirmed'
      return json(todayState === 'pending' ? pendingAckProjection() : presentAckProjection())
    }
    if (url.includes('/auth/refresh') || url.includes('/auth/tma')) return json({ accessToken: 'fixture-access-token' })
    return new Response(null, { status: 404 })
  }
}

export const fixtureConfirmedToday = fixturePresentToday

function selectedTodayState(): FixtureTodayState {
  const href = globalThis.location?.href ?? 'https://fixture.invalid/'
  const value = new URL(href).searchParams.get('fixtureToday')
  return value === 'default' || value === 'pending' || value === 'confirmed' ? value : 'default'
}

function selectedApiState(): FixtureApiState {
  const href = globalThis.location?.href ?? 'https://fixture.invalid/'
  const value = new URL(href).searchParams.get('fixtureApi')
  return value === 'network' || value === 'unauthorized' || value === 'forbidden' ? value : 'normal'
}

function fixtureScheduleProjection(): StudentSemesterSchedule {
  const todaySchedules = defaultProjection().lessons.map((lesson) => lesson.schedule)
  const first = todaySchedules[0]
  if (!first) throw new Error('Semester fixture must include a lesson')
  return {
    ...(schedule as StudentSemesterSchedule),
    lessons: [
      ...todaySchedules,
      {
        ...first,
        id: 'fixture-next-day',
        date: '2026-09-07',
        lessonNumber: 1,
        startsAt: '09:00:00',
        endsAt: '10:30:00',
        status: 'PLANNED',
        subject: { ...first.subject, id: 'fixture-discrete', name: 'Дискретная математика', type: 'PRACTICE' },
        room: { current: 'Б-218', previous: null, changeState: 'UNCHANGED' },
      },
    ],
  }
}

function todayProjection(state: FixtureTodayState): StudentToday {
  if (state === 'pending') return pendingProjection()
  if (state === 'confirmed') return confirmedProjection()
  return defaultProjection()
}

function defaultProjection(): StudentToday {
  const now = new Date()
  return {
    ...fixturePendingToday,
    serverNow: now.toISOString(),
    lessons: [
      lesson({
        id: 'fixture-math',
        startsAt: '09:00:00',
        endsAt: '10:30:00',
        status: 'CLOSED',
        subject: 'Математический анализ',
        kind: 'PRACTICE',
        room: 'А-312',
        attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: now.toISOString() },
        allowed: false,
        reason: 'ALREADY_PRESENT',
      }),
      lesson({
        id: '77',
        startsAt: '10:40:00',
        endsAt: '12:10:00',
        status: 'ACTIVE',
        subject: 'Основы программирования',
        kind: 'LECTURE',
        room: 'А-401',
        allowed: true,
        reason: 'ELIGIBLE',
      }),
      lesson({
        id: 'fixture-networks',
        startsAt: '12:20:00',
        endsAt: '13:50:00',
        status: 'PLANNED',
        subject: 'Компьютерные сети',
        kind: 'LAB',
        room: 'А-508',
        previousRoom: 'А-214',
        allowed: false,
        reason: 'TOO_EARLY',
      }),
    ],
  }
}

function pendingProjection(): StudentToday {
  const serverNow = new Date()
  const retryAt = new Date(serverNow.getTime() + 300_000).toISOString()
  return {
    ...defaultProjection(),
    serverNow: serverNow.toISOString(),
    lessons: defaultProjection().lessons.map((lesson) => ({
      ...lesson,
      attendance: lesson.schedule.id === '77' ? null : lesson.attendance,
      request: lesson.schedule.id === '77' ? canonicalFixtureLesson().request : lesson.request,
      checkinEligibility: lesson.schedule.id === '77'
        ? { allowed: false, reason: 'PENDING_CONFIRMATION', retryAt }
        : lesson.checkinEligibility,
    })),
  }
}

function confirmedProjection(): StudentToday {
  const serverNow = new Date()
  return {
    ...defaultProjection(),
    serverNow: serverNow.toISOString(),
    lessons: defaultProjection().lessons.map((lesson) => ({
      ...lesson,
      attendance: lesson.schedule.id === '77'
        ? { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: serverNow.toISOString() }
        : lesson.attendance,
      request: lesson.schedule.id === '77' ? null : lesson.request,
      checkinEligibility: lesson.schedule.id === '77'
        ? { allowed: false, reason: 'ALREADY_PRESENT', retryAt: null }
        : lesson.checkinEligibility,
    })),
  }
}

function lesson(options: {
  id: string
  startsAt: string
  endsAt: string
  status: TodayLesson['schedule']['status']
  subject: string
  kind: TodayLesson['schedule']['subject']['type']
  room: string
  previousRoom?: string
  attendance?: TodayLesson['attendance']
  allowed: boolean
  reason: TodayLesson['checkinEligibility']['reason']
}): TodayLesson {
  const source = canonicalFixtureLesson()
  return {
    ...source,
    schedule: {
      ...source.schedule,
      id: options.id,
      startsAt: options.startsAt,
      endsAt: options.endsAt,
      status: options.status,
      subject: { ...source.schedule.subject, name: options.subject, type: options.kind },
      room: {
        current: options.room,
        previous: options.previousRoom ?? null,
        changeState: options.previousRoom ? 'CHANGED' : 'UNCHANGED',
      },
    },
    attendance: options.attendance ?? null,
    request: null,
    checkinEligibility: { allowed: options.allowed, reason: options.reason, retryAt: null },
  }
}

function canonicalFixtureLesson(): TodayLesson {
  const source = fixturePendingToday.lessons[0]
  if (!source) throw new Error('Today fixture must include a lesson')
  return source
}

function pendingAckProjection(): StudentCheckinAck {
  const serverNow = new Date()
  return { ...fixturePendingAck, serverNow: serverNow.toISOString(), retryAt: new Date(serverNow.getTime() + 300_000).toISOString() }
}

function presentAckProjection(): StudentCheckinAck {
  return { ...fixturePresentAck, serverNow: new Date().toISOString() }
}

function json(body: unknown, headers?: HeadersInit): Response {
  return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json', ...headers } })
}
