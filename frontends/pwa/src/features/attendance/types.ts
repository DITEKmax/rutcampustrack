export interface SubjectStats {
  subjectId: number
  subjectName: string
  total: number
  attended: number
  absent: number
  excused: number
  percentage: number
}

export interface OverallStats {
  total: number
  attended: number
  absent: number
  excused: number
  percentage: number
}

export interface StudentStatsResponse {
  subjects: SubjectStats[]
  overall: OverallStats
}

export interface AttendanceRecordEntry {
  lessonId: number
  subjectId: number
  lessonDate: string
  lessonNumber: number
  status: string
  symbol: string
  source: string
}

export interface ResolvedThresholdResponse {
  minPercentage: number
  level: string
  sourceId: number
}
