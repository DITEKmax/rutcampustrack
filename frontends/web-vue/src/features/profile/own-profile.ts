import { computed, ref, shallowRef, watch } from 'vue'
import type { components } from '../../../../mobile-core/src/api/generated/academic.types'
import { ownProfileTelegramId } from '../../../../mobile-core/src/features/profile/profile-types'
import { createWebTransport, WebAuthorityChangedError } from '../../app/api'
import { message, webSession } from '../../auth/session'

type OwnDto = Pick<
  components['schemas']['EntityModelUserResponse'],
  'id' | 'firstName' | 'lastName' | 'middleName' | 'telegramId' | 'avatarId'
>
type OwnProfile = { [K in keyof OwnDto]-?: OwnDto[K] | undefined }

/** One owner for the existing /users/me response, shared by settings and the header. */
function createOwnProfile() {
  const transport = createWebTransport()
  const userId = webSession.snapshot.value!.userId
  const own = shallowRef<OwnProfile | null>(null)
  const ownError = ref('')
  let active = true
  let revision = 0
  let flight: { revision: number; promise: Promise<void> } | null = null

  function invalidateReads() {
    revision++
    flight = null
  }

  function load(force = false): Promise<void> {
    if (!active) return Promise.resolve()
    if (!force && flight?.revision === revision) return flight.promise
    const ticket = ++revision
    ownError.value = ''
    const promise = (async () => {
      try {
        const response = await transport.fetcher('/api/academic/users/me', { cache: 'no-store' })
        if (!response.ok) throw new Error('Не удалось загрузить профиль')
        const value: unknown = await response.json()
        transport.assertCurrent()
        ownProfileTelegramId(value, userId)
        const profile = value as OwnProfile
        if (
          [profile.firstName, profile.lastName].some((value) => typeof value !== 'string') ||
          (profile.avatarId != null && !/^avatar_[0-9]{2}$/.test(profile.avatarId))
        ) throw new Error('Некорректные данные профиля')
        if (active && ticket === revision) {
          own.value = {
            id: profile.id, firstName: profile.firstName, lastName: profile.lastName,
            middleName: profile.middleName, telegramId: profile.telegramId, avatarId: profile.avatarId,
          }
        }
      } catch (cause) {
        if (active && ticket === revision && !(cause instanceof WebAuthorityChangedError))
          ownError.value = message(cause)
      } finally {
        if (flight?.revision === ticket) flight = null
      }
    })()
    flight = { revision: ticket, promise }
    return promise
  }

  function dispose() {
    active = false
    invalidateReads()
    own.value = null
    ownError.value = ''
  }
  return { own, ownError, load, invalidateReads, dispose }
}

const current = shallowRef<ReturnType<typeof createOwnProfile> | null>(null)
export const webOwnProfile = computed(() => current.value)
watch(webSession.identity, (identity) => {
  current.value?.dispose()
  current.value = identity ? createOwnProfile() : null
  void current.value?.load()
}, { immediate: true, flush: 'sync' })

export function useOwnProfile() {
  if (!current.value) throw new WebAuthorityChangedError()
  return current.value
}
