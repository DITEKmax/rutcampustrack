<script setup lang="ts">
import BrandLogo from '../../../shared/brand/BrandLogo.vue';
import ThemeSelect from '../shared/ThemeSelect.vue';
defineProps<{ screen: string }>();
</script>
<template>
  <main
    id="main"
    class="web-auth"
    :data-screen-id="screen"
  >
    <div class="web-auth-theme">
      <ThemeSelect />
    </div>
    <div class="web-auth-content">
      <BrandLogo class="web-auth-brand" />
      <section class="web-auth-panel">
        <slot />
      </section>
    </div>
  </main>
</template>
