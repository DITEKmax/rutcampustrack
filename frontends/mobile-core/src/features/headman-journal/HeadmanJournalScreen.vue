<script setup lang="ts">
import {
  exportStateLabel,
  reportDownloadAcceptedMessage,
  validateTicketRequest,
} from '../../shared/report-download-client';
import {
  defineAsyncComponent,
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch,
} from 'vue';
import { HeadmanManualPreview, type ManualPreviewState } from './headman-manual-preview';
const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../shared/components/AttachmentPdfPreview.vue'),
);
import HeadmanLinkedRequest from './HeadmanLinkedRequest.vue';
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import MobileToolsSheet from '../../shared/components/MobileToolsSheet.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { journalScopeCurrent, type JournalMutationScope } from './headman-journal-operation';
import { moscowDate } from '../../domain/homework';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import type {
  ReportDownloadPort,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client';
import {
  openRequestAttachmentPopup,
  runRequestAttachmentOpen,
  type RequestAttachmentPopup,
} from '../requests/request-attachment-action';
import type { RequestAttachmentViewState } from '../requests/types';
import {
  HeadmanJournalApiError,
  createHeadmanLessonTransferRequest,
  type HeadmanJournalApi,
  type HeadmanJournalAttendanceStatus,
  type HeadmanJournalMarkCondition,
  type HeadmanJournalExcuseType,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
  type HeadmanLessonTransferErrorCode,
  type HeadmanLessonTransferRequest,
  type HeadmanLessonTransferResponse,
  type HeadmanWeeklyExportOptions,
  toHeadmanWeeklyReportRequest,
} from './headman-journal-client';
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client';
import type {
  HeadmanSubjectsApi,
  HeadmanSubject,
} from '../headman-subjects/headman-subjects-client';
import './headman-journal-screen.pcss';

const props = withDefaults(
  defineProps<{
    api: HeadmanJournalApi | null;
    groupId: number | null;
    subjectsApi?: HeadmanSubjectsApi | null;
    requestsApi?: HeadmanRequestsApi | null;
    assistantPermissions?: readonly HeadmanAssistantPermission[] | null;
    offline?: boolean;
    readOnly?: boolean;
    reportDownload?: ReportDownloadPort | null;
    initialDate?: string;
    initialLessonId?: number | null;
  }>(),
  {
    subjectsApi: null,
    requestsApi: null,
    assistantPermissions: null,
    offline: false,
    readOnly: false,
    reportDownload: null,
    initialDate: '',
    initialLessonId: null,
  },
);

const emit = defineEmits<{
  error: [cause: unknown];
  taskChange: [active: boolean];
}>();

const todayIso = (): string => {
  const value = moscowDate(new Date().toISOString());
  if (value === null) throw new Error('Не удалось определить дату журнала.');
  return value;
};
const selectedDate = ref(props.initialDate || todayIso());
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([]);
const report = shallowRef<HeadmanJournalReport | null>(null);
const selectedLessonId = ref<number | null>(null);
const loadingLessons = ref(false);
const loadingReport = ref(false);
const error = ref<string | null>(null);
const reportAccessDenied = ref(false);
const notice = ref<string | null>(null);
const pendingUserId = ref<number | null>(null);
const toolsSheet = ref<InstanceType<typeof MobileToolsSheet> | null>(null);
const weeklySheet = ref<InstanceType<typeof MobileToolsSheet> | null>(null);
const toolsOpen = ref(false);
const weeklyOpen = ref(false);
const rowErrors = ref<Record<number, string>>({});
const clearConfirmationDialog = ref<HTMLDialogElement | null>(null);
const clearConfirmation = shallowRef<{
  scope: JournalMutationScope;
  entry: HeadmanJournalRosterEntry;
} | null>(null);
let mutationGeneration = 0;
function mutationScope(): JournalMutationScope | null {
  if (!props.api || selectedLessonId.value === null) return null;
  return {
    api: props.api,
    groupId: props.groupId,
    lessonId: selectedLessonId.value,
    generation: mutationGeneration,
  };
}
function mutationCurrent(scope: JournalMutationScope): boolean {
  return (
    !disposed && journalScopeCurrent(scope, mutationScope()) && !props.offline && !props.readOnly
  );
}
function closeClearConfirmation(): void {
  clearConfirmationDialog.value?.close();
  clearConfirmation.value = null;
}
function requestClearExcuse(entry: HeadmanJournalRosterEntry): void {
  const scope = mutationScope();
  if (
    !scope ||
    !canClearEntry(entry) ||
    !canWriteExcuse(entry) ||
    pendingUserId.value !== null ||
    entry.status !== 'EXCUSED'
  )
    return;
  void clearExcuse(entry);
}
async function confirmClearExcuse(): Promise<void> {
  const confirmation = clearConfirmation.value;
  if (
    !confirmation ||
    !mutationCurrent(confirmation.scope) ||
    !entries.value.includes(confirmation.entry) ||
    !canClearEntry(confirmation.entry) ||
    !canWriteExcuse(confirmation.entry)
  ) {
    closeClearConfirmation();
    return;
  }
  closeClearConfirmation();
  await clearExcuse(confirmation.entry);
}
const linkedTicketEntry = shallowRef<HeadmanJournalRosterEntry | null>(null);
const editingUserId = ref<number | null>(null);
const markControls = new Map<string, { focus(): void }>();
function rememberMarkControl(key: string, element: unknown): void {
  if (element && typeof (element as HTMLElement).focus === 'function')
    markControls.set(key, element as HTMLElement);
  else markControls.delete(key);
}
watch(report, () => {
  editingUserId.value = null;
});
const lessonNotStarted = computed(
  () => selectedLesson.value?.status === 'PLANNED' && report.value?.editable === false,
);
function openMarkChoices(entry: HeadmanJournalRosterEntry): void {
  if (pendingUserId.value !== null || !canWrite(entry) || !entry.markRevision) return;
  editingUserId.value = entry.userId;
  void nextTick(() => markControls.get(`${entry.userId}:${entry.status}`)?.focus());
}
const excuseUserId = ref<number | null>(null);
const excuseType = ref<HeadmanJournalExcuseType | ''>('');
const excuseComment = ref('');
const excuseFile = ref<File | null>(null);
const cancelReason = ref('');
const lessonActionBusy = ref(false);
type LessonActionConfirmation = {
  action: 'cancel' | 'restore' | 'transfer';
  api: HeadmanJournalApi;
  groupId: number | null;
  lessonId: number;
  lessonDate: string;
  lessonNumber: number | null;
  permissions: readonly HeadmanAssistantPermission[] | null;
  reason: string | null;
  transferRequest: HeadmanLessonTransferRequest | null;
};
type TransferOperationPhase =
  | 'submitting'
  | 'pending'
  | 'checking'
  | 'uncertain'
  | 'completed'
  | 'error';
type TransferOperation = {
  readonly key: string;
  readonly api: HeadmanJournalApi;
  readonly groupId: number;
  readonly sourceLessonId: number;
  readonly sourceDate: string;
  readonly targetDate: string | null;
  readonly targetLessonNumber: number | null;
  readonly request: HeadmanLessonTransferRequest | null;
  readonly operationId: string | null;
  readonly response: HeadmanLessonTransferResponse | null;
  readonly phase: TransferOperationPhase;
  readonly message: string | null;
};
const lessonActionConfirmationDialog = ref<HTMLDialogElement | null>(null);
const pendingLessonActionConfirmation = shallowRef<LessonActionConfirmation | null>(null);
const transferTaskOpen = ref(false);
const transferTargetDate = ref('');
const transferTargetLessonNumber = ref<number | null>(null);
const transferTargetLessons = shallowRef<readonly HeadmanJournalLesson[]>([]);
const transferTargetLessonsDate = ref<string | null>(null);
const transferTargetLoading = ref(false);
const transferTargetError = ref<string | null>(null);
const transferOperations = shallowRef<readonly TransferOperation[]>([]);
const attachmentStates = ref<Readonly<Record<number, RequestAttachmentViewState>>>({});
const attachmentOwnerIdentity = ref<string | null>(props.api ? 'headman-journal' : null);
const attachmentOwnerGeneration = ref(0);
const weeklyExportOptions = shallowRef<HeadmanWeeklyExportOptions | null>(null);
const weeklyExportFormatCode = ref('');
const selectedWeeklyStarts = ref<readonly string[]>([]);
const loadingWeeklyOptions = ref(false);
const weeklyOptionsError = ref<string | null>(null);
const weeklyExportBusy = ref(false);
const weeklyExportError = ref<string | null>(null);
const weeklyExportStatus = ref<string | null>(null);
let lessonsRevision = 0;
let reportRevision = 0;
let lessonActionRevision = 0;
let transferTargetRevision = 0;
let transferContextGeneration = 0;
const transferRunRevisions = new Map<string, number>();
let weeklyOptionsRevision = 0;
let weeklyExportRevision = 0;
let weeklyExportAbort: AbortController | null = null;
let weeklyOptionsOwner: { api: HeadmanJournalApi; groupId: number | null } | null = null;
let activeLessonsLoad: Promise<void> = Promise.resolve();
let preferredLessonAfterDateChange: number | null = null;
let disposed = false;
let attachmentDisposed = false;
const attachmentPopups = new Set<RequestAttachmentPopup>();
const attachmentObjectUrls = new Set<string>();

const excuseTypes = [
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Участие в соревновании' },
  { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' },
  { value: 'UNIVERSITY_ORDER', label: 'Распоряжение университета' },
  { value: 'EXEMPTION', label: 'Освобождение' },
  { value: 'OTHER', label: 'Другая причина' },
] as const satisfies readonly { value: HeadmanJournalExcuseType; label: string }[];

const subjects = shallowRef<readonly HeadmanSubject[]>([]);
let subjectsRevision = 0;
watch(
  () => [props.subjectsApi, props.groupId, props.offline] as const,
  async () => {
    const revision = ++subjectsRevision;
    const api = props.subjectsApi;
    const groupId = props.groupId;
    subjects.value = [];
    if (!api || groupId === null || props.offline) return;
    try {
      const next = await api.listSubjects(groupId);
      if (!disposed && revision === subjectsRevision && api === props.subjectsApi)
        subjects.value = next.filter((subject) => subject.groupId === groupId);
    } catch (cause) {
      if (
        !disposed &&
        revision === subjectsRevision &&
        !(cause instanceof StaleSessionGenerationError)
      )
        emit('error', cause);
    }
  },
  { immediate: true },
);
const selectedLessonIndex = computed(() =>
  lessons.value.findIndex((lesson) => lesson.id === selectedLessonId.value),
);
function pageLesson(delta: number): void {
  const lesson = lessons.value[selectedLessonIndex.value + delta];
  if (lesson) selectLesson(lesson.id);
  else shiftDate(delta);
}
function subjectName(): string {
  const id = selectedLesson.value?.subjectId;
  return subjects.value.find((subject) => subject.id === id)?.name ?? 'Занятие группы';
}

const selectedLesson = computed(
  () => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null,
);
const entries = computed(() => report.value?.entries ?? []);
const busy = computed(() => loadingLessons.value || loadingReport.value);
const selectedWeeklyFormat = computed(
  () =>
    weeklyExportOptions.value?.formats.find(
      (format) => format.code === weeklyExportFormatCode.value,
    ) ?? null,
);
const selectedWeeklyCount = computed(() => selectedWeeklyStarts.value.length);
const transferTargetDateMin = computed(() => shiftIsoDate(todayIso(), 1));
const transferTargetSlots = computed(() => Array.from({ length: 8 }, (_, index) => index + 1));
const availableTransferTargetSlots = computed(() =>
  transferTargetSlots.value.filter((slot) => !isTransferTargetSlotUnavailable(slot)),
);
const selectedTransferOperation = computed(() =>
  selectedLesson.value ? transferOperationForLesson(selectedLesson.value.id) : null,
);
const selectedServerTransferState = computed(() => selectedLesson.value?.transferState ?? null);
const canSubmitTransfer = computed(() =>
  Boolean(
    transferTaskOpen.value &&
      selectedLesson.value &&
      canStartTransfer(selectedLesson.value) &&
      !lessonActionBusy.value &&
      transferTargetLessonsDate.value === transferTargetDate.value &&
      !transferTargetLoading.value &&
      transferTargetError.value === null &&
      transferTargetLessonNumber.value !== null &&
      availableTransferTargetSlots.value.includes(transferTargetLessonNumber.value),
  ),
);

function formatShortDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' }).format(date);
}

function lessonTypeLabel(value: string | null): string {
  return value === 'LECTURE'
    ? 'Лекция'
    : value === 'PRACTICE'
      ? 'Практика'
      : value === 'LAB'
        ? 'Лабораторная'
        : (value ?? 'Тип не указан');
}
function formatTime(value: string | null): string {
  return value?.slice(0, 5) ?? '—';
}

function sourceLabel(value: string): string | null {
  return value === 'student_geo'
    ? '(ГЕО)'
    : value === 'headman' || value === 'headman_excuse'
      ? '(СТАРОСТА)'
      : null;
}

function statusLabel(status: HeadmanJournalAttendanceStatus): string {
  switch (status) {
    case 'PRESENT':
      return 'Был';
    case 'ABSENT':
      return 'Не был';
    case 'EXCUSED':
      return 'Уважительная причина';
    case 'CANCELLED':
      return 'Пара отменена';
    case 'EMPTY':
      return 'Нет отметки';
    case 'UNSUPPORTED':
      return 'Неподдерживаемый статус';
  }
}

function statusSymbol(status: HeadmanJournalAttendanceStatus): string {
  switch (status) {
    case 'PRESENT':
      return '+';
    case 'ABSENT':
      return 'н';
    case 'EXCUSED':
      return 'у';
    case 'CANCELLED':
      return '—';
    case 'EMPTY':
      return '·';
    case 'UNSUPPORTED':
      return '?';
  }
}

function shiftDate(delta: number): void {
  selectedDate.value = shiftIsoDate(selectedDate.value, delta);
}

function shiftIsoDate(value: string, delta: number): string {
  const date = new Date(`${value}T12:00:00Z`);
  if (Number.isNaN(date.getTime())) return value;
  date.setUTCDate(date.getUTCDate() + delta);
  return date.toISOString().slice(0, 10);
}

function selectLesson(lessonId: number): void {
  if (selectedLessonId.value === lessonId && report.value?.lessonId === lessonId) return;
  selectedLessonId.value = lessonId;
  resumeTransferStatusForSelectedLesson();
  if (canViewReport()) void loadReport(lessonId);
  else report.value = null;
}

function clearRowError(userId: number): void {
  if (!(userId in rowErrors.value)) return;
  const next = { ...rowErrors.value };
  delete next[userId];
  rowErrors.value = next;
}

function setRowError(userId: number, message: string): void {
  rowErrors.value = { ...rowErrors.value, [userId]: message };
}

function hasAssistantPermission(permission: HeadmanAssistantPermission): boolean {
  return props.assistantPermissions === null || props.assistantPermissions.includes(permission);
}

function canViewReport(): boolean {
  return hasAssistantPermission('MARK_ATTENDANCE') || hasAssistantPermission('VIEW_STATS');
}

function canExportWeekly(): boolean {
  return Boolean(
    props.api &&
      props.groupId !== null &&
      !props.offline &&
      (props.assistantPermissions === null || props.assistantPermissions.includes('VIEW_STATS')),
  );
}

function weekForDate(options: HeadmanWeeklyExportOptions, date: string) {
  return options.weeks.find((week) => date >= week.weekStart && date <= week.weekEnd) ?? null;
}

function resetWeeklySelectionForDate(): void {
  const options = weeklyExportOptions.value;
  if (!options) {
    selectedWeeklyStarts.value = [];
    return;
  }
  const selected = weekForDate(options, selectedDate.value);
  selectedWeeklyStarts.value = selected ? [selected.weekStart] : [];
}

function toggleWeeklyWeek(weekStart: string, checked: boolean): void {
  const options = weeklyExportOptions.value;
  if (!options?.weeks.some((week) => week.weekStart === weekStart)) return;
  const next = new Set(selectedWeeklyStarts.value);
  if (checked) next.add(weekStart);
  else next.delete(weekStart);
  selectedWeeklyStarts.value = options.weeks
    .filter((week) => next.has(week.weekStart))
    .map((week) => week.weekStart);
  invalidateWeeklyExport('Недели изменились. Запусти скачивание для нового выбора.');
}

function invalidateWeeklyExport(message?: string): void {
  weeklyExportRevision += 1;
  weeklyExportAbort?.abort();
  weeklyExportAbort = null;
  weeklyExportBusy.value = false;
  weeklyExportError.value = null;
  weeklyExportStatus.value = null;
  if (message) weeklyExportStatus.value = message;
}

async function loadWeeklyExportOptions(): Promise<void> {
  const revision = ++weeklyOptionsRevision;
  invalidateWeeklyExport();
  const api = props.api;
  const sameOwner =
    weeklyOptionsOwner?.api === api && weeklyOptionsOwner?.groupId === props.groupId;
  if (!sameOwner || !canExportWeekly()) {
    weeklyExportOptions.value = null;
    weeklyExportFormatCode.value = '';
    selectedWeeklyStarts.value = [];
  }
  weeklyOptionsOwner = api ? { api, groupId: props.groupId } : null;
  weeklyOptionsError.value = null;
  if (!canExportWeekly()) {
    loadingWeeklyOptions.value = false;
    return;
  }
  loadingWeeklyOptions.value = true;
  weeklyExportError.value = null;
  // The durable accepted intent needs no fresh format/week catalogue to resume.
  try {
    resumePendingWeeklyExport();
    const options = await api!.getWeeklyExportOptions();
    if (disposed || revision !== weeklyOptionsRevision || api !== props.api || !canExportWeekly())
      return;
    const initializeSelection = weeklyExportOptions.value === null;
    weeklyExportOptions.value = options;
    if (initializeSelection) {
      weeklyExportFormatCode.value = options.formats[0]?.code ?? '';
      resetWeeklySelectionForDate();
    }
  } catch (cause) {
    if (
      disposed ||
      revision !== weeklyOptionsRevision ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    weeklyOptionsError.value =
      cause instanceof Error ? cause.message : 'Не удалось загрузить список недель и форматов.';
    emit('error', cause);
  } finally {
    if (revision === weeklyOptionsRevision) loadingWeeklyOptions.value = false;
  }
}

function resumePendingWeeklyExport(): void {
  if (disposed || weeklyExportBusy.value || !canExportWeekly()) return;
  const request = props.reportDownload
    ?.pendingRequests?.()
    .find(
      (entry) =>
        entry.kind === 'HEADMAN_WEEKLY_CURRENT' || entry.kind === 'HEADMAN_WEEKLY_SELECTED',
    );
  if (!request) return;
  weeklyExportStatus.value = 'Проверяем ранее запрошенную выгрузку…';
  void downloadWeeklyExport(request);
}

function reconnectWeeklyExport(): void {
  if (!disposed && !weeklyExportBusy.value) void loadWeeklyExportOptions();
}

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('online', reconnectWeeklyExport);
});

