<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { displayPercent, type AttendanceGraphPoint, type AttendanceGraphRange } from './attendance-view-model'
import { initialPeriodPage, periodState, shortDate, weekday, type AttendancePeriod } from './attendance-periods'
const props = withDefaults(defineProps<{ points: readonly (AttendanceGraphPoint | AttendancePeriod)[]; range?: AttendanceGraphRange; serverNow?: string | undefined }>(), { range: 'days', serverNow: '' })
const emit = defineEmits<{ 'change-range': [range: AttendanceGraphRange] }>()
const columns = ref<HTMLElement | null>(null)
const hasHorizontalOverflow = ref(false)
let observer: ResizeObserver | null = null
function measureColumns(): void { hasHorizontalOverflow.value = Boolean(columns.value && columns.value.scrollWidth > columns.value.clientWidth) }
onMounted(() => {
  observer = new ResizeObserver(measureColumns)
  if (columns.value) observer.observe(columns.value)
  measureColumns()
})
onBeforeUnmount(() => observer?.disconnect())
const page = ref(0)
const selectedId = ref<string | null>(null)
const size = computed(() => props.range === 'days' ? 7 : 6)
const pageCount = computed(() => Math.max(1, Math.ceil(props.points.length / size.value)))
const visible = computed(() => props.points.slice(page.value * size.value, (page.value + 1) * size.value))
watch(visible, () => { void nextTick(measureColumns) })
const selected = computed(() => visible.value.find((point) => point.id === selectedId.value) ?? visible.value[0] ?? null)
watch(() => [props.points, props.range, props.serverNow] as const, () => { page.value = initialPeriodPage(props.points, props.range, props.serverNow); selectedId.value = null }, { immediate: true })
function changePage(delta: number): void { page.value = Math.max(0, Math.min(pageCount.value - 1, page.value + delta)); selectedId.value = visible.value[0]?.id ?? null }
function keyboard(event: KeyboardEvent, index: number): void {
  const offset = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0
  if (!offset && event.key !== 'Home' && event.key !== 'End') return
  event.preventDefault()
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? visible.value.length - 1 : Math.max(0, Math.min(visible.value.length - 1, index + offset))
  selectedId.value = visible.value[next]?.id ?? null
  const target = (event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('button')[next]
  target?.focus()
  target?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}
function percentage(point: AttendanceGraphPoint, metric: 'present' | 'excused' | 'absent'): number { return point.state !== 'DATA' || !point.metrics.held ? 0 : Math.min(100, Math.max(0, point.metrics[metric].percent ?? 0)) }
function hasMarks(point: AttendanceGraphPoint): boolean { return point.state === 'DATA' && point.metrics.held > 0 && point.metrics.present.percent !== null && point.metrics.absent.percent !== null && point.metrics.excused.percent !== null }
function description(point: AttendanceGraphPoint): string { return `${point.label}: ${periodState(point)}${point.state === 'DATA' ? `, был ${displayPercent(point.metrics.present.percent)}, уважительная причина ${displayPercent(point.metrics.excused.percent)}, не был ${displayPercent(point.metrics.absent.percent)}` : ''}` }
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
        v-for="item in (['days', 'weeks'] as const)"
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
        ‹
      </button>
      <span role="status">{{ visible[0] ? shortDate(visible[0].dateFrom) : 'Нет периодов' }}{{ visible.length ? ` — ${shortDate(visible[visible.length - 1]!.dateTo)}` : '' }}</span>
      <button
        type="button"
        aria-label="Следующие периоды"
        :disabled="page + 1 >= pageCount"
        @click="changePage(1)"
      >
        ›
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
        aria-label="Выбери период для подробностей"
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
            :class="{ 'attendance-graph__stack--unknown': !hasMarks(point) }"
            aria-hidden="true"
          >
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
            <span
              v-if="!hasMarks(point)"
              class="attendance-graph__neutral-label"
            >{{ point.state === 'FUTURE' ? '›' : '—' }}</span>
          </span>
          <span class="attendance-graph__date">{{ range === 'days' ? weekday(point.dateFrom) : shortDate(point.dateFrom) }}<br v-if="range === 'days'">{{ range === 'days' ? point.dateFrom.slice(8) : '' }}</span>
        </button>
      </div>
    </div>
    <p
      v-if="hasHorizontalOverflow"
      class="attendance-graph__scroll-hint"
      role="status"
    >
      Листай график по горизонтали, чтобы увидеть все периоды.
    </p>
    <ul
      class="attendance-graph__legend"
      aria-label="Легенда"
    >
      <li data-tone="present">
        + Был
      </li><li data-tone="excused">
        у Уважительная
      </li><li data-tone="absent">
        н Не был
      </li><li data-tone="future">
        › Будущий период
      </li><li data-tone="no-data">
        — Нет данных
      </li>
    </ul>
    <section
      v-if="selected"
      class="attendance-graph__detail"
      aria-label="Выбранный период"
      aria-live="polite"
    >
      <h3>{{ shortDate(selected.dateFrom) }}{{ selected.dateTo !== selected.dateFrom ? ` — ${shortDate(selected.dateTo)}` : '' }}</h3>
      <p>{{ periodState(selected) }}</p>
      <template v-if="!('missing' in selected && selected.missing) && !('outsideSemester' in selected && selected.outsideSemester)">
        <p>Проведено {{ selected.metrics.held }} · Запланировано {{ selected.metrics.planned }}</p>
        <dl><div><dt>+ Был</dt><dd>{{ selected.metrics.present.count }} · {{ displayPercent(selected.metrics.present.percent) }}</dd></div><div><dt>у Уважительная причина</dt><dd>{{ selected.metrics.excused.count }} · {{ displayPercent(selected.metrics.excused.percent) }}</dd></div><div><dt>н Не был</dt><dd>{{ selected.metrics.absent.count }} · {{ displayPercent(selected.metrics.absent.percent) }}</dd></div></dl>
      </template>
    </section>
  </section>
</template>
