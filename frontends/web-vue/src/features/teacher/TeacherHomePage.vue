<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import type {
  TeacherAssignment,
  TeacherDayResponse,
  TeacherSearchResponse,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';
import { shortDate, today, useTeacherResource, useTeacherScope } from './teacher-state';
import TeacherRegion from './TeacherRegion.vue';
import TeacherNotice from './TeacherNotice.vue';
import TeacherDayPanel from './TeacherDayPanel.vue';

const scope = useTeacherScope();
const assignments = useTeacherResource<readonly TeacherAssignment[]>(scope);
const search = useTeacherResource<TeacherSearchResponse>(scope);
const day = useTeacherResource<TeacherDayResponse>(scope);
const date = ref(today());
const query = ref('');
const searchValidation = ref('');
const groups = computed(() => scope.catalog.value?.groups ?? []);
function loadAssignments() {
  const semester = scope.semester.value;
  if (!semester) {
    assignments.clear();
    return;
  }
  void assignments.load(() =>
    scope.api.assignments(semester.id, semester.dateFrom, semester.dateTo),
  );
}
function loadDay() {
  const semester = scope.semester.value;
  if (!semester || !date.value || date.value < semester.dateFrom || date.value > semester.dateTo) {
    day.clear();
    return;
  }
  void day.load(() => scope.api.day(semester.id, date.value));
}
function find() {
  const semester = scope.semester.value;
  searchValidation.value = '';
  if (!semester) return;
  if (query.value.trim().length < 2) {
    searchValidation.value = 'Введи не менее двух символов.';
    return;
  }
  const requested = query.value.trim();
  void search.load(() => scope.api.search(semester.id, requested));
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
    loadAssignments();
    loadDay();
    search.clear();
  },
);
watch(date, loadDay);
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      assignments.clear();
      day.clear();
      search.clear();
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
        <section
          class="teacher-panel web-stack"
          aria-label="Поиск по доступным группам и студентам"
        >
          <form
            class="teacher-toolbar"
            @submit.prevent="find"
          >
            <label class="web-field">Поиск группы или студента<input
              v-model="query"
              type="search"
              maxlength="128"
              @input="
                search.clear();
                searchValidation = '';
              "
            ></label><button
              class="web-button"
              :disabled="search.loading.value"
            >
              Найти
            </button>
          </form>
          <p
            v-if="searchValidation"
            class="web-error"
            role="alert"
          >
            {{ searchValidation }}
          </p>
          <TeacherNotice
            :loading="search.loading.value"
            :error="search.error.value"
            :empty="
              search.data.value &&
                !search.data.value.groups.length &&
                !search.data.value.students.length
                ? 'Ничего не найдено в доступных группах.'
                : ''
            "
            @retry="find"
          />
          <template v-if="search.data.value">
            <ul class="teacher-links">
              <li
                v-for="group in search.data.value.groups"
                :key="group.id"
              >
                <RouterLink :to="{ name: 'teacher-stats', query: { group: group.id } }">
                  {{
                    group.name
                  }}
                </RouterLink>
              </li><li
                v-for="student in search.data.value.students"
                :key="student.id"
              >
                <RouterLink
                  :to="{
                    name: 'teacher-stats',
                    query: { group: student.groupId, studentName: student.displayName },
                  }"
                >
                  {{ student.displayName }} · {{ student.groupName }}
                </RouterLink>
              </li>
            </ul><p
              v-if="search.data.value.truncated"
              class="web-caption"
            >
              Показаны не все результаты. Уточни запрос.
            </p>
          </template>
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
