<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string; empty?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div
    v-if="error"
    class="web-notice web-stack"
    role="alert"
  >
    <p>{{ error }}</p>
    <button
      class="web-button"
      :disabled="loading"
      @click="$emit('retry')"
    >
      Повторить
    </button>
  </div>
  <p
    v-else-if="loading"
    role="status"
    aria-busy="true"
  >
    Загружаем данные…
  </p>
  <p
    v-else-if="empty"
    class="web-notice"
    role="status"
  >
    {{ empty }}
  </p>
</template>
