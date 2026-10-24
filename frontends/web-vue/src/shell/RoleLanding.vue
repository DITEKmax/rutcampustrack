<script setup lang="ts">
import { webSession } from '../auth/session';
</script>

<template>
  <section class="web-stack">
    <h1>{{ webSession.snapshot.value?.activeRole ? 'Страница недоступна' : 'Выбери роль' }}</h1>
    <RouterLink
      v-if="webSession.snapshot.value?.activeRole"
      class="web-button"
      :to="{ name: 'role-home' }"
    >
      Открыть главную
    </RouterLink>
    <p
      v-if="webSession.snapshot.value?.activeRole"
      role="status"
    >
      Проверь адрес или перейди на главную своей роли.
    </p>
  </section>
</template>
