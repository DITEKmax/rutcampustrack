<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  type AdminSemesterArchiveAction,
  type AdminSemesterArchiveOperation,
  type AdminSemesterArchiveStatus,
  AdminSemesterApiError,
  type AdminSemesterType,
  type AdminSemester,
  type AdminSemesterClient,
} from './admin-semester-client'
import './admin-semester-screen.pcss'

const props = withDefaults(defineProps<{
  client: AdminSemesterClient
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
}>()

const semesters = ref<readonly AdminSemester[]>([])
const loading = ref(true)
const saving = ref(false)
const pendingActivationId = ref<number | null>(null)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingSemesterId = ref<number | null>(null)
const formLoading = ref(false)
const semesterType = ref<AdminSemesterType | null>(null)
const name = ref('')
const academicYear = ref('')
const academicYearTouched = ref(false)
const dateFrom = ref('')
const dateTo = ref('')
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const archiveConfirmation = ref<{ semesterId: number; action: AdminSemesterArchiveAction } | null>(null)
const archiveConfirmationChecking = ref(false)
const archiveCommand = ref<{
  semesterId: number
  action: AdminSemesterArchiveAction
  idempotencyKey: string | null
  operationId: string | null
  phase: 'SUBMITTING' | 'UNCERTAIN' | 'PENDING' | 'SETTLING'
} | null>(null)
const archiveOperations = ref<Record<number, AdminSemesterArchiveOperation>>({})
const archiveStatusErrors = ref<Record<number, string>>({})
const archiveStatusLoadingIds = ref(new Set<number>())
let disposed = false
let listRequestRevision = 0
let listAbortController: AbortController | null = null
let editRequestRevision = 0
let editAbortController: AbortController | null = null
let archiveScopeRevision = 0
let archiveConfirmationRevision = 0
let archiveMonitorController: AbortController | null = null
const archiveStatusControllers = new Set<AbortController>()

const activeSemesters = computed(() => semesters.value.filter((semester) => semester.active && !semester.archived))
const inactiveSemesters = computed(() => semesters.value.filter((semester) => !semester.active && !semester.archived))
const archivedSemesters = computed(() => semesters.value.filter((semester) => semester.archived))
const hasActiveSemester = computed(() => activeSemesters.value.length > 0)
const mutationBusy = computed(() => saving.value
  || archiveConfirmationChecking.value
  || archiveCommand.value !== null
  || semesters.value.some((semester) => semester.transition !== 'NONE'
    || semester.releasePending
    || archiveOperations.value[semester.id]?.operationState === 'PENDING'))
const archiveConfirmationSemester = computed(() => archiveConfirmation.value === null
  ? null
  : semesters.value.find((semester) => semester.id === archiveConfirmation.value?.semesterId) ?? null)
const archiveSemestersVisible = computed(() => semesters.value.filter((semester) =>
  needsArchiveStatus(semester)
    || archiveOperationFor(semester.id)?.operationState === 'ERROR'
    || Boolean(archiveStatusErrors.value[semester.id])))
const formName = computed(() => {
  if (semesterType.value === null) return name.value
  const year = Number(academicYear.value)
  if (!Number.isSafeInteger(year) || year < 1 || year > 9998) return ''
  return generatedSemesterName(semesterType.value, year)
})

watch([semesterType, dateFrom], ([type, from]) => {
  if (academicYearTouched.value || type === null || from === '') return
  const calendarYear = Number(from.slice(0, 4))
  const defaultYear = type === 'SPRING' ? calendarYear - 1 : calendarYear
  academicYear.value = Number.isSafeInteger(defaultYear) && defaultYear >= 1 && defaultYear <= 9998
    ? String(defaultYear)
    : ''
})

watch(() => props.client, () => {
  resetArchiveScope()
  listRequestRevision += 1
  listAbortController?.abort()
  cancelEditLoad()
  formVisible.value = false
  editingSemesterId.value = null
  void refresh()
}, { flush: 'sync' })

onMounted(() => void refresh())

async function refresh(loadArchiveStatuses = true): Promise<void> {
  const revision = ++listRequestRevision
  const archiveScope = archiveScopeRevision
  listAbortController?.abort()
  const controller = new AbortController()
  listAbortController = controller
  loading.value = true
  error.value = null
  try {
    const next = await props.client.listSemesters(controller.signal)
    if (!isCurrentListRequest(revision, controller)) return
    semesters.value = mergeSemesterList(semesters.value, next)
    if (loadArchiveStatuses && archiveCommand.value === null) {
      void hydrateArchiveStatuses(next, archiveScope)
    }
  } catch (cause) {
    if (!isCurrentListRequest(revision, controller)
      || cause instanceof StaleSessionGenerationError
      || isAbortError(cause)) return
    showError(cause, 'Список семестров не удалось загрузить.')
  } finally {
    if (isCurrentListRequest(revision, controller)) {
      loading.value = false
      listAbortController = null
    }
  }
}

function isCurrentListRequest(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRequestRevision && listAbortController === controller
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

function cancelEditLoad(): void {
  editRequestRevision += 1
  editAbortController?.abort()
  editAbortController = null
  formLoading.value = false
}

function openCreateForm(): void {
  if (mutationBusy.value) return
  cancelEditLoad()
  formMode.value = 'create'
  editingSemesterId.value = null
  semesterType.value = null
  name.value = ''
  academicYear.value = ''
  academicYearTouched.value = false
  dateFrom.value = ''
  dateTo.value = ''
  formVisible.value = true
  error.value = null
  notice.value = null
}

function closeForm(): void {
  if (mutationBusy.value) return
  cancelEditLoad()
  formVisible.value = false
  editingSemesterId.value = null
  error.value = null
  notice.value = null
}

async function openEditForm(semester: AdminSemester): Promise<void> {
  if (mutationBusy.value || !canEdit(semester)) return
  cancelEditLoad()
  formMode.value = 'edit'
  editingSemesterId.value = semester.id
  formVisible.value = true
  formLoading.value = true
  semesterType.value = null
  name.value = ''
  academicYear.value = ''
  academicYearTouched.value = false
  dateFrom.value = ''
  dateTo.value = ''
  error.value = null
  notice.value = null

  const revision = ++editRequestRevision
  const controller = new AbortController()
  editAbortController = controller
  try {
    const fresh = await props.client.getSemester(semester.id, controller.signal)
    if (!isCurrentEditRequest(revision, controller, semester.id)) return
    name.value = fresh.name
    dateFrom.value = fresh.dateFrom
    dateTo.value = fresh.dateTo
    // Legacy rows stay explicitly untyped until an admin chooses a type.
    semesterType.value = fresh.semesterType
    academicYear.value = fresh.academicYear === null ? '' : String(fresh.academicYear)
    // A persisted academic year is an explicit choice; changing dates must not replace it.
    academicYearTouched.value = fresh.academicYear !== null
  } catch (cause) {
    if (!isCurrentEditRequest(revision, controller, semester.id)
      || cause instanceof StaleSessionGenerationError
      || isAbortError(cause)) return
    showError(cause, 'Данные семестра не удалось загрузить.')
  } finally {
    if (isCurrentEditRequest(revision, controller, semester.id)) {
      formLoading.value = false
      editAbortController = null
    }
  }
}

function isCurrentEditRequest(revision: number, controller: AbortController, semesterId: number): boolean {
  return !disposed
    && revision === editRequestRevision
    && editAbortController === controller
    && formMode.value === 'edit'
    && editingSemesterId.value === semesterId
}

async function saveSemester(): Promise<void> {
  if (saving.value || formLoading.value || mutationBusy.value) return
  const edited = editingSemesterId.value === null
    ? null
    : semesters.value.find((semester) => semester.id === editingSemesterId.value) ?? null
  if (edited && !canEdit(edited)) {
    error.value = 'Этот семестр сейчас нельзя изменить. Обнови его состояние и повтори попытку.'
    return
  }
  const selectedType = semesterType.value
  const selectedYear = Number(academicYear.value)
  const selectedDateFrom = dateFrom.value
  const selectedDateTo = dateTo.value
  if (!selectedDateFrom || !selectedDateTo
    || selectedType === null
    || !Number.isSafeInteger(selectedYear)
    || selectedYear < 1
    || selectedYear > 9998) {
    error.value = 'Укажи тип семестра, учебный год, дату начала и дату окончания.'
    return
  }

  const payload = {
    name: generatedSemesterName(selectedType, selectedYear),
    dateFrom: selectedDateFrom,
    dateTo: selectedDateTo,
    semesterType: selectedType,
    academicYear: selectedYear,
  }
  if (payload.dateTo < payload.dateFrom) {
    error.value = 'Дата окончания не может быть раньше даты начала.'
    return
  }

  saving.value = true
  error.value = null
  notice.value = null
  try {
    const overlap = await props.client.checkOverlap(
      payload.dateFrom,
      payload.dateTo,
      editingSemesterId.value ?? undefined,
    )
    if (overlap.overlaps) {
      error.value = `Даты пересекаются с семестром «${overlap.conflictingName ?? 'без названия'}».`
      return
    }
    const wasEditing = formMode.value === 'edit'
    if (wasEditing && editingSemesterId.value !== null) {
      await props.client.updateSemester(editingSemesterId.value, payload)
    } else {
      await props.client.createSemester(payload)
    }
    if (disposed) return
    cancelEditLoad()
    editingSemesterId.value = null
    semesterType.value = null
    name.value = ''
    academicYear.value = ''
    academicYearTouched.value = false
    dateFrom.value = ''
    dateTo.value = ''
    formVisible.value = false
    formMode.value = 'create'
    notice.value = wasEditing ? 'Изменения семестра сохранены.' : 'Семестр создан.'
    await refresh()
  } catch (cause) {
    if (!disposed && !(cause instanceof StaleSessionGenerationError)) {
      showError(cause, formMode.value === 'edit' ? 'Семестр не удалось сохранить.' : 'Семестр не удалось создать.')
    }
  } finally {
    if (!disposed) saving.value = false
  }
}

async function activateSemester(semester: AdminSemester): Promise<void> {
  if (!canActivate(semester) || mutationBusy.value || pendingActivationId.value !== null) return
  saving.value = true
  pendingActivationId.value = semester.id
  error.value = null
  notice.value = null
  try {
    // The client sends exactly one PATCH; the server deactivates the previous
    // active semester in the same transaction.
    await props.client.activateSemester(semester.id)
    if (disposed) return
    notice.value = `Активирован семестр «${semester.name}».`
    await refresh()
  } catch (cause) {
    if (!disposed && !(cause instanceof StaleSessionGenerationError)) showError(cause, 'Семестр не удалось активировать.')
  } finally {
    if (!disposed) {
      saving.value = false
      pendingActivationId.value = null
    }
  }
}

function canEdit(semester: AdminSemester): boolean {
  return !semester.archived
    && !semester.isWriteBlocked
    && semester.transition === 'NONE'
    && !semester.releasePending
    && !hasPendingArchiveOperation(semester.id)
}

function canActivate(semester: AdminSemester): boolean {
  return !semester.active && canEdit(semester)
}

function canArchive(semester: AdminSemester): boolean {
  return !semester.archived
    && semester.transition === 'NONE'
    && !semester.releasePending
    && !hasPendingArchiveOperation(semester.id)
    && !isNonRetryableFailure(semester.id, 'ARCHIVE')
}

function canRestore(semester: AdminSemester): boolean {
  return semester.archived
    && semester.transition === 'NONE'
    && !semester.releasePending
    && !hasPendingArchiveOperation(semester.id)
    && !isNonRetryableFailure(semester.id, 'RESTORE')
}

function hasPendingArchiveOperation(semesterId: number): boolean {
  return archiveCommand.value?.semesterId === semesterId
    || archiveOperations.value[semesterId]?.operationState === 'PENDING'
}

function isNonRetryableFailure(semesterId: number, action: AdminSemesterArchiveAction): boolean {
  const operation = archiveOperations.value[semesterId]
  return operation?.action === action && operation.operationState === 'ERROR' && !operation.retryable
}

function openArchiveConfirmation(semester: AdminSemester, action: AdminSemesterArchiveAction): void {
  if (mutationBusy.value) return
  if (action === 'ARCHIVE' ? !canArchive(semester) : !canRestore(semester)) return
  archiveConfirmation.value = { semesterId: semester.id, action }
  error.value = null
  notice.value = null
}

function cancelArchiveConfirmation(): void {
  archiveConfirmationRevision += 1
  archiveConfirmationChecking.value = false
  archiveConfirmation.value = null
}

async function confirmArchiveAction(): Promise<void> {
  const confirmation = archiveConfirmation.value
  if (confirmation === null || mutationBusy.value || archiveConfirmationChecking.value) return
  const semester = semesters.value.find((item) => item.id === confirmation.semesterId)
  if (!semester || (confirmation.action === 'ARCHIVE' ? !canArchive(semester) : !canRestore(semester))) {
    archiveConfirmation.value = null
    error.value = 'Состояние семестра изменилось. Обнови список перед повторной попыткой.'
    return
  }
  const scope = archiveScopeRevision
  const checkRevision = ++archiveConfirmationRevision
  archiveConfirmationChecking.value = true
  try {
    const status = await readArchiveStatus(semester.id, scope)
    if (!status || !isCurrentArchiveScope(scope)
      || checkRevision !== archiveConfirmationRevision
      || archiveConfirmation.value?.semesterId !== semester.id) return
    const current = semesters.value.find((item) => item.id === semester.id)
    if (!current
      || (confirmation.action === 'ARCHIVE' ? !canArchive(current) : !canRestore(current))) {
      archiveConfirmation.value = null
      error.value = 'Состояние семестра изменилось. Проверь статус перед новой попыткой.'
      return
    }

    let idempotencyKey: string
    try {
      idempotencyKey = createIdempotencyKey()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Не удалось создать ключ операции.'
      return
    }

    const command = {
      semesterId: semester.id,
      action: confirmation.action,
      idempotencyKey,
      operationId: null,
      phase: 'SUBMITTING' as const,
    }
    archiveConfirmation.value = null
    archiveCommand.value = command
    archiveStatusErrors.value = { ...archiveStatusErrors.value, [semester.id]: '' }
    error.value = null
    notice.value = confirmation.action === 'ARCHIVE'
      ? 'Архивация отправлена. Ждём подтверждения сервера.'
      : 'Восстановление отправлено. Ждём подтверждения сервера.'
    archiveConfirmationChecking.value = false
    await submitArchiveCommand(command, archiveScopeRevision)
  } finally {
    if (checkRevision === archiveConfirmationRevision) archiveConfirmationChecking.value = false
  }
}

async function submitArchiveCommand(
  command: NonNullable<typeof archiveCommand.value>,
  scope: number,
): Promise<void> {
  if (!isCurrentArchiveScope(scope) || archiveCommand.value !== command || command.idempotencyKey === null) return
  const controller = beginArchiveMonitor()
  try {
    const operation = command.action === 'ARCHIVE'
      ? await props.client.archiveSemester(command.semesterId, command.idempotencyKey, controller.signal)
      : await props.client.restoreSemester(command.semesterId, command.idempotencyKey, controller.signal)
    if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return
    await processArchiveOperation(command, operation, scope, controller)
  } catch (cause) {
    if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return
    if (cause instanceof StaleSessionGenerationError) {
      resetArchiveScope()
      return
    }
    if (cause instanceof AdminSemesterApiError && cause.archiveOperation) {
      if (cause.archiveOperation.semesterId === command.semesterId && cause.archiveOperation.action === command.action) {
        await processArchiveOperation(command, cause.archiveOperation, scope, controller)
        return
      }
    }
    if (cause instanceof AdminSemesterApiError && cause.response.status < 500) {
      archiveCommand.value = null
      archiveMonitorController = null
      showError(cause, command.action === 'ARCHIVE' ? 'Семестр не удалось архивировать.' : 'Семестр не удалось восстановить.')
      return
    }
    archiveCommand.value = { ...command, phase: 'UNCERTAIN' }
    archiveMonitorController = null
    error.value = 'Ответ сервера не подтверждён. Повтори тот же запрос: он сохранит прежний ключ и не создаст второй операции.'
    notice.value = null
    if (cause instanceof AdminSemesterApiError && (cause.response.status === 401 || cause.response.status === 403)) {
      emit('ownerError', cause)
    }
  }
}

async function processArchiveOperation(
  command: NonNullable<typeof archiveCommand.value>,
  operation: AdminSemesterArchiveOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return
  if (operation.semesterId !== command.semesterId || operation.action !== command.action) {
    archiveCommand.value = null
    error.value = 'Сервер вернул операцию для другого семестра или действия. Обнови список.'
    return
  }
  archiveOperations.value = { ...archiveOperations.value, [operation.semesterId]: operation }
  archiveStatusErrors.value = { ...archiveStatusErrors.value, [operation.semesterId]: '' }
  if (operation.operationState === 'PENDING') {
    const pendingCommand = { ...command, operationId: operation.operationId, phase: 'PENDING' as const }
    archiveCommand.value = pendingCommand
    notice.value = operationLabel(operation)
    try {
      await refreshCurrentArchiveState(operation.semesterId, scope, controller)
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return
      archiveStatusErrors.value = {
        ...archiveStatusErrors.value,
        [operation.semesterId]: errorMessage(cause, 'Текущее состояние семестра не удалось загрузить.'),
      }
    }
    if (!isCurrentArchiveScope(scope, controller)) return
    await pollArchiveOperation(pendingCommand, operation, scope, controller)
    return
  }
  if (operation.operationState === 'COMPLETED') {
    archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'SETTLING' }
    await settleCompletedOperation(archiveCommand.value, operation, scope, controller)
    return
  }

  await refreshCurrentArchiveState(operation.semesterId, scope, controller)
  if (!isCurrentArchiveScope(scope, controller)) return
  archiveCommand.value = null
  error.value = operation.blockingReason
    ?? (operation.retryable ? 'Операция завершилась с ошибкой. Можно подтвердить новую попытку.' : 'Операция завершилась с ошибкой; повтор недоступен.')
  notice.value = null
}

async function pollArchiveOperation(
  command: NonNullable<typeof archiveCommand.value>,
  initial: AdminSemesterArchiveOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  let operation = initial
  for (let attempt = 0; attempt < 10; attempt += 1) {
    if (!await waitForArchivePoll(controller.signal, 1_200)) return
    if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value?.operationId !== operation.operationId) return
    try {
      operation = await props.client.getArchiveOperation(operation.operationId, controller.signal)
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope()
        return
      }
      archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'PENDING' }
      error.value = 'Не удалось проверить ход операции. Продолжи проверку тем же operation ID.'
      notice.value = null
      archiveMonitorController = null
      return
    }
    if (!isCurrentArchiveScope(scope, controller)) return
    archiveOperations.value = { ...archiveOperations.value, [operation.semesterId]: operation }
    if (operation.operationState !== 'PENDING') {
      await processArchiveOperation(command, operation, scope, controller)
      return
    }
  }
  if (isCurrentArchiveScope(scope, controller)) {
    archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'PENDING' }
    notice.value = 'Операция всё ещё выполняется. Автоматическая проверка приостановлена; её можно продолжить вручную.'
    archiveMonitorController = null
  }
}

