<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  HeadmanGroupApiError,
  type HeadmanAssistant,
  type HeadmanAssistantPermission,
  type HeadmanGroupApi,
  type HeadmanGroupMember,
  type HeadmanPermissionOption,
  type HeadmanRosterFormat,
} from './headman-group-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadFormat, ReportDownloadPort } from '../../shared/report-download-client'
import './headman-group-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanGroupApi | null
  groupId: number | null
  offline?: boolean
  readOnly?: boolean
  assistantPermissions?: readonly HeadmanAssistantPermission[] | null
  reportDownload?: ReportDownloadPort | null
}>(), {
  offline: false,
  readOnly: false,
  assistantPermissions: null,
  reportDownload: null,
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
const rosterFormats = ref<readonly HeadmanRosterFormat[]>([])
const rosterFormat = ref<ReportDownloadFormat | null>(null)
const rosterLoading = ref(false)
const rosterDownloading = ref(false)
const rosterError = ref<string | null>(null)
const rosterNotice = ref<string | null>(null)
let rosterRevision = 0
let downloadRevision = 0
let rosterController: AbortController | null = null
const isHeadman = computed(() => props.assistantPermissions === null)
const activeRosterFormat = computed(() => rosterFormats.value.find((format) => format.code === rosterFormat.value) ?? null)

function invalidateDownload(): void {
  downloadRevision += 1
  rosterController?.abort()
  rosterController = null
  rosterDownloading.value = false
  rosterError.value = null
  rosterNotice.value = null
}

async function loadRosterFormats(): Promise<void> {
  const revision = ++rosterRevision
  rosterFormats.value = []
  rosterFormat.value = null
  const api = props.api
  if (!api || props.groupId === null || props.offline || !isHeadman.value) {
    rosterLoading.value = false
    return
  }
  rosterLoading.value = true
  try {
    const formats = await api.listRosterFormats()
    if (revision !== rosterRevision) return
    rosterFormats.value = formats
    rosterFormat.value = formats[0]?.code ?? null
  } catch (cause) {
    if (revision !== rosterRevision || cause instanceof StaleSessionGenerationError) return
    rosterError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить форматы состава группы.'
    emit('error', cause)
  } finally {
    if (revision === rosterRevision) rosterLoading.value = false
  }
}

async function downloadRoster(): Promise<void> {
  const api = props.api
  const format = activeRosterFormat.value
  const groupId = props.groupId
  const reportDownload = props.reportDownload
  if (!api || !format || groupId === null || props.offline || !isHeadman.value || rosterDownloading.value) return
  const revision = ++downloadRevision
  const isCurrent = (): boolean => revision === downloadRevision && api === props.api
    && groupId === props.groupId && reportDownload === props.reportDownload && !props.offline
    && isHeadman.value && format.code === rosterFormat.value
  rosterDownloading.value = true
  rosterError.value = null
  rosterNotice.value = null
  try {
    if (reportDownload) {
      const result = await reportDownload.download({
        kind: 'HEADMAN_GROUP_COMPOSITION', headmanGroupComposition: { format: format.code },
      }, isCurrent)
      if (result === 'stale' || !isCurrent()) return
      if (result === 'unsupported') {
        rosterError.value = 'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.'
      } else {
        rosterNotice.value = result === 'accepted'
          ? 'Telegram принял запрос на скачивание; проверь завершение в Telegram.' : 'Скачивание отменено.'
      }
      return
    }
    rosterController = new AbortController()
    const downloaded = await api.downloadRoster(format, rosterController.signal)
    if (!isCurrent()) return
    const url = URL.createObjectURL(downloaded.blob)
    const anchor = document.createElement('a')
    try {
      anchor.href = url
      anchor.download = downloaded.filename
      anchor.rel = 'noopener'
      document.body.append(anchor)
      anchor.click()
      rosterNotice.value = 'Файл передан браузеру для скачивания.'
    } finally {
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 0)
    }
  } catch (cause) {
    if (!isCurrent() || cause instanceof StaleSessionGenerationError) return
    rosterError.value = cause instanceof Error ? cause.message : 'Не удалось скачать состав группы.'
    emit('error', cause)
  } finally {
    if (revision === downloadRevision) {
      rosterDownloading.value = false
      rosterController = null
    }
  }
}

const activeAssistants = computed(() => assistants.value.filter((assistant) => assistant.active))
const availableMembers = computed(() => {
  const assigned = new Set(activeAssistants.value.map((assistant) => assistant.studentId))
  return members.value.filter((member) => !assigned.has(member.id))
})

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

watch(() => [props.api, props.groupId, props.offline, props.assistantPermissions] as const, () => {
  invalidateDownload()
  void loadRosterFormats()
}, { immediate: true, flush: 'sync' })
watch(() => [props.reportDownload, rosterFormat.value] as const, invalidateDownload, { flush: 'sync' })

onBeforeUnmount(() => {
  loadRevision += 1
  rosterRevision += 1
  invalidateDownload()
})
</script>

