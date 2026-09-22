<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  HeadmanSubjectsApiError,
  type HeadmanSemester,
  type HeadmanSubject,
  type HeadmanSubjectType,
  type HeadmanTeacher,
  type HeadmanSubjectsApi,
} from './headman-subjects-client'
import './headman-subjects-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanSubjectsApi | null
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

const subjectTypes: readonly HeadmanSubjectType[] = ['LECTURE', 'PRACTICE', 'LAB']
const typeLabels: Record<HeadmanSubjectType, string> = {
  LECTURE: 'Лекция',
  PRACTICE: 'Практика',
  LAB: 'Лабораторная',
}

interface PickerState {
  query: string
  results: readonly HeadmanTeacher[]
  loading: boolean
  revision: number
}

function emptyPicker(): PickerState {
  return { query: '', results: [], loading: false, revision: 0 }
}

const subjects = ref<readonly HeadmanSubject[]>([])
const semesters = ref<readonly HeadmanSemester[]>([])
const activeSemester = computed(() => semesters.value.find((semester) => semester.active) ?? null)
const teachers = reactive<Record<number, HeadmanTeacher>>({})
const pickers = reactive<Record<HeadmanSubjectType, PickerState>>({
  LECTURE: emptyPicker(),
  PRACTICE: emptyPicker(),
  LAB: emptyPicker(),
})
const selectedTeachers = reactive<Record<HeadmanSubjectType, HeadmanTeacher[]>>({
  LECTURE: [],
  PRACTICE: [],
  LAB: [],
})
const selectedTypes = ref<HeadmanSubjectType[]>([])
const formOpen = ref(false)
const subjectName = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const denied = ref(false)
const addSubjectId = ref<number | null>(null)
const addLessonType = ref<HeadmanSubjectType | null>(null)
const addPicker = reactive<PickerState>(emptyPicker())
let loadRevision = 0
let mutationRevision = 0
let disposed = false
type LoadOutcome = 'loaded' | 'stale' | 'skipped' | 'denied' | 'failed'

const formTypes = computed(() => selectedTypes.value)
const addSubject = computed(() => subjects.value.find((subject) => subject.id === addSubjectId.value) ?? null)

function labelForType(type: HeadmanSubjectType): string {
  return typeLabels[type]
}

function teacherLabel(teacherId: number): string {
  const teacher = teachers[teacherId]
  return teacher?.fullName || `Преподаватель #${teacherId}`
}

function teacherDetail(teacher: HeadmanTeacher): string {
  return teacher.employeeNumber ? `${teacher.fullName} · ${teacher.employeeNumber}` : teacher.fullName
}

function setError(cause: unknown, fallback: string): void {
  error.value = cause instanceof Error ? cause.message : fallback
  if (!(cause instanceof HeadmanSubjectsApiError && (cause.response.status === 401 || cause.response.status === 403))) {
    emit('error', cause)
  }
}

async function load(): Promise<LoadOutcome> {
  const revision = ++loadRevision
  error.value = null
  notice.value = null
  denied.value = false
  if (props.offline || !props.api || props.groupId === null) {
    loading.value = false
    return 'skipped'
  }
  loading.value = true
  try {
    const [nextSubjects, nextSemesters] = await Promise.all([
      props.api.listSubjects(props.groupId),
      props.api.listSemesters(),
    ])
    if (revision !== loadRevision || disposed) return 'stale'
    subjects.value = nextSubjects
    semesters.value = nextSemesters
    const teacherIds = [...new Set(nextSubjects.flatMap((subject) => subject.teacherIds))]
    const resolvedTeachers = await props.api.resolveTeachers(teacherIds)
    if (revision !== loadRevision || disposed) return 'stale'
    for (const teacher of resolvedTeachers) teachers[teacher.id] = teacher
    return 'loaded'
  } catch (cause) {
    if (revision !== loadRevision || disposed) return 'stale'
    if (cause instanceof HeadmanSubjectsApiError && (cause.response.status === 401 || cause.response.status === 403)) {
      denied.value = true
      return 'denied'
    } else {
      setError(cause, 'Не удалось загрузить предметы группы.')
      return 'failed'
    }
  } finally {
    if (revision === loadRevision && !disposed) loading.value = false
  }
}

function resetPicker(type: HeadmanSubjectType): void {
  pickers[type].query = ''
  pickers[type].results = []
  pickers[type].loading = false
  pickers[type].revision += 1
  selectedTeachers[type] = []
}

