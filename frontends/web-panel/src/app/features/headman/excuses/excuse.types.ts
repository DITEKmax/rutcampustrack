/**
 * Excuse-ticket DTO types for headman-side views.
 *
 * M07 G3b (D3): DTO-types re-export'ятся из `@/api/schema` (generated
 * OpenAPI). Ручной `ExcuseType` был lowercase — runtime bug (backend
 * отдавал UPPERCASE из Java enum). После миграции UPPERCASE, tsc
 * покажет любое lowercase lookup'ание как ошибку.
 *
 * LessonBrief и PagedExcuseResponse — frontend-aggregate shapes, не
 * backend DTO; оставлены ручными.
 */

import type {
  ExcuseTicket as BaseExcuseTicket,
  ExcuseType,
  ExcuseTicketStatus,
} from '../../../api/schema';

export type { ExcuseType, ExcuseTicketStatus };

export type ExcuseTicket = BaseExcuseTicket & {
  /**
   * Клиентское обогащение — детали пар, которые бэкенд не возвращает в
   * ответе (в отличие от события excuse.requested, где они уже есть).
   * Заполняется компонентом через schedule-service + SubjectCacheService.
   */
  lessons?: LessonBrief[];
};

export interface LessonBrief {
  lessonId: number;
  lessonNumber: number | null;
  date: string | null;          // YYYY-MM-DD
  subjectId: number | null;
  subjectName: string | null;
}

export interface PagedExcuseResponse {
  _embedded?: {
    // Spring HATEOAS имя ключа генерируется из имени DTO: ExcuseTicketResponse
    // → excuseTicketResponseList. Старое имя оставлено для обратной совместимости
    // на случай, если кто-то добавит @Relation("excuseTicket") на бэкенде.
    excuseTicketResponseList?: ExcuseTicket[];
    excuseTicketList?: ExcuseTicket[];
  };
  page?: { totalElements: number; totalPages: number; size: number; number: number };
}

/** Russian labels for ExcuseType — backend UPPERCASE values. */
export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  ILLNESS: 'Болезнь',
  SUMMONS: 'Повестка',
  UNIVERSITY_ORDER: 'Приказ университета',
  EXEMPTION: 'Освобождение',
  FREE_ATTENDANCE: 'Свободное посещение',
  OTHER: 'Другое',
};

/** Russian labels for ExcuseTicketStatus. */
export const EXCUSE_STATUS_LABELS: Record<ExcuseTicketStatus, string> = {
  submitted: 'На рассмотрении',
  approved: 'Одобрено',
  rejected: 'Отклонено',
  draft: 'Черновик',
};
