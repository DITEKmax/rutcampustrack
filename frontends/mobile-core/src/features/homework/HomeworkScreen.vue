<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import type { StudentHomework, StudentHomeworkItem } from '../../api/types';
import {
  groupHomework,
  groupHomeworkPeriod,
  dedupeHomeworkItems,
  addHomeworkDays,
  shiftHomeworkPeriod,
  formatHomeworkMonth,
  validateHomeworkLink,
  type HomeworkDateRange,
  type HomeworkPeriodMode,
  type HomeworkGroup,
  type HomeworkLinkValidation,
} from '../../domain/homework';
import MobileShell from '../../shared/components/MobileShell.vue';
import {
  rootRoute,
  type MobileBottomNavItems,
  type MobileNavigationStack,
  type MobileRootRouteId,
  type MobileRoute,
} from '../../shared/navigation';
import type { MobileHostAdapter } from '../../shared/host';
import externalLinkIcon from '../../assets/homework-external-link.svg';
import expandChevronIcon from '../../assets/homework-expand-chevron.svg';
import HomeworkCompletion from './HomeworkCompletion.vue';
import { homeworkTextSegments } from './headman-homework-state';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import returnTodayChevronIcon from '../../assets/homework-return-today-chevron.svg';
import {
  canRestoreHomeworkFocus,
  homeworkFeedIdentity,
  type HomeworkFocusRequest,
} from './homework-focus';
import './homework-screen.pcss';

const props = withDefaults(
  defineProps<{
    homework: StudentHomework | null;
    loading: boolean;
    error: string | null;
    navItems: MobileBottomNavItems;
    offline?: boolean;
    readOnly?: boolean;
    updatedAt?: string | null;
    route?: MobileRoute | null;
    navigation?: MobileNavigationStack | null;
    activeId?: MobileRootRouteId;
    keyboardVisible?: boolean;
    host?: MobileHostAdapter | null;
    /** Stable adapter scope identity; stale requests never restore focus. */
    ownerKey?: string | null;
    historical?: boolean;
    canLoadPrevious?: boolean;
    pendingIds?: ReadonlySet<string> | readonly string[];
    itemErrors?: Readonly<Record<string, string | null>>;
    isItemPending?: (id: string) => boolean;
    itemError?: (id: string) => string | null;
    focusItemId?: string | null;
    focusRequestId?: number | null;
    unavailableMessage?: string | null;
    periodMode?: HomeworkPeriodMode;
    selectedDate?: string | null;
    periodRange?: HomeworkDateRange | null;
    semesterRange?: HomeworkDateRange | null;
    todayDate?: string | null;
  }>(),
  {
    offline: false,
    readOnly: false,
    updatedAt: null,
    route: null,
    navigation: null,
    activeId: 'homework',
    keyboardVisible: undefined as never,
    host: null,
    ownerKey: null,
    historical: false,
    canLoadPrevious: false,
    pendingIds: undefined as never,
    itemErrors: undefined as never,
    isItemPending: undefined as never,
    itemError: undefined as never,
    focusItemId: null,
    focusRequestId: null,
    unavailableMessage: null,
    periodMode: 'day',
    selectedDate: null,
    periodRange: null,
    semesterRange: null,
    todayDate: null,
  },
);

const emit = defineEmits<{
  complete: [item: StudentHomeworkItem, completed: boolean];
  retry: [item: StudentHomeworkItem];
  retryFeed: [];
  openMaterial: [originalUrl: string, item: StudentHomeworkItem];
  previous: [];
  returnToday: [];
  selectPeriod: [mode: HomeworkPeriodMode];
  selectDate: [date: string];
  stepPeriod: [step: number];
  navigate: [route: MobileRootRouteId];
  back: [route: MobileRoute | null];
}>();

const homeworkRoute = rootRoute('homework');
const shellOptionalProps = computed(() =>
  props.keyboardVisible === undefined ? {} : { keyboardVisible: props.keyboardVisible },
);
const expandedIds = ref<Set<string>>(new Set());
const trackElements = new Map<string, HTMLElement>();
const cardHeadingElements = new Map<string, HTMLElement>();
const focusRequest = ref<HomeworkFocusRequest | null>(null);

