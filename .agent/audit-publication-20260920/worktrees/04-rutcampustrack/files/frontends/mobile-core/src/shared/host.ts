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
  readonly backOwner: MobileBackOwner
  readonly primaryActionOwner?: MobilePrimaryActionOwner
  subscribeBack?: (listener: () => void) => () => void
  setBackVisible?: (visible: boolean) => void
  subscribeKeyboard?: (listener: (visible: boolean) => void) => () => void
  setPrimaryAction?: (action: MobileHostAction | null) => void
}

