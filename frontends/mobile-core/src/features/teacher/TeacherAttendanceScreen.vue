<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherJournalQuery,
  type TeacherStatsSubjectOption,
} from './teacher-client'
import './teacher-attendance-screen.pcss'

const props = defineProps<{
  api: TeacherApi | null
  semesterId: number | null
}>()

const emit = defineEmits<{
  apply: [query: TeacherJournalQuery]
  error: [cause: unknown]
}>()

const semester = ref<{ id: number; dateFrom: string; dateTo: string } | null>(null)
const groups = ref<readonly { id: number; name: string }[]>([])
const subjectOptions = ref<readonly TeacherStatsSubjectOption[]>([])
const selectedGroupId = ref<number | null>(null)
const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const dateFrom = ref('')
const dateTo = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const formError = ref<string | null>(null)
let revision = 0
let disposed = false

const subjects = computed(() => {
  const merged = new Map<number, TeacherStatsSubjectOption>()
  for (const option of subjectOptions.value) {
    if (option.groupId !== selectedGroupId.value) continue
    const existing = merged.get(option.subjectId)
    if (existing) {
      merged.set(option.subjectId, {
        ...existing,
        lessonTypes: [...new Set([...existing.lessonTypes, ...option.lessonTypes])],
      })
    } else {
      merged.set(option.subjectId, option)
    }
  }
  return [...merged.values()].sort((left, right) => left.subjectName.localeCompare(right.subjectName, 'ru'))
})
const types = computed(() => [...new Set(
  subjects.value.find((option) => option.subjectId === selectedSubjectId.value)?.lessonTypes ?? [],
)].sort((left, right) => left.localeCompare(right, 'ru')))
const canApply = computed(() => Boolean(
  props.api && semester.value && selectedGroupId.value && selectedSubjectId.value
  && selectedTypes.value.length > 0 && selectedTypes.value.length <= 3
  && dateFrom.value && dateTo.value && !loading.value,
))

watch(
  () => [props.api, props.semesterId] as const,
  () => {
    revision += 1
    semester.value = null
    groups.value = []
    subjectOptions.value = []
    selectedGroupId.value = null
    selectedSubjectId.value = null
    selectedTypes.value = []
    dateFrom.value = ''
    dateTo.value = ''
    formError.value = null
    void loadContext()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  revision += 1
})

async function loadContext(): Promise<void> {
  const api = props.api
  const semesterId = props.semesterId
  const current = ++revision
  error.value = null
  if (!api || !semesterId) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const activeSemester = await api.semester()
    if (!isCurrent(current)) return
    if (activeSemester.id !== semesterId) throw new Error('Семестр преподавателя изменился. Обнови экран.')
    const groupStats = await api.stats({
      semesterId,
      scope: 'groups',
      groupId: null,
      subjectId: null,
      lessonTypes: [],
      sorts: [],
      filters: [],
    })
    if (!isCurrent(current)) return
    const allowedGroups = groupStats.groups
      .filter((group) => Number.isSafeInteger(group.groupId) && group.groupId > 0)
      .map((group) => ({ id: group.groupId, name: group.groupName }))
      .sort((left, right) => left.name.localeCompare(right.name, 'ru'))
    semester.value = activeSemester
    groups.value = allowedGroups
    subjectOptions.value = groupStats.subjectOptions
    dateFrom.value = activeSemester.dateFrom
    dateTo.value = activeSemester.dateTo
    const firstGroupId = allowedGroups[0]?.id ?? null
    selectedGroupId.value = firstGroupId
    const firstSubject = subjectOptions.value.find((option) => option.groupId === firstGroupId)
    selectedSubjectId.value = firstSubject?.subjectId ?? null
    resetTypesForSubject(firstSubject?.subjectId ?? null)
  } catch (cause) {
    if (!isCurrent(current) || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось получить группы и предметы.'
    emit('error', cause)
  } finally {
    if (isCurrent(current)) loading.value = false
  }
}

function isCurrent(current: number): boolean {
  return !disposed && current === revision && props.api !== null && props.semesterId !== null
}

function changeGroup(): void {
  const firstSubject = subjects.value[0]
  selectedSubjectId.value = firstSubject?.subjectId ?? null
  resetTypesForSubject(firstSubject?.subjectId ?? null)
  formError.value = null
}

function changeSubject(): void {
  resetTypesForSubject(selectedSubjectId.value)
  formError.value = null
}

function resetTypesForSubject(subjectId: number | null): void {
  const option = subjects.value.find((item) => item.subjectId === subjectId)
  selectedTypes.value = [...new Set(option?.lessonTypes ?? [])].slice(0, 3)
}

function toggleType(lessonType: string, checked: boolean): void {
  formError.value = null
  if (checked) {
    if (selectedTypes.value.length >= 3) {
      formError.value = 'Можно выбрать не больше трёх типов занятий.'
      return
    }
    selectedTypes.value = [...new Set([...selectedTypes.value, lessonType])]
  } else {
    selectedTypes.value = selectedTypes.value.filter((value) => value !== lessonType)
  }
}

