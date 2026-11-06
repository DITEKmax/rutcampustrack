<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import {
  TeacherAttachmentPreview,
  type TeacherAttachmentState,
  type TeacherPreviewAttachment,
} from '../../../../mobile-core/src/features/teacher/teacher-attachment-preview';
import { teacherError, type TeacherScope } from './teacher-state';
import { manualAttachment, teacherAttachmentKey, type TeacherEvidenceSource } from './teacher-evidence';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import documentIcon from './document.svg';

const props = defineProps<{
  scope: TeacherScope;
  source: TeacherEvidenceSource;
  attachment: TeacherPreviewAttachment;
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
const suspended = ref(false);
const verifyError = ref('');
const manual = !('ticketId' in props.source);
const verificationInterval = 15_000;
const verificationDeadline = 8_000;
let timer: ReturnType<typeof setTimeout> | undefined;
let verification: AbortController | null = null;
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
  );
}
let preview = createPreview();
const expired = computed(
  () =>
    state.value.error === 'Срок хранения файла истёк.' ||
    (props.attachment.expiresAt !== null &&
      (Date.parse(props.attachment.expiresAt) <= Date.now() ||
        (!manual && !Number.isFinite(Date.parse(props.attachment.expiresAt))))),
);
const error = computed(() => verifyError.value || state.value.error);
const unavailable = computed(() => manual &&
  (props.attachment.expiresAt === null || !Number.isFinite(Date.parse(props.attachment.expiresAt))));
const busy = computed(() => suspended.value || state.value.status === 'loading');
function scheduleVerification() {
  clearTimeout(timer);
  if (manual && state.value.status === 'ready' && !suspended.value && !verification &&
      document.visibilityState !== 'hidden')
    timer = setTimeout(() => void verifyManual(), verificationInterval);
}
// The exact binary endpoint admits the first preview. Keep a manual proof only
// while the current roster still links that same descriptor to this pair and student.
async function verifyManual() {
  if (!manual || !props.scope.current() || state.value.status !== 'ready' || verification) return;
  const { lessonId, studentId } = props.source;
  const controller = new AbortController();
  verification = controller;
  const expected = teacherAttachmentKey(props.attachment);
  const deadline = setTimeout(
    () => controller.abort(new Error('Не удалось проверить доступ к документу вовремя.')),
    verificationDeadline,
  );
  try {
    const { signal } = controller;
    const aborted = new Promise<never>((_, reject) =>
      signal.addEventListener('abort', () => reject(signal.reason), { once: true }));
    const data = await Promise.race([props.scope.api.lesson(lessonId, { cache: 'no-store', signal }), aborted]);
    if (verification !== controller || !props.scope.current()) return;
    const row = data.roster.find(entry => entry.studentId === studentId);
    const file = row && manualAttachment(row);
    if (data.lesson.id !== lessonId || data.lesson.cancelled || !file || teacherAttachmentKey(file) !== expected)
      throw new Error('Документ больше недоступен для этой отметки.');
  } catch (cause) {
    if (verification !== controller || !props.scope.current()) return;
    preview.dispose();
    preview = createPreview();
    verifyError.value = teacherError(cause);
  } finally {
    clearTimeout(deadline);
    if (verification === controller) {
      verification = null;
      suspended.value = false;
      scheduleVerification();
    }
  }
}
function visibilityChanged() {
  if (document.visibilityState === 'hidden') {
    clearTimeout(timer);
    suspended.value = state.value.status === 'ready' || state.value.status === 'loading';
    const previous = verification;
    verification = null;
    previous?.abort();
  } else if (suspended.value && state.value.status === 'ready') {
    void verifyManual();
  } else {
    suspended.value = false;
    scheduleVerification();
  }
}
watch(() => state.value.status, scheduleVerification);
onMounted(() => {
  if (manual) document.addEventListener('visibilitychange', visibilityChanged);
});
function show() {
  if (busy.value || expired.value || unavailable.value) return;
  if (state.value.url) {
    open();
    return;
  }
  verifyError.value = '';
  void preview.load();
}
function open() {
  if (!props.scope.current() || suspended.value) return;
  const url = preview.openUrl();
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}
function resolveBlob(url: string) {
  return props.scope.current() && !suspended.value ? preview.openBlob(url) : null;
}
function download(event: Event) {
  if (!props.scope.current() || suspended.value || !preview.openUrl()) event.preventDefault();
}
onBeforeUnmount(() => {
  clearTimeout(timer);
  const previous = verification;
  verification = null;
  previous?.abort();
  if (manual) document.removeEventListener('visibilitychange', visibilityChanged);
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
        {{ suspended ? 'Проверяем доступ…' : busy ? 'Загружаем…' : state.url ? 'Открыть в новой вкладке' : error ? 'Повторить загрузку' : 'Показать файл' }}
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
