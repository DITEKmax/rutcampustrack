<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  ProfileHistoryEvent,
  ProfileRequestError,
  ProfileResolvedTheme,
  ProfileRoute,
  ProfileRole,
  ProfileSnapshot,
  ProfileTheme,
} from '../../../../../frontends/mobile-core/src/features/profile/profile-types'
import { ProfileRequestError as ProfileError } from '../../../../../frontends/mobile-core/src/features/profile/profile-types'
import MoreScreen from '../../../../../frontends/mobile-core/src/features/profile/MoreScreen.vue'
import ProfileScreen from '../../../../../frontends/mobile-core/src/features/profile/ProfileScreen.vue'
import RoleSwitchScreen from '../../../../../frontends/mobile-core/src/features/profile/RoleSwitchScreen.vue'
import AppearanceScreen from '../../../../../frontends/mobile-core/src/features/profile/AppearanceScreen.vue'
import SecurityScreen from '../../../../../frontends/mobile-core/src/features/profile/SecurityScreen.vue'
import SessionsScreen from '../../../../../frontends/mobile-core/src/features/profile/SessionsScreen.vue'
import AccountHistoryScreen from '../../../../../frontends/mobile-core/src/features/profile/AccountHistoryScreen.vue'
import '../../../../../frontends/mobile-core/src/styles/tokens.pcss'

type ScreenComponent = typeof MoreScreen | typeof ProfileScreen | typeof RoleSwitchScreen | typeof AppearanceScreen | typeof SecurityScreen | typeof SessionsScreen | typeof AccountHistoryScreen

const screen = ref<ProfileRoute>('profile')
const theme = ref<ProfileTheme>('dark')
const resolvedTheme = ref<ProfileResolvedTheme>('dark')
const largeText = ref(false)
const profileScenario = ref<'ready' | 'loading' | 'error'>('ready')
const profileRetryCount = ref(0)
const bootstrapProfile = ref(false)
const longIdentity = ref(false)
const securityError = ref<ProfileRequestError | null>(null)
const securityOffline = ref(false)
const passwordSubmitCount = ref(0)
const lastPasswordPayload = ref('нет')
const rolePending = ref<ProfileRole | null>(null)
const roleError = ref<ProfileRequestError | null>(null)
const roleOffline = ref(false)
const roleLoading = ref(false)
const roleSelectCount = ref(0)
const sessionsError = ref<ProfileRequestError | null>(null)
const sessionsOffline = ref(false)
const sessionsLoading = ref(false)
const sessionsBusy = ref(false)
const sessionsLoadMoreCount = ref(0)
const sessionsLogoutAllCount = ref(0)
const historyError = ref<ProfileRequestError | null>(null)
const historyLoading = ref(false)
const historyLoadMoreCount = ref(0)

const snapshot: ProfileSnapshot = {
  sessionId: '6f35d3f3-4f4d-4e09-9b64-0b48ca96bd42',
  userId: '1701',
  displayName: 'Александра Соколова',
  groupLabel: 'ИСТ-21',
  sessionVersion: '104',
  rolesVersion: '8',
  activeRole: 'STUDENT',
  readOnly: false,
  passwordPolicy: {
    minCodePoints: 12,
    maxUtf8Bytes: 72,
    requiresDecimalDigit: true,
    specialCategories: ['P', 'S'],
    normalization: 'NONE',
  },
  roles: [
    { grantId: '7001', role: 'STUDENT', status: 'ACTIVE', groupId: '2101', contextLabel: 'ИСТ-21', selectable: true, readOnly: false },
    { grantId: '7002', role: 'HEADMAN', status: 'ACTIVE', groupId: '2101', contextLabel: 'ИСТ-21', selectable: true, readOnly: false },
    { grantId: '7003', role: 'TEACHER', status: 'GRADUATED', contextLabel: 'Архивный доступ', selectable: true, readOnly: true },
    { grantId: '7004', role: 'ADMIN', status: 'SUSPENDED', contextLabel: 'Недоступна', selectable: false, readOnly: false },
  ],
}

