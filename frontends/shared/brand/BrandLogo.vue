<script setup lang="ts">
import lockupLight from './assets/horizontal-titlecase-on-light.svg?no-inline';
import lockupDark from './assets/horizontal-titlecase-on-dark.svg?no-inline';
import markColor from './assets/mark-color.svg?no-inline';
import markWhite from './assets/mark-white.svg?no-inline';
import './brand-logo.pcss';

withDefaults(defineProps<{ compact?: boolean; alt?: string }>(), {
  compact: false,
  alt: 'RutTrack',
});
</script>

<template>
  <span class="ruttrack-logo">
    <img
      class="ruttrack-logo__light"
      :src="compact ? markColor : lockupLight"
      :width="compact ? 256 : 685"
      :height="compact ? 256 : 192"
      :alt="alt"
    />
    <img
      class="ruttrack-logo__dark"
      :src="compact ? markWhite : lockupDark"
      :width="compact ? 256 : 685"
      :height="compact ? 256 : 192"
      :alt="alt"
    />
  </span>
</template>
