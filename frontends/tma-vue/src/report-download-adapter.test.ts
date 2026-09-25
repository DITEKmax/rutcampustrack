import { describe, expect, it, vi } from 'vitest'
import {
  createGenerationBoundReportDownloadClient,
  ReportDownloadClient,
  validateTicketRequest,
  type ReportDownloadTicket,
  type ReportDownloadTicketRequest,
  type ReportDownloadTicketIssuer,
} from '../../mobile-core/src/shared/report-download-client'
import {
  toHeadmanWeeklyReportRequest,
  type HeadmanWeeklyWeekOption,
} from '../../mobile-core/src/features/headman-journal/headman-journal-client'
import {
  headmanStatsResponseForCurrentQuery,
  headmanStatsQueryKey,
  hasHeadmanStatsResettableCriteria,
  resetHeadmanStatsCriteria,
  toHeadmanStatsReportRequest,
  toHeadmanStatsTrendReportRequest,
} from '../../mobile-core/src/features/headman-stats/headman-stats-client'
import {
  toTeacherJournalReportRequest,
  toTeacherStatsReportSelector,
} from '../../mobile-core/src/features/teacher/teacher-client'
import {
  buildTrustedHttpsDownloadUrl,
  createTelegramReportDownloadPort,
  validateSuggestedFilename,
  type TelegramReportDownloadHost,
} from './report-download-adapter'

const teacherJournalRequest = (): ReportDownloadTicketRequest => toTeacherJournalReportRequest({
  semesterId: 24,
  groupId: 8,
  subjectId: 3,
  lessonType: 'LECTURE',
  page: 2,
  pageSize: 50,
}, 'pdf')

const ticket = (suggestedFilename = 'teacher-journal.pdf'): ReportDownloadTicket => ({
  downloadPath: `/api/report-download/${'a'.repeat(43)}`,
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  suggestedFilename,
})

function fakeTicketIssuer(value = ticket()): ReportDownloadTicketIssuer {
  return {
    assertCurrent: () => undefined,
    issueTicket: async () => value,
  }
}

function fakeHost(
  accepted: boolean,
  supportsFileDownload = true,
): TelegramReportDownloadHost & { readonly params: ReturnType<typeof vi.fn> } {
  const params = vi.fn()
  return {
    params,
    supportsFileDownload: () => supportsFileDownload,
    requestFileDownload: (downloadParams, callback) => {
      params(downloadParams)
      callback(accepted)
      return true
    },
  }
}

