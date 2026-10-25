<script setup lang="ts">
import { onScopeDispose, ref } from 'vue';
import AuthFrame from './AuthFrame.vue';
import AuthMethods from './AuthMethods.vue';
import { webSession } from './session';

const login = ref('');
const password = ref('');
const visible = ref(false);
async function submit() {
  if (!login.value.trim() || !password.value || webSession.busy.value) return;
  await webSession.login(login.value.trim(), password.value);
  password.value = '';
}
onScopeDispose(webSession.cancelAuthentication);
</script>

<template>
  <AuthFrame screen="WEB-AUTH-PASSWORD">
    <h1 id="login-title">
      Вход
    </h1>
    <AuthMethods />
    <form
      class="web-stack"
      :aria-busy="webSession.busy.value"
      @submit.prevent="submit"
    >
      <label
        class="web-field"
        for="login"
      >
        <span>Логин *</span>
        <input
          id="login"
          v-model="login"
          name="username"
          autocomplete="username"
          placeholder="Введи логин"
          required
          :disabled="webSession.busy.value"
        >
      </label>
      <label
        class="web-field"
        for="password"
      >
        <span>Пароль *</span>
        <span class="web-password">
          <input
            id="password"
            v-model="password"
            name="password"
            :type="visible ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="Введи пароль"
            required
            :disabled="webSession.busy.value"
          >
          <button
            class="web-password-toggle"
            type="button"
            :aria-pressed="visible"
            :aria-label="visible ? 'Скрыть пароль' : 'Показать пароль'"
            @click="visible = !visible"
          >{{ visible ? 'Скрыть' : 'Показать' }}</button>
        </span>
      </label>
      <p
        v-if="webSession.error.value"
        class="web-error"
        role="alert"
      >
        {{ webSession.error.value }}
      </p>
      <button
        class="web-button web-button-primary"
        type="submit"
        :disabled="webSession.busy.value"
      >
        {{ webSession.busy.value ? 'Входим…' : 'Войти' }}
      </button>
    </form>
    <RouterLink
      class="web-auth-link"
      :to="{ name: 'password-recovery' }"
    >
      Забыл пароль?
    </RouterLink>
  </AuthFrame>
</template>
