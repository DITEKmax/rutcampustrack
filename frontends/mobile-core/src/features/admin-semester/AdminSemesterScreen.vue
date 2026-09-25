<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  AdminSemesterApiError,
  type AdminSemesterType,
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
const formMode = ref<'create' | 'edit'>('create')
const editingSemesterId = ref<number | null>(null)
const formLoading = ref(false)
const semesterType = ref<AdminSemesterType | null>(null)
const name = ref('')
const academicYear = ref('')
const academicYearTouched = ref(false)
const dateFrom = ref('')
const dateTo = ref('')
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
let disposed = false
let listRequestRevision = 0
let listAbortController: AbortController | null = null
let editRequestRevision = 0
let editAbortController: AbortController | null = null

const activeSemesters = computed(() => semesters.value.filter((semester) => semester.active))
const inactiveSemesters = computed(() => semesters.value.filter((semester) => !semester.active))
const hasActiveSemester = computed(() => activeSemesters.value.length > 0)
const formName = computed(() => {
  if (semesterType.value === null) return name.value
  const year = Number(academicYear.value)
  if (!Number.isSafeInteger(year) || year < 1 || year > 9998) return ''
  return generatedSemesterName(semesterType.value, year)
})

watch([semesterType, dateFrom], ([type, from]) => {
  if (academicYearTouched.value || type === null || from === '') return
  const calendarYear = Number(from.slice(0, 4))
  const defaultYear = type === 'SPRING' ? calendarYear - 1 : calendarYear
  academicYear.value = Number.isSafeInteger(defaultYear) && defaultYear >= 1 && defaultYear <= 9998
    ? String(defaultYear)
    : ''
})

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

function cancelEditLoad(): void {
  editRequestRevision += 1
  editAbortController?.abort()
  editAbortController = null
  formLoading.value = false
}

function openCreateForm(): void {
  if (saving.value) return
  cancelEditLoad()
  formMode.value = 'create'
  editingSemesterId.value = null
  semesterType.value = null
  name.value = ''
  academicYear.value = ''
  academicYearTouched.value = false
  dateFrom.value = ''
  dateTo.value = ''
  formVisible.value = true
  error.value = null
  notice.value = null
}

function closeForm(): void {
  if (saving.value) return
  cancelEditLoad()
  formVisible.value = false
  editingSemesterId.value = null
  error.value = null
  notice.value = null
}

async function openEditForm(semester: AdminSemester): Promise<void> {
  if (saving.value) return
  cancelEditLoad()
  formMode.value = 'edit'
  editingSemesterId.value = semester.id
  formVisible.value = true
  formLoading.value = true
  semesterType.value = null
  name.value = ''
  academicYear.value = ''
  academicYearTouched.value = false
  dateFrom.value = ''
  dateTo.value = ''
  error.value = null
  notice.value = null

  const revision = ++editRequestRevision
  const controller = new AbortController()
  editAbortController = controller
  try {
    const fresh = await props.client.getSemester(semester.id, controller.signal)
    if (!isCurrentEditRequest(revision, controller, semester.id)) return
    name.value = fresh.name
    dateFrom.value = fresh.dateFrom
    dateTo.value = fresh.dateTo
    // Legacy rows stay explicitly untyped until an admin chooses a type.
    semesterType.value = fresh.semesterType
    academicYear.value = fresh.academicYear === null ? '' : String(fresh.academicYear)
    // A persisted academic year is an explicit choice; changing dates must not replace it.
    academicYearTouched.value = fresh.academicYear !== null
  } catch (cause) {
    if (!isCurrentEditRequest(revision, controller, semester.id)
      || cause instanceof StaleSessionGenerationError
      || isAbortError(cause)) return
    showError(cause, 'Данные семестра не удалось загрузить.')
  } finally {
    if (isCurrentEditRequest(revision, controller, semester.id)) {
      formLoading.value = false
      editAbortController = null
    }
  }
}

function isCurrentEditRequest(revision: number, controller: AbortController, semesterId: number): boolean {
  return !disposed
    && revision === editRequestRevision
    && editAbortController === controller
    && formMode.value === 'edit'
    && editingSemesterId.value === semesterId
}

