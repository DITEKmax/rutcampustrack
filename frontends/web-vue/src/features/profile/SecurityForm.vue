<script setup lang="ts">
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import SvgIcon from '../../../../shared/foundation/SvgIcon.vue';
import securityShow from '../../../../mobile-core/src/features/profile/assets/security-show.svg';
import securityHide from '../../../../mobile-core/src/features/profile/assets/security-hide.svg';
import { computed, onScopeDispose, ref, useId } from 'vue';
import {
  validateNewPassword,
  type PasswordValidationIssue,
  type ProfilePasswordPolicy,
} from '../../../../mobile-core/src/features/profile/profile-types';
const props = defineProps<{ policy: ProfilePasswordPolicy; busy: boolean; uncertain: boolean }>();
const emit = defineEmits<{
  change: [value: { currentPassword: string; newPassword: string }];
  dirty: [value: boolean];
}>();
const current = ref('');
const password = ref('');
const confirmation = ref('');
const fieldId = useId();
const currentVisible = ref(false);
const passwordVisible = ref(false);
const confirmationVisible = ref(false);
const confirming = ref(false);
const passwordIssues = computed(() => validateNewPassword(password.value, props.policy));
const requirements = computed(() => {
  const passed = (issue: PasswordValidationIssue) => Boolean(password.value) && !passwordIssues.value.includes(issue);
  const items = [
    { id: 'length', label: `Не менее ${props.policy.minCodePoints} символов`, checked: passed('MIN_CODE_POINTS') },
  ];
  if (props.policy.requiresDecimalDigit)
    items.push({ id: 'digit', label: 'Есть цифра', checked: passed('DECIMAL_DIGIT') });
  items.push(
    { id: 'special', label: 'Есть специальный символ', checked: passed('SPECIAL_CHARACTER') },
    { id: 'confirmation', label: 'Пароли совпадают', checked: Boolean(password.value && confirmation.value) && password.value === confirmation.value },
  );
  return items;
});
const fulfilledRequirements = computed(() => requirements.value.filter((item) => item.checked).length);
const passwordError = computed(() => passwordIssues.value.includes('UNPAIRED_SURROGATE')
  ? 'Замени неподдерживаемый символ в пароле.'
  : passwordIssues.value.includes('MAX_UTF8_BYTES')
    ? 'Пароль слишком длинный — сократи его.'
    : '');
const valid = computed(
  () =>
    current.value.length > 0 &&
    password.value === confirmation.value &&
    passwordIssues.value.length === 0,
);
function input() {
  emit('dirty', Boolean(current.value || password.value || confirmation.value));
  confirming.value = false;
}
function submit() {
  if (!valid.value || props.busy || props.uncertain) return;
  emit('change', { currentPassword: current.value, newPassword: password.value });
  current.value = '';
  password.value = '';
  confirmation.value = '';
  confirming.value = false;
  emit('dirty', false);
}
onScopeDispose(() => {
  current.value = '';
  password.value = '';
  confirmation.value = '';
});
</script>
<template>
  <form
    class="web-panel web-stack web-security-form"
    :aria-busy="busy"
    @input="input"
    @submit.prevent="confirming = valid"
  >
    <h2>Смена пароля</h2>
    <label class="web-field" :for="fieldId + '-current'">
      <span :id="fieldId + '-current-label'">Текущий пароль *</span>
      <span class="web-password">
        <input
          :id="fieldId + '-current'"
          :aria-labelledby="fieldId + '-current-label'"
          v-model="current"
          :type="currentVisible ? 'text' : 'password'"
          autocomplete="current-password"
          required
          :disabled="busy || uncertain"
        >
        <button
          class="web-password-toggle"
          type="button"
          :aria-label="currentVisible ? 'Скрыть текущий пароль' : 'Показать текущий пароль'"
          :aria-pressed="currentVisible"
          @click="currentVisible = !currentVisible"
        ><SvgIcon :src="currentVisible ? securityHide : securityShow" :width="20" :height="20" /></button>
      </span>
    </label>
    <label class="web-field" :for="fieldId + '-new'">
      <span :id="fieldId + '-new-label'">Новый пароль *</span>
      <span class="web-password">
        <input
          :id="fieldId + '-new'"
          :aria-labelledby="fieldId + '-new-label'"
          v-model="password"
          :type="passwordVisible ? 'text' : 'password'"
          autocomplete="new-password"
          required
          :disabled="busy || uncertain"
          :aria-describedby="passwordError ? `security-policy ${fieldId}-issue` : 'security-policy'"
        >
        <button
          class="web-password-toggle"
          type="button"
          :aria-label="passwordVisible ? 'Скрыть новый пароль' : 'Показать новый пароль'"
          :aria-pressed="passwordVisible"
          @click="passwordVisible = !passwordVisible"
        ><SvgIcon :src="passwordVisible ? securityHide : securityShow" :width="20" :height="20" /></button>
      </span>
    </label>
    <label class="web-field" :for="fieldId + '-confirmation'">
      <span :id="fieldId + '-confirmation-label'">Повтори новый пароль *</span>
      <span class="web-password">
        <input
          :id="fieldId + '-confirmation'"
          :aria-labelledby="fieldId + '-confirmation-label'"
          v-model="confirmation"
          :type="confirmationVisible ? 'text' : 'password'"
          autocomplete="new-password"
          required
          :disabled="busy || uncertain"
        >
        <button
          class="web-password-toggle"
          type="button"
          :aria-label="confirmationVisible ? 'Скрыть повтор нового пароля' : 'Показать повтор нового пароля'"
          :aria-pressed="confirmationVisible"
          @click="confirmationVisible = !confirmationVisible"
        ><SvgIcon :src="confirmationVisible ? securityHide : securityShow" :width="20" :height="20" /></button>
      </span>
    </label>
    <ul
      id="security-policy"
      class="web-security-requirements"
      aria-label="Требования к паролю"
    >
      <li v-for="requirement in requirements" :key="requirement.id">
        <span class="web-security-requirement" :class="{ 'is-met': requirement.checked, 'is-unmet': !requirement.checked && Boolean(password) && (requirement.id !== 'confirmation' || Boolean(confirmation)) }" role="checkbox" :aria-checked="requirement.checked" aria-readonly="true">
          <span class="web-security-check" aria-hidden="true" />
          <span>{{ requirement.label }}</span>
        </span>
      </li>
    </ul>
    <p v-if="passwordError" :id="fieldId + '-issue'" class="web-error" role="status">{{ passwordError }}</p>
    <p v-else class="web-sr-only" role="status">Выполнено требований: {{ fulfilledRequirements }} из {{ requirements.length }}.</p>
    <ConfirmDialog
      :open="confirming"
      title="Сменить пароль?"
      confirm-label="Подтвердить смену пароля"
      :busy="busy"
      @cancel="confirming = false"
      @confirm="submit"
    >
      <p>После смены пароля все входы будут завершены.</p>
    </ConfirmDialog>
    <div class="web-security-actions">
      <RouterLink class="web-button web-security-recovery" :to="{ name: 'password-recovery' }">
        Не помню пароль
      </RouterLink>
      <button
        class="web-button web-button-primary"
        type="submit"
        :disabled="busy || uncertain || !valid"
      >
        Сменить пароль
      </button>
    </div>
  </form>
</template>
