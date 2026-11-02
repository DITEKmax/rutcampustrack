<script setup lang="ts">
import { computed } from 'vue';
import { findPreset } from '../../../shared/profile/avatar-presets';
const props = defineProps<{ avatarId?: string | null | undefined; initials: string }>();
const preset = computed(() => findPreset(props.avatarId));
</script>
<template>
  <span class="web-avatar" aria-hidden="true">
    <svg v-if="preset" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <template v-if="preset.id === 'avatar_01'">
        <path d="m2 9 10-5 10 5-10 5L2 9Zm4 2v6c4 3 8 3 12 0v-6M22 9v8" />
      </template>
      <template v-else-if="preset.id === 'avatar_02'">
        <path d="M4 13V4h17v14h-6M12 7h6M15 10h3M2 21v-3a4 4 0 0 1 8 0v3" /><circle cx="6" cy="11" r="3" />
      </template>
      <template v-else-if="preset.id === 'avatar_03'">
        <path d="M8 15c0-5 5-11 13-12 0 8-6 13-11 13l-2-1ZM8 10H4l-2 6 6-1m6 1v4l-6 2 2-6M5 18l-2 3" /><circle cx="16" cy="8" r="2" />
      </template>
      <template v-else-if="preset.id === 'avatar_04'">
        <path d="M5 9 3 3l7 4h4l7-4-2 6a8 8 0 1 1-14 0ZM10 15l2 2 2-2M12 17v2M2 13l5 1m-5 4 5-1m15-4-5 1m5 4-5-1" /><circle cx="9" cy="12" r=".5" /><circle cx="15" cy="12" r=".5" />
      </template>
      <template v-else-if="preset.id === 'avatar_05'">
        <path d="M7 5 3 3 1 12l4 3 2-6m10-4 4-2 2 9-4 3-2-6M7 5h10v10a5 5 0 0 1-10 0V5Zm3 10 2 2 2-2M12 17v3" /><circle cx="10" cy="11" r=".5" /><circle cx="14" cy="11" r=".5" />
      </template>
      <template v-else-if="preset.id === 'avatar_06'">
        <circle cx="12" cy="12" r="9" /><path d="M8 15a5 5 0 0 0 8 0" /><circle cx="9" cy="9" r=".5" /><circle cx="15" cy="9" r=".5" />
      </template>
      <template v-else-if="preset.id === 'avatar_07'">
        <path d="M5 21V10a7 7 0 0 1 14 0v11l-4-3-3 3-3-3-4 3Z" /><circle cx="9" cy="10" r="1" /><circle cx="15" cy="10" r="1" />
      </template>
      <template v-else-if="preset.id === 'avatar_08'">
        <path d="M7 7h10a4 4 0 0 1 4 3l1 8a2 2 0 0 1-3 2l-4-3H9l-4 3a2 2 0 0 1-3-2l1-8a4 4 0 0 1 4-3ZM7 10v5m-2-2.5h4" /><circle cx="16" cy="11" r=".5" /><circle cx="19" cy="14" r=".5" />
      </template>
      <template v-else-if="preset.id === 'avatar_09'">
        <path d="M9 18V5l11-2v13M9 9l11-2" /><ellipse cx="6" cy="18" rx="3" ry="2.5" /><ellipse cx="17" cy="16" rx="3" ry="2.5" />
      </template>
      <template v-else-if="preset.id === 'avatar_10'">
        <circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3v18M5.5 5.5a9 9 0 0 1 0 13m13-13a9 9 0 0 0 0 13" />
      </template>
      <template v-else-if="preset.id === 'avatar_11'">
        <path d="M20 3C8 3 3 7 4 14a7 7 0 0 0 7 7c7 0 10-6 9-18ZM3 22 15 10m-5 5v-4m0 4h4" />
      </template>
      <template v-else>
        <path d="M4 8h13v8a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V8Zm13 1h2a3 3 0 0 1 0 6h-2M7 3v2m5-2v2M2 22h18" />
      </template>
    </svg>
    <span v-else>{{ initials }}</span>
  </span>
</template>
