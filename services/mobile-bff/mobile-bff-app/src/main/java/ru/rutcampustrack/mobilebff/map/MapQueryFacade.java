package ru.rutcampustrack.mobilebff.map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.mobilebff.contract.model.StudentMapModels;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MapAcademicClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.UUID;

/** One role-scoped map reader shared by canonical and compatibility routes. */
@Service
public class MapQueryFacade {
    private final MapAcademicClient academic;
    private final MobileRequestContext requestContext;

    public MapQueryFacade(MapAcademicClient academic, MobileRequestContext requestContext) {
        this.academic = academic;
        this.requestContext = requestContext;
    }

    public MapAcademicClient.ManifestResult manifest(String ifNoneMatch) {
        requireReadScope();
        return academic.manifest(parseRevision(ifNoneMatch));
    }

    public StudentMapModels.PlanResponse floorPlan(String buildingId, String floorId) {
        requireReadScope();
        return academic.floorPlan(buildingId, floorId);
    }

    public MapAcademicClient.Download asset(String buildingId,
                                            String floorId,
                                            String version,
                                            StudentMapModels.Format format,
                                            String assetId) {
        requireReadScope();
        return academic.asset(buildingId, floorId, version, format, assetId);
    }

    public void recordFloorOpen(String buildingId, String floorId, UUID intentId) {
        requireReadScope();
        academic.recordFloorOpen(buildingId, floorId, intentId);
    }

    private void requireReadScope() {
        InternalJwtClaims claims = requestContext.claims();
        if (claims == null) {
            throw new MobileBffException(HttpStatus.UNAUTHORIZED, ProblemCode.INVALID_SESSION,
                    "Сессия недействительна");
        }
        if ("STUDENT".equalsIgnoreCase(claims.domainRole())) {
            if (claims.userId() <= 0 || claims.groupId() == null || claims.groupId() <= 0) {
                throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.OUT_OF_SCOPE,
                        "Не хватает student/group scope для карты");
            }
            return;
        }
        if ("TEACHER".equalsIgnoreCase(claims.domainRole())
                && claims.userId() > 0
                && !claims.readOnly()
                && "ACTIVE".equalsIgnoreCase(claims.status())) {
            return;
        }
        throw new MobileBffException(HttpStatus.FORBIDDEN, ProblemCode.WRONG_ROLE,
                "Карта доступна ролям STUDENT и TEACHER");
    }

    private static long parseRevision(String ifNoneMatch) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return 0L;
        }
        String value = ifNoneMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : 0L;
        } catch (NumberFormatException error) {
            return 0L;
        }
    }
}
