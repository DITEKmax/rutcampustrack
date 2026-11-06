<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  toTeacherJournalReportRequest,
  teacherJournalTypes,
  type TeacherJournalQuery,
  type TeacherJournalResponse,
  type TeacherJournalCell,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import {
  shortDate,
  useTeacherResource,
  useTeacherScope,
  useTeacherSelection,
} from './teacher-state';
import TeacherRegion from './TeacherRegion.vue';
import TeacherFilters from './TeacherFilters.vue';
import TeacherNotice from './TeacherNotice.vue';
import TeacherMark from './TeacherMark.vue';
import { teacherAttendanceLabel } from './teacher-mark';
import TeacherExcusePanel from './TeacherExcusePanel.vue';
import TeacherExport from './TeacherExport.vue';
import { useJournalPagination } from './use-journal-pagination';
import WebDateField from '../../shared/WebDateField.vue';
import type { TeacherRequestSource } from './teacher-evidence';

const scope = useTeacherScope();
const selection = useTeacherSelection(scope);
const journal = useTeacherResource<TeacherJournalResponse>(scope);
const loadedQuery = shallowRef<TeacherJournalQuery | null>(null);
const pagination = useJournalPagination(scope, journal.data, journal.loading, loadedQuery);
const { region: journalRegion, edge: journalEdge } = pagination;
const from = ref(''),
  to = ref('');
const fromField = ref<InstanceType<typeof WebDateField> | null>(null),
  toField = ref<InstanceType<typeof WebDateField> | null>(null);
const fromValid = ref(false),
  toValid = ref(false),
  fromEmpty = ref(true),
  toEmpty = ref(true);
