# Product image validation — module usage

Refs #2. This module slice validates bytes only. It has no upload controller, storage adapter, Product entity/Flyway migration, authorization policy or background work. Existing Spring Security chains and shared API contracts are unchanged. Internal error codes below are not a new HTTP contract.

## Rules

- Allow JPEG (`image/jpeg`, `.jpg`/`.jpeg`), PNG (`image/png`, `.png`), WebP (`image/webp`, `.webp`). MIME and extension are compared against detected bytes; neither is authoritative. MIME/extension comparisons ignore case; MIME parameters and unspecified/unsupported declarations fail closed.
- Maximum **2 MiB = 2,097,152 bytes**, inclusive, per image. The existing Issue says `2MB` without defining decimal/binary units; this implements the explicit user-provided binary convention, not an inferred decimal contract. No reported size is accepted.
- Maximum five total images: trusted stored count + additions. Empty batches are allowed; no requirement that a Product already has an image is introduced. Null collection/entry/stream, empty file, bad declarations and malformed content fail.
- At most 2 MiB + one byte is read from each stream. The caller owns/closes streams. Decode uses an in-memory ImageInputStream, not ImageIO's optional disk cache. The validator does not open a filename, URL or filesystem path.
- ImageIO decodes real pixels. PNG chunk lengths/order/CRC/end and WebP RIFF/chunk bounds/padding are checked before decode; JPEG requires its end marker and rejects decoder warnings. PNG's zlib stream must finish with a valid checksum and no trailing compressed bytes: JDK ImageIO alone can accept missing zlib trailers.
- Resource guards: each dimension at most **8192**, at most **16,000,000 pixels**; checked before pixel allocation. PNG inflation uses an 8 KiB scratch buffer and a dimension-derived upper bound for 16-bit RGBA plus Adam7 row/filter overhead. Metadata is ignored where the decoder supports that option; input size/chunk bounds still apply.
- This validator handles **still images**. APNG and animated WebP fail closed instead of checking only their first frame. These resource/animation restrictions must be reviewed before exposing an upload contract.
- Validation returns either the entire immutable batch or an exception; no usable partial subset is returned. Each result owns the exact validated bytes and exposes a defensive copy, dimensions, format and measured size. It does not return or trust the original filename as a storage key.

## Calling the internal validator

```java
var validator = new ProductImageValidator();
// Caller opens and closes the stream. No HTTP/JWT dependency is required here.
try (InputStream stream = suppliedStream) {
    var input = new ProductImageValidator.ImageInput(fileName, contentType, stream);
    var checked = validator.validateBatch(List.of(input), trustedStoredImageCount);
    // Future storage must consume checked.getFirst().content(), not reopen suppliedStream.
}
```

`trustedStoredImageCount` must be read from authoritative backend state under the future upload operation's concurrency control. Do not bind it from a request. This pure validator cannot enforce count across concurrent requests without that integration. Use zero only for a genuinely new/no-image product or when validating the complete replacement set. Validate authorization/ownership/current Product state before accepting uploads; a successful validation is not permission to store or publish.

`ImageValidationException.getCode()` returns `IMAGE_INVALID_INPUT`, `IMAGE_EMPTY`, `IMAGE_TOO_LARGE`, `IMAGE_UNSUPPORTED_TYPE`, `IMAGE_TYPE_MISMATCH`, `IMAGE_INVALID_CONTENT`, `IMAGE_COUNT_EXCEEDED` or `IMAGE_RESOURCE_LIMIT`. Messages are fixed and do not expose filenames, decoder errors or I/O details. An HTTP error/status adapter must be separately agreed with TV1; no global handler is added here.

## Decoder dependency and limits

JDK ImageIO provides JPEG/PNG, but not WebP. The single module-local runtime dependency is `com.twelvemonkeys.imageio:imageio-webp:3.15.3`, verified resolved by Gradle and executed on Java 21. It brings the ImageIO core/metadata and common helpers, without native libwebp, an external process, Batik/SVG or framework upgrades. No shared Gradle file is changed.

