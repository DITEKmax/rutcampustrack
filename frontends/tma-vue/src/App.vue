<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { TodayScreen, StudentApi, StudentApiError, createFixtureTransport, newIdempotencyKey, useToday } from '@rct/mobile-core'
import type { TodayLesson } from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'

const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true'
if (fixtureMode) installFixtureTelegramHost()
const host = new TelegramHost()
const accessToken = ref<string | null>(null)
const ready = ref(false)
const offline = ref(!navigator.onLine)
const error = ref<string | null>(null)
const api = new StudentApi({ accessToken: () => accessToken.value, onUnauthorized: authenticate, ...(fixtureMode ? { fetcher: createFixtureTransport() } : {}) })
const { query, mutation } = useToday(api, computed(() => ready.value && accessToken.value !== null), offline)

async function authenticate(): Promise<void> {
  const initData = host.start()
  if (!initData) throw new Error('Открой приложение из Telegram')
  const response = fixtureMode
    ? await createFixtureTransport()('/api/auth/tma', { method: 'POST', body: initData })
    : await fetch('/api/auth/tma', { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=UTF-8', Accept: 'application/json' }, body: initData, credentials: 'include' })
  if (!response.ok) throw new Error('Telegram не подтвердил сессию')
  const token = await response.json() as { accessToken?: string }
  if (!token.accessToken) throw new Error('Сервер не вернул access token')
  accessToken.value = token.accessToken
}

async function bootstrap(): Promise<void> {
  try { await authenticate() } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Не удалось открыть приложение' } finally { ready.value = true }
}

async function checkin(lesson: TodayLesson): Promise<void> {
  error.value = null
  try { await mutation.mutateAsync({ lessonId: lesson.schedule.id, command: await host.location(), key: newIdempotencyKey() }) }
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
    :loading="!ready || query.isPending.value"
    :error="offline ? 'TMA работает только при подключении к интернету' : error ?? (query.error.value instanceof Error ? query.error.value.message : null)"
    :offline="offline"
    :updated-at="null"
    :submitting-lesson-id="mutation.isPending.value ? mutation.variables.value?.lessonId ?? null : null"
    :semester-schedule="null"
    selected-date=""
    @checkin="checkin"
  />
</template>
