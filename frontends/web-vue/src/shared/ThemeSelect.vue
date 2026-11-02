<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue';
import { theme, setTheme } from '../app/theme';
import SvgIcon from '../../../shared/foundation/SvgIcon.vue';
import sunIcon from './assets/theme-sun.svg';
import moonIcon from './assets/theme-moon.svg';
import checkIcon from '../../../mobile-core/src/assets/admin/check.svg';
const choices = [
  { value: 'system', label: 'Системная тема' },
  { value: 'light', label: 'Светлая тема' },
  { value: 'dark', label: 'Тёмная тема' },
] as const;
const id = useId();
const trigger = ref<HTMLButtonElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const expanded = ref(false);
const activeIndex = ref(0);
const selectedIndex = computed(() => Math.max(0, choices.findIndex((choice) => choice.value === theme.value.mode)));
let requestedIndex: number | null = null;
function focusOption(index: number) {
  activeIndex.value = (index + choices.length) % choices.length;
  void nextTick(() => menu.value?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')[activeIndex.value]?.focus());
}
function toggled(event: Event) {
  expanded.value = (event as ToggleEvent).newState === 'open';
  if (expanded.value) {
    focusOption(requestedIndex ?? selectedIndex.value);
    requestedIndex = null;
  }
}
function open(index: number) {
  if (menu.value?.matches(':popover-open')) focusOption(index);
  else {
    requestedIndex = index;
    menu.value?.showPopover();
  }
}
function close() {
  if (menu.value?.matches(':popover-open')) menu.value.hidePopover();
  trigger.value?.focus();
}
function choose(index: number) {
  const choice = choices[index];
  if (!choice) return;
  setTheme(choice.value);
  close();
}
function keydown(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowDown': event.preventDefault(); focusOption(activeIndex.value + 1); break;
    case 'ArrowUp': event.preventDefault(); focusOption(activeIndex.value - 1); break;
    case 'Home': event.preventDefault(); focusOption(0); break;
    case 'End': event.preventDefault(); focusOption(choices.length - 1); break;
    case 'Enter':
    case ' ': event.preventDefault(); choose(activeIndex.value); break;
    case 'Escape': event.preventDefault(); close(); break;
  }
}
</script>

<template>
  <div class="web-theme-select">
    <button
      ref="trigger"
      class="web-button web-theme-trigger"
      type="button"
      :aria-label="`Тема оформления: ${choices[selectedIndex]?.label}`"
      aria-haspopup="menu"
      :aria-expanded="expanded"
      :aria-controls="id"
      :popovertarget="id"
      @keydown.down.prevent="open(0)"
      @keydown.up.prevent="open(choices.length - 1)"
    >
      <span class="web-theme-icon" aria-hidden="true"><SvgIcon :src="theme.resolvedMode === 'dark' ? sunIcon : moonIcon" :width="20" :height="20" /></span>
      <span class="web-theme-label" aria-hidden="true">{{ choices[selectedIndex]?.label }}</span>
    </button>
    <div
      :id="id"
      ref="menu"
      class="web-theme-menu"
      popover="auto"
      role="menu"
      aria-label="Тема оформления"
      @toggle="toggled"
      @keydown="keydown"
    >
      <button
        v-for="(choice, index) in choices"
        :key="choice.value"
        class="web-theme-option"
        type="button"
        role="menuitemradio"
        :aria-checked="theme.mode === choice.value"
        :tabindex="activeIndex === index ? 0 : -1"
        @focus="activeIndex = index"
        @click="choose(index)"
      >
        <span>{{ choice.label }}</span>
        <span class="web-theme-check" :class="{ 'is-selected': theme.mode === choice.value }" aria-hidden="true"><SvgIcon :src="checkIcon" /></span>
      </button>
    </div>
  </div>
</template>
