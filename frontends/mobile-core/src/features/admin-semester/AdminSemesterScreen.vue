<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { adminHasPermission, type AdminAccessProfile } from '../admin-access/admin-access-client';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import AdminNotice from '../../shared/components/AdminNotice.vue';
import SemesterCalendar from './SemesterCalendar.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import {
  type AdminSemesterArchiveAction,
  type AdminSemesterArchiveOperation,
  type AdminSemesterArchiveStatus,
  type AdminSemesterDeletionCounts,
  type AdminSemesterDeletionOperation,
  type AdminSemesterDeletionPhase,
  type AdminSemesterDeletionPreview,
  AdminSemesterApiError,
  type AdminSemesterType,
  type AdminSemester,
  type AdminSemesterClient,
} from './admin-semester-client';
import './admin-semester-screen.pcss';

const props = withDefaults(
  defineProps<{
    client: AdminSemesterClient;
    authority: AdminAccessProfile | null;
    theme?: 'dark' | 'light';
  }>(),
  {
    theme: 'dark',
  },
);

const emit = defineEmits<{
  ownerError: [cause: unknown];
  back: [];
  'navigation-visibility': [visible: boolean];
}>();

const canView = computed(() => adminHasPermission(props.authority, 'SEMESTERS_VIEW'));
const canManage = computed(
  () => canView.value && adminHasPermission(props.authority, 'SEMESTERS_MANAGE'),
);
const deletionAccessDenied = ref(false);
const canDelete = computed(
  () =>
    !deletionAccessDenied.value &&
    canManage.value &&
    adminHasPermission(props.authority, 'DATA_DELETE'),
);
const semesters = ref<readonly AdminSemester[]>([]);
const loading = ref(canView.value);
const saving = ref(false);
const pendingActivationId = ref<number | null>(null);
const formVisible = ref(false);
const calendarField = ref<'from' | 'to' | null>(null);
const activationConfirmation = ref<AdminSemester | null>(null);
const formMode = ref<'create' | 'edit'>('create');
const editingSemesterId = ref<number | null>(null);
const formLoading = ref(false);
const editingSnapshot = shallowRef<AdminSemester | null>(null);
const semesterType = ref<AdminSemesterType | null>(null);
const name = ref('');
const academicYear = ref('');
const academicYearTouched = ref(false);
const dateFrom = ref('');
const dateTo = ref('');
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const overlapWarning = ref<string | null>(null);
const dateError = ref<string | null>(null);
const deletionPasswordInvalid = ref(false);
const archiveConfirmation = ref<{ semesterId: number; action: AdminSemesterArchiveAction } | null>(
  null,
);
const archiveConfirmationChecking = ref(false);
// Async continuations compare this command by identity; keep the raw object.
const archiveCommand = shallowRef<{
  semesterId: number;
  action: AdminSemesterArchiveAction;
  idempotencyKey: string | null;
  operationId: string | null;
  phase: 'SUBMITTING' | 'UNCERTAIN' | 'PENDING' | 'SETTLING';
} | null>(null);
const archiveOperations = ref<Record<number, AdminSemesterArchiveOperation>>({});
const archiveSettlingOperationIds = ref<Record<number, string>>({});
const archiveStatusErrors = ref<Record<number, string>>({});
const archiveStatusLoadingIds = ref(new Set<number>());
const deletionPreview = shallowRef<AdminSemesterDeletionPreview | null>(null);
const deletionPreviewLoading = ref(false);
const deletionPassword = ref('');
const deletionError = ref<string | null>(null);
const deletionCommand = shallowRef<{
  semesterId: number;
  preview: AdminSemesterDeletionPreview;
  idempotencyKey: string;
  operationId: string | null;
  phase: 'SUBMITTING' | 'UNCERTAIN' | 'PASSWORD_REQUIRED' | 'PENDING' | 'TERMINAL';
  operation: AdminSemesterDeletionOperation | null;
} | null>(null);
const deletionOperation = shallowRef<AdminSemesterDeletionOperation | null>(null);
let authorityRevision = 0;
let disposed = false;
let listRequestRevision = 0;
let listAbortController: AbortController | null = null;
let editRequestRevision = 0;
let editAbortController: AbortController | null = null;
let archiveScopeRevision = 0;
let archiveConfirmationRevision = 0;
let archiveMonitorController: AbortController | null = null;
const archiveStatusControllers = new Set<AbortController>();
let deletionScopeRevision = 0;
let deletionPreviewRevision = 0;
let deletionPreviewController: AbortController | null = null;
let deletionMonitorController: AbortController | null = null;
let deletionStorageKey: string | null = null;

const formErrors = computed(() => {
  const old = editingSnapshot.value;
  return {
    type: !['AUTUMN', 'SPRING'].includes(semesterType.value ?? '') ? 'Выбери тип семестра.' : '',
    year:
      !/^[0-9]+$/.test(academicYear.value) ||
      Number(academicYear.value) < 1 ||
      Number(academicYear.value) > 9998
        ? 'Укажи учебный год от 1 до 9998.'
        : '',
    from: !validSemesterDate(dateFrom.value)
      ? 'Выбери корректную дату начала.'
      : old?.archived && dateFrom.value > old.dateFrom
        ? 'Начало архивного периода можно только перенести раньше.'
        : '',
    to: !validSemesterDate(dateTo.value)
      ? 'Выбери корректную дату окончания.'
      : dateTo.value < dateFrom.value
        ? 'Дата окончания не может быть раньше даты начала.'
        : old?.archived && dateTo.value < old.dateTo
          ? 'Окончание архивного периода можно только перенести позже.'
          : '',
  };
});
const formValid = computed(() => Object.values(formErrors.value).every((value) => value === ''));
function validSemesterDate(value: string): boolean {
  return (
    /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) &&
    value >= '0001-01-01' &&
    value <= '9999-12-31' &&
    !Number.isNaN(Date.parse(value + 'T00:00:00Z')) &&
    new Date(value + 'T00:00:00Z').toISOString() === value + 'T00:00:00.000Z'
  );
}
function moscowToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function semesterExpired(semester: AdminSemester): boolean {
  return semester.dateTo < moscowToday();
}
function semesterFuture(semester: AdminSemester): boolean {
  return !semester.archived && !semester.active && semester.dateFrom > moscowToday();
}

const activeSemesters = computed(() =>
  semesters.value.filter((semester) => semester.active && !semester.archived),
);
const inactiveSemesters = computed(() =>
  semesters.value.filter((semester) => !semester.active && !semester.archived),
);
const archivedSemesters = computed(() => semesters.value.filter((semester) => semester.archived));
const hasActiveSemester = computed(() => activeSemesters.value.length > 0);
// Durable barriers belong to one semester; only a request without a resolved
// сохранённой операцией occupies the single in-flight command slot for the whole screen.
const mutationBusy = computed(
  () =>
    saving.value ||
    archiveConfirmationChecking.value ||
    deletionPreviewLoading.value ||
    archiveCommand.value?.phase === 'SUBMITTING' ||
    archiveCommand.value?.phase === 'UNCERTAIN' ||
    (deletionCommand.value !== null && deletionCommand.value.phase !== 'TERMINAL') ||
    (deletionCommand.value?.phase === 'TERMINAL' &&
      deletionCommand.value.operation?.phase === 'CANCELLED' &&
      deletionPreview.value !== null),
);
const archiveConfirmationSemester = computed(() =>
  archiveConfirmation.value === null
    ? null
    : (semesters.value.find((semester) => semester.id === archiveConfirmation.value?.semesterId) ??
      null),
);
const visibleDeletionOperation = computed(
  () => deletionCommand.value?.operation ?? deletionOperation.value,
);
const deletionStorageAvailable = computed(() => currentDeletionStorageKey() !== null);
const archiveSemestersVisible = computed(() =>
  semesters.value.filter(
    (semester) =>
      needsArchiveStatus(semester) ||
      archiveOperationFor(semester.id)?.operationState === 'ERROR' ||
      Boolean(archiveStatusErrors.value[semester.id]),
  ),
);
const taskActive = computed(
  () =>
    formVisible.value ||
    activationConfirmation.value !== null ||
    archiveConfirmation.value !== null ||
    deletionPreview.value !== null ||
    deletionPreviewLoading.value ||
    deletionCommand.value !== null,
);
onMounted(() => emit('navigation-visibility', false));
function back(): void {
  if (calendarField.value) {
    calendarField.value = null;
    return;
  }
  if (activationConfirmation.value) {
    if (!saving.value) activationConfirmation.value = null;
    return;
  }
  if (archiveConfirmation.value) {
    if (!archiveConfirmationChecking.value) cancelArchiveConfirmation();
    return;
  }
  if (deletionPreview.value) {
    closeDeletionPreview();
    return;
  }
  if (deletionCommand.value) {
    if (deletionCommand.value.phase === 'TERMINAL') dismissDeletionResult();
    else emit('back');
    return;
  }
  if (formVisible.value) {
    closeForm();
    return;
  }
  emit('back');
}
defineExpose({ back });
function chooseCalendarDate(value: string): void {
  if (calendarField.value === 'from') dateFrom.value = value;
  else if (calendarField.value === 'to') dateTo.value = value;
}
const semesterSections = computed(() => [
  { key: 'active', label: 'Активный семестр', items: activeSemesters.value },
  { key: 'inactive', label: 'Неактивные семестры', items: inactiveSemesters.value },
  { key: 'archived', label: 'Архив', items: archivedSemesters.value },
]);

watch([dateFrom, dateTo], () => {
  overlapWarning.value = null;
  dateError.value = null;
});

watch([semesterType, dateFrom], ([type, from]) => {
  if (editingSnapshot.value?.archived) return;
  if (academicYearTouched.value || type === null || from === '') return;
  const calendarYear = Number(from.slice(0, 4));
  const defaultYear = type === 'SPRING' ? calendarYear - 1 : calendarYear;
  academicYear.value =
    Number.isSafeInteger(defaultYear) && defaultYear >= 1 && defaultYear <= 9998
      ? String(defaultYear)
      : '';
});

watch(
  () => props.client,
  () => {
    calendarField.value = null;
    activationConfirmation.value = null;
    resetArchiveScope();
    resetDeletionScope(true);
    reconcileDeletionStorageScope();
    listRequestRevision += 1;
    listAbortController?.abort();
    cancelEditLoad();
    formVisible.value = false;
    overlapWarning.value = null;
    editingSemesterId.value = null;
    void refresh();
  },
  { flush: 'sync' },
);

onMounted(() => {
  if (canDelete.value) restoreDeletionProgress();
  if (canView.value) void refresh();
});

