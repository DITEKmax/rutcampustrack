import { createHmac, randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';

// Synthetic token matches tests/e2e/.env.ci; teacher ID matches the initial Auth baseline callback.
const CI_TMA_BOT_TOKEN = 'ci_test_tma_bot_token_placeholder';

export function teacherInitData(tampered = false): string {
  return syntheticInitData(900000001, { tampered });
}

export function syntheticInitData(
  telegramId: number,
  options: { tampered?: boolean; authDate?: number } = {},
): string {
  const params = new URLSearchParams({
    auth_date: String(options.authDate ?? Math.floor(Date.now() / 1000)),
    query_id: randomUUID(),
    user: JSON.stringify({ id: telegramId, first_name: 'E2E synthetic user' }),
  });
  const check = [...params.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');
  const key = createHmac('sha256', 'WebAppData').update(CI_TMA_BOT_TOKEN).digest();
  const hash = createHmac('sha256', key).update(check).digest('hex');
  params.set('hash', options.tampered ? '0'.repeat(64) : hash);
  return params.toString();
}

/** Synthetic Telegram UI host; auth/session/domain HTTP still reach real services. */
export async function installTelegramHost(
  page: Page,
  initData: string,
  options: { observeDownloads?: boolean } = {},
): Promise<void> {
  await page.route('https://telegram.org/js/telegram-web-app.js', (route) =>
    route.fulfill({ body: '', contentType: 'application/javascript' }),
  );
  await page.addInitScript(
    ({ initData, observeDownloads }) => {
      const state = {
        backVisible: false,
        ready: false,
        callbacks: new Set<() => void>(),
        downloads: [] as { url: string; file_name: string }[],
      };
      Object.assign(window, {
        __tmaEntryState: state,
        Telegram: {
          WebApp: {
            initData,
            viewportHeight: 844,
            viewportStableHeight: 844,
            safeAreaInset: { top: 0, right: 0, bottom: 0, left: 0 },
            ready() {
              state.ready = true;
            },
            expand() {},
            onEvent() {},
            offEvent() {},
            ...(observeDownloads
              ? {
                  downloadFile(
                    params: { url: string; file_name: string },
                    callback: (accepted: boolean) => void,
                  ) {
                    // RAM observation proves only the application handoff. No
                    // Telegram provider or native file save is simulated here.
                    state.downloads.push({ url: params.url, file_name: params.file_name });
                    callback(true);
                  },
                }
              : {}),
            BackButton: {
              show() {
                state.backVisible = true;
              },
              hide() {
                state.backVisible = false;
              },
              onClick(callback: () => void) {
                state.callbacks.add(callback);
              },
              offClick(callback: () => void) {
                state.callbacks.delete(callback);
              },
            },
          },
        },
      });
    },
    { initData, observeDownloads: options.observeDownloads === true },
  );
}
