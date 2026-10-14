import { createHash, randomBytes } from 'node:crypto';
import { lstatSync } from 'node:fs';
import { type Page } from '@playwright/test';
import { test, expect, admit, type Actor } from '../fixtures/mobile-dataset';
import { loginPwa } from '../fixtures/pwa-auth';
import { requirePrivateArtifacts, sanitizeTestErrors } from '../fixtures/artifact-privacy';

test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test.afterEach(async ({}, info) => sanitizeTestErrors(info));

interface Slot {
  state: string;
  id: string;
  bytes: number;
  sha256: string;
}
interface Plan {
  buildingId: string;
  floorId: string;
  version: string;
  label: string;
  png: Slot;
  svg: Slot;
}

// Two ordinary 1x1 grayscale-alpha PNGs, each 68 bytes, with valid zlib data and chunk CRCs.
const firstPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGP4/x8AAwAB//wl3FEAAAAASUVORK5CYII=',
  'base64',
);
const replacementPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNg+A8AAQIBAEK+vGgAAAAASUVORK5CYII=',
  'base64',
);
const svg = Buffer.from(
  '<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1" viewBox="0 0 1 1"><rect width="1" height="1" fill="white"/></svg>',
);

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}
function digest(bytes: Buffer) {
  return createHash('sha256').update(bytes).digest('hex');
}
function identity(plan: Plan) {
  return JSON.stringify([
    plan.buildingId,
    plan.floorId,
    plan.version,
    plan.label,
    plan.png,
    plan.svg,
  ]);
}
function assetPath(plan: Plan, format: 'png' | 'svg') {
  return (
    `/api/v1/map/buildings/${plan.buildingId}/floors/${plan.floorId}` +
    `/plans/${plan.version}/assets/${format}/${plan[format].id}`
  );
}
async function openStudentMap(page: Page, buildingCode: string) {
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await page.getByRole('button', { name: 'Карта', exact: true }).click();
  const map = page.locator('.map-screen');
  await expect(map.getByRole('heading', { name: 'Карта кампуса', exact: true })).toBeVisible();
  await map.locator('#map-building').click();
  await map.getByRole('option', { name: `Корпус ${buildingCode}`, exact: true }).click();
  await expect(map.locator('#map-floor')).toHaveText('Этаж 1');
  return map;
}
async function mapDownload(page: Page, format: 'PNG' | 'SVG', expected: Buffer) {
  const pending = page.waitForEvent('download');
  await page.getByRole('link', { name: `Скачать ${format}`, exact: true }).click();
  const download = await pending;
  try {
    requireValue((await download.failure()) === null, 'Map download must complete');
    const stream = await download.createReadStream();
    requireValue(stream, 'Map download must provide its bytes');
    const chunks: Buffer[] = [];
    let size = 0;
    for await (const chunk of stream) {
      const bytes = Buffer.from(chunk);
      size += bytes.length;
      requireValue(size <= expected.length, 'Map download exceeds its synthetic fixture');
      chunks.push(bytes);
    }
    expect(Buffer.concat(chunks).equals(expected)).toBe(true);
  } finally {
    await download.delete();
  }
}

