# Runtime evidence

The root-granted affected runtime completed and was released. The first batch
handle `19865` terminated with exit 1 after Gradle reported 1m43s; the targeted
fixture correction rerun `78784` terminated with exit 1 after 1m04s. Both
failures were recorded as fixture defects before the final run. The final
IT-only handle `70801` terminated with exit 0; Gradle reported 1m28s and the
XML suite time was 16.888s for 22/22 tests. No prior runtime handle was reused
or relabeled. A source self-review then corrected the `markWithLesson`
old-status ordering and added direct retention/cleanup coverage to the same
IT. The root-granted final recheck handle `29410` terminated with exit 0;
Gradle reported 1m25s and the XML suite time was 10.581s for 22/22 tests.

Frontend dependencies were then installed with the existing lockfile only:
`npm ci` exited 0 after 11s observed, with no package/lockfile diff. The
affected typechecks for mobile-core, PWA, and TMA, the journal client test
(6/6), and mobile-core lint all exited 0. npm reported four existing audit
vulnerabilities; no audit fix or dependency update was run.
