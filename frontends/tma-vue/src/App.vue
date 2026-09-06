<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CheckinCommandRecovery, TodayScreen, StudentApi, StudentApiError, createFixtureTransport, useToday } from '@rct/mobile-core'
import type { TodayLesson } from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'
import { authenticateTma } from './tma-auth'

const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true'
if (fixtureMode) installFixtureTelegramHost()
const fixtureTransport = fixtureMode ? createFixtureTransport() : undefined
const host = new TelegramHost()
const accessToken = ref<string | null>(null)
const ready = ref(false)
const offline = ref(!navigator.onLine)
const error = ref<string | null>(null)
const api = new StudentApi({ accessToken: () => accessToken.value, onUnauthorized: authenticate, ...(fixtureTransport ? { fetcher: fixtureTransport } : {}) })
const { query, mutation } = useToday(api, computed(() => ready.value && accessToken.value !== null), offline)
const checkinRecovery = new CheckinCommandRecovery()
const todayLoading = computed(() => !ready.value || (accessToken.value !== null && query.isPending.value))

async function authenticate(): Promise<void> {
  const initData = host.start()
  if (!initData) throw new Error('Открой приложение из Telegram')
  const fetcher: typeof fetch = fixtureTransport ?? fetch
  accessToken.value = await authenticateTma(fetcher, initData)
}

async function bootstrap(): Promise<void> {
  try { await authenticate() } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Не удалось открыть приложение' } finally { ready.value = true }
}

async function checkin(lesson: TodayLesson): Promise<void> {
  error.value = null
  if (offline.value) return
  try {
    await checkinRecovery.execute(
      lesson.schedule.id,
      () => host.location(),
      (attempt) => mutation.mutateAsync(attempt),
    )
  }
  catch (cause) { error.value = cause instanceof StudentApiError ? cause.problem?.detail ?? cause.message : cause instanceof Error ? cause.message : 'Не удалось отправить отметку' }
}

function offlineNow(): void { offline.value = true }
function onlineNow(): void { offline.value = false; void query.refetch() }
onMounted(() => { window.addEventListener('offline', offlineNow); window.addEventListener('online', onlineNow); void bootstrap() })
onBeforeUnmount(() => { window.removeEventListener('offline', offlineNow); window.removeEventListener('online', onlineNow) })
</script>

<template>
  <TodayScreen
    :today="query.data.value ?? null"
    :loading="todayLoading"
    :error="offline ? 'TMA работает только при подключении к интернету' : error ?? (query.error.value instanceof Error ? query.error.value.message : null)"
    :offline="offline"
    :updated-at="null"
    :submitting-lesson-id="mutation.isPending.value ? mutation.variables.value?.lessonId ?? null : null"
    :semester-schedule="null"
    selected-date=""
    @checkin="checkin"
  />
</template>
