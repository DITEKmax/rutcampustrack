set -euo pipefail
umask 077
cd frontends
npm run generate:types:check
npm run generate:auth-types:check
npm run generate:academic-schedule-types:check
npm run test:contract
npm run typecheck:foundation
npm run typecheck
npx --no-install vitest run --coverage --maxWorkers=4 --reporter=default --reporter=junit --outputFile=test-results/local-release.xml
node --test pwa-vue/tests/*.test.mjs
node ../tests/load/scenario-controls.cjs
npm run build
