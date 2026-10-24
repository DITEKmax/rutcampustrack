<script setup lang="ts">
import type { StudentScope } from './student-state';
import './student.pcss';
defineProps<{ scope: StudentScope; screen: string }>();
</script>
<template>
  <section
    class="student-page web-stack"
    :data-screen-id="screen"
    @pointerenter="scope.setPointerWithin(true)"
    @pointerleave="scope.setPointerWithin(false)"
    @focusin="scope.setFocusWithin(true)"
    @focusout="
      scope.setFocusWithin(Boolean(
        $event.relatedTarget &&
          ($event.currentTarget as HTMLElement).contains($event.relatedTarget as Node),
      ))
    "
  >
    <slot />
  </section>
</template>
