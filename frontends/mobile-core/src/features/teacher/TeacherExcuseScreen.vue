<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { teacherExcuseTypeLabel } from './teacher-ui-format';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import TeacherLessonCard from './TeacherLessonCard.vue';
import TeacherAttachmentPreview from './TeacherAttachmentPreview.vue';
import { TeacherApiError, type TeacherApi, type TeacherExcuseResponse } from './teacher-client';
import './teacher-screen.pcss';
const props = withDefaults(
  defineProps<{ api: TeacherApi | null; requestId: string | null; showBack?: boolean }>(),
  { showBack: true },
);
const emit = defineEmits<{ back: []; error: [cause: unknown] }>();
const state = ref<TeacherExcuseResponse | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
let revision = 0;
watch(
  () => [props.api, props.requestId] as const,
  () => {
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  revision += 1;
});
async function load(): Promise<void> {
  const api = props.api;
  const id = props.requestId;
  const run = ++revision;
  state.value = null;
  error.value = null;
  loading.value = false;
  if (!api || !id) return;
  loading.value = true;
  try {
    const value = await api.excuse(id);
    if (run === revision) state.value = value;
  } catch (cause) {
    if (run !== revision || cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть заявление.';
    emit('error', cause);
  } finally {
    if (run === revision) loading.value = false;
  }
}
function instant(value: string | null): string {
  if (!value) return 'Дата не указана';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'Дата не указана'
    : new Intl.DateTimeFormat('ru-RU', {
        day: 'numeric',
        month: 'long',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
}
function status(value: string): string {
  return (
    (
      {
        SUBMITTED: 'Ожидает решения',
        APPROVED: 'Одобрена',
        REJECTED: 'Отклонена',
        CANCELLED: 'Отменена',
      } as Record<string, string>
    )[value.toUpperCase()] ?? 'Статус уточняется'
  );
}
function kind(value: string): string {
  return (
    (
      {
        EXCUSE: 'Уважительная причина',
        LATE_CHECKIN: 'Запрос на отметку',
        DISPUTE: 'Оспаривание отметки',
      } as Record<string, string>
    )[value.toUpperCase()] ?? 'Заявление'
  );
}
function decisionName(value: TeacherExcuseResponse): string | null {
  return value.decisionByName;
}
</script>
<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-excuse-title"
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
      </button><h1 id="teacher-excuse-title">
        Причина отсутствия
      </h1>
    </header>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем заявление…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Заявление недоступно"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="load"
    />
    <template v-else-if="state">
      <article class="teacher-screen__ticket">
        <dl class="teacher-screen__details">
          <div>
            <dt>Автор</dt><dd>{{ state.studentName }}</dd>
          </div><div>
            <dt>Заявление</dt><dd>{{ kind(state.kind) }} · {{ status(state.status) }}</dd>
          </div><div>
            <dt>Подана</dt><dd>{{ instant(state.createdAt) }}</dd>
          </div><div>
            <dt>Решение старосты</dt><dd v-if="state.decisionAt">
              <template v-if="decisionName(state)">
                {{ decisionName(state) }} ·
              </template>{{ instant(state.decisionAt) }}
            </dd><dd v-else>
              Решение ещё не принято
            </dd>
          </div><div v-if="state.decisionComment">
            <dt>Комментарий к решению</dt><dd>{{ state.decisionComment }}</dd>
          </div>
        </dl>
      </article>
      <article class="teacher-screen__ticket">
        <dl class="teacher-screen__details">
          <div>
            <dt>Причина</dt><dd>{{ teacherExcuseTypeLabel(state.excuseType) }}</dd>
          </div><div
            v-if="
              state.reason &&
                state.reason.trim() !== teacherExcuseTypeLabel(state.excuseType).trim()
            "
          >
            <dt>Описание</dt><dd>{{ state.reason }}</dd>
          </div><div v-if="state.comment">
            <dt>Комментарий</dt><dd>{{ state.comment }}</dd>
          </div>
        </dl>
      </article>
      <section
        class="teacher-screen__section"
        aria-labelledby="teacher-excuse-lessons"
      >
        <h2
          id="teacher-excuse-lessons"
          class="teacher-screen__section-title"
        >
          Пары заявления
        </h2><ul class="teacher-screen__list">
          <li
            v-for="lesson in state.lessons"
            :key="lesson.id"
          >
            <TeacherLessonCard
              :lesson="lesson"
              show-date
              :interactive="false"
            />
          </li>
        </ul>
      </section>
      <section
        v-if="state.attachments.length"
        class="teacher-screen__section"
        aria-labelledby="teacher-excuse-files"
      >
        <h2
          id="teacher-excuse-files"
          class="teacher-screen__section-title"
        >
          Справка и вложения
        </h2><TeacherAttachmentPreview
          v-for="attachment in state.attachments"
          :key="attachment.id"
          :api="api"
          :request-id="state.id"
          :attachment="attachment"
        />
      </section>
    </template>
    <p
      v-else
      class="teacher-screen__state"
      role="status"
    >
      Выбери заявление из посещаемости пары.
    </p>
  </main>
</template>
