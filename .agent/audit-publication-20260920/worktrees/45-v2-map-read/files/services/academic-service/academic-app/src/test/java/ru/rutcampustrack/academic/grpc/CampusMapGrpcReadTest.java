package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.rutcampustrack.academic.homework.HomeworkStudentService;
import ru.rutcampustrack.academic.map.CampusMapReadException;
import ru.rutcampustrack.academic.map.CampusMapReadModels.Asset;
import ru.rutcampustrack.academic.map.CampusMapReadService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CampusMapGrpcReadTest {
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void assetChunksUse64KiBBoundAndMonotonicOffsets() {
        CampusMapReadService mapService = mock(CampusMapReadService.class);
        byte[] content = new byte[131_073];
        when(mapService.readAsset(anyString(), anyString(), anyLong(),
                eq(ru.rutcampustrack.academic.map.CampusMapFormat.PNG), anyString(),
                any(InternalJwtClaims.class), any(BooleanSupplier.class)))
                .thenReturn(Optional.of(new Asset(content)));
        AcademicGrpcServiceImpl service = service(mapService);
        RecordingObserver<CampusMapAssetChunk> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims()).run(
                () -> service.readCampusMapAsset(assetRequest(), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).extracting(CampusMapAssetChunk::getOffset)
                .containsExactly(0L, 65_536L, 131_072L);
        assertThat(observer.values).extracting(chunk -> chunk.getData().size())
                .containsExactly(65_536, 65_536, 1);
    }

    @Test
    void cancelledServerStreamStopsBeforeCompletion() {
        CampusMapReadService mapService = mock(CampusMapReadService.class);
        byte[] content = new byte[131_073];
        when(mapService.readAsset(anyString(), anyString(), anyLong(),
                eq(ru.rutcampustrack.academic.map.CampusMapFormat.PNG), anyString(),
                any(InternalJwtClaims.class), any(BooleanSupplier.class)))
                .thenReturn(Optional.of(new Asset(content)));
        AcademicGrpcServiceImpl service = service(mapService);
        @SuppressWarnings("unchecked")
        ServerCallStreamObserver<CampusMapAssetChunk> observer = mock(ServerCallStreamObserver.class);
        when(observer.isCancelled()).thenReturn(false, false, false, true);

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims()).run(
                () -> service.readCampusMapAsset(assetRequest(), observer));

        verify(observer, times(1)).onNext(any(CampusMapAssetChunk.class));
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"manifest", "floor", "asset"})
    void missingSignedIdentityReturnsPermissionDeniedWithoutMapCallOrEmission(String rpc) {
        CampusMapReadService mapService = mock(CampusMapReadService.class);
        AcademicGrpcServiceImpl service = service(mapService);

        switch (rpc) {
            case "manifest" -> {
                RecordingObserver<CampusMapManifestResponse> observer = new RecordingObserver<>();
                service.getCampusMapManifest(CampusMapManifestRequest.getDefaultInstance(), observer);
                assertNoEmission(observer, Status.Code.PERMISSION_DENIED);
            }
            case "floor" -> {
                RecordingObserver<CampusMapPlanResponse> observer = new RecordingObserver<>();
                service.getCampusFloorPlan(CampusMapFloorRequest.getDefaultInstance(), observer);
                assertNoEmission(observer, Status.Code.PERMISSION_DENIED);
            }
            case "asset" -> {
                RecordingObserver<CampusMapAssetChunk> observer = new RecordingObserver<>();
                service.readCampusMapAsset(assetRequest(), observer);
                assertNoEmission(observer, Status.Code.PERMISSION_DENIED);
            }
            default -> throw new AssertionError("unknown RPC " + rpc);
        }

        verifyNoInteractions(mapService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"manifest", "floor", "asset"})
    void typedMapFailureUsesSafeStatusWithoutEmission(String rpc) {
        CampusMapReadService mapService = mock(CampusMapReadService.class);
        CampusMapReadException failure = CampusMapReadException.of(
                CampusMapReadException.Code.DATA_LOSS, "database detail must not escape");
        if ("manifest".equals(rpc)) {
            when(mapService.readManifest(anyLong(), any(InternalJwtClaims.class))).thenThrow(failure);
        } else if ("floor".equals(rpc)) {
            when(mapService.readFloorPlan(anyString(), anyString(), any(InternalJwtClaims.class)))
                    .thenThrow(failure);
        } else {
            when(mapService.readAsset(anyString(), anyString(), anyLong(),
                    any(ru.rutcampustrack.academic.map.CampusMapFormat.class), anyString(),
                    any(InternalJwtClaims.class), any(BooleanSupplier.class)))
                    .thenThrow(failure);
        }
        AcademicGrpcServiceImpl service = service(mapService);

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims()).run(() -> {
            if ("manifest".equals(rpc)) {
                RecordingObserver<CampusMapManifestResponse> observer = new RecordingObserver<>();
                service.getCampusMapManifest(CampusMapManifestRequest.getDefaultInstance(), observer);
                assertNoEmission(observer, Status.Code.DATA_LOSS);
                assertThat(Status.fromThrowable(observer.error).getDescription())
                        .isEqualTo("campus map data is inconsistent");
            } else if ("floor".equals(rpc)) {
                RecordingObserver<CampusMapPlanResponse> observer = new RecordingObserver<>();
                service.getCampusFloorPlan(CampusMapFloorRequest.getDefaultInstance(), observer);
                assertNoEmission(observer, Status.Code.DATA_LOSS);
                assertThat(Status.fromThrowable(observer.error).getDescription())
                        .isEqualTo("campus map data is inconsistent");
            } else {
                RecordingObserver<CampusMapAssetChunk> observer = new RecordingObserver<>();
                service.readCampusMapAsset(assetRequest(), observer);
                assertNoEmission(observer, Status.Code.DATA_LOSS);
                assertThat(Status.fromThrowable(observer.error).getDescription())
                        .isEqualTo("campus map data is inconsistent");
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"manifest", "floor", "asset"})
    void downstreamIllegalStateUsesInternalWithoutEmission(String rpc) {
        CampusMapReadService mapService = mock(CampusMapReadService.class);
        IllegalStateException failure = new IllegalStateException("downstream detail must not escape");
        if ("manifest".equals(rpc)) {
            when(mapService.readManifest(anyLong(), any(InternalJwtClaims.class))).thenThrow(failure);
        } else if ("floor".equals(rpc)) {
            when(mapService.readFloorPlan(anyString(), anyString(), any(InternalJwtClaims.class)))
                    .thenThrow(failure);
        } else {
            when(mapService.readAsset(anyString(), anyString(), anyLong(),
                    any(ru.rutcampustrack.academic.map.CampusMapFormat.class), anyString(),
                    any(InternalJwtClaims.class), any(BooleanSupplier.class)))
                    .thenThrow(failure);
        }
        AcademicGrpcServiceImpl service = service(mapService);

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims()).run(() -> {
            if ("manifest".equals(rpc)) {
                RecordingObserver<CampusMapManifestResponse> observer = new RecordingObserver<>();
                service.getCampusMapManifest(CampusMapManifestRequest.getDefaultInstance(), observer);
                assertNoEmission(observer, Status.Code.INTERNAL);
                assertThat(Status.fromThrowable(observer.error).getDescription())
                        .isEqualTo("campus map read failed");
            } else if ("floor".equals(rpc)) {
                RecordingObserver<CampusMapPlanResponse> observer = new RecordingObserver<>();
                service.getCampusFloorPlan(CampusMapFloorRequest.getDefaultInstance(), observer);
                assertNoEmission(observer, Status.Code.INTERNAL);
                assertThat(Status.fromThrowable(observer.error).getDescription())
                        .isEqualTo("campus map read failed");
            } else {
                RecordingObserver<CampusMapAssetChunk> observer = new RecordingObserver<>();
                service.readCampusMapAsset(assetRequest(), observer);
                assertNoEmission(observer, Status.Code.INTERNAL);
                assertThat(Status.fromThrowable(observer.error).getDescription())
                        .isEqualTo("campus map read failed");
            }
        });
    }

    @Test
    void invalidReturnedContentFailsBeforeFirstChunk() {
        CampusMapReadService mapService = mock(CampusMapReadService.class);
        when(mapService.readAsset(anyString(), anyString(), anyLong(),
                eq(ru.rutcampustrack.academic.map.CampusMapFormat.PNG), anyString(),
                any(InternalJwtClaims.class), any(BooleanSupplier.class)))
                .thenReturn(Optional.of(new Asset(new byte[0])));
        AcademicGrpcServiceImpl service = service(mapService);
        RecordingObserver<CampusMapAssetChunk> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims()).run(
                () -> service.readCampusMapAsset(assetRequest(), observer));

        assertNoEmission(observer, Status.Code.DATA_LOSS);
    }

    private static void assertNoEmission(RecordingObserver<?> observer, Status.Code code) {
        assertThat(observer.error).isNotNull();
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(code);
        assertThat(observer.values).isEmpty();
        assertThat(observer.completed).isFalse();
    }

    private static AcademicGrpcServiceImpl service(CampusMapReadService mapService) {
        return new AcademicGrpcServiceImpl(
                mock(AcademicReadService.class),
                mock(GroupRepository.class),
                mock(UserRepository.class),
                mock(SubjectRepository.class),
                mock(TeacherSubjectGroupRepository.class),
                mock(HomeworkRepository.class),
                mock(HomeworkCompletionRepository.class),
                mock(HeadmanRateLimiter.class),
                mock(HomeworkStudentService.class),
                mapService);
    }

    private static CampusMapAssetRequest assetRequest() {
        return CampusMapAssetRequest.newBuilder()
                .setBuildingId("20")
                .setFloorId("200")
                .setVersion(3L)
                .setFormat(CampusMapFormat.CAMPUS_MAP_FORMAT_PNG)
                .setAssetId("700")
                .build();
    }

    private static InternalJwtClaims claims() {
        return new InternalJwtClaims(100L, SESSION_ID, 1L, 1L,
                "STUDENT", "ACTIVE", 10L, false, false);
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
