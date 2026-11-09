<script setup lang="ts">
import { computed } from 'vue';
import { useDeviceAccess } from './device-access';
import './geo-acquisition.pcss';
defineProps<{ active?: boolean }>();
const emit = defineEmits<{ cancel: [] }>();
const owner = useDeviceAccess();
const message = computed(() => {
  const result = owner?.state.review;
  if (!result) return '';
  if (result.conflicting)
    return 'Устройство сообщило противоречивые координаты. Повтори определение или продолжи с полученной точкой.';
  if (result.reason === 'APP_DISABLED')
    return 'Геолокация выключена в приложении. Ты можешь включить её в профиле или продолжить без координат.';
  if (result.reason === 'PERMISSION_DENIED')
    return 'Доступ к геолокации запрещён. Разреши его в настройках устройства или продолжи без координат.';
  if (!result.sample)
    return 'Не удалось определить геолокацию. Попробуй ещё раз или продолжи без координат.';
  return result.sample.accuracy === null
    ? 'Устройство не сообщило точность геолокации. Повтори определение или продолжи с полученной точкой.'
    : `Точность геолокации около ${Math.round(result.sample.accuracy)} м. Повтори определение или продолжи с полученной точкой.`;
});
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
        owner?.state.stage === 'review'
          ? message
          : owner?.state.stage === 'refining'
            ? 'Уточняем геолокацию…'
            : owner?.state.stage === 'determining'
              ? 'Определяем геолокацию…'
              : 'Проверяем доступность отметки…'
      }}</p
    >
    <template v-if="owner?.state.stage === 'review'">
      <button
        class="profile-secondary-button"
        type="button"
        @click="owner.choose('retry')"
        >Определить ещё раз</button
      >
      <button
        class="profile-primary-button"
        type="button"
        @click="owner.choose('continue')"
        >Продолжить</button
      >
      <p class="profile-note__body"
        >Если геолокация не подтвердит присутствие, запрос отправится старосте.</p
      >
    </template>
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
