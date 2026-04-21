/**
 * Schedule feature types.
 *
 * M07 G3b (D3): ручные interface-копии заменены re-export'ами из
 * `@/api/schema` (централизованные aliases над generated OpenAPI types).
 * Если нужен новый DTO — добавь его в `src/api/schema.ts`, не возвращай
 * ручной interface сюда.
 */

export type {
  LessonResponse,
  LessonStatus,
  SubjectResponse,
  AttendanceStatus,
} from '@/api/schema'
