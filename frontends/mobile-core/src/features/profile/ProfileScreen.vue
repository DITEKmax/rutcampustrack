<script setup lang="ts">
import { computed } from 'vue'
import type { ProfileRequestError, ProfileResolvedTheme, ProfileRoute, ProfileSnapshot } from './profile-types'
import { displayInitials, roleLabel } from './profile-types'
import profileAppearance from './assets/profile-appearance.svg'
import profileHistory from './assets/profile-history.svg'
import profileRoleSwitch from './assets/profile-role-switch.svg'
import profileSecurity from './assets/profile-security.svg'
import profileSessions from './assets/profile-sessions.svg'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  snapshot: ProfileSnapshot | null
  loading?: boolean
  error?: ProfileRequestError | null
  onRetry?: (() => void | Promise<void>) | undefined
  onNavigate?: ((route: ProfileRoute) => void | Promise<void>) | undefined
  onLogout?: (() => void | Promise<void>) | undefined
  logoutBusy?: boolean
  showActiveRole?: boolean
  showRoleSwitch?: boolean
  theme?: ProfileResolvedTheme
}>(), {
  loading: false,
  error: null,
  onRetry: undefined,
  onNavigate: undefined,
  onLogout: undefined,
  logoutBusy: false,
  showActiveRole: true,
  showRoleSwitch: true,
  theme: 'dark',
})

const rows = [
  { route: 'role-switch', label: 'Сменить роль', icon: profileRoleSwitch },
  { route: 'appearance', label: 'Оформление', icon: profileAppearance },
  { route: 'security', label: 'Безопасность', icon: profileSecurity },
  { route: 'sessions', label: 'Активные сеансы', icon: profileSessions },
  { route: 'history', label: 'История аккаунта', icon: profileHistory },
] as const satisfies readonly { route: Extract<ProfileRoute, 'role-switch' | 'appearance' | 'security' | 'sessions' | 'history'>; label: string; icon: string }[]
const visibleRows = computed(() => props.showRoleSwitch ? rows : rows.filter((item) => item.route !== 'role-switch'))

function navigate(route: ProfileRoute): void {
  void props.onNavigate?.(route)
}

function isBootstrapUnavailable(route: ProfileRoute): boolean {
  return !props.snapshot?.activeRole && (route === 'security' || route === 'history')
}

function logout(): void {
  if (props.logoutBusy) return
  void props.onLogout?.()
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
      <StudentWarningBlock
        v-else-if="error"
        severity="error"
        title="Не удалось загрузить профиль"
        :message="error.message"
        :action-label="onRetry ? 'Повторить' : ''"
        @action="onRetry"
      />
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
            <p
              v-if="showActiveRole || snapshot.groupLabel"
              class="profile-card__meta"
            >
              <template v-if="showActiveRole">
                {{ snapshot.activeRole ? roleLabel(snapshot.activeRole) : 'Роль не выбрана' }}
              </template><span v-if="snapshot.groupLabel">{{ showActiveRole ? ' · ' : '' }}{{ snapshot.groupLabel }}</span>
            </p>
          </div>
        </section>

        <nav aria-label="Настройки профиля">
          <div class="profile-screen__content">
            <button
              v-for="item in visibleRows"
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
      <button
        v-if="onLogout"
        class="profile-danger-button"
        type="button"
        :disabled="logoutBusy"
        :aria-busy="logoutBusy"
        @click="logout"
      >
        {{ logoutBusy ? 'Завершаем сеанс…' : 'Выйти' }}
      </button>
    </div>
  </main>
</template>
