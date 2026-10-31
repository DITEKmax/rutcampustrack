package ru.rutcampustrack.attendance.marking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.attendance.exception.BadRequestException;

class RepeatableMarkingFileTest {
    @Test
    void lazySnapshotPreservesMetadataAndExactBytesAcrossAttempts() throws Exception {
        byte[] expected =
                "%PDF-1.7\nowned bytes\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MultipartFile original = mock(MultipartFile.class);
        when(original.getName()).thenReturn("file");
        when(original.getOriginalFilename()).thenReturn("medical.pdf");
        when(original.getContentType()).thenReturn("application/pdf");
        when(original.getSize()).thenReturn((long) expected.length);
        when(original.getInputStream()).thenReturn(new ByteArrayInputStream(expected));
        MultipartFile repeatable = RepeatableMarkingFile.of(original);
        verify(original, never()).getInputStream();
        assertThat(repeatable.getName()).isEqualTo("file");
        assertThat(repeatable.getOriginalFilename()).isEqualTo("medical.pdf");
        assertThat(repeatable.getContentType()).isEqualTo("application/pdf");
        assertThat(repeatable.getSize()).isEqualTo(expected.length);
        byte[] first = repeatable.getBytes();
        first[0] = 0;
        assertThat(repeatable.getBytes()).containsExactly(expected);
        assertThat(repeatable.getInputStream().readAllBytes()).containsExactly(expected);
        verify(original, times(1)).getInputStream();
        verify(original, never()).getBytes();
        assertThat(RepeatableMarkingFile.of(null)).isNull();
    }

    @Test
    void dishonestUploadSizeCannotCauseUnboundedBuffering() throws Exception {
        MultipartFile original = mock(MultipartFile.class);
        when(original.getSize()).thenReturn(1L);
        AtomicInteger read = new AtomicInteger();
        when(original.getInputStream())
                .thenReturn(
                        new InputStream() {
                            @Override
                            public int read() {
                                read.incrementAndGet();
                                return 1;
                            }
                        });
        MultipartFile repeatable = RepeatableMarkingFile.of(original);
        assertThatThrownBy(repeatable::getBytes).isInstanceOf(BadRequestException.class);
        assertThat(read).hasValue((int) AttendanceAttachmentService.MAX_BYTES + 1);
        verify(original, never()).getBytes();
    }
}
