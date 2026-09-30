<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { AdminUsersApiError, type AdminUsersClient, type AdminRecoveryLink } from './admin-users-client'
import './recovery-link-panel.pcss'

const props = defineProps<{ client: AdminUsersClient; userId: number; disabled?: boolean }>()
const emit = defineEmits<{ ownerError: [cause: unknown] }>()
const link = ref<AdminRecoveryLink | null>(null)
const busy = ref(false)
const feedback = ref<string | null>(null)
const now = ref(Date.now())
const expired = ref(false)
const remaining = computed(() => link.value ? Math.max(0, Math.ceil((Date.parse(link.value.expiresAt) - now.value) / 1000)) : 0)
const expiryLabel = computed(() => link.value
  ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(link.value.expiresAt)) : '')
let disposed = false
let revision = 0
let abort: AbortController | null = null
let timer: ReturnType<typeof setInterval> | null = null

function clearLink(): void {
  link.value = null
  if (timer !== null) clearInterval(timer)
  timer = null
}

async function issue(): Promise<void> {
  if (busy.value || props.disabled) return
  const request = ++revision
  abort?.abort()
  const controller = new AbortController()
  abort = controller
  clearLink()
  expired.value = false
  feedback.value = null
  busy.value = true
  try {
    const result = await props.client.issueRecoveryLink(props.userId, controller.signal)
    if (disposed || request !== revision) return
    now.value = Date.now()
    if (Date.parse(result.expiresAt) <= now.value) {
      expired.value = true
      return
    }
    link.value = result
    timer = setInterval(() => {
      now.value = Date.now()
      if (remaining.value === 0) {
        clearLink()
        expired.value = true
      }
    }, 1000)
  } catch (cause) {
    if (disposed || request !== revision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    if (cause instanceof AdminUsersApiError && [401, 403, 426, 503].includes(cause.response.status)) emit('ownerError', cause)
    feedback.value = 'Не удалось создать ссылку восстановления. Попробуй ещё раз.'
  } finally {
    if (!disposed && request === revision) busy.value = false
  }
}

async function copy(): Promise<void> {
  const value = link.value
  if (!value || Date.parse(value.expiresAt) <= Date.now()) {
    clearLink()
    expired.value = true
    return
  }
  const request = revision
  try {
    await navigator.clipboard.writeText(value.url)
    if (!disposed && request === revision && link.value === value) feedback.value = 'Ссылка скопирована.'
  } catch {
    if (!disposed && request === revision) feedback.value = 'Не удалось скопировать. Выдели ссылку и скопируй её вручную.'
  }
}

onBeforeUnmount(() => {
  disposed = true
  ++revision
  abort?.abort()
  clearLink()
})
</script>

<template>
  <section
    class="recovery-link-panel"
    aria-label="Восстановление доступа"
  >
    <h3>Восстановление доступа</h3>
    <p class="admin-users-muted">
      Создай одноразовую ссылку и передай её пользователю. Он задаст новый пароль; Telegram не нужен.
    </p>
    <button
      class="admin-users-action admin-users-action--secondary"
      type="button"
      :disabled="busy || disabled"
      :aria-busy="busy"
      @click="issue"
    >
      {{ busy ? 'Создаём ссылку…' : 'Создать ссылку восстановления' }}
    </button>
    <template v-if="link">
      <label class="recovery-link-panel__field">
        <span>Одноразовая ссылка</span>
        <input
          type="text"
          readonly
          :value="link.url"
          autocomplete="off"
          spellcheck="false"
          @focus="($event.target as HTMLInputElement).select()"
        >
      </label>
      <p class="admin-users-muted">
        Действует до {{ expiryLabel }}. Осталось {{ remaining }} сек. После смены пароля все прежние сессии завершатся.
      </p>
      <button
        class="admin-users-action"
        type="button"
        :disabled="disabled"
        @click="copy"
      >
        Скопировать ссылку
      </button>
      <button
        class="admin-users-action admin-users-action--secondary"
        type="button"
        @click="clearLink"
      >
        Скрыть ссылку
      </button>
    </template>
    <p
      v-if="expired"
      role="status"
    >
      Срок ссылки истёк. Создай новую.
    </p>
    <p
      v-if="feedback"
      role="status"
      aria-live="polite"
    >
      {{ feedback }}
    </p>
  </section>
</template>
