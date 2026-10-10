<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import type { AdminMapFloorResponse, AdminMapPlanDeletionPreview, MapFormat } from '../../api/types'
import type { AdminAccessProfile } from '../admin-access/admin-access-client'
import { isAdminPasswordProofFailure } from '../../shared/admin-authority'
import { AdminMapClient, MapApiError } from '../../api/map-client'
import { normalizeAdminMapCode } from './admin-map-state'
import AdminNotice from '../../shared/components/AdminNotice.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import './admin-map-screen.pcss'

const props = withDefaults(defineProps<{ client: AdminMapClient; authority: AdminAccessProfile | null; theme?: 'dark' | 'light'; showBack?: boolean }>(), { theme: 'dark', showBack: true })
const emit = defineEmits<{ back: []; 'owner-error': [cause: unknown] }>()
const buildingCode = ref('')
const floorCode = ref('')
const floor = shallowRef<AdminMapFloorResponse | null>(null)
const targetReady = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const warning = ref<string | null>(null)
const password = ref('')
const passwordError = ref<string | null>(null)
const filesError = ref<string | null>(null)
const files = shallowRef<Partial<Record<MapFormat, File>>>({})
const stagedUrls = ref<Partial<Record<MapFormat, string>>>({})
const assetUrls = ref<Partial<Record<MapFormat, string>>>({})
const assetErrors = ref<Partial<Record<MapFormat, string>>>({})
const assetLoading = ref(false)
const previewFormat = ref<MapFormat | null>(null)
const previewDialog = ref<HTMLDialogElement | null>(null)
async function openPreview(format: MapFormat): Promise<void> { previewFormat.value = format; await nextTick(); previewDialog.value?.showModal() }
function closePreview(): void { previewDialog.value?.close(); previewFormat.value = null }
const pendingUpload = ref<{ priorVersion: string | null; reconciled: boolean } | null>(null)
const deletion = shallowRef<{ preview: Readonly<AdminMapPlanDeletionPreview>; client: AdminMapClient; generation: number | undefined; revision: number } | null>(null)
const formats = ['svg', 'png'] as const
const canManage = computed(() => props.authority?.assigned === true && (props.authority.mode === 'FULL' || props.authority.mode === 'LIMITED') && props.authority.permissions.includes('CAMPUS_MAP_MANAGE'))
const canDelete = computed(() => canManage.value && props.authority?.permissions.includes('DATA_DELETE') === true)
const controlsLocked = computed(() => !canManage.value || saving.value || Boolean(deletion.value))
const pair = computed(() => {
  const building = normalizeAdminMapCode(buildingCode.value), level = normalizeAdminMapCode(floorCode.value)
  return building && level ? { buildingCode: building, floorCode: level } : null
})
const fieldErrors = computed(() => ({ building: buildingCode.value && !normalizeAdminMapCode(buildingCode.value) ? 'Введи от 1 до 64 цифр, начиная с 1–9, без пробелов.' : '', floor: floorCode.value && !normalizeAdminMapCode(floorCode.value) ? 'Введи от 1 до 64 цифр, начиная с 1–9, без пробелов.' : '' }))
let mounted = false
let revision = 0
let requestSequence = 0
let saveSequence = 0
let assetSequence = 0
let controller: AbortController | undefined
let previewController: AbortController | undefined

