<script setup lang="ts">
import './notifications-entry.pcss'

const emit = defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    class="notifications-entry"
    type="button"
    aria-label="Открыть уведомления"
    @click="emit('click')"
  >
    Уведомления
  </button>
</template>