async function settleCompletedOperation(
  command: NonNullable<typeof archiveCommand.value>,
  operation: AdminSemesterArchiveOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'SETTLING' }
  for (let attempt = 0; attempt < 10; attempt += 1) {
    if (attempt > 0 && !await waitForArchivePoll(controller.signal, 1_200)) return
    if (!isCurrentArchiveScope(scope, controller)) return
    try {
      const current = await refreshCurrentArchiveState(command.semesterId, scope, controller)
      if (!isCurrentArchiveScope(scope, controller)) return
      if (current && operationHasExpectedCurrentState(current, command.action)) {
        await refresh(false)
        if (!isCurrentArchiveScope(scope, controller)) return
        const listed = semesters.value.find((item) => item.id === command.semesterId)
        if (listed && operationHasExpectedCurrentState(listed, command.action)) {
          archiveCommand.value = null
          archiveMonitorController = null
          error.value = null
          notice.value = command.action === 'ARCHIVE'
            ? `Семестр «${listed.name}» архивирован и стал неактивным. Данные сохранены.`
            : `Семестр «${listed.name}» восстановлен и остался неактивным. Чтобы сделать его текущим, активируй его отдельно.`
          return
        }
      }
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope()
        return
      }
      archiveStatusErrors.value = {
        ...archiveStatusErrors.value,
        [command.semesterId]: 'Операция завершена, но текущее состояние ещё не подтверждено. Продолжи проверку.',
      }
    }
  }
  if (isCurrentArchiveScope(scope, controller)) {
    archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'SETTLING' }
    notice.value = 'Операция завершена. Текущее состояние семестра ещё обновляется; продолжи проверку перед следующими действиями.'
    error.value = null
    archiveMonitorController = null
  }
}

