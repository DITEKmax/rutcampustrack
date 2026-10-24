<script setup lang="ts">
import { ref } from 'vue';
import { webSession } from '../../auth/session';
const key = `rct:web:pwa-banner:${webSession.snapshot.value?.userId ?? ''}`;
const visible = ref(true);
try {
  visible.value =
    localStorage.getItem(key) !== 'hidden' &&
    localStorage.getItem('pwa-banner-dismissed') !== 'true';
} catch {
  /* Storage unavailable: keep the action usable. */
}
function dismiss() {
  visible.value = false;
  try {
    localStorage.setItem(key, 'hidden');
  } catch {
    /* This tab still remembers dismissal. */
  }
}
</script>
<template>
  <aside
    v-if="visible"
    class="student-pwa web-notice web-page-header"
    aria-label="Мобильное приложение"
  >
    <p>Расписание и домашние задания всегда под рукой</p>
    <div class="student-actions">
      <a
        class="web-button"
        href="/app/"
      >Установить приложение</a>
      <button
        class="web-button"
        @click="dismiss"
      >
        Больше не показывать
      </button>
    </div>
  </aside>
</template>