async function downloadWeeklyExport(resumedRequest?: ReportDownloadTicketRequest): Promise<void> {
  const api = props.api;
  const reportDownload = props.reportDownload;
  const options = weeklyExportOptions.value;
  const format = selectedWeeklyFormat.value;
  if (!api || !canExportWeekly() || weeklyExportBusy.value) return;
  if (resumedRequest) {
    if (
      !reportDownload ||
      !['HEADMAN_WEEKLY_CURRENT', 'HEADMAN_WEEKLY_SELECTED'].includes(resumedRequest.kind)
    )
      return;
  } else if (!options || !format) return;
  const weekStarts =
    options?.weeks
      .filter((week) => selectedWeeklyStarts.value.includes(week.weekStart))
      .map((week) => week.weekStart) ?? [];
  if (!resumedRequest && weekStarts.length === 0) {
    weeklyExportError.value = 'Выбери хотя бы одну неделю.';
    weeklyExportStatus.value = null;
    return;
  }
  invalidateWeeklyExport();
  const revision = weeklyExportRevision;
  const controller = new AbortController();
  weeklyExportAbort = controller;
  weeklyExportBusy.value = true;
  try {
    if (resumedRequest) {
      validateTicketRequest(resumedRequest);
      resumedRequest = structuredClone(resumedRequest);
    }
    if (reportDownload) {
      const request =
        resumedRequest ?? toHeadmanWeeklyReportRequest(options!.weeks, weekStarts, format!);
      const isCurrent = (): boolean =>
        !disposed &&
        revision === weeklyExportRevision &&
        api === props.api &&
        reportDownload === props.reportDownload &&
        canExportWeekly() &&
        (resumedRequest !== undefined ||
          (options === weeklyExportOptions.value &&
            selectedWeeklyFormat.value?.code === format!.code &&
            JSON.stringify(selectedWeeklyStarts.value) === JSON.stringify(weekStarts)));
      const result = await reportDownload.download(request, isCurrent, (state) => {
        if (isCurrent()) weeklyExportStatus.value = exportStateLabel(state);
      });
      if (result === 'stale' || !isCurrent()) return;
      if (result === 'unsupported') {
        weeklyExportStatus.value =
          'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.';
      } else {
        weeklyExportStatus.value =
          result === 'accepted'
            ? reportDownloadAcceptedMessage(reportDownload)
            : 'Скачивание отменено.';
      }
      return;
    }

    const downloaded = await api.downloadWeeklyExport(weekStarts, format!, controller.signal);
    if (disposed || revision !== weeklyExportRevision || api !== props.api || !canExportWeekly())
      return;
    if (
      typeof URL === 'undefined' ||
      typeof URL.createObjectURL !== 'function' ||
      typeof document === 'undefined'
    ) {
      throw new Error('Скачивание файла недоступно в этом приложении.');
    }
    const url = URL.createObjectURL(downloaded.blob);
    attachmentObjectUrls.add(url);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = downloaded.filename;
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => releaseAttachmentObjectUrl(url), 60_000);
    weeklyExportStatus.value = `Файл ${downloaded.filename} подготовлен.`;
  } catch (cause) {
    if (
      disposed ||
      revision !== weeklyExportRevision ||
      cause instanceof StaleSessionGenerationError ||
      (controller.signal.aborted &&
        typeof DOMException !== 'undefined' &&
        cause instanceof DOMException &&
        cause.name === 'AbortError')
    )
      return;
    if (
      cause instanceof TypeError ||
      (typeof DOMException !== 'undefined' &&
        cause instanceof DOMException &&
        cause.name === 'AbortError')
    ) {
      weeklyExportError.value =
        'Не удалось связаться с сервером. Проверь подключение и повтори скачивание.';
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 403) {
      weeklyExportError.value = 'Для выгрузки требуется действующее право VIEW_STATS.';
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 413) {
      weeklyExportError.value =
        cause.message ||
        'Файл слишком большой. Выбери меньше недель или другой доступный формат; данные не обрезаны.';
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 422) {
      weeklyExportError.value =
        'Выбранная неделя больше недоступна. Обнови список и выбери неделю из него.';
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status >= 500) {
      weeklyExportError.value =
        'Не удалось сформировать журнал. Проверь подключение и попробуй ещё раз.';
    } else {
      weeklyExportError.value =
        cause instanceof Error ? cause.message : 'Не удалось скачать журнал.';
    }
    emit('error', cause);
  } finally {
    if (revision === weeklyExportRevision) {
      weeklyExportBusy.value = false;
      weeklyExportAbort = null;
    }
  }
}

function canCancelLessons(): boolean {
  return Boolean(
    props.api &&
      !props.offline &&
      !props.readOnly &&
      !reportAccessDenied.value &&
      hasAssistantPermission('CANCEL_LESSONS'),
  );
}

function isHeadmanContext(): boolean {
  return props.assistantPermissions === null;
}

function isTransferOperationActive(operation: TransferOperation): boolean {
  return (
    operation.phase === 'submitting' ||
    operation.phase === 'pending' ||
    operation.phase === 'checking' ||
    operation.phase === 'uncertain'
  );
}

function transferOperationForLesson(lessonId: number): TransferOperation | null {
  for (let index = transferOperations.value.length - 1; index >= 0; index -= 1) {
    const operation = transferOperations.value[index];
    if (
      operation?.api === props.api &&
      operation.groupId === props.groupId &&
      operation.sourceLessonId === lessonId
    ) {
      return operation;
    }
  }
  return null;
}

function isLessonTransferLocked(lesson: HeadmanJournalLesson | null): boolean {
  if (!lesson) return false;
  if (lesson.status === 'TRANSFERRED' || lesson.transferState === 'PENDING') return true;
  return transferOperations.value.some(
    (operation) =>
      operation.api === props.api &&
      operation.groupId === props.groupId &&
      isTransferOperationActive(operation) &&
      (operation.sourceLessonId === lesson.id ||
        (operation.targetDate === lesson.date &&
          operation.targetLessonNumber === lesson.lessonNumber)),
  );
}

