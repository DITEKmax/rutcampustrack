<script setup lang="ts">
import { computed } from 'vue';
import { webSession } from '../auth/session';
import { canShowFeature, webFeatures } from '../app/features';
const features = computed(() => webFeatures.filter(canShowFeature));
</script>

<template>
  <section class="web-stack">
    <h1>{{ webSession.snapshot.value?.activeRole ? 'Разделы' : 'Выбери роль' }}</h1>
    <RouterLink
      v-for="feature in features"
      :key="feature.path"
      class="web-button"
      :to="feature.path"
    >
      {{ feature.meta?.label }}
    </RouterLink>
    <p
      v-if="!features.length && webSession.snapshot.value?.activeRole"
      role="status"
    >
      Разделы этой роли пока недоступны.
    </p>
  </section>
</template>
