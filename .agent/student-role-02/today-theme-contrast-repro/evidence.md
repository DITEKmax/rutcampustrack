# Today fixed-gradient foreground — confirmed light-theme defect

07.09.2026. Severity MEDIUM: the role selector and checkin action labels become almost invisible in explicit/system light mode. This is within active shared-theme scope, not a new feature.

Root read the writer's actual computed-style/contrast report today-defect-reproduction.json and opened today-defect-explicit-light-16.png. Role/action foreground is rgb(41,37,49) on the unchanged dark gradient backgrounds. Reported minimum contrast is1.01/1.00 in light/system-light (16/20/24px root fonts) versus7.9/11.79 in dark. The screenshot visibly confirms the dark-on-dark labels. Root copied the report and both16px theme screenshots with verified SHA256 into this directory.

Source: today-screen.pcss .today-role foreground45/background46 and checkin-action foreground173/background174 use text-primary over fixed dark gradients. Root independently computed the source gradient-stop ratios around1.01–1.59 before the actual runtime report confirmed the issue.

Canonical correction source already exists: docs/design/tokens-v2.json:414–417, color/text/on-fill-strong maps to color/neutral/0 in both modes. Exposing/using its CSS alias for these strong filled backgrounds is authorized in the existing theme task; no new palette or product token is needed. The active writer was notified to fix and recapture both themes. The accent-on-now alias would be dark in dark mode and is not a safe blanket replacement here.

This report is intermediate failing evidence, not final theme acceptance. Fresh independent review must inspect the corrected stable diff, both themes' actual colors/contrast and final viewport screenshots.
