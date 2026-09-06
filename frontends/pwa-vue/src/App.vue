<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { TodayScreen, SemesterSnapshotStore, StudentApi, StudentApiError, commandFromCoordinates, createFixtureTransport, newIdempotencyKey, offlineToday, unavailableCommand, useToday } from '@rct/mobile-core'
import type { StudentCheckinCommand, StudentSemesterSchedule, TodayLesson } from '@rct/mobile-core'
import { usePwaAuth } from './auth'
import { isRecoverableReadFailure } from '@rct/mobile-core'

const auth = usePwaAuth()
const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true'
const fixtureDiagnosticsMode = fixtureMode && new URLSearchParams(window.location.search).get('fixtureDiagnostics') === 'true'
const fixtureServiceWorkerBuildEnabled = import.meta.env.PROD
const offline = ref(!navigator.onLine)
const bootstrapError = ref<string | null>(null)
const fixtureDiagnosticLines = ref<string[]>([])
const sessionReady = ref(false)
const cachedToday = ref<ReturnType<typeof offlineToday> | null>(null)
const semesterSchedule = ref<StudentSemesterSchedule | null>(null)
const selectedDate = ref(new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' }))
const snapshotStore = new SemesterSnapshotStore()
if (fixtureMode) installFixtureViewportControls()
const api = new StudentApi({
  accessToken: () => auth.accessToken.value,
  onUnauthorized: fixtureMode ? async () => undefined : auth.refresh,
  ...(fixtureMode ? { fetcher: createFixtureTransport() } : {}),
})
const session = ref<Awaited<ReturnType<typeof api.getSession>> | null>(null)
const { query, mutation } = useToday(api, computed(() => sessionReady.value && auth.accessToken.value !== null), offline)

async function cacheSemester(schedule: StudentSemesterSchedule): Promise<void> {
  if (!session.value) return
  semesterSchedule.value = schedule
  await snapshotStore.write({ ownerId: session.value.user.id, schedule, etag: null })
}

async function loadOfflineSnapshot(): Promise<boolean> {
  try {
    const snapshot = await snapshotStore.readCurrent()
    if (!snapshot) return false
    semesterSchedule.value = snapshot.schedule
    cachedToday.value = offlineToday(snapshot.schedule)
    offline.value = true
    return true
  } catch {
    return false
  }
}

async function bootstrap(): Promise<void> {
  try {
    if (fixtureMode) auth.accessToken.value = 'fixture-access-token'
    else await auth.refresh()
    session.value = await api.getSession()
    await snapshotStore.switchTo(session.value.user.id)
    if (session.value.semester) {
      const current = await snapshotStore.read(session.value.user.id)
      const response = await api.getSemesterSchedule(session.value.semester.id, current?.etag ?? undefined)
      if (response.data) await cacheSemester(response.data)
      else if (current) semesterSchedule.value = current.schedule
    }
  } catch (error) {
    const recovered = isRecoverableReadFailure(error) && await loadOfflineSnapshot()
    if (!recovered) bootstrapError.value = error instanceof Error ? error.message : 'Не удалось открыть Today'
  } finally {
    sessionReady.value = true
  }
}

async function readPosition(): Promise<GeolocationPosition | null> {
  if (!navigator.geolocation) return null
  return new Promise((resolve) => navigator.geolocation.getCurrentPosition(resolve, () => resolve(null), { enableHighAccuracy: true, timeout: 10_000, maximumAge: 0 }))
}

async function checkin(lesson: TodayLesson): Promise<void> {
  bootstrapError.value = null
  const command = fixtureMode ? fixtureCheckinCommand() : await productionCheckinCommand()
  try {
    await mutation.mutateAsync({ lessonId: lesson.schedule.id, command, key: newIdempotencyKey() })
  } catch (error) {
    const problem = error instanceof StudentApiError ? error.problem : null
    bootstrapError.value = problem?.detail ?? (error instanceof Error ? error.message : 'Не удалось отправить отметку')
  }
}

async function productionCheckinCommand(): Promise<StudentCheckinCommand> {
  const position = await readPosition()
  return position ? commandFromCoordinates(position) : unavailableCommand('POSITION_UNAVAILABLE')
}

/** Fixture-only browser control: `fixtureGeo=unavailable|coordinates`; real GPS is never requested. */
function fixtureCheckinCommand(): StudentCheckinCommand {
  const scenario = new URLSearchParams(window.location.search).get('fixtureGeo')
  return scenario === 'coordinates'
    ? { geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 } }
    : unavailableCommand('POSITION_UNAVAILABLE')
}

/** Fixture-only root-font control: `fixtureRootFont=16|20|24`; production keeps document defaults. */
function installFixtureViewportControls(): void {
  const requested = Number(new URLSearchParams(window.location.search).get('fixtureRootFont'))
  if (requested === 16 || requested === 20 || requested === 24) {
    document.documentElement.style.fontSize = `${requested}px`
  }
}

