import type { Locator, Page, TestInfo } from '@playwright/test';

export const failedCases = {
  TMA_SIGNED_ENTRY: { lane: 'auth-smoke', title: 'signed teacher entry loads the real session and shared profile with host Back' },
  TMA_TAMPERED_ENTRY: { lane: 'auth-smoke', title: 'tampered Telegram proof cannot expose role data or navigation' },
  HEADMAN_TEACHER_READ: { lane: 'first3', title: 'HEADMAN marks the real roster and TEACHER reads without acquiring write permission' },
  ADMIN_HISTORICAL_MARK: { lane: 'admin', title: 'ADMIN creates a multirole headman and restores the archived account without old authority' },
  FULL_ADMIN_RIGHTS: { lane: 'role-security-export', title: 'FULL multirole ADMIN assigns updates and revokes LIMITED rights through Vue without foreign-role calls @cloud-accepted' },
  OWN_PROFILE_THEME: { lane: 'role-security-export', title: 'own profile security changes password and replacement login rejects old authority @cloud-accepted' },
  HOMEWORK_PERIOD: { lane: 'homework', title: 'STUDENT multi-item feed keeps focus and rolls back a failed completion with safe external materials @real-mobile' },
  SEMESTER_METADATA: { lane: 'semester-metadata', title: 'FULL admin manages owned semester metadata and historical group promotion through the ready UI' },
  HOMEWORK_NOTIFICATION: { lane: 'homework', title: 'MANAGE_HOMEWORK non-author edits preserve student completion and revoked authority cannot overwrite it @real-mobile' },
  HELPER_PERMISSIONS: { lane: 'helper', title: 'HEADMAN edits helper capabilities through member row and retains VIEW_STATS @cloud-accepted' },
} as const;
export type FailedCaseId = keyof typeof failedCases;
export function failedCaseId(title: string): FailedCaseId | undefined {
  return (Object.keys(failedCases) as FailedCaseId[]).find(id => failedCases[id].title === title);
}

const annotationType = 'rct-first-failure-observation-v1';
const stages = {
  TMA_AUTH: ['httpStatus', 'sessionStatus'],
  TMA_SESSION: ['httpStatus', 'loginStatus'],
  TEACHER_LOGIN: ['actor'],
  PWA_LOGIN: ['loginStatus', 'sessionStatus'],
  PWA_SESSION: ['loginStatus', 'sessionStatus'],
  PWA_READY: ['loginStatus', 'sessionStatus'],
  HISTORICAL_MARK: ['httpStatus', 'expectedStatus', 'expectedRevisionPresent'],
  ADMIN_ACTIVITY: ['httpStatus', 'jsonDecoded', 'activityReady', 'periodMatches', 'bucketMatches', 'metricStateKnown', 'geoCountsKnown', 'metricStateMatches', 'metricValueMatches', 'metricFractionMatches', 'metricDeltaMatches', 'selectedPeriodMatches'],
  HOMEWORK_MONTH: ['httpStatus', 'selectedDatePresent', 'fromContainsSelected', 'toContainsSelected', 'fromInsideSemester', 'toInsideSemester', 'semesterIdMatches', 'calendarReady', 'selectedDateEnabled'],
  HOMEWORK_WEEK: ['httpStatus', 'otherRangeResponseObserved', 'responseObserved', 'jsonDecoded', 'responseMatchesRequest', 'fromContainsToday', 'toContainsToday', 'fromInsideSemester', 'toInsideSemester', 'spanWithinWeek', 'semesterIdMatches'],
  TEACHER_SHORTCUT: ['requestObserved', 'requestFailed', 'httpStatus', 'semesterMatches', 'groupMatches', 'subjectMatches', 'lessonTypeMatches', 'pastOnlyMatches', 'pageMatches', 'pageSizeMatches', 'responseObserved', 'journalVisible', 'shortcutVisible', 'clickStarted', 'clickCompleted', 'clickPending', 'clickErrorClass', 'clickLogReason', 'clickRawRetention', 'clickSettledAfterFailure', 'clickCompletedAfterFailure', 'beforeCount', 'beforeVisible', 'beforeEnabled', 'beforeReceivesEvents', 'afterCount', 'afterVisible', 'afterEnabled', 'afterReceivesEvents', 'homeVisible', 'navigationDetail', 'activeTeacherRole'],
  SEMESTER_METADATA_ISOLATION: ['semesterIdEqual', 'digestEqual', 'stateVersionEqual', 'priorStateEqual', 'countsEqual', 'scheduleTemplatesEqual', 'oneOffLessonsEqual', 'lessonsEqual', 'assignmentsEqual', 'homeworksEqual', 'attendanceMarksEqual', 'studentRequestsEqual'],
  ADMIN_RIGHTS_MODE: ['limitedCount', 'fullCount', 'limitedChecked', 'fullChecked'],
  PROFILE_THEME: ['selectedTheme', 'appearanceTheme', 'htmlTheme', 'profileTheme'],
  NOTIFICATION_CHECKBOX: ['count', 'connected', 'visible', 'enabled', 'checked', 'inViewport', 'receivesEvents', 'inputKind', 'desired'],
  HELPER_REQUEST: ['hasPermissions', 'hasExpectedVersion', 'hasExpectedPermissions', 'knownKeysOnly', 'extraKeyCount', 'permissionArray', 'knownPermissions', 'unknownPermissions', 'permissionsEqual', 'expectedPermissionsArray', 'knownExpectedPermissions', 'unknownExpectedPermissions', 'expectedPermissionsEqual'],
} as const;
type Stage = keyof typeof stages;
const permissions = ['MARK_ATTENDANCE', 'MANAGE_EXCUSES', 'MANAGE_HOMEWORK', 'CANCEL_LESSONS', 'VIEW_STATS'];
const themes = ['light', 'dark', 'system', 'ABSENT', 'UNKNOWN'];
type Primitive = string | number | boolean | null | string[];
export type FirstFailureObservation = { caseId: FailedCaseId; stage: Stage; facts: Record<string, Primitive> };

