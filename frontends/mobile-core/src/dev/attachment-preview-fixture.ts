import imageUrl from './assets/request-preview-sample.png'
import pdfUrl from './assets/request-preview-sample.pdf?url'
export async function syntheticAttachmentResponse(
  kind: 'image' | 'pdf' | 'unsupported' = 'image',
): Promise<Response> {
  if (kind === 'unsupported')
    return new Response('<h1>Unsupported test data</h1>', {
      headers: { 'Content-Type': 'text/html' },
    })
  const response = await fetch(kind === 'pdf' ? pdfUrl : imageUrl)
  return new Response(await response.blob(), {
    headers: { 'Content-Type': kind === 'pdf' ? 'application/pdf' : 'image/png' },
  })
}