const sessions = [
  { sessionId: '6f35d3f3-4f4d-4e09-9b64-0b48ca96bd42', authMethod: 'PASSWORD' as const, clientLabel: 'Chrome на Windows', locationLabel: 'Москва', createdAt: '2026-09-07T09:00:00Z', lastSeenAt: '2026-09-08T08:30:00Z', current: true },
  { sessionId: 'f40efc8e-45be-4366-8f7e-3d2f19a8c7b1', authMethod: 'TMA' as const, clientLabel: 'Telegram', locationLabel: null, createdAt: '2026-08-28T11:00:00Z', lastSeenAt: '2026-09-06T19:10:00Z', current: false },
]

const history: readonly ProfileHistoryEvent[] = [
  { id: '9003', type: 'ROLE_CHANGED', occurredAt: '2026-09-08T07:30:00Z', authMethod: 'PASSWORD', clientLabel: 'Chrome на Windows', locationLabel: 'Москва' },
  { id: '9002', type: 'LOGIN', occurredAt: '2026-09-07T09:00:00Z', authMethod: 'PASSWORD', clientLabel: 'Chrome на Windows', locationLabel: null },
  { id: '9001', type: 'PASSWORD_CHANGED', occurredAt: '2026-08-14T16:45:00Z', authMethod: null, clientLabel: null, locationLabel: null },
]

const displaySnapshot = computed<ProfileSnapshot>(() => ({
  ...snapshot,
  displayName: longIdentity.value ? 'Александра Константиновна Соколова-Волкова' : snapshot.displayName,
  activeRole: bootstrapProfile.value ? null : snapshot.activeRole,
}))

const screenComponents: Record<ProfileRoute, ScreenComponent> = {
  statistics: MoreScreen,
  map: MoreScreen,
  requests: MoreScreen,
  profile: ProfileScreen,
  'role-switch': RoleSwitchScreen,
  appearance: AppearanceScreen,
  security: SecurityScreen,
  sessions: SessionsScreen,
  history: AccountHistoryScreen,
}

const screenComponent = computed(() => screenComponents[screen.value])
const screenProps = computed<Record<string, unknown>>(() => {
  if (screen.value === 'statistics' || screen.value === 'map' || screen.value === 'requests') {
    return { theme: resolvedTheme.value, onNavigate: navigate }
  }
  if (screen.value === 'profile') {
    return {
      snapshot: profileScenario.value === 'error' ? null : displaySnapshot.value,
      loading: profileScenario.value === 'loading',
      error: profileScenario.value === 'error' ? new ProfileError('NETWORK', 'Сервер профиля временно недоступен') : null,
      theme: resolvedTheme.value,
      onRetry: retryProfile,
      onNavigate: navigate,
    }
  }
  if (screen.value === 'role-switch') {
    return {
      snapshot: displaySnapshot.value,
      theme: resolvedTheme.value,
      pendingRole: rolePending.value,
      error: roleError.value,
      loading: roleLoading.value,
      offline: roleOffline.value,
      onBack: () => navigate('profile'),
      onSelectRole: selectRole,
    }
  }
  if (screen.value === 'appearance') {
    return {
      theme: theme.value,
      resolvedTheme: resolvedTheme.value,
      onBack: () => navigate('profile'),
      onThemeChange: changeTheme,
    }
  }
  if (screen.value === 'security') {
    return {
      policy: snapshot.passwordPolicy,
      error: securityError.value,
      busy: false,
      offline: securityOffline.value,
      theme: resolvedTheme.value,
      onBack: () => navigate('profile'),
      onRecover: () => { lastPasswordPayload.value = 'recovery' },
      onChangePassword: changePassword,
    }
  }
  if (screen.value === 'sessions') {
    return {
      sessions,
      nextCursor: sessionsLoadMoreCount.value > 0 ? null : 'sessions-next',
      loading: sessionsLoading.value,
      error: sessionsError.value,
      busy: sessionsBusy.value,
      offline: sessionsOffline.value,
      theme: resolvedTheme.value,
      onBack: () => navigate('profile'),
      onRetry: retrySessions,
      onLoadMore: loadMoreSessions,
      onLogoutAll: logoutAllSessions,
    }
  }
  return {
    events: history,
    nextCursor: historyLoadMoreCount.value > 0 ? null : 'history-next',
    loading: historyLoading.value,
    error: historyError.value,
    theme: resolvedTheme.value,
    onBack: () => navigate('profile'),
    onRetry: retryHistory,
    onLoadMore: loadMoreHistory,
  }
})

