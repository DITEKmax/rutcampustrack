<script setup lang="ts">
import { computed } from 'vue';
import AuthLayout from './AuthLayout.vue';
import AuthIllustration from './AuthIllustration.vue';
const props = defineProps<{
  error: string | null;
  outside: boolean;
  offline: boolean;
  loading: boolean;
  success?: boolean;
  proofCode?: string | null;
}>();
defineEmits<{ retry: [] }>();
const title = computed(() =>
  props.proofCode === 'AUTHORITY_UNAVAILABLE'
    ? 'Сервис временно недоступен'
    : props.proofCode === 'TMA_TELEGRAM_NOT_LINKED'
    ? 'Привяжи Telegram'
    : props.proofCode === 'TMA_INIT_DATA_EXPIRED'
      ? 'Открой приложение заново'
      : props.proofCode
        ? 'Telegram не подтвердил запуск'
        : props.error
    ? props.outside
      ? 'Открой приложение в Telegram'
      : props.offline
        ? 'Нет подключения'
        : 'Не удалось войти'
    : 'Подключаемся к Telegram',
);
</script>
<template>
  <AuthLayout>
    <section
      class="auth-content tma-content"
      :aria-busy="loading && !success"
    >
      <AuthIllustration :state="success ? 'success' : error ? 'error' : 'pending'" />
      <div
        :role="error ? 'alert' : 'status'"
        aria-live="polite"
        class="auth-heading"
      >
        <h1 class="auth-title">{{ title }}</h1>
        <p>{{ error ?? 'Проверяем твой аккаунт и открываем приложение.' }}</p>
      </div>
      <button
        v-if="error && !offline"
        class="primary"
        type="button"
        :disabled="loading"
        @click="$emit('retry')"
        >Повторить <span aria-hidden="true">→</span></button
      >
      <template v-else-if="!error"
        ><p class="inline-status"
          ><span
            v-if="!success"
            class="spinner"
            aria-hidden="true"
          />{{ success ? 'Вход выполнен' : 'Подтверждаем вход' }}</p
        ><div
          class="tma-steps"
          aria-hidden="true"
          ><i /><i /><i /></div
      ></template>
    </section>
  </AuthLayout>
</template>
