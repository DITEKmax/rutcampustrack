package ru.rutcampustrack.documentrenderer.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.documentrenderer.render.DocumentConversionException;
import ru.rutcampustrack.documentrenderer.render.OfficeDocumentConverter;

@GrpcService
public class DocumentRendererGrpcServiceImpl extends DocumentRendererGrpcServiceGrpc.DocumentRendererGrpcServiceImplBase {

    private final OfficeDocumentConverter converter;

    public DocumentRendererGrpcServiceImpl(OfficeDocumentConverter converter) {
        this.converter = converter;
    }

    @Override
    public void convert(ConvertDocumentRequest request, StreamObserver<ConvertDocumentResponse> responseObserver) {
        if (request.getDocx().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("DOCX content is empty")
                    .asRuntimeException());
            return;
        }
        try {
            ConvertDocumentResponse response = switch (request.getTargetFormat()) {
                case PDF -> response(converter.convertToPdf(request.getDocx().toByteArray()), "application/pdf", "pdf");
                case PNG -> response(converter.convertToPng(request.getDocx().toByteArray(), request.getPngDpi()),
                        "image/png", "png");
                case TARGET_FORMAT_UNSPECIFIED, UNRECOGNIZED -> throw new DocumentConversionException(
                        "Target format must be PDF or PNG");
            };
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (DocumentConversionException ex) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(ex.getMessage())
                    .asRuntimeException());
        }
    }

    private static ConvertDocumentResponse response(byte[] content, String contentType, String extension) {
        return ConvertDocumentResponse.newBuilder()
                .setContent(ByteString.copyFrom(content))
                .setContentType(contentType)
                .setExtension(extension)
                .build();
    }
}
