/**
 * Dev-mode banner — shown only when VITE_TMA_DEV=true.
 *
 * Lives above the AppHeader (via fixed position + z-overlay) so developers
 * can't miss they're in mock mode. Uses Transit Grid warning tokens — the
 * old bg-yellow-400 was off-brand. The warning glyph is an inline SVG
 * (not Phosphor) so this module stays free of heavy icon imports; the
 * component ships in the dev bundle only.
 */
export function DevModeBanner() {
  if (import.meta.env.VITE_TMA_DEV !== 'true') return null
  const mockUser = import.meta.env.VITE_TMA_MOCK_USER ?? 'student'

  return (
    <div
      role="status"
      className="fixed inset-x-0 top-0 z-[var(--z-overlay)] flex h-7 items-center justify-center gap-1.5 px-3 text-[11px] font-medium"
      style={{
        background: 'color-mix(in oklab, var(--accent-warning) 22%, var(--bg-elevated))',
        color: 'var(--accent-warning)',
        borderBottom: '1px solid color-mix(in oklab, var(--accent-warning) 36%, transparent)',
      }}
    >
      <svg
        aria-hidden="true"
        width="12"
        height="12"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M12 2 1 21h22L12 2z" />
        <path d="M12 10v5" />
        <path d="M12 18h.01" />
      </svg>
      <span>DEV MODE — mock user: {mockUser}</span>
    </div>
  )
}