async function refreshCurrentArchiveState(
  semesterId: number,
  scope: number,
  signalOwner: AbortController,
): Promise<AdminSemester | null> {
  const status = await props.client.getArchiveStatus(semesterId, signalOwner.signal)
  if (!isCurrentArchiveScope(scope, signalOwner)) return null
  if (status.semesterId !== semesterId) throw new Error('Сервер вернул статус другого семестра.')
  if (status.operation) archiveOperations.value = { ...archiveOperations.value, [semesterId]: status.operation }
  mergeArchiveAuthority(status)
  const current = await props.client.getSemester(semesterId, signalOwner.signal)
  if (!isCurrentArchiveScope(scope, signalOwner)) return null
  if (current.id !== semesterId) throw new Error('Сервер вернул другой семестр.')
  applySemesterSnapshot(current)
  return semesters.value.find((semester) => semester.id === semesterId) ?? current
}

function mergeArchiveAuthority(status: AdminSemesterArchiveStatus): void {
  const current = semesters.value.find((semester) => semester.id === status.semesterId)
  if (!current || status.stateVersion < current.stateVersion) return
  const authorityIsNewer = status.stateVersion > current.stateVersion
  const next: AdminSemester = {
    ...current,
    active: status.active,
    archived: status.archived,
    transition: status.transition,
    stateVersion: status.stateVersion,
    releasePending: status.releasePending,
    // The status DTO omits isWriteBlocked; remain fail-closed until the full
    // current semester response catches up to this authority version.
    isWriteBlocked: authorityIsNewer
      ? true
      : current.isWriteBlocked || status.archived || status.transition !== 'NONE' || status.releasePending,
  }
  applySemesterSnapshot(next)
}

