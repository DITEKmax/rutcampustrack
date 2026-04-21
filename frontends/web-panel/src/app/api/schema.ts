/**
 * Shared type aliases over generated OpenAPI schemas.
 *
 * M07 G3b (D3): pragmatic types-only migration — Angular HttpClient runtime
 * остаётся, компоненты импортируют `components['schemas'][...]` как type
 * вместо ручных interface-копий. Здесь сконцентрированы узкие aliases,
 * которые переопределяют HATEOAS optionality (openapi-typescript помечает
 * все поля `?:` из-за отсутствия `@NotNull` на Spring response DTO),
 * чтобы Angular strictTemplates не падал на каждом биндинге.
 *
 * Инварианты:
 * - `_links` HATEOAS игнорируется.
 * - Типы описывают **runtime-контракт**, который backend действительно
 *   возвращает (все поля кроме помеченных `?:` в этом файле — всегда
 *   присутствуют).
 * - При добавлении новых endpoint'ов — расширяй этот файл, не создавай
 *   ручные interfaces в feature-папках.
 */

import type { components as AcademicComponents } from './generated/academic.types';
import type { components as AttendanceComponents } from './generated/attendance.types';
import type { components as AuthComponents } from './generated/auth.types';
import type { components as ScheduleComponents } from './generated/schedule.types';

type AcademicSchemas = AcademicComponents['schemas'];
type AttendanceSchemas = AttendanceComponents['schemas'];
type AuthSchemas = AuthComponents['schemas'];
type ScheduleSchemas = ScheduleComponents['schemas'];

/**
 * Make all listed keys required, drop `_links`. Используется для
 * сужения HATEOAS-optionality до runtime-контракта.
 */
type Strict<T, K extends keyof T> = Required<Pick<T, K>> & Omit<T, K | '_links'>;

// ------------------------- schedule-service -------------------------

export type LessonResponse = Strict<
  ScheduleSchemas['EntityModelLessonResponse'],
  | 'id'
  | 'scheduleItemId'
  | 'groupId'
  | 'subjectId'
  | 'date'
  | 'status'
  | 'dayOfWeek'
  | 'lessonNumber'
  | 'startTime'
  | 'endTime'
  | 'weekType'
  | 'room'
  | 'geoBlocked'
  | 'createdAt'
>;

export type LessonStatus = NonNullable<LessonResponse['status']>;
export type WeekType = NonNullable<LessonResponse['weekType']>;

export type OneOffLessonResponse = Strict<
  ScheduleSchemas['EntityModelOneOffLessonResponse'],
  'id' | 'groupId' | 'subjectId' | 'date' | 'lessonNumber'
>;

export type ScheduleItemResponse = Strict<
  ScheduleSchemas['EntityModelScheduleItemResponse'],
  'id' | 'groupId' | 'subjectId' | 'semesterId' | 'dayOfWeek' | 'lessonNumber' | 'startTime' | 'endTime' | 'weekType' | 'createdAt'
>;

export type CreateScheduleItemRequest = ScheduleSchemas['CreateScheduleItemRequest'];
export type UpdateScheduleItemRequest = ScheduleSchemas['UpdateScheduleItemRequest'];
export type MassCancelRequest = ScheduleSchemas['MassCancelRequest'];
export type MassCancelResponse = Required<ScheduleSchemas['MassCancelResponse']>;
export type GeoBlockRequest = ScheduleSchemas['GeoBlockRequest'];
export type CancelLessonRequest = ScheduleSchemas['CancelLessonRequest'];
export type CreateOneOffLessonRequest = ScheduleSchemas['CreateOneOffLessonRequest'];

// ------------------------- academic-service -------------------------

export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT';
export type AccountStatus = 'active' | 'expelled' | 'suspended' | 'archived';
export type SemesterStatus = 'active' | 'planned' | 'finished';

/**
 * UserResponse: backend отдаёт все поля кроме middleName/groupId/employeeNumber/
 * telegramId/avatarId/initialPassword/passwordChanged как always-present.
 * passwordChanged не в generated types — backend добавил позже (BUG-006-4).
 */
export type UserResponse = Omit<
  Strict<
    AcademicSchemas['EntityModelUserResponse'],
    'id' | 'login' | 'lastName' | 'firstName' | 'role' | 'status' | 'headman' | 'createdAt'
  >,
  'middleName' | 'groupId' | 'employeeNumber' | 'telegramId' | 'initialPassword' | 'status'
> & {
  status: AccountStatus;
  middleName: string | null;
  groupId: number | null;
  employeeNumber: string | null;
  telegramId: number | null;
  initialPassword?: string | null;
  passwordChanged?: boolean;
};

