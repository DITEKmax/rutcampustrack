<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type {
  HeadmanRequestBucket,
  HeadmanRequestAttachment,
  HeadmanRequestDetail,
  HeadmanRequestKind,
  HeadmanRequestPage,
  HeadmanRequestSummary,
  HeadmanRequestsApi,
} from './headman-requests-client'
import { HeadmanRequestsApiError } from './headman-requests-client'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import type { RequestAttachmentViewState } from '../requests/types'
import {
  openRequestAttachmentPopup,
  runRequestAttachmentDownload,
  runRequestAttachmentOpen,
  type RequestAttachmentDownloadDependencies,
  type RequestAttachmentPopup,
} from '../requests/request-attachment-action'
import './headman-requests-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanRequestsApi | null
  assistantPermissions?: readonly HeadmanAssistantPermission[] | null
  offline?: boolean
  readOnly?: boolean
  showBack?: boolean
}>(), {
  assistantPermissions: null,
  offline: false,
  readOnly: false,
  showBack: true,
})

const emit = defineEmits<{
  back: []
  error: [cause: unknown]
}>()

const bucket = ref<HeadmanRequestBucket>('OPEN')
const type = ref<HeadmanRequestKind | ''>('')
const studentName = ref('')
const coverageDateFrom = ref('')
const coverageDateTo = ref('')
const page = ref<HeadmanRequestPage | null>(null)
const currentPage = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const expanded = ref(new Set<string>())
const details = ref<Record<string, HeadmanRequestDetail>>({})
const rejectReasons = ref<Record<string, string>>({})
const busyId = ref<string | null>(null)
const decisionError = ref<Record<string, string>>({})
const detailErrors = ref<Record<string, string>>({})
let loadRevision = 0
let attachmentGeneration = 0
let disposed = false
const attachmentStates = ref<Record<string, RequestAttachmentViewState>>({})
const unavailableAttachments = ref(new Set<string>())
const attachmentUrls = new Map<string, ReturnType<typeof setTimeout>>()
const pendingPopups = new Set<RequestAttachmentPopup>()
const detailGenerations = new Map<string, number>()
const attachmentNow = ref(Date.now())
let expiryTimer: ReturnType<typeof setTimeout> | null = null

function releaseAttachmentUrl(url: string): void {
  const timer = attachmentUrls.get(url)
  if (timer !== undefined) clearTimeout(timer)
  attachmentUrls.delete(url)
  try { URL.revokeObjectURL(url) } catch { /* Continue releasing the remaining browser resources. */ }
}

function releaseAttachmentResources(): void {
  for (const url of attachmentUrls.keys()) releaseAttachmentUrl(url)
  for (const popup of pendingPopups) closeAttachmentPopup(popup)
}

function closeAttachmentPopup(popup: RequestAttachmentPopup): void {
  pendingPopups.delete(popup)
  try { popup.close?.() } catch { /* The user may already have closed the target. */ }
}

function cancelAttachmentActions(): void {
  attachmentGeneration += 1
  releaseAttachmentResources()
  attachmentStates.value = Object.fromEntries(Object.entries(attachmentStates.value)
    .filter(([, state]) => state.status !== 'pending'))
}

function resetAttachments(): void {
  cancelAttachmentActions()
  attachmentStates.value = {}
  unavailableAttachments.value = new Set()
  if (expiryTimer !== null) clearTimeout(expiryTimer)
  expiryTimer = null
}

function scheduleAttachmentExpiry(): void {
  if (expiryTimer !== null) clearTimeout(expiryTimer)
  expiryTimer = null
  attachmentNow.value = Date.now()
  if (disposed) return
  const expiry = Math.min(...Object.values(details.value).flatMap((detail) => detail.attachments)
    .map((attachment) => Date.parse(attachment.expiresAt ?? ''))
    .filter((timestamp) => Number.isFinite(timestamp) && timestamp > attachmentNow.value))
  if (!Number.isFinite(expiry)) return
  expiryTimer = setTimeout(() => {
    expiryTimer = null
    cancelAttachmentActions()
    scheduleAttachmentExpiry()
  }, Math.min(expiry - attachmentNow.value, 2_147_483_647))
}

const pageCount = () => page.value?.totalPages ?? 0

