<script setup lang="ts">
import type { HeadmanJournalLesson, HeadmanJournalRosterEntry } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import { markLabels, markSymbols } from './context';
type MarkStatus = 'PRESENT' | 'EXCUSED' | 'ABSENT';
defineProps<{ entry: HeadmanJournalRosterEntry; lesson: HeadmanJournalLesson; allowed: readonly MarkStatus[]; locked: boolean }>();
defineEmits<{ choose: [status: MarkStatus] }>();
const statuses = ['PRESENT', 'EXCUSED', 'ABSENT'] as const;
</script>
<template>
  <div class="headman-home-status">
    <div v-if="allowed.length" class="headman-home-mark-actions" :class="{ 'has-mark': entry.status !== 'EMPTY' }" role="group" :aria-label="`Отметка: ${entry.displayName}`">
      <button v-for="status in statuses" :key="status" type="button" class="headman-home-mark-control" :data-mark-user="entry.userId" :data-mark-status="status" :aria-pressed="entry.status === status" :aria-label="`${entry.status === status ? 'Снять отметку' : 'Поставить отметку'} «${markLabels[status]}»: ${entry.displayName}`" :disabled="locked || !allowed.includes(status)" @click="$emit('choose', status)">
        <span class="headman-home-mark" :class="{ 'is-neutral': entry.status !== status }" :data-status="entry.status === status ? status : undefined" aria-hidden="true">{{ markSymbols[status] }}</span>
      </button>
    </div>
    <span v-else class="headman-home-mark" :data-status="entry.status" :aria-label="markLabels[entry.status]">{{ markSymbols[entry.status] }}</span>
    <small v-if="entry.status === 'EXCUSED' && entry.excuseReason">{{ entry.excuseReason }}</small>
    <RouterLink v-if="entry.approvedTicket" class="web-caption" :to="{ name: 'headman-requests', query: { request: entry.approvedTicket.id } }">Заявка № {{ entry.approvedTicket.id }}</RouterLink>
  </div>
</template>
