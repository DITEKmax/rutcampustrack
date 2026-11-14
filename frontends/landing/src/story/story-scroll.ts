const limit = (value: number) => Math.max(0, Math.min(1, value))
const ease = (value: number) => value * value * (3 - 2 * value)
const range = (value: number, start: number, end: number) => limit((value - start) / (end - start))

/** Updates both viewport edges and the existing story scenes in the landing's scroll frame. */
export function createLandingScrollEffects(root: HTMLElement, header: HTMLElement | null) {
  const scenes = [...root.querySelectorAll<HTMLElement>('[data-story-scroll]')].map((node) => ({
    node,
    progress: '',
  }))
  const panels = [...root.querySelectorAll<HTMLElement>('[data-landing-reveal]')].map((node) => ({
    node,
    hero: node.dataset.landingReveal === 'hero',
    opacity: 1,
    offset: 0,
  }))
  const descent = root.querySelector<HTMLElement>('[data-descent]')
  const descentStage = descent?.querySelector<HTMLElement>('[data-descent-stage]')
  const backendHeading = root.querySelector<HTMLElement>('#inside .rct-story__heading')
  let previousDescent = ''
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
  return {
    render() {
      const height = window.innerHeight
      const top = header?.getBoundingClientRect().bottom ?? 0
      const band = Math.max(34, Math.min(112, (height - top) * 0.14))
      const reduced = motion.matches
      const descentProgress =
        descent && descentStage && !reduced
          ? limit(
              (top - descent.getBoundingClientRect().top) /
                Math.max(1, descent.offsetHeight - descentStage.offsetHeight),
            )
          : 0
      const backendTop = backendHeading?.getBoundingClientRect().top ?? height
      const opening = ease(range(descentProgress, 0.025, 0.42))
      const approach = ease(range(descentProgress, 0.25, 0.54))
      const fall = ease(range(descentProgress, 0.44, 0.99))
      const travel = 0.19 * approach + 0.81 * fall
      const dissolve = ease(
        Math.min(range(descentProgress, 0.77, 1), range(height - backendTop, 0, height * 0.34)),
      )
      const progress = scenes.map((scene) => {
        if (reduced) return 1
        const rect = scene.node.getBoundingClientRect()
        const start = height * 0.75
        const end = Math.min(height * 0.15, start - rect.height)
        return limit((start - rect.top) / (start - end))
      })
      const reveals = panels.map((panel) => {
        if (
          reduced ||
          panel.node.matches(':focus-within') ||
          (panel.hero && panel.node.closest('[data-hero-entering]'))
        )
          return { opacity: 1, offset: 0 }
        const rect = panel.node.getBoundingClientRect()
        const entering = panel.hero
          ? 1
          : limit((height - rect.top + panel.offset) / band)
        const leaving = limit((rect.bottom - panel.offset - top) / (band * 0.76))
        const amount = ease(Math.min(entering, leaving))
        return {
          opacity: Math.round((0.3 + amount * 0.7) * 1000) / 1000,
          offset: Math.round((1 - amount) * (entering < leaving ? 12 : -8) * 100) / 100,
        }
      })
      if (descent) {
        const values = [descentProgress, opening, travel, fall, dissolve].map((value) =>
          value.toFixed(4),
        )
        const state = reduced ? '' : values.join('/')
        if (state !== previousDescent) {
          previousDescent = state
          const properties = ['progress', 'opening', 'travel', 'pitch', 'dissolve']
          for (const [index, name] of properties.entries()) {
            if (reduced) descent.style.removeProperty('--descent-' + name)
            else descent.style.setProperty('--descent-' + name, values[index] ?? '0')
          }
        }
      }
      scenes.forEach((scene, index) => {
        const next = (progress[index] ?? 1).toFixed(3)
        if (next === scene.progress) return
        scene.progress = next
        scene.node.style.setProperty('--story-progress', next)
      })
      panels.forEach((panel, index) => {
        const next = reveals[index]
        if (!next || (panel.opacity === next.opacity && panel.offset === next.offset)) return
        panel.opacity = next.opacity
        panel.offset = next.offset
        panel.node.style.setProperty('--landing-edge-opacity', String(next.opacity))
        panel.node.style.setProperty('--landing-edge-offset', `${next.offset}px`)
      })
    },
    motion,
  }
}
