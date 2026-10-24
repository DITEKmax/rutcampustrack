<script setup lang="ts">
import type { StudentAttendanceLesson } from '../../../../mobile-core/src/api/types';
import { formatLessonDate, lessonTypeLabel, studentMark } from './student-format';
defineProps<{ lesson: StudentAttendanceLesson; readOnly: boolean }>();
defineEmits<{ close: [] }>();
</script>
<template>
  <section
    class="student-mark-detail web-notice web-stack"
    aria-label="Подробности отметки"
    tabindex="-1"
    @keydown.esc.stop="$emit('close')"
  >
    <div class="web-page-header">
      <h3>{{ lesson.subject.name }}</h3><button
        class="web-button"
        @click="$emit('close')"
      >
        Закрыть подробности
      </button>
    </div>
    <p>
      {{ formatLessonDate(lesson.date) }} · {{ lessonTypeLabel(lesson.type) }} ·
      {{ lesson.schedule.startsAt.slice(0, 5) }}–{{ lesson.schedule.endsAt.slice(0, 5)
      }}<template v-if="lesson.schedule.room">
        · {{ lesson.schedule.room }}
      </template>
    </p>
    <p>{{ studentMark(lesson.status).symbol }} · {{ studentMark(lesson.status).label }}</p>
    <template v-if="lesson.explanation">
      <p v-if="lesson.explanation.reasonText">
        Причина: {{ lesson.explanation.reasonText }}
      </p><p v-if="lesson.explanation.comment">
        {{ lesson.explanation.comment }}
      </p><p v-if="lesson.explanation.cancellationReason">
        Причина отмены: {{ lesson.explanation.cancellationReason }}
      </p>
    </template>
    <div
      v-for="option in lesson.requestOptions"
      :key="option.id"
      class="web-stack"
    >
      <RouterLink
        v-if="option.enabled && !readOnly"
        class="web-button"
        :to="{ name: 'student-requests', query: { lesson: lesson.id, kind: option.kind } }"
      >
        {{ option.label }}
      </RouterLink>
      <p
        v-else
        class="web-caption"
      >
        {{ option.label }}:
        {{ readOnly ? 'доступен только просмотр' : option.reason || 'недоступно для этой пары' }}
      </p>
    </div>
  </section>
</template>
