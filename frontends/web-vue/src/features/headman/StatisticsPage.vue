<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue';
import { message } from '../../auth/session';
import { useHeadmanFeature, dateLabel, today, weekStart } from './context';
import {
  toHeadmanStatsReportRequest,
  toHeadmanStatsTrendReportRequest,
  type HeadmanStatsQuery,
  type HeadmanStatsResponse,
  type HeadmanStatsTrendQuery,
  type HeadmanStatsTrendResponse,
} from '../../../../mobile-core/src/features/headman-stats/headman-stats-client';
import type {
  HeadmanStatsSemesterCatalog,
  HeadmanStatsOverview,
} from '../../../../mobile-core/src/features/headman-stats/headman-stats-history-client';
import type { ReportDownloadFormat } from '../../../../mobile-core/src/shared/report-download-client';
import HeadmanPage from './HeadmanPage.vue';
import ReportExport from './ReportExport.vue';
import TrendChart from './TrendChart.vue';
import StudentDetail from './StudentDetail.vue';
const feature = useHeadmanFeature();
const loading = ref(false),
  error = ref<string | null>(null);
const catalog = shallowRef<HeadmanStatsSemesterCatalog | null>(null),
  overview = shallowRef<HeadmanStatsOverview | null>(null);
const data = shallowRef<HeadmanStatsResponse | null>(null),
  trend = shallowRef<HeadmanStatsTrendResponse | null>(null);
const draft = reactive({
  semesterId: 0,
  subjectId: 0,
  types: [] as string[],
  field: 'displayName',
  contains: '',
  minimum: '',
  maximum: '',
  descending: false,
  page: 0,
  mode: 'SEMESTER' as 'SEMESTER' | 'WEEK' | 'SUBJECT',
  week: weekStart(today()),
  bucket: 'WEEK' as 'DAY' | 'WEEK',
});
const applied = shallowRef<HeadmanStatsQuery | null>(null),
  appliedTrend = shallowRef<HeadmanStatsTrendQuery | null>(null);
const format = ref<ReportDownloadFormat>('xlsx'),
  trendFormat = ref<'png' | 'html'>('png'),
  selected = ref(0);
const columns = computed(() => data.value?.columns ?? []);
const currentField = computed(() => columns.value.find((column) => column.field === draft.field));
const subjects = computed(() => data.value?.subjects ?? []);
const types = computed(
  () => subjects.value.find((subject) => subject.id === draft.subjectId)?.lessonTypes ?? [],
);
const exportReport = computed(() =>
  applied.value ? toHeadmanStatsReportRequest(applied.value, format.value) : null,
);
const trendReport = computed(() =>
  appliedTrend.value
    ? toHeadmanStatsTrendReportRequest(appliedTrend.value, trendFormat.value)
    : null,
);
let run = 0;
const percent = (value: number, denominator: number) =>
  denominator === 0
    ? 'Нет данных'
    : `${value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })}%`;
