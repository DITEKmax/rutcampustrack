const encoder = new TextEncoder()
const encode = (value: string) => encoder.encode(value)
const xmlHeader = '<?xml version="1.0" encoding="UTF-8"?>'
const office = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'

function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff
  for (const byte of bytes) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function zip(files: Record<string, string | Uint8Array<ArrayBuffer>>): BlobPart[] {
  const entries: BlobPart[] = []
  const directory: Uint8Array<ArrayBuffer>[] = []
  let offset = 0
  for (const [name, data] of Object.entries(files)) {
    const filename = encode(name)
    const bytes = typeof data === 'string' ? encode(data) : data
    const crc = crc32(bytes)
    const local = new Uint8Array(30)
    const header = new DataView(local.buffer)
    header.setUint32(0, 0x04034b50, true)
    header.setUint16(4, 20, true)
    header.setUint32(14, crc, true)
    header.setUint32(18, bytes.length, true)
    header.setUint32(22, bytes.length, true)
    header.setUint16(26, filename.length, true)
    const central = new Uint8Array(46)
    const record = new DataView(central.buffer)
    record.setUint32(0, 0x02014b50, true)
    record.setUint16(4, 20, true)
    record.setUint16(6, 20, true)
    record.setUint32(16, crc, true)
    record.setUint32(20, bytes.length, true)
    record.setUint32(24, bytes.length, true)
    record.setUint16(28, filename.length, true)
    record.setUint32(42, offset, true)
    entries.push(local, filename, bytes)
    directory.push(central, filename)
    offset += local.length + filename.length + bytes.length
  }
  const trailer = new Uint8Array(22)
  const end = new DataView(trailer.buffer)
  end.setUint32(0, 0x06054b50, true)
  end.setUint16(8, directory.length / 2, true)
  end.setUint16(10, directory.length / 2, true)
  end.setUint32(12, directory.reduce((size, row) => size + row.length, 0), true)
  end.setUint32(16, offset, true)
  return [...entries, ...directory, trailer]
}

const escaped = (value: string) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const relationships = (type: string, target: string) => `${xmlHeader}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="${office}/${type}" Target="${target}"/></Relationships>`

function officeDocument(kind: 'docx' | 'xlsx', lines: string[]): BlobPart[] {
  const types = {
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml',
  }
  const docx = kind === 'docx'
  const target = docx ? 'word/document.xml' : 'xl/workbook.xml'
  const contentTypes = `${xmlHeader}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/${target}" ContentType="${types[kind]}"/>${
    docx ? '' : '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
  }</Types>`
  const packageFiles = { '[Content_Types].xml': contentTypes, '_rels/.rels': relationships('officeDocument', target) }
  if (docx) return zip({ ...packageFiles, [target]: `${xmlHeader}<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${
    lines.map(line => `<w:p><w:r><w:t>${escaped(line)}</w:t></w:r></w:p>`).join('')
  }</w:body></w:document>` })
  const sheetNamespace = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
  return zip({
    ...packageFiles,
    [target]: `${xmlHeader}<workbook xmlns="${sheetNamespace}" xmlns:r="${office}"><sheets><sheet name="RutCampusTrack" sheetId="1" r:id="rId1"/></sheets></workbook>`,
    'xl/_rels/workbook.xml.rels': relationships('worksheet', 'worksheets/sheet1.xml'),
    'xl/worksheets/sheet1.xml': `${xmlHeader}<worksheet xmlns="${sheetNamespace}"><sheetData>${
      lines.map((line, index) => `<row r="${index + 1}"><c r="A${index + 1}" t="inlineStr"><is><t>${escaped(line)}</t></is></c></row>`).join('')
    }</sheetData></worksheet>`,
  })
}

function drawReport(lines: string[]): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = 1100
  canvas.height = Math.max(220, lines.length * 32 + 60)
  const context = canvas.getContext('2d')!
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#242039'
  context.font = '20px system-ui'
  lines.forEach((line, index) => context.fillText(line, 35, 45 + index * 32, 1030))
  return new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Не удалось создать PNG'))))
}

export async function syntheticReportFile(extension: string, request: object, samplePdf: string): Promise<Response> {
  const lines = ['RutCampusTrack · Демо-отчёт', ...JSON.stringify(request, null, 2).split('\n')]
  const type: Record<string, string> = {
    pdf: 'application/pdf', html: 'text/html;charset=utf-8', png: 'image/png', zip: 'application/zip',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  }
  const contentType = type[extension]
  if (!contentType) return Response.json({ detail: 'Неизвестный формат демо-отчёта.' }, { status: 400 })
  let parts: BlobPart[]
  if (extension === 'html') parts = [`<!doctype html><html lang="ru"><meta charset="utf-8"><title>Демо-отчёт</title><pre>${escaped(lines.join('\n'))}</pre></html>`]
  else if (extension === 'pdf') parts = [samplePdf]
  else if (extension === 'docx' || extension === 'xlsx') parts = officeDocument(extension, lines)
  else {
    const png = await drawReport(lines)
    parts = extension === 'zip' ? zip({ 'rct-demo-report.png': new Uint8Array(await png.arrayBuffer()) }) : [png]
  }
  return new Response(new Blob(parts, { type: contentType }), { headers: {
    'Content-Disposition': `attachment; filename="rct-demo-report.${extension}"`,
  } })
}
