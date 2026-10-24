# PWA host logout presentation — independent Root source review PASS

Author fed1d627; Root reviewer did not author product source. Two assigned files only.
Removing the single stylesheet import restores current Rootf32 App.vue byte-for-byte;
all Auth/visibility/logout handlers, roles and active read fences are unchanged.

New36-line PCSS reuses the existing26.625rem Teacher content max, shared spacing,
44px-as-rem minimum touch size, semantic danger/on-fill/disabled tokens and shared
focus mixin. Narrow intrinsic width and centered max match the feature content;
safe-area/dock spacing is retained. Hover uses an inset hairline to preserve fill
contrast; press and disabled rules are local to the existing class. No theme copy,
new foundational token, inline style, functionality or implementation-mirroring test.

Accepted verification: one PWA vue-tsc+Vite build passes after the import/wiring;
final CSS-only hover delta separately passes Vite PostCSS import/mixin/native nested
focus processing and scoped format. Exact final production image is not claimed
from that earlier build. Existing build chunk warning unchanged. Backend/Auth tests
and successful Lesson reads are reused because their source/behavior is unchanged.

Source may integrate. Changed presentation still needs bounded actual after frames
at narrow/wide viewport with keyboard focus; no whole-screen/UI-logout PASS yet.
