<script setup lang="ts">
import MobileIcon from '../../shared/components/MobileIcon.vue';
import AttendanceDocumentPreview from './AttendanceDocumentPreview.vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import roomIcon from '../../assets/room-light.svg';
import lessonTypeIcon from '../../assets/lesson-type-light.svg';
import {
  attendanceExplanationText,
  attendanceDocuments,
  hasEnabledRequestOption,
  lessonStatusLabel,
  lessonTypeLabel,
  type AttendanceDocumentResolver,
  type AttendanceLesson,
  type AttendanceRequestOption,
} from './attendance-view-model';

const props = withDefaults(
  defineProps<{
    lesson: AttendanceLesson;
    documentForLesson?: AttendanceDocumentResolver | undefined;
    actionsOpen?: boolean;
    notificationTarget?: boolean;
    terminal?: boolean;
  }>(),
  {
    documentForLesson: undefined,
    actionsOpen: false,
    notificationTarget: false,
    terminal: false,
  },
);

const emit = defineEmits<{
  'toggle-actions': [lessonId: string];
  'explanation-visible': [visible: boolean];
  request: [lesson: AttendanceLesson, option: AttendanceRequestOption];
}>();

const requestOptions = computed(() => props.lesson.requestOptions);
const hasRequestOptions = computed(() => requestOptions.value.length > 0);
const canOpenActions = computed(() => hasEnabledRequestOption(props.lesson) && !props.terminal);
const documentPreview = ref<InstanceType<typeof AttendanceDocumentPreview>[]>([]);
function closeOverlay(): boolean {
  if (documentPreview.value.some(preview => preview.closeOverlay())) return true;
  if (explanationOpen.value === null) return false;
  explanationOpen.value = null;
  return true;
}
defineExpose({ closeOverlay });
const explanationOpen = ref<'hover' | 'focus' | 'tap' | null>(null);
const explanationText = computed(() =>
  attendanceExplanationText(props.lesson.status, props.lesson.explanation),
);
const explanationDocument = computed(() =>
  props.lesson.status === 'EXCUSED' ? attendanceDocuments(props.documentForLesson?.(props.lesson.id, props.lesson.explanation)) : [],
);
function closeExplanationOnBlur(event: FocusEvent): void {
  if (
    (props.lesson.status === 'EXCUSED' && explanationOpen.value === 'tap') ||
    (event.relatedTarget as HTMLElement | null)?.closest?.('[data-attendance-document]')
  )
    return;
  explanationOpen.value = null;
}
function closeHoverExplanation(event: MouseEvent): void {
  if ((event.relatedTarget as HTMLElement | null)?.closest?.('[data-attendance-explanation]'))
    return;
  if (explanationOpen.value === 'hover') explanationOpen.value = null;
}
onBeforeUnmount(() => emit('explanation-visible', false));
watch(explanationOpen, (open) => emit('explanation-visible', open !== null));
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
  if (status === 'ACTIVE') return 'active';
  if (status === 'FUTURE') return 'future';
  return 'neutral';
}

function statusSymbol(status: AttendanceLesson['status']): string {
  if (status === 'PRESENT') return '+';
  if (status === 'ABSENT') return 'н';
  if (status === 'EXCUSED') return 'у';
  if (status === 'ACTIVE' || status === 'FUTURE') return '';
  if (status === 'CANCELLED') return '×';
  return '—';
}

const badgeClasses = computed(() => `attendance-status-badge--${statusTone(props.lesson.status)}`);

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
          v-if="explanationText || explanationDocument.length"
          class="attendance-status-badge"
          :class="badgeClasses"
          type="button"
          :aria-label="`Статус: ${lessonStatusLabel(lesson.status)} · ${explanationText}`"
          :aria-expanded="explanationOpen !== null"
          :aria-describedby="explanationOpen ? `attendance-explanation-${lesson.id}` : undefined"
          @mouseenter="explanationOpen = explanationOpen ?? 'hover'"
          @mouseleave="closeHoverExplanation"
          @focus="explanationOpen = 'focus'"
          @blur="closeExplanationOnBlur"
          @click="openStatus"
          @keydown.esc="explanationOpen = null"
        >
          {{ statusSymbol(lesson.status) }}
        </button>
        <button
          v-else-if="hasRequestOptions"
          class="attendance-status-badge"
          :class="badgeClasses"
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
          class="attendance-status-badge"
          :class="badgeClasses"
          role="img"
          :aria-label="`Статус: ${lessonStatusLabel(lesson.status)}`"
          >{{ statusSymbol(lesson.status) }}</span
        >
      </header>
      <div
        v-if="explanationOpen && (explanationText || explanationDocument.length)"
        class="attendance-status-explanation"
        data-attendance-explanation
        @mouseleave="closeHoverExplanation"
      >
        <p
          :id="`attendance-explanation-${lesson.id}`"
          role="tooltip"
        >
          {{ explanationText }}
        </p>
        <AttendanceDocumentPreview
          v-for="(document, index) in explanationDocument"
          :key="index"
          ref="documentPreview"
          :document="document"
          @blur="closeExplanationOnBlur"
        />
        <button
          v-if="explanationOpen === 'tap'"
          class="attendance-explanation-close"
          type="button"
          aria-label="Закрыть причину"
          @click="explanationOpen = null"
        >
          <MobileIcon name="close" />
        </button>
      </div>
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
