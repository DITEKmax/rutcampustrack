<script setup lang="ts">
import {
  displayPercent,
  historyStatusAccessibleLabel,
  statisticsTypeLabel,
  type StatisticsHistoryStatus,
  type StatisticsTypeCardData,
} from './statistics-view-model';

defineProps<{
  card: StatisticsTypeCardData;
}>();

function historyTone(status: StatisticsHistoryStatus): string {
  return status.toLowerCase().replace('_', '-');
}
</script>

<template>
  <article
    class="statistics-type-card"
    :data-type="card.type"
  >
    <div class="statistics-type-card__pill">
      {{ statisticsTypeLabel(card.type) }}
    </div>
    <div
      class="statistics-type-card__metrics"
      aria-label="Метрики посещаемости"
    >
      <span
        v-for="metric in [
          { key: 'present', label: 'Был', value: card.metrics.present },
          { key: 'excused', label: 'Уважительная причина', value: card.metrics.excused },
          { key: 'absent', label: 'Не был', value: card.metrics.absent },
        ]"
        :key="metric.key"
        class="statistics-type-card__metric"
        :class="`statistics-type-card__metric--${metric.key}`"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ displayPercent(metric.value.percent) }}</strong>
        <span class="statistics-type-card__count"
          >{{ metric.value.count }} из {{ card.metrics.held }}</span
        >
      </span>
    </div>
    <div
      v-if="card.history.length"
      class="statistics-type-card__history"
      role="list"
      :aria-label="`История ${statisticsTypeLabel(card.type)}`"
    >
      <span
        v-for="segment in card.history"
        :key="segment.id"
        class="statistics-type-card__segment"
        :class="`statistics-type-card__segment--${historyTone(segment.status)}`"
        role="listitem"
        :aria-label="historyStatusAccessibleLabel(segment.status)"
      />
    </div>
  </article>
</template>
