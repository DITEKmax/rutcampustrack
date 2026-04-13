/**
 * Phase 56: Runtime caching predicate for headman GET endpoints (PWA-HEAD-04)
 *
 * Extracted as a pure module so it can be unit-tested without a Service Worker
 * context. Imported by sw.ts and by __tests__/sw-runtime-cache.test.ts.
 */

/**
 * Returns true if the given URL is a headman API endpoint that should be
 * cached using the Stale-While-Revalidate strategy.
 *
 * Covered endpoints (CONTEXT.md D-17):
 *   GET /api/academic/groups/:id/members
 *   GET /api/academic/groups/:id/subjects
 *   GET /api/academic/groups/:id/teachers
 *   GET /api/academic/subjects*           (list, details, teacher queries)
 *   GET /api/academic/thresholds/resolve*
 *   GET /api/attendance/reports/journal*
 *   GET /api/attendance/excuses/pending*  (may 404 — non-200 not cached per D-18)
 *   GET /api/attendance/late-checkins/pending*
 *
 * Deliberately excluded:
 *   /api/attendance/lessons/:id/students/:id  — mutation path (PUT mark attendance)
 *   All other /api/auth/, /api/academic/users/:id, /api/schedule/ paths
 */
export function isHeadmanApiRequest(url: URL): boolean {
  const { pathname } = url

  // /api/academic/groups/:id/members|subjects|teachers
  if (pathname.startsWith('/api/academic/groups/')) {
    if (
      pathname.endsWith('/members') ||
      pathname.endsWith('/subjects') ||
      pathname.endsWith('/teachers')
    ) {
      return true
    }
  }

  // /api/academic/subjects* — list, detail, sub-resources
  if (pathname.startsWith('/api/academic/subjects')) return true

  // /api/academic/thresholds/resolve*
  if (pathname.startsWith('/api/academic/thresholds/resolve')) return true

  // /api/attendance/reports/journal*
  if (pathname.startsWith('/api/attendance/reports/journal')) return true

  // /api/attendance/excuses/pending*
  if (pathname.startsWith('/api/attendance/excuses/pending')) return true

  // /api/attendance/late-checkins/pending*
  if (pathname.startsWith('/api/attendance/late-checkins/pending')) return true

  return false
}
