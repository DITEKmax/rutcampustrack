import { AuthRequestError } from './auth-client'

/**
 * Auth transport marks an unreachable bootstrap endpoint with NETWORK and no
 * HTTP status. This is the only typed Auth bootstrap failure that may consult
 * a previously committed student snapshot; typed denials and protocol/session
 * errors stay fail closed.
 */
export function isOfflineBootstrapRecoveryError(error: unknown): boolean {
  return error instanceof AuthRequestError && error.status === undefined && error.code === 'NETWORK'
}