// JS-ADMIN-23/25 + JS-STUDENT-24: API publication and a genuine Vue consumer.
// D004 excludes the legend; zoom/pan, deletion and negative SVG/PNG-budget cases remain separate.
test('ADMIN replaces a map while STUDENT downloads current PNG and SVG in Vue and old assets remain readable @real-mobile', async ({
  page,
  dataset,
  teachingInstant,
}, info) => {
  test.setTimeout(180000);
  requirePrivateArtifacts(info);
  requireValue(teachingInstant, 'Map lifecycle requires the current teaching-clock lane');
  const output = lstatSync(info.project.outputDir);
  requireValue(
    output.isDirectory() && !output.isSymbolicLink() && (output.mode & 0o777) === 0o700,
    'Map lifecycle requires private output',
  );
  expect(firstPng.length === 68 && replacementPng.length === 68).toBe(true);
  expect(firstPng.equals(replacementPng)).toBe(false);
  const buildingCode = String(1_000_000_000 + randomBytes(4).readUInt32BE(0));
  const otherCode = `${buildingCode}1`;
  const targetPath = `/api/academic/map/plans/target?buildingCode=${buildingCode}&floorCode=1`;
  const upload = (actor: Actor, png: Buffer, label: string, includeSvg = false) =>
    dataset.request.post('/api/academic/map/plans', {
      headers: actor.headers(),
      multipart: {
        buildingCode,
        floorCode: '1',
        label,
        png: { name: 'synthetic-map.png', mimeType: 'image/png', buffer: png },
        ...(includeSvg
          ? { svg: { name: 'synthetic-map.svg', mimeType: 'image/svg+xml', buffer: svg } }
          : {}),
      },
    });
  const published = await upload(dataset.admin, firstPng, dataset.marker, true);
  expect(published.status()).toBe(201);
  const created = await published.json();
  const first = created.plan as Plan;
  expect(created.status === 'created' && created.buildingCreated && created.floorCreated).toBe(
    true,
  );
  expect(
    first.version === '1' &&
      first.png.state === 'ready' &&
      first.svg.state === 'ready' &&
      first.png.sha256 === digest(firstPng),
  ).toBe(true);
  const other = await dataset.admin.call(
    'POST',
    '/api/academic/map/buildings',
    {
      code: otherCode,
      label: 'Синтетический другой корпус',
    },
    [201],
  );
  const student = await admit(dataset.request, dataset.student, 'STUDENT');
  const readAsset = async (plan: Plan, format: 'png' | 'svg') => {
    const slot = plan[format];
    requireValue(slot.bytes > 0 && slot.bytes <= 4096, 'Map fixture asset must be bounded');
    const response = await dataset.request.get(assetPath(plan, format), {
      headers: student.headers(),
    });
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type'].split(';', 1)[0]).toBe(
      format === 'png' ? 'image/png' : 'image/svg+xml',
    );
    expect(Number(response.headers()['content-length']) === slot.bytes).toBe(true);
    const bytes = await response.body();
    expect(bytes.length === slot.bytes && digest(bytes) === slot.sha256).toBe(true);
    return bytes;
  };
  const originalSvg = await readAsset(first, 'svg');
  expect((await readAsset(first, 'png')).equals(firstPng)).toBe(true);
  const wrongParent = await dataset.request.get(
    `/api/v1/map/buildings/${other.id}/floors/${first.floorId}/plan`,
    { headers: student.headers() },
  );
  expect(wrongParent.status()).toBe(404);
  // Campus maps are shared across groups; publication still requires the ADMIN map grant.
  for (const actor of [
    dataset.headmanActor,
    await admit(dataset.request, dataset.limitedAdmin, 'ADMIN'),
  ]) {
    expect((await upload(actor, replacementPng, 'Запрещённая замена')).status()).toBe(403);
    const current = await dataset.admin.call('GET', targetPath);
    expect(identity(current.floor.currentPlan) === identity(first)).toBe(true);
  }

  await loginPwa(page, dataset.student);
  const viewAndDownload = async (plan: Plan, png: Buffer) => {
    const response = page.waitForResponse(
      (value) =>
        new URL(value.url()).pathname === assetPath(plan, 'png') &&
        value.request().method() === 'GET',
    );
    const svgRead = page.waitForResponse(
      (value) =>
        new URL(value.url()).pathname === assetPath(plan, 'svg') &&
        value.request().method() === 'GET',
    );
    const map = await openStudentMap(page, buildingCode);
    const [pngResponse, svgResponse] = await Promise.all([response, svgRead]);
    expect(pngResponse.status()).toBe(200);
    expect((await pngResponse.headerValue('content-type'))?.split(';', 1)[0]).toBe('image/png');
    expect(Number(await pngResponse.headerValue('content-length')) === plan.png.bytes).toBe(true);
    expect(svgResponse.status()).toBe(200);
    expect((await svgResponse.headerValue('content-type'))?.split(';', 1)[0]).toBe('image/svg+xml');
    expect(Number(await svgResponse.headerValue('content-length')) === plan.svg.bytes).toBe(true);
    await expect(map.getByRole('heading', { name: plan.label, exact: true })).toBeVisible();
    await expect(map.getByText(`Версия ${plan.version}`, { exact: true })).toBeVisible();
    const preview = map.getByRole('img', { name: 'Схема выбранного этажа', exact: true });
    const svgDownload = map.getByRole('link', { name: 'Скачать SVG', exact: true });
    await expect(svgDownload).toBeVisible();
    await expect
      .poll(async () => {
        const href = await svgDownload.getAttribute('href');
        const src = await preview.getAttribute('src');
        return Boolean(href?.startsWith('blob:') && src === href);
      })
      .toBe(true);
    await expect
      .poll(() =>
        preview.evaluate(
          (image) => image instanceof HTMLImageElement && image.complete && image.naturalWidth > 0,
        ),
      )
      .toBe(true);
    await mapDownload(page, 'SVG', originalSvg);
    await mapDownload(page, 'PNG', png);
  };
  await viewAndDownload(first, firstPng);
  const replaced = await upload(dataset.admin, replacementPng, `${dataset.marker} новая`);
  expect(replaced.status()).toBe(200);
  const replacement = await replaced.json();
  const second = replacement.plan as Plan;
  expect(
    replacement.status === 'replaced' &&
      !replacement.buildingCreated &&
      !replacement.floorCreated &&
      second.floorId === first.floorId &&
      second.buildingId === first.buildingId &&
      second.version === '2' &&
      second.png.sha256 === digest(replacementPng) &&
      second.svg.sha256 === first.svg.sha256,
  ).toBe(true);
  const historical = await dataset.admin.call(
    'GET',
    `/api/academic/map/floors/${first.floorId}/versions/${first.version}`,
  );
  expect(identity(historical as Plan) === identity(first)).toBe(true);
  expect((await readAsset(first, 'png')).equals(firstPng)).toBe(true);
  expect((await readAsset(first, 'svg')).equals(originalSvg)).toBe(true);
  expect((await readAsset(second, 'svg')).equals(originalSvg)).toBe(true);
  await page.reload();
  await viewAndDownload(second, replacementPng);
  const current = await dataset.admin.call('GET', targetPath);
  expect(identity(current.floor.currentPlan) === identity(second)).toBe(true);
});
