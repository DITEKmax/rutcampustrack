<script setup lang="ts">
import type { ProfileResolvedTheme, ProfileTheme } from './profile-types'
import appearanceChecked from './assets/appearance-checked.svg'
import appearancePrevious from './assets/appearance-previous.svg'
import './profile-screen.pcss'

const props = withDefaults(defineProps<{
  theme: ProfileTheme
  resolvedTheme?: ProfileResolvedTheme
  onBack?: (() => void | Promise<void>) | undefined
  onThemeChange?: ((theme: ProfileTheme) => void | Promise<void>) | undefined
}>(), {
  resolvedTheme: 'dark',
  onBack: undefined,
  onThemeChange: undefined,
})

const options: readonly { value: ProfileTheme; label: string }[] = [
  { value: 'light', label: 'Светлая' },
  { value: 'dark', label: 'Тёмная' },
  { value: 'system', label: 'Как в системе' },
]

function chooseTheme(theme: ProfileTheme): void {
  void props.onThemeChange?.(theme)
}
</script>

<template>
  <main
    class="profile-screen profile-appearance"
    :data-theme="resolvedTheme"
    aria-labelledby="profile-appearance-title"
  >
    <div class="profile-screen__content">
      <header class="profile-header">
        <button
          class="profile-back"
          type="button"
          aria-label="Назад"
          @click="onBack"
        >
          <img
            :src="appearancePrevious"
            alt=""
            aria-hidden="true"
          >
        </button>
        <h1
          id="profile-appearance-title"
          class="profile-header__title"
        >
          Оформление
        </h1>
      </header>

      <fieldset class="profile-theme-group">
        <legend>Тема интерфейса</legend>
        <label
          v-for="option in options"
          :key="option.value"
          class="profile-theme-option"
        >
          <input
            type="radio"
            name="profile-theme"
            :value="option.value"
            :checked="theme === option.value"
            @change="chooseTheme(option.value)"
          >
          <span>{{ option.label }}</span>
          <img
            v-if="theme === option.value"
            :src="appearanceChecked"
            alt="Выбрано"
            aria-hidden="true"
          >
        </label>
      </fieldset>

      <section class="profile-note">
        <h2 class="profile-note__title">
          Системная тема
        </h2>
        <p class="profile-note__body">
          В Telegram этот режим следует теме клиента.
        </p>
      </section>
    </div>
  </main>
</template>
