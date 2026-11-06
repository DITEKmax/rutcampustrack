<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import {
  TeacherAttachmentPreview,
  type TeacherAttachmentState,
  type TeacherPreviewAttachment,
} from '../../../../mobile-core/src/features/teacher/teacher-attachment-preview';
import { TeacherApiError } from '../../../../mobile-core/src/features/teacher/teacher-client';
import { teacherError, type TeacherScope } from './teacher-state';
import { verifyTeacherAttachment, type TeacherEvidenceSource } from './teacher-evidence';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import documentIcon from './document.svg';

const props = defineProps<{
  scope: TeacherScope;
  source: TeacherEvidenceSource;
  attachment: TeacherPreviewAttachment;
  proofRevision: () => number;
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
const checking = ref(false);
const hidden = ref(document.visibilityState === 'hidden');
const verifyError = ref('');
const manual = !('ticketId' in props.source);
const verificationInterval = 15_000;
const verificationDeadline = 8_000;
let timer: ReturnType<typeof setTimeout> | undefined;
let operation: AbortController | null = null;
function attachmentError(cause: unknown) {
  if (cause instanceof TeacherApiError && cause.response.status !== 410) {
    if (cause.message !== `HTTP ${cause.response.status}`) return cause.message;
    if (cause.response.status >= 500) return 'Не удалось получить файл. Повтори попытку.';
    if (![403, 404].includes(cause.response.status)) return 'Файл недоступен. Повтори попытку.';
  }
  return teacherError(cause);
}
function createPreview() {
  return new TeacherAttachmentPreview(
    () => ({
      api: props.scope.current() ? props.scope.api : null,
      requestId: 'ticketId' in props.source ? props.source.ticketId : null,
      lessonId: props.source.lessonId,
      studentId: props.source.studentId,
      attachment: props.attachment,
    }),
    (value) => {
      if (props.scope.current()) state.value = value;
    },
    URL,
    attachmentError,
  );
}
let preview = createPreview();
const expired = computed(
  () =>
    state.value.error === 'Срок хранения файла истёк.' ||
    verifyError.value === 'Срок хранения файла истёк.' ||
    (props.attachment.expiresAt !== null &&
      (Date.parse(props.attachment.expiresAt) <= Date.now() ||
        (!manual && !Number.isFinite(Date.parse(props.attachment.expiresAt))))),
);
const error = computed(() => verifyError.value || state.value.error);
const unavailable = computed(() => manual &&
  (props.attachment.expiresAt === null || !Number.isFinite(Date.parse(props.attachment.expiresAt))));
const blocked = computed(() => checking.value || hidden.value);
const busy = computed(() => blocked.value || state.value.status === 'loading');
function stopOperation() {
  const previous = operation;
  operation = null;
  previous?.abort();
}
function resetPreview() {
  preview.dispose();
  preview = createPreview();
}
function scheduleVerification() {
  clearTimeout(timer);
  if (state.value.status === 'ready' && !hidden.value && !operation)
    timer = setTimeout(() => void refreshProof(false), verificationInterval);
}
async function checkSource(controller: AbortController) {
  const deadline = setTimeout(
    () => controller.abort(new Error('Не удалось проверить доступ к документу вовремя.')),
    verificationDeadline,
  );
  const { signal } = controller;
  let onAbort = () => {};
  try {
    signal.throwIfAborted();
    // Race the signal as session recovery may still be waiting after the fetch is aborted.
    const aborted = new Promise<never>((_, reject) => {
      onAbort = () => reject(signal.reason);
      signal.addEventListener('abort', onAbort, { once: true });
    });
    await Promise.race([
      verifyTeacherAttachment(props.scope.api, props.source, props.attachment, signal),
      aborted,
    ]);
  } finally {
    clearTimeout(deadline);
    signal.removeEventListener('abort', onAbort);
  }
}
async function refreshProof(loadFile: boolean) {
  if (!props.scope.current() || hidden.value || expired.value || unavailable.value) return;
  checking.value = true;
  clearTimeout(timer);
  stopOperation();
  if (state.value.status === 'loading') resetPreview();
  const controller = new AbortController();
  operation = controller;
  const current = () => operation === controller && props.scope.current() && !hidden.value;
  verifyError.value = '';
  try {
    // A ticket's binary route can admit another lesson on the same request.
    // Manual bytes already have an exact lesson and student check at the binary route.
    if (!loadFile || !manual) await checkSource(controller);
    if (!current() || !loadFile) return;
    await preview.load(controller.signal);
    if (current() && !manual && state.value.status === 'ready') await checkSource(controller);
  } catch (cause) {
    if (!current() || expired.value) return;
    resetPreview();
    verifyError.value = attachmentError(cause);
  } finally {
    if (operation === controller) {
      stopOperation();
      checking.value = false;
      scheduleVerification();
    }
  }
}
function invalidateProof() {
  if (state.value.status === 'ready') void refreshProof(false);
  else if (operation) void refreshProof(true);
}
function visibilityChanged() {
  hidden.value = document.visibilityState === 'hidden';
  if (hidden.value) {
    clearTimeout(timer);
    stopOperation();
    if (state.value.status === 'loading') resetPreview();
    checking.value = false;
  } else invalidateProof();
}
watch(props.proofRevision, invalidateProof, { flush: 'sync' });
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged));
function show() {
  if (busy.value || expired.value || unavailable.value) return;
  if (state.value.url) {
    open();
    return;
  }
  void refreshProof(true);
}
function open() {
  if (!props.scope.current() || blocked.value) return;
  const url = preview.openUrl();
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
function resolveBlob(url: string) {
  return props.scope.current() && !blocked.value ? preview.openBlob(url) : null;
}
function download(event: Event) {
  if (!props.scope.current() || blocked.value || !preview.openUrl()) event.preventDefault();
}
onBeforeUnmount(() => {
  clearTimeout(timer);
  stopOperation();
  document.removeEventListener('visibilitychange', visibilityChanged);
  preview.dispose();
});
</script>
<template>
  <section
    class="teacher-attachment web-stack"
    :aria-label="attachment.fileName"
    :aria-busy="busy"
  >
    <div class="teacher-attachment-header">
      <SvgIcon :src="documentIcon" />
      <strong>{{ attachment.fileName }}</strong>
      <span
        v-if="attachment.size !== null"
        class="web-caption"
      >{{ Math.ceil(attachment.size / 1024) }} КБ</span>
    </div>
    <p
      v-if="expired"
      class="web-caption"
    >
      Срок хранения файла истёк.
    </p>
    <p
      v-else-if="unavailable"
      class="web-caption"
    >
      Сервер не указал срок хранения. Открыть файл нельзя.
    </p>
    <p
      v-else-if="error"
      class="web-error"
      role="alert"
    >
      {{ error }}
    </p>
    <div
      v-if="!expired && !unavailable"
      class="teacher-toolbar"
    >
      <button
        class="web-button"
        type="button"
        :aria-disabled="busy"
        @click="show"
      >
        {{ state.status === 'loading' ? 'Загружаем…' : blocked ? 'Проверяем доступ…' : state.url ? 'Открыть в новой вкладке' : error ? 'Повторить загрузку' : 'Показать файл' }}
      </button>
      <a
        v-if="state.url && !busy"
        :href="state.url"
        :download="attachment.fileName"
        @click="download"
      >Скачать файл</a>
    </div>
    <template v-if="state.url && !busy && !expired">
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
        :resolve-blob="resolveBlob"
        :label="attachment.fileName"
      />
    </template>
  </section>
</template>
