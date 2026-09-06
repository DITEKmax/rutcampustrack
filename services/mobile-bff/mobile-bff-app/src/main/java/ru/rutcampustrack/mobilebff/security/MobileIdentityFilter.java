package ru.rutcampustrack.mobilebff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.MobileProblemDetails;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@Component
public class MobileIdentityFilter extends OncePerRequestFilter {
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
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/student/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = request.getHeader(properties.headerName());
            context.authenticate(validator.validate(token), token);
            chain.doFilter(request, response);
        } catch (InternalJwtException error) {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            MobileProblemDetails problem = new MobileProblemDetails(
                    401, URI.create("urn:rct:problem:invalid-session"), "Сессия недействительна",
                    "Требуется повторная аутентификация", URI.create(request.getRequestURI()),
                    Instant.now(clock), null, ProblemCode.INVALID_SESSION, null, null);
            objectMapper.writeValue(response.getOutputStream(), problem);
        }
    }
}
