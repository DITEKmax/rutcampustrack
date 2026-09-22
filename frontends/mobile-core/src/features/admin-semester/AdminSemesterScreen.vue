<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  AdminSemesterApiError,
  type AdminSemester,
  type AdminSemesterClient,
} from './admin-semester-client'
import './admin-semester-screen.pcss'

const props = withDefaults(defineProps<{
  client: AdminSemesterClient
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
}>()

const semesters = ref<readonly AdminSemester[]>([])
const loading = ref(true)
const saving = ref(false)
const pendingActivationId = ref<number | null>(null)
const formVisible = ref(false)
const name = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
let disposed = false
let listRequestRevision = 0
let listAbortController: AbortController | null = null

const activeSemesters = computed(() => semesters.value.filter((semester) => semester.active))
const inactiveSemesters = computed(() => semesters.value.filter((semester) => !semester.active))
const hasActiveSemester = computed(() => activeSemesters.value.length > 0)

onMounted(() => void refresh())

async function refresh(): Promise<void> {
  const revision = ++listRequestRevision
  listAbortController?.abort()
  const controller = new AbortController()
  listAbortController = controller
  loading.value = true
  error.value = null
  try {
    const next = await props.client.listSemesters(controller.signal)
    if (!isCurrentListRequest(revision, controller)) return
    semesters.value = next
  } catch (cause) {
    if (!isCurrentListRequest(revision, controller)
      || cause instanceof StaleSessionGenerationError
      || isAbortError(cause)) return
    showError(cause, 'Список семестров не удалось загрузить.')
  } finally {
    if (isCurrentListRequest(revision, controller)) {
      loading.value = false
      listAbortController = null
    }
  }
}

function isCurrentListRequest(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRequestRevision && listAbortController === controller
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

async function createSemester(): Promise<void> {
  const payload = {
    name: name.value.trim(),
    dateFrom: dateFrom.value,
    dateTo: dateTo.value,
  }
  if (!payload.name || !payload.dateFrom || !payload.dateTo) {
    error.value = 'Укажи название, дату начала и дату окончания.'
    return
  }
  if (payload.dateTo < payload.dateFrom) {
    error.value = 'Дата окончания не может быть раньше даты начала.'
    return
  }

  saving.value = true
  error.value = null
  notice.value = null
  try {
    await props.client.createSemester(payload)
    if (disposed) return
    name.value = ''
    dateFrom.value = ''
    dateTo.value = ''
    formVisible.value = false
    notice.value = 'Семестр создан.'
    await refresh()
  } catch (cause) {
    if (!disposed && !(cause instanceof StaleSessionGenerationError)) showError(cause, 'Семестр не удалось создать.')
  } finally {
    if (!disposed) saving.value = false
  }
}

async function activateSemester(semester: AdminSemester): Promise<void> {
  if (semester.active || saving.value || pendingActivationId.value !== null) return
  saving.value = true
  pendingActivationId.value = semester.id
  error.value = null
  notice.value = null
  try {
    // The client sends exactly one PATCH; the server deactivates the previous
    // active semester in the same transaction.
    await props.client.activateSemester(semester.id)
    if (disposed) return
    notice.value = `Активирован семестр «${semester.name}».`
    await refresh()
  } catch (cause) {
    if (!disposed && !(cause instanceof StaleSessionGenerationError)) showError(cause, 'Семестр не удалось активировать.')
  } finally {
    if (!disposed) {
      saving.value = false
      pendingActivationId.value = null
    }
  }
}

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof AdminSemesterApiError
    ? cause.problem?.detail ?? cause.message
    : cause instanceof Error ? cause.message : fallback
  if (cause instanceof AdminSemesterApiError && (cause.response.status === 401 || cause.response.status === 403)) {
    emit('ownerError', cause)
  }
}

function formatDate(value: string): string {
  const date = new Date(`${value}T00:00:00`)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' }).format(date)
}

function formatPeriod(semester: AdminSemester): string {
  return `${formatDate(semester.dateFrom)} — ${formatDate(semester.dateTo)}`
}

function statusLabel(semester: AdminSemester): string {
  return semester.active ? 'Активный' : 'Неактивный'
}

onBeforeUnmount(() => {
  disposed = true
  listRequestRevision += 1
  listAbortController?.abort()
  listAbortController = null
})
</script>

<template>
  <main
    class="admin-semester-screen"
    :data-theme="theme"
    aria-labelledby="admin-semester-title"
  >
    <header class="admin-semester-screen__header">
      <p>Администрирование</p>
      <div class="admin-semester-screen__heading-row">
        <h1 id="admin-semester-title">
          Семестры
        </h1>
        <button
          class="admin-semester-screen__new"
          type="button"
          :aria-expanded="formVisible"
          @click="formVisible = !formVisible; error = null; notice = null"
        >
          {{ formVisible ? 'Скрыть форму' : '+ Новый семестр' }}
        </button>
      </div>
      <p class="admin-semester-screen__description">
        Создай учебный период и выбери один текущий контекст для системы.
      </p>
    </header>

    <form
      v-if="formVisible"
      class="admin-semester-card admin-semester-form"
      aria-labelledby="admin-semester-form-title"
      @submit.prevent="createSemester"
    >
      <h2 id="admin-semester-form-title">
        Новый семестр
      </h2>
      <label>
        <span>Название</span>
        <input
          v-model="name"
          autocomplete="off"
          maxlength="120"
          required
          type="text"
        >
      </label>
      <div class="admin-semester-form__dates">
        <label>
          <span>Начало</span>
          <input
            v-model="dateFrom"
            required
            type="date"
          >
        </label>
        <label>
          <span>Конец</span>
          <input
            v-model="dateTo"
            required
            type="date"
          >
        </label>
      </div>
      <p class="admin-semester-form__hint">
        Начало может быть в прошлом. Пересечение с другим периодом проверит сервер.
      </p>
      <button
        class="admin-semester-action"
        type="submit"
        :disabled="saving"
        :aria-busy="saving"
      >
        {{ saving ? 'Сохраняем…' : 'Создать семестр' }}
      </button>
    </form>

    <p
      v-if="loading"
      class="admin-semester-state"
      role="status"
    >
      Загружаем семестры…
    </p>
    <div
      v-else
      class="admin-semester-screen__content"
    >
      <p
        v-if="error"
        class="admin-semester-state admin-semester-state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="notice"
        class="admin-semester-state admin-semester-state--success"
        role="status"
      >
        {{ notice }}
      </p>
      <p
        v-if="semesters.length > 0 && !hasActiveSemester"
        class="admin-semester-state admin-semester-state--warning"
        role="status"
      >
        Активный семестр не выбран. Выбери период, который должен стать текущим.
      </p>
      <p
        v-if="semesters.length === 0 && !error"
        class="admin-semester-state"
        data-state="empty"
      >
        Семестров пока нет. Создай первый учебный период.
      </p>

      <section
        v-if="activeSemesters.length"
        class="admin-semester-group"
        aria-labelledby="admin-semester-active-title"
      >
        <h2 id="admin-semester-active-title">
          Текущий
        </h2>
        <article
          v-for="semester in activeSemesters"
          :key="semester.id"
          class="admin-semester-card admin-semester-card--active"
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status admin-semester-status--active">
              <span aria-hidden="true">●</span> {{ statusLabel(semester) }}
            </span>
          </div>
          <p class="admin-semester-card__period">
            <time :datetime="semester.dateFrom">{{ formatPeriod(semester) }}</time>
          </p>
        </article>
      </section>

      <section
        v-if="inactiveSemesters.length"
        class="admin-semester-group"
        aria-labelledby="admin-semester-other-title"
      >
        <h2 id="admin-semester-other-title">
          Остальные
        </h2>
        <article
          v-for="semester in inactiveSemesters"
          :key="semester.id"
          class="admin-semester-card"
        >
          <div class="admin-semester-card__heading">
            <h3>{{ semester.name }}</h3>
            <span class="admin-semester-status">
              <span aria-hidden="true">○</span> {{ statusLabel(semester) }}
            </span>
          </div>
          <p class="admin-semester-card__period">
            <time :datetime="semester.dateFrom">{{ formatPeriod(semester) }}</time>
          </p>
          <button
            class="admin-semester-action admin-semester-action--secondary"
            type="button"
            :disabled="saving"
            :aria-busy="pendingActivationId === semester.id"
            @click="activateSemester(semester)"
          >
            {{ pendingActivationId === semester.id ? 'Активируем…' : 'Сделать текущим' }}
          </button>
        </article>
      </section>
    </div>
  </main>
</template>
