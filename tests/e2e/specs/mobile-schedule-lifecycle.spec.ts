import { createHash, randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import type { Page } from '@playwright/test';
import { Actor, admit, expect, test as base, MobileDataset } from '../fixtures/mobile-dataset';
import { http, registerPrivateSuite, selectRole } from '../fixtures/cloud-accepted-support';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';

registerPrivateSuite();

// The existing lesson-lifecycle spec uses a worker-scoped dataset and restores
// its physical lesson as a new generation. Each scenario here owns a fresh
// dataset: template/headman mutations never change that worker or the other case.
const test = base.extend<{ scenario: MobileDataset }>({
  scenario: async ({ dataset, teachingInstant }, use) => {
    const own = new MobileDataset(dataset.request, dataset.admin, teachingInstant);
    await own.initialize();
    await use(own);
  },
});

interface PhysicalLesson {
  id: number;
  scheduleItemId: number | null;
  occurrenceId: number;
  assignmentId: number;
  generation: number;
  revision: number;
  occurrenceRevision: number;
  date: string;
  status: string;
  room: string | null;
  current: boolean;
}

async function physicalLessons(dataset: MobileDataset, actor = dataset.headmanActor) {
  const response = await actor.call(
    'GET',
    `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.semester.dateFrom}&dateTo=${dataset.semester.dateTo}&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED&size=100`,
  );
  const rows: PhysicalLesson[] = response._embedded?.lessonResponseList ?? [];
  // This finite, otherwise empty group must fit the single inspected page.
  expect(Array.isArray(rows) && response.page.totalElements === rows.length).toBe(true);
  return rows;
}

function identities(rows: PhysicalLesson[]) {
  return JSON.stringify(
    rows.map(
      ({
        id,
        occurrenceId,
        assignmentId,
        generation,
        revision,
        occurrenceRevision,
        date,
        status,
        room,
        current,
      }) => ({
        id,
        occurrenceId,
        assignmentId,
        generation,
        revision,
        occurrenceRevision,
        date,
        status,
        room,
        current,
      }),
    ),
  );
}

// This is reconstructed accepted historical state, not a past API publication.
// Only the original publication/binding are seeded; normal APIs below create
// the edit history and student completion used by the preservation oracle.
function seedPastHomework(dataset: MobileDataset, lesson: PhysicalLesson, itemId: number) {
  const assignment = dataset.subject.assignments[0];
  for (const value of [
    dataset.group.id,
    dataset.subject.id,
    dataset.semester.id,
    dataset.headman.id,
    dataset.student.id,
    dataset.teacher.id,
    assignment.id,
    itemId,
    lesson.id,
    lesson.occurrenceId,
  ]) {
    if (!Number.isSafeInteger(value) || value <= 0)
      throw new Error('Historical homework fixture identity invalid');
  }
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(lesson.date) ||
    lesson.date >= dataset.today ||
    lesson.status !== 'CLOSED' ||
    !lesson.current ||
    lesson.generation !== 1 ||
    lesson.scheduleItemId !== itemId ||
    lesson.assignmentId !== assignment.id
  )
    throw new Error('Historical homework fixture lesson invalid');

  function docker(args: string[], input?: string) {
    try {
      const result = spawnSync('docker', args, {
        input,
        encoding: 'utf8',
        timeout: 8000,
        killSignal: 'SIGKILL',
        maxBuffer: 65536,
        stdio: ['pipe', 'pipe', 'pipe'],
      });
      if (result.error || result.status !== 0 || result.signal)
        throw new Error('Historical homework fixture command failed');
      return result.stdout.trim();
    } catch {
      throw new Error('Historical homework fixture command failed');
    }
  }
  // Application recovery leaves the original databases stopped and replaces
  // their service aliases with this run's explicitly disposable targets.
  const recoveryProject = process.env.RCT_APP_RECOVERY_PROJECT;
  const project = recoveryProject ?? 'rct-e2e';
  if (recoveryProject !== undefined) {
    const runId = process.env.GITHUB_RUN_ID;
    const runAttempt = process.env.GITHUB_RUN_ATTEMPT;
    if (
      process.env.GITHUB_ACTIONS !== 'true' ||
      !/^rct-recovery-[1-9][0-9]*-[1-9][0-9]*-dst$/.test(recoveryProject) ||
      recoveryProject !== `rct-recovery-${runId}-${runAttempt}-dst`
    )
      throw new Error('Historical homework fixture recovery project invalid');
  }
  type Service = 'postgres-academic' | 'postgres-schedule';
  const containers = new Map<Service, string>();
  function inspect(service: Service, id: string) {
    const observed = docker([
      'inspect',
      '--format',
      '{{.Id}}|{{.State.Running}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{index .Config.Labels "io.rutcampustrack.recovery"}}',
      id,
    ]);
    const fields = observed.split('|');
    if (
      fields.length !== 5 ||
      !/^[a-f0-9]{64}$/.test(fields[0]) ||
      !fields[0].startsWith(id) ||
      fields[1] !== 'true' ||
      fields[2] !== project ||
      fields[3] !== service ||
      (recoveryProject !== undefined && fields[4] !== 'disposable')
    )
      throw new Error('Historical homework fixture container invalid');
    return fields[0];
  }
  for (const service of ['postgres-academic', 'postgres-schedule'] as const) {
    const id = docker([
      'ps',
      '-q',
      '--filter',
      'status=running',
      '--filter',
      `label=com.docker.compose.project=${project}`,
      '--filter',
      `label=com.docker.compose.service=${service}`,
      ...(recoveryProject !== undefined
        ? ['--filter', 'label=io.rutcampustrack.recovery=disposable']
        : []),
    ]);
    if (!/^[a-f0-9]{12,64}$/.test(id))
      throw new Error('Historical homework fixture container missing');
    containers.set(service, inspect(service, id));
  }
  function sql(service: Service, statement: string) {
    const id = containers.get(service)!;
    if (inspect(service, id) !== id)
      throw new Error('Historical homework fixture container changed');
    return docker(
      [
        'exec',
        '-i',
        id,
        'psql',
        '-X',
        '-qAt',
        '--no-password',
        '-U',
        'rct_user',
        '-d',
        service === 'postgres-academic' ? 'academic_db' : 'schedule_db',
        '-v',
        'ON_ERROR_STOP=1',
      ],
      `BEGIN; SET LOCAL statement_timeout='5s'; SET LOCAL lock_timeout='1s';
       SET LOCAL standard_conforming_strings=on; ${statement} COMMIT;`,
    );
  }
  function idFrom(value: string) {
    const id = Number(value);
    if (!/^[1-9][0-9]*$/.test(value) || !Number.isSafeInteger(id))
      throw new Error('Historical homework fixture row not admitted');
    return id;
  }
  const quote = (value: string) => `'${value.replace(/'/g, "''")}'`;
  const group = dataset.group.id,
    subject = dataset.subject.id,
    semester = dataset.semester.id,
    actor = dataset.headman.id,
    day = quote(lesson.date);
  const title = 'Синтетическое прошлое ДЗ',
    description = 'Сохранённое задание предыдущего занятия',
    link = 'https://example.invalid/q07';
  const key = randomUUID();
  // Exact v3 original-create encoding from HomeworkService; no arbitrary hash
  // and no reuse of this setup encoding as the observable preservation oracle.
  const bytes: Buffer[] = [];
  const int = (value: number) => {
    const part = Buffer.alloc(4);
    part.writeInt32BE(value);
    bytes.push(part);
  };
  const long = (value: number) => {
    const part = Buffer.alloc(8);
    part.writeBigInt64BE(BigInt(value));
    bytes.push(part);
  };
  const text = (value: string) => {
    const part = Buffer.from(value, 'utf8');
    int(part.length);
    bytes.push(part);
  };
  int(3);
  for (const value of [actor, group, subject, semester]) long(value);
  text(lesson.date);
  int(7);
  long(lesson.occurrenceId);
  long(1); // Accepted original generation, before its normal CLOSED transition.
  for (const value of [title, description, link, 'LESSON', 'LECTURE']) text(value);
  bytes.push(Buffer.from([1]));
  long(lesson.occurrenceId);
  long(1);
  const hash = quote(createHash('sha256').update(Buffer.concat(bytes)).digest('hex'));
  const intent = quote(
    JSON.stringify({
      groupId: group,
      subjectId: subject,
      semesterId: semester,
      title,
      description,
      link,
      bindingMode: 'LESSON',
      lessonDate: lesson.date,
      lessonNumber: 7,
      lessonType: 'LECTURE',
      selectedOccurrenceId: lesson.occurrenceId,
      selectedOccurrenceRevision: 1,
    }),
  );
  const scope = `EXISTS (SELECT 1 FROM semesters s JOIN assignments a ON a.semester_id=s.id
    JOIN subjects sub ON sub.id=a.subject_id AND sub.group_id=a.group_id
    WHERE s.id=${semester} AND s.is_active AND NOT s.is_archived
      AND s.archive_transition='NONE' AND NOT s.archive_release_pending
      AND ${day}::date BETWEEN s.date_from AND s.date_to
      AND a.id=${assignment.id} AND a.group_id=${group} AND a.subject_id=${subject}
      AND a.teacher_id=${dataset.teacher.id} AND a.lesson_type='lecture'
      AND a.valid_from<=${day}::date
      AND (a.valid_until_exclusive IS NULL OR a.valid_until_exclusive>${day}::date))
    AND EXISTS (SELECT 1 FROM user_role_grants WHERE user_id=${actor}
      AND role='headman' AND status='active' AND group_id=${group})
    AND EXISTS (SELECT 1 FROM user_role_grants WHERE user_id=${dataset.student.id}
      AND role='student' AND status='active' AND group_id=${group})`;
  const homeworkId = idFrom(
    sql(
      'postgres-academic',
      `SELECT nextval(pg_get_serial_sequence('homeworks','id')) WHERE ${scope};`,
    ),
  );
  // Separate databases do not provide distributed atomicity. Any partial seed
  // fails this case closed and remains solely for the existing CI stack teardown.
  const bindingId = idFrom(
    sql(
      'postgres-schedule',
      `INSERT INTO lesson_homework_bindings
       (occurrence_id,current_lesson_id,homework_id,actor_id,request_key,payload_hash,state,
        revision,created_at,updated_at)
       SELECT o.id,l.id,${homeworkId},${actor},${quote(key)}::uuid,decode(${hash},'hex'),
         'ACTIVE',2,(${day}::date+TIME '00:00') AT TIME ZONE 'Europe/Moscow',
         (${day}::date+TIME '00:00:01') AT TIME ZONE 'Europe/Moscow'
       FROM lessons l JOIN lesson_occurrences o ON o.id=l.occurrence_id
       JOIN schedule_items i ON i.id=l.schedule_item_id
       JOIN schedule_assignment_fences f ON f.assignment_id=l.assignment_id
       WHERE l.id=${lesson.id} AND o.id=${lesson.occurrenceId} AND o.current_lesson_id=l.id
         AND l.schedule_item_id=${itemId} AND o.schedule_item_id=i.id AND i.is_active
         AND l.group_id=${group} AND l.subject_id=${subject} AND l.semester_id=${semester}
         AND l.assignment_id=${assignment.id} AND l.assigned_teacher_id=${dataset.teacher.id}
         AND l.lesson_type='lecture' AND l.lesson_number=7 AND l.date=${day}::date
         AND l.status='closed' AND l.generation=1 AND o.generation=1 AND o.revision=1
         AND o.group_id=l.group_id AND o.subject_id=l.subject_id AND o.semester_id=l.semester_id
         AND o.assignment_id=l.assignment_id AND o.assigned_teacher_id=l.assigned_teacher_id
         AND o.lesson_type=l.lesson_type AND o.occurrence_date=l.date
         AND l.cancelled_at IS NULL AND l.start_time>TIME '00:00:01'
         AND l.date<(CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date
         AND f.group_id=l.group_id AND f.subject_id=l.subject_id AND f.semester_id=l.semester_id
         AND f.assigned_teacher_id=l.assigned_teacher_id AND f.lesson_type=l.lesson_type
         AND f.valid_from<=l.date AND f.creation_cap_until_exclusive>l.date
         AND NOT EXISTS (SELECT 1 FROM lesson_transfer_operations t
           WHERE t.occurrence_id=o.id AND t.state<>'COMPLETED')
         AND NOT EXISTS (SELECT 1 FROM schedule_semester_archive_barriers b
           WHERE b.semester_id=${semester} AND b.participant_state<>'RELEASED')
       RETURNING binding_id;`,
    ),
  );
  const inserted = idFrom(
    sql(
      'postgres-academic',
      `INSERT INTO homeworks
       (id,group_id,subject_id,semester_id,title,description,link,published_by,actor_id,
        lesson_date,lesson_number,binding_id,request_key,payload_hash,publication_state,
        binding_mode,revision,lesson_type,placement_occurrence_id,placement_lesson_id,
        placement_binding_revision,create_actor_role,create_intent,create_intent_kind,created_at,updated_at)
       SELECT ${homeworkId},${group},${subject},${semester},${quote(title)},${quote(description)},
         ${quote(link)},${actor},${actor},${day}::date,7,${bindingId},${quote(key)}::uuid,
         decode(${hash},'hex'),'ACTIVE','LESSON',1,'LECTURE',${lesson.occurrenceId},${lesson.id},
         2,'HEADMAN',${intent}::jsonb,'ORIGINAL_CREATE',
         (${day}::date+TIME '00:00') AT TIME ZONE 'Europe/Moscow',
         (${day}::date+TIME '00:00:01') AT TIME ZONE 'Europe/Moscow'
       WHERE ${scope} RETURNING id;`,
    ),
  );
  if (inserted !== homeworkId) throw new Error('Historical homework fixture publication mismatch');
  function bindingRetained() {
    return (
      sql(
        'postgres-schedule',
        `SELECT count(*)=1 FROM lesson_homework_bindings WHERE binding_id=${bindingId}
         AND homework_id=${homeworkId} AND actor_id=${actor} AND request_key=${quote(key)}::uuid
         AND payload_hash=decode(${hash},'hex') AND state='ACTIVE' AND revision=2
         AND group_id=${group} AND subject_id=${subject} AND semester_id=${semester}
         AND occurrence_id=${lesson.occurrenceId} AND current_lesson_id=${lesson.id}
         AND binding_mode='LESSON' AND placement_date=${day}::date AND placement_lesson_number=7
         AND original_occurrence_id=${lesson.occurrenceId} AND original_binding_mode='LESSON'
         AND original_date=${day}::date AND original_lesson_number=7 AND pending_edit_operation_id IS NULL;`,
      ) === 't'
    );
  }
  expect(bindingRetained()).toBe(true);
  return { homeworkId, bindingId, key, title, description, link, bindingRetained };
}

