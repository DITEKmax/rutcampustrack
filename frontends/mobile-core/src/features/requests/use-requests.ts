import { computed, onBeforeUnmount, reactive, toValue, watch, type MaybeRefOrGetter } from 'vue'
import type { StudentApi } from '../../api/student-client'
import type { StudentFeatureScope } from '../../shared/session-owner'
import {
  createRequestsControllerView,
  RequestsController,
  type RequestsControllerView,
} from './requests-controller'
import type { RequestsPort } from './requests-port'
import type {
  ExcuseRequestPayload,
  LateCheckinRequestPayload,
  RequestBucket,
  RequestKind,
} from './types'

export interface UseRequestsOptions {
  offline: MaybeRefOrGetter<boolean>
  readOnly?: MaybeRefOrGetter<boolean | undefined>
}

/**
 * Feature-local owner for Requests. It deliberately uses one in-memory
 * controller instead of Vue Query so drafts, File refs and ambiguous command
 * state never enter a shared or persistent cache.
 */
export function useRequests(
  api: MaybeRefOrGetter<StudentApi | null>,
  scope: MaybeRefOrGetter<StudentFeatureScope | null>,
  options: UseRequestsOptions,
) {
  const view = reactive(createRequestsControllerView()) as RequestsControllerView
  const controller = new RequestsController({
    view,
    port: () => toValue(api) as RequestsPort | null,
    scope: () => toValue(scope),
    offline: () => toValue(options.offline),
    readOnly: () => Boolean(options.readOnly && toValue(options.readOnly)),
  })

  const activeBucket = computed(() => view[view.bucket])
  const currentRequests = computed(() => activeBucket.value.requests)
  const hasNextPage = computed(() => activeBucket.value.page >= 0 && activeBucket.value.page + 1 < activeBucket.value.totalPages)

  const stopContext = watch(
    [
      () => toValue(scope),
      () => Boolean(toValue(options.offline)),
      () => Boolean(options.readOnly && toValue(options.readOnly)),
    ],
    () => controller.onContextChanged(),
    { immediate: true, flush: 'sync' },
  )

  onBeforeUnmount(() => {
    stopContext()
    controller.dispose()
  })

  return {
    view,
    controller,
    activeBucket,
    currentRequests,
    hasNextPage,
    bucketView: (bucket?: RequestBucket) => controller.bucketView(bucket),
    selectBucket: (bucket: RequestBucket) => controller.selectBucket(bucket),
    load: (bucket?: RequestBucket) => controller.loadBucket(bucket),
    loadMore: (bucket?: RequestBucket) => controller.loadMore(bucket),
    loadOptions: () => controller.loadOptions(),
    submitExcuse: (payload: ExcuseRequestPayload) => controller.submitExcuse(payload),
    submitLateCheckin: (payload: LateCheckinRequestPayload) => controller.submitLateCheckin(payload),
    cancelRequest: (id: string) => controller.cancelRequest(id),
    downloadAttachment: (requestId: string, attachmentId: string) => controller.downloadAttachment(requestId, attachmentId),
    commandState: (kind: RequestKind) => controller.commandState(kind),
    abandonCommand: (kind: RequestKind) => controller.abandonCommand(kind),
    reconcileCommand: (kind: RequestKind) => controller.reconcileCommand(kind),
  }
}