function openCreate(): void {
  if (props.offline || props.readOnly || !activeSemester.value) {
    error.value = activeSemester.value ? 'Создание доступно только онлайн.' : 'Нет активного семестра для создания предмета.'
    return
  }
  error.value = null
  notice.value = null
  subjectName.value = ''
  selectedTypes.value = []
  subjectTypes.forEach(resetPicker)
  formOpen.value = true
}

function closeForm(): void {
  if (busy.value) return
  formOpen.value = false
}

function toggleType(type: HeadmanSubjectType): void {
  if (selectedTypes.value.includes(type)) {
    selectedTypes.value = selectedTypes.value.filter((candidate) => candidate !== type)
  } else {
    selectedTypes.value = [...selectedTypes.value, type]
  }
}

async function searchTeachers(type: HeadmanSubjectType): Promise<void> {
  if (!props.api) return
  const picker = pickers[type]
  const revision = ++picker.revision
  picker.loading = true
  try {
    const page = await props.api.searchTeachers(picker.query, 0)
    if (revision !== picker.revision || disposed) return
    picker.results = page.items
    for (const teacher of page.items) teachers[teacher.id] = teacher
  } catch (cause) {
    if (revision !== picker.revision || disposed) return
    setError(cause, 'Не удалось найти преподавателя.')
  } finally {
    if (revision === picker.revision) picker.loading = false
  }
}

function addSelectedTeacher(type: HeadmanSubjectType, teacher: HeadmanTeacher): void {
  if (selectedTeachers[type].some((candidate) => candidate.id === teacher.id)) return
  selectedTeachers[type].push(teacher)
  teachers[teacher.id] = teacher
}

function removeSelectedTeacher(type: HeadmanSubjectType, teacherId: number): void {
  selectedTeachers[type] = selectedTeachers[type].filter((teacher) => teacher.id !== teacherId)
}

async function saveSubject(): Promise<void> {
  if (!props.api || props.offline || props.readOnly || busy.value) return
  const semester = activeSemester.value
  const name = subjectName.value.trim()
  if (!semester) {
    error.value = 'Нет активного семестра для создания предмета.'
    return
  }
  if (!name) {
    error.value = 'Укажи название предмета.'
    return
  }
  if (formTypes.value.length === 0) {
    error.value = 'Выбери хотя бы один тип занятия.'
    return
  }
  if (formTypes.value.some((type) => selectedTeachers[type].length === 0)) {
    error.value = 'Для каждого выбранного типа добавь хотя бы одного преподавателя.'
    return
  }
  const requestRevision = ++mutationRevision
  const primaryType = formTypes.value[0]!
  busy.value = true
  error.value = null
  notice.value = null
  try {
    await props.api.createSubject({
      name,
      type: primaryType,
      lessonTypes: [...formTypes.value],
      initialAssignments: formTypes.value.flatMap((lessonType) => selectedTeachers[lessonType].map((teacher) => ({
        teacherId: teacher.id,
        semesterId: semester.id,
        lessonType,
        validFrom: semester.dateFrom,
        validUntilExclusive: null,
      }))),
    })
    if (requestRevision !== mutationRevision || disposed) return
    const reconciliation = await load()
    if (requestRevision !== mutationRevision || disposed) return
    if (reconciliation !== 'loaded') return
    formOpen.value = false
    notice.value = 'Предмет сохранён.'
  } catch (cause) {
    if (requestRevision !== mutationRevision || disposed) return
    setError(cause, 'Не удалось сохранить предмет.')
  } finally {
    if (requestRevision === mutationRevision && !disposed) busy.value = false
  }
}

function openAddTeacher(subjectId: number, type: HeadmanSubjectType): void {
  if (props.offline || props.readOnly || !activeSemester.value) return
  addSubjectId.value = subjectId
  addLessonType.value = type
  addPicker.query = ''
  addPicker.results = []
  addPicker.loading = false
  addPicker.revision += 1
  error.value = null
}

function closeAddTeacher(): void {
  if (busy.value) return
  addSubjectId.value = null
  addLessonType.value = null
}

