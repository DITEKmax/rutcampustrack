#!/usr/bin/env node
'use strict';

import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';
import fs from 'node:fs';
import https from 'node:https';
import { deflateSync, inflateSync } from 'node:zlib';
import { URL } from 'node:url';

const MAX_GATEWAY_BYTES = 24 * 1024 * 1024;
const FILE_BYTES = 10 * 1024 * 1024;
const BODY_READ_LIMIT = 4 * 1024 * 1024;
const BOUNDARY = 'rct-student-requests-gate-20260913';
const REDACTED = '<redacted>';
const args = process.argv.slice(2);

function hasArg(name) {
  return args.includes(name);
}

function argValue(name, fallback = undefined) {
  const index = args.indexOf(name);
  return index >= 0 && index + 1 < args.length ? args[index + 1] : fallback;
}

function assertThat(condition, message) {
  if (!condition) throw new Error(message);
}

function sha256(bytes) {
  return crypto.createHash('sha256').update(bytes).digest('hex');
}

function chunk(type, data) {
  const typeBytes = Buffer.from(type, 'ascii');
  assertThat(typeBytes.length === 4, `PNG chunk type must be four bytes: ${type}`);
  const length = Buffer.allocUnsafe(4);
  length.writeUInt32BE(data.length, 0);
  const crc = Buffer.allocUnsafe(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBytes, data])), 0);
  return Buffer.concat([length, typeBytes, data, crc]);
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function makeDeterministicPdf(size = FILE_BYTES) {
  const header = Buffer.from('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n', 'binary');
  const objectBodies = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] >>\nendobj\n'
  ].map((value) => Buffer.from(value, 'ascii'));
  const offsets = [];
  let offset = header.length;
  for (const body of objectBodies) {
    offsets.push(offset);
    offset += body.length;
  }
  const xrefPrefix = Buffer.from('xref\n0 4\n0000000000 65535 f \n', 'ascii');
  const xrefEntries = offsets
    .map((value) => `${String(value).padStart(10, '0')} 00000 n \n`)
    .join('');
  const trailerPrefix = Buffer.from('trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n', 'ascii');
  const trailerSuffix = Buffer.from('\n%%EOF\n', 'ascii');
  let padding = Buffer.alloc(0);
  let xref;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const startxref = header.length + objectBodies.reduce((sum, body) => sum + body.length, 0) + padding.length;
    xref = Buffer.from(`${xrefPrefix.toString('ascii')}${xrefEntries}${trailerPrefix.toString('ascii')}${startxref}${trailerSuffix.toString('ascii')}`, 'ascii');
    const nextPaddingLength = size - header.length - objectBodies.reduce((sum, body) => sum + body.length, 0) - xref.length;
    assertThat(nextPaddingLength > 1, 'deterministic PDF target is too small');
    padding = Buffer.alloc(nextPaddingLength, 0x20);
    padding[0] = 0x25;
    padding[padding.length - 1] = 0x0a;
  }
  const output = Buffer.concat([header, ...objectBodies, padding, xref]);
  assertThat(output.length === size, `PDF fixture length ${output.length} != ${size}`);
  assertThat(output.subarray(0, 5).toString('ascii') === '%PDF-', 'PDF fixture signature');
  assertThat(output.subarray(output.length - 6).toString('ascii') === '%%EOF\n', 'PDF fixture trailer');
  return output;
}

function makeDeterministicPng(size = FILE_BYTES) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(1, 0);
  ihdr.writeUInt32BE(1, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const idat = deflateSync(Buffer.from([0, 255, 255, 255, 255]));
  const fixed = [signature, chunk('IHDR', ihdr), chunk('IDAT', idat)];
  const iend = chunk('IEND', Buffer.alloc(0));
  let remaining = size - fixed.reduce((sum, part) => sum + part.length, 0) - iend.length;
  assertThat(remaining > 12, 'deterministic PNG target is too small');
  // rCTa is a valid unknown ancillary chunk: letters only, reserved bit clear,
  // and safe-to-copy. Its deterministic payload fills the exact fixture size.
  const opaquePart = chunk('rCTa', Buffer.alloc(remaining - 12, 0x52));
  const output = Buffer.concat([...fixed, opaquePart, iend]);
  assertThat(output.length === size, `PNG fixture length ${output.length} != ${size}`);
  assertThat(output.subarray(0, 8).equals(signature), 'PNG fixture signature');
  assertThat(output.subarray(output.length - 8, output.length - 4).toString('ascii') === 'IEND', 'PNG fixture trailer');
  validatePng(output);
  return output;
}

function validatePng(bytes) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  assertThat(Buffer.isBuffer(bytes) && bytes.length >= signature.length + 12, 'PNG fixture is not a byte buffer');
  assertThat(bytes.subarray(0, signature.length).equals(signature), 'PNG signature is invalid');
  let offset = signature.length;
  let sawIhdr = false;
  let sawIdat = false;
  let sawIend = false;
  let width = 0;
  let height = 0;
  let idat = [];
  while (offset < bytes.length) {
    assertThat(offset + 12 <= bytes.length, 'PNG chunk header/trailer is truncated');
    const length = bytes.readUInt32BE(offset);
    const typeBytes = bytes.subarray(offset + 4, offset + 8);
    const type = typeBytes.toString('ascii');
    assertThat(/^[A-Za-z]{4}$/.test(type), `PNG chunk type is invalid: ${type}`);
    assertThat((typeBytes[2] & 0x20) === 0, `PNG chunk reserved bit is set: ${type}`);
    const end = offset + 12 + length;
    assertThat(end <= bytes.length, `PNG chunk ${type} exceeds fixture length`);
    const data = bytes.subarray(offset + 8, offset + 8 + length);
    const expectedCrc = bytes.readUInt32BE(offset + 8 + length);
    const actualCrc = crc32(Buffer.concat([typeBytes, data]));
    assertThat(actualCrc === expectedCrc, `PNG chunk CRC mismatch: ${type}`);
    if (!sawIhdr) assertThat(type === 'IHDR', 'PNG IHDR must be the first chunk');
    if (type === 'IHDR') {
      assertThat(!sawIhdr && length === 13, 'PNG must contain one 13-byte IHDR');
      sawIhdr = true;
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      assertThat(width === 1 && height === 1, 'PNG fixture dimensions must be 1x1');
      assertThat(data[8] === 8 && data[9] === 6, 'PNG fixture must be RGBA 8-bit');
      assertThat(data[10] === 0 && data[11] === 0 && data[12] === 0, 'PNG fixture uses unsupported compression/filter/interlace');
    } else if (type === 'IDAT') {
      assertThat(sawIhdr && !sawIend, 'PNG IDAT must follow IHDR and precede IEND');
      sawIdat = true;
      idat.push(data);
    } else if (type === 'IEND') {
      assertThat(sawIhdr && sawIdat && length === 0, 'PNG IEND is missing required preceding data');
      sawIend = true;
      assertThat(end === bytes.length, 'PNG has trailing bytes after IEND');
    }
    offset = end;
    if (sawIend) break;
  }
  assertThat(sawIhdr && sawIdat && sawIend, 'PNG is missing IHDR, IDAT or IEND');
  const scanline = inflateSync(Buffer.concat(idat));
  assertThat(scanline.length === 5, 'PNG RGBA scanline must contain one filter and four channels');
  assertThat(scanline[0] === 0 && scanline.subarray(1).equals(Buffer.from([255, 255, 255, 255])), 'PNG scanline pixels are invalid');
  return { width, height, bitDepth: 8, colorType: 6, scanlineBytes: scanline.length };
}

