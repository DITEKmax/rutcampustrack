<script setup lang="ts">
import { computed } from 'vue';
import './svg.pcss';
const props = defineProps<{ src: string; width?: number; height?: number; alt?: string }>();
const maskStyle = computed(() => ({
  '--rct-svg-source': `url("${props.src}")`,
  '--rct-svg-width': `${(props.width ?? 16) / 16}rem`,
  '--rct-svg-height': `${(props.height ?? 16) / 16}rem`,
}));
</script>

<template>
  <i
    class="rct-svg-icon"
    :style="maskStyle"
    :aria-hidden="alt ? undefined : true"
    :aria-label="alt || undefined"
    :role="alt ? 'img' : undefined"
  />
</template>
