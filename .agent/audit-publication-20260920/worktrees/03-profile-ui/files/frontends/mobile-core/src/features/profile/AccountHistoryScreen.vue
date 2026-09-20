<script setup lang="ts">
import type { ProfileHistoryEvent, ProfileRequestError, ProfileResolvedTheme } from './profile-types'
import { formatProfileInstant, historyLabel, truthfulMetadata } from './profile-types'
import historyPrevious from './assets/history-previous.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  events: readonly ProfileHistoryEvent[]
  loading?: boolean
  error?: ProfileRequestError | null
  nextCursor?: string | null
  onBack?: (() => void | Promise<void>) | undefined
  onRetry?: (() => void | Promise<void>) | undefined
  onLoadMore?: ((cursor: string) => void | Promise<void>) | undefined
  theme?: ProfileResolvedTheme
}>(), {
  loading: false,
  error: null,
  nextCursor: null,
  onBack: undefined,
  onRetry: undefined,
  onLoadMore: undefined,
  theme: 'dark',
})

function authMethodLabel(method: ProfileHistoryEvent['authMethod']): string | null {
  if (method === 'PASSWORD') return 'вход по паролю'
  if (method === 'OTP') return 'вход по одноразовому коду'
  if (method === 'TMA') return 'вход через Telegram'
  return null
}

function metadata(event: ProfileHistoryEvent): string | null {
  return truthfulMetadata(event.clientLabel, event.locationLabel, authMethodLabel(event.authMethod))
}

function loadMore(): void {
  if (props.nextCursor) void props.onLoadMore?.(props.nextCursor)
}
</script>

<template>
  <main
    class="profile-screen profile-history"
    :data-theme="theme"
    aria-labelledby="profile-history-title"
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
            :src="historyPrevious"
            alt=""
            aria-hidden="true"
          >
        </button>
        <h1
          id="profile-history-title"
          class="profile-header__title"
        >
          История аккаунта
        </h1>
      </header>

      <section
        v-if="loading && events.length === 0"
        class="profile-state"
        aria-live="polite"
      >
        Загружаем историю…
      </section>
      <section
        v-else-if="error"
        class="profile-state"
        data-error="true"
        role="alert"
      >
        <h2>Не удалось загрузить историю</h2>
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
        v-else-if="events.length === 0"
        class="profile-state"
        aria-live="polite"
      >
        <h2>История пока пуста</h2>
        <p>Здесь появятся подтверждённые события аккаунта.</p>
      </section>
      <ol
        v-else
        class="profile-history-list"
        aria-label="История аккаунта"
      >
        <li
          v-for="event in events"
          :key="event.id"
          class="profile-history-card"
        >
          <p class="profile-history-card__title">
            {{ historyLabel(event.type) }}
          </p>
          <p
            v-if="metadata(event)"
            class="profile-history-card__description"
          >
            {{ metadata(event) }}
          </p>
          <p class="profile-history-card__time">
            {{ formatProfileInstant(event.occurredAt) }}
          </p>
        </li>
      </ol>

      <button
        v-if="nextCursor"
        class="profile-show-more"
        type="button"
        :disabled="loading"
        @click="loadMore"
      >
        {{ loading ? 'Загружаем…' : 'Показать ещё' }}
      </button>
    </div>
  </main>
</template>