async function refresh(loadArchiveStatuses = true): Promise<void> {
  if (!canView.value) {
    semesters.value = [];
    loading.value = false;
    return;
  }
  const revision = ++listRequestRevision;
  const archiveScope = archiveScopeRevision;
  listAbortController?.abort();
  const controller = new AbortController();
  listAbortController = controller;
  loading.value = true;
  error.value = null;
  try {
    const next = await props.client.listSemesters(controller.signal);
    if (!isCurrentListRequest(revision, controller)) return;
    semesters.value = mergeSemesterList(semesters.value, next);
    if (canManage.value && loadArchiveStatuses && archiveCommand.value === null) {
      void hydrateArchiveStatuses(next, archiveScope);
    }
  } catch (cause) {
    if (
      !isCurrentListRequest(revision, controller) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    showError(cause, 'Список семестров не удалось загрузить.');
  } finally {
    if (isCurrentListRequest(revision, controller)) {
      loading.value = false;
      listAbortController = null;
    }
  }
}

function isCurrentListRequest(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRequestRevision && listAbortController === controller;
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError';
}

function cancelEditLoad(): void {
  editRequestRevision += 1;
  editAbortController?.abort();
  editAbortController = null;
  formLoading.value = false;
}

function openCreateForm(): void {
  calendarField.value = null;
  if (!canManage.value || mutationBusy.value) return;
  cancelEditLoad();
  formMode.value = 'create';
  editingSnapshot.value = null;
  editingSemesterId.value = null;
  semesterType.value = 'AUTUMN';
  name.value = '';
  academicYear.value = String(new Date().getFullYear());
  academicYearTouched.value = false;
  dateFrom.value = `${academicYear.value}-09-01`;
  dateTo.value = `${Number(academicYear.value) + 1}-01-31`;
  formVisible.value = true;
  error.value = null;
  notice.value = null;
}

function closeForm(): void {
  calendarField.value = null;
  if (mutationBusy.value) return;
  cancelEditLoad();
  formVisible.value = false;
  overlapWarning.value = null;
  editingSemesterId.value = null;
  error.value = null;
  notice.value = null;
}

async function openEditForm(semester: AdminSemester): Promise<void> {
  calendarField.value = null;
  if (mutationBusy.value || !canEditMetadata(semester)) return;
  cancelEditLoad();
  formMode.value = 'edit';
  editingSnapshot.value = null;
  editingSemesterId.value = semester.id;
  formVisible.value = true;
  formLoading.value = true;
  semesterType.value = null;
  name.value = '';
  academicYear.value = '';
  academicYearTouched.value = false;
  dateFrom.value = '';
  dateTo.value = '';
  error.value = null;
  notice.value = null;

  const revision = ++editRequestRevision;
  const controller = new AbortController();
  editAbortController = controller;
  try {
    const fresh = await props.client.getSemester(semester.id, controller.signal);
    if (!isCurrentEditRequest(revision, controller, semester.id)) return;
    if (!canEditMetadata(fresh)) {
      formVisible.value = false;
      error.value = editBlockReason(fresh);
      return;
    }
    editingSnapshot.value = fresh;
    name.value = fresh.name;
    dateFrom.value = fresh.dateFrom;
    dateTo.value = fresh.dateTo;
    // Legacy rows stay explicitly untyped until an admin chooses a type.
    semesterType.value = fresh.semesterType;
    academicYear.value = fresh.academicYear === null ? '' : String(fresh.academicYear);
    // A persisted academic year is an explicit choice; changing dates must not replace it.
    academicYearTouched.value = fresh.academicYear !== null;
  } catch (cause) {
    if (
      !isCurrentEditRequest(revision, controller, semester.id) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    showError(cause, 'Данные семестра не удалось загрузить.');
  } finally {
    if (isCurrentEditRequest(revision, controller, semester.id)) {
      formLoading.value = false;
      editAbortController = null;
    }
  }
}

function isCurrentEditRequest(
  revision: number,
  controller: AbortController,
  semesterId: number,
): boolean {
  return (
    !disposed &&
    revision === editRequestRevision &&
    editAbortController === controller &&
    formMode.value === 'edit' &&
    editingSemesterId.value === semesterId
  );
}

async function saveSemester(): Promise<void> {
  if (!canManage.value || saving.value || formLoading.value || mutationBusy.value) return;
  const authority = props.authority;
  const scope = authorityRevision;
  const client = props.client;
  const edited =
    editingSemesterId.value === null
      ? null
      : (semesters.value.find((semester) => semester.id === editingSemesterId.value) ?? null);
  if (edited && !canEditMetadata(edited)) {
    error.value = 'Этот семестр сейчас нельзя изменить. Обнови его состояние и повтори попытку.';
    return;
  }
  if (!formValid.value) return;
  const snapshot = editingSnapshot.value;
  if (
    snapshot?.archived &&
    ((snapshot.semesterType !== null && semesterType.value !== snapshot.semesterType) ||
      (snapshot.academicYear !== null && Number(academicYear.value) !== snapshot.academicYear))
  )
    return;
  const selectedType = semesterType.value;
  const selectedYear = Number(academicYear.value);
  const selectedDateFrom = dateFrom.value;
  const selectedDateTo = dateTo.value;
  if (
    !selectedDateFrom ||
    !selectedDateTo ||
    selectedType === null ||
    !Number.isSafeInteger(selectedYear) ||
    selectedYear < 1 ||
    selectedYear > 9998
  ) {
    error.value = 'Укажи тип семестра, учебный год, дату начала и дату окончания.';
    return;
  }

  const payload = {
    name: generatedSemesterName(selectedType, selectedYear),
    dateFrom: selectedDateFrom,
    dateTo: selectedDateTo,
    semesterType: selectedType,
    academicYear: selectedYear,
  };
  if (payload.dateTo < payload.dateFrom) {
    dateError.value = 'Дата окончания не может быть раньше даты начала.';
    return;
  }

  saving.value = true;
  error.value = null;
  overlapWarning.value = null;
  notice.value = null;
  try {
    const overlap = await props.client.checkOverlap(
      payload.dateFrom,
      payload.dateTo,
      editingSemesterId.value ?? undefined,
    );
    if (
      disposed ||
      authority !== props.authority ||
      scope !== authorityRevision ||
      client !== props.client ||
      !canManage.value
    )
      return;
    if (overlap.overlaps) {
      overlapWarning.value = `Даты пересекаются с семестром «${overlap.conflictingName ?? 'без названия'}».`;
      return;
    }
    const wasEditing = formMode.value === 'edit';
    if (wasEditing && editingSemesterId.value !== null) {
      await props.client.updateSemester(editingSemesterId.value, payload);
    } else {
      await props.client.createSemester(payload);
    }
    if (
      disposed ||
      authority !== props.authority ||
      scope !== authorityRevision ||
      client !== props.client
    )
      return;
    cancelEditLoad();
    editingSemesterId.value = null;
    semesterType.value = null;
    name.value = '';
    academicYear.value = '';
    academicYearTouched.value = false;
    dateFrom.value = '';
    dateTo.value = '';
    formVisible.value = false;
    formMode.value = 'create';
    notice.value = wasEditing ? 'Изменения семестра сохранены.' : 'Семестр создан.';
    await refresh();
  } catch (cause) {
    if (
      !disposed &&
      authority === props.authority &&
      scope === authorityRevision &&
      client === props.client &&
      !(cause instanceof StaleSessionGenerationError)
    ) {
      showError(
        cause,
        formMode.value === 'edit' ? 'Семестр не удалось сохранить.' : 'Семестр не удалось создать.',
      );
    }
  } finally {
    if (!disposed && scope === authorityRevision) saving.value = false;
  }
}

async function activateSemester(semester: AdminSemester): Promise<void> {
  if (!canActivate(semester) || mutationBusy.value || pendingActivationId.value !== null) return;
  const authority = props.authority;
  const scope = authorityRevision;
  const client = props.client;
  saving.value = true;
  pendingActivationId.value = semester.id;
  error.value = null;
  notice.value = null;
  try {
    const result = await props.client.activateSemester(semester.id);
    if (
      disposed ||
      authority !== props.authority ||
      scope !== authorityRevision ||
      client !== props.client
    )
      return;
    activationConfirmation.value = null;
    notice.value = result.active
      ? `Активирован семестр «${semester.name}».`
      : result.activationScheduled
        ? `Активация семестра «${semester.name}» запланирована на ${formatDate(result.dateFrom)}.`
        : `Запрос принят, но активация не подтверждена. Проверь состояние семестра.`;
    await refresh();
  } catch (cause) {
    if (
      !disposed &&
      authority === props.authority &&
      scope === authorityRevision &&
      client === props.client &&
      !(cause instanceof StaleSessionGenerationError)
    )
      showError(cause, 'Семестр не удалось активировать.');
  } finally {
    if (!disposed && scope === authorityRevision) {
      saving.value = false;
      pendingActivationId.value = null;
    }
  }
}

function canEditMetadata(semester: AdminSemester): boolean {
  return (
    canManage.value &&
    (semester.archived || !semester.isWriteBlocked) &&
    semester.transition === 'NONE' &&
    !semester.releasePending &&
    !hasPendingArchiveOperation(semester.id) &&
    !(
      deletionCommand.value?.semesterId === semester.id &&
      deletionCommand.value.phase !== 'TERMINAL'
    )
  );
}

function canActivate(semester: AdminSemester): boolean {
  return (
    !semester.archived &&
    !semester.active &&
    !semesterExpired(semester) &&
    canEditMetadata(semester)
  );
}

function canArchive(semester: AdminSemester): boolean {
  return (
    canManage.value &&
    !semester.archived &&
    semester.transition === 'NONE' &&
    !semester.releasePending &&
    !hasPendingArchiveOperation(semester.id) &&
    !isNonRetryableFailure(semester.id, 'ARCHIVE')
  );
}

function canRestore(semester: AdminSemester): boolean {
  return (
    canManage.value &&
    semester.archived &&
    !semesterExpired(semester) &&
    semester.transition === 'NONE' &&
    !semester.releasePending &&
    !hasPendingArchiveOperation(semester.id) &&
    !isNonRetryableFailure(semester.id, 'RESTORE')
  );
}

function hasPendingArchiveOperation(semesterId: number): boolean {
  return (
    archiveCommand.value?.semesterId === semesterId ||
    archiveSettlingOperationIds.value[semesterId] !== undefined ||
    archiveOperations.value[semesterId]?.operationState === 'PENDING'
  );
}

function isNonRetryableFailure(semesterId: number, action: AdminSemesterArchiveAction): boolean {
  const operation = archiveOperations.value[semesterId];
  return (
    operation?.action === action && operation.operationState === 'ERROR' && !operation.retryable
  );
}

function canRequestSemesterDeletion(semester: AdminSemester): boolean {
  if (!canDelete.value) return false;
  return (
    semester.transition === 'NONE' &&
    !semester.releasePending &&
    !hasPendingArchiveOperation(semester.id)
  );
}

function deletionBlockReason(semester: AdminSemester): string {
  if (semester.transition !== 'NONE') return 'Дождись завершения текущей операции.';
  if (semester.releasePending) return 'Завершается восстановление.';
  if (hasPendingArchiveOperation(semester.id)) return 'Дождись завершения операции с архивом.';
  return 'Семестр пока нельзя удалить.';
}

async function openDeletionPreview(semester: AdminSemester): Promise<void> {
  if (mutationBusy.value || !canRequestSemesterDeletion(semester)) return;
  deletionPreviewController?.abort();
  const revision = ++deletionPreviewRevision;
  const scope = deletionScopeRevision;
  const controller = new AbortController();
  deletionPreviewController = controller;
  deletionPreviewLoading.value = true;
  deletionPreview.value = null;
  deletionPassword.value = '';
  deletionPasswordInvalid.value = false;
  deletionError.value = null;
  try {
    const preview = await props.client.getSemesterDeletionPreview(semester.id, controller.signal);
    if (!isCurrentDeletionScope(scope, controller) || revision !== deletionPreviewRevision) return;
    deletionPreview.value = preview;
  } catch (cause) {
    if (
      !isCurrentDeletionScope(scope, controller) ||
      revision !== deletionPreviewRevision ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    deletionError.value =
      'Числа для предпросмотра не удалось загрузить. Попробуй обновить их ещё раз.';
    if (rejectDeletionAuthorization(cause)) return;
  } finally {
    if (revision === deletionPreviewRevision && deletionPreviewController === controller) {
      deletionPreviewLoading.value = false;
      deletionPreviewController = null;
    }
  }
}

function closeDeletionPreview(): void {
  if (deletionCommand.value?.phase === 'SUBMITTING') return;
  deletionPreviewRevision += 1;
  deletionPreviewController?.abort();
  deletionPreviewController = null;
  deletionPreviewLoading.value = false;
  deletionPreview.value = null;
  deletionPassword.value = '';
  deletionPasswordInvalid.value = false;
  deletionError.value = null;
}

async function confirmSemesterDeletion(): Promise<void> {
  const preview = deletionPreview.value;
  const password = deletionPassword.value;
  if (!preview || password.length === 0 || deletionPreviewLoading.value) return;
  if (
    saving.value ||
    archiveConfirmationChecking.value ||
    archiveCommand.value?.phase === 'SUBMITTING' ||
    archiveCommand.value?.phase === 'UNCERTAIN'
  )
    return;
  const current = semesters.value.find((semester) => semester.id === preview.semesterId);
  if (current && !canRequestSemesterDeletion(current)) {
    deletionPassword.value = '';
    deletionError.value =
      'Состояние семестра изменилось. Сначала дождись завершения текущей операции.';
    return;
  }
  const scopeKey = currentDeletionStorageKey();
  if (scopeKey === null) {
    deletionPassword.value = '';
    deletionError.value =
      'Не удалось подтвердить текущую сессию администратора для сохранения хода операции. Обнови профиль и повтори попытку.';
    return;
  }
  let idempotencyKey: string;
  const previous = deletionCommand.value;
  if (
    previous &&
    previous.semesterId === preview.semesterId &&
    previous.preview.previewDigest === preview.previewDigest &&
    (previous.phase === 'UNCERTAIN' || previous.phase === 'PASSWORD_REQUIRED')
  ) {
    idempotencyKey = previous.idempotencyKey;
  } else {
    try {
      idempotencyKey = createIdempotencyKey();
    } catch (cause) {
      deletionPassword.value = '';
      deletionError.value =
        cause instanceof Error ? cause.message : 'Не удалось подготовить подтверждение.';
      return;
    }
  }
  const command = {
    semesterId: preview.semesterId,
    preview,
    idempotencyKey,
    operationId: null,
    phase: 'SUBMITTING' as const,
    operation: null,
  };
  deletionPassword.value = '';
  deletionPasswordInvalid.value = false;
  deletionError.value = null;
  deletionOperation.value = null;
  deletionCommand.value = command;
  deletionStorageKey = scopeKey;
  if (!persistDeletionCommand(command)) {
    deletionCommand.value = previous;
    deletionError.value =
      'Браузер не сохранил состояние операции. Удаление не отправлено; проверь настройки хранения страницы.';
    return;
  }
  deletionPreview.value = null;
  await sendDeletionCommand(command, password, deletionScopeRevision);
}

async function sendDeletionCommand(
  command: NonNullable<typeof deletionCommand.value>,
  password: string,
  scope: number,
): Promise<void> {
  if (
    !isCurrentDeletionScope(scope) ||
    deletionCommand.value?.idempotencyKey !== command.idempotencyKey
  )
    return;
  const controller = beginDeletionMonitor();
  try {
    const operation = await props.client.deleteSemester(
      command.semesterId,
      {
        previewDigest: command.preview.previewDigest,
        password,
      },
      command.idempotencyKey,
      controller.signal,
    );
    if (
      !isCurrentDeletionScope(scope, controller) ||
      deletionCommand.value?.idempotencyKey !== command.idempotencyKey
    )
      return;
    await applyDeletionOperation(command, operation, scope, controller);
    if (
      operation.phase === 'PREPARING' ||
      operation.phase === 'RELEASING' ||
      operation.phase === 'DELETING'
    ) {
      await pollDeletionOperation(command, operation, scope, controller);
    }
  } catch (cause) {
    if (
      !isCurrentDeletionScope(scope, controller) ||
      deletionCommand.value?.idempotencyKey !== command.idempotencyKey
    )
      return;
    if (cause instanceof StaleSessionGenerationError) {
      resetDeletionScope(true);
      return;
    }
    if (rejectDeletionAuthorization(cause, true)) return;
    if (
      cause instanceof AdminSemesterApiError &&
      cause.deletionOperation &&
      cause.deletionOperation.semesterId === command.semesterId
    ) {
      await applyDeletionOperation(command, cause.deletionOperation, scope, controller);
      if (
        cause.deletionOperation.phase === 'PREPARING' ||
        cause.deletionOperation.phase === 'RELEASING' ||
        cause.deletionOperation.phase === 'DELETING'
      ) {
        await pollDeletionOperation(command, cause.deletionOperation, scope, controller);
      }
      return;
    }
    handleDeletionSubmitError(command, cause);
  } finally {
    if (deletionMonitorController === controller) deletionMonitorController = null;
  }
}

function rejectDeletionAuthorization(cause: unknown, allowPasswordFailure = false): boolean {
  if (
    !(cause instanceof AdminSemesterApiError) ||
    (cause.response.status !== 401 && cause.response.status !== 403)
  )
    return false;
  const problem: unknown = cause.problem;
  const code = isRecord(problem)
    ? (problem.code ?? (isRecord(problem.extras) ? problem.extras.code : undefined))
    : undefined;
  if (allowPasswordFailure && cause.response.status === 403 && code === 'CURRENT_PASSWORD_INVALID')
    return false;
  removePersistedDeletionCommand();
  resetDeletionScope(true);
  deletionAccessDenied.value = true;
  error.value =
    cause.response.status === 401
      ? 'Сессия истекла. Войди снова и проверь состояние операции.'
      : 'Удаление недоступно с текущими правами. Обнови права доступа перед продолжением.';
  emit('ownerError', cause);
  return true;
}

function handleDeletionSubmitError(
  command: NonNullable<typeof deletionCommand.value>,
  cause: unknown,
): void {
  if (rejectDeletionAuthorization(cause, true)) return;
  const status = cause instanceof AdminSemesterApiError ? cause.response.status : null;
  deletionPasswordInvalid.value = status === 403;
  const retryNeedsPassword = status === 400 || status === 403 || status === 429;
  const next = {
    ...command,
    operationId: null,
    phase: retryNeedsPassword ? ('PASSWORD_REQUIRED' as const) : ('UNCERTAIN' as const),
    operation: null,
  };
  deletionCommand.value = next;
  deletionPreview.value = command.preview;
  deletionOperation.value = null;
  deletionError.value =
    status === 403
      ? 'Пароль не подтверждён. Проверь его и введи снова; повтор использует тот же предпросмотр.'
      : status === 429
        ? 'Слишком много попыток. После паузы введи пароль и продолжи по сохранённому предпросмотру.'
        : status === 400
          ? 'Запрос не принят. Проверь актуальность предпросмотра; повтор не создаст новую операцию.'
          : 'Ответ сервера не подтверждён. Повтори тот же запрос с тем же предпросмотром; пароль нужно ввести снова.';
  persistDeletionCommand(next);
}

function beginDeletionMonitor(): AbortController {
  deletionMonitorController?.abort();
  const controller = new AbortController();
  deletionMonitorController = controller;
  return controller;
}

async function pollDeletionOperation(
  command: NonNullable<typeof deletionCommand.value>,
  initial: AdminSemesterDeletionOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  const operationId = initial.operationId;
  let operation = initial;
  while (isCurrentDeletionScope(scope, controller)) {
    if (!(await waitForArchivePoll(controller.signal, 1_200))) return;
    if (
      !isCurrentDeletionScope(scope, controller) ||
      deletionCommand.value?.operationId !== operation.operationId
    )
      return;
    try {
      operation = await props.client.getSemesterDeletionOperation(
        operation.operationId,
        controller.signal,
      );
    } catch (cause) {
      if (!isCurrentDeletionScope(scope, controller)) return;
      if (cause instanceof StaleSessionGenerationError) {
        resetDeletionScope(true);
        return;
      }
      if (rejectDeletionAuthorization(cause)) return;
      const current = deletionCommand.value;
      if (current?.operationId === operationId) {
        const next = { ...current, phase: 'PENDING' as const };
        deletionCommand.value = next;
        persistDeletionCommand(next);
      }
      deletionError.value =
        'Не удалось проверить ход удаления. Операция остаётся доступна по сохранённым данным; продолжи проверку позже.';
      deletionMonitorController = null;
      return;
    }
    if (!isCurrentDeletionScope(scope, controller)) return;
    if (operation.semesterId !== command.semesterId || operation.operationId !== operationId) {
      deletionError.value =
        'Сервер вернул состояние другой операции. Обнови страницу и проверь сохранённый результат.';
      deletionMonitorController = null;
      return;
    }
    await applyDeletionOperation(command, operation, scope, controller);
    if (operation.phase === 'COMPLETED' || operation.phase === 'CANCELLED') return;
  }
}

async function applyDeletionOperation(
  command: NonNullable<typeof deletionCommand.value>,
  operation: AdminSemesterDeletionOperation,
  scope: number,
  controller?: AbortController,
): Promise<void> {
  if (
    !isCurrentDeletionScope(scope, controller) ||
    operation.semesterId !== command.semesterId ||
    (command.operationId !== null && command.operationId !== operation.operationId)
  )
    return;
  const terminal = operation.phase === 'COMPLETED' || operation.phase === 'CANCELLED';
  const next = {
    ...command,
    preview: operation.refreshedPreview ?? command.preview,
    operationId: operation.operationId,
    phase: terminal ? ('TERMINAL' as const) : ('PENDING' as const),
    operation,
  };
  deletionCommand.value = next;
  deletionOperation.value = operation;
  persistDeletionCommand(next);
  if (!terminal) {
    deletionPreview.value = null;
    deletionPassword.value = '';
    deletionError.value = null;
    return;
  }

  deletionPassword.value = '';
  if (operation.phase === 'COMPLETED') {
    deletionPreview.value = null;
    deletionError.value = null;
    semesters.value = semesters.value.filter((semester) => semester.id !== operation.semesterId);
    notice.value = `Семестр «${command.preview.semesterName}» удалён. Учетные записи и группы сохранены.`;
    await refresh(false);
    if (isCurrentDeletionScope(scope, controller)) {
      semesters.value = semesters.value.filter((semester) => semester.id !== operation.semesterId);
    }
    return;
  }

  deletionPreview.value = operation.refreshedPreview;
  deletionError.value = deletionCancellationMessage(operation);
  notice.value = null;
}

function deletionCancellationMessage(operation: AdminSemesterDeletionOperation): string {
  if (operation.reason === 'STALE_PREVIEW') {
    return 'Состав данных изменился. Удаление отменено; проверь обновлённые числа и подтверди действие ещё раз.';
  }
  if (operation.reason === 'PREPARE_EXPIRED') {
    return 'Подготовка истекла, связанные данные освобождены. Удаление отменено; проверь предпросмотр ещё раз.';
  }
  return operation.blockingReason ?? 'Удаление отменено. Данные семестра сохранены.';
}

async function continueDeletionCheck(): Promise<void> {
  const command = deletionCommand.value;
  if (!command || command.phase === 'SUBMITTING') return;
  if (!command.operationId) {
    deletionPreview.value = command.preview;
    deletionPassword.value = '';
    deletionError.value =
      'Введи пароль, чтобы повторить тот же запрос по сохранённому предпросмотру.';
    return;
  }
  const scope = deletionScopeRevision;
  const controller = beginDeletionMonitor();
  deletionError.value = null;
  try {
    const operation = await props.client.getSemesterDeletionOperation(
      command.operationId,
      controller.signal,
    );
    if (
      !isCurrentDeletionScope(scope, controller) ||
      deletionCommand.value?.operationId !== command.operationId
    )
      return;
    await applyDeletionOperation(command, operation, scope, controller);
    if (
      operation.phase === 'PREPARING' ||
      operation.phase === 'RELEASING' ||
      operation.phase === 'DELETING'
    ) {
      await pollDeletionOperation(command, operation, scope, controller);
    }
  } catch (cause) {
    if (!isCurrentDeletionScope(scope, controller)) return;
    if (cause instanceof StaleSessionGenerationError) {
      resetDeletionScope(true);
      return;
    }
    if (rejectDeletionAuthorization(cause)) return;
    deletionError.value =
      'Состояние операции пока недоступно. Продолжи проверку сохранённой операции.';
    deletionMonitorController = null;
  }
}

function reopenDeletionConfirmation(): void {
  const command = deletionCommand.value;
  if (!command || command.operationId !== null || command.phase === 'SUBMITTING') return;
  deletionPreview.value = command.preview;
  deletionPassword.value = '';
  deletionError.value =
    'Введи пароль, чтобы повторить тот же запрос по сохранённому предпросмотру.';
}

function loadFreshDeletionPreview(): void {
  const command = deletionCommand.value;
  if (!command) return;
  const semester = semesters.value.find((item) => item.id === command.semesterId);
  if (!semester) {
    deletionError.value =
      'Семестр уже отсутствует в списке. Проверь сохранённый результат операции.';
    return;
  }
  void openDeletionPreview(semester);
}

function dismissDeletionResult(): void {
  if (deletionCommand.value?.phase !== 'TERMINAL' || deletionPreview.value !== null) return;
  removePersistedDeletionCommand();
  deletionCommand.value = null;
  deletionOperation.value = null;
  deletionError.value = null;
  notice.value = null;
}

function openArchiveConfirmation(
  semester: AdminSemester,
  action: AdminSemesterArchiveAction,
): void {
  if (mutationBusy.value) return;
  if (action === 'ARCHIVE' ? !canArchive(semester) : !canRestore(semester)) return;
  archiveConfirmation.value = { semesterId: semester.id, action };
  error.value = null;
  notice.value = null;
}

function cancelArchiveConfirmation(): void {
  archiveConfirmationRevision += 1;
  archiveConfirmationChecking.value = false;
  archiveConfirmation.value = null;
}

async function confirmArchiveAction(): Promise<void> {
  const confirmation = archiveConfirmation.value;
  if (confirmation === null || mutationBusy.value || archiveConfirmationChecking.value) return;
  const semester = semesters.value.find((item) => item.id === confirmation.semesterId);
  if (
    !semester ||
    (confirmation.action === 'ARCHIVE' ? !canArchive(semester) : !canRestore(semester))
  ) {
    archiveConfirmation.value = null;
    error.value = 'Состояние семестра изменилось. Обнови список перед повторной попыткой.';
    return;
  }
  const scope = archiveScopeRevision;
  const checkRevision = ++archiveConfirmationRevision;
  archiveConfirmationChecking.value = true;
  try {
    const status = await readArchiveStatus(semester.id, scope);
    if (
      !status ||
      !isCurrentArchiveScope(scope) ||
      checkRevision !== archiveConfirmationRevision ||
      archiveConfirmation.value?.semesterId !== semester.id
    )
      return;
    const current = semesters.value.find((item) => item.id === semester.id);
    if (
      !current ||
      (confirmation.action === 'ARCHIVE' ? !canArchive(current) : !canRestore(current))
    ) {
      archiveConfirmation.value = null;
      error.value = 'Состояние семестра изменилось. Проверь статус перед новой попыткой.';
      return;
    }

    let idempotencyKey: string;
    try {
      idempotencyKey = createIdempotencyKey();
    } catch (cause) {
      error.value =
        cause instanceof Error ? cause.message : 'Не удалось подготовить подтверждение.';
      return;
    }

    const command = {
      semesterId: semester.id,
      action: confirmation.action,
      idempotencyKey,
      operationId: null,
      phase: 'SUBMITTING' as const,
    };
    archiveConfirmation.value = null;
    archiveCommand.value = command;
    archiveStatusErrors.value = { ...archiveStatusErrors.value, [semester.id]: '' };
    error.value = null;
    notice.value =
      confirmation.action === 'ARCHIVE'
        ? 'Архивация отправлена. Ждём подтверждения сервера.'
        : 'Восстановление отправлено. Ждём подтверждения сервера.';
    archiveConfirmationChecking.value = false;
    await submitArchiveCommand(command, archiveScopeRevision);
  } finally {
    if (checkRevision === archiveConfirmationRevision) archiveConfirmationChecking.value = false;
  }
}

async function submitArchiveCommand(
  command: NonNullable<typeof archiveCommand.value>,
  scope: number,
): Promise<void> {
  if (
    !isCurrentArchiveScope(scope) ||
    archiveCommand.value !== command ||
    command.idempotencyKey === null
  )
    return;
  const controller = beginArchiveMonitor();
  try {
    const operation =
      command.action === 'ARCHIVE'
        ? await props.client.archiveSemester(
            command.semesterId,
            command.idempotencyKey,
            controller.signal,
          )
        : await props.client.restoreSemester(
            command.semesterId,
            command.idempotencyKey,
            controller.signal,
          );
    if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return;
    await processArchiveOperation(command, operation, scope, controller);
  } catch (cause) {
    if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return;
    if (cause instanceof StaleSessionGenerationError) {
      resetArchiveScope();
      return;
    }
    if (cause instanceof AdminSemesterApiError && cause.archiveOperation) {
      if (
        cause.archiveOperation.semesterId === command.semesterId &&
        cause.archiveOperation.action === command.action
      ) {
        await processArchiveOperation(command, cause.archiveOperation, scope, controller);
        return;
      }
    }
    if (cause instanceof AdminSemesterApiError && cause.response.status < 500) {
      archiveCommand.value = null;
      archiveMonitorController = null;
      showError(
        cause,
        command.action === 'ARCHIVE'
          ? 'Семестр не удалось архивировать.'
          : 'Семестр не удалось восстановить.',
      );
      return;
    }
    archiveCommand.value = { ...command, phase: 'UNCERTAIN' };
    archiveMonitorController = null;
    error.value =
      'Ответ сервера не подтверждён. Повтори тот же запрос: он продолжит прежнюю операцию.';
    notice.value = null;
    if (
      cause instanceof AdminSemesterApiError &&
      (cause.response.status === 401 || cause.response.status === 403)
    ) {
      emit('ownerError', cause);
    }
  }
}

async function processArchiveOperation(
  command: NonNullable<typeof archiveCommand.value>,
  operation: AdminSemesterArchiveOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return;
  if (operation.semesterId !== command.semesterId || operation.action !== command.action) {
    archiveCommand.value = null;
    error.value = 'Сервер вернул операцию для другого семестра или действия. Обнови список.';
    return;
  }
  archiveOperations.value = { ...archiveOperations.value, [operation.semesterId]: operation };
  archiveStatusErrors.value = { ...archiveStatusErrors.value, [operation.semesterId]: '' };
  if (operation.operationState === 'PENDING') {
    const pendingCommand = {
      ...command,
      operationId: operation.operationId,
      phase: 'PENDING' as const,
    };
    archiveCommand.value = pendingCommand;
    notice.value = operationLabel(operation);
    try {
      await refreshCurrentArchiveState(operation.semesterId, scope, controller);
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return;
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope();
        return;
      }
      archiveStatusErrors.value = {
        ...archiveStatusErrors.value,
        [operation.semesterId]: errorMessage(
          cause,
          'Текущее состояние семестра не удалось загрузить.',
        ),
      };
      if (notifyOwnerOfAuthorizationError(cause)) {
        error.value = errorMessage(cause, 'Текущее состояние семестра не удалось загрузить.');
        notice.value = null;
        archiveMonitorController = null;
        return;
      }
    }
    if (!isCurrentArchiveScope(scope, controller)) return;
    await pollArchiveOperation(pendingCommand, operation, scope, controller);
    return;
  }
  if (operation.operationState === 'COMPLETED') {
    archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'SETTLING' };
    await settleCompletedOperation(archiveCommand.value, operation, scope, controller);
    return;
  }

  await refreshCurrentArchiveState(operation.semesterId, scope, controller);
  if (!isCurrentArchiveScope(scope, controller)) return;
  archiveCommand.value = null;
  error.value =
    operation.blockingReason ??
    (operation.retryable
      ? 'Операция завершилась с ошибкой. Можно подтвердить новую попытку.'
      : 'Операция завершилась с ошибкой; повтор недоступен.');
  notice.value = null;
}

async function pollArchiveOperation(
  command: NonNullable<typeof archiveCommand.value>,
  initial: AdminSemesterArchiveOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  let operation = initial;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    if (!(await waitForArchivePoll(controller.signal, 1_200))) return;
    if (
      !isCurrentArchiveScope(scope, controller) ||
      archiveCommand.value?.operationId !== operation.operationId
    )
      return;
    try {
      operation = await props.client.getArchiveOperation(operation.operationId, controller.signal);
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return;
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope();
        return;
      }
      archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'PENDING' };
      error.value = 'Не удалось проверить ход операции. Продолжи проверку сохранённой операции.';
      notice.value = null;
      archiveMonitorController = null;
      notifyOwnerOfAuthorizationError(cause);
      return;
    }
    if (!isCurrentArchiveScope(scope, controller)) return;
    archiveOperations.value = { ...archiveOperations.value, [operation.semesterId]: operation };
    if (operation.operationState !== 'PENDING') {
      await processArchiveOperation(command, operation, scope, controller);
      return;
    }
  }
  if (isCurrentArchiveScope(scope, controller)) {
    archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'PENDING' };
    notice.value =
      'Операция всё ещё выполняется. Автоматическая проверка приостановлена; её можно продолжить вручную.';
    archiveMonitorController = null;
  }
}

