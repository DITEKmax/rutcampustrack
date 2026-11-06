<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import {
  type TeacherApi,
  type TeacherLessonResponse,
  type TeacherRosterEntry,
} from './teacher-client';
import { teacherExcuseTypeLabel } from './teacher-ui-format';
import { isTeacherManualExcuse } from './teacher-manual-excuse';
import TeacherLessonCard from './TeacherLessonCard.vue';
import TeacherAttachmentPreview from './TeacherAttachmentPreview.vue';
import type { TeacherPreviewAttachment } from './teacher-attachment-preview';
import './teacher-screen.pcss';

const props = withDefaults(
  defineProps<{
    api: TeacherApi | null;
    lessonId: number | null;
    studentId: number | null;
    showBack?: boolean;
    attachmentCloseSignal?: number;
    realtimeRevision?: number;
    offline?: boolean;
  }>(),
  { showBack: true, attachmentCloseSignal: 0, realtimeRevision: 0, offline: false },
);
const emit = defineEmits<{
  back: [];
  error: [cause: unknown];
  'attachment-open': [open: boolean];
}>();
const state = ref<TeacherLessonResponse | null>(null);
const entry = ref<TeacherRosterEntry | null>(null);
const loading = ref(false);
const verifying = ref(false);
const attachmentReady = ref(false);
const error = ref<string | null>(null);
const reasonLabel = computed(() =>
  teacherExcuseTypeLabel(entry.value?.excuseType ?? entry.value?.excuseReason ?? null),
);
const attachment = computed<TeacherPreviewAttachment | null>(() =>
  entry.value?.attachmentId
    ? {
        id: entry.value.attachmentId,
        fileName: entry.value.attachmentName || 'Документ',
        contentType: entry.value.attachmentContentType || 'application/octet-stream',
        size: entry.value.attachmentSize,
        uploadedAt: null,
        expiresAt: entry.value.attachmentExpiresAt ?? null,
      }
    : null,
);
// Keep the current PDF page only when the authoritative descriptor is unchanged.
const attachmentKey = computed(() => {
  const expiresAt = entry.value?.attachmentExpiresAt;
  return JSON.stringify([
    entry.value?.attachmentId,
    entry.value?.attachmentName,
    entry.value?.attachmentContentType,
    entry.value?.attachmentSize,
    expiresAt,
    expiresAt ? Date.parse(expiresAt) > Date.now() : false,
  ]);
});
const recheckAfterMs = 15_000;
const requestDeadlineMs = 8_000;
let timer: ReturnType<typeof setTimeout> | undefined;
let request: AbortController | null = null;
let nextVerificationAt = 0;

