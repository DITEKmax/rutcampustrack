import { randomUUID } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { createServer } from 'node:http';
import { execFileSync } from 'node:child_process';
import type { BrowserContext, Page, Request, Response, TestInfo } from '@playwright/test';
import {
  test as mobileTest,
  expect,
  admit,
  selectPwaRole,
  type MobileDataset,
} from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { installTelegramHost, syntheticInitData } from '../fixtures/telegram';
import { requirePrivateArtifacts, sanitizeTestErrors } from '../fixtures/artifact-privacy';
import { datasetHttpFailure } from '../fixtures/safe-reporter';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';

// This opt-in fixture gets Playwright's independent teardown budget even when its body times out.
const test = mobileTest.extend<{
  directCacheControl: {
    context: BrowserContext;
    server: ReturnType<typeof createServer> | undefined;
  };
}>({
  directCacheControl: [
    async ({ browser }, use) => {
      const owned = {
        context: await browser.newContext({ serviceWorkers: 'allow' }),
        server: undefined as ReturnType<typeof createServer> | undefined,
      };
      try {
        await use(owned);
      } finally {
        const failures: unknown[] = [];
        if (owned.server?.listening) {
          owned.server.closeAllConnections();
          try {
            await new Promise<void>((resolve, reject) =>
              owned.server!.close((error) => (error ? reject(error) : resolve())),
            );
          } catch (error) {
            failures.push(error);
          }
        }
        try {
          await owned.context.close();
        } catch (error) {
          failures.push(error);
        }
        if (failures.length) throw failures[0];
      }
    },
    { timeout: 10000 },
  ],
});

// Generated credentials stay in memory; browser traces can capture login POSTs.
// Existing mandatory auth-smoke artifact policy remains unchanged.
test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test.describe.configure({ mode: 'default' });
test.afterEach(async ({}, info) => sanitizeTestErrors(info));

// Charge requested fixtures and body setup to this caller's monotonic budget.
// Earlier runner/auto-fixture overhead is unmeasured; the original timeout applies.
const retryStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async ({}, info) => {
  retryStartedAt.set(info, performance.now());
});

// Only the existing HEADMAN/TEACHER case needs completed journal data. Preparation
// has the dataset's existing 180s fixture budget; its original 60s body stays intact.
const it = test.extend<{
  teacherJournalSeed: {
    lessonId: number;
    date: string;
    png: Buffer;
    foreignLessonId: number;
    foreignRequestId: string;
    foreignAttachmentId: string;
    assistantActor: Awaited<ReturnType<typeof admit>>;
  };
  teacherGroupsSeed: {
    groupId: number;
    groupName: string;
    subjectId: number;
    lessonId: number;
    headmanId: number;
    headmanName: string;
  };
}>({
  teacherJournalSeed: [
    async ({ activeHeadmanDataset: dataset }, use) => {
      const date = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000)
        .toISOString()
        .slice(0, 10);
      const assignment = dataset.subject.assignments[0];
      expect(
        dataset.semester.dateFrom <= date &&
          date <= dataset.semester.dateTo &&
          assignment.validFrom <= date &&
          (assignment.validUntilExclusive == null || date < assignment.validUntilExclusive),
      ).toBe(true);
      const past = await dataset.headmanActor.call(
        'POST',
        '/api/schedule/one-off-lessons',
        {
          groupId: dataset.group.id,
          subjectId: dataset.subject.id,
          assignmentId: assignment.id,
          date,
          lessonNumber: 1,
          classroom: 'Синтетический журнал',
        },
        [201, 200],
        { 'Idempotency-Key': randomUUID() },
      );
      const assistantActor = await admit(dataset.request, dataset.assistant, 'STUDENT');
      const png = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
        'base64',
      );
      // A genuine foreign ticket needs its own headman and a different teacher;
      // the existing foreign student remains the requester and has no own-group grant.
      const foreignHeadman = await dataset.user('STUDENT', dataset.otherGroup.id);
      const foreignTeacher = await dataset.user('TEACHER');
      await dataset.admin.call('PUT', `/api/academic/groups/${dataset.otherGroup.id}/headman`, {
        studentId: foreignHeadman.id,
        expectedHeadmanId: null,
      });
      const foreignHeadmanActor = await admit(dataset.request, foreignHeadman, 'HEADMAN');
      let foreignSubject = await foreignHeadmanActor.call(
        'POST',
        '/api/academic/subjects',
        {
          name: `foreign-journal-${dataset.otherGroup.id}`,
          type: 'LECTURE',
          lessonTypes: ['LECTURE'],
          initialAssignments: [
            {
              teacherId: foreignTeacher.id,
              semesterId: dataset.semester.id,
              lessonType: 'LECTURE',
              validFrom: dataset.semester.dateFrom,
              validUntilExclusive: null,
            },
          ],
        },
        [201],
      );
      await expect
        .poll(
          async () => {
            foreignSubject = await foreignHeadmanActor.call(
              'GET',
              `/api/academic/subjects/${foreignSubject.id}`,
            );
            return foreignSubject.assignments?.length ?? 0;
          },
          { timeout: 60000, message: 'Foreign assignment adoption becomes published' },
        )
        .toBeGreaterThan(0);
      const foreignLesson = await foreignHeadmanActor.call(
        'POST',
        '/api/schedule/one-off-lessons',
        {
          groupId: dataset.otherGroup.id,
          subjectId: foreignSubject.id,
          assignmentId: foreignSubject.assignments[0].id,
          date: dataset.tomorrow,
          lessonNumber: 1,
          classroom: 'Синтетическая чужая группа',
        },
        [201, 200],
        { 'Idempotency-Key': randomUUID() },
      );
      const foreignStudent = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
      const foreignCreated = await dataset.request.post('/api/v1/student/requests/excuse', {
        headers: { ...foreignStudent.headers(), 'Idempotency-Key': randomUUID() },
        multipart: {
          request: {
            name: 'request.json',
            mimeType: 'application/json',
            buffer: Buffer.from(
              JSON.stringify({
                lessonIds: [String(foreignLesson.physicalLessonId)],
                reason: 'ILLNESS',
                comment: `foreign-ticket-${dataset.otherGroup.id}`,
              }),
            ),
          },
          files: { name: 'foreign-journal.png', mimeType: 'image/png', buffer: png },
        },
      });
      expect(foreignCreated.status()).toBe(200);
      const foreignRequest = await foreignCreated.json();
      expect(foreignRequest.attachments).toHaveLength(1);
      expect(foreignRequest.attachments[0].state).toBe('ACTIVE');
      // PLANNED -> ACTIVE -> CLOSED takes two real scheduler ticks. No SQL or clock writes.
      let report: {
        lessonStatus: string;
        entries: { userId: number; status: string; markRevision: string }[];
      };
      await expect
        .poll(
          async () => {
            report = (await dataset.headmanActor.call(
              'GET',
              `/api/attendance/reports/lesson/${past.physicalLessonId}`,
            )) as typeof report;
            return (
              report.lessonStatus === 'CLOSED' &&
              [dataset.student.id, dataset.headman.id, dataset.assistant.id].every((userId) => {
                const row = report.entries.find((entry) => Number(entry.userId) === userId);
                return (
                  row?.status === 'absent' &&
                  typeof row.markRevision === 'string' &&
                  row.markRevision.length > 0
                );
              })
            );
          },
          { timeout: 150000, intervals: [1000], message: 'Own journal lesson closes with absence' },
        )
        .toBe(true);
      const manual = report!.entries.find((row) => Number(row.userId) === dataset.headman.id)!;
      await dataset.headmanActor.call(
        'PUT',
        `/api/attendance/lessons/${past.physicalLessonId}/students/${dataset.headman.id}`,
        {
          status: 'EXCUSED',
          excuseType: 'ILLNESS',
          comment: 'Синтетическая ручная причина',
          expectedStatus: 'ABSENT',
          expectedRevision: manual.markRevision,
        },
      );
      await use({
        lessonId: Number(past.physicalLessonId),
        date,
        png,
        foreignLessonId: Number(foreignLesson.physicalLessonId),
        foreignRequestId: foreignRequest.summary.id,
        foreignAttachmentId: foreignRequest.attachments[0].id,
        assistantActor,
      });
    },
    { timeout: 180000 },
  ],
  teacherGroupsSeed: [
    async ({ dataset }, use, info) => {
      const setupDeadline = performance.now() + 170000;
      const setupRemaining = (cap: number) =>
        Math.max(1, Math.min(cap, setupDeadline - performance.now()));
      const teacher = await admit(dataset.request, dataset.teacher, 'TEACHER');
      const catalogPath = `/api/v1/teacher/catalog?semesterId=${dataset.semester.id}`;
      const scope = (catalog: Record<string, any>) =>
        JSON.stringify({
          groups: catalog.groups.map((row: { id: string }) => row.id).sort(),
          pairs: [
            ...new Set(
              [...catalog.taughtSubjectOptions, ...catalog.subjectOptions].map(
                (row: { groupId: string; subjectId: string }) => `${row.groupId}:${row.subjectId}`,
              ),
            ),
          ].sort(),
        });
      const beforeScope = scope(await teacher.call('GET', catalogPath));
      const alphabeticCode = String(dataset.group.alphabeticCode);
      const numericCode = '311';
      const name = `${alphabeticCode}-${numericCode}`;
      for (const status of ['ACTIVE', 'DRAFT', 'ARCHIVED']) {
        const registry = await dataset.admin.call(
          'GET',
          `/api/academic/groups/registry?status=${status}&search=${encodeURIComponent(alphabeticCode)}&size=100`,
        );
        expect(registry.totalPages <= 1 && registry.items.length === registry.totalElements).toBe(
          true,
        );
        expect(
          registry.items.some(
            (row: { alphabeticCode: string; numericCode: string; name: string }) =>
              (row.alphabeticCode === alphabeticCode && row.numericCode === numericCode) ||
              row.name === name ||
              row.name.startsWith(`${name} (`),
          ),
        ).toBe(false);
      }
      const group = await dataset.admin.call(
        'POST',
        '/api/academic/groups/registry',
        { alphabeticCode, numericCode, trainingDurationYears: 4 },
        [201],
      );
      expect(group.id !== dataset.group.id && group.id !== dataset.otherGroup.id).toBe(true);
      const headman = await dataset.user('STUDENT', group.id);
      await dataset.admin.call('PUT', `/api/academic/groups/${group.id}/headman`, {
        studentId: headman.id,
        expectedHeadmanId: null,
      });
      const headmanActor = await admit(dataset.request, headman, 'HEADMAN');
      // Capture the subject before any shared teacher scope can change, including a lost response.
      const subject = await headmanActor.call(
        'POST',
        '/api/academic/subjects',
        {
          name: dataset.subject.name,
          type: 'LECTURE',
          lessonTypes: ['LECTURE'],
          initialAssignments: [],
        },
        [201],
      );
      expect(subject.id !== dataset.subject.id).toBe(true);
      const date = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000)
        .toISOString()
        .slice(0, 10);
      expect(dataset.semester.dateFrom <= date && date <= dataset.semester.dateTo).toBe(true);
      let assignmentAttempted = false;
      let failed = false;
      let assignmentId: number | undefined;
      const assignmentForSubject = async () => {
        const current = await headmanActor.call('GET', `/api/academic/subjects/${subject.id}`);
        return current.assignments?.find(
          (row: { teacherId: number; semesterId: number; lessonType: string }) =>
            Number(row.teacherId) === dataset.teacher.id &&
            Number(row.semesterId) === dataset.semester.id &&
            row.lessonType === 'LECTURE',
        );
      };
      try {
        assignmentAttempted = true;
        await headmanActor.call(
          'POST',
          `/api/academic/subjects/${subject.id}/teachers/${dataset.teacher.id}`,
          {
            semesterId: dataset.semester.id,
            lessonType: 'LECTURE',
            validFrom: dataset.semester.dateFrom,
            validUntilExclusive: null,
          },
          [201],
        );
        await expect
          .poll(
            async () => {
              const assignment = await assignmentForSubject();
              if (assignment) {
                assignmentId = Number(assignment.id);
                expect(
                  assignment.validFrom <= date &&
                    (assignment.validUntilExclusive == null ||
                      date < assignment.validUntilExclusive),
                ).toBe(true);
              }
              return assignmentId != null;
            },
            {
              timeout: setupRemaining(60000),
              message: 'Second own group assignment becomes published',
            },
          )
          .toBe(true);
        const lesson = await headmanActor.call(
          'POST',
          '/api/schedule/one-off-lessons',
          {
            groupId: group.id,
            subjectId: subject.id,
            assignmentId,
            date,
            lessonNumber: 2,
            classroom: 'Синтетическая вторая группа',
          },
          [201, 200],
          { 'Idempotency-Key': randomUUID() },
        );
        await expect
          .poll(
            async () => {
              const report = await headmanActor.call(
                'GET',
                `/api/attendance/reports/lesson/${lesson.physicalLessonId}`,
              );
              return (
                report.lessonStatus === 'CLOSED' &&
                report.entries.length === 1 &&
                Number(report.entries[0].userId) === headman.id &&
                report.entries[0].status === 'absent'
              );
            },
            {
              timeout: setupRemaining(150000),
              intervals: [1000],
              message: 'Second own group lesson closes with its sole roster member',
            },
          )
          .toBe(true);
        await use({
          groupId: Number(group.id),
          groupName: group.name,
          subjectId: Number(subject.id),
          lessonId: Number(lesson.physicalLessonId),
          headmanId: headman.id,
          headmanName: headman.displayName,
        });
      } catch (error) {
        failed = true;
        throw error;
      } finally {
        try {
          if (assignmentAttempted) {
            // Recover only this known owned subject after an accepted/lost assignment response.
            if (assignmentId == null)
              await expect
                .poll(
                  async () => {
                    const assignment = await assignmentForSubject();
                    assignmentId = assignment == null ? undefined : Number(assignment.id);
                    return assignmentId != null;
                  },
                  { timeout: 30000, message: 'Recover only the second own assignment for cleanup' },
                )
                .toBe(true);
            const parts = new Intl.DateTimeFormat('en-CA', {
              timeZone: 'Europe/Moscow',
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
            }).formatToParts(new Date());
            const part = (key: string) => parts.find((value) => value.type === key)!.value;
            const cutoff = `${part('year')}-${part('month')}-${part('day')}`;
            expect(dataset.semester.dateFrom <= cutoff && cutoff <= dataset.semester.dateTo).toBe(
              true,
            );
            const closeKey = randomUUID();
            await expect
              .poll(
                async () => {
                  const response = await dataset.request.delete(
                    `/api/academic/assignments/${assignmentId}?validUntilExclusive=${cutoff}`,
                    {
                      headers: { ...headmanActor.headers(), 'Idempotency-Key': closeKey },
                      timeout: 10000,
                    },
                  );
                  try {
                    expect([204, 503]).toContain(response.status());
                    return response.status() === 204;
                  } finally {
                    await response.dispose();
                  }
                },
                {
                  timeout: 30000,
                  message: 'Close only the second own assignment with its original command',
                },
              )
              .toBe(true);
          }
          // Original suspension/reactivation can invalidate the earlier token; admit afresh.
          const currentTeacher = await admit(dataset.request, dataset.teacher, 'TEACHER');
          await expect
            .poll(
              async () => scope(await currentTeacher.call('GET', catalogPath)) === beforeScope,
              { timeout: 10000, message: 'Original teacher group and subject scope is restored' },
            )
            .toBe(true);
          // Retain the new group's legitimate member, lesson and closed assignment history.
        } catch (cleanupError) {
          if (failed || info.status !== info.expectedStatus)
            expect.soft(false, 'TEACHER_OWN_GROUP_SCOPE_RESTORE_FAILED').toBe(true);
          else throw cleanupError;
        }
      }
    },
    { timeout: 180000 },
  ],
});

async function selectTomorrowHomework(page: Page, dataset: MobileDataset): Promise<void> {
  await expect(page.getByRole('tab', { name: 'День', exact: true })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  const next = page.getByRole('button', { name: 'Следующий период', exact: true });
  await expect(next).toBeEnabled();
  const read = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === '/api/v1/student/homework' &&
      response.request().method() === 'GET' &&
      url.searchParams.get('from') === dataset.tomorrow &&
      url.searchParams.get('to') === dataset.tomorrow
    );
  });
  await next.click();
  const response = await read;
  expect(response.status()).toBe(200);
  const feed = await response.json();
  expect(feed.from === dataset.tomorrow && feed.to === dataset.tomorrow).toBe(true);
}

async function publishOfflineHomework(dataset: MobileDataset, title: string): Promise<void> {
  const requestKey = randomUUID();
  await expect
    .poll(async () => {
      const result = await dataset.headmanActor.call(
        'POST',
        '/api/academic/homeworks',
        {
          title,
          description: 'Синтетическое задание для проверки границы офлайн-доступа',
          link: null,
          subjectId: dataset.subject.id,
          groupId: dataset.group.id,
          semesterId: dataset.semester.id,
          lessonDate: dataset.today,
          lessonNumber: null,
          bindingMode: 'DATE',
          lessonType: 'LECTURE',
          requestKey,
        },
        [201, 200, 202],
      );
      return result.state !== 'PENDING' && result.id != null;
    })
    .toBe(true);
}

async function requireProductionWorker(page: Page): Promise<void> {
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await expect
    .poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null), {
      timeout: 15000,
    })
    .toBe(true);
}

