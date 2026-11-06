<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client';
import HeadmanLinkedRequest from './HeadmanLinkedRequest.vue';
import HeadmanManualExcuse from './HeadmanManualExcuse.vue';
import type { HeadmanJournalApi, HeadmanJournalRosterEntry } from './headman-journal-client';
import {
  manualStatusLabel,
  type ManualMarkDraft,
  type useHeadmanManualMarks,
} from './headman-manual-marks';
import './headman-manual-attendance.pcss';

const props = defineProps<{
  manual: ReturnType<typeof useHeadmanManualMarks>;
  draft: ManualMarkDraft;
  entry: HeadmanJournalRosterEntry;
  api: HeadmanJournalApi | null;
  requestsApi: HeadmanRequestsApi | null;
  lessonId: number;
  context: string;
}>();
const emit = defineEmits<{ error: [cause: unknown]; accessDenied: [cause: unknown] }>();
const titleId = useId();
const cancel = ref<HTMLButtonElement | null>(null);
const back = ref<HTMLButtonElement | null>(null);
const remove = ref<HTMLButtonElement | null>(null);
const confirming = computed(() => props.draft.kind === 'replace' || props.draft.removing);
const pending = computed(() => props.manual.pendingUserId.value === props.entry.userId);
const stale = computed(() => props.manual.staleRows.value.has(props.entry.userId));
const issue = computed(() => props.manual.issues.value[props.entry.userId] ?? null);
const writable = computed(() => props.manual.canWrite(props.entry, props.draft.choice));
const enabled = computed(
  () => writable.value && !props.manual.blocked.value && issue.value?.kind !== 'conflict',
);
const title = computed(() =>
  props.draft.kind === 'replace'
    ? 'Изменить отметку?'
    : confirming.value
      ? 'Снять отметку?'
      : props.draft.kind === 'linked'
        ? 'Информация о заявке'
        : 'Уважительная отметка',
);
const confirmLabel = computed(() =>
  props.draft.kind === 'replace' ? 'Изменить отметку' : 'Подтвердить снятие',
);

onMounted(() => (confirming.value ? cancel.value : back.value)?.focus?.());
watch(confirming, (open) => void nextTick(() => (open ? cancel.value : remove.value)?.focus?.()));
</script>

<template>
  <div
    class="headman-manual-task"
    @keydown.esc.stop.prevent="manual.closeDraft"
  >
    <div
      v-if="draft.kind === 'reason'"
      :hidden="confirming"
    >
      <HeadmanManualExcuse
        :entry="entry"
        :lesson-id="lessonId"
        :api="api"
        :context="context"
        :allowed="writable"
        :pending="pending"
        :blocked="manual.blocked.value"
        :stale="stale"
        :issue="issue"
        @back="manual.closeDraft"
        @save="manual.submitExcuse"
        @review-conflict="manual.reviewConflict"
        @refresh="manual.refreshRow(entry.userId, $event)"
        @access-denied="emit('accessDenied', $event)"
      />
    </div>
    <section
      v-else-if="draft.kind !== 'replace'"
      :hidden="confirming"
      class="headman-manual-task__details"
      :aria-labelledby="titleId"
    >
      <header class="headman-manual-excuse__heading">
        <button
          ref="back"
          class="headman-manual-excuse__back"
          type="button"
          aria-label="Назад"
          @click="manual.closeDraft"
        >
          <MobileIcon name="back" />
        </button>
        <h1 :id="confirming ? undefined : titleId">
          {{ title }}
        </h1>
      </header>
      <HeadmanLinkedRequest
        v-if="draft.kind === 'linked' && draft.ticket"
        :api="requestsApi"
        :ticket="draft.ticket"
        :student-id="entry.userId"
        :allowed="manual.canChoose(entry, 'EXCUSED')"
        :offline="false"
        @error="emit('error', $event)"
      />
      <p v-else>
        Источник этой отметки не подтверждён. Изменить её причину здесь нельзя.
      </p>
    </section>
    <button
      v-if="!confirming && entry.status === 'EXCUSED' && writable"
      ref="remove"
      class="headman-manual-task__action"
      type="button"
      :disabled="!enabled"
      @click="manual.requestRemoval"
    >
      Снять отметку
    </button>
    <section
      v-if="confirming"
      class="headman-manual-task__details"
      :aria-labelledby="titleId"
      :aria-busy="pending"
    >
      <h1 :id="titleId">
        {{ title }}
      </h1>
      <p><strong>{{ entry.displayName }}</strong> · {{ context }}</p>
      <p v-if="draft.kind === 'replace'">
        Статус изменится с «Уважительная причина» на «{{ manualStatusLabel[draft.replacement] }}».
      </p>
      <p v-else>
        Уважительная отметка будет снята с этой пары.
      </p>
      <p v-if="draft.ticket">
        Решение по заявке и её вложения останутся в архиве.
      </p>
      <p v-if="entry.attachmentId">
        Документ, прикреплённый к самой отметке, будет удалён.
      </p>
      <p
        v-if="issue"
        class="headman-manual-excuse__error"
        role="alert"
      >
        {{ issue.message }}
      </p>
      <button
        v-if="stale"
        class="headman-manual-task__action"
        type="button"
        :disabled="manual.blocked.value"
        @click="manual.refreshRow(entry.userId, $event.currentTarget)"
      >
        Обновить отметку
      </button>
      <div class="headman-manual-task__actions">
        <button
          ref="cancel"
          class="headman-manual-task__action"
          type="button"
          :disabled="pending"
          @click="manual.closeDraft"
        >
          Отмена
        </button>
        <button
          class="headman-manual-task__action"
          type="button"
          data-confirm
          :disabled="!enabled"
          @click="manual.confirmChange"
        >
          {{ pending ? 'Сохраняем…' : confirmLabel }}
        </button>
      </div>
    </section>
  </div>
</template>
