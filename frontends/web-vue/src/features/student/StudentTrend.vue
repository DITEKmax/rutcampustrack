<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { StudentStatisticsSeriesPoint } from '../../../../mobile-core/src/api/types';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import { percent, shortDate } from './student-format';
type TrendPoint = Pick<StudentStatisticsSeriesPoint, 'id' | 'label' | 'dateFrom' | 'dateTo' | 'state' | 'groupPresent'> & { metrics: Pick<StudentStatisticsSeriesPoint['metrics'], 'present' | 'presentOrExcused' | 'held'> };
const props = defineProps<{ points: readonly TrendPoint[]; compare?: boolean; comparison?: 'personal' | 'group'; countLabel?: string; area?: boolean }>();
const emit = defineEmits<{ 'update:comparison': [value: 'personal' | 'group'] }>();
const localGroup = ref(false),
  selected = ref<string | null>(null);
const group = computed({
  get: () => props.comparison === undefined ? localGroup.value : props.comparison === 'group',
  set: (value: boolean) => {
    if (props.comparison === undefined) localGroup.value = value;
    else emit('update:comparison', value ? 'group' : 'personal');
  },
});
const canCompare = computed(() => Boolean(props.compare && props.points.some((point) => point.groupPresent != null)));
watch(canCompare, (available) => { if (!available && group.value) group.value = false; });
const chosen = computed(() => props.points.find((p) => p.id === selected.value));
const trendRoot = ref<HTMLElement | null>(null);
const chartScroll = ref<HTMLElement | null>(null);
const chartCanvas = ref<HTMLElement | null>(null);
const axisProbe = ref<HTMLElement | null>(null);
const tableScroll = ref<HTMLElement | null>(null);
const tableBody = ref<HTMLElement | null>(null);
const canvasWidth = ref(0);
const labelWidth = ref(0);
let resizeObserver: ResizeObserver | undefined;
watch([chartCanvas, axisProbe], ([canvas, probe]) => {
  resizeObserver?.disconnect();
  if (!canvas || !probe) return;
  const measure = () => {
    canvasWidth.value = canvas.clientWidth;
    labelWidth.value = probe.getBoundingClientRect().width;
  };
  resizeObserver = new ResizeObserver(measure);
  resizeObserver.observe(canvas);
  resizeObserver.observe(probe);
  measure();
}, { flush: 'post' });
function closeOutside(event: PointerEvent) {
  if (trendRoot.value && !event.composedPath().includes(trendRoot.value)) selected.value = null;
}
onMounted(() => document.addEventListener('pointerdown', closeOutside));
onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  document.removeEventListener('pointerdown', closeOutside);
});
function periodLabel(point: TrendPoint) {
  return point.dateFrom === point.dateTo
    ? shortDate(point.dateFrom)
    : `${shortDate(point.dateFrom)} — ${shortDate(point.dateTo)}`;
}
function axisLabel(point: TrendPoint) {
  return new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })
    .format(new Date(`${point.dateFrom}T12:00:00Z`));
}
watch(
  () => props.points,
  (points) => {
    if (!points.some((p) => p.id === selected.value)) selected.value = null;
  },
);
const domain = computed(() => {
  const starts = props.points
    .map((p) => Date.parse(`${p.dateFrom}T12:00:00Z`))
    .filter(Number.isFinite);
  const ends = props.points.map((p) => Date.parse(`${p.dateTo}T12:00:00Z`)).filter(Number.isFinite);
  return {
    from: starts.length ? Math.min(...starts) : 0,
    to: ends.length ? Math.max(...ends) + 86_400_000 : 1,
  };
});
function x(point: TrendPoint) {
  return (
    50 +
    (((Date.parse(`${point.dateFrom}T12:00:00Z`) +
      Date.parse(`${point.dateTo}T12:00:00Z`) +
      86_400_000) /
      2 -
      domain.value.from) /
      (domain.value.to - domain.value.from)) *
      900
  );
}
// Keep all periods and marks; only the lower labels become less frequent.
const axisPoints = computed(() => {
  const first = props.points[0];
  const last = props.points[props.points.length - 1];
  if (!first || !last) return [];
  if (first === last) return [first];
  const gap = labelWidth.value * 2;
  const distance = (a: TrendPoint, b: TrendPoint) =>
    ((x(b) - x(a)) / 1000) * canvasWidth.value;
  const result = [first];
  let previous = first;
  for (const point of props.points.slice(1, -1)) {
    if (distance(previous, point) >= gap && distance(point, last) >= gap) {
      result.push(point);
      previous = point;
    }
  }
  result.push(last);
  return result;
});
function labelPosition(point: TrendPoint) {
  const halfLabel = canvasWidth.value ? (labelWidth.value / canvasWidth.value) * 50 : 0;
  return `${Math.min(100 - halfLabel, Math.max(halfLabel, x(point) / 10))}%`;
}
function targetPosition(index: number) {
  const point = props.points[index];
  if (!point) return {};
  const previous = props.points[index - 1];
  const next = props.points[index + 1];
  const start = previous ? (x(previous) + x(point)) / 2 : 0;
  const end = next ? (x(point) + x(next)) / 2 : 1000;
  return { left: `${start / 10}%`, width: `${(end - start) / 10}%` };
}
async function selectPeriod(point: TrendPoint, source: 'chart' | 'table') {
  selected.value = point.id;
  await nextTick();
  if (source === 'chart') {
    const container = tableScroll.value;
    const index = props.points.findIndex((item) => item.id === point.id);
    const row = tableBody.value?.children[index];
    if (!container || !(row instanceof HTMLElement)) return;
    const bounds = container.getBoundingClientRect();
    const rowBounds = row.getBoundingClientRect();
    if (rowBounds.top < bounds.top || rowBounds.bottom > bounds.bottom) {
      container.scrollTop += rowBounds.top - bounds.top - (container.clientHeight - rowBounds.height) / 2;
    }
  } else {
    const container = chartScroll.value;
    if (!container) return;
    const position = (x(point) / 1000) * canvasWidth.value;
    const margin = labelWidth.value;
    if (position < container.scrollLeft + margin || position > container.scrollLeft + container.clientWidth - margin) {
      container.scrollLeft = position - container.clientWidth / 2;
    }
  }
}
function value(point: TrendPoint, key: 'present' | 'presentOrExcused' | 'group') {
  if (point.state !== 'DATA') return null;
  if (key === 'group') return point.groupPresent?.denominator ? point.groupPresent.percent : null;
  return point.metrics.held > 0 ? point.metrics[key].percent : null;
}
const series = computed(() =>
  group.value
    ? [
        { key: 'present' as const, label: 'Моя доля «+»' },
        { key: 'group' as const, label: 'Доля «+» группы' },
      ]
    : [
        { key: 'present' as const, label: 'Доля «+»' },
        { key: 'presentOrExcused' as const, label: 'Доля «+» и «у»' },
      ],
);
const plottedSeries = computed(() => props.area ? [...series.value].reverse() : series.value);
function paths(key: 'present' | 'presentOrExcused' | 'group') {
  const runs: string[] = [];
  let previous = false;
  for (const point of props.points) {
    const number = value(point, key);
    if (number === null || !Number.isFinite(number)) {
      previous = false;
      continue;
    }
    const pair = `${x(point)},${180 - number * 1.6}`;
    if (previous) runs[runs.length - 1] += ` L ${pair}`;
    else runs.push(`M ${pair}`);
    previous = true;
  }
  return runs;
}
function areaPaths(key: 'present' | 'presentOrExcused' | 'group') {
  const runs: { x: number; y: number }[][] = [];
  let run: { x: number; y: number }[] = [];
  for (const point of props.points) {
    const number = value(point, key);
    if (number === null || !Number.isFinite(number)) {
      if (run.length) runs.push(run);
      run = [];
    } else run.push({ x: x(point), y: 180 - number * 1.6 });
  }
  if (run.length) runs.push(run);
  return runs.filter(points => points.length > 1).map(points =>
    `M ${points[0]!.x},180 ${points.map(point => `L ${point.x},${point.y}`).join(' ')} L ${points[points.length - 1]!.x},180 Z`,
  );
}

