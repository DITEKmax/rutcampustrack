package ru.rutcampustrack.academic.exportjobs;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.adminattendance.AdminAttendanceExportService;
import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels;
import ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels;
import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.Render;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.export.AdminRegistryExportService;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.security.RequireRole;

import java.util.UUID;

/** Only a live, exact job/lease report-purpose capability reaches this real HTTP boundary. */
@RestController
public class AcademicReportRenderController {
    private final ExportJobService jobs;
    private final RequestContext context;
    private final AdminRegistryExportService registry;
    private final AdminAttendanceExportService attendance;
    private final ObjectMapper mapper;

    public AcademicReportRenderController(
            ExportJobService jobs,
            RequestContext context,
            AdminRegistryExportService registry,
            AdminAttendanceExportService attendance,
            ObjectMapper mapper) {
        this.jobs = jobs;
        this.context = context;
        this.registry = registry;
        this.attendance = attendance;
        this.mapper = mapper;
    }

    @io.swagger.v3.oas.annotations.Hidden
    @PostMapping("/academic/export-jobs/{id}/render")
    @RequireRole(value = UserRole.ADMIN, adminShared = true)
    public ResponseEntity<byte[]> render(
            @PathVariable UUID id, @Valid @RequestBody Render request) {
        var job = jobs.renderPlan(id, request.leaseId(), context.getClaims());
        var authority = jobs.renderAdmission(job);
        if (authority.adminProfile() == null || !authority.adminProfile().assigned())
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        context.setAdmittedAdminProfile(authority.adminProfile());
        ExportJobService.GeneratedFile file =
                switch (job.identity().report().kind()) {
                    case ADMIN_USERS -> {
                        var result =
                                registry.users(
                                        mapper.convertValue(
                                                job.identity().report().adminUsers(),
                                                RegistryExportModels.Users.class),
                                        job.id(),
                                        job.leaseId());
                        yield new ExportJobService.GeneratedFile(
                                result.content(),
                                result.contentType(),
                                result.rowCount(),
                                result.requiresFull(),
                                null);
                    }
                    case ADMIN_GROUPS -> {
                        var result =
                                registry.groups(
                                        mapper.convertValue(
                                                job.identity().report().adminGroups(),
                                                RegistryExportModels.Groups.class),
                                        job.id(),
                                        job.leaseId());
                        yield new ExportJobService.GeneratedFile(
                                result.content(),
                                result.contentType(),
                                result.rowCount(),
                                false,
                                null);
                    }
                    case ADMIN_GROUP_ATTENDANCE -> {
                        var result =
                                attendance.generate(
                                        mapper.convertValue(
                                                job.identity().report().adminGroupAttendance(),
                                                AdminAttendanceReportModels.Export.class),
                                        job.id(),
                                        job.leaseId());
                        yield new ExportJobService.GeneratedFile(
                                result.content(),
                                result.contentType(),
                                result.rowCount(),
                                false,
                                null);
                    }
                    default -> throw ExportJobRepository.error(HttpStatus.BAD_REQUEST);
                };
        // Recheck live actor/domain scope and the exact lease after potentially slow rendering.
        jobs.renderPlan(id, request.leaseId(), context.getClaims());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header("X-Export-Row-Count", Integer.toString(file.rowCount()))
                .header("X-Export-Requires-Full", Boolean.toString(file.requiresFull()))
                .body(file.content());
    }
}
