<script setup lang="ts">
import { watch } from 'vue';
import { router } from './app/router';
import { webSession } from './auth/session';

watch(
  () => webSession.phase.value,
  (phase) => {
    if (phase === 'guest') void router.replace({ name: 'login' });
    else if (phase === 'ready') {
      const route = router.currentRoute.value;
      const role = webSession.snapshot.value?.activeRole;
      if (
        route.name === 'login' ||
        (route.meta.requiredRoles && (!role || !route.meta.requiredRoles.includes(role)))
      )
        void router.replace({ name: 'role-home' });
    }
  },
);
</script>

<template>
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
