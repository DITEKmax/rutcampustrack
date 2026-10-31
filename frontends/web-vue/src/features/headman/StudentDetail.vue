<script setup lang="ts">
import { onMounted, ref, shallowRef, watch } from 'vue';
import { message } from '../../auth/session';
import type { HeadmanFeature } from './context';
import { dateLabel, lessonTypes, markLabels, sourceLabel } from './context';
import type {
  HeadmanStatsStudentDetailResponse,
  HeadmanStatsTrendResponse,
} from '../../../../mobile-core/src/features/headman-stats/headman-stats-client';
import type { HeadmanStatsLessonPage } from '../../../../mobile-core/src/features/headman-stats/headman-stats-history-client';
import TrendChart from './TrendChart.vue';
const props = defineProps<{
  feature: HeadmanFeature;
  studentId: number;
  semesterId?: number | undefined;
}>();
const detail = shallowRef<HeadmanStatsStudentDetailResponse | null>(null),
  lessons = shallowRef<HeadmanStatsLessonPage | null>(null),
  trend = shallowRef<HeadmanStatsTrendResponse | null>(null);
const loading = ref(false),
  error = ref<string | null>(null),
  lessonPage = ref(0),
  latePage = ref(0),
  excusePage = ref(0);
let run = 0;
const percent = (value: number | null) =>
  value === null ? 'Нет данных' : `${value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })}%`;