const groups = computed<HomeworkGroup[]>(() => {
  if (!props.homework) return [];
  if (!props.periodRange || !props.selectedDate) return groupHomework(props.homework);
  const range =
    props.periodMode === 'month'
      ? { from: props.selectedDate, to: props.selectedDate }
      : props.periodRange;
  return groupHomeworkPeriod(props.homework, range);
});
const periodModes = [
  { value: 'day', label: 'День' },
  { value: 'week', label: 'Неделя' },
  { value: 'month', label: 'Месяц' },
] as const;
const periodTabs = ref<HTMLElement | null>(null);
function periodDateLabel(date: string): string {
  const label = new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(`${date}T12:00:00Z`));
  return date === props.todayDate ? `Сегодня, ${label}` : label;
}
const periodLabel = computed(() => {
  if (!props.selectedDate || !props.periodRange) return '';
  if (props.periodMode === 'month')
    return `${formatHomeworkMonth(props.selectedDate)} ${props.selectedDate.slice(0, 4)}`;
  const start = periodDateLabel(props.periodRange.from);
  return props.periodMode === 'day' ? start : `${start} — ${periodDateLabel(props.periodRange.to)}`;
});
const calendarState = computed(() => {
  if (props.loading) return 'loading';
  if (props.error) return 'error';
  const feed = props.homework,
    range = props.periodRange;
  return feed && range && feed.from <= range.from && feed.to >= range.to ? 'ready' : 'unknown';
});
const monthDays = computed(() => {
  if (!props.selectedDate || props.periodMode !== 'month') return [];
  const first = props.selectedDate.slice(0, 7) + '-01';
  const next = shiftHomeworkPeriod('month', first, 1);
  const prefix = (new Date(`${first}T12:00:00Z`).getUTCDay() + 6) % 7;
  const days: (string | null)[] = Array.from({ length: prefix }, () => null);
  for (let day = first; day < next; day = addHomeworkDays(day, 1)) days.push(day);
  return days;
});
const calendarItems = computed(() => {
  const result = new Map<string, { count: number; subjects: string[] }>();
  for (const item of dedupeHomeworkItems(props.homework?.items ?? [])) {
    const entry = result.get(item.lessonDate) ?? { count: 0, subjects: [] };
    entry.count += 1;
    if (!entry.subjects.includes(item.subject.name)) entry.subjects.push(item.subject.name);
    result.set(item.lessonDate, entry);
  }
  return result;
});
function dateAvailable(day: string): boolean {
  return Boolean(
    props.semesterRange && day >= props.semesterRange.from && day <= props.semesterRange.to,
  );
}
function canStep(step: number): boolean {
  const range = props.periodRange,
    semester = props.semesterRange;
  return Boolean(
    range &&
      semester &&
      !props.offline &&
      (step < 0 ? range.from > semester.from : range.to < semester.to),
  );
}
function calendarLabel(day: string): string {
  const date = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeZone: 'UTC' }).format(
    new Date(`${day}T12:00:00Z`),
  );
  const content = calendarItems.value.get(day);
  const state = calendarState.value;
  const summary =
    state === 'ready'
      ? content
        ? `Заданий: ${content.count}. ${content.subjects.join(', ')}`
        : 'Нет заданий'
      : state === 'loading'
        ? 'Данные загружаются'
        : state === 'error'
          ? 'Не удалось загрузить данные'
          : 'Данные не подтверждены';
  return `${date}${day === props.todayDate ? ', сегодня' : ''}. ${dateAvailable(day) ? summary : 'Вне активного семестра'}`;
}
async function movePeriodTab(event: KeyboardEvent, index: number): Promise<void> {
  if (props.offline) return;
  let target: number;
  if (event.key === 'ArrowLeft') target = (index + 2) % 3;
  else if (event.key === 'ArrowRight') target = (index + 1) % 3;
  else if (event.key === 'Home') target = 0;
  else if (event.key === 'End') target = 2;
  else return;
  event.preventDefault();
  const mode = periodModes[target]!.value;
  emit('selectPeriod', mode);
  await nextTick();
  periodTabs.value?.querySelector<HTMLButtonElement>(`[data-mode="${mode}"]`)?.focus();
}
const isReadOnly = computed(() => props.offline || props.readOnly);
const persistedUpdatedAt = computed(() => {
  if (!props.updatedAt || !Number.isFinite(Date.parse(props.updatedAt))) return null;
  return new Date(props.updatedAt).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' });
});

