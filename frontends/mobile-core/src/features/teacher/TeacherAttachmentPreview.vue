<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, shallowRef, watch } from 'vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import type { TeacherApi, TeacherExcuseAttachment } from './teacher-client';
import {
  TeacherAttachmentPreview,
  type TeacherAttachmentState,
} from './teacher-attachment-preview';
const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../shared/components/AttachmentPdfPreview.vue'),
);
const props = defineProps<{
  api: TeacherApi | null;
  requestId: string;
  attachment: TeacherExcuseAttachment;
}>();
const state = shallowRef<TeacherAttachmentState>({
  status: 'idle',
  url: null,
  mime: null,
  error: null,
});
const preview = new TeacherAttachmentPreview(
  () => props,
  (value) => {
    state.value = value;
  },
);
const image = computed(() => state.value.mime === 'image/png' || state.value.mime === 'image/jpeg');
const expired = computed(
  () =>
    state.value.status !== 'loading' &&
    props.attachment.expiresAt !== null &&
    (!Number.isFinite(Date.parse(props.attachment.expiresAt)) ||
      Date.parse(props.attachment.expiresAt) <= Date.now()),
);
watch(
  () => [props.api, props.requestId, props.attachment.id, props.attachment.expiresAt] as const,
  () => {
    void preview.load();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(() => preview.dispose());
function open(): void {
  const url = preview.openUrl();
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
function imageError(event: Event): void {
  if (event.target instanceof HTMLImageElement) preview.imageFailed(event.target.src);
}
</script>
<template>
  <section
    class="teacher-attachment"
    :aria-label="attachment.fileName"
    :aria-busy="state.status === 'loading'"
  >
    <div class="teacher-attachment__heading">
      <strong>{{ attachment.fileName }}</strong
      ><span class="teacher-screen__muted">{{ Math.ceil(attachment.size / 1024) }} КБ</span>
    </div>
    <p
      v-if="state.status === 'loading'"
      class="teacher-screen__muted"
      role="status"
    >
      Загружаем файл…
    </p>
    <StudentWarningBlock
      v-if="state.error"
      title="Файл недоступен"
      :message="state.error"
      :action-label="expired ? '' : 'Повторить'"
      @action="preview.load()"
    />
    <template v-if="state.url">
      <button
        v-if="image"
        class="teacher-attachment__image"
        type="button"
        :aria-label="'Открыть ' + attachment.fileName"
        @click="open"
      >
        <img
          :src="state.url"
          :alt="attachment.fileName"
          @error="imageError"
        /> </button
      ><AttachmentPdfPreview
        v-else
        :url="state.url"
        :open-file="open"
        :label="attachment.fileName + '. Первая страница PDF'"
      />
    </template>
  </section>
</template>
