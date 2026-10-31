<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import type {
  TeacherStatsFilter,
  TeacherStatsResponse,
  TeacherStatsSort,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import {
  parseTeacherStatsRanges,
} from '../../../../mobile-core/src/features/teacher/teacher-stats-route';

const props = defineProps<{
  data: TeacherStatsResponse;
  sorts: readonly TeacherStatsSort[];
  filters: readonly TeacherStatsFilter[];
  busy: boolean;
}>();
const emit = defineEmits<{
  sort: [column: string];
  filter: [filters: TeacherStatsFilter[]];
  reset: [];
  group: [groupId: number];
}>();
const tableRegion = ref<HTMLElement | null>(null);
function focusGroup(groupId: number) {
  tableRegion.value?.querySelector<HTMLButtonElement>(`[data-teacher-group="${groupId}"]`)?.focus();
}
defineExpose({ focusGroup });
const filterColumn = ref<string | null>(null),
  filterError = ref('');
const drafts = reactive<Record<string, { minimum: string; maximum: string }>>({});
const textDraft = ref(''),
  firstInput = ref<HTMLInputElement | null>(null);
let trigger: HTMLButtonElement | null = null;
let returnFocusAfterLoad = false;
watch(() => props.busy, async busy => {
  if (busy || !returnFocusAfterLoad) return;
  await nextTick();
  if (trigger?.isConnected && (document.activeElement === document.body || document.activeElement === trigger)) trigger.focus();
  returnFocusAfterLoad = false;
});
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
function sortLabel(column: string) {
  const index = props.sorts.findIndex((sort) => sort.column === column);
  return index >= 0 ? `${index + 1} ${props.sorts[index]!.descending ? '↓' : '↑'}` : '';
}
async function openFilter(column: string, event: MouseEvent) {
  trigger = event.currentTarget as HTMLButtonElement;
  filterColumn.value = column;
  filterError.value = '';
  const current = props.filters.find((filter) => filter.column === column);
  textDraft.value = current?.contains ?? '';
  drafts[column] = {
    minimum: String(current?.minPercent ?? current?.minValue ?? ''),
    maximum: String(current?.maxPercent ?? current?.maxValue ?? ''),
  };
  await nextTick();
  firstInput.value?.focus();
}
function closeFilter() {
  filterColumn.value = null;
  returnFocusAfterLoad = false;
  trigger?.focus();
}
function updateRange(bound: 'minimum' | 'maximum', event: Event) {
  const column = filterColumn.value;
  if (column && drafts[column]) drafts[column]![bound] = (event.target as HTMLInputElement).value;
}
function apply() {
  const column = filterColumn.value;
  if (!column) return;
  try {
    const next = props.filters.filter((filter) => filter.column !== column);
    if (column === nameColumn.value) {
      if (textDraft.value.trim()) next.push({ column, contains: textDraft.value.trim() });
    } else next.push(...parseTeacherStatsRanges(props.data.scope, { [column]: drafts[column]! }));
    emit('filter', next);
    closeFilter();
    returnFocusAfterLoad = true;
  } catch (cause) {
    filterError.value = cause instanceof Error ? cause.message : 'Проверь диапазон.';
  }
}
</script>
<template>
  <div
    ref="tableRegion"
    class="teacher-table-scroll"
    tabindex="0"
    aria-label="Статистика посещаемости"
  >
    <table class="teacher-table teacher-stats-table">
      <thead>
        <tr>
          <th
            v-for="column in columns"
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
            <div class="teacher-column-tools">
              <button
                class="teacher-text-button"
                :disabled="busy"
                :aria-label="`${column.label}: сортировка ${sortLabel(column.key) || 'не задана'}`"
                @click="emit('sort', column.key)"
              >
                {{ column.label }} <span>{{ sortLabel(column.key) }}</span><span
                  v-if="column.symbol"
                  class="web-caption"
                >{{ column.symbol }}</span>
              </button><button
                class="web-button teacher-filter-button"
                :disabled="busy"
                :aria-expanded="filterColumn === column.key"
                :aria-label="`Фильтр: ${column.label}${filters.some((filter) => filter.column === column.key) ? ', активен' : ''}`"
                @click="openFilter(column.key, $event)"
              >
                {{
                  filters.some((filter) => filter.column === column.key) ? 'Отбор •' : 'Отбор'
                }}
              </button>
            </div>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="filterColumn">
          <td :colspan="columns.length">
            <form
              class="teacher-toolbar teacher-column-filter"
              @submit.prevent="apply"
              @keydown.esc.prevent="closeFilter"
            >
              <label
                v-if="filterColumn === nameColumn"
                class="web-field"
              >Содержит<input
                ref="firstInput"
                v-model="textDraft"
                maxlength="128"
              ></label><template v-else>
                <label class="web-field">От{{ filterColumn === 'lessonsCount' ? '' : ', %'
                }}<input
                  ref="firstInput"
                  :value="drafts[filterColumn]!.minimum"
                  type="number"
                  min="0"
                  :max="filterColumn === 'lessonsCount' ? undefined : 100"
                  step="any"
                  @input="updateRange('minimum', $event)"
                ></label><label class="web-field">До{{ filterColumn === 'lessonsCount' ? '' : ', %'
                }}<input
                  :value="drafts[filterColumn]!.maximum"
                  type="number"
                  min="0"
                  :max="filterColumn === 'lessonsCount' ? undefined : 100"
                  step="any"
                  @input="updateRange('maximum', $event)"
                ></label>
              </template><button class="web-button">
                Применить отбор
              </button><button
                type="button"
                class="web-button"
                @click="closeFilter"
              >
                Закрыть отбор
              </button><p
                v-if="filterError"
                class="web-error"
                role="alert"
              >
                {{ filterError }}
              </p>
            </form>
          </td>
        </tr>
        <tr
          v-for="row in rows"
          :key="row.id"
        >
          <th scope="row">
            <button
              v-if="data.scope === 'groups'"
              class="teacher-text-button"
              :data-teacher-group="row.id"
              :disabled="busy"
              :aria-label="`Открыть студентов группы ${row.name}`"
              @click="emit('group', row.id)"
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
  <div class="teacher-toolbar">
    <button
      class="web-button"
      :disabled="busy || (!sorts.length && !filters.length)"
      @click="emit('reset')"
    >
      Сбросить отбор и сортировки
    </button><p
      v-if="sorts.length"
      class="web-caption"
    >
      Порядок сортировки:
      {{
        sorts
          .map(
            (sort, index) =>
              `${index + 1}. ${columns.find((column) => column.key === sort.column)?.label}`,
          )
          .join(' · ')
      }}
    </p>
  </div>
  <p class="web-caption">
    Знаменатель — прошедшие и не отменённые пары. Фильтры и сортировки применяются сервером.
  </p>
</template>
