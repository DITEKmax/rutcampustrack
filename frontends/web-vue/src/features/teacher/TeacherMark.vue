<script setup lang="ts">
import { computed } from 'vue';
import { journalMark } from '../../../../mobile-core/src/features/teacher/teacher-journal-model';
import type {
  TeacherJournalCell,
  TeacherRosterEntry,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
const props = defineProps<{ cell?: TeacherJournalCell | TeacherRosterEntry | null | undefined }>();
const kind = computed(() =>
  journalMark(
    props.cell
      ? { ...props.cell, lessonId: 'lessonId' in props.cell ? props.cell.lessonId : 0 }
      : null,
  ),
);
const label = computed(
  () =>
    ({
      present: 'Присутствовал',
      excused: 'Уважительная причина',
      absent: 'Отсутствовал',
      none: 'Нет отметки',
    })[kind.value ?? 'none'],
);
</script>
<template>
  <span
    class="teacher-mark"
    :class="`teacher-mark-${kind ?? 'none'}`"
    :title="label"
    :aria-label="`${label}${cell?.autoAbsent ? ', автоматически' : ''}${cell?.pendingTicket ? ', заявка на рассмотрении' : ''}`"
  >
    {{ kind === 'present' ? '+' : kind === 'excused' ? 'у' : kind === 'absent' ? 'н' : '—' }}
    <span
      v-if="cell?.pendingTicket"
      class="teacher-pending"
      aria-hidden="true"
    >•</span>
  </span>
</template>
