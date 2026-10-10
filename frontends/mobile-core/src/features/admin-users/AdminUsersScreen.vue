<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  AdminUsersApiError,
  type AdminManagedRole,
  type AdminRoleGrant,
  type AdminRoleStatus,
  type AdminUser,
  type CreateAdminUserInput,
  type UpdateAdminRoleInput,
  type UpdateAdminUserProfileInput,
  type AdminUsersClient,
  type AdminUserArchivePreview,
  type ArchiveAdminUserInput,
} from './admin-users-client'
import './admin-users-screen.pcss'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import AdminRoleIcon from './AdminRoleIcon.vue'
import copyIcon from '../../assets/admin/copy.svg'
import AdminUserGroupPicker from './AdminUserGroupPicker.vue'
import { AdminAccessApiError, adminHasPermission, type AdminAccessClient, type AdminAccessProfile, type AdminAccessCatalog, type AdminAccessSelection } from '../admin-access/admin-access-client'
import AdminPermissionEditor from '../admin-access/AdminPermissionEditor.vue'
import RecoveryLinkPanel from './RecoveryLinkPanel.vue'
import AdminNotice from '../../shared/components/AdminNotice.vue'
import AdminCheckbox from '../../shared/components/AdminCheckbox.vue'
import MobileToolsSheet from '../../shared/components/MobileToolsSheet.vue'
import { AdminAccessConfirmationFlow, type AdminAccessConfirmationContext, type AdminAccessConfirmationState } from '../admin-access/admin-access-confirmation'

