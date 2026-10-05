<script setup lang="ts">
import type { ProfileRequestError, ProfileResolvedTheme, ProfileSessionSummary } from './profile-types'
import { formatProfileInstant, truthfulMetadata } from './profile-types'
import sessionsPrevious from './assets/sessions-previous.svg'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  sessions: readonly ProfileSessionSummary[]
  loading?: boolean
  error?: ProfileRequestError | null
  terminatingSessionId?: string | null
  terminationError?: ProfileRequestError | null
  onTerminateSession?: ((sessionId: string) => void | Promise<void>) | undefined
  busy?: boolean
  offline?: boolean
  onBack?: (() => void | Promise<void>) | undefined
  onRetry?: (() => void | Promise<void>) | undefined
  onLogoutAll?: (() => void | Promise<void>) | undefined
  theme?: ProfileResolvedTheme
}>(), {
  loading: false,
  error: null,
  terminatingSessionId: null,
  terminationError: null,
  onTerminateSession: undefined,
  busy: false,
  offline: false,
  onBack: undefined,
  onRetry: undefined,
  onLogoutAll: undefined,
  theme: 'dark',
})

function authMethodLabel(method: ProfileSessionSummary['authMethod']): string | null {
  if (method === 'PASSWORD') return 'пароль'
  if (method === 'OTP') return 'одноразовый код'
  if (method === 'TMA') return 'Telegram'
  return null
}

function title(session: ProfileSessionSummary): string {
  return session.clientLabel?.trim() || 'Сеанс'
}

function metadata(session: ProfileSessionSummary): string {
  const method = authMethodLabel(session.authMethod)
  const lastSeen = `Последняя активность: ${formatProfileInstant(session.lastSeenAt)}`
  return truthfulMetadata(session.locationLabel, method, lastSeen) ?? lastSeen
}

async function terminate(sessionId: string): Promise<void> {
  if (props.offline || props.loading || props.busy || props.terminatingSessionId !== null || !props.onTerminateSession) return
  try { await props.onTerminateSession(sessionId) } catch { /* The account owner publishes terminationError and preserves the loaded list. */ }
}
</script>

<template>
  <main
    class="profile-screen profile-sessions"
    :data-theme="theme"
    aria-labelledby="profile-sessions-title"
  >
    <div class="profile-screen__content">
      <header class="profile-header">
        <button
          class="profile-back"
          type="button"
          aria-label="Назад"
          @click="onBack?.()"
        >
          <img
            :src="sessionsPrevious"
            alt=""
            aria-hidden="true"
          >
        </button>
        <h1
          id="profile-sessions-title"
          class="profile-header__title"
        >
          Активные сеансы
        </h1>
      </header>

      <StudentWarningBlock
        v-if="offline"
        title="Нет подключения"
        message="Управление сеансами доступно только онлайн."
      />

      <StudentWarningBlock
        v-if="terminationError"
        severity="error"
        title="Сеанс не завершён"
        :message="terminationError.message"
      />

      <StudentWarningBlock
        v-if="error && sessions.length > 0"
        severity="error"
        title="Не удалось загрузить все сеансы"
        :message="error.message"
        :action-label="onRetry ? 'Повторить' : ''"
        @action="onRetry"
      />

      <section
        v-if="loading && sessions.length === 0"
        class="profile-state"
        aria-live="polite"
      >
        Загружаем сеансы…
      </section>
      <StudentWarningBlock
        v-else-if="error && sessions.length === 0"
        severity="error"
        title="Не удалось загрузить сеансы"
        :message="error.message"
        :action-label="onRetry ? 'Повторить' : ''"
        @action="onRetry"
      />
      <section
        v-else-if="sessions.length === 0"
        class="profile-state"
        role="status"
      >
        <h2>Нет активных сеансов</h2>
        <p>Список активных сеансов пуст.</p>
      </section>
      <ul
        v-else
        class="profile-session-list"
        aria-label="Список активных сеансов"
      >
        <li
          v-for="session in sessions"
          :key="session.sessionId"
          class="profile-session-card"
        >
          <div class="profile-session-card__top">
            <p class="profile-session-card__title">
              {{ title(session) }}
            </p>
            <span
              v-if="session.current"
              class="profile-session-card__current"
            >Это устройство</span>
          </div>
          <p class="profile-session-card__meta">
            {{ metadata(session) }}
          </p>
          <button
            class="profile-session-terminate"
            type="button"
            :disabled="busy || loading || offline || terminatingSessionId !== null || !onTerminateSession"
            :aria-busy="terminatingSessionId === session.sessionId"
            @click="terminate(session.sessionId)"
          >
            {{ terminatingSessionId === session.sessionId ? 'Завершаем…' : session.current ? 'Завершить текущий сеанс' : 'Завершить сеанс' }}
          </button>
        </li>
      </ul>


      <button
        class="profile-danger-button"
        type="button"
        :disabled="busy || loading || offline || terminatingSessionId !== null || sessions.length === 0 || !onLogoutAll"
        @click="onLogoutAll?.()"
      >
        {{ busy && terminatingSessionId === null ? 'Завершаем сеансы…' : 'Выйти на всех устройствах' }}
      </button>
    </div>
  </main>
</template>
