<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string; empty?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div
    v-if="error"
    class="web-notice web-error web-stack"
    role="alert"
  >
    <p>{{ error }}</p><button
      class="web-button"
      @click="$emit('retry')"
    >
      Повторить
    </button>
  </div>
  <div
    v-else-if="loading"
    class="web-stack"
    aria-busy="true"
  >
    <span
      class="web-sr-only"
      role="status"
    >Загружаем данные…</span>
    <div
      class="web-skeleton"
      aria-hidden="true"
    />
  </div>
  <p
    v-else-if="empty"
    class="web-notice"
    role="status"
  >
    {{ empty }}
  </p>
</template>
