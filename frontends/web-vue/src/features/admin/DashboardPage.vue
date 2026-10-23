<script setup lang="ts">
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
  adminActivityObservedAt,
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
const points = computed(() =>
  activity.value ? adminActivityChartPoints(activity.value, metric.value) : [],
);
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
      <div
        v-if="stats"
        class="admin-metrics"
      >
        <article
          v-for="item in overview"
          :key="item.label"
          class="admin-metric"
        >
          <span>{{ item.label }}</span><strong>{{ item.value.toLocaleString('ru-RU') }}</strong><p class="web-caption">
            {{
              item.delta === null || item.delta === undefined
                ? 'Сравнение за сутки недоступно'
                : `${item.delta > 0 ? '+' : ''}${item.delta} за сутки`
            }}
          </p>
        </article>
      </div>
      <section class="admin-panel">
        <header class="admin-heading">
          <h2>Активность</h2><div class="admin-actions">
            <label class="web-field">Период<select
              v-model="period"
              :disabled="busy"
              @change="admin.run(load)"
            ><option value="today">Сегодня</option><option value="all_time">Вся сохранённая история</option></select></label><label class="web-field">Шаг<select
              v-model="bucket"
              :disabled="busy"
              @change="admin.run(load)"
            ><option value="day">Дни</option><option value="week">Недели</option></select></label>
          </div>
        </header>
        <template v-if="activity">
          <p class="web-caption">
            По состоянию на {{ adminActivityObservedAt(activity.observedAt) }}
          </p><div class="admin-metrics">
            <article
              v-for="code in ADMIN_ACTIVITY_METRICS"
              :key="code"
              class="admin-metric"
            >
              <h3>{{ adminActivityLabels[code] }}</h3><strong>{{ adminActivityValue(activity.summary.metrics[code], code) }}</strong><p
                v-if="adminActivityExplanation(activity.summary.metrics[code])"
                class="web-caption"
              >
                {{ adminActivityExplanation(activity.summary.metrics[code]) }}
              </p><template v-else>
                <p
                  v-if="code === 'geoMarks'"
                  class="web-caption"
                >
                  {{ activity.summary.metrics[code].numerator }} /
                  {{ activity.summary.metrics[code].denominator }}
                </p><p class="web-caption">
                  {{ adminActivityDelta(activity.summary.metrics[code], code).text }} ·
                  {{ adminActivityDelta(activity.summary.metrics[code], code).description }}
                </p>
              </template>
            </article>
          </div>
          <label class="web-field">Показатель графика<select v-model="metric"><option
            v-for="code in ADMIN_ACTIVITY_METRICS"
            :key="code"
            :value="code"
          >{{ adminActivityLabels[code] }}</option></select></label><AdminActivityChart
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
