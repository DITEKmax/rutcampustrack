<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  HeadmanSubjectsApiError,
  type HeadmanSemester,
  type HeadmanSubject,
  type HeadmanSubjectAssignment,
  type HeadmanSubjectType,
  type HeadmanTeacher,
  type HeadmanSubjectsApi,
} from './headman-subjects-client'
import './headman-subjects-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanSubjectsApi | null
  groupId: number | null
  actorUserId?: string | null
  offline?: boolean
  readOnly?: boolean
}>(), {
  actorUserId: null,
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

type StoredReplacementPhase = 'uncertain' | 'pending' | 'committed' | 'conflict' | 'rejected' | 'missing'

interface StoredReplacement {
  readonly version: 2
  readonly actorUserId: string
  readonly groupId: number
  readonly assignmentId: number
  readonly subjectId: number
  readonly subjectName: string
  readonly lessonType: string
  readonly sourceTeacherId: number
  readonly replacementTeacherId: string
  readonly effectiveFrom: string
  readonly requestKey: string
  readonly operationId: string | null
  readonly state: string | null
  readonly phase: StoredReplacementPhase
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
const replacementPicker = reactive<PickerState>(emptyPicker())
const replacementAssignmentId = ref<number | null>(null)
const replacementTeacher = ref<HeadmanTeacher | null>(null)
const replacementEffectiveFrom = ref('')
const replacementIntents = ref<StoredReplacement[]>([])
const replacementBusy = ref(false)
const replacementError = ref<string | null>(null)
const replacementErrorRequestKey = ref<string | null>(null)
const replacementStorageError = ref<string | null>(null)
const replacementStorageBlocked = ref(false)
const corruptedReplacementKeys = ref<string[]>([])
let loadRevision = 0
let mutationRevision = 0
let replacementRevision = 0
let disposed = false
type LoadOutcome = 'loaded' | 'stale' | 'skipped' | 'denied' | 'failed'

const formTypes = computed(() => selectedTypes.value)
const addSubject = computed(() => subjects.value.find((subject) => subject.id === addSubjectId.value) ?? null)
const replacementSource = computed(() => {
  for (const subject of subjects.value) {
    const assignment = subject.assignments.find((candidate) => candidate.id === replacementAssignmentId.value)
    if (assignment) return { subject, assignment }
  }
  return null
})
const mutationLocked = computed(() => busy.value || replacementBusy.value || replacementStorageBlocked.value)

function labelForType(type: HeadmanSubjectType): string {
  return typeLabels[type]
}

function storedLessonTypeLabel(type: string): string {
  return type in typeLabels ? typeLabels[type as HeadmanSubjectType] : type
}

function teacherLabel(teacherId: number): string {
  const teacher = teachers[teacherId]
  return teacher?.fullName || `Преподаватель #${teacherId}`
}

function teacherDetail(teacher: HeadmanTeacher): string {
  return teacher.employeeNumber ? `${teacher.fullName} · ${teacher.employeeNumber}` : teacher.fullName
}

function replacementStoragePrefix(actorUserId: string, groupId: number): string {
  return `rct.headman.assignment-replacement.v2.${encodeURIComponent(actorUserId)}.${groupId}.`
}

function replacementStorageKey(intent: Pick<StoredReplacement, 'actorUserId' | 'groupId' | 'requestKey'>): string {
  return `${replacementStoragePrefix(intent.actorUserId, intent.groupId)}${intent.requestKey}`
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

function isStoredReplacement(value: unknown, actorUserId: string, groupId: number, requestKey: string): value is StoredReplacement {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const record = value as Record<string, unknown>
  return record.version === 2
    && record.actorUserId === actorUserId
    && record.groupId === groupId
    && Number.isSafeInteger(record.assignmentId) && Number(record.assignmentId) > 0
    && Number.isSafeInteger(record.subjectId) && Number(record.subjectId) > 0
    && typeof record.subjectName === 'string'
    && typeof record.lessonType === 'string'
    && Number.isSafeInteger(record.sourceTeacherId) && Number(record.sourceTeacherId) > 0
    && typeof record.replacementTeacherId === 'string' && /^[1-9][0-9]*$/.test(record.replacementTeacherId)
    && typeof record.effectiveFrom === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(record.effectiveFrom)
    && isUuid(record.requestKey)
    && record.requestKey === requestKey
    && (record.operationId === null || isUuid(record.operationId))
    && (record.state === null || typeof record.state === 'string')
    && (record.phase === 'uncertain' || record.phase === 'pending' || record.phase === 'committed' || record.phase === 'conflict' || record.phase === 'rejected' || record.phase === 'missing')
}

function sameReplacementRequest(left: StoredReplacement, right: StoredReplacement): boolean {
  return left.version === right.version
    && left.actorUserId === right.actorUserId
    && left.groupId === right.groupId
    && left.assignmentId === right.assignmentId
    && left.subjectId === right.subjectId
    && left.subjectName === right.subjectName
    && left.lessonType === right.lessonType
    && left.sourceTeacherId === right.sourceTeacherId
    && left.replacementTeacherId === right.replacementTeacherId
    && left.effectiveFrom === right.effectiveFrom
    && left.requestKey === right.requestKey
}

function upsertReplacementIntent(intent: StoredReplacement): void {
  const index = replacementIntents.value.findIndex((candidate) => candidate.requestKey === intent.requestKey
    && candidate.actorUserId === intent.actorUserId
    && candidate.groupId === intent.groupId)
  if (index < 0) replacementIntents.value = [...replacementIntents.value, intent]
  else replacementIntents.value = replacementIntents.value.map((candidate, candidateIndex) => candidateIndex === index ? intent : candidate)
}

function removeReplacementIntentFromMemory(intent: StoredReplacement): void {
  replacementIntents.value = replacementIntents.value.filter((candidate) => !(candidate.actorUserId === intent.actorUserId
    && candidate.groupId === intent.groupId
    && candidate.requestKey === intent.requestKey))
}

function replacementBelongsToCurrentActor(intent: StoredReplacement): boolean {
  return props.actorUserId !== null
    && props.actorUserId !== undefined
    && props.actorUserId === intent.actorUserId
    && props.groupId === intent.groupId
}

function hasPendingReplacement(assignmentId: number): boolean {
  return replacementIntents.value.some((intent) => replacementBelongsToCurrentActor(intent) && intent.assignmentId === assignmentId)
}

function restoreReplacementIntents(): void {
  replacementIntents.value = []
  replacementStorageError.value = null
  replacementStorageBlocked.value = false
  corruptedReplacementKeys.value = []
  const actorUserId = props.actorUserId
  if (!actorUserId || props.groupId === null || typeof window === 'undefined') return
  try {
    const prefix = replacementStoragePrefix(actorUserId, props.groupId)
    const restored: StoredReplacement[] = []
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index)
      if (!key || !key.startsWith(prefix)) continue
      const requestKey = key.slice(prefix.length)
      const raw = window.localStorage.getItem(key)
      if (raw === null) continue
      try {
        const value: unknown = JSON.parse(raw)
        if (!isStoredReplacement(value, actorUserId, props.groupId, requestKey)) {
          corruptedReplacementKeys.value.push(key)
          continue
        }
        restored.push(value)
      } catch {
        corruptedReplacementKeys.value.push(key)
      }
    }
    replacementIntents.value = restored.sort((left, right) => left.requestKey.localeCompare(right.requestKey))
    if (corruptedReplacementKeys.value.length > 0) {
      replacementStorageError.value = 'Сохранённая операция замены повреждена. Очисти повреждённую запись, прежде чем начинать другую замену.'
      replacementStorageBlocked.value = true
    }
  } catch {
    replacementStorageError.value = 'Не удалось прочитать сохранённую операцию замены. Новые изменения заблокированы до восстановления хранилища.'
    replacementStorageBlocked.value = true
  }
}

function persistReplacementIntent(intent: StoredReplacement): boolean {
  if (!replacementBelongsToCurrentActor(intent)) {
    replacementStorageError.value = 'Пользовательская сессия изменилась. Операция не отправлена.'
    return false
  }
  if (typeof window === 'undefined') {
    replacementStorageError.value = 'Нельзя сохранить ключ замены в этом окружении. Запрос не отправлен.'
    return false
  }
  try {
    const key = replacementStorageKey(intent)
    const raw = window.localStorage.getItem(key)
    if (raw !== null) {
      let existing: unknown
      try {
        existing = JSON.parse(raw)
      } catch {
        replacementStorageError.value = 'Для этого ключа уже есть повреждённая локальная запись. Запрос не отправлен.'
        replacementStorageBlocked.value = true
        corruptedReplacementKeys.value = [...new Set([...corruptedReplacementKeys.value, key])]
        return false
      }
      if (!isStoredReplacement(existing, intent.actorUserId, intent.groupId, intent.requestKey)
          || !sameReplacementRequest(existing, intent)) {
        replacementStorageError.value = 'Ключ запроса уже привязан к другому намерению. Запрос не отправлен.'
        replacementStorageBlocked.value = true
        corruptedReplacementKeys.value = [...new Set([...corruptedReplacementKeys.value, key])]
        return false
      }
      const existingIsTerminalFailure = existing.phase === 'conflict' || existing.phase === 'rejected' || existing.phase === 'missing'
      if (existingIsTerminalFailure && intent.phase !== 'committed' && intent.state !== 'COMMITTED'
          && (intent.phase === 'uncertain' || intent.phase === 'pending')) {
        intent = { ...intent, operationId: existing.operationId ?? intent.operationId, state: existing.state ?? intent.state, phase: existing.phase }
      }
      if (existing.operationId !== null && intent.operationId === null) intent = { ...intent, operationId: existing.operationId }
      if (existing.state !== null && intent.state === null) intent = { ...intent, state: existing.state }
      if (existing.phase !== 'uncertain' && intent.phase === 'uncertain') intent = { ...intent, phase: existing.phase }
      if ((existing.phase === 'committed' || existing.state === 'COMMITTED')
          && intent.phase !== 'committed' && intent.state !== 'COMMITTED') {
        intent = { ...intent, phase: 'committed', state: 'COMMITTED' }
      }
    }
    window.localStorage.setItem(key, JSON.stringify(intent))
    upsertReplacementIntent(intent)
    replacementStorageError.value = null
    return true
  } catch {
    replacementStorageError.value = 'Не удалось сохранить ключ запроса. Замена не отправлена; проверь доступность хранилища браузера.'
    return false
  }
}

function removeStoredReplacementIntent(intent: StoredReplacement): boolean {
  if (typeof window === 'undefined' || !replacementBelongsToCurrentActor(intent)) return false
  try {
    const key = replacementStorageKey(intent)
    const raw = window.localStorage.getItem(key)
    if (raw !== null) {
      let existing: unknown
      try {
        existing = JSON.parse(raw)
      } catch {
        replacementStorageError.value = 'Не удалось проверить сохранённую операцию перед очисткой.'
        return false
      }
      if (!isStoredReplacement(existing, intent.actorUserId, intent.groupId, intent.requestKey)
          || !sameReplacementRequest(existing, intent)) {
        replacementStorageError.value = 'Локальная запись уже изменилась. Обнови её перед очисткой.'
        return false
      }
      window.localStorage.removeItem(key)
    }
    removeReplacementIntentFromMemory(intent)
    replacementStorageError.value = null
    return true
  } catch {
    replacementStorageError.value = 'Не удалось очистить локальную запись операции.'
    return false
  }
}

function clearCorruptedReplacementStorage(): void {
  const actorUserId = props.actorUserId
  if (!actorUserId || props.groupId === null || typeof window === 'undefined') return
  try {
    const prefix = replacementStoragePrefix(actorUserId, props.groupId)
    for (const key of corruptedReplacementKeys.value) {
      if (key.startsWith(prefix)) window.localStorage.removeItem(key)
    }
    restoreReplacementIntents()
  } catch {
    replacementStorageError.value = 'Не удалось очистить локальную запись операции.'
  }
}

function createRequestKey(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID()
  if (cryptoApi?.getRandomValues) {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
    bytes[6] = (bytes[6]! & 0x0f) | 0x40
    bytes[8] = (bytes[8]! & 0x3f) | 0x80
    const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  throw new Error('Браузер не может создать ключ безопасного повтора запроса.')
}

function replacementStatusText(intent: StoredReplacement): string {
  if (intent.phase === 'conflict') return 'Сервер сообщил о конфликте назначения. Обнови список и начни новую замену после проверки данных.'
  if (intent.phase === 'rejected') return 'Сервер отклонил параметры запроса. Проверь назначение и дату перед новой попыткой.'
  if (intent.phase === 'missing') return 'Сервер не нашёл сохранённую операцию. Проверь список перед новой попыткой.'
  if (intent.phase === 'committed' || intent.state === 'COMMITTED') return 'Сервер подтвердил замену. Обновляем предметы и назначения.'
  if (intent.state === 'PREPARED') return 'Операция подготовлена. Замена ещё не подтверждена.'
  if (intent.state === 'APPLIED') return 'Операция обрабатывается. Замена ещё не подтверждена.'
  if (intent.state && intent.state !== 'COMMITTED') return `Получено неизвестное состояние «${intent.state}». Замена не считается завершённой.`
  return intent.operationId
    ? 'Операция сохранена на сервере. Проверь её состояние.'
    : 'Результат запроса не получен. Повтор можно выполнить с сохранённым ключом.'
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
    const confirmed = replacementIntents.value.filter((intent) => replacementBelongsToCurrentActor(intent)
      && (intent.phase === 'committed' || intent.state === 'COMMITTED'))
    if (confirmed.length > 0) {
      notice.value = 'Замена подтверждена сервером; предметы и назначения обновлены.'
      for (const intent of confirmed) removeStoredReplacementIntent(intent)
    }
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

function openReplacement(assignment: HeadmanSubjectAssignment): void {
  if (!props.actorUserId || props.offline || props.readOnly || denied.value || !activeSemester.value || assignment.semesterId !== activeSemester.value.id || mutationLocked.value) return
  if (hasPendingReplacement(assignment.id)) {
    replacementError.value = 'Для этого назначения уже сохранена незавершённая замена.'
    replacementErrorRequestKey.value = null
    return
  }
  formOpen.value = false
  addSubjectId.value = null
  addLessonType.value = null
  replacementAssignmentId.value = assignment.id
  replacementTeacher.value = null
  replacementEffectiveFrom.value = ''
  replacementPicker.query = ''
  replacementPicker.results = []
  replacementPicker.loading = false
  replacementPicker.revision += 1
  replacementError.value = null
  replacementErrorRequestKey.value = null
  notice.value = null
}

function closeReplacement(): void {
  if (replacementBusy.value) return
  replacementRevision += 1
  replacementPicker.revision += 1
  replacementAssignmentId.value = null
  replacementTeacher.value = null
  replacementEffectiveFrom.value = ''
  replacementError.value = null
  replacementErrorRequestKey.value = null
}

async function searchReplacementTeachers(): Promise<void> {
  if (!props.actorUserId || !props.api || props.offline || props.readOnly || replacementBusy.value
      || (replacementAssignmentId.value !== null && hasPendingReplacement(replacementAssignmentId.value))) return
  const revision = ++replacementPicker.revision
  replacementPicker.loading = true
  replacementError.value = null
  try {
    const page = await props.api.searchTeachers(replacementPicker.query, 0)
    if (revision !== replacementPicker.revision || disposed) return
    replacementPicker.results = page.items
    for (const teacher of page.items) teachers[teacher.id] = teacher
  } catch (cause) {
    if (revision !== replacementPicker.revision || disposed) return
    if (cause instanceof HeadmanSubjectsApiError && (cause.response.status === 401 || cause.response.status === 403)) denied.value = true
    replacementError.value = cause instanceof Error ? cause.message : 'Не удалось найти преподавателя.'
    replacementErrorRequestKey.value = null
    if (!(cause instanceof HeadmanSubjectsApiError && (cause.response.status === 401 || cause.response.status === 403))) emit('error', cause)
  } finally {
    if (revision === replacementPicker.revision && !disposed) replacementPicker.loading = false
  }
}

function selectReplacementTeacher(teacher: HeadmanTeacher): void {
  if (!props.actorUserId || mutationLocked.value || props.offline || props.readOnly) return
  replacementTeacher.value = teacher
  teachers[teacher.id] = teacher
  replacementError.value = null
  replacementErrorRequestKey.value = null
}

async function startReplacement(): Promise<void> {
  const source = replacementSource.value
  const target = replacementTeacher.value
  if (!props.actorUserId || !props.api || props.offline || props.readOnly || denied.value || mutationLocked.value || !source || props.groupId === null) return
  if (hasPendingReplacement(source.assignment.id)) {
    replacementError.value = 'Для этого назначения уже сохранена незавершённая замена.'
    replacementErrorRequestKey.value = null
    return
  }
  if (!target) {
    replacementError.value = 'Найди и выбери нового преподавателя.'
    return
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(replacementEffectiveFrom.value)) {
    replacementError.value = 'Укажи дату начала замены.'
    return
  }
  let requestKey: string
  try {
    requestKey = createRequestKey()
  } catch (cause) {
    replacementError.value = cause instanceof Error ? cause.message : 'Не удалось создать ключ запроса.'
    return
  }
  const intent: StoredReplacement = {
    version: 2,
    actorUserId: props.actorUserId,
    groupId: props.groupId,
    assignmentId: source.assignment.id,
    subjectId: source.subject.id,
    subjectName: source.subject.name,
    lessonType: source.assignment.lessonType,
    sourceTeacherId: source.assignment.teacherId,
    replacementTeacherId: String(target.id),
    effectiveFrom: replacementEffectiveFrom.value,
    requestKey,
    operationId: null,
    state: null,
    phase: 'uncertain',
  }
  replacementError.value = null
  replacementErrorRequestKey.value = null
  notice.value = null
  if (!persistReplacementIntent(intent)) return
  await sendReplacementIntent(intent)
}

async function sendReplacementIntent(intent: StoredReplacement): Promise<void> {
  if (!replacementBelongsToCurrentActor(intent) || !props.api || props.offline || props.readOnly || denied.value) return
  const api = props.api
  const revision = ++replacementRevision
  replacementBusy.value = true
  replacementError.value = null
  replacementErrorRequestKey.value = intent.requestKey
  try {
    const response = await api.replaceAssignment(intent.assignmentId, {
      replacementTeacherId: intent.replacementTeacherId,
      effectiveFrom: intent.effectiveFrom,
      requestKey: intent.requestKey,
    })
    if (revision !== replacementRevision || disposed) return
    await acceptReplacementResponse(intent, response, revision)
  } catch (cause) {
    if (revision !== replacementRevision || disposed) return
    handleReplacementError(intent, cause)
  } finally {
    if (revision === replacementRevision && !disposed) replacementBusy.value = false
  }
}

async function checkReplacementStatus(intent: StoredReplacement): Promise<void> {
  if (!replacementBelongsToCurrentActor(intent) || !props.api || props.offline || props.readOnly || denied.value || !intent.operationId) return
  const api = props.api
  const revision = ++replacementRevision
  replacementBusy.value = true
  replacementError.value = null
  replacementErrorRequestKey.value = intent.requestKey
  try {
    const response = await api.getReplacementStatus(intent.operationId)
    if (revision !== replacementRevision || disposed) return
    await acceptReplacementResponse(intent, response, revision)
  } catch (cause) {
    if (revision !== replacementRevision || disposed) return
    handleReplacementError(intent, cause)
  } finally {
    if (revision === replacementRevision && !disposed) replacementBusy.value = false
  }
}

async function acceptReplacementResponse(
  intent: StoredReplacement,
  response: import('./headman-subjects-client').AssignmentReplacementResponse,
  revision: number,
): Promise<void> {
  if (!replacementBelongsToCurrentActor(intent)) return
  if (response.sourceAssignmentId !== intent.assignmentId
      || response.subjectId !== intent.subjectId
      || response.groupId !== intent.groupId
      || response.sourceTeacherId !== intent.sourceTeacherId
      || response.targetTeacherId !== Number(intent.replacementTeacherId)
      || response.lessonType !== intent.lessonType
      || response.effectiveFrom !== intent.effectiveFrom) {
    throw new Error('Ответ сервера не совпал с сохранённым намерением замены. Повтори проверку того же запроса.')
  }
  const next: StoredReplacement = {
    ...intent,
    operationId: response.operationId,
    state: response.state,
    phase: response.state === 'COMMITTED' ? 'committed' : 'pending',
  }
  if (!persistReplacementIntent(next)) upsertReplacementIntent(next)
  if (response.state === 'COMMITTED') {
    const outcome = await load()
    if (revision !== replacementRevision || disposed) return
    if (outcome !== 'loaded' && !replacementError.value) {
      replacementError.value = 'Сервер подтвердил замену, но предметы не удалось обновить. Повтори загрузку списка.'
      replacementErrorRequestKey.value = intent.requestKey
    }
  }
}

function handleReplacementError(intent: StoredReplacement, cause: unknown): void {
  const status = cause instanceof HeadmanSubjectsApiError ? cause.response.status : null
  if (status === 401 || status === 403) denied.value = true
  if (status === 409) {
    const conflicted = { ...intent, phase: 'conflict' as const }
    if (!persistReplacementIntent(conflicted)) upsertReplacementIntent(conflicted)
    replacementError.value = 'Назначение изменилось или конфликтует с другой заменой. Обнови предметы перед новой попыткой.'
    replacementErrorRequestKey.value = intent.requestKey
    return
  }
  if (status === 400) {
    const rejected = { ...intent, phase: 'rejected' as const }
    if (!persistReplacementIntent(rejected)) upsertReplacementIntent(rejected)
    replacementError.value = 'Сервер отклонил параметры замены. Проверь выбранного преподавателя и дату.'
    replacementErrorRequestKey.value = intent.requestKey
    return
  }
  if (status === 404 && intent.operationId) {
    const missing = { ...intent, phase: 'missing' as const }
    if (!persistReplacementIntent(missing)) upsertReplacementIntent(missing)
    replacementError.value = 'Сервер не нашёл операцию замены. Обнови предметы перед новой попыткой.'
    replacementErrorRequestKey.value = intent.requestKey
    return
  }
  if (status === 403) {
    replacementError.value = 'У этой сессии нет права старосты для группы. Операция сохранена для проверки позже.'
    replacementErrorRequestKey.value = intent.requestKey
    return
  }
  replacementError.value = cause instanceof Error ? cause.message : 'Не удалось получить результат замены. Повтори тот же запрос позже.'
  replacementErrorRequestKey.value = intent.requestKey
  if (!(cause instanceof HeadmanSubjectsApiError && status === 401)) emit('error', cause)
}

async function resumeReplacement(intent: StoredReplacement): Promise<void> {
  if (!replacementBelongsToCurrentActor(intent) || replacementBusy.value || denied.value) return
  replacementError.value = null
  replacementErrorRequestKey.value = intent.requestKey
  if (intent.phase === 'committed' || intent.state === 'COMMITTED') {
    const revision = ++replacementRevision
    replacementBusy.value = true
    const outcome = await load()
    if (revision === replacementRevision && outcome !== 'loaded' && !replacementError.value) {
      replacementError.value = 'Сервер уже подтвердил замену, но список ещё не обновлён. Повтори загрузку.'
      replacementErrorRequestKey.value = intent.requestKey
    }
    if (revision === replacementRevision && !disposed) replacementBusy.value = false
  } else if (intent.operationId) {
    await checkReplacementStatus(intent)
  } else {
    await sendReplacementIntent(intent)
  }
}

async function closeResolvedReplacement(intent: StoredReplacement): Promise<void> {
  if (!replacementBelongsToCurrentActor(intent)
      || (intent.phase !== 'conflict' && intent.phase !== 'rejected' && intent.phase !== 'missing')
      || replacementBusy.value) return
  replacementBusy.value = true
  replacementError.value = null
  replacementErrorRequestKey.value = intent.requestKey
  const outcome = await load()
  if (outcome === 'loaded') {
    if (removeStoredReplacementIntent(intent)) {
      if (replacementAssignmentId.value === intent.assignmentId) {
        replacementAssignmentId.value = null
        replacementTeacher.value = null
        replacementEffectiveFrom.value = ''
      }
      replacementError.value = null
      replacementErrorRequestKey.value = null
    }
  } else {
    replacementError.value = 'Не удалось обновить предметы. Сохранённую операцию не очищали.'
    replacementErrorRequestKey.value = intent.requestKey
  }
  replacementBusy.value = false
}

function resetPicker(type: HeadmanSubjectType): void {
  pickers[type].query = ''
  pickers[type].results = []
  pickers[type].loading = false
  pickers[type].revision += 1
  selectedTeachers[type] = []
}

function openCreate(): void {
  if (props.offline || props.readOnly || mutationLocked.value || !activeSemester.value) {
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
  if (mutationLocked.value) return
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
  if (!props.api || props.offline || props.readOnly || mutationLocked.value) return
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
  if (props.offline || props.readOnly || mutationLocked.value || !activeSemester.value) return
  addSubjectId.value = subjectId
  addLessonType.value = type
  addPicker.query = ''
  addPicker.results = []
  addPicker.loading = false
  addPicker.revision += 1
  error.value = null
}

function closeAddTeacher(): void {
  if (mutationLocked.value) return
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
  if (!props.api || !activeSemester.value || !addSubject.value || !addLessonType.value || mutationLocked.value) return
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

function onReplacementStorageChange(event: StorageEvent): void {
  if (typeof window === 'undefined' || (event.storageArea && event.storageArea !== window.localStorage)) return
  const actorUserId = props.actorUserId
  if (!actorUserId || props.groupId === null) return
  const prefix = replacementStoragePrefix(actorUserId, props.groupId)
  if (event.key !== null && !event.key.startsWith(prefix)) return
  restoreReplacementIntents()
  if (replacementAssignmentId.value !== null && hasPendingReplacement(replacementAssignmentId.value)) {
    replacementAssignmentId.value = null
    replacementTeacher.value = null
    replacementEffectiveFrom.value = ''
    replacementPicker.results = []
    replacementError.value = 'В другой вкладке уже сохранена незавершённая замена для этого назначения.'
    replacementErrorRequestKey.value = null
  }
}

if (typeof window !== 'undefined') window.addEventListener('storage', onReplacementStorageChange)

watch(
  () => [props.api, props.groupId, props.offline, props.actorUserId] as const,
  () => {
    loadRevision += 1
    mutationRevision += 1
    replacementRevision += 1
    busy.value = false
    loading.value = false
    replacementBusy.value = false
    replacementPicker.revision += 1
    replacementPicker.loading = false
    addPicker.revision += 1
    addPicker.loading = false
    for (const picker of Object.values(pickers)) {
      picker.revision += 1
      picker.loading = false
      picker.results = []
      picker.query = ''
    }
    subjects.value = []
    semesters.value = []
    for (const teacherId of Object.keys(teachers)) delete teachers[Number(teacherId)]
    selectedTypes.value = []
    for (const type of subjectTypes) selectedTeachers[type] = []
    formOpen.value = false
    subjectName.value = ''
    addSubjectId.value = null
    addLessonType.value = null
    addPicker.results = []
    addPicker.query = ''
    replacementAssignmentId.value = null
    replacementTeacher.value = null
    replacementEffectiveFrom.value = ''
    replacementError.value = null
    replacementErrorRequestKey.value = null
    replacementPicker.results = []
    replacementPicker.query = ''
    replacementIntents.value = []
    replacementStorageError.value = null
    corruptedReplacementKeys.value = []
    error.value = null
    notice.value = null
    denied.value = false
    restoreReplacementIntents()
    void load()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  loadRevision += 1
  mutationRevision += 1
  replacementRevision += 1
  replacementPicker.revision += 1
  if (typeof window !== 'undefined') window.removeEventListener('storage', onReplacementStorageChange)
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
      <button class="headman-subjects__primary" type="button" :disabled="offline || readOnly || loading || mutationLocked || !activeSemester" @click="openCreate">
        Добавить предмет
      </button>
    </header>

    <p v-if="offline" class="headman-subjects__state" role="status">Изменения доступны только онлайн.</p>
    <p v-if="denied" class="headman-subjects__state" role="alert">У этой сессии нет права старосты для группы.</p>
    <p v-if="error" class="headman-subjects__state headman-subjects__state--error" role="alert">{{ error }}</p>
    <p v-if="notice" class="headman-subjects__state headman-subjects__state--success" role="status">{{ notice }}</p>
    <section v-if="replacementStorageError" class="headman-subjects__replacement" aria-labelledby="headman-replacement-storage-title">
      <h2 id="headman-replacement-storage-title">Локальная запись замены</h2>
      <p class="headman-subjects__state headman-subjects__state--error" role="alert">{{ replacementStorageError }}</p>
        <button v-if="corruptedReplacementKeys.length > 0" class="headman-subjects__secondary" type="button" :disabled="replacementBusy" @click="clearCorruptedReplacementStorage">Очистить локальную запись</button>
    </section>
    <section v-if="replacementIntents.length > 0" class="headman-subjects__replacement" aria-labelledby="headman-replacement-pending-title" :aria-busy="replacementBusy">
      <h2 id="headman-replacement-pending-title">Незавершённые замены преподавателя</h2>
      <article v-for="intent in replacementIntents" :key="intent.requestKey" class="headman-subjects__replacement-operation">
        <p class="headman-subjects__replacement-summary">
          {{ intent.subjectName }} · {{ storedLessonTypeLabel(intent.lessonType) }}:
          {{ teacherLabel(intent.sourceTeacherId) }} → {{ teacherLabel(Number(intent.replacementTeacherId)) }}, с {{ intent.effectiveFrom }}
        </p>
        <p class="headman-subjects__state" role="status" aria-live="polite">{{ replacementStatusText(intent) }}</p>
        <p v-if="replacementError && replacementErrorRequestKey === intent.requestKey" class="headman-subjects__state headman-subjects__state--error" role="alert">{{ replacementError }}</p>
        <button
          v-if="intent.phase !== 'conflict' && intent.phase !== 'rejected' && intent.phase !== 'missing'"
          class="headman-subjects__primary"
          type="button"
          :disabled="offline || readOnly || denied || !actorUserId || replacementBusy"
          @click="resumeReplacement(intent)"
        >
          {{ replacementBusy ? 'Проверяем…' : intent.phase === 'committed' ? 'Обновить предметы' : intent.operationId ? 'Проверить состояние' : 'Повторить тот же запрос' }}
        </button>
        <button
          v-else
          class="headman-subjects__secondary"
          type="button"
          :disabled="offline || readOnly || denied || !actorUserId || replacementBusy"
          @click="closeResolvedReplacement(intent)"
        >
          {{ replacementBusy ? 'Обновляем…' : 'Обновить список и закрыть' }}
        </button>
      </article>
    </section>
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
              <span>{{ teacherLabel(assignment.teacherId) }}</span>
              <button
                v-if="activeSemester && assignment.semesterId === activeSemester.id"
                class="headman-subjects__replace-trigger"
                type="button"
                :disabled="offline || readOnly || denied || !actorUserId || mutationLocked || hasPendingReplacement(assignment.id)"
                :aria-label="`Заменить преподавателя ${teacherLabel(assignment.teacherId)} по предмету ${subject.name}`"
                @click="openReplacement(assignment)"
              >
                Заменить
              </button>
            </li>
          </ul>
          <p v-else class="headman-subjects__hint">Преподаватель ещё не назначен.</p>
          <button class="headman-subjects__secondary" type="button" :disabled="offline || readOnly || mutationLocked || !activeSemester" @click="openAddTeacher(subject.id, type)">
            Добавить преподавателя
          </button>
        </fieldset>
      </article>
    </section>

    <section v-if="formOpen" class="headman-subjects__form" aria-labelledby="headman-subject-form-title">
      <div class="headman-subjects__form-header">
        <h2 id="headman-subject-form-title">Новый предмет</h2>
        <button class="headman-subjects__secondary" type="button" :disabled="mutationLocked" @click="closeForm">Отмена</button>
      </div>
      <label class="headman-subjects__field">
        <span>Название предмета</span>
        <input v-model="subjectName" type="text" maxlength="255" autocomplete="off" :disabled="mutationLocked || offline || readOnly">
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
          :disabled="mutationLocked || offline || readOnly"
          @click="toggleType(type)"
        >
          {{ labelForType(type) }}
        </button>
      </fieldset>
      <fieldset v-for="type in formTypes" :key="type" class="headman-subjects__picker">
        <legend>Преподаватели · {{ labelForType(type) }}</legend>
        <div class="headman-subjects__search-row">
          <input v-model="pickers[type].query" type="search" placeholder="ФИО или табельный номер" :disabled="mutationLocked || offline || readOnly" @keyup.enter="searchTeachers(type)">
          <button class="headman-subjects__secondary" type="button" :disabled="mutationLocked || offline || readOnly || pickers[type].loading" @click="searchTeachers(type)">
            {{ pickers[type].loading ? 'Ищем…' : 'Найти' }}
          </button>
        </div>
        <ul v-if="pickers[type].results.length > 0" class="headman-subjects__results">
          <li v-for="teacher in pickers[type].results" :key="teacher.id">
            <button type="button" :disabled="mutationLocked || offline || readOnly" @click="addSelectedTeacher(type, teacher)">{{ teacherDetail(teacher) }}</button>
          </li>
        </ul>
        <ul v-if="selectedTeachers[type].length > 0" class="headman-subjects__teachers">
          <li v-for="teacher in selectedTeachers[type]" :key="teacher.id">
            {{ teacherDetail(teacher) }}
            <button class="headman-subjects__remove" type="button" :aria-label="`Удалить: ${teacher.fullName}`" :disabled="mutationLocked || offline || readOnly" @click="removeSelectedTeacher(type, teacher.id)">Удалить</button>
          </li>
        </ul>
      </fieldset>
      <button class="headman-subjects__primary" type="button" :disabled="mutationLocked || offline || readOnly" @click="saveSubject">
        {{ busy ? 'Сохраняем…' : 'Сохранить предмет' }}
      </button>
    </section>

    <section v-if="replacementSource && actorUserId && !hasPendingReplacement(replacementSource.assignment.id)" class="headman-subjects__replacement" aria-labelledby="headman-replacement-title" :aria-busy="replacementBusy">
      <div class="headman-subjects__form-header">
        <h2 id="headman-replacement-title">Заменить преподавателя</h2>
        <button class="headman-subjects__secondary" type="button" :disabled="replacementBusy" @click="closeReplacement">Отмена</button>
      </div>
      <p class="headman-subjects__replacement-summary">
        {{ replacementSource.subject.name }} · {{ labelForType(replacementSource.assignment.lessonType) }}:
        {{ teacherLabel(replacementSource.assignment.teacherId) }}
      </p>
      <fieldset class="headman-subjects__picker">
        <legend>Новый преподаватель</legend>
        <div class="headman-subjects__search-row">
          <label class="headman-subjects__field headman-subjects__search-field">
            <span>Поиск по ФИО или табельному номеру</span>
            <input
              v-model="replacementPicker.query"
              type="search"
              autocomplete="off"
              :aria-describedby="replacementError && replacementErrorRequestKey === null ? 'headman-replacement-error' : undefined"
              :disabled="mutationLocked || offline || readOnly || denied"
              @keyup.enter="searchReplacementTeachers"
            >
          </label>
          <button class="headman-subjects__secondary" type="button" :disabled="mutationLocked || offline || readOnly || denied || replacementPicker.loading" @click="searchReplacementTeachers">
            {{ replacementPicker.loading ? 'Ищем…' : 'Найти' }}
          </button>
        </div>
        <p v-if="replacementPicker.loading" class="headman-subjects__state" aria-live="polite">Ищем активных преподавателей…</p>
        <ul v-if="replacementPicker.results.length > 0" class="headman-subjects__results">
          <li v-for="teacher in replacementPicker.results" :key="teacher.id">
            <button
              type="button"
              :aria-pressed="replacementTeacher?.id === teacher.id"
              :disabled="mutationLocked || offline || readOnly || denied"
              @click="selectReplacementTeacher(teacher)"
            >
              {{ teacherDetail(teacher) }}
            </button>
          </li>
        </ul>
        <p v-if="replacementTeacher" class="headman-subjects__replacement-summary">Выбран: {{ teacherDetail(replacementTeacher) }}</p>
      </fieldset>
      <label class="headman-subjects__field">
        <span>Дата начала замены</span>
        <input v-model="replacementEffectiveFrom" type="date" required :aria-describedby="replacementError && replacementErrorRequestKey === null ? 'headman-replacement-error' : undefined" :disabled="mutationLocked || offline || readOnly || denied">
      </label>
      <p v-if="replacementError && replacementErrorRequestKey === null" id="headman-replacement-error" class="headman-subjects__state headman-subjects__state--error" role="alert">{{ replacementError }}</p>
      <p v-if="replacementTeacher && replacementEffectiveFrom" class="headman-subjects__replacement-summary" role="note">
        {{ teacherDetail(replacementTeacher) }} примет назначение с {{ replacementEffectiveFrom }}. Перед отправкой проверь предмет и дату.
      </p>
      <button
        class="headman-subjects__primary"
        type="button"
        :disabled="mutationLocked || offline || readOnly || denied || !replacementTeacher || !replacementEffectiveFrom"
        @click="startReplacement"
      >
        {{ replacementBusy ? 'Отправляем…' : 'Подтвердить замену' }}
      </button>
    </section>

    <section v-if="addSubject && addLessonType" class="headman-subjects__form" aria-labelledby="headman-add-teacher-title">
      <div class="headman-subjects__form-header">
        <h2 id="headman-add-teacher-title">Добавить преподавателя · {{ addSubject.name }} · {{ labelForType(addLessonType) }}</h2>
        <button class="headman-subjects__secondary" type="button" :disabled="mutationLocked" @click="closeAddTeacher">Отмена</button>
      </div>
      <div class="headman-subjects__search-row">
        <input v-model="addPicker.query" type="search" placeholder="ФИО или табельный номер" :disabled="mutationLocked || offline || readOnly" @keyup.enter="searchAddTeachers">
        <button class="headman-subjects__secondary" type="button" :disabled="mutationLocked || offline || readOnly || addPicker.loading" @click="searchAddTeachers">
          {{ addPicker.loading ? 'Ищем…' : 'Найти' }}
        </button>
      </div>
      <ul v-if="addPicker.results.length > 0" class="headman-subjects__results">
        <li v-for="teacher in addPicker.results" :key="teacher.id">
          <button type="button" :disabled="mutationLocked || offline || readOnly" @click="addTeacher(teacher)">{{ teacherDetail(teacher) }}</button>
        </li>
      </ul>
    </section>
  </main>
</template>
