<script setup lang="ts">
import { computed } from 'vue'
import attendanceTab from '../../assets/attendance-tab.svg'
import chevronDown from '../../assets/chevron-down.svg'
import moreTab from '../../assets/more-tab.svg'
import profileTab from '../../assets/profile-tab.svg'
import scheduleTab from '../../assets/schedule-tab.svg'
import StatisticsSemesterChart from './StatisticsSemesterChart.vue'
import StatisticsSubjectDetail from './StatisticsSubjectDetail.vue'
import {
  displayPercent,
  type StatisticsGraphRange,
  type StatisticsLessonType,
  type StatisticsOverviewData,
  type StatisticsReadState,
  type StatisticsSubjectDetailData,
  type StatisticsTheme,
} from './statistics-view-model'
import './statistics-screen.pcss'

const props = withDefaults(defineProps<{
  state: StatisticsReadState<StatisticsOverviewData>
  selectedSubjectId?: string | null
  detailState?: StatisticsReadState<StatisticsSubjectDetailData> | null
  range?: StatisticsGraphRange
  theme?: StatisticsTheme
  terminal?: boolean
  showDock?: boolean
}>(), {
  selectedSubjectId: null,
  detailState: null,
  range: 'weeks',
  theme: 'dark',
  terminal: false,
  showDock: false,
})

const emit = defineEmits<{
  'open-subject': [subjectId: string]
  'set-range': [range: StatisticsGraphRange]
  'set-types': [types: readonly StatisticsLessonType[]]
  back: []
  retry: []
  'detail-retry': []
}>()

const data = computed(() => props.state.status === 'ready' ? props.state.data : null)
const chartPoints = computed(() => data.value?.semesterSeries ?? [])
const detailState = computed<StatisticsReadState<StatisticsSubjectDetailData>>(() => props.detailState ?? { status: 'loading' })

