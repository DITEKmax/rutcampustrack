<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import type {
  HeadmanJournalApi,
  HeadmanJournalExcuseType,
} from '../headman-journal/headman-journal-client';
import type {
  HeadmanRequestsApi,
  HeadmanRequestDetail,
} from '../headman-requests/headman-requests-client';
import type { HeadmanStatsApi } from './headman-stats-client';
import {
  headmanStatsHistoryFilterKey,
  type HeadmanStatsHistoryFilter,
  type HeadmanStatsLesson,
} from './headman-stats-history-client';
import { useHeadmanStatsHistory } from './use-headman-stats-history';
import RequestAttachmentPreview from '../requests/RequestAttachmentPreview.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import './headman-stats-history.pcss';

const props = withDefaults(
  defineProps<{
    filter?: HeadmanStatsHistoryFilter;
    api: HeadmanStatsApi | null;
    journalApi: HeadmanJournalApi | null;
    requestsApi: HeadmanRequestsApi | null;
    groupId: number | null;
    studentId: number;
    semesterId: number | null;
    offline: boolean;
    allowed: boolean;
    markAllowed: boolean;
    excuseAllowed: boolean;
  }>(),
  { filter: () => ({ subjectId: null, lessonTypes: [] }) },
);
const emit = defineEmits<{ refreshed: [] }>();
const controller = useHeadmanStatsHistory(
  () => ({
    api: props.api,
    journalApi: props.journalApi,
    groupId: props.groupId,
    studentId: props.studentId,
    semesterId: props.semesterId,
    ...props.filter,
    offline: props.offline,
    allowed: props.allowed,
    markAllowed: props.markAllowed,
    excuseAllowed: props.excuseAllowed,
  }),
  () => emit('refreshed'),
);
const { data, loading, error, feedback, pending, refreshing, rowIssues, confirmation, excuse, denied } =
  controller;