function revoke(urls: Partial<Record<MapFormat, string>>): void { for (const url of Object.values(urls)) if (url) URL.revokeObjectURL(url) }
function clearAssets(): void { assetSequence += 1; revoke(assetUrls.value); assetUrls.value = {}; assetErrors.value = {}; assetLoading.value = false; closePreview() }
function clearDraft(): void { revoke(stagedUrls.value); stagedUrls.value = {}; files.value = {}; filesError.value = null }
function cancelDeletion(): void { previewController?.abort(); previewController = undefined; deletion.value = null; password.value = ''; passwordError.value = null }
function resetTarget(): void {
  revision += 1; requestSequence += 1; saveSequence += 1; controller?.abort(); cancelDeletion(); clearAssets(); clearDraft()
  floor.value = null; targetReady.value = false; pendingUpload.value = null; error.value = notice.value = warning.value = null; saving.value = loading.value = false
}
watch([buildingCode, floorCode], () => { resetTarget(); if (mounted && canManage.value && pair.value) void resolveTarget() }, { flush: 'sync' })
watch(() => [props.client, props.client.currentGeneration(), props.authority?.userId, props.authority?.assigned, props.authority?.mode, props.authority?.version, props.authority?.permissions.join('|')] as const, () => { resetTarget(); buildingCode.value = floorCode.value = '' }, { flush: 'sync' })
onMounted(() => { mounted = true })
onUnmounted(() => { mounted = false; resetTarget() })

