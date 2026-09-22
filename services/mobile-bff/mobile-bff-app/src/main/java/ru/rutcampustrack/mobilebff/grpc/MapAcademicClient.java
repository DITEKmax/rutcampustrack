package ru.rutcampustrack.mobilebff.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.CampusMapAssetChunk;
import ru.rutcampustrack.academic.grpc.CampusMapAssetRequest;
import ru.rutcampustrack.academic.grpc.CampusMapFormat;
import ru.rutcampustrack.academic.grpc.CampusMapFormatSlot;
import ru.rutcampustrack.academic.grpc.CampusMapFormatState;
import ru.rutcampustrack.academic.grpc.CampusMapFloorRequest;
import ru.rutcampustrack.academic.grpc.CampusMapManifestRequest;
import ru.rutcampustrack.academic.grpc.CampusMapManifestResponse;
import ru.rutcampustrack.academic.grpc.CampusMapOpenAck;
import ru.rutcampustrack.academic.grpc.CampusMapOpenRequest;
import ru.rutcampustrack.academic.grpc.CampusMapPlan;
import ru.rutcampustrack.academic.grpc.CampusMapPlanResponse;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels;
import ru.rutcampustrack.mobilebff.error.MobileBffException;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Dedicated Academic gRPC adapter for the role-scoped campus-map reader. */
@Component
public class MapAcademicClient {
    private static final int MAX_ASSET_BYTES = 10 * 1024 * 1024;

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    private final MobileGrpcAuth auth;

    public MapAcademicClient(MobileGrpcAuth auth) {
        this.auth = auth;
    }

