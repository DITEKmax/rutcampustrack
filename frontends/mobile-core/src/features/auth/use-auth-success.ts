import { onBeforeUnmount, ref } from 'vue'

/** Presentation only: authority and role owners never wait for this timer. */
export function useAuthSuccess() {
  const visible = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined
  function clear(): void {
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
    visible.value = false
  }
  function show(): void {
    clear()
    visible.value = true
    timer = setTimeout(clear, 550)
  }
  onBeforeUnmount(clear)
  return { visible, show, clear }
}
