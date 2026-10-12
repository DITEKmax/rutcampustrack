<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import {
  headmanStatsDate,
  headmanStatsTrendAxis,
  headmanStatsTrendPosition,
} from './headman-stats-trend-axis';
import type {
  HeadmanStatsTrendMode,
  HeadmanStatsTrendMetric,
  HeadmanStatsTrendPoint,
} from './headman-stats-client';
import './headman-stats-trend-chart.pcss';

const props = defineProps<{
  points: readonly HeadmanStatsTrendPoint[];
  mode?: HeadmanStatsTrendMode;
  from?: string | null | undefined;
  to?: string | null | undefined;
  weekStart?: string | undefined;
  presentLabel?: string;
  presentOrExcusedLabel?: string;
}>();

const selectedPoint = ref<HeadmanStatsTrendPoint | null>(null);
watch(
  () => props.points,
  () => {
    selectedPoint.value = null;
  },
);
const plot = ref<SVGSVGElement | null>(null);
const probe = ref<SVGTextElement | null>(null);
const width = ref(360);
const labelWidth = ref(50);
let resizeObserver: ResizeObserver | null = null;
function measure(): void {
  if (!plot.value || !probe.value) return;
  const measured = plot.value.getBoundingClientRect().width;
  if (measured > 0) width.value = measured;
  const style = getComputedStyle(probe.value);
  const context = document.createElement('canvas').getContext('2d');
  if (!context) return;
  context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
  const formatter = new Intl.DateTimeFormat(
    'ru-RU',
    props.mode === 'WEEK'
      ? { day: 'numeric', timeZone: 'UTC' }
      : { day: 'numeric', month: 'short', timeZone: 'UTC' },
  );
  const anchor =
    headmanStatsDate(props.mode === 'WEEK' ? props.weekStart : props.from) ??
    headmanStatsDate(props.points[0]?.from) ??
    Date.now();
  const year = new Date(anchor).getUTCFullYear();
  const possibleWidths: number[] = [];
  for (let month = 0; month < 12; month += 1)
    for (let date = 1; date <= 31; date += 1) {
      const candidate = new Date(Date.UTC(year, month, date));
      if (candidate.getUTCMonth() === month)
        possibleWidths.push(context.measureText(formatter.format(candidate)).width);
    }
  labelWidth.value = Math.max(...possibleWidths);
}
onMounted(() => {
  measure();
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(measure);
    if (plot.value) resizeObserver.observe(plot.value);
  }
  void document.fonts?.ready.then(measure);
});
watch(
  () => props.mode,
  () => {
    measure();
  },
  { flush: 'post' },
);
onBeforeUnmount(() => resizeObserver?.disconnect());
const height = 210;
const left = 52;
const right = 12;
const top = 18;
const bottom = 30;
const plotWidth = computed(() => Math.max(1, width.value - left - right));
const plotHeight = height - top - bottom;
const ticks = [0, 50, 100];
const hasChartData = computed(() =>
  props.points.some(
    (point) => point.present.percent !== null || point.presentOrExcused.percent !== null,
  ),
);

const axis = computed(() =>
  headmanStatsTrendAxis({
    mode: props.mode ?? 'SEMESTER',
    from: props.from,
    to: props.to,
    weekStart: props.weekStart,
    points: props.points,
    plotWidth: plotWidth.value,
    labelWidth: labelWidth.value,
  }),
);
function x(date: string): number | null {
  const position = axis.value ? headmanStatsTrendPosition(date, axis.value) : null;
  return position === null ? null : left + position * plotWidth.value;
}

function y(percent: number): number {
  return top + ((100 - percent) / 100) * plotHeight;
}

function pathFor(metric: 'present' | 'presentOrExcused'): string {
  const paths: string[] = [];
  let segment: string[] = [];
  const ordered = [...props.points].sort((a, b) => a.from.localeCompare(b.from));
  ordered.forEach((point) => {
    const percent = point[metric].percent;
    const pointX = x(point.from);
    if (percent === null || pointX === null) {
      if (segment.length) paths.push(segment.join(' '));
      segment = [];
      return;
    }
    segment.push(`${segment.length ? 'L' : 'M'} ${pointX.toFixed(2)} ${y(percent).toFixed(2)}`);
  });
  if (segment.length) paths.push(segment.join(' '));
  return paths.join(' ');
}

function metricText(metric: HeadmanStatsTrendMetric): string {
  return metric.percent === null
    ? 'Нет данных'
    : `${metric.percent.toFixed(1)}% (${metric.numerator} из ${metric.denominator})`;
}

