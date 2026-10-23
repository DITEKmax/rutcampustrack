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
    <div class="headman-heading"
      ><h1>{{ title }}</h1
      ><slot name="actions"
    /></div>
    <div
      v-if="error"
      class="web-notice web-error"
      role="alert"
      ><p>{{ error }}</p
      ><button
        class="web-button"
        :disabled="loading"
        @click="$emit('reload')"
        >Обновить данные</button
      ></div
    >
    <p
      v-if="loading"
      role="status"
      >Загрузка…</p
    >
    <slot />
  </section>
</template>