describe('report ticket boundary', () => {
  it('posts only the typed selector with the current bearer and validates the ticket response', async () => {
    const request = teacherJournalRequest()
    const fetcher = vi.fn<typeof fetch>(async () => new Response(JSON.stringify(ticket()), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    const client = new ReportDownloadClient({
      accessToken: () => 'synthetic-bearer',
      assertCurrent: () => undefined,
      fetcher,
    })

    const issued = await client.issueTicket(request)
    const [path, init] = fetcher.mock.calls[0]!
    const body = JSON.parse(String(init?.body)) as Record<string, unknown>

    expect(path).toBe('/api/auth/report-download-tickets')
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer synthetic-bearer')
    expect(Object.keys(body).sort()).toEqual(['kind', 'teacherJournal'])
    expect(body).toEqual(request)
    expect(issued.downloadPath).toMatch(/^\/api\/report-download\/[A-Za-z0-9_-]{43}$/)
    expect(JSON.stringify(body)).not.toContain('synthetic-bearer')
  })

  it.each([
    [400, 'Не удалось подготовить отчёт: проверь выбранные параметры и формат.'],
    [401, 'Сессия истекла. Войди снова, чтобы скачать отчёт.'],
    [403, 'У тебя нет права скачать этот отчёт.'],
    [409, 'Отчёт стал недоступен. Обнови данные и выбери его снова.'],
    [429, 'Слишком много запросов на скачивание. Попробуй позже.'],
    [503, 'Сервис отчётов временно недоступен. Попробуй позже.'],
  ])('keeps ticket HTTP %i visible to the user', async (status, message) => {
    const client = new ReportDownloadClient({
      accessToken: () => 'synthetic-bearer',
      assertCurrent: () => undefined,
      fetcher: async () => new Response(null, { status }),
    })

    await expect(client.issueTicket(teacherJournalRequest())).rejects.toMatchObject({ status, message })
  })

  it('rejects selectors that add fields outside their kind', () => {
    const invalid = {
      kind: 'HEADMAN_STATS',
      headmanStats: { format: 'xlsx', groupId: 8 },
    } as unknown as ReportDownloadTicketRequest

    expect(() => validateTicketRequest(invalid)).toThrow(RangeError)
  })
})

describe('selected report parameter mapping', () => {
  it('uses the journal context and omits page-only selectors', () => {
    expect(teacherJournalRequest()).toEqual({
      kind: 'TEACHER_JOURNAL',
      teacherJournal: {
        semesterId: 24,
        groupId: 8,
        subjectId: 3,
        lessonTypes: ['LECTURE'],
        format: 'pdf',
      },
    })
  })

  it('uses the canonical teacher stats sort, filter, and lesson-type order', () => {
    const selector = toTeacherStatsReportSelector({
      semesterId: 24,
      scope: 'groups',
      groupId: null,
      subjectId: null,
      lessonTypes: ['LECTURE', 'LAB'],
      sorts: [
        { column: 'present', descending: false },
        { column: 'absent', descending: true },
      ],
      filters: [
        { column: 'groupName', contains: 'A&B' },
        { column: 'present', minPercent: 20, maxPercent: 70 },
        { column: 'lessonsCount', minValue: 1, maxValue: 8 },
      ],
    }, 'xlsx')

    expect(selector).toEqual({
      semesterId: 24,
      scope: 'groups',
      lessonTypes: ['LECTURE', 'LAB'],
      sorts: ['present', '-absent'],
      filters: ['groupName~A&B', 'present>=20', 'present<=70', 'lessonsCount>=1', 'lessonsCount<=8'],
      format: 'xlsx',
    })
    expect(Object.keys(selector)).not.toContain('groupId')
    expect(Object.keys(selector)).not.toContain('subjectId')
  })

  it('uses the current selector only for a sole current week and otherwise preserves the selected weeks', () => {
    const weeks: readonly HeadmanWeeklyWeekOption[] = [
      { weekOfSemester: 1, isoWeek: 36, label: 'Неделя 1', weekStart: '2026-08-31', weekEnd: '2026-09-06', current: false },
      { weekOfSemester: 2, isoWeek: 37, label: 'Неделя 2', weekStart: '2026-09-07', weekEnd: '2026-09-13', current: false },
      { weekOfSemester: 3, isoWeek: 38, label: 'Неделя 3', weekStart: '2026-09-14', weekEnd: '2026-09-20', current: true },
    ]

    expect(toHeadmanWeeklyReportRequest(weeks, ['2026-09-14'], { code: 'png', label: 'PNG', contentType: 'application/zip', extension: 'zip' }))
      .toEqual({ kind: 'HEADMAN_WEEKLY_CURRENT', headmanWeeklyCurrent: { weekStart: '2026-09-14', format: 'png' } })
    expect(toHeadmanWeeklyReportRequest(weeks, ['2026-09-14', '2026-09-07'], { code: 'pdf', label: 'PDF', contentType: 'application/pdf', extension: 'pdf' }))
      .toEqual({ kind: 'HEADMAN_WEEKLY_SELECTED', headmanWeeklySelected: { weekStarts: ['2026-09-07', '2026-09-14'], format: 'pdf' } })
  })

  it('keeps the frozen headman stats selector free of group, semester, and page fields', () => {
    const request: ReportDownloadTicketRequest = {
      kind: 'HEADMAN_STATS',
      headmanStats: {
        subjectId: 3,
        lessonTypes: ['LECTURE'],
        sorts: [{ field: 'present', descending: true }],
        filters: [{ field: 'displayName', contains: 'Иван', minimum: 10, maximum: 90 }],
        format: 'pdf',
      },
    }
    validateTicketRequest(request)
    expect(Object.keys(request.headmanStats)).toEqual(['subjectId', 'lessonTypes', 'sorts', 'filters', 'format'])
  })

  it('maps the current headman stats slice and strips absent filter values from the ticket DTO', () => {
    const request = toHeadmanStatsReportRequest({
      subjectId: 3,
      lessonTypes: ['LECTURE', 'LAB'],
      sorts: [{ field: 'presentPercent', descending: true }],
      filters: [
        { field: 'displayName', contains: 'Иван' },
        { field: 'presentPercent', minimum: 40, maximum: null },
      ],
    }, 'xlsx')

    expect(request).toEqual({
      kind: 'HEADMAN_STATS',
      headmanStats: {
        subjectId: 3,
        lessonTypes: ['LECTURE', 'LAB'],
        sorts: [{ field: 'presentPercent', descending: true }],
        filters: [
          { field: 'displayName', contains: 'Иван' },
          { field: 'presentPercent', minimum: 40 },
        ],
        format: 'xlsx',
      },
    })
    validateTicketRequest(request)
  })

  it('keeps stale stats slices hidden and allows reset when only a sort is active', () => {
    const groupQuery = { subjectId: null, lessonTypes: [], sorts: [], filters: [], page: 0, size: 50 }
    const subjectQuery = { ...groupQuery, subjectId: 3 }

    const groupKey = headmanStatsQueryKey(groupQuery)
    const subjectKey = headmanStatsQueryKey(subjectQuery)
    expect(subjectKey).not.toBe(groupKey)
    const loadedGroupSlice = { heading: 'Вся группа', rows: ['студент группы'] }
    expect(headmanStatsResponseForCurrentQuery(loadedGroupSlice, groupKey, subjectKey)).toBeNull()
    expect(headmanStatsResponseForCurrentQuery(loadedGroupSlice, null, subjectKey)).toBeNull()
    expect(headmanStatsResponseForCurrentQuery(loadedGroupSlice, subjectKey, subjectKey)).toBe(loadedGroupSlice)
    expect(hasHeadmanStatsResettableCriteria([{ field: 'presentPercent', descending: true }], false)).toBe(true)
    expect(resetHeadmanStatsCriteria()).toEqual({ sorts: [], filters: [] })
    expect(hasHeadmanStatsResettableCriteria([], false)).toBe(false)
  })
})

describe('Telegram download adapter', () => {
  it('accepts only the fixed ticket path on the app HTTPS origin', () => {
    const path = `/api/report-download/${'x'.repeat(43)}`
    expect(buildTrustedHttpsDownloadUrl('https://rutcampustrack.example', path))
      .toBe(`https://rutcampustrack.example${path}`)
    for (const invalid of [
      ['http://rutcampustrack.example', path],
      ['https://user:pass@rutcampustrack.example', path],
      ['https://rutcampustrack.example/app', path],
      ['https://rutcampustrack.example', 'https://evil.example/download'],
      ['https://rutcampustrack.example', `${path}?next=https://evil.example`],
      ['https://rutcampustrack.example', `${path}#fragment`],
    ]) {
      expect(() => buildTrustedHttpsDownloadUrl(invalid[0]!, invalid[1]!)).toThrow()
    }
  })

  it('rejects path-like, newline, and format-mismatched filenames', () => {
    const request = teacherJournalRequest()
    expect(validateSuggestedFilename('teacher-journal.pdf', request)).toBe('teacher-journal.pdf')
    for (const invalid of ['../teacher-journal.pdf', 'teacher-journal.pdf\r\n', 'teacher-journal.docx']) {
      expect(() => validateSuggestedFilename(invalid, request)).toThrow()
    }
  })

  it.each([
    [true, 'accepted'],
    [false, 'cancelled'],
  ] as const)('maps native callback %s to %s, without claiming save completion', async (accepted, expected) => {
    const host = fakeHost(accepted)
    const port = createTelegramReportDownloadPort(fakeTicketIssuer(), host, () => 'https://rutcampustrack.example')

    const result = await port.download(teacherJournalRequest(), () => true)

    expect(result).toBe(expected)
    expect(host.params).toHaveBeenCalledWith({
      url: `https://rutcampustrack.example/api/report-download/${'a'.repeat(43)}`,
      file_name: 'teacher-journal.pdf',
    })
  })

  it('uses the native Telegram adapter and the PNG extension for a headman trend ticket', async () => {
    const request = toHeadmanStatsTrendReportRequest({
      mode: 'SUBJECT',
      subjectId: 3,
      lessonTypes: ['LECTURE'],
    }, 'png')
    validateTicketRequest(request)
    const host = fakeHost(true)
    const port = createTelegramReportDownloadPort(
      fakeTicketIssuer(ticket('headman-stats-trend.png')),
      host,
      () => 'https://rutcampustrack.example',
    )

    await expect(port.download(request, () => true)).resolves.toBe('accepted')
    expect(host.params).toHaveBeenCalledWith({
      url: `https://rutcampustrack.example/api/report-download/${'a'.repeat(43)}`,
      file_name: 'headman-stats-trend.png',
    })
  })

  it('does not issue a ticket on Telegram versions without downloadFile', async () => {
    const tickets = fakeTicketIssuer()
    const issueTicket = vi.spyOn(tickets, 'issueTicket')
    const host = fakeHost(false, false)
    const port = createTelegramReportDownloadPort(tickets, host, () => 'https://rutcampustrack.example')

    await expect(port.download(teacherJournalRequest(), () => true)).resolves.toBe('unsupported')
    expect(issueTicket).not.toHaveBeenCalled()
    expect(host.params).not.toHaveBeenCalled()
  })

  it('does not call the host when the report selection becomes stale after ticket issuance', async () => {
    let current = true
    const tickets: ReportDownloadTicketIssuer = {
      assertCurrent: () => undefined,
      issueTicket: async () => {
        current = false
        return ticket()
      },
    }
    const host = fakeHost(true)
    const port = createTelegramReportDownloadPort(tickets, host, () => 'https://rutcampustrack.example')

    const result = await port.download(teacherJournalRequest(), () => current)

    expect(result).toBe('stale')
    expect(host.params).not.toHaveBeenCalled()
  })

  it('never calls the host when the session generation changes during ticket issuance', async () => {
    let generation = 1
    const host = fakeHost(true)
    const client = createGenerationBoundReportDownloadClient({
      currentGeneration: () => generation,
      accessTokenFor: () => 'synthetic-bearer',
      refreshFor: async () => undefined,
    }, async () => {
      generation = 2
      return new Response(JSON.stringify(ticket()), { status: 200 })
    })
    const port = createTelegramReportDownloadPort(client, host, () => 'https://rutcampustrack.example')

    await expect(port.download(teacherJournalRequest(), () => true)).rejects.toThrow()
    expect(host.params).not.toHaveBeenCalled()
  })

  it('refreshes once on 401, retries with the new session token, and keeps the generation bound', async () => {
    const generation = 4
    let token = 'expired-synthetic-bearer'
    const refreshFor = vi.fn(async (expectedGeneration: number) => {
      expect(expectedGeneration).toBe(4)
      token = 'fresh-synthetic-bearer'
    })
    const fetcher = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const authorization = new Headers(init?.headers).get('Authorization')
      if (authorization === 'Bearer expired-synthetic-bearer') return new Response(null, { status: 401 })
      return new Response(JSON.stringify(ticket()), { status: 200 })
    })
    const client = createGenerationBoundReportDownloadClient({
      currentGeneration: () => generation,
      accessTokenFor: () => token,
      refreshFor,
    }, fetcher)

    await expect(client.issueTicket(teacherJournalRequest())).resolves.toMatchObject({
      suggestedFilename: 'teacher-journal.pdf',
    })

    expect(refreshFor).toHaveBeenCalledTimes(1)
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(new Headers(fetcher.mock.calls[0]![1]?.headers).get('Authorization'))
      .toBe('Bearer expired-synthetic-bearer')
    expect(new Headers(fetcher.mock.calls[1]![1]?.headers).get('Authorization'))
      .toBe('Bearer fresh-synthetic-bearer')
    expect(generation).toBe(4)
  })

  it('does not retry or download when the session generation changes during refresh', async () => {
    let generation = 9
    const fetcher = vi.fn(async () => new Response(null, { status: 401 }))
    const refreshFor = vi.fn(async () => {
      generation = 10
    })
    const host = fakeHost(true)
    const client = createGenerationBoundReportDownloadClient({
      currentGeneration: () => generation,
      accessTokenFor: () => 'expired-synthetic-bearer',
      refreshFor,
    }, fetcher)
    const port = createTelegramReportDownloadPort(client, host, () => 'https://rutcampustrack.example')

    await expect(port.download(teacherJournalRequest(), () => true)).rejects.toThrow()

    expect(refreshFor).toHaveBeenCalledTimes(1)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(host.params).not.toHaveBeenCalled()
  })

  it('fails closed when the ticket path is absolute, even after authenticated issuance', async () => {
    const host = fakeHost(true)
    const port = createTelegramReportDownloadPort(fakeTicketIssuer({
      ...ticket(),
      downloadPath: `https://evil.example/api/report-download/${'a'.repeat(43)}`,
    }), host, () => 'https://rutcampustrack.example')

    await expect(port.download(teacherJournalRequest(), () => true)).rejects.toThrow()
    expect(host.params).not.toHaveBeenCalled()
  })
})
