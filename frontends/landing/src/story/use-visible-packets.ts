import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export function useVisiblePackets<T extends Element>(target: Ref<T | null>) {
  const moving = ref(false)
  let visible = false
  let preference: MediaQueryList | undefined
  let observer: IntersectionObserver | undefined

  function update() {
    moving.value = visible && preference?.matches === false
  }

  onMounted(() => {
    if (!target.value || !('IntersectionObserver' in window)) return
    preference = window.matchMedia('(prefers-reduced-motion: reduce)')
    preference.addEventListener('change', update)
    observer = new IntersectionObserver(([entry]) => {
      visible = entry?.isIntersecting ?? false
      update()
    })
    observer.observe(target.value)
  })
  onBeforeUnmount(() => {
    preference?.removeEventListener('change', update)
    observer?.disconnect()
  })
  return moving
}
