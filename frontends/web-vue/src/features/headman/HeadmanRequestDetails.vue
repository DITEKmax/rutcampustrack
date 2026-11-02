<script setup lang="ts">
import { computed } from 'vue';
import type { HeadmanRequestDetail, HeadmanRequestLesson } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client';
import { dateLabel, lessonTypes, lessonTypeCode, markLabels } from './context';
import WebSelect from '../../shared/WebSelect.vue';
const props = defineProps<{ detail: HeadmanRequestDetail; affected: readonly HeadmanRequestLesson[]; excluded: readonly string[]; busy: boolean; pending: boolean; decisionAvailable: boolean; ownRequest: boolean; writable: boolean; proposed: 'APPROVED' | 'REJECTED' | null; reason: string }>();
const emit = defineEmits<{ 'update:proposed': [value: 'APPROVED' | 'REJECTED' | null]; 'update:reason': [value: string]; exclude: [id: string, checked: boolean]; download: [id: string, name: string | null]; refresh: []; decide: [] }>();
const choice = computed({ get: () => props.proposed ?? '', set: (value: string) => { if (value === 'APPROVED' || value === 'REJECTED') emit('update:proposed', value); else emit('update:proposed', null); } });
const reasonModel = computed({ get: () => props.reason, set: (value: string) => emit('update:reason', value) });
</script>
<template>
  <div class="web-stack">
    <div class="headman-flow-scroll headman-request-lessons" role="region" aria-label="Пары в заявке" tabindex="0"><table class="headman-request-table"><thead><tr><th>Дата / пара</th><th>Предмет</th><th>Отметка</th><th v-if="detail.summary.kind === 'EXCUSE' && decisionAvailable">Исключить из одобрения</th></tr></thead><tbody><tr v-for="(lesson, index) in affected" :key="lesson.occurrenceId ?? index"><td>{{ dateLabel(lesson.date) }} · {{ lesson.lessonNumber }}</td><td>{{ lesson.subjectName }} · {{ lessonTypes[lessonTypeCode(lesson.subjectType)] ?? 'Тип недоступен' }}</td><td>{{ markLabels[lesson.attendanceStatus ?? 'EMPTY'] ?? 'Статус недоступен' }}</td><td v-if="detail.summary.kind === 'EXCUSE' && decisionAvailable"><label v-if="lesson.occurrenceId" class="headman-flow-toggle"><input type="checkbox" :checked="excluded.includes(lesson.occurrenceId)" :disabled="busy || pending || !writable" @change="$emit('exclude', lesson.occurrenceId, ($event.target as HTMLInputElement).checked)" /><span>Исключить</span></label></td></tr></tbody></table></div>
    <div v-if="detail.attachments.length" class="headman-request-files"><button v-for="attachment in detail.attachments" :key="attachment.id" type="button" class="web-button" :disabled="busy" @click="$emit('download', attachment.id, attachment.name)">{{ attachment.name ?? 'Подтверждение' }} · {{ Math.ceil(attachment.size / 1024) }} КБ</button></div>
    <p v-if="detail.summary.status === 'PENDING' && !decisionAvailable" class="web-notice">{{ ownRequest ? 'Свою заявку обрабатывает другой уполномоченный участник группы.' : 'Решение недоступно: сервер не подтвердил текущий состав и версию заявки.' }}<button v-if="!ownRequest" type="button" class="web-button" :disabled="busy || pending" @click="$emit('refresh')">Обновить заявку</button></p>
    <form v-if="decisionAvailable && !pending" class="headman-request-decision web-stack" @submit.prevent="$emit('decide')">
      <WebSelect v-model="choice" label="Решение" :options="[{ value: '', label: 'Выбери решение', disabled: true }, { value: 'APPROVED', label: 'Одобрить' }, { value: 'REJECTED', label: 'Отклонить' }]" :disabled="busy" required />
      <label v-if="proposed === 'REJECTED'" class="web-field">Причина отказа<textarea v-model="reasonModel" required maxlength="300" :disabled="busy" /></label>
      <p v-if="proposed === 'APPROVED'" class="web-caption">Пар в решении: {{ affected.length - excluded.length }}. Исключено: {{ excluded.length }}. Уже стоящая отметка «Был» сохраняется.</p>
      <button class="web-button web-button-primary" :disabled="busy || !proposed">Подтвердить решение</button>
    </form>
  </div>
</template>
