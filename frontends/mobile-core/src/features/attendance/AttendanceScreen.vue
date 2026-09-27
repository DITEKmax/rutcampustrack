<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import attendanceTab from '../../assets/attendance-tab.svg'
import chevronDown from '../../assets/chevron-down.svg'
import moreTab from '../../assets/more-tab.svg'
import profileTab from '../../assets/profile-tab.svg'
import scheduleTab from '../../assets/schedule-tab.svg'
import AttendanceGraph from './AttendanceGraph.vue'
import AttendanceLessonRow from './AttendanceLessonRow.vue'
import AttendanceSubjectList from './AttendanceSubjectList.vue'
import {
  displayPercent,
  findAttendanceLesson,
  formatDayDate,
  graphForRange,
  type AttendanceGraphRange,
  type AttendanceLesson,
  type AttendanceMode,
  type AttendanceReadState,
  type AttendanceRequestOption,
  type AttendanceTheme,
  type AttendanceViewModel,
} from './attendance-view-model'
import './attendance-screen.pcss'

export interface AttendanceRequestFormSlotContext {
  readonly selectedLesson: AttendanceLesson
  readonly requestOption: AttendanceRequestOption | null
  readonly requestOptions: readonly AttendanceRequestOption[]
  readonly onClose: () => void
  readonly onCompleted: () => void
}

const props = withDefaults(defineProps<{
  state: AttendanceReadState<AttendanceViewModel>
  selectedDate: string
  mode?: AttendanceMode
  graphRange?: AttendanceGraphRange
  expandedSubjectId?: string | null
  actionLessonId?: string | null
  focusedLessonId?: string | null
  requestLessonId?: string | null
  requestOptionId?: string | null
  theme?: AttendanceTheme
  terminal?: boolean
  showDock?: boolean
}>(), {
  mode: 'days',
  graphRange: 'days',
  expandedSubjectId: null,
  actionLessonId: null,
  focusedLessonId: null,
  requestLessonId: null,
  requestOptionId: null,
  theme: 'dark',
  terminal: false,
  showDock: false,
})

const emit = defineEmits<{
  'select-date': [date: string]
  'set-mode': [mode: AttendanceMode]
  'set-graph-range': [range: AttendanceGraphRange]
  'toggle-subject': [subjectId: string]
  'toggle-actions': [lessonId: string]
  'open-request': [lesson: AttendanceLesson, option: AttendanceRequestOption]
  back: []
  retry: []
  'request-completed': [lessonId: string]
}>()

defineSlots<{
  'request-form'?: (context: AttendanceRequestFormSlotContext) => unknown
}>()

const data = computed(() => props.state.status === 'ready' ? props.state.data : null)
const readyData = computed(() => data.value ?? emptyViewModel)
const selectedLessons = computed(() => data.value?.days.find((day) => day.date === props.selectedDate)?.lessons ?? [])
const selectedLesson = computed(() => findAttendanceLesson(data.value ?? emptyViewModel, props.requestLessonId))
const selectedRequestOption = computed(() => {
  const lesson = selectedLesson.value
  if (!lesson) return null
  return lesson.requestOptions.find((option) => option.id === props.requestOptionId) ?? null
})
const graphPoints = computed(() => data.value ? graphForRange(data.value, props.graphRange) : [])
const isInlineRequest = computed(() => Boolean(selectedLesson.value) && props.requestLessonId !== null)
const screenRoot = ref<HTMLElement | null>(null)
const backButton = ref<HTMLButtonElement | null>(null)
let requestReturnLessonId: string | null = null

function focusRequestTrigger(lessonId: string): void {
  const trigger = Array.from(screenRoot.value?.querySelectorAll<HTMLButtonElement>('[data-request-trigger]') ?? [])
    .find((candidate) => candidate.dataset.requestTrigger === lessonId)
  trigger?.focus()
}

watch(() => props.requestLessonId, (nextLessonId, previousLessonId) => {
  if (nextLessonId !== null) {
    if (previousLessonId === null) requestReturnLessonId = nextLessonId
    void nextTick(() => backButton.value?.focus())
    return
  }
  if (previousLessonId !== null) {
    const returnLessonId = requestReturnLessonId ?? previousLessonId
    requestReturnLessonId = null
    void nextTick(() => focusRequestTrigger(returnLessonId))
  }
})

watch(
  () => [props.focusedLessonId, props.selectedDate, props.mode] as const,
  ([lessonId, , mode]) => {
    if (!lessonId || mode !== 'days') return
    void nextTick(() => {
      const row = Array.from(screenRoot.value?.querySelectorAll<HTMLElement>('[data-lesson-id]') ?? [])
        .find((candidate) => candidate.dataset.lessonId === lessonId)
      row?.querySelector<HTMLElement>('[data-notification-target]')?.focus()
    })
  },
  { flush: 'post' },
)

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
}

function onRequestCompleted(): void {
  if (selectedLesson.value) emit('request-completed', selectedLesson.value.id)
}

function stateTitle(): string {
  switch (props.state.status) {
    case 'loading': return 'Загружаем посещаемость…'
    case 'empty': return 'За этот период данных пока нет'
    case 'offline': return 'Посещаемость недоступна офлайн'
    case 'forbidden': return 'Посещаемость недоступна'
    case 'error': return 'Не удалось получить посещаемость'
    default: return ''
  }
}

