# Evidence — JS-STUDENT-01

- **Revision:** `87784165874e2da6fc261abc1c01584e24624289`.
- **Figma read evidence:** R001–R011 plus `whoami`; 11 reads, zero retries and zero
  duplicate screenshot requests. R001 is transport-truncated; R007 capped an SVG
  descriptor list. R003–R011 retain local before/after request timing proxies.
- **Captured UI:** six dark `390×844` PNG screenshots and ten lossless raw response
  files are listed with byte hashes in the packet manifest.
- **Assets:** 58 actual generated-code URL records collapse to 16 local canonical SVG
  sources by SHA-256. The placeholder PNG URL was boilerplate, not a missing asset.
- **Visual validation:** all six retained PNG screenshots were opened locally during
  capture. SVG byte validation (UTF-8 XML with SVG root) happened during download;
  no later byte rewrite occurred.
- **Runtime:** skipped. This is a local documentation/evidence change, so no product
  application or service behavior exists to run.

- **Targeted correction:** 34 actual `var(--…)` references now map through R011 to canonical `tokens-v2.json`; 11 dimension values are exact under the fixed 16px rem base and escaped `radius/2xl` is preserved as the exact existing `--radius-2xl` target with no alias.
- **Focused Sol re-review:** PASS (S2 bounded, findings 0). It accepted 34 references across 436 occurrences, correct escaped `radius/2xl` normalization, exact canonical targets/values and five evidence hashes; prior PNG/SVG/raw checks were not repeated.
