<script setup lang="ts">
import { theme, setTheme } from '../app/theme';
</script>

<template>
  <label class="web-theme-select">
    <span class="web-sr-only">Тема оформления</span>
    <select
      :value="theme.mode"
      @change="setTheme(($event.target as HTMLSelectElement).value)"
    >
      <option value="system">Системная тема</option>
      <option value="light">Светлая тема</option>
      <option value="dark">Тёмная тема</option>
    </select>
  </label>
</template>
