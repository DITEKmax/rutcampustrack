<script setup lang="ts">
import {
  exportStateLabel,
  reportDownloadAcceptedMessage,
} from '../../shared/report-download-client';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import {
  HeadmanGroupApiError,
  type HeadmanAssistantPermission,
  type HeadmanAssistant,
  type HeadmanGroupApi,
  type HeadmanGroupMember,
  type HeadmanPermissionOption,
  type HeadmanRosterFormat,
  type HeadmanLateCheckinBudget,
  type HeadmanBudgetIncrease,
} from './headman-group-client';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import type { ReportDownloadFormat, ReportDownloadPort } from '../../shared/report-download-client';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import MobileToolsSheet from '../../shared/components/MobileToolsSheet.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import {
  PermissionConfirmationFlow,
  type PermissionConfirmationState,
  type PermissionConfirmationContext,
} from './permission-confirmation';
import './headman-group-screen.pcss';
import { useHeadmanCredentials } from './use-headman-credentials';
const props = withDefaults(
  defineProps<{
    api: HeadmanGroupApi | null;
    groupId: number | null;
    actorUserId?: string | null;
    actorHeadman?: boolean;
    offline?: boolean;
    readOnly?: boolean;
    assistantPermissions?: readonly HeadmanAssistantPermission[] | null;
    reportDownload?: ReportDownloadPort | null;
  }>(),
  {
    actorUserId: null,
    actorHeadman: false,
    offline: false,
    readOnly: false,
    assistantPermissions: null,
    reportDownload: null,
  },
);
const emit = defineEmits<{ error: [cause: unknown]; back: [] }>();
const members = ref<readonly HeadmanGroupMember[]>([]);
const assistants = ref<readonly HeadmanAssistant[]>([]);
const options = ref<readonly HeadmanPermissionOption[]>([]);
const hasSnapshot = ref(false);
const loading = ref(false),
  busy = ref(false),
  denied = ref(false);
const error = ref<string | null>(null),
  notice = ref<string | null>(null);
const query = ref(''),
  view = ref<'list' | 'detail' | 'permissions'>('list');
const memberId = ref<number | null>(null);
const draft = ref<HeadmanAssistantPermission[]>([]),
  baseline = ref<HeadmanAssistantPermission[]>([]);
const dialog = ref<'leave' | 'confirm' | null>(null),
  rosterOpen = ref(false);
const sheet = ref<InstanceType<typeof MobileToolsSheet> | null>(null);
interface PermissionIntent {
  memberId: number;
  assistantId: number | null;
  permissions: readonly HeadmanAssistantPermission[];
  before: readonly HeadmanAssistantPermission[];
}
const intent = ref<PermissionIntent | null>(null);
const confirmationFlow = new PermissionConfirmationFlow();
const confirmation = ref<PermissionConfirmationState | null>(null);
const budget = ref<HeadmanLateCheckinBudget | null>(null);
const budgetLoading = ref(false),
  budgetBusy = ref(false);
const budgetError = ref<string | null>(null),
  budgetNotice = ref<string | null>(null);
const budgetIncrease = ref('');
const budgetIntent = ref<(HeadmanBudgetIncrease & { studentId: number }) | null>(null);
let budgetRevision = 0;
const baselineAssistantId = ref<number | null>(null);
let loadRevision = 0,
  ownerRevision = 0,
  disposed = false;
const rosterFormats = ref<readonly HeadmanRosterFormat[]>([]);
const rosterFormat = ref<ReportDownloadFormat | null>(null);
const rosterLoading = ref(false);
const rosterDownloading = ref(false);
const rosterError = ref<string | null>(null);
const rosterNotice = ref<string | null>(null);
let rosterRevision = 0;
let downloadRevision = 0;
let rosterController: AbortController | null = null;
const isHeadman = computed(() => props.assistantPermissions === null);
const activeRosterFormat = computed(
  () => rosterFormats.value.find((format) => format.code === rosterFormat.value) ?? null,
);

function invalidateDownload(): void {
  downloadRevision += 1;
  rosterController?.abort();
  rosterController = null;
  rosterDownloading.value = false;
  rosterError.value = null;
  rosterNotice.value = null;
}

async function loadRosterFormats(): Promise<void> {
  const revision = ++rosterRevision;
  rosterFormats.value = [];
  rosterFormat.value = null;
  const api = props.api;
  if (!api || props.groupId === null || props.offline || !isHeadman.value) {
    rosterLoading.value = false;
    return;
  }
  rosterLoading.value = true;
  try {
    const formats = await api.listRosterFormats();
    if (revision !== rosterRevision) return;
    rosterFormats.value = formats;
    rosterFormat.value = formats[0]?.code ?? null;
  } catch (cause) {
    if (revision !== rosterRevision || cause instanceof StaleSessionGenerationError) return;
    rosterError.value =
      cause instanceof Error ? cause.message : 'Не удалось загрузить форматы состава группы.';
    emit('error', cause);
  } finally {
    if (revision === rosterRevision) rosterLoading.value = false;
  }
}

