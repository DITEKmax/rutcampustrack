/**
 * Admin shared types — re-export из `@/api/schema` (M07 G3b, D3).
 * Весь admin-функционал использует тот же набор DTO что и остальные
 * features web-panel; оставляем тут алиасы для backward-compat импортов.
 */

export type {
  UserRole,
  AccountStatus,
  SemesterStatus,
  UserResponse,
  UserCreatedResponse,
  CreateUserRequest,
  PatchUserRequest,
  GroupResponse,
  GroupStatus,
  CreateGroupRequest,
  UpdateGroupRequest,
  PromotionPreviewItem,
  PrefixConflict,
  PromotionSummary,
  SemesterResponse,
  CreateSemesterRequest,
  UpdateSemesterRequest,
  DashboardStatsResponse,
  PagedResponse,
  AssistantResponse,
} from '../../../api/schema';

export { deriveSemesterStatus, fullName } from '../../../api/schema';
