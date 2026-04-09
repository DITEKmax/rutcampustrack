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
  status: 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled';
  symbol: string;
}

export interface GroupResponse {
  id: number;
  name: string;
  code: string;
  active: boolean;
  createdAt: string;
}

export interface SubjectResponse {
  id: number;
  name: string;
}

export interface AssignmentResponse {
  id: number;
  groupId: number;
  subjectId: number;
  teacherId: number;
}

export interface PagedResponse<T> {
  _embedded: Record<string, T[]>;
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

/** Column definition for the journal grid */
export interface JournalColumn {
  id: string;           // "{date}_lesson{lessonNumber}"
  date: string;         // ISO date e.g. "2026-03-15"
  lessonNumber: number;
  displayDate: string;  // "дд.мм" format e.g. "15.03"
  isToday: boolean;
}
