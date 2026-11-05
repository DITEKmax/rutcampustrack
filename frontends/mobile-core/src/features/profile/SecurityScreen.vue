<script setup lang="ts">
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import type {
  PasswordValidationIssue,
  ProfilePasswordPolicy,
  ProfileRequestError,
  ProfileResolvedTheme,
} from './profile-types';
import {
  DEFAULT_PASSWORD_POLICY,
  ProfileRequestError as ProfileError,
  validateNewPassword,
} from './profile-types';
import securityHide from './assets/security-hide.svg';
import securityPrevious from './assets/security-previous.svg';
import securityShow from './assets/security-show.svg';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import './profile-screen.pcss';

const props = withDefaults(
  defineProps<{
    policy?: ProfilePasswordPolicy;
    error?: ProfileRequestError | null;
    busy?: boolean;
    offline?: boolean;
    disabled?: boolean;
    onBack?: (() => void | Promise<void>) | undefined;
    onRecover?: (() => void | Promise<void>) | undefined;
    recoveryLabel?: string;
    onChangePassword?:
      | ((input: { currentPassword: string; newPassword: string }) => void | Promise<void>)
      | undefined;
    theme?: ProfileResolvedTheme;
    ownerKey?: string | null;
  }>(),
  {
    policy: () => DEFAULT_PASSWORD_POLICY,
    error: null,
    busy: false,
    offline: false,
    disabled: false,
    onBack: undefined,
    onRecover: undefined,
    recoveryLabel: 'Не помню текущий пароль',
    onChangePassword: undefined,
    ownerKey: null,
  },
);

const currentPassword = ref('');
const newPassword = ref('');
const repeatPassword = ref('');
const showCurrent = ref(false);
const showNew = ref(false);
const showRepeat = ref(false);
const submitted = ref(false);
const submitting = ref(false);
const localError = ref<ProfileRequestError | null>(null);
const currentInput = ref<HTMLInputElement | null>(null);
const newInput = ref<HTMLInputElement | null>(null);
const repeatInput = ref<HTMLInputElement | null>(null);

const policyIssues = computed(() => validateNewPassword(newPassword.value, props.policy));
const hasMismatch = computed(
  () => repeatPassword.value.length > 0 && newPassword.value !== repeatPassword.value,
);
const shouldShowPolicy = computed(() => submitted.value || newPassword.value.length > 0);
const requestError = computed(() => props.error ?? localError.value);
const currentPasswordInvalid = computed(
  () => requestError.value?.code === 'CURRENT_PASSWORD_INVALID',
);
const currentPasswordMissing = computed(
  () => submitted.value && currentPassword.value.length === 0,
);
const policyRejectedByServer = computed(
  () => requestError.value?.code === 'PASSWORD_POLICY_VIOLATION',
);
const repeatInvalid = computed(
  () => (submitted.value && repeatPassword.value.length === 0) || hasMismatch.value,
);
const isBusy = computed(() => props.busy || submitting.value);
const invalidatingCodes = new Set([
  'ACCOUNT_INVALIDATED',
  'INVALID_SESSION',
  'SESSION_REVOKED',
  'REFRESH_REJECTED',
]);
const accountInvalidated = computed(() => invalidatingCodes.has(props.error?.code ?? ''));
const canSubmit = computed(
  () =>
    !isBusy.value &&
    !props.offline &&
    !props.disabled &&
    !accountInvalidated.value &&
    Boolean(props.onChangePassword) &&
    currentPassword.value.length > 0 &&
    policyIssues.value.length === 0 &&
    repeatPassword.value.length > 0 &&
    !hasMismatch.value,
);
let formGeneration = 0;
let disposed = false;

function clearSensitiveForm(): void {
  formGeneration += 1;
  submitting.value = false;
  currentPassword.value = '';
  newPassword.value = '';
  repeatPassword.value = '';
  showCurrent.value = false;
  showNew.value = false;
  showRepeat.value = false;
  submitted.value = false;
  localError.value = null;
}

watch(
  () => [props.ownerKey, accountInvalidated.value] as const,
  (value, previous) => {
    if (value[1] || (previous && (previous[1] || value[0] !== previous[0]))) clearSensitiveForm();
  },
  { immediate: true },
);

function focusField(field: 'current' | 'new' | 'repeat'): void {
  void nextTick(() => {
    if (field === 'current') currentInput.value?.focus();
    if (field === 'new') newInput.value?.focus();
    if (field === 'repeat') repeatInput.value?.focus();
  });
}

