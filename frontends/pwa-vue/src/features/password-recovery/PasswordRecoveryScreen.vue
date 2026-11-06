<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AuthIllustration from '../../../../mobile-core/src/features/auth/AuthIllustration.vue';
import AuthLayout from '../../../../mobile-core/src/features/auth/AuthLayout.vue';
import { createPasswordRecoveryClient, PasswordRecoveryApiError } from './password-recovery-client';
import type { PasswordResetProof } from './password-recovery-link';
import { createPasswordRecoveryOperationGate } from './password-recovery-operation';
import './password-recovery-screen.pcss';

const props = withDefaults(
  defineProps<{
    initialProof?: PasswordResetProof | null;
    initialProofError?: string | null;
    fetcher?: typeof fetch;
  }>(),
  {
    initialProof: null,
    initialProofError: null,
    fetcher: globalThis.fetch,
  },
);

const emit = defineEmits<{
  cancel: [];
  completed: [];
  proofConsumed: [];
}>();

type RecoveryStep = 'request' | 'code' | 'password';

const client = createPasswordRecoveryClient(props.fetcher);
const operations = createPasswordRecoveryOperationGate();
const step = ref<RecoveryStep>('request');
const login = ref('');
const challengeId = ref<string | null>(null);
const code = ref('');
const challengeTtlSeconds = ref<number | null>(null);
const resetTicket = ref<string | null>(null);
const ticketTtlSeconds = ref<number | null>(null);
const adminLink = ref(false);
const attemptsRemaining = ref<number | null>(null);
const newPassword = ref('');
const passwordConfirmation = ref('');
const busy = ref(false);
const feedback = ref<string | null>(null);
const notice = ref<string | null>(null);
const heading = ref<HTMLHeadingElement | null>(null);

const stepContent = computed(() => {
  if (step.value === 'request') {
    return {
      title: 'Восстановить пароль',
      description: 'Введи логин. Если для аккаунта доступен Telegram, код придёт в бот.',
      action: 'Запросить код',
      pending: 'Запрашиваем…',
    };
  }
  if (step.value === 'code') {
    return {
      title: 'Введи код',
      description:
        challengeTtlSeconds.value === null
          ? 'Введи код из Telegram.'
          : 'Код из Telegram действует ' + formatDuration(challengeTtlSeconds.value) + '.',
      action: 'Проверить код',
      pending: 'Проверяем…',
    };
  }
  return {
    title: 'Задай новый пароль',
    description:
      ticketTtlSeconds.value === null
        ? 'Введи и подтверди новый пароль.'
        : 'Подтверждение действует ' + formatDuration(ticketTtlSeconds.value) + '.',
    action: 'Сохранить пароль',
    pending: 'Сохраняем…',
  };
});
const canSubmit = computed(() => {
  if (busy.value) return false;
  if (step.value === 'request') return Boolean(login.value.trim());
  if (step.value === 'code') return Boolean(code.value.trim());
  return Boolean(newPassword.value && newPassword.value === passwordConfirmation.value);
});
const passwordMismatch = computed(
  () => Boolean(passwordConfirmation.value) && newPassword.value !== passwordConfirmation.value,
);
const feedbackId = computed(() => (feedback.value ? 'password-recovery-error' : undefined));

const submitStep = {
  request: requestCode,
  code: verifyCode,
  password: completeReset,
};

watch(step, () => heading.value?.focus(), { flush: 'post' });

function beginOperation() {
  busy.value = true;
  return operations.begin();
}

function clearProof(): void {
  challengeId.value = null;
  code.value = '';
  challengeTtlSeconds.value = null;
  resetTicket.value = null;
  ticketTtlSeconds.value = null;
  attemptsRemaining.value = null;
  newPassword.value = '';
  passwordConfirmation.value = '';
}

function clearAllState(): void {
  clearProof();
  login.value = '';
  feedback.value = null;
  notice.value = null;
  busy.value = false;
}

function cancel(): void {
  operations.invalidate();
  clearAllState();
  emit('cancel');
}

async function requestCode(): Promise<void> {
  if (!login.value.trim() || busy.value) return;
  adminLink.value = false;
  feedback.value = null;
  notice.value = null;
  const operation = beginOperation();
  try {
    const result = await client.request(login.value, operation.signal);
    if (!operation.isCurrent()) return;
    challengeId.value = result.challengeId;
    challengeTtlSeconds.value = result.ttlSeconds;
    resetTicket.value = null;
    ticketTtlSeconds.value = null;
    attemptsRemaining.value = null;
    code.value = '';
    newPassword.value = '';
    passwordConfirmation.value = '';
    step.value = 'code';
    notice.value = 'Если для аккаунта доступен Telegram, код отправлен. Введи его в приложении.';
  } catch (error) {
    if (!operation.isCurrent()) return;
    feedback.value = requestErrorMessage(error);
  } finally {
    if (operation.isCurrent()) busy.value = false;
  }
}

async function verifyCode(): Promise<void> {
  if (!challengeId.value || !code.value.trim() || busy.value) return;
  await verifyProof(challengeId.value, code.value, false);
}

