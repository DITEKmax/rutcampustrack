<script setup lang="ts">
import type { ProfileRequestError, ProfileResolvedTheme, ProfileRole, ProfileSnapshot } from './profile-types'
import { roleInitial, roleLabel, statusLabel } from './profile-types'
import rolePrevious from './assets/role-previous.svg'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  snapshot: ProfileSnapshot | null
  pendingRole?: ProfileRole | null
  error?: ProfileRequestError | null
  loading?: boolean
  offline?: boolean
  showRolesOnError?: boolean
  onBack?: (() => void | Promise<void>) | undefined
  onSelectRole?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  theme?: ProfileResolvedTheme
}>(), {
  pendingRole: null,
  error: null,
  loading: false,
  offline: false,
  showRolesOnError: false,
  onBack: undefined,
  onSelectRole: undefined,
  theme: 'dark',
})

async function selectRole(role: ProfileRole): Promise<void> {
  if (props.offline || !props.snapshot || props.pendingRole !== null) return
  const grant = props.snapshot.roles.find((candidate) => candidate.role === role)
  if (!grant?.selectable) return
  try {
    await props.onSelectRole?.(role, props.snapshot.sessionVersion)
  } catch {
    // The owner publishes the failure through the error prop; never leave an
    // event-handler rejection unobserved.
  }
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
          @click="onBack?.()"
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

      <StudentWarningBlock
        v-if="offline"
        title="Нет подключения"
        message="Смена роли доступна только онлайн."
      />

      <section
        v-if="loading"
        class="profile-state"
        aria-live="polite"
      >
        Загружаем доступные роли…
      </section>
      <StudentWarningBlock
        v-else-if="error && (!showRolesOnError || !snapshot)"
        severity="error"
        title="Не удалось загрузить роли"
        :message="error.message"
      />
      <section
        v-else-if="snapshot"
        class="profile-role-list"
        aria-label="Доступные роли"
      >
        <StudentWarningBlock
          v-if="error"
          severity="error"
          title="Роль не изменена"
          :message="error.message"
        />
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
            <span
              v-if="grant.status !== 'ACTIVE' || grant.readOnly"
              class="profile-role-card__meta"
            >{{ grant.status !== 'ACTIVE' ? statusLabel(grant.status) : '' }}{{ grant.readOnly ? (grant.status !== 'ACTIVE' ? ' · только чтение' : 'Только чтение') : '' }}</span>
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
