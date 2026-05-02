import { describe, it, expect } from 'vitest';
import {
  GPS_DENIED_MESSAGE,
  GPS_TIMEOUT_MESSAGE,
  GPS_UNAVAILABLE_MESSAGE,
  mapCheckinError,
  mapGeolocationError,
} from './checkin-error-mapper';

function geoError(code: number): GeolocationPositionError {
  return {
    code,
    message: 'mock geolocation error',
    PERMISSION_DENIED: 1,
    POSITION_UNAVAILABLE: 2,
    TIMEOUT: 3,
  } as GeolocationPositionError;
}

describe('mapCheckinError', () => {
  it('maps 403 to teacher-blocked copy', () => {
    expect(mapCheckinError(403)).toBe('Геоотметка заблокирована преподавателем.');
  });

  it('maps 404 to lesson-not-found copy', () => {
    expect(mapCheckinError(404)).toBe('Активное занятие не найдено.');
  });

  it('maps 409 to already-marked copy', () => {
    expect(mapCheckinError(409)).toBe('Вы уже отмечены на этом занятии.');
  });

  it('maps 422 to out-of-range copy', () => {
    expect(mapCheckinError(422)).toBe(
      'Вы находитесь слишком далеко от кампуса. Геоотметка недоступна.',
    );
  });

  it('maps 429 to rate-limit copy', () => {
    expect(mapCheckinError(429)).toBe(
      'Слишком много попыток. Подождите минуту и попробуйте снова.',
    );
  });

  it('maps 500 to generic copy', () => {
    expect(mapCheckinError(500)).toBe('Не удалось отправить отметку. Попробуйте ещё раз.');
  });

  it('maps 0 (network unreachable) to generic copy', () => {
    expect(mapCheckinError(0)).toBe('Не удалось отправить отметку. Попробуйте ещё раз.');
  });
});

describe('GPS_DENIED_MESSAGE constant', () => {
  it('matches the UI-SPEC GPS denied copy', () => {
    expect(GPS_DENIED_MESSAGE).toBe(
      'Нет доступа к геолокации. Разрешите доступ в настройках браузера и попробуйте снова.',
    );
  });
});

describe('mapGeolocationError', () => {
  it('maps permission denied to permission copy', () => {
    expect(mapGeolocationError(geoError(1))).toBe(GPS_DENIED_MESSAGE);
  });

  it('maps position unavailable to GPS unavailable copy', () => {
    expect(mapGeolocationError(geoError(2))).toBe(GPS_UNAVAILABLE_MESSAGE);
  });

  it('maps timeout to timeout copy', () => {
    expect(mapGeolocationError(geoError(3))).toBe(GPS_TIMEOUT_MESSAGE);
  });
});
