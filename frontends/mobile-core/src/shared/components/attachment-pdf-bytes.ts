export const PDF_PREVIEW_MAX_BYTES = 20 * 1024 * 1024
const tooLarge = () => new Error('PDF слишком большой для предпросмотра. Открой файл отдельно.')
/** Never requests an unbounded arrayBuffer, including when Content-Length is absent. */
export async function readPdfPreviewBytes(
  response: Pick<Response, 'headers' | 'body'>,
  signal?: AbortSignal,
): Promise<Uint8Array> {
  const declared = response.headers.get('Content-Length')
  if (declared !== null && Number(declared) > PDF_PREVIEW_MAX_BYTES) {
    await response.body?.cancel().catch(() => undefined)
    throw tooLarge()
  }
  if (!response.body) throw new Error('Не удалось прочитать PDF для предпросмотра.')
  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  try {
    while (true) {
      signal?.throwIfAborted()
      const { value, done } = await reader.read()
      signal?.throwIfAborted()
      if (done) break
      if (value.byteLength > PDF_PREVIEW_MAX_BYTES - total) throw tooLarge()
      total += value.byteLength
      chunks.push(value)
    }
    if (!total) throw new Error('PDF пустой. Не удалось показать первую страницу.')
    const bytes = new Uint8Array(total)
    let offset = 0
    for (const chunk of chunks) {
      bytes.set(chunk, offset)
      offset += chunk.byteLength
    }
    return bytes
  } catch (cause) {
    await reader.cancel().catch(() => undefined)
    throw cause
  } finally {
    reader.releaseLock()
  }
}
