/**
 * M03b Группа 7: full client-state cleanup для web-panel при logout.
 *
 * Закрывает 10 P0-4 — cross-user leak через sessionStorage в общих
 * браузерах (деканат/лаборантская). Полный аналог PWA clearAllClientState.
 *
 * Web-panel не использует Service Worker (только PWA), поэтому SW cache
 * cleanup не нужен. Push-subscriptions — тоже PWA-only.
 */
export async function clearAllClientState(): Promise<void> {
  try {
    localStorage.clear();
  } catch {
    /* storage disabled */
  }
  try {
    sessionStorage.clear();
  } catch {
    /* storage disabled */
  }
}
