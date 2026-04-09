/**
 * Shared student-domain DTO types for Phase 51.
 *
 * Field names MUST match backend DTOs verbatim — these are parsed directly
 * from HATEOAS JSON bodies returned by schedule-service, attendance-service
 * and academic-service. See the source-of-truth references below.
 *
 * Canonical sources:
 * - LessonResponse / LessonStatus / AttendanceStatus: services/schedule-service
 *   and replicated in frontends/pwa/src/features/schedule/types.ts
 * - SubjectResponse: services/academic-service SubjectResponse
 * - StudentStatsResponse / SubjectStats / OverallStats:
 *   services/attendance-service/attendance-api-contract StudentStatsResponse.java
 * - ResolvedThresholdResponse: services/academic-service ThresholdApi
 * - CheckinRequest: services/attendance-service CheckinRequest record (lat, lng)
 * - AttendanceMarkedPayload: notification-web STOMP envelope payload
 *
 * Used by StudentApiService, SubjectCacheService, StudentStompService,
 * and downstream page components in Plans 02-04.
 */

export type LessonStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
export type WeekType = 'NUMERATOR' | 'DENOMINATOR' | 'BOTH';
export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance';

export interface LessonResponse {
  id: number;
  scheduleItemId: number;
  groupId: number;
  subjectId: number;
  teacherId: number;
  date: string;          // YYYY-MM-DD
  status: LessonStatus;
  dayOfWeek: number;     // 1=Mon..7=Sun
  lessonNumber: number;
  startTime: string;     // HH:mm:ss
  endTime: string;       // HH:mm:ss
  weekType: WeekType;
  room: string;
  geoBlocked: boolean;
  cancelReason: string | null;
  createdAt: string;
}

export interface SubjectResponse {
  id: number;
  name: string;
}

export interface SubjectStats {
  subjectId: number;
  subjectName: string;
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

export interface CheckinRequest {
  lat: number;
  lng: number;
}

export interface CheckinResponse {
  status: AttendanceStatus;
  lessonId: number;
  timestamp: string;
}

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

export interface PagedResponse<T> {
  _embedded?: Record<string, T[]>;
  page?: { totalElements: number; totalPages: number; size: number; number: number };
}

/** Homework assignment as returned by academic-service GET /api/academic/homeworks */
export interface HomeworkItem {
  id: number;
  title: string;
  description: string | null;
  link: string | null;
  subjectId: number;
  groupId: number;
  semesterId: number;
  publishedBy: number;
  completed: boolean;
  createdAt: string; // ISO-8601
}

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

export type ExcuseTicketStatus = 'pending' | 'approved' | 'rejected' | 'cancelled';

export interface ExcuseTicket {
  id: number;
  createdAt: string;         // ISO-8601
  subjectNames: string[];
  status: ExcuseTicketStatus;
  lessonIds: number[];
}

export interface ExcuseSubmitRequest {
  lessonIds: number[];
  comment: string | null;
  // files отправляются через FormData отдельно
}
