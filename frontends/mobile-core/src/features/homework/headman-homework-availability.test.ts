import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { watchHeadmanHomeworkAvailability } from './headman-homework-availability'
import { readPublishedHomeworks } from './headman-published-homework'
import type { HeadmanManagedHomework } from './headman-homework-client'
function item(id: number, archived: boolean): HeadmanManagedHomework {
  return {
    id,
    archived,
    title: 'Assignment',
    bindingId: id,
    requestKey: null,
    description: null,
    link: null,
    subjectId: 1,
    groupId: 2,
    semesterId: 3,
    publishedBy: 4,
    lessonDate: '2026-09-02',
    lessonNumber: null,
    bindingMode: 'DATE',
    revision: 1,
  }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((finish) => {
    resolve = finish
  })
  return { promise, resolve }
}
describe('homework read availability across connectivity cycles', () => {
  it.each(['active', 'archive'] as const)(
    'reloads the full published day after offline interrupts the %s GET',
    async (phase) => {
      const offline = ref(false)
      let generation = 0
      let items: readonly HeadmanManagedHomework[] = []
      const interrupted = deferred<readonly HeadmanManagedHomework[]>()
      const started = deferred<void>()
      let held = false
      let contextReloads = 0
      const reads: Promise<void>[] = []
      const api = {
        listHomeworks: async (_group: number, _semester: number, archived?: boolean) => {
          if (!held && Boolean(archived) === (phase === 'archive')) {
            held = true
            started.resolve()
            return interrupted.promise
          }
          return [item(archived ? 2 : 1, Boolean(archived))]
        },
      }
      const read = async () => {
        const run = ++generation
        items = []
        const current = () => run === generation && !offline.value
        const result = await readPublishedHomeworks(api, 2, 3, current)
        if (current() && result) items = result
      }
      const stop = watchHeadmanHomeworkAvailability({
        offline: () => offline.value,
        hasContext: () => true,
        mutationLocked: () => false,
        invalidateReads: () => {
          generation += 1
        },
        loadContext: () => {
          contextReloads += 1
        },
        refreshReads: () => {
          reads.push(read())
        },
      })
      const oldRead = read()
      await started.promise
      offline.value = true
      interrupted.resolve([item(99, phase === 'archive')])
      await oldRead
      expect(items).toEqual([])
      offline.value = false
      await reads[0]
      expect(items.map((entry) => [entry.id, entry.archived])).toEqual([
        [1, false],
        [2, true],
      ])
      expect(contextReloads).toBe(0)
      stop()
    },
  )
  it('resumes reads while retaining an unknown publication key, receipt and draft', () => {
    const offline = ref(false)
    const intent = Object.freeze({
      requestKey: 'original-key',
      receipt: Object.freeze({ homeworkId: '101', bindingId: '601' }),
      everUncertain: true,
    })
    const draft = Object.freeze({ title: 'Kept title', description: 'Kept text' })
    let currentIntent: typeof intent | null = intent
    let currentDraft: typeof draft | null = draft
    let refreshes = 0
    const stop = watchHeadmanHomeworkAvailability({
      offline: () => offline.value,
      hasContext: () => true,
      mutationLocked: () => currentIntent !== null,
      invalidateReads: () => undefined,
      loadContext: () => {
        currentIntent = null
        currentDraft = null
      },
      refreshReads: () => {
        refreshes += 1
      },
    })
    offline.value = true
    offline.value = false
    expect(refreshes).toBe(1)
    expect(currentIntent).toBe(intent)
    expect(currentDraft).toBe(draft)
    stop()
  })
  it('loads the first offline context on return and never resets a contextless uncertain mutation', () => {
    const offline = ref(true)
    let locked = false
    let loads = 0
    const stop = watchHeadmanHomeworkAvailability({
      offline: () => offline.value,
      hasContext: () => false,
      mutationLocked: () => locked,
      invalidateReads: () => undefined,
      loadContext: () => {
        loads += 1
      },
      refreshReads: () => {
        throw new Error('No context')
      },
    })
    offline.value = false
    expect(loads).toBe(1)
    locked = true
    offline.value = true
    offline.value = false
    expect(loads).toBe(1)
    stop()
  })
})
