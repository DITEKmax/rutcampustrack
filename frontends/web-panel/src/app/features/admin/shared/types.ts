export type UserRole = 'admin' | 'teacher' | 'student';
export type AccountStatus = 'active' | 'expelled' | 'suspended' | 'archived';
export type SemesterStatus = 'active' | 'planned' | 'finished';

export interface UserResponse {
  id: number;
  login: string;
  lastName: string;
  firstName: string;
  middleName: string | null;
  role: UserRole;
  status: AccountStatus;
  groupId: number | null;
  headman: boolean;
  employeeNumber: string | null;
  telegramId: number | null;
  createdAt: string;
  /** BUG-006-4: начальный пароль, видимый ADMIN-у пока пользователь его не сменил. null/undefined = скрыт. */
  initialPassword?: string | null;
  /** BUG-006-4: флаг смены пароля пользователем (после первой смены initialPassword уходит в null). */
  passwordChanged?: boolean;
}

export interface CreateUserRequest {
  lastName: string;
  firstName: string;
  middleName?: string;
  role: UserRole;
  groupId?: number;
  employeeNumber?: string;
  telegramId?: number;
}

export interface UserCreatedResponse extends UserResponse {
  initialPassword: string;
}

export interface PatchUserRequest {
  lastName?: string;
  firstName?: string;
  middleName?: string;
  isHeadman?: boolean;
  groupId?: number;
  employeeNumber?: string;
  telegramId?: number;
  status?: AccountStatus;
}

/** Composes ФИО из трёх отдельных полей. middleName опционален. */
export function fullName(u: { lastName: string; firstName: string; middleName?: string | null }): string {
  const mid = u.middleName ? ` ${u.middleName}` : '';
  return `${u.lastName} ${u.firstName}${mid}`;
}

export interface GroupResponse {
  id: number;
  name: string;
  active: boolean;
  createdAt: string;
  /** BUG-006-6 / plan 58-06: момент архивации (ISO); null у активных групп. */
  archivedAt?: string | null;
}

/** BUG-006-6 / plan 58-08: lifecycle-фильтр для GET /groups?status=... */
export type GroupStatus = 'ACTIVE' | 'ARCHIVED' | 'ALL';

/** BUG-006-6 / plan 58-08: строка preview для перевода групп. */
export interface PromotionPreviewItem {
  id: number;
  from: string;
  to?: string | null;
  action: 'PROMOTE' | 'ARCHIVE';
}

/** BUG-006-6 / plan 58-08: конфликт на уровне одного префикса (из PromotionSummary). */
export interface PrefixConflict {
  prefix: string;
  reason: 'name_conflict' | 'unknown_type' | 'parse_error';
  message: string;
  groupIds: number[];
}

/** BUG-006-6 / plan 58-08: ответ POST /groups/promote[/preview]. */
export interface PromotionSummary {
  toPromote: PromotionPreviewItem[];
  toArchive: PromotionPreviewItem[];
  conflicts: PrefixConflict[];
  dryRun: boolean;
  executed: boolean;
}

export interface CreateGroupRequest {
  name: string;
}

export interface UpdateGroupRequest {
  name: string;
  active: boolean;
}

export interface SemesterResponse {
  id: number;
  name: string;
  dateFrom: string;
  dateTo: string;
  active: boolean;
  createdAt: string;
}

export interface CreateSemesterRequest {
  name: string;
  dateFrom: string;
  dateTo: string;
}

export interface UpdateSemesterRequest {
  name: string;
  dateFrom: string;
  dateTo: string;
}

export interface DashboardStatsResponse {
  totalStudents: number;
  totalTeachers: number;
  totalGroups: number;
  activeGroups: number;
  activeSemesterName: string | null;
}

export interface PagedResponse<T> {
  _embedded?: Record<string, T[]>;
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

export interface AssistantResponse {
  id: number;
  studentId: number;
  groupId: number;
  permissions: string[];
  studentName: string;
}

export function deriveSemesterStatus(s: SemesterResponse): SemesterStatus {
  if (s.active) return 'active';
  const today = new Date().toISOString().slice(0, 10);
  return s.dateFrom > today ? 'planned' : 'finished';
}