async function verifyProof(id: string, proofCode: string, fromBotLink: boolean): Promise<void> {
  feedback.value = null;
  notice.value = fromBotLink ? 'Проверяем ссылку восстановления…' : null;
  const operation = beginOperation();
  try {
    const result = await client.verify({ challengeId: id, code: proofCode }, operation.signal);
    if (!operation.isCurrent()) return;
    resetTicket.value = result.resetTicket;
    ticketTtlSeconds.value = result.expiresInSeconds;
    attemptsRemaining.value = result.attemptsRemaining;
    code.value = '';
    feedback.value = null;
    notice.value = null;
    step.value = 'password';
  } catch (error) {
    if (!operation.isCurrent()) return;
    handleVerifyError(error, fromBotLink);
  } finally {
    if (operation.isCurrent()) busy.value = false;
  }
}

async function completeReset(): Promise<void> {
  if (!resetTicket.value || busy.value) return;
  if (newPassword.value !== passwordConfirmation.value) {
    feedback.value = 'Пароли не совпадают.';
    return;
  }

  feedback.value = null;
  const operation = beginOperation();
  try {
    await client.complete(
      { resetTicket: resetTicket.value, newPassword: newPassword.value },
      operation.signal,
    );
    if (!operation.isCurrent()) return;
    operations.invalidate();
    clearAllState();
    emit('completed');
  } catch (error) {
    if (!operation.isCurrent()) return;
    handleCompleteError(error);
  } finally {
    if (operation.isCurrent()) busy.value = false;
  }
}

function handleVerifyError(error: unknown, fromBotLink: boolean): void {
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_EXPIRED') {
    clearProof();
    step.value = 'request';
    feedback.value = 'Срок действия кода истёк. Запроси новый код.';
    notice.value = null;
    return;
  }
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_INVALID') {
    const remaining = error.problem.attemptsRemaining;
    if (fromBotLink || remaining === 0) {
      clearProof();
      step.value = 'request';
      feedback.value = fromBotLink
        ? 'Ссылка недействительна. Запроси новый код.'
        : 'Попытки закончились. Запроси новый код.';
      notice.value = null;
      return;
    }
    attemptsRemaining.value = remaining;
    feedback.value =
      remaining === null
        ? 'Код не подошёл. Проверь его и попробуй ещё раз.'
        : 'Код не подошёл. Осталось попыток: ' + remaining + '.';
    notice.value = null;
    return;
  }
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_RATE_LIMITED') {
    const wait = error.problem.retryAfterSeconds;
    if (fromBotLink) {
      clearProof();
      step.value = 'request';
    }
    feedback.value =
      wait === null || wait === 0
        ? 'Слишком много попыток. Попробуй позже.'
        : 'Подожди ' + formatDuration(wait) + ' и попробуй снова.';
    notice.value = null;
    return;
  }
  feedback.value = 'Не удалось проверить код. Попробуй ещё раз.';
  notice.value = null;
}

function handleCompleteError(error: unknown): void {
  if (error instanceof PasswordRecoveryApiError && error.code === 'PASSWORD_POLICY_VIOLATION') {
    feedback.value = safePasswordPolicyMessage(error);
    return;
  }
  if (error instanceof PasswordRecoveryApiError && error.code === 'RESET_TICKET_INVALID') {
    clearProof();
    step.value = 'request';
    feedback.value = adminLink.value
      ? 'Ссылка недействительна или уже использована. Попроси администратора создать новую.'
      : 'Срок подтверждения истёк. Запроси новый код.';
    return;
  }
  feedback.value = 'Не удалось сохранить пароль. Попробуй ещё раз.';
}

function requestErrorMessage(error: unknown): string {
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_RATE_LIMITED') {
    const wait = error.problem.retryAfterSeconds;
    return wait === null || wait === 0
      ? 'Слишком много запросов. Попробуй позже.'
      : 'Подожди ' + formatDuration(wait) + ' перед новым запросом.';
  }
  return 'Не удалось запросить код. Попробуй ещё раз.';
}

function safePasswordPolicyMessage(error: PasswordRecoveryApiError): string {
  let detail = error.problem.detail
    ?.split('')
    .map((character) =>
      character.charCodeAt(0) < 32 || character.charCodeAt(0) === 127 ? ' ' : character,
    )
    .join('')
    .replace(/\s+/g, ' ')
    .trim();
  const secrets = [newPassword.value, passwordConfirmation.value, resetTicket.value].filter(
    (secret): secret is string => secret !== null && secret.length > 0,
  );
  if (detail) {
    for (const secret of secrets) detail = detail.split(secret).join('[скрыто]');
  }
  if (detail && detail.length <= 240) return detail;
  return 'Пароль не соответствует требованиям. Проверь пароль и попробуй ещё раз.';
}

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  if (minutes === 0) return seconds + ' сек';
  if (remainder === 0) return minutes + ' мин';
  return minutes + ' мин ' + remainder + ' сек';
}

