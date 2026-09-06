import type { StudentCheckinAck, StudentCheckinCommand, StudentToday, TodayLesson, UnavailableReason } from '../api/types'
import { StudentApiError } from '../api/student-client'

export interface CheckinAttempt {
  lessonId: string
  command: StudentCheckinCommand
  key: string
}

interface PendingCheckin {
  attempt: Promise<CheckinAttempt>
  inFlight: Promise<StudentCheckinAck> | null
}

/** Keeps a retryable command only for this running shell instance. */
export class CheckinCommandRecovery {
  private readonly pending = new Map<string, PendingCheckin>()

  constructor(private readonly createKey: () => string = newIdempotencyKey) {}

  execute(
    lessonId: string,
    acquireCommand: () => Promise<StudentCheckinCommand>,
    submit: (attempt: CheckinAttempt) => Promise<StudentCheckinAck>,
  ): Promise<StudentCheckinAck> {
    let pending = this.pending.get(lessonId)
    if (!pending) {
      const attempt = Promise.resolve().then(async () => ({
        lessonId,
        command: await acquireCommand(),
        key: this.createKey(),
      }))
      pending = { attempt, inFlight: null }
      this.pending.set(lessonId, pending)
      void attempt.catch(() => {
        if (this.pending.get(lessonId) === pending) this.pending.delete(lessonId)
      })
    }
    if (pending.inFlight) return pending.inFlight

    const completion = pending.attempt
      .then(submit)
      .then(
        (ack) => {
          if (this.pending.get(lessonId) === pending) this.pending.delete(lessonId)
          return ack
        },
        (error: unknown) => {
          if (!isAmbiguousCheckinFailure(error) && this.pending.get(lessonId) === pending) this.pending.delete(lessonId)
          throw error
        },
      )
      .finally(() => { pending.inFlight = null })
    pending.inFlight = completion
    return completion
  }
}

function isAmbiguousCheckinFailure(error: unknown): boolean {
  return !(error instanceof StudentApiError) || error.response.status >= 500
}

export function unavailableReason(error: GeolocationPositionError | null): UnavailableReason {
  if (!error) return 'POSITION_UNAVAILABLE'
  if (error.code === error.PERMISSION_DENIED) return 'PERMISSION_DENIED'
  if (error.code === error.TIMEOUT) return 'TIMEOUT'
  return 'POSITION_UNAVAILABLE'
}

export function commandFromCoordinates(position: GeolocationPosition): StudentCheckinCommand {
  return {
    geo: {
      kind: 'COORDINATES',
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
    },
  }
}

export function unavailableCommand(reason: UnavailableReason): StudentCheckinCommand {
  return { geo: { kind: 'UNAVAILABLE', reason } }
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export function remainingSeconds(serverNow: string, retryAt: string | null, now = Date.now()): number {
  if (!retryAt) return 0
  const elapsed = Math.max(0, now - Date.parse(serverNow))
  return Math.max(0, Math.ceil((Date.parse(retryAt) - Date.parse(serverNow) - elapsed) / 1000))
}

export function countdownLabel(seconds: number): string {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0')
  return `${minutes}:${(seconds % 60).toString().padStart(2, '0')}`
}

export function applyCheckinAck(today: StudentToday, ack: StudentCheckinAck): StudentToday {
  return {
    ...today,
    // A replayed idempotency receipt retains its original serverNow. The query's
    // last fresh Today projection remains the clock baseline until refetch wins.
    serverNow: today.serverNow,
    lessons: today.lessons.map((lesson) => lesson.schedule.id === ack.lessonId ? lessonFromAck(lesson, ack) : lesson),
  }
}

function lessonFromAck(lesson: TodayLesson, ack: StudentCheckinAck): TodayLesson {
  return {
    ...lesson,
    attendance: ack.attendance,
    request: ack.request,
    checkinEligibility: {
      allowed: false,
      reason: ack.outcome === 'PRESENT' ? 'ALREADY_PRESENT' : 'PENDING_CONFIRMATION',
      retryAt: ack.retryAt,
    },
  }
}

export function eligibilityLabel(lesson: TodayLesson, countdown: number): string {
  if (lesson.attendance?.status === 'PRESENT') return 'Отметка подтверждена'
  if (lesson.request?.status === 'PENDING') return countdown > 0 ? `На подтверждении · ${countdownLabel(countdown)}` : 'Можно повторить отметку'
  const labels: Record<TodayLesson['checkinEligibility']['reason'], string> = {
    ELIGIBLE: 'Можно отметиться',
    ALREADY_PRESENT: 'Отметка подтверждена',
    LESSON_CANCELLED: 'Пара отменена',
    TOO_EARLY: 'Отметка ещё недоступна',
    WINDOW_CLOSED: 'Время отметки закончилось',
    GEO_BLOCKED: 'Отметка по геолокации недоступна',
    PENDING_CONFIRMATION: countdown > 0 ? `На подтверждении · ${countdownLabel(countdown)}` : 'Можно повторить отметку',
    COOLDOWN: countdown > 0 ? `Повтори через ${countdownLabel(countdown)}` : 'Можно повторить отметку',
    HEADMAN_ABSENT_REQUIRES_APPEAL: 'Нужна заявка на «н»',
    HEADMAN_USES_JOURNAL: 'Отметку ставит староста в журнале',
    DEPENDENCY_UNAVAILABLE: 'Статус будет доступен при подключении',
  }
  return labels[lesson.checkinEligibility.reason]
}
