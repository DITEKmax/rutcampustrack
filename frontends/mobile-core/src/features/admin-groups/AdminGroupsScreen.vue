<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  AdminGroupsApiError,
  type AdminGroup,
  type AdminGroupStatus,
  type AdminGroupsClient,
} from './admin-groups-client'
import './admin-groups-screen.pcss'

const props = withDefaults(defineProps<{
  client: AdminGroupsClient
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
}>()

const groups = ref<readonly AdminGroup[]>([])
const status = ref<AdminGroupStatus>('ACTIVE')
const search = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const counts = ref({ active: 0, draft: 0, archived: 0 })
const loading = ref(true)
const saving = ref(false)
const formVisible = ref(false)
const alphabeticCode = ref('')
const numericCode = ref('')
const trainingDurationYears = ref('')
const error = ref<string | null>(null)
const notice = ref<string | null>(null)

let disposed = false
let listRevision = 0
let listController: AbortController | null = null

const tabs: readonly { value: AdminGroupStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Активные' },
  { value: 'DRAFT', label: 'Черновики' },
  { value: 'ARCHIVED', label: 'Архив' },
]

onMounted(() => void refresh())

async function refresh(): Promise<void> {
  const revision = ++listRevision
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  loading.value = true
  error.value = null
  try {
    const result = await props.client.listGroups({
      status: status.value,
      search: search.value,
      page: page.value,
      size: 20,
    }, controller.signal)
    if (!isCurrent(revision, controller)) return
    groups.value = result.items
    totalPages.value = result.totalPages
    totalElements.value = result.totalElements
    counts.value = {
      active: result.activeCount,
      draft: result.draftCount,
      archived: result.archivedCount,
    }
    if (result.totalPages > 0 && page.value >= result.totalPages) {
      page.value = result.totalPages - 1
      return
    }
  } catch (cause) {
    if (!isCurrent(revision, controller) || cause instanceof StaleSessionGenerationError || isAbortError(cause)) return
    showError(cause, 'Реестр групп не удалось загрузить.')
  } finally {
    if (isCurrent(revision, controller)) {
      loading.value = false
      listController = null
    }
  }
}

function isCurrent(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRevision && listController === controller
}

function selectStatus(next: AdminGroupStatus): void {
  if (status.value === next) return
  status.value = next
  page.value = 0
  void refresh()
}

function submitSearch(): void {
  page.value = 0
  void refresh()
}

async function createGroup(): Promise<void> {
  const duration = Number(trainingDurationYears.value)
  if (!alphabeticCode.value.trim() || !numericCode.value.trim() || !Number.isInteger(duration)) {
    error.value = 'Укажи буквенный код, трёхзначный цифровой код и срок обучения.'
    return
  }
  saving.value = true
  error.value = null
  notice.value = null
  try {
    await props.client.createGroup({
      alphabeticCode: alphabeticCode.value,
      numericCode: numericCode.value,
      trainingDurationYears: duration,
    })
    if (disposed) return
    alphabeticCode.value = ''
    numericCode.value = ''
    trainingDurationYears.value = ''
    formVisible.value = false
    status.value = 'DRAFT'
    page.value = 0
    search.value = ''
    notice.value = 'Группа создана и добавлена в черновики.'
    await refresh()
  } catch (cause) {
    if (!disposed && !(cause instanceof StaleSessionGenerationError)) showError(cause, 'Группу не удалось создать.')
  } finally {
    if (!disposed) saving.value = false
  }
}

function showError(cause: unknown, fallback: string): void {
  error.value = cause instanceof AdminGroupsApiError
    ? cause.problem?.detail ?? cause.message
    : cause instanceof Error ? cause.message : fallback
  if (cause instanceof AdminGroupsApiError && (cause.response.status === 401 || cause.response.status === 403)) {
    emit('ownerError', cause)
  }
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

function statusCount(value: AdminGroupStatus): number {
  return value === 'ACTIVE' ? counts.value.active : value === 'DRAFT' ? counts.value.draft : counts.value.archived
}

function displayCode(group: AdminGroup): string {
  if (group.alphabeticCode && group.numericCode) return `${group.alphabeticCode}-${group.numericCode}`
  return group.name
}

function durationLabel(group: AdminGroup): string {
  return group.durationStatus === 'KNOWN' && group.trainingDurationYears !== null
    ? `${group.trainingDurationYears} лет`
    : 'Не определён'
}

function formatPage(): string {
  return totalElements.value === 0 ? '0 групп' : `${page.value + 1} / ${totalPages.value}`
}

onBeforeUnmount(() => {
  disposed = true
  listRevision += 1
  listController?.abort()
  listController = null
})
</script>

<template>
  <main
    class="admin-groups-screen"
    :data-theme="theme"
    aria-labelledby="admin-groups-title"
  >
    <header class="admin-groups-screen__header">
      <p>Администрирование</p>
      <div class="admin-groups-screen__heading-row">
        <h1 id="admin-groups-title">Группы</h1>
        <button
          class="admin-groups-screen__new"
          type="button"
          :aria-expanded="formVisible"
          @click="formVisible = !formVisible; error = null; notice = null"
        >
          {{ formVisible ? 'Скрыть форму' : '+ Создать группу' }}
        </button>
      </div>
      <p class="admin-groups-screen__description">
        Новая группа появится в черновиках, пока ей не назначен староста.
      </p>
    </header>

    <form
      v-if="formVisible"
      class="admin-groups-card admin-groups-form"
      aria-labelledby="admin-groups-form-title"
      @submit.prevent="createGroup"
    >
      <h2 id="admin-groups-form-title">Новая группа</h2>
      <div class="admin-groups-form__fields">
        <label>
          <span>Буквенный код</span>
          <input v-model="alphabeticCode" maxlength="4" required type="text" autocomplete="off">
        </label>
        <label>
          <span>Цифровой код</span>
          <input v-model="numericCode" inputmode="numeric" maxlength="3" pattern="\d{3}" required type="text" autocomplete="off">
        </label>
        <label>
          <span>Срок обучения, лет</span>
          <input v-model="trainingDurationYears" min="1" required type="number">
        </label>
      </div>
      <p class="admin-groups-form__hint">Текущий курс определяется первой цифрой цифрового кода.</p>
      <button class="admin-groups-action" type="submit" :disabled="saving" :aria-busy="saving">
        {{ saving ? 'Сохраняем…' : 'Создать и открыть черновик' }}
      </button>
    </form>

    <p v-if="error" class="admin-groups-state admin-groups-state--error" role="alert">{{ error }}</p>
    <p v-if="notice" class="admin-groups-state admin-groups-state--success" role="status">{{ notice }}</p>

    <section class="admin-groups-card" aria-label="Реестр групп">
      <div class="admin-groups-tabs" role="tablist" aria-label="Разрез групп">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="status === tab.value"
          class="admin-groups-tabs__item"
          :class="{ 'admin-groups-tabs__item--active': status === tab.value }"
          @click="selectStatus(tab.value)"
        >
          {{ tab.label }} <span aria-hidden="true">({{ statusCount(tab.value) }})</span>
          <span class="sr-only">: {{ statusCount(tab.value) }} групп</span>
        </button>
      </div>
      <form class="admin-groups-search" role="search" @submit.prevent="submitSearch">
        <label for="admin-groups-search-input">Поиск по названию группы</label>
        <input id="admin-groups-search-input" v-model="search" type="search" autocomplete="off">
        <button type="submit">Найти</button>
      </form>

      <p v-if="loading" class="admin-groups-state" role="status">Загружаем группы…</p>
      <div v-else-if="groups.length === 0" class="admin-groups-empty">
        <h2>{{ search ? 'Ничего не найдено' : 'В этом разрезе пока нет групп' }}</h2>
        <p>{{ search ? 'Измени поиск и попробуй снова.' : 'Создай группу — она появится в черновиках.' }}</p>
      </div>
      <div v-else class="admin-groups-table-wrap">
        <table class="admin-groups-table">
          <caption class="sr-only">Группы в разрезе {{ status.toLowerCase() }}</caption>
          <thead>
            <tr>
              <th scope="col">Код группы</th>
              <th scope="col">Курс</th>
              <th scope="col">Срок обучения, лет</th>
              <th scope="col">Студентов</th>
              <th scope="col">Кто староста</th>
              <th v-if="status === 'DRAFT'" scope="col">Почему черновик</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="group in groups" :key="group.id">
              <th scope="row">{{ displayCode(group) }}</th>
              <td>{{ group.currentCourse ?? '—' }}</td>
              <td>{{ durationLabel(group) }}</td>
              <td>{{ group.studentCount }}</td>
              <td>{{ group.headmanFio ?? 'Не назначен' }}</td>
              <td v-if="status === 'DRAFT'">{{ group.draftReason ?? 'Причина не указана' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="!loading" class="admin-groups-pagination">
        <span>{{ totalElements }} групп · {{ formatPage() }}</span>
        <div>
          <button type="button" :disabled="page === 0" @click="page -= 1; refresh()">Назад</button>
          <button type="button" :disabled="totalPages === 0 || page + 1 >= totalPages" @click="page += 1; refresh()">Дальше</button>
        </div>
      </footer>
    </section>
  </main>
</template>
