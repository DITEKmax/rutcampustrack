<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import {
  HOMEWORK_COMMIT_PROGRESS,
  beginHomeworkPointer,
  createHomeworkGesture,
  finishHomeworkPointer,
  moveHomeworkPointer,
  resetHomeworkGesture,
  stageHomeworkKey,
} from './homework-gesture';
import './homework-completion.pcss';

const props = defineProps<{
  completed: boolean;
  disabled: boolean;
  pending: boolean;
  label: string;
  subject: string;
  resetKey: string;
}>();
const emit = defineEmits<{
  complete: [desired: boolean];
  handleReady: [element: HTMLElement | null];
}>();
const track = ref<HTMLElement | null>(null);
const handle = ref<HTMLButtonElement | null>(null);
const state = reactive(createHomeworkGesture(props.completed, 0));
const submitted = ref(false);
// completed may contain the owner's optimistic presentation while pending.
const confirmed = ref(props.completed);
const settled = ref<'completed' | 'open' | null>(null);
const saving = computed(() => props.pending || submitted.value);
const armed = computed(() => state.progress >= HOMEWORK_COMMIT_PROGRESS);
const stagedCompletion = ref<boolean | null>(null);
const instructions = computed(() =>
  confirmed.value
    ? 'Стрелкой влево перемести шарик до конца, затем нажми Enter, чтобы снять отметку. Escape — сброс.'
    : 'Стрелкой вправо перемести шарик до конца, затем нажми Enter, чтобы выполнить. Escape — сброс.',
);
const position = computed(() =>
  stagedCompletion.value !== null
    ? Number(stagedCompletion.value)
    : props.pending
      ? Number(props.completed)
      : confirmed.value
        ? 1 - state.progress
        : state.progress,
);
const valueText = computed(() =>
  saving.value
    ? 'Сохраняем состояние'
    : state.progress > 0
      ? `Перемещение ${Math.round(state.progress * 100)} процентов. ${instructions.value}`
      : `${confirmed.value ? 'Выполнено' : 'Не выполнено'}. ${instructions.value}`,
);
function reset(): void {
  const pointerId = state.pointer?.id;
  resetHomeworkGesture(state);
  if (pointerId !== undefined && handle.value?.hasPointerCapture(pointerId))
    handle.value.releasePointerCapture(pointerId);
}
function measure(): void {
  const trackWidth = track.value?.getBoundingClientRect().width ?? 0;
  const handleWidth = handle.value?.getBoundingClientRect().width ?? 0;
  const padding = track.value
    ? Number.parseFloat(getComputedStyle(track.value).paddingInlineStart)
    : 0;
  state.travel = Math.max(0, trackWidth - handleWidth - padding * 2);
}
function submit(desired: boolean | null): void {
  if (desired === null || props.disabled || saving.value) return;
  settled.value = null;
  submitted.value = true;
  stagedCompletion.value = desired;
  emit('complete', desired);
}
function begin(event: PointerEvent): void {
  if (props.disabled || saving.value || !event.isPrimary || event.button !== 0 || state.pointer)
    return;
  settled.value = null;
  measure();
  handle.value?.focus({ preventScroll: true });
  beginHomeworkPointer(state, event.pointerId, event.clientX, event.clientY);
  handle.value?.setPointerCapture(event.pointerId);
}
function move(event: PointerEvent): void {
  moveHomeworkPointer(state, event.pointerId, event.clientX, event.clientY);
}
function finish(event: PointerEvent): void {
  move(event);
  const desired = finishHomeworkPointer(state, event.pointerId);
  if (handle.value?.hasPointerCapture(event.pointerId))
    handle.value.releasePointerCapture(event.pointerId);
  submit(desired);
}
function cancel(event: PointerEvent): void {
  if (state.pointer?.id === event.pointerId) reset();
}
function key(event: KeyboardEvent): void {
  if (!['ArrowLeft', 'ArrowRight', 'Enter', ' ', 'Escape'].includes(event.key)) return;
  event.preventDefault();
  if (props.disabled || saving.value) return;
  settled.value = null;
  submit(stageHomeworkKey(state, event.key));
}
watch(
  () => [props.completed, props.disabled, props.pending, props.resetKey] as const,
  ([completed, , pending, identity], previous) => {
    const identityChanged = identity !== previous[3];
    if (identityChanged) {
      submitted.value = false;
      stagedCompletion.value = null;
      settled.value = null;
      confirmed.value = completed;
    } else if (!pending) {
      // An error restores the old value; only a changed authoritative value settles.
      settled.value = confirmed.value !== completed ? (completed ? 'completed' : 'open') : null;
      confirmed.value = completed;
      submitted.value = false;
      stagedCompletion.value = null;
    }
    state.completed = confirmed.value;
    reset();
  },
);
onMounted(() => emit('handleReady', handle.value));
onBeforeUnmount(() => {
  reset();
  emit('handleReady', null);
});
</script>

<template>
  <div
    ref="track"
    class="homework-completion"
    :class="{
      'homework-completion--completed': confirmed && !saving,
      'homework-completion--readonly': disabled && !pending,
      'homework-completion--dragging': !!state.pointer,
      'homework-completion--armed': armed && !saving,
      'homework-completion--saving': saving,
    }"
    :data-state="saving ? 'pending' : confirmed ? 'completed' : 'open'"
    :data-settled="settled"
    :style="{ '--homework-progress': state.progress }"
  >
    <span
      class="homework-completion__fill"
      :style="{ transform: `scaleX(${position})` }"
      aria-hidden="true"
    />
    <span
      class="homework-completion__label"
      aria-hidden="true"
      >{{ !saving && armed ? 'Отпусти или нажми Enter' : label }}</span
    >
    <span
      class="homework-completion__travel"
      :style="{ transform: `translateX(${position * 100}%)` }"
    >
      <button
        ref="handle"
        class="homework-completion__handle"
        type="button"
        role="slider"
        :disabled="disabled || saving"
        :aria-label="`${confirmed ? 'Снять отметку' : 'Выполнить'}: ${subject}`"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="Math.round(state.progress * 100)"
        :aria-valuetext="valueText"
        :aria-busy="saving"
        @pointerdown="begin"
        @pointermove="move"
        @pointerup="finish"
        @pointercancel="cancel"
        @lostpointercapture="cancel"
        @keydown="key"
        @blur="reset"
        @click.prevent
      >
        <svg
          class="homework-completion__mark"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            v-if="confirmed && !saving"
            class="homework-completion__check"
            pathLength="1"
            d="m7 12 3 3 7-7"
          />
          <path
            v-else
            class="homework-completion__direction"
            :class="{ 'homework-completion__direction--reverse': confirmed }"
            d="m9 7 5 5-5 5"
          />
        </svg>
        <svg
          class="homework-completion__ring"
          viewBox="0 0 48 48"
          aria-hidden="true"
        >
          <circle
            cx="24"
            cy="24"
            r="21"
            pathLength="1"
          />
        </svg>
      </button>
    </span>
  </div>
</template>
