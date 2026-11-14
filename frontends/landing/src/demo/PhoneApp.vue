<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import AttendanceScreen from '../../../mobile-core/src/features/attendance/AttendanceScreen.vue';
import HomeworkScreen from '../../../mobile-core/src/features/homework/HomeworkScreen.vue';
import AppearanceScreen from '../../../mobile-core/src/features/profile/AppearanceScreen.vue';
import MoreScreen from '../../../mobile-core/src/features/profile/MoreScreen.vue';
import ProfileScreen from '../../../mobile-core/src/features/profile/ProfileScreen.vue';
import StatisticsScreen from '../../../mobile-core/src/features/statistics/StatisticsScreen.vue';
import RoleDemoScreen from './RoleDemoScreen.vue';
import SemesterScheduleScreen from '../../../mobile-core/src/features/today/SemesterScheduleScreen.vue';
import TodayScreen from '../../../mobile-core/src/features/today/TodayScreen.vue';
import MobileShell from '../../../mobile-core/src/shared/components/MobileShell.vue';
import RoleSwitchDialog from '../../../mobile-core/src/shared/components/RoleSwitchDialog.vue';
import type {
  AttendanceGraphRange,
  AttendanceMode,
} from '../../../mobile-core/src/features/attendance/attendance-view-model';
import type { StudentHomeworkItem, TodayLesson } from '../../../mobile-core/src/api/types';
import type {
  ProfileRole,
  ProfileRoute,
  ProfileTheme,
} from '../../../mobile-core/src/features/profile/profile-types';
import type { StatisticsGraphRange } from '../../../mobile-core/src/features/statistics/statistics-view-model';
import type { HomeworkPeriodMode } from '../../../mobile-core/src/domain/homework';
import { shiftDate } from '../../../mobile-core/src/features/attendance/attendance-periods';
import { createStudentNavigationItems } from '../../../mobile-core/src/shared/mobile-navigation-items';
import { nestedRoute, rootRoute } from '../../../mobile-core/src/shared/navigation';
import {
  attendance,
  demoDate,
  detail,
  homework,
  profile,
  scheduleFor,
  serverNow,
  statistics,
  today,
} from './fixtures';
import {
  channel,
  initialSnapshot,
  isPhoneView,
  isSnapshot,
  lessonId,
  isStudentView,
  roleForView,
  roles,
  type EditableMemberId,
  type ManualMark,
  type PhoneView,
} from './model';

const state = shallowRef(initialSnapshot);
const marked = computed(() => state.value.markState !== 'ready');
const view = computed(() => state.value.view);
const todayDate = computed(() => state.value.date);
const attendanceDate = todayDate;
const homeworkDate = todayDate;
const schedule = computed(() => scheduleFor(state.value));
const homeworkMode = ref<HomeworkPeriodMode>('day');
const attendanceMode = ref<AttendanceMode>('days');
const graphRange = ref<AttendanceGraphRange>('days');
const statsRange = ref<StatisticsGraphRange>('weeks');
const statsTypeSelected = ref(true);
const rankingVisible = ref(false);
const selectedSubject = ref<string | null>(null);
const expandedSubject = ref<string | null>(null);
const expandedLesson = ref<string | null>(null);
const completed = shallowRef<ReadonlySet<string>>(new Set());
const rolesOpen = ref(false);
const profilePanel = ref<ProfileRoute | null>(null);
const theme = ref<ProfileTheme>('dark');
const checkinStatus = ref<HTMLElement | null>(null);
const resetEpoch = ref(0);
const home = computed(() => today(state.value));
const attendanceData = computed(() => attendance(state.value));
const stats = computed(() => statistics(state.value, statsRange.value === 'days'));
const subjectDetail = computed(() =>
  detail(state.value, statsRange.value === 'days', selectedSubject.value, statsTypeSelected.value),
);
const account = computed(() => profile(roleForView(view.value)));
const homeworkData = computed(() => homework(completed.value, state.value));
const themeResolved = computed(() =>
  theme.value === 'system'
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
    : theme.value,
);
watch(
  themeResolved,
  (value) => {
    document.documentElement.dataset.theme = value;
  },
  { immediate: true },
);

