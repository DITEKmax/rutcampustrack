<script setup lang="ts">
import { useId } from 'vue'
import checkIcon from '../../assets/admin/check.svg'
import './admin-checkbox.pcss'

withDefaults(defineProps<{
  modelValue: boolean
  label: string
  disabled?: boolean
  code?: string
}>(), { disabled: false, code: '' })
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const id = useId()
function change(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).checked)
}
</script>

<template>
  <label
    class="admin-checkbox"
    :for="id"
    :data-disabled="disabled"
  >
    <input
      :id="id"
      type="checkbox"
      :checked="modelValue"
      :disabled="disabled"
      :data-permission="code || undefined"
      @change="change"
    >
    <span
      class="admin-checkbox__control"
      aria-hidden="true"
    >
      <img
        v-if="modelValue"
        :src="checkIcon"
        alt=""
      >
    </span>
    <span class="admin-checkbox__label">{{ label }}</span>
  </label>
</template>
