import type { WebTransport } from '../../app/api'
export interface SubjectDeletionPreview {
  subjectId: string
  semesterId: string
  groupId: string
  subjectName: string
  previewDigest: string
  academicRows: number
  scheduleRows: number
  attendanceRows: number
  mixedTickets: number
}
export interface SubjectDeletionChallenge {
  challengeId: string
  expiresAt: string
  attemptsRemaining: number
}
export interface SubjectDeletionOperation {
  operationId: string
  subjectId: string
  semesterId: string
  phase: 'PREPARING' | 'SEALED' | 'COMMITTING' | 'COMPLETED' | 'RELEASING' | 'CANCELLED'
  retryable: boolean
  reason: string | null
}
export interface SubjectDeletionConfirmation {
  operationId: string
  previewDigest: string
  challengeId: string
  code: string
}
export class SubjectDeletionError extends Error {
  constructor(
    readonly status: number,
    detail: string,
  ) {
    super(detail)
  }
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('Некорректный ответ удаления предмета.')
  return value as Record<string, unknown>
}
function text(row: Record<string, unknown>, key: string): string {
  if (typeof row[key] !== 'string' || !row[key])
    throw new Error('Неполный ответ удаления предмета.')
  return row[key]
}
export class SubjectDeletionClient {
  constructor(private readonly transport: WebTransport) {}
  private async request(path: string, init?: RequestInit): Promise<unknown> {
    this.transport.assertCurrent()
    const headers = new Headers(init?.headers)
    headers.set('Content-Type', 'application/json')
    const response = await this.transport.fetcher(path, { ...init, cache: 'no-store', headers })
    const value: unknown = await response.json().catch(() => null)
    this.transport.assertCurrent()
    if (!response.ok) {
      const problem = value && typeof value === 'object' ? (value as Record<string, unknown>) : null
      throw new SubjectDeletionError(
        response.status,
        typeof problem?.detail === 'string'
          ? problem.detail
          : `Не удалось подтвердить удаление (HTTP ${response.status}).`,
      )
    }
    return value
  }
  async preview(id: number): Promise<SubjectDeletionPreview> {
    const row = record(await this.request(`/api/academic/subjects/${id}/deletion-preview`))
    const result: SubjectDeletionPreview = {
      subjectId: text(row, 'subjectId'),
      semesterId: text(row, 'semesterId'),
      groupId: text(row, 'groupId'),
      subjectName: text(row, 'subjectName'),
      previewDigest: text(row, 'previewDigest'),
      academicRows: Number(row.academicRows),
      scheduleRows: Number(row.scheduleRows),
      attendanceRows: Number(row.attendanceRows),
      mixedTickets: Number(row.mixedTickets),
    }
    if (
      result.subjectId !== String(id) ||
      !/^[0-9a-f]{64}$/.test(result.previewDigest) ||
      [result.academicRows, result.scheduleRows, result.attendanceRows, result.mixedTickets].some(
        (count) => !Number.isSafeInteger(count) || count < 0,
      )
    )
      throw new Error('Сервер не подтвердил точный состав удаления.')
    return result
  }
  async challenge(
    id: number,
    operationId: string,
    previewDigest: string,
    password: string,
  ): Promise<SubjectDeletionChallenge> {
    const row = record(
      await this.request(`/api/academic/subjects/${id}/deletion-challenge`, {
        method: 'POST',
        body: JSON.stringify({ operationId, previewDigest, password }),
      }),
    )
    const result = {
      challengeId: text(row, 'challengeId'),
      expiresAt: text(row, 'expiresAt'),
      attemptsRemaining: Number(row.attemptsRemaining),
    }
    if (
      !/^[A-Za-z0-9_-]{32}$/.test(result.challengeId) ||
      !Number.isFinite(Date.parse(result.expiresAt)) ||
      !Number.isSafeInteger(result.attemptsRemaining) ||
      result.attemptsRemaining < 0
    )
      throw new Error('Сервер не подтвердил запрос кода.')
    return result
  }
  private operation(value: unknown, operationId: string): SubjectDeletionOperation {
    const row = record(value)
    if (
      row.operationId !== operationId ||
      !['PREPARING', 'SEALED', 'COMMITTING', 'COMPLETED', 'RELEASING', 'CANCELLED'].includes(
        String(row.phase),
      ) ||
      typeof row.retryable !== 'boolean' ||
      !(row.reason === null || typeof row.reason === 'string')
    )
      throw new Error('Сервер вернул другую операцию удаления.')
    return {
      operationId,
      subjectId: text(row, 'subjectId'),
      semesterId: text(row, 'semesterId'),
      phase: row.phase as SubjectDeletionOperation['phase'],
      retryable: row.retryable,
      reason: row.reason,
    }
  }
  async confirm(id: number, key: string, input: SubjectDeletionConfirmation) {
    return this.operation(
      await this.request(`/api/academic/subjects/${id}/deletion`, {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
        body: JSON.stringify(input),
      }),
      input.operationId,
    )
  }
  async status(operationId: string) {
    return this.operation(
      await this.request(
        `/api/academic/subject-deletion-operations/${encodeURIComponent(operationId)}`,
      ),
      operationId,
    )
  }
}
