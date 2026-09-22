<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type {
  HeadmanRequestBucket,
  HeadmanRequestDetail,
  HeadmanRequestKind,
  HeadmanRequestPage,
  HeadmanRequestSummary,
  HeadmanRequestsApi,
} from './headman-requests-client'
import { HeadmanRequestsApiError } from './headman-requests-client'
import './headman-requests-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanRequestsApi | null
  offline?: boolean
  readOnly?: boolean
}>(), {
  offline: false,
  readOnly: false,
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
let loadRevision = 0

const pageCount = () => page.value?.totalPages ?? 0

async function load(): Promise<void> {
  const revision = ++loadRevision
  error.value = null
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
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить заявки.'
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
  const next = new Set(expanded.value)
  if (next.has(item.id)) {
    next.delete(item.id)
    expanded.value = next
    return
  }
  next.add(item.id)
  expanded.value = next
  if (details.value[item.id] || !props.api) return
  try {
    details.value = { ...details.value, [item.id]: await props.api.get(item.id) }
  } catch (cause) {
    next.delete(item.id)
    expanded.value = next
    decisionError.value = { ...decisionError.value, [item.id]: cause instanceof Error ? cause.message : 'Не удалось открыть детали.' }
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
      : cause instanceof Error ? cause.message : 'Не удалось принять решение.'
    decisionError.value = { ...decisionError.value, [item.id]: message }
    emit('error', cause)
  } finally {
    busyId.value = null
  }
}

async function downloadAttachment(item: HeadmanRequestSummary, attachmentId: string, filename: string | null): Promise<void> {
  if (!props.api || props.offline) return
  try {
    const blob = await props.api.downloadAttachment(item.id, attachmentId)
    if (typeof window === 'undefined') return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename || 'attachment'
    link.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Не удалось скачать вложение.'
    emit('error', cause)
  }
}

function statusLabel(status: HeadmanRequestSummary['status']): string {
  return { PENDING: 'Ожидает решения', APPROVED: 'Одобрено', REJECTED: 'Отклонено', CANCELLED: 'Отменено' }[status]
}

function kindLabel(kind: HeadmanRequestSummary['kind']): string {
  return kind === 'EXCUSE' ? 'Пропуск' : 'Опоздание'
}

function dateLabel(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium' }).format(date)
}

function lessonStatusLabel(value: string | null): string {
  return value === 'PRESENT' ? 'Присутствовал' : value === 'EXCUSED' ? 'Уважительная' : value === 'ABSENT' ? 'Отсутствовал' : value || 'Не отмечено'
}

function detailFor(id: string): HeadmanRequestDetail {
  return details.value[id]!
}

watch(
  () => [props.api, props.offline] as const,
  () => { void load() },
  { immediate: true },
)

onMounted(() => { void load() })
onBeforeUnmount(() => { loadRevision += 1 })
</script>

<template>
  <main class="headman-requests" aria-labelledby="headman-requests-title">
    <header class="headman-requests__header">
      <button class="headman-requests__back" type="button" @click="emit('back')">
        Назад
      </button>
      <div>
        <p class="headman-requests__eyebrow">Староста · заявки</p>
        <h1 id="headman-requests-title">Заявки группы</h1>
        <p class="headman-requests__hint">Решение применяется ко всей заявке.</p>
      </div>
    </header>

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

    <p v-if="offline" class="headman-requests__state" role="status">Заявки доступны только онлайн.</p>
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
                  <button type="button" :disabled="attachment.state === 'EXPIRED' || offline" @click="downloadAttachment(item, attachment.id, attachment.name)">
                    {{ attachment.name || 'Файл' }} · {{ attachment.size }} Б
                  </button>
                </li>
              </ul>
            </section>
            <p v-if="detailFor(item.id).summary.decisionComment" class="headman-requests__decision-comment">
              Причина решения: {{ detailFor(item.id).summary.decisionComment }}
            </p>
          </template>
        </div>

        <div v-if="item.status === 'PENDING' && !readOnly" class="headman-requests__actions">
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
