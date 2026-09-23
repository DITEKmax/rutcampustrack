import type { TeacherStatsScope, TeacherStatsSort } from './teacher-client'

const ACTIVE_KEY = 'teacherStats'
const SEMESTER_KEY = 'teacherStatsSemester'
const SCOPE_KEY = 'teacherStatsScope'
const GROUP_KEY = 'teacherStatsGroup'
const SUBJECT_KEY = 'teacherStatsSubject'
const TYPE_KEY = 'teacherStatsType'
const SEARCH_KEY = 'teacherStatsSearch'
const SORT_KEY = 'teacherStatsSort'
const SORT_DESCENDING_KEY = 'teacherStatsSortDescending'

export interface TeacherStatsRouteContext {
  readonly semesterId: number | null
  readonly scope: TeacherStatsScope
  readonly groupId: number | null
  readonly subjectId: number | null
  readonly lessonTypes: readonly string[]
  readonly search: string
  readonly sort: TeacherStatsSort
}

const DEFAULT_CONTEXT: TeacherStatsRouteContext = {
  semesterId: null,
  scope: 'groups',
  groupId: null,
  subjectId: null,
  lessonTypes: [],
  search: '',
  sort: { column: 'present', descending: false },
}

export function hasTeacherStatsRoute(): boolean {
  return readSearchParams()?.get(ACTIVE_KEY) === '1'
}

export function activateTeacherStatsRoute(): void {
  updateSearchParams((params) => params.set(ACTIVE_KEY, '1'))
}

export function clearTeacherStatsRoute(): void {
  updateSearchParams((params) => {
    for (const key of [
      ACTIVE_KEY,
      SEMESTER_KEY,
      SCOPE_KEY,
      GROUP_KEY,
      SUBJECT_KEY,
      TYPE_KEY,
      SEARCH_KEY,
      SORT_KEY,
      SORT_DESCENDING_KEY,
    ]) params.delete(key)
  })
}

export function readTeacherStatsContext(): TeacherStatsRouteContext | null {
  const params = readSearchParams()
  if (!params || params.get(ACTIVE_KEY) !== '1') return null

  const scope = params.get(SCOPE_KEY)
  return {
    semesterId: positiveInteger(params.get(SEMESTER_KEY)),
    scope: scope === 'students' ? 'students' : DEFAULT_CONTEXT.scope,
    groupId: positiveInteger(params.get(GROUP_KEY)),
    subjectId: positiveInteger(params.get(SUBJECT_KEY)),
    lessonTypes: params.getAll(TYPE_KEY)
      .map((value) => value.trim())
      .filter((value, index, values) => value.length > 0 && index === values.indexOf(value))
      .slice(0, 50),
    search: (params.get(SEARCH_KEY) ?? '').trim().slice(0, 120),
    sort: {
      column: params.get(SORT_KEY) ?? DEFAULT_CONTEXT.sort.column,
      descending: params.get(SORT_DESCENDING_KEY) === '1',
    },
  }
}

export function writeTeacherStatsContext(context: TeacherStatsRouteContext): void {
  updateSearchParams((params) => {
    params.set(ACTIVE_KEY, '1')
    if (context.semesterId && context.semesterId > 0) params.set(SEMESTER_KEY, String(context.semesterId))
    else params.delete(SEMESTER_KEY)
    params.set(SCOPE_KEY, context.scope)
    setPositive(params, GROUP_KEY, context.groupId)
    setPositive(params, SUBJECT_KEY, context.subjectId)
    params.delete(TYPE_KEY)
    for (const lessonType of context.lessonTypes.slice(0, 50)) {
      const value = lessonType.trim()
      if (value) params.append(TYPE_KEY, value)
    }
    if (context.search.trim()) params.set(SEARCH_KEY, context.search.trim().slice(0, 120))
    else params.delete(SEARCH_KEY)
    if (context.sort.column.trim()) params.set(SORT_KEY, context.sort.column.trim())
    else params.delete(SORT_KEY)
    if (context.sort.descending) params.set(SORT_DESCENDING_KEY, '1')
    else params.delete(SORT_DESCENDING_KEY)
  })
}

function readSearchParams(): URLSearchParams | null {
  if (typeof window === 'undefined') return null
  return new URLSearchParams(window.location.search)
}

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
