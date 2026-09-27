<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  AdminGroupsApiError,
  type AdminGroup,
  type AdminGroupStatus,
  type HeadmanAssignmentPreview,
  type HeadmanRoster,
  type AdminGroupsClient,
  type PromotionSummary,
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
const assignmentGroup = ref<AdminGroup | null>(null)
const headmanRoster = ref<HeadmanRoster | null>(null)
const selectedStudentId = ref<number | null>(null)
const assignmentPreview = ref<HeadmanAssignmentPreview | null>(null)
const headmanLoading = ref(false)
const headmanSaving = ref(false)
const headmanMutationPending = ref(false)
const promotionPanelVisible = ref(false)
const promotionScopeGroup = ref<AdminGroup | null>(null)
const promotionPreview = ref<PromotionSummary | null>(null)
const promotionResult = ref<PromotionSummary | null>(null)
const promotionLoading = ref(false)
const promotionSaving = ref(false)
const promotionMutationPending = ref(false)
const promotionError = ref<string | null>(null)
const promotionConfirmDialog = ref<HTMLDialogElement | null>(null)

let disposed = false
let listRevision = 0
let listController: AbortController | null = null
let headmanRevision = 0
let headmanContextRevision = 0
let headmanController: AbortController | null = null
let headmanMutationRevision = 0
let headmanMutationController: AbortController | null = null
let promotionContextRevision = 0
let promotionPreviewRevision = 0
let promotionPreviewController: AbortController | null = null
let promotionMutationRevision = 0
let promotionMutationController: AbortController | null = null

const tabs: readonly { value: AdminGroupStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Активные' },
  { value: 'DRAFT', label: 'Черновики' },
  { value: 'ARCHIVED', label: 'Архив' },
]

onMounted(() => void refresh())

