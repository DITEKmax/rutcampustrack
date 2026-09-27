import type { RequestFileRef } from './types'

export type RequestAttachmentPreviewKind = 'image' | 'pdf' | 'unsupported'

interface PreviewResource {
  file: File
  url: string | null
}

export interface RequestAttachmentPreviewSnapshot {
  urls: ReadonlyMap<string, string>
  unavailableIds: ReadonlySet<string>
}

type ObjectUrlApi = Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'>

export function requestAttachmentPreviewKind(file: File | undefined): RequestAttachmentPreviewKind {
  if (!file) return 'unsupported'
  if (file.type === 'image/jpeg' || file.type === 'image/png') return 'image'
  if (file.type === 'application/pdf') return 'pdf'
  return 'unsupported'
}

/** Owns object URLs for selected request files and releases them with their File. */
export class RequestAttachmentPreviewUrls {
  private readonly resources = new Map<string, PreviewResource>()

  constructor(private readonly objectUrlApi: ObjectUrlApi = URL) {}

  sync(files: readonly RequestFileRef[]): RequestAttachmentPreviewSnapshot {
    const selected = new Map(files.map((file) => [file.id, file]))

    for (const [id, resource] of this.resources) {
      const current = selected.get(id)
      if (current?.file === resource.file) continue
      this.release(resource)
      this.resources.delete(id)
    }

    for (const file of files) {
      if (!file.file || requestAttachmentPreviewKind(file.file) === 'unsupported') continue
      if (this.resources.has(file.id)) continue

      let url: string | null = null
      try {
        url = this.objectUrlApi.createObjectURL(file.file)
      } catch {
        // Keep the attachment usable even if this browser cannot create a preview URL.
      }
      this.resources.set(file.id, { file: file.file, url })
    }

    const urls = new Map<string, string>()
    const unavailableIds = new Set<string>()
    for (const [id, resource] of this.resources) {
      if (resource.url) urls.set(id, resource.url)
      else unavailableIds.add(id)
    }
    return { urls, unavailableIds }
  }

  clear(): void {
    for (const resource of this.resources.values()) this.release(resource)
    this.resources.clear()
  }

  private release(resource: PreviewResource): void {
    if (!resource.url) return
    try {
      this.objectUrlApi.revokeObjectURL(resource.url)
    } catch {
      // Continue releasing the remaining URLs if one browser revocation fails.
    }
  }
}
