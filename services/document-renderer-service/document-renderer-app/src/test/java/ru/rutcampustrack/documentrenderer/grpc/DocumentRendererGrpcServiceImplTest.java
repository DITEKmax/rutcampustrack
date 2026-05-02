package ru.rutcampustrack.documentrenderer.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.documentrenderer.render.OfficeDocumentConverter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentRendererGrpcServiceImplTest {

    @Test
    void convertPdfReturnsPdfPayload() {
        OfficeDocumentConverter converter = mock(OfficeDocumentConverter.class);
        when(converter.convertToPdf(new byte[]{1, 2, 3})).thenReturn(new byte[]{4, 5});
        DocumentRendererGrpcServiceImpl service = new DocumentRendererGrpcServiceImpl(converter);
        RecordingObserver observer = new RecordingObserver();

        service.convert(ConvertDocumentRequest.newBuilder()
                .setDocx(com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3}))
                .setTargetFormat(TargetFormat.PDF)
                .build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getContent().toByteArray()).containsExactly(4, 5);
        assertThat(observer.values.get(0).getContentType()).isEqualTo("application/pdf");
        assertThat(observer.values.get(0).getExtension()).isEqualTo("pdf");
    }

    @Test
    void emptyDocxReturnsInvalidArgument() {
        DocumentRendererGrpcServiceImpl service = new DocumentRendererGrpcServiceImpl(mock(OfficeDocumentConverter.class));
        RecordingObserver observer = new RecordingObserver();

        service.convert(ConvertDocumentRequest.newBuilder()
                .setTargetFormat(TargetFormat.PNG)
                .build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException ex = (StatusRuntimeException) observer.error;
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(observer.completed).isFalse();
        assertThat(observer.values).isEmpty();
    }

    private static class RecordingObserver implements StreamObserver<ConvertDocumentResponse> {
        private final List<ConvertDocumentResponse> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(ConvertDocumentResponse value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
