<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { StudentAttendanceResponse, StudentStatisticsOverviewResponse, StudentStatisticsSeriesPoint } from '../../../../mobile-core/src/api/types';
import { useStudentResource, useStudentScope } from './student-state';
import { formatLessonDate, monday, percent, serverDate, shiftDate } from './student-format';
import StudentRegion from './StudentRegion.vue';
import StudentNotice from './StudentNotice.vue';
import StudentMetrics from './StudentMetrics.vue';
import StudentTrend from './StudentTrend.vue';
import StudentRanking from './StudentRanking.vue';
import StudentSubjectStats from './StudentSubjectStats.vue';
import StudentPeriodPager from './StudentPeriodPager.vue';
const scope = useStudentScope(),
  stats = useStudentResource<StudentStatisticsOverviewResponse>(scope),
  attendance = useStudentResource<StudentAttendanceResponse>(scope);
const range = ref<'week' | 'semester'>('semester');
const week = ref('');
const chartSemester = computed(() => attendance.data.value?.semester ?? scope.session.value?.semester);
const graph = computed<readonly StudentStatisticsSeriesPoint[]>(() => range.value === 'semester'
  ? stats.data.value?.semesterSeries ?? []
  : attendance.data.value?.graph.days.filter((point) => point.dateFrom >= week.value && point.dateFrom <= shiftDate(week.value, 6)) ?? []);
const comparison = ref<'personal' | 'group'>('personal');
const canCompare = computed(() => graph.value.some((point) => point.groupPresent != null));
watch(canCompare, (available) => { if (!available) comparison.value = 'personal'; });
const ranking = ref(false),
  expanded = ref<string[]>([]);
