import { expect, errors as playwrightErrors, type Page, type Locator } from '@playwright/test';
import type { Actor } from './mobile-dataset';
type Row = Record<string, any>;
function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) {
    const error = new Error(message);
    Error.captureStackTrace(error, requireValue);
    throw error;
  }
}

export async function terminal(
  admin: Actor,
  semesterId: number,
  action: 'ARCHIVE' | 'RESTORE',
  key: string,
  progress: Row,
  timeout = 120000,
  acceptedOperation?: Row,
  observeSemester: (progress: Row, value: Row) => void = () => {},
) {
  progress.action = action;
  progress.stage = 'COMMAND';
  const path = `/api/academic/semesters/${semesterId}/${action === 'ARCHIVE' ? 'archive' : 'restore'}`;
  const operation =
    acceptedOperation ??
    (await admin.call('POST', path, undefined, [200, 202, 503], {
      'Idempotency-Key': key,
    }));
  observeSemester(progress, operation);
  requireValue(
    typeof operation.operationId === 'string',
    'Semester command must retain its operation identity',
  );
  let status: Row = {};
  progress.stage = 'PARTICIPANT_BARRIER';
  await expect
    .poll(
      async () => {
        status = await admin.call('GET', `/api/academic/semesters/${semesterId}/archive/status`);
        observeSemester(progress, status);
        requireValue(
          status.operation?.operationId === operation.operationId &&
            status.operation.action === action,
          'Semester participant operation identity changed',
        );
        requireValue(
          status.operation.operationState !== 'ERROR' || status.operation.retryable === true,
          'Semester participant operation failed terminally',
        );
        return status.operation.operationState === 'COMPLETED';
      },
      { timeout, message: 'Real semester participant barrier completes' },
    )
    .toBe(true);
  const participant = action === 'ARCHIVE' ? 'READY' : 'RELEASED';
  progress.stage = 'SETTLED_READBACK';
  requireValue(
    ['academic', 'schedule', 'attendance'].every((name) => status.operation[name] === participant),
    'All three real semester participant receipts are required',
  );
  requireValue(
    status.active === false && status.transition === 'NONE' && status.releasePending === false,
    'Semester transition must settle without activation',
  );
  requireValue(
    status.archived === (action === 'ARCHIVE') && status.writeBlocked === (action === 'ARCHIVE'),
    'Semester archive write gate differs from the accepted state',
  );
  const semester = await admin.call('GET', `/api/academic/semesters/${semesterId}`);
  requireValue(
    Number(status.semesterId) === semesterId &&
      Number(status.operation.semesterId) === semesterId &&
      Number(semester.id) === semesterId &&
      semester.active === false &&
      semester.activationScheduled === false &&
      semester.archived === (action === 'ARCHIVE'),
    'Settled semester identity and inactive authority must agree',
  );
  progress.stage = 'REPLAY';
  const replay = await admin.call('POST', path, undefined, [200], { 'Idempotency-Key': key });
  requireValue(
    replay.operationId === operation.operationId &&
      replay.operationState === 'COMPLETED' &&
      replay.stateVersion === status.operation.stateVersion,
    'Semester command replay must retain the completed operation',
  );
  progress.stage = 'COMPLETED';
  return status;
}

export async function assertOwnedSemesterUi(
  screen: Locator, page: Page, semesterName: string, ownCard: Locator,
  action: 'ARCHIVE' | 'RESTORE', uiBudget: (cap?: number) => number,
  assertion: typeof expect = expect,
) {
  const statusCard = screen.locator('.admin-semester-archive-status article').filter({
    has: page.getByRole('heading', {
      level: 3,
      name: String(semesterName),
      exact: true,
    }),
  });
  const continueCheck = statusCard.getByRole('button', {
    name: 'Продолжить проверку',
    exact: true,
  });
  if (await continueCheck.isVisible()) {
    try {
      await continueCheck.click({ timeout: uiBudget() });
    } catch (error) {
      if (
        !(error instanceof playwrightErrors.TimeoutError) ||
        (await continueCheck.count()) !== 0
      )
        throw error;
      // The UI poll can remove this button after terminal() verified this exact
      // operation. Accept that race only if the owned terminal UI below settles.
    }
  }
  // The status card is transient: automatic settlement may remove it after
  // isVisible(). Completion still requires this exact owned semester card.
  await assertion(ownCard).toHaveCount(1, { timeout: uiBudget() });
  await assertion(
    ownCard.getByRole('img', {
      name: action === 'ARCHIVE' ? 'Архивный' : 'Неактивный',
      exact: true,
    }),
  ).toBeVisible({ timeout: uiBudget(15000) });
  await assertion(
    ownCard.getByRole('button', {
      name: action === 'ARCHIVE' ? 'Восстановить' : 'Архивировать',
      exact: true,
    }),
  ).toBeEnabled({ timeout: uiBudget(15000) });
}
