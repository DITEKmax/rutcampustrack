<script setup lang="ts">
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import attendanceTab from '../../assets/attendance-tab.svg';
import attendanceRest from '../../assets/student-attendance-rest.svg?raw';
import moreTab from '../../assets/more-tab.svg';
import profileTab from '../../assets/profile-tab.svg';
import scheduleTab from '../../assets/schedule-tab.svg';
import AttendanceGraph from './AttendanceGraph.vue';
import AttendanceLessonRow from './AttendanceLessonRow.vue';
import AttendanceSubjectList from './AttendanceSubjectList.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import {
  academicToday,
  attendanceBounds,
  calendarWeek,
  graphPeriods,
  monday,
  shiftDate,
  shortDate,
} from './attendance-periods';
import {
  displayPercent,
  findAttendanceLesson,
  formatDayDate,
  type AttendanceDocumentResolver,
  type AttendanceGraphRange,
  type AttendanceLesson,
  type AttendanceMode,
  type AttendanceReadState,
  type AttendanceRequestOption,
  type AttendanceTheme,
  type AttendanceViewModel,
} from './attendance-view-model';
import './attendance-screen.pcss';

export interface AttendanceRequestFormSlotContext {
  readonly selectedLesson: AttendanceLesson;
  readonly requestOption: AttendanceRequestOption | null;
  readonly requestOptions: readonly AttendanceRequestOption[];
  readonly onClose: () => void;
  readonly onCompleted: () => void;
}

const props = withDefaults(
  defineProps<{
    state: AttendanceReadState<AttendanceViewModel>;
    selectedDate: string;
    mode?: AttendanceMode;
    graphRange?: AttendanceGraphRange;
    expandedSubjectId?: string | null;
    actionLessonId?: string | null;
    focusedLessonId?: string | null;
    requestLessonId?: string | null;
    requestOptionId?: string | null;
    theme?: AttendanceTheme | undefined;
    terminal?: boolean;
    showDock?: boolean;
    overlayActive?: boolean;
    documentForLesson?: AttendanceDocumentResolver | undefined;
  }>(),
  {
    documentForLesson: undefined,
    theme: undefined,
    mode: 'days',
    graphRange: 'days',
    expandedSubjectId: null,
    actionLessonId: null,
    focusedLessonId: null,
    requestLessonId: null,
    requestOptionId: null,
    terminal: false,
    showDock: false,
    overlayActive: false,
  },
);

const emit = defineEmits<{
  'select-date': [date: string];
  'set-mode': [mode: AttendanceMode];
  'set-graph-range': [range: AttendanceGraphRange];
  'toggle-subject': [subjectId: string];
  'toggle-actions': [lessonId: string];
  'open-request': [lesson: AttendanceLesson, option: AttendanceRequestOption];
  back: [];
  retry: [];
  'request-completed': [lessonId: string];
  'overlay-visible': [visible: boolean];
}>();

defineSlots<{
  'request-form'?: (context: AttendanceRequestFormSlotContext) => unknown;
}>();

const data = computed(() => (props.state.status === 'ready' ? props.state.data : null));
const readyData = computed(() => data.value ?? emptyViewModel);
const selectedLessons = computed(
  () => data.value?.days.find((day) => day.date === props.selectedDate)?.lessons ?? [],
);
const selectedLesson = computed(() =>
  findAttendanceLesson(data.value ?? emptyViewModel, props.requestLessonId),
);
const selectedRequestOption = computed(() => {
  const lesson = selectedLesson.value;
  if (!lesson) return null;
  return lesson.requestOptions.find((option) => option.id === props.requestOptionId) ?? null;
});
const graphPoints = computed(() => (data.value ? graphPeriods(data.value, props.graphRange) : []));
const weekDate = ref(props.selectedDate);
watch(
  () => props.selectedDate,
  (date) => {
    weekDate.value = date;
  },
);
const weekDays = computed(() => calendarWeek(readyData.value, weekDate.value));
const railDays = computed(() =>
  weekDays.value.filter((day) => new Date(`${day.date}T12:00:00Z`).getUTCDay() !== 0),
);
const selectedWeek = computed(
  () => data.value?.weeks?.find((week) => week.dateFrom === monday(weekDate.value)) ?? null,
);
const sunday = computed(() =>
  weekDays.value.find((day) => new Date(`${day.date}T12:00:00Z`).getUTCDay() === 0),
);
const bounds = computed(() => attendanceBounds(readyData.value));
function canPageWeek(delta: number): boolean {
  const next = shiftDate(monday(weekDate.value), delta * 7);
  return Boolean(
    bounds.value && next <= bounds.value.to && shiftDate(next, 6) >= bounds.value.from,
  );
}
function pageWeek(delta: number): void {
  if (!canPageWeek(delta)) return;
  weekDate.value = shiftDate(monday(weekDate.value), delta * 7);
  const date =
    bounds.value && weekDate.value < bounds.value.from ? bounds.value.from : weekDate.value;
  emit('select-date', date);
}
function railKeyboard(event: KeyboardEvent): void {
  const offset = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0;
  if (!offset && event.key !== 'Home' && event.key !== 'End') return;
  event.preventDefault();
  const current = event.currentTarget as HTMLButtonElement;
  const buttons = Array.from(
    current.parentElement?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [],
  );
  const index = buttons.indexOf(current);
  const next =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? buttons.length - 1
        : Math.max(0, Math.min(buttons.length - 1, index + offset));
  const target = buttons[next];
  target?.click();
  target?.focus();
  target?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}
