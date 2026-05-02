/**
 * Teacher journal types.
 *
 * M07 G3b (D3): AttendanceStatus, SubjectResponse, GroupResponse,
 * PagedResponse — re-export из `@/api/schema`. Journal-specific shapes
 * (JournalResponse, JournalStudentRow, JournalCell, JournalColumn,
 * AssignmentResponse) — frontend-aggregate, остаются ручными.
 */

import type {
  AttendanceStatus,
  GroupResponse,
  PagedResponse,
  SubjectResponse,
} from '../../../api/schema';

export type { GroupResponse, PagedResponse, SubjectResponse };

export interface JournalResponse {
  groupId: number;
  subjectId: number;
  dates: string[];
  students: JournalStudentRow[];
}

export interface JournalStudentRow {
  userId: number;
  displayName: string;
  records: JournalCell[];
}

export interface JournalCell {
  lessonId?: number;  // Phase 55: headman marking support (D-01); optional — teacher grid ignores
  date: string;
  lessonNumber: number;
  status: AttendanceStatus;
  symbol: string;
  /** Human-readable excuse reason — present only for excused/free_attendance. */
  excuseReason?: string | null;
}

export interface AssignmentResponse {
  id: number;
  groupId: number;
  groupName?: string | null;
  subjectId: number;
  subjectName?: string | null;
  teacherId: number;
  teacherName?: string | null;
  semesterId?: number;
}

export interface SubjectOption {
  id: number;
  name: string;
}

/** Column definition for the journal grid */
export interface JournalColumn {
  id: string;           // "{date}_lesson{lessonNumber}"
  date: string;         // ISO date e.g. "2026-03-15"
  lessonNumber: number;
  displayDate: string;  // "дд.мм" format e.g. "15.03"
  isToday: boolean;
}
