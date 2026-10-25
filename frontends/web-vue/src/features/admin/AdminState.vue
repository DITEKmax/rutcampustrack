<script setup lang="ts">
defineProps<{ loading: boolean; busy: boolean; error: string; notice: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <p
    v-if="loading"
    role="status"
  >
    Загружаем…
  </p>
  <div
    v-if="error"
    class="web-notice admin-feedback-error"
    role="alert"
  >
    <strong>Не удалось выполнить действие</strong><p>{{ error }}</p>
    <button
      class="web-button"
      :disabled="busy"
      @click="$emit('retry')"
    >
      Обновить данные
    </button>
  </div>
  <p
    v-if="notice"
    class="web-notice"
    role="status"
  >
    {{ notice }}
  </p>
</template>
