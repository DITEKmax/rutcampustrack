package ru.rutcampustrack.attendance.geofence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

@ExtendWith(MockitoExtension.class)
class GeofenceServiceTest {

    @Mock private AcademicGrpcClient academicGrpcClient;

    @Test
    void legacyTwoHundredMeterConfigurationRemainsConfigDriven() {
        when(academicGrpcClient.getCampusGeofence())
                .thenReturn(
                        GeofenceResponse.newBuilder()
                                .setLat(55.788328)
                                .setLng(37.606637)
                                .setRadiusM(200)
                                .build());
        GeofenceService service = new GeofenceService(academicGrpcClient);
        double insideLat = 55.788328 + Math.toDegrees(199.9 / 6_371_000.0);
        double outsideLat = 55.788328 + Math.toDegrees(200.1 / 6_371_000.0);

        assertThat(service.evaluate(55.788328, 37.606637).accepted()).isTrue();
        assertThat(service.evaluate(insideLat, 37.606637).accepted()).isTrue();
        assertThat(service.evaluate(outsideLat, 37.606637).accepted()).isFalse();
    }

    @Test
    void ownerFourHundredMeterConfigurationAcceptsThreeHundredButRejectsFourHundredOne() {
        when(academicGrpcClient.getCampusGeofence())
                .thenReturn(
                        GeofenceResponse.newBuilder()
                                .setLat(55.788328)
                                .setLng(37.606637)
                                .setRadiusM(400)
                                .build());
        var service = new GeofenceService(academicGrpcClient);
        assertThat(
                        service.evaluate(55.788328 + Math.toDegrees(300 / 6_371_000.0), 37.606637)
                                .accepted())
                .isTrue();
        assertThat(
                        service.evaluate(55.788328 + Math.toDegrees(401 / 6_371_000.0), 37.606637)
                                .accepted())
                .isFalse();
        assertThat(service.cachedConfiguration())
                .isEqualTo(new GeofenceService.Configuration(55.788328, 37.606637, 400));
    }

    @Test
    void configuredFourHundredMeterBoundaryIsInclusive() {
        when(academicGrpcClient.getCampusGeofence())
                .thenReturn(
                        GeofenceResponse.newBuilder().setLat(0).setLng(0).setRadiusM(400).build());
        var service = new GeofenceService(academicGrpcClient);
        var boundary = service.evaluate(0, Math.toDegrees(400 / 6_371_000.0));
        assertThat(boundary.distanceMeters()).isEqualTo(400.0);
        assertThat(boundary.accepted()).isTrue();
        assertThat(service.evaluate(0, Math.toDegrees(400.001 / 6_371_000.0)).accepted()).isFalse();
    }

    @Test
    void coldAdvisoryCacheDoesNotCallAcademic() {
        var service = new GeofenceService(academicGrpcClient);
        assertThat(service.cachedConfiguration()).isNull();
        org.mockito.Mockito.verifyNoInteractions(academicGrpcClient);
    }

    @Test
    void expiredAdvisoryCacheDoesNotRefreshButBusinessEvaluationStillDoes() throws Exception {
        when(academicGrpcClient.getCampusGeofence())
                .thenReturn(mainCampusGeofence())
                .thenThrow(new AcademicServiceUnavailableException("synthetic unavailable"));
        var service = new GeofenceService(academicGrpcClient);
        service.refresh();
        expireCache(service);
        assertThat(service.cachedConfiguration()).isNotNull();
        verify(academicGrpcClient, times(1)).getCampusGeofence();
        assertThat(service.isWithinCampus(55.788204, 37.606762)).isTrue();
        verify(academicGrpcClient, times(2)).getCampusGeofence();
    }

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
