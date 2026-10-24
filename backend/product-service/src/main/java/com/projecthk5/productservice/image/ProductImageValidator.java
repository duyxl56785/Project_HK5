package com.projecthk5.productservice.image;

import static com.projecthk5.productservice.image.ImageValidationException.Code.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Internal, stateless validation only; no HTTP, storage, database or authorization adapter. */
public final class ProductImageValidator {
    public static final int MAX_IMAGES = 5;
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_DIMENSION = 8192;
    public static final long MAX_PIXELS = 16_000_000;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public enum Format {
        JPEG("image/jpeg", "jpeg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

        private final String contentType;
        private final String decoderName;

        Format(String contentType, String decoderName) {
            this.contentType = contentType;
            this.decoderName = decoderName;
        }

        public String contentType() { return contentType; }
    }

    /** The caller owns/closes the stream. Client size claims are deliberately not accepted. */
    public record ImageInput(String fileName, String contentType, InputStream content) {}

    /** Owns the exact validated bytes; callers must not reopen an untrusted source for storage. */
    public static final class ValidatedImage {
        private final Format format;
        private final int width;
        private final int height;
        private final byte[] content;

        private ValidatedImage(Format format, int width, int height, byte[] content) {
            this.format = format;
            this.width = width;
            this.height = height;
            this.content = content;
        }

        public Format format() { return format; }
        public int width() { return width; }
        public int height() { return height; }
        public int size() { return content.length; }
        public byte[] content() { return content.clone(); }
    }

    /** storedImageCount must come from trusted, concurrency-protected backend state, never a request. */
    public List<ValidatedImage> validateBatch(List<ImageInput> uploads, int storedImageCount) {
        if (uploads == null || storedImageCount < 0 || storedImageCount > MAX_IMAGES) {
            throw new ImageValidationException(IMAGE_INVALID_INPUT, "Invalid image collection or stored count");
        }
        if (uploads.size() > MAX_IMAGES - storedImageCount) {
            throw new ImageValidationException(IMAGE_COUNT_EXCEEDED, "A product can have at most five images");
        }
        // Preflight every reference before consuming streams; no partial result escapes on failure.
        for (ImageInput image : uploads) { requireInput(image); }
        var validated = new ArrayList<ValidatedImage>(uploads.size());
        for (ImageInput image : uploads) { validated.add(validate(image)); }
        return List.copyOf(validated);
    }

    public ValidatedImage validate(ImageInput image) {
        requireInput(image);
        Format declared = declaredFormat(image.contentType());
        Format extension = extensionFormat(image.fileName());
        byte[] bytes;
        try {
            bytes = image.content().readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw invalidContent();
        }
        if (bytes.length == 0) { throw new ImageValidationException(IMAGE_EMPTY, "Image is empty"); }
        if (bytes.length > MAX_BYTES) {
            throw new ImageValidationException(IMAGE_TOO_LARGE, "Image exceeds two MiB");
        }
        Format detected = detectFormat(bytes);
        if (detected != declared || detected != extension) {
            throw new ImageValidationException(IMAGE_TYPE_MISMATCH, "Image declarations do not match its content");
        }
        checkEnvelope(bytes, detected);
        return decode(bytes, detected);
    }

    private static void requireInput(ImageInput image) {
        if (image == null || image.content() == null) {
            throw new ImageValidationException(IMAGE_INVALID_INPUT, "Image input is required");
        }
    }

    private static Format declaredFormat(String contentType) {
        if (contentType != null) {
            for (Format format : Format.values()) {
                if (format.contentType.equals(contentType.strip().toLowerCase(Locale.ROOT))) { return format; }
            }
        }
        throw new ImageValidationException(IMAGE_UNSUPPORTED_TYPE, "Only JPEG, PNG and WebP are supported");
    }

    private static Format extensionFormat(String fileName) {
        if (fileName == null || fileName.indexOf('\0') >= 0) {
            throw new ImageValidationException(IMAGE_INVALID_INPUT, "Image filename is required");
        }
        int dot = fileName.lastIndexOf('.');
        return switch (dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> Format.JPEG;
            case "png" -> Format.PNG;
            case "webp" -> Format.WEBP;
            default -> throw new ImageValidationException(IMAGE_UNSUPPORTED_TYPE, "Unsupported image extension");
        };
    }

    private static Format detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) {
            return Format.JPEG;
        }
        if (bytes.length >= 8 && java.util.Arrays.equals(bytes, 0, 8, PNG_SIGNATURE, 0, 8)) { return Format.PNG; }
        if (bytes.length >= 12 && tag(bytes, 0).equals("RIFF") && tag(bytes, 8).equals("WEBP")) { return Format.WEBP; }
        throw invalidContent();
    }

    // Check framing that decoders may tolerate/ignore; pixel decompression stays in ImageIO.
    private static void checkEnvelope(byte[] bytes, Format format) {
        switch (format) {
            case JPEG -> {
                if (bytes.length < 4 || bytes[bytes.length - 2] != (byte) 0xff || bytes[bytes.length - 1] != (byte) 0xd9) {
                    throw invalidContent();
                }
            }
            case PNG -> checkPng(bytes);
            case WEBP -> checkWebp(bytes);
        }
    }

    private static void checkPng(byte[] bytes) {
        int offset = 8;
        boolean data = false;
        boolean dataEnded = false;
        while (offset <= bytes.length - 12) {
            long length = unsignedInt(bytes, offset, ByteOrder.BIG_ENDIAN);
            if (length > bytes.length - offset - 12) { throw invalidContent(); }
            int size = (int) length;
            String type = tag(bytes, offset + 4);
            for (int index = 0; index < 4; index++) {
                char value = type.charAt(index);
                if (!(value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z')) { throw invalidContent(); }
            }
            if (!Character.isUpperCase(type.charAt(2))) { throw invalidContent(); }
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, size + 4);
            if (crc.getValue() != unsignedInt(bytes, offset + size + 8, ByteOrder.BIG_ENDIAN)) { throw invalidContent(); }
            if (offset == 8 && (!type.equals("IHDR") || size != 13)) { throw invalidContent(); }
            if (offset != 8 && type.equals("IHDR")) { throw invalidContent(); }
            // Decode only still images; do not silently validate the first frame of an animation.
            if (type.equals("acTL")) { throw invalidContent(); }
            if (type.equals("IDAT")) {
                if (dataEnded) { throw invalidContent(); }
                data = true;
            } else if (data) { dataEnded = true; }
            if (type.equals("IEND")) {
                if (!data || size != 0 || offset + 12 != bytes.length) { throw invalidContent(); }
                return;
            }
            if (Character.isUpperCase(type.charAt(0)) && !List.of("IHDR", "PLTE", "IDAT").contains(type)) {
                throw invalidContent();
            }
            offset += size + 12;
        }
        throw invalidContent();
    }

    private static void checkWebp(byte[] bytes) {
        if (unsignedInt(bytes, 4, ByteOrder.LITTLE_ENDIAN) != bytes.length - 8L) { throw invalidContent(); }
        int offset = 12;
        int imageChunks = 0;
        while (offset <= bytes.length - 8) {
            String type = tag(bytes, offset);
            long size = unsignedInt(bytes, offset + 4, ByteOrder.LITTLE_ENDIAN);
            long end = offset + 8L + size + (size & 1);
            if (end > bytes.length) { throw invalidContent(); }
            if (offset == 12 && !List.of("VP8 ", "VP8L", "VP8X").contains(type)) { throw invalidContent(); }
            if (type.equals("ANIM") || type.equals("ANMF")) { throw invalidContent(); }
            if (type.equals("VP8X") && (offset != 12 || size != 10 || (bytes[offset + 8] & 2) != 0)) {
                throw invalidContent();
            }
            if (type.equals("VP8 ") || type.equals("VP8L")) { imageChunks++; }
            if ((size & 1) != 0 && bytes[(int) end - 1] != 0) { throw invalidContent(); }
            offset = (int) end;
        }
        if (offset != bytes.length || imageChunks != 1) { throw invalidContent(); }
    }

    private static ValidatedImage decode(byte[] bytes, Format format) {
        var readers = ImageIO.getImageReadersByFormatName(format.decoderName);
        if (!readers.hasNext()) { throw invalidContent(); }
        ImageReader reader = readers.next();
        boolean[] warned = {false};
        reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, false, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) {
                throw new ImageValidationException(IMAGE_RESOURCE_LIMIT, "Image dimensions exceed safe decoding limits");
            }
            if (format == Format.PNG) { checkPngCompression(bytes, width, height); }
            BufferedImage decoded = reader.read(0);
            if (decoded == null) { throw invalidContent(); }
            try {
                if (warned[0] || decoded.getWidth() != width || decoded.getHeight() != height) { throw invalidContent(); }
                return new ValidatedImage(format, width, height, bytes);
            } finally { decoded.flush(); }
        } catch (ImageValidationException e) {
            throw e;
        } catch (IOException | DataFormatException | RuntimeException e) {
            throw invalidContent();
        } finally { reader.dispose(); }
    }

    private static void checkPngCompression(byte[] bytes, int width, int height) throws DataFormatException {
        // ImageIO can stop once pixels are filled without verifying zlib's end/checksum.
        // 16-bit RGBA is at most 8 bytes/pixel; allow filter/padding rows for all seven Adam7 passes.
        long maxInflated = (long) width * height * 8 + 14L * height + 14;
        byte[] buffer = new byte[8192];
        Inflater inflater = new Inflater();
        try {
            for (int offset = 8; offset < bytes.length; ) {
                int length = (int) unsignedInt(bytes, offset, ByteOrder.BIG_ENDIAN);
                if (tag(bytes, offset + 4).equals("IDAT") && length > 0) {
                    if (inflater.finished()) { throw invalidContent(); }
                    inflater.setInput(bytes, offset + 8, length);
                    while (!inflater.needsInput() && !inflater.finished()) {
                        int count = inflater.inflate(buffer);
                        if (inflater.getBytesWritten() > maxInflated) {
                            throw new ImageValidationException(IMAGE_RESOURCE_LIMIT, "Image decompression exceeds safe limits");
                        }
                        if (count == 0 && !inflater.needsInput() && !inflater.finished()) { throw invalidContent(); }
                    }
                    if (inflater.finished() && inflater.getRemaining() != 0) { throw invalidContent(); }
                }
                offset += length + 12;
            }
            // ImageIO may ignore extra decompressed bytes once the declared pixels are filled.
            if (!inflater.finished() || inflater.getBytesWritten() != pngScanlineBytes(bytes, width, height)) {
                throw invalidContent();
            }
        } finally { inflater.end(); }
    }

    private static long pngScanlineBytes(byte[] bytes, int width, int height) {
        int channels = switch (Byte.toUnsignedInt(bytes[25])) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw invalidContent();
        };
        int bitsPerPixel = channels * Byte.toUnsignedInt(bytes[24]);
        // Each pass is x/y start followed by x/y stride; empty Adam7 passes have no filter bytes.
        int[][] passes = switch (bytes[28]) {
            case 0 -> new int[][] {{0, 0, 1, 1}};
            case 1 -> new int[][] {{0, 0, 8, 8}, {4, 0, 8, 8}, {0, 4, 4, 8}, {2, 0, 4, 4},
                    {0, 2, 2, 4}, {1, 0, 2, 2}, {0, 1, 1, 2}};
            default -> throw invalidContent();
        };
        long total = 0;
        for (int[] pass : passes) {
            if (width <= pass[0] || height <= pass[1]) { continue; }
            long columns = (width - pass[0] + pass[2] - 1) / pass[2];
            long rows = (height - pass[1] + pass[3] - 1) / pass[3];
            total += (1 + (columns * bitsPerPixel + 7) / 8) * rows;
        }
        return total;
    }

    private static long unsignedInt(byte[] bytes, int offset, ByteOrder order) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).order(order).getInt());
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static ImageValidationException invalidContent() {
        return new ImageValidationException(IMAGE_INVALID_CONTENT, "Image content cannot be validated");
    }
}
