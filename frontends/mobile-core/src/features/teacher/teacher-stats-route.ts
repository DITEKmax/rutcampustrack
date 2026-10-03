import type { TeacherStatsFilter, TeacherStatsQuery, TeacherStatsScope, TeacherStatsSort } from './teacher-client'

const ACTIVE_KEY = 'teacherStats'
const SEMESTER_KEY = 'teacherStatsSemester'
const SCOPE_KEY = 'teacherStatsScope'
const GROUP_KEY = 'teacherStatsGroup'
const SUBJECT_KEY = 'teacherStatsSubject'
const TYPE_KEY = 'teacherStatsType'
const SEARCH_KEY = 'teacherStatsSearch'
const SORT_KEY = 'teacherStatsSort'
const SORT_DESCENDING_KEY = 'teacherStatsSortDescending'
const FILTER_KEY = 'teacherStatsFilter'
const CRITERIA_KEY = 'teacherStatsCriteria'
const ROUTE_KEYS = [ACTIVE_KEY, SEMESTER_KEY, SCOPE_KEY, GROUP_KEY, SUBJECT_KEY, TYPE_KEY,
  SEARCH_KEY, SORT_KEY, SORT_DESCENDING_KEY, FILTER_KEY, CRITERIA_KEY]

export interface TeacherStatsRangeDraft { minimum: string; maximum: string }
export interface TeacherStatsRouteContext {
  readonly semesterId: number | null
  readonly scope: TeacherStatsScope
  readonly groupId: number | null
  readonly subjectId: number | null
  readonly lessonTypes: readonly string[]
  readonly search: string
  readonly sorts: readonly TeacherStatsSort[]
  readonly filters: readonly TeacherStatsFilter[]
}

export function resetTeacherStatsCriteria(context: TeacherStatsRouteContext): TeacherStatsRouteContext {
  return { ...context, lessonTypes: [], search: '', sorts: [], filters: [] }
}

export function readRecoverableTeacherStatsContext(params = readSearchParams()): {
  context: TeacherStatsRouteContext | null
  notice: string | null
} {
  try {
    return { context: readTeacherStatsContext(params), notice: null }
  } catch (cause) {
    // This boundary parses only URL criteria. API/auth errors never enter it.
    if (!(cause instanceof RangeError)) throw cause
    return { context: null, notice: 'Параметры статистики в адресе некорректны. Отбор восстановлен по умолчанию.' }
  }
}

export function teacherStatsNumericColumns(scope: TeacherStatsScope): readonly string[] {
  return ['present', 'presentOrExcused', 'excused', 'absent', ...(scope === 'groups' ? ['lessonsCount'] : [])]
}

export function teacherStatsSortColumns(scope: TeacherStatsScope): readonly string[] {
  return [scope === 'students' ? 'displayName' : 'groupName', ...teacherStatsNumericColumns(scope)]
}

export function parseTeacherStatsRanges(scope: TeacherStatsScope, drafts: Readonly<Record<string, TeacherStatsRangeDraft>>): TeacherStatsFilter[] {
  return teacherStatsNumericColumns(scope).flatMap((column) => {
    const draft = drafts[column]
    if (!draft) return []
    const count = column === 'lessonsCount'
    const minimum = rangeNumber(draft.minimum, count)
    const maximum = rangeNumber(draft.maximum, count)
    if (minimum !== undefined && maximum !== undefined && minimum > maximum) throw new RangeError('Нижняя граница не должна быть больше верхней.')
    if (minimum === undefined && maximum === undefined) return []
    return [{ column, ...(count
      ? { ...(minimum === undefined ? {} : { minValue: minimum }), ...(maximum === undefined ? {} : { maxValue: maximum }) }
      : { ...(minimum === undefined ? {} : { minPercent: minimum }), ...(maximum === undefined ? {} : { maxPercent: maximum }) }) }]
  })
}

export function teacherStatsQueryForContext(context: TeacherStatsRouteContext): TeacherStatsQuery | null {
  validateCriteria(context)
  if (!context.semesterId || context.scope === 'students' && (!context.groupId || !context.subjectId)) return null
  return {
    semesterId: context.semesterId, scope: context.scope,
    groupId: context.scope === 'students' ? context.groupId : null,
    subjectId: context.scope === 'students' ? context.subjectId : null,
    lessonTypes: [...context.lessonTypes], sorts: context.sorts.map((sort) => ({ ...sort })),
    filters: [
      ...(context.search.trim() ? [{ column: context.scope === 'students' ? 'displayName' : 'groupName', contains: context.search.trim() }] : []),
      ...context.filters.map((filter) => ({ ...filter })),
    ],
  }
}

export function hasTeacherStatsRoute(): boolean { return readSearchParams()?.get(ACTIVE_KEY) === '1' }
export function activateTeacherStatsRoute(): void { updateSearchParams((params) => params.set(ACTIVE_KEY, '1')) }
export function clearTeacherStatsRoute(): void { updateSearchParams((params) => ROUTE_KEYS.forEach((key) => params.delete(key))) }