function applySemesterSnapshot(next: AdminSemester): void {
  const current = semesters.value.find((semester) => semester.id === next.id)
  if (current && next.stateVersion < current.stateVersion) return
  semesters.value = current
    ? semesters.value.map((semester) => semester.id === next.id ? next : semester)
    : [...semesters.value, next]
}

async function hydrateArchiveStatuses(items: readonly AdminSemester[], scope: number): Promise<void> {
  for (const semester of items) {
    if (semester.transition === 'NONE' && !semester.releasePending) continue
    if (!isCurrentArchiveScope(scope) || archiveCommand.value !== null) return
    await readArchiveStatus(semester.id, scope)
  }
}

async function readArchiveStatus(semesterId: number, scope: number): Promise<AdminSemesterArchiveStatus | null> {
  const controller = new AbortController()
  archiveStatusControllers.add(controller)
  archiveStatusLoadingIds.value = new Set(archiveStatusLoadingIds.value).add(semesterId)
  try {
    const status = await props.client.getArchiveStatus(semesterId, controller.signal)
    if (!isCurrentArchiveScope(scope, controller)) return null
    if (status.semesterId !== semesterId) throw new Error('Сервер вернул статус другого семестра.')
    if (status.operation) archiveOperations.value = { ...archiveOperations.value, [semesterId]: status.operation }
    mergeArchiveAuthority(status)
    const current = await props.client.getSemester(semesterId, controller.signal)
    if (isCurrentArchiveScope(scope, controller)) applySemesterSnapshot(current)
    archiveStatusErrors.value = { ...archiveStatusErrors.value, [semesterId]: '' }
    return status
  } catch (cause) {
    if (isCurrentArchiveScope(scope, controller) && !(cause instanceof StaleSessionGenerationError) && !isAbortError(cause)) {
      const message = errorMessage(cause, 'Статус операции не удалось загрузить.')
      archiveStatusErrors.value = { ...archiveStatusErrors.value, [semesterId]: message }
      error.value = message
      if (cause instanceof AdminSemesterApiError && (cause.response.status === 401 || cause.response.status === 403)) {
        emit('ownerError', cause)
      }
    }
    return null
  } finally {
    archiveStatusControllers.delete(controller)
    const next = new Set(archiveStatusLoadingIds.value)
    next.delete(semesterId)
    archiveStatusLoadingIds.value = next
  }
}

