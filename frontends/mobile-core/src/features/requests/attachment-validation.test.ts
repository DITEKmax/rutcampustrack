import { describe, expect, it } from 'vitest'
import { acceptsRequestFile, requestAttachmentFormatHint } from './attachment-validation'

const limits = {
  contentTypes: ['image/jpeg', 'image/png', 'application/pdf'],
  extensions: ['.jpg', '.jpeg', '.png', '.pdf'],
}

describe('request attachment validation', () => {
  it('accepts only MIME and extension combinations supported by the server', () => {
    expect(acceptsRequestFile({ name: 'photo.jpg', type: 'image/jpeg' }, limits)).toBe(true)
    expect(acceptsRequestFile({ name: 'photo.jpeg', type: 'image/jpeg' }, limits)).toBe(true)
    expect(acceptsRequestFile({ name: 'scan.png', type: 'image/png' }, limits)).toBe(true)
    expect(acceptsRequestFile({ name: 'scan.pdf', type: 'application/pdf' }, limits)).toBe(true)
    expect(acceptsRequestFile({ name: 'proof..pdf', type: 'application/pdf' }, limits)).toBe(false)
    expect(acceptsRequestFile({ name: 'scan.jpg', type: 'application/pdf' }, limits)).toBe(false)
    expect(acceptsRequestFile({ name: 'scan.pdf', type: 'image/png' }, limits)).toBe(false)
  })

  it.each([
    ['NO-BREAK SPACE', '\u00a0'],
    ['FIGURE SPACE', '\u2007'],
    ['NARROW NO-BREAK SPACE', '\u202f'],
    ['ZERO WIDTH NO-BREAK SPACE', '\ufeff'],
  ])('preserves server rejection for a trailing %s in the filename', (_label, suffix) => {
    expect(acceptsRequestFile({ name: 'proof.pdf' + suffix, type: 'application/pdf' }, limits)).toBe(false)
  })

  it.each([
    ['NO-BREAK SPACE', '\u00a0'],
    ['FIGURE SPACE', '\u2007'],
    ['NARROW NO-BREAK SPACE', '\u202f'],
    ['ZERO WIDTH NO-BREAK SPACE', '\ufeff'],
  ])('preserves server rejection for a trailing %s in the MIME type', (_label, suffix) => {
    expect(acceptsRequestFile({ name: 'proof.pdf', type: 'application/pdf' + suffix }, limits)).toBe(false)
  })

  it.each([
    ['tab', '\u0009'],
    ['record separator', '\u001e'],
    ['Ogham space mark', '\u1680'],
    ['ideographic space', '\u3000'],
  ])('matches Java String.strip for %s around the filename', (_label, whitespace) => {
    expect(acceptsRequestFile({
      name: whitespace + 'proof.pdf' + whitespace,
      type: 'application/pdf',
    }, limits)).toBe(true)
  })

  it.each([
    ['tab', '\u0009'],
    ['record separator', '\u001e'],
    ['Ogham space mark', '\u1680'],
    ['ideographic space', '\u3000'],
  ])('matches Java String.strip for %s around the MIME type', (_label, whitespace) => {
    expect(acceptsRequestFile({
      name: 'proof.pdf',
      type: whitespace + 'application/pdf' + whitespace,
    }, limits)).toBe(true)
  })

  it('keeps a post-truncation filename suffix exact', () => {
    const truncated = 'a'.repeat(250) + '.pdf x'
    const validNeighbor = 'a'.repeat(250) + '.pdf'
    expect(acceptsRequestFile({ name: truncated, type: 'application/pdf' }, limits)).toBe(false)
    expect(acceptsRequestFile({ name: validNeighbor, type: 'application/pdf' }, limits)).toBe(true)
  })

  it('describes the concordance rule in the validation feedback', () => {
    expect(requestAttachmentFormatHint(limits)).toContain('MIME и расширение должны соответствовать')
    expect(requestAttachmentFormatHint(limits)).toContain('JPEG — .jpg/.jpeg')
  })
})
