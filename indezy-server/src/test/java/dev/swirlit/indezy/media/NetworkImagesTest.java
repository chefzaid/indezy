package dev.swirlit.indezy.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkImagesTest {

    private static BufferedImage read(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void makesImagesAnewAtMost2048PixelsWideKeepingOnlyTheirPixels() throws Exception {
        NetworkImages.Image large = NetworkImages.process(NetworkFixtures.withOrientation(NetworkFixtures.image(3000, 1000, "jpg"), 1, true));
        assertThat(large.contentType()).isEqualTo("image/jpeg");
        assertThat(large.width()).isEqualTo(2048);
        assertThat(large.height()).isEqualTo(683);
        assertThat(new String(large.content(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(read(large.content()).getWidth()).isEqualTo(2048);

        NetworkImages.Image transparent = NetworkImages.process(NetworkFixtures.image(40, 20, "png"));
        assertThat(transparent.contentType()).isEqualTo("image/png");
        assertThat(transparent.width()).isEqualTo(40);
        NetworkImages.Image gif = NetworkImages.process(NetworkFixtures.image(30, 10, "gif"));
        assertThat(gif.width()).isEqualTo(30);
    }

    @Test
    void turnsAPhoneThePhotoUprightAsItsCameraSaid() throws Exception {
        byte[] wide = NetworkFixtures.image(200, 100, "jpg");
        for (boolean little : new boolean[] {true, false}) {
            NetworkImages.Image turned = NetworkImages.process(NetworkFixtures.withOrientation(wide, 6, little));
            assertThat(turned.width()).isEqualTo(100);
            assertThat(turned.height()).isEqualTo(200);
            // Turned a quarter clockwise: the red left half is now on top.
            BufferedImage upright = read(turned.content());
            assertThat(new java.awt.Color(upright.getRGB(50, 20)).getRed()).isGreaterThan(200);
            assertThat(new java.awt.Color(upright.getRGB(50, 180)).getBlue()).isGreaterThan(200);
        }
        for (int orientation = 1; orientation <= 8; orientation++) {
            BufferedImage turned = NetworkImages.upright(read(wide), orientation);
            assertThat(turned.getWidth()).isEqualTo(orientation >= 5 ? 100 : 200);
        }
        assertThat(NetworkImages.upright(read(wide), 9).getWidth()).isEqualTo(200);
        assertThat(ExifOrientation.of(wide)).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.of(NetworkFixtures.withOrientation(wide, 12, true))).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 8, 'E', 'x', 'i', 'f', 0, 0}))
            .isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void refusesWhatIsNoImageItCanRead() {
        assertThatThrownBy(() -> NetworkImages.process("not an image".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be read");
        byte[] truncated = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0};
        assertThatThrownBy(() -> NetworkImages.process(truncated)).isInstanceOf(IllegalArgumentException.class);
    }
}