async function refresh(preserveMessages = false): Promise<void> {
  const revision = ++listRevision
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  loading.value = true
  if (!preserveMessages) error.value = null
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
    if (!preserveMessages) showError(cause, 'Реестр групп не удалось загрузить.')
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

async function openHeadman(group: AdminGroup): Promise<void> {
  closeHeadman(false)
  assignmentGroup.value = group
  const contextRevision = headmanContextRevision
  headmanLoading.value = true
  const revision = ++headmanRevision
  const controller = new AbortController()
  headmanController = controller
  try {
    const result = await props.client.getHeadmanRoster(group.id, controller.signal)
    if (!isHeadmanCurrent(revision, controller, contextRevision)
      || assignmentGroup.value?.id !== group.id) return
    headmanRoster.value = result
    selectedStudentId.value = result.currentHeadmanId
    if (selectedStudentId.value !== null) await loadPreview()
  } catch (cause) {
    if (!isHeadmanCurrent(revision, controller, contextRevision)
      || cause instanceof StaleSessionGenerationError || isAbortError(cause)) return
    showError(cause, 'Состав группы не удалось загрузить.')
  } finally {
    if (isHeadmanCurrent(revision, controller, contextRevision)) {
      headmanLoading.value = false
      headmanController = null
    }
  }
}

async function loadPreview(): Promise<void> {
  const group = assignmentGroup.value
  const studentId = selectedStudentId.value
  if (group === null || studentId === null) {
    assignmentPreview.value = null
    return
  }
  const contextRevision = headmanContextRevision
  const groupId = group.id
  headmanSaving.value = false
  headmanController?.abort()
  const revision = ++headmanRevision
  const controller = new AbortController()
  headmanController = controller
  headmanLoading.value = true
  try {
    const result = await props.client.previewHeadman(groupId, studentId, controller.signal)
    if (!isHeadmanCurrent(revision, controller, contextRevision)
      || !isHeadmanContextCurrent(contextRevision, groupId, studentId)) return
    assignmentPreview.value = result
  } catch (cause) {
    if (!isHeadmanCurrent(revision, controller, contextRevision)
      || cause instanceof StaleSessionGenerationError || isAbortError(cause)) return
    showError(cause, 'Предпросмотр назначения не удалось загрузить.')
    assignmentPreview.value = null
  } finally {
    if (isHeadmanCurrent(revision, controller, contextRevision)) {
      headmanLoading.value = false
      headmanController = null
    }
  }
}

async function confirmHeadman(): Promise<void> {
  if (headmanMutationPending.value) return
  const contextRevision = headmanContextRevision
  const group = assignmentGroup.value
  const roster = headmanRoster.value
  const studentId = selectedStudentId.value
  if (group === null || roster === null || studentId === null) return
  if (assignmentPreview.value === null || assignmentPreview.value.candidateId !== studentId) {
    await loadPreview()
    if (!isHeadmanContextCurrent(contextRevision, group.id, studentId)) return
    if (assignmentPreview.value === null || assignmentPreview.value.candidateId !== studentId) return
  }
  const currentGroup = assignmentGroup.value
  const currentRoster = headmanRoster.value
  const currentStudentId = selectedStudentId.value
  if (!isHeadmanContextCurrent(contextRevision, group.id, studentId)
    || currentGroup === null
    || currentRoster === null
    || currentStudentId === null) return
  headmanSaving.value = true
  headmanMutationPending.value = true
  error.value = null
  notice.value = null
  const revision = ++headmanMutationRevision
  const controller = new AbortController()
  headmanMutationController = controller
  const refreshAfterOutcome = (): Promise<void> => {
    return disposed ? Promise.resolve() : refresh(true)
  }
  try {
    const result = await props.client.assignHeadman(currentGroup.id, {
      studentId: currentStudentId,
      expectedHeadmanId: currentRoster.currentHeadmanId,
    }, controller.signal)
    const reconciliation = refreshAfterOutcome()
    if (!isHeadmanMutationCurrent(revision, controller, contextRevision, group.id, studentId)) {
      void reconciliation
      return
    }
    headmanSaving.value = false
    closeHeadman(false)
    notice.value = result.changed
      ? `Староста группы изменён: ${result.headmanFio}.`
      : 'Староста уже назначен, изменений нет.'
    await reconciliation
  } catch (cause) {
    void refreshAfterOutcome()
    if (!isHeadmanMutationCurrent(revision, controller, contextRevision, group.id, studentId)
      || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Старосту не удалось подтвердить, реестр обновляется.')
  } finally {
    if (headmanMutationRevision === revision && headmanMutationController === controller) {
      headmanMutationPending.value = false
      headmanMutationController = null
      if (isHeadmanContextCurrent(contextRevision, group.id, studentId)) {
        headmanSaving.value = false
      }
    }
  }
}

async function openPromotion(group?: AdminGroup): Promise<void> {
  if (promotionMutationPending.value) return
  closePromotion(false)
  promotionScopeGroup.value = group ?? null
  promotionPanelVisible.value = true
  promotionResult.value = null
  promotionError.value = null
  notice.value = null
  await loadPromotionPreview()
}

async function loadPromotionPreview(): Promise<void> {
  if (!promotionPanelVisible.value) return
  const context = promotionContextRevision
  const groupId = promotionScopeGroup.value?.id
  promotionPreviewController?.abort()
  const revision = ++promotionPreviewRevision
  const controller = new AbortController()
  promotionPreviewController = controller
  promotionLoading.value = true
  promotionError.value = null
  promotionPreview.value = null
  try {
    const result = await props.client.previewPromotion(groupId, controller.signal)
    if (!isPromotionPreviewCurrent(revision, controller, context, groupId)) return
    promotionPreview.value = result
  } catch (cause) {
    if (!isPromotionPreviewCurrent(revision, controller, context, groupId)
      || cause instanceof StaleSessionGenerationError || isAbortError(cause)) return
    promotionError.value = promotionApiErrorMessage(cause, 'Предпросмотр перевода не удалось загрузить.')
    emitPromotionAuthError(cause)
  } finally {
    if (isPromotionPreviewCurrent(revision, controller, context, groupId)) {
      promotionLoading.value = false
      promotionPreviewController = null
    }
  }
}

function isPromotionPreviewCurrent(
  revision: number,
  controller: AbortController,
  context: number,
  groupId: number | undefined,
): boolean {
  return !disposed
    && promotionPanelVisible.value
    && revision === promotionPreviewRevision
    && context === promotionContextRevision
    && promotionPreviewController === controller
    && promotionScopeGroup.value?.id === groupId
}

function requestPromotionConfirmation(): void {
  const preview = promotionPreview.value
  if (preview === null || !preview.dryRun || preview.executed
    || preview.groupId !== (promotionScopeGroup.value?.id ?? null)
    || (preview.toPromote.length === 0 && preview.toArchive.length === 0)
    || promotionMutationPending.value) return
  promotionConfirmDialog.value?.showModal()
}

async function confirmPromotion(): Promise<void> {
  if (promotionMutationPending.value) return
  const preview = promotionPreview.value
  const context = promotionContextRevision
  if (preview === null || !preview.dryRun || preview.executed
    || preview.groupId !== (promotionScopeGroup.value?.id ?? null)) return

  const groupId = preview.groupId
  const previousIndex = groupId === null
    ? -1
    : groups.value.findIndex(group => group.id === groupId)
  promotionSaving.value = true
  promotionMutationPending.value = true
  promotionError.value = null
  error.value = null
  const revision = ++promotionMutationRevision
  const controller = new AbortController()
  promotionMutationController = controller
  try {
    const result = await props.client.executePromotion({
      cycleSemesterId: preview.cycleSemesterId,
      previewVersion: preview.previewVersion,
      groupId: preview.groupId,
    }, controller.signal)
    if (!isPromotionMutationCurrent(revision, controller, context)) {
      void refresh(true)
      return
    }
    if (!result.executed || result.dryRun
      || result.cycleSemesterId !== preview.cycleSemesterId
      || result.previewVersion !== preview.previewVersion
      || result.groupId !== preview.groupId) {
      throw new Error('Сервер не подтвердил выполнение показанного плана.')
    }
    promotionResult.value = result
    promotionPreview.value = null
    promotionError.value = null
    promotionConfirmDialog.value?.close()
    notice.value = promotionResultMessage(result)
    await refresh(true)
    if (groupId !== null && result.toArchive.some(item => item.id === groupId)) {
      await focusAfterArchivedGroup(groupId, previousIndex)
    }
  } catch (cause) {
    void refresh(true)
    if (!isPromotionMutationCurrent(revision, controller, context)
      || cause instanceof StaleSessionGenerationError) return
    const stale = cause instanceof AdminGroupsApiError && cause.response.status === 409
    promotionPreview.value = null
    promotionConfirmDialog.value?.close()
    promotionError.value = promotionApiErrorMessage(
      cause,
      stale
        ? 'Предпросмотр устарел. Реестр обновлён; подготовь новый предпросмотр.'
        : 'Сервер не подтвердил результат. Обнови реестр и подготовь новый предпросмотр.',
    )
    emitPromotionAuthError(cause)
  } finally {
    if (promotionMutationRevision === revision && promotionMutationController === controller) {
      promotionMutationPending.value = false
      promotionMutationController = null
      if (isPromotionContextCurrent(context)) promotionSaving.value = false
    }
  }
}

function isPromotionMutationCurrent(
  revision: number,
  controller: AbortController,
  context: number,
): boolean {
  return !disposed
    && revision === promotionMutationRevision
    && promotionMutationController === controller
    && isPromotionContextCurrent(context)
}

function isPromotionContextCurrent(context: number): boolean {
  return !disposed && promotionPanelVisible.value && context === promotionContextRevision
}

function closePromotion(clearResult = true): void {
  promotionContextRevision += 1
  promotionPreviewRevision += 1
  promotionPreviewController?.abort()
  promotionPreviewController = null
  promotionPanelVisible.value = false
  promotionScopeGroup.value = null
  promotionPreview.value = null
  promotionLoading.value = false
  promotionSaving.value = false
  promotionError.value = null
  if (clearResult) promotionResult.value = null
  if (promotionConfirmDialog.value?.open) promotionConfirmDialog.value.close()
}

async function focusAfterArchivedGroup(groupId: number, previousIndex: number): Promise<void> {
  await nextTick()
  const rows = Array.from(document.querySelectorAll<HTMLTableRowElement>('.admin-groups-table tbody tr'))
  const target = rows.find(row => row.dataset.groupId === String(groupId))
    ?? rows[Math.min(Math.max(previousIndex, 0), rows.length - 1)]
  target?.querySelector<HTMLButtonElement>('button')?.focus()
}

function promotionResultMessage(result: PromotionSummary): string {
  return 'Сервер завершил перевод: ' + result.promoteCount + ' групп и '
    + result.promotedStudentCount + ' студентов переведено, ' + result.archiveCount + ' групп и '
    + result.archivedStudentCount + ' студентов отправлено в архив; пропущено '
    + result.skippedGroupCount + ' групп.'
}

function promotionApiErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof AdminGroupsApiError) return cause.problem?.detail ?? fallback
  return cause instanceof Error ? cause.message : fallback
}

