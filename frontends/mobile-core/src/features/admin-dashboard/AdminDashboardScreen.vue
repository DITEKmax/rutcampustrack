<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { AdminDashboardClient, AdminDashboardStats } from './admin-dashboard-client'
import './admin-dashboard-screen.pcss'

const props = defineProps<{
  client: AdminDashboardClient
  theme?: 'dark' | 'light' | 'system'
}>()

const emit = defineEmits<{
  'owner-error': [error: unknown]
  'open-role-switch': []
}>()

const stats = ref<AdminDashboardStats | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
let requestRevision = 0
let controller: AbortController | null = null
let disposed = false

async function refresh(): Promise<void> {
  const revision = ++requestRevision
  controller?.abort()
  controller = new AbortController()
  stats.value = null
  error.value = null
  loading.value = true

  try {
    const result = await props.client.getStats(controller.signal)
    if (!disposed && revision === requestRevision) stats.value = result
  } catch (cause) {
    if (disposed || revision !== requestRevision || isAbortError(cause)) return
    if (cause instanceof StaleSessionGenerationError) {
      emit('owner-error', cause)
      return
    }
    error.value = 'Статистику не удалось загрузить. Попробуй обновить данные ещё раз.'
  } finally {
    if (!disposed && revision === requestRevision) loading.value = false
  }
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

function formatCount(value: number): string {
  return new Intl.NumberFormat('ru-RU').format(value)
}

onMounted(() => { void refresh() })

onBeforeUnmount(() => {
  disposed = true
  ++requestRevision
  controller?.abort()
  controller = null
})
</script>

<template>
  <main
    class="admin-dashboard-screen"
    :data-theme="theme"
    aria-labelledby="admin-dashboard-title"
  >
    <header class="admin-dashboard-screen__header">
      <div>
        <p class="admin-dashboard-screen__eyebrow">Администрирование</p>
        <h1 id="admin-dashboard-title">Главная</h1>
      </div>
      <div class="admin-dashboard-screen__actions">
        <button
          class="admin-dashboard-screen__refresh"
          type="button"
          @click="emit('open-role-switch')"
        >
          Сменить роль
        </button>
        <button
          class="admin-dashboard-screen__refresh"
          type="button"
          :disabled="loading"
          :aria-busy="loading"
          @click="refresh"
        >
          {{ loading ? 'Обновляем…' : 'Обновить' }}
        </button>
      </div>
    </header>

    <p
      v-if="loading"
      class="admin-dashboard-screen__state"
      role="status"
      aria-live="polite"
    >
      Загружаем данные…
    </p>
    <p
      v-else-if="error"
      class="admin-dashboard-screen__state admin-dashboard-screen__state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <section
      v-else-if="stats"
      class="admin-dashboard-screen__content"
      aria-label="Текущие показатели системы"
    >
      <p class="admin-dashboard-screen__semester">
        <span>Активный семестр</span>
        <strong>{{ stats.activeSemesterName ?? 'Не выбран' }}</strong>
      </p>

      <dl class="admin-dashboard-screen__stats">
        <div class="admin-dashboard-screen__stat">
          <dt>Активные студенты</dt>
          <dd>{{ formatCount(stats.totalStudents) }}</dd>
        </div>
        <div class="admin-dashboard-screen__stat">
          <dt>Активные преподаватели</dt>
          <dd>{{ formatCount(stats.totalTeachers) }}</dd>
        </div>
        <div class="admin-dashboard-screen__stat">
          <dt>Действующие группы</dt>
          <dd>{{ formatCount(stats.activeGroups) }}</dd>
        </div>
      </dl>
    </section>
  </main>
</template>
