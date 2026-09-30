<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { createPasswordRecoveryClient, PasswordRecoveryApiError } from './password-recovery-client'
import type { PasswordResetProof } from './password-recovery-link'
import { createPasswordRecoveryOperationGate } from './password-recovery-operation'
import '../../login-screen.pcss'

const props = withDefaults(defineProps<{
  initialProof?: PasswordResetProof | null
  initialProofError?: string | null
  fetcher?: typeof fetch
}>(), {
  initialProof: null,
  initialProofError: null,
})

const emit = defineEmits<{
  cancel: []
  completed: []
  proofConsumed: []
}>()

type RecoveryStep = 'request' | 'code' | 'password'

const client = createPasswordRecoveryClient(props.fetcher)
const operations = createPasswordRecoveryOperationGate()
const step = ref<RecoveryStep>('request')
const login = ref('')
const challengeId = ref<string | null>(null)
const code = ref('')
const challengeTtlSeconds = ref<number | null>(null)
const resetTicket = ref<string | null>(null)
const ticketTtlSeconds = ref<number | null>(null)
const attemptsRemaining = ref<number | null>(null)
const newPassword = ref('')
const passwordConfirmation = ref('')
const busy = ref(false)
const feedback = ref<string | null>(null)
const notice = ref<string | null>(null)

function beginOperation() {
  busy.value = true
  return operations.begin()
}

function clearProof(): void {
  challengeId.value = null
  code.value = ''
  challengeTtlSeconds.value = null
  resetTicket.value = null
  ticketTtlSeconds.value = null
  attemptsRemaining.value = null
  newPassword.value = ''
  passwordConfirmation.value = ''
}

function clearAllState(): void {
  clearProof()
  login.value = ''
  feedback.value = null
  notice.value = null
  busy.value = false
}

function cancel(): void {
  operations.invalidate()
  clearAllState()
  emit('cancel')
}

async function requestCode(_event?: SubmitEvent): Promise<void> {
  if (!login.value.trim() || busy.value) return
  feedback.value = null
  notice.value = null
  const operation = beginOperation()
  try {
    const result = await client.request(login.value, operation.signal)
    if (!operation.isCurrent()) return
    challengeId.value = result.challengeId
    challengeTtlSeconds.value = result.ttlSeconds
    resetTicket.value = null
    ticketTtlSeconds.value = null
    attemptsRemaining.value = null
    code.value = ''
    newPassword.value = ''
    passwordConfirmation.value = ''
    step.value = 'code'
    notice.value = 'Если для аккаунта доступен Telegram, код отправлен. Введи его в приложении.'
  } catch (error) {
    if (!operation.isCurrent()) return
    feedback.value = requestErrorMessage(error)
  } finally {
    if (operation.isCurrent()) busy.value = false
  }
}

async function verifyCode(_event?: SubmitEvent): Promise<void> {
  if (!challengeId.value || !code.value.trim() || busy.value) return
  await verifyProof(challengeId.value, code.value, false)
}

async function verifyProof(id: string, proofCode: string, fromBotLink: boolean): Promise<void> {
  feedback.value = null
  notice.value = fromBotLink ? 'Проверяем ссылку восстановления…' : null
  const operation = beginOperation()
  try {
    const result = await client.verify({ challengeId: id, code: proofCode }, operation.signal)
    if (!operation.isCurrent()) return
    resetTicket.value = result.resetTicket
    ticketTtlSeconds.value = result.expiresInSeconds
    attemptsRemaining.value = result.attemptsRemaining
    code.value = ''
    feedback.value = null
    notice.value = null
    step.value = 'password'
  } catch (error) {
    if (!operation.isCurrent()) return
    handleVerifyError(error, fromBotLink)
  } finally {
    if (operation.isCurrent()) busy.value = false
  }
}

async function completeReset(_event?: SubmitEvent): Promise<void> {
  if (!resetTicket.value || busy.value) return
  if (newPassword.value !== passwordConfirmation.value) {
    feedback.value = 'Пароли не совпадают.'
    return
  }

  feedback.value = null
  const operation = beginOperation()
  try {
    await client.complete({ resetTicket: resetTicket.value, newPassword: newPassword.value }, operation.signal)
    if (!operation.isCurrent()) return
    operations.invalidate()
    clearAllState()
    emit('completed')
  } catch (error) {
    if (!operation.isCurrent()) return
    handleCompleteError(error)
  } finally {
    if (operation.isCurrent()) busy.value = false
  }
}

