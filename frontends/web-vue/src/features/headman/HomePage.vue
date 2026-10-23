<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import { message } from '../../auth/session';
import type {
  HeadmanJournalLesson,
  HeadmanJournalReport,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import type { HeadmanSubject } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import { dateLabel, lessonTypes, today, useHeadmanFeature } from './context';
import HeadmanPage from './HeadmanPage.vue';
const feature = useHeadmanFeature();
const loading = ref(false),
  error = ref<string | null>(null),
  date = ref(today());
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([]),
  subjects = shallowRef<readonly HeadmanSubject[]>([]);
const report = shallowRef<HeadmanJournalReport | null>(null),
  openRequests = ref<number | null>(null),
  homeworkCount = ref<number | null>(null);
const current = computed(() => lessons.value.find((lesson) => lesson.status === 'ACTIVE'));
let timer: ReturnType<typeof setInterval> | undefined,
  run = 0;
async function load(background = false) {
  const revision = ++run;
  if (!background) loading.value = true;
  try {
    await feature.initialize();
    const activeSemester = await feature.homework.activeSemester();
    feature.assertCurrent();
    const [schedule, subjectList, requests, homeworks] = await Promise.all([
      feature.journal.listLessons(feature.groupId, date.value, date.value),
      feature.subjects.listSubjects(feature.groupId),
      feature.requests.list({ bucket: 'OPEN', size: 1 }),
      activeSemester
        ? feature.homework.listHomeworks(feature.groupId, activeSemester.id)
        : Promise.resolve(null),
    ]);
    feature.assertCurrent();
    if (revision !== run) return;
    lessons.value = schedule;
    subjects.value = subjectList;
    openRequests.value = requests.totalElements;
    homeworkCount.value = homeworks
      ? homeworks.filter((homework) => homework.lessonDate === date.value).length
      : null;
    const active = schedule.find((lesson) => lesson.status === 'ACTIVE');
    const attendance = active ? await feature.journal.getLessonAttendance(active.id) : null;
    feature.assertCurrent();
    if (revision !== run) return;
    report.value = attendance;
    error.value = null;
  } catch (cause) {
    if (revision === run) error.value = message(cause);
  } finally {
    if (revision === run) loading.value = false;
  }
}
const refresh = () => {
  if (!document.hidden) void load(true);
};
onMounted(() => {
  void load();
  timer = setInterval(refresh, 30000);
  window.addEventListener('online', refresh);
  window.addEventListener('focus', refresh);
});
onBeforeUnmount(() => {
  run++;
  clearInterval(timer);
  window.removeEventListener('online', refresh);
  window.removeEventListener('focus', refresh);
});
</script>
<template>
  <HeadmanPage
    title="Главная"
    :loading="loading"
    :error="error"
    @reload="load()"
  >
    <template #actions
      ><label class="web-field"
        >Дата<input
          v-model="date"
          type="date"
          @change="load()" /></label
    ></template>
    <div class="headman-grid">
      <section class="headman-panel web-stack"
        ><h2>Текущая пара</h2
        ><template v-if="current && report"
          ><p>{{ subjects.find((subject) => subject.id === current?.subjectId)?.name }}</p
          ><strong class="headman-metric"
            >{{ report.entries.filter((entry) => entry.status === 'PRESENT').length }} /
            {{ report.entries.length }}</strong
          ><p
            >Были · уважительных
            {{ report.entries.filter((entry) => entry.status === 'EXCUSED').length }}</p
          ><RouterLink
            class="web-button"
            :to="{ name: 'headman-attendance', query: { lesson: current.id } }"
            >Открыть журнал</RouterLink
          ></template
        ><p v-else-if="!loading && !error">Сейчас пары нет.</p></section
      >
      <section class="headman-panel web-stack"
        ><h2>Заявки</h2><strong class="headman-metric">{{ openRequests ?? '—' }}</strong
        ><p>Открыто на рассмотрение</p
        ><RouterLink
          class="web-button"
          :to="{ name: 'headman-requests' }"
          >Рассмотреть заявки</RouterLink
        ></section
      >
      <section class="headman-panel web-stack"
        ><h2>Домашнее задание</h2><strong class="headman-metric">{{ homeworkCount ?? '—' }}</strong
        ><p>Заданий на {{ dateLabel(date) }}</p
        ><RouterLink
          class="web-button"
          to="/headman/homework"
          >Открыть задания</RouterLink
        ></section
      >
    </div>
    <h2>Пары на {{ dateLabel(date) }}</h2>
    <div
      v-if="lessons.length"
      class="headman-table-scroll"
      ><table class="headman-table"
        ><thead
          ><tr
            ><th>Пара</th><th>Предмет</th><th>Время / аудитория</th><th>Статус</th
            ><th>Действия</th></tr
          ></thead
        ><tbody
          ><tr
            v-for="lesson in lessons"
            :key="lesson.id"
            ><th scope="row">{{ lesson.lessonNumber }}</th
            ><td
              >{{ subjects.find((subject) => subject.id === lesson.subjectId)?.name }} ·
              {{ lessonTypes[lesson.lessonType ?? ''] }}</td
            ><td
              >{{ lesson.startTime?.slice(0, 5) }} — {{ lesson.endTime?.slice(0, 5) }} ·
              {{ lesson.room ?? '—' }}</td
            ><td>{{
              {
                PLANNED: 'Предстоит',
                ACTIVE: 'Идёт',
                CLOSED: 'Завершена',
                CANCELLED: 'Отменена',
                TRANSFERRED: 'Перенесена',
                UNSUPPORTED: 'Неизвестно',
              }[lesson.status]
            }}</td
            ><td
              ><RouterLink
                class="web-button"
                :to="{ name: 'headman-attendance', query: { lesson: lesson.id } }"
                >Журнал и действия</RouterLink
              ></td
            ></tr
          ></tbody
        ></table
      ></div
    ><p
      v-else-if="!loading && !error"
      class="web-notice"
      >Пар на эту дату нет.</p
    >
  </HeadmanPage>
</template>
