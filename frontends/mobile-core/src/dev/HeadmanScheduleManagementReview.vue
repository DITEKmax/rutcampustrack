<script setup lang="ts">
import './headman-schedule-management-review.pcss';
import { createRequestsReviewApi } from './headman-requests-review-fixtures';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  persistRecurringIntent,
  recurringUpdate,
} from '../features/schedule/headman-recurring-intent';
import {
  freezeRecurringCreate,
  recurringCreateScope,
} from '../features/schedule/headman-recurring-create';
import HeadmanScheduleScreen from '../features/schedule/HeadmanScheduleScreen.vue';
import HeadmanLessonManagementScreen from '../features/headman-lesson-management/HeadmanLessonManagementScreen.vue';
import MobileShell from '../shared/components/MobileShell.vue';
import { createMobileNavigationStack, nestedRoute } from '../shared/navigation';
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items';
import type { MobileHostAdapter } from '../shared/host';
import { DEFAULT_PASSWORD_POLICY, type ProfileSnapshot } from '../features/profile/profile-types';
import {
  createScheduleManagementApis,
  scheduleManagementStates,
} from './headman-schedule-management-fixtures';
import { datePlus } from '../features/headman-lesson-management/lesson-management-state';
const query = new URLSearchParams(window.location.search),
  state = query.get('state') ?? 'constructor-empty',
  shell = query.get('shell') === 'tma' ? 'tma' : 'pwa';
const assistantPermissions =
  state === 'management-assistant' ? (['CANCEL_LESSONS'] as const) : null;
const requestsFixtureState = query.get('requestsFixture') === 'unknown' ? 'unknown' : 'queue';
const requestsFixture = ['queue', 'unknown'].includes(query.get('requestsFixture') ?? '');
const requestLogs: string[] = [];
const requestsApi = requestsFixture
  ? createRequestsReviewApi(requestsFixtureState, (line) => {
      requestLogs.push(line);
      document.documentElement.dataset.scheduleManagementRequestsFixtureLog =
        JSON.stringify(requestLogs);
    })
  : null;
const constructor = state.startsWith('constructor') || requestsFixture,
  apis = createScheduleManagementApis(state),
  offline = ref(false),
  task = ref('agenda'),
  hostVisible = ref(false);
const run = query.get('run') ?? 'schedule-management-r1',
  session = 'synthetic-' + run + '-' + state + '-' + shell;