const navItems = createStudentNavigationItems({
  homeworkEnabled: true,
  attendanceEnabled: true,
  moreEnabled: true,
  profileEnabled: true,
});
const host = { backOwner: 'product', primaryActionOwner: 'product' } as const;
const route = computed(() => {
  if (state.value.homeworkFocus) return nestedRoute('homework', 'homework/notification-target', 'detail')
  switch (view.value) {
    case 'schedule':
      return nestedRoute('more', 'more/demo-schedule', 'task');
    case 'statistics':
      return nestedRoute('more', 'more/demo-statistics', 'task');
    default:
      return rootRoute(isStudentView(view.value) ? view.value : 'today');
  }
});
const weekEnd = computed(() => shiftDate(homeworkDate.value, 6));
const homeworkRange = computed(() => ({
  from: homeworkDate.value,
  to: homeworkMode.value === 'week' ? weekEnd.value : homeworkDate.value,
}));
function send(type: string, detail?: unknown) {
  if (window.parent !== window)
    window.parent.postMessage({ channel, type, detail, resetEpoch: state.value.resetEpoch }, location.origin);
}
const localState = computed(() => ({
  revision: state.value.revision,
  modified:
    homeworkMode.value !== 'day' ||
    attendanceMode.value !== 'days' ||
    graphRange.value !== 'days' ||
    statsRange.value !== 'weeks' ||
    !statsTypeSelected.value ||
    rankingVisible.value ||
    selectedSubject.value !== null ||
    expandedSubject.value !== null ||
    expandedLesson.value !== null ||
    completed.value.size > 0 ||
    profilePanel.value !== null ||
    theme.value !== 'dark',
}));
watch(localState, (report) => send('local-state', report), { flush: 'post' });

