# Hotfix: CI Build Failures (post-phase-54)

**Date:** 2026-04-10
**Discovered in:** GitHub Actions `npm run build` after Phase 54 merge

## Fixes Applied

### 1. `journal-cell.component.ts` — unused `NgIf` import

**File:** `frontends/web-panel/src/app/features/teacher/journal/journal-cell/journal-cell.component.ts`

**Problem:** `NgIf` was listed in `imports: [NgIf]` but the template uses the modern `@if` control flow syntax, making the import unused. Angular compiler emits `TS-998113` warning that caused build to fail.

**Fix:** Removed `NgIf` import from both the ES import statement and the component `imports` array.

---

### 2. `excuse-form-dialog.component.ts` + `.html` — `AbstractControl` ≠ `FormControl`

**File:** `frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/`

**Problem:** Template bound `[formControl]="commentForm.controls['comment']"`. `FormGroup.controls['key']` returns `AbstractControl`, which is not assignable to `FormControl` — Angular compiler emits `NG9` error (hard build failure).

**Fix:**
- Added `FormControl` to the `ReactiveFormsModule` import group in the `.ts` file
- Exposed a typed getter: `get commentControl(): FormControl { return this.commentForm.get('comment') as FormControl; }`
- Updated template to use `[formControl]="commentControl"`

---

## Non-blocking warnings (not fixed)

- `NG8102` in `student-homework.component.html` lines 42 and 57: `pendingItems()[item.id] ?? false` — the `??` operator is redundant because `Record<number, boolean>` doesn't include `undefined`. This is a warning only and does not block the build.
