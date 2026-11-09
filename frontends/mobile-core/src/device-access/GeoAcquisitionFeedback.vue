<script setup lang="ts">
import { useDeviceAccess } from './device-access';
import './geo-acquisition.pcss';
defineProps<{ active?: boolean }>();
const emit = defineEmits<{ cancel: [] }>();
const owner = useDeviceAccess();
</script>
<template>
  <section
    v-if="owner?.state.stage || active"
    class="checkin-geo-feedback profile-screen__content"
    aria-label="Определение геолокации"
  >
    <p
      role="status"
      class="profile-note__body"
      >{{
        owner?.state.stage === 'refining'
          ? 'Повторно определяем геолокацию…'
          : owner?.state.stage === 'determining'
            ? 'Определяем геолокацию…'
            : 'Проверяем доступность отметки…'
      }}</p
    >
    <button
      class="profile-secondary-button"
      type="button"
      @click="
        owner?.cancel();
        emit('cancel');
      "
      >Отменить</button
    >
  </section>
</template>
