import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { StudentToday, TodayLesson } from '../../../../mobile-core/src/api/types'
import { StudentApiError } from '../../../../mobile-core/src/api/student-client'
import {
  CheckinBeforeSendCancelled,
  CheckinCommandRecovery,
  eligibilityLabel,
  isPendingCheckin,
  remainingSeconds,
} from '../../../../mobile-core/src/domain/checkin'
import { provideDeviceAccess } from '../../../../mobile-core/src/device-access/device-access'
import { browserGeoSource } from '../../../../mobile-core/src/device-access/geolocation'
import { studentError, type StudentScope } from './student-state'

// Keep the original command/key across page remounts within one authority only.
let recoveryOwner: string | null = null
let recovery = new CheckinCommandRecovery()
export function useStudentCheckin(
  scope: StudentScope,
  refresh: () => Promise<void>,
  today: () => StudentToday | null,
) {
  if (recoveryOwner !== scope.identity) {
    recoveryOwner = scope.identity
    recovery = new CheckinCommandRecovery()
  }
  const deviceAccess = provideDeviceAccess({ geo: browserGeoSource() })
  watch(
    () => [scope.session.value?.user.id, scope.contextKey.value, scope.readOnly.value] as const,
    ([account, authority, readOnly]) =>
      deviceAccess.bindOwner(account ?? null, readOnly ? null : authority),
    { immediate: true, flush: 'sync' },
  )
  let acquisitionController: AbortController | null = null
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
      return acquiring.value
        ? deviceAccess.state.stage === 'refining'
          ? 'Уточняем геолокацию…'
          : 'Определяем геолокацию…'
        : 'Отправляем отметку…'
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
    const acquisition = new AbortController()
    acquisitionController = acquisition
    let sent = false
    try {
      const ack = await commands.execute(
        id,
        async () => {
          acquiring.value = true
          const command = await deviceAccess.acquire({ signal: acquisition.signal })
          scope.transport.assertCurrent()
          if (!scope.current() || acquisition.signal.aborted) throw new CheckinBeforeSendCancelled()
          await refresh()
          scope.transport.assertCurrent()
          const fresh = today()?.lessons.find((item) => item.schedule.id === id)
          if (
            !scope.current() ||
            acquisition.signal.aborted ||
            scope.readOnly.value ||
            !fresh?.checkinEligibility.allowed
          )
            throw new CheckinBeforeSendCancelled()
          acquiring.value = false
          return command
        },
        (attempt) => {
          scope.transport.assertCurrent()
          if (!scope.current() || scope.readOnly.value || acquisition.signal.aborted) throw new CheckinBeforeSendCancelled()
          sent = true
          return scope.api.checkin(attempt.lessonId, attempt.command, attempt.key)
        },
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
      if (!scope.current() || cause instanceof CheckinBeforeSendCancelled) return
      if (sent && (!(cause instanceof StudentApiError) || cause.response.status >= 500))
        uncertain.value = id
      error.value = studentError(cause)
      if (!uncertain.value) await refresh()
    } finally {
      if (acquisitionController === acquisition) acquisitionController = null
      if (scope.current()) {
        busyLesson.value = null
        acquiring.value = false
        scope.dirty.value = false
      }
    }
  }
  function cancelAcquisition() {
    acquisitionController?.abort()
    deviceAccess.cancel()
  }
  return {
    deviceAccess,
    acquiring,
    cancelAcquisition,
    busy,
    busyLesson,
    uncertain,
    error,
    result,
    clock,
    allowed,
    label,
    submit,
  }
}
