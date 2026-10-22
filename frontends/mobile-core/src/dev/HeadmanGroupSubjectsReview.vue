<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import HeadmanGroupScreen from '../features/headman-group/HeadmanGroupScreen.vue';
import HeadmanSubjectsScreen from '../features/headman-subjects/HeadmanSubjectsScreen.vue';
import MobileShell from '../shared/components/MobileShell.vue';
import { createMobileNavigationStack, nestedRoute } from '../shared/navigation';
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items';
import type { MobileHostAdapter } from '../shared/host';
import { createGroupSubjectsApis } from './headman-group-subjects-fixtures';
const query = new URLSearchParams(window.location.search),
  state = query.get('state') ?? 'group-list',
  shell = query.get('shell') ?? 'pwa';
const group = state.startsWith('group'),
  apis = createGroupSubjectsApis(state),
  offline = ref(false);
const groupScreen = ref<InstanceType<typeof HeadmanGroupScreen> | null>(null),
  subjectsScreen = ref<InstanceType<typeof HeadmanSubjectsScreen> | null>(null);
const navigation = createMobileNavigationStack(
  nestedRoute('headman-more', group ? 'headman-more/group' : 'headman-more/subjects', 'detail'),
);
const route = ref(navigation.current),
  hostVisible = ref(false);
