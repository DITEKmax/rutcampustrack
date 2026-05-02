package ru.rutcampustrack.attendance.geofence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.academic.grpc.GeofenceResponse;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeofenceServiceTest {

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Test
    void isWithinCampus_usesStaleGeofenceWhenRefreshFailsAfterTtl() throws Exception {
        when(academicGrpcClient.getCampusGeofence())
                .thenReturn(mainCampusGeofence())
                .thenThrow(new AcademicServiceUnavailableException("Academic Service unavailable"));

        GeofenceService service = new GeofenceService(academicGrpcClient);
        service.refresh();
        expireCache(service);

        assertThat(service.isWithinCampus(55.788204, 37.606762)).isTrue();
        verify(academicGrpcClient, times(2)).getCampusGeofence();
    }

    @Test
    void isWithinCampus_withoutCachedGeofencePropagatesRefreshFailure() {
        when(academicGrpcClient.getCampusGeofence())
                .thenThrow(new AcademicServiceUnavailableException("Academic Service unavailable"));

        GeofenceService service = new GeofenceService(academicGrpcClient);

        assertThatThrownBy(() -> service.isWithinCampus(55.788204, 37.606762))
                .isInstanceOf(AcademicServiceUnavailableException.class);
    }

    private static GeofenceResponse mainCampusGeofence() {
        return GeofenceResponse.newBuilder()
                .setLat(55.788204)
                .setLng(37.606762)
                .setRadiusM(200)
                .build();
    }

    private static void expireCache(GeofenceService service) throws Exception {
        Field field = GeofenceService.class.getDeclaredField("lastRefreshed");
        field.setAccessible(true);
        field.set(service, Instant.now().minus(Duration.ofMinutes(31)));
    }
}