function apiFailureMessage(cause: unknown, fallback: string, forbidden: string): string {
  if (cause instanceof HeadmanRequestsApiError && cause.response.status === 401) {
    return 'Сессия истекла. Повтори вход и открой заявки снова.'
  }
  if (cause instanceof HeadmanRequestsApiError && cause.response.status === 403) return forbidden
  return cause instanceof Error ? cause.message : fallback
}

function canManageExcuses(): boolean {
  return props.assistantPermissions === null || props.assistantPermissions.includes('MANAGE_EXCUSES')
}

async function load(): Promise<void> {
  const revision = ++loadRevision
  resetAttachments()
  expanded.value = new Set()
  details.value = {}
  detailErrors.value = {}
  detailGenerations.clear()
  page.value = null
  error.value = null
  if (!canManageExcuses()) {
    error.value = 'Нет доступа к заявкам этой группы.'
    loading.value = false
    return
  }
  if (props.offline) {
    page.value = null
    loading.value = false
    return
  }
  if (!props.api) {
    page.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    const next = await props.api.list({
      bucket: bucket.value,
      page: currentPage.value,
      size: 20,
      type: type.value || null,
      studentName: studentName.value || null,
      coverageDateFrom: coverageDateFrom.value || null,
      coverageDateTo: coverageDateTo.value || null,
    })
    if (revision !== loadRevision) return
    page.value = next
  } catch (cause) {
    if (revision !== loadRevision) return
    error.value = apiFailureMessage(cause, 'Не удалось загрузить заявки.', 'Нет доступа к заявкам этой группы.')
    emit('error', cause)
  } finally {
    if (revision === loadRevision) loading.value = false
  }
}

function setBucket(next: HeadmanRequestBucket): void {
  if (bucket.value === next) return
  bucket.value = next
  currentPage.value = 0
  void load()
}

function applyFilters(): void {
  currentPage.value = 0
  void load()
}

function changePage(next: number): void {
  if (next < 0 || next >= pageCount() || next === currentPage.value) return
  currentPage.value = next
  void load()
}

async function toggle(item: HeadmanRequestSummary): Promise<void> {
  cancelAttachmentActions()
  const detailGeneration = (detailGenerations.get(item.id) ?? 0) + 1
  detailGenerations.set(item.id, detailGeneration)
  const next = new Set(expanded.value)
  if (next.has(item.id)) {
    next.delete(item.id)
    expanded.value = next
    return
  }
  next.add(item.id)
  expanded.value = next
  detailErrors.value = { ...detailErrors.value, [item.id]: '' }
  const api = props.api
  const revision = loadRevision
  const isCurrent = () => !disposed && props.api === api && revision === loadRevision
    && detailGenerations.get(item.id) === detailGeneration && expanded.value.has(item.id) && canManageExcuses()
  if (details.value[item.id] || !api) return
  try {
    const detail = await api.get(item.id)
    if (!isCurrent()) return
    details.value = { ...details.value, [item.id]: detail }
  } catch (cause) {
    if (!isCurrent()) return
    const currentExpanded = new Set(expanded.value)
    currentExpanded.delete(item.id)
    expanded.value = currentExpanded
    detailErrors.value = { ...detailErrors.value, [item.id]: apiFailureMessage(
      cause,
      'Не удалось открыть детали.',
      'Нет доступа к деталям этой заявки.',
    ) }
    emit('error', cause)
  }
}

async function decide(item: HeadmanRequestSummary, decision: 'APPROVED' | 'REJECTED'): Promise<void> {
  if (!props.api || props.offline || props.readOnly || busyId.value !== null) return
  const reason = rejectReasons.value[item.id]?.trim() || null
  if (decision === 'REJECTED' && !reason) {
    decisionError.value = { ...decisionError.value, [item.id]: 'Укажи причину отклонения.' }
    return
  }
  busyId.value = item.id
  decisionError.value = { ...decisionError.value, [item.id]: '' }
  try {
    const detail = await props.api.decide(item.id, decision, reason)
    details.value = { ...details.value, [item.id]: detail }
    await load()
  } catch (cause) {
    const message = cause instanceof HeadmanRequestsApiError && cause.response.status === 409
      ? 'Заявка уже обработана другим оператором. Обнови список.'
      : apiFailureMessage(cause, 'Не удалось принять решение.', 'Недостаточно прав для решения заявки.')
    decisionError.value = { ...decisionError.value, [item.id]: message }
    emit('error', cause)
  } finally {
    busyId.value = null
  }
}