Primary sources checked 2026-10-20: [supported formats and reader lifecycle](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.3/README.md), [3.15.3 release](https://github.com/haraldk/TwelveMonkeys/releases/tag/twelvemonkeys-3.15.3), [BSD 3-Clause license](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.3/LICENSE.txt), [upstream security advisories](https://github.com/haraldk/TwelveMonkeys/security/advisories). This review does not certify absence of vulnerabilities. Keep the JDK/decoder patched; any upgrade needs this regression suite. The WebP framing check follows the [WebP container specification](https://developers.google.com/speed/webp/docs/riff_container); decompression remains in the library.

This is structural/decode validation, not antivirus, metadata stripping, re-encoding or a guarantee against all polyglots. It bounds input/pixels/PNG expansion but does not provide a hard CPU deadline, sandbox or global concurrency limit for hostile decoders/streams. Before an HTTP upload is enabled, TV1/TV2 must agree request/body/read deadlines, bounded workers, safe storage keys/delivery headers, verified identity/ownership, authoritative count locking and whether sanitizing re-encoding is required. Do not serve raw files in an executable context. No production deployment readiness is implied.

## Verification

From `backend`, Java 21 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :product-service:test --tests '*ProductImageValidatorTest' --no-daemon --console=plain
.\gradlew.bat :product-service:build --no-daemon --console=plain
```

The image tests need no database. Product build also runs existing Category/Catalog PostgreSQL Testcontainers regressions, so Docker must be available. Test-only SQL proposal cases are not Product persistence or Checkout E2E evidence. Actual results and hashes are recorded below after execution; raw logs/XML stay ignored under `build/image-validation-research`.

### Actual verification — 2026-10-20

Tested source/resources are committed in `abf6a123074f820aa1ffc35f59a33b52c9b437f5` (logic commit `1654f87`). Java 21.0.12, Gradle 8.10, JVM UTC, Docker 29.8.2, PostgreSQL 16.6 Testcontainers. No existing DB was used for these tests.

- Focused image suite: **56 PASS / 0 FAIL/errors / 0 SKIPPED**, exit 0 (18s). The final build reran these tests using the final, independently encoded fixtures.
- Final command: `./gradlew.bat :product-service:build --rerun-tasks --no-daemon --console=plain`; **BUILD SUCCESSFUL / exit 0 / 52s / 9 tasks**.
- Final JUnit: **309 PASS / 0 FAIL/errors / 0 SKIPPED** = 271 unit/API/security (215 existing + 56 image) and 38 PostgreSQL cases (14 production Category regressions + 24 isolated Catalog SQL-proposal cases). Do not sum the focused/intermediate runs into this total.
- Initial targeted zlib regression: **1 FAIL**, confirming that a truncated PNG zlib trailer could pass ImageIO alone. Added bounded complete-stream/checksum verification; the unchanged regression passes in the final run. No test was disabled/deleted or assertion weakened. Other-owner suites/full-backend/positive JWT/upload/storage/Checkout E2E **NOT_RUN**, outside this slice.
- `git diff --check`, staged diff/scope checks and runtime dependency resolution: PASS. Existing Docker container/volume manifests are identical before/after the two full builds. Only transient Testcontainers resources were managed by their normal test lifecycle; no existing DB, `.env`, Compose or migration was changed.
- Raw final log SHA256: `74E468120AB54CF82BC83507FEF18C1E4E0E00C66C0742831520D3358BF9FFE1`; focused GREEN log: `CD8D12C1DF81C83805A000B72CD797C9A561B452F775FB88C2665654A2B47CFB`. RED zlib log: `3EAB22FA562F9D906A5AD5EF9BD45AB69474591F68A695B60DF264B9D7BC8E48`; raw RED XML: `DBF21473C65E435A97427F52EA450AF1578E8510B606B9BF4EA47044CE0FD8BA`. Logs/XML remain local, ignored and unmodified.
