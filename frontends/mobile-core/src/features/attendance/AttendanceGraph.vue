<script setup lang="ts">
import { computed } from 'vue'
import type { AttendanceGraphPoint, AttendanceGraphRange } from './attendance-view-model'

const props = withDefaults(defineProps<{
  points: readonly AttendanceGraphPoint[]
  range?: AttendanceGraphRange
}>(), {
  range: 'days',
})

const emit = defineEmits<{ 'change-range': [range: AttendanceGraphRange] }>()

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

function metricPercent(point: AttendanceGraphPoint, metric: 'present' | 'presentOrExcused'): number | null {
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

function pointState(point: AttendanceGraphPoint): string {
  if (point.state === 'FUTURE') return 'Будущий период'
  if (point.state === 'NO_DATA') return 'Нет данных'
  return 'Данные доступны'
}
</script>

<template>
  <section
    class="attendance-graph"
    aria-labelledby="attendance-graph-title"
  >
    <h2 id="attendance-graph-title">
      Посещаемость за семестр
    </h2>
    <div
      class="attendance-graph__range"
      role="group"
      aria-label="Период графика"
    >
      <button
        class="attendance-graph__range-button"
        :class="{ 'attendance-graph__range-button--selected': range === 'days' }"
        type="button"
        :aria-pressed="range === 'days'"
        @click="emit('change-range', 'days')"
      >
        Дни
      </button>
      <button
        class="attendance-graph__range-button"
        :class="{ 'attendance-graph__range-button--selected': range === 'weeks' }"
        type="button"
        :aria-pressed="range === 'weeks'"
        @click="emit('change-range', 'weeks')"
      >
        Недели
      </button>
    </div>
    <div
      class="attendance-graph__plot"
      role="img"
      aria-label="График посещаемости за выбранный период"
    >
      <svg
        viewBox="0 0 284 190"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <g class="attendance-graph__areas">
          <path
            v-if="absentArea"
            class="attendance-graph__area attendance-graph__area--absent"
            :d="absentArea"
          />
          <path
            v-if="excusedArea"
            class="attendance-graph__area attendance-graph__area--excused"
            :d="excusedArea"
          />
          <path
            v-if="presentArea"
            class="attendance-graph__area attendance-graph__area--present"
            :d="presentArea"
          />
          <rect
            v-for="item in neutralPoints"
            :key="item.point.id"
            class="attendance-graph__neutral"
            :x="segmentX(item.index)"
            y="0"
            :width="segmentWidth + 0.5"
            :height="plotHeight"
          />
        </g>
        <g class="attendance-graph__labels">
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
    <ul
      class="attendance-graph__legend"
      aria-label="Состояния периодов"
    >
      <li
        v-for="point in plotPoints"
        :key="`state-${point.id}`"
        :data-state="point.state"
      >
        <span aria-hidden="true" />
        <span>{{ point.label }} · {{ pointState(point) }}</span>
      </li>
    </ul>
  </section>
</template>
