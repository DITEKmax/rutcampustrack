<script setup lang="ts">
import './headman.pcss';
defineProps<{ title: string; loading?: boolean; error?: string | null }>();
defineEmits<{ reload: [] }>();
</script>
<template>
  <section
    class="headman-page web-stack"
    :aria-busy="loading"
  >
    <div class="headman-heading">
      <h1>{{ title }}</h1><slot name="actions" />
    </div>
    <div
      v-if="error"
      class="web-notice web-error"
      role="alert"
    >
      <p>{{ error }}</p><button
        class="web-button"
        :disabled="loading"
        @click="$emit('reload')"
      >
        Обновить данные
      </button>
    </div>
    <div
      v-if="loading"
      class="web-stack"
      aria-busy="true"
    >
      <span
        class="web-sr-only"
        role="status"
      >Загрузка…</span>
      <div
        class="web-skeleton"
        aria-hidden="true"
      />
    </div>
    <slot />
  </section>
</template>
