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
  code: string;
  active: boolean;
  createdAt: string;
}

export interface CreateGroupRequest {
  name: string;
  code: string;
}

export interface UpdateGroupRequest {
  name: string;
  code: string;
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