function attachmentKey(requestId: string, attachmentId: string): string {
  return `${requestId}:${attachmentId}`
}

function attachmentExpired(attachment: HeadmanRequestAttachment): boolean {
  return attachment.state === 'EXPIRED'
    || Boolean(attachment.expiresAt && Date.parse(attachment.expiresAt) <= attachmentNow.value)
}

function attachmentDisabled(requestId: string, attachment: HeadmanRequestAttachment): boolean {
  return props.offline || !canManageExcuses() || attachmentExpired(attachment)
    || unavailableAttachments.value.has(attachmentKey(requestId, attachment.id))
    || attachmentStates.value[attachmentKey(requestId, attachment.id)]?.status === 'pending'
}

function attachmentErrorMessage(cause: unknown): string {
  if (cause instanceof HeadmanRequestsApiError) {
    if (cause.response.status === 410) return 'Вложение больше недоступно. Срок хранения истёк или файл удалён.'
    if (cause.response.status === 404) return 'Вложение больше недоступно. Обнови список заявок.'
    if (cause.response.status >= 500) return 'Не удалось загрузить вложение. Повтори попытку позже.'
    return apiFailureMessage(cause, 'Не удалось загрузить вложение.', 'Нет доступа к вложению этой заявки. Обнови список заявок.')
  }
  return 'Не удалось загрузить вложение. Проверь соединение и повтори.'
}

function attachmentDependencies(item: HeadmanRequestSummary, attachment: HeadmanRequestAttachment): RequestAttachmentDownloadDependencies | null {
  // A delayed/throttled timer must still make an expired click visibly unavailable.
  attachmentNow.value = Date.now()
  if (attachmentExpired(attachment)) {
    cancelAttachmentActions()
    scheduleAttachmentExpiry()
    return null
  }
  const api = props.api
  if (!api || disposed || attachmentDisabled(item.id, attachment) || typeof window === 'undefined') return null
  const generation = attachmentGeneration
  const key = attachmentKey(item.id, attachment.id)
  return {
    ownerIdentity: item.id,
    ownerGeneration: generation,
    currentOwnerIdentity: () => props.api === api && !props.offline && canManageExcuses()
      && expanded.value.has(item.id) && page.value?.content.some((entry) => entry.id === item.id) ? item.id : null,
    currentOwnerGeneration: () => attachmentGeneration,
    isDisposed: () => disposed,
    download: () => api.downloadAttachment(item.id, attachment.id),
    createObjectUrl: (blob) => {
      const url = URL.createObjectURL(blob)
      attachmentUrls.set(url, setTimeout(() => releaseAttachmentUrl(url), 60_000))
      return url
    },
    releaseObjectUrl: releaseAttachmentUrl,
    scheduleRelease: () => { /* Every created URL already has a bounded lifetime. */ },
    save: (url) => {
      const link = document.createElement('a')
      link.href = url
      link.download = attachment.name || 'attachment'
      link.click()
    },
    setState: (state) => { attachmentStates.value = { ...attachmentStates.value, [key]: state } },
    errorMessage: attachmentErrorMessage,
    onError: (cause) => {
      cancelAttachmentActions()
      if (cause instanceof HeadmanRequestsApiError && [401, 403, 404, 410].includes(cause.response.status)) {
        const ids = [401, 403].includes(cause.response.status)
          ? details.value[item.id]?.attachments.map((entry) => attachmentKey(item.id, entry.id)) ?? [key]
          : [key]
        unavailableAttachments.value = new Set([...unavailableAttachments.value, ...ids])
      }
      emit('error', cause)
    },
  }
}

function openAttachment(item: HeadmanRequestSummary, attachment: HeadmanRequestAttachment): void {
  const deps = attachmentDependencies(item, attachment)
  if (!deps) return
  runRequestAttachmentOpen({
    ...deps,
    openPopup: () => openRequestAttachmentPopup(window),
    navigate: (popup, url) => { popup.location.href = url },
    closePopup: closeAttachmentPopup,
    onPopupOpened: (popup) => pendingPopups.add(popup),
    onPopupNavigated: (popup) => pendingPopups.delete(popup),
  })
}

function downloadAttachment(item: HeadmanRequestSummary, attachment: HeadmanRequestAttachment): void {
  const deps = attachmentDependencies(item, attachment)
  if (deps) void runRequestAttachmentDownload(deps)
}