/** Test-only SW observability. It intentionally stays out of regular fixture screenshots. */
async function refreshFixtureDiagnostics(): Promise<void> {
  if (!fixtureDiagnosticsMode) return
  const lines = [
    `SW build flag: ${fixtureServiceWorkerBuildEnabled}`,
    `Origin: ${window.location.origin}`,
  ]
  if (!('serviceWorker' in navigator)) {
    fixtureDiagnosticLines.value = [...lines, 'Service worker API: unavailable']
    return
  }
  try {
    const registration = await navigator.serviceWorker.getRegistration()
    if (!registration) {
      fixtureDiagnosticLines.value = [...lines, 'Registration: none', `Controller: ${navigator.serviceWorker.controller ? 'present' : 'none'}`]
      return
    }
    const worker = registration.installing ?? registration.waiting ?? registration.active
    lines.push(`Registration: ${worker?.state ?? 'none'} (${registration.scope})`)
    lines.push(`Controller: ${navigator.serviceWorker.controller?.state ?? 'none'}`)
    const response = await fetch(`${import.meta.env.BASE_URL}sw-assets.js`, { cache: 'no-store' })
    const source = await response.text()
    const match = source.match(/=\s*(\[[\s\S]*\]);?\s*$/)
    const serializedAssets = match?.[1]
    if (!response.ok || !serializedAssets) throw new Error('Generated precache list unavailable')
    const assets = JSON.parse(serializedAssets) as unknown
    if (!Array.isArray(assets) || assets.some((asset) => typeof asset !== 'string')) throw new Error('Generated precache list is invalid')
    const cacheNames = await caches.keys()
    const cacheName = cacheNames.find((name) => name === 'rct-student-pwa-v3')
    if (!cacheName) {
      fixtureDiagnosticLines.value = [...lines, 'Precache: rct-student-pwa-v3 missing']
      return
    }
    const cache = await caches.open(cacheName)
    const matches = await Promise.all(assets.map((asset) => cache.match(asset)))
    const missing = assets.filter((asset, index) => !matches[index])
    lines.push(`Precache assets: ${assets.length - missing.length}/${assets.length}`)
    if (missing.length > 0) lines.push(`Missing: ${missing.join(', ')}`)
    fixtureDiagnosticLines.value = lines
  } catch (error) {
    fixtureDiagnosticLines.value = [...lines, `Diagnostics error: ${error instanceof Error ? error.message : 'unknown'}`]
  }
}

function goOffline(): void { offline.value = true; void loadOfflineSnapshot() }
function goOnline(): void { offline.value = false; void query.refetch() }

function onServiceWorkerControllerChange(): void { void refreshFixtureDiagnostics() }

onMounted(() => {
  window.addEventListener('offline', goOffline)
  window.addEventListener('online', goOnline)
  if (fixtureDiagnosticsMode && 'serviceWorker' in navigator) navigator.serviceWorker.addEventListener('controllerchange', onServiceWorkerControllerChange)
  void bootstrap()
  void refreshFixtureDiagnostics()
})
onBeforeUnmount(() => {
  window.removeEventListener('offline', goOffline)
  window.removeEventListener('online', goOnline)
  if (fixtureDiagnosticsMode && 'serviceWorker' in navigator) navigator.serviceWorker.removeEventListener('controllerchange', onServiceWorkerControllerChange)
})
watch(() => query.error.value, (error) => {
  if (error && isRecoverableReadFailure(error)) void loadOfflineSnapshot()
})

const displayToday = computed(() => offline.value ? cachedToday.value : query.data.value ?? cachedToday.value)
const displayError = computed(() => bootstrapError.value ?? (query.error.value instanceof Error && !cachedToday.value ? query.error.value.message : null))
</script>

<template>
  <TodayScreen
    :today="displayToday"
    :loading="!sessionReady || (query.isPending.value && !cachedToday)"
    :error="displayError"
    :offline="offline"
    :updated-at="cachedToday?.serverNow ?? null"
    :submitting-lesson-id="mutation.isPending.value ? mutation.variables.value?.lessonId ?? null : null"
    :semester-schedule="semesterSchedule"
    :selected-date="selectedDate"
    @checkin="checkin"
    @select-date="selectedDate = $event"
  />
  <section
    v-if="fixtureDiagnosticsMode"
    aria-label="Fixture service-worker diagnostics"
  >
    <h2>Fixture service-worker diagnostics</h2>
    <button
      type="button"
      @click="refreshFixtureDiagnostics"
    >
      Refresh diagnostics
    </button>
    <ul>
      <li
        v-for="line in fixtureDiagnosticLines"
        :key="line"
      >
        {{ line }}
      </li>
    </ul>
  </section>
</template>
