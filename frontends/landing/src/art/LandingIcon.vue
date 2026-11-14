<script setup lang="ts">
const paths = {
  check: 'm5 12 4.5 4.5L19 7',
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  down: 'M12 4v16m-6-6 6 6 6-6',
  calendar:
    'M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Zm1-2v4m8-4v4M4 11h16M8 15h.01m3.99 0h.01m3.99 0h.01',
  chart: 'M5 20V10m7 10V4m7 16v-7',
  pin: 'M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0ZM14.5 10a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z',
  bell: 'M5 17h14l-2-4V9a5 5 0 0 0-10 0v4l-2 4Zm5 3h4',
  phone: 'M8 2h8a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3Zm2 17h4',
  repeat: 'M20 7v5h-5m5 0a8 8 0 1 0-2 6',
  book: 'M3 5h6a3 3 0 0 1 3 3v13a4 4 0 0 0-4-3H3V5Zm18 0h-6a3 3 0 0 0-3 3v13a4 4 0 0 1 4-3h5V5Z',
} as const;
defineProps<{ name: keyof typeof paths }>();
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    class="landing-icon"
    aria-hidden="true"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path :d="paths[name]" />
  </svg>
</template>
