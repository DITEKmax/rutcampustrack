import {
  HeadmanJournalApiError,
  type HeadmanJournalApiGenerationOwner,
  type HeadmanJournalApiOptions,
} from './headman-journal-client'
import {
  normalizeLessonActionsPage,
  type HeadmanLessonActionsPage,
} from './headman-journal-actions'
import { StaleSessionGenerationError } from '../../shared/session-owner'

export class HeadmanLessonActionsApi {
  private readonly fetcher: typeof fetch
  constructor(private readonly options: HeadmanJournalApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }
  async getLessonActions(
    lessonId: number,
    cursor: string | null = null,
    limit = 50,
    retried = false,
  ): Promise<HeadmanLessonActionsPage> {
    if (
      !Number.isSafeInteger(lessonId) ||
      lessonId < 1 ||
      !Number.isInteger(limit) ||
      limit < 1 ||
      limit > 100
    )
      throw new RangeError('Некорректный запрос истории пары.')
    this.options.assertCurrent?.()
    const query = new URLSearchParams({ limit: String(limit) })
    if (cursor !== null) query.set('cursor', cursor)
    const token = this.options.accessToken()
    const response = await this.fetcher(
      `/api/attendance/reports/lesson/${lessonId}/actions?${query}`,
      {
        credentials: 'include',
        headers: {
          Accept: 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      },
    )
    this.options.assertCurrent?.()
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.options.assertCurrent?.()
      return this.getLessonActions(lessonId, cursor, limit, true)
    }
    const value: unknown = await response.json().catch(() => null)
    this.options.assertCurrent?.()
    if (!response.ok) throw new HeadmanJournalApiError(response, value)
    return normalizeLessonActionsPage(value)
  }
}
export function createGenerationBoundHeadmanLessonActionsApi(
  owner: HeadmanJournalApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanLessonActionsApi {
  const generation = owner.currentGeneration()
  const assertCurrent = () => {
    if (owner.currentGeneration() !== generation) throw new StaleSessionGenerationError()
  }
  return new HeadmanLessonActionsApi({
    assertCurrent,
    accessToken: () => {
      assertCurrent()
      return owner.accessTokenFor(generation)
    },
    onUnauthorized: async () => {
      assertCurrent()
      await owner.refreshFor(generation)
      assertCurrent()
    },
    ...(fetcher ? { fetcher } : {}),
  })
}
