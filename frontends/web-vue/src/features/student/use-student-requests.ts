import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { StudentApiError } from '../../../../mobile-core/src/api/student-client'
import { studentFeatureScope } from '../../../../mobile-core/src/shared/session-owner'
import {
  createRequestsControllerView,
  RequestsController,
} from '../../../../mobile-core/src/features/requests/requests-controller'
import type { RequestsPort } from '../../../../mobile-core/src/features/requests/requests-port'
import type {
  ExcuseRequestPayload,
  RequestBucket,
  RequestDetail,
  RequestKind,
} from '../../../../mobile-core/src/features/requests/types'
import { webSession } from '../../auth/session'
import { studentError, type StudentScope } from './student-state'

export function useStudentRequests(scope: StudentScope) {
  const authority = computed(() =>
    scope.session.value
      ? studentFeatureScope(scope.session.value, webSession.generation.value)
      : null,
  )
  async function guarded<T>(read: () => Promise<T>): Promise<T> {
    const value = await read()
    scope.transport.assertCurrent()
    if (!scope.current()) throw Error('Сессия изменилась.')
    return value
  }
  const port: RequestsPort = {
    listRequests: (...args) => guarded(() => scope.api.listRequests(...args)),
    getRequest: (...args) => guarded(() => scope.api.getRequest(...args)),
    getRequestOptions: () => guarded(() => scope.api.getRequestOptions()),
    submitExcuse: (...args) => guarded(() => scope.api.submitExcuse(...args)),
    submitLateCheckin: (...args) => guarded(() => scope.api.submitLateCheckin(...args)),
    cancelRequest: (...args) => guarded(() => scope.api.cancelRequest(...args)),
    downloadRequestAttachment: (...args) =>
      guarded(() => scope.api.downloadRequestAttachment(...args)),
  }
  const view = reactive(createRequestsControllerView())
  const controller = new RequestsController({
    view,
    port: () => (scope.current() ? port : null),
    scope: () => authority.value,
    offline: () => !navigator.onLine,
    readOnly: () => scope.readOnly.value,
  })
  const form = ref<RequestKind | null>(null),
    error = ref(''),
    message = ref(''),
    cancelling = ref<RequestDetail | null>(null),
    uncertainCancellation = ref<string | null>(null)
  const bucket = computed(() => controller.bucketView())
  const busy = computed(() => view.mutation !== 'idle')
  const ambiguous = computed(() => view.command?.status === 'ambiguous')
  watch(authority, () => controller.onContextChanged(), { flush: 'sync', immediate: true })
  watch([form, busy, ambiguous], () => {
    scope.dirty.value = Boolean(form.value || busy.value || ambiguous.value)
  })
  onBeforeUnmount(() => controller.dispose())
  onBeforeRouteLeave(() => {
    if (busy.value || ambiguous.value) {
      error.value = 'Сначала уточни результат отправки заявки.'
      return false
    }
    return true
  })
  async function load() {
    error.value = ''
    if (!authority.value) return
    await Promise.allSettled([controller.loadBucket(), controller.loadOptions()])
  }
  watch(scope.refreshed, load)
  async function selectBucket(value: RequestBucket) {
    controller.selectBucket(value)
    try {
      await controller.loadBucket(value)
    } catch {
      /* The controller exposes the failure. */
    }
  }
  async function submit(kind: RequestKind, payload: ExcuseRequestPayload | { lessonId: string }) {
    error.value = ''
    message.value = ''
    try {
      const result =
        kind === 'EXCUSE'
          ? await controller.submitExcuse(payload as ExcuseRequestPayload)
          : await controller.submitLateCheckin(payload as { lessonId: string })
      scope.transport.assertCurrent()
      form.value = null
      controller.selectBucket('open')
      await controller.loadOptions().catch(() => undefined)
      message.value = 'Заявка отправлена на рассмотрение.'
      return result
    } catch (cause) {
      if (scope.current()) error.value = studentError(cause)
      return null
    }
  }
  async function checkCancellation() {
    const id = uncertainCancellation.value
    if (!id) return
    try {
      const result = await port.getRequest(id)
      await controller.loadBucket()
      scope.transport.assertCurrent()
      uncertainCancellation.value = null
      error.value = ''
      message.value =
        result.summary.status === 'CANCELLED'
          ? 'Заявка отменена.'
          : `Текущий статус заявки: ${result.summary.status === 'PENDING' ? 'на рассмотрении' : 'решение уже принято'}.`
    } catch (cause) {
      if (scope.current()) error.value = studentError(cause)
    }
  }
  async function cancel() {
    const target = cancelling.value
    if (!target || busy.value) return
    error.value = ''
    try {
      await controller.cancelTargetRequest(target.summary.id, target.summary.kind)
      scope.transport.assertCurrent()
      message.value = 'Заявка отменена.'
      await controller.loadOptions()
    } catch (cause) {
      if (!scope.current()) return
      error.value = studentError(cause)
      if (!(cause instanceof StudentApiError) || cause.response.status >= 500)
        uncertainCancellation.value = target.summary.id
      await controller.loadBucket().catch(() => undefined)
    } finally {
      if (scope.current()) cancelling.value = null
    }
  }
  return {
    authority,
    view,
    controller,
    form,
    error,
    message,
    cancelling,
    uncertainCancellation,
    bucket,
    busy,
    ambiguous,
    load,
    selectBucket,
    submit,
    cancel,
    checkCancellation,
  }
}
