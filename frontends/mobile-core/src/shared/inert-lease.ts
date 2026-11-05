interface InertOwners {
  count: number
  previous: boolean
}
const owners = new WeakMap<HTMLElement, InertOwners>()

/** Independent overlays release only their own hold on the application root. */
export function acquireInert(target: HTMLElement | null): () => void {
  if (!target) return () => undefined
  let state = owners.get(target)
  if (!state) {
    state = { count: 0, previous: target.inert }
    owners.set(target, state)
  }
  state.count += 1
  target.inert = true
  let released = false
  return () => {
    if (released) return
    released = true
    state.count -= 1
    if (state.count !== 0) return
    target.inert = state.previous
    owners.delete(target)
  }
}