function data(value: unknown): Record<string, unknown> | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return;
  const descriptors = Object.getOwnPropertyDescriptors(value);
  if (Reflect.ownKeys(descriptors).some(key => typeof key !== 'string' || !('value' in descriptors[key]))) return;
  return Object.fromEntries(Object.entries(descriptors).map(([key, descriptor]) => [key, descriptor.value]));
}
function closed(value: unknown): FirstFailureObservation | undefined {
  try {
    const row = data(value);
    if (!row || Object.keys(row).length !== 3 || typeof row.caseId !== 'string' || typeof row.stage !== 'string'
      || !Object.hasOwn(failedCases, row.caseId) || !Object.hasOwn(stages, row.stage)) return;
    const caseId = row.caseId as FailedCaseId, stage = row.stage as Stage;
    const facts = data(row.facts), keys = stages[stage] as readonly string[];
    if (!facts || Object.keys(facts).length !== keys.length || Object.keys(facts).some(key => !keys.includes(key))) return;
    const safe: Record<string, Primitive> = {};
    for (const key of keys) {
      const item = facts[key];
      let valid: boolean;
      if (key === 'knownPermissions' || key === 'knownExpectedPermissions') valid = Array.isArray(item) && item.length <= 5 && new Set(item).size === item.length && item.every(code => typeof code === 'string' && permissions.includes(code));
      else if (key === 'beforeCount' || key === 'afterCount') valid = item === null || Number.isSafeInteger(item) && (item as number) >= 0 && (item as number) <= 10000;
      else if (key === 'clickLogReason') valid = typeof item === 'string' && ['NONE', 'POINTER_INTERCEPTION_SEEN', 'NOT_VISIBLE_SEEN', 'NOT_ENABLED_SEEN', 'NOT_STABLE_SEEN', 'DETACHED_SEEN', 'MULTIPLE_REASONS_SEEN', 'UNKNOWN'].includes(item);
      else if (key === 'clickRawRetention') valid = typeof item === 'string' && ['NOT_ATTEMPTED', 'OFF', 'RETAINED', 'ALREADY_RETAINED', 'REJECTED', 'FAILED'].includes(item);
      else if (key === 'clickErrorClass') valid = typeof item === 'string' && ['NONE', 'TIMEOUT', 'ERROR', 'TYPE_ERROR', 'UNKNOWN'].includes(item);
      else if (key.endsWith('Count') || key === 'count') valid = Number.isSafeInteger(item) && (item as number) >= 0 && (item as number) <= 10000;
      else if (['httpStatus', 'loginStatus', 'sessionStatus'].includes(key)) valid = item === null || Number.isInteger(item) && (item as number) >= 100 && (item as number) <= 599;
      else if (key.endsWith('Theme')) valid = typeof item === 'string' && themes.includes(item);
      else if (key === 'expectedStatus') valid = typeof item === 'string' && ['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED', 'LATE', 'UNKNOWN'].includes(item);
      else if (key === 'inputKind') valid = typeof item === 'string' && ['CHECKBOX', 'RADIO', 'BUTTON', 'OTHER', 'UNKNOWN'].includes(item);
      else if (key === 'actor') valid = item === 'TEACHER';
      else valid = item === null || typeof item === 'boolean';
      if (!valid) return;
      safe[key] = Array.isArray(item) ? [...item] : item as Primitive;
    }
    return { caseId, stage, facts: safe };
  } catch { return; }
}

