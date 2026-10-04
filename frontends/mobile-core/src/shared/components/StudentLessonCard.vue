<script setup lang="ts">
import { computed } from 'vue'
import type { TodayLesson } from '../../api/types'
import MobileIcon from './MobileIcon.vue'
import './student-lesson-card.pcss'

const props = withDefaults(defineProps<{
  lesson: TodayLesson
  current?: boolean
  pending?: boolean
  requestMessage?: string | null
  expanded?: boolean
  interactive?: boolean
  headingLevel?: 2 | 3
}>(), { current: false, pending: false, requestMessage: null, expanded: false, interactive: false, headingLevel: 3 })
const emit = defineEmits<{ activate: [] }>()
const status = computed(() => props.lesson.attendance?.status ?? null)
const statusLabel = computed(() => status.value === 'PRESENT' ? 'Отметка подтверждена' : status.value === 'EXCUSED' ? 'Уважительная причина' : 'Пропуск')
const symbol = computed(() => status.value === 'PRESENT' ? '+' : status.value === 'EXCUSED' ? 'у' : 'н')
const kind = computed(() => ({ LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' })[props.lesson.schedule.subject.type])
const cancelled = computed(() => props.lesson.schedule.status === 'CANCELLED')
</script>

<template>
  <div
    class="student-lesson"
    :data-current="current && !cancelled"
    :data-cancelled="cancelled"
  >
    <p class="student-lesson__time">
      <span>{{ lesson.schedule.startsAt.slice(0, 5) }}</span>
      <span>{{ lesson.schedule.endsAt.slice(0, 5) }}</span>
    </p>
    <article
      class="student-lesson__card"
      :data-has-status="status !== null"
      :data-expanded="expanded"
      :data-request="Boolean(requestMessage)"
    >
      <component
        :is="`h${headingLevel}`"
        class="student-lesson__title"
      >
        {{ lesson.schedule.subject.name }}
      </component>
      <p class="student-lesson__meta">
        <MobileIcon name="room" />
        <span :class="{ 'student-lesson__room--changed': lesson.schedule.room.changeState === 'CHANGED' }">{{ lesson.schedule.room.current ?? 'Аудитория уточняется' }}</span>
        <s
          v-if="lesson.schedule.room.previous"
          class="student-lesson__previous-room"
          :aria-label="`Прежняя аудитория: ${lesson.schedule.room.previous}`"
        >{{ lesson.schedule.room.previous }}</s>
      </p>
      <p class="student-lesson__meta">
        <MobileIcon name="lesson" /><span>{{ kind }}</span>
      </p>
      <button
        v-if="status && interactive"
        class="student-lesson__status-action"
        type="button"
        :aria-label="`${statusLabel}. Действия по пропуску`"
        :aria-expanded="expanded"
        @click="emit('activate')"
      >
        <span
          class="student-lesson__status"
          :data-status="status"
        >{{ symbol }}<MobileIcon
          v-if="pending"
          name="clock"
          class="student-lesson__pending"
        /></span>
      </button>
      <span
        v-else-if="status"
        class="student-lesson__status"
        :data-status="status"
        :aria-label="pending ? `${statusLabel}. Запрос на рассмотрении` : statusLabel"
      >{{ symbol }}<MobileIcon
        v-if="pending"
        name="clock"
        class="student-lesson__pending"
      /></span>
      <p
        v-if="cancelled"
        class="student-lesson__notice"
      >
        Пара отменена
      </p>
      <p
        v-if="requestMessage"
        class="student-lesson__request"
        role="status"
      >
        {{ requestMessage }}
      </p>
      <div
        v-if="expanded"
        class="student-lesson__actions"
      >
        <slot />
      </div>
    </article>
  </div>
</template>
