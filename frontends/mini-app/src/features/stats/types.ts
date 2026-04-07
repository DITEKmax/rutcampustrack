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
