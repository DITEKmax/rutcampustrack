<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { HeadmanJournalApprovedTicket } from './headman-journal-client'
import { type HeadmanRequestsApi, type HeadmanRequestDetail } from '../headman-requests/headman-requests-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import RequestAttachmentPreview from '../requests/RequestAttachmentPreview.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'

const props = defineProps<{ api: HeadmanRequestsApi | null; ticket: HeadmanJournalApprovedTicket; offline: boolean; allowed: boolean }>()
const emit = defineEmits<{ error: [cause: unknown] }>()
const detail = shallowRef<HeadmanRequestDetail | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
let generation = 0
let disposed = false
async function load(): Promise<void> {
  const run = ++generation
  detail.value = null
  error.value = null
  const api = props.api
  const id = props.ticket.id
  if (!api || props.offline || !props.allowed) { loading.value = false; return }
  loading.value = true
  try {
    const value = await api.get(id)
    if (disposed || run !== generation || api !== props.api || id !== props.ticket.id || !props.allowed || props.offline) return
    if (value.summary.id !== id || value.summary.kind !== props.ticket.kind || value.summary.status !== 'APPROVED') throw new Error('Сервер вернул другую заявку.')
    detail.value = value
  } catch (cause) {
    if (disposed || run !== generation || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить заявку.'
    emit('error', cause)
  } finally { if (!disposed && run === generation) loading.value = false }
}
watch(() => [props.api, props.ticket.id, props.offline, props.allowed] as const, () => { void load() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; generation += 1 })
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
    <StudentWarningBlock
      v-else-if="error"
      title="Заявка недоступна"
      :message="error"
      action-label="Повторить"
      @action="load"
    />
    <p
      v-else-if="loading"
      role="status"
    >
      Загружаем заявку…
    </p>
    <template v-else-if="detail">
      <div class="headman-journal__request-heading">
        <span
          class="request-status-badge"
          :data-status="detail.summary.status"
        >{{ detail.summary.status === 'APPROVED' ? 'Одобрено' : detail.summary.status }}</span>
        <span class="headman-journal__request-tag">{{ detail.summary.kind === 'EXCUSE' ? 'Заявка на пропуск' : 'Заявка на опоздание' }}</span>
      </div>
      <h2>{{ detail.summary.studentName ?? 'Студент' }}</h2>
      <p v-if="detail.summary.coverageStart">
        <MobileIcon name="calendar" />{{ detail.summary.coverageStart }}<template v-if="detail.summary.coverageEnd">
          — {{ detail.summary.coverageEnd }}
        </template>
      </p>
      <p v-if="detail.summary.reason">
        <MobileIcon name="homework" />{{ detail.summary.reason }}
      </p>
      <p v-if="detail.summary.comment">
        {{ detail.summary.comment }}
      </p>
      <p v-if="detail.summary.decisionComment">
        Комментарий к решению: {{ detail.summary.decisionComment }}
      </p>
      <RequestAttachmentPreview
        v-for="attachment in detail.attachments"
        :key="attachment.id"
        preview-only
        tap-to-open
        :api="api"
        :request-id="detail.summary.id"
        :attachment="attachment"
        :owner-key="String(generation)"
        :offline="offline"
        :allowed="allowed"
      />
      <p v-if="!detail.attachments.length">
        В заявке нет доступных вложений.
      </p>
    </template>
  </section>
</template>