async function downloadRoster(): Promise<void> {
  const api = props.api;
  const format = activeRosterFormat.value;
  const groupId = props.groupId;
  const reportDownload = props.reportDownload;
  if (
    !api ||
    !format ||
    groupId === null ||
    props.offline ||
    !isHeadman.value ||
    rosterDownloading.value
  )
    return;
  const revision = ++downloadRevision;
  const isCurrent = (): boolean =>
    revision === downloadRevision &&
    api === props.api &&
    groupId === props.groupId &&
    reportDownload === props.reportDownload &&
    !props.offline &&
    isHeadman.value &&
    format.code === rosterFormat.value;
  rosterDownloading.value = true;
  rosterError.value = null;
  rosterNotice.value = null;
  try {
    if (reportDownload) {
      const result = await reportDownload.download(
        {
          kind: 'HEADMAN_GROUP_COMPOSITION',
          headmanGroupComposition: { format: format.code },
        },
        isCurrent,
        (state) => {
          if (isCurrent()) rosterNotice.value = exportStateLabel(state);
        },
      );
      if (result === 'stale' || !isCurrent()) return;
      if (result === 'unsupported') {
        rosterError.value =
          'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.';
      } else {
        rosterNotice.value =
          result === 'accepted'
            ? reportDownloadAcceptedMessage(reportDownload)
            : 'Скачивание отменено.';
      }
      return;
    }
    rosterController = new AbortController();
    const downloaded = await api.downloadRoster(format, rosterController.signal);
    if (!isCurrent()) return;
    const url = URL.createObjectURL(downloaded.blob);
    const anchor = document.createElement('a');
    try {
      anchor.href = url;
      anchor.download = downloaded.filename;
      anchor.rel = 'noopener';
      document.body.append(anchor);
      anchor.click();
      rosterNotice.value = 'Файл передан браузеру для скачивания.';
    } finally {
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
    }
  } catch (cause) {
    if (!isCurrent() || cause instanceof StaleSessionGenerationError) return;
    rosterError.value =
      cause instanceof Error ? cause.message : 'Не удалось скачать состав группы.';
    emit('error', cause);
  } finally {
    if (revision === downloadRevision) {
      rosterDownloading.value = false;
      rosterController = null;
    }
  }
}

const member = computed(() => members.value.find((item) => item.id === memberId.value) ?? null);
const helper = computed(
  () =>
    assistants.value.find(
      (item) => item.active && item.groupId === props.groupId && item.studentId === memberId.value,
    ) ?? null,
);
const filteredMembers = computed(() =>
  members.value.filter((item) =>
    item.fullName.toLocaleLowerCase('ru').includes(query.value.trim().toLocaleLowerCase('ru')),
  ),
);
const changedCount = computed(
  () =>
    [...new Set([...draft.value, ...baseline.value])].filter(
      (code) => draft.value.includes(code) !== baseline.value.includes(code),
    ).length,
);
const writable = computed(
  () =>
    !props.offline &&
    !props.readOnly &&
    !denied.value &&
    isHeadman.value &&
    !loading.value &&
    !busy.value &&
    !intent.value &&
    member.value?.status === 'ACTIVE' &&
    !isMemberHeadman(member.value),
);
const credentialConfirmation = ref<{
  studentId: number;
  action: 'generate' | 'link';
  version: string;
} | null>(null);
function credentialEligible(item: HeadmanGroupMember): boolean {
  return !item.roles?.some((grant) => grant.role === 'ADMIN' && grant.status === 'ACTIVE');
}
const credentials = useHeadmanCredentials(() => ({
  api: props.api,
  groupId: props.groupId,
  actorUserId: props.actorUserId,
  enabled:
    hasSnapshot.value &&
    isHeadman.value &&
    props.actorHeadman &&
    !props.offline &&
    !props.readOnly &&
    !denied.value,
  memberIds: (view.value === 'list' ? members.value : member.value ? [member.value] : [])
    .filter(credentialEligible)
    .map((item) => item.id),
}));
function credentialLabel(studentId: number): string {
  const value = credentials.values.value[studentId];
  if (value?.state === 'STUDENT_CHANGED') return 'Пароль сменён студентом';
  if (value?.state === 'GENERATED')
    return `${value.origin === 'initial' ? 'Начальный пароль' : value.origin === 'admin_reset' ? 'Сброшен администратором' : 'Сброшен старостой'}: ${value.password}`;
  return 'Пароль недоступен';
}
function confirmCredential(action: 'generate' | 'link'): void {
  if (!member.value || !credentials.available.value || budgetBusy.value || budgetIntent.value)
    return;
  const value = credentials.values.value[member.value.id];
  if (value)
    credentialConfirmation.value = {
      studentId: member.value.id,
      action,
      version: value.credentialsVersion,
    };
}
async function commitCredential(): Promise<void> {
  const pending = credentialConfirmation.value;
  credentialConfirmation.value = null;
  if (!pending || member.value?.id !== pending.studentId) return;
  await credentials.perform(pending.studentId, pending.action, pending.version);
}
watch(
  () => [
    props.api,
    props.groupId,
    props.actorUserId,
    props.actorHeadman,
    props.offline,
    props.readOnly,
    props.assistantPermissions,
    view.value,
    memberId.value,
    denied.value,
  ],
  () => {
    credentialConfirmation.value = null;
  },
  { flush: 'sync' },
);

