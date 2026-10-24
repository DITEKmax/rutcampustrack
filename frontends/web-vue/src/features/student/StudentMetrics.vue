<script setup lang="ts">
import type { StudentAttendanceMetricSet } from '../../../../mobile-core/src/api/types';
import { percent } from './student-format';
defineProps<{ metrics: StudentAttendanceMetricSet; compact?: boolean }>();
const labels = [
  { key: 'present', label: 'Доля «+»' },
  { key: 'presentOrExcused', label: 'Доля «+» и «у»' },
  { key: 'excused', label: 'Уважительные причины' },
  { key: 'absent', label: 'Отсутствия' },
] as const;
</script>
<template>
  <dl
    class="student-metrics"
    :class="{ 'is-compact': compact }"
  >
    <div
      v-for="metric in compact ? labels.slice(0, 2) : labels"
      :key="metric.key"
      class="student-metric web-stack"
    >
      <dt>{{ metric.label }}</dt>
      <dd class="student-metric-value">
        {{ percent(metrics[metric.key].percent) }}
      </dd>
      <dd class="web-caption">
        {{ metrics[metric.key].count }} / {{ metrics.held }}
      </dd>
    </div>
  </dl>
</template>
