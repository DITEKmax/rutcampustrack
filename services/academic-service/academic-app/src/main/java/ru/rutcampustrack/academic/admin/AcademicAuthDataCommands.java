package ru.rutcampustrack.academic.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.authdb.AuthDataCommands;

import java.util.List;
import java.util.Locale;

/** Request adapter for the fixed Auth commands; no remote transaction or credential cache. */
@Component
public class AcademicAuthDataCommands {
    private final AuthDataCommands commands;
    private final RequestContext context;

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient identityData;

    public AcademicAuthDataCommands(JdbcTemplate jdbc, RequestContext context) {
        this.commands = new AuthDataCommands(jdbc);
        this.context = context;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(User user) {
        commands.initialize(
                context.getClaims(),
                user.getId(),
                user.getLogin(),
                user.getPasswordHash(),
                user.getInitialPassword(),
                code(user.getRole()),
                code(user.getStatus()),
                user.getTelegramId(),
                user.getTelegramUsername());
        user.setPasswordHash(null);
        user.setInitialPassword(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(User user, boolean archive) {
        commands.synchronize(
                context.getClaims(),
                user.getId(),
                code(user.getRole()),
                code(user.getStatus()),
                user.getTelegramId(),
                user.getTelegramUsername(),
                user.isHeadman(),
                archive);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void project(User user) {
        commands.project(
                context.getClaims(),
                user.getId(),
                code(user.getRole()),
                code(user.getStatus()),
                user.getTelegramId(),
                user.getTelegramUsername());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void grant(long target, String role, String status, Long group) {
        commands.grant(context.getClaims(), target, role, status, group);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void headman(long target, boolean active) {
        commands.headman(context.getClaims(), target, active);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceHeadman(
            Long previous,
            long candidate,
            long group,
            boolean legacy,
            Long telegram,
            String status) {
        commands.replaceHeadman(
                context.getClaims(), previous, candidate, group, legacy, telegram, status);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transfer(User user, String studentStatus, long group) {
        commands.transfer(
                context.getClaims(),
                user.getId(),
                code(user.getRole()),
                code(user.getStatus()),
                user.getTelegramId(),
                user.getTelegramUsername(),
                user.isHeadman(),
                studentStatus,
                group);
    }

    public String initialPassword(long target) {
        try {
            var response =
                    identityData.initialCredentials(
                            List.of(target),
                            System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
            if (response.getCredentialsCount() != 1
                    || response.getCredentials(0).getUserId() != target)
                throw io.grpc.Status.UNAVAILABLE.asRuntimeException();
            var credential = response.getCredentials(0);
            return credential.getPasswordChanged() || credential.getInitialPassword().isBlank()
                    ? null
                    : credential.getInitialPassword();
        } catch (io.grpc.StatusRuntimeException denied) {
            if (denied.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) return null;
            throw denied;
        }
    }

    private static String code(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
