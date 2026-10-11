package ru.rutcampustrack.documentrenderer.render;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.rutcampustrack.documentrenderer.grpc.ConvertDocumentRequest;
import ru.rutcampustrack.documentrenderer.grpc.DocumentRendererGrpcServiceGrpc;
import ru.rutcampustrack.documentrenderer.grpc.DocumentRendererGrpcServiceImpl;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real transport cancellation with an intentionally slow, real child-process dependency. */
class DocumentRendererCancellationIT {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelledRpcStopsItsProcessTree(boolean deadline, @TempDir Path directory) throws Exception {
        CountDownLatch conversionFinished = new CountDownLatch(1);
        AtomicInteger conversions = new AtomicInteger();
        OfficeDocumentConverter slowDependency = new OfficeDocumentConverter(
                new DocumentRendererProperties(), new ProcessRunner()) {
            @Override
            public byte[] convertToPdf(byte[] docx) {
                if (conversions.incrementAndGet() > 1) return new byte[]{'o', 'k'};
                try {
                    new ProcessRunner().run(ProcessRunnerLifecycleTest.command(directory),
                            directory, Duration.ofSeconds(30));
                    return new byte[]{1};
                } finally {
                    conversionFinished.countDown();
                }
            }
        };
        ExecutorService rpcExecutor = Executors.newSingleThreadExecutor();
        Server server = NettyServerBuilder.forPort(0).executor(rpcExecutor)
                .addService(new DocumentRendererGrpcServiceImpl(slowDependency)).build().start();
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext().build();
        List<Long> pids = List.of();
        try {
            var stub = DocumentRendererGrpcServiceGrpc.newFutureStub(channel)
                    .withDeadlineAfter(deadline ? 5 : 30, TimeUnit.SECONDS);
            var call = stub.convert(ConvertDocumentRequest.newBuilder()
                    .setDocx(ByteString.copyFromUtf8("synthetic slow dependency input"))
                    .setTargetFormat(TargetFormat.PDF).build());
            pids = ProcessRunnerLifecycleTest.waitForProcesses(directory);
            if (!deadline) assertThat(call.cancel(true)).isTrue();

            assertThat(conversionFinished.await(10, TimeUnit.SECONDS))
                    .as("client cancellation/deadline must stop server-side work").isTrue();
            assertThat(call.isDone()).isTrue();
            ProcessRunnerLifecycleTest.assertStopped(pids);
            var next = DocumentRendererGrpcServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS).convert(ConvertDocumentRequest.newBuilder()
                            .setDocx(ByteString.copyFromUtf8("next synthetic request"))
                            .setTargetFormat(TargetFormat.PDF).build());
            assertThat(next.getContent().toByteArray()).as("subsequent RPC remains usable")
                    .containsExactly((byte) 'o', (byte) 'k');
        } finally {
            ProcessRunnerLifecycleTest.stopOwned(pids);
            ProcessRunnerLifecycleTest.stopOwned(ProcessRunnerLifecycleTest.readProcesses(directory));
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            rpcExecutor.shutdownNow();
            assertThat(rpcExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
