<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
interface MapSelectOption { readonly id: string; readonly label: string }
import './map-select.pcss'

const props = withDefaults(defineProps<{
  id: string
  modelValue: string | null
  options: readonly MapSelectOption[]
  disabled?: boolean
  invalid?: boolean
  describedby?: string
}>(), { disabled: false, invalid: false, describedby: '' })
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const open = ref(false)
const activeIndex = ref(0)
const selected = computed(() => props.options.find((option) => option.id === props.modelValue))
const activeId = computed(() => open.value && props.options[activeIndex.value] ? props.id + '-option-' + activeIndex.value : undefined)

function close(restoreFocus = false): void {
  open.value = false
  if (restoreFocus) trigger.value?.focus()
}
function reveal(index?: number): void {
  if (props.disabled || props.options.length === 0) return
  activeIndex.value = index ?? Math.max(0, props.options.findIndex((option) => option.id === props.modelValue))
  open.value = true
  revealActive()
}
function revealActive(): void {
  void nextTick(() => {
    if (activeId.value) document.getElementById(activeId.value)?.scrollIntoView({ block: 'nearest' })
  })
}
function select(index: number): void {
  const option = props.options[index]
  if (!option || props.disabled) return
  emit('update:modelValue', option.id)
  close(true)
}
function onKeydown(event: KeyboardEvent): void {
  if (props.disabled) return
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    close(true)
  } else if (event.key === 'Tab') {
    close()
  } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) reveal(event.key === 'ArrowUp' ? props.options.length - 1 : undefined)
    else {
      activeIndex.value = (activeIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + props.options.length) % props.options.length
      revealActive()
    }
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    reveal(event.key === 'Home' ? 0 : props.options.length - 1)
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (open.value) select(activeIndex.value)
    else reveal()
  } else if (event.key.length === 1 && !event.ctrlKey && !event.altKey && !event.metaKey) {
    const search = event.key.toLocaleLowerCase('ru')
    const index = props.options.findIndex((option, index) => index > activeIndex.value && option.label.toLocaleLowerCase('ru').startsWith(search))
    const fallback = props.options.findIndex((option) => option.label.toLocaleLowerCase('ru').startsWith(search))
    if (index >= 0 || fallback >= 0) {
      event.preventDefault()
      reveal(index >= 0 ? index : fallback)
    }
  }
}
function onOutside(event: PointerEvent): void {
  if (event.target instanceof Node && !root.value?.contains(event.target)) close()
}
function onFocusOut(event: FocusEvent): void {
  if (!(event.relatedTarget instanceof Node) || !root.value?.contains(event.relatedTarget)) close()
}
watch(() => props.disabled, (disabled) => { if (disabled) close() })
watch(() => props.options, () => {
  if (props.options.length === 0) close()
  activeIndex.value = Math.min(activeIndex.value, Math.max(0, props.options.length - 1))
})
onMounted(() => document.addEventListener('pointerdown', onOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutside))
</script>

<template>
  <div
    ref="root"
    class="map-select"
    @focusout="onFocusOut"
  >
    <button
      :id="id"
      ref="trigger"
      class="map-select__trigger"
      type="button"
      role="combobox"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="id + '-listbox'"
      :aria-activedescendant="activeId"
      :aria-describedby="describedby"
      :aria-invalid="invalid || undefined"
      :disabled="disabled || options.length === 0"
      @click="open ? close() : reveal()"
      @keydown="onKeydown"
    >
      <span>{{ selected?.label || 'Выбери значение' }}</span>
      <MobileIcon name="chevron-down" />
    </button>
    <ul
      v-if="open"
      :id="id + '-listbox'"
      class="map-select__menu"
      role="listbox"
      :aria-labelledby="id"
    >
      <li
        v-for="(option, index) in options"
        :id="id + '-option-' + index"
        :key="option.id"
        class="map-select__option"
        :data-selected="option.id === modelValue"
        :data-active="index === activeIndex"
        role="option"
        :aria-selected="option.id === modelValue"
        @pointerdown.prevent
        @click="select(index)"
      >
        {{ option.label }}
      </li>
    </ul>
  </div>
</template>
