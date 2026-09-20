<script setup lang="ts">
import type { ProfileRequestError } from '@rct/mobile-core'
import './login-screen.pcss'

withDefaults(defineProps<{
  loading?: boolean
  error?: ProfileRequestError | null
}>(), {
  loading: false,
  error: null,
})

const emit = defineEmits<{
  submit: [input: { login: string; password: string }]
}>()

function submit(event: SubmitEvent): void {
  const form = event.currentTarget
  if (!(form instanceof HTMLFormElement)) return
  const data = new FormData(form)
  const login = String(data.get('login') ?? '')
  const password = String(data.get('password') ?? '')
  if (login.length === 0 || password.length === 0) return
  emit('submit', { login, password })
}
</script>

<template>
  <main
    class="login-screen"
    aria-labelledby="login-title"
  >
    <form
      class="login-card"
      @submit.prevent="submit"
    >
      <p class="login-card__eyebrow">
        RutCampusTrack
      </p>
      <h1 id="login-title">
        Войти в приложение
      </h1>
      <p class="login-card__hint">
        Введи логин и пароль, чтобы открыть расписание и задания.
      </p>

      <label class="login-field">
        <span>Логин</span>
        <input
          name="login"
          type="text"
          autocomplete="username"
          required
          :disabled="loading"
        >
      </label>
      <label class="login-field">
        <span>Пароль</span>
        <input
          name="password"
          type="password"
          autocomplete="current-password"
          required
          :disabled="loading"
        >
      </label>

      <p
        v-if="error"
        class="login-card__error"
        role="alert"
      >
        {{ error.message }}
      </p>
      <button
        class="login-card__submit"
        type="submit"
        :disabled="loading"
        :aria-busy="loading"
      >
        {{ loading ? 'Проверяем…' : 'Войти' }}
      </button>
    </form>
  </main>
</template>
