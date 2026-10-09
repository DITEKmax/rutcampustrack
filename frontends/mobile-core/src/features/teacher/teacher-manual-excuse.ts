export function isTeacherManualExcuse(value: { readonly ticketId: string | null; readonly recordPresent: boolean; readonly status: string | null; readonly symbol: string | null } | null): boolean {
  return Boolean(value?.recordPresent && !value.ticketId && (value.status?.toLowerCase() === 'excused' || value.symbol === 'у'))
}
