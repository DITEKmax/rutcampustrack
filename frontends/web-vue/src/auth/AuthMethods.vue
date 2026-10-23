<template>
  <nav
    class="web-auth-tabs"
    aria-label="Способ входа"
  >
    <RouterLink :to="{ name: 'login-otp' }">
      Код из Telegram
    </RouterLink>
    <RouterLink :to="{ name: 'login' }">
      Логин и пароль
    </RouterLink>
    <RouterLink :to="{ name: 'login-qr' }">
      QR-код
    </RouterLink>
  </nav>
</template>