function makeMultipartBody(request, files) {
  const parts = [
    Buffer.from(`--${BOUNDARY}\r\nContent-Disposition: form-data; name="request"\r\nContent-Type: application/json\r\n\r\n${JSON.stringify(request)}\r\n`, 'utf8')
  ];
  for (const file of files) {
    parts.push(Buffer.from(`--${BOUNDARY}\r\nContent-Disposition: form-data; name="files"; filename="${file.name}"\r\nContent-Type: ${file.contentType}\r\n\r\n`, 'ascii'));
    parts.push(file.bytes);
    parts.push(Buffer.from('\r\n', 'ascii'));
  }
  parts.push(Buffer.from(`--${BOUNDARY}--\r\n`, 'ascii'));
  return {
    body: Buffer.concat(parts),
    contentType: `multipart/form-data; boundary=${BOUNDARY}`
  };
}

function makeOversizeMultipart(size = MAX_GATEWAY_BYTES + 1) {
  const prefix = Buffer.from(`--${BOUNDARY}\r\nContent-Disposition: form-data; name="request"\r\nContent-Type: application/json\r\n\r\n{"lessonIds":["1"],"reason":"OTHER","comment":"oversize"}\r\n--${BOUNDARY}\r\nContent-Disposition: form-data; name="files"; filename="oversize.pdf"\r\nContent-Type: application/pdf\r\n\r\n`, 'ascii');
  const suffix = Buffer.from(`\r\n--${BOUNDARY}--\r\n`, 'ascii');
  assertThat(size > prefix.length + suffix.length, 'oversize fixture target is too small');
  return {
    prefix,
    suffix,
    payloadBytes: size - prefix.length - suffix.length,
    contentType: `multipart/form-data; boundary=${BOUNDARY}`
  };
}

