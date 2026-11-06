<script setup lang="ts">
import type { HeadmanRequestSummary } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import { dateLabel, reasonLabel } from './context';
defineProps<{ request: HeadmanRequestSummary; expanded: boolean; locked: boolean }>();
defineEmits<{ toggle: [] }>();
const statuses = { PENDING: 'На рассмотрении', APPROVED: 'Одобрена', REJECTED: 'Отклонена', CANCELLED: 'Отозвана' };
function kindAndReason(request: HeadmanRequestSummary) {
  const kind = request.kind === 'EXCUSE' ? 'Уважительная причина' : 'Апелляция отметки';
  if (!request.reason) return kind;
  const reason = reasonLabel(request.reason);
  const friendly = reason !== request.reason || ['Болезнь', 'Медосмотр', 'Соревнование', 'Семейные обстоятельства', 'Повестка', 'Приказ университета', 'Освобождение', 'Другое'].includes(reason);
  return `${kind}: ${friendly ? reason.charAt(0).toLocaleLowerCase('ru') + reason.slice(1) : reason}`;
}
</script>

<template>
  <article class="headman-flow-card headman-request-card">
    <div class="headman-heading">
      <div class="headman-request-summary">
        <h2>{{ request.studentName ?? 'Имя недоступно' }}</h2>
        <p>{{ kindAndReason(request) }}</p>
        <p class="web-caption">
          {{ dateLabel(request.coverageStart ?? request.coverageEnd) }}<template v-if="request.coverageStart && request.coverageEnd && request.coverageEnd !== request.coverageStart"> — {{ dateLabel(request.coverageEnd) }}</template>
        </p>
      </div>
      <div class="headman-actions">
        <span class="headman-flow-status" :data-status="request.status">{{ statuses[request.status] }}</span>
        <button type="button" class="web-button web-icon-button headman-request-expand" :disabled="locked" :aria-expanded="expanded" :aria-label="(expanded ? 'Свернуть' : 'Раскрыть') + ' заявку: ' + (request.studentName ?? 'Имя недоступно')" @click="$emit('toggle')">
          <MobileIcon name="chevron-down" />
        </button>
      </div>
    </div>
    <div v-if="expanded" class="headman-request-detail"><slot /></div>
  </article>
</template>