async function settleCompletedOperation(
  command: NonNullable<typeof archiveCommand.value>,
  operation: AdminSemesterArchiveOperation,
  scope: number,
  controller: AbortController,
): Promise<void> {
  archiveSettlingOperationIds.value = {
    ...archiveSettlingOperationIds.value,
    [command.semesterId]: operation.operationId,
  };
  archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'SETTLING' };
  for (let attempt = 0; attempt < 10; attempt += 1) {
    if (attempt > 0 && !(await waitForArchivePoll(controller.signal, 1_200))) return;
    if (!isCurrentArchiveScope(scope, controller)) return;
    try {
      const current = await refreshCurrentArchiveState(command.semesterId, scope, controller);
      if (!isCurrentArchiveScope(scope, controller)) return;
      if (current && operationHasExpectedCurrentState(current, command.action)) {
        await refresh(false);
        if (!isCurrentArchiveScope(scope, controller)) return;
        const listed = semesters.value.find((item) => item.id === command.semesterId);
        if (listed && operationHasExpectedCurrentState(listed, command.action)) {
          clearArchiveSettlement(command.semesterId, operation.operationId);
          archiveCommand.value = null;
          archiveMonitorController = null;
          error.value = null;
          notice.value =
            command.action === 'ARCHIVE'
              ? `Семестр «${listed.name}» архивирован и стал неактивным. Данные сохранены.`
              : `Семестр «${listed.name}» восстановлен и остался неактивным. Чтобы сделать его текущим, активируй его отдельно.`;
          return;
        }
      }
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return;
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope();
        return;
      }
      archiveStatusErrors.value = {
        ...archiveStatusErrors.value,
        [command.semesterId]:
          'Операция завершена, но текущее состояние ещё не подтверждено. Продолжи проверку.',
      };
      if (notifyOwnerOfAuthorizationError(cause)) {
        error.value = errorMessage(
          cause,
          'Операция завершена, но текущее состояние ещё не подтверждено.',
        );
        notice.value = null;
        archiveMonitorController = null;
        return;
      }
    }
  }
  if (isCurrentArchiveScope(scope, controller)) {
    archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'SETTLING' };
    notice.value =
      'Операция завершена. Текущее состояние семестра ещё обновляется; продолжи проверку перед следующими действиями.';
    error.value = null;
    archiveMonitorController = null;
  }
}

