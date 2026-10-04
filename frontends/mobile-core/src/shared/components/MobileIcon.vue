<script setup lang="ts">
defineProps<{ name: 'back' | 'chevron-down' | 'room' | 'lesson' | 'clock' | 'calendar' | 'homework' | 'attendance' | 'attendance-graph' | 'more' | 'profile' | 'warning' | 'retry' }>()
</script>

<template>
  <svg
    class="mobile-icon"
    width="16"
    height="16"
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    stroke-width="1.25"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path
      v-if="name === 'back'"
      d="M10 3.5 5.5 8 10 12.5"
    />
    <path
      v-else-if="name === 'chevron-down'"
      d="m3.5 6 4.5 4.5L12.5 6"
    />
    <template v-else-if="name === 'room'">
      <path d="M3 14V2.5L10.5 1v13M2 14h11M10.5 3H13v11" /><path d="M8 8h.01" />
    </template>
    <template v-else-if="name === 'lesson'">
      <rect
        x="2"
        y="2.5"
        width="12"
        height="8"
        rx="1.5"
      /><path d="M5.5 13.5h5M8 10.5v3M5 5.5h6M5 7.5h4" />
    </template>
    <template v-else-if="name === 'clock'">
      <circle
        cx="8"
        cy="8"
        r="6"
      /><path d="M8 4.5V8l2.5 1.5" />
    </template>
    <template v-else-if="name === 'calendar'">
      <rect
        x="2.5"
        y="3.5"
        width="11"
        height="10"
        rx="2"
      /><path d="M5 2v3M11 2v3M2.5 7h11M5 9.5h1M9 9.5h1M5 11.5h1" />
    </template>
    <template v-else-if="name === 'homework'">
      <rect
        x="3.5"
        y="2"
        width="9"
        height="12"
        rx="2"
      /><path d="M6 5h4M6 8h4M6 11h2" />
    </template>
    <template v-else-if="name === 'attendance'">
      <circle
        cx="8"
        cy="8"
        r="6"
      /><path d="m5 8 2 2 4-4" />
    </template>
    <template v-else-if="name === 'attendance-graph'">
      <path d="M2 2v12h12M5 11V8M8 11V5M11 11V3" />
    </template>
    <template v-else-if="name === 'more'">
      <circle
        cx="3"
        cy="8"
        r=".6"
        fill="currentColor"
      /><circle
        cx="8"
        cy="8"
        r=".6"
        fill="currentColor"
      /><circle
        cx="13"
        cy="8"
        r=".6"
        fill="currentColor"
      />
    </template>
    <template v-else-if="name === 'profile'">
      <circle
        cx="8"
        cy="4.5"
        r="2.5"
      /><path d="M3 14v-1.5a5 5 0 0 1 10 0V14" />
    </template>
    <template v-else-if="name === 'warning'">
      <path d="m7 2.5-5 9a1.4 1.4 0 0 0 1.2 2h9.6a1.4 1.4 0 0 0 1.2-2l-5-9a1.2 1.2 0 0 0-2 0Z" /><path d="M8 6v3M8 11h.01" />
    </template>
    <template v-else>
      <path d="M13.5 7A5.5 5.5 0 1 0 12 12M13.5 2.5V7H9" />
    </template>
  </svg>
</template>
