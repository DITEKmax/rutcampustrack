<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { StudentStatisticsSeriesPoint } from '../../../../mobile-core/src/api/types';
import { percent, shortDate } from './student-format';
const props = defineProps<{ points: readonly StudentStatisticsSeriesPoint[]; compare?: boolean }>();
const group = ref(false),
  selected = ref<string | null>(null);
const chosen = computed(() => props.points.find((p) => p.id === selected.value));
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
function x(point: StudentStatisticsSeriesPoint) {
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
function value(point: StudentStatisticsSeriesPoint, key: 'present' | 'presentOrExcused' | 'group') {
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
</script>
<template>
  <div class="student-trend">
    <div class="student-chart web-stack">
      <div
        v-if="compare"
        class="student-actions"
        role="group"
        aria-label="Сравнение графика"
      >
        <button
          class="web-button"
          :aria-pressed="!group"
          @click="group = false"
        >
          Личная
        </button><button
          class="web-button"
          :aria-pressed="group"
          @click="group = true"
        >
          С группой
        </button>
      </div>
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
          >{{ tick }}</span>
        </div>
        <svg
          class="student-chart-plot"
          viewBox="0 0 1000 210"
          preserveAspectRatio="none"
          role="img"
          aria-label="Динамика посещаемости, шкала от 0 до 100 процентов"
        >
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
            v-for="(line, index) in series"
            :key="line.key"
            :class="index ? 'student-series-secondary' : 'student-series-primary'"
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
              <circle
                v-if="value(point, line.key) !== null"
                :cx="x(point)"
                :cy="180 - (value(point, line.key) ?? 0) * 1.6"
                r="3"
              />
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
        <div
          class="student-chart-x-axis"
          aria-hidden="true"
        >
          <span>{{ points[0] ? shortDate(points[0].dateFrom) : '' }}</span><span>{{ points.at(-1) ? shortDate(points.at(-1)!.dateTo) : '' }}</span>
        </div>
      </div>
      <p
        v-else
        class="web-caption"
      >
        За выбранный период данных нет.
      </p>
      <p class="student-chart-legend">
        <span
          v-for="(line, index) in series"
          :key="line.key"
          :class="index ? 'student-series-secondary' : 'student-series-primary'"
        >{{ index ? '┄' : '━' }} {{ line.label }}</span>
      </p>
      <p
        v-if="chosen"
        role="status"
      >
        {{ chosen.label }}: {{ percent(value(chosen, 'present')) }} ·
        {{ chosen.metrics.present.count }} из {{ chosen.metrics.held }} пар<template v-if="group">
          · группа {{ percent(value(chosen, 'group')) }}
        </template>
      </p>
    </div>
    <div
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
              Доля «+»
            </th><th scope="col">
              {{ group ? 'Группа, «+»' : 'Доля «+» и «у»' }}
            </th><th scope="col">
              Пар учтено
            </th>
          </tr>
        </thead><tbody>
          <tr
            v-for="point in points"
            :key="point.id"
            :class="{ 'is-selected': selected === point.id }"
          >
            <th scope="row">
              <button
                class="student-text-button"
                :aria-pressed="selected === point.id"
                @click="selected = point.id"
              >
                {{ point.label || `${shortDate(point.dateFrom)} — ${shortDate(point.dateTo)}` }}
              </button>
            </th><td>{{ percent(value(point, 'present')) }}</td><td>{{ percent(value(point, group ? 'group' : 'presentOrExcused')) }}</td><td>
              {{ point.metrics.held
              }}<span
                v-if="point.state !== 'DATA'"
                class="web-caption"
              >
                · {{ point.state === 'FUTURE' ? 'Будущий период' : 'Нет данных' }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