async function resolveTarget(): Promise<void> {
  const target = pair.value
  if (!target || !canManage.value) return
  const client = props.client, generation = client.currentGeneration(), scope = revision, request = ++requestSequence
  controller?.abort(); const abort = new AbortController(); controller = abort
  const current = () => mounted && canManage.value && scope === revision && request === requestSequence && props.client === client && client.isCurrentGeneration(generation) && !abort.signal.aborted
  clearAssets(); loading.value = true; targetReady.value = false; error.value = null
  try {
    const result = await client.resolvePlanTarget(target.buildingCode, target.floorCode, abort.signal)
    if (!current()) return
    floor.value = result.floor; targetReady.value = true
    if (pendingUpload.value) {
      const changed = result.floor?.currentPlan && result.floor.currentPlan.version !== pendingUpload.value.priorVersion
      pendingUpload.value = { ...pendingUpload.value, reconciled: true }
      warning.value = changed ? 'На сервере появилась новая схема. Проверь её: исход именно твоей загрузки не подтверждён. Повторной отправки не было.' : 'Новая схема не подтверждена. Повторной отправки не было. Проверь текущую схему перед новой загрузкой.'
    }
    void loadAssets()
  } catch (cause) {
    if (!current()) return
    if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause)
    else error.value = mapErrorMessage(cause, 'Не удалось получить схему. Попробуй ещё раз.')
  } finally { if (current()) { loading.value = false; controller = undefined } }
}
async function loadAssets(): Promise<void> {
  clearAssets()
  const plan = floor.value?.currentPlan, floorId = floor.value?.id
  if (!plan || !floorId || !canManage.value) return
  const client = props.client, generation = client.currentGeneration(), scope = revision, sequence = assetSequence
  const current = () => mounted && canManage.value && scope === revision && sequence === assetSequence && props.client === client && client.isCurrentGeneration(generation)
  assetLoading.value = true
  await Promise.all(formats.map(async format => {
    const slot = plan[format]
    if (slot.state !== 'ready' || !slot.id) return
    try {
      const blob = await client.downloadAsset(floorId, plan.version, format, slot.id)
      if (!current()) return
      if (!blob.size || blob.type.split(';', 1)[0]?.toLowerCase() !== (format === 'png' ? 'image/png' : 'image/svg+xml')) throw new Error('Invalid map asset')
      assetUrls.value = { ...assetUrls.value, [format]: URL.createObjectURL(blob) }
    } catch (cause) {
      if (!current()) return
      if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause)
      else assetErrors.value = { ...assetErrors.value, [format]: 'Не удалось загрузить файл схемы.' }
    }
  }))
  if (current()) assetLoading.value = false
}
function stageFile(format: MapFormat, file: File | undefined): void {
  if (controlsLocked.value || pendingUpload.value || !targetReady.value || !file) return
  if (!file.size || file.type !== (format === 'png' ? 'image/png' : 'image/svg+xml')) { filesError.value = `Приложи файл ${format.toUpperCase()}.`; return }
  if (stagedUrls.value[format]) URL.revokeObjectURL(stagedUrls.value[format]!)
  files.value = { ...files.value, [format]: file }; stagedUrls.value = { ...stagedUrls.value, [format]: URL.createObjectURL(file) }; filesError.value = null
}
function chooseFile(format: MapFormat, event: Event): void { const input = event.target as HTMLInputElement; stageFile(format, input.files?.[0]); input.value = '' }
function dropFile(format: MapFormat, event: DragEvent): void { stageFile(format, event.dataTransfer?.files[0]) }
function removeFile(format: MapFormat): void {
  if (controlsLocked.value || pendingUpload.value) return
  if (stagedUrls.value[format]) URL.revokeObjectURL(stagedUrls.value[format]!)
  const nextFiles = { ...files.value }, nextUrls = { ...stagedUrls.value }; delete nextFiles[format]; delete nextUrls[format]; files.value = nextFiles; stagedUrls.value = nextUrls
}
async function upload(): Promise<void> {
  const target = pair.value
  if (!target || controlsLocked.value || loading.value || !targetReady.value || pendingUpload.value) return
  if (!files.value.png && !files.value.svg) { filesError.value = 'Приложи PNG или SVG.'; return }
  const payload = Object.freeze({ ...target, ...files.value }), priorVersion = floor.value?.currentPlan?.version ?? null
  const client = props.client, generation = client.currentGeneration(), scope = revision, sequence = ++saveSequence
  const current = () => mounted && canManage.value && scope === revision && sequence === saveSequence && props.client === client && client.isCurrentGeneration(generation)
  saving.value = true; error.value = notice.value = null
  try {
    const result = await client.uploadPlan(payload)
    if (!current()) return
    clearDraft(); notice.value = result.status === 'replaced' ? 'Схема заменена.' : 'Схема добавлена.'
    await resolveTarget()
  } catch (cause) {
    if (!current()) return
    if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause)
    else if (!(cause instanceof MapApiError) || cause.response.status >= 500) { pendingUpload.value = { priorVersion, reconciled: false }; warning.value = 'Исход загрузки неизвестен. Файлы сохранены. Проверь текущую схему перед новой загрузкой.' }
    else error.value = mapErrorMessage(cause, 'Схема не сохранена. Проверь файл.')
  } finally { if (current()) saving.value = false }
}
function finishReconciliation(): void { if (!pendingUpload.value?.reconciled || saving.value || loading.value) return; pendingUpload.value = null; warning.value = null; clearDraft() }
async function previewDeletion(format: MapFormat): Promise<void> {
  const target = pair.value
  if (!target || !canDelete.value || controlsLocked.value || loading.value || !targetReady.value || pendingUpload.value || files.value[format]) return
  const client = props.client, generation = client.currentGeneration(), scope = revision, sequence = ++saveSequence
  const abort = new AbortController(); previewController = abort
  const current = () => mounted && canDelete.value && scope === revision && sequence === saveSequence && props.client === client && client.isCurrentGeneration(generation) && !abort.signal.aborted
  saving.value = true; error.value = notice.value = null
  try {
    const preview = await client.planDeletionPreview(target.buildingCode, target.floorCode, format, abort.signal)
    if (current()) deletion.value = Object.freeze({ preview: Object.freeze(preview), client, generation, revision: scope })
  } catch (cause) { if (current()) { if (cause instanceof StaleSessionGenerationError) emit('owner-error', cause); else error.value = mapErrorMessage(cause, 'Не удалось получить последствия удаления.') } }
  finally { if (current()) saving.value = false }
}
async function confirmDeletion(): Promise<void> {
  const context = deletion.value, target = pair.value
  if (!context || !target || !canDelete.value || saving.value || !password.value || password.value.length > 256) return
  const { client, generation, preview, revision: scope } = context
  if (props.client !== client || !client.isCurrentGeneration(generation) || scope !== revision || preview.buildingCode !== target.buildingCode || preview.floorCode !== target.floorCode) { cancelDeletion(); return }
  const operationId = crypto.randomUUID(), secret = password.value, sequence = ++saveSequence
  password.value = ''; passwordError.value = null; saving.value = true; error.value = notice.value = null
  const current = () => mounted && canDelete.value && scope === revision && sequence === saveSequence && props.client === client && client.isCurrentGeneration(generation)
  try {
    await client.deletePlanTarget({ ...target, ...(preview.format ? { format: preview.format } : {}), operationId, previewDigest: preview.previewDigest, password: secret })
    if (!current()) return
    cancelDeletion(); notice.value = 'Формат текущей схемы удалён.'; await resolveTarget()
  } catch (cause) {
    if (!current()) return
    if (cause instanceof MapApiError && cause.response.status === 403 && isAdminPasswordProofFailure(cause.problem)) { passwordError.value = 'Пароль не подошёл. Попробуй ещё раз.'; return }
    cancelDeletion()
    if (cause instanceof StaleSessionGenerationError) { emit('owner-error', cause); return }
    const message = !(cause instanceof MapApiError) || cause.response.status >= 500 ? 'Исход удаления неизвестен. Запрос не повторяется. Проверь текущую схему.' : mapErrorMessage(cause, 'Сервер отклонил удаление. Запроси последствия заново.')
    await resolveTarget(); if (current()) error.value = message
  } finally { if (current()) saving.value = false }
}
function mapErrorMessage(cause: unknown, fallback: string): string {
  if (!(cause instanceof MapApiError)) return fallback
  if (cause.response.status === 401) return 'Сессия истекла. Войди снова; запрос не повторяется.'
  if (cause.response.status === 403) return 'Недостаточно прав. Проверь активную сессию администратора.'
  if (cause.response.status === 404) return 'Текущая схема уже недоступна. Проверь выбранные номера.'
  if (cause.response.status === 409) return cause.problem?.detail || 'Схема изменилась. Проверь её и запроси подтверждение заново.'
  return cause.problem?.detail || fallback
}
function stateLabel(format: MapFormat): string { return { processing: 'Файл обрабатывается.', failed: 'Ошибка проверки файла.', absent: 'Файл не загружен.', ready: 'Файл загружен.' }[floor.value?.currentPlan?.[format].state ?? 'absent'] }
</script>

