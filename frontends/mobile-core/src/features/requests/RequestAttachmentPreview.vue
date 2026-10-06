<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import type { HeadmanRequestsApi, HeadmanRequestAttachment } from '../headman-requests/headman-requests-client'
import { ServerAttachmentPreview, type ServerAttachmentPreviewState } from './server-attachment-preview'
import './request-attachment-preview.pcss'
const props = defineProps<{ api: HeadmanRequestsApi | null; requestId: string; attachment: HeadmanRequestAttachment; ownerKey: string; allowed: boolean; offline: boolean }>()
const state = shallowRef<ServerAttachmentPreviewState>({ status: 'idle', url: null, mime: null, error: null })
const preview = new ServerAttachmentPreview(() => props, (next) => { state.value = next })
const isImage = computed(() => state.value.mime === 'image/png' || state.value.mime === 'image/jpeg')
const canRetry = computed(() => props.allowed && !props.offline && props.attachment.state === 'AVAILABLE' && (props.attachment.expiresAt === null || Date.parse(props.attachment.expiresAt) > Date.now()))
function open(): void { const url = preview.openUrl(); if (url) window.open(url, '_blank', 'noopener,noreferrer') }
function imageFailed(event: Event): void { if (event.target instanceof HTMLImageElement) preview.imageFailed(event.target.src) }
watch(() => [props.api, props.requestId, props.attachment.id, props.attachment.state, props.attachment.expiresAt, props.ownerKey, props.allowed, props.offline] as const, () => { void preview.load() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => preview.dispose())
</script>
<template>
  <section
    class="request-server-preview"
    :aria-busy="state.status === 'loading'"
    :aria-label="attachment.name ?? 'Вложение заявки'"
  >
    <p
      v-if="state.status === 'loading'"
      role="status"
    >
      Загружаем {{ attachment.name ?? 'вложение' }}…
    </p>
    <StudentWarningBlock
      v-if="state.error"
      title="Не удалось показать файл"
      :message="state.error"
      :action-label="canRetry ? 'Повторить' : ''"
      @action="preview.load"
    />
    <template v-if="state.url && allowed && !offline">
      <img
        v-if="isImage"
        :src="state.url"
        :alt="attachment.name ?? 'Вложение заявки'"
        @error="imageFailed"
      >
      <object
        v-else
        :data="state.url"
        type="application/pdf"
        :aria-label="attachment.name ?? 'PDF заявки'"
      ><p>Предпросмотр PDF недоступен в этом браузере. Открой файл кнопкой ниже.</p></object>
      <button
        type="button"
        @click="open"
      >
        Открыть {{ attachment.name ?? 'файл' }}
      </button>
    </template>
  </section>
</template>
