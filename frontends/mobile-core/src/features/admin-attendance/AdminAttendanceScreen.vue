<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { adminHasPermission, type AdminAccessProfile } from '../admin-access/admin-access-client';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import AdminNotice from '../../shared/components/AdminNotice.vue';
import ExportButton from '../../shared/components/ExportButton.vue';
import type {
  ReportDownloadFormat,
  ReportDownloadPort,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client';
import type {
  AdminAttendanceClient,
  AdminAttendanceReport,
  AdminAttendanceSemester,
  AdminAttendanceSort,
} from './admin-attendance-client';
import './admin-attendance-screen.pcss';
const props = withDefaults(
  defineProps<{
    client: AdminAttendanceClient;
    authority: AdminAccessProfile | null;
    reportDownload?: ReportDownloadPort | null;
    showBack?: boolean;
    online?: boolean;
  }>(),
  { reportDownload: null, showBack: true, online: true },
);
const emit = defineEmits<{ back: []; 'owner-error': [error: unknown] }>();
const canView = computed(
  () => props.online && adminHasPermission(props.authority, 'DASHBOARD_VIEW'),
);
const semesters = ref<readonly AdminAttendanceSemester[]>([]);
const semesterId = ref(''),
  search = ref(''),
  appliedSearch = ref('');
const sort = ref<AdminAttendanceSort>('GROUP_NAME'),
  direction = ref<'ASC' | 'DESC'>('ASC'),
  page = ref(0);
const report = ref<AdminAttendanceReport | null>(null),
  loading = ref(false),
  error = ref(false);
let sequence = 0,
  disposed = false,
  controller: AbortController | null = null;
const sortOptions: readonly { value: AdminAttendanceSort; label: string }[] = [
  { value: 'GROUP_NAME', label: 'По группе' },
  { value: 'PRESENT_PERCENT', label: 'По посещаемости % +' },
  { value: 'PRESENT_OR_EXCUSED_PERCENT', label: 'По % (+ и у)' },
  { value: 'COUNTED_LESSONS', label: 'По числу учтённых пар' },
  { value: 'PRESENT', label: 'По отметкам +' },
  { value: 'ABSENT', label: 'По отметкам н' },
  { value: 'EXCUSED', label: 'По отметкам у' },
];
function invalidate(): void {
  ++sequence;
  controller?.abort();
  controller = null;
  report.value = null;
  loading.value = false;
  error.value = false;
}
async function load(reloadSemesters = false): Promise<void> {
  invalidate();
  if (disposed || !canView.value) return;
  const current = sequence,
    client: AdminAttendanceClient = props.client,
    request = new AbortController();
  controller = request;
  loading.value = true;
  const valid = () => !disposed && current === sequence && client === props.client && canView.value;
  try {
    if (reloadSemesters) {
      const values = await client.getSemesters(request.signal);
      if (!valid()) return;
      semesters.value = values;
      if (!values.some((item) => item.id === semesterId.value))
        semesterId.value = (values.find((item) => item.active) ?? values[0])?.id ?? '';
    }
    if (!valid() || !semesterId.value) return;
    const result = await client.getReport(
      {
        semesterId: semesterId.value,
        search: appliedSearch.value,
        sort: sort.value,
        direction: direction.value,
        page: page.value,
        size: 25,
      },
      request.signal,
    );
    if (valid()) report.value = result;
  } catch (cause) {
    if (!valid() || request.signal.aborted) return;
    if (cause instanceof StaleSessionGenerationError) {
      emit('owner-error', cause);
      return;
    }
    error.value = true;
    emit('owner-error', cause);
  } finally {
    if (valid()) loading.value = false;
  }
}
function filtersChanged(): void {
  page.value = 0;
  void load();
}
function submitSearch(): void {
  appliedSearch.value = search.value.trim();
  filtersChanged();
}
function turn(delta: number): void {
  if (!report.value || loading.value) return;
  page.value += delta;
  void load();
}
function count(value: number): string {
  return new Intl.NumberFormat('ru-RU').format(value);
}
function percent(value: number | null): string {
  return value === null
    ? 'Нет данных'
    : `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value)} %`;
}
const exportScope = computed(() =>
  JSON.stringify([
    props.authority?.userId,
    props.authority?.version,
    semesterId.value,
    appliedSearch.value,
    sort.value,
    direction.value,
    sequence,
  ]),
);
function exportRequest(format: ReportDownloadFormat): ReportDownloadTicketRequest {
  const current = report.value;
  if (!canView.value || loading.value || !current) throw new StaleSessionGenerationError();
  return {
    kind: 'ADMIN_GROUP_ATTENDANCE',
    adminGroupAttendance: {
      semesterId: current.semesterId,
      search: current.search,
      sort: current.sort,
      direction: current.direction,
      format,
    },
  };
}
watch(
  () =>
    [
      props.client,
      props.online,
      props.authority?.userId,
      props.authority?.assigned,
      props.authority?.mode,
      props.authority?.version,
      props.authority?.permissions.join('|'),
    ] as const,
  () => {
    invalidate();
    semesters.value = [];
    semesterId.value = '';
    search.value = '';
    appliedSearch.value = '';
    page.value = 0;
    void load(true);
  },
  { flush: 'sync' },
);
onMounted(() => {
  void load(true);
});
onBeforeUnmount(() => {
  disposed = true;
  invalidate();
});
</script>
<template>
  <main
    class="admin-attendance-screen"
    aria-labelledby="admin-attendance-title"
    :aria-busy="loading"
  >
    <header class="admin-attendance-screen__header">
      <button
        v-if="showBack"
        type="button"
        @click="emit('back')"
      >
        Назад
      </button>
      <h1 id="admin-attendance-title">
        Посещаемость групп
      </h1>
    </header>
    <p
      v-if="!online"
      role="status"
    >
      Для просмотра отчёта подключись к сети.
    </p>
    <p
      v-else-if="!canView"
      role="status"
    >
      Нет доступа к отчёту посещаемости.
    </p>
    <template v-else>
      <label
        class="admin-attendance-screen__field"
        for="admin-attendance-semester"
      >Семестр
        <select
          id="admin-attendance-semester"
          v-model="semesterId"
          :disabled="loading || semesters.length === 0"
          @change="filtersChanged"
        >
          <option
            v-for="item in semesters"
            :key="item.id"
            :value="item.id"
          >{{ item.name }}</option>
        </select>
      </label>
      <form
        class="admin-attendance-screen__search"
        @submit.prevent="submitSearch"
      >
        <label
          class="admin-attendance-screen__field"
          for="admin-attendance-search"
        >Найти группу<input
          id="admin-attendance-search"
          v-model="search"
          type="search"
          maxlength="200"
          :disabled="loading || !semesterId"
        ></label>
        <button
          type="submit"
          :disabled="loading || !semesterId"
        >
          Найти
        </button>
      </form>
      <div class="admin-attendance-screen__filters">
        <label
          class="admin-attendance-screen__field"
          for="admin-attendance-sort"
        >Сортировка<select
          id="admin-attendance-sort"
          v-model="sort"
          :disabled="loading || !semesterId"
          @change="filtersChanged"
        ><option
          v-for="item in sortOptions"
          :key="item.value"
          :value="item.value"
        >{{ item.label }}</option></select></label>
        <label
          class="admin-attendance-screen__field"
          for="admin-attendance-direction"
        >Порядок<select
          id="admin-attendance-direction"
          v-model="direction"
          :disabled="loading || !semesterId"
          @change="filtersChanged"
        ><option value="ASC">По возрастанию</option><option value="DESC">По убыванию</option></select></label>
      </div>
      <p
        v-if="loading"
        role="status"
      >
        Загружаем посещаемость…
      </p>
      <AdminNotice
        v-if="error"
        tone="danger"
        title="Посещаемость не удалось загрузить"
      >
        <p>Попробуй ещё раз.</p><template #actions>
          <button
            type="button"
            @click="load(semesters.length === 0)"
          >
            Повторить
          </button>
        </template>
      </AdminNotice>
      <p
        v-else-if="!loading && semesters.length === 0"
        role="status"
      >
        Семестров пока нет.
      </p>
      <template v-if="report">
        <p class="admin-attendance-screen__note">
          % + — посещаемость; % (+ и у) — посещаемость с уважительными причинами. Отменённые пары не учитываются.
        </p>
        <ExportButton
          :port="reportDownload"
          :formats="() => client.getFormats()"
          :request="exportRequest"
          :scope-key="exportScope"
          :disabled="!canView || loading"
          @error="emit('owner-error', $event)"
        />
        <p
          v-if="report.rows.length === 0"
          role="status"
        >
          Групп по этому запросу нет.
        </p>
        <div
          v-else
          class="admin-attendance-screen__table"
          role="region"
          aria-label="Посещаемость групп за семестр"
          tabindex="0"
        >
          <table>
            <caption>{{ report.semester.name }}</caption><thead>
              <tr>
                <th scope="col">
                  Группа
                </th><th scope="col">
                  % +
                </th><th scope="col">
                  % (+ и у)
                </th><th scope="col">
                  Пар учтено
                </th><th scope="col">
                  + (присутствовал)
                </th><th scope="col">
                  н (отсутствовал)
                </th><th scope="col">
                  у (уважительная)
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in report.rows"
                :key="row.groupId"
                :data-group-id="row.groupId"
              >
                <th scope="row">
                  {{ row.groupName }}
                </th><td>{{ percent(row.presentPercent) }}</td><td>{{ percent(row.presentOrExcusedPercent) }}</td><td>{{ count(row.countedLessons) }}</td><td>{{ count(row.presentCount) }}</td><td>{{ count(row.absentCount) }}</td><td>{{ count(row.excusedCount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav
          class="admin-attendance-screen__pagination"
          aria-label="Страницы отчёта"
        >
          <button
            type="button"
            :disabled="loading || page === 0"
            @click="turn(-1)"
          >
            Предыдущая
          </button><span role="status">{{ page + 1 }} / {{ Math.max(1, report.totalPages) }}</span><button
            type="button"
            :disabled="loading || page + 1 >= report.totalPages"
            @click="turn(1)"
          >
            Следующая
          </button>
        </nav>
      </template>
    </template>
  </main>
</template>
