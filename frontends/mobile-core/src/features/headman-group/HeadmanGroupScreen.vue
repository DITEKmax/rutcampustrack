<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  HeadmanGroupApiError,
  type HeadmanAssistant,
  type HeadmanAssistantPermission,
  type HeadmanGroupApi,
  type HeadmanGroupMember,
  type HeadmanPermissionOption,
} from './headman-group-client'
import './headman-group-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanGroupApi | null
  groupId: number | null
  offline?: boolean
  readOnly?: boolean
}>(), {
  offline: false,
  readOnly: false,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const members = ref<readonly HeadmanGroupMember[]>([])
const assistants = ref<readonly HeadmanAssistant[]>([])
const options = ref<readonly HeadmanPermissionOption[]>([])
const selectedStudent = ref<number | null>(null)
const selectedPermissions = ref<HeadmanAssistantPermission[]>([])
const editingPermissions = ref<Record<number, HeadmanAssistantPermission[]>>({})
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
let loadRevision = 0

const activeAssistants = computed(() => assistants.value.filter((assistant) => assistant.active))
const availableMembers = computed(() => {
  const assigned = new Set(activeAssistants.value.map((assistant) => assistant.studentId))
  return members.value.filter((member) => !assigned.has(member.id))
})

function optionLabel(code: HeadmanAssistantPermission): string {
  return options.value.find((option) => option.code === code)?.label ?? code
}

function permissionCodes(): HeadmanAssistantPermission[] {
  return options.value.map((option) => option.code)
}

function setError(cause: unknown, fallback: string): void {
  error.value = cause instanceof Error ? cause.message : fallback
  if (!(cause instanceof HeadmanGroupApiError && (cause.response.status === 401 || cause.response.status === 403))) {
    emit('error', cause)
  }
}

async function load(): Promise<void> {
  const revision = ++loadRevision
  error.value = null
  notice.value = null
  if (props.offline || !props.api || props.groupId === null) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [nextMembers, nextOptions, nextAssistants] = await Promise.all([
      props.api.listMembers(props.groupId),
      props.api.listPermissionCatalog(),
      props.api.listAssistants(props.groupId),
    ])
    if (revision !== loadRevision) return
    members.value = nextMembers
    options.value = nextOptions
    assistants.value = nextAssistants
    const firstAvailable = availableMembers.value[0]
    if (selectedStudent.value === null || !availableMembers.value.some((member) => member.id === selectedStudent.value)) {
      selectedStudent.value = firstAvailable?.id ?? null
    }
    if (selectedPermissions.value.length === 0) selectedPermissions.value = permissionCodes().slice(0, 1)
    for (const assistant of nextAssistants) {
      editingPermissions.value[assistant.id] = [...assistant.permissions]
    }
  } catch (cause) {
    if (revision !== loadRevision) return
    setError(cause, 'Не удалось загрузить группу и помощников.')
  } finally {
    if (revision === loadRevision) loading.value = false
  }
}

function togglePermission(target: HeadmanAssistantPermission, targetValue: HeadmanAssistantPermission[]): void {
  const index = targetValue.indexOf(target)
  if (index >= 0) targetValue.splice(index, 1)
  else targetValue.push(target)
}

function ensureEditingPermissions(assistant: HeadmanAssistant): HeadmanAssistantPermission[] {
  const current = editingPermissions.value[assistant.id]
  if (current) return current
  const created = [...assistant.permissions]
  editingPermissions.value[assistant.id] = created
  return created
}

async function assign(): Promise<void> {
  if (busy.value || props.offline || props.readOnly || !props.api || props.groupId === null
    || selectedStudent.value === null || selectedPermissions.value.length === 0) return
  busy.value = true
  error.value = null
  notice.value = null
  try {
    await props.api.assignAssistant(props.groupId, selectedStudent.value, selectedPermissions.value)
    await load()
    notice.value = 'Помощник назначен. Список обновлён с сервера.'
  } catch (cause) {
    setError(cause, 'Не удалось назначить помощника.')
  } finally {
    busy.value = false
  }
}

async function update(assistant: HeadmanAssistant): Promise<void> {
  if (busy.value || props.offline || props.readOnly || !props.api) return
  const permissions = editingPermissions.value[assistant.id] ?? []
  if (permissions.length === 0) {
    error.value = 'Оставь хотя бы одно право или отзови помощника.'
    return
  }
  busy.value = true
  error.value = null
  try {
    await props.api.updatePermissions(assistant.id, permissions)
    await load()
    notice.value = 'Права сохранены. Сервер подтвердил изменения.'
  } catch (cause) {
    setError(cause, 'Не удалось сохранить права.')
  } finally {
    busy.value = false
  }
}

