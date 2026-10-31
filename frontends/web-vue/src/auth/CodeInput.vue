<script setup lang="ts">
const value = defineModel<string>({ required: true });
defineProps<{ disabled?: boolean; label: string }>();
function update(event: Event) {
  const input = event.target as HTMLInputElement;
  value.value = input.value.replace(/[^0-9]/g, '').slice(0, 6);
  input.value = value.value;
}
</script>
<template>
  <label class="web-field">
    <span>{{ label }}</span>
    <span class="web-code-control">
      <input
        :value="value"
        :disabled="disabled"
        :aria-label="label"
        inputmode="numeric"
        autocomplete="one-time-code"
        pattern="[0-9]{6}"
        maxlength="6"
        required
        @input="update"
      >
      <span
        v-for="position in 6"
        :key="position"
        class="web-code-cell"
        aria-hidden="true"
      >{{ value[position - 1] ?? '' }}</span>
    </span>
  </label>
</template>
