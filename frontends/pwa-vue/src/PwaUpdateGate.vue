<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  APP_UPDATE_REQUIRED_EVENT,
  APP_VERSION,
  VERSION_POLICY_URL,
  isUpdateRequiredByPolicy,
  type PwaUpdateRequiredDetail,
  type VersionPolicy,
} from './pwa-version'

const props = withDefaults(defineProps<{ enabled?: boolean }>(), { enabled: true })

const required = ref<PwaUpdateRequiredDetail | null>(null)
const updating = ref(false)
const updateButton = ref<HTMLButtonElement | null>(null)
let intervalId: number | undefined

function showUpdate(detail: PwaUpdateRequiredDetail): void {
  required.value = {
    ...required.value,
    ...detail,
  }
}

function onUpdateRequired(event: Event): void {
  const detail = (event as CustomEvent<PwaUpdateRequiredDetail>).detail
  if (detail?.reason === 'api' || detail?.reason === 'policy') showUpdate(detail)
}

async function checkPolicy(): Promise<void> {
  if (!props.enabled || typeof document === 'undefined' || document.visibilityState === 'hidden') return
  try {
    const separator = VERSION_POLICY_URL.includes('?') ? '&' : '?'
    const response = await globalThis.fetch(`${VERSION_POLICY_URL}${separator}t=${Date.now()}`, {
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) return
    const policy = await response.json() as VersionPolicy
    if (isUpdateRequiredByPolicy(APP_VERSION, policy)) {
      showUpdate({
        reason: 'policy',
        ...(typeof policy.latest === 'string' ? { latest: policy.latest } : {}),
        ...(typeof policy.minimumSupported === 'string' ? { minimumSupported: policy.minimumSupported } : {}),
        ...(typeof policy.message === 'string' ? { message: policy.message } : {}),
      })
    }
  } catch {
    // A temporary policy read failure must not log out or discard pending work.
  }
}

async function requestUpdate(): Promise<void> {
  if (updating.value) return
  updating.value = true
  try {
    if (!('serviceWorker' in navigator)) {
      window.location.reload()
      return
    }

    const registration = await navigator.serviceWorker.getRegistration()
    if (!registration) {
      window.location.reload()
      return
    }

    await registration.update()
    const waiting = await waitForWaitingWorker(registration)
    if (!waiting) {
      // Reloading while the old worker still controls the page can reopen the
      // stale shell. Keep the hard block and let the user retry after a
      // transient update check failure instead.
      updating.value = false
      return
    }

    const activated = await activateWaitingWorker(registration, waiting)
    if (activated) window.location.reload()
    else updating.value = false
  } catch {
    // The hard block remains visible; reload is still the safe fallback.
    updating.value = false
  }
}

function waitForWaitingWorker(registration: ServiceWorkerRegistration): Promise<ServiceWorker | null> {
  if (registration.waiting) return Promise.resolve(registration.waiting)
  const installing = registration.installing
  if (!installing) return Promise.resolve(null)

  return new Promise((resolve) => {
    let settled = false
    const timeoutId = window.setTimeout(() => finish(null), 10_000)
    const finish = (worker: ServiceWorker | null): void => {
      if (settled) return
      settled = true
      window.clearTimeout(timeoutId)
      installing.removeEventListener('statechange', onStateChange)
      resolve(worker)
    }
    const onStateChange = (): void => {
      if (registration.waiting) {
        finish(registration.waiting)
      } else if (installing.state === 'redundant') {
        finish(null)
      }
    }
    installing.addEventListener('statechange', onStateChange)
    onStateChange()
  })
}

function activateWaitingWorker(
  registration: ServiceWorkerRegistration,
  waiting: ServiceWorker,
): Promise<boolean> {
  return new Promise((resolve) => {
    let settled = false
    let timeoutId: number | undefined
    const finish = (value: boolean): void => {
      if (settled) return
      settled = true
      if (timeoutId !== undefined) window.clearTimeout(timeoutId)
      navigator.serviceWorker.removeEventListener('controllerchange', onControllerChange)
      waiting.removeEventListener('statechange', onStateChange)
      resolve(value)
    }
    const onControllerChange = (): void => finish(true)
    const onStateChange = (): void => {
      if (waiting.state === 'activated' && !navigator.serviceWorker.controller) finish(true)
    }
    timeoutId = window.setTimeout(() => finish(false), 5_000)
    navigator.serviceWorker.addEventListener('controllerchange', onControllerChange)
    waiting.addEventListener('statechange', onStateChange)
    waiting.postMessage({ type: 'SKIP_WAITING' })
    if (registration.active === waiting && !navigator.serviceWorker.controller) finish(true)
  })
}

function onVisibilityChange(): void {
  if (document.visibilityState === 'visible') void checkPolicy()
}

function onGateKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Tab') return
  event.preventDefault()
  updateButton.value?.focus()
}

onMounted(() => {
  if (!props.enabled) return
  window.addEventListener(APP_UPDATE_REQUIRED_EVENT, onUpdateRequired)
  window.addEventListener('online', checkPolicy)
  document.addEventListener('visibilitychange', onVisibilityChange)
  intervalId = window.setInterval(() => void checkPolicy(), 60_000)
  void checkPolicy()
})

watch(required, async (value) => {
  const appRoot = document.getElementById('app')
  if (value) {
    appRoot?.setAttribute('inert', '')
    await nextTick()
    updateButton.value?.focus()
  } else {
    appRoot?.removeAttribute('inert')
  }
})

onBeforeUnmount(() => {
  if (intervalId !== undefined) window.clearInterval(intervalId)
  window.removeEventListener(APP_UPDATE_REQUIRED_EVENT, onUpdateRequired)
  window.removeEventListener('online', checkPolicy)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  document.getElementById('app')?.removeAttribute('inert')
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="required"
      class="pwa-update-gate"
      role="dialog"
      aria-modal="true"
      aria-labelledby="pwa-update-gate-title"
      aria-describedby="pwa-update-gate-message"
      @keydown="onGateKeydown"
    >
      <section class="pwa-update-gate__panel">
        <p class="pwa-update-gate__eyebrow">
          Обновление приложения
        </p>
        <h1 id="pwa-update-gate-title">
          Нужно обновить RutTrack
        </h1>
        <p id="pwa-update-gate-message">
          {{ required.message ?? 'Текущая версия больше не поддерживается. Обнови приложение, чтобы продолжить.' }}
        </p>
        <p
          v-if="required.latest || required.minimumSupported"
          class="pwa-update-gate__versions"
        >
          Новая версия: {{ required.latest ?? required.minimumSupported }}
        </p>
        <button
          ref="updateButton"
          type="button"
          :disabled="updating"
          :aria-busy="updating"
          @click="requestUpdate"
        >
          {{ updating ? 'Обновляем…' : 'Обновить приложение' }}
        </button>
      </section>
    </div>
  </Teleport>
</template>

<style src="./pwa-update-gate.pcss"></style>
