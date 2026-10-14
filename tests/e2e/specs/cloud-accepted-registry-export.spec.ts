import { randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import {
  test,
  expect,
  admit,
  http,
  selectRole,
  registerPrivateSuite,
  registryHtmlBoundary,
} from '../fixtures/cloud-accepted-support';
import type { MobileDataset } from '../fixtures/mobile-dataset';
import { loginPwa } from '../fixtures/pwa-auth';
import { datasetHttpFailure } from '../fixtures/safe-reporter';

registerPrivateSuite();

// CLIENT-MAP-FILES-EXPORTS + CLIENT-ADMIN / D006,D007 / JS-ADMIN-13 / BOUNDARY-46.
// This finite HTML carrier verifies the actual choose → durable ready → ticket →
// browser download sequence. Other four formats/native Telegram remain separate.
for (const mode of ['FULL', 'LIMITED'] as const) {
  test(`${mode} ordinary user export downloads filtered HTML with exact safe fields @cloud-accepted`, async ({
    page,
    dataset,
  }, info) => {
    // TestInfo.duration is zero while running. Reserve 30s of the original
    // case budget and charge all body setup against this one absolute limit.
    const retryUntilMs = Date.now() + Math.max(0, Math.min(info.timeout, 120000) - 30000);
    const user = await dataset.user('ADMIN', undefined, mode === 'LIMITED');
    const target = await dataset.user('STUDENT', dataset.group.id);
    const nonmatching = await dataset.user('STUDENT', dataset.otherGroup.id);
    expect(nonmatching.login !== target.login).toBe(true);
    const staffNumber = `E2E-${target.id}`;
    await dataset.admin.call('PUT', `/api/academic/users/${target.id}/roles/TEACHER`, {
      status: 'ACTIVE',
      employeeNumber: staffNumber,
    });
    const profile = await dataset.admin.call('GET', `/api/academic/users/${target.id}`);
    expect(profile.fullName === target.displayName).toBe(true);
    expect(profile.employeeNumber === staffNumber).toBe(true);
    expect(
      profile.roles
        .map((grant: { role: string; status: string }) => `${grant.role}: ${grant.status}`)
        .join('; ') === 'STUDENT: ACTIVE; TEACHER: ACTIVE',
    ).toBe(true);
    expect(typeof dataset.group.name === 'string' && dataset.group.name.length > 0).toBe(true);
    await loginPwa(page, user, { retryUntilMs });
    const selected = await selectRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await page
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(target.login);
    await page.getByRole('button', { name: 'Найти', exact: true }).click();
    await expect(page.locator(`[data-user-id="${target.id}"]`)).toBeVisible();
    await expect(page.locator('.admin-users-card')).toHaveCount(1);
    const catalogue = http(page, '/api/academic/registry-exports/users/formats', 'GET');
    await page.getByRole('button', { name: 'Выгрузка', exact: true }).click();
    const formats = await catalogue;
    expect(formats.status()).toBe(200);
    expect((await formats.json()).map((value: { code: string }) => value.code).sort()).toEqual([
      'docx',
      'html',
      'pdf',
      'png',
      'xlsx',
    ]);
    await page.getByLabel(/^Формат выгрузки/).selectOption('html');
    const created = http(page, '/api/auth/export-jobs', 'POST');
    const capability = page.waitForResponse(
      (response) =>
        /^\/api\/auth\/export-jobs\/[0-9a-f-]+\/ticket$/.test(new URL(response.url()).pathname) &&
        response.request().method() === 'POST',
    );
    const bytes = page.waitForResponse((response) =>
      /^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(new URL(response.url()).pathname),
    );
    const saved = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Скачать', exact: true }).click();
    const job = await created;
    expect(job.status()).toBe(202);
    const jobId: string = (await job.json()).jobId;
    const issued = await capability;
    expect(issued.status()).toBe(200);
    const ticket: { downloadPath: string; suggestedFilename: string } = await issued.json();
    const file = await bytes;
    if (file.status() !== 200)
      throw datasetHttpFailure('GET', new URL(file.url()).pathname, file.status());
    expect(file.status()).toBe(200);
    expect(file.headers()['content-type'].split(';')[0]).toBe('text/html');
    const content = (await file.body()).toString('utf8');
    expect(content.startsWith('<!doctype html>')).toBe(true);
    const boundary = registryHtmlBoundary(
      content,
      mode === 'FULL',
      [
        target.displayName,
        target.login,
        'STUDENT: ACTIVE; TEACHER: ACTIVE',
        dataset.group.name,
        staffNumber,
        'Привязан',
        ...(mode === 'FULL' ? [String(target.telegramId)] : []),
      ],
      nonmatching,
    );
    expect(boundary.tableShape).toBe(true);
    expect(boundary.columnsExact).toBe(true);
    expect(boundary.oneRow).toBe(true);
    expect(boundary.valuesExact).toBe(true);
    expect(boundary.knownNonmatchAbsent).toBe(true);
    expect(content.includes('Telegram ID')).toBe(mode === 'FULL');
    expect(content.includes(String(target.telegramId))).toBe(mode === 'FULL');
    expect(content.includes(target.password)).toBe(false);
    expect(/парол|password|password-reset/i.test(content)).toBe(false);
    const download = await saved;
    expect(download.suggestedFilename() === ticket.suggestedFilename).toBe(true);
    expect(download.suggestedFilename().endsWith('.html')).toBe(true);
    expect(await download.failure()).toBeNull();
    await expect(
      page.getByRole('status').filter({ hasText: 'Файл готов. Скачивание передано браузеру.' }),
    ).toBeVisible();
    const headers = { Authorization: `Bearer ${selected.accessToken}` };
    expect(
      (await dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers })).status(),
    ).toBe(200);
    const foreignUser = await dataset.user('ADMIN');
    const foreign = await admit(dataset.request, foreignUser, 'ADMIN');
    expect(
      (
        await dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers: foreign.headers() })
      ).status(),
    ).toBe(404);
    expect(
      (
        await dataset.request.get(`/api/auth/export-jobs/${jobId}/file`, {
          headers: foreign.headers(),
        })
      ).status(),
    ).toBe(404);
    if (mode === 'LIMITED') {
      const path = `/api/academic/users/${user.id}/admin-access`;
      const profile = await dataset.admin.call('GET', path);
      // A fresh, working capability distinguishes live revocation from ticket expiry.
      const freshTicketResponse = await dataset.request.post(
        `/api/auth/export-jobs/${jobId}/ticket`,
        { headers },
      );
      expect(freshTicketResponse.status()).toBe(200);
      const freshTicket: { downloadPath: string } = await freshTicketResponse.json();
      expect((await dataset.request.get(freshTicket.downloadPath)).status()).toBe(200);
      await dataset.admin.call('PUT', path, {
        mode: 'LIMITED',
        permissions: ['GROUPS_VIEW'],
        expectedVersion: profile.version,
      });
      // The profile write bumps rolesVersion. Old credentials must fail as stale,
      // before the request can reach the current registry permission check.
      for (const operation of [
        () => dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers }),
        () => dataset.request.post(`/api/auth/export-jobs/${jobId}/ticket`, { headers }),
        () => dataset.request.get(`/api/auth/export-jobs/${jobId}/file`, { headers }),
      ]) {
        const denied = await operation();
        expect(denied.status()).toBe(409);
        expect((await denied.json()).code === 'SESSION_STATE_STALE').toBe(true);
      }
      expect((await dataset.request.get(freshTicket.downloadPath)).status()).toBe(409);

      // Refresh the browser's owning session, whose cookie is independent of the
      // synthetic actors above; a new login would change ownership and yield 404.
      const refreshed = await page.request.post('/api/auth/refresh');
      expect(refreshed.status()).toBe(200);
      const currentHeaders = {
        Authorization: `Bearer ${(await refreshed.json()).accessToken}`,
      };
      const current = await page.request.get('/api/auth/session', { headers: currentHeaders });
      expect(current.status()).toBe(200);
      const currentSession = await current.json();
      expect(currentSession.sessionId === selected.session.sessionId).toBe(true);
      expect(currentSession.activeRole).toBe('ADMIN');
      for (const operation of [
        () => dataset.request.get(`/api/auth/export-jobs/${jobId}`, { headers: currentHeaders }),
        () =>
          dataset.request.post(`/api/auth/export-jobs/${jobId}/ticket`, {
            headers: currentHeaders,
          }),
        () =>
          dataset.request.get(`/api/auth/export-jobs/${jobId}/file`, {
            headers: currentHeaders,
          }),
      ])
        expect((await operation()).status()).toBe(403);
      // A new request also reaches live USERS_VIEW admission, independently of
      // the old job's stored principal/version and idempotent replay identity.
      expect(
        (
          await dataset.request.post('/api/auth/export-jobs', {
            headers: currentHeaders,
            data: { requestId: randomUUID(), report: job.request().postDataJSON().report },
          })
        ).status(),
      ).toBe(403);
      expect((await dataset.request.get(freshTicket.downloadPath)).status()).toBe(409);
      const unchanged = await dataset.admin.call('GET', `/api/academic/users/${target.id}`);
      expect(unchanged.initialPassword === target.password).toBe(true);
    }
    // Download bodies/tickets/identities are never attached or persisted by this test.
    await download.delete();
  });
}