function emitPromotionAuthError(cause: unknown): void {
  if (cause instanceof AdminGroupsApiError
    && (cause.response.status === 401 || cause.response.status === 403)) emit('ownerError', cause)
}

function closeHeadman(clearNotice = true): void {
  headmanContextRevision += 1
  headmanRevision += 1
  headmanController?.abort()
  headmanController = null
  assignmentGroup.value = null
  headmanRoster.value = null
  selectedStudentId.value = null
  assignmentPreview.value = null
  headmanLoading.value = false
  headmanSaving.value = false
  if (clearNotice) error.value = null
}

function isHeadmanCurrent(
  revision: number,
  controller: AbortController,
  contextRevision: number,
): boolean {
  return !disposed
    && revision === headmanRevision
    && contextRevision === headmanContextRevision
    && headmanController === controller
}

function isHeadmanContextCurrent(contextRevision: number, groupId: number, studentId: number): boolean {
  return !disposed
    && contextRevision === headmanContextRevision
    && assignmentGroup.value?.id === groupId
    && selectedStudentId.value === studentId
}

function isHeadmanMutationCurrent(
  revision: number,
  controller: AbortController,
  contextRevision: number,
  groupId: number,
  studentId: number,
): boolean {
  return headmanMutationRevision === revision
    && headmanMutationController === controller
    && isHeadmanContextCurrent(contextRevision, groupId, studentId)
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
  headmanContextRevision += 1
  headmanRevision += 1
  headmanController?.abort()
  headmanController = null
  promotionContextRevision += 1
  promotionPreviewRevision += 1
  promotionPreviewController?.abort()
  promotionPreviewController = null
  promotionMutationRevision += 1
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
      <div
        v-if="status !== 'ARCHIVED'"
        class="admin-groups-promotion__toolbar"
      >
        <p>
          Цикл перевода определяется последним завершённым весенним семестром.
        </p>
        <button
          class="admin-groups-action admin-groups-promotion__open"
          type="button"
          :disabled="promotionMutationPending || counts.active + counts.draft === 0"
          @click="openPromotion()"
        >
          Перевести все группы на следующий курс
        </button>
      </div>
      <form class="admin-groups-search" role="search" @submit.prevent="submitSearch">
        <label for="admin-groups-search-input">Поиск по названию группы</label>
        <input id="admin-groups-search-input" v-model="search" type="search" autocomplete="off">
        <button type="submit">Найти</button>
      </form>

      <section
        v-if="promotionPanelVisible"
        id="admin-groups-promotion-panel"
        class="admin-groups-promotion"
        aria-labelledby="admin-groups-promotion-title"
      >
        <div class="admin-groups-promotion__header">
          <div>
            <h2 id="admin-groups-promotion-title">
              {{ promotionScopeGroup ? 'Перевод группы ' + displayCode(promotionScopeGroup) : 'Предпросмотр массового перевода' }}
            </h2>
            <p>Серверный план привязан к завершённому весеннему семестру.</p>
          </div>
          <button
            class="admin-groups-headman__close admin-groups-promotion__refresh"
            type="button"
            :disabled="promotionMutationPending"
            @click="closePromotion()"
          >
            Закрыть
          </button>
        </div>

        <p
          v-if="promotionLoading"
          class="admin-groups-state"
          role="status"
        >
          Считаем последствия…
        </p>
        <div
          v-if="promotionError"
          class="admin-groups-promotion__error"
        >
          <p
            class="admin-groups-state admin-groups-state--error"
            role="alert"
          >
            {{ promotionError }}
          </p>
          <button
            v-if="!promotionMutationPending"
            class="admin-groups-table__action"
            type="button"
            @click="loadPromotionPreview"
          >
            Подготовить предпросмотр
          </button>
        </div>

        <section
          v-if="promotionPreview"
          class="admin-groups-promotion__details"
          role="status"
        >
          <p class="admin-groups-promotion__cycle">
            Цикл: весенний семестр завершился {{ promotionPreview.cycleDateTo.split('-').reverse().join('.') }}.
          </p>
          <dl class="admin-groups-promotion__counts">
            <div><dt>Перейдут на курс выше</dt><dd>{{ promotionPreview.promoteCount }} групп · {{ promotionPreview.promotedStudentCount }} студентов</dd></div>
            <div><dt>Уйдут в архив</dt><dd>{{ promotionPreview.archiveCount }} групп · {{ promotionPreview.archivedStudentCount }} студентов</dd></div>
            <div><dt>Пропущено</dt><dd>{{ promotionPreview.skippedGroupCount }} групп · {{ promotionPreview.skippedStudentCount }} студентов</dd></div>
            <div><dt>Конфликтные префиксы</dt><dd>{{ promotionPreview.conflictCount }}</dd></div>
          </dl>

          <section
            v-if="promotionPreview.toPromote.length"
            aria-labelledby="admin-groups-promotion-next-title"
          >
            <h3 id="admin-groups-promotion-next-title">
              Перевод на следующий курс
            </h3>
            <ul class="admin-groups-promotion__list">
              <li
                v-for="item in promotionPreview.toPromote"
                :key="'promote-' + item.id"
              >
                <strong>{{ item.from }} → {{ item.to }}</strong>
                <span>{{ item.studentCount }} студентов · ID {{ item.id }}</span>
              </li>
            </ul>
          </section>

          <section
            v-if="promotionPreview.toArchive.length"
            aria-labelledby="admin-groups-promotion-archive-title"
          >
            <h3 id="admin-groups-promotion-archive-title">
              Архивация выпускных групп
            </h3>
            <ul class="admin-groups-promotion__list">
              <li
                v-for="item in promotionPreview.toArchive"
                :key="'archive-' + item.id"
              >
                <strong>{{ item.from }} — выпуск</strong>
                <span>{{ item.studentCount }} студентов · ID {{ item.id }}</span>
              </li>
            </ul>
          </section>

          <section
            v-if="promotionPreview.skipped.length"
            aria-labelledby="admin-groups-promotion-skipped-title"
          >
            <h3 id="admin-groups-promotion-skipped-title">
              Группы без изменений
            </h3>
            <ul class="admin-groups-promotion__list">
              <li
                v-for="item in promotionPreview.skipped"
                :key="'skip-' + item.id"
              >
                <strong>{{ item.name }}</strong>
                <span>
                  {{ item.reason === 'ALREADY_PROCESSED'
                    ? 'Уже обработана в этом цикле'
                    : 'Создана после окончания семестра' }}
                  · {{ item.studentCount }} студентов · ID {{ item.id }}
                </span>
              </li>
            </ul>
          </section>

          <section
            v-if="promotionPreview.conflicts.length"
            aria-labelledby="admin-groups-promotion-conflicts-title"
          >
            <h3 id="admin-groups-promotion-conflicts-title">
              Нужна проверка данных
            </h3>
            <ul class="admin-groups-promotion__list">
              <li
                v-for="(conflict, index) in promotionPreview.conflicts"
                :key="conflict.prefix + '-' + conflict.reason + '-' + index"
              >
                <strong>{{ conflict.prefix || 'Группа' }}</strong>
                <span>{{ conflict.message }}</span>
              </li>
            </ul>
          </section>

          <button
            class="admin-groups-action admin-groups-promotion__confirm"
            type="button"
            :disabled="promotionMutationPending || promotionLoading
              || (promotionPreview.toPromote.length === 0 && promotionPreview.toArchive.length === 0)"
            @click="requestPromotionConfirmation"
          >
            Подтвердить показанный план
          </button>
        </section>

        <section
          v-if="promotionResult"
          class="admin-groups-promotion__result"
          role="status"
          aria-labelledby="admin-groups-promotion-result-title"
        >
          <h3 id="admin-groups-promotion-result-title">
            Результат сервера
          </h3>
          <p>{{ promotionResultMessage(promotionResult) }}</p>
          <ul class="admin-groups-promotion__list">
            <li
              v-for="item in promotionResult.toPromote"
              :key="'result-promote-' + item.id"
            >
              <strong>{{ item.from }} → {{ item.to }}</strong>
              <span>{{ item.studentCount }} студентов · ID {{ item.id }}</span>
            </li>
            <li
              v-for="item in promotionResult.toArchive"
              :key="'result-archive-' + item.id"
            >
              <strong>{{ item.from }} — отправлена в архив</strong>
              <span>{{ item.studentCount }} студентов · ID {{ item.id }}</span>
            </li>
          </ul>
          <section
            v-if="promotionResult.skipped.length"
            aria-labelledby="admin-groups-promotion-result-skipped-title"
          >
            <h4 id="admin-groups-promotion-result-skipped-title">
              Группы без изменений
            </h4>
            <ul class="admin-groups-promotion__list">
              <li
                v-for="item in promotionResult.skipped"
                :key="'result-skip-' + item.id"
              >
                <strong>{{ item.name }}</strong>
                <span>
                  {{ item.reason === 'ALREADY_PROCESSED'
                    ? 'Уже обработана в этом цикле'
                    : 'Создана после окончания семестра' }}
                  · {{ item.studentCount }} студентов · ID {{ item.id }}
                </span>
              </li>
            </ul>
          </section>
          <p
            v-if="promotionResult.conflicts.length"
            class="admin-groups-state"
          >
            Пропущено конфликтов: {{ promotionResult.conflictCount }}.
          </p>
        </section>
      </section>

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
              <th scope="col">Действие</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="group in groups"
              :key="group.id"
              :data-group-id="group.id"
            >
              <th scope="row">{{ displayCode(group) }}</th>
              <td>{{ group.currentCourse ?? '—' }}</td>
              <td>{{ durationLabel(group) }}</td>
              <td>{{ group.studentCount }}</td>
              <td>{{ group.headmanFio ?? 'Не назначен' }}</td>
              <td v-if="status === 'DRAFT'">{{ group.draftReason ?? 'Причина не указана' }}</td>
              <td>
                <button
                  v-if="status !== 'ARCHIVED'"
                  class="admin-groups-table__promotion"
                  type="button"
                  :disabled="promotionMutationPending"
                  @click="openPromotion(group)"
                >
                  Перевести на следующий курс
                </button>
                <button
                  class="admin-groups-table__action"
                  type="button"
                  :disabled="status === 'ARCHIVED'"
                  @click="openHeadman(group)"
                >
                  {{ group.headmanFio ? 'Изменить старосту' : 'Назначить старосту' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <section
        v-if="assignmentGroup"
        class="admin-groups-headman"
        aria-labelledby="admin-groups-headman-title"
      >
        <div class="admin-groups-headman__header">
          <div>
            <h2 id="admin-groups-headman-title">Староста группы {{ displayCode(assignmentGroup) }}</h2>
            <p v-if="headmanRoster" class="admin-groups-headman__meta">
              Текущий староста: {{ headmanRoster.currentHeadmanFio ?? 'не назначен' }} ·
              активных помощников: {{ headmanRoster.activeAssistantCount }}
            </p>
          </div>
          <button class="admin-groups-headman__close" type="button" @click="closeHeadman()">Закрыть</button>
        </div>

        <p v-if="headmanLoading" class="admin-groups-state" role="status">Загружаем состав…</p>
        <form v-else-if="headmanRoster" class="admin-groups-headman__form" @submit.prevent="confirmHeadman">
          <label for="admin-groups-headman-select">Выбери старосту</label>
          <select
            id="admin-groups-headman-select"
            v-model.number="selectedStudentId"
            @change="loadPreview"
          >
            <option :value="null" disabled>Выбери студента</option>
            <option v-for="candidate in headmanRoster.candidates" :key="candidate.id" :value="candidate.id">
              {{ candidate.fio }}{{ candidate.current ? ' — текущий староста' : '' }}
            </option>
          </select>
          <p v-if="assignmentPreview" class="admin-groups-headman__preview" role="status">
            <template v-if="assignmentPreview.sameHeadman">Этот студент уже староста. Изменений не будет.</template>
            <template v-else>
              После подтверждения {{ assignmentPreview.activatesDraft ? 'группа станет активной' : 'староста будет заменён' }}.
              Помощников к отзыву: {{ assignmentPreview.assistantsToRevoke }}.
            </template>
          </p>
          <button
            class="admin-groups-action"
            type="submit"
            :disabled="headmanSaving || headmanMutationPending || selectedStudentId === null || assignmentPreview === null"
            :aria-busy="headmanSaving"
          >
            {{ headmanSaving ? 'Сохраняем…' : 'Подтвердить назначение' }}
          </button>
        </form>
      </section>

      <dialog
        ref="promotionConfirmDialog"
        class="admin-groups-promotion__dialog"
        aria-labelledby="admin-groups-promotion-confirm-title"
        aria-describedby="admin-groups-promotion-confirm-detail"
      >
        <h2 id="admin-groups-promotion-confirm-title">
          Подтверди перевод групп
        </h2>
        <p
          v-if="promotionPreview"
          id="admin-groups-promotion-confirm-detail"
        >
          {{ promotionScopeGroup ? 'Группа ' + displayCode(promotionScopeGroup) : 'В выбранном цикле' }}:
          весенний семестр завершился {{ promotionPreview.cycleDateTo.split('-').reverse().join('.') }}.
          {{ promotionPreview.promoteCount }} групп перейдут на курс выше
          ({{ promotionPreview.promotedStudentCount }} студентов), {{ promotionPreview.archiveCount }}
          групп уйдут в архив ({{ promotionPreview.archivedStudentCount }} студентов).
          {{ promotionPreview.skippedGroupCount }} групп будут пропущены
          ({{ promotionPreview.skippedStudentCount }} студентов), конфликтных префиксов: {{ promotionPreview.conflictCount }}.
          Пропущенные группы останутся без изменений.
        </p>
        <div class="admin-groups-promotion__dialog-actions">
          <button
            class="admin-groups-table__action admin-groups-promotion__dialog-cancel"
            type="button"
            :disabled="promotionMutationPending"
            @click="promotionConfirmDialog?.close()"
          >
            Назад
          </button>
          <button
            class="admin-groups-action admin-groups-promotion__dialog-confirm"
            type="button"
            :disabled="promotionMutationPending || promotionPreview === null"
            :aria-busy="promotionSaving"
            @click="confirmPromotion"
          >
            {{ promotionSaving ? 'Сохраняем…' : 'Подтвердить перевод' }}
          </button>
        </div>
      </dialog>

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
