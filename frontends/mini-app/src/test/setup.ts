import '@testing-library/jest-dom'
import { vi } from 'vitest'

// Mock @telegram-apps/sdk-react for tests
vi.mock('@telegram-apps/sdk-react', () => ({
  init: vi.fn(),
  isTMA: vi.fn().mockResolvedValue(false),
  mockTelegramEnv: vi.fn(),
  emitEvent: vi.fn(),
  miniApp: { ready: vi.fn(), mount: { isAvailable: () => false } },
  viewport: { mount: { isAvailable: () => false }, expand: vi.fn() },
  themeParams: { state: vi.fn() },
  retrieveLaunchParams: vi.fn().mockReturnValue({
    initDataRaw: 'auth_date=1234567890&hash=mockHash&user=%7B%22id%22%3A1%7D',
  }),
  useSignal: vi.fn().mockReturnValue(null),
}))