async function fillSlot(page: Page, dataset: MobileDataset, labels: string[], room: string) {
  const form = page.locator('.headman-schedule__form');
  for (const label of labels) {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const choice = form
      .getByRole('group', { name: 'Учебные недели', exact: true })
      .getByRole('button', { name: new RegExp(`^${escaped}`) });
    if ((await choice.getAttribute('aria-pressed')) === 'false') await choice.click();
    await expect(choice).toHaveAttribute('aria-pressed', 'true');
  }
  await form.getByRole('button', { name: 'Номер пары: выбери номер пары', exact: true }).click();
  await form.locator('button[data-slot="7"]').click();
  await form
    .locator('label')
    .filter({
      has: page.locator('span.headman-schedule__field-caption').filter({ hasText: /^Предмет$/ }),
    })
    .locator('select')
    .selectOption(String(dataset.subject.id));
  await form
    .locator('label')
    .filter({
      has: page
        .locator('span.headman-schedule__field-caption')
        .filter({ hasText: /^Тип занятия$/ }),
    })
    .locator('select')
    .selectOption(String(dataset.subject.assignments[0].id));
  await form.getByLabel('Аудитория', { exact: true }).fill(room);
}

// JS-HEADMAN-40 / DF-PWA-BUILDER-REMOVE-001. Actual Vue DELETE/return and parity
// changes; the already accepted real-PG elapsed-gap/no-backfill rule is reused.
// No clock advance or claim of an elapsed absence interval in this browser case.
test('HEADMAN removes and returns a recurring slot without losing past identity or marks @real-mobile', async ({
  page,
  scenario: dataset,
}) => {
  expect(dataset.semester.dateFrom < dataset.today).toBe(true);
  const day = new Date(`${dataset.semester.dateFrom}T12:00:00Z`).getUTCDay() || 7;
  const dayLabel = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'][day - 1]!;
  const room = 'Синтетическая аудитория конструктора';
  const policy = await dataset.headmanActor.call(
    'GET',
    `/api/schedule/academic-week-policy?date=${dataset.today}`,
  );
  const weeks: { weekType: string; label: string }[] = policy.weekTypes.filter(
    (option: { parityNumber: number | null }) => option.parityNumber !== null,
  );
  expect(weeks.length === 2 && new Set(weeks.map((week) => week.weekType)).size === 2).toBe(true);
  await loginPwa(page, dataset.headman);
  await selectRole(page, 'Староста');
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await page.getByRole('button', { name: 'Конструктор расписания', exact: true }).click();
  await page.getByRole('tab', { name: dayLabel, exact: true }).click();
  await page
    .locator('.headman-schedule__weeks')
    .getByRole('button', { name: weeks[0]!.label, exact: true })
    .click();
  await page.getByRole('button', { name: 'Добавить пару', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Удалить пару', exact: true })).toBeDisabled();
  await fillSlot(
    page,
    dataset,
    weeks.map((week) => week.label),
    room,
  );
  const created = http(page, '/api/schedule/items', 'POST');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  const createResponse = await created;
  expect(createResponse.status()).toBe(201);
  const item = await createResponse.json();
  const itemPath = `/api/schedule/items/${item.id}`;
  expect(
    item.groupId === dataset.group.id &&
      item.semesterId === dataset.semester.id &&
      item.weekType === 'ALL',
  ).toBe(true);
  await expect(page.getByRole('status').filter({ hasText: 'Слот сохранён.' })).toBeVisible();
  let own: PhysicalLesson[] = [];
  await expect
    .poll(
      async () => {
        own = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
        return (
          own.some((row) => row.date < dataset.today) &&
          own.filter((row) => row.date < dataset.today).every((row) => row.status === 'CLOSED')
        );
      },
      {
        timeout: 90000,
        message: 'Own recurring history closes through the normal educational job',
      },
    )
    .toBe(true);
  const past = own.filter((row) => row.date < dataset.today);
  const future = own.filter((row) => row.date >= dataset.today);
  expect(future.length > 1 && future.every((row) => row.status === 'PLANNED' && row.current)).toBe(
    true,
  );
  expect(
    own.length === item.generatedCount &&
      own.every((row) => row.occurrenceId > 0 && row.generation === 1),
  ).toBe(true);
  const reportPath = `/api/attendance/reports/lesson/${past[0]!.id}`;
  let mark: { status: string; markRevision: string } | undefined;
  await expect
    .poll(
      async () => {
        const report = await dataset.headmanActor.call('GET', reportPath);
        mark = report.entries.find(
          (entry: { userId: number }) => entry.userId === dataset.student.id,
        );
        return (
          mark?.status === 'absent' &&
          typeof mark.markRevision === 'string' &&
          mark.markRevision.trim().length > 0
        );
      },
      { timeout: 60000, message: 'Own closed mark materializes before its exact CAS' },
    )
    .toBe(true);
  await dataset.headmanActor.call(
    'PUT',
    `/api/attendance/lessons/${past[0]!.id}/students/${dataset.student.id}`,
    {
      status: 'PRESENT',
      expectedStatus: 'ABSENT',
      expectedRevision: mark!.markRevision,
    },
  );
  const marked = await dataset.headmanActor.call('GET', reportPath);
  const savedMark = marked.entries.find(
    (entry: { userId: number }) => entry.userId === dataset.student.id,
  );
  expect(savedMark.status === 'present' && typeof savedMark.markRevision === 'string').toBe(true);
  const pastIdentity = identities(past);

  const historical = seedPastHomework(dataset, past[0]!, item.id);
  const homeworkPath = `/api/academic/homeworks/${historical.homeworkId}`;
  const originalHomework = await dataset.headmanActor.call('GET', homeworkPath);
  expect(
    originalHomework.id === historical.homeworkId &&
      originalHomework.bindingId === historical.bindingId &&
      originalHomework.requestKey === historical.key &&
      originalHomework.title === historical.title &&
      originalHomework.description === historical.description &&
      originalHomework.link === historical.link &&
      originalHomework.groupId === dataset.group.id &&
      originalHomework.subjectId === dataset.subject.id &&
      originalHomework.semesterId === dataset.semester.id &&
      originalHomework.publishedBy === dataset.headman.id &&
      originalHomework.bindingMode === 'LESSON' &&
      originalHomework.lessonDate === past[0]!.date &&
      originalHomework.lessonNumber === 7 &&
      originalHomework.lessonType === 'LECTURE' &&
      originalHomework.revision === 1 &&
      originalHomework.archived === false,
  ).toBe(true);
  const editedTitle = 'Синтетическое прошлое ДЗ после изменения';
  // Omit placement fields: the accepted content-only operation creates real
  // history on the retained past LESSON, without publishing onto a past date.
  await dataset.headmanActor.call('PUT', homeworkPath, {
    title: editedTitle,
    description: historical.description,
    link: historical.link,
    expectedRevision: 1,
    requestKey: randomUUID(),
  });
  const homeworkBefore = await dataset.headmanActor.call('GET', homeworkPath);
  expect(
    homeworkBefore.title === editedTitle &&
      homeworkBefore.description === historical.description &&
      homeworkBefore.link === historical.link &&
      homeworkBefore.revision === 2 &&
      homeworkBefore.bindingId === historical.bindingId &&
      homeworkBefore.lessonDate === past[0]!.date &&
      homeworkBefore.lessonNumber === 7 &&
      homeworkBefore.lessonType === 'LECTURE' &&
      homeworkBefore.archived === false,
  ).toBe(true);
  const historyPath = `${homeworkPath}/history?size=100`;
  const historyBefore = await dataset.headmanActor.call('GET', historyPath);
  const events = historyBefore._embedded?.homeworkHistoryResponseList ?? historyBefore.content;
  expect(
    Array.isArray(events) &&
      historyBefore.totalElements === 1 &&
      events.length === 1 &&
      events[0].revision === 2 &&
      events[0].actorId === dataset.headman.id &&
      events[0].action === 'EDITED' &&
      typeof events[0].occurredAt === 'string' &&
      Number.isFinite(Date.parse(events[0].occurredAt)) &&
      events[0].before?.title === historical.title &&
      events[0].after?.title === editedTitle,
  ).toBe(true);
  const student = await admit(dataset.request, dataset.student, 'STUDENT');
  const completed = await student.call(
    'PUT',
    `/api/v1/student/homework/${historical.homeworkId}/completion`,
    { completed: true },
  );
  expect(
    completed.id === String(historical.homeworkId) &&
      completed.completed === true &&
      typeof completed.completedAt === 'string' &&
      Number.isFinite(Date.parse(completed.completedAt)),
  ).toBe(true);
  const feedPath = `/api/v1/student/homework?from=${past[0]!.date}&to=${past[0]!.date}`;
  async function pastStudentHomework() {
    const feed = await student.call('GET', feedPath);
    expect(Array.isArray(feed.items)).toBe(true);
    const matches = feed.items.filter((row: { id: string }) => row.id === completed.id);
    expect(matches.length === 1).toBe(true);
    return matches[0];
  }
  const studentBefore = await pastStudentHomework();
  expect(
    studentBefore.title === editedTitle &&
      studentBefore.description === historical.description &&
      studentBefore.link === historical.link &&
      studentBefore.lessonDate === past[0]!.date &&
      studentBefore.lessonNumber === 7 &&
      studentBefore.bindingMode === 'LESSON' &&
      studentBefore.archived === false &&
      studentBefore.completed === true &&
      studentBefore.completedAt === completed.completedAt,
  ).toBe(true);
  async function expectPastHomeworkRetained() {
    const homework = await dataset.headmanActor.call('GET', homeworkPath);
    const history = await dataset.headmanActor.call('GET', historyPath);
    const personal = await pastStudentHomework();
    expect(JSON.stringify(homework) === JSON.stringify(homeworkBefore)).toBe(true);
    expect(JSON.stringify(history) === JSON.stringify(historyBefore)).toBe(true);
    expect(JSON.stringify(personal) === JSON.stringify(studentBefore)).toBe(true);
    expect(historical.bindingRetained()).toBe(true);
  }
  await expectPastHomeworkRetained();

  const slot = page.locator('.headman-schedule__slot').filter({ hasText: room });
  await slot.click();
  const remove = page.getByRole('button', { name: 'Удалить пару', exact: true });
  const previewResponse = http(page, `${itemPath}/lifecycle-preview`, 'POST');
  await remove.click();
  const preview = await previewResponse;
  expect(preview.status()).toBe(200);
  expect(new URL(preview.url()).search === '?delete=true').toBe(true);
  const proposed = await preview.json();
  expect(proposed.removedCount === future.length).toBe(true);
  const confirmation = page.getByRole('dialog', {
    name: 'Удалить пару из конструктора?',
    exact: true,
  });
  await expect(confirmation).toBeVisible();
  await expect(confirmation.getByRole('status')).toContainText(String(future.length));
  await confirmation.getByRole('button', { name: 'Отмена', exact: true }).press('Escape');
  await expect(confirmation).not.toBeVisible();
  await expect(remove).toBeFocused();
  expect((await dataset.headmanActor.call('GET', itemPath)).active).toBe(true);
  expect(
    identities((await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id)) ===
      identities(own),
  ).toBe(true);

  // A helper grant never confers builder authority; another actual HEADMAN is
  // still outside this group. Valid preview revision/key avoid malformed denials.
  const helper = await admit(dataset.request, dataset.assistant, 'STUDENT');
  await dataset.admin.call('PUT', `/api/academic/groups/${dataset.otherGroup.id}/headman`, {
    studentId: dataset.foreignStudent.id,
    expectedHeadmanId: null,
  });
  const foreign = await admit(dataset.request, dataset.foreignStudent, 'HEADMAN');
  for (const actor of [helper, foreign]) {
    expect(
      (
        await dataset.request.post(`${itemPath}/lifecycle-preview?delete=true`, {
          headers: actor.headers(),
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await dataset.request.delete(itemPath, {
          headers: {
            ...actor.headers(),
            'Idempotency-Key': randomUUID(),
            'If-Match': proposed.revision,
          },
        })
      ).status(),
    ).toBe(403);
  }
  expect((await dataset.headmanActor.call('GET', itemPath)).active).toBe(true);
  expect(
    identities((await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id)) ===
      identities(own),
  ).toBe(true);

  const recalculated = http(page, `${itemPath}/lifecycle-preview`, 'POST');
  await remove.click();
  expect((await recalculated).status()).toBe(200);
  const deleted = http(page, itemPath, 'DELETE');
  await confirmation.getByRole('button', { name: 'Удалить пару', exact: true }).click();
  const deletedResponse = await deleted;
  expect(deletedResponse.status()).toBe(204);
  expect(/^[0-9a-f-]{36}$/.test(deletedResponse.request().headers()['idempotency-key'] ?? '')).toBe(
    true,
  );
  await expect(confirmation).not.toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Слот деактивирован.' })).toBeVisible();
  expect((await dataset.headmanActor.call('GET', itemPath)).active).toBe(false);
  const stopped = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(identities(stopped.filter((row) => row.date < dataset.today)) === pastIdentity).toBe(true);
  await expectPastHomeworkRetained();
  const stoppedFuture = stopped.filter((row) => row.date >= dataset.today);
  expect(
    stoppedFuture.length === future.length &&
      stoppedFuture.every(
        (row) =>
          row.status === 'CANCELLED' &&
          future.some(
            (old) =>
              old.id === row.id &&
              old.occurrenceId === row.occurrenceId &&
              old.generation === row.generation,
          ),
      ),
  ).toBe(true);
  await expect(slot).toHaveCount(0);
  await page.getByRole('button', { name: 'Добавить пару', exact: true }).click();
  await fillSlot(
    page,
    dataset,
    weeks.map((week) => week.label),
    room,
  );
  const returned = http(page, '/api/schedule/items', 'POST');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  const returnedResponse = await returned;
  expect(returnedResponse.status()).toBe(201);
  const returnedItem = await returnedResponse.json();
  expect(returnedItem.id === item.id && returnedItem.active === true).toBe(true);
  await expect(slot).toBeVisible();
  const resumed = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(resumed.length === own.length).toBe(true);
  expect(identities(resumed.filter((row) => row.date < dataset.today)) === pastIdentity).toBe(true);
  await expectPastHomeworkRetained();
  expect(
    resumed
      .filter((row) => row.date >= dataset.today)
      .every((row) => {
        const old = future.find((candidate) => candidate.occurrenceId === row.occurrenceId);
        return (
          old &&
          row.id !== old.id &&
          row.generation === old.generation + 1 &&
          row.status === 'PLANNED' &&
          row.current
        );
      }),
  ).toBe(true);

  // UI parity removal/return uses the server's labels and mapping, with no
  // independent parity/calendar formula or local-PG rule replay.
  await slot.click();
  const otherWeek = page
    .getByRole('group', { name: 'Учебные недели', exact: true })
    .getByRole('button', {
      name: new RegExp(`^${weeks[1]!.label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`),
    });
  await otherWeek.click();
  const narrowed = http(page, itemPath, 'PUT');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  expect((await narrowed).status()).toBe(200);
  await expect(slot).toBeVisible();
  expect((await dataset.headmanActor.call('GET', itemPath)).weekType === weeks[0]!.weekType).toBe(
    true,
  );
  const half = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(half.some((row) => row.date >= dataset.today && row.status === 'CANCELLED')).toBe(true);
  expect(half.some((row) => row.date >= dataset.today && row.status === 'PLANNED')).toBe(true);
  await slot.click();
  await otherWeek.click();
  const both = http(page, itemPath, 'PUT');
  await page.getByRole('button', { name: 'Сохранить пару', exact: true }).click();
  expect((await both).status()).toBe(200);
  await expect(slot).toBeVisible();
  expect((await dataset.headmanActor.call('GET', itemPath)).weekType).toBe('ALL');
  const allAgain = (await physicalLessons(dataset)).filter((row) => row.scheduleItemId === item.id);
  expect(
    allAgain.length === own.length &&
      identities(allAgain.filter((row) => row.date < dataset.today)) === pastIdentity,
  ).toBe(true);
  expect(
    allAgain
      .filter((row) => row.date >= dataset.today)
      .every((row) => {
        const old = half.find((candidate) => candidate.occurrenceId === row.occurrenceId);
        return (
          old &&
          row.status === 'PLANNED' &&
          row.current &&
          (old.status === 'CANCELLED'
            ? row.id !== old.id && row.generation === old.generation + 1
            : row.id === old.id && row.generation === old.generation)
        );
      }),
  ).toBe(true);
  const retained = await dataset.headmanActor.call('GET', reportPath);
  expect(
    JSON.stringify(
      retained.entries.find((entry: { userId: number }) => entry.userId === dataset.student.id),
    ) === JSON.stringify(savedMark),
  ).toBe(true);
  await expectPastHomeworkRetained();
  // Retire only this fixture through the normal archive operation before the
  // original live-rights revocation. Immutable history remains for CI teardown.
  await dataset.headmanActor.call('DELETE', homeworkPath, undefined, [204]);
  const archivedHomework = await dataset.headmanActor.call('GET', `${homeworkPath}?archived=true`);
  expect(archivedHomework.archived === true && archivedHomework.id === historical.homeworkId).toBe(
    true,
  );

  // Revocation after a real preview cannot turn a retained editor into authority.
  await slot.click();
  const finalPreview = http(page, `${itemPath}/lifecycle-preview`, 'POST');
  await remove.click();
  expect((await finalPreview).status()).toBe(200);
  await expect(confirmation).toBeVisible();
  await dataset.admin.call('PUT', `/api/academic/groups/${dataset.group.id}/headman`, {
    studentId: dataset.student.id,
    expectedHeadmanId: dataset.headman.id,
  });
  const refused = http(page, itemPath, 'DELETE');
  await confirmation.getByRole('button', { name: 'Удалить пару', exact: true }).click();
  expect((await refused).status()).toBe(403);
  await expect(confirmation).not.toBeVisible();
  expect((await dataset.admin.call('GET', itemPath)).active).toBe(true);
  expect(
    identities(
      (await physicalLessons(dataset, dataset.admin)).filter(
        (row) => row.scheduleItemId === item.id,
      ),
    ) === identities(allAgain),
  ).toBe(true);
});

// The ready conditional route is the semester BFF. Today is the existing Vue
// room consumer; a scoped HTTP PATCH is the accepted cache-invalidation trigger.
test('STUDENT warm semester ETag stays scoped and refreshes the same physical room in Today @real-mobile', async ({
  page,
  scenario: dataset,
}) => {
  // This existing student's otherGroup is its own genuinely empty scope.
  // The two response faults exercise the unchanged Today retry:1 client path,
  // not a Schedule outage; the manual retry below returns to the real server.
  const todayPath = '/api/v1/student/today';
  const [emptyTodayResponse, emptyScopeResponse, emptyAdmission] = await Promise.all([
    http(page, todayPath, 'GET'),
    http(page, '/api/v1/student/session', 'GET'),
    loginPwa(page, dataset.foreignStudent),
  ]);
  expect(
    emptyAdmission.session.activeRole === 'STUDENT' &&
      String(emptyAdmission.session.userId) === String(dataset.foreignStudent.id),
  ).toBe(true);
  expect(emptyScopeResponse.status()).toBe(200);
  const emptyScope = await emptyScopeResponse.json();
  expect(
    String(emptyScope.user.id) === String(dataset.foreignStudent.id) &&
      String(emptyScope.group.id) === String(dataset.otherGroup.id) &&
      String(emptyScope.semester.id) === String(dataset.semester.id),
  ).toBe(true);
  expect(emptyTodayResponse.status()).toBe(200);
  const emptyToday = await emptyTodayResponse.json();
  expect(
    emptyToday.date === dataset.today &&
      emptyToday.timeZone === 'Europe/Moscow' &&
      emptyToday.lessons.length === 0,
  ).toBe(true);
  await expect(
    page.getByRole('heading', { name: 'На сегодня пар нет', exact: true }),
  ).toBeVisible();
  await expect(page.locator('.today-list .student-lesson')).toHaveCount(0);
  const todayUrl = new URL(todayPath, page.url()).href;
  let rejectedTodayReads = 0;
  const failToday: Parameters<typeof page.route>[1] = async (route) => {
    if (route.request().method() !== 'GET' || rejectedTodayReads >= 2) {
      await route.continue();
      return;
    }
    rejectedTodayReads += 1;
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      headers: { 'cache-control': 'no-store' },
      json: {
        type: 'urn:rct:problem:dependency-unavailable',
        title: 'Service Unavailable',
        status: 503,
        code: 'DEPENDENCY_UNAVAILABLE',
        detail: 'Синтетическая ошибка чтения расписания.',
      },
    });
  };
  await page.route(todayUrl, failToday);
  try {
    const [failed] = await Promise.all([
      http(page, todayPath, 'GET'),
      page.getByRole('button', { name: 'Обновить', exact: true }).click(),
    ]);
    expect(failed.status()).toBe(503);
    const error = page.getByRole('alert').filter({
      has: page.getByRole('heading', { name: 'Не удалось получить данные', exact: true }),
    });
    await expect(error).toBeVisible();
    await expect(error.getByRole('button', { name: 'Повторить', exact: true })).toBeEnabled();
    expect(rejectedTodayReads).toBe(2);
    await expect(
      page.getByRole('heading', { name: 'На сегодня пар нет', exact: true }),
    ).toHaveCount(0);
  } finally {
    await page.unroute(todayUrl, failToday);
  }
  const [recoveredTodayResponse] = await Promise.all([
    http(page, todayPath, 'GET'),
    page.getByRole('alert').getByRole('button', { name: 'Повторить', exact: true }).click(),
  ]);
  expect(recoveredTodayResponse.status()).toBe(200);
  const recoveredToday = await recoveredTodayResponse.json();
  expect(
    recoveredToday.date === dataset.today &&
      recoveredToday.timeZone === 'Europe/Moscow' &&
      recoveredToday.lessons.length === 0,
  ).toBe(true);
  expect(rejectedTodayReads).toBe(2);
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(
    page.getByRole('heading', { name: 'На сегодня пар нет', exact: true }),
  ).toBeVisible();
  await expect(page.locator('.today-list .student-lesson')).toHaveCount(0);
  await logoutPwa(page);
  expect((await page.context().cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(
    false,
  );

  const oldRoom = 'Синтетическая кэш-аудитория А';
  const newRoom = 'Синтетическая кэш-аудитория Б';
  const physical = await dataset.headmanActor.call(
    'POST',
    '/api/schedule/one-off-lessons',
    {
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      assignmentId: dataset.subject.assignments[0].id,
      date: dataset.today,
      lessonNumber: 8,
      classroom: oldRoom,
    },
    [201],
    { 'Idempotency-Key': randomUUID() },
  );
  // Settle the existing 09:00 teaching-clock transition before warming ETags;
  // a still-PLANNED current lesson would make both the hero and cache unstable.
  await expect
    .poll(
      async () =>
        (await physicalLessons(dataset)).some(
          (row) => row.id === dataset.lesson.physicalLessonId && row.status === 'ACTIVE',
        ),
      {
        timeout: 75000,
        message: 'Own Today lesson becomes active through the normal educational job',
      },
    )
    .toBe(true);
  const admitted = await loginPwa(page, dataset.student);
  const student = new Actor(dataset.request, admitted.accessToken, admitted.session);
  expect(admitted.session.activeRole).toBe('STUDENT');
  await expect(page.getByText(oldRoom, { exact: true })).toBeVisible();
  const schedulePath = `/api/v1/student/schedule?semesterId=${dataset.semester.id}`;
  const warm = await dataset.request.get(schedulePath, { headers: student.headers() });
  expect(warm.status()).toBe(200);
  const etag = warm.headers().etag;
  expect(typeof etag === 'string' && etag.length > 0).toBe(true);
  expect(warm.headers()['cache-control']).toContain('private');
  const before = await warm.json();
  const projected = before.lessons.filter(
    (row: { id: string }) => row.id === String(physical.physicalLessonId),
  );
  expect(projected.length === 1 && projected[0].room.current === oldRoom).toBe(true);
  const conditional = await dataset.request.get(schedulePath, {
    headers: { ...student.headers(), 'If-None-Match': etag! },
  });
  expect(conditional.status()).toBe(304);
  expect(conditional.headers().etag === etag).toBe(true);
  const browserWarm = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname + new URL(response.url()).search === schedulePath,
  );
  await page.reload();
  const browserConditional = await browserWarm;
  expect(browserConditional.status()).toBe(304);
  expect(browserConditional.request().headers()['if-none-match'] === etag).toBe(true);
  await expect(page.getByText(oldRoom, { exact: true })).toBeVisible();

  const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
  const isolated = await dataset.request.get(schedulePath, {
    headers: { ...foreign.headers(), 'If-None-Match': etag! },
  });
  expect(isolated.status()).toBe(200);
  const foreignBody = await isolated.json();
  const foreignEtag = isolated.headers().etag;
  expect(
    foreignBody.lessons.length === 0 && typeof foreignEtag === 'string' && foreignEtag !== etag,
  ).toBe(true);
  expect(
    (
      await dataset.request.get(schedulePath, {
        headers: { ...student.headers(), 'If-None-Match': foreignEtag! },
      })
    ).status(),
  ).toBe(200);
  const original = (await physicalLessons(dataset)).find(
    (row) => row.id === physical.physicalLessonId,
  )!;
  expect(original.current && original.status === 'PLANNED' && original.occurrenceId > 0).toBe(true);
  const roomPath = `/api/schedule/lessons/${original.id}/room`;
  const mutation = { room: newRoom, expectedRevision: String(original.occurrenceRevision) };
  expect(
    (
      await dataset.request.patch(roomPath, {
        headers: { ...foreign.headers(), 'Idempotency-Key': randomUUID() },
        data: mutation,
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await dataset.request.patch(roomPath, {
        headers: { ...student.headers(), 'Idempotency-Key': randomUUID() },
        data: mutation,
      })
    ).status(),
  ).toBe(403);
  expect(
    identities([(await physicalLessons(dataset)).find((row) => row.id === original.id)!]) ===
      identities([original]),
  ).toBe(true);
  expect(
    (
      await dataset.request.get(schedulePath, {
        headers: { ...student.headers(), 'If-None-Match': etag! },
      })
    ).status(),
  ).toBe(304);
  const changed = await dataset.headmanActor.call('PATCH', roomPath, mutation, [200], {
    'Idempotency-Key': randomUUID(),
  });
  expect(
    changed.state === 'COMPLETED' &&
      changed.lessonId === String(original.id) &&
      changed.occurrenceId === String(original.occurrenceId) &&
      changed.room === newRoom,
  ).toBe(true);
  const samePhysical = (await physicalLessons(dataset)).find((row) => row.id === original.id)!;
  expect(
    samePhysical.occurrenceId === original.occurrenceId &&
      samePhysical.generation === original.generation &&
      samePhysical.room === newRoom &&
      samePhysical.occurrenceRevision > original.occurrenceRevision &&
      changed.revision === String(samePhysical.occurrenceRevision),
  ).toBe(true);
  const fresh = await dataset.request.get(schedulePath, {
    headers: { ...student.headers(), 'If-None-Match': etag! },
  });
  expect(fresh.status()).toBe(200);
  const newEtag = fresh.headers().etag;
  expect(typeof newEtag === 'string' && newEtag !== etag).toBe(true);
  const updated = await fresh.json();
  expect(updated.lessons.length === before.lessons.length).toBe(true);
  const changedProjection = updated.lessons.filter(
    (row: { id: string }) => row.id === String(original.id),
  );
  expect(
    changedProjection.length === 1 &&
      changedProjection[0].room.current === newRoom &&
      changedProjection[0].room.previous === oldRoom,
  ).toBe(true);
  const browserRefresh = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname + new URL(response.url()).search === schedulePath,
  );
  await page.reload();
  const current = await browserRefresh;
  expect(current.status()).toBe(200);
  expect(
    current.request().headers()['if-none-match'] === etag && current.headers().etag === newEtag,
  ).toBe(true);
  const card = page
    .locator('.student-lesson__card')
    .filter({ has: page.getByText(newRoom, { exact: true }) });
  await expect(card).toBeVisible();
  await expect(
    card.getByRole('heading', { name: dataset.subject.name, exact: true }),
  ).toBeVisible();
  await expect(card.locator('.student-lesson__room--changed')).toHaveText(newRoom);
  await expect(card.getByLabel(`Прежняя аудитория: ${oldRoom}`, { exact: true })).toHaveText(
    oldRoom,
  );
  expect(
    (
      await dataset.request.get(schedulePath, {
        headers: { ...student.headers(), 'If-None-Match': newEtag! },
      })
    ).status(),
  ).toBe(304);
});