async function resumeArchiveStatus(semester: AdminSemester): Promise<void> {
  if (archiveCommand.value !== null || archiveStatusLoadingIds.value.has(semester.id)) return
  const scope = archiveScopeRevision
  const status = await readArchiveStatus(semester.id, scope)
  if (!status || !isCurrentArchiveScope(scope) || status.operation === null) return
  const operation = status.operation
  if (operation.operationState === 'PENDING') {
    const command = {
      semesterId: operation.semesterId,
      action: operation.action,
      idempotencyKey: null,
      operationId: operation.operationId,
      phase: 'PENDING' as const,
    }
    archiveCommand.value = command
    const controller = beginArchiveMonitor()
    await pollArchiveOperation(command, operation, scope, controller)
  } else if (operation.operationState === 'COMPLETED') {
    const command = {
      semesterId: operation.semesterId,
      action: operation.action,
      idempotencyKey: null,
      operationId: operation.operationId,
      phase: 'SETTLING' as const,
    }
    archiveCommand.value = command
    const controller = beginArchiveMonitor()
    await settleCompletedOperation(command, operation, scope, controller)
  } else {
    error.value = operation.blockingReason
      ?? (operation.retryable ? 'Операция завершилась с ошибкой. Можно подтвердить новую попытку.' : 'Операция завершилась с ошибкой; повтор недоступен.')
    notice.value = null
  }
}

function retryUncertainArchiveCommand(semesterId: number): void {
  const command = archiveCommand.value
  if (!command || command.semesterId !== semesterId || command.phase !== 'UNCERTAIN' || command.idempotencyKey === null) return
  error.value = null
  notice.value = 'Повторяем запрос с тем же ключом операции…'
  void submitArchiveCommand(command, archiveScopeRevision)
}

async function continueArchiveCheck(semester: AdminSemester): Promise<void> {
  const command = archiveCommand.value
  if (command?.semesterId === semester.id) {
    if (command.operationId === null) return
    const controller = beginArchiveMonitor()
    const scope = archiveScopeRevision
    error.value = null
    notice.value = 'Проверяем состояние операции…'
    try {
      const operation = await props.client.getArchiveOperation(command.operationId, controller.signal)
      if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return
      if (operation.semesterId !== semester.id || operation.action !== command.action) {
        throw new Error('Сервер вернул операцию для другого семестра или действия.')
      }
      await processArchiveOperation(command, operation, scope, controller)
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope()
        return
      }
      error.value = errorMessage(cause, 'Состояние операции не удалось проверить.')
      notice.value = null
      archiveMonitorController = null
    }
    return
  }
  await resumeArchiveStatus(semester)
}

function beginArchiveMonitor(): AbortController {
  archiveMonitorController?.abort()
  const controller = new AbortController()
  archiveMonitorController = controller
  return controller
}

function resetArchiveScope(): void {
  archiveScopeRevision += 1
  archiveConfirmationRevision += 1
  archiveMonitorController?.abort()
  archiveMonitorController = null
  for (const controller of archiveStatusControllers) controller.abort()
  archiveStatusControllers.clear()
  archiveCommand.value = null
  archiveConfirmation.value = null
  archiveConfirmationChecking.value = false
  archiveOperations.value = {}
  archiveStatusErrors.value = {}
  archiveStatusLoadingIds.value = new Set()
  error.value = null
  notice.value = null
}

function isCurrentArchiveScope(scope: number, controller?: AbortController): boolean {
  return !disposed && scope === archiveScopeRevision && !controller?.signal.aborted
}

function createIdempotencyKey(): string {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    throw new Error('Браузер не поддерживает безопасный ключ операции. Обнови страницу в актуальном браузере.')
  }
  return globalThis.crypto.randomUUID()
}

function waitForArchivePoll(signal: AbortSignal, durationMs: number): Promise<boolean> {
  if (signal.aborted) return Promise.resolve(false)
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve(true)
    }, durationMs)
    const onAbort = (): void => {
      clearTimeout(timer)
      signal.removeEventListener('abort', onAbort)
      resolve(false)
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

function operationHasExpectedCurrentState(semester: AdminSemester, action: AdminSemesterArchiveAction): boolean {
  if (semester.transition !== 'NONE' || semester.releasePending) return false
  if (action === 'ARCHIVE') return semester.archived && !semester.active
  return !semester.archived && !semester.active && !semester.isWriteBlocked
}

function operationLabel(operation: AdminSemesterArchiveOperation): string {
  return operation.action === 'ARCHIVE' ? 'Архивация выполняется на сервере…' : 'Восстановление выполняется на сервере…'
}

function operationStateLabel(value: string): string {
  const labels: Record<string, string> = {
    NOT_STARTED: 'Ожидает',
    PENDING: 'Выполняется',
    READY: 'Подготовлено',
    PREPARED_RESTORE: 'Готово к восстановлению',
    RELEASE_PENDING: 'Ожидает завершения',
    RELEASED: 'Завершено',
  }
  return labels[value] ?? 'Состояние обновляется'
}

function participantStateLabel(value: string): string {
  return operationStateLabel(value)
}

function editBlockReason(semester: AdminSemester): string {
  if (semester.archived) return 'Сначала восстанови семестр из архива.'
  if (semester.transition !== 'NONE') return 'Дождись завершения архивации или восстановления.'
  if (semester.releasePending) return 'Сервер завершает восстановление.'
  if (hasPendingArchiveOperation(semester.id)) return 'Дождись завершения операции с архивом.'
  if (semester.isWriteBlocked) return 'Сервер временно запретил изменение семестра.'
  return 'Семестр сейчас нельзя изменить.'
}

function archiveBlockReason(semester: AdminSemester, action: AdminSemesterArchiveAction): string {
  if (semester.transition !== 'NONE') return 'Дождись завершения текущей операции.'
  if (semester.releasePending) return 'Сервер завершает восстановление.'
  if (hasPendingArchiveOperation(semester.id)) return 'Дождись завершения текущей операции.'
  return action === 'ARCHIVE' ? 'Семестр уже находится в архиве.' : 'Сначала архивируй семестр.'
}

function archiveOperationFor(semesterId: number): AdminSemesterArchiveOperation | null {
  return archiveOperations.value[semesterId] ?? null
}

function archiveCommandFor(semesterId: number): NonNullable<typeof archiveCommand.value> | null {
  return archiveCommand.value?.semesterId === semesterId ? archiveCommand.value : null
}

function needsArchiveStatus(semester: AdminSemester): boolean {
  return semester.transition !== 'NONE'
    || semester.releasePending
    || archiveCommandFor(semester.id) !== null
    || archiveOperationFor(semester.id)?.operationState === 'PENDING'
}

function canStartNewArchiveAttempt(semester: AdminSemester, action: AdminSemesterArchiveAction): boolean {
  const operation = archiveOperationFor(semester.id)
  return operation?.action === action && operation.operationState === 'ERROR' && operation.retryable
}

function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof AdminSemesterApiError
    ? cause.problem?.detail ?? cause.message
    : cause instanceof Error ? cause.message : fallback
}

