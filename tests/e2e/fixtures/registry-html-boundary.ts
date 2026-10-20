import type { Page } from '@playwright/test';

/** Whole-output privacy survives parser-discarded tokens such as duplicate attributes. */
export function registryRawPrivacyBoundary({ content, forbiddenValues }: {
  content: string; forbiddenValues: readonly string[];
}): boolean {
  const named: Record<string, string> = {
    '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'",
  };
  let decoder: HTMLTextAreaElement | undefined;
  let complete = true;
  const decode = (entity: string): string => {
    // In the real browser decode each entity in isolation; raw markup is never mounted or reparsed.
    if (typeof document !== 'undefined') {
      decoder ??= document.createElement('textarea');
      decoder.innerHTML = entity;
      return decoder.value;
    }
    if (Object.hasOwn(named, entity)) return named[entity];
    const numeric = /^&#(x[0-9a-f]+|[0-9]+);?$/i.exec(entity);
    if (numeric) {
      const code = numeric[1][0].toLowerCase() === 'x'
        ? Number.parseInt(numeric[1].slice(1), 16) : Number.parseInt(numeric[1], 10);
      return Number.isSafeInteger(code) && code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff)
        ? String.fromCodePoint(code) : '\ufffd';
    }
    // Browser-free controls intentionally have no named-entity parser; unsupported cases fail closed.
    complete = false;
    return entity;
  };
  const privateText = (value: string) => /парол|password|password-reset/i.test(value) ||
    forbiddenValues.some((forbidden) => !forbidden.length || value.includes(forbidden));
  let decoded = content;
  for (let pass = 0; pass < 8; pass++) {
    if (privateText(decoded)) return false;
    const next = decoded.replace(/&(?:#[xX][0-9a-fA-F]+;?|#[0-9]+;?|[A-Za-z][A-Za-z0-9]*;?)/g, decode);
    if (next === decoded) return complete;
    decoded = next;
  }
  // Excessively nested entity encoding cannot become a privacy PASS.
  return false;
}

/** Inert downloaded-document reader. Only closed booleans cross the browser boundary. */
export async function registryTableBoundary(
  page: Page,
  content: string,
  columns: readonly string[],
  expectedRows: readonly (readonly string[])[],
  forbiddenValues: readonly string[],
) {
  const wholeOutputPrivate = await page.evaluate(registryRawPrivacyBoundary, { content, forbiddenValues: [...forbiddenValues] });
  return page.evaluate(({ content, columns, expectedRows, forbiddenValues, wholeOutputPrivate }) => {
    const doc = new DOMParser().parseFromString(content, 'text/html');
    const tables = [...doc.querySelectorAll('table')];
    const table = tables[0];
    const only = (parent: Element | null | undefined, tags: readonly string[]) =>
      !!parent && [...parent.childNodes].every((node) =>
        node.nodeType === Node.TEXT_NODE ? !node.textContent?.trim() :
          node.nodeType === Node.COMMENT_NODE ||
          (node.nodeType === Node.ELEMENT_NODE && tags.includes((node as Element).tagName)));
    const allowedNodes = new Set(['HTML', 'HEAD', 'BODY', 'META', 'TITLE', 'STYLE', 'H1', 'P', 'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD']);
    const safeAttribute = (element: Element, attribute: Attr) => {
      if (['class', 'id', 'lang', 'scope', 'role', 'title'].includes(attribute.name) ||
        /^aria-(?:label|labelledby|describedby|hidden)$/.test(attribute.name)) return true;
      return element.tagName === 'META' && attribute.name === 'charset' && /^utf-8$/i.test(attribute.value);
    };
    const nodesSafe = [...doc.querySelectorAll('*')].every((element) =>
      allowedNodes.has(element.tagName) && [...element.attributes].every((attr) => safeAttribute(element, attr))) &&
      [...doc.querySelectorAll('style')].every((style) => !/@import|url\s*\(|expression\s*\(/i.test(style.textContent ?? ''));
    const head = table?.querySelector(':scope > thead');
    const body = table?.querySelector(':scope > tbody');
    const headerRows = head ? [...head.children] : [];
    const bodyRows = body ? [...body.children] : [];
    const cells = (row: Element, tag: string) => [...row.children]
      .filter((cell) => cell.tagName === tag).map((cell) => cell.textContent ?? '');
    const headers = headerRows[0] ? cells(headerRows[0], 'TH') : [];
    const rows = bodyRows.map((row) => cells(row, 'TD'));
    const textOnly = (row: Element, tag: string) => row.tagName === 'TR' && only(row, [tag]) &&
      [...row.children].every((cell) => cell.tagName === tag && [...cell.childNodes].every((node) =>
        node.nodeType === Node.TEXT_NODE));
    const tableShape = nodesSafe && doc.doctype?.name === 'html' && tables.length === 1 &&
      only(doc.body, ['H1', 'P', 'TABLE']) && only(table, ['THEAD', 'TBODY']) && table?.children.length === 2 &&
      table.children[0] === head && table.children[1] === body && only(head, ['TR']) && only(body, ['TR']) &&
      headerRows.length === 1 && headerRows.every((row) => textOnly(row, 'TH')) &&
      bodyRows.every((row) => textOnly(row, 'TD'));
    const same = (left: readonly string[], right: readonly string[]) =>
      left.length === right.length && left.every((value, index) => value === right[index]);
    // Include decoded attributes and comments, not just visible cell text, in privacy checks.
    const walker = doc.createTreeWalker(doc, NodeFilter.SHOW_ALL);
    const values: string[] = [];
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      if (node.nodeValue) values.push(node.nodeValue);
      if (node.nodeType === Node.ELEMENT_NODE)
        values.push(...[...(node as Element).attributes].map((attr) => attr.value));
    }
    const allData = values.join('\n');
    return {
      tableShape: !!tableShape,
      columnsExact: same(headers, columns),
      rowsExact: rows.length === expectedRows.length && rows.every((row, index) => same(row, expectedRows[index])),
      oneRow: rows.length === 1,
      forbiddenAbsent: wholeOutputPrivate && forbiddenValues.every((value) => value.length > 0 && !allData.includes(value)) &&
        !/парол|password|password-reset/i.test(allData),
    };
  }, { content, columns: [...columns], expectedRows: expectedRows.map((row) => [...row]), forbiddenValues: [...forbiddenValues], wholeOutputPrivate });
}

/** Cheap native-parser sensitivity controls in the already owned browser context. */
export async function registryTableParserControls(page: Page): Promise<boolean> {
  const header = '<!doctype html><html><head><title>Реестр</title></head><body>';
  const body = '<table class="safe"><thead>\n<tr><th scope="col">Имя</th></tr>\n</thead>\n<tbody><tr><td>A &amp; B</td></tr></tbody></table>';
  const content = header + body + '</body></html>';
  const read = (html: string) => registryTableBoundary(page, html, ['Имя'], [['A & B']], ['synthetic-secret']);
  const valid = await read(content);
  if (!(valid.tableShape && valid.columnsExact && valid.rowsExact && valid.forbiddenAbsent)) return false;
  for (const malformed of [
    content.replace('<td>A &amp; B</td>', '<td>A &amp; B</td><td>extra</td>'),
    content.replace('<tr><td>A &amp; B</td></tr>', ''),
    content.replace('</tbody>', '<tr><td>A &amp; B</td></tr></tbody>'),
    content.replace('</table>', '</table>' + body),
    content.replace('A &amp; B', '<span>A &amp; B</span>'),
    content.replace('</body>', '<script>void 0</script></body>'),
    content.replace('class="safe"', 'onclick="void 0"'),
    content.replace('class="safe"', 'title="synthetic&#45;secret"'),
    content.replace('class="safe"', 'class="safe" title="public" title="synthetic&#45;secret"'),
    content.replace('</body>', '<input value="extra-data"></body>'),
  ]) {
    const value = await read(malformed);
    if (value.tableShape && value.columnsExact && value.rowsExact && value.forbiddenAbsent) return false;
  }
  return true;
}
