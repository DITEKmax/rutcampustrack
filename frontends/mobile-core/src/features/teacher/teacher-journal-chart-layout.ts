import { headmanStatsTrendPosition, type HeadmanStatsTrendAxis } from '../headman-stats/headman-stats-trend-axis'

export interface JournalChartBarLayout { readonly lessonId: number; readonly x: number; readonly width: number }
/** Each date owns its interval between neighbouring calendar-anchor midpoints.
 * Fit all same-day lessons inside that interval, then clamp the whole cluster.
 * Fractional SVG widths preserve individual lessons even in dense semesters.
 */
export function journalChartBarLayout(lessons: readonly { id: number; date: string }[], axis: HeadmanStatsTrendAxis | null, plotWidth: number, left = 0): JournalChartBarLayout[] {
  if (!axis || !Number.isFinite(plotWidth) || plotWidth <= 0) return []
  const dates = new Map<string, { anchor: number; lessons: number[] }>()
  for (const lesson of lessons) {
    const position = headmanStatsTrendPosition(lesson.date, axis)
    if (position === null) continue
    const day = dates.get(lesson.date) ?? { anchor: position * plotWidth, lessons: [] }
    day.lessons.push(lesson.id)
    dates.set(lesson.date, day)
  }
  const groups = [...dates.values()].sort((a,b) => a.anchor - b.anchor)
  if (!groups.length) return []
  const intervals = groups.map((group,index) => ({ ...group,
    start: index === 0 ? 0 : (groups[index-1]!.anchor + group.anchor) / 2,
    end: index === groups.length-1 ? plotWidth : (group.anchor + groups[index+1]!.anchor) / 2,
  }))
  // A common width keeps a lesson's visual weight independent of date density.
  // The 20% space reserve leaves a gap between neighbouring date clusters.
  const width = Math.min(18, ...intervals.map(group => (group.end - group.start) * 0.8 / group.lessons.length))
  return intervals.flatMap(group => {
    const clusterWidth = group.lessons.length * width
    const start = Math.max(group.start, Math.min(group.end - clusterWidth, group.anchor - clusterWidth / 2))
    return group.lessons.map((lessonId,index) => ({ lessonId, x: left + start + index * width, width }))
  })
}