async function searchAddTeachers(): Promise<void> {
  if (!props.api) return
  const revision = ++addPicker.revision
  addPicker.loading = true
  try {
    const page = await props.api.searchTeachers(addPicker.query, 0)
    if (revision !== addPicker.revision || disposed) return
    addPicker.results = page.items
    for (const teacher of page.items) teachers[teacher.id] = teacher
  } catch (cause) {
    if (revision !== addPicker.revision || disposed) return
    setError(cause, 'Не удалось найти преподавателя.')
  } finally {
    if (revision === addPicker.revision) addPicker.loading = false
  }
}

async function addTeacher(teacher: HeadmanTeacher): Promise<void> {
  if (!props.api || !activeSemester.value || !addSubject.value || !addLessonType.value || busy.value) return
  if (addSubject.value.assignments.some((assignment) => assignment.lessonType === addLessonType.value && assignment.teacherId === teacher.id && assignment.semesterId === activeSemester.value?.id)) {
    error.value = 'Этот преподаватель уже назначен на выбранный тип.'
    return
  }
  const requestRevision = ++mutationRevision
  busy.value = true
  error.value = null
  notice.value = null
  try {
    await props.api.addTeacher(addSubject.value.id, teacher.id, {
      semesterId: activeSemester.value.id,
      lessonType: addLessonType.value,
      validFrom: activeSemester.value.dateFrom,
      validUntilExclusive: null,
    })
    if (requestRevision !== mutationRevision || disposed) return
    const reconciliation = await load()
    if (requestRevision !== mutationRevision || disposed) return
    if (reconciliation !== 'loaded') return
    addSubjectId.value = null
    addLessonType.value = null
    notice.value = 'Преподаватель добавлен.'
  } catch (cause) {
    if (requestRevision !== mutationRevision || disposed) return
    setError(cause, 'Не удалось добавить преподавателя.')
  } finally {
    if (requestRevision === mutationRevision && !disposed) busy.value = false
  }
}

function assignmentsFor(subject: HeadmanSubject, type: HeadmanSubjectType) {
  return subject.assignments.filter((assignment) => assignment.lessonType === type)
}

watch(
  () => [props.api, props.groupId, props.offline] as const,
  () => { void load() },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  loadRevision += 1
  mutationRevision += 1
})
</script>

