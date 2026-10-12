<script setup lang="ts">
import ExportButton from '../../shared/components/ExportButton.vue';
import type {
  ReportDownloadPort,
  ReportDownloadFormat,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { adminHasPermission, type AdminAccessProfile } from '../admin-access/admin-access-client';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import AdminNotice from '../../shared/components/AdminNotice.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import {
  AdminGroupsApiError,
  adminGroupProgramTypes,
  type AdminGroupSort,
  type AdminGroup,
  type AdminGroupStatus,
  type HeadmanAssignmentPreview,
  type HeadmanRoster,
  type AdminGroupsClient,
  type PromotionSummary,
  type PromotionConflict,
} from './admin-groups-client';
import './admin-groups-screen.pcss';

const props = withDefaults(
  defineProps<{
    reportDownload?: ReportDownloadPort | null;
    client: AdminGroupsClient;
    authority: AdminAccessProfile | null;
    theme?: 'dark' | 'light';
  }>(),
  {
    theme: 'dark',
    reportDownload: null,
  },
);

const emit = defineEmits<{
  ownerError: [cause: unknown];
  'navigation-visibility': [visible: boolean];
}>();

const canView = computed(() => adminHasPermission(props.authority, 'GROUPS_VIEW'));
const canManage = computed(
  () => canView.value && adminHasPermission(props.authority, 'GROUPS_MANAGE'),
);
const groups = ref<readonly AdminGroup[]>([]);
const status = ref<AdminGroupStatus>('ACTIVE');
const search = ref('');
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const counts = ref({ active: 0, draft: 0, archived: 0 });
const loading = ref(true);
const saving = ref(false);
const restoreSavingId = ref<number | null>(null);
const restoreRefreshPendingIds = ref<ReadonlySet<number>>(new Set());
const formVisible = ref(false);
const expandedGroupId = ref<number | null>(null);
const filtersVisible = ref(false);
const filterStatus = ref<AdminGroupStatus>('ACTIVE');
const course = ref<number | null>(null),
  programType = ref<number | null>(null),
  registrySort = ref<AdminGroupSort | null>(null);
const filterCourse = ref<number | null>(null),
  filterProgramType = ref<number | null>(null),
  filterSort = ref<AdminGroupSort | null>(null);
const expandedFilter = ref<'course' | 'program' | 'sort' | 'status' | null>(null);
const sortOptions: readonly { value: AdminGroupSort | null; label: string }[] = [
  { value: null, label: 'По умолчанию' },
  { value: 'name,asc', label: 'По коду: А–Я' },
  { value: 'name,desc', label: 'По коду: Я–А' },
  { value: 'currentCourse,asc', label: 'По курсу: сначала младшие' },
  { value: 'currentCourse,desc', label: 'По курсу: сначала старшие' },
  { value: 'programType,asc', label: 'По типу программы: 1–9' },
  { value: 'programType,desc', label: 'По типу программы: 9–1' },
];
const exportScope = computed(() =>
  JSON.stringify([
    status.value,
    search.value,
    course.value,
    programType.value,
    registrySort.value,
    props.authority,
  ]),
);
function exportRequest(format: ReportDownloadFormat): ReportDownloadTicketRequest {
  const program = adminGroupProgramTypes.find((item) => item.digit === programType.value);
  const sort = registrySort.value?.split(',');
  return {
    kind: 'ADMIN_GROUPS',
    adminGroups: {
      status: status.value,
      ...(search.value.trim() ? { search: search.value.trim() } : {}),
      ...(course.value === null ? {} : { course: course.value }),
      ...(program ? { studyMode: program.studyMode, educationLevel: program.educationLevel } : {}),
      ...(sort ? { sorts: [{ field: sort[0]!, descending: sort[1] === 'desc' }] } : {}),
      format,
    },
  };
}
function openFilters(): void {
  filterStatus.value = status.value;
  filterCourse.value = course.value;
  filterProgramType.value = programType.value;
  filterSort.value = registrySort.value;
  expandedFilter.value = null;
  filtersVisible.value = true;
}
function applyFilters(): void {
  invalidateRestoreForScopeChange();
  course.value = filterCourse.value;
  programType.value = filterProgramType.value;
  registrySort.value = filterSort.value;
  status.value = filterStatus.value;
  page.value = 0;
  expandedGroupId.value = null;
  filtersVisible.value = false;
  void refresh();
}
function resetFilters(): void {
  course.value = null;
  programType.value = null;
  registrySort.value = null;
  search.value = '';
  status.value = 'ACTIVE';
  submitSearch();
}
function programTypeLabel(digit: number | null): string {
  return adminGroupProgramTypes.find((item) => item.digit === digit)?.label ?? 'Все типы программ';
}
const archiveGroupConfirmation = ref<AdminGroup | null>(null);
const archiveGroupSaving = ref(false);
const alphabeticCode = ref('');
const numericCode = ref('');
const trainingDurationYears = ref('');
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const assignmentGroup = ref<AdminGroup | null>(null);
const headmanRoster = ref<HeadmanRoster | null>(null);
const selectedStudentId = ref<number | null>(null);
const assignmentPreview = ref<HeadmanAssignmentPreview | null>(null);
const headmanLoading = ref(false);
const headmanSaving = ref(false);
const headmanMutationPending = ref(false);
const promotionPanelVisible = ref(false);
const promotionScopeGroup = ref<AdminGroup | null>(null);
const promotionPreview = ref<PromotionSummary | null>(null);
const promotionResult = ref<PromotionSummary | null>(null);
const promotionLoading = ref(false);
const promotionSaving = ref(false);
const promotionMutationPending = ref(false);
const promotionError = ref<string | null>(null);
const renameGroup = ref<AdminGroup | null>(null);
const renameName = ref('');
const renameDuration = ref('');
const renameSaving = ref(false);
const renameError = ref<string | null>(null);
const renameUnknown = ref(false);
const renameValid = computed(() => {
  const group = renameGroup.value,
    code = renameName.value
      .trim()
      .toUpperCase()
      .match(/^[А-ЯЁ]{2,4}-([1-5])[1-9][0-9]$/);
  const duration = Number(renameDuration.value);
  return (
    group !== null &&
    code !== null &&
    /^[1-5]$/.test(renameDuration.value) &&
    Number(code[1]) <= duration &&
    (renameName.value.trim().toUpperCase() !== group.name ||
      duration !== group.trainingDurationYears)
  );
});
const promotionConfirmDialog = ref<HTMLDialogElement | null>(null);

