export type AcademicWeekType = 'ALL' | 'ODD' | 'EVEN';

export interface AcademicWeekTypeOption {
  weekType: AcademicWeekType;
  parityNumber: 1 | 2 | null;
  label: string;
}

/** Schedule metadata API; names and parity are provided only by the server. */
export interface AcademicWeekPolicyResponse {
  weekStart: string;
  weekEnd: string;
  isoWeek: number;
  weekType: Exclude<AcademicWeekType, 'ALL'>;
  parityNumber: 1 | 2;
  label: string;
  weekTypes: AcademicWeekTypeOption[];
}
