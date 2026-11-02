import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { createAuthClient, AuthRequestError } from '../../../../pwa-vue/src/auth-client'
import { ProfileState } from '../../../../mobile-core/src/features/profile/profile-state'
import { ProfileRequestError } from '../../../../mobile-core/src/features/profile/profile-types'
import { createWebTransport, WebAuthorityChangedError } from '../../app/api'
import { useWebLiveRefresh } from '../../app/live'
import { reauthenticationReceipt } from '../../auth/reauthentication-receipt'
import { webSession } from '../../auth/session'
import { webAssistantAccess } from '../../app/assistant'
import { useOwnProfile } from './own-profile'
export function useWebProfile(dirty: () => boolean) {
  const transport = createWebTransport()
  const owner = webSession.snapshot.value!
  const client = createAuthClient({
    fetcher: transport.fetcher,
    accessToken: transport.accessToken,
  })
  const ownProfile = useOwnProfile()
  const { own, ownError } = ownProfile
  const notice = ref('')
  const success = shallowRef<{ message: string } | null>(null)
  watch(webSession.identity, () => { success.value = null }, { flush: 'sync' })
  function showSuccess(message: string) {
    transport.assertCurrent()
    notice.value = ''
    success.value = { message }
  }
  const uncertain = ref(false)
  const avatarBusy = ref(false)
  const historyBusy = ref(false)
  let historyTail: Promise<unknown> = Promise.resolve()
  let historyReads = 0
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
            if (!all.items.some((item) => item.sessionId === targetSessionId)) {
              uncertain.value = false
              showSuccess('Вход завершён.')
              return
            }
          } catch {
            /* Keep the unknown result if readback is unavailable. */
          }
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
  function loadHistory(more = false) {
    historyReads++
    historyBusy.value = true
    const task = historyTail
      .catch(() => undefined)
      .then(() => {
        transport.assertCurrent()
        const cursor = state.view.historyNextCursor
        if (more && !cursor) return
        return state.loadHistory(more ? { cursor: cursor! } : undefined)
      })
    historyTail = task.catch(() => undefined)
    return task.finally(() => {
      historyReads--
      historyBusy.value = historyReads > 0
    })
  }
  const loadOwn = () => ownProfile.load()
  async function load() {
    try {
      await state.loadSnapshot()
    } catch {
      return
    }
    await Promise.allSettled([
      state.loadSessions(),
      loadHistory(),
      loadOwn(),
      owner.activeRole === 'STUDENT' ? webAssistantAccess.load(true) : Promise.resolve(),
    ])
  }
  async function saveAvatar(avatarId: string) {
    if (avatarBusy.value || uncertain.value) return
    avatarBusy.value = true
    ownError.value = ''
    ownProfile.invalidateReads()
    try {
      const response = await transport.fetcher('/api/academic/users/me/avatar', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ avatarId }),
      })
      if (!response.ok) {
        ownError.value =
          response.status === 403
            ? 'Изменение аватара недоступно для текущего контекста.'
            : 'Аватар не сохранён. Проверь данные и повтори действие.'
        return
      }
      await ownProfile.load(true)
      if (own.value?.avatarId === (avatarId || null) || (!own.value?.avatarId && !avatarId))
        showSuccess('Аватар сохранён.')
      else ownError.value = 'Не удалось подтвердить выбранный аватар. Обнови профиль.'
    } catch (cause) {
      if (!(cause instanceof WebAuthorityChangedError)) {
        await ownProfile.load(true)
        if (own.value?.avatarId === (avatarId || null)) showSuccess('Аватар сохранён.')
        else
          ownError.value = 'Результат сохранения неизвестен. Проверь профиль перед новой попыткой.'
      }
    } finally {
      avatarBusy.value = false
    }
  }
  const busy = computed(() => state.view.mutationBusy !== null || avatarBusy.value)
  useWebLiveRefresh(load, {
    blocked: computed(() => dirty() || busy.value || uncertain.value || historyBusy.value),
  })
  void load()
  return {
    state,
    own,
    ownError,
    notice,
    success,
    uncertain,
    avatarBusy,
    busy,
    historyBusy,
    loadHistory,
    load,
    loadOwn,
    saveAvatar,
  }
}
