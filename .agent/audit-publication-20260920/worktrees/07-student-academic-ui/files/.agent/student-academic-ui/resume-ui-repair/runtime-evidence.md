# Runtime evidence and limits

Status: `PENDING_ROOT_BROWSER_QA`.

This leaf did not start a dev server, attach a browser, take screenshots, or
claim keyboard/focus runtime PASS. Root owns the post-freeze runtime check.

The recorded pre-repair reproduction is at
`http://127.0.0.1:18210/?fixture=4593-142&theme=dark`, viewport `390x844`:
`clientWidth=375`, `scrollWidth=407`, `main` width `406.8`, and root computed
`box-sizing=content-box` with `20px 16px 108px` padding. All 13 known fixture
states shared the horizontal overflow.

Root must independently verify after this stable build:

- Attendance and Statistics root `box-sizing` computes to `border-box`;
- 390px and narrower viewports have no horizontal overflow;
- increased root font preserves the layout and visible metrics/history;
- dark and light themes retain the same composition and visible content;
- metrics and history remain visible without clipping/masking;
- the existing keyboard open → Back → return focus behavior remains intact.

No backend, real API, OTP, Telegram, PWA/TMA, shell or Requests integration
evidence is claimed by this repair.
