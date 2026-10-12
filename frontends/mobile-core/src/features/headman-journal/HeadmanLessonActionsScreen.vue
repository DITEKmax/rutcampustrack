<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { HeadmanJournalApiError } from './headman-journal-client';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import type { HeadmanJournalLesson } from './headman-journal-client';
import type { HeadmanLessonAction } from './headman-journal-actions';
import type { HeadmanLessonActionsApi } from './headman-journal-actions-client';
import './headman-lesson-actions-screen.pcss';
const props = withDefaults(
  defineProps<{
    api: HeadmanLessonActionsApi | null;
    lesson: HeadmanJournalLesson;
    subjectName?: string;
    offline?: boolean;
    showHeader?: boolean;
  }>(),
  { showHeader: true, subjectName: 'Занятие группы', offline: false },
);
const emit = defineEmits<{ error: [cause: unknown] }>();
const items = shallowRef<readonly HeadmanLessonAction[]>([]);
const nextCursor = ref<string | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const filter = ref<'all' | 'marks' | 'lesson' | 'homework'>('all');
let revision = 0;
let disposed = false;
const visibleItems = computed(() =>
  items.value.filter(
    (item) =>
      filter.value === 'all' ||
      (filter.value === 'lesson' &&
        (item.action.startsWith('LESSON_') || item.action === 'ROOM_CHANGED')) ||
      (filter.value === 'homework' && item.action.startsWith('HOMEWORK_')) ||
      (filter.value === 'marks' && /^(ATTENDANCE_|GEO_|AUTO_|REQUEST_)/.test(item.action)),
  ),
);
function title(item: HeadmanLessonAction): string {
  const labels: Record<string, string> = {
    ATTENDANCE_SET: 'Отметка поставлена',
    ATTENDANCE_CLEARED: 'Отметка снята',
    GEO_ATTEMPT: 'Попытка геоотметки',
    AUTO_ABSENT: 'Автоматическая отметка «н»',
    REQUEST_SUBMITTED: 'Заявка подана',
    REQUEST_DECIDED: 'По заявке принято решение',
    REQUEST_CANCELLED: 'Заявка отменена',
    ROOM_CHANGED: 'Аудитория изменена',
    LESSON_CREATED: 'Пара создана',
    LESSON_EDITED: 'Пара изменена',
    LESSON_CANCELLED: 'Пара отменена',
    LESSON_RESTORED: 'Пара восстановлена',
    LESSON_TRANSFERRED: 'Пара перенесена',
    HOMEWORK_CREATED: 'Выдано домашнее задание',
    HOMEWORK_EDITED: 'Домашнее задание изменено',
    HOMEWORK_ARCHIVED: 'Домашнее задание архивировано',
  };
  if (item.action === 'GEO_ATTEMPT' && item.outcome === 'REJECTED') return 'Отклонение геоотметки';
  const label = labels[item.action] ?? `Событие: ${item.action}`;
  return label;
}
function rejectionReason(item: HeadmanLessonAction): string {
  const reasons: Record<string, string> = {
    CHECKIN_NOT_ELIGIBLE: 'Отметка сейчас недоступна',
    CHECKIN_COOLDOWN: 'Повторная попытка пока недоступна',
    MANUAL_ABSENCE_REQUIRES_APPEAL: 'Для ручной отметки «н» нужна заявка',
    IDEMPOTENCY_PAYLOAD_MISMATCH: 'Содержимое повторного запроса изменилось',
    LESSON_NOT_EDITABLE: 'Пара недоступна для изменения',
    ATTENDANCE_REVISION_CONFLICT: 'Отметка уже изменилась',
    ATTENDANCE_WRITE_CONFLICT: 'Занятие или семестр уже изменились',
  };
  return item.errorCode
    ? (reasons[item.errorCode] ?? 'Сервер отклонил действие')
    : 'Сервер отклонил действие';
}
function timestamp(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value));
}
function lessonType(value: string | null): string {
  return value === 'LECTURE'
    ? 'Лекция'
    : value === 'PRACTICE'
      ? 'Практика'
      : value === 'LAB'
        ? 'Лабораторная'
        : (value ?? 'Тип не указан');
}
function changeText(item: HeadmanLessonAction): string | null {
  if (item.action === 'ROOM_CHANGED') {
    const room = (value: unknown) =>
      typeof value === 'string' && value.trim() ? value : 'Не указана';
    return room(item.before?.room_snapshot) + ' → ' + room(item.after?.room_snapshot);
  }
  const status = (value: unknown) =>
    value === 'PRESENT'
      ? '+'
      : value === 'EXCUSED'
        ? 'у'
        : value === 'ABSENT'
          ? 'н'
          : value === 'PLANNED'
            ? 'Предстоит'
            : value === 'ACTIVE'
              ? 'Открыта'
              : value === 'CLOSED'
                ? 'Закрыта'
                : value === 'CANCELLED'
                  ? 'Отменена'
                  : value === 'TRANSFERRED'
                    ? 'Перенесена'
                    : null;
  const before = status(item.before?.status);
  const after = status(item.after?.status);
  return before || after ? (before ?? 'Без отметки') + ' → ' + (after ?? 'Без отметки') : null;
}
function markTransition(item: HeadmanLessonAction): boolean {
  return (
    item.outcome === 'APPLIED' &&
    (item.action === 'ATTENDANCE_CLEARED' ||
      ['PRESENT', 'EXCUSED', 'ABSENT'].includes(String(item.before?.status)) ||
      ['PRESENT', 'EXCUSED', 'ABSENT'].includes(String(item.after?.status)))
  );
}
function markStatus(value: unknown): string {
  return ['PRESENT', 'EXCUSED', 'ABSENT'].includes(String(value))
    ? String(value).toLowerCase()
    : 'empty';
}
function markSymbol(value: unknown): string {
  return value === 'PRESENT' ? '+' : value === 'EXCUSED' ? 'у' : value === 'ABSENT' ? 'н' : '—';
}
function markLabel(value: unknown): string {
  return value === 'PRESENT'
    ? 'Присутствует'
    : value === 'EXCUSED'
      ? 'Уважительная причина'
      : value === 'ABSENT'
        ? 'Отсутствует'
        : 'Без отметки';
}
function authorName(item: HeadmanLessonAction): string | null {
  if (
    !item.actor ||
    item.actor.userId === item.student?.userId ||
    !['HEADMAN', 'ASSISTANT', 'HEADMAN_ASSISTANT'].includes(item.actor.role?.toUpperCase() ?? '')
  )
    return null;
  return item.actor.displayName ?? 'Автор не указан';
}
async function load(more = false): Promise<void> {
  if (loading.value && more) return;
  const run = ++revision;
  const api = props.api;
  const lessonId = props.lesson.id;
  const cursor = more ? nextCursor.value : null;
  if (!more) {
    items.value = [];
    nextCursor.value = null;
  }
  error.value = null;
  if (!api || props.offline) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const page = await api.getLessonActions(lessonId, cursor);
    if (
      disposed ||
      run !== revision ||
      api !== props.api ||
      lessonId !== props.lesson.id ||
      props.offline
    )
      return;
    const existing = more ? items.value : [];
    const ids = new Set(existing.map((item) => item.id));
    items.value = [...existing, ...page.items.filter((item) => !ids.has(item.id))];
    nextCursor.value = page.nextCursor;
  } catch (cause) {
    if (
      disposed ||
      run !== revision ||
      api !== props.api ||
      lessonId !== props.lesson.id ||
      props.offline ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    if (cause instanceof HeadmanJournalApiError && [401, 403].includes(cause.response.status)) {
      items.value = [];
      nextCursor.value = null;
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить журнал действий.';
    emit('error', cause);
  } finally {
    if (!disposed && run === revision) loading.value = false;
  }
}
watch(
  () => [props.api, props.lesson.id, props.offline] as const,
  () => {
    void load();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(() => {
  disposed = true;
  revision += 1;
});
</script>
<template>
  <main
    class="headman-actions"
    :aria-labelledby="showHeader ? 'headman-actions-title' : undefined"
    :aria-label="showHeader ? undefined : 'Журнал действий'"
    :aria-busy="loading"
    :data-lesson-id="lesson.id"
  >
    <h1
      v-if="showHeader"
      id="headman-actions-title"
    >
      Журнал действий
    </h1>
    <section
      class="headman-actions__context"
      aria-label="Выбранная пара"
    >
      <h2>{{ subjectName }}</h2
      ><p
        >{{ lesson.date }} · {{ lesson.startTime?.slice(0, 5) ?? '—' }}–{{
          lesson.endTime?.slice(0, 5) ?? '—'
        }}</p
      ><p><MobileIcon name="lesson" />{{ lessonType(lesson.lessonType) }}</p>
    </section>
    <div
      class="headman-actions__filters"
      role="group"
      aria-label="События пары"
    >
      <button
        v-for="choice in [
          ['all', 'Все'],
          ['marks', 'Отметки'],
          ['lesson', 'Пара'],
          ['homework', 'ДЗ'],
        ] as const"
        :key="choice[0]"
        type="button"
        :aria-pressed="filter === choice[0]"
        @click="filter = choice[0]"
      >
        {{ choice[1] }}
      </button>
    </div>
    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Журнал действий доступен при подключении к интернету."
    />
    <StudentWarningBlock
      v-else-if="error"
      title="Журнал недоступен"
      :message="error"
      action-label="Повторить"
      @action="load(items.length > 0)"
    />
    <p
      v-else-if="loading && !items.length"
      role="status"
    >
      Загружаем журнал…
    </p>
    <p
      v-else-if="!visibleItems.length"
      class="headman-actions__empty"
      role="status"
    >
      {{ items.length ? 'Событий этого типа нет.' : 'Сервер ещё не вернул событий по этой паре.' }}
    </p>
    <ol class="headman-actions__list">
      <li
        v-for="item in visibleItems"
        :key="item.id"
        :data-event-id="item.id"
        :data-mark="markTransition(item) ? markStatus(item.after?.status) : undefined"
        :data-outcome="item.outcome"
      >
        <div class="headman-actions__event-header">
          <strong>{{
            item.action.startsWith('HOMEWORK_')
              ? title(item)
              : item.student
                ? (item.student.displayName ?? 'Студент')
                : title(item)
          }}</strong>
          <div class="headman-actions__event-meta">
            <time :datetime="item.occurredAt">{{ timestamp(item.occurredAt) }}</time>
            <span
              v-if="authorName(item)"
              class="headman-actions__actor"
              >{{ authorName(item) }}</span
            >
          </div>
        </div>
        <template v-if="!item.action.startsWith('HOMEWORK_')">
          <div
            v-if="markTransition(item)"
            class="headman-actions__transition"
            :aria-label="`${markLabel(item.before?.status)} → ${markLabel(item.after?.status)}`"
          >
            <span
              class="headman-actions__mark"
              :data-status="markStatus(item.before?.status)"
              aria-hidden="true"
              >{{ markSymbol(item.before?.status) }}</span
            >
            <span aria-hidden="true">→</span>
            <span
              class="headman-actions__mark"
              :data-status="markStatus(item.after?.status)"
              aria-hidden="true"
              >{{ markSymbol(item.after?.status) }}</span
            >
          </div>
          <p v-else-if="item.student">
            {{ title(item) }}
          </p>
          <p
            v-if="item.outcome === 'REJECTED' && item.action !== 'GEO_ATTEMPT'"
            class="headman-actions__rejected"
          >
            Отклонено · {{ rejectionReason(item) }}
          </p>
          <p v-if="item.outcome === 'APPLIED' && !markTransition(item) && changeText(item)">
            {{ changeText(item) }}
          </p>
          <template v-if="item.action === 'GEO_ATTEMPT' || item.geo">
            <p v-if="item.geo?.latitude != null && item.geo?.longitude != null">
              Координаты: {{ item.geo.latitude }}, {{ item.geo.longitude }}
            </p>
            <p v-else> Координаты недоступны </p>
            <p v-if="item.geo?.distanceMeters != null">
              {{ item.geo.distanceMeters }} м от центра вуза
            </p>
            <p v-else> Расстояние недоступно </p>
          </template>
          <p v-if="item.ticket">
            Заявка
            {{
              item.ticket.status === 'APPROVED'
                ? 'одобрена'
                : item.ticket.status === 'PENDING'
                  ? 'ожидает решения'
                  : item.ticket.status === 'REJECTED'
                    ? 'отклонена'
                    : item.ticket.status === 'CANCELLED'
                      ? 'отменена'
                      : item.ticket.status
            }}
          </p>
        </template>
      </li>
    </ol>
    <button
      v-if="nextCursor"
      type="button"
      :disabled="loading || offline"
      @click="load(true)"
    >
      {{ loading ? 'Загружаем…' : 'Показать ещё' }}
    </button>
  </main>
</template>
