<script setup lang="ts">
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  displayPercent,
  type AttendanceGraphPoint,
  type AttendanceGraphRange,
} from './attendance-view-model';
import {
  initialPeriodPage,
  periodState,
  shortDate,
  weekday,
  type AttendancePeriod,
} from './attendance-periods';
type GraphPoint = Parameters<typeof periodState>[0];
const props = withDefaults(
  defineProps<{
    points: readonly (AttendanceGraphPoint | AttendancePeriod)[];
    range?: AttendanceGraphRange;
    serverNow?: string | undefined;
  }>(),
  { range: 'days', serverNow: '' },
);
const emit = defineEmits<{ 'change-range': [range: AttendanceGraphRange] }>();
const columns = ref<HTMLElement | null>(null);
const detailMetrics = ref<HTMLElement | null>(null);
const detailLabelsOutside = ref(false);
let observer: ResizeObserver | null = null;
function measureDetailLabels(): void {
  detailLabelsOutside.value = Array.from(
    detailMetrics.value?.querySelectorAll<HTMLElement>('.attendance-graph__detail-segment') ?? [],
  ).some((segment) => {
    const label = segment.querySelector<HTMLElement>('.attendance-graph__detail-segment-label');
    return Boolean(
      label && label.getBoundingClientRect().width > segment.getBoundingClientRect().width,
    );
  });
}
watch(
  detailMetrics,
  (current, previous) => {
    if (previous) observer?.unobserve(previous);
    if (current) observer?.observe(current);
    void nextTick(measureDetailLabels);
  },
  { flush: 'post' },
);
onMounted(() => {
  observer = new ResizeObserver(() => {
    measureDetailLabels();
  });
  if (columns.value) observer.observe(columns.value);
  if (detailMetrics.value) observer.observe(detailMetrics.value);
  measureDetailLabels();
  void document.fonts.ready.then(() => {
    if (observer) measureDetailLabels();
  });
});
onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
});
const page = ref(0);
const selectedId = ref<string | null>(null);
const size = computed(() => (props.range === 'days' ? 7 : 6));
const pageCount = computed(() => Math.max(1, Math.ceil(props.points.length / size.value)));
const visible = computed(() => props.points);
const pagePoints = computed(() =>
  props.points.slice(page.value * size.value, (page.value + 1) * size.value),
);
const selected = computed(
  () =>
    visible.value.find((point) => point.id === selectedId.value) ??
    pagePoints.value.find(
      (point) => point.state === 'DATA' && !('outsideSemester' in point && point.outsideSemester),
    ) ??
    pagePoints.value.find((point) => !('outsideSemester' in point && point.outsideSemester)) ??
    pagePoints.value[0] ??
    null,
);
const statusMetrics = ['present', 'excused', 'absent'] as const;
const statusLabels = { present: 'Был', excused: 'Уважительная причина', absent: 'Не был' } as const;
const selectedState = computed(() => (selected.value ? graphState(selected.value) : ''));
watch(selected, () => {
  void nextTick(measureDetailLabels);
});
watch(
  () => [props.points, props.range, props.serverNow] as const,
  () => {
    page.value = initialPeriodPage(props.points, props.range, props.serverNow);
    selectedId.value = null;
    void nextTick(scrollToPage);
  },
  { immediate: true },
);
function changePage(delta: number): void {
  page.value = Math.max(0, Math.min(pageCount.value - 1, page.value + delta));
  selectedId.value = null;
  scrollToPage();
}
function scrollToPage(): void {
  const plot = columns.value;
  const target = plot?.querySelectorAll<HTMLButtonElement>('button')[page.value * size.value];
  if (!plot || !target) return;
  plot.scrollTo({ left: target.offsetLeft, behavior: 'auto' });
}
function onScroll(): void {
  const plot = columns.value;
  if (!plot || !plot.clientWidth) return;
  const buttons = Array.from(plot.querySelectorAll<HTMLButtonElement>('button'));
  const first = buttons[0];
  if (!first) return;
  // Keep the existing period controls in sync with touch, trackpad and keyboard scrolling.
  const index = buttons.findIndex(
    (button) => button.offsetLeft - first.offsetLeft >= plot.scrollLeft,
  );
  page.value =
    plot.scrollLeft + plot.clientWidth >= plot.scrollWidth - 1
      ? pageCount.value - 1
      : Math.min(pageCount.value - 1, Math.floor(Math.max(0, index) / size.value));
}
function keyboard(event: KeyboardEvent, index: number): void {
  const offset = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0;
  if (!offset && event.key !== 'Home' && event.key !== 'End') return;
  event.preventDefault();
  const next =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? visible.value.length - 1
        : Math.max(0, Math.min(visible.value.length - 1, index + offset));
  selectedId.value = visible.value[next]?.id ?? null;
  const target = (
    event.currentTarget as HTMLElement
  ).parentElement?.querySelectorAll<HTMLButtonElement>('button')[next];
  target?.focus();
  target?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}
