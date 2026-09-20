<script setup lang="ts">
import type { ProfileRequestError, ProfileResolvedTheme, ProfileRoute, ProfileSnapshot } from './profile-types'
import { displayInitials, roleLabel } from './profile-types'
import profileAppearance from './assets/profile-appearance.svg'
import profileHistory from './assets/profile-history.svg'
import profileRoleSwitch from './assets/profile-role-switch.svg'
import profileSecurity from './assets/profile-security.svg'
import profileSessions from './assets/profile-sessions.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  snapshot: ProfileSnapshot | null
  loading?: boolean
  error?: ProfileRequestError | null
  onRetry?: () => void | Promise<void>
  onNavigate?: (route: ProfileRoute) => void | Promise<void>
  theme?: ProfileResolvedTheme
}>(), {
  loading: false,
  error: null,
  onRetry: undefined,
  onNavigate: undefined,
  theme: 'dark',
})

const rows = [
  { route: 'role-switch', label: 'Сменить роль', icon: profileRoleSwitch },
  { route: 'appearance', label: 'Оформление', icon: profileAppearance },
  { route: 'security', label: 'Безопасность', icon: profileSecurity },
  { route: 'sessions', label: 'Активные сеансы', icon: profileSessions },
  { route: 'history', label: 'История аккаунта', icon: profileHistory },
] as const satisfies readonly { route: Extract<ProfileRoute, 'role-switch' | 'appearance' | 'security' | 'sessions' | 'history'>; label: string; icon: string }[]

function navigate(route: ProfileRoute): void {
  void props.onNavigate?.(route)
}

function isBootstrapUnavailable(route: ProfileRoute): boolean {
  return !props.snapshot?.activeRole && (route === 'security' || route === 'history')
}
</script>

<template>
  <main
    class="profile-screen profile-page"
    :data-theme="theme"
    aria-labelledby="profile-title"
  >
    <div class="profile-screen__content">
      <h1
        id="profile-title"
        class="profile-screen__title"
      >
        Профиль
      </h1>

      <section
        v-if="loading"
        class="profile-state"
        aria-live="polite"
      >
        Загружаем профиль…
      </section>
      <section
        v-else-if="error"
        class="profile-state"
        data-error="true"
        role="alert"
      >
        <h2>Не удалось загрузить профиль</h2>
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
      <template v-else-if="snapshot">
        <section
          class="profile-card"
          aria-label="Данные профиля"
        >
          <div
            class="profile-avatar"
            aria-hidden="true"
          >
            {{ displayInitials(snapshot.displayName) }}
          </div>
          <div class="profile-card__copy">
            <p class="profile-card__name">
              {{ snapshot.displayName }}
            </p>
            <p class="profile-card__meta">
              {{ snapshot.activeRole ? roleLabel(snapshot.activeRole) : 'Роль не выбрана' }}<span v-if="snapshot.groupLabel"> · {{ snapshot.groupLabel }}</span>
            </p>
          </div>
        </section>

        <nav aria-label="Настройки профиля">
          <div class="profile-screen__content">
            <button
              v-for="item in rows"
              :key="item.route"
              class="profile-row"
              type="button"
              :disabled="isBootstrapUnavailable(item.route)"
              :aria-disabled="isBootstrapUnavailable(item.route)"
              @click="navigate(item.route)"
            >
              <img
                class="profile-icon"
                :src="item.icon"
                alt=""
                aria-hidden="true"
              >
              <span>{{ item.label }}</span>
            </button>
          </div>
        </nav>
      </template>
    </div>
  </main>
</template>
