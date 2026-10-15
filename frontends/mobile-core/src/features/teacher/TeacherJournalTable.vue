<script setup lang="ts">
import { computed } from 'vue';
import type { TeacherJournalCell, TeacherJournalResponse } from './teacher-client';
import { journalCounts, journalMark, journalPercent } from './teacher-journal-model';
import { teacherLessonTypeLabel } from './teacher-ui-format';
const props = defineProps<{ journal: TeacherJournalResponse }>();
const emit = defineEmits<{
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
const lessonIds = computed(
  () => new Set(lessons.value.filter((lesson) => !lesson.cancelled).map((lesson) => lesson.id)),
);
const rows = computed(() =>
  props.journal.students.map((student) => ({
    ...student,
    cellsById: new Map(student.cells.map((cell) => [cell.lessonId, cell])),
    counts: journalCounts(student.cells, lessonIds.value),
  })),
);
function symbol(cell: TeacherJournalCell | undefined): string {
  const mark = journalMark(cell);
  return mark === 'present' ? '+' : mark === 'excused' ? 'у' : mark === 'absent' ? 'н' : '—';
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
  <div
    class="teacher-journal__table-scroll"
    tabindex="0"
    role="region"
    aria-label="Журнал посещаемости. Таблицу можно прокрутить вправо."
  >
    <table class="teacher-journal__table">
      <caption class="teacher-journal__sr">
        Посещаемость прошедших занятий
      </caption>
      <thead>
        <tr>
          <th
            scope="col"
            class="teacher-journal__name"
          >
            ФИО
          </th><th
            scope="col"
            class="teacher-journal__percent-heading"
          >
            %
            <span class="teacher-journal__sr">Присутствия, уважительные причины, отсутствия</span>
          </th><th
            v-for="lesson in lessons"
            :key="lesson.id"
            scope="col"
            :data-cancelled="lesson.cancelled"
            :title="lesson.cancellationReason ?? undefined"
          >
            <time :datetime="lesson.date">{{ date(lesson.date) }}</time><span>{{ teacherLessonTypeLabel(lesson.lessonType) }}</span><span class="teacher-journal__sr">{{ lesson.lessonNumber }}-я пара</span><span
              v-if="lesson.cancelled"
              class="teacher-journal__cancelled-label"
            >Отменена</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="student in rows"
          :key="student.studentId"
        >
          <th
            scope="row"
            class="teacher-journal__name"
          >
            {{ student.displayName }}
          </th><td class="teacher-journal__percent">
            <span
              class="teacher-journal__present"
              :aria-label="`Присутствия: ${student.counts.present} из ${student.counts.denominator}`"
            ><span
              class="teacher-journal__metric-mark teacher-journal__metric-mark--present"
              aria-hidden="true"
            />{{ journalPercent(student.counts.present, student.counts.denominator) }}</span><span
              class="teacher-journal__excused"
              :aria-label="`Уважительные причины: ${student.counts.excused} из ${student.counts.denominator}`"
            ><span
              class="teacher-journal__metric-mark teacher-journal__metric-mark--excused"
              aria-hidden="true"
            />{{ journalPercent(student.counts.excused, student.counts.denominator) }}</span><span
              class="teacher-journal__absent"
              :aria-label="`Отсутствия: ${student.counts.absent} из ${student.counts.denominator}`"
            ><span
              class="teacher-journal__metric-mark teacher-journal__metric-mark--absent"
              aria-hidden="true"
            />{{ journalPercent(student.counts.absent, student.counts.denominator) }}</span>
          </td><td
            v-for="lesson in lessons"
            :key="lesson.id"
          >
            <span
              v-if="lesson.cancelled"
              class="teacher-journal__mark teacher-journal__mark--cancelled"
              aria-label="Отменена"
            ><span
              class="teacher-journal__cell-circle"
              aria-hidden="true"
            >—</span></span><button
              v-else-if="journalMark(student.cellsById.get(lesson.id)) === 'excused'"
              type="button"
              class="teacher-journal__mark teacher-journal__mark--excused"
              :data-pending-ticket="Boolean(student.cellsById.get(lesson.id)?.pendingTicket)"
              :aria-description="description(student.cellsById.get(lesson.id))"
              :title="description(student.cellsById.get(lesson.id))"
              :aria-label="`${student.displayName}: ${label(student.cellsById.get(lesson.id))}, ${date(lesson.date)}, ${teacherLessonTypeLabel(lesson.lessonType)}`"
              @click="open(student.cellsById.get(lesson.id), student.studentId)"
            >
              <span
                class="teacher-journal__cell-circle"
                aria-hidden="true"
              >у</span>
              <span
                v-if="student.cellsById.get(lesson.id)?.pendingTicket"
                class="teacher-journal__pending"
                aria-hidden="true"
              >Ожидает решения</span>
            </button><span
              v-else
              class="teacher-journal__mark"
              :class="`teacher-journal__mark--${journalMark(student.cellsById.get(lesson.id)) ?? 'none'}`"
              :data-auto-absent="Boolean(description(student.cellsById.get(lesson.id)))"
              :data-pending-ticket="Boolean(student.cellsById.get(lesson.id)?.pendingTicket)"
              :aria-description="description(student.cellsById.get(lesson.id))"
              :title="description(student.cellsById.get(lesson.id))"
              :aria-label="label(student.cellsById.get(lesson.id))"
            ><span
               class="teacher-journal__cell-circle"
               aria-hidden="true"
             >{{ symbol(student.cellsById.get(lesson.id)) }}</span>
              <span
                v-if="student.cellsById.get(lesson.id)?.pendingTicket"
                class="teacher-journal__pending"
                aria-hidden="true"
              >Ожидает решения</span></span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
