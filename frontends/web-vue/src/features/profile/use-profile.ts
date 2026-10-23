import { computed, reactive, ref, shallowRef } from 'vue'
import { createAuthClient, AuthRequestError } from '../../../../pwa-vue/src/auth-client'
import { ProfileState } from '../../../../mobile-core/src/features/profile/profile-state'
import {
  ownProfileTelegramId,
  ProfileRequestError,
} from '../../../../mobile-core/src/features/profile/profile-types'
import type { components } from '../../../../mobile-core/src/api/generated/academic.types'
import { createWebTransport, WebAuthorityChangedError } from '../../app/api'
import { useWebLiveRefresh } from '../../app/live'
import { reauthenticationReceipt } from '../../auth/reauthentication-receipt'
import { webSession, message } from '../../auth/session'
import { webAssistantAccess } from '../../app/assistant'
type OwnDto = Pick<
  components['schemas']['EntityModelUserResponse'],
  'id' | 'firstName' | 'lastName' | 'middleName' | 'telegramId' | 'avatarId'
>
type OwnProfile = { [K in keyof OwnDto]-?: OwnDto[K] | undefined }
export function useWebProfile(dirty: () => boolean) {
  const transport = createWebTransport()
  const owner = webSession.snapshot.value!
  const client = createAuthClient({
    fetcher: transport.fetcher,
    accessToken: transport.accessToken,
  })
  const own = shallowRef<OwnProfile | null>(null)
  const ownError = ref('')
  const notice = ref('')
  const uncertain = ref(false)
  const avatarBusy = ref(false)
  let readRevision = 0
  async function read<T>(operation: () => Promise<T>): Promise<T> {
    try {
      const data = await operation()
      transport.assertCurrent()
      return data
    } catch (cause) {
      if (cause instanceof AuthRequestError && cause.status === 401) {
        await transport.onUnauthorized()
        const data = await operation()
        transport.assertCurrent()
        return data
      }
      throw cause
    }
  }
  async function mutate(
    operation: () => Promise<void>,
    kind: 'password' | 'logout-all' | 'session',
    targetSessionId?: string,
  ): Promise<void> {
    if (uncertain.value) throw new ProfileRequestError('UNKNOWN', notice.value)
    try {
      await operation()
      transport.assertCurrent()
    } catch (cause) {
      if (cause instanceof WebAuthorityChangedError) throw cause
      if (
        !(cause instanceof AuthRequestError) ||
        cause.status === undefined ||
        cause.status >= 500
      ) {
        uncertain.value = true
        notice.value = 'Результат операции неизвестен. Не повторяй действие до проверки состояния.'
        try {
          await client.getSnapshot()
          transport.assertCurrent()
        } catch (reconcile) {
          if (reconcile instanceof AuthRequestError && reconcile.status === 401) {
            return
          }
        }
        if (kind === 'session' && targetSessionId) {
          try {
            const all = await state.loadAllSessions()
            transport.assertCurrent()
            if (!all.items.some(item => item.sessionId === targetSessionId)) {
              uncertain.value = false
              notice.value = 'Вход завершён.'
              return
            }
          } catch { /* Keep the unknown result if readback is unavailable. */ }
        }
        throw new ProfileRequestError('NETWORK', notice.value)
      }
      throw cause
    }
  }
  const state = reactive(
    new ProfileState({
      getSnapshot: () => read(() => client.getSnapshot()),
      selectRole: (input) => client.selectRole(input),
      listSessions: (input) => read(() => client.listSessions(input)),
      listHistory: (input) => read(() => client.listHistory(input)),
      changePassword: (input) => mutate(() => client.changePassword(input), 'password'),
      logoutAll: () => mutate(() => client.logoutAll(), 'logout-all'),
      terminateSession: (id) => mutate(() => client.terminateSession(id), 'session', id),
      onInvalidated: (reason) => {
        transport.assertCurrent()
        reauthenticationReceipt.show({
          kind: 'session-ended',
          targetLabel: owner.displayName,
          message: uncertain.value
            ? 'Результат операции неизвестен. Сессия завершена. Войди снова и проверь доступ; не повторяй изменение пароля вслепую.'
            : reason === 'password-changed'
              ? 'Пароль изменён. Войди с новым паролем.'
              : 'Вход завершён. Войди снова.',
        })
        webSession.invalidate()
      },
      onRefreshAlreadyRotated: () => transport.onUnauthorized(),
      isOnline: () => navigator.onLine,
    }),
  )
  async function loadOwn() {
    const revision = ++readRevision
    ownError.value = ''
    try {
      const response = await transport.fetcher('/api/academic/users/me', { cache: 'no-store' })
      if (!response.ok) throw new Error('Не удалось загрузить профиль')
      const value: unknown = await response.json()
      transport.assertCurrent()
      ownProfileTelegramId(value, owner.userId)
      const profile = value as OwnProfile
      if (
        [profile.firstName, profile.lastName].some((value) => typeof value !== 'string') ||
        (profile.avatarId != null && !/^avatar_[0-9]{2}$/.test(profile.avatarId))
      )
        throw new Error('Некорректные данные профиля')
      if (revision === readRevision)
        own.value = {
          id: profile.id,
          firstName: profile.firstName,
          lastName: profile.lastName,
          middleName: profile.middleName,
          telegramId: profile.telegramId,
          avatarId: profile.avatarId,
        }
    } catch (cause) {
      if (revision === readRevision && !(cause instanceof WebAuthorityChangedError))
        ownError.value = message(cause)
    }
  }
  async function load() {
    try {
      await state.loadSnapshot()
    } catch {
      return
    }
    await Promise.allSettled([
      state.loadSessions(),
      state.loadHistory(),
      loadOwn(),
      owner.activeRole === 'STUDENT' ? webAssistantAccess.load(true) : Promise.resolve(),
    ])
  }
  async function saveAvatar(avatarId: string) {
    if (avatarBusy.value || uncertain.value) return
    avatarBusy.value = true
    ownError.value = ''
    ++readRevision
    try {
      const response = await transport.fetcher('/api/academic/users/me/avatar', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ avatarId }),
      })
      if (!response.ok) { ownError.value = response.status === 403 ? 'Изменение аватара недоступно для текущего контекста.' : 'Аватар не сохранён. Проверь данные и повтори действие.'; return }
      await loadOwn()
      if (own.value?.avatarId === (avatarId || null) || (!own.value?.avatarId && !avatarId))
        notice.value = 'Аватар сохранён.'
      else ownError.value = 'Не удалось подтвердить выбранный аватар. Обнови профиль.'
    } catch (cause) {
      if (!(cause instanceof WebAuthorityChangedError)) {
        await loadOwn()
        if (own.value?.avatarId === (avatarId || null)) notice.value = 'Аватар сохранён.'
        else ownError.value = 'Результат сохранения неизвестен. Проверь профиль перед новой попыткой.'
      }
    } finally {
      avatarBusy.value = false
    }
  }
  const busy = computed(() => state.view.mutationBusy !== null || avatarBusy.value)
  useWebLiveRefresh(load, { blocked: computed(() => dirty() || busy.value || uncertain.value) })
  void load()
  return { state, own, ownError, notice, uncertain, avatarBusy, busy, load, loadOwn, saveAvatar }
}