async function revoke(assistant: HeadmanAssistant): Promise<void> {
  if (busy.value || props.offline || props.readOnly || !props.api) return
  busy.value = true
  error.value = null
  try {
    await props.api.revokeAssistant(assistant.id)
    await load()
    notice.value = 'Помощник отозван. Список обновлён с сервера.'
  } catch (cause) {
    setError(cause, 'Не удалось отозвать помощника.')
  } finally {
    busy.value = false
  }
}

watch(
  () => [props.api, props.groupId, props.offline] as const,
  () => { void load() },
  { immediate: true },
)

onBeforeUnmount(() => { loadRevision += 1 })
</script>

<template>
  <main class="headman-group" aria-labelledby="headman-group-title">
    <header class="headman-group__header">
      <div>
        <p class="headman-group__eyebrow">Староста · группа</p>
        <h1 id="headman-group-title">Помощники группы</h1>
        <p class="headman-group__hint">Назначай только действующих студентов своей группы.</p>
      </div>
    </header>

    <p v-if="offline" class="headman-group__state" role="status">Управление доступно только онлайн.</p>
    <p v-if="loading" class="headman-group__state" role="status">Загружаем студентов и права…</p>
    <p v-if="error" class="headman-group__state headman-group__state--error" role="alert">{{ error }}</p>
    <p v-if="notice" class="headman-group__state headman-group__state--success" role="status">{{ notice }}</p>

    <section v-if="!loading && options.length > 0" class="headman-group__assign" aria-labelledby="headman-group-assign-title">
      <h2 id="headman-group-assign-title">Новый помощник</h2>
      <label class="headman-group__field">
        <span>Студент</span>
        <select v-model.number="selectedStudent" :disabled="busy || offline || readOnly || availableMembers.length === 0">
          <option v-if="availableMembers.length === 0" :value="null">Нет доступных студентов</option>
          <option v-for="member in availableMembers" :key="member.id" :value="member.id">{{ member.fullName }}</option>
        </select>
      </label>
      <fieldset class="headman-group__permissions">
        <legend>Права</legend>
        <label v-for="option in options" :key="option.code" class="headman-group__check">
          <input
            type="checkbox"
            :checked="selectedPermissions.includes(option.code)"
            :disabled="busy || offline || readOnly"
            @change="togglePermission(option.code, selectedPermissions)"
          >
          <span>{{ option.label }}</span>
        </label>
      </fieldset>
      <button class="headman-group__primary" type="button" :disabled="busy || offline || readOnly || selectedStudent === null || selectedPermissions.length === 0" @click="assign">
        {{ busy ? 'Сохраняем…' : 'Назначить помощника' }}
      </button>
    </section>

    <section class="headman-group__list" aria-labelledby="headman-group-list-title">
      <h2 id="headman-group-list-title">Действующие помощники</h2>
      <p v-if="!loading && activeAssistants.length === 0" class="headman-group__state">Помощников пока нет.</p>
      <article v-for="assistant in activeAssistants" :key="assistant.id" class="headman-group__card">
        <div class="headman-group__card-header">
          <div>
            <h3>{{ assistant.studentName }}</h3>
            <p>{{ assistant.login || `Студент #${assistant.studentId}` }}</p>
          </div>
          <button class="headman-group__secondary" type="button" :disabled="busy || offline || readOnly" @click="revoke(assistant)">Отозвать</button>
        </div>
        <fieldset class="headman-group__permissions">
          <legend>Разрешённые действия</legend>
          <label v-for="option in options" :key="`${assistant.id}-${option.code}`" class="headman-group__check">
            <input
              type="checkbox"
              :checked="(editingPermissions[assistant.id] ?? []).includes(option.code)"
              :disabled="busy || offline || readOnly"
              @change="togglePermission(option.code, ensureEditingPermissions(assistant))"
            >
            <span>{{ option.label }}</span>
          </label>
        </fieldset>
        <button class="headman-group__secondary" type="button" :disabled="busy || offline || readOnly" @click="update(assistant)">Сохранить права</button>
      </article>
    </section>
  </main>
</template>