function stateTitle(): string {
  switch (props.state.status) {
    case 'loading': return 'Загружаем статистику…'
    case 'empty': return 'За этот период данных пока нет'
    case 'offline': return 'Статистика недоступна офлайн'
    case 'forbidden': return 'Статистика недоступна'
    case 'error': return 'Не удалось получить статистику'
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

function rankLabel(rank: StatisticsOverviewData['ownRank']): string {
  return rank.available && rank.position !== null
    ? `${rank.position} из ${rank.participantCount}`
    : 'Нет данных'
}
</script>

<template>
  <StatisticsSubjectDetail
    v-if="selectedSubjectId !== null"
    :state="detailState"
    :theme="theme"
    :range="range"
    :terminal="terminal"
    @back="emit('back')"
    @retry="emit('detail-retry')"
    @set-types="emit('set-types', $event)"
    @set-range="emit('set-range', $event)"
  />

  <main
    v-else
    class="statistics-screen"
    :data-theme="theme"
    :aria-busy="state.status === 'loading'"
    aria-labelledby="statistics-title"
  >
    <header class="statistics-screen__header">
      <h1 id="statistics-title">
        Статистика
      </h1>
    </header>

    <section
      v-if="state.status !== 'ready'"
      class="statistics-state"
      :class="{ 'statistics-state--error': state.status === 'error' || state.status === 'forbidden' }"
      :data-state="state.status"
      :role="state.status === 'error' || state.status === 'forbidden' ? 'alert' : 'status'"
    >
      <span
        v-if="state.status === 'loading'"
        class="statistics-state__spinner"
        aria-hidden="true"
      />
      <h2>{{ stateTitle() }}</h2>
      <p v-if="stateMessage()">
        {{ stateMessage() }}
      </p>
      <button
        v-if="state.status === 'error' && state.retryable"
        class="statistics-state__retry"
        type="button"
        @click="emit('retry')"
      >
        Повторить
      </button>
    </section>

    <template v-else-if="data">
      <section
        class="statistics-rank"
        aria-label="Место в группе"
      >
        <p>Твоё место в группе</p>
        <strong>{{ rankLabel(data.ownRank) }}</strong>
      </section>

      <section
        class="statistics-metrics"
        aria-label="Метрики посещаемости"
      >
        <article class="statistics-metric statistics-metric--present">
          <span
            class="statistics-metric__symbol"
            aria-hidden="true"
          >+</span>
          <span>{{ displayPercent(data.metrics.present.percent) }}</span>
          <span class="statistics-visually-hidden">был, {{ data.metrics.present.count }}</span>
        </article>
        <article class="statistics-metric statistics-metric--absent">
          <span
            class="statistics-metric__symbol"
            aria-hidden="true"
          >н</span>
          <span>{{ displayPercent(data.metrics.absent.percent) }}</span>
          <span class="statistics-visually-hidden">не был, {{ data.metrics.absent.count }}</span>
        </article>
        <article class="statistics-metric statistics-metric--excused">
          <span
            class="statistics-metric__symbol"
            aria-hidden="true"
          >у</span>
          <span>{{ displayPercent(data.metrics.excused.percent) }}</span>
          <span class="statistics-visually-hidden">уважительная причина, {{ data.metrics.excused.count }}</span>
        </article>
      </section>

      <StatisticsSemesterChart
        :points="chartPoints"
        :range="range"
        title="Посещаемость за семестр"
        @change-range="emit('set-range', $event)"
      />

      <section
        class="statistics-subjects"
        aria-labelledby="statistics-subjects-title"
      >
        <h2 id="statistics-subjects-title">
          По предметам
        </h2>
        <p
          v-if="data.subjects.length === 0"
          class="statistics-subjects__empty"
        >
          Предметы появятся после первой пары.
        </p>
        <ul v-else>
          <li
            v-for="subject in data.subjects"
            :key="subject.id"
          >
            <button
              class="statistics-subject-summary"
              type="button"
              @click="emit('open-subject', subject.id)"
            >
              <span class="statistics-subject-summary__name">{{ subject.name }}</span>
              <span
                class="statistics-subject-summary__metrics"
                aria-label="Метрики посещаемости"
              >
                <span class="statistics-subject-summary__metric statistics-subject-summary__metric--present">
                  <i aria-hidden="true" />{{ displayPercent(subject.metrics.present.percent) }}
                  <span class="statistics-visually-hidden"> присутствовал, {{ subject.metrics.present.count }}</span>
                </span>
                <span class="statistics-subject-summary__metric statistics-subject-summary__metric--excused">
                  <i aria-hidden="true" />{{ displayPercent(subject.metrics.excused.percent) }}
                  <span class="statistics-visually-hidden"> уважительная причина, {{ subject.metrics.excused.count }}</span>
                </span>
                <span class="statistics-subject-summary__metric statistics-subject-summary__metric--absent">
                  <i aria-hidden="true" />{{ displayPercent(subject.metrics.absent.percent) }}
                  <span class="statistics-visually-hidden"> отсутствовал, {{ subject.metrics.absent.count }}</span>
                </span>
              </span>
              <span
                class="statistics-subject-summary__disclosure"
                aria-hidden="true"
              >
                <img
                  :src="chevronDown"
                  alt=""
                >
              </span>
            </button>
          </li>
        </ul>
      </section>
    </template>

    <nav
      v-if="showDock"
      class="statistics-dock"
      aria-label="Основная навигация"
    >
      <span class="statistics-dock__item"><span class="statistics-dock__icon"><img
        :src="scheduleTab"
        alt=""
        aria-hidden="true"
      ></span><span>Сегодня</span></span>
      <span class="statistics-dock__item"><span class="statistics-dock__icon"><img
        :src="moreTab"
        alt=""
        aria-hidden="true"
      ></span><span>Задания</span></span>
      <span class="statistics-dock__item"><span class="statistics-dock__icon"><img
        :src="attendanceTab"
        alt=""
        aria-hidden="true"
      ></span><span>Учёт</span></span>
      <span
        class="statistics-dock__item statistics-dock__item--active"
        aria-current="page"
      ><span class="statistics-dock__icon"><img
        :src="moreTab"
        alt=""
        aria-hidden="true"
      ></span><span>Ещё</span></span>
      <span class="statistics-dock__item"><span class="statistics-dock__icon"><img
        :src="profileTab"
        alt=""
        aria-hidden="true"
      ></span><span>Профиль</span></span>
    </nav>
  </main>
</template>