const dialog = ref<HTMLDialogElement | null>(null);
let returnFocus: HTMLButtonElement | null = null;
const excuseType = ref<HeadmanJournalExcuseType>('OTHER');
const comment = ref('');
const file = ref<File | null>(null);
const ticket = ref<HeadmanRequestDetail | null>(null);
const ticketError = ref<string | null>(null);
const ticketBusy = ref(false);
const ticketId = ref<string | null>(null);
const ticketLessonId = ref<number | null>(null);
let ticketRevision = 0;
const intent = computed(() => confirmation.value ?? excuse.value);
const grouped = computed(() => {
  const groups = new Map<string, HeadmanStatsLesson[]>();
  for (const lesson of data.value?.items ?? [])
    groups.set(lesson.lessonDate, [...(groups.get(lesson.lessonDate) ?? []), lesson]);
  return [...groups].map(([date, lessons]) => ({ date, lessons }));
});
const labels = { PRESENT: '+', EXCUSED: 'у', ABSENT: 'н' };
const words = { PRESENT: 'Присутствовал', EXCUSED: 'Уважительная причина', ABSENT: 'Отсутствовал' };
const kinds = ['PRESENT', 'EXCUSED', 'ABSENT'] as const;
const reasons: readonly { value: HeadmanJournalExcuseType; label: string }[] = [
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Соревнования' },
  { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' },
  { value: 'UNIVERSITY_ORDER', label: 'Приказ университета' },
  { value: 'EXEMPTION', label: 'Освобождение' },
  { value: 'OTHER', label: 'Другая причина' },
];
function dateText(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(value + 'T12:00:00Z'));
}
function timeText(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Europe/Moscow',
  }).format(new Date(value));
}
function typeText(value: string): string {
  return { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' }[value] ?? value;
}
function restoreRowFocus(trigger: HTMLButtonElement | null, row: HTMLElement | null): void {
  if (!row?.isConnected) return;
  const active = document.activeElement;
  if (active !== document.body && active !== trigger && active !== row && !dialog.value?.contains(active)) return;
  if (trigger?.isConnected && !trigger.disabled && trigger.getAttribute('aria-disabled') !== 'true')
    trigger.focus({ preventScroll: true });
  else {
    const action = row.querySelector<HTMLElement>(
      '.headman-stats-history__choice:not(:disabled):not([aria-disabled="true"]), .headman-stats-history__retry:not(:disabled)',
    );
    (action ?? row).focus({ preventScroll: true });
  }
}
async function choose(
  lesson: HeadmanStatsLesson,
  status: (typeof kinds)[number],
  event: MouseEvent,
): Promise<void> {
  if (!controller.canEdit(lesson)) return;
  const trigger = event.currentTarget as HTMLButtonElement;
  const row = trigger.closest<HTMLElement>('li');
  returnFocus = trigger;
  const action = controller.choose(lesson, status);
  if (!action) return;
  await action;
  await nextTick();
  restoreRowFocus(trigger, row);
}
watch(intent, (value) => {
  if (value) {
    excuseType.value = 'OTHER';
    comment.value = '';
    file.value = null;
    void nextTick(() => dialog.value?.showModal());
  } else {
    dialog.value?.close();
    const trigger = returnFocus;
    const row = trigger?.closest<HTMLElement>('li') ?? null;
    void nextTick(() => restoreRowFocus(trigger, row));
  }
});
async function finishDialog(action: () => Promise<void> | undefined): Promise<void> {
  const trigger = returnFocus;
  const row = trigger?.closest<HTMLElement>('li') ?? null;
  await action();
  await nextTick();
  restoreRowFocus(trigger, row);
}
function saveExcuse(): Promise<void> {
  return finishDialog(() => controller.saveExcuse({
    status: 'EXCUSED',
    excuseType: excuseType.value,
    comment: comment.value.trim() || null,
    file: file.value,
  }));
}
async function retryRow(lessonId: number, event: MouseEvent): Promise<void> {
  const trigger = event.currentTarget as HTMLButtonElement;
  const row = trigger.closest<HTMLElement>('li');
  await controller.retryRow(lessonId);
  await nextTick();
  restoreRowFocus(trigger, row);
}
function readFile(event: Event): void {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null;
}
function filterKey(): string {
  return headmanStatsHistoryFilterKey(props.filter.subjectId, props.filter.lessonTypes);
}
function ownerKey(): string {
  return `${props.groupId}/${props.studentId}/${props.semesterId}/${filterKey()}/${props.offline}/${props.allowed}/${denied.value}`;
}
function resetTicket(): void {
  ticketRevision += 1;
  ticket.value = null;
  ticketId.value = null;
  ticketLessonId.value = null;
  ticketError.value = null;
  ticketBusy.value = false;
}
async function openTicket(lesson: HeadmanStatsLesson): Promise<void> {
  const api = props.requestsApi;
  const link = lesson.approvedTicket;
  if (!api || !link || denied.value || props.offline || !props.excuseAllowed || ticketBusy.value)
    return;
  if (ticketLessonId.value === lesson.lessonId && ticket.value) {
    resetTicket();
    return;
  }
  const revision = ++ticketRevision;
  ticketLessonId.value = lesson.lessonId;
  const owner = ownerKey();
  ticket.value = null;
  ticketError.value = null;
  ticketId.value = link.id;
  ticketBusy.value = true;
  try {
    const result = await api.get(link.id);
    if (revision !== ticketRevision || api !== props.requestsApi || owner !== ownerKey()) return;
    if (
      result.summary.id !== link.id ||
      result.summary.studentId !== props.studentId ||
      result.summary.kind !== link.kind
    )
      throw new Error('Сервер вернул другую заявку.');
    ticket.value = result;
  } catch (cause) {
    if (revision === ticketRevision && api === props.requestsApi && owner === ownerKey())
      ticketError.value = cause instanceof Error ? cause.message : 'Не удалось открыть заявку.';
  } finally {
    if (revision === ticketRevision) ticketBusy.value = false;
  }
}
watch(
  [
    () => props.api,
    () => props.requestsApi,
    () => props.journalApi,
    () => props.groupId,
    () => props.studentId,
    () => props.semesterId,
    filterKey,
    () => props.offline,
    () => props.allowed,
    () => props.excuseAllowed,
    denied,
  ],
  () => {
    resetTicket();
    file.value = null;
    comment.value = '';
  },
  { flush: 'sync' },
);
watch(data, (page) => {
  if (ticketLessonId.value === null) return;
  const link = page?.items.find((item) => item.lessonId === ticketLessonId.value)?.approvedTicket;
  if (!link || link.id !== ticketId.value || (ticket.value && link.kind !== ticket.value.summary.kind))
    resetTicket();
}, { flush: 'sync' });
onBeforeUnmount(() => {
  ticketRevision += 1;
  dialog.value?.close();
});
function back(): boolean {
  if (intent.value) {
    controller.cancel();
    return true;
  }
  return false;
}
defineExpose({ ready: computed(() => data.value !== null), controller, back });
</script>
<template>
  <section
    class="headman-stats-history"
    aria-label="Прошедшие пары"
    :aria-busy="loading"
  >
    <h2
      v-if="!data || data.items.length || loading"
      id="headman-stats-history-title"
    >
      Прошедшие пары
    </h2>
    <p
      v-if="offline"
      role="status"
    >
      История доступна только при подключении к интернету.
    </p>
    <p
      v-if="feedback"
      class="statistics-visually-hidden"
      role="status"
    >
      {{ feedback }}
    </p>
    <StudentWarningBlock
      v-if="error"
      title="История недоступна"
      :message="error"
      action-label="Повторить"
      :action-disabled="loading || offline || pending !== null || refreshing !== null"
      @action="controller.load"
    />
    <p
      v-if="loading"
      role="status"
    >
      Загружаем историю занятий…
    </p>
    <p
      v-if="data && !data.items.length && !loading"
      class="headman-stats-history__empty"
      role="status"
    >
      За выбранный семестр прошедших пар пока нет.
    </p>
    <section
      v-for="group in grouped"
      :key="group.date"
      class="headman-stats-history__day"
    >
      <h3>{{ dateText(group.date) }}</h3><ul>
        <li
          v-for="lesson in group.lessons"
          :key="lesson.lessonId"
          class="headman-stats-history__lesson"
          :class="{ 'headman-stats-history__lesson--empty': lesson.status === null }"
          tabindex="-1"
          :aria-busy="pending === lesson.lessonId || refreshing === lesson.lessonId"
        >
          <div class="headman-stats-history__lesson-main">
            <strong>{{ lesson.subjectName }}</strong><span>{{ timeText(lesson.startsAt) }} · {{ lesson.room || 'Аудитория не указана' }} ·
              {{ typeText(lesson.lessonType) }}</span><small v-if="!lesson.editable">{{
              lesson.blockedReason || 'Только просмотр'
            }}</small>
          </div><div
            class="headman-stats-history__choices"
            role="group"
            :aria-label="`Отметка: ${lesson.subjectName}, ${dateText(lesson.lessonDate)}`"
          >
            <button
              v-for="status in lesson.status ? [lesson.status] : kinds"
              :key="status"
              type="button"
              :class="[
                'headman-stats-history__choice',
                lesson.status
                  ? 'headman-stats-history__choice--' + status.toLowerCase()
                  : 'headman-stats-history__choice--empty',
              ]"
              :disabled="
                !allowed || offline || !journalApi?.writable || !lesson.editable ||
                  (status === 'EXCUSED' ? !excuseAllowed : !markAllowed)
              "
              :aria-disabled="!controller.canEdit(lesson)"
              :aria-label="
                lesson.status
                  ? `Снять отметку: ${words[status]}`
                  : `Поставить отметку: ${words[status]}`
              "
              @click="choose(lesson, status, $event)"
            >
              {{ labels[status] }}
            </button>
          </div>
          <div
            v-if="rowIssues.has(lesson.lessonId) && pending !== lesson.lessonId"
            class="headman-stats-history__error"
          >
            <p role="status">{{ rowIssues.get(lesson.lessonId)?.message }}</p>
            <button
              v-if="rowIssues.get(lesson.lessonId)?.needsRead"
              class="headman-stats-history__retry"
              type="button"
              :disabled="loading || pending !== null"
              :aria-disabled="refreshing !== null"
              @click="retryRow(lesson.lessonId, $event)"
            >
              Обновить отметку
            </button>
          </div>
          <button
            v-if="lesson.approvedTicket"
            class="headman-stats-history__ticket"
            type="button"
            :disabled="!requestsApi || offline || !excuseAllowed || ticketBusy"
            :aria-expanded="ticketLessonId === lesson.lessonId"
            @click="openTicket(lesson)"
          >
            {{
              ticketLessonId === lesson.lessonId && ticket
                ? 'Скрыть заявку'
                : 'Открыть принятую заявку ' +
                  (lesson.approvedTicket.kind === 'EXCUSE' ? '«у»' : '«н»')
            }}
          </button>
          <section
            v-if="ticketLessonId === lesson.lessonId && (ticket || ticketError || ticketBusy)"
            class="headman-stats-history__request"
            aria-label="Принятая заявка"
          >
            <p
              v-if="ticketBusy"
              role="status"
            >
              Загружаем…
            </p><p
              v-if="ticketError"
              role="alert"
            >
              {{ ticketError }}
            </p><template v-if="ticket">
              <span
                class="request-status-badge"
                :data-status="ticket.summary.status"
              >{{
                ticket.summary.status === 'APPROVED' ? 'Одобрена' : ticket.summary.status
              }}</span>
              <p v-if="ticket.summary.reason">
                {{ ticket.summary.reason }}
              </p><p v-if="ticket.summary.comment">
                {{ ticket.summary.comment }}
              </p>
              <RequestAttachmentPreview
                v-for="attachment in ticket.attachments"
                :key="attachment.id"
                :preview-only="true"
                :tap-to-open="true"
                :api="requestsApi"
                :request-id="ticket.summary.id"
                :attachment="attachment"
                :owner-key="ownerKey()"
                :allowed="allowed && excuseAllowed && !denied"
                :offline="offline"
              />
            </template>
          </section>
        </li>
      </ul>
    </section>
    <nav
      v-if="data && data.totalPages > 1"
      class="headman-stats-history__pages"
      aria-label="Страницы истории"
    >
      <button
        type="button"
        :disabled="!data.hasPrevious || loading || pending !== null || refreshing !== null"
        @click="controller.changePage(data.page - 1)"
      >
        Назад
      </button><span>{{ data.page + 1 }} из {{ data.totalPages }}</span><button
        type="button"
        :disabled="!data.hasNext || loading || pending !== null || refreshing !== null"
        @click="controller.changePage(data.page + 1)"
      >
        Дальше
      </button>
    </nav>
    <dialog
      ref="dialog"
      class="headman-stats-history__dialog"
      aria-labelledby="headman-stats-confirm-title"
      @cancel.prevent="controller.cancel"
    >
      <template v-if="intent">
        <h2 id="headman-stats-confirm-title">
          {{ confirmation ? 'Снять уважительную отметку?' : 'Уважительная причина' }}
        </h2><p>{{ dateText(intent.lesson.lessonDate) }} · {{ intent.lesson.subjectName }}</p><template v-if="confirmation">
          <p>
            {{ "Если у отметки есть файл, добавленный старостой, он будет удалён. Принятая заявка студента и её файлы сохранятся." }}
          </p><div class="headman-stats-history__dialog-actions">
            <button
              type="button"
              autofocus
              data-action="cancel"
              @click="controller.cancel"
            >
              Отмена
            </button><button
              type="button"
              data-action="clear"
              @click="finishDialog(controller.confirm)"
            >
              Очистить
            </button>
          </div>
        </template><form
          v-else
          @submit.prevent="saveExcuse"
        >
          <label>Причина<select v-model="excuseType"><option
            v-for="reason in reasons"
            :key="reason.value"
            :value="reason.value"
          >{{ reason.label }}</option></select></label><label>Комментарий<textarea v-model="comment" /></label><label class="mobile-upload"><input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            @change="readFile"
          ><MobileIcon name="homework" /><span>{{ file ? file.name : 'Прикрепить файл' }}</span><small>JPEG, PNG или PDF · до 10 МиБ</small></label><div class="headman-stats-history__dialog-actions">
            <button
              type="button"
              data-action="cancel"
              @click="controller.cancel"
            >
              Отмена
            </button><button
              type="submit"
              data-action="confirm"
            >
              Сохранить
            </button>
          </div>
        </form>
      </template>
    </dialog>
  </section>
</template>
