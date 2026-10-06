export interface AcademicWeekOption { readonly weekType: 'ALL' | 'ODD' | 'EVEN'; readonly parityNumber: 1 | 2 | null; readonly label: string }
export interface AcademicWeekPolicy {
  readonly weekStart: string
  readonly weekEnd: string
  readonly isoWeek: number
  readonly weekType: 'ODD' | 'EVEN'
  readonly parityNumber: 1 | 2
  readonly label: string
  readonly weekTypes: readonly AcademicWeekOption[]
}
function record(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null && !Array.isArray(value) }
export function academicDate(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const parsed = Date.parse(value + 'T00:00:00Z')
  return Number.isFinite(parsed) && new Date(parsed).toISOString().slice(0, 10) === value ? parsed : null
}
export function normalizeAcademicWeekPolicy(value: unknown, selectedDate: string): AcademicWeekPolicy {
  const fail = () => { throw new Error('Сервер вернул некорректную чётность недели.') }
  if (!record(value) || typeof value.weekStart !== 'string' || typeof value.weekEnd !== 'string' || !Array.isArray(value.weekTypes)) return fail()
  const start = academicDate(value.weekStart); const end = academicDate(value.weekEnd); const selected = academicDate(selectedDate)
  if (start === null || end === null || selected === null || new Date(start).getUTCDay() !== 1 || end - start !== 6 * 86_400_000 || selected < start || selected > end
    || !Number.isInteger(value.isoWeek) || Number(value.isoWeek) < 1 || Number(value.isoWeek) > 53 || !['ODD', 'EVEN'].includes(String(value.weekType))
    || (value.parityNumber !== 1 && value.parityNumber !== 2) || typeof value.label !== 'string' || !value.label.trim()) return fail()
  const options = value.weekTypes.map((option): AcademicWeekOption => {
    if (!record(option) || !['ALL','ODD','EVEN'].includes(String(option.weekType)) || typeof option.label !== 'string' || !option.label.trim()
      || (option.parityNumber !== null && option.parityNumber !== 1 && option.parityNumber !== 2)) return fail()
    return { weekType: option.weekType as AcademicWeekOption['weekType'], parityNumber: option.parityNumber as AcademicWeekOption['parityNumber'], label: option.label }
  })
  if (new Set(options.map((option) => option.weekType)).size !== 3 || options.length !== 3
    || !options.some((option) => option.weekType === value.weekType && option.parityNumber === value.parityNumber && option.label === value.label)) return fail()
  return { weekStart: value.weekStart, weekEnd: value.weekEnd, isoWeek: Number(value.isoWeek), weekType: value.weekType as AcademicWeekPolicy['weekType'], parityNumber: value.parityNumber, label: value.label, weekTypes: options }
}
/** Semester ordinal is geometry only; parity and names come exclusively from Schedule. */
export function academicWeekOrdinal(date: string, semesterFrom: string | null, semesterTo: string | null): number | null {
  if (!semesterFrom || !semesterTo) return null
  const selected = academicDate(date); const first = academicDate(semesterFrom); const last = academicDate(semesterTo)
  if (selected === null || first === null || last === null || selected < first || selected > last) return null
  const monday = first - (new Date(first).getUTCDay() + 6) % 7 * 86_400_000
  return Math.floor((selected - monday) / (7 * 86_400_000)) + 1
}