const taskActive = computed(
  () =>
    formVisible.value ||
    filtersVisible.value ||
    archiveGroupConfirmation.value !== null ||
    assignmentGroup.value !== null ||
    promotionPanelVisible.value,
);
watch(taskActive, (active) => emit('navigation-visibility', !active), { immediate: true });
function back(): void {
  if (archiveGroupSaving.value) return;
  if (archiveGroupConfirmation.value) {
    archiveGroupConfirmation.value = null;
    return;
  }
  if (filtersVisible.value) {
    filtersVisible.value = false;
    return;
  }
  if (promotionConfirmDialog.value?.open) {
    promotionConfirmDialog.value.close();
    return;
  }
  if (renameGroup.value) {
    if (!renameSaving.value) {
      renameGroup.value = null;
      renameError.value = null;
    }
    return;
  }
  if (promotionPanelVisible.value) {
    closePromotion();
    return;
  }
  if (assignmentGroup.value) {
    closeHeadman();
    return;
  }
  if (!saving.value) formVisible.value = false;
}
async function confirmGroupArchive(): Promise<void> {
  const group = archiveGroupConfirmation.value;
  if (!canManage.value || !group || archiveGroupSaving.value) return;
  archiveGroupSaving.value = true;
  error.value = null;
  const client = props.client;
  const authority = props.authority;
  const scope = registryScopeRevision;
  try {
    await client.archiveGroup(group.id, group.name);
    if (
      disposed ||
      client !== props.client ||
      authority !== props.authority ||
      scope !== registryScopeRevision
    )
      return;
    archiveGroupConfirmation.value = null;
    notice.value = `Группа ${displayCode(group)} отправлена в архив.`;
    await refresh(true);
  } catch (cause) {
    if (
      disposed ||
      client !== props.client ||
      authority !== props.authority ||
      scope !== registryScopeRevision ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    showError(cause, 'Архивацию не удалось подтвердить. Обнови список перед повтором.');
    archiveGroupConfirmation.value = null;
    await refresh(true);
  } finally {
    if (
      !disposed &&
      client === props.client &&
      authority === props.authority &&
      scope === registryScopeRevision
    )
      archiveGroupSaving.value = false;
  }
}
async function openNotificationGroup(
  groupId: number,
  isCurrentContext: () => boolean,
): Promise<boolean> {
  const client = props.client,
    authority = props.authority;
  if (disposed || !canView.value || !isCurrentContext()) return false;
  const target = await client.resolveNotificationGroup(groupId);
  if (
    !target ||
    disposed ||
    !isCurrentContext() ||
    client !== props.client ||
    authority !== props.authority ||
    !canView.value
  )
    return false;
  status.value = target.status;
  search.value = target.name;
  page.value = 0;
  course.value = null;
  programType.value = null;
  registrySort.value = null;
  const result = await refresh();
  if (
    result !== 'success' ||
    !isCurrentContext() ||
    disposed ||
    !canView.value ||
    !groups.value.some((group) => group.id === groupId)
  )
    return false;
  expandedGroupId.value = groupId;
  return true;
}
defineExpose({ back, openNotificationGroup });
let disposed = false;
let listRevision = 0;
let listController: AbortController | null = null;
let registryScopeRevision = 0;
let restoreMutationRevision = 0;
let restoreMutationController: AbortController | null = null;
let headmanRevision = 0;
let headmanContextRevision = 0;
let headmanController: AbortController | null = null;
let headmanMutationRevision = 0;
let headmanMutationController: AbortController | null = null;
let promotionContextRevision = 0;
let promotionPreviewRevision = 0;
let promotionPreviewController: AbortController | null = null;
let promotionMutationRevision = 0;
let promotionMutationController: AbortController | null = null;

const tabs: readonly { value: AdminGroupStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Активные' },
  { value: 'DRAFT', label: 'Черновики' },
  { value: 'ARCHIVED', label: 'Архив' },
];

onMounted(() => void refresh());

async function refresh(preserveMessages = false): Promise<'success' | 'failed' | 'stale'> {
  if (!canView.value) {
    groups.value = [];
    loading.value = false;
    return 'stale';
  }
  const revision = ++listRevision;
  listController?.abort();
  const controller = new AbortController();
  listController = controller;
  loading.value = true;
  if (!preserveMessages) error.value = null;
  try {
    const result = await props.client.listGroups(
      {
        status: status.value,
        search: search.value,
        page: page.value,
        size: 3,
        ...(course.value === null ? {} : { course: course.value }),
        ...(() => {
          const program = adminGroupProgramTypes.find((item) => item.digit === programType.value);
          return program
            ? { studyMode: program.studyMode, educationLevel: program.educationLevel }
            : {};
        })(),
        ...(registrySort.value === null ? {} : { sort: [registrySort.value] }),
      },
      controller.signal,
    );
    if (!isCurrent(revision, controller)) return 'stale';
    groups.value = result.items;
    if (status.value === 'ARCHIVED') {
      const returnedArchivedIds = new Set(result.items.map((group) => group.id));
      restoreRefreshPendingIds.value = new Set(
        [...restoreRefreshPendingIds.value].filter((id) => !returnedArchivedIds.has(id)),
      );
    }
    totalPages.value = result.totalPages;
    totalElements.value = result.totalElements;
    counts.value = {
      active: result.activeCount,
      draft: result.draftCount,
      archived: result.archivedCount,
    };
    if (result.totalPages === 0 && page.value !== 0) {
      page.value = 0;
      return await refresh(preserveMessages);
    }
    if (result.totalPages > 0 && page.value >= result.totalPages) {
      page.value = result.totalPages - 1;
      return await refresh(preserveMessages);
    }
    return 'success';
  } catch (cause) {
    if (
      !isCurrent(revision, controller) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return 'stale';
    if (!preserveMessages) showError(cause, 'Реестр групп не удалось загрузить.');
    return 'failed';
  } finally {
    if (isCurrent(revision, controller)) {
      loading.value = false;
      listController = null;
    }
  }
}

function isCurrent(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRevision && listController === controller;
}

function selectStatus(next: AdminGroupStatus): void {
  if (status.value === next) return;
  expandedGroupId.value = null;
  invalidateRestoreForScopeChange();
  status.value = next;
  page.value = 0;
  void refresh();
}

function submitSearch(): void {
  invalidateRestoreForScopeChange();
  page.value = 0;
  void refresh();
}

function changePage(next: number): void {
  if (next === page.value) return;
  invalidateRestoreForScopeChange();
  page.value = next;
  void refresh();
}

watch(search, () => invalidateRestoreForScopeChange(), { flush: 'sync' });

function invalidateRestoreForScopeChange(): void {
  registryScopeRevision += 1;
  restoreMutationRevision += 1;
  restoreMutationController?.abort();
  restoreMutationController = null;
  restoreSavingId.value = null;
}

function isRestoreCurrent(
  revision: number,
  controller: AbortController,
  scopeRevision: number,
): boolean {
  return (
    !disposed &&
    revision === restoreMutationRevision &&
    restoreMutationController === controller &&
    registryScopeRevision === scopeRevision &&
    status.value === 'ARCHIVED'
  );
}

async function restoreGroup(group: AdminGroup): Promise<void> {
  if (
    !canManage.value ||
    status.value !== 'ARCHIVED' ||
    restoreSavingId.value !== null ||
    restoreRefreshPendingIds.value.has(group.id)
  )
    return;

  const revision = ++restoreMutationRevision;
  const scopeRevision = registryScopeRevision;
  const controller = new AbortController();
  restoreMutationController = controller;
  restoreSavingId.value = group.id;
  error.value = null;
  notice.value = null;
  try {
    await props.client.restoreGroup(group.id, controller.signal);
    if (!isRestoreCurrent(revision, controller, scopeRevision)) return;

    restoreRefreshPendingIds.value = new Set([...restoreRefreshPendingIds.value, group.id]);
    notice.value = `Группа ${displayCode(group)} восстановлена.`;
    const refreshed = await refresh(true);
    if (!isRestoreCurrent(revision, controller, scopeRevision)) return;
    if (refreshed === 'failed') {
      error.value =
        'Группа восстановлена, но список и счётчики не удалось обновить. Обнови реестр.';
    }
  } catch (cause) {
    if (
      !isRestoreCurrent(revision, controller, scopeRevision) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    showError(cause, 'Группу не удалось восстановить.');
  } finally {
    if (revision === restoreMutationRevision && restoreMutationController === controller) {
      restoreSavingId.value = null;
      restoreMutationController = null;
    }
  }
}

async function openHeadman(group: AdminGroup): Promise<void> {
  if (!canManage.value) return;
  closeHeadman(false);
  assignmentGroup.value = group;
  const contextRevision = headmanContextRevision;
  headmanLoading.value = true;
  const revision = ++headmanRevision;
  const controller = new AbortController();
  headmanController = controller;
  try {
    const result = await props.client.getHeadmanRoster(group.id, controller.signal);
    if (
      !isHeadmanCurrent(revision, controller, contextRevision) ||
      assignmentGroup.value?.id !== group.id
    )
      return;
    headmanRoster.value = result;
    selectedStudentId.value = result.currentHeadmanId;
    if (selectedStudentId.value !== null) await loadPreview();
  } catch (cause) {
    if (
      !isHeadmanCurrent(revision, controller, contextRevision) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    showError(cause, 'Состав группы не удалось загрузить.');
  } finally {
    if (isHeadmanCurrent(revision, controller, contextRevision)) {
      headmanLoading.value = false;
      headmanController = null;
    }
  }
}

async function loadPreview(): Promise<void> {
  if (!canManage.value) return;
  const group = assignmentGroup.value;
  const studentId = selectedStudentId.value;
  if (group === null || studentId === null) {
    assignmentPreview.value = null;
    return;
  }
  const contextRevision = headmanContextRevision;
  const groupId = group.id;
  headmanSaving.value = false;
  headmanController?.abort();
  const revision = ++headmanRevision;
  const controller = new AbortController();
  headmanController = controller;
  headmanLoading.value = true;
  try {
    const result = await props.client.previewHeadman(groupId, studentId, controller.signal);
    if (
      !isHeadmanCurrent(revision, controller, contextRevision) ||
      !isHeadmanContextCurrent(contextRevision, groupId, studentId)
    )
      return;
    assignmentPreview.value = result;
  } catch (cause) {
    if (
      !isHeadmanCurrent(revision, controller, contextRevision) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    showError(cause, 'Предпросмотр назначения не удалось загрузить.');
    assignmentPreview.value = null;
  } finally {
    if (isHeadmanCurrent(revision, controller, contextRevision)) {
      headmanLoading.value = false;
      headmanController = null;
    }
  }
}

async function confirmHeadman(): Promise<void> {
  if (!canManage.value || headmanMutationPending.value) return;
  const contextRevision = headmanContextRevision;
  const group = assignmentGroup.value;
  const roster = headmanRoster.value;
  const studentId = selectedStudentId.value;
  if (group === null || roster === null || studentId === null) return;
  if (assignmentPreview.value === null || assignmentPreview.value.candidateId !== studentId) {
    await loadPreview();
    if (!isHeadmanContextCurrent(contextRevision, group.id, studentId)) return;
    if (assignmentPreview.value === null || assignmentPreview.value.candidateId !== studentId)
      return;
  }
  const currentGroup = assignmentGroup.value;
  const currentRoster = headmanRoster.value;
  const currentStudentId = selectedStudentId.value;
  if (
    !isHeadmanContextCurrent(contextRevision, group.id, studentId) ||
    currentGroup === null ||
    currentRoster === null ||
    currentStudentId === null
  )
    return;
  headmanSaving.value = true;
  headmanMutationPending.value = true;
  error.value = null;
  notice.value = null;
  const revision = ++headmanMutationRevision;
  const controller = new AbortController();
  headmanMutationController = controller;
  const refreshAfterOutcome = async (): Promise<void> => {
    if (!disposed) await refresh(true);
  };
  try {
    const result = await props.client.assignHeadman(
      currentGroup.id,
      {
        studentId: currentStudentId,
        expectedHeadmanId: currentRoster.currentHeadmanId,
      },
      controller.signal,
    );
    const reconciliation = refreshAfterOutcome();
    if (!isHeadmanMutationCurrent(revision, controller, contextRevision, group.id, studentId)) {
      void reconciliation;
      return;
    }
    headmanSaving.value = false;
    closeHeadman(false);
    notice.value = result.changed
      ? `Староста группы изменён: ${result.headmanFio}.`
      : 'Староста уже назначен, изменений нет.';
    await reconciliation;
  } catch (cause) {
    void refreshAfterOutcome();
    if (
      !isHeadmanMutationCurrent(revision, controller, contextRevision, group.id, studentId) ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    showError(cause, 'Старосту не удалось подтвердить, реестр обновляется.');
  } finally {
    if (headmanMutationRevision === revision && headmanMutationController === controller) {
      headmanMutationPending.value = false;
      headmanMutationController = null;
      if (isHeadmanContextCurrent(contextRevision, group.id, studentId)) {
        headmanSaving.value = false;
      }
    }
  }
}

async function openPromotion(group?: AdminGroup): Promise<void> {
  if (!canManage.value || promotionMutationPending.value) return;
  closePromotion(false);
  promotionScopeGroup.value = group ?? null;
  promotionPanelVisible.value = true;
  promotionResult.value = null;
  promotionError.value = null;
  notice.value = null;
  await loadPromotionPreview();
}

async function loadPromotionPreview(): Promise<void> {
  if (!canManage.value) return;
  if (!promotionPanelVisible.value || promotionMutationPending.value) return;
  promotionResult.value = null;
  notice.value = null;
  const context = promotionContextRevision;
  const groupId = promotionScopeGroup.value?.id;
  promotionPreviewController?.abort();
  const revision = ++promotionPreviewRevision;
  const controller = new AbortController();
  promotionPreviewController = controller;
  promotionLoading.value = true;
  promotionError.value = null;
  promotionPreview.value = null;
  try {
    const result = await props.client.previewPromotion(groupId, controller.signal);
    if (!isPromotionPreviewCurrent(revision, controller, context, groupId)) return;
    promotionPreview.value = result;
  } catch (cause) {
    if (
      !isPromotionPreviewCurrent(revision, controller, context, groupId) ||
      cause instanceof StaleSessionGenerationError ||
      isAbortError(cause)
    )
      return;
    promotionError.value = promotionApiErrorMessage(
      cause,
      'Предпросмотр перевода не удалось загрузить.',
    );
    emitPromotionAuthError(cause);
  } finally {
    if (isPromotionPreviewCurrent(revision, controller, context, groupId)) {
      promotionLoading.value = false;
      promotionPreviewController = null;
    }
  }
}

function correctionGroups(conflict: PromotionConflict): readonly AdminGroup[] {
  if (
    ![
      'name_conflict',
      'parse_error',
      'invalid_code',
      'unknown_type',
      'unrepresentable_next_course',
    ].includes(conflict.reason)
  )
    return [];
  return groups.value.filter(
    (group) => conflict.groupIds.includes(group.id) && group.status === 'ACTIVE',
  );
}
function conflictResolution(conflict: PromotionConflict): string {
  return ['name_conflict', 'parse_error', 'invalid_code'].includes(conflict.reason)
    ? 'Проверь название и срок обучения указанных групп в реестре. После исправления обнови предпросмотр.'
    : 'Проверь тип программы, курс и срок обучения указанных групп. Укажи срок явно, если он неизвестен; затем обнови предпросмотр.';
}
async function openRename(group: AdminGroup): Promise<void> {
  if (!canManage.value || renameSaving.value || promotionMutationPending.value) return;
  const context = promotionContextRevision;
  const result = await refresh(true);
  if (!isPromotionContextCurrent(context) || !canManage.value || result !== 'success') return;
  const fresh = groups.value.find((item) => item.id === group.id);
  if (!fresh || fresh.status !== 'ACTIVE') return;
  renameGroup.value = fresh;
  renameName.value = fresh.name;
  renameDuration.value =
    fresh.trainingDurationYears === null ? '' : String(fresh.trainingDurationYears);
  renameError.value = null;
  renameUnknown.value = false;
}
async function saveRename(): Promise<void> {
  const group = renameGroup.value;
  if (!canManage.value || !group || !renameValid.value || renameSaving.value || renameUnknown.value)
    return;
  const context = promotionContextRevision,
    client = props.client;
  const name = renameName.value.trim().toUpperCase();
  const duration = Number(renameDuration.value);
  renameSaving.value = true;
  renameError.value = null;
  try {
    const current = await client.listGroups({ status: 'ACTIVE', search: group.name, size: 200 });
    if (
      !isPromotionContextCurrent(context) ||
      client !== props.client ||
      renameGroup.value?.id !== group.id
    )
      return;
    const fresh = current.items.find((item) => item.id === group.id);
    if (
      !fresh ||
      fresh.name !== group.name ||
      fresh.trainingDurationYears !== group.trainingDurationYears ||
      fresh.durationStatus !== group.durationStatus
    ) {
      renameError.value = 'Данные группы изменились. Закрой форму и обнови предпросмотр.';
      return;
    }
    await client.renameGroup(
      fresh,
      name,
      duration === fresh.trainingDurationYears ? undefined : duration,
    );
    if (!isPromotionContextCurrent(context) || client !== props.client) return;
    renameGroup.value = null;
    await refresh(true);
    await loadPromotionPreview();
  } catch (cause) {
    if (
      !isPromotionContextCurrent(context) ||
      client !== props.client ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    renameUnknown.value = !(cause instanceof AdminGroupsApiError) || cause.response.status >= 500;
    renameError.value = renameUnknown.value
      ? 'Ответ не подтверждён. Проверь название в реестре перед новым запросом.'
      : promotionApiErrorMessage(cause, 'Название не сохранено.');
    emitPromotionAuthError(cause);
    await refresh(true);
  } finally {
    if (isPromotionContextCurrent(context) && client === props.client) renameSaving.value = false;
  }
}
async function reconcileRename(): Promise<void> {
  const group = renameGroup.value;
  if (!canManage.value || !group || renameSaving.value) return;
  const context = promotionContextRevision,
    client = props.client,
    expected = renameName.value.trim().toUpperCase(),
    duration = Number(renameDuration.value);
  renameSaving.value = true;
  try {
    const current = await client.listGroups({ status: 'ACTIVE', search: expected, size: 200 });
    if (!isPromotionContextCurrent(context) || client !== props.client) return;
    if (
      current.items.some(
        (item) =>
          item.id === group.id && item.name === expected && item.trainingDurationYears === duration,
      )
    ) {
      renameGroup.value = null;
      await refresh(true);
      await loadPromotionPreview();
    } else
      renameError.value =
        'Новое название пока не подтверждено. Закрой форму, проверь группу в реестре и подготовь новый предпросмотр.';
  } catch (cause) {
    if (
      isPromotionContextCurrent(context) &&
      client === props.client &&
      !(cause instanceof StaleSessionGenerationError)
    ) {
      renameError.value = promotionApiErrorMessage(cause, 'Реестр не удалось проверить.');
      emitPromotionAuthError(cause);
    }
  } finally {
    if (isPromotionContextCurrent(context) && client === props.client) renameSaving.value = false;
  }
}

function downloadPromotionConflicts(): void {
  if (!canManage.value) return;
  const summary = promotionResult.value ?? promotionPreview.value;
  if (!summary?.conflicts.length) return;
  const url = URL.createObjectURL(
    new Blob(
      [
        JSON.stringify(
          {
            cycleSemesterId: summary.cycleSemesterId,
            cycleDateTo: summary.cycleDateTo,
            groupId: summary.groupId,
            previewVersion: summary.previewVersion,
            executed: summary.executed,
            conflicts: summary.conflicts,
          },
          null,
          2,
        ),
      ],
      { type: 'application/json;charset=utf-8' },
    ),
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = `group-promotion-conflicts-${summary.cycleSemesterId}.json`;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function isPromotionPreviewCurrent(
  revision: number,
  controller: AbortController,
  context: number,
  groupId: number | undefined,
): boolean {
  return (
    !disposed &&
    promotionPanelVisible.value &&
    revision === promotionPreviewRevision &&
    context === promotionContextRevision &&
    promotionPreviewController === controller &&
    promotionScopeGroup.value?.id === groupId
  );
}

function requestPromotionConfirmation(): void {
  const preview = promotionPreview.value;
  if (
    preview === null ||
    !preview.dryRun ||
    preview.executed ||
    preview.groupId !== (promotionScopeGroup.value?.id ?? null) ||
    (preview.toPromote.length === 0 && preview.toArchive.length === 0) ||
    promotionMutationPending.value
  )
    return;
  promotionConfirmDialog.value?.showModal();
}

async function confirmPromotion(): Promise<void> {
  if (!canManage.value) return;
  if (!canManage.value || promotionMutationPending.value) return;
  const preview = promotionPreview.value;
  const context = promotionContextRevision;
  if (
    preview === null ||
    !preview.dryRun ||
    preview.executed ||
    preview.groupId !== (promotionScopeGroup.value?.id ?? null)
  )
    return;

  const groupId = preview.groupId;
  const previousIndex =
    groupId === null ? -1 : groups.value.findIndex((group) => group.id === groupId);
  promotionSaving.value = true;
  promotionMutationPending.value = true;
  promotionError.value = null;
  error.value = null;
  const revision = ++promotionMutationRevision;
  const controller = new AbortController();
  promotionMutationController = controller;
  try {
    const result = await props.client.executePromotion(
      {
        cycleSemesterId: preview.cycleSemesterId,
        previewVersion: preview.previewVersion,
        groupId: preview.groupId,
      },
      controller.signal,
    );
    if (!isPromotionMutationCurrent(revision, controller, context)) {
      void refresh(true);
      return;
    }
    if (
      !result.executed ||
      result.dryRun ||
      result.cycleSemesterId !== preview.cycleSemesterId ||
      result.previewVersion !== preview.previewVersion ||
      result.groupId !== preview.groupId
    ) {
      throw new Error('Сервер не подтвердил выполнение показанного плана.');
    }
    promotionResult.value = result;
    promotionPreview.value = null;
    promotionError.value = null;
    promotionConfirmDialog.value?.close();
    notice.value = promotionResultMessage(result);
    await refresh(true);
    if (groupId !== null && result.toArchive.some((item) => item.id === groupId)) {
      await focusAfterArchivedGroup(groupId, previousIndex);
    }
  } catch (cause) {
    void refresh(true);
    if (
      !isPromotionMutationCurrent(revision, controller, context) ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    const stale = cause instanceof AdminGroupsApiError && cause.response.status === 409;
    promotionPreview.value = null;
    promotionConfirmDialog.value?.close();
    promotionError.value = promotionApiErrorMessage(
      cause,
      stale
        ? 'Предпросмотр устарел. Реестр обновлён; подготовь новый предпросмотр.'
        : 'Сервер не подтвердил результат. Обнови реестр и подготовь новый предпросмотр.',
    );
    emitPromotionAuthError(cause);
  } finally {
    if (promotionMutationRevision === revision && promotionMutationController === controller) {
      promotionMutationPending.value = false;
      promotionMutationController = null;
      if (isPromotionContextCurrent(context)) promotionSaving.value = false;
    }
  }
}

function isPromotionMutationCurrent(
  revision: number,
  controller: AbortController,
  context: number,
): boolean {
  return (
    !disposed &&
    revision === promotionMutationRevision &&
    promotionMutationController === controller &&
    isPromotionContextCurrent(context)
  );
}

function isPromotionContextCurrent(context: number): boolean {
  return !disposed && promotionPanelVisible.value && context === promotionContextRevision;
}

function closePromotion(clearResult = true): void {
  renameGroup.value = null;
  renameError.value = null;
  renameSaving.value = false;
  renameUnknown.value = false;
  promotionContextRevision += 1;
  promotionPreviewRevision += 1;
  promotionPreviewController?.abort();
  promotionPreviewController = null;
  promotionPanelVisible.value = false;
  promotionScopeGroup.value = null;
  promotionPreview.value = null;
  promotionLoading.value = false;
  promotionSaving.value = false;
  promotionError.value = null;
  if (clearResult) promotionResult.value = null;
  if (promotionConfirmDialog.value?.open) promotionConfirmDialog.value.close();
}

async function focusAfterArchivedGroup(groupId: number, previousIndex: number): Promise<void> {
  await nextTick();
  const rows = Array.from(
    document.querySelectorAll<HTMLElement>('.admin-groups-list [data-group-id]'),
  );
  const target =
    rows.find((row) => row.dataset.groupId === String(groupId)) ??
    rows[Math.min(Math.max(previousIndex, 0), rows.length - 1)];
  target?.querySelector<HTMLButtonElement>('button')?.focus();
}

function promotionResultMessage(result: PromotionSummary): string {
  return (
    'Перевод завершён. На следующем курсе: ' +
    groupCountLabel(result.promoteCount) +
    ' · ' +
    studentCountLabel(result.promotedStudentCount) +
    '. В архиве: ' +
    groupCountLabel(result.archiveCount) +
    ' · ' +
    studentCountLabel(result.archivedStudentCount) +
    '. Пропущено: ' +
    groupCountLabel(result.skippedGroupCount) +
    '.'
  );
}

function promotionApiErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof AdminGroupsApiError) return cause.problem?.detail ?? fallback;
  return cause instanceof Error ? cause.message : fallback;
}

function emitPromotionAuthError(cause: unknown): void {
  if (
    cause instanceof AdminGroupsApiError &&
    (cause.response.status === 401 || cause.response.status === 403)
  )
    emit('ownerError', cause);
}

function closeHeadman(clearNotice = true): void {
  headmanContextRevision += 1;
  headmanRevision += 1;
  headmanController?.abort();
  headmanController = null;
  assignmentGroup.value = null;
  headmanRoster.value = null;
  selectedStudentId.value = null;
  assignmentPreview.value = null;
  headmanLoading.value = false;
  headmanSaving.value = false;
  if (clearNotice) error.value = null;
}

function isHeadmanCurrent(
  revision: number,
  controller: AbortController,
  contextRevision: number,
): boolean {
  return (
    !disposed &&
    revision === headmanRevision &&
    contextRevision === headmanContextRevision &&
    headmanController === controller
  );
}

function isHeadmanContextCurrent(
  contextRevision: number,
  groupId: number,
  studentId: number,
): boolean {
  return (
    !disposed &&
    contextRevision === headmanContextRevision &&
    assignmentGroup.value?.id === groupId &&
    selectedStudentId.value === studentId
  );
}

function isHeadmanMutationCurrent(
  revision: number,
  controller: AbortController,
  contextRevision: number,
  groupId: number,
  studentId: number,
): boolean {
  return (
    headmanMutationRevision === revision &&
    headmanMutationController === controller &&
    isHeadmanContextCurrent(contextRevision, groupId, studentId)
  );
}

async function createGroup(): Promise<void> {
  if (!canManage.value || saving.value) return;
  const authority = props.authority;
  const scope = registryScopeRevision;
  const duration = Number(trainingDurationYears.value);
  if (!alphabeticCode.value.trim() || !numericCode.value.trim() || !Number.isInteger(duration)) {
    error.value = 'Укажи буквенный код, трёхзначный цифровой код и срок обучения.';
    return;
  }
  saving.value = true;
  error.value = null;
  notice.value = null;
  try {
    await props.client.createGroup({
      alphabeticCode: alphabeticCode.value,
      numericCode: numericCode.value,
      trainingDurationYears: duration,
    });
    if (disposed || authority !== props.authority || scope !== registryScopeRevision) return;
    alphabeticCode.value = '';
    numericCode.value = '';
    trainingDurationYears.value = '';
    formVisible.value = false;
    status.value = 'DRAFT';
    page.value = 0;
    search.value = '';
    saving.value = false;
    invalidateRestoreForScopeChange();
    notice.value = 'Группа создана и добавлена в черновики.';
    await refresh();
  } catch (cause) {
    if (
      !disposed &&
      authority === props.authority &&
      scope === registryScopeRevision &&
      !(cause instanceof StaleSessionGenerationError)
    )
      showError(cause, 'Группу не удалось создать.');
  } finally {
    if (!disposed && authority === props.authority && scope === registryScopeRevision)
      saving.value = false;
  }
}

function showError(cause: unknown, fallback: string): void {
  error.value =
    cause instanceof AdminGroupsApiError
      ? (cause.problem?.detail ?? cause.message)
      : cause instanceof Error
        ? cause.message
        : fallback;
  if (
    cause instanceof AdminGroupsApiError &&
    (cause.response.status === 401 || cause.response.status === 403)
  ) {
    emit('ownerError', cause);
  }
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError';
}

function statusCount(value: AdminGroupStatus): number {
  return value === 'ACTIVE'
    ? counts.value.active
    : value === 'DRAFT'
      ? counts.value.draft
      : counts.value.archived;
}

function displayCode(group: AdminGroup): string {
  if (group.alphabeticCode && group.numericCode)
    return `${group.alphabeticCode}-${group.numericCode}`;
  return group.name;
}

function groupCountLabel(count: number): string {
  const value = Math.abs(count);
  const ending =
    value % 100 >= 11 && value % 100 <= 14
      ? 'групп'
      : value % 10 === 1
        ? 'группа'
        : value % 10 >= 2 && value % 10 <= 4
          ? 'группы'
          : 'групп';
  return `${count} ${ending}`;
}

function studentCountLabel(count: number): string {
  const value = Math.abs(count);
  const ending =
    value % 100 >= 11 && value % 100 <= 14
      ? 'студентов'
      : value % 10 === 1
        ? 'студент'
        : value % 10 >= 2 && value % 10 <= 4
          ? 'студента'
          : 'студентов';
  return `${count} ${ending}`;
}

function durationLabel(group: AdminGroup): string {
  return group.durationStatus === 'KNOWN' && group.trainingDurationYears !== null
    ? `${group.trainingDurationYears} лет`
    : 'Не определён';
}

watch(
  () => props.client,
  () => {
    if (renameGroup.value) closePromotion();
  },
  { flush: 'sync' },
);

watch(
  () => [
    props.authority,
    props.authority?.version,
    props.authority?.mode,
    props.authority?.assigned,
    props.authority?.permissions.join('|'),
  ],
  () => {
    listRevision += 1;
    listController?.abort();
    listController = null;
    invalidateRestoreForScopeChange();
    closeHeadman();
    headmanMutationRevision += 1;
    headmanMutationController?.abort();
    headmanMutationController = null;
    headmanMutationPending.value = false;
    closePromotion();
    promotionMutationRevision += 1;
    promotionMutationController?.abort();
    promotionMutationController = null;
    promotionMutationPending.value = false;
    groups.value = [];
    counts.value = { active: 0, draft: 0, archived: 0 };
    totalElements.value = 0;
    totalPages.value = 0;
    page.value = 0;
    loading.value = false;
    formVisible.value = false;
    filtersVisible.value = false;
    archiveGroupConfirmation.value = null;
    archiveGroupSaving.value = false;
    expandedGroupId.value = null;
    saving.value = false;
    error.value = null;
    notice.value = null;
    if (canView.value) void refresh();
  },
  { flush: 'sync' },
);

onBeforeUnmount(() => {
  disposed = true;
  restoreMutationRevision += 1;
  restoreMutationController?.abort();
  restoreMutationController = null;
  listRevision += 1;
  listController?.abort();
  listController = null;
  headmanContextRevision += 1;
  headmanRevision += 1;
  headmanController?.abort();
  headmanController = null;
  promotionContextRevision += 1;
  promotionPreviewRevision += 1;
  promotionPreviewController?.abort();
  promotionPreviewController = null;
  promotionMutationRevision += 1;
});
</script>

<template>
  <main
    class="admin-groups-screen"
    :data-theme="theme"
    :data-admin-groups-view="
      renameGroup
        ? 'rename'
        : filtersVisible
          ? 'filters'
          : archiveGroupConfirmation
            ? 'archive-confirm'
            : formVisible
              ? 'create'
              : assignmentGroup
                ? 'headman'
                : promotionPanelVisible
                  ? 'promotion'
                  : 'list'
    "
    aria-labelledby="admin-groups-title"
  >
    <header class="admin-groups-screen__header">
      <button
        v-if="taskActive"
        class="admin-groups-back"
        type="button"
        aria-label="Назад"
        @click="back"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="admin-groups-title">
        {{
          renameGroup
            ? 'Исправить группу'
            : filtersVisible
              ? 'Фильтры групп'
              : archiveGroupConfirmation
                ? 'Архивировать группу'
                : formVisible
                  ? 'Создать группу'
                  : assignmentGroup
                    ? 'Назначить старосту'
                    : promotionPanelVisible
                      ? 'Перевод групп'
                      : 'Группы'
        }}
      </h1>
    </header>
    <AdminNotice
      v-if="!canView"
      tone="warning"
      title="Нет доступа"
      class="admin-groups-state"
    >
      Просмотр групп недоступен с текущими правами.
    </AdminNotice>
    <AdminNotice
      v-if="error"
      tone="danger"
      title="Не удалось выполнить действие"
      class="admin-groups-state--error"
    >
      {{ error }}
    </AdminNotice>
    <AdminNotice
      v-if="notice"
      tone="success"
      :title="promotionResult ? 'Перевод завершён' : 'Готово'"
      class="admin-groups-state--success"
    >
      {{ notice }}
    </AdminNotice>
    <form
      v-if="filtersVisible"
      class="admin-groups-form"
      @submit.prevent="applyFilters"
    >
      <div class="admin-groups-filter-field">
        <span id="admin-group-course-label">Курс</span>
        <button
          type="button"
          class="admin-groups-selector"
          aria-labelledby="admin-group-course-label admin-group-course-value"
          :aria-expanded="expandedFilter === 'course'"
          @click="expandedFilter = expandedFilter === 'course' ? null : 'course'"
        >
          <span id="admin-group-course-value">{{
            filterCourse === null ? 'Все курсы' : filterCourse + ' курс'
          }}</span
          ><MobileIcon name="chevron-down" />
        </button>
        <div
          v-if="expandedFilter === 'course'"
          class="admin-groups-options"
          data-filter-options="course"
        >
          <button
            type="button"
            :aria-pressed="filterCourse === null"
            @click="
              filterCourse = null;
              expandedFilter = null;
            "
          >
            Все курсы </button
          ><button
            v-for="value in 5"
            :key="value"
            type="button"
            :aria-pressed="filterCourse === value"
            @click="
              filterCourse = value;
              expandedFilter = null;
            "
          >
            {{ value }} курс
          </button>
        </div>
      </div>
      <div class="admin-groups-filter-field">
        <span id="admin-group-program-label">Тип программы</span>
        <button
          type="button"
          class="admin-groups-selector"
          aria-labelledby="admin-group-program-label admin-group-program-value"
          :aria-expanded="expandedFilter === 'program'"
          @click="expandedFilter = expandedFilter === 'program' ? null : 'program'"
        >
          <span id="admin-group-program-value">{{ programTypeLabel(filterProgramType) }}</span
          ><MobileIcon name="chevron-down" />
        </button>
        <div
          v-if="expandedFilter === 'program'"
          class="admin-groups-options"
          data-filter-options="program"
        >
          <button
            type="button"
            :aria-pressed="filterProgramType === null"
            @click="
              filterProgramType = null;
              expandedFilter = null;
            "
          >
            Все типы программ </button
          ><button
            v-for="program in adminGroupProgramTypes"
            :key="program.digit"
            type="button"
            :aria-pressed="filterProgramType === program.digit"
            @click="
              filterProgramType = program.digit;
              expandedFilter = null;
            "
          >
            {{ program.digit }} · {{ program.label }}
          </button>
        </div>
      </div>
      <div class="admin-groups-filter-field">
        <span id="admin-group-sort-label">Сортировка</span>
        <button
          type="button"
          class="admin-groups-selector"
          aria-labelledby="admin-group-sort-label admin-group-sort-value"
          :aria-expanded="expandedFilter === 'sort'"
          @click="expandedFilter = expandedFilter === 'sort' ? null : 'sort'"
        >
          <span id="admin-group-sort-value">{{
            sortOptions.find((item) => item.value === filterSort)?.label
          }}</span
          ><MobileIcon name="chevron-down" />
        </button>
        <div
          v-if="expandedFilter === 'sort'"
          class="admin-groups-options"
          data-filter-options="sort"
        >
          <button
            v-for="option in sortOptions"
            :key="option.value ?? 'default'"
            type="button"
            :aria-pressed="filterSort === option.value"
            @click="
              filterSort = option.value;
              expandedFilter = null;
            "
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      <div class="admin-groups-filter-field">
        <span id="admin-group-status-label">Статус группы</span>
        <button
          type="button"
          class="admin-groups-selector"
          aria-labelledby="admin-group-status-label admin-group-status-value"
          :aria-expanded="expandedFilter === 'status'"
          @click="expandedFilter = expandedFilter === 'status' ? null : 'status'"
        >
          <span id="admin-group-status-value">{{
            tabs.find((tab) => tab.value === filterStatus)?.label
          }}</span
          ><MobileIcon name="chevron-down" />
        </button>
        <div
          v-if="expandedFilter === 'status'"
          class="admin-groups-options"
          data-filter-options="status"
        >
          <button
            v-for="tab in tabs"
            :key="tab.value"
            type="button"
            :aria-pressed="filterStatus === tab.value"
            @click="
              filterStatus = tab.value;
              expandedFilter = null;
            "
          >
            {{ tab.label }}
          </button>
        </div>
      </div>
      <button
        class="admin-groups-action"
        type="submit"
      >
        Применить
      </button>
    </form>
    <section
      v-if="archiveGroupConfirmation"
      class="admin-groups-headman"
    >
      <div class="admin-groups-archive-summary">
        <h2>{{ displayCode(archiveGroupConfirmation) }}</h2
        ><p>{{ studentCountLabel(archiveGroupConfirmation.studentCount) }}</p
        ><p
          >{{ archiveGroupConfirmation.status === 'DRAFT' ? 'Черновик' : 'Активная группа' }}{{ " → Архив" }}</p
        > </div
      ><p
        >{{ "Группа и её история сохранятся. Группа станет архивной; изменить её можно будет после восстановления." }}</p
      >
      <button
        class="admin-groups-action"
        type="button"
        :disabled="archiveGroupSaving"
        @click="confirmGroupArchive"
      >
        {{ archiveGroupSaving ? 'Архивируем…' : 'Подтвердить архивацию' }}
      </button>
      <button
        class="admin-groups-table__action"
        type="button"
        :disabled="archiveGroupSaving"
        @click="back"
      >
        Отмена
      </button>
    </section>
    <form
      v-if="formVisible"
      class="admin-groups-form"
      aria-label="Новая группа"
      @submit.prevent="createGroup"
    >
      <label
        ><span>Буквенный код</span
        ><input
          v-model="alphabeticCode"
          maxlength="4"
          required
          type="text"
          autocomplete="off"
          :disabled="saving"
      /></label>
      <label
        ><span>Цифровой код</span
        ><input
          v-model="numericCode"
          inputmode="numeric"
          maxlength="3"
          pattern="\d{3}"
          required
          type="text"
          autocomplete="off"
          :disabled="saving"
      /></label>
      <label
        ><span>Срок обучения, лет</span
        ><input
          v-model="trainingDurationYears"
          min="1"
          required
          type="number"
          :disabled="saving"
      /></label>
      <p class="admin-groups-form__hint">
        Курс определяется первой цифрой цифрового кода. Новая группа появится в черновиках до
        назначения старосты.
      </p>
      <button
        class="admin-groups-action admin-groups-form__submit"
        type="submit"
        :disabled="saving"
        :aria-busy="saving"
      >
        <MobileIcon name="plus" />{{ saving ? 'Сохраняем…' : 'Создать группу' }}
      </button>
    </form>
    <section
      v-if="!taskActive && canView"
      aria-label="Реестр групп"
    >
      <div
        v-if="canManage"
        class="admin-groups-management"
      >
        <button
          class="admin-groups-action admin-groups-create"
          type="button"
          @click="
            formVisible = true;
            error = null;
            notice = null;
          "
        >
          <MobileIcon name="plus" />Создать группу </button
        ><button
          v-if="status !== 'ARCHIVED'"
          class="admin-groups-table__action"
          type="button"
          :disabled="promotionMutationPending || counts.active + counts.draft === 0"
          @click="openPromotion()"
        >
          Перевести все группы на следующий курс
        </button>
      </div>
      <div
        class="admin-groups-tabs"
        role="tablist"
        aria-label="Статус группы"
      >
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="status === tab.value"
          class="admin-groups-tabs__item"
          :class="{ 'admin-groups-tabs__item--active': status === tab.value }"
          @click="selectStatus(tab.value)"
        >
          {{ tab.label }}
          <span v-if="counts.active + counts.draft + counts.archived > 0">{{
            statusCount(tab.value)
          }}</span>
        </button>
      </div>
      <form
        class="admin-groups-search"
        role="search"
        @submit.prevent="submitSearch"
      >
        <label
          class="admin-groups-visually-hidden"
          for="admin-groups-search-input"
          >Найти группу</label
        ><button
          type="submit"
          aria-label="Найти группу"
        >
          <MobileIcon name="search" />
        </button>
        <input
          id="admin-groups-search-input"
          v-model="search"
          type="search"
          placeholder="Найти группу"
          autocomplete="off"
          @change="submitSearch"
        />
        <button
          v-if="search"
          type="button"
          aria-label="Очистить поиск"
          @click="
            search = '';
            submitSearch();
          "
        >
          ×
        </button>
      </form>
      <ExportButton
        :port="reportDownload"
        :formats="() => client.exportFormats()"
        :request="exportRequest"
        :scope-key="exportScope"
        :disabled="!canView"
        @error="emit('ownerError', $event)"
      />
      <div class="admin-groups-filterbar">
        <button
          type="button"
          class="admin-groups-table__action"
          @click="openFilters"
        >
          Фильтры и сортировка </button
        ><button
          type="button"
          class="admin-groups-table__action"
          aria-label="Сбросить фильтры и поиск"
          @click="resetFilters"
        >
          ×
        </button>
      </div>
      <p
        v-if="totalElements > 0"
        class="admin-groups-total"
      >
        Всего: {{ totalElements }}
      </p>
      <p
        v-if="loading"
        class="admin-groups-state"
        role="status"
      >
        Загружаем группы…
      </p>
      <div
        v-else-if="groups.length === 0"
        class="admin-groups-empty"
      >
        <h2>{{ search ? 'Ничего не найдено' : 'Групп пока нет' }}</h2
        ><p>{{
          search ? 'Измени поиск и попробуй снова.' : 'Создай группу — она появится в черновиках.'
        }}</p>
      </div>
      <div
        v-else
        class="admin-groups-list"
      >
        <article
          v-for="group in groups"
          :key="group.id"
          class="admin-groups-item"
          :data-group-id="group.id"
        >
          <button
            type="button"
            class="admin-groups-item__trigger"
            :aria-expanded="expandedGroupId === group.id"
            :aria-controls="'admin-group-' + group.id"
            @click="expandedGroupId = expandedGroupId === group.id ? null : group.id"
          >
            <span
              ><strong>{{ displayCode(group) }}</strong
              ><span
                v-if="expandedGroupId !== group.id"
                class="admin-groups-item__summary"
                >{{
                  status === 'DRAFT'
                    ? (group.draftReason ?? 'Группа только создана')
                    : (status === 'ARCHIVED' ? 'Архив · ' : '') +
                      (group.currentCourse ?? '—') +
                      ' курс · ' +
                      studentCountLabel(group.studentCount)
                }}</span
              ><span
                v-if="status === 'DRAFT' && expandedGroupId !== group.id"
                class="admin-groups-item__summary"
                >Староста не назначен</span
              ></span
            >
            <MobileIcon
              name="chevron-down"
              :class="{ 'admin-groups-item__chevron--open': expandedGroupId === group.id }"
            />
          </button>
          <div
            v-if="expandedGroupId === group.id"
            :id="'admin-group-' + group.id"
            class="admin-groups-item__details"
          >
            <dl
              ><div
                ><dt>Курс</dt><dd>{{ group.currentCourse ?? '—' }}</dd></div
              ><div
                ><dt>Срок обучения</dt><dd>{{ durationLabel(group) }}</dd></div
              ><div
                ><dt>Студентов</dt><dd>{{ group.studentCount }}</dd></div
              ><div
                ><dt>Староста</dt><dd>{{ group.headmanFio ?? 'Не назначен' }}</dd></div
              ></dl
            >
            <p
              v-if="status === 'DRAFT'"
              class="admin-groups-form__hint"
            >
              {{ group.draftReason ?? 'Назначь старосту, чтобы активировать группу.' }}
            </p>
            <div
              v-if="canManage"
              class="admin-groups-item__actions"
            >
              <div class="admin-groups-item__action-options">
                <template v-if="status !== 'ARCHIVED'">
                  <button
                    class="admin-groups-table__action"
                    type="button"
                    @click="openHeadman(group)"
                  >
                    {{ group.headmanFio ? 'Изменить старосту' : 'Назначить старосту' }} </button
                  ><button
                    class="admin-groups-table__action"
                    type="button"
                    :disabled="promotionMutationPending"
                    @click="openPromotion(group)"
                  >
                    Перевести на следующий курс </button
                  ><button
                    class="admin-groups-table__action"
                    type="button"
                    :disabled="archiveGroupSaving"
                    @click="archiveGroupConfirmation = group"
                  >
                    Архивировать
                  </button>
                </template>
                <button
                  v-else
                  class="admin-groups-table__action"
                  type="button"
                  :disabled="restoreSavingId !== null || restoreRefreshPendingIds.has(group.id)"
                  :aria-busy="restoreSavingId === group.id"
                  @click="restoreGroup(group)"
                >
                  {{
                    restoreSavingId === group.id
                      ? 'Восстанавливаем…'
                      : restoreRefreshPendingIds.has(group.id)
                        ? 'Восстановлена'
                        : 'Восстановить группу'
                  }}
                </button>
              </div>
            </div>
          </div>
        </article>
      </div>
      <footer
        v-if="!loading && totalElements > 0"
        class="admin-groups-pagination"
      >
        <button
          type="button"
          aria-label="Предыдущая страница"
          :disabled="page === 0"
          @click="changePage(page - 1)"
        >
          <MobileIcon name="back" /> </button
        ><span
          >{{ totalElements ? page * 3 + 1 : 0 }}–{{
            Math.min(page * 3 + groups.length, totalElements)
          }}
          из {{ totalElements }}</span
        ><button
          type="button"
          aria-label="Следующая страница"
          :disabled="totalPages === 0 || page + 1 >= totalPages"
          @click="changePage(page + 1)"
        >
          <MobileIcon
            name="back"
            class="admin-groups-next"
          />
        </button>
      </footer>
    </section>
    <section
      v-if="promotionPanelVisible && !renameGroup"
      id="admin-groups-promotion-panel"
      class="admin-groups-promotion"
      aria-labelledby="admin-groups-promotion-title"
    >
      <div class="admin-groups-promotion__header">
        <div>
          <h2 id="admin-groups-promotion-title">
            {{
              promotionScopeGroup
                ? 'Перевод группы ' + displayCode(promotionScopeGroup)
                : 'Предпросмотр массового перевода'
            }}
          </h2>
          <p>Проверь изменения перед переводом.</p>
        </div>
        <button
          class="admin-groups-headman__close admin-groups-promotion__refresh"
          type="button"
          :disabled="promotionMutationPending"
          @click="closePromotion()"
        >
          Закрыть
        </button>
      </div>

      <p
        v-if="promotionLoading"
        class="admin-groups-state"
        role="status"
      >
        Считаем последствия…
      </p>
      <AdminNotice
        v-if="promotionError"
        tone="danger"
        title="Перевод не выполнен"
        class="admin-groups-promotion__error"
      >
        <p>{{ promotionError }}</p
        ><template #actions>
          <button
            v-if="!promotionMutationPending"
            class="admin-groups-table__action"
            type="button"
            @click="loadPromotionPreview"
          >
            Подготовить предпросмотр
          </button>
        </template>
      </AdminNotice>

      <div
        v-if="(promotionResult ?? promotionPreview)?.conflicts.length"
        class="admin-groups-item__action-options"
      >
        <button
          class="admin-groups-table__action"
          type="button"
          @click="downloadPromotionConflicts"
        >
          Скачать отчёт о конфликтах
        </button>
        <button
          class="admin-groups-table__action"
          type="button"
          :disabled="promotionMutationPending || promotionLoading"
          @click="loadPromotionPreview"
        >
          Обновить предпросмотр
        </button>
      </div>

      <section
        v-if="promotionPreview"
        class="admin-groups-promotion__details"
        role="status"
      >
        <AdminNotice
          tone="warning"
          title="Цикл перевода"
          class="admin-groups-promotion__cycle"
        >
          Весенний семестр завершился
          {{ promotionPreview.cycleDateTo.split('-').reverse().join('.') }}. Группы, уже
          обработанные в этом цикле, останутся без изменений.
        </AdminNotice>
        <dl class="admin-groups-promotion__counts">
          <div>
            <dt>Перейдут на курс выше</dt>
            <dd class="admin-groups-promotion__count-values">
              <span class="admin-groups-promotion__count-value"
                ><strong>{{ promotionPreview.promoteCount }}</strong> <small>Группы</small></span
              >
              <!-- Preserve the literal space between this count and its inline label. -->
              <!-- prettier-ignore -->
              <span class="admin-groups-promotion__count-value"><strong>{{ promotionPreview.promotedStudentCount }}</strong> <small>Студенты</small></span>
            </dd>
          </div>
          <div>
            <dt>Уйдут в архив</dt>
            <dd class="admin-groups-promotion__count-values">
              <span class="admin-groups-promotion__count-value"
                ><strong>{{ promotionPreview.archiveCount }}</strong> <small>Группы</small></span
              >
              <!-- Preserve the literal space between this count and its inline label. -->
              <!-- prettier-ignore -->
              <span class="admin-groups-promotion__count-value"><strong>{{ promotionPreview.archivedStudentCount }}</strong> <small>Студенты</small></span>
            </dd>
          </div>
          <div>
            <dt>Пропущено</dt>
            <dd class="admin-groups-promotion__count-values">
              <!-- Preserve the literal space between this count and its inline label. -->
              <!-- prettier-ignore -->
              <span class="admin-groups-promotion__count-value"><strong>{{ promotionPreview.skippedGroupCount }}</strong> <small>Группы</small></span>
              <!-- Preserve the literal space between this count and its inline label. -->
              <!-- prettier-ignore -->
              <span class="admin-groups-promotion__count-value"><strong>{{ promotionPreview.skippedStudentCount }}</strong> <small>Студенты</small></span>
            </dd>
          </div>
          <div
            ><dt>Конфликтные префиксы</dt><dd>{{ promotionPreview.conflictCount }}</dd></div
          >
        </dl>

        <section
          v-if="promotionPreview.toPromote.length"
          aria-labelledby="admin-groups-promotion-next-title"
        >
          <h3 id="admin-groups-promotion-next-title"> Перевод на следующий курс </h3>
          <ul class="admin-groups-promotion__list">
            <li
              v-for="item in promotionPreview.toPromote"
              :key="'promote-' + item.id"
            >
              <strong>{{ item.from }} → {{ item.to }}</strong>
              <span>{{ studentCountLabel(item.studentCount) }}</span>
            </li>
          </ul>
        </section>

        <section
          v-if="promotionPreview.toArchive.length"
          aria-labelledby="admin-groups-promotion-archive-title"
        >
          <h3 id="admin-groups-promotion-archive-title"> Архивация выпускных групп </h3>
          <ul class="admin-groups-promotion__list">
            <li
              v-for="item in promotionPreview.toArchive"
              :key="'archive-' + item.id"
            >
              <strong>{{ item.from }} — выпуск</strong>
              <span>{{ studentCountLabel(item.studentCount) }}</span>
            </li>
          </ul>
        </section>

        <section
          v-if="promotionPreview.skipped.length"
          aria-labelledby="admin-groups-promotion-skipped-title"
        >
          <h3 id="admin-groups-promotion-skipped-title"> Группы без изменений </h3>
          <ul class="admin-groups-promotion__list">
            <li
              v-for="item in promotionPreview.skipped"
              :key="'skip-' + item.id"
            >
              <strong>{{ item.name }}</strong>
              <span>
                {{
                  item.reason === 'ALREADY_PROCESSED'
                    ? 'Уже обработана в этом цикле'
                    : 'Создана после окончания семестра'
                }}
                · {{ studentCountLabel(item.studentCount) }}
              </span>
            </li>
          </ul>
        </section>

        <section
          v-if="promotionPreview.conflicts.length"
          aria-labelledby="admin-groups-promotion-conflicts-title"
        >
          <h3 id="admin-groups-promotion-conflicts-title"> Нужна проверка данных </h3>
          <ul class="admin-groups-promotion__list">
            <li
              v-for="(conflict, index) in promotionPreview.conflicts"
              :key="conflict.prefix + '-' + conflict.reason + '-' + index"
            >
              <strong>{{ conflict.prefix || 'Группа' }}</strong>
              <span>{{ conflict.message }}</span>
              <span>{{ conflictResolution(conflict) }}</span>
              <button
                v-for="group in correctionGroups(conflict)"
                :key="group.id"
                type="button"
                class="admin-groups-table__action"
                :disabled="renameSaving || promotionMutationPending"
                @click="openRename(group)"
              >
                Исправить название {{ group.name }}
              </button>
            </li>
          </ul>
        </section>

        <button
          class="admin-groups-action admin-groups-promotion__confirm"
          type="button"
          :disabled="
            promotionMutationPending ||
            promotionLoading ||
            (promotionPreview.toPromote.length === 0 && promotionPreview.toArchive.length === 0)
          "
          @click="requestPromotionConfirmation"
        >
          Подтвердить показанный план
        </button>
      </section>

      <section
        v-if="promotionResult"
        class="admin-groups-promotion__result"
        role="status"
        aria-labelledby="admin-groups-promotion-result-title"
      >
        <h3 id="admin-groups-promotion-result-title"> История перевода </h3>
        <ul class="admin-groups-promotion__list">
          <li
            v-for="item in promotionResult.toPromote"
            :key="'result-promote-' + item.id"
          >
            <strong>{{ item.from }} → {{ item.to }}</strong>
            <span>{{ studentCountLabel(item.studentCount) }}</span>
          </li>
          <li
            v-for="item in promotionResult.toArchive"
            :key="'result-archive-' + item.id"
          >
            <strong>{{ item.from }} — отправлена в архив</strong>
            <span>{{ studentCountLabel(item.studentCount) }}</span>
          </li>
        </ul>
        <section
          v-if="promotionResult.skipped.length"
          aria-labelledby="admin-groups-promotion-result-skipped-title"
        >
          <h4 id="admin-groups-promotion-result-skipped-title"> Группы без изменений </h4>
          <ul class="admin-groups-promotion__list">
            <li
              v-for="item in promotionResult.skipped"
              :key="'result-skip-' + item.id"
            >
              <strong>{{ item.name }}</strong>
              <span>
                {{
                  item.reason === 'ALREADY_PROCESSED'
                    ? 'Уже обработана в этом цикле'
                    : 'Создана после окончания семестра'
                }}
                · {{ studentCountLabel(item.studentCount) }}
              </span>
            </li>
          </ul>
        </section>
        <p
          v-if="promotionResult.conflicts.length"
          class="admin-groups-state"
        >
          Пропущено конфликтов: {{ promotionResult.conflictCount }}.
        </p>
      </section>
    </section>

    <form
      v-if="renameGroup"
      class="admin-groups-form"
      @submit.prevent="saveRename"
    >
      <p class="admin-groups-form__hint">
        Группа {{ renameGroup.name }} · срок обучения
        {{
          renameGroup.trainingDurationYears === null
            ? 'не указан'
            : renameGroup.trainingDurationYears + ' лет'
        }}
      </p>
      <label for="admin-groups-rename-name"
        ><span>Исправить название</span>
        <input
          id="admin-groups-rename-name"
          v-model="renameName"
          :disabled="renameSaving || renameUnknown"
          :aria-invalid="!!renameError || undefined"
          :aria-describedby="renameError ? 'admin-groups-rename-error' : undefined"
          required
          autocomplete="off"
        />
      </label>
      <label for="admin-groups-rename-duration"
        ><span>Срок обучения, лет</span>
        <input
          id="admin-groups-rename-duration"
          v-model="renameDuration"
          type="number"
          min="1"
          max="5"
          step="1"
          inputmode="numeric"
          :disabled="renameSaving || renameUnknown"
          required
        />
      </label>
      <p class="admin-groups-form__hint">
        Формат названия: УИТ-311. Курс 1–5, тип программы 1–9; курс не выше срока обучения.
      </p>
      <p
        v-if="renameError"
        id="admin-groups-rename-error"
        class="admin-groups-state--error"
        role="alert"
      >
        {{ renameError }}
      </p>
      <button
        v-if="renameUnknown"
        type="button"
        class="admin-groups-action"
        :disabled="renameSaving"
        @click="reconcileRename"
      >
        Проверить название в реестре
      </button>
      <button
        v-else
        type="submit"
        class="admin-groups-action"
        :disabled="!renameValid || renameSaving"
      >
        {{ renameSaving ? 'Сохраняем…' : 'Сохранить название' }}
      </button>
    </form>

    <section
      v-if="assignmentGroup"
      class="admin-groups-headman"
      aria-labelledby="admin-groups-headman-title"
    >
      <div class="admin-groups-headman__header">
        <div>
          <h2 id="admin-groups-headman-title">
            Староста группы {{ displayCode(assignmentGroup) }}
          </h2>
          <p
            v-if="headmanRoster"
            class="admin-groups-headman__meta"
          >
            Текущий староста: {{ headmanRoster.currentHeadmanFio ?? 'не назначен' }} · активных
            помощников: {{ headmanRoster.activeAssistantCount }}
          </p>
        </div>
        <button
          class="admin-groups-headman__close"
          type="button"
          @click="closeHeadman()"
        >
          Закрыть
        </button>
      </div>

      <p
        v-if="headmanLoading"
        class="admin-groups-state"
        role="status"
      >
        Загружаем состав…
      </p>
      <form
        v-else-if="headmanRoster"
        class="admin-groups-headman__form"
        @submit.prevent="confirmHeadman"
      >
        <div class="admin-groups-headman__current">
          <span>Текущий староста</span
          ><strong>{{ headmanRoster.currentHeadmanFio ?? 'Не назначен' }}</strong
          ><span>{{ headmanRoster.activeAssistantCount }} помощника</span>
        </div>
        <label for="admin-groups-headman-select">Выбери старосту</label>
        <select
          id="admin-groups-headman-select"
          v-model.number="selectedStudentId"
          @change="loadPreview"
        >
          <option
            :value="null"
            disabled
          >
            Выбери студента
          </option>
          <option
            v-for="candidate in headmanRoster.candidates"
            :key="candidate.id"
            :value="candidate.id"
          >
            {{ candidate.fio }}{{ candidate.current ? ' — текущий староста' : '' }}
          </option>
        </select>
        <p
          v-if="assignmentPreview"
          class="admin-groups-headman__preview"
          role="status"
        >
          <template v-if="assignmentPreview.sameHeadman">
            Этот студент уже староста. Изменений не будет.
          </template>
          <template v-else>
            После подтверждения
            {{
              assignmentPreview.activatesDraft
                ? 'группа станет активной'
                : 'староста будет заменён'
            }}. Помощников к отзыву: {{ assignmentPreview.assistantsToRevoke }}.
          </template>
        </p>
        <button
          v-if="!assignmentPreview?.sameHeadman"
          class="admin-groups-action"
          type="submit"
          :disabled="
            headmanSaving ||
            headmanMutationPending ||
            selectedStudentId === null ||
            assignmentPreview === null
          "
          :aria-busy="headmanSaving"
        >
          {{ headmanSaving ? 'Сохраняем…' : 'Подтвердить назначение' }}
        </button>
      </form>
    </section>

    <dialog
      ref="promotionConfirmDialog"
      class="admin-groups-promotion__dialog"
      aria-labelledby="admin-groups-promotion-confirm-title"
      aria-describedby="admin-groups-promotion-confirm-detail"
    >
      <h2 id="admin-groups-promotion-confirm-title"> Подтверди перевод групп </h2>
      <p
        v-if="promotionPreview"
        id="admin-groups-promotion-confirm-detail"
      >
        {{
          promotionScopeGroup ? 'Группа ' + displayCode(promotionScopeGroup) : 'В выбранном цикле'
        }}: весенний семестр завершился
        {{ promotionPreview.cycleDateTo.split('-').reverse().join('.') }}. К переводу:
        {{ groupCountLabel(promotionPreview.promoteCount) }} ({{
          studentCountLabel(promotionPreview.promotedStudentCount)
        }}). В архив: {{ groupCountLabel(promotionPreview.archiveCount) }} ({{
          studentCountLabel(promotionPreview.archivedStudentCount)
        }}). Без изменений: {{ groupCountLabel(promotionPreview.skippedGroupCount) }} ({{
          studentCountLabel(promotionPreview.skippedStudentCount)
        }}), конфликтных префиксов: {{ promotionPreview.conflictCount }}. Пропущенные группы
        останутся без изменений.
      </p>
      <div class="admin-groups-promotion__dialog-actions">
        <button
          class="admin-groups-table__action admin-groups-promotion__dialog-cancel"
          type="button"
          :disabled="promotionMutationPending"
          @click="promotionConfirmDialog?.close()"
        >
          Назад
        </button>
        <button
          class="admin-groups-action admin-groups-promotion__dialog-confirm"
          type="button"
          :disabled="promotionMutationPending || promotionPreview === null"
          :aria-busy="promotionSaving"
          @click="confirmPromotion"
        >
          {{ promotionSaving ? 'Сохраняем…' : 'Подтвердить перевод' }}
        </button>
      </div>
    </dialog>
  </main>
</template>
