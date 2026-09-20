<script setup lang="ts">
import {
  displayPercent,
  historyStatusAccessibleLabel,
  statisticsTypeLabel,
  type StatisticsHistoryStatus,
  type StatisticsTypeCardData,
} from './statistics-view-model'

defineProps<{
  card: StatisticsTypeCardData
}>()

function historyTone(status: StatisticsHistoryStatus): string {
  return status.toLowerCase().replace('_', '-')
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
    <h3>Пар {{ card.metrics.held }}/{{ card.metrics.planned }}</h3>
    <div
      class="statistics-type-card__metrics"
      aria-label="Метрики посещаемости"
    >
      <span class="statistics-type-card__metric statistics-type-card__metric--present">
        <i aria-hidden="true" />{{ displayPercent(card.metrics.present.percent) }}
        <span class="statistics-visually-hidden"> присутствие, {{ card.metrics.present.count }}</span>
      </span>
      <span class="statistics-type-card__metric statistics-type-card__metric--excused">
        <i aria-hidden="true" />{{ displayPercent(card.metrics.excused.percent) }}
        <span class="statistics-visually-hidden"> уважительная причина, {{ card.metrics.excused.count }}</span>
      </span>
      <span class="statistics-type-card__metric statistics-type-card__metric--absent">
        <i aria-hidden="true" />{{ displayPercent(card.metrics.absent.percent) }}
        <span class="statistics-visually-hidden"> отсутствие, {{ card.metrics.absent.count }}</span>
      </span>
    </div>
    <div
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
