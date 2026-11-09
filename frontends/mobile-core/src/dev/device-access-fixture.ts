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
      if (scenario === 'coordinates') emit(20)
      else if (scenario === 'refining') {
        emit(500)
        timer = setTimeout(() => emit(20), 3_000)
      } else if (scenario === 'low-accuracy') emit(500)
      else if (scenario === 'unknown-accuracy') emit(null)
      else if (scenario === 'conflicting') {
        emit(80)
        emit(20, 56.75)
      } else failed(scenario === 'denied' ? 'PERMISSION_DENIED' : 'POSITION_UNAVAILABLE')
      return () => clearTimeout(timer)
    },
  }
}