export function safeFirstFailureObservations(annotations: unknown): FirstFailureObservation[] {
  const result: FirstFailureObservation[] = [];
  try {
    if (!Array.isArray(annotations)) return result;
    for (let index = 0; index < Math.min(annotations.length, 32); index++) {
      try {
        const raw = Object.getOwnPropertyDescriptor(annotations, String(index))?.value;
        const entry = data(raw);
        if (entry?.type !== annotationType || typeof entry.description !== 'string' || entry.description.length > 2048) continue;
        const row = closed(JSON.parse(entry.description));
        if (row && !result.some(old => old.caseId === row.caseId && old.stage === row.stage)) result.push(row);
      } catch { /* Untrusted annotations never become raw evidence. */ }
    }
  } catch { /* A hostile annotation container is not evidence. */ }
  return result.slice(0, 8);
}
export function retainFirstFailure(info: Pick<TestInfo, 'annotations' | 'title'>, stage: Stage, facts: unknown): void {
  try {
    const caseId = failedCaseId(info.title);
    const row = closed({ caseId, stage, facts });
    if (!row || info.annotations.length >= 32 || safeFirstFailureObservations(info.annotations).some(old => old.caseId === caseId && old.stage === stage)) return;
    info.annotations.push({ type: annotationType, description: JSON.stringify(row) });
  } catch { /* Observation cannot replace the original failure. */ }
}

/** Handle eager dependent rejection immediately, while returning the original promise. */
export function handledWaiter<T>(promise: Promise<T>): Promise<T> {
  void promise.catch(() => {});
  return promise;
}
export async function failureBoundary<T>(info: Pick<TestInfo, 'annotations' | 'title'>, stage: Stage,
  action: () => Promise<T>, observe: () => unknown | Promise<unknown>): Promise<T> {
  try { return await action(); }
  catch (primary) {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const facts = await Promise.race([Promise.resolve().then(observe), new Promise<null>(resolve => { timer = setTimeout(() => resolve(null), 300); })]);
      retainFirstFailure(info, stage, facts);
    } catch { /* Closed unavailable observation; preserve primary identity and outcome. */ }
    finally { if (timer) clearTimeout(timer); }
    throw primary;
  }
}

export async function controlState(locator: Locator) {
  return locator.evaluateAll(elements => {
    const element = elements.length === 1 ? elements[0] as HTMLElement : null;
    const input = element instanceof HTMLInputElement ? element : null;
    const rect = element?.getBoundingClientRect(), style = element ? getComputedStyle(element) : null;
    const centerX = rect ? rect.left + rect.width / 2 : -1, centerY = rect ? rect.top + rect.height / 2 : -1;
    const hit = element && rect ? document.elementFromPoint(centerX, centerY) : null;
    return { count: Math.min(elements.length, 10000), connected: element?.isConnected ?? null,
      visible: rect && style ? rect.width > 0 && rect.height > 0 && !['hidden', 'collapse'].includes(style.visibility) : null,
      enabled: element ? !element.matches(':disabled') && !element.closest('[aria-disabled="true"]') : null,
      checked: input ? input.checked : null,
      inViewport: rect ? rect.left >= 0 && rect.top >= 0 && rect.right <= innerWidth && rect.bottom <= innerHeight : null,
      receivesEvents: element && hit ? element === hit || element.contains(hit) : null,
      inputKind: input?.type === 'checkbox' ? 'CHECKBOX' : input?.type === 'radio' ? 'RADIO' : element?.tagName === 'BUTTON' ? 'BUTTON' : element ? 'OTHER' : 'UNKNOWN' };
  });
}
export async function themeState(page: Page) {
  return page.evaluate(() => {
    const known = (value: string | null | undefined) => value == null ? 'ABSENT' : ['light', 'dark', 'system'].includes(value) ? value : 'UNKNOWN';
    const selected = document.querySelectorAll('.profile-appearance input[type="radio"]:checked');
    return { selectedTheme: known(selected.length === 1 && selected[0] instanceof HTMLInputElement ? selected[0].value : null),
      appearanceTheme: known(document.querySelector('.profile-appearance')?.getAttribute('data-theme')),
      htmlTheme: known(document.documentElement.getAttribute('data-theme')),
      profileTheme: known(document.querySelector('.profile-page')?.getAttribute('data-theme')) };
  });
}
export function helperRequestState(value: unknown, expectedPermissions: readonly string[]) {
  const row = data(value), supplied = row?.permissions, expected = row?.expectedPermissions;
  const array = Array.isArray(supplied), known = array ? supplied.filter(code => typeof code === 'string' && permissions.includes(code)) as string[] : [];
  const expectedArray = Array.isArray(expected), knownExpected = expectedArray ? expected.filter(code => typeof code === 'string' && permissions.includes(code)) as string[] : [];
  return { hasPermissions: !!row && Object.hasOwn(row, 'permissions'), hasExpectedVersion: !!row && Object.hasOwn(row, 'expectedVersion'),
    hasExpectedPermissions: !!row && Object.hasOwn(row, 'expectedPermissions'),
    knownKeysOnly: !!row && Object.keys(row).every(key => ['permissions', 'expectedPermissions'].includes(key)),
    extraKeyCount: row ? Math.min(Object.keys(row).filter(key => !['permissions', 'expectedPermissions'].includes(key)).length, 10000) : 0,
    permissionArray: array, knownPermissions: [...new Set(known)].sort(),
    unknownPermissions: array && supplied.some(code => typeof code !== 'string' || !permissions.includes(code)),
    permissionsEqual: array && supplied.length === 1 && supplied[0] === 'VIEW_STATS',
    expectedPermissionsArray: expectedArray, knownExpectedPermissions: [...new Set(knownExpected)].sort(),
    unknownExpectedPermissions: expectedArray && expected.some(code => typeof code !== 'string' || !permissions.includes(code)),
    expectedPermissionsEqual: expectedArray && expected.length === expectedPermissions.length
      && new Set(expected).size === expected.length && [...expected].sort().every((code, index) => code === [...expectedPermissions].sort()[index]) };
}

