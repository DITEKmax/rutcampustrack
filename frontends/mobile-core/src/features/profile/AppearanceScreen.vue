<script setup lang="ts">
import MobileIcon from '../../shared/components/MobileIcon.vue';
import type { ProfileResolvedTheme, ProfileTheme } from './profile-types';
import './profile-screen.pcss';

const props = withDefaults(
  defineProps<{
    theme: ProfileTheme;
    resolvedTheme?: ProfileResolvedTheme;
    onBack?: (() => void | Promise<void>) | undefined;
    onThemeChange?: ((theme: ProfileTheme) => void | Promise<void>) | undefined;
  }>(),
  {
    resolvedTheme: 'dark',
    onBack: undefined,
    onThemeChange: undefined,
  },
);

const options: readonly { value: ProfileTheme; label: string }[] = [
  { value: 'light', label: 'Светлая' },
  { value: 'dark', label: 'Тёмная' },
  { value: 'system', label: 'Как в системе' },
];

function chooseTheme(theme: ProfileTheme): void {
  void props.onThemeChange?.(theme);
}
</script>

<template>
  <main
    class="profile-screen profile-appearance"
    :data-theme="resolvedTheme"
    :style="{ colorScheme: resolvedTheme }"
    aria-labelledby="profile-appearance-title"
  >
    <div class="profile-screen__content">
      <header class="profile-header">
        <button
          class="profile-back"
          type="button"
          aria-label="Назад"
          @click="onBack?.()"
        >
          <MobileIcon name="back" />
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
          />
          <span>{{ option.label }}</span>
        </label>
      </fieldset>
    </div>
  </main>
</template>
