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
  <p
    v-else-if="loading"
    class="web-notice"
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
