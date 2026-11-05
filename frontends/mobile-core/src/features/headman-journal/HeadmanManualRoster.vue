<script setup lang="ts">
import { ref } from 'vue';
import type { HeadmanJournalRosterEntry } from './headman-journal-client';
import {
  manualChoices,
  manualStatusLabel,
  manualStatusSymbol,
  markCondition,
  rosterName,
  type ManualChoice,
  type ManualRowIssue,
} from './headman-manual-marks';
import './headman-manual-attendance.pcss';

defineProps<{
  entries: readonly HeadmanJournalRosterEntry[];
  label?: string;
  pendingUserId: number | null;
  blocked: boolean;
  future: boolean;
  issues: Readonly<Record<number, ManualRowIssue>>;
  staleRows: ReadonlySet<number>;
  canWrite: (entry: HeadmanJournalRosterEntry, choice: ManualChoice) => boolean;
}>();
const emit = defineEmits<{
  choose: [entry: HeadmanJournalRosterEntry, choice: ManualChoice, source: EventTarget | null];
  refresh: [userId: number, source: EventTarget | null];
}>();

const roster = ref<HTMLOListElement | null>(null);
function focus(userId: number, choice: ManualChoice): void {
  const row = roster.value?.querySelector?.<HTMLElement>(`[data-user-id="${userId}"]`);
  const control =
    row?.querySelector<HTMLButtonElement>(`button[data-choice="${choice}"]:enabled`) ??
    row?.querySelector<HTMLButtonElement>('button[data-choice]:enabled');
  (control ?? row)?.focus({ preventScroll: true });
}
defineExpose({ focus });

function sourceLabel(source: string | null): string {
  return source === 'student_geo'
    ? 'Геоотметка'
    : source === 'headman' || source === 'headman_excuse'
      ? 'Отметка старосты'
      : '';
}
</script>

<template>
  <ol
    ref="roster"
    class="headman-manual-roster"
    :aria-label="label ?? 'Состав группы'"
  >
    <li
      v-for="entry in entries"
      :key="entry.userId"
      class="headman-manual-roster__row"
      :data-user-id="entry.userId"
      :data-status="entry.status.toLowerCase()"
      :aria-busy="pendingUserId === entry.userId"
      tabindex="-1"
    >
      <div class="headman-manual-roster__main">
        <span
          class="headman-manual-roster__name"
          :title="entry.displayName"
        >
          <span aria-hidden="true">{{ rosterName(entry.displayName) }}</span>
          <span class="headman-manual__sr-only">{{ entry.displayName }}</span>
        </span>
        <div
          v-if="!future && entry.status !== 'CANCELLED' && entry.status !== 'UNSUPPORTED'"
          class="headman-manual-roster__choices"
          role="group"
          :aria-label="`Отметка: ${entry.displayName}`"
        >
          <button
            v-for="choice in manualChoices"
            :key="choice"
            type="button"
            :data-choice="choice"
            :disabled="!canWrite(entry, choice) && !staleRows.has(entry.userId)"
            :aria-disabled="blocked || staleRows.has(entry.userId) ? true : undefined"
            :aria-pressed="entry.status === choice"
            :aria-label="`${manualStatusLabel[choice]}: ${entry.displayName}${entry.status === choice && sourceLabel(entry.source) ? `. ${sourceLabel(entry.source)}` : ''}`"
            @click="emit('choose', entry, choice, $event.currentTarget)"
          >
            <span
              class="headman-manual__circle"
              :data-mark="entry.status === choice ? choice : undefined"
              aria-hidden="true"
              >{{ manualStatusSymbol[choice] }}</span
            >
          </button>
        </div>
        <span
          v-else-if="future"
          class="headman-manual-roster__hint"
          >Пара ещё не началась</span
        >
        <span
          v-else
          class="headman-manual__circle"
          :aria-label="manualStatusLabel[entry.status]"
        >
          {{ manualStatusSymbol[entry.status] }}
        </span>
      </div>
      <slot
        name="details"
        :entry="entry"
      />
      <p
        v-if="pendingUserId === entry.userId"
        class="headman-manual__sr-only"
        role="status"
      >
        Обновляем отметку…
      </p>
      <p
        v-if="issues[entry.userId]"
        class="headman-manual-roster__issue"
        :role="issues[entry.userId]?.kind === 'accepted' ? 'status' : 'alert'"
      >
        {{ issues[entry.userId]?.message }}
      </p>
      <button
        v-if="
          staleRows.has(entry.userId) ||
          (entry.editable &&
            !future &&
            markCondition(entry) === null &&
            !['CANCELLED', 'UNSUPPORTED'].includes(entry.status))
        "
        class="headman-manual-roster__retry"
        type="button"
        :disabled="blocked"
        :aria-label="`Обновить отметку: ${entry.displayName}`"
        @click="emit('refresh', entry.userId, $event.currentTarget)"
      >
        Обновить отметку
      </button>
    </li>
  </ol>
</template>
