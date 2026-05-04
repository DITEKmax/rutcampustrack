/**
 * Shared student-domain DTO types.
 *
 * M07 G3b (D3): DTO-типы (LessonStatus, WeekType, AttendanceStatus,
 * SubjectResponse, CheckinRequest, ExcuseType, ExcuseTicket,
 * ExcuseTicketStatus) re-export'ятся из `@/api/schema`. Frontend-aggregate
 * types (StudentStatsResponse, ResolvedThresholdResponse, StompEnvelope,
 * NotificationItem, AttendanceMarkedPayload — STOMP-пейлоад snake_case)
 * остаются ручными.
 *
 * Breaking changes для callers:
 * - WeekType теперь 'ALL' | 'ODD' | 'EVEN' (было NUMERATOR/DENOMINATOR/BOTH);
 *   backend уже давно отдаёт новые значения.
 * - ExcuseType теперь UPPERCASE ('ILLNESS' | ... | 'OTHER') — backend
 *   отдаёт UPPERCASE из Java enum. Ручной lowercase был runtime bug.
 */

import type {
  AttendanceStatus,
  CreateExcuseRequest,
  ExcuseTicket,
  ExcuseTicketStatus,
  ExcuseType,
  HomeworkResponse,
  LessonResponse as GeneratedLessonResponse,
  LessonStatus,
  PagedResponse,
  SubjectResponse,
  WeekType,
} from '../../../api/schema';

export type {
  AttendanceStatus,
  ExcuseTicket,
  ExcuseTicketStatus,
  ExcuseType,
  LessonStatus,
  PagedResponse,
  SubjectResponse,
  WeekType,
};

/**
 * Student-facing LessonResponse добавляет `teacherId` и `cancelReason`
 * поверх generated shape (backend schedule-service отдаёт их в runtime,
 * но @Schema пока не помечает). Override до M11.
 */
export type LessonResponse = GeneratedLessonResponse & {
  teacherId: number;
  cancelReason: string | null;
};

export interface SubjectStats {
  subjectId: number;
  subjectName: string;
  subjectType?: string | null;
  total: number;
  attended: number;
  absent: number;
  excused: number;
  percentage: number; // 0..100
}

export interface OverallStats {
  total: number;
  attended: number;
  absent: number;
  excused: number;
  percentage: number;
}

export interface StudentStatsResponse {
  subjects: SubjectStats[];
  overall: OverallStats;
}

export interface ResolvedThresholdResponse {
  groupId: number | null;
  subjectId: number | null;
  percentage: number;
  level: 'global' | 'group' | 'subject';
}

export type CheckinRequest = {
  lat: number;
  lng: number;
};

export interface CheckinResponse {
  status: AttendanceStatus;
  lessonId: number;
  timestamp: string;
}

/**
 * STOMP envelope payload `/topic/attendance/marked` — snake_case, не
 * проходит через OpenAPI (шина, не REST). Остаётся ручным типом.
 */
export interface AttendanceMarkedPayload {
  lesson_id: number;
  user_id: number;
  group_id: number;
  status: string;
  marked_by: string;
}

export interface StompEnvelope<T> {
  type: string;
  payload: T;
}

/** Alias для совместимости — academic-service HomeworkResponse. */
export type HomeworkItem = HomeworkResponse;

/** In-session notification log entry built from STOMP envelopes */
export interface NotificationItem {
  id: string;           // crypto.randomUUID() at receive time
  type: string;         // STOMP envelope type field (e.g. 'lesson.started')
  payload: Record<string, unknown>;
  receivedAt: Date;
  read: boolean;        // false until user visits /student/notifications
}

/** Attendance record returned by GET /api/attendance/reports/student/records */
export interface AttendanceRecord {
  lessonId: number;
  subjectId: number;
  lessonDate: string;      // YYYY-MM-DD
  lessonNumber: number;
  status: string;          // 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled'
  symbol: string;          // 'б' | 'н' | 'у' | 'сп' | '--'
  source: string;          // 'manual' | 'auto' | 'checkin'
}

export type ExcuseSubmitRequest = CreateExcuseRequest;

/** Russian labels for ExcuseType — backend UPPERCASE values. */
export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  ILLNESS: 'Болезнь',
  SUMMONS: 'Повестка',
  UNIVERSITY_ORDER: 'Приказ университета',
  EXEMPTION: 'Освобождение',
  FREE_ATTENDANCE: 'Свободное посещение',
  OTHER: 'Другое',
};
