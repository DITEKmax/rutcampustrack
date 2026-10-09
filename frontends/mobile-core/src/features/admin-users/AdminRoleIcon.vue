<script setup lang="ts">
import type { AdminManagedRole } from './admin-users-client'
defineProps<{ role: AdminManagedRole }>()
</script>

<template>
  <svg
    class="admin-users-role-symbol"
    width="16"
    height="16"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.875"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="role === 'STUDENT'">
      <path d="m2 9 10-5 10 5-10 5L2 9Z" />
      <path d="M6 11v6c3 3 9 3 12 0v-6M22 9v7" />
    </template>
    <template v-else-if="role === 'TEACHER'">
      <path d="M10 3h11v12h-7M14 7h4M15 11h3" />
      <circle
        cx="6"
        cy="8"
        r="2.5"
      />
      <path d="M2 21v-5a4 4 0 0 1 8 0v5M10 15l4-5M5 17v4" />
    </template>
    <template v-else-if="role === 'HEADMAN'">
      <circle
        cx="8"
        cy="7"
        r="3"
      />
      <path d="M2 21v-2a6 6 0 0 1 11-3" />
      <path d="m18 12 1.5 3 3.3.5-2.4 2.3.6 3.2-3-1.5-3 1.5.6-3.2-2.4-2.3 3.3-.5L18 12Z" />
    </template>
    <template v-else>
      <path d="M12 2 21 6v6c0 5-5 8-9 10-4-2-9-5-9-10V6l9-4Z" />
      <path d="m8 12 3 3 5-6" />
    </template>
  </svg>
</template>