async function refreshCurrentArchiveState(
  semesterId: number,
  scope: number,
  signalOwner: AbortController,
): Promise<AdminSemester | null> {
  const status = await props.client.getArchiveStatus(semesterId, signalOwner.signal);
  if (!isCurrentArchiveScope(scope, signalOwner)) return null;
  if (status.semesterId !== semesterId) throw new Error('Сервер вернул статус другого семестра.');
  if (status.operation)
    archiveOperations.value = { ...archiveOperations.value, [semesterId]: status.operation };
  mergeArchiveAuthority(status);
  const current = await props.client.getSemester(semesterId, signalOwner.signal);
  if (!isCurrentArchiveScope(scope, signalOwner)) return null;
  if (current.id !== semesterId) throw new Error('Сервер вернул другой семестр.');
  applySemesterSnapshot(current);
  return semesters.value.find((semester) => semester.id === semesterId) ?? current;
}

function mergeArchiveAuthority(status: AdminSemesterArchiveStatus): void {
  const current = semesters.value.find((semester) => semester.id === status.semesterId);
  if (!current || status.stateVersion < current.stateVersion) return;
  const authorityIsNewer = status.stateVersion > current.stateVersion;
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
      : current.isWriteBlocked ||
        status.archived ||
        status.transition !== 'NONE' ||
        status.releasePending,
  };
  applySemesterSnapshot(next);
}

