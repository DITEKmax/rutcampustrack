<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { message } from '../../auth/session';
import { useWebLiveRefresh } from '../../app/live';
import { useHeadmanFeature, dateLabel, today, weekStart, addDays } from './context';
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
import HeadmanMetricCards from './HeadmanMetricCards.vue';
import StudentPeriodPager from '../student/StudentPeriodPager.vue';
import WebSelect from '../../shared/WebSelect.vue';
import WebDateField from '../../shared/WebDateField.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import '../student/student.pcss';
import './headman-analytics.pcss';
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
  sortField: 'displayName',
  contains: '',
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
const appliedDraft = ref('');
const dirty = computed(() => appliedDraft.value !== JSON.stringify(draft));
const selectedSemester = computed(() => catalog.value?.semesters.find(item => item.id === draft.semesterId));
const subjectOptions = computed(() => [{ value: 0, label: 'Вся группа' }, ...subjects.value.map(item => ({ value: item.id, label: item.label }))]);
function chooseSubject() { draft.types = []; if (!draft.subjectId && draft.mode === 'SUBJECT') draft.mode = 'SEMESTER'; }
function sortBy(field: string) { draft.descending = draft.sortField === field ? !draft.descending : false; draft.sortField = field; void load(true); }
function stepWeek(direction: -1 | 1) { draft.week = addDays(draft.week, direction * 7); void load(); }
function sortIndicator(field: string) { const sort = applied.value?.sorts[0]; return sort?.field === field ? sort.descending ? '↓' : '↑' : ''; }
function sortAria(field: string): 'none' | 'ascending' | 'descending' { const sort = applied.value?.sorts[0]; return sort?.field === field ? sort.descending ? 'descending' : 'ascending' : 'none'; }
let run = 0, alive = true, suppressAutoApply = false;
let applyTimer: ReturnType<typeof setTimeout> | null = null;
let catalogRequest: Promise<void> | null = null;
function clearApplyTimer() {
  if (applyTimer !== null) clearTimeout(applyTimer);
  applyTimer = null;
}
function scheduleApply(delay: number) {
  if (!alive || suppressAutoApply) return;
  clearApplyTimer();
  // Invalidate the previous request before the debounce delay, not when its successor starts.
  run++;
  draft.page = 0;
  applyTimer = setTimeout(() => { applyTimer = null; void load(); }, delay);
}
function resetFilters() {
  suppressAutoApply = true;
  draft.contains = '';
  draft.subjectId = 0;
  draft.types = [];
  draft.sortField = 'displayName';
  draft.descending = false;
  draft.page = 0;
  if (draft.mode === 'SUBJECT') draft.mode = 'SEMESTER';
  suppressAutoApply = false;
  // Semester and the current week/bucket remain the chosen context.
  void load(true);
}
async function ensureCatalog() {
  if (catalog.value) return;
  if (!catalogRequest) catalogRequest = (async () => {
    const [semesters, summary] = await Promise.all([feature.stats.semesters(), feature.stats.overview()]);
    feature.assertCurrent();
    if (!alive) return;
    catalog.value = semesters;
    overview.value = summary;
    if (!draft.semesterId) {
      suppressAutoApply = true;
      draft.semesterId = semesters.activeSemesterId ?? semesters.semesters[0]?.id ?? 0;
      suppressAutoApply = false;
    }
  })().finally(() => { catalogRequest = null; });
  await catalogRequest;
}
const percent = (value: number, denominator: number) =>
  denominator === 0
    ? 'Нет данных'
    : `${value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })}%`;
