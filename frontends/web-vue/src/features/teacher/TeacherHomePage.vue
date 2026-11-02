<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import type {
  TeacherAssignment,
  TeacherDayResponse,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import { shortDate, today, useTeacherResource, useTeacherScope } from './teacher-state';
import TeacherRegion from './TeacherRegion.vue';
import TeacherNotice from './TeacherNotice.vue';
import TeacherDayPanel from './TeacherDayPanel.vue';

const scope = useTeacherScope();
const assignments = useTeacherResource<readonly TeacherAssignment[]>(scope);
const day = useTeacherResource<TeacherDayResponse>(scope);
const date = ref(today());
const groups = computed(() => scope.catalog.value?.groups ?? []);
function loadAssignments(background = false) {
  const semester = scope.semester.value;
  if (!semester) {
    assignments.clear();
    return;
  }
  const retain = background === true && assignments.data.value?.every(item =>
    item.semesterId === semester.id && scope.catalog.value?.groups.some(group => group.id === item.groupId));
  void assignments.load(() =>
    scope.api.assignments(semester.id, semester.dateFrom, semester.dateTo), undefined, !!retain);
}
function loadDay(background = false) {
  const semester = scope.semester.value;
  if (!semester || !date.value || date.value < semester.dateFrom || date.value > semester.dateTo) {
    day.clear();
    return;
  }
  const previous = day.data.value;
  const retain = background === true && previous?.semesterId === semester.id && previous.date === date.value &&
    previous.lessons.every(lesson => scope.catalog.value?.groups.some(group => group.id === lesson.groupId));
  void day.load(() => scope.api.day(semester.id, date.value), undefined, !!retain);
}
watch(
  () => scope.refreshed.value,
  () => {
    const semester = scope.semester.value;
    if (semester)
      date.value =
        date.value < semester.dateFrom
          ? semester.dateFrom
          : date.value > semester.dateTo
            ? semester.dateTo
            : date.value;
    loadAssignments(scope.backgroundRefresh.value);
    loadDay(scope.backgroundRefresh.value);
  },
);
watch(date, () => loadDay());
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      assignments.clear();
      day.clear();
    }
  },
);
</script>
<template>
  <TeacherRegion
    :scope="scope"
    screen="WEB-TEACHER-HOME"
  >
    <h1 class="web-sr-only">
      Главная преподавателя
    </h1>
    <TeacherNotice
      :loading="scope.loading.value && !scope.catalog.value"
      :error="scope.error.value"
      @retry="scope.load"
    />
    <div
      v-if="scope.catalog.value"
      class="teacher-home-layout"
    >
      <div class="web-stack">
        <section
          class="teacher-panel web-stack"
          aria-labelledby="teacher-groups"
        >
          <h2 id="teacher-groups">
            Мои группы
          </h2>
          <p v-if="!groups.length">
            Сейчас нет назначенных групп.
          </p>
          <ul class="teacher-links">
            <li
              v-for="group in groups"
              :key="group.id"
            >
              <RouterLink :to="{ name: 'teacher-stats', query: { group: group.id } }">
                {{
                  group.name
                }}
              </RouterLink>
            </li>
          </ul>
        </section>
        <section
          class="teacher-panel web-stack"
          aria-labelledby="teacher-assignments"
        >
          <h2 id="teacher-assignments">
            Мои предметы
          </h2>
          <TeacherNotice
            :loading="assignments.loading.value"
            :error="assignments.error.value"
            :empty="assignments.data.value?.length === 0 ? 'В этом семестре назначений нет.' : ''"
            @retry="loadAssignments"
          />
          <div
            v-if="assignments.data.value?.length"
            class="teacher-table-scroll"
            tabindex="0"
            aria-label="Назначенные предметы"
          >
            <table class="teacher-table">
              <thead>
                <tr>
                  <th scope="col">
                    Группа
                  </th><th scope="col">
                    Предмет
                  </th><th scope="col">
                    Тип занятия
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in assignments.data.value"
                  :key="item.id"
                >
                  <td>
                    <RouterLink
                      :to="{
                        name: 'teacher-journal',
                        query: {
                          group: item.groupId,
                          subject: item.subjectId,
                          type: item.lessonType,
                        },
                      }"
                    >
                      {{ item.groupName }}
                    </RouterLink>
                  </td><td>{{ item.subjectName }}</td><td>{{ teacherLessonTypeLabel(item.lessonType) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
      <section
        class="web-stack"
        aria-label="Пары на день"
      >
        <h2 class="web-sr-only">
          Пары на {{ shortDate(date) }}
        </h2>
        <TeacherDayPanel
          v-model="date"
          :semester="scope.semester.value"
          :data="day.data.value"
          :loading="day.loading.value"
          :error="day.error.value"
          @retry="loadDay"
        />
      </section>
    </div>
  </TeacherRegion>
</template>
