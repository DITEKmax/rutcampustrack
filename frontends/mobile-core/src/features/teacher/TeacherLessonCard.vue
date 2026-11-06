<script setup lang="ts">
import { teacherLessonTypeLabel } from './teacher-ui-format';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import type { TeacherDayLesson } from './teacher-client';
import './teacher-lesson-r3.pcss';
withDefaults(
  defineProps<{
    lesson: TeacherDayLesson;
    current?: boolean;
    showDate?: boolean;
    interactive?: boolean;
    compact?: boolean;
    showStatus?: boolean;
    attendance?: boolean;
  }>(),
  {
    current: false,
    showDate: false,
    interactive: true,
    compact: false,
    showStatus: false,
    attendance: false,
  },
);
const emit = defineEmits<{ open: [lessonId: number] }>();
function dateLabel(date: string): string {
  const parsed = new Date(`${date}T12:00:00`);
  return Number.isNaN(parsed.getTime())
    ? date
    : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' }).format(parsed);
}
function time(value: string | null): string {
  return value?.slice(0, 5) ?? '—';
}
function status(lesson: TeacherDayLesson): string {
  return lesson.status === 'ACTIVE'
    ? 'Сейчас идёт'
    : lesson.status === 'CLOSED'
      ? 'Закончилась'
      : lesson.status === 'PLANNED'
        ? 'Запланирована'
        : 'Время уточняется';
}
</script>
<template>
  <component
    :is="interactive && !lesson.cancelled ? 'button' : 'article'"
    :type="interactive && !lesson.cancelled ? 'button' : undefined"
    class="teacher-lesson-card"
    :data-current="current"
    :data-cancelled="lesson.cancelled"
    :data-one-off="lesson.oneOff && !lesson.moved && !lesson.cancelled"
    :data-attendance="attendance"
    :data-compact="compact"
    :data-teacher-action="interactive && !lesson.cancelled ? 'lesson-open' : undefined"
    :data-lesson-id="lesson.id"
    @click="interactive && !lesson.cancelled && emit('open', lesson.id)"
  >
    <span
      v-if="current && !showStatus && !lesson.cancelled"
      class="teacher-screen__sr"
    >{{ status(lesson) }}</span>
    <span
      class="teacher-lesson-card__group"
      :title="lesson.groupName"
    >{{ lesson.groupName }}</span>
    <span class="teacher-lesson-card__topline">
      <span class="teacher-lesson-card__identity">
        <span
          v-if="lesson.cancelled"
          class="teacher-lesson-card__tag"
          data-tone="cancelled"
        >Отменена</span>
        <span
          v-else-if="lesson.moved"
          class="teacher-lesson-card__tag"
          data-tone="neutral"
        >Перенесена</span>
        <span
          v-else-if="lesson.oneOff"
          class="teacher-lesson-card__tag"
          data-tone="one-off"
        >Разовая</span>
      </span>
      <span class="teacher-lesson-card__time">{{ showDate ? dateLabel(lesson.date) + ' · ' : '' }}{{ time(lesson.startsAt) }}–{{
        time(lesson.endsAt)
      }}</span>
    </span>
    <span
      v-if="showStatus && !lesson.cancelled"
      class="teacher-lesson-card__tags"
    >
      <span
        class="teacher-lesson-card__tag"
        :data-tone="lesson.status === 'ACTIVE' ? 'active' : 'neutral'"
      >{{ status(lesson) }}</span>
    </span>
    <span
      v-if="!compact"
      class="teacher-lesson-card__title-row"
    >
      <strong class="teacher-lesson-card__title">{{ lesson.subjectName }}</strong>
      <MobileIcon
        v-if="interactive && !lesson.cancelled"
        name="back"
        class="teacher-lesson-card__next"
      />
    </span>
    <span class="teacher-lesson-card__meta">
      <span class="teacher-lesson-card__meta-item"><MobileIcon name="lesson" /><span>{{
        teacherLessonTypeLabel(lesson.lessonType)
      }}</span></span>
      <span class="teacher-lesson-card__meta-item"><MobileIcon name="room" /><span>{{
        lesson.room ? 'Ауд. ' + lesson.room : 'Аудитория не указана'
      }}</span></span>
    </span>
    <span
      v-if="lesson.cancelled && lesson.cancellationReason"
      class="teacher-lesson-card__cancellation"
    >{{ lesson.cancellationReason }}</span>
    <MobileIcon
      v-if="compact && interactive && !lesson.cancelled"
      name="back"
      class="teacher-lesson-card__next"
    />
  </component>
</template>
