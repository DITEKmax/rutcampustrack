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
function instant(value: string | null) {
  if (!value || !Number.isFinite(Date.parse(value))) return null;
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow' }).format(new Date(value));
}
</script>
<template>
  <article class="headman-flow-card headman-request-card">
    <div class="headman-heading"><div class="web-stack"><h2>Заявка №{{ request.id }} · {{ request.studentName ?? 'Имя недоступно' }}</h2></div><div class="headman-actions"><span class="headman-flow-status" :data-status="request.status">{{ statuses[request.status] }}</span><button type="button" class="web-button web-icon-button headman-request-expand" :disabled="locked" :aria-expanded="expanded" :aria-label="`${expanded ? 'Свернуть' : 'Раскрыть'} заявку № ${request.id}`" @click="$emit('toggle')"><MobileIcon name="chevron-down" /></button></div></div>
    <p>{{ kindAndReason(request) }}</p><p v-if="request.comment" class="headman-flow-description">{{ request.comment }}</p>
    <p v-if="request.decisionComment" class="headman-flow-description">Решение: {{ request.decisionComment }}</p>
    <div v-if="expanded" class="headman-request-detail"><slot /></div>
    <p class="headman-request-meta">Пар: {{ request.lessonCount }}<template v-if="request.coverageStart"> · {{ dateLabel(request.coverageStart) }}<template v-if="request.coverageEnd && request.coverageEnd !== request.coverageStart"> — {{ dateLabel(request.coverageEnd) }}</template></template><template v-if="request.alreadyMarkedCount"> · Уже отмечено {{ request.alreadyMarkedCount }}</template><template v-if="instant(request.createdAt)"> · Подана {{ instant(request.createdAt) }} МСК</template><template v-if="instant(request.decisionAt)"> · Решение {{ instant(request.decisionAt) }} МСК</template></p>
  </article>
</template>
