import { shallowRef } from 'vue'
import type { ProfileStateView } from '../../features/profile/profile-state'

export const EMPTY_PROFILE_VIEW: ProfileStateView = {
  snapshot: null,
  snapshotStatus: 'idle',
  snapshotError: null,
  sessions: [],
  sessionsNextCursor: null,
  sessionsStatus: 'idle',
  sessionsError: null,
  history: [],
  historyNextCursor: null,
  historyStatus: 'idle',
  historyError: null,
  error: null,
  mutationBusy: null,
}

/**
 * ProfileState owns one plain view object. Replace it with a fresh shallow-ref
 * value whenever the owner observes a state transition so Vue render effects
 * do not retain the raw object reference.
 */
export function createProfileViewPublication(
  readView: () => ProfileStateView | null,
  isDisposed: () => boolean = () => false,
) {
  const view = shallowRef<ProfileStateView>(EMPTY_PROFILE_VIEW)

  function publish(): void {
    if (isDisposed()) return
    const source = readView()
    if (!source) {
      view.value = EMPTY_PROFILE_VIEW
      return
    }
    view.value = {
      snapshot: source.snapshot,
      snapshotStatus: source.snapshotStatus,
      snapshotError: source.snapshotError,
      sessions: [...source.sessions],
      sessionsNextCursor: source.sessionsNextCursor,
      sessionsStatus: source.sessionsStatus,
      sessionsError: source.sessionsError,
      history: [...source.history],
      historyNextCursor: source.historyNextCursor,
      historyStatus: source.historyStatus,
      historyError: source.historyError,
      error: source.error,
      mutationBusy: source.mutationBusy,
    }
  }

  return { view, publish }
}
