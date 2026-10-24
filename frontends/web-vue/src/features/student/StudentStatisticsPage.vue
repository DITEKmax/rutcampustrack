<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { StudentStatisticsOverviewResponse } from '../../../../mobile-core/src/api/types';
import { useStudentResource, useStudentScope } from './student-state';
import { percent } from './student-format';
import StudentRegion from './StudentRegion.vue';
import StudentNotice from './StudentNotice.vue';
import StudentMetrics from './StudentMetrics.vue';
import StudentTrend from './StudentTrend.vue';
import StudentRanking from './StudentRanking.vue';
import StudentSubjectStats from './StudentSubjectStats.vue';
const scope = useStudentScope(),
  stats = useStudentResource<StudentStatisticsOverviewResponse>(scope);
const ranking = ref(false),
  expanded = ref<string[]>([]),
  search = ref('');
type Column = 'name' | 'present' | 'presentOrExcused' | 'excused' | 'absent';
const sorts = ref<{ column: Column; descending: boolean }[]>([]);
const rows = computed(() =>
  [...(stats.data.value?.subjects ?? [])]
    .filter((s) => s.name.toLocaleLowerCase('ru').includes(search.value.toLocaleLowerCase('ru')))
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
  return index < 0 ? '' : `${index + 1}${sorts.value[index]!.descending ? '↓' : '↑'}`;
}
async function load() {
  const semester = scope.session.value?.semester;
  if (!semester) {
    stats.clear();
    return;
  }
  await stats.load(() => scope.api.getStatistics(semester.id));
}
watch(scope.refreshed, load);
watch(scope.session, (s) => {
  if (!s) stats.clear();
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
      <div class="student-dashboard-pair">
        <StudentMetrics :metrics="stats.data.value.metrics" /><section
          class="student-metric web-stack"
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
            class="web-button"
            :aria-expanded="ranking"
            @click="ranking = !ranking"
          >
            {{ ranking ? 'Скрыть рейтинг группы' : 'Рейтинг моей группы' }}
          </button><StudentRanking
            v-if="ranking"
            :scope="scope"
          />
        </section>
      </div>
      <h2>Динамика за семестр</h2><StudentTrend
        :points="stats.data.value.semesterSeries"
        compare
      />
      <section
        class="web-stack"
        aria-labelledby="student-subjects-title"
      >
        <div class="web-page-header">
          <h2 id="student-subjects-title">
            По предметам
          </h2><label class="web-field">Найти предмет<input
            v-model="search"
            type="search"
          ></label>
        </div><p class="web-caption">
          {{ scope.session.value?.semester?.name }} · показатели рассчитаны сервером по завершённым
          учитываемым парам.
        </p><div
          class="student-table-scroll"
          tabindex="0"
          aria-label="Статистика по предметам"
        >
          <table class="student-table">
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
                  Предметов с такими условиями нет.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </StudentRegion>
</template>
