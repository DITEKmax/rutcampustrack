<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import type { ProfileRequestError } from '@rct/mobile-core';
import AuthLayout from '../../mobile-core/src/features/auth/AuthLayout.vue';
import AuthIllustration from '../../mobile-core/src/features/auth/AuthIllustration.vue';
const props = withDefaults(
  defineProps<{
    loading?: boolean;
    success?: boolean;
    error?: ProfileRequestError | null;
    notice?: string | null;
  }>(),
  { loading: false, success: false, error: null, notice: null },
);
const emit = defineEmits<{
  submit: [input: { login: string; password: string }];
  openRecovery: [];
  submitOtp: [input: { code: string }];
}>();
const method = ref<'password' | 'otp'>('password');
const login = ref('');
const password = ref('');
const showPassword = ref(false);
const digits = ref<string[]>(Array(6).fill(''));
const code = computed(() => digits.value.join(''));
const canSubmit = computed(
  () =>
    !props.loading &&
    !props.success &&
    (method.value === 'otp'
      ? /^[0-9]{6}$/u.test(code.value)
      : login.value.trim().length > 0 && password.value.length > 0),
);
function selectMethod(next: 'password' | 'otp'): void {
  if (props.loading || props.success) return;
  digits.value = Array(6).fill('');
  method.value = next;
}
function tabKey(event: KeyboardEvent): void {
  if (
    !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key) ||
    props.loading ||
    props.success
  )
    return;
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
function focusDigit(index: number): void {
  const cell = document.getElementById(`login-otp-${index}`);
  if (cell instanceof HTMLInputElement) {
    cell.focus();
    cell.select();
  }
}
function distribute(index: number, text: string): void {
  if (props.loading || props.success) return;
  const numbers = text.replace(/[^0-9]/gu, '').slice(0, 6);
  if (!numbers) return;
  const start = numbers.length === 6 ? 0 : index;
  for (let n = 0; n < numbers.length && start + n < 6; n += 1)
    digits.value[start + n] = numbers[n] ?? '';
  focusDigit(Math.min(start + numbers.length, 5));
}
function digitInput(event: Event, index: number): void {
  const input = event.target;
  if (!(input instanceof HTMLInputElement)) return;
  const value = input.value;
  if (!value) digits.value[index] = '';
  else distribute(index, value);
  input.value = digits.value[index] ?? '';
}
function digitPaste(event: ClipboardEvent, index: number): void {
  event.preventDefault();
  distribute(index, event.clipboardData?.getData('text') ?? '');
}
function digitKey(event: KeyboardEvent, index: number): void {
  if (props.loading || props.success) return;
  if (event.key === 'Backspace' && !digits.value[index] && index > 0) {
    event.preventDefault();
    digits.value[index - 1] = '';
    focusDigit(index - 1);
  }
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault();
    focusDigit(Math.max(0, Math.min(5, index + (event.key === 'ArrowLeft' ? -1 : 1))));
  }
  if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault();
    focusDigit(event.key === 'Home' ? 0 : 5);
  }
}
function submit(): void {
  if (!canSubmit.value) return;
  if (method.value === 'otp') {
    const input = { code: code.value };
    digits.value = Array(6).fill('');
    emit('submitOtp', input);
  } else emit('submit', { login: login.value, password: password.value });
}
onBeforeUnmount(() => {
  digits.value = Array(6).fill('');
  password.value = '';
});
</script>
<template>
  <AuthLayout>
    <section
      class="auth-content login-content"
      aria-labelledby="login-title"
    >
      <AuthIllustration
        :state="success ? 'success' : loading ? 'pending' : error ? 'error' : 'idle'"
      />
      <div class="auth-heading"
        ><h1
          id="login-title"
          class="auth-title"
          >Войти в приложение</h1
        ></div
      >
      <div
        class="method-tabs"
        role="tablist"
        aria-label="Способ входа"
        @keydown="tabKey"
      >
        <button
          v-for="option in ['password', 'otp'] as const"
          :id="`login-${option}-tab`"
          :key="option"
          type="button"
          role="tab"
          :aria-controls="`login-${option}-panel`"
          :aria-selected="method === option"
          :tabindex="method === option ? 0 : -1"
          :disabled="loading || success"
          @click="selectMethod(option)"
          >{{ option === 'password' ? 'Пароль' : 'Код из Telegram' }}</button
        >
      </div>
      <form
        class="auth-form"
        :aria-busy="loading"
        @submit.prevent="submit"
      >
        <div
          v-if="method === 'password'"
          id="login-password-panel"
          class="auth-form"
          role="tabpanel"
          aria-labelledby="login-password-tab"
        >
          <label
            class="field"
            for="login-username"
            ><span class="field-label">Логин</span
            ><input
              id="login-username"
              v-model="login"
              name="login"
              type="text"
              autocomplete="username"
              placeholder="Введи логин"
              required
              :disabled="loading || success"
              :aria-invalid="!!error"
              :aria-describedby="error ? 'login-error' : undefined"
          /></label>
          <label
            class="field"
            for="login-password"
            ><span class="field-label">Пароль</span
            ><span class="input-wrap"
              ><input
                id="login-password"
                v-model="password"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                data-password
                autocomplete="current-password"
                placeholder="Введи пароль"
                required
                :disabled="loading || success"
                :aria-invalid="!!error"
                :aria-describedby="error ? 'login-error' : undefined" /><button
                class="icon-button"
                type="button"
                :aria-label="showPassword ? 'Скрыть пароль' : 'Показать пароль'"
                :aria-pressed="showPassword"
                :disabled="loading || success"
                @click="showPassword = !showPassword"
                ><svg
                  viewBox="0 0 24 24"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"
                    stroke="currentColor"
                    stroke-width="1.7"
                  />
                  <circle
                    cx="12"
                    cy="12"
                    r="3"
                    stroke="currentColor"
                    stroke-width="1.7"
                  />
                  <path
                    v-if="showPassword"
                    d="m3 3 18 18"
                    stroke="currentColor"
                    stroke-width="1.7"
                  /></svg></button></span
          ></label>
        </div>
        <div
          v-else
          id="login-otp-panel"
          role="tabpanel"
          aria-labelledby="login-otp-tab"
        >
          <fieldset class="otp-field"
            ><legend class="sr-only">Код для входа, 6 цифр</legend
            ><div class="otp-cells"
              ><label
                v-for="(_, index) in digits"
                :key="index"
                :for="`login-otp-${index}`"
                ><span class="sr-only">Цифра {{ index + 1 }} из 6</span
                ><input
                  :id="`login-otp-${index}`"
                  class="otp-cell"
                  :value="digits[index]"
                  type="text"
                  inputmode="numeric"
                  :autocomplete="index === 0 ? 'one-time-code' : 'off'"
                  autocapitalize="off"
                  :spellcheck="false"
                  :maxlength="6"
                  :disabled="loading || success"
                  :aria-invalid="!!error"
                  :aria-describedby="error ? 'login-error' : undefined"
                  @input="digitInput($event, index)"
                  @paste="digitPaste($event, index)"
                  @keydown="digitKey($event, index)" /></label></div
          ></fieldset>
        </div>
        <p
          v-if="error"
          id="login-error"
          class="feedback"
          role="alert"
          ><span aria-hidden="true">!</span>{{ error.message }}</p
        >
        <p
          v-if="notice"
          class="feedback notice"
          role="status"
          >{{ notice }}</p
        >
        <button
          class="primary"
          type="submit"
          :disabled="!canSubmit"
          :aria-busy="loading"
          ><span
            v-if="loading"
            class="spinner"
            aria-hidden="true"
          />{{ loading ? 'Проверяем…' : 'Войти'
          }}<span
            v-if="!loading"
            aria-hidden="true"
            >→</span
          ></button
        >
      </form>
      <p
        v-if="success"
        class="sr-only"
        role="status"
        >Вход выполнен.</p
      >
    </section>
  </AuthLayout>
</template>
