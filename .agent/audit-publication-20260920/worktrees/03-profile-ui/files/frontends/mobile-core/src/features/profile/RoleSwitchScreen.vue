<script setup lang="ts">
import type { ProfileRequestError, ProfileResolvedTheme, ProfileRole, ProfileSnapshot } from './profile-types'
import { roleInitial, roleLabel, statusLabel } from './profile-types'
import rolePrevious from './assets/role-previous.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  snapshot: ProfileSnapshot | null
  pendingRole?: ProfileRole | null
  error?: ProfileRequestError | null
  loading?: boolean
  offline?: boolean
  onBack?: (() => void | Promise<void>) | undefined
  onSelectRole?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  theme?: ProfileResolvedTheme
}>(), {
  pendingRole: null,
  error: null,
  loading: false,
  offline: false,
  onBack: undefined,
  onSelectRole: undefined,
  theme: 'dark',
})

function selectRole(role: ProfileRole): void {
  if (props.offline || !props.snapshot || props.pendingRole !== null) return
  const grant = props.snapshot.roles.find((candidate) => candidate.role === role)
  if (!grant?.selectable) return
  void props.onSelectRole?.(role, props.snapshot.sessionVersion)
}
</script>

<template>
  <main
    class="profile-screen profile-role-switch"
    :data-theme="theme"
    aria-labelledby="profile-role-title"
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
            :src="rolePrevious"
            alt=""
            aria-hidden="true"
          >
        </button>
        <h1
          id="profile-role-title"
          class="profile-header__title"
        >
          Сменить роль
        </h1>
      </header>

      <p
        v-if="offline"
        class="profile-inline-error"
        role="status"
      >
        Смена роли доступна только онлайн.
      </p>

      <section
        v-if="loading"
        class="profile-state"
        aria-live="polite"
      >
        Загружаем доступные роли…
      </section>
      <section
        v-else-if="error"
        class="profile-state"
        data-error="true"
        role="alert"
      >
        <h2>Не удалось загрузить роли</h2>
        <p>{{ error.message }}</p>
      </section>
      <section
        v-else-if="snapshot"
        class="profile-role-list"
        aria-label="Доступные роли"
      >
        <button
          v-for="grant in snapshot.roles"
          :key="grant.grantId"
          class="profile-role-card"
          :data-selected="snapshot.activeRole === grant.role"
          :data-disabled="offline || !grant.selectable"
          type="button"
          :disabled="offline || !grant.selectable || pendingRole !== null"
          :aria-pressed="snapshot.activeRole === grant.role"
          @click="selectRole(grant.role)"
        >
          <span
            class="profile-role-card__avatar"
            aria-hidden="true"
          >{{ roleInitial(grant.role) }}</span>
          <span class="profile-role-card__copy">
            <span class="profile-role-card__title">{{ roleLabel(grant.role) }}</span>
            <span
              v-if="grant.contextLabel"
              class="profile-role-card__meta"
            >{{ grant.contextLabel }}</span>
            <span class="profile-role-card__meta">{{ statusLabel(grant.status) }}<span v-if="grant.readOnly"> · только чтение</span></span>
          </span>
          <span
            v-if="pendingRole === grant.role"
            class="profile-role-card__status"
            role="status"
          >Сохраняем…</span>
        </button>
        <p
          v-if="snapshot.roles.length === 0"
          class="profile-state"
        >
          Для этого аккаунта нет доступных ролей.
        </p>
      </section>
    </div>
  </main>
</template>
