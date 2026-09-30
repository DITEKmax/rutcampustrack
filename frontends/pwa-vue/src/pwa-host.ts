import type { MobileHostAdapter, MobilePushPort } from '@rct/mobile-core'

/** Browser viewport adapter. It owns only its listeners; the product owns navigation. */
export class PwaHostAdapter implements MobileHostAdapter {
  readonly push?: MobilePushPort
  constructor(push?: MobilePushPort) { if (push) this.push = push }
  readonly backOwner = 'browser' as const
  readonly primaryActionOwner = 'none' as const

  subscribeKeyboard(listener: (visible: boolean) => void): () => void {
    const viewport = window.visualViewport
    const notify = (): void => {
      const height = viewport?.height ?? window.innerHeight
      listener(height < window.innerHeight - 120)
    }
    window.addEventListener('resize', notify)
    viewport?.addEventListener('resize', notify)
    notify()
    return () => {
      window.removeEventListener('resize', notify)
      viewport?.removeEventListener('resize', notify)
    }
  }
}
