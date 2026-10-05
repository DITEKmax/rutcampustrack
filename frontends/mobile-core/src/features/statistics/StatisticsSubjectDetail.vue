<script setup lang="ts">
import { computed } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StatisticsSemesterChart from './StatisticsSemesterChart.vue'
import StatisticsTypeCard from './StatisticsTypeCard.vue'
import {
  seriesForRange,
  statisticsTypeLabel,
  STATISTICS_TYPE_ORDER,
  toggleTypeSelection,
  type StatisticsGraphRange,
  type StatisticsLessonType,
  type StatisticsReadState,
  type StatisticsSubjectDetailData,
  type StatisticsTheme,
} from './statistics-view-model'

const props = withDefaults(defineProps<{
  state: StatisticsReadState<StatisticsSubjectDetailData>
  theme?: StatisticsTheme
  range?: StatisticsGraphRange
  semesterStartsOn?: string
  semesterEndsOn?: string
  terminal?: boolean
}>(), {
  semesterStartsOn: '',
  semesterEndsOn: '',
  theme: 'dark',
  range: 'weeks',
  terminal: false,
})

const emit = defineEmits<{
  back: []
  retry: []
  'set-types': [types: readonly StatisticsLessonType[]]
  'set-range': [range: StatisticsGraphRange]
}>()

const data = computed(() => props.state.status === 'ready' ? props.state.data : null)
const availableTypes = computed(() => data.value
  ? STATISTICS_TYPE_ORDER.filter((type) => data.value?.availableTypes.includes(type))
  : [])
const orderedCards = computed(() => {
  if (!data.value) return []
  return STATISTICS_TYPE_ORDER
    .map((type) => data.value?.typeCards.find((card) => card.type === type))
    .filter((card): card is NonNullable<typeof card> => Boolean(card))
})
const chartPoints = computed(() => data.value ? seriesForRange(data.value, props.range) : [])

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

function isSelected(type: StatisticsLessonType): boolean {
  return data.value?.selectedTypes.includes(type) ?? false
}

function toggleType(type: StatisticsLessonType): void {
  if (!data.value) return
  emit('set-types', toggleTypeSelection(data.value.selectedTypes, type))
}
</script>

<template>
  <main
    class="statistics-detail"
    :data-theme="theme"
    :aria-busy="state.status === 'loading'"
    aria-labelledby="statistics-detail-title"
  >
    <header class="statistics-detail__header">
      <button
        class="statistics-back-button"
        type="button"
        aria-label="Назад к статистике"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="statistics-detail-title">
        {{ data?.name ?? 'Предмет' }}
      </h1>
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
      <StatisticsSemesterChart
        :hidden-graph="data.selectedTypes.length === 0"
        :points="chartPoints"
        :loading="data.graphStatus === 'loading'"
        :error-message="data.graphStatus === 'error' ? data.graphMessage ?? 'Попробуй ещё раз.' : null"
        :range="range"
        :semester-starts-on="semesterStartsOn"
        :semester-ends-on="semesterEndsOn"
        title="Посещаемость за семестр"
        @change-range="emit('set-range', $event)"
        @retry="emit('retry')"
      >
        <template #filters>
          <section
            v-if="availableTypes.length > 1"
            class="statistics-type-filter"
            aria-labelledby="statistics-type-filter-title"
          >
            <h2
              id="statistics-type-filter-title"
              class="statistics-visually-hidden"
            >
              Типы пар для графика
            </h2>
            <div
              class="statistics-type-filter__options"
              role="group"
              aria-label="Типы пар"
            >
              <button
                v-for="type in availableTypes"
                :key="type"
                class="statistics-type-filter__button"
                :class="{ 'statistics-type-filter__button--selected': isSelected(type) }"
                type="button"
                :aria-pressed="isSelected(type)"
                @click="toggleType(type)"
              >
                {{ statisticsTypeLabel(type) }}
              </button>
            </div>
          </section>
        </template>
      </StatisticsSemesterChart>

      <section
        class="statistics-type-stack"
        aria-labelledby="statistics-type-stack-title"
      >
        <h2
          id="statistics-type-stack-title"
          class="statistics-visually-hidden"
        >
          По типам пар
        </h2>
        <StatisticsTypeCard
          v-for="card in orderedCards"
          :key="card.type"
          :card="card"
        />
      </section>
    </template>
  </main>
</template>