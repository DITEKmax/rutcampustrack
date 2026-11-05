<script setup lang="ts">
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import type { AttendanceLessonDocument } from './attendance-view-model';

const props = defineProps<{ document: AttendanceLessonDocument | null }>();
const emit = defineEmits<{ 'full-view': [open: boolean]; blur: [event: FocusEvent] }>();
const fullView = ref(false);
const dialog = ref<HTMLDialogElement | null>(null);
const previewButton = ref<HTMLButtonElement | null>(null);
const failed = ref(false);

function closeFullView(): void {
  dialog.value?.close?.();
  fullView.value = false;
  emit('full-view', false);
  void nextTick(() => previewButton.value?.focus?.());
}
function closeOverlay(): boolean {
  if (!fullView.value) return false;
  closeFullView();
  return true;
}
defineExpose({ closeOverlay });
function openFullView(): void {
  fullView.value = true;
  emit('full-view', true);
  void nextTick(() => {
    if (fullView.value) dialog.value?.showModal?.();
  });
}
watch([() => props.document?.preview?.src, () => props.document?.preview?.kind], () => {
  if (fullView.value) closeFullView();
  failed.value = false;
});
onBeforeUnmount(() => {
  dialog.value?.close?.();
  if (fullView.value) emit('full-view', false);
});
</script>

<template>
  <div
    v-if="document"
    data-attendance-document
  >
    <template v-if="document.preview && !failed">
      <button
        ref="previewButton"
        class="attendance-document-preview"
        type="button"
        :aria-label="`Открыть полностью: ${document.label}`"
        @click="openFullView"
      >
        <img
          v-if="document.preview.kind === 'image'"
          :src="document.preview.src"
          :alt="document.preview.alt ?? document.label"
          @error="failed = true"
        />
        <object
          v-else
          :data="document.preview.src"
          type="application/pdf"
          :aria-label="document.preview.alt ?? document.label"
          tabindex="-1"
          >{{ document.label }}</object
        >
      </button>
      <dialog
        v-if="fullView"
        ref="dialog"
        class="attendance-document-full"
        :aria-label="document.label"
        @cancel.prevent="closeFullView"
      >
        <header>
          <h2>{{ document.label }}</h2>
          <button
            class="attendance-document-close"
            type="button"
            aria-label="Закрыть"
            autofocus
            @click="closeFullView"
          >
            <MobileIcon name="close" />
          </button>
        </header>
        <img
          v-if="document.preview.kind === 'image'"
          :src="document.preview.src"
          :alt="document.preview.alt ?? document.label"
        />
        <object
          v-else
          :data="document.preview.src"
          type="application/pdf"
          :aria-label="document.label"
        >
          <button
            type="button"
            @click="document.open()"
            >Открыть {{ document.label }}</button
          >
        </object>
        <button type="button" @click="document.open()">Скачать {{ document.label }}</button>
      </dialog>
    </template>
    <template v-else>
      <p v-if="document.error || document.unavailable" role="status">
        {{ document.error ?? 'Документ сейчас недоступен.' }}
      </p>
      <p
        v-if="failed"
        role="status"
      >
        Не удалось загрузить документ.
      </p>
      <button
        class="attendance-status-document"
        type="button"
        :disabled="document.pending || document.unavailable"
        @click="document.open()"
        @blur="emit('blur', $event)"
      >
        {{ document.pending ? 'Загружаем документ…' : document.error ? `Повторить: ${document.label}` : document.label }}
      </button>
    </template>
  </div>
</template>
