/**
 * Excuse-ticket DTO types for headman-side views.
 *
 * Field names MUST match backend `ExcuseTicketResponse` (plan 59-01):
 * services/attendance-service/attendance-api-contract/
 *   src/main/java/ru/rutcampustrack/attendance/contract/dto/excuse/ExcuseTicketResponse.java
 *
 * Kept local to the headman feature on purpose — plan 59-07 updates the
 * student-side types in parallel; we intentionally avoid cross-feature
 * coupling until both plans merge.
 */

export type ExcuseType =
  | 'illness'
  | 'summons'
  | 'university_order'
  | 'exemption'
  | 'free_attendance'
  | 'other';

export type ExcuseTicketStatus = 'submitted' | 'approved' | 'rejected' | 'draft';

export interface ExcuseTicket {
  id: string;
  studentId: number;
  groupId: number;
  studentName: string;
  lessonIds: number[];
  excuseType: ExcuseType;
  comment: string | null;
  status: ExcuseTicketStatus;
  decisionBy: number | null;
  decisionComment: string | null;
  decisionAt: string | null;
  createdAt: string;
  updatedAt: string;
  /**
   * Клиентское обогащение — детали пар, которые бэкенд не возвращает в ответе
   * (в отличие от события excuse.requested, где они уже есть).
   * Заполняется компонентом через schedule-service + SubjectCacheService.
   */
  lessons?: LessonBrief[];
}

export interface LessonBrief {
  lessonId: number;
  lessonNumber: number | null;
  date: string | null;          // YYYY-MM-DD
  subjectId: number | null;
  subjectName: string | null;
}

export interface PagedExcuseResponse {
  _embedded?: {
    excuseTicketList?: ExcuseTicket[];
  };
  page?: { totalElements: number; totalPages: number; size: number; number: number };
}

/** Russian labels for ExcuseType (D-21). */
export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  illness: 'Болезнь',
  summons: 'Повестка',
  university_order: 'Приказ университета',
  exemption: 'Освобождение',
  free_attendance: 'Свободное посещение',
  other: 'Другое',
};

/** Russian labels for ExcuseTicketStatus. */
export const EXCUSE_STATUS_LABELS: Record<ExcuseTicketStatus, string> = {
  submitted: 'На рассмотрении',
  approved: 'Одобрено',
  rejected: 'Отклонено',
  draft: 'Черновик',
};
