<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import archiveIcon from '../../assets/headman-homework-archive.svg';
import AdminNotice from '../../shared/components/AdminNotice.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import RequestAttachmentPreview from '../requests/RequestAttachmentPreview.vue';
import { requestStatusLabel, requestStatusTone } from '../requests/state';
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client';
import {
  HeadmanRequestsApiError,
  type HeadmanRequestBucket,
  type HeadmanRequestDetail,
  type HeadmanRequestKind,
  type HeadmanRequestLesson,
  type HeadmanRequestPage,
  type HeadmanRequestSummary,
  type HeadmanRequestFilters,
  type HeadmanRequestsApi,
} from './headman-requests-client';
import {
  freezeRequestDecision,
  sendRequestDecision,
  updateRequestExclusions,
  type FrozenRequestDecision,
  type RequestDecision,
} from './request-decision';
import { formatRequestLessonTime } from './request-lesson-time';
import {
  currentLessonRows,
  groupHeadmanRequests,
  hasDifferentOriginalLessons,
  originalLessonRows,
  previewApproval,
  requestDate,
  requestPeriod,
  requestReason,
  storedDecisionRows,
  type RequestLessonRow,
} from './request-presentation';
import RequestLessonList from './RequestLessonList.vue';
import './headman-requests-screen.pcss';

const props = withDefaults(
  defineProps<{
    api: HeadmanRequestsApi | null;
    initialKind?: HeadmanRequestKind | null;
    assistantPermissions?: readonly HeadmanAssistantPermission[] | null;
    offline?: boolean;
    readOnly?: boolean;
    showBack?: boolean;
  }>(),
  {
    initialKind: null,
    assistantPermissions: null,
    offline: false,
    readOnly: false,
    showBack: true,
  },
);
export type HeadmanRequestsView = 'queue' | 'archive' | 'detail' | 'approve' | 'reject';
const emit = defineEmits<{
  back: [];
  error: [cause: unknown];
  'view-change': [view: HeadmanRequestsView];
}>();
const view = ref<HeadmanRequestsView>('queue'),
  bucket = ref<HeadmanRequestBucket>('OPEN'),
  kind = ref<HeadmanRequestKind | ''>(props.initialKind ?? '');
let openKind = kind.value;
const heading = ref<HTMLElement | null>(null);
const studentName = ref(''),
  dateFrom = ref(''),
  dateTo = ref(''),
  filterOpen = ref(false),
  currentPage = ref(0);
const appliedQuery = shallowRef<HeadmanRequestFilters | null>(null);
const page = ref<HeadmanRequestPage | null>(null),
  selected = ref<HeadmanRequestDetail | null>(null),
  selectedRequest = shallowRef<HeadmanRequestSummary | null>(null);
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref<{ tone: 'success' | 'warning'; message: string } | null>(null),
  reason = ref(''),
  counts = ref<Partial<Record<'all' | HeadmanRequestKind, number>>>({});
const pending = shallowRef<FrozenRequestDecision | null>(null);
const excludedIds = ref<readonly string[]>([]);
let revision = 0,
  disposed = false;
