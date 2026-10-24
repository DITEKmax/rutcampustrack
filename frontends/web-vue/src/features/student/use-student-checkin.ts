import { computed, onBeforeUnmount, ref } from 'vue'
import type { TodayLesson } from '../../../../mobile-core/src/api/types'
import { StudentApiError } from '../../../../mobile-core/src/api/student-client'
import {
  CheckinCommandRecovery,
  commandFromCoordinates,
  eligibilityLabel,
  isPendingCheckin,
  remainingSeconds,
  unavailableCommand,
  unavailableReason,
} from '../../../../mobile-core/src/domain/checkin'
import { studentError, type StudentScope } from './student-state'

// Keep the original command/key across page remounts within one authority only.
let recoveryOwner: string | null = null
let recovery = new CheckinCommandRecovery()
export function useStudentCheckin(scope: StudentScope, refresh: () => Promise<void>) {
  if (recoveryOwner !== scope.identity) {
    recoveryOwner = scope.identity
    recovery = new CheckinCommandRecovery()
  }
  const commands = recovery
  const busyLesson = ref<string | null>(null),
    error = ref(''),
    result = ref('')
  const acquiring = ref(false),
    uncertain = ref<string | null>(null),
    clock = ref(Date.now())
  const timer = window.setInterval(() => {
    clock.value = Date.now()
  }, 1000)
  onBeforeUnmount(() => window.clearInterval(timer))
  const busy = computed(() => busyLesson.value !== null)
  function countdown(lesson: TodayLesson, serverNow: string) {
    return remainingSeconds(serverNow, lesson.checkinEligibility.retryAt, clock.value)
  }
  function allowed(lesson: TodayLesson, serverNow: string) {
    return (
      Boolean(scope.session.value?.capabilities.includes('GEO_CHECKIN')) &&
      !scope.readOnly.value &&
      !busy.value &&
      lesson.attendance?.status !== 'PRESENT' &&
      (!isPendingCheckin(lesson) || lesson.request?.origin === 'AUTO_GEO_FAILURE') &&
      (lesson.checkinEligibility.allowed ||
        (lesson.checkinEligibility.reason === 'COOLDOWN' &&
          lesson.checkinEligibility.retryAt !== null &&
          countdown(lesson, serverNow) === 0))
    )
  }
  function label(lesson: TodayLesson, serverNow: string) {
    if (busyLesson.value === lesson.schedule.id)
      return acquiring.value ? 'Проверяем геолокацию…' : 'Отправляем отметку…'
    if (scope.readOnly.value) return 'Доступен только просмотр'
    return allowed(lesson, serverNow)
      ? 'Отметиться на паре'
      : eligibilityLabel(lesson, countdown(lesson, serverNow))
  }
  async function submit(lesson: TodayLesson, serverNow: string, reconcile = false) {
    if (
      busy.value ||
      scope.readOnly.value ||
      !scope.current() ||
      (!reconcile && !allowed(lesson, serverNow))
    )
      return
    const id = lesson.schedule.id
    busyLesson.value = id
    scope.dirty.value = true
    error.value = ''
    result.value = ''
    try {
      const ack = await commands.execute(
        id,
        async () => {
          acquiring.value = true
          const position = await new Promise<GeolocationPosition | GeolocationPositionError | null>(
            (resolve) => {
              if (!navigator.geolocation) return resolve(null)
              navigator.geolocation.getCurrentPosition(resolve, resolve, {
                enableHighAccuracy: true,
                timeout: 10_000,
                maximumAge: 0,
              })
            },
          )
          scope.transport.assertCurrent()
          acquiring.value = false
          return position && 'coords' in position
            ? commandFromCoordinates(position)
            : unavailableCommand(unavailableReason(position))
        },
        (attempt) => scope.api.checkin(attempt.lessonId, attempt.command, attempt.key),
      )
      scope.transport.assertCurrent()
      if (!scope.current()) return
      uncertain.value = null
      result.value =
        ack.outcome === 'PRESENT'
          ? 'Отметка подтверждена: присутствовал.'
          : 'Создан запрос старосте на подтверждение присутствия.'
      await refresh()
    } catch (cause) {
      if (!scope.current()) return
      if (!(cause instanceof StudentApiError) || cause.response.status >= 500) uncertain.value = id
      error.value = studentError(cause)
      if (!uncertain.value) await refresh()
    } finally {
      if (scope.current()) {
        busyLesson.value = null
        acquiring.value = false
        scope.dirty.value = false
      }
    }
  }
  return { busy, busyLesson, uncertain, error, result, clock, allowed, label, submit }
}
