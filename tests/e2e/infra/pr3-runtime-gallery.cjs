#!/usr/bin/env node
'use strict';
// Actual runtime captures only. No routing mocks, fixture pages, or token artifacts.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { createRequire } = require('node:module');

function args(argv) {
  const result = { command: argv[0] };
  for (let i = 1; i < argv.length; i += 2) {
    if (!argv[i]?.startsWith('--') || !argv[i + 1]) throw new Error('Expected --option value');
    result[argv[i].slice(2)] = argv[i + 1];
  }
  return result;
}
function read(file) { return JSON.parse(fs.readFileSync(file, 'utf8')); }
function write(file, value) { fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n'); }
function git(root, ...command) { return execFileSync('git', ['-C', root, ...command], { encoding: 'utf8' }).trim(); }
function assert(value, message) { if (!value) throw new Error(message); }
function id(entry) { return `${entry.screenId}/${entry.stateId}/${entry.theme}/${entry.width}`; }
function sourceCheck(plan) {
  assert(/^[a-f0-9]{40}$/.test(plan.source.revision), 'Exact 40-character source revision required');
  assert(git(plan.source.root, 'rev-parse', 'HEAD') === plan.source.revision, 'Source HEAD differs from frozen revision');
  assert(plan.source.paths?.length, 'Explicit source paths required');
  assert(!git(plan.source.root, 'status', '--porcelain', '--', ...plan.source.paths), 'Source paths must be clean before/after capture');
  assert(plan.runtime.provenance === 'real-runtime' && plan.runtime.synthetic === true, 'Only actual synthetic runtime is supported');
  assert(plan.runtime.backendScope?.length, 'Real backend scope required');
  assert(/^[a-f0-9]{40}$/.test(plan.runtime.backendSourceRevision), 'Exact backend source revision required');
}
function screenshotPath(root, relative) {
  assert(typeof relative === 'string' && !relative.includes('\\') && !path.isAbsolute(relative), 'Screenshot path must be relative');
  assert(relative.split('/').every(part => part && part !== '.' && part !== '..'), 'Unsafe screenshot path');
  const resolved = path.resolve(root, relative);
  assert(fs.realpathSync(resolved).startsWith(fs.realpathSync(root) + path.sep), 'Screenshot must remain inside gallery');
  return resolved;
}
function pngInfo(file) {
  const data = fs.readFileSync(file);
  assert(data.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'Expected PNG signature');
  assert(data.toString('ascii', 12, 16) === 'IHDR', 'Expected PNG IHDR');
  return { width: data.readUInt32BE(16), height: data.readUInt32BE(20), bytes: data.length, sha256: crypto.createHash('sha256').update(data).digest('hex') };
}
function verify(manifest, directory) {
  assert(manifest.schemaVersion === 1 && Array.isArray(manifest.entries), 'Unsupported manifest');
  const seen = new Set();
  for (const entry of manifest.entries) {
    assert(entry.id === id(entry) && !seen.has(entry.id), 'Stable ID must be unique screen/state/theme/width');
    seen.add(entry.id);
    assert(/^[A-Z0-9_-]+$/.test(entry.screenId) && /^[a-z0-9_-]+$/.test(entry.stateId), 'Invalid stable screen/state ID');
    assert(['dark', 'light', 'system'].includes(entry.theme) && Number.isInteger(entry.width) && entry.width > 0, 'Invalid theme/width');
    assert(entry.title && entry.description && entry.sourceRevision === manifest.source.revision, 'Missing title/description or inconsistent source revision');
    assert(['captured', 'blocked', 'unknown'].includes(entry.status), 'Invalid capture status');
    if (entry.status === 'captured') {
      assert(entry.provenance === 'real-runtime' && entry.capturedAt && entry.url, 'Runtime provenance required');
      const png = pngInfo(screenshotPath(directory, entry.screenshot));
      assert(png.width === entry.imageWidth && png.height === entry.imageHeight && png.bytes === entry.bytes && png.sha256 === entry.sha256, 'PNG dimensions/hash/manifest mismatch');
    } else {
      assert(!entry.screenshot && entry.reason, 'Unknown/blocked entries need a reason and no claimed screenshot');
    }
  }
  return { entries: seen.size, captured: manifest.entries.filter(e => e.status === 'captured').length };
}
function escape(value) { return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]); }
function generate(manifest, directory) {
  const result = verify(manifest, directory);
  const cards = manifest.entries.map(entry => `<article data-search="${escape(`${entry.id} ${entry.title} ${entry.status}`.toLowerCase())}"><header><span class="tag ${entry.status}">${escape(entry.status)}</span><h2>${escape(entry.title)}</h2><code>${escape(entry.id)}</code></header><p>${escape(entry.description)}</p>${entry.status === 'captured' ? `<a href="${escape(entry.screenshot)}"><img loading="lazy" src="${escape(entry.screenshot)}" width="${entry.width}" height="${entry.imageHeight}" alt="${escape(entry.title)}"></a><p>${escape(entry.capturedAt)} · ${entry.width}×${entry.imageHeight} · <a href="${escape(entry.screenshot)}" download>PNG</a></p>` : `<p class="reason">${escape(entry.reason)}</p>`}${entry.limitations?.length ? `<ul>${entry.limitations.map(item => `<li>${escape(item)}</li>`).join('')}</ul>` : ''}<details><summary>Runtime evidence</summary><pre>${escape(JSON.stringify({ sourceRevision: entry.sourceRevision, url: entry.url, http: entry.http, javascriptErrors: entry.javascriptErrors }, null, 2))}</pre></details></article>`).join('\n');
  const html = `<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>PR3 · Runtime gallery</title><style>body{font:16px system-ui;margin:0;background:#101216;color:#e5e7eb}main{max-width:1400px;margin:auto;padding:24px}h1{margin-bottom:8px}h2{font-size:20px}p,li{line-height:1.5}a{color:#8cbcff}code,pre{overflow-wrap:anywhere;white-space:pre-wrap}input{font:inherit;width:min(90%,600px);padding:12px;border:1px solid #677080;border-radius:8px;background:#1a1e26;color:inherit}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,420px),1fr));gap:24px;margin-top:24px}article{background:#1a1e26;border:1px solid #353c49;border-radius:12px;padding:18px;min-width:0}article img{display:block;width:100%;height:auto;max-height:700px;object-fit:contain;object-position:top;border:1px solid #353c49}.tag{font-size:13px;border:1px solid #8291a5;padding:3px 8px;border-radius:5px}.blocked,.reason{color:#ffc280}.unknown{color:#c9cdd5}details{margin-top:12px}pre{font-size:12px}small{display:block}article[hidden]{display:none}</style><main><h1>PR3 · Runtime gallery</h1><p>Снято в реальном синтетическом стенде. Captured означает сохранённый снимок; это не утверждение о полной продуктовой или визуальной приёмке. Unknown/blocked не считаются успешными.</p><p>${result.captured}/${result.entries} снимков · ${escape(manifest.generatedAt)} · <a href="manifest.json" download>Manifest JSON</a></p><p>Frontend revision: <code>${escape(manifest.source.revision)}</code><br>Backend revision: <code>${escape(manifest.runtime.backendSourceRevision)}</code><br>Real backend scope: ${escape(manifest.runtime.backendScope.join(', '))}</p>${manifest.runtime.limitations?.length ? `<ul>${manifest.runtime.limitations.map(item => `<li>${escape(item)}</li>`).join('')}</ul>` : ''}<label>Поиск по экрану, состоянию, теме, ширине и статусу<br><input id="filter" type="search" placeholder="Например: homework dark 1440"></label><div class="grid">${cards}</div><p>Открой index.html локально рядом с manifest.json и папкой screenshots. Изображения отдельные; публикация сайта не требуется.</p></main><script>document.querySelector('#filter').addEventListener('input',event=>{const terms=event.target.value.toLowerCase().split(/\\s+/).filter(Boolean);for(const card of document.querySelectorAll('article'))card.hidden=!terms.every(term=>card.dataset.search.includes(term));});</script></html>`;
  assert(Buffer.byteLength(html) < 1024 * 1024, 'Gallery HTML must stay below 1 MiB');
  fs.writeFileSync(path.join(directory, 'index.html'), html);
  return result;
}
function locator(page, action) {
  if (action.label) return page.getByLabel(action.label, { exact: action.exact ?? false });
  if (action.role) return page.getByRole(action.role, { name: action.name, exact: true });
  assert(action.selector, 'Action requires selector, label, or role');
  return page.locator(action.selector);
}
async function action(page, step) {
  let response;
  if (step.expectResponse) {
    response = page.waitForResponse(r => new URL(r.url()).pathname === step.expectResponse.path && r.request().method() === (step.expectResponse.method || 'GET'), { timeout: 20000 });
    response.catch(() => {}); // Keep a failed action from leaving an unhandled waiter.
  }
  const target = step.type === 'goto' ? null : locator(page, step);
  if (step.type === 'goto') await page.goto(step.url, { waitUntil: 'domcontentloaded' });
  else if (step.type === 'fill') await target.fill(step.value);
  else if (step.type === 'click') await target.click();
  else if (step.type === 'select') await target.selectOption(step.value);
  else if (step.type === 'check') await target.setChecked(step.checked);
  else if (step.type === 'wait') await target.waitFor({ state: step.state || 'visible' });
  else if (step.type === 'expectChecked') assert(await target.isChecked() === step.checked, 'Checkbox state differs from named state');
  else throw new Error(`Unsupported action ${step.type}`);
  if (response) assert((await response).status() === step.expectResponse.status, 'Expected API status differs');
}
async function capture(options) {
  const plan = read(options.plan);
  sourceCheck(plan);
  assert(plan.scenarios?.length, 'Capture plan needs actual named scenarios');
  const directory = path.resolve(options.out);
  fs.mkdirSync(path.join(directory, 'screenshots'), { recursive: true });
  assert(!fs.existsSync(path.join(directory, 'manifest.json')), 'Use a new output directory; previous capture evidence is immutable');
  const requirePlaywright = createRequire(path.join(path.resolve(options['playwright-root']), 'package.json'));
  const { chromium } = requirePlaywright('playwright');
  const browser = await chromium.launch({ headless: true, ...(options.chromium ? { executablePath: options.chromium } : {}) });
  const manifest = { schemaVersion: 1, generatedAt: new Date().toISOString(), source: plan.source, runtime: plan.runtime, harnessRevision: git(path.resolve(__dirname, '../../..'), 'rev-parse', 'HEAD'), browserVersion: browser.version(), browserSettings: { serviceWorkers: 'block', timezoneId: 'Europe/Moscow', locale: 'ru-RU', fullPage: true }, entries: [] };
  const sessions = new Map(); // Reuse one in-memory login for related named states.
  try {
    for (const scenario of plan.scenarios) {
      const entry = { screenId: scenario.screenId, stateId: scenario.stateId, title: scenario.title, description: scenario.description, theme: scenario.theme, width: scenario.width, sourceRevision: plan.source.revision, limitations: scenario.limitations || [], status: 'unknown' };
      entry.id = id(entry);
      if (scenario.skipReason) { entry.reason = scenario.skipReason; manifest.entries.push(entry); continue; }
      let session = scenario.sessionGroup && sessions.get(scenario.sessionGroup);
      if (!session) {
        const context = await browser.newContext({ viewport: { width: scenario.width, height: scenario.height || 1000 }, colorScheme: scenario.theme === 'light' ? 'light' : 'dark', locale: 'ru-RU', timezoneId: 'Europe/Moscow', serviceWorkers: 'block' });
        session = { context, page: await context.newPage() };
        if (scenario.sessionGroup) sessions.set(scenario.sessionGroup, session);
      }
      const { context, page } = session;
      await page.setViewportSize({ width: scenario.width, height: scenario.height || 1000 });
      await page.emulateMedia({ colorScheme: scenario.theme === 'light' ? 'light' : 'dark' });
      const http = [], errors = [];
      page.on('pageerror', error => errors.push(error.name));
      page.on('response', response => { const url = new URL(response.url()); if (url.pathname.startsWith('/api/')) http.push({ method: response.request().method(), path: url.pathname, status: response.status() }); });
      let stepIndex = -1;
      try {
        for (const step of scenario.actions) { stepIndex++; await action(page, step); }
        await page.evaluate(() => document.fonts.ready);
        await page.waitForTimeout(200); // Let the actual UI's 150ms theme transition settle.
        assert(errors.length === 0, 'Page JavaScript errors present');
        sourceCheck(plan);
        const relative = `screenshots/${entry.id.replaceAll('/', '__')}.png`;
        const file = path.join(directory, relative);
        await page.screenshot({ path: file, fullPage: true, animations: 'disabled' });
        sourceCheck(plan);
        const image = pngInfo(file);
        const layout = await page.evaluate(() => ({ horizontalOverflow: document.documentElement.scrollWidth > innerWidth, rootFontSize: getComputedStyle(document.documentElement).fontSize, resolvedColorScheme: getComputedStyle(document.documentElement).colorScheme }));
        Object.assign(entry, image, layout, { width: scenario.width, imageWidth: image.width, viewportHeight: scenario.height || 1000, imageHeight: image.height, screenshot: relative, status: 'captured', provenance: 'real-runtime', capturedAt: new Date().toISOString(), url: page.url(), http, javascriptErrors: errors });
        delete entry.height;
      } catch (error) {
        Object.assign(entry, { status: 'blocked', reason: `Action ${stepIndex + 1} (${scenario.actions[stepIndex]?.type || 'capture'}) failed: ${error.name}`, http, javascriptErrors: errors });
      } finally {
        for (const step of scenario.cleanup || []) { try { await action(page, step); } catch { entry.limitations.push('Cleanup did not complete; check owned synthetic state/session.'); } }
        page.removeAllListeners('pageerror');
        page.removeAllListeners('response');
        if (!scenario.sessionGroup) await context.close();
      }
      manifest.entries.push(entry);
      write(path.join(directory, 'manifest.json'), manifest);
      console.log(`${entry.status} ${entry.id}`);
    }
  } finally { await browser.close(); }
  sourceCheck(plan);
  write(path.join(directory, 'manifest.json'), manifest);
  console.log(JSON.stringify(generate(manifest, directory)));
}
async function main() {
  const options = args(process.argv.slice(2));
  if (options.command === 'capture') return capture(options);
  if (['verify', 'generate'].includes(options.command)) {
    const file = path.resolve(options.manifest);
    const manifest = read(file), directory = path.dirname(file);
    console.log(JSON.stringify(options.command === 'generate' ? generate(manifest, directory) : verify(manifest, directory)));
    return;
  }
  console.log('Usage:\n  node pr3-runtime-gallery.cjs capture --plan PLAN.json --out NEW_DIR --playwright-root /repo/tests/e2e [--chromium /usr/bin/chromium]\n  node pr3-runtime-gallery.cjs generate --manifest DIR/manifest.json\n  node pr3-runtime-gallery.cjs verify --manifest DIR/manifest.json\n\nPlan: {source:{root,revision,paths:[]},runtime:{provenance:"real-runtime",synthetic:true,backendSourceRevision,backendScope:[],limitations:[]},scenarios:[{screenId,stateId,title,description,theme,width,height,sessionGroup,actions:[],cleanup:[],limitations:[]}]}. Related sessionGroup scenarios reuse a single in-memory login. Actions: goto(url), fill/select(value), click, check/expectChecked(checked), wait(state); locators: selector or label or role+name; optional expectResponse:{path,method,status}. No interception/evaluation actions. Unknown scenarios use skipReason. Credentials are never saved by the helper.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
