<script setup lang="ts">
import { inject, onBeforeUnmount, ref, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { createMobileTheme, mobileThemeSystemSourceKey } from '../../shared/theme';
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

// Preview only this screen; the host keeps ownership of html and persistence.
const systemThemeSource = inject(mobileThemeSystemSourceKey, undefined);
const preview = createMobileTheme({
  target: null,
  mode: props.theme,
  ...(systemThemeSource === undefined ? {} : { systemSource: systemThemeSource }),
});
const previewTheme = ref<ProfileResolvedTheme>(preview.resolvedMode);
const stopPreviewSnapshot = preview.subscribe(({ resolvedMode }) => {
  previewTheme.value = resolvedMode;
});
const stopPreviewMode = watch(() => props.theme, mode => preview.setMode(mode));
onBeforeUnmount(() => {
  stopPreviewMode();
  stopPreviewSnapshot();
  preview.dispose();
});

function chooseTheme(theme: ProfileTheme): void {
  void props.onThemeChange?.(theme);
}
</script>

<template>
  <main
    class="profile-screen profile-appearance"
    :data-theme="previewTheme"
    :style="{ colorScheme: previewTheme }"
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
      <p class="profile-note__body">
        Выбранная тема сохраняется и пока применяется только на этом экране.
        Остальные экраны остаются тёмными.
      </p>
    </div>
  </main>
</template>