function mergeSemesterList(current: readonly AdminSemester[], next: readonly AdminSemester[]): readonly AdminSemester[] {
  const currentById = new Map(current.map((semester) => [semester.id, semester]))
  return next.map((semester) => {
    const previous = currentById.get(semester.id)
    return previous && previous.stateVersion > semester.stateVersion ? previous : semester
  })
}

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof AdminSemesterApiError
    ? cause.problem?.detail ?? cause.message
    : cause instanceof Error ? cause.message : fallback
  if (cause instanceof AdminSemesterApiError && (cause.response.status === 401 || cause.response.status === 403)) {
    emit('ownerError', cause)
  }
}

function formatDate(value: string): string {
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' }).format(date)
}

function formatPeriod(semester: AdminSemester): string {
  return `${formatDate(semester.dateFrom)} — ${formatDate(semester.dateTo)}`
}

function statusLabel(semester: AdminSemester): string {
  if (semester.archived) return 'Архивный'
  if (semester.transition === 'ARCHIVING') return 'Архивация выполняется'
  if (semester.transition === 'RESTORING') return 'Восстановление выполняется'
  if (semester.releasePending) return 'Завершается восстановление'
  return semester.active ? 'Активный' : 'Неактивный'
}

function semesterTypeLabel(value: AdminSemesterType | null): string {
  if (value === 'AUTUMN') return 'Осенний семестр'
  if (value === 'SPRING') return 'Весенний семестр'
  return 'Тип не указан'
}

function generatedSemesterName(type: AdminSemesterType, academicYearValue: number): string {
  const season = type === 'AUTUMN' ? 'Осенний' : 'Весенний'
  return `${season} ${academicYearValue}/${academicYearValue + 1}`
}

function setAcademicYear(event: Event): void {
  academicYearTouched.value = true
  academicYear.value = (event.target as HTMLInputElement).value
}

onBeforeUnmount(() => {
  disposed = true
  listRequestRevision += 1
  listAbortController?.abort()
  listAbortController = null
  cancelEditLoad()
  resetArchiveScope()
})
</script>

