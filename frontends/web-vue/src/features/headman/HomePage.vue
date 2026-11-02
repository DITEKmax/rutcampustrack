<script setup lang="ts">
import { computed, nextTick, ref, type ComponentPublicInstance } from 'vue';
import { webSession } from '../../auth/session';
import type { HeadmanJournalLesson, HeadmanJournalRosterEntry } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import type { HeadmanLessonAction } from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions';
import { actionLabel, dateLabel, lessonTypes, lessonTypeCode, sourceLabel, useHeadmanFeature } from './context';
import { useHeadmanHome } from './use-headman-home';
import { lessonActionDetails } from './lesson-action-details';
import HeadmanPage from './HeadmanPage.vue';
import HeadmanHomeMark from './HeadmanHomeMark.vue';
import StudentPeriodPager from '../student/StudentPeriodPager.vue';
import LinkifiedText from '../../shared/LinkifiedText.vue';
import WebSelect from '../../shared/WebSelect.vue';
import '../student/student.pcss';
import './headman-home.pcss';

const feature = useHeadmanFeature();
const home = useHeadmanHome(feature);
const { interactionLocked, markCanAccept, markBusy, markError, markNotice, markPending, excuseDraft, excuseType, blockageBusy, blockageError, blockageNotice, blockagePending, canChangeBlockage, loading, error, lessons, ongoing, currentReport, currentError, manualId, manualLesson, manualReport, manualLoading, manualError, auditId, auditLesson, audit, cursor, auditLoading, auditError, homeworks, homeworkError, progress } = home;
const subjectName = (id: number | null) => home.subjects.value.find((subject) => subject.id === id)?.name ?? 'Предмет недоступен';
const timeRange = (lesson: HeadmanJournalLesson) => `${lesson.startTime?.slice(0, 5) ?? '—'}–${lesson.endTime?.slice(0, 5) ?? '—'}`;
const lessonLabel = (lesson: HeadmanJournalLesson | null) => lesson ? `${subjectName(lesson.subjectId)} · ${timeRange(lesson)}` : 'На сегодня пар нет';
const manualIndex = computed(() => lessons.value.findIndex((lesson) => lesson.id === manualId.value));
const auditIndex = computed(() => lessons.value.findIndex((lesson) => lesson.id === auditId.value));
const roster = computed(() => [...(manualReport.value?.entries ?? [])].sort((a, b) => a.displayName.localeCompare(b.displayName, 'ru')));
const requestGroups = computed(() => [
  { kind: 'EXCUSE', label: 'Заявки на «у»', page: home.excuses.value, items: home.todayExcuses.value, error: home.excuseError.value },
  { kind: 'LATE_CHECKIN', label: 'Поздние отметки «н»', page: home.late.value, items: home.todayLate.value, error: home.lateError.value },
] as const);
const statuses = ['PRESENT', 'EXCUSED', 'ABSENT'] as const;
const reasonOptions = [
  { value: '', label: 'Выбери причину', disabled: true },
  { value: 'ILLNESS', label: 'Болезнь' }, { value: 'MEDICAL_EXAMINATION', label: 'Медосмотр' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Соревнование' }, { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' }, { value: 'UNIVERSITY_ORDER', label: 'Приказ университета' }, { value: 'EXEMPTION', label: 'Освобождение' }, { value: 'OTHER', label: 'Другое' },
];
const excuseForm = ref<HTMLFormElement | null>(null);
function setExcuseForm(element: Element | ComponentPublicInstance | null) {
  excuseForm.value = element as HTMLFormElement | null;
}
const rosterRoot = ref<HTMLElement | null>(null);
async function chooseMark(entry: HeadmanJournalRosterEntry, status: typeof statuses[number]) {
  await home.chooseMark(entry, status);
  if (excuseDraft.value?.entry.userId === entry.userId && !markPending.value) {
    await nextTick();
    excuseForm.value?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
  }
}
async function closeExcuse() {
  const userId = excuseDraft.value?.entry.userId;
  home.closeExcuse();
  await nextTick();
  if (userId) rosterRoot.value?.querySelector<HTMLButtonElement>(`button[data-mark-user="${userId}"][data-mark-status="EXCUSED"]`)?.focus({ preventScroll: true });
}
const markTime = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow' });
const fullMarkTime = new Intl.DateTimeFormat('ru-RU', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'Europe/Moscow' });
function timestamp(entry: HeadmanJournalRosterEntry) {
  if (entry.status === 'EMPTY' || !entry.timestamp || !Number.isFinite(Date.parse(entry.timestamp))) return null;
  return new Date(entry.timestamp);
}
function instant(value: string) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow' }).format(date) : '—';
}
function created(value: string | null) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Moscow' }).format(date) : null;
}
function eventText(action: HeadmanLessonAction) {
  const actor = action.actor?.displayName ?? 'Система';
  const student = action.student && action.student.userId !== action.actor?.userId ? ` · ${action.student.displayName ?? 'Студент'}` : '';
  const details = lessonActionDetails(action);
  return `${actor} · ${actionLabel(action.action)}${student}${details.length ? ` · ${details.join(' · ')}` : ''}${action.outcome === 'REJECTED' ? ' · отклонено' : ''}`;
}
</script>

