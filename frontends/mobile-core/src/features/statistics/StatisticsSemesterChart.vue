<script setup lang="ts">
import { computed } from 'vue'
import {
  displayPercent,
  type StatisticsGraphRange,
  type StatisticsSeriesPoint,
} from './statistics-view-model'

const props = withDefaults(defineProps<{
  points: readonly StatisticsSeriesPoint[]
  range?: StatisticsGraphRange
  title?: string
}>(), {
  range: 'weeks',
  title: 'Посещаемость за семестр',
})

const emit = defineEmits<{ 'change-range': [range: StatisticsGraphRange] }>()

const plotWidth = 284
const plotHeight = 170
const plotPoints = computed(() => props.points)
const neutralPoints = computed(() => plotPoints.value
  .map((point, index) => ({ point, index }))
  .filter(({ point }) => point.state !== 'DATA'))
const segmentWidth = computed(() => plotPoints.value.length > 0 ? plotWidth / plotPoints.value.length : plotWidth)

function centerX(index: number): number {
  return segmentWidth.value * (index + 0.5)
}

function segmentX(index: number): number {
  return segmentWidth.value * index
}

function y(percent: number): number {
  const bounded = Math.min(100, Math.max(0, percent))
  return plotHeight - (bounded / 100) * plotHeight
}

function metricPercent(point: StatisticsSeriesPoint, metric: 'present' | 'presentOrExcused'): number | null {
  if (point.state !== 'DATA') return null
  return point.metrics[metric].percent
}

function bandPath(
  upper: 'present' | 'presentOrExcused' | 'top',
  lower: 'present' | 'presentOrExcused' | 'bottom',
): string {
  const indexes = plotPoints.value
    .map((point, index) => ({ point, index }))
    .filter(({ point }) => {
      const upperValue = upper === 'top' ? 100 : metricPercent(point, upper)
      const lowerValue = lower === 'bottom' ? 0 : metricPercent(point, lower)
      return upperValue !== null && lowerValue !== null
    })
  if (indexes.length === 0) return ''

  const topLine = indexes.map(({ point, index }) => {
    const value = upper === 'top' ? 100 : metricPercent(point, upper) ?? 0
    return `${centerX(index).toFixed(2)},${y(value).toFixed(2)}`
  })
  const bottomLine = indexes.slice().reverse().map(({ point, index }) => {
    const value = lower === 'bottom' ? 0 : metricPercent(point, lower) ?? 0
    return `${centerX(index).toFixed(2)},${y(value).toFixed(2)}`
  })
  return `M ${topLine.join(' L ')} L ${bottomLine.join(' L ')} Z`
}

const presentArea = computed(() => bandPath('present', 'bottom'))
const excusedArea = computed(() => bandPath('presentOrExcused', 'present'))
const absentArea = computed(() => bandPath('top', 'presentOrExcused'))

function pointState(point: StatisticsSeriesPoint): string {
  if (point.state === 'FUTURE') return 'Будущий период'
  if (point.state === 'NO_DATA') return 'Нет данных'
  return 'Данные доступны'
}

function pointDateRange(point: StatisticsSeriesPoint): string {
  return point.dateFrom === point.dateTo
    ? point.dateFrom
    : `${point.dateFrom} — ${point.dateTo}`
}
</script>

<template>
  <section
    class="statistics-chart"
    aria-labelledby="statistics-chart-title"
  >
    <div class="statistics-chart__header">
      <h2 id="statistics-chart-title">
        {{ title }}
      </h2>
      <div
        class="statistics-chart__range"
        role="group"
        aria-label="Период графика"
      >
        <button
          class="statistics-chart__range-button"
          :class="{ 'statistics-chart__range-button--selected': range === 'days' }"
          type="button"
          :aria-pressed="range === 'days'"
          @click="emit('change-range', 'days')"
        >
          Дни
        </button>
        <button
          class="statistics-chart__range-button"
          :class="{ 'statistics-chart__range-button--selected': range === 'weeks' }"
          type="button"
          :aria-pressed="range === 'weeks'"
          @click="emit('change-range', 'weeks')"
        >
          Недели
        </button>
      </div>
    </div>
    <div
      class="statistics-chart__plot"
      role="img"
      aria-label="График посещаемости за выбранный период"
    >
      <svg
        viewBox="0 0 284 190"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <g class="statistics-chart__areas">
          <path
            v-if="absentArea"
            class="statistics-chart__area statistics-chart__area--absent"
            :d="absentArea"
          />
          <path
            v-if="excusedArea"
            class="statistics-chart__area statistics-chart__area--excused"
            :d="excusedArea"
          />
          <path
            v-if="presentArea"
            class="statistics-chart__area statistics-chart__area--present"
            :d="presentArea"
          />
          <rect
            v-for="item in neutralPoints"
            :key="item.point.id"
            class="statistics-chart__neutral"
            :x="segmentX(item.index)"
            y="0"
            :width="segmentWidth + 0.5"
            :height="plotHeight"
          />
        </g>
        <g class="statistics-chart__labels">
          <text
            x="0"
            y="10"
          >100%</text>
          <text
            x="0"
            :y="plotHeight"
          >0%</text>
          <text
            v-for="(point, index) in plotPoints"
            :key="`label-${point.id}`"
            :x="centerX(index)"
            y="187"
            text-anchor="middle"
          >{{ point.label }}</text>
        </g>
      </svg>
    </div>
    <ol
      class="statistics-chart__legend statistics-visually-hidden"
      aria-label="Точки графика"
    >
      <li
        v-for="point in plotPoints"
        :key="`state-${point.id}`"
        :data-state="point.state"
      >
        Период {{ point.label }}, {{ pointDateRange(point) }}:
        {{ pointState(point) }}.
        Присутствие {{ displayPercent(point.metrics.present.percent) }},
        {{ point.metrics.present.count }} пар;
        присутствие или уважительная причина
        {{ displayPercent(point.metrics.presentOrExcused.percent) }},
        {{ point.metrics.presentOrExcused.count }} пар;
        уважительная причина {{ displayPercent(point.metrics.excused.percent) }},
        {{ point.metrics.excused.count }} пар;
        отсутствие {{ displayPercent(point.metrics.absent.percent) }},
        {{ point.metrics.absent.count }} пар.
        Закрыто {{ point.metrics.held }} из {{ point.metrics.planned }} пар.
      </li>
    </ol>
  </section>
</template>
