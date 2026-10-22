import { describe, it, expect, vi, afterEach } from 'vitest';
import { clearAllClientState } from './clear-all-client-state';

describe('clearAllClientState — web-panel logout cleanup', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('removes both local and session state', async () => {
    localStorage.setItem('audit-local-owner', 'synthetic-local');
    sessionStorage.setItem('audit-session-owner', 'synthetic-session');
    await clearAllClientState();
    expect(localStorage.getItem('audit-local-owner')).toBeNull();
    expect(sessionStorage.getItem('audit-session-owner')).toBeNull();
  });

  it.each(['localStorage', 'sessionStorage'] as const)('still clears the other storage when %s is unavailable', async blocked => {
    localStorage.setItem('audit-local-owner', 'synthetic-local');
    sessionStorage.setItem('audit-session-owner', 'synthetic-session');
    const originalClear = Storage.prototype.clear;
    vi.spyOn(Storage.prototype, 'clear').mockImplementation(function(this: Storage) {
      if (this === window[blocked]) throw new Error('storage disabled');
      originalClear.call(this);
    });
    await expect(clearAllClientState()).resolves.toBeUndefined();
    if (blocked === 'localStorage') expect(sessionStorage.getItem('audit-session-owner')).toBeNull();
    else expect(localStorage.getItem('audit-local-owner')).toBeNull();
  });
});