function statusLabel(status: HeadmanRequestSummary['status']): string {
  return { PENDING: 'Ожидает решения', APPROVED: 'Одобрено', REJECTED: 'Отклонено', CANCELLED: 'Отменено' }[status]
}

function kindLabel(kind: HeadmanRequestSummary['kind']): string {
  return kind === 'EXCUSE' ? 'Пропуск' : 'Опоздание'
}

function dateLabel(value: string | null): string {
  if (!value) return '—'
  const dateOnly = /^\d{4}-\d{2}-\d{2}$/.test(value)
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const options: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }
  if (dateOnly) options.timeZone = 'UTC'
  return new Intl.DateTimeFormat('ru-RU', options).format(date)
}

function lessonStatusLabel(value: string | null): string {
  return value === 'PRESENT' ? 'Присутствовал' : value === 'EXCUSED' ? 'Уважительная' : value === 'ABSENT' ? 'Отсутствовал' : value || 'Не отмечено'
}

function detailFor(id: string): HeadmanRequestDetail {
  return details.value[id]!
}

watch(
  () => [props.api, props.offline, props.assistantPermissions?.join(',')] as const,
  () => { void load() },
  { immediate: true },
)

watch(details, scheduleAttachmentExpiry, { flush: 'sync' })
onBeforeUnmount(() => { disposed = true; loadRevision += 1; resetAttachments() })
</script>