    public ManifestResult manifest(long knownRevision) {
        if (knownRevision < 0) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Некорректная ревизия карты");
        }
        CampusMapManifestResponse response = call(() -> auth.attach(stub)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .getCampusMapManifest(CampusMapManifestRequest.newBuilder()
                        .setKnownRevision(knownRevision)
                        .build()));
        if (response.hasUnchanged()) {
            return new ManifestResult(true, response.getUnchanged().getRevision(), null);
        }
        if (!response.hasManifest()) {
            throw dependencyFailure("Academic Service вернул пустой ответ карты");
        }
        StudentMapModels.ManifestResponse manifest;
        try {
            manifest = toManifest(response.getManifest());
        } catch (MobileBffException error) {
            throw error;
        } catch (RuntimeException error) {
            throw dependencyFailure("Academic Service вернул некорректную карту");
        }
        return new ManifestResult(false, response.getManifest().getRevision(), manifest);
    }

    public StudentMapModels.PlanResponse floorPlan(String buildingId, String floorId) {
        CampusMapPlanResponse response = call(() -> auth.attach(stub)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .getCampusFloorPlan(CampusMapFloorRequest.newBuilder()
                        .setBuildingId(requiredId(buildingId, "buildingId"))
                        .setFloorId(requiredId(floorId, "floorId"))
                        .build()));
        if (!response.hasPlan()) {
            return new StudentMapModels.PlanResponse(null);
        }
        try {
            return new StudentMapModels.PlanResponse(toPlan(response.getPlan()));
        } catch (MobileBffException error) {
            throw error;
        } catch (RuntimeException error) {
            throw dependencyFailure("Academic Service вернул некорректный план карты");
        }
    }

    public Download asset(String buildingId,
                          String floorId,
                          String version,
                          StudentMapModels.Format format,
                          String assetId) {
        long versionValue = positiveLong(version, "version");
        CampusMapFormat protoFormat = toProtoFormat(format);
        Iterator<CampusMapAssetChunk> chunks = call(() -> auth.attach(stub)
                .withDeadlineAfter(15, TimeUnit.SECONDS)
                .readCampusMapAsset(CampusMapAssetRequest.newBuilder()
                        .setBuildingId(requiredId(buildingId, "buildingId"))
                        .setFloorId(requiredId(floorId, "floorId"))
                        .setVersion(versionValue)
                        .setFormat(protoFormat)
                        .setAssetId(requiredId(assetId, "assetId"))
                        .build()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long expectedOffset = 0;
        try {
            while (chunks.hasNext()) {
                CampusMapAssetChunk chunk = chunks.next();
                if (chunk.getOffset() != expectedOffset || chunk.getData().isEmpty()) {
                    throw dependencyFailure("Academic Service вернул повреждённый поток карты");
                }
                if (expectedOffset + chunk.getData().size() > MAX_ASSET_BYTES) {
                    throw new MobileBffException(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCode.PAYLOAD_TOO_LARGE,
                            "Файл карты превышает допустимый размер");
                }
                bytes.writeBytes(chunk.getData().toByteArray());
                expectedOffset += chunk.getData().size();
            }
        } catch (MobileBffException error) {
            throw error;
        } catch (StatusRuntimeException error) {
            throw mapStatus(error);
        } catch (RuntimeException error) {
            throw dependencyFailure("Academic Service временно недоступен");
        }
        if (bytes.size() == 0) {
            throw dependencyFailure("Academic Service вернул пустой файл карты");
        }
        return new Download(contentType(format), bytes.toByteArray());
    }

    public void recordFloorOpen(String buildingId, String floorId, UUID intentId) {
        if (intentId == null) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Ключ идемпотентности открытия карты обязателен");
        }
        CampusMapOpenAck response = call(() -> auth.attach(stub)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .recordCampusFloorOpen(CampusMapOpenRequest.newBuilder()
                        .setBuildingId(requiredId(buildingId, "buildingId"))
                        .setFloorId(requiredId(floorId, "floorId"))
                        .setIntentId(intentId.toString())
                        .build()));
        if (!response.getAccepted()) {
            throw new MobileBffException(HttpStatus.CONFLICT, ProblemCode.REQUEST_CONFLICT,
                    "Academic Service не принял открытие карты");
        }
    }

    private static StudentMapModels.ManifestResponse toManifest(
            ru.rutcampustrack.academic.grpc.CampusMapManifest source) {
        return new StudentMapModels.ManifestResponse(
                source.getSchemaVersion(),
                source.getValidationPolicyVersion(),
                Long.toString(source.getRevision()),
                source.getBuildingsList().stream()
                        .map(building -> new StudentMapModels.Building(
                                building.getId(),
                                building.getLabel(),
                                building.getFloorsList().stream()
                                        .map(floor -> new StudentMapModels.Floor(
                                                floor.getId(),
                                                floor.getLabel(),
                                                floor.hasPlan() ? toPlan(floor.getPlan()) : null))
                                        .toList()))
                        .toList());
    }

    private static StudentMapModels.Plan toPlan(CampusMapPlan source) {
        return new StudentMapModels.Plan(
                source.getBuildingId(),
                source.getFloorId(),
                Long.toString(source.getVersion()),
                source.getLabel(),
                toSlot(source.getPng()),
                toSlot(source.getSvg()));
    }

    private static StudentMapModels.FormatSlot toSlot(CampusMapFormatSlot source) {
        StudentMapModels.Format format = switch (source.getFormat()) {
            case CAMPUS_MAP_FORMAT_PNG -> StudentMapModels.Format.png;
            case CAMPUS_MAP_FORMAT_SVG -> StudentMapModels.Format.svg;
            default -> throw dependencyFailure("Academic Service вернул неизвестный формат карты");
        };
        StudentMapModels.FormatState state = switch (source.getState()) {
            case CAMPUS_MAP_FORMAT_STATE_ABSENT -> StudentMapModels.FormatState.absent;
            case CAMPUS_MAP_FORMAT_STATE_PROCESSING -> StudentMapModels.FormatState.processing;
            case CAMPUS_MAP_FORMAT_STATE_READY -> StudentMapModels.FormatState.ready;
            case CAMPUS_MAP_FORMAT_STATE_FAILED -> StudentMapModels.FormatState.failed;
            default -> throw dependencyFailure("Academic Service вернул неизвестное состояние карты");
        };
        return new StudentMapModels.FormatSlot(
                format,
                state,
                source.getContentType(),
                source.hasAssetId() ? source.getAssetId() : null,
                source.getBytes(),
                source.hasSha256() ? source.getSha256() : null,
                source.hasWidth() ? source.getWidth() : null,
                source.hasHeight() ? source.getHeight() : null,
                source.getViewBoxCount() == 0 ? null : List.copyOf(source.getViewBoxList()));
    }

    private static CampusMapFormat toProtoFormat(StudentMapModels.Format format) {
        if (format == null) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Формат карты обязателен");
        }
        return format == StudentMapModels.Format.png
                ? CampusMapFormat.CAMPUS_MAP_FORMAT_PNG
                : CampusMapFormat.CAMPUS_MAP_FORMAT_SVG;
    }

    private static String contentType(StudentMapModels.Format format) {
        return format == StudentMapModels.Format.png ? "image/png" : "image/svg+xml";
    }

    private static String requiredId(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Некорректный " + field);
        }
        return value;
    }

    private static long positiveLong(String value, String field) {
        try {
            return Long.parseLong(requiredId(value, field));
        } catch (NumberFormatException error) {
            throw new MobileBffException(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_REQUEST,
                    "Некорректный " + field);
        }
    }

    private static <T> T call(Callable<T> action) {
        try {
            return action.call();
        } catch (MobileBffException error) {
            throw error;
        } catch (StatusRuntimeException error) {
            throw mapStatus(error);
        } catch (Exception error) {
            throw dependencyFailure("Academic Service временно недоступен");
        }
    }

    private static MobileBffException mapStatus(StatusRuntimeException error) {
        return switch (error.getStatus().getCode()) {
            case UNAUTHENTICATED -> new MobileBffException(HttpStatus.UNAUTHORIZED,
                    ProblemCode.INVALID_SESSION, "Сессия недействительна");
            case PERMISSION_DENIED -> new MobileBffException(HttpStatus.FORBIDDEN,
                    ProblemCode.OUT_OF_SCOPE, "Карта недоступна в текущей роли или scope");
            case INVALID_ARGUMENT -> new MobileBffException(HttpStatus.BAD_REQUEST,
                    ProblemCode.INVALID_REQUEST, "Academic Service отклонил запрос карты");
            case NOT_FOUND -> new MobileBffException(HttpStatus.NOT_FOUND,
                    ProblemCode.OUT_OF_SCOPE, "Карта не найдена");
            case FAILED_PRECONDITION -> new MobileBffException(HttpStatus.CONFLICT,
                    ProblemCode.REQUEST_CONFLICT, "Формат карты ещё не опубликован");
            case RESOURCE_EXHAUSTED -> new MobileBffException(HttpStatus.PAYLOAD_TOO_LARGE,
                    ProblemCode.PAYLOAD_TOO_LARGE, "Файл карты слишком большой");
            default -> dependencyFailure("Academic Service временно недоступен");
        };
    }

    private static MobileBffException dependencyFailure(String message) {
        return new MobileBffException(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemCode.DEPENDENCY_UNAVAILABLE, message);
    }

    public record ManifestResult(boolean unchanged, long revision,
                                 StudentMapModels.ManifestResponse manifest) {
    }

    public record Download(String contentType, byte[] bytes) {
    }
}
