import type { RequestFileLimits, RequestFileRef } from './types'

const JPEG_EXTENSIONS = new Set(['.jpg', '.jpeg'])
const PNG_EXTENSIONS = new Set(['.png'])
const PDF_EXTENSIONS = new Set(['.pdf'])

function normalizeContentType(value: string | null | undefined): string {
  return value === null || value === undefined ? '' : stripJavaWhitespace(value).toLowerCase()
}

function isJavaWhitespace(codePoint: number): boolean {
  return (codePoint >= 0x0009 && codePoint <= 0x000d)
    || (codePoint >= 0x001c && codePoint <= 0x0020)
    || codePoint === 0x1680
    || (codePoint >= 0x2000 && codePoint <= 0x2006)
    || (codePoint >= 0x2008 && codePoint <= 0x200a)
    || codePoint === 0x2028
    || codePoint === 0x2029
    || codePoint === 0x205f
    || codePoint === 0x3000
}

function codePointBefore(value: string, index: number): number {
  const low = value.charCodeAt(index - 1)
  if (low >= 0xdc00 && low <= 0xdfff && index > 1) {
    const high = value.charCodeAt(index - 2)
    if (high >= 0xd800 && high <= 0xdbff) {
      return ((high - 0xd800) * 0x400) + (low - 0xdc00) + 0x10000
    }
  }
  return low
}

function stripJavaWhitespace(value: string): string {
  let start = 0
  let end = value.length
  while (start < end) {
    const codePoint = value.codePointAt(start)
    if (codePoint === undefined || !isJavaWhitespace(codePoint)) break
    start += codePoint > 0xffff ? 2 : 1
  }
  while (end > start) {
    const codePoint = codePointBefore(value, end)
    if (!isJavaWhitespace(codePoint)) break
    end -= codePoint > 0xffff ? 2 : 1
  }
  return value.slice(start, end)
}

export function normalizeAttachmentExtension(value: string | null | undefined): string {
  const normalized = value === null || value === undefined ? '' : stripJavaWhitespace(value).toLowerCase()
  if (!normalized) return ''
  return normalized.startsWith('.') ? normalized : '.' + normalized
}

function safeAttachmentFilename(name: string): string {
  let value = stripJavaWhitespace(name).replaceAll('\\', '_').replaceAll('/', '_').replaceAll('..', '_')
  let filtered = ''
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    if (code >= 0x20 && code !== 0x7f) filtered += value[index]
  }
  value = stripJavaWhitespace(filtered)
  if (!value || value === '.' || value === '..') return 'attachment'
  return value.length > 255 ? value.slice(0, 255) : value
}

function attachmentExtension(name: string): string {
  const safeName = safeAttachmentFilename(name)
  const dot = safeName.lastIndexOf('.')
  return dot >= 0 ? safeName.slice(dot).toLowerCase() : ''
}

/**
 * The attendance service validates the declared MIME, filename extension and
 * detected signature as one of these exact pairs. The options endpoint exposes
 * the two allowed lists separately, so the client keeps this same concordance
 * as a fail-fast check before multipart submission.
 */
export function mimeExtensionMatches(contentType: string | null | undefined, extension: string): boolean {
  const normalizedType = normalizeContentType(contentType)
  return (normalizedType === 'image/jpeg' && JPEG_EXTENSIONS.has(extension))
    || (normalizedType === 'image/png' && PNG_EXTENSIONS.has(extension))
    || (normalizedType === 'application/pdf' && PDF_EXTENSIONS.has(extension))
}

function contentTypeMatches(contentType: string, accepted: string): boolean {
  const normalizedAccepted = normalizeContentType(accepted)
  return normalizedAccepted.endsWith('/*')
    ? contentType.startsWith(normalizedAccepted.slice(0, -1))
    : contentType === normalizedAccepted
}

export function acceptsRequestFile(
  file: Pick<RequestFileRef, 'name' | 'type'>,
  limits: RequestFileLimits | null | undefined,
): boolean {
  const types = limits?.contentTypes ?? []
  const extensions = (limits?.extensions ?? []).map(normalizeAttachmentExtension)
  if (types.length === 0 && extensions.length === 0) return true

  const contentType = normalizeContentType(file.type)
  const extension = attachmentExtension(file.name)
  const typeMatches = types.length === 0 || types.some((accepted) => contentTypeMatches(contentType, accepted))
  const extensionMatches = extensions.length === 0 || extensions.includes(extension)
  if (!typeMatches || !extensionMatches) return false

  // Current server metadata contains both lists. If either list is omitted,
  // preserve the endpoint's open-ended metadata semantics; when both are
  // present, enforce the server's exact MIME/extension concordance.
  return types.length === 0 || extensions.length === 0 || mimeExtensionMatches(contentType, extension)
}

export function requestAttachmentFormatHint(limits: RequestFileLimits | null | undefined): string {
  const types = (limits?.contentTypes ?? []).map(normalizeContentType)
  const extensions = (limits?.extensions ?? []).map(normalizeAttachmentExtension)
  if (types.length === 0 && extensions.length === 0) return ''
  const pairs = [
    { type: 'image/jpeg', label: 'JPEG — .jpg/.jpeg', extensions: JPEG_EXTENSIONS },
    { type: 'image/png', label: 'PNG — .png', extensions: PNG_EXTENSIONS },
    { type: 'application/pdf', label: 'PDF — .pdf', extensions: PDF_EXTENSIONS },
  ]
    .filter((pair) => types.includes(pair.type) && [...pair.extensions].some((extension) => extensions.includes(extension)))
    .map((pair) => pair.label)
  return pairs.length > 0
    ? 'MIME и расширение должны соответствовать: ' + pairs.join(', ') + '.'
    : 'MIME и расширение должны соответствовать указанным ограничениям.'
}
