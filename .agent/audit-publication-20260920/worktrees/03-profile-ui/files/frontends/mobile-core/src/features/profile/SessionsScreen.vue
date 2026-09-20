<script setup lang="ts">
import type { ProfileRequestError, ProfileResolvedTheme, ProfileSessionSummary } from './profile-types'
import { formatProfileInstant, truthfulMetadata } from './profile-types'
import sessionsPrevious from './assets/sessions-previous.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  sessions: readonly ProfileSessionSummary[]
  loading?: boolean
  error?: ProfileRequestError | null
  nextCursor?: string | null
  busy?: boolean
  offline?: boolean
  onBack?: (() => void | Promise<void>) | undefined
  onRetry?: (() => void | Promise<void>) | undefined
  onLoadMore?: ((cursor: string) => void | Promise<void>) | undefined
  onLogoutAll?: (() => void | Promise<void>) | undefined
  theme?: ProfileResolvedTheme
}>(), {
  loading: false,
  error: null,
  nextCursor: null,
  busy: false,
  offline: false,
  onBack: undefined,
  onRetry: undefined,
  onLoadMore: undefined,
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

function loadMore(): void {
  if (props.nextCursor) void props.onLoadMore?.(props.nextCursor)
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
          @click="onBack"
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

      <p
        v-if="offline"
        class="profile-inline-error"
        role="status"
      >
        Управление сеансами доступно только онлайн.
      </p>

      <section
        v-if="loading && sessions.length === 0"
        class="profile-state"
        aria-live="polite"
      >
        Загружаем сеансы…
      </section>
      <section
        v-else-if="error"
        class="profile-state"
        data-error="true"
        role="alert"
      >
        <h2>Не удалось загрузить сеансы</h2>
        <p>{{ error.message }}</p>
        <button
          v-if="onRetry"
          class="profile-secondary-button"
          type="button"
          @click="onRetry"
        >
          Повторить
        </button>
      </section>
      <section
        v-else-if="sessions.length === 0"
        class="profile-state"
        data-error="true"
        role="alert"
      >
        <h2>Нет активных сеансов</h2>
        <p>Список не загрузился. Повтори попытку.</p>
        <button
          v-if="onRetry"
          class="profile-secondary-button"
          type="button"
          @click="onRetry"
        >
          Повторить
        </button>
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
        </li>
      </ul>

      <button
        v-if="nextCursor"
        class="profile-show-more"
        type="button"
        :disabled="loading"
        @click="loadMore"
      >
        {{ loading ? 'Загружаем…' : 'Показать ещё' }}
      </button>

      <button
        class="profile-danger-button"
        type="button"
        :disabled="busy || offline || sessions.length === 0"
        @click="onLogoutAll"
      >
        {{ busy ? 'Завершаем сеансы…' : 'Выйти на всех устройствах' }}
      </button>
    </div>
  </main>
</template>
