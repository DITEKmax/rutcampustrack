import type { AttendanceStatus } from '@/features/headman/shared/types'
import { STATUS_SYMBOLS, STATUS_ARIA_LABELS } from '@/features/schedule/statusSymbols'

const SEGMENTS: ReadonlyArray<{
  value: AttendanceStatus
  label: string
  ariaLabel: string
}> = [
  { value: 'present',         label: STATUS_SYMBOLS.present,         ariaLabel: STATUS_ARIA_LABELS.present },
  { value: 'absent',          label: STATUS_SYMBOLS.absent,          ariaLabel: STATUS_ARIA_LABELS.absent },
  { value: 'excused',         label: STATUS_SYMBOLS.excused,         ariaLabel: STATUS_ARIA_LABELS.excused },
  { value: 'free_attendance', label: STATUS_SYMBOLS.free_attendance, ariaLabel: STATUS_ARIA_LABELS.free_attendance },
  { value: 'cancelled',       label: STATUS_SYMBOLS.cancelled,       ariaLabel: STATUS_ARIA_LABELS.cancelled },
]

export interface SegmentedControlProps {
  value: AttendanceStatus
  onValueChange: (status: AttendanceStatus) => void
  disabled?: boolean
  ariaLabel?: string
}

export function SegmentedControl({
  value,
  onValueChange,
  disabled,
  ariaLabel,
}: SegmentedControlProps) {
  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel ?? 'Статус посещения'}
      className="inline-flex gap-1 rounded-md p-1"
      style={{ background: 'var(--bg-surface)' }}
    >
      {SEGMENTS.map((seg) => {
        const isSelected = seg.value === value
        return (
          <button
            key={seg.value}
            type="button"
            role="radio"
            aria-checked={isSelected}
            aria-label={seg.ariaLabel}
            disabled={disabled}
            onClick={() => {
              if (!disabled) onValueChange(seg.value)
            }}
            className={[
              'min-w-[40px] min-h-[40px] flex items-center justify-center',
              'text-sm font-semibold rounded-md transition-colors duration-75',
              isSelected ? 'shadow-sm text-white' : '',
              disabled ? 'opacity-50 cursor-not-allowed' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            style={{
              background: isSelected ? 'var(--accent-primary)' : 'transparent',
              color: isSelected ? '#fff' : 'var(--text-primary)',
            }}
          >
            {seg.label}
          </button>
        )
      })}
    </div>
  )
}