<template>
  <main
    class="admin-map-screen"
    :data-theme="theme"
    aria-labelledby="admin-map-title"
  >
    <header class="admin-map-screen__header">
      <button
        v-if="showBack"
        class="admin-map-back"
        type="button"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="admin-map-title">
        Корпуса и этажи
      </h1>
    </header>
    <AdminNotice
      v-if="!canManage"
      tone="warning"
      title="Управление картой недоступно"
    >
      Проверь права активного администратора.
    </AdminNotice>
    <div
      v-else
      class="admin-map-screen__content"
    >
      <AdminNotice
        v-if="error"
        tone="danger"
        title="Изменение не выполнено"
      >
        {{ error }}<template #actions>
          <button
            v-if="pair && !saving && !deletion"
            type="button"
            @click="resolveTarget"
          >
            Повторить
          </button>
        </template>
      </AdminNotice>
      <AdminNotice
        v-if="warning"
        tone="warning"
        title="Проверь результат"
      >
        {{ warning }}<template #actions>
          <button
            type="button"
            :disabled="saving || loading"
            @click="resolveTarget"
          >
            Проверить текущую схему
          </button><button
            v-if="pendingUpload?.reconciled"
            type="button"
            :disabled="saving || loading"
            @click="finishReconciliation"
          >
            Завершить проверку
          </button>
        </template>
      </AdminNotice>
      <AdminNotice
        v-if="notice"
        tone="success"
        title="Изменение сохранено"
      >
        {{ notice }}
      </AdminNotice>
      <section
        class="admin-map-card"
        aria-label="Схема корпуса и этажа"
      >
        <div class="admin-map-fields">
          <label class="admin-map-field"><span>Номер корпуса</span><input
            v-model="buildingCode"
            data-field="building-code"
            type="text"
            inputmode="numeric"
            maxlength="64"
            :disabled="controlsLocked || Boolean(pendingUpload)"
            :aria-invalid="Boolean(fieldErrors.building)"
            :aria-describedby="fieldErrors.building ? 'admin-map-building-error' : undefined"
          ><small
            v-if="fieldErrors.building"
            id="admin-map-building-error"
            class="admin-map-field-error"
            role="alert"
          >{{ fieldErrors.building }}</small></label>
          <label class="admin-map-field"><span>Номер этажа</span><input
            v-model="floorCode"
            data-field="floor-code"
            type="text"
            inputmode="numeric"
            maxlength="64"
            :disabled="controlsLocked || Boolean(pendingUpload)"
            :aria-invalid="Boolean(fieldErrors.floor)"
            :aria-describedby="fieldErrors.floor ? 'admin-map-floor-error' : undefined"
          ><small
            v-if="fieldErrors.floor"
            id="admin-map-floor-error"
            class="admin-map-field-error"
            role="alert"
          >{{ fieldErrors.floor }}</small></label>
        </div>
        <p
          v-if="loading"
          class="admin-map-state"
          role="status"
        >
          Загружаем схему…
        </p>
        <template v-if="targetReady">
          <section
            v-for="format in formats"
            :key="format"
            class="admin-map-format"
            :aria-label="format.toUpperCase()"
          >
            <div class="admin-map-format__heading">
              <h2>{{ format.toUpperCase() }}</h2><div class="admin-map-format__actions">
                <a
                  v-if="assetUrls[format] && !files[format]"
                  class="admin-map-icon-action"
                  :href="assetUrls[format]"
                  :download="`корпус-${buildingCode}-этаж-${floorCode}.${format}`"
                  :aria-label="`Скачать ${format.toUpperCase()}`"
                ><MobileIcon name="download" /></a>
                <button
                  v-if="canDelete && floor?.currentPlan?.[format].state === 'ready' && !files[format]"
                  class="admin-map-icon-action admin-map-danger"
                  type="button"
                  data-action="delete"
                  :aria-label="`Удалить ${format.toUpperCase()}`"
                  :disabled="controlsLocked || Boolean(pendingUpload)"
                  @click="previewDeletion(format)"
                >
                  <MobileIcon name="trash" />
                </button>
                <button
                  v-if="files[format]"
                  class="admin-map-icon-action admin-map-secondary"
                  type="button"
                  :disabled="controlsLocked || Boolean(pendingUpload)"
                  :aria-label="`Убрать выбранный ${format.toUpperCase()}`"
                  @click="removeFile(format)"
                >
                  <MobileIcon name="trash" />
                </button>
              </div>
            </div>
            <button
              v-if="stagedUrls[format] || assetUrls[format]"
              class="admin-map-preview-button"
              type="button"
              :aria-label="`Увеличить ${format.toUpperCase()} схемы: корпус ${buildingCode}, этаж ${floorCode}`"
              @click="openPreview(format)"
            >
              <img
                class="admin-map-preview"
                :src="stagedUrls[format] || assetUrls[format]"
                :alt="`Схема: корпус ${buildingCode}, этаж ${floorCode}`"
              >
            </button>
            <p
              v-if="files[format]"
              class="admin-map-help"
            >
              {{ files[format]?.name }}
            </p>
            <p
              v-if="assetErrors[format]"
              class="admin-map-field-error"
              role="alert"
            >
              {{ assetErrors[format] }}
            </p>
            <button
              v-if="assetErrors[format]"
              type="button"
              :disabled="controlsLocked || assetLoading"
              @click="loadAssets"
            >
              Повторить загрузку файла
            </button>
            <p
              v-if="!stagedUrls[format] && !assetUrls[format] && floor?.currentPlan?.[format].state && floor.currentPlan[format].state !== 'absent'"
              class="admin-map-help"
              role="status"
            >
              {{ assetLoading ? 'Загружаем файл…' : stateLabel(format) }}
            </p>
            <label
              class="admin-map-file"
              :class="{ 'admin-map-file--compact': Boolean(assetUrls[format] || stagedUrls[format]), 'admin-map-file--disabled': controlsLocked || Boolean(pendingUpload) }"
              @dragover.prevent
              @drop.prevent="dropFile(format, $event)"
            ><MobileIcon name="plus" /><strong>{{ assetUrls[format] || stagedUrls[format] ? 'Выбрать другой файл' : `Добавить ${format.toUpperCase()}` }}</strong><input
              type="file"
              :accept="format === 'png' ? 'image/png,.png' : 'image/svg+xml,.svg'"
              :aria-label="`Выбрать ${format.toUpperCase()}`"
              :disabled="controlsLocked || Boolean(pendingUpload)"
              @change="chooseFile(format, $event)"
            ></label>
          </section>
          <small
            v-if="filesError"
            class="admin-map-field-error"
            role="alert"
          >{{ filesError }}</small>
          <button
            v-if="files.png || files.svg"
            type="button"
            :disabled="controlsLocked || loading || Boolean(pendingUpload)"
            @click="upload"
          >
            {{ saving ? 'Сохраняем…' : 'Сохранить схему' }}
          </button>
        </template>
      </section>
      <section
        v-if="deletion && canDelete"
        class="admin-map-card"
        role="dialog"
        aria-labelledby="admin-map-delete-title"
        aria-describedby="admin-map-delete-help"
      >
        <h2 id="admin-map-delete-title">
          Удалить {{ deletion.preview.format?.toUpperCase() }}: корпус {{ deletion.preview.buildingCode }}, этаж {{ deletion.preview.floorCode }}?
        </h2>
        <p
          id="admin-map-delete-help"
          class="admin-map-help"
        >
          Формат исчезнет из текущей схемы. Этаж и история схем сохранятся.
        </p>
        <dl class="admin-map-slots">
          <div><dt>Файлов</dt><dd>{{ deletion.preview.files }}</dd></div><div><dt>Объём, байт</dt><dd>{{ deletion.preview.bytes }}</dd></div>
        </dl>
        <label class="admin-map-field"><span>Пароль текущего администратора</span><input
          v-model="password"
          type="password"
          autocomplete="current-password"
          maxlength="256"
          :disabled="saving"
          :aria-invalid="Boolean(passwordError)"
          :aria-describedby="passwordError ? 'admin-map-password-error' : undefined"
        ><small
          v-if="passwordError"
          id="admin-map-password-error"
          class="admin-map-field-error"
          role="alert"
        >{{ passwordError }}</small></label>
        <button
          class="admin-map-danger"
          type="button"
          :disabled="saving || !password"
          @click="confirmDeletion"
        >
          {{ saving ? 'Удаляем…' : 'Подтвердить удаление' }}
        </button>
        <button
          class="admin-map-secondary"
          type="button"
          data-action="cancel"
          :disabled="saving"
          @click="cancelDeletion"
        >
          Отмена
        </button>
      </section>
    </div>
    <dialog
      v-if="previewFormat"
      ref="previewDialog"
      class="admin-map-preview-overlay"
      :aria-label="`Схема ${previewFormat.toUpperCase()}`"
      @close="previewFormat = null"
      @click.self="closePreview"
    >
      <button
        type="button"
        class="admin-map-back"
        aria-label="Закрыть просмотр"
        autofocus
        @click="closePreview"
      >
        <MobileIcon name="close" />
      </button><img
        class="admin-map-preview"
        :src="stagedUrls[previewFormat] || assetUrls[previewFormat]"
        :alt="`Схема: корпус ${buildingCode}, этаж ${floorCode}`"
      >
    </dialog>
  </main>
</template>