function pageHidden(): boolean {
  return typeof document !== 'undefined' && document.visibilityState === 'hidden';
}
function stopVerification(): void {
  clearTimeout(timer);
  timer = undefined;
  const current = request;
  request = null;
  current?.abort();
}
function scheduleVerification(): void {
  clearTimeout(timer);
  timer = undefined;
  if (
    !attachmentReady.value ||
    !attachment.value?.expiresAt ||
    Date.parse(attachment.value.expiresAt) <= Date.now() ||
    request ||
    pageHidden() ||
    props.offline
  )
    return;
  timer = setTimeout(
    () => void load('periodic'),
    Math.max(0, Math.ceil(nextVerificationAt - performance.now())),
  );
}
function updateAvailability(ready: boolean): void {
  attachmentReady.value = ready;
  scheduleVerification();
}
function onVisibilityChange(): void {
  if (pageHidden()) {
    stopVerification();
    loading.value = false;
    verifying.value = true;
  } else if (attachment.value || (!state.value && !error.value)) {
    void load('invalidated');
  }
}
onMounted(() => {
  if (typeof document !== 'undefined')
    document.addEventListener?.('visibilitychange', onVisibilityChange);
});
onBeforeUnmount(() => {
  stopVerification();
  if (typeof document !== 'undefined')
    document.removeEventListener?.('visibilitychange', onVisibilityChange);
});
watch(
  [() => props.api, () => props.lessonId, () => props.studentId, () => props.offline],
  () => void load('initial'),
  { immediate: true },
);
watch(
  () => props.realtimeRevision,
  () => {
    if (attachment.value || !state.value) void load('invalidated');
    else void load('periodic');
  },
);
async function load(reason: 'initial' | 'invalidated' | 'periodic' = 'initial'): Promise<void> {
  stopVerification();
  const { api, lessonId, studentId } = props;
  if (reason === 'initial') {
    state.value = null;
    entry.value = null;
    attachmentReady.value = false;
  }
  loading.value = false;
  verifying.value = reason !== 'periodic';
  error.value = null;
  if (!api || lessonId === null || studentId === null || props.offline || pageHidden()) return;

  const controller = new AbortController();
  request = controller;
  // A slow response must not delay the next check of the document.
  nextVerificationAt = performance.now() + recheckAfterMs;
  const { signal } = controller;
  const isCurrent = () =>
    request === controller &&
    props.api === api &&
    props.lessonId === lessonId &&
    props.studentId === studentId &&
    !props.offline &&
    !pageHidden();
  loading.value = !state.value;
  const deadline = setTimeout(
    () => controller.abort(new Error('Не удалось проверить доступ к документу вовремя.')),
    requestDeadlineMs,
  );
  try {
    // Race abort as well as fetch, so a slow session recovery cannot extend the deadline.
    const aborted = new Promise<never>((_, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true });
    });
    const response = await Promise.race([
      api.lesson(lessonId, { cache: 'no-store', signal }),
      aborted,
    ]);
    if (!isCurrent()) return;
    const student = response.roster.find((row) => row.studentId === studentId);
    if (
      response.lesson.id !== lessonId ||
      response.lesson.cancelled ||
      !student ||
      !isTeacherManualExcuse(student)
    ) {
      state.value = null;
      entry.value = null;
      error.value = 'Ручная причина для этой пары недоступна.';
      return;
    }
    state.value = response;
    entry.value = student;
  } catch (cause) {
    if (!isCurrent()) return;
    state.value = null;
    entry.value = null;
    if (cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof Error ? cause.message : 'Не удалось открыть причину отсутствия.';
    emit('error', cause);
  } finally {
    clearTimeout(deadline);
    if (request === controller) {
      request = null;
      loading.value = false;
      verifying.value = false;
      scheduleVerification();
    }
  }
}
</script>
<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-manual-excuse-title"
    :aria-busy="loading"
  >
    <header class="teacher-screen__detail-header">
      <button
        v-if="showBack"
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="teacher-manual-excuse-title">
        Причина отсутствия
      </h1>
    </header>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем причину…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Причина недоступна"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="load()"
    />
    <template v-else-if="state && entry">
      <article class="teacher-screen__ticket">
        <p>Причина указана старостой без заявки.</p>
        <dl class="teacher-screen__details">
          <div>
            <dt>Студент</dt><dd>{{ entry.displayName }}</dd>
          </div>
          <div>
            <dt>Причина</dt><dd>{{ reasonLabel }}</dd>
          </div>
          <div v-if="entry.excuseReason && entry.excuseReason.trim() !== reasonLabel.trim()">
            <dt>Описание</dt><dd>{{ entry.excuseReason }}</dd>
          </div>
          <div v-if="entry.excuseComment">
            <dt>Комментарий</dt><dd>{{ entry.excuseComment }}</dd>
          </div>
        </dl>
      </article>
      <section
        v-if="attachment"
        class="teacher-screen__section"
        aria-labelledby="teacher-manual-excuse-files"
      >
        <h2
          id="teacher-manual-excuse-files"
          class="teacher-screen__section-title"
        >
          Документ
        </h2>
        <TeacherAttachmentPreview
          :key="attachmentKey"
          :api="api"
          :lesson-id="lessonId"
          :student-id="studentId"
          :attachment="attachment"
          :close-signal="attachmentCloseSignal"
          :suspended="verifying"
          @availability="updateAvailability"
          @fullscreen-open="emit('attachment-open', $event)"
        />
      </section>
      <TeacherLessonCard
        :lesson="state.lesson"
        show-date
        :interactive="false"
      />
    </template>
    <p
      v-else
      class="teacher-screen__state"
      role="status"
    >
      Выбери ручную уважительную причину.
    </p>
  </main>
</template>
