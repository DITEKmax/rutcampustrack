<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue';
import { message } from '../../auth/session';
import { useWebLiveRefresh } from '../../app/live';
import type {
  HeadmanJournalLesson,
  HeadmanJournalReport,
} from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import type { HeadmanLessonActionsPage } from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions';
import type { HeadmanSubject } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import type { HeadmanManagedHomework } from '../../../../mobile-core/src/features/homework/headman-homework-client';
import {
  dateLabel,
  lessonTypes,
  lessonTypeCode,
  markLabels,
  sourceLabel,
  actionLabel,
  today,
  useHeadmanFeature,
} from './context';
import HeadmanPage from './HeadmanPage.vue';
import LinkifiedText from '../../shared/LinkifiedText.vue';
const feature = useHeadmanFeature();
const loading = ref(false),
  error = ref<string | null>(null),
  date = ref(today());
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([]),
  subjects = shallowRef<readonly HeadmanSubject[]>([]),
  homeworks = shallowRef<readonly HeadmanManagedHomework[] | null>(null);
const ongoing = shallowRef<HeadmanJournalLesson | null>(null),
  report = shallowRef<HeadmanJournalReport | null>(null),
  audit = shallowRef<HeadmanLessonActionsPage | null>(null);
const requests = shallowRef<{ excuse: number; late: number } | null>(null);
let run = 0;
function subjectName(id: number | null) {
  return subjects.value.find((subject) => subject.id === id)?.name ?? 'Предмет недоступен';
}
async function load(background = false) {
  const revision = ++run;
  if (!background) loading.value = true;
  try {
    await feature.initialize();
    const activeSemester = await feature.homework.activeSemester();
    feature.assertCurrent();
    const day = today();
    const [schedule, currentLessons, subjectList, excuses, late, homeworkList] = await Promise.all([
      feature.journal.listLessons(feature.groupId, date.value, date.value),
      date.value === day
        ? Promise.resolve(null)
        : feature.journal.listLessons(feature.groupId, day, day),
      feature.subjects.listSubjects(feature.groupId),
      feature.requests.list({ bucket: 'OPEN', type: 'EXCUSE', size: 1 }),
      feature.requests.list({ bucket: 'OPEN', type: 'LATE_CHECKIN', size: 1 }),
      activeSemester
        ? feature.homework.listHomeworks(feature.groupId, activeSemester.id)
        : Promise.resolve(null),
    ]);
    feature.assertCurrent();
    const active =
      (currentLessons ?? schedule).find((lesson) => lesson.status === 'ACTIVE') ?? null;
    const [attendance, actions] = active
      ? await Promise.all([
          feature.journal.getLessonAttendance(active.id),
          feature.actions.getLessonActions(active.id),
        ])
      : [null, null];
    feature.assertCurrent();
    if (revision !== run) return;
    lessons.value = schedule;
    subjects.value = subjectList;
    requests.value = { excuse: excuses.totalElements, late: late.totalElements };
    homeworks.value = homeworkList?.filter((homework) => homework.lessonDate === day) ?? null;
    ongoing.value = active;
    report.value = attendance;
    audit.value = actions;
    error.value = null;
  } catch (cause) {
    if (revision === run) error.value = message(cause);
  } finally {
    if (revision === run) loading.value = false;
  }
}
onMounted(() => load());
useWebLiveRefresh(() => load(true), { blocked: () => loading.value });
</script>
<template>
  <HeadmanPage
    title="Главная"
    :loading="loading"
    :error="error"
    @reload="load()"
  >
    <div class="headman-grid"
      ><section
        class="headman-panel web-stack"
        :class="{ 'headman-current': ongoing }"
        ><h2>Текущая пара</h2
        ><template v-if="ongoing"
          ><p>{{ subjectName(ongoing.subjectId) }}</p
          ><p
            >{{ ongoing.startTime?.slice(0, 5) }} — {{ ongoing.endTime?.slice(0, 5) }} ·
            {{ lessonTypes[lessonTypeCode(ongoing.lessonType)] }} ·
            {{ ongoing.room ?? 'Аудитория не указана' }}</p
          ><RouterLink
            class="web-button"
            :to="{ name: 'headman-lessons', query: { date: ongoing.date, lesson: ongoing.id } }"
            >Действия пары</RouterLink
          ></template
        ><p v-else-if="!loading && !error">Сейчас пары нет.</p></section
      ><section
        v-for="[status, label] in [
          ['PRESENT', 'Присутствуют «+»'],
          ['EXCUSED', 'Уважительная причина «у»'],
        ]"
        :key="status"
        class="headman-panel web-stack"
        ><h2>{{ label }}</h2
        ><template v-if="ongoing && report"
          ><strong
            class="headman-metric"
            :aria-label="`${report.entries.filter((entry) => entry.status === status).length} из ${report.entries.length}`"
            >{{ report.entries.filter((entry) => entry.status === status).length }}/{{
              report.entries.length
            }}</strong
          ><p class="web-caption">Текущая пара · {{ subjectName(ongoing.subjectId) }}</p></template
        ><p v-else-if="!loading && !error">Нет идущей пары</p></section
      ></div
    >
    <div
      v-if="ongoing && report"
      class="headman-work-grid"
      ><section class="web-stack"
        ><h2>Отметки текущей пары</h2
        ><div class="headman-table-scroll"
          ><table class="headman-table"
            ><thead
              ><tr><th>Студент</th><th>Отметка / источник</th><th>Действие</th></tr></thead
            ><tbody
              ><tr
                v-for="entry in report.entries"
                :key="entry.userId"
                ><th scope="row">{{ entry.displayName }}</th
                ><td>{{ markLabels[entry.status] }} · {{ sourceLabel(entry.source) }}</td
                ><td
                  ><RouterLink
                    class="web-button"
                    :to="{
                      name: 'headman-attendance',
                      query: { lesson: ongoing.id, student: entry.userId, date: ongoing.date },
                    }"
                    >Открыть отметку</RouterLink
                  ></td
                ></tr
              ></tbody
            ></table
          ></div
        ></section
      ><section class="headman-panel web-stack"
        ><h2>Действия текущей пары</h2
        ><ol class="headman-list"
          ><li
            v-for="action in audit?.items"
            :key="action.id"
            >{{ new Date(action.occurredAt).toLocaleTimeString('ru-RU') }} ·
            {{ action.actor?.displayName ?? 'Система' }} · {{ actionLabel(action.action) }} ·
            {{ action.outcome === 'APPLIED' ? 'Применено' : 'Отклонено' }}</li
          ></ol
        ><p v-if="!audit?.items.length">Действий пока нет.</p
        ><RouterLink
          class="web-button"
          :to="{ name: 'headman-attendance', query: { lesson: ongoing.id, date: ongoing.date } }"
          >Полный журнал действий</RouterLink
        ></section
      ></div
    >
    <div class="headman-work-grid"
      ><section class="headman-panel web-stack"
        ><div class="headman-heading"
          ><h2>Домашнее задание на сегодня</h2
          ><RouterLink
            class="web-button"
            to="/headman/homework"
            >Все задания и публикация</RouterLink
          ></div
        ><article
          v-for="homework in homeworks"
          :key="homework.id"
          class="web-stack"
          ><h3>{{ homework.title }}</h3
          ><p class="web-caption"
            >{{ subjectName(homework.subjectId) }} ·
            {{ lessonTypes[lessonTypeCode(homework.lessonType)]
            }}{{ homework.lessonNumber ? ` · пара №${homework.lessonNumber}` : '' }}</p
          ><p v-if="homework.description"><LinkifiedText :text="homework.description" /></p
          ><a
            v-if="homework.link"
            :href="homework.link"
            target="_blank"
            rel="noopener noreferrer"
            >Основная ссылка</a
          ></article
        ><p v-if="homeworks && !homeworks.length">Заданий на сегодня нет.</p></section
      ><section class="web-stack"
        ><article
          v-for="[kind, label, count] in [
            ['EXCUSE', 'Заявки на уважительную причину', requests?.excuse],
            ['LATE_CHECKIN', 'Апелляции отметок', requests?.late],
          ] as const"
          :key="kind"
          class="headman-panel web-stack"
          ><h2>{{ label }}</h2
          ><strong class="headman-metric">{{ count ?? '—' }}</strong
          ><RouterLink
            class="web-button"
            :to="{ name: 'headman-requests', query: { type: kind } }"
            >Рассмотреть</RouterLink
          ></article
        ></section
      ></div
    >
    <div class="headman-heading"
      ><h2>Пары на {{ dateLabel(date) }}</h2
      ><label class="web-field"
        >Дата<input
          v-model="date"
          type="date"
          @change="load()" /></label></div
    ><div
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
              >{{ subjectName(lesson.subjectId) }} ·
              {{ lessonTypes[lessonTypeCode(lesson.lessonType)] }}</td
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
                :to="{
                  name: 'headman-attendance',
                  query: { lesson: lesson.id, date: lesson.date },
                }"
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
