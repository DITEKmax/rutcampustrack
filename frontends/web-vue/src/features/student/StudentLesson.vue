<script setup lang="ts">
import type { LessonSchedule, TodayLesson } from '../../../../mobile-core/src/api/types';
import { lessonTypeLabel, studentMark } from './student-format';
const props = defineProps<{ lesson: LessonSchedule; today?: TodayLesson; featured?: boolean }>();
const statuses = {
  PLANNED: 'Пара запланирована',
  ACTIVE: 'Пара идёт',
  CLOSED: 'Пара завершена',
  CANCELLED: 'Пара отменена',
};
</script>
<template>
  <article
    class="student-lesson web-stack"
    :class="{
      'is-current': lesson.status === 'ACTIVE',
      'is-featured': featured,
      'is-cancelled': lesson.status === 'CANCELLED',
    }"
  >
    <div
      v-if="featured"
      class="web-stack student-featured-content"
    >
      <h3>{{ lesson.subject.name }}</h3>
      <p>Тип: {{ lessonTypeLabel(lesson.subject.type) }}</p>
      <p>Время: {{ lesson.startsAt.slice(0, 5) }}–{{ lesson.endsAt.slice(0, 5) }}</p>
      <p v-if="lesson.room.current">
        Аудитория: {{ lesson.room.current }}
      </p>
      <p v-if="lesson.room.changeState === 'CHANGED'">
        Аудитория изменена<template v-if="lesson.room.previous">
          : раньше {{ lesson.room.previous }}
        </template>
      </p>
    </div>
    <div
      v-else
      class="student-lesson-main"
    >
      <p class="student-lesson-time">
        {{ lesson.startsAt.slice(0, 5) }}–{{ lesson.endsAt.slice(0, 5) }}
      </p>
      <div class="web-stack student-lesson-content">
        <h3>{{ lesson.subject.name }}</h3>
        <p>
          {{ lessonTypeLabel(lesson.subject.type)
          }}<template v-if="lesson.room.current">
            · {{ lesson.room.current }}
          </template>
        </p>
        <p
          v-if="lesson.room.changeState === 'CHANGED'"
          class="student-room-change"
        >
          Аудитория изменена<template v-if="lesson.room.previous">
            : раньше {{ lesson.room.previous }}
          </template>
        </p>
        <p v-if="lesson.status === 'CANCELLED'">
          Пара отменена
        </p>
      </div>
      <span
        v-if="today?.attendance"
        class="student-mark"
        :data-tone="studentMark(today.attendance.status).tone"
        :aria-label="studentMark(today.attendance.status).label"
      >{{ studentMark(today.attendance.status).symbol }}</span>
    </div>
    <details
      v-if="!featured"
      class="student-lesson-details"
    >
      <summary>Детали пары</summary>
      <p>{{ statuses[props.lesson.status] }}</p>
      <p v-if="today?.attendance">
        {{ studentMark(today.attendance.status).label }}
      </p>
      <p v-if="today?.request?.status === 'PENDING'">
        Заявка на подтверждении у старосты
      </p>
      <slot name="actions" />
    </details>
    <slot />
  </article>
</template>
