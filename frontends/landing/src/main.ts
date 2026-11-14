import { startLanding } from './start-landing'

void startLanding(async () => (await import('./landing-app')).createLandingApp())
