<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import './mobile-tools-sheet.pcss';
const props = defineProps<{ open: boolean; title: string; iconClose?: boolean }>();
const emit = defineEmits<{ 'update:open': [value: boolean] }>();
const dialog = ref<HTMLDialogElement | null>(null);
let trigger: HTMLElement | null = null;
let restoreFocus = true;
watch(
  () => props.open,
  async (value) => {
    await nextTick();
    if (value !== props.open) return;
    if (value && !dialog.value?.open) {
      restoreFocus = true;
      trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      dialog.value?.showModal();
    } else if (!value && dialog.value?.open) dialog.value.close();
  },
  { immediate: true },
);
function close(): void {
  emit('update:open', false);
  if (restoreFocus && trigger?.isConnected) trigger.focus();
}
function closeImmediately(restore = true): void {
  restoreFocus = restore;
  dialog.value?.close();
  emit('update:open', false);
}
defineExpose({ close: closeImmediately });
onBeforeUnmount(() => {
  restoreFocus = false;
  dialog.value?.close();
});
</script>
<template>
  <dialog
    ref="dialog"
    class="mobile-tools-sheet"
    :aria-label="title"
    @cancel.prevent="emit('update:open', false)"
    @close="close"
  >
    <header>
      <h2>{{ title }}</h2><button
        type="button"
        :class="{ 'mobile-tools-sheet__icon-close': iconClose }"
        aria-label="Закрыть настройки"
        @click="emit('update:open', false)"
      >
        <svg
          v-if="iconClose"
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          aria-hidden="true"
        >
          <path d="m4 4 8 8M12 4l-8 8" />
        </svg>
        <template v-else>
          Закрыть
        </template>
      </button>
    </header>
    <div class="mobile-tools-sheet__content">
      <slot />
    </div>
  </dialog>
</template>