async function load(reset = false) {
  if (!alive) return;
  clearApplyTimer();
  const revision = ++run;
  loading.value = true;
  error.value = null;
  if (reset) draft.page = 0;
  try {
    await feature.initialize();
    if (!feature.allowed('VIEW_STATS')) throw new Error('Нет права просмотра статистики группы.');
    if (!alive || revision !== run) return;
    await ensureCatalog();
    if (!alive || revision !== run) return;
    const frozenDraft = JSON.stringify(draft);
    const query: HeadmanStatsQuery = {
      ...(draft.semesterId ? { semesterId: draft.semesterId } : {}),
      subjectId: draft.subjectId || null,
      lessonTypes: draft.subjectId ? [...draft.types] : [],
      page: draft.page,
      size: 30,
      sorts: [{ field: draft.sortField, descending: draft.descending }],
      filters: draft.contains.trim() ? [{ field: 'displayName', contains: draft.contains.trim() }] : [],
    };
    const trendQuery: HeadmanStatsTrendQuery = {
      ...(draft.semesterId ? { semesterId: draft.semesterId } : {}),
      bucketUnit: draft.mode === 'WEEK' ? 'DAY' : draft.bucket,
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
    if (!alive || revision !== run || frozenDraft !== JSON.stringify(draft)) return;
    data.value = result;
    if (selected.value && !result.rows.some(row => row.studentId === selected.value)) selected.value = 0;
    trend.value = dynamics;
    applied.value = query;
    appliedTrend.value = trendQuery;
    appliedDraft.value = frozenDraft;
  } catch (cause) {
    if (alive && revision === run) error.value = message(cause);
  } finally {
    if (alive && revision === run) loading.value = false;
  }
}
watch(() => JSON.stringify([draft.semesterId, draft.subjectId, draft.types]), () => scheduleApply(0), { flush: 'sync' });
watch(() => draft.contains, () => scheduleApply(300), { flush: 'sync' });
onMounted(() => load());
onBeforeUnmount(() => { alive = false; run++; clearApplyTimer(); });
useWebLiveRefresh(() => load(), {
  blocked: () => loading.value || dirty.value || !!selected.value,
});
</script>
<template>
  <HeadmanPage title="Статистика" class="headman-analytics headman-statistics-page" hide-title :error="error" @reload="load()">
    <HeadmanMetricCards v-if="data" :metrics="data.summary" :context="data.context.subjectName || ''" />
    <div class="headman-filter-bar headman-statistics-filters">
      <WebSelect v-model="draft.semesterId" label="Семестр" :options="(catalog?.semesters ?? []).map(item => ({ value: item.id, label: item.name + (item.archived ? ' · архив' : '') }))" :disabled="!catalog" />
      <WebSelect v-model="draft.subjectId" label="Предмет" :options="subjectOptions" :disabled="!data" @change="chooseSubject" />
      <fieldset v-if="types.length" class="headman-type-picker headman-statistics-types"><legend>Типы занятий</legend><div><label v-for="type in types" :key="type.code" class="headman-choice"><input v-model="draft.types" type="checkbox" :value="type.code" /><span>{{ type.label }}</span></label></div></fieldset>
      <label class="web-field headman-statistics-search">Поиск студента<input v-model="draft.contains" type="search" placeholder="ФИО студента" autocomplete="off" /></label>
      <button type="button" class="web-button headman-statistics-reset" @click="resetFilters">Сбросить</button>
    </div>
    <section v-if="data" class="web-stack headman-statistics-results">
      <h2>{{ data.context.subjectId ? 'Статистика по предмету' : 'Статистика по группе' }}</h2>
      <div class="headman-analytics-scroll headman-stats-viewport" tabindex="0" role="region" aria-label="Статистика студентов группы">
        <table class="headman-statistics-table"><thead>
          <tr><th rowspan="2" scope="col" class="headman-stats-name" :aria-sort="sortAria('displayName')"><button type="button" :disabled="loading" @click="sortBy('displayName')">ФИО {{ sortIndicator('displayName') }}</button></th>
            <th rowspan="2" scope="col" :aria-sort="sortAria('presentPercent')"><button type="button" :disabled="loading" @click="sortBy('presentPercent')">% «+» {{ sortIndicator('presentPercent') }}</button></th><th rowspan="2" scope="col">% «+» и «у»</th><th rowspan="2" scope="col" :aria-sort="sortAria('excusedPercent')"><button type="button" :disabled="loading" @click="sortBy('excusedPercent')">% «у» {{ sortIndicator('excusedPercent') }}</button></th><th rowspan="2" scope="col" :aria-sort="sortAria('absentPercent')"><button type="button" :disabled="loading" @click="sortBy('absentPercent')">% «н» {{ sortIndicator('absentPercent') }}</button></th><th colspan="3" scope="colgroup">Апелляции</th><th colspan="3" scope="colgroup">Уважительные причины</th><th colspan="4" scope="colgroup">Источники «+»</th></tr>
          <tr><th scope="col">Подано</th><th scope="col">Одобрено</th><th scope="col">Отклонено</th><th scope="col">Подано</th><th scope="col">Одобрено</th><th scope="col">Отклонено</th><th scope="col">Геоотметка</th><th scope="col">Апелляция</th><th scope="col">Сбой гео</th><th scope="col">Староста</th></tr>
        </thead><tbody><template v-for="row in data.rows" :key="row.studentId">
          <tr :class="{ 'is-selected': selected === row.studentId }"><th scope="row" class="headman-stats-name"><button type="button" class="headman-student-trigger" :aria-expanded="selected === row.studentId" @click="selected = selected === row.studentId ? 0 : row.studentId"><span>{{ row.displayName }}</span><MobileIcon name="chevron-down" /></button></th><td v-for="key in ['present', 'presentOrExcused', 'excused', 'absent'] as const" :key="key"><strong>{{ percent(row.metrics[key].percent, row.metrics[key].denominator) }}</strong><small>{{ row.metrics[key].numerator }}/{{ row.metrics[key].denominator }}</small></td><td>{{ row.lateCheckin.submitted }}</td><td>{{ row.lateCheckin.approved }}</td><td>{{ row.lateCheckin.rejected }}</td><td>{{ row.excuse.submitted }}</td><td>{{ row.excuse.approved }}</td><td>{{ row.excuse.rejected }}</td><td>{{ row.sources.studentGeo }}</td><td>{{ row.sources.manualRequest }}</td><td>{{ row.sources.autoAfterGeoFailure }}</td><td>{{ row.sources.headmanManual }}</td></tr>
          <tr v-if="selected === row.studentId" class="headman-student-reveal"><td colspan="15"><StudentDetail :key="`${selected}:${applied?.semesterId}`" :feature="feature" :student-id="selected" :semester-id="applied?.semesterId" /></td></tr>
        </template></tbody></table>
      </div>
      <p v-if="!data.rows.length" class="web-notice">{{ data.emptyState === 'FILTERED_EMPTY' ? 'Никто не подходит под фильтры.' : 'Нет завершённых пар или студентов для расчёта.' }}</p>
      <div class="headman-analytics-footer headman-statistics-table-footer"><div class="headman-actions"><button class="web-button" :disabled="!data.hasPrevious || loading" @click="draft.page--; load()">Назад</button><span class="web-caption">{{ data.page + 1 }} / {{ Math.max(1, data.totalPages) }} · {{ data.totalElements }} студентов</span><button class="web-button" :disabled="!data.hasNext || loading" @click="draft.page++; load()">Далее</button></div>
        <div class="headman-actions headman-statistics-export-controls"><WebSelect v-model="format" class="headman-statistics-format" label="Формат" :options="data.formats.map(item => ({ value: item.code, label: item.label }))" /><ReportExport :transport="feature.transport" scope="statistics" :report="exportReport" :disabled="loading" :new-intent-disabled="dirty" label="Скачать срез" /></div>
      </div>
    </section>
    <section class="web-stack">
      <h2>Динамика посещаемости группы</h2>
      <div class="headman-period-toolbar">
        <StudentPeriodPager v-if="draft.mode === 'WEEK'" :label="`${dateLabel(draft.week)} — ${dateLabel(addDays(draft.week, 6))}`" unit="период динамики" :busy="loading" :previous-disabled="!!selectedSemester && addDays(draft.week, -7) < weekStart(selectedSemester.dateFrom)" :next-disabled="!!selectedSemester && addDays(draft.week, 7) > selectedSemester.dateTo" @step="stepWeek" @reset="draft.week = weekStart(today()); load()"><WebDateField v-model="draft.week" label="Неделя динамики" :trigger-label="`${dateLabel(draft.week)} — ${dateLabel(addDays(draft.week, 6))}`" :min="selectedSemester ? weekStart(selectedSemester.dateFrom) : undefined" :max="selectedSemester?.dateTo" :today="today()" required :disabled="loading" @change="draft.week = weekStart(draft.week); load()" /></StudentPeriodPager>
        <p v-else class="headman-period-title">{{ draft.mode === 'SUBJECT' ? data?.context.subjectName : selectedSemester?.name }}</p>
        <div class="headman-actions" role="group" aria-label="Период динамики"><button v-for="item in [{ value: 'SEMESTER', label: 'За семестр' }, { value: 'WEEK', label: 'Неделя' }, { value: 'SUBJECT', label: 'По предмету' }] as const" :key="item.value" type="button" class="web-button" :class="{ 'web-button-selected': draft.mode === item.value }" :aria-pressed="draft.mode === item.value" :disabled="loading || (item.value === 'SUBJECT' && !draft.subjectId)" @click="draft.mode = item.value; load()">{{ item.label }}</button></div>
      </div>
      <div class="headman-analytics-footer"><WebSelect v-if="draft.mode !== 'WEEK'" v-model="draft.bucket" label="Шаг графика" :options="[{ value: 'DAY', label: 'Дни' }, { value: 'WEEK', label: 'Недели' }]" :disabled="loading" @change="load()" /><div v-if="trend" class="headman-actions headman-statistics-export-controls headman-statistics-trend-download"><WebSelect v-model="trendFormat" class="headman-statistics-format" label="Формат" :options="trend.formats.map(item => ({ value: item.code, label: item.label }))" /><ReportExport :transport="feature.transport" scope="trend" :report="trendReport" :disabled="loading" :new-intent-disabled="dirty" label="Скачать динамику" /></div></div>
      <TrendChart v-if="trend" :points="trend.points" :from="appliedTrend?.mode === 'WEEK' ? appliedTrend.weekStart : trend.context.semesterFrom" :to="appliedTrend?.mode === 'WEEK' ? addDays(appliedTrend.weekStart, 6) : trend.context.semesterTo" />
      <p v-else class="web-caption">{{ loading ? 'Загрузка динамики…' : 'Динамика пока недоступна.' }}</p>
    </section>
  </HeadmanPage>
</template>