function isTransferSourceEligible(lesson: HeadmanJournalLesson): boolean {
  return (
    lesson.current &&
    lesson.status === 'PLANNED' &&
    lesson.date > todayIso() &&
    lesson.occurrenceRevision !== null &&
    /^[1-9][0-9]*$/.test(lesson.occurrenceRevision) &&
    lesson.transferState !== 'PENDING'
  );
}

function canStartTransfer(lesson: HeadmanJournalLesson): boolean {
  return Boolean(
    isHeadmanContext() &&
      canCancelLessons() &&
      props.groupId !== null &&
      isTransferSourceEligible(lesson) &&
      !isLessonTransferLocked(lesson),
  );
}

function isTransferTargetSlotUnavailable(slot: number): boolean {
  const source = selectedLesson.value;
  const targetDate = transferTargetDate.value;
  if (
    !transferTaskOpen.value ||
    !source ||
    transferTargetDateProblem(targetDate) !== null ||
    transferTargetLessonsDate.value !== targetDate ||
    transferTargetLoading.value ||
    transferTargetError.value !== null
  )
    return true;
  if (source.date === targetDate && source.lessonNumber === slot) return true;
  if (
    transferTargetLessons.value.some(
      (lesson) =>
        lesson.current &&
        lesson.id !== source.id &&
        lesson.lessonNumber === slot &&
        lesson.status !== 'CANCELLED' &&
        lesson.status !== 'TRANSFERRED',
    )
  )
    return true;
  return transferOperations.value.some(
    (operation) =>
      operation.api === props.api &&
      operation.groupId === props.groupId &&
      isTransferOperationActive(operation) &&
      operation.targetDate === targetDate &&
      operation.targetLessonNumber === slot,
  );
}

function transferTargetSlotLabel(slot: number): string {
  if (
    selectedLesson.value?.date === transferTargetDate.value &&
    selectedLesson.value.lessonNumber === slot
  ) {
    return `${slot}-я пара · исходное место`;
  }
  if (
    transferTargetLessons.value.some(
      (lesson) =>
        lesson.current &&
        lesson.id !== selectedLesson.value?.id &&
        lesson.lessonNumber === slot &&
        lesson.status !== 'CANCELLED' &&
        lesson.status !== 'TRANSFERRED',
    )
  )
    return `${slot}-я пара · занято`;
  if (
    transferOperations.value.some(
      (operation) =>
        operation.api === props.api &&
        operation.groupId === props.groupId &&
        isTransferOperationActive(operation) &&
        operation.targetDate === transferTargetDate.value &&
        operation.targetLessonNumber === slot,
    )
  ) {
    return `${slot}-я пара · ожидает переноса`;
  }
  return `${slot}-я пара`;
}