<template>
  <HeadmanPage class="headman-home" :title="`Главная — ${webSession.snapshot.value?.groupLabel ?? 'группа'}`" hide-title :error="error" @reload="home.load">
    <div class="headman-home-counters">
      <section class="headman-home-card headman-home-current" :class="{ 'is-current': ongoing }" aria-label="Текущая пара">
        <template v-if="ongoing">
          <div class="headman-home-current-main">
            <p>{{ timeRange(ongoing) }}</p>
            <div><h2>{{ subjectName(ongoing.subjectId) }}</h2><p>{{ lessonTypes[lessonTypeCode(ongoing.lessonType)] ?? 'Тип не указан' }}<template v-if="ongoing.room"> · {{ ongoing.room }}</template></p></div>
          </div>
          <progress v-if="progress !== null" class="headman-home-progress" max="100" :value="progress" aria-label="Ход текущей пары" />
        </template>
        <p v-else role="status">{{ loading ? 'Загрузка текущей пары…' : currentError ? 'Не удалось загрузить текущую пару.' : 'Сейчас пары нет.' }}</p>
      </section>
      <section v-for="[status, label] in [['PRESENT', 'Присутствуют «+»'], ['EXCUSED', 'Уважительная причина «у»']] as const" :key="status" class="headman-home-card headman-home-counter" :aria-label="label">
        <h2>{{ label }}</h2>
        <p v-if="currentError" class="web-error" role="alert">{{ currentError }}</p>
        <p v-if="ongoing && currentReport" class="headman-home-ratio" role="status" :aria-label="`${label}: ${currentReport.entries.filter(entry => entry.status === status).length} из ${currentReport.entries.length}. Текущая пара: ${subjectName(ongoing.subjectId)}`"><strong>{{ currentReport.entries.filter(entry => entry.status === status).length }}</strong><span>/{{ currentReport.entries.length }}</span></p>
        <p v-else-if="!currentError" class="web-caption">{{ loading ? 'Загрузка…' : 'Нет идущей пары' }}</p>
      </section>
    </div>

    <div class="headman-home-work">
      <section class="headman-home-panel headman-home-manual-panel" aria-label="Ручная отметка">
        <h2 class="web-sr-only">Ручная отметка</h2>
        <nav aria-label="Пара в ручной отметке">
          <StudentPeriodPager :label="lessonLabel(manualLesson)" unit="период ручной отметки" :previous-disabled="manualIndex <= 0" :next-disabled="manualIndex < 0 || manualIndex >= lessons.length - 1" :busy="loading || manualLoading || interactionLocked" @step="home.step('manual', $event)" @reset="home.reset('manual')" />
        </nav>
        <div class="headman-home-manual-controls">
        <div v-if="feature.isHeadman && manualLesson" class="headman-home-blockage">
          <button v-if="typeof manualLesson.blockedByHeadman === 'boolean'" type="button" class="headman-home-blockage-switch" role="switch" aria-label="Только ручная отметка" :aria-checked="manualLesson.blockedByHeadman" :aria-busy="blockageBusy" :aria-describedby="blockageError ? 'headman-home-blockage-error' : undefined" :disabled="!canChangeBlockage" @click="home.changeBlockage">
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="7" width="10" height="7" rx="2" /><path d="M5 7V5a3 3 0 0 1 6 0v2M8 10v1" /></svg>
            <span>Только ручная отметка</span><span class="headman-home-switch-track" aria-hidden="true"><span /></span><span class="headman-home-switch-state">{{ manualLesson.blockedByHeadman ? 'Включено' : 'Выключено' }}</span>
          </button>
          <p v-else class="web-caption">Состояние блокировки недоступно.</p>
          <p v-if="blockageError" id="headman-home-blockage-error" class="web-error" role="alert">{{ blockageError }} <button v-if="blockagePending" type="button" class="web-button" :disabled="blockageBusy" @click="home.reconcileBlockage">Проверить состояние</button></p>
          <span class="web-sr-only" role="status">{{ blockageBusy ? 'Проверка состояния пары…' : blockageNotice }}</span>
        </div>
        <p v-if="manualError" class="web-error" role="alert">{{ manualError }} <button class="web-button" :disabled="manualLoading || interactionLocked" @click="home.readManual(manualId)">Обновить отметки</button></p>
        <p v-if="markError" class="web-error headman-home-mark-error" role="alert">{{ markError }} <button v-if="markPending" type="button" class="web-button" :disabled="markBusy" @click="home.reconcileMark">Проверить отметку</button> <button v-if="markCanAccept" type="button" class="web-button" @click="home.acceptCurrentMark">Принять текущее состояние</button></p>
        <span class="web-sr-only" role="status">{{ markNotice }}</span>
        </div>
        <div v-if="manualLesson && manualReport" ref="rosterRoot" class="headman-home-roster headman-home-scroll" role="region" aria-label="Отметки выбранной пары" :aria-busy="manualLoading">
          <table class="headman-home-table">
            <thead><tr><th scope="col">ФИО</th><th scope="col">Статус</th><th scope="col">Источник</th><th scope="col">Время</th></tr></thead>
            <tbody><template v-for="entry in roster" :key="entry.userId">
              <tr><th scope="row">{{ entry.displayName }}</th><td><HeadmanHomeMark :entry="entry" :lesson="manualLesson" :allowed="statuses.filter(status => home.canChooseMark(entry, status))" :locked="interactionLocked || loading || manualLoading || auditLoading" @choose="chooseMark(entry, $event)" /></td><td>{{ sourceLabel(entry.source) }}</td><td class="headman-home-mark-time"><time v-if="timestamp(entry)" :datetime="entry.timestamp ?? undefined" :title="`${fullMarkTime.format(timestamp(entry)!)} МСК`">{{ markTime.format(timestamp(entry)!) }}</time><template v-else>—</template></td></tr>
              <tr v-if="excuseDraft?.entry.userId === entry.userId" class="headman-home-excuse-row"><td colspan="4"><form :ref="setExcuseForm" class="headman-home-excuse-form" @submit.prevent="home.saveExcuse"><WebSelect v-model="excuseType" class="headman-home-excuse-select" label="Причина уважительного отсутствия" :options="reasonOptions" :disabled="markBusy || !!markPending" required /><div class="headman-home-excuse-actions"><button type="button" class="web-button" :disabled="markBusy || !!markPending" @click="closeExcuse">Отмена</button><button class="web-button web-button-primary" :disabled="markBusy || !!markPending || !excuseType">Поставить «у»</button></div></form></td></tr>
            </template></tbody>
          </table>
          <p v-if="!roster.length" class="headman-home-empty">Состав группы пока недоступен.</p>
        </div>
        <p v-else-if="!manualError" class="headman-home-empty" role="status">{{ loading || manualLoading ? 'Загрузка отметок…' : 'На сегодня пар нет.' }}</p>
      </section>

      <section class="headman-home-panel headman-home-audit-panel" aria-label="Журнал действий по паре">
        <nav aria-label="Пара в журнале действий">
          <StudentPeriodPager :label="lessonLabel(auditLesson)" unit="период журнала действий" :previous-disabled="auditIndex <= 0" :next-disabled="auditIndex < 0 || auditIndex >= lessons.length - 1" :busy="loading || auditLoading || interactionLocked" @step="home.step('audit', $event)" @reset="home.reset('audit')" />
        </nav>
        <div class="headman-home-card headman-home-audit">
          <h2>Журнал действий</h2>
          <p v-if="auditError" class="web-error" role="alert">{{ auditError }} <button class="web-button" :disabled="auditLoading || interactionLocked" @click="home.readAudit(auditId)">Обновить журнал</button></p>
          <div v-if="audit.length" class="headman-home-scroll headman-home-audit-scroll" role="region" aria-label="Действия выбранной пары" tabindex="0" :aria-busy="auditLoading">
            <ol class="headman-home-events"><li v-for="action in audit" :key="action.id"><time :datetime="action.occurredAt">{{ instant(action.occurredAt) }}</time><div><p>{{ eventText(action) }}</p><p v-if="action.geo" class="web-caption">Геопроверка<template v-if="action.geo.distanceMeters !== null"> · {{ action.geo.distanceMeters }} м от центра</template></p></div></li></ol>
          </div>
          <p v-else-if="!auditError" class="web-caption" role="status">{{ loading || auditLoading ? 'Загрузка журнала…' : auditLesson ? 'Действий пока нет.' : 'На сегодня пар нет.' }}</p>
          <button v-if="cursor" class="web-button" :disabled="auditLoading || interactionLocked" @click="home.readAudit(auditId, true)">Показать ещё</button>
        </div>
      </section>
    </div>

    <div class="headman-home-bottom">
      <section class="headman-home-homework" aria-label="Домашнее задание на сегодня">
        <h2 class="web-sr-only">Домашнее задание на сегодня</h2>
        <p v-if="homeworkError" class="web-error" role="alert">{{ homeworkError }}</p>
        <div v-if="homeworks?.length" class="headman-home-homework-list headman-home-scroll" role="region" aria-label="Задания на сегодня" tabindex="0">
          <article v-for="homework in homeworks" :key="homework.id" class="headman-home-card headman-home-homework-card">
            <p class="web-caption">{{ subjectName(homework.subjectId) }}</p><h3>{{ homework.title || subjectName(homework.subjectId) }}</h3>
            <p v-if="homework.description" class="headman-home-homework-description"><LinkifiedText :text="homework.description" /></p>
            <a v-if="homework.link" :href="homework.link" target="_blank" rel="noopener noreferrer">Основная ссылка <span class="web-sr-only">(откроется в новой вкладке)</span></a>

          </article>
        </div>
        <p v-else-if="!homeworkError" class="headman-home-card web-caption">{{ loading && homeworks === null ? 'Загрузка заданий…' : 'Заданий на сегодня нет.' }}</p>
      </section>
      <aside class="headman-home-requests" aria-label="Открытые заявки за сегодня">
        <section v-for="group in requestGroups" :key="group.kind" class="headman-home-card headman-home-request-group">
          <h2><RouterLink :to="{ name: 'headman-requests', query: { type: group.kind } }">{{ group.label }}<span v-if="group.page" class="web-sr-only"> · показано за сегодня {{ group.items.length }}</span></RouterLink></h2>
          <p v-if="group.error" class="web-error" role="alert">{{ group.error }}</p>
          <RouterLink v-for="request in group.items" :key="request.id" class="headman-home-request" :to="{ name: 'headman-requests', query: { type: group.kind, request: request.id } }">
            <span>№ {{ request.id }}</span><div><p>Автор: {{ request.studentName ?? 'Не указан' }}<template v-if="request.coverageStart"> · на {{ dateLabel(request.coverageStart) }}<template v-if="request.coverageEnd && request.coverageEnd !== request.coverageStart">–{{ dateLabel(request.coverageEnd) }}</template></template></p><p v-if="created(request.createdAt)" class="web-caption">Создана {{ created(request.createdAt) }}</p></div>
          </RouterLink>
          <p v-if="!group.error && !group.items.length" class="web-caption">{{ loading && !group.page ? 'Загрузка заявок…' : 'За сегодня открытых заявок нет.' }}</p>
        </section>
      </aside>
    </div>
  </HeadmanPage>
</template>
