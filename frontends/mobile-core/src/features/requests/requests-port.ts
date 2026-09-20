import type {
  StudentExcuseRequest,
  StudentLateCheckinRequest,
  StudentRequestDetail,
  StudentRequestOptions,
  StudentRequestPage,
} from '../../api/types'

export type RequestsApiBucket = 'OPEN' | 'ARCHIVE'

/** Authenticated transport owned by StudentApi; no cache or token access lives here. */
export interface RequestsPort {
  listRequests(bucket: RequestsApiBucket, page: number): Promise<StudentRequestPage>
  getRequest(id: string): Promise<StudentRequestDetail>
  getRequestOptions(): Promise<StudentRequestOptions>
  submitExcuse(command: StudentExcuseRequest, files: readonly File[], idempotencyKey: string): Promise<StudentRequestDetail>
  submitLateCheckin(command: StudentLateCheckinRequest, idempotencyKey: string): Promise<StudentRequestDetail>
  cancelRequest(id: string): Promise<StudentRequestDetail>
  downloadRequestAttachment(id: string, attachmentId: string): Promise<Blob>
}
