<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import './headman-stats-select.pcss'
const props = withDefaults(defineProps<{ label: string; modelValue: string; options: readonly { value: string; label: string }[]; placeholder?: string; disabled?: boolean }>(), { placeholder: 'Выбери вариант', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const id = useId(); const open = ref(false); const active = ref(0)
const host = ref<HTMLElement | null>(null); const trigger = ref<HTMLButtonElement | null>(null); const list = ref<HTMLElement | null>(null)
const selected = computed(() => props.options.find(option => option.value === props.modelValue))
function focusOption(): void { void nextTick(() => list.value?.querySelectorAll<HTMLButtonElement>('[role="option"]')[active.value]?.focus()) }
function toggle(): void { open.value = !open.value; if (open.value) { active.value = Math.max(0, props.options.findIndex(option => option.value === props.modelValue)); focusOption() } }
function close(focus = false): void { open.value = false; if (focus) void nextTick(() => trigger.value?.focus()) }
function choose(value: string): void { emit('update:modelValue', value); close(true) }
function keydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') { event.preventDefault(); close(true); return }
  if (event.key === 'Tab') return
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key) || !props.options.length) return
  event.preventDefault()
  if (!open.value) { toggle(); return }
  active.value = event.key === 'Home' ? 0 : event.key === 'End' ? props.options.length - 1 : (active.value + (event.key === 'ArrowDown' ? 1 : -1) + props.options.length) % props.options.length
  focusOption()
}
function focusout(event: FocusEvent): void { if (event.relatedTarget instanceof Node && !host.value?.contains(event.relatedTarget)) close() }
function outside(event: PointerEvent): void { if (open.value && event.target instanceof Node && !host.value?.contains(event.target)) close() }
watch(() => props.disabled, disabled => { if (disabled) close() })
onMounted(() => { if (typeof document !== 'undefined') document.addEventListener('pointerdown', outside) })
onBeforeUnmount(() => { if (typeof document !== 'undefined') document.removeEventListener('pointerdown', outside) })
</script>
<template>
  <div
    ref="host"
    class="headman-stats-select"
    @keydown="keydown"
    @focusout="focusout"
  >
    <button
      ref="trigger"
      class="headman-stats-select__trigger"
      type="button"
      :aria-label="label"
      aria-haspopup="listbox"
      :aria-controls="id"
      :aria-describedby="id + '-value'"
      :aria-expanded="open"
      :disabled="disabled"
      @click="toggle"
    >
      <span :id="id + '-value'">{{ selected?.label ?? placeholder }}</span><MobileIcon name="chevron-down" />
    </button>
    <div
      v-if="open"
      :id="id"
      ref="list"
      class="headman-stats-select__options"
      role="listbox"
      :aria-label="label"
    >
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        role="option"
        :aria-selected="option.value === modelValue"
        :tabindex="-1"
        @click="choose(option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>
