<script setup lang="ts">
import { computed } from 'vue';
import type { HeadmanRequestDetail, HeadmanRequestLesson } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client';
import { storedDecisionRows } from '../../../../mobile-core/src/features/headman-requests/request-presentation';
import { dateLabel, lessonTypes, lessonTypeCode, markLabels, sourceLabel } from './context';
import WebSelect from '../../shared/WebSelect.vue';
const props = defineProps<{ detail: HeadmanRequestDetail; affected: readonly HeadmanRequestLesson[]; excluded: readonly string[]; busy: boolean; pending: boolean; decisionAvailable: boolean; ownRequest: boolean; writable: boolean; proposed: 'APPROVED' | 'REJECTED' | null; reason: string }>();
const emit = defineEmits<{ 'update:proposed': [value: 'APPROVED' | 'REJECTED' | null]; 'update:reason': [value: string]; exclude: [id: string, checked: boolean]; download: [id: string, name: string | null]; refresh: []; decide: [] }>();
const choice = computed({ get: () => props.proposed ?? '', set: (value: string) => { if (value === 'APPROVED' || value === 'REJECTED') emit('update:proposed', value); else emit('update:proposed', null); } });
const reasonModel = computed({ get: () => props.reason, set: (value: string) => emit('update:reason', value) });
const receipt = computed(() =>
  props.detail.summary.status !== 'PENDING' && props.detail.decisionSelection
    ? storedDecisionRows(props.detail)
    : null,
);
const originalSeparate = computed(() =>
  !!props.detail.currentAffectedLessons || !!props.detail.decisionSelection?.beforeLessons,
);
const instantFormatter = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow',
});
function instant(value: string | null) {
  return value && Number.isFinite(Date.parse(value)) ? instantFormatter.format(new Date(value)) : null;
}
</script>
<template>
  <div class="web-stack">
    <div class="headman-request-context">
      <h3>Заявка №{{ detail.summary.id }}</h3>
      <dl class="headman-definition">
        <div><dt>Пар в заявке</dt><dd>{{ detail.summary.lessonCount }}</dd></div>
        <div v-if="detail.summary.alreadyMarkedCount"><dt>Уже отмечено</dt><dd>{{ detail.summary.alreadyMarkedCount }}</dd></div>
        <div v-if="instant(detail.summary.createdAt)"><dt>Подана</dt><dd>{{ instant(detail.summary.createdAt) }} МСК</dd></div>
      </dl>
      <p
        v-if="detail.summary.comment"
        class="headman-flow-description"
      >
        {{ detail.summary.comment }}
      </p>
      <div
        v-if="detail.attachments.length"
        class="headman-request-files"
      >
        <button
          v-for="attachment in detail.attachments"
          :key="attachment.id" type="button" class="web-button" :disabled="busy" @click="$emit('download', attachment.id, attachment.name)"
        >
          {{ attachment.name ?? 'Подтверждение' }} · {{ Math.ceil(attachment.size / 1024) }} КБ
        </button>
      </div>
    </div>
    <section class="headman-request-section">
      <h3>Пары заявки</h3>
      <div
        v-if="affected.length"
        class="headman-flow-scroll headman-request-lessons" role="region" aria-label="Пары заявки" tabindex="0"
      >
        <table class="headman-request-table">
          <thead>
            <tr>
              <th scope="col">
                Дата / пара
              </th>
              <th scope="col">
                Предмет
              </th>
              <th scope="col">
                Отметка
              </th>
              <th
                v-if="detail.summary.kind === 'EXCUSE' && decisionAvailable"
                scope="col"
              >
                Исключить из одобрения
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(lesson, index) in affected"
              :key="lesson.occurrenceId ?? index"
            >
              <td>{{ dateLabel(lesson.date) }} · {{ lesson.lessonNumber }}</td>
              <td>{{ lesson.subjectName ?? 'Предмет недоступен' }} · {{ lessonTypes[lessonTypeCode(lesson.subjectType)] ?? 'Тип недоступен' }}</td>
              <td>
                {{ markLabels[lesson.attendanceStatus ?? 'EMPTY'] ?? 'Статус недоступен' }}
                <small
                  v-if="lesson.attendanceSource"
                  class="web-caption"
                >{{ sourceLabel(lesson.attendanceSource) }}</small>
              </td>
              <td v-if="detail.summary.kind === 'EXCUSE' && decisionAvailable">
                <label
                  v-if="lesson.occurrenceId"
                  class="headman-flow-toggle"
                >
                  <input type="checkbox" :checked="excluded.includes(lesson.occurrenceId)" :disabled="busy || pending || !writable" @change="$emit('exclude', lesson.occurrenceId, ($event.target as HTMLInputElement).checked)" />
                  <span>Исключить<span class="web-sr-only"> {{ lesson.subjectName }}, {{ dateLabel(lesson.date) }}, пара {{ lesson.lessonNumber }}</span></span>
                </label>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p
        v-else
        class="web-caption"
      >
        Нет данных о парах.
      </p>
      <details
        v-if="originalSeparate && detail.lessons.length"
        class="headman-disclosure"
      >
        <summary>Исходный состав заявки</summary>
        <ul class="headman-list">
          <li
            v-for="(lesson, index) in detail.lessons"
            :key="index"
          >
            {{ dateLabel(lesson.date) }} · пара {{ lesson.lessonNumber ?? '—' }} · {{ lesson.subjectName ?? 'Предмет недоступен' }} · {{ lessonTypes[lessonTypeCode(lesson.subjectType)] ?? 'Тип недоступен' }}
          </li>
        </ul>
      </details>
    </section>
    <section
      v-if="detail.summary.status === 'PENDING' || receipt || detail.summary.decisionAt || detail.summary.decisionComment"
      class="headman-request-section"
    >
      <h3>Решение</h3>
      <p
        v-if="instant(detail.summary.decisionAt)"
        class="web-caption"
      >
        {{ instant(detail.summary.decisionAt) }} МСК
      </p>
      <p
        v-if="detail.summary.decisionComment"
        class="headman-flow-description"
      >
        {{ detail.summary.decisionComment }}
      </p>
      <template v-if="receipt">
        <p
          v-if="detail.decisionSelection"
          class="web-caption"
        >
          Пар в решении: {{ detail.decisionSelection.includedLessons.length }}. Исключено: {{ detail.decisionSelection.excludedLessons.length }}.
        </p>
        <details
          v-if="detail.decisionSelection?.beforeLessons?.length"
          class="headman-disclosure"
        >
          <summary>Отметки перед решением</summary>
          <ul class="headman-list">
            <li
              v-for="(row, index) in receipt.before"
              :key="index"
            >
              {{ dateLabel(row.lesson.date) }} · пара {{ row.lesson.lessonNumber ?? '—' }} · {{ row.lesson.subjectName ?? 'Предмет недоступен' }} · {{ markLabels[row.mark ?? 'EMPTY'] ?? 'Статус недоступен' }}
            </li>
          </ul>
        </details>
        <ul
          v-if="receipt.after.length"
          class="headman-list"
          aria-label="Пары в сохранённом решении"
        >
          <li
            v-for="(row, index) in receipt.after"
            :key="index"
          >
            {{ dateLabel(row.lesson.date) }} · пара {{ row.lesson.lessonNumber ?? '—' }} · {{ row.lesson.subjectName ?? 'Предмет недоступен' }} · {{ row.label }} · {{ markLabels[row.mark ?? 'EMPTY'] ?? 'Статус недоступен' }}
            <small
              v-if="row.lesson.attendanceSource"
              class="web-caption"
            > · {{ sourceLabel(row.lesson.attendanceSource) }}</small>
          </li>
        </ul>
      </template>
      <p
        v-if="detail.summary.status === 'PENDING' && !decisionAvailable"
        class="web-notice"
      >
        {{ ownRequest ? 'Свою заявку обрабатывает другой уполномоченный участник группы.' : 'Решение недоступно: сервер не подтвердил текущий состав и версию заявки.' }}
        <button v-if="!ownRequest" type="button" class="web-button" :disabled="busy || pending" @click="$emit('refresh')">Обновить заявку</button>
      </p>
      <form
        v-if="decisionAvailable && !pending"
        class="headman-request-decision web-stack" @submit.prevent="$emit('decide')"
      >
        <WebSelect v-model="choice" label="Решение" :options="[{ value: '', label: 'Выбери решение', disabled: true }, { value: 'APPROVED', label: 'Одобрить' }, { value: 'REJECTED', label: 'Отклонить' }]" :disabled="busy" required />
        <label
          v-if="proposed === 'REJECTED'"
          class="web-field"
        >Причина отказа<textarea v-model="reasonModel" required maxlength="300" :disabled="busy" /></label>
        <p
          v-if="proposed === 'APPROVED'"
          class="web-caption"
        >
          Пар в решении: {{ affected.length - excluded.length }}. Исключено: {{ excluded.length }}. Уже стоящая отметка «Был» сохраняется.
        </p>
        <button class="web-button web-button-primary" :disabled="busy || !proposed">Подтвердить решение</button>
      </form>
    </section>
  </div>
</template>