<template>
  <main
    class="admin-semester-screen"
    :data-theme="theme"
    aria-labelledby="admin-semester-title"
  >
    <header class="admin-semester-screen__header">
      <p>Администрирование</p>
      <div class="admin-semester-screen__heading-row">
        <h1 id="admin-semester-title">
          Семестры
        </h1>
        <button
          class="admin-semester-screen__new"
          type="button"
          :disabled="mutationBusy"
          :aria-expanded="formVisible"
          @click="formVisible ? closeForm() : openCreateForm()"
        >
          {{ formVisible ? 'Скрыть форму' : '+ Новый семестр' }}
        </button>
      </div>
      <p class="admin-semester-screen__description">
        Создай учебный период и выбери один текущий контекст для системы.
      </p>
    </header>

    <form
      v-if="formVisible"
      class="admin-semester-card admin-semester-form"
      aria-labelledby="admin-semester-form-title"
      @submit.prevent="saveSemester"
    >
      <h2 id="admin-semester-form-title">
        {{ formMode === 'edit' ? 'Изменить семестр' : 'Новый семестр' }}
      </h2>
      <p
        v-if="formLoading"
        class="admin-semester-form__hint"
        role="status"
      >
        Загружаем сохранённые данные…
      </p>
      <fieldset
        class="admin-semester-form__types"
        :disabled="mutationBusy || formLoading"
      >
        <legend>Тип семестра</legend>
        <label class="admin-semester-form__type-option">
          <input
            v-model="semesterType"
            name="semester-type"
            required
            type="radio"
            value="AUTUMN"
          >
          <span>Осенний</span>
        </label>
        <label class="admin-semester-form__type-option">
          <input
            v-model="semesterType"
            name="semester-type"
            required
            type="radio"
            value="SPRING"
          >
          <span>Весенний</span>
        </label>
      </fieldset>
      <label>
        <span>Учебный год</span>
        <input
          :value="academicYear"
          inputmode="numeric"
          max="9998"
          min="1"
          required
          step="1"
          type="number"
          :disabled="mutationBusy || formLoading"
          @input="setAcademicYear"
        >
      </label>
      <p class="admin-semester-form__hint">
        Название формируется автоматически: {{ formName || 'выбери тип и укажи учебный год' }}.
      </p>
      <div class="admin-semester-form__dates">
        <label>
          <span>Начало</span>
          <input
            v-model="dateFrom"
            required
            type="date"
            :disabled="mutationBusy || formLoading"
          >
        </label>
        <label>
          <span>Конец</span>
          <input
            v-model="dateTo"
            required
            type="date"
            :disabled="mutationBusy || formLoading"
          >
        </label>
      </div>
      <p class="admin-semester-form__hint">
        Начало может быть в прошлом. Пересечение проверяется с учётом этой записи; сервер проверит его ещё раз при сохранении.
      </p>
      <p
        v-if="formMode === 'edit' && semesterType === null"
        class="admin-semester-form__hint"
      >
        У старой записи тип не указан. Выбери его явно; название и сохранённые данные не переопределяются автоматически.
      </p>
      <button
        class="admin-semester-action"
        type="submit"
        :disabled="mutationBusy || formLoading"
        :aria-busy="saving"
      >
        {{ saving ? 'Сохраняем…' : formMode === 'edit' ? 'Сохранить изменения' : 'Создать семестр' }}
      </button>
    </form>

    <p
      v-if="loading"
      class="admin-semester-state"
      role="status"
    >
      Загружаем семестры…
    </p>
    <div
      v-else
      class="admin-semester-screen__content"
    >
      <p
        v-if="error"
        class="admin-semester-state admin-semester-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="notice"
        class="admin-semester-state admin-semester-state--success"
        role="status"
      >
        {{ notice }}
      </p>
      <p
        v-if="semesters.length > 0 && !hasActiveSemester"
        class="admin-semester-state admin-semester-state--warning"
        role="status"
      >
        Активный семестр не выбран. Выбери период, который должен стать текущим.
      </p>
      <section
        v-if="archiveConfirmation && archiveConfirmationSemester"
        class="admin-semester-card admin-semester-confirmation"
        role="group"
        aria-labelledby="admin-semester-archive-confirmation-title"
      >
        <h2 id="admin-semester-archive-confirmation-title">
          {{ archiveConfirmation.action === 'ARCHIVE' ? 'Подтверди архивацию' : 'Подтверди восстановление' }}
        </h2>
        <p>
          {{ archiveConfirmation.action === 'ARCHIVE'
            ? `Архивировать семестр «${archiveConfirmationSemester.name}»? Все расписание, посещаемость и задания сохранятся; редактирование будет запрещено.`
            : `Восстановить семестр «${archiveConfirmationSemester.name}»? Все данные сохранятся, а сам семестр останется неактивным.` }}
        </p>
        <p v-if="archiveConfirmation.action === 'ARCHIVE' && archiveConfirmationSemester.active">
          После завершения не останется активного семестра. Другой период не активируется автоматически.
        </p>
        <p v-if="archiveConfirmation.action === 'RESTORE'">
          Чтобы сделать восстановленный семестр текущим, после завершения активируй его отдельно.
        </p>
        <div class="admin-semester-card__actions">
          <button
            class="admin-semester-action admin-semester-action--secondary"
            type="button"
            @click="cancelArchiveConfirmation"
          >
            Отмена
          </button>
          <button
            class="admin-semester-action"
            type="button"
            :disabled="archiveConfirmationChecking"
            :aria-busy="archiveConfirmationChecking"
            @click="confirmArchiveAction"
          >
            {{ archiveConfirmationChecking
              ? 'Проверяем состояние…'
              : archiveConfirmation.action === 'ARCHIVE' ? 'Подтвердить архивацию' : 'Подтвердить восстановление' }}
          </button>
        </div>
      </section>

      <section
        v-if="archiveSemestersVisible.length"
        class="admin-semester-group admin-semester-archive-status"
        aria-labelledby="admin-semester-archive-status-title"
      >
        <h2 id="admin-semester-archive-status-title">
          Архивация и восстановление
        </h2>
        <article
          v-for="semester in archiveSemestersVisible"
          :key="semester.id"
          class="admin-semester-card"
          :aria-busy="archiveStatusLoadingIds.has(semester.id) || archiveCommandFor(semester.id)?.phase === 'SUBMITTING'"
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status">
              <span aria-hidden="true">{{ semester.archived ? '▣' : semester.active ? '●' : '○' }}</span>
              {{ statusLabel(semester) }}
            </span>
          </div>
          <p
            v-if="archiveCommandFor(semester.id)?.phase === 'SUBMITTING'"
            class="admin-semester-archive-progress"
            role="status"
          >
            Отправляем запрос и ждём подтверждения сервера…
          </p>
          <p
            v-else-if="archiveCommandFor(semester.id)?.phase === 'UNCERTAIN'"
            class="admin-semester-state admin-semester-state--warning"
            role="status"
          >
            Ответ сервера не подтверждён. Повтори тот же запрос; ключ операции сохранён.
          </p>
          <p
            v-else-if="archiveCommandFor(semester.id)?.phase === 'SETTLING'"
            class="admin-semester-archive-progress"
            role="status"
          >
            Операция завершилась. Проверяем актуальное состояние семестра…
          </p>
          <p
            v-else-if="archiveCommandFor(semester.id)?.phase === 'PENDING' || archiveOperationFor(semester.id)?.operationState === 'PENDING'"
            class="admin-semester-archive-progress"
            role="status"
          >
            {{ archiveOperationFor(semester.id) ? operationLabel(archiveOperationFor(semester.id)!) : 'Сервер выполняет операцию…' }}
          </p>
          <p
            v-else-if="semester.releasePending"
            class="admin-semester-archive-progress"
            role="status"
          >
            Сервер завершает восстановление. Изменения пока недоступны.
          </p>
          <p
            v-else-if="semester.transition !== 'NONE'"
            class="admin-semester-archive-progress"
            role="status"
          >
            {{ semester.transition === 'ARCHIVING' ? 'Сервер архивирует семестр.' : 'Сервер восстанавливает семестр.' }}
          </p>
          <p
            v-if="semester.isWriteBlocked"
            class="admin-semester-form__hint"
          >
            Запись временно запрещена сервером.
          </p>
          <div
            v-if="archiveOperationFor(semester.id)"
            class="admin-semester-archive-details"
          >
            <p>
              Последняя операция: {{ archiveOperationFor(semester.id)?.action === 'ARCHIVE' ? 'архивация' : 'восстановление' }} —
              {{ operationStateLabel(archiveOperationFor(semester.id)!.operationState) }}.
            </p>
            <ul>
              <li>Учебные данные: {{ participantStateLabel(archiveOperationFor(semester.id)!.academic) }}</li>
              <li>Расписание: {{ participantStateLabel(archiveOperationFor(semester.id)!.schedule) }}</li>
              <li>Посещаемость: {{ participantStateLabel(archiveOperationFor(semester.id)!.attendance) }}</li>
            </ul>
          </div>
          <p
            v-if="archiveOperationFor(semester.id)?.operationState === 'ERROR'"
            class="admin-semester-state admin-semester-state--error"
            role="alert"
          >
            {{ archiveOperationFor(semester.id)?.blockingReason ?? 'Операция завершилась с ошибкой.' }}
          </p>
          <p
            v-if="archiveStatusErrors[semester.id]"
            class="admin-semester-state admin-semester-state--error"
            role="alert"
          >
            {{ archiveStatusErrors[semester.id] }}
          </p>
          <div class="admin-semester-card__actions">
            <button
              v-if="archiveCommandFor(semester.id)?.phase === 'UNCERTAIN'"
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              @click="retryUncertainArchiveCommand(semester.id)"
            >
              Повторить тот же запрос
            </button>
            <button
              v-else-if="archiveCommandFor(semester.id)?.phase === 'PENDING' || archiveCommandFor(semester.id)?.phase === 'SETTLING'"
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              @click="continueArchiveCheck(semester)"
            >
              Продолжить проверку
            </button>
            <button
              v-else-if="needsArchiveStatus(semester)"
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="archiveStatusLoadingIds.has(semester.id)"
              @click="resumeArchiveStatus(semester)"
            >
              {{ archiveStatusLoadingIds.has(semester.id) ? 'Проверяем…' : 'Проверить статус' }}
            </button>
          </div>
        </article>
      </section>
      <p
        v-if="semesters.length === 0 && !error"
        class="admin-semester-state"
        data-state="empty"
      >
        Семестров пока нет. Создай первый учебный период.
      </p>

      <section
        v-if="activeSemesters.length"
        class="admin-semester-group"
        aria-labelledby="admin-semester-active-title"
      >
        <h2 id="admin-semester-active-title">
          Текущий
        </h2>
        <article
          v-for="semester in activeSemesters"
          :key="semester.id"
          class="admin-semester-card admin-semester-card--active"
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status admin-semester-status--active">
              <span aria-hidden="true">●</span> {{ statusLabel(semester) }}
            </span>
          </div>
          <p class="admin-semester-card__period">
            <time :datetime="semester.dateFrom">{{ formatPeriod(semester) }}</time>
          </p>
          <p class="admin-semester-card__type">
            {{ semesterTypeLabel(semester.semesterType) }}
          </p>
          <div class="admin-semester-card__actions">
            <button
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="mutationBusy || !canEdit(semester)"
              :title="canEdit(semester) ? undefined : editBlockReason(semester)"
              @click="openEditForm(semester)"
            >
              Изменить
            </button>
            <button
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="mutationBusy || !canArchive(semester)"
              :title="canArchive(semester) ? undefined : archiveBlockReason(semester, 'ARCHIVE')"
              @click="openArchiveConfirmation(semester, 'ARCHIVE')"
            >
              {{ canStartNewArchiveAttempt(semester, 'ARCHIVE') ? 'Повторить архивацию' : 'Архивировать' }}
            </button>
          </div>
        </article>
      </section>

      <section
        v-if="inactiveSemesters.length"
        class="admin-semester-group"
        aria-labelledby="admin-semester-other-title"
      >
        <h2 id="admin-semester-other-title">
          Остальные
        </h2>
        <article
          v-for="semester in inactiveSemesters"
          :key="semester.id"
          class="admin-semester-card"
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status">
              <span aria-hidden="true">○</span> {{ statusLabel(semester) }}
            </span>
          </div>
          <p class="admin-semester-card__period">
            <time :datetime="semester.dateFrom">{{ formatPeriod(semester) }}</time>
          </p>
          <p class="admin-semester-card__type">
            {{ semesterTypeLabel(semester.semesterType) }}
          </p>
          <div class="admin-semester-card__actions">
            <button
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="mutationBusy || !canEdit(semester)"
              :title="canEdit(semester) ? undefined : editBlockReason(semester)"
              @click="openEditForm(semester)"
            >
              Изменить
            </button>
            <button
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="mutationBusy || !canActivate(semester)"
              :title="canActivate(semester) ? undefined : editBlockReason(semester)"
              :aria-busy="pendingActivationId === semester.id"
              @click="activateSemester(semester)"
            >
              {{ pendingActivationId === semester.id ? 'Активируем…' : 'Сделать текущим' }}
            </button>
            <button
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="mutationBusy || !canArchive(semester)"
              :title="canArchive(semester) ? undefined : archiveBlockReason(semester, 'ARCHIVE')"
              @click="openArchiveConfirmation(semester, 'ARCHIVE')"
            >
              {{ canStartNewArchiveAttempt(semester, 'ARCHIVE') ? 'Повторить архивацию' : 'Архивировать' }}
            </button>
          </div>
        </article>
      </section>

      <section
        v-if="archivedSemesters.length"
        class="admin-semester-group"
        aria-labelledby="admin-semester-archived-title"
      >
        <h2 id="admin-semester-archived-title">
          Архив
        </h2>
        <article
          v-for="semester in archivedSemesters"
          :key="semester.id"
          class="admin-semester-card admin-semester-card--archived"
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status admin-semester-status--archived">
              <span aria-hidden="true">▣</span> {{ statusLabel(semester) }}
            </span>
          </div>
          <p class="admin-semester-card__period">
            <time :datetime="semester.dateFrom">{{ formatPeriod(semester) }}</time>
          </p>
          <p class="admin-semester-card__type">
            {{ semesterTypeLabel(semester.semesterType) }}
          </p>
          <p class="admin-semester-form__hint">
            Данные сохранены. Редактирование и активация недоступны до восстановления.
          </p>
          <button
            class="admin-semester-action admin-semester-action--secondary"
            type="button"
            :disabled="mutationBusy || !canRestore(semester)"
            :title="canRestore(semester) ? undefined : archiveBlockReason(semester, 'RESTORE')"
            @click="openArchiveConfirmation(semester, 'RESTORE')"
          >
            {{ canStartNewArchiveAttempt(semester, 'RESTORE') ? 'Повторить восстановление' : 'Восстановить' }}
          </button>
        </article>
      </section>
    </div>
  </main>
</template>