function toggle(field: 'current' | 'new' | 'repeat'): void {
  if (field === 'current') showCurrent.value = !showCurrent.value;
  if (field === 'new') showNew.value = !showNew.value;
  if (field === 'repeat') showRepeat.value = !showRepeat.value;
}

const passwordRules = computed(() =>
  [
    { key: 'MIN_CODE_POINTS', label: `Не менее ${props.policy.minCodePoints} символов` },
    ...(props.policy.requiresDecimalDigit
      ? [{ key: 'DECIMAL_DIGIT', label: 'Хотя бы одна цифра' }]
      : []),
    ...(props.policy.specialCategories.length > 0
      ? [{ key: 'SPECIAL_CHARACTER', label: 'Хотя бы один специальный знак' }]
      : []),
  ].map((rule) => ({
    ...rule,
    met: !policyIssues.value.includes(rule.key as PasswordValidationIssue),
  })),
);

async function submit(): Promise<void> {
  if (isBusy.value || props.disabled || accountInvalidated.value) return;
  submitted.value = true;
  localError.value = null;
  if (currentPassword.value.length === 0) {
    focusField('current');
    return;
  }
  if (policyIssues.value.length > 0) {
    focusField('new');
    return;
  }
  if (repeatPassword.value.length === 0 || hasMismatch.value) {
    focusField('repeat');
    return;
  }
  if (!canSubmit.value || !props.onChangePassword) return;
  submitting.value = true;
  const requestGeneration = formGeneration;
  try {
    await props.onChangePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    });
    if (!disposed && requestGeneration === formGeneration) clearSensitiveForm();
  } catch (error) {
    if (disposed || requestGeneration !== formGeneration) return;
    localError.value =
      error instanceof ProfileError
        ? error
        : new ProfileError('NETWORK', 'Не удалось изменить пароль');
  } finally {
    if (!disposed && requestGeneration === formGeneration) submitting.value = false;
  }
}

function recover(): void {
  void props.onRecover?.();
}

onUnmounted(() => {
  disposed = true;
  clearSensitiveForm();
});
</script>

