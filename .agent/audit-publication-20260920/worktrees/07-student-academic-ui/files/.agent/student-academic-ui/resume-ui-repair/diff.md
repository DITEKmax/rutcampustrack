# Bounded diff record

Baseline and current HEAD: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

The product change is exactly two declarations:

```diff
 .attendance-screen {
   display: flex;
   flex-direction: column;
   inline-size: min(100%, 26.625rem);
+  box-sizing: border-box;
   min-block-size: 100dvh;
 }

 .statistics-screen,
 .statistics-detail {
   display: flex;
   flex-direction: column;
   inline-size: min(100%, 26.625rem);
+  box-sizing: border-box;
   min-block-size: 100dvh;
 }
```

The direct SHA guard reports 2 changed product paths out of the frozen 23:

- `attendance-screen.pcss`: `C0CE846F…C1283` → `6F7ACB86…DE4A2`;
- `statistics-screen.pcss`: `45B5CE2D…5A2A3` → `474C49CD…67115`.

All other 21 application SHA values are unchanged. `git diff --check` returned
exit code 0. No stage, commit, reset, clean, rollback or browser/server action
was performed by this leaf.
