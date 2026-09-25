<script setup lang="ts">
import { computed } from 'vue'
import type { HeadmanStatsTrendMetric, HeadmanStatsTrendPoint } from './headman-stats-client'
import './headman-stats-trend-chart.pcss'

const props = defineProps<{
  points: readonly HeadmanStatsTrendPoint[]
  presentLabel?: string
  presentOrExcusedLabel?: string
}>()

const width = 720
const height = 280
const left = 52
const right = 20
const top = 18
const bottom = 30
const plotWidth = width - left - right
const plotHeight = height - top - bottom
const ticks = [0, 50, 100]
const hasChartData = computed(() => props.points.some((point) =>
  point.present.percent !== null || point.presentOrExcused.percent !== null))

function x(index: number): number {
  if (props.points.length <= 1) return left + plotWidth / 2
  return left + (index / (props.points.length - 1)) * plotWidth
}

function y(percent: number): number {
  return top + ((100 - percent) / 100) * plotHeight
}

function pathFor(metric: 'present' | 'presentOrExcused'): string {
  const paths: string[] = []
  let segment: string[] = []
  props.points.forEach((point, index) => {
    const percent = point[metric].percent
    if (percent === null) {
      if (segment.length) paths.push(segment.join(' '))
      segment = []
      return
    }
    segment.push(`${segment.length ? 'L' : 'M'} ${x(index).toFixed(2)} ${y(percent).toFixed(2)}`)
  })
  if (segment.length) paths.push(segment.join(' '))
  return paths.join(' ')
}

function metricText(metric: HeadmanStatsTrendMetric): string {
  return metric.percent === null
    ? 'Нет данных'
    : `${metric.percent.toFixed(1)}% (${metric.numerator} из ${metric.denominator})`
}

function pointAriaLabel(point: HeadmanStatsTrendPoint, label: string, metric: HeadmanStatsTrendMetric): string {
  return `${point.label}, ${label}: ${metricText(metric)}`
}
</script>

<template>
  <div class="headman-stats-trend">
    <div
      class="headman-stats-trend__legend"
      aria-label="Обозначения линий"
    >
      <span class="headman-stats-trend__legend-item headman-stats-trend__legend-item--present">
        <span aria-hidden="true" />{{ presentLabel ?? 'Присутствовал' }}
      </span>
      <span class="headman-stats-trend__legend-item headman-stats-trend__legend-item--excused">
        <span aria-hidden="true" />{{ presentOrExcusedLabel ?? 'Присутствовал или отсутствовал по уважительной причине' }}
      </span>
    </div>
    <p
      v-if="!hasChartData"
      class="headman-stats-trend__empty"
      role="status"
    >
      За выбранный период нет данных для графика.
    </p>
    <div
      class="headman-stats-trend__plot-wrap"
      role="group"
      aria-label="График посещаемости от 0 до 100 процентов. Точки доступны клавиатурой; подробные значения также приведены в таблице ниже."
    >
      <svg
        class="headman-stats-trend__plot"
        :viewBox="`0 0 ${width} ${height}`"
        preserveAspectRatio="none"
        aria-hidden="false"
      >
        <g
          class="headman-stats-trend__grid"
          aria-hidden="true"
        >
          <template
            v-for="tick in ticks"
            :key="tick"
          >
            <line
              :x1="left"
              :x2="width - right"
              :y1="y(tick)"
              :y2="y(tick)"
            />
            <text
              :x="left - 8"
              :y="y(tick) + 4"
            >{{ tick }}%</text>
          </template>
        </g>
        <path
          class="headman-stats-trend__line headman-stats-trend__line--present"
          :d="pathFor('present')"
        />
        <path
          class="headman-stats-trend__line headman-stats-trend__line--excused"
          :d="pathFor('presentOrExcused')"
        />
        <g
          v-for="(point, index) in points"
          :key="point.key"
        >
          <circle
            v-if="point.present.percent !== null"
            class="headman-stats-trend__point headman-stats-trend__point--present"
            :cx="x(index)"
            :cy="y(point.present.percent)"
            r="5"
            tabindex="0"
            role="img"
            :aria-label="pointAriaLabel(point, presentLabel ?? 'Присутствовал', point.present)"
          >
            <title>{{ pointAriaLabel(point, presentLabel ?? 'Присутствовал', point.present) }}</title>
          </circle>
          <rect
            v-if="point.presentOrExcused.percent !== null"
            class="headman-stats-trend__point headman-stats-trend__point--excused"
            :x="x(index) - 4.5"
            :y="y(point.presentOrExcused.percent) - 4.5"
            width="9"
            height="9"
            :transform="`rotate(45 ${x(index)} ${y(point.presentOrExcused.percent)})`"
            tabindex="0"
            role="img"
            :aria-label="pointAriaLabel(point, presentOrExcusedLabel ?? 'Присутствовал или отсутствовал по уважительной причине', point.presentOrExcused)"
          >
            <title>{{ pointAriaLabel(point, presentOrExcusedLabel ?? 'Присутствовал или отсутствовал по уважительной причине', point.presentOrExcused) }}</title>
          </rect>
        </g>
      </svg>
    </div>
    <div
      class="headman-stats-trend__table-wrap"
      tabindex="0"
      aria-label="Таблица значений динамики, прокручивается по горизонтали"
    >
      <table>
        <caption>Динамика посещаемости</caption>
        <thead>
          <tr>
            <th scope="col">
              Период
            </th>
            <th scope="col">
              {{ presentLabel ?? 'Присутствовал' }}
            </th>
            <th scope="col">
              {{ presentOrExcusedLabel ?? 'Присутствовал или отсутствовал по уважительной причине' }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="point in points"
            :key="point.key"
          >
            <th scope="row">
              {{ point.label }}
            </th>
            <td>{{ metricText(point.present) }}</td>
            <td>{{ metricText(point.presentOrExcused) }}</td>
          </tr>
          <tr v-if="points.length === 0">
            <td colspan="3">
              Нет данных за выбранный период.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
