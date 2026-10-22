package ru.rutcampustrack.shared.observability.errors;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Attachment;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.JsonSerializer;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.User;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

class SafeErrorEventTest {
    @Test
    void exportsOnlyStructuralStackAndReleaseWithoutSecretOrPii() throws Exception {
        String marker = "synthetic-private@example.invalid";
        SentryEvent event = new SentryEvent();
        event.setRelease("26cbe5912f6683a81780a831609dbddbc26feba4");
        event.setEnvironment("isolated-ci");
        User user = new User();
        user.setEmail(marker);
        event.setUser(user);
        Request request = new Request();
        request.setUrl(marker);
        request.setData(marker);
        request.setHeaders(Map.of("Authorization", marker));
        event.setRequest(request);
        event.setExtra("password", marker);
        event.addBreadcrumb(new Breadcrumb(marker));
        event.getContexts().put("credentials", Map.of("token", marker));
        SentryStackFrame frame = new SentryStackFrame();
        frame.setFilename("DocxRenderer.java");
        frame.setFunction("render");
        frame.setModule("ru.rutcampustrack.attendance.report.DocxRenderer");
        frame.setLineno(40);
        frame.setVars(Map.of("password", marker));
        frame.setContextLine(marker);
        frame.setAbsPath(marker);
        SentryException exception = new SentryException();
        exception.setType("IllegalStateException");
        exception.setValue(marker);
        exception.setStacktrace(new SentryStackTrace(List.of(frame)));
        event.setExceptions(List.of(exception));
        Hint hint = Hint.withAttachment(new Attachment(marker.getBytes(), "private.txt"));
        SentryEvent safe = new SafeErrorEvent().execute(event, hint);
        assertThat(hint.getAttachments()).isEmpty();
        StringWriter output = new StringWriter();
        new JsonSerializer(new SentryOptions()).serialize(safe, output);
        assertThat(output.toString()).doesNotContain(marker, "Authorization", "credentials");
        assertThat(output.toString())
                .contains("DocxRenderer.java", "IllegalStateException", event.getRelease());
        assertThat(safe.getEventId()).isEqualTo(event.getEventId());
        assertThat(safe.getUser()).isNull();
        assertThat(safe.getRequest()).isNull();
    }

    @Test
    void dropsUnstructuredEvents() {
        assertThat(new SafeErrorEvent().execute(new SentryEvent(), new Hint())).isNull();
    }
}
