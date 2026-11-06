<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  nextTick,
  onBeforeUnmount,
  ref,
  shallowRef,
  watch,
} from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import type { TeacherApi } from './teacher-client';
import {
  TeacherAttachmentPreview,
  type TeacherAttachmentState,
  type TeacherPreviewAttachment,
} from './teacher-attachment-preview';
const AttachmentPdfPreview = defineAsyncComponent(
  () => import('../../shared/components/AttachmentPdfPreview.vue'),
);
const TeacherPdfDocumentPreview = defineAsyncComponent(
  () => import('./TeacherPdfDocumentPreview.vue'),
);
const props = withDefaults(
  defineProps<{
    api: TeacherApi | null;
    requestId?: string | null;
    lessonId?: number | null;
    studentId?: number | null;
    attachment: TeacherPreviewAttachment;
    closeSignal?: number;
    suspended?: boolean;
  }>(),
  { closeSignal: 0, requestId: null, lessonId: null, studentId: null, suspended: false },
);
const emit = defineEmits<{
  'fullscreen-open': [open: boolean];
  availability: [ready: boolean];
}>();
const fullscreen = ref(false);
const inlinePreview = ref<HTMLElement | null>(null);
const fullDialog = ref<HTMLElement | null>(null);
function closeImage(): void {
  if (!fullscreen.value) return;
  fullscreen.value = false;
  emit('fullscreen-open', false);
  void nextTick(() => inlinePreview.value?.querySelector?.<HTMLButtonElement>('button')?.focus());
}
function fullscreenKeyboard(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    closeImage();
  } else if (event.key === 'Tab') {
    const buttons = Array.from(
      fullDialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [],
    ).filter((button) => !button.closest('[hidden]'));
    const first = buttons[0],
      last = buttons.at(-1);
    if (
      event.shiftKey &&
      (event.target === first || !buttons.includes(event.target as HTMLButtonElement))
    ) {
      event.preventDefault();
      last?.focus();
    } else if (
      !event.shiftKey &&
      (event.target === last || !buttons.includes(event.target as HTMLButtonElement))
    ) {
      event.preventDefault();
      first?.focus();
    }
  }
}
watch(() => props.closeSignal, closeImage);
const state = shallowRef<TeacherAttachmentState>({
  status: 'idle',
  url: null,
  mime: null,
  error: null,
});
const preview = new TeacherAttachmentPreview(
  () => ({
    api: props.api,
    requestId: props.requestId,
    lessonId: props.lessonId,
    studentId: props.studentId,
    attachment: { ...props.attachment },
  }),
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
  [
    () => props.api,
    () => props.requestId,
    () => props.lessonId,
    () => props.studentId,
    () => props.attachment.id,
    () => props.attachment.expiresAt,
    () => props.attachment.fileName,
    () => props.attachment.contentType,
    () => props.attachment.size,
  ],
  () => {
    void preview.load();
  },
  { immediate: true, flush: 'sync' },
);
watch(() => state.value.url, closeImage, { flush: 'sync' });
watch(
  () => state.value.status === 'ready',
  (ready) => emit('availability', ready),
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  if (fullscreen.value) emit('fullscreen-open', false);
  preview.dispose();
});
function open(): void {
  if (props.suspended) return;
  const url = preview.openUrl();
  if (!url) return;
  fullscreen.value = true;
  emit('fullscreen-open', true);
  void nextTick(() => fullDialog.value?.querySelector?.<HTMLButtonElement>('button')?.focus());
}
function imageError(event: Event): void {
  if (event.target instanceof HTMLImageElement) preview.imageFailed(event.target.src);
}
</script>
<template>
  <section
    ref="inlinePreview"
    class="teacher-attachment"
    :aria-label="attachment.fileName"
    :aria-busy="state.status === 'loading' || suspended"
  >
    <div class="teacher-attachment__heading">
      <strong>{{ attachment.fileName }}</strong><span class="teacher-screen__muted">{{
        attachment.size === null ? 'Размер не указан' : Math.ceil(attachment.size / 1024) + ' КБ'
      }}</span>
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
    <p
      v-if="suspended && !fullscreen"
      role="status"
    >
      Проверяем доступ к документу…
    </p>
    <div
      v-if="state.url"
      :hidden="suspended"
    >
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
        >
      </button><AttachmentPdfPreview
        v-else
        :url="state.url"
        :open-file="open"
        :label="attachment.fileName + '. Первая страница PDF'"
      />
    </div>
  </section>
  <div
    v-if="fullscreen && state.url"
    class="teacher-attachment-full"
    @click.self="closeImage"
    @keydown="fullscreenKeyboard"
  >
    <section
      ref="fullDialog"
      class="teacher-attachment-full__dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="attachment.fileName"
      :aria-busy="suspended"
    >
      <header class="teacher-attachment-full__heading">
        <h2>{{ attachment.fileName }}</h2>
        <button
          type="button"
          class="teacher-screen__icon-button"
          aria-label="Закрыть документ"
          @click="closeImage"
        >
          <MobileIcon name="close" />
        </button>
      </header>
      <p
        v-if="suspended"
        role="status"
      >
        Проверяем доступ к документу…
      </p>
      <div
        class="teacher-attachment-full__content"
        :hidden="suspended"
      >
        <img
          v-if="image"
          :src="state.url"
          :alt="attachment.fileName"
          @error="imageError"
        >
        <TeacherPdfDocumentPreview
          v-else
          :url="state.url"
          :label="attachment.fileName"
        />
      </div>
    </section>
  </div>
</template>