<template>
  <main
    class="profile-screen profile-security"
    :data-theme="theme"
    aria-labelledby="profile-security-title"
  >
    <div class="profile-screen__content">
      <header class="profile-header">
        <button
          class="profile-back"
          type="button"
          aria-label="Назад"
          @click="onBack?.()"
        >
          <SvgIcon
            :src="securityPrevious"
            aria-hidden="true"
          />
        </button>
        <h1
          id="profile-security-title"
          class="profile-header__title"
        >
          Безопасность
        </h1>
      </header>

      <StudentWarningBlock
        v-if="offline"
        title="Нет подключения"
        message="Изменение пароля доступно только онлайн."
      />

      <form
        class="profile-form"
        :aria-busy="isBusy"
        novalidate
        @submit.prevent="submit"
      >
        <div class="profile-field">
          <label for="profile-current-password">Текущий пароль *</label>
          <div
            class="profile-field__control"
            :data-invalid="currentPasswordInvalid || currentPasswordMissing"
          >
            <input
              id="profile-current-password"
              ref="currentInput"
              v-model="currentPassword"
              :type="showCurrent ? 'text' : 'password'"
              autocomplete="current-password"
              required
              :readonly="isBusy"
              :disabled="accountInvalidated"
              :aria-invalid="currentPasswordInvalid || currentPasswordMissing"
              :aria-describedby="
                currentPasswordInvalid || currentPasswordMissing
                  ? 'profile-current-password-error'
                  : undefined
              "
            />
            <button
              class="profile-field__toggle"
              type="button"
              :disabled="isBusy || accountInvalidated"
              :aria-pressed="showCurrent"
              :aria-label="showCurrent ? 'Скрыть текущий пароль' : 'Показать текущий пароль'"
              @click="toggle('current')"
            >
              <SvgIcon
                :width="20"
                :height="20"
                :src="showCurrent ? securityHide : securityShow"
                aria-hidden="true"
              />
            </button>
          </div>
          <span
            v-if="currentPasswordInvalid || currentPasswordMissing"
            id="profile-current-password-error"
            class="profile-inline-error"
            role="alert"
          >
            {{ currentPasswordInvalid ? 'Текущий пароль неверен.' : 'Введи текущий пароль.' }}
          </span>
        </div>

        <div class="profile-field">
          <label for="profile-new-password">Новый пароль *</label>
          <div
            class="profile-field__control"
            :data-invalid="(shouldShowPolicy && policyIssues.length > 0) || policyRejectedByServer"
          >
            <input
              id="profile-new-password"
              ref="newInput"
              v-model="newPassword"
              :type="showNew ? 'text' : 'password'"
              autocomplete="new-password"
              required
              :readonly="isBusy"
              :disabled="accountInvalidated"
              :aria-invalid="
                (shouldShowPolicy && policyIssues.length > 0) || policyRejectedByServer
              "
              aria-describedby="profile-password-policy"
            />
            <button
              class="profile-field__toggle"
              type="button"
              :disabled="isBusy || accountInvalidated"
              :aria-pressed="showNew"
              :aria-label="showNew ? 'Скрыть новый пароль' : 'Показать новый пароль'"
              @click="toggle('new')"
            >
              <SvgIcon
                :width="20"
                :height="20"
                :src="showNew ? securityHide : securityShow"
                aria-hidden="true"
              />
            </button>
          </div>
          <div
            id="profile-password-policy"
            class="profile-password-rules"
            role="status"
          >
            <p
              v-for="rule in passwordRules"
              :key="rule.key"
              class="profile-password-policy"
              :data-valid="rule.met"
              :data-invalid="!rule.met"
            >
              <span
                class="profile-password-policy__check"
                role="img"
                :aria-label="rule.met ? 'Выполнено' : 'Не выполнено'"
                >{{ rule.met ? '✓' : '×' }}</span
              >
              <span>{{ rule.label }}</span>
            </p>
            <p
              v-if="shouldShowPolicy && policyIssues.includes('MAX_UTF8_BYTES')"
              class="profile-inline-error"
            >
              Не более {{ policy.maxUtf8Bytes }} байт в UTF-8.
            </p>
            <p
              v-if="shouldShowPolicy && policyIssues.includes('UNPAIRED_SURROGATE')"
              class="profile-inline-error"
            >
              Удали некорректный символ.
            </p>
            <p
              v-if="policyRejectedByServer"
              class="profile-inline-error"
            >
              Сервер отклонил пароль по политике.
            </p>
          </div>
        </div>

        <div class="profile-field">
          <label for="profile-repeat-password">Повтори новый пароль *</label>
          <div
            class="profile-field__control"
            :data-invalid="repeatInvalid"
          >
            <input
              id="profile-repeat-password"
              ref="repeatInput"
              v-model="repeatPassword"
              :type="showRepeat ? 'text' : 'password'"
              autocomplete="new-password"
              required
              :readonly="isBusy"
              :disabled="accountInvalidated"
              :aria-invalid="repeatInvalid"
              :aria-describedby="repeatInvalid ? 'profile-repeat-password-error' : undefined"
            />
            <button
              class="profile-field__toggle"
              type="button"
              :disabled="isBusy || accountInvalidated"
              :aria-pressed="showRepeat"
              :aria-label="
                showRepeat ? 'Скрыть повтор нового пароля' : 'Показать повтор нового пароля'
              "
              @click="toggle('repeat')"
            >
              <SvgIcon
                :width="20"
                :height="20"
                :src="showRepeat ? securityHide : securityShow"
                aria-hidden="true"
              />
            </button>
          </div>
          <span
            v-if="repeatInvalid"
            id="profile-repeat-password-error"
            class="profile-inline-error"
            role="alert"
          >
            {{ repeatPassword.length === 0 ? 'Повтори новый пароль.' : 'Пароли не совпадают.' }}
          </span>
        </div>

        <button
          v-if="onRecover"
          class="profile-secondary-button profile-security__recovery"
          type="button"
          @click="recover"
        >
          {{ recoveryLabel }}
        </button>

        <StudentWarningBlock
          v-if="requestError && !currentPasswordInvalid && !policyRejectedByServer"
          severity="error"
          title="Пароль не изменён"
          :message="requestError.message"
        />

        <div class="profile-form__actions">
          <button
            class="profile-primary-button"
            type="submit"
            :disabled="!canSubmit"
            :aria-busy="isBusy"
          >
            {{ isBusy ? 'Сохраняем…' : 'Сменить пароль' }}
          </button>
        </div>
      </form>

      <StudentWarningBlock
        title="После изменения пароля"
        message="Ты выйдешь со всех устройств, включая текущее."
      />
    </div>
  </main>
</template>
