<script setup lang="ts">
import { computed } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import { formatLessonDate, formatLessonTime, lessonTypeLabel } from './state'
import type { RequestLesson } from './types'
import './request-lesson-summary.pcss'

const props = withDefaults(defineProps<{
  lesson: RequestLesson
  attendanceStatus?: 'PRESENT' | 'ABSENT' | 'EXCUSED' | null
}>(), { attendanceStatus: null })
const statusLabel = computed(() => props.attendanceStatus === 'PRESENT' ? 'Отметка подтверждена' : props.attendanceStatus === 'ABSENT' ? 'Пропуск' : props.attendanceStatus === 'EXCUSED' ? 'Уважительная причина' : 'Отметка не указана')
const symbol = computed(() => props.attendanceStatus === 'PRESENT' ? '+' : props.attendanceStatus === 'EXCUSED' ? 'у' : 'н')
</script>

<template>
  <article
    class="request-lesson-summary"
    aria-label="Выбранная пара"
  >
    <div class="request-lesson-summary__head">
      <h2>{{ lesson.subjectName || 'Предмет не указан' }}</h2>
      <span
        v-if="attendanceStatus"
        class="request-lesson-summary__status"
        :data-status="attendanceStatus"
        :aria-label="statusLabel"
      >{{ symbol }}</span>
    </div>
    <p><MobileIcon name="calendar" /><span>{{ formatLessonDate(lesson.date) }}</span></p>
    <p><MobileIcon name="clock" /><span>{{ formatLessonTime(lesson) }}</span></p>
    <p><MobileIcon name="lesson" /><span>{{ lessonTypeLabel(lesson.subjectType) }}</span></p>
    <p
      v-if="!attendanceStatus"
      class="request-lesson-summary__unknown"
    >
      {{ statusLabel }}
    </p>
    <p
      v-if="lesson.status === 'CANCELLED'"
      class="request-lesson-summary__cancelled"
    >
      Пара отменена
    </p>
  </article>
</template>