function navigate(next: string) {
  if (isPhoneView(next)) send('navigate', next);
}
function changeRole(role: ProfileRole) {
  const target = roles.find((item) => item.id === role);
  if (!target) return;
  rolesOpen.value = false;
  navigate(target.home);
}
function selectDate(date: string) {
  send('select-date', date);
}
function selectLesson(id: string) {
  send('select-lesson', id);
}
function markMember(lesson: string, id: EditableMemberId, value: ManualMark, expectedRevision: number) {
  send('mark-member', { lesson, id, value, expectedRevision });
}
function openProfile(next: ProfileRoute) {
  if (next === 'role-switch') {
    rolesOpen.value = true;
    return;
  }
  profilePanel.value = next;
}
const profileMessages: Partial<Record<ProfileRoute, [string, string]>> = {
  security: ['Безопасность', 'В этой демонстрации нет настоящего аккаунта и пароля.'],
  sessions: ['Активные сеансы', 'В этой демонстрации нет сеансов.'],
  history: ['История аккаунта', 'В этой демонстрации нет истории аккаунта.'],
  requests: ['Заявки', 'В этом учебном дне нет заявок.'],
};
const panelCopy = computed(() =>
  profilePanel.value === 'requests' && state.value.scenario === 'pending'
    ? ['Заявки', 'Основы программирования · Заявка на подтверждении']
    : profilePanel.value ? profileMessages[profilePanel.value] : undefined,
);
function navigateMore(next: ProfileRoute) {
  if (next === 'requests') openProfile(next);
  else navigate(next);
}
function checkin(lesson: TodayLesson) {
  if (lesson.schedule.id === lessonId) {
    selectLesson(lessonId);
    send('mark', lessonId);
  }
}
function setComplete(item: StudentHomeworkItem, value: boolean) {
  const next = new Set(completed.value);
  if (value) next.add(item.id);
  else next.delete(item.id);
  completed.value = next;
}
function stepHomework(step: number) {
  if (homeworkMode.value === 'month') {
    const date = new Date(homeworkDate.value + 'T12:00:00Z');
    date.setUTCDate(1);
    date.setUTCMonth(date.getUTCMonth() + step);
    selectDate(date.toISOString().slice(0, 10));
  } else
    selectDate(shiftDate(homeworkDate.value, step * (homeworkMode.value === 'week' ? 7 : 1)));
}
function resetLocal() {
  resetEpoch.value++;
  homeworkMode.value = 'day';
  attendanceMode.value = 'days';
  graphRange.value = 'days';
  statsRange.value = 'weeks';
  statsTypeSelected.value = true;
  rankingVisible.value = false;
  selectedSubject.value = null;
  expandedSubject.value = null;
  expandedLesson.value = null;
  completed.value = new Set();
  rolesOpen.value = false;
  profilePanel.value = null;
  theme.value = 'dark';
}
async function receive(event: MessageEvent) {
  if (
    event.origin !== location.origin ||
    event.source !== window.parent ||
    event.data?.channel !== channel ||
    event.data.type !== 'snapshot' ||
    !isSnapshot(event.data.detail)
  )
    return;
  const previous = state.value;
  const next = event.data.detail;
  const hadMarkFocus = document.activeElement?.classList.contains('today-hero__action');
  const changedView = previous.view !== next.view;
  const changedRevision = previous.revision !== next.revision;
  const reset = previous.resetEpoch !== next.resetEpoch;
  const focusHomework = next.homeworkFocus !== null &&
    next.homeworkFocus.requestId !== previous.homeworkFocus?.requestId;
  if (focusHomework) homeworkMode.value = 'day';
  if (reset) resetLocal();
  if (changedRevision && next.focusMark) {
    expandedLesson.value = null;
    rolesOpen.value = false;
  }
  if (changedView) {
    profilePanel.value = null;
    selectedSubject.value = null;
  }
  state.value = next;
  await nextTick();
  if (!focusHomework && (changedView || changedRevision || reset)) window.scrollTo({ top: 0, behavior: 'instant' });
  if (changedRevision && next.focusMark) {
    const mark = document.querySelector<HTMLButtonElement>('.today-hero__action:not(:disabled)');
    (mark ?? document.querySelector<HTMLElement>('#today-title'))?.focus({ preventScroll: true });
  } else if (reset && document.hasFocus()) {
    document.querySelector<HTMLButtonElement>('.today-role')?.focus({ preventScroll: true });
  } else if (changedView && !focusHomework && document.hasFocus()) {
    const heading = document.querySelector<HTMLElement>('#app h1');
    if (heading) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
    }
  } else if (hadMarkFocus && ((previous.markState === 'ready' && marked.value) || (previous.scenario !== 'pending' && next.scenario === 'pending'))) {
    checkinStatus.value?.focus({ preventScroll: true });
  }
}
onMounted(() => {
  window.addEventListener('message', receive);
  send('ready');
});
onBeforeUnmount(() => window.removeEventListener('message', receive));
</script>