export type UserCreatedResponse = UserResponse & { initialPassword: string };

export type CreateUserRequest = Omit<
  AcademicSchemas['CreateUserRequest'],
  'role'
> & {
  role: UserRole;
};

export type PatchUserRequest = {
  lastName?: string;
  firstName?: string;
  middleName?: string;
  isHeadman?: boolean;
  groupId?: number;
  employeeNumber?: string;
  telegramId?: number;
  status?: AccountStatus;
};

export type GroupResponse = Strict<
  AcademicSchemas['EntityModelGroupResponse'],
  'id' | 'name' | 'active' | 'createdAt'
> & {
  /** BUG-006-6 / plan 58-06: момент архивации (ISO); null у активных групп. */
  archivedAt?: string | null;
};

/** BUG-006-6 / plan 58-08: lifecycle-фильтр для GET /groups?status=... */
export type GroupStatus = 'ACTIVE' | 'ARCHIVED' | 'ALL';

export interface PromotionPreviewItem {
  id: number;
  from: string;
  to?: string | null;
  action: 'PROMOTE' | 'ARCHIVE';
}

export interface PrefixConflict {
  prefix: string;
  reason: 'name_conflict' | 'unknown_type' | 'parse_error';
  message: string;
  groupIds: number[];
}

export interface PromotionSummary {
  toPromote: PromotionPreviewItem[];
  toArchive: PromotionPreviewItem[];
  conflicts: PrefixConflict[];
  dryRun: boolean;
  executed: boolean;
}

export type CreateGroupRequest = Required<Pick<AcademicSchemas['CreateGroupRequest'], 'name'>>;
export type UpdateGroupRequest = {
  name: string;
  active: boolean;
};

export type SubjectResponse = Strict<
  AcademicSchemas['EntityModelSubjectResponse'],
  'id' | 'name' | 'type' | 'groupId' | 'teacherIds' | 'createdAt'
>;

export type SubjectType = NonNullable<SubjectResponse['type']>;

export type CreateSubjectRequest = AcademicSchemas['CreateSubjectRequest'];
export type UpdateSubjectRequest = AcademicSchemas['UpdateSubjectRequest'];

export type SemesterResponse = Strict<
  AcademicSchemas['EntityModelSemesterResponse'],
  'id' | 'name' | 'dateFrom' | 'dateTo' | 'active' | 'createdAt'
>;

export type CreateSemesterRequest = AcademicSchemas['CreateSemesterRequest'];
export type UpdateSemesterRequest = AcademicSchemas['UpdateSemesterRequest'];

export type ThresholdResponse = Strict<
  AcademicSchemas['EntityModelThresholdResponse'],
  'id' | 'groupId' | 'subjectId' | 'minPercentage' | 'updatedAt'
>;

export type SetThresholdRequest = Required<AcademicSchemas['SetThresholdRequest']>;

/**
 * Homework: backend в @Schema пока не помечает `description`/`link` как
 * nullable, но runtime отдаёт их как `null`. Override'им явно до M11.
 */
export type HomeworkResponse = Omit<
  Strict<
    AcademicSchemas['EntityModelHomeworkResponse'],
    | 'id'
    | 'title'
    | 'subjectId'
    | 'groupId'
    | 'semesterId'
    | 'publishedBy'
    | 'completed'
    | 'createdAt'
    | 'lessonDate'
    | 'lessonNumber'
  >,
  'description' | 'link'
> & {
  description: string | null;
  link: string | null;
};

export type CreateHomeworkRequest = Omit<
  AcademicSchemas['CreateHomeworkRequest'],
  'description' | 'link'
> & {
  description: string | null;
  link: string | null;
};

export type UpdateHomeworkRequest = Omit<
  AcademicSchemas['UpdateHomeworkRequest'],
  'description' | 'link'
> & {
  description: string | null;
  link: string | null;
};

// ------------------------- admin dashboard --------------------------

export interface DashboardStatsResponse {
  totalStudents: number;
  totalTeachers: number;
  totalGroups: number;
  activeGroups: number;
  activeSemesterName: string | null;
}