const isInlineRequest = computed(
  () => Boolean(selectedLesson.value) && props.requestLessonId !== null,
);
const clockTick = ref(Date.now());
let serverClockObservedAt = Date.now();
watch(
  () => data.value?.serverNow,
  () => {
    serverClockObservedAt = Date.now();
    clockTick.value = serverClockObservedAt;
  },
);
const currentAcademicDay = computed(() =>
  academicToday(data.value?.serverNow, clockTick.value - serverClockObservedAt, clockTick.value),
);
const openExplanations = ref<ReadonlySet<string>>(new Set());
watch(
  () => openExplanations.value.size > 0,
  (visible) => emit('overlay-visible', visible),
);
const subjectList = ref<InstanceType<typeof AttendanceSubjectList> | null>(null);
const lessonRows = ref<InstanceType<typeof AttendanceLessonRow>[]>([]);
const inlineLessonRow = ref<InstanceType<typeof AttendanceLessonRow> | null>(null);
function closeOverlay(): boolean {
  if (subjectList.value?.closeOverlay() || inlineLessonRow.value?.closeOverlay()) return true;
  return lessonRows.value.some((row) => row.closeOverlay());
}
defineExpose({ closeOverlay });
function explanationVisibility(id: string, visible: boolean): void {
  const next = new Set(openExplanations.value);
  if (visible) next.add(id);
  else next.delete(id);
  openExplanations.value = next;
}
watch(
  () => [props.mode, props.selectedDate, props.state],
  () => {
    openExplanations.value = new Set();
  },
);
const todayVisible = computed(
  () =>
    props.state.status === 'ready' &&
    props.selectedDate !== currentAcademicDay.value &&
    !props.overlayActive &&
    !isInlineRequest.value &&
    openExplanations.value.size === 0,
);
const todayAvailable = computed(() =>
  Boolean(
    bounds.value &&
      currentAcademicDay.value >= bounds.value.from &&
      currentAcademicDay.value <= bounds.value.to,
  ),
);
function selectToday(): void {
  clockTick.value = Date.now();
  if (!todayAvailable.value) return;
  emit('set-mode', 'days');
  emit('select-date', currentAcademicDay.value);
}
let clockTimer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
  clockTimer = setInterval(() => {
    clockTick.value = Date.now();
  }, 30_000);
});
onBeforeUnmount(() => {
  if (clockTimer !== null) clearInterval(clockTimer);
  emit('overlay-visible', false);
});
const screenRoot = ref<HTMLElement | null>(null);
const backButton = ref<HTMLButtonElement | null>(null);
let requestReturnLessonId: string | null = null;

function focusRequestTrigger(lessonId: string): void {
  const trigger = Array.from(
    screenRoot.value?.querySelectorAll<HTMLButtonElement>('[data-request-trigger]') ?? [],
  ).find((candidate) => candidate.dataset.requestTrigger === lessonId);
  trigger?.focus();
}

watch(
  () => props.requestLessonId,
  (nextLessonId, previousLessonId) => {
    if (nextLessonId !== null) {
      if (previousLessonId === null) requestReturnLessonId = nextLessonId;
      void nextTick(() => backButton.value?.focus());
      return;
    }
    if (previousLessonId !== null) {
      const returnLessonId = requestReturnLessonId ?? previousLessonId;
      requestReturnLessonId = null;
      void nextTick(() => focusRequestTrigger(returnLessonId));
    }
  },
);

