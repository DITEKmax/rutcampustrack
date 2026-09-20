package ru.rutcampustrack.mobilebff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.MobileProblemDetails;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@Component
public class MobileIdentityFilter extends OncePerRequestFilter {
    private static final PathPattern STUDENT_API_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/**");
    private static final PathPattern HOMEWORK_COMPLETION_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/homework/{homeworkId}/completion");
    private static final PathPattern CHECKIN_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/lessons/{lessonId}/checkin");
    private static final PathPattern EXCUSE_SUBMISSION_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/requests/excuse");
    private static final PathPattern LATE_CHECKIN_SUBMISSION_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/requests/late-checkin");
    private static final PathPattern REQUEST_CANCEL_PATTERN =
            PathPatternParser.defaultInstance.parse("/api/v1/student/requests/{requestId}/cancel");

    private final InternalJwtValidator validator;
    private final InternalJwtProperties properties;
    private final MobileRequestContext context;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MobileIdentityFilter(InternalJwtValidator validator, InternalJwtProperties properties,
                                MobileRequestContext context, ObjectMapper objectMapper, Clock clock) {
        this.validator = validator;
        this.properties = properties;
        this.context = context;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !STUDENT_API_PATTERN.matches(pathContainer(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        try {
            String token = request.getHeader(properties.headerName());
            InternalJwtClaims claims = validator.validate(token);
            context.authenticate(claims, token);
            if ("STUDENT".equals(claims.domainRole()) && claims.readOnly() && isMutationRoute(request)) {
                writeReadOnlyProblem(request, response);
                return;
            }
            chain.doFilter(request, response);
        } catch (InternalJwtException error) {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
            MobileProblemDetails problem = new MobileProblemDetails(
                    401, URI.create("urn:rct:problem:invalid-session"), "Сессия недействительна",
                    "Требуется повторная аутентификация", URI.create(request.getRequestURI()),
                    Instant.now(clock), null, ProblemCode.INVALID_SESSION, null, null);
            objectMapper.writeValue(response.getOutputStream(), problem);
        }
    }

    private void writeReadOnlyProblem(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MobileProblemDetails problem = new MobileProblemDetails(
                HttpStatus.FORBIDDEN.value(), URI.create("urn:rct:problem:role-read-only"),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Терминальная student-сессия доступна только для чтения",
                URI.create(request.getRequestURI()), clock.instant(), null,
                ProblemCode.ROLE_READ_ONLY, null, null);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static boolean isMutationRoute(HttpServletRequest request) {
        PathContainer path = pathContainer(request);
        if ("PUT".equalsIgnoreCase(request.getMethod())) {
            return HOMEWORK_COMPLETION_PATTERN.matches(path);
        }
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            return CHECKIN_PATTERN.matches(path)
                    || EXCUSE_SUBMISSION_PATTERN.matches(path)
                    || LATE_CHECKIN_SUBMISSION_PATTERN.matches(path)
                    || REQUEST_CANCEL_PATTERN.matches(path);
        }
        return false;
    }

    private static PathContainer pathContainer(HttpServletRequest request) {
        return PathContainer.parsePath(pathWithoutContext(request));
    }

    private static String pathWithoutContext(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
