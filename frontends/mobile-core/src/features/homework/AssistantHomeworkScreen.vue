<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { moscowDate } from '../../domain/homework'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { HeadmanJournalApi, HeadmanJournalLesson } from '../headman-journal/headman-journal-client'
import {
  HeadmanHomeworkApiError,
  type HeadmanHomeworkApi,
  type HeadmanHomeworkSemester,
  type HeadmanManagedHomework,
  type HeadmanHomeworkSubject,
  type HeadmanHomeworkHistory,
  type HomeworkBindingMode,
} from './headman-homework-client'
import {
  intentAfterHomeworkCreateFailure,
  reuseOrCreateAssistantHomeworkIntent,
  withAssistantHomeworkPublicationReceipt,
  reuseOrCreateAssistantHomeworkEditIntent,
  sameAssistantHomeworkDraftContext,
  type AssistantHomeworkCreateIntent,
  type AssistantHomeworkDraftContext,
  type AssistantHomeworkEditIntent,
} from './assistant-homework-create-intent'
import './assistant-homework-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanHomeworkApi | null
  journalApi: HeadmanJournalApi | null
  groupId: number | null
  userId: number | null
  offline?: boolean
  readOnly?: boolean
}>(), {
  offline: false,
  readOnly: false,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const todayIso = (): string => {
  const value = moscowDate(new Date().toISOString())
  if (value === null) throw new Error('Не удалось определить дату домашних заданий.')
  return value
}

const selectedDate = ref(todayIso())
const semester = ref<HeadmanHomeworkSemester | null>(null)
const lessons = ref<readonly HeadmanJournalLesson[]>([])
const homeworks = ref<readonly HeadmanManagedHomework[]>([])
const selectedLessonId = ref<number | null>(null)
const bindingMode = ref<HomeworkBindingMode>('LESSON')
const subjects = ref<readonly HeadmanHomeworkSubject[]>([])
const selectedSubjectId = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const formId = ref<number | 'new' | null>(null)
const title = ref('')
const description = ref('')
const link = ref('')
const draftContext = shallowRef<AssistantHomeworkDraftContext | null>(null)
const createIntent = shallowRef<AssistantHomeworkCreateIntent | null>(null)
const createConflict = ref(false)
const createPending = ref(false)
const editIntent = shallowRef<AssistantHomeworkEditIntent | null>(null)
const editRevision = ref<number | null>(null)
const editConflict = ref(false)
const historyId = ref<number | null>(null)
const historyEntries = ref<readonly HeadmanHomeworkHistory[]>([])
const historyLoading = ref(false)
let historyRevision = 0
const mutationBusy = ref(false)
const mutationId = ref<number | null>(null)
let draftApi: HeadmanHomeworkApi | null = null
let loadRevision = 0
let mutationRevision = 0
let disposed = false

const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const selectedHomeworks = computed(() => {
  const lesson = selectedLesson.value
  return homeworks.value
    .filter((item) => bindingMode.value === 'DATE'
      ? item.bindingMode === 'DATE' && item.lessonDate === selectedDate.value
      : lesson !== null && item.bindingMode === 'LESSON' && item.lessonDate === lesson?.date && item.lessonNumber === lesson?.lessonNumber)
    .slice()
    .sort((left, right) => left.id - right.id)
})
const canCreate = computed(() => Boolean(
  !props.offline && !props.readOnly && semester.value
    && (bindingMode.value === 'DATE' ? selectedSubjectId.value !== null
      : selectedLesson.value?.current && selectedLesson.value.subjectId && selectedLesson.value.lessonNumber
        && selectedLesson.value.status !== 'CANCELLED' && selectedLesson.value.status !== 'TRANSFERRED'),
))
const retryLocked = computed(() => (createIntent.value !== null || editIntent.value !== null) && !mutationBusy.value)
const uncertainCreate = computed(() => retryLocked.value && !createConflict.value && !editConflict.value && !createPending.value)

function canEdit(item: HeadmanManagedHomework): boolean {
  return props.userId !== null && !props.offline && !props.readOnly && !item.archived && item.revision !== null
}

function subjectLabel(item: HeadmanManagedHomework): string {
  return subjects.value.find((subject) => subject.id === item.subjectId)?.name ?? 'Предмет недоступен'
}

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', weekday: 'long' }).format(date)
}