</script>
<template>
  <div ref="trendRoot" class="student-trend" :class="{ 'has-area': area }" @keydown.esc.stop="selected = null">
      <div
        v-if="canCompare && comparison === undefined"
        class="student-actions student-trend-comparison"
        role="group"
        aria-label="Сравнение графика"
      >
        <button
          class="web-button"
          :class="{ 'web-button-selected': !group }"
          :aria-pressed="!group"
          @click="group = false"
        >
          Личная
        </button><button
          class="web-button"
          :class="{ 'web-button-selected': group }"
          :aria-pressed="group"
          @click="group = true"
        >
          С группой
        </button>
      </div>
    <div class="student-chart web-stack">
      <div
        v-if="points.length"
        class="student-chart-frame"
      >
        <div
          class="student-chart-y-axis"
          aria-hidden="true"
        >
          <span
            v-for="tick in [100, 75, 50, 25, 0]"
            :key="tick"
            :style="{ top: `${((180 - tick * 1.6) / 210) * 100}%` }"
          >{{ tick }}</span>
        </div>
        <div ref="chartScroll" class="student-chart-scroll" tabindex="0" aria-label="График по периодам">
          <div ref="chartCanvas" class="student-chart-canvas" :style="{ '--student-chart-point-count': points.length }">
            <svg
              class="student-chart-plot"
              viewBox="0 0 1000 210"
              preserveAspectRatio="none"
              role="img"
              aria-label="Динамика посещаемости, шкала от 0 до 100 процентов"
            >
              <g v-if="area" aria-hidden="true">
                <g :class="group ? 'student-series-group' : 'student-series-secondary'"><path v-for="(path, n) in areaPaths(group ? 'group' : 'presentOrExcused')" :key="n" :d="path" class="student-chart-area" /></g>
                <g class="student-series-primary"><path v-for="(path, n) in areaPaths('present')" :key="n" :d="path" class="student-chart-area" /></g>
              </g>
              <g
                v-for="tick in [0, 25, 50, 75, 100]"
                :key="tick"
              >
                <line
                  x1="50"
                  x2="950"
                  :y1="180 - tick * 1.6"
                  :y2="180 - tick * 1.6"
                  class="student-chart-grid"
                />
              </g>
              <g
                v-for="line in plottedSeries"
                :key="line.key"
                :class="line.key === 'group' ? 'student-series-group' : line.key === 'present' ? 'student-series-primary' : 'student-series-secondary'"
              >
                <path
                  v-for="(path, n) in paths(line.key)"
                  :key="n"
                  :d="path"
                  class="student-chart-line"
                />
                <template
                  v-for="point in points"
                  :key="point.id"
                >
                  <path
                    v-if="value(point, line.key) !== null"
                    :d="`M ${x(point)} ${180 - (value(point, line.key) ?? 0) * 1.6} h 0`"
                    class="student-chart-point"
                  ><title>{{ line.label }} · {{ periodLabel(point) }}: {{ percent(value(point, line.key)) }}</title></path>
                </template>
              </g>
              <line
                v-if="chosen"
                :x1="x(chosen)"
                :x2="x(chosen)"
                y1="20"
                y2="180"
                class="student-chart-selection"
              />
            </svg>
            <div class="student-chart-targets" role="group" aria-label="Выбери период графика">
              <button
                v-for="(point, index) in points"
                :key="point.id"
                type="button"
                class="student-chart-target"
                :style="targetPosition(index)"
                :aria-label="`${periodLabel(point)}: доля «+» ${percent(value(point, 'present'))}, ${group ? 'группа' : 'доля «+» и «у»'} ${percent(value(point, group ? 'group' : 'presentOrExcused'))}, ${countLabel ?? 'Пар учтено'} ${point.metrics.held}`"
                :aria-pressed="selected === point.id"
                @click="selectPeriod(point, 'chart')"
              />
            </div>
            <div
              class="student-chart-x-axis"
              aria-hidden="true"
            >
              <span ref="axisProbe" class="student-chart-axis-probe">00.00</span>
              <span
                v-for="point in axisPoints"
                :key="point.id"
                :style="{ left: labelPosition(point) }"
                :title="periodLabel(point)"
              >{{ axisLabel(point) }}</span>
            </div>
          </div>
        </div>
        <div v-if="chosen" class="student-chart-detail" role="status">
          <strong>{{ periodLabel(chosen) }}</strong>
          <span>Доля «+»: {{ percent(value(chosen, 'present')) }}</span>
          <span>{{ group ? 'Группа, «+»' : 'Доля «+» и «у»' }}: {{ percent(value(chosen, group ? 'group' : 'presentOrExcused')) }}</span>
          <span>{{ countLabel ?? 'Пар учтено' }}: {{ chosen.metrics.held }}</span>
          <span v-if="chosen.state !== 'DATA'">{{ chosen.state === 'FUTURE' ? 'Пары ещё не завершены' : 'Нет данных' }}</span>
          <button type="button" class="web-button web-icon-button student-chart-detail-close" aria-label="Закрыть сведения о периоде" @click="selected = null"><MobileIcon name="close" /></button>
        </div>
      </div>
      <p
        v-else
        class="web-caption"
      >
        За выбранный период данных нет.
      </p>
    </div>
    <div
      ref="tableScroll"
      class="student-chart-data"
      tabindex="0"
      aria-label="Данные графика"
    >
      <table class="student-table">
        <caption class="web-sr-only">
          Выбери период для подробностей
        </caption><thead>
          <tr>
            <th scope="col">
              Период
            </th><th scope="col">
              <span class="student-series-swatch student-series-primary" aria-hidden="true" />
              Доля «+»
            </th><th scope="col">
              <span class="student-series-swatch" :class="group ? 'student-series-group' : 'student-series-secondary'" aria-hidden="true" />
              {{ group ? 'Группа, «+»' : 'Доля «+» и «у»' }}
            </th><th scope="col">
              {{ countLabel ?? 'Пар учтено' }}
            </th>
          </tr>
        </thead><tbody ref="tableBody">
          <tr
            v-for="point in points"
            :key="point.id"
            :class="{ 'is-selected': selected === point.id }"
          >
            <th scope="row">
              <button
                class="student-text-button"
                :aria-pressed="selected === point.id"
                @click="selectPeriod(point, 'table')"
              >
                {{ periodLabel(point) }}
              </button>
            </th><td>{{ percent(value(point, 'present')) }}</td><td>{{ percent(value(point, group ? 'group' : 'presentOrExcused')) }}</td><td>{{ point.metrics.held }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
