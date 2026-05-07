import { JournalResponse } from '../journal/types';

export interface StudentChartData {
  name: string;
  present: number;
  excused: number;
  absent: number;
}

export interface OverallStats {
  totalLessons: number;
  attendanceRate: number; // 0-100 percentage
}

export function deriveStudentChartData(journal: JournalResponse): StudentChartData[] {
  return journal.students.map(student => {
    const counts = { present: 0, excused: 0, absent: 0 };
    student.records.forEach(cell => {
      if (cell.status === 'present') counts.present++;
      else if (cell.status === 'excused' || cell.status === 'free_attendance') counts.excused++;
      else if (cell.status === 'absent') counts.absent++;
      // 'cancelled' is excluded per business rules
    });
    const name =
      student.displayName.length > 16
        ? student.displayName.slice(0, 16) + '...'
        : student.displayName;
    return { name, ...counts };
  });
}

export function deriveOverallStats(journal: JournalResponse): OverallStats {
  if (journal.students.length === 0) {
    return { totalLessons: 0, attendanceRate: 0 };
  }
  // Count unique non-cancelled lessons
  const lessonSet = new Set<string>();
  let totalAttended = 0;
  let totalRecords = 0;
  journal.students.forEach(student => {
    student.records.forEach(cell => {
      if (cell.status !== 'cancelled') {
        lessonSet.add(`${cell.date}_${cell.lessonNumber}`);
        totalRecords++;
        if (cell.status === 'present' || cell.status === 'excused' || cell.status === 'free_attendance') {
          totalAttended++;
        }
      }
    });
  });
  return {
    totalLessons: lessonSet.size,
    attendanceRate: totalRecords > 0 ? Math.round((totalAttended / totalRecords) * 100) : 0,
  };
}
