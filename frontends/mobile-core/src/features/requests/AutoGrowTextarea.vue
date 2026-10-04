<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import './auto-grow-textarea.pcss'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const textarea = ref<HTMLTextAreaElement | null>(null)
let resizeObserver: ResizeObserver | null = null
let rootObserver: MutationObserver | null = null
let lastWidth = -1
let disposed = false
let resizeFrame: number | null = null

function resize(): void {
  const element = textarea.value
  if (!element || disposed) return
  // Reset before measuring to allow deletion and narrower/wider lines to shrink.
  element.style.height = 'auto'
  const styles = getComputedStyle(element)
  const borders = parseFloat(styles.borderTopWidth) + parseFloat(styles.borderBottomWidth)
  const minimum = parseFloat(styles.minHeight) || 0
  element.style.height = Math.ceil(Math.max(minimum, element.scrollHeight + borders)) + 'px'
  // Restoring height can restore the page scrollbar and narrow this control.
  // Fit the final wrapping without resetting height again (which removes it).
  for (let pass = 0; pass < 3 && element.scrollHeight > element.clientHeight; pass += 1) {
    element.style.height = Math.ceil(Math.max(minimum, element.scrollHeight + borders)) + 'px'
  }
  lastWidth = element.clientWidth
}
function scheduleResize(): void {
  if (disposed) return
  void nextTick(() => {
    if (disposed) return
    // Primary measurement also runs in hidden previews where animation frames
    // may be paused. The frame only stabilizes layout after the immediate pass.
    resize()
    if (resizeFrame !== null) return
    resizeFrame = requestAnimationFrame(() => {
      resizeFrame = null
      resize()
    })
  })
}
function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
  resize()
  scheduleResize()
}
watch(() => props.modelValue, scheduleResize)
onMounted(() => {
  resize()
  scheduleResize()
  // Observe the control itself: a page scrollbar can change its wrapping width
  // after its initial height has changed, without a parent attribute mutation.
  resizeObserver = new ResizeObserver(() => {
    if (textarea.value && textarea.value.clientWidth !== lastWidth) scheduleResize()
  })
  if (textarea.value) resizeObserver.observe(textarea.value)
  // Root font and theme changes can wrap saved content without any input event.
  rootObserver = new MutationObserver(scheduleResize)
  let ancestor = textarea.value?.parentElement ?? null
  while (ancestor) {
    rootObserver.observe(ancestor, { attributes: true, attributeFilter: ['class', 'style'] })
    ancestor = ancestor.parentElement
  }
  window.addEventListener('resize', scheduleResize)
  document.fonts?.addEventListener('loadingdone', scheduleResize)
  void document.fonts?.ready.then(scheduleResize)
})
onBeforeUnmount(() => {
  disposed = true
  if (resizeFrame !== null) cancelAnimationFrame(resizeFrame)
  resizeFrame = null
  resizeObserver?.disconnect()
  rootObserver?.disconnect()
  window.removeEventListener('resize', scheduleResize)
  document.fonts?.removeEventListener('loadingdone', scheduleResize)
})
</script>

<template>
  <textarea
    ref="textarea"
    v-bind="$attrs"
    class="request-auto-grow"
    :value="props.modelValue"
    rows="1"
    @input="onInput"
  />
</template>