<template>
  <main class="headman-requests" aria-labelledby="headman-requests-title">
    <header class="headman-requests__header">
      <button v-if="showBack" class="headman-requests__back" type="button" @click="emit('back')">
        Назад
      </button>
      <div>
        <p class="headman-requests__eyebrow">Староста · заявки</p>
        <h1 id="headman-requests-title">Заявки группы</h1>
        <p class="headman-requests__hint">Решение применяется ко всей заявке.</p>
      </div>
    </header>

    <template v-if="api && !offline">
      <div class="headman-requests__tabs" role="tablist" aria-label="Раздел заявок">
      <button type="button" role="tab" :aria-selected="bucket === 'OPEN'" :data-selected="bucket === 'OPEN'" @click="setBucket('OPEN')">
        Входящие
      </button>
      <button type="button" role="tab" :aria-selected="bucket === 'ARCHIVE'" :data-selected="bucket === 'ARCHIVE'" @click="setBucket('ARCHIVE')">
        Архив
      </button>
    </div>

    <form class="headman-requests__filters" @submit.prevent="applyFilters">
      <label>
        <span>Тип</span>
        <select v-model="type">
          <option value="">Все типы</option>
          <option value="EXCUSE">Пропуски</option>
          <option value="LATE_CHECKIN">Опоздания</option>
        </select>
      </label>
      <label>
        <span>ФИО</span>
        <input v-model="studentName" type="search" placeholder="Начни вводить фамилию">
      </label>
      <label>
        <span>Период от</span>
        <input v-model="coverageDateFrom" type="date">
      </label>
      <label>
        <span>Период до</span>
        <input v-model="coverageDateTo" type="date">
      </label>
      <button class="headman-requests__filter-submit" type="submit" :disabled="loading || offline">
        Применить
      </button>
      </form>
    </template>

    <p v-if="offline" class="headman-requests__state" role="status">Заявки доступны только онлайн.</p>
    <p v-else-if="!api" class="headman-requests__state headman-requests__state--error" role="alert">
      Заявки недоступны: источник данных не подключён.
    </p>
    <p v-else-if="loading" class="headman-requests__state" role="status" aria-live="polite">Загружаем заявки…</p>
    <p v-else-if="error" class="headman-requests__state headman-requests__state--error" role="alert">{{ error }}</p>
    <p v-else-if="page && page.content.length === 0" class="headman-requests__state">Заявок по заданным условиям нет.</p>

    <section v-else-if="page" class="headman-requests__list" aria-live="polite">
      <article v-for="item in page.content" :key="item.id" class="headman-requests__card">
        <header class="headman-requests__card-header">
          <div>
            <p class="headman-requests__card-kind">{{ kindLabel(item.kind) }}</p>
            <h2>{{ item.studentName || 'Студент без имени' }}</h2>
          </div>
          <span class="headman-requests__status" :data-status="item.status">{{ statusLabel(item.status) }}</span>
        </header>
        <dl class="headman-requests__summary">
          <div><dt>Причина</dt><dd>{{ item.reason || '—' }}</dd></div>
          <div><dt>Период</dt><dd>{{ dateLabel(item.coverageStart) }} — {{ dateLabel(item.coverageEnd) }}</dd></div>
          <div><dt>Пары</dt><dd>{{ item.lessonCount }}<span v-if="item.alreadyMarkedCount"> · {{ item.alreadyMarkedCount }} уже отмечено</span></dd></div>
        </dl>
        <p v-if="item.comment" class="headman-requests__comment">{{ item.comment }}</p>
        <button class="headman-requests__details-button" type="button" :aria-expanded="expanded.has(item.id)" @click="toggle(item)">
          {{ expanded.has(item.id) ? 'Скрыть детали' : 'Открыть детали' }}
        </button>
        <p v-if="detailErrors[item.id]" class="headman-requests__state headman-requests__state--error" role="alert">{{ detailErrors[item.id] }}</p>

        <div v-if="expanded.has(item.id)" class="headman-requests__details">
          <p v-if="!details[item.id]" class="headman-requests__state">Загружаем детали…</p>
          <template v-else>
            <section>
              <h3>Занятия</h3>
              <ul class="headman-requests__lessons">
                <li v-for="lesson in detailFor(item.id).lessons" :key="`${item.id}-${lesson.lessonId}`">
                  <strong>{{ dateLabel(lesson.date) }} · {{ lesson.lessonNumber || '—' }} пара</strong>
                  <span>{{ lesson.subjectName || 'Предмет не указан' }} · {{ lessonStatusLabel(lesson.attendanceStatus) }}</span>
                </li>
              </ul>
            </section>
            <section v-if="detailFor(item.id).attachments.length">
              <h3>Вложения</h3>
              <ul class="headman-requests__attachments">
                <li v-for="attachment in detailFor(item.id).attachments" :key="attachment.id">
                  <p>{{ attachment.name || 'Файл' }} · {{ attachment.size }} Б</p>
                  <p v-if="attachmentExpired(attachment)" class="headman-requests__state" role="status">Срок хранения вложения истёк. Файл больше недоступен.</p>
                  <template v-else>
                    <button type="button" :disabled="attachmentDisabled(item.id, attachment)" @click="openAttachment(item, attachment)">Открыть</button>
                    <button type="button" :disabled="attachmentDisabled(item.id, attachment)" @click="downloadAttachment(item, attachment)">Скачать</button>
                    <p v-if="attachmentStates[attachmentKey(item.id, attachment.id)]?.status === 'pending'" role="status">Загружаем вложение…</p>
                    <p v-if="attachmentStates[attachmentKey(item.id, attachment.id)]?.error" class="headman-requests__state headman-requests__state--error" role="alert">{{ attachmentStates[attachmentKey(item.id, attachment.id)]?.error }}</p>
                  </template>
                </li>
              </ul>
            </section>
            <p v-if="detailFor(item.id).summary.decisionComment" class="headman-requests__decision-comment">
              Причина решения: {{ detailFor(item.id).summary.decisionComment }}
            </p>
          </template>
        </div>

        <div v-if="item.status === 'PENDING' && !readOnly && canManageExcuses()" class="headman-requests__actions">
          <label>
            <span>Причина отклонения</span>
            <textarea v-model="rejectReasons[item.id]" rows="2" maxlength="1000" placeholder="Обязательна только для отклонения" />
          </label>
          <p v-if="decisionError[item.id]" class="headman-requests__state headman-requests__state--error" role="alert">{{ decisionError[item.id] }}</p>
          <div>
            <button type="button" :disabled="busyId !== null" @click="decide(item, 'APPROVED')">{{ busyId === item.id ? 'Сохраняем…' : 'Одобрить заявку' }}</button>
            <button type="button" :disabled="busyId !== null" @click="decide(item, 'REJECTED')">Отклонить</button>
          </div>
        </div>
      </article>
    </section>

    <nav v-if="page && page.totalPages > 1" class="headman-requests__pagination" aria-label="Страницы заявок">
      <button type="button" :disabled="currentPage === 0 || loading" @click="changePage(currentPage - 1)">Назад</button>
      <span>Страница {{ currentPage + 1 }} из {{ page.totalPages }}</span>
      <button type="button" :disabled="currentPage + 1 >= page.totalPages || loading" @click="changePage(currentPage + 1)">Дальше</button>
    </nav>
  </main>
</template>