function navigate(route: ProfileRoute): void {
  screen.value = route
}

function selectRole(role: ProfileRole): void {
  roleSelectCount.value += 1
  rolePending.value = role
}

function retryProfile(): void {
  profileRetryCount.value += 1
  profileScenario.value = 'ready'
}

function retrySessions(): void {
  sessionsError.value = null
  sessionsLoading.value = false
}

function loadMoreSessions(): void {
  sessionsLoadMoreCount.value += 1
}

function logoutAllSessions(): void {
  sessionsLogoutAllCount.value += 1
}

function retryHistory(): void {
  historyError.value = null
  historyLoading.value = false
}

function loadMoreHistory(): void {
  historyLoadMoreCount.value += 1
}

function changeTheme(value: ProfileTheme): void {
  theme.value = value
  if (value === 'system') return
  resolvedTheme.value = value
}

async function changePassword(input: { currentPassword: string; newPassword: string }): Promise<void> {
  passwordSubmitCount.value += 1
  lastPasswordPayload.value = input.newPassword.length > 0 ? 'получен' : 'пусто'
}

function setSecurityInvalidation(): void {
  securityError.value = new ProfileError('ACCOUNT_INVALIDATED', 'Аккаунт больше недоступен')
}

function setSecurityOrdinaryError(): void {
  securityError.value = new ProfileError('CURRENT_PASSWORD_INVALID', 'Текущий пароль неверен')
}

function clearSecurityError(): void {
  securityError.value = null
}

function toggleLargeText(): void {
  largeText.value = !largeText.value
}
</script>

