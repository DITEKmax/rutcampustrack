<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { HeadmanJournalApprovedTicket } from './headman-journal-client'
import { HeadmanRequestsApiError, type HeadmanRequestsApi, type HeadmanRequestDetail, type HeadmanRequestAttachment } from '../headman-requests/headman-requests-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { runRequestAttachmentDownload } from '../requests/request-attachment-action'
import type { RequestAttachmentViewState } from '../requests/types'

const props = defineProps<{ api: HeadmanRequestsApi | null; ticket: HeadmanJournalApprovedTicket; offline: boolean; allowed: boolean }>()
const emit = defineEmits<{ error: [cause: unknown] }>()
const detail = shallowRef<HeadmanRequestDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const attachments = ref<Record<string, RequestAttachmentViewState>>({})
let generation = 0
let disposed = false
const urls = new Map<string, ReturnType<typeof setTimeout>>()
function release(url: string): void {
  clearTimeout(urls.get(url))
  urls.delete(url)
  URL.revokeObjectURL(url)
}
function resetDownloads(): void { for (const url of urls.keys()) release(url); attachments.value = {} }
async function load(): Promise<void> {
  const run = ++generation
  resetDownloads()
  detail.value = null
  error.value = null
  const api = props.api
  const id = props.ticket.id
  if (!api || props.offline || !props.allowed) { loading.value = false; return }
  loading.value = true
  try {
    const value = await api.get(id)
    if (disposed || run !== generation || api !== props.api || id !== props.ticket.id || !props.allowed || props.offline) return
    if (value.summary.id !== id || value.summary.kind !== props.ticket.kind) throw new Error('Сервер вернул другую заявку.')
    detail.value = value
  } catch (cause) {
    if (disposed || run !== generation || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить заявку.'
    emit('error', cause)
  } finally { if (!disposed && run === generation) loading.value = false }
}
function expired(attachment: HeadmanRequestAttachment): boolean {
  return attachment.expiresAt !== null && (!Number.isFinite(Date.parse(attachment.expiresAt)) || Date.parse(attachment.expiresAt) <= Date.now())
}
function download(attachment: HeadmanRequestAttachment): void {
  const api = props.api
  const id = props.ticket.id
  const run = generation
  if (!api || props.offline || !props.allowed || attachments.value[attachment.id]?.status === 'pending') return
  if (expired(attachment)) { attachments.value = { ...attachments.value, [attachment.id]: { status: 'error', error: 'Срок хранения файла истёк.' } }; return }
  void runRequestAttachmentDownload({
    ownerIdentity: id, ownerGeneration: run,
    currentOwnerIdentity: () => api === props.api && !props.offline && props.allowed && detail.value?.summary.id === id ? props.ticket.id : null,
    currentOwnerGeneration: () => generation, isDisposed: () => disposed,
    download: () => api.downloadAttachment(id, attachment.id),
    createObjectUrl: (blob) => { const url = URL.createObjectURL(blob); urls.set(url, setTimeout(() => release(url), 60_000)); return url },
    releaseObjectUrl: release, scheduleRelease: () => undefined,
    save: (url) => { const link = document.createElement('a'); link.href = url; link.download = attachment.name ?? 'Вложение'; link.click() },
    setState: (value) => { attachments.value = { ...attachments.value, [attachment.id]: value } },
    errorMessage: (cause) => cause instanceof HeadmanRequestsApiError && [403, 404, 410].includes(cause.response.status) ? 'Файл недоступен или срок хранения истёк.' : 'Не удалось скачать файл. Повтори попытку.',
    onError: (cause) => emit('error', cause),
  })
}
watch(() => [props.api, props.ticket.id, props.offline, props.allowed] as const, () => { void load() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; generation += 1; resetDownloads() })
</script>
<template>
  <section
    class="headman-journal__linked-request"
    :aria-busy="loading"
    aria-label="Одобренная заявка студента"
  >
    <p
      v-if="!allowed"
      role="status"
    >
      Детали заявки доступны с правом управления уважительными причинами.
    </p>
    <p
      v-else-if="offline"
      role="status"
    >
      Заявка доступна при подключении к интернету.
    </p>
    <p
      v-else-if="!api"
      role="status"
    >
      Источник заявки не подключён.
    </p>
    <p
      v-else-if="error"
      role="alert"
    >
      {{ error }} <button
        type="button"
        @click="load"
      >
        Повторить
      </button>
    </p>
    <p
      v-else-if="loading"
      role="status"
    >
      Загружаем заявку…
    </p>
    <template v-else-if="detail">
      <h2>{{ detail.summary.kind === 'EXCUSE' ? 'Заявка на пропуск' : 'Заявка на опоздание' }}</h2>
      <p>{{ detail.summary.studentName ?? 'Студент' }} · {{ detail.summary.status === 'APPROVED' ? 'Одобрено' : detail.summary.status }}</p>
      <p v-if="detail.summary.reason">
        Причина: {{ detail.summary.reason }}
      </p>
      <p v-if="detail.summary.comment">
        {{ detail.summary.comment }}
      </p>
      <p v-if="detail.summary.decisionComment">
        Комментарий к решению: {{ detail.summary.decisionComment }}
      </p>
      <p v-if="detail.summary.coverageStart">
        Период: {{ detail.summary.coverageStart }}<template v-if="detail.summary.coverageEnd">
          — {{ detail.summary.coverageEnd }}
        </template>
      </p>
      <ul v-if="detail.attachments.length">
        <li
          v-for="attachment in detail.attachments"
          :key="attachment.id"
        >
          <button
            type="button"
            :disabled="offline || !allowed || attachments[attachment.id]?.status === 'pending'"
            @click="download(attachment)"
          >
            {{ attachments[attachment.id]?.status === 'pending' ? 'Загружаем…' : attachment.name ?? 'Скачать вложение' }}
          </button>
          <p
            v-if="attachments[attachment.id]?.error"
            role="alert"
          >
            {{ attachments[attachment.id]?.error }}
          </p>
        </li>
      </ul>
      <p v-else>
        В заявке нет доступных вложений.
      </p>
      <p>Решение заявки и её файлы сохраняются после снятия отметки в журнале.</p>
    </template>
  </section>
</template>
