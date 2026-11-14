import type { StoryLanguage } from './story-source'
const keywords = new Set(
  'abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new non-sealed null package permits private protected public record return sealed short static strictfp super switch synchronized this throw throws transient try var void volatile while yield true false'.split(
    ' ',
  ),
)

const pieces =
  /\/\/[^\r\n]*|\/\*.*?\*\/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@[A-Za-z_$][\w$]*|\b(?:0[xX][0-9A-Fa-f_]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[Ee][+-]?\d[\d_]*)?[lLfFdD]?)\b|[A-Za-z_$][\w$]*|->|::|==|!=|&&|\|\||[=<>!?:+*%&|^~-]|\s+|./gu

type TokenKind =
  | 'plain'
  | 'keyword'
  | 'annotation'
  | 'string'
  | 'comment'
  | 'number'
  | 'type'
  | 'method'
  | 'operator'
export interface JavaToken {
  text: string
  kind: TokenKind
}

function kind(text: string, after: string): TokenKind {
  if (text.startsWith('//') || text.startsWith('/*')) return 'comment'
  if (text.startsWith('"') || text.startsWith("'")) return 'string'
  if (text.startsWith('@')) return 'annotation'
  if (keywords.has(text)) return 'keyword'
  if (/^\d/.test(text)) return 'number'
  if (/^[A-Z][\w$]+$/.test(text)) return 'type'
  if (/^[A-Za-z_$]/.test(text) && /^\s*\(/.test(after)) return 'method'
  if (/^[=<>!?:+*%&|^~\-]/.test(text)) return 'operator'
  return 'plain'
}

/** Lexes the displayed excerpts only. The UI renders token text through Vue. */
export function tokenizeJavaLine(line: string): JavaToken[] {
  return Array.from(line.matchAll(pieces), (match) => {
    const text = match[0]
    return { text, kind: kind(text, line.slice(match.index + text.length)) }
  })
}


const keywordsByLanguage: Record<Exclude<StoryLanguage, 'java'>, ReadonlySet<string>> = {
  sql: new Set('ADD ALTER AND AS ASC BY CASCADE CHECK COLUMN CONSTRAINT CREATE DEFAULT DELETE DESC EXCLUDE EXISTS FALSE FOREIGN FROM GENERATED IDENTITY IF IN INDEX IS KEY NOT NULL ON OR PRIMARY REFERENCES RESTRICT SELECT SET TABLE TRUE UNIQUE UPDATE USING WHERE WITH'.split(' ')),
  json: new Set(['true', 'false', 'null']),
  http: new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HTTP']),
  proto: new Set('service rpc returns message enum bytes string int32 int64 bool oneof repeated optional'.split(' ')),
  config: new Set('location listen proxy_pass proxy_set_header set true false null endpoint forward_to'.split(' ')),
  css: new Set(['var', 'tabular-nums']),
  typescript: new Set('async await const let function if return throw new as null undefined true false'.split(' ')),
  python: new Set('async await def if else except for in return try True False None lambda'.split(' ')),
}
const otherPieces = /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|https?:\/\/[^\s"']+|\/\/[^\r\n]*|#[^\r\n]*|\b-?\d+(?:\.\d+)?\b|[A-Za-z_$][\w$-]*|[=:<>+*]|\s+|./gu

const sqlPieces = /--[^\r\n]*|'(?:''|[^'])*'|"(?:""|[^"])*"|\b\d+(?:\.\d+)?\b|[A-Za-z_$][\w$]*|[=<>+*]|\s+|./gu

export function tokenizeStoryLine(line: string, language: StoryLanguage): JavaToken[] {
  if (language === 'java') return tokenizeJavaLine(line)
  return Array.from(line.matchAll(language === 'sql' ? sqlPieces : otherPieces), (match) => {
    const text = match[0]
    const tail = line.slice(match.index + text.length)
    let kind: TokenKind = 'plain'
    if (text.startsWith('#') || text.startsWith('//') || (language === 'sql' && text.startsWith('--'))) kind = 'comment'
    else if (keywordsByLanguage[language].has(language === 'sql' ? text.toUpperCase() : text)) kind = 'keyword'
    else if ((language === 'json' || language === 'config' || language === 'http' || language === 'css') && /^\s*:/.test(tail)) kind = 'type'
    else if (text.startsWith('"') || text.startsWith("'") || /^https?:/.test(text)) kind = 'string'
    else if (/^-?\d/.test(text)) kind = 'number'
    else if (/^[=:<>+*]/.test(text)) kind = 'operator'
    return { text, kind }
  })
}
