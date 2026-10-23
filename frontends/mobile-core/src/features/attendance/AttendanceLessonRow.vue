<script setup lang="ts">
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import { computed, ref, watch } from 'vue';
import roomIcon from '../../assets/room-light.svg';
import lessonTypeIcon from '../../assets/lesson-type-light.svg';
import {
  attendanceExplanationText,
  hasEnabledRequestOption,
  lessonStatusLabel,
  lessonTypeLabel,
  type AttendanceLesson,
  type AttendanceRequestOption,
} from './attendance-view-model';

const props = withDefaults(
  defineProps<{
    lesson: AttendanceLesson;
    actionsOpen?: boolean;
    notificationTarget?: boolean;
    terminal?: boolean;
  }>(),
  {
    actionsOpen: false,
    notificationTarget: false,
    terminal: false,
  },
);

const emit = defineEmits<{
  'toggle-actions': [lessonId: string];
  request: [lesson: AttendanceLesson, option: AttendanceRequestOption];
}>();

const requestOptions = computed(() => props.lesson.requestOptions);
const hasRequestOptions = computed(() => requestOptions.value.length > 0);
const canOpenActions = computed(() => hasEnabledRequestOption(props.lesson) && !props.terminal);
const explanationOpen = ref<'hover' | 'focus' | 'tap' | null>(null);
const explanationText = computed(() =>
  attendanceExplanationText(props.lesson.status, props.lesson.explanation),
);
watch(
  () => props.lesson,
  () => {
    explanationOpen.value = null;
  },
);
function openStatus(): void {
  explanationOpen.value = 'tap';
  if (canOpenActions.value) emit('toggle-actions', props.lesson.id);
}

function statusTone(status: AttendanceLesson['status']): string {
  if (status === 'PRESENT') return 'present';
  if (status === 'ABSENT') return 'absent';
  if (status === 'EXCUSED') return 'excused';
  if (status === 'CANCELLED') return 'cancelled';
  return 'neutral';
}

function statusSymbol(status: AttendanceLesson['status']): string {
  if (status === 'PRESENT') return '+';
  if (status === 'ABSENT') return 'н';
  if (status === 'EXCUSED') return 'у';
  if (status === 'ACTIVE') return 'сейчас';
  if (status === 'FUTURE') return 'будет';
  if (status === 'CANCELLED') return '×';
  return '—';
}

function optionDisabled(option: AttendanceRequestOption): boolean {
  return props.terminal || !option.enabled;
}

function optionLabel(option: AttendanceRequestOption): string {
  if (optionDisabled(option) && option.reason) return `${option.label} · ${option.reason}`;
  return option.label;
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
        <h3
          :data-notification-target="notificationTarget || undefined"
          :tabindex="notificationTarget ? -1 : undefined"
        >
          {{ lesson.subject.name }}
        </h3>
        <button
          v-if="explanationText"
          class="attendance-status-badge"
          :class="`attendance-status-badge--${statusTone(lesson.status)}`"
          type="button"
          :aria-label="`Статус: ${lessonStatusLabel(lesson.status)} · ${explanationText}`"
          :aria-expanded="explanationOpen !== null"
          :aria-describedby="explanationOpen ? `attendance-explanation-${lesson.id}` : undefined"
          @mouseenter="explanationOpen = explanationOpen ?? 'hover'"
          @mouseleave="explanationOpen === 'hover' && (explanationOpen = null)"
          @focus="explanationOpen = 'focus'"
          @blur="explanationOpen = null"
          @click="openStatus"
          @keydown.esc="explanationOpen = null"
        >
          {{ statusSymbol(lesson.status) }}
        </button>
        <button
          v-else-if="hasRequestOptions"
          class="attendance-status-badge"
          :class="`attendance-status-badge--${statusTone(lesson.status)}`"
          :data-request-trigger="lesson.id"
          type="button"
          :disabled="!canOpenActions"
          :aria-label="
            canOpenActions
              ? `Действия для отметки ${lessonStatusLabel(lesson.status)}`
              : 'Действия недоступны'
          "
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
      <p
        v-if="explanationOpen && explanationText"
        :id="`attendance-explanation-${lesson.id}`"
        class="attendance-status-explanation"
        role="tooltip"
      >
        {{ explanationText }}
      </p>
      <div class="attendance-lesson-row__metadata">
        <span>
          <SvgIcon
            :width="10"
            :height="10"
            :src="roomIcon"
            aria-hidden="true"
          />
          {{ lesson.schedule.room ?? 'Аудитория уточняется' }}
        </span>
        <span>
          <SvgIcon
            :width="10"
            :height="10"
            :src="lessonTypeIcon"
            aria-hidden="true"
          />
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
