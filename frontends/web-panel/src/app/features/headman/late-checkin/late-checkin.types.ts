/**
 * View model for a late-checkin request (headman cabinet).
 *
 * M07 G3b (D3): базовые DTO-поля re-export'ятся из `@/api/schema`
 * (generated из LateCheckinRequestResponse). Enrichment-поля
 * (lessonNumber, lessonDate, subjectId, subjectName) — клиентское
 * обогащение из schedule-service/STOMP, остаются ручными как
 * intersection.
 */

import type {
  LateCheckinRequest,
  LateCheckinRequestStatus,
} from '../../../api/schema';

export type { LateCheckinRequestStatus };

export type LateCheckinRequestView = LateCheckinRequest & {
  /** Обогащение из STOMP payload или schedule-service. Необязательное. */
  lessonNumber?: number | null;
  /** YYYY-MM-DD. */
  lessonDate?: string | null;
  subjectId?: number | null;
  subjectName?: string | null;
};

/**
 * STOMP envelope shape for the `/topic/group/{groupId}/headman` channel.
 * Fired by notification-service when a student submits a late-checkin request.
 * Payload shape matches `LateCheckinEventPublisher.publishRequested` in the
 * backend (fields: request_id, user_id, group_id, lesson_id, student_name,
 * lesson_date, lesson_number, subject_id, subject_name).
 */
export interface LateCheckinRequestedEvent {
  type: 'late_checkin.requested';
  payload: {
    request_id: string;
    user_id: number;
    group_id: number;
    lesson_id: number;
    student_name: string;
    lesson_date: string | null;
    lesson_number: number | null;
    subject_id: number | null;
    subject_name: string | null;
  };
}

/** STOMP envelope для {@code late_checkin.decided} — используется для авто-закрытия карточек. */
export interface LateCheckinDecidedEvent {
  type: 'late_checkin.decided';
  payload: {
    request_id: string;
    user_id: number;
    group_id: number;
    lesson_id: number;
    decision_by: number | null;
    status: 'approved' | 'rejected';
    decided_at: string | null;
    lesson_date: string | null;
    lesson_number: number | null;
    subject_id: number | null;
    subject_name: string | null;
  };
}
