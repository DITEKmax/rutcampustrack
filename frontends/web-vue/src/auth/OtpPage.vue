<script setup lang="ts">
import { onScopeDispose, ref } from 'vue';
import AuthFrame from './AuthFrame.vue';
import AuthMethods from './AuthMethods.vue';
import CodeInput from './CodeInput.vue';
import { webSession } from './session';
const code = ref('');
async function submit() {
  if (!/^[0-9]{6}$/.test(code.value) || webSession.busy.value) return;
  await webSession.loginOtp(code.value);
  code.value = '';
}
onScopeDispose(webSession.cancelAuthentication);
</script>
<template>
  <AuthFrame screen="WEB-AUTH-OTP">
    <h1>Вход</h1>
    <AuthMethods />
    <p class="web-caption">
      Получи код входа в Telegram-боте RutTrack и введи его здесь.
    </p>
    <form
      class="web-stack"
      :aria-busy="webSession.busy.value"
      @submit.prevent="submit"
    >
      <CodeInput
        v-model="code"
        label="Шестизначный код из Telegram"
        :disabled="webSession.busy.value"
      />
      <p
        v-if="webSession.error.value"
        class="web-error"
        role="alert"
      >
        {{ webSession.error.value }}
      </p>
      <button
        class="web-button web-button-primary"
        :disabled="webSession.busy.value || code.length !== 6"
      >
        {{ webSession.busy.value ? 'Проверяем…' : 'Войти' }}
      </button>
    </form>
  </AuthFrame>
</template>
