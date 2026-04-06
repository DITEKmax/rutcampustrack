import '@testing-library/jest-dom'
import { vi } from 'vitest'

Object.defineProperty(global.navigator, 'geolocation', {
  value: {
    getCurrentPosition: vi.fn(),
    watchPosition: vi.fn(),
    clearWatch: vi.fn(),
  },
  configurable: true,
})

Object.defineProperty(global.navigator, 'onLine', {
  value: true,
  writable: true,
  configurable: true,
})

// Mock scrollIntoView for jsdom
Element.prototype.scrollIntoView = vi.fn()

Object.defineProperty(global, 'Notification', {
  value: { permission: 'default', requestPermission: vi.fn().mockResolvedValue('granted') },
  configurable: true,
  writable: true,
})

Object.defineProperty(global.navigator, 'serviceWorker', {
  value: {
    ready: Promise.resolve({
      pushManager: {
        subscribe: vi.fn(),
        getSubscription: vi.fn().mockResolvedValue(null),
      },
    }),
  },
  configurable: true,
})