function handleVerifyError(error: unknown, fromBotLink: boolean): void {
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_EXPIRED') {
    clearProof()
    step.value = 'request'
    feedback.value = 'Срок действия кода истёк. Запроси новый код.'
    notice.value = null
    return
  }
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_INVALID') {
    const remaining = error.problem.attemptsRemaining
    if (fromBotLink || remaining === 0) {
      clearProof()
      step.value = 'request'
      feedback.value = fromBotLink
        ? 'Ссылка недействительна. Запроси новый код.'
        : 'Попытки закончились. Запроси новый код.'
      notice.value = null
      return
    }
    attemptsRemaining.value = remaining
    feedback.value = remaining === null
      ? 'Код не подошёл. Проверь его и попробуй ещё раз.'
      : 'Код не подошёл. Осталось попыток: ' + remaining + '.'
    notice.value = null
    return
  }
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_RATE_LIMITED') {
    const wait = error.problem.retryAfterSeconds
    if (fromBotLink) {
      clearProof()
      step.value = 'request'
    }
    feedback.value = wait === null || wait === 0
      ? 'Слишком много попыток. Попробуй позже.'
      : 'Подожди ' + formatDuration(wait) + ' и попробуй снова.'
    notice.value = null
    return
  }
  feedback.value = 'Не удалось проверить код. Попробуй ещё раз.'
  notice.value = null
}

function handleCompleteError(error: unknown): void {
  if (error instanceof PasswordRecoveryApiError && error.code === 'PASSWORD_POLICY_VIOLATION') {
    feedback.value = safePasswordPolicyMessage(error)
    return
  }
  if (error instanceof PasswordRecoveryApiError && error.code === 'RESET_TICKET_INVALID') {
    clearProof()
    step.value = 'request'
    feedback.value = 'Срок подтверждения истёк. Запроси новый код.'
    return
  }
  feedback.value = 'Не удалось сохранить пароль. Попробуй ещё раз.'
}

function requestErrorMessage(error: unknown): string {
  if (error instanceof PasswordRecoveryApiError && error.code === 'OTP_RATE_LIMITED') {
    const wait = error.problem.retryAfterSeconds
    return wait === null || wait === 0
      ? 'Слишком много запросов. Попробуй позже.'
      : 'Подожди ' + formatDuration(wait) + ' перед новым запросом.'
  }
  return 'Не удалось запросить код. Попробуй ещё раз.'
}

function safePasswordPolicyMessage(error: PasswordRecoveryApiError): string {
  let detail = error.problem.detail
    ?.replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const secrets = [newPassword.value, passwordConfirmation.value, resetTicket.value]
    .filter((secret): secret is string => secret !== null && secret.length > 0)
  if (detail) {
    for (const secret of secrets) detail = detail.split(secret).join('[скрыто]')
  }
  if (detail && detail.length <= 240) return detail
  return 'Пароль не соответствует требованиям. Проверь пароль и попробуй ещё раз.'
}

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  if (minutes === 0) return seconds + ' сек'
  if (remainder === 0) return minutes + ' мин'
  return minutes + ' мин ' + remainder + ' сек'
}

onMounted(() => {
  if (props.initialProofError) {
    feedback.value = props.initialProofError
    return
  }
  const proof = props.initialProof
  if (!proof) {
    return
  }

  challengeId.value = proof.challengeId
  step.value = 'code'
  const pendingVerification = verifyProof(proof.challengeId, proof.code, true)
  emit('proofConsumed')
  void pendingVerification
})

onBeforeUnmount(() => {
  operations.invalidate()
  clearAllState()
})
</script>