// Q11 / JS-ADMIN-13 / D006,D007. The HTML browser/grant tests above remain the
// client carrier. These selected binary cases add the actual native producer
// and its own committed audit, without replaying the format-independent denials.
const registryByteLimit = 20 * 1024 * 1024;

function registryRequire(value: unknown, code: string): asserts value {
  if (!value) throw new Error(`Registry PDF check: ${code}`);
}

// All callers below use fixed Docker/Poppler/psql arguments, never a shell.
// Capture errors as closed codes: a native exception can include its input,
// command, stderr or extracted names. No native output is attached or logged.
function registryDocker(until: number, args: string[], input?: Buffer, maxBuffer = 16384): Buffer {
  try {
    const remaining = until - Date.now();
    registryRequire(remaining > 0, 'NATIVE_DEADLINE');
    const result = spawnSync('docker', args, {
      input,
      timeout: Math.min(10000, remaining),
      killSignal: 'SIGKILL',
      maxBuffer,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    registryRequire(!result.error && result.status === 0 && !result.signal, 'NATIVE_EXIT');
    return result.stdout;
  } catch {
    throw new Error('Registry PDF check: NATIVE_EXIT');
  }
}

function registryContainer(
  until: number,
  service: 'document-renderer-service' | 'postgres-academic',
): string {
  const selected = registryDocker(until, [
    'ps',
    '--filter',
    'label=com.docker.compose.project=rct-e2e',
    '--filter',
    `label=com.docker.compose.service=${service}`,
    '--format',
    '{{.ID}}',
  ])
    .toString('utf8')
    .trim();
  registryRequire(/^[0-9a-f]{12,64}$/.test(selected), 'OWN_CONTAINER_COUNT');
  const identity = registryDocker(until, [
    'inspect',
    '--type',
    'container',
    '--format',
    '{{.Id}} {{index .Config.Labels "com.docker.compose.project"}} {{index .Config.Labels "com.docker.compose.service"}} {{.State.Running}}',
    selected,
  ])
    .toString('utf8')
    .trim()
    .split(' ');
  registryRequire(
    identity.length === 4 &&
      /^[0-9a-f]{64}$/.test(identity[0]!) &&
      identity[0]!.startsWith(selected) &&
      identity[1] === 'rct-e2e' &&
      identity[2] === service &&
      identity[3] === 'true',
    'OWN_CONTAINER_IDENTITY',
  );
  return identity[0]!;
}

function registryPoppler(until: number, content: Buffer, tool: 'pdfinfo' | 'pdftotext'): string {
  registryRequire(content.length > 0 && content.length <= registryByteLimit, 'PDF_SIZE');
  const container = registryContainer(until, 'document-renderer-service');
  return registryDocker(
    until,
    [
      'exec',
      '-i',
      container,
      '/usr/bin/timeout',
      '-s',
      'KILL',
      '8',
      `/usr/bin/${tool}`,
      ...(tool === 'pdftotext' ? ['-bbox-layout', '-', '-'] : ['-']),
    ],
    content,
    2 * 1024 * 1024,
  ).toString('utf8');
}

interface RegistryPdfWord {
  text: string;
  x: number;
  y: number;
}

function registryPdfDecode(value: string): string {
  return value.replace(/&(amp|lt|gt|quot|apos);/g, (entity) => {
    const entities: Record<string, string> = {
      '&amp;': '&',
      '&lt;': '<',
      '&gt;': '>',
      '&quot;': '"',
      '&apos;': "'",
    };
    return entities[entity]!;
  });
}

function registryPdfCells(document: string, expectedRows: string[][], full: boolean): boolean {
  const compact = (value: string) => value.replace(/\s+/g, '');
  const headers = [
    'ФИО',
    'Логин',
    'Роли и статусы',
    'Группа',
    'Табельный номер',
    'Привязка Telegram',
    ...(full ? ['Telegram ID'] : []),
  ];
  const pages = [...document.matchAll(/<page\b[^>]*>([\s\S]*?)<\/page>/g)];
  registryRequire(pages.length > 0 && pages.length <= 4, 'PDF_PAGE_BOUND');
  const body: (RegistryPdfWord & { column: number })[] = [];
  for (const [pageIndex, page] of pages.entries()) {
    const words: RegistryPdfWord[] = [
      ...page[1]!.matchAll(
        /<word xMin="([0-9.]+)" yMin="([0-9.]+)" xMax="[0-9.]+" yMax="[0-9.]+">([^<]*)<\/word>/g,
      ),
    ].map((word) => ({
      x: Number(word[1]),
      y: Number(word[2]),
      text: registryPdfDecode(word[3]!),
    }));
    registryRequire(
      words.length > 0 &&
        words.length <= 4000 &&
        words.every((word) => Number.isFinite(word.x) && Number.isFinite(word.y)),
      'PDF_WORD_BOUND',
    );
    const first = words.find((word) => word.text === 'ФИО');
    registryRequire(first, 'PDF_TABLE_HEADER');
    const anchors = headers.map(
      (header, column) =>
        words
          .filter(
            (word) =>
              word.text === header.split(' ')[0] &&
              Math.abs(word.y - first.y) < 1 &&
              (column !== 6 ||
                word.x > (words.find((item) => item.text === 'Привязка')?.x ?? Infinity)),
          )
          .sort((left, right) => (column === 6 ? right.x - left.x : left.x - right.x))[0],
    );
    registryRequire(
      anchors.every((word) => word !== undefined) &&
        anchors.every((word, index) => index === 0 || word!.x > anchors[index - 1]!.x),
      'PDF_COLUMN_ORDER',
    );
    registryRequire(
      words.every((word) => word.y < first.y - 1 || word.x >= first.x - 1),
      'PDF_EXTRA_COLUMN',
    );
    for (const [column, header] of headers.entries()) {
      const cell = words
        .filter(
          (word) =>
            word.y >= first.y - 1 &&
            word.x >= anchors[column]!.x - 1 &&
            (column === headers.length - 1 || word.x < anchors[column + 1]!.x - 1),
        )
        .sort((left, right) => left.y - right.y || left.x - right.x);
      const count = header.split(' ').length;
      registryRequire(
        compact(
          cell
            .slice(0, count)
            .map((word) => word.text)
            .join(' '),
        ) === compact(header),
        'PDF_COLUMNS_EXACT',
      );
      body.push(
        ...cell.slice(count).map((word) => ({ ...word, column, y: word.y + pageIndex * 10000 })),
      );
    }
  }
  // The two short unique surnames fit a cell. Login/role lines may wrap; join
  // those line fragments within their actual cell, never across columns/rows.
  const starts = expectedRows.map((row) => {
    const surname = row[0]!.split(' ')[0];
    const matches = body.filter((word) => word.column === 0 && word.text === surname);
    registryRequire(matches.length === 1, 'PDF_ROW_IDENTITY');
    return matches[0]!.y;
  });
  registryRequire(starts[0]! < starts[1]!, 'PDF_ROW_ORDER');
  registryRequire(
    body.every((word) => word.y >= starts[0]! - 1),
    'PDF_EXTRA_ROW',
  );
  return expectedRows.every((row, index) =>
    row.every((value, column) => {
      const actual = body
        .filter(
          (word) =>
            word.column === column &&
            word.y >= starts[index]! - 1 &&
            (index === expectedRows.length - 1 || word.y < starts[index + 1]! - 1),
        )
        .sort((left, right) => left.y - right.y || left.x - right.x)
        .map((word) => word.text)
        .join(' ');
      return compact(actual) === compact(value);
    }),
  );
}

function registryAudit(until: number, jobId: string, actorId: number, startedAt: number): void {
  registryRequire(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(jobId), 'AUDIT_JOB');
  registryRequire(Number.isSafeInteger(actorId) && actorId > 0, 'AUDIT_ACTOR');
  registryRequire(Number.isSafeInteger(startedAt) && startedAt > 0, 'AUDIT_TIME');
  const container = registryContainer(until, 'postgres-academic');
  // Only this own validated UUID is selected; no arbitrary table, identifier,
  // credential read or write. psql uses the existing local synthetic socket.
  const sql = `BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '3s';
SET LOCAL lock_timeout = '1s';
SELECT json_build_object(
  'one', count(*) = 1,
  'actor', coalesce(bool_and(user_id = ${actorId}), false),
  'kind', coalesce(bool_and(target_type = 'ADMIN_USERS' AND extras->>'kind' = 'ADMIN_USERS'), false),
  'rows', coalesce(bool_and(extras->>'rowCount' = '2'), false),
  'job', coalesce(bool_and(extras->>'jobId' = '${jobId}'), false),
  'time', coalesce(bool_and(created_at >= to_timestamp(${startedAt} / 1000.0) AND created_at <= now()), false),
  'succeeded', coalesce(bool_and(succeeded), false))
FROM audit_log WHERE action = 'registry.export' AND extras->>'jobId' = '${jobId}';
ROLLBACK;`;
  const output = registryDocker(
    until,
    [
      'exec',
      '-i',
      container,
      '/usr/bin/timeout',
      '-s',
      'KILL',
      '8',
      'psql',
      '-X',
      '--no-password',
      '-U',
      'rct_user',
      '-d',
      'academic_db',
      '-v',
      'ON_ERROR_STOP=1',
      '-qAt',
    ],
    Buffer.from(sql),
  );
  let fact: Record<string, unknown>;
  try {
    fact = JSON.parse(output.toString('utf8'));
  } catch {
    throw new Error('Registry PDF check: AUDIT_JSON');
  }
  registryRequire(
    fact !== null &&
      Object.keys(fact).sort().join(',') === 'actor,job,kind,one,rows,succeeded,time' &&
      Object.values(fact).every((value) => value === true),
    'AUDIT_FACT',
  );
}

async function registryPdfUser(dataset: MobileDataset, prefix: string, ending: string) {
  const lastName = `${prefix}${ending}`;
  const telegramId = 8_000_000_000 + Number.parseInt(randomUUID().slice(0, 8), 16);
  const profile = await dataset.admin.call(
    'POST',
    '/api/academic/users',
    {
      lastName,
      firstName: 'Тест',
      middleName: null,
      role: 'STUDENT',
      groupId: dataset.group.id,
      employeeNumber: null,
      telegramId,
      adminAccess: null,
    },
    [201],
  );
  registryRequire(
    Number.isSafeInteger(profile.id) &&
      profile.id > 0 &&
      typeof profile.login === 'string' &&
      typeof profile.initialPassword === 'string',
    'OWN_CREATED_USER',
  );
  const staffNumber = `E2E-${profile.id}`;
  await dataset.admin.call('PUT', `/api/academic/users/${profile.id}/roles/TEACHER`, {
    status: 'ACTIVE',
    employeeNumber: staffNumber,
  });
  registryRequire(profile.lastName === lastName && profile.firstName === 'Тест', 'OWN_USER_NAME');
  return {
    displayName: `${lastName} Тест`,
    login: profile.login as string,
    password: profile.initialPassword as string,
    telegramId,
    staffNumber,
  };
}

for (const mode of ['FULL', 'LIMITED'] as const) {
  test(`${mode} native user PDF preserves selected ordered fields and its committed audit @cloud-accepted`, async ({
    dataset,
  }, info) => {
    const nativeUntil = Date.now() + info.timeout;
    const owner = await dataset.user('ADMIN', undefined, mode === 'LIMITED');
    const actor = await admit(dataset.request, owner, 'ADMIN');
    const prefix = `Эк${randomUUID().replace(/-/g, '').slice(0, 8)}`;
    const targets = [
      await registryPdfUser(dataset, prefix, 'А'),
      await registryPdfUser(dataset, prefix, 'Я'),
    ].sort((left, right) => (left.login < right.login ? 1 : -1));
    const nonmatching = await dataset.user('STUDENT', dataset.otherGroup.id);
    registryRequire(targets[0]!.login !== targets[1]!.login, 'OWN_DISTINCT_ROWS');
    const startedAt = Date.now();
    const created = await actor.call(
      'POST',
      '/api/auth/export-jobs',
      {
        requestId: randomUUID(),
        report: {
          kind: 'ADMIN_USERS',
          adminUsers: {
            search: prefix,
            sorts: [{ field: 'login', descending: true }],
            format: 'pdf',
          },
        },
      },
      [202],
    );
    const jobId: unknown = created.jobId;
    registryRequire(
      typeof jobId === 'string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(jobId),
      'OWN_JOB_ID',
    );
    await expect
      .poll(
        async () => {
          const status = await actor.call('GET', `/api/auth/export-jobs/${jobId}`);
          registryRequire(
            status.jobId === jobId && ['queued', 'preparing', 'ready'].includes(status.state),
            'OWN_JOB_STATUS',
          );
          return status.state;
        },
        { timeout: 60000, message: 'Own native PDF export becomes durably ready' },
      )
      .toBe('ready');
    const ticket = await actor.call('POST', `/api/auth/export-jobs/${jobId}/ticket`);
    registryRequire(
      typeof ticket.downloadPath === 'string' &&
        /^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(ticket.downloadPath) &&
        typeof ticket.suggestedFilename === 'string' &&
        ticket.suggestedFilename.endsWith('.pdf'),
      'OWN_TICKET_PATH',
    );
    const response = await dataset.request.get(ticket.downloadPath);
    try {
      if (response.status() !== 200)
        throw datasetHttpFailure('GET', ticket.downloadPath, response.status());
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']?.split(';')[0] === 'application/pdf').toBe(true);
      const content = await response.body();
      const info = registryPoppler(nativeUntil, content, 'pdfinfo');
      const pageCount = Number(/^Pages:\s+([0-9]+)\s*$/m.exec(info)?.[1]);
      registryRequire(
        Number.isSafeInteger(pageCount) && pageCount > 0 && pageCount <= 4,
        'PDF_PAGES',
      );
      const document = registryPoppler(nativeUntil, content, 'pdftotext');
      const extracted = [...document.matchAll(/<word\b[^>]*>([^<]*)<\/word>/g)]
        .map((word) => registryPdfDecode(word[1]!))
        .join(' ')
        .replace(/\s+/g, '');
      expect((document.match(/<page\b/g) ?? []).length === pageCount).toBe(true);
      const expectedRows = targets.map((user) => [
        user.displayName,
        user.login,
        'STUDENT: ACTIVE; TEACHER: ACTIVE',
        dataset.group.name as string,
        user.staffNumber,
        'Привязан',
        ...(mode === 'FULL' ? [String(user.telegramId)] : []),
      ]);
      expect(registryPdfCells(document, expectedRows, mode === 'FULL')).toBe(true);
      // Test only booleans; neither parsed names nor synthetic initial credentials
      // may appear in an assertion diff or native-process error.
      expect(
        extracted.includes(nonmatching.login) ||
          extracted.includes(nonmatching.displayName.replace(/\s+/g, '')),
      ).toBe(false);
      expect(/парол|password|password-reset/i.test(extracted)).toBe(false);
      for (const user of [...targets, nonmatching, owner]) {
        expect(extracted.includes(user.password)).toBe(false);
        if (mode === 'LIMITED') expect(extracted.includes(String(user.telegramId))).toBe(false);
      }
      registryAudit(nativeUntil, jobId, owner.id, startedAt);
    } finally {
      await response.dispose();
    }
  });
}
