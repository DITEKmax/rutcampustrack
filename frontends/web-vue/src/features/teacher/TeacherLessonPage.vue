<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import {
  toTeacherJournalReportRequest,
  type TeacherLessonResponse,
  type TeacherRosterEntry,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import { shortDate, useTeacherResource, useTeacherScope } from './teacher-state';
import TeacherRegion from './TeacherRegion.vue';
import TeacherNotice from './TeacherNotice.vue';
import TeacherMark from './TeacherMark.vue';
import { teacherAttendanceLabel } from './teacher-mark';
import TeacherExcusePanel from './TeacherExcusePanel.vue';
import TeacherExport from './TeacherExport.vue';
import { isManualEvidence, type TeacherEvidenceSource } from './teacher-evidence';

const scope = useTeacherScope(),
  route = useRoute();
const lesson = useTeacherResource<TeacherLessonResponse>(scope);
const selected = shallowRef<TeacherEvidenceSource | null>(null);
const trigger = shallowRef<HTMLElement | null>(null);
const selectedEntry = computed(() => {
  const source = selected.value;
  const current = lesson.data.value;
  if (!source || !current || current.lesson.id !== source.lessonId) return null;
  const entry = current.roster.find(row => row.studentId === source.studentId);
  if (!entry) return null;
  return ('ticketId' in source ? entry.ticketId === source.ticketId : !current.lesson.cancelled && isManualEvidence(entry))
    ? entry
    : null;
});
watch(selectedEntry, entry => { if (!entry) selected.value = null; });
function openEvidence(entry: TeacherRosterEntry, event: MouseEvent) {
  const current = lesson.data.value?.lesson;
  if (!current || (!entry.ticketId && (current.cancelled || !isManualEvidence(entry)))) return;
  trigger.value = event.currentTarget as HTMLElement;
  const source = { lessonId: current.id, studentId: entry.studentId };
  selected.value = entry.ticketId ? { ...source, ticketId: entry.ticketId } : source;
}
function load(background = false) {
  const id = Number(route.params.lessonId);
  const previous = lesson.data.value?.lesson;
  const retain = background === true && previous?.id === id &&
    previous.semesterId === scope.semester.value?.id &&
    scope.catalog.value?.subjectOptions.some(option => option.groupId === previous.groupId &&
      option.subjectId === previous.subjectId && option.lessonTypes.includes(previous.lessonType));
  if (!retain) selected.value = null;
  if (!Number.isSafeInteger(id) || id < 1) {
    lesson.clear();
    lesson.error.value = 'Некорректный адрес пары.';
    return;
  }
  void lesson.load(() => scope.api.lesson(id), undefined, !!retain);
}
watch(
  () => [scope.refreshed.value, route.params.lessonId],
  (value, previous) => {
    if (scope.catalog.value) load(scope.backgroundRefresh.value && value[1] === previous[1]);
  },
);
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      lesson.clear();
      selected.value = null;
    }
  },
);
const exportRequest = computed(() => {
  const data = lesson.data.value?.lesson;
  return data && !lesson.loading.value
    ? (format: Parameters<typeof toTeacherJournalReportRequest>[1]) =>
        toTeacherJournalReportRequest(
          {
            semesterId: data.semesterId,
            groupId: data.groupId,
            subjectId: data.subjectId,
            lessonTypes: [data.lessonType],
            lessonId: String(data.id),
          },
          format,
        )
    : null;
});
</script>
<template>
  <TeacherRegion
    :scope="scope"
    screen="WEB-TEACHER-LESSON"
  >
    <h1 class="web-sr-only">
      Посещаемость одной пары
    </h1>
    <TeacherNotice
      :loading="scope.loading.value && !scope.catalog.value"
      :error="scope.error.value"
      @retry="scope.load"
    />
    <TeacherNotice
      :loading="lesson.loading.value"
      :error="lesson.error.value"
      @retry="load"
    />
    <div
      v-if="lesson.data.value"
      class="teacher-lesson-layout"
    >
      <section
        class="teacher-panel web-stack"
        aria-label="Контекст пары"
      >
        <h2>{{ lesson.data.value.lesson.subjectName }}</h2><p>
          {{ lesson.data.value.lesson.groupName }} ·
          {{ teacherLessonTypeLabel(lesson.data.value.lesson.lessonType) }}
        </p>
        <dl class="teacher-definitions">
          <div>
            <dt>Дата и время</dt><dd>
              {{ shortDate(lesson.data.value.lesson.date) }} ·
              {{
                lesson.data.value.lesson.startsAt?.slice(0, 5) ??
                  `Пара ${lesson.data.value.lesson.lessonNumber}`
              }}<template v-if="lesson.data.value.lesson.endsAt">
                –{{ lesson.data.value.lesson.endsAt.slice(0, 5) }}
              </template>
            </dd>
          </div><div>
            <dt>Аудитория</dt><dd>{{ lesson.data.value.lesson.room ?? 'Не указана' }}</dd>
          </div>
        </dl>
        <p
          v-if="lesson.data.value.lesson.cancelled"
          class="web-notice"
        >
          Пара отменена. {{ lesson.data.value.lesson.cancellationReason }}
        </p><p
          v-else-if="lesson.data.value.lesson.moved"
          class="web-caption"
        >
          Пара перенесена.
        </p>
        <p class="web-caption">
          Только чтение. Отметки ведёт староста.
        </p>
        <RouterLink
          :to="{
            name: 'teacher-journal',
            query: {
              group: lesson.data.value.lesson.groupId,
              subject: lesson.data.value.lesson.subjectId,
              type: lesson.data.value.lesson.lessonType,
            },
          }"
        >
          Открыть журнал за семестр
        </RouterLink>
      </section>
      <section
        class="teacher-panel web-stack"
        aria-label="Посещаемость студентов"
      >
        <p
          v-if="!lesson.data.value.roster.length"
          class="web-notice"
        >
          В этой паре пока нет студентов.
        </p>
        <div
          v-else
          class="teacher-table-scroll"
          tabindex="0"
          aria-label="Список студентов пары"
        >
          <table class="teacher-table teacher-roster">
            <thead>
              <tr>
                <th scope="col">
                  ФИО
                </th><th scope="col">
                  Статус
                </th>
              </tr>
            </thead><tbody>
              <tr
                v-for="student in lesson.data.value.roster"
                :key="student.studentId"
              >
                <th scope="row">
                  {{ student.displayName }}
                </th><td>
                  <button
                    v-if="student.ticketId || (!lesson.data.value.lesson.cancelled && isManualEvidence(student))"
                    class="teacher-cell-button"
                    :aria-label="`${student.ticketId ? 'Открыть заявку' : 'Открыть причину'}: ${student.displayName}, ${teacherAttendanceLabel(student)}`"
                    @click="openEvidence(student, $event)"
                  >
                    <TeacherMark :cell="student" />
                  </button><TeacherMark
                    v-else
                    :cell="student"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <TeacherExport
          :scope="scope"
          :request="exportRequest"
        />
      </section>
    </div>
    <TeacherExcusePanel
      v-if="selected && selectedEntry"
      :key="`${selected.lessonId}:${selected.studentId}:${selectedEntry.ticketId ?? ''}`"
      :scope="scope"
      :source="selected"
      :entry="selectedEntry"
      :trigger="trigger"
      @close="selected = null"
    />
  </TeacherRegion>
</template>