watch(
  () => [props.focusedLessonId, props.selectedDate, props.mode] as const,
  ([lessonId, , mode]) => {
    if (!lessonId || mode !== 'days') return;
    void nextTick(() => {
      const row = Array.from(
        screenRoot.value?.querySelectorAll<HTMLElement>('[data-lesson-id]') ?? [],
      ).find((candidate) => candidate.dataset.lessonId === lessonId);
      row?.querySelector<HTMLElement>('[data-notification-target]')?.focus();
    });
  },
  { flush: 'post', immediate: true },
);

const emptyViewModel: AttendanceViewModel = {
  metrics: {
    present: { count: 0, percent: null },
    presentOrExcused: { count: 0, percent: null },
    excused: { count: 0, percent: null },
    absent: { count: 0, percent: null },
    held: 0,
    planned: 0,
  },
  days: [],
  subjects: [],
  graph: { days: [], weeks: [] },
};

function onRequestCompleted(): void {
  if (selectedLesson.value) emit('request-completed', selectedLesson.value.id);
}

function stateTitle(): string {
  switch (props.state.status) {
    case 'loading':
      return 'Загружаем посещаемость…';
    case 'empty':
      return 'За этот период данных пока нет';
    case 'offline':
      return 'Посещаемость недоступна офлайн';
    case 'forbidden':
      return 'Посещаемость недоступна';
    case 'error':
      return 'Не удалось получить посещаемость';
    case 'no-semester':
      return 'Нет активного семестра';
    default:
      return '';
  }
}

function stateMessage(): string {
  switch (props.state.status) {
    case 'offline':
      return 'Подключись к интернету, чтобы получить актуальные данные.';
    case 'forbidden':
      return props.state.reason;
    case 'error':
      return props.state.message;
    case 'empty':
      return 'Когда появятся закрытые пары, здесь будет история.';
    case 'no-semester':
      return 'Посещаемость появится, когда начнётся семестр.';
    default:
      return '';
  }
}

function dateLabel(date: string): string {
  return formatDayDate(date);
}
</script>