function applySemesterSnapshot(next: AdminSemester): void {
  const current = semesters.value.find((semester) => semester.id === next.id);
  if (current && next.stateVersion < current.stateVersion) return;
  semesters.value = current
    ? semesters.value.map((semester) => (semester.id === next.id ? next : semester))
    : [...semesters.value, next];
}

async function hydrateArchiveStatuses(
  items: readonly AdminSemester[],
  scope: number,
): Promise<void> {
  for (const semester of items) {
    if (semester.transition === 'NONE' && !semester.releasePending) continue;
    if (!isCurrentArchiveScope(scope) || archiveCommand.value !== null) return;
    await readArchiveStatus(semester.id, scope);
  }
}

async function readArchiveStatus(
  semesterId: number,
  scope: number,
): Promise<AdminSemesterArchiveStatus | null> {
  const controller = new AbortController();
  archiveStatusControllers.add(controller);
  archiveStatusLoadingIds.value = new Set(archiveStatusLoadingIds.value).add(semesterId);
  try {
    const status = await props.client.getArchiveStatus(semesterId, controller.signal);
    if (!isCurrentArchiveScope(scope, controller)) return null;
    if (status.semesterId !== semesterId) throw new Error('Сервер вернул статус другого семестра.');
    if (status.operation)
      archiveOperations.value = { ...archiveOperations.value, [semesterId]: status.operation };
    mergeArchiveAuthority(status);
    const current = await props.client.getSemester(semesterId, controller.signal);
    if (isCurrentArchiveScope(scope, controller)) applySemesterSnapshot(current);
    archiveStatusErrors.value = { ...archiveStatusErrors.value, [semesterId]: '' };
    return status;
  } catch (cause) {
    if (
      isCurrentArchiveScope(scope, controller) &&
      !(cause instanceof StaleSessionGenerationError) &&
      !isAbortError(cause)
    ) {
      const message = errorMessage(cause, 'Статус операции не удалось загрузить.');
      archiveStatusErrors.value = { ...archiveStatusErrors.value, [semesterId]: message };
      error.value = message;
      if (
        cause instanceof AdminSemesterApiError &&
        (cause.response.status === 401 || cause.response.status === 403)
      ) {
        emit('ownerError', cause);
      }
    }
    return null;
  } finally {
    archiveStatusControllers.delete(controller);
    const next = new Set(archiveStatusLoadingIds.value);
    next.delete(semesterId);
    archiveStatusLoadingIds.value = next;
  }
}

async function resumeArchiveStatus(semester: AdminSemester): Promise<void> {
  if (mutationBusy.value || archiveStatusLoadingIds.value.has(semester.id)) return;
  const commandAtStart = archiveCommand.value;
  const scope = archiveScopeRevision;
  const status = await readArchiveStatus(semester.id, scope);
  if (
    !status ||
    !isCurrentArchiveScope(scope) ||
    archiveCommand.value !== commandAtStart ||
    status.operation === null
  )
    return;
  const operation = status.operation;
  if (operation.operationState === 'PENDING') {
    const command = {
      semesterId: operation.semesterId,
      action: operation.action,
      idempotencyKey: null,
      operationId: operation.operationId,
      phase: 'PENDING' as const,
    };
    archiveCommand.value = command;
    const controller = beginArchiveMonitor();
    await pollArchiveOperation(command, operation, scope, controller);
  } else if (operation.operationState === 'COMPLETED') {
    const command = {
      semesterId: operation.semesterId,
      action: operation.action,
      idempotencyKey: null,
      operationId: operation.operationId,
      phase: 'SETTLING' as const,
    };
    archiveCommand.value = command;
    const controller = beginArchiveMonitor();
    await settleCompletedOperation(command, operation, scope, controller);
  } else {
    error.value =
      operation.blockingReason ??
      (operation.retryable
        ? 'Операция завершилась с ошибкой. Можно подтвердить новую попытку.'
        : 'Операция завершилась с ошибкой; повтор недоступен.');
    notice.value = null;
  }
}

function retryUncertainArchiveCommand(semesterId: number): void {
  const command = archiveCommand.value;
  if (
    !command ||
    command.semesterId !== semesterId ||
    command.phase !== 'UNCERTAIN' ||
    command.idempotencyKey === null
  )
    return;
  error.value = null;
  notice.value = 'Повторяем запрос по сохранённому предпросмотру…';
  void submitArchiveCommand(command, archiveScopeRevision);
}

async function continueArchiveCheck(semester: AdminSemester): Promise<void> {
  const command = archiveCommand.value;
  if (command?.semesterId === semester.id) {
    if (command.operationId === null) return;
    const controller = beginArchiveMonitor();
    const scope = archiveScopeRevision;
    error.value = null;
    notice.value = 'Проверяем состояние операции…';
    try {
      const operation = await props.client.getArchiveOperation(
        command.operationId,
        controller.signal,
      );
      if (!isCurrentArchiveScope(scope, controller) || archiveCommand.value !== command) return;
      if (operation.semesterId !== semester.id || operation.action !== command.action) {
        throw new Error('Сервер вернул операцию для другого семестра или действия.');
      }
      await processArchiveOperation(command, operation, scope, controller);
    } catch (cause) {
      if (!isCurrentArchiveScope(scope, controller)) return;
      if (cause instanceof StaleSessionGenerationError) {
        resetArchiveScope();
        return;
      }
      error.value = errorMessage(cause, 'Состояние операции не удалось проверить.');
      notice.value = null;
      archiveMonitorController = null;
      notifyOwnerOfAuthorizationError(cause);
    }
    return;
  }
  await resumeArchiveStatus(semester);
}

function beginArchiveMonitor(): AbortController {
  archiveMonitorController?.abort();
  const controller = new AbortController();
  archiveMonitorController = controller;
  return controller;
}

function resetArchiveScope(): void {
  archiveScopeRevision += 1;
  archiveConfirmationRevision += 1;
  archiveMonitorController?.abort();
  archiveMonitorController = null;
  for (const controller of archiveStatusControllers) controller.abort();
  archiveStatusControllers.clear();
  archiveCommand.value = null;
  archiveConfirmation.value = null;
  archiveConfirmationChecking.value = false;
  archiveOperations.value = {};
  archiveSettlingOperationIds.value = {};
  archiveStatusErrors.value = {};
  archiveStatusLoadingIds.value = new Set();
  error.value = null;
  notice.value = null;
}

function resetDeletionScope(preserveStoredOperation = false): void {
  deletionScopeRevision += 1;
  deletionPreviewRevision += 1;
  deletionPreviewController?.abort();
  deletionPreviewController = null;
  deletionMonitorController?.abort();
  deletionMonitorController = null;
  if (!preserveStoredOperation) deletionStorageKey = null;
  deletionPreviewLoading.value = false;
  deletionPreview.value = null;
  deletionPassword.value = '';
  deletionPasswordInvalid.value = false;
  deletionError.value = null;
  deletionCommand.value = null;
  deletionOperation.value = null;
}

function reconcileDeletionStorageScope(): void {
  const scope = currentDeletionStorageScope();
  if (scope === null) return;
  const key = storageKeyForDeletionScope(scope);
  deletionStorageKey = key;
  restoreDeletionProgress();
}

function currentDeletionStorageScope(): string | null {
  if (!canDelete.value) return null;
  try {
    return props.client.getDeletionStorageScope();
  } catch {
    return null;
  }
}

function storageKeyForDeletionScope(scope: string): string {
  return `rct:admin:semester-delete:v1:${encodeURIComponent(scope)}`;
}

function currentDeletionStorageKey(): string | null {
  const scope = currentDeletionStorageScope();
  if (scope === null || typeof window === 'undefined') return null;
  return storageKeyForDeletionScope(scope);
}

function persistDeletionCommand(command: NonNullable<typeof deletionCommand.value>): boolean {
  const scope = currentDeletionStorageScope();
  if (scope === null || typeof window === 'undefined') return false;
  const key = storageKeyForDeletionScope(scope);
  const state = {
    version: 1,
    scope,
    semesterId: command.semesterId,
    preview: command.preview,
    idempotencyKey: command.idempotencyKey,
    operationId: command.operationId,
  };
  try {
    window.sessionStorage.setItem(key, JSON.stringify(state));
    deletionStorageKey = key;
    return true;
  } catch {
    return false;
  }
}

function removePersistedDeletionCommand(): void {
  const key = deletionStorageKey;
  if (!key || typeof window === 'undefined') return;
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    // The in-memory command is still cleared when browser storage is unavailable.
  }
  deletionStorageKey = null;
}

function restoreDeletionProgress(): void {
  if (!canDelete.value) return;
  const scope = currentDeletionStorageScope();
  if (scope === null || typeof window === 'undefined') return;
  const key = storageKeyForDeletionScope(scope);
  deletionStorageKey = key;
  let stored: unknown;
  try {
    const raw = window.sessionStorage.getItem(key);
    if (raw === null) return;
    stored = JSON.parse(raw) as unknown;
  } catch {
    deletionError.value =
      'Сохранённый ход удаления не удалось прочитать. Проверь статус операции вручную.';
    return;
  }
  const record = isRecord(stored) ? stored : null;
  const preview = readStoredDeletionPreview(record?.preview);
  const semesterId = record?.semesterId;
  const idempotencyKey = record?.idempotencyKey;
  const operationId = record?.operationId;
  if (
    record?.version !== 1 ||
    record.scope !== scope ||
    !preview ||
    typeof semesterId !== 'number' ||
    semesterId !== preview.semesterId ||
    typeof idempotencyKey !== 'string' ||
    !isUuid(idempotencyKey) ||
    (operationId !== null && (typeof operationId !== 'string' || !isUuid(operationId)))
  ) {
    removePersistedDeletionCommand();
    return;
  }
  const command = {
    semesterId,
    preview,
    idempotencyKey,
    operationId: operationId as string | null,
    phase: operationId === null ? ('UNCERTAIN' as const) : ('PENDING' as const),
    operation: null,
  };
  deletionCommand.value = command;
  deletionOperation.value = null;
  if (operationId === null) {
    deletionPreview.value = preview;
    deletionError.value =
      'Ранее отправленный запрос не получил подтверждения. Введи пароль, чтобы безопасно повторить тот же запрос.';
    return;
  }
  void resumeDeletionProgress(command);
}

async function resumeDeletionProgress(
  command: NonNullable<typeof deletionCommand.value>,
): Promise<void> {
  const scope = deletionScopeRevision;
  const controller = beginDeletionMonitor();
  try {
    const operation = await props.client.getSemesterDeletionOperation(
      command.operationId!,
      controller.signal,
    );
    if (
      !isCurrentDeletionScope(scope, controller) ||
      deletionCommand.value?.operationId !== command.operationId
    )
      return;
    await applyDeletionOperation(command, operation, scope, controller);
    if (
      operation.phase === 'PREPARING' ||
      operation.phase === 'RELEASING' ||
      operation.phase === 'DELETING'
    ) {
      await pollDeletionOperation(command, operation, scope, controller);
    }
  } catch (cause) {
    if (!isCurrentDeletionScope(scope, controller)) return;
    if (cause instanceof StaleSessionGenerationError) {
      resetDeletionScope(true);
      return;
    }
    if (rejectDeletionAuthorization(cause)) return;
    deletionError.value =
      'Сохранённая операция ещё не загрузилась. Продолжи проверку сохранённой операции.';
    deletionMonitorController = null;
  }
}

