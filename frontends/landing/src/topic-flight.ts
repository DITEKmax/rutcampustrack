const flightKey = 'rct-presentation-topic-flight'

interface TopicFlight {
  path: string
  expiresAt: number
  left: number
  top: number
  width: number
  height: number
}

export function saveTopicFlight(path: string, { left, top, width, height }: DOMRect) {
  try {
    const flight: TopicFlight = { path, left, top, width, height, expiresAt: Date.now() + 2500 }
    sessionStorage.setItem(flightKey, JSON.stringify(flight))
  } catch {
    // The destination still opens normally when storage is disabled.
  }
}

export function takeTopicFlight() {
  try {
    const raw = sessionStorage.getItem(flightKey)
    if (!raw) return
    sessionStorage.removeItem(flightKey)
    const flight: Partial<TopicFlight> = JSON.parse(raw)
    const { path, expiresAt, left, top, width, height } = flight
    if (path !== location.pathname || location.hash ||
        typeof expiresAt !== 'number' || expiresAt < Date.now() ||
        typeof left !== 'number' || !Number.isFinite(left) ||
        typeof top !== 'number' || !Number.isFinite(top) ||
        typeof width !== 'number' || width < 1 || width > innerWidth ||
        typeof height !== 'number' || height < 1 || height > innerHeight) return
    return { left, top, width, height }
  } catch {
    return
  }
}
