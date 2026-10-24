<script setup lang="ts">
import type { StudentAttendanceMetricSet } from '../../../../mobile-core/src/api/types';
import { percent } from './student-format';
defineProps<{ metrics: StudentAttendanceMetricSet; compact?: boolean; showCount?: boolean }>();
const labels = [
  { key: 'present', label: 'Посещено' },
  { key: 'excused', label: 'Уважительная причина пропуска' },
  { key: 'presentOrExcused', label: 'Посещено и уважительная причина пропуска' },
  { key: 'absent', label: 'Пропущено' },
] as const;
const compactLabels = [
  { key: 'present', label: 'Доля «+»' },
  { key: 'presentOrExcused', label: 'Доля «+» и «у»' },
] as const;
</script>
<template>
  <dl
    class="student-metrics"
    :class="{ 'is-compact': compact, 'has-count': showCount }"
  >
    <div
      v-for="metric in compact ? compactLabels : labels"
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
    <div
      v-if="showCount"
      class="student-metric web-stack"
    >
      <dt>Прошедших пар</dt><dd class="student-metric-value">
        {{ metrics.held }}
      </dd>
    </div>
  </dl>
</template>
