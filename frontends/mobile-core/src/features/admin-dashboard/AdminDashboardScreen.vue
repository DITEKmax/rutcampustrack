<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { AdminDashboardClient, AdminDashboardStats } from './admin-dashboard-client'
import type { AdminActivityPeriod, AdminActivityBucket, AdminActivityMetricCode, AdminDashboardActivity } from './admin-dashboard-activity'
import { ADMIN_ACTIVITY_METRICS, adminActivityLabels, adminActivityValue, adminActivityExplanation, adminActivityDelta, adminActivityChartPoints, adminActivityMoscowDate } from './admin-dashboard-activity-view'
import chevronIcon from '../../assets/admin/chevron-down.svg'
import AdminNotice from '../../shared/components/AdminNotice.vue'
import MobileToolsSheet from '../../shared/components/MobileToolsSheet.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import checkIcon from '../../assets/admin/check.svg'
import AdminActivityChart from './AdminActivityChart.vue'
import './admin-dashboard-screen.pcss'

const props = defineProps<{ client: AdminDashboardClient; theme?: 'dark' | 'light' | 'system'; invalidationRevision?: number }>()
const emit = defineEmits<{ 'owner-error': [error: unknown]; 'open-role-switch': [] }>()
const period = ref<AdminActivityPeriod>('today')
const bucket = ref<AdminActivityBucket>('day')
const metricCode = ref<AdminActivityMetricCode>('positiveMarks')
const metricOptionsOpen = ref(false)
const stats = ref<AdminDashboardStats | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const activity = ref<AdminDashboardActivity | null>(null)
const activityLoading = ref(false)
const activityError = ref<string | null>(null)
const points = computed(() => activity.value ? adminActivityChartPoints(activity.value, metricCode.value) : [])
const hasSeriesGaps = computed(() => activity.value?.series.some(point => point.metrics[metricCode.value].state !== 'AVAILABLE') ?? false)
let requestRevision = 0
let activityRevision = 0
let controller: AbortController | null = null
let activityController: AbortController | null = null
let disposed = false

function isAbortError(cause: unknown): boolean { return cause instanceof Error && cause.name === 'AbortError' }
async function refreshStats(quiet = false): Promise<void> {
  const revision = ++requestRevision
  controller?.abort()
  const request = new AbortController()
  controller = request
  if (!quiet) stats.value = null
  error.value = null; loading.value = !quiet || stats.value === null
  try {
    const result = await props.client.getStats(request.signal)
    if (!disposed && revision === requestRevision) stats.value = result
  } catch (cause) {
    if (disposed || revision !== requestRevision || isAbortError(cause)) return
    if (cause instanceof StaleSessionGenerationError) { emit('owner-error', cause); return }
    error.value = 'Попробуй обновить данные ещё раз.'
  } finally { if (!disposed && revision === requestRevision) loading.value = false }
}
async function refreshActivity(quiet = false): Promise<void> {
  const revision = ++activityRevision
  activityController?.abort()
  const request = new AbortController()
  activityController = request
  const requestedPeriod = period.value, requestedBucket = bucket.value
  if (!quiet) activity.value = null
  activityError.value = null; activityLoading.value = !quiet || activity.value === null
  try {
    const result = await props.client.getActivity(requestedPeriod, requestedBucket, request.signal)
    if (!disposed && revision === activityRevision) activity.value = result
  } catch (cause) {
    if (disposed || revision !== activityRevision || isAbortError(cause)) return
    if (cause instanceof StaleSessionGenerationError) { emit('owner-error', cause); return }
    activityError.value = 'Попробуй загрузить активность ещё раз.'
  } finally { if (!disposed && revision === activityRevision) activityLoading.value = false }
}
function refresh(): void { void refreshStats(); void refreshActivity() }
function formatCount(value: number): string { return new Intl.NumberFormat('ru-RU').format(value) }
watch([period, bucket], () => { void refreshActivity() })
watch(() => props.invalidationRevision, () => { void refreshStats(true); void refreshActivity(true) })
onMounted(refresh)
onBeforeUnmount(() => {
  disposed = true; ++requestRevision; ++activityRevision
  controller?.abort(); activityController?.abort()
  controller = null; activityController = null
})
</script>

