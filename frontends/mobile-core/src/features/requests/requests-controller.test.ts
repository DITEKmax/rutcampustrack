import { describe, expect, it } from 'vitest'
import { StudentApiError } from '../../api/student-client'
import type {
  StudentRequestDetail,
  StudentRequestOptions,
  StudentRequestPage,
  StudentRequestSummary,
} from '../../api/types'
import type { StudentFeatureScope } from '../../shared/session-owner'
import { RequestsController, shouldLoadRequestOptions, validateExcusePayload } from './requests-controller'
import type { RequestsPort } from './requests-port'
import type { ExcuseRequestPayload, RequestKind, RequestOptions } from './types'

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void; reject: (reason?: unknown) => void } {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const scope: StudentFeatureScope = {
  userId: 'student-1',
  activeRole: 'STUDENT',
  groupId: 'group-1',
  semesterId: 'semester-1',
  sessionId: 'session-1',
  sessionVersion: '1',
  rolesVersion: '1',
  readOnly: false,
  resetGeneration: 1,
}

function summary(id: string, kind: RequestKind = 'EXCUSE'): StudentRequestSummary {
  return {
    id,
    kind,
    status: 'PENDING',
    origin: 'MANUAL',
    createdAt: '2026-09-13T08:00:00Z',
    updatedAt: '2026-09-13T08:00:00Z',
    lessons: [{ id: 'lesson-' + id, lessonNumber: 1, status: 'OPEN', blocked: false }],
  }
}

function detail(value: StudentRequestSummary): StudentRequestDetail {
  return {
    summary: value,
    reason: 'OTHER',
    comment: 'Причина',
    decision: null,
    attachments: [],
  }
}

function page(content: StudentRequestSummary[]): StudentRequestPage {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function options(overrides: Partial<RequestOptions> = {}): RequestOptions {
  return {
    reasons: [{ code: 'OTHER', label: 'Другое', commentRequired: true }],
    files: { maxFiles: 1, maxBytesPerFile: 10, maxBytesTotal: 10, contentTypes: ['text/plain'], extensions: [] },
    budget: { limit: 2, used: 0, remaining: 2, semesterId: 'semester-1' },
    lessons: [{
      lesson: { id: 'lesson-1', lessonNumber: 1, status: 'OPEN', blocked: false },
      excuseEligible: true,
      lateCheckinEligible: true,
      pendingRequests: [],
    }],
    ...overrides,
  }
}

function generatedOptions(): StudentRequestOptions {
  return {
    reasons: [{ code: 'OTHER', label: 'Другое', commentRequired: true }],
    files: { maxFiles: 1, maxBytesPerFile: 10, maxBytesTotal: 10, contentTypes: ['text/plain'], extensions: [] },
    budget: { limit: 2, remaining: 2, semesterId: 'semester-1', used: 0 },
    lessons: [{
      lesson: { id: 'lesson-1', lessonNumber: 1, status: 'OPEN', blocked: false },
      excuseEligible: true,
      lateCheckinEligible: true,
      pendingRequests: [],
    }],
  }
}

function port(overrides: Partial<RequestsPort> = {}): RequestsPort {
  return {
    listRequests: async () => page([]),
    getRequest: async (id) => detail(summary(id)),
    getRequestOptions: async () => generatedOptions(),
    submitExcuse: async (command) => detail({ ...summary('created'), lessons: [{ id: command.lessonIds[0]!, lessonNumber: 1, status: 'OPEN', blocked: false }] }),
    submitLateCheckin: async () => detail(summary('created-late')),
    cancelRequest: async () => detail(summary('cancelled')),
    downloadRequestAttachment: async () => new Blob(['file']),
    ...overrides,
  }
}

function controller(overrides: Partial<{
  port: RequestsPort | null
  scope: StudentFeatureScope | null
  offline: boolean
  readOnly: boolean
}> = {}, keyFactory = () => 'request-key-0001') {
  const state = {
    port: overrides.port === undefined ? port() : overrides.port,
    scope: overrides.scope === undefined ? scope : overrides.scope,
    offline: overrides.offline ?? false,
    readOnly: overrides.readOnly ?? false,
  }
  const value = new RequestsController({
    port: () => state.port,
    scope: () => state.scope,
    offline: () => state.offline,
    readOnly: () => state.readOnly,
    keyFactory,
  })
  return { value, state }
}

type ConcurrentRequestResource = 'open' | 'archive' | 'options'

async function runConcurrentLoads(order: readonly ConcurrentRequestResource[]): Promise<RequestsController> {
  const open = deferred<StudentRequestPage>()
  const archive = deferred<StudentRequestPage>()
  const requestOptions = deferred<StudentRequestOptions>()
  const { value } = controller({
    port: port({
      listRequests: async (bucket) => bucket === 'OPEN' ? open.promise : archive.promise,
      getRequestOptions: async () => requestOptions.promise,
    }),
  })
  const loads = [value.loadBucket('open'), value.loadBucket('archive'), value.loadOptions()]
  for (const resource of order) {
    if (resource === 'open') open.resolve(page([summary('open-concurrent')]))
    if (resource === 'archive') archive.resolve(page([summary('archive-concurrent')]))
    if (resource === 'options') requestOptions.resolve(generatedOptions())
    await Promise.resolve()
  }
  await Promise.all(loads)
  return value
}

describe('RequestsController', () => {
  it('settles OPEN, ARCHIVE and options independently when OPEN resolves first', async () => {
    const value = await runConcurrentLoads(['open', 'archive', 'options'])

    expect(value.view.open.requests.map((item) => item.summary.id)).toEqual(['open-concurrent'])
    expect(value.view.archive.requests.map((item) => item.summary.id)).toEqual(['archive-concurrent'])
    expect(value.view.open.loading).toBe(false)
    expect(value.view.archive.loading).toBe(false)
    expect(value.view.optionsLoading).toBe(false)
    expect(value.view.options).not.toBeNull()
  })

  it('settles OPEN, ARCHIVE and options independently when options resolves first', async () => {
    const value = await runConcurrentLoads(['options', 'archive', 'open'])

    expect(value.view.open.requests.map((item) => item.summary.id)).toEqual(['open-concurrent'])
    expect(value.view.archive.requests.map((item) => item.summary.id)).toEqual(['archive-concurrent'])
    expect(value.view.open.loading).toBe(false)
    expect(value.view.archive.loading).toBe(false)
    expect(value.view.optionsLoading).toBe(false)
    expect(value.view.options).not.toBeNull()
  })

  it('clears options loading when the owner changes during an options request', async () => {
    const pendingOptions = deferred<StudentRequestOptions>()
    const first = controller({
      port: port({ getRequestOptions: async () => pendingOptions.promise }),
    })
    const loading = first.value.loadOptions()

    expect(first.value.view.optionsLoading).toBe(true)
    first.state.scope = { ...scope, resetGeneration: 2 }
    first.value.onContextChanged()

    expect(first.value.view.optionsLoading).toBe(false)
    pendingOptions.resolve(generatedOptions())
    await loading
    expect(first.value.view.optionsLoading).toBe(false)
    expect(first.value.view.options).toBeNull()
  })

  it('guards route and new-request option loading with one in-flight request', async () => {
    const pendingOptions = deferred<StudentRequestOptions>()
    let calls = 0
    const { value } = controller({
      port: port({
        getRequestOptions: async () => {
          calls += 1
          return pendingOptions.promise
        },
      }),
    })
    const loadIfNeeded = (): Promise<void> | undefined => {
      if (!shouldLoadRequestOptions(value.view.options, value.view.optionsLoading, false)) return undefined
      return value.loadOptions()
    }

    const firstLoad = loadIfNeeded()
    const duplicateLoad = loadIfNeeded()

    expect(firstLoad).toBeDefined()
    expect(duplicateLoad).toBeUndefined()
    expect(calls).toBe(1)
    expect(value.view.optionsLoading).toBe(true)

    pendingOptions.resolve(generatedOptions())
    await firstLoad

    expect(calls).toBe(1)
    expect(value.view.options).not.toBeNull()
    expect(value.view.optionsLoading).toBe(false)
    expect(loadIfNeeded()).toBeUndefined()
    expect(calls).toBe(1)
  })

  it('suppresses stale option 401/403 rejections after a newer result or context change', async () => {
    const firstOptions = deferred<StudentRequestOptions>()
    const secondOptions = deferred<StudentRequestOptions>()
    let calls = 0
    const first = controller({
      port: port({
        getRequestOptions: async () => {
          calls += 1
          return calls === 1 ? firstOptions.promise : secondOptions.promise
        },
      }),
    })
    const firstLoad = first.value.loadOptions()
    await Promise.resolve()
    const secondLoad = first.value.loadOptions()

    secondOptions.resolve(generatedOptions())
    await secondLoad
    expect(first.value.view.options).not.toBeNull()
    expect(first.value.view.optionsLoading).toBe(false)

    firstOptions.reject({ status: 401 })
    await expect(firstLoad).resolves.toBeUndefined()
    expect(first.value.view.optionsError).toBeNull()

    const staleOptions = deferred<StudentRequestOptions>()
    const context = controller({ port: port({ getRequestOptions: async () => staleOptions.promise }) })
    const contextLoad = context.value.loadOptions()
    context.state.scope = { ...scope, resetGeneration: 2 }
    context.value.onContextChanged()
    staleOptions.reject({ status: 403 })

    await expect(contextLoad).resolves.toBeUndefined()
    expect(context.value.view.optionsError).toBeNull()
    expect(context.value.view.optionsLoading).toBe(false)
  })

  it('requests OPEN page size 10 and hydrates at most ten details with concurrency three', async () => {
    const summaries = Array.from({ length: 12 }, (_, index) => summary('request-' + index))
    let active = 0
    let maximumActive = 0
    const requests: string[] = []
    const { value } = controller({
      port: port({
        listRequests: async (bucket, pageNumber) => {
          expect(bucket).toBe('OPEN')
          expect(pageNumber).toBe(0)
          return page(summaries)
        },
        getRequest: async (id) => {
          requests.push(id)
          active += 1
          maximumActive = Math.max(maximumActive, active)
          await Promise.resolve()
          active -= 1
          return detail(summary(id))
        },
      }),
    })

    await value.loadBucket('open')

    expect(requests).toHaveLength(10)
    expect(maximumActive).toBeLessThanOrEqual(3)
    expect(value.view.open.requests).toHaveLength(10)
  })

  it('keeps failed details visibly unavailable and suppresses a late owner response', async () => {
    let resolveList!: (value: StudentRequestPage) => void
    const pendingList = new Promise<StudentRequestPage>((resolve) => { resolveList = resolve })
    const first = controller({ port: port({ listRequests: async () => pendingList }) })
    const loading = first.value.loadBucket('open')
    first.state.scope = { ...scope, resetGeneration: 2 }
    first.value.onContextChanged()
    resolveList(page([summary('late')]))
    await loading
    expect(first.value.view.open.requests).toEqual([])

    const failed = controller({
      port: port({
        listRequests: async () => page([summary('failed')]),
        getRequest: async () => { throw new Error('деталь не отвечает') },
      }),
    })
    await failed.value.loadBucket('open')
    expect(failed.value.view.open.requests[0]?.detailState).toBe('unavailable')
    expect(failed.value.view.open.requests[0]?.decision).toBeUndefined()
    expect(failed.value.view.open.requests[0]?.attachments).toBeUndefined()
  })

  it('loads an exact notification request directly and rejects wrong or stale detail', async () => {
    const fetchedIds: string[] = []
    let listCalls = 0
    const direct = controller({
      port: port({
        listRequests: async () => { listCalls += 1; return page([]) },
        getRequest: async (id) => { fetchedIds.push(id); return detail(summary(id)) },
      }),
    })

    const loaded = await direct.value.loadTargetDetail('ticket-42', 'EXCUSE')

    expect(fetchedIds).toEqual(['ticket-42'])
    expect(listCalls).toBe(0)
    expect(loaded.summary.id).toBe('ticket-42')
    expect(loaded.summary.canCancel).toBe(true)

    const wrongIdentity = controller({ port: port({ getRequest: async () => detail(summary('other-ticket')) }) })
    await expect(wrongIdentity.value.loadTargetDetail('ticket-42', 'EXCUSE')).rejects.toMatchObject({ code: 'UNAVAILABLE' })

    const wrongKind = controller({
      port: port({ getRequest: async (id) => detail({ ...summary(id), kind: 'EXCUSE' }) }),
    })
    await expect(wrongKind.value.loadTargetDetail('late-42', 'LATE_CHECKIN')).rejects.toMatchObject({ code: 'UNAVAILABLE' })

    const pendingDetail = deferred<StudentRequestDetail>()
    const stale = controller({ port: port({ getRequest: async () => pendingDetail.promise }) })
    const pending = stale.value.loadTargetDetail('ticket-43', 'EXCUSE')
    stale.state.scope = { ...scope, resetGeneration: 2 }
    stale.value.onContextChanged()
    pendingDetail.resolve(detail(summary('ticket-43')))
    await expect(pending).rejects.toMatchObject({ code: 'STALE' })
  })

  it.each(['EXCUSE', 'LATE_CHECKIN'] as const)('cancels a %s notification target without bucket data', async (kind) => {
    let listCalls = 0
    const fetchedIds: string[] = []
    const cancelledIds: string[] = []
    const refreshed = detail({ ...summary('target-51', kind), status: 'CANCELLED' })
    const direct = controller({
      port: port({
        listRequests: async () => { listCalls += 1; return page([]) },
        getRequest: async (id) => { fetchedIds.push(id); return detail(summary(id, kind)) },
        cancelRequest: async (id) => { cancelledIds.push(id); return refreshed },
      }),
    })

    const updated = await direct.value.cancelTargetRequest('target-51', kind)

    expect(listCalls).toBe(0)
    expect(fetchedIds).toEqual(['target-51'])
    expect(cancelledIds).toEqual(['target-51'])
    expect(direct.value.view.open.requests).toEqual([])
    expect(direct.value.view.archive.requests).toEqual([])
    expect(updated.summary.status).toBe('CANCELLED')
    expect(updated.summary.canCancel).toBe(false)
  })

  it('refreshes loaded request buckets after notification target cancellation', async () => {
    const pending = summary('target-54')
    const cancelled = { ...pending, status: 'CANCELLED' as const }
    let hasCancelled = false
    let openListCalls = 0
    let archiveListCalls = 0
    const direct = controller({
      port: port({
        listRequests: async (bucket) => {
          if (bucket === 'OPEN') {
            openListCalls += 1
            return page(hasCancelled ? [] : [pending])
          }
          archiveListCalls += 1
          return page(hasCancelled ? [cancelled] : [])
        },
        getRequest: async () => detail(hasCancelled ? cancelled : pending),
        cancelRequest: async () => {
          hasCancelled = true
          return detail(cancelled)
        },
      }),
    })

    await direct.value.loadBucket('open')
    await direct.value.loadBucket('archive')
    expect(direct.value.view.open.requests[0]?.summary.status).toBe('PENDING')

    await direct.value.cancelTargetRequest('target-54', 'EXCUSE')

    expect(openListCalls).toBe(2)
    expect(archiveListCalls).toBe(2)
    expect(direct.value.view.open.requests).toEqual([])
    expect(direct.value.view.archive.requests.map((request) => [request.summary.id, request.summary.status]))
      .toEqual([['target-54', 'CANCELLED']])
  })

  it('keeps target cancellation successful when a loaded bucket refresh fails', async () => {
    let listCalls = 0
    const cancelled = detail({ ...summary('target-55'), status: 'CANCELLED' })
    const direct = controller({
      port: port({
        listRequests: async () => {
          listCalls += 1
          if (listCalls > 1) throw new Error('Список недоступен')
          return page([summary('target-55')])
        },
        getRequest: async () => detail(summary('target-55')),
        cancelRequest: async () => cancelled,
      }),
    })

    await direct.value.loadBucket('open')
    const updated = await direct.value.cancelTargetRequest('target-55', 'EXCUSE')

    expect(updated.summary.status).toBe('CANCELLED')
    expect(direct.value.view.mutationError).toBeNull()
    expect(direct.value.view.open.error).toBe('Список недоступен')
  })

  it('does not cancel a notification target after its student context changes', async () => {
    const pendingDetail = deferred<StudentRequestDetail>()
    let cancelCalls = 0
    const stale = controller({
      port: port({
        getRequest: async () => pendingDetail.promise,
        cancelRequest: async () => { cancelCalls += 1; return detail({ ...summary('target-52'), status: 'CANCELLED' }) },
      }),
    })
    const pending = stale.value.cancelTargetRequest('target-52', 'EXCUSE')
    stale.state.scope = { ...scope, resetGeneration: 2 }
    stale.value.onContextChanged()
    pendingDetail.resolve(detail(summary('target-52')))

    await expect(pending).rejects.toMatchObject({ code: 'STALE' })
    expect(cancelCalls).toBe(0)
  })

  it('preserves a 403 cancellation failure instead of returning target success', async () => {
    const forbidden = new StudentApiError(new Response(null, { status: 403 }), null)
    let cancelCalls = 0
    const denied = controller({
      port: port({
        cancelRequest: async () => { cancelCalls += 1; throw forbidden },
      }),
    })

    await expect(denied.value.cancelTargetRequest('target-53', 'EXCUSE')).rejects.toBe(forbidden)
    expect(cancelCalls).toBe(1)
  })

  it('stops mutations before transport while offline or read-only', async () => {
    let sends = 0
    const transport = port({ submitLateCheckin: async () => { sends += 1; return detail(summary('sent')) } })
    const offline = controller({ port: transport, offline: true })
    offline.value.view.options = options()
    await expect(offline.value.submitLateCheckin({ lessonId: 'lesson-1' })).rejects.toMatchObject({ code: 'OFFLINE' })

    const readOnly = controller({ port: transport, readOnly: true })
    readOnly.value.view.options = options()
    await expect(readOnly.value.submitLateCheckin({ lessonId: 'lesson-1' })).rejects.toMatchObject({ code: 'READ_ONLY' })
    expect(sends).toBe(0)
  })

  it('uses server option metadata for OTHER comments and file constraints', () => {
    const payload: ExcuseRequestPayload = { lessonIds: ['lesson-1'], reason: 'OTHER', comment: '', files: [] }
    expect(validateExcusePayload(payload, options())).toContain('Добавь комментарий для выбранной причины.')
    expect(validateExcusePayload({ ...payload, comment: 'Есть пояснение', files: [{ id: 'x', name: 'x.txt', size: 11, type: 'text/plain' }] }, options())).toContain('x.txt: размер больше допустимого.')
  })

  it('requires the MIME and extension pair to agree before submission', () => {
    const payload: ExcuseRequestPayload = { lessonIds: ['lesson-1'], reason: 'OTHER', comment: 'Есть пояснение', files: [] }
    const file = (name: string, type: string) => ({ id: name, name, size: 4, type, file: {} as File })
    const limits = {
      maxFiles: 2,
      maxBytesPerFile: 10,
      maxBytesTotal: 20,
      contentTypes: ['image/jpeg', 'image/png', 'application/pdf'],
      extensions: ['.jpg', '.jpeg', '.png', '.pdf'],
    }
    const requestOptions = options({ files: limits })

    expect(validateExcusePayload({ ...payload, files: [file('photo.jpeg', 'image/jpeg')] }, requestOptions)).toEqual([])
    expect(validateExcusePayload({ ...payload, files: [file('proof.pdf', 'application/pdf')] }, requestOptions)).toEqual([])
    expect(validateExcusePayload({ ...payload, files: [file('photo.jpg', 'image/jpeg')] }, requestOptions)).toEqual([])
    const mismatch = validateExcusePayload({ ...payload, files: [file('document.jpg', 'application/pdf')] }, requestOptions)
    expect(mismatch.some((error) => error.includes('document.jpg: формат не поддерживается. MIME и расширение должны соответствовать:'))).toBe(true)
    const secondMismatch = validateExcusePayload({ ...payload, files: [file('document.pdf', 'image/png')] }, requestOptions)
    expect(secondMismatch.some((error) => error.includes('document.pdf: формат не поддерживается. MIME и расширение должны соответствовать:'))).toBe(true)
  })

  it('reuses one key for an ambiguous retry and blocks edits until explicit abandon', async () => {
    const sent: Array<{ key: string; comment: string }> = []
    const keys = ['request-key-0001', 'request-key-0002']
    let attempts = 0
    const { value } = controller({
      port: port({
        submitExcuse: async (command, _files, key) => {
          sent.push({ key, comment: command.comment ?? '' })
          attempts += 1
          if (attempts < 3) {
            throw new TypeError('network response lost')
          }
          return detail(summary('created'))
        },
      }),
    }, () => keys.shift() ?? 'request-key-0002')
    value.view.options = options()
    const payload: ExcuseRequestPayload = { lessonIds: ['lesson-1'], reason: 'OTHER', comment: 'Есть пояснение', files: [] }

    await expect(value.submitExcuse(payload)).rejects.toThrow('network response lost')
    await expect(value.submitExcuse(payload)).rejects.toThrow('network response lost')
    expect(sent).toEqual([
      { key: 'request-key-0001', comment: 'Есть пояснение' },
      { key: 'request-key-0001', comment: 'Есть пояснение' },
    ])
    await expect(value.submitExcuse({ ...payload, comment: 'Изменённый текст' })).rejects.toMatchObject({ code: 'AMBIGUOUS' })
    value.abandonCommand('EXCUSE')
    await expect(value.submitExcuse({ ...payload, comment: 'Изменённый текст' })).resolves.toBeTruthy()
    expect(sent[2]?.key).toBe('request-key-0002')
  })
})
