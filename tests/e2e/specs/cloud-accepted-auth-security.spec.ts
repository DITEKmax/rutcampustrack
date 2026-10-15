import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync } from 'node:fs';
import { isIP } from 'node:net';
import { isAbsolute, join, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import type { TestInfo } from '@playwright/test';
import {
  test as base,
  expect,
  http,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';
import { admit } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';

registerPrivateSuite();

// Playwright awaits this separate, bounded fixture slot even after the 120s body times out.
const test = base.extend<{ otpRecipient: ReturnType<typeof ownedOtpReceiver> }>({
  otpRecipient: [
    async ({}, use, info) => {
      const recipient = ownedOtpReceiver(info);
      try {
        await use(recipient);
      } finally {
        await recipient.close();
      }
    },
    { timeout: 30000 },
  ],
});

const startedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async ({}, info) => {
  startedAt.set(info, performance.now());
});

function requireOwned(value: unknown, message: string): asserts value {
  if (!value) {
    const error = new Error(message);
    Error.captureStackTrace(error, requireOwned);
    throw error;
  }
}

/** A real event recipient in the same disposable network; never a product OTP endpoint. */
function ownedOtpReceiver(info: TestInfo) {
  const run = process.env.RCT_REMAINING5_RUN;
  requireOwned(run && isAbsolute(run), 'OTP receiver requires a private run directory');
  for (const directory of [run, join(run, 'artifacts')]) {
    const stat = lstatSync(directory);
    requireOwned(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'OTP receiver directories must be private',
    );
  }
  requireOwned(
    process.env.CI === 'true' &&
      process.env.RCT_E2E_REMAINING5 === 'true' &&
      process.env.RCT_E2E_ROLE_LOGOUT === 'true' &&
      process.env.RCT_E2E_SYNTHETIC_SEED === 'true' &&
      info.project.outputDir === join(run, 'artifacts') &&
      info.project.use.baseURL === 'https://localhost:8443',
    'OTP receiver requires the exact disposable CI lane',
  );
  const root = resolve(__dirname, '../../..');
  const envFile = join(root, 'tests/e2e/.env.ci');
  const envStat = lstatSync(envFile);
  requireOwned(
    envStat.isFile() &&
      !envStat.isSymbolicLink() &&
      envStat.size <= 65536 &&
      (envStat.mode & 0o022) === 0,
    'Synthetic CI configuration must be bounded',
  );
  const secret: Record<string, string> = {};
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = /^(RABBITMQ_USER|RABBITMQ_PASSWORD)=(.*)$/.exec(line);
    if (!match) continue;
    let value = match[2];
    if (value.length >= 2 && ['"', "'"].includes(value[0]) && value.at(-1) === value[0])
      value = value.slice(1, -1);
    requireOwned(
      value.length > 0 && value.length <= 1024 && secret[match[1]] === undefined,
      'Synthetic broker credential shape is invalid',
    );
    secret[match[1]] = value;
  }
  requireOwned(
    secret.RABBITMQ_USER && secret.RABBITMQ_PASSWORD,
    'Synthetic broker credentials are required',
  );
  const docker = (args: string[], timeout = 4000) => {
    try {
      return execFileSync('docker', args, {
        cwd: root,
        env: { PATH: process.env.PATH ?? '/usr/bin:/bin', LANG: 'C.UTF-8' },
        timeout: Math.max(1, Math.floor(Math.min(4000, timeout))),
        maxBuffer: 65536,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch {
      throw new Error('Owned OTP container lookup failed');
    }
  };
  const ids = docker([
    'compose',
    '--env-file',
    envFile,
    '-p',
    'rct-e2e',
    '-f',
    join(root, 'docker-compose.e2e.yml'),
    'ps',
    '-q',
    'rabbitmq',
  ])
    .trim()
    .split(/\s+/);
  requireOwned(
    ids.length === 1 && /^[0-9a-f]{12,64}$/.test(ids[0]),
    'Exactly one owned OTP broker is required',
  );
  const inspect = (timeout = 4000) => {
    try {
      return JSON.parse(
        docker(
          [
            'inspect',
            '--format',
            '{"id":{{json .Id}},"image":{{json .Image}},"imageName":{{json .Config.Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}',
            ids[0],
          ],
          timeout,
        ),
      ) as Record<string, any>;
    } catch {
      throw new Error('Owned OTP broker inspection failed');
    }
  };
  const pin = inspect();
  requireOwned(
    /^[0-9a-f]{64}$/.test(pin.id) &&
      /^sha256:[0-9a-f]{64}$/.test(pin.image) &&
      pin.imageName === 'rabbitmq:3.13-management-alpine' &&
      pin.labels?.['com.docker.compose.project'] === 'rct-e2e' &&
      pin.labels?.['com.docker.compose.service'] === 'rabbitmq' &&
      pin.running === true &&
      pin.health === 'healthy',
    'Owned OTP broker identity is invalid',
  );
  const network = pin.networks?.rct_e2e_private_net ?? pin.networks?.['rct-e2e_private_net'];
  const ip = network?.IPAddress;
  requireOwned(
    typeof ip === 'string' &&
      isIP(ip) === 4 &&
      /^(?:10\.|172\.(?:1[6-9]|2\d|3[01])\.|192\.168\.)\d/.test(ip),
    'Owned private OTP broker address is required',
  );
  const identity = (timeout = 4000) => {
    const current = inspect(timeout);
    requireOwned(
      current.id === pin.id &&
        current.image === pin.image &&
        current.imageName === pin.imageName &&
        current.running === true &&
        current.health === 'healthy' &&
        JSON.stringify(current.labels) === JSON.stringify(pin.labels) &&
        JSON.stringify(current.mounts) === JSON.stringify(pin.mounts) &&
        JSON.stringify(current.networks) === JSON.stringify(pin.networks),
      'Owned OTP broker identity changed',
    );
  };
  const queue = `rct-auth-reset-${randomUUID()}`;
  const queuePath = `queues/%2F/${encodeURIComponent(queue)}`;
  let attempted = false;
  let closing = false;
  let inFlight: Promise<{ status: number; body: any }> | null = null;
  let totalBytes = 0;
  let cleanupBytes = 0;
  const request = async (
    method: string,
    path: string,
    statuses: number[],
    data?: unknown,
    timeout = 4000,
    cleaning = false,
  ): Promise<{ status: number; body: any }> => {
    const end = performance.now() + Math.max(1, Math.min(4000, timeout));
    identity(end - performance.now());
    const remaining = Math.floor(end - performance.now());
    requireOwned(remaining > 0, 'Owned OTP broker control deadline expired');
    let response: globalThis.Response;
    try {
      response = await fetch(`http://${ip}:15672/api/${path}`, {
        method,
        redirect: 'error',
        signal: AbortSignal.timeout(remaining),
        headers: {
          Authorization: `Basic ${Buffer.from(`${secret.RABBITMQ_USER}:${secret.RABBITMQ_PASSWORD}`).toString('base64')}`,
          'Content-Type': 'application/json',
        },
        ...(data === undefined ? {} : { body: JSON.stringify(data) }),
      });
    } catch {
      throw new Error('Owned OTP broker control unavailable');
    }
    requireOwned(statuses.includes(response.status), 'Owned OTP broker control rejected');
    const chunks: Uint8Array[] = [];
    let size = 0;
    const reader = response.body?.getReader();
    if (reader) {
      try {
        for (;;) {
          const part = await reader.read();
          if (part.done) break;
          size += part.value.length;
          if (cleaning) cleanupBytes += part.value.length;
          else totalBytes += part.value.length;
          requireOwned(
            size <= 16384 && (cleaning ? cleanupBytes <= 32768 : totalBytes <= 262144),
            'Owned OTP broker response exceeds its bound',
          );
          chunks.push(part.value);
        }
      } finally {
        await reader.cancel().catch(() => {});
      }
    }
    try {
      return {
        status: response.status,
        body: size === 0 ? null : JSON.parse(Buffer.concat(chunks).toString('utf8')),
      };
    } catch {
      throw new Error('Owned OTP broker returned an invalid response');
    }
  };
  const call = (...args: Parameters<typeof request>) => {
    requireOwned(args[5] === true || !closing, 'Owned OTP receiver is closing');
    requireOwned(inFlight === null, 'Owned OTP broker calls must remain serial');
    const operation = request(...args);
    inFlight = operation;
    const settled = () => {
      if (inFlight === operation) inFlight = null;
    };
    void operation.then(settled, settled);
    return operation;
  };
  return {
    async bind() {
      await call('GET', queuePath, [404]);
      requireOwned(!closing, 'Owned OTP receiver is closing');
      attempted = true;
      await call('PUT', queuePath, [201], { durable: false, auto_delete: false, arguments: {} });
      await call('POST', `bindings/%2F/e/rut-uit.events/q/${encodeURIComponent(queue)}`, [201], {
        routing_key: '',
        arguments: {},
      });
    },
    async receive(telegramId: number, challengeId: string, ttlSeconds: number) {
      const end = performance.now() + 30000;
      let messages = 0;
      for (let polls = 0; polls < 120 && performance.now() < end; polls += 1) {
        const result = await call(
          'POST',
          `${queuePath}/get`,
          [200],
          { count: 1, ackmode: 'ack_requeue_false', encoding: 'auto', truncate: 8192 },
          end - performance.now(),
        );
        requireOwned(!closing, 'Owned OTP receiver is closing');
        requireOwned(
          Array.isArray(result.body) && result.body.length <= 1,
          'Owned OTP queue response shape is invalid',
        );
        for (const message of result.body) {
          messages += 1;
          requireOwned(
            messages <= 64 &&
              message.payload_encoding === 'string' &&
              typeof message.payload === 'string' &&
              Buffer.byteLength(message.payload) <= 8192,
            'Owned OTP queue delivery exceeds its bound',
          );
          let event: Record<string, any>;
          try {
            event = JSON.parse(message.payload);
          } catch {
            throw new Error('Owned OTP event is malformed');
          }
          requireOwned(
            event !== null && typeof event === 'object' && !Array.isArray(event),
            'Owned OTP event shape is invalid',
          );
          if (
            event.event_type !== 'otp.requested' ||
            event.source !== 'auth-service' ||
            event.payload?.purpose !== 'password_reset' ||
            event.payload?.challenge_id !== challengeId ||
            event.payload?.telegram_id !== telegramId
          )
            continue;
          requireOwned(
            event.payload.ttl_seconds === ttlSeconds &&
              Number.isSafeInteger(event.payload.attempts_remaining) &&
              event.payload.attempts_remaining > 0 &&
              typeof event.payload.code === 'string' &&
              /^[0-9]{6}$/.test(event.payload.code),
            'Owned reset OTP contract is invalid',
          );
          return {
            code: event.payload.code as string,
            attemptsRemaining: event.payload.attempts_remaining as number,
          };
        }
        const remaining = end - performance.now();
        if (remaining > 0) await delay(Math.min(250, remaining));
      }
      throw new Error('Own reset OTP was not delivered within its bound');
    },
    async close() {
      closing = true;
      // The original caller still owns its result; cleanup independently settles its I/O.
      if (inFlight) await Promise.allSettled([inFlight]);
      if (!attempted) return;
      const existing = await call('GET', queuePath, [200, 404], undefined, 4000, true);
      if (existing.status === 404) return;
      requireOwned(existing.body?.name === queue, 'Owned OTP queue name must match');
      requireOwned(existing.body.durable === false, 'Owned OTP queue must be non-durable');
      requireOwned(existing.body.auto_delete === false, 'Owned OTP queue must not auto-delete');
      requireOwned(existing.body.exclusive === false, 'Owned OTP queue must not be exclusive');
      requireOwned(existing.body.consumers === 0, 'Owned OTP queue must have no consumers');
      await call('DELETE', `${queuePath}?if-unused=true`, [204, 404], undefined, 4000, true);
    },
  };
}

// AUTH05 / JS-STUDENT-25 / JS-SYSTEM-04: own real data and durable password change.
test('own profile security changes password and replacement login rejects old authority @cloud-accepted', async ({
  page,
  context,
  dataset,
}, info) => {
  test.setTimeout(120000);
  const cutoff = (startedAt.get(info) ?? performance.now()) + 90000;
  const loginOptions = () => {
    const remaining = Math.floor(cutoff - performance.now());
    expect(remaining, 'Positive login retry budget remains').toBeGreaterThan(0);
    return { retryUntilMs: Date.now() + Math.min(65000, remaining) };
  };
  const user = await dataset.user('STUDENT', dataset.otherGroup.id);
  const peer = await admit(dataset.request, user);
  const original = await (async () => {
    const fresh = await context.storageState({ indexedDB: true });
    expect(
      fresh.cookies.length === 0 && fresh.origins.length === 0,
      'Fresh browser has no owner',
    ).toBe(true);
    const origin = new URL(String(info.project.use.baseURL)).origin;
    const remaining = () => {
      const value = Math.floor(cutoff - performance.now());
      expect(value > 0, 'Initial context recovery retains its original budget').toBe(true);
      return value;
    };
    const contextPath = (url: URL) =>
      url.origin === origin && url.pathname === '/api/auth/session' && url.search === '';
    let release: (delivery: 'fault' | 'abort') => void = () => {};
    const gate = new Promise<'fault' | 'abort'>((resolve) => {
      release = resolve;
    });
    let signalReady: () => void = () => {};
    const ready = new Promise<void>((resolve) => {
      signalReady = resolve;
    });
    let finishHeld: () => void = () => {};
    const finished = new Promise<void>((resolve) => {
      finishHeld = resolve;
    });
    let held = false;
    let ownContextReady = false;
    let heldSessionId: string | undefined;
    let watchdogFired = false;
    let holdTimer: ReturnType<typeof setTimeout> | undefined;
    let routeFailed = false;
    let routeError: unknown;
    let observedLogin: import('@playwright/test').Response | undefined;
    let blockCommands = true;
    let protectedCommands = 0;
    const observeLogin = (response: import('@playwright/test').Response) => {
      const url = new URL(response.url());
      if (
        url.origin === origin &&
        url.pathname === '/api/auth/login' &&
        response.request().method() === 'POST' &&
        response.status() === 200
      )
        observedLogin = response;
    };
    const countCommands = (request: import('@playwright/test').Request) => {
      const url = new URL(request.url());
      if (
        blockCommands &&
        url.origin === origin &&
        url.pathname.startsWith('/api/') &&
        request.method() !== 'GET' &&
        !['/api/auth/login', '/api/auth/refresh'].includes(url.pathname)
      )
        protectedCommands += 1;
    };
    const holdContext = async (route: import('@playwright/test').Route) => {
      if (route.request().method() !== 'GET' || held) {
        await route.continue();
        return;
      }
      held = true;
      try {
        const response = await route.fetch({ timeout: Math.min(10000, remaining()) });
        try {
          const profile = await response.json();
          ownContextReady =
            response.status() === 200 &&
            profile.userId === String(user.id) &&
            profile.activeRole === 'STUDENT' &&
            typeof profile.sessionId === 'string' &&
            profile.sessionId.length > 0;
          heldSessionId = profile.sessionId;
          holdTimer = setTimeout(
            () => {
              watchdogFired = true;
              release('abort');
            },
            Math.min(15000, remaining()),
          );
          signalReady();
          if ((await gate) === 'fault' && !watchdogFired) {
            // One controlled HTTP response fault; the real own 200 above stays in RAM.
            await route.fulfill({
              status: 503,
              contentType: 'application/problem+json',
              body: JSON.stringify({
                type: 'about:blank',
                title: 'Service Unavailable',
                status: 503,
              }),
            });
          } else await route.abort('failed');
        } finally {
          await response.dispose();
        }
      } catch (error) {
        routeFailed = true;
        routeError = error;
        signalReady();
        await route.abort('failed').catch((error) => {
          cleanupErrors.push(error);
        });
      } finally {
        clearTimeout(holdTimer);
        finishHeld();
      }
    };
    let loginFailed = false;
    let loginError: unknown;
    let admission: Promise<void> | undefined;
    let bodyFailed = false;
    const cleanupErrors: unknown[] = [];
    page.on('response', observeLogin);
    page.on('request', countCommands);
    try {
      await page.route(contextPath, holdContext);
      // Settle both outcomes immediately; a held context cannot leave a rejected helper unobserved.
      admission = loginPwa(page, user, loginOptions()).then(
        () => {},
        (error) => {
          loginFailed = true;
          loginError = error;
        },
      );
      await Promise.race([
        ready,
        admission.then(() => {
          if (loginFailed) throw loginError;
          throw new Error('Own context was not held');
        }),
      ]);
      if (routeFailed) throw routeError;
      expect(ownContextReady, 'Real own context was fetched before the fault').toBe(true);
      expect(Boolean(observedLogin), 'Real UI login returned 200').toBe(true);
      const login = await observedLogin!.json();
      expect(typeof login.accessToken === 'string', 'Real login token stays in RAM').toBe(true);
      await page.evaluate(
        () =>
          new Promise<void>((resolve) =>
            requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
          ),
      );
      await expect(
        page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
      ).toBeVisible();
      await expect(page.getByLabel('Логин', { exact: true })).toBeDisabled();
      await expect(page.getByLabel('Пароль', { exact: true })).toBeDisabled();
      await expect(page.locator('.login-card__submit')).toHaveAttribute('aria-busy', 'true');
      await expect(
        page.getByRole('navigation', { name: 'Основная навигация', exact: true }),
      ).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Профиль', exact: true })).toHaveCount(0);
      expect(
        await page.evaluate(
          () => localStorage.getItem('rct-student-mobile.current-owner') === null,
        ),
        'No snapshot owner before context',
      ).toBe(true);
      expect(protectedCommands === 0, 'No protected command before context').toBe(true);
      expect(watchdogFired, 'Context hold was not released by its watchdog').toBe(false);
      release('fault');
      await finished;
      await admission;
      if (routeFailed) throw routeError;
      if (!loginFailed) throw new Error('Context fault did not reject login');
      if (
        !(loginError instanceof Error) ||
        loginError.message !== 'Synthetic actor session: HTTP 503'
      )
        throw loginError;
      expect(watchdogFired, 'Context fault was explicitly released').toBe(false);
      await expect(page.getByRole('alert')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Войти', exact: true })).toBeEnabled();
      await expect(
        page.getByRole('navigation', { name: 'Основная навигация', exact: true }),
      ).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Профиль', exact: true })).toHaveCount(0);
      expect(
        await page.evaluate(
          () => localStorage.getItem('rct-student-mobile.current-owner') === null,
        ),
        'Context failure cannot establish a snapshot owner',
      ).toBe(true);
      expect(protectedCommands === 0, 'Context failure cannot enable protected commands').toBe(
        true,
      );
      await page.unroute(contextPath, holdContext);
      blockCommands = false;
      const responseOptions = { timeout: Math.min(10000, remaining()) };
      const refreshed = page.waitForResponse(
        (response) =>
          new URL(response.url()).origin === origin &&
          new URL(response.url()).pathname === '/api/auth/refresh' &&
          response.request().method() === 'POST',
        responseOptions,
      );
      const recoveredContext = page.waitForResponse(
        (response) => contextPath(new URL(response.url())) && response.request().method() === 'GET',
        responseOptions,
      );
      const recoveredStudent = page.waitForResponse(
        (response) =>
          new URL(response.url()).origin === origin &&
          new URL(response.url()).pathname === '/api/v1/student/session' &&
          response.request().method() === 'GET',
        responseOptions,
      );
      const recoveredSchedule = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return (
          url.origin === origin &&
          url.pathname === '/api/v1/student/schedule' &&
          url.searchParams.get('semesterId') === String(dataset.semester.id) &&
          response.request().method() === 'GET'
        );
      }, responseOptions);
      // A user reload retries context with the actual cookie; it creates no third LOGIN/session.
      const recovery = await Promise.all([
        page.reload({ timeout: Math.min(10000, remaining()) }),
        refreshed,
        recoveredContext,
        recoveredStudent,
        recoveredSchedule,
      ]);
      expect(
        recovery.slice(1).every((response) => response?.status() === 200),
        'Real refreshed context and own schedule recover',
      ).toBe(true);
      const session: Awaited<ReturnType<typeof loginPwa>>['session'] = await recovery[2]!.json();
      const student = await recovery[3]!.json();
      const schedule = await recovery[4]!.json();
      expect(
        session.userId === String(user.id) &&
          session.sessionId === heldSessionId &&
          session.activeRole === 'STUDENT',
        'Recovery retains the exact own session',
      ).toBe(true);
      expect(
        String(student.user.id) === String(user.id) &&
          String(student.group?.id) === String(dataset.otherGroup.id) &&
          String(student.semester?.id) === String(dataset.semester.id) &&
          String(schedule.semester.id) === String(dataset.semester.id),
        'Real BFF recovery retains the own group and semester',
      ).toBe(true);
      await expect(
        page.getByRole('navigation', { name: 'Основная навигация', exact: true }),
      ).toBeVisible();
      await expect(page.getByRole('button', { name: 'Профиль', exact: true })).toBeVisible();
      return { accessToken: login.accessToken as string, session };
    } catch (error) {
      bodyFailed = true;
      throw error;
    } finally {
      release('abort');
      clearTimeout(holdTimer);
      page.off('response', observeLogin);
      page.off('request', countCommands);
      try {
        await page.unroute(contextPath, holdContext);
      } catch (error) {
        cleanupErrors.push(error);
      }
      if (held) {
        try {
          await finished;
        } catch (error) {
          cleanupErrors.push(error);
        }
      }
      if (admission) {
        try {
          await admission;
        } catch (error) {
          cleanupErrors.push(error);
        }
      }
      if (cleanupErrors.length) {
        if (bodyFailed) expect.soft(false, 'CONTEXT_FAULT_CLEANUP_FAILED').toBe(true);
        else throw cleanupErrors[0];
      }
    }
  })();
  expect(original.session.userId === String(user.id), 'Own student session admitted').toBe(true);

  const profileRead = http(page, '/api/auth/session', 'GET');
  await page.getByRole('button', { name: 'Профиль', exact: true }).click();
  const profileResponse = await profileRead;
  expect(profileResponse.status(), 'Own profile read succeeds').toBe(200);
  const profile = await profileResponse.json();
  expect(profile.userId === String(user.id), 'Profile response belongs to the owner').toBe(true);
  const card = page.getByRole('region', { name: 'Данные профиля' });
  await expect(card).toBeVisible();
  expect(
    await card.evaluate(
      (element, expected) => element.textContent?.includes(expected) === true,
      user.displayName,
    ),
    'Own name is displayed',
  ).toBe(true);
  expect(
    await card.evaluate(
      (element, expected) => element.textContent?.includes(expected) === true,
      dataset.otherGroup.name,
    ),
    'Own group is displayed',
  ).toBe(true);
  await expect(card.locator('input, textarea, select')).toHaveCount(0);
  // Keep this accepted field observable even while the remaining journey continues.
  expect
    .soft(
      await card.evaluate(
        (element, expected) => element.textContent?.includes(expected) === true,
        String(user.telegramId),
      ),
      'PROFILE_OWN_TELEGRAM_ID_VISIBLE',
    )
    .toBe(true);

  const sessionRead = http(page, '/api/auth/sessions', 'GET');
  await page.getByRole('button', { name: 'Активные сеансы', exact: true }).click();
  const sessionsResponse = await sessionRead;
  expect(sessionsResponse.status(), 'Own sessions read succeeds').toBe(200);
  const sessions = await sessionsResponse.json();
  expect(
    Array.isArray(sessions.items) &&
      sessions.items.length === 2 &&
      sessions.items.some(
        (item: { sessionId: string; current: boolean }) =>
          item.sessionId === original.session.sessionId && item.current,
      ) &&
      sessions.items.some(
        (item: { sessionId: string }) => item.sessionId === peer.session.sessionId,
      ),
    'Only both known own sessions are returned',
  ).toBe(true);
  await expect(
    page.getByRole('list', { name: 'Список активных сеансов' }).locator('li'),
  ).toHaveCount(2);
  await expect(page.getByText('Это устройство', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Назад', exact: true }).click();

  const historyRead = http(page, '/api/auth/account-history', 'GET');
  await page.getByRole('button', { name: 'История аккаунта', exact: true }).click();
  const historyResponse = await historyRead;
  expect(historyResponse.status(), 'Own history read succeeds').toBe(200);
  const history = await historyResponse.json();
  expect(
    Array.isArray(history.items) &&
      history.items.length === 2 &&
      history.items.every((item: { type: string }) => item.type === 'LOGIN'),
    'Fresh own history contains its two admissions',
  ).toBe(true);
  await expect(page.getByRole('list', { name: 'История аккаунта' }).locator('li')).toHaveCount(2);
  await expect(page.getByText('Вход в аккаунт', { exact: true })).toHaveCount(2);
  await page.getByRole('button', { name: 'Назад', exact: true }).click();

  try {
    await page.getByRole('button', { name: 'Оформление', exact: true }).click();
    const appearance = page.locator('.profile-appearance');
    await page.getByRole('radio', { name: 'Светлая', exact: true }).check();
    await expect(appearance).toHaveAttribute('data-theme', 'light');
    await expect(appearance).toHaveCSS('background-color', 'rgb(252, 251, 254)');
    await expect(appearance).toContainText('Остальные экраны пока тёмные.');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.reload();
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    await expect(page.locator('.profile-page')).toHaveAttribute('data-theme', 'dark');
    await page.getByRole('button', { name: 'Оформление', exact: true }).click();
    await expect(page.getByRole('radio', { name: 'Светлая', exact: true })).toBeChecked();
    await expect(appearance).toHaveAttribute('data-theme', 'light');
    await expect(appearance).toHaveCSS('background-color', 'rgb(252, 251, 254)');
    await page.getByRole('radio', { name: 'Тёмная', exact: true }).check();
    await expect(appearance).toHaveAttribute('data-theme', 'dark');
    await page.getByRole('radio', { name: 'Как в системе', exact: true }).check();
    await expect(page.getByRole('radio', { name: 'Как в системе', exact: true })).toBeChecked();
    await page.emulateMedia({ colorScheme: 'light' });
    await expect(appearance).toHaveAttribute('data-theme', 'light');
    await expect(appearance).toHaveCSS('background-color', 'rgb(252, 251, 254)');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.emulateMedia({ colorScheme: 'dark' });
    await expect(appearance).toHaveAttribute('data-theme', 'dark');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(page.locator('.profile-page')).toHaveAttribute('data-theme', 'dark');
  } finally {
    const restored = await page.emulateMedia({ colorScheme: null }).then(
      () => true,
      () => false,
    );
    expect.soft(restored, 'Appearance media override restored').toBe(true);
  }

  await page.getByRole('button', { name: 'Безопасность', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Не помню текущий пароль', exact: true }),
  ).toBeVisible();
  const replacementPassword = `Changed-${randomUUID()}!1`;
  await page.getByLabel('Текущий пароль *', { exact: true }).fill(user.password);
  await page.getByLabel('Новый пароль *', { exact: true }).fill(replacementPassword);
  await page.getByLabel('Повтори новый пароль *', { exact: true }).fill(replacementPassword);
  const changed = http(page, '/api/auth/change-password', 'POST');
  await page.getByRole('button', { name: 'Сменить пароль', exact: true }).click();
  expect((await changed).status(), 'Real password change commits').toBe(204);
  await expect(
    page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
  expect(
    (await context.cookies()).some((cookie) => cookie.name === 'rct_refresh'),
    'Changed password clears the refresh cookie',
  ).toBe(false);
  for (const token of [original.accessToken, peer.token]) {
    const denied = await dataset.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(denied.status(), 'Every old own bearer is rejected').toBe(401);
  }
  const oldLogin = await dataset.request.post('/api/auth/login', {
    data: { login: user.login, password: user.password },
  });
  expect(oldLogin.status(), 'Previous password no longer admits').toBe(401);

  const replacement = await loginPwa(
    page,
    { ...user, password: replacementPassword },
    loginOptions(),
  );
  expect(
    replacement.session.userId === String(user.id) &&
      replacement.session.sessionId !== original.session.sessionId &&
      replacement.session.sessionId !== peer.session.sessionId,
    'Replacement login owns a fresh session for the same user',
  ).toBe(true);
  const changedHistory = http(page, '/api/auth/account-history', 'GET');
  await page.getByRole('button', { name: 'Профиль', exact: true }).click();
  await page.getByRole('button', { name: 'История аккаунта', exact: true }).click();
  const changedHistoryResponse = await changedHistory;
  expect(changedHistoryResponse.status()).toBe(200);
  const changedEvents = await changedHistoryResponse.json();
  expect(
    Array.isArray(changedEvents.items) &&
      changedEvents.items.filter((item: { type: string }) => item.type === 'PASSWORD_CHANGED')
        .length === 1,
    'Own history records exactly one password change',
  ).toBe(true);
  await expect(page.getByText('Смена пароля', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Назад', exact: true }).click();
  await logoutPwa(page);
});

test('password recovery consumes its own delivered OTP and admits the replacement password @cloud-accepted', async ({
  page,
  dataset,
  otpRecipient: recipient,
}, info) => {
  test.setTimeout(120000);
  const cutoff = (startedAt.get(info) ?? performance.now()) + 90000;
  const user = await dataset.user('STUDENT', dataset.otherGroup.id);
  await recipient.bind();
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Забыл пароль', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Восстановить пароль', exact: true }),
  ).toBeVisible();
  await page.getByLabel('Логин', { exact: true }).fill(user.login);
  const requested = http(page, '/api/auth/password-reset/request', 'POST');
  await page.getByRole('button', { name: 'Запросить код', exact: true }).click();
  const requestedResponse = await requested;
  expect(requestedResponse.status(), 'Real reset request is accepted').toBe(202);
  const challenge = await requestedResponse.json();
  expect(
    typeof challenge.challengeId === 'string' &&
      challenge.challengeId.length > 0 &&
      Number.isSafeInteger(challenge.ttlSeconds) &&
      challenge.ttlSeconds > 0 &&
      !Object.hasOwn(challenge, 'code'),
    'Reset challenge exposes server TTL without its code',
  ).toBe(true);
  await expect(page.getByRole('heading', { name: 'Введи код', exact: true })).toBeVisible();
  expect(
    await page.locator('.login-card__hint').evaluateAll(
      (elements, ttl: number) =>
        elements.some((element) => {
          const match = /^Код из Telegram действует (?:(\d+) мин(?: (\d+) сек)?|(\d+) сек)\.$/.exec(
            element.textContent?.trim() ?? '',
          );
          return (
            match !== null && Number(match[1] ?? 0) * 60 + Number(match[2] ?? match[3] ?? 0) === ttl
          );
        }),
      challenge.ttlSeconds,
    ),
    'UI displays the real challenge TTL',
  ).toBe(true);
  const delivered = await recipient.receive(
    user.telegramId,
    challenge.challengeId,
    challenge.ttlSeconds,
  );
  const wrongCode = String((Number(delivered.code[0]) + 1) % 10) + delivered.code.slice(1);
  await page.getByLabel('Код из Telegram', { exact: true }).fill(wrongCode);
  const wrong = http(page, '/api/auth/password-reset/verify', 'POST');
  await page.getByRole('button', { name: 'Проверить код', exact: true }).click();
  const wrongResponse = await wrong;
  expect(wrongResponse.status(), 'Wrong own challenge code is rejected').toBe(400);
  const wrongProblem = await wrongResponse.json();
  const remainingAttempts =
    wrongProblem.extras?.attemptsRemaining ?? wrongProblem.attemptsRemaining;
  expect(
    remainingAttempts === delivered.attemptsRemaining - 1 && remainingAttempts > 0,
    'Server decreases the own challenge attempt count',
  ).toBe(true);
  await expect
    .poll(
      () =>
        page
          .getByRole('status')
          .evaluateAll(
            (elements, remaining: number) =>
              elements.some(
                (element) => element.textContent?.trim() === `Осталось попыток: ${remaining}.`,
              ),
            remainingAttempts,
          ),
      { message: 'UI displays server-provided remaining attempts' },
    )
    .toBe(true);
  await page.getByLabel('Код из Telegram', { exact: true }).fill(delivered.code);
  const verified = http(page, '/api/auth/password-reset/verify', 'POST');
  await page.getByRole('button', { name: 'Проверить код', exact: true }).click();
  const verifiedResponse = await verified;
  expect(verifiedResponse.status(), 'Real delivered reset code verifies').toBe(200);
  const proof = await verifiedResponse.json();
  expect(
    typeof proof.resetTicket === 'string' &&
      proof.resetTicket.length > 0 &&
      Number.isSafeInteger(proof.expiresInSeconds) &&
      proof.expiresInSeconds > 0 &&
      proof.attemptsRemaining === remainingAttempts,
    'Verified reset retains the server ticket and attempt contract',
  ).toBe(true);
  await expect(
    page.getByRole('heading', { name: 'Задай новый пароль', exact: true }),
  ).toBeVisible();
  const replacementPassword = `Reset-${randomUUID()}!1`;
  await page.getByLabel('Новый пароль', { exact: true }).fill(replacementPassword);
  await page.getByLabel('Повтори новый пароль', { exact: true }).fill(replacementPassword);
  const completed = http(page, '/api/auth/password-reset/complete', 'POST');
  await page.getByRole('button', { name: 'Сохранить пароль', exact: true }).click();
  expect((await completed).status(), 'Real reset commits').toBe(204);
  await expect(
    page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
  ).toBeVisible();
  await expect(page.getByRole('status')).toHaveText('Пароль обновлён. Войди с новым паролем.');
  const remaining = Math.floor(cutoff - performance.now());
  expect(remaining, 'Positive login retry budget remains').toBeGreaterThan(0);
  const replacement = await loginPwa(
    page,
    { ...user, password: replacementPassword },
    { retryUntilMs: Date.now() + Math.min(65000, remaining) },
  );
  expect(
    replacement.session.userId === String(user.id) && replacement.session.activeRole === 'STUDENT',
    'Replacement password admits the exact reset owner',
  ).toBe(true);
  await logoutPwa(page);
});
