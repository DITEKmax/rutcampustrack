<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { HeadmanStatsTrendPoint } from '../../../../mobile-core/src/features/headman-stats/headman-stats-client';
import { dateLabel } from './context';
const props = defineProps<{
  points: readonly HeadmanStatsTrendPoint[];
  from?: string | null;
  to?: string | null;
}>();
const selected = ref<string | null>(null);
watch(
  () => props.points,
  () => {
    selected.value = null;
  },
);
const ordered = computed(() => [...props.points].sort((a, b) => a.from.localeCompare(b.from)));
const origin = computed(() => Date.parse(`${props.from ?? ordered.value[0]?.from}T12:00:00Z`));
const end = computed(() => Date.parse(`${props.to ?? ordered.value.at(-1)?.to}T12:00:00Z`));
function x(point: HeadmanStatsTrendPoint) {
  return (
    55 +
    ((Date.parse(`${point.from}T12:00:00Z`) - origin.value) /
      Math.max(86400000, end.value - origin.value)) *
      820
  );
}
function y(percent: number) {
  return 205 - percent * 1.8;
}
function path(metric: 'present' | 'presentOrExcused') {
  let connected = false;
  return ordered.value
    .map((point) => {
      const percent = point[metric].percent;
      if (percent === null) {
        connected = false;
        return '';
      }
      const segment = `${connected ? 'L' : 'M'}${x(point)},${y(percent)}`;
      connected = true;
      return segment;
    })
    .join(' ');
}
const active = computed(() => props.points.find((point) => point.key === selected.value));
const format = (value: number | null) =>
  value === null ? 'Нет данных' : `${value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })}%`;
</script>
<template>
  <div class="web-stack"
    ><p
      v-if="!points.some((point) => point.present.percent !== null)"
      class="web-notice"
      >Нет завершённых пар для расчёта динамики.</p
    >
    <svg
      v-else
      class="headman-trend"
      viewBox="0 0 900 250"
      role="img"
      aria-label="Динамика посещаемости в процентах от 0 до 100"
    >
      <g
        v-for="tick in [0, 50, 100]"
        :key="tick"
      >
        <line
          x1="55"
          x2="875"
          :y1="y(tick)"
          :y2="y(tick)"
        />
        <text
          x="45"
          :y="y(tick) + 4"
          text-anchor="end"
        >
          {{ tick }}%
        </text>
      </g>
      <path
        class="headman-trend-plus"
        :d="path('present')"
      />
      <path
        class="headman-trend-combined"
        :d="path('presentOrExcused')"
      />
      <g
        v-for="point in ordered"
        :key="point.key"
      >
        <circle
          v-if="point.present.percent !== null"
          class="headman-trend-point"
          :cx="x(point)"
          :cy="y(point.present.percent)"
          r="4"
          tabindex="0"
          role="button"
          :aria-label="`${point.label}: были ${format(point.present.percent)}, были и уважительная ${format(point.presentOrExcused.percent)}`"
          @focus="selected = point.key"
          @click="selected = point.key"
          @keydown.enter="selected = point.key"
          @keydown.space.prevent="selected = point.key"
        />
      </g>
      <text
        x="55"
        y="240"
      >
        {{ dateLabel(from ?? ordered[0]?.from) }}
      </text>
      <text
        x="875"
        y="240"
        text-anchor="end"
      >
        {{ dateLabel(to ?? ordered.at(-1)?.to) }}
      </text>
    </svg>
    <p>Сплошная линия — были «+» · пунктир — были и уважительная «+ и у»</p
    ><p
      v-if="active"
      role="status"
      >{{ active.label }}: были {{ format(active.present.percent) }} ({{
        active.present.numerator
      }}/{{ active.present.denominator }}), были и уважительная
      {{ format(active.presentOrExcused.percent) }} ({{ active.presentOrExcused.numerator }}/{{
        active.presentOrExcused.denominator
      }})</p
    >
    <details
      ><summary>Данные графика</summary
      ><div class="headman-table-scroll"
        ><table class="headman-table"
          ><thead
            ><tr><th>Период</th><th>Были</th><th>Были и уважительная</th></tr></thead
          ><tbody
            ><tr
              v-for="point in ordered"
              :key="point.key"
              ><th>{{ point.label }}</th
              ><td
                >{{ format(point.present.percent) }} · {{ point.present.numerator }}/{{
                  point.present.denominator
                }}</td
              ><td
                >{{ format(point.presentOrExcused.percent) }} ·
                {{ point.presentOrExcused.numerator }}/{{ point.presentOrExcused.denominator }}</td
              ></tr
            ></tbody
          ></table
        ></div
      ></details
    >
  </div>
</template>
