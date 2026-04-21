// Generate landing/dist/assets/og/ruttrack-og-1200x630.png from ruttrack-og.svg.
// Uses @resvg/resvg-js to embed self-hosted Fontshare/Google woff2 so the PNG
// renders with brand typography instead of generic fallback.
//
// Usage: node scripts/generate-og.mjs (run from frontends/landing/).
//   Requires: npm i -D @resvg/resvg-js.
//
// Re-run manually only when ruttrack-og.svg changes. The PNG is committed
// to git as the deterministic artefact consumed by <meta property="og:image">.

import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const landingRoot = resolve(__dirname, '..');
const svgPath = resolve(landingRoot, 'dist/assets/og/ruttrack-og.svg');
const pngPath = resolve(landingRoot, 'dist/assets/og/ruttrack-og-1200x630.png');

const fontsDir = resolve(landingRoot, 'dist/assets/fonts');
const fontFiles = [
  resolve(fontsDir, 'fontshare/ClashDisplay-600.woff2'),
  resolve(fontsDir, 'fontshare/ClashDisplay-700.woff2'),
  resolve(fontsDir, 'fontshare/GeneralSans-500.woff2'),
  resolve(fontsDir, 'fontshare/GeneralSans-600.woff2'),
  resolve(fontsDir, 'google/DMSans-latin.woff2'),
  resolve(fontsDir, 'google/DMSans-latin-ext.woff2'),
  resolve(fontsDir, 'google/JetBrainsMono-latin.woff2'),
  resolve(fontsDir, 'google/JetBrainsMono-cyrillic.woff2'),
];

for (const p of [svgPath, ...fontFiles]) {
  if (!existsSync(p)) {
    console.error(`[generate-og] missing: ${p}`);
    process.exit(1);
  }
}

const svg = readFileSync(svgPath, 'utf8');
const fontBuffers = fontFiles.map((p) => readFileSync(p));

const resvg = new Resvg(svg, {
  fitTo: { mode: 'width', value: 1200 },
  background: '#0A0E17',
  font: {
    fontBuffers,
    defaultFontFamily: 'Clash Display',
    loadSystemFonts: true,
  },
});

const png = resvg.render().asPng();
writeFileSync(pngPath, png);

console.log(`[generate-og] wrote ${pngPath} (${png.length} B, ${fontFiles.length} fonts embedded)`);
