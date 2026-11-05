<script setup lang="ts">
import { computed } from 'vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import MobileShell from '../../shared/components/MobileShell.vue';
import { createStudentNavigationItems } from '../../shared/mobile-navigation-items';
import StatisticsSemesterChart from './StatisticsSemesterChart.vue';
import StatisticsSubjectDetail from './StatisticsSubjectDetail.vue';
import {
  displayPercent,
  statisticsRankingName,
  type StatisticsGraphRange,
  type StatisticsLessonType,
  type StatisticsOverviewData,
  type StatisticsReadState,
  type StatisticsRankingData,
  type StatisticsSubjectDetailData,
  type StatisticsTheme,
} from './statistics-view-model';
import './statistics-screen.pcss';

const props = withDefaults(
  defineProps<{
    state: StatisticsReadState<StatisticsOverviewData>;
    selectedSubjectId?: string | null;
    detailState?: StatisticsReadState<StatisticsSubjectDetailData> | null;
    range?: StatisticsGraphRange;
    semesterStartsOn?: string;
    semesterEndsOn?: string;
    theme?: StatisticsTheme | undefined;
    terminal?: boolean;
    showDock?: boolean;
    rankingVisible?: boolean;
    rankingState?: StatisticsReadState<StatisticsRankingData> | null;
  }>(),
  {
    theme: undefined,
    semesterStartsOn: '',
    semesterEndsOn: '',
    selectedSubjectId: null,
    detailState: null,
    range: 'weeks',
    terminal: false,
    showDock: false,
    rankingVisible: false,
    rankingState: null,
  },
);

const emit = defineEmits<{
  'open-subject': [subjectId: string];
  'set-range': [range: StatisticsGraphRange];
  'set-types': [types: readonly StatisticsLessonType[]];
  back: [];
  retry: [];
  'detail-retry': [];
  'open-ranking': [];
  'ranking-page': [page: number];
  'ranking-retry': [];
}>();

const navItems = createStudentNavigationItems({
  homeworkEnabled: true,
  attendanceEnabled: true,
  moreEnabled: true,
  profileEnabled: true,
});
const data = computed(() => (props.state.status === 'ready' ? props.state.data : null));
const chartPoints = computed(() => data.value?.semesterSeries ?? []);
const detailState = computed<StatisticsReadState<StatisticsSubjectDetailData>>(
  () => props.detailState ?? { status: 'loading' },
);

function stateTitle(): string {
  switch (props.state.status) {
    case 'loading':
      return 'Загружаем статистику…';
    case 'empty':
      return 'За этот период данных пока нет';
    case 'offline':
      return 'Статистика недоступна офлайн';
    case 'forbidden':
      return 'Статистика недоступна';
    case 'error':
      return 'Не удалось получить статистику';
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
    default:
      return '';
  }
}

function rankLabel(rank: StatisticsOverviewData['ownRank']): string {
  return rank.available && rank.position !== null
    ? `${rank.position} из ${rank.participantCount}`
    : 'Нет данных';
}
</script>