const datesValid = computed(() => (fromValid.value || fromEmpty.value) && (toValid.value || toEmpty.value));
async function dateValidity(field: 'from' | 'to', valid: boolean) {
  (field === 'from' ? fromValid : toValid).value = valid;
  // Optional blank and invalid draft both emit false. A calendar clear of an
  // already-empty model emits no change, so read its rendered draft after validation.
  await nextTick();
  const root = (field === 'from' ? fromField : toField).value?.$el;
  const input = root instanceof HTMLElement ? root.querySelector<HTMLInputElement>('.web-date-input') : null;
  if (input) (field === 'from' ? fromEmpty : toEmpty).value = input.value === '';
}
function dateInput(field: 'from' | 'to', event: Event) {
  if (!(event.target instanceof HTMLInputElement)) return;
  (field === 'from' ? fromEmpty : toEmpty).value = event.target.value === '';
  scope.dirty.value = true;
}
function dateChanged(field: 'from' | 'to', value: string) {
  (field === 'from' ? fromEmpty : toEmpty).value = value === '';
  scope.dirty.value = true;
}
const pastOnly = ref(false);
const appliedPeriod = shallowRef({ from: '', to: '', pastOnly: false });
const ticket = shallowRef<TeacherRequestSource | null>(null);
const ticketTrigger = shallowRef<HTMLElement | null>(null);
const visibleTicket = computed(() => {
  const target = ticket.value;
  if (!target || !journal.data.value?.lessons.some(lesson => lesson.id === target.lessonId)) return null;
  const student = journal.data.value.students.find(row => row.studentId === target.studentId);
  return student?.cells.some(cell => cell.lessonId === target.lessonId && cell.ticketId === target.ticketId)
    ? target
    : null;
});
watch(visibleTicket, value => { if (!value) ticket.value = null; });
function openTicket(cell: TeacherJournalCell | undefined, lessonId: number, studentId: number, event: MouseEvent) {
  if (!cell?.ticketId || cell.lessonId !== lessonId) return;
  ticketTrigger.value = event.currentTarget as HTMLElement;
  ticket.value = { ticketId: cell.ticketId, lessonId, studentId };
}
type JournalPosition = { studentId: number | null; lessonId: number };
const hovered = shallowRef<JournalPosition | null>(null);
const focused = shallowRef<JournalPosition | null>(null);
const active = computed(() => hovered.value ?? focused.value);
function focusLesson(lessonId: number, studentId: number | null, event: FocusEvent) {
  hovered.value = null;
  focused.value = { studentId, lessonId };
  const target = event.target as HTMLElement;
  const sticky = journalRegion.value?.querySelector<HTMLElement>('.teacher-sticky-name');
  if (sticky && target.getBoundingClientRect().left < sticky.getBoundingClientRect().right)
    target.scrollIntoView({ block: 'nearest', inline: 'start' });
}
const rangeError = computed(() =>
  datesValid.value && (from.value || to.value) && (!from.value || !to.value || from.value > to.value)
    ? 'Укажи начало и конец периода в правильном порядке.'
    : '',
);
const query = computed<TeacherJournalQuery | null>(() =>
  selection.ready.value && scope.semester.value
    ? {
        semesterId: scope.semester.value.id,
        groupId: selection.groupId.value,
        subjectId: selection.subjectId.value,
        lessonTypes: [...selection.lessonTypes.value],
        pastOnly: appliedPeriod.value.pastOnly,
        includeMetrics: true,
        pageSize: 30,
        ...(appliedPeriod.value.from && appliedPeriod.value.to
          ? { dateFrom: appliedPeriod.value.from, dateTo: appliedPeriod.value.to }
          : {}),
      }
    : null,
);
const appliedLabel = computed(() => {
  const current = loadedQuery.value;
  if (!current || !journal.data.value) return '';
  const period = current.dateFrom && current.dateTo
    ? `${shortDate(current.dateFrom)} — ${shortDate(current.dateTo)}`
    : 'весь семестр';
  return `Период: ${period}${current.pastOnly ? ' · только прошедшие пары' : ''}`;
});
let loadRevision = 0;
function load(background = false) {
  const run = ++loadRevision;
  const previous = loadedQuery.value;
  const retain = background === true && previous !== null &&
    previous.semesterId === scope.semester.value?.id &&
    previous.groupId === selection.groupId.value && previous.subjectId === selection.subjectId.value &&
    JSON.stringify(teacherJournalTypes(previous)) === JSON.stringify(selection.lessonTypes.value);
  // A live hint refreshes the applied period, never submits the date-field draft.
  const requested = retain ? previous : query.value;
  if (!retain) {
    ticket.value = null;
    loadedQuery.value = null;
    hovered.value = null;
    focused.value = null;
  }
  if (!requested) {
    journal.clear();
    return;
  }
  void journal.load(async () => {
    const data = retain ? await pagination.refreshWindow(requested) : await scope.api.journal(requested);
    scope.transport.assertCurrent();
    if (run === loadRevision && scope.current()) loadedQuery.value = requested;
    return data;
  }, undefined, retain);
}
function applyPeriod() {
  if (!datesValid.value || rangeError.value || !selection.ready.value || !scope.semester.value) return;
  scope.dirty.value = false;
  const next = { from: from.value, to: to.value, pastOnly: pastOnly.value };
  const previous = appliedPeriod.value;
  if (loadedQuery.value && !journal.error.value && next.from === previous.from &&
      next.to === previous.to && next.pastOnly === previous.pastOnly) return;
  appliedPeriod.value = next;
  load();
}

