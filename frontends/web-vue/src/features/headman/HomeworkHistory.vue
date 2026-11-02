<script setup lang="ts">
import type { HeadmanHomeworkHistory } from '../../../../mobile-core/src/features/homework/headman-homework-client';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import LinkifiedText from '../../shared/LinkifiedText.vue';
import { dateLabel, lessonTypes } from './context';

defineProps<{ title: string; entries: readonly HeadmanHomeworkHistory[] | null; loading: boolean; error: string | null }>();
defineEmits<{ close: [] }>();
const labels: Readonly<Record<string, string>> = {
  title: 'Заголовок', description: 'Описание', link: 'Ссылка', bindingMode: 'Размещение',
  lessonDate: 'Дата', lessonNumber: 'Номер пары', lessonType: 'Тип занятия',
};
const actionLabels: Readonly<Record<string, string>> = { CREATED: 'Опубликовано', EDITED: 'Изменено', UPDATED: 'Изменено', DELETED: 'Удалено', ARCHIVED: 'В архиве' };
function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : value === null || value === undefined ? {} : { value };
}
function safeLink(value: unknown) {
  if (typeof value !== 'string' || !value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? value : null; } catch { return null; }
}
function cell(value: Record<string, unknown>, key: string) {
  if (!Object.prototype.hasOwnProperty.call(value, key)) return { text: 'Нет записи', link: null };
  const item = value[key];
  if (item === null || item === '') return { text: 'Не указано', link: null };
  const link = key === 'link' ? safeLink(item) : null;
  const text = key === 'bindingMode' && (item === 'LESSON' || item === 'DATE') ? item === 'LESSON' ? 'На пару' : 'На дату'
    : key === 'lessonDate' && typeof item === 'string' ? dateLabel(item)
      : key === 'lessonType' && typeof item === 'string' ? lessonTypes[item] ?? item
        : typeof item === 'object' ? JSON.stringify(item, null, 2) : String(item);
  return { text, link };
}
function fields(entry: HeadmanHomeworkHistory) {
  const before = record(entry.before), after = record(entry.after);
  return Object.keys(labels).filter(key => Object.prototype.hasOwnProperty.call(before, key) || Object.prototype.hasOwnProperty.call(after, key)).map(key => ({ key, label: labels[key] ?? key, before: cell(before, key), after: cell(after, key) }));
}
function timestamp(value: string) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toLocaleString('ru-RU', { timeZone: 'Europe/Moscow', dateStyle: 'long', timeStyle: 'short' }) + ' МСК' : value;
}
</script>
<template>
  <section class="headman-homework-history headman-panel web-stack" aria-label="История задания">
    <div class="headman-heading"><h3>История · {{ title }}</h3><button type="button" class="web-button web-icon-button" aria-label="Закрыть историю задания" @click="$emit('close')"><MobileIcon name="close" /></button></div>
    <p v-if="loading" class="web-caption" role="status">Загрузка истории…</p>
    <p v-else-if="error" class="web-error" role="alert">{{ error }}</p>
    <ol v-else-if="entries?.length" class="headman-homework-events">
      <li v-for="entry in entries" :key="entry.id">
        <header class="headman-homework-event-header"><div><strong>{{ actionLabels[entry.action] ?? entry.action }}</strong><span class="web-caption">Версия {{ entry.revision }}</span></div><div><time :datetime="entry.occurredAt">{{ timestamp(entry.occurredAt) }}</time><span>{{ entry.actorDisplayName ?? (entry.actorId ? `Автор №${entry.actorId}` : 'Система') }}</span></div></header>
        <div v-if="fields(entry).length" class="headman-homework-diff" role="region" aria-label="Содержимое до и после изменения" tabindex="0">
          <table><thead><tr><th scope="col">Поле</th><th scope="col">До изменения</th><th scope="col">После изменения</th></tr></thead><tbody><tr v-for="field in fields(entry)" :key="field.key"><th scope="row">{{ field.label }}</th><td v-for="(value, index) in [field.before, field.after]" :key="index"><a v-if="value.link" :href="value.link" :title="value.text" target="_blank" rel="noopener noreferrer">Открыть ссылку<span class="web-sr-only"> (в новой вкладке)</span></a><LinkifiedText v-else :text="value.text" /></td></tr></tbody></table>
        </div>
        <p v-else class="web-caption">Нет записи.</p>
      </li>
    </ol>
    <p v-else class="web-caption">История пока пуста.</p>
  </section>
</template>
