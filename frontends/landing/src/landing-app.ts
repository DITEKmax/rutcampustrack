import { createApp } from 'vue'
import LandingApp from './LandingApp.vue'
import './landing.pcss'

export function createLandingApp() {
  return createApp(LandingApp)
}
