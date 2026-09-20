# Preservation milestone — 2026-09-08

- baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- nested product source: `frontends/mobile-core/src/features/profile/` (38 files)
- preserved outer duplicates: 8 product files
- preserved outer evidence: `.agent/profile-ui/progress.md`, `.agent/profile-ui/status.md`
- total preserved files: 48
- exact copy manifest: `preserved/manifest.json`
- manifest bytes: `22825`
- manifest SHA256: `6777B39AF8B07F56AF2E57C9667310B89CADE227F63E4641333BFFE22566B95A`

The copy job asserted every destination under the nested own evidence prefix and
verified source/preserved byte length and SHA256 for all 48 files after copying.
The 27 SVG sources were copied as binary files and remain immutable in product
scope. The outer duplicate products and outer evidence remain read-only.

Next checks: record baseline reproduction for the snapshot authority race and
repeated `SESSION_STATE_STALE` reload behavior, apply the bounded state repair,
then run focused state/mounted checks, strict Vue SFC typecheck, focused lint and
PCSS source scan. Browser work is pending a fresh root GO after a free-port check
and exact harness proposal in `18110–18119`; no server or browser has started.