function shiftDate(delta: number): void {
  const date = new Date(`${selectedDate.value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + delta)
  selectedDate.value = date.toISOString().slice(0, 10)
}

function lessonLabel(lesson: HeadmanJournalLesson): string {
  const time = `${lesson.startTime?.slice(0, 5) ?? '—'}–${lesson.endTime?.slice(0, 5) ?? '—'}`
  return `Пара ${lesson.lessonNumber ?? '—'} · ${time}`
}

function currentDraftContext(): AssistantHomeworkDraftContext | null {
  const lesson = selectedLesson.value
  if (props.userId === null || props.groupId === null || semester.value === null) return null
  if (bindingMode.value === 'DATE') {
    if (selectedSubjectId.value === null) return null
    return { userId: props.userId, groupId: props.groupId, semesterId: semester.value.id,
      selectedDate: selectedDate.value, lessonId: null, lessonDate: selectedDate.value,
      subjectId: selectedSubjectId.value, lessonNumber: null, bindingMode: 'DATE' }
  }
  if (!lesson || lesson.subjectId === null || lesson.lessonNumber === null) return null
  return {
    userId: props.userId,
    groupId: props.groupId,
    semesterId: semester.value.id,
    selectedDate: selectedDate.value,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    subjectId: lesson.subjectId,
    lessonNumber: lesson.lessonNumber,
    bindingMode: 'LESSON',
  }
}

function clearDraft(): void {
  formId.value = null
  title.value = ''
  description.value = ''
  link.value = ''
  draftContext.value = null
  createIntent.value = null
  createConflict.value = false
  createPending.value = false
  editIntent.value = null
  editRevision.value = null
  editConflict.value = false
  draftApi = null
}

function invalidateDraft(): void {
  mutationRevision += 1
  mutationBusy.value = false
  mutationId.value = null
  clearDraft()
}

function resetContextData(): void {
  loadRevision += 1
  invalidateDraft()
  semester.value = null
  lessons.value = []
  homeworks.value = []
  selectedLessonId.value = null
  subjects.value = []
  selectedSubjectId.value = null
  historyRevision += 1
  historyId.value = null
  historyEntries.value = []
  historyLoading.value = false
  loading.value = false
  error.value = null
  notice.value = null
}

function isCurrentDraftContext(context: AssistantHomeworkDraftContext): boolean {
  return draftApi !== null
    && draftApi === props.api
    && sameAssistantHomeworkDraftContext(context, currentDraftContext())
}

function openCreate(): void {
  if (!canCreate.value || formId.value !== null || !props.api) return
  const context = currentDraftContext()
  if (!context) return
  formId.value = 'new'
  draftContext.value = context
  draftApi = props.api
  title.value = ''
  description.value = ''
  link.value = ''
  notice.value = null
}

function openEdit(item: HeadmanManagedHomework): void {
  if (!canEdit(item)
    || formId.value !== null || createIntent.value !== null || !props.api) return
  bindingMode.value = item.bindingMode
  if (item.bindingMode === 'DATE') selectedSubjectId.value = item.subjectId
  const context = currentDraftContext()
  if (!context) return
  formId.value = item.id
  draftContext.value = context
  draftApi = props.api
  editRevision.value = item.revision
  title.value = item.title
  description.value = item.description ?? ''
  link.value = item.link ?? ''
  notice.value = null
}

function closeForm(): void {
  if (!mutationBusy.value && !retryLocked.value) clearDraft()
}

function requestKey(): string {
  return crypto.randomUUID()
}

function validForm(): boolean {
  return Boolean(title.value.trim())
    && Array.from(title.value).length <= 255
    && Array.from(description.value).length <= 4000
    && Array.from(link.value).length <= 2048
}

async function save(): Promise<void> {
  const api = props.api
  const context = draftContext.value
  const currentForm = formId.value
  if (mutationBusy.value || !api || !context || !isCurrentDraftContext(context) || !validForm()
    || (currentForm === 'new' && !canCreate.value)) return
  const revision = ++mutationRevision
  mutationBusy.value = true
  error.value = null
  notice.value = null
  try {
    if (currentForm === 'new') {
      const intent = reuseOrCreateAssistantHomeworkIntent(createIntent.value, context, {
        title: title.value,
        description: description.value,
        link: link.value,
      }, requestKey)
      if (!intent) return
      createIntent.value = intent
      createConflict.value = false
      createPending.value = false
      const created = await api.createHomework(intent.input, intent.receipt)
      if (disposed || revision !== mutationRevision) return
      if (created.state === 'PENDING') {
        createIntent.value = withAssistantHomeworkPublicationReceipt(intent, created.receipt)
        createPending.value = true
        notice.value = 'Сервер принял публикацию. Повтори запрос с тем же ключом, чтобы проверить завершение.'
        return
      }
    } else if (typeof currentForm === 'number') {
      const existing = selectedHomeworks.value.find((item) => item.id === currentForm)
      if (!existing || !canEdit(existing) || editRevision.value === null) return
      const intent = reuseOrCreateAssistantHomeworkEditIntent(editIntent.value, context, currentForm, editRevision.value, {
        title: title.value,
        description: description.value,
        link: link.value,
      }, requestKey)
      if (!intent) return
      editIntent.value = intent
      editConflict.value = false
      await api.updateHomework(currentForm, intent.input)
    } else {
      return
    }
    if (disposed || revision !== mutationRevision) return
    clearDraft()
    notice.value = 'ДЗ сохранено. Список обновлён с сервера.'
    await load()
  } catch (cause) {
    if (disposed || cause instanceof StaleSessionGenerationError) return
    if (revision !== mutationRevision) {
      // The request may still receive a current-owner capability denial after
      // the user changed dates or lessons. Refresh permissions without
      // restoring the stale draft or its error state.
      if (cause instanceof HeadmanHomeworkApiError && cause.response.status === 403) emit('error', cause)
      return
    }
    if (currentForm === 'new') {
      createPending.value = false
      const status = cause instanceof HeadmanHomeworkApiError ? cause.response.status : null
      createIntent.value = intentAfterHomeworkCreateFailure(
        createIntent.value,
        status,
      )
      createConflict.value = status === 409 && createIntent.value !== null
    } else {
      const status = cause instanceof HeadmanHomeworkApiError ? cause.response.status : null
      if (status === 400 || status === 422) editIntent.value = null
      editConflict.value = status === 409
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось сохранить ДЗ.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === mutationRevision) mutationBusy.value = false
  }
}

async function remove(item: HeadmanManagedHomework): Promise<void> {
  const api = props.api
  if (mutationBusy.value || retryLocked.value || !api || props.offline || props.readOnly || props.userId === null
    || item.archived) return
  if (typeof window !== 'undefined' && !window.confirm(`Удалить задание «${item.title}»?`)) return
  const revision = ++mutationRevision
  mutationBusy.value = true
  mutationId.value = item.id
  error.value = null
  notice.value = null
  try {
    await api.deleteHomework(item.id)
    if (disposed || revision !== mutationRevision) return
    notice.value = 'ДЗ удалено. Список обновлён с сервера.'
    await load()
  } catch (cause) {
    if (disposed || cause instanceof StaleSessionGenerationError) return
    if (revision !== mutationRevision) {
      if (cause instanceof HeadmanHomeworkApiError && cause.response.status === 403) emit('error', cause)
      return
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось удалить ДЗ.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === mutationRevision) {
      mutationBusy.value = false
      mutationId.value = null
    }
  }
}

async function load(): Promise<void> {
  const revision = ++loadRevision
  loading.value = true
  error.value = null
  const api = props.api
  const journalApi = props.journalApi
  const groupId = props.groupId
  if (disposed || props.offline || !api || !journalApi || groupId === null) {
    loading.value = false
    return
  }
  try {
    const currentSemester = await api.activeSemester()
    if (disposed || revision !== loadRevision) return
    if ((semester.value?.id ?? null) !== (currentSemester?.id ?? null)) {
      invalidateDraft()
      lessons.value = []
      homeworks.value = []
      selectedLessonId.value = null
    }
    semester.value = currentSemester
    if (!currentSemester) {
      lessons.value = []
      homeworks.value = []
      selectedLessonId.value = null
      return
    }
    const [nextLessons, nextHomeworks, nextSubjects] = await Promise.all([
      journalApi.listLessons(groupId, selectedDate.value, selectedDate.value),
      api.listHomeworks(groupId, currentSemester.id),
      api.listSubjects(),
    ])
    if (disposed || revision !== loadRevision) return
    lessons.value = nextLessons
    homeworks.value = nextHomeworks
    subjects.value = nextSubjects
    selectedSubjectId.value = nextSubjects.some((subject) => subject.id === selectedSubjectId.value)
      ? selectedSubjectId.value : nextSubjects[0]?.id ?? null
    selectedLessonId.value = nextLessons.some((lesson) => lesson.id === selectedLessonId.value)
      ? selectedLessonId.value
      : nextLessons[0]?.id ?? null
  } catch (cause) {
    if (disposed || revision !== loadRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof HeadmanHomeworkApiError && (cause.response.status === 401 || cause.response.status === 403)
      ? 'Управление ДЗ недоступно для текущей роли.'
      : cause instanceof Error ? cause.message : 'Не удалось загрузить домашние задания.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === loadRevision) loading.value = false
  }
}

watch(
  () => [props.api, props.journalApi, props.groupId, props.userId, props.offline, props.readOnly, selectedDate.value] as const,
  () => {
    resetContextData()
    void load()
  },
  { immediate: true, flush: 'sync' },
)

watch(
  () => { const lesson = selectedLesson.value
    return bindingMode.value === 'DATE' ? `DATE:${selectedSubjectId.value}`
      : `LESSON:${lesson?.id}:${lesson?.date}:${lesson?.subjectId}:${lesson?.lessonNumber}` },
  () => {
    invalidateDraft()
    error.value = null
    notice.value = null
  },
  { flush: 'sync' },
)

async function refreshConflictingEdit(): Promise<void> {
  if (mutationBusy.value) return
  clearDraft()
  await load()
  notice.value = 'Список обновлён. Открой задание заново, чтобы редактировать текущую версию.'
}

async function showHistory(item: HeadmanManagedHomework): Promise<void> {
  const api = props.api
  if (!api || props.offline) return
  const revision = ++historyRevision
  historyId.value = item.id
  historyEntries.value = []
  historyLoading.value = true
  try {
    const entries = await api.history(item.id)
    if (!disposed && revision === historyRevision && props.api === api) historyEntries.value = entries
  } catch (cause) {
    if (!disposed && revision === historyRevision && !(cause instanceof StaleSessionGenerationError)) {
      error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить историю ДЗ.'
      emit('error', cause)
    }
  } finally {
    if (!disposed && revision === historyRevision) historyLoading.value = false
  }
}

onBeforeUnmount(() => {
  disposed = true
  loadRevision += 1
  mutationRevision += 1
  historyRevision += 1
})
</script>

<template>
  <main
    class="assistant-homework"
    :aria-busy="loading || mutationBusy"
    aria-labelledby="assistant-homework-title"
  >
    <header class="assistant-homework__header">
      <div>
        <p class="assistant-homework__eyebrow">Помощник группы · ДЗ</p>
        <h1 id="assistant-homework-title">Домашние задания</h1>
        <p class="assistant-homework__date">{{ formatDate(selectedDate) }}</p>
      </div>
      <nav class="assistant-homework__date-nav" aria-label="Выбор даты">
        <button type="button" aria-label="Предыдущий день" @click="shiftDate(-1)">←</button>
        <button type="button" aria-label="Следующий день" @click="shiftDate(1)">→</button>
      </nav>
    </header>

    <p v-if="offline" class="assistant-homework__state" role="status">Управление ДЗ доступно только онлайн.</p>
    <p v-else-if="error" class="assistant-homework__state assistant-homework__state--error" role="alert">{{ error }}</p>
    <p v-if="notice" class="assistant-homework__state assistant-homework__state--notice" role="status">{{ notice }}</p>
    <p v-if="!semester && !loading" class="assistant-homework__state">Нет активного семестра для управления ДЗ.</p>

    <section v-if="loading" class="assistant-homework__state" role="status">Загружаем пары и ДЗ…</section>
    <template v-else-if="semester">
      <section class="assistant-homework__form" aria-label="Привязка домашнего задания">
        <label><span>Дата</span><input v-model="selectedDate" type="date" :min="semester.dateFrom ?? undefined" :max="semester.dateTo ?? undefined" :disabled="mutationBusy || formId !== null"></label>
        <label><span>Привязка</span><select v-model="bindingMode" :disabled="mutationBusy || formId !== null">
          <option value="LESSON">К паре</option><option value="DATE">На дату</option>
        </select></label>
        <label v-if="bindingMode === 'DATE'"><span>Предмет</span><select v-model="selectedSubjectId" :disabled="mutationBusy || formId !== null">
          <option v-for="subject in subjects" :key="subject.id" :value="subject.id">{{ subject.name }}</option>
        </select></label>
      </section>
      <section v-if="bindingMode === 'LESSON'" class="assistant-homework__lessons" aria-labelledby="assistant-homework-lessons-title">
        <h2 id="assistant-homework-lessons-title">Пары за день</h2>
        <p v-if="lessons.length === 0" class="assistant-homework__empty">На эту дату пар нет.</p>
        <div v-else class="assistant-homework__lesson-list" role="list">
          <button
            v-for="lesson in lessons"
            :key="lesson.id"
            class="assistant-homework__lesson"
            :data-selected="selectedLessonId === lesson.id"
            type="button"
            :disabled="mutationBusy || formId !== null"
            @click="selectedLessonId = lesson.id"
          >
            {{ lessonLabel(lesson) }}
          </button>
        </div>
      </section>

      <section v-if="bindingMode === 'DATE' || selectedLesson" class="assistant-homework__items" aria-labelledby="assistant-homework-items-title">
        <header class="assistant-homework__section-header">
          <div>
            <h2 id="assistant-homework-items-title">{{ bindingMode === 'DATE' ? 'ДЗ на выбранную дату' : 'ДЗ на выбранную пару' }}</h2>
            <p>{{ bindingMode === 'DATE' ? formatDate(selectedDate) : selectedLesson ? lessonLabel(selectedLesson) : '' }}</p>
          </div>
          <button
            v-if="canCreate"
            type="button"
            :disabled="mutationBusy || formId !== null"
            @click="openCreate"
          >
            Добавить
          </button>
        </header>

        <p v-if="selectedHomeworks.length === 0" class="assistant-homework__empty">Заданий пока нет.</p>
        <article v-for="item in selectedHomeworks" :key="item.id" class="assistant-homework__item">
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ subjectLabel(item) }}</p>
            <p v-if="item.archived">Архив · только чтение</p>
            <p v-if="item.description">{{ item.description }}</p>
            <a v-if="item.link" :href="item.link" target="_blank" rel="noopener noreferrer">Открыть материал</a>
          </div>
          <div class="assistant-homework__item-actions">
            <button v-if="canEdit(item)" type="button" :disabled="mutationBusy || formId !== null" @click="openEdit(item)">Изменить</button>
            <button v-if="canEdit(item)" type="button" :disabled="mutationBusy || formId !== null" @click="remove(item)">
              {{ mutationId === item.id ? 'Удаляем…' : 'Удалить' }}
            </button>
            <button type="button" :disabled="offline || historyLoading" @click="showHistory(item)">История</button>
          </div>
          <section v-if="historyId === item.id" aria-label="История домашнего задания">
            <p v-if="historyLoading" role="status">Загружаем историю…</p>
            <p v-else-if="historyEntries.length === 0">История изменений пуста.</p>
            <ol v-else>
              <li v-for="entry in historyEntries" :key="entry.id">
                Версия {{ entry.revision }} · {{ entry.action === 'LESSON_TRANSFERRED' ? 'Пара перенесена' : 'ДЗ изменено' }}
                · {{ new Date(entry.occurredAt).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' }) }}
                <span v-if="entry.actorId !== null"> · Пользователь #{{ entry.actorId }}</span>
              </li>
            </ol>
          </section>
        </article>

        <form v-if="formId !== null" class="assistant-homework__form" @submit.prevent="save">
          <h3>{{ formId === 'new' ? 'Новое ДЗ' : 'Изменить ДЗ' }}</h3>
          <p v-if="createConflict" class="assistant-homework__state assistant-homework__state--error" role="alert">
            Сервер вернул конфликт. Повтор отправит тот же запрос с тем же ключом.
          </p>
          <div v-else-if="editConflict" class="assistant-homework__state assistant-homework__state--error" role="alert">
            Задание уже изменилось или ключ запроса конфликтует. Твой вариант не перезаписал серверную версию.
            <button type="button" :disabled="mutationBusy" @click="refreshConflictingEdit">Обновить задание и закрыть черновик</button>
          </div>
          <p v-else-if="createPending" class="assistant-homework__state" role="status">
            Публикация ещё выполняется. Повтор проверит тот же запрос, без нового задания.
          </p>
          <p v-else-if="uncertainCreate" class="assistant-homework__state" role="status">
            Результат отправки неизвестен. Повтори сохранение, чтобы отправить тот же запрос.
          </p>
          <label><span>Название</span><input v-model="title" maxlength="255" required :disabled="mutationBusy || retryLocked"></label>
          <label><span>Описание</span><textarea v-model="description" maxlength="4000" rows="3" :disabled="mutationBusy || retryLocked" /></label>
          <label><span>Ссылка</span><input v-model="link" maxlength="2048" type="url" :disabled="mutationBusy || retryLocked"></label>
          <p v-if="title.trim() === '' || !validForm()" class="assistant-homework__form-error">Проверь название и длину полей.</p>
          <div class="assistant-homework__form-actions">
            <button type="button" :disabled="mutationBusy || retryLocked" @click="closeForm">Отмена</button>
            <button type="submit" :disabled="mutationBusy || !validForm() || editConflict">
              {{ mutationBusy ? 'Сохраняем…' : retryLocked ? 'Повторить запрос' : 'Сохранить' }}
            </button>
          </div>
        </form>
      </section>
    </template>
  </main>
</template>
