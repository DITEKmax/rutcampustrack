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
  type HeadmanPendingClosure,
} from './headman-subjects-client'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import MobileToolsSheet from '../../shared/components/MobileToolsSheet.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { parseSubjectEditIntent, createSubjectClosureIntent, subjectEditStorageKey, subjectEditRemoval, type SubjectEditIntent, type SubjectRemovalIntent } from './subject-edit-intent'
import { createSubjectSaveFlow } from './subject-save-flow'
import { subjectEditorAssignments, subjectEditorAssignment } from './subject-editor-state'
import { moscowDate } from '../../domain/homework'
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
  back: []
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
const hasSnapshot = ref(false)
const semesters = ref<readonly HeadmanSemester[]>([])
const activeSemester = computed(() => semesters.value.find((semester) => semester.active) ?? null)
const teachers = reactive<Record<number, HeadmanTeacher>>({})
const pickers = reactive<Record<HeadmanSubjectType, PickerState>>({
  LECTURE: emptyPicker(),
  PRACTICE: emptyPicker(),
  LAB: emptyPicker(),
})
interface EditorTeacher extends HeadmanTeacher { readonly assignmentId?: number; readonly validFrom?: string; readonly validUntilExclusive?: string | null }
const selectedTeachers = reactive<Record<HeadmanSubjectType, EditorTeacher[]>>({
  LECTURE: [],
  PRACTICE: [],
  LAB: [],
})
const selectedTypes = ref<HeadmanSubjectType[]>([])
const formOpen = ref(false)
const editorSubjectId = ref<number | null>(null), selectedSemesterId = ref<number | null>(null)
const editSemester = computed(() => selectedSemesterId.value === null ? activeSemester.value : semesters.value.find((item) => item.id === selectedSemesterId.value) ?? null)
const editorSubject = computed(() => subjects.value.find((item) => item.id === editorSubjectId.value) ?? null)
const pickerType = ref<HeadmanSubjectType | null>(null), pickerTeacher = ref<HeadmanTeacher | null>(null), pickerValidFrom = ref('')
const editorBaseline = ref(''), leaveOpen = ref(false)
const removeCandidate = ref<{ type: HeadmanSubjectType; teacher: EditorTeacher; assignment: HeadmanSubjectAssignment | null } | null>(null)
const editIntent = ref<SubjectEditIntent | null>(null), editStorageError = ref<string | null>(null)
let editStorageSnapshot: string | null = null
const draftRemovals = ref<SubjectRemovalIntent[]>([])
const recoverySubjectId = ref<number | null>(null)
const editorDetail = ref<HeadmanSubject | null>(null), detailLoading = ref(false)
let detailRevision = 0
const editorDirty = computed(() => formOpen.value && editorBaseline.value !== editorSnapshot())
const historicalSemester = computed(() => editSemester.value !== null && !editSemester.value.active)
const editorWritable = computed(() => !historicalSemester.value && !props.offline && !props.readOnly && !denied.value && !mutationLocked.value && !!props.actorUserId && !detailLoading.value && (editorSubjectId.value === null || editorDetail.value !== null) && !editorDetail.value?.pendingClosures?.length)
const draftValidFrom = reactive<Partial<Record<HeadmanSubjectType, Record<number, string>>>>({})
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
let replacementRevision = 0
let disposed = false
type LoadOutcome = 'loaded' | 'stale' | 'skipped' | 'denied' | 'failed'

const replacementSource = computed(() => {
  for (const subject of subjects.value) {
    const assignment = subject.assignments.find((candidate) => candidate.id === replacementAssignmentId.value)
    if (assignment) return { subject, assignment }
  }
  return null
})
const mutationLocked = computed(() => busy.value || replacementBusy.value || replacementStorageBlocked.value || editIntent.value !== null || editStorageError.value !== null)

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

function teacherInitials(teacher: HeadmanTeacher): string { return teacher.fullName.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join('') }
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
    hasSnapshot.value = true
    subjects.value = nextSubjects
    semesters.value = nextSemesters
    if (selectedSemesterId.value === null) selectedSemesterId.value = nextSemesters.find((semester) => semester.active)?.id ?? nextSemesters[0]?.id ?? null
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
  if (editorDirty.value) { error.value = 'Сначала сохрани или отмени несохранённый черновик предмета. Замена преподавателя не удалит его автоматически.'; return }
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

