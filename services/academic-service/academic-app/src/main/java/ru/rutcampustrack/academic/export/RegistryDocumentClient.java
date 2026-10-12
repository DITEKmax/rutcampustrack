package ru.rutcampustrack.academic.export;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.documentrenderer.grpc.*;

@Component
public class RegistryDocumentClient {
    @GrpcClient("document-renderer-service")
    private DocumentRendererGrpcServiceGrpc.DocumentRendererGrpcServiceBlockingStub stub;

    public byte[] convert(byte[] docx, boolean png) {
        if (docx.length > 4 * 1024 * 1024 - 1024)
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Реестр превышает лимит преобразования");
        try {
            return stub.withDeadlineAfter(30, TimeUnit.SECONDS)
                    .convert(
                            ConvertDocumentRequest.newBuilder()
                                    .setDocx(ByteString.copyFrom(docx))
                                    .setTargetFormat(
                                            png ? TargetFormat.PNG_PAGES_ZIP : TargetFormat.PDF)
                                    .setPngDpi(200)
                                    .build())
                    .getContent()
                    .toByteArray();
        } catch (StatusRuntimeException e) {
            throw new ResponseStatusException(
                    e.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED
                            ? HttpStatus.PAYLOAD_TOO_LARGE
                            : HttpStatus.SERVICE_UNAVAILABLE,
                    "Формирование файла недоступно");
        }
    }
}
