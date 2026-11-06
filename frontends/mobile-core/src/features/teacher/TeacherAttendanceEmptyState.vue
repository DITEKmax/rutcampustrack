<script setup lang="ts">
import emptyCalendar from '../../assets/headman-schedule-empty-day.svg?raw';
import emptyFilter from './teacher-stats-empty-filter.svg?raw';
import './teacher-attendance-empty-state.pcss';

const messages = {
  types: 'Выбери один или несколько типов занятий.',
  period: 'За выбранный период занятий нет.',
  'past-period': 'В выбранном периоде прошедших занятий нет.',
} as const;
defineProps<{ reason: keyof typeof messages }>();
</script>

<template>
  <div
    class="teacher-attendance-empty"
    role="status"
  >
    <!-- eslint-disable vue/no-v-html -- locally bundled, themed illustrations -->
    <span
      class="teacher-attendance-empty__art"
      aria-hidden="true"
      v-html="reason === 'types' ? emptyFilter : emptyCalendar"
    />
    <!-- eslint-enable vue/no-v-html -->
    <p>
      {{ messages[reason] }}
    </p>
  </div>
</template>