<template>
  <div
    class="harness"
    :class="{ 'harness--large': largeText }"
  >
    <header class="harness__toolbar">
      <label>
        Экран
        <select v-model="screen" data-testid="screen-selector">
          <option value="profile">Профиль</option>
          <option value="role-switch">Смена роли</option>
          <option value="appearance">Оформление</option>
          <option value="security">Безопасность</option>
          <option value="sessions">Сеансы</option>
          <option value="history">История</option>
          <option value="statistics">Ещё: статистика</option>
          <option value="map">Ещё: карта</option>
          <option value="requests">Ещё: заявки</option>
        </select>
      </label>
      <label>
        Тема
        <select v-model="resolvedTheme" data-testid="resolved-theme">
          <option value="dark">Тёмная</option>
          <option value="light">Светлая</option>
        </select>
      </label>
      <button type="button" data-testid="large-text" @click="toggleLargeText">
        {{ largeText ? 'Обычный текст' : '200% текст' }}
      </button>
    </header>

    <div
      v-if="screen === 'profile'"
      class="harness__scenario-controls"
      aria-label="Управление сценарием профиля"
    >
      <button type="button" data-testid="profile-ready" @click="profileScenario = 'ready'">Профиль готов</button>
      <button type="button" data-testid="profile-loading" @click="profileScenario = 'loading'">Профиль загружается</button>
      <button type="button" data-testid="profile-error" @click="profileScenario = 'error'">Ошибка профиля</button>
      <label><input v-model="longIdentity" type="checkbox" data-testid="long-identity" /> Длинное имя</label>
      <label><input v-model="bootstrapProfile" type="checkbox" data-testid="bootstrap-profile" /> Bootstrap без роли</label>
      <output data-testid="profile-retry-count">Повторов: {{ profileRetryCount }}</output>
    </div>

    <div
      v-if="screen === 'role-switch'"
      class="harness__scenario-controls"
      aria-label="Управление сценарием ролей"
    >
      <button type="button" data-testid="role-clear" @click="rolePending = null; roleError = null; roleLoading = false">Готовые роли</button>
      <button type="button" data-testid="role-loading" @click="roleLoading = true">Загрузка ролей</button>
      <button type="button" data-testid="role-error" @click="roleError = new ProfileError('SESSION_VERSION_CONFLICT', 'Версия сессии устарела')">Ошибка конфликта</button>
      <label><input v-model="roleOffline" type="checkbox" data-testid="role-offline" /> Офлайн</label>
      <output data-testid="role-select-count">Выборов: {{ roleSelectCount }}</output>
    </div>

    <div
      v-if="screen === 'sessions'"
      class="harness__scenario-controls"
      aria-label="Управление сценарием сеансов"
    >
      <button type="button" data-testid="sessions-ready" @click="sessionsError = null; sessionsLoading = false">Сеансы готовы</button>
      <button type="button" data-testid="sessions-loading" @click="sessionsLoading = true">Загрузка сеансов</button>
      <button type="button" data-testid="sessions-error" @click="sessionsError = new ProfileError('NETWORK', 'Список сеансов недоступен')">Ошибка сеансов</button>
      <label><input v-model="sessionsOffline" type="checkbox" data-testid="sessions-offline" /> Офлайн</label>
      <label><input v-model="sessionsBusy" type="checkbox" data-testid="sessions-busy" /> Logout busy</label>
      <output data-testid="sessions-load-more-count">Ещё: {{ sessionsLoadMoreCount }}</output>
      <output data-testid="sessions-logout-count">Logout: {{ sessionsLogoutAllCount }}</output>
    </div>

    <div
      v-if="screen === 'history'"
      class="harness__scenario-controls"
      aria-label="Управление сценарием истории"
    >
      <button type="button" data-testid="history-ready" @click="historyError = null; historyLoading = false">История готова</button>
      <button type="button" data-testid="history-loading" @click="historyLoading = true">Загрузка истории</button>
      <button type="button" data-testid="history-error" @click="historyError = new ProfileError('NETWORK', 'История недоступна')">Ошибка истории</button>
      <output data-testid="history-load-more-count">Ещё: {{ historyLoadMoreCount }}</output>
    </div>

    <div
      v-if="screen === 'security'"
      class="harness__security-controls"
      aria-label="Управление сценарием безопасности"
    >
      <button type="button" data-testid="security-invalidate" @click="setSecurityInvalidation">
        Сменить ошибку на ACCOUNT_INVALIDATED
      </button>
      <button type="button" data-testid="security-ordinary-error" @click="setSecurityOrdinaryError">
        Обычная ошибка пароля
      </button>
      <button type="button" data-testid="security-clear-error" @click="clearSecurityError">
        Очистить ошибку
      </button>
      <label>
        <input v-model="securityOffline" type="checkbox" data-testid="security-offline" />
        Офлайн
      </label>
      <output data-testid="password-submit-count">Отправок: {{ passwordSubmitCount }}</output>
      <output data-testid="password-last-payload">Последний результат: {{ lastPasswordPayload }}</output>
    </div>

    <component
      :is="screenComponent"
      v-bind="screenProps"
      :data-screen="screen"
    />
  </div>
</template>

<style>
:root {
  color-scheme: dark;
  background: #111018;
  font-family: Arial, sans-serif;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-width: 20rem;
  background: #111018;
}

button,
select,
input {
  font: inherit;
}

.harness {
  min-height: 100vh;
  color: #f5f1fb;
  background: #111018;
}

.harness__toolbar,
.harness__scenario-controls,
.harness__security-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  padding: 0.75rem;
  background: #25212c;
}

.harness__toolbar {
  position: sticky;
  z-index: 2;
  top: 0;
}

.harness__toolbar label,
.harness__scenario-controls label,
.harness__security-controls label {
  display: inline-flex;
  gap: 0.25rem;
  align-items: center;
  font-size: 0.75rem;
}

.harness__toolbar select,
.harness__toolbar button,
.harness__scenario-controls button,
.harness__security-controls button,
.harness__security-controls input {
  min-height: 2.75rem;
}

.harness__toolbar button,
.harness__scenario-controls button,
.harness__security-controls button,
.harness__toolbar select {
  border: 1px solid #827c8c;
  border-radius: 0.5rem;
  padding: 0.35rem 0.6rem;
  color: #fff;
  background: #3d3748;
}

.harness__security-controls {
  align-items: stretch;
}

.harness__security-controls output {
  align-self: center;
  font-size: 0.75rem;
}

.harness--large {
  font-size: 200%;
}

@media (prefers-color-scheme: light) {
  :root,
  body {
    color-scheme: light;
    background: #f4f1f8;
  }
}
</style>
