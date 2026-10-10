<script setup lang="ts">
import { computed } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import './notifications-entry.pcss'
const props = withDefaults(defineProps<{ count?: number | null; floating?: boolean; disabled?: boolean }>(), { count: null, floating: false, disabled: false })
const emit = defineEmits<{ click: [] }>()
const badge = computed(() => props.count !== null && props.count > 0 ? props.count > 99 ? '99+' : String(props.count) : null)
const label = computed(() => props.count === null ? 'Открыть уведомления, счётчик недоступен' : props.count > 0 ? `Открыть уведомления, непрочитанных: ${props.count}` : 'Открыть уведомления')
</script>
<template>
  <button
    class="notifications-entry"
    :class="{ 'notifications-entry--floating': floating }"
    type="button"
    :disabled="disabled"
    :aria-label="label"
    @click="emit('click')"
  >
    <MobileIcon name="bell" /><span
      v-if="badge"
      class="notifications-entry__badge"
      aria-hidden="true"
    >{{ badge }}</span>
  </button>
</template>