<template>
  <StatisticsSubjectDetail
    v-if="selectedSubjectId !== null"
    :state="detailState"
    :theme="theme"
    :range="range"
    :semester-starts-on="semesterStartsOn"
    :semester-ends-on="semesterEndsOn"
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
      <h1 id="statistics-title"> Статистика </h1>
    </header>

    <section
      v-if="state.status === 'loading'"
      class="statistics-state"
      role="status"
    >
      <span
        class="statistics-state__spinner"
        aria-hidden="true"
      />
      <h2>{{ stateTitle() }}</h2>
    </section>
    <StudentWarningBlock
      v-else-if="state.status !== 'ready'"
      :title="stateTitle()"
      :message="stateMessage()"
      :severity="state.status === 'error' || state.status === 'forbidden' ? 'error' : 'warning'"
      :action-label="state.status === 'error' && state.retryable ? 'Повторить' : ''"
      @action="emit('retry')"
    />

    <template v-else-if="data">
      <section
        class="statistics-rank"
        aria-label="Место в группе"
      >
        <button
          class="statistics-rank__toggle"
          type="button"
          :aria-expanded="rankingVisible"
          aria-label="Рейтинг группы"
          aria-controls="statistics-group-ranking"
          @click="emit('open-ranking')"
        >
          <span class="statistics-rank__context">Твоё место в группе</span>
          <strong>{{ rankLabel(data.ownRank) }}</strong>
          <MobileIcon name="chevron-down" />
        </button>
        <section
          v-if="rankingVisible"
          id="statistics-group-ranking"
          class="statistics-ranking"
          aria-label="Рейтинг группы"
        >
          <p
            v-if="!rankingState || rankingState.status === 'loading'"
            role="status"
          >
            Загружаем рейтинг…
          </p>
          <template v-else-if="rankingState.status === 'ready'">
            <ol
              class="statistics-ranking__rows"
              aria-label="Рейтинг группы"
            >
              <li
                v-for="row in rankingState.data.rows"
                :key="row.id"
                class="statistics-ranking__row"
                :data-self="row.isSelf"
              >
                <span>{{ row.position ?? '—' }}</span>
                <span class="statistics-ranking__name">
                  <span aria-hidden="true">{{ statisticsRankingName(row.name) }}</span>
                  <span class="statistics-visually-hidden">{{ row.name }}</span>
                </span>
                <span>{{ displayPercent(row.percentage) }}</span>
              </li>
            </ol>
          </template>
          <StudentWarningBlock
            v-else-if="rankingState.status === 'error' || rankingState.status === 'forbidden'"
            title="Рейтинг недоступен"
            severity="error"
            :message="rankingState.status === 'error' ? rankingState.message : rankingState.reason"
            :action-label="
              rankingState.status === 'error' && rankingState.retryable ? 'Повторить' : ''
            "
            @action="emit('ranking-retry')"
          />
          <p
            v-else
            role="status"
          >
            {{
              rankingState.status === 'offline'
                ? 'Рейтинг недоступен офлайн'
                : 'В рейтинге пока нет данных'
            }}
          </p>
        </section>
      </section>

      <section
        class="statistics-metrics"
        aria-label="Метрики посещаемости"
      >
        <article
          v-for="metric in [
            { key: 'present', label: 'Был', value: data.metrics.present },
            {
              key: 'presentOrExcused',
              label: 'С учётом уважительных',
              value: data.metrics.presentOrExcused,
            },
            { key: 'absent', label: 'Не был', value: data.metrics.absent },
            { key: 'excused', label: 'Уважительная причина', value: data.metrics.excused },
          ]"
          :key="metric.key"
          class="statistics-metric"
          :class="`statistics-metric--${metric.key}`"
        >
          <span class="statistics-metric__label">{{ metric.label }}</span>
          <strong class="statistics-metric__value">{{
            displayPercent(metric.value.percent)
          }}</strong>
          <span class="statistics-metric__count"
            >{{ metric.value.count }} из {{ data.metrics.held }} пар</span
          >
        </article>
      </section>

      <StatisticsSemesterChart
        :points="chartPoints"
        :range="'weeks'"
        :semester-starts-on="semesterStartsOn"
        :semester-ends-on="semesterEndsOn"
        :show-range="false"
        title="Посещаемость за семестр"
        @change-range="emit('set-range', $event)"
      />

      <section
        class="statistics-subjects"
        aria-labelledby="statistics-subjects-title"
      >
        <h2 id="statistics-subjects-title"> По предметам </h2>
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
              <span class="statistics-subject-summary__metrics">
                <span
                  class="statistics-subject-summary__metric statistics-subject-summary__metric--present"
                >
                  <i aria-hidden="true" />
                  <span class="statistics-visually-hidden">Был</span>
                  {{ displayPercent(subject.metrics.present.percent) }}
                </span>
                <span
                  class="statistics-subject-summary__metric statistics-subject-summary__metric--excused"
                >
                  <i aria-hidden="true" />
                  <span class="statistics-visually-hidden">Уважительная причина</span>
                  {{ displayPercent(subject.metrics.excused.percent) }}
                </span>
                <span
                  class="statistics-subject-summary__metric statistics-subject-summary__metric--absent"
                >
                  <i aria-hidden="true" />
                  <span class="statistics-visually-hidden">Не был</span>
                  {{ displayPercent(subject.metrics.absent.percent) }}
                </span>
              </span>
              <span
                class="statistics-subject-summary__disclosure"
                aria-hidden="true"
              >
                <MobileIcon name="chevron-down" />
              </span>
            </button>
          </li>
        </ul>
      </section>
    </template>

    <MobileShell
      v-if="showDock"
      :nav-items="navItems"
      active-id="more"
    />
  </main>
</template>
