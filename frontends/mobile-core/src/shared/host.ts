export type MobileBackOwner = 'browser' | 'product' | 'host' | 'none'

export type MobilePrimaryActionOwner = 'product' | 'host' | 'none'

export interface MobileHostAction {
  label: string
  disabled?: boolean
  onInvoke: () => void | Promise<void>
}

/**
 * Platform boundary for mobile chrome. The core knows only callbacks and
 * ownership; PWA and Telegram adapters map their native APIs at the edge.
 */
export interface MobileHostAdapter {
  readonly push?: MobilePushPort
  readonly backOwner: MobileBackOwner
  readonly primaryActionOwner?: MobilePrimaryActionOwner
  subscribeBack?: (listener: () => void) => () => void
  setBackVisible?: (visible: boolean) => void
  /** Temporarily suppresses host Back while an app-level overlay is active. */
  suspendBack?: () => () => void
  subscribeKeyboard?: (listener: (visible: boolean) => void) => () => void
  setPrimaryAction?: (action: MobileHostAction | null) => void
}

export interface MobilePushState {
  status: 'unsupported' | 'signed-out' | 'off' | 'enabled' | 'denied' | 'busy' | 'error'
  message: string
}

/** Optional PWA capability. Telegram keeps its native notification channel. */
export interface MobilePushPort {
  snapshot(): MobilePushState
  subscribe(listener: (state: MobilePushState) => void): () => void
  enable(): Promise<void>
  disable(): Promise<void>
}

