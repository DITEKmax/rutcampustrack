<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  adminActivityGeometry,
  adminActivityTickCandidates,
  adminActivityPointDate,
  fitAdminActivityTicks,
  ADMIN_ACTIVITY_PLOT_WIDTH,
  ADMIN_ACTIVITY_PLOT_HEIGHT,
  type AdminActivityChartPoint,
} from './admin-activity-chart';
import './admin-activity-chart.pcss';

const props = withDefaults(
  defineProps<{
    title: string;
    points: readonly AdminActivityChartPoint[];
    asOfDate: string;
    unit?: 'count' | 'percent';
  }>(),
  { unit: 'count' },
);
const geometry = computed(() =>
  adminActivityGeometry(props.points, props.asOfDate, props.unit === 'percent'),
);
const selection = ref<string | null>(null);
const selectedIndex = computed(() => {
  const index = geometry.value.cells.findIndex((cell) => cell.point.id === selection.value);
  return index < 0 ? Math.max(0, geometry.value.cells.length - 1) : index;
});
const selected = computed(() => geometry.value.cells[selectedIndex.value] ?? null);
const readoutVisible = computed(
  () => selection.value !== null && selected.value?.point.id === selection.value,
);
watch(
  () => [props.points, props.title],
  () => {
    selection.value = null;
  },
);
const tickContainer = ref<HTMLElement | null>(null);
const tickMeasure = ref<HTMLElement | null>(null);
const candidates = computed(() =>
  adminActivityTickCandidates(geometry.value.cells.map((cell) => cell.point)),
);
const labels = computed(() => [
  ...new Set(candidates.value.flatMap((ticks) => ticks.map((tick) => tick.label))),
]);
const rootFont = ref(16);
const width = ref(0),
  gap = ref(0),
  labelWidths = ref<Record<string, number>>({});
const axis = computed(() =>
  fitAdminActivityTicks(candidates.value, width.value, labelWidths.value, gap.value),
);
let observer: ResizeObserver | null = null;
function measure(): void {
  if (!tickContainer.value || !tickMeasure.value) return;
  rootFont.value = Number.parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;
  width.value = tickContainer.value.getBoundingClientRect().width;
  gap.value = Number.parseFloat(getComputedStyle(tickContainer.value).columnGap) || 0;
  labelWidths.value = Object.fromEntries(
    Array.from(tickMeasure.value.children).map((element) => [
      element.textContent ?? '',
      element.getBoundingClientRect().width,
    ]),
  );
}
watch(
  candidates,
  () => {
    void nextTick(measure);
  },
  { flush: 'post' },
);
watch(
  [tickContainer, tickMeasure],
  ([currentContainer, currentMeasure], [previousContainer, previousMeasure]) => {
    for (const element of [previousContainer, previousMeasure])
      if (element) observer?.unobserve(element);
    for (const element of [currentContainer, currentMeasure])
      if (element) observer?.observe(element);
    void nextTick(measure);
  },
  { flush: 'post' },
);
onMounted(() => {
  observer = new ResizeObserver(measure);
  for (const element of [tickContainer.value, tickMeasure.value])
    if (element) observer.observe(element);
  void document.fonts.ready.then(() => {
    if (observer) measure();
  });
  document.fonts.addEventListener('loadingdone', measure);
});
onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
  document.fonts.removeEventListener('loadingdone', measure);
});
function valueLabel(value: number | null): string {
  return value === null
    ? 'Нет данных'
    : new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value) +
        (props.unit === 'percent' ? '%' : '');
}
function description(point: AdminActivityChartPoint): string {
  return [adminActivityPointDate(point), valueLabel(point.value), point.detail]
    .filter(Boolean)
    .join('. ');
}
function choose(index: number): void {
  selection.value =
    geometry.value.cells[Math.max(0, Math.min(geometry.value.cells.length - 1, index))]?.point.id ??
    null;
}
function keyboard(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    selection.value = null;
    event.preventDefault();
    return;
  }
  const delta = ['ArrowLeft', 'ArrowDown'].includes(event.key)
    ? -1
    : ['ArrowRight', 'ArrowUp'].includes(event.key)
      ? 1
      : 0;
  if (!delta && event.key !== 'Home' && event.key !== 'End') return;
  event.preventDefault();
  choose(
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? geometry.value.cells.length - 1
        : selectedIndex.value + delta,
  );
}
function pointer(event: PointerEvent): void {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  if (!rect.width) return;
  const x = ((event.clientX - rect.left) / rect.width) * ADMIN_ACTIVITY_PLOT_WIDTH;
  const nearest = geometry.value.cells.reduce<(typeof geometry.value.cells)[number] | null>(
    (closest, cell) =>
      !closest || Math.abs(cell.x - x) < Math.abs(closest.x - x) ? cell : closest,
    null,
  );
  if (nearest) choose(nearest.index);
}
</script>

