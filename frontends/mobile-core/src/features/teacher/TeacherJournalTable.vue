<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue';
import type { TeacherJournalCell, TeacherJournalResponse } from './teacher-client';
import { journalMark, journalMetricPercent, journalMetricLabel } from './teacher-journal-model';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import { journalThreshold, journalThresholdResult } from './teacher-journal-threshold';
import { createTeacherJournalZoom } from './teacher-journal-zoom';
const props = defineProps<{
  journal: TeacherJournalResponse;
  contextKey?: string;
}>();
const emit = defineEmits<{
  'open-lesson': [lessonId: number, page: number];
  'open-excuse': [requestId: string];
  'open-manual-excuse': [lessonId: number, studentId: number];
}>();
const lessons = computed(() =>
  props.journal.lessons.filter(
    (lesson) =>
      (lesson.status === 'CLOSED' && !lesson.cancelled) ||
      (lesson.status === 'CANCELLED' && lesson.cancelled),
  ),
);
const rows = computed(() =>
  props.journal.students.map((student) => ({
    ...student,
    cellsById: new Map(student.cells.map((cell) => [cell.lessonId, cell])),
    metrics: props.journal.metrics?.students.find((row) => row.studentId === student.studentId),
  })),
);
const selectedStudentId = ref<number | null>(null);
const selectedLessonId = ref<number | null>(null);
watch(
  () => props.contextKey,
  () => {
    selectedStudentId.value = null;
    selectedLessonId.value = null;
    thresholdInputs.value = emptyThresholds();
    zoom.reset();
  },
  { flush: 'sync' },
);
watch(
  [rows, lessons],
  () => {
    if (!rows.value.some((row) => row.studentId === selectedStudentId.value))
      selectedStudentId.value = null;
    if (!lessons.value.some((lesson) => lesson.id === selectedLessonId.value))
      selectedLessonId.value = null;
  },
  { flush: 'sync' },
);
function selectStudent(id: number): void {
  selectedStudentId.value = selectedStudentId.value === id ? null : id;
}
function selectLesson(id: number): void {
  selectedLessonId.value = selectedLessonId.value === id ? null : id;
}
function symbol(cell: TeacherJournalCell | undefined): string {
  const mark = journalMark(cell);
  return mark === 'present' ? '+' : mark === 'excused' ? 'У' : mark === 'absent' ? 'Н' : '—';
}
const metricColumns = [
  { key: 'present', label: 'Присутствия', symbols: ['present'] },
  {
    key: 'presentOrExcused',
    label: 'Присутствия и уважительные причины',
    symbols: ['present', 'excused'],
  },
  { key: 'excused', label: 'Уважительные причины', symbols: ['excused'] },
  { key: 'absent', label: 'Отсутствия', symbols: ['absent'] },
] as const;
type MetricKey = (typeof metricColumns)[number]['key'];
function emptyThresholds(): Record<MetricKey, string> {
  return { present: '', presentOrExcused: '', excused: '', absent: '' };
}
const thresholdInputs = ref(emptyThresholds());
const thresholds = computed(
  () =>
    Object.fromEntries(
      metricColumns.map(({ key }) => [key, journalThreshold(thresholdInputs.value[key])]),
    ) as Record<MetricKey, ReturnType<typeof journalThreshold>>,
);
const tableId = useId();
const tableScroll = ref<HTMLElement | null>(null);
const table = ref<HTMLTableElement | null>(null);
const nameHeading = ref<HTMLTableCellElement | null>(null);
const zoom = createTeacherJournalZoom(
  () => tableScroll.value,
  () => table.value,
  () => nameHeading.value,
);
function updateThreshold(key: MetricKey, event: Event): void {
  thresholdInputs.value[key] = (event.target as HTMLInputElement).value;
}
function metricLabel(
  label: string,
  metric: Parameters<typeof journalThresholdResult>[0],
  key: MetricKey,
): string {
  const result = journalThresholdResult(metric, thresholds.value[key]);
  const comparison =
    result === 'meets' ? 'порог достигнут' : result === 'below' ? 'ниже порога' : '';
  return (
    journalMetricLabel(label, metric) +
    (comparison ? `; ${comparison} ${thresholdInputs.value[key]} %` : '')
  );
}
function compactLessonType(value: string): string {
  return (
    ({ lecture: 'ЛК', lab: 'ЛЗ', laboratory: 'ЛЗ', practice: 'ПЗ' } as Record<string, string>)[
      value.toLowerCase()
    ] ?? '—'
  );
}
function dayStart(index: number): boolean {
  return index > 0 && lessons.value[index - 1]?.date !== lessons.value[index]?.date;
}
function compactName(name: string): string {
  if (name.length <= 25) return name;
  const [surname, ...parts] = name.trim().split(/\s+/);
  return [surname, ...parts.map((part) => `${part[0]}.`)].join(' ');
}
function openLesson(id: number): void {
  const index = props.journal.lessons.findIndex((lesson) => lesson.id === id);
  if (index >= 0 && !props.journal.lessons[index]?.cancelled)
    emit('open-lesson', id, Math.floor(index / Math.max(1, props.journal.pageSize)));
}
function label(cell: TeacherJournalCell | undefined): string {
  const mark = journalMark(cell);
  const value =
    mark === 'present'
      ? 'Присутствовал'
      : mark === 'excused'
        ? 'Уважительная причина'
        : mark === 'absent'
          ? 'Отсутствовал'
          : cell
            ? 'Нет данных'
            : 'Нет строки списка на эту дату';
  return cell?.pendingTicket ? `${value}; заявление ожидает решения` : value;
}
function description(cell: TeacherJournalCell | undefined): string | undefined {
  return cell?.autoAbsent && journalMark(cell) === 'absent'
    ? 'Отсутствие отмечено автоматически при закрытии пары'
    : undefined;
}
function pendingAbsence(cell: TeacherJournalCell | undefined): boolean {
  return Boolean(cell?.pendingTicket && journalMark(cell) === 'absent');
}
function open(cell: TeacherJournalCell | undefined, studentId: number): void {
  if (!cell || journalMark(cell) !== 'excused') return;
  if (cell.ticketId) emit('open-excuse', cell.ticketId);
  else emit('open-manual-excuse', cell.lessonId, studentId);
}
function date(value: string): string {
  return new Intl.DateTimeFormat('ru', {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'UTC',
  }).format(new Date(value + 'T12:00:00Z'));
}
</script>
<template>
  <div class="teacher-journal__table-panel">
    <p
      :id="`${tableId}-instructions`"
      class="teacher-journal__sr"
    >
      Прокручивай таблицу стрелками. Клавиши плюс и минус меняют масштаб таблицы, ноль сбрасывает.
    </p>
    <div
      :id="tableId"
      ref="tableScroll"
      class="teacher-journal__table-scroll"
      tabindex="0"
      role="region"
      aria-label="Журнал посещаемости"
      :aria-describedby="`${tableId}-instructions`"
      @keydown="zoom.keydown"
      @touchstart="zoom.touchStart"
      @touchmove="zoom.touchMove"
      @touchend="zoom.touchEnd"
      @touchcancel="zoom.touchEnd"
      @click.capture="zoom.captureClick"
    >
      <table
        ref="table"
        class="teacher-journal__table"
      >
        <caption class="teacher-journal__sr">
          Посещаемость прошедших занятий
        </caption>
        <thead>
          <tr>
            <th
              ref="nameHeading"
              scope="col"
              class="teacher-journal__name"
            >
              ФИО
            </th>
            <th
              v-for="(lesson, index) in lessons"
              :key="lesson.id"
              :data-day-start="dayStart(index)"
              scope="col"
              :data-selected-column="selectedLessonId === lesson.id"
              :data-cancelled="lesson.cancelled"
              :title="lesson.cancellationReason ?? undefined"
            >
              <button
                class="teacher-journal__lesson-heading teacher-journal__date-heading"
                type="button"
                :aria-label="`Выделить ${lesson.lessonNumber}-ю пару, ${date(lesson.date)}, ${teacherLessonTypeLabel(lesson.lessonType)}`"
                :aria-pressed="selectedLessonId === lesson.id"
                @click="selectLesson(lesson.id)"
              >
                <time :datetime="lesson.date">{{ date(lesson.date) }}</time>
                <span class="teacher-journal__sr">{{ lesson.lessonNumber }}-я пара</span>
              </button>
            </th>
            <th
              v-for="metric in metricColumns"
              :key="metric.key"
              scope="col"
              class="teacher-journal__metric-heading"
              :aria-label="metric.label + ', проценты'"
            >
              <span
                class="teacher-journal__metric-symbols"
                aria-hidden="true"
              >
                <i
                  v-for="mark in metric.symbols"
                  :key="mark"
                  class="teacher-journal__metric-mark"
                  :class="`teacher-journal__metric-mark--${mark}`"
                >{{ mark === 'present' ? '+' : mark === 'excused' ? 'У' : 'Н' }}</i>
              </span>
            </th>
          </tr>
          <tr class="teacher-journal__type-row">
            <th class="teacher-journal__name">
              <span class="teacher-journal__sr">Тип занятия</span>
            </th>
            <th
              v-for="(lesson, index) in lessons"
              :key="lesson.id"
              :data-day-start="dayStart(index)"
              scope="col"
              :data-selected-column="selectedLessonId === lesson.id"
            >
              <button
                v-if="!lesson.cancelled"
                class="teacher-journal__lesson-heading teacher-journal__type-heading"
                type="button"
                :aria-label="`Открыть ${lesson.lessonNumber}-ю пару, ${date(lesson.date)}, ${teacherLessonTypeLabel(lesson.lessonType)}`"
                @click="openLesson(lesson.id)"
              >
                {{ compactLessonType(lesson.lessonType) }}
              </button>
              <span
                v-else
                class="teacher-journal__cancelled-label"
                :aria-label="`${teacherLessonTypeLabel(lesson.lessonType)}, отменена`"
              >{{ compactLessonType(lesson.lessonType) }} · Отменена</span>
            </th>
            <th
              v-for="metric in metricColumns"
              :key="metric.key"
              class="teacher-journal__metric-heading"
              aria-hidden="true"
            />
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="student in rows"
            :key="student.studentId"
            :data-selected-row="selectedStudentId === student.studentId"
          >
            <th
              scope="row"
              class="teacher-journal__name"
            >
              <button
                type="button"
                class="teacher-journal__student-heading"
                :aria-label="student.displayName"
                :title="student.displayName"
                :aria-pressed="selectedStudentId === student.studentId"
                @click="selectStudent(student.studentId)"
              >
                {{ compactName(student.displayName) }}
              </button>
            </th><td
              v-for="(lesson, index) in lessons"
              :key="lesson.id"
              :data-day-start="dayStart(index)"
              :data-selected-column="selectedLessonId === lesson.id"
            >
              <span
                v-if="lesson.cancelled"
                class="teacher-journal__mark teacher-journal__mark--cancelled"
                role="img"
                aria-label="Отменена"
              ><span
                class="teacher-journal__cell-circle"
                aria-hidden="true"
              >—</span></span><button
                v-else-if="journalMark(student.cellsById.get(lesson.id)) === 'excused'"
                type="button"
                class="teacher-journal__mark teacher-journal__mark--excused"
                :aria-description="description(student.cellsById.get(lesson.id))"
                :title="description(student.cellsById.get(lesson.id))"
                :aria-label="`${student.displayName}: ${label(student.cellsById.get(lesson.id))}, ${date(lesson.date)}, ${teacherLessonTypeLabel(lesson.lessonType)}`"
                @click="open(student.cellsById.get(lesson.id), student.studentId)"
              >
                <span
                  class="teacher-journal__cell-circle"
                  aria-hidden="true"
                >У</span>
              </button><span
                v-else
                role="img"
                class="teacher-journal__mark"
                :class="`teacher-journal__mark--${journalMark(student.cellsById.get(lesson.id)) ?? 'none'}`"
                :data-auto-absent="Boolean(description(student.cellsById.get(lesson.id)))"
                :data-pending-ticket="pendingAbsence(student.cellsById.get(lesson.id))"
                :aria-description="description(student.cellsById.get(lesson.id))"
                :title="description(student.cellsById.get(lesson.id))"
                :aria-label="label(student.cellsById.get(lesson.id))"
              ><span
                class="teacher-journal__cell-circle"
                aria-hidden="true"
              >{{ symbol(student.cellsById.get(lesson.id)) }}</span></span>
            </td>
            <td
              v-for="metric in metricColumns"
              :key="metric.key"
              class="teacher-journal__percent"
              :data-threshold="
                journalThresholdResult(student.metrics?.[metric.key], thresholds[metric.key])
              "
              :aria-label="metricLabel(metric.label, student.metrics?.[metric.key], metric.key)"
            >
              {{ journalMetricPercent(student.metrics?.[metric.key]) }}
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th
              class="teacher-journal__name"
              scope="row"
            >
              Порог, %
            </th>
            <td
              v-if="lessons.length"
              :colspan="lessons.length"
              aria-hidden="true"
            />
            <td
              v-for="metric in metricColumns"
              :key="metric.key"
              class="teacher-journal__threshold"
            >
              <label
                class="teacher-journal__sr"
                :for="`${tableId}-${metric.key}-input`"
              >{{ `Порог: ${metric.label}, проценты` }}</label>
              <input
                :id="`${tableId}-${metric.key}-input`"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                class="teacher-journal__threshold-input"
                :value="thresholdInputs[metric.key]"
                :aria-label="`Порог: ${metric.label}, проценты`"
                :aria-invalid="thresholds[metric.key].state === 'invalid'"
                :aria-describedby="`${tableId}-${metric.key}-hint`"
                placeholder="—"
                @input="updateThreshold(metric.key, $event)"
              >
              <span
                :id="`${tableId}-${metric.key}-hint`"
                :class="
                  thresholds[metric.key].state === 'invalid'
                    ? 'teacher-journal__threshold-error'
                    : 'teacher-journal__sr'
                "
              >
                {{
                  thresholds[metric.key].state === 'invalid'
                    ? 'Введи число от 0 до 100'
                    : 'От 0 до 100. Пустое поле отключает подсветку.'
                }}
              </span>
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
    <p class="teacher-journal__legend">
      <span
        class="teacher-journal__metric-mark teacher-journal__metric-mark--absent teacher-journal__pending-ring"
        aria-hidden="true"
      >
        Н
      </span>
      Заявление ожидает решения
    </p>
  </div>
</template>
