<script setup lang="ts">
import BrandLogo from '../../../../shared/brand/BrandLogo.vue';
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
      <BrandLogo class="product-brand" />
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
