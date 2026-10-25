<script setup lang="ts">
import { watch } from 'vue';
import { router } from './app/router';
import { webSession } from './auth/session';
import ReauthenticationReceipt from './auth/ReauthenticationReceipt.vue';
import { chunkRecovery } from './app/chunk-recovery';
import './app/live';
let lastRole = webSession.snapshot.value?.activeRole ?? null;

watch(
  [webSession.phase, () => webSession.snapshot.value?.activeRole, webSession.identity],
  ([phase], [previousPhase]) => {
    if (phase === 'guest' && previousPhase !== 'starting' && !router.currentRoute.value.meta.guest)
      void router.replace({ name: 'login' });
    else if (phase === 'ready') {
      if (!webSession.snapshot.value) return;
      const route = router.currentRoute.value;
      const role = webSession.snapshot.value?.activeRole;
      const changedRole = lastRole !== null && lastRole !== role;
      lastRole = role ?? null;
      if (
        changedRole ||
        (route.name === 'role-home' && role) ||
        (route.meta.guest && route.name !== 'password-recovery') ||
        (route.meta.requiredRoles && (!role || !route.meta.requiredRoles.includes(role)))
      )
        void router.replace({
          name: 'role-home',
          query: route.query,
          hash: route.hash,
          force: route.name === 'role-home',
        });
    }
    if (phase === 'guest') lastRole = null;
  },
);
</script>

<template>
  <ReauthenticationReceipt />
  <section
    v-if="chunkRecovery.failed.value"
    class="web-notice web-stack"
    aria-label="Не удалось открыть раздел"
  >
    <p role="alert">
      Не удалось открыть раздел. Возможно, приложение обновилось или пропало подключение. Перед
      обновлением страницы сохрани изменения и проверь результат незавершённых операций.
    </p>
    <button
      v-if="chunkRecovery.canRefresh.value"
      class="web-button"
      @click="chunkRecovery.refresh"
    >
      Обновить страницу
    </button>
    <p v-else>
      Проверь подключение. Если раздел по-прежнему недоступен, попробуй открыть его позже.
    </p>
  </section>
  <main
    v-if="
      webSession.phase.value === 'starting' ||
        (webSession.phase.value === 'ready' && !webSession.snapshot.value)
    "
    id="main"
    class="web-session-state"
    aria-busy="true"
  >
    <p role="status">
      Загрузка сессии…
    </p>
  </main>
  <main
    v-else-if="webSession.phase.value === 'error'"
    id="main"
    class="web-session-state web-stack"
  >
    <h1>Сессия недоступна</h1>
    <p
      class="web-error"
      role="alert"
    >
      {{ webSession.error.value }}
    </p>
    <button
      class="web-button"
      @click="webSession.bootstrap"
    >
      Повторить
    </button>
  </main>
  <RouterView v-else />
</template>
