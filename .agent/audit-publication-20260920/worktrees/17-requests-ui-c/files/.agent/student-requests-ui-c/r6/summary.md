# Requests UI R6 summary

## Scope and diff

R6 wrote only evidence under .agent/student-requests-ui-c/r6. Product files and
the three harness files remained read-only, and their exact hashes stayed at
15/15 relative to the immutable R5 checkpoint. No tracked product/harness diff
was introduced; foreign work in the detached worktree was preserved.

The only runtime correction was invocation-level: the first bare Vite command
failed to load Vue SFCs because it had no Vue config. Reusing the existing
pwa-vue/vite.config.ts with --configLoader runner started the same harness
without changing source or configuration.

## Criteria and evidence

Five required exact 390x844 PNG states are persisted in browser-evidence.json:
open, archive, type, excuse, and late. excuse.png visibly contains the
present-ineligible and missing recovery rows without raw lesson-missing ID.
excuse-focus.png separately shows the real file input focused and the visible
purple focus-within ring; the two frames are needed because excuse scrollHeight
is 1110.

The final-source-manifest.json covers exactly 12 Requests product files, 3
harness files, the five design assets, and the existing guarded source hashes.
checks.md records command, exit code, environment, runtime metrics, cleanup and
the bounded invocation finding.

## Limitations

The initial window-size capture was discarded as a browser viewport artifact;
the final CDP metrics are authoritative for 390x844 and show scrollWidth 390 in
every state. Light-theme QA is deferred by owner decision. Reduced-motion is
verified from the existing source guard; an unrepresented browser variant was
not invented. Backend/API integration remains outside this UI evidence leaf.

## Release

RELEASE: R6 evidence bundle is ready for root inspection and fresh Sol/high
review. Owned Vite and Edge runtimes are stopped, and ports 18540 and 18541
are proven free. No product or harness edit was made.