<script setup lang="ts">
import { lessonTypeLabel } from '../requests/state';
import { formatRequestLessonTime } from './request-lesson-time';
import { requestDate, type RequestLessonRow } from './request-presentation';

defineProps<{ rows: readonly RequestLessonRow[] }>();
defineSlots<{ control?: (props: { row: RequestLessonRow }) => unknown }>();

function markLabel(mark: string | null): { text: string; symbol: string } {
  if (mark === 'PRESENT') return { text: 'Присутствовал', symbol: '+' };
  if (mark === 'EXCUSED') return { text: 'Уважительная причина', symbol: 'у' };
  if (mark === 'ABSENT') return { text: 'Отсутствовал', symbol: 'н' };
  if (mark === 'CANCELLED') return { text: 'Пара отменена', symbol: '×' };
  return { text: mark === null ? 'Нет отметки' : 'Отметка недоступна', symbol: '—' };
}
</script>

<template>
  <ul class="headman-requests__lessons">
    <li
      v-for="(row, index) in rows"
      :key="row.lesson.occurrenceId ?? row.lesson.lessonId ?? index"
      :data-excluded="row.excluded"
    >
      <slot
        name="control"
        :row="row"
      />
      <span class="headman-requests__lesson-copy">
        <strong>{{ row.lesson.subjectName ?? 'Предмет не указан' }}</strong>
        <span>{{ requestDate(row.lesson.date) }} · {{ formatRequestLessonTime(row.lesson) }} ·
          {{ lessonTypeLabel(row.lesson.subjectType) }}</span>
        <span
          v-if="row.label"
          class="headman-requests__lesson-label"
        >{{ row.label }}</span>
      </span>
      <span
        v-if="row.mark !== undefined"
        class="headman-requests__attendance"
        :data-status="row.mark"
      >
        <span aria-hidden="true">{{ markLabel(row.mark).symbol }}</span>
        <span class="headman-requests__sr">{{ markLabel(row.mark).text }}</span>
      </span>
    </li>
  </ul>
</template>
