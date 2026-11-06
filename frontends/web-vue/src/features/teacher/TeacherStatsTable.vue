<script setup lang="ts">
import { computed, ref } from 'vue';
import type {
  TeacherStatsFilter,
  TeacherStatsResponse,
  TeacherStatsSort,
} from '../../../../mobile-core/src/features/teacher/teacher-client';

const props = defineProps<{
  data: TeacherStatsResponse;
  sorts: readonly TeacherStatsSort[];
  filters: readonly TeacherStatsFilter[];
  busy: boolean;
}>();
const emit = defineEmits<{
  sort: [column: string];
  group: [groupId: number];
}>();
const tableRegion = ref<HTMLElement | null>(null);
function focusGroup(groupId: number) {
  const target = tableRegion.value?.querySelector<HTMLButtonElement>(`[data-teacher-group="${groupId}"]`);
  target?.focus();
  return !!target;
}
defineExpose({ focusGroup });
const nameColumn = computed(() => (props.data.scope === 'groups' ? 'groupName' : 'displayName'));
const metrics = [
  { key: 'present', label: 'Присутствия', symbol: '+' },
  { key: 'presentOrExcused', label: 'Присутствия и уважительные', symbol: '+ и у' },
  { key: 'excused', label: 'Уважительные причины', symbol: 'у' },
  { key: 'absent', label: 'Отсутствия', symbol: 'н' },
] as const;
const columns = computed(() => [
  { key: nameColumn.value, label: props.data.scope === 'groups' ? 'Группа' : 'ФИО', symbol: '' },
  ...metrics,
  ...(props.data.scope === 'groups'
    ? [{ key: 'lessonsCount', label: 'Пар учтено', symbol: '' }]
    : []),
]);
const rows = computed(() =>
  props.data.scope === 'groups'
    ? props.data.groups.map((row) => ({ ...row, id: row.groupId, name: row.groupName }))
    : props.data.students.map((row) => ({
        ...row,
        id: row.studentId,
        name: row.displayName,
        lessonsCount: null,
      })),
);
const defaultSort = { column: 'present', descending: false };
const appliedSorts = computed(() => props.sorts.length ? props.sorts : [defaultSort]);
const primarySort = computed(() => appliedSorts.value[0]);
const sortSummary = computed(() => appliedSorts.value.map(sort =>
  `${columns.value.find(column => column.key === sort.column)?.label ?? sort.column} ${sort.descending ? '↓' : '↑'}`,
).join(', '));
function sortLabel(column: string) {
  const index = props.sorts.findIndex((sort) => sort.column === column);
  return index >= 0 ? `${index + 1} ${props.sorts[index]!.descending ? '↓' : '↑'}` : '';
}
</script>
<template>
  <p class="web-caption">
    Сортировка: {{ sortSummary }}<template v-if="!sorts.length">
      (по умолчанию)
    </template>
  </p>
  <div
    ref="tableRegion"
    class="teacher-table-scroll"
    tabindex="0"
    aria-label="Статистика посещаемости"
    :aria-busy="busy"
  >
    <table class="teacher-table teacher-stats-table">
      <thead>
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            :aria-sort="
              primarySort?.column === column.key
                ? primarySort.descending
                  ? 'descending'
                  : 'ascending'
                : undefined
            "
          >
            <div class="teacher-column-tools">
              <button
                class="teacher-text-button"
                :aria-disabled="busy"
                :aria-label="`${column.label}: сортировка ${sortLabel(column.key) || (primarySort?.column === column.key ? 'по возрастанию, по умолчанию' : 'не задана')}`"
                @click="!busy && emit('sort', column.key)"
              >
                {{ column.label }} <span>{{ sortLabel(column.key) }}</span><span
                  v-if="column.symbol"
                  class="web-caption"
                >{{ column.symbol }}</span>
              </button>
            </div>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.id"
        >
          <th scope="row">
            <button
              v-if="data.scope === 'groups'"
              class="teacher-text-button"
              :data-teacher-group="row.id"
              :aria-disabled="busy"
              :aria-label="`Открыть студентов группы ${row.name}`"
              @click="!busy && emit('group', row.id)"
            >
              {{ row.name }}
            </button>
            <template v-else>
              {{ row.name }}
            </template>
          </th><td
            v-for="metric in metrics"
            :key="metric.key"
          >
            <span>{{
              row[metric.key].denominator
                ? `${row[metric.key].percent.toLocaleString('ru', { maximumFractionDigits: 1 })} %`
                : 'Нет данных'
            }}</span><span class="web-caption">{{ row[metric.key].numerator }} / {{ row[metric.key].denominator }}</span>
          </td><td v-if="data.scope === 'groups'">
            {{ row.lessonsCount }}
          </td>
        </tr>
        <tr v-if="!rows.length">
          <td :colspan="columns.length">
            {{
              filters.length
                ? 'По заданным условиям ничего не найдено.'
                : 'За этот период данных пока нет.'
            }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