<template>
  <main
    class="login-screen password-recovery-screen"
    aria-labelledby="password-recovery-title"
  >
    <form
      v-if="step === 'request'"
      class="login-card"
      @submit.prevent="requestCode"
    >
      <p class="login-card__eyebrow">
        RutCampusTrack
      </p>
      <h1 id="password-recovery-title">
        Восстановить пароль
      </h1>
      <p class="login-card__hint">
        Введи логин. Если для аккаунта доступен Telegram, код придёт в бот.
      </p>
      <p class="login-card__hint">
        Если не можешь получить код: студенту — к старосте группы; старосте и преподавателю — к администратору; администратору — к оператору.
      </p>
      <label class="login-field">
        <span>Логин</span>
        <input
          v-model="login"
          name="login"
          type="text"
          autocomplete="username"
          required
          :disabled="busy"
        >
      </label>
      <p
        v-if="feedback"
        class="login-card__error"
        role="alert"
        aria-live="polite"
      >
        {{ feedback }}
      </p>
      <button
        class="login-card__submit"
        type="submit"
        :disabled="busy"
        :aria-busy="busy"
      >
        {{ busy ? 'Запрашиваем…' : 'Запросить код' }}
      </button>
      <button
        class="login-card__secondary"
        type="button"
        @click="cancel"
      >
        Вернуться ко входу
      </button>
    </form>

    <form
      v-else-if="step === 'code'"
      class="login-card"
      @submit.prevent="verifyCode"
    >
      <p class="login-card__eyebrow">
        RutCampusTrack
      </p>
      <h1 id="password-recovery-title">
        Введи код
      </h1>
      <p class="login-card__hint">
        {{ challengeTtlSeconds === null ? 'Введи код из Telegram.' : 'Код из Telegram действует ' + formatDuration(challengeTtlSeconds) + '.' }}
      </p>
      <p
        v-if="notice"
        class="login-card__notice"
        role="status"
        aria-live="polite"
      >
        {{ notice }}
      </p>
      <p
        v-if="attemptsRemaining !== null"
        class="login-card__hint"
        role="status"
        aria-live="polite"
      >
        Осталось попыток: {{ attemptsRemaining }}.
      </p>
      <label class="login-field">
        <span>Код из Telegram</span>
        <input
          v-model="code"
          name="code"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
          required
          :disabled="busy"
        >
      </label>
      <p
        v-if="feedback"
        class="login-card__error"
        role="alert"
        aria-live="polite"
      >
        {{ feedback }}
      </p>
      <button
        class="login-card__submit"
        type="submit"
        :disabled="busy || !code.trim()"
        :aria-busy="busy"
      >
        {{ busy ? 'Проверяем…' : 'Проверить код' }}
      </button>
      <button
        class="login-card__secondary"
        type="button"
        :disabled="busy"
        @click="requestCode()"
      >
        Запросить новый код
      </button>
      <button
        class="login-card__secondary"
        type="button"
        @click="cancel"
      >
        Вернуться ко входу
      </button>
    </form>

    <form
      v-else
      class="login-card"
      @submit.prevent="completeReset"
    >
      <p class="login-card__eyebrow">
        RutCampusTrack
      </p>
      <h1 id="password-recovery-title">
        Задай новый пароль
      </h1>
      <p class="login-card__hint">
        Подтверждение действует {{ ticketTtlSeconds === null ? '' : formatDuration(ticketTtlSeconds) }}.
      </p>
      <label class="login-field">
        <span>Новый пароль</span>
        <input
          v-model="newPassword"
          name="newPassword"
          type="password"
          autocomplete="new-password"
          required
          :disabled="busy"
        >
      </label>
      <label class="login-field">
        <span>Повтори новый пароль</span>
        <input
          v-model="passwordConfirmation"
          name="passwordConfirmation"
          type="password"
          autocomplete="new-password"
          required
          :disabled="busy"
        >
      </label>
      <p
        v-if="feedback"
        class="login-card__error"
        role="alert"
        aria-live="polite"
      >
        {{ feedback }}
      </p>
      <button
        class="login-card__submit"
        type="submit"
        :disabled="busy || newPassword !== passwordConfirmation || !newPassword"
        :aria-busy="busy"
      >
        {{ busy ? 'Сохраняем…' : 'Сохранить пароль' }}
      </button>
      <button
        class="login-card__secondary"
        type="button"
        @click="cancel"
      >
        Вернуться ко входу
      </button>
    </form>
  </main>
</template>