<template>
  <main
    class="admin-dashboard-screen"
    :data-theme="theme"
    :aria-busy="loading || activityLoading"
    aria-labelledby="admin-dashboard-title"
  >
    <button
      class="admin-dashboard-screen__role"
      type="button"
      @click="emit('open-role-switch')"
    >
      Администратор<img
        :src="chevronIcon"
        alt=""
        aria-hidden="true"
      >
    </button>
    <header class="admin-dashboard-screen__header">
      <h1 id="admin-dashboard-title">
        Обзор
      </h1>
    </header>
    <p
      v-if="loading"
      class="admin-dashboard-screen__state"
      role="status"
    >
      Загружаем данные…
    </p>
    <AdminNotice
      v-if="error"
      tone="danger"
      title="Статистику не удалось загрузить"
    >
      <p>{{ error }}</p>
      <template #actions>
        <button
          type="button"
          @click="refreshStats()"
        >
          Повторить
        </button>
      </template>
    </AdminNotice>
    <dl
      v-if="stats"
      class="admin-dashboard-screen__stats"
      aria-label="Активные аккаунты и группы"
    >
      <div class="admin-dashboard-screen__stat">
        <dt>Студенты</dt><dd>
          <span>{{ formatCount(stats.totalStudents) }}</span><small
            class="admin-dashboard-screen__delta"
            aria-label="Сравнение недоступно"
          >—</small>
        </dd>
      </div>
      <div class="admin-dashboard-screen__stat">
        <dt>Преподаватели</dt><dd>
          <span>{{ formatCount(stats.totalTeachers) }}</span><small
            class="admin-dashboard-screen__delta"
            aria-label="Сравнение недоступно"
          >—</small>
        </dd>
      </div>
      <div class="admin-dashboard-screen__stat">
        <dt>Группы</dt><dd>
          <span>{{ formatCount(stats.activeGroups) }}</span><small
            class="admin-dashboard-screen__delta"
            aria-label="Сравнение недоступно"
          >—</small>
        </dd>
      </div>
    </dl>
    <section
      class="admin-dashboard-screen__activity"
      aria-labelledby="admin-dashboard-activity-title"
      :aria-busy="activityLoading"
    >
      <h2 id="admin-dashboard-activity-title">
        Активность
      </h2>
      <div
        class="admin-dashboard-screen__period"
        role="group"
        aria-label="Период активности"
      >
        <button
          type="button"
          :aria-pressed="period === 'today'"
          @click="period = 'today'"
        >
          Сегодня
        </button>
        <button
          type="button"
          :aria-pressed="period === 'all_time'"
          @click="period = 'all_time'"
        >
          Всё время
        </button>
      </div>
      <div
        class="admin-dashboard-screen__period"
        role="group"
        aria-label="Шаг графика"
      >
        <button
          type="button"
          :aria-pressed="bucket === 'day'"
          @click="bucket = 'day'"
        >
          По дням
        </button>
        <button
          type="button"
          :aria-pressed="bucket === 'week'"
          @click="bucket = 'week'"
        >
          По неделям
        </button>
      </div>
      <p
        v-if="activityLoading"
        class="admin-dashboard-screen__note"
        role="status"
      >
        Загружаем активность…
      </p>
      <AdminNotice
        v-if="activityError"
        tone="danger"
        title="Активность не загрузилась"
      >
        <p>{{ activityError }}</p>
        <template #actions>
          <button
            type="button"
            @click="refreshActivity()"
          >
            Повторить
          </button>
        </template>
      </AdminNotice>
      <template v-if="activity">
        <div class="admin-dashboard-screen__metric-picker">
          <span>Показатель графика</span>
          <button
            class="admin-dashboard-screen__metric-trigger"
            type="button"
            aria-haspopup="dialog"
            :aria-expanded="metricOptionsOpen"
            data-admin-metric-trigger
            @click="metricOptionsOpen = true"
          >
            <span>{{ adminActivityLabels[metricCode] }}</span>
            <MobileIcon name="back" />
          </button>
        </div>
        <MobileToolsSheet
          v-model:open="metricOptionsOpen"
          title="Показатель графика"
          icon-close
        >
          <div
            class="admin-dashboard-screen__metric-options"
            role="group"
            aria-label="Показатели активности"
          >
            <button
              v-for="code in ADMIN_ACTIVITY_METRICS"
              :key="code"
              type="button"
              :aria-pressed="metricCode === code"
              :data-admin-metric-option="code"
              @click="metricCode = code; metricOptionsOpen = false"
            >
              <span>{{ adminActivityLabels[code] }}</span>
              <img
                v-if="metricCode === code"
                :src="checkIcon"
                alt=""
                aria-hidden="true"
              >
            </button>
          </div>
        </MobileToolsSheet>
        <AdminActivityChart
          :title="adminActivityLabels[metricCode]"
          :points="points"
          :as-of-date="adminActivityMoscowDate(activity.observedAt)"
          :unit="metricCode === 'geoMarks' ? 'percent' : 'count'"
        />
        <AdminNotice
          v-if="activity.summary.metrics[metricCode].state !== 'AVAILABLE' || hasSeriesGaps"
          tone="warning"
          title="История показателя неполная"
        >
          <p>{{ activity.summary.metrics[metricCode].state === 'AVAILABLE' ? 'За часть периодов данных нет. Известные значения показаны на графике.' : adminActivityExplanation(activity.summary.metrics[metricCode]) }}</p>
        </AdminNotice>
        <dl
          class="admin-dashboard-screen__stats admin-dashboard-screen__stats--activity"
          aria-label="Итоги активности"
        >
          <div
            v-for="code in ADMIN_ACTIVITY_METRICS"
            :key="code"
            class="admin-dashboard-screen__stat"
            :data-admin-activity-metric="code"
            :data-state="activity.summary.metrics[code].state"
          >
            <dt>{{ adminActivityLabels[code] }}</dt><dd>
              <span :aria-label="activity.summary.metrics[code].state === 'AVAILABLE' && !(code === 'geoMarks' && activity.summary.metrics[code].percent === null) ? undefined : 'Данные недоступны'">{{ adminActivityValue(activity.summary.metrics[code], code) }}</span>
              <small
                class="admin-dashboard-screen__delta"
                :data-tone="adminActivityDelta(activity.summary.metrics[code], code).tone"
                :aria-label="adminActivityDelta(activity.summary.metrics[code], code).description"
              >{{ adminActivityDelta(activity.summary.metrics[code], code).text }}</small>
            </dd>
          </div>
        </dl>
      </template>
    </section>
    <button
      class="admin-dashboard-screen__refresh"
      type="button"
      :disabled="loading || activityLoading"
      @click="refresh"
    >
      Обновить данные
    </button>
  </main>
</template>