/** HATEOAS paged response — generic shape `_embedded.<dto>List` + page meta. */
export interface PagedResponse<T> {
  _embedded?: Record<string, T[]>;
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// ------------------------- assistants -------------------------------

export interface AssistantResponse {
  id: number;
  studentId: number;
  groupId: number;
  permissions: string[];
  studentName: string;
}

export type AssistantPermission =
  | 'manage_students'
  | 'manage_subjects'
  | 'manage_excuses'
  | 'manage_stats';

// ------------------------- attendance-service -----------------------

/**
 * AttendanceStatus — включает `cancelled` как agg-статус для journal
 * (пара отменена → всем `cancelled`). Backend записывает только 4 первых.
 */
export type AttendanceStatus =
  | 'present'
  | 'absent'
  | 'excused'
  | 'free_attendance'
  | 'cancelled';

export type AttendanceSource =
  | 'student_geo'
  | 'headman'
  | 'auto_scheduler'
  | 'late_checkin'
  | 'headman_excuse';

export type StudentAttendanceEntry = {
  userId: number;
  displayName: string;
  symbol: string;
  status: AttendanceStatus;
  source: AttendanceSource | null;
  excuseReason?: string | null;
};

export type LessonAttendance = {
  lessonId: number;
  groupId: number;
  subjectId: number;
  lessonDate: string;
  entries: StudentAttendanceEntry[];
};

export type OverallStats = Required<AttendanceSchemas['OverallStats']>;
export type StatusBreakdown = Required<AttendanceSchemas['StatusBreakdown']>;
export type WeeklyStat = Required<AttendanceSchemas['WeeklyStat']>;
export type TopMissedSubject = Required<AttendanceSchemas['TopMissedSubject']>;

export type StudentDashboard = {
  overall: OverallStats;
  breakdown: StatusBreakdown;
  weekly: WeeklyStat[];
  topMissed: TopMissedSubject[];
};

/**
 * ExcuseTicket: backend отдаёт excuseType UPPERCASE (Java enum). Ручные
 * excuse.types.ts в web-panel раньше держали lowercase — runtime
 * рассогласование (EXCUSE_TYPE_LABELS lookup никогда не срабатывал).
 * G3b фиксит тип, fix потребителей — G4/G6 (tsc показывает breakage).
 *
 * Nullable поля override'им до OpenAPI polish (M11).
 */
export type ExcuseType = NonNullable<AttendanceSchemas['CreateExcuseRequest']['excuseType']>;
export type ExcuseTicketStatus = 'submitted' | 'approved' | 'rejected' | 'draft';

export type ExcuseTicket = Omit<
  Strict<
    AttendanceSchemas['EntityModelExcuseTicketResponse'],
    'id' | 'studentId' | 'groupId' | 'studentName' | 'lessonIds' | 'excuseType' | 'createdAt' | 'updatedAt'
  >,
  'comment' | 'decisionBy' | 'decisionComment' | 'decisionAt' | 'status'
> & {
  status: ExcuseTicketStatus;
  comment: string | null;
  decisionBy: number | null;
  decisionComment: string | null;
  decisionAt: string | null;
};

export type CreateExcuseRequest = AttendanceSchemas['CreateExcuseRequest'];

export type LateCheckinRequestStatus = 'pending' | 'approved' | 'rejected';

export type LateCheckinRequest = Omit<
  Strict<
    AttendanceSchemas['EntityModelLateCheckinRequestResponse'],
    'id' | 'studentId' | 'groupId' | 'lessonId' | 'studentName' | 'createdAt' | 'updatedAt'
  >,
  'decisionBy' | 'decisionAt' | 'status'
> & {
  status: LateCheckinRequestStatus;
  decisionBy: number | null;
  decisionAt: string | null;
};

export type LateCheckinDecisionRequest = Required<AttendanceSchemas['LateCheckinDecisionRequest']>;

// ------------------------- auth-service -----------------------------

export type LoginRequest = Required<AuthSchemas['LoginRequest']>;

export type TokenResponse = Strict<
  AuthSchemas['TokenResponse'],
  'accessToken' | 'expiresIn'
>;

export type WsTicketResponse = Required<AuthSchemas['WsTicketResponse']>;

export type OtpVerifyByCodeRequest = Required<AuthSchemas['OtpVerifyByCodeRequest']>;
export type ChangePasswordRequest = Required<AuthSchemas['ChangePasswordRequest']>;

// ------------------------- admin avatars ----------------------------

/** preset-avatars custom shape — не DTO, оставляется ручным. */

// ------------------------- utils ------------------------------------

export function deriveSemesterStatus(s: SemesterResponse): SemesterStatus {
  if (s.active) return 'active';
  const today = new Date().toISOString().slice(0, 10);
  return s.dateFrom > today ? 'planned' : 'finished';
}

export function fullName(u: { lastName: string; firstName: string; middleName?: string | null }): string {
  const mid = u.middleName ? ` ${u.middleName}` : '';
  return `${u.lastName} ${u.firstName}${mid}`;
}
