# Runtime evidence

The fixture served the actual shared `HomeworkScreen` and `useHomework` from this worktree with a deterministic `StudentApi` fetcher:

```text
node .\frontends\node_modules\vite\bin\vite.js --config .agent\student-role-02\homework-presentation-repair\runtime\vite.config.mjs --configLoader native --host 127.0.0.1 --port 5181
```

The final probe used Microsoft Edge headless, locale `ru-RU`, timezone `Europe/Moscow`, device scale factor 1 and viewport 390×844. The final result is `final-visual-evidence.json`; 21 PNGs are in `screenshots-final/`.

Observed and asserted on the final source:

- open: mandatory title is present, optional detail is absent, non-empty detail has one collapsed control without `aria-controls`, and empty detail has no control or region;
- expanded: distinct detail text is present before actions, computed detail font is 12px, and the control references `homework-description-source-programming`;
- no materials: null-link card has no materials action, its disclosure right edge equals the action-row right edge (`0` px delta), and supported card still exposes Materials;
- empty no materials: null-link plus empty detail has no empty actions row, detail region or disclosure;
- unsafe material: `javascript:` has no actionable Materials button and exposes the supported recoverable error;
- completion, reversal, keyboard Space, failure/retry, offline read-only and historical previous/today transitions remain functional;
- axe reported zero violations for the inspected open, expanded, no-material, empty, unsafe, failure and offline states;
- 12 matrix cases (light/system/dark × root 16/20/24 and widths 320/390/430) had zero horizontal overflow, PNG dimensions matching the requested viewport and `document.fonts.check('13px "Onest Variable"') === true`.

These are synthetic shared-component fixtures with no real PWA/TMA host or backend. Real API, adapter, Telegram and downstream integration acceptance remain open for the parent/root task.
