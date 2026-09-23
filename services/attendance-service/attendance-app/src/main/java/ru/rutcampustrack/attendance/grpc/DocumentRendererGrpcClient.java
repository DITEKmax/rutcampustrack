package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
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
    private static final int DEFAULT_RENDERER_MESSAGE_LIMIT_BYTES = 4 * 1024 * 1024;

    @GrpcClient("document-renderer-service")
    private DocumentRendererGrpcServiceGrpc.DocumentRendererGrpcServiceBlockingStub stub;

    public byte[] convertDocx(byte[] docx, TargetFormat targetFormat) {
        return convertDocx(docx, targetFormat, false);
    }

    /**
     * Export-only conversion preserves RESOURCE_EXHAUSTED so the teacher export
     * boundary can return a user-visible size limit instead of a generic outage.
     */
    public byte[] convertDocxForTeacherExport(byte[] docx, TargetFormat targetFormat) {
        if (docx != null && docx.length > DEFAULT_RENDERER_MESSAGE_LIMIT_BYTES - 1024) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Teacher DOCX exceeds the renderer's 4 MiB input limit")
                    .asRuntimeException();
        }
        return convertDocx(docx, targetFormat, true);
    }

    /**
     * Bounded conversion for a headman weekly export. Keep the teacher-specific
     * boundary unchanged while preserving renderer size failures for the 413 API mapping.
     */
    public byte[] convertDocxForHeadmanWeeklyExport(byte[] docx, TargetFormat targetFormat) {
        if (docx != null && docx.length > DEFAULT_RENDERER_MESSAGE_LIMIT_BYTES - 1024) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Headman weekly DOCX exceeds the renderer's 4 MiB input limit")
                    .asRuntimeException();
        }
        return convertDocx(docx, targetFormat, true);
    }

    private byte[] convertDocx(byte[] docx, TargetFormat targetFormat, boolean preserveSizeLimit) {
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
            if (preserveSizeLimit && e.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                throw e;
            }
            throw new ReportExportUnavailableException("Document renderer unavailable: " + e.getStatus());
        }
    }

    public byte[] convertDocxToPngPagesZip(byte[] docx) {
        return convertDocx(docx, TargetFormat.PNG_PAGES_ZIP);
    }
}
