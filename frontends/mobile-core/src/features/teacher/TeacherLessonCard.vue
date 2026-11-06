<script setup lang="ts">
import { computed } from 'vue';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import type { TeacherDayLesson } from './teacher-client';
import './teacher-lesson-r3.pcss';
const props = withDefaults(
  defineProps<{
    lesson: TeacherDayLesson;
    current?: boolean;
    showDate?: boolean;
    interactive?: boolean;
    compact?: boolean;
    showStatus?: boolean;
    attendance?: boolean;
    presentation?: 'default' | 'today';
  }>(),
  {
    current: false,
    showDate: false,
    interactive: true,
    compact: false,
    showStatus: false,
    attendance: false,
    presentation: 'default',
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
const originTag = computed(() => {
  if (props.lesson.cancelled) return { label: 'Отменена', tone: 'cancelled' };
  if (props.lesson.moved) return { label: 'Перенесена', tone: 'moved' };
  if (props.lesson.oneOff) return { label: 'Разовая', tone: 'one-off' };
  return null;
});
const timeTag = computed(() => ({
  label: status(props.lesson),
  tone: props.lesson.status === 'ACTIVE' ? 'active' : 'neutral',
}));
const todayTag = computed(() => originTag.value ?? timeTag.value);
const announceTimeStatus = computed(() => !props.lesson.cancelled && (
  props.presentation === 'today'
    ? props.lesson.status === 'ACTIVE' && originTag.value !== null
    : props.current && !props.showStatus
));
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
    :data-presentation="presentation"
    :data-teacher-action="interactive && !lesson.cancelled ? 'lesson-open' : undefined"
    :data-lesson-id="lesson.id"
    @click="interactive && !lesson.cancelled && emit('open', lesson.id)"
  >
    <span
      v-if="announceTimeStatus"
      class="teacher-screen__sr"
    >{{ timeTag.label }}</span>
    <span class="teacher-lesson-card__header">
      <span
        v-if="presentation === 'today'"
        class="teacher-lesson-card__tag"
        :data-tone="todayTag.tone"
      >{{ todayTag.label }}</span>
      <span
        class="teacher-lesson-card__group"
        :title="lesson.groupName"
      >{{ lesson.groupName }}</span>
    </span>
    <span class="teacher-lesson-card__topline">
      <span
        v-if="presentation !== 'today' && originTag"
        class="teacher-lesson-card__identity"
      >
        <span
          class="teacher-lesson-card__tag"
          :data-tone="originTag.tone === 'moved' ? 'neutral' : originTag.tone"
        >{{ originTag.label }}</span>
      </span>
      <span class="teacher-lesson-card__time">{{ showDate ? dateLabel(lesson.date) + ' · ' : '' }}{{ time(lesson.startsAt) }}–{{
        time(lesson.endsAt)
      }}</span>
    </span>
    <span
      v-if="presentation !== 'today' && showStatus && !lesson.cancelled"
      class="teacher-lesson-card__tags"
    >
      <span
        class="teacher-lesson-card__tag"
        :data-tone="timeTag.tone"
      >{{ timeTag.label }}</span>
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
