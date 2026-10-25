<script setup lang="ts">
import { computed } from 'vue'
import type { HeadmanAssistantPermission } from '../../../mobile-core/src/features/headman-group/headman-group-client'
import { webAssistantAccess as access } from '../app/assistant'
import { webSession } from '../auth/session'

const props = defineProps<{ permission?: HeadmanAssistantPermission | undefined }>()
const required = computed(() => webSession.snapshot.value?.activeRole === 'STUDENT' ? props.permission : undefined)
const admitted = computed(() => !required.value || (access.loaded.value && access.permissions.value.includes(required.value)))
async function retry() {
  try { await access.load(true) } catch { /* The access owner exposes the failure. */ }
}
</script>
<template>
  <slot v-if="admitted" />
  <section
    v-else
    class="web-stack"
    aria-labelledby="web-assistant-access-title"
  >
    <h1 id="web-assistant-access-title">
      {{ access.loaded.value ? 'Доступ к разделу отозван' : 'Проверка доступа' }}
    </h1>
    <p
      v-if="access.loaded.value"
      role="alert"
    >
      Этот раздел больше недоступен с текущими делегированными правами.
    </p>
    <p
      v-else
      role="status"
    >
      {{ access.error.value || 'Проверяем твои делегированные права…' }}
    </p>
    <button
      v-if="!access.checking.value"
      class="web-button"
      @click="retry"
    >
      Проверить доступ
    </button>
    <RouterLink
      class="web-button"
      :to="{ name: 'role-home' }"
    >
      Открыть свою главную
    </RouterLink>
  </section>
</template>
