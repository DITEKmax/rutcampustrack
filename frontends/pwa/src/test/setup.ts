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