async function saveSemester(): Promise<void> {
  if (saving.value || formLoading.value) return
  const selectedType = semesterType.value
  const selectedYear = Number(academicYear.value)
  const payload = {
    name: selectedType !== null && Number.isSafeInteger(selectedYear) && selectedYear >= 1 && selectedYear <= 9998
      ? generatedSemesterName(selectedType, selectedYear)
      : '',
    dateFrom: dateFrom.value,
    dateTo: dateTo.value,
    semesterType: selectedType,
    academicYear: selectedYear,
  }
  if (!payload.dateFrom || !payload.dateTo || payload.semesterType === null || payload.name === '') {
    error.value = 'Укажи тип семестра, учебный год, дату начала и дату окончания.'
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
    const overlap = await props.client.checkOverlap(
      payload.dateFrom,
      payload.dateTo,
      editingSemesterId.value ?? undefined,
    )
    if (overlap.overlaps) {
      error.value = `Даты пересекаются с семестром «${overlap.conflictingName ?? 'без названия'}».`
      return
    }
    const wasEditing = formMode.value === 'edit'
    if (wasEditing && editingSemesterId.value !== null) {
      await props.client.updateSemester(editingSemesterId.value, payload)
    } else {
      await props.client.createSemester(payload)
    }
    if (disposed) return
    cancelEditLoad()
    editingSemesterId.value = null
    semesterType.value = null
    name.value = ''
    academicYear.value = ''
    academicYearTouched.value = false
    dateFrom.value = ''
    dateTo.value = ''
    formVisible.value = false
    formMode.value = 'create'
    notice.value = wasEditing ? 'Изменения семестра сохранены.' : 'Семестр создан.'
    await refresh()
  } catch (cause) {
    if (!disposed && !(cause instanceof StaleSessionGenerationError)) {
      showError(cause, formMode.value === 'edit' ? 'Семестр не удалось сохранить.' : 'Семестр не удалось создать.')
    }
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

function semesterTypeLabel(value: AdminSemesterType | null): string {
  if (value === 'AUTUMN') return 'Осенний семестр'
  if (value === 'SPRING') return 'Весенний семестр'
  return 'Тип не указан'
}

function generatedSemesterName(type: AdminSemesterType, academicYearValue: number): string {
  const season = type === 'AUTUMN' ? 'Осенний' : 'Весенний'
  return `${season} ${academicYearValue}/${academicYearValue + 1}`
}

function setAcademicYear(event: Event): void {
  academicYearTouched.value = true
  academicYear.value = (event.target as HTMLInputElement).value
}

onBeforeUnmount(() => {
  disposed = true
  listRequestRevision += 1
  listAbortController?.abort()
  listAbortController = null
  cancelEditLoad()
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
          :disabled="saving"
          :aria-expanded="formVisible"
          @click="formVisible ? closeForm() : openCreateForm()"
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
      @submit.prevent="saveSemester"
    >
      <h2 id="admin-semester-form-title">
        {{ formMode === 'edit' ? 'Изменить семестр' : 'Новый семестр' }}
      </h2>
      <p
        v-if="formLoading"
        class="admin-semester-form__hint"
        role="status"
      >
        Загружаем сохранённые данные…
      </p>
      <fieldset
        class="admin-semester-form__types"
        :disabled="saving || formLoading"
      >
        <legend>Тип семестра</legend>
        <label class="admin-semester-form__type-option">
          <input
            v-model="semesterType"
            name="semester-type"
            required
            type="radio"
            value="AUTUMN"
          >
          <span>Осенний</span>
        </label>
        <label class="admin-semester-form__type-option">
          <input
            v-model="semesterType"
            name="semester-type"
            required
            type="radio"
            value="SPRING"
          >
          <span>Весенний</span>
        </label>
      </fieldset>
      <label>
        <span>Учебный год</span>
        <input
          :value="academicYear"
          inputmode="numeric"
          max="9998"
          min="1"
          required
          step="1"
          type="number"
          :disabled="saving || formLoading"
          @input="setAcademicYear"
        >
      </label>
      <p class="admin-semester-form__hint">
        Название формируется автоматически: {{ formName || 'выбери тип и укажи учебный год' }}.
      </p>
      <div class="admin-semester-form__dates">
        <label>
          <span>Начало</span>
          <input
            v-model="dateFrom"
            required
            type="date"
            :disabled="saving || formLoading"
          >
        </label>
        <label>
          <span>Конец</span>
          <input
            v-model="dateTo"
            required
            type="date"
            :disabled="saving || formLoading"
          >
        </label>
      </div>
      <p class="admin-semester-form__hint">
        Начало может быть в прошлом. Пересечение проверяется с учётом этой записи; сервер проверит его ещё раз при сохранении.
      </p>
      <p
        v-if="formMode === 'edit' && semesterType === null"
        class="admin-semester-form__hint"
      >
        У старой записи тип не указан. Выбери его явно; название и сохранённые данные не переопределяются автоматически.
      </p>
      <button
        class="admin-semester-action"
        type="submit"
        :disabled="saving || formLoading"
        :aria-busy="saving"
      >
        {{ saving ? 'Сохраняем…' : formMode === 'edit' ? 'Сохранить изменения' : 'Создать семестр' }}
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
          <p class="admin-semester-card__type">
            {{ semesterTypeLabel(semester.semesterType) }}
          </p>
          <button
            class="admin-semester-action admin-semester-action--secondary"
            type="button"
            :disabled="saving"
            @click="openEditForm(semester)"
          >
            Изменить
          </button>
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
          <p class="admin-semester-card__type">
            {{ semesterTypeLabel(semester.semesterType) }}
          </p>
          <button
            class="admin-semester-action admin-semester-action--secondary"
            type="button"
            :disabled="saving"
            @click="openEditForm(semester)"
          >
            Изменить
          </button>
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
