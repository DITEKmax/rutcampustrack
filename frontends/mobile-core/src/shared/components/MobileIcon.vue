<script setup lang="ts">
import copyIcon from '../../assets/admin/copy.svg'
defineProps<{ name: 'plus' | 'table' | 'chart' | 'filter' | 'download' | 'trash' | 'close' | 'sort' | 'copy' | 'geo-approved' | 'back' | 'chevron-down' | 'room' | 'lesson' | 'clock' | 'calendar' | 'homework' | 'attendance' | 'attendance-graph' | 'more' | 'profile' | 'search' | 'warning' | 'retry' | 'bell' | 'assistant' | 'settings' }>()
</script>

<template>
  <img
    v-if="name === 'copy'"
    class="mobile-icon"
    :src="copyIcon"
    alt=""
    aria-hidden="true"
  >
  <svg
    v-else
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
    <template v-else-if="name === 'settings'">
      <path d="m6.5 2-.5 1.5-1.5.8L3 4l-1 2 1.2 1.1v1.8L2 10l1 2 1.5-.3 1.5.8.5 1.5h3l.5-1.5 1.5-.8 1.5.3 1-2-1.2-1.1V7.1L14 6l-1-2-1.5.3-1.5-.8L9.5 2Z" />
      <circle
        cx="8"
        cy="8"
        r="2"
      />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M8 3v10M3 8h10" />
    </template>
    <template v-else-if="name === 'table'">
      <rect
        x="2"
        y="2.5"
        width="12"
        height="11"
        rx="1"
      />
      <path d="M2 6h12M2 9.5h12M6 2.5v11" />
    </template>
    <template v-else-if="name === 'chart'">
      <path d="M2 2v12h12M5 11V8M8 11V5M11 11V3" />
    </template>
    <template v-else-if="name === 'filter'">
      <path d="M2 3h12L9.5 8v4.5l-3 1V8L2 3Z" />
    </template>
    <template v-else-if="name === 'download'">
      <path d="M8 2v8M5 7l3 3 3-3M2.5 10.5v3h11v-3" />
    </template>
    <template v-else-if="name === 'trash'">
      <path d="M2.5 4h11M6 4V2.5h4V4M4 4l.7 9.5h6.6L12 4M6.5 6.5v4M9.5 6.5v4" />
    </template>
    <template v-else-if="name === 'close'">
      <path d="m4 4 8 8M12 4l-8 8" />
    </template>
    <template v-else-if="name === 'sort'">
      <path d="M5 2v12M2.5 4.5 5 2l2.5 2.5M11 14V2M8.5 11.5 11 14l2.5-2.5" />
    </template>
    <template v-else-if="name === 'geo-approved'">
      <path
        d="M8 14C8 14 12 10.45 12 7C12 5.93913 11.5786 4.92172 10.8284 4.17157C10.0783 3.42143 9.06087 3 8 3C6.93913 3 5.92172 3.42143 5.17157 4.17157C4.42143 4.92172 4 5.93913 4 7C4 10.45 8 14 8 14Z"
        stroke-width="1"
      />
      <path
        d="M6 7.1L7.25 8.35L10 5.7"
        stroke-width="1"
      />
    </template>
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
    <template v-else-if="name === 'bell'">
      <path d="M3 11.5h10l-1.5-2V6a3.5 3.5 0 0 0-7 0v3.5L3 11.5ZM6.5 14h3M8 1v1.5" />
    </template>
    <template v-else-if="name === 'assistant'">
      <path d="m2 12 6.5-6.5 2 2L4 14l-2-2ZM7 7l2 2M12 1l.8 2.2L15 4l-2.2.8L12 7l-.8-2.2L9 4l2.2-.8L12 1ZM3.5 2.5v3M2 4h3" />
    </template>
    <template v-else-if="name === 'search'">
      <circle
        cx="6.75"
        cy="6.75"
        r="4.25"
      /><path d="m10 10 3.5 3.5" />
    </template>
    <template v-else-if="name === 'warning'">
      <path d="m7 2.5-5 9a1.4 1.4 0 0 0 1.2 2h9.6a1.4 1.4 0 0 0 1.2-2l-5-9a1.2 1.2 0 0 0-2 0Z" /><path d="M8 6v3M8 11h.01" />
    </template>
    <template v-else>
      <path d="M13.5 7A5.5 5.5 0 1 0 12 12M13.5 2.5V7H9" />
    </template>
  </svg>
</template>
