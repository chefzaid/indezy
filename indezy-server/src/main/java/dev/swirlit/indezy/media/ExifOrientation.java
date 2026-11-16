package dev.swirlit.indezy.media;

import java.nio.charset.StandardCharsets;

/**
 * The orientation a camera wrote in a JPEG's EXIF data (1 to 8; 1 when it wrote none), which tells how to turn the
 * picture so that it stands as it was taken: the pixels of a phone photo are often stored sideways.
 */
final class ExifOrientation {

    static final int NORMAL = 1;
    private static final int TAG = 0x0112;
    private static final int APP1 = 0xE1;
    private static final int START_OF_SCAN = 0xDA;
    private static final int EXIF_HEADER = 6;
    private static final int ENTRY = 12;

    private ExifOrientation() {
    }

    static int of(byte[] jpeg) {
        int at = 2;
        while (at + 4 <= jpeg.length && (jpeg[at] & 0xFF) == 0xFF) {
            int marker = jpeg[at + 1] & 0xFF;
            int length = ((jpeg[at + 2] & 0xFF) << 8) | (jpeg[at + 3] & 0xFF);
            if (marker == START_OF_SCAN || length < 2) {
                return NORMAL;
            }
            if (marker == APP1 && at + 4 + EXIF_HEADER <= jpeg.length && "Exif".equals(new String(jpeg, at + 4, 4, StandardCharsets.US_ASCII))) {
                return fromTiff(jpeg, at + 4 + EXIF_HEADER, Math.min(jpeg.length, at + 2 + length));
            }
            at += 2 + length;
        }
        return NORMAL;
    }

    /** Reads the orientation entry of the first directory of the TIFF block from {@code start} to {@code end}. */
    private static int fromTiff(byte[] data, int start, int end) {
        if (start + 8 > end) {
            return NORMAL;
        }
        boolean little = data[start] == 'I' && data[start + 1] == 'I';
        int directory = start + (int) read(data, start + 4, 4, little);
        if (directory < start || directory + 2 > end) {
            return NORMAL;
        }
        int entries = (int) read(data, directory, 2, little);
        for (int i = 0; i < entries; i++) {
            int entry = directory + 2 + i * ENTRY;
            if (entry + ENTRY > end) {
                return NORMAL;
            }
            if (read(data, entry, 2, little) == TAG) {
                int value = (int) read(data, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static long read(byte[] data, int at, int bytes, boolean little) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            int b = data[at + (little ? bytes - 1 - i : i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }
}
