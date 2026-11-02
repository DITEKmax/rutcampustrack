<script setup lang="ts">
import { computed } from 'vue';
import type { HeadmanStatsTrendPoint } from '../../../../mobile-core/src/features/headman-stats/headman-stats-client';
import StudentTrend from '../student/StudentTrend.vue';
import '../student/student.pcss';
const props = defineProps<{ points: readonly HeadmanStatsTrendPoint[]; from?: string | null; to?: string | null }>();
// Adapt only fields consumed by the shared renderer; no aggregate or missing series is invented.
const points = computed(() => [...props.points].sort((a, b) => a.from.localeCompare(b.from)).map(point => ({
  id: point.key, label: point.label, dateFrom: point.from, dateTo: point.to,
  state: point.present.denominator > 0 ? 'DATA' as const : 'NO_DATA' as const,
  metrics: { held: point.present.denominator, present: { count: point.present.numerator, percent: point.present.percent }, presentOrExcused: { count: point.presentOrExcused.numerator, percent: point.presentOrExcused.percent } },
})));
</script>
<template><StudentTrend class="headman-trend" area :points="points" count-label="Отметок учтено" /></template>
