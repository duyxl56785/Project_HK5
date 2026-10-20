package com.projecthk5.productservice.image;

import static com.projecthk5.productservice.image.ImageValidationException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.productservice.image.ImageValidationException.Code;
import com.projecthk5.productservice.image.ProductImageValidator.Format;
import com.projecthk5.productservice.image.ProductImageValidator.ImageInput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductImageValidatorTest {
    private final ProductImageValidator validator = new ProductImageValidator();

    static Stream<Arguments> images() {
        return Stream.of(Arguments.of("sample.jpg", Format.JPEG), Arguments.of("sample.png", Format.PNG),
                Arguments.of("sample.webp", Format.WEBP), Arguments.of("alpha.webp", Format.WEBP));
    }

    @ParameterizedTest
    @MethodSource("images")
    void decodesRealImagePixelsAndReturnsExactBytes(String name, Format format) throws IOException {
        byte[] bytes = fixture(name);
        var result = validator.validate(input(name, format.contentType(), bytes));
        assertEquals(format, result.format());
        assertEquals(16, result.width());
        assertEquals(12, result.height());
        assertEquals(bytes.length, result.size());
        assertArrayEquals(bytes, result.content());
    }

    @Test
    void jpegAliasAndCaseInsensitiveDeclarationsAreAccepted() throws IOException {
        assertEquals(Format.JPEG, validator.validate(input("PHOTO.JPEG", " IMAGE/JPEG ", fixture("sample.jpg"))).format());
    }

    @Test
    void decodesActualLosslessWebpFixture() throws IOException {
        var result = validator.validate(input("lossless.webp", "image/webp", fixture("lossless.webp")));
        assertEquals(Format.WEBP, result.format());
        assertTrue(result.width() > 0);
        assertTrue(result.height() > 0);
    }

    @Test
    void resultOwnsItsBytesInsteadOfReopeningOrExposingTheInput() throws IOException {
        byte[] original = fixture("sample.png");
        var result = validator.validate(input("photo.png", "image/png", original));
        original[0] = 0;
        result.content()[0] = 0;
        assertEquals((byte) 137, result.content()[0]);
    }

    @Test
    void nullInputAndStreamAreRejected() {
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validate(null));
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validate(new ImageInput("photo.png", "image/png", null)));
    }

    @Test
    void zeroBytesAreRejected() {
        assertCode(IMAGE_EMPTY, () -> validator.validate(input("photo.png", "image/png", new byte[0])));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"image/gif", "image/svg+xml", "application/octet-stream", "image/jpg", "image/png; charset=utf-8"})
    void onlyExplicitSupportedMimeTypesAreAccepted(String type) throws IOException {
        byte[] bytes = fixture("sample.png");
        assertCode(IMAGE_UNSUPPORTED_TYPE, () -> validator.validate(input("photo.png", type, bytes)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.svg", "photo.gif", "photo.png.exe", "photo", ""})
    void unsupportedOrDisguisedFilenameExtensionIsRejected(String name) throws IOException {
        byte[] bytes = fixture("sample.png");
        assertCode(IMAGE_UNSUPPORTED_TYPE, () -> validator.validate(input(name, "image/png", bytes)));
    }

    @Test
    void nullOrNulFilenameIsRejectedWithoutUsingItAsAPath() throws IOException {
        byte[] bytes = fixture("sample.png");
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validate(input(null, "image/png", bytes)));
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validate(input("photo\0.png", "image/png", bytes)));
        assertEquals(Format.PNG, validator.validate(input("../../photo.png", "image/png", bytes)).format());
    }

    @Test
    void realJpegDeclaredAsPngAndRealPngRenamedAsJpegAreRejected() throws IOException {
        byte[] jpeg = fixture("sample.jpg");
        byte[] png = fixture("sample.png");
        assertCode(IMAGE_TYPE_MISMATCH, () -> validator.validate(input("photo.png", "image/png", jpeg)));
        assertCode(IMAGE_TYPE_MISMATCH, () -> validator.validate(input("photo.jpg", "image/png", png)));
        assertCode(IMAGE_TYPE_MISMATCH, () -> validator.validate(input("photo.png", "image/jpeg", png)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello world", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>", "<script>alert(1)</script>"})
    void nonImagesWithPngFilenameAndMimeAreRejected(String value) {
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", value.getBytes(StandardCharsets.UTF_8))));
    }

    @ParameterizedTest
    @MethodSource("images")
    void truncatedRealImagesAreRejected(String name, Format format) throws IOException {
        byte[] bytes = fixture(name);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input(name, format.contentType(), Arrays.copyOf(bytes, bytes.length / 2))));
    }

    @Test
    void jpegTruncatedInsideScanIsRejectedEvenWithRestoredEndMarker() throws IOException {
        byte[] bytes = fixture("sample.jpg");
        byte[] damaged = Arrays.copyOf(bytes, bytes.length - 60);
        damaged[damaged.length - 2] = (byte) 0xff;
        damaged[damaged.length - 1] = (byte) 0xd9;
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.jpg", "image/jpeg", damaged)));
    }

    @Test
    void pngCorruptedCompressedPixelsAreRejectedEvenWithCorrectChunkCrc() throws IOException {
        byte[] bytes = fixture("sample.png");
        int offset = chunkOffset(bytes, "IDAT");
        int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
        Arrays.fill(bytes, offset + 8, offset + 8 + length, (byte) 0);
        rewriteCrc(bytes, offset);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", bytes)));
    }

    @Test
    void pngMissingZlibChecksumIsRejectedEvenWhenOuterChunksAreWellFormed() throws IOException {
        byte[] png = fixture("sample.png");
        int offset = chunkOffset(png, "IDAT");
        int length = ByteBuffer.wrap(png, offset, 4).getInt();
        byte[] truncated = new byte[png.length - 4];
        System.arraycopy(png, 0, truncated, 0, offset + 8 + length - 4);
        ByteBuffer.wrap(truncated, offset, 4).putInt(length - 4);
        rewriteCrc(truncated, offset);
        System.arraycopy(png, offset + 12 + length, truncated, offset + 8 + length, png.length - offset - 12 - length);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", truncated)));
    }

    @Test
    void pngBadZlibChecksumAndTrailingCompressedDataAreRejected() throws IOException {
        byte[] png = fixture("sample.png");
        int offset = chunkOffset(png, "IDAT");
        int length = ByteBuffer.wrap(png, offset, 4).getInt();
        byte[] compressed = Arrays.copyOfRange(png, offset + 8, offset + 8 + length);
        compressed[compressed.length - 1] ^= 1;
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", replacePngData(png, compressed))));
        byte[] extra = Arrays.copyOfRange(png, offset + 8, offset + 8 + length + 1);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", replacePngData(png, extra))));
    }

    @Test
    void compressedDataCannotExpandBeyondDeclaredPixelBudget() throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) { deflater.write(new byte[1_000_000]); }
        byte[] png = replacePngData(fixture("sample.png"), compressed.toByteArray());
        assertCode(IMAGE_RESOURCE_LIMIT, () -> validator.validate(input("photo.png", "image/png", png)));
    }

    @Test
    void pngCrcMismatchAndMissingEndChunkAreRejected() throws IOException {
        byte[] bytes = fixture("sample.png");
        byte[] badCrc = bytes.clone();
        badCrc[29] ^= 1;
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", badCrc)));
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", Arrays.copyOf(bytes, bytes.length - 12))));
    }

    @Test
    void webpWithValidRiffHeaderAndChunkSizesButGarbagePixelsIsRejected() throws IOException {
        byte[] bytes = fixture("sample.webp");
        Arrays.fill(bytes, 20, bytes.length, (byte) 0);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.webp", "image/webp", bytes)));
    }

    @Test
    void impossibleChunkLengthsCannotAllocateFromUntrustedMetadata() throws IOException {
        byte[] png = fixture("sample.png");
        Arrays.fill(png, 8, 12, (byte) 0xff);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", png)));
        byte[] webp = fixture("sample.webp");
        Arrays.fill(webp, 16, 20, (byte) 0xff);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.webp", "image/webp", webp)));
    }

    @Test
    void webpRiffLengthMismatchAndTrailingDataAreRejected() throws IOException {
        byte[] bytes = fixture("sample.webp");
        byte[] badLength = bytes.clone();
        badLength[4] ^= 1;
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.webp", "image/webp", badLength)));
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 10);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.webp", "image/webp", trailing)));
    }

    @Test
    void oversizedDimensionsAndPixelBombAreRejectedBeforePixelDecode() throws IOException {
        for (int[] dimensions : List.of(new int[]{8193, 1}, new int[]{5000, 5000})) {
            byte[] bytes = fixture("sample.png");
            ByteBuffer.wrap(bytes, 16, 8).putInt(dimensions[0]).putInt(dimensions[1]);
            rewriteCrc(bytes, 8);
            assertCode(IMAGE_RESOURCE_LIMIT, () -> validator.validate(input("photo.png", "image/png", bytes)));
        }
        byte[] webp = fixture("alpha.webp");
        webp[24] = 0;
        webp[25] = 0x30;
        webp[26] = 0;
        assertCode(IMAGE_RESOURCE_LIMIT, () -> validator.validate(input("photo.webp", "image/webp", webp)));
    }

    @Test
    void animationIsRejectedRatherThanValidatingOnlyItsFirstFrame() throws IOException {
        byte[] png = withPngChunk(fixture("sample.png"), "acTL", new byte[8]);
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.png", "image/png", png)));
        byte[] webp = fixture("alpha.webp");
        webp[20] |= 2;
        assertCode(IMAGE_INVALID_CONTENT, () -> validator.validate(input("photo.webp", "image/webp", webp)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void actualByteLimitIsInclusiveAndDoesNotTrustAReportedSize(int delta) throws IOException {
        byte[] original = fixture("sample.png");
        byte[] bytes = withPngChunk(original, "ruSt", new byte[ProductImageValidator.MAX_BYTES + delta - original.length - 12]);
        assertEquals(ProductImageValidator.MAX_BYTES + delta, bytes.length);
        if (delta <= 0) {
            assertEquals(bytes.length, validator.validate(input("photo.png", "image/png", bytes)).size());
        } else {
            assertCode(IMAGE_TOO_LARGE, () -> validator.validate(input("photo.png", "image/png", bytes)));
        }
    }

    @Test
    void endlessStreamReadsAtMostLimitPlusOneByte() {
        var reads = new AtomicInteger();
        InputStream endless = new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return 1; }
            @Override public int read(byte[] b, int offset, int length) {
                reads.addAndGet(length);
                Arrays.fill(b, offset, offset + length, (byte) 1);
                return length;
            }
        };
        assertCode(IMAGE_TOO_LARGE, () -> validator.validate(new ImageInput("photo.png", "image/png", endless)));
        assertEquals(ProductImageValidator.MAX_BYTES + 1, reads.get());
    }

    @Test
    void ioErrorsUseSafeInternalErrorAndCallerKeepsStreamOwnership() throws IOException {
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("private filesystem path and credential"); }
        };
        var error = assertThrows(ImageValidationException.class,
                () -> validator.validate(new ImageInput("photo.png", "image/png", broken)));
        assertEquals(IMAGE_INVALID_CONTENT, error.getCode());
        assertFalse(error.getMessage().contains("private"));
        assertNull(error.getCause());
        var closed = new AtomicInteger();
        var stream = new ByteArrayInputStream(fixture("sample.png")) {
            @Override public void close() { closed.incrementAndGet(); }
        };
        validator.validate(new ImageInput("photo.png", "image/png", stream));
        assertEquals(0, closed.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void acceptsZeroThroughFiveImagesWithoutInventingRequiredImageRule(int count) throws IOException {
        byte[] bytes = fixture("sample.png");
        var batch = IntStream.range(0, count).mapToObj(i -> input("photo.png", "image/png", bytes)).toList();
        assertEquals(count, validator.validateBatch(batch, 0).size());
    }

    @Test
    void sixthImageIsRejectedBeforeAnyStreamIsRead() {
        InputStream mustNotRead = new InputStream() {
            @Override public int read() { fail("Count preflight must run before reading"); return -1; }
        };
        var batch = IntStream.range(0, 6).mapToObj(i -> new ImageInput("photo.png", "image/png", mustNotRead)).toList();
        assertCode(IMAGE_COUNT_EXCEEDED, () -> validator.validateBatch(batch, 0));
    }

    @Test
    void existingTrustedCountIsIncludedAndCannotOverflow() throws IOException {
        var image = input("photo.png", "image/png", fixture("sample.png"));
        assertEquals(1, validator.validateBatch(List.of(image), 4).size());
        assertEquals(List.of(), validator.validateBatch(List.of(), 5));
        assertCode(IMAGE_COUNT_EXCEEDED, () -> validator.validateBatch(List.of(image), 5));
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validateBatch(List.of(), -1));
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validateBatch(List.of(), 6));
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validateBatch(List.of(), Integer.MAX_VALUE));
    }

    @Test
    void nullCollectionAndNullElementAreRejected() {
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validateBatch(null, 0));
        assertCode(IMAGE_INVALID_INPUT, () -> validator.validateBatch(Arrays.asList((ImageInput) null), 0));
    }

    @Test
    void invalidBatchDoesNotReturnAUsableSubsetAndResultIsImmutable() throws IOException {
        byte[] png = fixture("sample.png");
        assertCode(IMAGE_EMPTY, () -> validator.validateBatch(List.of(
                input("photo.png", "image/png", png), input("bad.png", "image/png", new byte[0])), 0));
        var images = validator.validateBatch(List.of(input("photo.png", "image/png", png)), 0);
        assertThrows(UnsupportedOperationException.class, images::clear);
    }

    private static ImageInput input(String name, String type, byte[] bytes) {
        return new ImageInput(name, type, new ByteArrayInputStream(bytes));
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = ProductImageValidatorTest.class.getResourceAsStream("/images/" + name)) {
            return java.util.Objects.requireNonNull(input).readAllBytes();
        }
    }

    private static void assertCode(Code code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(ImageValidationException.class, operation).getCode());
    }

    private static int chunkOffset(byte[] png, String name) {
        for (int offset = 8; offset < png.length; offset += ByteBuffer.wrap(png, offset, 4).getInt() + 12) {
            if (new String(png, offset + 4, 4, StandardCharsets.US_ASCII).equals(name)) { return offset; }
        }
        throw new AssertionError("Missing fixture chunk " + name);
    }

    private static void rewriteCrc(byte[] png, int offset) {
        int length = ByteBuffer.wrap(png, offset, 4).getInt();
        CRC32 crc = new CRC32();
        crc.update(png, offset + 4, length + 4);
        ByteBuffer.wrap(png, offset + length + 8, 4).putInt((int) crc.getValue());
    }

    private static byte[] withPngChunk(byte[] png, String type, byte[] data) throws IOException {
        int insert = chunkOffset(png, "IDAT");
        var output = new ByteArrayOutputStream();
        output.write(png, 0, insert);
        byte[] chunk = new byte[data.length + 12];
        ByteBuffer.wrap(chunk).putInt(data.length).put(type.getBytes(StandardCharsets.US_ASCII)).put(data);
        rewriteCrc(chunk, 0);
        output.write(chunk);
        output.write(png, insert, png.length - insert);
        return output.toByteArray();
    }

    private static byte[] replacePngData(byte[] png, byte[] data) throws IOException {
        int offset = chunkOffset(png, "IDAT");
        int length = ByteBuffer.wrap(png, offset, 4).getInt();
        var output = new ByteArrayOutputStream();
        output.write(png, 0, offset);
        byte[] chunk = new byte[data.length + 12];
        ByteBuffer.wrap(chunk).putInt(data.length).put("IDAT".getBytes(StandardCharsets.US_ASCII)).put(data);
        rewriteCrc(chunk, 0);
        output.write(chunk);
        output.write(png, offset + length + 12, png.length - offset - length - 12);
        return output.toByteArray();
    }
}