<template>
  <section
    class="admin-activity-chart"
    :aria-label="title"
  >
    <p
      v-if="!geometry.cells.length"
      class="admin-activity-chart__empty"
      role="status"
    >
      За этот период пока нет данных.
    </p>
    <template v-else>
      <div class="admin-activity-chart__plot">
        <div
          class="admin-activity-chart__values"
          aria-hidden="true"
        >
          <span>{{ valueLabel(geometry.maximum) }}</span>
          <span>{{ valueLabel(geometry.maximum / 2) }}</span>
          <span>{{ valueLabel(0) }}</span>
        </div>
        <div
          class="admin-activity-chart__canvas"
          :style="{ marginInline: `${axis.gutter / rootFont}rem` }"
          role="slider"
          tabindex="0"
          :aria-label="'Период: ' + title"
          :aria-valuemin="1"
          :aria-valuemax="geometry.cells.length"
          :aria-valuenow="selectedIndex + 1"
          :aria-valuetext="selected ? description(selected.point) : ''"
          :data-admin-chart-points="geometry.cells.length"
          @keydown="keyboard"
          @pointerdown="pointer"
        >
          <svg
            :viewBox="`0 0 ${ADMIN_ACTIVITY_PLOT_WIDTH} ${ADMIN_ACTIVITY_PLOT_HEIGHT}`"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <path
              v-for="level in [0, 0.5, 1]"
              :key="level"
              class="admin-activity-chart__grid"
              :d="`M0 ${level * ADMIN_ACTIVITY_PLOT_HEIGHT}H${ADMIN_ACTIVITY_PLOT_WIDTH}`"
            />
            <path
              v-for="(path, index) in geometry.paths"
              :key="index"
              class="admin-activity-chart__line"
              :d="path"
            />
            <circle
              v-for="cell in geometry.cells.filter((item) => item.y !== null)"
              :key="cell.point.id"
              class="admin-activity-chart__point"
              :cx="cell.x"
              :cy="cell.y!"
              r="3"
            />
            <path
              v-if="readoutVisible && selected"
              class="admin-activity-chart__selection"
              :d="`M${selected.x} 0V${ADMIN_ACTIVITY_PLOT_HEIGHT}`"
            />
          </svg>
        </div>
        <div
          ref="tickContainer"
          class="admin-activity-chart__ticks"
          :class="{ 'admin-activity-chart__ticks--stacked': axis.stacked }"
          aria-hidden="true"
        >
          <div
            ref="tickMeasure"
            class="admin-activity-chart__measure"
          >
            <span
              v-for="label in labels"
              :key="label"
              >{{ label }}</span
            >
          </div>
          <span
            v-for="tick in axis.ticks"
            :key="tick.position"
            class="admin-activity-chart__tick"
            :style="{ left: `${(axis.gutter + tick.position * axis.plotWidth) / rootFont}rem` }"
            :title="tick.description"
            >{{ tick.label }}</span
          >
        </div>
      </div>
      <div
        v-if="readoutVisible && selected"
        class="admin-activity-chart__readout"
        role="status"
        data-admin-chart-readout
      >
        <span>{{ adminActivityPointDate(selected.point) }}</span
        ><strong>{{ valueLabel(selected.point.value) }}</strong>
        <p v-if="selected.point.detail">
          {{ selected.point.detail }}
        </p>
      </div>
      <div class="admin-activity-chart__alternative">
        <p
          >Временные опоры шкалы: {{ axis.ticks.map((tick) => tick.description).join('; ') }}.
          Значения есть только у измеренных точек.</p
        >
        <table>
          <caption>{{ title }}: значения графика</caption>
          <thead>
            <tr> <th scope="col"> Период </th><th scope="col"> Значение </th> </tr>
          </thead>
          <tbody>
            <tr
              v-for="cell in geometry.cells"
              :key="cell.point.id"
            >
              <th scope="row"> {{ adminActivityPointDate(cell.point) }} </th
              ><td>{{ valueLabel(cell.point.value) }} {{ cell.point.detail }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
