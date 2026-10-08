<script setup lang="ts">
import { teacherLessonTypeLabel } from './teacher-ui-format'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import type { TeacherDayLesson } from './teacher-client'
withDefaults(defineProps<{ lesson: TeacherDayLesson; current?: boolean; showDate?: boolean; interactive?: boolean; compact?: boolean }>(), { current: false, showDate: false, interactive: true, compact: false })
const emit = defineEmits<{ open: [lessonId: number] }>()
function dateLabel(date: string): string { const parsed = new Date(`${date}T12:00:00`); return Number.isNaN(parsed.getTime()) ? date : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' }).format(parsed) }
function time(value: string | null): string { return value?.slice(0,5) ?? '—' }
function status(lesson: TeacherDayLesson): string { return lesson.cancelled ? 'Отменена' : lesson.status === 'ACTIVE' ? 'Сейчас' : lesson.status === 'CLOSED' ? 'Завершена' : lesson.status === 'PLANNED' ? 'Запланирована' : 'Время уточняется' }
</script>
<template>
  <component
    :is="interactive && !lesson.cancelled ? 'button' : 'article'"
    :type="interactive && !lesson.cancelled ? 'button' : undefined"
    class="teacher-lesson-card"
    :data-current="current"
    :data-cancelled="lesson.cancelled"
    :data-teacher-action="interactive && !lesson.cancelled ? 'lesson-open' : undefined"
    :data-lesson-id="lesson.id"
    @click="interactive && !lesson.cancelled && emit('open', lesson.id)"
  >
    <span class="teacher-lesson-card__time">{{ showDate ? dateLabel(lesson.date) + ' · ' : '' }}{{ current ? 'Сейчас · ' : '' }}{{ time(lesson.startsAt) }}–{{ time(lesson.endsAt) }}</span>
    <strong
      v-if="!compact"
      class="teacher-lesson-card__title"
    >{{ lesson.subjectName }}</strong>
    <span
      v-if="!compact"
      class="teacher-lesson-card__group"
    >{{ lesson.groupName }}</span>
    <span class="teacher-lesson-card__meta"><MobileIcon name="room" />{{ lesson.room ?? 'Аудитория не указана' }}</span>
    <span class="teacher-lesson-card__meta"><MobileIcon name="lesson" />{{ teacherLessonTypeLabel(lesson.lessonType) }}</span>
    <span
      v-if="!current || lesson.moved || lesson.oneOff"
      class="teacher-lesson-card__state"
    >{{ status(lesson) }}<template v-if="lesson.moved"> · Перенесена</template><template v-if="lesson.oneOff"> · Разовая</template></span>
    <MobileIcon
      v-if="interactive && !lesson.cancelled"
      name="back"
      class="teacher-lesson-card__next"
    />
  </component>
</template>
