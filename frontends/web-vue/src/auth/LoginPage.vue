<script setup lang="ts">
import { ref } from 'vue';
import ThemeSelect from '../shared/ThemeSelect.vue';
import { webSession } from './session';

const login = ref('');
const password = ref('');
const visible = ref(false);
async function submit() {
  if (!login.value.trim() || !password.value || webSession.busy.value) return;
  await webSession.login(login.value.trim(), password.value);
  password.value = '';
}
</script>

<template>
  <main
    id="main"
    class="web-auth"
    data-screen-id="WEB-AUTH-PASSWORD"
  >
    <div class="web-auth-theme">
      <ThemeSelect />
    </div>
    <div class="web-auth-content">
      <div
        class="web-brand"
        aria-label="RutTrack"
      />
      <section
        class="web-auth-panel"
        aria-labelledby="login-title"
      >
        <h1 id="login-title">
          Вход
        </h1>
        <div class="web-auth-method">
          Логин и пароль
        </div>
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
      </section>
    </div>
  </main>
</template>
