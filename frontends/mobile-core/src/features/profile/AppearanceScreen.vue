<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { createMobileTheme, type MobileThemeController } from '../../shared/theme';
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

// The preview owns no document target; the app's validated global appearance stays dark.
const previewResolvedTheme = ref<ProfileResolvedTheme>(props.theme === 'light' ? 'light' : 'dark');
let preview: MobileThemeController | undefined;
let stopPreview: (() => void) | undefined;
onMounted(() => {
  preview = createMobileTheme({ target: null, mode: props.theme });
  previewResolvedTheme.value = preview.resolvedMode;
  stopPreview = preview.subscribe(({ resolvedMode }) => {
    previewResolvedTheme.value = resolvedMode;
  });
});
watch(
  () => props.theme,
  (mode) => preview?.setMode(mode),
);
onBeforeUnmount(() => {
  stopPreview?.();
  preview?.dispose();
});

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
    :data-theme="previewResolvedTheme"
    :style="{ colorScheme: previewResolvedTheme }"
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

      <fieldset
        class="profile-theme-group"
        aria-describedby="profile-theme-preview-note"
      >
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
        </label>
      </fieldset>
      <section
        class="profile-note"
        aria-label="Предпросмотр темы"
      >
        <p
          id="profile-theme-preview-note"
          class="profile-note__body"
        >
          Предпросмотр темы — только на этом экране. Остальные экраны пока тёмные.
        </p>
        <p
          class="profile-note__body"
          role="status"
        >
          Предпросмотр: {{ previewResolvedTheme === 'light' ? 'светлая' : 'тёмная' }} тема.
        </p>
      </section>
    </div>
  </main>
</template>