onMounted(() => {
  if (props.initialProofError) {
    feedback.value = props.initialProofError;
    return;
  }
  const proof = props.initialProof;
  if (!proof) {
    return;
  }

  if ('resetTicket' in proof) {
    adminLink.value = true;
    const ttl = Math.ceil((Date.parse(proof.expiresAt) - Date.now()) / 1000);
    if (ttl <= 0) {
      feedback.value = 'Срок ссылки истёк. Попроси администратора создать новую.';
    } else {
      resetTicket.value = proof.resetTicket;
      ticketTtlSeconds.value = ttl;
      step.value = 'password';
    }
    emit('proofConsumed');
    return;
  }

  challengeId.value = proof.challengeId;
  step.value = 'code';
  const pendingVerification = verifyProof(proof.challengeId, proof.code, true);
  emit('proofConsumed');
  void pendingVerification;
});

onBeforeUnmount(() => {
  operations.invalidate();
  clearAllState();
});
</script>

<template>
  <AuthLayout
    class="password-recovery-screen"
    aria-labelledby="password-recovery-title"
  >
    <section class="auth-content recovery-content">
      <AuthIllustration :state="busy ? 'pending' : feedback ? 'error' : 'idle'" />
      <div class="auth-heading">
        <h1
          id="password-recovery-title"
          ref="heading"
          class="auth-title"
          tabindex="-1"
        >
          {{ stepContent.title }}
        </h1>
        <p>{{ stepContent.description }}</p>
      </div>

      <form
        class="auth-form"
        :aria-busy="busy"
        @submit.prevent="submitStep[step]()"
      >
        <label
          v-if="step === 'request'"
          class="field"
          for="password-recovery-login"
        >
          <span class="field-label">Логин</span>
          <input
            id="password-recovery-login"
            v-model="login"
            name="login"
            type="text"
            autocomplete="username"
            autocapitalize="off"
            :spellcheck="false"
            placeholder="Введи логин"
            required
            :disabled="busy"
            :aria-describedby="feedbackId"
          >
        </label>
        <template v-else-if="step === 'code'">
          <p
            v-if="notice"
            :class="busy ? 'helper recovery-meta' : 'feedback notice'"
            role="status"
          >
            {{ notice }}
          </p>
          <label
            class="field"
            for="password-recovery-code"
          >
            <span class="field-label">Код из Telegram</span>
            <input
              id="password-recovery-code"
              v-model="code"
              name="code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              required
              :disabled="busy"
              :aria-describedby="feedbackId"
            >
          </label>
          <p
            v-if="attemptsRemaining !== null && !feedback"
            class="helper recovery-meta"
            role="status"
          >
            Осталось попыток: {{ attemptsRemaining }}.
          </p>
        </template>
        <template v-else>
          <label
            class="field"
            for="password-recovery-new-password"
          >
            <span class="field-label">Новый пароль</span>
            <input
              id="password-recovery-new-password"
              v-model="newPassword"
              name="newPassword"
              type="password"
              autocomplete="new-password"
              required
              :disabled="busy"
              :aria-describedby="feedbackId"
            >
          </label>
          <label
            class="field"
            for="password-recovery-password-confirmation"
          >
            <span class="field-label">Повтори новый пароль</span>
            <input
              id="password-recovery-password-confirmation"
              v-model="passwordConfirmation"
              name="passwordConfirmation"
              type="password"
              autocomplete="new-password"
              required
              :disabled="busy"
              :aria-invalid="passwordMismatch"
              :aria-describedby="passwordMismatch ? 'password-recovery-mismatch' : feedbackId"
            >
          </label>
          <p
            v-if="passwordMismatch"
            id="password-recovery-mismatch"
            class="helper recovery-meta"
            role="status"
          >
            Пароли не совпадают.
          </p>
        </template>
        <p
          v-if="feedback"
          id="password-recovery-error"
          class="feedback"
          role="alert"
          aria-live="polite"
        >
          <span aria-hidden="true">!</span>
          {{ feedback }}
        </p>
        <button
          class="primary"
          type="submit"
          :disabled="!canSubmit"
          :aria-busy="busy"
        >
          <span
            v-if="busy"
            class="spinner"
            aria-hidden="true"
          />
          {{ busy ? stepContent.pending : stepContent.action }}
          <span
            v-if="!busy"
            aria-hidden="true"
          >→</span>
        </button>
      </form>

      <div class="recovery-actions">
        <button
          v-if="step === 'code' && login.trim()"
          class="recovery-quiet-button"
          type="button"
          :disabled="busy"
          @click="requestCode"
        >
          Запросить новый код
        </button>
        <button
          class="recovery-quiet-button"
          type="button"
          @click="cancel"
        >
          Вернуться ко входу
        </button>
      </div>
      <p
        v-if="step === 'request'"
        class="helper recovery-help"
      >
        Если не можешь получить код: студенту — к старосте группы; старосте и преподавателю — к
        администратору; администратору — к оператору.
      </p>
    </section>
  </AuthLayout>
</template>