const stopRoute = navigation.subscribe(() => {
  route.value = navigation.current;
});
const stopGuard = navigation.beforeBack(
  () => !(group ? groupScreen.value?.back() : subjectsScreen.value?.back()),
);
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product',
  primaryActionOwner: 'product',
  setBackVisible: (value) => {
    hostVisible.value = value;
  },
  subscribeBack: (listener) => {
    window.addEventListener('group-subjects-host-back', listener);
    return () => window.removeEventListener('group-subjects-host-back', listener);
  },
};
function hostBack(): void {
  window.dispatchEvent(new Event('group-subjects-host-back'));
}
const originalFont = document.documentElement.style.fontSize;
if (query.get('font') === '20') document.documentElement.style.fontSize = '20px';
let disposed = false;
const viewObserver = new MutationObserver(() => {
  const main = document.querySelector('main');
  document.documentElement.dataset.groupSubjectsFixtureView =
    main?.getAttribute(group ? 'data-group-view' : 'data-subject-view') ?? '';
});
async function waitFor(predicate: () => boolean): Promise<void> {
  for (let frame = 0; frame < 600 && !disposed; frame++) {
    if (predicate()) return;
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  }
  throw new Error(`Fixture condition did not settle: ${state}`);
}
function click(selector: string): void {
  const element = document.querySelector<HTMLButtonElement>(selector);
  if (!element) throw new Error(`Missing fixture action ${selector}`);
  element.click();
}
async function clickEnabled(selector: string): Promise<void> {
  await nextTick();
  await waitFor(() => {
    const button = document.querySelector<HTMLButtonElement>(selector);
    return !!button && !button.disabled;
  });
  click(selector);
}
function setInput(selector: string, value: string): void {
  const element = document.querySelector<HTMLInputElement>(selector);
  if (!element) throw new Error(`Missing fixture input ${selector}`);
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
}
onMounted(async () => {
  viewObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['data-group-view', 'data-subject-view'],
    subtree: true,
  });
  try {
    await waitFor(() => document.querySelector('main[aria-busy="false"]') !== null);
    if (
      group &&
      !['group-list', 'group-empty', 'group-offline', 'group-read-error'].includes(state)
    ) {
      click(`[data-member-id="${state === 'group-ordinary' ? 3 : 2}"]`);
      await waitFor(() => document.querySelector('[data-group-view="detail"]') !== null);
      if (
        [
          'group-permissions',
          'group-dirty-leave',
          'group-revoke-confirm',
          'group-permission-confirm',
          'group-grant-confirm',
          'group-revoke-only-confirm',
          'group-permission-mixed-step1',
          'group-permission-mixed-step2',
          'group-permission-stale',
          'group-permission-unknown',
          'group-readonly',
        ].includes(state)
      ) {
        click('.headman-group__configure');
        await waitFor(() => document.querySelectorAll('[role="switch"]').length === 5);
        if (
          [
            'group-permissions',
            'group-permission-confirm',
            'group-permission-mixed-step1',
            'group-permission-mixed-step2',
            'group-permission-stale',
            'group-permission-unknown',
            'group-grant-confirm',
            'group-revoke-only-confirm',
          ].includes(state)
        ) {
          const switches = document.querySelectorAll<HTMLButtonElement>('[role="switch"]');
          const indexes =
            state === 'group-grant-confirm'
              ? [2]
              : state === 'group-revoke-only-confirm'
                ? [1]
                : [1, 2, 3];
          for (const index of indexes) switches[index]!.click();
          await nextTick();
          await waitFor(
            () =>
              document.querySelectorAll('.headman-group__permission[data-changed="true"]')
                .length === indexes.length,
          );
          if (state !== 'group-permissions') {
            click('.headman-group__footer button');
            await waitFor(() => document.querySelector('dialog[open]') !== null);
          }
          if (
            [
              'group-permission-mixed-step2',
              'group-permission-stale',
              'group-permission-unknown',
            ].includes(state)
          ) {
            click('dialog[open] [data-action="delete"]');
            await waitFor(
              () => document.querySelector('dialog[open] h2')?.textContent === 'Применить права?',
            );
          }
        }
        if (state === 'group-dirty-leave') {
          click('[role="switch"]');
          click('.headman-group__back');
        }
        if (state === 'group-revoke-confirm') {
          document
            .querySelectorAll<HTMLButtonElement>('[role="switch"][aria-checked="true"]')
            .forEach((button) => button.click());
          await waitFor(() => !document.querySelector('[role="switch"][aria-checked="true"]'));
          click('.headman-group__footer button');
        }
      }
    }
    if (!group && state.startsWith('subjects-historical')) {
      const select = document.querySelector<HTMLSelectElement>(
        '.headman-subjects__semester select',
      )!;
      select.value = '2';
      select.dispatchEvent(new Event('change', { bubbles: true }));
      await nextTick();
    }
    if (
      !group &&
      !['subjects-list', 'subjects-historical', 'subjects-read-error'].includes(state)
    ) {
      if (['subjects-new', 'subjects-create-rejected'].includes(state))
        click('.headman-subjects__add');
      else click('[data-subject-id="1"]');
      await waitFor(
        () =>
          document.querySelector(
            '[data-subject-view="editor"][aria-busy="false"][data-subject-detail-ready="true"]',
          ) !== null,
      );
      if (['subjects-picker', 'subjects-partial', 'subjects-adoption-pending'].includes(state)) {
        click('.headman-subjects__assignment:last-of-type .headman-subjects__teacher');
        await waitFor(
          () => document.querySelectorAll('.headman-subjects__teacher-options button').length === 4,
        );
        if (state === 'subjects-picker') {
          click('.headman-subjects__teacher-options button:nth-child(3)');
          await nextTick();
          await waitFor(
            () =>
              document
                .querySelector('.headman-subjects__teacher-options button:nth-child(3)')
                ?.getAttribute('aria-pressed') === 'true' &&
              document.querySelector<HTMLButtonElement>('.headman-subjects__footer button')
                ?.disabled === false,
          );
        }
        if (['subjects-partial', 'subjects-adoption-pending'].includes(state)) {
          click('.headman-subjects__teacher-options button:nth-child(3)');
          await clickEnabled('.headman-subjects__footer button');
          await waitFor(
            () =>
              document.querySelector(
                '[data-subject-view="editor"][aria-busy="false"][data-subject-detail-ready="true"]',
              ) !== null,
          );
          setInput('.headman-subjects__name input', 'Информационные системы · изменение');
          await clickEnabled('.headman-subjects__footer button');
          await waitFor(
            () =>
              !!document.querySelector('.headman-subjects__recovery') &&
              document.querySelector('main[aria-busy="false"]') !== null,
          );
        }
      }
      if (['subjects-remove-confirm', 'subjects-removal-pending'].includes(state)) {
        click('.headman-subjects__teacher');
        await waitFor(() => document.querySelector('[data-subject-view="remove"]') !== null);
        if (state === 'subjects-removal-pending') {
          click('[data-subject-view="remove"] [data-action="delete"]');
          await waitFor(
            () =>
              document.querySelector(
                '[data-subject-view="editor"][aria-busy="false"][data-subject-detail-ready="true"]',
              ) !== null,
          );
          await clickEnabled('.headman-subjects__footer button');
          await waitFor(
            () =>
              !!document.querySelector('.headman-subjects__recovery') &&
              document.querySelector('main[aria-busy="false"]') !== null,
          );
        }
      }
      if (state === 'subjects-create-rejected') {
        setInput('.headman-subjects__name input', 'Новый предмет · черновик');
        click('.headman-subjects__types button');
        await clickEnabled('.headman-subjects__footer button');
        await waitFor(
          () =>
            document.querySelector('main[aria-busy="false"]') !== null &&
            !document.querySelector('.headman-subjects__recovery') &&
            !!document.querySelector('[data-severity="error"]'),
        );
      }
      if (state === 'subjects-dirty-leave') {
        setInput('.headman-subjects__name input', 'Информационные системы · черновик');
        click('.headman-subjects__back');
        await waitFor(() => document.querySelector('dialog[open]') !== null);
      }
      if (state === 'subjects-unknown') {
        setInput('.headman-subjects__name input', 'Информационные системы · изменение');
        await clickEnabled('.headman-subjects__footer button');
        await waitFor(
          () =>
            !!document.querySelector('.headman-subjects__recovery') &&
            document.querySelector('main[aria-busy="false"]') !== null,
        );
      }
    }
    if (state === 'group-permission-stale') {
      offline.value = true;
      await nextTick();
      apis.changeAssistantPermissions();
      offline.value = false;
      await waitFor(
        () =>
          !document.querySelector('dialog[open]') &&
          document.querySelectorAll('.headman-group__permission[data-changed="true"]').length === 1,
      );
    }
    if (state === 'group-permission-unknown') {
      click('dialog[open] [data-action="confirm"]');
      await waitFor(
        () =>
          document
            .querySelector('.student-warning')
            ?.textContent?.includes('Ответ на сохранение') === true ||
          document.body.textContent?.includes('Ответ на сохранение') === true,
      );
    }
    if (state === 'group-offline') offline.value = true;
    if (['group-dirty-leave', 'group-revoke-confirm'].includes(state))
      await waitFor(() => document.querySelector('dialog[open]') !== null);
    await nextTick();
    document.documentElement.dataset.groupSubjectsFixtureView =
      document
        .querySelector('main')
        ?.getAttribute(group ? 'data-group-view' : 'data-subject-view') ?? '';
    document.documentElement.dataset.groupSubjectsFixtureReady = 'true';
  } catch (cause) {
    document.documentElement.dataset.groupSubjectsFixtureError =
      cause instanceof Error ? cause.message : String(cause);
  }
});
onBeforeUnmount(() => {
  disposed = true;
  viewObserver.disconnect();
  stopGuard();
  stopRoute();
  document.documentElement.style.fontSize = originalFont;
});
</script>
<template>
  <MobileShell
    :route="route"
    :navigation="navigation"
    :nav-items="createHeadmanNavigationItems()"
    :host="host"
  >
    <template #back>
      <span hidden />
    </template>
    <HeadmanGroupScreen
      v-if="group"
      ref="groupScreen"
      :api="apis.group"
      :group-id="4"
      actor-user-id="1"
      :actor-headman="true"
      :offline="offline"
      :read-only="state === 'group-readonly'"
      @back="navigation.back()"
    />
    <HeadmanSubjectsScreen
      v-else
      ref="subjectsScreen"
      :api="apis.subjects"
      :group-id="4"
      :actor-user-id="`fixture-group-subjects-${state}-${query.get('run') ?? 'baseline'}`"
      :offline="offline"
      :read-only="state === 'subjects-readonly'"
      @back="navigation.back()"
    />
  </MobileShell>
  <button
    v-if="shell === 'tma' && hostVisible && query.get('controls') === '1'"
    type="button"
    @click="hostBack"
  >
    Host Back
  </button>
</template>
