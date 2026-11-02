<script setup lang="ts">
import type { HeadmanStatsMetrics } from '../../../../mobile-core/src/features/headman-stats/headman-stats-client';
defineProps<{ metrics: HeadmanStatsMetrics; context: string }>();
const items = [{ key: 'present', label: 'Доля отметок «+»' }, { key: 'presentOrExcused', label: 'Доля отметок «+» и «у»' }, { key: 'excused', label: 'Доля отметок «у»' }, { key: 'absent', label: 'Доля отметок «н»' }] as const;
const percent = (value: number, denominator: number) => denominator ? `${value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })}%` : '—';
</script>
<template>
  <div class="headman-metric-cards">
    <section v-for="item in items" :key="item.key" class="headman-metric-card" :data-metric="item.key">
      <div><h2>{{ item.label }}</h2><p v-if="context" class="web-caption">{{ context }}</p></div>
      <div class="headman-donut" :aria-label="`${item.label}: ${percent(metrics[item.key].percent, metrics[item.key].denominator)}, ${metrics[item.key].numerator} из ${metrics[item.key].denominator}`" role="img">
        <svg viewBox="0 0 100 100" aria-hidden="true"><circle cx="50" cy="50" r="42" class="headman-donut-track" /><circle v-if="metrics[item.key].denominator" cx="50" cy="50" r="42" pathLength="100" :stroke-dasharray="`${Math.max(0, Math.min(100, metrics[item.key].percent))} 100`" transform="rotate(-90 50 50)" class="headman-donut-value" /></svg>
        <div><strong>{{ percent(metrics[item.key].percent, metrics[item.key].denominator) }}</strong><span>{{ metrics[item.key].numerator }} / {{ metrics[item.key].denominator }}</span></div>
      </div>
    </section>
  </div>
</template>
