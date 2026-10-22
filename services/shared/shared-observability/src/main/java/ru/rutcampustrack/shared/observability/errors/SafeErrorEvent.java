package ru.rutcampustrack.shared.observability.errors;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reconstructs error exports: request/user/context/message/locals never leave the process. */
public final class SafeErrorEvent implements SentryOptions.BeforeSendCallback {
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z_$][A-Za-z0-9_.$<>-]{0,159}");
    private static final Pattern RELEASE =
            Pattern.compile(
                    "(?:[0-9a-f]{7,40}|[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]{1,40})?)");
    private static final Set<String> ENVIRONMENTS =
            Set.of("production", "development", "isolated-ci");

    @Override
    public SentryEvent execute(SentryEvent original, Hint hint) {
        hint.clearAttachments();
        hint.setScreenshot(null);
        hint.setViewHierarchy(null);
        hint.setThreadDump(null);
        hint.setTombstone(null);
        if (original.getExceptions() == null || original.getExceptions().isEmpty()) return null;
        SentryEvent safe = new SentryEvent(original.getTimestamp());
        safe.setEventId(original.getEventId());
        safe.setPlatform("java");
        safe.setLevel(SentryLevel.ERROR);
        if (original.getRelease() != null && RELEASE.matcher(original.getRelease()).matches()) {
            safe.setRelease(original.getRelease());
        }
        if (ENVIRONMENTS.contains(
                original.getEnvironment() == null ? "" : original.getEnvironment())) {
            safe.setEnvironment(original.getEnvironment());
        }
        List<SentryException> exceptions = new ArrayList<>();
        for (SentryException exception : original.getExceptions().stream().limit(4).toList()) {
            SentryException copy = new SentryException();
            copy.setType(symbol(exception.getType(), "Exception"));
            copy.setModule(symbol(exception.getModule(), null));
            copy.setValue("[redacted]");
            if (exception.getStacktrace() != null
                    && exception.getStacktrace().getFrames() != null) {
                List<SentryStackFrame> source = exception.getStacktrace().getFrames();
                List<SentryStackFrame> frames = new ArrayList<>();
                for (SentryStackFrame frame :
                        source.subList(Math.max(0, source.size() - 64), source.size())) {
                    SentryStackFrame item = new SentryStackFrame();
                    item.setModule(symbol(frame.getModule(), null));
                    item.setFunction(symbol(frame.getFunction(), null));
                    String filename = frame.getFilename();
                    if (filename != null && filename.matches("[A-Za-z0-9_$.-]{1,120}\\.java"))
                        item.setFilename(filename);
                    if (frame.getLineno() != null
                            && frame.getLineno() > 0
                            && frame.getLineno() <= 10000000) item.setLineno(frame.getLineno());
                    item.setInApp(frame.isInApp());
                    frames.add(item);
                }
                copy.setStacktrace(new SentryStackTrace(frames));
            }
            exceptions.add(copy);
        }
        safe.setExceptions(exceptions);
        return safe;
    }

    private static String symbol(String value, String fallback) {
        return value != null && SYMBOL.matcher(value).matches() ? value : fallback;
    }
}
