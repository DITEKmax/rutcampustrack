<script setup lang="ts">
defineProps<{ message: string; announce?: boolean }>();
</script>

<template>
  <div class="student-empty-lessons">
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 56 44"
      fill="none"
    >
      <rect
        x="3"
        y="5"
        width="42"
        height="35"
        rx="9"
        fill="currentColor"
        opacity=".12"
      />
      <rect
        x="9"
        y="11"
        width="35"
        height="25"
        rx="4"
        stroke="currentColor"
        stroke-width="2"
      />
      <path
        d="M10 18h33m-25-10v6m17-6v6m-14 13 4 4 9-9"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <p :role="announce ? 'status' : undefined">
      {{ message }}
    </p>
  </div>
</template>
