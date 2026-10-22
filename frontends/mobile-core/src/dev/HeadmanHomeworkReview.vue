<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import HeadmanHomeworkScreen from '../features/homework/HeadmanHomeworkScreen.vue';
import HeadmanScheduleScreen from '../features/schedule/HeadmanScheduleScreen.vue';
import { CampusMapClient } from '../api/map-client';
import { mapFixture } from './profile-map-fixtures';
import MobileShell from '../shared/components/MobileShell.vue';
import { createMobileNavigationStack, nestedRoute } from '../shared/navigation';
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items';
import type { MobileHostAdapter } from '../shared/host';
import {
  createHeadmanHomeworkFixtures,
  headmanHomeworkStates,
  headmanHomeworkClock,
} from './headman-homework-fixtures';
import backIcon from '../assets/headman-homework-previous.svg';
import './headman-homework-review.pcss';

const query = new URLSearchParams(window.location.search);
const state = query.get('state') ?? 'calendar';
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa';
const capture = query.get('capture') === '1';
const offline = ref(state === 'offline');
const fixtures = createHeadmanHomeworkFixtures(state);
const navigationState = state === 'navigation';
const mapClient = new CampusMapClient({
  accessToken: () => null,
  fetcher: async () => Response.json(mapFixture('map-empty')),
});
const editState = state.startsWith('edit-');
const view = ref<'calendar' | 'detail' | 'wizard' | 'history'>('calendar');
const screen = ref<InstanceType<typeof HeadmanHomeworkScreen> | null>(null);
const backVisible = ref(false);
const navItems = createHeadmanNavigationItems();
const navigation = createMobileNavigationStack(
  nestedRoute('headman-more', 'headman-more/homework', 'overview'),
);
const stopGuard = navigation.beforeBack(() => !screen.value?.back());
const route = computed(() =>
  nestedRoute(
    'headman-more',
    'headman-more/homework',
    view.value === 'calendar' ? 'overview' : 'editor',
  ),
);
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product',
  primaryActionOwner: 'product',
  setBackVisible: (value) => {
    backVisible.value = value;
  },
  subscribeBack: (handler) => {
    window.addEventListener('headman-homework-host-back', handler);
    return () => window.removeEventListener('headman-homework-host-back', handler);
  },
};
const originalFont = document.documentElement.style.fontSize;
if (query.get('font') === '20') document.documentElement.style.fontSize = '20px';
const reviewState = {
  state,
  shell,
  ready: false,
  finished: false,
  actualView: '',
  error: null as string | null,
  requests: fixtures.requests,
};
function hostBack(): void {
  window.dispatchEvent(new Event('headman-homework-host-back'));
}
function openReviewMaterial(url: string): void {
  document.documentElement.dataset.openedMaterial = url;
}
Object.assign(window, { __headmanHomeworkReview: reviewState });
async function settle(): Promise<void> {
  for (let i = 0; i < 20; i += 1) await Promise.resolve();
  await nextTick();
}
async function waitFor(predicate: () => boolean): Promise<void> {
  const deadline = Date.now() + 5000;
  while (!predicate()) {
    if (Date.now() >= deadline)
      throw new Error(`Состояние ${state} не достигло ожидаемого контекста`);
    await new Promise<void>((resolve) => setTimeout(resolve, 20));
  }
}
async function clickText(value: string): Promise<void> {
  const find = () =>
    [...document.querySelectorAll<HTMLButtonElement>('.headman-homework button')].find(
      (item) =>
        (item.getAttribute('aria-label') === value || item.textContent?.trim() === value) &&
        !item.disabled,
    );
  await waitFor(() => Boolean(find()));
  find()!.click();
  await settle();
}
async function clickSelector(selector: string): Promise<void> {
  await waitFor(() => {
    const target = document.querySelector<HTMLButtonElement>(selector);
    return target !== null && !target.disabled;
  });
  document.querySelector<HTMLButtonElement>(selector)!.click();
  await settle();
}
async function advance(): Promise<void> {
  await clickText('Продолжить');
}
onMounted(async () => {
  try {
    if (state === 'loading') return;
    if (navigationState) {
      await waitFor(() => Boolean(document.querySelector('.headman-home__actions button')));
      await document.fonts.load('400 16px "Onest Variable"');
      await document.fonts.ready;
      reviewState.actualView = 'navigation';
      reviewState.finished = true;
      reviewState.ready = document.fonts.check('400 16px "Onest Variable"');
      document.documentElement.dataset.headmanHomeworkReady = String(reviewState.ready);
      document.documentElement.dataset.headmanHomeworkRequested = state;
      document.documentElement.dataset.headmanHomeworkView = 'navigation';
      return;
    }
    await waitFor(
      () => document.querySelector('.headman-homework')?.getAttribute('aria-busy') === 'false',
    );
    await settle();
    if (state.startsWith('read-offline-')) {
      const held = fixtures.holdNextRead(state === 'read-offline-archive');
      screen.value?.selectRootDate('2026-09-02');
      await held.started;
      offline.value = true;
      await settle();
      held.release();
      await settle();
      offline.value = false;
      await settle();
      await waitFor(
        () =>
          Boolean(document.querySelector('.headman-homework__item[data-homework-id="91"]')) &&
          Boolean(document.querySelector('.headman-homework__item[data-homework-id="19"]')),
      );
    }
    if (state === 'no-active-history') {
      await clickText('Вторник, 1 сентября');
      await clickSelector('.headman-homework__item[data-homework-id="19"]');
      await clickText('История изменений');
      await waitFor(() => Boolean(document.querySelector('.headman-homework__history')));
    }
    if (
      state === 'detail' ||
      state === 'long-content' ||
      state === 'unsafe-link' ||
      state === 'history-active' ||
      state === 'history-unknown' ||
      editState
    ) {
      await clickSelector('.headman-homework__item');
    }
    if (state === 'terminal-detail' || state === 'history-terminal')
      await clickSelector('.headman-homework__item[data-homework-id="19"]');
    if (state === 'history-active' || state === 'history-terminal' || state === 'history-unknown') {
      await clickText('История изменений');
      await waitFor(() => Boolean(document.querySelector('.headman-homework__history')));
    }
    const verifyChangedSchedule = query.get('connectivity') === 'changed-schedule';
    if (
      state.startsWith('create-') ||
      editState ||
      ['pending', 'uncertain', 'stale-selection', 'multiple-pairs'].includes(state)
    ) {
      await clickText(editState ? 'Изменить задание' : 'Выдать ДЗ');
      await settle();
      if (state !== 'create-subject' && state !== 'edit-subject') {
        if (!editState) await clickText('Информационные системы');
        await settle();
        await advance();
        if (state !== 'create-type') {
          await clickSelector('.headman-homework__choices[aria-label="Тип занятия"] button');
          await advance();
          if (
            !verifyChangedSchedule &&
            (state === 'create-calendar-any' ||
              state === 'create-content' ||
              state === 'pending' ||
              state === 'uncertain')
          ) {
            await clickSelector('.headman-homework__any-date');
            await clickSelector(
              '.headman-homework__month button[aria-label="Суббота, 5 сентября"]',
            );
          }
          if (
            [
              'create-content',
              'edit-content',
              'pending',
              'uncertain',
              'edit-conflict',
              'stale-selection',
            ].includes(state)
          ) {
            await advance();
            const input = document.querySelector<HTMLInputElement>(
              '.headman-homework__content input',
            );
            if (input) {
              input.value = 'Пределы функций';
              input.dispatchEvent(new Event('input', { bubbles: true }));
            }
            const textarea = document.querySelector<HTMLTextAreaElement>('textarea');
            if (textarea) {
              textarea.value =
                'Повтори определения предела функции и непрерывности. Реши задачи № 16–20 из методички. Для № 19 приложи краткое обоснование.';
              textarea.dispatchEvent(new Event('input', { bubbles: true }));
            }
            const link = document.querySelector<HTMLInputElement>('input[type="url"]');
            if (link) {
              link.value = 'https://rut-miit.ru/materials/limits';
              link.dispatchEvent(new Event('input', { bubbles: true }));
            }
            await settle();
            if (['pending', 'uncertain', 'edit-conflict', 'stale-selection'].includes(state)) {
              await clickText(
                state === 'edit-conflict' ? 'Сохранить изменения' : 'Опубликовать задание',
              );
              await settle();
            }
          }
        }
      }
    }
    const verifyConnectivityIntent =
      (query.get('connectivity') === '1' || verifyChangedSchedule) &&
      (state === 'uncertain' || state === 'pending');
    if (verifyConnectivityIntent) {
      const original = fixtures.requests.find((request) => request.method === 'POST')?.body;
      const before = JSON.stringify(original);
      if (verifyChangedSchedule) {
        if (
          !original ||
          typeof original !== 'object' ||
          !('bindingMode' in original) ||
          original.bindingMode !== 'LESSON' ||
          !('selectedOccurrenceId' in original)
        )
          throw new Error('Проверка должна начинаться с автоматически выбранной пары');
        fixtures.addEligiblePair();
      }
      const draftTitle = document.querySelector<HTMLInputElement>(
        '.headman-homework__content input',
      )?.value;
      offline.value = true;
      await settle();
      const refreshedCalendarStart = fixtures.dayLessonReadCounts.length;
      offline.value = false;
      await settle();
      await waitFor(
        () =>
          document.querySelector<HTMLInputElement>('.headman-homework__content input')?.value ===
            draftTitle &&
          document.querySelector('.headman-homework')?.getAttribute('data-view') === 'wizard',
      );
      if (verifyChangedSchedule)
        await waitFor(() => {
          const retry = [
            ...document.querySelectorAll<HTMLButtonElement>('.headman-homework button'),
          ].find((button) => button.textContent?.trim() === 'Повторить публикацию');
          return (
            fixtures.dayLessonReadCounts.slice(refreshedCalendarStart).includes(2) &&
            Boolean(retry && !retry.disabled)
          );
        });
      await clickText('Повторить публикацию');
      await waitFor(
        () => document.querySelector('.headman-homework')?.getAttribute('data-view') === 'calendar',
      );
      const writes = fixtures.requests.filter((request) => request.method === 'POST');
      if (writes.length !== 2 || before !== JSON.stringify(writes[1]?.body))
        throw new Error('Повтор изменил исходный ключ или содержимое публикации');
      await waitFor(
        () =>
          Boolean(document.querySelector('.headman-homework__item[data-homework-id="101"]')) &&
          ![...document.querySelectorAll('.headman-homework [role="status"]')].some(
            (status) => status.textContent?.trim() === 'Загружаем задания выбранного дня…',
          ),
      );
      document.documentElement.dataset.headmanHomeworkConnectivityIdentity = 'true';
      if (verifyChangedSchedule)
        document.documentElement.dataset.headmanHomeworkChangedScheduleIdentity = 'true';
    }
    await document.fonts.load('400 16px "Onest Variable"');
    await document.fonts.ready;
    await settle();
    await waitFor(
      () => document.querySelector('.headman-homework')?.getAttribute('aria-busy') === 'false',
    );
    const expectedSteps: Record<string, number> = {
      'create-subject': 1,
      'create-type': 2,
      'create-calendar-schedule': 3,
      'create-calendar-any': 3,
      'multiple-pairs': 3,
      'create-content': 4,
      'edit-subject': 1,
      'edit-placement': 3,
      'edit-content': 4,
      pending: 4,
      uncertain: 4,
      'stale-selection': 4,
      'edit-conflict': 4,
    };
    if (verifyConnectivityIntent) delete expectedSteps[state];
    const expectedView = expectedSteps[state]
      ? 'wizard'
      : ['history-active', 'history-terminal', 'history-unknown', 'no-active-history'].includes(
            state,
          )
        ? 'history'
        : ['detail', 'long-content', 'unsafe-link', 'terminal-detail'].includes(state)
          ? 'detail'
          : 'calendar';
    await waitFor(
      () =>
        document.querySelector<HTMLElement>('.headman-homework')?.dataset.view === expectedView &&
        (!expectedSteps[state] ||
          document.querySelector('progress')?.getAttribute('value') ===
            String(expectedSteps[state])),
    );
    reviewState.actualView = expectedView;
    reviewState.finished = true;
    reviewState.ready = document.fonts.check('400 16px "Onest Variable"');
    document.documentElement.dataset.headmanHomeworkReady = String(reviewState.ready);
    document.documentElement.dataset.headmanHomeworkRequested = state;
    document.documentElement.dataset.headmanHomeworkView = expectedView;
  } catch (cause) {
    reviewState.error = cause instanceof Error ? cause.message : 'Ошибка оператора';
    document.documentElement.dataset.headmanHomeworkReady = 'false';
    document.documentElement.dataset.headmanHomeworkOperatorError = reviewState.error;
  }
});
onBeforeUnmount(() => {
  stopGuard();
  document.documentElement.style.fontSize = originalFont;
});
</script>

