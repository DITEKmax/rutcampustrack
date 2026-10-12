<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import AdminSemesterScreen from '../features/admin-semester/AdminSemesterScreen.vue';
import {
  adminSemesterAuthority,
  adminSemesterStates,
  createAdminSemesterFixture,
} from './admin-semester-fixtures';
import './admin-academic-review.pcss';
const query = new URLSearchParams(location.search),
  state = query.get('state') ?? 'list',
  tma = query.get('shell') === 'tma';
const client = createAdminSemesterFixture(state),
  screen = ref<InstanceType<typeof AdminSemesterScreen> | null>(null);
const authority = ref(adminSemesterAuthority(state));
const showScreen = ref(true);
function back(): void {
  if (state === 'delete-background-resume') showScreen.value = false;
}
const originalFont = document.documentElement.style.fontSize;
if (query.get('rootFont') === '20') document.documentElement.style.fontSize = '20px';
let ownerErrors = 0;
function ownerError(): void {
  ownerErrors += 1;
  document.documentElement.dataset.adminFixtureOwnerErrors = String(ownerErrors);
}
function assertDeletionDenied(): void {
  if (
    document.querySelector(
      '.admin-semester-deletion-confirmation, .admin-semester-deletion-progress',
    )
  )
    throw new Error('Forbidden deletion task survived');
  if (
    window.sessionStorage.getItem(
      'rct:admin:semester-delete:v1:' + encodeURIComponent('synthetic-admin-semester-' + state),
    ) !== null
  )
    throw new Error('Forbidden deletion preview remained persisted');
  if (ownerErrors !== 1) throw new Error('Forbidden deletion did not notify owner exactly once');
}
let disposed = false;
async function waitFor(condition: () => boolean): Promise<void> {
  for (let frame = 0; frame < 600 && !disposed; frame++) {
    if (condition()) {
      await nextTick();
      return;
    }
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  }
  throw new Error('Semester fixture did not settle: ' + state);
}
function click(selector: string): void {
  const element = document.querySelector<HTMLElement>(selector);
  if (!element) throw new Error('Missing semester action: ' + selector);
  element.click();
}
function clickText(selector: string, text: string): void {
  const element = [...document.querySelectorAll<HTMLElement>(selector)].find(
    (item) => item.textContent?.trim() === text,
  );
  if (!element) throw new Error('Missing semester action: ' + text);
  element.click();
}
function input(selector: string, value: string): void {
  const field = document.querySelector<HTMLInputElement>(selector);
  if (!field) throw new Error('Missing semester field: ' + selector);
  field.value = value;
  field.dispatchEvent(new Event('input', { bubbles: true }));
}
function chooseState(event: Event): void {
  const url = new URL(location.href);
  url.searchParams.set('state', (event.target as HTMLSelectElement).value);
  location.href = url.toString();
}
onMounted(async () => {
  try {
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
    if (state === 'missing-authority') {
      await waitFor(() => document.body.textContent?.includes('текущими правами') === true);
      if (document.documentElement.dataset.adminFixtureRequests !== '0')
        throw new Error('Missing authority sent a request');
    } else if (['error', 'forbidden'].includes(state))
      await waitFor(() => !!document.querySelector('[role="alert"]'));
    else if (state === 'empty')
      await waitFor(() => !!document.querySelector('[data-state="empty"]'));
    else {
      await waitFor(() => document.querySelectorAll('[data-semester-id]').length === 4);
      if (state === 'revoked') {
        click('[data-semester-id="1"] > button');
        authority.value = null;
        await waitFor(() => document.body.textContent?.includes('текущими правами') === true);
        if (
          document.querySelector('[data-semester-id]') ||
          document.querySelector('.admin-semester-form')
        )
          throw new Error('Semester data survived revocation');
      }
      if (['create', 'start-calendar', 'spring', 'overlap'].includes(state)) {
        click('.admin-semester-screen__new');
        await waitFor(() => !!document.querySelector('.admin-semester-form'));
        input('.admin-semester-form > label input', '2026');
        click('#admin-semester-from-value');
        await waitFor(() => !!document.querySelector('[data-date="2026-09-01"]'));
        click('[data-date="2026-09-01"]');
        if (state !== 'start-calendar') {
          screen.value?.back();
          await nextTick();
        }
        if (state === 'spring') click('input[value="SPRING"]');
        if (state === 'overlap') {
          click('.admin-semester-form button[type="submit"]');
          await waitFor(() => !!document.querySelector('.admin-semester-overlap-warning'));
        }
      }
      if (['edit', 'end-calendar', 'legacy', 'required-classification'].includes(state)) {
        click('[data-semester-id="1"] > button');
        await waitFor(
          () =>
            document.querySelector<HTMLInputElement>('.admin-semester-form > label input')
              ?.value === (['legacy', 'required-classification'].includes(state) ? '' : '2026'),
        );
        if (state === 'end-calendar') {
          click('#admin-semester-to-value');
          await waitFor(() => !!document.querySelector('[data-date="2027-01-31"]'));
        }
      }
      if (state === 'required-classification') {
        await waitFor(
          () =>
            document.querySelector<HTMLButtonElement>('.admin-semester-form button[type="submit"]')
              ?.disabled === true &&
            !!document.querySelector('#admin-semester-type-error') &&
            !!document.querySelector('#admin-semester-year-error'),
        );
        if (
          !document.querySelector<HTMLButtonElement>('.admin-semester-form button[type="submit"]')
            ?.disabled ||
          !document.querySelector('#admin-semester-type-error') ||
          !document.querySelector('#admin-semester-year-error')
        )
          throw new Error('Incomplete classification offered save');
      }
      if (state === 'expired-archive') {
        const restore = [
          ...document.querySelectorAll<HTMLButtonElement>('[data-semester-id="3"] button'),
        ].find((button) => button.textContent?.trim() === 'Восстановить');
        if (
          !restore?.disabled ||
          !document
            .querySelector('[data-semester-id="3"]')
            ?.textContent?.includes('Срок семестра истёк')
        )
          throw new Error('Expired archive offered restore');
      }
      if (['date-extension', 'future-valid'].includes(state)) {
        if (state === 'date-extension') clickText('[data-semester-id="3"] button', 'Редактировать');
        else click('.admin-semester-screen__new');
        await waitFor(
          () =>
            !!document.querySelector('.admin-semester-form') &&
            !document.querySelector('.admin-semester-form [role="status"]'),
        );
        if (state === 'future-valid') {
          input('.admin-semester-form > label input', '2028');
          click('#admin-semester-from-value');
          await waitFor(() => !!document.querySelector('.semester-calendar'));
          for (let month = 0; month < 24; month++) {
            click('[aria-label="Следующий месяц"]');
            await nextTick();
          }
          click('[data-date="2028-09-01"]');
          screen.value?.back();
          await nextTick();
        }
        click('#admin-semester-to-value');
        await waitFor(() => !!document.querySelector('.semester-calendar'));
        const target = state === 'date-extension' ? '2026-10-31' : '2029-01-31',
          months = state === 'date-extension' ? 4 : 24;
        for (let month = 0; month < months; month++) {
          click('[aria-label="Следующий месяц"]');
          await nextTick();
        }
        click(`[data-date="${target}"]`);
        screen.value?.back();
        await nextTick();
        if (
          document.querySelector<HTMLButtonElement>('.admin-semester-form button[type="submit"]')
            ?.disabled
        )
          throw new Error('Valid future/extension form remained disabled');
      }
      if (['activation-confirm', 'activated'].includes(state)) {
        clickText('[data-semester-id="2"] button', 'Сделать текущим');
        await waitFor(
          () => !!document.querySelector('[data-admin-semester-view="activate-confirm"]'),
        );
        if (state === 'activated') {
          click('.admin-semester-confirmation > button');
          await waitFor(() =>
            [...document.querySelectorAll('[role="status"]')].some((element) =>
              /Активирован|Активация.*запланирована/.test(element.textContent ?? ''),
            ),
          );
        }
      }
      if (
        [
          'archive-confirm',
          'archive-unknown',
          'archiving',
          'archived',
          'restore-confirm',
          'restored',
        ].includes(state)
      ) {
        const restore = state.startsWith('restore'),
          id = restore ? 3 : 1;
        clickText(`[data-semester-id="${id}"] button`, restore ? 'Восстановить' : 'Архивировать');
        await waitFor(() => !!document.querySelector('.admin-semester-confirmation'));
        if (!state.endsWith('confirm')) {
          clickText(
            '.admin-semester-confirmation button',
            restore ? 'Подтвердить восстановление' : 'Подтвердить архивацию',
          );
          await waitFor(() =>
            state === 'archive-unknown'
              ? document
                  .querySelector('.admin-semester-archive-status')
                  ?.textContent?.includes('Ответ не подтверждён') === true
              : state === 'archiving'
                ? !!document.querySelector('.admin-semester-archive-status')
                : document
                    .querySelector('.admin-semester-state--success')
                    ?.textContent?.includes(restore ? 'восстановлен' : 'архивирован') === true,
          );
        }
      }
      if (state === 'delete-resume-forbidden') {
        await waitFor(() => ownerErrors === 1);
        assertDeletionDenied();
      }
      if (state.startsWith('delete') && state !== 'delete-resume-forbidden') {
        clickText('[data-semester-id="1"] button', 'Удалить');
        await waitFor(() => !!document.querySelector('.admin-semester-deletion-confirmation'));
        if (state !== 'delete-confirm') {
          input('#admin-semester-delete-password', 'Synthetic-only-Password1!');
          await nextTick();
          if (
            document.querySelector<HTMLButtonElement>(
              '.admin-semester-deletion-confirmation button[type="submit"]',
            )?.disabled
          )
            throw new Error('Deletion submit remained disabled after password input');
          click('.admin-semester-deletion-confirmation button[type="submit"]');
          await waitFor(() =>
            [
              'delete-stale',
              'delete-expired',
              'delete-password',
              'delete-password-extras',
              'delete-forbidden',
              'delete-unknown',
            ].includes(state)
              ? !!document.querySelector('.admin-semester-state--error')
              : !!document.querySelector('.admin-semester-deletion-progress'),
          );
          if (['delete-forbidden', 'delete-poll-forbidden'].includes(state)) {
            await waitFor(() => ownerErrors === 1);
            assertDeletionDenied();
          }
          if (['delete-password', 'delete-password-extras'].includes(state)) {
            await waitFor(
              () =>
                document
                  .querySelector('.admin-semester-state--error')
                  ?.textContent?.includes('Пароль не подтверждён') === true &&
                !!document.querySelector('.admin-semester-deletion-confirmation'),
            );
            if (ownerErrors !== 0) throw new Error('Password retry notified owner');
            if (document.querySelector('.admin-semester-deletion-progress'))
              throw new Error('Password retry duplicated the preview');
          }
          if (state === 'delete-unknown') {
            await waitFor(
              () =>
                document
                  .querySelector(
                    '.admin-semester-deletion-confirmation .admin-semester-state--error',
                  )
                  ?.textContent?.includes('Ответ сервера не подтверждён') === true,
            );
            if (document.querySelector('.admin-semester-deletion-progress'))
              throw new Error('Uncertain deletion duplicated the preview');
          }
        }
      }
      if (state.startsWith('delete-background')) {
        if (
          [...document.querySelectorAll('button')].some(
            (item) => item.textContent?.trim() === 'Продолжить проверку',
          )
        )
          throw new Error('Ordinary pending deletion offered manual polling');
        if (state === 'delete-background-resume') {
          screen.value?.back();
          await nextTick();
          const key =
            'rct:admin:semester-delete:v1:' +
            encodeURIComponent('synthetic-admin-semester-' + state);
          if (!sessionStorage.getItem(key))
            throw new Error('Navigation lost durable deletion command');
          showScreen.value = true;
          await nextTick();
        }
        if (state !== 'delete-background')
          await waitFor(
            () =>
              document
                .querySelector('.admin-semester-state--success')
                ?.textContent?.includes('удалён') === true &&
              !document.querySelector('[data-semester-id="1"]'),
          );
      }
    }
    await nextTick();
    document.documentElement.dataset.adminFixtureHeading =
      document.querySelector('main h1')?.textContent?.trim() ?? '';
    document.documentElement.dataset.adminFixtureState = state;
    document.documentElement.dataset.adminFixtureReady = 'true';
  } catch (cause) {
    document.documentElement.dataset.adminFixtureError =
      cause instanceof Error ? cause.message : String(cause);
  }
});
onBeforeUnmount(() => {
  disposed = true;
  document.documentElement.style.fontSize = originalFont;
  delete document.documentElement.dataset.adminFixtureReady;
});
</script>
<template>
  <div
    class="admin-academic-review"
    :data-back-owner="tma ? 'host' : 'product'"
  >
    <aside
      v-if="query.get('capture') !== '1'"
      class="admin-academic-review__toolbar"
    >
      <label>Состояние
        <select
          :value="state"
          @change="chooseState"
        ><option
          v-for="item in adminSemesterStates"
          :key="item"
        >{{ item }}</option></select></label><button
        v-if="tma"
        type="button"
        @click="screen?.back()"
      >
        Telegram BackButton
      </button>
    </aside>
    <AdminSemesterScreen
      v-if="showScreen"
      ref="screen"
      :client="client"
      :authority="authority"
      @owner-error="ownerError"
      @back="back"
    />
  </div>
</template>