/** Closed class only: never read or export exception message/stack or invoke a name getter. */
export function teacherShortcutClickErrorClass(error: unknown): 'TIMEOUT' | 'ERROR' | 'TYPE_ERROR' | 'UNKNOWN' {
  try {
    const name = error && typeof error === 'object' ? Object.getOwnPropertyDescriptor(error, 'name')?.value : undefined;
    if (name === 'TimeoutError') return 'TIMEOUT';
    if (error instanceof TypeError) return 'TYPE_ERROR';
    if (error instanceof Error) return 'ERROR';
  } catch { /* A diagnostic class cannot replace the primary failure. */ }
  return 'UNKNOWN';
}

/** A bounded own-message call-log witness, never proof of the product root cause. */
export function teacherShortcutClickLogReason(error: unknown): 'POINTER_INTERCEPTION_SEEN' | 'NOT_VISIBLE_SEEN' | 'NOT_ENABLED_SEEN' | 'NOT_STABLE_SEEN' | 'DETACHED_SEEN' | 'MULTIPLE_REASONS_SEEN' | 'UNKNOWN' {
  try {
    const message = error && typeof error === 'object' ? Object.getOwnPropertyDescriptor(error, 'message')?.value : undefined;
    if (typeof message !== 'string' || message.length > 8192) return 'UNKNOWN';
    const marker = message.indexOf('\nCall log:\n');
    if (marker < 0) return 'UNKNOWN';
    const log = message.slice(marker + 11);
    const reasons = [
      ['POINTER_INTERCEPTION_SEEN', /intercepts pointer events/],
      ['NOT_VISIBLE_SEEN', /element is not visible/],
      ['NOT_ENABLED_SEEN', /element is not enabled/],
      ['NOT_STABLE_SEEN', /element is not stable/],
      ['DETACHED_SEEN', /element (?:is not attached to the DOM|was detached from the DOM)/],
    ] as const;
    const seen = reasons.filter(([, pattern]) => log.split('\n').some(line => /^\s*- /.test(line) && pattern.test(line)));
    return seen.length > 1 ? 'MULTIPLE_REASONS_SEEN' : seen[0]?.[0] ?? 'UNKNOWN';
  } catch { return 'UNKNOWN'; }
}

/** Read-only, bounded witnesses of the actual teacher owner and internal route surface. */
export async function teacherShortcutState(page: Page, shortcut: Locator) {
  const unavailable = { count: null as number | null, visible: null as boolean | null,
    enabled: null as boolean | null, receivesEvents: null as boolean | null,
    journalVisible: null as boolean | null, shortcutVisible: null as boolean | null,
    homeVisible: null as boolean | null, navigationDetail: null as boolean | null,
    activeTeacherRole: null as boolean | null };
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      (async () => {
        const [control, journalVisible, shortcutVisible, homeVisible, navigationDetail, activeTeacherRole] = await Promise.all([
          controlState(shortcut), page.locator('.teacher-journal').isVisible(), shortcut.isVisible(),
          page.locator('.teacher-home').isVisible(),
          page.locator('.mobile-shell[data-surface="detail"] .teacher-feature-owner').isVisible(),
          page.locator('.teacher-feature-owner').isVisible(),
        ]);
        return { count: control.count, visible: control.visible, enabled: control.enabled,
          receivesEvents: control.receivesEvents, journalVisible, shortcutVisible, homeVisible,
          navigationDetail, activeTeacherRole };
      })(),
      new Promise<typeof unavailable>(resolve => { timer = setTimeout(() => resolve(unavailable), 300); }),
    ]);
  } catch { return unavailable; }
  finally { if (timer) clearTimeout(timer); }
}
