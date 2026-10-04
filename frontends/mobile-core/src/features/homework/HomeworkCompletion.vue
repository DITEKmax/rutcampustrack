<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  beginHomeworkPointer, createHomeworkGesture, finishHomeworkPointer,
  moveHomeworkPointer, resetHomeworkGesture, stageHomeworkKey,
} from './homework-gesture'
import './homework-completion.pcss'

const props = defineProps<{ completed: boolean; disabled: boolean; pending: boolean; label: string; subject: string; resetKey: string }>()
const emit = defineEmits<{ complete: [desired: boolean]; handleReady: [element: HTMLElement | null] }>()
const track = ref<HTMLElement | null>(null)
const handle = ref<HTMLButtonElement | null>(null)
const state = reactive(createHomeworkGesture(props.completed, 0))
let submitted = false
const instructions = computed(() => props.completed
  ? 'Стрелкой влево перемести шарик до конца, затем нажми Enter, чтобы снять отметку. Escape — сброс.'
  : 'Стрелкой вправо перемести шарик до конца, затем нажми Enter, чтобы выполнить. Escape — сброс.')
const position = computed(() => props.completed ? 1 - state.progress : state.progress)
const valueText = computed(() => props.pending ? 'Сохраняем состояние' : state.progress > 0
  ? `Перемещение ${Math.round(state.progress * 100)} процентов. ${instructions.value}`
  : `${props.completed ? 'Выполнено' : 'Не выполнено'}. ${instructions.value}`)
function reset(): void { resetHomeworkGesture(state) }
function measure(): void {
  const trackWidth = track.value?.getBoundingClientRect().width ?? 0
  const handleWidth = handle.value?.getBoundingClientRect().width ?? 0
  const padding = track.value ? Number.parseFloat(getComputedStyle(track.value).paddingInlineStart) : 0
  state.travel = Math.max(0, trackWidth - handleWidth - padding * 2)
}
function submit(desired: boolean | null): void {
  if (desired === null || props.disabled || submitted) return
  submitted = true
  emit('complete', desired)
}
function begin(event: PointerEvent): void {
  if (props.disabled || submitted || !event.isPrimary || event.button !== 0 || state.pointer) return
  measure()
  handle.value?.focus({ preventScroll: true })
  beginHomeworkPointer(state, event.pointerId, event.clientX, event.clientY)
  handle.value?.setPointerCapture(event.pointerId)
}
function move(event: PointerEvent): void { moveHomeworkPointer(state, event.pointerId, event.clientX, event.clientY) }
function finish(event: PointerEvent): void {
  move(event)
  submit(finishHomeworkPointer(state, event.pointerId))
}
function cancel(event: PointerEvent): void { if (state.pointer?.id === event.pointerId) reset() }
function key(event: KeyboardEvent): void {
  if (!['ArrowLeft', 'ArrowRight', 'Enter', ' ', 'Escape'].includes(event.key)) return
  event.preventDefault()
  if (props.disabled || submitted) return
  submit(stageHomeworkKey(state, event.key))
}
watch(() => [props.completed, props.disabled, props.resetKey], () => {
  state.completed = props.completed
  submitted = false
  reset()
})
onMounted(() => emit('handleReady', handle.value))
onBeforeUnmount(() => { reset(); emit('handleReady', null) })
</script>

<template>
  <div
    ref="track"
    class="homework-completion"
    :class="{ 'homework-completion--completed': completed, 'homework-completion--readonly': disabled && !pending }"
    :data-state="pending ? 'pending' : completed ? 'completed' : 'open'"
  >
    <span
      class="homework-completion__label"
      aria-hidden="true"
    >{{ label }}</span>
    <button
      ref="handle"
      class="homework-completion__handle"
      type="button"
      role="slider"
      :disabled="disabled"
      :aria-label="`${completed ? 'Снять отметку' : 'Выполнить'}: ${subject}`"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="Math.round(state.progress * 100)"
      :aria-valuetext="valueText"
      :aria-busy="pending"
      :style="{ insetInlineStart: `calc(var(--rct-space-1) + (100% - var(--rct-control-min-touch) - 2 * var(--rct-space-1)) * ${position})` }"
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
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          v-if="completed"
          d="m7 12 3 3 7-7"
        />
        <path
          v-else
          d="m9 7 5 5-5 5"
        />
      </svg>
    </button>
  </div>
</template>