<template>
  <main
    ref="screenRoot"
    class="attendance-screen"
    :data-today-visible="todayVisible"
    :data-theme="theme"
    :aria-busy="state.status === 'loading'"
    aria-labelledby="attendance-title"
  >
    <template v-if="isInlineRequest && selectedLesson">
      <header class="attendance-inline__header">
        <button
          ref="backButton"
          class="attendance-back-button"
          type="button"
          aria-label="Назад к посещаемости"
          @click="emit('back')"
        >
          <MobileIcon name="back" />
        </button>
        <h1 id="attendance-title">
          {{ selectedRequestOption?.label ?? 'Форма заявки' }}
        </h1>
      </header>
      <ol
        class="attendance-inline__lesson attendance-lesson-list"
        aria-label="Выбранная пара"
      >
        <AttendanceLessonRow
          ref="inlineLessonRow"
          :lesson="selectedLesson"
          :document-for-lesson="documentForLesson"
          :notification-target="selectedLesson.id === focusedLessonId"
          :terminal="terminal"
        />
      </ol>
      <div class="attendance-inline__slot">
        <slot
          name="request-form"
          :selected-lesson="selectedLesson"
          :request-option="selectedRequestOption"
          :request-options="selectedLesson.requestOptions"
          :on-close="() => emit('back')"
          :on-completed="onRequestCompleted"
        >
          <section
            class="attendance-inline__slot-open"
            role="status"
            data-slot-state="OPEN"
          >
            <h2>Форма заявки</h2>
            <p>Форма заявки пока недоступна.</p>
          </section>
        </slot>
      </div>
    </template>

    <template v-else>
      <header class="attendance-screen__header">
        <h1 id="attendance-title"> Посещаемость </h1>
      </header>

      <StudentWarningBlock
        v-if="
          state.status === 'error' || state.status === 'forbidden' || state.status === 'offline'
        "
        :title="stateTitle()"
        :message="stateMessage()"
        :severity="state.status === 'offline' ? 'warning' : 'error'"
        :action-label="state.status === 'error' && state.retryable ? 'Повторить' : ''"
        @action="emit('retry')"
      />
      <section
        v-else-if="state.status !== 'ready'"
        class="attendance-state"
        :data-state="state.status"
        role="status"
      >
        <span
          v-if="state.status === 'loading'"
          class="attendance-state__spinner"
          aria-hidden="true"
        />
        <h2>{{ stateTitle() }}</h2>
        <p v-if="stateMessage()">
          {{ stateMessage() }}
        </p>
      </section>

      <template v-else>
        <StudentWarningBlock
          v-if="terminal"
          title="Посещаемость только для чтения"
          message="Ты можешь смотреть историю. Отправка заявок сейчас недоступна."
        />
        <section
          v-if="mode !== 'subjects'"
          class="attendance-metrics"
          aria-label="Метрики посещаемости"
        >
          <article class="attendance-metric attendance-metric--present">
            <span
              class="attendance-metric__symbol"
              aria-hidden="true"
              >+</span
            >
            <span>{{ displayPercent(readyData.metrics.present.percent) }}</span>
            <span class="attendance-visually-hidden"
              >был, {{ readyData.metrics.present.count }}</span
            >
          </article>
          <article class="attendance-metric attendance-metric--absent">
            <span
              class="attendance-metric__symbol"
              aria-hidden="true"
              >н</span
            >
            <span>{{ displayPercent(readyData.metrics.absent.percent) }}</span>
            <span class="attendance-visually-hidden"
              >не был, {{ readyData.metrics.absent.count }}</span
            >
          </article>
          <article class="attendance-metric attendance-metric--excused">
            <span
              class="attendance-metric__symbol"
              aria-hidden="true"
              >у</span
            >
            <span>{{ displayPercent(readyData.metrics.excused.percent) }}</span>
            <span class="attendance-visually-hidden"
              >уважительная причина, {{ readyData.metrics.excused.count }}</span
            >
          </article>
        </section>

        <nav
          class="attendance-view-switch"
          aria-label="Представление посещаемости"
        >
          <button
            type="button"
            :class="{ 'attendance-view-switch__button--selected': mode === 'days' }"
            :aria-pressed="mode === 'days'"
            @click="emit('set-mode', 'days')"
          >
            По дням
          </button>
          <button
            type="button"
            :class="{ 'attendance-view-switch__button--selected': mode === 'subjects' }"
            :aria-pressed="mode === 'subjects'"
            @click="emit('set-mode', 'subjects')"
          >
            По предметам
          </button>
          <button
            class="attendance-view-switch__graph"
            type="button"
            :class="{ 'attendance-view-switch__button--selected': mode === 'graph' }"
            :aria-pressed="mode === 'graph'"
            aria-label="График посещаемости"
            @click="emit('set-mode', 'graph')"
          >
            <MobileIcon name="attendance-graph" />
          </button>
        </nav>

        <section
          v-if="mode === 'days'"
          class="attendance-days"
          aria-labelledby="attendance-days-title"
        >
          <h2
            id="attendance-days-title"
            class="attendance-visually-hidden"
          >
            Дни семестра
          </h2>
          <div class="attendance-period-pager attendance-week-pager">
            <button
              type="button"
              aria-label="Предыдущая неделя"
              :disabled="!canPageWeek(-1)"
              @click="pageWeek(-1)"
            >
              <MobileIcon name="back" />
            </button>
            <span
              class="attendance-week-pager__label"
              role="status"
            >
              <strong>{{ dateLabel(selectedDate) }}</strong>
              <span v-if="weekDays.length"
                >{{ shortDate(weekDays[0]!.date) }} —
                {{ shortDate(weekDays[weekDays.length - 1]!.date) }}</span
              >
              <span
                v-if="selectedWeek"
                class="attendance-week-pager__metadata"
                >Неделя {{ selectedWeek.weekOfSemester }} · Чётность
                {{ selectedWeek.parityLabel }}</span
              >
            </span>
            <button
              type="button"
              aria-label="Следующая неделя"
              :disabled="!canPageWeek(1)"
              @click="pageWeek(1)"
            >
              <MobileIcon
                name="back"
                class="attendance-period-pager__next"
              />
            </button>
          </div>
          <div
            class="attendance-day-rail"
            role="group"
            aria-label="Дни семестра"
          >
            <button
              v-for="day in railDays"
              :key="day.date"
              class="attendance-day-rail__item"
              :class="{ 'attendance-day-rail__item--selected': day.date === selectedDate }"
              type="button"
              :aria-pressed="day.date === selectedDate"
              :aria-label="dateLabel(day.date)"
              @click="emit('select-date', day.date)"
              @keydown="railKeyboard"
            >
              <span>{{ day.weekday }}</span>
              <strong>{{ day.dayNumber }}</strong>
            </button>
          </div>

          <button
            v-if="sunday && (sunday.lessons.length || sunday.date === selectedDate)"
            class="attendance-sunday"
            type="button"
            :aria-pressed="sunday.date === selectedDate"
            @click="emit('select-date', sunday.date)"
          >
            Воскресенье, {{ shortDate(sunday.date) }} · {{ sunday.lessons.length }} пар
          </button>
          <div
            v-if="selectedLessons.length === 0"
            class="attendance-days__empty"
          >
            <!-- eslint-disable vue/no-v-html -- Trusted bundled decorative SVG, never user or API content. -->
            <span
              class="attendance-days__empty-art"
              aria-hidden="true"
              v-html="attendanceRest"
            />
            <!-- eslint-enable vue/no-v-html -->
            <p>Заслуженный отдых</p>
          </div>
          <ol
            v-else
            class="attendance-lesson-list"
            aria-label="Пары выбранного дня"
          >
            <AttendanceLessonRow
              v-for="lesson in selectedLessons"
              ref="lessonRows"
              :key="lesson.id"
              :lesson="lesson"
              :document-for-lesson="documentForLesson"
              :notification-target="lesson.id === focusedLessonId"
              :actions-open="lesson.id === actionLessonId"
              :terminal="terminal"
              @explanation-visible="explanationVisibility(lesson.id, $event)"
              @toggle-actions="emit('toggle-actions', $event)"
              @request="(lesson, option) => emit('open-request', lesson, option)"
            />
          </ol>
        </section>

        <AttendanceSubjectList
          v-else-if="mode === 'subjects'"
          ref="subjectList"
          :subjects="readyData.subjects"
          :document-for-lesson="documentForLesson"
          :expanded-subject-id="expandedSubjectId"
          @explanation-visible="explanationVisibility('subjects', $event)"
          @toggle="emit('toggle-subject', $event)"
        />

        <AttendanceGraph
          v-else
          :points="graphPoints"
          :range="graphRange"
          :server-now="readyData.serverNow"
          @change-range="emit('set-graph-range', $event)"
        />
      </template>
    </template>

    <button
      v-if="todayVisible"
      class="attendance-today"
      type="button"
      :disabled="!todayAvailable"
      :aria-label="
        todayAvailable ? 'Вернуться к сегодняшнему дню' : 'Сегодня вне активного семестра'
      "
      @click="selectToday"
    >
      {{ todayAvailable ? 'Сегодня' : 'Сегодня вне семестра' }}
    </button>

    <nav
      v-if="showDock && !isInlineRequest"
      class="attendance-dock"
      aria-label="Основная навигация"
    >
      <span
        class="attendance-dock__item"
        aria-label="Сегодня"
      >
        <span class="attendance-dock__icon"
          ><SvgIcon
            :src="scheduleTab"
            aria-hidden="true"
        /></span>
        <span>Сегодня</span>
      </span>
      <span
        class="attendance-dock__item"
        aria-label="Задания"
      >
        <span class="attendance-dock__icon"
          ><SvgIcon
            :src="moreTab"
            aria-hidden="true"
        /></span>
        <span>Задания</span>
      </span>
      <span
        class="attendance-dock__item attendance-dock__item--active"
        aria-current="page"
      >
        <span class="attendance-dock__icon"
          ><SvgIcon
            :src="attendanceTab"
            aria-hidden="true"
        /></span>
        <span>Учёт</span>
      </span>
      <span
        class="attendance-dock__item"
        aria-label="Ещё"
      >
        <span class="attendance-dock__icon"
          ><SvgIcon
            :src="moreTab"
            aria-hidden="true"
        /></span>
        <span>Ещё</span>
      </span>
      <span
        class="attendance-dock__item"
        aria-label="Профиль"
      >
        <span class="attendance-dock__icon"
          ><SvgIcon
            :src="profileTab"
            aria-hidden="true"
        /></span>
        <span>Профиль</span>
      </span>
    </nav>
  </main>
</template>
