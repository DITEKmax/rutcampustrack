<script setup lang="ts">
import { computed } from 'vue';
import { teacherAttendanceKind, teacherAttendanceLabel } from './teacher-mark';
import type {
  TeacherJournalCell,
  TeacherRosterEntry,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
const props = defineProps<{ cell?: TeacherJournalCell | TeacherRosterEntry | null | undefined }>();
const kind = computed(() => teacherAttendanceKind(props.cell));
const label = computed(() => teacherAttendanceLabel(props.cell));
</script>
<template>
  <span
    class="teacher-mark"
    :class="`teacher-mark-${kind ?? 'none'}`"
    :title="label"
    :aria-label="label"
  >
    {{ kind === 'present' ? '+' : kind === 'excused' ? 'у' : kind === 'absent' ? 'н' : '—' }}
    <span
      v-if="cell?.pendingTicket"
      class="teacher-pending"
      aria-hidden="true"
    >•</span>
  </span>
</template>