const allowed = computed(
  () =>
    props.assistantPermissions === null || props.assistantPermissions.includes('MANAGE_EXCUSES'),
);
const writable = computed(
  () =>
    !!props.api &&
    allowed.value &&
    !props.offline &&
    !props.readOnly &&
    !busy.value &&
    !loading.value &&
    !pending.value,
);
const root = computed(() => view.value === 'queue' || view.value === 'archive');
// Historical snapshots are evidence. Confirmation uses only the additive current projection.
const affected = computed(() =>
  (selected.value?.currentAffectedLessons ?? []).filter(
    (lesson) => !excludedIds.value.includes(lesson.occurrenceId ?? ''),
  ),
);
const pairsPlural = new Intl.PluralRules('ru-RU');
const approvalLabel = computed(() => {
  const count = affected.value.length;
  const form = pairsPlural.select(count);
  return `Одобрить ${count} ${form === 'one' ? 'пару' : form === 'few' ? 'пары' : 'пар'}`;
});
const selectable = computed(
  () =>
    !!selected.value?.decisionRevision &&
    selected.value.summary.kind === 'EXCUSE' &&
    !!selected.value.currentAffectedLessons?.length &&
    selected.value.currentAffectedLessons.every(
      (lesson) => !!lesson.occurrenceId && !!lesson.occurrenceRevision,
    ),
);
const groups = computed(() => groupHeadmanRequests(page.value?.content ?? []));
const originalRows = computed(() => originalLessonRows(selected.value?.lessons ?? []));
const currentRows = computed(() =>
  selected.value?.currentAffectedLessons
    ? currentLessonRows(selected.value.currentAffectedLessons, excludedIds.value)
    : originalRows.value,
);
const preview = computed(() =>
  selected.value && view.value === 'approve' && selected.value.summary.status === 'PENDING'
    ? previewApproval(selected.value, excludedIds.value)
    : null,
);
const stored = computed(() => {
  const detail = selected.value;
  if (!detail?.decisionSelection || detail.summary.status === 'PENDING') return null;
  const { before, after } = storedDecisionRows(detail);
  return { before: detail.decisionSelection.beforeLessons ? before : [], after };
});
const comparison = computed(() => preview.value ?? stored.value);
const originalDistinct = computed(() => {
  const detail = selected.value;
  if (!detail) return false;
  const archived = detail.summary.status !== 'PENDING';
  const compared = archived
    ? detail.decisionSelection?.beforeLessons
    : detail.currentAffectedLessons;
  if (archived && (!detail.lessons.length || !compared?.length)) return false;
  return !!compared && hasDifferentOriginalLessons(detail.lessons, compared);
});
const archiveCurrentRows = computed(() => {
  const detail = selected.value;
  if (
    !detail?.currentAffectedLessons ||
    detail.summary.status === 'PENDING' ||
    stored.value?.before.length ||
    stored.value?.after.length
  ) {
    return [];
  }
  return currentLessonRows(detail.currentAffectedLessons);
});
const title = computed(
  () =>
    ({
      queue: 'Заявки',
      archive: 'Архив заявок',
      detail: 'Заявка',
      approve: 'Одобрить заявку',
      reject: 'Отклонить заявку',
    })[view.value],
);
const ownerKey = computed(() => `${revision}:${selected.value?.summary.id ?? ''}`);
function setView(next: HeadmanRequestsView): void {
  view.value = next;
  emit('view-change', next);
}
function current(run: number, api: HeadmanRequestsApi | null): boolean {
  return !disposed && run === revision && api === props.api && allowed.value && !props.offline;
}
function failure(cause: unknown, fallback: string): string {
  if (cause instanceof HeadmanRequestsApiError) {
    if (cause.response.status === 401) return 'Сессия истекла. Войди снова.';
    if (cause.response.status === 403) return 'Нет права управлять заявками этой группы.';
    if (cause.response.status === 409) return 'Заявка изменилась. Проверь актуальные данные.';
  }
  return cause instanceof Error ? cause.message : fallback;
}
function filters() {
  return {
    bucket: bucket.value,
    studentName: studentName.value,
    coverageDateFrom: dateFrom.value,
    coverageDateTo: dateTo.value,
  };
}
async function load(append = false): Promise<void> {
  if (loading.value && append) return;
  const run = ++revision,
    api = props.api;
  const query =
    append && appliedQuery.value
      ? appliedQuery.value
      : Object.freeze({ ...filters(), type: kind.value || null });
  if (!append) appliedQuery.value = query;
  if (!append) {
    page.value = null;
    counts.value = {};
    currentPage.value = 0;
  }
  error.value = '';
  loading.value = false;
  if (!api || !allowed.value || props.offline) return;
  if (
    query.coverageDateFrom &&
    query.coverageDateTo &&
    query.coverageDateFrom > query.coverageDateTo
  ) {
    error.value = 'Начало периода должно быть раньше его конца.';
    return;
  }
  loading.value = true;
  try {
    const data = await api.list({ ...query, page: append ? currentPage.value + 1 : 0, size: 20 });
    if (!current(run, api)) return;
    if (append && page.value && data.totalElements !== page.value.totalElements)
      throw new Error('Очередь изменилась. Обнови список, чтобы продолжить загрузку.');
    const existing = append ? (page.value?.content ?? []) : [];
    page.value = {
      ...data,
      content: [...new Map([...existing, ...data.content].map((item) => [item.id, item])).values()],
    };
    currentPage.value = data.page;
    if (!append && query.bucket === 'OPEN')
      void loadOpenCounts(run, api, query, data.totalElements);
  } catch (cause) {
    if (current(run, api)) {
      error.value = failure(cause, 'Не удалось загрузить заявки.');
      emit('error', cause);
    }
  } finally {
    if (run === revision) loading.value = false;
  }
}
async function loadOpenCounts(
  run: number,
  api: HeadmanRequestsApi,
  query: HeadmanRequestFilters,
  total: number,
): Promise<void> {
  const result = await Promise.allSettled(
    (
      [
        ['all', null],
        ['EXCUSE', 'EXCUSE'],
        ['LATE_CHECKIN', 'LATE_CHECKIN'],
      ] as const
    ).map(async ([key, type]) => ({
      key,
      count:
        query.type === type
          ? total
          : (await api.list({ ...query, type, size: 1, page: 0 })).totalElements,
    })),
  );
  if (!current(run, api)) return;
  counts.value = Object.fromEntries(
    result.flatMap((entry) =>
      entry.status === 'fulfilled' ? [[entry.value.key, entry.value.count]] : [],
    ),
  );
}
function chooseKind(next: HeadmanRequestKind | ''): void {
  if (busy.value || pending.value || kind.value === next) return;
  kind.value = next;
  if (bucket.value === 'OPEN') openKind = next;
  void load();
}
function switchBucket(): void {
  if (busy.value || pending.value) return;
  const archive = bucket.value === 'OPEN';
  bucket.value = archive ? 'ARCHIVE' : 'OPEN';
  kind.value = archive ? '' : openKind;
  filterOpen.value = false;
  setView(archive ? 'archive' : 'queue');
  void load();
}
async function open(item: HeadmanRequestSummary): Promise<void> {
  if (!props.api || busy.value || pending.value || props.offline || !allowed.value) return;
  const api = props.api,
    run = ++revision;
  selectedRequest.value = item;
  selected.value = null;
  excludedIds.value = [];
  reason.value = '';
  error.value = '';
  notice.value = null;
  setView('detail');
  loading.value = true;
  try {
    const detail = await api.get(item.id);
    if (!current(run, api)) return;
    if (detail.summary.id !== item.id) throw new Error('Сервер вернул другую заявку.');
    selected.value = detail;
  } catch (cause) {
    if (current(run, api)) {
      error.value = failure(cause, 'Не удалось открыть заявку.');
      emit('error', cause);
    }
  } finally {
    if (run === revision) loading.value = false;
  }
}
function changeExclusion(lesson: HeadmanRequestLesson, exclude: boolean): void {
  if (!writable.value || !selectable.value || !selected.value || !lesson.occurrenceId) return;
  try {
    excludedIds.value = updateRequestExclusions(
      selected.value,
      excludedIds.value,
      lesson.occurrenceId,
      exclude,
    );
  } catch (cause) {
    error.value = failure(cause, 'Не удалось изменить выбор пар.');
  }
}
async function confirm(decision: RequestDecision): Promise<void> {
  const previous = selected.value,
    api = props.api;
  if (!previous || !api || !writable.value) return;
  const run = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const fresh = await api.get(previous.summary.id);
    if (!current(run, api) || props.readOnly) return;
    if (fresh.summary.id !== previous.summary.id) throw new Error('Сервер вернул другую заявку.');
    selected.value = fresh;
    if (fresh.summary.status !== 'PENDING') {
      excludedIds.value = [];
      notice.value = {
        tone: 'warning',
        message: 'Заявка уже обработана. Показано актуальное решение.',
      };
      setView('detail');
      return;
    }
    if (excludedIds.value.length && fresh.decisionRevision !== previous.decisionRevision) {
      excludedIds.value = [];
      error.value = 'Состав заявки изменился. Проверь текущие пары и выбери исключения заново.';
      return;
    }
    if (
      decision === 'APPROVED' &&
      (!fresh.currentAffectedLessons?.length ||
        (fresh.summary.kind === 'EXCUSE' && !selectable.value))
    )
      throw new Error('Сервер не подтвердил текущий состав и версию решения. Обнови заявку.');
    setView(decision === 'APPROVED' ? 'approve' : 'reject');
  } catch (cause) {
    if (current(run, api)) {
      error.value = failure(cause, 'Не удалось проверить заявку.');
      emit('error', cause);
    }
  } finally {
    if (run === revision) loading.value = false;
  }
}
async function decide(replay = false): Promise<void> {
  const api = props.api,
    detail = selected.value;
  if (!api || !detail || props.offline || props.readOnly || !allowed.value || busy.value) return;
  if (pending.value && pending.value.actor !== api) return;
  if (!pending.value) {
    if (view.value === 'approve' && !detail.currentAffectedLessons) {
      error.value = 'Не удалось проверить текущие пары. Обнови заявку перед решением.';
      return;
    }
    try {
      pending.value = freezeRequestDecision(
        api,
        detail,
        view.value === 'approve' ? 'APPROVED' : 'REJECTED',
        reason.value,
        view.value === 'approve' ? excludedIds.value : [],
      );
    } catch (cause) {
      error.value = failure(cause, 'Проверь причину решения.');
      return;
    }
  }
  const frozen = pending.value,
    run = revision;
  busy.value = true;
  error.value = '';
  const outcome = await sendRequestDecision(
    frozen,
    replay,
    () => current(run, api) && !props.readOnly && pending.value === frozen,
  );
  if (!current(run, api) || props.readOnly) {
    busy.value = false;
    return;
  }
  busy.value = false;
  if (outcome.status === 'unknown') {
    error.value =
      'Результат решения неизвестен. Проверь результат: сначала обновим заявку, затем при необходимости повторим то же решение.';
    emit('error', outcome.cause);
    return;
  }
  if (outcome.status === 'blocked') {
    error.value = 'Решение не отправлено: проверь доступ и повтори то же решение.';
    return;
  }
  pending.value = null;
  if (outcome.status === 'rejected') {
    error.value = failure(outcome.cause, 'Решение не принято.');
    emit('error', outcome.cause);
    return;
  }
  selected.value = outcome.detail;
  excludedIds.value = [];
  setView('detail');
  notice.value =
    outcome.status === 'done'
      ? { tone: 'success', message: 'Решение сохранено. Заявка находится в архиве.' }
      : {
          tone: 'warning',
          message: 'Это решение не подтверждено. Показан результат, который сохранил сервер.',
        };
}
function dismissNotice(): void {
  notice.value = null;
  void nextTick(() => heading.value?.focus({ preventScroll: true }));
}
function back(): boolean {
  if (busy.value || pending.value) return true;
  if (filterOpen.value) {
    filterOpen.value = false;
    return true;
  }
  if (view.value === 'approve' || view.value === 'reject') {
    error.value = '';
    setView('detail');
    return true;
  }
  if (view.value === 'detail') {
    selected.value = null;
    selectedRequest.value = null;
    excludedIds.value = [];
    notice.value = null;
    reason.value = '';
    error.value = '';
    setView(bucket.value === 'ARCHIVE' ? 'archive' : 'queue');
    void load();
    return true;
  }
  if (view.value === 'archive') {
    switchBucket();
    return true;
  }
  return false;
}
function handleBack(): void {
  if (!back()) emit('back');
}
function apply(): void {
  filterOpen.value = false;
  currentPage.value = 0;
  void load();
}
function loadMore(): void {
  if (!loading.value && !busy.value && currentPage.value + 1 < (page.value?.totalPages ?? 0))
    void load(true);
}
function exclusionLabel({ lesson, excluded }: RequestLessonRow): string {
  return `${excluded ? 'Вернуть' : 'Исключить'} пару: ${lesson.subjectName ?? 'Предмет'}, ${requestDate(lesson.date)}, ${formatRequestLessonTime(lesson)}`;
}
watch(
  () => props.api,
  () => {
    revision += 1;
    busy.value = false;
    selected.value = null;
    selectedRequest.value = null;
    pending.value = null;
    excludedIds.value = [];
    notice.value = null;
    reason.value = '';
    error.value = '';
    setView(bucket.value === 'ARCHIVE' ? 'archive' : 'queue');
    void load();
  },
  { immediate: true, flush: 'sync' },
);
watch(
  () => [props.offline, props.assistantPermissions?.join(','), props.readOnly] as const,
  () => {
    revision += 1;
    busy.value = false;
    loading.value = false;
    if (pending.value)
      error.value =
        'Решение отправлено, но результат ещё не подтверждён. После восстановления доступа проверь и повтори то же решение.';
    if (root.value) void load();
    // Same actor's uncertain write stays frozen through connectivity/permission changes.
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  disposed = true;
  revision += 1;
});
function openNotificationRequest(detail: HeadmanRequestDetail): boolean {
  if (!props.api || props.offline || !allowed.value || busy.value || pending.value || disposed)
    return false;
  revision += 1;
  selectedRequest.value = detail.summary;
  selected.value = detail;
  excludedIds.value = [];
  reason.value = '';
  error.value = '';
  notice.value = null;
  loading.value = false;
  setView('detail');
  return true;
}
defineExpose({ back, openNotificationRequest });
</script>