watch(
  () => scope.refreshed.value,
  () => {
    selection.reconcile();
    const semester = scope.semester.value;
    const period = appliedPeriod.value;
    if (semester && period.from && (period.from < semester.dateFrom || period.to > semester.dateTo))
      appliedPeriod.value = { from: '', to: '', pastOnly: period.pastOnly };
    load(scope.backgroundRefresh.value);
  },
);
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      loadRevision++;
      journal.clear();
      loadedQuery.value = null;
      ticket.value = null;
    }
  },
);
const exportRequest = computed(() =>
  journal.data.value?.lessons.length &&
  loadedQuery.value &&
  !journal.error.value &&
  !scope.loading.value &&
  !journal.loading.value &&
  !pagination.loading.value &&
  !pagination.refreshing.value
    ? (format: Parameters<typeof toTeacherJournalReportRequest>[1]) =>
        toTeacherJournalReportRequest(loadedQuery.value!, format)
    : null,
);
function metric(studentId: number, column: 'present' | 'presentOrExcused' | 'excused' | 'absent') {
  const value = journal.data.value?.metrics?.students.find(
    (student) => student.studentId === studentId,
  )?.[column];
  return value
    ? `${value.percent.toLocaleString('ru', { maximumFractionDigits: 1 })}% · ${value.numerator}/${value.denominator}`
    : '—';
}
</script>
<template>
  <TeacherRegion
    :scope="scope"
    screen="WEB-TEACHER-ATTENDANCE"
  >
    <h1 class="web-sr-only">
      Журнал посещаемости
    </h1>
    <TeacherNotice
      :loading="scope.loading.value && !scope.catalog.value"
      :error="scope.error.value"
      @retry="scope.load"
    />
    <template v-if="scope.catalog.value">
      <TeacherFilters
        :selection="selection"
        :disabled="scope.loading.value"
        @change="load"
      />
      <form
        class="teacher-toolbar teacher-period"
        @submit.prevent="applyPeriod"
      >
        <WebDateField
          ref="fromField"
          v-model="from"
          label="С даты"
          :min="scope.semester.value?.dateFrom"
          :max="scope.semester.value?.dateTo"
          @input="dateInput('from', $event)"
          @change="dateChanged('from', $event)"
          @validity="dateValidity('from', $event)"
        />
        <WebDateField
          ref="toField"
          v-model="to"
          label="По дату"
          :min="scope.semester.value?.dateFrom"
          :max="scope.semester.value?.dateTo"
          @input="dateInput('to', $event)"
          @change="dateChanged('to', $event)"
          @validity="dateValidity('to', $event)"
        />
        <label class="teacher-checkbox"><input
          v-model="pastOnly"
          type="checkbox"
          @change="scope.dirty.value = true"
        >
          Только прошедшие пары</label><button
          class="web-button web-button-primary"
          type="submit"
          :disabled="scope.loading.value || !selection.ready.value"
        >
          Применить
        </button>
      </form>
      <p
        v-if="rangeError"
        class="web-error"
        role="alert"
      >
        {{ rangeError }}
      </p>
      <p
        v-if="appliedLabel"
        class="web-caption"
      >
        {{ appliedLabel }}
      </p>
      <TeacherNotice
        :loading="journal.loading.value && !journal.data.value"
        :error="journal.error.value"
        :empty="
          !selection.ready.value
            ? 'Выбери хотя бы один тип занятия.'
            : journal.data.value?.lessons.length === 0
              ? 'За этот период пар нет.'
              : journal.data.value?.students.length === 0
                ? 'В журнале пока нет студентов.'
                : ''
        "
        @retry="load"
      />
      <div
        v-if="journal.data.value?.lessons.length"
        ref="journalRegion"
        class="teacher-table-scroll teacher-journal"
        role="region"
        tabindex="0"
        aria-label="Журнал. Прокрути, чтобы увидеть все пары"
        @mouseleave="hovered = null"
        @focusout="focused = null"
      >
        <table class="teacher-table">
          <caption class="web-sr-only">
            Посещаемость, только чтение
          </caption><thead>
            <tr class="teacher-journal-dates">
              <th
                class="teacher-sticky-name"
                scope="col"
                rowspan="3"
              >
                Студенты
              </th>
              <th
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                scope="col"
                class="teacher-journal-slot"
                :class="{ 'teacher-cross': active?.lessonId === lesson.id }"
                @mouseenter="hovered = { studentId: null, lessonId: lesson.id }"
                @mouseleave="hovered = null"
              >
                <RouterLink
                  :to="{ name: 'teacher-lesson', params: { lessonId: lesson.id } }"
                  @focus="focusLesson(lesson.id, null, $event)"
                >
                  {{ shortDate(lesson.date) }}
                </RouterLink>
                <span
                  v-if="lesson.cancelled"
                  class="web-caption"
                > · отменена</span>
              </th>
              <th
                scope="col"
                rowspan="3"
              >
                Присутствия
              </th>
              <th
                scope="col"
                rowspan="3"
              >
                Присутствия и ув.
              </th>
              <th
                scope="col"
                rowspan="3"
              >
                Уважительные
              </th>
              <th
                ref="journalEdge"
                scope="col"
                rowspan="3"
              >
                Отсутствия
              </th>
            </tr>
            <tr class="teacher-journal-times">
              <th
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                scope="col"
                class="teacher-journal-slot"
                :class="{ 'teacher-cross': active?.lessonId === lesson.id }"
                @mouseenter="hovered = { studentId: null, lessonId: lesson.id }"
                @mouseleave="hovered = null"
              >
                {{ lesson.startsAt?.slice(0, 5) ?? `Пара ${lesson.lessonNumber}` }}<span v-if="lesson.endsAt">–{{ lesson.endsAt.slice(0, 5) }}</span>
              </th>
            </tr>
            <tr class="teacher-journal-types">
              <th
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                scope="col"
                class="teacher-journal-slot"
                :class="{ 'teacher-cross': active?.lessonId === lesson.id }"
                @mouseenter="hovered = { studentId: null, lessonId: lesson.id }"
                @mouseleave="hovered = null"
              >
                {{ teacherLessonTypeLabel(lesson.lessonType) }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="student in journal.data.value.students"
              :key="student.studentId"
              :class="{ 'teacher-cross': active?.studentId === student.studentId }"
            >
              <th
                class="teacher-sticky-name"
                scope="row"
              >
                {{ student.displayName }}
              </th><td
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                class="teacher-journal-slot"
                :class="{ 'teacher-cross': active?.lessonId === lesson.id }"
                @mouseenter="hovered = { studentId: student.studentId, lessonId: lesson.id }"
                @mouseleave="hovered = null"
              >
                <button
                  v-if="student.cells.find((cell) => cell.lessonId === lesson.id)?.ticketId"
                  class="teacher-cell-button"
                  :aria-label="`Открыть заявку: ${student.displayName}, ${shortDate(lesson.date)}, ${teacherAttendanceLabel(student.cells.find(cell => cell.lessonId === lesson.id))}`"
                  @focus="focusLesson(lesson.id, student.studentId, $event)"
                  @click="openTicket(student.cells.find(cell => cell.lessonId === lesson.id), lesson.id, student.studentId, $event)"
                >
                  <TeacherMark
                    :cell="student.cells.find((cell) => cell.lessonId === lesson.id)"
                  />
                </button><TeacherMark
                  v-else
                  :cell="student.cells.find((cell) => cell.lessonId === lesson.id)"
                />
              </td><td>{{ metric(student.studentId, 'present') }}</td><td>{{ metric(student.studentId, 'presentOrExcused') }}</td><td>{{ metric(student.studentId, 'excused') }}</td><td>{{ metric(student.studentId, 'absent') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-if="journal.data.value"
        class="teacher-toolbar"
      >
        <button
          v-if="journal.data.value.hasMore && !pagination.automatic && !pagination.error.value"
          class="web-button"
          :disabled="pagination.loading.value || pagination.refreshing.value"
          @click="pagination.more"
        >
          {{ pagination.loading.value ? 'Загружаем…' : 'Загрузить ещё пары' }}
        </button>
      </div>
      <TeacherNotice
        :loading="pagination.loading.value"
        :error="pagination.error.value"
        @retry="pagination.more"
      />
      <TeacherExport
        :scope="scope"
        :request="exportRequest"
      />
      <TeacherExcusePanel
        v-if="visibleTicket"
        :key="`${visibleTicket.ticketId}:${visibleTicket.lessonId}:${visibleTicket.studentId}`"
        :scope="scope"
        :source="visibleTicket"
        :trigger="ticketTrigger"
        @close="ticket = null"
      />
    </template>
  </TeacherRegion>
</template>