<template>
  <main class="headman-subjects" aria-labelledby="headman-subjects-title">
    <header class="headman-subjects__header">
      <div>
        <p class="headman-subjects__eyebrow">Староста · управление</p>
        <h1 id="headman-subjects-title">Предметы группы</h1>
        <p v-if="activeSemester" class="headman-subjects__context">Активный семестр: {{ activeSemester.name }}</p>
      </div>
      <button class="headman-subjects__primary" type="button" :disabled="offline || readOnly || loading || !activeSemester" @click="openCreate">
        Добавить предмет
      </button>
    </header>

    <p v-if="offline" class="headman-subjects__state" role="status">Изменения доступны только онлайн.</p>
    <p v-if="denied" class="headman-subjects__state" role="alert">У этой сессии нет права старосты для группы.</p>
    <p v-if="error" class="headman-subjects__state headman-subjects__state--error" role="alert">{{ error }}</p>
    <p v-if="notice" class="headman-subjects__state headman-subjects__state--success" role="status">{{ notice }}</p>
    <p v-if="loading" class="headman-subjects__state" aria-live="polite">Загружаем предметы…</p>

    <section v-else class="headman-subjects__list" aria-labelledby="headman-subjects-list-title">
      <h2 id="headman-subjects-list-title">Предметы</h2>
      <p v-if="subjects.length === 0" class="headman-subjects__state">Предметов пока нет. Добавь первый предмет группы.</p>
      <article v-for="subject in subjects" :key="subject.id" class="headman-subjects__card">
        <div class="headman-subjects__card-header">
          <div>
            <h3>{{ subject.name }}</h3>
            <p>{{ subject.lessonTypes.map(labelForType).join(' · ') }}</p>
          </div>
          <span class="headman-subjects__id">#{{ subject.id }}</span>
        </div>
        <fieldset v-for="type in subject.lessonTypes" :key="`${subject.id}-${type}`" class="headman-subjects__type">
          <legend>{{ labelForType(type) }}</legend>
          <ul v-if="assignmentsFor(subject, type).length > 0" class="headman-subjects__teachers">
            <li v-for="assignment in assignmentsFor(subject, type)" :key="assignment.id">
              {{ teacherLabel(assignment.teacherId) }}
            </li>
          </ul>
          <p v-else class="headman-subjects__hint">Преподаватель ещё не назначен.</p>
          <button class="headman-subjects__secondary" type="button" :disabled="offline || readOnly || !activeSemester" @click="openAddTeacher(subject.id, type)">
            Добавить преподавателя
          </button>
        </fieldset>
      </article>
    </section>

    <section v-if="formOpen" class="headman-subjects__form" aria-labelledby="headman-subject-form-title">
      <div class="headman-subjects__form-header">
        <h2 id="headman-subject-form-title">Новый предмет</h2>
        <button class="headman-subjects__secondary" type="button" :disabled="busy" @click="closeForm">Отмена</button>
      </div>
      <label class="headman-subjects__field">
        <span>Название предмета</span>
        <input v-model="subjectName" type="text" maxlength="255" autocomplete="off" :disabled="busy || offline || readOnly">
      </label>
      <fieldset class="headman-subjects__type-picker">
        <legend>Типы занятий</legend>
        <button
          v-for="type in subjectTypes"
          :key="type"
          class="headman-subjects__type-toggle"
          :data-selected="selectedTypes.includes(type)"
          type="button"
          :aria-pressed="selectedTypes.includes(type)"
          :disabled="busy || offline || readOnly"
          @click="toggleType(type)"
        >
          {{ labelForType(type) }}
        </button>
      </fieldset>
      <fieldset v-for="type in formTypes" :key="type" class="headman-subjects__picker">
        <legend>Преподаватели · {{ labelForType(type) }}</legend>
        <div class="headman-subjects__search-row">
          <input v-model="pickers[type].query" type="search" placeholder="ФИО или табельный номер" :disabled="busy || offline || readOnly" @keyup.enter="searchTeachers(type)">
          <button class="headman-subjects__secondary" type="button" :disabled="busy || offline || readOnly || pickers[type].loading" @click="searchTeachers(type)">
            {{ pickers[type].loading ? 'Ищем…' : 'Найти' }}
          </button>
        </div>
        <ul v-if="pickers[type].results.length > 0" class="headman-subjects__results">
          <li v-for="teacher in pickers[type].results" :key="teacher.id">
            <button type="button" :disabled="busy || offline || readOnly" @click="addSelectedTeacher(type, teacher)">{{ teacherDetail(teacher) }}</button>
          </li>
        </ul>
        <ul v-if="selectedTeachers[type].length > 0" class="headman-subjects__teachers">
          <li v-for="teacher in selectedTeachers[type]" :key="teacher.id">
            {{ teacherDetail(teacher) }}
            <button class="headman-subjects__remove" type="button" :aria-label="`Удалить: ${teacher.fullName}`" :disabled="busy || offline || readOnly" @click="removeSelectedTeacher(type, teacher.id)">Удалить</button>
          </li>
        </ul>
      </fieldset>
      <button class="headman-subjects__primary" type="button" :disabled="busy || offline || readOnly" @click="saveSubject">
        {{ busy ? 'Сохраняем…' : 'Сохранить предмет' }}
      </button>
    </section>

    <section v-if="addSubject && addLessonType" class="headman-subjects__form" aria-labelledby="headman-add-teacher-title">
      <div class="headman-subjects__form-header">
        <h2 id="headman-add-teacher-title">Добавить преподавателя · {{ addSubject.name }} · {{ labelForType(addLessonType) }}</h2>
        <button class="headman-subjects__secondary" type="button" :disabled="busy" @click="closeAddTeacher">Отмена</button>
      </div>
      <div class="headman-subjects__search-row">
        <input v-model="addPicker.query" type="search" placeholder="ФИО или табельный номер" :disabled="busy || offline || readOnly" @keyup.enter="searchAddTeachers">
        <button class="headman-subjects__secondary" type="button" :disabled="busy || offline || readOnly || addPicker.loading" @click="searchAddTeachers">
          {{ addPicker.loading ? 'Ищем…' : 'Найти' }}
        </button>
      </div>
      <ul v-if="addPicker.results.length > 0" class="headman-subjects__results">
        <li v-for="teacher in addPicker.results" :key="teacher.id">
          <button type="button" :disabled="busy || offline || readOnly" @click="addTeacher(teacher)">{{ teacherDetail(teacher) }}</button>
        </li>
      </ul>
    </section>
  </main>
</template>
