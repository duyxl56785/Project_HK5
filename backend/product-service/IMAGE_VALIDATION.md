# Product image validation — module usage

Refs #2. Validation and an internal local-disk storage adapter are available. This image slice adds no upload controller, Product-image entity/Flyway migration, authorization policy or background work. Product persistence is inherited from merged main; Product-to-image linkage remains pending. Existing Spring Security chains and shared API contracts are unchanged. Internal error codes below are not a new HTTP contract.

## Rules

- Allow JPEG (`image/jpeg`, `.jpg`/`.jpeg`), PNG (`image/png`, `.png`), WebP (`image/webp`, `.webp`). MIME and extension are compared against detected bytes; neither is authoritative. MIME/extension comparisons ignore case; MIME parameters and unspecified/unsupported declarations fail closed.
- Maximum **2 MiB = 2,097,152 bytes**, inclusive, per image. The existing Issue says `2MB` without defining decimal/binary units; this implements the explicit user-provided binary convention, not an inferred decimal contract. No reported size is accepted.
- Maximum five total images: trusted stored count + additions. Empty batches are allowed; no requirement that a Product already has an image is introduced. Null collection/entry/stream, empty file, bad declarations and malformed content fail.
- At most 2 MiB + one byte is read from each stream. The caller owns/closes streams. Decode uses an in-memory ImageInputStream, not ImageIO's optional disk cache. The validator does not open a filename, URL or filesystem path.
- ImageIO decodes real pixels. PNG chunk lengths/order/CRC/end and WebP RIFF/chunk bounds/padding are checked before decode; JPEG requires its end marker and rejects decoder warnings. PNG's zlib stream must finish with a valid checksum and no trailing compressed bytes: JDK ImageIO alone can accept missing zlib trailers. Inflated PNG data must also have exactly the filtered scanline length declared by IHDR, including packed samples and each nonempty Adam7 pass; a valid checksum does not make surplus pixel data valid.
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

### Exact PNG scanline regression — 2026-10-22

Based on main `aca5dff247ef8eae3ce97c1a73b9474b87e94110`. Java 21.0.12, Gradle 8.10, JVM UTC, Docker 29.9.1 and PostgreSQL 16.6 Testcontainers. No decoder/dependency, HTTP contract, security chain, storage or schema changes. Existing results above remain historical.

