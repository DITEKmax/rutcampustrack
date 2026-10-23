<script setup lang="ts">
import type { TeacherScope } from './teacher-state';
defineProps<{ scope: TeacherScope; screen: string }>();
</script>
<template>
  <section
    class="teacher-page web-stack"
    :data-screen-id="screen"
    @pointerenter="scope.setPointerWithin(true)"
    @pointerleave="scope.setPointerWithin(false)"
    @focusin="scope.setFocusWithin(true)"
    @focusout="
      scope.setFocusWithin(Boolean(
        ($event.relatedTarget as Node | null) && $el.contains($event.relatedTarget),
      ))
    "
  >
    <slot />
  </section>
</template>
<style src="./teacher.pcss"></style>
