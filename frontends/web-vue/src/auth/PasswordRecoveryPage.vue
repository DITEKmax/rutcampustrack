<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref } from 'vue';
import {
  createPasswordRecoveryClient,
  PasswordRecoveryApiError,
} from '../../../pwa-vue/src/features/password-recovery/password-recovery-client';
import { createPasswordRecoveryOperationGate } from '../../../pwa-vue/src/features/password-recovery/password-recovery-operation';
import { consumePasswordResetFragment } from '../../../pwa-vue/src/features/password-recovery/password-recovery-link';
import {
  DEFAULT_PASSWORD_POLICY,
  validateNewPassword,
} from '../../../mobile-core/src/features/profile/profile-types';
import { webSession } from './session';
import AuthFrame from './AuthFrame.vue';
import CodeInput from './CodeInput.vue';
const client = createPasswordRecoveryClient();
const gate = createPasswordRecoveryOperationGate();
const step = ref<'request' | 'verify' | 'password' | 'complete' | 'unknown'>('request');
const login = ref('');
const code = ref('');
const password = ref('');
const confirmation = ref('');
const visible = ref(false);
const busy = ref(false);
const error = ref('');
const deadline = ref(0);
const now = ref(Date.now());
let challengeId = '';
let resetTicket = '';
const remaining = computed(() => Math.max(0, Math.ceil((deadline.value - now.value) / 1000)));
const validPassword = computed(
  () =>
    validateNewPassword(password.value, DEFAULT_PASSWORD_POLICY).length === 0 &&
    password.value === confirmation.value,
);
const clock = setInterval(() => {
  now.value = Date.now();
}, 1000);
function failure(cause: unknown) {
  if (cause instanceof PasswordRecoveryApiError) {
    if (cause.status === 429)
      return `Слишком много попыток.${cause.problem.retryAfterSeconds ? ' Повтори через ' + cause.problem.retryAfterSeconds + ' с.' : ' Попробуй позже.'}`;
    if (cause.status === 400 || cause.status === 401 || cause.status === 410)
      return 'Код или ссылка недействительны. Проверь данные или запроси новый код.';
    if (cause.status === 409)
      return 'Восстановление уже использовано или состояние изменилось. Вернись ко входу.';
    if (cause.problem.attemptsRemaining !== null)
      return `Код не подошёл. Осталось попыток: ${cause.problem.attemptsRemaining}.`;
  }
  return 'Не удалось связаться с сервером. Попробуй позже.';
}
async function request() {
  if (!login.value.trim() || busy.value) return;
  const operation = gate.begin();
  busy.value = true;
  error.value = '';
  try {
    const value = await client.request(login.value.trim(), operation.signal);
    if (!operation.isCurrent()) return;
    challengeId = value.challengeId;
    deadline.value = Date.now() + value.ttlSeconds * 1000;
    code.value = '';
    step.value = 'verify';
  } catch (cause) {
    if (operation.isCurrent()) error.value = failure(cause);
  } finally {
    if (operation.isCurrent()) busy.value = false;
  }
}
async function verify(fromFragment = false) {
  if (
    !challengeId ||
    !/^[0-9]{6}$/.test(code.value) ||
    busy.value ||
    (remaining.value === 0 && !fromFragment)
  )
    return;
  const operation = gate.begin();
  busy.value = true;
  error.value = '';
  try {
    const value = await client.verify({ challengeId, code: code.value }, operation.signal);
    if (!operation.isCurrent()) return;
    resetTicket = value.resetTicket;
    code.value = '';
    challengeId = '';
    deadline.value = Date.now() + value.expiresInSeconds * 1000;
    step.value = 'password';
  } catch (cause) {
    if (operation.isCurrent()) error.value = failure(cause);
  } finally {
    if (operation.isCurrent()) busy.value = false;
  }
}
async function complete() {
  if (!resetTicket || !validPassword.value || busy.value || remaining.value === 0) return;
  const operation = gate.begin();
  busy.value = true;
  error.value = '';
  try {
    await client.complete({ resetTicket, newPassword: password.value }, operation.signal);
    if (operation.isCurrent()) {
      resetTicket = '';
      step.value = 'complete';
      if (webSession.snapshot.value) webSession.invalidate();
    }
  } catch (cause) {
    if (!operation.isCurrent()) return;
    if (!(cause instanceof PasswordRecoveryApiError) || cause.status === 0 || cause.status >= 500) {
      resetTicket = '';
      step.value = 'unknown';
    } else error.value = failure(cause);
  } finally {
    password.value = '';
    confirmation.value = '';
    if (operation.isCurrent()) busy.value = false;
  }
}
function restart() {
  gate.invalidate();
  challengeId = '';
  resetTicket = '';
  code.value = '';
  password.value = '';
  confirmation.value = '';
  error.value = '';
  busy.value = false;
  step.value = 'request';
}
onMounted(() => {
  if (!location.hash) return;
  try {
    const proof = consumePasswordResetFragment(location.hash, () =>
      history.replaceState(history.state, '', location.pathname + location.search),
    );
    if (!proof) {
      error.value = 'Некорректная ссылка восстановления.';
      return;
    }
    if ('resetTicket' in proof) {
      resetTicket = proof.resetTicket;
      deadline.value = Date.parse(proof.expiresAt);
      step.value = 'password';
    } else {
      challengeId = proof.challengeId;
      code.value = proof.code;
      deadline.value = 0;
      step.value = 'verify';
      void verify(true);
    }
  } catch {
    error.value = 'Не удалось безопасно открыть ссылку восстановления.';
  }
});
onScopeDispose(() => {
  gate.invalidate();
  clearInterval(clock);
  resetTicket = '';
  challengeId = '';
  password.value = '';
  confirmation.value = '';
  code.value = '';
});
</script>
<template>
  <AuthFrame :screen="step === 'password' ? 'WEB-AUTH-NEW-PASSWORD' : 'WEB-AUTH-RECOVERY'">
    <RouterLink
      class="web-auth-link"
      :to="{ name: 'login' }"
    >
      ← К входу
    </RouterLink>
    <h1>
      {{
        step === 'password'
          ? 'Новый пароль'
          : step === 'verify'
            ? 'Введи код'
            : 'Восстановление пароля'
      }}
    </h1>
    <form
      v-if="step === 'request'"
      class="web-stack"
      :aria-busy="busy"
      @submit.prevent="request"
    >
      <p>Введи логин. Если аккаунт связан с Telegram, бот пришлёт код восстановления.</p>
      <label class="web-field">Логин *<input
        v-model="login"
        autocomplete="username"
        required
        :disabled="busy"
        placeholder="Введи логин"
      ></label>
      <button
        class="web-button web-button-primary"
        :disabled="busy"
      >
        {{ busy ? 'Запрашиваем…' : 'Получить код' }}
      </button>
      <p class="web-caption">
        Если доступа к Telegram нет, обратись к старосте или администратору.
      </p>
    </form>
    <form
      v-if="step === 'verify'"
      class="web-stack"
      :aria-busy="busy"
      @submit.prevent="verify()"
    >
      <p>Введи шестизначный код из Telegram.</p>
      <CodeInput
        v-model="code"
        label="Код восстановления"
        :disabled="busy"
      />
      <p
        class="web-caption"
        role="status"
      >
        {{
          remaining > 0
            ? 'Код действует ещё ' + remaining + ' с.'
            : 'Срок кода истёк. Запроси новый.'
        }}
      </p>
      <button
        class="web-button web-button-primary"
        :disabled="busy || code.length !== 6 || remaining === 0"
      >
        {{ busy ? 'Проверяем…' : 'Продолжить' }}
      </button>
      <button
        class="web-button"
        type="button"
        :disabled="busy"
        @click="restart"
      >
        Начать заново
      </button>
    </form>
    <form
      v-if="step === 'password'"
      class="web-stack"
      :aria-busy="busy"
      @submit.prevent="complete"
    >
      <label class="web-field">Новый пароль *<input
        v-model="password"
        :type="visible ? 'text' : 'password'"
        autocomplete="new-password"
        required
        :disabled="busy"
        aria-describedby="reset-policy"
      ></label>
      <label class="web-field">Повтори пароль *<input
        v-model="confirmation"
        :type="visible ? 'text' : 'password'"
        autocomplete="new-password"
        required
        :disabled="busy"
      ></label>
      <button
        class="web-button"
        type="button"
        :aria-pressed="visible"
        @click="visible = !visible"
      >
        {{ visible ? 'Скрыть пароли' : 'Показать пароли' }}
      </button>
      <p
        id="reset-policy"
        class="web-caption"
      >
        Не менее 12 символов, цифра и специальный символ. Не более 72 байт UTF-8.
      </p>
      <p
        v-if="confirmation && confirmation !== password"
        class="web-error"
      >
        Пароли не совпадают.
      </p>
      <p
        class="web-caption"
        role="status"
      >
        {{
          remaining > 0
            ? 'Ссылка действует ещё ' + remaining + ' с.'
            : 'Ссылка истекла. Запроси восстановление заново.'
        }}
      </p>
      <button
        class="web-button web-button-primary"
        :disabled="busy || !validPassword || remaining === 0"
      >
        {{ busy ? 'Сохраняем…' : 'Установить пароль' }}
      </button>
      <button
        v-if="remaining === 0"
        class="web-button"
        type="button"
        @click="restart"
      >
        Начать заново
      </button>
    </form>
    <p
      v-if="step === 'complete'"
      role="status"
    >
      Пароль изменён. Войди с новым паролем.
    </p>
    <p
      v-if="step === 'unknown'"
      role="alert"
    >
      Результат изменения пароля неизвестен. Попробуй войти с новым паролем. Не отправляй изменение
      повторно; если войти не удалось, начни новое восстановление.
    </p>
    <p
      v-if="error"
      class="web-error"
      role="alert"
    >
      {{ error }}
    </p>
  </AuthFrame>
</template>