function stateMessage(): string {
  switch (props.state.status) {
    case 'offline': return 'Подключись к интернету, чтобы получить актуальные данные.'
    case 'forbidden': return props.state.reason
    case 'error': return props.state.message
    case 'empty': return 'Когда появятся закрытые пары, здесь будет история.'
    default: return ''
  }
}

function dateLabel(date: string): string {
  return formatDayDate(date)
}
</script>

<template>
  <main
    ref="screenRoot"
    class="attendance-screen"
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
          <img
            :src="chevronDown"
            alt=""
            aria-hidden="true"
          >
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
          :lesson="selectedLesson"
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
        <h1 id="attendance-title">
          Посещаемость
        </h1>
      </header>

      <section
        v-if="state.status !== 'ready'"
        class="attendance-state"
        :class="{ 'attendance-state--error': state.status === 'error' || state.status === 'forbidden' }"
        :data-state="state.status"
        :role="state.status === 'error' || state.status === 'forbidden' ? 'alert' : 'status'"
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
        <button
          v-if="state.status === 'error' && state.retryable"
          class="attendance-state__retry"
          type="button"
          @click="emit('retry')"
        >
          Повторить
        </button>
      </section>

      <template v-else>
        <section
          v-if="mode !== 'subjects'"
          class="attendance-metrics"
          aria-label="Метрики посещаемости"
        >
          <article class="attendance-metric attendance-metric--present">
            <span
              class="attendance-metric__symbol"
              aria-hidden="true"
            >+</span>
            <span>{{ displayPercent(readyData.metrics.present.percent) }}</span>
            <span class="attendance-visually-hidden">был, {{ readyData.metrics.present.count }}</span>
          </article>
          <article class="attendance-metric attendance-metric--absent">
            <span
              class="attendance-metric__symbol"
              aria-hidden="true"
            >н</span>
            <span>{{ displayPercent(readyData.metrics.absent.percent) }}</span>
            <span class="attendance-visually-hidden">не был, {{ readyData.metrics.absent.count }}</span>
          </article>
          <article class="attendance-metric attendance-metric--excused">
            <span
              class="attendance-metric__symbol"
              aria-hidden="true"
            >у</span>
            <span>{{ displayPercent(readyData.metrics.excused.percent) }}</span>
            <span class="attendance-visually-hidden">уважительная причина, {{ readyData.metrics.excused.count }}</span>
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
            <span aria-hidden="true">⌁</span>
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
          <div
            class="attendance-day-rail"
            role="tablist"
            aria-label="Дни семестра"
          >
            <button
              v-for="day in readyData.days"
              :key="day.date"
              class="attendance-day-rail__item"
              :class="{ 'attendance-day-rail__item--selected': day.date === selectedDate }"
              type="button"
              role="tab"
              :aria-selected="day.date === selectedDate"
              :aria-label="dateLabel(day.date)"
              @click="emit('select-date', day.date)"
            >
              <span>{{ day.weekday }}</span>
              <strong>{{ day.dayNumber }}</strong>
            </button>
          </div>
          <p
            v-if="selectedLessons.length === 0"
            class="attendance-days__empty"
          >
            Заслуженный отдых
          </p>
          <ol
            v-else
            class="attendance-lesson-list"
            aria-label="Пары выбранного дня"
          >
            <AttendanceLessonRow
              v-for="lesson in selectedLessons"
              :key="lesson.id"
              :lesson="lesson"
              :notification-target="lesson.id === focusedLessonId"
              :actions-open="lesson.id === actionLessonId"
              :terminal="terminal"
              @toggle-actions="emit('toggle-actions', $event)"
              @request="(lesson, option) => emit('open-request', lesson, option)"
            />
          </ol>
        </section>

        <AttendanceSubjectList
          v-else-if="mode === 'subjects'"
          :subjects="readyData.subjects"
          :expanded-subject-id="expandedSubjectId"
          @toggle="emit('toggle-subject', $event)"
        />

        <AttendanceGraph
          v-else
          :points="graphPoints"
          :range="graphRange"
          @change-range="emit('set-graph-range', $event)"
        />
      </template>
    </template>

    <nav
      v-if="showDock && !isInlineRequest"
      class="attendance-dock"
      aria-label="Основная навигация"
    >
      <span
        class="attendance-dock__item"
        aria-label="Сегодня"
      >
        <span class="attendance-dock__icon"><img
          :src="scheduleTab"
          alt=""
          aria-hidden="true"
        ></span>
        <span>Сегодня</span>
      </span>
      <span
        class="attendance-dock__item"
        aria-label="Задания"
      >
        <span class="attendance-dock__icon"><img
          :src="moreTab"
          alt=""
          aria-hidden="true"
        ></span>
        <span>Задания</span>
      </span>
      <span
        class="attendance-dock__item attendance-dock__item--active"
        aria-current="page"
      >
        <span class="attendance-dock__icon"><img
          :src="attendanceTab"
          alt=""
          aria-hidden="true"
        ></span>
        <span>Учёт</span>
      </span>
      <span
        class="attendance-dock__item"
        aria-label="Ещё"
      >
        <span class="attendance-dock__icon"><img
          :src="moreTab"
          alt=""
          aria-hidden="true"
        ></span>
        <span>Ещё</span>
      </span>
      <span
        class="attendance-dock__item"
        aria-label="Профиль"
      >
        <span class="attendance-dock__icon"><img
          :src="profileTab"
          alt=""
          aria-hidden="true"
        ></span>
        <span>Профиль</span>
      </span>
    </nav>
  </main>
</template>
