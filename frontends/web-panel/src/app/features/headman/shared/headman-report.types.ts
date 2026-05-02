import type { HttpResponse } from '@angular/common/http';

export type HeadmanWeeklyReportFormat = 'docx' | 'pdf' | 'png';

export interface HeadmanWeeklyWeekOption {
  weekOfSemester: number;
  isoWeek: number;
  label: string;
  weekStart: string;
  weekEnd: string;
  current: boolean;
}

export interface HeadmanWeeklyWeeksResponse {
  semesterId: number;
  semesterName: string;
  semesterDateFrom: string;
  semesterDateTo: string;
  weeks: HeadmanWeeklyWeekOption[];
}

export interface HeadmanWeeklyExportRequest {
  weekStarts: string[];
  format: HeadmanWeeklyReportFormat;
}

export type ReportBlobResponse = HttpResponse<Blob>;
