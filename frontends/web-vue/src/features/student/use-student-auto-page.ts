import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/** The page owns its query/revision; this only observes the next visible edge. */
export function useStudentAutoPage(enabled: Ref<boolean>, append: () => Promise<void>) {
  const edge = ref<HTMLElement | null>(null), supported = ref(true)
  let observer: IntersectionObserver | null = null
  let alive = true
  onMounted(() => {
    supported.value = 'IntersectionObserver' in window
    if (!supported.value) return
    observer = new IntersectionObserver(entries => {
      if (alive && enabled.value && entries.some(entry => entry.isIntersecting)) void append()
    })
    if (edge.value) observer.observe(edge.value)
  })
  watch([edge, enabled], ([element]) => {
    observer?.disconnect()
    if (element && enabled.value) observer?.observe(element)
  }, { flush: 'post' })
  onBeforeUnmount(() => { alive = false; observer?.disconnect() })
  return { edge, supported }
}