function percentage(point: GraphPoint, metric: 'present' | 'excused' | 'absent'): number {
  return point.state !== 'DATA' || !point.metrics.held
    ? 0
    : Math.min(100, Math.max(0, point.metrics[metric].percent ?? 0));
}
function hasMarks(point: GraphPoint): boolean {
  return (
    !('missing' in point && point.missing) &&
    !('outsideSemester' in point && point.outsideSemester) &&
    point.state === 'DATA' &&
    point.metrics.held > 0 &&
    point.metrics.present.percent !== null &&
    point.metrics.absent.percent !== null &&
    point.metrics.excused.percent !== null
  );
}
function isPlanned(point: GraphPoint): boolean {
  return (
    point.state === 'FUTURE' &&
    point.metrics.planned > 0 &&
    !('missing' in point && point.missing) &&
    !('outsideSemester' in point && point.outsideSemester)
  );
}
function graphState(point: GraphPoint): string {
  if (
    ('missing' in point && point.missing) ||
    ('outsideSemester' in point && point.outsideSemester)
  )
    return periodState(point);
  if (hasMarks(point)) return '';
  if (point.state === 'DATA' && point.metrics.held > 0) return 'Нет данных об отметках';
  return periodState(point);
}
function metricDescription(point: GraphPoint): string {
  return statusMetrics
    .map(
      (metric) =>
        `${statusLabels[metric]}: ${point.metrics[metric].count}, ${displayPercent(point.metrics[metric].percent)}`,
    )
    .join('; ');
}
function description(point: GraphPoint): string {
  return [point.label, graphState(point), point.state === 'DATA' ? metricDescription(point) : '']
    .filter(Boolean)
    .join(': ');
}
</script>
<template>
  <section
    class="attendance-graph"
    aria-labelledby="attendance-graph-title"
  >
    <h2 id="attendance-graph-title"> Посещаемость за семестр </h2>
    <div
      class="attendance-graph__range"
      role="group"
      aria-label="Период графика"
    >
      <button
        v-for="item in ['days', 'weeks'] as const"
        :key="item"
        class="attendance-graph__range-button"
        :class="{ 'attendance-graph__range-button--selected': range === item }"
        type="button"
        :aria-pressed="range === item"
        @click="emit('change-range', item)"
      >
        {{ item === 'days' ? 'Дни' : 'Недели' }}
      </button>
    </div>
    <div class="attendance-period-pager">
      <button
        type="button"
        aria-label="Предыдущие периоды"
        :disabled="page === 0"
        @click="changePage(-1)"
      >
        <MobileIcon name="back" />
      </button>
      <span role="status"
        >{{ pagePoints[0] ? shortDate(pagePoints[0].dateFrom) : 'Нет периодов'
        }}{{
          pagePoints.length ? ` — ${shortDate(pagePoints[pagePoints.length - 1]!.dateTo)}` : ''
        }}</span
      >
      <button
        type="button"
        aria-label="Следующие периоды"
        :disabled="page + 1 >= pageCount"
        @click="changePage(1)"
      >
        <MobileIcon
          name="back"
          class="attendance-period-pager__next"
        />
      </button>
    </div>
    <p
      v-if="!points.length"
      class="attendance-graph__empty"
      role="status"
    >
      За семестр пока нет данных.
    </p>
    <div
      v-else
      class="attendance-graph__plot"
    >
      <div
        class="attendance-graph__axis"
        aria-hidden="true"
      >
        <span>100%</span><span>50%</span><span>0%</span>
      </div>
      <div
        ref="columns"
        class="attendance-graph__columns"
        :data-range="range"
        role="group"
        aria-label="График по всему семестру. Выбери период для подробностей"
        @scroll.passive="onScroll"
      >
        <button
          v-for="(point, index) in visible"
          :key="point.id"
          class="attendance-graph__column"
          type="button"
          :data-state="point.state"
          :class="{ 'attendance-graph__column--selected': selected?.id === point.id }"
          :aria-pressed="selected?.id === point.id"
          :aria-label="description(point)"
          @click="selectedId = point.id"
          @keydown="keyboard($event, index)"
        >
          <span
            class="attendance-graph__stack"
            :class="{
              'attendance-graph__stack--unknown': !hasMarks(point),
              'attendance-graph__stack--planned': isPlanned(point),
            }"
            aria-hidden="true"
          >
            <template v-if="hasMarks(point)">
              <span
                class="attendance-graph__band attendance-graph__band--present"
                :style="{ height: `${percentage(point, 'present')}%` }"
              />
              <span
                class="attendance-graph__band attendance-graph__band--excused"
                :style="{ height: `${percentage(point, 'excused')}%` }"
              />
              <span
                class="attendance-graph__band attendance-graph__band--absent"
                :style="{ height: `${percentage(point, 'absent')}%` }"
              />
            </template>
            <span
              v-if="!hasMarks(point) && !isPlanned(point)"
              class="attendance-graph__neutral-label"
              >—</span
            >
          </span>
          <span class="attendance-graph__date"
            >{{ range === 'days' ? weekday(point.dateFrom) : shortDate(point.dateFrom)
            }}<br v-if="range === 'days'" />{{
              range === 'days' ? point.dateFrom.slice(8) : ''
            }}</span
          >
        </button>
      </div>
    </div>
    <section
      v-if="selected"
      class="attendance-graph__detail"
      aria-label="Выбранный период"
      aria-live="polite"
    >
      <h3>
        {{ shortDate(selected.dateFrom)
        }}{{ selected.dateTo !== selected.dateFrom ? ` — ${shortDate(selected.dateTo)}` : '' }}
      </h3>
      <p v-if="selectedState">
        {{ selectedState }}
      </p>
      <template
        v-if="
          !('missing' in selected && selected.missing) &&
          !('outsideSemester' in selected && selected.outsideSemester)
        "
      >
        <p>Проведено {{ selected.metrics.held }} из {{ selected.metrics.planned }}</p>
        <p
          v-if="selected.state === 'DATA' && !hasMarks(selected)"
          class="attendance-visually-hidden"
        >
          {{ metricDescription(selected) }}
        </p>
        <div
          v-if="hasMarks(selected)"
          ref="detailMetrics"
          class="attendance-graph__detail-metrics"
          :class="{ 'attendance-graph__detail-metrics--outside-labels': detailLabelsOutside }"
          role="img"
          :aria-label="metricDescription(selected)"
        >
          <span
            v-for="metric in statusMetrics"
            :key="metric"
            class="attendance-graph__detail-segment"
            :class="`attendance-graph__band--${metric}`"
            :style="{ inlineSize: `${percentage(selected, metric)}%` }"
            aria-hidden="true"
          >
            <span class="attendance-graph__detail-segment-label">{{
              displayPercent(selected.metrics[metric].percent)
            }}</span>
          </span>
        </div>
        <div
          v-if="hasMarks(selected) && detailLabelsOutside"
          class="attendance-graph__detail-percentages"
          aria-hidden="true"
        >
          <span
            v-for="metric in statusMetrics"
            :key="metric"
            :class="`attendance-graph__detail-percentage--${metric}`"
            >{{ displayPercent(selected.metrics[metric].percent) }}</span
          >
        </div>
      </template>
    </section>
  </section>
</template>
