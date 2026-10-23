<script setup lang="ts">
import { useWebLiveRefresh } from '../../app/live';
import { onMounted, ref, shallowRef } from 'vue';
import {
  AdminAttendanceClient,
  type AdminAttendanceReport,
  type AdminAttendanceSemester,
  type AdminAttendanceSort,
} from '../../../../mobile-core/src/features/admin-attendance/admin-attendance-client';
import { useAdmin, dateLabel } from './use-admin';
import AdminState from './AdminState.vue';
import ExportControl from './ExportControl.vue';
import './admin.pcss';
const admin = useAdmin();
const { loading, busy, error, notice } = admin;
const client = new AdminAttendanceClient(admin.options);
const semesters = shallowRef<readonly AdminAttendanceSemester[]>([]);
const semesterId = ref(''),
  search = ref(''),
  sort = ref<AdminAttendanceSort>('GROUP_NAME'),
  direction = ref<'ASC' | 'DESC'>('ASC');
const report = shallowRef<AdminAttendanceReport | null>(null);
const applied = shallowRef(query());
const sortLabels: Record<AdminAttendanceSort, string> = {
  GROUP_NAME: 'Название группы',
  PRESENT_PERCENT: 'Доля «+»',
  PRESENT_OR_EXCUSED_PERCENT: 'Доля «+» и «у»',
  COUNTED_LESSONS: 'Учтённые занятия',
  PRESENT: 'Присутствия',
  ABSENT: 'Пропуски',
  EXCUSED: 'Уважительные пропуски',
};
function query() {
  return {
    semesterId: semesterId.value,
    search: search.value,
    sort: sort.value,
    direction: direction.value,
  };
}
async function load(page = 0) {
  if (!applied.value.semesterId) return;
  const value = await client.getReport({ ...applied.value, page, size: 30 }, admin.signal);
  admin.assertCurrent();
  report.value = value;
}
function apply() {
  applied.value = query();
  report.value = null;
  void admin.run(() => load());
}
onMounted(() =>
  admin.init('DASHBOARD_VIEW', async () => {
    const value = await client.getSemesters(admin.signal);
    admin.assertCurrent();
    semesters.value = value;
    semesterId.value = value.find((s) => s.active)?.id ?? value[0]?.id ?? '';
    applied.value = query();
    await load();
  }),
);
const percent = (value: number | null) =>
  value === null ? 'Нет данных' : `${value.toLocaleString('ru-RU', { maximumFractionDigits: 2 })}%`;
useWebLiveRefresh(
  () =>
    admin.run(async () => {
      await admin.requirePermission('DASHBOARD_VIEW');
      await load(report.value?.page ?? 0);
    }),
  { blocked: () => busy.value },
);
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <h1>Посещаемость групп</h1>
    </header><AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(() => load())"
    />
    <form
      v-if="semesters.length && admin.can('DASHBOARD_VIEW')"
      class="admin-toolbar"
      @submit.prevent="apply"
    >
      <label class="web-field">Семестр<select v-model="semesterId"><option
        v-for="semester in semesters"
        :key="semester.id"
        :value="semester.id"
      >{{ semester.name }}{{ semester.archived ? ' · архив' : '' }}</option></select></label><label class="web-field">Группа<input
        v-model="search"
        type="search"
        placeholder="Название группы"
      ></label><label class="web-field">Сортировка<select v-model="sort"><option
        v-for="(label, code) in sortLabels"
        :key="code"
        :value="code"
      >{{ label }}</option></select></label><label class="web-field">Порядок<select v-model="direction"><option value="ASC">По возрастанию</option><option value="DESC">По убыванию</option></select></label><button
        class="web-button"
        :disabled="busy"
      >
        Показать
      </button>
    </form><p
      v-else-if="!loading && !error"
      role="status"
    >
      Нет семестров для отчёта.
    </p>
    <template v-if="report && admin.can('DASHBOARD_VIEW')">
      <p class="web-caption">
        {{ report.semester.name }} · {{ dateLabel(report.semester.dateFrom) }} —
        {{ dateLabel(report.semester.dateTo) }}. Обновлено
        {{ new Date(report.observedAt).toLocaleString('ru-RU') }}.
      </p><div
        v-if="report.rows.length"
        class="admin-table-wrap"
      >
        <table class="admin-table">
          <caption class="web-sr-only">
            Посещаемость групп за выбранный семестр
          </caption><thead>
            <tr>
              <th scope="col">
                Группа
              </th><th scope="col">
                Учтено занятий
              </th><th scope="col">
                «+»
              </th><th scope="col">
                «н»
              </th><th scope="col">
                «у»
              </th><th scope="col">
                Доля «+»
              </th><th scope="col">
                Доля «+» и «у»
              </th>
            </tr>
          </thead><tbody>
            <tr
              v-for="row in report.rows"
              :key="row.groupId"
            >
              <td>
                {{ row.groupName
                }}<span
                  v-if="row.dataState === 'NO_DATA'"
                  class="admin-subtext"
                >Нет учтённых данных</span>
              </td><td>{{ row.countedLessons }}</td><td>{{ row.presentCount }}</td><td>{{ row.absentCount }}</td><td>{{ row.excusedCount }}</td><td>{{ percent(row.presentPercent) }}</td><td>{{ percent(row.presentOrExcusedPercent) }}</td>
            </tr>
          </tbody>
        </table>
      </div><p
        v-else
        role="status"
      >
        Для выбранных фильтров нет групп.
      </p><div class="admin-pager">
        <div class="admin-actions">
          <button
            class="web-button"
            :disabled="busy || report.page === 0"
            @click="admin.run(() => load(report!.page - 1))"
          >
            Назад
          </button><span v-if="report.totalPages">{{ report.page + 1 }} / {{ report.totalPages }} ·
            {{ report.totalElements }} групп</span><button
            class="web-button"
            :disabled="busy || report.page + 1 >= report.totalPages"
            @click="admin.run(() => load(report!.page + 1))"
          >
            Дальше
          </button>
        </div><ExportControl
          kind="attendance"
          :selector="applied"
        />
      </div>
    </template>
  </section>
</template>
