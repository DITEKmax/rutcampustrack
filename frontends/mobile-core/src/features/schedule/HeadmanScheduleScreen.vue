<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import MobileShell from '../../shared/components/MobileShell.vue'
import type { CampusMapClient } from '../../api/map-client'
import MapScreen from '../map/MapScreen.vue'
import { createStudentNavigationItems } from '../../shared/mobile-navigation-items'
import {
  createMobileNavigationStack,
  nestedRoute,
  type MobileBottomNavItems,
  type MobileNavigationStack,
  type MobileRoute,
} from '../../shared/navigation'
import type { MobileHostAdapter } from '../../shared/host'
import type { ProfileSnapshot } from '../profile/profile-types'
import HeadmanJournalScreen from '../headman-journal/HeadmanJournalScreen.vue'
import type { HeadmanJournalApi } from '../headman-journal/headman-journal-client'
import HeadmanRequestsScreen from '../headman-requests/HeadmanRequestsScreen.vue'
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import HeadmanGroupScreen from '../headman-group/HeadmanGroupScreen.vue'
import type { HeadmanGroupApi } from '../headman-group/headman-group-client'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import {
  HeadmanScheduleApiError,
  type HeadmanScheduleApi,
  type HeadmanScheduleAssignment,
  type HeadmanScheduleCreateInput,
  type HeadmanScheduleItem,
  type HeadmanScheduleSemester,
} from './headman-schedule-client'
import './headman-schedule-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanScheduleApi | null
  journalApi?: HeadmanJournalApi | null
  requestsApi?: HeadmanRequestsApi | null
  groupApi?: HeadmanGroupApi | null
  assistantPermissions?: readonly HeadmanAssistantPermission[] | null
  profile: ProfileSnapshot | null
  groupId: number | null
  mapClient?: CampusMapClient | null
  offline?: boolean
  readOnly?: boolean
  host?: MobileHostAdapter | null
  navItems?: MobileBottomNavItems
  onRoleSwitch?: (() => void | Promise<void>) | undefined
}>(), {
  journalApi: null,
  requestsApi: null,
  groupApi: null,
  assistantPermissions: null,
  mapClient: null,
  offline: false,
  readOnly: false,
  host: null,
  navItems: undefined as never,
  onRoleSwitch: undefined,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const navigation: MobileNavigationStack = createMobileNavigationStack(
  nestedRoute('more', 'more/headman-schedule/list', 'task'),
)
const route = shallowRef<MobileRoute>(navigation.current)
const navigationItems = computed(() => props.navItems ?? createStudentNavigationItems())
const loading = ref(true)
const error = ref<string | null>(null)
const denied = ref(false)
const assignments = shallowRef<readonly HeadmanScheduleAssignment[]>([])
const scheduleItems = shallowRef<readonly HeadmanScheduleItem[]>([])
const semester = shallowRef<HeadmanScheduleSemester | null>(null)
const selectedDay = ref(1)
const formOpen = ref(false)
const journalOpen = ref(false)
const requestsOpen = ref(false)
const groupOpen = ref(false)
const formBusy = ref(false)
const formError = ref<string | null>(null)
const notice = ref<string | null>(null)
const commandKey = ref<string | null>(null)
const assignmentId = ref<number | null>(null)
const weekType = ref<'ALL' | 'ODD' | 'EVEN'>('ALL')
const lessonNumber = ref<number | null>(null)
const startTime = ref('')
const endTime = ref('')
const room = ref('')
const commandFingerprint = ref<string | null>(null)
const journalRouteId = 'more/headman-journal/journal' as const
const requestsRouteId = 'more/headman-requests/list' as const
const groupRouteId = 'more/headman-group/list' as const
let loadRevision = 0
let stopNavigation = navigation.subscribe(() => {
  const next = navigation.current
  const previous = route.value
  route.value = next
  if (next.kind === 'root' && previous.kind === 'nested') {
    formOpen.value = false
    journalOpen.value = false
    requestsOpen.value = false
    groupOpen.value = false
    void props.onRoleSwitch?.()
  } else if (next.id === journalRouteId) {
    formOpen.value = false
    journalOpen.value = true
    requestsOpen.value = false
    groupOpen.value = false
  } else if (next.id === requestsRouteId) {
    formOpen.value = false
    journalOpen.value = false
    requestsOpen.value = true
    groupOpen.value = false
  } else if (next.id === groupRouteId) {
    formOpen.value = false
    journalOpen.value = false
    requestsOpen.value = false
    groupOpen.value = true
  } else if (next.id === 'more/headman-schedule/list') {
    formOpen.value = false
    journalOpen.value = false
    requestsOpen.value = false
    groupOpen.value = false
  } else if (next.id === 'more/headman-schedule/form') {
    journalOpen.value = false
    requestsOpen.value = false
    formOpen.value = true
  }
})

const days = [
  { value: 1, label: 'Пн' },
  { value: 2, label: 'Вт' },
  { value: 3, label: 'Ср' },
  { value: 4, label: 'Чт' },
  { value: 5, label: 'Пт' },
  { value: 6, label: 'Сб' },
] as const

const activeAssignments = computed(() => assignments.value.filter((item) => item.id !== undefined && item.subjectId !== undefined))
const selectedAssignment = computed(() => activeAssignments.value.find((item) => item.id === assignmentId.value) ?? null)
const dayItems = computed(() => scheduleItems.value
  .filter((item) => item.dayOfWeek === selectedDay.value)
  .slice()
  .sort((left, right) => (left.lessonNumber ?? 0) - (right.lessonNumber ?? 0)))
const stateMessage = computed(() => {
  if (props.offline) return 'Изменения доступны только при подключении к интернету.'
  if (denied.value) return 'Роль старосты не привязана к учебной группе.'
  if (semester.value === null && !loading.value) return 'Нет активного семестра для заполнения расписания.'
  if (activeAssignments.value.length === 0 && !loading.value) return 'Для группы пока нет назначений преподавателей.'
  return null
})

function parityLabel(value: HeadmanScheduleItem['weekType']): string {
  if (value === 'ODD') return '1 неделя'
  if (value === 'EVEN') return '2 неделя'
  return '1 и 2 недели'
}

function assignmentLabel(item: HeadmanScheduleAssignment): string {
  const subject = item.subjectName?.trim() || 'Предмет без названия'
  const teacher = item.teacherName?.trim()
  return teacher ? `${subject} · ${teacher}` : subject
}

function newCommandKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `headman-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function openForm(): void {
  if (props.offline || props.readOnly || loading.value || semester.value === null || activeAssignments.value.length === 0) return
  formError.value = null
  notice.value = null
  commandKey.value = newCommandKey()
  commandFingerprint.value = null
  assignmentId.value = activeAssignments.value[0]?.id ?? null
  weekType.value = 'ALL'
  lessonNumber.value = null
  startTime.value = ''
  endTime.value = ''
  room.value = ''
  formOpen.value = true
  navigation.push(nestedRoute('more', 'more/headman-schedule/form', 'editor'))
}

function closeForm(): void {
  if (formBusy.value) return
  navigation.replace(nestedRoute('more', 'more/headman-schedule/list', 'task'))
}

function openJournal(): void {
  if (!props.journalApi || props.groupId === null) return
  journalOpen.value = true
  navigation.push(nestedRoute('more', journalRouteId, 'task'))
}

function openRequests(): void {
  if (!props.requestsApi || props.offline) return
  requestsOpen.value = true
  navigation.push(nestedRoute('more', requestsRouteId, 'task'))
}

function openGroup(): void {
  if (!props.groupApi || props.groupId === null || props.offline) return
  groupOpen.value = true
  navigation.push(nestedRoute('more', groupRouteId, 'task'))
}

function closeRequests(): void {
  navigation.replace(nestedRoute('more', 'more/headman-schedule/list', 'task'))
}

function openMap(): void {
  if (!props.mapClient || props.offline) return
  navigation.push(nestedRoute('more', 'more/map', 'overview'))
}

function validTime(value: string): boolean {
  return /^\d{2}:\d{2}$/.test(value) && Number(value.slice(0, 2)) < 24 && Number(value.slice(3)) < 60
}

function buildInput(): HeadmanScheduleCreateInput | null {
  const selected = selectedAssignment.value
  const currentSemester = semester.value
  if (!props.groupId || !currentSemester?.id || !selected?.id || !selected.subjectId) {
    formError.value = 'Не удалось определить группу, семестр или предмет.'
    return null
  }
  if (!lessonNumber.value || !Number.isSafeInteger(lessonNumber.value) || lessonNumber.value < 1) {
    formError.value = 'Укажи номер пары.'
    return null
  }
  if (!validTime(startTime.value) || !validTime(endTime.value)) {
    formError.value = 'Укажи время начала и конца в формате ЧЧ:ММ.'
    return null
  }
  if (startTime.value >= endTime.value) {
    formError.value = 'Время конца должно быть позже времени начала.'
    return null
  }
  return {
    assignmentId: selected.id,
    groupId: props.groupId,
    subjectId: selected.subjectId,
    semesterId: currentSemester.id,
    dayOfWeek: selectedDay.value,
    lessonNumber: lessonNumber.value,
    startTime: `${startTime.value}:00`,
    endTime: `${endTime.value}:00`,
    weekType: weekType.value,
    ...(room.value.trim() ? { room: room.value.trim() } : {}),
  }
}

async function save(): Promise<void> {
  if (formBusy.value || props.offline || props.readOnly || !props.api) return
  formError.value = null
  const input = buildInput()
  if (!input) return
  const nextFingerprint = JSON.stringify(input)
  if (commandFingerprint.value !== null && commandFingerprint.value !== nextFingerprint) commandKey.value = newCommandKey()
  commandFingerprint.value = nextFingerprint
  const key = commandKey.value ?? newCommandKey()
  commandKey.value = key
  formBusy.value = true
  try {
    await props.api.createScheduleItem(input, key)
    notice.value = 'Слот сохранён. Расписание обновлено с сервера.'
    commandKey.value = null
    commandFingerprint.value = null
    closeForm()
    await load()
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : 'Не удалось сохранить слот.'
    emit('error', cause)
  } finally {
    formBusy.value = false
  }
}

async function load(): Promise<void> {
  const revision = ++loadRevision
  loading.value = true
  error.value = null
  denied.value = false
  if (props.offline) {
    loading.value = false
    return
  }
  if (!props.api || props.groupId === null) {
    denied.value = true
    loading.value = false
    return
  }
  try {
    const semesters = await props.api.listSemesters()
    if (revision !== loadRevision) return
    const current = semesters.find((candidate) => candidate.active === true) ?? null
    semester.value = current
    if (!current?.id) {
      assignments.value = []
      scheduleItems.value = []
      return
    }
    const [nextAssignments, nextItems] = await Promise.all([
      props.api.listAssignments(props.groupId, current.id),
      props.api.listScheduleItems(props.groupId, current.id),
    ])
    if (revision !== loadRevision) return
    assignments.value = nextAssignments
    scheduleItems.value = nextItems
  } catch (cause) {
    if (revision !== loadRevision) return
    if (cause instanceof HeadmanScheduleApiError && (cause.response.status === 401 || cause.response.status === 403)) {
      denied.value = true
    } else {
      error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить расписание.'
      emit('error', cause)
    }
  } finally {
    if (revision === loadRevision) loading.value = false
  }
}

watch(
  () => [props.api, props.groupId, props.offline] as const,
  () => { void load() },
  { immediate: true },
)

watch(
  () => [props.host, formOpen.value, formBusy.value, props.offline, props.readOnly] as const,
  ([host, open, busy, offline, readOnly]) => {
    if (!host || host.primaryActionOwner !== 'host') return
    host.setPrimaryAction?.(open
      ? { label: busy ? 'Сохраняем…' : 'Сохранить слот', disabled: busy || offline || readOnly, onInvoke: save }
      : null)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  loadRevision += 1
  stopNavigation()
  props.host?.setPrimaryAction?.(null)
})
</script>

<template>
  <MobileShell
    :route="route"
    :navigation="navigation"
    :nav-items="navigationItems"
    active-id="more"
    :host="host"
    back-label="Назад"
  >
    <MapScreen
      v-if="route.id === 'more/map' && props.mapClient"
      :client="props.mapClient"
    />
    <HeadmanJournalScreen
      v-else-if="journalOpen"
      :api="journalApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      @error="emit('error', $event)"
    />
    <HeadmanRequestsScreen
      v-else-if="requestsOpen"
      :api="requestsApi"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      @back="closeRequests"
      @error="emit('error', $event)"
    />
    <HeadmanGroupScreen
      v-else-if="groupOpen"
      :api="groupApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      @error="emit('error', $event)"
    />
    <main
      v-else
      class="headman-schedule"
      aria-labelledby="headman-schedule-title"
    >
      <header class="headman-schedule__header">
        <div>
          <p class="headman-schedule__eyebrow">
            Староста · расписание
          </p>
          <h1 id="headman-schedule-title">
            Расписание группы
          </h1>
          <p class="headman-schedule__context">
            {{ profile?.roles.find((grant) => grant.role === 'HEADMAN')?.contextLabel || 'Авторизованная группа' }}
          </p>
        </div>
        <button
          class="headman-schedule__role-button"
          type="button"
          @click="onRoleSwitch"
        >
          Сменить роль
        </button>
      </header>

      <p
        v-if="offline"
        class="headman-schedule__state"
        role="status"
      >
        Просмотр и изменение расписания доступны только онлайн.
      </p>
      <p
        v-if="error"
        class="headman-schedule__state headman-schedule__state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="notice"
        class="headman-schedule__state headman-schedule__state--success"
        role="status"
      >
        {{ notice }}
      </p>

      <button
        v-if="journalApi && groupId !== null"
        class="headman-schedule__journal"
        type="button"
        :disabled="offline"
        @click="openJournal"
      >
        Открыть журнал посещаемости
      </button>
      <button
        v-if="requestsApi"
        class="headman-schedule__journal"
        type="button"
        :disabled="offline"
        @click="openRequests"
      >
        Открыть заявки группы
      </button>
      <button
        v-if="groupApi && groupId !== null"
        class="headman-schedule__journal"
        type="button"
        :disabled="offline"
        @click="openGroup"
      >
        Управление помощниками
      </button>
      <button
        v-if="mapClient"
        class="headman-schedule__journal"
        type="button"
        :disabled="offline"
        @click="openMap"
      >
        Открыть карту кампуса
      </button>

      <section
        v-if="loading"
        class="headman-schedule__state"
        aria-live="polite"
      >
        Загружаем семестр, назначения и расписание…
      </section>
      <section
        v-else-if="stateMessage"
        class="headman-schedule__state"
        :data-denied="denied"
      >
        {{ stateMessage }}
      </section>
      <template v-else>
        <div
          class="headman-schedule__days"
          role="tablist"
          aria-label="День недели"
        >
          <button
            v-for="day in days"
            :key="day.value"
            class="headman-schedule__day"
            :data-selected="selectedDay === day.value"
            type="button"
            role="tab"
            :aria-selected="selectedDay === day.value"
            @click="selectedDay = day.value"
          >
            {{ day.label }}
          </button>
        </div>

        <section
          class="headman-schedule__list"
          aria-live="polite"
        >
          <p
            v-if="dayItems.length === 0"
            class="headman-schedule__empty"
          >
            В этот день слотов пока нет.
          </p>
          <article
            v-for="item in dayItems"
            :key="item.id ?? `${item.lessonNumber}-${item.weekType}`"
            class="headman-schedule__slot"
          >
            <div class="headman-schedule__slot-time">
              <strong>{{ item.lessonNumber ?? '—' }} пара</strong>
              <span>{{ item.startTime ?? '—' }}–{{ item.endTime ?? '—' }}</span>
            </div>
            <div class="headman-schedule__slot-copy">
              <strong>{{ assignments.find((candidate) => candidate.id === item.assignmentId)?.subjectName || 'Предмет' }}</strong>
              <span>{{ parityLabel(item.weekType) }}<span v-if="item.room"> · {{ item.room }}</span></span>
            </div>
          </article>
        </section>

        <button
          class="headman-schedule__primary"
          type="button"
          :disabled="offline || readOnly || activeAssignments.length === 0"
          @click="openForm"
        >
          Добавить слот
        </button>
      </template>

      <section
        v-if="formOpen"
        class="headman-schedule__form"
        aria-labelledby="headman-schedule-form-title"
      >
        <div class="headman-schedule__form-header">
          <h2 id="headman-schedule-form-title">
            Новый слот · {{ days.find((day) => day.value === selectedDay)?.label }}
          </h2>
          <button
            class="headman-schedule__secondary"
            type="button"
            :disabled="formBusy"
            @click="closeForm"
          >
            Отмена
          </button>
        </div>
        <label class="headman-schedule__field">
          <span>Предмет и преподаватель</span>
          <select
            v-model.number="assignmentId"
            :disabled="formBusy || offline || readOnly"
          >
            <option
              v-for="item in activeAssignments"
              :key="item.id"
              :value="item.id"
            >
              {{ assignmentLabel(item) }}
            </option>
          </select>
        </label>
        <fieldset class="headman-schedule__field">
          <legend>Неделя</legend>
          <div class="headman-schedule__parity">
            <button
              type="button"
              :data-selected="weekType === 'ODD'"
              :disabled="formBusy || offline || readOnly"
              @click="weekType = 'ODD'"
            >
              1
            </button>
            <button
              type="button"
              :data-selected="weekType === 'EVEN'"
              :disabled="formBusy || offline || readOnly"
              @click="weekType = 'EVEN'"
            >
              2
            </button>
            <button
              type="button"
              :data-selected="weekType === 'ALL'"
              :disabled="formBusy || offline || readOnly"
              @click="weekType = 'ALL'"
            >
              Обе
            </button>
          </div>
        </fieldset>
        <div class="headman-schedule__field-row">
          <label class="headman-schedule__field">
            <span>Номер пары</span>
            <input
              v-model.number="lessonNumber"
              min="1"
              max="20"
              inputmode="numeric"
              type="number"
              :disabled="formBusy || offline || readOnly"
            >
          </label>
          <label class="headman-schedule__field">
            <span>Аудитория</span>
            <input
              v-model="room"
              type="text"
              autocomplete="off"
              :disabled="formBusy || offline || readOnly"
            >
          </label>
        </div>
        <div class="headman-schedule__field-row">
          <label class="headman-schedule__field">
            <span>Начало</span>
            <input
              v-model="startTime"
              type="time"
              :disabled="formBusy || offline || readOnly"
            >
          </label>
          <label class="headman-schedule__field">
            <span>Конец</span>
            <input
              v-model="endTime"
              type="time"
              :disabled="formBusy || offline || readOnly"
            >
          </label>
        </div>
        <p
          v-if="formError"
          class="headman-schedule__form-error"
          role="alert"
        >
          {{ formError }}
        </p>
        <button
          class="headman-schedule__primary"
          type="button"
          :disabled="formBusy || offline || readOnly"
          @click="save"
        >
          {{ formBusy ? 'Сохраняем…' : 'Сохранить слот' }}
        </button>
      </section>
    </main>
  </MobileShell>
</template>