function periodLabel(point: HeadmanStatsTrendPoint): string {
  const formatter = new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  });
  const from = formatter.format(new Date(point.from + 'T12:00:00Z'));
  return point.from === point.to
    ? from
    : `${from} — ${formatter.format(new Date(point.to + 'T12:00:00Z'))}`;
}
function pointAriaLabel(
  point: HeadmanStatsTrendPoint,
  label: string,
  metric: HeadmanStatsTrendMetric,
): string {
  return `${point.label}, ${label}: ${metricText(metric)}`;
}
</script>

<template>
  <div class="headman-stats-trend">
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
      aria-label="График посещаемости от 0 до 100 процентов. Зелёная линия — присутствовал, жёлтая — присутствовал или уважительная причина. Нажми на точку или перейди к ней клавиатурой, чтобы прочитать значения."
    >
      <svg
        ref="plot"
        class="headman-stats-trend__plot"
        :viewBox="`0 0 ${width} ${height}`"
        preserveAspectRatio="none"
        aria-hidden="false"
      >
        <!-- Keep exact SVG text: pinned Prettier pads this label with spaces. -->
        <!-- prettier-ignore -->
        <text
          ref="probe"
          class="headman-stats-trend__axis headman-stats-trend__probe"
          aria-hidden="true"
        >28 сент.</text>
        <g
          aria-hidden="true"
          class="headman-stats-trend__dividers"
        >
          <line
            v-for="tick in axis?.ticks ?? []"
            :key="tick.date"
            :x1="left + tick.position * plotWidth"
            :x2="left + tick.position * plotWidth"
            :y1="top"
            :y2="height - bottom"
          />
        </g>
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
            <!-- Keep exact SVG suffix text: pinned Prettier pads this interpolation. -->
            <!-- prettier-ignore -->
            <text
              :x="left - 8"
              :y="y(tick) + 4"
            >{{ tick }}%</text>
          </template>
        </g>
        <g aria-hidden="true">
          <text
            v-for="(tick, index) in axis?.ticks ?? []"
            :key="tick.date"
            :x="left + tick.position * plotWidth"
            :y="height - 6"
            :text-anchor="
              index === 0 ? 'start' : index === (axis?.ticks.length ?? 0) - 1 ? 'end' : 'middle'
            "
            class="headman-stats-trend__axis"
          >
            {{ tick.label }}
          </text>
        </g>
        <g aria-hidden="true">
          <text
            v-for="month in axis?.months ?? []"
            :key="month.position"
            :x="left + month.position * plotWidth"
            :y="height - 6"
            text-anchor="middle"
            class="headman-stats-trend__axis"
          >
            {{ month.label }}
          </text>
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
          v-for="point in points"
          :key="point.key"
        >
          <circle
            v-if="point.present.percent !== null && x(point.from) !== null"
            class="headman-stats-trend__point headman-stats-trend__point--present"
            :cx="x(point.from)!"
            :cy="y(point.present.percent)"
            r="5"
            tabindex="0"
            role="img"
            :aria-label="pointAriaLabel(point, presentLabel ?? 'Присутствовал', point.present)"
            @click="selectedPoint = point"
            @focus="selectedPoint = point"
          >
            <title>
              {{ pointAriaLabel(point, presentLabel ?? 'Присутствовал', point.present) }}
            </title>
          </circle>
          <circle
            v-if="point.presentOrExcused.percent !== null && x(point.from) !== null"
            class="headman-stats-trend__point headman-stats-trend__point--excused"
            :cx="x(point.from)!"
            :cy="y(point.presentOrExcused.percent)"
            r="5"
            tabindex="0"
            role="img"
            :aria-label="
              pointAriaLabel(
                point,
                presentOrExcusedLabel ?? 'Присутствовал или отсутствовал по уважительной причине',
                point.presentOrExcused,
              )
            "
            @click="selectedPoint = point"
            @focus="selectedPoint = point"
          >
            <title>
              {{
                pointAriaLabel(
                  point,
                  presentOrExcusedLabel ?? 'Присутствовал или отсутствовал по уважительной причине',
                  point.presentOrExcused,
                )
              }}
            </title>
          </circle>
        </g>
      </svg>
    </div>
    <div
      v-if="axis && (mode ?? 'SEMESTER') === 'SEMESTER'"
      class="headman-stats-trend__boundaries"
    >
      <time>{{
        new Intl.DateTimeFormat('ru-RU', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
          timeZone: 'UTC',
        }).format(axis.start)
      }}</time
      ><time v-if="axis.start !== axis.end">{{
        new Intl.DateTimeFormat('ru-RU', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
          timeZone: 'UTC',
        }).format(axis.end)
      }}</time>
    </div>
    <p
      v-if="selectedPoint"
      class="headman-stats-trend__readout"
      role="status"
    >
      {{ periodLabel(selectedPoint) }} · «+» {{ metricText(selectedPoint.present) }} · «+ и у»
      {{ metricText(selectedPoint.presentOrExcused) }}
    </p>
  </div>
</template>
