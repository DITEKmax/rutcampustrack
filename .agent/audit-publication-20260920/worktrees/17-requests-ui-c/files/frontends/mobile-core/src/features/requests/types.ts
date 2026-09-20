export type RequestBucket = 'open' | 'archive'
export type RequestKind = 'EXCUSE' | 'LATE_CHECKIN'
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | (string & {})
export type RequestOrigin = 'MANUAL' | 'AUTO_GEO_FAILURE' | (string & {})
export type RequestAttachmentState = 'ACTIVE' | 'EXPIRED' | (string & {})

/** Feature-local projection of StudentRequestApiModels.Lesson. */
export interface RequestLesson {
  id: string
  lessonNumber?: number | null
  status?: string | null
  blocked?: boolean | null
  subjectId?: string | null
  subjectName?: string | null
  subjectType?: string | null
  semesterId?: string | null
  date?: string | null
  startsAt?: string | null
  endsAt?: string | null
}

export interface RequestSummary {
  id: string
  kind: RequestKind
  status: RequestStatus
  origin: RequestOrigin
  lessons?: readonly RequestLesson[] | null
  createdAt?: string | null
  updatedAt?: string | null
  canCancel?: boolean | null
}

export interface RequestDecision {
  comment?: string | null
  decidedAt?: string | null
}

export interface RequestAttachment {
  id: string
  name?: string | null
  contentType?: string | null
  sizeBytes?: number | null
  state?: RequestAttachmentState | null
  expiresAt?: string | null
  expiredAt?: string | null
}

export interface RequestDetail {
  summary: RequestSummary
  reason?: string | null
  comment?: string | null
  decision?: RequestDecision | null
  attachments?: readonly RequestAttachment[] | null
}

export interface RequestReasonOption {
  code: string
  label: string
  commentRequired: boolean
}

export interface RequestFileLimits {
  maxFiles?: number | null
  maxBytesPerFile?: number | null
  maxBytesTotal?: number | null
  contentTypes?: readonly string[] | null
  extensions?: readonly string[] | null
}

export interface RequestBudget {
  semesterId?: string | null
  limit?: number | null
  used?: number | null
  remaining?: number | null
}

export interface RequestPendingRef {
  id: string
  kind?: RequestKind | null
  origin?: RequestOrigin | null
}

export interface RequestLessonOption {
  lesson: RequestLesson | null
  excuseEligible?: boolean | null
  lateCheckinEligible?: boolean | null
  unavailableReason?: string | null
  pendingRequests?: readonly RequestPendingRef[] | null
}

export interface RequestOptions {
  reasons?: readonly RequestReasonOption[] | null
  files?: RequestFileLimits | null
  budget?: RequestBudget | null
  lessons?: readonly RequestLessonOption[] | null
}

export type RequestAccessState = 'allowed' | 'forbidden' | 'no-active-semester'

export interface RequestFileRef {
  id: string
  name: string
  size: number
  type: string
  lastModified?: number
  file?: File
}

export interface RequestTypeChoice {
  kind: RequestKind
  label: string
  symbol: string
  available: boolean
  reason?: string | null
}

export interface ExcuseRequestPayload {
  lessonIds: readonly string[]
  reason: string
  comment: string
  files: readonly RequestFileRef[]
}

export interface LateCheckinRequestPayload {
  lessonId: string
}

export type RequestsView = 'inbox' | 'type' | 'excuse' | 'late'

export interface RequestsDraft {
  ownerId: string
  sessionGeneration: string
  bucket: RequestBucket
  view: RequestsView
  excuseLessonIds: string[]
  excuseReason: string | null
  excuseComment: string
  excuseFiles: RequestFileRef[]
  lateLessonId: string | null
}

export type RequestsDraftPatch = Partial<
  Pick<
    RequestsDraft,
    'bucket' | 'view' | 'excuseLessonIds' | 'excuseReason' | 'excuseComment' | 'excuseFiles' | 'lateLessonId'
  >
>
