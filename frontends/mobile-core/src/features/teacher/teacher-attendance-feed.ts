import type { TeacherDayLesson, TeacherJournalQuery, TeacherJournalResponse } from './teacher-client'

export interface TeacherAttendanceFeedState {
  readonly lessons: readonly TeacherDayLesson[]
  readonly nextPage: number
  readonly loading: boolean
  readonly hasMore: boolean
  readonly error: unknown | null
}

/** One sequential cursor per filter generation. A late page never owns the new feed. */
export class TeacherAttendanceFeed {
  private generation = 0
  private query: TeacherJournalQuery | null = null
  private reader: ((query: TeacherJournalQuery) => Promise<TeacherJournalResponse>) | null = null
  private value: TeacherAttendanceFeedState = this.empty()

  constructor(private readonly publish: (state: TeacherAttendanceFeedState) => void) {}

  get state(): TeacherAttendanceFeedState { return this.value }

  reset(query: TeacherJournalQuery | null, reader: ((query: TeacherJournalQuery) => Promise<TeacherJournalResponse>) | null): void {
    this.generation += 1
    this.query = query
    this.reader = reader
    this.update({ ...this.empty(), hasMore: Boolean(query && reader) })
  }

  async loadNext(): Promise<void> {
    if (!this.query || !this.reader || this.value.loading || !this.value.hasMore) return
    const generation = this.generation
    const page = this.value.nextPage
    const query = this.query
    const reader = this.reader
    this.update({ ...this.value, loading: true, error: null })
    try {
      const response = await reader({ ...query, page, pageSize: 100 })
      if (generation !== this.generation) return
      const merged = new Map(this.value.lessons.map(lesson => [lesson.id, lesson]))
      for (const lesson of response.lessons) merged.set(lesson.id, lesson)
      this.update({ lessons: [...merged.values()], nextPage: page + 1, hasMore: response.hasMore, loading: false, error: null })
    } catch (error) {
      if (generation !== this.generation) return
      this.update({ ...this.value, loading: false, error })
    }
  }

  private empty(): TeacherAttendanceFeedState { return { lessons: [], nextPage: 0, loading: false, hasMore: false, error: null } }
  private update(state: TeacherAttendanceFeedState): void { this.value = state; this.publish(state) }
}
