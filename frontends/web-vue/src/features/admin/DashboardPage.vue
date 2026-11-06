<script setup lang="ts">
import WebSelect from '../../shared/WebSelect.vue';
import {
  computed,
  effectScope,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  type EffectScope,
} from 'vue';
import { useWebLiveRefresh } from '../../app/live';
import { RouterLink } from 'vue-router';
import {
  AdminDashboardClient,
  type AdminDashboardStats,
  type AdminDashboardActivity,
  type AdminActivityPeriod,
  type AdminActivityBucket,
  type AdminActivityMetricCode,
} from '../../../../mobile-core/src/features/admin-dashboard/admin-dashboard-client';
import {
  ADMIN_ACTIVITY_METRICS,
  adminActivityLabels,
  adminActivityValue,
  adminActivityExplanation,
  adminActivityDelta,
  adminActivityChartPoints,
  adminActivityMoscowDate,
} from '../../../../mobile-core/src/features/admin-dashboard/admin-dashboard-activity-view';
import AdminActivityChart from '../../../../mobile-core/src/features/admin-dashboard/AdminActivityChart.vue';
import { useAdmin } from './use-admin';
import AdminState from './AdminState.vue';
import './admin.pcss';
const admin = useAdmin();
const { loading, busy, error, notice } = admin;
const client = new AdminDashboardClient(admin.options);
const stats = shallowRef<AdminDashboardStats | null>(null);
const activity = shallowRef<AdminDashboardActivity | null>(null);
const period = ref<AdminActivityPeriod>('today'),
  bucket = ref<AdminActivityBucket>('day');
const metric = ref<AdminActivityMetricCode>('positiveMarks');
const metricOptions = ADMIN_ACTIVITY_METRICS.map(code => ({ value: code, label: adminActivityLabels[code] }));
const points = computed(() =>
  activity.value ? adminActivityChartPoints(activity.value, metric.value) : [],
);
const summaryRows = computed(() => {
  const metrics = activity.value?.summary.metrics;
  if (!metrics) return [];
  return ADMIN_ACTIVITY_METRICS.map(code => {
    const item = metrics[code];
    return {
      code,
      label: adminActivityLabels[code],
      value: adminActivityValue(item, code),
      explanation: adminActivityExplanation(item),
      delta: adminActivityDelta(item, code),
      basis: code === 'geoMarks' && item.numerator !== null && item.denominator !== null
        ? `${item.numerator.toLocaleString('ru-RU')} из ${item.denominator.toLocaleString('ru-RU')}`
        : '',
    };
  });
});
const overview = computed(() =>
  stats.value
    ? [
        {
          label: 'Студенты',
          value: stats.value.totalStudents,
          delta: stats.value.dailyGrowth?.studentsDelta,
        },
        {
          label: 'Преподаватели',
          value: stats.value.totalTeachers,
          delta: stats.value.dailyGrowth?.teachersDelta,
        },
        {
          label: 'Группы',
          value: stats.value.totalGroups,
          delta: stats.value.dailyGrowth?.groupsDelta,
        },
      ]
    : [],
);
let revision = 0;
async function load() {
  const own = ++revision;
  const [s, a] = await Promise.all([
    client.getStats(admin.signal),
    client.getActivity(period.value, bucket.value, admin.signal),
  ]);
  admin.assertCurrent();
  if (own === revision) {
    stats.value = s;
    activity.value = a;
  }
}
function changeScope() {
  if (activity.value?.period !== period.value || activity.value.bucket !== bucket.value)
    activity.value = null;
  void admin.run(load);
}
let live: EffectScope | null = null;
onMounted(async () => {
  await admin.init('DASHBOARD_VIEW', load);
  if (!admin.can('DASHBOARD_VIEW')) return;
  live = effectScope();
  live.run(() =>
    useWebLiveRefresh(
      () =>
        admin.refresh(async () => {
          await admin.requirePermission('DASHBOARD_VIEW');
          await load();
        }),
      { blocked: busy, adminDashboard: true },
    ),
  );
});
onBeforeUnmount(() => live?.stop());
</script>
<template>
  <section
    class="admin-page"
    :aria-busy="busy"
  >
    <header class="admin-heading">
      <div>
        <h1>Обзор системы</h1><p
          v-if="stats"
          class="web-caption"
        >
          {{ stats.activeSemesterName ?? 'Нет активного семестра' }}
        </p>
      </div><RouterLink
        v-if="admin.can('DASHBOARD_VIEW')"
        class="web-button"
        to="/admin/attendance"
      >
        Отчёт посещаемости
      </RouterLink>
    </header>
    <AdminState
      :loading="loading"
      :busy="busy"
      :error="error"
      :notice="notice"
      @retry="admin.run(load)"
    />
    <template v-if="admin.can('DASHBOARD_VIEW')">
      <dl
        v-if="stats"
        class="admin-counts"
      >
        <div
          v-for="item in overview"
          :key="item.label"
        >
          <dt>{{ item.label }}</dt>
          <dd>
            <strong>{{ item.value.toLocaleString('ru-RU') }}</strong>
            <small class="web-caption">
              {{ item.delta == null ? 'Сравнение за сутки недоступно' : `${item.delta > 0 ? '+' : ''}${item.delta} за сутки` }}
            </small>
          </dd>
        </div>
      </dl>
      <section class="admin-panel">
        <header class="admin-heading admin-activity-heading">
          <h2>Активность</h2>
          <div class="admin-activity-scope">
            <WebSelect
              v-model="period"
              label="Период"
              :options="[{ value: 'today', label: 'Сегодня' }, { value: 'all_time', label: 'Вся сохранённая история' }]"
              :disabled="busy"
              @change="changeScope"
            />
            <WebSelect
              v-model="bucket"
              label="Шаг"
              :options="[{ value: 'day', label: 'Дни' }, { value: 'week', label: 'Недели' }]"
              :disabled="busy"
              @change="changeScope"
            />
          </div>
        </header>
        <template v-if="activity">
          <table class="admin-activity-table">
            <caption class="web-sr-only">
              Показатели активности за выбранный период
            </caption>
            <thead>
              <tr>
                <th scope="col">
                  Показатель
                </th>
                <th scope="col">
                  Значение
                </th>
                <th scope="col">
                  Изменение<small v-if="activity.period === 'today'">К этому времени вчера</small>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in summaryRows"
                :key="row.code"
              >
                <th scope="row">
                  {{ row.label }}
                </th>
                <td>
                  <strong>{{ row.value }}</strong>
                  <small v-if="row.basis">{{ row.basis }}</small>
                </td>
                <td>
                  <span v-if="row.explanation">{{ row.explanation }}</span>
                  <template v-else>
                    <span
                      aria-hidden="true"
                      :title="row.delta.description"
                    >{{ row.delta.text }}</span>
                    <span class="web-sr-only">{{ row.delta.description }}</span>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
          <WebSelect
            v-model="metric"
            class="admin-activity-metric"
            label="Показатель графика"
            :options="metricOptions"
          />
          <AdminActivityChart
            v-if="points.length"
            :title="adminActivityLabels[metric]"
            :points="points"
            :as-of-date="adminActivityMoscowDate(activity.observedAt)"
            :unit="metric === 'geoMarks' ? 'percent' : 'count'"
          /><p
            v-else
            role="status"
          >
            Для выбранного периода нет точек графика.
          </p>
        </template>
      </section>
    </template>
  </section>
</template>