function readStoredDeletionPreview(value: unknown): AdminSemesterDeletionPreview | null {
  if (!isRecord(value)) return null;
  const counts = value.counts;
  if (!isRecord(counts)) return null;
  const countFields: Array<keyof AdminSemesterDeletionCounts> = [
    'scheduleTemplates',
    'oneOffLessons',
    'lessons',
    'assignments',
    'homeworks',
    'attendanceMarks',
    'studentRequests',
  ];
  if (
    countFields.some(
      (field) => !Number.isSafeInteger(counts[field]) || (counts[field] as number) < 0,
    )
  )
    return null;
  if (
    !Number.isSafeInteger(value.semesterId) ||
    (value.semesterId as number) <= 0 ||
    typeof value.semesterName !== 'string' ||
    value.semesterName.trim() === '' ||
    (value.priorState !== 'ACTIVE' &&
      value.priorState !== 'INACTIVE' &&
      value.priorState !== 'ARCHIVED') ||
    !Number.isSafeInteger(value.stateVersion) ||
    (value.stateVersion as number) < 0 ||
    typeof value.previewDigest !== 'string' ||
    value.previewDigest.trim() === ''
  )
    return null;
  return {
    semesterId: value.semesterId as number,
    semesterName: value.semesterName,
    priorState: value.priorState,
    stateVersion: value.stateVersion as number,
    previewDigest: value.previewDigest,
    counts: {
      scheduleTemplates: counts.scheduleTemplates as number,
      oneOffLessons: counts.oneOffLessons as number,
      lessons: counts.lessons as number,
      assignments: counts.assignments as number,
      homeworks: counts.homeworks as number,
      attendanceMarks: counts.attendanceMarks as number,
      studentRequests: counts.studentRequests as number,
    },
  };
}

function isUuid(value: string): boolean {
  return /^[\da-f]{8}-[\da-f]{4}-[1-8][\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/i.test(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function clearArchiveSettlement(semesterId: number, operationId: string): void {
  if (archiveSettlingOperationIds.value[semesterId] !== operationId) return;
  const next = { ...archiveSettlingOperationIds.value };
  delete next[semesterId];
  archiveSettlingOperationIds.value = next;
}

function isCurrentArchiveScope(scope: number, controller?: AbortController): boolean {
  return (
    canManage.value && !disposed && scope === archiveScopeRevision && !controller?.signal.aborted
  );
}

function isCurrentDeletionScope(scope: number, controller?: AbortController): boolean {
  return (
    canDelete.value && !disposed && scope === deletionScopeRevision && !controller?.signal.aborted
  );
}

function createIdempotencyKey(): string {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    throw new Error(
      'Браузер не поддерживает безопасное подтверждение. Обнови страницу в актуальном браузере.',
    );
  }
  return globalThis.crypto.randomUUID();
}

function waitForArchivePoll(signal: AbortSignal, durationMs: number): Promise<boolean> {
  if (signal.aborted) return Promise.resolve(false);
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve(true);
    }, durationMs);
    const onAbort = (): void => {
      clearTimeout(timer);
      signal.removeEventListener('abort', onAbort);
      resolve(false);
    };
    signal.addEventListener('abort', onAbort, { once: true });
  });
}

function operationHasExpectedCurrentState(
  semester: AdminSemester,
  action: AdminSemesterArchiveAction,
): boolean {
  if (semester.transition !== 'NONE' || semester.releasePending) return false;
  if (action === 'ARCHIVE') return semester.archived && !semester.active;
  return !semester.archived && !semester.active && !semester.isWriteBlocked;
}

function operationLabel(operation: AdminSemesterArchiveOperation): string {
  return operation.action === 'ARCHIVE'
    ? 'Архивация выполняется на сервере…'
    : 'Восстановление выполняется на сервере…';
}

function deletionPhaseLabel(phase: AdminSemesterDeletionPhase): string {
  const labels: Record<AdminSemesterDeletionPhase, string> = {
    PREPARING: 'Подготавливаем удаление и проверяем связанные данные…',
    RELEASING: 'Освобождаем связанные данные…',
    DELETING: 'Удаляем данные семестра…',
    COMPLETED: 'Удаление завершено.',
    CANCELLED: 'Удаление отменено; данные сохранены.',
  };
  return labels[phase];
}

function deletionPriorStateLabel(state: AdminSemesterDeletionPreview['priorState']): string {
  if (state === 'ACTIVE') return 'Текущий семестр';
  if (state === 'ARCHIVED') return 'В архиве';
  return 'Неактивный';
}

function deletionCountItems(
  counts: AdminSemesterDeletionCounts,
): readonly { label: string; value: number }[] {
  return [
    { label: 'Шаблоны расписания', value: counts.scheduleTemplates },
    { label: 'Разовые занятия', value: counts.oneOffLessons },
    { label: 'Занятия', value: counts.lessons },
    { label: 'Задания', value: counts.assignments },
    { label: 'Домашние работы', value: counts.homeworks },
    { label: 'Отметки посещаемости', value: counts.attendanceMarks },
    { label: 'Заявки студентов', value: counts.studentRequests },
  ];
}

function operationStateLabel(value: string): string {
  const labels: Record<string, string> = {
    NOT_STARTED: 'Ожидает',
    PENDING: 'Выполняется',
    READY: 'Подготовлено',
    PREPARED_RESTORE: 'Готово к восстановлению',
    RELEASE_PENDING: 'Ожидает завершения',
    RELEASED: 'Завершено',
  };
  return labels[value] ?? 'Состояние обновляется';
}

function participantStateLabel(value: string): string {
  return operationStateLabel(value);
}

function editBlockReason(semester: AdminSemester): string {
  if (semester.transition !== 'NONE') return 'Дождись завершения архивации или восстановления.';
  if (semester.releasePending) return 'Завершается восстановление.';
  if (hasPendingArchiveOperation(semester.id)) return 'Дождись завершения операции с архивом.';
  if (!semester.archived && semester.isWriteBlocked)
    return 'Сервер временно запретил изменение семестра.';
  return 'Семестр сейчас нельзя изменить.';
}

function archiveBlockReason(semester: AdminSemester, action: AdminSemesterArchiveAction): string {
  if (action === 'RESTORE' && semester.archived && semesterExpired(semester))
    return 'Срок семестра истёк. Сначала продли даты архивного периода.';
  if (semester.transition !== 'NONE') return 'Дождись завершения текущей операции.';
  if (semester.releasePending) return 'Завершается восстановление.';
  if (hasPendingArchiveOperation(semester.id)) return 'Дождись завершения текущей операции.';
  return action === 'ARCHIVE' ? 'Семестр уже находится в архиве.' : 'Сначала архивируй семестр.';
}

function archiveOperationFor(semesterId: number): AdminSemesterArchiveOperation | null {
  return archiveOperations.value[semesterId] ?? null;
}

function archiveCommandFor(semesterId: number): NonNullable<typeof archiveCommand.value> | null {
  return archiveCommand.value?.semesterId === semesterId ? archiveCommand.value : null;
}

function needsArchiveStatus(semester: AdminSemester): boolean {
  return (
    semester.transition !== 'NONE' ||
    semester.releasePending ||
    archiveCommandFor(semester.id) !== null ||
    archiveSettlingOperationIds.value[semester.id] !== undefined ||
    archiveOperationFor(semester.id)?.operationState === 'PENDING'
  );
}

function canStartNewArchiveAttempt(
  semester: AdminSemester,
  action: AdminSemesterArchiveAction,
): boolean {
  const operation = archiveOperationFor(semester.id);
  return (
    operation?.action === action && operation.operationState === 'ERROR' && operation.retryable
  );
}

function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof AdminSemesterApiError
    ? (cause.problem?.detail ?? cause.message)
    : cause instanceof Error
      ? cause.message
      : fallback;
}

function notifyOwnerOfAuthorizationError(cause: unknown): boolean {
  if (
    !(cause instanceof AdminSemesterApiError) ||
    (cause.response.status !== 401 && cause.response.status !== 403)
  )
    return false;
  emit('ownerError', cause);
  return true;
}

function mergeSemesterList(
  current: readonly AdminSemester[],
  next: readonly AdminSemester[],
): readonly AdminSemester[] {
  const currentById = new Map(current.map((semester) => [semester.id, semester]));
  return next.map((semester) => {
    const previous = currentById.get(semester.id);
    return previous && previous.stateVersion > semester.stateVersion ? previous : semester;
  });
}

function showError(cause: unknown, fallback: string): void {
  error.value =
    cause instanceof AdminSemesterApiError
      ? (cause.problem?.detail ?? cause.message)
      : cause instanceof Error
        ? cause.message
        : fallback;
  notifyOwnerOfAuthorizationError(cause);
}

function formatDate(value: string): string {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      }).format(date);
}

function formatPeriod(semester: AdminSemester): string {
  return `${formatDate(semester.dateFrom)} — ${formatDate(semester.dateTo)}`;
}

function statusLabel(semester: AdminSemester): string {
  if (semester.archived) return 'Архивный';
  if (semester.transition === 'ARCHIVING') return 'Архивация выполняется';
  if (semester.transition === 'RESTORING') return 'Восстановление выполняется';
  if (semester.releasePending) return 'Завершается восстановление';
  return semester.active
    ? 'Активный'
    : semester.activationScheduled
      ? 'Активация запланирована'
      : semesterFuture(semester)
        ? 'Будущий'
        : 'Неактивный';
}

function generatedSemesterName(type: AdminSemesterType, academicYearValue: number): string {
  const season = type === 'AUTUMN' ? 'Осенний' : 'Весенний';
  return `${season} ${academicYearValue}/${academicYearValue + 1}`;
}

function setAcademicYear(event: Event): void {
  academicYearTouched.value = true;
  academicYear.value = (event.target as HTMLInputElement).value;
}

watch(
  () => [
    props.authority,
    props.authority?.version,
    props.authority?.mode,
    props.authority?.assigned,
    props.authority?.permissions.join('|'),
  ],
  () => {
    authorityRevision += 1;
    overlapWarning.value = null;
    if (
      !adminHasPermission(props.authority, 'SEMESTERS_VIEW') ||
      !adminHasPermission(props.authority, 'SEMESTERS_MANAGE') ||
      !adminHasPermission(props.authority, 'DATA_DELETE')
    )
      removePersistedDeletionCommand();
    deletionAccessDenied.value = false;
    resetArchiveScope();
    resetDeletionScope(true);
    listRequestRevision += 1;
    listAbortController?.abort();
    listAbortController = null;
    cancelEditLoad();
    formVisible.value = false;
    calendarField.value = null;
    activationConfirmation.value = null;
    archiveConfirmation.value = null;
    editingSemesterId.value = null;
    semesters.value = [];
    loading.value = false;
    saving.value = false;
    pendingActivationId.value = null;
    error.value = null;
    notice.value = null;
    if (canView.value) void refresh();
  },
  { flush: 'sync' },
);

onBeforeUnmount(() => {
  disposed = true;
  listRequestRevision += 1;
  listAbortController?.abort();
  listAbortController = null;
  cancelEditLoad();
  resetArchiveScope();
  resetDeletionScope(false);
});
</script>

