package ru.rutcampustrack.academic.integration;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ru.rutcampustrack.auth.session.model.AuthRole;

/** Selects the explicitly requested existing administrator through actual Auth authority. */
final class AcademicTestAdminRequest {
    private AcademicTestAdminRequest() {}

    static RequestPostProcessor signed(AcademicIdentityIntegrationFixture fixture) {
        return request -> {
            if (request.getHeader("X-Internal-Token") != null) return request;
            String supplied = request.getHeader("X-User-Id");
            if (supplied == null)
                throw new IllegalArgumentException("Explicit existing actor required");
            if (!"ADMIN".equalsIgnoreCase(request.getHeader("X-User-Role")))
                throw new IllegalArgumentException("Explicit selected ADMIN intent required");
            var selected = fixture.select(Long.parseLong(supplied), AuthRole.ADMIN);
            return fixture.signed(selected).postProcessRequest(request);
        };
    }
}
