package ru.rutcampustrack.attendance.report.planned;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.attendance.exception.*;
import ru.rutcampustrack.attendance.report.PlannedReportRenderer;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.shared.security.*;

/** Data-only rendering. Job/lease lifecycle and fresh owner checks remain in Academic. */
@GrpcService
public final class PlannedReportGrpcService
        extends ReportRenderingServiceGrpc.ReportRenderingServiceImplBase {
    private final InternalJwtValidator validator;
    private final PlannedReportRenderer renderer;

    public PlannedReportGrpcService(
            InternalJwtValidator validator, PlannedReportRenderer renderer) {
        this.validator = validator;
        this.renderer = renderer;
    }

    @Override
    public void renderReport(
            RenderReportRequest request, StreamObserver<RenderReportResponse> observer) {
        try {
            String capability = PlannedReportIdentityInterceptor.requireCapability();
            if (!request.hasPlan() || !request.getUnknownFields().asMap().isEmpty())
                throw Status.INVALID_ARGUMENT.asRuntimeException();
            var source = request.getPlan();
            String hash = AdmittedReportPlan.digest(source);
            var actor =
                    validator.validateAcademicReport(
                            capability,
                            source.getReportKind(),
                            hash,
                            source.getJobId(),
                            source.getLeaseId());
            var plan = AdmittedReportPlan.admit(source, actor);
            if (Context.current().isCancelled()) throw Status.CANCELLED.asRuntimeException();
            RenderReportResponse result = renderer.render(plan);
            // A slow renderer never discloses bytes after the exact render admission expired.
            var after =
                    validator.validateAcademicReport(
                            capability,
                            source.getReportKind(),
                            hash,
                            source.getJobId(),
                            source.getLeaseId());
            if (!actor.equals(after)) throw Status.UNAUTHENTICATED.asRuntimeException();
            if (Context.current().isCancelled()) throw Status.CANCELLED.asRuntimeException();
            observer.onNext(result);
            observer.onCompleted();
        } catch (InternalJwtException denied) {
            observer.onError(
                    Status.UNAUTHENTICATED
                            .withDescription("Report authority expired or invalid")
                            .asRuntimeException());
        } catch (AccessDeniedException denied) {
            observer.onError(
                    Status.PERMISSION_DENIED
                            .withDescription("Report scope denied")
                            .asRuntimeException());
        } catch (ReportExportTooLargeException | PayloadTooLargeException tooLarge) {
            observer.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription("Report exceeds its size limit")
                            .asRuntimeException());
        } catch (ReportValidationException
                | BadRequestException
                | IllegalArgumentException
                | java.time.DateTimeException invalid) {
            observer.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid report plan or selector")
                            .asRuntimeException());
        } catch (StatusRuntimeException failure) {
            Status.Code code = failure.getStatus().getCode();
            if (!java.util.Set.of(
                            Status.Code.UNAUTHENTICATED,
                            Status.Code.PERMISSION_DENIED,
                            Status.Code.INVALID_ARGUMENT,
                            Status.Code.RESOURCE_EXHAUSTED,
                            Status.Code.FAILED_PRECONDITION,
                            Status.Code.CANCELLED)
                    .contains(code)) code = Status.Code.UNAVAILABLE;
            observer.onError(
                    Status.fromCode(code)
                            .withDescription("Report rendering unavailable")
                            .asRuntimeException());
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("Report rendering unavailable")
                            .asRuntimeException());
        }
    }

    @Override
    public void readAdminGroupStats(
            RenderReportRequest request,
            StreamObserver<ru.rutcampustrack.attendance.grpc.AdminGroupStatsSnapshot> observer) {
        try {
            String capability = PlannedReportIdentityInterceptor.requireCapability();
            if (!request.hasPlan() || !request.getUnknownFields().asMap().isEmpty())
                throw Status.INVALID_ARGUMENT.asRuntimeException();
            var source = request.getPlan();
            if (!"ADMIN_GROUP_ATTENDANCE".equals(source.getReportKind()))
                throw Status.PERMISSION_DENIED.asRuntimeException();
            String hash = AdmittedReportPlan.digest(source);
            var actor =
                    validator.validateAcademicReport(
                            capability,
                            source.getReportKind(),
                            hash,
                            source.getJobId(),
                            source.getLeaseId());
            var plan = AdmittedReportPlan.admit(source, actor);
            if (Context.current().isCancelled()) throw Status.CANCELLED.asRuntimeException();
            var result = renderer.readAdminGroupStats(plan);
            if (result.getSerializedSize() > AdmittedReportPlan.MAX_BYTES)
                throw Status.RESOURCE_EXHAUSTED.asRuntimeException();
            // A slow renderer never discloses bytes after the exact render admission expired.
            var after =
                    validator.validateAcademicReport(
                            capability,
                            source.getReportKind(),
                            hash,
                            source.getJobId(),
                            source.getLeaseId());
            if (!actor.equals(after)) throw Status.UNAUTHENTICATED.asRuntimeException();
            if (Context.current().isCancelled()) throw Status.CANCELLED.asRuntimeException();
            observer.onNext(result);
            observer.onCompleted();
        } catch (InternalJwtException denied) {
            observer.onError(
                    Status.UNAUTHENTICATED
                            .withDescription("Report authority expired or invalid")
                            .asRuntimeException());
        } catch (AccessDeniedException denied) {
            observer.onError(
                    Status.PERMISSION_DENIED
                            .withDescription("Report scope denied")
                            .asRuntimeException());
        } catch (ReportExportTooLargeException | PayloadTooLargeException tooLarge) {
            observer.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription("Report exceeds its size limit")
                            .asRuntimeException());
        } catch (ReportValidationException
                | BadRequestException
                | IllegalArgumentException
                | java.time.DateTimeException invalid) {
            observer.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Invalid report plan or selector")
                            .asRuntimeException());
        } catch (StatusRuntimeException failure) {
            Status.Code code = failure.getStatus().getCode();
            if (!java.util.Set.of(
                            Status.Code.UNAUTHENTICATED,
                            Status.Code.PERMISSION_DENIED,
                            Status.Code.INVALID_ARGUMENT,
                            Status.Code.RESOURCE_EXHAUSTED,
                            Status.Code.FAILED_PRECONDITION,
                            Status.Code.CANCELLED)
                    .contains(code)) code = Status.Code.UNAVAILABLE;
            observer.onError(
                    Status.fromCode(code)
                            .withDescription("Report rendering unavailable")
                            .asRuntimeException());
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("Report rendering unavailable")
                            .asRuntimeException());
        }
    }
}
