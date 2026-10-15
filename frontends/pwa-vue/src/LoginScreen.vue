<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import type { ProfileRequestError } from '@rct/mobile-core';
import './login-screen.pcss';

const props = withDefaults(
  defineProps<{
    loading?: boolean;
    error?: ProfileRequestError | null;
    notice?: string | null;
  }>(),
  {
    loading: false,
    error: null,
    notice: null,
  },
);

const emit = defineEmits<{
  submit: [input: { login: string; password: string }];
  openRecovery: [];
  submitOtp: [input: { code: string }];
}>();

const method = ref<'password' | 'otp'>('password');
const code = ref('');
function selectMethod(next: 'password' | 'otp'): void {
  if (props.loading) return;
  code.value = '';
  method.value = next;
}
function tabKey(event: KeyboardEvent): void {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) || props.loading) return;
  event.preventDefault();
  selectMethod(
    event.key === 'Home'
      ? 'password'
      : event.key === 'End'
        ? 'otp'
        : method.value === 'password'
          ? 'otp'
          : 'password',
  );
  document.getElementById(`login-${method.value}-tab`)?.focus();
}
onBeforeUnmount(() => {
  code.value = '';
});
function submit(event: SubmitEvent): void {
  if (props.loading) return;
  if (method.value === 'otp') {
    if (/^[0-9]{6}$/u.test(code.value)) {
      const input = { code: code.value };
      code.value = '';
      emit('submitOtp', input);
    }
    return;
  }
  const form = event.currentTarget;
  if (!(form instanceof HTMLFormElement)) return;
  const data = new FormData(form);
  const login = String(data.get('login') ?? '');
  const password = String(data.get('password') ?? '');
  if (login.length === 0 || password.length === 0) return;
  emit('submit', { login, password });
}
</script>

<template>
  <main
    class="login-screen"
    aria-labelledby="login-title"
  >
    <form
      class="login-card"
      @submit.prevent="submit"
    >
      <p class="login-card__eyebrow">
        RutCampusTrack
      </p>
      <h1 id="login-title">
        Войти в приложение
      </h1>
      <p class="login-card__hint">
        {{ method === 'password' ? 'Введи логин и пароль, чтобы открыть расписание и задания.' : 'Получи код для входа в Telegram и введи его здесь.' }}
      </p>

      <div
        class="login-methods"
        role="tablist"
        aria-label="Способ входа"
        @keydown="tabKey"
      >
        <button
          id="login-password-tab"
          class="login-card__secondary"
          type="button"
          role="tab"
          aria-controls="login-password-panel"
          :aria-selected="method === 'password'"
          :tabindex="method === 'password' ? 0 : -1"
          :disabled="loading"
          @click="selectMethod('password')"
        >
          Пароль
        </button>
        <button
          id="login-otp-tab"
          class="login-card__secondary"
          type="button"
          role="tab"
          aria-controls="login-otp-panel"
          :aria-selected="method === 'otp'"
          :tabindex="method === 'otp' ? 0 : -1"
          :disabled="loading"
          @click="selectMethod('otp')"
        >
          Код из Telegram
        </button>
      </div>
      <div
        v-if="method === 'password'"
        id="login-password-panel"
        role="tabpanel"
        aria-labelledby="login-password-tab"
      >
        <label class="login-field">
          <span>Логин</span>
          <input
            name="login"
            type="text"
            autocomplete="username"
            required
            :disabled="loading"
          >
        </label>
        <label class="login-field">
          <span>Пароль</span>
          <input
            name="password"
            type="password"
            autocomplete="current-password"
            required
            :disabled="loading"
          >
        </label>
      </div>
      <div
        v-else
        id="login-otp-panel"
        role="tabpanel"
        aria-labelledby="login-otp-tab"
      >
        <label class="login-field">
          <span>Код для входа</span>
          <input
            v-model="code"
            name="code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            pattern="[0-9]{6}"
            minlength="6"
            maxlength="6"
            required
            :disabled="loading"
          >
        </label>
      </div>

      <p
        v-if="error"
        class="login-card__error"
        role="alert"
      >
        {{ error.message }}
      </p>
      <p
        v-if="notice"
        class="login-card__notice"
        role="status"
        aria-live="polite"
      >
        {{ notice }}
      </p>
      <button
        class="login-card__submit"
        type="submit"
        :disabled="loading"
        :aria-busy="loading"
      >
        {{ loading ? 'Проверяем…' : 'Войти' }}
      </button>
      <button
        v-if="method === 'password'"
        class="login-card__secondary"
        type="button"
        :disabled="loading"
        @click="emit('openRecovery')"
      >
        Забыл пароль
      </button>
    </form>
  </main>
</template>
