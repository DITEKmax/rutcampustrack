import { StudentApiError } from '../api/student-client'

/**
 * An expired PWA session and a transport failure may continue to show the
 * already partitioned schedule snapshot. Scope denial must never reuse it.
 */
export function isRecoverableReadFailure(error: unknown): boolean {
  return !(error instanceof StudentApiError) || error.response.status === 401
}
