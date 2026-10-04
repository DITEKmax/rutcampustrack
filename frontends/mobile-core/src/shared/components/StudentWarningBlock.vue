<script setup lang="ts">
import MobileIcon from './MobileIcon.vue'
import './student-warning-block.pcss'

withDefaults(defineProps<{
  title: string
  message: string
  actionLabel?: string
  actionDisabled?: boolean
  severity?: 'warning' | 'error'
}>(), { actionLabel: '', actionDisabled: false, severity: 'warning' })
const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <section
    class="student-warning"
    :data-severity="severity"
    :role="severity === 'error' ? 'alert' : 'status'"
  >
    <MobileIcon
      name="warning"
      class="student-warning__icon"
    />
    <div class="student-warning__content">
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
      <button
        v-if="actionLabel"
        type="button"
        :disabled="actionDisabled"
        @click="emit('action')"
      >
        {{ actionLabel }}
      </button>
    </div>
  </section>
</template>
