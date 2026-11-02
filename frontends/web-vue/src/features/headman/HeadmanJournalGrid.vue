<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { HeadmanJournalLesson, HeadmanJournalReport, HeadmanJournalRosterEntry } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import { dateLabel, lessonTypeCode, lessonTypes, markLabels, markSymbols } from './context';
const props = defineProps<{ lessons: readonly HeadmanJournalLesson[]; reports: ReadonlyMap<number, HeadmanJournalReport>; roster: readonly (readonly [number, string])[]; subjects: readonly { id: number; name: string }[]; compact: boolean; locked: boolean; hasStarted: (lesson: HeadmanJournalLesson) => boolean; canMark: (lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry, status: 'PRESENT' | 'EXCUSED' | 'ABSENT') => boolean }>();
defineEmits<{ choose: [lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry]; audit: [lessonId: number]; mark: [lesson: HeadmanJournalLesson, entry: HeadmanJournalRosterEntry, status: 'PRESENT' | 'EXCUSED' | 'ABSENT'] }>();
type JournalTarget = { userId: number | null; lessonId: number };
const hovered = ref<JournalTarget | null>(null), focused = ref<JournalTarget | null>(null);
const active = computed(() => hovered.value ?? focused.value);
watch(() => [props.lessons, props.roster], () => { hovered.value = null; focused.value = null; });
const days = computed(() => {
  const result: { date: string; count: number }[] = [];
  for (const lesson of props.lessons) {
    const previous = result.at(-1);
    if (previous?.date === lesson.date) previous.count++;
    else result.push({ date: lesson.date, count: 1 });
  }
  return result;
});
const entryFor = (lessonId: number, userId: number) => props.reports.get(lessonId)?.entries.find((entry) => entry.userId === userId);
const automaticAbsence = (entry: HeadmanJournalRosterEntry) => entry.status === 'ABSENT' && ['AUTO', 'AUTO_ABSENT', 'AUTO_SCHEDULER'].includes(entry.source?.toUpperCase() ?? '');
const dayEnd = (index: number) => index < props.lessons.length - 1 && props.lessons[index]?.date !== props.lessons[index + 1]?.date;
function weekday(date: string) {
  return new Intl.DateTimeFormat('ru-RU', { weekday: props.compact ? 'short' : 'long', timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`));
}
const statuses = ['PRESENT', 'EXCUSED', 'ABSENT'] as const;
const typeCode: Readonly<Record<string, string>> = { LECTURE: 'ЛК', PRACTICE: 'ПЗ', LAB: 'ЛЗ' };
</script>
<template>
  <div class="headman-analytics-scroll headman-journal-viewport" tabindex="0" role="region" aria-label="Журнал посещаемости группы" @pointerleave="hovered = null">
    <table class="headman-journal-grid" :style="{ '--journal-columns': lessons.length }">
      <caption class="web-sr-only">Журнал посещаемости. Выбери отметку для просмотра или изменения.</caption>
      <thead>
        <tr><th rowspan="4" scope="col" class="headman-journal-name">Студенты</th><th v-for="(day, index) in days" :key="day.date" :colspan="day.count" scope="colgroup" :class="{ 'is-day-end': index < days.length - 1 }">{{ weekday(day.date) }}</th></tr>
        <tr><th v-for="(day, index) in days" :key="day.date" :colspan="day.count" scope="colgroup" :class="{ 'is-day-end': index < days.length - 1 }">{{ dateLabel(day.date) }}</th></tr>
        <tr><th v-for="(lesson, index) in lessons" :key="lesson.id" :class="{ 'is-current': lesson.status === 'ACTIVE', 'is-day-end': dayEnd(index), 'is-cancelled': lesson.status === 'CANCELLED', 'is-hover-column': active?.lessonId === lesson.id }" scope="col">
          <button type="button" class="headman-journal-subject" :disabled="locked" :title="`${subjects.find(subject => subject.id === lesson.subjectId)?.name ?? 'Предмет недоступен'} · ${dateLabel(lesson.date)} · пара ${lesson.lessonNumber ?? '—'}. Журнал действий`" @focus="focused = { userId: null, lessonId: lesson.id }" @blur="focused = null" @click="$emit('audit', lesson.id)"><span>{{ subjects.find(subject => subject.id === lesson.subjectId)?.name ?? 'Предмет недоступен' }}<template v-if="lesson.status === 'CANCELLED'"> · Отменена</template></span></button>
        </th></tr>
        <tr><th v-for="(lesson, index) in lessons" :key="lesson.id" scope="col" :class="{ 'is-current': lesson.status === 'ACTIVE', 'is-day-end': dayEnd(index), 'is-cancelled': lesson.status === 'CANCELLED', 'is-hover-column': active?.lessonId === lesson.id }" :title="lessonTypes[lessonTypeCode(lesson.lessonType)] ?? 'Тип не указан'"><span class="headman-journal-type">{{ typeCode[lessonTypeCode(lesson.lessonType)] ?? '—' }}</span></th></tr>
      </thead>
      <tbody><tr v-for="[userId, name] in roster" :key="userId" :class="{ 'is-hover-row': active?.userId === userId }"><th scope="row" class="headman-journal-name"><span>{{ name }}</span></th><td v-for="(lesson, index) in lessons" :key="lesson.id" :class="{ 'is-day-end': dayEnd(index), 'is-cancelled': lesson.status === 'CANCELLED', 'is-hover-column': active?.lessonId === lesson.id }" @pointerenter="hovered = { userId, lessonId: lesson.id }" @pointerleave="hovered = null">
        <div v-if="entryFor(lesson.id, userId) && statuses.some(status => canMark(lesson, entryFor(lesson.id, userId)!, status))" class="headman-journal-mark-actions" :class="{ 'has-mark': entryFor(lesson.id, userId)!.status !== 'EMPTY' }" role="group" :aria-label="`Отметка: ${name}, ${dateLabel(lesson.date)}, пара ${lesson.lessonNumber}`">
          <button v-for="status in statuses" :key="status" type="button" class="headman-journal-mark-control" :data-mark-lesson="lesson.id" :data-mark-user="userId" :data-mark-status="status" :disabled="locked || !canMark(lesson, entryFor(lesson.id, userId)!, status)" :aria-pressed="entryFor(lesson.id, userId)!.status === status" :aria-label="`${entryFor(lesson.id, userId)!.status === status ? 'Снять' : 'Поставить'} «${markLabels[status]}»: ${name}`" @focus="focused = { userId, lessonId: lesson.id }" @blur="focused = null" @click="$emit('mark', lesson, entryFor(lesson.id, userId)!, status)"><span class="headman-journal-badge" :class="{ 'is-neutral': entryFor(lesson.id, userId)!.status !== status, 'is-auto': status === 'ABSENT' && automaticAbsence(entryFor(lesson.id, userId)!) }" :data-status="entryFor(lesson.id, userId)!.status === status ? status : undefined" aria-hidden="true">{{ markSymbols[status] }}</span></button>
        </div>
        <button v-else-if="entryFor(lesson.id, userId)" type="button" class="headman-journal-cell" :class="{ 'is-future': !hasStarted(lesson) }" :disabled="locked || !hasStarted(lesson)" :aria-label="`${name}, ${dateLabel(lesson.date)}, пара ${lesson.lessonNumber}: ${markLabels[entryFor(lesson.id, userId)!.status]}${!hasStarted(lesson) ? ' · Пара ещё не началась' : ''}`" @focus="focused = { userId, lessonId: lesson.id }" @blur="focused = null" @click="$emit('choose', lesson, entryFor(lesson.id, userId)!)"><span class="headman-journal-badge" :class="{ 'is-auto': automaticAbsence(entryFor(lesson.id, userId)!) }" :data-status="entryFor(lesson.id, userId)!.status">{{ markSymbols[entryFor(lesson.id, userId)!.status] }}</span></button><span v-else class="web-caption">—</span>
      </td></tr></tbody>
    </table>
  </div>
</template>
