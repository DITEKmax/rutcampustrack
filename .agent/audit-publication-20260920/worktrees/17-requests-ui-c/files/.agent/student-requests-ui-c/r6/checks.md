# R6 checks

Revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e
Environment: Windows PowerShell, Node v24.14.0, npm 11.9.0, Vite 7.3.6, installed Microsoft Edge headless; localhost only.

| Check | Command/evidence | Exit | Result |
|---|---|---:|---|
| Frozen preflight | Get-FileHash SHA256 over exact 12 product + 3 harness paths; Get-NetTCPConnection -State Listen -LocalPort 18540,18541 | 0 | 15/15 checkpoint hashes match; no listeners before runtime |
| Bare Vite invocation finding | vite.cmd --host 127.0.0.1 --port 18540 --strictPort | 1 | Import-analysis overlay because bare root had no Vue config; source parse was valid. Evidence: vite.stderr.log and initial discarded open overlay |
| Bounded invocation correction | vite.cmd --config pwa-vue/vite.config.ts --configLoader runner --host 127.0.0.1 --port 18540 --strictPort | 0 | VITE v7.3.6 ready; evidence: vite-runner.stdout.log; wrapper 2980, actual listener 47856 |
| Local runtime | Invoke-WebRequest http://127.0.0.1:18540/.requests-harness/?state=open | 0 | HTTP 200, Requests UI harness |
| Browser capture | Installed Edge headless screenshot invocation for five states, then CDP device metrics and Page.captureScreenshot | 0 | open/archive/type/excuse/late PNGs plus excuse-focus supplement persisted |
| PNG guard | System.Drawing.Image dimensions, PNG signature 89504E470D0A1A0A, Get-FileHash SHA256 for six files | 0 | Every PNG is exactly 390x844 with recorded SHA in browser-evidence.json |
| Exact viewport/overflow | CDP Emulation.setDeviceMetricsOverride width 390 height 844; Runtime.evaluate clientWidth/scrollWidth/bodyScrollWidth/maxRight | 0 | All states clientWidth=390, scrollWidth=390, bodyScrollWidth=390, maxRight=390; excuse scrollHeight=1110 |
| Recovery/focus | CDP Runtime.evaluate selected input[type=file], focus preventScroll, scrollIntoView; visual inspect of excuse.png and excuse-focus.png | 0 | Missing/ineligible recovery visible without raw ID; active INPUT.request-visually-hidden and visible purple focus ring |
| Source guards | rg reduced-motion/focus-within/missing-label in Requests PCSS/SFC/harness | 0 | prefers-reduced-motion guard and focus-within rule present; missing label rendered |
| Historical R5 checks | R5 typecheck/lint/SSR/state/vue-tsc/PostCSS/contract checks | N/A | Accepted unchanged history; no covered source changed in R6 |
| Cleanup | Stop-Process owned Edge 1272 and Vite 2980/47856; netstat -ano port scan | 0 | Owned processes stopped; no port lines for 18540/18541 |
| Scope/diff | git diff --name-only; frozen source/harness hash comparison | 0 | No tracked product/harness diff; only bounded R6 evidence added; foreign work preserved |

## Runtime observations

The first screenshots made with only Edge --window-size were discarded because
the browser reported CSS viewport 492x744; that invocation artifact visually
clipped content. The corrected CDP 390x844 capture reports no horizontal
overflow and is the only screenshot set in this bundle.

Dark theme is represented by the existing harness. Light-theme QA is deferred
by the owner. Backend/API integration is outside this leaf and remains OPEN.
| Evidence integrity | PowerShell ConvertFrom-Json for browser-evidence/final-source-manifest; System.Drawing dimensions; non-recursive port scan | 0 | JSON valid; 6 PNGs 390x844; product 12, harness 3, guarded 18/assets 5; ports free |
