import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.security.auth.module.UnixSystem;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.*;
import javax.management.*;
import javax.management.openmbean.CompositeData;

/** Opt-in numeric telemetry only. No application classes, collection requests or heap dumps. */
public final class MemoryProbeAgent {
    private static final long PID = ProcessHandle.current().pid();
    private static final long MAX_BYTES = 16_777_216;
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final Map<String, String> heapPools = new TreeMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "attendance-memory-probe");
        thread.setDaemon(true);
        return thread;
    });
    private final NotificationListener listener = this::onGc;
    private final SeekableByteChannel output;
    private final long maxBytes;
    private long written;
    private volatile boolean active = true;

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (arguments == null || arguments.isBlank()) return;
        MemoryProbeAgent probe = null;
        try {
            Map<String, String> args = parse(arguments);
            Path path = Path.of(args.get("path"));
            long limit = Long.parseLong(args.get("maxBytes"));
            SeekableByteChannel channel = openPrivate(path, limit);
            try {
                probe = new MemoryProbeAgent(channel, limit);
            } catch (Throwable failure) {
                channel.close();
                throw failure;
            }
            probe.start();
        } catch (IllegalArgumentException failure) {
            marker("ARGUMENTS");
            if (probe != null) probe.fail("MONITOR");
        } catch (SecurityException failure) {
            marker("PATH");
            if (probe != null) probe.fail("MONITOR");
        } catch (IOException failure) {
            marker("IO");
            if (probe != null) probe.fail("MONITOR");
        } catch (Throwable failure) {
            marker("MONITOR");
            if (probe != null) probe.fail("MONITOR");
        }
    }

    private static Map<String, String> parse(String arguments) {
        if (arguments.length() > 8192) throw new IllegalArgumentException();
        Map<String, String> result = new HashMap<>();
        for (String argument : arguments.split(",", -1)) {
            int separator = argument.indexOf('=');
            if (separator < 1 || result.put(argument.substring(0, separator), argument.substring(separator + 1)) != null)
                throw new IllegalArgumentException();
        }
        if (!result.keySet().equals(Set.of("path", "intervalMs", "maxBytes"))
                || !result.get("intervalMs").equals("1000")
                || !result.get("maxBytes").matches("[0-9]{1,8}")) throw new IllegalArgumentException();
        long limit = Long.parseLong(result.get("maxBytes"));
        if (limit < 4096 || limit > MAX_BYTES) throw new IllegalArgumentException();
        return result;
    }

    private static SeekableByteChannel openPrivate(Path path, long limit) throws IOException {
        if (!path.isAbsolute() || !path.equals(path.normalize()) || path.getFileName() == null)
            throw new SecurityException();
        long uid = new UnixSystem().getUid();
        Path parent = path.getParent();
        Path current = path.getRoot();
        for (Path part : parent) {
            current = current.resolve(part);
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current))
                throw new SecurityException();
        }
        checkPrivate(parent, uid, true);
        UserPrincipal parentOwner = Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS);
        // SecureDirectoryStream anchors final operations to the verified directory, avoiding link following.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            if (!(stream instanceof SecureDirectoryStream<Path> directory)) throw new SecurityException();
            PosixFileAttributes anchored = directory.getFileAttributeView(PosixFileAttributeView.class).readAttributes();
            if (!anchored.owner().equals(parentOwner) || !anchored.permissions().equals(PRIVATE_DIRECTORY))
                throw new SecurityException();
            Path name = path.getFileName();
            Set<OpenOption> options = new HashSet<>(Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
            SeekableByteChannel channel;
            try {
                options.add(StandardOpenOption.CREATE_NEW);
                channel = directory.newByteChannel(name, options, PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
            } catch (FileAlreadyExistsException exists) {
                checkPrivate(path, uid, false);
                options.remove(StandardOpenOption.CREATE_NEW);
                channel = directory.newByteChannel(name, options);
            }
            try {
                checkPrivate(path, uid, false);
                if (!(channel instanceof FileChannel file) || file.tryLock() == null) throw new SecurityException();
                if (channel.size() >= limit) throw new SecurityException();
                channel.position(channel.size());
                return channel;
            } catch (Throwable failure) {
                channel.close();
                throw failure;
            }
        }
    }

    private static void checkPrivate(Path path, long uid, boolean directory) throws IOException {
        PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attrs.isSymbolicLink() || (directory ? !attrs.isDirectory() : !attrs.isRegularFile())
                || !attrs.permissions().equals(directory ? PRIVATE_DIRECTORY : PRIVATE_FILE)
                || ((Number) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue() != uid
                || (!directory && ((Number) Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() != 1))
            throw new SecurityException();
    }

    private MemoryProbeAgent(SeekableByteChannel output, long maxBytes) throws IOException {
        this.output = output;
        this.maxBytes = maxBytes;
        written = output.size();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                String name = pool.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                heapPools.put(name, lower.contains("eden") ? "EDEN" : lower.contains("survivor") ? "SURVIVOR"
                        : lower.contains("old") || lower.contains("tenured") ? "OLD" : "OTHER");
            }
        }
        if (heapPools.size() > 32) throw new IllegalArgumentException();
    }

    private void start() {
        Runtime.Version runtime = Runtime.version();
        String version = String.join(".", runtime.version().stream().map(String::valueOf).toList())
                + runtime.build().map(build -> "+" + build).orElse("");
        if (runtime.pre().isPresent()) version = "UNKNOWN";
        if (!version.matches("[0-9][0-9.+_-]{0,63}")) version = "UNKNOWN";
        String vendor = "Eclipse Adoptium".equals(System.getProperty("java.vendor")) ? "TEMURIN" : "OTHER";
        Long xmx = null;
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.matches("-Xmx[0-9]{1,12}[kKmMgG]?")) {
                String size = argument.substring(4);
                char suffix = Character.toLowerCase(size.charAt(size.length() - 1));
                long multiplier = suffix == 'k' ? 1024L : suffix == 'm' ? 1_048_576L : suffix == 'g' ? 1_073_741_824L : 1;
                xmx = Math.multiplyExact(Long.parseLong(multiplier == 1 ? size : size.substring(0, size.length() - 1)), multiplier);
            }
        }
        emit(base("START").append(",\"javaVersion\":\"").append(version).append("\",\"javaVendor\":\"")
                .append(vendor).append("\",\"heapMaxBytes\":").append(memory.getHeapMemoryUsage().getMax())
                .append(",\"heapInitBytes\":").append(memory.getHeapMemoryUsage().getInit())
                .append(",\"xmxBytes\":").append(xmx).append(",\"intervalMs\":1000,\"maxBytes\":")
                .append(maxBytes).append(",\"tcSessionId\":null}"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop("SHUTDOWN"), "attendance-memory-probe-stop"));
        for (GarbageCollectorMXBean collector : collectors) {
            if (collector instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener(listener, null, null);
            }
        }
        timer.scheduleAtFixedRate(this::sample, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void sample() {
        if (!active) return;
        try {
            MemoryUsage heap = memory.getHeapMemoryUsage();
            MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
            long count = 0, time = 0;
            for (GarbageCollectorMXBean collector : collectors) {
                count += Math.max(0, collector.getCollectionCount());
                time += Math.max(0, collector.getCollectionTime());
            }
            long cpu = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                    ? os.getProcessCpuTime() : -1;
            emit(base("SAMPLE").append(",\"heapUsedBytes\":").append(heap.getUsed())
                    .append(",\"heapCommittedBytes\":").append(heap.getCommitted()).append(",\"heapMaxBytes\":").append(heap.getMax())
                    .append(",\"nonHeapUsedBytes\":").append(nonHeap.getUsed()).append(",\"nonHeapCommittedBytes\":").append(nonHeap.getCommitted())
                    .append(",\"gcCount\":").append(count).append(",\"gcTimeMs\":").append(time)
                    .append(",\"processCpuTimeNs\":").append(cpu).append('}'));
        } catch (Throwable failure) { fail("MONITOR"); }
    }

    private void onGc(Notification notification, Object handback) {
        if (!active || !GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
        try {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
            var gc = info.getGcInfo();
            long before = 0, after = 0;
            StringBuilder pools = new StringBuilder("[");
            for (var pool : heapPools.entrySet()) {
                MemoryUsage pre = gc.getMemoryUsageBeforeGc().get(pool.getKey());
                MemoryUsage post = gc.getMemoryUsageAfterGc().get(pool.getKey());
                if (pre == null || post == null) continue;
                before += pre.getUsed(); after += post.getUsed();
                if (pools.length() > 1) pools.append(',');
                pools.append("{\"category\":\"").append(pool.getValue()).append("\",\"usedBeforeBytes\":")
                        .append(pre.getUsed()).append(",\"usedAfterBytes\":").append(post.getUsed()).append('}');
            }
            pools.append(']');
            String action = "end of minor GC".equals(info.getGcAction()) ? "MINOR"
                    : "end of major GC".equals(info.getGcAction()) ? "MAJOR" : "OTHER";
            emit(base("GC").append(",\"gcId\":").append(gc.getId()).append(",\"gcStartMs\":").append(gc.getStartTime())
                    .append(",\"gcEndMs\":").append(gc.getEndTime()).append(",\"gcDurationMs\":").append(gc.getDuration())
                    .append(",\"gcAction\":\"").append(action).append("\",\"heapUsedBeforeBytes\":").append(before)
                    .append(",\"heapUsedAfterBytes\":").append(after).append(",\"heapPools\":").append(pools).append('}'));
        } catch (Throwable failure) { fail("MONITOR"); }
    }

    private static StringBuilder base(String event) {
        return new StringBuilder(512).append("{\"schema\":1,\"event\":\"").append(event)
                .append("\",\"timestampMs\":").append(System.currentTimeMillis()).append(",\"pid\":").append(PID);
    }

    private synchronized void emit(StringBuilder record) {
        if (!active) return;
        try {
            byte[] bytes = record.append('\n').toString().getBytes(StandardCharsets.UTF_8);
            // Reserve bounded room for a final STOP record.
            if (written + bytes.length > maxBytes - 256) { stop("SIZE_LIMIT"); return; }
            write(bytes);
        } catch (Throwable failure) { fail("IO"); }
    }

    private void write(byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) output.write(buffer);
        written += bytes.length;
    }

    private synchronized void stop(String reason) {
        if (!active) return;
        active = false;
        timer.shutdown();
        try {
            byte[] bytes = base("STOP").append(",\"reason\":\"").append(reason).append("\"}\n").toString().getBytes(StandardCharsets.UTF_8);
            if (written + bytes.length <= maxBytes) write(bytes);
            else marker("SIZE_LIMIT");
        } catch (Throwable failure) { marker("IO"); }
        finally { closeOutput(); }
        if (reason.equals("SIZE_LIMIT")) marker("SIZE_LIMIT");
    }

    private synchronized void fail(String reason) {
        if (!active) return;
        active = false;
        marker(reason);
        timer.shutdown();
        closeOutput();
    }

    private void closeOutput() {
        try { output.close(); } catch (Throwable failure) { marker("IO"); }
    }

    private static void marker(String reason) {
        try { System.err.println("RCT_MEMORY_PROBE_" + reason); } catch (Throwable ignored) { }
    }
}
