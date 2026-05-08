import { vi } from 'vitest'

const telegramMock = vi.hoisted(() => ({
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
  retrieveRawInitData: vi.fn().mockReturnValue('auth_date=1234567890&hash=mockHash&user=%7B%22id%22%3A1%7D'),
  useSignal: vi.fn().mockReturnValue(null),
  backButton: {
    show: Object.assign(vi.fn(), { isAvailable: vi.fn().mockReturnValue(true) }),
    hide: Object.assign(vi.fn(), { isAvailable: vi.fn().mockReturnValue(true) }),
    onClick: vi.fn().mockReturnValue(vi.fn()),
  },
  mainButton: {
    setParams: Object.assign(vi.fn(), { isAvailable: vi.fn().mockReturnValue(true) }),
    onClick: vi.fn().mockReturnValue(vi.fn()),
  },
  hapticFeedback: {
    selectionChanged: Object.assign(vi.fn(), { isAvailable: vi.fn().mockReturnValue(false) }),
    impactOccurred: Object.assign(vi.fn(), { isAvailable: vi.fn().mockReturnValue(false) }),
    notificationOccurred: Object.assign(vi.fn(), { isAvailable: vi.fn().mockReturnValue(false) }),
  },
}))

vi.mock('@telegram-apps/sdk-react', () => telegramMock)