<template>
  <main
    class="headman-requests"
    :data-request-view="view"
    :aria-busy="loading || busy"
    aria-labelledby="headman-requests-title"
  >
    <header class="headman-requests__header">
      <button
        v-if="!root || showBack || view === 'archive'"
        class="headman-requests__back"
        type="button"
        aria-label="Назад"
        @click="handleBack"
      >
        <MobileIcon name="back" />
      </button>
      <h1
        id="headman-requests-title"
        ref="heading"
        tabindex="-1"
      >
        {{ title }}
      </h1>
      <button
        v-if="root"
        type="button"
        class="headman-requests__filter"
        aria-label="Поиск и период"
        :aria-expanded="filterOpen"
        @click="filterOpen = !filterOpen"
      >
        <MobileIcon name="search" />
      </button>
    </header>
    <div
      v-if="root"
      class="headman-requests__tabs"
      role="group"
      aria-label="Фильтры заявок"
    >
      <button
        type="button"
        :aria-pressed="!kind"
        @click="chooseKind('')"
      >
        Все <span v-if="bucket === 'OPEN' && (counts.all ?? 0) > 0">{{ counts.all }}</span>
      </button>
      <button
        type="button"
        :aria-pressed="kind === 'EXCUSE'"
        @click="chooseKind('EXCUSE')"
      >
        <span
          class="headman-requests__symbol"
          data-status="EXCUSED"
          aria-hidden="true"
        >у</span>
        <span class="headman-requests__sr">Уважительные причины</span>
        <span v-if="bucket === 'OPEN' && (counts.EXCUSE ?? 0) > 0">{{ counts.EXCUSE }}</span>
      </button>
      <button
        type="button"
        :aria-pressed="kind === 'LATE_CHECKIN'"
        @click="chooseKind('LATE_CHECKIN')"
      >
        <span
          class="headman-requests__symbol"
          data-status="ABSENT"
          aria-hidden="true"
        >н</span>
        <span class="headman-requests__sr">Забыл отметиться</span>
        <span v-if="bucket === 'OPEN' && (counts.LATE_CHECKIN ?? 0) > 0">{{
          counts.LATE_CHECKIN
        }}</span>
      </button>
      <button
        type="button"
        :aria-pressed="bucket === 'ARCHIVE'"
        @click="switchBucket"
      >
        <SvgIcon
          :src="archiveIcon"
          aria-hidden="true"
        />
        Архив
      </button>
    </div>
    <form
      v-if="root && filterOpen"
      class="headman-requests__filters"
      @submit.prevent="apply"
    >
      <label>ФИО<input
        v-model="studentName"
        type="search"
        placeholder="Начни вводить фамилию"
      ></label>
      <label>Период от<input
        v-model="dateFrom"
        type="date"
      ></label><label>Период до<input
        v-model="dateTo"
        type="date"
      ></label>
      <button
        class="headman-requests__primary"
        type="submit"
      >
        Применить
      </button>
    </form>
    <StudentWarningBlock
      v-if="!allowed"
      severity="error"
      title="Нет доступа"
      message="Для заявок нужно право управления уважительными причинами."
    />
    <StudentWarningBlock
      v-else-if="offline"
      title="Ты офлайн"
      message="Подключись к интернету, чтобы открыть заявки и принять решение."
    />
    <StudentWarningBlock
      v-else-if="!api"
      severity="error"
      title="Заявки недоступны"
      message="Источник данных не подключён."
    />
    <StudentWarningBlock
      v-if="error"
      :severity="pending ? 'warning' : 'error'"
      :title="pending ? 'Проверь результат' : 'Не удалось выполнить действие'"
      :message="error"
      :action-label="
        pending
          ? 'Проверить и повторить'
          : root
            ? 'Повторить'
            : selectedRequest
              ? 'Обновить заявку'
              : ''
      "
      :action-disabled="busy || offline || readOnly || !allowed || !api"
      @action="
        pending ? decide(true) : root ? load() : selectedRequest ? open(selectedRequest) : undefined
      "
    />
    <AdminNotice
      v-if="notice && selected && allowed && !offline"
      :tone="notice.tone"
      :title="notice.tone === 'success' ? 'Заявка обработана' : 'Проверь результат'"
      dismiss-label="Закрыть уведомление"
      @dismiss="dismissNotice"
    >
      <p>{{ notice.message }}</p>
    </AdminNotice>
    <div
      v-if="loading"
      :class="root ? 'headman-requests__loading' : 'headman-requests__state'"
      role="status"
    >
      <span
        v-if="root && !page"
        class="headman-requests__spinner"
        aria-hidden="true"
      />
      <span :class="{ 'headman-requests__sr': root }">
        {{ root ? 'Загружаем заявки…' : 'Загружаем заявку…' }}
      </span>
    </div>
    <template v-if="root && page && allowed && !offline">
      <p
        v-if="!page.content.length"
        class="headman-requests__state"
        role="status"
      >
        {{
          bucket === 'ARCHIVE'
            ? 'В архиве нет заявок по заданным условиям.'
            : 'Заявок по заданным условиям нет.'
        }}
      </p>
      <section
        v-for="group in groups"
        :key="group.key"
        class="headman-requests__group"
        :aria-labelledby="`headman-request-group-${group.key}`"
      >
        <h2 :id="`headman-request-group-${group.key}`">
          {{ group.name ?? 'Студент' }}
        </h2>
        <button
          v-for="item in group.items"
          :key="item.id"
          type="button"
          class="headman-requests__ticket"
          :data-request-id="item.id"
          @click="open(item)"
        >
          <span
            class="headman-requests__kind"
            :data-kind="item.kind"
            aria-hidden="true"
          />
          <span class="headman-requests__ticket-copy">
            <span class="headman-requests__sr">{{
              item.kind === 'EXCUSE' ? 'Уважительная причина' : 'Забыл отметиться'
            }}</span>
            <strong>{{ requestReason(item) }}</strong>
            <span>{{ requestPeriod(item) }}</span>
          </span>
          <span
            v-if="bucket === 'ARCHIVE'"
            class="headman-requests__badge"
            :data-tone="requestStatusTone(item.status)"
          >
            {{ requestStatusLabel(item.status) }}
          </span>
          <MobileIcon
            name="back"
            class="headman-requests__next"
          />
        </button>
      </section>
      <div
        v-if="currentPage + 1 < page.totalPages"
        class="headman-requests__pagination"
      >
        <button
          type="button"
          :disabled="loading || busy"
          @click="loadMore"
        >
          Показать ещё
        </button>
      </div>
    </template>
    <template v-else-if="selected && allowed && !offline">
      <section
        class="headman-requests__student"
        :data-status="selected.summary.status"
      >
        <strong>{{ selected.summary.studentName ?? 'Студент' }}</strong>
        <p>{{ requestReason(selected.summary) }}</p>
        <p>Исходный период: {{ requestPeriod(selected.summary) }}</p>
        <p
          v-if="selected.summary.kind === 'EXCUSE' && selected.summary.status === 'PENDING'"
          role="status"
        >
          {{ selected.summary.alreadyMarkedCount }} из {{ selected.summary.lessonCount }} пар уже
          отмечены. Подтверждённое присутствие сохранится.
        </p><span
          v-if="selected.summary.status !== 'PENDING'"
          class="headman-requests__badge"
          :data-tone="requestStatusTone(selected.summary.status)"
        >{{ requestStatusLabel(selected.summary.status) }}</span>
      </section>
      <template v-if="view === 'detail'">
        <h2 class="headman-requests__section-title">
          Комментарий студента
        </h2><p class="headman-requests__comment">
          {{ selected.summary.comment || 'Комментарий не добавлен.' }}
        </p>
        <RequestAttachmentPreview
          v-for="attachment in selected.attachments"
          :key="attachment.id"
          :api="api"
          :request-id="selected.summary.id"
          :attachment="attachment"
          :owner-key="ownerKey"
          :allowed="allowed"
          :offline="offline"
          preview-only
          tap-to-open
        />
        <p
          v-if="!selected.attachments.length"
          class="headman-requests__hint"
        >
          Вложений нет.
        </p>
        <p
          v-if="selected.summary.decisionComment"
          class="headman-requests__comment"
        >
          Причина решения: {{ selected.summary.decisionComment }}
        </p>
      </template>
      <template v-if="view === 'detail' && selected.summary.status === 'PENDING'">
        <h2 class="headman-requests__section-title">
          {{ selected.currentAffectedLessons ? 'Текущие пары заявки' : 'Исходные пары заявки' }}
        </h2>
        <StudentWarningBlock
          v-if="!selected.currentAffectedLessons"
          title="Текущие пары недоступны"
          message="Сохранённые данные заявки показаны ниже. Одобрение доступно после проверки текущих пар."
        />
        <RequestLessonList :rows="currentRows">
          <template #control="{ row }">
            <button
              v-if="selectable"
              type="button"
              class="headman-requests__exclude"
              :disabled="!writable || (!row.excluded && affected.length <= 1)"
              :aria-label="exclusionLabel(row)"
              @click="changeExclusion(row.lesson, !row.excluded)"
            >
              <MobileIcon
                v-if="!row.excluded"
                name="trash"
              />
              <svg
                v-else
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                stroke="currentColor"
                stroke-width="1.25"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M3 8l3 3 7-7" />
              </svg>
            </button>
          </template>
        </RequestLessonList>
      </template>
      <div
        v-if="comparison && (preview || comparison.before.length || comparison.after.length)"
        class="headman-requests__comparison"
      >
        <section
          v-if="preview || comparison.before.length"
          class="headman-requests__comparison-panel"
          data-comparison="before"
        >
          <h2>До</h2>
          <p class="headman-requests__hint">
            {{ preview ? 'Текущие отметки' : 'Отметки перед решением' }}
          </p>
          <RequestLessonList :rows="comparison.before" />
        </section>
        <section
          v-if="preview || comparison.after.length"
          class="headman-requests__comparison-panel"
          data-comparison="after"
        >
          <h2>После</h2>
          <p class="headman-requests__hint">
            {{
              preview
                ? 'Прогноз после одобрения. Подтверждённое присутствие сохранится.'
                : 'Отметки на момент решения'
            }}
          </p>
          <RequestLessonList :rows="comparison.after" />
        </section>
      </div>
      <section
        v-if="archiveCurrentRows.length"
        class="headman-requests__original"
      >
        <h2 class="headman-requests__section-title">
          Текущие отметки в журнале
        </h2>
        <RequestLessonList :rows="archiveCurrentRows" />
      </section>
      <section
        v-if="view !== 'reject' && originalDistinct"
        class="headman-requests__original"
      >
        <h2 class="headman-requests__section-title">
          Исходные пары заявки
        </h2>
        <RequestLessonList :rows="originalRows" />
      </section>
      <template v-if="view === 'reject'">
        <StudentWarningBlock
          title="Заявка будет отклонена"
          message="Отметки посещаемости не изменятся. Студент увидит твою причину отказа."
        /><label class="headman-requests__reason">Причина отклонения<textarea
          v-model="reason"
          maxlength="300"
          rows="5"
          placeholder="Напиши, почему отклоняешь заявку"
          :disabled="!!pending || busy"
        /><span>{{ reason.length }} / 300</span></label>
      </template>
      <div
        v-if="selected.summary.status === 'PENDING'"
        class="headman-requests__actions"
      >
        <template v-if="view === 'detail'">
          <button
            type="button"
            class="headman-requests__primary"
            :disabled="!writable"
            @click="confirm('APPROVED')"
          >
            Одобрить
          </button><button
            type="button"
            class="headman-requests__danger"
            :disabled="!writable"
            @click="confirm('REJECTED')"
          >
            Отклонить
          </button>
        </template>
        <button
          v-else-if="view === 'approve'"
          type="button"
          class="headman-requests__primary"
          :disabled="
            !writable ||
              !affected.length ||
              !selected.currentAffectedLessons ||
              (selected.summary.kind === 'EXCUSE' && !selectable)
          "
          @click="decide()"
        >
          {{ busy ? 'Сохраняем…' : approvalLabel }}
        </button>
        <button
          v-else
          type="button"
          class="headman-requests__danger"
          :disabled="!writable || !reason.trim()"
          @click="decide()"
        >
          {{ busy ? 'Сохраняем…' : 'Отклонить заявку' }}
        </button>
      </div>
      <p
        v-if="readOnly"
        class="headman-requests__hint"
      >
        Режим просмотра. Решения недоступны.
      </p>
    </template>
  </main>
</template>
