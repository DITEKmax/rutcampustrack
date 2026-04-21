/**
 * Homework feature types — re-export из `@/api/schema` (M07 G3b, D3).
 * DTO-типы больше не дублируются здесь; SemesterResponse берётся из
 * academic-service generated schema.
 */

export type {
  HomeworkResponse,
  CreateHomeworkRequest,
  UpdateHomeworkRequest,
  SemesterResponse,
} from '@/api/schema'
