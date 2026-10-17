# PNG validation resource budget — owner decision 2026-10-11

D008. The owner answered clarified Q008: «Разрешить предел 128 МиБ (рекомендую)».

The clarified question explicitly retained the 10 MiB uploaded PNG file limit and proposed an additional validation budget of 128 MiB of decoded data and one million pixel rows. The earlier request for an explanation was not an approval; this subsequent answer approves the clarified proposal.

Accepted behavior: retain the existing uploaded-file limit of 10 MiB. Before publishing inventory, assets or a new revision, reject a PNG whose validation requires more than 134217728 decoded scanline bytes or 1000000 scanline checks, with a clear field-specific 4xx response. Count decoded scanline data including filter bytes and all nonempty Adam7 passes; count each validated pass row as one scanline check. Overflow or inconsistent sizes must become a safe validation failure, never an unhandled exception. These are processing-resource budgets, not a new width/height canvas limit or a requirement to allocate the decoded image in memory.

Preserve ordinary safe PNG formats, grayscale/indexed/Adam7 support and the already verified wide 100000-pixel one-row positive fixture within these budgets. Keep bounded streaming validation and the 10 MiB upload limit. SVG resource validation, client access, map history and owner D004 legend deferral are separate scopes.

Supersedes: the unresolved decoded-work/row-budget branch of current MAP-04 validation and independent source finding MVR03. It does not increase the accepted uploaded file size or authorize production operations.

Verification: boundary-at-limit, over-limit, overflow-safe size accounting and a scaled strongly compressed many-row counterexample; reject before any data publication and preserve valid positive controls. Do not execute a billion-row input just to prove resource exhaustion. Actual browser/render/network acceptance remains separate.
