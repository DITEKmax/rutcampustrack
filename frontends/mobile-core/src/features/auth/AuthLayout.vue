<script setup lang="ts">
import { authTheme as theme } from './auth-theme';
import AuthSun from './AuthSun.vue';
import AuthMoon from './AuthMoon.vue';
import './auth-screen.pcss';
</script>
<template>
  <main
    class="rct-auth"
    :data-theme="theme"
  >
    <header class="product-header">
      <div class="product-brand">
        <svg
          class="brand-symbol"
          viewBox="0 0 28 28"
          fill="none"
          aria-hidden="true"
        >
          <rect
            x="1"
            y="1"
            width="26"
            height="26"
            rx="8"
            fill="var(--rct-color-accent-soft)"
          />
          <path
            d="M8 21V8h7a4 4 0 0 1 0 8H8m7-1 6 6M11 5v6m0 7v5"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
          />
        </svg>
        RutCampusTrack
      </div>
      <button
        class="icon-button theme-toggle"
        type="button"
        :aria-label="theme === 'dark' ? 'Включить светлую тему' : 'Включить тёмную тему'"
        @click="theme = theme === 'dark' ? 'light' : 'dark'"
      >
        <AuthSun
          v-if="theme === 'dark'"
          aria-hidden="true"
        /><AuthMoon
          v-else
          aria-hidden="true"
        />
      </button>
    </header>
    <slot />
  </main>
</template>
