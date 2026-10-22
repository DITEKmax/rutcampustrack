<script setup lang="ts">
import MobileIcon from './MobileIcon.vue';
import checkIcon from '../../assets/admin/check.svg';
import './admin-notice.pcss';

defineProps<{ tone: 'success' | 'warning' | 'danger'; title: string }>();
</script>

<template>
  <section
    class="admin-notice"
    :data-tone="tone"
    :role="tone === 'danger' ? 'alert' : 'status'"
  >
    <div class="admin-notice__heading">
      <span
        class="admin-notice__icon"
        aria-hidden="true"
      >
        <img
          v-if="tone === 'success'"
          :src="checkIcon"
          alt=""
        >
        <MobileIcon
          v-else
          name="warning"
        />
      </span>
      <h2>{{ title }}</h2>
    </div>
    <div
      v-if="$slots.default"
      class="admin-notice__body"
    >
      <slot />
    </div>
    <div
      v-if="$slots.actions"
      class="admin-notice__actions"
    >
      <slot name="actions" />
    </div>
  </section>
</template>
