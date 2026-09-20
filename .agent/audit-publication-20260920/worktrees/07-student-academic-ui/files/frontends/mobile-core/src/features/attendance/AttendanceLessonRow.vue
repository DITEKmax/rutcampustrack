<script setup lang="ts">
import { computed } from 'vue'
import roomIcon from '../../assets/room-light.svg'
import lessonTypeIcon from '../../assets/lesson-type-light.svg'
import {
  hasEnabledRequestOption,
  lessonStatusLabel,
  lessonTypeLabel,
  type AttendanceLesson,
  type AttendanceRequestOption,
} from './attendance-view-model'

const props = withDefaults(defineProps<{
  lesson: AttendanceLesson
  actionsOpen?: boolean
  terminal?: boolean
}>(), {
  actionsOpen: false,
  terminal: false,
})

const emit = defineEmits<{
  'toggle-actions': [lessonId: string]
  request: [lesson: AttendanceLesson, option: AttendanceRequestOption]
}>()

const requestOptions = computed(() => props.lesson.requestOptions)
const hasRequestOptions = computed(() => requestOptions.value.length > 0)
const canOpenActions = computed(() => hasEnabledRequestOption(props.lesson) && !props.terminal)

function statusTone(status: AttendanceLesson['status']): string {
  if (status === 'PRESENT') return 'present'
  if (status === 'ABSENT') return 'absent'
  if (status === 'EXCUSED') return 'excused'
  if (status === 'CANCELLED') return 'cancelled'
  return 'neutral'
}

function statusSymbol(status: AttendanceLesson['status']): string {
  if (status === 'PRESENT') return '+'
  if (status === 'ABSENT') return 'н'
  if (status === 'EXCUSED') return 'у'
  if (status === 'ACTIVE') return 'сейчас'
  if (status === 'FUTURE') return 'будет'
  if (status === 'CANCELLED') return '×'
  return '—'
}

function optionDisabled(option: AttendanceRequestOption): boolean {
  return props.terminal || !option.enabled
}

function optionLabel(option: AttendanceRequestOption): string {
  if (optionDisabled(option) && option.reason) return `${option.label} · ${option.reason}`
  return option.label
}
</script>

<template>
  <li
    class="attendance-lesson-row"
    :data-status="statusTone(lesson.status)"
    :data-lesson-id="lesson.id"
  >
    <p class="attendance-lesson-row__time">
      <span>{{ lesson.schedule.startsAt.slice(0, 5) }}</span>
      <span>{{ lesson.schedule.endsAt.slice(0, 5) }}</span>
    </p>
    <article class="attendance-lesson-row__card">
      <header class="attendance-lesson-row__head">
        <h3>{{ lesson.subject.name }}</h3>
        <button
          v-if="hasRequestOptions"
          class="attendance-status-badge"
          :class="`attendance-status-badge--${statusTone(lesson.status)}`"
          :data-request-trigger="lesson.id"
          type="button"
          :disabled="!canOpenActions"
          :aria-label="canOpenActions ? `Действия для отметки ${lessonStatusLabel(lesson.status)}` : 'Действия недоступны'"
          :aria-expanded="canOpenActions ? actionsOpen : undefined"
          @click="emit('toggle-actions', lesson.id)"
        >
          {{ statusSymbol(lesson.status) }}
        </button>
        <span
          v-else
          :class="`attendance-status-badge attendance-status-badge--${statusTone(lesson.status)}`"
          role="img"
          :aria-label="`Статус: ${lessonStatusLabel(lesson.status)}`"
        >{{ statusSymbol(lesson.status) }}</span>
      </header>
      <div class="attendance-lesson-row__metadata">
        <span>
          <img
            :src="roomIcon"
            alt=""
            aria-hidden="true"
          >
          {{ lesson.schedule.room ?? 'Аудитория уточняется' }}
        </span>
        <span>
          <img
            :src="lessonTypeIcon"
            alt=""
            aria-hidden="true"
          >
          {{ lessonTypeLabel(lesson.type) }}
        </span>
      </div>
      <div
        v-if="actionsOpen"
        class="attendance-lesson-row__actions"
        role="group"
        :aria-label="`Действия для ${lesson.subject.name}`"
      >
        <button
          v-for="option in requestOptions"
          :key="option.id"
          class="attendance-lesson-row__action"
          type="button"
          :disabled="optionDisabled(option)"
          @click="emit('request', lesson, option)"
        >
          {{ optionLabel(option) }}
        </button>
      </div>
    </article>
  </li>
</template>