function editorSnapshot(): string {
  return JSON.stringify({ name: subjectName.value, types: selectedTypes.value, semester: editSemester.value?.id,
    teachers: subjectTypes.map((type) => [type, selectedTeachers[type].map((teacher) => [teacher.id, teacher.assignmentId ?? null])]), dates: draftValidFrom, removals: draftRemovals.value })
}
function semesterDay(): string {
  const current = moscowDate(new Date().toISOString())!, semester = editSemester.value
  return semester ? current < semester.dateFrom ? semester.dateFrom : current > semester.dateTo ? semester.dateTo : current : current
}
function assignmentsFor(subject: HeadmanSubject, type: HeadmanSubjectType): readonly HeadmanSubjectAssignment[] {
  return subjectEditorAssignments(subject, type, editSemester.value, moscowDate(new Date().toISOString())!)
}
function resetEditor(subject: HeadmanSubject | null): void {
  editorSubjectId.value = subject?.id ?? null; subjectName.value = subject?.name ?? ''; selectedTypes.value = [...(subject?.lessonTypes ?? [])]
  for (const type of subjectTypes) {
    selectedTeachers[type] = subject ? assignmentsFor(subject, type).map((a) => ({ ...(teachers[a.teacherId] ?? { id: a.teacherId, fullName: teacherLabel(a.teacherId), employeeNumber: null }), assignmentId: a.id, validFrom: a.validFrom, validUntilExclusive: a.validUntilExclusive })) : []
    draftValidFrom[type] = {}
  }
  draftRemovals.value = []; formOpen.value = true; pickerType.value = null; notice.value = null; error.value = null; editorBaseline.value = editorSnapshot()
}
function openCreate(): void { if (editorWritable.value && activeSemester.value) { selectedSemesterId.value = activeSemester.value.id; resetEditor(null) } }
async function refreshOpenSubject(): Promise<void> {
  const api = props.api, actor = props.actorUserId, group = props.groupId, id = editorSubjectId.value, revision = ++detailRevision
  if (!api || !actor || group === null || id === null || props.offline) return
  editorDetail.value = null; detailLoading.value = true
  const current = () => revision === detailRevision && !disposed && api === props.api && actor === props.actorUserId && group === props.groupId && id === editorSubjectId.value && !props.offline
  try {
    const subject = await api.getSubject(id)
    if (!current()) return
    if (subject.id !== id || subject.groupId !== group || subject.pendingClosures?.some((h) => h.subjectId !== id || h.groupId !== group)) throw new Error('Данные восстановления не соответствуют текущему предмету и группе.')
    subjects.value = subjects.value.map((item) => item.id === id ? subject : item)
    if (!editorDirty.value) resetEditor(subject)
    editorDetail.value = subject
  } catch (cause) { if (current()) setError(cause, 'Не удалось прочитать актуальное состояние предмета.') }
  finally { if (current()) detailLoading.value = false }
}
function openSubject(subject: HeadmanSubject): void { if (!mutationLocked.value) { editorDetail.value = null; resetEditor(subject); void refreshOpenSubject() } }
async function finishServerClosure(handle: HeadmanPendingClosure): Promise<void> {
  const subject = editorDetail.value, actor = props.actorUserId, group = props.groupId
  if (!subject || !actor || group === null || historicalSemester.value || props.offline || props.readOnly || denied.value || mutationLocked.value || detailLoading.value) return
  if (editorDirty.value) { error.value = 'Сначала сохрани или отмени черновик предмета. Исходная квитанция снятия сохранена сервером.'; return }
  const plan = createSubjectClosureIntent(actor, group, subject, handle)
  if (!plan) { error.value = 'Квитанция снятия не соответствует текущему предмету и группе.'; return }
  if (!persistEdit(plan)) return
  await saveFlow.reconcile()
  if (saveFlow.canContinue()) await saveFlow.send()
}

