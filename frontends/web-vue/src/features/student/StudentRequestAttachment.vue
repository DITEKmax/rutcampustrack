<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, shallowRef, watch } from 'vue';
import type { RequestAttachment } from '../../../../mobile-core/src/features/requests/types';
import {
  ServerAttachmentPreview,
  type ServerAttachmentPreviewState,
} from '../../../../mobile-core/src/features/requests/server-attachment-preview';
import type { StudentScope } from './student-state';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import documentIcon from '../teacher/document.svg';
const props = defineProps<{
  scope: StudentScope;
  requestId: string;
  attachment: RequestAttachment;
}>();
const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../../../mobile-core/src/shared/components/AttachmentPdfPreview.vue'),
);
const state = shallowRef<ServerAttachmentPreviewState>({
  status: 'idle',
  url: null,
  mime: null,
  error: null,
});
const api = {
  downloadAttachment: async (id: string, attachmentId: string) => {
    const blob = await props.scope.api.downloadRequestAttachment(id, attachmentId);
    props.scope.transport.assertCurrent();
    return blob;
  },
};
const preview = new ServerAttachmentPreview(
  () => ({
    api: props.scope.current() ? api : null,
    requestId: props.requestId,
    ownerKey: props.scope.identity ?? '',
    allowed: props.scope.current(),
    offline: !navigator.onLine,
    attachment: {
      id: props.attachment.id,
      name: props.attachment.name ?? null,
      contentType: props.attachment.contentType ?? null,
      size: props.attachment.sizeBytes ?? 0,
      sha256: null,
      state: props.attachment.state === 'ACTIVE' ? 'AVAILABLE' : 'EXPIRED',
      uploadedAt: null,
      expiresAt: props.attachment.expiresAt ?? null,
      downloadUrl: null,
    },
  }),
  (value) => {
    if (props.scope.current()) state.value = value;
  },
);
const expired = computed(
  () =>
    props.attachment.state === 'EXPIRED' ||
    state.value.error === 'Срок хранения файла истёк.' ||
    Boolean(props.attachment.expiresAt && Date.parse(props.attachment.expiresAt) <= Date.now()),
);
function open() {
  props.scope.transport.assertCurrent();
  const url = preview.openUrl();
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
function download(event: Event) {
  props.scope.transport.assertCurrent();
  if (!preview.openUrl()) event.preventDefault();
}
watch(
  () => [props.requestId, props.attachment.id, props.attachment.state, props.attachment.expiresAt],
  () => {
    void preview.load();
  },
);
onBeforeUnmount(() => preview.dispose());
</script>
<template>
  <section
    class="student-attachment web-notice web-stack"
    :aria-label="attachment.name ?? 'Вложение заявки'"
    :aria-busy="state.status === 'loading'"
  >
    <div class="web-page-header">
      <div class="student-actions">
        <SvgIcon :src="documentIcon" /><strong :class="{ 'is-expired': expired }">{{
          attachment.name ?? 'Вложение'
        }}</strong><span class="web-caption">{{ Math.ceil((attachment.sizeBytes ?? 0) / 1024) }} КБ</span>
      </div><button
        v-if="!state.url"
        class="web-button"
        :disabled="expired || state.status === 'loading'"
        @click="preview.load()"
      >
        {{
          state.status === 'loading'
            ? 'Загружаем…'
            : state.error
              ? 'Повторить загрузку'
              : 'Открыть вложение'
        }}
      </button>
    </div><p
      v-if="expired"
      class="web-caption"
    >
      Срок хранения вложения истёк. Файл недоступен.
    </p><p
      v-if="state.error"
      class="web-error"
      role="alert"
    >
      {{ state.error }}
    </p>
    <template v-if="state.url">
      <img
        v-if="state.mime !== 'application/pdf'"
        class="student-attachment-image"
        :src="state.url"
        :alt="attachment.name ?? 'Вложение'"
        @error="preview.imageFailed(state.url!)"
      ><AttachmentPdfPreview
        v-else
        :url="state.url"
        :label="attachment.name ?? 'Документ'"
        :open-file="open"
      /><div class="student-actions">
        <button
          class="web-button"
          @click="open"
        >
          Открыть в новой вкладке
        </button><a
          :href="state.url"
          :download="attachment.name ?? 'attachment'"
          @click="download"
        >Скачать файл</a>
      </div>
    </template>
  </section>
</template>