function redact(value, secrets = []) {
  const registered = secrets
    .filter((item) => typeof item === 'string' && item.length > 0)
    .sort((left, right) => right.length - left.length);
  const registeredPattern = registered.length > 0
    ? new RegExp(registered.map((item) => item.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'), 'g')
    : null;
  const replaceText = (text) => {
    const safe = registeredPattern ? text.replace(registeredPattern, REDACTED) : text;
    return safe
      .replace(/(Bearer\s+)[A-Za-z0-9._~+/=-]+/gi, `$1${REDACTED}`)
      .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, REDACTED);
  };
  const sensitiveTextPattern = /("?(?:accessToken|refreshToken|password|authorization|cookie|set-cookie)"?\s*[:=]\s*)("[^"\\]*(?:\\[\s\S][^"\\]*)*"|'[^'\\]*(?:\\[\s\S][^'\\]*)*'|[^,;}\s]+)/gi;
  const replacePlainText = (text, quoteValue) => replaceText(text)
    .replace(sensitiveTextPattern, `$1${quoteValue ? `"${REDACTED}"` : REDACTED}`);
  const sensitiveKey = /^(?:accessToken|refreshToken|password|authorization|cookie|set-cookie)$/i;
  const sanitize = (input) => {
    if (typeof input === 'string') return replacePlainText(input, false);
    if (Array.isArray(input)) return input.map((item) => sanitize(item));
    if (input && typeof input === 'object') {
      return Object.fromEntries(Object.entries(input).map(([key, item]) => [
        key,
        sensitiveKey.test(key) && typeof item === 'string' && item.length > 0 ? REDACTED : sanitize(item)
      ]));
    }
    return input;
  };
  if (typeof value === 'string') return replacePlainText(value, true);
  return JSON.stringify(sanitize(value));
}

function registerDiagnosticSecret(registry, value) {
  if (!Array.isArray(registry) || typeof value !== 'string' || value.length === 0) return;
  if (!registry.includes(value)) registry.push(value);
}

function registerCookieSecrets(registry, cookieHeader) {
  if (typeof cookieHeader !== 'string' || cookieHeader.length === 0) return;
  registerDiagnosticSecret(registry, cookieHeader);
  for (const pair of cookieHeader.split(';')) {
    const trimmed = pair.trim();
    registerDiagnosticSecret(registry, trimmed);
    const separator = trimmed.indexOf('=');
    if (separator > 0) registerDiagnosticSecret(registry, trimmed.slice(separator + 1));
  }
}

function bodyText(response) {
  return response.body.length === 0 ? '' : response.body.toString('utf8');
}

function jsonResponse(response) {
  const text = bodyText(response);
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function canonicalInstant(value, label) {
  assertThat(typeof value === 'string' && value.length > 0, `${label} is missing`);
  assertThat(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value),
    `${label} is not a UTC Instant wire value`);
  const parsed = Date.parse(value);
  assertThat(Number.isFinite(parsed), `${label} is not an ISO timestamp`);
  return new Date(parsed).toISOString();
}

function canonicalAttachmentDescriptor(item, label) {
  assertThat(item && typeof item === 'object', `${label} is not an object`);
  // This validator consumes the public BFF wire DTO. Mongo's snake_case
  // projection is normalized separately by runner.ps1; aliases here would
  // let a generic DTO pass as an API response.
  for (const alias of ['content_type', 'size', 'uploaded_at', 'expires_at', 'expired_at']) {
    assertThat(!Object.prototype.hasOwnProperty.call(item, alias), `${label}.${alias} is not a public API field`);
  }
  const id = item.id;
  const name = item.name;
  const contentType = item.contentType;
  const size = item.sizeBytes;
  const digest = item.sha256;
  const state = item.state;
  const uploadedAt = item.uploadedAt;
  const expiresAt = item.expiresAt;
  const expiredAt = item.expiredAt;
  assertThat(typeof id === 'string' && id.length > 0, `${label}.id is missing`);
  assertThat(typeof name === 'string' && name.length > 0, `${label}.name is missing`);
  assertThat(typeof contentType === 'string' && contentType.length > 0, `${label}.content_type is missing`);
  assertThat(Number.isSafeInteger(size) && size >= 0, `${label}.size is invalid`);
  assertThat(typeof digest === 'string' && /^[0-9a-f]{64}$/i.test(digest), `${label}.sha256 is invalid`);
  assertThat(state === 'ACTIVE', `${label}.state must be exactly ACTIVE for a newly submitted request`);
  const canonicalUploadedAt = canonicalInstant(uploadedAt, `${label}.uploaded_at`);
  const canonicalExpiresAt = canonicalInstant(expiresAt, `${label}.expires_at`);
  assertThat(Date.parse(canonicalUploadedAt) < Date.parse(canonicalExpiresAt), `${label}.uploaded_at must be before expires_at for a newly submitted request`);
  assertThat(expiredAt === undefined || expiredAt === null, `${label}.expired_at must be null or absent for a newly submitted request`);
  const canonicalExpiredAt = null;
  return {
    id,
    name,
    contentType,
    sizeBytes: size,
    sha256: digest.toLowerCase(),
    // Preserve the exact public enum emitted by StudentRequestApiModels.
    // PowerShell normalizes stored Mongo descriptors separately, but the
    // serialized API evidence must retain ACTIVE rather than lowercasing it.
    state,
    uploadedAt: canonicalUploadedAt,
    expiresAt: canonicalExpiresAt,
    expiredAt: canonicalExpiredAt
  };
}

function assertStudentSessionContract(session, label = 'Auth session') {
  assertThat(session && typeof session === 'object', `${label} is not a JSON object`);
  assertThat(typeof session.userId === 'string' && /^[1-9][0-9]*$/.test(session.userId),
    `${label}.userId must be a positive decimal string`);
  assertThat(session.activeRole === 'STUDENT', `${label}.activeRole is not STUDENT`);
  return { userId: session.userId, activeRole: session.activeRole };
}

function assertRequestDetailContract(detail, submittedPayload, label = 'I1 response') {
  assertThat(detail && typeof detail === 'object', `${label} is not a JSON object`);
  assertThat(!Object.prototype.hasOwnProperty.call(detail, 'selectedLesson'),
    `${label}.selectedLesson is not part of StudentRequestApiModels.Detail`);
  assertThat(detail.reason === submittedPayload.reason, `${label} reason does not match submitted payload`);
  assertThat(detail.comment === submittedPayload.comment, `${label} comment does not match submitted payload`);
  const summary = detail.summary;
  assertThat(summary && summary.kind === 'EXCUSE', `${label} summary.kind is not EXCUSE`);
  assertThat(summary.status === 'PENDING', `${label} summary.status is not PENDING (SUBMITTED domain tickets map to the API pending state)`);
  assertThat(summary.origin === 'MANUAL', `${label} summary.origin is not MANUAL`);
  assertThat(typeof summary.id === 'string' && summary.id.length > 0, `${label} summary.id is missing`);
  assertThat(typeof summary.createdAt === 'string', `${label} summary.createdAt is missing`);
  assertThat(typeof summary.updatedAt === 'string', `${label} summary.updatedAt is missing`);
  canonicalInstant(summary.createdAt, `${label}.summary.createdAt`);
  canonicalInstant(summary.updatedAt, `${label}.summary.updatedAt`);
  const submittedLessonIds = submittedPayload.lessonIds.map((value) => String(value)).sort();
  assertThat(Array.isArray(summary.lessons), `${label}.summary.lessons must be an array`);
  const returnedLessons = summary.lessons;
  const returnedLessonIds = returnedLessons.map((lesson, index) => {
    assertThat(lesson && typeof lesson === 'object', `${label}.summary.lessons[${index}] is not an object`);
    assertThat(typeof lesson.id === 'string' && lesson.id.length > 0, `${label}.summary.lessons[${index}].id is missing`);
    assertThat(Number.isInteger(lesson.lessonNumber), `${label}.summary.lessons[${index}].lessonNumber is invalid`);
    assertThat(typeof lesson.status === 'string' && lesson.status.length > 0, `${label}.summary.lessons[${index}].status is missing`);
    assertThat(typeof lesson.blocked === 'boolean', `${label}.summary.lessons[${index}].blocked is invalid`);
    return lesson.id;
  }).sort();
  assertThat(JSON.stringify(returnedLessonIds) === JSON.stringify(submittedLessonIds), `${label} summary.lessons do not match submitted lessonIds`);
  const selectedLessonId = returnedLessons[0]?.id;
  assertThat(selectedLessonId !== undefined && submittedLessonIds.includes(String(selectedLessonId)), `${label} summary lesson does not match submitted lessonIds`);
  const decision = detail.decision;
  assertThat(decision === undefined || decision === null, `${label}.decision must be null or absent for a newly submitted request`);
  assertThat(Array.isArray(detail.attachments), `${label}.attachments must be an array`);
  const attachments = detail.attachments.map((item, index) => canonicalAttachmentDescriptor(item, `${label}.attachments[${index}]`));
  return {
    reason: detail.reason,
    comment: detail.comment,
    kind: summary.kind,
    status: summary.status,
    origin: summary.origin,
    lessonIds: returnedLessonIds,
    selectedLessonId: String(selectedLessonId),
    decision: decision ?? null,
    attachments
  };
}

function header(response, name) {
  const value = response.headers[name.toLowerCase()];
  return Array.isArray(value) ? value.join(', ') : String(value ?? '');
}

function httpsRequest(origin, requestPath, options = {}) {
  const target = new URL(requestPath, origin);
  const headers = { Accept: 'application/json', ...(options.headers ?? {}) };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;
  if (options.cookie) headers.Cookie = options.cookie;
  const requestOptions = {
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port,
    path: `${target.pathname}${target.search}`,
    method: options.method ?? 'GET',
    headers,
    agent: options.agent,
    servername: target.hostname
  };
  return new Promise((resolve, reject) => {
    const request = https.request(requestOptions, (response) => {
      const parts = [];
      let size = 0;
      response.on('data', (part) => {
        size += part.length;
        if (size <= BODY_READ_LIMIT) parts.push(part);
      });
      response.on('end', () => resolve({
        status: response.statusCode ?? 0,
        headers: response.headers,
        body: Buffer.concat(parts),
        truncated: size > BODY_READ_LIMIT
      }));
    });
    request.setTimeout(options.timeoutMs ?? 30000, () => request.destroy(new Error('request timeout')));
    request.on('error', reject);
    if (options.body) request.end(options.body);
    else request.end();
  });
}

// The edge may send HTTP 413 and then close the upload socket with EPIPE. Keep
// that response as evidence while still rejecting transport errors that occur
// before any HTTP response (or after a successful response).
function httpsFixedOversize(origin, requestPath, options, requestTransport = https) {
  const target = new URL(requestPath, origin);
  const contentType = options.contentType
    ?? options.headers?.['Content-Type']
    ?? options.headers?.['content-type'];
  assertThat(typeof contentType === 'string' && contentType.length > 0,
    'fixed oversize request requires a non-empty Content-Type header');
  const headers = {
    Accept: 'application/json',
    ...(options.headers ?? {}),
    'Content-Type': contentType,
    ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    ...(options.cookie ? { Cookie: options.cookie } : {})
  };
  delete headers['content-type'];
  const requestOptions = {
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port,
    path: `${target.pathname}${target.search}`,
    method: 'POST',
    headers,
    agent: options.agent,
    servername: target.hostname
  };
  return new Promise((resolve, reject) => {
    const parts = [];
    let response = null;
    let responseSize = 0;
    let settled = false;
    const settleResponse = (requestError = null) => {
      if (settled) return;
      settled = true;
      resolve({
        status: response?.statusCode ?? 0,
        headers: response?.headers ?? {},
        body: Buffer.concat(parts),
        truncated: responseSize > BODY_READ_LIMIT,
        requestError
      });
    };
    const request = requestTransport.request(requestOptions, (incoming) => {
      response = incoming;
      incoming.on('data', (part) => {
        responseSize += part.length;
        if (responseSize <= BODY_READ_LIMIT) parts.push(part);
      });
      incoming.on('end', () => settleResponse());
    });
    request.setTimeout(options.timeoutMs ?? 120000, () => request.destroy(new Error('fixed request timeout')));
    request.on('error', (error) => {
      if (settled) return;
      if (response && (response.statusCode ?? 0) >= 400) {
        settleResponse(error.code ?? error.name);
      } else {
        settled = true;
        reject(error);
      }
    });
    request.end(options.body);
  });
}

function httpsChunkedOversize(origin, requestPath, options, requestTransport = https) {
  const target = new URL(requestPath, origin);
  const headers = {
    Accept: 'application/json',
    'Content-Type': options.contentType,
    'Transfer-Encoding': 'chunked',
    ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    ...(options.cookie ? { Cookie: options.cookie } : {})
  };
  const requestOptions = {
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port,
    path: `${target.pathname}${target.search}`,
    method: 'POST',
    headers,
    agent: options.agent,
    servername: target.hostname
  };
  return new Promise((resolve, reject) => {
    const parts = [];
    let response = null;
    let responseSize = 0;
    let settled = false;
    let writeOffset = 0;
    const payloadChunk = Buffer.alloc(64 * 1024, 0x58);
    const request = requestTransport.request(requestOptions, (incoming) => {
      response = incoming;
      incoming.on('data', (part) => {
        responseSize += part.length;
        if (responseSize <= BODY_READ_LIMIT) parts.push(part);
      });
      incoming.on('end', () => {
        settled = true;
        resolve({
          status: incoming.statusCode ?? 0,
          headers: incoming.headers,
          body: Buffer.concat(parts),
          truncated: responseSize > BODY_READ_LIMIT,
          requestError: null
        });
      });
    });
    request.setTimeout(options.timeoutMs ?? 120000, () => request.destroy(new Error('chunked request timeout')));
    request.on('error', (error) => {
      if (settled) return;
      if (response && (response.statusCode ?? 0) >= 400) {
        settled = true;
        resolve({
          status: response.statusCode ?? 0,
          headers: response.headers,
          body: Buffer.concat(parts),
          truncated: responseSize > BODY_READ_LIMIT,
          requestError: error.code ?? error.name
        });
      } else {
        settled = true;
        reject(error);
      }
    });
    const writeNext = () => {
      if (settled) return;
      while (writeOffset < options.size) {
        const count = Math.min(payloadChunk.length, options.size - writeOffset);
        const piece = count === payloadChunk.length ? payloadChunk : payloadChunk.subarray(0, count);
        writeOffset += count;
        if (!request.write(piece)) {
          request.once('drain', writeNext);
          return;
        }
      }
      request.end();
    };
    writeNext();
  });
}

function parseCookies(headers) {
  const values = headers['set-cookie'];
  if (!Array.isArray(values)) return '';
  return values.map((value) => value.split(';', 1)[0]).join('; ');
}

function assertNoStore(response, label) {
  const value = header(response, 'cache-control').toLowerCase();
  assertThat(value.includes('no-store'), `${label} missing Cache-Control: no-store`);
}

function makeMockTransport({ statusCode = 413, headers = { 'cache-control': 'no-store' }, body = Buffer.from('request too large'), requestError = null, earlyError = false, requestOptionsSink = null } = {}) {
  return {
    request(_options, onResponse) {
      if (requestOptionsSink) requestOptionsSink.push(_options);
      for (const [name, value] of Object.entries(_options.headers ?? {})) {
        assertThat(value !== undefined && value !== null, `Node rejected undefined header value for ${name}`);
      }
      const request = new EventEmitter();
      request.setTimeout = () => request;
      request.write = () => true;
      request.destroy = (error) => queueMicrotask(() => request.emit('error', error));
      request.end = () => queueMicrotask(() => {
        if (requestError && statusCode === 0) {
          request.emit('error', Object.assign(new Error(requestError), { code: requestError }));
          return;
        }
        const response = new EventEmitter();
        response.statusCode = statusCode;
        response.headers = headers;
        onResponse(response);
        response.emit('data', body);
        if (requestError && earlyError) {
          request.emit('error', Object.assign(new Error(requestError), { code: requestError }));
        } else if (requestError) {
          request.emit('error', Object.assign(new Error(requestError), { code: requestError }));
        } else {
          response.emit('end');
        }
      });
      return request;
    }
  };
}

async function runSelfTest() {
  const pdf = makeDeterministicPdf();
  const png = makeDeterministicPng();
  const multipart = makeMultipartBody(
    { lessonIds: ['123'], reason: 'OTHER', comment: 'runtime fixture' },
    [
      { name: 'fixture.pdf', contentType: 'application/pdf', bytes: pdf },
      { name: 'fixture.png', contentType: 'image/png', bytes: png }
    ]
  );
  const oversize = makeOversizeMultipart();
  const secrets = ['access-secret', 'password-secret'];
  const safe = redact('Authorization: Bearer access-secret password-secret', secrets);
  const pngStructure = validatePng(png);
  assertThat(pdf.length === FILE_BYTES && png.length === FILE_BYTES, 'I1 fixtures are exactly 10 MiB');
  assertThat(multipart.body.length < MAX_GATEWAY_BYTES, 'I1 multipart body is below the 24 MiB edge cap');
  assertThat(oversize.payloadBytes + oversize.prefix.length + oversize.suffix.length > MAX_GATEWAY_BYTES, 'I2 fixed/chunked body is above the 24 MiB edge cap');
  assertThat(pdf.subarray(0, 5).toString('ascii') === '%PDF-', 'I1 PDF is structurally identified');
  assertThat(png.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'I1 PNG is structurally identified');
  assertThat(!safe.includes('access-secret') && !safe.includes('password-secret'), 'self-test redaction removes credentials');
  const detailPayload = { lessonIds: ['123'], reason: 'OTHER', comment: 'runtime fixture' };
  const detailFixture = {
    summary: {
      id: 'request-1',
      kind: 'EXCUSE',
      status: 'PENDING',
      origin: 'MANUAL',
      lessons: [{ id: '123', lessonNumber: 1, status: 'scheduled', blocked: false }],
      createdAt: '2026-09-14T00:00:00Z',
      updatedAt: '2026-09-14T00:00:00Z'
    },
    reason: 'OTHER',
    comment: 'runtime fixture',
    decision: null,
    attachments: [{
      id: 'att-1',
      name: 'fixture.pdf',
      contentType: 'application/pdf',
      sizeBytes: 7,
      sha256: 'a'.repeat(64),
      state: 'ACTIVE',
      uploadedAt: '2026-09-14T00:00:00.000Z',
      expiresAt: '2026-09-15T00:00:00.000Z',
      expiredAt: null
    }, {
      id: 'att-2',
      name: 'fixture.png',
      contentType: 'image/png',
      sizeBytes: 9,
      sha256: 'b'.repeat(64),
      state: 'ACTIVE',
      uploadedAt: '2026-09-14T00:00:01.000Z',
      expiresAt: '2026-09-15T00:00:01.000Z',
      expiredAt: null
    }],
  };
  const detailEvidence = assertRequestDetailContract(detailFixture, detailPayload, 'self-test detail');
  const detailWithoutExpiredAt = JSON.parse(JSON.stringify(detailFixture));
  delete detailWithoutExpiredAt.attachments[0].expiredAt;
  assertRequestDetailContract(detailWithoutExpiredAt, detailPayload, 'detail without nullable expiredAt');
  const detailWithoutDecision = JSON.parse(JSON.stringify(detailFixture));
  delete detailWithoutDecision.decision;
  assertRequestDetailContract(detailWithoutDecision, detailPayload, 'detail without nullable decision');
  const sessionFixture = {
    sessionId: '5e0d2c6a-5cf1-4e17-8e65-40ce6e2cf420',
    userId: '42',
    displayName: 'Student fixture',
    groupLabel: 'R-101',
    sessionVersion: '7',
    rolesVersion: '3',
    activeRole: 'STUDENT',
    roles: [{ grantId: '1', role: 'STUDENT', status: 'ACTIVE', groupId: '7', contextLabel: 'R-101', selectable: true, readOnly: false }],
    readOnly: false,
    passwordPolicy: { minCodePoints: 8, maxUtf8Bytes: 64, requiresDecimalDigit: false, specialCategories: ['P', 'S'], normalization: 'NFC' }
  };
  const sessionEvidence = assertStudentSessionContract(sessionFixture, 'self-test session');
  const sessionCorruptions = [
    ['nested user shape', (value) => { delete value.userId; value.user = { id: '42' }; }],
    ['role', (value) => { value.activeRole = 'TEACHER'; }],
    ['zero id', (value) => { value.userId = '0'; }],
    ['non-decimal id', (value) => { value.userId = 'student-42'; }]
  ];
  for (const [name, corrupt] of sessionCorruptions) {
    const candidate = JSON.parse(JSON.stringify(sessionFixture));
    corrupt(candidate);
    let rejected = false;
    try { assertStudentSessionContract(candidate, `corrupt ${name}`); } catch { rejected = true; }
    assertThat(rejected, `Auth session contract accepted corrupted ${name}`);
  }
  const corruptions = [
    ['reason', (value) => { value.reason = 'ILLNESS'; }],
    ['comment', (value) => { value.comment = 'corrupted'; }],
    ['summary.status', (value) => { value.summary.status = 'SUBMITTED'; }],
    ['summary.kind', (value) => { value.summary.kind = 'LATE_CHECKIN'; }],
    ['summary.origin', (value) => { value.summary.origin = 'AUTO_GEO_FAILURE'; }],
    ['summary.lessons', (value) => { value.summary.lessons[0].id = '999'; }],
    ['summary.lessonId-alias', (value) => { delete value.summary.lessons[0].id; value.summary.lessons[0].lessonId = '123'; }],
    ['selectedLesson-extra', (value) => { value.selectedLesson = { id: '123' }; }],
    ['attachment.state', (value) => { value.attachments[0].state = 'EXPIRED'; }],
    ['attachment.state-case', (value) => { value.attachments[0].state = 'active'; }],
    ['decision', (value) => { value.decision = { comment: 'corrupted' }; }],
    ['attachment.contentType-alias', (value) => { delete value.attachments[0].contentType; value.attachments[0].content_type = 'application/pdf'; }],
    ['attachment.size-alias', (value) => { delete value.attachments[0].sizeBytes; value.attachments[0].size = 7; }],
    ['attachment.uploadedAt-alias', (value) => { delete value.attachments[0].uploadedAt; value.attachments[0].uploaded_at = '2026-09-14T00:00:00.000Z'; }],
    ['attachment.expiresAt-alias', (value) => { delete value.attachments[0].expiresAt; value.attachments[0].expires_at = '2026-09-15T00:00:00.000Z'; }],
    ['attachment.expiredAt-alias', (value) => { delete value.attachments[0].expiredAt; value.attachments[0].expired_at = '2026-09-14T12:00:00.000Z'; }],
    ['attachment.temporal-order', (value) => { value.attachments[0].uploadedAt = '2026-09-16T00:00:00.000Z'; }],
    ['attachment.expiredAt', (value) => { value.attachments[0].expiredAt = '2026-09-14T12:00:00.000Z'; }]
  ];
  for (const [name, corrupt] of corruptions) {
    const candidate = JSON.parse(JSON.stringify(detailFixture));
    corrupt(candidate);
    let rejected = false;
    try { assertRequestDetailContract(candidate, detailPayload, `corrupt ${name}`); } catch { rejected = true; }
    assertThat(rejected, `detail contract accepted corrupted ${name}`);
  }
  const fixedRequestOptions = [];
  const fixedEarlyEpipe = await httpsFixedOversize('https://mock.invalid', '/oversize', {
    headers: { 'Content-Type': 'multipart/form-data' }, body: Buffer.from('oversize')
  }, makeMockTransport({ statusCode: 413, requestError: 'EPIPE', earlyError: true, requestOptionsSink: fixedRequestOptions }));
  const chunkedEarlyEpipe = await httpsChunkedOversize('https://mock.invalid', '/oversize', {
    contentType: 'multipart/form-data', size: 7
  }, makeMockTransport({ statusCode: 413, requestError: 'EPIPE', earlyError: true }));
  assertThat(fixedEarlyEpipe.status === 413 && fixedEarlyEpipe.requestError === 'EPIPE', 'fixed 413 response was lost when the upload socket returned EPIPE');
  assertThat(chunkedEarlyEpipe.status === 413 && chunkedEarlyEpipe.requestError === 'EPIPE', 'chunked 413 response was lost when the upload socket returned EPIPE');
  assertThat(fixedRequestOptions.length === 1 && fixedRequestOptions[0].headers['Content-Type'] === 'multipart/form-data', 'fixed oversize headers-only caller did not produce a valid Node Content-Type');
  assertNoStore(fixedEarlyEpipe, 'mock fixed 413 response');
  assertNoStore(chunkedEarlyEpipe, 'mock chunked 413 response');
  let fixedTransportFailed = false;
  try {
    await httpsFixedOversize('https://mock.invalid', '/oversize', { contentType: 'multipart/form-data', body: Buffer.from('oversize') }, makeMockTransport({ statusCode: 0, requestError: 'ECONNRESET' }));
  } catch {
    fixedTransportFailed = true;
  }
  let chunkedTransportFailed = false;
  try {
    await httpsChunkedOversize('https://mock.invalid', '/oversize', { contentType: 'multipart/form-data', size: 7 }, makeMockTransport({ statusCode: 0, requestError: 'ECONNRESET' }));
  } catch {
    chunkedTransportFailed = true;
  }
  assertThat(fixedTransportFailed && chunkedTransportFailed, 'transport errors without an HTTP response were accepted');
  return {
    schema: 'rct.student-requests-probe.v1',
    status: 'PASS',
    mode: 'source-self-test',
    fixtures: {
      pdfBytes: pdf.length,
      pngBytes: png.length,
      pdfSha256: sha256(pdf),
      pngSha256: sha256(png),
      pngStructure,
      multipartBytes: multipart.body.length,
      oversizeBytes: oversize.payloadBytes + oversize.prefix.length + oversize.suffix.length
    },
    framing: { fixedUsesContentLength: true, chunkedOmitsContentLength: true, transferEncoding: 'chunked' },
    authSession: { dto: 'CurrentSessionResponse', valid: sessionEvidence, corruptionRejected: true },
    apiAttachmentFixture: detailEvidence.attachments,
    detailContract: {
      responsePayloadFields: [
        'summary.id', 'summary.lessons[].id', 'summary.kind', 'summary.status=PENDING',
        'summary.origin', 'summary.createdAt', 'summary.updatedAt',
        'reason', 'comment', 'decision=null|absent',
        'attachments[].contentType', 'attachments[].sizeBytes', 'attachments[].state=ACTIVE',
        'attachments[].uploadedAt<expiresAt', 'attachments[].expiredAt=null|absent'
      ],
      corruptionRejected: true,
      nullableExpiredAtAccepted: true
    },
    transport: { fixedEarlyEpipe413: true, chunkedEarlyEpipe413: true, noResponseErrorsFail: true, mode: 'MOCKTRANSPORT' }
  };
}

async function runRuntimeProbe(diagnosticSecrets = []) {
  const origin = argValue('--origin');
  const certPath = argValue('--ca');
  const login = argValue('--login', 'student');
  const password = argValue('--password', 'password');
  registerDiagnosticSecret(diagnosticSecrets, password);
  assertThat(origin && certPath, 'runtime probe requires --origin and --ca');
  const ca = fs.readFileSync(certPath);
  const agent = new https.Agent({ ca, rejectUnauthorized: true, keepAlive: true });
  if (hasArg('--health-only')) return runHealthOnly(origin, agent);
  let accessToken = null;
  let cookie = '';
  const loginResponse = await httpsRequest(origin, '/api/auth/login', {
    method: 'POST',
    agent,
    body: Buffer.from(JSON.stringify({ login, password }), 'utf8'),
    headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(JSON.stringify({ login, password })) }
  });
  const loginJson = jsonResponse(loginResponse);
  accessToken = loginJson?.accessToken;
  registerDiagnosticSecret(diagnosticSecrets, accessToken);
  cookie = parseCookies(loginResponse.headers);
  registerCookieSecrets(diagnosticSecrets, cookie);
  assertThat(loginResponse.status === 200 && typeof accessToken === 'string' && accessToken.length > 0, `login failed with HTTP ${loginResponse.status}`);
  const sessionResponse = await httpsRequest(origin, '/api/auth/session', { agent, token: accessToken, cookie });
  const session = jsonResponse(sessionResponse);
  assertThat(sessionResponse.status === 200, `Auth session failed with HTTP ${sessionResponse.status}`);
  const sessionEvidence = assertStudentSessionContract(session, 'Auth session');
  assertNoStore(sessionResponse, 'Auth session response');
  sessionEvidence.status = sessionResponse.status;
  if (hasArg('--i2-only')) return runOversizeOnly(origin, agent, accessToken, cookie, sessionEvidence);

  const optionsResponse = await httpsRequest(origin, '/api/v1/student/requests/options', { agent, token: accessToken, cookie });
  const options = jsonResponse(optionsResponse);
  assertThat(optionsResponse.status === 200 && options, `request options failed with HTTP ${optionsResponse.status}`);
  const eligibleLesson = (options.lessons ?? []).find((item) => item?.excuseEligible === true && item.lesson?.id);
  assertThat(eligibleLesson, 'options did not return an excuse-eligible lesson');
  const otherReason = (options.reasons ?? []).find((item) => item?.code === 'OTHER');
  assertThat(otherReason, 'options did not return the OTHER reason');
  assertThat(otherReason.commentRequired === true, 'OTHER reason must require a comment');
  const lessonId = String(eligibleLesson.lesson.id);
  const requestPayload = { lessonIds: [lessonId], reason: 'OTHER', comment: 'Deterministic Requests runtime fixture' };
  const pdf = makeDeterministicPdf();
  const png = makeDeterministicPng();
  const multipart = makeMultipartBody(requestPayload, [
    { name: 'fixture.pdf', contentType: 'application/pdf', bytes: pdf },
    { name: 'fixture.png', contentType: 'image/png', bytes: png }
  ]);
  const idempotencyKey = 'rct-requests-runtime-i1-20260913-fixed-key';
  const postOptions = {
    method: 'POST',
    agent,
    token: accessToken,
    cookie,
    body: multipart.body,
    headers: {
      'Content-Type': multipart.contentType,
      'Content-Length': multipart.body.length,
      'Idempotency-Key': idempotencyKey
    }
  };
  let firstResponse;
  try {
    firstResponse = await httpsRequest(origin, '/api/v1/student/requests/excuse', postOptions);
  } catch (error) {
    firstResponse = await httpsRequest(origin, '/api/v1/student/requests/excuse', postOptions);
    firstResponse.retriedAfterAmbiguousTransport = error.code ?? error.name;
  }
  const firstJson = jsonResponse(firstResponse);
  assertThat(firstResponse.status === 200 && firstJson?.summary?.id, `I1 create failed with HTTP ${firstResponse.status}`);
  assertNoStore(firstResponse, 'I1 response');
  assertThat(Array.isArray(firstJson.attachments) && firstJson.attachments.length === 2, 'I1 response must contain two attachments');
  const detailEvidence = assertRequestDetailContract(firstJson, requestPayload);
  const expectedFiles = [
    { bytes: pdf, name: 'fixture.pdf', contentType: 'application/pdf' },
    { bytes: png, name: 'fixture.png', contentType: 'image/png' }
  ];
  const responseAttachments = firstJson.attachments.map((item, index) => canonicalAttachmentDescriptor(item, `I1 response attachments[${index}]`));
  const expectedDigests = responseAttachments.map((descriptor) => ({
    ...descriptor,
    sizeBytes: descriptor.sizeBytes
  }));
  for (const expected of expectedFiles) {
    const actual = expectedDigests.find((item) => item.name === expected.name);
    assertThat(actual && actual.sizeBytes === expected.bytes.length && actual.sha256 === sha256(expected.bytes)
      && actual.contentType === expected.contentType, `I1 metadata mismatch for ${expected.name}`);
  }
  const downloaded = [];
  for (const attachment of firstJson.attachments) {
    const streamed = await downloadAttachment(origin, `/api/v1/student/requests/${firstJson.summary.id}/attachments/${attachment.id}`, { agent, token: accessToken, cookie });
    assertThat(streamed.status === 200, `I1 attachment download failed with HTTP ${streamed.status}`);
    assertThat(streamed.cacheControl.toLowerCase().includes('no-store'), `I1 attachment download missing no-store for ${attachment.name}`);
    const attachmentSize = attachment.sizeBytes;
    assertThat(streamed.bytes === attachmentSize && streamed.sha256 === attachment.sha256, `I1 downloaded bytes mismatch for ${attachment.name}`);
    downloaded.push({ id: attachment.id, bytes: streamed.bytes, sha256: streamed.sha256 });
  }
  const replayResponse = await httpsRequest(origin, '/api/v1/student/requests/excuse', postOptions);
  const replayJson = jsonResponse(replayResponse);
  assertThat(replayResponse.status === 200 && replayJson?.summary?.id === firstJson.summary.id, 'I1 same-key replay did not return the original request');
  assertNoStore(replayResponse, 'I1 replay response');
  assertThat(JSON.stringify(replayJson.attachments) === JSON.stringify(firstJson.attachments), 'I1 replay attachment metadata changed');
  if (hasArg('--i1-only')) {
    return {
      schema: 'rct.student-requests-probe.v1',
      status: 'PASS',
      mode: 'runtime-i1',
      i1: {
        status: 'PASS',
        lessonId,
        reason: otherReason.code,
        requestId: firstJson.summary.id,
        idempotencyReplaySameId: true,
        session: sessionEvidence,
        detail: detailEvidence,
        files: expectedDigests,
        downloaded,
        responseCacheControl: header(firstResponse, 'cache-control')
      }
    };
  }

  const oversize = makeOversizeMultipart();
  const fixedOversizeResponse = await httpsFixedOversize(origin, '/api/v1/student/requests/excuse', {
    method: 'POST', agent, token: accessToken, cookie,
    contentType: oversize.contentType,
    body: Buffer.concat([oversize.prefix, Buffer.alloc(oversize.payloadBytes, 0x58), oversize.suffix]),
    headers: {
      'Content-Type': oversize.contentType,
      'Content-Length': oversize.payloadBytes + oversize.prefix.length + oversize.suffix.length,
      'Idempotency-Key': 'rct-requests-runtime-i2-fixed-20260913'
    }
  });
  const chunkedOversizeResponse = await httpsChunkedOversize(origin, '/api/v1/student/requests/excuse', {
    agent, token: accessToken, cookie, contentType: oversize.contentType, size: oversize.payloadBytes + oversize.prefix.length + oversize.suffix.length
  });
  assertThat(fixedOversizeResponse.status === 413, `I2 fixed request expected HTTP 413, got ${fixedOversizeResponse.status}`);
  assertThat(chunkedOversizeResponse.status === 413, `I2 chunked request expected HTTP 413, got ${chunkedOversizeResponse.status}`);
  assertNoStore(fixedOversizeResponse, 'I2 fixed response');
  assertNoStore(chunkedOversizeResponse, 'I2 chunked response');
  return {
    schema: 'rct.student-requests-probe.v1',
    status: 'PASS',
    mode: 'runtime',
    i1: {
      status: 'PASS',
      lessonId,
      reason: otherReason.code,
      requestId: firstJson.summary.id,
      idempotencyReplaySameId: true,
      session: sessionEvidence,
      detail: detailEvidence,
      files: expectedDigests,
      downloaded,
      responseCacheControl: header(firstResponse, 'cache-control')
    },
    i2: {
      status: 'PASS',
      bodyBytes: oversize.payloadBytes + oversize.prefix.length + oversize.suffix.length,
      fixed: { status: fixedOversizeResponse.status, cacheControl: header(fixedOversizeResponse, 'cache-control'), requestError: fixedOversizeResponse.requestError },
      chunked: { status: chunkedOversizeResponse.status, cacheControl: header(chunkedOversizeResponse, 'cache-control'), requestError: chunkedOversizeResponse.requestError }
    }
  };
}

async function runHealthOnly(origin, agent) {
  const response = await httpsRequest(origin, '/health', { agent, timeoutMs: 10000 });
  assertThat(response.status === 200, `HTTPS health probe failed with HTTP ${response.status}`);
  return { schema: 'rct.student-requests-probe.v1', status: 'PASS', mode: 'health-only', statusCode: response.status };
}

async function runOversizeOnly(origin, agent, accessToken, cookie, sessionEvidence) {
  const oversize = makeOversizeMultipart();
  const size = oversize.payloadBytes + oversize.prefix.length + oversize.suffix.length;
  const fixedOversizeResponse = await httpsFixedOversize(origin, '/api/v1/student/requests/excuse', {
    method: 'POST', agent, token: accessToken, cookie,
    contentType: oversize.contentType,
    body: Buffer.concat([oversize.prefix, Buffer.alloc(oversize.payloadBytes, 0x58), oversize.suffix]),
    headers: {
      'Content-Type': oversize.contentType,
      'Content-Length': size,
      'Idempotency-Key': 'rct-requests-runtime-i2-fixed-20260913'
    }
  });
  const chunkedOversizeResponse = await httpsChunkedOversize(origin, '/api/v1/student/requests/excuse', {
    agent, token: accessToken, cookie, contentType: oversize.contentType, size
  });
  assertThat(fixedOversizeResponse.status === 413, `I2 fixed request expected HTTP 413, got ${fixedOversizeResponse.status}`);
  assertThat(chunkedOversizeResponse.status === 413, `I2 chunked request expected HTTP 413, got ${chunkedOversizeResponse.status}`);
  assertNoStore(fixedOversizeResponse, 'I2 fixed response');
  assertNoStore(chunkedOversizeResponse, 'I2 chunked response');
  return {
    schema: 'rct.student-requests-probe.v1',
    status: 'PASS',
    mode: 'runtime-i2',
    i2: {
      status: 'PASS',
      session: sessionEvidence,
      bodyBytes: size,
      fixed: { status: fixedOversizeResponse.status, cacheControl: header(fixedOversizeResponse, 'cache-control'), requestError: fixedOversizeResponse.requestError },
      chunked: { status: chunkedOversizeResponse.status, cacheControl: header(chunkedOversizeResponse, 'cache-control'), requestError: chunkedOversizeResponse.requestError }
    }
  };
}

function downloadAttachment(origin, requestPath, options) {
  const target = new URL(requestPath, origin);
  return new Promise((resolve, reject) => {
    const request = https.request({
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port,
      path: `${target.pathname}${target.search}`,
      method: 'GET',
      headers: { Accept: 'application/octet-stream', Authorization: `Bearer ${options.token}`, Cookie: options.cookie },
      agent: options.agent,
      servername: target.hostname
    }, (response) => {
      const digest = crypto.createHash('sha256');
      let bytes = 0;
      response.on('data', (part) => { bytes += part.length; digest.update(part); });
      response.on('end', () => resolve({
        status: response.statusCode ?? 0,
        bytes,
        sha256: digest.digest('hex'),
        cacheControl: header({ headers: response.headers }, 'cache-control'),
        contentType: header({ headers: response.headers }, 'content-type')
      }));
    });
    request.setTimeout(120000, () => request.destroy(new Error('attachment download timeout')));
    request.on('error', reject);
    request.end();
  });
}

async function main() {
  const diagnosticSecrets = [];
  try {
    const result = hasArg('--self-test') ? await runSelfTest() : await runRuntimeProbe(diagnosticSecrets);
    process.stdout.write(`${redact(result, diagnosticSecrets)}\n`);
  } catch (error) {
    process.stdout.write(`${redact({ schema: 'rct.student-requests-probe.v1', status: 'FAIL', error: error.message }, diagnosticSecrets)}\n`);
    process.exitCode = 1;
  }
}

main();