const profile: ProfileSnapshot = {
  userId: '1',
  sessionId: session,
  displayName: 'Максим Кузнецов',
  groupLabel: 'ИБ-21',
  sessionVersion: '1',
  rolesVersion: '1',
  activeRole: 'HEADMAN',
  readOnly: false,
  passwordPolicy: DEFAULT_PASSWORD_POLICY,
  roles: [
    {
      grantId: 'fixture-headman',
      role: 'HEADMAN',
      status: 'ACTIVE',
      groupId: '4',
      contextLabel: 'ИБ-21',
      selectable: true,
      readOnly: false,
    },
  ],
};
if (state === 'constructor-all-restore') {
  const intent = freezeRecurringCreate(
    { userId: profile.userId, sessionId: profile.sessionId, groupId: 4, semesterId: 1 },
    '77777777-7777-4777-8777-777777777777',
    {
      groupId: 4,
      semesterId: 1,
      subjectId: 1,
      assignmentId: 101,
      dayOfWeek: 3,
      lessonNumber: 4,
      startTime: '13:00:00',
      endTime: '14:30:00',
      weekType: 'ALL',
      room: 'А-401',
    },
  );
  sessionStorage.setItem(recurringCreateScope(intent.owner), JSON.stringify(intent));
}
if (state === 'constructor-all-update-restore') {
  const target = {
    id: 201,
    assignmentId: 101,
    subjectId: 1,
    groupId: 4,
    semesterId: 1,
    dayOfWeek: 3,
    lessonNumber: 3,
    startTime: '11:20:00',
    endTime: '12:50:00',
    weekType: 'ODD' as const,
    room: 'А-401',
    active: true,
  };
  const revision = 'a'.repeat(64);
  persistRecurringIntent(sessionStorage, {
    owner: { userId: profile.userId, sessionId: profile.sessionId, groupId: 4, semesterId: 1 },
    target,
    key: '88888888-8888-4888-8888-888888888888',
    action: 'UPDATE',
    input: { ...recurringUpdate(target, 'ALL', 'Б-204'), expectedRevision: revision },
    preview: { revision, updatedCount: 8, removedCount: 0, restoredCount: 0, createdCount: 0 },
    rejected: false,
    transferIds: [],
  });
}
const navigation = createMobileNavigationStack(
  nestedRoute('headman-more', 'headman-more/lessons', 'overview'),
);
const route = computed(() =>
  nestedRoute(
    'headman-more',
    'headman-more/lessons',
    task.value === 'agenda' ? 'overview' : 'editor',
  ),
);
const screen = ref<InstanceType<typeof HeadmanLessonManagementScreen> | null>(null);
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product',
  primaryActionOwner: 'product',
  setBackVisible: (value) => {
    hostVisible.value = value;
  },
  subscribeBack: (listener) => {
    window.addEventListener('schedule-management-host-back', listener);
    return () => window.removeEventListener('schedule-management-host-back', listener);
  },
};
const stopGuard = navigation.beforeBack(() => !screen.value?.back());
const originalFont = document.documentElement.style.fontSize;
if (query.get('font') === '20') document.documentElement.style.fontSize = '20px';
function hostBack(): void {
  window.dispatchEvent(new Event('schedule-management-host-back'));
}
let disposed = false;
async function waitFor(predicate: () => boolean): Promise<void> {
  for (let i = 0; i < 600 && !disposed; i++) {
    if (predicate()) return;
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  }
  throw new Error('Fixture did not settle ' + state);
}
function clickText(label: string): void {
  const button = [...document.querySelectorAll<HTMLButtonElement>('button')].find(
    (b) => b.textContent?.trim() === label || b.textContent?.trim().startsWith(label),
  );
  if (!button || button.disabled) throw new Error('Fixture action unavailable ' + label);
  button.click();
}
function click(selector: string): void {
  const el = document.querySelector<HTMLButtonElement>(selector);
  if (!el || el.disabled) throw new Error('Fixture action unavailable ' + selector);
  el.click();
}
function value(selector: string, text: string): void {
  const el = document.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(
    selector,
  );
  if (!el) throw new Error('Fixture field missing ' + selector);
  el.value = text;
  el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
}
onMounted(async () => {
  document.documentElement.dataset.scheduleManagementFixtureState = state;
  document.documentElement.dataset.scheduleManagementFixtureSynthetic = 'true';
  document.documentElement.dataset.scheduleManagementFixtureSession = session;
  try {
    if (requestsFixture) {
      await waitFor(() => !!document.querySelector('.mobile-bottom-nav'));
      clickText('Заявки');
      await waitFor(
        () => !!document.querySelector('main[data-request-view="queue"][aria-busy="false"]'),
      );
    } else if (constructor) {
      await waitFor(() => !!document.querySelector('.mobile-bottom-nav'));
      clickText('Ещё');
      await waitFor(() => !!document.querySelector('.headman-more'));
      clickText('Конструктор расписания');
      if (state === 'constructor-all-restore' || state === 'constructor-all-update-restore') {
        await waitFor(
          () => !!document.querySelector('[data-constructor-view="editor"][aria-busy="false"]'),
        );
        await waitFor(
          () => document.querySelectorAll('.headman-schedule__parity button').length === 2,
        );
        if (
          document.querySelectorAll('.headman-schedule__parity button[aria-pressed="true"]')
            .length !== 2
        )
          throw new Error('ALL intent lost its week selection before policy loaded');
        document.documentElement.dataset.scheduleManagementAllRestoreKey =
          state === 'constructor-all-restore'
            ? '77777777-7777-4777-8777-777777777777'
            : '88888888-8888-4888-8888-888888888888';
      } else {
        await waitFor(
          () => !!document.querySelector('[data-constructor-view="list"][aria-busy="false"]'),
        );
        if (state !== 'constructor-read-error') clickText('Ср');
        await nextTick();
      }
      if (['constructor-add', 'constructor-slots', 'constructor-create-unknown'].includes(state)) {
        click('[data-constructor-view="list"] .headman-schedule__primary');
        await waitFor(
          () => !!document.querySelector('[data-constructor-view="editor"][aria-busy="false"]'),
        );
        click('.headman-schedule__select');
        await nextTick();
        if (state !== 'constructor-slots') {
          click('[data-slot="4"]');
          await nextTick();
        }
      }
      if (state === 'constructor-series') {
        click('.headman-schedule__slot');
        await waitFor(() => !!document.querySelector('[data-constructor-view="editor"]'));
        await nextTick();
      }
      if (state === 'constructor-week-two') {
        click('.headman-schedule__weeks button:last-child');
        await nextTick();
      }
      if (state === 'constructor-both-weeks') {
        click('[data-constructor-view="list"] .headman-schedule__primary');
        await waitFor(() => !!document.querySelector('[data-constructor-view="editor"]'));
        click('.headman-schedule__parity button:last-child');
        await nextTick();
      }
      if (state === 'constructor-create-unknown') {
        clickText('Сохранить пару');
        await waitFor(
          () =>
            !!document.querySelector('.student-warning') &&
            !!document.querySelector('[data-constructor-view="editor"][aria-busy="false"]'),
        );
      }
    } else {
      await waitFor(
        () => !!document.querySelector('[data-management-view="agenda"][aria-busy="false"]'),
      );
      if (state === 'management-add' || state === 'management-oneoff') {
        click('[role="switch"]');
        await nextTick();
        if (state === 'management-oneoff') {
          click('[data-slot="6"]');
          await waitFor(
            () => !!document.querySelector('[data-management-view="oneoff"][aria-busy="false"]'),
          );
        }
      }
      if (
        [
          'management-active',
          'management-transfer',
          'management-calendar',
          'management-cancel',
          'management-room',
          'management-transfer-pending',
          'management-cancel-unknown',
          'management-room-log',
          'management-conflict',
          'management-assistant',
        ].includes(state)
      ) {
        click('[data-lesson-id="41"]');
        await nextTick();
        if (
          [
            'management-transfer',
            'management-calendar',
            'management-transfer-pending',
            'management-conflict',
          ].includes(state)
        ) {
          clickText('Перенести пару');
          await waitFor(
            () =>
              !!document.querySelector('[data-management-view="transfer"]') &&
              !document.querySelector('[role="status"]'),
          );
          value('.lesson-management__field select', '4');
          await nextTick();
          value('.lesson-management__field input', 'Б-204');
        }
        if (state === 'management-calendar') click('.lesson-management__select');
        if (state === 'management-room') clickText('Изменить аудиторию');
        if (state === 'management-cancel' || state === 'management-cancel-unknown') {
          clickText('Отменить пару');
          await nextTick();
          value('textarea', 'Преподаватель отсутствует. Новая дата будет согласована отдельно.');
          await nextTick();
        }
        if (state === 'management-room-log') {
          clickText('Открыть журнал действий');
          await waitFor(() => !!document.querySelector('.headman-actions:not([aria-busy="true"])'));
        }
        if (state === 'management-transfer-pending' || state === 'management-conflict') {
          clickText('Перенести пару');
          await waitFor(() => !!document.querySelector('.student-warning'));
        }
        if (state === 'management-cancel-unknown') {
          clickText('Отменить пару');
          await waitFor(() => !!document.querySelector('.student-warning'));
        }
      }
      if (state === 'management-cancelled') {
        click('[role="switch"]:last-child');
        await nextTick();
        click('[data-lesson-id="45"]');
      }
      if (state === 'management-offline') offline.value = true;
    }
    await nextTick();
    await document.fonts.ready;
    document.documentElement.dataset.scheduleManagementFixtureView =
      document
        .querySelector('main[data-constructor-view],[data-management-view]')
        ?.getAttribute(constructor ? 'data-constructor-view' : 'data-management-view') ?? '';
    document.documentElement.dataset.scheduleManagementFixtureRevision =
      query.get('revision') ?? 'working-tree';
    document.documentElement.dataset.scheduleManagementFixtureReady = 'true';
  } catch (cause) {
    document.documentElement.dataset.scheduleManagementFixtureError =
      cause instanceof Error ? cause.message : String(cause);
  }
});
onBeforeUnmount(() => {
  disposed = true;
  stopGuard();
  document.documentElement.style.fontSize = originalFont;
});
</script>
<template>
  <HeadmanScheduleScreen
    v-if="constructor"
    :api="apis.schedule"
    :requests-api="requestsApi"
    :profile="profile"
    :group-id="4"
    :host="host"
  />
  <MobileShell
    v-else
    :route="route"
    :navigation="navigation"
    :nav-items="createHeadmanNavigationItems()"
    :host="host"
  >
    <template #back> <span hidden /> </template
    ><HeadmanLessonManagementScreen
      ref="screen"
      :journal-api="apis.journal"
      :schedule-api="apis.schedule"
      :actions-api="apis.actions"
      :group-id="4"
      :owner="profile"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :initial-date="
        datePlus(new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' }), 7)
      "
      @view-change="task = $event"
    />
  </MobileShell>
  <button
    v-if="shell === 'tma' && hostVisible"
    type="button"
    class="schedule-management-fixture__host"
    aria-label="Назад через Telegram"
    @click="hostBack"
  >
    Назад
  </button>
  <span hidden>{{ scheduleManagementStates.length }}</span>
</template>