const props = withDefaults(defineProps<{
  client: AdminUsersClient
  accessClient?: AdminAccessClient | null
  authority?: AdminAccessProfile | null
  theme?: 'dark' | 'light'
}>(), {
  theme: 'dark',
  accessClient: null,
  authority: null,
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
  navigationVisibility: [visible: boolean]
}>()

const users = ref<readonly AdminUser[]>([])
const selectedUser = ref<AdminUser | null>(null)
const search = ref('')
const roleFilter = ref<AdminManagedRole | ''>('')
const statusFilter = ref<AdminRoleStatus | ''>('')
const pageSize = 3
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const archivePasswordError = ref<string | null>(null)
type View = 'list' | 'filters' | 'detail' | 'create' | 'created' | 'edit' | 'role' | 'transfer' | 'recovery' | 'recovery-issued' | 'archive' | 'restore' | 'permissions'
const view = ref<View>('list')
const addRoleExpanded = ref(false)
const title = computed(() => ({ list: 'Пользователи', filters: 'Фильтры и сортировка', detail: 'Пользователь', create: 'Создать пользователя', created: 'Пользователь создан', edit: 'Редактировать данные', role: 'Настроить роль', transfer: 'Перевести студента', recovery: 'Восстановление доступа', 'recovery-issued':'Ссылка восстановления', archive: 'Архивировать аккаунт', restore: 'Восстановить аккаунт', permissions: 'Права доступа' })[view.value])
const createVisible = computed({ get: () => view.value === 'create', set: (value: boolean) => { view.value = value ? 'create' : 'detail' } })
const draftRole = ref<AdminManagedRole | ''>('')
const draftStatus = ref<AdminRoleStatus | ''>('')
const sortDirection = ref<'asc' | 'desc'>('asc')
const draftSort = ref<'asc' | 'desc'>('asc')

const archivePreview = ref<AdminUserArchivePreview | null>(null)
const archivePassword = ref('')
const lifecycleLoading = ref(false)
const lifecycleConfirmed = ref(false)
const lifecycleUncertain = ref(false)
const lifecycleStale = ref(false)
let lifecycleRevision = 0
let lifecycleAbort: AbortController | null = null
let archiveInput: ArchiveAdminUserInput | null = null
let restoreOperation: string | null = null
const isFull = computed(() => props.authority?.assigned === true && props.authority.mode === 'FULL')
const canRead = computed(() => adminHasPermission(props.authority,'USERS_VIEW'))
const targetIsAdmin = computed(() => selectedUser.value?.roles.some(grant => grant.role === 'ADMIN' && grant.status === 'ACTIVE') ?? false)
const canManage = computed(() => adminHasPermission(props.authority,'USERS_MANAGE') && (isFull.value || !targetIsAdmin.value))
const canCredentials = computed(() => adminHasPermission(props.authority,'USER_CREDENTIALS_MANAGE') && (isFull.value || !targetIsAdmin.value))
const canArchive = computed(() => adminHasPermission(props.authority,'USERS_ARCHIVE') && (isFull.value || !targetIsAdmin.value))
const canEditAccess = computed(() => isFull.value && selectedUser.value?.id !== props.authority?.userId && selectedUser.value?.status !== 'ARCHIVED' && Boolean(props.accessClient))
const accessCatalog = ref<AdminAccessCatalog | null>(null)
const accessProfile = ref<AdminAccessProfile | null>(null)
const accessDraft = ref<AdminAccessSelection>({mode:'LIMITED',permissions:[]})
const createAccessConfirmed = ref(false)
const createAccess = ref<AdminAccessSelection>({mode:'LIMITED',permissions:[]})
const accessOwner = ref<'create'|'target'>('target')
const accessLoading = ref(false)
const accessUncertain = ref(false)
const accessOperation = ref<'update'|'revoke'>('update')
const accessFlow = new AdminAccessConfirmationFlow()
const accessConfirmation = ref<AdminAccessConfirmationState | null>(null)
const accessConflict = ref(false)
const accessUnknownResult = ref(false)
const accessConfirmed = ref(false)
let accessRevision = 0
let accessAbort: AbortController | null = null
const accessLabels = (codes: readonly string[]) => codes.map(code => accessCatalog.value?.permissions.find(permission => permission.code === code)?.label ?? code)
const accessEffectiveDraft = computed(() => accessDraft.value.mode === 'FULL' ? accessCatalog.value?.permissions.map(permission => permission.code) ?? [] : accessDraft.value.permissions)
const statusTone = (status:string) => status === 'ACTIVE' ? 'success' : status === 'SUSPENDED' ? 'warning' : status === 'ARCHIVED' ? 'muted' : 'danger'
const accountStatusLabel = (status:string) => status === 'ACTIVE' ? 'Аккаунт активен' : status === 'ARCHIVED' ? 'Аккаунт в архиве' : 'Аккаунт неактивен'
const errorTitle = computed(() => view.value === 'permissions' && accessConflict.value ? 'Права изменились' : lifecycleStale.value ? 'Предпросмотр устарел' : unknownMutation.value || accessUncertain.value || lifecycleUncertain.value || view.value === 'permissions' && accessUnknownResult.value ? 'Результат не подтверждён' : 'Действие не выполнено')
const errorTone = computed(() => view.value === 'permissions' && accessConflict.value ? 'danger' : unknownMutation.value || accessUncertain.value || lifecycleUncertain.value || view.value === 'permissions' && accessUnknownResult.value ? 'warning' : 'danger')
const accessSelectionValid = computed(() => accessDraft.value.mode === 'FULL' || accessDraft.value.permissions.length > 0)
async function openPermissions(owner: 'create'|'target'): Promise<void> {
  if (!isFull.value || !props.accessClient || saving.value || owner === 'target' && !canEditAccess.value) return
  closeAccessConfirmation()
  accessConflict.value = false
  accessUnknownResult.value = false
  accessOwner.value = owner
  accessDraft.value = owner === 'create' ? {...createAccess.value,permissions:[...createAccess.value.permissions]} : {mode:'LIMITED',permissions:[]}
  accessProfile.value = null
  accessUncertain.value = false
  view.value = 'permissions'
  error.value = null
  const revision = ++accessRevision
  accessAbort?.abort()
  const controller = new AbortController()
  accessAbort = controller
  accessLoading.value = true
  const userId = selectedUser.value?.id
  try {
    const catalog = await props.accessClient.getCatalog(controller.signal)
    if (disposed || revision !== accessRevision) return
    accessCatalog.value = catalog
    if (owner === 'target' && userId) {
      const profile = await props.accessClient.getUserAccess(userId,controller.signal)
      if (disposed || revision !== accessRevision || selectedUser.value?.id !== userId) return
      accessProfile.value = profile
      accessDraft.value = {mode:profile.mode ?? 'LIMITED',permissions:profile.mode === 'FULL' ? [] : [...profile.permissions]}
    }
  } catch (cause) {
    if (disposed || revision !== accessRevision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    showError(cause,'Не удалось загрузить права доступа.')
  } finally { if (!disposed && revision === accessRevision) accessLoading.value = false }
}
function accessContext(): AdminAccessConfirmationContext | null {
  const user=selectedUser.value, profile=accessProfile.value
  if(!user || !profile || !props.authority) return null
  return { ownerKey:JSON.stringify([accessRevision,props.authority,profile]), userId:user.id,userName:user.fullName,expectedVersion:profile.version,
    mode:accessOperation.value === 'revoke' ? null : accessDraft.value.mode, permissions:accessOperation.value === 'revoke' ? [] : accessDraft.value.permissions,
    before:profile.permissions,after:accessOperation.value === 'revoke' ? [] : accessEffectiveDraft.value }
}
function closeAccessConfirmation(): void { accessFlow.cancel(); accessConfirmation.value=null; accessConfirmed.value=false }
function prepareAccess(operation: 'update'|'revoke' = 'update'): void {
  if (saving.value || accessLoading.value || !isFull.value || !accessSelectionValid.value && operation === 'update') return
  if (accessOwner.value === 'create') { createAccess.value = {...accessDraft.value,permissions:[...accessDraft.value.permissions]}; createAccessConfirmed.value = false; view.value = 'create'; return }
  if (!accessProfile.value || !canEditAccess.value || accessUncertain.value || !accessCatalog.value) return
  accessOperation.value = operation
  const context=accessContext()
  accessConfirmation.value=context ? accessFlow.start(context,accessCatalog.value.permissions) : null
  accessConfirmed.value=false
  if(!accessConfirmation.value) error.value='Нет изменений прав для подтверждения.'
}
async function reloadAccess(): Promise<void> {
  const user = selectedUser.value
  if (!user || !props.accessClient || accessLoading.value) return
  const revision = ++accessRevision
  const controller = new AbortController()
  accessAbort = controller
  accessLoading.value = true
  try {
    const profile = await props.accessClient.getUserAccess(user.id,controller.signal)
    if (disposed || revision !== accessRevision || selectedUser.value?.id !== user.id) return
    accessProfile.value = profile
    accessDraft.value = {mode:profile.mode ?? 'LIMITED',permissions:profile.mode === 'FULL' ? [] : [...profile.permissions]}
    closeAccessConfirmation()
    accessUncertain.value = false
    error.value = 'Текущие права обновлены. Сравни их с выбранными и подтверди новое действие.'
  } catch (cause) {
    if (disposed || revision !== accessRevision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    showError(cause,'Не удалось обновить текущие права.')
  } finally { if (!disposed && revision === accessRevision) accessLoading.value = false }
}
async function saveAccess(): Promise<void> {
  const user = selectedUser.value, current = accessProfile.value
  if (!user || !current || !props.accessClient || !accessConfirmed.value || saving.value || !canEditAccess.value) return
  const context=accessContext()
  const confirmation=context ? accessFlow.confirm(context) : {kind:'invalid'} as const
  if(confirmation.kind === 'invalid') { closeAccessConfirmation(); error.value='Права изменились. Проверь выбор и подтверди изменения заново.';return }
  if(confirmation.kind === 'next') { accessConfirmation.value=confirmation.state;accessConfirmed.value=false;return }
  const snapshot=confirmation.snapshot
  closeAccessConfirmation()
  const {revision,controller} = beginMutation()
  saving.value = true
  error.value = null
  accessConflict.value = false
  accessUnknownResult.value = false
  try {
    if (snapshot.mode === null) await props.accessClient.revokeUserAccess(snapshot.userId,snapshot.expectedVersion,controller.signal)
    else await props.accessClient.updateUserAccess(snapshot.userId,{mode:snapshot.mode,permissions:[...snapshot.permissions],expectedVersion:snapshot.expectedVersion},controller.signal)
    if (!isCurrentMutation(revision,controller)) return
    closeAccessConfirmation()
    view.value = 'detail'
    notice.value = accessOperation.value === 'revoke' ? 'Права администратора отозваны. Аккаунт и остальные роли сохранены.' : 'Права администратора сохранены.'
    await selectUser(user)
    if (!isCurrentMutation(revision,controller)) return
    notice.value = accessOperation.value === 'revoke' ? 'Права администратора отозваны. Аккаунт и остальные роли сохранены.' : 'Права администратора сохранены.'
  } catch (cause) {
    if (!isCurrentMutation(revision,controller) || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    closeAccessConfirmation()
    if (cause instanceof AdminAccessApiError && cause.response.status === 409) {
      accessProfile.value = cause.currentProfile
      if(cause.currentProfile) accessDraft.value={mode:cause.currentProfile.mode ?? 'LIMITED',permissions:cause.currentProfile.mode === 'FULL' ? [] : [...cause.currentProfile.permissions]}
      accessConflict.value = true
      accessUncertain.value = !cause.currentProfile
      error.value = 'В другом сеансе права уже изменили. Здесь отмечены текущие права с сервера. Проверь их и выбери новое действие.'
      if (!cause.currentProfile) await reloadAccess()
    } else if (!(cause instanceof AdminAccessApiError) || cause.response.status >= 500) {
      accessUncertain.value = true
      accessUnknownResult.value = true
      await reloadAccess()
      if (isCurrentMutation(revision,controller)) error.value = 'Результат изменения не подтверждён. Проверь текущие права перед новым действием.'
    } else showError(cause,'Не удалось изменить права доступа.')
  } finally { if (isCurrentMutation(revision,controller)) { saving.value = false; mutationAbortController = null } }
}
const unknownMutation = ref(false)
const copied = ref(false)
const draftStatuses = computed(() => draftRole.value ? roleStatusOptions[draftRole.value] : [])
function back(): void {
  if (saving.value || lifecycleUncertain.value || accessUncertain.value) return
  if (view.value === 'permissions') { closeAccessConfirmation(); ++accessRevision; accessAbort?.abort(); accessLoading.value = false; view.value = accessOwner.value === 'create' ? 'create' : 'edit'; return }
  cancelProfileLoad()
  ++lifecycleRevision
  lifecycleAbort?.abort()
  lifecycleLoading.value = false
  archivePreview.value = null
  archivePassword.value = ''
  archiveInput = null
  restoreOperation = null
  error.value = null
  notice.value = null
  copied.value = false
  if (['edit', 'role', 'transfer', 'recovery','recovery-issued','archive','restore'].includes(view.value)) view.value = 'detail'
  else { const reconcile = unknownMutation.value; view.value = 'list'; selectedUser.value = null; profileOriginal.value = null; unknownMutation.value = false; if (reconcile) void refresh() }
}
defineExpose({ back })
watch(view, value => { addRoleExpanded.value = false; emit('navigationVisibility', value === 'list') }, { immediate: true })
watch(() => [accessDraft.value,accessProfile.value,props.authority,selectedUser.value?.id,selectedUser.value?.fullName], () => { if(accessConfirmation.value) closeAccessConfirmation() }, {deep:true,flush:'sync'})
function openFilters(): void { draftRole.value = roleFilter.value; draftStatus.value = statusFilter.value; draftSort.value = sortDirection.value; view.value = 'filters' }
function applyFilters(): void { roleFilter.value = draftRole.value; statusFilter.value = draftRole.value ? draftStatus.value : ''; sortDirection.value = draftSort.value; page.value = 0; view.value = 'list'; void refresh() }
function resetFilters(): void { search.value = ''; roleFilter.value = ''; statusFilter.value = ''; sortDirection.value = 'asc'; page.value = 0; void refresh() }
function openCreate(): void { if (!adminHasPermission(props.authority,'USERS_MANAGE')) return; resetCreateForm(); createAccess.value = {mode:'LIMITED',permissions:[]}; createAccessConfirmed.value = false; unknownMutation.value = false; view.value = 'create'; error.value = null; notice.value = null }
function closeDropdown(event: Event): void { (event.currentTarget as HTMLElement).closest('details')?.removeAttribute('open') }
async function copyCredential(value: string): Promise<void> {
  const userId = selectedUser.value?.id
  try { await navigator.clipboard.writeText(value); if (!disposed && selectedUser.value?.id === userId && canCredentials.value) copied.value = true }
  catch { if (!disposed && selectedUser.value?.id === userId && canCredentials.value) notice.value = 'Выдели значение и скопируй его вручную.' }
}
function openRole(role: Exclude<AdminManagedRole, 'HEADMAN'>): void { editorRole.value = role; if (selectedUser.value) syncEditorFromUser(selectedUser.value); view.value = 'role' }
function openTransfer(): void { view.value = 'transfer' }

const createLastName = ref('')
const createFirstName = ref('')
const createMiddleName = ref('')
const createRole = ref<Exclude<AdminManagedRole, 'HEADMAN'>>('STUDENT')
const createGroupId = ref('')
const createEmployeeNumber = ref('')
const createTelegramId = ref('')
const profileEditorVisible = computed({ get: () => view.value === 'edit', set: (value: boolean) => { view.value = value ? 'edit' : 'detail' } })
const profileLoading = ref(false)
const profileOriginal = ref<AdminUser | null>(null)
const profileLastName = ref('')
const profileFirstName = ref('')
const profileMiddleName = ref('')
const profileEmployeeNumber = ref('')
const editorRole = ref<Exclude<AdminManagedRole, 'HEADMAN'>>('STUDENT')
const editorStatus = ref<AdminRoleStatus>('ACTIVE')
const editorGroupId = ref('')
const editorEmployeeNumber = ref('')
const editorTelegramId = ref('')
const transferGroupId = ref('')
const transferReason = ref('')

let disposed = false
let listRequestRevision = 0
let listAbortController: AbortController | null = null
let mutationRevision = 0
let mutationAbortController: AbortController | null = null
let profileLoadRevision = 0
let profileLoadAbortController: AbortController | null = null

const roleFilters: readonly { value: AdminManagedRole | ''; label: string }[] = [
  { value: '', label: 'Все роли' },
  { value: 'STUDENT', label: 'Студенты' },
  { value: 'TEACHER', label: 'Преподаватели' },
  { value: 'ADMIN', label: 'Администраторы' },
  { value: 'HEADMAN', label: 'Старосты' },
]
const roleStatusOptions: Record<AdminManagedRole, readonly AdminRoleStatus[]> = {
  STUDENT: ['ACTIVE', 'EXPELLED', 'GRADUATED', 'SUSPENDED', 'ARCHIVED'],
  TEACHER: ['ACTIVE', 'DISMISSED', 'SUSPENDED', 'ARCHIVED'],
  ADMIN: ['ACTIVE', 'ARCHIVED'],
  HEADMAN: ['ACTIVE', 'SUSPENDED', 'ARCHIVED'],
}
const selectedGrant = computed<AdminRoleGrant | null>(() =>
  selectedUser.value?.roles.find((grant) => grant.role === editorRole.value) ?? null)
const needsRoleAssignment = computed(() => !selectedGrant.value || selectedGrant.value.status === 'ARCHIVED')
const editorCanUpdate = computed(() => selectedGrant.value?.canUpdate ?? true)
const editorStatuses = computed(() => {
  if (selectedGrant.value) return selectedGrant.value.applicableStatuses.filter(status => status !== 'ARCHIVED')
  if (editorRole.value === 'ADMIN') return ['ACTIVE'] as const
  if (editorRole.value === 'TEACHER') return ['ACTIVE', 'DISMISSED', 'SUSPENDED'] as const
  return ['ACTIVE', 'EXPELLED', 'GRADUATED', 'SUSPENDED'] as const
})
const hasStudentGrant = computed(() => Boolean(selectedUser.value?.roles.some((grant) => grant.role === 'STUDENT')))
const canTransfer = computed(() => hasStudentGrant.value && selectedUser.value?.roles.some((grant) => grant.role === 'STUDENT' && grant.status === 'ACTIVE'))
const profileHasTeacherRole = computed(() => selectedUser.value?.role === 'TEACHER'
  || Boolean(selectedUser.value?.roles.some((grant) => grant.role === 'TEACHER')))

onMounted(() => void refresh())

async function openArchive(): Promise<void> {
  const user = selectedUser.value
  if (!user || saving.value || lifecycleUncertain.value || !canArchive.value) return
  view.value = 'archive'
  archivePreview.value = null
  archivePassword.value = ''
  archivePasswordError.value = null
  archiveInput = null
  lifecycleConfirmed.value = false
  lifecycleStale.value = false
  error.value = null
  const revision = ++lifecycleRevision
  lifecycleAbort?.abort()
  const controller = new AbortController()
  lifecycleAbort = controller
  lifecycleLoading.value = true
  try {
    const result = await props.client.previewArchive(user.id,controller.signal)
    if (disposed || revision !== lifecycleRevision || selectedUser.value?.id !== user.id || view.value !== 'archive') return
    archivePreview.value = result
  } catch (cause) {
    if (disposed || revision !== lifecycleRevision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    showError(cause,'Не удалось подготовить архивирование.')
  } finally { if (!disposed && revision === lifecycleRevision) lifecycleLoading.value = false }
}
function openRestore(): void {
  if (saving.value || !canArchive.value) return
  view.value = 'restore'
  lifecycleConfirmed.value = false
  lifecycleUncertain.value = false
  restoreOperation = null
  error.value = null
}
async function submitLifecycle(): Promise<void> {
  const user = selectedUser.value
  const restoring = view.value === 'restore'
  if (!user || saving.value || lifecycleLoading.value || !lifecycleConfirmed.value || !canArchive.value) return
  if (!restoring) {
    const preview = archivePreview.value
    if (!preview || lifecycleStale.value) return
    if (!lifecycleUncertain.value && Date.parse(preview.expiresAt) <= Date.now()) { lifecycleStale.value = true; error.value = 'Предпросмотр устарел. Обнови данные и подтверди действие ещё раз.'; return }
    if (!lifecycleUncertain.value && preview.requiresPassword && !archivePassword.value) { archivePasswordError.value = 'Введи свой текущий пароль.'; return }
    archiveInput ??= { operationId: crypto.randomUUID(), previewDigest: preview.previewDigest }
    if (!lifecycleUncertain.value) archiveInput = { operationId: archiveInput.operationId, previewDigest: archiveInput.previewDigest, ...(preview.requiresPassword ? { password: archivePassword.value } : {}) }
  } else restoreOperation ??= crypto.randomUUID()
  archivePasswordError.value = null
  const { revision,controller } = beginMutation()
  saving.value = true
  error.value = null
  try {
    if (restoring) await props.client.restoreUser(user.id,restoreOperation!,controller.signal)
    else await props.client.archiveUser(user.id,archiveInput!,controller.signal)
    if (!isCurrentMutation(revision,controller)) return
    lifecycleUncertain.value = false
    archivePassword.value = ''
    archiveInput = null
    restoreOperation = null
    archivePreview.value = null
    selectedUser.value = null
    profileOriginal.value = null
    view.value = 'list'
    notice.value = restoring ? 'Аккаунт восстановлен без активных прав. Назначь нужные роли заново.' : 'Аккаунт архивирован. История сохранена.'
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision,controller) || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    if (!(cause instanceof AdminUsersApiError) || cause.response.status >= 500) {
      lifecycleUncertain.value = true
      error.value = 'Результат пока не подтверждён. Повтори подтверждение: действие не выполнится дважды.'
    } else {
      lifecycleUncertain.value = false
      archivePassword.value = ''
      if (archiveInput) archiveInput = { operationId: archiveInput.operationId, previewDigest: archiveInput.previewDigest }
      if (!restoring && cause.response.status === 403 && String(cause.problem?.code) === 'CURRENT_PASSWORD_INVALID') archivePasswordError.value = 'Текущий пароль неверен. Введи его ещё раз.'
      else if (cause.response.status === 409 && !restoring) { lifecycleStale.value = true; archiveInput = null; error.value = 'Данные изменились. Обнови предпросмотр и подтверди действие ещё раз.' }
      else showError(cause, 'Не удалось сохранить действие.')
    }
  } finally { if (isCurrentMutation(revision,controller)) { saving.value = false; mutationAbortController = null } }
}

function beginListRequest(): { revision: number; controller: AbortController } {
  const revision = ++listRequestRevision
  listAbortController?.abort()
  const controller = new AbortController()
  listAbortController = controller
  return { revision, controller }
}

function isCurrentListRequest(revision: number, controller: AbortController): boolean {
  return !disposed && revision === listRequestRevision && listAbortController === controller
}

function beginMutation(): { revision: number; controller: AbortController } {
  const revision = ++mutationRevision
  mutationAbortController?.abort()
  const controller = new AbortController()
  mutationAbortController = controller
  listAbortController?.abort()
  ++listRequestRevision
  listAbortController = null
  loading.value = false
  return { revision, controller }
}

function isCurrentMutation(revision: number, controller: AbortController): boolean {
  return !disposed && revision === mutationRevision && mutationAbortController === controller
}

function isCurrentProfileLoad(revision: number, controller: AbortController, userId: number): boolean {
  return !disposed
    && revision === profileLoadRevision
    && profileLoadAbortController === controller
    && selectedUser.value?.id === userId
}

function cancelProfileLoad(): void {
  ++profileLoadRevision
  profileLoadAbortController?.abort()
  profileLoadAbortController = null
  profileLoading.value = false
}

async function refresh(): Promise<void> {
  if (!canRead.value) { users.value = []; loading.value = false; return }
  const { revision, controller } = beginListRequest()
  loading.value = true
  error.value = null
  try {
    const result = await props.client.listUsers({
      search: search.value,
      role: roleFilter.value,
      roleStatus: statusFilter.value,
      page: page.value,
      size: pageSize,
      sortDirection: sortDirection.value,
    }, controller.signal)
    if (!isCurrentListRequest(revision, controller)) return
    users.value = result.items
    totalPages.value = result.totalPages
    totalElements.value = result.totalElements
    if (result.totalPages > 0 && page.value >= result.totalPages) {
      page.value = result.totalPages - 1
      return
    }
    const selectedId = selectedUser.value?.id
    if (selectedId !== undefined && view.value === 'list') selectedUser.value = null
  } catch (cause) {
    if (!isCurrentListRequest(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Список пользователей не удалось загрузить.')
  } finally {
    if (isCurrentListRequest(revision, controller)) {
      loading.value = false
      listAbortController = null
    }
  }
}

async function createUser(): Promise<void> {
  if (saving.value || unknownMutation.value || profileEditorVisible.value || !adminHasPermission(props.authority,'USERS_MANAGE')) return
  const role = createRole.value
  if (role === 'ADMIN' && (!isFull.value || !createAccessConfirmed.value || createAccess.value.mode === 'LIMITED' && !createAccess.value.permissions.length)) { error.value = 'Выбери права администратора.'; return }
  let input: CreateAdminUserInput
  try {
    input = {
      lastName: createLastName.value,
      firstName: createFirstName.value,
      middleName: createMiddleName.value,
      role,
      ...(role === 'ADMIN' ? {adminAccess:createAccess.value} : {}),
      ...(role === 'STUDENT' && createGroupId.value.trim() ? { groupId: parsePositive(createGroupId.value, 'groupId') } : {}),
      ...(role === 'TEACHER' && createEmployeeNumber.value.trim() ? { employeeNumber: createEmployeeNumber.value } : {}),
      ...(createTelegramId.value.trim() ? { telegramId: parsePositive(createTelegramId.value, 'telegramId') } : {}),
    }
    if (!input.lastName.trim() || !input.firstName.trim()) throw new Error('Укажи фамилию и имя.')
    if (role === 'STUDENT' && (!input.groupId || !input.telegramId)) throw new Error('Выбери группу и укажи Telegram ID студента.')
    if (role === 'TEACHER' && !input.employeeNumber?.trim()) throw new Error('Укажи табельный номер преподавателя.')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Проверь данные пользователя.'
    return
  }
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const created = await props.client.createUser(input, controller.signal)
    if (!isCurrentMutation(revision, controller)) return
    selectedUser.value = created
    view.value = 'created'
    notice.value = null
    resetCreateForm()
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Пользователя не удалось создать.', true)
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function updateRole(): Promise<void> {
  const user = selectedUser.value
  if (!user || saving.value || unknownMutation.value || profileEditorVisible.value || !editorCanUpdate.value || !canManage.value) return
  if (editorRole.value === 'ADMIN') return
  let input: UpdateAdminRoleInput
  try {
    input = {
      status: editorStatus.value,
      ...(editorGroupId.value.trim() ? { groupId: parsePositive(editorGroupId.value, 'groupId') } : {}),
      ...(editorEmployeeNumber.value.trim() ? { employeeNumber: editorEmployeeNumber.value } : {}),
      ...(editorTelegramId.value.trim() ? { telegramId: parsePositive(editorTelegramId.value, 'telegramId') } : {}),
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Проверь данные роли.'
    return
  }
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await props.client.updateRole(user.id, editorRole.value, input, controller.signal)
    if (!isCurrentMutation(revision, controller)) return
    selectedUser.value = updated
    view.value = 'detail'
    await selectUser(updated)
    if (!isCurrentMutation(revision, controller)) return
    notice.value = 'Роль сохранена.'
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Роль не удалось обновить.', true)
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function beginProfileEdit(): Promise<void> {
  const user = selectedUser.value
  if (!user || saving.value || profileLoading.value || createVisible.value || !canManage.value) return
  cancelProfileLoad()
  const revision = ++profileLoadRevision
  const controller = new AbortController()
  profileLoadAbortController = controller
  profileEditorVisible.value = true
  profileLoading.value = true
  profileOriginal.value = null
  error.value = null
  notice.value = null
  try {
    const fresh = await props.client.getUser(user.id, controller.signal)
    if (!isCurrentProfileLoad(revision, controller, user.id)) return
    selectedUser.value = fresh
    profileOriginal.value = fresh
    unknownMutation.value = false
    profileLastName.value = fresh.lastName
    profileFirstName.value = fresh.firstName
    profileMiddleName.value = fresh.middleName ?? ''
    profileEmployeeNumber.value = fresh.employeeNumber ?? ''
    syncEditorFromUser(fresh)
  } catch (cause) {
    if (!isCurrentProfileLoad(revision, controller, user.id) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    profileEditorVisible.value = false
    showError(cause, 'Данные пользователя не удалось загрузить.')
  } finally {
    if (isCurrentProfileLoad(revision, controller, user.id)) {
      profileLoading.value = false
      profileLoadAbortController = null
    }
  }
}

function cancelProfileEdit(): void {
  cancelProfileLoad()
  profileEditorVisible.value = false
  profileOriginal.value = null
}

async function updateProfile(): Promise<void> {
  const user = selectedUser.value
  const original = profileOriginal.value
  if (!user || !original || user.id !== original.id || saving.value || unknownMutation.value || profileLoading.value || !canManage.value) return

  const lastName = profileLastName.value.trim()
  const firstName = profileFirstName.value.trim()
  const middleName = profileMiddleName.value.trim()
  const employeeNumber = profileEmployeeNumber.value.trim()
  if (!lastName || !firstName) {
    error.value = 'Укажи фамилию и имя.'
    return
  }
  if (profileHasTeacherRole.value && !employeeNumber) {
    error.value = 'Для преподавателя табельный номер обязателен.'
    return
  }

  const input: {
    lastName?: string
    firstName?: string
    middleName?: string
    employeeNumber?: string
  } = {}
  if (lastName !== original.lastName) input.lastName = lastName
  if (firstName !== original.firstName) input.firstName = firstName
  if (middleName !== (original.middleName ?? '')) input.middleName = middleName
  if (employeeNumber !== (original.employeeNumber ?? '')) input.employeeNumber = employeeNumber
  if (Object.keys(input).length === 0) {
    cancelProfileEdit()
    notice.value = 'Изменений нет.'
    return
  }

  const patch: UpdateAdminUserProfileInput = input
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await props.client.updateProfile(user.id, patch, controller.signal)
    if (!isCurrentMutation(revision, controller) || selectedUser.value?.id !== user.id) return
    selectedUser.value = updated
    syncEditorFromUser(updated)
    cancelProfileEdit()
    await selectUser(updated)
    if (!isCurrentMutation(revision, controller)) return
    notice.value = 'Данные пользователя сохранены.'
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Данные пользователя не удалось сохранить.', true)
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function transferStudent(): Promise<void> {
  const user = selectedUser.value
  if (!user || saving.value || unknownMutation.value || !canTransfer.value || !canManage.value) return
  let newGroupId: number
  try {
    newGroupId = parsePositive(transferGroupId.value, 'newGroupId')
    if (!transferReason.value.trim()) throw new Error('Укажи причину перевода.')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Проверь данные перевода.'
    return
  }
  const { revision, controller } = beginMutation()
  saving.value = true
  error.value = null
  notice.value = null
  try {
    const updated = await props.client.transferStudent(user.id, {
      newGroupId,
      reason: transferReason.value,
    }, controller.signal)
    if (!isCurrentMutation(revision, controller)) return
    selectedUser.value = updated
    transferGroupId.value = ''
    transferReason.value = ''
    view.value = 'detail'
    await selectUser(updated)
    if (!isCurrentMutation(revision, controller)) return
    notice.value = 'Студент переведён в другую группу.'
    await refresh()
  } catch (cause) {
    if (!isCurrentMutation(revision, controller) || isAbortError(cause) || cause instanceof StaleSessionGenerationError) return
    showError(cause, 'Перевод не удалось сохранить.', true)
  } finally {
    if (isCurrentMutation(revision, controller)) {
      saving.value = false
      mutationAbortController = null
    }
  }
}

async function selectUser(user: AdminUser): Promise<void> {
  cancelProfileLoad()
  selectedUser.value = { ...user, initialPassword: null }
  syncEditorFromUser(user)
  view.value = 'detail'
  error.value = null
  notice.value = null
  unknownMutation.value = false
  const revision = ++profileLoadRevision
  const controller = new AbortController()
  profileLoadAbortController = controller
  profileLoading.value = true
  try {
    const fresh = await props.client.getUser(user.id, controller.signal)
    if (!isCurrentProfileLoad(revision,controller,user.id)) return
    selectedUser.value = fresh
    syncEditorFromUser(fresh)
  } catch (cause) {
    if (!isCurrentProfileLoad(revision,controller,user.id) || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    showError(cause,'Данные пользователя не удалось загрузить.')
  } finally { if (isCurrentProfileLoad(revision,controller,user.id)) { profileLoading.value = false; profileLoadAbortController = null } }
}

function syncEditorFromUser(user: AdminUser): void {
  const grant = user.roles.find((candidate) => candidate.role === editorRole.value)
  editorStatus.value = grant?.status === 'ARCHIVED' ? 'ACTIVE' : grant?.status ?? 'ACTIVE'
  editorGroupId.value = grant?.groupId === null || grant?.groupId === undefined ? '' : String(grant.groupId)
  editorEmployeeNumber.value = editorRole.value === 'TEACHER' ? user.employeeNumber ?? '' : ''
  editorTelegramId.value = editorRole.value === 'STUDENT' ? user.telegramId === null ? '' : String(user.telegramId) : ''
  transferGroupId.value = ''
  transferReason.value = ''
}


function resetCreateForm(): void {
  createLastName.value = ''
  createFirstName.value = ''
  createMiddleName.value = ''
  createRole.value = 'STUDENT'
  createGroupId.value = ''
  createEmployeeNumber.value = ''
  createTelegramId.value = ''
}

function submitSearch(): void {
  if (saving.value || profileEditorVisible.value) return
  page.value = 0
  void refresh()
}

function changePage(next: number): void {
  if (saving.value || profileEditorVisible.value || next < 0 || (totalPages.value > 0 && next >= totalPages.value)) return
  page.value = next
  void refresh()
}

function ownerError(cause: unknown): void {
  closeAccessConfirmation()
  archivePasswordError.value = null
  ++listRequestRevision; listAbortController?.abort()
  ++mutationRevision; mutationAbortController?.abort()
  ++accessRevision; accessAbort?.abort()
  ++lifecycleRevision; lifecycleAbort?.abort()
  cancelProfileLoad()
  selectedUser.value = null
  profileOriginal.value = null
  archivePassword.value = ''
  archiveInput = null
  emit('ownerError',cause)
}

function showError(cause: unknown, fallback: string, mutation = false): void {
  if (mutation && (!(cause instanceof AdminUsersApiError || cause instanceof AdminAccessApiError) || cause.response.status >= 500)) {
    unknownMutation.value = true
    error.value = 'Не удалось подтвердить результат. Вернись к списку и проверь данные перед новым действием.'
    return
  }
  error.value = (cause instanceof AdminUsersApiError || cause instanceof AdminAccessApiError)
    ? cause.problem?.detail ?? cause.message
    : fallback
  if ((cause instanceof AdminUsersApiError || cause instanceof AdminAccessApiError) && (cause.response.status === 401 || cause.response.status === 403)) ownerError(cause)
}

function parsePositive(value: string, field: string): number {
  const parsed = Number(value.trim())
  if (!Number.isSafeInteger(parsed) || parsed <= 0) throw new Error(`${field.includes('roup') ? 'Выбери группу' : 'Укажи корректный Telegram ID'}.`)
  return parsed
}

function isAbortError(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'AbortError'
}

function roleLabel(role: AdminManagedRole): string {
  return role === 'STUDENT' ? 'Студент' : role === 'TEACHER' ? 'Преподаватель' : role === 'ADMIN' ? 'Администратор' : 'Староста'
}

function statusLabel(status: AdminRoleStatus): string {
  return status === 'ACTIVE' ? 'Активна'
    : status === 'SUSPENDED' ? 'Приостановлена'
      : status === 'EXPELLED' ? 'Отчислен'
        : status === 'GRADUATED' ? 'Выпустился'
          : status === 'DISMISSED' ? 'Уволен' : 'Архивная'
}

watch(editorStatuses, (statuses) => {
  if (statuses.length && !statuses.includes(editorStatus.value as never)) editorStatus.value = statuses[0]!
})
watch(draftRole, () => { if (!draftStatuses.value.includes(draftStatus.value as AdminRoleStatus)) draftStatus.value = '' })

watch(() => props.authority, (current,previous) => {
  if (current === previous) return
  ++listRequestRevision; listAbortController?.abort()
  ++mutationRevision; mutationAbortController?.abort()
  ++accessRevision; accessAbort?.abort()
  ++lifecycleRevision; lifecycleAbort?.abort()
  cancelProfileLoad()
  selectedUser.value = null; profileOriginal.value = null; users.value = []
  archiveInput = null; archivePassword.value = ''; accessProfile.value = null
  saving.value = false; accessLoading.value = false; lifecycleLoading.value = false
  closeAccessConfirmation(); archivePasswordError.value=null
  view.value = 'list'; accessUncertain.value = false; lifecycleUncertain.value = false
  void refresh()
})

onBeforeUnmount(() => {
  disposed = true
  ++listRequestRevision
  ++mutationRevision
  listAbortController?.abort()
  mutationAbortController?.abort()
  cancelProfileLoad()
  ++accessRevision
  accessAbort?.abort()
  ++lifecycleRevision
  lifecycleAbort?.abort()
  archiveInput = null
  archivePassword.value = ''
  selectedUser.value = null
  profileOriginal.value = null
  listAbortController = null
  mutationAbortController = null
})
</script>

<template>
  <main
    class="admin-users-screen"
    :data-theme="theme"
    :data-view="view"
    :aria-busy="loading && view === 'list' || profileLoading || saving"
    aria-labelledby="admin-users-title"
  >
    <header class="admin-users-screen__header">
      <button
        v-if="view !== 'list'"
        class="admin-users-icon-button"
        type="button"
        aria-label="Назад"
        :disabled="saving || lifecycleUncertain"
        @click="back"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="admin-users-title">
        {{ title }}
      </h1>
    </header>
    <AdminNotice
      v-if="error"
      :tone="errorTone"
      :title="errorTitle"
    >
      {{ error }}
      <template
        v-if="view === 'list' && canRead"
        #actions
      >
        <button
          class="admin-users-action"
          type="button"
          @click="refresh"
        >
          Попробовать ещё раз
        </button>
      </template>
    </AdminNotice>
    <AdminNotice
      v-if="notice"
      tone="success"
      title="Готово"
    >
      {{ notice }}
    </AdminNotice>

    <AdminNotice
      v-if="!canRead"
      tone="warning"
      title="Нет доступа"
    >
      Просмотр пользователей недоступен.
    </AdminNotice>
    <template v-else-if="view === 'list'">
      <button
        v-if="adminHasPermission(authority,'USERS_MANAGE')"
        class="admin-users-action"
        type="button"
        @click="openCreate"
      >
        Создать пользователя
      </button>
      <form
        class="admin-users-search"
        role="search"
        @submit.prevent="submitSearch"
      >
        <MobileIcon name="search" /><input
          v-model="search"
          type="search"
          aria-label="Найти пользователя"
          placeholder="Найти пользователя"
          autocomplete="off"
        ><button
          type="submit"
          aria-label="Найти"
        >
          <MobileIcon
            name="back"
            class="admin-users-next"
          />
        </button>
      </form>
      <div class="admin-users-tools">
        <button
          class="admin-users-action admin-users-action--secondary"
          type="button"
          @click="openFilters"
        >
          Фильтры и сортировка
        </button><button
          class="admin-users-icon-button"
          type="button"
          aria-label="Сбросить поиск и фильтры"
          @click="resetFilters"
        >
          <MobileIcon name="retry" />
        </button>
      </div>
      <p class="admin-users-muted">
        Всего: {{ totalElements.toLocaleString('ru-RU') }}<template v-if="roleFilter">
          · {{ roleLabel(roleFilter) }}
        </template><template v-if="statusFilter">
          · {{ statusLabel(statusFilter) }}
        </template>
      </p>
      <p
        v-if="loading"
        class="admin-users-state"
        role="status"
      >
        Загружаем пользователей…
      </p>
      <div v-else-if="error" />
      <p
        v-else-if="!users.length"
        class="admin-users-state"
        role="status"
      >
        Пользователи не найдены. Измени поиск или сбрось фильтры.
      </p>
      <div
        v-else
        class="admin-users-results"
      >
        <button
          v-for="user in users"
          :key="user.id"
          class="admin-users-card"
          type="button"
          :data-user-id="user.id"
          @click="selectUser(user)"
        >
          <span class="admin-users-card__copy">
            <span class="admin-users-card__overview">
              <span class="admin-users-role-icons"><span
                v-for="grant in user.roles"
                :key="grant.role"
                class="admin-users-role-icon"
                :data-active="grant.status === 'ACTIVE'"
                role="img"
                :aria-label="`${roleLabel(grant.role)}: ${statusLabel(grant.status)}`"
              ><AdminRoleIcon :role="grant.role" /></span></span>
              <span
                class="admin-users-status admin-users-status--badge"
                :data-tone="statusTone(user.status)"
              ><span aria-hidden="true">●</span>{{ accountStatusLabel(user.status) }}</span>
            </span>
            <strong><span>{{ user.fullName }}</span><MobileIcon
              name="back"
              class="admin-users-next"
            /></strong>
            <small>Логин: {{ user.login }}</small>
            <small
              v-for="groupName in [...new Set(user.roles.flatMap(grant => grant.groupName ? [grant.groupName] : []))]"
              :key="groupName"
            >Группа: {{ groupName }}</small>
            <small v-if="!user.roles.length">Роли не назначены</small>
          </span>
        </button>
      </div>
      <nav
        v-if="totalPages > 1"
        class="admin-users-pagination"
        aria-label="Страницы пользователей"
      >
        <button
          class="admin-users-icon-button"
          type="button"
          aria-label="Предыдущая страница"
          :disabled="loading || page === 0"
          @click="changePage(page - 1)"
        >
          <MobileIcon name="back" />
        </button><span>{{ page * pageSize + 1 }}–{{ Math.min((page + 1) * pageSize, totalElements) }} из {{ totalElements.toLocaleString('ru-RU') }}</span><button
          class="admin-users-icon-button"
          type="button"
          aria-label="Следующая страница"
          :disabled="loading || page + 1 >= totalPages"
          @click="changePage(page + 1)"
        >
          <MobileIcon
            name="back"
            class="admin-users-next"
          />
        </button>
      </nav>
    </template>

    <form
      v-else-if="view === 'filters'"
      class="admin-users-form"
      @submit.prevent="applyFilters"
    >
      <details
        class="admin-users-select"
        data-picker="filter-role"
      >
        <summary><span><small>Роли</small>{{ draftRole ? roleLabel(draftRole) : 'Все роли' }}</span><MobileIcon name="chevron-down" /></summary><div class="admin-users-select__options">
          <button
            v-for="option in roleFilters"
            :key="option.value"
            type="button"
            :aria-pressed="draftRole === option.value"
            @click="draftRole = option.value; closeDropdown($event)"
          >
            {{ option.label }}
          </button>
        </div>
      </details>
      <details
        class="admin-users-select"
        data-picker="filter-status"
      >
        <summary><span><small>Статусы</small>{{ draftStatus ? statusLabel(draftStatus) : 'Все статусы' }}</span><MobileIcon name="chevron-down" /></summary><div class="admin-users-select__options">
          <button
            type="button"
            @click="draftStatus = ''; closeDropdown($event)"
          >
            Все статусы
          </button><button
            v-for="status in draftStatuses"
            :key="status"
            type="button"
            :aria-pressed="draftStatus === status"
            @click="draftStatus = status; closeDropdown($event)"
          >
            {{ statusLabel(status) }}
          </button><p
            v-if="!draftRole"
            class="admin-users-muted"
          >
            Сначала выбери роль.
          </p>
        </div>
      </details>
      <details
        class="admin-users-select"
        data-picker="filter-sort"
      >
        <summary><span><small>ФИО</small>{{ draftSort === 'asc' ? 'По алфавиту · А–Я' : 'По алфавиту · Я–А' }}</span><MobileIcon name="chevron-down" /></summary><div class="admin-users-select__options">
          <button
            type="button"
            :aria-pressed="draftSort === 'asc'"
            @click="draftSort = 'asc'; closeDropdown($event)"
          >
            По алфавиту · А–Я
          </button><button
            type="button"
            :aria-pressed="draftSort === 'desc'"
            @click="draftSort = 'desc'; closeDropdown($event)"
          >
            По алфавиту · Я–А
          </button>
        </div>
      </details>
      <button
        class="admin-users-action admin-users-footer"
        type="submit"
      >
        Применить
      </button>
    </form>

    <form
      v-else-if="view === 'create'"
      class="admin-users-form"
      @submit.prevent="createUser"
    >
      <details
        class="admin-users-select"
        data-picker="create-role"
      >
        <summary><span><small>Роль</small>{{ roleLabel(createRole) }}</span><MobileIcon name="chevron-down" /></summary><div class="admin-users-select__options">
          <button
            v-for="role in (['STUDENT','TEACHER',...(isFull ? ['ADMIN'] as const : [])] as const)"
            :key="role"
            type="button"
            :aria-pressed="createRole === role"
            @click="createRole = role; closeDropdown($event)"
          >
            {{ roleLabel(role) }}
          </button>
        </div>
      </details>
      <fieldset class="admin-users-names">
        <legend>ФИО</legend><label><span>Фамилия</span><input
          v-model="createLastName"
          required
          maxlength="128"
          autocomplete="family-name"
          placeholder="Фамилия"
        ></label><label><span>Имя</span><input
          v-model="createFirstName"
          required
          maxlength="128"
          autocomplete="given-name"
          placeholder="Имя"
        ></label><label><span>Отчество</span><input
          v-model="createMiddleName"
          maxlength="128"
          autocomplete="additional-name"
          placeholder="Отчество, если есть"
        ></label>
      </fieldset>
      <AdminUserGroupPicker
        v-if="createRole === 'STUDENT'"
        v-model="createGroupId"
        :client="client"
        label="Группа"
        @owner-error="ownerError"
      />
      <label
        v-if="createRole === 'TEACHER'"
        class="admin-users-field"
      ><span>Табельный номер</span><input
        v-model="createEmployeeNumber"
        required
        maxlength="32"
        autocomplete="off"
        placeholder="Т-00421"
      ></label>
      <button
        v-if="createRole === 'ADMIN'"
        class="admin-users-select admin-users-access-choice"
        type="button"
        @click="openPermissions('create')"
      >
        <span><small>Права доступа</small>{{ createAccess.mode === 'FULL' ? 'Полный доступ' : createAccess.permissions.length ? `Выбрано прав: ${createAccess.permissions.length}` : 'Выбрать права' }}</span><MobileIcon name="chevron-down" />
      </button>
      <AdminCheckbox
        v-if="createRole === 'ADMIN' && (createAccess.mode === 'FULL' || createAccess.permissions.length)"
        v-model="createAccessConfirmed"
        :label="'Подтверждаю создание администратора: ' + (createAccess.mode === 'FULL' ? 'полный доступ' : accessLabels(createAccess.permissions).join(', '))"
      />
      <label class="admin-users-field"><span>Telegram ID<template v-if="createRole !== 'STUDENT'"> · необязательно</template></span><input
        v-model="createTelegramId"
        :required="createRole === 'STUDENT'"
        inputmode="numeric"
        pattern="[0-9]+"
        autocomplete="off"
        placeholder="123456789"
      ></label>
      <button
        class="admin-users-action admin-users-footer"
        type="submit"
        :disabled="saving || unknownMutation || createRole === 'ADMIN' && (!createAccessConfirmed || createAccess.mode === 'LIMITED' && !createAccess.permissions.length)"
        :aria-busy="saving"
      >
        {{ saving ? 'Создаём…' : 'Создать пользователя' }}
      </button>
    </form>

    <template v-else-if="view === 'created' && selectedUser">
      <AdminNotice
        tone="success"
        title="Пользователь создан"
      >
        <p>{{ selectedUser.fullName }}</p><small>{{ roleLabel(selectedUser.role) }}<template v-if="selectedUser.employeeNumber"> · {{ selectedUser.employeeNumber }}</template></small>
      </AdminNotice>
      <h2 v-if="canCredentials">
        Учётные данные
      </h2><div
        v-if="canCredentials"
        class="admin-users-credentials"
      >
        <button
          class="admin-users-credential"
          type="button"
          aria-label="Скопировать логин"
          @click="copyCredential(selectedUser.login)"
        >
          <span><small>Логин</small><strong>{{ selectedUser.login }}</strong></span><img
            :src="copyIcon"
            alt=""
            aria-hidden="true"
          >
        </button><button
          v-if="selectedUser.initialPassword"
          class="admin-users-credential"
          type="button"
          aria-label="Скопировать начальный пароль"
          @click="copyCredential(selectedUser.initialPassword!)"
        >
          <span><small>Начальный пароль</small><strong>{{ selectedUser.initialPassword }}</strong></span><img
            :src="copyIcon"
            alt=""
            aria-hidden="true"
          >
        </button>
      </div><p
        v-if="copied"
        role="status"
      >
        Скопировано
      </p>
      <button
        class="admin-users-action admin-users-footer"
        type="button"
        @click="back"
      >
        Готово
      </button>
    </template>

    <template v-else-if="view === 'detail' && selectedUser">
      <div class="admin-users-summary">
        <strong>{{ selectedUser.fullName }}</strong>
      </div>
      <h2>Данные</h2><dl class="admin-users-data">
        <div
          v-for="grant in selectedUser.roles"
          :key="grant.role"
          class="admin-users-role-record"
        >
          <dt class="admin-users-role-heading">
            <span
              class="admin-users-role-icon"
              :data-active="grant.status === 'ACTIVE'"
              aria-hidden="true"
            ><AdminRoleIcon :role="grant.role" /></span>{{ roleLabel(grant.role) }}
          </dt>
          <dd>
            <span
              class="admin-users-status"
              :data-tone="statusTone(grant.status)"
            ><span aria-hidden="true">●</span>{{ statusLabel(grant.status) }}</span><small v-if="grant.groupName">Группа: {{ grant.groupName }}</small>
          </dd>
        </div><div v-if="!selectedUser.roles.length">
          <dt>Роли</dt><dd>Роли не назначены</dd>
        </div>
        <div>
          <dt>Статус аккаунта</dt><dd>
            <span
              class="admin-users-status"
              :data-tone="statusTone(selectedUser.status)"
            ><span aria-hidden="true">●</span>{{ accountStatusLabel(selectedUser.status) }}</span>
          </dd>
        </div><div v-if="selectedUser.employeeNumber">
          <dt>Табельный номер</dt><dd>{{ selectedUser.employeeNumber }}</dd>
        </div><div v-if="selectedUser.telegramId">
          <dt>Telegram ID</dt><dd>{{ selectedUser.telegramId }}</dd>
        </div>
      </dl>
      <h2 v-if="canCredentials">
        Данные для входа
      </h2><div
        v-if="canCredentials"
        class="admin-users-credentials"
      >
        <button
          class="admin-users-credential"
          type="button"
          aria-label="Скопировать логин"
          @click="copyCredential(selectedUser.login)"
        >
          <span><small>Логин</small><strong>{{ selectedUser.login }}</strong></span><img
            :src="copyIcon"
            alt=""
            aria-hidden="true"
          >
        </button><button
          class="admin-users-credential"
          type="button"
          :disabled="saving || profileLoading || lifecycleUncertain"
          @click="view = 'recovery'"
        >
          <span><strong>Восстановить доступ</strong></span><MobileIcon
            name="back"
            class="admin-users-next"
          />
        </button>
      </div><p
        v-if="copied"
        role="status"
      >
        Скопировано
      </p>
      <button
        v-if="canManage || canCredentials || canArchive || canEditAccess"
        class="admin-users-action admin-users-footer"
        type="button"
        :disabled="profileLoading || saving"
        @click="canManage ? beginProfileEdit() : view = 'edit'"
      >
        Редактировать данные
      </button>
    </template>

    <template v-else-if="view === 'edit' && selectedUser">
      <div class="admin-users-summary">
        <strong>{{ selectedUser.fullName }}</strong><small>{{ selectedUser.login }} · {{ selectedUser.status === 'ARCHIVED' ? 'аккаунт в архиве' : 'аккаунт активен' }}</small>
      </div>
      <p
        v-if="profileLoading"
        role="status"
      >
        Загружаем данные…
      </p>
      <form
        v-else-if="canManage"
        class="admin-users-form"
        @submit.prevent="updateProfile"
      >
        <fieldset class="admin-users-names">
          <legend>ФИО</legend><label><span>Фамилия</span><input
            v-model="profileLastName"
            required
            maxlength="128"
            autocomplete="family-name"
          ></label><label><span>Имя</span><input
            v-model="profileFirstName"
            required
            maxlength="128"
            autocomplete="given-name"
          ></label><label><span>Отчество</span><input
            v-model="profileMiddleName"
            maxlength="128"
            autocomplete="additional-name"
          ></label>
        </fieldset><label
          v-if="profileHasTeacherRole || profileEmployeeNumber"
          class="admin-users-field"
        ><span>Табельный номер</span><input
          v-model="profileEmployeeNumber"
          :required="profileHasTeacherRole"
          maxlength="32"
          autocomplete="off"
        ></label><div
          v-if="selectedUser.telegramId"
          class="admin-users-field"
        >
          <span>Telegram ID</span><p>{{ selectedUser.telegramId }}</p>
        </div><button
          class="admin-users-action"
          type="submit"
          :disabled="saving || unknownMutation"
        >
          {{ saving ? 'Сохраняем…' : 'Сохранить данные' }}
        </button>
      </form>
      <button
        v-if="canEditAccess"
        class="admin-users-action admin-users-action--secondary"
        type="button"
        :disabled="saving"
        @click="openPermissions('target')"
      >
        {{ targetIsAdmin ? 'Настроить права администратора' : 'Назначить права администратора' }}
      </button>
      <h2>Роли</h2><div class="admin-users-results">
        <div
          v-for="grant in selectedUser.roles"
          :key="grant.role"
          class="admin-users-role"
        >
          <div class="admin-users-role__heading">
            <strong class="admin-users-role-heading"><span
              class="admin-users-role-icon"
              :data-active="grant.status === 'ACTIVE'"
              aria-hidden="true"
            ><AdminRoleIcon :role="grant.role" /></span>{{ roleLabel(grant.role) }}</strong><span
              class="admin-users-status admin-users-status--badge"
              :data-tone="statusTone(grant.status)"
            ><span aria-hidden="true">●</span>{{ statusLabel(grant.status) }}</span>
          </div><button
            v-if="grant.role !== 'HEADMAN' && grant.role !== 'ADMIN' && grant.canUpdate && canManage"
            class="admin-users-action admin-users-action--secondary"
            type="button"
            :disabled="saving"
            @click="openRole(grant.role)"
          >
            {{ grant.status === 'ARCHIVED' ? 'Назначить заново' : 'Изменить статус' }}
          </button><p
            v-if="grant.blockedReason"
            class="admin-users-muted"
          >
            {{ grant.blockedReason }}
          </p>
        </div>
      </div>
      <div
        v-if="canManage && (['STUDENT','TEACHER',...(isFull ? ['ADMIN'] as const : [])] as const).some(role => !selectedUser!.roles.some(grant => grant.role === role))"
        class="admin-users-select"
        data-picker="add-role"
      >
        <button
          class="admin-users-add-role"
          type="button"
          :aria-expanded="addRoleExpanded"
          aria-controls="admin-users-add-role-options"
          :disabled="saving"
          @click="addRoleExpanded = !addRoleExpanded"
        >
          <span>Добавить роль</span><MobileIcon name="plus" />
        </button><div
          v-if="addRoleExpanded"
          id="admin-users-add-role-options"
          class="admin-users-select__options"
        >
          <button
            v-for="role in (['STUDENT','TEACHER',...(isFull ? ['ADMIN'] as const : [])] as const).filter(role => !selectedUser!.roles.some(grant => grant.role === role))"
            :key="role"
            type="button"
            @click="role === 'ADMIN' ? openPermissions('target') : openRole(role)"
          >
            {{ roleLabel(role) }}
          </button>
        </div>
      </div>
      <button
        v-if="canTransfer && canManage"
        class="admin-users-action admin-users-action--secondary"
        type="button"
        :disabled="saving"
        @click="openTransfer"
      >
        Перевести в другую группу
      </button><button
        v-if="canCredentials"
        class="admin-users-action admin-users-action--secondary"
        type="button"
        :disabled="saving || profileLoading || lifecycleUncertain"
        @click="view = 'recovery'"
      >
        Восстановить доступ
      </button>
      <button
        v-if="canArchive"
        class="admin-users-action admin-users-action--secondary"
        type="button"
        :disabled="saving"
        @click="selectedUser.status === 'ARCHIVED' ? openRestore() : openArchive()"
      >
        {{ selectedUser.status === 'ARCHIVED' ? 'Восстановить аккаунт' : 'Архивировать аккаунт' }}
      </button>
    </template>

    <form
      v-else-if="view === 'role' && selectedUser"
      class="admin-users-form"
      @submit.prevent="updateRole"
    >
      <div class="admin-users-summary">
        <strong>{{ selectedUser.fullName }}</strong><small>{{ roleLabel(editorRole) }}</small>
      </div>
      <label class="admin-users-field"><span>Статус роли</span><select
        v-model="editorStatus"
        :disabled="!editorCanUpdate"
      ><option
        v-for="status in editorStatuses"
        :key="status"
        :value="status"
      >{{ statusLabel(status) }}</option></select></label>
      <AdminUserGroupPicker
        v-if="editorRole === 'STUDENT' && needsRoleAssignment"
        v-model="editorGroupId"
        :client="client"
        label="Группа"
        @owner-error="ownerError"
      />
      <label
        v-if="editorRole === 'TEACHER' && needsRoleAssignment"
        class="admin-users-field"
      ><span>Табельный номер</span><input
        v-model="editorEmployeeNumber"
        required
        maxlength="32"
      ></label><label
        v-if="editorRole === 'STUDENT' && needsRoleAssignment"
        class="admin-users-field"
      ><span>Telegram ID</span><input
        v-model="editorTelegramId"
        required
        inputmode="numeric"
        pattern="[0-9]+"
      ></label>
      <p
        v-if="selectedGrant?.blockedReason"
        class="admin-users-muted"
      >
        {{ selectedGrant.blockedReason }}
      </p><button
        class="admin-users-action admin-users-footer"
        type="submit"
        :disabled="saving || unknownMutation || !editorCanUpdate || !editorStatuses.length || editorRole === 'ADMIN'"
      >
        {{ saving ? 'Сохраняем…' : needsRoleAssignment ? 'Назначить роль' : 'Сохранить статус' }}
      </button>
    </form>

    <form
      v-else-if="view === 'transfer' && selectedUser"
      class="admin-users-form admin-users-form--compact"
      @submit.prevent="transferStudent"
    >
      <div class="admin-users-summary">
        <strong>{{ selectedUser.fullName }}</strong>
      </div><AdminUserGroupPicker
        v-model="transferGroupId"
        :client="client"
        label="Новая группа"
        :excluded-group-id="selectedUser.roles.find(grant => grant.role === 'STUDENT')?.groupId ?? null"
        @owner-error="ownerError"
      /><label class="admin-users-field"><span>Причина перевода</span><textarea
        v-model="transferReason"
        required
        maxlength="1000"
        rows="2"
        placeholder="Укажи причину"
      /></label><button
        class="admin-users-action admin-users-footer"
        type="submit"
        :disabled="saving || unknownMutation"
      >
        {{ saving ? 'Переводим…' : 'Перевести студента' }}
      </button>
    </form>
    <form
      v-else-if="(view === 'archive' || view === 'restore') && selectedUser"
      class="admin-users-form"
      @submit.prevent="submitLifecycle"
    >
      <div class="admin-users-summary">
        <strong>{{ selectedUser.fullName }}</strong><small>{{ selectedUser.login }}</small>
      </div>
      <template v-if="view === 'archive'">
        <p
          v-if="lifecycleLoading"
          role="status"
        >
          Проверяем связанные данные…
        </p>
        <dl
          v-if="archivePreview"
          class="admin-users-data"
        >
          <div><dt>Связанные группы</dt><dd>{{ archivePreview.linkedGroupCount }}</dd></div><div><dt>Отметки посещаемости</dt><dd>{{ archivePreview.attendanceMarksCount }}</dd></div><div><dt>Группы со старостой</dt><dd>{{ archivePreview.activeHeadmanGroupCount }}</dd></div><div><dt>Назначения единственного преподавателя</dt><dd>{{ archivePreview.soleTeacherAssignmentCount }}</dd></div>
        </dl>
        <label
          v-if="archivePreview?.requiresPassword"
          class="admin-users-field admin-users-field--password"
        ><span>Твой текущий пароль</span><input
          v-model="archivePassword"
          type="password"
          :required="!lifecycleUncertain"
          :disabled="lifecycleUncertain || saving"
          autocomplete="current-password"
          :aria-invalid="Boolean(archivePasswordError)"
          :aria-describedby="archivePasswordError ? 'admin-archive-password-error' : undefined"
          @input="archivePasswordError = null"
        ></label>
        <AdminNotice
          v-if="archivePasswordError"
          id="admin-archive-password-error"
          tone="danger"
          title="Проверь пароль"
        >
          {{ archivePasswordError }}
        </AdminNotice>
        <button
          v-if="!lifecycleUncertain && (!archivePreview || lifecycleStale)"
          class="admin-users-action admin-users-action--secondary"
          type="button"
          :disabled="lifecycleLoading"
          @click="openArchive"
        >
          Обновить предпросмотр
        </button>
      </template>
      <p
        v-else
        class="admin-users-feedback"
      >
        Аккаунт восстановится без активных прав. Назначь роли заново. Группы, староста, помощники и прежние сеансы не восстановятся.
      </p>
      <AdminCheckbox
        v-model="lifecycleConfirmed"
        label="Подтверждаю"
        :disabled="saving || lifecycleUncertain"
        class="admin-users-confirm-check"
      />
      <button
        class="admin-users-action admin-users-footer"
        type="submit"
        :disabled="saving || lifecycleLoading || !lifecycleConfirmed || view === 'archive' && (!archivePreview || lifecycleStale)"
      >
        {{ saving ? 'Сохраняем…' : lifecycleUncertain ? 'Повторить подтверждение' : view === 'archive' ? 'Архивировать аккаунт' : 'Восстановить аккаунт' }}
      </button>
    </form>
    <section
      v-else-if="view === 'permissions'"
      class="admin-users-form"
    >
      <p
        v-if="accessLoading"
        role="status"
      >
        Загружаем права…
      </p>
      <template v-else-if="accessCatalog">
        <div
          v-if="selectedUser && accessOwner === 'target'"
          class="admin-users-summary"
        >
          <strong>{{ selectedUser.fullName }}</strong>
        </div><AdminPermissionEditor
          v-model="accessDraft"
          :catalog="accessCatalog"
          :disabled="saving || accessUncertain"
        /><button
          v-if="accessUncertain"
          class="admin-users-action admin-users-action--secondary"
          type="button"
          @click="reloadAccess"
        >
          Обновить текущие права
        </button><button
          class="admin-users-action"
          type="button"
          :disabled="saving || accessUncertain || !accessSelectionValid || accessOwner === 'target' && !accessProfile"
          @click="prepareAccess()"
        >
          {{ accessOwner === 'create' ? 'Выбрать права' : 'Проверить изменения' }}
        </button><button
          v-if="accessOwner === 'target' && accessProfile?.assigned"
          class="admin-users-action admin-users-action--secondary"
          type="button"
          :disabled="saving || accessUncertain"
          @click="prepareAccess('revoke')"
        >
          Отозвать права администратора
        </button>
      </template>
      <button
        v-else
        class="admin-users-action admin-users-action--secondary"
        type="button"
        @click="openPermissions(accessOwner)"
      >
        Попробовать ещё раз
      </button>
      <MobileToolsSheet
        :key="accessConfirmation?.step.kind ?? 'closed'"
        :open="accessConfirmation !== null"
        :title="accessConfirmation?.step.kind === 'grant' ? 'Применить права?' : 'Отозвать права?'"
        icon-close
        @update:open="!saving && closeAccessConfirmation()"
      >
        <template v-if="accessConfirmation">
          <p class="admin-users-confirm-person">
            {{ accessConfirmation.snapshot.userName }}
          </p>
          <ul class="admin-users-permission-diff">
            <li
              v-for="right in accessConfirmation.step.rights"
              :key="right.code"
            >
              <strong>{{ right.label }}</strong><span>{{ accessConfirmation.step.kind === 'grant' ? 'Запрещено → Разрешено' : 'Разрешено → Запрещено' }}</span>
            </li>
          </ul>
          <AdminCheckbox
            v-model="accessConfirmed"
            label="Подтверждаю"
            :disabled="saving"
            class="admin-users-confirm-check"
          />
          <div class="admin-users-confirm-actions">
            <button
              class="admin-users-action admin-users-action--secondary"
              type="button"
              :disabled="saving"
              @click="closeAccessConfirmation"
            >
              Отмена
            </button><button
              class="admin-users-action"
              type="button"
              :disabled="saving || !accessConfirmed"
              @click="saveAccess"
            >
              {{ accessConfirmation.step.kind === 'grant' ? 'Применить права' : accessConfirmation.steps.length === 1 ? 'Отозвать права' : 'Подтвердить отзыв' }}
            </button>
          </div>
        </template>
      </MobileToolsSheet>
    </section>
    <section
      v-else-if="(view === 'recovery' || view === 'recovery-issued') && selectedUser && canCredentials"
      class="admin-users-form"
      aria-label="Восстановление доступа пользователя"
    >
      <div class="admin-users-summary">
        <strong>{{ selectedUser.fullName }}</strong>
      </div>
      <RecoveryLinkPanel
        :key="selectedUser.id"
        :client="client"
        :user-id="selectedUser.id"
        :disabled="saving"
        embedded
        @issued="view = $event ? 'recovery-issued' : 'recovery'"
        @done="back"
        @owner-error="ownerError"
      />
    </section>
  </main>
</template>