<template>
  <main
    class="admin-semester-screen"
    :data-theme="theme"
    :data-admin-semester-view="
      formVisible
        ? formMode
        : deletionPreview
          ? 'delete-confirm'
          : deletionCommand
            ? 'delete-progress'
            : activationConfirmation
              ? 'activate-confirm'
              : archiveConfirmation
                ? 'archive-confirm'
                : 'list'
    "
    aria-labelledby="admin-semester-title"
  >
    <header class="admin-semester-screen__header">
      <button
        class="admin-semester-back"
        type="button"
        aria-label="Назад"
        @click="back"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="admin-semester-title">
        {{
          formVisible
            ? formMode === 'edit'
              ? 'Редактировать семестр'
              : 'Добавить семестр'
            : activationConfirmation
              ? 'Сделать текущим'
              : archiveConfirmation
                ? archiveConfirmation.action === 'ARCHIVE'
                  ? 'Архивировать семестр'
                  : 'Восстановить семестр'
                : deletionPreview || deletionCommand
                  ? 'Удаление семестра'
                  : 'Семестры'
        }}
      </h1>
    </header>
    <button
      v-if="!taskActive && canManage"
      class="admin-semester-screen__new"
      type="button"
      :disabled="mutationBusy"
      @click="openCreateForm"
    >
      Добавить семестр
    </button>
    <AdminNotice
      v-if="!canView"
      tone="warning"
      title="Нет доступа"
    >
      Просмотр семестров недоступен с текущими правами.
    </AdminNotice>
    <AdminNotice
      v-if="error"
      tone="danger"
      title="Не удалось выполнить действие"
      class="admin-semester-state--error"
    >
      {{ error }}
    </AdminNotice>
    <AdminNotice
      v-if="overlapWarning"
      tone="warning"
      title="Даты пересекаются"
      class="admin-semester-overlap-warning"
    >
      {{ overlapWarning }}
    </AdminNotice>
    <AdminNotice
      v-if="notice"
      :tone="archiveCommand ? 'warning' : 'success'"
      :title="archiveCommand ? 'Операция выполняется' : 'Готово'"
      :class="archiveCommand ? 'admin-semester-state--warning' : 'admin-semester-state--success'"
    >
      {{ notice }}
    </AdminNotice>
    <section
      v-if="activationConfirmation"
      class="admin-semester-card admin-semester-confirmation"
      aria-labelledby="admin-semester-activation-title"
    >
      <h2 id="admin-semester-activation-title">
        {{ activationConfirmation.name }}
      </h2>
      <p v-if="semesterFuture(activationConfirmation)">
        Активация будет запланирована на {{ formatDate(activationConfirmation.dateFrom) }}. Текущий
        семестр останется активным до этой даты.
      </p>
      <p v-else>
        Этот семестр станет текущим.
        {{
          activeSemesters.length
            ? 'Предыдущий активный семестр станет неактивным. Его данные сохранятся.'
            : 'Сейчас активный семестр не выбран.'
        }}
      </p>
      <button
        class="admin-semester-action"
        type="button"
        :disabled="saving || mutationBusy"
        @click="activateSemester(activationConfirmation)"
      >
        {{ saving ? 'Активируем…' : 'Подтвердить активацию' }}
      </button>
      <button
        class="admin-semester-action admin-semester-action--secondary"
        type="button"
        :disabled="saving"
        @click="activationConfirmation = null"
      >
        Отмена
      </button>
    </section>
    <form
      v-if="formVisible"
      class="admin-semester-form"
      aria-labelledby="admin-semester-form-title"
      @submit.prevent="saveSemester"
    >
      <h2
        id="admin-semester-form-title"
        class="admin-semester-visually-hidden"
      >
        Параметры семестра
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
        :disabled="
          mutationBusy ||
          formLoading ||
          (editingSnapshot?.archived && editingSnapshot.semesterType !== null)
        "
        :aria-describedby="formErrors.type ? 'admin-semester-type-error' : undefined"
        :aria-invalid="!!formErrors.type || undefined"
      >
        <legend>Тип семестра</legend>
        <div class="admin-semester-form__type-options">
          <label class="admin-semester-form__type-option">
            <input
              v-model="semesterType"
              name="semester-type"
              required
              type="radio"
              value="AUTUMN"
            />
            <span>Осенний</span>
          </label>
          <label class="admin-semester-form__type-option">
            <input
              v-model="semesterType"
              name="semester-type"
              required
              type="radio"
              value="SPRING"
            />
            <span>Весенний</span>
          </label>
        </div>
        <p
          v-if="!formLoading && formErrors.type"
          id="admin-semester-type-error"
          class="admin-semester-field-error"
        >
          {{ formErrors.type }}
        </p>
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
          :readonly="editingSnapshot?.archived && editingSnapshot.academicYear !== null"
          :aria-invalid="!!formErrors.year || undefined"
          :aria-describedby="formErrors.year ? 'admin-semester-year-error' : undefined"
          @input="setAcademicYear"
        />
        <span
          v-if="!formLoading && formErrors.year"
          id="admin-semester-year-error"
          class="admin-semester-field-error"
          >{{ formErrors.year }}</span
        >
      </label>
      <p
        v-if="editingSnapshot?.archived"
        class="admin-semester-form__hint"
      >
        Расширь диапазон, сохранив прежние даты внутри него. Учебные данные останутся в архиве.
      </p>
      <div
        class="admin-semester-form__dates"
        :class="{ 'admin-semester-form__dates--calendar': calendarField }"
      >
        <div
          class="admin-semester-date"
          :data-selected="calendarField === 'from'"
        >
          <button
            type="button"
            aria-labelledby="admin-semester-from-label admin-semester-from-value"
            :aria-invalid="!!formErrors.from || undefined"
            :aria-describedby="formErrors.from ? 'admin-semester-from-error' : undefined"
            :aria-expanded="calendarField === 'from'"
            :disabled="mutationBusy || formLoading"
            @click="calendarField = calendarField === 'from' ? null : 'from'"
          >
            <span
              id="admin-semester-from-label"
              class="admin-semester-date__label"
              >Начало</span
            >
            <span id="admin-semester-from-value">{{
              dateFrom ? formatDate(dateFrom) : 'Выбери дату'
            }}</span>
          </button>
          <p
            v-if="!formLoading && formErrors.from"
            id="admin-semester-from-error"
            class="admin-semester-field-error"
          >
            {{ formErrors.from }}
          </p>
        </div>
        <div
          class="admin-semester-date"
          :data-selected="calendarField === 'to'"
        >
          <button
            type="button"
            aria-labelledby="admin-semester-to-label admin-semester-to-value"
            :aria-invalid="!!formErrors.to || !!dateError || undefined"
            :aria-describedby="
              formErrors.to
                ? 'admin-semester-to-error'
                : dateError
                  ? 'admin-semester-date-error'
                  : undefined
            "
            :aria-expanded="calendarField === 'to'"
            :disabled="mutationBusy || formLoading"
            @click="calendarField = calendarField === 'to' ? null : 'to'"
          >
            <span
              id="admin-semester-to-label"
              class="admin-semester-date__label"
              >Окончание</span
            >
            <span id="admin-semester-to-value">{{
              dateTo ? formatDate(dateTo) : 'Выбери дату'
            }}</span>
          </button>
          <p
            v-if="!formLoading && formErrors.to"
            id="admin-semester-to-error"
            class="admin-semester-field-error"
          >
            {{ formErrors.to }}
          </p>
        </div>
      </div>
      <p
        v-if="dateError"
        id="admin-semester-date-error"
        class="admin-semester-field-error"
        role="alert"
      >
        {{ dateError }}
      </p>
      <SemesterCalendar
        v-if="calendarField"
        :model-value="calendarField === 'from' ? dateFrom : dateTo"
        :label="calendarField === 'from' ? 'Начало' : 'Окончание'"
        :disabled="mutationBusy || formLoading"
        @update:model-value="chooseCalendarDate"
      />

      <p
        v-if="formMode === 'edit' && semesterType === null"
        class="admin-semester-form__hint"
      >
        У старой записи тип не указан. Выбери его явно; название и сохранённые данные не
        переопределяются автоматически.
      </p>
      <button
        class="admin-semester-action"
        type="submit"
        :disabled="mutationBusy || formLoading || !formValid"
        :aria-busy="saving"
      >
        {{ saving ? 'Сохраняем…' : formMode === 'edit' ? 'Сохранить' : 'Добавить семестр' }}
      </button>
    </form>

    <section
      v-if="deletionCommand && !deletionPreview"
      class="admin-semester-card admin-semester-deletion-progress"
      role="region"
      aria-labelledby="admin-semester-deletion-progress-title"
      :aria-busy="deletionCommand.phase === 'SUBMITTING' || deletionCommand.phase === 'PENDING'"
    >
      <h2 id="admin-semester-deletion-progress-title"> Удаление семестра </h2>
      <h3>{{ deletionCommand.preview.semesterName }}</h3>
      <p
        v-if="visibleDeletionOperation"
        role="status"
      >
        {{ deletionPhaseLabel(visibleDeletionOperation.phase) }}
      </p>
      <p
        v-else-if="deletionCommand.phase === 'SUBMITTING'"
        role="status"
      >
        Отправляем подтверждение и ждём ответа…
      </p>
      <p
        v-else-if="deletionCommand.phase === 'UNCERTAIN'"
        role="status"
      >
        Ответ не подтверждён. Продолжи с тем же предпросмотром.
      </p>
      <p
        v-else-if="deletionCommand.phase === 'PASSWORD_REQUIRED'"
        role="status"
      >
        Для повтора нужен пароль. Предпросмотр сохранён.
      </p>
      <p
        v-else
        role="status"
      >
        Загружаем сохранённое состояние операции…
      </p>
      <AdminNotice
        v-if="deletionError && !deletionPreview"
        tone="danger"
        title="Проверка удаления приостановлена"
        class="admin-semester-state--error"
      >
        {{ deletionError }}
      </AdminNotice>
      <p
        v-if="visibleDeletionOperation?.blockingReason"
        class="admin-semester-deletion-progress__reason"
      >
        {{ visibleDeletionOperation.blockingReason }}
      </p>
      <dl class="admin-semester-deletion-counts">
        <div
          v-for="item in deletionCountItems(
            visibleDeletionOperation?.counts ?? deletionCommand.preview.counts,
          )"
          :key="item.label"
        >
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
      <div class="admin-semester-card__actions">
        <button
          v-if="deletionCommand.operationId === null && deletionCommand.phase !== 'SUBMITTING'"
          class="admin-semester-action admin-semester-action--secondary"
          type="button"
          @click="reopenDeletionConfirmation"
        >
          Ввести пароль и продолжить
        </button>
        <button
          v-else-if="deletionCommand.phase === 'PENDING' && deletionError !== null"
          class="admin-semester-action admin-semester-action--secondary"
          type="button"
          @click="continueDeletionCheck"
        >
          Продолжить проверку
        </button>
        <button
          v-if="
            visibleDeletionOperation?.phase === 'CANCELLED' &&
            !visibleDeletionOperation.refreshedPreview
          "
          class="admin-semester-action admin-semester-action--secondary"
          type="button"
          :disabled="!semesters.some((semester) => semester.id === deletionCommand?.semesterId)"
          @click="loadFreshDeletionPreview"
        >
          Загрузить новый предпросмотр
        </button>
        <button
          v-if="deletionCommand.phase === 'TERMINAL' && deletionPreview === null"
          class="admin-semester-action admin-semester-action--secondary"
          type="button"
          @click="dismissDeletionResult"
        >
          Закрыть результат
        </button>
      </div>
    </section>

    <form
      v-if="deletionPreview"
      class="admin-semester-card admin-semester-deletion-confirmation"
      role="group"
      aria-labelledby="admin-semester-deletion-confirmation-title"
      @submit.prevent="confirmSemesterDeletion"
    >
      <h2 id="admin-semester-deletion-confirmation-title"> Проверь последствия удаления </h2>
      <h3>{{ deletionPreview.semesterName }}</h3>
      <p>
        Состояние: {{ deletionPriorStateLabel(deletionPreview.priorState) }}. После подтверждения
        семестр и перечисленные данные будут удалены без возможности восстановления.
      </p>
      <p class="admin-semester-deletion-confirmation__warning">
        Пользователи и группы сохранятся. Архивирование обратимо, а удаление окончательно.
      </p>
      <dl class="admin-semester-deletion-counts">
        <div
          v-for="item in deletionCountItems(deletionPreview.counts)"
          :key="item.label"
        >
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
      <label
        class="admin-semester-deletion-confirmation__password"
        for="admin-semester-delete-password"
      >
        Пароль администратора
        <input
          id="admin-semester-delete-password"
          v-model="deletionPassword"
          autocomplete="current-password"
          :aria-describedby="
            deletionPasswordInvalid ? 'admin-semester-delete-password-error' : undefined
          "
          :aria-invalid="deletionPasswordInvalid || undefined"
          required
          type="password"
          :disabled="deletionCommand?.phase === 'SUBMITTING'"
        />
      </label>
      <p
        v-if="deletionPasswordInvalid"
        id="admin-semester-delete-password-error"
        class="admin-semester-state--error admin-semester-field-error"
        role="alert"
      >
        Пароль не подтверждён. Проверь его и введи снова.
      </p>
      <AdminNotice
        v-if="!deletionStorageAvailable"
        tone="warning"
        title="Подтверждение недоступно"
      >
        Не удалось подтвердить сессию администратора. Обнови профиль перед продолжением.
      </AdminNotice>
      <AdminNotice
        v-if="deletionError && !deletionPasswordInvalid"
        tone="danger"
        title="Удаление не подтверждено"
        class="admin-semester-state--error"
      >
        {{ deletionError }}
      </AdminNotice>
      <div class="admin-semester-card__actions">
        <button
          class="admin-semester-action admin-semester-action--secondary"
          type="button"
          @click="closeDeletionPreview"
        >
          Закрыть предпросмотр
        </button>
        <button
          class="admin-semester-action admin-semester-action--danger"
          type="submit"
          :disabled="
            deletionPassword.length === 0 ||
            !deletionStorageAvailable ||
            deletionPreviewLoading ||
            deletionCommand?.phase === 'SUBMITTING'
          "
          :aria-busy="deletionCommand?.phase === 'SUBMITTING'"
        >
          {{
            deletionCommand?.phase === 'UNCERTAIN' || deletionCommand?.phase === 'PASSWORD_REQUIRED'
              ? 'Повторить тот же запрос'
              : deletionCommand?.phase === 'TERMINAL' &&
                  deletionCommand.operation?.phase === 'CANCELLED' &&
                  deletionCommand.operation.refreshedPreview?.previewDigest ===
                    deletionPreview.previewDigest
                ? 'Подтвердить по обновлённым числам'
                : 'Удалить семестр безвозвратно'
          }}
        </button>
      </div>
    </form>

    <p
      v-if="deletionPreviewLoading && !deletionPreview"
      class="admin-semester-state"
      role="status"
    >
      Загружаем числовой предпросмотр удаления…
    </p>
    <AdminNotice
      v-else-if="deletionError && !deletionPreview && !deletionCommand"
      tone="danger"
      title="Не удалось подготовить удаление"
      class="admin-semester-state--error"
    >
      {{ deletionError }}
    </AdminNotice>

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
        v-if="!taskActive && semesters.length > 0 && !hasActiveSemester"
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
          {{
            archiveConfirmation.action === 'ARCHIVE'
              ? 'Подтверди архивацию'
              : 'Подтверди восстановление'
          }}
        </h2>
        <p>
          {{
            archiveConfirmation.action === 'ARCHIVE'
              ? `Архивировать семестр «${archiveConfirmationSemester.name}»? Все расписание, посещаемость и задания сохранятся; редактирование будет запрещено.`
              : `Восстановить семестр «${archiveConfirmationSemester.name}»? Все данные сохранятся, а сам семестр останется неактивным.`
          }}
        </p>
        <p v-if="archiveConfirmation.action === 'ARCHIVE' && archiveConfirmationSemester.active">
          После завершения не останется активного семестра. Другой период не активируется
          автоматически.
        </p>
        <p v-if="archiveConfirmation.action === 'RESTORE'">
          Чтобы сделать восстановленный семестр текущим, после завершения активируй его отдельно.
        </p>
        <div class="admin-semester-card__actions">
          <button
            class="admin-semester-action admin-semester-action--secondary"
            type="button"
            data-action="cancel"
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
            {{
              archiveConfirmationChecking
                ? 'Проверяем состояние…'
                : archiveConfirmation.action === 'ARCHIVE'
                  ? 'Подтвердить архивацию'
                  : 'Подтвердить восстановление'
            }}
          </button>
        </div>
      </section>

      <section
        v-if="archiveSemestersVisible.length"
        class="admin-semester-group admin-semester-archive-status"
        aria-labelledby="admin-semester-archive-status-title"
      >
        <h2 id="admin-semester-archive-status-title"> Архивация и восстановление </h2>
        <article
          v-for="semester in archiveSemestersVisible"
          :key="semester.id"
          class="admin-semester-card"
          :aria-busy="
            archiveStatusLoadingIds.has(semester.id) ||
            archiveCommandFor(semester.id)?.phase === 'SUBMITTING' ||
            archiveSettlingOperationIds[semester.id] !== undefined
          "
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status">
              <span aria-hidden="true">{{
                semester.archived ? '▣' : semester.active ? '●' : '○'
              }}</span>
              {{ statusLabel(semester) }}
            </span>
          </div>
          <p
            v-if="archiveCommandFor(semester.id)?.phase === 'SUBMITTING'"
            class="admin-semester-archive-progress"
            role="status"
          >
            Ждём подтверждения операции…
          </p>
          <AdminNotice
            v-else-if="archiveCommandFor(semester.id)?.phase === 'UNCERTAIN'"
            tone="warning"
            title="Ответ не подтверждён"
          >
            Повтори тот же запрос; ход операции сохранён.
          </AdminNotice>
          <p
            v-else-if="archiveCommandFor(semester.id)?.phase === 'SETTLING'"
            class="admin-semester-archive-progress"
            role="status"
          >
            Операция завершилась. Проверяем актуальное состояние семестра…
          </p>
          <p
            v-else-if="
              archiveCommandFor(semester.id)?.phase === 'PENDING' ||
              archiveOperationFor(semester.id)?.operationState === 'PENDING'
            "
            class="admin-semester-archive-progress"
            role="status"
          >
            {{
              archiveOperationFor(semester.id)
                ? operationLabel(archiveOperationFor(semester.id)!)
                : 'Операция выполняется…'
            }}
          </p>
          <p
            v-else-if="archiveSettlingOperationIds[semester.id] !== undefined"
            class="admin-semester-archive-progress"
            role="status"
          >
            Операция завершилась. Проверяем актуальное состояние семестра…
          </p>
          <p
            v-else-if="semester.releasePending"
            class="admin-semester-archive-progress"
            role="status"
          >
            Завершается восстановление. Изменения пока недоступны.
          </p>
          <p
            v-else-if="semester.transition !== 'NONE'"
            class="admin-semester-archive-progress"
            role="status"
          >
            {{
              semester.transition === 'ARCHIVING'
                ? 'Семестр архивируется.'
                : 'Семестр восстанавливается.'
            }}
          </p>
          <p
            v-if="semester.isWriteBlocked"
            class="admin-semester-form__hint"
          >
            Изменения временно недоступны.
          </p>
          <div
            v-if="archiveOperationFor(semester.id)"
            class="admin-semester-archive-details"
          >
            <p>
              Последняя операция:
              {{
                archiveOperationFor(semester.id)?.action === 'ARCHIVE'
                  ? 'архивация'
                  : 'восстановление'
              }}
              — {{ operationStateLabel(archiveOperationFor(semester.id)!.operationState) }}.
            </p>
            <ul>
              <li
                >Учебные данные:
                {{ participantStateLabel(archiveOperationFor(semester.id)!.academic) }}</li
              >
              <li
                >Расписание:
                {{ participantStateLabel(archiveOperationFor(semester.id)!.schedule) }}</li
              >
              <li
                >Посещаемость:
                {{ participantStateLabel(archiveOperationFor(semester.id)!.attendance) }}</li
              >
            </ul>
          </div>
          <AdminNotice
            v-if="archiveOperationFor(semester.id)?.operationState === 'ERROR'"
            tone="danger"
            title="Операция не завершена"
          >
            {{
              archiveOperationFor(semester.id)?.blockingReason ?? 'Попробуй продолжить операцию.'
            }}
          </AdminNotice>
          <AdminNotice
            v-if="archiveStatusErrors[semester.id]"
            tone="danger"
            title="Статус недоступен"
          >
            {{ archiveStatusErrors[semester.id] }}
          </AdminNotice>
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
              v-else-if="
                archiveCommandFor(semester.id)?.phase === 'PENDING' ||
                archiveCommandFor(semester.id)?.phase === 'SETTLING'
              "
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
        v-if="canView && !taskActive && semesters.length === 0 && !error"
        class="admin-semester-state"
        data-state="empty"
      >
        Семестров пока нет. Создай первый учебный период.
      </p>

      <template v-if="!taskActive && canView">
        <section
          v-for="section in semesterSections"
          v-show="section.items.length"
          :key="section.key"
          class="admin-semester-group"
          :aria-label="section.label"
        >
          <h2>{{ section.label }}</h2>
          <article
            v-for="semester in section.items"
            :key="semester.id"
            class="admin-semester-card"
            :class="{ 'admin-semester-card--active': semester.active && !semester.archived }"
            :data-semester-id="semester.id"
          >
            <div class="admin-semester-card__heading">
              <h3>{{ semester.name }}</h3>
              <span
                class="admin-semester-status-dot"
                :class="{
                  'admin-semester-status-dot--active': semester.active && !semester.archived,
                  'admin-semester-status-dot--future': semesterFuture(semester),
                }"
                role="img"
                :aria-label="statusLabel(semester)"
              />
            </div>
            <p class="admin-semester-card__period">
              {{ formatPeriod(semester) }}
            </p>
            <button
              v-if="canManage && semester.active"
              class="admin-semester-action admin-semester-action--secondary"
              type="button"
              :disabled="mutationBusy || !canEditMetadata(semester)"
              :title="canEditMetadata(semester) ? undefined : editBlockReason(semester)"
              @click="openEditForm(semester)"
            >
              Редактировать
            </button>
            <div class="admin-semester-card__details">
              <p
                v-if="semester.archived"
                class="admin-semester-form__hint"
              >
                Учебные данные сохранены в архиве. Можно расширить даты периода; восстановление
                оставит семестр неактивным.
              </p>
              <p
                v-if="semester.archived && semesterExpired(semester)"
                class="admin-semester-form__hint"
              >
                Срок семестра истёк. Чтобы восстановить его, сначала продли даты периода.
              </p>
              <div
                v-if="canManage"
                class="admin-semester-card__actions"
              >
                <button
                  v-if="!semester.active"
                  class="admin-semester-action admin-semester-action--secondary"
                  type="button"
                  :disabled="mutationBusy || !canEditMetadata(semester)"
                  :title="canEditMetadata(semester) ? undefined : editBlockReason(semester)"
                  @click="openEditForm(semester)"
                >
                  Редактировать
                </button>
                <button
                  v-if="!semester.archived && !semester.active"
                  class="admin-semester-action admin-semester-action--secondary"
                  type="button"
                  :disabled="mutationBusy || !canActivate(semester)"
                  :title="
                    canActivate(semester)
                      ? undefined
                      : semesterExpired(semester)
                        ? 'Срок семестра истёк.'
                        : editBlockReason(semester)
                  "
                  @click="activationConfirmation = semester"
                >
                  Сделать текущим
                </button>
                <div class="admin-semester-card__lifecycle">
                  <button
                    v-if="!semester.archived"
                    class="admin-semester-action admin-semester-action--secondary"
                    type="button"
                    :disabled="mutationBusy || !canArchive(semester)"
                    :title="
                      canArchive(semester) ? undefined : archiveBlockReason(semester, 'ARCHIVE')
                    "
                    @click="openArchiveConfirmation(semester, 'ARCHIVE')"
                  >
                    {{
                      canStartNewArchiveAttempt(semester, 'ARCHIVE')
                        ? 'Повторить архивацию'
                        : 'Архивировать'
                    }}
                  </button>
                  <button
                    v-else
                    class="admin-semester-action admin-semester-action--secondary"
                    type="button"
                    :disabled="mutationBusy || !canRestore(semester)"
                    :title="
                      canRestore(semester) ? undefined : archiveBlockReason(semester, 'RESTORE')
                    "
                    @click="openArchiveConfirmation(semester, 'RESTORE')"
                  >
                    {{
                      canStartNewArchiveAttempt(semester, 'RESTORE')
                        ? 'Повторить восстановление'
                        : 'Восстановить'
                    }}
                  </button>
                  <button
                    v-if="canDelete"
                    class="admin-semester-action admin-semester-action--danger"
                    type="button"
                    :disabled="mutationBusy || !canRequestSemesterDeletion(semester)"
                    :title="
                      canRequestSemesterDeletion(semester)
                        ? undefined
                        : deletionBlockReason(semester)
                    "
                    @click="openDeletionPreview(semester)"
                  >
                    Удалить
                  </button>
                </div>
              </div>
            </div>
          </article>
        </section>
      </template>
    </div>
  </main>
</template>
