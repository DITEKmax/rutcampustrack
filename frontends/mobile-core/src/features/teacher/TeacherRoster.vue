<script setup lang="ts">
import { ref, watch } from 'vue'
import { teacherExcuseTypeLabel } from './teacher-ui-format'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import type { TeacherJournalCell, TeacherRosterEntry } from './teacher-client'
type Attendance = Pick<TeacherRosterEntry, 'status' | 'symbol' | 'source' | 'recordPresent' | 'pendingTicket' | 'autoAbsent' | 'ticketId' | 'excuseType' | 'excuseReason' | 'excuseComment'> | TeacherJournalCell
export interface TeacherRosterRow { studentId: number; displayName: string; attendance: Attendance | null }
const props = defineProps<{ rows: readonly TeacherRosterRow[] }>()
const emit = defineEmits<{ 'open-excuse': [requestId: string] }>()
const expanded = ref<number | null>(null)
watch(() => props.rows, () => { expanded.value = null })
function manual(value: Attendance | null): boolean { return Boolean(value?.source === 'NO_TICKET' && (value.excuseReason || value.excuseType || 'excuseComment' in value && value.excuseComment)) }
function description(value: Attendance | null): string { return !value ? '' : teacherExcuseTypeLabel(value.excuseReason || value.excuseType) }
function comment(value: Attendance | null): string | null { const text = value && 'excuseComment' in value ? value.excuseComment : null; return text?.trim() && text.trim() !== description(value).trim() ? text : null }
function canOpen(value: Attendance | null): boolean { return Boolean(value?.ticketId && (value.pendingTicket || value.status === 'excused' || value.symbol === 'у')) }
function label(value: Attendance | null): string {
  if (!value) return 'Нет данных о посещаемости'
  const status = !value.recordPresent ? 'Нет записи' : value.autoAbsent ? 'Автоматически отмечено отсутствие' : value.source === 'NO_TICKET' ? 'Уважительная причина без заявления' : value.status === 'present' ? 'Присутствовал' : value.status === 'excused' ? 'Уважительная причина' : value.status === 'absent' ? 'Отсутствовал' : 'Нет данных о посещаемости'
  return value.pendingTicket ? status + '; заявление ожидает решения' : status
}
function symbol(value: Attendance | null): string { return !value?.recordPresent ? '—' : value.status === 'present' ? '+' : value.symbol ?? '—' }
function tone(value: Attendance | null): string { return value?.pendingTicket ? 'pending' : !value?.recordPresent ? 'none' : value.autoAbsent ? 'auto' : value.status === 'present' ? 'present' : value.status === 'excused' || value.symbol === 'у' ? 'excused' : value.status === 'absent' || value.symbol === 'н' ? 'absent' : 'auto' }
function open(row: TeacherRosterRow): void { if (canOpen(row.attendance) && row.attendance?.ticketId) emit('open-excuse',row.attendance.ticketId); else if (manual(row.attendance)) expanded.value = expanded.value === row.studentId ? null : row.studentId }
</script>
<template>
  <ul
    v-if="rows.length"
    class="teacher-roster"
    aria-label="Студенты и посещаемость"
  >
    <li
      v-for="row in rows"
      :key="row.studentId"
    >
      <component
        :is="canOpen(row.attendance) || manual(row.attendance) ? 'button' : 'div'"
        :type="canOpen(row.attendance) || manual(row.attendance) ? 'button' : undefined"
        class="teacher-roster__row"
        :role="canOpen(row.attendance) || manual(row.attendance) ? undefined : 'group'"
        :aria-label="row.displayName + ': ' + label(row.attendance) + (canOpen(row.attendance) || manual(row.attendance) ? '. Открыть причину отсутствия' : '')"
        :aria-expanded="manual(row.attendance) ? expanded === row.studentId : undefined"
        :data-teacher-action="canOpen(row.attendance) ? 'excuse-open' : manual(row.attendance) ? 'manual-reason-toggle' : undefined"
        @click="open(row)"
      >
        <span
          class="teacher-roster__name"
          aria-hidden="true"
        >{{ row.displayName }}</span>
        <span
          v-if="row.attendance?.pendingTicket"
          class="teacher-roster__pending"
          aria-hidden="true"
        >Ожидает решения</span>
        <span
          v-else
          class="teacher-roster__status"
          :data-tone="tone(row.attendance)"
          aria-hidden="true"
        >{{ symbol(row.attendance) }}</span>
        <MobileIcon
          v-if="canOpen(row.attendance) || manual(row.attendance)"
          name="back"
          class="teacher-roster__next"
        />
      </component>
      <p
        v-if="manual(row.attendance) && expanded === row.studentId"
        class="teacher-roster__reason"
      >
        <span>{{ description(row.attendance) }}</span><span
          v-if="comment(row.attendance)"
          class="teacher-roster__comment"
        >{{ comment(row.attendance) }}</span>
      </p>
    </li>
  </ul>
  <p
    v-else
    class="teacher-screen__state"
    role="status"
  >
    В списке группы нет студентов.
  </p>
</template>
