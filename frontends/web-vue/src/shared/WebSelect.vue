<script setup lang="ts" generic="T extends string | number">
import { computed, nextTick, ref, useId, watch } from 'vue';
import MobileIcon from '../../../mobile-core/src/shared/components/MobileIcon.vue';
import SvgIcon from '../../../shared/foundation/SvgIcon.vue';
import checkIcon from '../../../mobile-core/src/assets/admin/check.svg';
import { useFieldPopover } from './use-field-popover';
import './web-fields.pcss';

const props = defineProps<{
  modelValue: T;
  options: readonly { value: T; label: string; disabled?: boolean }[];
  label: string;
  disabled?: boolean;
  required?: boolean;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: T]; change: [value: T] }>();
const id = useId();
const root = ref<HTMLElement | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);
const panel = ref<HTMLElement | null>(null);
const active = ref(-1);
const invalid = ref(false);
const selected = computed(() => props.options.find((option) => option.value === props.modelValue));
let search = '';
let lastSearch = 0;
const popup = useFieldPopover(trigger, panel, () => focusOption(active.value), true, root);
function focusOption(index: number) {
  const option = props.options[index];
  if (!option || option.disabled) return;
  active.value = index;
  void nextTick(() => {
    const target = panel.value?.querySelectorAll<HTMLButtonElement>('[role="option"]')[index];
    target?.focus({ preventScroll: true });
    const list = panel.value;
    if (!list || !target) return;
    if (target.offsetTop < list.scrollTop) list.scrollTop = target.offsetTop;
    else if (target.offsetTop + target.offsetHeight > list.scrollTop + list.clientHeight)
      list.scrollTop = target.offsetTop + target.offsetHeight - list.clientHeight;
  });
}
function enabledIndex(direction: -1 | 1, start: number) {
  const count = props.options.length;
  for (let step = 1; step <= count; step++) {
    const index = (start + direction * step + count) % count;
    if (!props.options[index]?.disabled) return index;
  }
  return -1;
}
function open(index?: number) {
  if (props.disabled) return;
  const selectedIndex = props.options.findIndex((option) => option.value === props.modelValue && !option.disabled);
  active.value = index ?? (selectedIndex >= 0 ? selectedIndex : enabledIndex(1, -1));
  popup.open();
}
function choose(index: number) {
  const option = props.options[index];
  if (!option || option.disabled || props.disabled) return;
  if (option.value !== props.modelValue) {
    emit('update:modelValue', option.value);
    emit('change', option.value);
  }
  invalid.value = Boolean(props.required && option.value === '');
  popup.close();
}
function keydown(event: KeyboardEvent) {
  if (props.disabled) return;
  const opened = popup.expanded.value;
  const move = (index: number) => opened ? focusOption(index) : open(index);
  switch (event.key) {
    case 'ArrowDown': event.preventDefault(); if (opened) focusOption(enabledIndex(1, active.value)); else open(); return;
    case 'ArrowUp': event.preventDefault(); if (opened) focusOption(enabledIndex(-1, active.value)); else open(); return;
    case 'Home': event.preventDefault(); move(enabledIndex(1, -1)); return;
    case 'End': event.preventDefault(); move(enabledIndex(-1, 0)); return;
    case 'Enter':
    case ' ': event.preventDefault(); if (opened) choose(active.value); else open(); return;
    case 'Escape': if (opened) { event.preventDefault(); event.stopPropagation(); popup.close(); } return;
    case 'Tab': if (opened) popup.close(); return;
  }
  if (event.key.length !== 1 || event.ctrlKey || event.metaKey || event.altKey) return;
  event.preventDefault();
  const now = Date.now();
  search = now - lastSearch > 700 ? event.key : search + event.key;
  lastSearch = now;
  const query = [...search].every((letter) => letter === search[0]) ? search[0]! : search;
  for (let step = 1; step <= props.options.length; step++) {
    const index = (active.value + step + props.options.length) % props.options.length;
    const option = props.options[index];
    if (option && !option.disabled && option.label.toLocaleLowerCase('ru').startsWith(query.toLocaleLowerCase('ru'))) {
      move(index);
      break;
    }
  }
}
watch(() => props.disabled, (disabled) => { if (disabled) popup.close(false); });
watch(() => props.modelValue, () => { invalid.value = false; });
</script>

<template>
  <div ref="root" class="web-field web-select-field">
    <label :id="`${id}-label`" :for="id">{{ label }}</label>
    <button
      :id="id" ref="trigger" class="web-field-trigger" type="button"
      :disabled="disabled" aria-haspopup="listbox" :aria-expanded="popup.expanded.value"
      :aria-controls="`${id}-options`" :aria-labelledby="`${id}-label ${id}-value`"
      :aria-required="required || undefined" :aria-invalid="invalid || undefined"
      :aria-describedby="invalid ? `${id}-error` : undefined"
      @click="popup.expanded.value ? popup.close() : open()" @keydown="keydown"
    >
      <span :id="`${id}-value`" class="web-field-value">{{ selected?.label ?? 'Выбери значение' }}</span>
      <MobileIcon name="chevron-down" />
    </button>
    <input
      v-if="required" class="web-select-validation" type="text" tabindex="-1" aria-hidden="true"
      :value="selected?.value ?? ''" :disabled="disabled" required
      @invalid.prevent="invalid = true; trigger?.focus()"
    >
    <p v-if="invalid" :id="`${id}-error`" class="web-error" role="status">Выбери значение.</p>
    <div
      :id="`${id}-options`" ref="panel" class="web-field-popover web-select-options" popover="manual"
      role="listbox" :aria-labelledby="`${id}-label`"
      @toggle="popup.toggled" @keydown="keydown" @focusout="popup.focusout"
    >
      <button
        v-for="(option, index) in options" :key="option.value"
        class="web-select-option" type="button" role="option" tabindex="-1"
        :aria-selected="option.value === modelValue" :disabled="option.disabled"
        @focus="active = index" @click="choose(index)"
      >
        <span>{{ option.label }}</span><span v-if="option.value === modelValue" class="web-select-check" aria-hidden="true"><SvgIcon :src="checkIcon" /></span>
      </button>
      <p v-if="!options.length" class="web-field-empty">Нет доступных значений.</p>
    </div>
  </div>
</template>