type Column = 'name' | 'present' | 'presentOrExcused' | 'excused' | 'absent';
const sorts = ref<{ column: Column; descending: boolean }[]>([]);
const rows = computed(() =>
  [...(stats.data.value?.subjects ?? [])]
    .sort((a, b) => {
      for (const sort of sorts.value) {
        const compare =
          sort.column === 'name'
            ? a.name.localeCompare(b.name, 'ru')
            : (a.metrics[sort.column].percent ?? -1) - (b.metrics[sort.column].percent ?? -1);
        if (compare) return sort.descending ? -compare : compare;
      }
      return a.name.localeCompare(b.name, 'ru');
    }),
);
function sort(column: Column) {
  const current = sorts.value.find((s) => s.column === column);
  if (!current) sorts.value.push({ column, descending: false });
  else if (!current.descending) current.descending = true;
  else sorts.value = sorts.value.filter((s) => s.column !== column);
}
function sortLabel(column: Column) {
  const index = sorts.value.findIndex((s) => s.column === column);
  return index < 0 ? '' : sorts.value[index]!.descending ? '↓' : '↑';
}
async function load() {
  const semester = scope.session.value?.semester;
  if (!semester) {
    stats.clear();
    attendance.clear();
    return;
  }
  if (!week.value) week.value = monday(serverDate(scope.session.value?.serverNow) ?? semester.startsOn);
  await Promise.all([
    stats.load(() => scope.api.getStatistics(semester.id), true),
    loadAttendance(),
  ]);
}
async function loadAttendance() {
  const semester = scope.session.value?.semester;
  if (semester) await attendance.load(() => scope.api.getAttendance(semester.id), true);
  else attendance.clear();
}
watch(scope.refreshed, load);
watch(scope.session, (s) => {
  if (!s) {
    stats.clear();
    attendance.clear();
  }
});
function toggle(id: string) {
  expanded.value = expanded.value.includes(id)
    ? expanded.value.filter((s) => s !== id)
    : [...expanded.value, id];
}
</script>
<template>
  <StudentRegion
    :scope="scope"
    screen="WEB-STUDENT-STATISTICS"
  >
    <h1>Статистика</h1><StudentNotice
      :loading="scope.loading.value || stats.loading.value"
      :error="scope.error.value || stats.error.value"
      :empty="
        scope.session.value && !scope.session.value.semester ? 'Активного семестра пока нет.' : ''
      "
      @retry="scope.load"
    />
    <template v-if="stats.data.value">
      <div class="student-dashboard-pair student-statistics-summary" :class="{ 'has-expanded-ranking': ranking }">
        <StudentMetrics :metrics="stats.data.value.metrics" /><section
          class="student-metric web-stack student-statistics-ranking-card"
        >
          <h2>Рейтинг за семестр</h2><p class="student-metric-value">
            {{
              stats.data.value.ownRank.available && stats.data.value.ownRank.position !== null
                ? `${stats.data.value.ownRank.position} / ${stats.data.value.ownRank.participantCount}`
                : '—'
            }}
          </p><p
            v-if="!stats.data.value.ownRank.available"
            class="web-caption"
          >
            Позиция появится после учитываемых пар.
          </p><button
            class="web-button student-ranking-toggle"
            :aria-expanded="ranking"
            @click="ranking = !ranking"
          >
            <span class="student-ranking-toggle-labels">
              <span :aria-hidden="ranking">Рейтинг моей группы</span>
              <span :aria-hidden="!ranking">Скрыть рейтинг группы</span>
            </span>
          </button>
        </section>
        <div v-if="ranking" class="student-statistics-ranking-details">
          <StudentRanking :scope="scope" />
        </div>
      </div>
      <section class="web-stack student-statistics-trend" aria-labelledby="student-dynamics-title">
        <h2 id="student-dynamics-title">Динамика посещаемости</h2>
        <div v-if="chartSemester" class="student-chart-toolbar">
          <StudentPeriodPager
            v-if="range === 'week'"
            :label="`${formatLessonDate(week)} — ${formatLessonDate(shiftDate(week, 6))}`"
            unit="период графика"
            :previous-disabled="week <= monday(chartSemester.startsOn)"
            :next-disabled="shiftDate(week, 7) > chartSemester.endsOn"
            @step="week = shiftDate(week, $event * 7)"
            @reset="week = monday(serverDate(attendance.data.value?.serverNow ?? scope.session.value?.serverNow) ?? chartSemester.startsOn)"
          />
          <p v-else class="student-chart-period-label" role="status">
            {{ formatLessonDate(chartSemester.startsOn) }} — {{ formatLessonDate(chartSemester.endsOn) }}
          </p>
          <div class="student-chart-controls" :class="{ 'has-comparison': canCompare }">
            <div v-if="canCompare" class="student-actions student-chart-comparison" role="group" aria-label="Сравнение графика">
              <button
                v-for="item in [{ id: 'personal', label: 'Личная' }, { id: 'group', label: 'С группой' }] as const"
                :key="item.id" class="web-button" :aria-pressed="comparison === item.id"
                :class="{ 'web-button-selected': comparison === item.id }" @click="comparison = item.id"
              >{{ item.label }}</button>
            </div>
            <div class="student-actions student-chart-range" role="group" aria-label="Период графика">
              <button
                v-for="item in [{ id: 'week', label: 'Эта неделя' }, { id: 'semester', label: 'Весь период' }] as const"
                :key="item.id" class="web-button" :aria-pressed="range === item.id"
                :class="{ 'web-button-selected': range === item.id }" @click="range = item.id"
              >{{ item.label }}</button>
            </div>
          </div>
        </div>
        <StudentNotice v-if="range === 'week'" :loading="attendance.loading.value" :error="attendance.error.value" @retry="loadAttendance" />
        <StudentTrend v-if="range === 'semester' || attendance.data.value" v-model:comparison="comparison" :points="graph" compare />
      </section>
      <section
        class="web-stack"
        aria-labelledby="student-subjects-title"
      >
        <div class="web-page-header">
          <h2 id="student-subjects-title">
            По предметам
          </h2>
        </div><div
          class="student-table-scroll"
          tabindex="0"
          aria-label="Статистика по предметам"
        >
          <table class="student-table student-subjects-table">
            <thead>
              <tr>
                <th
                  v-for="column in [
                    { key: 'name', label: 'Предмет' },
                    { key: 'present', label: 'Доля «+»' },
                    { key: 'presentOrExcused', label: 'Доля «+» и «у»' },
                    { key: 'excused', label: 'Уважительные' },
                    { key: 'absent', label: 'Отсутствия' },
                  ] as const"
                  :key="column.key"
                  scope="col"
                  :aria-sort="
                    sorts[0]?.column === column.key
                      ? sorts[0].descending
                        ? 'descending'
                        : 'ascending'
                      : undefined
                  "
                >
                  <button
                    class="student-text-button"
                    :aria-label="`${column.label}: сортировка ${sortLabel(column.key) || 'не задана'}`"
                    @click="sort(column.key)"
                  >
                    {{ column.label }} {{ sortLabel(column.key) }}
                  </button>
                </th>
              </tr>
            </thead><tbody>
              <template
                v-for="subject in rows"
                :key="subject.id"
              >
                <tr>
                  <th scope="row">
                    <button
                      class="student-text-button"
                      :aria-expanded="expanded.includes(subject.id)"
                      @click="toggle(subject.id)"
                    >
                      {{ subject.name }}
                    </button>
                  </th><td
                    v-for="metric in ['present', 'presentOrExcused', 'excused', 'absent'] as const"
                    :key="metric"
                  >
                    {{ percent(subject.metrics[metric].percent)
                    }}<small class="web-caption">{{ subject.metrics[metric].count }} / {{ subject.metrics.held }}</small>
                  </td>
                </tr><tr v-if="expanded.includes(subject.id)">
                  <td colspan="5">
                    <StudentSubjectStats
                      :scope="scope"
                      :subject-id="subject.id"
                    />
                  </td>
                </tr>
              </template><tr v-if="!rows.length">
                <td colspan="5">
                  Предметов пока нет.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </StudentRegion>
</template>
