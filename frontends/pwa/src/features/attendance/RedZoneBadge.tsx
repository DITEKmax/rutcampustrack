import { WarningCircle } from '@phosphor-icons/react'

export function RedZoneBadge() {
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-red-100 text-red-700 text-xs font-medium">
      <WarningCircle size={14} weight="bold" />
      Красная зона
    </span>
  )
}
