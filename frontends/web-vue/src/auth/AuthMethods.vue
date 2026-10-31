<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <nav
    class="web-auth-tabs"
    aria-label="Способ входа"
  >
    <RouterLink :to="{ name: 'login-otp', query: route.query, hash: route.hash }">
      Код из Telegram
    </RouterLink>
    <RouterLink :to="{ name: 'login', query: route.query, hash: route.hash }">
      Логин и пароль
    </RouterLink>
    <RouterLink :to="{ name: 'login-qr', query: route.query, hash: route.hash }">
      QR-код
    </RouterLink>
  </nav>
</template>