<template>
  <main
    class="headman-group"
    aria-labelledby="headman-group-title"
  >
    <header class="headman-group__header">
      <div>
        <p class="headman-group__eyebrow">
          Староста · группа
        </p>
        <h1 id="headman-group-title">
          Помощники группы
        </h1>
        <p class="headman-group__hint">
          Назначай только действующих студентов своей группы.
        </p>
      </div>
    </header>

    <p
      v-if="offline"
      class="headman-group__state"
      role="status"
    >
      Управление доступно только онлайн.
    </p>
    <p
      v-if="loading"
      class="headman-group__state"
      role="status"
    >
      Загружаем студентов и права…
    </p>
    <p
      v-if="error"
      class="headman-group__state headman-group__state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      class="headman-group__state headman-group__state--success"
      role="status"
    >
      {{ notice }}
    </p>

    <section
      v-if="isHeadman"
      class="headman-group__export"
      aria-labelledby="headman-group-export-title"
      :aria-busy="rosterDownloading"
    >
      <h2 id="headman-group-export-title">
        Состав группы
      </h2>
      <p class="headman-group__hint">
        Текущий состав: номер по алфавиту, ФИО, логин и роль. В файле — группа и дата выгрузки.
      </p>
      <p
        v-if="rosterLoading"
        class="headman-group__state"
        role="status"
      >
        Загружаем форматы…
      </p>
      <label class="headman-group__field">
        <span>Формат файла</span>
        <select
          v-model="rosterFormat"
          :disabled="rosterLoading || rosterDownloading || offline || rosterFormats.length === 0"
        >
          <option
            v-if="rosterFormats.length === 0"
            :value="null"
          >Нет доступных форматов</option>
          <option
            v-for="format in rosterFormats"
            :key="format.code"
            :value="format.code"
          >{{ format.label }}</option>
        </select>
      </label>
      <button
        class="headman-group__secondary"
        type="button"
        :disabled="rosterLoading || rosterDownloading || offline || !api || groupId === null || !activeRosterFormat"
        @click="downloadRoster"
      >
        {{ rosterDownloading ? 'Готовим файл…' : 'Скачать состав группы' }}
      </button>
      <p
        v-if="rosterError"
        class="headman-group__state headman-group__state--error"
        role="alert"
      >
        {{ rosterError }}
      </p>
      <p
        v-if="rosterNotice"
        class="headman-group__state headman-group__state--success"
        role="status"
      >
        {{ rosterNotice }}
      </p>
    </section>

    <section
      v-if="!loading && options.length > 0"
      class="headman-group__assign"
      aria-labelledby="headman-group-assign-title"
    >
      <h2 id="headman-group-assign-title">
        Новый помощник
      </h2>
      <label class="headman-group__field">
        <span>Студент</span>
        <select
          v-model.number="selectedStudent"
          :disabled="busy || offline || readOnly || availableMembers.length === 0"
        >
          <option
            v-if="availableMembers.length === 0"
            :value="null"
          >Нет доступных студентов</option>
          <option
            v-for="member in availableMembers"
            :key="member.id"
            :value="member.id"
          >{{ member.fullName }}</option>
        </select>
      </label>
      <fieldset class="headman-group__permissions">
        <legend>Права</legend>
        <label
          v-for="option in options"
          :key="option.code"
          class="headman-group__check"
        >
          <input
            type="checkbox"
            :checked="selectedPermissions.includes(option.code)"
            :disabled="busy || offline || readOnly"
            @change="togglePermission(option.code, selectedPermissions)"
          >
          <span>{{ option.label }}</span>
        </label>
      </fieldset>
      <button
        class="headman-group__primary"
        type="button"
        :disabled="busy || offline || readOnly || selectedStudent === null || selectedPermissions.length === 0"
        @click="assign"
      >
        {{ busy ? 'Сохраняем…' : 'Назначить помощника' }}
      </button>
    </section>

    <section
      class="headman-group__list"
      aria-labelledby="headman-group-list-title"
    >
      <h2 id="headman-group-list-title">
        Действующие помощники
      </h2>
      <p
        v-if="!loading && activeAssistants.length === 0"
        class="headman-group__state"
      >
        Помощников пока нет.
      </p>
      <article
        v-for="assistant in activeAssistants"
        :key="assistant.id"
        class="headman-group__card"
      >
        <div class="headman-group__card-header">
          <div>
            <h3>{{ assistant.studentName }}</h3>
            <p>{{ assistant.login || `Студент #${assistant.studentId}` }}</p>
          </div>
          <button
            class="headman-group__secondary"
            type="button"
            :disabled="busy || offline || readOnly"
            @click="revoke(assistant)"
          >
            Отозвать
          </button>
        </div>
        <fieldset class="headman-group__permissions">
          <legend>Разрешённые действия</legend>
          <label
            v-for="option in options"
            :key="`${assistant.id}-${option.code}`"
            class="headman-group__check"
          >
            <input
              type="checkbox"
              :checked="(editingPermissions[assistant.id] ?? []).includes(option.code)"
              :disabled="busy || offline || readOnly"
              @change="togglePermission(option.code, ensureEditingPermissions(assistant))"
            >
            <span>{{ option.label }}</span>
          </label>
        </fieldset>
        <button
          class="headman-group__secondary"
          type="button"
          :disabled="busy || offline || readOnly"
          @click="update(assistant)"
        >
          Сохранить права
        </button>
      </article>
    </section>
  </main>
</template>
