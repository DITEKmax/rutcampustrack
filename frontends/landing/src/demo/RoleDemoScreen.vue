<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import AdminDashboardScreen from '../../../mobile-core/src/features/admin-dashboard/AdminDashboardScreen.vue';
import HeadmanManualRoster from '../../../mobile-core/src/features/headman-journal/HeadmanManualRoster.vue';
import TeacherLessonCard from '../../../mobile-core/src/features/teacher/TeacherLessonCard.vue';
import TeacherRoster from '../../../mobile-core/src/features/teacher/TeacherRoster.vue';
import type { HeadmanJournalRosterEntry } from '../../../mobile-core/src/features/headman-journal/headman-journal-client';
import type { ManualChoice } from '../../../mobile-core/src/features/headman-journal/headman-manual-marks';
import '../../../mobile-core/src/features/teacher/teacher-screen.pcss';
import {
  createAdminDemoClient,
  demoActivityCounts,
  groupRoster,
  teacherLessons,
  teacherRoster,
} from './role-fixtures';
import { schedule, selectedDemoLesson } from './fixtures';
import { shiftDate } from '../../../mobile-core/src/features/attendance/attendance-periods';
import {
  group,
  isEditableMemberId,
  isManualMark,
  lessonId,
  roleForView,
  roles,
  type DemoSnapshot,
  type EditableMemberId,
  type ManualMark,
  type PhoneView,
} from './model';

const props = defineProps<{ snapshot: DemoSnapshot; theme: 'dark' | 'light' }>();
const emit = defineEmits<{
  navigate: [view: PhoneView];
  roleSwitch: [];
  markMember: [lesson: string, id: EditableMemberId, value: ManualMark, expectedRevision: number];
  selectDate: [date: string];
  selectLesson: [lesson: string];
}>();
const role = computed(() => roles.find((item) => item.id === roleForView(props.snapshot.view)));
const groupRows = computed(() => groupRoster(props.snapshot));
const lessonList = ref<HTMLDetailsElement | null>(null);
const availableLessons = computed(() => teacherLessons(props.snapshot));
const selectedId = computed(() => selectedDemoLesson(props.snapshot)?.id ?? null);
const selected = computed(() => availableLessons.value.find(lesson => String(lesson.id) === selectedId.value));
const teacherRows = computed(() => teacherRoster(props.snapshot));
const adminDemoClient = createAdminDemoClient(() => props.snapshot);
const activity = computed(() => demoActivityCounts(props.snapshot));
const dashboardRevision = computed(() => [activity.value.positive, activity.value.geo, activity.value.requests].join(':'));
const dateLabel = computed(() => new Date(props.snapshot.date + 'T12:00:00Z').toLocaleDateString('ru-RU', {
  day: 'numeric', month: 'long', timeZone: 'UTC',
}));
const present = computed(() => groupRows.value.filter((row) => row.status === 'PRESENT').length);
const excused = computed(() => groupRows.value.filter((row) => row.status === 'EXCUSED').length);
const absent = computed(() => groupRows.value.filter((row) => row.status === 'ABSENT').length);
const pending = computed(
  () => groupRows.value.length - present.value - excused.value - absent.value,
);
const staleRows = new Set<number>();
const excuseId = ref<number | null>(null);
const closeReasonButton = ref<HTMLButtonElement | null>(null);
let reasonTrigger: HTMLElement | null = null;
const excuse = computed(() =>
  groupRows.value.find((row) => row.userId === excuseId.value && row.excuseReason),
);
async function showReason(id: number) {
  reasonTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  excuseId.value = id;
  await nextTick();
  closeReasonButton.value?.focus();
}
function closeReason() {
  excuseId.value = null;
  if (reasonTrigger?.isConnected) reasonTrigger.focus();
  reasonTrigger = null;
}
watch(
  [() => props.snapshot.view, () => props.snapshot.date, selectedId],
  () => {
    excuseId.value = null;
    reasonTrigger = null;
  },
);
function canChoose(entry: HeadmanJournalRosterEntry, choice: ManualChoice) {
  return entry.editable && isManualMark(choice);
}
function markMember(entry: HeadmanJournalRosterEntry, choice: ManualChoice) {
  if (canChoose(entry, choice) && isEditableMemberId(entry.userId) && isManualMark(choice)) {
    const lesson = selectedId.value;
    if (lesson) emit('markMember', lesson, entry.userId, choice, Number(entry.markRevision ?? 1));
  }
}
function openLesson(id: number) {
  emit('selectLesson', String(id));
  excuseId.value = null;
  reasonTrigger = null;
  if (lessonList.value) {
    lessonList.value.open = false;
    lessonList.value.querySelector('summary')?.focus();
  }
}
</script>