function itemPending(id: string): boolean {
  if (props.isItemPending) return props.isItemPending(id);
  const pendingIds = props.pendingIds;
  if (!pendingIds) return false;
  if ('has' in pendingIds) return pendingIds.has(id);
  return pendingIds.includes(id);
}

function getItemError(item: StudentHomeworkItem): string | null {
  return props.itemError?.(item.id) ?? props.itemErrors?.[item.id] ?? null;
}

function iconStyle(icon: string): Record<string, string> {
  return { '--rct-icon-mask': `url("${icon}")` };
}

function isExpanded(id: string): boolean {
  return expandedIds.value.has(id);
}

function hasDescription(item: StudentHomeworkItem): boolean {
  return item.description.trim().length > 0;
}

function toggleExpanded(id: string): void {
  const next = new Set(expandedIds.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  expandedIds.value = next;
}

function materialState(item: StudentHomeworkItem): HomeworkLinkValidation {
  return validateHomeworkLink(item.link);
}

function openMaterial(item: StudentHomeworkItem): void {
  const result = materialState(item);
  if (result.supported) emit('openMaterial', result.original, item);
}

function materialHostname(item: StudentHomeworkItem): string {
  const result = materialState(item);
  return result.supported ? new URL(result.original).hostname : '';
}

function completionLabel(item: StudentHomeworkItem): string {
  if (itemPending(item.id)) return 'Сохраняем…';
  if (item.archived) return item.completed ? 'Выполнено · архив' : 'Архив · только чтение';
  if (item.completed) return 'Выполнено';
  if (isReadOnly.value) return 'Только просмотр';
  return 'Проведи, чтобы выполнить';
}

function requestCompletion(item: StudentHomeworkItem, desired: boolean): void {
  if (isReadOnly.value || item.archived || itemPending(item.id)) return;
  rememberCompletionFocus(item, desired);
  emit('complete', item, desired);
}

function retryItem(item: StudentHomeworkItem): void {
  if (!isReadOnly.value && !item.archived && !itemPending(item.id)) {
    rememberCompletionFocus(item, !item.completed);
    emit('retry', item);
  }
}

function setTrackRef(id: string, element: unknown): void {
  if (typeof HTMLElement !== 'undefined' && element instanceof HTMLElement) {
    trackElements.set(id, element);
    return;
  }
  // Vue can clear the previous ref after assigning the moved keyed node. Keep
  // a connected replacement so ACK reorder still has a focus target.
  if (!trackElements.get(id)?.isConnected) trackElements.delete(id);
}

function setCardHeadingRef(id: string, element: unknown): void {
  if (typeof HTMLElement !== 'undefined' && element instanceof HTMLElement) {
    cardHeadingElements.set(id, element);
    return;
  }
  if (!cardHeadingElements.get(id)?.isConnected) cardHeadingElements.delete(id);
}

function rememberCompletionFocus(item: StudentHomeworkItem, expectedCompleted: boolean): void {
  focusRequest.value = {
    id: item.id,
    expectedCompleted,
    origin: typeof document === 'undefined' ? null : document.activeElement,
    feedIdentity: homeworkFeedIdentity(props.homework),
    ownerKey: props.ownerKey ?? null,
  };
}

function groupHasMonth(index: number): boolean {
  return index === 0 || groups.value[index - 1]?.monthLabel !== groups.value[index]?.monthLabel;
}

function groupId(group: HomeworkGroup): string {
  return `homework-group-${group.key.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
}

const completionSignature = computed(() => {
  const feed = props.homework;
  const items =
    feed?.items
      .map((item) =>
        [
          item.id,
          item.completed ? '1' : '0',
          item.completedAt ?? '',
          itemPending(item.id) ? 'pending' : 'settled',
        ].join(':'),
      )
      .join('|') ?? '';
  return `${homeworkFeedIdentity(feed) ?? ''}|${items}`;
});

function currentActiveElement(): Element | null {
  return typeof document === 'undefined' ? null : document.activeElement;
}

function currentDocumentBody(): Element | null {
  return typeof document === 'undefined' ? null : document.body;
}

function clearStaleFocusRequest(request: HomeworkFocusRequest): boolean {
  const feedIdentity = homeworkFeedIdentity(props.homework);
  const ownerKey = props.ownerKey ?? null;
  if (request.feedIdentity !== feedIdentity || request.ownerKey !== ownerKey) {
    if (focusRequest.value === request) focusRequest.value = null;
    return true;
  }
  const activeElement = currentActiveElement();
  const body = currentDocumentBody();
  if (activeElement && activeElement !== body && activeElement !== request.origin) {
    if (focusRequest.value === request) focusRequest.value = null;
    return true;
  }
  return false;
}

async function restoreCompletionFocus(): Promise<void> {
  const request = focusRequest.value;
  if (!request || clearStaleFocusRequest(request)) return;
  const feed = props.homework;
  const item = feed?.items.find((entry) => entry.id === request.id);
  if (!item || itemPending(request.id)) return;

  if (
    !canRestoreHomeworkFocus({
      request,
      item,
      pending: false,
      activeElement: currentActiveElement(),
      documentBody: currentDocumentBody(),
      feedIdentity: homeworkFeedIdentity(feed),
      ownerKey: props.ownerKey ?? null,
    })
  )
    return;

  await nextTick();
  if (focusRequest.value !== request || clearStaleFocusRequest(request)) return;
  const target = trackElements.get(request.id);
  if (!target || !target.isConnected) return;
  target.focus();
  if (focusRequest.value === request) focusRequest.value = null;
}

watch(
  completionSignature,
  () => {
    void restoreCompletionFocus();
  },
  { flush: 'post' },
);

watch(
  () => [props.focusItemId, props.focusRequestId, props.homework?.items] as const,
  async ([id, requestId]) => {
    if (!id || requestId === null || !props.homework?.items.some((item) => item.id === id)) return;
    const next = new Set(expandedIds.value);
    next.add(id);
    expandedIds.value = next;
    await nextTick();
    if (props.focusItemId !== id || props.focusRequestId !== requestId) return;
    const target = trackElements.get(id);
    if (target?.isConnected && !target.matches(':disabled')) target.focus();
    else {
      const heading = cardHeadingElements.get(id);
      if (heading?.isConnected) heading.focus();
    }
  },
  { flush: 'post', immediate: true },
);

watch(
  () => `${props.ownerKey ?? ''}|${props.route?.id ?? ''}|${props.activeId ?? ''}`,
  () => {
    focusRequest.value = null;
  },
);

onBeforeUnmount(() => {
  focusRequest.value = null;
  trackElements.clear();
  cardHeadingElements.clear();
});
</script>

<template>
  <MobileShell
    :route="route ?? homeworkRoute"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="activeId"
    v-bind="shellOptionalProps"
    :host="host"
    @navigate="emit('navigate', $event)"
    @back="emit('back', $event)"
  >
    <main
      class="homework-screen"
      aria-labelledby="homework-title"
    >
      <div class="homework-content">
        <h1 id="homework-title">
          Задания
        </h1>

        <template v-if="periodRange && selectedDate">
          <div
            ref="periodTabs"
            class="homework-period-tabs"
            role="tablist"
            aria-label="Период заданий"
          >
            <button
              v-for="(mode, index) in periodModes"
              :id="`homework-tab-${mode.value}`"
              :key="mode.value"
              type="button"
              role="tab"
              :data-mode="mode.value"
              :aria-selected="periodMode === mode.value"
              :tabindex="periodMode === mode.value ? 0 : -1"
              aria-controls="homework-period-panel"
              :disabled="offline"
              @click="emit('selectPeriod', mode.value)"
              @keydown="movePeriodTab($event, index)"
            >
              {{ mode.label }}
            </button>
          </div>
          <nav
            class="homework-period-pager"
            aria-label="Выбор периода заданий"
          >
            <button
              type="button"
              aria-label="Предыдущий период"
              :disabled="!canStep(-1)"
              @click="emit('stepPeriod', -1)"
            >
              ←
            </button>
            <output aria-live="polite">{{ periodLabel }}</output>
            <button
              type="button"
              aria-label="Следующий период"
              :disabled="!canStep(1)"
              @click="emit('stepPeriod', 1)"
            >
              →
            </button>
          </nav>
          <div
            v-if="periodMode === 'month'"
            class="homework-calendar-scroll"
            role="region"
            tabindex="0"
            aria-label="Календарь заданий"
          >
            <p
              v-if="calendarState !== 'ready'"
              role="status"
            >
              {{ calendarState === 'loading' ? 'Загружаем календарь…' : calendarState === 'error' ? 'Не удалось загрузить календарь.' : 'Данные календаря не подтверждены.' }}
            </p>
            <div
              class="homework-calendar"
              aria-label="Задания по дням месяца"
              :aria-busy="calendarState === 'loading'"
            >
              <span
                v-for="weekday in ['ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ', 'ВС']"
                :key="weekday"
                aria-hidden="true"
              >{{ weekday }}</span>
              <template
                v-for="(day, index) in monthDays"
                :key="day ?? `blank-${index}`"
              >
                <button
                  v-if="day"
                  type="button"
                  :data-homework-date="day"
                  :disabled="offline || !dateAvailable(day) || calendarState !== 'ready'"
                  :aria-label="calendarLabel(day)"
                  :aria-pressed="day === selectedDate"
                  :aria-current="day === todayDate ? 'date' : undefined"
                  @click="emit('selectDate', day)"
                >
                  <strong>{{ Number(day.slice(8)) }}</strong>
                  <small v-if="day === todayDate">Сегодня</small>
                  <span
                    v-for="subject in calendarState === 'ready' ? calendarItems.get(day)?.subjects ?? [] : []"
                    :key="subject"
                    :title="subject"
                  >{{ subject }}</span>
                </button>
                <span
                  v-else
                  aria-hidden="true"
                />
              </template>
            </div>
          </div>
        </template>

        <StudentWarningBlock
          v-if="offline || readOnly"
          :title="offline ? 'Ты офлайн' : 'Только просмотр'"
          :message="
            offline
              ? `Задания доступны для просмотра.${persistedUpdatedAt ? ` Данные обновлены ${persistedUpdatedAt}.` : ''}`
              : 'Изменение выполнения сейчас недоступно.'
          "
        />

        <button
          v-if="!periodRange && canLoadPrevious && !historical"
          class="homework-previous"
          type="button"
          @click="emit('previous')"
        >
          Посмотреть предыдущие
        </button>

        <div
          id="homework-period-panel"
          :role="periodRange ? 'tabpanel' : undefined"
          :aria-labelledby="periodRange ? `homework-tab-${periodMode}` : undefined"
          class="homework-period-panel"
        >
          <section
            v-if="loading && !homework"
            class="homework-skeleton"
            aria-label="Загружаем задания"
            role="status"
            aria-busy="true"
          >
            <div
              v-for="index in 3"
              :key="index"
              class="homework-skeleton__card"
              aria-hidden="true"
            >
              <span class="homework-skeleton__line" />
              <span class="homework-skeleton__line" />
              <span class="homework-skeleton__track" />
            </div>
          </section>
          <StudentWarningBlock
            v-else-if="error && !homework"
            severity="error"
            title="Не удалось получить задания"
            :message="error"
            action-label="Повторить"
            @action="emit('retryFeed')"
          />
          <StudentWarningBlock
            v-else-if="unavailableMessage"
            title="Задания недоступны"
            :message="unavailableMessage"
          />
          <section
            v-else-if="periodMode === 'month' && calendarState !== 'ready'"
            class="homework-state"
            role="status"
          >
            <p>Данные выбранного месяца пока недоступны.</p>
            <button
              v-if="!offline"
              type="button"
              @click="emit('retryFeed')"
            >
              Повторить
            </button>
          </section>
          <section
            v-else-if="!homework || groups.length === 0"
            class="homework-state"
          >
            <h2>Заданий пока нет</h2>
            <p>Новые задания появятся здесь после публикации.</p>
          </section>
          <template v-else>
            <StudentWarningBlock
              v-if="error"
              severity="error"
              title="Не удалось обновить задания"
              :message="error"
              action-label="Повторить"
              @action="emit('retryFeed')"
            />

            <section
              v-for="(group, groupIndex) in groups"
              :key="group.key"
              class="homework-group"
              :aria-labelledby="groupId(group)"
            >
              <p
                v-if="groupHasMonth(groupIndex) && group.monthLabel"
                class="homework-month"
              >
                {{ group.monthLabel }}
              </p>
              <h2 :id="groupId(group)">
                {{ group.label }}
              </h2>
              <p
                v-if="group.items.length === 0"
                class="homework-empty-day"
              >
                На этот день заданий нет.
              </p>
              <div class="homework-group__cards">
                <article
                  v-for="item in group.items"
                  :key="item.id"
                  class="homework-card"
                  :data-completion="item.completed ? 'completed' : 'open'"
                  :data-pending="itemPending(item.id)"
                  :data-material="
                    item.link === null
                      ? 'none'
                      : materialState(item).supported
                        ? 'available'
                        : 'unsafe'
                  "
                >
                  <h3
                    :ref="(element) => setCardHeadingRef(item.id, element)"
                    tabindex="-1"
                  >
                    {{ item.subject.name }}
                  </h3>
                  <p class="homework-card__title">
                    {{ item.title }}
                  </p>
                  <p
                    v-if="hasDescription(item) && isExpanded(item.id)"
                    :id="`homework-description-${item.id}`"
                    class="homework-card__description"
                  >
                    <template
                      v-for="(segment, index) in homeworkTextSegments(item.description)"
                      :key="index"
                    >
                      <a
                        v-if="segment.url"
                        :href="segment.url"
                        target="_blank"
                        rel="noopener noreferrer"
                        :aria-label="`Материалы к заданию: ${item.subject.name} (${segment.text})`"
                        @click.prevent="emit('openMaterial', segment.url, item)"
                      >{{ segment.text }}</a>
                      <template v-else>
                        {{ segment.text }}
                      </template>
                    </template>
                  </p>
                  <div
                    v-if="item.link !== null || hasDescription(item)"
                    class="homework-card__actions"
                    :class="{ 'homework-card__actions--no-material': item.link === null }"
                  >
                    <button
                      v-if="item.link !== null && materialState(item).supported"
                      class="homework-material"
                      type="button"
                      :aria-label="`Материалы к заданию: ${item.subject.name}`"
                      @click="openMaterial(item)"
                    >
                      <span
                        class="homework-icon homework-material__icon"
                        :style="iconStyle(externalLinkIcon)"
                        aria-hidden="true"
                      />
                      <span class="homework-material__label">
                        Материалы
                        <span class="homework-material__hostname">{{ materialHostname(item) }}</span>
                      </span>
                    </button>
                    <p
                      v-else-if="item.link !== null"
                      class="homework-material-error"
                      role="status"
                    >
                      Материалы недоступны: ссылка не поддерживается.
                    </p>
                    <button
                      v-if="hasDescription(item)"
                      class="homework-expand"
                      type="button"
                      :aria-expanded="isExpanded(item.id)"
                      :aria-controls="
                        isExpanded(item.id) ? `homework-description-${item.id}` : undefined
                      "
                      :aria-label="isExpanded(item.id) ? 'Свернуть описание' : 'Раскрыть описание'"
                      @click="toggleExpanded(item.id)"
                    >
                      <span
                        class="homework-icon homework-expand__icon"
                        :style="iconStyle(expandChevronIcon)"
                        aria-hidden="true"
                        :class="{ 'homework-expand__icon--open': isExpanded(item.id) }"
                      />
                    </button>
                  </div>
                  <HomeworkCompletion
                    :key="`${item.id}:${ownerKey ?? ''}`"
                    :completed="item.completed"
                    :disabled="
                      isReadOnly || item.archived || itemPending(item.id) || !!getItemError(item)
                    "
                    :pending="itemPending(item.id)"
                    :label="completionLabel(item)"
                    :subject="item.subject.name"
                    :reset-key="`${ownerKey ?? ''}:${homeworkFeedIdentity(homework) ?? ''}`"
                    @handle-ready="setTrackRef(item.id, $event)"
                    @complete="requestCompletion(item, $event)"
                  />
                  <StudentWarningBlock
                    v-if="getItemError(item)"
                    severity="error"
                    title="Отметка не сохранена"
                    :message="getItemError(item) ?? ''"
                    action-label="Повторить"
                    :action-disabled="isReadOnly || item.archived || itemPending(item.id)"
                    @action="retryItem(item)"
                  />
                </article>
              </div>
            </section>
          </template>
        </div>
      </div>

      <button
        v-if="historical"
        class="homework-return-today"
        type="button"
        aria-label="Вернуться к сегодняшним заданиям"
        @click="emit('returnToday')"
      >
        <span
          class="homework-icon homework-return-today__icon"
          :style="iconStyle(returnTodayChevronIcon)"
          aria-hidden="true"
        />
      </button>
    </main>
  </MobileShell>
</template>
