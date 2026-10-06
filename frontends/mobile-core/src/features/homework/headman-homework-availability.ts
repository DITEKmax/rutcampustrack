import { watch, type WatchStopHandle } from 'vue'
/** Connectivity changes refresh reads without resetting the owner or mutation draft. */
export function watchHeadmanHomeworkAvailability(options: {
  offline: () => boolean
  hasContext: () => boolean
  mutationLocked: () => boolean
  invalidateReads: () => void
  loadContext: () => void
  refreshReads: () => void
}): WatchStopHandle {
  return watch(options.offline, (offline) => {
    if (offline) options.invalidateReads()
    else if (options.hasContext()) options.refreshReads()
    else if (!options.mutationLocked()) options.loadContext()
  }, { flush: 'sync' })
}
