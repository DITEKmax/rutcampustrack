import { SkeletonList } from './Skeleton'

/**
 * Default loading fallback used by lazy routes (`Suspense fallback`).
 *
 * Name kept for backwards compat with existing imports and test mocks,
 * but per brandbook §5.4 we now render a skeleton list instead of a
 * spinning circle.
 */
export function LoadingSpinner() {
  return <SkeletonList count={4} />
}
