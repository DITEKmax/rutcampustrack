<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import type { ProfileRequestError, ProfileRole, ProfileSnapshot } from '../../features/profile/profile-types'
import { roleInitial, roleLabel } from '../../features/profile/profile-types'
import './role-switch-dialog.pcss'

const props = withDefaults(defineProps<{
  snapshot: ProfileSnapshot | null
  pendingRole?: ProfileRole | null
  error?: ProfileRequestError | null
  loading?: boolean
  offline?: boolean
  onSelectRole?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
}>(), { pendingRole: null, error: null, loading: false, offline: false, onSelectRole: undefined })
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement | null>(null)
const titleId = useId()
let previousFocus: HTMLElement | null = null
let disposed = false
function close(): void { if (props.pendingRole === null) emit('close') }
function controls(): HTMLElement[] {
  return [...panel.value?.querySelectorAll<HTMLElement>('button:not(:disabled), [tabindex="0"]') ?? []]
}
function keydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); return }
  if (event.key !== 'Tab') return
  const available = controls()
  const first = available[0]
  const last = available[available.length - 1]
  if (!first || !last) { event.preventDefault(); panel.value?.focus(); return }
  if (event.shiftKey && (document.activeElement === first || !panel.value?.contains(document.activeElement))) {
    event.preventDefault(); last.focus()
  } else if (!event.shiftKey && (document.activeElement === last || !panel.value?.contains(document.activeElement))) {
    event.preventDefault(); first.focus()
  }
}
function containFocus(event: FocusEvent): void {
  if (!panel.value?.contains(event.target as Node)) (controls()[0] ?? panel.value)?.focus()
}
async function select(role: ProfileRole): Promise<void> {
  const snapshot = props.snapshot
  if (!snapshot || props.offline || props.loading || props.pendingRole !== null || !props.onSelectRole) return
  if (!snapshot.roles.some((grant) => grant.role === role && grant.selectable)) return
  if (snapshot.activeRole === role) { close(); return }
  try { await props.onSelectRole(role, snapshot.sessionVersion) } catch { /* owner renders the error */ }
}
onMounted(async () => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  await nextTick()
  if (disposed) return
  ;(controls()[0] ?? panel.value)?.focus()
  document.addEventListener('keydown', keydown, true)
  document.addEventListener('focusin', containFocus)
})
onBeforeUnmount(() => {
  disposed = true
  document.removeEventListener('keydown', keydown, true)
  document.removeEventListener('focusin', containFocus)
  previousFocus?.focus()
})
</script>

<template>
  <Teleport to="body">
    <div
      class="role-dialog-backdrop"
      @click.self="close"
    >
      <section
        ref="panel"
        class="role-dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
        :aria-busy="pendingRole !== null || loading"
      >
        <h2
          :id="titleId"
          class="role-dialog__title"
        >
          Сменить роль
        </h2>
        <p
          v-if="offline"
          class="role-dialog__notice"
          role="status"
        >
          Смена роли доступна только онлайн.
        </p>
        <p
          v-if="error"
          class="role-dialog__error"
          role="alert"
        >
          {{ error.message }}
        </p>
        <p
          v-if="loading"
          role="status"
        >
          Загружаем доступные роли…
        </p>
        <div
          v-else-if="snapshot"
          class="role-dialog__list"
          aria-label="Доступные роли"
        >
          <button
            v-for="grant in snapshot.roles"
            :key="grant.grantId"
            class="role-dialog__row"
            type="button"
            :data-selected="snapshot.activeRole === grant.role"
            :aria-pressed="snapshot.activeRole === grant.role"
            :disabled="offline || !grant.selectable || pendingRole !== null || !onSelectRole"
            @click="select(grant.role)"
          >
            <span
              class="role-dialog__avatar"
              aria-hidden="true"
            >{{ roleInitial(grant.role) }}</span>
            <span>{{ roleLabel(grant.role) }}</span>
            <span
              v-if="pendingRole === grant.role"
              class="role-dialog__pending"
              role="status"
            >Сохраняем…</span>
          </button>
          <p
            v-if="snapshot.roles.length === 0"
            role="status"
          >
            Для этого аккаунта нет доступных ролей.
          </p>
        </div>
      </section>
    </div>
  </Teleport>
</template>