// Only the CI-owned static container participates; no backend, source tree or volume is changed.
function productionWorkerFixture() {
  if (
    process.env.RCT_E2E_SW_LIFECYCLE !== 'true' ||
    process.env.RCT_E2E_REMAINING5 !== 'true' ||
    process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' ||
    process.env.RUNNER_ENVIRONMENT !== 'github-hosted' ||
    process.env.CI !== 'true' ||
    process.env.E2E_BASE_URL !== 'https://localhost:8443' ||
    !process.env.GITHUB_WORKSPACE
  ) {
    throw new Error('Production worker fixture requires the owned disposable CI stack');
  }
  function docker(args: string[], input?: Buffer): Buffer {
    try {
      return execFileSync('docker', args, {
        input,
        timeout: 10000,
        maxBuffer: 65536,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch {
      // Child-process errors include captured arguments/stdout; never forward them to reporters.
      throw new Error('Owned production worker fixture operation failed');
    }
  }
  const target = JSON.parse(
    docker([
      'inspect',
      '--format',
      '{"id":{{json .Id}},"labels":{{json .Config.Labels}},"networks":{{json .NetworkSettings.Networks}},"mounts":{{json .Mounts}},"readonly":{{json .HostConfig.ReadonlyRootfs}},"running":{{json .State.Running}}}',
      'rct-e2e-pwa-nginx-1',
    ]).toString('utf8'),
  );
  const network = JSON.parse(
    docker([
      'network',
      'inspect',
      '--format',
      '{"id":{{json .Id}},"labels":{{json .Labels}}}',
      'rct-e2e_private_net',
    ]).toString('utf8'),
  );
  if (
    !/^[a-f0-9]{64}$/.test(target.id) ||
    target.labels?.['com.docker.compose.project'] !== 'rct-e2e' ||
    target.labels?.['com.docker.compose.service'] !== 'pwa-nginx' ||
    target.labels?.['com.docker.compose.project.working_dir'] !== process.env.GITHUB_WORKSPACE ||
    target.running !== true ||
    target.readonly !== false ||
    !Array.isArray(target.mounts) ||
    target.mounts.length !== 0 ||
    Object.keys(target.networks).join() !== 'rct-e2e_private_net' ||
    target.networks['rct-e2e_private_net'].NetworkID !== network.id ||
    network.labels?.['com.docker.compose.project'] !== 'rct-e2e' ||
    network.labels?.['com.docker.compose.network'] !== 'private_net'
  ) {
    throw new Error('Production worker fixture ownership or writable-layer mismatch');
  }
  const regularFiles = `
    for directory in /usr /usr/share /usr/share/nginx /usr/share/nginx/html; do
      test -d "$directory" && test ! -L "$directory"
    done
    path="/usr/share/nginx/html/$1"
    test -f "$path" && test ! -L "$path"
    test "$(stat -c %a "$path")" = 644
    test "$(wc -c < "$path")" -le 32768
  `;
  function read(name: 'sw.js' | 'version.json'): Buffer {
    return docker([
      'exec',
      target.id,
      'sh',
      '-eu',
      '-c',
      `${regularFiles}\ncat "$path"`,
      'sh',
      name,
    ]);
  }
  function write(name: 'sw.js' | 'version.json', bytes: Buffer): void {
    if (bytes.length === 0 || bytes.length > 32768)
      throw new Error('Invalid bounded static fixture');
    docker(
      [
        'exec',
        '-i',
        target.id,
        'sh',
        '-eu',
        '-c',
        `${regularFiles}
          temporary="$path.rct-e2e-fixture"
          # Exclusive creation and rename avoid following a destination link when writing.
          (set -C; cat > "$temporary")
          trap 'rm -f -- "$temporary"' EXIT
          chmod 644 "$temporary"
          mv -f -- "$temporary" "$path"
        `,
        'sh',
        name,
      ],
      bytes,
    );
    if (!read(name).equals(bytes)) throw new Error('Static fixture byte verification failed');
  }
  const worker = read('sw.js');
  const policy = read('version.json');
  const marker = 'const releaseVersion = "0.0.0"';
  const source = worker.toString('utf8');
  const version = JSON.parse(policy.toString('utf8'));
  if (source.split(marker).length !== 2 || version.latest !== '0.0.0') {
    throw new Error('Unexpected production worker or policy release');
  }
  return {
    update() {
      write('sw.js', Buffer.from(source.replace(marker, 'const releaseVersion = "0.0.1"')));
      write(
        'version.json',
        Buffer.from(JSON.stringify({ ...version, latest: '0.0.1', force: true })),
      );
    },
    restorePolicy() {
      write('version.json', policy);
    },
    restore() {
      const errors: unknown[] = [];
      for (const [name, bytes] of [
        ['sw.js', worker],
        ['version.json', policy],
      ] as const) {
        try {
          write(name, bytes);
        } catch (error) {
          errors.push(error);
        }
      }
      if (errors.length)
        throw new AggregateError(errors, 'Production static fixture restoration failed');
    },
  };
}

test.describe('Current Vue roles with real synthetic domain @real-mobile', () => {
  test('STUDENT completion persists and another group cannot mutate the homework', async ({
    page,
    dataset,
  }) => {
    const { accessToken } = await loginPwa(page, dataset.student);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectTomorrowHomework(page, dataset);
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
    const completion = page.getByRole('slider', {
      name: `Выполнить: ${dataset.marker}`,
      exact: true,
    });
    await completion.focus();
    for (let index = 0; index < 4; index++) await completion.press('ArrowRight');
    const saved = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/student/homework/${dataset.homework.id}/completion` &&
        response.request().method() === 'PUT',
    );
    await completion.press('Enter');
    expect((await saved).status()).toBe(200);
    await expect(
      page.getByRole('slider', { name: `Снять отметку: ${dataset.marker}`, exact: true }),
    ).toBeVisible();
    await page.reload();
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectTomorrowHomework(page, dataset);
    await expect(
      page.getByRole('slider', { name: `Снять отметку: ${dataset.marker}`, exact: true }),
    ).toBeVisible();
    const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
    const denied = await dataset.request.put(
      `/api/v1/student/homework/${dataset.homework.id}/completion`,
      { headers: foreign.headers(), data: { completed: true } },
    );
    expect([403, 404]).toContain(denied.status());
    const read = await page.request.get(
      `/api/v1/student/homework?from=${dataset.today}&to=${dataset.tomorrow}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(read.status()).toBe(200);
    expect(
      (await read.json()).items.find(
        (item: { id: string }) => String(item.id) === String(dataset.homework.id),
      ).completed,
    ).toBe(true);
  });

  it('HEADMAN marks the real roster and TEACHER reads without acquiring write permission', async ({
    page,
    activeHeadmanDataset: dataset,
    ignoreHTTPSErrors,
    teacherJournalSeed: journalSeed,
  }, info) => {
    let approvedRequest: { id: string; attachmentId: string };
    await loginPwa(page, dataset.headman);
    await selectPwaRole(page, 'Староста');
    await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
    const marked = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.student.id}` &&
        response.request().method() === 'PUT',
    );
    await page
      .getByRole('button', { name: `Был: ${dataset.student.displayName}`, exact: true })
      .click();
    expect((await marked).status()).toBe(200);
    await expect(
      page.getByRole('button', {
        name: `Был: ${dataset.student.displayName}. Открыть выбор отметки`,
        exact: true,
      }),
    ).toBeVisible();
    const id = dataset.lesson.physicalLessonId;
    const markPath = `/api/attendance/lessons/${id}/students/${dataset.student.id}`;
    const reportPath = `/api/attendance/reports/lesson/${id}`;
    const studentRow = () =>
      page.locator(`.headman-journal__roster-list [data-user-id="${dataset.student.id}"]`);
    const storedStudent = async () => {
      const report = await dataset.headmanActor.call('GET', reportPath);
      return report.entries.find(
        (row: { userId: number }) => Number(row.userId) === dataset.student.id,
      );
    };
    const choose = async (label: string, method: 'PUT' | 'DELETE') => {
      const response = page.waitForResponse(
        (item) => new URL(item.url()).pathname === markPath && item.request().method() === method,
      );
      await studentRow()
        .getByRole('button', { name: `${label}: ${dataset.student.displayName}`, exact: true })
        .click();
      expect((await response).status()).toBe(method === 'DELETE' ? 204 : 200);
    };
    const openChoices = async () => {
      const before = await storedStudent();
      await studentRow()
        .getByRole('button', {
          name: `Изменить отметку: ${dataset.student.displayName}`,
          exact: true,
        })
        .click();
      for (const label of ['Был', 'Уважительная причина', 'Не был']) {
        await expect(
          studentRow().getByRole('button', {
            name: `${label}: ${dataset.student.displayName}`,
            exact: true,
          }),
        ).toBeVisible();
      }
      expect(await storedStudent()).toEqual(before);
    };
    await openChoices();
    await expect(
      studentRow().getByRole('button', {
        name: `Был: ${dataset.student.displayName}`,
        exact: true,
      }),
    ).toHaveAttribute('aria-pressed', 'true');
    await choose('Был', 'DELETE');
    await expect(studentRow()).toHaveAttribute('data-status', 'empty');
    expect((await storedStudent()).status).toBeNull();
    await choose('Не был', 'PUT');
    await expect(studentRow()).toHaveAttribute('data-status', 'absent');
    expect(await storedStudent()).toMatchObject({ status: 'absent', source: 'headman' });
    await openChoices();
    await choose('Не был', 'DELETE');
    await expect(studentRow()).toHaveAttribute('data-status', 'empty');

    // The existing student's blank row is a genuine geo witness; a headman remains
    // HEADMAN_USES_JOURNAL even after choosing its STUDENT role.
    await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
    await expect(
      page.getByRole('button', { name: 'Открыть учёт выбранной пары', exact: true }),
    ).toBeVisible();
    const blockTrigger = page.getByRole('button', {
      name: 'Заблокировать геоотметки',
      exact: true,
    });
    await blockTrigger.focus();
    await blockTrigger.press('Enter');
    const confirmation = page.getByRole('group', {
      name: 'Подтвердить блокировку геоотметок',
      exact: true,
    });
    await expect(confirmation).toBeVisible();
    const cancelBlock = confirmation.getByRole('button', { name: 'Отмена', exact: true });
    await expect(cancelBlock).toBeFocused();
    await cancelBlock.press('Escape');
    await expect(confirmation).toHaveCount(0);
    await expect(blockTrigger).toBeFocused();
    await blockTrigger.press('Enter');
    await expect(cancelBlock).toBeFocused();
    await cancelBlock.click();
    await expect(confirmation).toHaveCount(0);
    await expect(blockTrigger).toBeFocused();
    await expect(
      page.getByRole('button', { name: 'Заблокировать геоотметки', exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Заблокировать геоотметки', exact: true }).click();
    const blocked = page.waitForResponse(
      (item) =>
        new URL(item.url()).pathname === `/api/schedule/lessons/${id}/blockage` &&
        item.request().method() === 'POST',
    );
    await confirmation
      .getByRole('button', { name: 'Подтвердить блокировку геоотметок', exact: true })
      .click();
    const blockAck = await blocked;
    expect(blockAck.status()).toBe(200);
    expect((await blockAck.json()).blockedByHeadman).toBe(true);
    const browser = page.context().browser();
    if (!browser) throw new Error('Synthetic student browser context is required');
    requirePrivateArtifacts(info);
    // Match the existing disposable stand's TLS setting; do not enable capture.
    const studentContext = await browser.newContext({
      baseURL: new URL(page.url()).origin,
      ...(ignoreHTTPSErrors === undefined ? {} : { ignoreHTTPSErrors }),
    });
    try {
      const studentPage = await studentContext.newPage();
      const studentLogin = await loginPwa(studentPage, dataset.student);
      const headers = { Authorization: `Bearer ${studentLogin.accessToken}` };
      const snapshot = async () => {
        const response = await studentPage.request.get('/api/v1/student/today', { headers });
        expect(response.status()).toBe(200);
        return (await response.json()).lessons.find(
          (row: { schedule: { id: string } }) => row.schedule.id === String(id),
        );
      };
      const budget = async () => {
        const response = await studentPage.request.get('/api/v1/student/requests/options', {
          headers,
        });
        expect(response.status()).toBe(200);
        return (await response.json()).budget;
      };
      const beforeBudget = await budget();
      expect((await snapshot()).checkinEligibility).toMatchObject({
        allowed: false,
        reason: 'GEO_BLOCKED',
      });
      await expect(
        studentPage.getByRole('button', { name: 'Отметка по геолокации недоступна', exact: true }),
      ).toBeDisabled();
      const denied = await studentPage.request.post(`/api/v1/student/lessons/${id}/checkin`, {
        headers: { ...headers, 'Idempotency-Key': randomUUID() },
        data: { geo: { kind: 'COORDINATES', latitude: 0, longitude: 0 } },
      });
      expect(denied.status()).toBe(409);
      expect((await snapshot()).request).toBeNull();
      expect(await budget()).toEqual(beforeBudget);
      await page.getByRole('button', { name: 'Открыть учёт выбранной пары', exact: true }).click();
      await choose('Был', 'PUT');
      expect(await storedStudent()).toMatchObject({ status: 'present', source: 'headman' });
      await openChoices();
      await choose('Был', 'DELETE');
      await page.getByRole('button', { name: 'Назад', exact: true }).click();
      await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
      const unblocked = page.waitForResponse(
        (item) =>
          new URL(item.url()).pathname === `/api/schedule/lessons/${id}/blockage` &&
          item.request().method() === 'DELETE',
      );
      await page.getByRole('button', { name: 'Разблокировать геоотметки', exact: true }).click();
      const unblockAck = await unblocked;
      expect(unblockAck.status()).toBe(200);
      expect((await unblockAck.json()).blockedByHeadman).toBe(false);
      await expect(confirmation).toHaveCount(0);
      await studentPage.reload();
      await expect(
        studentPage.getByRole('button', { name: 'Отметиться', exact: true }),
      ).toBeEnabled();
      expect((await snapshot()).checkinEligibility).toMatchObject({
        allowed: true,
        reason: 'ELIGIBLE',
      });
      expect(await budget()).toEqual(beforeBudget);
      const created = await studentPage.request.post('/api/v1/student/requests/excuse', {
        headers: { ...headers, 'Idempotency-Key': randomUUID() },
        multipart: {
          request: {
            name: 'request.json',
            mimeType: 'application/json',
            buffer: Buffer.from(
              JSON.stringify({
                lessonIds: [String(journalSeed.lessonId)],
                reason: 'ILLNESS',
                comment: 'Синтетическое заявление для журнала преподавателя',
              }),
            ),
          },
          files: { name: 'teacher-journal.png', mimeType: 'image/png', buffer: journalSeed.png },
        },
      });
      expect(created.status()).toBe(200);
      const request = await created.json();
      expect(request.attachments).toHaveLength(1);
      expect(request.attachments[0].state).toBe('ACTIVE');
      const path = `/api/attendance/requests/${request.summary.id}`;
      const pending = await dataset.headmanActor.call('GET', path);
      const approved = await dataset.headmanActor.call('POST', `${path}/decision`, {
        decision: 'APPROVED',
        reason: null,
        expectedRevision: pending.decisionRevision,
        excludedOccurrenceIds: [],
        requestKey: randomUUID(),
      });
      expect(approved.summary.status).toBe('APPROVED');
      approvedRequest = { id: request.summary.id, attachmentId: request.attachments[0].id };
    } finally {
      await studentContext.close();
    }
    await page.getByRole('button', { name: 'Открыть учёт выбранной пары', exact: true }).click();
    await choose('Был', 'PUT');
    await expect(studentRow()).toHaveAttribute('data-status', 'present');
    const future = await dataset.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: dataset.tomorrow,
        lessonNumber: 1,
        classroom: 'Синтетическая аудитория',
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    const futureReport = page.waitForResponse(
      (item) =>
        new URL(item.url()).pathname ===
          `/api/attendance/reports/lesson/${future.physicalLessonId}` &&
        item.request().method() === 'GET',
    );
    await page.getByRole('button', { name: 'Следующая пара или день', exact: true }).click();
    const futureRead = await futureReport;
    expect(futureRead.status()).toBe(200);
    expect(await futureRead.json()).toMatchObject({
      editable: false,
      lessonDate: dataset.tomorrow,
    });
    await expect(page.getByText('Пара ещё не началась', { exact: true }).first()).toBeVisible();
    await expect(page.locator('.headman-journal__picker')).toHaveCount(0);
    await page.getByRole('button', { name: 'Предыдущая пара или день', exact: true }).click();
    await expect(studentRow()).toHaveAttribute('data-status', 'present');
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
    await page
      .getByRole('button', {
        name: `Уважительная причина: ${dataset.headman.displayName}`,
        exact: true,
      })
      .click();
    await page.locator('.headman-journal__excuse-form select').selectOption('ILLNESS');
    await page.getByLabel('Комментарий', { exact: true }).fill('Синтетическая причина');
    const expectedPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
      'base64',
    );
    await page.locator('.headman-journal__excuse-form input[type="file"]').setInputFiles({
      name: 'синтетическая-справка.png',
      mimeType: 'image/png',
      buffer: expectedPng,
    });
    const attachmentSaved = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.headman.id}` &&
        response.request().method() === 'PUT',
    );
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
    expect((await attachmentSaved).status()).toBe(200);
    const attachmentPath = `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.headman.id}/attachment`;
    const opened = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === attachmentPath &&
        response.request().method() === 'GET',
    );
    // The client severs popup.opener, so observe the owning context's new page.
    const openedPopup = page.context().waitForEvent('page');
    await page.getByRole('button', { name: 'Открыть файл', exact: true }).click();
    const file = await opened;
    expect(file.status()).toBe(200);
    expect(file.headers()['content-type']).toContain('image/png');
    const popup = await openedPopup;
    try {
      await popup.waitForURL(/^blob:/);
      const openedBytesEqual = await popup.evaluate(async (expected) => {
        const response = await fetch(location.href);
        // Inspect only the fixture-sized Blob already obtained by the real client.
        if (Number(response.headers.get('content-length')) !== expected.length) return false;
        const bytes = new Uint8Array(await response.arrayBuffer());
        return (
          bytes.length === expected.length && bytes.every((byte, index) => byte === expected[index])
        );
      }, Array.from(expectedPng));
      expect(openedBytesEqual).toBe(true);
    } finally {
      await popup.close();
    }
    const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
    expect([403, 404]).toContain(
      (await dataset.request.get(attachmentPath, { headers: foreign.headers() })).status(),
    );
    await logoutPwa(page);
    const teacherDay = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/teacher/day' &&
        url.searchParams.get('date') === dataset.today &&
        response.request().method() === 'GET'
      );
    });
    const { accessToken } = await loginPwa(page, dataset.teacher);
    await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    const lesson = await page.request.get(
      `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(lesson.status()).toBe(200);
    expect(JSON.stringify(await lesson.json())).toContain(dataset.subject.name);
    const beforeTeacherWrite = await dataset.headmanActor.call('GET', reportPath);
    const denied = await page.request.put(
      `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: { status: 'PRESENT', expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
      },
    );
    expect(denied.status()).toBe(403);
    expect(await dataset.headmanActor.call('GET', reportPath)).toEqual(beforeTeacherWrite);
    const headers = { Authorization: `Bearer ${accessToken}` };
    const dayResponse = await teacherDay;
    expect(dayResponse.status()).toBe(200);
    const day = await dayResponse.json();
    expect(day.date).toBe(dataset.today);
    const ownDayLesson = day.lessons.find((row: { id: string }) => row.id === String(id));
    expect(ownDayLesson).toMatchObject({
      groupId: String(dataset.group.id),
      subjectId: String(dataset.subject.id),
      date: dataset.today,
      oneOff: true,
      room: 'Синтетическая аудитория',
    });
    const todayCard = page
      .locator(`[data-teacher-action="lesson-open"][data-lesson-id="${id}"]`)
      .first();
    await expect(todayCard).toContainText(dataset.subject.name);
    await expect(todayCard).toContainText(ownDayLesson.groupName);
    await expect(todayCard).toContainText('Синтетическая аудитория');
    await expect(todayCard).toContainText(
      `${ownDayLesson.startsAt.slice(0, 5)}–${ownDayLesson.endsAt.slice(0, 5)}`,
    );
    const detailRead = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/v1/teacher/lessons/${id}` &&
        response.request().method() === 'GET',
    );
    await todayCard.click();
    expect((await detailRead).status()).toBe(200);
    await expect(page.getByRole('heading', { name: 'Пара', exact: true })).toBeVisible();
    await expect(page.locator('.teacher-roster')).toContainText(dataset.student.displayName);
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    const catalogRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/teacher/catalog' &&
        url.searchParams.get('semesterId') === String(dataset.semester.id) &&
        response.request().method() === 'GET'
      );
    });
    await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
    const catalogResponse = await catalogRead;
    expect(catalogResponse.status()).toBe(200);
    const catalog = await catalogResponse.json();
    expect(catalog.groups.map((group: { id: string }) => group.id)).toEqual([
      String(dataset.group.id),
    ]);
    const groupName = catalog.groups[0].name;
    await page.getByRole('button', { name: 'Группа', exact: true }).click();
    await expect(page.getByRole('option')).toHaveCount(1);
    await expect(page.getByRole('option', { name: groupName, exact: true })).toBeVisible();
    expect(
      catalog.groups.some((group: { id: string }) => group.id === String(dataset.otherGroup.id)),
    ).toBe(false);
    await page.getByRole('option', { name: groupName, exact: true }).click();
    await page.getByRole('button', { name: 'Предмет', exact: true }).click();
    await page.getByRole('option', { name: dataset.subject.name, exact: true }).click();
    const journalRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/teacher/journal' &&
        url.searchParams.get('semesterId') === String(dataset.semester.id) &&
        url.searchParams.get('groupId') === String(dataset.group.id) &&
        url.searchParams.get('subjectId') === String(dataset.subject.id) &&
        url.searchParams.get('pastOnly') === 'true' &&
        response.request().method() === 'GET'
      );
    });
    await page.getByRole('button', { name: 'Открыть журнал и выгрузку', exact: true }).click();
    await page
      .locator('.teacher-journal')
      .getByRole('button', { name: 'Лекция', exact: true })
      .click();
    const journalResponse = await journalRead;
    expect(journalResponse.status()).toBe(200);
    expect(
      new URL(journalResponse.url()).searchParams
        .getAll('lessonType')
        .map((type) => type.toUpperCase()),
    ).toEqual(['LECTURE']);
    const journal = await journalResponse.json();
    expect(journal.lessons).toHaveLength(1);
    expect(journal.lessons[0]).toMatchObject({
      id: String(journalSeed.lessonId),
      date: journalSeed.date,
      groupId: String(dataset.group.id),
      subjectId: String(dataset.subject.id),
      status: 'closed',
    });
    const expectedNames = [dataset.student, dataset.headman, dataset.assistant]
      .map((user) => user.displayName)
      .sort((a, b) => a.localeCompare(b, 'ru'));
    expect(journal.students.map((student: { displayName: string }) => student.displayName)).toEqual(
      expectedNames,
    );
    const table = page.locator('.teacher-journal__table');
    await expect(table.locator('tbody th[scope="row"]')).toHaveText(expectedNames);
    await expect(table.locator('thead time')).toHaveAttribute('datetime', journalSeed.date);
    const journalStudent = journal.students.find(
      (row: { studentId: string }) => row.studentId === String(dataset.student.id),
    );
    expect(journalStudent.cells).toEqual([
      expect.objectContaining({
        lessonId: String(journalSeed.lessonId),
        status: 'excused',
        ticketId: approvedRequest!.id,
      }),
    ]);
    const journalManual = journal.students.find(
      (row: { studentId: string }) => row.studentId === String(dataset.headman.id),
    );
    expect(journalManual.cells).toEqual([
      expect.objectContaining({
        lessonId: String(journalSeed.lessonId),
        status: 'excused',
        ticketId: null,
        excuseType: 'ILLNESS',
      }),
    ]);
    const journalAbsent = journal.students.find(
      (row: { studentId: string }) => row.studentId === String(dataset.assistant.id),
    );
    expect(journalAbsent.cells).toEqual([
      expect.objectContaining({ lessonId: String(journalSeed.lessonId), status: 'absent' }),
    ]);
    await expect(
      table
        .getByRole('row')
        .filter({ hasText: dataset.assistant.displayName })
        .getByLabel('Отсутствовал', { exact: true }),
    ).toBeVisible();
    const excusePath = `/api/v1/teacher/excuses/${approvedRequest!.id}`;
    const teacherAttachmentPath = `${excusePath}/attachments/${approvedRequest!.attachmentId}`;
    const excuseRead = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === excusePath && response.request().method() === 'GET',
    );
    const teacherFileRead = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === teacherAttachmentPath &&
        response.request().method() === 'GET',
    );
    await table
      .getByRole('row')
      .filter({ hasText: dataset.student.displayName })
      .getByRole('button', { name: /Уважительная причина/ })
      .click();
    const excuseResponse = await excuseRead;
    expect(excuseResponse.status()).toBe(200);
    const excuse = await excuseResponse.json();
    expect(excuse).toMatchObject({
      id: approvedRequest!.id,
      studentId: String(dataset.student.id),
      studentName: dataset.student.displayName,
      groupId: String(dataset.group.id),
      status: 'approved',
      excuseType: 'illness',
      decisionBy: String(dataset.headman.id),
      comment: 'Синтетическое заявление для журнала преподавателя',
    });
    expect(excuse.lessons.map((row: { id: string }) => row.id)).toEqual([
      String(journalSeed.lessonId),
    ]);
    expect(
      Number.isFinite(Date.parse(excuse.createdAt)) &&
        Number.isFinite(Date.parse(excuse.decisionAt)),
    ).toBe(true);
    const excuseMain = page.getByRole('main', { name: 'Причина отсутствия', exact: true });
    await expect(excuseMain).toContainText(dataset.student.displayName);
    await expect(excuseMain).toContainText('Уважительная причина · Одобрена');
    await expect(excuseMain).toContainText('Болезнь');
    await expect(excuseMain).toContainText(excuse.comment);
    const instants = await page.evaluate(
      (values: string[]) =>
        values.map((value) =>
          new Intl.DateTimeFormat('ru-RU', {
            day: 'numeric',
            month: 'long',
            hour: '2-digit',
            minute: '2-digit',
          }).format(new Date(value)),
        ),
      [excuse.createdAt, excuse.decisionAt],
    );
    const details = excuseMain.locator('.teacher-screen__details > div');
    await expect(
      details.filter({ has: page.getByText('Подана', { exact: true }) }).locator('dd'),
    ).toHaveText(instants[0]);
    await expect(
      details.filter({ has: page.getByText('Решение старосты', { exact: true }) }).locator('dd'),
    ).toContainText(instants[1]);
    await expect(
      excuseMain.getByRole('button', { name: /Одобрить|Отклонить|Изменить отметку|Сохранить/ }),
    ).toHaveCount(0);
    const teacherFile = await teacherFileRead;
    expect(teacherFile.status()).toBe(200);
    expect(teacherFile.headers()['content-type']).toContain('image/png');
    // The opened app Blob and direct API below verify bytes; Playwright captured this fetch empty.
    const preview = excuseMain.getByRole('img', { name: 'teacher-journal.png', exact: true });
    await expect(preview).toBeVisible();
    await expect
      .poll(() =>
        preview.evaluate(
          (element) =>
            element instanceof HTMLImageElement &&
            element.complete &&
            element.naturalWidth === 1 &&
            element.naturalHeight === 1,
        ),
      )
      .toBe(true);
    const teacherPopupOpened = page.context().waitForEvent('page');
    await excuseMain
      .getByRole('button', { name: 'Открыть teacher-journal.png', exact: true })
      .click();
    const teacherPopup = await teacherPopupOpened;
    try {
      await teacherPopup.waitForURL(/^blob:/);
      expect(
        await teacherPopup.evaluate(async (expected) => {
          const response = await fetch(location.href);
          const bytes = new Uint8Array(await response.arrayBuffer());
          return (
            bytes.length === expected.length &&
            bytes.every((byte, index) => byte === expected[index])
          );
        }, Array.from(journalSeed.png)),
      ).toBe(true);
    } finally {
      await teacherPopup.close();
    }
    await excuseMain.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(table.locator('tbody th[scope="row"]')).toHaveText(expectedNames);
    await expect(
      page.locator('.teacher-journal').getByRole('button', { name: 'Предмет', exact: true }),
    ).toContainText(dataset.subject.name);
    const manualRead = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === `/api/v1/teacher/lessons/${journalSeed.lessonId}` &&
        response.request().method() === 'GET',
    );
    await table
      .getByRole('row')
      .filter({ hasText: dataset.headman.displayName })
      .getByRole('button', { name: /Уважительная причина/ })
      .click();
    const manualResponse = await manualRead;
    expect(manualResponse.status()).toBe(200);
    expect(
      (await manualResponse.json()).roster.find(
        (row: { studentId: string }) => row.studentId === String(dataset.headman.id),
      ),
    ).toMatchObject({
      ticketId: null,
      status: 'excused',
      source: 'NO_TICKET',
      excuseType: 'ILLNESS',
    });
    const manualMain = page.getByRole('main', { name: 'Причина отсутствия', exact: true });
    await expect(manualMain).toContainText(dataset.headman.displayName);
    await expect(manualMain).toContainText('Болезнь');
    await expect(manualMain).toContainText('Синтетическая ручная причина');
    await expect(
      manualMain.getByText(/^(Автор|Подана|Решение старосты|Комментарий к решению)$/),
    ).toHaveCount(0);
    await expect(
      manualMain.getByRole('button', { name: /Одобрить|Отклонить|Изменить отметку|Сохранить/ }),
    ).toHaveCount(0);
    // Keep the accepted no-ticket explanation oracle even if it exposes the current UI gap.
    // Soft failure retains the remaining real isolation and return-context observations.
    await expect
      .soft(manualMain)
      .toContainText(
        /(?:заявк[аи]|заявлени[ея])\s+(?:нет|не существует)|без\s+(?:заявки|заявления)/i,
      );
    await manualMain.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(table.locator('tbody th[scope="row"]')).toHaveText(expectedNames);
    await expect(
      page.getByRole('heading', { name: new RegExp(`Журнал.*${groupName}`) }),
    ).toBeVisible();
    for (const path of [
      `/api/v1/teacher/lessons/${journalSeed.foreignLessonId}`,
      `/api/v1/teacher/excuses/${journalSeed.foreignRequestId}`,
      `/api/v1/teacher/excuses/${journalSeed.foreignRequestId}/attachments/${journalSeed.foreignAttachmentId}`,
    ]) {
      const forbidden = await page.request.get(path, { headers });
      expect([403, 404]).toContain(forbidden.status());
      expect(forbidden.headers()['content-type'] ?? '').not.toContain('image/png');
      const forbiddenBody = await forbidden.body();
      expect(forbiddenBody.includes(journalSeed.png)).toBe(false);
      expect(forbiddenBody.includes(Buffer.from(dataset.foreignStudent.displayName))).toBe(false);
      expect(forbiddenBody.includes(Buffer.from(`foreign-ticket-${dataset.otherGroup.id}`))).toBe(
        false,
      );
    }
    const ownStillReadable = await page.request.get(excusePath, { headers });
    expect(ownStillReadable.status()).toBe(200);
    expect((await ownStillReadable.json()).id).toBe(approvedRequest!.id);
    const ownFileStillReadable = await page.request.get(teacherAttachmentPath, { headers });
    expect(ownFileStillReadable.status()).toBe(200);
    expect((await ownFileStillReadable.body()).equals(journalSeed.png)).toBe(true);

    // These additional reads run only after every original teacher/attachment/rights oracle.
    const assistantRow = table.locator('tbody tr').filter({
      has: page.getByRole('rowheader', { name: dataset.assistant.displayName, exact: true }),
    });
    const automatic = assistantRow.getByLabel('Отсутствовал', { exact: true });
    await expect(automatic).toHaveAttribute('data-auto-absent', 'true');
    await expect(automatic).toHaveAttribute(
      'aria-description',
      'Отсутствие отмечено автоматически при закрытии пары',
    );
    const pendingKey = randomUUID();
    let pendingId: string | undefined;
    let contrastLessonId: number | undefined;
    let originalCancelAttempted = false;
    let pastCancelAttempted = false;
    let shortcutSubjectId: number | undefined;
    const shortcutCloseKey = randomUUID();
    const currentSubjectScope = (value: {
      taughtSubjectOptions: { groupId: string; subjectId: string; lessonTypes: string[] }[];
    }) =>
      value.taughtSubjectOptions
        .flatMap((option) =>
          option.lessonTypes.map(
            (type) => `${option.groupId}:${option.subjectId}:${type.toUpperCase()}`,
          ),
        )
        .sort();
    try {
      const submitted = await dataset.request.post('/api/v1/student/requests/excuse', {
        headers: { ...journalSeed.assistantActor.headers(), 'Idempotency-Key': pendingKey },
        multipart: {
          request: {
            name: 'request.json',
            mimeType: 'application/json',
            buffer: Buffer.from(
              JSON.stringify({
                lessonIds: [String(journalSeed.lessonId)],
                reason: 'OTHER',
                comment: 'Синтетическое заявление ожидает решения',
              }),
            ),
          },
        },
      });
      expect(submitted.status()).toBe(200);
      const pending = await submitted.json();
      pendingId = pending.summary.id;
      expect(typeof pendingId).toBe('string');
      expect(pending.summary.status).toBe('PENDING');

      await page.getByRole('button', { name: 'Назад', exact: true }).click();
      await page.getByRole('button', { name: 'Итоги', exact: true }).click();
      await expect(
        page.getByRole('status').filter({ hasText: 'У тебя одна группа — сравнивать не с чем.' }),
      ).toBeVisible();
      await expect(page.locator('.teacher-stats__card')).toHaveCount(0);
      await page.getByRole('button', { name: 'Перейти к студентам', exact: true }).click();
      await page.getByRole('button', { name: 'По студентам', exact: true }).click();
      await page.getByRole('button', { name: 'Группа', exact: true }).click();
      await page.getByRole('option', { name: groupName, exact: true }).click();
      await page.getByRole('button', { name: 'Предмет', exact: true }).click();
      await page.getByRole('option', { name: dataset.subject.name, exact: true }).click();
      const statsRead = (sort: string) =>
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/v1/teacher/stats' &&
            url.searchParams.get('semesterId') === String(dataset.semester.id) &&
            url.searchParams.get('groupId') === String(dataset.group.id) &&
            url.searchParams.get('subjectId') === String(dataset.subject.id) &&
            url.searchParams.get('scope') === 'students' &&
            url.searchParams
              .getAll('lessonType')
              .map((type) => type.toUpperCase())
              .join() === 'LECTURE' &&
            url.searchParams.getAll('sort').join() === sort
          );
        });
      const [statsResponse] = await Promise.all([
        statsRead('present'),
        page.getByRole('button', { name: 'Лекция', exact: true }).click(),
      ]);
      expect(statsResponse.status()).toBe(200);
      const stats = await statsResponse.json();
      expect(stats.lessonsCount).toBe(1);
      expect(stats.students).toHaveLength(expectedNames.length);
      expect(
        stats.students
          .map((row: { displayName: string }) => row.displayName)
          .sort((a: string, b: string) => a.localeCompare(b, 'ru')),
      ).toEqual(expectedNames);
      await expect(
        page.getByRole('heading', { name: 'Студенты · Пар учтено: 1', exact: true }),
      ).toBeVisible();
      await expect(page.getByRole('button', { name: 'Сортировка', exact: true })).toContainText(
        'Присутствия: сначала ниже',
      );
      const cards = page.locator('.teacher-stats__card');
      for (const row of stats.students) {
        const isAbsent = row.studentId === String(dataset.assistant.id);
        expect(row.present).toEqual({ numerator: 0, denominator: 1, percent: 0 });
        expect(row.presentOrExcused).toEqual({
          numerator: isAbsent ? 0 : 1,
          denominator: 1,
          percent: isAbsent ? 0 : 100,
        });
        expect(row.excused).toEqual(row.presentOrExcused);
        expect(row.absent).toEqual({
          numerator: isAbsent ? 1 : 0,
          denominator: 1,
          percent: isAbsent ? 100 : 0,
        });
        const card = cards.filter({
          has: page.getByRole('heading', { name: row.displayName, exact: true }),
        });
        for (const [index, metric] of [
          row.present,
          row.presentOrExcused,
          row.excused,
          row.absent,
        ].entries()) {
          await expect(card.locator('dd').nth(index)).toHaveText(
            `${metric.percent} % · ${metric.numerator}/${metric.denominator}`,
          );
        }
      }
      await page.getByRole('button', { name: 'Сортировка', exact: true }).click();
      const [sortedResponse] = await Promise.all([
        statsRead('-absent'),
        page.getByRole('option', { name: 'Отсутствия: сначала выше', exact: true }).click(),
      ]);
      expect(sortedResponse.status()).toBe(200);
      const sorted = await sortedResponse.json();
      expect(sorted.students[0].studentId).toBe(String(dataset.assistant.id));
      expect(
        sorted.students.map((row: { absent: { percent: number } }) => row.absent.percent),
      ).toEqual([100, 0, 0]);
      await expect(cards.getByRole('heading')).toHaveText(
        sorted.students.map((row: { displayName: string }) => row.displayName),
      );

      await page.getByRole('button', { name: 'Открыть журнал', exact: true }).click();
      const [pendingJournal] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/v1/teacher/journal' &&
            response.request().method() === 'GET',
        ),
        page
          .locator('.teacher-journal')
          .getByRole('button', { name: 'Лекция', exact: true })
          .click(),
      ]);
      expect(pendingJournal.status()).toBe(200);
      const pendingCell = (await pendingJournal.json()).students
        .find((row: { studentId: string }) => row.studentId === String(dataset.assistant.id))
        .cells.find((cell: { lessonId: string }) => cell.lessonId === String(journalSeed.lessonId));
      expect(pendingCell).toMatchObject({
        status: 'absent',
        autoAbsent: true,
        pendingTicket: true,
        ticketId: pendingId,
      });
      const pendingMark = assistantRow.getByLabel('Отсутствовал; заявление ожидает решения', {
        exact: true,
      });
      await expect(pendingMark).toHaveAttribute('data-auto-absent', 'true');
      await expect(pendingMark).toContainText('Ожидает решения');
      expect(await pendingMark.evaluate((element) => element.tagName)).toBe('SPAN');
      await expect(assistantRow.getByRole('button')).toHaveCount(0);
      const cancelledRequest = await journalSeed.assistantActor.call(
        'POST',
        `/api/v1/student/requests/${pendingId}/cancel`,
      );
      expect(cancelledRequest.summary.status).toBe('CANCELLED');
      pendingId = undefined;

      const ownPastLessons = await dataset.headmanActor.call(
        'GET',
        `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${journalSeed.date}&dateTo=${journalSeed.date}&size=100`,
      );
      const pastBefore = (
        ownPastLessons._embedded?.lessonResponseList ?? ownPastLessons.content
      ).find((lesson: { id: number }) => Number(lesson.id) === journalSeed.lessonId);
      expect(pastBefore).toMatchObject({
        id: journalSeed.lessonId,
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        status: 'CLOSED',
        current: true,
      });
      expect(pastBefore.revision).toBeGreaterThan(0);
      pastCancelAttempted = true;
      // The native cancellation DTO is reason-only; the server locks and validates current ownership.
      const pastCancelled = await dataset.headmanActor.call(
        'PATCH',
        `/api/schedule/lessons/${journalSeed.lessonId}/cancel`,
        { reason: 'Синтетическая отмена прошедшей пары' },
      );
      expect(pastCancelled).toMatchObject({ id: journalSeed.lessonId, status: 'CANCELLED' });
      expect(pastCancelled.revision).toBeGreaterThan(pastBefore.revision);
      await page
        .locator('.teacher-journal')
        .getByRole('button', { name: 'Лекция', exact: true })
        .click();
      const [cancelledJournal] = await Promise.all([
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/v1/teacher/journal' &&
            url.searchParams.get('semesterId') === String(dataset.semester.id) &&
            url.searchParams.get('groupId') === String(dataset.group.id) &&
            url.searchParams.get('subjectId') === String(dataset.subject.id) &&
            url.searchParams.get('pastOnly') === 'true'
          );
        }),
        page
          .locator('.teacher-journal')
          .getByRole('button', { name: 'Лекция', exact: true })
          .click(),
      ]);
      expect(cancelledJournal.status()).toBe(200);
      const cancelledJournalData = await cancelledJournal.json();
      expect(cancelledJournalData.lessons).toHaveLength(1);
      expect(cancelledJournalData.lessons[0]).toMatchObject({
        id: String(journalSeed.lessonId),
        status: 'cancelled',
        cancelled: true,
        date: journalSeed.date,
      });
      await expect(table.locator('thead th[data-cancelled="true"]')).toContainText('Отменена');
      await expect(table.locator('tbody th[scope="row"]')).toHaveText(expectedNames);
      await expect(table.getByLabel('Отменена', { exact: true })).toHaveCount(expectedNames.length);
      await expect(table.getByRole('button')).toHaveCount(0);
      for (const metric of ['Присутствия', 'Уважительные причины', 'Отсутствия']) {
        await expect(table.getByLabel(`${metric}: 0 из 0`, { exact: true })).toHaveCount(
          expectedNames.length,
        );
      }
      await expect(table.getByLabel('Отсутствовал', { exact: true })).toHaveCount(0);

      const contrast = await dataset.headmanActor.call(
        'POST',
        '/api/schedule/one-off-lessons',
        {
          groupId: dataset.group.id,
          subjectId: dataset.subject.id,
          assignmentId: dataset.subject.assignments[0].id,
          date: dataset.tomorrow,
          lessonNumber: 2,
          classroom: 'Синтетическая следующая пара',
        },
        [201, 200],
        { 'Idempotency-Key': randomUUID() },
      );
      contrastLessonId = Number(contrast.physicalLessonId);
      originalCancelAttempted = true;
      await dataset.headmanActor.call(
        'PATCH',
        `/api/schedule/lessons/${future.physicalLessonId}/cancel`,
        { reason: 'Синтетическая отмена для просмотра преподавателем' },
      );
      await page.getByRole('button', { name: 'Назад', exact: true }).click();
      await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
      const dayRead = (date: string) =>
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/v1/teacher/day' &&
            url.searchParams.get('date') === date &&
            url.searchParams.get('semesterId') === String(dataset.semester.id)
          );
        });
      const [tomorrowResponse] = await Promise.all([
        dayRead(dataset.tomorrow),
        page.getByRole('button', { name: 'Следующий день', exact: true }).click(),
      ]);
      expect(tomorrowResponse.status()).toBe(200);
      const tomorrow = await tomorrowResponse.json();
      expect(tomorrow.date).toBe(dataset.tomorrow);
      expect(tomorrow.lessons.map((lesson: { id: string }) => lesson.id)).toEqual([
        String(future.physicalLessonId),
        String(contrastLessonId),
      ]);
      expect(tomorrow.lessons[0].cancelled).toBe(true);
      expect(tomorrow.lessons[1].cancelled).toBe(false);
      expect(tomorrow.lessons[0].startsAt < tomorrow.lessons[1].startsAt).toBe(true);
      const home = page.getByRole('main', { name: 'Сегодня у преподавателя', exact: true });
      await expect(home.locator('[data-lesson-id]')).toHaveCount(2);
      expect(
        await home
          .locator('[data-lesson-id]')
          .evaluateAll((elements) =>
            elements.map((element) => element.getAttribute('data-lesson-id')),
          ),
      ).toEqual([String(future.physicalLessonId), String(contrastLessonId)]);
      const cancelledCard = home.locator(`article[data-lesson-id="${future.physicalLessonId}"]`);
      await expect(cancelledCard).toContainText('Отменена');
      await expect(cancelledCard).toContainText(
        'Синтетическая отмена для просмотра преподавателем',
      );
      await expect(cancelledCard).not.toHaveAttribute('data-teacher-action');
      await expect(cancelledCard.getByRole('button')).toHaveCount(0);
      await cancelledCard.click();
      await expect(home).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Пара', exact: true })).toHaveCount(0);
      await expect(
        home.locator(`[data-teacher-action="lesson-open"][data-lesson-id="${contrastLessonId}"]`),
      ).toBeVisible();
      const [todayAgain] = await Promise.all([
        dayRead(dataset.today),
        page.getByRole('button', { name: 'Предыдущий день', exact: true }).click(),
      ]);
      expect(todayAgain.status()).toBe(200);
      expect((await todayAgain.json()).lessons.map((lesson: { id: string }) => lesson.id)).toEqual([
        String(id),
      ]);
      const [yesterdayResponse] = await Promise.all([
        dayRead(journalSeed.date),
        page.getByRole('button', { name: 'Предыдущий день', exact: true }).click(),
      ]);
      expect(yesterdayResponse.status()).toBe(200);
      expect(
        (await yesterdayResponse.json()).lessons.map((lesson: { id: string }) => lesson.id),
      ).toEqual([String(journalSeed.lessonId)]);
      await expect(home.locator(`[data-lesson-id="${journalSeed.lessonId}"]`)).toBeVisible();

      // An owned unscheduled subject exposes the accepted subject/type shortcut without changing dates.
      const shortcutName = `Teacher shortcut ${randomUUID()}`;
      const shortcutSubject = await dataset.headmanActor.call(
        'POST',
        '/api/academic/subjects',
        {
          name: shortcutName,
          type: 'LECTURE',
          lessonTypes: ['LECTURE'],
          initialAssignments: [
            {
              teacherId: dataset.teacher.id,
              semesterId: dataset.semester.id,
              lessonType: 'LECTURE',
              validFrom: dataset.semester.dateFrom,
              validUntilExclusive: null,
            },
          ],
        },
        [201],
      );
      shortcutSubjectId = Number(shortcutSubject.id);
      expect(Number.isSafeInteger(shortcutSubjectId) && shortcutSubjectId > 0).toBe(true);
      await expect
        .poll(
          async () => {
            const response = await page.request.get(
              `/api/v1/teacher/catalog?semesterId=${dataset.semester.id}`,
              { headers },
            );
            try {
              if (response.status() !== 200)
                throw datasetHttpFailure('GET', '/api/v1/teacher/catalog', response.status());
              const current = await response.json();
              return current.taughtSubjectOptions.some(
                (option: { groupId: string; subjectId: string; lessonTypes: string[] }) =>
                  option.groupId === String(dataset.group.id) &&
                  option.subjectId === String(shortcutSubjectId) &&
                  option.lessonTypes.some((type) => type.toUpperCase() === 'LECTURE'),
              );
            } finally {
              await response.dispose();
            }
          },
          { timeout: 5000, message: 'Owned shortcut assignment becomes authoritative' },
        )
        .toBe(true);
      const [freshCatalog] = await Promise.all([
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/v1/teacher/catalog' &&
            url.searchParams.get('semesterId') === String(dataset.semester.id)
          );
        }),
        page.reload(),
      ]);
      expect(freshCatalog.status()).toBe(200);
      const shortcut = page
        .locator('[data-teacher-action="journal-open"]')
        .filter({ hasText: shortcutName });
      await expect(shortcut).toHaveCount(1);
      await expect(shortcut).toContainText(groupName);
      await expect(shortcut).toContainText('Лекция');
      const [shortcutResponse] = await Promise.all([
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/v1/teacher/journal' &&
            url.searchParams.get('semesterId') === String(dataset.semester.id) &&
            url.searchParams.get('groupId') === String(dataset.group.id) &&
            url.searchParams.get('subjectId') === String(shortcutSubjectId) &&
            url.searchParams
              .getAll('lessonType')
              .map((type) => type.toUpperCase())
              .join() === 'LECTURE' &&
            url.searchParams.get('pastOnly') === 'true' &&
            url.searchParams.get('page') === '0' &&
            url.searchParams.get('pageSize') === '100'
          );
        }),
        shortcut.click(),
      ]);
      expect(shortcutResponse.status()).toBe(200);
      const shortcutJournal = await shortcutResponse.json();
      expect(shortcutJournal.lessons).toEqual([]);
      expect(shortcutJournal.students).toEqual([]);
      await expect(page.locator('.teacher-journal')).toBeVisible();
      await expect(
        page.locator('.teacher-journal').getByRole('button', { name: 'Лекция', exact: true }),
      ).toHaveAttribute('aria-pressed', 'true');
    } finally {
      const cleanup = await Promise.allSettled([
        ...(shortcutSubjectId
          ? [
              (async () => {
                const subject = await dataset.headmanActor.call(
                  'GET',
                  `/api/academic/subjects/${shortcutSubjectId}`,
                );
                const assignment = subject.assignments.find(
                  (row: { teacherId: number; semesterId: number; lessonType: string }) =>
                    Number(row.teacherId) === dataset.teacher.id &&
                    Number(row.semesterId) === dataset.semester.id &&
                    row.lessonType.toUpperCase() === 'LECTURE',
                );
                expect(
                  Number.isSafeInteger(Number(assignment?.id)) && Number(assignment.id) > 0,
                ).toBe(true);
                const closePath = `/api/academic/subjects/${shortcutSubjectId}/teachers/${dataset.teacher.id}?assignmentId=${assignment.id}&validUntilExclusive=${dataset.today}`;
                await expect
                  .poll(
                    async () => {
                      const closed = await dataset.request.delete(closePath, {
                        headers: {
                          ...dataset.headmanActor.headers(),
                          'Idempotency-Key': shortcutCloseKey,
                        },
                      });
                      try {
                        if (![204, 503].includes(closed.status()))
                          throw datasetHttpFailure('DELETE', closePath, closed.status());
                        return closed.status();
                      } finally {
                        await closed.dispose();
                      }
                    },
                    { timeout: 5000, message: 'Owned shortcut assignment closure completes' },
                  )
                  .toBe(204);
                const restoredScope = await page.request.get(
                  `/api/v1/teacher/catalog?semesterId=${dataset.semester.id}`,
                  { headers },
                );
                try {
                  expect(restoredScope.status()).toBe(200);
                  const current = await restoredScope.json();
                  expect(currentSubjectScope(current)).toEqual(currentSubjectScope(catalog));
                  expect(current.groups.map((row: { id: string }) => row.id)).toEqual(
                    catalog.groups.map((row: { id: string }) => row.id),
                  );
                } finally {
                  await restoredScope.dispose();
                }
                // Retain the closed assignment and empty synthetic subject with their immutable history.
              })(),
            ]
          : []),
        ...(pendingId
          ? [
              journalSeed.assistantActor.call(
                'POST',
                `/api/v1/student/requests/${pendingId}/cancel`,
              ),
            ]
          : []),
        ...(originalCancelAttempted
          ? [
              dataset.headmanActor.call(
                'PATCH',
                `/api/schedule/lessons/${future.physicalLessonId}/restore`,
              ),
            ]
          : []),
        ...(contrastLessonId
          ? [
              dataset.headmanActor.call(
                'PATCH',
                `/api/schedule/lessons/${contrastLessonId}/cancel`,
                { reason: 'Синтетическая завершённая проверка дня' },
              ),
            ]
          : []),
      ]);
      // Restoration keeps physical identity and history; cancelled attendance/tickets are not resurrected.
      if (pastCancelAttempted) {
        try {
          const restored = await dataset.headmanActor.call(
            'PATCH',
            `/api/schedule/lessons/${journalSeed.lessonId}/restore`,
          );
          expect(restored).toMatchObject({
            id: journalSeed.lessonId,
            groupId: dataset.group.id,
            subjectId: dataset.subject.id,
            current: true,
          });
          expect(restored.status).not.toBe('CANCELLED');
          await expect
            .poll(
              async () => {
                const report = await dataset.headmanActor.call(
                  'GET',
                  `/api/attendance/reports/lesson/${journalSeed.lessonId}`,
                );
                return [dataset.student.id, dataset.headman.id, dataset.assistant.id].every(
                  (studentId) => {
                    const row = report.entries.find(
                      (entry: { userId: number }) => Number(entry.userId) === studentId,
                    );
                    return (
                      row &&
                      !['present', 'excused'].includes(row.status) &&
                      row.excuseType == null &&
                      row.attachmentId == null
                    );
                  },
                );
              },
              {
                timeout: 5000,
                message: 'Owned cancelled marks are not resurrected by physical restore',
              },
            )
            .toBe(true);
        } catch {
          expect
            .soft(false, 'Owned past lesson physical restore and no-resurrection check completed')
            .toBe(true);
        }
      }
      expect
        .soft(
          cleanup.every((result) => result.status === 'fulfilled'),
          'Owned teacher witness cleanup completed',
        )
        .toBe(true);
    }
  });

  test('STUDENT helper marks with its capability and revocation denies the same effect', async ({
    page,
    dataset,
  }) => {
    const { accessToken, session } = await loginPwa(page, dataset.assistant);
    expect(session.activeRole).toBe('STUDENT');
    expect(session.roles.some((grant) => grant.role === 'HEADMAN' && grant.selectable)).toBe(false);
    const before = await page.request.get('/api/academic/assistants/me/permissions', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(before.status()).toBe(200);
    expect(assistantPermissionCodes(await before.json())).toContain('MARK_ATTENDANCE');
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Действия помощника', exact: true }).click();
    await page.getByRole('button', { name: 'Посещаемость и журнал', exact: true }).click();
    await expect(
      page.getByRole('button', { name: `Был: ${dataset.assistant.displayName}`, exact: true }),
    ).toBeEnabled();
    const markPath = `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`;
    const marked = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === markPath && response.request().method() === 'PUT',
    );
    await page
      .getByRole('button', { name: `Был: ${dataset.assistant.displayName}`, exact: true })
      .click();
    expect((await marked).status()).toBe(200);
    await expect(
      page.getByRole('button', {
        name: `Был: ${dataset.assistant.displayName}. Открыть выбор отметки`,
        exact: true,
      }),
    ).toBeVisible();
    const reportPath = `/api/attendance/reports/lesson/${dataset.lesson.physicalLessonId}`;
    const report = await dataset.headmanActor.call('GET', reportPath);
    const entry = report.entries.find(
      (row: { userId: number }) => Number(row.userId) === dataset.assistant.id,
    );
    expect(String(entry.status).toUpperCase()).toBe('PRESENT');
    expect(typeof entry.markRevision).toBe('string');
    await dataset.headmanActor.call(
      'DELETE',
      `/api/academic/assistants/${dataset.assistantGrant.id}`,
      undefined,
      [204],
    );
    const denied = await dataset.request.put(markPath, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { status: 'ABSENT', expectedStatus: 'PRESENT', expectedRevision: entry.markRevision },
    });
    expect(denied.status()).toBe(403);
    const after = await dataset.headmanActor.call('GET', reportPath);
    expect(
      String(
        after.entries.find((row: { userId: number }) => Number(row.userId) === dataset.assistant.id)
          .status,
      ).toUpperCase(),
    ).toBe('PRESENT');
    await page.reload();
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Действия помощника', exact: true })).toHaveCount(
      0,
    );
    const stale = await page.request.get('/api/academic/assistants/me/permissions', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(stale.status()).toBe(200);
    expect(assistantPermissionCodes(await stale.json())).toEqual([]);
  });

  test('LIMITED ADMIN views accounts while creation is denied by UI and server', async ({
    page,
    dataset,
  }) => {
    await loginPwa(page, dataset.limitedAdmin);
    await selectPwaRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await expect(
      page.getByRole('searchbox', { name: 'Найти пользователя', exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Создать пользователя', exact: true }),
    ).toHaveCount(0);
    const limited = await admit(dataset.request, dataset.limitedAdmin, 'ADMIN');
    const denied = await dataset.request.post('/api/academic/users', {
      headers: limited.headers(),
      data: {
        lastName: 'Запрещённый',
        firstName: 'Синтетика',
        role: 'STUDENT',
        groupId: dataset.group.id,
        telegramId: 999999999999,
      },
    });
    expect(denied.status()).toBe(403);
  });

  test('empty foreign-group homework and transport failure provide a real retry path', async ({
    page,
    dataset,
  }) => {
    const emptyRead = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/v1/student/homework' &&
        response.request().method() === 'GET',
    );
    await loginPwa(page, dataset.foreignStudent);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    expect((await emptyRead).status()).toBe(200);
    await expect(page.getByText('На этот день заданий нет.', { exact: true })).toBeVisible();
    await expect(page.locator('.homework-card')).toHaveCount(0);
    await logoutPwa(page);
    // The owner loads homework during login and retries failed reads automatically.
    // Keep the real transport unavailable until its manual-retry state is visible.
    const homeworkRoute = (url: URL) => url.pathname === '/api/v1/student/homework';
    const abortHomework: Parameters<typeof page.route>[1] = (route) =>
      route.request().method() === 'GET' ? route.abort('failed') : route.continue();
    await page.route(homeworkRoute, abortHomework);
    try {
      const failed = page.waitForEvent(
        'requestfailed',
        (request) =>
          new URL(request.url()).pathname === '/api/v1/student/homework' &&
          request.method() === 'GET',
      );
      await Promise.all([failed, loginPwa(page, dataset.student)]);
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByRole('button', { name: 'Повторить', exact: true })).toBeVisible();
    } finally {
      await page.unroute(homeworkRoute, abortHomework);
    }
    const [retried] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/v1/student/homework' &&
          response.request().method() === 'GET',
      ),
      page.getByRole('button', { name: 'Повторить', exact: true }).click(),
    ]);
    expect(retried.status()).toBe(200);
    await selectTomorrowHomework(page, dataset);
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
  });

  test('another live session revokes the browser session and recovery exposes login only', async ({
    page,
    dataset,
  }) => {
    const { accessToken, session } = await loginPwa(page, dataset.foreignStudent);
    const other = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
    await other.call('DELETE', `/api/auth/sessions/${session.sessionId}`, undefined, [204]);
    await page.reload();
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    const denied = await page.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(denied.status()).toBe(401);
  });

  it('D003 reuse admits twice; suspended grant yields neutral bootstrap and protected denials', async ({
    page,
    dataset,
    teacherGroupsSeed: secondGroup,
  }) => {
    const proof = syntheticInitData(dataset.teacher.telegramId);
    await installTelegramHost(page, proof, { observeDownloads: true });
    const lessonPath = `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`;
    const launch = Buffer.from(
      JSON.stringify({
        v: 2,
        action: {
          kind: 'lesson',
          role: 'TEACHER',
          lessonId: String(dataset.lesson.physicalLessonId),
        },
      }),
    ).toString('base64url');
    expect(launch.length <= 384).toBe(true);
    let activeToken = '';
    for (let attempt = 0; attempt < 2; attempt++) {
      const target =
        attempt === 1
          ? page.waitForResponse(
              (response) =>
                new URL(response.url()).pathname === lessonPath &&
                response.request().method() === 'GET',
            )
          : null;
      const admission = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/tma' &&
          response.request().method() === 'POST',
      );
      const current = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      );
      await page.goto(attempt === 1 ? `/mini-app/?tgWebAppStartParam=n2_${launch}` : '/mini-app/');
      const authenticated = await admission;
      expect(authenticated.status()).toBe(200);
      const { accessToken } = await authenticated.json();
      expect(typeof accessToken === 'string' && accessToken.length > 0).toBe(true);
      activeToken = accessToken;
      const actual = await current;
      expect(actual.status()).toBe(200);
      const session = await actual.json();
      expect(String(session.userId)).toBe(String(dataset.teacher.id));
      expect(session.activeRole).toBe('TEACHER');
      await expect(page.locator('.teacher-feature-owner')).toBeVisible();
      expect(
        await page.evaluate(
          ({ token, proof }) => {
            const stored = JSON.stringify([
              Object.entries(localStorage),
              Object.entries(sessionStorage),
            ]);
            return !stored.includes(token) && !stored.includes(proof);
          },
          { token: accessToken, proof },
        ),
      ).toBe(true);
      if (target) {
        expect((await target).status()).toBe(200);
        await expect(page.getByRole('heading', { name: 'Пара', exact: true })).toBeVisible();
      }
    }

    // D015/H1: the host is synthetic; admission, target authorization and the
    // selected HTML job/ticket/file are genuine. Observe only the native API
    // handoff in RAM, never claim that Telegram or the OS saved a file.
    // Job/ticket expiry follows real security time, independently of teaching dates.
    await page.clock.setFixedTime(new Date());
    await page.getByRole('button', { name: 'Скачать посещаемость этой пары', exact: true }).click();
    const created = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/export-jobs' &&
        response.request().method() === 'POST',
    );
    const issued = page.waitForResponse(
      (response) =>
        /^\/api\/auth\/export-jobs\/[0-9a-f-]+\/ticket$/.test(new URL(response.url()).pathname) &&
        response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'HTML (.html)', exact: true }).click();
    const jobResponse = await created;
    expect(jobResponse.status()).toBe(202);
    const report = jobResponse.request().postDataJSON().report;
    expect(
      report?.kind === 'TEACHER_JOURNAL' &&
        report.teacherJournal?.lessonId === String(dataset.lesson.physicalLessonId) &&
        report.teacherJournal.groupId === dataset.group.id &&
        report.teacherJournal.format === 'html',
    ).toBe(true);
    const jobId: unknown = (await jobResponse.json()).jobId;
    expect(
      typeof jobId === 'string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(jobId),
    ).toBe(true);
    const ticketResponse = await issued;
    expect(ticketResponse.status()).toBe(200);
    expect(new URL(ticketResponse.url()).pathname === `/api/auth/export-jobs/${jobId}/ticket`).toBe(
      true,
    );
    const ticket = await ticketResponse.json();
    expect(
      typeof ticket.downloadPath === 'string' &&
        /^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(ticket.downloadPath),
    ).toBe(true);
    await expect(
      page.getByRole('status').filter({
        hasText: 'Telegram принял запрос на скачивание; проверь завершение в Telegram.',
      }),
    ).toBeVisible();
    const downloads = await page.evaluate(
      () =>
        (
          window as unknown as {
            __tmaEntryState: { downloads: { url: string; file_name: string }[] };
          }
        ).__tmaEntryState.downloads,
    );
    expect(downloads.length).toBe(1);
    const download = downloads[0]!;
    expect(
      new URL(download.url).protocol === 'https:' &&
        download.url === new URL(ticket.downloadPath, new URL(page.url()).origin).href,
    ).toBe(true);
    expect(
      download.file_name === ticket.suggestedFilename &&
        /^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/.test(download.file_name) &&
        download.file_name.endsWith('.html'),
    ).toBe(true);
    const job = await page.request.get(`/api/auth/export-jobs/${jobId}`, {
      headers: { Authorization: `Bearer ${activeToken}` },
    });
    try {
      expect(job.status()).toBe(200);
      const currentJob = await job.json();
      expect(currentJob.jobId === jobId && currentJob.state === 'ready').toBe(true);
    } finally {
      await job.dispose();
    }
    const file = await page.request.get(download.url);
    try {
      if (file.status() !== 200)
        throw datasetHttpFailure('GET', ticket.downloadPath, file.status());
      expect(file.headers()['content-type']?.split(';')[0] === 'text/html').toBe(true);
      const content = await file.text();
      expect(/<!doctype html/i.test(content) && /<table\b/i.test(content)).toBe(true);
    } finally {
      await file.dispose();
    }
    // D023/JS-TEACHER-02: same genuine TMA owner, two currently authorized groups.
    for (const student of [dataset.student, dataset.headman, dataset.assistant]) {
      await expect(page.locator('.teacher-roster')).toContainText(student.displayName);
    }
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await page.getByRole('button', { name: 'Итоги', exact: true }).click();
    const groupsRead = (sort: string) =>
      page.waitForResponse((response) => {
        const url = new URL(response.url());
        return (
          response.request().method() === 'GET' &&
          url.pathname === '/api/v1/teacher/stats' &&
          url.searchParams.get('semesterId') === String(dataset.semester.id) &&
          url.searchParams.get('scope') === 'groups' &&
          !url.searchParams.has('groupId') &&
          !url.searchParams.has('subjectId') &&
          url.searchParams.getAll('lessonType').join().toUpperCase() === 'LECTURE' &&
          url.searchParams.getAll('sort').join() === sort
        );
      });
    const [groupsResponse] = await Promise.all([
      groupsRead('present'),
      page.getByRole('button', { name: 'Лекция', exact: true }).click(),
    ]);
    expect(groupsResponse.status()).toBe(200);
    const groupsStats = await groupsResponse.json();
    expect(groupsStats.groups.map((row: { groupId: string }) => row.groupId).sort()).toEqual(
      [String(dataset.group.id), String(secondGroup.groupId)].sort(),
    );
    expect(
      groupsStats.groups.some(
        (row: { groupId: string }) => row.groupId === String(dataset.otherGroup.id),
      ),
    ).toBe(false);
    expect(
      groupsStats.groups.find(
        (row: { groupId: string }) => row.groupId === String(secondGroup.groupId),
      ),
    ).toMatchObject({ lessonsCount: 1, absent: { numerator: 1, denominator: 1, percent: 100 } });
    const cards = page.locator('.teacher-stats__card');
    const assertGroupMetrics = async (data: typeof groupsStats) => {
      await expect(cards.locator('.teacher-stats__row-name')).toHaveText(
        data.groups.map((row: { groupName: string }) => row.groupName),
      );
      await expect(cards.locator('.teacher-stats__lesson-count')).toHaveText(
        data.groups.map((row: { lessonsCount: number }) => `${row.lessonsCount} пар`),
      );
      for (const row of data.groups) {
        const card = cards.filter({ has: page.getByText(row.groupName, { exact: true }) });
        for (const [key, label] of [
          ['present', 'Присутствия'],
          ['presentOrExcused', 'Присутствия и уважительные причины'],
          ['excused', 'Уважительные причины'],
          ['absent', 'Отсутствия'],
        ] as const) {
          const metric = row[key];
          await expect(
            card
              .locator('dl > div')
              .filter({ has: page.getByLabel(label, { exact: true }) })
              .locator('dd'),
          ).toHaveText(
            metric.denominator === 0
              ? 'нет данных'
              : `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format(metric.percent)} % · ${metric.numerator}/${metric.denominator}`,
          );
        }
      }
    };
    await assertGroupMetrics(groupsStats);
    await page.getByRole('button', { name: 'Сортировка', exact: true }).click();
    const [sortedGroups] = await Promise.all([
      groupsRead('-absent'),
      page.getByRole('option', { name: 'Отсутствия: сначала выше', exact: true }).click(),
    ]);
    expect(sortedGroups.status()).toBe(200);
    await assertGroupMetrics(await sortedGroups.json());
    for (const selected of [
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        groupName: groupsStats.groups.find(
          (row: { groupId: string }) => row.groupId === String(dataset.group.id),
        ).groupName,
        studentIds: [dataset.student.id, dataset.headman.id, dataset.assistant.id],
        lessonId: null,
      },
      { ...secondGroup, studentIds: [secondGroup.headmanId] },
    ]) {
      const card = cards.filter({ has: page.getByText(selected.groupName, { exact: true }) });
      await card.getByRole('button').click();
      await expect(page.getByRole('button', { name: 'Предмет', exact: true })).toContainText(
        'Выбери предмет',
      );
      await expect(page.locator('.teacher-stats__card')).toHaveCount(0);
      await page.getByRole('button', { name: 'Предмет', exact: true }).click();
      await page.getByRole('option', { name: dataset.subject.name, exact: true }).click();
      const selectedRead = (pathname: string) =>
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === pathname &&
            url.searchParams.get('semesterId') === String(dataset.semester.id) &&
            url.searchParams.get('groupId') === String(selected.groupId) &&
            url.searchParams.get('subjectId') === String(selected.subjectId)
          );
        });
      const [studentResponse] = await Promise.all([
        selectedRead('/api/v1/teacher/stats'),
        page.getByRole('button', { name: 'Лекция', exact: true }).click(),
      ]);
      expect(studentResponse.status()).toBe(200);
      const studentStats = await studentResponse.json();
      expect(studentStats.scope).toBe('students');
      if (studentStats.lessonsCount > 0)
        expect(
          studentStats.students.map((row: { studentId: string }) => row.studentId).sort(),
        ).toEqual(selected.studentIds.map(String).sort());
      else expect(selected.groupId).toBe(dataset.group.id);
      await page.getByRole('button', { name: 'Открыть журнал', exact: true }).click();
      const [journalResponse] = await Promise.all([
        selectedRead('/api/v1/teacher/journal'),
        page
          .locator('.teacher-journal')
          .getByRole('button', { name: 'Лекция', exact: true })
          .click(),
      ]);
      expect(journalResponse.status()).toBe(200);
      const journal = await journalResponse.json();
      expect(
        journal.lessons.every(
          (row: { groupId: string; subjectId: string }) =>
            row.groupId === String(selected.groupId) &&
            row.subjectId === String(selected.subjectId),
        ),
      ).toBe(true);
      if (selected.lessonId != null) {
        expect(journal.lessons.map((row: { id: string }) => row.id)).toEqual([
          String(selected.lessonId),
        ]);
        expect(journal.students.map((row: { studentId: string }) => row.studentId)).toEqual(
          selected.studentIds.map(String),
        );
        expect(journal.students[0].cells[0]).toMatchObject({ status: 'absent', autoAbsent: true });
        await expect(page.locator('.teacher-journal__table tbody th[scope="row"]')).toHaveText([
          secondGroup.headmanName,
        ]);
      } else if (journal.lessons.length === 0) {
        expect(journal.students).toEqual([]);
        await expect(
          page
            .getByRole('status')
            .filter({ hasText: 'В выбранном периоде прошедших занятий нет.' }),
        ).toBeVisible();
      } else {
        expect(journal.students.map((row: { studentId: string }) => row.studentId).sort()).toEqual(
          selected.studentIds.map(String).sort(),
        );
      }
      await page.getByRole('button', { name: 'Назад', exact: true }).click();
      await page.getByRole('button', { name: 'По моим группам', exact: true }).click();
      await expect(cards).toHaveCount(2);
    }
    await dataset.admin.call('PUT', `/api/academic/users/${dataset.teacher.id}/roles/TEACHER`, {
      status: 'SUSPENDED',
    });
    try {
      const neutralAdmission = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/tma' &&
          response.request().method() === 'POST',
      );
      const current = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      );
      await page.reload();
      const admitted = await neutralAdmission;
      expect(admitted.status()).toBe(200);
      const { accessToken } = await admitted.json();
      const response = await current;
      expect(response.status()).toBe(200);
      const neutral = await response.json();
      expect(neutral.activeRole ?? null).toBeNull();
      expect(
        neutral.roles.some(
          (grant: { role: string; selectable: boolean; status: string }) =>
            grant.role === 'TEACHER' && grant.status === 'SUSPENDED' && !grant.selectable,
        ),
      ).toBe(true);
      await expect(page.getByRole('heading', { name: 'Сменить роль', exact: true })).toBeVisible();
      await expect(page.locator('.teacher-feature-owner')).toHaveCount(0);
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      const headers = { Authorization: `Bearer ${accessToken}` };
      const deniedRole = await page.request.put('/api/auth/session/active-role', {
        headers,
        data: { role: 'TEACHER', expectedSessionVersion: neutral.sessionVersion },
      });
      expect(deniedRole.status()).toBe(403);
      expect((await deniedRole.json()).extras.code).toBe('ROLE_NOT_SELECTABLE');
      const deniedRead = await page.request.get(
        `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`,
        { headers },
      );
      expect(deniedRead.status()).toBe(403);
      expect((await deniedRead.json()).code).toBe('BOOTSTRAP_SCOPE_DENIED');
      const deniedWrite = await page.request.put(
        `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`,
        {
          headers,
          data: { status: 'PRESENT', expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
        },
      );
      expect(deniedWrite.status()).toBe(403);
      expect((await deniedWrite.json()).code).toBe('BOOTSTRAP_SCOPE_DENIED');
    } finally {
      await dataset.admin.call('PUT', `/api/academic/users/${dataset.teacher.id}/roles/TEACHER`, {
        status: 'ACTIVE',
      });
    }
  });
});

test.describe('Signed HEADMAN Telegram entry @real-mobile', () => {
  test('D003 signed HEADMAN TMA marks an isolated own-group roster row', async ({
    page,
    request: issuer,
    activeHeadmanDataset: dataset,
  }) => {
    // The worker owns this disposable group; this case adds its own member and
    // never changes a known student's mark or another browser's selected role.
    const student = await dataset.user('STUDENT', dataset.group.id);
    const reportPath = `/api/attendance/reports/lesson/${dataset.lesson.physicalLessonId}`;
    const before = await dataset.headmanActor.call('GET', reportPath);
    expect(
      before.entries.some(
        (row: { userId: number; status: string | null }) =>
          Number(row.userId) === student.id && row.status === null,
      ),
    ).toBe(true);
    const knownMarks = (
      entries: { userId: number; status: string | null; source: string | null }[],
    ) =>
      entries
        .filter((row) => Number(row.userId) !== student.id)
        .map((row) => ({ userId: Number(row.userId), status: row.status, source: row.source }))
        .sort((left, right) => left.userId - right.userId);

    await installTelegramHost(page, syntheticInitData(dataset.headman.telegramId));
    const [admitted, current] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/tma' &&
          response.request().method() === 'POST',
      ),
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      ),
      page.goto('/mini-app/'),
    ]);
    expect(admitted.status()).toBe(200);
    let sourceAccessToken: string = (await admitted.json()).accessToken;
    expect(current.status()).toBe(200);
    let session = await current.json();
    expect(String(session.userId) === String(dataset.headman.id)).toBe(true);
    expect(
      session.roles.some(
        (grant: { role: string; selectable: boolean }) =>
          grant.role === 'HEADMAN' && grant.selectable,
      ),
    ).toBe(true);
    if (session.activeRole !== 'HEADMAN') {
      // Admission responses arrive before the corresponding Vue owner is ready.
      if ((session.activeRole ?? null) === null) {
        await expect(
          page.getByRole('heading', { name: 'Сменить роль', exact: true }),
        ).toBeVisible();
        await expect(page.getByRole('button', { name: /Староста/ })).toBeEnabled();
      } else {
        await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
      }
      const selected = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session/active-role' &&
          response.request().method() === 'PUT',
      );
      await selectPwaRole(page, 'Староста');
      const response = await selected;
      expect(response.status()).toBe(200);
      const selection = await response.json();
      session = selection.session;
      sourceAccessToken = selection.accessToken;
    }
    expect(
      String(session.userId) === String(dataset.headman.id) && session.activeRole === 'HEADMAN',
    ).toBe(true);
    await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
    const marked = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${student.id}` &&
        response.request().method() === 'PUT',
    );
    await page.getByRole('button', { name: `Был: ${student.displayName}`, exact: true }).click();
    expect((await marked).status()).toBe(200);
    await expect(
      page.locator(`.headman-journal__roster-list [data-user-id="${student.id}"]`),
    ).toHaveAttribute('data-status', 'present');
    const after = await dataset.headmanActor.call('GET', reportPath);
    expect(
      after.entries.some(
        (row: { userId: number; status: string; source: string }) =>
          Number(row.userId) === student.id && row.status === 'present' && row.source === 'headman',
      ),
    ).toBe(true);
    expect(knownMarks(after.entries)).toEqual(knownMarks(before.entries));

    // D024: the same signed mobile owner explicitly rejects a browser LOGIN proof.
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    await page.getByRole('button', { name: 'Вход в браузере', exact: true }).click();
    try {
      const issued = await issuer.post('/api/auth/qr/challenges', { data: { purpose: 'LOGIN' } });
      let challenge;
      try {
        expect(issued.status()).toBe(200);
        challenge = await issued.json();
      } finally {
        await issued.dispose();
      }
      expect(
        typeof challenge.qrPayload === 'string',
        'Owned browser receives an opaque LOGIN proof',
      ).toBe(true);
      await page.getByLabel('Данные QR для входа', { exact: true }).fill(challenge.qrPayload);
      const [preview] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/auth/qr/preview' &&
            response.request().method() === 'POST',
        ),
        page.getByRole('button', { name: 'Проверить вход', exact: true }).click(),
      ]);
      expect(preview.status()).toBe(200);
      const previewed = await preview.json();
      expect(
        previewed.challengeId === challenge.challengeId &&
          previewed.purpose === 'LOGIN' &&
          previewed.status === 'pending',
        'TMA previews the exact pending browser proof',
      ).toBe(true);
      const [decision] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/auth/qr/decision' &&
            response.request().method() === 'POST',
        ),
        page.getByRole('button', { name: 'Отклонить вход', exact: true }).click(),
      ]);
      expect(decision.status()).toBe(200);
      expect((await decision.json()).status === 'rejected').toBe(true);
      await expect(page.getByRole('status')).toHaveText('Вход в браузере отклонён');
      const denied = await issuer.post('/api/auth/qr/exchange', {
        data: {
          purpose: 'LOGIN',
          issuerId: challenge.issuerId,
          issuerSecret: challenge.issuerSecret,
          challengeId: challenge.challengeId,
        },
      });
      try {
        expect(denied.status(), 'Rejected browser cannot exchange the proof').toBe(410);
      } finally {
        await denied.dispose();
      }
      const source = await page.request.get('/api/auth/session', {
        headers: { Authorization: `Bearer ${sourceAccessToken}` },
      });
      try {
        expect(source.status()).toBe(200);
        const currentOwner = await source.json();
        expect(
          String(currentOwner.userId) === String(dataset.headman.id) &&
            currentOwner.sessionId === session.sessionId &&
            currentOwner.activeRole === 'HEADMAN',
          'Original signed owner remains current',
        ).toBe(true);
      } finally {
        await source.dispose();
      }
    } finally {
      await issuer.dispose();
    }
  });
});

