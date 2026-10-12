<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import AdminUsersScreen from '../features/admin-users/AdminUsersScreen.vue';
import AdminRoleNavigation from '../features/admin-semester/AdminRoleNavigation.vue';
import { adminUsersReviewStates, createAdminUsersReviewApis } from './admin-users-fixtures';
import './admin-users-review.pcss';

const query = new URLSearchParams(window.location.search);
const state = query.get('state') ?? 'list';
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa';
const capture = query.get('capture') === '1';
const { client, accessClient, authority, observations } = createAdminUsersReviewApis(state);
const screen = ref<InstanceType<typeof AdminUsersScreen> | null>(null);
const navigationVisible = ref(true);
const originalFont = document.documentElement.style.fontSize;
if ((query.get('rootFont') ?? query.get('font')) === '20')
  document.documentElement.style.fontSize = '20px';
let disposed = false;
const disposers = new Set<() => void>();
function waitFor(predicate: () => boolean): Promise<void> {
  if (predicate()) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const observer = new MutationObserver(check);
    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error('Состояние не завершило загрузку.'));
    }, 10000);
    function cleanup(): void {
      observer.disconnect();
      clearTimeout(timeout);
      disposers.delete(cleanup);
    }
    function check(): void {
      if (predicate()) {
        cleanup();
        resolve();
      }
    }
    disposers.add(cleanup);
    observer.observe(document.getElementById('app')!, {
      attributes: true,
      childList: true,
      subtree: true,
      characterData: true,
    });
    check();
  });
}
function main(): HTMLElement | null {
  return document.querySelector('.admin-users-screen');
}
function readyView(view: string): boolean {
  return main()?.getAttribute('data-view') === view && main()?.getAttribute('aria-busy') !== 'true';
}
function button(label: string, scope: Element = main()!): HTMLButtonElement {
  const found = Array.from(scope.querySelectorAll<HTMLButtonElement>('button')).find(
    (item) => item.textContent?.trim() === label,
  );
  if (!found) throw new Error(`Не найдено действие: ${label}`);
  return found;
}
function fill(selector: string, value: string): void {
  const input = main()?.querySelector<HTMLInputElement>(selector);
  if (!input) throw new Error('Поле проверки недоступно.');
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
}
function submit(): void {
  main()?.querySelector<HTMLFormElement>('.admin-users-form')?.requestSubmit();
}
async function chooseRole(role: string): Promise<void> {
  button(role, main()!.querySelector('[data-picker="create-role"]')!).click();
  await nextTick();
}
async function fillCreate(): Promise<void> {
  fill(
    '[autocomplete="family-name"]',
    state === 'create-student' || state === 'create-group' ? 'Николаева' : 'Волкова',
  );
  fill(
    '[autocomplete="given-name"]',
    state === 'create-student' || state === 'create-group' ? 'Алина' : 'Мария',
  );
  fill('input[inputmode="numeric"]', '748391026');
  if (main()?.querySelector('[data-picker="group"]')) {
    await waitFor(() =>
      Boolean(main()?.querySelector('[data-picker="group"] .admin-users-select__options > button')),
    );
    button('ИКБО-01-24', main()!.querySelector('[data-picker="group"]')!).click();
  }
  const employee = main()?.querySelector<HTMLInputElement>('input[placeholder="Т-00421"]');
  if (employee) fill('input[placeholder="Т-00421"]', 'Т-00421');
  await nextTick();
}
async function scenario(): Promise<void> {
  await Promise.all([
    document.fonts.load('400 16px "Onest Variable"', 'Администратор ABC123'),
    document.fonts.load('600 16px "Onest Variable"', 'Администратор ABC123'),
  ]);
  await document.fonts.ready;
  if (
    !document.fonts.check('400 16px "Onest Variable"', 'Администратор ABC123') ||
    !document.fonts.check('600 16px "Onest Variable"', 'Администратор ABC123')
  )
    throw new Error('Review fonts are not ready');
  document.documentElement.dataset.adminFixtureFontsReady = 'true';
  await waitFor(() => readyView('list'));
  if (state.startsWith('filters')) {
    button('Фильтры и сортировка').click();
    await nextTick();
    button(
      state === 'filters-student-statuses'
        ? 'Студенты'
        : state === 'filters-admin-statuses'
          ? 'Администраторы'
          : state === 'filters-headman-statuses'
            ? 'Старосты'
            : 'Преподаватели',
      main()!.querySelector('[data-picker="filter-role"]')!,
    ).click();
    await nextTick();
    button('Активна', main()!.querySelector('[data-picker="filter-status"]')!).click();
    await nextTick();
    const picker = state.endsWith('-statuses')
      ? 'filter-status'
      : state === 'filters-role'
        ? 'filter-role'
        : state === 'filters-status'
          ? 'filter-status'
          : state === 'filters-sort'
            ? 'filter-sort'
            : null;
    if (picker) main()!.querySelector<HTMLDetailsElement>(`[data-picker="${picker}"]`)!.open = true;
  } else if (
    state.startsWith('create') ||
    state === 'created' ||
    state === 'created-no-credentials' ||
    state === 'validation' ||
    state === 'permissions-create'
  ) {
    button('Создать пользователя').click();
    await nextTick();
    if (['create-teacher', 'created', 'create-uncertain', 'created-no-credentials'].includes(state))
      await chooseRole('Преподаватель');
    if (state === 'create-admin' || state === 'permissions-create')
      await chooseRole('Администратор');
    await fillCreate();
    if (state === 'permissions-create') {
      main()!.querySelector<HTMLButtonElement>('.admin-users-access-choice')!.click();
      await waitFor(() => Boolean(main()?.querySelector('.admin-permission-editor')));
      main()!.querySelector<HTMLInputElement>('[data-permission="USERS_MANAGE"]')!.click();
      await nextTick();
    }
    if (state === 'create-role')
      main()!.querySelector<HTMLDetailsElement>('[data-picker="create-role"]')!.open = true;
    if (state === 'create-group')
      main()!.querySelector<HTMLDetailsElement>('[data-picker="group"]')!.open = true;
    if (state === 'validation') {
      fill('[autocomplete="family-name"]', '');
      main()
        ?.querySelector('.admin-users-form')
        ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    }
    if (state === 'created' || state === 'created-no-credentials' || state === 'create-uncertain') {
      submit();
      await waitFor(() =>
        state === 'created' || state === 'created-no-credentials'
          ? readyView('created')
          : Boolean(main()?.querySelector('[role="alert"], [data-tone="warning"]')) &&
            readyView('create'),
      );
    }
  } else if (!['list', 'list-multirole', 'empty', 'error', 'limited-no-access'].includes(state)) {
    const student =
      state.startsWith('detail-student') ||
      state === 'detail-headman-suspended' ||
      state === 'transfer';
    main()!
      .querySelector<HTMLButtonElement>(`[data-user-id="${student ? 1 : 3}"]`)!
      .click();
    await waitFor(() => readyView('detail'));
    if (
      !state.startsWith('detail') &&
      state !== 'limited-readonly' &&
      state !== 'limited-target-admin'
    ) {
      if (!state.startsWith('recovery') && state !== 'limited-credentials') {
        button('Редактировать данные').click();
        await waitFor(() => readyView('edit'));
      }
      if (state.startsWith('permissions') && state !== 'permissions-create') {
        button('Настроить права администратора').click();
        await waitFor(
          () =>
            Boolean(main()?.querySelector('.admin-permission-editor')) &&
            !main()?.querySelector('[role="status"]'),
        );
        if (state.startsWith('permissions-mixed')) {
          main()!.querySelector<HTMLInputElement>('[data-permission="GROUPS_VIEW"]')!.click();
          await nextTick();
        }
        if (state === 'permissions-full')
          main()!.querySelectorAll<HTMLInputElement>('[name="admin-access-mode"]')[1]!.click();
        else if (!state.startsWith('permissions-revoke')) {
          main()!.querySelector<HTMLInputElement>('[data-permission="USERS_MANAGE"]')!.click();
          await nextTick();
        }
        if (
          [
            'permissions-confirm',
            'permissions-conflict',
            'permissions-uncertain',
            'permissions-saved',
            'permissions-mixed-revoke',
            'permissions-mixed-grant',
            'permissions-mixed-saved',
          ].includes(state)
        ) {
          button('Проверить изменения').click();
          await waitFor(() => Boolean(main()?.querySelector('dialog[open]')));
        }
        if (state === 'permissions-revoke-confirm') {
          button('Отозвать права администратора').click();
          await waitFor(() => Boolean(main()?.querySelector('dialog[open]')));
        }
        if (state === 'permissions-mixed-grant' || state === 'permissions-mixed-saved') {
          main()!.querySelector<HTMLInputElement>('dialog[open] input[type="checkbox"]')!.click();
          await nextTick();
          button('Подтвердить отзыв', main()!.querySelector('dialog[open]')!).click();
          await waitFor(
            () =>
              Boolean(main()?.querySelector('dialog[open] input[type="checkbox"]')) &&
              main()!.querySelector('dialog[open] h2')?.textContent?.trim() === 'Применить права?',
          );
          if (observations.accessMutations !== 0)
            throw new Error('Смешанные права нельзя сохранять после первого подтверждения.');
          if (state === 'permissions-mixed-saved') {
            main()!.querySelector<HTMLInputElement>('dialog[open] input[type="checkbox"]')!.click();
            await nextTick();
            button('Применить права', main()!.querySelector('dialog[open]')!).click();
            await waitFor(() => readyView('detail'));
            if (Number(observations.accessMutations) !== 1)
              throw new Error('Смешанные права сохраняются одним CAS после двух подтверждений.');
          }
        }
        if (
          ['permissions-conflict', 'permissions-uncertain', 'permissions-saved'].includes(state)
        ) {
          main()!.querySelector<HTMLInputElement>('dialog[open] input[type="checkbox"]')!.click();
          await nextTick();
          button('Применить права', main()!.querySelector('dialog[open]')!).click();
          await waitFor(() =>
            state === 'permissions-saved'
              ? readyView('detail')
              : Boolean(main()?.querySelector('[role="alert"], [data-tone="warning"]')) &&
                main()?.getAttribute('aria-busy') !== 'true',
          );
        }
      }
      if (state === 'role-editor') {
        button('Изменить статус').click();
        await nextTick();
      }
      if (state === 'add-role') button('Добавить роль').click();
      if (state === 'transfer') {
        button('Перевести в другую группу').click();
        await nextTick();
        await waitFor(() =>
          Boolean(
            main()?.querySelector('[data-picker="group"] .admin-users-select__options > button'),
          ),
        );
      }
      if (state.startsWith('recovery') || state === 'limited-credentials') {
        button('Восстановить доступ').click();
        await waitFor(() => readyView('recovery'));
        if (
          observations.recoveryPosts !== 0 ||
          main()?.querySelector('.recovery-link-panel__field')
        )
          throw new Error('Вход из карточки не должен выдавать ссылку автоматически.');
        if (
          [
            'recovery-issued',
            'recovery-done',
            'recovery-back',
            'recovery-new-issue',
            'recovery-expired',
          ].includes(state)
        ) {
          button('Создать ссылку восстановления').click();
          await waitFor(() =>
            state === 'recovery-expired'
              ? Boolean(main()?.querySelector('[data-tone="warning"]'))
              : readyView('recovery-issued') &&
                Boolean(main()?.querySelector('.recovery-link-panel__field')),
          );
          if (state !== 'recovery-expired' && Number(observations.recoveryPosts) !== 1)
            throw new Error('Выдача должна открывать один новый результат.');
          if (['recovery-done', 'recovery-back', 'recovery-new-issue'].includes(state)) {
            const previous = main()!.querySelector<HTMLInputElement>(
              '.recovery-link-panel__field input',
            )!.value;
            if (state === 'recovery-back') screen.value!.back();
            else button('Готово').click();
            await waitFor(() => readyView('detail'));
            if (main()?.querySelector('.recovery-link-panel__field'))
              throw new Error('Готово/Назад должно очищать чувствительный результат.');
            if (state === 'recovery-new-issue') {
              button('Восстановить доступ').click();
              await waitFor(() => readyView('recovery'));
              if (main()?.querySelector('.recovery-link-panel__field'))
                throw new Error('Повторное открытие не должно возвращать прежнюю ссылку.');
              button('Создать ссылку восстановления').click();
              await waitFor(
                () =>
                  readyView('recovery-issued') &&
                  Boolean(main()?.querySelector('.recovery-link-panel__field')),
              );
              if (
                Number(observations.recoveryPosts) !== 2 ||
                main()!.querySelector<HTMLInputElement>('.recovery-link-panel__field input')!
                  .value === previous
              )
                throw new Error('Новый запрос должен выдать новую ссылку.');
            }
          }
        }
        if (state === 'recovery-uncertain' || state === 'recovery-after-unknown') {
          button('Создать ссылку восстановления').click();
          await waitFor(() =>
            Boolean(main()?.querySelector('.recovery-link-panel [data-tone="warning"]')),
          );
          if (
            button('Создать новую ссылку').disabled ||
            Number(observations.recoveryPosts) !== 1 ||
            main()?.querySelector('.recovery-link-panel__field')
          )
            throw new Error(
              'Неизвестный ответ допускает только явную новую выдачу без ложного результата.',
            );
          await nextTick();
          if (Number(observations.recoveryPosts) !== 1)
            throw new Error('Автоматический повтор запрещён.');
          if (state === 'recovery-after-unknown') {
            button('Создать новую ссылку').click();
            await waitFor(
              () =>
                readyView('recovery-issued') &&
                Boolean(main()?.querySelector('.recovery-link-panel__field')),
            );
            if (Number(observations.recoveryPosts) !== 2)
              throw new Error('Явное новое действие должно выдать новую ссылку.');
          }
        }
      }
      if (state.startsWith('archive')) {
        button('Архивировать аккаунт').click();
        await waitFor(() => Boolean(main()?.querySelector('.admin-users-data')));
        if (state === 'archive-password-error' || state === 'archive-password-corrected') {
          const target = main()!.querySelector('.admin-users-summary')!.textContent;
          fill('input[autocomplete="current-password"]', 'synthetic-rejected-proof');
          main()!
            .querySelector<HTMLInputElement>('.admin-checkbox input[type="checkbox"]')!
            .click();
          await nextTick();
          submit();
          await waitFor(
            () =>
              Boolean(main()?.querySelector('[role="alert"], [data-tone="warning"]')) &&
              readyView('archive'),
          );
          const password = main()!.querySelector<HTMLInputElement>(
            'input[autocomplete="current-password"]',
          );
          if (
            !password ||
            password.disabled ||
            password.value ||
            main()!.querySelector('.admin-users-summary')?.textContent !== target ||
            observations.archivePosts !== 1
          )
            throw new Error(
              'Неверный пароль должен сохранить аккаунт и очистить редактируемое поле.',
            );
          if (state === 'archive-password-corrected') {
            fill('input[autocomplete="current-password"]', 'synthetic-corrected-proof');
            await nextTick();
            submit();
            await waitFor(
              () => readyView('list') && Boolean(main()?.querySelector('[role="status"]')),
            );
            if (
              Number(observations.archivePosts) !== 2 ||
              !observations.archiveOperationStable ||
              !observations.archiveProofUpdated
            )
              throw new Error('Повтор должен сохранить UUID/digest и взять новый proof.');
          }
        }
        if (['archive-expired', 'archive-conflict', 'archive-uncertain'].includes(state)) {
          main()!
            .querySelector<HTMLInputElement>('.admin-checkbox input[type="checkbox"]')!
            .click();
          await nextTick();
          submit();
          await waitFor(
            () =>
              Boolean(main()?.querySelector('[role="alert"], [data-tone="warning"]')) &&
              main()?.getAttribute('aria-busy') !== 'true',
          );
        }
      }
      if (state.startsWith('restore')) {
        button('Восстановить аккаунт').click();
        await nextTick();
        if (state === 'restore-result') {
          main()!
            .querySelector<HTMLInputElement>('.admin-checkbox input[type="checkbox"]')!
            .click();
          await nextTick();
          submit();
          await waitFor(() => readyView('list'));
        }
      }
      if (state === 'profile-error') {
        fill('input[maxlength="32"]', 'Т-00999');
        submit();
        await waitFor(() =>
          Boolean(main()?.querySelector('[role="alert"], [data-tone="warning"]')),
        );
      }
    }
  }
  if (
    state === 'permissions-conflict' &&
    (!main()?.querySelector('[data-tone="danger"]') ||
      !main()?.querySelector<HTMLInputElement>('[data-permission="USERS_MANAGE"]')?.checked)
  )
    throw new Error('Конфликт должен показать красное предупреждение и текущие отмеченные права.');
  await nextTick();
  await document.fonts.ready;
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  if (disposed) return;
  document.documentElement.dataset.adminUsersAccessMutations = String(observations.accessMutations);
  document.documentElement.dataset.adminUsersRecoveryPosts = String(observations.recoveryPosts);
  document.documentElement.dataset.adminUsersArchivePosts = String(observations.archivePosts);
  document.documentElement.dataset.adminUsersArchiveOperationStable = String(
    observations.archiveOperationStable,
  );
  document.documentElement.dataset.adminUsersHeading =
    main()?.querySelector('h1')?.textContent?.trim() ?? '';
  document.documentElement.dataset.adminUsersReady = 'true';
  document.documentElement.dataset.adminUsersState = state;
}
function hostBack(): void {
  screen.value?.back();
}
onMounted(() => {
  document.documentElement.dataset.adminUsersReady = 'false';
  void scenario().catch(() => {
    document.documentElement.dataset.adminUsersReady = 'error';
  });
});
onBeforeUnmount(() => {
  disposed = true;
  for (const dispose of disposers) dispose();
  document.documentElement.style.fontSize = originalFont;
  delete document.documentElement.dataset.adminUsersReady;
  delete document.documentElement.dataset.adminUsersState;
  delete document.documentElement.dataset.adminUsersAccessMutations;
  delete document.documentElement.dataset.adminUsersRecoveryPosts;
  delete document.documentElement.dataset.adminUsersArchivePosts;
  delete document.documentElement.dataset.adminUsersArchiveOperationStable;
});
</script>
<template>
  <div
    class="admin-users-review"
    :data-back-owner="shell === 'tma' ? 'host' : 'product'"
  >
    <nav
      v-if="!capture"
      class="admin-users-review-catalog"
      aria-label="Состояния проверки"
    >
      <a
        v-for="item in adminUsersReviewStates"
        :key="item.state"
        :href="`?state=${item.state}&shell=${shell}`"
      >{{ item.label }}</a>
    </nav>
    <button
      v-if="shell === 'tma' && !navigationVisible && !capture"
      class="admin-users-review-hostback"
      type="button"
      @click="hostBack"
    >
      Назад · Telegram
    </button>
    <AdminUsersScreen
      ref="screen"
      :client="client"
      :access-client="accessClient"
      :authority="authority"
      @navigation-visibility="navigationVisible = $event"
    />
    <AdminRoleNavigation
      v-if="navigationVisible"
      active="users"
    />
  </div>
</template>