function transferTargetDateProblem(date: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return 'Выбери будущую дату.';
  const parsed = new Date(`${date}T12:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    return 'Дата переноса указана некорректно.';
  }
  if (date <= todayIso()) return 'Выбери будущую дату.';
  if (parsed.getUTCDay() === 0)
    return 'Перенос возможен только на учебный день с понедельника по субботу.';
  return null;
}

function transferErrorMessage(cause: unknown): string {
  if (!(cause instanceof HeadmanJournalApiError)) {
    return cause instanceof Error ? cause.message : 'Не удалось подтвердить перенос пары.';
  }
  const problem = cause.problem;
  const code =
    typeof problem === 'object' && problem !== null && 'errorCode' in problem
      ? problem.errorCode
      : null;
  const knownCode =
    code === 'TARGET_DATA_CONFLICT' ||
    code === 'SOURCE_STATE_CONFLICT' ||
    code === 'SCOPE_MISMATCH' ||
    code === 'INVALID_SNAPSHOT' ||
    code === 'DEPENDENCY_UNAVAILABLE' ||
    code === 'ARCHIVED_SEMESTER'
      ? (code as HeadmanLessonTransferErrorCode)
      : null;
  if (knownCode) return transferErrorCodeMessage(knownCode);
  if (cause.response.status === 401)
    return 'Сеанс истёк. Войди снова и проверь состояние переноса.';
  if (cause.response.status === 403) return 'У тебя нет прав на перенос этой пары.';
  if (cause.response.status === 404) return 'Пара не найдена. Обнови журнал и выбери её ещё раз.';
  if (cause.response.status === 409)
    return 'Пара или выбранное место в расписании изменились. Обнови журнал и выбери свободное место.';
  if (
    cause.response.status >= 500 ||
    cause.response.status === 408 ||
    cause.response.status === 429
  ) {
    return 'Сервер не подтвердил запрос. Повтори его тем же запросом или проверь состояние операции.';
  }
  return cause.message;
}

function transferErrorCodeMessage(code: HeadmanLessonTransferErrorCode): string {
  switch (code) {
    case 'TARGET_DATA_CONFLICT':
      return 'Выбранная дата или пара уже заняты. Обнови список и выбери другое место.';
    case 'SOURCE_STATE_CONFLICT':
      return 'Исходная пара изменилась. Обнови журнал перед новым переносом.';
    case 'SCOPE_MISMATCH':
      return 'Перенос этой пары в выбранную дату недоступен.';
    case 'INVALID_SNAPSHOT':
      return 'Данные пары изменились. Обнови журнал и начни перенос заново.';
    case 'DEPENDENCY_UNAVAILABLE':
      return 'Перенос ожидает проверки сервиса. Проверь состояние операции позже.';
    case 'ARCHIVED_SEMESTER':
      return 'Семестр закрыт. Перенос в нём недоступен.';
  }
}

function transferPhaseMessage(operation: TransferOperation): string {
  if (operation.message) return operation.message;
  switch (operation.phase) {
    case 'submitting':
      return 'Отправляем запрос на перенос…';
    case 'pending':
      return 'Перенос принят и ожидает завершения.';
    case 'checking':
      return 'Проверяем состояние переноса…';
    case 'uncertain':
      return 'Ответ не получен. Повтор запроса безопасен: будет использован тот же ключ и payload.';
    case 'completed':
      return 'Перенос завершён.';
    case 'error':
      return 'Перенос завершился ошибкой.';
  }
}

function serverTransferStateMessage(state: HeadmanJournalLesson['transferState']): string {
  switch (state) {
    case 'PENDING':
      return 'Перенос выполняется. Проверь его состояние перед другими действиями с парой.';
    case 'COMPLETED':
      return 'Сервер подтвердил завершение переноса. Проверь дату операции, чтобы открыть новую пару.';
    case 'ERROR':
      return 'Сервер сохранил ошибку переноса. Обнови журнал перед новым действием.';
    case null:
      return '';
  }
}

function canResumeSelectedTransferSource(): boolean {
  const lesson = selectedLesson.value;
  if (!lesson) return false;
  const operation = transferOperationForLesson(lesson.id);
  return (
    operation?.sourceLessonId === lesson.id ||
    (lesson.status === 'TRANSFERRED' && lesson.transferOperationId !== null)
  );
}

function canCheckSelectedTransferStatus(): boolean {
  const lesson = selectedLesson.value;
  if (!lesson) return false;
  const operation = transferOperationForLesson(lesson.id);
  if (operation?.sourceLessonId === lesson.id) return operation.operationId !== null;
  return lesson.status === 'TRANSFERRED' && lesson.transferOperationId !== null;
}

function resumeTransferStatusForSelectedLesson(): void {
  const lesson = selectedLesson.value;
  if (!lesson || !isHeadmanContext() || props.offline || props.readOnly) return;
  const operation = transferOperationForLesson(lesson.id);
  if (operation?.sourceLessonId === lesson.id) {
    if (
      operation.phase === 'checking' ||
      operation.phase === 'submitting' ||
      operation.phase === 'completed' ||
      operation.operationId === null
    )
      return;
    resumeTransferForSelectedLesson(false);
    return;
  }
  if (lesson.status !== 'TRANSFERRED' || !lesson.transferOperationId) return;
  resumeTransferForSelectedLesson(false);
}

function canRunTransferOperation(operation: TransferOperation): boolean {
  return (
    !disposed &&
    operation.api === props.api &&
    operation.groupId === props.groupId &&
    selectedDate.value === operation.sourceDate &&
    selectedLessonId.value === operation.sourceLessonId &&
    selectedLesson.value?.id === operation.sourceLessonId &&
    !props.offline &&
    !props.readOnly &&
    isHeadmanContext()
  );
}

function replaceTransferOperation(key: string, patch: Partial<TransferOperation>): void {
  const index = transferOperations.value.findIndex((operation) => operation.key === key);
  if (index < 0) return;
  const next = [...transferOperations.value];
  const current = next[index];
  if (!current) return;
  next[index] = { ...current, ...patch };
  transferOperations.value = next;
}

async function loadTransferTargetLessons(): Promise<void> {
  const revision = ++transferTargetRevision;
  const api = props.api;
  const groupId = props.groupId;
  const source = selectedLesson.value;
  const date = transferTargetDate.value;
  transferTargetLessons.value = [];
  transferTargetLessonsDate.value = null;
  transferTargetError.value = null;
  transferTargetLessonNumber.value = null;
  if (!transferTaskOpen.value || !api || groupId === null || !source || !canStartTransfer(source)) {
    transferTargetLoading.value = false;
    return;
  }
  const dateProblem = transferTargetDateProblem(date);
  if (dateProblem !== null) {
    transferTargetError.value = dateProblem;
    transferTargetLoading.value = false;
    return;
  }
  transferTargetLoading.value = true;
  try {
    const next = await api.listLessons(groupId, date, date);
    if (
      disposed ||
      revision !== transferTargetRevision ||
      api !== props.api ||
      groupId !== props.groupId ||
      selectedDate.value !== source.date ||
      selectedLessonId.value !== source.id ||
      transferTargetDate.value !== date ||
      !transferTaskOpen.value
    )
      return;
    transferTargetLessons.value = next;
    transferTargetLessonsDate.value = date;
    transferTargetLoading.value = false;
    transferTargetLessonNumber.value =
      transferTargetSlots.value.find((slot) => !isTransferTargetSlotUnavailable(slot)) ?? null;
  } catch (cause) {
    if (
      disposed ||
      revision !== transferTargetRevision ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    transferTargetError.value = transferErrorMessage(cause);
    emit('error', cause);
  } finally {
    if (revision === transferTargetRevision) transferTargetLoading.value = false;
  }
}

function openTransferTask(): void {
  const lesson = selectedLesson.value;
  if (!lesson || !canStartTransfer(lesson) || lessonActionBusy.value) return;
  transferTargetDate.value = lesson.date;
  transferTargetLessonNumber.value = null;
  transferTaskOpen.value = true;
}

function closeTransferTask(): void {
  transferTaskOpen.value = false;
  transferTargetDate.value = '';
  transferTargetLessonNumber.value = null;
  transferTargetLessons.value = [];
  transferTargetLessonsDate.value = null;
  transferTargetError.value = null;
}

function requestTransferSelectedLesson(): void {
  const api = props.api;
  const lesson = selectedLesson.value;
  const groupId = props.groupId;
  const targetDate = transferTargetDate.value;
  const targetLessonNumber = transferTargetLessonNumber.value;
  if (
    !api ||
    groupId === null ||
    !lesson ||
    !canSubmitTransfer.value ||
    targetLessonNumber === null ||
    lesson.occurrenceRevision === null
  )
    return;
  try {
    if (!globalThis.crypto || typeof globalThis.crypto.randomUUID !== 'function') {
      throw new Error('Безопасный ключ запроса недоступен в этой версии браузера.');
    }
    const transferRequest = createHeadmanLessonTransferRequest(
      targetDate,
      targetLessonNumber,
      lesson.occurrenceRevision,
      globalThis.crypto.randomUUID(),
    );
    openLessonActionConfirmation({
      action: 'transfer',
      api,
      groupId,
      lessonId: lesson.id,
      lessonDate: lesson.date,
      lessonNumber: lesson.lessonNumber,
      permissions: assistantPermissionSnapshot(),
      reason: null,
      transferRequest,
    });
  } catch (cause) {
    notice.value =
      cause instanceof Error ? cause.message : 'Не удалось подготовить запрос переноса.';
  }
}

function assistantPermissionSnapshot(): readonly HeadmanAssistantPermission[] | null {
  return props.assistantPermissions === null ? null : [...props.assistantPermissions].sort();
}

function assistantPermissionsMatch(
  snapshot: readonly HeadmanAssistantPermission[] | null,
): boolean {
  const current = assistantPermissionSnapshot();
  return snapshot === null || current === null
    ? snapshot === current
    : snapshot.length === current.length &&
        snapshot.every((permission, index) => permission === current[index]);
}

function isLessonActionConfirmationCurrent(confirmation: LessonActionConfirmation): boolean {
  const lesson = selectedLesson.value;
  if (
    lessonActionBusy.value ||
    !lesson ||
    lesson.id !== confirmation.lessonId ||
    selectedLessonId.value !== confirmation.lessonId ||
    props.api !== confirmation.api ||
    props.groupId !== confirmation.groupId ||
    props.offline ||
    props.readOnly ||
    !canCancelLessons() ||
    !assistantPermissionsMatch(confirmation.permissions)
  )
    return false;

  if (confirmation.action === 'cancel') {
    return (
      lesson.status !== 'CANCELLED' &&
      confirmation.reason !== null &&
      cancelReason.value.trim() === confirmation.reason &&
      !isLessonTransferLocked(lesson)
    );
  }
  if (confirmation.action === 'restore')
    return lesson.status === 'CANCELLED' && !isLessonTransferLocked(lesson);
  const transferRequest = confirmation.transferRequest;
  return (
    transferRequest !== null &&
    transferTaskOpen.value &&
    canStartTransfer(lesson) &&
    transferTargetDate.value === transferRequest.targetDate &&
    transferTargetLessonNumber.value === transferRequest.targetLessonNumber &&
    transferTargetLessonsDate.value === transferRequest.targetDate &&
    !isTransferTargetSlotUnavailable(transferRequest.targetLessonNumber)
  );
}

const lessonActionConfirmationCurrent = computed(() => {
  const confirmation = pendingLessonActionConfirmation.value;
  return confirmation !== null && isLessonActionConfirmationCurrent(confirmation);
});

function closeLessonActionConfirmation(): void {
  pendingLessonActionConfirmation.value = null;
  const dialog = lessonActionConfirmationDialog.value;
  if (dialog?.open) dialog.close();
}

function invalidateLessonActionConfirmation(): void {
  if (pendingLessonActionConfirmation.value !== null) closeLessonActionConfirmation();
}

function openLessonActionConfirmation(confirmation: LessonActionConfirmation): void {
  const dialog = lessonActionConfirmationDialog.value;
  if (!dialog || dialog.open || !isLessonActionConfirmationCurrent(confirmation)) return;
  pendingLessonActionConfirmation.value = confirmation;
  toolsSheet.value?.close(false);
  toolsOpen.value = false;
  dialog.showModal();
}

function canManageExcuses(): boolean {
  return hasAssistantPermission('MANAGE_EXCUSES');
}

function canWrite(entry: HeadmanJournalRosterEntry): boolean {
  const required = entry.status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE';
  return Boolean(
    props.api?.writable &&
      entry.editable &&
      !props.offline &&
      !props.readOnly &&
      entry.status !== 'UNSUPPORTED' &&
      entry.status !== 'CANCELLED' &&
      selectedLessonId.value !== null &&
      selectedLesson.value !== null &&
      selectedLesson.value.status !== 'TRANSFERRED' &&
      hasAssistantPermission(required),
  );
}

function markCondition(entry: HeadmanJournalRosterEntry): HeadmanJournalMarkCondition | null {
  if (entry.status === 'EMPTY') return { expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' };
  if (
    (entry.status === 'PRESENT' || entry.status === 'ABSENT' || entry.status === 'EXCUSED') &&
    entry.markRevision
  ) {
    return { expectedStatus: entry.status, expectedRevision: entry.markRevision };
  }
  return null;
}

function canClearEntry(entry: HeadmanJournalRosterEntry): boolean {
  return (
    canWrite(entry) &&
    Boolean(entry.markRevision) &&
    ['PRESENT', 'EXCUSED', 'ABSENT'].includes(entry.status)
  );
}

async function refreshMissingRevision(entry: HeadmanJournalRosterEntry): Promise<void> {
  const lessonId = selectedLessonId.value;
  if (lessonId === null) return;
  await loadReport(lessonId);
  if (selectedLessonId.value === lessonId && !disposed) {
    setRowError(
      entry.userId,
      'Без серверной версии отметку снять нельзя. Если версия ещё недоступна, попробуй позже.',
    );
  }
}

function canWriteExcuse(entry: HeadmanJournalRosterEntry): boolean {
  return canManageExcuses() && canWrite(entry) && markCondition(entry) !== null;
}

function requestCancelSelectedLesson(): void {
  const api = props.api;
  const lesson = selectedLesson.value;
  if (
    lessonActionBusy.value ||
    !api ||
    !lesson ||
    lesson.status === 'CANCELLED' ||
    !canCancelLessons() ||
    isLessonTransferLocked(lesson)
  )
    return;
  const reason = cancelReason.value.trim();
  if (!reason) {
    notice.value = 'Укажи причину отмены пары.';
    return;
  }
  if (Array.from(reason).length > 512) {
    notice.value = 'Причина отмены не может быть длиннее 512 символов.';
    return;
  }
  openLessonActionConfirmation({
    action: 'cancel',
    api,
    groupId: props.groupId,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    permissions: assistantPermissionSnapshot(),
    reason,
    transferRequest: null,
  });
}

function requestRestoreSelectedLesson(): void {
  const api = props.api;
  const lesson = selectedLesson.value;
  if (
    lessonActionBusy.value ||
    !api ||
    !lesson ||
    lesson.status !== 'CANCELLED' ||
    !canCancelLessons() ||
    isLessonTransferLocked(lesson)
  )
    return;
  openLessonActionConfirmation({
    action: 'restore',
    api,
    groupId: props.groupId,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    permissions: assistantPermissionSnapshot(),
    reason: null,
    transferRequest: null,
  });
}

function transferOperationKey(requestKey: string): string {
  return `request:${requestKey}`;
}

function beginConfirmedTransfer(confirmation: LessonActionConfirmation): void {
  const request = confirmation.transferRequest;
  const api = confirmation.api;
  const groupId = confirmation.groupId;
  const lesson = selectedLesson.value;
  if (
    confirmation.action !== 'transfer' ||
    !request ||
    groupId === null ||
    !lesson ||
    lesson.id !== confirmation.lessonId ||
    !canStartTransfer(lesson)
  )
    return;
  const operation: TransferOperation = {
    key: transferOperationKey(request.requestKey),
    api,
    groupId,
    sourceLessonId: lesson.id,
    sourceDate: lesson.date,
    targetDate: request.targetDate,
    targetLessonNumber: request.targetLessonNumber,
    request,
    operationId: null,
    response: null,
    phase: 'submitting',
    message: null,
  };
  closeTransferTask();
  transferOperations.value = [...transferOperations.value, operation];
  void runTransferOperation(operation, 'submit');
}

async function confirmLessonAction(): Promise<void> {
  const confirmation = pendingLessonActionConfirmation.value;
  if (!confirmation) return;
  if (!isLessonActionConfirmationCurrent(confirmation)) {
    closeLessonActionConfirmation();
    return;
  }
  closeLessonActionConfirmation();
  if (confirmation.action === 'transfer') {
    beginConfirmedTransfer(confirmation);
  } else if (confirmation.action === 'cancel' && confirmation.reason !== null) {
    await cancelSelectedLesson(confirmation.api, confirmation.lessonId, confirmation.reason);
  } else {
    await restoreSelectedLesson(confirmation.api, confirmation.lessonId);
  }
}

async function runTransferOperation(
  operation: TransferOperation,
  mode: 'submit' | 'status',
): Promise<void> {
  if (!canRunTransferOperation(operation)) return;
  if (
    (mode === 'submit' && operation.request === null) ||
    (mode === 'status' && operation.operationId === null)
  )
    return;
  const runRevision = (transferRunRevisions.get(operation.key) ?? 0) + 1;
  transferRunRevisions.set(operation.key, runRevision);
  const contextGeneration = transferContextGeneration;
  const isCurrentRun = (): boolean =>
    transferRunRevisions.get(operation.key) === runRevision &&
    transferContextGeneration === contextGeneration &&
    canRunTransferOperation(operation);
  replaceTransferOperation(operation.key, {
    phase: mode === 'submit' ? 'submitting' : 'checking',
    message: null,
  });
  try {
    const response =
      mode === 'submit'
        ? await operation.api.transferLesson(operation.sourceLessonId, operation.request!)
        : await operation.api.getLessonTransfer(operation.operationId!);
    if (!isCurrentRun()) return;
    if (
      response.sourceLessonId !== String(operation.sourceLessonId) ||
      (operation.operationId !== null && response.operationId !== operation.operationId) ||
      (operation.targetDate !== null && response.targetDate !== operation.targetDate)
    ) {
      throw new Error('Сервер вернул состояние другого переноса.');
    }
    await applyTransferResponse(operation, response, isCurrentRun);
  } catch (cause) {
    if (!isCurrentRun()) return;
    if (cause instanceof StaleSessionGenerationError) {
      replaceTransferOperation(operation.key, {
        phase: operation.operationId === null ? 'uncertain' : 'pending',
        message: 'Сеанс изменился. После входа проверь состояние операции переноса.',
      });
      return;
    }
    if (mode === 'status') {
      replaceTransferOperation(operation.key, {
        phase: 'pending',
        message: `Состояние пока не получено. ${transferErrorMessage(cause)} Проверь его ещё раз.`,
      });
      emit('error', cause);
      return;
    }
    const definitive =
      cause instanceof HeadmanJournalApiError &&
      cause.response.status >= 400 &&
      cause.response.status < 500 &&
      cause.response.status !== 401 &&
      cause.response.status !== 408 &&
      cause.response.status !== 429;
    replaceTransferOperation(operation.key, {
      phase: definitive ? 'error' : 'uncertain',
      message: transferErrorMessage(cause),
    });
    emit('error', cause);
  }
}

async function applyTransferResponse(
  operation: TransferOperation,
  response: HeadmanLessonTransferResponse,
  isCurrentRun: () => boolean,
): Promise<void> {
  if (!isCurrentRun()) return;
  if (response.state === 'PENDING') {
    replaceTransferOperation(operation.key, {
      operationId: response.operationId,
      targetDate: response.targetDate,
      response,
      phase: 'pending',
      message: null,
    });
    await pollTransferStatus(operation, response, isCurrentRun);
    return;
  }
  if (response.state === 'ERROR') {
    replaceTransferOperation(operation.key, {
      operationId: response.operationId,
      targetDate: response.targetDate,
      response,
      phase: 'error',
      message: response.errorCode
        ? transferErrorCodeMessage(response.errorCode)
        : 'Перенос завершился ошибкой.',
    });
    return;
  }
  const completedOperation = { ...operation, targetDate: response.targetDate };
  replaceTransferOperation(operation.key, {
    operationId: response.operationId,
    targetDate: response.targetDate,
    response,
    phase: 'completed',
    message: 'Перенос завершён. Журнал обновляется на новой дате.',
  });
  await reloadTransferredLesson(completedOperation, response, isCurrentRun);
}

async function pollTransferStatus(
  operation: TransferOperation,
  accepted: HeadmanLessonTransferResponse,
  isCurrentRun: () => boolean,
): Promise<void> {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    await new Promise<void>((resolve) => setTimeout(resolve, 750));
    if (!isCurrentRun()) return;
    try {
      const response = await operation.api.getLessonTransfer(accepted.operationId);
      if (!isCurrentRun()) return;
      if (
        response.sourceLessonId !== String(operation.sourceLessonId) ||
        response.operationId !== accepted.operationId ||
        response.targetDate !== accepted.targetDate
      ) {
        throw new Error('Сервер вернул состояние другого переноса.');
      }
      if (response.state === 'PENDING') {
        replaceTransferOperation(operation.key, {
          operationId: response.operationId,
          response,
          phase: 'pending',
          message: null,
        });
        continue;
      }
      await applyTransferResponse(operation, response, isCurrentRun);
      return;
    } catch (cause) {
      if (!isCurrentRun()) return;
      replaceTransferOperation(operation.key, {
        operationId: accepted.operationId,
        phase: 'pending',
        message: `Не удалось проверить состояние. ${transferErrorMessage(cause)} Проверь его ещё раз.`,
      });
      emit('error', cause);
      return;
    }
  }
  if (isCurrentRun()) {
    replaceTransferOperation(operation.key, {
      operationId: accepted.operationId,
      phase: 'pending',
      message: 'Перенос ещё выполняется. Проверь состояние пары через журнал.',
    });
  }
}

async function reloadTransferredLesson(
  operation: TransferOperation,
  response: HeadmanLessonTransferResponse,
  isCurrentRun: () => boolean,
): Promise<void> {
  const targetDate = response.targetDate;
  if (!isCurrentRun()) return;
  const targetLessonId = Number(response.targetLessonId);
  const preferredId =
    Number.isSafeInteger(targetLessonId) && targetLessonId > 0 ? targetLessonId : null;
  transferTaskOpen.value = false;
  if (selectedDate.value === targetDate) {
    activeLessonsLoad = loadLessons(preferredId);
  } else {
    preferredLessonAfterDateChange = preferredId;
    selectedDate.value = targetDate;
  }
  const reload = activeLessonsLoad;
  await reload;
  if (
    props.api !== operation.api ||
    props.groupId !== operation.groupId ||
    selectedDate.value !== targetDate
  )
    return;
  const targetVisible =
    preferredId === null || lessons.value.some((lesson) => lesson.id === preferredId);
  notice.value = targetVisible
    ? 'Перенос завершён. Журнал обновлён на новой дате.'
    : 'Перенос завершён, но новая пара не появилась в списке. Обнови журнал ещё раз.';
}

function resumeTransferForSelectedLesson(retryUncertain: boolean): void {
  const lesson = selectedLesson.value;
  const api = props.api;
  const groupId = props.groupId;
  if (
    !lesson ||
    !api ||
    groupId === null ||
    !isHeadmanContext() ||
    props.offline ||
    props.readOnly ||
    !canResumeSelectedTransferSource()
  )
    return;
  let operation = transferOperationForLesson(lesson.id);
  if (!operation && lesson.status === 'TRANSFERRED' && lesson.transferOperationId) {
    const key = `operation:${lesson.transferOperationId}`;
    operation = {
      key,
      api,
      groupId,
      sourceLessonId: lesson.id,
      sourceDate: lesson.date,
      targetDate: null,
      targetLessonNumber: null,
      request: null,
      operationId: lesson.transferOperationId,
      response: null,
      phase: 'pending',
      message: null,
    };
    transferOperations.value = [...transferOperations.value, operation];
  } else if (
    operation?.sourceLessonId === lesson.id &&
    lesson.status === 'TRANSFERRED' &&
    lesson.transferOperationId &&
    operation.operationId !== lesson.transferOperationId
  ) {
    transferRunRevisions.set(operation.key, (transferRunRevisions.get(operation.key) ?? 0) + 1);
    operation = {
      ...operation,
      operationId: lesson.transferOperationId,
      targetDate: null,
      targetLessonNumber: null,
      response: null,
      phase: 'pending',
      message: null,
    };
    replaceTransferOperation(operation.key, operation);
  }
  if (!operation || !canRunTransferOperation(operation)) return;
  if (retryUncertain && operation.operationId === null && operation.request !== null) {
    void runTransferOperation(operation, 'submit');
  } else if (operation.operationId !== null) {
    void runTransferOperation(operation, 'status');
  }
}

async function cancelSelectedLesson(
  api: HeadmanJournalApi,
  lessonId: number,
  reason: string,
): Promise<void> {
  if (
    lessonActionBusy.value ||
    api !== props.api ||
    selectedLessonId.value !== lessonId ||
    !canCancelLessons() ||
    isLessonTransferLocked(selectedLesson.value)
  )
    return;
  const revision = ++lessonActionRevision;
  lessonActionBusy.value = true;
  error.value = null;
  notice.value = null;
  try {
    await api.cancelLesson(lessonId, reason);
    if (disposed || revision !== lessonActionRevision) return;
    notice.value = 'Пара отменена. Список обновлён с сервера.';
    cancelReason.value = '';
    await loadLessons();
  } catch (cause) {
    if (
      disposed ||
      revision !== lessonActionRevision ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    error.value = cause instanceof Error ? cause.message : 'Не удалось отменить пару.';
    emit('error', cause);
  } finally {
    if (!disposed && revision === lessonActionRevision) lessonActionBusy.value = false;
  }
}

async function restoreSelectedLesson(api: HeadmanJournalApi, lessonId: number): Promise<void> {
  if (
    lessonActionBusy.value ||
    api !== props.api ||
    selectedLessonId.value !== lessonId ||
    !canCancelLessons() ||
    isLessonTransferLocked(selectedLesson.value)
  )
    return;
  const revision = ++lessonActionRevision;
  lessonActionBusy.value = true;
  error.value = null;
  notice.value = null;
  try {
    await api.restoreLesson(lessonId);
    if (disposed || revision !== lessonActionRevision) return;
    notice.value = 'Пара восстановлена. Список обновлён с сервера.';
    await loadLessons();
  } catch (cause) {
    if (
      disposed ||
      revision !== lessonActionRevision ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    error.value = cause instanceof Error ? cause.message : 'Не удалось восстановить пару.';
    emit('error', cause);
  } finally {
    if (!disposed && revision === lessonActionRevision) lessonActionBusy.value = false;
  }
}

function writeUnavailableMessage(entry: HeadmanJournalRosterEntry): string {
  if (props.offline) return 'Изменение отметки доступно только онлайн.';
  if (props.readOnly) return 'Текущая сессия открыта только для чтения.';
  if (!entry.editable) return 'Сервер запретил изменение этой отметки.';
  return 'Изменение отметки пока недоступно.';
}

function excuseTypeLabel(value: string | null): string | null {
  return excuseTypes.find((item) => item.value === value)?.label ?? value;
}

function formatFileSize(value: number | null): string {
  if (value === null) return '';
  if (value < 1024) return `${value} Б`;
  return `${Math.ceil(value / 1024)} КБ`;
}

function attachmentState(entry: HeadmanJournalRosterEntry): RequestAttachmentViewState | null {
  return attachmentStates.value[entry.userId] ?? null;
}

function releaseAttachmentObjectUrl(url: string): void {
  attachmentObjectUrls.delete(url);
  if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function')
    URL.revokeObjectURL(url);
}

function closeAttachmentPopup(popup: RequestAttachmentPopup): void {
  try {
    popup.close?.();
  } catch {
    /* Browser may have closed it already. */
  }
  attachmentPopups.delete(popup);
}

function openAttachment(entry: HeadmanJournalRosterEntry): void {
  const lessonId = selectedLessonId.value;
  const api = props.api;
  if (
    props.offline ||
    !api ||
    lessonId === null ||
    !entry.attachmentId ||
    !canManageExcuses() ||
    attachmentState(entry)?.status === 'pending'
  )
    return;
  const ownerIdentityAtStart = attachmentOwnerIdentity.value;
  const ownerGenerationAtStart = attachmentOwnerGeneration.value;
  runRequestAttachmentOpen({
    ownerIdentity: ownerIdentityAtStart,
    ownerGeneration: ownerGenerationAtStart,
    currentOwnerIdentity: () => attachmentOwnerIdentity.value,
    currentOwnerGeneration: () => attachmentOwnerGeneration.value,
    isDisposed: () => attachmentDisposed,
    openPopup: () => (typeof window === 'undefined' ? null : openRequestAttachmentPopup(window)),
    download: () => api.downloadAttachment(lessonId, entry.userId),
    createObjectUrl: (blob) => {
      if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function')
        throw new Error('Не удалось подготовить вложение.');
      const url = URL.createObjectURL(blob);
      attachmentObjectUrls.add(url);
      return url;
    },
    releaseObjectUrl: releaseAttachmentObjectUrl,
    scheduleRelease: (url) => {
      if (typeof window === 'undefined') {
        releaseAttachmentObjectUrl(url);
        return;
      }
      window.setTimeout(() => releaseAttachmentObjectUrl(url), 60_000);
    },
    navigate: (popup, url) => {
      popup.location.href = url;
    },
    closePopup: closeAttachmentPopup,
    onPopupOpened: (popup) => attachmentPopups.add(popup),
    onPopupNavigated: (popup) => attachmentPopups.delete(popup),
    setState: (state) => {
      if (
        !attachmentDisposed &&
        ownerIdentityAtStart === attachmentOwnerIdentity.value &&
        ownerGenerationAtStart === attachmentOwnerGeneration.value
      ) {
        attachmentStates.value = { ...attachmentStates.value, [entry.userId]: state };
      }
    },
    onError: (cause) => emit('error', cause),
    errorMessage: (cause) =>
      cause instanceof Error ? cause.message : 'Не удалось открыть вложение.',
  });
}

function openExcuseForm(entry: HeadmanJournalRosterEntry): void {
  if (!canWriteExcuse(entry)) {
    notice.value = writeUnavailableMessage(entry);
    return;
  }
  excuseUserId.value = entry.userId;
  excuseType.value = excuseTypes.some((item) => item.value === entry.excuseType)
    ? (entry.excuseType as HeadmanJournalExcuseType)
    : '';
  excuseComment.value = entry.comment ?? '';
  excuseFile.value = null;
  notice.value = null;
}

function closeExcuseForm(): void {
  if (pendingUserId.value !== null) return;
  linkedTicketEntry.value = null;
  excuseUserId.value = null;
  excuseComment.value = '';
  excuseFile.value = null;
}

watch(
  () =>
    [
      excuseUserId.value,
      linkedTicketEntry.value,
      toolsOpen.value,
      weeklyOpen.value,
      clearConfirmation.value,
      pendingLessonActionConfirmation.value,
    ] as const,
  ([userId, ticket, tools, weekly, clear, action]) =>
    emit(
      'taskChange',
      userId !== null || ticket !== null || tools || weekly || clear !== null || action !== null,
    ),
  { flush: 'sync' },
);
watch(
  () =>
    [
      props.api,
      props.requestsApi,
      props.groupId,
      selectedLessonId.value,
      props.offline,
      props.assistantPermissions,
    ] as const,
  () => {
    linkedTicketEntry.value = null;
  },
  { flush: 'sync' },
);
function openLinkedTicket(entry: HeadmanJournalRosterEntry): void {
  if (!entry.approvedTicket || !canManageExcuses() || props.offline || pendingUserId.value !== null)
    return;
  excuseUserId.value = null;
  linkedTicketEntry.value = entry;
}
function back(): boolean {
  if (clearConfirmation.value) {
    closeClearConfirmation();
    return true;
  }
  if (pendingLessonActionConfirmation.value) {
    closeLessonActionConfirmation();
    return true;
  }
  if (toolsOpen.value) {
    toolsSheet.value?.close();
    toolsOpen.value = false;
    return true;
  }
  if (weeklyOpen.value) {
    weeklySheet.value?.close();
    weeklyOpen.value = false;
    return true;
  }
  if (excuseUserId.value !== null || linkedTicketEntry.value !== null) {
    closeExcuseForm();
    return true;
  }
  return false;
}
defineExpose({ closeExcuseForm, back });

const manualPreview = ref<ManualPreviewState>({
  url: null,
  mime: null,
  loading: false,
  error: null,
});
const manualPreviewOwner = new HeadmanManualPreview(
  () => {
    const entry = entries.value.find((row) => row.userId === excuseUserId.value);
    return {
      api: props.api,
      lessonId: selectedLessonId.value,
      userId: entry?.userId ?? null,
      attachmentId: entry?.attachmentId ?? null,
      file: excuseFile.value,
      ownerKey: `${props.groupId}/${entry?.markRevision ?? ''}`,
      allowed: Boolean(entry && canWriteExcuse(entry)),
    };
  },
  (state) => {
    manualPreview.value = state;
  },
);
watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      props.readOnly,
      props.assistantPermissions,
      selectedLessonId.value,
      excuseUserId.value,
      excuseFile.value,
      report.value,
    ] as const,
  () => {
    void manualPreviewOwner.load();
  },
  { flush: 'sync' },
);

function openManualAttachment(): void {
  const url = manualPreviewOwner.openUrl();
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
function chooseExcuseFile(event: Event): void {
  const input = event.target as HTMLInputElement;
  excuseFile.value = input.files?.[0] ?? null;
}

async function submitExcuse(entry: HeadmanJournalRosterEntry): Promise<void> {
  if (pendingUserId.value !== null || excuseUserId.value !== entry.userId || !canWriteExcuse(entry))
    return;
  const lessonId = selectedLessonId.value;
  const api = props.api;
  if (!api || lessonId === null) return;
  if (!excuseType.value) {
    setRowError(entry.userId, 'Выбери причину.');
    return;
  }
  if (Array.from(excuseComment.value).length > 1000) {
    setRowError(entry.userId, 'Комментарий не может быть длиннее 1000 символов.');
    return;
  }
  const scope = mutationScope();
  if (!scope) return;
  pendingUserId.value = entry.userId;
  notice.value = null;
  clearRowError(entry.userId);
  try {
    await api.mark(
      lessonId,
      entry.userId,
      {
        status: 'EXCUSED',
        excuseType: excuseType.value,
        ...(excuseComment.value.trim() ? { comment: excuseComment.value } : {}),
        ...(excuseFile.value ? { file: excuseFile.value } : {}),
      },
      markCondition(entry)!,
    );
    if (!mutationCurrent(scope)) return;
    await loadReport(lessonId);
  } catch (cause) {
    if (!mutationCurrent(scope) || cause instanceof StaleSessionGenerationError) return;
    if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {
      await loadReport(scope.lessonId);
      if (mutationCurrent(scope))
        setRowError(
          entry.userId,
          'Отметка изменилась. Проверь обновлённые данные и заново укажи причину.',
        );
      return;
    }
    const message = cause instanceof Error ? cause.message : 'Не удалось сохранить причину.';
    setRowError(entry.userId, message);
    emit('error', cause);
  } finally {
    if (mutationCurrent(scope) && pendingUserId.value === entry.userId) {
      pendingUserId.value = null;
      await nextTick();
      if (mutationCurrent(scope))
        (
          markControls.get(`${entry.userId}:current`) ?? markControls.get(`${entry.userId}:PRESENT`)
        )?.focus();
    }
  }
}

async function clearExcuse(entry: HeadmanJournalRosterEntry): Promise<void> {
  if (
    pendingUserId.value !== null ||
    (excuseUserId.value !== null && excuseUserId.value !== entry.userId) ||
    !canWriteExcuse(entry)
  )
    return;
  const scope = mutationScope();
  if (!scope) return;
  pendingUserId.value = entry.userId;
  notice.value = null;
  clearRowError(entry.userId);
  try {
    await clearEntry(entry);
    closeExcuseForm();
  } catch (cause) {
    if (!mutationCurrent(scope) || cause instanceof StaleSessionGenerationError) return;
    if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {
      await loadReport(scope.lessonId);
      if (mutationCurrent(scope))
        setRowError(
          entry.userId,
          'Отметка изменилась. Проверь обновлённые данные и подтверди снятие ещё раз.',
        );
      return;
    }
    const message = cause instanceof Error ? cause.message : 'Не удалось снять отметку.';
    setRowError(entry.userId, message);
    emit('error', cause);
  } finally {
    if (mutationCurrent(scope) && pendingUserId.value === entry.userId) {
      pendingUserId.value = null;
      await nextTick();
      if (mutationCurrent(scope))
        (
          markControls.get(`${entry.userId}:current`) ?? markControls.get(`${entry.userId}:PRESENT`)
        )?.focus();
    }
  }
}

async function applyStatus(
  entry: HeadmanJournalRosterEntry,
  status: 'PRESENT' | 'ABSENT',
): Promise<void> {
  if (pendingUserId.value !== null) return;
  if (!canWrite(entry) || markCondition(entry) === null) {
    notice.value = writeUnavailableMessage(entry);
    return;
  }
  const lessonId = selectedLessonId.value;
  const api = props.api;
  if (!api || lessonId === null) return;
  const scope = mutationScope();
  if (!scope) return;
  pendingUserId.value = entry.userId;
  notice.value = null;
  clearRowError(entry.userId);
  try {
    if (entry.status === status) {
      await clearEntry(entry);
    } else {
      await api.mark(lessonId, entry.userId, { status }, markCondition(entry)!);
      if (!mutationCurrent(scope)) return;
      await loadReport(lessonId);
    }
  } catch (cause) {
    if (!mutationCurrent(scope) || cause instanceof StaleSessionGenerationError) return;
    if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {
      await loadReport(scope.lessonId);
      if (mutationCurrent(scope))
        setRowError(
          entry.userId,
          'Отметка изменилась. Проверь обновлённые данные и повтори действие.',
        );
      return;
    }
    const message = cause instanceof Error ? cause.message : 'Не удалось сохранить отметку.';
    setRowError(entry.userId, message);
    emit('error', cause);
  } finally {
    if (mutationCurrent(scope) && pendingUserId.value === entry.userId) {
      pendingUserId.value = null;
      await nextTick();
      if (mutationCurrent(scope))
        (
          markControls.get(`${entry.userId}:current`) ?? markControls.get(`${entry.userId}:PRESENT`)
        )?.focus();
    }
  }
}

async function clearEntry(entry: HeadmanJournalRosterEntry): Promise<void> {
  const lessonId = selectedLessonId.value;
  const api = props.api;
  if (!api || lessonId === null) return;
  const scope = mutationScope();
  if (!scope) return;
  if (
    !canClearEntry(entry) ||
    !entry.markRevision ||
    (entry.status !== 'PRESENT' && entry.status !== 'ABSENT' && entry.status !== 'EXCUSED')
  ) {
    await refreshMissingRevision(entry);
    return;
  }
  await api.clear(lessonId, entry.userId, {
    expectedStatus: entry.status,
    expectedRevision: entry.markRevision,
  });
  if (!mutationCurrent(scope)) return;
  // A row is updated only after the server ACK. The fresh GET also carries
  // any server-side source, reason, or editability changes.
  await loadReport(lessonId);
}

function onStatusClick(
  entry: HeadmanJournalRosterEntry,
  status: 'PRESENT' | 'ABSENT' | 'EXCUSED',
): void {
  if (status === 'EXCUSED') {
    if (entry.status === 'EXCUSED') requestClearExcuse(entry);
    else openExcuseForm(entry);
    return;
  }
  void applyStatus(entry, status);
}

async function loadReport(lessonId: number): Promise<void> {
  closeClearConfirmation();
  const revision = ++reportRevision;
  attachmentOwnerGeneration.value += 1;
  attachmentStates.value = {};
  loadingReport.value = true;
  error.value = null;
  notice.value = null;
  // A failed refresh must not make the visible request/file disappear or allow a second uncertain edit.
  const previousReport = report.value?.lessonId === lessonId ? report.value : null;
  report.value = previousReport
    ? {
        ...previousReport,
        editable: false,
        entries: previousReport.entries.map((entry) => ({ ...entry, editable: false })),
      }
    : null;
  excuseUserId.value = null;
  excuseFile.value = null;
  const api = props.api;
  if (!api || props.offline) {
    loadingReport.value = false;
    return;
  }
  try {
    const next = await api.getLessonAttendance(lessonId);
    if (
      disposed ||
      api !== props.api ||
      revision !== reportRevision ||
      selectedLessonId.value !== lessonId
    )
      return;
    report.value = next;
    reportAccessDenied.value = false;
  } catch (cause) {
    if (
      disposed ||
      api !== props.api ||
      revision !== reportRevision ||
      selectedLessonId.value !== lessonId
    )
      return;
    if (cause instanceof StaleSessionGenerationError) return;
    if (cause instanceof HeadmanJournalApiError && [401, 403].includes(cause.response.status)) {
      reportAccessDenied.value = true;
      report.value = null;
      linkedTicketEntry.value = null;
      excuseUserId.value = null;
      excuseFile.value = null;
      rowErrors.value = {};
      mutationGeneration += 1;
      pendingUserId.value = null;
      lessonActionRevision += 1;
      closeClearConfirmation();
      closeLessonActionConfirmation();
      attachmentOwnerGeneration.value += 1;
      for (const popup of attachmentPopups) closeAttachmentPopup(popup);
      for (const url of attachmentObjectUrls) releaseAttachmentObjectUrl(url);
      attachmentStates.value = {};
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить состав группы.';
    emit('error', cause);
  } finally {
    if (revision === reportRevision) loadingReport.value = false;
  }
}

async function loadLessons(preferredLessonId: number | null = null): Promise<void> {
  const revision = ++lessonsRevision;
  reportRevision += 1;
  loadingLessons.value = true;
  loadingReport.value = false;
  error.value = null;
  notice.value = null;
  lessons.value = [];
  report.value = null;
  selectedLessonId.value = null;
  const api = props.api;
  if (!api || props.groupId === null || props.offline) {
    loadingLessons.value = false;
    return;
  }
  try {
    const next = await api.listLessons(props.groupId, selectedDate.value, selectedDate.value);
    if (revision !== lessonsRevision) return;
    lessons.value = next;
    const wantsInitialLesson =
      props.initialLessonId !== null && selectedDate.value === props.initialDate;
    const requestedLesson = wantsInitialLesson
      ? (next.find((lesson) => lesson.id === props.initialLessonId) ?? null)
      : null;
    const preferredLesson =
      preferredLessonId === null
        ? null
        : (next.find((lesson) => lesson.id === preferredLessonId) ?? null);
    if (wantsInitialLesson && requestedLesson === null) {
      error.value =
        'Выбранная пара недоступна на этой дате. Вернись в «Сегодня» и выбери занятие ещё раз.';
      return;
    }
    if (preferredLessonId !== null && preferredLesson === null) {
      error.value = 'Перенос завершён, но новая пара не появилась в списке. Обнови журнал ещё раз.';
      return;
    }
    const first = requestedLesson ?? preferredLesson ?? next[0];
    if (first && canViewReport()) {
      selectedLessonId.value = first.id;
      resumeTransferStatusForSelectedLesson();
      await loadReport(first.id);
    } else if (first) {
      selectedLessonId.value = first.id;
      resumeTransferStatusForSelectedLesson();
      report.value = null;
    }
  } catch (cause) {
    if (revision !== lessonsRevision) return;
    if (cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof HeadmanJournalApiError &&
      (cause.response.status === 401 || cause.response.status === 403)
        ? 'Журнал недоступен для текущей роли.'
        : cause instanceof Error
          ? cause.message
          : 'Не удалось загрузить пары.';
    emit('error', cause);
  } finally {
    if (revision === lessonsRevision) loadingLessons.value = false;
  }
}

watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      props.readOnly,
      selectedDate.value,
      selectedLessonId.value,
      assistantPermissionSnapshot()?.join('\u0000'),
    ] as const,
  () => {
    mutationGeneration += 1;
    editingUserId.value = null;
    pendingUserId.value = null;
    excuseUserId.value = null;
    excuseFile.value = null;
    closeClearConfirmation();
  },
  { flush: 'sync' },
);

watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      selectedDate.value,
      props.assistantPermissions,
    ] as const,
  () => {
    const preferredId = preferredLessonAfterDateChange;
    preferredLessonAfterDateChange = null;
    activeLessonsLoad = loadLessons(preferredId);
  },
  { immediate: true, flush: 'sync' },
);

watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      props.readOnly,
      assistantPermissionSnapshot()?.join('\u0000') ?? null,
      selectedDate.value,
      selectedLessonId.value,
    ] as const,
  (next, previous) => {
    transferContextGeneration += 1;
    transferTargetRevision += 1;
    transferTargetLoading.value = false;
    const ownerChanged = next[0] !== previous[0] || next[1] !== previous[1];
    if (ownerChanged) {
      transferOperations.value = [];
      transferRunRevisions.clear();
    } else {
      transferOperations.value = transferOperations.value.map((operation) => {
        if (!isTransferOperationActive(operation)) return operation;
        return {
          ...operation,
          phase: operation.operationId === null ? 'uncertain' : 'pending',
          message:
            'Контекст журнала изменился. Выбери исходную пару, чтобы безопасно проверить перенос.',
        };
      });
    }
    closeTransferTask();
  },
  { flush: 'sync' },
);

watch(
  () =>
    [
      transferTaskOpen.value,
      transferTargetDate.value,
      selectedDate.value,
      selectedLessonId.value,
      props.api,
      props.groupId,
    ] as const,
  () => {
    void loadTransferTargetLessons();
  },
  { flush: 'sync' },
);

watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      props.assistantPermissions,
      props.reportDownload,
    ] as const,
  () => {
    void loadWeeklyExportOptions();
  },
  { immediate: true, flush: 'sync' },
);

watch(
  () => selectedDate.value,
  () => {
    invalidateWeeklyExport();
    resetWeeklySelectionForDate();
  },
  { flush: 'sync' },
);

watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      props.readOnly,
      props.assistantPermissions,
      assistantPermissionSnapshot()?.join('\u0000'),
      selectedDate.value,
      selectedLessonId.value,
      selectedLesson.value?.id,
      selectedLesson.value?.status,
      transferTargetDate.value,
      transferTargetLessonNumber.value,
      transferTargetLessonsDate.value,
      cancelReason.value.trim(),
      canCancelLessons(),
    ] as const,
  invalidateLessonActionConfirmation,
  { flush: 'sync' },
);

watch(
  () => [props.api, props.groupId, selectedLessonId.value] as const,
  () => {
    attachmentOwnerGeneration.value += 1;
    attachmentOwnerIdentity.value = props.api
      ? `headman-journal-${attachmentOwnerGeneration.value}`
      : null;
    attachmentStates.value = {};
  },
  { flush: 'sync', immediate: true },
);

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('online', reconnectWeeklyExport);
  manualPreviewOwner.dispose();
  disposed = true;
  mutationGeneration += 1;
  closeClearConfirmation();
  closeLessonActionConfirmation();
  lessonActionRevision += 1;
  attachmentDisposed = true;
  lessonsRevision += 1;
  reportRevision += 1;
  weeklyOptionsRevision += 1;
  invalidateWeeklyExport();
  weeklyExportAbort?.abort();
  weeklyExportOptions.value = null;
  attachmentOwnerGeneration.value += 1;
  for (const popup of attachmentPopups) closeAttachmentPopup(popup);
  for (const url of attachmentObjectUrls) releaseAttachmentObjectUrl(url);
});
</script>

<template>
  <main
    class="headman-journal"
    :class="{ 'headman-journal--manual': excuseUserId !== null }"
    :aria-busy="busy"
    aria-labelledby="headman-journal-title"
  >
    <header class="headman-journal__header">
      <h1 id="headman-journal-title">
        {{
          linkedTicketEntry ? 'Информация о заявке' : excuseUserId !== null ? 'Причина «у»' : 'Учёт'
        }}
      </h1>
    </header>

    <nav
      v-if="excuseUserId === null && linkedTicketEntry === null"
      class="headman-journal__date-nav"
      aria-label="Выбор даты"
    >
      <button
        type="button"
        aria-label="Предыдущая пара или день"
        @click="pageLesson(-1)"
      >
        <MobileIcon name="back" />
      </button>
      <output>{{ formatShortDate(selectedDate)
      }}<template v-if="selectedLesson">
        · {{ selectedLesson.lessonNumber ?? '—' }} пара</template></output>
      <button
        type="button"
        aria-label="Следующая пара или день"
        @click="pageLesson(1)"
      >
        <MobileIcon
          class="headman-journal__next"
          name="back"
        />
      </button>
    </nav>

    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Журнал доступен при подключении к интернету."
    />
    <StudentWarningBlock
      v-else-if="error"
      title="Журнал недоступен"
      :message="error"
      :action-label="selectedLessonId !== null && !offline ? 'Повторить загрузку' : ''"
      @action="selectedLessonId !== null && loadReport(selectedLessonId)"
    />
    <p
      v-else-if="groupId === null"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      Не удалось определить группу старосты.
    </p>
    <p
      v-else-if="!api"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      Журнал недоступен: источник данных не подключён.
    </p>
    <p
      v-if="notice"
      class="headman-journal__state headman-journal__state--notice"
      role="status"
    >
      {{ notice }}
    </p>

    <p
      v-if="loadingLessons"
      class="headman-journal__state"
      role="status"
    >
      Загружаем пары…
    </p>
    <p
      v-else-if="!offline && api && groupId !== null && lessons.length === 0"
      class="headman-journal__empty"
      role="status"
    >
      На этот день занятий нет.
    </p>
    <nav
      v-if="selectedLesson && excuseUserId === null && linkedTicketEntry === null"
      class="mobile-toolbar"
      aria-label="Инструменты журнала"
    >
      <button
        v-if="canCancelLessons()"
        type="button"
        @click="toolsOpen = true"
      >
        Управление парой
      </button>
      <button
        v-if="canExportWeekly()"
        type="button"
        @click="weeklyOpen = true"
      >
        Скачать журнал
      </button>
    </nav>
    <p
      v-if="!weeklyOpen && (weeklyExportStatus || weeklyExportError)"
      class="headman-journal__state"
      :class="{ 'headman-journal__state--error': weeklyExportError }"
      :role="weeklyExportError ? 'alert' : 'status'"
    >
      {{ weeklyExportError || weeklyExportStatus }}
    </p>
    <section
      v-if="selectedLesson"
      class="headman-journal__context"
      aria-label="Выбранная пара"
    >
      <h2>{{ subjectName() }}</h2>
      <p>
        <MobileIcon name="clock" />{{ formatTime(selectedLesson.startTime) }}–{{
          formatTime(selectedLesson.endTime)
        }}
      </p><p><MobileIcon name="lesson" />{{ lessonTypeLabel(selectedLesson.lessonType) }}</p>
    </section>

    <section
      v-if="selectedLesson && linkedTicketEntry === null"
      class="headman-journal__roster"
      aria-labelledby="headman-journal-roster-title"
    >
      <h2
        id="headman-journal-roster-title"
        class="headman-journal__visually-hidden"
      >
        Состав группы
      </h2>
      <dialog
        ref="lessonActionConfirmationDialog"
        aria-labelledby="headman-journal-lesson-action-confirmation-title"
        aria-describedby="headman-journal-lesson-action-confirmation-detail"
        @close="pendingLessonActionConfirmation = null"
      >
        <h2 id="headman-journal-lesson-action-confirmation-title">
          {{
            pendingLessonActionConfirmation?.action === 'cancel'
              ? 'Отменить пару?'
              : pendingLessonActionConfirmation?.action === 'restore'
                ? 'Восстановить пару?'
                : 'Перенести пару?'
          }}
        </h2>
        <p id="headman-journal-lesson-action-confirmation-detail">
          <template v-if="pendingLessonActionConfirmation?.action === 'cancel'">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара
            {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }}. Причина:
            {{ pendingLessonActionConfirmation.reason }}. Отмена удалит отметки посещаемости,
            архивирует домашние задания и пересчитает статистику.
          </template>
          <template v-else-if="pendingLessonActionConfirmation?.action === 'restore'">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара
            {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }}. Удалённые отметки
            посещаемости не вернутся.
          </template>
          <template
            v-else-if="
              pendingLessonActionConfirmation?.action === 'transfer' &&
                pendingLessonActionConfirmation.transferRequest
            "
          >
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара
            {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }} →
            {{ formatShortDate(pendingLessonActionConfirmation.transferRequest.targetDate) }} · пара
            {{ pendingLessonActionConfirmation.transferRequest.targetLessonNumber }}. После
            подтверждения отметки, вложения и связанные домашние задания перейдут на новую дату.
          </template>
        </p>
        <button
          type="button"
          data-action="cancel"
          @click="closeLessonActionConfirmation"
        >
          Назад
        </button>
        <button
          type="button"
          :disabled="lessonActionBusy || !lessonActionConfirmationCurrent"
          :data-action="pendingLessonActionConfirmation?.action === 'cancel' ? 'delete' : 'confirm'"
          @click="void confirmLessonAction()"
        >
          {{
            pendingLessonActionConfirmation?.action === 'cancel'
              ? 'Отменить пару'
              : pendingLessonActionConfirmation?.action === 'restore'
                ? 'Восстановить пару'
                : 'Подтвердить перенос'
          }}
        </button>
      </dialog>

      <p
        v-if="canViewReport() && loadingReport"
        class="headman-journal__state"
        role="status"
      >
        Загружаем состав и отметки…
      </p>
      <p
        v-else-if="canViewReport() && report && report.entries.length === 0"
        class="headman-journal__empty"
      >
        Сервер вернул пустой состав группы.
      </p>
      <ol
        v-else-if="canViewReport() && report"
        class="headman-journal__roster-list"
      >
        <li
          v-for="entry in excuseUserId === null
            ? entries
            : entries.filter((row) => row.userId === excuseUserId)"
          :key="entry.userId"
          class="headman-journal__row"
          :data-user-id="entry.userId"
          :data-status="entry.status.toLowerCase()"
        >
          <div
            class="headman-journal__row-main"
            :class="{ 'headman-journal__student-card': excuseUserId !== null }"
          >
            <span
              v-if="excuseUserId === entry.userId"
              class="headman-journal__status-circle"
              data-status="excused"
              aria-label="Уважительная причина, ещё не сохранена"
            >у</span>
            <span class="headman-journal__row-name">{{ entry.displayName }}
              <small
                v-if="excuseUserId === null && entry.source && sourceLabel(entry.source)"
                class="headman-journal__source"
              >{{ sourceLabel(entry.source) }}</small></span>
            <span
              v-if="excuseUserId === null"
              class="headman-journal__row-meta"
            >
              <span v-if="entry.excuseReason">{{ entry.excuseReason }}</span>
              <span v-else-if="entry.excuseType">{{ excuseTypeLabel(entry.excuseType) }}</span>
              <button
                v-if="entry.status === 'EXCUSED' && canWrite(entry) && excuseUserId === null"
                class="headman-journal__reason-edit"
                type="button"
                @click="openExcuseForm(entry)"
              >
                Изменить причину
              </button>
              <span
                v-if="entry.attachmentId && canManageExcuses()"
                class="headman-journal__attachment-details"
              >
                <button
                  class="headman-journal__attachment"
                  type="button"
                  :disabled="offline || attachmentState(entry)?.status === 'pending'"
                  :aria-busy="attachmentState(entry)?.status === 'pending'"
                  @click="openAttachment(entry)"
                >
                  {{
                    attachmentState(entry)?.status === 'pending'
                      ? 'Открываем…'
                      : attachmentState(entry)?.status === 'error'
                        ? 'Повторить файл'
                        : 'Открыть файл'
                  }}
                </button>
                <span v-if="entry.attachmentName">
                  {{ entry.attachmentName
                  }}<span v-if="entry.attachmentSize !== null">
                    ({{ formatFileSize(entry.attachmentSize) }})</span>
                </span>
                <span
                  v-if="attachmentState(entry)?.status === 'error'"
                  class="headman-journal__attachment-error"
                  role="alert"
                >
                  {{ attachmentState(entry)?.error || 'Не удалось открыть вложение.' }}
                </span>
              </span>
            </span>
          </div>

          <button
            v-if="entry.approvedTicket && canManageExcuses()"
            class="headman-journal__ticket-link"
            type="button"
            :disabled="offline || pendingUserId !== null"
            @click="openLinkedTicket(entry)"
          >
            Информация о заявке
          </button>

          <div
            v-if="!lessonNotStarted && (entry.status === 'EMPTY' || editingUserId === entry.userId) && excuseUserId === null"
            class="headman-journal__picker"
            role="group"
            :aria-label="`Отметка: ${entry.displayName}`"
          >
            <button
              v-for="choice in ['PRESENT', 'EXCUSED', 'ABSENT'] as const"
              :key="choice"
              :ref="element => rememberMarkControl(`${entry.userId}:${choice}`, element)"
              type="button"
              :disabled="
                pendingUserId !== null ||
                  !canWrite(entry) ||
                  (choice === 'EXCUSED' && !canManageExcuses())
              "
              :aria-label="`${statusLabel(choice)}: ${entry.displayName}`"
              :aria-pressed="entry.status === choice"
              @click="onStatusClick(entry, choice)"
            >
              <span
                class="headman-journal__status-circle"
                data-status="empty"
              >{{ statusSymbol(choice) }}</span>
            </button>
          </div>
          <button
            v-else-if="!lessonNotStarted && excuseUserId === null"
            :ref="element => rememberMarkControl(`${entry.userId}:current`, element)"
            class="headman-journal__current"
            type="button"
            :disabled="pendingUserId !== null || !canClearEntry(entry)"
            :aria-label="`${statusLabel(entry.status)}: ${entry.displayName}. Открыть выбор отметки`"
            @click="openMarkChoices(entry)"
          >
            <span
              class="headman-journal__status-circle"
              :data-status="entry.status.toLowerCase()"
            >{{ statusSymbol(entry.status) }}</span>
          </button>

          <button
            v-if="!lessonNotStarted && entry.status !== 'EMPTY' && editingUserId !== entry.userId && excuseUserId === null"
            class="headman-journal__current"
            type="button"
            :disabled="pendingUserId !== null || !canWrite(entry) || !entry.markRevision"
            :aria-label="`Изменить отметку: ${entry.displayName}`"
            @click="openMarkChoices(entry)"
          >
            <MobileIcon name="edit" />
          </button>
          <p
            v-if="lessonNotStarted"
            class="headman-journal__future"
          >
            Пара ещё не началась
          </p>

          <button
            v-if="entry.status !== 'EMPTY' && canWrite(entry) && !entry.markRevision"
            class="headman-journal__refresh"
            type="button"
            @click="refreshMissingRevision(entry)"
          >
            Обновить версию отметки
          </button>

          <form
            v-if="excuseUserId === entry.userId"
            class="headman-journal__excuse-form"
            @submit.prevent="submitExcuse(entry)"
          >
            <label>
              <span>Причина</span>
              <select
                v-model="excuseType"
                required
              >
                <option
                  value=""
                  disabled
                >Выбери причину</option>
                <option
                  v-for="item in excuseTypes"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              <span>Комментарий</span>
              <textarea
                v-model="excuseComment"
                maxlength="1000"
                rows="3"
              />
            </label>
            <label class="mobile-upload">
              <input
                type="file"
                accept="image/jpeg,image/png,application/pdf"
                @change="chooseExcuseFile"
              >
              <MobileIcon name="homework" /><span>{{
                excuseFile ? 'Заменить файл' : 'Прикрепить файл'
              }}</span>
            </label>
            <section
              v-if="manualPreview.loading || manualPreview.url || manualPreview.error"
              class="headman-journal__manual-preview"
              aria-label="Вложение к причине"
              :aria-busy="manualPreview.loading"
            >
              <p
                v-if="manualPreview.loading"
                role="status"
              >
                Загружаем вложение…
              </p>
              <p
                v-if="manualPreview.error"
                role="alert"
              >
                {{ manualPreview.error }}
              </p>
              <img
                v-if="manualPreview.url && manualPreview.mime !== 'application/pdf'"
                :src="manualPreview.url"
                alt="Вложение к причине"
                @error="manualPreview.error = 'Не удалось показать изображение.'"
              >
              <AttachmentPdfPreview
                v-else-if="manualPreview.url"
                :url="manualPreview.url"
                :open-file="openManualAttachment"
                label="Первая страница PDF к причине"
              />
            </section>
            <div class="headman-journal__excuse-actions">
              <button
                type="submit"
                data-action="confirm"
                :disabled="pendingUserId !== null"
              >
                {{ pendingUserId === entry.userId ? 'Сохраняем…' : 'Сохранить' }}
              </button>
              <button
                type="button"
                :disabled="pendingUserId !== null"
                data-action="cancel"
                @click="closeExcuseForm"
              >
                Отмена
              </button>
            </div>
          </form>

          <span
            v-if="pendingUserId === entry.userId"
            class="headman-journal__row-status"
            role="status"
          >
            Сохраняем…
          </span>
          <StudentWarningBlock
            v-else-if="rowErrors[entry.userId]"
            class="headman-journal__row-status"
            title="Отметка не изменена"
            :message="rowErrors[entry.userId]!"
            action-label="Обновить отметку"
            :action-disabled="offline || pendingUserId !== null"
            @action="selectedLessonId !== null && loadReport(selectedLessonId)"
          />
          <span
            v-else-if="
              entry.status !== 'EXCUSED' &&
                entry.status !== 'CANCELLED' &&
                entry.status !== 'UNSUPPORTED' &&
                !canWrite(entry)
            "
            class="headman-journal__row-status"
          >
            {{ writeUnavailableMessage(entry) }}
          </span>
        </li>
      </ol>
    </section>
    <template v-if="excuseUserId === null && linkedTicketEntry === null && selectedLesson">
      <MobileToolsSheet
        v-if="canCancelLessons()"
        ref="toolsSheet"
        v-model:open="toolsOpen"
        icon-close
        title="Управление парой"
        class="headman-journal__lesson-actions"
        aria-labelledby="headman-journal-lesson-actions-title"
      >
        <template v-if="selectedLesson.status === 'CANCELLED'">
          <p>Пара отменена сервером.</p>
          <button
            type="button"
            :disabled="
              lessonActionBusy || offline || readOnly || isLessonTransferLocked(selectedLesson)
            "
            @click="requestRestoreSelectedLesson"
          >
            {{ lessonActionBusy ? 'Восстанавливаем…' : 'Восстановить пару' }}
          </button>
        </template>
        <template v-else-if="selectedLesson.status === 'TRANSFERRED'">
          <p>Исходная пара перенесена. История переноса доступна ниже.</p>
        </template>
        <template v-else>
          <label>
            <span>Причина отмены</span>
            <input
              v-model="cancelReason"
              type="text"
              maxlength="512"
              :disabled="
                lessonActionBusy || offline || readOnly || isLessonTransferLocked(selectedLesson)
              "
              placeholder="Например, преподаватель болен"
            >
          </label>
          <button
            type="button"
            :disabled="
              lessonActionBusy ||
                offline ||
                readOnly ||
                !cancelReason.trim() ||
                isLessonTransferLocked(selectedLesson)
            "
            data-action="delete"
            @click="requestCancelSelectedLesson"
          >
            {{ lessonActionBusy ? 'Отменяем…' : 'Отменить пару' }}
          </button>
          <button
            v-if="canStartTransfer(selectedLesson) && !transferTaskOpen"
            type="button"
            :disabled="lessonActionBusy || offline || readOnly"
            @click="openTransferTask"
          >
            Перенести пару
          </button>
        </template>

        <section
          v-if="transferTaskOpen"
          class="headman-journal__transfer-task"
          aria-labelledby="headman-journal-transfer-title"
        >
          <h4 id="headman-journal-transfer-title">
            Новая дата и номер пары
          </h4>
          <p>
            {{ formatShortDate(selectedLesson.date) }} · пара
            {{ selectedLesson.lessonNumber ?? '—' }}
          </p>
          <label>
            <span>Новая дата</span>
            <input
              v-model="transferTargetDate"
              type="date"
              :min="transferTargetDateMin"
              :disabled="lessonActionBusy || offline || readOnly"
            >
          </label>
          <label>
            <span>Номер пары</span>
            <select
              v-model.number="transferTargetLessonNumber"
              :disabled="
                lessonActionBusy ||
                  offline ||
                  readOnly ||
                  transferTargetLoading ||
                  transferTargetLessonsDate !== transferTargetDate ||
                  transferTargetError !== null
              "
            >
              <option
                :value="null"
                disabled
              >
                Выбери свободное место
              </option>
              <option
                v-for="slot in transferTargetSlots"
                :key="slot"
                :value="slot"
                :disabled="isTransferTargetSlotUnavailable(slot)"
              >
                {{ transferTargetSlotLabel(slot) }}
              </option>
            </select>
          </label>
          <p
            v-if="transferTargetLoading"
            role="status"
          >
            Проверяем расписание на выбранную дату…
          </p>
          <div
            v-else-if="transferTargetError"
            class="headman-journal__transfer-error"
            role="alert"
          >
            {{ transferTargetError }}
            <button
              type="button"
              @click="void loadTransferTargetLessons()"
            >
              Проверить дату ещё раз
            </button>
          </div>
          <p
            v-else-if="
              transferTargetLessonsDate === transferTargetDate &&
                availableTransferTargetSlots.length === 0
            "
            role="status"
          >
            На эту дату нет свободных мест для переноса.
          </p>
          <div class="headman-journal__transfer-task-actions">
            <button
              type="button"
              :disabled="!canSubmitTransfer"
              @click="requestTransferSelectedLesson"
            >
              Продолжить
            </button>
            <button
              type="button"
              class="headman-journal__transfer-secondary"
              @click="closeTransferTask"
            >
              Закрыть
            </button>
          </div>
        </section>

        <section
          v-if="
            isHeadmanContext() &&
              (selectedTransferOperation ||
                (selectedLesson.status === 'TRANSFERRED' && selectedServerTransferState !== null))
          "
          class="headman-journal__transfer-status"
          aria-labelledby="headman-journal-transfer-status-title"
        >
          <h4 id="headman-journal-transfer-status-title">
            Состояние переноса
          </h4>
          <p role="status">
            {{
              selectedTransferOperation
                ? transferPhaseMessage(selectedTransferOperation)
                : serverTransferStateMessage(selectedServerTransferState)
            }}
          </p>
          <p
            v-if="
              selectedTransferOperation?.targetDate ||
                selectedTransferOperation?.response?.targetDate
            "
          >
            Новая дата:
            {{
              formatShortDate(
                selectedTransferOperation?.targetDate ??
                  selectedTransferOperation?.response?.targetDate ??
                  '',
              )
            }}
          </p>
          <button
            v-if="canCheckSelectedTransferStatus()"
            type="button"
            :disabled="
              offline ||
                readOnly ||
                lessonActionBusy ||
                selectedTransferOperation?.phase === 'submitting' ||
                selectedTransferOperation?.phase === 'checking'
            "
            @click="resumeTransferForSelectedLesson(false)"
          >
            Проверить состояние
          </button>
          <button
            v-if="
              selectedTransferOperation?.phase === 'uncertain' && selectedTransferOperation.request
            "
            type="button"
            :disabled="offline || readOnly || lessonActionBusy"
            @click="resumeTransferForSelectedLesson(true)"
          >
            Повторить тот же запрос
          </button>
        </section>
      </MobileToolsSheet>
      <MobileToolsSheet
        v-if="canExportWeekly()"
        ref="weeklySheet"
        v-model:open="weeklyOpen"
        icon-close
        title="Скачать недельный журнал"
        class="headman-journal__weekly-export"
        aria-labelledby="headman-journal-weekly-export-title"
        :aria-busy="loadingWeeklyOptions || weeklyExportBusy"
      >
        <p
          v-if="loadingWeeklyOptions"
          class="headman-journal__state"
          role="status"
        >
          Загружаем серверный список недель и форматов…
        </p>
        <p
          v-else-if="weeklyOptionsError && !weeklyExportOptions"
          class="headman-journal__state headman-journal__state--error"
          role="alert"
        >
          {{ weeklyOptionsError }}
        </p>
        <div
          v-else-if="weeklyExportOptions"
          class="headman-journal__weekly-export-controls"
        >
          <details class="headman-journal__weekly-dropdown">
            <summary>Выбрать недели<MobileIcon name="chevron-down" /></summary>
            <fieldset
              class="headman-journal__weekly-weeks"
              :disabled="weeklyExportBusy"
            >
              <legend class="headman-journal__visually-hidden">
                Выбери недели
              </legend>
              <label
                v-for="week in weeklyExportOptions.weeks"
                :key="week.weekStart"
                class="headman-journal__weekly-week"
              >
                <input
                  type="checkbox"
                  :checked="selectedWeeklyStarts.includes(week.weekStart)"
                  :aria-label="`${week.label}, ${formatShortDate(week.weekStart)}–${formatShortDate(week.weekEnd)}`"
                  @change="
                    toggleWeeklyWeek(week.weekStart, ($event.target as HTMLInputElement).checked)
                  "
                >
                <span>
                  {{ week.label }} · {{ formatShortDate(week.weekStart) }}–{{
                    formatShortDate(week.weekEnd)
                  }}
                </span>
              </label>
            </fieldset>
          </details>
          <label class="headman-journal__weekly-format">
            <span>Формат файла</span>
            <select
              v-model="weeklyExportFormatCode"
              :disabled="weeklyExportBusy"
            >
              <option
                v-for="format in weeklyExportOptions.formats"
                :key="format.code"
                :value="format.code"
              >
                {{ format.label }}
              </option>
            </select>
          </label>
          <button
            class="headman-journal__weekly-download"
            type="button"
            :disabled="weeklyExportBusy || selectedWeeklyCount === 0 || !selectedWeeklyFormat"
            :aria-busy="weeklyExportBusy"
            @click="downloadWeeklyExport()"
          >
            {{ weeklyExportBusy ? 'Готовим файл…' : 'Скачать' }}
          </button>
          <p
            v-if="weeklyExportError"
            class="headman-journal__state headman-journal__state--error"
            role="alert"
          >
            {{ weeklyExportError }}
          </p>
          <p
            v-if="weeklyExportStatus"
            class="headman-journal__state"
            role="status"
          >
            {{ weeklyExportStatus }}
          </p>
        </div>
      </MobileToolsSheet>
    </template>
    <HeadmanLinkedRequest
      v-if="linkedTicketEntry?.approvedTicket"
      :api="requestsApi"
      :ticket="linkedTicketEntry.approvedTicket"
      :offline="offline"
      :allowed="canManageExcuses()"
      @error="emit('error', $event)"
    />
    <dialog
      ref="clearConfirmationDialog"
      class="headman-journal__clear-dialog"
      aria-labelledby="headman-journal-clear-title"
      aria-describedby="headman-journal-clear-description"
      @close="clearConfirmation = null"
      @cancel="closeClearConfirmation"
    >
      <h2 id="headman-journal-clear-title">
        Снять отметку «у»?
      </h2>
      <p v-if="clearConfirmation">
        {{ clearConfirmation.entry.displayName }} · {{ formatShortDate(selectedDate) }} · пара
        {{ selectedLesson?.lessonNumber ?? '—' }}
      </p>
      <p id="headman-journal-clear-description">
        Отметка и файл, прикреплённый вручную к журналу, будут удалены. Заявка студента, её файлы и
        история сохранятся в заявках.
      </p>
      <div class="headman-journal__excuse-actions">
        <button
          type="button"
          autofocus
          data-action="cancel"
          @click="closeClearConfirmation"
        >
          Отмена
        </button>
        <button
          type="button"
          data-action="clear"
          @click="confirmClearExcuse"
        >
          Снять «у»
        </button>
      </div>
    </dialog>
  </main>
</template>