test.describe('Production PWA service worker D002 @real-mobile', () => {
  test.use({ serviceWorkers: 'allow' });
  test('production worker update activates through the update gate and retains a warm launch', async ({
    page,
    context,
    dataset,
  }) => {
    test.skip(process.env.RCT_E2E_SW_LIFECYCLE !== 'true', 'Requires the owned CI static fixture');
    test.setTimeout(120000);
    const fixture = productionWorkerFixture();
    const failures: unknown[] = [];
    try {
      await publishOfflineHomework(dataset, dataset.marker);
      await loginPwa(page, dataset.student);
      await requireProductionWorker(page);
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
      const original = await page.evaluate(async () => {
        Object.defineProperty(window, '__rctWorkerUpdateOldWindow', { value: true });
        const registration = await navigator.serviceWorker.ready;
        return {
          scope: registration.scope,
          script: registration.active?.scriptURL,
          active: registration.active?.state,
          waiting: registration.waiting !== null,
          caches: await caches.keys(),
        };
      });
      expect(original.scope).toBe(new URL('/app/', page.url()).href);
      expect(original.script).toBe(new URL('/app/sw.js', page.url()).href);
      expect(original.active).toBe('activated');
      expect(original.waiting).toBe(false);
      expect(original.caches).toContain('rct-student-pwa-v3-0.0.0');
      const preserved = await page.evaluate(async () => {
        const names = [
          'rct-e2e-other-application',
          `workbox-precache-v2-${new URL('/another-app/', location.origin).href}`,
        ];
        for (const name of [
          ...names,
          'rct-student-pwa-retired-fixture',
          'headman-api-cache-v1',
          `workbox-precache-v2-${new URL('/app/', location.origin).href}`,
        ]) {
          const cache = await caches.open(name);
          await cache.put('/app/lifecycle-fixture', new Response('synthetic cache ownership'));
        }
        return names;
      });

      fixture.update();
      await page.evaluate(async () => {
        const registration = await navigator.serviceWorker.ready;
        await registration.update();
      });
      await expect
        .poll(() =>
          page.evaluate(async () => {
            const registration = await navigator.serviceWorker.ready;
            return registration.waiting?.state;
          }),
        )
        .toBe('installed');
      expect(await page.evaluate(() => navigator.serviceWorker.controller?.state)).toBe(
        'activated',
      );
      expect(await page.evaluate(() => caches.keys())).toContain('rct-student-pwa-v3-0.0.0');
      const policyObserver = await page.evaluateHandle(() => {
        const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'fetch');
        if (!descriptor || typeof descriptor.value !== 'function' || !descriptor.configurable) {
          return { read: () => null, restore: () => {} };
        }
        const originalFetch = descriptor.value as typeof fetch;
        let offlineSeen = false;
        let onlineSeen = false;
        let visibleAtOnline = false;
        let outcome: 'NOT_CALLED' | 'PENDING' | 'FULFILLED' | 'REJECTED' = 'NOT_CALLED';
        const offline = () => {
          offlineSeen = true;
        };
        const online = () => {
          if (!onlineSeen) visibleAtOnline = document.visibilityState === 'visible';
          onlineSeen = true;
        };
        const observed: typeof fetch = function (this: unknown, ...args) {
          let firstMatch = false;
          try {
            const [input, init] = args;
            const request = input instanceof globalThis.Request ? input : null;
            const method = init?.method ?? request?.method ?? 'GET';
            const address =
              typeof input === 'string' ? input : input instanceof URL ? input.href : request?.url;
            if (
              outcome === 'NOT_CALLED' &&
              method.toUpperCase() === 'GET' &&
              typeof address === 'string'
            ) {
              const url = new URL(address, location.href);
              if (url.origin === location.origin && url.pathname === '/app/version.json') {
                firstMatch = true;
                outcome = 'PENDING';
              }
            }
          } catch {
            // Only observation may fail; native fetch retains its own result or error.
          }
          const promise = Reflect.apply(originalFetch, this, args) as ReturnType<typeof fetch>;
          if (firstMatch) {
            try {
              void promise
                .then(
                  () => {
                    outcome = 'FULFILLED';
                  },
                  () => {
                    outcome = 'REJECTED';
                  },
                )
                .catch(() => {});
            } catch {
              // Side-observation failure must not replace the original promise.
            }
          }
          return promise;
        };
        Object.defineProperty(globalThis, 'fetch', { ...descriptor, value: observed });
        addEventListener('offline', offline);
        addEventListener('online', online);
        return {
          read: () => ({ offlineSeen, onlineSeen, visibleAtOnline, outcome }),
          restore: () => {
            removeEventListener('offline', offline);
            removeEventListener('online', online);
            Object.defineProperty(globalThis, 'fetch', descriptor);
          },
        };
      });
      let policyObserverRestored = false;
      let policyRead!: ReturnType<Page['waitForResponse']>;
      try {
        policyRead = page.waitForResponse(
          (response) => new URL(response.url()).pathname === '/app/version.json',
        );
        // Genuine network transitions invoke the existing production policy reader.
        await context.setOffline(true);
        await context.setOffline(false);
        try {
          await policyRead;
        } catch (error) {
          const observed = await policyObserver.evaluate((value) => value.read()).catch(() => null);
          if (observed === null) expect.soft(false, 'PWA policy observer UNAVAILABLE').toBe(true);
          else {
            if (!observed.onlineSeen && !observed.offlineSeen)
              expect.soft(false, 'PWA policy NO_OFFLINE_NO_ONLINE').toBe(true);
            else if (!observed.onlineSeen)
              expect.soft(false, 'PWA policy OFFLINE_SEEN_NO_ONLINE').toBe(true);
            else if (!observed.offlineSeen && observed.visibleAtOnline)
              expect.soft(false, 'PWA policy NO_OFFLINE_ONLINE_VISIBLE').toBe(true);
            else if (!observed.offlineSeen)
              expect.soft(false, 'PWA policy NO_OFFLINE_ONLINE_HIDDEN').toBe(true);
            else if (observed.visibleAtOnline)
              expect.soft(false, 'PWA policy OFFLINE_SEEN_ONLINE_VISIBLE').toBe(true);
            else expect.soft(false, 'PWA policy OFFLINE_SEEN_ONLINE_HIDDEN').toBe(true);
            if (observed.outcome === 'NOT_CALLED')
              expect.soft(false, 'PWA policy FETCH_NOT_CALLED').toBe(true);
            else if (observed.outcome === 'PENDING')
              expect.soft(false, 'PWA policy FETCH_PENDING').toBe(true);
            else if (observed.outcome === 'FULFILLED')
              expect.soft(false, 'PWA policy FETCH_FULFILLED').toBe(true);
            else expect.soft(false, 'PWA policy FETCH_REJECTED').toBe(true);
          }
          throw error;
        }
      } finally {
        try {
          await policyObserver.evaluate((value) => value.restore());
          policyObserverRestored = true;
        } catch {
          expect.soft(false, 'PWA policy observer RESTORE_UNAVAILABLE').toBe(true);
        } finally {
          await policyObserver.dispose().catch(() => {});
        }
      }
      expect(policyObserverRestored, 'PWA policy observer NOT_RESTORED').toBe(true);
      expect((await (await policyRead).json()).latest).toBe('0.0.1');
      const gate = page.getByRole('dialog', { name: 'Нужно обновить RutTrack', exact: true });
      await expect(gate).toBeVisible();
      await expect(gate).toContainText('0.0.1');
      // No second JS bundle is claimed: restore its compatible policy before the real UI reload.
      // The existing gate retains required.value until it unmounts on reload.
      fixture.restorePolicy();
      const reloaded = page.waitForEvent('load').then(
        () => true,
        () => false,
      );
      await gate.getByRole('button', { name: 'Обновить приложение', exact: true }).click();
      if (!(await reloaded)) {
        expect.soft(false, 'PWA update LOAD_NOT_OBSERVED').toBe(true);
        const observed = await page
          .evaluate(async () => {
            const registration = await navigator.serviceWorker.getRegistration();
            return {
              oldDocument:
                Object.getOwnPropertyDescriptor(window, '__rctWorkerUpdateOldWindow')?.value ===
                true,
              readyState: document.readyState,
              active: registration?.active?.state === 'activated',
              waiting: registration?.waiting?.state === 'installed',
              updating: Boolean(registration?.installing),
            };
          })
          .catch(() => null);
        if (observed === null) expect.soft(false, 'PWA update OBSERVATION_UNAVAILABLE').toBe(true);
        else {
          if (observed.oldDocument) expect.soft(false, 'PWA update OLD_DOCUMENT').toBe(true);
          else expect.soft(false, 'PWA update NEW_DOCUMENT').toBe(true);
          if (observed.readyState === 'loading')
            expect.soft(false, 'PWA update DOCUMENT_LOADING').toBe(true);
          else if (observed.readyState === 'interactive')
            expect.soft(false, 'PWA update DOCUMENT_INTERACTIVE').toBe(true);
          else expect.soft(false, 'PWA update DOCUMENT_COMPLETE').toBe(true);
          if (observed.active) expect.soft(false, 'PWA update ACTIVE_ACTIVATED').toBe(true);
          else expect.soft(false, 'PWA update ACTIVE_NOT_ACTIVATED').toBe(true);
          if (observed.waiting) expect.soft(false, 'PWA update WAITING_INSTALLED').toBe(true);
          else expect.soft(false, 'PWA update WAITING_NOT_INSTALLED').toBe(true);
          if (observed.updating) expect.soft(false, 'PWA update INSTALLING_PRESENT').toBe(true);
          else expect.soft(false, 'PWA update INSTALLING_ABSENT').toBe(true);
        }
      }
      await expect(gate).toHaveCount(0);
      await requireProductionWorker(page);
      await expect
        .poll(() =>
          page.evaluate(async () => {
            const registration = await navigator.serviceWorker.ready;
            const names = await caches.keys();
            return (
              registration.active?.state === 'activated' &&
              registration.waiting === null &&
              names.includes('rct-student-pwa-v3-0.0.1') &&
              !names.some(
                (name) =>
                  name.startsWith('rct-student-pwa-') && name !== 'rct-student-pwa-v3-0.0.1',
              )
            );
          }),
        )
        .toBe(true);
      const updated = await page.evaluate(async () => {
        const names = await caches.keys();
        const cache = await caches.open('rct-student-pwa-v3-0.0.1');
        return {
          names,
          assets: (await cache.keys()).map((request) => new URL(request.url).pathname),
          controller: navigator.serviceWorker.controller?.scriptURL,
        };
      });
      expect(updated.controller).toBe(original.script);
      expect(updated.names).toEqual(expect.arrayContaining(preserved));
      expect(updated.names).not.toContain('headman-api-cache-v1');
      expect(updated.names).not.toContain(`workbox-precache-v2-${original.scope}`);
      expect(updated.assets).toEqual(
        expect.arrayContaining(['/app/', '/app/manifest.webmanifest']),
      );
      expect(
        updated.assets.some((path) => path.startsWith('/app/assets/') && path.endsWith('.js')),
      ).toBe(true);
      expect(updated.assets.every((path) => !path.includes('/api/'))).toBe(true);
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
      await context.setOffline(true);
      try {
        await page.reload();
        await page.getByRole('button', { name: 'Задания', exact: true }).click();
        await expect(page.getByText('Ты офлайн', { exact: true })).toBeVisible();
        await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
        await expect(page.getByRole('slider', { name: new RegExp(dataset.marker) })).toBeDisabled();
      } finally {
        await context.setOffline(false);
      }
    } catch (error) {
      failures.push(error);
    } finally {
      try {
        fixture.restore();
      } catch (error) {
        failures.push(error);
      }
    }
    if (failures.length === 1) throw failures[0];
    if (failures.length)
      throw new AggregateError(failures, 'Worker lifecycle and restoration failed');
  });

  test('synthetic installation prompt handles dismissed unavailable and accepted outcomes', async ({
    page,
    dataset,
  }) => {
    test.skip(process.env.RCT_E2E_SW_LIFECYCLE !== 'true', 'Explicit synthetic install logic lane');
    await loginPwa(page, dataset.student);
    const offer = page.getByRole('complementary', {
      name: 'Установка RutCampusTrack',
      exact: true,
    });
    for (const outcome of ['dismissed', 'unavailable', 'accepted'] as const) {
      // These events exercise controller logic only; they are never OS installation evidence.
      await page.evaluate((choice) => {
        const event = new Event('beforeinstallprompt', { cancelable: true });
        Object.assign(event, {
          prompt: async () => {
            if (choice === 'unavailable') throw new Error('Synthetic install API unavailable');
          },
          userChoice: Promise.resolve({
            outcome: choice === 'accepted' ? 'accepted' : 'dismissed',
          }),
        });
        window.dispatchEvent(event);
      }, outcome);
      await expect(offer).toHaveAttribute('data-install-mode', 'native');
      await offer.getByRole('button', { name: 'Установить', exact: true }).click();
      await expect(offer).toHaveCount(0);
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
    }
    // A repeated prompt cannot re-offer installation after the accepted controller outcome.
    await page.evaluate(() => {
      const event = new Event('beforeinstallprompt', { cancelable: true });
      Object.assign(event, {
        prompt: async () => undefined,
        userChoice: Promise.resolve({ outcome: 'dismissed' }),
      });
      window.dispatchEvent(event);
    });
    await expect(offer).toHaveCount(0);
  });

  test('synthetic iOS installation instructions dismiss persistently without a native prompt', async ({
    browser,
    dataset,
  }) => {
    test.skip(process.env.RCT_E2E_SW_LIFECYCLE !== 'true', 'Explicit synthetic install logic lane');
    const ios = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL,
      ignoreHTTPSErrors: true,
      serviceWorkers: 'block',
      userAgent:
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1',
    });
    try {
      const page = await ios.newPage();
      await loginPwa(page, dataset.student);
      const offer = page.getByRole('complementary', {
        name: 'Установка RutCampusTrack',
        exact: true,
      });
      await expect(offer).toHaveAttribute('data-install-mode', 'ios');
      await expect(offer).toContainText('Открой эту страницу в Safari');
      await expect(offer).toContainText('Поделиться');
      await expect(offer.getByRole('button', { name: 'Установить', exact: true })).toHaveCount(0);
      await offer
        .getByRole('button', { name: 'Скрыть предложение установки навсегда', exact: true })
        .click();
      await expect(offer).toHaveCount(0);
      await page.reload();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
      await expect(offer).toHaveCount(0);
    } finally {
      await ios.close();
    }
  });

  test('warm offline reload remains read-only; revoked session reconnect clears the cached user', async ({
    page,
    context,
    dataset,
  }) => {
    // Offline reload opens Today; keep the original tomorrow seed unchanged.
    const todayHomework = {
      title: dataset.marker,
      description: 'Синтетическое задание для сегодняшнего офлайн-снимка',
      link: null,
      subjectId: dataset.subject.id,
      groupId: dataset.group.id,
      semesterId: dataset.semester.id,
      lessonDate: dataset.today,
      lessonNumber: null,
      bindingMode: 'DATE',
      lessonType: 'LECTURE',
      requestKey: randomUUID(),
    };
    await expect
      .poll(async () => {
        const published = await dataset.headmanActor.call(
          'POST',
          '/api/academic/homeworks',
          todayHomework,
          [201, 200, 202],
        );
        return published.state !== 'PENDING' && published.id != null;
      })
      .toBe(true);
    const tomorrowRoom = `offline-next-day-${dataset.group.id}`;
    const tomorrowLesson = await dataset.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: dataset.tomorrow,
        lessonNumber: 2,
        classroom: tomorrowRoom,
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    const { accessToken, session } = await loginPwa(page, dataset.student);
    const headers = { Authorization: `Bearer ${accessToken}` };
    const schedule = await page.request.get(
      `/api/v1/student/schedule?semesterId=${dataset.semester.id}`,
      { headers },
    );
    expect(schedule.status()).toBe(200);
    const lessons = (await schedule.json()).lessons;
    expect(
      lessons.some(
        (lesson: { id: string; date: string }) =>
          String(lesson.id) === String(dataset.lesson.physicalLessonId) &&
          lesson.date === dataset.today,
      ),
    ).toBe(true);
    expect(
      lessons.some(
        (lesson: { id: string; date: string }) =>
          String(lesson.id) === String(tomorrowLesson.physicalLessonId) &&
          lesson.date === dataset.tomorrow,
      ),
    ).toBe(true);
    // A real active attachment is available online, but acquiring it offline is forbidden.
    const fileName = 'offline-boundary.png';
    const png = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGP4/x8AAwAB//wl3FEAAAAASUVORK5CYII=',
      'base64',
    );
    const created = await dataset.request.post('/api/v1/student/requests/excuse', {
      headers: { ...headers, 'Idempotency-Key': randomUUID() },
      multipart: {
        request: {
          name: 'request.json',
          mimeType: 'application/json',
          buffer: Buffer.from(
            JSON.stringify({
              lessonIds: [String(tomorrowLesson.physicalLessonId)],
              reason: 'ILLNESS',
              comment: dataset.marker,
            }),
          ),
        },
        files: { name: fileName, mimeType: 'image/png', buffer: png },
      },
    });
    expect(created.status()).toBe(200);
    const request = await created.json();
    expect(request.attachments.length === 1 && request.attachments[0].state === 'ACTIVE').toBe(
      true,
    );
    const attachmentPath = `/api/v1/student/requests/${request.summary.id}/attachments/${request.attachments[0].id}`;
    const downloaded = await dataset.request.get(attachmentPath, { headers });
    expect(downloaded.status()).toBe(200);
    expect((await downloaded.body()).equals(png)).toBe(true);
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Заявки', exact: true }).click();
    const attachment = page.getByRole('button', { name: `Открыть ${fileName}`, exact: true });
    await expect(attachment).toBeEnabled();
    let offlineAttachmentReads = 0;
    const countAttachment = (item: Request) => {
      if (new URL(item.url()).pathname === attachmentPath) offlineAttachmentReads += 1;
    };
    const pagesBefore = context.pages().length;
    page.on('request', countAttachment);
    await context.setOffline(true);
    try {
      await expect(attachment).toBeDisabled();
      await attachment.press('Enter');
      expect(offlineAttachmentReads).toBe(0);
      expect(context.pages().length).toBe(pagesBefore);
    } finally {
      page.off('request', countAttachment);
      const admitted = page.waitForResponse(
        (item) =>
          new URL(item.url()).pathname === '/api/auth/session' && item.request().method() === 'GET',
      );
      await context.setOffline(false);
      expect((await admitted).status()).toBe(200);
    }
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
    await page.evaluate(async () => {
      await navigator.serviceWorker.ready;
    });
    await expect
      .poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null), {
        timeout: 15000,
      })
      .toBe(true);
    const another = await admit(dataset.request, dataset.student, 'STUDENT');
    await another.call('DELETE', `/api/auth/sessions/${session.sessionId}`, undefined, [204]);
    let completionCommands = 0;
    const countCompletion = (item: Request) => {
      if (new URL(item.url()).pathname.endsWith('/completion') && item.method() === 'PUT')
        completionCommands += 1;
    };
    const browserTime = await page.evaluate(() => Date.now());
    page.on('request', countCompletion);
    try {
      await page.clock.setFixedTime(new Date(`${dataset.today}T20:59:59Z`));
      await context.setOffline(true);
      try {
        await page.evaluate(() => {
          Object.defineProperty(window, '__rctD002WarmOfflineReloadOldWindow', { value: true });
        });
        const cachedFallback = await page
          .evaluate(async () => {
            try {
              const worker = navigator.serviceWorker.controller;
              if (!worker) return 'UNAVAILABLE';
              return (await caches.match(new URL('./', worker.scriptURL).href))
                ? 'PRESENT'
                : 'ABSENT';
            } catch {
              return 'UNAVAILABLE';
            }
          })
          .catch(() => 'UNAVAILABLE');
        let mainDocumentRequestSeen = false;
        let mainDocumentResponse = 'NONE' as
          | 'NONE'
          | 'SW_200'
          | 'SW_OTHER'
          | 'NETWORK_200'
          | 'NETWORK_OTHER'
          | 'UNAVAILABLE';
        const observeDocumentRequest = (item: Request) => {
          try {
            if (item.isNavigationRequest() && item.frame() === page.mainFrame())
              mainDocumentRequestSeen = true;
          } catch {
            // Missing protocol observations remain unknown.
          }
        };
        const observeDocumentResponse = (item: Response) => {
          try {
            const request = item.request();
            if (!request.isNavigationRequest() || request.frame() !== page.mainFrame()) return;
            mainDocumentResponse = item.fromServiceWorker()
              ? item.status() === 200
                ? 'SW_200'
                : 'SW_OTHER'
              : item.status() === 200
                ? 'NETWORK_200'
                : 'NETWORK_OTHER';
          } catch {
            mainDocumentResponse = 'UNAVAILABLE';
          }
        };
        const reloadLocation = new URL(page.url());
        let mainDocumentFailureObserved = false;
        let mainDocumentFailure = 'UNOBSERVED' as 'UNOBSERVED' | 'NETWORK' | 'INTERNAL' | 'OTHER';
        const observeDocumentFailure = (item: Request) => {
          if (mainDocumentFailureObserved) return;
          try {
            if (!item.isNavigationRequest() || item.frame() !== page.mainFrame()) return;
            const location = new URL(item.url());
            if (
              location.origin !== reloadLocation.origin ||
              location.pathname !== reloadLocation.pathname ||
              location.search !== reloadLocation.search
            )
              return;
            mainDocumentFailureObserved = true;
            const errorText = item.failure()?.errorText;
            if (typeof errorText !== 'string' || errorText.length > 512) return;
            mainDocumentFailure =
              /ERR_INTERNET_DISCONNECTED|NS_ERROR_OFFLINE|NetworkError|network (?:error|connection)|Could not connect|Connection refused|The Internet connection appears to be offline/i.test(
                errorText,
              )
                ? 'NETWORK'
                : /internal error/i.test(errorText)
                  ? 'INTERNAL'
                  : 'OTHER';
          } catch {
            // Missing or unsafe protocol failure text remains unknown.
          }
        };
        page.on('request', observeDocumentRequest);
        page.on('response', observeDocumentResponse);
        page.on('requestfailed', observeDocumentFailure);
        try {
          await page.reload();
        } catch (error) {
          // Two closed, static-frame hints retain the original navigation error below.
          const descriptor =
            error !== null && typeof error === 'object'
              ? Object.getOwnPropertyDescriptor(error, 'message')
              : undefined;
          const message =
            descriptor &&
            'value' in descriptor &&
            typeof descriptor.value === 'string' &&
            descriptor.value.length <= 512
              ? descriptor.value
              : '';
          const category =
            /ERR_INTERNET_DISCONNECTED|NS_ERROR_OFFLINE|network (?:error|connection)|Could not connect|Connection refused/i.test(
              message,
            )
              ? 'NETWORK'
              : /ERR_ABORTED|NS_BINDING_ABORTED|navigation (?:was )?(?:aborted|interrupted)/i.test(
                    message,
                  )
                ? 'ABORT'
                : /ERR_CERT|SSL|TLS|certificate/i.test(message)
                  ? 'TLS'
                  : /timeout|timed out/i.test(message)
                    ? 'TIMEOUT'
                    : /internal error/i.test(message)
                      ? 'INTERNAL'
                      : /(?:page|context|browser|target).*(?:closed|crashed)/i.test(message)
                        ? 'CLOSED'
                        : 'OTHER';
          expect.soft(false, 'D002 offline reload rejected').toBe(true);
          switch (category) {
            case 'NETWORK':
              expect.soft(false, 'D002 reload NETWORK').toBe(true);
              break;
            case 'ABORT':
              expect.soft(false, 'D002 reload ABORT').toBe(true);
              break;
            case 'TLS':
              expect.soft(false, 'D002 reload TLS').toBe(true);
              break;
            case 'TIMEOUT':
              expect.soft(false, 'D002 reload TIMEOUT').toBe(true);
              break;
            case 'INTERNAL':
              expect.soft(false, 'D002 reload INTERNAL').toBe(true);
              break;
            case 'CLOSED':
              expect.soft(false, 'D002 reload CLOSED').toBe(true);
              break;
            case 'OTHER':
              expect.soft(false, 'D002 reload OTHER').toBe(true);
              break;
          }
          const oldDocument = await page
            .evaluate(
              () =>
                Object.getOwnPropertyDescriptor(window, '__rctD002WarmOfflineReloadOldWindow')
                  ?.value === true,
            )
            .catch(() => null);
          if (oldDocument === true) expect.soft(false, 'D002 reload OLD_DOCUMENT').toBe(true);
          else if (oldDocument === false) expect.soft(false, 'D002 reload NEW_DOCUMENT').toBe(true);
          else expect.soft(false, 'D002 reload DOCUMENT_UNAVAILABLE').toBe(true);
          switch (cachedFallback) {
            case 'PRESENT':
              expect.soft(false, 'D002 reload FALLBACK_PRESENT').toBe(true);
              break;
            case 'ABSENT':
              expect.soft(false, 'D002 reload FALLBACK_ABSENT').toBe(true);
              break;
            default:
              expect.soft(false, 'D002 reload FALLBACK_UNAVAILABLE').toBe(true);
          }
          if (mainDocumentRequestSeen) {
            switch (mainDocumentResponse) {
              case 'SW_200':
                expect.soft(false, 'D002 reload REQUEST_SEEN_SW_200').toBe(true);
                break;
              case 'SW_OTHER':
                expect.soft(false, 'D002 reload REQUEST_SEEN_SW_OTHER').toBe(true);
                break;
              case 'NETWORK_200':
                expect.soft(false, 'D002 reload REQUEST_SEEN_NETWORK_200').toBe(true);
                break;
              case 'NETWORK_OTHER':
                expect.soft(false, 'D002 reload REQUEST_SEEN_NETWORK_OTHER').toBe(true);
                break;
              case 'NONE':
                expect.soft(false, 'D002 reload REQUEST_SEEN_RESPONSE_UNOBSERVED').toBe(true);
                break;
              case 'UNAVAILABLE':
                expect.soft(false, 'D002 reload REQUEST_SEEN_RESPONSE_UNAVAILABLE').toBe(true);
                break;
            }
          } else {
            switch (mainDocumentResponse) {
              case 'SW_200':
                expect.soft(false, 'D002 reload REQUEST_UNOBSERVED_SW_200').toBe(true);
                break;
              case 'SW_OTHER':
                expect.soft(false, 'D002 reload REQUEST_UNOBSERVED_SW_OTHER').toBe(true);
                break;
              case 'NETWORK_200':
                expect.soft(false, 'D002 reload REQUEST_UNOBSERVED_NETWORK_200').toBe(true);
                break;
              case 'NETWORK_OTHER':
                expect.soft(false, 'D002 reload REQUEST_UNOBSERVED_NETWORK_OTHER').toBe(true);
                break;
              case 'NONE':
                expect.soft(false, 'D002 reload REQUEST_AND_RESPONSE_UNOBSERVED').toBe(true);
                break;
              case 'UNAVAILABLE':
                expect
                  .soft(false, 'D002 reload REQUEST_UNOBSERVED_RESPONSE_UNAVAILABLE')
                  .toBe(true);
                break;
            }
          }
          switch (mainDocumentFailure) {
            case 'NETWORK':
              expect.soft(false, 'D002 reload REQUEST_FAILED_NETWORK').toBe(true);
              break;
            case 'INTERNAL':
              expect.soft(false, 'D002 reload REQUEST_FAILED_INTERNAL').toBe(true);
              break;
            case 'OTHER':
              expect.soft(false, 'D002 reload REQUEST_FAILED_OTHER').toBe(true);
              break;
            case 'UNOBSERVED':
              expect.soft(false, 'D002 reload REQUEST_FAILED_UNOBSERVED').toBe(true);
              break;
          }
          throw error;
        } finally {
          page.off('request', observeDocumentRequest);
          page.off('response', observeDocumentResponse);
          page.off('requestfailed', observeDocumentFailure);
        }
        await expect(
          page.locator('.today-list').getByText('Синтетическая аудитория', { exact: true }),
        ).toBeVisible();
        await expect(page.locator('.today-list .student-lesson')).toHaveCount(
          lessons.filter((lesson: { date: string }) => lesson.date === dataset.today).length,
        );
        await expect(
          page.locator('.today-list').getByText(tomorrowRoom, { exact: true }),
        ).toHaveCount(0);
        await expect(
          page.getByRole('button', { name: 'Сменить роль, активная роль: студент', exact: true }),
        ).toBeDisabled();
        await page.getByRole('button', { name: 'Ещё', exact: true }).click();
        await page.getByRole('button', { name: 'Расписание семестра', exact: true }).click();
        await expect(page.getByText('Сохранённое расписание', { exact: true })).toBeVisible();
        const dates = page.getByRole('combobox', { name: 'Дата', exact: true });
        await dates.selectOption(dataset.tomorrow);
        await expect(
          page.getByRole('heading', { name: dataset.marker, exact: true }).first(),
        ).toBeVisible();
        await expect(dates).toHaveValue(dataset.tomorrow);
        await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
        // Moscow crosses midnight while UTC is still on the same calendar day.
        await page.clock.setFixedTime(new Date(`${dataset.today}T21:00:01Z`));
        await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
        await expect(
          page.locator('.today-list').getByText(tomorrowRoom, { exact: true }),
        ).toBeVisible();
        await expect(page.locator('.today-list .student-lesson')).toHaveCount(
          lessons.filter((lesson: { date: string }) => lesson.date === dataset.tomorrow).length,
        );
        await page.clock.setFixedTime(new Date(`${dataset.today}T20:59:59Z`));
        await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
        await page.getByRole('button', { name: 'Задания', exact: true }).click();
        await expect(page.getByText('Ты офлайн', { exact: true })).toBeVisible();
        await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
        await expect(page.getByRole('slider', { name: new RegExp(dataset.marker) })).toBeDisabled();
        const completion = page.getByRole('slider', { name: new RegExp(dataset.marker) });
        for (let index = 0; index < 4; index++) await completion.press('ArrowRight');
        await completion.press('Enter');
        expect(completionCommands).toBe(0);
      } finally {
        await page.clock.setFixedTime(new Date(browserTime));
        const admission = page.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/auth/refresh' &&
            item.request().method() === 'POST',
        );
        await context.setOffline(false);
        expect(await (await admission).finished()).toBeNull();
      }
      await expect(
        page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
      ).toBeVisible();
      // Observe through admission denial: an offline queue must not replay on reconnect.
      expect(completionCommands).toBe(0);
      const saved = await another.call(
        'GET',
        `/api/v1/student/homework?from=${dataset.today}&to=${dataset.today}`,
      );
      const own = saved.items.find((item: { title: string }) => item.title === dataset.marker);
      expect(own !== undefined && own.completed === false).toBe(true);
      expect(completionCommands).toBe(0);
    } finally {
      page.off('request', countCompletion);
    }
    await loginPwa(page, dataset.foreignStudent);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
    await logoutPwa(page);
    await context.setOffline(true);
    try {
      await page.reload();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
    } finally {
      await context.setOffline(false);
    }
  });

  test('D002 reconnect rechecks changed grants and a late old feed cannot replace the new account', async ({
    page,
    context,
    dataset,
  }) => {
    const marker = `${dataset.marker}-offline-owner`;
    await publishOfflineHomework(dataset, marker);
    const multirole = await dataset.user('TEACHER');
    const rolePath = `/api/academic/users/${multirole.id}/roles/STUDENT`;
    await dataset.admin.call('PUT', rolePath, {
      status: 'ACTIVE',
      groupId: dataset.group.id,
      telegramId: multirole.telegramId,
    });
    try {
      const logged = await loginPwa(page, multirole);
      expect(logged.session.activeRole).toBe('STUDENT');
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByText(marker, { exact: true })).toBeVisible();
      await requireProductionWorker(page);
      await page.getByRole('button', { name: 'Профиль', exact: true }).click();
      await page.getByRole('button', { name: 'Сменить роль', exact: true }).click();
      const teacher = page.getByRole('button', { name: /Преподаватель/ });
      await expect(teacher).toBeEnabled();
      let roleCommands = 0;
      const countRole = (item: Request) => {
        if (
          new URL(item.url()).pathname === '/api/auth/session/active-role' &&
          item.method() === 'PUT'
        )
          roleCommands += 1;
      };
      page.on('request', countRole);
      await context.setOffline(true);
      try {
        await expect(teacher).toBeDisabled();
        await expect(page.getByRole('button', { name: /Студент/ })).toBeDisabled();
        await teacher.press('Enter');
        expect(roleCommands).toBe(0);
        await dataset.admin.call('PUT', rolePath, { status: 'SUSPENDED' });
      } finally {
        page.off('request', countRole);
        const current = page.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/auth/session' &&
            item.request().method() === 'GET',
        );
        await context.setOffline(false);
        const response = await current;
        expect(response.status()).toBe(200);
        const neutral = await response.json();
        expect(neutral.activeRole ?? null).toBeNull();
        expect(
          neutral.roles.some(
            (grant: { role: string; selectable: boolean }) =>
              grant.role === 'STUDENT' && !grant.selectable,
          ),
        ).toBe(true);
        expect(
          neutral.roles.some(
            (grant: { role: string; selectable: boolean }) =>
              grant.role === 'TEACHER' && grant.selectable,
          ),
        ).toBe(true);
      }
      await expect(page.getByRole('heading', { name: 'Сменить роль', exact: true })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      await expect(page.getByText(marker, { exact: true })).toHaveCount(0);
      await selectPwaRole(page, 'Преподаватель');
      await expect(page.locator('.teacher-feature-owner')).toBeVisible();
      await logoutPwa(page);
    } finally {
      await context.setOffline(false);
      await dataset.admin.call('PUT', rolePath, { status: 'ACTIVE', groupId: dataset.group.id });
    }

    await loginPwa(page, dataset.student);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(marker, { exact: true })).toBeVisible();
    const heldFetch = await page.evaluateHandle(
      ({ tomorrow, homeworkId }) => {
        const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'fetch');
        if (!descriptor || typeof descriptor.value !== 'function' || !descriptor.configurable)
          throw new Error('D002 native fetch descriptor unavailable');
        const original = descriptor.value as typeof fetch;
        let held = false;
        let fetchedOwnResponse = false;
        let nativeRejected = false;
        let inspectionFailed = false;
        let ownerReady = false;
        let releasedToNewOwner = false;
        let delivered = false;
        let released = false;
        let releaseGate!: () => void;
        const gate = new Promise<void>((resolve) => {
          releaseGate = resolve;
        });
        let releaseTimer: ReturnType<typeof setTimeout> | undefined;
        const release = () => {
          if (released) return;
          released = true;
          if (releaseTimer !== undefined) clearTimeout(releaseTimer);
          releaseGate();
        };
        const delayed: typeof fetch = function (this: unknown, ...args) {
          let matches = false;
          try {
            const [input, init] = args;
            const request = input instanceof globalThis.Request ? input : null;
            const method = init?.method ?? request?.method ?? 'GET';
            const address =
              typeof input === 'string' ? input : input instanceof URL ? input.href : request?.url;
            if (!held && method.toUpperCase() === 'GET' && typeof address === 'string') {
              const url = new URL(address, location.href);
              matches =
                url.origin === location.origin &&
                url.pathname === '/api/v1/student/homework' &&
                url.searchParams.get('from') === tomorrow &&
                url.searchParams.get('to') === tomorrow;
            }
          } catch {
            // Matching never changes the native call's arguments, result or error.
          }
          if (matches) held = true;
          const nativePromise: ReturnType<typeof fetch> = Reflect.apply(original, this, args);
          if (!matches) return nativePromise;
          return nativePromise.then(
            async (response) => {
              try {
                const feed = await response.clone().json();
                fetchedOwnResponse =
                  response.status === 200 &&
                  Array.isArray(feed.items) &&
                  feed.items.some((item: { id: string }) => String(item.id) === homeworkId);
              } catch {
                inspectionFailed = true;
              }
              if (!released) releaseTimer = setTimeout(release, 15000);
              await gate;
              releasedToNewOwner = ownerReady;
              delivered = true;
              return response;
            },
            (error) => {
              nativeRejected = true;
              throw error;
            },
          );
        };
        const restore = () => Object.defineProperty(globalThis, 'fetch', descriptor);
        Object.defineProperty(globalThis, 'fetch', { ...descriptor, value: delayed });
        return {
          read: () => ({
            held,
            fetchedOwnResponse,
            nativeRejected,
            inspectionFailed,
            delivered,
            releasedToNewOwner,
          }),
          restore: () => {
            restore();
          },
          releaseToNewOwner: () => {
            ownerReady = true;
            release();
          },
          cleanup: () => {
            release();
            restore();
          },
        };
      },
      { tomorrow: dataset.tomorrow, homeworkId: String(dataset.homework.id) },
    );
    let fetchRestored = false;
    let gateDisposed = false;
    try {
      try {
        await page.getByRole('button', { name: 'Следующий период', exact: true }).click();
        try {
          await expect
            .poll(
              async () => (await heldFetch.evaluate((value) => value.read())).fetchedOwnResponse,
            )
            .toBe(true);
        } catch (error) {
          const state = await heldFetch.evaluate((value) => value.read()).catch(() => null);
          if (state === null) expect.soft(false, 'D002 old feed GATE_UNAVAILABLE').toBe(true);
          else if (!state.held) expect.soft(false, 'D002 old feed GATE_NOT_CLAIMED').toBe(true);
          else if (state.nativeRejected)
            expect.soft(false, 'D002 old feed NATIVE_REJECTED').toBe(true);
          else if (state.inspectionFailed)
            expect.soft(false, 'D002 old feed INSPECTION_FAILED').toBe(true);
          else expect.soft(false, 'D002 old feed OWN_RESPONSE_MISSING').toBe(true);
          throw error;
        }
      } finally {
        try {
          await heldFetch.evaluate((value) => value.restore());
          fetchRestored = true;
        } catch {
          expect.soft(false, 'D002 old feed FETCH_GATE_RESTORE_UNAVAILABLE').toBe(true);
        }
      }
      expect(fetchRestored, 'D002 old feed FETCH_GATE_NOT_RESTORED').toBe(true);
      await logoutPwa(page);
      // Keep this document alive: loginPwa.goto would destroy the delayed callback.
      await page.getByLabel('Логин', { exact: true }).fill(dataset.foreignStudent.login);
      await page
        .getByLabel('Пароль', { exact: true })
        .and(page.locator('input[name="password"]'))
        .fill(dataset.foreignStudent.password);
      const [login, current] = await Promise.all([
        page.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/auth/login' &&
            item.request().method() === 'POST',
        ),
        page.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/auth/session' &&
            item.request().method() === 'GET',
        ),
        page.getByRole('button', { name: 'Войти', exact: true }).click(),
      ]);
      expect(login.status()).toBe(200);
      expect(current.status()).toBe(200);
      expect(String((await current.json()).userId) === String(dataset.foreignStudent.id)).toBe(
        true,
      );
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      const emptyDay = page.locator('.homework-empty-day');
      await expect(emptyDay).toHaveText('На этот день заданий нет.');
      await expect(emptyDay).toBeVisible();
      await expect(page.locator('.homework-card')).toHaveCount(0);
      await heldFetch.evaluate((value) => value.releaseToNewOwner());
      await expect
        .poll(async () => (await heldFetch.evaluate((value) => value.read())).delivered)
        .toBe(true);
      expect((await heldFetch.evaluate((value) => value.read())).releasedToNewOwner).toBe(true);
      await page.evaluate(
        () =>
          new Promise<void>((resolve) =>
            requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
          ),
      );
      await expect(page.getByText(marker, { exact: true })).toHaveCount(0);
      await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
      await expect
        .poll(() =>
          page.evaluate(
            (id) => localStorage.getItem('rct-student-mobile.current-owner') === id,
            String(dataset.foreignStudent.id),
          ),
        )
        .toBe(true);
      await heldFetch.evaluate((value) => value.cleanup());
      await heldFetch.dispose().catch(() => {});
      gateDisposed = true;
      await context.setOffline(true);
      await page.reload();
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByText('Ты офлайн', { exact: true })).toBeVisible();
      await expect(page.getByText(marker, { exact: true })).toHaveCount(0);
      await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
      expect(
        await page.evaluate(
          (id) => localStorage.getItem('rct-student-mobile.current-owner') === id,
          String(dataset.foreignStudent.id),
        ),
      ).toBe(true);
    } finally {
      if (!gateDisposed) {
        try {
          await heldFetch.evaluate((value) => value.cleanup());
        } catch {
          expect.soft(false, 'D002 old feed FETCH_GATE_CLEANUP_UNAVAILABLE').toBe(true);
        } finally {
          await heldFetch.dispose().catch(() => {});
        }
      }
      await context.setOffline(false);
    }
  });

  test('D002 TMA stays online-only beside a warm PWA snapshot', async ({
    page,
    context,
    dataset,
  }) => {
    const marker = `${dataset.marker}-tma-offline`;
    await publishOfflineHomework(dataset, marker);
    await loginPwa(page, dataset.student);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(marker, { exact: true })).toBeVisible();
    await requireProductionWorker(page);
    const tma = await context.newPage();
    try {
      // The page fixture fixes educational Date only for its own page.
      await tma.clock.setFixedTime(new Date(await page.evaluate(() => Date.now())));
      await installTelegramHost(tma, syntheticInitData(dataset.student.telegramId));
      const [admitted, current] = await Promise.all([
        tma.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/auth/tma' && item.request().method() === 'POST',
        ),
        tma.waitForResponse(
          (item) =>
            new URL(item.url()).pathname === '/api/auth/session' &&
            item.request().method() === 'GET',
        ),
        tma.goto('/mini-app/'),
      ]);
      expect(admitted.status()).toBe(200);
      expect(current.status()).toBe(200);
      expect(String((await current.json()).userId) === String(dataset.student.id)).toBe(true);
      expect(await tma.evaluate(() => navigator.serviceWorker.controller === null)).toBe(true);
      expect(
        await tma.evaluate(
          (id) => localStorage.getItem('rct-student-mobile.current-owner') === id,
          String(dataset.student.id),
        ),
      ).toBe(true);
      await tma.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(tma.getByText(marker, { exact: true })).toBeVisible();
      const completion = tma
        .getByRole('article')
        .filter({ has: tma.getByText(marker, { exact: true }) })
        .getByRole('slider');
      await expect(completion).toBeEnabled();
      let commands = 0;
      const countCommand = (item: Request) => {
        const path = new URL(item.url()).pathname;
        if (
          item.method() !== 'GET' &&
          (path.startsWith('/api/v1/student/') || path === '/api/auth/session/active-role')
        )
          commands += 1;
      };
      tma.on('request', countCommand);
      await context.setOffline(true);
      try {
        await expect(
          tma.getByText('TMA работает только при подключении к интернету', { exact: true }),
        ).toBeVisible();
        await expect(tma.getByText(marker, { exact: true })).toHaveCount(0);
        await expect(
          tma.getByRole('article').filter({ has: tma.getByText(marker, { exact: true }) }),
        ).toHaveCount(0);
        await expect(completion).toHaveCount(0);
        expect(commands).toBe(0);
        // The production TMA shell is no-store and has no offline service worker.
        // A network-denied reload proves no hydration, not native Telegram behavior.
        let disconnected = false;
        try {
          await tma.reload({ waitUntil: 'domcontentloaded' });
        } catch (error) {
          disconnected =
            error instanceof Error && error.message.includes('net::ERR_INTERNET_DISCONNECTED');
        }
        expect(disconnected).toBe(true);
        await expect(tma.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
        await expect(tma.getByText(marker, { exact: true })).toHaveCount(0);
        expect(commands).toBe(0);
      } finally {
        tma.off('request', countCommand);
      }
    } finally {
      await tma.close();
      await context.setOffline(false);
    }
  });

  // Q04 / JS-ADMIN-01,04,07: one real account, group authority and retained history.
  test('ADMIN creates a multirole headman and restores the archived account without old authority', async ({
    page,
    dataset,
  }) => {
    const administrator = await dataset.user('ADMIN');
    const assistantPath = `/api/academic/assistants?groupId=${dataset.group.id}`;
    const existing = await dataset.headmanActor.call('GET', assistantPath);
    const active = existing._embedded?.assistantResponseList ?? existing.content ?? [];
    expect(
      active.length <= 1 &&
        active.every(
          (grant: { studentId: number }) => Number(grant.studentId) === dataset.assistant.id,
        ),
    ).toBe(true);
    // The worker's earlier revocation case leaves no active grant; a fresh lane has one.
    if (active.length === 0) {
      await dataset.headmanActor.call(
        'POST',
        '/api/academic/assistants',
        {
          groupId: dataset.group.id,
          studentId: dataset.assistant.id,
          permissions: ['MARK_ATTENDANCE'],
        },
        [201],
      );
    }
    const confirmed = await dataset.headmanActor.call('GET', assistantPath);
    const currentHelpers = confirmed._embedded?.assistantResponseList ?? confirmed.content ?? [];
    expect(currentHelpers.length).toBe(1);
    expect(Number(currentHelpers[0].studentId)).toBe(dataset.assistant.id);
    expect(currentHelpers[0].permissions).toContain('MARK_ATTENDANCE');
    const helper = await admit(dataset.request, dataset.assistant, 'STUDENT');
    const teacher = await admit(dataset.request, dataset.teacher, 'TEACHER');
    const archiveDate = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Europe/Moscow',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());
    const part = (name: string) => archiveDate.find((value) => value.type === name)!.value;
    const realToday = `${part('year')}-${part('month')}-${part('day')}`;
    const yesterday = new Date(Date.parse(`${realToday}T12:00:00Z`) - 86400000)
      .toISOString()
      .slice(0, 10);
    // Account archival closes membership at real Moscow today, not synthetic teaching Today.
    expect(yesterday >= dataset.semester.dateFrom).toBe(true);
    await loginPwa(page, administrator);
    await selectPwaRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    const users = page.locator('.admin-users-screen');
    await users.getByRole('button', { name: 'Создать пользователя', exact: true }).click();
    const lastName = `${dataset.student.displayName.split(' ')[0]}Новый`;
    const telegramId = dataset.student.telegramId + 20_000_000_000;
    await users.getByLabel('Фамилия', { exact: true }).fill(lastName);
    await users.getByLabel('Имя', { exact: true }).fill('Проверка');
    await users.getByLabel('Отчество', { exact: true }).fill('Синтетическая');
    const picker = users.locator('[data-picker="group"]');
    await picker.locator('summary').click();
    await picker
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await picker.getByRole('button', { name: dataset.group.name, exact: true }).click();
    await users.getByLabel('Telegram ID', { exact: true }).fill(String(telegramId));
    const [createdResponse] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/academic/users' &&
          response.request().method() === 'POST',
      ),
      users.getByRole('button', { name: 'Создать пользователя', exact: true }).click(),
    ]);
    expect(createdResponse.status()).toBe(201);
    const created = await createdResponse.json();
    expect(typeof created.login).toBe('string');
    expect(typeof created.initialPassword).toBe('string');
    const userPath = `/api/academic/users/${created.id}`;
    const original = await dataset.admin.call('GET', userPath);
    expect(typeof original.fullName === 'string' && original.fullName.trim().length > 0).toBe(true);
    expect(
      original.lastName === lastName &&
        original.firstName === 'Проверка' &&
        original.middleName === 'Синтетическая' &&
        Number(original.groupId) === dataset.group.id &&
        Number(original.telegramId) === telegramId,
    ).toBe(true);
    await users.getByRole('button', { name: 'Готово', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(created.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${created.id}"]`).click();
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    await users.getByRole('button', { name: 'Добавить роль', exact: true }).click();
    await users.getByRole('button', { name: 'Преподаватель', exact: true }).click();
    const employeeNumber = `E2E-${dataset.group.id}-${created.id}`;
    await users.getByLabel('Табельный номер', { exact: true }).fill(employeeNumber);
    const [roleSaved] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/roles/TEACHER` &&
          response.request().method() === 'PUT',
      ),
      users.getByRole('button', { name: 'Назначить роль', exact: true }).click(),
    ]);
    expect(roleSaved.status()).toBe(200);
    const multirole = await dataset.admin.call('GET', userPath);
    expect(
      ['id', 'login', 'lastName', 'firstName', 'middleName', 'groupId', 'telegramId'].every(
        (field) => multirole[field] === original[field],
      ),
    ).toBe(true);
    expect(multirole.employeeNumber === employeeNumber).toBe(true);
    expect(
      multirole.roles
        .filter((grant: { status: string }) => grant.status === 'ACTIVE')
        .map((grant: { role: string }) => grant.role)
        .sort(),
    ).toEqual(['STUDENT', 'TEACHER']);

    const historical = await dataset.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: yesterday,
        lessonNumber: 1,
        classroom: 'Синтетическая история',
      },
      [201, 200],
      { 'Idempotency-Key': dataset.marker.slice(-36) },
    );
    const reportPath = `/api/attendance/reports/lesson/${historical.physicalLessonId}`;
    const markPath = `/api/attendance/lessons/${historical.physicalLessonId}/students/${created.id}`;
    const initial = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(initial !== undefined).toBe(true);
    // The same helper bearer succeeds before replacement and is refused afterwards.
    await helper.call('PUT', markPath, {
      status: 'PRESENT',
      expectedStatus: String(initial.status ?? 'EMPTY').toUpperCase(),
      expectedRevision: initial.markRevision ?? 'EMPTY',
    });
    const before = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(before !== undefined).toBe(true);
    expect(String(before.status).toUpperCase()).toBe('PRESENT');
    expect(typeof before.markRevision).toBe('string');

    await users.getByRole('button', { name: 'Назад', exact: true }).click();
    await page.getByRole('button', { name: 'Группы', exact: true }).click();
    const groups = page.locator('.admin-groups-screen');
    await groups
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
    const group = groups.locator(`[data-group-id="${dataset.group.id}"]`);
    await group.locator('.admin-groups-item__trigger').click();
    await group.getByRole('button', { name: 'Изменить старосту', exact: true }).click();
    const replacement = groups.locator('.admin-groups-headman');
    const [headmanPreview] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname ===
            `/api/academic/groups/${dataset.group.id}/headman/preview` &&
          new URL(response.url()).searchParams.get('studentId') === String(created.id) &&
          response.request().method() === 'GET',
      ),
      replacement.getByLabel('Выбери старосту', { exact: true }).selectOption(String(created.id)),
    ]);
    expect(headmanPreview.status()).toBe(200);
    const replacementImpact = await headmanPreview.json();
    expect(Number(replacementImpact.currentHeadmanId)).toBe(dataset.headman.id);
    expect(Number(replacementImpact.candidateId)).toBe(Number(created.id));
    expect(replacementImpact.assistantsToRevoke).toBe(1);
    await expect(replacement.getByRole('status')).toContainText('Помощников к отзыву: 1.');
    const [replaced] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `/api/academic/groups/${dataset.group.id}/headman` &&
          response.request().method() === 'PUT',
      ),
      replacement.getByRole('button', { name: 'Подтвердить назначение', exact: true }).click(),
    ]);
    expect(replaced.status()).toBe(200);
    const replacedBody = await replaced.json();
    expect(Number(replacedBody.headmanId)).toBe(Number(created.id));
    expect(replacedBody.assistantsRevoked).toBe(1);
    const rosterPath = `/api/academic/groups/${dataset.group.id}/headman/roster`;
    const assignedRoster = await dataset.admin.call('GET', rosterPath);
    expect(Number(assignedRoster.currentHeadmanId)).toBe(Number(created.id));
    expect(assignedRoster.activeAssistantCount).toBe(0);
    const oldHeadman = await dataset.admin.call('GET', `/api/academic/users/${dataset.headman.id}`);
    expect(
      oldHeadman.roles.some(
        (grant: { role: string; status: string }) =>
          grant.role === 'HEADMAN' && grant.status === 'ACTIVE',
      ),
    ).toBe(false);
    for (const actor of [helper, dataset.headmanActor]) {
      const denied = await dataset.request.put(markPath, {
        headers: actor.headers(),
        data: {
          status: 'ABSENT',
          expectedStatus: 'PRESENT',
          expectedRevision: before.markRevision,
        },
      });
      if (actor === helper) expect(denied.status()).toBe(403);
      else expect([401, 403]).toContain(denied.status());
    }
    const afterReplacement = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(afterReplacement !== undefined).toBe(true);
    expect(
      afterReplacement.status === before.status &&
        afterReplacement.markRevision === before.markRevision,
    ).toBe(true);
    await page.reload();
    await page.getByRole('button', { name: 'Группы', exact: true }).click();
    await groups
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
    await group.locator('.admin-groups-item__trigger').click();
    await expect(group.getByText(original.fullName, { exact: true })).toBeVisible();

    const currentHeadman = await admit(
      dataset.request,
      {
        login: created.login,
        password: created.initialPassword,
        role: 'STUDENT',
        expectedLandingPath: '/app/',
      },
      'HEADMAN',
    );
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(created.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${created.id}"]`).click();
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    const [archivePreview] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/archive-preview` &&
          response.request().method() === 'GET',
      ),
      users.getByRole('button', { name: 'Архивировать аккаунт', exact: true }).click(),
    ]);
    expect(archivePreview.status()).toBe(200);
    const archiveImpact = await archivePreview.json();
    expect(Number(archiveImpact.userId)).toBe(Number(created.id));
    expect(archiveImpact.attendanceMarksCount).toBe(1);
    expect(archiveImpact.activeHeadmanGroupCount).toBe(1);
    expect(archiveImpact.requiresPassword).toBe(true);
    await expect(users.locator('.admin-users-data')).toContainText('Отметки посещаемости');
    await users.getByLabel('Твой текущий пароль', { exact: true }).fill(administrator.password);
    await users.getByRole('checkbox', { name: 'Подтверждаю', exact: true }).check();
    const [archived] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/archive` &&
          response.request().method() === 'POST',
      ),
      users.getByRole('button', { name: 'Архивировать аккаунт', exact: true }).click(),
    ]);
    if (archived.status() !== 204) {
      throw datasetHttpFailure('POST', `${userPath}/archive`, archived.status());
    }
    expect(archived.status()).toBe(204);
    await expect(
      users.getByText('Аккаунт архивирован. История сохранена.', { exact: true }),
    ).toBeVisible();
    const archivedAccount = await dataset.admin.call('GET', userPath);
    expect(archivedAccount.status).toBe('ARCHIVED');
    expect(
      archivedAccount.roles.some((grant: { status: string }) => grant.status === 'ACTIVE'),
    ).toBe(false);
    const archiveDenial = await dataset.request.put(markPath, {
      headers: currentHeadman.headers(),
      data: { status: 'ABSENT', expectedStatus: 'PRESENT', expectedRevision: before.markRevision },
    });
    expect([401, 403]).toContain(archiveDenial.status());
    const afterArchive = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(afterArchive !== undefined).toBe(true);
    expect(
      afterArchive.status === before.status && afterArchive.markRevision === before.markRevision,
    ).toBe(true);
    await page.reload();
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await users.getByRole('button', { name: 'Фильтры и сортировка', exact: true }).click();
    const archivedRoleFilter = users.locator('[data-picker="filter-role"]');
    await archivedRoleFilter.locator('summary').click();
    await archivedRoleFilter.getByRole('button', { name: 'Студенты', exact: true }).click();
    const archivedStatusFilter = users.locator('[data-picker="filter-status"]');
    await archivedStatusFilter.locator('summary').click();
    await archivedStatusFilter.getByRole('button', { name: 'Архивная', exact: true }).click();
    await users.getByRole('button', { name: 'Применить', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(created.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${created.id}"]`).click();
    const accountStatus = users
      .locator('.admin-users-data > div')
      .filter({ has: page.locator('dt', { hasText: /^Статус аккаунта$/ }) })
      .locator('dd');
    await expect(accountStatus).toBeVisible();
    await expect(accountStatus).toHaveText(/^\s*●?\s*Аккаунт в архиве\s*$/);
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    await users.getByRole('button', { name: 'Восстановить аккаунт', exact: true }).click();
    await expect(users.getByText(/Аккаунт восстановится без активных прав/)).toBeVisible();
    await users.getByRole('checkbox', { name: 'Подтверждаю', exact: true }).check();
    const [restored] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/restore` &&
          response.request().method() === 'POST',
      ),
      users.getByRole('button', { name: 'Восстановить аккаунт', exact: true }).click(),
    ]);
    expect(restored.status()).toBe(204);
    await expect(
      users.getByText('Аккаунт восстановлен без активных прав. Назначь нужные роли заново.', {
        exact: true,
      }),
    ).toBeVisible();
    const restoredAccount = await dataset.admin.call('GET', userPath);
    expect(restoredAccount.status).toBe('ACTIVE');
    expect(restoredAccount.groupId).toBeNull();
    expect(restoredAccount.headman).toBe(false);
    expect(
      ['id', 'login', 'lastName', 'firstName', 'middleName', 'telegramId', 'employeeNumber'].every(
        (field) => restoredAccount[field] === multirole[field],
      ),
    ).toBe(true);
    expect(
      restoredAccount.roles.some(
        (grant: { status: string; selectable: boolean }) =>
          grant.status === 'ACTIVE' || grant.selectable,
      ),
    ).toBe(false);
    const restoreDenial = await dataset.request.put(markPath, {
      headers: currentHeadman.headers(),
      data: { status: 'ABSENT', expectedStatus: 'PRESENT', expectedRevision: before.markRevision },
    });
    expect([401, 403]).toContain(restoreDenial.status());
    const afterRestore = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(afterRestore !== undefined).toBe(true);
    expect(
      afterRestore.userId === before.userId &&
        afterRestore.status === before.status &&
        afterRestore.source === before.source &&
        afterRestore.markRevision === before.markRevision,
    ).toBe(true);
    const vacant = await dataset.admin.call('GET', rosterPath);
    expect(vacant.currentHeadmanId).toBeNull();
    expect(vacant.activeAssistantCount).toBe(0);
    expect(vacant.candidates.length).toBe(assignedRoster.candidates.length - 1);
    expect(
      vacant.candidates.some(
        (candidate: { id: number }) => Number(candidate.id) === Number(created.id),
      ),
    ).toBe(false);
    const draftRegistry = await dataset.admin.call(
      'GET',
      `/api/academic/groups/registry?status=DRAFT&search=${encodeURIComponent(dataset.group.name)}&size=100`,
    );
    const draft = draftRegistry.items.find(
      (entry: { id: number }) => entry.id === dataset.group.id,
    );
    expect(draft !== undefined).toBe(true);
    expect(draft.headmanFio).toBeNull();
    expect(draft.studentCount).toBe(vacant.candidates.length);
    expect(typeof draft.draftReason === 'string' && draft.draftReason.trim().length > 0).toBe(true);
    await page.getByRole('button', { name: 'Группы', exact: true }).click();
    await groups.getByRole('tab', { name: /^Черновики/ }).click();
    await groups
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
    await group.locator('.admin-groups-item__trigger').click();
    const draftHeadman = group
      .locator('dl > div')
      .filter({ has: page.locator('dt', { hasText: /^Староста$/ }) })
      .locator('dd');
    await expect(draftHeadman).toBeVisible();
    await expect(draftHeadman).toHaveText('Не назначен');
    await expect(group).toContainText(draft.draftReason);
    await expect(
      group.locator('dl > div').filter({ hasText: 'Студентов' }).locator('dd'),
    ).toHaveText(String(draft.studentCount));

    // One ready-client group creation; the existing domain checks cover all nine programme codes.
    const ownAlphabeticCode = String(dataset.group.alphabeticCode);
    const ownNumericCode = '211';
    const ownGroupName = `${ownAlphabeticCode}-${ownNumericCode}`;
    expect(/^[А-ЯЁ]{2,4}$/.test(ownAlphabeticCode)).toBe(true);
    const ownRegistryFields = {
      name: ownGroupName,
      alphabeticCode: ownAlphabeticCode,
      numericCode: ownNumericCode,
      currentCourse: 2,
      programTypeDigit: 1,
      studyMode: 'FULL_TIME',
      educationLevel: 'BACHELOR',
      trainingDurationYears: 4,
      durationStatus: 'KNOWN',
      status: 'DRAFT',
      studentCount: 0,
      headmanFio: null,
    };
    const ownRegistry = async (status: 'ACTIVE' | 'DRAFT' | 'ARCHIVED') => {
      const result = await dataset.admin.call(
        'GET',
        `/api/academic/groups/registry?status=${status}&search=${encodeURIComponent(ownAlphabeticCode)}&size=100`,
      );
      expect(result.totalPages <= 1 && result.items.length === result.totalElements).toBe(true);
      return result.items.filter(
        (row: { alphabeticCode: string | null; numericCode: string | null; name: string }) =>
          (row.alphabeticCode === ownAlphabeticCode && row.numericCode === ownNumericCode) ||
          row.name === ownGroupName ||
          row.name.startsWith(`${ownGroupName} (`),
      );
    };
    for (const status of ['ACTIVE', 'DRAFT', 'ARCHIVED'] as const) {
      const absent = await ownRegistry(status);
      // Search includes archived names and split codes, so retained history is not ours to clean.
      expect(absent.length === 0).toBe(true);
    }
    const assertOwnRegistry = (row: Record<string, unknown>, knownId: number | null) => {
      expect(
        Number.isSafeInteger(row.id) &&
          Number(row.id) > 0 &&
          Number(row.id) !== dataset.group.id &&
          Number(row.id) !== dataset.otherGroup.id &&
          (knownId === null || row.id === knownId),
      ).toBe(true);
      expect(Object.entries(ownRegistryFields).every(([key, value]) => row[key] === value)).toBe(
        true,
      );
    };
    let ownCreatedGroupId: number | null = null;
    let ownGroupCreateAttempted = false;
    let ownGroupFlowFailed = false;
    try {
      await groups.getByRole('button', { name: 'Создать группу', exact: true }).click();
      const createForm = groups.getByRole('form', { name: 'Новая группа', exact: true });
      await createForm.getByLabel('Буквенный код', { exact: true }).fill(ownAlphabeticCode);
      await createForm.getByLabel('Цифровой код', { exact: true }).fill(ownNumericCode);
      await createForm.getByLabel('Срок обучения, лет', { exact: true }).fill('4');
      ownGroupCreateAttempted = true;
      const [groupCreated] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/academic/groups/registry' &&
            response.request().method() === 'POST',
        ),
        createForm.getByRole('button', { name: 'Создать группу', exact: true }).click(),
      ]);
      expect(groupCreated.status()).toBe(201);
      const nativeCreatedGroup = await groupCreated.json();
      if (Number.isSafeInteger(nativeCreatedGroup.id) && nativeCreatedGroup.id > 0)
        ownCreatedGroupId = nativeCreatedGroup.id;
      assertOwnRegistry(nativeCreatedGroup, ownCreatedGroupId);
      expect(ownCreatedGroupId !== null).toBe(true);
      await expect(
        groups.getByText('Группа создана и добавлена в черновики.', { exact: true }),
      ).toBeVisible();
      await expect(groups.getByRole('tab', { name: /^Черновики/ })).toHaveAttribute(
        'aria-selected',
        'true',
      );
      const nativeReadback = await dataset.admin.call(
        'GET',
        `/api/academic/groups/${ownCreatedGroupId}`,
      );
      expect(
        nativeReadback.id === ownCreatedGroupId &&
          nativeReadback.name === ownGroupName &&
          nativeReadback.alphabeticCode === ownAlphabeticCode &&
          nativeReadback.numericCode === ownNumericCode &&
          nativeReadback.currentCourse === 2 &&
          nativeReadback.trainingDurationYears === 4 &&
          nativeReadback.durationStatus === 'KNOWN' &&
          nativeReadback.active === true,
      ).toBe(true);
      const registryReadback = await ownRegistry('DRAFT');
      expect(registryReadback.length === 1).toBe(true);
      assertOwnRegistry(registryReadback[0], ownCreatedGroupId);
      await groups.getByRole('searchbox', { name: 'Найти группу', exact: true }).fill(ownGroupName);
      await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
      const ownGroupRow = groups.locator(`[data-group-id="${ownCreatedGroupId}"]`);
      await ownGroupRow.locator('.admin-groups-item__trigger').click();
      await expect(ownGroupRow.locator('strong')).toHaveText(ownGroupName);
      for (const [label, value] of [
        ['Курс', '2'],
        ['Срок обучения', '4 лет'],
        ['Студентов', '0'],
        ['Староста', 'Не назначен'],
      ] as const) {
        await expect(
          ownGroupRow
            .locator('dl > div')
            .filter({ has: page.getByText(label, { exact: true }) })
            .locator('dd'),
        ).toHaveText(value);
      }
    } catch (error) {
      ownGroupFlowFailed = true;
      throw error;
    } finally {
      if (ownGroupCreateAttempted) {
        try {
          const candidates = await ownRegistry('DRAFT');
          expect(candidates.length <= 1).toBe(true);
          if (candidates.length === 1) {
            const candidate = candidates[0];
            assertOwnRegistry(candidate, ownCreatedGroupId);
            ownCreatedGroupId = candidate.id;
            const ownPath = `/api/academic/groups/${ownCreatedGroupId}`;
            const fresh = await dataset.admin.call('GET', ownPath);
            expect(
              fresh.id === ownCreatedGroupId &&
                fresh.name === ownGroupName &&
                fresh.alphabeticCode === ownAlphabeticCode &&
                fresh.numericCode === ownNumericCode &&
                fresh.currentCourse === 2 &&
                fresh.trainingDurationYears === 4 &&
                fresh.durationStatus === 'KNOWN' &&
                fresh.active === true,
            ).toBe(true);
            const emptyRoster = await dataset.admin.call('GET', `${ownPath}/headman/roster`);
            expect(
              emptyRoster.currentHeadmanId === null &&
                emptyRoster.activeAssistantCount === 0 &&
                emptyRoster.candidates.length === 0,
            ).toBe(true);
            // Managed group history forbids DELETE, including for a newly created empty group.
            await dataset.admin.call('PUT', ownPath, { name: ownGroupName, active: false });
            const archivedOwnGroup = await dataset.admin.call('GET', ownPath);
            expect(
              archivedOwnGroup.id === ownCreatedGroupId &&
                archivedOwnGroup.active === false &&
                archivedOwnGroup.alphabeticCode === ownAlphabeticCode &&
                archivedOwnGroup.numericCode === ownNumericCode &&
                archivedOwnGroup.currentCourse === 2 &&
                archivedOwnGroup.trainingDurationYears === 4 &&
                archivedOwnGroup.durationStatus === 'KNOWN',
            ).toBe(true);
          } else {
            expect(ownCreatedGroupId === null).toBe(true);
          }
        } catch (error) {
          if (ownGroupFlowFailed)
            expect.soft(false, 'ADMIN_OWN_GROUP_ARCHIVE_CLEANUP_FAILED').toBe(true);
          else throw error;
        }
      }
    }
  });

  // Q03: one live multirole authority transition and the ready client's logout-all.
  test('SUSPENDED STUDENT retains active TEACHER access until UI logout-all revokes both sessions', async ({
    page,
    dataset,
    playwright,
  }, info) => {
    const retryCutoffMs =
      (retryStartedAt.get(info) ?? Number.NEGATIVE_INFINITY) +
      Math.max(0, Math.min(info.timeout, 120000) - 30000);
    const studentRole = `/api/academic/users/${dataset.teacher.id}/roles/STUDENT`;
    await dataset.admin.call('PUT', studentRole, {
      status: 'ACTIVE',
      groupId: dataset.group.id,
      telegramId: dataset.teacher.telegramId,
    });
    const retryRemainingMs = Math.floor(retryCutoffMs - performance.now());
    expect(retryRemainingMs, 'Synthetic teacher login retry budget remains').toBeGreaterThan(0);
    const logged = await loginPwa(page, dataset.teacher, {
      retryUntilMs: Date.now() + Math.min(65000, retryRemainingMs),
    });
    expect(logged.session.activeRole).toBe('STUDENT');
    expect(
      ['STUDENT', 'TEACHER'].every((role) =>
        logged.session.roles.some((grant) => grant.role === role && grant.selectable),
      ),
    ).toBe(true);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectTomorrowHomework(page, dataset);
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
    const homeworkPath = '/api/v1/student/homework';
    expect(
      (
        await page.request.get(homeworkPath, {
          headers: { Authorization: `Bearer ${logged.accessToken}` },
        })
      ).status(),
    ).toBe(200);

    await dataset.admin.call('PUT', studentRole, { status: 'SUSPENDED' });
    // Live admission rejects the unselectable role before checking the old JWT version.
    const deniedStudent = await page.request.get(homeworkPath, {
      headers: { Authorization: `Bearer ${logged.accessToken}` },
    });
    expect(deniedStudent.status()).toBe(403);
    expect((await deniedStudent.json()).code).toBe('WRONG_ROLE');
    const [refreshed, current] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/refresh' &&
          response.request().method() === 'POST',
      ),
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      ),
      page.reload(),
    ]);
    expect(refreshed.status()).toBe(200);
    expect(current.status()).toBe(200);
    const neutral = await current.json();
    expect(neutral.activeRole ?? null).toBeNull();
    expect(
      neutral.roles.some(
        (grant: { role: string; status: string; selectable: boolean }) =>
          grant.role === 'STUDENT' && grant.status === 'SUSPENDED' && !grant.selectable,
      ),
    ).toBe(true);
    expect(
      neutral.roles.some(
        (grant: { role: string; status: string; selectable: boolean }) =>
          grant.role === 'TEACHER' && grant.status === 'ACTIVE' && grant.selectable,
      ),
    ).toBe(true);
    await expect(page.getByRole('heading', { name: 'Сменить роль', exact: true })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Студент/ })).toBeDisabled();
    const [selected] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session/active-role' &&
          response.request().method() === 'PUT',
      ),
      selectPwaRole(page, 'Преподаватель'),
    ]);
    expect(selected.status()).toBe(200);
    const teacherAdmission = await selected.json();
    expect(teacherAdmission.session.activeRole).toBe('TEACHER');
    await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    const lessonPath = `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`;
    const teacherHeaders = { Authorization: `Bearer ${teacherAdmission.accessToken}` };
    const assigned = await page.request.get(lessonPath, { headers: teacherHeaders });
    expect(assigned.status()).toBe(200);
    const beforeLogout = await assigned.json();
    expect(JSON.stringify(beforeLogout).includes(dataset.subject.name)).toBe(true);

    // A separate cookie jar keeps this login from replacing the browser's refresh authority.
    const origin = new URL(page.url()).origin;
    const second = await playwright.request.newContext({
      baseURL: origin,
      ignoreHTTPSErrors: true,
    });
    try {
      const other = await admit(second, dataset.teacher, 'TEACHER');
      expect(other.session.sessionId !== teacherAdmission.session.sessionId).toBe(true);
      expect((await second.get(lessonPath, { headers: other.headers() })).status()).toBe(200);
      await page.getByRole('button', { name: 'Профиль', exact: true }).click();
      const profile = page.getByRole('main', { name: 'Профиль', exact: true });
      const [listed] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/auth/sessions' &&
            response.request().method() === 'GET',
        ),
        profile.getByRole('button', { name: 'Активные сеансы', exact: true }).click(),
      ]);
      expect(listed.status()).toBe(200);
      const sessions = await listed.json();
      expect(
        [teacherAdmission.session.sessionId, other.session.sessionId].every((sessionId) =>
          sessions.items.some((item: { sessionId: string }) => item.sessionId === sessionId),
        ),
      ).toBe(true);
      const screen = page.getByRole('main', { name: 'Активные сеансы', exact: true });
      await expect(screen.getByRole('list', { name: 'Список активных сеансов' })).toBeVisible();
      // Retain both actual current refresh proofs before the UI clears its own cookie.
      const browserCookie = (await page.context().cookies(`${origin}/api/auth/refresh`)).find(
        (cookie) => cookie.name === 'rct_refresh',
      );
      const secondCookie = (await second.storageState()).cookies.find(
        (cookie) => cookie.name === 'rct_refresh',
      );
      expect(Boolean(browserCookie?.value && secondCookie?.value)).toBe(true);
      expect(browserCookie?.value !== secondCookie?.value).toBe(true);
      const [loggedOut] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/auth/logout-all' &&
            response.request().method() === 'POST',
        ),
        screen.getByRole('button', { name: 'Выйти на всех устройствах', exact: true }).click(),
      ]);
      expect(loggedOut.status()).toBe(204);
      await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      for (const headers of [teacherHeaders, other.headers()]) {
        expect((await second.get(lessonPath, { headers })).status()).toBe(401);
      }
      for (const cookie of [browserCookie, secondCookie]) {
        const deniedRefresh = await second.post('/api/auth/refresh', {
          headers: { Cookie: `rct_refresh=${cookie!.value}`, Origin: origin },
        });
        expect(deniedRefresh.status()).toBe(401);
      }
      // Fresh admission can still read the same assignment; revocation did not alter its data.
      const fresh = await admit(second, dataset.teacher, 'TEACHER');
      const retained = await fresh.call('GET', lessonPath);
      expect
        .soft(
          isDeepStrictEqual(
            { lesson: retained.lesson, roster: retained.roster },
            { lesson: beforeLogout.lesson, roster: beforeLogout.roster },
          ),
          'Retained lesson and ordered roster preserve structural data',
        )
        .toBe(true);
      expect(
        JSON.stringify({ lesson: retained.lesson, roster: retained.roster }) ===
          JSON.stringify({ lesson: beforeLogout.lesson, roster: beforeLogout.roster }),
      ).toBe(true);
      await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    } finally {
      await second.dispose();
    }
  });
});

// Engine/emulation control only; this cannot replace production PWA offline acceptance.
test('WebKit direct cached document survives emulated offline reload control', async ({
  directCacheControl,
}) => {
  const shellPath = '/rct-offline-control/';
  const workerPath = `${shellPath}sw.js`;
  const cacheName = 'rct-direct-cache-control-v1';
  const shell =
    '<!DOCTYPE html><html><head></head><body><main>Direct cached offline control</main></body></html>';
  const worker = `
const cacheName = ${JSON.stringify(cacheName)};
const shellURL = new URL('./', self.location.href).href;
self.addEventListener('install', event => {
  event.waitUntil(caches.open(cacheName)
    .then(cache => cache.put(shellURL, new Response(${JSON.stringify(shell)}, {
      status: 200, headers: { 'Content-Type': 'text/html; charset=utf-8' }
    })))
    .then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('fetch', event => {
  if (event.request.mode !== 'navigate' || event.request.url !== shellURL) return;
  event.respondWith(caches.open(cacheName).then(cache => cache.match(shellURL)).then(cached => {
    if (!cached) throw new Error('Control cached document missing');
    return cached;
  }));
});
`;
  let handlerFailed = false;
  const server = createServer((request, response) => {
    try {
      if (request.method === 'GET' && request.url === shellPath) {
        response.writeHead(200, {
          'Content-Type': 'text/html; charset=utf-8',
          'Cache-Control': 'no-store',
        });
        response.end(shell);
      } else if (request.method === 'GET' && request.url === workerPath) {
        response.writeHead(200, {
          'Content-Type': 'application/javascript; charset=utf-8',
          'Cache-Control': 'no-store',
        });
        response.end(worker);
      } else {
        response.writeHead(404);
        response.end();
      }
    } catch {
      handlerFailed = true;
      response.destroy();
    }
  });
  server.on('error', () => {
    handlerFailed = true;
  });
  directCacheControl.server = server;
  const context = directCacheControl.context;
  let controlPage: Page | undefined;
  let bodyFailed = false;
  let bodyError: unknown;
  const cleanupErrors: unknown[] = [];
  try {
    await new Promise<void>((resolve, reject) => {
      server.once('error', reject);
      server.listen(0, '127.0.0.1', resolve);
    });
    const address = server.address();
    expect(address !== null && typeof address !== 'string').toBe(true);
    if (address === null || typeof address === 'string') throw new Error('Control address missing');
    const origin = `http://127.0.0.1:${address.port}`;
    const shellURL = `${origin}${shellPath}`;
    controlPage = await context.newPage();
    const initial = await controlPage.goto(shellURL, { waitUntil: 'load', timeout: 10000 });
    expect(initial?.status()).toBe(200);
    expect(await controlPage.evaluate(() => window.isSecureContext)).toBe(true);
    await controlPage.evaluate(
      async ({ workerPath, shellPath }) => {
        let timer: ReturnType<typeof setTimeout> | undefined;
        try {
          await Promise.race([
            navigator.serviceWorker
              .register(workerPath, { scope: shellPath })
              .then(() => navigator.serviceWorker.ready),
            new Promise<never>((_, reject) => {
              timer = setTimeout(
                () => reject(new Error('Control worker admission timed out')),
                5000,
              );
            }),
          ]);
        } finally {
          clearTimeout(timer);
        }
      },
      { workerPath, shellPath },
    );
    await expect
      .poll(() => controlPage!.evaluate(() => navigator.serviceWorker.controller?.state), {
        timeout: 10000,
      })
      .toBe('activated');
    expect(
      await controlPage.evaluate(
        async ({ cacheName, shellURL, shell }) => {
          const cached = await (await caches.open(cacheName)).match(shellURL);
          return cached?.status === 200 && (await cached.text()) === shell;
        },
        { cacheName, shellURL, shell },
      ),
    ).toBe(true);
    const online = await controlPage.reload({ waitUntil: 'load', timeout: 10000 });
    expect(online?.status()).toBe(200);
    expect(online?.fromServiceWorker()).toBe(true);
    await expect(controlPage.getByRole('main')).toHaveText('Direct cached offline control');
    expect(await controlPage.content()).toBe(shell);
    await controlPage.evaluate(() => {
      Object.defineProperty(window, '__rctDirectCacheOldDocument', {
        value: true,
        configurable: true,
      });
    });
    await context.setOffline(true);
    const offline = await controlPage.reload({ waitUntil: 'load', timeout: 10000 });
    expect(offline?.status()).toBe(200);
    expect(offline?.fromServiceWorker()).toBe(true);
    expect(
      await controlPage.evaluate(
        () =>
          !Object.prototype.hasOwnProperty.call(window, '__rctDirectCacheOldDocument') &&
          document.readyState === 'complete',
      ),
    ).toBe(true);
    await expect(controlPage.getByRole('main')).toHaveText('Direct cached offline control');
    expect(await controlPage.content()).toBe(shell);
  } catch (error) {
    bodyFailed = true;
    bodyError = error;
  } finally {
    if (context) {
      try {
        await context.setOffline(false);
      } catch (error) {
        cleanupErrors.push(error);
      }
      if (controlPage && !controlPage.isClosed()) {
        try {
          await controlPage.evaluate(
            async ({ shellPath, cacheName }) => {
              const ownScope = new URL(shellPath, location.origin).href;
              const registration = await navigator.serviceWorker.getRegistration(ownScope);
              if (registration?.scope === ownScope) await registration.unregister();
              await caches.delete(cacheName);
            },
            { shellPath, cacheName },
          );
        } catch (error) {
          cleanupErrors.push(error);
        }
      }
    }
    if (server.listening) {
      server.closeAllConnections();
      try {
        await new Promise<void>((resolve, reject) =>
          server.close((error) => (error ? reject(error) : resolve())),
        );
      } catch (error) {
        cleanupErrors.push(error);
      }
    }
  }
  expect.soft(handlerFailed, 'DIRECT_CACHE_CONTROL_HTTP_HANDLER_FAILED').toBe(false);
  expect.soft(cleanupErrors.length === 0, 'DIRECT_CACHE_CONTROL_CLEANUP_FAILED').toBe(true);
  if (bodyFailed) throw bodyError;
});
