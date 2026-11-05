<script setup lang="ts">
import type { HeadmanStatsPersonalMetrics } from './headman-stats-client';
import { headmanStatsPercent } from './headman-stats-view-model';

defineProps<{ label: string; metrics: HeadmanStatsPersonalMetrics }>();

const rows = [
  { key: 'present', markers: [{ kind: 'present', text: '+' }], label: 'Присутствовал' },
  {
    key: 'presentOrExcused',
    markers: [{ kind: 'present', text: '+' }, { kind: 'excused', text: 'у' }],
    label: 'Присутствовал или отсутствовал по уважительной причине',
  },
  { key: 'excused', markers: [{ kind: 'excused', text: 'у' }], label: 'Уважительная причина' },
  { key: 'absent', markers: [{ kind: 'absent', text: 'н' }], label: 'Отсутствовал' },
] as const;
</script>

<template>
  <table class="headman-stats-mobile__table headman-stats-mobile__metric-table">
    <caption class="statistics-visually-hidden">{{ label }}</caption>
    <thead>
      <tr>
        <th scope="col">Статус</th>
        <th scope="col">Количество</th>
        <th scope="col">Доля</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.key">
        <th scope="row">
          <span class="statistics-visually-hidden">{{ row.label }}</span>
          <span
            v-for="marker in row.markers"
            :key="marker.kind"
            :class="'headman-stats-mobile__status headman-stats-mobile__status--' + marker.kind"
            aria-hidden="true"
          >{{ marker.text }}</span>
        </th>
        <td>{{ metrics[row.key].numerator }} из {{ metrics[row.key].denominator }}</td>
        <td>{{ headmanStatsPercent(metrics[row.key]) }}</td>
      </tr>
    </tbody>
  </table>
</template>
