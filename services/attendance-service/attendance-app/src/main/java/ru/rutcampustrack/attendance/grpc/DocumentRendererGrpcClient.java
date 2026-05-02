package ru.rutcampustrack.attendance.grpc;

import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.documentrenderer.grpc.ConvertDocumentRequest;
import ru.rutcampustrack.documentrenderer.grpc.DocumentRendererGrpcServiceGrpc;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;

import java.util.concurrent.TimeUnit;

@Component
public class DocumentRendererGrpcClient {

    private static final int PNG_DPI = 200;

    @GrpcClient("document-renderer-service")
    private DocumentRendererGrpcServiceGrpc.DocumentRendererGrpcServiceBlockingStub stub;

    public byte[] convertDocx(byte[] docx, TargetFormat targetFormat) {
        if (docx == null || docx.length == 0) {
            throw new ReportExportUnavailableException("DOCX content is empty");
        }
        if (targetFormat == TargetFormat.TARGET_FORMAT_UNSPECIFIED) {
            throw new ReportExportUnavailableException("Renderer target format is required");
        }
        try {
            return stub.withDeadlineAfter(30, TimeUnit.SECONDS)
                    .convert(ConvertDocumentRequest.newBuilder()
                            .setDocx(com.google.protobuf.ByteString.copyFrom(docx))
                            .setTargetFormat(targetFormat)
                            .setPngDpi(PNG_DPI)
                            .build())
                    .getContent()
                    .toByteArray();
        } catch (StatusRuntimeException e) {
            throw new ReportExportUnavailableException("Document renderer unavailable: " + e.getStatus());
        }
    }
}
