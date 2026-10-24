import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { createWebTransport } from '../../app/api'
import { useWebLiveRefresh } from '../../app/live'
import { webSession } from '../../auth/session'
import {
  TeacherApi,
  TeacherApiError,
  type TeacherCatalogResponse,
  type TeacherSemester,
} from '../../../../mobile-core/src/features/teacher/teacher-client'

export function teacherError(cause: unknown): string {
  if (cause instanceof TypeError) return 'Не удалось связаться с сервером. Повтори попытку.'
  if (cause instanceof DOMException && cause.name === 'AbortError') return 'Сервер не ответил вовремя. Повтори попытку.'
  if (cause instanceof TeacherApiError) {
    if (cause.response.status === 403)
      return 'Доступ изменился. Доступны только твои текущие группы и назначенные предметы.'
    if (cause.response.status === 404) return 'Данные больше недоступны.'
    if (cause.response.status === 410) return 'Срок хранения файла истёк.'
  }
  return cause instanceof Error ? cause.message : 'Не удалось получить данные. Повтори попытку.'
}

/** One page owns its transport and all decoded responses. No cross-role continuation. */
export function useTeacherScope() {
  const transport = createWebTransport()
  const api = new TeacherApi(transport)
  const identity = webSession.identity.value
  let alive = true
  const current = () => alive && webSession.identity.value === identity
  onBeforeUnmount(() => {
    alive = false
  })
  const catalog = shallowRef<TeacherCatalogResponse | null>(null)
  const semester = shallowRef<TeacherSemester | null>(null)
  const loading = ref(false)
  const error = ref('')
  const refreshed = ref(0)
  const pointerWithin = ref(false)
  const focusWithin = ref(false)
  const dirty = ref(false)
  const blocked = computed(() => pointerWithin.value || focusWithin.value || dirty.value)
  let revision = 0
  async function load() {
    const run = ++revision
    loading.value = true
    error.value = ''
    try {
      const nextSemester = await api.semester()
      const nextCatalog = await api.catalog(nextSemester.id)
      transport.assertCurrent()
      if (!current() || run !== revision) return
      semester.value = nextSemester
      catalog.value = nextCatalog
      refreshed.value++
    } catch (cause) {
      if (!current() || run !== revision) return
      catalog.value = null
      semester.value = null
      error.value = teacherError(cause)
    } finally {
      if (current() && run === revision) loading.value = false
    }
  }
  watch(() => webSession.identity.value, () => {
    revision++
    catalog.value = null
    semester.value = null
    loading.value = false
  }, { flush: 'sync' })
  onMounted(load)
  useWebLiveRefresh(load, { blocked })
  return {
    transport,
    api,
    current,
    catalog,
    semester,
    loading,
    error,
    refreshed,
    load,
    pointerWithin,
    focusWithin,
    dirty,
    setPointerWithin: (value: boolean) => { pointerWithin.value = value },
    setFocusWithin: (value: boolean) => { focusWithin.value = value },
  }
}

export type TeacherScope = ReturnType<typeof useTeacherScope>

export function useTeacherResource<T>(scope: TeacherScope) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref('')
  let revision = 0
  function clear() {
    revision++
    data.value = null
    error.value = ''
    loading.value = false
  }
  async function load(request: () => Promise<T>, append?: (old: T | null, next: T) => T) {
    const run = ++revision
    loading.value = true
    error.value = ''
    if (!append) data.value = null
    try {
      const next = await request()
      scope.transport.assertCurrent()
      if (!scope.current() || run !== revision) return
      data.value = append ? append(data.value, next) : next
    } catch (cause) {
      if (!scope.current() || run !== revision) return
      data.value = null
      error.value = teacherError(cause)
    } finally {
      if (scope.current() && run === revision) loading.value = false
    }
  }
  return { data, loading, error, load, clear }
}

export function useTeacherSelection(scope: TeacherScope) {
  const route = useRoute()
  const groupId = ref(Number(route.query.group) || 0)
  const subjectId = ref(Number(route.query.subject) || 0)
  const lessonTypes = ref<string[]>(typeof route.query.type === 'string' ? [route.query.type] : [])
  const groups = computed(() => scope.catalog.value?.groups ?? [])
  const subjects = computed(
    () =>
      scope.catalog.value?.subjectOptions.filter((item) => item.groupId === groupId.value) ?? [],
  )
  const types = computed(
    () => subjects.value.find((item) => item.subjectId === subjectId.value)?.lessonTypes ?? [],
  )
  function reconcile() {
    if (!groups.value.some((item) => item.id === groupId.value))
      groupId.value = groups.value[0]?.id ?? 0
    if (!subjects.value.some((item) => item.subjectId === subjectId.value))
      subjectId.value = subjects.value[0]?.subjectId ?? 0
    lessonTypes.value = lessonTypes.value.filter((type) => types.value.includes(type))
    if (!lessonTypes.value.length) lessonTypes.value = [...types.value]
  }
  const ready = computed(() =>
    Boolean(
      scope.semester.value &&
        groupId.value &&
        subjectId.value &&
        lessonTypes.value.length &&
        lessonTypes.value.every((type) => types.value.includes(type)),
    ),
  )
  return { groups, subjects, types, groupId, subjectId, lessonTypes, ready, reconcile,
    setGroup(value: number) { groupId.value = value; subjectId.value = 0; reconcile() },
    setSubject(value: number) { subjectId.value = value; lessonTypes.value = []; reconcile() },
    setTypes(value: string[]) { lessonTypes.value = value },
  }
}

export type TeacherSelection = ReturnType<typeof useTeacherSelection>
export function shortDate(value: string): string {
  return new Intl.DateTimeFormat('ru', { day: 'numeric', month: 'short' }).format(
    new Date(`${value}T12:00:00Z`),
  )
}

export function today(): string {
  return new Intl.DateTimeFormat('sv', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}