const budgetWritable = computed(
  () =>
    !props.offline &&
    !props.readOnly &&
    !denied.value &&
    isHeadman.value &&
    props.actorHeadman &&
    props.actorUserId !== null &&
    props.api !== null &&
    !loading.value &&
    !busy.value &&
    !intent.value &&
    member.value?.status === 'ACTIVE' &&
    member.value.groupId === props.groupId &&
    !budgetLoading.value &&
    !budgetBusy.value &&
    budget.value !== null,
);
const increaseValue = computed(() =>
  /^[1-9]\d*$/.test(budgetIncrease.value) && Number(budgetIncrease.value) <= 2147483647
    ? Number(budgetIncrease.value)
    : null,
);

async function loadBudget(): Promise<void> {
  const revision = ++budgetRevision,
    owner = ownerRevision;
  const api = props.api,
    target = member.value?.id;
  budget.value = null;
  if (
    !api ||
    !target ||
    view.value !== 'detail' ||
    !isHeadman.value ||
    !props.actorHeadman ||
    props.offline ||
    props.readOnly ||
    denied.value ||
    member.value?.status !== 'ACTIVE'
  ) {
    budgetLoading.value = false;
    return;
  }
  budgetLoading.value = true;
  budgetError.value = null;
  try {
    const next = await api.getLateCheckinBudget(target);
    if (owner !== ownerRevision || revision !== budgetRevision || disposed || props.offline) return;
    budget.value = next;
  } catch (cause) {
    if (
      owner !== ownerRevision ||
      revision !== budgetRevision ||
      disposed ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    if (cause instanceof HeadmanGroupApiError && [401, 403].includes(cause.response.status)) {
      denied.value = true;
      budgetIntent.value = null;
    }
    budgetError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить лимит.';
    emit('error', cause);
  } finally {
    if (owner === ownerRevision && revision === budgetRevision) budgetLoading.value = false;
  }
}

async function increaseBudget(): Promise<void> {
  if (
    credentials.busy.value ||
    !budgetWritable.value ||
    !member.value ||
    !props.api ||
    !budget.value
  )
    return;
  if (!budgetIntent.value) {
    if (increaseValue.value === null) return;
    budgetIntent.value = {
      studentId: member.value.id,
      semesterId: budget.value.semesterId,
      increase: increaseValue.value,
      requestKey: crypto.randomUUID(),
    };
  }
  const pending = budgetIntent.value,
    api = props.api,
    owner = ownerRevision;
  const revision = budgetRevision;
  budgetBusy.value = true;
  budgetError.value = null;
  budgetNotice.value = null;
  try {
    await api.increaseLateCheckinBudget(pending.studentId, {
      semesterId: pending.semesterId,
      increase: pending.increase,
      requestKey: pending.requestKey,
    });
    if (owner !== ownerRevision || revision !== budgetRevision || disposed) return;
    // An exact replay returns the original snapshot; only a fresh read is the current counter.
    await loadBudget();
    if (owner !== ownerRevision || disposed || !budget.value) return;
    budgetIntent.value = null;
    budgetIncrease.value = '';
    budgetNotice.value = 'Лимит увеличен. Остаток подтверждён сервером.';
  } catch (cause) {
    if (
      owner !== ownerRevision ||
      revision !== budgetRevision ||
      disposed ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    if (
      cause instanceof HeadmanGroupApiError &&
      cause.response.status >= 400 &&
      cause.response.status < 500
    ) {
      budgetIntent.value = null;
      if ([401, 403].includes(cause.response.status)) {
        denied.value = true;
        budget.value = null;
      }
      budgetError.value = cause.message;
    } else budgetError.value = 'Ответ не подтверждён. Повтори увеличение с тем же запросом.';
    emit('error', cause);
  } finally {
    if (owner === ownerRevision && !disposed) budgetBusy.value = false;
  }
}
function isMemberHeadman(item: HeadmanGroupMember | null): boolean {
  if (!item) return false;
  if (item.roles !== null && item.roles !== undefined)
    return item.roles.some(
      (grant) =>
        grant.role === 'HEADMAN' && grant.status === 'ACTIVE' && grant.groupId === props.groupId,
    );
  return (
    props.actorHeadman && String(item.id) === props.actorUserId && item.groupId === props.groupId
  );
}
function roleLabel(item: HeadmanGroupMember): string {
  return isMemberHeadman(item)
    ? 'Староста'
    : assistants.value.some(
          (value) => value.active && value.groupId === props.groupId && value.studentId === item.id,
        )
      ? 'Помощник старосты'
      : 'Студент';
}
function openMember(item: HeadmanGroupMember): void {
  memberId.value = item.id;
  view.value = 'detail';
  error.value = null;
  notice.value = null;
}
function openPermissions(): void {
  if (credentials.busy.value || credentialConfirmation.value) return;
  if (budgetBusy.value || budgetIntent.value) return;
  if (!member.value || isMemberHeadman(member.value)) return;
  baselineAssistantId.value = helper.value?.id ?? null;
  baseline.value = [...(helper.value?.permissions ?? [])];
  draft.value = [...baseline.value];
  view.value = 'permissions';
}
function toggle(code: HeadmanAssistantPermission): void {
  if (!writable.value) return;
  draft.value = draft.value.includes(code)
    ? draft.value.filter((item) => item !== code)
    : [...draft.value, code];
}
function goBack(): void {
  if (view.value === 'permissions') {
    view.value = 'detail';
    draft.value = [...baseline.value];
  } else if (view.value === 'detail') {
    view.value = 'list';
    memberId.value = null;
  } else emit('back');
}
function back(): boolean {
  if (credentials.busy.value) return true;
  if (credentialConfirmation.value) {
    credentialConfirmation.value = null;
    return true;
  }
  if (budgetBusy.value || budgetIntent.value) {
    budgetError.value =
      'Результат увеличения ещё не подтверждён. Повтори запрос или проверь лимит.';
    return true;
  }
  if (rosterOpen.value) {
    sheet.value?.close();
    rosterOpen.value = false;
    return true;
  }
  if (dialog.value) {
    closeDialog();
    return true;
  }
  if (busy.value || intent.value) {
    error.value = 'Результат сохранения ещё не подтверждён. Проверь состояние перед выходом.';
    return true;
  }
  if (view.value === 'permissions' && changedCount.value) {
    dialog.value = 'leave';
    return true;
  }
  if (view.value !== 'list') {
    goBack();
    return true;
  }
  return false;
}
function clickBack(): void {
  if (!back()) emit('back');
}
function matches(nextAssistants: readonly HeadmanAssistant[], pending: PermissionIntent): boolean {
  const actual = nextAssistants.find(
    (item) => item.active && item.groupId === props.groupId && item.studentId === pending.memberId,
  );
  return pending.permissions.length === 0
    ? !actual
    : Boolean(
        actual &&
          actual.permissions.length === pending.permissions.length &&
          pending.permissions.every((code) => actual.permissions.includes(code)),
      );
}
function acceptIntent(nextAssistants: readonly HeadmanAssistant[]): void {
  if (
    props.readOnly ||
    !isHeadman.value ||
    denied.value ||
    !intent.value ||
    !matches(nextAssistants, intent.value)
  )
    return;
  baselineAssistantId.value =
    nextAssistants.find(
      (item) =>
        item.active && item.groupId === props.groupId && item.studentId === intent.value?.memberId,
    )?.id ?? null;
  baseline.value = [...intent.value.permissions];
  draft.value = [...baseline.value];
  intent.value = null;
  notice.value = 'Изменения подтверждены сервером.';
  error.value = null;
}
async function load(): Promise<void> {
  const revision = ++loadRevision,
    owner = ownerRevision,
    api = props.api,
    groupId = props.groupId;
  if (!api || groupId === null || props.offline) {
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = null;
  try {
    const [nextMembers, nextOptions, nextAssistants] = await Promise.all([
      api.listMembers(groupId),
      api.listPermissionCatalog(),
      api.listAssistants(groupId),
    ]);
    if (revision !== loadRevision || owner !== ownerRevision || disposed || props.offline) return;
    hasSnapshot.value = true;
    members.value = nextMembers.filter((item) => item.groupId === groupId);
    options.value = nextOptions;
    assistants.value = nextAssistants;
    if (!intent.value && view.value === 'permissions' && !baselineMatchesCurrent()) {
      baselineAssistantId.value = helper.value?.id ?? null;
      baseline.value = [...(helper.value?.permissions ?? [])];
      closeDialog();
      notice.value =
        'Права обновились. Проверь изменения относительно текущих прав и подтверди их заново.';
    }
    acceptIntent(nextAssistants);
    if (intent.value)
      error.value =
        'Список обновлён, но результат сохранения ещё не подтверждён. Повторный запрос заблокирован; проверь состояние позже.';
  } catch (cause) {
    if (
      revision !== loadRevision ||
      owner !== ownerRevision ||
      disposed ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    if (cause instanceof HeadmanGroupApiError && [401, 403].includes(cause.response.status))
      denied.value = true;
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить группу.';
    emit('error', cause);
  } finally {
    if (revision === loadRevision && owner === ownerRevision) loading.value = false;
  }
}
function save(): void {
  if (!writable.value || !changedCount.value || !member.value) return;
  if (!draft.value.length && !helper.value) return;
  if (!baselineMatchesCurrent()) {
    error.value = 'Права изменились. Открой настройку заново и проверь изменения.';
    return;
  }
  confirmation.value = confirmationFlow.start(confirmationContext(), options.value);
  if (!confirmation.value) {
    error.value = 'Не удалось подтвердить полный список изменений прав. Обнови данные.';
    return;
  }
  dialog.value = 'confirm';
}
function samePermissions(
  left: readonly HeadmanAssistantPermission[],
  right: readonly HeadmanAssistantPermission[],
): boolean {
  return left.length === right.length && left.every((code) => right.includes(code));
}
function baselineMatchesCurrent(): boolean {
  return (
    baselineAssistantId.value === (helper.value?.id ?? null) &&
    samePermissions(baseline.value, helper.value?.permissions ?? [])
  );
}
function confirmationContext(): PermissionConfirmationContext {
  return {
    ownerRevision,
    actorUserId: props.actorUserId,
    groupId: props.groupId!,
    memberId: member.value!.id,
    memberName: member.value!.fullName,
    assistantId: helper.value?.id ?? null,
    helperIdentity: JSON.stringify(helper.value),
    before: baseline.value,
    after: draft.value,
  };
}
function closeDialog(): void {
  confirmationFlow.cancel();
  confirmation.value = null;
  dialog.value = null;
}
async function commit(): Promise<void> {
  if (
    dialog.value !== 'confirm' ||
    !writable.value ||
    !member.value ||
    !props.api ||
    props.groupId === null
  )
    return;
  const result = confirmationFlow.confirm(confirmationContext());
  if (result.kind === 'invalid' || !baselineMatchesCurrent()) {
    closeDialog();
    error.value = 'Права изменились. Проверь список и подтверди изменения заново.';
    return;
  }
  if (result.kind === 'next') {
    confirmation.value = result.state;
    return;
  }
  const confirmed = result.snapshot;
  const api = props.api,
    groupId = props.groupId,
    owner = ownerRevision;
  const pending: PermissionIntent = {
    memberId: member.value.id,
    assistantId: confirmed.assistantId,
    permissions: [...confirmed.after],
    before: [...confirmed.before],
  };
  if (!pending.permissions.length && pending.assistantId === null) return;
  closeDialog();
  intent.value = pending;
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    if (pending.assistantId !== null)
      await api.updatePermissions(pending.assistantId, pending.permissions, pending.before);
    else await api.assignAssistant(groupId, pending.memberId, pending.permissions);
    if (owner !== ownerRevision || disposed) return;
    if (!props.offline) await load();
  } catch (cause) {
    if (owner !== ownerRevision || disposed || cause instanceof StaleSessionGenerationError) return;
    if (
      cause instanceof HeadmanGroupApiError &&
      cause.response.status >= 400 &&
      cause.response.status < 500
    ) {
      intent.value = null;
      denied.value = [401, 403].includes(cause.response.status);
      error.value = cause.message;
    } else
      error.value =
        'Ответ на сохранение не получен. Проверь состояние; повторный запрос заблокирован, чтобы не создать дубликат.';
    emit('error', cause);
  } finally {
    if (owner === ownerRevision && !disposed) busy.value = false;
  }
}
watch(
  () =>
    [
      view.value,
      memberId.value,
      props.api,
      props.groupId,
      props.actorUserId,
      props.actorHeadman,
      props.assistantPermissions,
      props.offline,
      props.readOnly,
    ] as const,
  () => {
    budgetRevision++;
    budget.value = null;
    budgetLoading.value = false;
    budgetError.value = null;
    budgetNotice.value = null;
    if (!props.actorHeadman || !isHeadman.value || props.readOnly) budgetIntent.value = null;
    if (!props.offline) void loadBudget();
  },
  { flush: 'sync' },
);
watch(
  () => [props.api, props.groupId, props.actorUserId] as const,
  () => {
    ownerRevision++;
    budgetIntent.value = null;
    budgetBusy.value = false;
    budgetIncrease.value = '';
    loadRevision++;
    hasSnapshot.value = false;
    members.value = [];
    assistants.value = [];
    options.value = [];
    memberId.value = null;
    draft.value = [];
    baseline.value = [];
    baselineAssistantId.value = null;
    closeDialog();
    intent.value = null;
    busy.value = false;
    denied.value = false;
    error.value = null;
    notice.value = null;
    view.value = 'list';
    dialog.value = null;
    rosterOpen.value = false;
    void load();
  },
  { immediate: true, flush: 'sync' },
);
watch(
  () =>
    [
      draft.value,
      baseline.value,
      helper.value,
      member.value,
      props.actorUserId,
      props.actorHeadman,
      props.assistantPermissions,
      props.offline,
      props.readOnly,
    ] as const,
  () => {
    if (confirmation.value) closeDialog();
  },
  { deep: true, flush: 'sync' },
);
watch(
  () => props.offline,
  (value) => {
    loadRevision++;
    loading.value = false;
    if (!value) void load();
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
    invalidateDownload();
    void loadRosterFormats();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(() => {
  disposed = true;
  ownerRevision++;
  loadRevision++;
  rosterRevision++;
  invalidateDownload();
});
defineExpose({ back });
</script>
<template>
  <main
    class="headman-group"
    :data-group-view="view"
    :aria-busy="loading || busy"
  >
    <header class="headman-group__header">
      <button
        class="headman-group__back"
        type="button"
        aria-label="Назад"
        @click="clickBack"
      >
        <MobileIcon name="back" />
      </button><h1>{{ view === 'list' ? 'Группа' : view === 'detail' ? 'Студент' : 'Права помощника' }}</h1><button
        v-if="view === 'list' && isHeadman"
        class="headman-group__back"
        type="button"
        aria-label="Скачать состав группы"
        @click="rosterOpen = true"
      >
        <MobileIcon name="more" />
      </button>
    </header>
    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Изменения доступны онлайн. Черновик прав сохранён на этом экране."
    />
    <StudentWarningBlock
      v-if="readOnly || !isHeadman || denied"
      title="Только просмотр"
      message="Изменять права помощника может староста с действующим доступом к этой группе."
    />
    <StudentWarningBlock
      v-if="error"
      title="Не удалось подтвердить данные"
      :message="error"
      severity="error"
      action-label="Проверить состояние"
      :action-disabled="offline || loading || busy"
      @action="load"
    />
    <p
      v-if="notice"
      class="headman-group__notice"
      role="status"
    >
      {{ notice }}
    </p>
    <p
      v-if="loading"
      role="status"
    >
      Загружаем группу…
    </p>
    <StudentWarningBlock
      v-if="view === 'list' && credentials.error.value"
      title="Доступ не подтверждён"
      :message="credentials.error.value"
      severity="error"
      action-label="Проверить данные доступа"
      :action-disabled="credentials.loading.value || credentials.busy.value || offline"
      @action="credentials.refresh"
    />
    <template v-if="view === 'list' && hasSnapshot">
      <p class="headman-group__count">
        {{ members.length }} студентов
      </p>
      <label class="headman-group__search"><MobileIcon name="search" /><input
        v-model="query"
        type="search"
        aria-label="Поиск студента"
        placeholder="Поиск студента"
      ></label>
      <div class="headman-group__list">
        <button
          v-for="item in filteredMembers"
          :key="item.id"
          type="button"
          class="headman-group__member"
          :data-member-id="item.id"
          :data-role="roleLabel(item)"
          @click="openMember(item)"
        >
          <span><strong>{{ item.fullName }}</strong><small v-if="roleLabel(item) !== 'Студент'">{{ roleLabel(item) }}</small><small v-if="isHeadman && actorHeadman && !offline && !readOnly && !denied">{{ credentialLabel(item.id) }}</small></span><MobileIcon name="chevron-down" />
        </button>
      </div>
      <p v-if="!loading && !filteredMembers.length">
        {{ members.length ? 'Студенты не найдены.' : 'В группе пока нет студентов.' }}
      </p>
    </template>
    <template v-else-if="member">
      <section class="headman-group__identity">
        <h2>{{ member.fullName }}</h2><p>{{ roleLabel(member) }}</p>
      </section>
      <template v-if="view === 'detail'">
        <section
          v-if="isHeadman && actorHeadman && !offline && !readOnly && !denied && credentialEligible(member)"
          class="headman-group__summary"
          aria-labelledby="headman-credentials-title"
          :aria-busy="credentials.loading.value || credentials.busy.value"
        >
          <h2 id="headman-credentials-title">
            Доступ студента
          </h2>
          <p>{{ credentials.loading.value ? 'Загружаем данные доступа…' : credentialLabel(member.id) }}</p>
          <StudentWarningBlock
            v-if="credentials.error.value"
            title="Доступ не подтверждён"
            :message="credentials.error.value"
            severity="error"
            action-label="Проверить данные доступа"
            :action-disabled="credentials.busy.value"
            @action="credentials.refresh"
          />
          <button
            type="button"
            class="headman-group__configure"
            :disabled="!credentials.available.value || !credentials.values.value[member.id] || budgetBusy || Boolean(budgetIntent)"
            @click="confirmCredential('generate')"
          >
            Сбросить пароль
          </button>
          <button
            type="button"
            class="headman-group__configure"
            :disabled="!credentials.available.value || !credentials.values.value[member.id] || budgetBusy || Boolean(budgetIntent)"
            @click="confirmCredential('link')"
          >
            Выдать ссылку сброса
          </button>
          <template v-if="credentials.link.value">
            <label class="headman-group__field">Ссылка для студента<input
              :value="credentials.link.value.url"
              readonly
              type="text"
              autocomplete="off"
              spellcheck="false"
            ></label>
            <p>Ссылка действует до {{ new Date(credentials.link.value.expiresAt).toLocaleString('ru-RU') }}. После её использования студент задаст свой пароль.</p>
          </template>
        </section>
        <section
          v-if="isHeadman && actorHeadman"
          class="headman-group__summary"
          aria-labelledby="headman-budget-title"
        >
          <h2 id="headman-budget-title">
            Ручные запросы на отметку
          </h2>
          <p
            v-if="budgetLoading"
            role="status"
          >
            Загружаем лимит…
          </p>
          <p
            v-if="budget"
            role="status"
          >
            Осталось попыток: {{ budget.remaining }} из {{ budget.limit }}. Использовано:
            {{ budget.used }}.
          </p>
          <StudentWarningBlock
            v-if="budgetError"
            severity="error"
            title="Лимит не подтверждён"
            :message="budgetError"
            action-label="Обновить лимит"
            :action-disabled="offline || budgetLoading || budgetBusy"
            @action="loadBudget"
          />
          <p
            v-if="budgetNotice"
            role="status"
          >
            {{ budgetNotice }}
          </p>
          <label class="headman-group__field">Добавить попытки<input
            v-model="budgetIncrease"
            inputmode="numeric"
            pattern="[1-9][0-9]*"
            type="text"
            :disabled="!budgetWritable || Boolean(budgetIntent)"
            aria-describedby="budget-increase-hint"
          ></label>
          <p id="budget-increase-hint">
            Добавь положительное целое число. Использованные попытки сохранятся.
          </p>
          <button
            type="button"
            class="headman-group__primary"
            :disabled="!budgetWritable || (!budgetIntent && increaseValue === null)"
            @click="increaseBudget"
          >
            {{
              budgetBusy ? 'Сохраняем…' : budgetIntent ? 'Повторить увеличение' : 'Увеличить лимит'
            }}
          </button>
        </section>
        <section class="headman-group__summary">
          <h2>Права помощника</h2><p>
            {{
              helper ? `Разрешено прав: ${helper.permissions.length}` : 'Права помощника не назначены'
            }}
          </p>
        </section>
        <button
          v-if="!isMemberHeadman(member)"
          type="button"
          class="headman-group__configure"
          @click="openPermissions"
        >
          Настроить права<MobileIcon name="chevron-down" />
        </button>
      </template>
      <template v-else>
        <div class="headman-group__permissions">
          <div
            v-for="option in options"
            :key="option.code"
            class="headman-group__permission"
            :data-changed="draft.includes(option.code) !== baseline.includes(option.code)"
          >
            <span class="headman-group__permission-name">{{ option.label }}</span><span
              class="headman-group__permission-status"
              :data-enabled="draft.includes(option.code)"
            >{{ draft.includes(option.code) ? 'Разрешено' : 'Запрещено' }}</span><button
              type="button"
              role="switch"
              :aria-label="option.label"
              :aria-checked="draft.includes(option.code)"
              :disabled="!writable"
              class="headman-group__switch"
              @click="toggle(option.code)"
            >
              <span />
            </button>
          </div>
        </div>
        <footer class="headman-group__footer">
          <button
            type="button"
            class="headman-group__primary"
            data-action="confirm"
            :disabled="!writable || !changedCount || (!draft.length && !helper)"
            @click="save"
          >
            {{
              busy ? 'Сохраняем…' : `Сохранить изменения${changedCount ? `: ${changedCount}` : ''}`
            }}
          </button>
        </footer>
      </template>
    </template>
    <MobileToolsSheet
      :open="credentialConfirmation !== null"
      :title="credentialConfirmation?.action === 'generate' ? 'Сбросить пароль?' : 'Выдать ссылку сброса?'"
      icon-close
      @update:open="credentialConfirmation = null"
    >
      <p>{{ member?.fullName }}</p>
      <p>{{ credentialConfirmation?.action === 'generate' ? 'Будет создан новый пароль. Текущие сеансы студента завершатся. Пароль останется виден здесь до смены студентом.' : 'Предыдущая ссылка перестанет действовать. Текущий пароль и сеансы сохранятся до использования новой ссылки.' }}</p>
      <div class="headman-group__dialog-actions">
        <button
          type="button"
          @click="credentialConfirmation = null"
        >
          Отмена
        </button>
        <button
          type="button"
          data-action="confirm"
          @click="commitCredential"
        >
          {{ credentialConfirmation?.action === 'generate' ? 'Сбросить пароль' : 'Выдать ссылку' }}
        </button>
      </div>
    </MobileToolsSheet>
    <MobileToolsSheet
      v-if="isHeadman"
      ref="sheet"
      v-model:open="rosterOpen"
      title="Состав группы"
    >
      <label class="headman-group__field">Формат файла<select
        v-model="rosterFormat"
        :disabled="rosterLoading || rosterDownloading"
      ><option
        v-for="format in rosterFormats"
        :key="format.code"
        :value="format.code"
      >{{ format.label }}</option></select><MobileIcon name="chevron-down" /></label><StudentWarningBlock
        v-if="rosterError"
        title="Скачивание недоступно"
        :message="rosterError"
        severity="error"
      /><p
        v-if="rosterNotice"
        role="status"
      >
        {{ rosterNotice }}
      </p><button
        type="button"
        class="headman-group__primary"
        :disabled="offline || rosterLoading || rosterDownloading || !activeRosterFormat"
        @click="downloadRoster"
      >
        {{ rosterDownloading ? 'Скачиваем…' : 'Скачать' }}
      </button>
    </MobileToolsSheet>
    <MobileToolsSheet
      :key="confirmation?.step.kind ?? 'leave'"
      :open="dialog !== null"
      :title="
        dialog === 'confirm'
          ? confirmation?.step.kind === 'grant'
            ? 'Применить права?'
            : 'Отозвать права?'
          : 'Выйти без сохранения?'
      "
      icon-close
      @update:open="closeDialog"
    >
      <template v-if="dialog === 'confirm' && confirmation">
        <p>{{ confirmation.snapshot.memberName }}</p>
        <ul class="headman-group__permission-diff">
          <li
            v-for="change in confirmation.step.rights"
            :key="change.code"
          >
            <strong>{{ change.label }}</strong><span>{{
              confirmation.step.kind === 'grant' ? 'Запрещено → Разрешено' : 'Разрешено → Запрещено'
            }}</span>
          </li>
        </ul>
        <p v-if="confirmation.steps.length > 1 && confirmation.stepIndex === 0">
          После подтверждения отзыва проверь выдачу прав. Все изменения сохранятся вместе после
          второго подтверждения.
        </p><p v-else>
          Подтверди выбранные {{ confirmation.step.kind === 'grant' ? 'права' : 'изменения' }}.
        </p>
      </template>
      <p v-else>
        Несохранённые изменения прав будут потеряны.
      </p>
      <div class="headman-group__dialog-actions">
        <button
          type="button"
          data-action="cancel"
          @click="closeDialog"
        >
          Отмена
        </button>
        <button
          v-if="dialog === 'confirm'"
          type="button"
          :data-action="confirmation?.step.kind === 'grant' ? 'confirm' : 'delete'"
          :disabled="!writable"
          @click="commit"
        >
          {{
            confirmation?.step.kind === 'grant'
              ? 'Применить права'
              : confirmation?.steps.length === 1
                ? 'Отозвать права'
                : 'Подтвердить отзыв'
          }}
        </button>
        <button
          v-else
          type="button"
          data-action="confirm"
          @click="
            closeDialog();
            goBack();
          "
        >
          Выйти
        </button>
      </div>
    </MobileToolsSheet>
  </main>
</template>
