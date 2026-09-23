<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  AdminUsersApiError,
  type AdminManagedRole,
  type AdminRoleGrant,
  type AdminRoleStatus,
  type AdminUser,
  type CreateAdminUserInput,
  type UpdateAdminRoleInput,
  type UpdateAdminUserProfileInput,
  type AdminUsersClient,
} from './admin-users-client'
import './admin-users-screen.pcss'

const props = withDefaults(defineProps<{
  client: AdminUsersClient
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
}>()

const users = ref<readonly AdminUser[]>([])
const selectedUser = ref<AdminUser | null>(null)
const search = ref('')
const roleFilter = ref<AdminManagedRole | ''>('')
const statusFilter = ref<AdminRoleStatus | ''>('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const createVisible = ref(false)
const createLastName = ref('')
const createFirstName = ref('')
const createMiddleName = ref('')
const createRole = ref<Exclude<AdminManagedRole, 'HEADMAN'>>('STUDENT')
const createGroupId = ref('')
const createEmployeeNumber = ref('')
const createTelegramId = ref('')
const profileEditorVisible = ref(false)
const profileLoading = ref(false)
const profileOriginal = ref<AdminUser | null>(null)
const profileLastName = ref('')
const profileFirstName = ref('')
const profileMiddleName = ref('')
const profileEmployeeNumber = ref('')
const editorRole = ref<Exclude<AdminManagedRole, 'HEADMAN'>>('STUDENT')
const editorStatus = ref<AdminRoleStatus>('ACTIVE')
const editorGroupId = ref('')
const editorEmployeeNumber = ref('')
const editorTelegramId = ref('')
const transferGroupId = ref('')
const transferReason = ref('')

let disposed = false
let listRequestRevision = 0
let listAbortController: AbortController | null = null
let mutationRevision = 0
let mutationAbortController: AbortController | null = null
let profileLoadRevision = 0
let profileLoadAbortController: AbortController | null = null

const roleFilters: readonly { value: AdminManagedRole | ''; label: string }[] = [
  { value: '', label: 'Все роли' },
  { value: 'STUDENT', label: 'Студенты' },
  { value: 'TEACHER', label: 'Преподаватели' },
  { value: 'ADMIN', label: 'Администраторы' },
  { value: 'HEADMAN', label: 'Старосты' },
]
const roleStatusOptions: Record<AdminManagedRole, readonly AdminRoleStatus[]> = {
  STUDENT: ['ACTIVE', 'EXPELLED', 'GRADUATED', 'SUSPENDED', 'ARCHIVED'],
  TEACHER: ['ACTIVE', 'DISMISSED', 'SUSPENDED', 'ARCHIVED'],
  ADMIN: ['ACTIVE', 'ARCHIVED'],
  HEADMAN: ['ACTIVE', 'SUSPENDED', 'ARCHIVED'],
}
const statusFilters = computed<readonly { value: AdminRoleStatus | ''; label: string }[]>(() => [
  { value: '', label: 'Все статусы' },
  ...(roleFilter.value
    ? roleStatusOptions[roleFilter.value].map((status) => ({ value: status, label: statusLabel(status) }))
    : []),
])

const selectedGrant = computed<AdminRoleGrant | null>(() =>
  selectedUser.value?.roles.find((grant) => grant.role === editorRole.value) ?? null)
const editorCanUpdate = computed(() => selectedGrant.value?.canUpdate ?? true)
const editorStatuses = computed(() => {
  if (editorRole.value === 'ADMIN') return ['ACTIVE'] as const
  if (editorRole.value === 'TEACHER') return ['ACTIVE', 'DISMISSED', 'SUSPENDED'] as const
  return ['ACTIVE', 'EXPELLED', 'GRADUATED', 'SUSPENDED'] as const
})
const hasStudentGrant = computed(() => Boolean(selectedUser.value?.roles.some((grant) => grant.role === 'STUDENT')))
const canTransfer = computed(() => hasStudentGrant.value && selectedUser.value?.roles.some((grant) => grant.role === 'STUDENT' && grant.status === 'ACTIVE'))
const profileHasTeacherRole = computed(() => selectedUser.value?.role === 'TEACHER'
  || Boolean(selectedUser.value?.roles.some((grant) => grant.role === 'TEACHER')))
const totalLabel = computed(() => totalElements.value === 1 ? '1 пользователь' : `${totalElements.value} пользователей`)

onMounted(() => void refresh())

function beginListRequest(): { revision: number; controller: AbortController } {
  const revision = ++listRequestRevision
  listAbortController?.abort()
  const controller = new AbortController()
  listAbortController = controller
  return { revision, controller }
}

function isCurrentListRequest(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRequestRevision && listAbortController === controller
}

function beginMutation(): { revision: number; controller: AbortController } {
  const revision = ++mutationRevision
  mutationAbortController?.abort()
  const controller = new AbortController()
  mutationAbortController = controller
  listAbortController?.abort()
  ++listRequestRevision
  listAbortController = null
  return { revision, controller }
}

function isCurrentMutation(revision: number, controller: AbortController): boolean {
  return !disposed && revision === mutationRevision && mutationAbortController === controller
}

function isCurrentProfileLoad(revision: number, controller: AbortController, userId: number): boolean {
  return !disposed
    && revision === profileLoadRevision
    && profileLoadAbortController === controller
    && selectedUser.value?.id === userId
}

function cancelProfileLoad(): void {
  ++profileLoadRevision
  profileLoadAbortController?.abort()
  profileLoadAbortController = null
  profileLoading.value = false
}

async function refresh(): Promise<void> {
  const { revision, controller } = beginListRequest()
  loading.value = true
  error.value = null
  try {
    const result = await props.client.listUsers({
      search: search.value,
      role: roleFilter.value,
      roleStatus: statusFilter.value,
      page: page.value,
      size: 20,
    }, controller.signal)
    if (!isCurrentListRequest(revision, controller)) return
    users.value = result.items
    totalPages.value = result.totalPages
    totalElements.value = result.totalElements
    if (result.totalPages > 0 && page.value >= result.totalPages) {
      page.value = result.totalPages - 1
      return
    }
    const selectedId = selectedUser.value?.id
    selectedUser.value = selectedId === undefined
      ? (result.items[0] ?? null)
      : result.items.find((user) => user.id === selectedId) ?? null
    if (selectedUser.value) syncEditorFromUser(selectedUser.value)
  } catch (cause) {
    if (!isCurrentListRequest(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Список пользователей не удалось загрузить.')
  } finally {
    if (isCurrentListRequest(revision, controller)) {
      loading.value = false
      listAbortController = null
    }
  }
}

async function createUser(): Promise<void> {
  if (saving.value || profileEditorVisible.value) return
  const role = createRole.value
  let input: CreateAdminUserInput
  try {
    input = {
      lastName: createLastName.value,
      firstName: createFirstName.value,
      middleName: createMiddleName.value,
      role,
      ...(createGroupId.value.trim() ? { groupId: parsePositive(createGroupId.value, 'groupId') } : {}),
      ...(createEmployeeNumber.value.trim() ? { employeeNumber: createEmployeeNumber.value } : {}),
      ...(createTelegramId.value.trim() ? { telegramId: parsePositive(createTelegramId.value, 'telegramId') } : {}),
    }
    if (role === 'STUDENT' && (!input.groupId || !input.telegramId)) throw new Error('Для STUDENT нужны группа и Telegram ID.')
    if (role === 'TEACHER' && !input.employeeNumber?.trim()) throw new Error('Для TEACHER нужен табельный номер.')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Проверь данные пользователя.'
    return
  }
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const created = await props.client.createUser(input, controller.signal)
    if (!isCurrentMutation(revision, controller)) return
    selectedUser.value = created
    createVisible.value = false
    notice.value = `Пользователь создан. Логин: ${created.login}.`
    resetCreateForm()
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Пользователя не удалось создать.')
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function updateRole(): Promise<void> {
  const user = selectedUser.value
  if (!user || saving.value || profileEditorVisible.value || !editorCanUpdate.value) return
  let input: UpdateAdminRoleInput
  try {
    input = {
      status: editorStatus.value,
      ...(editorGroupId.value.trim() ? { groupId: parsePositive(editorGroupId.value, 'groupId') } : {}),
      ...(editorEmployeeNumber.value.trim() ? { employeeNumber: editorEmployeeNumber.value } : {}),
      ...(editorTelegramId.value.trim() ? { telegramId: parsePositive(editorTelegramId.value, 'telegramId') } : {}),
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Проверь данные роли.'
    return
  }
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await props.client.updateRole(user.id, editorRole.value, input, controller.signal)
    if (!isCurrentMutation(revision, controller)) return
    selectedUser.value = updated
    notice.value = `Роль ${roleLabel(editorRole.value).toLocaleLowerCase('ru-RU')} обновлена.`
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Роль не удалось обновить.')
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function beginProfileEdit(): Promise<void> {
  const user = selectedUser.value
  if (!user || saving.value || profileLoading.value || createVisible.value) return
  cancelProfileLoad()
  const revision = ++profileLoadRevision
  const controller = new AbortController()
  profileLoadAbortController = controller
  profileEditorVisible.value = true
  profileLoading.value = true
  profileOriginal.value = null
  error.value = null
  notice.value = null
  try {
    const fresh = await props.client.getUser(user.id, controller.signal)
    if (!isCurrentProfileLoad(revision, controller, user.id)) return
    selectedUser.value = fresh
    profileOriginal.value = fresh
    profileLastName.value = fresh.lastName
    profileFirstName.value = fresh.firstName
    profileMiddleName.value = fresh.middleName ?? ''
    profileEmployeeNumber.value = fresh.employeeNumber ?? ''
    syncEditorFromUser(fresh)
  } catch (cause) {
    if (!isCurrentProfileLoad(revision, controller, user.id) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    profileEditorVisible.value = false
    showError(cause, 'Данные пользователя не удалось загрузить.')
  } finally {
    if (isCurrentProfileLoad(revision, controller, user.id)) {
      profileLoading.value = false
      profileLoadAbortController = null
    }
  }
}

function cancelProfileEdit(): void {
  cancelProfileLoad()
  profileEditorVisible.value = false
  profileOriginal.value = null
}

async function updateProfile(): Promise<void> {
  const user = selectedUser.value
  const original = profileOriginal.value
  if (!user || !original || user.id !== original.id || saving.value || profileLoading.value) return

  const lastName = profileLastName.value.trim()
  const firstName = profileFirstName.value.trim()
  const middleName = profileMiddleName.value.trim()
  const employeeNumber = profileEmployeeNumber.value.trim()
  if (!lastName || !firstName) {
    error.value = 'Укажи фамилию и имя.'
    return
  }
  if (profileHasTeacherRole.value && !employeeNumber) {
    error.value = 'Для преподавателя табельный номер обязателен.'
    return
  }

  const input: {
    lastName?: string
    firstName?: string
    middleName?: string
    employeeNumber?: string
  } = {}
  if (lastName !== original.lastName) input.lastName = lastName
  if (firstName !== original.firstName) input.firstName = firstName
  if (middleName !== (original.middleName ?? '')) input.middleName = middleName
  if (employeeNumber !== (original.employeeNumber ?? '')) input.employeeNumber = employeeNumber
  if (Object.keys(input).length === 0) {
    cancelProfileEdit()
    notice.value = 'Изменений нет.'
    return
  }

  const patch: UpdateAdminUserProfileInput = input
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await props.client.updateProfile(user.id, patch, controller.signal)
    if (!isCurrentMutation(revision, controller) || selectedUser.value?.id !== user.id) return
    selectedUser.value = updated
    syncEditorFromUser(updated)
    cancelProfileEdit()
    notice.value = 'Данные пользователя сохранены.'
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Данные пользователя не удалось сохранить.')
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function transferStudent(): Promise<void> {
  const user = selectedUser.value
  if (!user || !canTransfer.value) return
  let newGroupId: number
  try {
    newGroupId = parsePositive(transferGroupId.value, 'newGroupId')
    if (!transferReason.value.trim()) throw new Error('Укажи причину перевода.')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Проверь данные перевода.'
    return
  }
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await props.client.transferStudent(user.id, {
      newGroupId,
      reason: transferReason.value,
    }, controller.signal)
    if (!isCurrentMutation(revision, controller)) return
    selectedUser.value = updated
    transferGroupId.value = ''
    transferReason.value = ''
    notice.value = 'Перевод сохранён сервером.'
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Перевод не удалось сохранить.')
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

function selectUser(user: AdminUser): void {
  cancelProfileEdit()
  selectedUser.value = user
  syncEditorFromUser(user)
  error.value = null
  notice.value = null
}

function syncEditorFromUser(user: AdminUser): void {
  const grant = user.roles.find((candidate) => candidate.role === editorRole.value)
  editorStatus.value = grant?.status ?? 'ACTIVE'
  editorGroupId.value = grant?.groupId === null || grant?.groupId === undefined ? '' : String(grant.groupId)
  editorEmployeeNumber.value = editorRole.value === 'TEACHER' ? user.employeeNumber ?? '' : ''
  editorTelegramId.value = editorRole.value === 'STUDENT' ? user.telegramId === null ? '' : String(user.telegramId) : ''
  transferGroupId.value = ''
  transferReason.value = ''
}

function changeEditorRole(role: Exclude<AdminManagedRole, 'HEADMAN'>): void {
  editorRole.value = role
  if (selectedUser.value) syncEditorFromUser(selectedUser.value)
}

function resetCreateForm(): void {
  createLastName.value = ''
  createFirstName.value = ''
  createMiddleName.value = ''
  createRole.value = 'STUDENT'
  createGroupId.value = ''
  createEmployeeNumber.value = ''
  createTelegramId.value = ''
}

function submitSearch(): void {
  if (saving.value || profileEditorVisible.value) return
  page.value = 0
  void refresh()
}

function changePage(next: number): void {
  if (saving.value || profileEditorVisible.value || next < 0 || (totalPages.value > 0 && next >= totalPages.value)) return
  page.value = next
  void refresh()
}

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof AdminUsersApiError
    ? cause.problem?.detail ?? cause.message
    : cause instanceof Error ? cause.message : fallback
  if (cause instanceof AdminUsersApiError && (cause.response.status === 401 || cause.response.status === 403)) emit('ownerError', cause)
}

function parsePositive(value: string, field: string): number {
  const parsed = Number(value.trim())
  if (!Number.isSafeInteger(parsed) || parsed <= 0) throw new Error(`${field} должен быть положительным числом.`)
  return parsed
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

function roleLabel(role: AdminManagedRole): string {
  return role === 'STUDENT' ? 'Студент' : role === 'TEACHER' ? 'Преподаватель' : role === 'ADMIN' ? 'Администратор' : 'Староста'
}

function statusLabel(status: AdminRoleStatus): string {
  return status === 'ACTIVE' ? 'Активна'
    : status === 'SUSPENDED' ? 'Приостановлена'
      : status === 'EXPELLED' ? 'Отчислен'
        : status === 'GRADUATED' ? 'Выпустился'
          : status === 'DISMISSED' ? 'Уволен' : 'Архивная'
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium' }).format(date)
}

watch(editorStatuses, (statuses) => {
  if (!statuses.includes(editorStatus.value as never)) editorStatus.value = statuses[0]
})

onBeforeUnmount(() => {
  disposed = true
  ++listRequestRevision
  ++mutationRevision
  listAbortController?.abort()
  mutationAbortController?.abort()
  cancelProfileLoad()
  listAbortController = null
  mutationAbortController = null
})
</script>

<template>
  <main
    class="admin-users-screen"
    :data-theme="theme"
    aria-labelledby="admin-users-title"
  >
    <header class="admin-users-screen__header">
      <p>Администрирование</p>
      <div class="admin-users-screen__heading-row">
        <div>
          <h1 id="admin-users-title">
            Пользователи
          </h1>
          <p class="admin-users-screen__description">
            Поиск людей и управление действующими правами ролей.
          </p>
        </div>
        <button
          class="admin-users-screen__new"
          type="button"
          :disabled="profileEditorVisible || saving"
          :aria-expanded="createVisible"
          @click="createVisible = !createVisible; error = null; notice = null"
        >
          {{ createVisible ? 'Скрыть' : '+ Пользователь' }}
        </button>
      </div>
    </header>

    <form
      class="admin-users-toolbar"
      @submit.prevent="submitSearch"
    >
      <label class="admin-users-toolbar__search">
        <span>Поиск</span>
        <input
          v-model="search"
          :disabled="saving || profileEditorVisible"
          type="search"
          placeholder="ФИО, логин или табельный номер"
          autocomplete="off"
        >
      </label>
      <label>
        <span>Роль</span>
        <select
          v-model="roleFilter"
          :disabled="saving || profileEditorVisible"
          @change="statusFilter = ''; submitSearch()"
        >
          <option
            v-for="option in roleFilters"
            :key="option.value"
            :value="option.value"
          >{{ option.label }}</option>
        </select>
      </label>
      <label>
        <span>Статус</span>
        <select
          v-model="statusFilter"
          :disabled="!roleFilter || saving || profileEditorVisible"
          @change="submitSearch"
        >
          <option
            v-for="option in statusFilters"
            :key="option.value"
            :value="option.value"
          >{{ option.label }}</option>
        </select>
      </label>
      <button
        class="admin-users-action"
        type="submit"
        :disabled="loading || saving || profileEditorVisible"
      >
        Найти
      </button>
    </form>

    <form
      v-if="createVisible"
      class="admin-users-card admin-users-form"
      @submit.prevent="createUser"
    >
      <h2>Новый пользователь</h2>
      <div class="admin-users-form__grid">
        <label><span>Фамилия</span><input
          v-model="createLastName"
          required
          type="text"
          autocomplete="family-name"
        ></label>
        <label><span>Имя</span><input
          v-model="createFirstName"
          required
          type="text"
          autocomplete="given-name"
        ></label>
        <label><span>Отчество</span><input
          v-model="createMiddleName"
          type="text"
          autocomplete="additional-name"
        ></label>
        <label><span>Роль</span><select v-model="createRole"><option value="STUDENT">Студент</option><option value="TEACHER">Преподаватель</option><option value="ADMIN">Администратор</option></select></label>
        <label v-if="createRole === 'STUDENT'"><span>ID группы</span><input
          v-model="createGroupId"
          inputmode="numeric"
          required
          type="text"
        ></label>
        <label v-if="createRole === 'STUDENT'"><span>Telegram ID</span><input
          v-model="createTelegramId"
          inputmode="numeric"
          required
          type="text"
        ></label>
        <label v-if="createRole === 'TEACHER'"><span>Табельный номер</span><input
          v-model="createEmployeeNumber"
          required
          type="text"
        ></label>
      </div>
      <p class="admin-users-form__hint">
        Логин создаст сервер. Начальный пароль в списке не показывается.
      </p>
      <button
        class="admin-users-action"
        type="submit"
        :disabled="saving"
        :aria-busy="saving"
      >
        {{ saving ? 'Создаём…' : 'Создать' }}
      </button>
    </form>

    <p
      v-if="loading"
      class="admin-users-state"
      role="status"
    >
      Загружаем пользователей…
    </p>
    <p
      v-else-if="error"
      class="admin-users-state admin-users-state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      class="admin-users-state admin-users-state--success"
      role="status"
    >
      {{ notice }}
    </p>
    <p
      v-if="!loading && !error && users.length === 0"
      class="admin-users-state"
      data-state="empty"
    >
      Пользователи не найдены.
    </p>

    <section
      v-if="!loading && users.length"
      class="admin-users-layout"
      aria-label="Результаты поиска пользователей"
    >
      <div class="admin-users-list">
        <div class="admin-users-list__summary">
          {{ totalLabel }}
        </div>
        <button
          v-for="user in users"
          :key="user.id"
          type="button"
          class="admin-users-row"
          :class="{ 'admin-users-row--selected': selectedUser?.id === user.id }"
          :disabled="saving || profileEditorVisible"
          @click="selectUser(user)"
        >
          <strong>{{ user.fullName }}</strong>
          <span>{{ user.login }} · {{ user.roles.map((grant) => `${roleLabel(grant.role)}: ${statusLabel(grant.status).toLocaleLowerCase('ru-RU')}`).join(' · ') || roleLabel(user.role) }}</span>
        </button>
        <div class="admin-users-pagination">
          <button
            type="button"
            :disabled="saving || profileEditorVisible || page === 0"
            @click="changePage(page - 1)"
          >
            ← Назад
          </button>
          <span>{{ totalPages ? `${page + 1} из ${totalPages}` : '—' }}</span>
          <button
            type="button"
            :disabled="saving || profileEditorVisible || totalPages === 0 || page + 1 >= totalPages"
            @click="changePage(page + 1)"
          >
            Вперёд →
          </button>
        </div>
      </div>

      <aside
        v-if="selectedUser"
        class="admin-users-card admin-users-detail"
        aria-labelledby="admin-users-detail-title"
      >
        <div class="admin-users-detail__heading">
          <div>
            <p class="admin-users-eyebrow">
              Карточка пользователя
            </p><h2 id="admin-users-detail-title">
              {{ selectedUser.fullName }}
            </h2>
          </div>
          <span class="admin-users-login">{{ selectedUser.login }}</span>
        </div>
        <dl class="admin-users-meta">
          <div><dt>ID</dt><dd>{{ selectedUser.id }}</dd></div>
          <div><dt>Создан</dt><dd>{{ formatDate(selectedUser.createdAt) }}</dd></div>
          <div><dt>Табельный номер</dt><dd>{{ selectedUser.employeeNumber ?? '—' }}</dd></div>
          <div><dt>Telegram ID</dt><dd>{{ selectedUser.telegramId ?? '—' }}</dd></div>
        </dl>
        <button
          v-if="!profileEditorVisible"
          class="admin-users-action admin-users-action--secondary"
          type="button"
          :disabled="saving || createVisible"
          @click="void beginProfileEdit()"
        >
          Изменить ФИО и табельный номер
        </button>
        <p
          v-if="profileLoading"
          class="admin-users-state"
          role="status"
        >
          Загружаем актуальные данные пользователя…
        </p>
        <button
          v-if="profileLoading"
          class="admin-users-action admin-users-action--secondary"
          type="button"
          @click="cancelProfileEdit"
        >
          Отмена
        </button>
        <form
          v-if="profileEditorVisible && profileOriginal"
          class="admin-users-editor"
          @submit.prevent="updateProfile"
        >
          <h3>Личные данные</h3>
          <label for="admin-user-profile-last-name"><span>Фамилия</span><input
            id="admin-user-profile-last-name"
            v-model="profileLastName"
            :disabled="saving || profileLoading"
            aria-describedby="admin-user-profile-name-hint"
            autocomplete="family-name"
            maxlength="128"
            required
            type="text"
          ></label>
          <label for="admin-user-profile-first-name"><span>Имя</span><input
            id="admin-user-profile-first-name"
            v-model="profileFirstName"
            :disabled="saving || profileLoading"
            aria-describedby="admin-user-profile-name-hint"
            autocomplete="given-name"
            maxlength="128"
            required
            type="text"
          ></label>
          <label for="admin-user-profile-middle-name"><span>Отчество</span><input
            id="admin-user-profile-middle-name"
            v-model="profileMiddleName"
            :disabled="saving || profileLoading"
            aria-describedby="admin-user-profile-middle-hint"
            autocomplete="additional-name"
            maxlength="128"
            type="text"
          ></label>
          <p
            id="admin-user-profile-name-hint"
            class="admin-users-form__hint"
          >
            Фамилия и имя обязательны. Максимальная длина каждого поля — 128 символов.
          </p>
          <p
            id="admin-user-profile-middle-hint"
            class="admin-users-form__hint"
          >
            Оставь поле пустым, чтобы убрать отчество.
          </p>
          <label for="admin-user-profile-employee-number"><span>Табельный номер</span><input
            id="admin-user-profile-employee-number"
            v-model="profileEmployeeNumber"
            :disabled="saving || profileLoading"
            aria-describedby="admin-user-profile-employee-hint"
            :required="profileHasTeacherRole"
            autocomplete="off"
            maxlength="32"
            type="text"
          ></label>
          <p
            id="admin-user-profile-employee-hint"
            class="admin-users-form__hint"
          >
            Пустое значение уберёт номер; у пользователя с ролью преподавателя номер обязателен. Максимум 32 символа.
          </p>
          <button
            class="admin-users-action"
            type="submit"
            :disabled="saving || profileLoading"
            :aria-busy="saving"
          >
            {{ saving ? 'Сохраняем…' : 'Сохранить данные' }}
          </button>
          <button
            class="admin-users-action admin-users-action--secondary"
            type="button"
            :disabled="saving"
            @click="cancelProfileEdit"
          >
            Отмена
          </button>
        </form>
        <div class="admin-users-grants">
          <h3>Права ролей</h3>
          <div
            v-for="grant in selectedUser.roles"
            :key="grant.role"
            class="admin-users-grant"
            :class="{ 'admin-users-grant--active': grant.selectable }"
          >
            <div><strong>{{ roleLabel(grant.role) }}</strong><span>{{ statusLabel(grant.status) }}<template v-if="grant.groupName"> · {{ grant.groupName }}</template></span></div>
            <span
              v-if="grant.readOnly"
              class="admin-users-muted"
            >Только чтение</span>
          </div>
        </div>

        <form
          class="admin-users-editor"
          @submit.prevent="updateRole"
        >
          <h3>Изменить или добавить роль</h3>
          <label><span>Роль</span><select
            :value="editorRole"
            :disabled="saving || profileEditorVisible"
            @change="changeEditorRole(($event.target as HTMLSelectElement).value as Exclude<AdminManagedRole, 'HEADMAN'>)"
          ><option value="STUDENT">Студент</option><option value="TEACHER">Преподаватель</option><option value="ADMIN">Администратор</option></select></label>
          <p
            v-if="selectedGrant?.blockedReason"
            class="admin-users-form__hint"
          >
            {{ selectedGrant.blockedReason }}
          </p>
          <label v-if="editorCanUpdate">
            <span>Статус</span>
            <select
              v-model="editorStatus"
              :disabled="saving || profileEditorVisible"
            >
              <option
                v-for="status in editorStatuses"
                :key="status"
                :value="status"
              >{{ statusLabel(status) }}</option>
            </select>
          </label>
          <label v-if="editorRole === 'STUDENT' && editorCanUpdate"><span>ID группы</span><input
            v-model="editorGroupId"
            :disabled="saving || profileEditorVisible"
            inputmode="numeric"
            type="text"
          ></label>
          <label v-if="editorRole === 'STUDENT' && editorCanUpdate"><span>Telegram ID</span><input
            v-model="editorTelegramId"
            :disabled="saving || profileEditorVisible"
            inputmode="numeric"
            type="text"
          ></label>
          <label v-if="editorRole === 'TEACHER' && editorCanUpdate"><span>Табельный номер</span><input
            v-model="editorEmployeeNumber"
            :disabled="saving || profileEditorVisible"
            type="text"
          ></label>
          <button
            class="admin-users-action"
            type="submit"
            :disabled="saving || profileEditorVisible || !editorCanUpdate"
          >
            {{ saving ? 'Сохраняем…' : selectedGrant ? 'Сохранить роль' : 'Добавить роль' }}
          </button>
        </form>

        <form
          v-if="canTransfer"
          class="admin-users-editor admin-users-editor--transfer"
          @submit.prevent="transferStudent"
        >
          <h3>Перевести студента</h3>
          <label><span>Новая группа</span><input
            v-model="transferGroupId"
            :disabled="saving || profileEditorVisible"
            inputmode="numeric"
            required
            type="text"
          ></label>
          <label><span>Причина</span><textarea
            v-model="transferReason"
            :disabled="saving || profileEditorVisible"
            required
            rows="3"
          /></label>
          <button
            class="admin-users-action admin-users-action--secondary"
            type="submit"
            :disabled="saving || profileEditorVisible"
          >
            Перевести
          </button>
        </form>
      </aside>
    </section>
  </main>
</template>