<template>
  <div
    class="headman-review"
    :data-shell="shell"
    :data-review-state="state"
  >
    <HeadmanScheduleScreen
      v-if="navigationState"
      :api="null"
      :profile="null"
      :group-id="7"
      selected-date="2026-09-02"
      :subjects-api="fixtures.subjectsApi"
      :journal-api="fixtures.journalApi"
      :homework-api="fixtures.api"
      :homework-actor-user-id="41"
      :map-client="mapClient"
      :host="host"
      :open-material="openReviewMaterial"
    />
    <MobileShell
      v-else
      :route="route"
      :navigation="navigation"
      :nav-items="navItems"
      active-id="headman-more"
      :host="host"
    >
      <template #back="{ visible, onBack }">
        <button
          v-if="visible"
          class="mobile-shell__back headman-homework-back"
          type="button"
          aria-label="Назад"
          @click="onBack"
        >
          <img
            :src="backIcon"
            alt=""
          >
        </button>
      </template>
      <HeadmanHomeworkScreen
        ref="screen"
        :api="fixtures.api"
        :subjects-api="fixtures.subjectsApi"
        :journal-api="fixtures.journalApi"
        :group-id="7"
        :user-id="41"
        :offline="offline"
        :read-only="state === 'read-only'"
        :host="host"
        :now="headmanHomeworkClock"
        :open-material="openReviewMaterial"
        @view-change="view = $event"
      />
    </MobileShell>
    <nav
      v-if="!capture"
      class="headman-review__links"
      aria-label="Состояния синтетического API"
    >
      <p>Фактический Vue · синтетический API · {{ shell.toUpperCase() }}</p><a :href="`?state=navigation&shell=${shell}`">navigation</a><button
        v-if="backVisible"
        type="button"
        @click="hostBack"
      >
        Telegram Back
      </button><a
        v-for="id in headmanHomeworkStates"
        :key="id"
        :href="`?state=${id}&shell=${shell}`"
      >{{ id }}</a>
    </nav>
  </div>
</template>