export function readTeacherStatsContext(params = readSearchParams()): TeacherStatsRouteContext | null {
  if (!params || params.get(ACTIVE_KEY) !== '1') return null
  const scope = params.get(SCOPE_KEY) === 'students' ? 'students' : 'groups'
  const currentCriteria = params.get(CRITERIA_KEY) === '1'
  const sortValues = params.getAll(SORT_KEY)
  const context: TeacherStatsRouteContext = {
    semesterId: positiveInteger(params.get(SEMESTER_KEY)), scope,
    groupId: positiveInteger(params.get(GROUP_KEY)), subjectId: positiveInteger(params.get(SUBJECT_KEY)),
    lessonTypes: params.getAll(TYPE_KEY).map((value) => value.trim()).filter((value, index, values) => value.length > 0 && index === values.indexOf(value)),
    search: (params.get(SEARCH_KEY) ?? '').trim(),
    sorts: sortValues.length ? sortValues.map((value) => ({
      column: value.startsWith('-') ? value.slice(1) : value,
      descending: value.startsWith('-') || !currentCriteria && params.get(SORT_DESCENDING_KEY) === '1',
    })) : currentCriteria ? [] : [{ column: 'present', descending: false }],
    filters: params.getAll(FILTER_KEY).map((value): TeacherStatsFilter => {
      try { return JSON.parse(value) as TeacherStatsFilter } catch { throw new RangeError('Проверь фильтры в адресе статистики.') }
    }),
  }
  validateCriteria(context)
  return context
}

export function teacherStatsContextParams(context: TeacherStatsRouteContext): URLSearchParams {
  validateCriteria(context)
  const params = new URLSearchParams({ [ACTIVE_KEY]: '1', [SCOPE_KEY]: context.scope, [CRITERIA_KEY]: '1' })
  setPositive(params, SEMESTER_KEY, context.semesterId)
  setPositive(params, GROUP_KEY, context.scope === 'students' ? context.groupId : null)
  setPositive(params, SUBJECT_KEY, context.scope === 'students' ? context.subjectId : null)
  for (const lessonType of context.lessonTypes) params.append(TYPE_KEY, lessonType)
  if (context.search.trim()) params.set(SEARCH_KEY, context.search.trim())
  for (const sort of context.sorts) params.append(SORT_KEY, `${sort.descending ? '-' : ''}${sort.column}`)
  for (const filter of context.filters) params.append(FILTER_KEY, JSON.stringify(filter))
  return params
}

export function writeTeacherStatsContext(context: TeacherStatsRouteContext): void {
  const criteria = teacherStatsContextParams(context)
  updateSearchParams((params) => {
    ROUTE_KEYS.forEach((key) => params.delete(key))
    criteria.forEach((value, key) => params.append(key, value))
  })
}

function validateCriteria(context: TeacherStatsRouteContext): void {
  const columns = teacherStatsSortColumns(context.scope)
  if (context.search.length > 120 || context.lessonTypes.length > 50
    || context.sorts.some((sort) => !columns.includes(sort.column) || typeof sort.descending !== 'boolean' && sort.descending !== undefined)
    || new Set(context.sorts.map((sort) => sort.column)).size !== context.sorts.length) throw new RangeError('Проверь поиск и порядок сортировки статистики.')
  const numericColumns = teacherStatsNumericColumns(context.scope)
  const seen = new Set<string>()
  for (const filter of context.filters) {
    if (!filter || typeof filter !== 'object' || !numericColumns.includes(filter.column) || seen.has(filter.column)) throw new RangeError('Проверь фильтры в адресе статистики.')
    seen.add(filter.column)
    const count = filter.column === 'lessonsCount'
    const fields = count ? ['column', 'minValue', 'maxValue'] : ['column', 'minPercent', 'maxPercent']
    if (Object.keys(filter).some((key) => !fields.includes(key))) throw new RangeError('Проверь фильтры в адресе статистики.')
    const minimum = count ? filter.minValue : filter.minPercent
    const maximum = count ? filter.maxValue : filter.maxPercent
    if (minimum === undefined && maximum === undefined) throw new RangeError('Укажи хотя бы одну границу диапазона.')
    for (const value of [minimum, maximum]) {
      if (value !== undefined && (typeof value !== 'number' || !Number.isFinite(value) || value < 0
        || (count ? !Number.isSafeInteger(value) : value > 100))) throw new RangeError(rangeError(count))
    }
    if (minimum !== undefined && maximum !== undefined && minimum > maximum) throw new RangeError('Нижняя граница не должна быть больше верхней.')
  }
}

function rangeNumber(raw: string, count: boolean): number | undefined {
  const text = raw.trim()
  if (!text) return undefined
  if (!/^\d+(?:\.\d+)?$/.test(text)) throw new RangeError(rangeError(count))
  const value = Number(text)
  if (!Number.isFinite(value) || (count ? !Number.isSafeInteger(value) : value > 100)) throw new RangeError(rangeError(count))
  return value
}
function rangeError(count: boolean): string {
  return count ? 'Количество пар должно быть целым неотрицательным числом.' : 'Процент должен быть числом от 0 до 100. Для дробной части используй точку.'
}
function readSearchParams(): URLSearchParams | null { return typeof window === 'undefined' ? null : new URLSearchParams(window.location.search) }
function updateSearchParams(update: (params: URLSearchParams) => void): void {
  if (typeof window === 'undefined') return
  const url = new URL(window.location.href)
  update(url.searchParams)
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
}
function positiveInteger(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}
function setPositive(params: URLSearchParams, key: string, value: number | null): void {
  if (value && Number.isSafeInteger(value) && value > 0) params.set(key, String(value))
  else params.delete(key)
}
