package ru.rutcampustrack.academic.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

final class CampusMapFileFixture {
    private CampusMapFileFixture() {}

    static byte[] png(int width, int height) {
        return png(width, height, BufferedImage.TYPE_INT_ARGB, false);
    }

    static byte[] png(int width, int height, int type, boolean interlaced) {
        BufferedImage image = new BufferedImage(width, height, type);
        image.setRGB(0, 0, 0xffffffff);
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var output = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(output);
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                if (interlaced) parameters.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
                writer.write(null, new IIOImage(image, null, null), parameters);
            }
            byte[] png = bytes.toByteArray();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(width);
            assertThat(decoded.getHeight()).isEqualTo(height);
            assertThat(decoded.getRGB(0, 0)).isEqualTo(image.getRGB(0, 0));
            return png;
        } catch (java.io.IOException error) {
            throw new AssertionError("Cannot encode/decode synthetic PNG fixture", error);
        } finally {
            writer.dispose();
        }
    }

    /** Encodes black grayscale scanlines with a fixed buffer, including test-only wide canvases. */
    static byte[] streamingGrayPng(int width, int height, int depth, boolean interlaced) {
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            long rawBytes = 0;
            int[][] passes =
                    interlaced
                            ? new int[][] {
                                {0, 0, 8, 8},
                                {4, 0, 8, 8},
                                {0, 4, 4, 8},
                                {2, 0, 4, 4},
                                {0, 2, 2, 4},
                                {1, 0, 2, 2},
                                {0, 1, 1, 2}
                            }
                            : new int[][] {{0, 0, 1, 1}};
            for (int[] pass : passes) {
                long columns = Math.max(0L, ((long) width - pass[0] + pass[2] - 1) / pass[2]);
                long rows = Math.max(0L, ((long) height - pass[1] + pass[3] - 1) / pass[3]);
                if (columns > 0 && rows > 0) {
                    rawBytes =
                            Math.addExact(
                                    rawBytes,
                                    Math.multiplyExact(rows, 1 + (columns * depth + 7) / 8));
                }
            }
            // These fixtures are finite: no billion-row input and no decoded raster allocation.
            assertThat(rawBytes).isLessThanOrEqualTo(134_217_729L);
            try (var deflater = new DeflaterOutputStream(compressed)) {
                byte[] zeros = new byte[8192];
                for (long remaining = rawBytes; remaining > 0; ) {
                    int count = (int) Math.min(remaining, zeros.length);
                    deflater.write(zeros, 0, count);
                    remaining -= count;
                }
            }
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            var output = new DataOutputStream(encoded);
            output.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            var header = new DataOutputStream(headerBytes);
            header.writeInt(width);
            header.writeInt(height);
            header.write(new byte[] {(byte) depth, 0, 0, 0, (byte) (interlaced ? 1 : 0)});
            writeChunk(output, "IHDR", headerBytes.toByteArray());
            writeChunk(output, "IDAT", compressed.toByteArray());
            writeChunk(output, "IEND", new byte[0]);
            assertThat(encoded.size()).isLessThan(10 * 1024 * 1024);
            return encoded.toByteArray();
        } catch (java.io.IOException error) {
            throw new AssertionError("Cannot encode bounded streaming PNG fixture", error);
        }
    }

    private static void writeChunk(DataOutputStream output, String name, byte[] content)
            throws java.io.IOException {
        byte[] type = name.getBytes(StandardCharsets.US_ASCII);
        CRC32 checksum = new CRC32();
        checksum.update(type);
        checksum.update(content);
        output.writeInt(content.length);
        output.write(type);
        output.write(content);
        output.writeInt((int) checksum.getValue());
    }
}