<template>
  <AdminDashboardScreen
    v-if="snapshot.view === 'admin'"
    :key="dashboardRevision"
    :theme="theme"
    :client="adminDemoClient"
    @open-role-switch="emit('roleSwitch')"
    @open-attendance="emit('navigate', 'admin-attendance')"
  />
  <section
    v-else
    class="phone-journal"
  >
    <button
      class="phone-back"
      type="button"
      aria-label="Сменить роль"
      @click="emit('roleSwitch')"
      >{{ role?.label }} ⌄</button
    >
    <button
      v-if="snapshot.view === 'admin-attendance'"
      class="phone-back"
      type="button"
      @click="emit('navigate', 'admin')"
    >← К обзору</button>
    <p class="phone-journal__group">{{ group }} · {{ dateLabel }}</p>
    <h1>{{ snapshot.view === 'group' ? 'Учёт группы' : snapshot.view === 'journal' ? 'Журнал занятия' : 'Посещаемость группы' }}</h1>
    <div class="phone-demo-date">
      <button class="phone-back" type="button" aria-label="Предыдущий день" :disabled="snapshot.date <= schedule.dateFrom" @click="emit('selectDate', shiftDate(snapshot.date, -1))">‹</button>
      <span>{{ dateLabel }}</span>
      <button class="phone-back" type="button" aria-label="Следующий день" :disabled="snapshot.date >= schedule.dateTo" @click="emit('selectDate', shiftDate(snapshot.date, 1))">›</button>
    </div>
    <TeacherLessonCard
      v-if="selected"
      :lesson="selected"
      :interactive="false"
      :current="selected.status === 'ACTIVE'"
    />
    <details v-if="availableLessons.length" ref="lessonList" class="phone-lessons">
      <summary>Выбрать занятие</summary>
      <div>
        <TeacherLessonCard
          v-for="lesson in availableLessons"
          :key="lesson.id"
          :lesson="lesson"
          :show-status="true"
          :current="String(lesson.id) === selectedId"
          @open="openLesson"
        />
      </div>
    </details>
    <p v-if="!selected" role="status">На этот день пар нет.</p>
    <p v-else-if="selected.cancelled" role="status">Пара отменена.</p>
    <template v-else-if="snapshot.view === 'group'">
      <p v-if="selectedId === lessonId && snapshot.scenario === 'pending'" role="status">Анна Смирнова · Заявка на подтверждении</p>
      <p v-else-if="selected.status !== 'PLANNED'">Поставь «был» или «не был» для доступной строки. Журнал преподавателя покажет изменение.</p>
      <HeadmanManualRoster
        :entries="groupRows"
        :pending-user-id="null"
        :blocked="false"
        :future="selected.status === 'PLANNED'"
        :issues="{}"
        :stale-rows="staleRows"
        :can-choose="canChoose"
        @choose="markMember"
      />
    </template>
    <template v-else-if="snapshot.view === 'journal'">
      <TeacherRoster :rows="teacherRows" @open-manual-excuse="showReason" />
      <p v-if="selectedId === lessonId" role="status">{{
        snapshot.scenario === 'pending'
          ? 'Анна Смирнова · Заявка на подтверждении'
          : snapshot.markState === 'ready'
            ? 'Анна Смирнова · Ожидает отметки'
            : 'Анна Смирнова · Одна отметка о присутствии'
      }}</p>
    </template>
    <dl v-else class="phone-summary">
      <div><dt>Были</dt><dd>{{ present }}</dd></div>
      <div><dt>Уважительная</dt><dd>{{ excused }}</dd></div>
      <div><dt>Не были</dt><dd>{{ absent }}</dd></div>
      <div><dt>Без отметки</dt><dd>{{ pending }}</dd></div>
    </dl>
    <div
      v-if="excuse"
      class="phone-excuse"
      role="region"
      aria-label="Причина отсутствия"
    >
      <h2>{{ excuse.displayName }}</h2>
      <p>{{ excuse.excuseReason }}</p>
      <button
        ref="closeReasonButton"
        class="phone-back"
        type="button"
        @click="closeReason"
        >Закрыть причину</button
      >
    </div>
  </section>
</template>

<style scoped>
.phone-demo-date {
  display: flex;
  align-items: center;
  gap: .5rem;
  justify-content: space-between;
}
</style>
