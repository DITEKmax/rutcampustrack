import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { clearAllClientState } from './clear-all-client-state';

/**
 * M08 Группа 6 (P2-8/6) — regression guard для 10 P0-4 web-panel
 * cross-user leak fix через sessionStorage (общие браузеры деканата).
 *
 * Web-panel version делает только local+session storage clear —
 * не трогает Service Worker caches (web-panel не использует SW) и
 * не PushManager (PWA-only).
 */

describe('clearAllClientState — web-panel logout cleanup (10 P0-4 regression guard)', () => {
  let localStorageClearSpy: any;
  let sessionStorageClearSpy: any;

  beforeEach(() => {
    localStorageClearSpy = vi.spyOn(Storage.prototype, 'clear');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('clears localStorage', async () => {
    await clearAllClientState();
    // Storage.prototype.clear — shared prototype для local+session
    expect(localStorageClearSpy).toHaveBeenCalled();
  });

  it('does not throw if localStorage disabled', async () => {
    localStorageClearSpy.mockImplementation(() => {
      throw new Error('storage disabled');
    });

    // Graceful fallback — не должно бросать
    await expect(clearAllClientState()).resolves.toBeUndefined();
  });
});
