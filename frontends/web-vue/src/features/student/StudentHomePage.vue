<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type {
  StudentStatisticsSubjectDetailResponse,
  StudentToday,
  StudentRequestOptions,
} from '../../../../mobile-core/src/api/types';
import { useStudentResource, useStudentScope } from './student-state';
import { useStudentCheckin } from './use-student-checkin';
import { formatLessonDate } from './student-format';
import StudentRegion from './StudentRegion.vue';
import StudentNotice from './StudentNotice.vue';
import StudentLesson from './StudentLesson.vue';
import StudentMetrics from './StudentMetrics.vue';
import StudentPwaBanner from './StudentPwaBanner.vue';
import StudentHomeworkDay from './StudentHomeworkDay.vue';
import { canRequestLesson } from '../../../../mobile-core/src/features/requests/state';
const scope = useStudentScope();
const today = useStudentResource<StudentToday>(scope);
const subject = useStudentResource<StudentStatisticsSubjectDetailResponse>(scope);
const options = useStudentResource<StudentRequestOptions>(scope);
const receivedAt = ref(Date.now());
watch(today.data, () => {
  receivedAt.value = Date.now();
});
const hero = computed(
  () =>
    today.data.value?.lessons.find((l) => l.schedule.status === 'ACTIVE') ??
    today.data.value?.lessons.find((l) => l.schedule.status === 'PLANNED') ??
    null,
);
async function load() {
  const semester = scope.session.value?.semester;
  if (!semester) {
    today.clear();
    subject.clear();
    options.clear();
    return;
  }
  await Promise.all([
    today.load(() => scope.api.getToday(), true),
    options.load(() => scope.api.getRequestOptions(), true),
  ]);
  if (!scope.current()) return;
  scope.transport.assertCurrent();
  await loadSubject();
}
watch(scope.refreshed, load);
async function loadSubject() {
  const lesson = hero.value;
  const semester = scope.session.value?.semester;
  if (lesson && semester)
    await subject.load(
      () =>
        scope.api.getStatisticsSubject(lesson.schedule.subject.id, semester.id, 'weeks', [
          lesson.schedule.subject.type,
        ]),
      String(subject.data.value?.subjectId) === lesson.schedule.subject.id &&
        subject.data.value?.selectedTypes.length === 1 &&
        subject.data.value.selectedTypes.includes(lesson.schedule.subject.type),
    );
  else subject.clear();
}
watch(scope.session, (value) => {
  if (!value) {
    today.clear();
    subject.clear();
  }
});
const checkin = useStudentCheckin(scope, load);
const progress = computed(() => {
  const lesson = hero.value?.schedule,
    value = today.data.value;
  if (!lesson || !value || lesson.status !== 'ACTIVE') return null;
  const start = Date.parse(`${lesson.date}T${lesson.startsAt}+03:00`),
    end = Date.parse(`${lesson.date}T${lesson.endsAt}+03:00`);
  return Number.isFinite(start) && end > start
    ? Math.max(
        0,
        Math.min(
          100,
          ((Date.parse(value.serverNow) +
            Math.max(0, checkin.clock.value - receivedAt.value) -
            start) /
            (end - start)) *
            100,
        ),
      )
    : null;
});
</script>
<template>
  <StudentRegion
    :scope="scope"
    screen="WEB-STUDENT-HOME"
  >
    <h1 class="web-sr-only">
      Главная студента
    </h1>
    <StudentPwaBanner />
    <StudentNotice
      :loading="scope.loading.value"
      :error="scope.error.value"
      :empty="
        scope.session.value && !scope.session.value.semester ? 'Активного семестра пока нет.' : ''
      "
      @retry="scope.load"
    />
    <template v-if="scope.session.value?.semester">
      <p
        v-if="scope.readOnly.value"
        class="web-notice"
      >
        Доступен только просмотр. Отметки и подача заявок недоступны.
      </p>
      <StudentNotice
        :loading="today.loading.value"
        :error="today.error.value"
        @retry="load"
      />
      <div
        v-if="today.data.value"
        class="student-dashboard-pair"
      >
        <section
          class="student-current web-stack"
          aria-labelledby="student-current-title"
        >
          <h2 id="student-current-title">
            {{ hero?.schedule.status === 'ACTIVE' ? 'Текущая пара' : 'Ближайшая пара' }}
          </h2>
          <template v-if="hero">
            <StudentLesson
              :lesson="hero.schedule"
              :today="hero"
              featured
            />
            <progress
              v-if="progress !== null"
              class="student-progress"
              :value="progress"
              max="100"
              aria-label="Прошедшая часть пары"
            />
            <button
              class="web-button web-button-primary"
              :disabled="
                !checkin.allowed(hero, today.data.value.serverNow) ||
                  checkin.uncertain.value === hero.schedule.id
              "
              :aria-busy="checkin.busy.value"
              @click="checkin.submit(hero, today.data.value.serverNow)"
            >
              {{ checkin.label(hero, today.data.value.serverNow) }}
            </button>
            <button
              v-if="checkin.uncertain.value === hero.schedule.id"
              class="web-button"
              :disabled="checkin.busy.value"
              @click="checkin.submit(hero, today.data.value.serverNow, true)"
            >
              Уточнить результат отметки
            </button>
          </template>
          <p v-else>
            На сегодня текущих и предстоящих пар нет.
          </p>
          <p
            v-if="checkin.error.value"
            class="web-error"
            role="alert"
          >
            {{ checkin.error.value
            }}<template v-if="checkin.uncertain.value">
              Результат пока неизвестен. Уточни его перед новой попыткой.
            </template>
          </p>
          <p
            v-if="checkin.result.value"
            role="status"
          >
            {{ checkin.result.value }}
          </p>
        </section>
        <section
          class="web-stack student-current-stats"
          aria-labelledby="student-current-stats-title"
        >
          <h2 id="student-current-stats-title">
            По текущему предмету
          </h2>
          <StudentNotice
            :loading="subject.loading.value"
            :error="subject.error.value"
            :empty="!hero ? 'Нет текущего предмета.' : ''"
            @retry="load"
          />
          <StudentMetrics
            v-if="subject.data.value"
            :metrics="subject.data.value.selectedAggregate"
            compact
            show-absences
          />
        </section>
      </div>
      <div
        v-if="today.data.value"
        class="student-dashboard-bottom"
      >
        <section
          class="web-stack"
          aria-labelledby="student-today-title"
        >
          <h2 id="student-today-title">
            Расписание на сегодня
          </h2><p class="web-caption">
            {{ formatLessonDate(today.data.value.date) }}
          </p>
          <p v-if="!today.data.value.lessons.length">
            Сегодня пар нет.
          </p>
          <ol class="student-lesson-list">
            <li
              v-for="lesson in today.data.value.lessons"
              :key="lesson.schedule.id"
            >
              <StudentLesson
                :lesson="lesson.schedule"
                :today="lesson"
              >
                <template #actions>
                  <template
                    v-for="kind in ['EXCUSE', 'LATE_CHECKIN'] as const"
                    :key="kind"
                  >
                    <RouterLink
                      v-if="
                        !scope.readOnly.value &&
                          canRequestLesson(
                            options.data.value?.lessons.find(
                              (option) => option.lesson.id === lesson.schedule.id,
                            ),
                            kind,
                          )
                      "
                      class="web-button"
                      :to="{
                        name: 'student-requests',
                        query: { lesson: lesson.schedule.id, kind },
                      }"
                    >
                      {{
                        kind === 'EXCUSE' ? 'Подать уважительную причину' : 'Оспорить отсутствие'
                      }}
                    </RouterLink>
                  </template>
                </template>
              </StudentLesson>
            </li>
          </ol>
          <RouterLink
            class="web-button"
            :to="{ name: 'student-schedule' }"
          >
            Расписание семестра
          </RouterLink>
        </section>
        <StudentHomeworkDay />
      </div>
    </template>
  </StudentRegion>
</template>
