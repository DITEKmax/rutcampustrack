package ru.rutcampustrack.documentrenderer.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.documentrenderer.render.DocumentConversionException;
import ru.rutcampustrack.documentrenderer.render.InvalidDocumentException;
import ru.rutcampustrack.documentrenderer.render.OfficeDocumentConverter;

@GrpcService
public class DocumentRendererGrpcServiceImpl
        extends DocumentRendererGrpcServiceGrpc.DocumentRendererGrpcServiceImplBase {

    private final OfficeDocumentConverter converter;

    public DocumentRendererGrpcServiceImpl(OfficeDocumentConverter converter) {
        this.converter = converter;
    }

    @Override
    public void convert(
            ConvertDocumentRequest request,
            StreamObserver<ConvertDocumentResponse> responseObserver) {
        if (request.getDocx().isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("DOCX content is empty")
                            .asRuntimeException());
            return;
        }
        if (request.getTargetFormat() == TargetFormat.TARGET_FORMAT_UNSPECIFIED
                || request.getTargetFormat() == TargetFormat.UNRECOGNIZED) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Target format must be PDF, PNG, or PNG_PAGES_ZIP")
                            .asRuntimeException());
            return;
        }
        Context context = Context.current();
        // gRPC serializes cancellation callbacks with this handler. Return before blocking
        // so cancellation can reach the conversion rather than waiting for it to finish.
        Thread.ofVirtual()
                .name("rct-renderer-conversion")
                .start(context.wrap(() -> convertDocument(request, responseObserver, context)));
    }

    private void convertDocument(
            ConvertDocumentRequest request,
            StreamObserver<ConvertDocumentResponse> responseObserver,
            Context context) {
        Thread conversionThread = Thread.currentThread();
        boolean alreadyInterrupted = conversionThread.isInterrupted();
        Object cancellationLock = new Object();
        boolean[] active = {true};
        boolean[] interruptedByCancellation = {false};
        Context.CancellationListener cancellation =
                ignored -> {
                    synchronized (cancellationLock) {
                        if (active[0] && !conversionThread.isInterrupted()) {
                            interruptedByCancellation[0] = true;
                            conversionThread.interrupt();
                        }
                    }
                };
        context.addListener(cancellation, Runnable::run);
        try {
            if (context.isCancelled()) {
                return;
            }
            ConvertDocumentResponse response =
                    switch (request.getTargetFormat()) {
                        case PDF ->
                                response(
                                        converter.convertToPdf(request.getDocx().toByteArray()),
                                        "application/pdf",
                                        "pdf");
                        case PNG ->
                                response(
                                        converter.convertToPng(
                                                request.getDocx().toByteArray(),
                                                request.getPngDpi()),
                                        "image/png",
                                        "png");
                        case PNG_PAGES_ZIP ->
                                response(
                                        converter.convertToPngPagesZip(
                                                request.getDocx().toByteArray(),
                                                request.getPngDpi()),
                                        "application/zip",
                                        "zip");
                        case TARGET_FORMAT_UNSPECIFIED, UNRECOGNIZED ->
                                throw new DocumentConversionException(
                                        "Target format must be PDF, PNG, or PNG_PAGES_ZIP");
                    };
            if (!context.isCancelled()) {
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        } catch (InvalidDocumentException ex) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (DocumentConversionException ex) {
            responseObserver.onError(
                    (context.isCancelled() ? Status.CANCELLED : Status.INTERNAL)
                            .withDescription(ex.getMessage())
                            .asRuntimeException());
        } finally {
            context.removeListener(cancellation);
            synchronized (cancellationLock) {
                active[0] = false;
                // Cancellation owns this interrupt; do not contaminate the next pooled-thread RPC.
                if (interruptedByCancellation[0] && !alreadyInterrupted) {
                    Thread.interrupted();
                }
            }
        }
    }

    private static ConvertDocumentResponse response(
            byte[] content, String contentType, String extension) {
        return ConvertDocumentResponse.newBuilder()
                .setContent(ByteString.copyFrom(content))
                .setContentType(contentType)
                .setExtension(extension)
                .build();
    }
}