<template>
  <div
    :key="resetEpoch"
    class="student-feature-owner"
    :data-student-ui="isStudentView(view) ? '' : undefined"
    :data-headman-ui="view === 'group' ? '' : undefined"
  >
    <RoleDemoScreen
      v-if="!isStudentView(view)"
      :key="roleForView(view)"
      :snapshot="state"
      :theme="themeResolved"
      @navigate="navigate"
      @role-switch="rolesOpen = true"
      @mark-member="markMember"
      @select-date="selectDate"
      @select-lesson="selectLesson"
    />
    <template v-else-if="view === 'today'">
    <p v-if="state.scenario === 'geo-unavailable' && state.date === demoDate" class="phone-demo-note" role="status">Геолокация недоступна в этом сценарии.</p>
    <TodayScreen
      :today="home"
      :loading="false"
      :error="null"
      :offline="false"
      :updated-at="serverNow"
      :submitting-lesson-id="null"
      :semester-schedule="schedule"
      :selected-date="todayDate"
      :nav-items="navItems"
      :host="host"
      :role-switch-disabled="false"
      :expanded-lesson-id="expandedLesson"
      @navigate="navigate"
      @checkin="checkin"
      @role-switch="rolesOpen = true"
      @select-date="selectDate"
      @toggle-actions="
        selectLesson($event.schedule.id);
        expandedLesson = expandedLesson === $event.schedule.id ? null : $event.schedule.id
      "
    />
    </template>
    <HomeworkScreen
      v-else-if="view === 'homework'"
      :homework="homeworkData"
      :loading="false"
      :error="null"
      :nav-items="navItems"
      :host="host"
      :today-date="demoDate"
      :selected-date="homeworkDate"
      :period-mode="homeworkMode"
      :route="route"
      :focus-item-id="state.homeworkFocus?.itemId ?? null"
      :focus-request-id="state.homeworkFocus?.requestId ?? null"
      @back="navigate('homework')"
      :period-range="homeworkRange"
      :semester-range="{ from: schedule.dateFrom, to: schedule.dateTo }"
      @navigate="navigate"
      @complete="setComplete"
      @select-period="homeworkMode = $event"
      @select-date="selectDate"
      @step-period="stepHomework"
      @return-today="selectDate(demoDate)"
    />
    <MobileShell
      v-else
      :nav-items="navItems"
      :route="route"
      :host="host"
      custom-back
      @navigate="navigate"
    >
      <AttendanceScreen
        v-if="view === 'attendance'"
        :state="{ status: 'ready', data: attendanceData }"
        :selected-date="attendanceDate"
        :mode="attendanceMode"
        :graph-range="graphRange"
        :expanded-subject-id="expandedSubject"
        @select-date="selectDate"
        @set-mode="attendanceMode = $event"
        @set-graph-range="graphRange = $event"
        @toggle-subject="expandedSubject = expandedSubject === $event ? null : $event"
        @back="navigate('today')"
      />
      <SemesterScheduleScreen
        v-else-if="view === 'schedule'"
        :schedule="schedule"
        :current-date="demoDate"
        :offline="false"
        @back="navigate('more')"
      />
      <StatisticsScreen
        v-else-if="view === 'statistics'"
        :state="{ status: 'ready', data: stats }"
        :range="statsRange"
        :selected-subject-id="selectedSubject"
        :semester-starts-on="schedule.dateFrom"
        :semester-ends-on="schedule.dateTo"
        :detail-state="{ status: 'ready', data: subjectDetail }"
        :ranking-visible="rankingVisible"
        :ranking-state="{ status: 'empty' }"
        @set-range="statsRange = $event"
        @set-types="statsTypeSelected = $event.length > 0"
        @open-ranking="rankingVisible = !rankingVisible"
        @open-subject="
          selectedSubject = $event;
          statsTypeSelected = true;
        "
        @back="selectedSubject ? (selectedSubject = null) : navigate('more')"
      />
      <AppearanceScreen
        v-else-if="profilePanel === 'appearance'"
        :theme="theme"
        :resolved-theme="themeResolved"
        :theme-unlocked="true"
        :on-theme-change="
          (next) => {
            theme = next;
          }
        "
        :on-back="
          () => {
            profilePanel = null;
          }
        "
      />
      <section
        v-else-if="panelCopy"
        class="phone-journal"
      >
        <button
          class="phone-back"
          type="button"
          @click="profilePanel = null"
          >← Назад</button
        >
        <h1>{{ panelCopy[0] }}</h1>
        <p>{{ panelCopy[1] }}</p>
      </section>
      <MoreScreen
        v-else-if="view === 'more'"
        :on-navigate="navigateMore"
      />
      <ProfileScreen
        v-else
        :snapshot="account"
        :on-navigate="openProfile"
      />
    </MobileShell>
    <RoleSwitchDialog
      v-if="rolesOpen"
      student-ui
      dismissible
      :snapshot="account"
      :on-select-role="changeRole"
      @close="rolesOpen = false"
    />
    <p
      v-if="view === 'today'"
      ref="checkinStatus"
      class="phone-sr"
      role="status"
      tabindex="-1"
    >
      {{ marked ? 'Присутствие отмечено. Журнал обновлён.' : state.scenario === 'pending' ? 'Заявка отправлена старосте.' : '' }}
    </p>
  </div>
</template>

<style scoped>
.phone-demo-note {
  margin: 0;
  padding: .65rem 1rem;
  color: var(--text-primary);
  background: color-mix(in srgb, currentColor 8%, transparent);
  font-size: .82rem;
}
</style>
