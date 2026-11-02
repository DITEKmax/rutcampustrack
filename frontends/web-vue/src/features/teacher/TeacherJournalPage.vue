<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  toTeacherJournalReportRequest,
  teacherJournalTypes,
  type TeacherJournalQuery,
  type TeacherJournalResponse,
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
const ticketId = ref<string | null>(null);
const activeStudent = ref<number | null>(null),
  activeLesson = ref<number | null>(null);
const validation = computed(() =>
  !datesValid.value
    ? 'Исправь дату периода.'
    : (from.value || to.value) && (!from.value || !to.value || from.value > to.value)
      ? 'Укажи начало и конец периода в правильном порядке.'
      : '',
);
const query = computed<TeacherJournalQuery | null>(() =>
  selection.ready.value && scope.semester.value && !validation.value
    ? {
        semesterId: scope.semester.value.id,
        groupId: selection.groupId.value,
        subjectId: selection.subjectId.value,
        lessonTypes: [...selection.lessonTypes.value],
        pastOnly: pastOnly.value,
        includeMetrics: true,
        pageSize: 30,
        ...(from.value && to.value ? { dateFrom: from.value, dateTo: to.value } : {}),
      }
    : null,
);
function load(background = false) {
  const previous = loadedQuery.value;
  const retain = background === true && previous !== null &&
    previous.semesterId === scope.semester.value?.id &&
    previous.groupId === selection.groupId.value && previous.subjectId === selection.subjectId.value &&
    JSON.stringify(teacherJournalTypes(previous)) === JSON.stringify(selection.lessonTypes.value);
  // A live hint refreshes the applied period, never submits the date-field draft.
  const requested = retain ? previous : query.value;
  if (!retain) {
    ticketId.value = null;
    loadedQuery.value = null;
  }
  if (!requested) {
    journal.clear();
    return;
  }
  void journal.load(async () => {
    const data = retain ? await pagination.refreshWindow(requested) : await scope.api.journal(requested);
    scope.transport.assertCurrent();
    if (JSON.stringify(query.value) === JSON.stringify(requested)) loadedQuery.value = requested;
    return data;
  }, undefined, retain);
}

watch(
  () => scope.refreshed.value,
  () => {
    selection.reconcile();
    load(scope.backgroundRefresh.value);
  },
);
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      journal.clear();
      loadedQuery.value = null;
      ticketId.value = null;
    }
  },
);
const exportRequest = computed(() =>
  !validation.value &&
  journal.data.value?.lessons.length &&
  loadedQuery.value &&
  !journal.loading.value &&
  !pagination.loading.value
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
        class="teacher-toolbar"
        @submit.prevent="load()"
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
          class="web-button"
          @click="scope.dirty.value = false"
        >
          Применить период
        </button>
      </form>
      <p
        v-if="validation"
        class="web-error"
        role="alert"
      >
        {{ validation }}
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
        tabindex="0"
        aria-label="Журнал. Прокрути, чтобы увидеть все пары"
        @mouseleave="
          activeStudent = null;
          activeLesson = null;
        "
        @focusout="
          activeStudent = null;
          activeLesson = null;
        "
      >
        <table class="teacher-table">
          <caption class="web-sr-only">
            Посещаемость, только чтение
          </caption><thead>
            <tr>
              <th class="teacher-sticky-name" scope="col" rowspan="3">Студенты</th>
              <th
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                scope="col"
                :class="{ 'teacher-cross': activeLesson === lesson.id }"
              >
                <RouterLink :to="{ name: 'teacher-lesson', params: { lessonId: lesson.id } }">{{ shortDate(lesson.date) }}</RouterLink>
                <span v-if="lesson.cancelled" class="web-caption"> · отменена</span>
              </th>
              <th scope="col" rowspan="3">Присутствия</th>
              <th scope="col" rowspan="3">Присутствия и ув.</th>
              <th scope="col" rowspan="3">Уважительные</th>
              <th ref="journalEdge" scope="col" rowspan="3">Отсутствия</th>
            </tr>
            <tr>
              <th
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                scope="col"
                :class="{ 'teacher-cross': activeLesson === lesson.id }"
              >{{ lesson.startsAt?.slice(0, 5) ?? `Пара ${lesson.lessonNumber}` }}<template v-if="lesson.endsAt">–{{ lesson.endsAt.slice(0, 5) }}</template></th>
            </tr>
            <tr>
              <th
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                scope="col"
                :class="{ 'teacher-cross': activeLesson === lesson.id }"
              >{{ teacherLessonTypeLabel(lesson.lessonType) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="student in journal.data.value.students"
              :key="student.studentId"
              :class="{ 'teacher-cross': activeStudent === student.studentId }"
            >
              <th
                class="teacher-sticky-name"
                scope="row"
              >
                {{ student.displayName }}
              </th><td
                v-for="lesson in journal.data.value.lessons"
                :key="lesson.id"
                :class="{ 'teacher-cross': activeLesson === lesson.id }"
                @mouseenter="
                  activeStudent = student.studentId;
                  activeLesson = lesson.id;
                "
              >
                <button
                  v-if="student.cells.find((cell) => cell.lessonId === lesson.id)?.ticketId"
                  class="teacher-cell-button"
                  :aria-label="`Открыть заявку: ${student.displayName}, ${shortDate(lesson.date)}, ${teacherAttendanceLabel(student.cells.find(cell => cell.lessonId === lesson.id))}`"
                  @focus="
                    activeStudent = student.studentId;
                    activeLesson = lesson.id;
                  "
                  @click="
                    ticketId =
                      student.cells.find((cell) => cell.lessonId === lesson.id)?.ticketId ?? null
                  "
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
        v-if="ticketId"
        :key="ticketId"
        :scope="scope"
        :ticket-id="ticketId"
        @close="ticketId = null"
      />
    </template>
  </TeacherRegion>
</template>
