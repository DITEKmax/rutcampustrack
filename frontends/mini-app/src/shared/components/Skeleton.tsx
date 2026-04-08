import { cn } from '@/lib/utils'

/**
 * Transit Grid skeleton primitive (brandbook §5.4), mini-app variant.
 *
 * Low-opacity shimmer block that animates only `opacity` (compositor-only,
 * prefers-reduced-motion safe). Compose into route-specific skeletons or
 * use the helpers below.
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

/** Compact card-shaped skeleton. */
export function SkeletonCard() {
  return (
    <div
      role="status"
      aria-label="Загрузка"
      className="flex flex-col gap-2 rounded-2xl border border-border p-3"
      style={{ background: 'var(--bg-secondary)' }}
    >
      <div className="flex items-start justify-between gap-2">
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-4 w-12 rounded-full" />
      </div>
      <Skeleton className="h-3 w-5/6" />
      <Skeleton className="h-2.5 w-1/3" />
    </div>
  )
}

/** Default Suspense/loading fallback. */
export function SkeletonList({ count = 3 }: { count?: number }) {
  return (
    <div className="flex flex-col gap-3">
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </div>
  )
}
