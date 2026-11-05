<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue';
import { acquireInert } from '../../shared/inert-lease';
const props = defineProps<{ visible: boolean }>();
let releaseInert: (() => void) | undefined;
watch(
  () => props.visible,
  (visible) => {
    if (visible) releaseInert ??= acquireInert(document.getElementById('app'));
    else {
      releaseInert?.();
      releaseInert = undefined;
    }
  },
  { flush: 'post' },
);
onBeforeUnmount(() => releaseInert?.());
</script>
<template>
  <Teleport
    to="body"
    :disabled="!visible"
  >
    <div :class="{ 'rct-auth-success-overlay': visible }"><slot /></div>
  </Teleport>
</template>
