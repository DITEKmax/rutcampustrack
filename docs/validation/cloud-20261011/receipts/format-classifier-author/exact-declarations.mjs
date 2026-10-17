// Exact source/evidence copies verified against their immutable originals and hash manifests.
// Maintained author files in the same directories remain eligible for formatting.
const exactReceiptCopies = new Set(
  [
    'ci-artifact-reconcile-636/assess.py',
    'ci-artifact-reconcile-636/reconcile.py',
    'ci-artifact-reconcile-636/source-SubjectListDataIT-actual-checkout.java',
    'ci-generator-recheck/audit.py',
    'ci-generator-recheck/clock_audit.py',
    'clock-runtime-recheck/audit.py',
    'export-ebsr-recheck/check-runtime-receipts.py',
    'export-ebsr-recheck/final-check.py',
    'export-ebsr-recheck/recheck.py',
    'export-merged-repository-review/check.py',
    'fingerprint-review/audit.py',
    'fingerprint-review/author-originals/sensitivity.py',
    'fingerprint-review/author-originals/verify.py',
    'format-freeze-prep/inventory.py',
    'map-validation-recheck/audit_evidence.py',
    'map-validation-recheck/original-evidence/run-map-check.py',
    'map-validation-recheck/source/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapAdminServiceTest.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapFileBoundaryIT.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapFileFixture.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java',
    'map-validation-review/audit_evidence.py',
    'matrix-evidence-reconciliation/dispositions.py',
    'matrix-evidence-reconciliation/queue.py',
    'matrix-evidence-reconciliation/reconcile.py',
    'notification-recovery-review/collect-review-inputs.py',
    'np01-config-author/verify-config.py',
    'np01-source-resolution/source-ledger.py',
    'ntr-seal-review/audit.py',
    'security-fingerprint-author/sensitivity.py',
    'security-fingerprint-author/verify.py',
    'source-manifest-recovery/check-dependencies.py',
    'source-manifest-recovery/propose-restoration.py',
  ].map((path) => `docs/validation/cloud-20261011/receipts/${path}`),
)

// Imported source registries and machine hash receipts have separate byte owners.
const exactSourceRegistries = new Set([
  'docs/design/packets/JS-STUDENT-01/manifest.json',
  'docs/presentations/manifest.json',
  'docs/validation/cloud-20261011/receipts/manifest-post-hooks.json',
  'docs/validation/cloud-20261011/skills/manifest.json',
])