function changeSemester(): void { if (formOpen.value && editorSubject.value) resetEditor(editorSubject.value) }
function toggleType(type: HeadmanSubjectType): void {
  if (editorWritable.value) selectedTypes.value = selectedTypes.value.includes(type) ? selectedTypes.value.filter((item) => item !== type) : [...selectedTypes.value, type]
}
function openPicker(type: HeadmanSubjectType = selectedTypes.value[0] ?? 'LECTURE'): void {
  if (!editorWritable.value || !editSemester.value) return
  pickerType.value = type; pickerTeacher.value = null; pickerValidFrom.value = semesterDay(); addPicker.query = ''; addPicker.results = []; void searchAddTeachers()
}
async function searchAddTeachers(): Promise<void> {
  const api = props.api, actor = props.actorUserId, group = props.groupId, revision = ++addPicker.revision
  if (!api || props.offline || !pickerType.value) return
  addPicker.loading = true; error.value = null
  try {
    const page = await api.searchTeachers(addPicker.query, 0)
    if (revision !== addPicker.revision || disposed || api !== props.api || actor !== props.actorUserId || group !== props.groupId || props.offline) return
    addPicker.results = page.items; for (const teacher of page.items) teachers[teacher.id] = teacher
  } catch (cause) { if (revision === addPicker.revision && !disposed) setError(cause, 'Не удалось найти преподавателя.') }
  finally { if (revision === addPicker.revision) addPicker.loading = false }
}
function addTeacherDraft(): void {
  const type = pickerType.value, teacher = pickerTeacher.value, semester = editSemester.value
  if (!editorWritable.value || !type || !teacher || !semester) return
  if (pickerValidFrom.value < semester.dateFrom || pickerValidFrom.value > semester.dateTo || !/^\d{4}-\d{2}-\d{2}$/.test(pickerValidFrom.value)) { error.value = 'Дата назначения должна быть внутри выбранного семестра.'; return }
  if (!selectedTypes.value.includes(type)) selectedTypes.value.push(type)
  if (!selectedTeachers[type].some((item) => item.id === teacher.id)) { selectedTeachers[type].push(teacher); (draftValidFrom[type] ??= {})[teacher.id] = pickerValidFrom.value;  }
  pickerType.value = null; addPicker.revision++; error.value = null
}
function requestRemove(type: HeadmanSubjectType, teacher: EditorTeacher): void {
  removeCandidate.value = { type, teacher, assignment: subjectEditorAssignment(editorSubject.value, type, editSemester.value?.id, teacher.id, teacher.assignmentId) }
}
function removeDraftTeacher(): void {
  const target = removeCandidate.value
  if (!editorWritable.value || !target) return
  if (target.assignment && editorSubject.value) draftRemovals.value.push({ subjectId: editorSubject.value.id, teacherId: target.teacher.id, assignmentId: target.assignment.id, validUntilExclusive: moscowDate(new Date().toISOString())!, requestKey: crypto.randomUUID() })
  selectedTeachers[target.type] = selectedTeachers[target.type].filter((item) => target.teacher.assignmentId === undefined ? item.id !== target.teacher.id : item.assignmentId !== target.teacher.assignmentId); delete draftValidFrom[target.type]?.[target.teacher.id]; removeCandidate.value = null
}
function closeForm(): void { detailRevision++; detailLoading.value = false; editorDetail.value = null; formOpen.value = false; pickerType.value = null; editorSubjectId.value = null }
function back(): boolean {
  if (removeCandidate.value) { removeCandidate.value = null; return true }
  if (leaveOpen.value) { leaveOpen.value = false; return true }
  if (replacementAssignmentId.value !== null) { closeReplacement(); return true }
  if (pickerType.value) { pickerType.value = null; addPicker.revision++; return true }
  if (busy.value || editIntent.value) { error.value = 'Сохранение ещё не завершено. Проверь состояние, чтобы сохранить исходный черновик.'; return true }
  if (formOpen.value) { if (editorDirty.value) leaveOpen.value = true; else closeForm(); return true }
  return false
}
function clickBack(): void { if (!back()) emit('back') }
function editKey(): string | null { return props.actorUserId && props.groupId !== null ? subjectEditStorageKey(props.actorUserId, props.groupId) : null }
function persistEdit(next: SubjectEditIntent | null): boolean {
  const key = editKey(); if (!key) return false
  try {
    if (window.localStorage.getItem(key) !== editStorageSnapshot) throw new Error('Черновик сохранения изменился в другой вкладке. Обнови состояние перед продолжением.')
    const raw = next ? JSON.stringify(next) : null
    if (raw) window.localStorage.setItem(key, raw); else window.localStorage.removeItem(key)
    editStorageSnapshot = raw; editIntent.value = next; return true
  } catch (cause) { editStorageError.value = cause instanceof Error ? cause.message : 'Не удалось сохранить локальный черновик операции.'; return false }
}
function restoreEdit(): void {
  editStorageSnapshot = null; editIntent.value = null; editStorageError.value = null; recoverySubjectId.value = null
  const key = editKey(); if (!key) return
  try {
    const raw = window.localStorage.getItem(key); editStorageSnapshot = raw; if (!raw) return
    const parsed = parseSubjectEditIntent(raw, props.actorUserId!, props.groupId!)
    if (!parsed) { editStorageError.value = 'Локальная запись сохранения повреждена. Изменения заблокированы, чтобы не повторить неизвестную операцию.'; return }
    editIntent.value = parsed
  } catch { editStorageError.value = 'Локальное хранилище недоступно. Сохранение заблокировано.' }
}
async function saveSubject(): Promise<void> {
  const semester = editSemester.value, actor = props.actorUserId, group = props.groupId
  if (!editorWritable.value || !semester || !actor || group === null || !editorDirty.value) return
  const name = subjectName.value.trim(), types = [...selectedTypes.value]
  if (!name || !types.length) { error.value = 'Укажи название и хотя бы один тип занятия.'; return }
  const assignments = types.flatMap((lessonType) => selectedTeachers[lessonType].filter((teacher) => teacher.assignmentId === undefined).map((teacher) => ({ teacherId: teacher.id, semesterId: semester.id, lessonType, validFrom: draftValidFrom[lessonType]?.[teacher.id] ?? semesterDay(), validUntilExclusive: null })))
  const plan: SubjectEditIntent = { version: 1, actorUserId: actor, groupId: group, editId: crypto.randomUUID(), subjectId: editorSubjectId.value, createdId: null, name, lessonTypes: types, assignments, step: 0, unknown: false }
  if (persistEdit({ ...plan, removals: [...draftRemovals.value], pendingAdoptions: [] })) await saveFlow.send(true)
}
const saveFlow = createSubjectSaveFlow({
  api: () => props.api, actor: () => props.actorUserId, group: () => props.groupId,
  available: () => !historicalSemester.value && !props.offline && !props.readOnly && !denied.value && !editStorageError.value && !replacementStorageBlocked.value && !replacementBusy.value && !!props.actorUserId,
  disposed: () => disposed, subjects: () => subjects.value, intent: () => editIntent.value, persist: persistEdit,
  read: async () => await load() === 'loaded', busy: (value) => { busy.value = value }, error: (value) => { error.value = value }, denied: (value) => { denied.value = value },
  completed: (selectedResult) => { closeForm(); notice.value = selectedResult ? 'Выбранный предмет принят как результат. Исходный запрос не повторялся.' : 'Изменения подтверждены сервером.'; error.value = null }, cancelled: () => { closeForm(); notice.value = 'Подтверждённые изменения сохранены. Оставшиеся изменения отменены; предмет можно редактировать заново.'; error.value = null }, failed: (cause) => emit('error', cause),
})
async function reconcileEdit(subjectId: number | null = null, acceptSelectedResult = false): Promise<void> { await saveFlow.reconcile(subjectId, acceptSelectedResult) }
async function continueSave(): Promise<void> { await saveFlow.send() }

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
  () => [props.api, props.groupId, props.actorUserId] as const,
  () => {
    loadRevision += 1
    detailRevision++; detailLoading.value = false; editorDetail.value = null
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
    hasSnapshot.value = false
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
    editorSubjectId.value = null; selectedSemesterId.value = null; draftRemovals.value = []; saveFlow.reset();
    restoreReplacementIntents(); restoreEdit(); pickerType.value = null; removeCandidate.value = null; leaveOpen.value = false
    void load()
  },
  { immediate: true, flush: 'sync' },
)
watch(() => props.offline, (offline) => { detailRevision++; detailLoading.value = false; loadRevision++; loading.value = false; addPicker.revision++; replacementPicker.revision++; replacementRevision++; replacementBusy.value = false; replacementPicker.loading = false; if (!offline) { restoreReplacementIntents(); void load(); if (formOpen.value && editorSubjectId.value !== null) void refreshOpenSubject() } }, { flush: 'sync' })
defineExpose({ back })
onBeforeUnmount(() => {
  disposed = true; detailRevision++; saveFlow.reset()
  loadRevision += 1
  replacementRevision += 1
  replacementPicker.revision += 1
  if (typeof window !== 'undefined') window.removeEventListener('storage', onReplacementStorageChange)
})
</script>

