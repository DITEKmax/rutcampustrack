package ru.rutcampustrack.documentrenderer.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.rutcampustrack.documentrenderer.render.InvalidDocumentException;
import ru.rutcampustrack.documentrenderer.render.OfficeDocumentConverter;

class DocumentRendererGrpcServiceImplTest {

    @Test
    void invalidDocxReturnsInvalidArgumentWithoutPrivateParserDetails() {
        OfficeDocumentConverter converter = mock(OfficeDocumentConverter.class);
        when(converter.convertToPdf(new byte[] {1, 2, 3}))
                .thenThrow(
                        new InvalidDocumentException(
                                new IllegalArgumentException("private parser detail")));
        RecordingObserver observer = new RecordingObserver();

        new DocumentRendererGrpcServiceImpl(converter)
                .convert(
                        ConvertDocumentRequest.newBuilder()
                                .setDocx(
                                        com.google.protobuf.ByteString.copyFrom(
                                                new byte[] {1, 2, 3}))
                                .setTargetFormat(TargetFormat.PDF)
                                .build(),
                        observer);
        observer.awaitTerminal();

        assertThat(Status.fromThrowable(observer.error).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(Status.fromThrowable(observer.error).getDescription())
                .doesNotContain("private parser detail");
        assertThat(observer.values).isEmpty();
        assertThat(observer.completed).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 999})
    void unsupportedFormatReturnsInvalidArgumentBeforeConversion(int targetFormat) {
        OfficeDocumentConverter converter = mock(OfficeDocumentConverter.class);
        DocumentRendererGrpcServiceImpl service = new DocumentRendererGrpcServiceImpl(converter);
        RecordingObserver observer = new RecordingObserver();

        service.convert(
                ConvertDocumentRequest.newBuilder()
                        .setDocx(com.google.protobuf.ByteString.copyFrom(new byte[] {1, 2, 3}))
                        .setTargetFormatValue(targetFormat)
                        .build(),
                observer);
        observer.awaitTerminal();

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(observer.values).isEmpty();
        assertThat(observer.completed).isFalse();
        verifyNoInteractions(converter);
    }

    @Test
    void convertPdfReturnsPdfPayload() {
        OfficeDocumentConverter converter = mock(OfficeDocumentConverter.class);
        when(converter.convertToPdf(new byte[] {1, 2, 3})).thenReturn(new byte[] {4, 5});
        DocumentRendererGrpcServiceImpl service = new DocumentRendererGrpcServiceImpl(converter);
        RecordingObserver observer = new RecordingObserver();

        service.convert(
                ConvertDocumentRequest.newBuilder()
                        .setDocx(com.google.protobuf.ByteString.copyFrom(new byte[] {1, 2, 3}))
                        .setTargetFormat(TargetFormat.PDF)
                        .build(),
                observer);
        observer.awaitTerminal();

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getContent().toByteArray()).containsExactly(4, 5);
        assertThat(observer.values.get(0).getContentType()).isEqualTo("application/pdf");
        assertThat(observer.values.get(0).getExtension()).isEqualTo("pdf");
    }

    @Test
    void emptyDocxReturnsInvalidArgument() {
        DocumentRendererGrpcServiceImpl service =
                new DocumentRendererGrpcServiceImpl(mock(OfficeDocumentConverter.class));
        RecordingObserver observer = new RecordingObserver();

        service.convert(
                ConvertDocumentRequest.newBuilder().setTargetFormat(TargetFormat.PNG).build(),
                observer);
        observer.awaitTerminal();

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException ex = (StatusRuntimeException) observer.error;
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(observer.completed).isFalse();
        assertThat(observer.values).isEmpty();
    }

    @Test
    void convertAllPngPagesReturnsZipPayload() {
        OfficeDocumentConverter converter = mock(OfficeDocumentConverter.class);
        when(converter.convertToPngPagesZip(new byte[] {1, 2, 3}, 180))
                .thenReturn(new byte[] {8, 9});
        DocumentRendererGrpcServiceImpl service = new DocumentRendererGrpcServiceImpl(converter);
        RecordingObserver observer = new RecordingObserver();

        service.convert(
                ConvertDocumentRequest.newBuilder()
                        .setDocx(com.google.protobuf.ByteString.copyFrom(new byte[] {1, 2, 3}))
                        .setPngDpi(180)
                        .setTargetFormat(TargetFormat.PNG_PAGES_ZIP)
                        .build(),
                observer);
        observer.awaitTerminal();

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getContent().toByteArray()).containsExactly(8, 9);
        assertThat(observer.values.get(0).getContentType()).isEqualTo("application/zip");
        assertThat(observer.values.get(0).getExtension()).isEqualTo("zip");
    }

    private static class RecordingObserver implements StreamObserver<ConvertDocumentResponse> {
        private final List<ConvertDocumentResponse> values = new ArrayList<>();
        private final CountDownLatch terminal = new CountDownLatch(1);
        private Throwable error;
        private boolean completed;

        private void awaitTerminal() {
            try {
                assertThat(terminal.await(5, TimeUnit.SECONDS))
                        .as("RPC reached a terminal response")
                        .isTrue();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Test observer interrupted", error);
            }
        }

        @Override
        public void onNext(ConvertDocumentResponse value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            terminal.countDown();
        }

        @Override
        public void onCompleted() {
            completed = true;
            terminal.countDown();
        }
    }
}
