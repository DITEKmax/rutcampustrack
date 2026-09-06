# Runtime evidence

Local PWA fixture server was started only on `127.0.0.1:4174` with `VITE_MOBILE_FIXTURE_MODE=true`. Browser navigation to `/?fixtureGeo=coordinates` showed the loaded Russian Today screen, its current/upcoming lesson block, three lessons for today, and navigation; it did not request real authentication or browser geolocation.

The lost-response scenario has no existing browser fixture transport, so its runtime evidence is the targeted recovery test rather than a fabricated network route. TMA is covered by its existing entry/auth suite and production build.
