<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, shallowRef } from 'vue';
import type { TeacherExcuseAttachment } from '../../../../mobile-core/src/features/teacher/teacher-client';
import {
  TeacherAttachmentPreview,
  type TeacherAttachmentState,
} from '../../../../mobile-core/src/features/teacher/teacher-attachment-preview';
import type { TeacherScope } from './teacher-state';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import documentIcon from './document.svg';

const props = defineProps<{
  scope: TeacherScope;
  ticketId: string;
  attachment: TeacherExcuseAttachment;
}>();
const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../../../mobile-core/src/shared/components/AttachmentPdfPreview.vue'),
);
const state = shallowRef<TeacherAttachmentState>({
  status: 'idle',
  url: null,
  mime: null,
  error: null,
});
const preview = new TeacherAttachmentPreview(
  () => ({
    api: props.scope.current() ? props.scope.api : null,
    requestId: props.ticketId,
    attachment: props.attachment,
  }),
  (value) => {
    if (props.scope.current()) state.value = value;
  },
);
const expired = computed(
  () =>
    state.value.error === 'Срок хранения файла истёк.' ||
    (props.attachment.expiresAt !== null &&
    (!Number.isFinite(Date.parse(props.attachment.expiresAt)) ||
      Date.parse(props.attachment.expiresAt) <= Date.now())),
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
onBeforeUnmount(() => preview.dispose());
</script>
<template>
  <section
    class="teacher-attachment web-stack"
    :aria-label="attachment.fileName"
    :aria-busy="state.status === 'loading'"
  >
    <div class="teacher-toolbar">
      <SvgIcon :src="documentIcon" /><strong>{{ attachment.fileName }}</strong><span class="web-caption">{{ Math.ceil(attachment.size / 1024) }} КБ</span><button
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
    </div>
    <p
      v-if="expired"
      class="web-caption"
    >
      Срок хранения файла истёк.
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
        class="teacher-attachment-image"
        :src="state.url"
        :alt="attachment.fileName"
        @error="preview.imageFailed(state.url!)"
      ><AttachmentPdfPreview
        v-else
        :url="state.url"
        :open-file="open"
        :label="attachment.fileName"
      /><div class="teacher-toolbar">
        <button
          class="web-button"
          @click="open"
        >
          Открыть в новой вкладке
        </button><a
          :href="state.url"
          :download="attachment.fileName"
          @click="download"
        >Скачать файл</a>
      </div>
    </template>
  </section>
</template>
