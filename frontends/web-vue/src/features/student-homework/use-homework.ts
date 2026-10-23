import { computed, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import { StudentApiError } from '../../../../mobile-core/src/api/student-client'
import type { StudentHomework, StudentHomeworkItem } from '../../../../mobile-core/src/api/types'
import {
  addHomeworkDays,
  clampHomeworkDate,
  groupHomeworkPeriod,
  homeworkPeriodRange,
  moscowDate,
  shiftHomeworkPeriod,
  type HomeworkPeriodMode,
} from '../../../../mobile-core/src/domain/homework'
import { studentApi } from '../../app/api'
import { message, webSession } from '../../auth/session'

export function useWebHomework() {
  const feed = shallowRef<StudentHomework | null>(null)
  const mode = ref<HomeworkPeriodMode>('day')
  const dates = reactive({ day: '', week: '', month: '' })
  const selected = ref('')
  const loading = ref(true)
  const error = ref<string | null>(null)
  const forbidden = ref(false)
  const itemErrors = reactive<Record<string, string>>({})
  const pending = reactive<Record<string, boolean>>({})
  const desired = reactive<Record<string, boolean>>({})
  const pointerWithin = ref(false)
  const focusWithin = ref(false)
  const interacting = computed(() => pointerWithin.value || focusWithin.value)
  const uncertain = reactive<Record<string, boolean>>({})
  const identity = webSession.generation.value
  let alive = true
  let request = 0
  let mutationRevision = 0
  let timer: ReturnType<typeof setInterval> | undefined
  const current = () => alive && webSession.generation.value === identity
  const semester = computed(() =>
    feed.value ? { from: feed.value.semester.dateFrom, to: feed.value.semester.dateTo } : null,
  )
  const date = computed(() => dates[mode.value])
  const range = computed(
    () => semester.value && homeworkPeriodRange(mode.value, date.value, semester.value),
  )
  const today = computed(() => (feed.value ? moscowDate(feed.value.serverNow) : null))
  const groups = computed(() => {
    if (!feed.value || !range.value) return []
    const visible = {
      ...feed.value,
      items: feed.value.items.map((item) =>
        item.id in desired ? { ...item, completed: desired[item.id]! } : item,
      ),
    }
    const selectedRange =
      mode.value === 'month' ? { from: selected.value, to: selected.value } : range.value
    return groupHomeworkPeriod(visible, selectedRange)
  })
  const calendar = computed(() => {
    if (!date.value) return []
    const start = `${date.value.slice(0, 7)}-01`
    const offset = (new Date(`${start}T12:00:00Z`).getUTCDay() + 6) % 7
    return Array.from({ length: 42 }, (_, index) => {
      const value = addHomeworkDays(start, index - offset)
      return {
        date: value,
        number: Number(value.slice(8)),
        inMonth: value.slice(0, 7) === start.slice(0, 7),
        count: feed.value?.items.filter((item) => item.lessonDate === value).length ?? 0,
      }
    })
  })

  async function load(background = false) {
    const revision = ++request
    const writesAtStart = mutationRevision
    if (!background) loading.value = true
    try {
      const requestedRange = range.value
      const result = await studentApi.getHomework(requestedRange?.from, requestedRange?.to)
      if (!current() || revision !== request || writesAtStart !== mutationRevision) return
      feed.value = result
      forbidden.value = false
      error.value = null
      if (!date.value) {
        const initial = clampHomeworkDate(moscowDate(result.serverNow) ?? result.from, {
          from: result.semester.dateFrom,
          to: result.semester.dateTo,
        })
        dates.day = dates.week = dates.month = initial
        selected.value = initial
      }
    } catch (cause) {
      if (!current() || revision !== request || writesAtStart !== mutationRevision) return
      forbidden.value = cause instanceof StudentApiError && cause.response.status === 403
      error.value = message(cause)
    } finally {
      if (current() && revision === request) loading.value = false
      if (current() && revision === request && writesAtStart !== mutationRevision) void load(true)
    }
  }

  function step(direction: number) {
    if (!semester.value) return
    dates[mode.value] = clampHomeworkDate(
      shiftHomeworkPeriod(mode.value, date.value, direction),
      semester.value,
    )
    if (mode.value === 'month') selected.value = dates.month
  }
  function reset() {
    if (!today.value || !semester.value) return
    dates[mode.value] = clampHomeworkDate(today.value, semester.value)
    selected.value = dates[mode.value]
  }
  function canStep(direction: number) {
    if (!range.value || !semester.value) return false
    return direction < 0
      ? range.value.from > semester.value.from
      : range.value.to < semester.value.to
  }
  async function complete(item: StudentHomeworkItem, completed: boolean) {
    if (pending[item.id] || uncertain[item.id] || webSession.snapshot.value?.readOnly || !current())
      return
    pending[item.id] = true
    mutationRevision++
    desired[item.id] = completed
    delete itemErrors[item.id]
    try {
      const ack = await studentApi.setHomeworkCompletion(item.id, { completed })
      if (
        !ack ||
        ack.id !== item.id ||
        ack.completed !== completed ||
        (completed
          ? !ack.completedAt || !Number.isFinite(Date.parse(ack.completedAt))
          : ack.completedAt !== null)
      )
        throw new Error('Сервер вернул некорректную отметку выполнения')
      if (current() && feed.value)
        feed.value = {
          ...feed.value,
          items: feed.value.items.map((value) =>
            value.id === item.id ? { ...value, ...ack } : value,
          ),
        }
    } catch (cause) {
      if (!current()) return
      // PUT may have committed before the connection failed. Read once, never repeat it.
      try {
        const latest = await studentApi.getHomework(range.value?.from, range.value?.to)
        if (!current()) return
        const actual = latest.items.find((value) => value.id === item.id)
        if (actual && feed.value)
          feed.value = {
            ...feed.value,
            items: feed.value.items.map((value) => (value.id === item.id ? actual : value)),
          }
        if (actual?.completed !== completed) itemErrors[item.id] = message(cause)
      } catch {
        if (current()) {
          uncertain[item.id] = true
          itemErrors[item.id] =
            'Результат неизвестен. Проверь состояние задания перед следующим изменением.'
        }
      }
    } finally {
      mutationRevision++
      if (current()) {
        delete desired[item.id]
        pending[item.id] = false
      }
    }
  }
  async function reconcile(id: string) {
    await load()
    if (!error.value && feed.value?.items.some((item) => item.id === id)) {
      delete uncertain[id]
      delete itemErrors[id]
    }
  }
  watch(
    () => `${mode.value}:${date.value}`,
    (_, old) => {
      if (old && feed.value) void load()
    },
  )
  const refreshVisible = () => {
    if (!document.hidden && !interacting.value && !Object.values(pending).some(Boolean))
      void load(true)
  }
  onMounted(() => {
    void load()
    timer = setInterval(refreshVisible, 15000)
    window.addEventListener('focus', refreshVisible)
    window.addEventListener('online', refreshVisible)
  })
  onUnmounted(() => {
    alive = false
    clearInterval(timer)
    window.removeEventListener('focus', refreshVisible)
    window.removeEventListener('online', refreshVisible)
  })
  return {
    feed,
    mode,
    dates,
    date,
    selected,
    today,
    range,
    groups,
    calendar,
    loading,
    error,
    forbidden,
    itemErrors,
    pending,
    uncertain,
    pointerWithin,
    focusWithin,
    load,
    complete,
    reconcile,
    step,
    reset,
    canStep,
  }
}