async function load(reset = false) {
  const revision = ++run;
  loading.value = true;
  error.value = null;
  if (reset) draft.page = 0;
  try {
    await feature.initialize();
    if (!feature.allowed('VIEW_STATS')) throw new Error('Нет права просмотра статистики группы.');
    if (!catalog.value) {
      const [semesters, summary] = await Promise.all([
        feature.stats.semesters(),
        feature.stats.overview(),
      ]);
      feature.assertCurrent();
      catalog.value = semesters;
      overview.value = summary;
      draft.semesterId = semesters.activeSemesterId ?? semesters.semesters[0]?.id ?? 0;
    }
    const query: HeadmanStatsQuery = {
      ...(draft.semesterId ? { semesterId: draft.semesterId } : {}),
      subjectId: draft.subjectId || null,
      lessonTypes: draft.subjectId ? [...draft.types] : [],
      page: draft.page,
      size: 30,
      sorts: [{ field: draft.field, descending: draft.descending }],
      filters:
        draft.contains || draft.minimum || draft.maximum
          ? [
              {
                field: draft.field,
                ...(currentField.value?.filterKind === 'TEXT'
                  ? { contains: draft.contains }
                  : {
                      ...(draft.minimum ? { minimum: Number(draft.minimum) } : {}),
                      ...(draft.maximum ? { maximum: Number(draft.maximum) } : {}),
                    }),
              },
            ]
          : [],
    };
    const trendQuery: HeadmanStatsTrendQuery = {
      ...(draft.semesterId ? { semesterId: draft.semesterId } : {}),
      bucketUnit: draft.bucket,
      ...(draft.mode === 'WEEK'
        ? { mode: 'WEEK', weekStart: draft.week }
        : draft.mode === 'SUBJECT' && draft.subjectId
          ? { mode: 'SUBJECT', subjectId: draft.subjectId, lessonTypes: [...draft.types] }
          : { mode: 'SEMESTER' }),
    };
    const [result, dynamics] = await Promise.all([
      feature.stats.query(query),
      feature.stats.trend(trendQuery),
    ]);
    feature.assertCurrent();
    if (revision !== run) return;
    data.value = result;
    trend.value = dynamics;
    applied.value = query;
    appliedTrend.value = trendQuery;
  } catch (cause) {
    if (revision === run) error.value = message(cause);
  } finally {
    if (revision === run) loading.value = false;
  }
}
onMounted(() => load());
</script>
<template>
  <HeadmanPage
    title="Статистика"
    :loading="loading"
    :error="error"
    @reload="load()"
  >
    <p v-if="overview"
      >Активных студентов: {{ overview.membership.active }} из {{ overview.membership.total }} ·
      сегодня {{ dateLabel(overview.today.date) }}: были
      {{
        overview.today.present.percent === null
          ? 'нет данных'
          : `${overview.today.present.percent}%`
      }}
      ({{ overview.today.present.numerator }}/{{ overview.today.present.denominator }})</p
    >
    <form
      class="headman-panel headman-toolbar"
      @submit.prevent="load(true)"
      ><label class="web-field"
        >Семестр<select v-model.number="draft.semesterId"
          ><option
            v-for="semester in catalog?.semesters"
            :key="semester.id"
            :value="semester.id"
            >{{ semester.name }}{{ semester.archived ? ' · архив' : '' }}</option
          ></select
        ></label
      ><label class="web-field"
        >Предмет<select
          v-model.number="draft.subjectId"
          @change="draft.types = []"
          ><option :value="0">Вся группа</option
          ><option
            v-for="subject in subjects"
            :key="subject.id"
            :value="subject.id"
            >{{ subject.label }}</option
          ></select
        ></label
      ><fieldset v-if="types.length"
        ><legend>Типы занятий</legend
        ><label
          v-for="type in types"
          :key="type.code"
          ><input
            v-model="draft.types"
            type="checkbox"
            :value="type.code"
          />{{ type.label }}</label
        ></fieldset
      ><button
        class="web-button"
        :disabled="loading"
        >Применить</button
      ></form
    >
    <template v-if="data"
      ><p
        >{{ data.context.semesterName }} · {{ dateLabel(data.context.semesterFrom) }} —
        {{ dateLabel(data.context.semesterTo) }} · {{ data.context.lessonsCount }} завершённых
        пар</p
      ><div class="headman-grid"
        ><section
          v-for="[key, label] in [
            ['present', 'Доля отметок «+»'],
            ['presentOrExcused', 'Доля отметок «+» и «у»'],
            ['excused', 'Доля отметок «у»'],
            ['absent', 'Доля отметок «н»'],
          ] as const"
          :key="key"
          class="headman-panel web-stack"
          ><h2>{{ label }}</h2
          ><strong class="headman-metric">{{
            percent(data.summary[key].percent, data.summary[key].denominator)
          }}</strong
          ><p>{{ data.summary[key].numerator }}/{{ data.summary[key].denominator }}</p></section
        ></div
      >
      <form
        class="headman-toolbar"
        @submit.prevent="load(true)"
        ><label class="web-field"
          >Поле фильтра и сортировки<select
            v-model="draft.field"
            @change="draft.contains = draft.minimum = draft.maximum = ''"
            ><option
              v-for="column in columns"
              :key="column.field"
              :value="column.field"
              >{{ column.label }}</option
            ></select
          ></label
        ><label
          v-if="currentField?.filterKind === 'TEXT'"
          class="web-field"
          >Содержит<input
            v-model="draft.contains"
            type="search" /></label
        ><template v-else
          ><label class="web-field"
            >От<input
              v-model="draft.minimum"
              type="number"
              min="0" /></label
          ><label class="web-field"
            >До<input
              v-model="draft.maximum"
              type="number"
              min="0" /></label></template
        ><label
          ><input
            v-model="draft.descending"
            type="checkbox"
          />
          По убыванию</label
        ><button
          class="web-button"
          :disabled="loading"
          >Применить</button
        ><button
          type="button"
          class="web-button"
          :disabled="loading"
          @click="
            draft.contains = draft.minimum = draft.maximum = '';
            draft.descending = false;
            load(true);
          "
          >Сбросить</button
        ></form
      >
      <div class="headman-table-scroll"
        ><table class="headman-table"
          ><thead
            ><tr
              ><th>Студент</th><th>Были «+»</th><th>«+» и «у»</th><th>«у»</th><th>«н»</th
              ><th>Апелляции</th><th>Уважительные причины</th><th>Источники «+»</th></tr
            ></thead
          ><tbody
            ><tr
              v-for="row in data.rows"
              :key="row.studentId"
              ><th scope="row"
                ><button
                  class="web-button"
                  :aria-expanded="selected === row.studentId"
                  @click="selected = selected === row.studentId ? 0 : row.studentId"
                  >{{ row.displayName }}</button
                ></th
              ><td
                v-for="key in ['present', 'presentOrExcused', 'excused', 'absent'] as const"
                :key="key"
                >{{ percent(row.metrics[key].percent, row.metrics[key].denominator)
                }}<p class="web-caption"
                  >{{ row.metrics[key].numerator }}/{{ row.metrics[key].denominator }}</p
                ></td
              ><td
                >{{ row.lateCheckin.submitted }} / {{ row.lateCheckin.approved }} /
                {{ row.lateCheckin.rejected }}</td
              ><td
                >{{ row.excuse.submitted }} / {{ row.excuse.approved }} /
                {{ row.excuse.rejected }}</td
              ><td
                >Гео {{ row.sources.studentGeo }} · апелляция {{ row.sources.manualRequest }} · сбой
                гео {{ row.sources.autoAfterGeoFailure }} · староста
                {{ row.sources.headmanManual }}</td
              ></tr
            ></tbody
          ></table
        ></div
      ><p
        v-if="!data.rows.length"
        class="web-notice"
        >{{
          data.emptyState === 'FILTERED_EMPTY'
            ? 'Никто не подходит под фильтры.'
            : 'Нет завершённых пар или студентов для расчёта.'
        }}</p
      ><p class="web-caption">Заявки: подано / одобрено / отклонено</p>
      <div class="headman-actions"
        ><button
          class="web-button"
          :disabled="!data.hasPrevious || loading"
          @click="
            draft.page--;
            load();
          "
          >Назад</button
        ><span
          >{{ data.page + 1 }} / {{ Math.max(1, data.totalPages) }} · найдено
          {{ data.totalElements }}</span
        ><button
          class="web-button"
          :disabled="!data.hasNext || loading"
          @click="
            draft.page++;
            load();
          "
          >Далее</button
        ></div
      >
      <StudentDetail
        v-if="selected"
        :key="`${selected}:${draft.semesterId}`"
        :feature="feature"
        :student-id="selected"
        :semester-id="draft.semesterId"
      />
      <div class="headman-actions"
        ><label class="web-field"
          >Формат<select v-model="format"
            ><option
              v-for="item in data.formats"
              :key="item.code"
              :value="item.code"
              >{{ item.label }}</option
            ></select
          ></label
        ><ReportExport
          :transport="feature.transport"
          scope="statistics"
          :report="exportReport"
          :disabled="loading"
          label="Скачать текущий срез"
      /></div>
    </template>
    <section class="headman-panel web-stack"
      ><h2>Динамика посещаемости</h2
      ><form
        class="headman-toolbar"
        @submit.prevent="load()"
        ><label class="web-field"
          >Период<select v-model="draft.mode"
            ><option value="SEMESTER">За семестр</option
            ><option value="WEEK">Неделя</option
            ><option
              value="SUBJECT"
              :disabled="!draft.subjectId"
              >По выбранному предмету</option
            ></select
          ></label
        ><label
          v-if="draft.mode === 'WEEK'"
          class="web-field"
          >Начало недели<input
            v-model="draft.week"
            type="date"
            required /></label
        ><label class="web-field"
          >Шаг<select v-model="draft.bucket"
            ><option value="DAY">Дни</option
            ><option value="WEEK">Недели</option></select
          ></label
        ><button
          class="web-button"
          :disabled="loading"
          >Показать динамику</button
        ></form
      ><TrendChart
        v-if="trend"
        :points="trend.points"
        :from="draft.mode === 'WEEK' ? draft.week : trend.context.semesterFrom"
        :to="trend.context.semesterTo" /><div
        v-if="trend"
        class="headman-actions"
        ><label class="web-field"
          >Формат графика<select v-model="trendFormat"
            ><option
              v-for="item in trend.formats"
              :key="item.code"
              :value="item.code"
              >{{ item.label }}</option
            ></select
          ></label
        ><ReportExport
          :transport="feature.transport"
          scope="trend"
          :report="trendReport"
          :disabled="loading"
          label="Скачать динамику" /></div
    ></section>
  </HeadmanPage>
</template>
