<script setup lang="ts">
import type { StudentScope } from './student-state';
import './student.pcss';
defineProps<{ scope: StudentScope; screen: string }>();
</script>
<template>
  <section
    class="student-page web-stack"
    :data-screen-id="screen"
  >
    <slot />
  </section>
</template>
