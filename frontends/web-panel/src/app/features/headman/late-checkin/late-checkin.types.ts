/**
 * View model for a late-checkin request, returned by
 * `GET /api/attendance/late-checkin/pending` and used in the headman cabinet.
 *
 * Shape mirrors {@code LateCheckinRequestResponse} from the backend contract
 * (services/attendance-service/attendance-api-contract) minus the HATEOAS
 * `_links` wrapper. Status arrives lowercase to match Mongo / event payloads.
 */
export type LateCheckinRequestStatus = 'pending' | 'approved' | 'rejected';

export interface LateCheckinRequestView {
  id: string;
  studentId: number;
  groupId: number;
  lessonId: number;
  studentName: string;
  status: LateCheckinRequestStatus;
  decisionBy: number | null;
  decisionAt: string | null;
  createdAt: string;
  updatedAt: string;
}

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
