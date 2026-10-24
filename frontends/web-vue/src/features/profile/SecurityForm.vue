<script setup lang="ts">
import ConfirmDialog from '../../shared/ConfirmDialog.vue';
import { computed, onScopeDispose, ref } from 'vue';
import {
  validateNewPassword,
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
const visible = ref(false);
const confirming = ref(false);
const valid = computed(
  () =>
    current.value.length > 0 &&
    password.value === confirmation.value &&
    validateNewPassword(password.value, props.policy).length === 0,
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
    class="web-panel web-stack"
    :aria-busy="busy"
    @input="input"
    @submit.prevent="confirming = valid"
  >
    <h2>Смена пароля</h2>
    <label class="web-field">Текущий пароль *<input
      v-model="current"
      :type="visible ? 'text' : 'password'"
      autocomplete="current-password"
      required
      :disabled="busy || uncertain"
    ></label>
    <label class="web-field">Новый пароль *<input
      v-model="password"
      :type="visible ? 'text' : 'password'"
      autocomplete="new-password"
      required
      :disabled="busy || uncertain"
      aria-describedby="security-policy"
    ></label>
    <label class="web-field">Повтори новый пароль *<input
      v-model="confirmation"
      :type="visible ? 'text' : 'password'"
      autocomplete="new-password"
      required
      :disabled="busy || uncertain"
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
      id="security-policy"
      class="web-caption"
    >
      Не менее {{ policy.minCodePoints }} символов, цифра и специальный символ. Не более
      {{ policy.maxUtf8Bytes }} байт UTF-8.
    </p>
    <p
      v-if="confirmation && password !== confirmation"
      class="web-error"
    >
      Пароли не совпадают.
    </p>
    <RouterLink :to="{ name: 'password-recovery' }">
      Не помню текущий пароль
    </RouterLink>
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
    <button
      class="web-button web-button-primary"
      :disabled="busy || uncertain || !valid"
    >
      Сменить пароль
    </button>
  </form>
</template>
