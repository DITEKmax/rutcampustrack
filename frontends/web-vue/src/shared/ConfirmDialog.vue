<script setup lang="ts">
import { ref, useId, watchPostEffect } from 'vue';
const props = defineProps<{ open: boolean; title: string; confirmLabel: string; busy?: boolean; danger?: boolean }>();
const emit = defineEmits<{ confirm: []; cancel: [] }>();
const titleId = useId();
const dialog = ref<HTMLDialogElement | null>(null);
watchPostEffect(() => {
  if (props.open && !dialog.value?.open) dialog.value?.showModal();
  else if (!props.open && dialog.value?.open) dialog.value.close();
});
</script>
<template>
  <dialog
    ref="dialog"
    class="web-confirm-dialog web-stack"
    :aria-labelledby="titleId"
    :aria-busy="busy"
    @cancel.prevent="!busy && emit('cancel')"
  >
    <h2 :id="titleId">
      {{ title }}
    </h2><div><slot /></div>
    <div class="web-confirm-actions">
      <button
        class="web-button"
        type="button"
        :disabled="busy"
        autofocus
        @click="emit('cancel')"
      >
        Отмена
      </button><button
        class="web-button"
        :class="danger ? 'web-button-danger' : 'web-button-primary'"
        type="button"
        :disabled="busy"
        @click="emit('confirm')"
      >
        {{ confirmLabel }}
      </button>
    </div>
  </dialog>
</template>