The validator now checks the complete inflated length against IHDR dimensions, channel count, sample depth and interlace layout. Per-row byte packing, filter bytes and empty Adam7 passes follow the [W3C PNG specification, scanlines and interlacing](https://www.w3.org/TR/2025/REC-png-3-20250624/#7Scanlines). ImageIO still decodes the pixels, and the existing bounded inflater/checksum/trailing-compressed-data checks remain in force.

- Actual RED: the two excess-inflated-data regressions **FAIL** before the production change because validation returned successfully. Both fixtures have valid zlib checksum and chunk CRC; one adds a byte, the other adds a whole RGB row. An earlier fixture-construction attempt separately failed twice with `EOFException` before the assertions (it treated a multi-IDAT image as one chunk). Both raw runs are retained; that setup failure is not the validator RED.
- Focused command: `./backend/gradlew.bat -p backend :product-service:test --tests '*ProductImageValidatorTest' --rerun-tasks --no-daemon --console=plain`; **67 PASS / 0 FAIL/errors / 0 SKIPPED**, exit 0, 14s. Includes seven independently JDK-encoded layouts (packed/indexed/gray/RGB/RGBA, 16-bit gray and Adam7 with empty passes) and two valid 16-bit alpha layouts. No new binary fixtures or weakened assertions.
- Full command: `./backend/gradlew.bat -p backend :product-service:build --rerun-tasks --no-daemon --console=plain`; **BUILD SUCCESSFUL**, exit 0, 44s, nine executed tasks. Final XML: **282 unit/API/security + 14 production Category PostgreSQL + 24 Catalog SQL-proposal PostgreSQL = 320 PASS / 0 FAIL/errors / 0 SKIPPED**. The focused suite is included, not added again. Flyway V1 ran on disposable PostgreSQL; prototype cases do not establish Product persistence or Checkout acceptance.
- Independent read-only review: no blocking findings in the validator/test diff. Existing Docker container IDs/names and volume names are unchanged before/after the full build. Only normal Testcontainers lifecycle cleanup occurred; no existing container, volume or DB was deleted.
- Seller/full-backend reruns, real JWT/HTTP upload, storage/Product association and live Checkout E2E: **NOT_RUN**, outside this parser-only change or dependent on unapproved runtime/contracts. Product image upload/F06 aggregate remains incomplete.
- Raw evidence stays ignored under `build/png-scanline-evidence-20261022`: actual RED log SHA256 `74EB57625C0558E5C5446C7105F2AFE3825F140C3903893B69A2FDB04EA9ABFE`; RED XML `7E447099F6C4C4EE97F0C4062118346F4B45DFAAFCD0FDE3F963A07143325DE3`; focused GREEN log `DB8366425A1E947F3C0538C2E270C367A2EF3611776E22D61CCF13D6502DE7AE`; full-build log `D83DA4ABCDE6AC1614A328F0018BE6561E4D5F94673B7ED2C0F916A45F114BEA`. Raw logs/XML are not edited or committed.

## Internal local image storage — not an upload API

`ImageStorage` stores a `ProductImageValidator.ValidatedImage` and returns opaque key/format/measured
size/dimensions. `read(key)` returns freshly read bytes. No filename, stream, Product ID, owner or image
count is accepted by storage. `LocalFileSystemImageStorage` takes an **explicit existing absolute private
directory**; it has no default root, configuration bean, public URL or static-resource mapping.
Provision that directory outside source/build/public/secret locations. The root and its ancestors must
be controlled by the service account, protected from untrusted local writers. Windows inherits that ACL;
on POSIX, newly reserved directories/files use0700/0600. The adapter does not modify existing root ACLs.

Each server-generated UUID reserves its own directory using create-new semantics before writing.
Only the defensive `ValidatedImage.content()` snapshot is written to `.pending`, forced and closed,
then renamed to `content` with ATOMIC_MOVE. Only AtomicMoveNotSupportedException enables a same-directory
move without replace. Reservation, not an existence precheck or ATOMIC_MOVE alone, prevents another
adapter overwriting an existing key. Metadata is returned only after publication; no partial `.pending`
bytes are readable through `read`. Non-atomic fallback does not promise atomic visibility or power-loss
durability; force is not a portable directory fsync or transactional database commit.

Strict canonical UUID keys reject separators, absolute paths, traversal, alternate encodings and suffixes.
Root ancestry, key directories and content reject symlinks/reparse/junction/nonregular entries. Reads use
NOFOLLOW_LINKS and are bounded to2MiB+1, including tampered/oversized stored files. Directory file keys are
checked when available; Windows creation time is only a replacement diagnostic. **Portable Java on Windows
cannot guarantee race-free directory traversal against a concurrent same-account filesystem attacker**;
the private-root/ancestor ACL requirement is essential. No SecureDirectoryStream guarantee is claimed.

I/O exceptions have fixed internal codes/messages with no filesystem cause/suppressed-path disclosure.
Failure cleanup touches only the temp/empty directory reserved by that call, without recursive deletion;
it never deletes a colliding key. If permission loss or unsafe redirection prevents cleanup, a private
orphan can remain and the original sanitized failure is preserved. No destructive automatic sweeper is added.

Tests use real temporary files and Windows junctions (Unix symlinks on Unix); deterministic filesystem
failure/atomic-fallback injection is distinguished from a physically full disk. No existing user data or
database is used for storage tests. Existing Product security denies proposed image upload/read/delete
paths, including a test-only handler behind the unchanged filter chain; this is not real JWT acceptance.

**Pending integration:** Product-to-image metadata/order/ownership/link persistence, concurrency-protected
five-image count, compensation/orphan policy, verified authorization, upload/read/delete HTTP, delivery
headers and public URLs. A key does not grant ownership. PR72 PNG exact-scanline hardening was unmerged
at the original storage baseline. It is now inherited from main via commit
`61f39b42d63a59c76f5323581bf8fe98fa26faa2`; storage consumes the current validator without a separate
copy of its patch. Preserving validated bytes does not certify antivirus safety or live upload P0 completion.
