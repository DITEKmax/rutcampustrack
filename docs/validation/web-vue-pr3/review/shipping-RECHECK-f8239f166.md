# Independent static-output readability review — f8239f166

PASS for the three-line Docker correction, integrateda81394473. Reviewer root
is not the product author. Each final nginx stage adds a+rX only to its selected
public dist tree after COPY. This preserves bytes and adds read/traversal for
worker access; private build secrets and host/source files are outside the path.
No scripts/API/permissions/theme/lockfile/CI behavior change.

The original WEB9b8 image returned403 for canonical auth PNG: nginx open()
Permission denied, source/dist0600. Source and image PNG SHA256 matched the
original Figma bytes. The failure was real, retained separately. No checksum,
HTTP status, MIME, fallback or cache assertion was relaxed.

Corrected actual WEBa813 image: strict production build PASS; nginx -t PASS;
12 bounded static checks PASS (shell/deep links no-store, missingJS/CSS/worker404,
module MIME and served/container/current-build byte identity, originalPNG200
with canonical hash). PWA/TMA actual images build PASS; their inner static smoke
keeps base prefixes and PWA worker/version no-store behavior. No external Push
subscription/delivery or outer production routing acceptance is implied.

Own image/cache verification remains separate from Angular removal and final
production wiring. Root recorded the subsequent disk incident and exact-ID
owned-cache recovery; successful image serving does not hide that incident.
