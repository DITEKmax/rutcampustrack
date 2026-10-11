package ru.rutcampustrack.academic.map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

final class CampusMapFileFixture {
    private CampusMapFileFixture() {
    }

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
}
