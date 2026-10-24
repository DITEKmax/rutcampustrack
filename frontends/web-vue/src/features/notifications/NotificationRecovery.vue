<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { webNotifications as n } from './notifications'
const status = ref<HTMLElement | null>(null)
async function check(event: Event) {
  const store = n.value
  const hadFocus = document.activeElement === event.currentTarget
  if (!store) return
  await store.reconcile()
  await nextTick()
  if (n.value === store && !store.uncertain.value && hadFocus) status.value?.focus()
}
</script>
<template>
  <div
    v-if="n?.notice.value || n?.uncertain.value"
    class="web-notice web-stack"
    role="status"
  >
    <p
      ref="status"
      tabindex="-1"
    >
      {{ n?.notice.value }}
    </p>
    <button
      v-if="n?.uncertain.value"
      class="web-button"
      :disabled="n.pending.value || n.reconciling.value"
      @click="check"
    >
      {{ n.reconciling.value ? 'Проверяем состояние…' : 'Проверить состояние' }}
    </button>
  </div>
</template>
