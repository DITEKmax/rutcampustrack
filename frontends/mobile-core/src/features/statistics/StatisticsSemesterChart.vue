<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { displayPercent, type StatisticsGraphRange, type StatisticsSeriesPoint } from './statistics-view-model'
import { hasStatisticsMarks, statisticsGeometry, statisticsPointState, statisticsTickCells, STATISTICS_BANDS, STATISTICS_PLOT_WIDTH, STATISTICS_PLOT_HEIGHT } from './statistics-chart-geometry'

const props = withDefaults(defineProps<{
  points: readonly StatisticsSeriesPoint[]
  range?: StatisticsGraphRange
  title?: string
  showRange?: boolean
  hiddenGraph?: boolean
  loading?: boolean
  errorMessage?: string | null
}>(), { range: 'weeks', title: 'Посещаемость за семестр', showRange: true, hiddenGraph: false, loading: false, errorMessage: null })
const emit = defineEmits<{ 'change-range': [range: StatisticsGraphRange]; retry: [] }>()
const geometry = computed(() => statisticsGeometry(props.points))
const selectedId = ref<string | null>(null)
const readoutVisible = computed(() => selectedId.value !== null && props.points.some((point) => point.id === selectedId.value))
watch(() => props.points, () => { if (selectedId.value !== null && !readoutVisible.value) selectedId.value = null })
const selectedIndex = computed(() => {
  const index = props.points.findIndex((point) => point.id === selectedId.value)
  if (index >= 0) return index
  const lastData = props.points.map((point, index) => hasStatisticsMarks(point) ? index : -1).filter((index) => index >= 0).pop() ?? -1
  return Math.max(0, lastData)
})
const selected = computed(() => props.points[selectedIndex.value] ?? null)
const ticks = computed(() => statisticsTickCells(geometry.value.cells))
const detailMetrics = ref<HTMLElement | null>(null)
const outsideLabels = ref(false)
let observer: ResizeObserver | null = null
function measureLabels(): void {
  outsideLabels.value = Array.from(detailMetrics.value?.querySelectorAll<HTMLElement>('.statistics-chart__percentage') ?? []).some((segment) => {
    const label = segment.querySelector<HTMLElement>('.statistics-chart__percentage-label')
    return Boolean(label && label.getBoundingClientRect().width > segment.getBoundingClientRect().width)
  })
}
watch(detailMetrics, (current, previous) => { if (previous) observer?.unobserve(previous); if (current) observer?.observe(current); void nextTick(measureLabels) }, { flush: 'post' })
watch(selected, () => { void nextTick(measureLabels) })
onMounted(() => {
  observer = new ResizeObserver(measureLabels)
  if (detailMetrics.value) observer.observe(detailMetrics.value)
  void document.fonts.ready.then(() => { if (observer) measureLabels() })
})
onBeforeUnmount(() => { observer?.disconnect(); observer = null })
const bands = STATISTICS_BANDS
const labels = { present: 'Был', excused: 'Уважительная причина', absent: 'Не был' }
watch(() => props.range, () => { selectedId.value = null })
function choose(index: number): void { selectedId.value = props.points[Math.max(0, Math.min(props.points.length - 1, index))]?.id ?? null }
function keyboard(event: KeyboardEvent): void {
  if (event.key === 'Escape') { selectedId.value = null; event.preventDefault(); return }
  const offset = event.key === 'ArrowLeft' || event.key === 'ArrowDown' ? -1 : event.key === 'ArrowRight' || event.key === 'ArrowUp' ? 1 : 0
  if (!offset && event.key !== 'Home' && event.key !== 'End') return
  event.preventDefault()
  choose(event.key === 'Home' ? 0 : event.key === 'End' ? props.points.length - 1 : selectedIndex.value + offset)
}
function pointer(event: PointerEvent): void {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const x = (event.clientX - rect.left) / rect.width * STATISTICS_PLOT_WIDTH
  const cell = geometry.value.cells.reduce<(typeof geometry.value.cells)[number] | null>((closest, item) => !closest || Math.abs(item.center - x) < Math.abs(closest.center - x) ? item : closest, null)
  if (cell) choose(cell.index)
}
function dateLabel(point: StatisticsSeriesPoint): string {
  const short = (date: string) => `${date.slice(8, 10)}.${date.slice(5, 7)}`
  return point.dateFrom === point.dateTo ? short(point.dateFrom) : `${short(point.dateFrom)} — ${short(point.dateTo)}`
}
function description(point: StatisticsSeriesPoint): string {
  return [point.label, dateLabel(point), statisticsPointState(point), `Проведено ${point.metrics.held} из ${point.metrics.planned}`, ...bands.map((band) => `${labels[band]}: ${displayPercent(point.metrics[band].percent)}`)].filter(Boolean).join('. ')
}
</script>
<template>
  <section
    class="statistics-chart"
    aria-label="График посещаемости"
  >
    <div class="statistics-chart__header">
      <h2>{{ title }}</h2>
    </div>
    <slot name="filters" />
    <div
      v-if="showRange && !hiddenGraph"
      class="statistics-chart__range"
      role="group"
      aria-label="Период графика"
    >
      <button
        v-for="item in (['days', 'weeks'] as const)"
        :key="item"
        class="statistics-chart__range-button"
        :class="{ 'statistics-chart__range-button--selected': range === item }"
        type="button"
        :aria-pressed="range === item"
        @click="emit('change-range', item)"
      >
        {{ item === 'days' ? 'Дни' : 'Недели' }}
      </button>
    </div>
    <StudentWarningBlock
      v-if="!hiddenGraph && errorMessage"
      title="График не загружен"
      :message="errorMessage"
      severity="error"
      action-label="Повторить"
      @action="emit('retry')"
    />
    <p
      v-else-if="!hiddenGraph && loading"
      class="statistics-chart__loading"
      role="status"
    >
      Загружаем график…
    </p>
    <p
      v-else-if="!hiddenGraph && !points.length"
      class="statistics-chart__empty"
      role="status"
    >
      За семестр пока нет данных.
    </p>
    <template v-else-if="!hiddenGraph">
      <div class="statistics-chart__plot">
        <div
          class="statistics-chart__axis"
          aria-hidden="true"
        >
          <span>100%</span><span>50%</span><span>0%</span>
        </div>
        <div
          class="statistics-chart__canvas"
          role="slider"
          tabindex="0"
          aria-label="Выбери период графика"
          :aria-valuemin="1"
          :aria-valuemax="points.length"
          :aria-valuenow="selectedIndex + 1"
          :aria-valuetext="selected ? description(selected) : ''"
          @keydown="keyboard"
          @pointerdown="pointer"
        >
          <svg
            :viewBox="`0 0 ${STATISTICS_PLOT_WIDTH} ${STATISTICS_PLOT_HEIGHT}`"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <g class="statistics-chart__grid"><path
              v-for="level in [0, 50, 100]"
              :key="level"
              :d="`M0 ${STATISTICS_PLOT_HEIGHT * level / 100}H${STATISTICS_PLOT_WIDTH}`"
            /></g>
            <g
              v-for="band in geometry.bands"
              :key="band.band"
              :class="`statistics-chart__area--${band.band}`"
            ><path
              v-for="(path, index) in band.paths"
              :key="index"
              :d="path"
            /></g>
            <rect
              v-for="cell in geometry.cells.filter((item) => !hasStatisticsMarks(item.point))"
              :key="cell.point.id"
              class="statistics-chart__neutral"
              :data-state="cell.point.state"
              :x="cell.left"
              y="0"
              :width="cell.right - cell.left"
              :height="STATISTICS_PLOT_HEIGHT"
            />
            <rect
              v-for="(gap, index) in geometry.calendarGaps"
              :key="`gap-${index}`"
              class="statistics-chart__calendar-gap"
              :x="gap.left"
              y="0"
              :width="gap.right - gap.left"
              :height="STATISTICS_PLOT_HEIGHT"
            />
            <path
              v-if="readoutVisible && selected"
              class="statistics-chart__selection"
              :d="`M${geometry.cells[selectedIndex]?.center ?? 0} 0V${STATISTICS_PLOT_HEIGHT}`"
            />
          </svg>
        </div>
      </div>
      <div
        class="statistics-chart__ticks"
        aria-hidden="true"
      >
        <span
          v-for="tick in ticks"
          :key="tick.point.id"
          :style="{ left: `clamp(12.5%, ${tick.center / STATISTICS_PLOT_WIDTH * 100}%, 87.5%)` }"
          :title="tick.point.label"
        >{{ tick.point.dateFrom.slice(8, 10) }}.{{ tick.point.dateFrom.slice(5, 7) }}</span>
      </div>
      <section
        v-if="readoutVisible && selected"
        class="statistics-chart__detail"
        aria-label="Выбранный период"
        aria-live="polite"
      >
        <h3>{{ dateLabel(selected) }}</h3>
        <p v-if="statisticsPointState(selected)">
          {{ statisticsPointState(selected) }}
        </p>
        <p>Проведено {{ selected.metrics.held }} из {{ selected.metrics.planned }}</p>
        <template v-if="hasStatisticsMarks(selected)">
          <div
            ref="detailMetrics"
            class="statistics-chart__percentages"
            :class="{ 'statistics-chart__percentages--outside': outsideLabels }"
            role="img"
            :aria-label="description(selected)"
          >
            <span
              v-for="band in bands"
              :key="band"
              class="statistics-chart__percentage"
              :class="`statistics-chart__percentage--${band}`"
              :style="{ inlineSize: `${selected.metrics[band].percent ?? 0}%` }"
              aria-hidden="true"
            ><span class="statistics-chart__percentage-label">{{ displayPercent(selected.metrics[band].percent) }}</span></span>
          </div>
          <div
            v-if="outsideLabels"
            class="statistics-chart__percentage-labels"
            aria-hidden="true"
          >
            <span
              v-for="band in bands"
              :key="band"
              :class="`statistics-chart__value--${band}`"
            >{{ displayPercent(selected.metrics[band].percent) }}</span>
          </div>
        </template>
      </section>
    </template>
  </section>
</template>