function apply(): void {
  formError.value = null
  const activeSemester = semester.value
  const groupId = selectedGroupId.value
  const subjectId = selectedSubjectId.value
  const api = props.api
  if (!api || !activeSemester || !props.semesterId || !groupId || !subjectId) return
  if (selectedTypes.value.length < 1 || selectedTypes.value.length > 3) {
    formError.value = 'Выбери от одного до трёх типов занятий.'
    return
  }
  if (!isIsoDate(dateFrom.value) || !isIsoDate(dateTo.value)) {
    formError.value = 'Укажи обе даты в формате ГГГГ-ММ-ДД.'
    return
  }
  if (dateFrom.value > dateTo.value) {
    formError.value = 'Начало периода не может быть позже окончания.'
    return
  }
  if (dateFrom.value < activeSemester.dateFrom || dateTo.value > activeSemester.dateTo) {
    formError.value = 'Период должен находиться внутри семестра.'
    return
  }
  emit('apply', {
    semesterId: props.semesterId,
    groupId,
    subjectId,
    lessonTypes: [...selectedTypes.value],
    dateFrom: dateFrom.value,
    dateTo: dateTo.value,
    page: 0,
    pageSize: 100,
  })
}

function isIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}
</script>

<template>
  <main
    class="teacher-screen teacher-attendance"
    aria-labelledby="teacher-attendance-title"
    :aria-busy="loading"
  >
    <header class="teacher-screen__header">
      <div>
        <p class="teacher-screen__muted">
          Группы и прошлые занятия
        </p>
        <h1 id="teacher-attendance-title">
          Посещаемость
        </h1>
      </div>
    </header>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем доступные группы и предметы…
    </p>
    <p
      v-else-if="error"
      class="teacher-screen__state teacher-screen__state--error"
      role="alert"
    >
      {{ error }}
      <button
        class="teacher-screen__secondary"
        type="button"
        @click="loadContext"
      >
        Повторить
      </button>
    </p>
    <p
      v-else-if="groups.length === 0"
      class="teacher-screen__state"
      role="status"
    >
      Для выбранного семестра не найдено групп с занятиями.
    </p>
    <form
      v-else
      class="teacher-attendance__filters"
      @submit.prevent="apply"
    >
      <label class="teacher-attendance__field">
        <span>Группа</span>
        <select
          v-model.number="selectedGroupId"
          class="teacher-screen__date"
          @change="changeGroup"
        >
          <option
            v-for="group in groups"
            :key="group.id"
            :value="group.id"
          >{{ group.name }}</option>
        </select>
      </label>
      <label class="teacher-attendance__field">
        <span>Предмет</span>
        <select
          v-model.number="selectedSubjectId"
          class="teacher-screen__date"
          :disabled="subjects.length === 0"
          @change="changeSubject"
        >
          <option
            v-for="subject in subjects"
            :key="subject.subjectId"
            :value="subject.subjectId"
          >
            {{ subject.subjectName }}
          </option>
        </select>
      </label>
      <fieldset
        class="teacher-attendance__types"
        :disabled="types.length === 0"
      >
        <legend>Типы занятий</legend>
        <label
          v-for="lessonType in types"
          :key="lessonType"
          class="teacher-attendance__type"
        >
          <input
            type="checkbox"
            :checked="selectedTypes.includes(lessonType)"
            :disabled="!selectedTypes.includes(lessonType) && selectedTypes.length >= 3"
            @change="toggleType(lessonType, ($event.target as HTMLInputElement).checked)"
          >
          <span>{{ lessonType }}</span>
        </label>
      </fieldset>
      <div class="teacher-attendance__dates">
        <label class="teacher-attendance__field">
          <span>С</span>
          <input
            v-model="dateFrom"
            class="teacher-screen__date"
            type="date"
            :min="semester?.dateFrom"
            :max="semester?.dateTo"
          >
        </label>
        <label class="teacher-attendance__field">
          <span>По</span>
          <input
            v-model="dateTo"
            class="teacher-screen__date"
            type="date"
            :min="semester?.dateFrom"
            :max="semester?.dateTo"
          >
        </label>
      </div>
      <p
        v-if="semester"
        class="teacher-screen__muted teacher-attendance__semester"
      >
        По умолчанию выбран весь семестр: {{ semester.dateFrom }} — {{ semester.dateTo }}.
      </p>
      <p
        v-if="formError"
        class="teacher-screen__state teacher-screen__state--error"
        role="alert"
      >
        {{ formError }}
      </p>
      <button
        class="teacher-screen__primary"
        type="submit"
        :disabled="!canApply"
      >
        Показать занятия
      </button>
    </form>
  </main>
</template>