<template>
  <main
    class="headman-subjects"
    :data-subject-view="removeCandidate ? 'remove' : pickerType ? 'picker' : replacementSource ? 'replacement' : formOpen ? 'editor' : 'list'"
    :aria-busy="loading || busy || detailLoading"
    :data-subject-detail-ready="editorSubjectId === null || editorDetail !== null"
  >
    <header class="headman-subjects__header">
      <button
        type="button"
        class="headman-subjects__back"
        aria-label="Назад"
        @click="clickBack"
      >
        <MobileIcon name="back" />
      </button><h1>{{ removeCandidate ? 'Убрать преподавателя' : pickerType ? 'Добавить преподавателя' : replacementSource ? 'Заменить преподавателя' : formOpen ? editorSubject?.name ?? 'Новый предмет' : 'Предметы' }}</h1>
    </header>
    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Изменения доступны онлайн. Несохранённый черновик остаётся на этом экране."
    />
    <StudentWarningBlock
      v-if="readOnly || denied"
      title="Только просмотр"
      message="Для сохранения нужен действующий доступ старосты к этой группе."
    />
    <StudentWarningBlock
      v-if="error"
      title="Сохранение или загрузка не завершены"
      :message="error"
      severity="error"
      :action-label="!hasSnapshot || (formOpen && editorSubjectId !== null && !editorDetail) ? 'Повторить загрузку' : ''"
      :action-disabled="offline || loading || busy || detailLoading"
      @action="formOpen && editorSubjectId !== null ? refreshOpenSubject() : load()"
    />
    <StudentWarningBlock
      v-if="editStorageError"
      title="Локальная запись недоступна"
      :message="editStorageError"
      severity="error"
    />
    <p
      v-if="notice"
      class="headman-subjects__notice"
      role="status"
    >
      {{ notice }}
    </p>
    <section
      v-if="editorDetail?.pendingClosures?.length && !editIntent"
      class="headman-subjects__recovery"
    >
      <h2>Незавершённое снятие преподавателя</h2>
      <p>Продолжение использует исходную серверную квитанцию и дату. Будущие пары и история сохранятся.</p>
      <button
        v-for="handle in editorDetail.pendingClosures"
        :key="handle.operationId"
        type="button"
        data-server-closure
        :disabled="offline || readOnly || denied || mutationLocked || detailLoading || editorDirty"
        @click="finishServerClosure(handle)"
      >
        Завершить снятие
      </button>
    </section>
    <section
      v-if="editIntent"
      class="headman-subjects__recovery"
    >
      <h2>Незавершённое сохранение</h2>
      <label
        v-if="editIntent.subjectId === null && editIntent.createdId === null && editIntent.unknown"
        class="headman-subjects__field"
      ><span>Выбери конкретный сохранённый предмет для проверки</span><select v-model="recoverySubjectId"><option :value="null">Предмет не выбран</option><option
        v-for="subject in subjects"
        :key="subject.id"
        :value="subject.id"
      >{{ subject.name }} · № {{ subject.id }}</option></select></label>
      <button
        v-if="editIntent.subjectId === null && editIntent.createdId === null && editIntent.unknown"
        type="button"
        :disabled="!recoverySubjectId || offline || busy || readOnly"
        @click="reconcileEdit(recoverySubjectId, editIntent.assignments.length === 0)"
      >
        {{ editIntent.assignments.length === 0 ? 'Использовать этот предмет' : 'Проверить выбранный предмет' }}
      </button>
      <button
        v-if="editIntent.rejected"
        type="button"
        data-action="cancel"
        :disabled="offline || busy || loading || readOnly || denied || !!editStorageError"
        @click="saveFlow.cancelRejected()"
      >
        Отменить оставшиеся изменения
      </button>
      <p>{{ editIntent.name }} · подтверждённых шагов: {{ editIntent.step }}</p><p>{{ editIntent.unknown ? 'Ответ на запись ещё не подтверждён. Повтор неизвестной записи заблокирован.' : 'Остались изменения, которые ещё не были отправлены.' }}</p><button
        type="button"
        :disabled="offline || busy || loading"
        @click="reconcileEdit()"
      >
        Проверить состояние
      </button><button
        v-if="!editIntent.rejected && (!editIntent.unknown || subjectEditRemoval(editIntent))"
        type="button"
        :disabled="offline || busy || loading || readOnly || denied || !!editStorageError || !saveFlow.canContinue()"
        @click="continueSave"
      >
        Продолжить сохранение
      </button>
    </section>
    <p
      v-if="loading"
      role="status"
    >
      Загружаем предметы…
    </p>
    <template v-if="!formOpen && !replacementSource && !removeCandidate && hasSnapshot">
      <p class="headman-subjects__count">
        {{ subjects.length }} предметов
      </p>
      <label class="headman-subjects__semester"><select
        v-model="selectedSemesterId"
        aria-label="Семестр"
        :disabled="loading || mutationLocked"
        @change="changeSemester"
      ><option
        v-for="semester in semesters"
        :key="semester.id"
        :value="semester.id"
      >{{ semester.name }}</option></select><MobileIcon name="chevron-down" /></label>
      <button
        class="headman-subjects__primary headman-subjects__add"
        type="button"
        :disabled="!editorWritable || !activeSemester"
        @click="openCreate"
      >
        <span aria-hidden="true">+</span>Добавить предмет
      </button>
      <div class="headman-subjects__list">
        <button
          v-for="subject in subjects"
          :key="subject.id"
          class="headman-subjects__subject"
          type="button"
          :data-subject-id="subject.id"
          :disabled="mutationLocked"
          @click="openSubject(subject)"
        >
          <span><strong>{{ subject.name }}</strong><small><MobileIcon name="profile" />{{ [...new Set(subject.lessonTypes.flatMap((type) => assignmentsFor(subject, type).map((a) => teacherLabel(a.teacherId))))].join(', ') || 'Преподаватель не назначен' }}</small></span><MobileIcon name="chevron-down" />
        </button>
      </div>
      <p v-if="!loading && !subjects.length">
        Предметов пока нет.
      </p><button
        v-if="!subjects.length || error"
        class="headman-subjects__secondary"
        type="button"
        :disabled="offline || loading"
        @click="load"
      >
        Обновить список
      </button>
    </template>
    <template v-if="formOpen && !pickerType && !removeCandidate && !replacementSource">
      <p
        v-if="historicalSemester"
        class="headman-subjects__hint"
        role="note"
      >
        Исторический семестр · только просмотр
      </p>
      <label class="headman-subjects__name"><span>Название</span><input
        v-model="subjectName"
        type="text"
        :disabled="!editorWritable"
        required
      ></label>
      <h2 class="headman-subjects__section-title">
        Типы занятий и преподаватели
      </h2>
      <div class="headman-subjects__types">
        <button
          v-for="type in subjectTypes"
          :key="type"
          type="button"
          :aria-pressed="selectedTypes.includes(type)"
          :disabled="!editorWritable"
          @click="toggleType(type)"
        >
          {{ type === 'LAB' ? 'Лаб.' : labelForType(type) }}
        </button>
      </div>
      <section
        v-for="type in selectedTypes"
        :key="type"
        class="headman-subjects__assignment"
      >
        <h3>{{ labelForType(type) }}</h3><button
          v-for="teacher in selectedTeachers[type]"
          :key="teacher.assignmentId ?? teacher.id"
          class="headman-subjects__teacher"
          type="button"
          :disabled="!editorWritable"
          @click="requestRemove(type, teacher)"
        >
          <MobileIcon name="profile" /><span>{{ teacher.fullName }}<small v-if="teacher.validFrom && teacher.validFrom > semesterDay()">С {{ teacher.validFrom }}</small></span><MobileIcon name="chevron-down" />
        </button><button
          v-if="!selectedTeachers[type].length"
          class="headman-subjects__teacher"
          type="button"
          :disabled="!editorWritable"
          @click="openPicker(type)"
        >
          <MobileIcon name="profile" /><span>Не назначен</span><MobileIcon name="chevron-down" />
        </button>
      </section>
      <button
        class="headman-subjects__secondary headman-subjects__add"
        type="button"
        :disabled="!editorWritable || !editSemester"
        @click="openPicker()"
      >
        <span aria-hidden="true">+</span>Добавить преподавателя
      </button>
      <footer class="headman-subjects__footer">
        <button
          type="button"
          class="headman-subjects__primary"
          data-action="confirm"
          :disabled="!editorWritable || !editorDirty"
          @click="saveSubject"
        >
          {{ busy ? 'Сохраняем…' : 'Сохранить изменения' }}
        </button>
      </footer>
    </template>
    <template v-if="pickerType && !removeCandidate">
      <section class="headman-subjects__context-card">
        <h2>{{ subjectName || 'Новый предмет' }}</h2><p>{{ editSemester?.name }}</p>
      </section><h2 class="headman-subjects__section-title">
        Тип занятия
      </h2>
      <div class="headman-subjects__types">
        <button
          v-for="type in subjectTypes"
          :key="type"
          type="button"
          :aria-pressed="pickerType === type"
          @click="pickerType = type; pickerTeacher = null"
        >
          {{ type === 'LAB' ? 'Лаб.' : labelForType(type) }}
        </button>
      </div>
      <label class="headman-subjects__search"><MobileIcon name="search" /><input
        v-model="addPicker.query"
        type="search"
        aria-label="ФИО или табельный номер"
        placeholder="ФИО или табельный номер"
        :disabled="offline || !editorWritable"
        @input="searchAddTeachers"
        @keyup.enter="searchAddTeachers"
      ></label>
      <p
        v-if="addPicker.loading"
        role="status"
      >
        Ищем преподавателей…
      </p><div class="headman-subjects__teacher-options">
        <button
          v-for="teacher in addPicker.results"
          :key="teacher.id"
          type="button"
          :aria-pressed="pickerTeacher?.id === teacher.id"
          :disabled="!editorWritable || selectedTeachers[pickerType].some((item) => item.id === teacher.id)"
          @click="pickerTeacher = teacher"
        >
          <span
            class="headman-subjects__avatar"
            aria-hidden="true"
          >{{ teacherInitials(teacher) }}</span><span><strong>{{ teacher.fullName }}</strong><small>{{ teacher.employeeNumber ? `Табельный № ${teacher.employeeNumber}` : 'Табельный номер недоступен' }}</small></span><span
            class="headman-subjects__choice"
            aria-hidden="true"
          />
        </button>
      </div><p v-if="!addPicker.loading && !addPicker.results.length">
        Преподаватели не найдены.
      </p>
      <label class="headman-subjects__field"><span>Начало назначения</span><input
        v-model="pickerValidFrom"
        type="date"
        :min="editSemester?.dateFrom"
        :max="editSemester?.dateTo"
        :disabled="!editorWritable"
      ></label>
      <footer class="headman-subjects__footer">
        <button
          type="button"
          class="headman-subjects__primary"
          :disabled="!editorWritable || !pickerTeacher"
          @click="addTeacherDraft"
        >
          Добавить преподавателя
        </button>
      </footer>
    </template>
    <template v-if="removeCandidate">
      <section class="headman-subjects__context-card">
        <h2>{{ subjectName }}</h2><p>{{ labelForType(removeCandidate.type) }}</p>
      </section><section class="headman-subjects__context-card">
        <h2>{{ removeCandidate.teacher.fullName }}</h2><p>{{ removeCandidate.teacher.employeeNumber ? `Табельный № ${removeCandidate.teacher.employeeNumber}` : 'Табельный номер недоступен' }}</p>
      </section>
      <section class="headman-subjects__context-card">
        <h2>Убрать преподавателя?</h2><p>{{ removeCandidate.assignment ? 'Снятие будет отправлено после сохранения предмета. Будущие пары сохранятся без преподавателя; история посещаемости останется доступной.' : 'Преподаватель будет убран из черновика. Связь ещё не сохранена на сервере.' }}</p>
      </section>
      <footer class="headman-subjects__footer">
        <button
          type="button"
          class="headman-subjects__primary"
          data-action="delete"
          :disabled="!editorWritable"
          @click="removeDraftTeacher"
        >
          Убрать преподавателя
        </button><button
          v-if="removeCandidate.assignment && activeSemester?.id === removeCandidate.assignment.semesterId"
          class="headman-subjects__secondary"
          type="button"
          :disabled="!editorWritable"
          @click="openReplacement(removeCandidate.assignment); removeCandidate = null"
        >
          Заменить преподавателя
        </button>
      </footer>
    </template>
    <MobileToolsSheet
      v-model:open="leaveOpen"
      icon-close
      title="Выйти без сохранения?"
    >
      <p>Изменения названия, типов и выбранных преподавателей будут потеряны.</p><div class="headman-subjects__dialog-actions">
        <button
          type="button"
          data-action="cancel"
          @click="leaveOpen = false"
        >
          Отмена
        </button><button
          type="button"
          data-action="confirm"
          @click="leaveOpen = false; closeForm()"
        >
          Выйти
        </button>
      </div>
    </MobileToolsSheet>
    <section
      v-if="replacementStorageError"
      class="headman-subjects__replacement"
      aria-labelledby="headman-replacement-storage-title"
    >
      <h2 id="headman-replacement-storage-title">
        Локальная запись замены
      </h2>
      <p
        class="headman-subjects__state headman-subjects__state--error"
        role="alert"
      >
        {{ replacementStorageError }}
      </p>
      <button
        v-if="corruptedReplacementKeys.length > 0"
        class="headman-subjects__secondary"
        type="button"
        :disabled="replacementBusy"
        @click="clearCorruptedReplacementStorage"
      >
        Очистить локальную запись
      </button>
    </section>
    <section
      v-if="replacementIntents.length > 0"
      class="headman-subjects__replacement"
      aria-labelledby="headman-replacement-pending-title"
      :aria-busy="replacementBusy"
    >
      <h2 id="headman-replacement-pending-title">
        Незавершённые замены преподавателя
      </h2>
      <article
        v-for="intent in replacementIntents"
        :key="intent.requestKey"
        class="headman-subjects__replacement-operation"
      >
        <p class="headman-subjects__replacement-summary">
          {{ intent.subjectName }} · {{ storedLessonTypeLabel(intent.lessonType) }}:
          {{ teacherLabel(intent.sourceTeacherId) }} → {{ teacherLabel(Number(intent.replacementTeacherId)) }}, с {{ intent.effectiveFrom }}
        </p>
        <p
          class="headman-subjects__state"
          role="status"
          aria-live="polite"
        >
          {{ replacementStatusText(intent) }}
        </p>
        <p
          v-if="replacementError && replacementErrorRequestKey === intent.requestKey"
          class="headman-subjects__state headman-subjects__state--error"
          role="alert"
        >
          {{ replacementError }}
        </p>
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
    <section
      v-if="replacementSource && actorUserId && !hasPendingReplacement(replacementSource.assignment.id)"
      class="headman-subjects__replacement"
      aria-labelledby="headman-replacement-title"
      :aria-busy="replacementBusy"
    >
      <div class="headman-subjects__form-header">
        <h2 id="headman-replacement-title">
          Заменить преподавателя
        </h2>
        <button
          class="headman-subjects__secondary"
          type="button"
          :disabled="replacementBusy"
          data-action="cancel"
          @click="closeReplacement"
        >
          Отмена
        </button>
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
          <button
            class="headman-subjects__secondary"
            type="button"
            :disabled="mutationLocked || offline || readOnly || denied || replacementPicker.loading"
            @click="searchReplacementTeachers"
          >
            {{ replacementPicker.loading ? 'Ищем…' : 'Найти' }}
          </button>
        </div>
        <p
          v-if="replacementPicker.loading"
          class="headman-subjects__state"
          aria-live="polite"
        >
          Ищем активных преподавателей…
        </p>
        <ul
          v-if="replacementPicker.results.length > 0"
          class="headman-subjects__results"
        >
          <li
            v-for="teacher in replacementPicker.results"
            :key="teacher.id"
          >
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
        <p
          v-if="replacementTeacher"
          class="headman-subjects__replacement-summary"
        >
          Выбран: {{ teacherDetail(replacementTeacher) }}
        </p>
      </fieldset>
      <label class="headman-subjects__field">
        <span>Дата начала замены</span>
        <input
          v-model="replacementEffectiveFrom"
          type="date"
          required
          :aria-describedby="replacementError && replacementErrorRequestKey === null ? 'headman-replacement-error' : undefined"
          :disabled="mutationLocked || offline || readOnly || denied"
        >
      </label>
      <p
        v-if="replacementError && replacementErrorRequestKey === null"
        id="headman-replacement-error"
        class="headman-subjects__state headman-subjects__state--error"
        role="alert"
      >
        {{ replacementError }}
      </p>
      <p
        v-if="replacementTeacher && replacementEffectiveFrom"
        class="headman-subjects__replacement-summary"
        role="note"
      >
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
  </main>
</template>