async function load() {
  const revision = ++run;
  loading.value = true;
  error.value = null;
  const semester = props.semesterId ? { semesterId: props.semesterId } : {};
  try {
    if (!props.feature.allowed('VIEW_STATS')) throw new Error('Нет права просмотра статистики.');
    const [personal, history, dynamics] = await Promise.all([
      props.feature.stats.studentDetail(props.studentId, {
        ...semester,
        latePage: latePage.value,
        excusePage: excusePage.value,
        size: 10,
      }),
      props.feature.stats.studentLessons(props.studentId, {
        ...semester,
        page: lessonPage.value,
        size: 20,
      }),
      props.feature.stats.studentTrend(props.studentId, {
        ...semester,
        mode: 'SEMESTER',
        bucketUnit: 'WEEK',
      }),
    ]);
    props.feature.assertCurrent();
    if (revision !== run) return;
    detail.value = personal;
    lessons.value = history;
    trend.value = dynamics;
  } catch (cause) {
    if (revision === run) error.value = message(cause);
  } finally {
    if (revision === run) loading.value = false;
  }
}
watch(
  () => `${props.studentId}:${props.semesterId}`,
  () => {
    lessonPage.value = latePage.value = excusePage.value = 0;
    void load();
  },
);
onMounted(load);
</script>
<template>
  <section
    class="headman-panel web-stack"
    :aria-busy="loading"
  >
    <h2>{{ detail?.student.displayName ?? 'Статистика студента' }}</h2
    ><p
      v-if="loading"
      role="status"
      >Загрузка…</p
    ><p
      v-if="error"
      class="web-error"
      role="alert"
      >{{ error }}
      <button
        class="web-button"
        @click="load"
        >Обновить</button
      ></p
    >
    <template v-if="detail"
      ><p
        >{{ detail.context.semesterName }} · {{ dateLabel(detail.context.semesterFrom) }} —
        {{ dateLabel(detail.context.semesterTo) }}</p
      ><div class="headman-grid"
        ><div
          v-for="[key, label] in [
            ['present', 'Были'],
            ['presentOrExcused', 'Были и уважительная'],
            ['excused', 'Уважительная'],
            ['absent', 'Не были'],
          ] as const"
          :key="key"
          ><p>{{ label }}</p
          ><strong class="headman-metric">{{ percent(detail.metrics[key].percent) }}</strong
          ><p>{{ detail.metrics[key].numerator }}/{{ detail.metrics[key].denominator }}</p></div
        ></div
      ><p v-if="detail.rank?.available"
        >Место в группе: {{ detail.rank.position }} из {{ detail.rank.participantCount }}</p
      >
      <div class="headman-table-scroll"
        ><table class="headman-table"
          ><thead
            ><tr
              ><th>Предмет</th><th>Были</th><th>Были и уважительная</th><th>Уважительная</th
              ><th>Не были</th></tr
            ></thead
          ><tbody
            ><tr
              v-for="subject in detail.subjects"
              :key="subject.subjectId"
              ><th>{{ subject.subjectName }}</th
              ><td
                v-for="key in ['present', 'presentOrExcused', 'excused', 'absent'] as const"
                :key="key"
                >{{ percent(subject.metrics[key].percent) }} ·
                {{ subject.metrics[key].numerator }}/{{ subject.metrics[key].denominator }}</td
              ></tr
            ></tbody
          ></table
        ></div
      >
      <TrendChart
        v-if="trend"
        :points="trend.points"
        :from="trend.context.semesterFrom"
        :to="trend.context.semesterTo"
      />
      <h3>История посещаемости</h3
      ><div
        v-if="lessons"
        class="headman-table-scroll"
        ><table class="headman-table"
          ><thead
            ><tr><th>Дата</th><th>Предмет / пара</th><th>Отметка</th><th>Источник</th></tr></thead
          ><tbody
            ><tr
              v-for="lesson in lessons.items"
              :key="lesson.lessonId"
              ><th>{{ dateLabel(lesson.lessonDate) }}</th
              ><td
                >{{ lesson.subjectName }} · {{ lessonTypes[lesson.lessonType] }} ·
                {{ lesson.lessonNumber }}</td
              ><td>{{ markLabels[lesson.status ?? 'EMPTY'] }}</td
              ><td
                >{{ sourceLabel(lesson.source)
                }}<RouterLink
                  v-if="lesson.approvedTicket"
                  :to="{ name: 'headman-requests', query: { request: lesson.approvedTicket.id } }"
                >
                  Одобренная заявка</RouterLink
                ></td
              ></tr
            ></tbody
          ></table
        ></div
      ><div
        v-if="lessons && lessons.totalPages > 1"
        class="headman-actions"
        ><button
          class="web-button"
          :disabled="!lessons.hasPrevious || loading"
          @click="
            lessonPage--;
            load();
          "
          >Назад</button
        ><span>{{ lessons.page + 1 }} / {{ lessons.totalPages }}</span
        ><button
          class="web-button"
          :disabled="!lessons.hasNext || loading"
          @click="
            lessonPage++;
            load();
          "
          >Далее</button
        ></div
      >
      <h3>Апелляции</h3
      ><ul class="headman-list"
        ><li
          v-for="ticket in detail.lateCheckins.items"
          :key="ticket.id"
          >{{ dateLabel(ticket.lessonDate) }} · {{ ticket.subjectName }} · {{ ticket.status
          }}<RouterLink :to="{ name: 'headman-requests', query: { request: ticket.id } }">
            Открыть</RouterLink
          ></li
        ></ul
      ><div
        v-if="detail.lateCheckins.totalPages > 1"
        class="headman-actions"
        ><button
          class="web-button"
          :disabled="!detail.lateCheckins.hasPrevious || loading"
          @click="
            latePage--;
            load();
          "
          >Назад</button
        ><button
          class="web-button"
          :disabled="!detail.lateCheckins.hasNext || loading"
          @click="
            latePage++;
            load();
          "
          >Ещё апелляции</button
        ></div
      >
      <h3>Уважительные причины</h3
      ><ul class="headman-list"
        ><li
          v-for="ticket in detail.excuses.items"
          :key="ticket.id"
          >{{ dateLabel(ticket.submittedAt) }} · {{ ticket.status }} · пар {{ ticket.lessons.length
          }}<RouterLink :to="{ name: 'headman-requests', query: { request: ticket.id } }">
            Открыть</RouterLink
          ></li
        ></ul
      ><div
        v-if="detail.excuses.totalPages > 1"
        class="headman-actions"
        ><button
          class="web-button"
          :disabled="!detail.excuses.hasPrevious || loading"
          @click="
            excusePage--;
            load();
          "
          >Назад</button
        ><button
          class="web-button"
          :disabled="!detail.excuses.hasNext || loading"
          @click="
            excusePage++;
            load();
          "
          >Ещё причины</button
        ></div
      >
    </template>
  </section>
</template>
