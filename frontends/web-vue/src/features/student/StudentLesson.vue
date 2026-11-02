<script setup lang="ts">
import type { LessonSchedule, TodayLesson } from '../../../../mobile-core/src/api/types';
import { lessonTypeLabel, studentMark } from './student-format';
defineProps<{ lesson: LessonSchedule; today?: TodayLesson; featured?: boolean }>();
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
      <p v-if="lesson.room.current || (lesson.room.changeState === 'CHANGED' && lesson.room.previous)">
        Аудитория: <template v-if="lesson.room.changeState === 'CHANGED'"><span v-if="lesson.room.current" class="student-room-current">{{ lesson.room.current }}</span><s v-if="lesson.room.previous" class="student-room-previous">{{ lesson.room.previous }}</s></template><template v-else>{{ lesson.room.current }}</template>
      </p>
      <p v-if="lesson.room.changeState === 'CHANGED'" class="student-room-change">
        Аудитория изменена
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
          }}<template v-if="lesson.room.current || (lesson.room.changeState === 'CHANGED' && lesson.room.previous)">
            · <template v-if="lesson.room.changeState === 'CHANGED'"><span v-if="lesson.room.current" class="student-room-current">{{ lesson.room.current }}</span><s v-if="lesson.room.previous" class="student-room-previous">{{ lesson.room.previous }}</s></template><template v-else>{{ lesson.room.current }}</template>
          </template>
        </p>
        <p
          v-if="lesson.room.changeState === 'CHANGED'"
          class="student-room-change"
        >
          Аудитория изменена
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
    <template v-if="!featured">
      <p v-if="today?.request?.status === 'PENDING'">
        Заявка на подтверждении у старосты
      </p>
      <slot name="actions" />
    </template>
    <slot />
  </article>
</template>
