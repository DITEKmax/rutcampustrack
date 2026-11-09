import type { GeoSource } from '../device-access/geolocation'

/** Used only by the existing opt-in synthetic fixture shells, never by a live session. */
export function fixtureGeoSource(source: GeoSource['source'], scenario: string | null): GeoSource {
  return {
    source,
    watch(sample, failed) {
      const emit = (accuracy: number | null, latitude = 55.75) =>
        sample({
          latitude,
          longitude: 37.62,
          accuracy,
          timestamp: source === 'TMA' ? null : Date.now(),
          receivedAt: Date.now(),
        })
      let timer: ReturnType<typeof setTimeout> | undefined
      timer = setTimeout(
        () => {
          if (scenario === 'coordinates') emit(20)
          else if (scenario === 'refining' || scenario === 'low-accuracy') emit(500)
          else if (scenario === 'unknown-accuracy') emit(null)
          else if (scenario === 'conflicting') emit(80, 56.75)
          else failed(scenario === 'denied' ? 'PERMISSION_DENIED' : 'POSITION_UNAVAILABLE')
        },
        scenario === 'refining' ? 1_000 : 1,
      )
      return () => clearTimeout(timer)
    },
  }
}
