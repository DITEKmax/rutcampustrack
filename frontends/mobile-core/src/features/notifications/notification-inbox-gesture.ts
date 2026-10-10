/** Pointer hold state only; no read state, navigation or persistence. */
export class NotificationInboxHold {
  private pointer: { id: number; x: number; y: number } | null = null
  private timer: ReturnType<typeof setTimeout> | null = null
  private suppressClick = false
  constructor(private readonly showMenu: () => void, private readonly delay = 550) {}
  start(id: number, x: number, y: number): void {
    this.cancel()
    this.suppressClick = false
    this.pointer = { id, x, y }
    this.timer = setTimeout(() => {
      this.timer = null
      if (!this.pointer) return
      this.suppressClick = true
      this.showMenu()
    }, this.delay)
  }
  move(id: number, x: number, y: number): void {
    if (!this.pointer || this.pointer.id !== id) return
    if (Math.hypot(x - this.pointer.x, y - this.pointer.y) > 10) this.cancel()
  }
  end(id: number): void {
    if (this.pointer?.id === id) this.cancel()
  }
  cancel(): void {
    if (this.timer !== null) clearTimeout(this.timer)
    this.timer = null
    this.pointer = null
  }
  consumeClick(pointerClick: boolean): boolean {
    if (!pointerClick || !this.suppressClick) return false
    this.suppressClick = false
    return true
  }
}
