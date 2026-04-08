import { cn } from '@/lib/utils'

/**
 * Transit Grid skeleton primitive (brandbook §5.4).
 *
 * Per brandbook: "Skeleton-загрузка вместо спиннеров". A low-opacity shimmer
 * block that animates only `opacity` (compositor-only, reduced-motion safe).
 *
 * Compose these into route-specific skeletons inside each feature, or use
 * the `SkeletonCard` / `SkeletonList` helpers below for a sensible default.
 */
interface SkeletonProps {
  className?: string
}

export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={cn('rounded-md animate-pulse', className)}
      style={{
        background: 'color-mix(in oklab, var(--text-primary) 8%, transparent)',
      }}
    />
  )
}

/** Card-shaped skeleton: title + two body lines + trailing chip. */
export function SkeletonCard() {
  return (
    <div
      role="status"
      aria-label="Загрузка"
      className="flex flex-col gap-3 rounded-xl border border-border p-4"
      style={{ background: 'var(--bg-secondary)' }}
    >
      <div className="flex items-start justify-between gap-3">
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-5 w-14 rounded-full" />
      </div>
      <Skeleton className="h-3 w-full" />
      <Skeleton className="h-3 w-5/6" />
    </div>
  )
}

/** Stack of SkeletonCards — the default fallback for lazy routes. */
export function SkeletonList({ count = 4 }: { count?: number }) {
  return (
    <div className="flex flex-col gap-3 px-4 py-4">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}
