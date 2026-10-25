import {
  computed,
  inject,
  onScopeDispose,
  provide,
  toValue,
  watch,
  type InjectionKey,
  type MaybeRefOrGetter,
  type Ref,
} from 'vue'
const projectionRevision: InjectionKey<Readonly<Ref<number>>> = Symbol('projection-revision')
export function provideProjectionRevision(revision: () => number): void {
  provide(projectionRevision, computed(revision))
}
/** Coalesce hidden read invalidations without replacing a mounted owner or replaying writes. */
export function useProjectionRefresh(
  load: () => void | Promise<unknown>,
  options: { revision?: () => number; blocked?: MaybeRefOrGetter<boolean> } = {},
): void {
  const inherited = inject(projectionRevision, null)
  const revision = options.revision ?? (() => inherited?.value ?? 0)
  const blocked = computed(() => Boolean(toValue(options.blocked)))
  let pending = false,
    running = false,
    active = true
  async function flush(): Promise<void> {
    if (!active || !pending || running || blocked.value) return
    pending = false
    running = true
    try {
      await load()
    } catch {
      /* The feature owns read errors and its retry UI. */
    } finally {
      running = false
      if (active && pending && !blocked.value) void flush()
    }
  }
  watch(revision, () => {
    pending = true
    void flush()
  })
  watch(blocked, () => {
    if (!blocked.value) void flush()
  })
  onScopeDispose(() => {
    active = false
  })
}
