<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { headmanStatsTrendAxis } from '../headman-stats/headman-stats-trend-axis';
import type { TeacherJournalResponse, TeacherJournalMetrics } from './teacher-client';
import { journalMetricPercent } from './teacher-journal-model';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import { journalChartBarLayout } from './teacher-journal-chart-layout';
const props = defineProps<{
  journal: TeacherJournalResponse;
  studentId: number | null;
  metrics?: TeacherJournalMetrics | null;
  from: string;
  to: string;
}>();
const plot = ref<SVGSVGElement | null>(null);
const probe = ref<SVGTextElement | null>(null);
const width = ref(360);
const labelWidth = ref(50);
const percentLabelWidth = ref(30);
const readout = ref<string | null>(null);
let observer: ResizeObserver | null = null;
function measure(): void {
  if (!plot.value || !probe.value) return;
  const measured = plot.value.getBoundingClientRect().width;
  if (measured > 0) width.value = measured;
  const ctx = document.createElement('canvas').getContext('2d');
  if (!ctx) return;
  const style = getComputedStyle(probe.value);
  ctx.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
  percentLabelWidth.value = ctx.measureText('100%').width;
  const format = new Intl.DateTimeFormat('ru', { day: 'numeric', month: 'short', timeZone: 'UTC' });
  let max = 0;
  for (let month = 0; month < 12; month++)
    for (let day = 1; day <= 28; day++)
      max = Math.max(
        max,
        ctx.measureText(format.format(new Date(Date.UTC(2026, month, day)))).width,
      );
  labelWidth.value = max;
}
onMounted(() => {
  measure();
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(measure);
    if (plot.value) observer.observe(plot.value);
  }
  void document.fonts?.ready.then(measure);
});
onBeforeUnmount(() => observer?.disconnect());
watch(
  () => [props.journal, props.studentId, props.from, props.to],
  () => {
    readout.value = null;
  },
);
const left = computed(() => Math.ceil(percentLabelWidth.value) + 12);
const right = 8,
  top = 12,
  bottom = 32,
  height = 210;
const plotWidth = computed(() => Math.max(1, width.value - left.value - right));
// Reuse accepted equidistant label math with explicit anchors and no observation-derived bounds.
const axis = computed(() =>
  headmanStatsTrendAxis({
    mode: 'SUBJECT',
    from: props.from,
    to: props.to,
    points: [],
    plotWidth: plotWidth.value,
    labelWidth: labelWidth.value,
  }),
);
const bars = computed(() =>
  props.journal.lessons
    .filter(
      (lesson) =>
        lesson.status === 'CLOSED' &&
        !lesson.cancelled &&
        lesson.date >= props.from &&
        lesson.date <= props.to,
    )
    .map((lesson) => {
      const counts =
        props.metrics?.selectedStudentId === props.studentId
          ? props.metrics.series.find((point) => point.lessonId === lesson.id)
          : undefined;
      return { lesson, counts };
    })
    .filter(
      (bar): bar is typeof bar & { counts: NonNullable<typeof bar.counts> } => bar.counts != null,
    ),
);
const layout = computed(
  () =>
    new Map(
      journalChartBarLayout(
        bars.value.map((bar) => bar.lesson),
        axis.value,
        plotWidth.value,
        left.value,
      ).map((bar) => [bar.lessonId, bar]),
    ),
);
function x(index: number): number {
  return layout.value.get(bars.value[index]!.lesson.id)?.x ?? left.value;
}
function barWidth(index: number): number {
  return layout.value.get(bars.value[index]!.lesson.id)?.width ?? 0;
}
function size(percent: number): number {
  return (percent / 100) * (height - top - bottom);
}
function text(index: number): string {
  const bar = bars.value[index]!;
  return `${bar.lesson.date}, ${teacherLessonTypeLabel(bar.lesson.lessonType)}, ${bar.lesson.lessonNumber}-я пара: присутствия ${journalMetricPercent(bar.counts.present)}, присутствия и уважительные причины ${journalMetricPercent(bar.counts.presentOrExcused)}, уважительные причины ${journalMetricPercent(bar.counts.excused)}, отсутствия ${journalMetricPercent(bar.counts.absent)}; ${bar.counts.present.denominator} отметок`;
}
</script>
<template>
  <section
    class="teacher-journal__chart"
    aria-label="Столбчатый график посещаемости"
  >
    <p
      v-if="!bars.some((bar) => bar.counts.present.denominator)"
      role="status"
    >
      {{ metrics ? 'За выбранный период нет данных для графика.' : 'Метрики графика недоступны.' }}
    </p>
    <svg
      ref="plot"
      :viewBox="`0 0 ${width} ${height}`"
      class="teacher-journal__plot"
      role="group"
      aria-label="Зелёный — присутствовал, жёлтый — уважительная причина, красный — отсутствовал"
    >
      <!-- Keep exact SVG text: pinned Prettier pads this label with spaces. -->
      <!-- prettier-ignore -->
      <text
        ref="probe"
        class="teacher-journal__chart-probe"
        aria-hidden="true"
      >28 сент.</text>
      <g
        class="teacher-journal__chart-grid"
        aria-hidden="true"
      >
        <template
          v-for="tick in [0, 50, 100]"
          :key="tick"
        >
          <line
            :x1="left"
            :x2="width - right"
            :y1="top + ((100 - tick) / 100) * (height - top - bottom)"
            :y2="top + ((100 - tick) / 100) * (height - top - bottom)"
          />
          <!-- Keep exact SVG suffix text: pinned Prettier pads this interpolation. -->
          <!-- prettier-ignore -->
          <text
            :x="left - 6"
            :y="top + ((100 - tick) / 100) * (height - top - bottom) + 4"
            text-anchor="end"
          >{{ tick }}%</text>
        </template>
      </g>
      <g
        v-for="(bar, index) in bars"
        :key="bar.lesson.id"
        tabindex="0"
        role="img"
        :aria-label="text(index)"
        @focus="readout = text(index)"
        @click="readout = text(index)"
      >
        <title>{{ text(index) }}</title>
        <rect
          class="teacher-journal__bar--present"
          :x="x(index)"
          :y="height - bottom - size(bar.counts.present.percent)"
          :width="barWidth(index)"
          :height="size(bar.counts.present.percent)"
        />
        <rect
          class="teacher-journal__bar--excused"
          :x="x(index)"
          :y="
            height - bottom - size(bar.counts.present.percent + bar.counts.excused.percent)
          "
          :width="barWidth(index)"
          :height="size(bar.counts.excused.percent)"
        />
        <rect
          class="teacher-journal__bar--absent"
          :x="x(index)"
          :y="height - bottom - size(100)"
          :width="barWidth(index)"
          :height="size(bar.counts.absent.percent)"
        />
      </g>
      <g
        class="teacher-journal__chart-axis"
        aria-hidden="true"
      >
        <text
          v-for="(tick, index) in axis?.ticks ?? []"
          :key="tick.date"
          :x="left + tick.position * plotWidth"
          :y="height - 8"
          :text-anchor="
            index === 0 ? 'start' : index === (axis?.ticks.length ?? 0) - 1 ? 'end' : 'middle'
          "
        >
          {{ tick.label }}
        </text>
      </g>
    </svg>
    <p
      v-if="readout"
      role="status"
    >
      {{ readout }}
    </p>
  </section>
</template>
