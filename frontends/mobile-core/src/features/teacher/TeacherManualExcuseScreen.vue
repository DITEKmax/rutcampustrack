<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherLessonResponse,
  type TeacherRosterEntry,
} from './teacher-client';
import { teacherExcuseTypeLabel } from './teacher-ui-format';
import { isTeacherManualExcuse } from './teacher-manual-excuse';
import TeacherLessonCard from './TeacherLessonCard.vue';
import './teacher-screen.pcss';

const props = withDefaults(
  defineProps<{
    api: TeacherApi | null;
    lessonId: number | null;
    studentId: number | null;
    showBack?: boolean;
  }>(),
  { showBack: true },
);
const emit = defineEmits<{ back: []; error: [cause: unknown] }>();
const state = ref<TeacherLessonResponse | null>(null);
const entry = ref<TeacherRosterEntry | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const reasonLabel = computed(() =>
  teacherExcuseTypeLabel(entry.value?.excuseType ?? entry.value?.excuseReason ?? null),
);
let revision = 0;
watch(
  () => [props.api, props.lessonId, props.studentId] as const,
  () => {
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  revision += 1;
});
async function load(): Promise<void> {
  const api = props.api,
    lessonId = props.lessonId,
    studentId = props.studentId,
    run = ++revision;
  state.value = null;
  entry.value = null;
  loading.value = false;
  error.value = null;
  if (!api || lessonId === null || studentId === null) return;
  loading.value = true;
  try {
    const response = await api.lesson(lessonId);
    if (run !== revision) return;
    const student = response.roster.find((row) => row.studentId === studentId);
    if (!student || !isTeacherManualExcuse(student)) {
      error.value = 'Ручная причина для этой пары недоступна.';
      return;
    }
    state.value = response;
    entry.value = student;
  } catch (cause) {
    if (run !== revision || cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть причину отсутствия.';
    emit('error', cause);
  } finally {
    if (run === revision) loading.value = false;
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
      <h1 id="teacher-manual-excuse-title"> Причина отсутствия </h1>
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
      @action="load"
    />
    <template v-else-if="state && entry">
      <article class="teacher-screen__ticket">
        <dl class="teacher-screen__details">
          <div
            ><dt>Студент</dt><dd>{{ entry.displayName }}</dd></div
          >
          <div
            ><dt>Причина</dt><dd>{{ reasonLabel }}</dd></div
          >
          <div v-if="entry.excuseReason && entry.excuseReason.trim() !== reasonLabel.trim()">
            <dt>Описание</dt><dd>{{ entry.excuseReason }}</dd>
          </div>
          <div v-if="entry.excuseComment">
            <dt>Комментарий</dt><dd>{{ entry.excuseComment }}</dd>
          </div>
        </dl>
      </article>
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
